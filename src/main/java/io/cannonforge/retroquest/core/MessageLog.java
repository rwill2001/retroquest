package io.cannonforge.retroquest.core;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * In-game CRT message log — sits below GamePanel, full width.
 *
 * Two modes:
 *   PASSIVE  — messages scroll in automatically, no input needed
 *   PROMPT   — shows a question + labelled key options, waiting for input
 *
 * Retroquest calls:
 *   game.log("You enter Stonehaven!")                       — passive
 *   game.log("You found 40 gold!", MessageLog.Type.LOOT)   — coloured
 *   game.prompt("Drink from the fountain?",                 — Y/N prompt
 *       () -> drinkFountain(), null)
 *   game.prompt("Which way?", opts, callbacks)              — multi-option
 */
@SuppressWarnings("serial")
public class MessageLog extends javax.swing.JPanel {

    // ── Message types ─────────────────────────────────────────────────────────
    public enum Type {
        NORMAL, INFO, LOOT, DANGER, SYSTEM, GOOD, DIM
    }

    // ── Palette ───────────────────────────────────────────────────────────────
    private static final Color BG          = new Color(  5,   7,  11);
    private static final Color PANEL_BG    = new Color(  9,  12,  18);
    private static final Color BORDER_TOP  = new Color( 28,  42,  62);
    private static final Color CYAN_ACC    = new Color(  0, 200, 255);
    private static final Color AMBER       = new Color(255, 200,  50);
    private static final Color AMBER_DIM   = new Color(160, 120,  25);
    private static final Color TEXT_BRIGHT = new Color(200, 222, 255);
    private static final Color TEXT_DIM    = new Color( 80, 100, 125);
    private static final Color DANGER      = new Color(220,  55,  55);
    private static final Color GOOD        = new Color( 50, 210,  80);

    private static final Font F_MSG   = Fonts.mono    (12);
    private static final Font F_LABEL = Fonts.monoBold (12);
    private static final Font F_KEY   = Fonts.monoBold (11);
    private static final Font F_SMALL = Fonts.mono     (10);

    // ── Message entry ─────────────────────────────────────────────────────────
    private static class Entry {
        final String text;
        final Color  color;
        final long   time;
        Entry(String t, Color c) { text = t; color = c; time = System.currentTimeMillis(); }
    }

    // ── Prompt state ──────────────────────────────────────────────────────────
    private boolean    inPrompt   = false;

    // ── Text-input state ──────────────────────────────────────────────────────
    private boolean              inTextInput   = false;
    private String               textInputPrompt = "";
    private StringBuilder        textInputBuffer = new StringBuilder();
    private java.util.function.Consumer<String> textInputCallback;
    private String     promptText = "";
    private String[]   promptKeys;     // e.g. ["Y","N"] or ["1","2","3"]
    private String[]   promptLabels;   // e.g. ["Yes","No"] or ["Go Up","Go Down","Cancel"]
    private Runnable[] promptActions;
    private int        promptHighlight = 0;  // for arrow-key navigation

    // ── Log buffer ────────────────────────────────────────────────────────────
    private static final int MAX_ENTRIES = 120;
    private final Deque<Entry> entries = new ArrayDeque<>();

    // ── Scroll state ──────────────────────────────────────────────────────────
    /** Lines scrolled up from the bottom; 0 = newest message at bottom. */
    private int scrollOffset = 0;

    // ── Scanline animation ────────────────────────────────────────────────────
    private int scanOffset = 0;
    private javax.swing.Timer scanTimer;

    /**
     * How long an entry keeps fading (see {@code paintLog}: {@code age / 30_000f}).
     * Once the newest entry is older than this nothing on the panel changes, so the
     * repaint timer has nothing left to do and stops itself.
     */
    private static final long FADE_MS = 30_000L;

    // ── Cached buffer ─────────────────────────────────────────────────────────
    private BufferedImage buffer;

    // ── Panel height ─────────────────────────────────────────────────────────
    private static final int PANEL_H = DisplayScale.scaled(90);

    public MessageLog() {
        setPreferredSize(new Dimension(0, PANEL_H));
        setMinimumSize  (new Dimension(0, PANEL_H));
        setMaximumSize  (new Dimension(Integer.MAX_VALUE, PANEL_H));
        setBackground(BG);
        setOpaque(true);

        // Repaint timer for the message fade + scanline drift. It used to run
        // forever at 10 Hz over a panel whose contents were usually static; it now
        // runs only while the newest entry is still fading and stops itself after.
        scanTimer = new javax.swing.Timer(100, e -> {
            if (!fadeInProgress()) { scanTimer.stop(); return; }
            scanOffset = (scanOffset + 1) % 4;
            repaint();
        });

        // Mouse-wheel scrolling through history
        addMouseWheelListener(e -> {
            scrollOffset = Math.max(0, scrollOffset + e.getWheelRotation());
            buffer = null;
            repaint();
        });
    }

    /** True while the newest entry's colour is still changing frame to frame. */
    private boolean fadeInProgress() {
        Entry newest = entries.peekLast();
        return newest != null && System.currentTimeMillis() - newest.time < FADE_MS;
    }

    /** Restarts the fade/scanline repaint timer if it has idled out. */
    private void pokeAnimation() {
        if (scanTimer != null && !scanTimer.isRunning()) scanTimer.start();
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Append a plain message. */
    public void log(String text) {
        log(text, Type.NORMAL);
    }

    /** Append a typed message (colour-coded). */
    public void log(String text, Type type) {
        Color c = switch (type) {
            case INFO   -> TEXT_BRIGHT;
            case LOOT   -> AMBER;
            case DANGER -> DANGER;
            case SYSTEM -> CYAN_ACC;
            case GOOD   -> GOOD;
            case DIM    -> TEXT_DIM;
            default     -> new Color(170, 195, 225);
        };
        push(text, c);
    }

    /** Show a Y/N prompt. yesAction or noAction may be null. */
    public void prompt(String question, Runnable yesAction, Runnable noAction) {
        promptText    = question;
        promptKeys    = new String[]{"Y", "N"};
        promptLabels  = new String[]{"Yes", "No"};
        promptActions = new Runnable[]{ yesAction, noAction };
        promptHighlight = 0;
        inPrompt      = true;
        buffer        = null;
        repaint();
    }

    /** Show a multi-option prompt (up to 4 options). */
    public void prompt(String question, String[] labels, Runnable[] actions) {
        promptText    = question;
        promptKeys    = new String[labels.length];
        promptLabels  = labels;
        promptActions = actions;
        for (int i = 0; i < labels.length; i++)
            promptKeys[i] = String.valueOf(i + 1);
        promptHighlight = 0;
        inPrompt      = true;
        buffer        = null;
        repaint();
    }

    /**
     * The last {@code n} lines written to the log, oldest first. Read-only; exists so a
     * playtest run can say what the game actually reported rather than guessing from the
     * numbers that changed.
     */
    public java.util.List<String> getRecentLines(int n) {
        java.util.List<String> out = new java.util.ArrayList<>();
        Entry[] arr = entries.toArray(new Entry[0]);
        for (int i = Math.max(0, arr.length - n); i < arr.length; i++) out.add(arr[i].text);
        return out;
    }

    /** Returns true if currently waiting for a prompt answer. */
    public boolean isPromptActive() { return inPrompt || inTextInput; }

    /** The question currently being asked, or null when nothing is pending. */
    public String getPromptText() {
        return inTextInput ? textInputPrompt : (inPrompt ? promptText : null);
    }

    /** Labels of the options currently offered, or null when nothing is pending. */
    public String[] getPromptLabels() { return inPrompt ? promptLabels : null; }

    /**
     * Show a free-text input prompt. callback receives the entered string,
     * or null if the user pressed Escape.
     */
    public void promptInput(String question, java.util.function.Consumer<String> callback) {
        textInputPrompt   = question;
        textInputBuffer   = new StringBuilder();
        textInputCallback = callback;
        inTextInput       = true;
        inPrompt          = false;
        buffer            = null;
        repaint();
    }

    /**
     * Route key events here when prompt is active.
     * Returns true if the key was consumed.
     */
    public boolean handleKey(KeyEvent e) {
        // Text input mode
        if (inTextInput) {
            int code = e.getKeyCode();
            if (code == KeyEvent.VK_ENTER) {
                inTextInput = false;
                buffer = null;
                String result = textInputBuffer.toString().trim();
                repaint();
                if (textInputCallback != null) textInputCallback.accept(result);
                return true;
            }
            if (code == KeyEvent.VK_ESCAPE) {
                inTextInput = false;
                buffer = null;
                repaint();
                if (textInputCallback != null) textInputCallback.accept(null);
                return true;
            }
            if (code == KeyEvent.VK_BACK_SPACE) {
                if (textInputBuffer.length() > 0)
                    textInputBuffer.deleteCharAt(textInputBuffer.length() - 1);
                buffer = null; repaint(); return true;
            }
            char c = e.getKeyChar();
            if (c != KeyEvent.CHAR_UNDEFINED && !Character.isISOControl(c) && textInputBuffer.length() < 32) {
                textInputBuffer.append(c);
                buffer = null; repaint();
            }
            return true;
        }
        if (!inPrompt) return false;

        int code = e.getKeyCode();

        // Number keys 1-9 / Y / N
        for (int i = 0; i < promptKeys.length; i++) {
            boolean match = false;
            String pk = promptKeys[i].toUpperCase();
            if (pk.equals("Y") && code == KeyEvent.VK_Y) match = true;
            if (pk.equals("N") && code == KeyEvent.VK_N) match = true;
            if (pk.equals(String.valueOf(i + 1))) {
                if (code == KeyEvent.VK_1 + i) match = true;
            }
            if (match) {
                firePromptAction(i);
                return true;
            }
        }

        // Arrow keys to move highlight, Enter to confirm
        if (code == KeyEvent.VK_LEFT || code == KeyEvent.VK_UP) {
            promptHighlight = Math.max(0, promptHighlight - 1);
            repaint(); return true;
        }
        if (code == KeyEvent.VK_RIGHT || code == KeyEvent.VK_DOWN) {
            promptHighlight = Math.min(promptKeys.length - 1, promptHighlight + 1);
            repaint(); return true;
        }
        if (code == KeyEvent.VK_ENTER) {
            firePromptAction(promptHighlight);
            return true;
        }
        // Escape = last option (usually Cancel/No)
        if (code == KeyEvent.VK_ESCAPE) {
            firePromptAction(promptActions.length - 1);
            return true;
        }
        return true; // consume all keys while prompting
    }

    private void firePromptAction(int idx) {
        inPrompt = false;
        buffer   = null;
        repaint();
        if (idx >= 0 && idx < promptActions.length && promptActions[idx] != null)
            promptActions[idx].run();
    }

    // ── Painting ──────────────────────────────────────────────────────────────
    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        int w = getWidth(), h = getHeight();

        if (buffer == null || buffer.getWidth() != w || buffer.getHeight() != h)
            buffer = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

        Graphics2D g = buffer.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Background
        g.setColor(BG);
        g.fillRect(0, 0, w, h);
        GradientPaint bgGrad = new GradientPaint(0, 0, PANEL_BG, 0, h, BG);
        g.setPaint(bgGrad);
        g.fillRect(0, 0, w, h);

        // Top border — phosphor glow strip
        g.setColor(new Color(0, 200, 80, 50));
        g.fillRect(0, 0, w, 2);
        g.setColor(BORDER_TOP);
        g.fillRect(0, 1, w, 1);

        if (inTextInput) paintTextInput(g, w, h);
        else if (inPrompt) paintPrompt(g, w, h);
        else               paintLog(g, w, h);

        // Scanlines
        g.setColor(new Color(0, 0, 0, 16));
        for (int y = scanOffset % 2; y < h; y += 2)
            g.drawLine(0, y, w, y);

        g.dispose();
        g0.drawImage(buffer, 0, 0, null);
    }

    // ── Log rendering ─────────────────────────────────────────────────────────
    // Layout is built bottom-up so the newest message is always at the bottom
    // and word-wrapped entries never push later entries off-screen.
    private void paintLog(Graphics2D g, int w, int h) {
        g.setFont(F_MSG);
        FontMetrics fm  = g.getFontMetrics();
        int lineH       = fm.getHeight() + 2;
        int padX        = 14;
        int padBot      = 6;
        int barW        = 7;   // scroll-bar track width
        int textW       = w - padX - barW - 6;

        // Build a flat list of every rendered line, oldest → newest.
        // Each element carries the parent Entry and a flag for the newest entry.
        Entry[] arr = entries.toArray(new Entry[0]);
        // lineData: { lineText, parentEntry, isFirstLineOfEntry, isNewestEntry }
        List<Object[]> allLines = new ArrayList<>();
        for (int ei = 0; ei < arr.length; ei++) {
            Entry e = arr[ei];
            List<String> wrapped = wordWrap(g, e.text, textW);
            boolean newest = (ei == arr.length - 1);
            for (int li = 0; li < wrapped.size(); li++) {
                allLines.add(new Object[]{wrapped.get(li), e, li == 0, newest});
            }
        }

        int totalLines = allLines.size();
        int maxLines   = Math.max(1, (h - padBot - 4) / lineH);

        // Clamp scroll offset so we never scroll past the oldest line
        int maxScroll = Math.max(0, totalLines - maxLines);
        scrollOffset  = Math.min(scrollOffset, maxScroll);

        // Visible window: [viewStart, viewEnd)  — scrollOffset 0 → viewEnd = totalLines
        int viewEnd   = totalLines - scrollOffset;
        int viewStart = Math.max(0, viewEnd - maxLines);

        // Draw lines bottom-up
        int baseline = h - padBot;
        for (int i = viewEnd - 1; i >= viewStart; i--) {
            if (baseline - fm.getAscent() < 4) break;

            Object[] ld           = allLines.get(i);
            String   lineText     = (String)  ld[0];
            Entry    entry        = (Entry)   ld[1];
            boolean  firstLine    = (boolean) ld[2];
            boolean  newestEntry  = (boolean) ld[3];

            long  age  = System.currentTimeMillis() - entry.time;
            float fade = Math.max(0.35f, 1f - (age / 30_000f) * 0.65f);
            Color col  = new Color(
                    (int)(entry.color.getRed()   * fade),
                    (int)(entry.color.getGreen() * fade),
                    (int)(entry.color.getBlue()  * fade));

            // Bullet on the first line of the newest entry
            if (newestEntry && firstLine) {
                g.setColor(new Color(AMBER_DIM.getRed(), AMBER_DIM.getGreen(),
                        AMBER_DIM.getBlue(), (int)(180 * fade)));
                g.drawString("\u25b8", padX - 10, baseline);
            }

            g.setColor(col);
            g.drawString(lineText, padX, baseline);
            baseline -= lineH;
        }

        // ── Scroll bar ────────────────────────────────────────────────────────
        int barX  = w - barW - 2;
        int barH  = h - 8;
        // Track
        g.setColor(new Color(15, 22, 33));
        g.fillRoundRect(barX, 4, barW - 2, barH, 3, 3);
        if (totalLines > maxLines) {
            float thumbRatio = (float) maxLines / totalLines;
            int   thumbH     = Math.max(8, (int)(barH * thumbRatio));
            // Thumb sits higher when scrolled up (offset > 0 → thumb moves up)
            float thumbFrac  = (maxScroll > 0) ? (float)(maxScroll - scrollOffset) / maxScroll : 1f;
            int   thumbY     = 4 + (int)((barH - thumbH) * thumbFrac);
            g.setColor(scrollOffset > 0 ? AMBER_DIM : new Color(38, 52, 72));
            g.fillRoundRect(barX, thumbY, barW - 2, thumbH, 3, 3);
        }

        // ── Top-right label ───────────────────────────────────────────────────
        g.setFont(F_SMALL);
        g.setColor(TEXT_DIM);
        String label = scrollOffset > 0 ? "SCROLLED \u2191 (wheel to return)" : "MESSAGE LOG";
        g.drawString(label, w - g.getFontMetrics().stringWidth(label) - barW - 6, 12);
    }

    // ── Prompt rendering ──────────────────────────────────────────────────────
    private void paintPrompt(Graphics2D g, int w, int h) {
        int padX = 14, padY = 8;

        // Question text — word-wrapped
        g.setFont(F_LABEL);
        FontMetrics fm = g.getFontMetrics();
        int textW = w - padX * 2 - fm.stringWidth("\u25b8 ");
        List<String> lines = wordWrap(g, promptText, textW);
        int lineH = fm.getHeight();
        for (int i = 0; i < lines.size(); i++) {
            String prefix = (i == 0) ? "\u25b8 " : "  ";
            int ly = padY + fm.getAscent() + i * lineH;
            // Amber glow
            g.setColor(new Color(AMBER.getRed(), AMBER.getGreen(), AMBER.getBlue(), 30));
            g.drawString(prefix + lines.get(i), padX + 1, ly + 1);
            g.setColor(AMBER);
            g.drawString(prefix + lines.get(i), padX, ly);
        }

        // Option buttons
        int btnY    = padY + lineH * lines.size() + 8;
        int btnH    = h - btnY - 10;
        int btnGap  = 12;
        int btnX    = padX;

        for (int i = 0; i < promptKeys.length; i++) {
            boolean selected = (i == promptHighlight);

            // Badge background
            String badge    = "[" + promptKeys[i] + "]";
            String label    = promptLabels[i];
            g.setFont(F_KEY);
            FontMetrics fmK = g.getFontMetrics();
            g.setFont(F_LABEL);
            FontMetrics fmL = g.getFontMetrics();

            int bw = fmK.stringWidth(badge) + fmL.stringWidth(label) + 22;

            Color btnFg = selected ? AMBER : CYAN_ACC;
            Color btnBg = selected ? new Color(40, 32, 0) : new Color(8, 16, 28);

            g.setColor(btnBg);
            g.fillRoundRect(btnX, btnY, bw, btnH, 5, 5);
            g.setColor(new Color(btnFg.getRed(), btnFg.getGreen(), btnFg.getBlue(), 160));
            g.setStroke(new BasicStroke(selected ? 1.5f : 1f));
            g.drawRoundRect(btnX, btnY, bw, btnH, 5, 5);

            int textY = btnY + (btnH + fmK.getAscent() - fmK.getDescent()) / 2;

            // Key badge
            g.setFont(F_KEY);
            g.setColor(btnFg);
            g.drawString(badge, btnX + 8, textY);

            // Label
            g.setFont(F_LABEL);
            g.setColor(selected ? TEXT_BRIGHT : new Color(140, 165, 195));
            g.drawString(label, btnX + 8 + fmK.stringWidth(badge) + 6, textY);

            btnX += bw + btnGap;
        }

        // Dim hint at right edge
        g.setFont(F_SMALL);
        g.setColor(TEXT_DIM);
        String hint = "Arrows to select  \u00b7  Enter to confirm  \u00b7  Esc to cancel";
        g.drawString(hint, w - g.getFontMetrics().stringWidth(hint) - 10, h - 6);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private void paintTextInput(Graphics2D g, int w, int h) {
        // Background
        g.setColor(new Color(8, 10, 18));
        g.fillRect(0, 0, w, h);
        g.setColor(new Color(40, 55, 75));
        g.drawLine(0, 0, w, 0);

        int midY = h / 2;
        int padX = 12;

        // Question text
        g.setFont(new Font("Monospaced", Font.BOLD, 12));
        FontMetrics fm = g.getFontMetrics();
        g.setColor(new Color(255, 200, 50)); // amber
        g.drawString(textInputPrompt, padX, midY - 4);

        // Input field
        String display = "> " + textInputBuffer.toString() + "_";
        g.setFont(new Font("Monospaced", Font.PLAIN, 12));
        g.setColor(new Color(0, 255, 120)); // phosphor
        g.drawString(display, padX, midY + fm.getHeight());

        // Hint
        g.setFont(new Font("Monospaced", Font.PLAIN, 10));
        g.setColor(new Color(80, 105, 135));
        g.drawString("[Enter] Confirm  [Esc] Cancel", w - 200, midY + fm.getHeight());
    }


    private void push(String text, Color col) {
        entries.addLast(new Entry(text, col));
        while (entries.size() > MAX_ENTRIES) entries.removeFirst();
        scrollOffset = 0;   // auto-scroll to newest message
        buffer = null;
        pokeAnimation();    // a fresh entry has 30 s of fade to animate
        repaint();
    }

    private List<String> wordWrap(Graphics2D g, String text, int maxPx) {
        List<String> lines = new ArrayList<>();
        FontMetrics fm = g.getFontMetrics();
        if (fm.stringWidth(text) <= maxPx) { lines.add(text); return lines; }
        String[] words = text.split(" ");
        StringBuilder cur = new StringBuilder();
        for (String word : words) {
            String test = cur.isEmpty() ? word : cur + " " + word;
            if (fm.stringWidth(test) <= maxPx) {
                cur = new StringBuilder(test);
            } else {
                if (!cur.isEmpty()) lines.add(cur.toString());
                cur = new StringBuilder(word);
            }
        }
        if (!cur.isEmpty()) lines.add(cur.toString());
        return lines;
    }

    /** Stop the scan timer when this panel is replaced (e.g. on game reload). */
    public void dispose() {
        if (scanTimer != null) scanTimer.stop();
    }
}

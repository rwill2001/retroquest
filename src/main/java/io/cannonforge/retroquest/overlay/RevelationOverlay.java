package io.cannonforge.retroquest.overlay;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.util.List;

import io.cannonforge.retroquest.core.Fonts;
import io.cannonforge.retroquest.core.SoundManager;

/**
 * A cinematic "something speaks to you" card: title, subtitle, an accent colour, a body
 * revealed one character at a time, and an optional footer panel that fades in only once
 * the speaker has finished.
 *
 * <p>This is the presentation half of {@link DivineAudienceOverlay}, lifted out so it can be
 * used by anything with something to say. The god-specific parts of that overlay — which god,
 * their colour, the standing-with-the-seven panel — are now supplied by the caller: the god's
 * name is the title, their domain the subtitle, and the standing panel is a {@link Footer}.
 *
 * <p>The reason it is worth sharing rather than copying is the timing. The typewriter, the
 * click-to-skip, the beat between the last character and the footer appearing, and the fact
 * that {@code Enter} does nothing until the text is done — those are what make the screen feel
 * like a scene instead of a dialog box, and they are fiddly enough that a second copy would
 * drift out of step with the first.
 *
 * <p>Callers own what happens next: {@link #close()} fires {@code onDismiss}, which is how the
 * divine audience defers its teleport and how a boss revelation returns the player to the
 * dungeon floor.
 */
public class RevelationOverlay {

    /**
     * An optional panel drawn along the foot of the card, after the body has finished
     * revealing. It fades in on its own so the reader is not handed two things at once.
     */
    public interface Footer {
        /** Height this footer needs. Called before layout, so it may not paint anything. */
        int height(Graphics2D g);
        /**
         * Paints the footer.
         *
         * @param top   y of the footer's first pixel
         * @param alpha 0..1 fade-in progress; multiply every colour by it
         */
        void paint(Graphics2D g, int cardX, int cardW, int top, float alpha);
    }

    // ── Timing and type ─────────────────────────────────────────────────────
    private static final long ENTRY_MS      = 400;
    private static final int  CHARS_PER_SEC = 30;
    private static final long CHAR_DELAY_MS = 1000 / CHARS_PER_SEC;
    private static final long CURSOR_BLINK  = 500;
    /** Beat between the last character and the footer, so they do not arrive together. */
    private static final long FOOTER_FADE_MS = 450;

    private static final Font F_TITLE = Fonts.monoBold(20);
    private static final Font F_SUB   = OverlayTheme.F_DESC;
    private static final Font F_BODY  = OverlayTheme.F_ITEM;

    // ── State ───────────────────────────────────────────────────────────────
    private boolean  active;
    private long     entryTime;
    /** Wall time the typewriter finished; drives the footer fade. 0 until then. */
    private long     revealTime;

    private String   title    = "";
    private String   subtitle = "";
    private Color    accent   = OverlayTheme.CYAN_ACC;
    private String   fullText = "";
    private int      revealedChars;
    private long     lastCharTime;
    private boolean  textFullyRevealed;
    private Footer   footer;
    private Runnable onDismiss;

    // ── Lifecycle ───────────────────────────────────────────────────────────

    /**
     * Opens the card.
     *
     * @param title     the speaker, drawn large in the accent colour
     * @param subtitle  a line under the title; may be null or blank
     * @param accent    tints the border, title, cursor and card gradient
     * @param body      the message; revealed one character at a time
     * @param footer    optional panel along the foot; may be null
     * @param onDismiss run when the player dismisses the card; may be null
     */
    public void open(String title, String subtitle, Color accent, String body,
                     Footer footer, Runnable onDismiss) {
        this.title             = title    != null ? title    : "";
        this.subtitle          = subtitle != null ? subtitle : "";
        this.accent            = accent   != null ? accent   : OverlayTheme.CYAN_ACC;
        this.fullText          = body     != null ? body     : "";
        this.footer            = footer;
        this.onDismiss         = onDismiss;
        this.revealedChars     = 0;
        this.lastCharTime      = System.currentTimeMillis();
        this.textFullyRevealed = false;
        this.entryTime         = System.currentTimeMillis();
        this.revealTime        = 0;
        this.active            = true;
        SoundManager.getInstance().playAltarChime();
    }

    public void close() {
        active = false;
        Runnable cb = onDismiss;
        onDismiss = null;
        if (cb != null) cb.run();
    }

    public boolean isActive() { return active; }

    // ── Input ───────────────────────────────────────────────────────────────

    /** First press skips the typewriter; only once the text is done does Enter dismiss. */
    public void handleKey(KeyEvent e) {
        if (!active) return;
        if (!textFullyRevealed) {
            revealedChars     = fullText.length();
            textFullyRevealed = true;
            return;
        }
        int code = e.getKeyCode();
        if (code == KeyEvent.VK_ENTER || code == KeyEvent.VK_ESCAPE
                || code == KeyEvent.VK_SPACE) {
            close();
        }
    }

    public void handleClick(int mx, int my) {
        if (!active) return;
        if (!textFullyRevealed) {
            revealedChars     = fullText.length();
            textFullyRevealed = true;
            return;
        }
        close();
    }

    // ── Rendering ───────────────────────────────────────────────────────────

    public void paint(Graphics2D g, int W, int H) {
        try { doPaint(g, W, H); }
        catch (Exception ignored) { /* never crash the render loop */ }
    }

    private void doPaint(Graphics2D g, int W, int H) {
        if (!active) return;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                           RenderingHints.VALUE_ANTIALIAS_ON);

        float ease = OverlayTheme.entryEase(entryTime, ENTRY_MS);
        long  now  = System.currentTimeMillis();

        // ── Advance typewriter ───────────────────────────────────────────────
        if (!textFullyRevealed && revealedChars < fullText.length()) {
            int prev = revealedChars;
            while (now - lastCharTime >= CHAR_DELAY_MS && revealedChars < fullText.length()) {
                revealedChars++;
                lastCharTime += CHAR_DELAY_MS;
            }
            if (revealedChars > prev) {
                char c = fullText.charAt(revealedChars - 1);
                if (c != ' ' && c != '\n') SoundManager.getInstance().play("textblip");
            }
            if (revealedChars >= fullText.length()) textFullyRevealed = true;
        }
        if (textFullyRevealed && revealTime == 0) revealTime = now;

        // ── Full-screen dim ──────────────────────────────────────────────────
        g.setColor(new Color(0, 0, 0, (int)(200 * ease)));
        g.fillRect(0, 0, W, H);

        // ── Card geometry ────────────────────────────────────────────────────
        // Measured from the WHOLE message, not the part revealed so far, so the card is the
        // size it will end up being from the first character. Sizing to the visible prefix
        // would grow the box under the reader as it types, which is worse than dead space.
        FontMetrics fmName = g.getFontMetrics(F_TITLE);
        FontMetrics fmSub  = g.getFontMetrics(F_SUB);
        FontMetrics fmBody = g.getFontMetrics(F_BODY);

        int footerH = (footer != null) ? Math.max(0, footer.height(g)) : 0;
        int cardW   = Math.min(OverlayTheme.scaled(600), W - OverlayTheme.scaled(50));
        int bodyMaxW = cardW - 48;
        int bodyH   = Math.max(1, OverlayTheme.wordWrap(fmBody, fullText, bodyMaxW).size())
                    * fmBody.getHeight();

        int headH = OverlayTheme.scaled(10) + fmName.getHeight()
                  + (subtitle.isBlank() ? 0 : fmSub.getHeight() + 2)
                  + 10;                                   // gap down to the divider
        int barH  = OverlayTheme.scaled(26);
        int cardH = headH + 16 + bodyH + OverlayTheme.scaled(18)
                  + (footerH > 0 ? footerH + OverlayTheme.scaled(10) : 0)
                  + barH + OverlayTheme.scaled(12);
        cardH = Math.min(cardH, H - OverlayTheme.scaled(40));

        int cx = (W - cardW) / 2;
        int cy = (int)((H - cardH) / 2 - OverlayTheme.scaled(20) * (1f - ease));

        // ── Card background (gradient from dark accent tint to BG) ───────────
        Color darkTint = new Color(accent.getRed() / 10, accent.getGreen() / 10, accent.getBlue() / 10);
        g.setPaint(new GradientPaint(cx, cy, darkTint, cx, cy + cardH, OverlayTheme.BG));
        g.fillRoundRect(cx, cy, cardW, cardH, 12, 12);

        g.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 160));
        g.setStroke(new BasicStroke(1.5f));
        g.drawRoundRect(cx, cy, cardW, cardH, 12, 12);

        // ── Title ────────────────────────────────────────────────────────────
        g.setFont(F_TITLE);
        int nameY = cy + fmName.getAscent() + OverlayTheme.scaled(10);
        g.setColor(accent);
        g.drawString(title, cx + (cardW - fmName.stringWidth(title)) / 2, nameY);

        // ── Subtitle ─────────────────────────────────────────────────────────
        int subY = nameY;
        if (!subtitle.isBlank()) {
            g.setFont(F_SUB);
            subY = nameY + fmSub.getHeight() + 2;
            g.setColor(OverlayTheme.TEXT_DIM);
            g.drawString(subtitle, cx + (cardW - fmSub.stringWidth(subtitle)) / 2, subY);
        }

        // ── Divider ──────────────────────────────────────────────────────────
        int divY = subY + 10;
        g.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 80));
        g.setStroke(new BasicStroke(1f));
        g.drawLine(cx + 20, divY, cx + cardW - 20, divY);

        // ── Body (typewriter) ────────────────────────────────────────────────
        g.setFont(F_BODY);
        int bodyX = cx + 24;
        int bodyY = divY + 16;
        String visible = fullText.substring(0, revealedChars);
        List<String> lines = OverlayTheme.wordWrap(fmBody, visible, bodyMaxW);

        g.setColor(OverlayTheme.TEXT_BRIGHT);
        int lineH = fmBody.getHeight();
        for (int i = 0; i < lines.size(); i++) {
            g.drawString(lines.get(i), bodyX, bodyY + i * lineH);
        }

        // ── Blinking cursor while still speaking ─────────────────────────────
        if (!textFullyRevealed) {
            boolean blink = ((now - entryTime) / CURSOR_BLINK) % 2 == 0;
            if (blink && !lines.isEmpty()) {
                String last = lines.get(lines.size() - 1);
                int cursorX = bodyX + fmBody.stringWidth(last);
                int cursorY = bodyY + (lines.size() - 1) * lineH;
                g.setColor(accent);
                g.fillRect(cursorX + 2, cursorY - fmBody.getAscent() + 2, 2, fmBody.getHeight() - 2);
            }
        }

        // ── Footer, once the speaker has finished ────────────────────────────
        int barY = cy + cardH - OverlayTheme.scaled(30);
        if (footer != null && revealTime > 0) {
            float alpha = Math.min(1f, (float)(now - revealTime) / FOOTER_FADE_MS);
            footer.paint(g, cx, cardW, barY - OverlayTheme.scaled(6) - footerH, alpha);
        }

        // ── Continue prompt ──────────────────────────────────────────────────
        if (textFullyRevealed) {
            OverlayTheme.paintKeyBadges(g, cx, barY, cardW, OverlayTheme.scaled(26),
                new String[][] { { "Enter", "Continue" } }, accent);
        }
    }

    /** Clamps a faded alpha to a legal channel value. Shared with footer implementors. */
    public static int clampAlpha(float v) {
        return Math.max(0, Math.min(255, (int) v));
    }

    /**
     * A plain footer: a heading in the accent colour and a few short lines under it.
     *
     * <p>This is the shape a boss revelation uses — the heading names the thing you now know
     * and the lines are the bare facts of it, so the panel reads as something written down
     * rather than a rewards summary. Empty headings and null lists are tolerated; a footer
     * with nothing in it reports zero height and the card closes up around it.
     */
    public static final class LinesFooter implements Footer {
        private static final Font F_HEAD = Fonts.monoBold(10);
        private static final Font F_LINE = Fonts.mono    (10);

        private final String       heading;
        private final List<String> lines;
        private final Color        accent;

        public LinesFooter(String heading, List<String> lines, Color accent) {
            this.heading = heading != null ? heading : "";
            this.lines   = lines   != null ? lines   : List.of();
            this.accent  = accent  != null ? accent  : OverlayTheme.CYAN_ACC;
        }

        @Override
        public int height(Graphics2D g) {
            if (heading.isBlank() && lines.isEmpty()) return 0;
            FontMetrics fmHead = g.getFontMetrics(F_HEAD);
            FontMetrics fmLine = g.getFontMetrics(F_LINE);
            int h = heading.isBlank() ? 0 : fmHead.getHeight() + OverlayTheme.scaled(6);
            return h + lines.size() * (fmLine.getHeight() + OverlayTheme.scaled(2))
                     + OverlayTheme.scaled(8);
        }

        @Override
        public void paint(Graphics2D g, int cardX, int cardW, int top, float alpha) {
            if (heading.isBlank() && lines.isEmpty()) return;
            int pad = OverlayTheme.scaled(24);

            g.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(),
                                 clampAlpha(60 * alpha)));
            g.setStroke(new BasicStroke(1f));
            g.drawLine(cardX + OverlayTheme.scaled(20), top - OverlayTheme.scaled(6),
                       cardX + cardW - OverlayTheme.scaled(20), top - OverlayTheme.scaled(6));

            int y = top;
            if (!heading.isBlank()) {
                g.setFont(F_HEAD);
                FontMetrics fmHead = g.getFontMetrics();
                g.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(),
                                     clampAlpha(200 * alpha)));
                g.drawString(heading, cardX + pad, y + fmHead.getAscent());
                y += fmHead.getHeight() + OverlayTheme.scaled(6);
            }

            g.setFont(F_LINE);
            FontMetrics fmLine = g.getFontMetrics();
            int rowH = fmLine.getHeight() + OverlayTheme.scaled(2);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                if (line == null) continue;
                g.setColor(new Color(OverlayTheme.TEXT_BRIGHT.getRed(),
                                     OverlayTheme.TEXT_BRIGHT.getGreen(),
                                     OverlayTheme.TEXT_BRIGHT.getBlue(),
                                     clampAlpha(190 * alpha)));
                g.drawString("· " + line, cardX + pad, y + i * rowH + fmLine.getAscent());
            }
        }
    }
}

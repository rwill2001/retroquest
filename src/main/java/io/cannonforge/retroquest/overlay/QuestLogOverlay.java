package io.cannonforge.retroquest.overlay;
import static io.cannonforge.retroquest.overlay.OverlayTheme.AMBER;
import static io.cannonforge.retroquest.overlay.OverlayTheme.BG;
import static io.cannonforge.retroquest.overlay.OverlayTheme.BORDER_COL;
import static io.cannonforge.retroquest.overlay.OverlayTheme.CYAN_ACC;
import static io.cannonforge.retroquest.overlay.OverlayTheme.F_DESC;
import static io.cannonforge.retroquest.overlay.OverlayTheme.F_ITEM;
import static io.cannonforge.retroquest.overlay.OverlayTheme.F_KEY;
import static io.cannonforge.retroquest.overlay.OverlayTheme.F_LABEL;
import static io.cannonforge.retroquest.overlay.OverlayTheme.F_SMALL;
import static io.cannonforge.retroquest.overlay.OverlayTheme.F_TITLE;
import static io.cannonforge.retroquest.overlay.OverlayTheme.GOOD;
import static io.cannonforge.retroquest.overlay.OverlayTheme.PANEL_BG;
import static io.cannonforge.retroquest.overlay.OverlayTheme.PHOSPHOR;
import static io.cannonforge.retroquest.overlay.OverlayTheme.ROW_ALT;
import static io.cannonforge.retroquest.overlay.OverlayTheme.TEXT_BRIGHT;
import static io.cannonforge.retroquest.overlay.OverlayTheme.TEXT_DIM;
import static io.cannonforge.retroquest.overlay.OverlayTheme.entryEase;
import static io.cannonforge.retroquest.overlay.OverlayTheme.paintScrollIndicators;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.cannonforge.retroquest.core.Retroquest;
import io.cannonforge.retroquest.model.Quest;

/**
 * In-panel quest log overlay.
 * Slides in from the right. Left: quest list. Right: quest details.
 *
 * <p>The details pane names the quest giver (and the town they stand in) and
 * states what actually finishes the quest. Only DELIVER quests are handed in:
 * {@code Player.progressQuest()} calls {@code completeQuest()} the moment a KILL,
 * COLLECT, TALK or EXPLORE counter fills, so those close themselves in the field
 * and must not be advertised as needing a trip back to town.
 */
public class QuestLogOverlay {

    private static final Color SEL_BG = new Color(0, 40, 55);

    // ── Quest-giver → town index ────────────────────────────────────────────────
    // Quest only stores giverName, so the town is recovered by scanning the town
    // files for the NPC. Built once, off the EDT; until it lands the pane simply
    // shows the giver's name with no town.

    private static final String TOWNS_DIR = "data/towns";
    /** NPC "name" keys sit at exactly six spaces of indent; dialogue-tree keys are deeper. */
    private static final Pattern NPC_NAME = Pattern.compile("(?m)^ {6}\"name\": \"([^\"]+)\"");

    private static volatile Map<String, String> giverTowns = null;
    private static volatile boolean indexStarted = false;

    private final Retroquest game;
    private boolean active    = false;
    private int     selected  = 0;
    private int     scrollTop = 0;
    private long    entryTime = 0;
    private static final long ENTRY_MS = 280;

    // Flat list of all quests (active first, then completed)
    private record QuestEntry(Quest quest, boolean turnedIn) {}
    private final List<QuestEntry> entries = new ArrayList<>();

    // Cached layout. Section headers shift rows around, so each painted row's
    // rectangle is recorded rather than derived from a fixed row pitch.
    private Rectangle lastCardRect = new Rectangle();
    private final List<Rectangle> lastRowRects   = new ArrayList<>();
    private final List<Integer>   lastRowIndices = new ArrayList<>();
    private java.awt.Rectangle[] lastKeyBarRects = new java.awt.Rectangle[0];

    public QuestLogOverlay(Retroquest game) {
        this.game = game;
    }

    public boolean isActive() { return active; }

    public void open() {
        buildQuestList();
        ensureTownIndex();
        selected  = 0;
        scrollTop = 0;
        entryTime = System.currentTimeMillis();
        active    = true;
    }

    /** Kicks off the one-time town scan on a daemon thread; cheap no-op afterwards. */
    private static void ensureTownIndex() {
        if (indexStarted) return;
        indexStarted = true;
        Thread t = new Thread(QuestLogOverlay::buildTownIndex, "quest-giver-town-index");
        t.setDaemon(true);
        t.start();
    }

    private static void buildTownIndex() {
        Map<String, String> map = new HashMap<>();
        File[] files = new File(TOWNS_DIR).listFiles((d, n) -> n.toLowerCase().endsWith(".rfmap"));
        if (files != null) {
            for (File f : files) {
                try {
                    String text  = Files.readString(f.toPath(), StandardCharsets.UTF_8);
                    int npcIdx   = text.indexOf("\"npcs\"");
                    if (npcIdx < 0) continue;
                    String town  = prettyTownName(f.getName());
                    Matcher m    = NPC_NAME.matcher(text.substring(npcIdx));
                    while (m.find()) map.putIfAbsent(m.group(1), town);
                } catch (Exception ignored) {
                    // A town file that will not read simply contributes no givers.
                }
            }
        }
        giverTowns = map;
    }

    /** "amber_grove.rfmap" → "Amber Grove". */
    private static String prettyTownName(String fileName) {
        String base = fileName.replaceAll("(?i)\\.rfmap$", "").replace('_', ' ').trim();
        StringBuilder sb = new StringBuilder(base.length());
        boolean startOfWord = true;
        for (char c : base.toCharArray()) {
            sb.append(startOfWord ? Character.toUpperCase(c) : c);
            startOfWord = (c == ' ');
        }
        return sb.toString();
    }

    /** Town the named giver stands in, or null if unknown / not indexed yet. */
    private static String townOf(String giverName) {
        Map<String, String> idx = giverTowns;
        if (idx == null || giverName == null) return null;
        return idx.get(giverName);
    }

    // ── Quest guidance ────────────────────────────────────────────────────────

    /** Only DELIVER quests are handed in; everything else closes itself in the field. */
    private static boolean needsTurnIn(Quest q) {
        return q.getType() == Quest.Type.DELIVER;
    }

    /** One accurate line telling the player what actually finishes this quest. */
    private static String nextStep(Quest q) {
        String target = q.getTarget() != null ? q.getTarget() : "the objective";
        int left = Math.max(0, q.getRequiredAmount() - q.getProgress());
        return switch (q.getType()) {
            case KILL    -> "Kill " + left + " more " + target
                          + ". Closes itself the moment the last one falls \u2014 no need to report back.";
            case COLLECT -> "Pick up " + left + " more " + target
                          + ". Closes itself as soon as the last one is in your pack.";
            case TALK    -> "Speak to " + target + ". Closes itself the moment you do.";
            case EXPLORE -> "Reach " + target + ". Closes itself as soon as you arrive.";
            case DELIVER -> {
                String where = townOf(target);
                yield "Carry the goods to " + target
                    + (where != null ? " in " + where : "")
                    + " and talk to them \u2014 this one must be handed over in person.";
            }
            default -> "Keep at it.";
        };
    }

    public void close() { active = false; }

    private void buildQuestList() {
        entries.clear();
        for (Quest q : game.getPlayer().getActiveQuests()) entries.add(new QuestEntry(q, false));
        for (Quest q : game.getPlayer().getCompletedQuests()) entries.add(new QuestEntry(q, true));
    }

    // ── Input ─────────────────────────────────────────────────────────────────

    public void handleKey(KeyEvent e) {
        if (!active) return;
        switch (e.getKeyCode()) {
            case KeyEvent.VK_ESCAPE, KeyEvent.VK_L -> close();
            case KeyEvent.VK_UP,   KeyEvent.VK_K   -> { if (selected > 0) { selected--; clampScroll(); } }
            case KeyEvent.VK_DOWN, KeyEvent.VK_J   -> { if (selected < entries.size() - 1) { selected++; clampScroll(); } }
        }
    }

    public void handleClick(int mx, int my) {
        if (!active) return;
        if (!lastCardRect.contains(mx, my)) return;   // clicks outside the panel do nothing

        // Key bar buttons
        for (Rectangle lastKeyBarRect : lastKeyBarRects) {
            if (lastKeyBarRect.contains(mx, my)) {
                close();
                return;
            }
        }

        // Quest list rows
        for (int i = 0; i < lastRowRects.size(); i++) {
            if (lastRowRects.get(i).contains(mx, my)) {
                selected = lastRowIndices.get(i);
                clampScroll();
                return;
            }
        }
    }

    private void clampScroll() {
        if (selected < scrollTop) scrollTop = selected;
    }

    // ── Paint ─────────────────────────────────────────────────────────────────

    public void paint(Graphics2D g, int W, int H) {
        if (!active) return;
        try { doPaint(g, W, H); } catch (Exception ex) { ex.printStackTrace(); active = false; }
    }

    private void doPaint(Graphics2D g, int W, int H) {
        float ease = entryEase(entryTime, ENTRY_MS);

        // Dim
        g.setColor(new Color(0, 0, 0, (int)(180 * ease)));
        g.fillRect(0, 0, W, H);

        int cW = Math.min(OverlayTheme.scaled(740), W - 40);
        int cH = Math.min(OverlayTheme.scaled(480), H - 40);
        int cX = (int)((W - cW) / 2 * ease + (1f - ease) * W);  // slide from right
        int cY = (H - cH) / 2;

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Shadow
        g.setColor(new Color(0, 0, 0, (int)(120 * ease)));
        g.fillRoundRect(cX + 4, cY + 4, cW, cH, 6, 6);

        // Card
        g.setPaint(new GradientPaint(cX, cY, new Color(8, 14, 20), cX, cY + cH, BG));
        g.fillRoundRect(cX, cY, cW, cH, 6, 6);

        // Border — amber for quest log
        g.setStroke(new BasicStroke(1.5f));
        g.setColor(new Color(AMBER.getRed(), AMBER.getGreen(), AMBER.getBlue(), 160));
        g.drawRoundRect(cX, cY, cW, cH, 6, 6);
        g.setStroke(new BasicStroke(1f));

        int titleH  = OverlayTheme.scaled(36);
        int keybarH = OverlayTheme.scaled(34);
        int bodyH   = cH - titleH - keybarH;
        int splitX  = cW * 42 / 100;  // list takes 42%

        lastCardRect = new Rectangle(cX, cY, cW, cH);

        paintTitle  (g, cX, cY,                        cW, titleH);
        paintList   (g, cX, cY + titleH,               splitX, bodyH);
        paintDetails(g, cX + splitX, cY + titleH,      cW - splitX, bodyH);
        paintKeyBar (g, cX, cY + titleH + bodyH,        cW, keybarH);
    }

    // ── TITLE BAR ─────────────────────────────────────────────────────────────

    private void paintTitle(Graphics2D g, int x, int y, int w, int h) {
        g.setPaint(new GradientPaint(x, y, new Color(20, 16, 4), x + w, y, new Color(8, 12, 16)));
        g.fillRoundRect(x, y, w, h, 6, 6);
        g.fillRect(x, y + h / 2, w, h / 2);
        g.setColor(BORDER_COL);
        g.drawLine(x, y + h, x + w, y + h);

        g.setFont(F_TITLE);
        FontMetrics fm = g.getFontMetrics();
        int ty = y + (h + fm.getAscent() - fm.getDescent()) / 2;

        g.setColor(new Color(AMBER.getRed(), AMBER.getGreen(), AMBER.getBlue(), 35));
        g.drawString("\u25c8  QUEST JOURNAL", x + 15, ty + 1);
        g.setColor(AMBER);
        g.drawString("\u25c8  QUEST JOURNAL", x + 14, ty);

        // Right: counts
        g.setFont(F_SMALL);
        FontMetrics fmS = g.getFontMetrics();
        long activeCount = game.getPlayer().getActiveQuests().size();
        long done        = game.getPlayer().getCompletedQuests().size();
        String cnt       = "Active: " + activeCount + "   Done: " + done;
        g.setColor(TEXT_DIM);
        g.drawString(cnt, x + w - fmS.stringWidth(cnt) - 14, ty);
    }

    // ── QUEST LIST ────────────────────────────────────────────────────────────

    private void paintList(Graphics2D g, int x, int y, int w, int h) {
        g.setColor(BG);
        g.fillRect(x, y, w, h);
        g.setColor(BORDER_COL);
        g.drawLine(x + w, y, x + w, y + h);

        g.setFont(F_ITEM);
        FontMetrics fm = g.getFontMetrics();
        int rowH    = fm.getHeight() + OverlayTheme.scaled(6);
        int maxRows = Math.max(1, (h - 6) / rowH);

        if (selected >= scrollTop + maxRows) scrollTop = selected - maxRows + 1;
        if (selected <  scrollTop)           scrollTop = selected;
        int maxScroll = Math.max(0, entries.size() - maxRows);
        scrollTop = Math.max(0, Math.min(scrollTop, maxScroll));

        lastRowRects.clear();
        lastRowIndices.clear();

        if (entries.isEmpty()) {
            g.setColor(TEXT_DIM);
            g.setFont(F_DESC);
            g.drawString("No quests yet.", x + 12, y + 28);
            return;
        }

        // Section header tracking
        boolean shownActiveHeader    = false;
        boolean shownCompletedHeader = false;

        int dy = y + 4;
        for (int i = scrollTop; i < entries.size() && i < scrollTop + maxRows; i++) {
            Quest q   = entries.get(i).quest();
            boolean completed = entries.get(i).turnedIn();
            boolean sel = (i == selected);

            // Section divider
            if (!completed && !shownActiveHeader) {
                shownActiveHeader = true;
                g.setFont(F_KEY);
                g.setColor(TEXT_DIM);
                g.drawString("ACTIVE", x + 8, dy + g.getFontMetrics().getAscent());
                dy += g.getFontMetrics().getHeight() + 2;
            } else if (completed && !shownCompletedHeader) {
                shownCompletedHeader = true;
                g.setFont(F_KEY);
                g.setColor(TEXT_DIM);
                g.drawString("COMPLETED", x + 8, dy + g.getFontMetrics().getAscent());
                dy += g.getFontMetrics().getHeight() + 2;
            }

            g.setFont(F_ITEM);
            fm = g.getFontMetrics();

            // Row bg
            g.setColor(sel ? SEL_BG : (i % 2 == 0 ? BG : ROW_ALT));
            g.fillRect(x, dy, w, rowH);
            lastRowRects.add(new Rectangle(x, dy, w, rowH));
            lastRowIndices.add(i);

            // Accent bar
            if (sel) { g.setColor(AMBER); g.fillRect(x, dy, 3, rowH); }

            int textY = dy + rowH - (rowH - fm.getAscent()) / 2 - 1;

            // The "ready" marker only ever applies to DELIVER; every other type
            // closes itself out in the field once its counter fills.
            boolean awaitingTurnIn = !completed && q.isComplete() && needsTurnIn(q);
            // Status icon
            String icon = completed ? "\u2713"                        // ✓ done
                : awaitingTurnIn    ? "\u25ce"                        // ◎ ready to turn in
                :                     "\u25cf";                       // ● active
            Color iconCol = completed ? GOOD
                : awaitingTurnIn    ? AMBER
                :                     CYAN_ACC;
            g.setColor(sel ? TEXT_BRIGHT : iconCol);
            g.drawString(icon, x + 8, textY);
            int iconW = fm.stringWidth(icon) + 8;

            // Quest title
            String title = q.getTitle();
            int maxW = w - iconW - 16;
            while (fm.stringWidth(title) > maxW && title.length() > 1)
                title = title.substring(0, title.length() - 1);
            if (fm.stringWidth(q.getTitle()) > maxW) title += "\u2026";

            g.setColor(sel ? TEXT_BRIGHT : (completed ? TEXT_DIM : TEXT_BRIGHT));
            g.drawString(title, x + iconW + 8, textY);

            // "HAND IN" badge — only for the one type that genuinely needs a trip back
            if (awaitingTurnIn) {
                g.setFont(F_SMALL);
                FontMetrics fmSm = g.getFontMetrics();
                String badge = "HAND IN";
                int bx = x + w - fmSm.stringWidth(badge) - 8;
                g.setColor(new Color(AMBER.getRed(), AMBER.getGreen(), AMBER.getBlue(), 60));
                g.fillRoundRect(bx - 3, dy + 2, fmSm.stringWidth(badge) + 6, rowH - 4, 3, 3);
                g.setColor(AMBER);
                g.drawString(badge, bx, textY);
                g.setFont(F_ITEM);
                fm = g.getFontMetrics();
            }

            dy += rowH;
        }

        paintScrollIndicators(g, scrollTop, maxRows, entries.size(), x, y, w, h);
    }

    // ── DETAILS PANE ─────────────────────────────────────────────────────────

    private void paintDetails(Graphics2D g, int x, int y, int w, int h) {
        g.setColor(PANEL_BG);
        g.fillRect(x, y, w, h);

        if (entries.isEmpty() || selected >= entries.size()) {
            g.setFont(F_DESC);
            g.setColor(TEXT_DIM);
            g.drawString("Select a quest to view details.", x + 14, y + 28);
            return;
        }

        Quest q    = entries.get(selected).quest();
        boolean done = entries.get(selected).turnedIn();
        int pad  = 14;
        int dy   = y + pad;

        // Quest title
        g.setFont(F_LABEL);
        FontMetrics fm = g.getFontMetrics();
        g.setColor(done ? GOOD : q.isComplete() ? AMBER : CYAN_ACC);
        String title = q.getTitle();
        dy += fm.getAscent();
        g.drawString(title, x + pad, dy);
        dy += fm.getDescent() + 6;

        // Status badge
        boolean awaitingTurnIn = !done && q.isComplete() && needsTurnIn(q);
        g.setFont(F_SMALL);
        FontMetrics fmS = g.getFontMetrics();
        String status = done ? "COMPLETED" : awaitingTurnIn ? "READY TO HAND IN" : "IN PROGRESS";
        Color statusCol = done ? GOOD : awaitingTurnIn ? AMBER : CYAN_ACC;
        g.setColor(new Color(statusCol.getRed(), statusCol.getGreen(), statusCol.getBlue(), 40));
        g.fillRoundRect(x + pad, dy, fmS.stringWidth(status) + 10, fmS.getHeight() + 4, 3, 3);
        g.setColor(statusCol);
        g.drawString(status, x + pad + 5, dy + fmS.getAscent() + 2);
        dy += fmS.getHeight() + 10;

        // Who gave it, and where they stand. Quest carries only the name, so the
        // town comes from the background index and may not be resolved yet.
        String giver = q.getGiverName();
        if (giver != null && !giver.isBlank()) {
            String town = townOf(giver);
            g.setFont(F_SMALL);
            g.setColor(TEXT_DIM);
            g.drawString("Given by " + giver + (town != null ? "  \u00b7  " + town : ""),
                         x + pad, dy + fmS.getAscent());
            dy += fmS.getHeight() + 6;
        }

        // Divider
        g.setColor(BORDER_COL);
        g.drawLine(x + pad, dy, x + w - pad, dy);
        dy += 10;

        // Reserve enough room below for progress, next step and rewards so the
        // description can never push them off the card.
        g.setFont(F_DESC);
        FontMetrics fmD = g.getFontMetrics();
        int reserve = done ? fmD.getHeight() * 6 + OverlayTheme.scaled(40)
                           : fmD.getHeight() * 11 + OverlayTheme.scaled(50);

        // Description — word wrapped
        for (String line : OverlayTheme.wordWrap(fmD, q.getDescription() != null ? q.getDescription() : "", w - pad * 2)) {
            if (dy + fmD.getAscent() > y + h - reserve) break;
            g.setColor(TEXT_BRIGHT);
            g.drawString(line, x + pad, dy + fmD.getAscent());
            dy += fmD.getHeight() + 1;
        }
        dy += 10;

        // Progress + what actually finishes this quest
        if (!done) {
            g.setFont(F_KEY);
            FontMetrics fmK = g.getFontMetrics();
            g.setColor(TEXT_DIM);
            g.drawString("Progress", x + pad, dy + fmK.getAscent());
            dy += fmK.getHeight() + 4;

            g.setFont(F_DESC);
            g.setColor(awaitingTurnIn ? AMBER : PHOSPHOR);
            g.drawString(q.getProgressText(), x + pad, dy + fmD.getAscent());
            dy += fmD.getHeight() + 8;

            g.setFont(F_KEY);
            g.setColor(TEXT_DIM);
            g.drawString("Next step", x + pad, dy + fmK.getAscent());
            dy += fmK.getHeight() + 4;

            g.setFont(F_DESC);
            String step = awaitingTurnIn ? nextStep(q)
                        : q.isComplete() ? "Done \u2014 speak to " + (giver != null ? giver : "the giver")
                                           + " to claim the reward."
                        : nextStep(q);
            for (String line : OverlayTheme.wordWrap(fmD, step, w - pad * 2)) {
                g.setColor(TEXT_BRIGHT);
                g.drawString(line, x + pad, dy + fmD.getAscent());
                dy += fmD.getHeight() + 1;
            }
            dy += 10;
        }

        // Divider
        g.setColor(BORDER_COL);
        g.drawLine(x + pad, dy, x + w - pad, dy);
        dy += 8;

        // Rewards
        g.setFont(F_KEY);
        FontMetrics fmK = g.getFontMetrics();
        g.setColor(TEXT_DIM);
        g.drawString("Rewards", x + pad, dy + fmK.getAscent());
        dy += fmK.getHeight() + 4;

        g.setFont(F_DESC);
        if (q.getGoldReward() > 0) {
            g.setColor(AMBER);
            g.drawString("\u25c6 " + q.getGoldReward() + " gold", x + pad, dy + fmD.getAscent());
            dy += fmD.getHeight() + 2;
        }
        if (q.getXpReward() > 0) {
            g.setColor(CYAN_ACC);
            g.drawString("\u25c6 +" + q.getXpReward() + " XP", x + pad, dy + fmD.getAscent());
            dy += fmD.getHeight() + 2;
        }
        if (q.getItemReward() != null) {
            g.setColor(PHOSPHOR);
            g.drawString("\u25c6 " + q.getItemReward().getName(), x + pad, dy + fmD.getAscent());
        }
        if (q.getGoldReward() == 0 && q.getXpReward() == 0 && q.getItemReward() == null) {
            g.setColor(TEXT_DIM);
            g.drawString("None", x + pad, dy + fmD.getAscent());
        }
    }

    // ── KEY BAR ───────────────────────────────────────────────────────────────

    private void paintKeyBar(Graphics2D g, int x, int y, int w, int h) {
        g.setPaint(new GradientPaint(x, y, new Color(14, 12, 6), x, y + h, BG));
        g.fillRect(x, y, w, h);
        g.fillRoundRect(x, y + h / 2, w, h / 2 + 4, 6, 6);
        g.setColor(BORDER_COL);
        g.drawLine(x, y, x + w, y);

        String[][] keys = {{"↑↓", "Navigate"}, {"Esc", "Close"}};
        lastKeyBarRects = OverlayTheme.paintKeyBadges(g, x, y, w, h, keys, AMBER);
    }
}

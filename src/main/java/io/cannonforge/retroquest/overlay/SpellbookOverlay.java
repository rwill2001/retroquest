package io.cannonforge.retroquest.overlay;
import static io.cannonforge.retroquest.overlay.OverlayTheme.AMBER;
import static io.cannonforge.retroquest.overlay.OverlayTheme.BG;
import static io.cannonforge.retroquest.overlay.OverlayTheme.BORDER_COL;
import static io.cannonforge.retroquest.overlay.OverlayTheme.CYAN_ACC;
import static io.cannonforge.retroquest.overlay.OverlayTheme.DANGER;
import static io.cannonforge.retroquest.overlay.OverlayTheme.F_DESC;
import static io.cannonforge.retroquest.overlay.OverlayTheme.F_ITEM;
import static io.cannonforge.retroquest.overlay.OverlayTheme.F_KEY;
import static io.cannonforge.retroquest.overlay.OverlayTheme.F_LABEL;
import static io.cannonforge.retroquest.overlay.OverlayTheme.F_SMALL;
import static io.cannonforge.retroquest.overlay.OverlayTheme.F_TITLE;
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
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

import io.cannonforge.retroquest.core.GamePanel;
import io.cannonforge.retroquest.core.MessageLog;
import io.cannonforge.retroquest.core.Retroquest;
import io.cannonforge.retroquest.model.Player;
import io.cannonforge.retroquest.model.Spell;

/**
 * In-panel spellbook overlay — renders inside {@link GamePanel} without a JDialog.
 *
 * <p>Slides in from the left side. Displays the player's known spells in a single
 * scrollable list, with a detail pane on the right. Can be opened from the
 * overworld map (field spells) or from inside {@link CombatOverlay} (combat spells only).
 */
public class SpellbookOverlay {

    private static final Color SEL_BG = new Color(0, 50, 80);

    // ── State ─────────────────────────────────────────────────────────────────
    private final Retroquest      game;
    private       Player          player;
    private       CombatOverlay   combatOverlay; // non-null when opened from combat
    private       boolean         combatMode;
    private       boolean         active = false;

    private final List<Spell> spells     = new ArrayList<>();
    private int               selected   = 0;
    private int               scrollTop  = 0;   // first visible row index

    // ── Entry animation ───────────────────────────────────────────────────────
    private long  entryTime = 0;
    private static final long ENTRY_MS = 280;

    // Cached layout from last paint — for mouse hit-testing
    private int lastListX, lastListY, lastListW, lastRowH, lastScrollTop;
    private java.awt.Rectangle[] lastKeyBarRects = new java.awt.Rectangle[0];

    public SpellbookOverlay(Retroquest game) {
        this.game = game;
    }

    // ── Public API ────────────────────────────────────────────────────────────
    public boolean isActive() { return active; }

    /** Open from world map (no combat context). */
    public void open() {
        open(false, null);
    }

    /** Open from combat — spell result fed back into CombatOverlay. */
    public void open(boolean combatMode, CombatOverlay overlay) {
        this.player        = game.getPlayer();
        this.combatMode    = combatMode;
        this.combatOverlay = overlay;
        this.active        = true;
        this.selected      = 0;
        this.scrollTop     = 0;
        this.entryTime     = System.currentTimeMillis();
        buildSpellList();
    }

    public void close() {
        active = false;
    }

    // ── Input ─────────────────────────────────────────────────────────────────
    public void handleKey(KeyEvent e) {
        if (!active) return;
        switch (e.getKeyCode()) {
            case KeyEvent.VK_ESCAPE -> close();
            case KeyEvent.VK_C -> { if (combatMode) castSelected(); else close(); }
            case KeyEvent.VK_UP, KeyEvent.VK_K -> {
                if (selected > 0) { selected--; clampScroll(); }
            }
            case KeyEvent.VK_DOWN, KeyEvent.VK_J -> {
                if (selected < spells.size() - 1) { selected++; clampScroll(); }
            }
            case KeyEvent.VK_ENTER -> castSelected();
        }
    }

    // ── Logic ─────────────────────────────────────────────────────────────────
    private void buildSpellList() {
        spells.clear();
        for (Spell s : player.getKnownSpells()) {
            if ((s == null) || !player.isSpellLearned(s) || (s.getLevel() > player.getLevel())) continue;
            boolean allowed = combatMode
                ? (s.getUsage() == Spell.Usage.COMBAT || s.getUsage() == Spell.Usage.BOTH)
                : (s.getUsage() == Spell.Usage.MAP    || s.getUsage() == Spell.Usage.BOTH);
            if (allowed) spells.add(s);
        }
    }

    private void castSelected() {
        if (spells.isEmpty() || selected < 0 || selected >= spells.size()) return;
        Spell chosen = spells.get(selected);

        if (!player.canCast(chosen)) {
            game.log("No casts remaining for " + chosen.getName() + "!", MessageLog.Type.DANGER);
            return;
        }

        player.castSpell(chosen);
        close();

        if (combatMode && combatOverlay != null) {
            combatOverlay.executeCombatSpell(chosen);
        } else {
            game.castMapSpell(chosen);
        }
    }

    private void clampScroll() {
        // Will be recalculated in paint() with actual row count
        if (selected < scrollTop) scrollTop = selected;
    }


    // ── Mouse support ─────────────────────────────────────────────────────────

    public void handleClick(int mx, int my) {
        if (!active) return;

        // Key bar buttons: [0]=Navigate(no-op) [1]=Cast [2]=Close
        for (int i = 0; i < lastKeyBarRects.length; i++) {
            if (lastKeyBarRects[i].contains(mx, my)) {
                switch (i) {
                    case 1 -> castSelected();
                    case 2 -> close();
                }
                return;
            }
        }

        // Spell list rows
        if (lastRowH > 0 && !spells.isEmpty()
                && mx >= lastListX && mx <= lastListX + lastListW && my >= lastListY) {
            int relY       = my - lastListY;
            int clickedRow = lastScrollTop + relY / lastRowH;
            if (clickedRow >= 0 && clickedRow < spells.size()) {
                if (clickedRow == selected) {
                    castSelected();
                } else {
                    selected = clickedRow;
                    clampScroll();
                }
            }
        }
    }

    // ── Rendering ─────────────────────────────────────────────────────────────
    public void paint(Graphics2D g, int panelW, int panelH) {
        if (!active) return;
        try {
        float ease = entryEase(entryTime, ENTRY_MS);

        // Dim background
        g.setColor(new Color(0, 0, 0, (int)(180 * ease)));
        g.fillRect(0, 0, panelW, panelH);

        int cardW = Math.min(680, panelW - 40);
        int cardH = Math.min(460, panelH - 40);
        int cardX = (panelW - cardW) / 2;
        int cardY = (panelH - cardH) / 2;
        // slide in from left
        cardX = (int)(cardX * ease) + (int)((1f - ease) * (-cardW));

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Shadow
        g.setColor(new Color(0, 0, 0, (int)(100 * ease)));
        g.fillRoundRect(cardX + 5, cardY + 5, cardW, cardH, 6, 6);

        // Card background
        g.setPaint(new GradientPaint(cardX, cardY, new Color(12, 17, 26), cardX, cardY + cardH, BG));
        g.fillRoundRect(cardX, cardY, cardW, cardH, 6, 6);

        // Card border — cyan for spellbook
        g.setStroke(new BasicStroke(1.5f));
        g.setColor(new Color(CYAN_ACC.getRed(), CYAN_ACC.getGreen(), CYAN_ACC.getBlue(), 160));
        g.drawRoundRect(cardX, cardY, cardW, cardH, 6, 6);
        g.setStroke(new BasicStroke(1f));

        int titleH  = 36;
        int keybarH = 34;
        int bodyH   = cardH - titleH - keybarH;
        int splitX  = cardX + cardW * 45 / 100;  // list takes 45% width

        paintTitleBar(g, cardX, cardY, cardW, titleH);
        paintList    (g, cardX, cardY + titleH, splitX - cardX, bodyH);
        paintDesc    (g, splitX, cardY + titleH, cardX + cardW - splitX, bodyH);
        paintKeyBar  (g, cardX, cardY + titleH + bodyH, cardW, keybarH);

        g.setStroke(new BasicStroke(1f));
        } catch (Exception ex) {
            active = false;
            System.err.println("[SpellbookOverlay] paint error: " + ex);
        }
    }

    // ── TITLE BAR ─────────────────────────────────────────────────────────────
    private void paintTitleBar(Graphics2D g, int x, int y, int w, int h) {
        g.setPaint(new GradientPaint(x, y, new Color(6, 20, 30), x + w, y, new Color(8, 12, 20)));
        g.fillRoundRect(x, y, w, h, 6, 6);
        g.fillRect(x, y + h/2, w, h/2);

        g.setColor(BORDER_COL);
        g.drawLine(x, y + h, x + w, y + h);

        g.setFont(F_TITLE);
        FontMetrics fm = g.getFontMetrics();
        String title = "\u25c8  " + (combatMode ? "COMBAT SPELLS" : "SPELLBOOK");
        int ty = y + (h + fm.getAscent() - fm.getDescent()) / 2;

        // glow
        g.setColor(new Color(CYAN_ACC.getRed(), CYAN_ACC.getGreen(), CYAN_ACC.getBlue(), 35));
        g.drawString(title, x + 14 + 1, ty + 1);
        g.setColor(CYAN_ACC);
        g.drawString(title, x + 14, ty);

        // right: spell count
        g.setFont(F_SMALL);
        g.setColor(TEXT_DIM);
        String cnt = spells.size() + " spell" + (spells.size() != 1 ? "s" : "") + " available";
        g.drawString(cnt, x + w - g.getFontMetrics().stringWidth(cnt) - 14, ty);
    }

    /** Returns a color for a spell's name based on its category (damage/heal/buff/other). */
    private Color spellNameColor(Spell s, boolean selected, boolean canCast) {
        if (!canCast)  return TEXT_DIM;
        if (selected)  return CYAN_ACC;
        String n = s.getName().toLowerCase();
        if (n.contains("fire") || n.contains("lightning") || n.contains("missile") ||
            n.contains("ice")  || n.contains("cone")      || n.contains("chain")   ||
            n.contains("meteor")|| n.contains("flame")    || n.contains("death")   ||
            n.contains("kill")  || n.contains("divine")   || n.contains("holy word") ||
            n.contains("entangle"))
            return new Color(255, 140, 40);   // orange — damage
        if (n.contains("cure") || n.equals("heal") || n.contains("restoration") ||
            n.contains("resurrection") || n.equals("wish"))
            return new Color(50, 220, 80);    // green — healing
        if (n.contains("bless") || n.contains("prayer") || n.contains("protect") ||
            n.contains("armor")  || n.contains("resist")  || n.contains("haste")  ||
            n.contains("invisibility") || n.contains("time stop") || n.equals("shield") ||
            n.equals("sanctuary") || n.equals("scry"))
            return new Color(0, 200, 255);    // cyan — buff/utility
        return TEXT_BRIGHT;
    }

    // ── SPELL LIST ────────────────────────────────────────────────────────────
    private void paintList(Graphics2D g, int x, int y, int w, int h) {
        // Background
        g.setColor(BG);
        g.fillRect(x, y, w, h);

        // Right border
        g.setColor(BORDER_COL);
        g.drawLine(x + w, y, x + w, y + h);

        g.setFont(F_ITEM);
        FontMetrics fm = g.getFontMetrics();
        int rowH     = fm.getHeight() + 6;
        int maxRows  = (h - 8) / rowH;

        // Clamp scroll so selected is always visible
        if (selected >= scrollTop + maxRows) scrollTop = selected - maxRows + 1;
        if (selected < scrollTop)            scrollTop = selected;
        int maxScroll = Math.max(0, spells.size() - maxRows);
        scrollTop = Math.max(0, Math.min(scrollTop, maxScroll));

        // Cache for mouse hit-testing
        lastListX = x; lastListY = y; lastListW = w; lastRowH = rowH; lastScrollTop = scrollTop;

        if (spells.isEmpty()) {
            g.setColor(TEXT_DIM);
            g.drawString("No spells available.", x + 12, y + 30 + fm.getAscent());
            return;
        }

        int drawY = y + 6;
        for (int i = scrollTop; i < spells.size() && i < scrollTop + maxRows; i++) {
            Spell s     = spells.get(i);
            boolean sel = (i == selected);

            // Row background
            g.setColor(sel ? SEL_BG : (i % 2 == 0 ? BG : ROW_ALT));
            g.fillRect(x, drawY, w, rowH);

            // Selection accent bar
            if (sel) {
                g.setColor(CYAN_ACC);
                g.fillRect(x, drawY, 3, rowH);
            }

            // Casts remaining
            int casts = (s.getType() == Spell.Type.MAGE)
                ? player.getMageCasts()[s.getLevel()]
                : player.getClericCasts()[s.getLevel()];
            boolean canCast = player.canCast(s);

            Color nameCol = spellNameColor(s, sel, canCast);
            Color castCol = casts > 2 ? PHOSPHOR : casts > 0 ? AMBER : DANGER;

            // Level badge
            g.setFont(F_KEY);
            FontMetrics fmK = g.getFontMetrics();
            String lvl = "Lv" + s.getLevel();
            g.setColor(sel ? new Color(0,180,220) : TEXT_DIM);
            g.drawString(lvl, x + 8, drawY + rowH - (rowH - fm.getAscent()) / 2 - 1);
            int lvlW = fmK.stringWidth("Lv0") + 10;

            // Name
            g.setFont(F_ITEM);
            g.setColor(nameCol);
            String name = s.getName();
            // Truncate if needed
            while (g.getFontMetrics().stringWidth(name) > w - lvlW - 50 && name.length() > 3)
                name = name.substring(0, name.length() - 1);
            g.drawString(name, x + 8 + lvlW, drawY + rowH - (rowH - fm.getAscent()) / 2 - 1);

            // Casts badge (right-aligned)
            g.setFont(F_KEY);
            String castStr = "[" + casts + "]";
            int castX = x + w - g.getFontMetrics().stringWidth(castStr) - 8;
            g.setColor(castCol);
            g.drawString(castStr, castX, drawY + rowH - (rowH - fmK.getAscent()) / 2 - 1);

            drawY += rowH;
        }

        paintScrollIndicators(g, scrollTop, maxRows, spells.size(), x, y, w, h);
    }

    // ── DESCRIPTION ───────────────────────────────────────────────────────────
    private void paintDesc(Graphics2D g, int x, int y, int w, int h) {
        g.setColor(PANEL_BG);
        g.fillRect(x, y, w, h);

        if (spells.isEmpty()) return;
        Spell s = spells.get(Math.min(selected, spells.size() - 1));

        int pad = 14;
        int drawY = y + pad;

        // Spell name header
        g.setFont(F_LABEL);
        FontMetrics fm = g.getFontMetrics();
        g.setColor(AMBER);
        g.drawString(s.getName(), x + pad, drawY + fm.getAscent());
        drawY += fm.getHeight() + 4;

        // Divider
        g.setColor(BORDER_COL);
        g.drawLine(x + pad, drawY, x + w - pad, drawY);
        drawY += 8;

        // Stats row
        g.setFont(F_SMALL);
        FontMetrics fmS = g.getFontMetrics();
        int casts = (s.getType() == Spell.Type.MAGE)
            ? player.getMageCasts()[s.getLevel()]
            : player.getClericCasts()[s.getLevel()];

        String[] statLabels = {"Level", "Type", "Use", "Casts"};
        String[] statVals   = {
            String.valueOf(s.getLevel()),
            s.getType() == Spell.Type.MAGE ? "Mage" : "Cleric",
            s.getUsage() == Spell.Usage.COMBAT ? "Combat" :
            s.getUsage() == Spell.Usage.MAP ? "Map" : "Both",
            String.valueOf(casts)
        };
        Color[] statCols = {TEXT_DIM, TEXT_DIM, TEXT_DIM,
            casts > 2 ? PHOSPHOR : casts > 0 ? AMBER : DANGER};

        for (int i = 0; i < statLabels.length; i++) {
            g.setColor(TEXT_DIM);
            g.drawString(statLabels[i] + ":", x + pad, drawY + fmS.getAscent());
            g.setColor(statCols[i]);
            g.drawString(statVals[i], x + pad + 58, drawY + fmS.getAscent());
            drawY += fmS.getHeight() + 2;
        }

        drawY += 8;
        g.setColor(BORDER_COL);
        g.drawLine(x + pad, drawY, x + w - pad, drawY);
        drawY += 10;

        // Description — word wrapped
        g.setFont(F_DESC);
        FontMetrics fmD = g.getFontMetrics();
        String desc = s.getLongDescription();
        int maxW = w - pad * 2;
        for (String line : OverlayTheme.wordWrap(fmD, desc, maxW)) {
            if (drawY + fmD.getAscent() > y + h - pad) break;
            g.setColor(new Color(160, 185, 220));
            g.drawString(line, x + pad, drawY + fmD.getAscent());
            drawY += fmD.getHeight() + 1;
        }

        // Power estimate at bottom
        if (drawY + 40 < y + h) {
            int power = s.getPower(player);
            g.setFont(F_SMALL);
            g.setColor(TEXT_DIM);
            g.drawString("Est. power: ", x + pad, y + h - 16);
            g.setColor(PHOSPHOR);
            g.drawString(String.valueOf(power), x + pad + fmS.stringWidth("Est. power: "), y + h - 16);
        }
    }

    // ── KEY BAR ───────────────────────────────────────────────────────────────
    private void paintKeyBar(Graphics2D g, int x, int y, int w, int h) {
        g.setPaint(new GradientPaint(x, y, new Color(8, 12, 20), x, y + h, BG));
        g.fillRect(x, y, w, h);
        g.fillRoundRect(x, y + h/2, w, h/2 + 4, 6, 6);
        g.setColor(BORDER_COL);
        g.drawLine(x, y, x + w, y);

        String[][] keys = {{"↑↓", "Navigate"}, {"Enter", "Cast"}, {"Esc", "Close"}};
        lastKeyBarRects = OverlayTheme.paintKeyBadges(g, x, y, w, h, keys, CYAN_ACC);
    }
}

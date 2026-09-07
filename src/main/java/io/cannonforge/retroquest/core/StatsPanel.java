package io.cannonforge.retroquest.core;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;

import javax.swing.JPanel;

import io.cannonforge.retroquest.model.Boon;
import io.cannonforge.retroquest.model.Player;

/**
 * Sidebar stats panel — displays the player's HP, food, gold, level, XP, and
 * current weapon/armor in a compact CRT-styled panel next to the game view.
 *
 * <p>Layout (top to bottom): header with name/level, XP bar, attribute grid,
 * vitals (HP bar, gold, food, AC), equipment slots, and hotkey legend.
 *
 * <p>Refresh via {@link #refresh(Player)} whenever the player's state changes.
 */
@SuppressWarnings("serial")
public class StatsPanel extends JPanel {

    // ── Palette ───────────────────────────────────────────────────────────────
    private static final Color BG         = new Color(  6,   8,  12);
    private static final Color PANEL_BG   = new Color( 11,  15,  22);
    private static final Color BORDER_COL = new Color( 28,  42,  62);
    private static final Color PHOSPHOR   = new Color(  0, 255, 120);
    private static final Color CYAN_ACC   = new Color(  0, 200, 255);
    private static final Color AMBER      = new Color(255, 200,  50);
    private static final Color AMBER_DIM  = new Color(200, 140,  20);
    private static final Color TEXT_DIM   = new Color( 90, 110, 135);
    private static final Color TEXT_MID   = new Color(140, 165, 195);
    private static final Color TEXT_BRIGHT= new Color(200, 222, 255);
    private static final Color DANGER     = new Color(220,  55,  55);
    private static final Color HP_GREEN   = new Color( 50, 210,  80);
    private static final Color HP_YELLOW  = new Color(215, 175,  35);
    private static final Color HP_RED     = new Color(210,  50,  50);

    // ── Fonts ─────────────────────────────────────────────────────────────────
    private static final Font F_TITLE  = Fonts.monoBold(15);
    private static final Font F_STAT   = Fonts.monoBold(13);
    private static final Font F_LABEL  = Fonts.mono    (11);
    private static final Font F_SMALL  = Fonts.mono    (10);
    private static final Font F_SECT   = Fonts.monoBold(10);
    private static final Font F_KEY    = Fonts.monoBold(10);

    // ── Panel width constant ──────────────────────────────────────────────────
    private static final int W = DisplayScale.scaled(230);

    // ── Live data ─────────────────────────────────────────────────────────────
    private String playerName   = "HERO";
    private int    level        = 1;
    private int    hp = 30, maxHp = 30;
    private int    xp = 0,  xpNext = 1000;
    private int    gold = 0, food = 300, ac = 10;
    private int    str = 10, dex = 10, con = 10, intel = 10, wis = 10, cha = 10;
    private String weaponName   = "Bare Hands";
    private int    weaponVal    = 0;
    private String armorName    = "None";
    private int    armorVal     = 0;
    private String helmName     = null;
    private int    helmVal      = 0;
    private String shieldName   = null;
    private int    shieldVal    = 0;
    private String amuletName   = null;
    private int    amuletVal    = 0;
    private String ring1Name    = null;
    private int    ring1Val     = 0;
    private String ring2Name    = null;
    private int    ring2Val     = 0;

    // ── Boon snapshot (read from Player in refresh()) ────────────────────────
    private java.util.Set<String> boonIds = new java.util.HashSet<>();

    // ── Spell buff snapshot (read from Player in refresh()) ──────────────────
    private int shieldSteps    = 0;
    private int hasteSteps     = 0;
    private int prayerSteps    = 0;
    private int holyArmorSteps = 0;
    private int elemResSteps   = 0;
    private int invisSteps     = 0;
    private int protEvSteps    = 0;
    private int blessSteps     = 0;
    private boolean resurrectionCharged = false;
    private int drunkSteps     = 0;
    private int poisonSteps    = 0;

    // ── Scanline animation ────────────────────────────────────────────────────
    private int            scanLine  = 0;
    private javax.swing.Timer pulseTimer;

    // ── Cached offscreen buffer ───────────────────────────────────────────────
    private BufferedImage  buffer;

    private final Retroquest game;

    // ── Constructor ───────────────────────────────────────────────────────────
    public StatsPanel(Retroquest game) {
        this.game = game;
        setPreferredSize(new Dimension(W, DisplayScale.scaled(600)));
        setMinimumSize  (new Dimension(W, DisplayScale.scaled(400)));
        setBackground(BG);
        setOpaque(true);
        setDoubleBuffered(true);

        // No always-on repaint timer. This panel used to redraw every 90 ms
        // forever — a full gradient fill plus a per-scanline loop, ~11 times a
        // second, with nothing on screen changing — which kept the whole app off
        // idle. The panel is data-driven, so it now repaints from refresh(), which
        // the controllers already call after every state change.
    }

    // ── Painting entry ────────────────────────────────────────────────────────
    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        int w = getWidth(), h = getHeight();

        // Render to offscreen buffer so scanlines composite cleanly
        if (buffer == null || buffer.getWidth() != w || buffer.getHeight() != h)
            buffer = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

        Graphics2D g = buffer.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        renderAll(g, w, h);
        g.dispose();

        g0.drawImage(buffer, 0, 0, null);
    }

    private void renderAll(Graphics2D g, int w, int h) {
        // ── Base fill ──
        g.setColor(BG);
        g.fillRect(0, 0, w, h);

        // ── Inner panel with subtle gradient ──
        GradientPaint bgGrad = new GradientPaint(0, 0, PANEL_BG, 0, h, BG);
        g.setPaint(bgGrad);
        g.fillRect(2, 0, w - 2, h);

        // ── Left glow border (phosphor strip) ──
        g.setColor(new Color(0, 200, 80, 45));
        g.fillRect(0, 0, 2, h);
        g.setColor(new Color(0, 255, 120, 80));
        g.fillRect(0, 0, 1, h);

        // ── Right edge border ──
        g.setColor(BORDER_COL);
        g.fillRect(w - 1, 0, 1, h);

        // ── Content sections ──
        int y = 0;
        y = paintHeader(g, w, y);
        y = paintDivider(g, w, y, PHOSPHOR,   "ATTRIBUTES");
        y = paintAttributes(g, w, y);
        y = paintDivider(g, w, y, CYAN_ACC,   "VITALS");
        y = paintVitals(g, w, y);
        y = paintDivider(g, w, y, AMBER,      "EQUIPMENT");
        y = paintEquipment(g, w, y);
        y = paintBuffs(g, w, y);
        y = paintBoons(g, w, y);
        y = paintDivider(g, w, y, TEXT_DIM,   "KEYS");
        paintKeys(g, w, y, h);

        // ── Scanline overlay (last, on top of everything) ──
        paintScanlines(g, w, h);
    }

    // ── HEADER ────────────────────────────────────────────────────────────────
    private int paintHeader(Graphics2D g, int w, int y) {
        y += 12;

        // Name — with phosphor glow layers
        g.setFont(F_TITLE);
        String name = "\u25c8  " + playerName.toUpperCase();

        // Glow halos
        g.setColor(new Color(0, 255, 120, 14));
        for (int d = 3; d >= 1; d--) {
            g.drawString(name, 10 + d, y + d);
            g.drawString(name, 10 - d, y - d);
        }
        // Solid text
        g.setColor(PHOSPHOR);
        g.drawString(name, 10, y);

        // Level badge — top right
        String lv = "LV " + level;
        g.setFont(F_STAT);
        FontMetrics fmS = g.getFontMetrics();
        int badgeW = fmS.stringWidth(lv) + 10;
        int badgeX = w - badgeW - 8;
        // Badge background
        g.setColor(new Color(40, 30, 0));
        g.fillRoundRect(badgeX - 2, y - fmS.getAscent(), badgeW + 4, fmS.getHeight(), 4, 4);
        g.setColor(new Color(AMBER.getRed(), AMBER.getGreen(), AMBER.getBlue(), 120));
        g.setStroke(new BasicStroke(1f));
        g.drawRoundRect(badgeX - 2, y - fmS.getAscent(), badgeW + 4, fmS.getHeight(), 4, 4);
        g.setColor(AMBER);
        g.drawString(lv, badgeX + 3, y);

        y += 14;

        // XP bar
        float xpFrac = xpNext > 0 ? Math.min(1f, (float) xp / xpNext) : 1f;
        paintBar(g, 10, y, w - 20, 6, xpFrac,
            new Color(50, 38, 0), AMBER_DIM, AMBER, false);

        // XP label right-aligned
        g.setFont(F_SMALL);
        g.setColor(new Color(AMBER.getRed(), AMBER.getGreen(), AMBER.getBlue(), 160));
        String xpStr = xp + " / " + xpNext + " xp";
        g.drawString(xpStr, w - g.getFontMetrics().stringWidth(xpStr) - 8, y - 2);

        y += 12;
        return y;
    }

    // ── SECTION DIVIDER ───────────────────────────────────────────────────────
    private int paintDivider(Graphics2D g, int w, int y, Color col, String label) {
        y += 7;
        g.setFont(F_SECT);
        FontMetrics fm = g.getFontMetrics();
        int ty = y + fm.getAscent();

        // Label
        g.setColor(col);
        g.drawString(label, 10, ty);

        // Line after label
        int lineX = 10 + fm.stringWidth(label) + 6;
        g.setColor(new Color(col.getRed(), col.getGreen(), col.getBlue(), 50));
        g.setStroke(new BasicStroke(1f));
        g.drawLine(lineX, ty - fm.getAscent() / 2, w - 8, ty - fm.getAscent() / 2);

        y += fm.getHeight() + 3;
        return y;
    }

    // ── ATTRIBUTES (3 × 2 grid) ───────────────────────────────────────────────
    private int paintAttributes(Graphics2D g, int w, int y) {
        y += 3;
        int[][] vals  = {{str, intel}, {dex, wis}, {con, cha}};
        String[][] keys = {{"STR","INT"},{"DEX","WIS"},{"CON","CHA"}};
        int rowH = 19, colW = (w - 20) / 2;

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 2; col++) {
                int cx = 10 + col * colW;
                int cy = y + row * rowH;
                String k = keys[row][col];
                int v    = vals[row][col];

                // Stat value colour-coded by magnitude
                Color valCol = v >= 16 ? PHOSPHOR
                             : v >= 12 ? CYAN_ACC
                             : v >=  8 ? TEXT_BRIGHT
                             : TEXT_DIM;

                // Label
                g.setFont(F_LABEL);
                g.setColor(TEXT_DIM);
                g.drawString(k, cx, cy + g.getFontMetrics().getAscent());

                // Value — right-aligned within column
                g.setFont(F_STAT);
                String vs = String.format("%2d", v);
                int vx = cx + colW - g.getFontMetrics().stringWidth(vs) - 6;
                // Subtle highlight backing for high values
                if (v >= 16) {
                    g.setColor(new Color(0, 80, 40, 50));
                    g.fillRoundRect(vx - 2, cy, g.getFontMetrics().stringWidth(vs) + 4, rowH - 3, 3, 3);
                }
                g.setColor(valCol);
                g.drawString(vs, vx, cy + g.getFontMetrics().getAscent());
            }
        }

        return y + 3 * rowH + 5;
    }

    // ── VITALS ────────────────────────────────────────────────────────────────
    private int paintVitals(Graphics2D g, int w, int y) {
        y += 3;

        // HP bar — tall, colour-coded
        float hpFrac  = maxHp > 0 ? Math.min(1f, (float) hp / maxHp) : 0f;
        Color hpColor = hpFrac > 0.60f ? HP_GREEN
                      : hpFrac > 0.30f ? HP_YELLOW
                      :                  HP_RED;
        Color hpBg    = new Color(hpColor.getRed()/5, hpColor.getGreen()/5, hpColor.getBlue()/5);

        paintBar(g, 10, y, w - 20, 13, hpFrac, hpBg, hpColor.darker(), hpColor, true);

        // HP text over the bar
        g.setFont(F_LABEL);
        g.setColor(Color.WHITE);
        FontMetrics fm = g.getFontMetrics();
        String hpStr = "HP  " + hp + " / " + maxHp;
        g.drawString(hpStr, 10 + (w - 20 - fm.stringWidth(hpStr)) / 2,
            y + (13 + fm.getAscent() - fm.getDescent()) / 2);

        // Low HP warning glow
        if (hpFrac < 0.25f) {
            int alpha = (int)(80 + 60 * Math.sin(System.currentTimeMillis() / 300.0));
            g.setColor(new Color(220, 30, 30, alpha));
            g.setStroke(new BasicStroke(1.5f));
            g.drawRoundRect(9, y - 1, w - 19, 15, 3, 3);
        }

        y += 19;

        // Stat rows
        y = paintStatRow(g, w, y, "GOLD", fmtGold(gold),       AMBER);
        y = paintStatRow(g, w, y, "FOOD", String.valueOf(food),
            food < 50 ? DANGER : food < 150 ? new Color(220, 140, 40) : TEXT_BRIGHT);
        y = paintStatRow(g, w, y, "AC",   String.valueOf(ac),   CYAN_ACC);

        return y + 3;
    }

    private int paintStatRow(Graphics2D g, int w, int y, String lbl, String val, Color valCol) {
        FontMetrics fmL, fmV;

        g.setFont(F_LABEL);
        fmL = g.getFontMetrics();
        g.setColor(TEXT_DIM);
        g.drawString(lbl, 10, y + fmL.getAscent());

        g.setFont(F_STAT);
        fmV = g.getFontMetrics();
        g.setColor(valCol);
        int vx = w - fmV.stringWidth(val) - 10;
        g.drawString(val, vx, y + fmL.getAscent());

        // Dot leader
        g.setFont(F_SMALL);
        FontMetrics fmD = g.getFontMetrics();
        g.setColor(new Color(40, 55, 75));
        int dotStart = 10 + fmL.stringWidth(lbl) + 5;
        int dotEnd   = vx - 4;
        for (int dx = dotStart; dx < dotEnd; dx += 6)
            g.drawString("\u00b7", dx, y + fmD.getAscent());

        return y + 17;
    }

    // ── EQUIPMENT ─────────────────────────────────────────────────────────────
    private int paintEquipment(Graphics2D g, int w, int y) {
        y += 3;

        // Weapon
        String wpnBonus = weaponVal > 0 ? "+" + weaponVal : "";
        y = paintEquipRow(g, w, y, "WPN", weaponName, wpnBonus, AMBER);

        // Armor
        String armBonus = armorVal > 0 ? "+" + armorVal : "";
        y = paintEquipRow(g, w, y, "ARM", armorName, armBonus, CYAN_ACC);

        // Shield
        if (shieldName != null) {
            String shdBonus = shieldVal > 0 ? "+" + shieldVal : "";
            y = paintEquipRow(g, w, y, "SHD", shieldName, shdBonus, CYAN_ACC);
        }

        // Helm
        if (helmName != null) {
            String hlmBonus = helmVal > 0 ? "+" + helmVal : "";
            y = paintEquipRow(g, w, y, "HLM", helmName, hlmBonus, CYAN_ACC);
        }

        // Amulet
        if (amuletName != null) {
            String amuBonus = amuletVal > 0 ? "+" + amuletVal : "";
            y = paintEquipRow(g, w, y, "AMU", amuletName, amuBonus, PHOSPHOR);
        }

        // Ring 1
        if (ring1Name != null) {
            String r1Bonus = ring1Val > 0 ? "+" + ring1Val : "";
            y = paintEquipRow(g, w, y, "RG1", ring1Name, r1Bonus, PHOSPHOR);
        } else {
            y = paintEquipRow(g, w, y, "RG1", "None", "", TEXT_DIM);
        }

        // Ring 2
        if (ring2Name != null) {
            String r2Bonus = ring2Val > 0 ? "+" + ring2Val : "";
            y = paintEquipRow(g, w, y, "RG2", ring2Name, r2Bonus, PHOSPHOR);
        } else {
            y = paintEquipRow(g, w, y, "RG2", "None", "", TEXT_DIM);
        }

        return y + 3;
    }

    private int paintEquipRow(Graphics2D g, int w, int y, String slot, String item, String bonus, Color col) {
        int rowH = 16;

        // Slot tag
        g.setFont(F_SMALL);
        FontMetrics fmS = g.getFontMetrics();
        g.setColor(new Color(col.getRed(), col.getGreen(), col.getBlue(), 120));
        g.fillRoundRect(8, y + 1, fmS.stringWidth(slot) + 6, rowH - 3, 3, 3);
        g.setColor(col);
        g.setStroke(new BasicStroke(0.8f));
        g.drawRoundRect(8, y + 1, fmS.stringWidth(slot) + 6, rowH - 3, 3, 3);
        g.setColor(TEXT_BRIGHT);
        g.drawString(slot, 11, y + fmS.getAscent() + 1);

        // Bonus right-aligned
        int bonusX = w - 10;
        if (!bonus.isEmpty()) {
            g.setFont(F_STAT);
            FontMetrics fmB = g.getFontMetrics();
            bonusX = w - fmB.stringWidth(bonus) - 10;
            g.setColor(col);
            g.drawString(bonus, bonusX, y + fmS.getAscent() + 1);
        }

        // Item name — between tag and bonus, truncated
        g.setFont(F_SMALL);
        FontMetrics fmN = g.getFontMetrics();
        int nameX   = 8 + fmS.stringWidth(slot) + 10;
        int nameMax = bonusX - nameX - 4;
        String name = truncate(g, item, nameMax);
        g.setColor(item.equals("None") ? TEXT_DIM : TEXT_BRIGHT);
        g.drawString(name, nameX, y + fmN.getAscent() + 1);

        return y + rowH + 2;
    }

    // ── BUFFS ────────────────────────────────────────────────────────────────
    private int paintBuffs(Graphics2D g, int w, int y) {
        // Build list of active buffs with their step counts
        java.util.List<String[]> buffs = new java.util.ArrayList<>();
        if (hasteSteps     > 0) buffs.add(new String[]{"Haste",    String.valueOf(hasteSteps)});
        if (shieldSteps    > 0) buffs.add(new String[]{"Shield",   String.valueOf(shieldSteps)});
        if (prayerSteps    > 0) buffs.add(new String[]{"Prayer",   String.valueOf(prayerSteps)});
        if (holyArmorSteps > 0) buffs.add(new String[]{"HolyArmor",String.valueOf(holyArmorSteps)});
        if (elemResSteps   > 0) buffs.add(new String[]{"ElemRes",  String.valueOf(elemResSteps)});
        if (invisSteps     > 0) buffs.add(new String[]{"Invis",    String.valueOf(invisSteps)});
        if (protEvSteps    > 0) buffs.add(new String[]{"ProtEv",   String.valueOf(protEvSteps)});
        if (blessSteps     > 0) buffs.add(new String[]{"Bless",    String.valueOf(blessSteps)});
        if (resurrectionCharged)  buffs.add(new String[]{"Resurrect", "WARD"});
        if (drunkSteps     > 0) buffs.add(new String[]{"Drunk",    String.valueOf(drunkSteps)});
        if (poisonSteps    > 0) buffs.add(new String[]{"POISON",   String.valueOf(poisonSteps)});

        if (buffs.isEmpty()) return y;

        y = paintDivider(g, w, y, PHOSPHOR, "BUFFS");

        Color BUFF_GREEN = new Color(0x55, 0xCC, 0x55);
        g.setFont(F_SMALL);
        FontMetrics fm = g.getFontMetrics();

        int col = 0;
        int rowH = 16;
        int colW = w / 2 - 6;

        for (String[] buff : buffs) {
            int bx = col == 0 ? 8 : w / 2 + 2;
            boolean isWard = "WARD".equals(buff[1]);
            boolean isDebuff = "POISON".equals(buff[0]) || "Drunk".equals(buff[0]);
            Color buffCol = isWard ? AMBER : isDebuff ? DANGER : BUFF_GREEN;

            // Label
            g.setColor(buffCol);
            g.drawString(buff[0], bx, y + fm.getAscent());

            // Step count right-aligned within column
            String steps = "(" + buff[1] + ")";
            int stepsX = bx + colW - fm.stringWidth(steps);
            g.setColor(new Color(buffCol.getRed(), buffCol.getGreen(), buffCol.getBlue(), 160));
            g.drawString(steps, stepsX, y + fm.getAscent());

            col++;
            if (col == 2) { col = 0; y += rowH; }
        }
        if (col != 0) y += rowH;  // finish partial row

        return y + 2;
    }

    // ── BOONS ─────────────────────────────────────────────────────────────────
    private int paintBoons(Graphics2D g, int w, int y) {
        if (boonIds == null || boonIds.isEmpty()) return y;
        java.util.Set<String> acquired = boonIds;

        y = paintDivider(g, w, y, new Color(0xCC, 0xAA, 0x55), "BOONS");

        Color BOON_COL = new Color(0xDD, 0xBB, 0x44);
        g.setFont(F_SMALL);
        FontMetrics fm = g.getFontMetrics();
        int rowH = 15;

        for (String id : acquired) {
            Boon boon = Boon.byId(id);
            String label = boon != null ? boon.displayName : id;
            g.setColor(BOON_COL);
            g.drawString("\u2605 " + label, 8, y + fm.getAscent());
            y += rowH;
        }
        return y + 2;
    }

    // ── KEYS ─────────────────────────────────────────────────────────────────
    private void paintKeys(Graphics2D g, int w, int startY, int h) {
        int y = startY + 4;

        String[][] keys = {
            {"\u2190\u2191\u2192\u2193", "Move"},
            {"I", "Inventory"},
            {"C", "Cast Spell"},
            {"T", "Talk"},
            {"E", "Enter / Exit"},
            {"S", "Save Game"},
            {"L", "Quest Log"},
            {"F1", "Help"},
            {"+/-", "Zoom"},
            {"Q", "Quit"},
        };

        int rowH = 15;

        for (String[] k : keys) {
            if (y + rowH > h - 6) break;  // don't overflow

            // Key badge
            g.setFont(F_KEY);
            FontMetrics fm = g.getFontMetrics();
            String badge = k[0];
            int bw = fm.stringWidth(badge) + 8;

            g.setColor(new Color(0, 30, 50));
            g.fillRoundRect(10, y, bw, rowH - 2, 3, 3);
            g.setColor(new Color(CYAN_ACC.getRed(), CYAN_ACC.getGreen(), CYAN_ACC.getBlue(), 130));
            g.setStroke(new BasicStroke(0.8f));
            g.drawRoundRect(10, y, bw, rowH - 2, 3, 3);
            g.setColor(CYAN_ACC);
            g.drawString(badge, 14, y + fm.getAscent());

            // Action label
            g.setFont(F_SMALL);
            g.setColor(TEXT_MID);
            g.drawString(k[1], 10 + bw + 7, y + g.getFontMetrics().getAscent());

            y += rowH + 1;
        }

        // Version tag at very bottom
        g.setFont(F_SMALL);
        g.setColor(new Color(TEXT_DIM.getRed(), TEXT_DIM.getGreen(), TEXT_DIM.getBlue(), 60));
        String ver = "RETROQUEST  v0.1";
        g.drawString(ver, (w - g.getFontMetrics().stringWidth(ver)) / 2, h - 6);
    }

    // ── BAR ──────────────────────────────────────────────────────────────────
    private void paintBar(Graphics2D g, int x, int y, int w, int h,
                          float frac, Color bgCol, Color dimCol, Color brightCol,
                          boolean glow) {
        // Track
        g.setColor(bgCol);
        g.fillRoundRect(x, y, w, h, 3, 3);

        // Fill
        int fillW = Math.max(2, (int)(w * Math.min(1f, Math.max(0f, frac))));
        GradientPaint fill = new GradientPaint(x, y, brightCol, x, y + h, dimCol);
        g.setPaint(fill);
        g.fillRoundRect(x, y, fillW, h, 3, 3);

        // Segment ticks on HP bar (every 25%)
        if (glow) {
            g.setColor(new Color(BG.getRed(), BG.getGreen(), BG.getBlue(), 100));
            g.setStroke(new BasicStroke(1f));
            for (int pct = 25; pct < 100; pct += 25) {
                int tx = x + (int)(w * pct / 100f);
                g.drawLine(tx, y + 1, tx, y + h - 1);
            }
        }

        // Border
        g.setColor(new Color(brightCol.getRed(), brightCol.getGreen(), brightCol.getBlue(), 90));
        g.setStroke(new BasicStroke(1f));
        g.drawRoundRect(x, y, w, h, 3, 3);

        // Inner glow on filled part edge
        if (glow && fillW > 4) {
            g.setColor(new Color(255, 255, 255, 30));
            g.fillRoundRect(x + 1, y + 1, fillW - 2, h / 2, 2, 2);
        }
    }

    // ── SCANLINES ────────────────────────────────────────────────────────────
    private void paintScanlines(Graphics2D g, int w, int h) {
        g.setColor(new Color(0, 0, 0, 18));
        for (int y = scanLine % 2; y < h; y += 2)
            g.drawLine(0, y, w, y);

        // Vignette — fade edges slightly
        RadialGradientPaint vig = new RadialGradientPaint(
            new Point2D.Float(w / 2f, h / 2f),
            Math.max(w, h) * 0.75f,
            new float[]{0f, 1f},
            new Color[]{new Color(0,0,0,0), new Color(0,0,0,40)});
        g.setPaint(vig);
        g.fillRect(0, 0, w, h);
    }

    // ── HELPERS ───────────────────────────────────────────────────────────────
    private String fmtGold(int g) {
        if (g >= 1_000_000) return String.format("%,.1fM", g / 1_000_000.0);
        if (g >= 1_000)     return String.format("%,d", g);
        return String.valueOf(g);
    }

    private String truncate(Graphics2D g, String text, int maxPx) {
        if (maxPx <= 0) return "";
        FontMetrics fm = g.getFontMetrics();
        if (fm.stringWidth(text) <= maxPx) return text;
        while (text.length() > 1 && fm.stringWidth(text + "\u2026") > maxPx)
            text = text.substring(0, text.length() - 1);
        return text + "\u2026";
    }

    // ── DISPOSE ──────────────────────────────────────────────────────────────
    /**
     * Releases panel resources when it is replaced (e.g. on game reload).
     * Kept as a no-op-safe hook now that the panel no longer runs a repaint timer.
     */
    public void dispose() {
        if (pulseTimer != null) {
            pulseTimer.stop();
            pulseTimer = null;
        }
        buffer = null;
    }

    // ── REFRESH ───────────────────────────────────────────────────────────────
    public void refresh() {
        Player p = game.getPlayer();

        playerName = p.getName();
        level      = p.getLevel();
        hp         = p.getHp();
        maxHp      = p.getMaxHp();
        xp         = p.getXp();
        xpNext     = p.getXpToNextLevel();
        gold       = p.getGold();
        food       = p.getFood();
        ac         = p.getAC() + p.getAcBonus();
        str        = p.getStr();
        dex        = p.getDex();
        con        = p.getCon();
        intel      = p.getIntelligence();
        wis        = p.getWisdom();
        cha        = p.getCharisma();
        weaponName = p.getWeapon().getName();
        weaponVal  = p.getWeapon().getValue();
        armorName  = p.getArmor().getName();
        armorVal   = p.getArmor().getValue();

        helmName   = p.getHelm()   != null ? p.getHelm().getName()   : null;
        helmVal    = p.getHelm()   != null ? p.getHelm().getValue()  : 0;
        shieldName = p.getShield() != null ? p.getShield().getName() : null;
        shieldVal  = p.getShield() != null ? p.getShield().getValue(): 0;
        amuletName = p.getAmulet() != null ? p.getAmulet().getName() : null;
        amuletVal  = p.getAmulet() != null ? p.getAmulet().getValue(): 0;

        ring1Name = p.getRing1() != null ? p.getRing1().getName() : null;
        ring1Val  = p.getRing1() != null ? p.getRing1().getValue() : 0;
        ring2Name = p.getRing2() != null ? p.getRing2().getName() : null;
        ring2Val  = p.getRing2() != null ? p.getRing2().getValue() : 0;

        hasteSteps     = p.hasteStepsLeft();
        shieldSteps    = p.shieldStepsLeft();
        prayerSteps    = p.prayerStepsLeft();
        holyArmorSteps = p.holyArmorStepsLeft();
        elemResSteps   = p.elemResStepsLeft();
        invisSteps     = p.invisStepsLeft();
        protEvSteps    = p.protEvStepsLeft();
        blessSteps     = p.blessStepsLeft();
        resurrectionCharged = p.isResurrectionCharged();
        drunkSteps     = p.drunkStepsLeft();
        poisonSteps    = p.poisonStepsLeft();
        boonIds = new java.util.HashSet<>(p.getAcquiredBoons());

        buffer = null; // force full redraw
        repaint();
    }
}

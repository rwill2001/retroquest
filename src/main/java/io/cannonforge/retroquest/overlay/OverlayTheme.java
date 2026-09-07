package io.cannonforge.retroquest.overlay;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

import io.cannonforge.retroquest.core.DisplayScale;
import io.cannonforge.retroquest.core.Fonts;

/**
 * Shared color palette, fonts, and utility methods for all overlay panels.
 * Overlays that need a custom tint (e.g. DeathOverlay's red scheme) keep their
 * own constants and only reference specific helpers from here.
 */
public final class OverlayTheme {

    private OverlayTheme() {}

    // ── Shared palette ──────────────────────────────────────────────────────────
    public static final Color BG         = new Color(  6,   8,  14);
    public static final Color PANEL_BG   = new Color( 10,  14,  22);
    public static final Color BORDER_COL = new Color( 35,  52,  78);
    public static final Color PHOSPHOR   = new Color(  0, 255, 120);
    public static final Color CYAN_ACC   = new Color(  0, 200, 255);
    public static final Color AMBER      = new Color(255, 200,  50);
    public static final Color TEXT_DIM   = new Color( 80, 105, 135);
    public static final Color TEXT_BRIGHT= new Color(200, 222, 255);
    public static final Color DANGER     = new Color(220,  55,  55);
    public static final Color GOOD       = new Color( 50, 210,  80);
    public static final Color ROW_ALT    = new Color( 10,  14,  22);

    // ── Shared fonts ────────────────────────────────────────────────────────────
    public static final Font F_TITLE = Fonts.monoBold(14);
    public static final Font F_ITEM  = Fonts.mono    (12);
    public static final Font F_LABEL = Fonts.monoBold(12);
    public static final Font F_KEY   = Fonts.monoBold(11);
    public static final Font F_DESC  = Fonts.mono    (11);
    public static final Font F_SMALL = Fonts.mono    (10);

    // ── Scaling ─────────────────────────────────────────────────────────────────

    /**
     * Scales a pixel dimension by the global {@link DisplayScale#SCALE}. Fonts
     * already scale via {@link Fonts}; every hard-coded bar height, padding and
     * card size in the overlays has to go through here or text overlaps its box
     * on anything above 100%.
     */
    public static int scaled(int px) { return DisplayScale.scaled(px); }

    // ── Entry animation easing ──────────────────────────────────────────────────

    /**
     * Cubic ease-out: {@code 1 - (1-p)^3} where p is clamped progress [0..1].
     * @param entryTime {@code System.currentTimeMillis()} when overlay opened
     * @param durationMs animation duration in milliseconds
     * @return eased value from 0.0 (start) to 1.0 (fully visible)
     */
    public static float entryEase(long entryTime, long durationMs) {
        float p = Math.min(1f, (float)(System.currentTimeMillis() - entryTime) / durationMs);
        return 1f - (float) Math.pow(1f - p, 3);
    }

    // ── Scroll indicators ───────────────────────────────────────────────────────

    /**
     * Draws ▲/▼ arrows at the right edge of a scrollable list area when content
     * extends above or below the visible region.
     */
    public static void paintScrollIndicators(Graphics2D g, int scrollTop,
            int maxRows, int totalItems, int x, int y, int w, int h) {
        if (totalItems <= maxRows) return;
        g.setFont(F_SMALL);
        g.setColor(TEXT_DIM);
        if (scrollTop > 0)                    g.drawString("\u25b2", x + w - scaled(14), y + scaled(12));
        if (scrollTop + maxRows < totalItems) g.drawString("\u25bc", x + w - scaled(14), y + h - scaled(4));
    }

    // ── Word wrap ───────────────────────────────────────────────────────────────

    /**
     * Splits {@code text} into lines that fit within {@code maxPx} pixels.
     * Respects explicit newlines in the input.
     */
    public static List<String> wordWrap(FontMetrics fm, String text, int maxPx) {
        List<String> lines = new ArrayList<>();
        for (String para : text.split("\n")) {
            if (fm.stringWidth(para) <= maxPx) { lines.add(para); continue; }
            String[] words = para.split(" ");
            StringBuilder cur = new StringBuilder();
            for (String word : words) {
                String test = cur.isEmpty() ? word : cur + " " + word;
                if (fm.stringWidth(test) <= maxPx) cur = new StringBuilder(test);
                else { if (!cur.isEmpty()) lines.add(cur.toString()); cur = new StringBuilder(word); }
            }
            if (!cur.isEmpty()) lines.add(cur.toString());
        }
        return lines;
    }

    // ── Key badge bar ───────────────────────────────────────────────────────────

    /**
     * Paints a centered row of {@code [Key] Label} badges.
     * @param g      graphics context
     * @param x      left edge of the badge area
     * @param y      top edge of the badge area
     * @param w      width of the badge area
     * @param h      height of the badge area
     * @param keys   array of {keyText, label} pairs
     * @param accent colour for the key badge border and text
     * @return rectangles for each badge (for mouse hit-testing)
     */
    public static Rectangle[] paintKeyBadges(Graphics2D g, int x, int y, int w, int h,
                                              String[][] keys, Color accent) {
        int totalW = 0;
        int gap    = scaled(20);
        int pad    = scaled(20);
        int[] bws  = new int[keys.length];
        Font fKey  = Fonts.monoBold(11);
        Font fLbl  = Fonts.mono    (11);

        g.setFont(fKey);
        FontMetrics fmK = g.getFontMetrics();
        g.setFont(fLbl);
        FontMetrics fmL = g.getFontMetrics();

        for (int i = 0; i < keys.length; i++) {
            bws[i] = fmK.stringWidth("[" + keys[i][0] + "]") + fmL.stringWidth(keys[i][1]) + pad;
            totalW += bws[i];
        }
        totalW += gap * (keys.length - 1);

        int bx = x + (w - totalW) / 2;
        int bh = Math.max(fmK.getHeight() + scaled(4), h - scaled(10));
        int by = y + (h - bh) / 2;

        Rectangle[] rects = new Rectangle[keys.length];
        for (int i = 0; i < keys.length; i++) {
            rects[i] = new Rectangle(bx, by, bws[i], bh);

            g.setColor(new Color(10, 16, 28));
            g.fillRoundRect(bx, by, bws[i], bh, 4, 4);
            g.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 100));
            g.setStroke(new BasicStroke(1f));
            g.drawRoundRect(bx, by, bws[i], bh, 4, 4);

            int textY = by + (bh + fmK.getAscent() - fmK.getDescent()) / 2;
            g.setFont(fKey);
            g.setColor(accent);
            String badge = "[" + keys[i][0] + "]";
            g.drawString(badge, bx + scaled(8), textY);

            g.setFont(fLbl);
            g.setColor(TEXT_BRIGHT);
            g.drawString(keys[i][1], bx + scaled(8) + fmK.stringWidth(badge) + scaled(4), textY);

            bx += bws[i] + gap;
        }
        return rects;
    }
}

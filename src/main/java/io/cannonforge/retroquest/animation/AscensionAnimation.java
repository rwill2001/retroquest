package io.cannonforge.retroquest.animation;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import io.cannonforge.retroquest.core.Fonts;
import io.cannonforge.retroquest.core.Retroquest;

/**
 * Island 7→Cradle transition animation (Bellorak to Cradle of Shards, ~6 s).
 *
 * The most dramatic portal animation. A column of golden light rises,
 * stone crumbles away, seven god-glyphs appear and converge, and the screen
 * floods with white before fading to reveal the Cradle's name.
 *
 * Sequence:
 *   0–10%  : solid black; a golden light point appears at bottom-center
 *   8–50%  : golden light column rises and widens from the bottom
 *  15–65%  : stone debris arcs outward from the column with gravity
 *  30–75%  : seven god-glyphs appear in an arc, each in its signature color
 *  65–85%  : glyphs accelerate toward center, leaving colored trails; white flood
 *  80–92%  : pure white screen hold
 *  90–100% : fade to black with "THE CRADLE OF SHARDS" in gold
 */
public class AscensionAnimation {

    private static final long ANIM_MS = AnimationSpeed.scale(6000);

    // ── Palette ───────────────────────────────────────────────────────────────
    private static final Color GOLD_LIGHT  = new Color(255, 220, 100);
    private static final Color GOLD_BRIGHT = new Color(255, 245, 200);
    private static final Color STONE_GRAY  = new Color(100,  90,  78);
    private static final Color STONE_BROWN = new Color(130, 105,  72);
    private static final Color TEXT_GOLD   = new Color(255, 210,  80);

    // God colors (indexed by god ordinal 0-6)
    private static final Color[] GOD_COLORS = {
        new Color( 60, 140, 200),  // Lirandel  — tidal blue
        new Color(255, 120,  30),  // Pyralis   — fire orange
        new Color(140,  80, 200),  // Zephyrion — storm purple
        new Color( 60, 180,  80),  // Sylvandar — forest green
        new Color( 30, 140, 140),  // Thalorax  — deep teal
        new Color(100,  50, 140),  // Umbryn    — shadow violet
        new Color(220, 180,  40),  // Bellorak  — war gold
    };

    // God glyph shapes (simple geometric forms)
    private static final int[][] GLYPH_SHAPES = {
        // Each shape: pairs of (dx,dy) offsets from center, drawn as filled rects
        // 0=circle(Lirandel), 1=triangle(Pyralis), 2=diamond(Zephyrion),
        // 3=cross(Sylvandar), 4=hexagon(Thalorax), 5=crescent(Umbryn), 6=star(Bellorak)
    };

    private static final Font F_TITLE = Fonts.monoBold(16);

    // ── State ─────────────────────────────────────────────────────────────────
    private final Retroquest game;
    private boolean active = false;
    private boolean fired  = false;
    private long    startTime;
    private javax.swing.Timer animTimer;

    // Stone debris
    private static final int DEBRIS_COUNT = 22;
    private final float[] debrisX  = new float[DEBRIS_COUNT];
    private final float[] debrisY  = new float[DEBRIS_COUNT];
    private final float[] debrisVX = new float[DEBRIS_COUNT];
    private final float[] debrisVY = new float[DEBRIS_COUNT];
    private final int[]   debrisSz = new int[DEBRIS_COUNT];
    private final boolean[] debrisBrown = new boolean[DEBRIS_COUNT];

    // God glyph positions (normalized, in arc)
    private final float[] glyphX = new float[7];
    private final float[] glyphY = new float[7];

    public AscensionAnimation(Retroquest game) {
        this.game = game;
    }

    public boolean isActive() { return active; }

    /** Keys pressed in the first moment of the ascent are ignored. */
    private static final long SKIP_GRACE_MS = 700;

    /**
     * True once the ascent has been running long enough to be worth skipping.
     *
     * <p>Nothing forwards keys here yet: {@code Retroquest.handleKey} has no
     * branch for the ascension the way it has for the epilogue and the endgame
     * cinematic. Adding one (three lines, mirroring the wash-ashore branch, plus
     * the two pass-throughs on {@code GamePanel}) makes this live.
     */
    public boolean isWaitingForKey() {
        return active && !fired
            && (System.currentTimeMillis() - startTime) > SKIP_GRACE_MS;
    }

    /**
     * Any key: jump to the closing beat, so the fade-to-black and the
     * "THE CRADLE OF SHARDS" card still play and {@code finishAscension()} still
     * fires from the timer exactly as it does on an unskipped run.
     */
    public void pressKey() {
        if (!active || fired) return;
        long now    = System.currentTimeMillis();
        long target = (long)(0.90f * ANIM_MS);
        if (now - startTime < SKIP_GRACE_MS) return;
        if (now - startTime >= target) return;   // already in the closing beat
        startTime = now - target;
    }

    public void start() {
        java.util.Random rng = new java.util.Random(System.nanoTime());
        fired = false;
        startTime = System.currentTimeMillis();

        // Stone debris — explode outward from center-bottom
        for (int i = 0; i < DEBRIS_COUNT; i++) {
            debrisX[i]  = 0.48f + rng.nextFloat() * 0.04f; // near center
            debrisY[i]  = 0.85f + rng.nextFloat() * 0.10f;
            float angle = (float)(Math.PI * 0.3 + rng.nextFloat() * Math.PI * 0.4); // upward arc
            float spd = 0.003f + rng.nextFloat() * 0.004f;
            debrisVX[i] = (float)(Math.cos(angle) * spd) * (rng.nextBoolean() ? 1 : -1);
            debrisVY[i] = -(float)(Math.sin(angle) * spd);
            debrisSz[i] = 3 + rng.nextInt(4);
            debrisBrown[i] = rng.nextBoolean();
        }

        // God glyphs — arc across upper third
        for (int i = 0; i < 7; i++) {
            float angle = (float)(Math.PI * 0.15 + (Math.PI * 0.70) * i / 6f);
            glyphX[i] = 0.5f + (float)(Math.cos(angle) * 0.32f);
            glyphY[i] = 0.25f - (float)(Math.sin(angle) * 0.12f);
        }

        active = true;
        if (animTimer != null) animTimer.stop();
        animTimer = new javax.swing.Timer(16, e -> {
            // Gravity on debris
            for (int i = 0; i < DEBRIS_COUNT; i++) {
                debrisX[i] += debrisVX[i];
                debrisY[i] += debrisVY[i];
                debrisVY[i] += 0.00012f; // gravity
            }
            long elapsed = System.currentTimeMillis() - startTime;
            if (elapsed > ANIM_MS && !fired) {
                fired  = true;
                active = false;
                animTimer.stop();
                game.finishAscension();
            }
            if (game.getGamePanel() != null) game.getGamePanel().repaint();
        });
        animTimer.start();
    }

    public void paint(Graphics2D g, int W, int H) {
        if (!active) return;
        long elapsed = System.currentTimeMillis() - startTime;
        float p = Math.min(1f, elapsed / (float) ANIM_MS);

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

        // ── Phase 1: Black with growing golden point (0-10%) ─────────────────
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, W, H);

        // ── Phase 2: Light column (8-50%) ────────────────────────────────────
        if (p >= 0.06f) {
            float colP = Math.min(1f, (p - 0.06f) / 0.44f);

            // Column rises from bottom
            int colBaseY = H;
            int colTopY = (int)(H * (1f - colP * 0.85f)); // rises to 15% from top
            int colWidthBase = (int)(8 + colP * W * 0.35f);
            int colWidthTop = Math.max(4, colWidthBase / 3);
            int colCX = W / 2;

            // Draw column as gradient bands (bright center, dim edges)
            for (int y = colTopY; y < colBaseY; y++) {
                float yFrac = (float)(y - colTopY) / Math.max(1, colBaseY - colTopY);
                int halfW = (int)(colWidthTop + (colWidthBase - colWidthTop) * yFrac) / 2;
                // Outer glow
                int glowAlpha = (int)(40 * colP);
                g.setColor(new Color(255, 220, 100, Math.min(255, glowAlpha)));
                g.drawLine(colCX - halfW - 4, y, colCX + halfW + 4, y);
                // Main column
                int mainAlpha = (int)(160 * colP);
                g.setColor(new Color(255, 220, 100, Math.min(255, mainAlpha)));
                g.drawLine(colCX - halfW, y, colCX + halfW, y);
                // Bright center core
                int coreHalf = Math.max(1, halfW / 3);
                int coreAlpha = (int)(220 * colP);
                g.setColor(new Color(255, 245, 200, Math.min(255, coreAlpha)));
                g.drawLine(colCX - coreHalf, y, colCX + coreHalf, y);
            }

            // Bottom glow pool
            int poolR = (int)(colWidthBase * 0.6f);
            int poolAlpha = (int)(60 * colP);
            for (int r = poolR; r > 0; r -= 3) {
                g.setColor(new Color(255, 220, 100, Math.min(255, poolAlpha * r / poolR)));
                g.drawOval(colCX - r, H - r / 2, r * 2, r);
            }
        }

        // ── Phase 3: Stone debris (15-65%) ───────────────────────────────────
        if (p >= 0.15f && p <= 0.70f) {
            float debrisFade = p < 0.25f ? (p - 0.15f) / 0.10f : p > 0.60f ? (0.70f - p) / 0.10f : 1f;
            for (int i = 0; i < DEBRIS_COUNT; i++) {
                int dx = (int)(debrisX[i] * W);
                int dy = (int)(debrisY[i] * H);
                if (dx < -20 || dx > W + 20 || dy < -20 || dy > H + 20) continue;
                int alpha = (int)(180 * debrisFade);
                Color c = debrisBrown[i] ? STONE_BROWN : STONE_GRAY;
                g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.min(255, alpha)));
                g.fillRect(dx, dy, debrisSz[i], debrisSz[i]);
            }
        }

        // ── Phase 4: God glyphs appear (30-75%) ─────────────────────────────
        if (p >= 0.28f && p <= 0.85f) {
            for (int i = 0; i < 7; i++) {
                // Each glyph appears staggered: glyph 0 at 30%, glyph 6 at 54%
                float glyphAppear = 0.28f + i * 0.04f;
                if (p < glyphAppear) continue;

                float gx, gy;
                int alpha;

                if (p < 0.65f) {
                    // Stationary in arc
                    gx = glyphX[i];
                    gy = glyphY[i];
                    float fadeIn = Math.min(1f, (p - glyphAppear) / 0.06f);
                    alpha = (int)(230 * fadeIn);
                } else {
                    // Converge toward center (65-80%)
                    float convP = Math.min(1f, (p - 0.65f) / 0.15f);
                    // Ease-in (accelerate)
                    float eased = convP * convP;
                    gx = glyphX[i] + (0.5f - glyphX[i]) * eased;
                    gy = glyphY[i] + (0.42f - glyphY[i]) * eased;
                    alpha = (int)(230 * (1f - convP * 0.5f));

                    // Colored trail
                    if (convP < 0.8f) {
                        Color tc = GOD_COLORS[i];
                        int trailAlpha = (int)(80 * (1f - convP));
                        g.setColor(new Color(tc.getRed(), tc.getGreen(), tc.getBlue(),
                                Math.min(255, trailAlpha)));
                        int tx1 = (int)(glyphX[i] * W);
                        int ty1 = (int)(glyphY[i] * H);
                        int tx2 = (int)(gx * W);
                        int ty2 = (int)(gy * H);
                        g.drawLine(tx1, ty1, tx2, ty2);
                    }
                }

                int px = (int)(gx * W);
                int py = (int)(gy * H);
                Color gc = GOD_COLORS[i];
                alpha = Math.min(255, alpha);

                // Draw glyph as simple shape
                g.setColor(new Color(gc.getRed(), gc.getGreen(), gc.getBlue(), alpha));
                drawGlyph(g, i, px, py, 8);

                // Glow around glyph
                int glowAlpha = Math.min(255, alpha / 3);
                g.setColor(new Color(gc.getRed(), gc.getGreen(), gc.getBlue(), glowAlpha));
                drawGlyph(g, i, px, py, 11);
            }
        }

        // ── Phase 5: White flood (65-92%) ────────────────────────────────────
        if (p >= 0.65f && p <= 0.92f) {
            float floodP = (p - 0.65f) / 0.27f;
            // Radial flood from center
            if (floodP > 0.4f) {
                float whiteP = (floodP - 0.4f) / 0.6f;
                int whiteAlpha = (int)(255 * Math.min(1f, whiteP * 1.5f));
                g.setColor(new Color(255, 255, 255, Math.min(255, whiteAlpha)));
                g.fillRect(0, 0, W, H);
            }
            // Central bright point earlier
            if (floodP < 0.6f) {
                int r = (int)(floodP * Math.max(W, H));
                int cAlpha = (int)(200 * (1f - floodP / 0.6f));
                g.setColor(new Color(255, 245, 200, Math.min(255, cAlpha)));
                g.fillOval(W / 2 - r / 4, H * 42 / 100 - r / 4, r / 2, r / 2);
            }
        }

        // ── Phase 6: Pure white hold (80-92%) ────────────────────────────────
        if (p >= 0.80f && p <= 0.92f) {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, W, H);
        }

        // ── Phase 7: Fade to black with title (90-100%) ─────────────────────
        if (p >= 0.90f) {
            float fadeP = (p - 0.90f) / 0.10f;

            // White → black
            int blackAlpha = (int)(255 * fadeP);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, W, H);
            g.setColor(new Color(0, 0, 0, Math.min(255, blackAlpha)));
            g.fillRect(0, 0, W, H);

            // Title text fades in
            if (fadeP > 0.3f) {
                float textFade = (fadeP - 0.3f) / 0.7f;
                int textAlpha = (int)(240 * textFade);
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                        RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g.setFont(F_TITLE);
                g.setColor(new Color(255, 210, 80, Math.min(255, textAlpha)));
                FontMetrics fm = g.getFontMetrics();
                String title = "THE CRADLE OF SHARDS";
                int tx = (W - fm.stringWidth(title)) / 2;
                int ty = H / 2 + fm.getAscent() / 2;
                g.drawString(title, tx, ty);
            }
        }
    }

    /** Draws a simple geometric glyph for the given god index. */
    private void drawGlyph(Graphics2D g, int godIndex, int cx, int cy, int r) {
        switch (godIndex) {
            case 0 -> g.fillOval(cx - r, cy - r, r * 2, r * 2);             // Circle (Lirandel)
            case 1 -> {                                                       // Triangle (Pyralis)
                int[] tx = { cx, cx - r, cx + r };
                int[] ty = { cy - r, cy + r, cy + r };
                g.fillPolygon(tx, ty, 3);
            }
            case 2 -> {                                                       // Diamond (Zephyrion)
                int[] dx = { cx, cx + r, cx, cx - r };
                int[] dy = { cy - r, cy, cy + r, cy };
                g.fillPolygon(dx, dy, 4);
            }
            case 3 -> {                                                       // Cross (Sylvandar)
                int t = Math.max(2, r / 3);
                g.fillRect(cx - t, cy - r, t * 2, r * 2);
                g.fillRect(cx - r, cy - t, r * 2, t * 2);
            }
            case 4 -> {                                                       // Hexagon (Thalorax)
                int[] hx = new int[6];
                int[] hy = new int[6];
                for (int i = 0; i < 6; i++) {
                    double a = Math.PI / 3 * i - Math.PI / 6;
                    hx[i] = cx + (int)(r * Math.cos(a));
                    hy[i] = cy + (int)(r * Math.sin(a));
                }
                g.fillPolygon(hx, hy, 6);
            }
            case 5 -> {                                                       // Crescent (Umbryn)
                g.fillOval(cx - r, cy - r, r * 2, r * 2);
                // Cut out overlapping circle to make crescent
                Color bg = g.getColor();
                g.setColor(new Color(0, 0, 0, bg.getAlpha()));
                g.fillOval(cx - r / 2, cy - r, r * 2, r * 2);
                g.setColor(bg);
            }
            case 6 -> {                                                       // Star/crossed swords (Bellorak)
                // Simple 4-pointed star
                int[] sx = { cx, cx + r/3, cx + r, cx + r/3, cx, cx - r/3, cx - r, cx - r/3 };
                int[] sy = { cy - r, cy - r/3, cy, cy + r/3, cy + r, cy + r/3, cy, cy - r/3 };
                g.fillPolygon(sx, sy, 8);
            }
        }
    }
}

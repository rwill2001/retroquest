package io.cannonforge.retroquest.animation;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import io.cannonforge.retroquest.core.Fonts;
import io.cannonforge.retroquest.core.Retroquest;

/**
 * Island 6→7 transition animation (Umbryn to Bellorak, ~5 s).
 *
 * The player crosses from the shadow trenches to the war-torn Golden War Isles.
 * Lightning cracks, war drums pulse, banners rise, soldier silhouettes march,
 * and the screen smash-cuts to black.
 *
 * Sequence:
 *   0–100% : storm sky background (gradient: gray top → burnt orange horizon → scorched earth)
 *   0–100% : red vignette pulses at ~1.5 Hz (war drums)
 *   5–80%  : lightning bolts strike at pre-seeded intervals
 *  15–60%  : two banners (gold + iron) rise from the bottom
 *  20–70%  : row of soldier silhouettes march left-to-right
 *  10–85%  : dust/smoke particles rise from the bottom
 *  40–65%  : narration text: "War calls." / "Steel answers."
 *  85–100% : smash cut — white flash → black → red afterglow ring
 */
public class WarMarchAnimation implements AnimationGuard.Cancellable {

    private static final long ANIM_MS = AnimationSpeed.scale(5000);

    // ── Palette ───────────────────────────────────────────────────────────────
    private static final Color SKY_TOP     = new Color( 25,  22,  30);
    private static final Color SKY_HORIZON = new Color(140,  60,  20);
    private static final Color GROUND      = new Color( 40,  28,  18);
    private static final Color LIGHTNING   = new Color(240, 245, 255);
    private static final Color BANNER_GOLD = new Color(200, 170,  40);
    private static final Color BANNER_IRON = new Color(120, 125, 130);
    private static final Color SOLDIER_SIL = new Color( 15,  12,  10);
    private static final Color DRUM_RED    = new Color(160,  30,  20);
    private static final Color TEXT_AMBER  = new Color(220, 180,  60);
    private static final Color EMBER       = new Color(255, 120,  30);

    private static final Font F_TEXT = Fonts.monoBold(15);

    // ── State ─────────────────────────────────────────────────────────────────
    private final Retroquest game;
    private boolean active = false;
    private boolean fired  = false;
    /** Particle physics advances in fixed steps of this size, driven by the wall clock. */
    private static final long PHYS_STEP_MS = 16;
    private long    lastPhysMs = 0;   // wall-clock anchor for the fixed-step physics
    private long    startTime;
    private javax.swing.Timer animTimer;

    // Lightning bolt timings (normalized)
    private static final float[] BOLT_TIMES = { 0.08f, 0.25f, 0.42f, 0.58f, 0.72f };
    private static final int BOLT_COUNT = BOLT_TIMES.length;
    private final int[][] boltSegX = new int[BOLT_COUNT][6];
    private final int[][] boltSegY = new int[BOLT_COUNT][6];

    // Soldiers
    private static final int SOLDIER_COUNT = 10;
    private final float[] soldierX = new float[SOLDIER_COUNT];
    private final float[] soldierY = new float[SOLDIER_COUNT];

    // Dust/smoke particles
    private static final int DUST_COUNT = 25;
    private final float[] dustX  = new float[DUST_COUNT];
    private final float[] dustY  = new float[DUST_COUNT];
    private final float[] dustVX = new float[DUST_COUNT];
    private final float[] dustVY = new float[DUST_COUNT];
    private final int[]   dustSz = new int[DUST_COUNT];
    private final boolean[] dustEmber = new boolean[DUST_COUNT]; // true = ember orange

    public WarMarchAnimation(Retroquest game) {
        this.game = game;
    }

    public boolean isActive() { return active; }

    /** Stops this run without firing its completion callback (a newer run superseded it). */
    @Override public void cancel() {
        fired  = true;
        active = false;
        if (animTimer != null) animTimer.stop();
    }

    public void start() {
        if (active) return;   // idempotent: never stack a second timer or a second callback
        AnimationGuard.claim(AnimationGuard.WAR_MARCH, this);

        java.util.Random rng = new java.util.Random(System.nanoTime());
        fired = false;
        startTime = System.currentTimeMillis();
        lastPhysMs = startTime;

        // Seed lightning bolt shapes
        for (int b = 0; b < BOLT_COUNT; b++) {
            int x = 60 + rng.nextInt(680);
            boltSegX[b][0] = x;
            boltSegY[b][0] = 0;
            for (int s = 1; s < 6; s++) {
                boltSegX[b][s] = boltSegX[b][s - 1] + (rng.nextInt(40) - 20);
                boltSegY[b][s] = boltSegY[b][s - 1] + 30 + rng.nextInt(30);
            }
        }

        // Soldiers evenly spaced
        for (int i = 0; i < SOLDIER_COUNT; i++) {
            soldierX[i] = -0.10f - i * 0.08f; // start offscreen left, staggered
            soldierY[i] = 0.82f + (i % 2) * 0.02f; // slight row variation
        }

        // Dust particles
        for (int i = 0; i < DUST_COUNT; i++) {
            dustX[i]  = rng.nextFloat();
            dustY[i]  = 0.85f + rng.nextFloat() * 0.15f;
            dustVX[i] = (rng.nextFloat() - 0.3f) * 0.0004f;
            dustVY[i] = -(0.0005f + rng.nextFloat() * 0.0008f);
            dustSz[i] = 2 + rng.nextInt(3);
            dustEmber[i] = rng.nextFloat() < 0.3f;
        }

        active = true;
        if (animTimer != null) animTimer.stop();
        animTimer = new javax.swing.Timer(16, e -> {
            if (!active) return;
            long elapsed = System.currentTimeMillis() - startTime;

            // Physics on a fixed step driven by the wall clock, so the march stays in
            // sync with the wall-clock progress `p` even when ticks arrive late.
            int steps = (int)((startTime + elapsed - lastPhysMs) / PHYS_STEP_MS);
            if (steps > 8) steps = 8;                       // don't replay a long stall
            lastPhysMs += (long) steps * PHYS_STEP_MS;
            for (int s = 0; s < steps; s++) {
                // March soldiers right
                for (int i = 0; i < SOLDIER_COUNT; i++) {
                    soldierX[i] += 0.0018f;
                }
                // Animate dust
                for (int i = 0; i < DUST_COUNT; i++) {
                    dustX[i] += dustVX[i];
                    dustY[i] += dustVY[i];
                    if (dustY[i] < 0.5f) {
                        dustY[i] = 0.85f + (float)(Math.random() * 0.15);
                        dustX[i] = (float) Math.random();
                    }
                }
            }

            if (elapsed > ANIM_MS && !fired) {
                fired  = true;
                active = false;
                animTimer.stop();
                AnimationGuard.release(AnimationGuard.WAR_MARCH, this);
                game.finishWarMarch();
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

        // ── Background: storm sky gradient ───────────────────────────────────
        int horizonY = (int)(H * 0.60f);
        for (int y = 0; y < H; y++) {
            if (y < horizonY) {
                float t = (float) y / horizonY;
                g.setColor(lerpColor(SKY_TOP, SKY_HORIZON, t));
            } else {
                float t = (float)(y - horizonY) / (H - horizonY);
                g.setColor(lerpColor(SKY_HORIZON, GROUND, t));
            }
            g.drawLine(0, y, W, y);
        }

        // ── War drums vignette pulse (0-100%) ────────────────────────────────
        float drumPhase = (float) Math.sin(p * Math.PI * 2 * 7.5); // ~1.5 Hz over 5s
        float vignetteAlpha = 0.10f + 0.12f * (drumPhase * 0.5f + 0.5f);
        int va = (int)(vignetteAlpha * 255);
        // Top and bottom vignette
        for (int i = 0; i < 40; i++) {
            int alpha = Math.min(255, va * (40 - i) / 40);
            g.setColor(new Color(160, 30, 20, alpha));
            g.drawLine(0, i, W, i);
            g.drawLine(0, H - 1 - i, W, H - 1 - i);
        }
        // Left and right vignette
        for (int i = 0; i < 30; i++) {
            int alpha = Math.min(255, va * (30 - i) / 30);
            g.setColor(new Color(160, 30, 20, alpha));
            g.drawLine(i, 0, i, H);
            g.drawLine(W - 1 - i, 0, W - 1 - i, H);
        }

        // ── Lightning bolts (5-80%) ──────────────────────────────────────────
        if (p >= 0.05f && p <= 0.80f) {
            for (int b = 0; b < BOLT_COUNT; b++) {
                float boltP = (p - BOLT_TIMES[b]);
                if (boltP < 0 || boltP > 0.04f) continue; // each bolt visible ~4%
                float boltFade = 1f - (boltP / 0.04f);

                // Screen flash
                if (boltP < 0.01f) {
                    int flashAlpha = (int)(100 * (1f - boltP / 0.01f));
                    g.setColor(new Color(240, 245, 255, Math.min(255, flashAlpha)));
                    g.fillRect(0, 0, W, H);
                }

                // Bolt segments
                float scaleX = W / 800f;
                float scaleY = H / (float)(boltSegY[b][5] + 20);
                int alpha = (int)(255 * boltFade);
                g.setColor(new Color(240, 245, 255, Math.min(255, alpha)));
                g.setStroke(new BasicStroke(2f));
                for (int s = 0; s < 5; s++) {
                    int x1 = (int)(boltSegX[b][s] * scaleX);
                    int y1 = (int)(boltSegY[b][s] * scaleY);
                    int x2 = (int)(boltSegX[b][s + 1] * scaleX);
                    int y2 = (int)(boltSegY[b][s + 1] * scaleY);
                    g.drawLine(x1, y1, x2, y2);
                }
                g.setStroke(new BasicStroke(1f));
            }
        }

        // ── Dust and embers (10-85%) ─────────────────────────────────────────
        if (p >= 0.10f && p <= 0.88f) {
            float dustFade = p < 0.20f ? (p - 0.10f) / 0.10f : p > 0.80f ? (0.88f - p) / 0.08f : 1f;
            for (int i = 0; i < DUST_COUNT; i++) {
                int dx = (int)(dustX[i] * W);
                int dy = (int)(dustY[i] * H);
                if (dy < 0 || dy > H) continue;
                int alpha = (int)(120 * dustFade);
                if (dustEmber[i]) {
                    g.setColor(new Color(255, 120, 30, Math.min(255, alpha)));
                } else {
                    g.setColor(new Color(80, 70, 60, Math.min(255, alpha)));
                }
                g.fillRect(dx, dy, dustSz[i], dustSz[i]);
            }
        }

        // ── Banners (15-60%) ─────────────────────────────────────────────────
        if (p >= 0.15f && p <= 0.70f) {
            float bannerP = Math.min(1f, (p - 0.15f) / 0.20f);
            float bannerFade = p > 0.60f ? (0.70f - p) / 0.10f : 1f;
            float sway = (float) Math.sin(p * 12) * 2;

            // Gold banner (left)
            drawBanner(g, W, H, 0.25f, bannerP, bannerFade, sway, BANNER_GOLD);
            // Iron banner (right)
            drawBanner(g, W, H, 0.75f, bannerP, bannerFade, -sway, BANNER_IRON);
        }

        // ── Soldier silhouettes (20-70%) ─────────────────────────────────────
        if (p >= 0.20f && p <= 0.75f) {
            float solFade = p < 0.30f ? (p - 0.20f) / 0.10f : p > 0.65f ? (0.75f - p) / 0.10f : 1f;
            int solAlpha = (int)(200 * solFade);
            g.setColor(new Color(15, 12, 10, Math.min(255, solAlpha)));
            for (int i = 0; i < SOLDIER_COUNT; i++) {
                float sx = soldierX[i];
                if (sx < -0.05f || sx > 1.05f) continue;
                int px = (int)(sx * W);
                int py = (int)(soldierY[i] * H);
                // Bob up/down
                int bob = (int)(Math.sin(p * 30 + i * 0.8) * 1.5);
                // Body
                g.fillRect(px - 3, py - 10 + bob, 7, 12);
                // Head
                g.fillOval(px - 2, py - 14 + bob, 5, 5);
                // Weapon (spear)
                g.drawLine(px + 3, py - 14 + bob, px + 3, py - 22 + bob);
            }
        }

        // ── Narration text (40-65%) ──────────────────────────────────────────
        if (p >= 0.38f && p <= 0.68f) {
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setFont(F_TEXT);
            FontMetrics fm = g.getFontMetrics();

            // "War calls."
            float t1P = (p - 0.38f) / 0.30f;
            float t1Fade = t1P < 0.2f ? t1P / 0.2f : t1P > 0.8f ? (1f - t1P) / 0.2f : 1f;
            int t1Alpha = (int)(230 * t1Fade);
            if (t1Alpha > 0) {
                g.setColor(new Color(220, 180, 60, Math.min(255, t1Alpha)));
                String line1 = "War calls.";
                g.drawString(line1, (W - fm.stringWidth(line1)) / 2, H / 2 - 10);
            }

            // "Steel answers." — appears slightly later
            if (p >= 0.44f) {
                float t2P = (p - 0.44f) / 0.24f;
                float t2Fade = t2P < 0.2f ? t2P / 0.2f : t2P > 0.8f ? (1f - t2P) / 0.2f : 1f;
                int t2Alpha = (int)(230 * t2Fade);
                if (t2Alpha > 0) {
                    g.setColor(new Color(220, 180, 60, Math.min(255, t2Alpha)));
                    String line2 = "Steel answers.";
                    g.drawString(line2, (W - fm.stringWidth(line2)) / 2, H / 2 + 12);
                }
            }
        }

        // ── Smash cut (85-100%) ──────────────────────────────────────────────
        if (p >= 0.85f) {
            float cutP = (p - 0.85f) / 0.15f;

            if (cutP < 0.15f) {
                // White flash (3 frames worth at 60fps ≈ 0.05)
                int flashAlpha = (int)(255 * (1f - cutP / 0.15f));
                g.setColor(new Color(255, 255, 255, Math.min(255, flashAlpha)));
                g.fillRect(0, 0, W, H);
            } else {
                // Black
                g.setColor(Color.BLACK);
                g.fillRect(0, 0, W, H);

                // Red afterglow ring (brief)
                if (cutP > 0.25f && cutP < 0.55f) {
                    float ringP = (cutP - 0.25f) / 0.30f;
                    int ringAlpha = (int)(80 * (1f - ringP));
                    int ringR = (int)(40 + ringP * 60);
                    g.setColor(new Color(160, 30, 20, Math.min(255, ringAlpha)));
                    g.setStroke(new BasicStroke(2f));
                    g.drawOval(W / 2 - ringR, H / 2 - ringR, ringR * 2, ringR * 2);
                    g.setStroke(new BasicStroke(1f));
                }
            }
        }
    }

    private void drawBanner(Graphics2D g, int W, int H, float xFrac, float riseP,
                            float fade, float sway, Color color) {
        int bx = (int)(xFrac * W + sway);
        int poleTop = (int)(H * 0.30f);
        int poleBot = (int)(H * 0.80f);
        int riseH = (int)((poleBot - poleTop) * riseP);
        int currentTop = poleBot - riseH;
        int alpha = (int)(200 * fade);
        if (alpha <= 0) return;

        // Pole
        g.setColor(new Color(80, 70, 55, Math.min(255, alpha)));
        g.fillRect(bx - 1, currentTop, 3, riseH);

        // Pennant (triangular)
        if (riseP > 0.3f) {
            int pennantH = Math.min(40, riseH / 2);
            int pennantW = 20;
            int[] px = { bx + 2, bx + 2 + pennantW, bx + 2 };
            int[] py = { currentTop, currentTop + pennantH / 2, currentTop + pennantH };
            g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(),
                    Math.min(255, alpha)));
            g.fillPolygon(px, py, 3);
        }
    }

    private static Color lerpColor(Color a, Color b, float t) {
        t = Math.max(0, Math.min(1, t));
        return new Color(
            (int)(a.getRed()   + (b.getRed()   - a.getRed())   * t),
            (int)(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
            (int)(a.getBlue()  + (b.getBlue()  - a.getBlue())  * t)
        );
    }
}

package io.cannonforge.retroquest.animation;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import io.cannonforge.retroquest.core.Fonts;
import io.cannonforge.retroquest.core.Retroquest;

/**
 * Island 5→6 transition animation (Thalorax to Umbryn, ~5 s).
 *
 * The player descends from the abyssal depths into the shadow trenches.
 * Light drains away, ghost paths flicker, memory fragments drift downward,
 * and the screen collapses into void.
 *
 * Sequence:
 *   0–15%  : light drains downward scanline-by-scanline from ocean blue to void-black
 *  10–40%  : shadow tendrils creep inward from edges with violet glow tips
 *  25–60%  : translucent ghost paths flicker horizontally across the screen
 *  30–70%  : parallax falling dots in 3 layers (descent through shadow)
 *  50–75%  : amber memory fragments drift and rotate downward
 *  55–75%  : narration text: "The shadows remember..."
 *  75–100% : rectangular crush-close to center, dim purple flash, then black
 */
public class ShadowDescentAnimation implements AnimationGuard.Cancellable {

    private static final long ANIM_MS = AnimationSpeed.scale(5000);
    /** Particle physics advances in fixed steps of this size, driven by the wall clock. */
    private static final long PHYS_STEP_MS = 16;

    // ── Palette ───────────────────────────────────────────────────────────────
    private static final Color VOID_BLACK    = new Color(  2,   2,   8);
    private static final Color OCEAN_BLUE    = new Color( 20,  50,  80);
    private static final Color SHADOW_PURPLE = new Color( 40,  15,  60);
    private static final Color TENDRIL_TIP   = new Color( 80,  40, 120);
    private static final Color GHOST_BLUE    = new Color(140, 160, 200);
    private static final Color MEMORY_AMBER  = new Color(180, 150,  80);
    private static final Color TEXT_COL      = new Color(120,  80, 160);

    private static final Font F_TEXT = Fonts.monoBold(14);

    // ── State ─────────────────────────────────────────────────────────────────
    private final Retroquest game;
    private boolean active = false;
    private boolean fired  = false;
    private long    startTime;
    private long    lastPhysMs;   // wall-clock anchor for the fixed-step particle physics
    private javax.swing.Timer animTimer;

    // Ghost paths (horizontal flicker lines)
    private static final int GHOST_COUNT = 18;
    private final float[] ghostY     = new float[GHOST_COUNT];
    private final float[] ghostStart = new float[GHOST_COUNT]; // normalized start time
    private final float[] ghostLen   = new float[GHOST_COUNT]; // fraction of screen width

    // Memory fragments
    private static final int FRAG_COUNT = 14;
    private final float[] fragX   = new float[FRAG_COUNT];
    private final float[] fragY   = new float[FRAG_COUNT];
    private final float[] fragVY  = new float[FRAG_COUNT];
    private final float[] fragVX  = new float[FRAG_COUNT];
    private final int[]   fragSz  = new int[FRAG_COUNT];
    private final float[] fragRot = new float[FRAG_COUNT]; // rotation speed

    // Falling parallax dots
    private static final int DOT_COUNT = 40;
    private final float[] dotX    = new float[DOT_COUNT];
    private final float[] dotY    = new float[DOT_COUNT];
    private final float[] dotSpd  = new float[DOT_COUNT];
    private final int[]   dotLayer = new int[DOT_COUNT]; // 0,1,2

    // Tendrils
    private static final int TENDRIL_COUNT = 12;
    private final float[] tendrilX    = new float[TENDRIL_COUNT]; // 0=left edge, 1=right edge
    private final float[] tendrilY    = new float[TENDRIL_COUNT];
    private final float[] tendrilLen  = new float[TENDRIL_COUNT];
    private final boolean[] tendrilFromRight = new boolean[TENDRIL_COUNT];
    private final float[] tendrilPhase = new float[TENDRIL_COUNT]; // sine offset

    public ShadowDescentAnimation(Retroquest game) {
        this.game = game;
    }

    public boolean isActive() { return active; }

    /**
     * Stops this run without firing {@code finishShadowDescent()} — a newer descent
     * has taken the slot, and only that one may complete.
     */
    @Override public void cancel() {
        fired  = true;
        active = false;
        if (animTimer != null) animTimer.stop();
    }

    public void start() {
        if (active) return;   // idempotent: never stack a second timer or a second callback
        AnimationGuard.claim(AnimationGuard.SHADOW_DESCENT, this);

        java.util.Random rng = new java.util.Random(System.nanoTime());
        fired = false;
        startTime  = System.currentTimeMillis();
        lastPhysMs = startTime;

        // Ghost paths
        for (int i = 0; i < GHOST_COUNT; i++) {
            ghostY[i]     = 0.05f + rng.nextFloat() * 0.90f;
            ghostStart[i] = 0.25f + rng.nextFloat() * 0.30f; // staggered 25-55%
            ghostLen[i]   = 0.3f + rng.nextFloat() * 0.5f;
        }

        // Memory fragments
        for (int i = 0; i < FRAG_COUNT; i++) {
            fragX[i]   = 0.1f + rng.nextFloat() * 0.8f;
            fragY[i]   = -0.05f - rng.nextFloat() * 0.3f; // start above screen
            fragVY[i]  = 0.0004f + rng.nextFloat() * 0.0003f;
            fragVX[i]  = (rng.nextFloat() - 0.5f) * 0.0002f;
            fragSz[i]  = 4 + rng.nextInt(5);
            fragRot[i] = (rng.nextFloat() - 0.5f) * 0.02f;
        }

        // Parallax dots
        for (int i = 0; i < DOT_COUNT; i++) {
            dotX[i]    = rng.nextFloat();
            dotY[i]    = rng.nextFloat();
            dotLayer[i] = rng.nextInt(3);
            dotSpd[i]  = 0.001f + dotLayer[i] * 0.0008f + rng.nextFloat() * 0.0005f;
        }

        // Tendrils
        for (int i = 0; i < TENDRIL_COUNT; i++) {
            tendrilFromRight[i] = rng.nextBoolean();
            tendrilY[i]    = 0.05f + rng.nextFloat() * 0.90f;
            tendrilLen[i]  = 0.15f + rng.nextFloat() * 0.20f;
            tendrilPhase[i] = rng.nextFloat() * 6.28f;
        }

        active = true;
        if (animTimer != null) animTimer.stop();
        animTimer = new javax.swing.Timer(16, e -> {
            if (!active) return;
            long elapsed = System.currentTimeMillis() - startTime;

            // Physics on a fixed step driven by the wall clock, so the particles stay
            // in sync with the wall-clock progress `p` even when ticks arrive late.
            int steps = (int)((startTime + elapsed - lastPhysMs) / PHYS_STEP_MS);
            if (steps > 8) steps = 8;                       // don't replay a long stall
            lastPhysMs += (long) steps * PHYS_STEP_MS;
            for (int s = 0; s < steps; s++) {
                // Animate fragments
                for (int i = 0; i < FRAG_COUNT; i++) {
                    fragY[i] += fragVY[i];
                    fragX[i] += fragVX[i];
                }
                // Animate dots
                for (int i = 0; i < DOT_COUNT; i++) {
                    dotY[i] += dotSpd[i];
                    if (dotY[i] > 1.1f) dotY[i] = -0.05f;
                }
            }

            if (elapsed > ANIM_MS && !fired) {
                fired  = true;
                active = false;
                animTimer.stop();
                AnimationGuard.release(AnimationGuard.SHADOW_DESCENT, this);
                game.finishShadowDescent();
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

        // ── Phase 1: Light drain (0-15%) ─────────────────────────────────────
        // Fill background: top drains to void, bottom still ocean blue
        float drainLine = Math.min(1f, p / 0.15f); // 0→1 over first 15%
        int drainY = (int)(drainLine * H);
        g.setColor(VOID_BLACK);
        g.fillRect(0, 0, W, drainY);
        if (drainY < H) {
            // Gradient from ocean blue to void below the drain line
            for (int y = drainY; y < H; y++) {
                float t = (float)(y - drainY) / (H - drainY);
                g.setColor(lerpColor(OCEAN_BLUE, VOID_BLACK, t * 0.5f + p * 0.5f));
                g.drawLine(0, y, W, y);
            }
        }

        // After drain completes, full void background
        if (p > 0.15f) {
            float voidAlpha = Math.min(1f, (p - 0.15f) / 0.10f);
            g.setColor(new Color(2, 2, 8, (int)(voidAlpha * 255)));
            g.fillRect(0, 0, W, H);
        }

        // ── Phase 2: Shadow tendrils (10-40%) ────────────────────────────────
        if (p >= 0.10f && p <= 0.60f) {
            float tendrilP = Math.min(1f, (p - 0.10f) / 0.30f);
            for (int i = 0; i < TENDRIL_COUNT; i++) {
                float len = tendrilLen[i] * tendrilP * W;
                int ty = (int)(tendrilY[i] * H);
                float sineOff = (float) Math.sin(p * 20 + tendrilPhase[i]) * 3;
                int alpha = (int)(120 * tendrilP * (1f - Math.max(0, (p - 0.40f) / 0.20f)));
                if (alpha <= 0) continue;

                if (tendrilFromRight[i]) {
                    int tx = W - (int) len;
                    // Tendril body
                    g.setColor(new Color(40, 15, 60, Math.min(255, alpha)));
                    for (int dy = -1; dy <= 1; dy++)
                        g.drawLine(tx, ty + dy + (int) sineOff, W, ty + dy + (int) sineOff);
                    // Glowing tip
                    g.setColor(new Color(80, 40, 120, Math.min(255, alpha + 40)));
                    g.fillRect(tx - 2, ty - 2 + (int) sineOff, 5, 5);
                } else {
                    int tx = (int) len;
                    g.setColor(new Color(40, 15, 60, Math.min(255, alpha)));
                    for (int dy = -1; dy <= 1; dy++)
                        g.drawLine(0, ty + dy + (int) sineOff, tx, ty + dy + (int) sineOff);
                    g.setColor(new Color(80, 40, 120, Math.min(255, alpha + 40)));
                    g.fillRect(tx - 2, ty - 2 + (int) sineOff, 5, 5);
                }
            }
        }

        // ── Phase 3: Ghost paths (25-60%) ────────────────────────────────────
        if (p >= 0.25f && p <= 0.65f) {
            for (int i = 0; i < GHOST_COUNT; i++) {
                float localP = (p - ghostStart[i]) / 0.08f; // each ghost visible for ~8% of anim
                if (localP < 0 || localP > 1) continue;
                // Fade in then out
                float fade = localP < 0.3f ? localP / 0.3f : (1f - localP) / 0.7f;
                int alpha = (int)(100 * fade);
                if (alpha <= 0) continue;
                int gy = (int)(ghostY[i] * H);
                int gx = (int)((0.5f - ghostLen[i] / 2) * W);
                int gw = (int)(ghostLen[i] * W);
                g.setColor(new Color(140, 160, 200, Math.min(255, alpha)));
                g.drawLine(gx, gy, gx + gw, gy);
                // Second faint line for width
                g.setColor(new Color(140, 160, 200, Math.min(255, alpha / 2)));
                g.drawLine(gx, gy + 1, gx + gw, gy + 1);
            }
        }

        // ── Phase 4: Falling parallax dots (30-70%) ──────────────────────────
        if (p >= 0.20f && p <= 0.80f) {
            float dotFade = p < 0.30f ? (p - 0.20f) / 0.10f : p > 0.70f ? (0.80f - p) / 0.10f : 1f;
            Color[] layerColors = {
                new Color(60, 55, 65, (int)(60 * dotFade)),
                new Color(80, 50, 100, (int)(80 * dotFade)),
                new Color(160, 150, 170, (int)(50 * dotFade))
            };
            for (int i = 0; i < DOT_COUNT; i++) {
                int dx = (int)(dotX[i] * W);
                int dy = (int)(dotY[i] * H);
                if (dy < 0 || dy > H) continue;
                int sz = 1 + dotLayer[i];
                g.setColor(layerColors[dotLayer[i]]);
                g.fillRect(dx, dy, sz, sz);
            }
        }

        // ── Phase 5: Memory fragments (50-75%) ──────────────────────────────
        if (p >= 0.40f && p <= 0.80f) {
            float fragFade = p < 0.50f ? (p - 0.40f) / 0.10f : p > 0.72f ? (0.80f - p) / 0.08f : 1f;
            int fragAlpha = (int)(140 * Math.max(0, fragFade));
            if (fragAlpha > 0) {
                g.setColor(new Color(180, 150, 80, Math.min(255, fragAlpha)));
                for (int i = 0; i < FRAG_COUNT; i++) {
                    int fx = (int)(fragX[i] * W);
                    int fy = (int)(fragY[i] * H);
                    if (fy < -20 || fy > H + 20) continue;
                    // Simple rotating rectangle via offset
                    float rot = p * fragRot[i] * 100;
                    int ox = (int)(Math.sin(rot) * fragSz[i] / 2);
                    g.fillRect(fx + ox, fy, fragSz[i], fragSz[i]);
                }
            }
        }

        // ── Phase 6: Narration text (55-75%) ─────────────────────────────────
        if (p >= 0.50f && p <= 0.78f) {
            float textP = (p - 0.50f) / 0.28f;
            float textFade = textP < 0.25f ? textP / 0.25f : textP > 0.75f ? (1f - textP) / 0.25f : 1f;
            int textAlpha = (int)(220 * textFade);
            if (textAlpha > 0) {
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                        RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g.setFont(F_TEXT);
                g.setColor(new Color(120, 80, 160, Math.min(255, textAlpha)));
                FontMetrics fm = g.getFontMetrics();
                String text = "The shadows remember...";
                int tx = (W - fm.stringWidth(text)) / 2;
                int ty = H / 2;
                g.drawString(text, tx, ty);
            }
        }

        // ── Phase 7: Void close (75-100%) ────────────────────────────────────
        if (p >= 0.75f) {
            float closeP = (p - 0.75f) / 0.25f;
            // Rectangular crush from top/bottom
            int crushH = (int)(H / 2 * closeP);
            g.setColor(VOID_BLACK);
            g.fillRect(0, 0, W, crushH);
            g.fillRect(0, H - crushH, W, crushH);
            // Side crush
            int crushW = (int)(W / 2 * closeP * 0.6f);
            g.fillRect(0, 0, crushW, H);
            g.fillRect(W - crushW, 0, crushW, H);

            // Purple flash near the end
            if (closeP > 0.85f && closeP < 0.95f) {
                float flashP = (closeP - 0.85f) / 0.10f;
                int flashAlpha = (int)(80 * (1f - Math.abs(flashP - 0.5f) * 2));
                g.setColor(new Color(60, 20, 90, Math.min(255, flashAlpha)));
                g.fillRect(0, 0, W, H);
            }

            // Final black
            if (closeP > 0.95f) {
                g.setColor(Color.BLACK);
                g.fillRect(0, 0, W, H);
            }
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

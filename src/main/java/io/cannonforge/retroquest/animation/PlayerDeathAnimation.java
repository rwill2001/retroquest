package io.cannonforge.retroquest.animation;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import io.cannonforge.retroquest.core.Fonts;
import io.cannonforge.retroquest.core.Retroquest;
import io.cannonforge.retroquest.core.SoundManager;

/**
 * 8-bit player death animation — plays over the game panel,
 * then calls game.showDeathScreen() when complete.
 *
 * Sequence:
 *   0–15%  : red flash / screen shake
 *   10–45% : player sprite shatters into pixel fragments that fall with gravity
 *   35–70% : screen darkens, tombstone rises from bottom
 *   65–85% : "R.I.P." chiselled onto tombstone letter by letter
 *   80–100%: fade to black, then trigger gameOver()
 */
public class PlayerDeathAnimation implements AnimationGuard.Cancellable {

    private static final long ANIM_MS = AnimationSpeed.scale(2800);
    /** Particle physics advances in fixed steps of this size, driven by the wall clock. */
    private static final long PHYS_STEP_MS = 16;

    private static final Color STONE_LT  = new Color(160, 150, 140);
    private static final Color STONE_MD  = new Color(110, 100,  90);
    private static final Color STONE_DK  = new Color( 65,  58,  50);
    private static final Color GRASS     = new Color( 40, 120,  40);
    private static final Color DIRT      = new Color( 90,  65,  40);
    private static final Color DIRT_DK   = DIRT.darker();
    private static final Color TEXT_RIP  = new Color(200, 190, 175);
    private static final Color SHADOW_80 = new Color(0, 0, 0, 80);

    // Slab dithering alternates between these two shades — a fresh Color per column
    // used to allocate ~90 objects every frame.
    private static final Color SLAB_A    = STONE_MD;
    private static final Color SLAB_B    = new Color(
            Math.min(255, STONE_MD.getRed()   + 15),
            Math.min(255, STONE_MD.getGreen() + 15),
            Math.min(255, STONE_MD.getBlue()  + 15));
    private static final Color CHISEL    = new Color(STONE_DK.getRed(), STONE_DK.getGreen(), STONE_DK.getBlue(), 80);

    // The three fragment tints, reused each frame with the current alpha.
    private static final Color[] FRAG_TINTS = {
            new Color(220, 160, 100),   // skin
            new Color( 80,  80, 180),   // cloth
            new Color(180, 180, 200)    // metal
    };

    private static final Font F_RIP      = Fonts.monoBold(18);
    private static final Font F_NAME     = Fonts.mono    (11);
    private static final Font F_SMALL    = Fonts.mono    (10);

    // Pixel fragments from shattered sprite
    private static final int FRAG_COUNT = 80;
    private final float[] fragX     = new float[FRAG_COUNT];
    private final float[] fragY     = new float[FRAG_COUNT];
    private final float[] fragVX    = new float[FRAG_COUNT];
    private final float[] fragVY    = new float[FRAG_COUNT];
    private final int[]   fragSize  = new int[FRAG_COUNT];
    private final int[]   fragTint  = new int[FRAG_COUNT];   // index into FRAG_TINTS
    /** Scratch: the three tints at the current frame's alpha — rebuilt once per frame. */
    private final Color[] fragShade = new Color[FRAG_TINTS.length];
    private int           fragShadeAlpha = -1;

    private final Retroquest game;
    private final String     slayerName;
    private boolean          active      = false;
    private boolean          fired       = false; // gameOver called?
    private long             startTime   = 0;
    private long             lastPhysMs  = 0;   // wall-clock anchor for fixed-step physics
    private int              shakeOX     = 0;
    private int              shakeOY     = 0;
    private javax.swing.Timer animTimer  = null;

    public PlayerDeathAnimation(Retroquest game, String slayerName) {
        this.game       = game;
        this.slayerName = slayerName;
    }

    public boolean isActive() { return active; }

    /** Stops this run without showing a death screen — a newer death animation superseded it. */
    @Override public void cancel() {
        fired  = true;
        active = false;
        if (animTimer != null) animTimer.stop();
    }

    public void start() {
        if (active) return;   // idempotent: never stack a second timer or a second callback
        AnimationGuard.claim(AnimationGuard.PLAYER_DEATH, this);

        java.util.Random rng = new java.util.Random();
        active     = false; // will be set true after init
        fired      = false;
        startTime  = System.currentTimeMillis();
        lastPhysMs = startTime;

        // Initialise fragments — burst outward from centre
        for (int i = 0; i < FRAG_COUNT; i++) {
            double angle = rng.nextDouble() * Math.PI * 2;
            float  speed = 1.5f + rng.nextFloat() * 5f;
            fragX[i]    = 0.5f; // normalised 0-1 of screen
            fragY[i]    = 0.45f;
            fragVX[i]   = (float)(Math.cos(angle) * speed * 0.003f);
            fragVY[i]   = (float)(Math.sin(angle) * speed * 0.003f) - 0.008f; // slight upward bias
            fragSize[i] = 2 + rng.nextInt(5);
            // Hero colours — skin, cloth, metal
            fragTint[i] = rng.nextInt(FRAG_TINTS.length);
        }

        active = true;
        SoundManager.getInstance().playDeathSong();

        if (animTimer != null) animTimer.stop();
        animTimer = new javax.swing.Timer(16, e -> {
            if (!active) return;
            long elapsed = System.currentTimeMillis() - startTime;

            // Gravity on a fixed step driven by the wall clock, so the fragments stay in
            // sync with the wall-clock progress `p` even when ticks arrive late.
            int steps = (int)((startTime + elapsed - lastPhysMs) / PHYS_STEP_MS);
            if (steps > 8) steps = 8;                       // don't replay a long stall
            lastPhysMs += (long) steps * PHYS_STEP_MS;
            for (int s = 0; s < steps; s++) {
                for (int i = 0; i < FRAG_COUNT; i++) {
                    fragVY[i] += 0.00045f; // gravity
                    fragX[i]  += fragVX[i];
                    fragY[i]  += fragVY[i];
                }
            }

            // Shake offset
            float p = (float) elapsed / ANIM_MS;
            if (p < 0.18f) {
                shakeOX = (int)((Math.random() - 0.5) * 10 * (1 - p / 0.18f));
                shakeOY = (int)((Math.random() - 0.5) *  6 * (1 - p / 0.18f));
            } else {
                shakeOX = 0; shakeOY = 0;
            }

            if (elapsed > ANIM_MS && !fired) {
                fired = true;
                active = false;
                animTimer.stop();
                AnimationGuard.release(AnimationGuard.PLAYER_DEATH, this);
                // slayerName is either a monster name or an already-formed sentence
                // (starvation, poison), which must not be wrapped a second time.
                game.showDeathScreen(slayerName != null && slayerName.startsWith("You")
                        ? slayerName
                        : "You were slain by the " + slayerName + ".");
            }
            if (game.getGamePanel() != null) game.getGamePanel().repaint();
        });
        animTimer.start();
    }

    public void paint(Graphics2D g, int W, int H) {
        if (!active) return;
        long elapsed = System.currentTimeMillis() - startTime;
        float p = Math.min(1f, (float) elapsed / ANIM_MS);

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // ── Phase 1 (0–18%): red flash + shake ───────────────────────────────
        if (p < 0.18f) {
            float flashA = (float) Math.sin(p / 0.18f * Math.PI);
            g.setColor(new Color(180, 0, 0, (int)(flashA * 160)));
            g.fillRect(0, 0, W, H);
            // scanline flicker
            g.setColor(new Color(255, 255, 255, (int)(flashA * 60)));
            for (int y = 0; y < H; y += 4) g.drawLine(0, y, W, y);
        }

        // ── Phase 2 (10–55%): pixel fragments ────────────────────────────────
        float fragP = Math.min(1f, Math.max(0f, (p - 0.10f) / 0.45f));
        // Alpha is the same for every fragment, so build the three tints once per
        // frame instead of allocating a Color for each of the 80 fragments.
        float lifeP = Math.min(1f, fragP * 2f);
        int   alpha = (int)(220 * lifeP * Math.max(0, 1f - fragP * 0.8f));
        if (fragP > 0 && alpha > 0) {
            int cx = W / 2 + shakeOX;
            int cy = (int)(H * 0.45f) + shakeOY;
            if (alpha != fragShadeAlpha) {
                for (int t = 0; t < FRAG_TINTS.length; t++) {
                    Color base = FRAG_TINTS[t];
                    fragShade[t] = new Color(base.getRed(), base.getGreen(), base.getBlue(), alpha);
                }
                fragShadeAlpha = alpha;
            }
            for (int i = 0; i < FRAG_COUNT; i++) {
                g.setColor(fragShade[fragTint[i]]);
                int fx = cx + (int)(fragX[i] * W * 0.6f - W * 0.3f);
                int fy = cy + (int)(fragY[i] * H * 0.8f - H * 0.15f);
                int fs = fragSize[i];
                g.fillRect(fx, fy, fs, fs);
            }
        }

        // ── Phase 3 (35–72%): darkness floods in ─────────────────────────────
        float darkP = Math.min(1f, Math.max(0f, (p - 0.35f) / 0.37f));
        if (darkP > 0) {
            g.setColor(new Color(0, 0, 0, (int)(darkP * darkP * 200)));
            g.fillRect(0, 0, W, H);
        }

        // ── Phase 4 (42–80%): tombstone rises ────────────────────────────────
        float stoneP = Math.min(1f, Math.max(0f, (p - 0.42f) / 0.38f));
        if (stoneP > 0) {
            paintTombstone(g, W, H, stoneP, p);
        }

        // ── Phase 5 (92–100%): fade to black ─────────────────────────────────
        if (p > 0.92f) {
            float fadeP = (p - 0.92f) / 0.08f;
            g.setColor(new Color(0, 0, 0, Math.min(255, (int)(fadeP * fadeP * 255))));
            g.fillRect(0, 0, W, H);
        }
    }

    private void paintTombstone(Graphics2D g, int W, int H, float stoneP, float p) {
        // Stone dimensions
        int tsW  = 90;
        int tsH  = 110;
        int tsX  = (W - tsW) / 2;
        // Slides up from below
        int tsYFull = (int)(H * 0.38f);
        int tsY  = (int)(H - stoneP * stoneP * (H - tsYFull));

        // ── Dirt mound ────────────────────────────────────────────────────────
        int moundY = tsY + tsH + 2;
        int moundW = tsW + 30;
        int moundH = 18;
        int moundX = (W - moundW) / 2;
        // Dirt
        g.setColor(DIRT);
        g.fillOval(moundX, moundY, moundW, moundH);
        g.setColor(DIRT_DK);
        g.fillOval(moundX + 4, moundY + 4, moundW - 8, moundH - 6);
        // Grass tufts on mound
        g.setColor(GRASS);
        for (int gx = moundX + 6; gx < moundX + moundW - 6; gx += 8) {
            g.fillRect(gx,     moundY + 1, 2, 5);
            g.fillRect(gx + 3, moundY,     2, 4);
        }

        // ── Stone body ────────────────────────────────────────────────────────
        // Shadow
        g.setColor(SHADOW_80);
        g.fillRoundRect(tsX + 5, tsY + 5, tsW, tsH, 12, 12);

        // Main slab — 8-bit dithered columns (two pre-built shades, no per-column Color)
        for (int col = 0; col < tsW; col++) {
            g.setColor((col % 4 < 2) ? SLAB_A : SLAB_B);
            g.drawLine(tsX + col, tsY + 10, tsX + col, tsY + tsH - 2);
        }

        // Rounded top arc
        g.setColor(STONE_LT);
        g.fillArc(tsX, tsY, tsW, 30, 0, 180);
        // Dither the arc top
        g.setColor(STONE_MD);
        for (int col = 0; col < tsW; col += 2) {
            g.drawLine(tsX + col, tsY, tsX + col, tsY + 3);
        }

        // Left highlight edge
        g.setColor(STONE_LT);
        g.drawLine(tsX + 2, tsY + 14, tsX + 2, tsY + tsH - 4);
        g.drawLine(tsX + 3, tsY + 14, tsX + 3, tsY + tsH - 4);

        // Bottom shadow edge
        g.setColor(STONE_DK);
        g.fillRect(tsX, tsY + tsH - 4, tsW, 4);

        // Stone border
        g.setColor(STONE_DK);
        g.setStroke(new BasicStroke(1.5f));
        g.drawRoundRect(tsX, tsY, tsW, tsH, 12, 12);
        g.setStroke(new BasicStroke(1f));

        // Horizontal chisel lines on stone
        g.setColor(CHISEL);
        for (int ly = tsY + 20; ly < tsY + tsH - 8; ly += 12) {
            g.drawLine(tsX + 8, ly, tsX + tsW - 8, ly);
        }

        // ── R.I.P. text ───────────────────────────────────────────────────────
        float ripP = Math.min(1f, Math.max(0f, (p - 0.62f) / 0.22f));
        if (ripP > 0) {
            g.setFont(F_RIP);
            FontMetrics fm = g.getFontMetrics();
            String rip    = "R.I.P.";
            int fullW     = fm.stringWidth(rip);
            // Reveal letter by letter
            int lettersShown = Math.min(rip.length(), (int)(ripP * rip.length() * 1.3f));
            String shown  = rip.substring(0, Math.min(lettersShown, rip.length()));
            int rx = tsX + (tsW - fullW) / 2;
            int ry = tsY + 42;
            // Chisel shadow
            g.setColor(STONE_DK);
            g.drawString(shown, rx + 1, ry + 1);
            // Main text
            g.setColor(TEXT_RIP);
            g.drawString(shown, rx, ry);
        }

        // ── Player name ───────────────────────────────────────────────────────
        float nameP = Math.min(1f, Math.max(0f, (p - 0.76f) / 0.15f));
        if (nameP > 0) {
            g.setFont(F_NAME);
            FontMetrics fmN = g.getFontMetrics();
            String name = game.getPlayer().getName();
            int nx = tsX + (tsW - fmN.stringWidth(name)) / 2;
            int ny = tsY + 62;
            g.setColor(new Color(STONE_DK.getRed(), STONE_DK.getGreen(), STONE_DK.getBlue(), (int)(nameP * 255)));
            g.drawString(name, nx + 1, ny + 1);
            g.setColor(new Color(TEXT_RIP.getRed(), TEXT_RIP.getGreen(), TEXT_RIP.getBlue(), (int)(nameP * 200)));
            g.drawString(name, nx, ny);

            // Death cause in tiny text
            g.setFont(F_SMALL);
            FontMetrics fmS = g.getFontMetrics();
            String cause = "Slain by";
            String who   = slayerName.length() > 14 ? slayerName.substring(0, 13) + "." : slayerName;
            int cAlpha = (int)(nameP * 150);
            g.setColor(new Color(TEXT_RIP.getRed(), TEXT_RIP.getGreen(), TEXT_RIP.getBlue(), cAlpha));
            g.drawString(cause, tsX + (tsW - fmS.stringWidth(cause)) / 2, tsY + 78);
            g.drawString(who,   tsX + (tsW - fmS.stringWidth(who))   / 2, tsY + 90);
        }

        // ── Ambient — small cross on top of stone ─────────────────────────────
        if (stoneP > 0.6f) {
            int crossAlpha = (int)((stoneP - 0.6f) / 0.4f * 180);
            g.setColor(new Color(STONE_DK.getRed(), STONE_DK.getGreen(), STONE_DK.getBlue(), crossAlpha));
            int cx = tsX + tsW / 2;
            g.setStroke(new BasicStroke(3f));
            g.drawLine(cx, tsY - 14, cx, tsY + 2);       // vertical
            g.drawLine(cx - 9, tsY - 8, cx + 9, tsY - 8); // horizontal
            g.setStroke(new BasicStroke(1f));
        }
    }
}

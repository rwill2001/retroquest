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
 * 8-bit dungeon exit animation — reverse of DungeonEntryAnimation.
 *
 * The player emerges from the stone-framed dungeon entrance and walks toward
 * the camera (growing larger). The overworld wilderness opens up behind them:
 * sky, clouds, grass, road. Birds fly overhead. The entrance (with torches)
 * is visible behind the player. Fade to full daylight at the end.
 *
 * Sequence  (total ~3.0 s)
 *   0–15%  : entrance fills view; interior glow fades out
 *  12–80%  : player fades in at gate threshold and walks toward camera
 *  25–70%  : wilderness panorama iris-opens
 *  50–80%  : birds fly overhead
 *  80–100% : fade to overworld
 */
public class DungeonExitAnimation implements AnimationGuard.Cancellable {

    private static final long ANIM_MS = AnimationSpeed.scale(3000);

    // ── Palette ───────────────────────────────────────────────────────────────
    private static final Color SKY_TOP    = new Color( 55,  85, 150);
    private static final Color SKY_BOT    = new Color(200, 220, 240);
    private static final Color GRASS_LT   = new Color( 85, 165,  60);
    private static final Color GRASS_DK   = new Color( 52, 110,  35);
    private static final Color MTN_FAR    = new Color( 90, 100, 130);
    private static final Color MTN_MID    = new Color( 70, 100,  75);
    private static final Color MTN_SNOW   = new Color(230, 235, 245);
    private static final Color FOREST_DK  = new Color( 25,  65,  30);
    private static final Color FOREST_MD  = new Color( 38,  90,  42);
    private static final Color ROAD_LT    = new Color(175, 152, 108);
    private static final Color ROAD_DK    = new Color(138, 115,  75);
    private static final Color STONE_LT   = new Color(170, 158, 140);
    private static final Color STONE_MD   = new Color(130, 118, 100);
    private static final Color STONE_DK   = new Color( 80,  70,  58);
    private static final Color MORTAR     = new Color( 90,  82,  70);
    private static final Color ARCH_DARK  = new Color(  8,   6,   4);
    private static final Color TORCH_ORG  = new Color(255, 140,  20);
    private static final Color TORCH_YEL  = new Color(255, 220,  80);
    private static final Color WOOD_DK    = new Color( 80,  55,  30);

    private static final Font F_EXIT = Fonts.monoBold(16);
    private static final Font F_SUB  = Fonts.mono    (11);

    // ── State ─────────────────────────────────────────────────────────────────
    private final Retroquest game;
    private boolean          active    = false;
    private boolean          fired     = false;
    private boolean paintFailed = false;   // doPaint() threw — stop drawing, but stay active
    /** Particle physics advances in fixed steps of this size, driven by the wall clock. */
    private static final long PHYS_STEP_MS = 16;
    private long    lastPhysMs = 0;   // wall-clock anchor for the fixed-step physics
    private long             startTime;
    private javax.swing.Timer animTimer;

    // Birds
    private static final int BIRD_COUNT = 10;
    private final float[] birdX  = new float[BIRD_COUNT];
    private final float[] birdY  = new float[BIRD_COUNT];
    private final float[] birdVX = new float[BIRD_COUNT];
    private final float[] birdVY = new float[BIRD_COUNT];
    private final int[]   birdWing = new int[BIRD_COUNT];

    // Clouds
    private static final int CLOUD_COUNT = 5;
    private final int[] cloudX = new int[CLOUD_COUNT];
    private final int[] cloudY = new int[CLOUD_COUNT];
    private final int[] cloudW = new int[CLOUD_COUNT];

    // Cobblestones
    private final int[] cobbleX = new int[28];
    private final int[] cobbleY = new int[28];
    private final int[] cobbleW = new int[28];

    // Mountain profiles
    private final int[]     mtnX    = new int[18];
    private final int[]     mtnH    = new int[18];
    private final int[]     mtnW    = new int[18];
    private final boolean[] mtnSnow = new boolean[18];

    public DungeonExitAnimation(Retroquest game) {
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
        AnimationGuard.claim(AnimationGuard.DUNGEON_EXIT, this);

        java.util.Random rng = new java.util.Random(0xCA7EBEEF);
        fired     = false;
        paintFailed = false;
        startTime = System.currentTimeMillis();
        lastPhysMs = startTime;

        // Birds glide across sky from left
        for (int i = 0; i < BIRD_COUNT; i++) {
            birdX[i]  = -0.05f + rng.nextFloat() * 0.3f;
            birdY[i]  = 0.10f  + rng.nextFloat() * 0.18f;
            birdVX[i] = 0.0014f + rng.nextFloat() * 0.0010f;
            birdVY[i] = (rng.nextFloat() - 0.5f) * 0.0003f;
            birdWing[i] = rng.nextInt(8);
        }

        // Clouds
        for (int i = 0; i < CLOUD_COUNT; i++) {
            cloudX[i] = 30  + rng.nextInt(750);
            cloudY[i] = 15  + rng.nextInt(55);
            cloudW[i] = 55  + rng.nextInt(110);
        }

        // Cobblestones
        for (int i = 0; i < cobbleX.length; i++) {
            cobbleX[i] = rng.nextInt(100);
            cobbleY[i] = rng.nextInt(100);
            cobbleW[i] = 16 + rng.nextInt(20);
        }

        // Mountains
        int x = 0;
        for (int i = 0; i < mtnX.length; i++) {
            mtnX[i]    = x;
            mtnW[i]    = 60  + rng.nextInt(90);
            mtnH[i]    = 55  + rng.nextInt(85);
            mtnSnow[i] = rng.nextBoolean();
            x += mtnW[i] - 20;
        }

        active = true;
        if (animTimer != null) animTimer.stop();
        animTimer = new javax.swing.Timer(16, e -> {
            if (!active) return;
            long el = System.currentTimeMillis() - startTime;

            // Physics on a fixed step driven by the wall clock, so the birds stay in
            // sync with the wall-clock progress `p` even when ticks arrive late.
            int steps = (int)((startTime + el - lastPhysMs) / PHYS_STEP_MS);
            if (steps > 8) steps = 8;                       // don't replay a long stall
            lastPhysMs += (long) steps * PHYS_STEP_MS;
            for (int s = 0; s < steps; s++) {
                for (int i = 0; i < BIRD_COUNT; i++) {
                    birdX[i] += birdVX[i];
                    birdY[i] += birdVY[i];
                    birdWing[i] = (birdWing[i] + 1) % 8;
                }
            }

            if (el > ANIM_MS && !fired) {
                fired  = true;
                active = false;
                animTimer.stop();
                AnimationGuard.release(AnimationGuard.DUNGEON_EXIT, this);
                game.finishDungeonExit();
            }
            if (game.getGamePanel() != null) game.getGamePanel().repaint();
        });
        animTimer.start();
    }

    // ── PAINT ─────────────────────────────────────────────────────────────────

    public void paint(Graphics2D g, int W, int H) {
        if (!active || paintFailed) return;
        // A paint failure must not clear `active`: the timer is still running and its
        // completion callback is still pending, so isActive() has to keep saying true
        // (that is what blocks input for the rest of the run). Just stop drawing.
        try { doPaint(g, W, H); } catch (Exception ex) { ex.printStackTrace(); paintFailed = true; }
    }

    private void doPaint(Graphics2D g, int W, int H) {
        long now     = System.currentTimeMillis();
        long elapsed = now - startTime;
        float p      = Math.min(1f, (float) elapsed / ANIM_MS);

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // How far the wilderness iris has opened
        float irisOpen = Math.min(1f, Math.max(0f, (p - 0.25f) / 0.55f));

        // ── WILDERNESS BACKGROUND ─────────────────────────────────────────────
        paintWilderness(g, W, H, irisOpen, p, now, elapsed);

        // ── DUNGEON ENTRANCE behind player (shrinks as player walks away) ─────
        float entScale = 1f - Math.min(1f, Math.max(0f, (p - 0.40f) / 0.45f)) * 0.75f;
        int   entCX    = W / 2;
        int   skyH     = (int)(H * 0.45f);
        paintExitEntrance(g, entCX, skyH, W, H, entScale, p, now);

        // ── PLAYER WALKS OUT ──────────────────────────────────────────────────
        float walkP = Math.min(1f, Math.max(0f, (p - 0.12f) / 0.72f));
        if (walkP > 0) paintPlayerExiting(g, W, H, skyH, walkP, now);

        // ── BIRDS ─────────────────────────────────────────────────────────────
        float birdAlpha = Math.min(1f, Math.max(0f, (p - 0.50f) / 0.20f));
        if (birdAlpha > 0) {
            for (int i = 0; i < BIRD_COUNT; i++) {
                int bx = (int)(birdX[i] * W);
                int by = (int)(birdY[i] * H);
                paintBird(g, bx, by, birdWing[i], birdAlpha);
            }
        }

        // ── "RETURN TO SURFACE" TEXT ──────────────────────────────────────────
        float textAlpha = Math.min(1f, Math.max(0f, (p - 0.35f) / 0.20f))
                        * (1f - Math.min(1f, Math.max(0f, (p - 0.72f) / 0.12f)));
        if (textAlpha > 0) paintReturnText(g, W, H, textAlpha);

        // ── DUNGEON INTERIOR GLOW (fades out quickly) ─────────────────────────
        float glowFade = 1f - Math.min(1f, p / 0.20f);
        if (glowFade > 0) {
            int glowR = (int)(W * 0.07f);
            for (int r = glowR; r >= 1; r--) {
                float fr = (float) r / glowR;
                g.setColor(new Color(255, 120, 20,
                    Math.max(0, (int)(glowFade * 70 * (1 - fr)))));
                g.fillOval(W/2 - r, skyH - r, r * 2, r * 2);
            }
        }

        // ── FADE TO OVERWORLD ─────────────────────────────────────────────────
        if (p > 0.86f) {
            float fadeP = (p - 0.86f) / 0.14f;
            g.setColor(new Color(0, 0, 0, Math.min(255, (int)(fadeP * fadeP * 255))));
            g.fillRect(0, 0, W, H);
        }
    }

    // ── WILDERNESS ────────────────────────────────────────────────────────────

    private void paintWilderness(Graphics2D g, int W, int H, float reveal, float p,
                                  long now, long elapsed) {
        int skyH = (int)(H * 0.45f);

        // Sky gradient
        for (int y = 0; y < skyH; y++) {
            float t    = (float) y / skyH;
            float warm = Math.max(0f, 1f - p * 1.5f);
            int r  = (int)(SKY_TOP.getRed()   + t * (SKY_BOT.getRed()   - SKY_TOP.getRed())   + warm * 35);
            int gr = (int)(SKY_TOP.getGreen() + t * (SKY_BOT.getGreen() - SKY_TOP.getGreen()) + warm * 18);
            int b  = (int)(SKY_TOP.getBlue()  + t * (SKY_BOT.getBlue()  - SKY_TOP.getBlue()));
            g.setColor(new Color(Math.min(255, r), Math.min(255, gr), Math.min(255, b)));
            g.drawLine(0, y, W, y);
        }

        // Sun
        float sunAlpha = Math.min(1f, reveal * 1.5f);
        if (sunAlpha > 0) {
            int sunX = (int)(W * 0.70f), sunY = (int)(skyH * 0.50f);
            int sunR = (int)(26 * sunAlpha);
            for (int r = sunR + 18; r >= sunR; r--) {
                g.setColor(new Color(255, 200, 60,
                    Math.max(0, (int)(sunAlpha * 38 * (1 - (float)(r - sunR) / 18)))));
                g.fillOval(sunX - r, sunY - r, r * 2, r * 2);
            }
            g.setColor(new Color(255, 230, 100, (int)(sunAlpha * 230)));
            g.fillOval(sunX - sunR, sunY - sunR, sunR * 2, sunR * 2);
        }

        // Clouds
        float cloudAlpha = Math.min(1f, reveal * 2f);
        if (cloudAlpha > 0) {
            for (int i = 0; i < CLOUD_COUNT; i++) {
                int cx = (int)(cloudX[i] + elapsed * 0.006f * (i % 2 == 0 ? 1 : -0.4f)) % (W + 150) - 75;
                paintCloud(g, cx, cloudY[i] * skyH / 200, cloudW[i], cloudAlpha);
            }
        }

        // Mountains
        float mtnAlpha = Math.min(1f, reveal * 1.8f);
        if (mtnAlpha > 0) {
            paintMountains(g, W, skyH, mtnAlpha, 0.6f, MTN_FAR,
                new Color(MTN_FAR.getRed() + 30, MTN_FAR.getGreen() + 30, MTN_FAR.getBlue() + 30));
            paintMountains(g, W, skyH, mtnAlpha, 0.0f, MTN_MID,
                new Color(MTN_MID.getRed() + 20, MTN_MID.getGreen() + 25, MTN_MID.getBlue() + 15));
        }

        // Ground grass strips
        for (int y = skyH; y < H; y++) {
            Color gc = (((y - skyH) / 3) % 2 == 0) ? GRASS_LT : GRASS_DK;
            g.setColor(new Color(
                Math.max(0, (int)(gc.getRed()   * (0.5f + reveal * 0.5f))),
                Math.max(0, (int)(gc.getGreen() * (0.5f + reveal * 0.5f))),
                Math.max(0, (int)(gc.getBlue()  * (0.5f + reveal * 0.5f)))
            ));
            g.drawLine(0, y, W, y);
        }

        // Pine forest band
        float forestAlpha = Math.min(1f, reveal * 2f);
        if (forestAlpha > 0) paintForest(g, W, skyH, forestAlpha);

        // Road from entrance outward
        float roadAlpha = Math.min(1f, reveal * 2f);
        if (roadAlpha > 0) paintRoad(g, W, H, skyH, roadAlpha, elapsed);
    }

    // ── MOUNTAINS ─────────────────────────────────────────────────────────────

    private void paintMountains(Graphics2D g, int W, int skyH, float alpha,
                                  float yOffset, Color col, Color litCol) {
        int baseY = skyH + (int)(yOffset * skyH * 0.4f);
        for (int i = 0; i < mtnX.length; i++) {
            int mx = mtnX[i] % (W + 200) - 100;
            int mh = mtnH[i];
            int mw = mtnW[i];
            int my = baseY;

            for (int col2 = 0; col2 < mw; col2++) {
                float t = (float) col2 / mw;
                float heightT = 1f - Math.abs(t - 0.5f) * 2f;
                heightT = heightT * heightT;
                int topY = my - (int)(mh * heightT);
                int shade = (col2 % 4 < 2) ? 0 : 10;
                Color c = new Color(
                    Math.min(255, (int)(col.getRed()   * alpha) + shade),
                    Math.min(255, (int)(col.getGreen() * alpha) + shade),
                    Math.min(255, (int)(col.getBlue()  * alpha))
                );
                g.setColor(c);
                g.drawLine(mx + col2, topY, mx + col2, my);
            }

            if (mtnSnow[i] && alpha > 0.3f) {
                int snowH = Math.max(4, mh / 4);
                int cx2   = mx + mw / 2;
                int peakY = my - mh;
                g.setColor(new Color(MTN_SNOW.getRed(), MTN_SNOW.getGreen(), MTN_SNOW.getBlue(),
                    (int)(alpha * 220)));
                for (int sy = 0; sy < snowH; sy++) {
                    float sw = (float) sy / snowH;
                    int halfW = (int)(mw * 0.12f * sw);
                    g.drawLine(cx2 - halfW, peakY + sy, cx2 + halfW, peakY + sy);
                }
            }

            g.setColor(new Color(
                Math.min(255, (int)(litCol.getRed()   * alpha)),
                Math.min(255, (int)(litCol.getGreen() * alpha)),
                Math.min(255, (int)(litCol.getBlue()  * alpha))
            ));
            g.drawLine(mx, my, mx + mw / 2, my - mh);
        }
    }

    // Small constant for forest calc
    private static final int H_FIELD = 200;

    // ── FOREST ────────────────────────────────────────────────────────────────

    private void paintForest(Graphics2D g, int W, int skyH, float alpha) {
        int baseY = skyH + (int)(H_FIELD * 0.28f);
        java.util.Random rng = new java.util.Random(0xF1D3B051);
        for (int i = 0; i < 38; i++) {
            int tx = rng.nextInt(W);
            int th = 22 + rng.nextInt(30);
            int tw = 10 + rng.nextInt(12);
            int ty = baseY - rng.nextInt(12);
            // Trunk
            g.setColor(new Color(WOOD_DK.getRed(), WOOD_DK.getGreen(), WOOD_DK.getBlue(),
                (int)(alpha * 200)));
            g.fillRect(tx + tw / 2 - 1, ty, 3, th / 3);
            // Three tiered triangles (pine shape)
            for (int tier = 0; tier < 3; tier++) {
                Color pc = (tier % 2 == 0) ? FOREST_DK : FOREST_MD;
                g.setColor(new Color(pc.getRed(), pc.getGreen(), pc.getBlue(),
                    (int)(alpha * 220)));
                int tierW = tw - tier * 2;
                int tierY = ty - tier * (th / 4) - th / 4;
                int[] xs = {tx + tw / 2, tx + tw / 2 - tierW / 2, tx + tw / 2 + tierW / 2};
                int[] ys = {tierY - th / 3, tierY, tierY};
                g.fillPolygon(xs, ys, 3);
            }
        }
    }

    // ── ROAD ──────────────────────────────────────────────────────────────────

    private void paintRoad(Graphics2D g, int W, int H, int skyH, float alpha, long elapsed) {
        int roadTopW = (int)(W * 0.06f);
        int roadBotW = (int)(W * 0.50f);
        int roadTopX = (W - roadTopW) / 2;
        int roadBotX = (W - roadBotW) / 2;
        int[] rxs = {roadTopX, roadTopX + roadTopW, roadBotX + roadBotW, roadBotX};
        int[] rys = {skyH, skyH, H, H};

        g.setColor(new Color(ROAD_LT.getRed(), ROAD_LT.getGreen(), ROAD_LT.getBlue(),
            (int)(alpha * 220)));
        g.fillPolygon(rxs, rys, 4);

        for (int i = 0; i < cobbleX.length; i++) {
            float rowT   = cobbleY[i] / 100f;
            float scroll = (1f - (elapsed % 600) / 600f);
            float dispT  = (rowT + scroll * 0.5f) % 1f;
            int cy2      = skyH + (int)(dispT * (H - skyH));
            float scale2 = 0.15f + dispT * 0.85f;
            int cx2      = (int)(roadBotX + cobbleX[i] / 100f * roadBotW);
            int cw2      = (int)(cobbleW[i] * scale2);
            int ch2      = (int)(7 * scale2);
            int ca       = (int)(alpha * 180);
            g.setColor(new Color(ROAD_DK.getRed(), ROAD_DK.getGreen(), ROAD_DK.getBlue(), ca));
            g.fillRoundRect(cx2, cy2, Math.max(3, cw2), Math.max(2, ch2), 2, 2);
            g.setColor(new Color(100, 85, 55, ca / 2));
            g.drawRoundRect(cx2, cy2, Math.max(3, cw2), Math.max(2, ch2), 2, 2);
        }

        g.setColor(new Color(90, 75, 45, (int)(alpha * 120)));
        g.drawLine(roadTopX, skyH, roadBotX, H);
        g.drawLine(roadTopX + roadTopW, skyH, roadBotX + roadBotW, H);
    }

    // ── EXIT ENTRANCE (stone frame, visible behind player) ────────────────────

    private void paintExitEntrance(Graphics2D g, int cx, int baseY, int W, int H,
                                    float scale, float p, long now) {
        int eW      = (int)(220 * scale);
        int pillarW = (int)(38  * scale);
        int pillarH = (int)(190 * scale);
        int pillarY = baseY - pillarH;

        // Stone pillars
        paintStonePillar(g, cx - eW / 2, pillarY, pillarW, pillarH, scale);
        paintStonePillar(g, cx + eW / 2 - pillarW, pillarY, pillarW, pillarH, scale);

        // Lintel
        int lintelH = (int)(28 * scale);
        int lintelY = baseY - (int)(180 * scale) - lintelH + (int)(28 * scale);
        paintStoneLintel(g, cx - eW / 2, lintelY, eW, lintelH, scale);

        // Hole (dark interior)
        int holeW = eW - pillarW * 2;
        int holeX = cx - holeW / 2;
        int holeH = (int)(180 * scale) - lintelH;
        int holeY = baseY - holeH;

        g.setColor(ARCH_DARK);
        g.fillRect(holeX, holeY, holeW, holeH);

        // Mortar outline
        g.setColor(MORTAR);
        g.setStroke(new BasicStroke(Math.max(1, (int)(3 * scale))));
        g.drawRect(holeX, holeY, holeW, holeH);
        g.setStroke(new BasicStroke(1f));

        // Torches
        float tf1 = 0.7f + 0.3f * (float)Math.sin(now / 82.0);
        float tf2 = 0.7f + 0.3f * (float)Math.sin(now / 97.0 + 1.1);
        paintTorch(g, cx - eW / 2 + pillarW / 2 - (int)(3 * scale), pillarY + (int)(18 * scale), scale, tf1, now);
        paintTorch(g, cx + eW / 2 - pillarW / 2 - (int)(3 * scale), pillarY + (int)(18 * scale), scale, tf2, now);
    }

    // ── STONE PILLAR ──────────────────────────────────────────────────────────

    private void paintStonePillar(Graphics2D g, int x, int y, int w, int h, float scale) {
        for (int col = 0; col < w; col++) {
            int shade = (col % 4 < 2) ? 0 : 12;
            g.setColor(new Color(
                Math.min(255, STONE_MD.getRed()   + shade),
                Math.min(255, STONE_MD.getGreen() + shade),
                Math.min(255, STONE_MD.getBlue()  + shade)
            ));
            g.drawLine(x + col, y, x + col, y + h);
        }
        int blockH = Math.max(6, (int)(14 * scale));
        int blockW = Math.max(8, (int)(20 * scale));
        for (int row = 0; row * blockH < h; row++) {
            int ry = y + row * blockH;
            g.setColor(MORTAR);
            g.drawLine(x, ry, x + w, ry);
            int offset = (row % 2 == 0) ? 0 : blockW / 2;
            for (int col = offset; col < w; col += blockW) {
                g.setColor(MORTAR);
                g.drawLine(x + col, ry, x + col, ry + blockH);
            }
        }
        g.setColor(STONE_LT);
        g.drawLine(x, y, x, y + h);
        g.setColor(STONE_DK);
        g.fillRect(x, y + h - Math.max(2, (int)(3 * scale)), w, Math.max(2, (int)(3 * scale)));
        // Pillar cap
        int mH = Math.max(3, (int)(12 * scale));
        int mW = Math.max(4, (int)(12 * scale));
        for (int i = 0; i <= w / mW; i++) {
            int mx = x + i * mW;
            if (mx + mW > x + w) break;
            g.setColor(STONE_LT);
            g.fillRect(mx, y - mH, mW - 1, mH);
            g.setColor(STONE_DK);
            g.drawRect(mx, y - mH, mW - 1, mH);
        }
    }

    // ── STONE LINTEL ──────────────────────────────────────────────────────────

    private void paintStoneLintel(Graphics2D g, int x, int y, int w, int h, float scale) {
        for (int col = 0; col < w; col++) {
            int shade = (col % 6 < 3) ? 0 : 10;
            g.setColor(new Color(
                Math.min(255, STONE_MD.getRed()   + shade),
                Math.min(255, STONE_MD.getGreen() + shade),
                Math.min(255, STONE_MD.getBlue()  + shade)
            ));
            g.drawLine(x + col, y, x + col, y + h);
        }
        g.setColor(MORTAR);
        g.drawLine(x, y, x + w, y);
        g.drawLine(x, y + h, x + w, y + h);
        g.setColor(STONE_LT);
        g.drawLine(x, y + 1, x + w, y + 1);
        g.setColor(STONE_DK);
        g.fillRect(x, y + h - 1, w, 1);
    }

    // ── TORCH ─────────────────────────────────────────────────────────────────

    private void paintTorch(Graphics2D g, int x, int y, float scale, float flicker, long now) {
        int tw = Math.max(3, (int)(6  * scale));
        int th = Math.max(5, (int)(12 * scale));
        g.setColor(STONE_DK);
        g.fillRect(x - (int)(4 * scale), y + th / 2, (int)(6 * scale), Math.max(2, (int)(3 * scale)));
        g.setColor(WOOD_DK);
        g.fillRect(x, y, tw, th);
        int flameR = (int)(14 * scale * flicker);
        for (int r = flameR; r >= 1; r--) {
            float fr = (float) r / Math.max(1, flameR);
            g.setColor(new Color(255, (int)(100 * fr), 0,
                Math.max(0, (int)(120 * (1 - fr) * flicker))));
            g.fillOval(x + tw / 2 - r, y - r, r * 2, r * 2);
        }
        g.setColor(TORCH_ORG);
        g.fillRect(x + tw / 2 - 1, y - Math.max(2, (int)(4 * scale)), 3, Math.max(3, (int)(6 * scale)));
        g.setColor(TORCH_YEL);
        g.fillRect(x + tw / 2, y - Math.max(2, (int)(4 * scale)), 2, Math.max(2, (int)(4 * scale)));
    }

    // ── PLAYER EXITING (emerges and grows toward camera) ──────────────────────

    private void paintPlayerExiting(Graphics2D g, int W, int H, int skyH, float walkP, long now) {
        // Starts tiny at entrance threshold, grows toward camera
        float yT     = 0.43f + walkP * 0.27f;    // 0.43 (entrance) → 0.70 (near camera)
        float sizeT  = 0.28f + walkP * 0.72f;     // tiny → full size

        float fadeIn  = Math.min(1f, walkP * 4f);
        int baseAlpha = (int)(fadeIn * 255);
        if (baseAlpha <= 0) return;

        int px    = W / 2;
        int py    = (int)(yT * H);
        int ps    = Math.max(6, (int)(32 * sizeT));
        int frame = (int)(now / 110) % 4;

        // Shadow grows with player
        g.setColor(new Color(0, 0, 0, (int)(50 * fadeIn)));
        g.fillOval(px - ps / 2, py + ps - 2, ps, ps / 4);

        // Body
        g.setColor(new Color(60, 80, 160, baseAlpha));
        g.fillRect(px - ps / 3, py + ps / 3, ps * 2 / 3, ps / 2);

        // Head
        g.setColor(new Color(220, 170, 110, baseAlpha));
        g.fillRect(px - ps / 4, py, ps / 2, ps / 3);

        // Helmet
        g.setColor(new Color(160, 160, 170, baseAlpha));
        g.fillRect(px - ps / 4 - 1, py - ps / 6, ps / 2 + 2, ps / 5);

        // Cape billowing behind as player walks toward us
        g.setColor(new Color(160, 30, 30, baseAlpha));
        int capeOff = (frame % 2 == 0) ? 2 : -1;
        g.fillRect(px - ps / 3 - 3, py + ps / 3, ps / 5, ps / 2 + capeOff);

        // Legs
        int legOff = (frame < 2) ? ps / 6 : -ps / 6;
        g.setColor(new Color(80, 60, 40, baseAlpha));
        g.fillRect(px - ps / 5,  py + ps * 5 / 6, ps / 5, ps / 3 + legOff);
        g.fillRect(px,            py + ps * 5 / 6, ps / 5, ps / 3 - legOff);

        // Sword visible from front
        if (ps > 10) {
            g.setColor(new Color(190, 190, 200, baseAlpha));
            g.drawLine(px - ps / 2, py + ps / 2, px - ps / 3, py + ps * 3 / 4);
            g.setColor(new Color(140, 90, 30, baseAlpha));
            g.fillRect(px - ps / 2 - 1, py + ps * 2 / 5, ps / 8, 3);
        }

        // Backpack visible from front
        if (ps > 14) {
            g.setColor(new Color(100, 70, 35, baseAlpha));
            g.fillRect(px + ps / 4, py + ps / 3, ps / 5, ps / 3);
            g.setColor(new Color(120, 90, 50, baseAlpha));
            g.drawRect(px + ps / 4, py + ps / 3, ps / 5, ps / 3);
        }
    }

    // ── CLOUD ─────────────────────────────────────────────────────────────────

    private void paintCloud(Graphics2D g, int x, int y, int w, float alpha) {
        int h = w / 3;
        int a = (int)(alpha * 220);
        g.setColor(new Color(230, 235, 245, a));
        g.fillOval(x, y + h / 3, w, h * 2 / 3);
        g.fillOval(x + w / 5, y, w * 3 / 5, h);
        g.fillOval(x + w / 2, y + h / 4, w / 2, h * 2 / 3);
        g.setColor(new Color(190, 198, 215, a / 2));
        g.drawLine(x + 4, y + h, x + w - 4, y + h);
    }

    // ── BIRD ──────────────────────────────────────────────────────────────────

    private void paintBird(Graphics2D g, int x, int y, int wingPhase, float alpha) {
        int a = (int)(alpha * 200);
        g.setColor(new Color(30, 25, 20, a));
        int wingY = (wingPhase < 4) ? -1 : 1;
        g.drawLine(x - 4, y + wingY, x, y);
        g.drawLine(x, y, x + 4, y + wingY);
    }

    // ── RETURN TEXT ───────────────────────────────────────────────────────────

    private void paintReturnText(Graphics2D g, int W, int H, float alpha) {
        int a = (int)(alpha * 230);
        String line1 = "Back to the surface";
        String line2 = "THE ADVENTURE CONTINUES!";

        g.setFont(F_SUB);
        FontMetrics fmS = g.getFontMetrics();
        g.setFont(F_EXIT);
        FontMetrics fmT = g.getFontMetrics();

        int x1 = (W - fmS.stringWidth(line1)) / 2;
        int x2 = (W - fmT.stringWidth(line2)) / 2;
        int y1 = (int)(H * 0.82f);

        g.setFont(F_SUB); int y2 = y1 + fmS.getHeight() + 2;

        // Drop shadows
        g.setColor(new Color(0, 0, 0, a / 2));
        g.setFont(F_SUB);  g.drawString(line1, x1 + 2, y1 + 2);
        g.setFont(F_EXIT); g.drawString(line2, x2 + 2, y2 + 2);

        // Text
        g.setFont(F_SUB);
        g.setColor(new Color(200, 230, 160, a));
        g.drawString(line1, x1, y1);

        g.setFont(F_EXIT);
        g.setColor(new Color(100, 220, 140, a));
        g.drawString(line2, x2, y2);
    }
}

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
 * 8-bit dungeon entry animation.
 *
 * The player walks toward a stone-framed dungeon entrance hole that grows
 * (perspective zoom) as the player approaches. Bats emerge from the dark
 * interior. Torches flicker on stone pillars. Darkness closes in from
 * the sides as the player nears the threshold, then an iris wipe to black.
 *
 * Sequence  (total ~3.2 s)
 *   0–15%  : sky, road, grass appear — dungeon entrance is tiny on horizon
 *  10–80%  : entrance grows via perspective zoom; player walks toward it
 *  28–70%  : label "Dungeon Entrance" / "DANGER AHEAD" visible
 *  48–70%  : bats emerge from the opening (dark M-shapes)
 *  60–85%  : vignette — darkness closes in from sides, warm torch glow
 *  80–100% : iris wipe to black
 */
public class DungeonEntryAnimation implements AnimationGuard.Cancellable {

    private static final long ANIM_MS       = AnimationSpeed.scale(3200);
    private static final long ANIM_MS_SCARY = AnimationSpeed.scale(4200); // slower, more dread

    // ── Palette ───────────────────────────────────────────────────────────────
    private static final Color SKY_TOP    = new Color( 60,  90, 160);
    private static final Color SKY_BOT    = new Color(140, 180, 220);
    // Scary variants (volcanic, ominous)
    private static final Color SKY_TOP_S  = new Color( 30,  10,  15);
    private static final Color SKY_BOT_S  = new Color( 80,  25,  20);
    private static final Color CLOUD_COL  = new Color(230, 235, 245);
    private static final Color GRASS_LT   = new Color( 80, 160,  60);
    private static final Color GRASS_DK   = new Color( 50, 110,  35);
    private static final Color ROAD_LT    = new Color(180, 155, 110);
    private static final Color ROAD_DK    = new Color(140, 118,  80);
    private static final Color STONE_LT   = new Color(170, 158, 140);
    private static final Color STONE_MD   = new Color(130, 118, 100);
    private static final Color STONE_DK   = new Color( 80,  70,  58);
    private static final Color MORTAR     = new Color( 90,  82,  70);
    private static final Color ARCH_DARK  = new Color(  5,   3,   1);
    private static final Color TORCH_ORG  = new Color(255, 140,  20);
    private static final Color TORCH_YEL  = new Color(255, 220,  80);
    private static final Color WOOD_DK    = new Color( 80,  55,  30);

    private static final Font F_ENTRY = Fonts.monoBold(16);
    private static final Font F_WARN  = Fonts.monoBold(12);

    private static final Color LAVA_GLOW   = new Color(255, 60, 10);
    private static final Color EMBER_COL   = new Color(255, 140, 30);
    private static final Color EYE_RED     = new Color(255, 20, 20);

    // ── State ─────────────────────────────────────────────────────────────────
    private final Retroquest game;
    private boolean          active    = false;
    private boolean          fired     = false;
    private boolean paintFailed = false;   // doPaint() threw — stop drawing, but stay active
    /** Particle physics advances in fixed steps of this size, driven by the wall clock. */
    private static final long PHYS_STEP_MS = 16;
    private long    lastPhysMs = 0;   // wall-clock anchor for the fixed-step physics
    private boolean          scary     = false;
    /** Display name of the authored dungeon being entered, or null for a procedural one. */
    private String            dungeonTitle = null;
    private long             startTime;
    private long             duration  = ANIM_MS;
    private javax.swing.Timer animTimer;

    // Embers (scary mode)
    private static final int EMBER_COUNT = 25;
    private final float[] emberX  = new float[EMBER_COUNT];
    private final float[] emberY  = new float[EMBER_COUNT];
    private final float[] emberVX = new float[EMBER_COUNT];
    private final float[] emberVY = new float[EMBER_COUNT];
    private final float[] emberSz = new float[EMBER_COUNT];

    // Bats
    private static final int BAT_COUNT = 10;
    private final float[] batX        = new float[BAT_COUNT];
    private final float[] batY        = new float[BAT_COUNT];
    private final float[] batVX       = new float[BAT_COUNT];
    private final float[] batVY       = new float[BAT_COUNT];
    private final int[]   batWingPhase = new int[BAT_COUNT];

    // Clouds
    private static final int CLOUD_COUNT = 3;
    private final int[] cloudX = new int[CLOUD_COUNT];
    private final int[] cloudY = new int[CLOUD_COUNT];
    private final int[] cloudW = new int[CLOUD_COUNT];

    // Cobblestones
    private final int[] cobbleX = new int[24];
    private final int[] cobbleY = new int[24];
    private final int[] cobbleW = new int[24];

    public DungeonEntryAnimation(Retroquest game) {
        this.game = game;
    }

    /** Returns {@code true} while the animation sequence is running. */
    public boolean isActive() { return active; }

    /** Stops this run without firing its completion callback (a newer run superseded it). */
    @Override public void cancel() {
        fired  = true;
        active = false;
        if (animTimer != null) animTimer.stop();
    }

    /**
     * Initialises random elements (bats, clouds, cobblestones) and starts
     * the 3.2-second animation timer. Calls {@link Retroquest#finishDungeonEntry()}
     * when the sequence completes.
     */
    public void start() {
        if (active) return;   // idempotent: never stack a second timer or a second callback
        AnimationGuard.claim(AnimationGuard.DUNGEON_ENTRY, this);

        java.util.Random rng = new java.util.Random(0xDEADCA7EL);
        fired     = false;
        paintFailed = false;
        startTime = System.currentTimeMillis();
        lastPhysMs = startTime;
        scary     = game.getDungeonViewState().isAuthored();
        dungeonTitle = titleFor(game.getDungeonViewState().getAuthoredDungeonName());
        duration  = scary ? ANIM_MS_SCARY : ANIM_MS;

        // Embers (scary mode) — rise from the entrance
        for (int i = 0; i < EMBER_COUNT; i++) {
            emberX[i]  = 0.35f + rng.nextFloat() * 0.30f;
            emberY[i]  = 0.45f + rng.nextFloat() * 0.20f;
            emberVX[i] = (rng.nextFloat() - 0.5f) * 0.001f;
            emberVY[i] = -(0.0004f + rng.nextFloat() * 0.0008f);
            emberSz[i] = 1 + rng.nextFloat() * 3;
        }

        // Bats emerge from the entrance
        for (int i = 0; i < BAT_COUNT; i++) {
            batX[i]  = 0.40f + rng.nextFloat() * 0.20f;
            batY[i]  = 0.35f + rng.nextFloat() * 0.10f;
            float angle = (float)(rng.nextDouble() * Math.PI * 2);
            float spd = 0.0010f + rng.nextFloat() * 0.0015f;
            batVX[i] = (float)(Math.cos(angle) * spd);
            batVY[i] = -(0.0006f + rng.nextFloat() * 0.0008f); // upward
            batWingPhase[i] = rng.nextInt(8);
        }

        // Clouds
        for (int i = 0; i < CLOUD_COUNT; i++) {
            cloudX[i] = 50  + rng.nextInt(700);
            cloudY[i] = 20  + rng.nextInt(60);
            cloudW[i] = 60  + rng.nextInt(100);
        }

        // Cobblestones
        for (int i = 0; i < cobbleX.length; i++) {
            cobbleX[i] = rng.nextInt(100);
            cobbleY[i] = rng.nextInt(100);
            cobbleW[i] = 18 + rng.nextInt(20);
        }

        active = true;
        if (animTimer != null) animTimer.stop();
        animTimer = new javax.swing.Timer(16, e -> {
            if (!active) return;
            long elapsed = System.currentTimeMillis() - startTime;

            // Physics on a fixed step driven by the wall clock, so the particles stay in
            // sync with the wall-clock progress `p` even when ticks arrive late.
            int steps = (int)((startTime + elapsed - lastPhysMs) / PHYS_STEP_MS);
            if (steps > 8) steps = 8;                       // don't replay a long stall
            lastPhysMs += (long) steps * PHYS_STEP_MS;
            for (int s = 0; s < steps; s++) {
                // Animate bats
                for (int i = 0; i < BAT_COUNT; i++) {
                    batX[i] += batVX[i];
                    batY[i] += batVY[i];
                    batVY[i] += 0.000012f;
                    batWingPhase[i] = (batWingPhase[i] + 1) % 8;
                }
                // Animate embers (scary mode)
                if (scary) {
                    for (int i = 0; i < EMBER_COUNT; i++) {
                        emberX[i] += emberVX[i];
                        emberY[i] += emberVY[i];
                        emberVX[i] += (rng.nextFloat() - 0.5f) * 0.0001f; // drift
                    }
                }
            }

            if (elapsed > duration && !fired) {
                fired  = true;
                active = false;
                animTimer.stop();
                AnimationGuard.release(AnimationGuard.DUNGEON_ENTRY, this);
                game.finishDungeonEntry();
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
        float p      = Math.min(1f, (float) elapsed / duration);

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int skyH = (int)(H * 0.45f);

        // ── SKY ───────────────────────────────────────────────────────────────
        Color skyT = scary ? SKY_TOP_S : SKY_TOP;
        Color skyB = scary ? SKY_BOT_S : SKY_BOT;
        for (int y = 0; y < skyH; y++) {
            float t = (float) y / skyH;
            int r  = (int)(skyT.getRed()   + t * (skyB.getRed()   - skyT.getRed()));
            int gr = (int)(skyT.getGreen() + t * (skyB.getGreen() - skyT.getGreen()));
            int b  = (int)(skyT.getBlue()  + t * (skyB.getBlue()  - skyT.getBlue()));
            g.setColor(new Color(r, gr, b));
            g.drawLine(0, y, W, y);
        }

        // ── CLOUDS ────────────────────────────────────────────────────────────
        for (int i = 0; i < CLOUD_COUNT; i++) {
            int cx = (int)(cloudX[i] + elapsed * 0.007f * (i % 2 == 0 ? 1 : -0.5f)) % (W + 150) - 75;
            paintCloud(g, cx, cloudY[i] * H / 200, cloudW[i]);
        }

        // ── GROUND ────────────────────────────────────────────────────────────
        Color groundLt = scary ? new Color(60, 45, 30) : GRASS_LT;
        Color groundDk = scary ? new Color(40, 30, 18) : GRASS_DK;
        for (int y = skyH; y < H; y++) {
            float t = (float)(y - skyH) / (H - skyH);
            Color gc = (y % 4 < 2) ? groundLt : groundDk;
            g.setColor(new Color(
                (int)(gc.getRed()   * (1 - t * 0.3f)),
                (int)(gc.getGreen() * (1 - t * 0.2f)),
                (int)(gc.getBlue()  * (1 - t * 0.1f))
            ));
            g.drawLine(0, y, W, y);
        }

        // ── ROAD ──────────────────────────────────────────────────────────────
        int roadTopW = (int)(W * 0.06f);
        int roadBotW = (int)(W * 0.55f);
        int roadTopX = (W - roadTopW) / 2;
        int roadBotX = (W - roadBotW) / 2;
        int[] roadXs = {roadTopX, roadTopX + roadTopW, roadBotX + roadBotW, roadBotX};
        int[] roadYs = {skyH, skyH, H, H};
        g.setColor(ROAD_LT);
        g.fillPolygon(roadXs, roadYs, 4);

        // Cobblestone pattern
        for (int i = 0; i < cobbleX.length; i++) {
            float rowT = cobbleY[i] / 100f;
            float scroll = (p * 2f % 1f) * (H - skyH);
            int cy2 = (int)(skyH + (rowT * (H - skyH) + scroll) % (H - skyH));
            float scale = 0.15f + rowT * 0.85f;
            int cx2 = (int)(roadBotX + cobbleX[i] / 100f * roadBotW - roadBotW * 0.1f);
            int cw  = (int)(cobbleW[i] * scale);
            int ch  = (int)(8 * scale);
            g.setColor((i % 3 == 0) ? ROAD_DK : ROAD_LT);
            g.fillRoundRect(cx2, cy2, Math.max(4, cw), Math.max(3, ch), 2, 2);
            g.setColor(new Color(100, 85, 55));
            g.drawRoundRect(cx2, cy2, Math.max(4, cw), Math.max(3, ch), 2, 2);
        }

        // ── DUNGEON ENTRANCE (grows as player approaches) ─────────────────────
        float entScale = 0.18f + p * 0.82f;
        int   entCX    = W / 2;
        int   entCY    = skyH;

        paintDungeonEntrance(g, entCX, entCY, W, H, entScale, p, now);

        // ── PLAYER SPRITE WALKING ─────────────────────────────────────────────
        float walkP = Math.min(1f, p / 0.75f);
        paintPlayerWalking(g, W, H, skyH, walkP, now);

        // ── BATS emerge from entrance opening ─────────────────────────────────
        float batAlpha = Math.min(1f, Math.max(0f, (p - 0.48f) / 0.22f));
        if (batAlpha > 0) {
            for (int i = 0; i < BAT_COUNT; i++) {
                int bx = (int)(batX[i] * W);
                int by = (int)(batY[i] * H);
                paintBat(g, bx, by, batWingPhase[i], batAlpha);
            }
        }

        // ── SCARY: Ember particles rising from entrance ──────────────────────
        if (scary && p > 0.15f) {
            float emberAlpha = Math.min(1f, (p - 0.15f) / 0.20f);
            for (int i = 0; i < EMBER_COUNT; i++) {
                int ex = (int)(emberX[i] * W);
                int ey = (int)(emberY[i] * H);
                if (ey < 0 || ey > H || ex < 0 || ex > W) continue;
                float life = 1f - Math.min(1f, Math.max(0f, (0.45f - emberY[i]) / 0.2f));
                int ea = (int)(emberAlpha * life * 220);
                if (ea <= 0) continue;
                g.setColor(new Color(EMBER_COL.getRed(), EMBER_COL.getGreen(), EMBER_COL.getBlue(), ea));
                int sz = Math.max(1, (int)(emberSz[i] * entScale));
                g.fillRect(ex, ey, sz, sz);
                // Glow around ember
                g.setColor(new Color(255, 80, 10, ea / 3));
                g.fillOval(ex - sz, ey - sz, sz * 3, sz * 3);
            }
        }

        // ── SCARY: Glowing red eyes in the darkness ──────────────────────────
        if (scary && p > 0.30f && p < 0.75f) {
            float eyeAlpha = Math.min(1f, (p - 0.30f) / 0.10f) * (1f - Math.max(0f, (p - 0.65f) / 0.10f));
            // Pulsing effect
            float pulse = 0.6f + 0.4f * (float)Math.sin(now / 200.0);
            int ea = (int)(eyeAlpha * pulse * 255);
            if (ea > 0) {
                int eyeSize = Math.max(3, (int)(8 * entScale));
                int eyeSpacing = (int)(30 * entScale);
                int eyeY2 = entCY - (int)(40 * entScale);
                // Left eye
                g.setColor(new Color(EYE_RED.getRed(), EYE_RED.getGreen(), EYE_RED.getBlue(), ea));
                g.fillOval(entCX - eyeSpacing/2 - eyeSize/2, eyeY2, eyeSize, eyeSize);
                // Right eye
                g.fillOval(entCX + eyeSpacing/2 - eyeSize/2, eyeY2, eyeSize, eyeSize);
                // Eye glow
                int glowSz = eyeSize * 3;
                g.setColor(new Color(255, 30, 10, ea / 4));
                g.fillOval(entCX - eyeSpacing/2 - glowSz/2, eyeY2 - glowSz/3, glowSz, glowSz);
                g.fillOval(entCX + eyeSpacing/2 - glowSz/2, eyeY2 - glowSz/3, glowSz, glowSz);
            }
        }

        // ── SCARY: Lava glow from below entrance ─────────────────────────────
        if (scary && p > 0.20f) {
            float lavaP = Math.min(1f, (p - 0.20f) / 0.30f);
            float lavaFlicker = 0.7f + 0.3f * (float)Math.sin(now / 150.0);
            int la = (int)(lavaP * lavaFlicker * 80);
            int glowW2 = (int)(180 * entScale);
            int glowH2 = (int)(60 * entScale);
            g.setColor(new Color(LAVA_GLOW.getRed(), LAVA_GLOW.getGreen(), LAVA_GLOW.getBlue(), la));
            g.fillOval(entCX - glowW2/2, entCY - glowH2/2, glowW2, glowH2);
        }

        // ── SCARY: Screen shake during descent ───────────────────────────────
        if (scary && p > 0.55f && p < 0.85f) {
            float shakeIntensity = Math.min(1f, (p - 0.55f) / 0.15f) * (1f - Math.max(0f, (p - 0.75f) / 0.10f));
            int shakeX = (int)(Math.sin(now / 30.0) * 4 * shakeIntensity);
            int shakeY = (int)(Math.cos(now / 25.0) * 3 * shakeIntensity);
            g.translate(shakeX, shakeY);
        }

        // ── LABEL ─────────────────────────────────────────────────────────────
        float labelAlpha = Math.min(1f, Math.max(0f, (p - 0.28f) / 0.20f))
                         * (1f - Math.min(1f, Math.max(0f, (p - 0.65f) / 0.10f)));
        if (labelAlpha > 0) paintLabel(g, W, H, labelAlpha);

        // ── DESCENT VIGNETTE ──────────────────────────────────────────────────
        float vigP = Math.min(1f, Math.max(0f, (p - 0.60f) / 0.25f));
        if (vigP > 0) paintDescentVignette(g, W, H, entCX, entCY, entScale, vigP, now);

        // ── IRIS WIPE TO BLACK ────────────────────────────────────────────────
        float irisP = Math.min(1f, Math.max(0f, (p - 0.80f) / 0.20f));
        if (irisP > 0) paintIrisWipe(g, W, H, entCX, skyH, irisP);
    }

    // ── DUNGEON ENTRANCE ──────────────────────────────────────────────────────

    private void paintDungeonEntrance(Graphics2D g, int cx, int baseY, int W, int H,
                                       float scale, float p, long now) {
        int eW  = (int)(220 * scale);   // total width of the entrance frame
        int eH  = (int)(180 * scale);   // total height
        int eX  = cx - eW / 2;
        // Stone pillar left
        int pillarW = (int)(38 * scale);
        int pillarH = (int)(190 * scale);
        int pillarY = baseY - pillarH;
        paintStonePillar(g, eX, pillarY, pillarW, pillarH, scale);
        // Stone pillar right
        paintStonePillar(g, eX + eW - pillarW, pillarY, pillarW, pillarH, scale);

        // Stone lintel (top crossbeam)
        int lintelH = (int)(28 * scale);
        int lintelY = baseY - eH - lintelH + (int)(28 * scale);
        paintStoneLintel(g, eX, lintelY, eW, lintelH, scale);

        // Dark interior hole
        int holeW = eW - pillarW * 2;
        int holeX = eX + pillarW;
        int holeH = eH - lintelH;
        int holeY = baseY - holeH;

        g.setColor(ARCH_DARK);
        g.fillRect(holeX, holeY, holeW, holeH);

        // Stone steps descending into darkness (visible inside)
        if (scale > 0.35f) {
            int steps = 5;
            int stepH = Math.max(2, (int)(8 * scale));
            int stepW = holeW - (int)(4 * scale);
            for (int s = 0; s < steps; s++) {
                int sy = holeY + holeH - (s + 1) * stepH;
                int indent = s * (int)(3 * scale);
                Color sc = (s % 2 == 0) ? new Color(45, 35, 25) : new Color(30, 22, 14);
                g.setColor(sc);
                g.fillRect(holeX + indent, sy, stepW - indent * 2, stepH);
                g.setColor(new Color(60, 48, 32));
                g.drawLine(holeX + indent, sy, holeX + indent + stepW - indent * 2, sy);
            }
        }

        // Warning sign with skull — visible when large enough
        if (scale > 0.45f) {
            float signAlpha = Math.min(1f, (scale - 0.45f) / 0.30f);
            int signX = cx - (int)(12 * scale);
            int signY = holeY + (int)(8 * scale);
            paintSkullSign(g, signX, signY, scale, signAlpha, now);
        }

        // Mortar outline around hole
        g.setColor(MORTAR);
        g.setStroke(new BasicStroke(Math.max(1, (int)(3 * scale))));
        g.drawRect(holeX, holeY, holeW, holeH);
        g.setStroke(new BasicStroke(1f));

        // Torches on pillars
        float tf1 = 0.7f + 0.3f * (float)Math.sin(now / 80.0 + 1.2);
        float tf2 = 0.7f + 0.3f * (float)Math.sin(now / 95.0);
        paintTorch(g, eX + pillarW / 2 - (int)(3 * scale), pillarY + (int)(18 * scale), scale, tf1, now);
        paintTorch(g, eX + eW - pillarW / 2 - (int)(3 * scale), pillarY + (int)(18 * scale), scale, tf2, now);
    }

    // ── STONE PILLAR ──────────────────────────────────────────────────────────

    private void paintStonePillar(Graphics2D g, int x, int y, int w, int h, float scale) {
        // Base fill with dithered columns
        for (int col = 0; col < w; col++) {
            int shade = (col % 4 < 2) ? 0 : 12;
            Color c = new Color(
                Math.min(255, STONE_MD.getRed()   + shade),
                Math.min(255, STONE_MD.getGreen() + shade),
                Math.min(255, STONE_MD.getBlue()  + shade)
            );
            g.setColor(c);
            g.drawLine(x + col, y, x + col, y + h);
        }
        // Mortar lines
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
        // Pillar cap (top merlons)
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

    // ── SKULL SIGN ────────────────────────────────────────────────────────────

    private void paintSkullSign(Graphics2D g, int x, int y, float scale, float alpha, long now) {
        int a = (int)(alpha * 220);
        if (a <= 0) return;
        int sw = Math.max(16, (int)(28 * scale));
        int sh = Math.max(12, (int)(22 * scale));

        // Sign background
        g.setColor(new Color(WOOD_DK.getRed(), WOOD_DK.getGreen(), WOOD_DK.getBlue(), a));
        g.fillRect(x, y, sw, sh);
        g.setColor(new Color(120, 88, 45, a));
        g.drawRect(x, y, sw, sh);

        // Skull character
        int fontSize = Math.max(8, (int)(13 * scale));
        g.setFont(Fonts.monoBold(fontSize));
        g.setColor(new Color(220, 30, 30, a));
        FontMetrics fm = g.getFontMetrics();
        String skull = "\u2620";  // ☠
        int tx = x + (sw - fm.stringWidth(skull)) / 2;
        int ty = y + sh / 2 + fm.getAscent() / 2;
        g.drawString(skull, tx, ty);
    }

    // ── TORCH ─────────────────────────────────────────────────────────────────

    private void paintTorch(Graphics2D g, int x, int y, float scale, float flicker, long now) {
        int tw = Math.max(3, (int)(6 * scale));
        int th = Math.max(5, (int)(12 * scale));
        // Bracket arm
        g.setColor(STONE_DK);
        g.fillRect(x - (int)(4 * scale), y + th / 2, (int)(6 * scale), Math.max(2, (int)(3 * scale)));
        // Handle
        g.setColor(WOOD_DK);
        g.fillRect(x, y, tw, th);
        // Flame glow
        int flameR = (int)(14 * scale * flicker);
        for (int r = flameR; r >= 1; r--) {
            float fr = (float) r / Math.max(1, flameR);
            int alpha = (int)(120 * (1 - fr) * flicker);
            g.setColor(new Color(255, (int)(100 * fr), 0, Math.max(0, alpha)));
            g.fillOval(x + tw / 2 - r, y - r, r * 2, r * 2);
        }
        // Flame pixels
        int fx = x + tw / 2;
        int fy = y - Math.max(2, (int)(4 * scale));
        g.setColor(TORCH_ORG);
        g.fillRect(fx - 1, fy, 3, Math.max(3, (int)(6 * scale)));
        g.setColor(TORCH_YEL);
        g.fillRect(fx, fy, 2, Math.max(2, (int)(4 * scale)));
        if (flicker > 0.85f) {
            g.setColor(new Color(255, 255, 200, 180));
            g.fillRect(fx + (int)((Math.random() - 0.5) * 4), fy - 2, 1, 2);
        }
    }

    // ── CLOUD ─────────────────────────────────────────────────────────────────

    private void paintCloud(Graphics2D g, int x, int y, int w) {
        int h = w / 3;
        g.setColor(CLOUD_COL);
        g.fillOval(x, y + h / 3, w, h * 2 / 3);
        g.fillOval(x + w / 5, y, w * 3 / 5, h);
        g.fillOval(x + w / 2, y + h / 4, w / 2, h * 2 / 3);
        g.setColor(new Color(190, 198, 215));
        g.drawLine(x + 4, y + h, x + w - 4, y + h);
    }

    // ── BAT ───────────────────────────────────────────────────────────────────

    private void paintBat(Graphics2D g, int x, int y, int wingPhase, float alpha) {
        int a = (int)(alpha * 200);
        g.setColor(new Color(15, 10, 20, a));
        // Dark M-shape with tiny body pixel
        int wingY = (wingPhase < 4) ? -2 : 1;
        g.drawLine(x - 5, y + wingY + 1, x - 2, y);
        g.drawLine(x - 2, y, x, y + 1);       // left wing
        g.drawLine(x, y + 1, x + 2, y);
        g.drawLine(x + 2, y, x + 5, y + wingY + 1);   // right wing
        // tiny body pixel
        g.setColor(new Color(25, 18, 35, a));
        g.fillRect(x - 1, y + 1, 3, 2);
    }

    // ── PLAYER WALKING ────────────────────────────────────────────────────────

    private void paintPlayerWalking(Graphics2D g, int W, int H, int skyH, float walkP, long now) {
        // Same logic as TownEntryAnimation: walks from lower screen toward entrance
        float yT     = 0.70f - walkP * 0.28f;
        float sizeT  = 1f - walkP * 0.65f;
        float fadeOut = Math.max(0f, (walkP - 0.85f) / 0.15f);
        int   px     = W / 2;
        int   py     = (int)(yT * H);
        int   ps     = Math.max(6, (int)(32 * sizeT));

        int frame = (int)(now / 120) % 4;

        int baseAlpha = (int)((1f - fadeOut) * 255);
        if (baseAlpha <= 0) return;

        // Shadow
        g.setColor(new Color(0, 0, 0, (int)(60 * (1f - fadeOut))));
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

        // Cape
        g.setColor(new Color(160, 30, 30, baseAlpha));
        int capeOff = (frame % 2 == 0) ? 1 : -1;
        g.fillRect(px - ps / 3 - 2, py + ps / 3, ps / 6, ps / 2 + capeOff * 2);

        // Legs — walk cycle
        int legOff = (frame < 2) ? ps / 6 : -ps / 6;
        g.setColor(new Color(80, 60, 40, baseAlpha));
        g.fillRect(px - ps / 5,  py + ps * 5 / 6, ps / 5, ps / 3 + legOff);
        g.fillRect(px,            py + ps * 5 / 6, ps / 5, ps / 3 - legOff);

        // Sword
        if (ps > 10) {
            g.setColor(new Color(190, 190, 200, baseAlpha));
            g.drawLine(px + ps / 3, py + ps / 3, px + ps / 2, py + ps * 3 / 4);
            g.setColor(new Color(140, 90, 30, baseAlpha));
            g.fillRect(px + ps / 3 - 1, py + ps * 2 / 5, 3, ps / 8);
        }
    }

    /**
     * Turns a dungeon group name into the title shown on the entry card:
     * {@code "archive_of_tears"} → {@code "The Archive of Tears"}.
     *
     * @return null for a procedural dungeon, which has no name to show
     */
    private static String titleFor(String dungeonName) {
        if (dungeonName == null || dungeonName.isEmpty()) return null;
        StringBuilder sb = new StringBuilder("The");
        for (String word : dungeonName.split("_")) {
            if (word.isEmpty()) continue;
            sb.append(' ');
            // Joining words stay lowercase — "The Archive of Tears", not "The Archive Of Tears".
            if (sb.length() > 4 && (word.equals("of") || word.equals("the")
                                 || word.equals("and"))) {
                sb.append(word);
            } else {
                sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            }
        }
        return sb.toString();
    }

    // ── LABEL ─────────────────────────────────────────────────────────────────

    private void paintLabel(Graphics2D g, int W, int H, float alpha) {
        int a = (int)(alpha * 230);
        // Every authored dungeon used to announce itself as the Ember Caverns, because the
        // scary variant of this label was written when Pyralis was the only one.
        String line1 = dungeonTitle != null ? dungeonTitle
                     : scary ? "The Deep Places" : "Dungeon Entrance";
        String line2 = scary ? "SOMETHING STIRS BELOW" : "DANGER AHEAD";

        g.setFont(F_ENTRY);
        FontMetrics fm1 = g.getFontMetrics();
        int x1 = (W - fm1.stringWidth(line1)) / 2;
        int y1 = (int)(H * 0.82f);

        g.setFont(F_WARN);
        FontMetrics fm2 = g.getFontMetrics();
        int x2 = (W - fm2.stringWidth(line2)) / 2;
        int y2 = y1 + fm1.getHeight() + 4;

        // Drop shadow
        g.setFont(F_ENTRY);
        g.setColor(new Color(0, 0, 0, a / 2));
        g.drawString(line1, x1 + 2, y1 + 2);
        g.setFont(F_WARN);
        g.drawString(line2, x2 + 2, y2 + 2);

        // Text
        g.setFont(F_ENTRY);
        g.setColor(scary ? new Color(255, 80, 30, a) : new Color(200, 185, 150, a));
        g.drawString(line1, x1, y1);

        g.setFont(F_WARN);
        g.setColor(scary ? new Color(255, 20, 20, a) : new Color(220, 40, 40, a));
        g.drawString(line2, x2, y2);
    }

    // ── DESCENT VIGNETTE ──────────────────────────────────────────────────────

    private void paintDescentVignette(Graphics2D g, int W, int H, int cx, int baseY,
                                       float scale, float vigP, long now) {
        int holeW = (int)((220 - 38 * 2) * scale);
        int holeH = (int)((180 - 28) * scale);
        int holeX = cx - holeW / 2;
        int holeY = baseY - holeH;

        // Dark side panels
        int darkA = (int)(vigP * vigP * 240);
        g.setColor(new Color(0, 0, 0, Math.min(255, darkA)));
        g.fillRect(0, 0, holeX, H);
        g.fillRect(holeX + holeW, 0, W - holeX - holeW, H);
        g.fillRect(0, 0, W, holeY);

        // Warm orange torch glow at centre of hole entrance
        float glowR = holeW * 0.35f * vigP;
        int gcx = cx;
        int gcy = holeY + (int)(holeH * 0.5f);
        for (int r = (int)glowR; r >= 1; r--) {
            float fr = r / Math.max(1, glowR);
            int ga = (int)(vigP * 55 * (1 - fr));
            g.setColor(new Color(255, 130, 20, Math.max(0, ga)));
            g.fillOval(gcx - r, gcy - r, r * 2, r * 2);
        }
    }

    // ── IRIS WIPE ─────────────────────────────────────────────────────────────

    private void paintIrisWipe(Graphics2D g, int W, int H, int cx, int cy, float irisP) {
        float maxR   = (float) Math.sqrt(W * W + H * H);
        float innerR = maxR * (1f - irisP * irisP);

        int steps = 40;
        for (int i = 0; i < steps; i++) {
            float fr    = (float) i / steps;
            float r     = innerR + fr * (maxR - innerR);
            if (r > maxR) break;
            int alpha = (int)(((float) i / steps) * 255 * irisP);
            g.setColor(new Color(0, 0, 0, Math.min(255, alpha)));
            int ri = (int) r;
            g.fillOval(cx - ri, cy - ri, ri * 2, ri * 2);
        }
        if (irisP > 0.85f) {
            float f = (irisP - 0.85f) / 0.15f;
            g.setColor(new Color(0, 0, 0, (int)(f * 255)));
            g.fillRect(0, 0, W, H);
        }
    }
}

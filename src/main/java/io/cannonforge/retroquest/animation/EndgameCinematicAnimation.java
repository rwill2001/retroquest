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
 * Endgame cinematic played when the player steps on the altar at Cradle of
 * Shards level 8.  Seven keys rise onto pedestals, energy beams connect them,
 * the ground cracks, the Scar blazes, narration sets the stakes, then an
 * iris-wipe transitions to the choice overlay.
 *
 * <p>Sequence (~10 s, holds at 65% for keypress):
 * <pre>
 *   0–10%  : fade from black — Cradle interior appears
 *   8–25%  : seven Keys rise to pedestals (god-coloured glyphs)
 *  20–40%  : energy beams connect pedestals
 *  35–50%  : ground cracks + screen shake + ember particles
 *  45–58%  : hero silhouette with blazing Scar
 *  48–65%  : narration (4 lines, typewriter)
 *  65%     : HOLD — wait for keypress
 *  88–100% : iris-wipe to black → callback
 * </pre>
 */
public class EndgameCinematicAnimation {

    private static final long ANIM_MS = AnimationSpeed.scale(10000);
    private static final float HOLD_AT = 0.65f;

    // ── Palette ─────────────────────────────────────────────────────────────
    private static final Color CAVERN_TOP  = new Color(  4,   6,  12);
    private static final Color CAVERN_BOT  = new Color( 30,  25,  20);
    private static final Color FLOOR_DK    = new Color( 15,  12,  18);
    private static final Color FLOOR_LT    = new Color(  8,   6,  12);
    private static final Color PEDESTAL_DK = new Color( 40,  35,  30);
    private static final Color PEDESTAL_LT = new Color( 70,  60,  50);
    private static final Color BEAM_CORE   = new Color(255, 255, 255);
    private static final Color BEAM_GLOW   = new Color(180, 200, 255);
    private static final Color CRACK_HOT   = new Color(255, 120,  40);
    private static final Color CRACK_COOL  = new Color(180,  60,  20);
    private static final Color HERO_BODY   = new Color( 60,  80, 160);
    private static final Color HERO_CAPE   = new Color(140,  28,  28);
    private static final Color HERO_SKIN   = new Color(200, 155, 100);
    private static final Color HERO_HELM   = new Color(150, 152, 162);
    private static final Color SCAR_COL    = new Color(180, 240, 255);
    private static final Color TEXT_COL    = new Color(220, 200, 155);
    private static final Color TEXT_DIM    = new Color(130, 118,  88);

    private static final Font F_NARR  = Fonts.mono    (15);
    private static final Font F_HINT  = Fonts.monoBold(11);

    // God accent colours (same order as God enum: Lirandel..Bellorak)
    private static final Color[] GOD_COLORS = {
        new Color(180, 200, 255),   // Lirandel  — silver-blue
        new Color(255, 120,  40),   // Pyralis   — fiery orange
        new Color(140, 180, 255),   // Zephyrion — storm blue
        new Color( 80, 220, 100),   // Sylvandar — verdant green
        new Color( 60, 180, 200),   // Thalorax  — deep teal
        new Color(160, 170, 200),   // Umbryn    — silver-grey
        new Color(200, 160,  60),   // Bellorak  — war gold
    };

    private static final String[] LINES = {
        "The seven Keys burn in their cradles.",
        "The Scar on your chest tears open with blinding light.",
        "For one heartbeat, you see all of time at once.",
        "You must choose."
    };

    // ── Crack geometry (seeded) ─────────────────────────────────────────────
    private static final int CRACK_COUNT = 6;
    private final int[][] crackX = new int[CRACK_COUNT][8];
    private final int[][] crackY = new int[CRACK_COUNT][8];

    // ── Ember particles ─────────────────────────────────────────────────────
    private static final int EMBER_COUNT = 25;
    private final float[] emberX  = new float[EMBER_COUNT];
    private final float[] emberY  = new float[EMBER_COUNT];
    private final float[] emberVX = new float[EMBER_COUNT];
    private final float[] emberVY = new float[EMBER_COUNT];
    private final int[]   emberA  = new int[EMBER_COUNT];

    // ── State ───────────────────────────────────────────────────────────────
    private final Retroquest game;
    private boolean active        = false;
    private boolean fired         = false;
    private boolean waitingForKey = false;
    private boolean keyWasPressed = false;
    private long    startTime;
    private long    holdElapsed;
    private boolean soundPlayed      = false;
    private boolean crackSoundPlayed = false;
    private boolean scarSoundPlayed  = false;
    private javax.swing.Timer animTimer;

    public EndgameCinematicAnimation(Retroquest game) {
        this.game = game;
    }

    public boolean isActive() { return active; }

    /**
     * Reports "yes" for the whole run, not just the held tableau, so that the
     * dispatcher in {@code Retroquest.handleKey} — which forwards a key only
     * when this returns true — lets the player skip ahead at any point. A short
     * grace period stops a key still down from the descent eating the opening.
     */
    public boolean isWaitingForKey() {
        if (!active || fired) return false;
        if (waitingForKey) return true;
        return (System.currentTimeMillis() - startTime) > SKIP_GRACE_MS;
    }

    /**
     * Any key. The first press runs the build-up out to the held tableau (so the
     * seven Keys, the Scar and the narration are all on screen); the second runs
     * the iris wipe, which still drives the callback into the choice overlay.
     */
    public void pressKey() {
        if (!active || fired) return;

        if (waitingForKey) {
            keyWasPressed = true;
            waitingForKey = false;
            startTime = System.currentTimeMillis() - (long)(0.88f * ANIM_MS);
            return;
        }

        long now = System.currentTimeMillis();
        if (now - startTime < SKIP_GRACE_MS) return;
        // Jump to the hold; the timer raises waitingForKey on the next tick.
        startTime = now - (long)(HOLD_AT * ANIM_MS);
    }

    /** Keys pressed in the first moment of the cinematic are ignored. */
    private static final long SKIP_GRACE_MS = 700;

    public void start() {
        java.util.Random rng = new java.util.Random(0xC4AD1);

        // Seed crack geometry (relative to center, in thousandths)
        for (int c = 0; c < CRACK_COUNT; c++) {
            double angle = (Math.PI * 2 * c) / CRACK_COUNT + rng.nextDouble() * 0.5;
            int px = 500, py = 500; // center in thousandths
            for (int s = 0; s < 8; s++) {
                px += (int)(Math.cos(angle) * (30 + rng.nextInt(40)));
                py += (int)(Math.sin(angle) * (30 + rng.nextInt(40)));
                angle += (rng.nextDouble() - 0.5) * 0.8;
                crackX[c][s] = px;
                crackY[c][s] = py;
            }
        }

        // Seed embers
        for (int i = 0; i < EMBER_COUNT; i++) {
            emberX[i]  = 400 + rng.nextInt(200);
            emberY[i]  = 800 + rng.nextInt(100);
            emberVX[i] = (rng.nextFloat() - 0.5f) * 60;
            emberVY[i] = -(80 + rng.nextFloat() * 120);
            emberA[i]  = 180 + rng.nextInt(75);
        }

        fired            = false;
        soundPlayed      = false;
        crackSoundPlayed = false;
        scarSoundPlayed  = false;
        startTime        = System.currentTimeMillis();
        active           = true;
        keyWasPressed    = false;
        waitingForKey    = false;

        if (animTimer != null) animTimer.stop();
        animTimer = new javax.swing.Timer(16, e -> {
            long elapsed = System.currentTimeMillis() - startTime;
            if (!waitingForKey && !keyWasPressed && elapsed >= (long)(HOLD_AT * ANIM_MS)) {
                waitingForKey = true;
                holdElapsed   = (long)(HOLD_AT * ANIM_MS);
            }
            if (!waitingForKey && elapsed > ANIM_MS && !fired) {
                fired  = true;
                active = false;
                animTimer.stop();
                game.presentCradleChoice();
            }
            if (game.getGamePanel() != null) game.getGamePanel().repaint();
        });
        animTimer.start();
    }

    // ── PAINT ───────────────────────────────────────────────────────────────

    public void paint(Graphics2D g, int W, int H) {
        if (!active) return;
        try { doPaint(g, W, H); }
        catch (Exception ex) { ex.printStackTrace(); active = false; }
    }

    private void doPaint(Graphics2D g, int W, int H) {
        long now         = System.currentTimeMillis();
        long wallElapsed = now - startTime;
        long elapsed     = waitingForKey ? holdElapsed : wallElapsed;
        float p          = Math.min(1f, (float) elapsed / ANIM_MS);

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        float sceneFade = Math.min(1f, p / 0.10f);

        // ── Sound triggers ──────────────────────────────────────────────────
        // The awakening drone runs out well before the cinematic does, leaving
        // the biggest beats — the ground cracking and the Scar tearing open —
        // playing in silence. Both now get a cue of their own.
        if (!soundPlayed && p > 0.08f) {
            soundPlayed = true;
            SoundManager.getInstance().playAltarChime();
            SoundManager.getInstance().playCradleAwaken();
        }
        if (!crackSoundPlayed && p > 0.36f) {
            crackSoundPlayed = true;
            SoundManager.getInstance().playPhaseTransition();
        }
        if (!scarSoundPlayed && p > 0.52f) {
            scarSoundPlayed = true;
            SoundManager.getInstance().playAltarChime();
        }

        // ── Screen shake ────────────────────────────────────────────────────
        int shakeOX = 0, shakeOY = 0;
        if (p > 0.35f && p < 0.55f) {
            float intensity = 1f - Math.abs(p - 0.425f) / 0.075f;
            intensity = Math.max(0f, intensity);
            shakeOX = (int)((Math.random() - 0.5) * 8 * intensity);
            shakeOY = (int)((Math.random() - 0.5) * 6 * intensity);
        }
        g.translate(shakeOX, shakeOY);

        int floorY = (int)(H * 0.60f);

        // ── CAVERN WALLS ────────────────────────────────────────────────────
        for (int y = 0; y < floorY; y++) {
            float t = (float) y / floorY;
            int r = blend(CAVERN_TOP.getRed(),   CAVERN_BOT.getRed(),   t);
            int gr= blend(CAVERN_TOP.getGreen(), CAVERN_BOT.getGreen(), t);
            int b = blend(CAVERN_TOP.getBlue(),  CAVERN_BOT.getBlue(),  t);
            g.setColor(new Color(r, gr, b, clamp((int)(sceneFade * 255))));
            g.drawLine(0, y, W, y);
        }

        // ── FLOOR ───────────────────────────────────────────────────────────
        for (int y = floorY; y < H; y++) {
            float t = (float)(y - floorY) / (H - floorY);
            int r = blend(FLOOR_DK.getRed(),   FLOOR_LT.getRed(),   t);
            int gr= blend(FLOOR_DK.getGreen(), FLOOR_LT.getGreen(), t);
            int b = blend(FLOOR_DK.getBlue(),  FLOOR_LT.getBlue(),  t);
            g.setColor(new Color(r, gr, b, clamp((int)(sceneFade * 255))));
            g.drawLine(0, y, W, y);
        }

        // ── PEDESTALS (semicircle arc) ──────────────────────────────────────
        int pedW = W / 28;
        int pedH = (int)(H * 0.08f);
        int arcCX = W / 2;
        int arcCY = (int)(H * 0.52f);
        int arcR  = (int)(W * 0.32f);

        int[][] pedPos = new int[7][2];
        for (int i = 0; i < 7; i++) {
            double angle = Math.PI * 0.15 + (Math.PI * 0.70 * i / 6.0);
            pedPos[i][0] = arcCX + (int)(Math.cos(angle) * arcR);
            pedPos[i][1] = arcCY + (int)(Math.sin(angle) * arcR * 0.35);
        }

        for (int i = 0; i < 7; i++) {
            int px = pedPos[i][0] - pedW / 2;
            int py = pedPos[i][1];
            int a = clamp((int)(sceneFade * 220));
            // Base
            g.setColor(new Color(PEDESTAL_DK.getRed(), PEDESTAL_DK.getGreen(),
                                 PEDESTAL_DK.getBlue(), a));
            g.fillRect(px, py, pedW, pedH);
            // Cap
            g.setColor(new Color(PEDESTAL_LT.getRed(), PEDESTAL_LT.getGreen(),
                                 PEDESTAL_LT.getBlue(), a));
            g.fillRect(px - 2, py - 3, pedW + 4, 5);
        }

        // ── KEYS RISING (8-25%) ─────────────────────────────────────────────
        for (int i = 0; i < 7; i++) {
            float keyStart = 0.08f + i * 0.015f;
            float keyP = Math.min(1f, Math.max(0f, (p - keyStart) / 0.17f));
            if (keyP <= 0) continue;

            Color kc = GOD_COLORS[i];
            int kx = pedPos[i][0] - 4;
            int ky = pedPos[i][1] - 3 - (int)(keyP * pedH * 0.8f);
            int ka = clamp((int)(keyP * sceneFade * 255));

            // Glow
            g.setColor(new Color(kc.getRed(), kc.getGreen(), kc.getBlue(), clamp(ka / 3)));
            g.fillOval(kx - 6, ky - 6, 20, 20);
            // Key glyph
            g.setColor(new Color(kc.getRed(), kc.getGreen(), kc.getBlue(), ka));
            g.fillRect(kx, ky, 8, 8);
        }

        // ── ENERGY BEAMS (20-40%) ───────────────────────────────────────────
        if (p > 0.20f) {
            for (int i = 0; i < 6; i++) {
                float beamStart = 0.20f + i * 0.025f;
                float beamP = Math.min(1f, Math.max(0f, (p - beamStart) / 0.05f));
                if (beamP <= 0) continue;

                float pulse = 0.7f + 0.3f * (float)Math.sin(wallElapsed / 300.0 + i);
                int ba = clamp((int)(beamP * pulse * sceneFade * 180));

                // Glow line (wide)
                g.setColor(new Color(BEAM_GLOW.getRed(), BEAM_GLOW.getGreen(),
                                     BEAM_GLOW.getBlue(), clamp(ba / 3)));
                g.setStroke(new BasicStroke(4f));
                g.drawLine(pedPos[i][0], pedPos[i][1] - 5,
                           pedPos[i + 1][0], pedPos[i + 1][1] - 5);

                // Core line
                g.setColor(new Color(BEAM_CORE.getRed(), BEAM_CORE.getGreen(),
                                     BEAM_CORE.getBlue(), ba));
                g.setStroke(new BasicStroke(1.5f));
                g.drawLine(pedPos[i][0], pedPos[i][1] - 5,
                           pedPos[i + 1][0], pedPos[i + 1][1] - 5);
            }
            // Close the arc (7th to 1st)
            float beamP = Math.min(1f, Math.max(0f, (p - 0.35f) / 0.05f));
            if (beamP > 0) {
                float pulse = 0.7f + 0.3f * (float)Math.sin(wallElapsed / 300.0 + 6);
                int ba = clamp((int)(beamP * pulse * sceneFade * 180));
                g.setColor(new Color(BEAM_GLOW.getRed(), BEAM_GLOW.getGreen(),
                                     BEAM_GLOW.getBlue(), clamp(ba / 3)));
                g.setStroke(new BasicStroke(4f));
                g.drawLine(pedPos[6][0], pedPos[6][1] - 5,
                           pedPos[0][0], pedPos[0][1] - 5);
                g.setColor(new Color(BEAM_CORE.getRed(), BEAM_CORE.getGreen(),
                                     BEAM_CORE.getBlue(), ba));
                g.setStroke(new BasicStroke(1.5f));
                g.drawLine(pedPos[6][0], pedPos[6][1] - 5,
                           pedPos[0][0], pedPos[0][1] - 5);
            }
        }

        // ── GROUND CRACKS (35-50%) ──────────────────────────────────────────
        if (p > 0.35f) {
            float crackP = Math.min(1f, (p - 0.35f) / 0.15f);
            int segments = (int)(crackP * 8);
            g.setStroke(new BasicStroke(2f));
            for (int c = 0; c < CRACK_COUNT; c++) {
                for (int s = 0; s < segments - 1 && s < 7; s++) {
                    float fade = 1f - (float) s / 8;
                    int cr = blend(CRACK_HOT.getRed(),   CRACK_COOL.getRed(),   1f - fade);
                    int cg = blend(CRACK_HOT.getGreen(), CRACK_COOL.getGreen(), 1f - fade);
                    int cb = blend(CRACK_HOT.getBlue(),  CRACK_COOL.getBlue(),  1f - fade);
                    g.setColor(new Color(cr, cg, cb, clamp((int)(crackP * sceneFade * 220))));
                    g.drawLine(crackX[c][s] * W / 1000, crackY[c][s] * H / 1000,
                               crackX[c][s + 1] * W / 1000, crackY[c][s + 1] * H / 1000);
                }
            }
        }

        // ── EMBERS (35-60%) ─────────────────────────────────────────────────
        if (p > 0.35f && p < 0.65f) {
            float eP = (p - 0.35f) / 0.30f;
            for (int i = 0; i < EMBER_COUNT; i++) {
                float ex = emberX[i] + emberVX[i] * eP;
                float ey = emberY[i] + emberVY[i] * eP;
                float alpha = (1f - eP) * sceneFade;
                int ea = clamp((int)(alpha * emberA[i]));
                g.setColor(new Color(255, 180 + (int)(Math.random() * 60), 40, ea));
                int sx = (int)(ex * W / 1000);
                int sy = (int)(ey * H / 1000);
                g.fillRect(sx, sy, 2, 2);
            }
        }

        // ── HERO SILHOUETTE + SCAR (45-58%) ─────────────────────────────────
        float heroFade = Math.min(1f, Math.max(0f, (p - 0.40f) / 0.10f)) * sceneFade;
        if (heroFade > 0.01f) {
            paintHero(g, W, H, heroFade, p, now);
        }

        // ── NARRATION (48-65%) ──────────────────────────────────────────────
        float[][] lineTimings = {
            {0.44f, 0.05f},
            {0.50f, 0.05f},
            {0.56f, 0.05f},
            {0.60f, 0.04f},
        };
        paintNarration(g, W, H, p, lineTimings, sceneFade);

        // ── IRIS WIPE (88-100%) ─────────────────────────────────────────────
        float irisP = Math.min(1f, Math.max(0f, (p - 0.88f) / 0.12f));
        if (irisP > 0) {
            paintIrisWipe(g, W, H, W / 2, H / 2, irisP);
        }

        // Undo shake
        g.translate(-shakeOX, -shakeOY);

        // ── INITIAL FADE FROM BLACK ─────────────────────────────────────────
        if (sceneFade < 1f) {
            int blackA = (int)((1f - sceneFade) * 255);
            g.setColor(new Color(0, 0, 0, blackA));
            g.fillRect(0, 0, W, H);
        }

        // ── Quiet promise that the build-up can be skipped ──────────────────
        if (!waitingForKey && wallElapsed > SKIP_GRACE_MS && p < 0.63f) {
            g.setFont(F_HINT);
            String hint = "ANY KEY \u25b8";
            FontMetrics fmH = g.getFontMetrics();
            g.setColor(new Color(TEXT_DIM.getRed(), TEXT_DIM.getGreen(),
                                 TEXT_DIM.getBlue(), 90));
            g.drawString(hint, W - fmH.stringWidth(hint) - 18, H - 16);
        }
    }

    // ── HERO ────────────────────────────────────────────────────────────────

    private void paintHero(Graphics2D g, int W, int H, float alpha, float p, long now) {
        int cx = W / 2;
        int cy = (int)(H * 0.50f);
        int s  = (int)(H * 0.06f);
        int a  = clamp((int)(alpha * 230));

        // Shadow
        g.setColor(new Color(0, 0, 0, clamp((int)(alpha * 60))));
        g.fillOval(cx - s / 2, cy + s + s / 4, s, s / 4);

        // Legs
        g.setColor(new Color(80, 60, 40, a));
        g.fillRect(cx - s / 3, cy + s / 2, s / 4, s / 2 + 2);
        g.fillRect(cx + s / 8, cy + s / 2, s / 4, s / 2 + 2);

        // Body
        g.setColor(new Color(HERO_BODY.getRed(), HERO_BODY.getGreen(), HERO_BODY.getBlue(), a));
        g.fillRect(cx - s / 3, cy - s / 4, s * 2 / 3, s * 3 / 4);

        // Cape
        g.setColor(new Color(HERO_CAPE.getRed(), HERO_CAPE.getGreen(), HERO_CAPE.getBlue(), a));
        g.fillRect(cx - s / 3 - 2, cy - s / 6, s / 4, s * 2 / 3);

        // Head
        g.setColor(new Color(HERO_SKIN.getRed(), HERO_SKIN.getGreen(), HERO_SKIN.getBlue(), a));
        g.fillRect(cx - s / 6, cy - s / 2, s / 3, s / 4);

        // Helmet
        g.setColor(new Color(HERO_HELM.getRed(), HERO_HELM.getGreen(), HERO_HELM.getBlue(), a));
        g.fillRect(cx - s / 5, cy - s / 2 - 2, s * 2 / 5, s / 6);

        // Scar blaze (45-58%)
        float scarP = Math.min(1f, Math.max(0f, (p - 0.42f) / 0.08f));
        if (scarP > 0) {
            float pulse = 0.5f + 0.5f * (float)Math.sin(now / 400.0);
            int glowA = (int)(scarP * pulse * 200 * alpha);
            int gcx = cx;
            int gcy = cy;
            for (int r = (int)(s * 1.5f); r >= 1; r--) {
                float fr = r / (s * 1.5f);
                int ga = (int)(glowA * (1 - fr));
                g.setColor(new Color(SCAR_COL.getRed(), SCAR_COL.getGreen(),
                                     SCAR_COL.getBlue(), clamp(ga)));
                g.fillOval(gcx - r, gcy - r / 2, r * 2, r);
            }
            // Bright core
            g.setColor(new Color(240, 255, 255, clamp((int)(glowA * 0.9f))));
            g.fillRect(gcx - 3, gcy - 2, 6, 4);
        }
    }

    // ── NARRATION ───────────────────────────────────────────────────────────

    private void paintNarration(Graphics2D g, int W, int H, float p,
                                 float[][] timings, float sceneFade) {
        g.setFont(F_NARR);
        FontMetrics fm = g.getFontMetrics();
        int lineH  = fm.getHeight() + 5;
        int blockH = LINES.length * lineH;
        int pad    = 14;
        int startY = H - blockH - (int)(H * 0.10f);

        float out = 1f - Math.min(1f, Math.max(0f, (p - 0.86f) / 0.08f));

        float maxFa = 0f;
        for (int i = 0; i < LINES.length; i++) {
            float fa = Math.min(1f, Math.max(0f, (p - timings[i][0]) / timings[i][1]));
            if (fa > maxFa) maxFa = fa;
        }

        // Background panel
        if (maxFa * out > 0.02f) {
            int bgAlpha = (int)(maxFa * out * 185);
            int maxLineW = 0;
            for (String line : LINES) {
                int lw = fm.stringWidth(line);
                if (lw > maxLineW) maxLineW = lw;
            }
            int bgX = (W - maxLineW) / 2 - pad;
            int bgY = startY - pad;
            int bgW = maxLineW + pad * 2;
            int bgH = blockH + pad * 2 + lineH;
            g.setColor(new Color(4, 6, 12, clamp(bgAlpha)));
            g.fillRoundRect(bgX, bgY, bgW, bgH, 8, 8);
            g.setColor(new Color(60, 75, 100, clamp(bgAlpha / 3)));
            g.drawRoundRect(bgX, bgY, bgW, bgH, 8, 8);
        }

        for (int i = 0; i < LINES.length; i++) {
            float t0  = timings[i][0];
            float dur = timings[i][1];
            float fa  = Math.min(1f, Math.max(0f, (p - t0) / dur)) * sceneFade * out;
            if (fa < 0.01f) continue;

            int a = clamp((int)(fa * 235));
            String line = LINES[i];
            int lx = (W - fm.stringWidth(line)) / 2;
            int ly = startY + i * lineH + fm.getAscent();

            g.setColor(new Color(0, 0, 0, clamp(a * 2 / 3)));
            g.drawString(line, lx + 2, ly + 2);
            g.setColor(new Color(TEXT_COL.getRed(), TEXT_COL.getGreen(), TEXT_COL.getBlue(), a));
            g.drawString(line, lx, ly);
        }

        // "Press any key" hint
        float hintP = Math.min(1f, Math.max(0f, (p - 0.63f) / 0.05f)) * out * sceneFade;
        if (hintP > 0.01f) {
            g.setFont(F_HINT);
            String hint = "[ PRESS ANY KEY TO CONTINUE ]";
            FontMetrics fm2 = g.getFontMetrics();
            int hx = (W - fm2.stringWidth(hint)) / 2;
            int hy = startY + blockH + fm2.getAscent() + 6;
            g.setColor(new Color(TEXT_DIM.getRed(), TEXT_DIM.getGreen(),
                                 TEXT_DIM.getBlue(), clamp((int)(hintP * 200))));
            g.drawString(hint, hx, hy);
        }
    }

    // ── IRIS WIPE ───────────────────────────────────────────────────────────

    private void paintIrisWipe(Graphics2D g, int W, int H, int cx, int cy, float irisP) {
        float maxR  = (float) Math.sqrt(W * W + H * H);
        float innerR = maxR * (1f - irisP * irisP);
        int steps = 40;
        for (int i = 0; i < steps; i++) {
            float fr = (float) i / steps;
            float r  = innerR + fr * (maxR - innerR);
            if (r > maxR) break;
            int alpha = (int)(((float) i / steps) * 255 * irisP);
            g.setColor(new Color(0, 0, 0, clamp(alpha)));
            int ri = (int) r;
            g.fillOval(cx - ri, cy - ri, ri * 2, ri * 2);
        }
        if (irisP > 0.85f) {
            float f = (irisP - 0.85f) / 0.15f;
            g.setColor(new Color(0, 0, 0, (int)(f * 255)));
            g.fillRect(0, 0, W, H);
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────
    private static int blend(int a, int b, float t) { return (int)(a + t * (b - a)); }
    private static int clamp(int v) { return Math.max(0, Math.min(255, v)); }
}

package io.cannonforge.retroquest.animation;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import io.cannonforge.retroquest.core.Fonts;
import io.cannonforge.retroquest.core.Retroquest;

/**
 * Intro cinematic played once for new characters.
 *
 * The hero washes ashore on a dark, storm-battered beach at the edge of dawn.
 * Waves roll in, a glowing scar pulses faintly on the hero's chest, and three
 * lines of narration fade in over the scene before an iris-wipe closes to black
 * and the game world begins.
 *
 * Sequence (total ~11 s)
 *   0–12%  : fade from black — stormy dawn sky, ocean, beach appear
 *  10–100% : waves animate continuously
 *  18–42%  : hero silhouette fades in, face-down on the beach
 *  30–38%  : scar glow pulses visible on hero
 *  25–52%  : narration line 1 fades in / holds
 *  45–68%  : narration line 2 fades in / holds
 *  62–82%  : narration line 3 fades in / holds
 *  82–100% : iris-wipe to black
 */
public class WashAshoreAnimation implements AnimationGuard.Cancellable {

    private static final long ANIM_MS = AnimationSpeed.scale(11000);

    // ── Palette ───────────────────────────────────────────────────────────────
    private static final Color SKY_TOP   = new Color( 18,  22,  38);
    private static final Color SKY_BOT   = new Color( 55,  72, 108);
    private static final Color HORIZON   = new Color( 90, 115, 145);
    private static final Color STAR_COL  = new Color(210, 220, 240);
    private static final Color OCEAN_DK  = new Color( 12,  38,  75);
    private static final Color OCEAN_LT  = new Color( 22,  65, 120);
    private static final Color FOAM      = new Color(200, 215, 230);
    private static final Color SAND_LT   = new Color(195, 175, 130);
    private static final Color SAND_DK   = new Color(155, 138,  95);
    private static final Color SAND_WET  = new Color(140, 125,  88);
    private static final Color HERO_BODY = new Color( 60,  80, 160);
    private static final Color HERO_CAPE = new Color(140,  28,  28);
    private static final Color HERO_SKIN = new Color(200, 155, 100);
    private static final Color HERO_HELM = new Color(150, 152, 162);
    private static final Color SCAR_COL  = new Color(180, 240, 255);
    private static final Color TEXT_COL  = new Color(220, 200, 155);
    private static final Color TEXT_DIM  = new Color(130, 118,  88);

    private static final Font F_NARR  = Fonts.mono    (15);
    private static final Font F_TITLE = Fonts.monoBold(11);

    private static final String[] LINES = {
        "You wash ashore, salt in your wounds.",
        "A strange scar burns on your chest — it glows",
        "faintly when you look at the stars.",
        "Something is very wrong with the world.",
        "And it knows your name."
    };

    // ── Wave geometry (seeded) ────────────────────────────────────────────────
    private static final int WAVE_COUNT = 6;
    private final float[] wavePhase  = new float[WAVE_COUNT];
    private final float[] waveSpeed  = new float[WAVE_COUNT];
    private final float[] waveAmp    = new float[WAVE_COUNT];
    private final float[] waveY      = new float[WAVE_COUNT]; // 0=horizon 1=shore

    // ── Star positions ────────────────────────────────────────────────────────
    private static final int STAR_COUNT = 40;
    private final int[] starX = new int[STAR_COUNT];
    private final int[] starY = new int[STAR_COUNT];
    private final int[] starBright = new int[STAR_COUNT];

    // Progress freezes here until the player presses a key (72% through the scene —
    // all text visible, hint showing).  The iris wipe plays after the key is pressed.
    private static final float HOLD_AT = 0.72f;

    // ── State ─────────────────────────────────────────────────────────────────
    private final Retroquest game;
    private boolean active         = false;
    private boolean fired          = false;
    private boolean paintFailed = false;   // doPaint() threw — stop drawing, but stay active
    private boolean waitingForKey  = false;
    private boolean keyWasPressed  = false;
    private long    startTime;
    private long    holdElapsed;   // frozen progress elapsed when waitingForKey
    private javax.swing.Timer animTimer;

    public WashAshoreAnimation(Retroquest game) {
        this.game = game;
    }

    public boolean isActive() { return active; }

    public boolean isWaitingForKey() { return waitingForKey; }

    /**
     * Called when the player presses any key. Skips straight to the iris wipe (p=0.88).
     *
     * <p>This used to do nothing until the scene had already run its 8-second hold, so a
     * player pressing keys during the opening sat through it regardless — the animation
     * looked frozen and unresponsive. Any press now advances it, whether the hold has been
     * reached or not.
     */
    public void pressKey() {
        keyWasPressed = true;
        waitingForKey = false;
        startTime = System.currentTimeMillis() - (long)(0.88f * ANIM_MS);
    }

    /** Stops this run without firing its completion callback (a newer run superseded it). */
    @Override public void cancel() {
        fired  = true;
        active = false;
        if (animTimer != null) animTimer.stop();
    }

    public void start() {
        if (active) return;   // idempotent: never stack a second timer or a second callback
        AnimationGuard.claim(AnimationGuard.WASH_ASHORE, this);

        java.util.Random rng = new java.util.Random(0xBEAC4);

        for (int i = 0; i < WAVE_COUNT; i++) {
            wavePhase[i] = rng.nextFloat() * (float)(Math.PI * 2);
            waveSpeed[i] = 0.0012f + rng.nextFloat() * 0.0010f;
            waveAmp[i]   = 3f + rng.nextFloat() * 5f;
            waveY[i]     = (float) i / (WAVE_COUNT - 1);
        }

        for (int i = 0; i < STAR_COUNT; i++) {
            starX[i]      = rng.nextInt(1000);
            starY[i]      = rng.nextInt(100);
            starBright[i] = 120 + rng.nextInt(120);
        }

        fired     = false;
        paintFailed = false;
        startTime = System.currentTimeMillis();
        active    = true;

        if (animTimer != null) animTimer.stop();
        animTimer = new javax.swing.Timer(16, e -> {
            if (!active) return;
            long elapsed = System.currentTimeMillis() - startTime;
            // Freeze story progress once all text is visible; wait for keypress
            if (!waitingForKey && !keyWasPressed && elapsed >= (long)(HOLD_AT * ANIM_MS)) {
                waitingForKey = true;
                holdElapsed   = (long)(HOLD_AT * ANIM_MS);
            }
            // After key is pressed, let the iris wipe play then fire completion
            if (!waitingForKey && elapsed > ANIM_MS && !fired) {
                fired  = true;
                active = false;
                animTimer.stop();
                AnimationGuard.release(AnimationGuard.WASH_ASHORE, this);
                game.finishWashAshore();
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
        long now         = System.currentTimeMillis();
        long wallElapsed = now - startTime;                          // always advances (waves)
        long elapsed     = waitingForKey ? holdElapsed : wallElapsed; // frozen while waiting
        float p          = Math.min(1f, (float) elapsed / ANIM_MS);

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Overall scene fade-in from black
        float sceneFade = Math.min(1f, p / 0.12f);

        int skyH      = (int)(H * 0.42f);   // horizon line
        int shoreTopY = (int)(H * 0.58f);   // top of sand
        int wetLineY  = (int)(H * 0.70f);   // wet/dry sand boundary

        // ── SKY ───────────────────────────────────────────────────────────────
        for (int y = 0; y < skyH; y++) {
            float t = (float) y / skyH;
            int r = blend(SKY_TOP.getRed(),   SKY_BOT.getRed(),   t);
            int gr= blend(SKY_TOP.getGreen(), SKY_BOT.getGreen(), t);
            int b = blend(SKY_TOP.getBlue(),  SKY_BOT.getBlue(),  t);
            // slight brightening near horizon
            float hFade = Math.max(0f, 1f - (1f - t) * 2f);
            r = clamp(r + (int)(hFade * (HORIZON.getRed()   - r) * 0.5f));
            gr= clamp(gr+ (int)(hFade * (HORIZON.getGreen() - gr) * 0.5f));
            b = clamp(b + (int)(hFade * (HORIZON.getBlue()  - b) * 0.5f));
            int alpha = (int)(sceneFade * 255);
            g.setColor(new Color(r, gr, b, alpha));
            g.drawLine(0, y, W, y);
        }

        // ── STARS (fade out as scene brightens) ───────────────────────────────
        float starAlpha = Math.max(0f, (1f - p * 2.5f)) * sceneFade;
        if (starAlpha > 0.02f) {
            for (int i = 0; i < STAR_COUNT; i++) {
                int sx = starX[i] * W / 1000;
                int sy = starY[i] * skyH / 100;
                int a  = (int)(starBright[i] * starAlpha);
                g.setColor(new Color(STAR_COL.getRed(), STAR_COL.getGreen(), STAR_COL.getBlue(), clamp(a)));
                g.fillRect(sx, sy, (starBright[i] > 200) ? 2 : 1, (starBright[i] > 200) ? 2 : 1);
            }
        }

        // ── OCEAN ─────────────────────────────────────────────────────────────
        for (int y = skyH; y < shoreTopY; y++) {
            float t = (float)(y - skyH) / (shoreTopY - skyH);
            int r = blend(OCEAN_LT.getRed(),   OCEAN_DK.getRed(),   t);
            int gr= blend(OCEAN_LT.getGreen(), OCEAN_DK.getGreen(), t);
            int b = blend(OCEAN_LT.getBlue(),  OCEAN_DK.getBlue(),  t);
            int alpha = (int)(sceneFade * 255);
            g.setColor(new Color(r, gr, b, alpha));
            g.drawLine(0, y, W, y);
        }

        // ── WAVES ─────────────────────────────────────────────────────────────
        for (int wi = WAVE_COUNT - 1; wi >= 0; wi--) {
            float wt    = waveY[wi]; // 0=back 1=front
            int   baseY = skyH + (int)(wt * (shoreTopY - skyH));
            float phase = wavePhase[wi] + wallElapsed * waveSpeed[wi];
            int foamAlpha = (int)(sceneFade * (140 + (int)(wt * 80)));
            g.setColor(new Color(FOAM.getRed(), FOAM.getGreen(), FOAM.getBlue(), clamp(foamAlpha)));
            for (int x = 0; x < W; x++) {
                int wy2 = baseY + (int)(Math.sin(phase + x * 0.018f) * waveAmp[wi]);
                g.drawLine(x, wy2, x, wy2 + 1);
            }
        }

        // ── BEACH ─────────────────────────────────────────────────────────────
        for (int y = shoreTopY; y < H; y++) {
            float t = (float)(y - shoreTopY) / (H - shoreTopY);
            Color base = (y < wetLineY) ? SAND_WET : ((y % 3 < 2) ? SAND_LT : SAND_DK);
            int r = (int)(base.getRed()   * (1 - t * 0.15f));
            int gr= (int)(base.getGreen() * (1 - t * 0.12f));
            int b = (int)(base.getBlue()  * (1 - t * 0.10f));
            int alpha = (int)(sceneFade * 255);
            g.setColor(new Color(clamp(r), clamp(gr), clamp(b), alpha));
            g.drawLine(0, y, W, y);
        }

        // ── HERO WASHED ASHORE ────────────────────────────────────────────────
        float heroFade = Math.min(1f, Math.max(0f, (p - 0.18f) / 0.24f)) * sceneFade;
        if (heroFade > 0.01f) {
            paintHero(g, W, H, shoreTopY, wetLineY, heroFade, p, now);
        }

        // ── NARRATION TEXT ────────────────────────────────────────────────────
        // Lines fade in progressively; all stay visible until iris wipe (0.88)
        float[][] lineTimings = {
            {0.20f, 0.07f},   // line 0
            {0.30f, 0.07f},   // line 1
            {0.38f, 0.07f},   // line 2
            {0.50f, 0.07f},   // line 3
            {0.58f, 0.07f},   // line 4
        };
        paintNarration(g, W, H, p, lineTimings, sceneFade);

        // ── IRIS WIPE TO BLACK ────────────────────────────────────────────────
        float irisP = Math.min(1f, Math.max(0f, (p - 0.88f) / 0.12f));
        if (irisP > 0) {
            paintIrisWipe(g, W, H, W / 2, H / 2, irisP);
        }

        // ── INITIAL FADE-IN OVERLAY ───────────────────────────────────────────
        if (sceneFade < 1f) {
            int blackA = (int)((1f - sceneFade) * 255);
            g.setColor(new Color(0, 0, 0, blackA));
            g.fillRect(0, 0, W, H);
        }
    }

    // ── HERO ──────────────────────────────────────────────────────────────────

    private void paintHero(Graphics2D g, int W, int H, int shoreTopY, int wetLineY,
                            float alpha, float p, long now) {
        int cx = (int)(W * 0.52f);
        int cy = (int)(shoreTopY + (wetLineY - shoreTopY) * 0.35f);
        int s  = (int)(H * 0.052f); // scale relative to screen height
        int a  = (int)(alpha * 230);

        // Shadow
        g.setColor(new Color(0, 0, 0, (int)(alpha * 60)));
        g.fillOval(cx - s, cy + s / 3, s * 2, s / 3);

        // Legs (horizontal, splayed)
        g.setColor(new Color(80, 60, 40, a));
        g.fillRect(cx + s / 4, cy + s / 4, s * 2, s / 3);
        g.fillRect(cx + s / 4, cy + s / 2, s + s / 2, s / 3);

        // Body (torso horizontal)
        g.setColor(new Color(HERO_BODY.getRed(), HERO_BODY.getGreen(), HERO_BODY.getBlue(), a));
        g.fillRect(cx - s, cy, s * 2, s / 2);

        // Cape trailing behind
        g.setColor(new Color(HERO_CAPE.getRed(), HERO_CAPE.getGreen(), HERO_CAPE.getBlue(), a));
        g.fillRect(cx - s * 2, cy - s / 6, s, s / 2);

        // Head
        g.setColor(new Color(HERO_SKIN.getRed(), HERO_SKIN.getGreen(), HERO_SKIN.getBlue(), a));
        g.fillRect(cx - s - s / 3, cy - s / 5, s * 2 / 3, s / 2);

        // Helmet
        g.setColor(new Color(HERO_HELM.getRed(), HERO_HELM.getGreen(), HERO_HELM.getBlue(), a));
        g.fillRect(cx - s - s / 3 - 1, cy - s / 3, s * 2 / 3 + 2, s / 5);

        // Arm reaching toward shore
        g.setColor(new Color(HERO_SKIN.getRed(), HERO_SKIN.getGreen(), HERO_SKIN.getBlue(), a));
        g.fillRect(cx - s / 4, cy + s / 8, s + s / 4, s / 4);

        // Sword on ground
        g.setColor(new Color(170, 170, 185, a));
        g.drawLine(cx - s / 2, cy + s * 3 / 4, cx + s + s / 3, cy + s * 3 / 4);
        g.setColor(new Color(120, 80, 28, a));
        g.fillRect(cx + s / 4, cy + s * 5 / 8, s / 2, s / 8);

        // Scar glow on chest
        float scarP = Math.min(1f, Math.max(0f, (p - 0.22f) / 0.08f));
        if (scarP > 0) {
            float pulse = 0.6f + 0.4f * (float)Math.sin(now / 700.0);
            int glowA = (int)(scarP * pulse * 150);
            int gcx = cx - s / 2;
            int gcy2 = cy + s / 6;
            for (int r = s / 2; r >= 1; r--) {
                float fr = r / (s / 2f);
                int ga = (int)(glowA * (1 - fr));
                g.setColor(new Color(SCAR_COL.getRed(), SCAR_COL.getGreen(), SCAR_COL.getBlue(), clamp(ga)));
                g.fillOval(gcx - r, gcy2 - r / 2, r * 2, r);
            }
            // Bright core
            g.setColor(new Color(240, 255, 255, clamp((int)(glowA * 0.8f))));
            g.fillRect(gcx - 2, gcy2 - 1, 4, 2);
        }
    }

    // ── NARRATION ─────────────────────────────────────────────────────────────

    private void paintNarration(Graphics2D g, int W, int H, float p,
                                 float[][] timings, float sceneFade) {
        g.setFont(F_NARR);
        FontMetrics fm = g.getFontMetrics();
        int lineH  = fm.getHeight() + 5;
        int blockH = LINES.length * lineH;
        int pad    = 14;
        int startY = H - blockH - (int)(H * 0.10f);

        // Overall fade: text holds until iris closes at 0.88
        float out = 1f - Math.min(1f, Math.max(0f, (p - 0.86f) / 0.08f));

        // How many lines are visible (used for background sizing)
        float maxFa = 0f;
        for (int i = 0; i < LINES.length; i++) {
            float fa = Math.min(1f, Math.max(0f, (p - timings[i][0]) / timings[i][1]));
            if (fa > maxFa) maxFa = fa;
        }

        // Dark semi-transparent background panel behind all text
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
            int bgH = blockH + pad * 2 + lineH; // extra row for hint
            g.setColor(new Color(4, 6, 12, clamp(bgAlpha)));
            g.fillRoundRect(bgX, bgY, bgW, bgH, 8, 8);
            // subtle border
            g.setColor(new Color(60, 75, 100, clamp(bgAlpha / 3)));
            g.drawRoundRect(bgX, bgY, bgW, bgH, 8, 8);
        }

        for (int i = 0; i < LINES.length; i++) {
            float t0  = timings[i][0];
            float dur = timings[i][1];
            float fa  = Math.min(1f, Math.max(0f, (p - t0) / dur)) * sceneFade * out;
            if (fa < 0.01f) continue;

            int a = (int)(fa * 235);
            String line = LINES[i];
            int lx = (W - fm.stringWidth(line)) / 2;
            int ly = startY + i * lineH + fm.getAscent();

            // Drop shadow (2px offset)
            g.setFont(F_NARR);
            g.setColor(new Color(0, 0, 0, clamp(a * 2 / 3)));
            g.drawString(line, lx + 2, ly + 2);

            // Main text
            g.setColor(new Color(TEXT_COL.getRed(), TEXT_COL.getGreen(), TEXT_COL.getBlue(), a));
            g.drawString(line, lx, ly);
        }

        // "Press any key" hint — visible once all lines have appeared, fades with text
        float hintP = Math.min(1f, Math.max(0f, (p - 0.68f) / 0.10f)) * out * sceneFade;
        if (hintP > 0.01f) {
            g.setFont(F_TITLE);
            String hint = "[ PRESS ANY KEY TO CONTINUE ]";
            FontMetrics fm2 = g.getFontMetrics();
            int hx = (W - fm2.stringWidth(hint)) / 2;
            int hy = startY + blockH + fm2.getAscent() + 6;
            g.setColor(new Color(TEXT_DIM.getRed(), TEXT_DIM.getGreen(), TEXT_DIM.getBlue(),
                                 clamp((int)(hintP * 200))));
            g.drawString(hint, hx, hy);
        }
    }

    // ── IRIS WIPE ─────────────────────────────────────────────────────────────

    private void paintIrisWipe(Graphics2D g, int W, int H, int cx, int cy, float irisP) {
        float maxR  = (float) Math.sqrt(W * W + H * H);
        float innerR = maxR * (1f - irisP * irisP);
        int steps = 40;
        for (int i = 0; i < steps; i++) {
            float fr    = (float) i / steps;
            float r     = innerR + fr * (maxR - innerR);
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

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static int blend(int a, int b, float t) { return (int)(a + t * (b - a)); }
    private static int clamp(int v) { return Math.max(0, Math.min(255, v)); }
}

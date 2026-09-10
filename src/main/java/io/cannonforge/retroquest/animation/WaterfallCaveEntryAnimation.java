package io.cannonforge.retroquest.animation;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import io.cannonforge.retroquest.core.Fonts;
import io.cannonforge.retroquest.core.Retroquest;
import io.cannonforge.retroquest.core.Town;

/**
 * 8-bit waterfall cave entry animation.
 *
 * Player approaches a mossy cliff with a cascading waterfall. The falls part
 * vertically, revealing a dark moss-ringed cave mouth behind them. Mist rises
 * from a pool at the base and ferns frame the scene. Iris-wipes to black.
 *
 * Sequence (total ~3.0 s)
 *   0–12%  : soft sky + forest silhouette + mist fade in
 *  10–55%  : cliff face grows via perspective zoom; waterfall animates
 *  25–70%  : player walks toward the falls (shrinking)
 *  42–75%  : waterfall parts, dark cave mouth emerges behind it
 *  55–85%  : moss ring + hanging vines around the cave; pool ripples
 *  82–100% : iris-wipe toward the cave mouth
 */
public class WaterfallCaveEntryAnimation implements AnimationGuard.Cancellable {

    private static final long ANIM_MS = AnimationSpeed.scale(3000);

    private static final Color SKY_TOP   = new Color( 80, 110, 130);
    private static final Color SKY_BOT   = new Color(150, 180, 190);
    private static final Color MIST      = new Color(220, 230, 235);
    private static final Color FOREST_DK = new Color( 18,  50,  28);
    private static final Color FOREST_MD = new Color( 32,  78,  40);
    private static final Color MOSS_LT   = new Color( 95, 150,  70);
    private static final Color MOSS_DK   = new Color( 50, 100,  45);
    private static final Color FERN      = new Color( 55, 130,  55);
    private static final Color ROCK_LT   = new Color(120, 120, 110);
    private static final Color ROCK_DK   = new Color( 50,  50,  45);
    private static final Color WATER_LT  = new Color(190, 220, 240);
    private static final Color WATER_MD  = new Color(120, 170, 220);
    private static final Color WATER_DK  = new Color( 60, 100, 160);
    private static final Color FOAM      = new Color(240, 250, 255);
    private static final Color CAVE_DARK = new Color(  5,   8,  12);

    private static final Font F_TITLE = Fonts.monoBold(16);
    private static final Font F_SUB   = Fonts.mono    (11);

    private final Retroquest game;
    private final String     townName;
    private boolean          active    = false;
    private boolean          fired     = false;
    private boolean paintFailed = false;   // doPaint() threw — stop drawing, but stay active
    /** Particle physics advances in fixed steps of this size, driven by the wall clock. */
    private static final long PHYS_STEP_MS = 16;
    private long    lastPhysMs = 0;   // wall-clock anchor for the fixed-step physics
    private long             startTime;
    private javax.swing.Timer animTimer;

    private static final int MIST_COUNT = 26;
    private final float[] mistX  = new float[MIST_COUNT];
    private final float[] mistY  = new float[MIST_COUNT];
    private final float[] mistVX = new float[MIST_COUNT];
    private final float[] mistR  = new float[MIST_COUNT];

    private static final int STRAND_COUNT = 14;
    private final float[] strandPhase = new float[STRAND_COUNT];

    private static final int FERN_COUNT = 8;
    private final float[] fernX     = new float[FERN_COUNT];
    private final float[] fernY     = new float[FERN_COUNT];
    private final float[] fernScale = new float[FERN_COUNT];

    public WaterfallCaveEntryAnimation(Retroquest game, String townName) {
        this.game     = game;
        this.townName = townName;
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
        AnimationGuard.claim(AnimationGuard.TOWN_ENTRY, this);

        java.util.Random rng = new java.util.Random(townName.hashCode() * 31L);
        fired     = false;
        paintFailed = false;
        startTime = System.currentTimeMillis();
        lastPhysMs = startTime;

        for (int i = 0; i < MIST_COUNT; i++) {
            mistX[i]  = rng.nextFloat();
            mistY[i]  = 0.45f + rng.nextFloat() * 0.45f;
            mistVX[i] = -0.00015f + rng.nextFloat() * 0.0003f;
            mistR[i]  = 10f + rng.nextFloat() * 24f;
        }
        for (int i = 0; i < STRAND_COUNT; i++) {
            strandPhase[i] = rng.nextFloat() * 1000f;
        }
        for (int i = 0; i < FERN_COUNT; i++) {
            fernX[i]     = (i % 2 == 0) ? rng.nextFloat() * 0.22f : 0.78f + rng.nextFloat() * 0.22f;
            fernY[i]     = 0.55f + rng.nextFloat() * 0.35f;
            fernScale[i] = 0.6f + rng.nextFloat() * 0.8f;
        }

        active = true;
        if (animTimer != null) animTimer.stop();
        animTimer = new javax.swing.Timer(16, e -> {
            if (!active) return;
            long elapsed = System.currentTimeMillis() - startTime;

            // Physics on a fixed step driven by the wall clock, so the mist stays in
            // sync with the wall-clock progress `p` even when ticks arrive late.
            int steps = (int)((startTime + elapsed - lastPhysMs) / PHYS_STEP_MS);
            if (steps > 8) steps = 8;                       // don't replay a long stall
            lastPhysMs += (long) steps * PHYS_STEP_MS;
            for (int s = 0; s < steps; s++) {
                for (int i = 0; i < MIST_COUNT; i++) {
                    mistX[i] += mistVX[i];
                    mistY[i] -= 0.0008f;
                    if (mistY[i] < 0.35f) {
                        mistY[i] = 0.88f;
                        mistX[i] = (float) Math.random();
                    }
                }
            }

            if (elapsed > ANIM_MS && !fired) {
                fired  = true;
                active = false;
                animTimer.stop();
                AnimationGuard.release(AnimationGuard.TOWN_ENTRY, this);
                game.finishTownEntry();
            }
            if (game.getGamePanel() != null) game.getGamePanel().repaint();
        });
        animTimer.start();
    }

    public void paint(Graphics2D g, int W, int H) {
        if (!active || paintFailed) return;
        // A paint failure must not clear `active`: the timer is still running and its
        // completion callback is still pending, so isActive() has to keep saying true
        // (that is what blocks input for the rest of the run). Just stop drawing.
        try { doPaint(g, W, H); } catch (Exception ex) { ex.printStackTrace(); paintFailed = true; }
    }

    private void doPaint(Graphics2D g, int W, int H) {
        long  now     = System.currentTimeMillis();
        long  elapsed = now - startTime;
        float p       = Math.min(1f, (float) elapsed / ANIM_MS);

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int skyH = (int)(H * 0.35f);

        // Sky
        for (int y = 0; y < skyH; y++) {
            float t = (float) y / skyH;
            int r  = (int)(SKY_TOP.getRed()   + t * (SKY_BOT.getRed()   - SKY_TOP.getRed()));
            int gr = (int)(SKY_TOP.getGreen() + t * (SKY_BOT.getGreen() - SKY_TOP.getGreen()));
            int b  = (int)(SKY_TOP.getBlue()  + t * (SKY_BOT.getBlue()  - SKY_TOP.getBlue()));
            g.setColor(new Color(r, gr, b));
            g.drawLine(0, y, W, y);
        }

        // Forest canopy silhouette
        paintForestSilhouette(g, W, skyH);

        // Mossy ground
        for (int y = skyH; y < H; y++) {
            float t = (float)(y - skyH) / Math.max(1, (H - skyH));
            int r  = (int)(FOREST_DK.getRed()   + t * 30);
            int gg = (int)(FOREST_DK.getGreen() + t * 50);
            int b  = (int)(FOREST_DK.getBlue()  + t * 20);
            Color gc = (y % 3 == 0) ? new Color(r, gg, b) : new Color(r + 5, gg + 10, b + 5);
            g.setColor(gc);
            g.drawLine(0, y, W, y);
        }

        float cliffScale = 0.22f + p * 0.78f;
        int cliffCX = W / 2;
        int cliffTop = skyH - (int)(60 * cliffScale);
        int cliffH   = (int)(H * 0.82f * cliffScale);
        int cliffW   = (int)(W * 0.80f * cliffScale);
        paintCliffFace(g, cliffCX, cliffTop, cliffW, cliffH, cliffScale, p, now);

        paintFerns(g, W, H);
        paintMist(g, W, H);

        float walkP = Math.min(1f, p / 0.70f);
        paintPlayerWalking(g, W, H, walkP, now);

        float nameP = Math.min(1f, Math.max(0f, (p - 0.28f) / 0.25f))
                    * (1f - Math.min(1f, Math.max(0f, (p - 0.78f) / 0.10f)));
        if (nameP > 0) paintCaveName(g, W, H, nameP);

        float irisP = Math.min(1f, Math.max(0f, (p - 0.82f) / 0.18f));
        if (irisP > 0) paintIrisWipe(g, W, H, cliffCX, cliffTop + cliffH / 2, irisP);
    }

    private void paintForestSilhouette(Graphics2D g, int W, int skyH) {
        g.setColor(FOREST_MD);
        int pts = 9;
        int[] xs = new int[pts + 2];
        int[] ys = new int[pts + 2];
        for (int i = 0; i < pts; i++) {
            xs[i] = i * W / (pts - 1);
            ys[i] = skyH - (int)(32 + Math.sin(i * 1.7) * 14);
        }
        xs[pts]     = W; ys[pts]     = skyH;
        xs[pts + 1] = 0; ys[pts + 1] = skyH;
        g.fillPolygon(xs, ys, pts + 2);
    }

    private void paintCliffFace(Graphics2D g, int cx, int top, int w, int h,
                                float scale, float p, long now) {
        int x = cx - w / 2;

        // Cliff body — banded rock
        for (int row = 0; row < h; row++) {
            int shadeT = row * 70 / Math.max(1, h);
            int r  = Math.max(30, ROCK_LT.getRed()   - shadeT);
            int gr = Math.max(30, ROCK_LT.getGreen() - shadeT);
            int b  = Math.max(30, ROCK_LT.getBlue()  - shadeT);
            Color rc = (row % 4 < 2)
                ? new Color(r, gr, b)
                : new Color(Math.max(0, r - 10), Math.max(0, gr - 10), Math.max(0, b - 10));
            g.setColor(rc);
            g.drawLine(x, top + row, x + w, top + row);
        }

        // Block texture
        int blockH = Math.max(8,  (int)(18 * scale));
        int blockW = Math.max(16, (int)(28 * scale));
        for (int ry = 0; ry < h; ry += blockH) {
            int offset = ((ry / blockH) % 2 == 0) ? 0 : blockW / 2;
            for (int rx = offset; rx < w; rx += blockW) {
                g.setColor(ROCK_DK);
                g.drawLine(x + rx, top + ry, x + rx, top + ry + blockH);
                g.drawLine(x + rx, top + ry, x + rx + blockW, top + ry);
            }
        }

        // Moss fringe along the top
        int mossThick = Math.max(4, (int)(12 * scale));
        for (int mx = 0; mx < w; mx++) {
            int bump = (int)(Math.sin(mx * 0.15) * 3);
            g.setColor((mx % 3 == 0) ? MOSS_DK : MOSS_LT);
            g.fillRect(x + mx, top - bump, 1, mossThick + bump);
        }

        // Cave mouth — revealed as the waterfall parts
        float partP = Math.min(1f, Math.max(0f, (p - 0.42f) / 0.33f));
        int caveW = (int)(w * 0.28f);
        int caveH = (int)(h * 0.55f);
        int caveX = cx - caveW / 2;
        int caveY = top + (int)(h * 0.18f);
        paintCaveMouth(g, caveX, caveY, caveW, caveH, partP);

        // Waterfall — drawn after the cave so parting halves occlude it
        paintWaterfall(g, cx, top, w, h, scale, partP, now);

        // Moss ring + vines appear once the fall has parted
        paintCaveMossRing(g, caveX, caveY, caveW, caveH, scale, partP);

        // Splash pool at the base of the falls
        paintPool(g, cx, top + h, w, scale, now);
    }

    private void paintCaveMouth(Graphics2D g, int x, int y, int w, int h, float reveal) {
        int a = (int)(255 * Math.min(1f, reveal * 1.3f));
        g.setColor(new Color(CAVE_DARK.getRed(), CAVE_DARK.getGreen(), CAVE_DARK.getBlue(), a));
        g.fillRect(x, y + h / 3, w, h - h / 3);
        g.fillArc(x, y, w, (int)(h * 0.67f), 0, 180);

        if (reveal > 0.3f) {
            int depth = (int)((reveal - 0.3f) / 0.7f * 80);
            for (int r = 0; r < 4; r++) {
                g.setColor(new Color(20, 25, 35, Math.max(0, depth - r * 15)));
                g.drawArc(x + r, y + r, w - r * 2, (int)(h * 0.67f) - r * 2, 0, 180);
            }
        }
    }

    private void paintWaterfall(Graphics2D g, int cx, int top, int w, int h,
                                float scale, float partP, long now) {
        int fallW = (int)(w * 0.36f);
        int fallH = (int)(h * 0.82f);
        int fallX = cx - fallW / 2;
        int fallY = top + (int)(h * 0.05f);

        int partOffset = (int)(partP * fallW * 0.35f);

        for (int side = 0; side < 2; side++) {
            int halfW = fallW / 2;
            int halfX = (side == 0) ? fallX - partOffset : fallX + halfW + partOffset;

            g.setColor(WATER_DK);
            g.fillRect(halfX, fallY, halfW, fallH);

            int strands = Math.max(3, STRAND_COUNT / 2);
            for (int s = 0; s < strands; s++) {
                float phase = strandPhase[s + side * strands] + now * 0.006f;
                int sx   = halfX + s * halfW / strands;
                int swd  = Math.max(1, halfW / (strands + 1));
                for (int sy = 0; sy < fallH; sy += 6) {
                    int phasedY = (int)((sy + phase * 20) % fallH);
                    Color wc = (sy / 6) % 3 == 0
                        ? FOAM
                        : (sy / 6) % 3 == 1 ? WATER_LT : WATER_MD;
                    g.setColor(wc);
                    g.fillRect(sx, fallY + phasedY, swd, 4);
                }
            }

            g.setColor(FOAM);
            g.drawLine(halfX, fallY, halfX, fallY + fallH);
            g.drawLine(halfX + halfW - 1, fallY, halfX + halfW - 1, fallY + fallH);
        }

        // Lip foam crowning the top of the falls
        g.setColor(FOAM);
        g.fillRect(fallX - partOffset - 4, fallY - 2, fallW + partOffset * 2 + 8, 3);

        // Splash ring at the base of the cascade
        int splashY = fallY + fallH;
        for (int i = 0; i < 10; i++) {
            double ang = i / 10.0 * Math.PI;
            int sx = cx + (int)(Math.cos(ang) * fallW * 0.5);
            int sy = splashY - (int)(Math.sin(ang) * 6 * scale);
            g.setColor(FOAM);
            g.fillRect(sx - 1, sy - 1, 3, 3);
        }
    }

    private void paintCaveMossRing(Graphics2D g, int x, int y, int w, int h,
                                   float scale, float reveal) {
        if (reveal <= 0.1f) return;
        int alpha = (int)(Math.min(1f, reveal * 1.3f) * 255);

        for (int ang = 0; ang < 180; ang += 4) {
            double rad = Math.toRadians(ang);
            int rx = x + w / 2 + (int)(Math.cos(rad) * w * 0.5);
            int ry = y + (int)(h * 0.33f) - (int)(Math.sin(rad) * h * 0.33f);
            Color c = (ang % 8 < 4) ? MOSS_LT : MOSS_DK;
            g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha));
            int dotR = Math.max(2, (int)(4 * scale));
            g.fillOval(rx - dotR / 2, ry - dotR / 2, dotR, dotR);
        }

        for (int v = 0; v < 5; v++) {
            int vx = x + v * w / 4;
            int vy = y + 4;
            int vh = (int)(14 * scale + v * 3);
            g.setColor(new Color(MOSS_DK.getRed(), MOSS_DK.getGreen(), MOSS_DK.getBlue(), alpha));
            g.drawLine(vx, vy, vx, vy + vh);
            g.setColor(new Color(MOSS_LT.getRed(), MOSS_LT.getGreen(), MOSS_LT.getBlue(), alpha));
            g.fillRect(vx - 1, vy + vh, 3, 2);
        }
    }

    private void paintPool(Graphics2D g, int cx, int baseY, int w, float scale, long now) {
        int poolW = (int)(w * 0.5f);
        int poolH = (int)(18 * scale);
        if (poolW <= 0 || poolH <= 0) return;
        int poolX = cx - poolW / 2;
        int poolY = baseY - poolH / 2;

        g.setColor(WATER_DK);
        g.fillOval(poolX, poolY, poolW, poolH);
        g.setColor(WATER_MD);
        g.fillOval(poolX + 4, poolY + 2, poolW - 8, poolH - 4);

        for (int i = 0; i < 3; i++) {
            float phase = (now / 400f + i * 0.7f) % 1f;
            int rw = (int)(poolW * 0.1f + phase * poolW * 0.35f);
            int rh = (int)(poolH * 0.2f + phase * poolH * 0.5f);
            int alpha = (int)((1f - phase) * 140);
            g.setColor(new Color(FOAM.getRed(), FOAM.getGreen(), FOAM.getBlue(), alpha));
            g.drawOval(cx - rw / 2, poolY + poolH / 2 - rh / 2, rw, rh);
        }
    }

    private void paintFerns(Graphics2D g, int W, int H) {
        for (int i = 0; i < FERN_COUNT; i++) {
            int fx = (int)(fernX[i] * W);
            int fy = (int)(fernY[i] * H);
            float sz = fernScale[i];
            int hGt = Math.max(10, (int)(26 * sz));
            int wGt = Math.max(8,  (int)(20 * sz));
            g.setColor(MOSS_DK);
            g.drawLine(fx, fy, fx, fy - hGt);
            for (int lf = 1; lf <= 6; lf++) {
                int ly = fy - hGt * lf / 6;
                int lw = wGt * (6 - lf) / 6 + 2;
                g.setColor((lf % 2 == 0) ? FERN : MOSS_LT);
                g.drawLine(fx, ly, fx - lw, ly + 2);
                g.drawLine(fx, ly, fx + lw, ly + 2);
            }
        }
    }

    private void paintMist(Graphics2D g, int W, int H) {
        for (int i = 0; i < MIST_COUNT; i++) {
            int mx = (int)(mistX[i] * W);
            int my = (int)(mistY[i] * H);
            int r  = (int) mistR[i];
            int alpha = (int)(90 * (mistY[i] - 0.35f));
            if (alpha <= 0) continue;
            g.setColor(new Color(MIST.getRed(), MIST.getGreen(), MIST.getBlue(), Math.min(90, alpha)));
            g.fillOval(mx - r, my - r / 2, r * 2, r);
        }
    }

    private void paintPlayerWalking(Graphics2D g, int W, int H, float walkP, long now) {
        float yT      = 0.72f - walkP * 0.26f;
        float sizeT   = 1f - walkP * 0.70f;
        float fadeOut = Math.max(0f, (walkP - 0.85f) / 0.15f);
        int px = W / 2;
        int py = (int)(yT * H);
        int ps = Math.max(6, (int)(32 * sizeT));
        int baseAlpha = (int)((1f - fadeOut) * 255);
        if (baseAlpha <= 0) return;

        int frame = (int)(now / 120) % 4;

        g.setColor(new Color(0, 0, 0, (int)(60 * (1f - fadeOut))));
        g.fillOval(px - ps / 2, py + ps - 2, ps, ps / 4);

        g.setColor(new Color(60, 80, 160, baseAlpha));
        g.fillRect(px - ps / 3, py + ps / 3, ps * 2 / 3, ps / 2);

        g.setColor(new Color(220, 170, 110, baseAlpha));
        g.fillRect(px - ps / 4, py, ps / 2, ps / 3);

        g.setColor(new Color(160, 160, 170, baseAlpha));
        g.fillRect(px - ps / 4 - 1, py - ps / 6, ps / 2 + 2, ps / 5);

        int capeOff = (frame % 2 == 0) ? 1 : -1;
        g.setColor(new Color(160, 30, 30, baseAlpha));
        g.fillRect(px - ps / 3 - 2, py + ps / 3, ps / 6, ps / 2 + capeOff * 2);

        int legOff = (frame < 2) ? ps / 6 : -ps / 6;
        g.setColor(new Color(80, 60, 40, baseAlpha));
        g.fillRect(px - ps / 5, py + ps * 5 / 6, ps / 5, ps / 3 + legOff);
        g.fillRect(px,          py + ps * 5 / 6, ps / 5, ps / 3 - legOff);

        if (ps > 10) {
            g.setColor(new Color(190, 190, 200, baseAlpha));
            g.drawLine(px + ps / 3, py + ps / 3, px + ps / 2, py + ps * 3 / 4);
        }
    }

    private void paintCaveName(Graphics2D g, int W, int H, float alpha) {
        int a = (int)(alpha * 230);
        String welcome = "Behind the cascade";
        String raw = Town.displayName(townName);
        if (raw.toLowerCase().endsWith("cave") && raw.length() > 4) {
            raw = raw.substring(0, raw.length() - 4) + " Cave";
        }
        String name = raw.toUpperCase();

        g.setFont(F_SUB);
        FontMetrics fmS = g.getFontMetrics();
        int wy = (int)(H * 0.84f);
        int wx = (W - fmS.stringWidth(welcome)) / 2;
        g.setColor(new Color(0, 0, 0, a / 2));
        g.drawString(welcome, wx + 2, wy + 2);
        g.setColor(new Color(200, 230, 240, a));
        g.drawString(welcome, wx, wy);

        g.setFont(F_TITLE);
        FontMetrics fmT = g.getFontMetrics();
        int ny = wy + fmT.getHeight() + 6;
        int nx = (W - fmT.stringWidth(name)) / 2;
        g.setColor(new Color(0, 0, 0, a / 2));
        g.drawString(name, nx + 2, ny + 2);
        g.setColor(new Color(220, 240, 255, a));
        g.drawString(name, nx, ny);
    }

    private void paintIrisWipe(Graphics2D g, int W, int H, int cx, int cy, float irisP) {
        float maxR   = (float) Math.sqrt(W * W + H * H);
        float innerR = maxR * (1f - irisP * irisP);
        int steps = 40;
        for (int i = 0; i < steps; i++) {
            float fr = (float) i / steps;
            float r  = innerR + fr * (maxR - innerR);
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

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
 * 8-bit town entry animation.
 *
 * The player walks toward a medieval town gate that grows larger as they
 * approach. Torches flicker on the gateposts. A portcullis rises. Citizens
 * wave from the battlements. Birds scatter from the rooftops. Then the
 * camera "passes through" the gate arch with a perspective warp, and we
 * fade into the town interior.
 *
 * Sequence  (total ~3.2 s)
 *   0–12%  : overworld recedes — sky, road, distant walls appear
 *  10–45%  : player sprite walks forward, gate grows (perspective zoom)
 *  38–55%  : portcullis rises with chain rattle pixel-by-pixel
 *  48–70%  : birds scatter from battlements, citizens appear waving
 *  62–85%  : player walks through arch — arch edge darkens, vignette
 *  78–100% : iris-wipe to black then brief warm interior flash
 */
public class TownEntryAnimation implements AnimationGuard.Cancellable {

    private static final long ANIM_MS = AnimationSpeed.scale(3200);

    // ── Palette ───────────────────────────────────────────────────────────────
    private static final Color SKY_TOP    = new Color( 60,  90, 160);
    private static final Color SKY_BOT    = new Color(140, 180, 220);
    private static final Color CLOUD      = new Color(230, 235, 245);
    private static final Color ROAD_LT    = new Color(180, 155, 110);
    private static final Color ROAD_DK    = new Color(140, 118,  80);
    private static final Color GRASS_LT   = new Color( 80, 160,  60);
    private static final Color GRASS_DK   = new Color( 50, 110,  35);
    private static final Color STONE_LT   = new Color(170, 158, 140);
    private static final Color STONE_MD   = new Color(130, 118, 100);
    private static final Color STONE_DK   = new Color( 80,  70,  58);
    private static final Color STONE_DARK = new Color( 40,  35,  28);
    private static final Color MORTAR     = new Color( 90,  82,  70);
    private static final Color PORTCULLIS = new Color( 60,  55,  45);
    private static final Color TORCH_ORG  = new Color(255, 140,  20);
    private static final Color TORCH_YEL  = new Color(255, 220,  80);
    private static final Color BANNER_R   = new Color(180,  30,  30);
    private static final Color BANNER_G   = new Color( 30, 130,  50);
    private static final Color WOOD_DK    = new Color( 80,  55,  30);
    private static final Color ARCH_DARK  = new Color(  8,   6,   4);

    private static final Font F_TOWN  = Fonts.monoBold(16);
    private static final Font F_SUB   = Fonts.mono    (11);

    // ── State ─────────────────────────────────────────────────────────────────
    private final Retroquest game;
    private final String     townName;
    private boolean          active   = false;
    private boolean          fired    = false;
    private boolean paintFailed = false;   // doPaint() threw — stop drawing, but stay active
    /** Particle physics advances in fixed steps of this size, driven by the wall clock. */
    private static final long PHYS_STEP_MS = 16;
    private long    lastPhysMs = 0;   // wall-clock anchor for the fixed-step physics
    private long             startTime;
    private javax.swing.Timer animTimer;

    // Birds
    private static final int BIRD_COUNT = 12;
    private final float[] birdX  = new float[BIRD_COUNT];
    private final float[] birdY  = new float[BIRD_COUNT];
    private final float[] birdVX = new float[BIRD_COUNT];
    private final float[] birdVY = new float[BIRD_COUNT];
    private final int[]   birdWingPhase = new int[BIRD_COUNT];

    // Clouds
    private static final int CLOUD_COUNT = 4;
    private final int[] cloudX = new int[CLOUD_COUNT];
    private final int[] cloudY = new int[CLOUD_COUNT];
    private final int[] cloudW = new int[CLOUD_COUNT];

    // Cobblestone offsets for road
    private final int[] cobbleX = new int[32];
    private final int[] cobbleY = new int[32];
    private final int[] cobbleW = new int[32];

    public TownEntryAnimation(Retroquest game, String townName) {
        this.game     = game;
        this.townName = townName;
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
     * Initialises random elements (birds, clouds, cobblestones) and starts
     * the 3.2-second animation timer. Calls {@link Retroquest#finishTownEntry()}
     * when the sequence completes.
     */
    public void start() {
        if (active) return;   // idempotent: never stack a second timer or a second callback
        AnimationGuard.claim(AnimationGuard.TOWN_ENTRY, this);

        java.util.Random rng = new java.util.Random(townName.hashCode());
        fired     = false;
        paintFailed = false;
        startTime = System.currentTimeMillis();
        lastPhysMs = startTime;

        // Birds scatter from top of gate
        for (int i = 0; i < BIRD_COUNT; i++) {
            birdX[i]  = 0.35f + rng.nextFloat() * 0.30f;
            birdY[i]  = 0.18f + rng.nextFloat() * 0.12f;
            float angle = (float)(rng.nextDouble() * Math.PI * 2);
            float spd = 0.0012f + rng.nextFloat() * 0.0018f;
            birdVX[i] = (float)(Math.cos(angle) * spd);
            birdVY[i] = -(0.0008f + rng.nextFloat() * 0.0010f); // upward
            birdWingPhase[i] = rng.nextInt(8);
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

            // Physics on a fixed step driven by the wall clock, so the birds stay in
            // sync with the wall-clock progress `p` even when ticks arrive late.
            int steps = (int)((startTime + elapsed - lastPhysMs) / PHYS_STEP_MS);
            if (steps > 8) steps = 8;                       // don't replay a long stall
            lastPhysMs += (long) steps * PHYS_STEP_MS;
            for (int s = 0; s < steps; s++) {
                // Animate birds
                for (int i = 0; i < BIRD_COUNT; i++) {
                    birdX[i]  += birdVX[i];
                    birdY[i]  += birdVY[i];
                    birdVY[i] += 0.000015f; // slight gravity
                    birdWingPhase[i] = (birdWingPhase[i] + 1) % 8;
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

        int skyH = (int)(H * 0.45f);

        // ── SKY ───────────────────────────────────────────────────────────────
        for (int y = 0; y < skyH; y++) {
            float t = (float) y / skyH;
            int r = (int)(SKY_TOP.getRed()   + t * (SKY_BOT.getRed()   - SKY_TOP.getRed()));
            int gr= (int)(SKY_TOP.getGreen() + t * (SKY_BOT.getGreen() - SKY_TOP.getGreen()));
            int b = (int)(SKY_TOP.getBlue()  + t * (SKY_BOT.getBlue()  - SKY_TOP.getBlue()));
            g.setColor(new Color(r, gr, b));
            g.drawLine(0, y, W, y);
        }

        // ── CLOUDS ────────────────────────────────────────────────────────────
        for (int i = 0; i < CLOUD_COUNT; i++) {
            int cx = (int)(cloudX[i] + elapsed * 0.008f * (i % 2 == 0 ? 1 : -0.5f)) % (W + 150) - 75;
            paintCloud(g, cx, cloudY[i] * H / 200, cloudW[i]);
        }

        // ── GROUND ────────────────────────────────────────────────────────────
        // Grass bands
        for (int y = skyH; y < H; y++) {
            float t = (float)(y - skyH) / (H - skyH);
            Color gc = (y % 4 < 2) ? GRASS_LT : GRASS_DK;
            g.setColor(new Color(
                (int)(gc.getRed()   * (1 - t * 0.3f)),
                (int)(gc.getGreen() * (1 - t * 0.2f)),
                (int)(gc.getBlue()  * (1 - t * 0.1f))
            ));
            g.drawLine(0, y, W, y);
        }

        // ── ROAD ──────────────────────────────────────────────────────────────
        // Perspective trapezoid road from horizon to bottom-centre
        int roadTopW = (int)(W * 0.06f);
        int roadBotW = (int)(W * 0.55f);
        int roadTopX = (W - roadTopW) / 2;
        int roadBotX = (W - roadBotW) / 2;
        int[] roadXs = {roadTopX, roadTopX + roadTopW, roadBotX + roadBotW, roadBotX};
        int[] roadYs = {skyH, skyH, H, H};
        g.setColor(ROAD_LT);
        g.fillPolygon(roadXs, roadYs, 4);

        // Cobblestone pattern on road
        g.setFont(Fonts.mono(8));
        for (int i = 0; i < cobbleX.length; i++) {
            float rowT = cobbleY[i] / 100f;
            float scale = 0.15f + rowT * 0.85f;
            int cx2    = (int)(roadBotX + cobbleX[i] / 100f * roadBotW - roadBotW * 0.1f);
            int cw     = (int)(cobbleW[i] * scale);
            int ch     = (int)(8 * scale);
            // Scroll toward viewer
            float scroll = (p * 2f % 1f) * (H - skyH);
            int cy3 = (int)(skyH + (rowT * (H - skyH) + scroll) % (H - skyH));
            g.setColor((i % 3 == 0) ? ROAD_DK : ROAD_LT);
            g.fillRoundRect(cx2, cy3, Math.max(4, cw), Math.max(3, ch), 2, 2);
            g.setColor(new Color(100, 85, 55));
            g.drawRoundRect(cx2, cy3, Math.max(4, cw), Math.max(3, ch), 2, 2);
        }

        // ── DISTANT CITY SILHOUETTE ───────────────────────────────────────────
        float gateScale = 0.18f + p * 0.82f; // gate grows from tiny to full
        int   gateCX    = W / 2;
        int   gateCY    = skyH;

        // Background wall battlements (far towers)
        paintDistantWalls(g, gateCX, gateCY, W, gateScale);

        // ── MAIN GATE ─────────────────────────────────────────────────────────
        paintGate(g, gateCX, gateCY, W, H, gateScale, p, now);

        // ── PLAYER SPRITE WALKING ─────────────────────────────────────────────
        float walkP = Math.min(1f, p / 0.75f);
        paintPlayerWalking(g, W, H, skyH, walkP, now);

        // ── BIRDS ─────────────────────────────────────────────────────────────
        float birdP = Math.min(1f, Math.max(0f, (p - 0.48f) / 0.22f));
        if (birdP > 0) {
            for (int i = 0; i < BIRD_COUNT; i++) {
                int bx = (int)(birdX[i] * W);
                int by = (int)(birdY[i] * H);
                paintBird(g, bx, by, birdWingPhase[i], birdP);
            }
        }

        // ── ARCH VIGNETTE (pass-through) ──────────────────────────────────────
        float vigP = Math.min(1f, Math.max(0f, (p - 0.62f) / 0.23f));
        if (vigP > 0) paintArchVignette(g, W, H, gateCX, gateCY, gateScale, vigP);

        // ── TOWN NAME BANNER ──────────────────────────────────────────────────
        float nameP = Math.min(1f, Math.max(0f, (p - 0.30f) / 0.25f))
                    * (1f - Math.min(1f, Math.max(0f, (p - 0.72f) / 0.12f)));
        if (nameP > 0) paintTownName(g, W, H, nameP);

        // ── IRIS WIPE TO BLACK ────────────────────────────────────────────────
        float irisP = Math.min(1f, Math.max(0f, (p - 0.82f) / 0.18f));
        if (irisP > 0) paintIrisWipe(g, W, H, gateCX, skyH, irisP);
    }

    // ── GATE ──────────────────────────────────────────────────────────────────

    private void paintGate(Graphics2D g, int cx, int baseY, int W, int H,
                            float scale, float p, long now) {
        int gW  = (int)(240 * scale);
        int gX  = cx - gW / 2;
        // Gate towers (left and right)
        int tW  = (int)(60 * scale);
        int tH  = (int)(220 * scale);
        int tY  = baseY - tH;

        paintTower(g, gX - tW + (int)(8 * scale), tY, tW, tH, scale, false);
        paintTower(g, gX + gW - (int)(8 * scale), tY, tW, tH, scale, true);

        // Gate wall between towers
        int wallY = baseY - (int)(160 * scale);
        int wallH = (int)(160 * scale);
        paintStoneWall(g, gX, wallY, gW, wallH, scale);

        // Arch opening
        int archW = (int)(90 * scale);
        int archH = (int)(110 * scale);
        int archX = cx - archW / 2;
        int archY = baseY - archH;

        // Arch interior (dark)
        g.setColor(ARCH_DARK);
        g.fillRect(archX, archY + archH / 3, archW, archH - archH / 3);
        g.setColor(ARCH_DARK);
        g.fillArc(archX, archY, archW, (int)(archH * 0.67f), 0, 180);

        // Portcullis
        float portP = Math.min(1f, Math.max(0f, (p - 0.38f) / 0.17f));
        int portH   = (int)(archH * 0.85f);
        int portDrop = (int)(portH * (1f - portP)); // starts down, rises up
        paintPortcullis(g, archX, archY + portDrop, archW, portH - portDrop, scale);

        // Arch stonework surround
        paintArchSurround(g, archX, archY, archW, archH, scale);

        // Torches on gate wall
        float torchFlicker = 0.7f + 0.3f * (float)Math.sin(now / 80.0 + 1.2);
        float torchFlicker2= 0.7f + 0.3f * (float)Math.sin(now / 95.0);
        paintTorch(g, archX - (int)(18 * scale), archY + (int)(20 * scale), scale, torchFlicker, now);
        paintTorch(g, archX + archW + (int)(4 * scale), archY + (int)(20 * scale), scale, torchFlicker2, now);

        // Banners
        paintBanner(g, gX + (int)(10 * scale), wallY + (int)(10 * scale), scale, BANNER_R, now);
        paintBanner(g, gX + gW - (int)(22 * scale), wallY + (int)(10 * scale), scale, BANNER_G, now);

        // Battlements atop wall
        paintBattlements(g, gX, wallY, gW, scale);

        // Citizens waving from battlements
        float citizenP = Math.min(1f, Math.max(0f, (p - 0.50f) / 0.18f));
        if (citizenP > 0) {
            paintCitizen(g, gX + (int)(20 * scale), wallY - (int)(12 * scale), scale, citizenP, now, 0);
            paintCitizen(g, gX + gW - (int)(30 * scale), wallY - (int)(12 * scale), scale, citizenP, now, 1);
        }
    }

    // ── TOWER ─────────────────────────────────────────────────────────────────

    private void paintTower(Graphics2D g, int x, int y, int w, int h, float scale, boolean right) {
        paintStoneWall(g, x, y, w, h, scale);
        // Conical/merlon top
        int mW = Math.max(4, (int)(14 * scale));
        int mH = Math.max(3, (int)(18 * scale));
        for (int i = 0; i <= w / mW; i++) {
            int mx = x + i * mW;
            if (mx + mW > x + w) break;
            g.setColor(STONE_LT);
            g.fillRect(mx, y - mH, mW - 2, mH);
            g.setColor(STONE_DK);
            g.drawRect(mx, y - mH, mW - 2, mH);
        }
        // Arrow slit windows
        for (int row = 1; row <= 3; row++) {
            int wx2 = x + w / 2 - Math.max(2, (int)(3 * scale));
            int wy  = y + (int)(row * h / 4.2f);
            int ww  = Math.max(4, (int)(6 * scale));
            int wh  = Math.max(8, (int)(14 * scale));
            g.setColor(ARCH_DARK);
            g.fillRect(wx2, wy, ww, wh);
            g.setColor(STONE_DK);
            g.drawRect(wx2, wy, ww, wh);
        }
        // Torch on tower face
        if (scale > 0.4f) {
            long now = System.currentTimeMillis();
            float tf = 0.7f + 0.3f * (float)Math.sin(now / 88.0 + (right ? 2.1 : 0.5));
            paintTorch(g, x + w / 2 - (int)(3 * scale), y + (int)(h * 0.15f), scale * 0.8f, tf, now);
        }
    }

    // ── STONE WALL ────────────────────────────────────────────────────────────

    private void paintStoneWall(Graphics2D g, int x, int y, int w, int h, float scale) {
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
        // Mortar lines — horizontal
        int blockH = Math.max(6, (int)(14 * scale));
        int blockW = Math.max(10, (int)(22 * scale));
        for (int row = 0; row * blockH < h; row++) {
            int ry = y + row * blockH;
            g.setColor(MORTAR);
            g.drawLine(x, ry, x + w, ry);
            // Vertical mortar (offset every other row)
            int offset = (row % 2 == 0) ? 0 : blockW / 2;
            for (int col = offset; col < w; col += blockW) {
                g.setColor(MORTAR);
                g.drawLine(x + col, ry, x + col, ry + blockH);
            }
        }
        // Edge highlight
        g.setColor(STONE_LT);
        g.drawLine(x, y, x, y + h);
        // Bottom shadow
        g.setColor(STONE_DARK);
        g.fillRect(x, y + h - Math.max(2, (int)(3 * scale)), w, Math.max(2, (int)(3 * scale)));
    }

    // ── ARCH SURROUND ─────────────────────────────────────────────────────────

    private void paintArchSurround(Graphics2D g, int ax, int ay, int aw, int ah, float scale) {
        int thick = Math.max(4, (int)(10 * scale));
        // Keystone blocks around arch — alternate light/dark for 8-bit look
        g.setColor(STONE_LT);
        g.setStroke(new BasicStroke(thick));
        g.drawArc(ax, ay, aw, (int)(ah * 0.67f), 0, 180);
        g.setColor(STONE_DK);
        g.setStroke(new BasicStroke(Math.max(1, thick - 2)));
        g.drawArc(ax + 2, ay + 2, aw - 4, (int)(ah * 0.67f) - 4, 0, 180);
        g.setStroke(new BasicStroke(1f));
        // Jambs
        g.setColor(STONE_LT);
        g.fillRect(ax, ay + (int)(ah * 0.34f), thick, ah - (int)(ah * 0.34f));
        g.fillRect(ax + aw - thick, ay + (int)(ah * 0.34f), thick, ah - (int)(ah * 0.34f));
    }

    // ── PORTCULLIS ────────────────────────────────────────────────────────────

    private void paintPortcullis(Graphics2D g, int x, int y, int w, int h, float scale) {
        if (h <= 0) return;
        int barW = Math.max(2, (int)(4 * scale));
        int gap  = Math.max(4, (int)(10 * scale));
        g.setColor(PORTCULLIS);
        // Vertical bars
        for (int bx = x + gap; bx < x + w - gap; bx += gap + barW) {
            g.fillRect(bx, y, barW, h);
            g.setColor(new Color(90, 82, 68));
            g.drawLine(bx + 1, y, bx + 1, y + h);
            g.setColor(PORTCULLIS);
        }
        // Horizontal cross-bars
        for (int by = y + gap; by < y + h; by += gap * 2) {
            g.setColor(PORTCULLIS);
            g.fillRect(x + gap, by, w - gap * 2, barW);
        }
        // Chain links at top corners
        for (int cy = y - Math.max(4, (int)(8 * scale)); cy > y - (int)(30 * scale); cy -= (int)(6 * scale)) {
            g.setColor(new Color(100, 92, 78));
            g.fillOval(x + (int)(8 * scale), cy, (int)(6 * scale), (int)(5 * scale));
            g.fillOval(x + w - (int)(14 * scale), cy, (int)(6 * scale), (int)(5 * scale));
        }
    }

    // ── BATTLEMENTS ───────────────────────────────────────────────────────────

    private void paintBattlements(Graphics2D g, int x, int y, int w, float scale) {
        int mW = Math.max(6, (int)(16 * scale));
        int mH = Math.max(5, (int)(20 * scale));
        int gap = Math.max(4, (int)(10 * scale));
        for (int mx = x; mx + mW <= x + w; mx += mW + gap) {
            g.setColor(STONE_MD);
            g.fillRect(mx, y - mH, mW, mH);
            g.setColor(STONE_LT);
            g.drawLine(mx, y - mH, mx, y);
            g.setColor(STONE_DK);
            g.drawRect(mx, y - mH, mW, mH);
        }
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
        // Flame glow (radial)
        int flameR = (int)(14 * scale * flicker);
        for (int r = flameR; r >= 1; r--) {
            float fr = (float) r / flameR;
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
        // Flicker spark
        if (flicker > 0.85f) {
            g.setColor(new Color(255, 255, 200, 180));
            g.fillRect(fx + (int)((Math.random() - 0.5) * 4), fy - 2, 1, 2);
        }
    }

    // ── BANNER ────────────────────────────────────────────────────────────────

    private void paintBanner(Graphics2D g, int x, int y, float scale, Color col, long now) {
        int bW = Math.max(8, (int)(18 * scale));
        int bH = Math.max(12, (int)(30 * scale));
        // Wave effect
        float wave = (float) Math.sin(now / 300.0) * 2 * scale;
        int[] bannerX = {x, x + bW + (int) wave, x + bW, x};
        int[] bannerY = {y, y + bH / 3, y + bH, y + bH};
        g.setColor(col);
        g.fillPolygon(bannerX, bannerY, 4);
        // Cross emblem
        g.setColor(new Color(255, 255, 255, 160));
        g.drawLine(x + bW / 2, y + 3, x + bW / 2, y + bH - 4);
        g.drawLine(x + 3, y + bH / 3, x + bW - 3, y + bH / 3);
        g.setColor(col.darker());
        g.drawPolygon(bannerX, bannerY, 4);
        // Pole
        g.setColor(WOOD_DK);
        g.fillRect(x - 2, y - (int)(8 * scale), Math.max(2, (int)(3 * scale)), bH + (int)(10 * scale));
    }

    // ── CITIZEN ───────────────────────────────────────────────────────────────

    private void paintCitizen(Graphics2D g, int x, int y, float scale, float alpha, long now, int which) {
        int a = (int)(alpha * 220);
        if (a <= 0) return;
        int s = Math.max(3, (int)(8 * scale));
        // Body
        Color tunic = (which == 0) ? new Color(160, 80, 30, a) : new Color(60, 100, 160, a);
        g.setColor(tunic);
        g.fillRect(x, y + s, s * 2, s * 2);
        // Head
        g.setColor(new Color(220, 170, 120, a));
        g.fillRect(x + s / 2, y, s, s);
        // Waving arm — oscillate
        float wave = (float) Math.sin(now / 200.0 + which * 1.5);
        int armX = x + s * 2;
        int armY = (int)(y + s + s * (0.3f + 0.4f * wave));
        g.setColor(tunic);
        g.fillRect(armX, armY, s, s / 2);
        // Hand
        g.setColor(new Color(220, 170, 120, a));
        g.fillRect(armX + s, armY, s / 2, s / 2);
    }

    // ── PLAYER WALKING ────────────────────────────────────────────────────────

    private void paintPlayerWalking(Graphics2D g, int W, int H, int skyH, float walkP, long now) {
        // Walks from lower screen toward gate — stops at arch threshold then fades into darkness
        float yT    = 0.70f - walkP * 0.28f;         // moves from 0.70 up to 0.42 (gate threshold)
        float sizeT = 1f - walkP * 0.65f;             // shrinks as walks away (stops at ~35% size)
        // Fade out once past the gate threshold (walkP > 0.85)
        float fadeOut = Math.max(0f, (walkP - 0.85f) / 0.15f);
        int   px    = W / 2;
        int   py    = (int)(yT * H);
        int   ps    = Math.max(6, (int)(32 * sizeT));

        // Walk cycle
        int frame = (int)(now / 120) % 4;

        int baseAlpha = (int)((1f - fadeOut) * 255);
        if (baseAlpha <= 0) return;
        // Shadow
        g.setColor(new Color(0, 0, 0, (int)(60 * (1f - fadeOut))));
        g.fillOval(px - ps / 2, py + ps - 2, ps, ps / 4);

        // Body
        g.setColor(new Color(60, 80, 160, baseAlpha));   // blue tunic
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
        g.fillRect(px - ps / 5,         py + ps * 5 / 6,      ps / 5, ps / 3 + legOff);
        g.fillRect(px,                   py + ps * 5 / 6,      ps / 5, ps / 3 - legOff);

        // Sword
        if (ps > 10) {
            g.setColor(new Color(190, 190, 200, baseAlpha));
            g.drawLine(px + ps / 3, py + ps / 3, px + ps / 2, py + ps * 3 / 4);
            g.setColor(new Color(140, 90, 30, baseAlpha));
            g.fillRect(px + ps / 3 - 1, py + ps * 2 / 5, 3, ps / 8);
        }
    }

    // ── DISTANT WALLS ─────────────────────────────────────────────────────────

    private void paintDistantWalls(Graphics2D g, int cx, int baseY, int W, float scale) {
        // Rooftops and towers visible above and beside the main gate
        int wallY = baseY - (int)(80 * scale);
        int wallH = (int)(50 * scale);
        // Left wall segment
        g.setColor(new Color(STONE_MD.getRed() - 20, STONE_MD.getGreen() - 20, STONE_MD.getBlue() - 20));
        g.fillRect(cx - (int)(200 * scale), wallY + (int)(10 * scale), (int)(130 * scale), wallH);
        paintBattlements(g, cx - (int)(200 * scale), wallY + (int)(10 * scale), (int)(130 * scale), scale * 0.7f);
        // Right wall segment
        g.fillRect(cx + (int)(70 * scale), wallY + (int)(10 * scale), (int)(130 * scale), wallH);
        paintBattlements(g, cx + (int)(70 * scale), wallY + (int)(10 * scale), (int)(130 * scale), scale * 0.7f);
        // Distant spires/rooftops peeking above wall
        paintSpire(g, cx - (int)(130 * scale), wallY, scale * 0.5f);
        paintSpire(g, cx + (int)(110 * scale), wallY, scale * 0.45f);
        paintSpire(g, cx - (int)(60 * scale),  wallY - (int)(10 * scale), scale * 0.35f);
    }

    private void paintSpire(Graphics2D g, int x, int y, float scale) {
        int w = Math.max(4, (int)(18 * scale));
        int h = Math.max(8, (int)(40 * scale));
        g.setColor(new Color(100, 88, 75));
        g.fillRect(x - w / 2, y - h, w, h);
        // Pointed top
        int[] xs = {x - w / 2, x + w / 2, x};
        int[] ys = {y - h, y - h, y - h - (int)(20 * scale)};
        g.setColor(new Color(80, 68, 55));
        g.fillPolygon(xs, ys, 3);
    }

    // ── CLOUD ─────────────────────────────────────────────────────────────────

    private void paintCloud(Graphics2D g, int x, int y, int w) {
        int h = w / 3;
        g.setColor(CLOUD);
        g.fillOval(x, y + h / 3, w, h * 2 / 3);
        g.fillOval(x + w / 5, y, w * 3 / 5, h);
        g.fillOval(x + w / 2, y + h / 4, w / 2, h * 2 / 3);
        // Shadow underside
        g.setColor(new Color(190, 198, 215));
        g.drawLine(x + 4, y + h, x + w - 4, y + h);
    }

    // ── BIRD ──────────────────────────────────────────────────────────────────

    private void paintBird(Graphics2D g, int x, int y, int wingPhase, float alpha) {
        int a = (int)(alpha * 200);
        g.setColor(new Color(30, 25, 20, a));
        // Simple 3-pixel M shape with wing phase
        int wingY = (wingPhase < 4) ? -1 : 1;
        g.drawLine(x - 4, y + wingY, x, y);
        g.drawLine(x, y, x + 4, y + wingY);
    }

    // ── ARCH VIGNETTE ─────────────────────────────────────────────────────────

    private void paintArchVignette(Graphics2D g, int W, int H, int cx, int baseY, float scale, float vigP) {
        // Darken the outer areas as if passing through arch
        int archW = (int)(90 * scale);
        int archH = (int)(110 * scale);
        int archX = cx - archW / 2;
        int archY = baseY - archH;

        // Dark side panels
        int darkA = (int)(vigP * vigP * 240);
        g.setColor(new Color(0, 0, 0, Math.min(255, darkA)));
        g.fillRect(0, 0, archX, H);
        g.fillRect(archX + archW, 0, W - archX - archW, H);
        g.fillRect(0, 0, W, archY + archH / 3);

        // Warm glow at centre of arch (lantern light inside)
        float glowR = archW * 0.4f * vigP;
        int gcx = cx, gcy = archY + (int)(archH * 0.7f);
        for (int r = (int)glowR; r >= 1; r--) {
            float fr = r / glowR;
            int ga = (int)(vigP * 60 * (1 - fr));
            g.setColor(new Color(255, 180, 80, Math.max(0, ga)));
            g.fillOval(gcx - r, gcy - r, r * 2, r * 2);
        }
    }

    // ── TOWN NAME ─────────────────────────────────────────────────────────────

    private void paintTownName(Graphics2D g, int W, int H, float alpha) {
        int a = (int)(alpha * 230);
        g.setFont(F_TOWN);
        FontMetrics fm = g.getFontMetrics();
        String welcome = "Welcome to";
        String name    = townName.toUpperCase();
        int wx  = (W - fm.stringWidth(welcome)) / 2;
        int nx  = (W - fm.stringWidth(name))    / 2;
        int wy  = (int)(H * 0.82f);
        int ny  = wy + fm.getHeight() + 4;

        // Drop shadow
        g.setColor(new Color(0, 0, 0, a / 2));
        g.drawString(welcome, wx + 2, wy + 2);
        g.setFont(F_TOWN);
        g.drawString(name,    nx + 2, ny + 2);

        // Text
        g.setFont(F_SUB);
        g.setColor(new Color(220, 200, 150, a));
        g.drawString(welcome, wx, wy);

        g.setFont(F_TOWN);
        g.setColor(new Color(255, 230, 100, a));
        g.drawString(name, nx, ny);

        // Decorative line under name
        int lineY = ny + 6;
        g.setColor(new Color(180, 140, 60, a / 2));
        g.drawLine(nx + 10, lineY, nx + fm.stringWidth(name) - 10, lineY);
    }

    // ── IRIS WIPE ─────────────────────────────────────────────────────────────

    private void paintIrisWipe(Graphics2D g, int W, int H, int cx, int cy, float irisP) {
        // Circular iris closing to black — 8-bit stepped rings
        float maxR  = (float) Math.sqrt(W * W + H * H);
        float innerR = maxR * (1f - irisP * irisP);

        // Fill everything outside the circle black
        // Use a clip trick: draw black rect then cut out circle with XOR not available —
        // instead draw concentric rings from outside inward
        int steps = 40;
        for (int i = 0; i < steps; i++) {
            float fr   = (float) i / steps;
            float r    = innerR + fr * (maxR - innerR);
            if (r > maxR) break;
            int alpha = (int)(((float)(i) / steps) * 255 * irisP);
            g.setColor(new Color(0, 0, 0, Math.min(255, alpha)));
            int ri = (int) r;
            g.fillOval(cx - ri, cy - ri, ri * 2, ri * 2);
        }
        // Final solid black once fully closed
        if (irisP > 0.85f) {
            float f = (irisP - 0.85f) / 0.15f;
            g.setColor(new Color(0, 0, 0, (int)(f * 255)));
            g.fillRect(0, 0, W, H);
        }
    }
}

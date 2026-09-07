package io.cannonforge.retroquest.overlay;

import static io.cannonforge.retroquest.overlay.SkyGamesOverlay.SKY_BLUE;
import static io.cannonforge.retroquest.overlay.SkyGamesOverlay.STORM_GREY;
import static io.cannonforge.retroquest.overlay.SkyGamesOverlay.WIND_GOLD;
import static io.cannonforge.retroquest.overlay.SkyGamesOverlay.F_TITLE;
import static io.cannonforge.retroquest.overlay.SkyGamesOverlay.F_MENU;
import static io.cannonforge.retroquest.overlay.SkyGamesOverlay.F_BIG;
import static io.cannonforge.retroquest.overlay.SkyGamesOverlay.drawCentered;
import static io.cannonforge.retroquest.overlay.OverlayTheme.*;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import io.cannonforge.retroquest.core.MessageLog;
import io.cannonforge.retroquest.core.Retroquest;
import io.cannonforge.retroquest.core.SoundManager;

/**
 * Gust Glider: a side-scrolling dodge-and-collect mini-game. The player
 * auto-scrolls right through a stormy sky, adjusting altitude with Up/Down.
 * Dodge storm clouds and lightning, fly through gold rings for bonus payout.
 * Cash out anytime or crash to lose the bet. DEX improves glider handling.
 */
class GustGliderGame implements MiniGame {

    private enum State { BET, PLAY, LANDING, RESULT }

    // Sky gradient
    private static final Color SKY_TOP      = new Color( 20,  30,  60);
    private static final Color SKY_BOT      = new Color( 50,  70, 110);
    // Entities
    private static final Color CLOUD_DARK   = new Color( 60,  70,  90);
    private static final Color CLOUD_LIGHT  = new Color( 80,  95, 115);
    private static final Color BOLT_WARN    = new Color(255, 255, 100,  60);
    private static final Color BOLT_STRIKE  = new Color(255, 255, 220);
    private static final Color RING_COL     = new Color(255, 210, 100);
    private static final Color RING_FILL    = new Color(255, 210, 100,  80);
    private static final Color GUST_COL     = new Color(100, 220, 180, 120);
    // Player
    private static final Color GLIDER_COL   = WIND_GOLD;
    private static final Color GLIDER_HIT   = new Color(255, 100,  80);
    // Background
    private static final Color WIND_LINE_COL = new Color( 80, 130, 200,  30);
    private static final Color RUNWAY_COL    = new Color( 60,  80,  40);

    // Timing
    private static final long BOLT_WARN_MS   = 600;
    private static final long BOLT_STRIKE_MS = 200;
    private static final long LANDING_MS     = 600;
    private static final long GUST_BOOST_MS  = 500;

    // Payout tiers are measured in MILLISECONDS FLOWN, not frames, so a fast or
    // slow machine (or a fast key-repeat) cannot change what a flight is worth.
    private static final int METRES_PER_SEC = 6;   // display only: metres = ms * 6 / 1000
    private static final int DIST_TIER1 = 10000;   // 10.0s =  60m
    private static final int DIST_TIER2 = 18000;   // 18.0s = 108m
    private static final int DIST_TIER3 = 27000;   // 27.0s = 162m
    private static final int DIST_TIER4 = 38000;   // 38.0s = 228m

    // -- Payout ladder --------------------------------------------------------
    // Distance and ring multiples ADD, and a crash pays nothing, so the glider is
    // a press-your-luck: best stopping point is around TIER3 for roughly 0.96x.
    private static final float DIST_PAY_T4 = 2.0f, DIST_PAY_T3 = 1.4f, DIST_PAY_T2 = 0.8f, DIST_PAY_T1 = 0.4f;
    private static final float RING_PAY_20 = 0.6f, RING_PAY_15 = 0.4f, RING_PAY_10 = 0.25f, RING_PAY_5 = 0.1f;

    private final Retroquest game;
    private final SkyGamesOverlay overlay;
    private final Random rng;

    private State state = State.BET;

    // Player
    private float playerY;           // 0.0 = top, 1.0 = bottom (normalized within field)
    private boolean movingUp, movingDown; // per-frame movement flags (reset each update)

    // Progress
    private int distance;            // milliseconds flown
    private long flightStart;        // wall-clock anchor for `distance`
    private long lastSpawnMs;        // wall-clock anchor for entity spawning
    private int ringsCollected;
    private boolean reachedMilestone; // played levelup at 200m

    // Entities
    private final List<Entity> entities = new ArrayList<>();
    private long crashTime;          // for hit flash
    private long landStartTime;

    // Gust boost
    private long gustBoostEnd;

    // Wind line parallax
    private final float[] windLineY;
    private final float[] windLineX;

    private String resultMessage = "";

    // ── Entity types ─────────────────────────────────────────────────────────

    private enum EntityType {
        STORM_CLOUD, BOLT_WARNING, BOLT_STRIKE, GOLD_RING, WIND_GUST
    }

    private static class Entity {
        EntityType type;
        float x, y;       // normalized 0.0–1.0
        float height;     // for clouds and bolts (vertical extent)
        long spawnTime;
        boolean collected;

        Entity(EntityType type, float x, float y, long spawnTime) {
            this.type = type;
            this.x = x;
            this.y = y;
            this.spawnTime = spawnTime;
        }
    }

    // ── Constructor ──────────────────────────────────────────────────────────

    GustGliderGame(Retroquest game, SkyGamesOverlay overlay, Random rng) {
        this.game = game;
        this.overlay = overlay;
        this.rng = rng;
        windLineY = new float[10];
        windLineX = new float[10];
        for (int i = 0; i < windLineY.length; i++) {
            windLineY[i] = rng.nextFloat();
            windLineX[i] = rng.nextFloat();
        }
    }

    // ── MiniGame interface ───────────────────────────────────────────────────

    @Override
    public void reset(int betAmount) {
        state = State.BET;
        playerY = 0.5f;
        movingUp = false;
        movingDown = false;
        distance = 0;
        ringsCollected = 0;
        reachedMilestone = false;
        entities.clear();
        flightStart = System.currentTimeMillis();
        lastSpawnMs = flightStart;
        crashTime = 0;
        gustBoostEnd = 0;
        resultMessage = "";
    }

    @Override
    public boolean isShowingResult() { return state == State.RESULT; }

    // ── Input ────────────────────────────────────────────────────────────────

    @Override
    public void handleKey(KeyEvent e) {
        switch (state) {
            case BET     -> handleBetKey(e);
            case PLAY    -> handlePlayKey(e);
            case LANDING -> {} // non-interactive
            case RESULT  -> handleResultKey(e);
        }
    }

    private void handleBetKey(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_LEFT  -> overlay.adjustBet(e.isShiftDown() ? -10 : -1);
            case KeyEvent.VK_RIGHT -> overlay.adjustBet(e.isShiftDown() ?  10 :  1);
            case KeyEvent.VK_ENTER -> {
                if (game.getPlayer().getGold() < overlay.betAmount) {
                    game.log("Not enough gold!", MessageLog.Type.DANGER);
                    return;
                }
                game.getPlayer().addGold(-overlay.betAmount);
                overlay.lastBet = overlay.betAmount;
                SoundManager.getInstance().play("coin");
                // Anchor the flight clock at launch, not at reset, or time spent
                // on the bet screen would count as distance flown.
                flightStart = System.currentTimeMillis();
                lastSpawnMs = flightStart;
                distance = 0;
                state = State.PLAY;
            }
            case KeyEvent.VK_ESCAPE -> { overlay.backToMenu(); SoundManager.getInstance().play("menublip"); }
        }
    }

    private void handlePlayKey(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_UP    -> movingUp = true;
            case KeyEvent.VK_DOWN  -> movingDown = true;
            case KeyEvent.VK_ENTER, KeyEvent.VK_ESCAPE -> beginCashOut();
        }
    }

    private void handleResultKey(KeyEvent e) {
        overlay.backToMenu();
        if (game.getStatsPanel() != null) game.getStatsPanel().refresh();
    }

    // ── Cash out / crash ─────────────────────────────────────────────────────

    private void beginCashOut() {
        landStartTime = System.currentTimeMillis();
        state = State.LANDING;
    }

    private void completeCashOut() {
        float mult = distanceMultiplier() + ringBonus();
        int payout = (int)(overlay.betAmount * mult);

        if (payout > 0) {
            game.getPlayer().addGold(payout);
            int metres = metres();
            resultMessage = distance >= DIST_TIER4
                ? "MASTER GLIDER! Won " + payout + "g!"
                : "Safe landing! Won " + payout + "g!";
            SoundManager.getInstance().play(mult >= 3.0f ? "victory" : "coin");
            game.log(resultMessage, MessageLog.Type.LOOT);
        } else {
            resultMessage = "Too short a flight. Lost " + overlay.betAmount + "g.";
            SoundManager.getInstance().play("hurt");
            game.log(resultMessage, MessageLog.Type.DANGER);
        }
        game.getPlayer().recordGamblingResult(payout - overlay.betAmount);
        state = State.RESULT;
    }

    private void crash() {
        resultMessage = "You crashed! Lost " + overlay.betAmount + "g.";
        SoundManager.getInstance().play("hurt");
        game.log(resultMessage, MessageLog.Type.DANGER);
        game.getPlayer().recordGamblingResult(-overlay.betAmount);
        crashTime = System.currentTimeMillis();
        state = State.RESULT;
    }

    private float distanceMultiplier() {
        if (distance >= DIST_TIER4) return DIST_PAY_T4;
        if (distance >= DIST_TIER3) return DIST_PAY_T3;
        if (distance >= DIST_TIER2) return DIST_PAY_T2;
        if (distance >= DIST_TIER1) return DIST_PAY_T1;
        return 0f;
    }

    private float ringBonus() {
        if (ringsCollected >= 20) return RING_PAY_20;
        if (ringsCollected >= 15) return RING_PAY_15;
        if (ringsCollected >= 10) return RING_PAY_10;
        if (ringsCollected >=  5) return RING_PAY_5;
        return 0f;
    }

    /** Metres flown, for display only. */
    private int metres() { return distance * METRES_PER_SEC / 1000; }

    // ── Update (called every frame ~16ms) ────────────────────────────────────

    @Override
    public void update() {
        if (state == State.LANDING) {
            long elapsed = System.currentTimeMillis() - landStartTime;
            // Glider descends during landing
            playerY = Math.min(0.95f, playerY + 0.008f);
            if (elapsed >= LANDING_MS) {
                completeCashOut();
            }
            return;
        }
        if (state != State.PLAY) return;

        distance = (int)(System.currentTimeMillis() - flightStart);

        // DEX bonuses
        int dex = game.getPlayer().getDex();
        float moveSpeed = 0.04f + Math.max(0, (dex - 10)) * 0.003f;
        float gravity   = 0.003f - Math.min(0.0015f, Math.max(0, (dex - 10)) * 0.0003f);

        // Player movement (flags set by keyPressed, consumed each frame)
        if (movingUp)   playerY -= moveSpeed;
        if (movingDown) playerY += moveSpeed;
        movingUp = false;
        movingDown = false;
        playerY += gravity; // gentle downward drift
        playerY = Math.max(0.03f, Math.min(0.97f, playerY));

        // Scroll speed ramp based on time flown (tops out after ~42s)
        float speedMult = 1.0f + Math.min(0.8f, distance / 42000.0f);
        long now = System.currentTimeMillis();
        boolean boosted = now < gustBoostEnd;
        float scrollSpeed = 0.006f * speedMult * (boosted ? 1.5f : 1.0f);

        // Milestone sound
        if (!reachedMilestone && distance >= DIST_TIER4) {
            reachedMilestone = true;
            SoundManager.getInstance().play("levelup");
        }

        // ── Spawn entities ──────────────────────────────────────────────────
        int spawnInterval = Math.max(333, 833 - distance / 100);
        if (now - lastSpawnMs >= spawnInterval) {
            lastSpawnMs = now;
            spawnEntity(now);
        }

        // ── Update wind lines ───────────────────────────────────────────────
        for (int i = 0; i < windLineX.length; i++) {
            windLineX[i] -= 0.012f * speedMult;
            if (windLineX[i] < -0.1f) {
                windLineX[i] = 1.1f;
                windLineY[i] = rng.nextFloat();
            }
        }

        // ── Update entities ─────────────────────────────────────────────────
        Iterator<Entity> it = entities.iterator();
        while (it.hasNext()) {
            Entity e = it.next();

            switch (e.type) {
                case STORM_CLOUD -> {
                    e.x -= scrollSpeed;
                    if (e.x < -0.15f) { it.remove(); continue; }
                    // Collision: glider at ~0.13 horizontal, check overlap
                    if (!e.collected && e.x < 0.18f && e.x > 0.05f) {
                        if (Math.abs(playerY - e.y) < e.height * 0.45f + 0.03f) {
                            crash();
                            return;
                        }
                    }
                }
                case BOLT_WARNING -> {
                    if (now - e.spawnTime >= BOLT_WARN_MS) {
                        e.type = EntityType.BOLT_STRIKE;
                        e.spawnTime = now;
                        // Check if player is in the strike band
                        if (playerY >= e.y && playerY <= e.y + e.height) {
                            crash();
                            return;
                        }
                    }
                }
                case BOLT_STRIKE -> {
                    if (now - e.spawnTime >= BOLT_STRIKE_MS) {
                        it.remove();
                        continue;
                    }
                }
                case GOLD_RING -> {
                    e.x -= scrollSpeed;
                    if (e.x < -0.1f) { it.remove(); continue; }
                    if (!e.collected && e.x < 0.18f && e.x > 0.08f) {
                        if (Math.abs(playerY - e.y) < 0.05f) {
                            e.collected = true;
                            ringsCollected++;
                            SoundManager.getInstance().play("coin");
                            it.remove();
                            continue;
                        }
                    }
                }
                case WIND_GUST -> {
                    e.x -= scrollSpeed * 0.7f; // gusts move slower
                    if (e.x < -0.1f) { it.remove(); continue; }
                    if (!e.collected && e.x < 0.18f && e.x > 0.08f) {
                        if (Math.abs(playerY - e.y) < 0.06f) {
                            e.collected = true;
                            gustBoostEnd = now + GUST_BOOST_MS;
                            // Small vertical push (random up or down)
                            playerY += rng.nextBoolean() ? -0.05f : 0.05f;
                            playerY = Math.max(0.03f, Math.min(0.97f, playerY));
                            SoundManager.getInstance().play("menublip");
                            it.remove();
                            continue;
                        }
                    }
                }
            }
        }
    }

    private void spawnEntity(long now) {
        int metres = metres();
        int roll = rng.nextInt(10);

        if (roll < 3) {
            // 30% storm cloud
            Entity e = new Entity(EntityType.STORM_CLOUD, 1.1f, 0.1f + rng.nextFloat() * 0.8f, now);
            e.height = 0.08f + rng.nextFloat() * 0.04f; // variable size
            entities.add(e);
        } else if (roll < 6) {
            // 30% gold ring
            entities.add(new Entity(EntityType.GOLD_RING, 1.1f, 0.08f + rng.nextFloat() * 0.84f, now));
        } else if (roll < 8 && metres >= 50) {
            // 20% lightning (after 50m)
            Entity e = new Entity(EntityType.BOLT_WARNING, 0.4f + rng.nextFloat() * 0.5f, rng.nextFloat() * 0.6f, now);
            e.height = 0.2f + rng.nextFloat() * 0.2f; // vertical band height
            entities.add(e);
        } else {
            // 20% wind gust (or cloud if <50m and lightning slot)
            if (metres < 50 && roll < 8) {
                Entity e = new Entity(EntityType.STORM_CLOUD, 1.1f, 0.1f + rng.nextFloat() * 0.8f, now);
                e.height = 0.08f + rng.nextFloat() * 0.04f;
                entities.add(e);
            } else {
                entities.add(new Entity(EntityType.WIND_GUST, 1.1f, 0.1f + rng.nextFloat() * 0.8f, now));
            }
        }
    }

    // ── Paint ────────────────────────────────────────────────────────────────

    @Override
    public void paint(Graphics2D g, int px, int py, int pw, int ph) {
        g.setFont(F_TITLE);
        g.setColor(SKY_BLUE);
        drawCentered(g, "GUST GLIDER", px, py + 30, pw);

        switch (state) {
            case BET          -> paintBet(g, px, py, pw, ph);
            case PLAY, LANDING -> paintPlay(g, px, py, pw, ph);
            case RESULT       -> paintResult(g, px, py, pw, ph);
        }
    }

    private void paintBet(Graphics2D g, int px, int py, int pw, int ph) {
        g.setFont(F_MENU);
        g.setColor(TEXT_BRIGHT);
        drawCentered(g, "Glide through the storm!", px, py + 70, pw);
        drawCentered(g, "Dodge clouds & lightning, collect gold rings.", px, py + 90, pw);
        drawCentered(g, "60m = 0.4x   108m = 0.8x   162m = 1.4x   228m = 2.0x  (+rings)", px, py + 110, pw);
        drawCentered(g, "Land to bank it. Crash and the stake is gone.", px, py + 130, pw);

        g.setFont(F_BIG);
        g.setColor(WIND_GOLD);
        drawCentered(g, "Bet: " + overlay.betAmount + "g", px, py + ph / 2, pw);

        g.setFont(F_ITEM);
        g.setColor(AMBER);
        drawCentered(g, "Gold: " + game.getPlayer().getGold() + "g", px, py + ph / 2 + 30, pw);

        g.setFont(F_SMALL);
        g.setColor(TEXT_DIM);
        int dex = game.getPlayer().getDex();
        drawCentered(g, "DEX " + dex + " (glider handling)", px, py + ph / 2 + 60, pw);

        g.setFont(F_KEY);
        g.setColor(TEXT_DIM);
        g.drawString("[Left/Right] Bet   [Shift] x10   [Enter] Launch   [Esc] Back", px + 20, py + ph - 20);
    }

    private void paintPlay(Graphics2D g, int px, int py, int pw, int ph) {
        int fieldX = px + 20;
        int fieldW = pw - 40;
        int fieldY = py + 60;
        int fieldH = ph - 105;

        // Sky gradient
        for (int row = 0; row < fieldH; row++) {
            float t = (float) row / fieldH;
            int r = (int)(SKY_TOP.getRed()   + t * (SKY_BOT.getRed()   - SKY_TOP.getRed()));
            int gr = (int)(SKY_TOP.getGreen() + t * (SKY_BOT.getGreen() - SKY_TOP.getGreen()));
            int b = (int)(SKY_TOP.getBlue()  + t * (SKY_BOT.getBlue()  - SKY_TOP.getBlue()));
            g.setColor(new Color(r, gr, b));
            g.drawLine(fieldX, fieldY + row, fieldX + fieldW, fieldY + row);
        }

        // Wind lines (parallax)
        g.setColor(WIND_LINE_COL);
        for (int i = 0; i < windLineX.length; i++) {
            int lx = fieldX + (int)(windLineX[i] * fieldW);
            int ly = fieldY + (int)(windLineY[i] * fieldH);
            g.drawLine(lx, ly, lx + 30, ly);
        }

        // Entities
        for (Entity e : entities) {
            int ex = fieldX + (int)(e.x * fieldW);
            int ey = fieldY + (int)(e.y * fieldH);

            switch (e.type) {
                case STORM_CLOUD -> {
                    int cw = (int)(e.height * fieldH * 1.8f); // wider than tall
                    int ch = (int)(e.height * fieldH);
                    // Shadow
                    g.setColor(CLOUD_DARK);
                    g.fillRoundRect(ex - cw / 2 + 2, ey - ch / 2 + 2, cw, ch, ch, ch);
                    // Body
                    g.setColor(CLOUD_LIGHT);
                    g.fillRoundRect(ex - cw / 2, ey - ch / 2, cw, ch, ch, ch);
                }
                case BOLT_WARNING -> {
                    int bandY = fieldY + (int)(e.y * fieldH);
                    int bandH = (int)(e.height * fieldH);
                    int bandX = fieldX + (int)(e.x * fieldW) - 15;
                    g.setColor(BOLT_WARN);
                    g.fillRect(bandX, bandY, 30, bandH);
                    // Warning symbol
                    g.setFont(F_MENU);
                    g.setColor(WIND_GOLD);
                    g.drawString("!", bandX + 12, bandY + bandH / 2 + 5);
                }
                case BOLT_STRIKE -> {
                    int bandY = fieldY + (int)(e.y * fieldH);
                    int bandH = (int)(e.height * fieldH);
                    int bandX = fieldX + (int)(e.x * fieldW) - 2;
                    // Bright zigzag
                    g.setColor(BOLT_STRIKE);
                    int zx = bandX;
                    int zy = bandY;
                    for (int seg = 0; seg < 6; seg++) {
                        int nx = zx + (seg % 2 == 0 ? 6 : -6);
                        int ny = zy + bandH / 6;
                        g.drawLine(zx, zy, nx, ny);
                        zx = nx;
                        zy = ny;
                    }
                    // Glow
                    g.setColor(new Color(255, 255, 255, 40));
                    g.fillRect(bandX - 12, bandY, 28, bandH);
                }
                case GOLD_RING -> {
                    g.setColor(RING_FILL);
                    g.fillOval(ex - 9, ey - 9, 18, 18);
                    g.setColor(RING_COL);
                    g.drawOval(ex - 9, ey - 9, 18, 18);
                    g.drawOval(ex - 7, ey - 7, 14, 14);
                }
                case WIND_GUST -> {
                    g.setColor(GUST_COL);
                    for (int line = -1; line <= 1; line++) {
                        int ly = ey + line * 5;
                        g.drawLine(ex - 10, ly, ex + 10, ly);
                        // Arrowhead
                        g.drawLine(ex + 10, ly, ex + 6, ly - 2);
                        g.drawLine(ex + 10, ly, ex + 6, ly + 2);
                    }
                }
            }
        }

        // Glider
        int gx = fieldX + (int)(0.13f * fieldW);
        int gy = fieldY + (int)(playerY * fieldH);

        boolean landing = state == State.LANDING;
        boolean justCrashed = crashTime > 0 && System.currentTimeMillis() - crashTime < 300;
        Color gliderColor = justCrashed ? GLIDER_HIT : GLIDER_COL;

        // Delta wing shape
        int[] wingX = { gx + 14, gx - 10, gx - 10 };
        int[] wingY = { gy,      gy - 9,  gy + 9  };
        g.setColor(gliderColor);
        g.fillPolygon(wingX, wingY, 3);
        // Outline
        g.setColor(gliderColor.darker());
        g.drawPolygon(wingX, wingY, 3);
        // Cockpit dot
        g.setColor(new Color(255, 255, 255, 160));
        g.fillOval(gx - 2, gy - 2, 4, 4);

        // Landing runway
        if (landing) {
            g.setColor(RUNWAY_COL);
            int rwy = fieldY + fieldH - 6;
            g.fillRect(fieldX, rwy, fieldW, 6);
            g.setColor(WIND_GOLD);
            for (int dx = 0; dx < fieldW; dx += 20) {
                g.fillRect(fieldX + dx + 8, rwy + 2, 6, 2);
            }
        }

        // Field border
        g.setColor(SKY_BLUE);
        g.drawRect(fieldX, fieldY, fieldW, fieldH);

        // ── HUD ─────────────────────────────────────────────────────────────
        int metres = metres();
        float mult = distanceMultiplier() + ringBonus();

        g.setFont(F_ITEM);
        g.setColor(AMBER);
        String hud = "Dist: " + metres + "m  |  Rings: " + ringsCollected
            + "  |  x" + formatMult(mult);
        drawCentered(g, hud, px, py + 50, pw);

        // Minimum distance warning
        if (distance < DIST_TIER1) {
            g.setFont(F_SMALL);
            g.setColor(TEXT_DIM);
            drawCentered(g, "Fly " + ((DIST_TIER1 - distance) * METRES_PER_SEC / 1000) + "m more to earn payout!", px, py + ph - 40, pw);
        }

        // Boost indicator
        if (System.currentTimeMillis() < gustBoostEnd) {
            g.setFont(F_SMALL);
            g.setColor(GUST_COL);
            g.drawString("BOOST!", px + 24, py + ph - 40);
        }

        // Controls
        g.setFont(F_KEY);
        g.setColor(TEXT_DIM);
        g.drawString("[Up/Down] Fly   [Enter] Cash out   [Esc] Land", px + 20, py + ph - 20);
    }

    private void paintResult(Graphics2D g, int px, int py, int pw, int ph) {
        int metres = metres();
        g.setFont(F_BIG);
        g.setColor(SKY_BLUE);
        drawCentered(g, "Distance: " + metres + "m", px, py + 120, pw);

        g.setFont(F_ITEM);
        g.setColor(STORM_GREY);
        drawCentered(g, "Rings collected: " + ringsCollected, px, py + 155, pw);

        float mult = distanceMultiplier() + ringBonus();
        if (mult > 0 && !resultMessage.contains("crashed")) {
            g.setColor(TEXT_DIM);
            drawCentered(g, "Distance x" + formatMult(distanceMultiplier())
                + "  +  Ring bonus x" + formatMult(ringBonus())
                + "  =  x" + formatMult(mult), px, py + 180, pw);
        }

        g.setFont(F_MENU);
        boolean won = resultMessage.contains("Won") || resultMessage.contains("MASTER");
        g.setColor(won ? GOOD : DANGER);
        drawCentered(g, resultMessage, px, py + 220, pw);

        g.setFont(F_KEY);
        g.setColor(TEXT_DIM);
        drawCentered(g, "[Any key] Continue", px, py + ph - 20, pw);
    }

    private static String formatMult(float m) {
        return m == (int) m ? String.valueOf((int) m) : String.format("%.1f", m);
    }
}

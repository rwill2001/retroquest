package io.cannonforge.retroquest.overlay;

import static io.cannonforge.retroquest.overlay.SkyGamesOverlay.SKY_BLUE;
import static io.cannonforge.retroquest.overlay.SkyGamesOverlay.STORM_GREY;
import static io.cannonforge.retroquest.overlay.SkyGamesOverlay.WIND_GOLD;
import static io.cannonforge.retroquest.overlay.SkyGamesOverlay.F_TITLE;
import static io.cannonforge.retroquest.overlay.SkyGamesOverlay.F_MENU;
import static io.cannonforge.retroquest.overlay.SkyGamesOverlay.F_BIG;
import static io.cannonforge.retroquest.overlay.SkyGamesOverlay.drawCentered;
import static io.cannonforge.retroquest.overlay.SkyGamesOverlay.drawCenteredAt;
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
 * Storm Rider: dodge lightning bolts and collect wind crystals while riding
 * a wind current. Speed increases over time. 3 hits = game over (CON bonus:
 * 4 hits). Score converts to gold payout.
 */
class StormRiderGame implements MiniGame {

    private enum State { BET, PLAY, RESULT }

    // Colours
    private static final Color BOLT_WARNING = new Color(255, 255, 100, 120);
    private static final Color BOLT_STRIKE  = new Color(255, 255, 220);
    private static final Color CRYSTAL_COL  = new Color(100, 220, 255);
    private static final Color COIN_COL     = new Color(255, 200, 60);
    private static final Color PLAYER_COL   = new Color(180, 220, 255);
    private static final Color PLAYER_HIT   = new Color(255, 100, 80);
    private static final Color WIND_LINE    = new Color(80, 130, 200, 40);
    private static final Color FIELD_BG     = new Color(12, 20, 40);
    private static final Color HP_BAR       = new Color(100, 200, 100);
    private static final Color HP_EMPTY     = new Color(60, 30, 30);

    private static final int LANES = 5;
    private static final int FIELD_W = 300;
    private static final long BOLT_WARN_MS  = 800;
    private static final long BOLT_STRIKE_MS = 200;
    private static final long SPAWN_BASE_MS = 1200;
    private static final long SPEED_INTERVAL_MS = 5000;
    private static final long INVULN_MS = 500;
    /** Hard time limit. Without one the run was endless, so score (and payout) had no ceiling. */
    private static final long RUN_LIMIT_MS = 45000;

    // -- Payout ladder --------------------------------------------------------
    // Score is 5 per crystal / 2 per coin over a 45s run. Values are the multiple
    // of the stake RETURNED (1.0 = break even, 0 = stake lost). Esc banks early.
    private static final int   SCORE_TOP = 70, SCORE_GREAT = 55, SCORE_GOOD = 40, SCORE_WEAK = 25;
    private static final float PAY_TOP = 2.5f, PAY_GREAT = 1.3f, PAY_GOOD = 0.6f, PAY_WEAK = 0.25f;

    private final Retroquest game;
    private final SkyGamesOverlay overlay;
    private final Random rng;

    private State state = State.BET;
    private int playerLane = 2;        // 0-4
    private int score = 0;
    private int hitsLeft = 3;
    private int maxHits = 3;
    private long gameStartTime = 0;
    private long lastSpawnTime = 0;
    private long lastHitTime = 0;
    private int speedLevel = 0;
    private String resultMessage = "";

    // Obstacles and collectibles
    private final List<Entity> entities = new ArrayList<>();

    // Wind lines for visual effect
    private final float[] windLineY = new float[12];

    private enum EntityType { BOLT_WARNING, BOLT_STRIKE, CRYSTAL, GOLD_COIN }

    private static class Entity {
        EntityType type;
        int lane;
        float y;        // 0.0 = top, 1.0 = bottom
        long spawnTime;

        Entity(EntityType type, int lane, float y, long spawnTime) {
            this.type = type;
            this.lane = lane;
            this.y = y;
            this.spawnTime = spawnTime;
        }
    }

    StormRiderGame(Retroquest game, SkyGamesOverlay overlay, Random rng) {
        this.game = game;
        this.overlay = overlay;
        this.rng = rng;
        for (int i = 0; i < windLineY.length; i++) windLineY[i] = rng.nextFloat();
    }

    @Override
    public void reset(int betAmount) {
        state = State.BET;
        playerLane = 2;
        score = 0;
        entities.clear();
        resultMessage = "";
        maxHits = game.getPlayer().getCon() >= 16 ? 4 : 3;
        hitsLeft = maxHits;
        speedLevel = 0;
    }

    @Override
    public boolean isShowingResult() { return state == State.RESULT; }

    @Override
    public void handleKey(KeyEvent e) {
        switch (state) {
            case BET    -> handleBetKey(e);
            case PLAY   -> handlePlayKey(e);
            case RESULT -> handleResultKey(e);
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
                gameStartTime = System.currentTimeMillis();
                lastSpawnTime = gameStartTime;
                state = State.PLAY;
            }
            case KeyEvent.VK_ESCAPE -> { overlay.backToMenu(); SoundManager.getInstance().play("menublip"); }
        }
    }

    private void handlePlayKey(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_LEFT  -> { if (playerLane > 0) playerLane--; }
            case KeyEvent.VK_RIGHT -> { if (playerLane < LANES - 1) playerLane++; }
            case KeyEvent.VK_ESCAPE -> resolveGame();
        }
    }

    private void handleResultKey(KeyEvent e) {
        overlay.backToMenu();
        if (game.getStatsPanel() != null) game.getStatsPanel().refresh();
    }

    private void resolveGame() {
        // Payout based on score
        int payout;
        if      (score >= SCORE_TOP)   payout = (int)(overlay.betAmount * PAY_TOP);
        else if (score >= SCORE_GREAT) payout = (int)(overlay.betAmount * PAY_GREAT);
        else if (score >= SCORE_GOOD)  payout = (int)(overlay.betAmount * PAY_GOOD);
        else if (score >= SCORE_WEAK)  payout = (int)(overlay.betAmount * PAY_WEAK);
        else payout = 0;

        if (payout > 0) {
            game.getPlayer().addGold(payout);
            resultMessage = score >= SCORE_TOP
                ? "STORM MASTER! Won " + payout + "g!"
                : "You won " + payout + "g!";
            SoundManager.getInstance().play(score >= SCORE_TOP ? "victory" : "coin");
            game.log(resultMessage, MessageLog.Type.LOOT);
        } else {
            resultMessage = "The storm overwhelms you. Lost " + overlay.betAmount + "g.";
            SoundManager.getInstance().play("hurt");
            game.log(resultMessage, MessageLog.Type.DANGER);
        }
        game.getPlayer().recordGamblingResult(payout - overlay.betAmount);
        state = State.RESULT;
    }

    @Override
    public void update() {
        if (state != State.PLAY) return;

        long now = System.currentTimeMillis();
        long elapsed = now - gameStartTime;

        // Speed ramp
        // The storm blows itself out after RUN_LIMIT_MS, capping what one ride can earn.
        if (elapsed >= RUN_LIMIT_MS) { resolveGame(); return; }

        speedLevel = (int)(elapsed / SPEED_INTERVAL_MS);
        float speedMult = 1.0f + speedLevel * 0.25f;

        // Spawn new entities
        long spawnInterval = Math.max(400, SPAWN_BASE_MS - speedLevel * 100L);
        if (now - lastSpawnTime >= spawnInterval) {
            lastSpawnTime = now;
            spawnEntity(now);
        }

        // Update wind lines
        for (int i = 0; i < windLineY.length; i++) {
            windLineY[i] += 0.015f * speedMult;
            if (windLineY[i] > 1.0f) windLineY[i] = 0;
        }

        // Update entities
        float moveSpeed = 0.008f * speedMult;
        boolean invuln = (now - lastHitTime) < INVULN_MS;

        Iterator<Entity> it = entities.iterator();
        while (it.hasNext()) {
            Entity e = it.next();

            // Bolt warning -> strike transition
            if (e.type == EntityType.BOLT_WARNING && now - e.spawnTime >= BOLT_WARN_MS) {
                e.type = EntityType.BOLT_STRIKE;
                e.spawnTime = now;
                // Check hit
                if (e.lane == playerLane && !invuln) {
                    hitsLeft--;
                    lastHitTime = now;
                    SoundManager.getInstance().play("hurt");
                    if (hitsLeft <= 0) {
                        resolveGame();
                        return;
                    }
                }
            }

            // Remove expired bolt strikes
            if (e.type == EntityType.BOLT_STRIKE && now - e.spawnTime >= BOLT_STRIKE_MS) {
                it.remove();
                continue;
            }

            // Move collectibles downward
            if (e.type == EntityType.CRYSTAL || e.type == EntityType.GOLD_COIN) {
                e.y += moveSpeed;

                // Check collection
                if (e.y >= 0.8f && e.y <= 0.95f && e.lane == playerLane) {
                    if (e.type == EntityType.CRYSTAL) {
                        score += 5;
                        SoundManager.getInstance().play("menublip");
                    } else {
                        score += 2;
                        SoundManager.getInstance().play("coin");
                    }
                    it.remove();
                    continue;
                }

                // Remove if past bottom
                if (e.y > 1.1f) {
                    it.remove();
                }
            }
        }
    }

    private void spawnEntity(long now) {
        int lane = rng.nextInt(LANES);
        int type = rng.nextInt(10);
        if (type < 4) {
            // 40% lightning
            entities.add(new Entity(EntityType.BOLT_WARNING, lane, 0, now));
        } else if (type < 7) {
            // 30% crystal
            entities.add(new Entity(EntityType.CRYSTAL, lane, -0.05f, now));
        } else {
            // 30% gold coin
            entities.add(new Entity(EntityType.GOLD_COIN, lane, -0.05f, now));
        }
    }

    @Override
    public void paint(Graphics2D g, int px, int py, int pw, int ph) {
        g.setFont(F_TITLE);
        g.setColor(SKY_BLUE);
        drawCentered(g, "STORM RIDER", px, py + 30, pw);

        switch (state) {
            case BET    -> paintBet(g, px, py, pw, ph);
            case PLAY   -> paintPlay(g, px, py, pw, ph);
            case RESULT -> paintResult(g, px, py, pw, ph);
        }
    }

    private void paintBet(Graphics2D g, int px, int py, int pw, int ph) {
        g.setFont(F_MENU);
        g.setColor(TEXT_BRIGHT);
        drawCentered(g, "Dodge lightning, collect crystals!", px, py + 70, pw);
        drawCentered(g, "Speed increases every 5 seconds.", px, py + 90, pw);

        g.setFont(F_BIG);
        g.setColor(WIND_GOLD);
        drawCentered(g, "Bet: " + overlay.betAmount + "g", px, py + ph / 2, pw);

        g.setFont(F_ITEM);
        g.setColor(AMBER);
        drawCentered(g, "Gold: " + game.getPlayer().getGold() + "g", px, py + ph / 2 + 30, pw);

        g.setFont(F_SMALL);
        g.setColor(TEXT_DIM);
        int con = game.getPlayer().getCon();
        drawCentered(g, "CON " + con + (con >= 16 ? " (4 hits!)" : " (3 hits)"), px, py + ph / 2 + 60, pw);
        drawCentered(g, "The storm lasts " + (RUN_LIMIT_MS / 1000) + "s.  " + SCORE_TOP + "+ = 2.5x   "
                + SCORE_GREAT + " = 1.3x   " + SCORE_GOOD + " = 0.6x   " + SCORE_WEAK + " = 0.25x", px, py + ph / 2 + 78, pw);

        g.setFont(F_KEY);
        g.setColor(TEXT_DIM);
        g.drawString("[Left/Right] Bet   [Shift] x10   [Enter] Ride!   [Esc] Back", px + 20, py + ph - 20);
    }

    private void paintPlay(Graphics2D g, int px, int py, int pw, int ph) {
        long now = System.currentTimeMillis();
        boolean invuln = (now - lastHitTime) < INVULN_MS;

        // Play field
        int fieldX = px + (pw - FIELD_W) / 2;
        int fieldY = py + 55;
        int fieldH = ph - 100;
        int laneW = FIELD_W / LANES;

        // Background
        g.setColor(FIELD_BG);
        g.fillRect(fieldX, fieldY, FIELD_W, fieldH);

        // Wind lines
        g.setColor(WIND_LINE);
        for (float wy : windLineY) {
            int lineY = fieldY + (int)(wy * fieldH);
            g.drawLine(fieldX, lineY, fieldX + FIELD_W, lineY);
        }

        // Lane dividers
        g.setColor(new Color(40, 60, 90));
        for (int i = 1; i < LANES; i++) {
            int lx = fieldX + i * laneW;
            for (int dy = 0; dy < fieldH; dy += 12) {
                g.drawLine(lx, fieldY + dy, lx, fieldY + dy + 6);
            }
        }

        // Entities
        for (Entity e : entities) {
            int elx = fieldX + e.lane * laneW;
            int ecx = elx + laneW / 2;

            switch (e.type) {
                case BOLT_WARNING -> {
                    // Full lane warning flash
                    g.setColor(BOLT_WARNING);
                    g.fillRect(elx + 2, fieldY, laneW - 4, fieldH);
                    // Warning symbol
                    g.setFont(F_BIG);
                    g.setColor(WIND_GOLD);
                    drawCenteredAt(g, "\u26A1", ecx, fieldY + fieldH / 2); // ⚡
                }
                case BOLT_STRIKE -> {
                    // Bright flash
                    g.setColor(BOLT_STRIKE);
                    g.fillRect(ecx - 2, fieldY, 4, fieldH);
                    g.setColor(new Color(255, 255, 255, 80));
                    g.fillRect(elx, fieldY, laneW, fieldH);
                }
                case CRYSTAL -> {
                    int ey = fieldY + (int)(e.y * fieldH);
                    g.setColor(CRYSTAL_COL);
                    // Diamond shape
                    int[] xp = {ecx, ecx + 8, ecx, ecx - 8};
                    int[] yp = {ey - 8, ey, ey + 8, ey};
                    g.fillPolygon(xp, yp, 4);
                    g.setColor(CRYSTAL_COL.brighter());
                    g.drawPolygon(xp, yp, 4);
                }
                case GOLD_COIN -> {
                    int ey = fieldY + (int)(e.y * fieldH);
                    g.setColor(COIN_COL);
                    g.fillOval(ecx - 6, ey - 6, 12, 12);
                    g.setColor(COIN_COL.darker());
                    g.drawOval(ecx - 6, ey - 6, 12, 12);
                }
            }
        }

        // Player
        int plx = fieldX + playerLane * laneW + laneW / 2;
        int ply = fieldY + (int)(0.85f * fieldH);
        g.setColor(invuln ? PLAYER_HIT : PLAYER_COL);
        // Body
        g.fillRect(plx - 5, ply - 6, 10, 12);
        // Head
        g.fillOval(plx - 4, ply - 12, 8, 8);
        // Wind cloak
        g.setColor(new Color(PLAYER_COL.getRed(), PLAYER_COL.getGreen(), PLAYER_COL.getBlue(), 80));
        g.fillRect(plx - 8, ply - 4, 16, 8);

        // Field border
        g.setColor(SKY_BLUE);
        g.drawRect(fieldX, fieldY, FIELD_W, fieldH);

        // HUD
        g.setFont(F_ITEM);
        g.setColor(AMBER);
        g.drawString("Score: " + score, px + 20, py + 50);

        // HP bar
        int hpBarX = px + pw - 140;
        int hpBarY = py + 40;
        int hpBarW = 120;
        int hpBarH = 12;
        g.setColor(HP_EMPTY);
        g.fillRect(hpBarX, hpBarY, hpBarW, hpBarH);
        g.setColor(hitsLeft <= 1 ? DANGER : HP_BAR);
        g.fillRect(hpBarX, hpBarY, hpBarW * hitsLeft / maxHits, hpBarH);
        g.setColor(TEXT_DIM);
        g.drawRect(hpBarX, hpBarY, hpBarW, hpBarH);
        g.setFont(F_SMALL);
        g.setColor(TEXT_BRIGHT);
        g.drawString("Hits: " + hitsLeft + "/" + maxHits, hpBarX, hpBarY - 3);

        // Speed indicator
        g.setFont(F_SMALL);
        g.setColor(speedLevel >= 4 ? DANGER : STORM_GREY);
        g.drawString("Speed: " + (speedLevel + 1) + "x", px + 20, py + ph - 25);

        g.setFont(F_KEY);
        g.setColor(TEXT_DIM);
        g.drawString("[Left/Right] Dodge   [Esc] End ride", px + 150, py + ph - 25);
    }

    private void paintResult(Graphics2D g, int px, int py, int pw, int ph) {
        g.setFont(F_BIG);
        g.setColor(SKY_BLUE);
        drawCentered(g, "Final Score: " + score, px, py + 140, pw);

        g.setFont(F_ITEM);
        g.setColor(STORM_GREY);
        long elapsed = 0;
        if (gameStartTime > 0) elapsed = (System.currentTimeMillis() - gameStartTime) / 1000;
        drawCentered(g, "Survived " + elapsed + " seconds", px, py + 170, pw);

        g.setFont(F_MENU);
        g.setColor(resultMessage.contains("won") || resultMessage.contains("Won") || resultMessage.contains("MASTER") ? GOOD : DANGER);
        drawCentered(g, resultMessage, px, py + 210, pw);

        g.setFont(F_KEY);
        g.setColor(TEXT_DIM);
        drawCentered(g, "[Any key] Continue", px, py + ph - 20, pw);
    }
}

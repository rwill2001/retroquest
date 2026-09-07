package io.cannonforge.retroquest.overlay;

import static io.cannonforge.retroquest.overlay.MemoryGamesOverlay.SHADOW_PURPLE;
import static io.cannonforge.retroquest.overlay.MemoryGamesOverlay.GHOST_WHITE;
import static io.cannonforge.retroquest.overlay.MemoryGamesOverlay.VOID_BLUE;
import static io.cannonforge.retroquest.overlay.MemoryGamesOverlay.GRIEF_GREY;
import static io.cannonforge.retroquest.overlay.MemoryGamesOverlay.F_TITLE;
import static io.cannonforge.retroquest.overlay.MemoryGamesOverlay.F_MENU;
import static io.cannonforge.retroquest.overlay.MemoryGamesOverlay.F_BIG;
import static io.cannonforge.retroquest.overlay.MemoryGamesOverlay.drawCentered;
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
 * Echoes: a sorting/catch mini-game. Labeled memories fall in 3 columns.
 * A target type is shown at the bottom. Move Left/Center/Right to catch the
 * right memories. Wrong catches penalize. WIS slows the fall speed.
 */
class EchoesGame implements MiniGame {

    private enum State { BET, PLAY, RESULT }

    private static final String[] TYPES      = { "SORROW", "HOPE", "REGRET", "WONDER" };
    private static final Color[]  TYPE_COLS  = {
        new Color(160,  70, 100),   // SORROW - muted red
        new Color( 80, 180, 140),   // HOPE   - teal
        new Color(160, 130,  60),   // REGRET - amber
        new Color( 80, 130, 200),   // WONDER - blue
    };
    private static final int   COLS       = 3;
    private static final int   PLAY_SECS  = 30;
    private static final long  BASE_SPAWN_MS = 1200;
    /** Fall rate in field-heights per SECOND (was 0.010 per frame, i.e. machine-dependent). */
    private static final float FALL_PER_SEC = 0.60f;

    // -- Payout ladder --------------------------------------------------------
    // A 30s round spawns ~33 memories and only a quarter carry the current target,
    // so ~8 catches is a strong round and 11 is near the ceiling. The old tiers
    // (5/10/15/20) sat above what the spawn rate can produce, leaving break-even
    // as the only reachable result. Values are the multiple of the stake RETURNED.
    private static final int   SCORE_TOP = 11, SCORE_GREAT = 9, SCORE_GOOD = 7, SCORE_WEAK = 5;
    private static final float PAY_TOP = 2.5f, PAY_GREAT = 1.3f, PAY_GOOD = 0.6f, PAY_WEAK = 0.25f;

    private static final Color PLAYER_COL = new Color(200, 200, 220);
    private static final Color FIELD_BG   = new Color( 10,   6,  20);

    private final Retroquest game;
    private final EchoesGame.MemoryEntity[] falling = new EchoesGame.MemoryEntity[0]; // placeholder
    private final MemoryGamesOverlay overlay;
    private final Random rng;

    private State state      = State.PLAY; // overridden by reset
    private int   playerCol  = 1;          // 0=left, 1=center, 2=right
    private int   score      = 0;
    private String targetType;
    private int   targetTypeIdx;
    private int   catchCount = 0;          // catches of current target; change target every 5
    private long  gameStart  = 0;
    private long  lastSpawn  = 0;
    private long  lastUpdate = 0;   // wall-clock anchor so fall speed is frame-rate independent
    private List<MemoryEntity> entities = new ArrayList<>();
    private String resultMessage = "";

    private static class MemoryEntity {
        String type;
        int col;
        float y;         // 0=top, 1=bottom
        long spawnTime;

        MemoryEntity(String type, int col, long spawnTime) {
            this.type = type; this.col = col; this.y = -0.05f; this.spawnTime = spawnTime;
        }
    }

    EchoesGame(Retroquest game, MemoryGamesOverlay overlay, Random rng) {
        this.game    = game;
        this.overlay = overlay;
        this.rng     = rng;
    }

    @Override public boolean isShowingResult() { return state == State.RESULT; }

    @Override
    public void reset(int betAmount) {
        state = State.BET;
        score = 0;
        catchCount = 0;
        entities.clear();
        resultMessage = "";
        playerCol = 1;
        pickNewTarget();
    }

    private void pickNewTarget() {
        targetTypeIdx = rng.nextInt(TYPES.length);
        targetType = TYPES[targetTypeIdx];
        catchCount = 0;
    }

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
                gameStart = System.currentTimeMillis();
                lastSpawn = gameStart;
                lastUpdate = gameStart;
                state = State.PLAY;
            }
            case KeyEvent.VK_ESCAPE -> { overlay.backToMenu(); SoundManager.getInstance().play("menublip"); }
        }
    }

    private void handlePlayKey(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_LEFT  -> playerCol = 0;
            case KeyEvent.VK_DOWN, KeyEvent.VK_UP -> playerCol = 1;
            case KeyEvent.VK_RIGHT -> playerCol = 2;
        }
    }

    private void handleResultKey(KeyEvent e) {
        overlay.backToMenu();
        if (game.getStatsPanel() != null) game.getStatsPanel().refresh();
    }

    @Override
    public void update() {
        if (state != State.PLAY) return;
        long now = System.currentTimeMillis();

        // Check time limit
        if (now - gameStart >= PLAY_SECS * 1000L) {
            resolveGame();
            return;
        }

        // Spawn
        int wis = game.getPlayer().getWisdom();
        long spawnMs = Math.max(700, BASE_SPAWN_MS - (now - gameStart) / 5000 * 100);
        if (now - lastSpawn >= spawnMs) {
            lastSpawn = now;
            int col = rng.nextInt(COLS);
            String type = TYPES[rng.nextInt(TYPES.length)];
            entities.add(new MemoryEntity(type, col, now));
        }

        // Move — distance is measured in elapsed milliseconds, not frames
        float dtSec = Math.min(0.25f, (now - lastUpdate) / 1000f);
        lastUpdate = now;
        float fallSpeed = FALL_PER_SEC * (1f - Math.max(0, wis - 10) * 0.02f) * dtSec;
        Iterator<MemoryEntity> it = entities.iterator();
        while (it.hasNext()) {
            MemoryEntity e = it.next();
            e.y += fallSpeed;

            // Collection zone: y >= 0.88
            if (e.y >= 0.88f && e.y <= 1.0f) {
                if (e.col == playerCol) {
                    if (e.type.equals(targetType)) {
                        score++;
                        catchCount++;
                        SoundManager.getInstance().play("coin");
                        if (catchCount >= 5) pickNewTarget();
                    } else {
                        score = Math.max(0, score - 1);
                        SoundManager.getInstance().play("hurt");
                    }
                    it.remove();
                    continue;
                }
            }
            if (e.y > 1.1f) it.remove();
        }
    }

    private void resolveGame() {
        int payout;
        if      (score >= SCORE_TOP)   payout = (int)(overlay.betAmount * PAY_TOP);
        else if (score >= SCORE_GREAT) payout = (int)(overlay.betAmount * PAY_GREAT);
        else if (score >= SCORE_GOOD)  payout = (int)(overlay.betAmount * PAY_GOOD);
        else if (score >= SCORE_WEAK)  payout = (int)(overlay.betAmount * PAY_WEAK);
        else                           payout = 0;

        if (payout > 0) {
            game.getPlayer().addGold(payout);
            resultMessage = score >= SCORE_TOP ? "PERFECT SORTER! Won " + payout + "g!"
                                               : score + " memories sorted! Won " + payout + "g!";
            SoundManager.getInstance().play(score >= SCORE_TOP ? "victory" : "coin");
            game.log(resultMessage, MessageLog.Type.LOOT);
        } else {
            resultMessage = "The echoes overwhelmed you. Lost " + overlay.betAmount + "g.";
            SoundManager.getInstance().play("hurt");
            game.log(resultMessage, MessageLog.Type.DANGER);
        }
        game.getPlayer().recordGamblingResult(payout - overlay.betAmount);
        state = State.RESULT;
    }

    @Override
    public void paint(Graphics2D g, int px, int py, int pw, int ph) {
        g.setFont(F_TITLE);
        g.setColor(SHADOW_PURPLE);
        drawCentered(g, "ECHOES", px, py + 30, pw);

        switch (state) {
            case BET    -> paintBet(g, px, py, pw, ph);
            case PLAY   -> paintPlay(g, px, py, pw, ph);
            case RESULT -> paintResult(g, px, py, pw, ph);
        }
    }

    private void paintBet(Graphics2D g, int px, int py, int pw, int ph) {
        g.setFont(F_MENU);
        g.setColor(TEXT_BRIGHT);
        drawCentered(g, "Catch only the memories shown at the bottom!", px, py + 70, pw);
        drawCentered(g, "Wrong catches reduce your score. 30 seconds.", px, py + 90, pw);
        g.setFont(F_SMALL);
        drawCentered(g, SCORE_TOP + "+ = 2.5x   " + SCORE_GREAT + " = 1.3x   " + SCORE_GOOD
                + " = 0.6x   " + SCORE_WEAK + " = 0.25x   under " + SCORE_WEAK + " = lost", px, py + 110, pw);
        g.setFont(F_MENU);

        g.setFont(F_BIG);
        g.setColor(SHADOW_PURPLE);
        drawCentered(g, "Bet: " + overlay.betAmount + "g", px, py + ph / 2, pw);

        g.setFont(F_ITEM);
        g.setColor(AMBER);
        drawCentered(g, "Gold: " + game.getPlayer().getGold() + "g", px, py + ph / 2 + 30, pw);

        g.setFont(F_SMALL);
        g.setColor(TEXT_DIM);
        drawCentered(g, "WIS " + game.getPlayer().getWisdom() + " (slower fall speed)", px, py + ph / 2 + 60, pw);

        g.setFont(F_KEY);
        g.setColor(TEXT_DIM);
        g.drawString("[Left/Right] Bet   [Shift] x10   [Enter] Start   [Esc] Back", px + 20, py + ph - 20);
    }

    private void paintPlay(Graphics2D g, int px, int py, int pw, int ph) {
        int colW   = 120;
        int fieldW = COLS * colW;
        int fieldX = px + (pw - fieldW) / 2;
        int fieldY = py + 60;
        int fieldH = ph - 120;

        // Background
        g.setColor(FIELD_BG);
        g.fillRect(fieldX, fieldY, fieldW, fieldH);

        // Column dividers
        g.setColor(new Color(40, 30, 70));
        for (int c = 1; c < COLS; c++) g.drawLine(fieldX + c * colW, fieldY, fieldX + c * colW, fieldY + fieldH);

        // Falling entities
        for (MemoryEntity e : entities) {
            int ex = fieldX + e.col * colW + colW / 2;
            int ey = fieldY + (int)(e.y * fieldH);
            int typeIdx = indexOf(TYPES, e.type);
            Color col = typeIdx >= 0 ? TYPE_COLS[typeIdx] : GRIEF_GREY;
            g.setColor(col);
            g.fillRoundRect(ex - 38, ey - 10, 76, 20, 6, 6);
            g.setFont(F_SMALL);
            g.setColor(GHOST_WHITE);
            int tw = g.getFontMetrics().stringWidth(e.type);
            g.drawString(e.type, ex - tw / 2, ey + 5);
        }

        // Player
        int px2 = fieldX + playerCol * colW;
        g.setColor(new Color(40, 30, 70));
        g.fillRect(px2, fieldY + fieldH - 24, colW, 24);
        g.setColor(PLAYER_COL);
        g.drawRect(px2, fieldY + fieldH - 24, colW, 24);
        g.setFont(F_SMALL);
        g.setColor(GHOST_WHITE);
        drawCentered(g, "[ YOU ]", px2, fieldY + fieldH - 8, colW);

        // Field border
        g.setColor(SHADOW_PURPLE);
        g.drawRect(fieldX, fieldY, fieldW, fieldH);

        // Target label
        long elapsed = System.currentTimeMillis() - gameStart;
        long remaining = Math.max(0, PLAY_SECS * 1000L - elapsed);
        int remSec = (int)(remaining / 1000) + 1;
        int targetIdx = indexOf(TYPES, targetType);
        Color tc = targetIdx >= 0 ? TYPE_COLS[targetIdx] : GRIEF_GREY;

        g.setFont(F_ITEM);
        g.setColor(AMBER);
        g.drawString("Score: " + score, px + 20, py + 50);

        g.setFont(F_ITEM);
        g.setColor(tc);
        drawCentered(g, "Catch: " + targetType, px, py + ph - 45, pw);

        g.setFont(F_SMALL);
        g.setColor(remaining < 5000 ? DANGER : TEXT_DIM);
        drawCentered(g, "Time: " + remSec + "s", px, py + ph - 28, pw);

        g.setFont(F_KEY);
        g.setColor(TEXT_DIM);
        drawCentered(g, "[Left/Right] Move", px, py + ph - 12, pw);
    }

    private void paintResult(Graphics2D g, int px, int py, int pw, int ph) {
        g.setFont(F_BIG);
        g.setColor(SHADOW_PURPLE);
        drawCentered(g, "Score: " + score, px, py + 130, pw);

        g.setFont(F_MENU);
        boolean won = resultMessage.contains("Won") || resultMessage.contains("PERFECT");
        g.setColor(won ? GOOD : DANGER);
        drawCentered(g, resultMessage, px, py + 175, pw);

        g.setFont(F_KEY);
        g.setColor(TEXT_DIM);
        drawCentered(g, "[Any key] Continue", px, py + ph - 20, pw);
    }

    private static int indexOf(String[] arr, String val) {
        for (int i = 0; i < arr.length; i++) if (arr[i].equals(val)) return i;
        return -1;
    }
}

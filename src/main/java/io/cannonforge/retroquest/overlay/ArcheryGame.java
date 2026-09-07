package io.cannonforge.retroquest.overlay;

import static io.cannonforge.retroquest.overlay.NatureGamesOverlay.FOREST_GREEN;
import static io.cannonforge.retroquest.overlay.NatureGamesOverlay.LEAF_BRIGHT;
import static io.cannonforge.retroquest.overlay.NatureGamesOverlay.BARK_BROWN;
import static io.cannonforge.retroquest.overlay.NatureGamesOverlay.EARTH_AMBER;
import static io.cannonforge.retroquest.overlay.NatureGamesOverlay.F_TITLE;
import static io.cannonforge.retroquest.overlay.NatureGamesOverlay.F_MENU;
import static io.cannonforge.retroquest.overlay.NatureGamesOverlay.F_BIG;
import static io.cannonforge.retroquest.overlay.NatureGamesOverlay.drawCentered;
import static io.cannonforge.retroquest.overlay.OverlayTheme.*;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.util.Random;

import io.cannonforge.retroquest.core.MessageLog;
import io.cannonforge.retroquest.core.Retroquest;
import io.cannonforge.retroquest.core.SoundManager;

/**
 * Archery Range: a timing mini-game. A power bar oscillates back and forth.
 * Press Enter when the bar is in the green "sweet spot" to release the arrow.
 * Wind pushes shots left or right. DEX widens the bullseye hitbox.
 * Score is tallied over 5 shots.
 */
class ArcheryGame implements MiniGame {

    private enum State { BET, AIM, FLIGHT, SCORE_PAUSE, RESULT }

    private static final Color TARGET_RING  = new Color(200,  60,  50);
    private static final Color TARGET_GOLD  = new Color(220, 180,  50);
    private static final Color ARROW_COL    = new Color(160, 100,  40);
    private static final Color GROUND_COL   = new Color( 60,  80,  30);
    private static final Color SKY_COL      = new Color( 70, 100, 140);
    private static final Color POWER_GOOD   = new Color( 80, 200,  80);
    private static final Color POWER_BAD    = new Color(200,  80,  80);
    private static final Color WIND_COL     = new Color(150, 200, 255);

    private static final int   SHOTS        = 5;

    // ── Wind vs DEX ───────────────────────────────────────────────────────────
    // Wind is +/-3 units. DEX can steady the shot but never fully cancel a gust:
    // relief is capped at DEX_WIND_RELIEF_CAP of the drift, so a high-DEX archer
    // still has to time the power bar. (Previously DEX 18+ erased the worst wind
    // outright, which made the top payout automatic.)
    private static final float WIND_PER_UNIT        = 0.06f;
    private static final float DEX_RELIEF_PER_POINT = 0.02f;
    private static final float DEX_WIND_RELIEF_CAP  = 0.75f;

    // ── Payout ladder ─────────────────────────────────────────────────────────
    // Score is 0-25 (5 shots x 5 for a bullseye). Values are the multiple of the
    // stake RETURNED (1.0 = break even, 0 = stake lost).
    private static final int   SCORE_TOP = 19, SCORE_GREAT = 15, SCORE_GOOD = 11, SCORE_WEAK = 7;
    private static final float PAY_TOP = 2.5f, PAY_GREAT = 1.4f, PAY_GOOD = 0.75f, PAY_WEAK = 0.35f;
    private static final long  POWER_PERIOD = 2400; // ms for full oscillation
    private static final long  FLIGHT_MS    = 600;
    private static final long  PAUSE_MS     = 1000;

    private final Retroquest game;
    private final NatureGamesOverlay overlay;
    private final Random rng;

    private State state = State.BET;
    private int   shotsFired = 0;
    private int   totalScore = 0;
    private int   wind = 0;           // -3 to +3 (negative = left)
    private float powerFrac = 0f;     // 0.0 – 1.0 (oscillating)
    private long  powerStart = 0;
    private long  flightStart = 0;
    private long  pauseStart  = 0;
    private float arrowX = 0f;       // landing position normalised 0-1 relative to target
    private int   lastShotScore = 0;
    private String resultMessage = "";

    ArcheryGame(Retroquest game, NatureGamesOverlay overlay, Random rng) {
        this.game    = game;
        this.overlay = overlay;
        this.rng     = rng;
    }

    @Override public boolean isShowingResult() { return state == State.RESULT; }

    @Override
    public void reset(int betAmount) {
        state = State.BET;
        shotsFired  = 0;
        totalScore  = 0;
        resultMessage = "";
        newShot();
    }

    private void newShot() {
        wind = rng.nextInt(7) - 3; // -3 to +3
        powerStart = System.currentTimeMillis();
    }

    @Override
    public void handleKey(KeyEvent e) {
        switch (state) {
            case BET         -> handleBetKey(e);
            case AIM         -> handleAimKey(e);
            case RESULT      -> handleResultKey(e);
            default          -> {}
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
                powerStart = System.currentTimeMillis();
                state = State.AIM;
            }
            case KeyEvent.VK_ESCAPE -> { overlay.backToMenu(); SoundManager.getInstance().play("menublip"); }
        }
    }

    private void handleAimKey(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_ENTER) {
            fireArrow();
        }
    }

    private void handleResultKey(KeyEvent e) {
        overlay.backToMenu();
        if (game.getStatsPanel() != null) game.getStatsPanel().refresh();
    }

    private void fireArrow() {
        // Current power (0-1). Wind drift: each unit = 0.08 displacement.
        // Perfect = power 0.5 → 0 error. DEX widens the bullseye (reduces required precision).
        float err = Math.abs(powerFrac - 0.5f) * 2f; // 0=perfect, 1=worst
        float windOffset = Math.abs(wind * WIND_PER_UNIT);

        // DEX steadies the bow against the wind only — it can never cancel a
        // mistimed release, and never more than DEX_WIND_RELIEF_CAP of the drift.
        int dex = game.getPlayer().getDex();
        float relief = Math.min(windOffset * DEX_WIND_RELIEF_CAP,
                                Math.max(0, dex - 10) * DEX_RELIEF_PER_POINT);
        arrowX = Math.max(0, err + windOffset - relief); // combined miss distance (0=bull)

        lastShotScore = arrowX < 0.08f ? 5 : arrowX < 0.22f ? 2 : arrowX < 0.45f ? 1 : 0;

        SoundManager.getInstance().play(lastShotScore >= 5 ? "coin" : lastShotScore > 0 ? "menublip" : "hurt");
        flightStart = System.currentTimeMillis();
        state = State.FLIGHT;
    }

    @Override
    public void update() {
        long now = System.currentTimeMillis();

        if (state == State.AIM) {
            // Oscillate power 0→1→0
            long elapsed = (now - powerStart) % POWER_PERIOD;
            powerFrac = elapsed < POWER_PERIOD / 2
                ? (float) elapsed / (POWER_PERIOD / 2)
                : 1f - (float)(elapsed - POWER_PERIOD / 2) / (POWER_PERIOD / 2);
        } else if (state == State.FLIGHT) {
            if (now - flightStart >= FLIGHT_MS) {
                totalScore += lastShotScore;
                shotsFired++;
                pauseStart = now;
                state = State.SCORE_PAUSE;
            }
        } else if (state == State.SCORE_PAUSE) {
            if (now - pauseStart >= PAUSE_MS) {
                if (shotsFired >= SHOTS) {
                    resolveGame();
                } else {
                    newShot();
                    state = State.AIM;
                }
            }
        }
    }

    private void resolveGame() {
        int payout;
        if      (totalScore >= SCORE_TOP)   payout = (int)(overlay.betAmount * PAY_TOP);
        else if (totalScore >= SCORE_GREAT) payout = (int)(overlay.betAmount * PAY_GREAT);
        else if (totalScore >= SCORE_GOOD)  payout = (int)(overlay.betAmount * PAY_GOOD);
        else if (totalScore >= SCORE_WEAK)  payout = (int)(overlay.betAmount * PAY_WEAK);
        else                                payout = 0;

        if (payout > 0) {
            game.getPlayer().addGold(payout);
            resultMessage = totalScore >= SCORE_TOP ? "MASTER ARCHER! Won " + payout + "g!"
                                                    : "Good shooting! Won " + payout + "g!";
            SoundManager.getInstance().play(totalScore >= SCORE_TOP ? "victory" : "coin");
            game.log(resultMessage, MessageLog.Type.LOOT);
        } else {
            resultMessage = "Not enough hits. Lost " + overlay.betAmount + "g.";
            SoundManager.getInstance().play("hurt");
            game.log(resultMessage, MessageLog.Type.DANGER);
        }
        game.getPlayer().recordGamblingResult(payout - overlay.betAmount);
        state = State.RESULT;
    }

    @Override
    public void paint(Graphics2D g, int px, int py, int pw, int ph) {
        g.setFont(F_TITLE);
        g.setColor(FOREST_GREEN);
        drawCentered(g, "ARCHERY RANGE", px, py + 30, pw);

        switch (state) {
            case BET                       -> paintBet(g, px, py, pw, ph);
            case AIM, FLIGHT, SCORE_PAUSE  -> paintAim(g, px, py, pw, ph);
            case RESULT                    -> paintResult(g, px, py, pw, ph);
        }
    }

    private void paintBet(Graphics2D g, int px, int py, int pw, int ph) {
        g.setFont(F_MENU);
        g.setColor(TEXT_BRIGHT);
        drawCentered(g, "Watch the power bar and fire at the right moment!", px, py + 70, pw);
        drawCentered(g, "Wind will push your arrow left or right.", px, py + 90, pw);

        g.setFont(F_BIG);
        g.setColor(EARTH_AMBER);
        drawCentered(g, "Bet: " + overlay.betAmount + "g", px, py + ph / 2, pw);

        g.setFont(F_ITEM);
        g.setColor(AMBER);
        drawCentered(g, "Gold: " + game.getPlayer().getGold() + "g", px, py + ph / 2 + 30, pw);

        g.setFont(F_SMALL);
        g.setColor(TEXT_DIM);
        int dex = game.getPlayer().getDex();
        drawCentered(g, "DEX " + dex + " (bullseye accuracy)", px, py + ph / 2 + 60, pw);

        g.setFont(F_KEY);
        g.setColor(TEXT_DIM);
        g.drawString("[Left/Right] Bet   [Shift] x10   [Enter] Start   [Esc] Back", px + 20, py + ph - 20);
    }

    private void paintAim(Graphics2D g, int px, int py, int pw, int ph) {
        // Sky and ground
        int fieldX = px + 30;
        int fieldW = pw - 60;
        int fieldY = py + 55;
        int fieldH = ph - 110;
        int groundY = fieldY + (int)(fieldH * 0.7f);

        g.setColor(SKY_COL);
        g.fillRect(fieldX, fieldY, fieldW, groundY - fieldY);
        g.setColor(GROUND_COL);
        g.fillRect(fieldX, groundY, fieldW, fieldH - (groundY - fieldY));

        // Target (right side of field)
        int tx = fieldX + (int)(fieldW * 0.75f);
        int ty = groundY - 40;
        int tr = 28;
        g.setColor(TARGET_RING);  g.fillOval(tx - tr, ty - tr, tr * 2, tr * 2);
        g.setColor(Color.WHITE);  g.fillOval(tx - (int)(tr * 0.66f), ty - (int)(tr * 0.66f), (int)(tr * 1.33f), (int)(tr * 1.33f));
        g.setColor(TARGET_RING);  g.fillOval(tx - tr / 2, ty - tr / 2, tr, tr);
        g.setColor(TARGET_GOLD);  g.fillOval(tx - tr / 4, ty - tr / 4, tr / 2, tr / 2);
        // Target stand
        g.setColor(BARK_BROWN);
        g.fillRect(tx - 3, ty + tr, 6, 30);

        // Arrow in flight
        if (state == State.FLIGHT) {
            float prog = Math.min(1f, (System.currentTimeMillis() - flightStart) / (float) FLIGHT_MS);
            int ax = fieldX + (int)(fieldW * 0.15f) + (int)((tx - fieldX - (int)(fieldW * 0.15f)) * prog);
            int ay = groundY - 20 - (int)(Math.sin(prog * Math.PI) * 60);
            g.setColor(ARROW_COL);
            g.drawLine(ax - 10, ay, ax + 10, ay);
            g.fillRect(ax + 8, ay - 2, 4, 4);
        }

        // Score pause: show hit zone on target
        if (state == State.SCORE_PAUSE) {
            String scoreText = lastShotScore >= 5 ? "BULLSEYE! +" + lastShotScore
                : lastShotScore > 0 ? "Hit! +" + lastShotScore : "Miss!";
            g.setFont(F_MENU);
            g.setColor(lastShotScore > 0 ? LEAF_BRIGHT : DANGER);
            drawCentered(g, scoreText, px, py + 50, pw);
        }

        // Power bar
        int barX = fieldX;
        int barY = fieldY + fieldH + 5;
        int barW = fieldW;
        int barH = 14;
        // Background
        g.setColor(new Color(30, 30, 30));
        g.fillRect(barX, barY, barW, barH);
        // Good zone (green, 40-60%)
        int goodX = barX + (int)(barW * 0.4f);
        int goodW = (int)(barW * 0.2f);
        g.setColor(new Color(0, 80, 0));
        g.fillRect(goodX, barY, goodW, barH);
        // Fill to current power
        g.setColor(powerFrac > 0.4f && powerFrac < 0.6f ? POWER_GOOD : POWER_BAD);
        g.fillRect(barX, barY, (int)(barW * powerFrac), barH);
        g.setColor(TEXT_DIM);
        g.drawRect(barX, barY, barW, barH);

        // Wind indicator
        g.setFont(F_SMALL);
        g.setColor(WIND_COL);
        String windStr = wind == 0 ? "Wind: calm"
            : wind < 0 ? "Wind: <" + Math.abs(wind) : "Wind: >" + wind;
        g.drawString(windStr, fieldX + 5, fieldY + 15);

        // HUD
        g.setFont(F_ITEM);
        g.setColor(EARTH_AMBER);
        g.drawString("Shot: " + (shotsFired + 1) + "/" + SHOTS + "  Score: " + totalScore, px + 20, py + 50);

        g.setFont(F_KEY);
        g.setColor(TEXT_DIM);
        drawCentered(g, "[Enter] Release arrow", px, py + ph - 20, pw);
    }

    private void paintResult(Graphics2D g, int px, int py, int pw, int ph) {
        g.setFont(F_BIG);
        g.setColor(FOREST_GREEN);
        drawCentered(g, "Final Score: " + totalScore + "/" + (SHOTS * 5), px, py + 130, pw);

        g.setFont(F_MENU);
        boolean won = resultMessage.contains("Won") || resultMessage.contains("MASTER");
        g.setColor(won ? GOOD : DANGER);
        drawCentered(g, resultMessage, px, py + 175, pw);

        g.setFont(F_KEY);
        g.setColor(TEXT_DIM);
        drawCentered(g, "[Any key] Continue", px, py + ph - 20, pw);
    }
}

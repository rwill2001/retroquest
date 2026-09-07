package io.cannonforge.retroquest.overlay;

import static io.cannonforge.retroquest.overlay.WarGamesOverlay.WAR_RED;
import static io.cannonforge.retroquest.overlay.WarGamesOverlay.IRON_GREY;
import static io.cannonforge.retroquest.overlay.WarGamesOverlay.BATTLE_GOLD;
import static io.cannonforge.retroquest.overlay.WarGamesOverlay.F_TITLE;
import static io.cannonforge.retroquest.overlay.WarGamesOverlay.F_MENU;
import static io.cannonforge.retroquest.overlay.WarGamesOverlay.F_BIG;
import static io.cannonforge.retroquest.overlay.WarGamesOverlay.drawCentered;
import static io.cannonforge.retroquest.overlay.OverlayTheme.*;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.util.Random;

import io.cannonforge.retroquest.core.MessageLog;
import io.cannonforge.retroquest.core.Retroquest;
import io.cannonforge.retroquest.core.SoundManager;

/**
 * Siege Catapult: an aim-and-power mini-game. Left/Right adjusts the launch
 * angle. A power bar oscillates — press Enter to fire. Score by proximity
 * to target over 5 shots. STR bonus widens the power sweet spot.
 */
class SiegeCatapultGame implements MiniGame {

    private enum State { BET, AIM, FLIGHT, SCORE_PAUSE, RESULT }

    private static final Color SKY_COL    = new Color(60,  50,  40);
    private static final Color GROUND_COL = new Color(50,  40,  25);
    private static final Color TARGET_COL = new Color(200, 60,  50);
    private static final Color ARM_COL    = new Color(110, 75,  35);
    private static final Color STONE_COL  = new Color(140, 130, 120);
    private static final Color GOOD_ZONE  = new Color(80, 200,  80, 80);

    private static final int   SHOTS       = 5;

    // ── Payout ladder ─────────────────────────────────────────────────────────
    // Score is 0-25 (5 shots x 5 for a direct hit). Values are the multiple of
    // the stake RETURNED (1.0 = break even, 0 = stake lost).
    private static final int   SCORE_TOP = 19, SCORE_GREAT = 15, SCORE_GOOD = 11, SCORE_WEAK = 7;
    private static final float PAY_TOP = 2.5f, PAY_GREAT = 1.4f, PAY_GOOD = 0.75f, PAY_WEAK = 0.35f;
    private static final long  POWER_PERIOD = 2800;
    private static final long  FLIGHT_MS    = 700;
    private static final long  PAUSE_MS     = 1000;
    private static final int   ANGLE_MIN    = 20;
    private static final int   ANGLE_MAX    = 75;

    private final Retroquest game;
    private final WarGamesOverlay overlay;
    private final Random rng;

    private State state     = State.BET;
    private int   angle     = 45;      // launch angle in degrees
    private float powerFrac = 0f;
    private long  powerStart = 0;
    private float targetX   = 0f;     // normalized target position 0-1
    private int   shotsFired = 0;
    private int   totalScore = 0;
    private int   lastScore  = 0;
    private long  flightStart = 0;
    private long  pauseStart  = 0;
    private float landX = 0f;
    private String resultMessage = "";

    SiegeCatapultGame(Retroquest game, WarGamesOverlay overlay, Random rng) {
        this.game    = game;
        this.overlay = overlay;
        this.rng     = rng;
    }

    @Override public boolean isShowingResult() { return state == State.RESULT; }

    @Override
    public void reset(int betAmount) {
        state = State.BET;
        angle = 45;
        shotsFired = 0;
        totalScore = 0;
        resultMessage = "";
        newShot();
    }

    private void newShot() {
        targetX = 0.4f + rng.nextFloat() * 0.45f;
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
        switch (e.getKeyCode()) {
            case KeyEvent.VK_LEFT  -> angle = Math.max(ANGLE_MIN, angle - 3);
            case KeyEvent.VK_RIGHT -> angle = Math.min(ANGLE_MAX, angle + 3);
            case KeyEvent.VK_ENTER -> fire();
        }
    }

    private void handleResultKey(KeyEvent e) {
        overlay.backToMenu();
        if (game.getStatsPanel() != null) game.getStatsPanel().refresh();
    }

    private void fire() {
        // Landing X: physics approximation.
        // range ∝ sin(2*angle) * power. Ideal angle=45deg, ideal power=50%.
        // Normalize so that angle=45, power=0.5 → lands exactly on target.
        double rad = Math.toRadians(angle * 2);
        float angleFactor = (float) Math.sin(rad); // max 1.0 at 45deg
        float powerFactor = powerFrac;  // 0-1

        // Base range: angleFactor * powerFactor * fieldWidth lands on some distance.
        // We'll normalise so target.x = angleFactor_ideal * powerFactor_ideal.
        // Expected land = angleFactor * powerFactor * 1.1 (scale to field)
        float expected = angleFactor * powerFactor * 1.1f;

        // STR bonus: reduces power error
        int str = game.getPlayer().getStr();
        float strBonus = Math.max(0, (str - 10)) * 0.01f;

        float error = Math.abs(expected - targetX) - strBonus;
        error = Math.max(0, error);

        lastScore = error < 0.03f ? 5 : error < 0.10f ? 2 : error < 0.22f ? 1 : 0;
        landX = expected;

        SoundManager.getInstance().play(lastScore >= 5 ? "coin" : lastScore > 0 ? "menublip" : "hurt");
        flightStart = System.currentTimeMillis();
        state = State.FLIGHT;
    }

    @Override
    public void update() {
        long now = System.currentTimeMillis();

        if (state == State.AIM) {
            long elapsed = (now - powerStart) % POWER_PERIOD;
            powerFrac = elapsed < POWER_PERIOD / 2
                ? (float) elapsed / (POWER_PERIOD / 2)
                : 1f - (float)(elapsed - POWER_PERIOD / 2) / (POWER_PERIOD / 2);
        } else if (state == State.FLIGHT) {
            if (now - flightStart >= FLIGHT_MS) {
                totalScore += lastScore;
                shotsFired++;
                pauseStart = now;
                state = State.SCORE_PAUSE;
            }
        } else if (state == State.SCORE_PAUSE) {
            if (now - pauseStart >= PAUSE_MS) {
                if (shotsFired >= SHOTS) resolveGame();
                else { newShot(); state = State.AIM; }
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
            resultMessage = totalScore >= SCORE_TOP ? "SIEGE MASTER! Won " + payout + "g!"
                                                    : "Well aimed! Won " + payout + "g!";
            SoundManager.getInstance().play(totalScore >= SCORE_TOP ? "victory" : "coin");
            game.log(resultMessage, MessageLog.Type.LOOT);
        } else {
            resultMessage = "Missed too much. Lost " + overlay.betAmount + "g.";
            SoundManager.getInstance().play("hurt");
            game.log(resultMessage, MessageLog.Type.DANGER);
        }
        game.getPlayer().recordGamblingResult(payout - overlay.betAmount);
        state = State.RESULT;
    }

    @Override
    public void paint(Graphics2D g, int px, int py, int pw, int ph) {
        g.setFont(F_TITLE);
        g.setColor(WAR_RED);
        drawCentered(g, "SIEGE CATAPULT", px, py + 30, pw);

        switch (state) {
            case BET                      -> paintBet(g, px, py, pw, ph);
            case AIM, FLIGHT, SCORE_PAUSE -> paintAim(g, px, py, pw, ph);
            case RESULT                   -> paintResult(g, px, py, pw, ph);
        }
    }

    private void paintBet(Graphics2D g, int px, int py, int pw, int ph) {
        g.setFont(F_MENU);
        g.setColor(TEXT_BRIGHT);
        drawCentered(g, "Adjust angle with Left/Right. Watch the power bar!", px, py + 70, pw);
        drawCentered(g, "Fire when the bar is in the green zone.", px, py + 90, pw);

        g.setFont(F_BIG);
        g.setColor(BATTLE_GOLD);
        drawCentered(g, "Bet: " + overlay.betAmount + "g", px, py + ph / 2, pw);

        g.setFont(F_ITEM);
        g.setColor(AMBER);
        drawCentered(g, "Gold: " + game.getPlayer().getGold() + "g", px, py + ph / 2 + 30, pw);

        g.setFont(F_SMALL);
        g.setColor(TEXT_DIM);
        drawCentered(g, "STR " + game.getPlayer().getStr() + " (aim accuracy)", px, py + ph / 2 + 60, pw);

        g.setFont(F_KEY);
        g.setColor(TEXT_DIM);
        g.drawString("[Left/Right] Bet   [Shift] x10   [Enter] Start   [Esc] Back", px + 20, py + ph - 20);
    }

    private void paintAim(Graphics2D g, int px, int py, int pw, int ph) {
        int fieldX = px + 30;
        int fieldW = pw - 60;
        int fieldY = py + 55;
        int fieldH = ph - 110;
        int groundY = fieldY + (int)(fieldH * 0.72f);

        // Sky and ground
        g.setColor(SKY_COL);  g.fillRect(fieldX, fieldY, fieldW, groundY - fieldY);
        g.setColor(GROUND_COL); g.fillRect(fieldX, groundY, fieldW, fieldH - (groundY - fieldY));

        // Target
        int tx = fieldX + (int)(targetX * fieldW);
        g.setColor(TARGET_COL);
        // Fortress wall silhouette
        g.fillRect(tx - 20, groundY - 35, 40, 35);
        g.fillRect(tx - 25, groundY - 45, 12, 15);
        g.fillRect(tx - 6, groundY - 45, 12, 15);
        g.fillRect(tx + 13, groundY - 45, 12, 15);

        // Catapult (left side of field)
        int cx = fieldX + 50, cy = groundY;
        g.setColor(ARM_COL);
        // Base
        g.fillRect(cx - 15, cy - 12, 30, 12);
        // Arm (rotated by angle)
        double armRad = Math.toRadians(90 - angle);
        int ax = (int)(Math.cos(armRad) * 40), ay = (int)(Math.sin(armRad) * 40);
        g.drawLine(cx, cy - 6, cx + ax, cy - 6 - ay);
        // Projectile on arm
        if (state == State.AIM) {
            g.setColor(STONE_COL);
            g.fillOval(cx + ax - 4, cy - 6 - ay - 4, 8, 8);
        }

        // In-flight projectile arc
        if (state == State.FLIGHT || state == State.SCORE_PAUSE) {
            float prog = state == State.SCORE_PAUSE ? 1f : Math.min(1f, (System.currentTimeMillis() - flightStart) / (float) FLIGHT_MS);
            int fx = fieldX + (int)(landX * prog * fieldW);
            int startFY = cy - 6 - ay;
            int endFY = groundY - 10;
            int fly = startFY + (int)((endFY - startFY) * prog) - (int)(Math.sin(prog * Math.PI) * 60);
            g.setColor(STONE_COL);
            g.fillOval(fx - 5, fly - 5, 10, 10);
        }

        // Score feedback
        if (state == State.SCORE_PAUSE) {
            String sf = lastScore >= 5 ? "DIRECT HIT! +" + lastScore : lastScore > 0 ? "Hit! +" + lastScore : "Miss!";
            g.setFont(F_MENU);
            g.setColor(lastScore > 0 ? BATTLE_GOLD : DANGER);
            drawCentered(g, sf, px, py + 50, pw);
        }

        // Power bar
        int barX = fieldX, barY = fieldY + fieldH + 5, barW = fieldW, barH = 14;
        g.setColor(new Color(30, 20, 20)); g.fillRect(barX, barY, barW, barH);
        g.setColor(new Color(40, 80, 0));  g.fillRect(barX + (int)(barW * 0.4f), barY, (int)(barW * 0.2f), barH);
        g.setColor(powerFrac > 0.4f && powerFrac < 0.6f ? new Color(80, 200, 80) : new Color(200, 80, 80));
        g.fillRect(barX, barY, (int)(barW * powerFrac), barH);
        g.setColor(TEXT_DIM); g.drawRect(barX, barY, barW, barH);

        // Angle indicator
        g.setFont(F_SMALL);
        g.setColor(IRON_GREY);
        g.drawString("Angle: " + angle + "deg", fieldX + 5, fieldY + 15);

        g.setFont(F_ITEM);
        g.setColor(BATTLE_GOLD);
        g.drawString("Shot: " + (shotsFired + 1) + "/" + SHOTS + "  Score: " + totalScore, px + 20, py + 50);

        g.setFont(F_KEY);
        g.setColor(TEXT_DIM);
        drawCentered(g, "[Left/Right] Aim   [Enter] Fire", px, py + ph - 20, pw);
    }

    private void paintResult(Graphics2D g, int px, int py, int pw, int ph) {
        g.setFont(F_BIG);
        g.setColor(WAR_RED);
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

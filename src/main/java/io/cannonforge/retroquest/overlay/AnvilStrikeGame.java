package io.cannonforge.retroquest.overlay;

import static io.cannonforge.retroquest.overlay.ForgeArenaOverlay.FORGE_GOLD;
import static io.cannonforge.retroquest.overlay.ForgeArenaOverlay.F_BIG;
import static io.cannonforge.retroquest.overlay.ForgeArenaOverlay.F_MENU;
import static io.cannonforge.retroquest.overlay.ForgeArenaOverlay.F_TITLE;
import static io.cannonforge.retroquest.overlay.ForgeArenaOverlay.drawCentered;
import static io.cannonforge.retroquest.overlay.OverlayTheme.AMBER;
import static io.cannonforge.retroquest.overlay.OverlayTheme.DANGER;
import static io.cannonforge.retroquest.overlay.OverlayTheme.F_ITEM;
import static io.cannonforge.retroquest.overlay.OverlayTheme.F_KEY;
import static io.cannonforge.retroquest.overlay.OverlayTheme.F_SMALL;
import static io.cannonforge.retroquest.overlay.OverlayTheme.GOOD;
import static io.cannonforge.retroquest.overlay.OverlayTheme.TEXT_BRIGHT;
import static io.cannonforge.retroquest.overlay.OverlayTheme.TEXT_DIM;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.util.Random;

import io.cannonforge.retroquest.core.MessageLog;
import io.cannonforge.retroquest.core.Retroquest;
import io.cannonforge.retroquest.core.SoundManager;

/**
 * Anvil Strike mini-game: time your hammer blows on a power meter. Five strikes,
 * each faster. STR widens the perfect zone; CON slows the speed ramp.
 */
class AnvilStrikeGame implements MiniGame {

    private enum State { BET, HEAT, STRIKE, COOL, RESULT }

    // Anvil colours
    private static final Color ANVIL_DARK  = new Color( 50,  50,  55);
    private static final Color ANVIL_FACE  = new Color( 70,  70,  78);
    private static final Color ANVIL_HORN  = new Color( 60,  60,  65);
    private static final Color IRON_HOT    = new Color(255, 150,  30);
    private static final Color IRON_WARM   = new Color(200,  80,  20);
    private static final Color IRON_DIM    = new Color(120,  40,  10);
    private static final Color HAMMER_HEAD = new Color(130, 130, 140);
    private static final Color HAMMER_SHAFT = new Color( 90,  60,  25);
    private static final Color SPARK_COL   = new Color(255, 220,  80);

    private static final int TOTAL_STRIKES = 5;

    // ── Payout ladder ─────────────────────────────────────────────────────────
    // Tiers are a fraction of the maximum score; values are the multiple of the
    // stake RETURNED (1.0 = break even, 0 = stake lost). Only a flawless heat
    // (every strike perfect, ~1 in 13 for a good smith) reaches the top tier.
    private static final float TIER_TOP   = 1.00f, PAY_TOP   = 2.5f;
    private static final float TIER_GREAT = 0.88f, PAY_GREAT = 1.3f;
    private static final float TIER_GOOD  = 0.65f, PAY_GOOD  = 0.8f;
    private static final float TIER_WEAK  = 0.45f, PAY_WEAK  = 0.35f;
    private static final int METER_H       = 200;
    private static final int METER_W       =  30;
    private static final long HEAT_MS      = 1500;
    private static final long COOL_MS      =  600;

    private final Retroquest game;
    private final ForgeArenaOverlay overlay;
    private final Random rng;

    private State state = State.BET;
    private int strike = 0;
    private int[] scores = new int[TOTAL_STRIKES];
    private long phaseStartTime = 0;
    private float baseSpeed = 2.0f;
    private String resultMessage = "";

    // Spark particles
    private float[][] sparks = new float[12][4]; // x, y, vx, vy
    private boolean sparksActive = false;

    AnvilStrikeGame(Retroquest game, ForgeArenaOverlay overlay, Random rng) {
        this.game = game;
        this.overlay = overlay;
        this.rng = rng;
    }

    @Override
    public void reset(int betAmount) {
        state = State.BET;
        strike = 0;
        resultMessage = "";
        sparksActive = false;
        for (int i = 0; i < TOTAL_STRIKES; i++) scores[i] = 0;
    }

    @Override
    public boolean isShowingResult() { return state == State.RESULT; }

    @Override
    public void handleKey(KeyEvent e) {
        switch (state) {
            case BET    -> handleBetKey(e);
            case HEAT   -> {} // auto-advances
            case STRIKE -> handleStrikeKey(e);
            case COOL   -> {} // auto-advances
            case RESULT -> handleResultKey(e);
        }
    }

    private void handleBetKey(KeyEvent e) {
        int code = e.getKeyCode();
        boolean shift = e.isShiftDown();
        switch (code) {
            case KeyEvent.VK_LEFT  -> overlay.adjustBet(shift ? -10 : -1);
            case KeyEvent.VK_RIGHT -> overlay.adjustBet(shift ?  10 :  1);
            case KeyEvent.VK_ENTER -> {
                if (game.getPlayer().getGold() < overlay.betAmount) {
                    game.log("Not enough gold!", MessageLog.Type.DANGER);
                    return;
                }
                game.getPlayer().addGold(-overlay.betAmount);
                overlay.lastBet = overlay.betAmount;
                SoundManager.getInstance().play("coin");
                startHeat();
            }
            case KeyEvent.VK_ESCAPE -> { overlay.backToMenu(); SoundManager.getInstance().play("menublip"); }
        }
    }

    private void handleStrikeKey(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_ENTER || e.getKeyCode() == KeyEvent.VK_SPACE) {
            // Read current meter position
            long elapsed = System.currentTimeMillis() - phaseStartTime;
            float conMod = 0.4f - (game.getPlayer().getCon() - 10) * 0.02f;
            conMod = Math.max(0.15f, Math.min(0.6f, conMod));
            float speed = baseSpeed + strike * conMod;
            double t = elapsed * speed * 0.001;

            // Triangle wave: 0→1→0→1... (peak at top)
            double wave = Math.abs(2.0 * (t - Math.floor(t + 0.5)));
            double pos = wave; // 0=bottom, 1=top

            // STR widens perfect zone
            int strBonus = game.getPlayer().getStr() - 10;
            // Narrow band: STR helps, but never enough to make PERFECT automatic.
            double perfectZone = 0.06 + strBonus * 0.008;
            perfectZone = Math.max(0.04, Math.min(0.12, perfectZone));

            int score;
            if (pos >= 1.0 - perfectZone)  score = 30;  // perfect (near top)
            else if (pos >= 0.7)           score = 20;  // good
            else if (pos >= 0.4)           score = 10;  // fair
            else                           score = 0;   // miss (bottom half)

            scores[strike] = score;
            SoundManager.getInstance().play("anvil");
            spawnSparks(score);
            phaseStartTime = System.currentTimeMillis();
            state = State.COOL;
        } else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
            resolveGame();
        }
    }

    private void handleResultKey(KeyEvent e) {
        overlay.backToMenu();
        if (game.getStatsPanel() != null) game.getStatsPanel().refresh();
    }

    // ── Logic ─────────────────────────────────────────────────────────────────

    private void startHeat() {
        phaseStartTime = System.currentTimeMillis();
        sparksActive = false;
        state = State.HEAT;
    }

    private void startStrike() {
        phaseStartTime = System.currentTimeMillis();
        state = State.STRIKE;
    }

    private void spawnSparks(int score) {
        sparksActive = true;
        int count = score >= 30 ? 12 : score >= 20 ? 8 : score >= 10 ? 4 : 0;
        for (int i = 0; i < sparks.length; i++) {
            if (i < count) {
                sparks[i][0] = 0; // relative x
                sparks[i][1] = 0; // relative y
                sparks[i][2] = (rng.nextFloat() - 0.5f) * 6;  // vx
                sparks[i][3] = -rng.nextFloat() * 5 - 1;       // vy (upward)
            } else {
                sparks[i][0] = -1000; // off-screen
            }
        }
    }

    private void resolveGame() {
        int total = 0;
        for (int s : scores) total += s;
        int maxPossible = 30 * TOTAL_STRIKES;

        int payout = 0;
        if      (total >= maxPossible * TIER_TOP)   payout = (int)(overlay.betAmount * PAY_TOP);
        else if (total >= maxPossible * TIER_GREAT) payout = (int)(overlay.betAmount * PAY_GREAT);
        else if (total >= maxPossible * TIER_GOOD)  payout = (int)(overlay.betAmount * PAY_GOOD);
        else if (total >= maxPossible * TIER_WEAK)  payout = (int)(overlay.betAmount * PAY_WEAK);

        if (payout > 0) {
            game.getPlayer().addGold(payout);
            if (total >= maxPossible) {
                resultMessage = "MASTER SMITH! Won " + payout + "g!";
                SoundManager.getInstance().play("victory");
            } else {
                resultMessage = "You won " + payout + "g!";
                SoundManager.getInstance().play("coin");
            }
            game.log(resultMessage, MessageLog.Type.LOOT);
        } else {
            resultMessage = "Poor craftsmanship! You lost " + overlay.betAmount + "g.";
            SoundManager.getInstance().play("hurt");
            game.log(resultMessage, MessageLog.Type.DANGER);
        }
        game.getPlayer().recordGamblingResult(payout - overlay.betAmount);
        state = State.RESULT;
    }

    @Override
    public void update() {
        if (state == State.HEAT && System.currentTimeMillis() - phaseStartTime > HEAT_MS) {
            startStrike();
        }
        if (state == State.COOL && System.currentTimeMillis() - phaseStartTime > COOL_MS) {
            strike++;
            if (strike >= TOTAL_STRIKES) resolveGame();
            else startHeat();
        }
        // Update spark positions
        if (sparksActive) {
            for (float[] s : sparks) {
                s[0] += s[2];
                s[1] += s[3];
                s[3] += 0.2f; // gravity
            }
        }
    }

    @Override
    public void paint(Graphics2D g, int px, int py, int pw, int ph) {
        g.setFont(F_TITLE);
        g.setColor(FORGE_GOLD);
        drawCentered(g, "ANVIL STRIKE", px, py + 30, pw);

        switch (state) {
            case BET           -> paintBet(g, px, py, pw, ph);
            case HEAT          -> paintHeat(g, px, py, pw, ph);
            case STRIKE        -> paintStrike(g, px, py, pw, ph);
            case COOL          -> paintCool(g, px, py, pw, ph);
            case RESULT        -> paintResult(g, px, py, pw, ph);
        }
    }

    private void paintBet(Graphics2D g, int px, int py, int pw, int ph) {
        g.setFont(F_MENU);
        g.setColor(TEXT_BRIGHT);
        drawCentered(g, "Five strikes — hit at the peak!", px, py + 70, pw);

        g.setFont(F_BIG);
        g.setColor(FORGE_GOLD);
        drawCentered(g, "Bet: " + overlay.betAmount + "g", px, py + ph / 2, pw);

        g.setFont(F_ITEM);
        g.setColor(AMBER);
        drawCentered(g, "Gold: " + game.getPlayer().getGold() + "g", px, py + ph / 2 + 30, pw);

        g.setFont(F_SMALL);
        g.setColor(TEXT_DIM);
        drawCentered(g, "STR " + game.getPlayer().getStr() + " (precision)   CON " + game.getPlayer().getCon() + " (stamina)", px, py + ph / 2 + 60, pw);

        g.setFont(F_KEY);
        g.setColor(TEXT_DIM);
        g.drawString("[Left/Right] Bet   [Shift] x10   [Enter] Begin!   [Esc] Back", px + 20, py + ph - 20);
    }

    private void paintHeat(Graphics2D g, int px, int py, int pw, int ph) {
        paintScoreStrip(g, px + 20, py + 55);
        paintAnvilScene(g, px, py, pw, ph, -1);

        // Temperature bar rising
        long elapsed = System.currentTimeMillis() - phaseStartTime;
        float heatProgress = Math.min(1.0f, elapsed / (float)HEAT_MS);

        int barX = px + pw - 80;
        int barY = py + 100;
        g.setColor(new Color(30, 15, 10));
        g.fillRect(barX, barY, METER_W, METER_H);

        // Fill from bottom
        int fillH = (int)(METER_H * heatProgress);
        Color heatCol = heatProgress > 0.7f ? IRON_HOT : heatProgress > 0.4f ? IRON_WARM : IRON_DIM;
        g.setColor(heatCol);
        g.fillRect(barX, barY + METER_H - fillH, METER_W, fillH);

        g.setFont(F_SMALL);
        g.setColor(TEXT_DIM);
        drawCentered(g, "Heating...", px, py + ph - 20, pw);
    }

    private void paintStrike(Graphics2D g, int px, int py, int pw, int ph) {
        paintScoreStrip(g, px + 20, py + 55);
        paintAnvilScene(g, px, py, pw, ph, -1);

        // Power meter with oscillating indicator
        int barX = px + pw - 80;
        int barY = py + 100;
        paintPowerMeter(g, barX, barY);

        // Current position indicator
        long elapsed = System.currentTimeMillis() - phaseStartTime;
        float conMod = 0.4f - (game.getPlayer().getCon() - 10) * 0.02f;
        conMod = Math.max(0.15f, Math.min(0.6f, conMod));
        float speed = baseSpeed + strike * conMod;
        double t = elapsed * speed * 0.001;
        double wave = Math.abs(2.0 * (t - Math.floor(t + 0.5)));

        int indicatorY = barY + METER_H - (int)(wave * METER_H);
        g.setColor(Color.WHITE);
        g.fillRect(barX - 5, indicatorY - 2, METER_W + 10, 4);

        g.setFont(F_KEY);
        g.setColor(TEXT_DIM);
        drawCentered(g, "[Enter/Space] Strike!  (Strike " + (strike + 1) + "/" + TOTAL_STRIKES + ")", px, py + ph - 20, pw);
    }

    private void paintCool(Graphics2D g, int px, int py, int pw, int ph) {
        paintScoreStrip(g, px + 20, py + 55);
        paintAnvilScene(g, px, py, pw, ph, scores[strike]);

        // Sparks
        if (sparksActive) {
            int sparkBaseX = px + pw / 2;
            int sparkBaseY = py + 280;
            g.setColor(SPARK_COL);
            for (float[] s : sparks) {
                int sx = sparkBaseX + (int)s[0];
                int sy = sparkBaseY + (int)s[1];
                if (sy > py && sy < py + ph) {
                    g.fillRect(sx, sy, 2, 2);
                }
            }
        }

        // Score feedback
        g.setFont(F_BIG);
        String label = scores[strike] >= 30 ? "PERFECT!" : scores[strike] >= 20 ? "Good hit!" :
                       scores[strike] >= 10 ? "Fair" : "Miss!";
        g.setColor(scores[strike] >= 20 ? FORGE_GOLD : scores[strike] >= 10 ? AMBER : DANGER);
        drawCentered(g, label + " (+" + scores[strike] + ")", px, py + ph - 50, pw);
    }

    private void paintResult(Graphics2D g, int px, int py, int pw, int ph) {
        paintScoreStrip(g, px + 20, py + 80);

        int total = 0;
        for (int s : scores) total += s;

        // Forged item description (cosmetic)
        g.setFont(F_ITEM);
        g.setColor(TEXT_DIM);
        String quality = total >= 150 ? "Masterwork blade" : total >= 120 ? "Fine sword" :
                         total >= 90 ? "Decent dagger" : total >= 50 ? "Bent horseshoe" : "Slag heap";
        drawCentered(g, "You forged: " + quality, px, py + 130, pw);

        g.setFont(F_BIG);
        g.setColor(FORGE_GOLD);
        drawCentered(g, "Total: " + total + " pts", px, py + 180, pw);

        g.setFont(F_MENU);
        g.setColor(resultMessage.contains("won") || resultMessage.contains("MASTER") ? GOOD : DANGER);
        drawCentered(g, resultMessage, px, py + 220, pw);

        g.setFont(F_KEY);
        g.setColor(TEXT_DIM);
        drawCentered(g, "[Any key] Continue", px, py + ph - 20, pw);
    }

    // ── Drawing helpers ───────────────────────────────────────────────────────

    private void paintAnvilScene(Graphics2D g, int px, int py, int pw, int ph, int hitScore) {
        int cx = px + pw / 2 - 40; // offset left to leave room for meter
        int anvilY = py + 300;

        // Anvil body
        g.setColor(ANVIL_DARK);
        g.fillRect(cx - 30, anvilY, 60, 20);       // base
        g.setColor(ANVIL_FACE);
        g.fillRect(cx - 40, anvilY - 15, 80, 15);  // face
        g.setColor(ANVIL_HORN);
        g.fillRect(cx + 40, anvilY - 12, 20, 8);   // horn

        // Hot iron on anvil
        Color ironCol = (state == State.STRIKE || state == State.COOL) ? IRON_HOT : IRON_WARM;
        g.setColor(ironCol);
        g.fillRect(cx - 15, anvilY - 20, 30, 5);

        // Hammer (above anvil, descending during COOL)
        int hammerY = anvilY - 80;
        if (state == State.COOL) {
            long elapsed = System.currentTimeMillis() - phaseStartTime;
            float progress = Math.min(1.0f, elapsed / 200.0f);
            hammerY = anvilY - 80 + (int)(progress * 55);
        }
        // Shaft
        g.setColor(HAMMER_SHAFT);
        g.fillRect(cx - 2, hammerY - 30, 4, 30);
        // Head
        g.setColor(HAMMER_HEAD);
        g.fillRect(cx - 10, hammerY - 35, 20, 10);
    }

    private void paintPowerMeter(Graphics2D g, int barX, int barY) {
        // Background
        g.setColor(new Color(30, 15, 10));
        g.fillRect(barX, barY, METER_W, METER_H);

        // STR affects perfect zone size
        int strBonus = game.getPlayer().getStr() - 10;
        double perfectZone = 0.08 + strBonus * 0.015;
        perfectZone = Math.max(0.05, Math.min(0.25, perfectZone));

        // Zone bands (from bottom to top: miss, fair, good, perfect)
        int missH = (int)(METER_H * 0.4);
        int fairH = (int)(METER_H * 0.3);
        int goodH = (int)(METER_H * (0.3 - perfectZone));
        int perfH = (int)(METER_H * perfectZone);

        g.setColor(new Color(60, 20, 15));  // miss zone
        g.fillRect(barX, barY + METER_H - missH, METER_W, missH);
        g.setColor(IRON_DIM);               // fair zone
        g.fillRect(barX, barY + METER_H - missH - fairH, METER_W, fairH);
        g.setColor(IRON_WARM);              // good zone
        g.fillRect(barX, barY + perfH, METER_W, goodH);
        g.setColor(IRON_HOT);               // perfect zone (top)
        g.fillRect(barX, barY, METER_W, perfH);

        // Labels
        g.setFont(F_SMALL);
        g.setColor(TEXT_DIM);
        g.drawString("30", barX + METER_W + 4, barY + perfH / 2 + 4);
        g.drawString("20", barX + METER_W + 4, barY + perfH + goodH / 2 + 4);
        g.drawString("10", barX + METER_W + 4, barY + METER_H - missH - fairH / 2 + 4);
        g.drawString("0",  barX + METER_W + 4, barY + METER_H - missH / 2 + 4);
    }

    private void paintScoreStrip(Graphics2D g, int x, int y) {
        g.setFont(F_SMALL);
        int segW = 80;
        for (int i = 0; i < TOTAL_STRIKES; i++) {
            boolean done = (state == State.COOL || state == State.RESULT) ? i <= strike : i < strike;
            g.setColor(done ? (scores[i] >= 30 ? GOOD : scores[i] >= 15 ? AMBER : DANGER) : TEXT_DIM);
            g.drawString((i + 1) + ":" + (done ? scores[i] : "-"), x + i * segW, y);
        }
    }
}

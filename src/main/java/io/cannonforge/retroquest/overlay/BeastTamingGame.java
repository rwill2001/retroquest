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
 * Beast Taming: a reaction mini-game. Each of 5 rounds the beast settles
 * through AGITATED (amber, hands off) into a short CALM window (green, press
 * Enter to tame) and then REARING (red, too late). The settling time is
 * jittered, so the CALM window has to be watched for rather than timed.
 * Pressing while AGITATED or REARING loses the round; WIS widens CALM.
 */
class BeastTamingGame implements MiniGame {

    private enum State { BET, WATCH, BETWEEN, RESULT }
    private enum Mood  { CALM, AGITATED, REARING }

    private static final Color CALM_AURA  = new Color( 80, 200,  80, 160);
    private static final Color AGIT_AURA  = new Color(220, 180,  50, 160);
    private static final Color REAR_AURA  = new Color(220,  60,  50, 160);
    private static final Color BEAST_COL  = new Color(100,  70,  40);
    private static final Color BEAST_EYE  = new Color(255, 200,  50);
    private static final Color GRASS_COL  = new Color( 50,  90,  30);
    private static final Color SKY_COL    = new Color( 90, 130, 180);

    private static final int  ROUNDS       = 5;
    private static final long BASE_CALM_MS = 1100;  // the window you must react inside
    private static final long BASE_AGIT_MS = 1600;  // nominal settling time before CALM
    private static final long BASE_REAR_MS = 700;
    private static final long BETWEEN_MS   = 1000;
    /** The settling phase is jittered by +/- this fraction so CALM can never be timed blind. */
    private static final float AGIT_JITTER = 0.6f;

    // ── Payout ladder ─────────────────────────────────────────────────────────
    // Multiple of the stake RETURNED (1.0 = break even, 0 = stake lost).
    private static final float PAY_5_TAMED = 2.5f;
    private static final float PAY_4_TAMED = 1.2f;
    private static final float PAY_3_TAMED = 0.5f;

    private final Retroquest game;
    private final NatureGamesOverlay overlay;
    private final Random rng;

    private State state      = State.BET;
    private Mood  mood       = Mood.CALM;
    private int   round      = 0;
    private int   tamed      = 0;
    private long  moodStart  = 0;
    private long  agitMs     = 0;   // jittered settling time for the current round
    private long  betweenStart = 0;
    private float beastX     = 0.5f;
    private float beastDir   = 1f;
    private String feedbackMsg = "";
    private boolean lastTamed = false;
    private String resultMessage = "";

    BeastTamingGame(Retroquest game, NatureGamesOverlay overlay, Random rng) {
        this.game    = game;
        this.overlay = overlay;
        this.rng     = rng;
    }

    @Override public boolean isShowingResult() { return state == State.RESULT; }

    @Override
    public void reset(int betAmount) {
        state = State.BET;
        round = 0;
        tamed = 0;
        resultMessage = "";
        feedbackMsg = "";
    }

    @Override
    public void handleKey(KeyEvent e) {
        switch (state) {
            case BET    -> handleBetKey(e);
            case WATCH  -> handleWatchKey(e);
            case RESULT -> handleResultKey(e);
            default     -> {}
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
                startRound();
            }
            case KeyEvent.VK_ESCAPE -> { overlay.backToMenu(); SoundManager.getInstance().play("menublip"); }
        }
    }

    private void handleWatchKey(KeyEvent e) {
        if (e.getKeyCode() != KeyEvent.VK_ENTER) return;
        switch (mood) {
            case CALM -> {
                tamed++;
                lastTamed = true;
                feedbackMsg = "Tamed!";
                SoundManager.getInstance().play("coin");
                endRound();
            }
            case AGITATED -> {
                // Grabbing at an agitated beast spooks it — mashing Enter now loses the round.
                lastTamed = false;
                feedbackMsg = "Too soon — you spooked it!";
                SoundManager.getInstance().play("hurt");
                endRound();
            }
            case REARING -> {
                lastTamed = false;
                feedbackMsg = "The beast kicked you!";
                SoundManager.getInstance().play("hurt");
                endRound();
            }
        }
    }

    private void handleResultKey(KeyEvent e) {
        overlay.backToMenu();
        if (game.getStatsPanel() != null) game.getStatsPanel().refresh();
    }

    private void startRound() {
        round++;
        beastX = 0.2f + rng.nextFloat() * 0.5f;
        beastDir = rng.nextBoolean() ? 1f : -1f;
        // Every round opens AGITATED for a jittered stretch, so CALM has to be watched for.
        mood = Mood.AGITATED;
        float roundMult = Math.max(0.4f, 1f - (round - 1) * 0.08f);
        float jitter = 1f + (rng.nextFloat() * 2f - 1f) * AGIT_JITTER;
        agitMs = Math.max(400, (long)(BASE_AGIT_MS * roundMult * jitter));
        moodStart = System.currentTimeMillis();
        feedbackMsg = "";
        lastTamed = false;
        state = State.WATCH;
    }

    private void endRound() {
        betweenStart = System.currentTimeMillis();
        state = State.BETWEEN;
    }

    @Override
    public void update() {
        long now = System.currentTimeMillis();

        if (state == State.WATCH) {
            // Move beast left/right
            float speed = 0.002f + round * 0.0008f;
            beastX += beastDir * speed;
            if (beastX > 0.85f) { beastX = 0.85f; beastDir = -1f; }
            if (beastX < 0.10f) { beastX = 0.10f; beastDir =  1f; }

            // Mood timing (shrinks each round)
            int wis = game.getPlayer().getWisdom();
            float mult = 1f - (round - 1) * 0.08f;
            long calmMs = Math.max(450, (long)((BASE_CALM_MS + Math.max(0, wis - 10) * 120L) * mult));
            long rearMs = Math.max(350, (long)(BASE_REAR_MS * mult));

            long elapsed = now - moodStart;
            switch (mood) {
                // AGITATED -> CALM -> REARING. Miss the CALM window and the beast rears.
                case AGITATED -> {
                    if (elapsed >= agitMs) {
                        mood = Mood.CALM;
                        moodStart = now;
                    }
                }
                case CALM -> {
                    if (elapsed >= calmMs) {
                        mood = Mood.REARING;
                        moodStart = now;
                    }
                }
                case REARING -> {
                    if (elapsed >= rearMs) {
                        // Auto-end: player missed the window
                        lastTamed = false;
                        feedbackMsg = "The beast bolted!";
                        endRound();
                    }
                }
            }
        } else if (state == State.BETWEEN) {
            if (now - betweenStart >= BETWEEN_MS) {
                if (round >= ROUNDS) {
                    resolveGame();
                } else {
                    startRound();
                }
            }
        }
    }

    private void resolveGame() {
        int payout;
        if      (tamed >= 5) payout = (int)(overlay.betAmount * PAY_5_TAMED);
        else if (tamed >= 4) payout = (int)(overlay.betAmount * PAY_4_TAMED);
        else if (tamed >= 3) payout = (int)(overlay.betAmount * PAY_3_TAMED);
        else                 payout = 0;

        if (payout > 0) {
            game.getPlayer().addGold(payout);
            resultMessage = tamed >= 5 ? "MASTER TAMER! Won " + payout + "g!"
                                       : tamed + " beasts tamed! Won " + payout + "g!";
            SoundManager.getInstance().play(tamed >= 5 ? "victory" : "coin");
            game.log(resultMessage, MessageLog.Type.LOOT);
        } else {
            resultMessage = "Not enough tamed. Lost " + overlay.betAmount + "g.";
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
        drawCentered(g, "BEAST TAMING", px, py + 30, pw);

        switch (state) {
            case BET     -> paintBet(g, px, py, pw, ph);
            case WATCH, BETWEEN -> paintWatch(g, px, py, pw, ph);
            case RESULT  -> paintResult(g, px, py, pw, ph);
        }
    }

    private void paintBet(Graphics2D g, int px, int py, int pw, int ph) {
        g.setFont(F_MENU);
        g.setColor(TEXT_BRIGHT);
        drawCentered(g, "Press Enter only when the beast is CALM (green).", px, py + 70, pw);
        drawCentered(g, "Press while AGITATED (amber) or REARING (red) and the round is lost!", px, py + 90, pw);

        g.setFont(F_BIG);
        g.setColor(EARTH_AMBER);
        drawCentered(g, "Bet: " + overlay.betAmount + "g", px, py + ph / 2, pw);

        g.setFont(F_ITEM);
        g.setColor(AMBER);
        drawCentered(g, "Gold: " + game.getPlayer().getGold() + "g", px, py + ph / 2 + 30, pw);

        g.setFont(F_SMALL);
        g.setColor(TEXT_DIM);
        int wis = game.getPlayer().getWisdom();
        drawCentered(g, "WIS " + wis + " (extends calm phase)", px, py + ph / 2 + 60, pw);
        drawCentered(g, "5/5 = 2.5x   4/5 = 1.2x   3/5 = 0.5x   2/5 or less = lost", px, py + ph / 2 + 78, pw);

        g.setFont(F_KEY);
        g.setColor(TEXT_DIM);
        g.drawString("[Left/Right] Bet   [Shift] x10   [Enter] Start   [Esc] Back", px + 20, py + ph - 20);
    }

    private void paintWatch(Graphics2D g, int px, int py, int pw, int ph) {
        int fieldX = px + 20;
        int fieldW = pw - 40;
        int fieldY = py + 55;
        int fieldH = ph - 110;
        int groundY = fieldY + (int)(fieldH * 0.68f);

        // Sky + ground
        g.setColor(SKY_COL);
        g.fillRect(fieldX, fieldY, fieldW, groundY - fieldY);
        g.setColor(GRASS_COL);
        g.fillRect(fieldX, groundY, fieldW, fieldH - (groundY - fieldY));

        // Beast
        int bx = fieldX + (int)(beastX * fieldW);
        int by = groundY - 4;

        // Aura
        Color aura = switch (mood) {
            case CALM     -> CALM_AURA;
            case AGITATED -> AGIT_AURA;
            case REARING  -> REAR_AURA;
        };
        g.setColor(aura);
        g.fillOval(bx - 38, by - 44, 76, 56);

        // Beast body
        g.setColor(BEAST_COL);
        boolean rear = mood == Mood.REARING;
        if (rear) {
            // Rearing: body tilted up
            g.fillRoundRect(bx - 12, by - 50, 24, 46, 10, 10);
            g.fillRoundRect(bx - 18, by - 54, 20, 16, 8, 8); // head
            // Back legs planted
            g.fillRect(bx - 14, by - 4, 6, 14);
            g.fillRect(bx +  6, by - 4, 6, 14);
        } else {
            long leg = System.currentTimeMillis() / 120 % 2;
            g.fillRoundRect(bx - 25, by - 22, 50, 22, 10, 10);
            g.fillRoundRect(bx + 18, by - 28, 20, 18, 8, 8);
            g.fillRect(bx - 20, by, 6, 12 + (int)(leg * 3));
            g.fillRect(bx -  6, by, 6, 12 - (int)(leg * 3));
            g.fillRect(bx +  8, by, 6, 12 + (int)(leg * 3));
            g.fillRect(bx + 18, by, 6, 12 - (int)(leg * 3));
        }
        g.setColor(BEAST_EYE);
        g.fillOval(rear ? bx - 14 : bx + 32, rear ? by - 50 : by - 24, 4, 4);

        // Field border
        g.setColor(FOREST_GREEN);
        g.drawRect(fieldX, fieldY, fieldW, fieldH);

        // Mood label
        String moodLabel = switch (mood) {
            case CALM     -> "CALM — press Enter!";
            case AGITATED -> "AGITATED — wait...";
            case REARING  -> "REARING — stay back!";
        };
        Color moodCol = switch (mood) {
            case CALM     -> new Color(80, 200, 80);
            case AGITATED -> new Color(220, 180, 50);
            case REARING  -> new Color(220, 60, 50);
        };
        g.setFont(F_MENU);
        g.setColor(moodCol);
        drawCentered(g, moodLabel, px, py + 50, pw);

        // Feedback
        if (!feedbackMsg.isEmpty()) {
            g.setFont(F_ITEM);
            g.setColor(feedbackMsg.contains("Tamed") ? LEAF_BRIGHT : DANGER);
            drawCentered(g, feedbackMsg, px, py + ph - 45, pw);
        }

        g.setFont(F_ITEM);
        g.setColor(EARTH_AMBER);
        g.drawString("Round: " + round + "/" + ROUNDS + "  Tamed: " + tamed, px + 20, py + 50);

        g.setFont(F_KEY);
        g.setColor(TEXT_DIM);
        drawCentered(g, "[Enter] Act", px, py + ph - 20, pw);
    }

    private void paintResult(Graphics2D g, int px, int py, int pw, int ph) {
        g.setFont(F_BIG);
        g.setColor(FOREST_GREEN);
        drawCentered(g, "Tamed: " + tamed + " / " + ROUNDS, px, py + 130, pw);

        g.setFont(F_MENU);
        boolean won = resultMessage.contains("Won") || resultMessage.contains("MASTER");
        g.setColor(won ? GOOD : DANGER);
        drawCentered(g, resultMessage, px, py + 175, pw);

        g.setFont(F_KEY);
        g.setColor(TEXT_DIM);
        drawCentered(g, "[Any key] Continue", px, py + ph - 20, pw);
    }
}

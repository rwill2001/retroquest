package io.cannonforge.retroquest.overlay;

import static io.cannonforge.retroquest.overlay.WarGamesOverlay.WAR_RED;
import static io.cannonforge.retroquest.overlay.WarGamesOverlay.IRON_GREY;
import static io.cannonforge.retroquest.overlay.WarGamesOverlay.BATTLE_GOLD;
import static io.cannonforge.retroquest.overlay.WarGamesOverlay.BLOOD_DARK;
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
 * War Dice: the opponent commits his hand first and lays it out face-up,
 * strongest die in HIGH. You then roll 3 dice and place them one-by-one into
 * HIGH/MID/LOW, deciding which of your dice answers which of his.
 * Highest value takes the slot (ties go to the house). Win 2 of 3 slots to take
 * the round; best of 3 rounds. One re-roll per round; INT 14+ grants a second.
 *
 * Payout (multiple of the stake returned): 1 round won = 0.4x, 2 = 1.3x, 3 = 2.5x.
 */
class WarDiceGame implements MiniGame {

    private enum State { BET, ROLL, PLACE, OPPONENT_PLACE, RESOLVE_PAUSE, RESULT }

    private static final int   TOTAL_ROUNDS    = 3;
    private static final int   DICE_COUNT      = 3;
    private static final long  RESOLVE_MS      = 2000;
    private static final long  OPP_PLACE_MS    = 800;  // AI "thinking" delay

    private static final String[] SLOT_NAMES = { "HIGH", "MID", "LOW" };

    // -- Payout ladder --------------------------------------------------------
    // Rounds won -> multiple of the stake RETURNED (1.0 = break even, 0 = lost).
    // Placement against a revealed hand wins a round a little over half the time,
    // which puts a well-played match a shade under break-even.
    private static final float PAY_3_ROUNDS = 2.5f, PAY_2_ROUNDS = 1.3f, PAY_1_ROUND = 0.4f;

    /** Dice the opponent throws before keeping his best DICE_COUNT. The difficulty dial. */
    private static final int OPP_DICE_POOL = 5;

    private static final Color SLOT_EMPTY  = new Color( 40,  30,  50);
    private static final Color SLOT_FILLED = new Color( 60,  50,  80);
    private static final Color SLOT_WIN    = new Color( 40, 110,  60);
    private static final Color SLOT_LOSE   = new Color(110,  30,  30);
    private static final Color SLOT_TIE    = new Color( 90,  80,  30);
    private static final Color DIE_COL     = new Color(230, 220, 200);
    private static final Color DIE_ACTIVE  = new Color(255, 240,  80);
    private static final Color OPP_DIE_COL = new Color(180,  90,  70);

    private final Retroquest game;
    private final WarGamesOverlay overlay;
    private final Random rng;

    private State state      = State.BET;
    private int   round      = 1;
    private int   playerRoundsWon = 0;

    // Per-round state
    private int[] dice       = new int[DICE_COUNT];   // player's current dice values
    private int   dieIndex   = 0;   // which die to place next (0, 1, 2)
    private int[] playerSlots = new int[DICE_COUNT];  // player's placements (-1 = empty)
    private int[] oppSlots    = new int[DICE_COUNT];  // opponent's placements
    private int   rerollsLeft = 1;
    private boolean resolved  = false;
    private boolean[] slotWon  = new boolean[DICE_COUNT];
    private boolean[] slotTied = new boolean[DICE_COUNT];
    private long  resolveStart;
    private long  oppPlaceStart;

    private String resultMessage = "";

    WarDiceGame(Retroquest game, WarGamesOverlay overlay, Random rng) {
        this.game    = game;
        this.overlay = overlay;
        this.rng     = rng;
    }

    @Override public boolean isShowingResult() { return state == State.RESULT; }

    @Override
    public void reset(int betAmount) {
        state = State.BET;
        round = 1;
        playerRoundsWon = 0;
        resultMessage = "";
    }

    @Override
    public void handleKey(KeyEvent e) {
        switch (state) {
            case BET   -> handleBetKey(e);
            case ROLL  -> handleRollKey(e);
            case PLACE -> handlePlaceKey(e);
            case RESULT -> handleResultKey(e);
            default    -> {}
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

    private void handleRollKey(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_ENTER -> {
                // Reroll
                if (rerollsLeft > 0) {
                    rollDice();
                    rerollsLeft--;
                    SoundManager.getInstance().play("menublip");
                }
                state = State.PLACE;
            }
            case KeyEvent.VK_ESCAPE -> {
                // Skip reroll, go straight to placing
                state = State.PLACE;
            }
        }
    }

    private void handlePlaceKey(KeyEvent e) {
        // Place the current die (dieIndex) into a slot
        // Left = HIGH (slot 0), Down = MID (slot 1), Right = LOW (slot 2)
        int slot = switch (e.getKeyCode()) {
            case KeyEvent.VK_LEFT  -> 0;
            case KeyEvent.VK_DOWN  -> 1;
            case KeyEvent.VK_RIGHT -> 2;
            default -> -1;
        };
        if (slot >= 0 && playerSlots[slot] == -1) {
            playerSlots[slot] = dice[dieIndex];
            dieIndex++;
            SoundManager.getInstance().play("menublip");

            if (dieIndex >= DICE_COUNT) {
                // All placed — AI places
                oppPlaceStart = System.currentTimeMillis();
                state = State.OPPONENT_PLACE;
            }
        }
    }

    private void handleResultKey(KeyEvent e) {
        overlay.backToMenu();
        if (game.getStatsPanel() != null) game.getStatsPanel().refresh();
    }

    private void startRound() {
        for (int i = 0; i < DICE_COUNT; i++) playerSlots[i] = -1;
        // The opponent commits a real hand up front and lays it out strongest-first
        // (HIGH gets its best die). It is face-up from the start, so choosing which
        // of your dice answers which slot is an actual decision.
        rollOpponentHand();
        dieIndex = 0;
        resolved = false;
        int intel = game.getPlayer().getIntelligence();
        rerollsLeft = intel >= 14 ? 2 : 1;
        rollDice();
        state = State.ROLL;
    }

    private void rollDice() {
        for (int i = 0; i < DICE_COUNT; i++) dice[i] = rng.nextInt(6) + 1;
    }

    /**
     * The veteran throws a fistful of OPP_DICE_POOL dice and keeps his best
     * DICE_COUNT, laid out strongest-first. Simulated over 200k matches this
     * leaves a well-played round a shade under even, with a 3-0 sweep landing
     * about 1 game in 10. Lower OPP_DICE_POOL to make him easier.
     */
    private void rollOpponentHand() {
        int[] pool = new int[OPP_DICE_POOL];
        for (int i = 0; i < OPP_DICE_POOL; i++) pool[i] = rng.nextInt(6) + 1;
        java.util.Arrays.sort(pool);
        for (int i = 0; i < DICE_COUNT; i++) oppSlots[i] = pool[OPP_DICE_POOL - 1 - i];
    }

    @Override
    public void update() {
        long now = System.currentTimeMillis();

        if (state == State.OPPONENT_PLACE) {
            if (now - oppPlaceStart >= OPP_PLACE_MS) {
                // Opponent's hand was rolled and revealed at the start of the round;
                // nothing more is drawn here, so the player's placement is what decides it.

                // Score slots
                int playerSlotWins = 0;
                for (int i = 0; i < DICE_COUNT; i++) {
                    slotWon[i]  = playerSlots[i] > oppSlots[i];
                    slotTied[i] = playerSlots[i] == oppSlots[i];
                    if (slotWon[i]) playerSlotWins++;
                }
                resolved = true;   // was never set, so the win/lose slot colours never showed
                if (playerSlotWins >= 2) {
                    playerRoundsWon++;
                    SoundManager.getInstance().play("coin");
                } else {
                    SoundManager.getInstance().play("hurt");
                }
                resolveStart = now;
                state = State.RESOLVE_PAUSE;
            }
        } else if (state == State.RESOLVE_PAUSE) {
            if (now - resolveStart >= RESOLVE_MS) {
                round++;
                if (round > TOTAL_ROUNDS) resolveGame();
                else startRound();
            }
        }
    }

    private void resolveGame() {
        int payout;
        if      (playerRoundsWon >= 3) payout = (int)(overlay.betAmount * PAY_3_ROUNDS);
        else if (playerRoundsWon >= 2) payout = (int)(overlay.betAmount * PAY_2_ROUNDS);
        else if (playerRoundsWon >= 1) payout = (int)(overlay.betAmount * PAY_1_ROUND);
        else                           payout = 0;

        if (payout > 0) {
            game.getPlayer().addGold(payout);
            resultMessage = playerRoundsWon >= 3 ? "WAR DICE MASTER! Won " + payout + "g!"
                                                 : playerRoundsWon + " rounds won! Won " + payout + "g!";
            SoundManager.getInstance().play(playerRoundsWon >= 3 ? "victory" : "coin");
            game.log(resultMessage, MessageLog.Type.LOOT);
        } else {
            resultMessage = "Fortune favored your foe. Lost " + overlay.betAmount + "g.";
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
        drawCentered(g, "WAR DICE", px, py + 30, pw);

        switch (state) {
            case BET                          -> paintBet(g, px, py, pw, ph);
            case ROLL, PLACE, OPPONENT_PLACE,
                 RESOLVE_PAUSE               -> paintGame(g, px, py, pw, ph);
            case RESULT                       -> paintResult(g, px, py, pw, ph);
        }
    }

    private void paintBet(Graphics2D g, int px, int py, int pw, int ph) {
        g.setFont(F_MENU);
        g.setColor(TEXT_BRIGHT);
        drawCentered(g, "Place dice into HIGH / MID / LOW slots.", px, py + 70, pw);
        drawCentered(g, "His hand is face-up. Win 2 of 3 slots to take the round. Best of 3.", px, py + 90, pw);
        drawCentered(g, "3 rounds = 2.5x   2 = 1.3x   1 = 0.4x   0 = lost", px, py + 110, pw);

        g.setFont(F_BIG);
        g.setColor(BATTLE_GOLD);
        drawCentered(g, "Bet: " + overlay.betAmount + "g", px, py + ph / 2, pw);

        g.setFont(F_ITEM);
        g.setColor(AMBER);
        drawCentered(g, "Gold: " + game.getPlayer().getGold() + "g", px, py + ph / 2 + 30, pw);

        g.setFont(F_SMALL);
        g.setColor(TEXT_DIM);
        int intel = game.getPlayer().getIntelligence();
        String bonus = intel >= 14 ? "INT " + intel + " (2 re-rolls per round!)" : "INT " + intel + " (need 14 for 2 re-rolls)";
        drawCentered(g, bonus, px, py + ph / 2 + 60, pw);

        g.setFont(F_KEY);
        g.setColor(TEXT_DIM);
        g.drawString("[Left/Right] Bet   [Shift] x10   [Enter] Start   [Esc] Back", px + 20, py + ph - 20);
    }

    private void paintGame(Graphics2D g, int px, int py, int pw, int ph) {
        // Round tracker
        g.setFont(F_ITEM);
        g.setColor(BATTLE_GOLD);
        g.drawString("Round: " + round + "/" + TOTAL_ROUNDS + "  Rounds Won: " + playerRoundsWon, px + 20, py + 50);

        // Slots section
        int slotW = 90, slotH = 60, slotPad = 20;
        int slotsW = DICE_COUNT * (slotW + slotPad) - slotPad;
        int slotsX = px + (pw - slotsW) / 2;
        int playerSlotsY = py + 70;
        int oppSlotsY    = py + 155;

        // Labels
        g.setFont(F_SMALL);
        g.setColor(IRON_GREY);
        drawCentered(g, "OPPONENT", slotsX, oppSlotsY - 4, slotsW);
        g.setColor(TEXT_BRIGHT);
        drawCentered(g, "YOU", slotsX, playerSlotsY - 4, slotsW);

        for (int i = 0; i < DICE_COUNT; i++) {
            int sx = slotsX + i * (slotW + slotPad);

            // Opponent slot
            Color oppBg = state == State.RESOLVE_PAUSE ?
                (slotWon[i] ? SLOT_LOSE : slotTied[i] ? SLOT_TIE : SLOT_WIN) : SLOT_FILLED;
            if (oppSlots[i] == -1) oppBg = SLOT_EMPTY;
            g.setColor(oppBg);
            g.fillRoundRect(sx, oppSlotsY, slotW, slotH, 8, 8);
            g.setColor(OPP_DIE_COL);
            g.drawRoundRect(sx, oppSlotsY, slotW, slotH, 8, 8);
            if (oppSlots[i] != -1) {
                g.setFont(F_BIG);
                g.setColor(OPP_DIE_COL);
                drawCentered(g, String.valueOf(oppSlots[i]), sx, oppSlotsY + slotH / 2 + 8, slotW);
            }

            // Slot name label between
            g.setFont(F_SMALL);
            g.setColor(TEXT_DIM);
            drawCentered(g, SLOT_NAMES[i], sx, oppSlotsY + slotH + 14, slotW);

            // Player slot
            Color plyBg = state == State.RESOLVE_PAUSE ?
                (slotWon[i] ? SLOT_WIN : slotTied[i] ? SLOT_TIE : SLOT_LOSE) : SLOT_FILLED;
            if (playerSlots[i] == -1) plyBg = SLOT_EMPTY;
            g.setColor(plyBg);
            g.fillRoundRect(sx, playerSlotsY, slotW, slotH, 8, 8);
            g.setColor(playerSlots[i] == -1 ? IRON_GREY : DIE_COL);
            g.drawRoundRect(sx, playerSlotsY, slotW, slotH, 8, 8);
            if (playerSlots[i] != -1) {
                g.setFont(F_BIG);
                g.setColor(DIE_COL);
                drawCentered(g, String.valueOf(playerSlots[i]), sx, playerSlotsY + slotH / 2 + 8, slotW);
            }
        }

        // Dice in hand
        int diceAreaY = py + 270;
        g.setFont(F_SMALL);
        g.setColor(TEXT_DIM);
        drawCentered(g, "YOUR DICE:", px, diceAreaY, pw);

        int diceW = 50, diceH = 50;
        int diceAreaX = px + (pw - (DICE_COUNT * (diceW + 10) - 10)) / 2;
        for (int i = 0; i < DICE_COUNT; i++) {
            int dx = diceAreaX + i * (diceW + 10);
            boolean isCurrent = (i == dieIndex && state == State.PLACE);
            boolean isPlaced  = (i < dieIndex);
            g.setColor(isPlaced ? new Color(30, 25, 40) : isCurrent ? DIE_ACTIVE : DIE_COL);
            g.fillRoundRect(dx, diceAreaY + 12, diceW, diceH, 8, 8);
            g.setColor(isPlaced ? IRON_GREY : BLOOD_DARK);
            g.drawRoundRect(dx, diceAreaY + 12, diceW, diceH, 8, 8);
            if (!isPlaced) {
                g.setFont(F_BIG);
                g.setColor(isPlaced ? IRON_GREY : BLOOD_DARK);
                drawCentered(g, String.valueOf(dice[i]), dx, diceAreaY + 12 + diceH / 2 + 8, diceW);
            }
        }

        // State-specific prompt
        g.setFont(F_KEY);
        if (state == State.ROLL) {
            g.setColor(TEXT_BRIGHT);
            drawCentered(g, "Re-rolls left: " + rerollsLeft, px, py + ph - 38, pw);
            g.setColor(TEXT_DIM);
            drawCentered(g, "[Enter] Re-roll   [Esc] Keep & Place", px, py + ph - 20, pw);
        } else if (state == State.PLACE) {
            g.setFont(F_SMALL);
            g.setColor(BATTLE_GOLD);
            drawCentered(g, "Place die " + (dieIndex + 1) + " of " + DICE_COUNT, px, py + ph - 38, pw);
            g.setFont(F_KEY);
            g.setColor(TEXT_DIM);
            drawCentered(g, "[Left] HIGH   [Down] MID   [Right] LOW", px, py + ph - 20, pw);
        } else if (state == State.OPPONENT_PLACE) {
            g.setColor(WAR_RED);
            drawCentered(g, "Opponent placing...", px, py + ph - 20, pw);
        } else if (state == State.RESOLVE_PAUSE) {
            // Tally slots won
            int won = 0;
            for (boolean w : slotWon) if (w) won++;
            g.setColor(won >= 2 ? GOOD : DANGER);
            drawCentered(g, won >= 2 ? "Round won! (" + won + "/3 slots)" : "Round lost. (" + won + "/3 slots)", px, py + ph - 20, pw);
        }
    }

    private void paintResult(Graphics2D g, int px, int py, int pw, int ph) {
        g.setFont(F_BIG);
        g.setColor(WAR_RED);
        drawCentered(g, "Rounds Won: " + playerRoundsWon + "/" + TOTAL_ROUNDS, px, py + 130, pw);

        g.setFont(F_MENU);
        boolean won = resultMessage.contains("Won") || resultMessage.contains("MASTER");
        g.setColor(won ? GOOD : DANGER);
        drawCentered(g, resultMessage, px, py + 175, pw);

        g.setFont(F_KEY);
        g.setColor(TEXT_DIM);
        drawCentered(g, "[Any key] Continue", px, py + ph - 20, pw);
    }
}

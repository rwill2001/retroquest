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
import java.util.Collections;
import java.util.List;
import java.util.Random;

import io.cannonforge.retroquest.core.MessageLog;
import io.cannonforge.retroquest.core.Retroquest;
import io.cannonforge.retroquest.core.SoundManager;

/**
 * Hollow Riddles: a timed trivia mini-game. 5 riddles from a pool of 15,
 * each with 3 multiple-choice answers and a shrinking timer bar.
 * WIS adds time per riddle. Payout scales with correct answers.
 */
class HollowRiddlesGame implements MiniGame {

    private enum State { BET, RIDDLE, ANSWER_PAUSE, RESULT }

    // Format: { question, correct_answer, wrong1, wrong2 }
    private static final String[][] RIDDLE_POOL = {
        { "I speak without a mouth. I hear without ears.\nI come alive with wind.", "Echo", "Ghost", "Shadow" },
        { "The more you take,\nthe more you leave behind.", "Footsteps", "Regrets", "Gold" },
        { "What gets darker\nthe more light you shine on it?", "A Shadow", "A Cave", "A Memory" },
        { "I am always ahead of you,\nbut can never be reached.", "The Future", "The Horizon", "The Past" },
        { "I have no life, but I can die.\nI need air but have no lungs.", "Fire", "Memory", "The Wind" },
        { "I have hands,\nbut cannot clap.", "A Clock", "A Statue", "A Glove" },
        { "You can catch me,\nbut you cannot throw me.", "A Cold", "A Dream", "A Promise" },
        { "I run but never walk.\nI have a mouth but never talk.", "A River", "A Ghost", "Time" },
        { "I can fill a room,\nyet take up no space.", "Light", "Sound", "Silence" },
        { "Dead men eat it.\nIf living eat it, they soon die.", "Nothing", "Grief", "Poison" },
        { "Lighter than a feather,\nyet no man can hold me long.", "Breath", "Smoke", "A Shadow" },
        { "The more of me there is,\nthe less you see.", "Darkness", "Fog", "Sleep" },
        { "Speak my name\nand I cease to be.", "Silence", "Memory", "An Echo" },
        { "I have cities without houses,\nforests without trees.", "A Map", "A Dream", "A Shadow" },
        { "I have a neck but no head,\nand two arms but no hands.", "A Bottle", "A River", "A Shirt" },
    };

    private static final int  RIDDLE_COUNT  = 5;
    private static final long BASE_TIME_MS  = 3500;  // per riddle

    // -- Payout ladder --------------------------------------------------------
    // Multiple of the stake RETURNED (1.0 = break even, 0 = stake lost).
    // Deliberately the flattest ladder in the casino: RIDDLE_POOL is a fixed
    // list, so anyone who has seen every riddle can answer them all. The
    // ceiling is held near break-even so memorising the pool is not a faucet.
    // Widen RIDDLE_POOL before raising PAY_5_CORRECT.
    private static final float PAY_5_CORRECT = 1.5f, PAY_4_CORRECT = 0.9f, PAY_3_CORRECT = 0.4f;
    private static final long PAUSE_MS      = 1200;

    private static final Color CHOICE_SEL   = new Color(25, 15, 50);
    private static final Color CHOICE_RIGHT = new Color(20, 60, 40);
    private static final Color CHOICE_WRONG = new Color(60, 15, 20);

    private final Retroquest game;
    private final MemoryGamesOverlay overlay;
    private final Random rng;

    private State state    = State.BET;
    private List<String[]> riddles = new ArrayList<>();
    private int   riddleIdx  = 0;
    private int   correct    = 0;
    private int   choiceSel  = 0;       // 0, 1, or 2 (left/center/right)
    private String[] choices = new String[3]; // shuffled choices for current riddle
    private int   correctIdx = 0;       // index in choices of the right answer
    private long  riddleStart = 0;
    private long  pauseStart  = 0;
    private boolean lastCorrect = false;
    private String resultMessage = "";

    HollowRiddlesGame(Retroquest game, MemoryGamesOverlay overlay, Random rng) {
        this.game    = game;
        this.overlay = overlay;
        this.rng     = rng;
    }

    @Override public boolean isShowingResult() { return state == State.RESULT; }

    @Override
    public void reset(int betAmount) {
        state = State.BET;
        correct = 0;
        riddleIdx = 0;
        resultMessage = "";

        // Pick 5 random riddles
        List<String[]> pool = new ArrayList<>(List.of(RIDDLE_POOL));
        Collections.shuffle(pool, rng);
        riddles = pool.subList(0, RIDDLE_COUNT);
    }

    @Override
    public void handleKey(KeyEvent e) {
        switch (state) {
            case BET          -> handleBetKey(e);
            case RIDDLE       -> handleRiddleKey(e);
            case ANSWER_PAUSE -> {}
            case RESULT       -> handleResultKey(e);
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
                startRiddle();
            }
            case KeyEvent.VK_ESCAPE -> { overlay.backToMenu(); SoundManager.getInstance().play("menublip"); }
        }
    }

    private void handleRiddleKey(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_LEFT  -> { choiceSel = 0; SoundManager.getInstance().play("menublip"); }
            case KeyEvent.VK_DOWN  -> { choiceSel = 1; SoundManager.getInstance().play("menublip"); }
            case KeyEvent.VK_RIGHT -> { choiceSel = 2; SoundManager.getInstance().play("menublip"); }
            case KeyEvent.VK_ENTER -> confirmAnswer();
        }
    }

    private void handleResultKey(KeyEvent e) {
        overlay.backToMenu();
        if (game.getStatsPanel() != null) game.getStatsPanel().refresh();
    }

    private void startRiddle() {
        String[] riddle = riddles.get(riddleIdx);

        // Build shuffled choices
        List<String> opts = new ArrayList<>();
        opts.add(riddle[1]); opts.add(riddle[2]); opts.add(riddle[3]);
        Collections.shuffle(opts, rng);

        choices[0] = opts.get(0); choices[1] = opts.get(1); choices[2] = opts.get(2);
        for (int i = 0; i < 3; i++) { if (choices[i].equals(riddle[1])) { correctIdx = i; break; } }

        choiceSel = 0;
        riddleStart = System.currentTimeMillis();
        state = State.RIDDLE;
    }

    private void confirmAnswer() {
        lastCorrect = (choiceSel == correctIdx);
        if (lastCorrect) {
            correct++;
            SoundManager.getInstance().play("coin");
        } else {
            SoundManager.getInstance().play("hurt");
        }
        pauseStart = System.currentTimeMillis();
        state = State.ANSWER_PAUSE;
    }

    @Override
    public void update() {
        long now = System.currentTimeMillis();

        if (state == State.RIDDLE) {
            int wis = game.getPlayer().getWisdom();
            long timeMs = BASE_TIME_MS + Math.max(0, (wis - 10)) * 200L;
            if (now - riddleStart >= timeMs) {
                // Time expired — count as wrong
                lastCorrect = false;
                SoundManager.getInstance().play("hurt");
                pauseStart = now;
                state = State.ANSWER_PAUSE;
            }
        } else if (state == State.ANSWER_PAUSE) {
            if (now - pauseStart >= PAUSE_MS) {
                riddleIdx++;
                if (riddleIdx >= RIDDLE_COUNT) {
                    resolveGame();
                } else {
                    startRiddle();
                }
            }
        }
    }

    private void resolveGame() {
        int payout;
        if      (correct >= 5) payout = (int)(overlay.betAmount * PAY_5_CORRECT);
        else if (correct >= 4) payout = (int)(overlay.betAmount * PAY_4_CORRECT);
        else if (correct >= 3) payout = (int)(overlay.betAmount * PAY_3_CORRECT);
        else                   payout = 0;

        if (payout > 0) {
            game.getPlayer().addGold(payout);
            resultMessage = correct >= 5 ? "RIDDLEMASTER! Won " + payout + "g!"
                                         : correct + " correct! Won " + payout + "g!";
            SoundManager.getInstance().play(correct >= 5 ? "victory" : "coin");
            game.log(resultMessage, MessageLog.Type.LOOT);
        } else {
            resultMessage = "The riddles stumped you. Lost " + overlay.betAmount + "g.";
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
        drawCentered(g, "HOLLOW RIDDLES", px, py + 30, pw);

        switch (state) {
            case BET          -> paintBet(g, px, py, pw, ph);
            case RIDDLE       -> paintRiddle(g, px, py, pw, ph);
            case ANSWER_PAUSE -> paintAnswerPause(g, px, py, pw, ph);
            case RESULT       -> paintResult(g, px, py, pw, ph);
        }
    }

    private void paintBet(Graphics2D g, int px, int py, int pw, int ph) {
        g.setFont(F_MENU);
        g.setColor(TEXT_BRIGHT);
        drawCentered(g, "Answer 5 riddles before the timer runs out.", px, py + 70, pw);
        drawCentered(g, "Left / Down / Right to pick. Enter to confirm.", px, py + 90, pw);

        g.setFont(F_BIG);
        g.setColor(SHADOW_PURPLE);
        drawCentered(g, "Bet: " + overlay.betAmount + "g", px, py + ph / 2, pw);

        g.setFont(F_ITEM);
        g.setColor(AMBER);
        drawCentered(g, "Gold: " + game.getPlayer().getGold() + "g", px, py + ph / 2 + 30, pw);

        g.setFont(F_SMALL);
        g.setColor(TEXT_DIM);
        drawCentered(g, "WIS " + game.getPlayer().getWisdom() + " (more time per riddle)", px, py + ph / 2 + 60, pw);

        g.setFont(F_KEY);
        g.setColor(TEXT_DIM);
        g.drawString("[Left/Right] Bet   [Shift] x10   [Enter] Start   [Esc] Back", px + 20, py + ph - 20);
    }

    private void paintRiddle(Graphics2D g, int px, int py, int pw, int ph) {
        // Timer bar
        int wis = game.getPlayer().getWisdom();
        long timeMs = BASE_TIME_MS + Math.max(0, (wis - 10)) * 200L;
        float timerFrac = 1f - Math.min(1f, (System.currentTimeMillis() - riddleStart) / (float) timeMs);
        int barX = px + 30; int barW = pw - 60;
        g.setColor(new Color(30, 20, 50));
        g.fillRect(barX, py + 45, barW, 8);
        g.setColor(timerFrac > 0.35f ? SHADOW_PURPLE : DANGER);
        g.fillRect(barX, py + 45, (int)(barW * timerFrac), 8);

        // Riddle number
        g.setFont(F_SMALL);
        g.setColor(GRIEF_GREY);
        drawCentered(g, "Riddle " + (riddleIdx + 1) + " of " + RIDDLE_COUNT, px, py + 65, pw);

        // Question (word-wrapped, max 2 lines shown)
        String[] lines = riddles.get(riddleIdx)[0].split("\n");
        int qy = py + 100;
        for (String line : lines) {
            g.setFont(F_MENU);
            g.setColor(GHOST_WHITE);
            drawCentered(g, line, px, qy, pw);
            qy += 24;
        }

        // Choices: Left=choices[0], Down=choices[1], Right=choices[2]
        String[] labels = { "[Left] " + choices[0], "[Down] " + choices[1], "[Right] " + choices[2] };
        int choiceY = py + 185;
        for (int i = 0; i < 3; i++) {
            boolean sel = i == choiceSel;
            int cw = 180, cx = px + (pw - cw) / 2;
            if (i == 0) cx = px + 30;
            else if (i == 2) cx = px + pw - 30 - cw;

            g.setColor(sel ? CHOICE_SEL : new Color(14, 10, 24));
            g.fillRoundRect(cx, choiceY, cw, 28, 6, 6);
            g.setColor(sel ? SHADOW_PURPLE : BORDER_COL);
            g.drawRoundRect(cx, choiceY, cw, 28, 6, 6);
            g.setFont(F_SMALL);
            g.setColor(sel ? GHOST_WHITE : TEXT_BRIGHT);
            int tw = g.getFontMetrics().stringWidth(labels[i]);
            g.drawString(labels[i], cx + (cw - tw) / 2, choiceY + 18);
        }

        g.setFont(F_ITEM);
        g.setColor(AMBER);
        g.drawString("Correct: " + correct + "/" + (riddleIdx), px + 20, py + 50);

        g.setFont(F_KEY);
        g.setColor(TEXT_DIM);
        drawCentered(g, "[Left/Down/Right] Choose   [Enter] Answer", px, py + ph - 20, pw);
    }

    private void paintAnswerPause(Graphics2D g, int px, int py, int pw, int ph) {
        // Show question still + highlight correct/wrong
        String[] lines = riddles.get(riddleIdx)[0].split("\n");
        int qy = py + 100;
        for (String line : lines) {
            g.setFont(F_MENU);
            g.setColor(GHOST_WHITE);
            drawCentered(g, line, px, qy, pw);
            qy += 24;
        }
        // Highlight chosen
        String[] labels = { choices[0], choices[1], choices[2] };
        int choiceY = py + 185;
        for (int i = 0; i < 3; i++) {
            boolean isCorrectChoice = (i == correctIdx);
            boolean wasChosen = (i == choiceSel);
            int cw = 180, cx = px + (pw - cw) / 2;
            if (i == 0) cx = px + 30;
            else if (i == 2) cx = px + pw - 30 - cw;

            if (isCorrectChoice)      g.setColor(CHOICE_RIGHT);
            else if (wasChosen)       g.setColor(CHOICE_WRONG);
            else                      g.setColor(new Color(14, 10, 24));
            g.fillRoundRect(cx, choiceY, cw, 28, 6, 6);
            g.setColor(isCorrectChoice ? GOOD : wasChosen ? DANGER : BORDER_COL);
            g.drawRoundRect(cx, choiceY, cw, 28, 6, 6);
            g.setFont(F_SMALL);
            g.setColor(isCorrectChoice ? GOOD : wasChosen ? DANGER : TEXT_DIM);
            int tw = g.getFontMetrics().stringWidth(labels[i]);
            g.drawString(labels[i], cx + (cw - tw) / 2, choiceY + 18);
        }
        g.setFont(F_BIG);
        g.setColor(lastCorrect ? GOOD : DANGER);
        drawCentered(g, lastCorrect ? "Correct!" : "Wrong!", px, py + 80, pw);
    }

    private void paintResult(Graphics2D g, int px, int py, int pw, int ph) {
        g.setFont(F_BIG);
        g.setColor(SHADOW_PURPLE);
        drawCentered(g, "Correct: " + correct + " / " + RIDDLE_COUNT, px, py + 130, pw);

        g.setFont(F_MENU);
        boolean won = resultMessage.contains("Won") || resultMessage.contains("MASTER");
        g.setColor(won ? GOOD : DANGER);
        drawCentered(g, resultMessage, px, py + 175, pw);

        g.setFont(F_KEY);
        g.setColor(TEXT_DIM);
        drawCentered(g, "[Any key] Continue", px, py + ph - 20, pw);
    }
}

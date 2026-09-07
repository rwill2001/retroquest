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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import io.cannonforge.retroquest.core.MessageLog;
import io.cannonforge.retroquest.core.Retroquest;
import io.cannonforge.retroquest.core.SoundManager;

/**
 * Herbalist's Brew: a memory mini-game. A recipe of ingredients is shown
 * briefly. Then pick them in the correct order from a shuffled list.
 * INT bonus extends the viewing time. 4 rounds with increasing recipe length.
 */
class HerbalistBrewGame implements MiniGame {

    private enum State { BET, MEMORIZE, PICK, ROUND_END, RESULT }

    private static final String[] ALL_HERBS = {
        "Moonbloom", "Thornleaf", "Ember Root", "Dewpetal", "Shadowmoss",
        "Ironbark", "Glowspore", "Verdant Fern", "Amber Sap", "Nightshade",
        "Crystal Lichen", "Wolfsbane", "Silkweed", "Starflower", "Root Bark"
    };
    private static final int   ROUNDS        = 4;
    private static final int   LIST_SIZE     = 10;   // items shown to pick from
    private static final long  BASE_VIEW_MS  = 1800; // viewing time before INT bonus

    // -- Payout ladder --------------------------------------------------------
    // Multiple of the stake RETURNED (1.0 = break even, 0 = stake lost).
    // 1 round passed no longer returns the stake.
    private static final float PAY_4_ROUNDS = 2.2f, PAY_3_ROUNDS = 0.9f, PAY_2_ROUNDS = 0.3f;
    private static final long  PAUSE_MS      = 1200;

    private static final Color HERB_SEL  = new Color(20, 60, 20);
    private static final Color HERB_DONE = new Color(40, 120, 40);
    private static final Color HERB_WRONG = new Color(100, 20, 20);

    private final Retroquest game;
    private final NatureGamesOverlay overlay;
    private final Random rng;

    private State state = State.BET;
    private int round = 0;
    private int roundsPassed = 0;
    private List<String> recipe   = new ArrayList<>();   // correct order
    private List<String> pickList = new ArrayList<>();   // shuffled display list
    private int pickIndex = 0;      // next ingredient to pick
    private int listCursor = 0;     // highlighted row in pickList
    private long memorizeStart = 0;
    private long pauseStart    = 0;
    private boolean lastRoundPassed = false;
    private String resultMessage = "";

    HerbalistBrewGame(Retroquest game, NatureGamesOverlay overlay, Random rng) {
        this.game    = game;
        this.overlay = overlay;
        this.rng     = rng;
    }

    @Override public boolean isShowingResult() { return state == State.RESULT; }

    @Override
    public void reset(int betAmount) {
        state = State.BET;
        round = 0;
        roundsPassed = 0;
        resultMessage = "";
    }

    @Override
    public void handleKey(KeyEvent e) {
        switch (state) {
            case BET       -> handleBetKey(e);
            case MEMORIZE  -> {}  // non-interactive
            case PICK      -> handlePickKey(e);
            case ROUND_END -> {}  // auto-advances
            case RESULT    -> handleResultKey(e);
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

    private void handlePickKey(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_UP   -> { listCursor = Math.max(0, listCursor - 1); SoundManager.getInstance().play("menublip"); }
            case KeyEvent.VK_DOWN -> { listCursor = Math.min(pickList.size() - 1, listCursor + 1); SoundManager.getInstance().play("menublip"); }
            case KeyEvent.VK_ENTER -> confirmPick();
        }
    }

    private void handleResultKey(KeyEvent e) {
        overlay.backToMenu();
        if (game.getStatsPanel() != null) game.getStatsPanel().refresh();
    }

    private void startRound() {
        round++;
        int recipeLen = 2 + round; // round 1=3, round 2=4, round 3=5, round 4=6
        recipe.clear();

        List<String> pool = new ArrayList<>(List.of(ALL_HERBS));
        Collections.shuffle(pool, rng);
        recipe.addAll(pool.subList(0, recipeLen));

        // Build pick list: include all recipe items plus fillers
        pickList.clear();
        pickList.addAll(recipe);
        for (String herb : pool.subList(recipeLen, pool.size())) {
            if (pickList.size() >= LIST_SIZE) break;
            pickList.add(herb);
        }
        Collections.shuffle(pickList, rng);

        pickIndex  = 0;
        listCursor = 0;
        memorizeStart = System.currentTimeMillis();
        state = State.MEMORIZE;
    }

    private void confirmPick() {
        String chosen = pickList.get(listCursor);
        if (chosen.equals(recipe.get(pickIndex))) {
            pickIndex++;
            SoundManager.getInstance().play("coin");
            if (pickIndex >= recipe.size()) {
                // Round passed!
                lastRoundPassed = true;
                roundsPassed++;
                pauseStart = System.currentTimeMillis();
                state = State.ROUND_END;
            }
        } else {
            // Wrong pick — round failed
            lastRoundPassed = false;
            SoundManager.getInstance().play("hurt");
            pauseStart = System.currentTimeMillis();
            state = State.ROUND_END;
        }
    }

    @Override
    public void update() {
        long now = System.currentTimeMillis();

        if (state == State.MEMORIZE) {
            int intel = game.getPlayer().getIntelligence();
            long viewMs = BASE_VIEW_MS + Math.max(0, (intel - 10)) * 500L;
            if (now - memorizeStart >= viewMs) {
                state = State.PICK;
            }
        } else if (state == State.ROUND_END) {
            if (now - pauseStart >= PAUSE_MS) {
                if (round >= ROUNDS || !lastRoundPassed) {
                    resolveGame();
                } else {
                    startRound();
                }
            }
        }
    }

    private void resolveGame() {
        int payout;
        if      (roundsPassed >= 4) payout = (int)(overlay.betAmount * PAY_4_ROUNDS);
        else if (roundsPassed >= 3) payout = (int)(overlay.betAmount * PAY_3_ROUNDS);
        else if (roundsPassed >= 2) payout = (int)(overlay.betAmount * PAY_2_ROUNDS);
        else                        payout = 0;

        if (payout > 0) {
            game.getPlayer().addGold(payout);
            resultMessage = roundsPassed >= 4 ? "MASTER HERBALIST! Won " + payout + "g!"
                                              : "Well brewed! Won " + payout + "g!";
            SoundManager.getInstance().play(roundsPassed >= 4 ? "victory" : "coin");
            game.log(resultMessage, MessageLog.Type.LOOT);
        } else {
            resultMessage = "Wrong ingredient! Lost " + overlay.betAmount + "g.";
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
        drawCentered(g, "HERBALIST'S BREW", px, py + 30, pw);

        switch (state) {
            case BET       -> paintBet(g, px, py, pw, ph);
            case MEMORIZE  -> paintMemorize(g, px, py, pw, ph);
            case PICK      -> paintPick(g, px, py, pw, ph);
            case ROUND_END -> paintRoundEnd(g, px, py, pw, ph);
            case RESULT    -> paintResult(g, px, py, pw, ph);
        }
    }

    private void paintBet(Graphics2D g, int px, int py, int pw, int ph) {
        g.setFont(F_MENU);
        g.setColor(TEXT_BRIGHT);
        drawCentered(g, "Memorize the recipe, then pick ingredients in order.", px, py + 70, pw);
        drawCentered(g, "A wrong choice ends the round immediately!", px, py + 90, pw);

        g.setFont(F_BIG);
        g.setColor(EARTH_AMBER);
        drawCentered(g, "Bet: " + overlay.betAmount + "g", px, py + ph / 2, pw);

        g.setFont(F_ITEM);
        g.setColor(AMBER);
        drawCentered(g, "Gold: " + game.getPlayer().getGold() + "g", px, py + ph / 2 + 30, pw);

        g.setFont(F_SMALL);
        g.setColor(TEXT_DIM);
        int intel = game.getPlayer().getIntelligence();
        drawCentered(g, "INT " + intel + " (memorize time)", px, py + ph / 2 + 60, pw);

        g.setFont(F_KEY);
        g.setColor(TEXT_DIM);
        g.drawString("[Left/Right] Bet   [Shift] x10   [Enter] Start   [Esc] Back", px + 20, py + ph - 20);
    }

    private void paintMemorize(Graphics2D g, int px, int py, int pw, int ph) {
        g.setFont(F_MENU);
        g.setColor(LEAF_BRIGHT);
        drawCentered(g, "Round " + round + " / " + ROUNDS + "  —  Memorize this recipe!", px, py + 60, pw);

        // Timer bar
        int intel = game.getPlayer().getIntelligence();
        long viewMs = BASE_VIEW_MS + Math.max(0, (intel - 10)) * 500L;
        float timerFrac = 1f - Math.min(1f, (System.currentTimeMillis() - memorizeStart) / (float) viewMs);
        int barX = px + 40; int barW = pw - 80; int barY = py + 80;
        g.setColor(new Color(30, 30, 30));
        g.fillRect(barX, barY, barW, 8);
        g.setColor(timerFrac > 0.4f ? FOREST_GREEN : DANGER);
        g.fillRect(barX, barY, (int)(barW * timerFrac), 8);

        // Recipe list
        int startY = py + 110;
        for (int i = 0; i < recipe.size(); i++) {
            g.setFont(F_MENU);
            g.setColor(EARTH_AMBER);
            drawCentered(g, (i + 1) + ". " + recipe.get(i), px, startY + i * 26, pw);
        }

        g.setFont(F_KEY);
        g.setColor(TEXT_DIM);
        drawCentered(g, "Memorize quickly! The list will disappear.", px, py + ph - 20, pw);
    }

    private void paintPick(Graphics2D g, int px, int py, int pw, int ph) {
        g.setFont(F_ITEM);
        g.setColor(LEAF_BRIGHT);
        drawCentered(g, "Pick ingredient " + (pickIndex + 1) + " of " + recipe.size() + ":", px, py + 55, pw);

        int listX = px + (pw - 260) / 2;
        int listY = py + 75;
        int rowH  = 26;

        for (int i = 0; i < pickList.size(); i++) {
            int ry = listY + i * rowH;
            boolean sel = i == listCursor;

            g.setColor(sel ? HERB_SEL : new Color(12, 20, 12));
            g.fillRect(listX, ry, 260, rowH - 2);
            if (sel) { g.setColor(FOREST_GREEN); g.drawRect(listX, ry, 260, rowH - 2); }

            g.setFont(F_SMALL);
            g.setColor(sel ? LEAF_BRIGHT : TEXT_BRIGHT);
            g.drawString((sel ? "> " : "  ") + pickList.get(i), listX + 8, ry + rowH - 7);
        }

        // Show already-picked ingredients
        if (pickIndex > 0) {
            g.setFont(F_SMALL);
            g.setColor(TEXT_DIM);
            StringBuilder picked = new StringBuilder("Picked: ");
            for (int i = 0; i < pickIndex; i++) {
                if (i > 0) picked.append(", ");
                picked.append(recipe.get(i));
            }
            drawCentered(g, picked.toString(), px, py + ph - 35, pw);
        }

        g.setFont(F_KEY);
        g.setColor(TEXT_DIM);
        drawCentered(g, "[Up/Down] Select   [Enter] Pick", px, py + ph - 20, pw);
    }

    private void paintRoundEnd(Graphics2D g, int px, int py, int pw, int ph) {
        g.setFont(F_BIG);
        g.setColor(lastRoundPassed ? LEAF_BRIGHT : DANGER);
        drawCentered(g, lastRoundPassed ? "Round " + round + " complete!" : "Wrong ingredient!", px, py + ph / 2 - 20, pw);

        g.setFont(F_MENU);
        g.setColor(EARTH_AMBER);
        drawCentered(g, "Rounds passed: " + roundsPassed, px, py + ph / 2 + 20, pw);
    }

    private void paintResult(Graphics2D g, int px, int py, int pw, int ph) {
        g.setFont(F_BIG);
        g.setColor(FOREST_GREEN);
        drawCentered(g, "Rounds completed: " + roundsPassed + "/" + ROUNDS, px, py + 130, pw);

        g.setFont(F_MENU);
        boolean won = resultMessage.contains("Won") || resultMessage.contains("MASTER");
        g.setColor(won ? GOOD : DANGER);
        drawCentered(g, resultMessage, px, py + 175, pw);

        g.setFont(F_KEY);
        g.setColor(TEXT_DIM);
        drawCentered(g, "[Any key] Continue", px, py + ph - 20, pw);
    }
}

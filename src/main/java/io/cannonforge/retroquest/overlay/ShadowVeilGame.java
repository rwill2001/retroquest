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
 * Shadow Veil: a memory card-flip mini-game. A 4x4 grid holds 8 pairs of
 * shadow symbols. Flip two cards; matched pairs stay revealed. Track mistakes.
 * INT bonus reveals all cards briefly at the start.
 */
class ShadowVeilGame implements MiniGame {

    private enum State { BET, PEEK, SELECT_FIRST, SELECT_SECOND, MATCH_PAUSE, RESULT }

    private static final String[] SYMBOLS = {
        "VEIL", "ECHO", "SHADE", "GRIEF", "HOLLOW", "SORROW", "VOID", "LOST"
    };
    private static final int GRID_COLS  = 4;
    private static final int GRID_ROWS  = 4;
    private static final int TOTAL_CARDS = GRID_COLS * GRID_ROWS;
    private static final long BASE_PEEK_MS   = 0;      // ms cards visible at start (INT adds more)
    private static final long FLIP_PAUSE_MS  = 800;    // pause after mismatch before flipping back

    // -- Payout ladder --------------------------------------------------------
    // Fewer mistakes is better. Values are the multiple of the stake RETURNED
    // (1.0 = break even, 0 = stake lost). Even perfect recall on 8 pairs costs
    // several blind flips, so the top tier lands roughly 1 game in 5, and 10 or
    // more mistakes now loses the stake outright.
    private static final int   MISS_TOP = 3, MISS_GREAT = 5, MISS_GOOD = 7, MISS_WEAK = 9;
    private static final float PAY_TOP = 2.2f, PAY_GREAT = 1.1f, PAY_GOOD = 0.5f, PAY_WEAK = 0.2f;

    private static final Color CARD_BACK   = new Color( 30,  20,  50);
    private static final Color CARD_FACE   = new Color( 50,  40,  80);
    private static final Color CARD_MATCH  = new Color( 30,  60,  80);
    private static final Color CARD_SEL    = new Color( 80,  60, 130);
    private static final Color CARD_BORDER = new Color( 80,  70, 110);
    private static final Color MATCH_TEXT  = new Color(100, 200, 220);

    private final Retroquest game;
    private final MemoryGamesOverlay overlay;
    private final Random rng;

    private State state = State.BET;
    private String[] cards  = new String[TOTAL_CARDS];   // symbol at each position
    private boolean[] faceUp = new boolean[TOTAL_CARDS]; // currently visible
    private boolean[] matched = new boolean[TOTAL_CARDS];
    private int cursorX = 0, cursorY = 0;
    private int firstFlip = -1;
    private int mistakes  = 0;
    private int matchesMade = 0;
    private long pauseStart = 0;
    private long peekStart  = 0;
    private String resultMessage = "";

    ShadowVeilGame(Retroquest game, MemoryGamesOverlay overlay, Random rng) {
        this.game    = game;
        this.overlay = overlay;
        this.rng     = rng;
    }

    @Override public boolean isShowingResult() { return state == State.RESULT; }

    @Override
    public void reset(int betAmount) {
        state = State.BET;
        mistakes = 0;
        matchesMade = 0;
        resultMessage = "";
        firstFlip = -1;
        cursorX = 0; cursorY = 0;

        // Shuffle cards
        List<String> deck = new ArrayList<>();
        for (String sym : SYMBOLS) { deck.add(sym); deck.add(sym); }
        Collections.shuffle(deck, rng);
        for (int i = 0; i < TOTAL_CARDS; i++) {
            cards[i]   = deck.get(i);
            faceUp[i]  = false;
            matched[i] = false;
        }
    }

    @Override
    public void handleKey(KeyEvent e) {
        switch (state) {
            case BET          -> handleBetKey(e);
            case PEEK         -> {}  // non-interactive, auto-transitions
            case SELECT_FIRST, SELECT_SECOND -> handleSelectKey(e);
            case RESULT       -> handleResultKey(e);
            default           -> {}
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
                startGame();
            }
            case KeyEvent.VK_ESCAPE -> { overlay.backToMenu(); SoundManager.getInstance().play("menublip"); }
        }
    }

    private void handleSelectKey(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_LEFT  -> { cursorX = Math.max(0, cursorX - 1); SoundManager.getInstance().play("menublip"); }
            case KeyEvent.VK_RIGHT -> { cursorX = Math.min(GRID_COLS - 1, cursorX + 1); SoundManager.getInstance().play("menublip"); }
            case KeyEvent.VK_UP    -> { cursorY = Math.max(0, cursorY - 1); SoundManager.getInstance().play("menublip"); }
            case KeyEvent.VK_DOWN  -> { cursorY = Math.min(GRID_ROWS - 1, cursorY + 1); SoundManager.getInstance().play("menublip"); }
            case KeyEvent.VK_ENTER -> flipCard();
        }
    }

    private void handleResultKey(KeyEvent e) {
        overlay.backToMenu();
        if (game.getStatsPanel() != null) game.getStatsPanel().refresh();
    }

    private void startGame() {
        int intel = game.getPlayer().getIntelligence();
        if (intel >= 14) {
            // Show all cards briefly
            for (int i = 0; i < TOTAL_CARDS; i++) faceUp[i] = true;
            peekStart = System.currentTimeMillis();
            state = State.PEEK;
        } else {
            state = State.SELECT_FIRST;
        }
    }

    private void flipCard() {
        int idx = cursorY * GRID_COLS + cursorX;
        if (faceUp[idx] || matched[idx]) return;

        faceUp[idx] = true;
        SoundManager.getInstance().play("menublip");

        if (state == State.SELECT_FIRST) {
            firstFlip = idx;
            state = State.SELECT_SECOND;
        } else {
            // Check match
            if (cards[firstFlip].equals(cards[idx])) {
                matched[firstFlip] = true;
                matched[idx] = true;
                matchesMade++;
                SoundManager.getInstance().play("coin");
                if (matchesMade >= SYMBOLS.length) {
                    resolveGame();
                } else {
                    state = State.SELECT_FIRST;
                }
            } else {
                mistakes++;
                SoundManager.getInstance().play("hurt");
                pauseStart = System.currentTimeMillis();
                state = State.MATCH_PAUSE;
            }
            firstFlip = -1;
        }
    }

    @Override
    public void update() {
        long now = System.currentTimeMillis();
        if (state == State.PEEK) {
            int intel = game.getPlayer().getIntelligence();
            long peekMs = 1000 + Math.max(0, intel - 14) * 500L;
            if (now - peekStart >= peekMs) {
                for (int i = 0; i < TOTAL_CARDS; i++) faceUp[i] = false;
                state = State.SELECT_FIRST;
            }
        } else if (state == State.MATCH_PAUSE) {
            if (now - pauseStart >= FLIP_PAUSE_MS) {
                // Flip the two non-matched face-up cards back down
                for (int i = 0; i < TOTAL_CARDS; i++) {
                    if (faceUp[i] && !matched[i]) faceUp[i] = false;
                }
                state = State.SELECT_FIRST;
            }
        }
    }

    private void resolveGame() {
        int payout;
        if      (mistakes <= MISS_TOP)   payout = (int)(overlay.betAmount * PAY_TOP);
        else if (mistakes <= MISS_GREAT) payout = (int)(overlay.betAmount * PAY_GREAT);
        else if (mistakes <= MISS_GOOD)  payout = (int)(overlay.betAmount * PAY_GOOD);
        else if (mistakes <= MISS_WEAK)  payout = (int)(overlay.betAmount * PAY_WEAK);
        else                             payout = 0;

        if (payout > 0) {
            game.getPlayer().addGold(payout);
            resultMessage = mistakes <= MISS_TOP ? "PERFECT MEMORY! Won " + payout + "g!"
                                                 : "All matched! Won " + payout + "g!";
            SoundManager.getInstance().play(mistakes <= MISS_TOP ? "victory" : "coin");
            game.log(resultMessage, MessageLog.Type.LOOT);
        } else {
            resultMessage = "Too many mistakes. Lost " + overlay.betAmount + "g.";
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
        drawCentered(g, "SHADOW VEIL", px, py + 30, pw);

        switch (state) {
            case BET                                  -> paintBet(g, px, py, pw, ph);
            case PEEK, SELECT_FIRST, SELECT_SECOND,
                 MATCH_PAUSE                          -> paintGrid(g, px, py, pw, ph);
            case RESULT                               -> paintResult(g, px, py, pw, ph);
        }
    }

    private void paintBet(Graphics2D g, int px, int py, int pw, int ph) {
        g.setFont(F_MENU);
        g.setColor(TEXT_BRIGHT);
        drawCentered(g, "Match all 8 pairs of shadow symbols.", px, py + 70, pw);
        drawCentered(g, "Fewer mistakes = bigger payout.", px, py + 90, pw);

        g.setFont(F_BIG);
        g.setColor(SHADOW_PURPLE);
        drawCentered(g, "Bet: " + overlay.betAmount + "g", px, py + ph / 2, pw);

        g.setFont(F_ITEM);
        g.setColor(AMBER);
        drawCentered(g, "Gold: " + game.getPlayer().getGold() + "g", px, py + ph / 2 + 30, pw);

        g.setFont(F_SMALL);
        g.setColor(TEXT_DIM);
        int intel = game.getPlayer().getIntelligence();
        drawCentered(g, "INT " + intel + (intel >= 14 ? " (cards revealed at start!)" : " (need 14 for peek bonus)"), px, py + ph / 2 + 60, pw);

        g.setFont(F_KEY);
        g.setColor(TEXT_DIM);
        g.drawString("[Left/Right] Bet   [Shift] x10   [Enter] Start   [Esc] Back", px + 20, py + ph - 20);
    }

    private void paintGrid(Graphics2D g, int px, int py, int pw, int ph) {
        int cardW = 90, cardH = 44, padX = 14, padY = 10;
        int gridW = GRID_COLS * (cardW + padX) - padX;
        int gridH = GRID_ROWS * (cardH + padY) - padY;
        int startX = px + (pw - gridW) / 2;
        int startY = py + 65;

        for (int row = 0; row < GRID_ROWS; row++) {
            for (int col = 0; col < GRID_COLS; col++) {
                int idx = row * GRID_COLS + col;
                int cx = startX + col * (cardW + padX);
                int cy = startY + row * (cardH + padY);
                boolean isCursor = (col == cursorX && row == cursorY);
                boolean isFirst  = (idx == firstFlip);

                if (matched[idx]) {
                    g.setColor(CARD_MATCH);
                } else if (isCursor || isFirst) {
                    g.setColor(CARD_SEL);
                } else {
                    g.setColor(faceUp[idx] ? CARD_FACE : CARD_BACK);
                }
                g.fillRoundRect(cx, cy, cardW, cardH, 6, 6);

                g.setColor(matched[idx] ? MATCH_TEXT : isCursor ? GHOST_WHITE : CARD_BORDER);
                g.drawRoundRect(cx, cy, cardW, cardH, 6, 6);

                if (faceUp[idx] || matched[idx]) {
                    g.setFont(F_SMALL);
                    g.setColor(matched[idx] ? MATCH_TEXT : GHOST_WHITE);
                    int tw = g.getFontMetrics().stringWidth(cards[idx]);
                    g.drawString(cards[idx], cx + (cardW - tw) / 2, cy + cardH / 2 + 5);
                } else {
                    // Back face: small shadow glyph
                    g.setFont(F_BIG);
                    g.setColor(VOID_BLUE);
                    g.drawString("?", cx + cardW / 2 - 5, cy + cardH / 2 + 6);
                }
            }
        }

        // HUD
        g.setFont(F_ITEM);
        g.setColor(SHADOW_PURPLE);
        g.drawString("Pairs: " + matchesMade + "/" + SYMBOLS.length + "  Mistakes: " + mistakes, px + 20, py + 52);

        if (state == State.PEEK) {
            g.setFont(F_MENU);
            g.setColor(GHOST_WHITE);
            drawCentered(g, "Memorize!", px, py + ph - 45, pw);
        }

        g.setFont(F_KEY);
        g.setColor(TEXT_DIM);
        drawCentered(g, "[Arrows] Move   [Enter] Flip", px, py + ph - 20, pw);
    }

    private void paintResult(Graphics2D g, int px, int py, int pw, int ph) {
        g.setFont(F_BIG);
        g.setColor(SHADOW_PURPLE);
        drawCentered(g, "Mistakes: " + mistakes, px, py + 130, pw);

        g.setFont(F_MENU);
        boolean won = resultMessage.contains("Won") || resultMessage.contains("PERFECT");
        g.setColor(won ? GOOD : DANGER);
        drawCentered(g, resultMessage, px, py + 175, pw);

        g.setFont(F_KEY);
        g.setColor(TEXT_DIM);
        drawCentered(g, "[Any key] Continue", px, py + ph - 20, pw);
    }
}

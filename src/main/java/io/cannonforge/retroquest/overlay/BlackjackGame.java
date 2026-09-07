package io.cannonforge.retroquest.overlay;

import static io.cannonforge.retroquest.overlay.CasinoOverlay.F_BIG;
import static io.cannonforge.retroquest.overlay.CasinoOverlay.F_MENU;
import static io.cannonforge.retroquest.overlay.CasinoOverlay.F_TITLE;
import static io.cannonforge.retroquest.overlay.CasinoOverlay.GOLD_COL;
import static io.cannonforge.retroquest.overlay.CasinoOverlay.drawCentered;
import static io.cannonforge.retroquest.overlay.OverlayTheme.AMBER;
import static io.cannonforge.retroquest.overlay.OverlayTheme.BORDER_COL;
import static io.cannonforge.retroquest.overlay.OverlayTheme.DANGER;
import static io.cannonforge.retroquest.overlay.OverlayTheme.F_ITEM;
import static io.cannonforge.retroquest.overlay.OverlayTheme.F_KEY;
import static io.cannonforge.retroquest.overlay.OverlayTheme.F_SMALL;
import static io.cannonforge.retroquest.overlay.OverlayTheme.GOOD;
import static io.cannonforge.retroquest.overlay.OverlayTheme.TEXT_BRIGHT;
import static io.cannonforge.retroquest.overlay.OverlayTheme.TEXT_DIM;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import io.cannonforge.retroquest.core.MessageLog;
import io.cannonforge.retroquest.core.Retroquest;
import io.cannonforge.retroquest.core.SoundManager;

/**
 * Blackjack mini-game for the casino overlay.
 */
class BlackjackGame implements MiniGame {

    private enum State { BET, DEAL, PLAY, DEALER, RESULT }

    private record Card(int rank, int suit) {} // rank 1-13, suit 0-3

    // Card colours
    private static final Color CARD_WHITE  = new Color(230, 230, 220);
    private static final Color CARD_BORDER = new Color( 50,  40,  30);
    private static final Color CARD_RED    = new Color(200,  40,  40);
    private static final Color CARD_BLACK  = new Color( 20,  20,  20);
    private static final Color CARD_BACK   = new Color( 60,  30,  90);
    private static final Color CARD_HATCH  = new Color( 80,  50, 110);

    // Felt green for blackjack table
    private static final Color FELT = new Color( 20,  80,  40);

    private static final int CARD_W = 72;
    private static final int CARD_H = 96;
    private static final int CARD_FAN_OFFSET = 45;
    private static final long DEAL_CARD_MS = 300;

    // Suit pixel art (7x5 boolean grids)
    private static final boolean[][][] SUIT_ART = buildSuitArt();

    private final Retroquest game;
    private final CasinoOverlay overlay;
    private final Random rng;

    private State state = State.BET;

    private final List<Card> deck = new ArrayList<>();
    private final List<Card> playerHand = new ArrayList<>();
    private final List<Card> dealerHand = new ArrayList<>();
    private boolean dealerHoleRevealed = false;
    private long dealStartTime = 0;
    private long dealerRevealTime = 0;
    private int dealerDrawIndex = 0;
    private String bjResultMessage = "";
    /**
     * Stake actually riding on THIS hand. Kept separate from overlay.betAmount so a
     * double-down cannot push the table stake past the house maximum, and so the
     * doubled amount does not leak into the next hand or into the other games.
     */
    private int activeBet = 0;

    BlackjackGame(Retroquest game, CasinoOverlay overlay, Random rng) {
        this.game = game;
        this.overlay = overlay;
        this.rng = rng;
    }

    @Override
    public void reset(int betAmount) {
        state = State.BET;
        bjResultMessage = "";
    }

    @Override
    public boolean isShowingResult() {
        return state == State.RESULT;
    }

    @Override
    public void handleKey(KeyEvent e) {
        switch (state) {
            case BET    -> handleBetKey(e);
            case DEAL   -> {} // no input during deal
            case PLAY   -> handlePlayKey(e);
            case DEALER -> {} // no input during dealer turn
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
                activeBet = overlay.betAmount;
                SoundManager.getInstance().play("coin");
                startDeal();
            }
            case KeyEvent.VK_ESCAPE -> { overlay.backToMenu(); SoundManager.getInstance().play("menublip"); }
        }
    }

    private void handlePlayKey(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_H -> bjHit();
            case KeyEvent.VK_S -> bjStand();
            case KeyEvent.VK_D -> bjDoubleDown();
        }
    }

    private void handleResultKey(KeyEvent e) {
        overlay.backToMenu();
        if (game.getStatsPanel() != null) game.getStatsPanel().refresh();
    }

    // ── Logic ───────────────────────────────────────────────────────────────────

    private void shuffleDeck() {
        deck.clear();
        for (int s = 0; s < 4; s++)
            for (int r = 1; r <= 13; r++)
                deck.add(new Card(r, s));
        Collections.shuffle(deck, rng);
    }

    private Card drawCardFromDeck() {
        if (deck.size() < 15) shuffleDeck();
        return deck.remove(deck.size() - 1);
    }

    private int handValue(List<Card> hand) {
        int total = 0, aces = 0;
        for (Card c : hand) {
            int v = Math.min(c.rank(), 10);
            if (c.rank() == 1) { v = 11; aces++; }
            total += v;
        }
        while (total > 21 && aces > 0) { total -= 10; aces--; }
        return total;
    }

    private void startDeal() {
        playerHand.clear();
        dealerHand.clear();
        dealerHoleRevealed = false;
        bjResultMessage = "";

        if (deck.size() < 15) shuffleDeck();

        playerHand.add(drawCardFromDeck());
        dealerHand.add(drawCardFromDeck());
        playerHand.add(drawCardFromDeck());
        dealerHand.add(drawCardFromDeck());

        dealStartTime = System.currentTimeMillis();
        state = State.DEAL;
    }

    private void checkDealComplete() {
        long elapsed = System.currentTimeMillis() - dealStartTime;
        int cardsShown = Math.min(4, (int)(elapsed / DEAL_CARD_MS) + 1);
        if (cardsShown >= 4 && elapsed > DEAL_CARD_MS * 4 + 200) {
            if (handValue(playerHand) == 21) {
                dealerHoleRevealed = true;
                if (handValue(dealerHand) == 21) {
                    bjResultMessage = "Push! Both have Blackjack.";
                    game.getPlayer().addGold(activeBet);
                    game.getPlayer().recordGamblingResult(0);
                } else {
                    // 3:2. Rounded rather than integer-divided, so a 1g bet still
                    // pays 3:2 instead of silently collapsing to 1:1.
                    int payout = activeBet + Math.round(activeBet * 1.5f);
                    game.getPlayer().addGold(payout);
                    bjResultMessage = "Blackjack! +" + payout + "g";
                    SoundManager.getInstance().play("victory");
                    game.getPlayer().recordGamblingResult(payout - activeBet);
                }
                state = State.RESULT;
                if (game.getStatsPanel() != null) game.getStatsPanel().refresh();
            } else {
                state = State.PLAY;
            }
        }
    }

    private void bjHit() {
        playerHand.add(drawCardFromDeck());
        SoundManager.getInstance().play("menublip");
        if (handValue(playerHand) > 21) {
            bjResultMessage = "Bust! You lose " + activeBet + "g.";
            SoundManager.getInstance().play("hurt");
            game.getPlayer().recordGamblingResult(-activeBet);
            state = State.RESULT;
            if (game.getStatsPanel() != null) game.getStatsPanel().refresh();
        }
    }

    private void bjStand() {
        dealerHoleRevealed = true;
        dealerRevealTime = System.currentTimeMillis();
        dealerDrawIndex = dealerHand.size();
        state = State.DEALER;
    }

    private void bjDoubleDown() {
        if (playerHand.size() != 2) return;
        if (game.getPlayer().getGold() < activeBet) {
            game.log("Not enough gold to double down!", MessageLog.Type.DANGER);
            return;
        }
        // A doubled hand is still one stake at this table, so it must respect the house limit.
        if (activeBet * 2 > game.getPlayer().getMaxBet()) {
            game.log("The house limit won't cover a doubled bet!", MessageLog.Type.DANGER);
            return;
        }
        game.getPlayer().addGold(-activeBet);
        activeBet *= 2;
        SoundManager.getInstance().play("coin");

        playerHand.add(drawCardFromDeck());
        if (handValue(playerHand) > 21) {
            bjResultMessage = "Bust! You lose " + activeBet + "g.";
            SoundManager.getInstance().play("hurt");
            game.getPlayer().recordGamblingResult(-activeBet);
            state = State.RESULT;
            if (game.getStatsPanel() != null) game.getStatsPanel().refresh();
        } else {
            bjStand();
        }
    }

    private void checkDealerTurn() {
        long elapsed = System.currentTimeMillis() - dealerRevealTime;
        int expectedCards = 2 + (int)(elapsed / 500);

        while (dealerDrawIndex < expectedCards && handValue(dealerHand) < 17) {
            dealerHand.add(drawCardFromDeck());
            dealerDrawIndex++;
            SoundManager.getInstance().play("menublip");
        }

        if (handValue(dealerHand) >= 17 || dealerDrawIndex >= expectedCards) {
            if (handValue(dealerHand) >= 17) {
                resolveResult();
            }
        }
    }

    private void resolveResult() {
        int pVal = handValue(playerHand);
        int dVal = handValue(dealerHand);
        int net;

        if (dVal > 21) {
            int payout = activeBet * 2;
            game.getPlayer().addGold(payout);
            bjResultMessage = "Dealer busts! +" + payout + "g";
            SoundManager.getInstance().play("coin");
            net = payout - activeBet;
        } else if (pVal > dVal) {
            int payout = activeBet * 2;
            game.getPlayer().addGold(payout);
            bjResultMessage = "You win! +" + payout + "g";
            SoundManager.getInstance().play("coin");
            net = payout - activeBet;
        } else if (pVal == dVal) {
            game.getPlayer().addGold(activeBet);
            bjResultMessage = "Push! Bet returned.";
            net = 0;
        } else {
            bjResultMessage = "Dealer wins. You lose " + activeBet + "g.";
            net = -activeBet;
        }

        game.getPlayer().recordGamblingResult(net);
        state = State.RESULT;
        if (game.getStatsPanel() != null) game.getStatsPanel().refresh();
    }

    // ── Update ──────────────────────────────────────────────────────────────────

    @Override
    public void update() {
        if (state == State.DEAL) checkDealComplete();
        if (state == State.DEALER) checkDealerTurn();
    }

    // ── Paint ───────────────────────────────────────────────────────────────────

    @Override
    public void paint(Graphics2D g, int px, int py, int pw, int ph) {
        switch (state) {
            case BET -> paintBet(g, px, py, pw, ph);
            case DEAL, PLAY, DEALER -> paintPlay(g, px, py, pw, ph);
            case RESULT -> paintResult(g, px, py, pw, ph);
        }
    }

    private void paintBet(Graphics2D g, int px, int py, int pw, int ph) {
        g.setFont(F_TITLE);
        g.setColor(GOLD_COL);
        drawCentered(g, "BLACKJACK", px, py + 30, pw);

        g.setFont(F_MENU);
        g.setColor(AMBER);
        drawCentered(g, "Bet: " + overlay.betAmount + "g", px, py + 80, pw);

        g.setFont(F_ITEM);
        g.setColor(TEXT_DIM);
        drawCentered(g, "Left/Right to adjust (Shift for x10)", px, py + 105, pw);
        drawCentered(g, "Gold: " + game.getPlayer().getGold() + "g", px, py + 130, pw);

        // Mini rules
        g.setFont(F_SMALL);
        g.setColor(TEXT_DIM);
        int ry = py + 180;
        drawCentered(g, "Standard Blackjack rules", ry, ry, pw);
        g.drawString("  Natural 21 pays 3:2", px + 40, ry + 20);
        g.drawString("  Regular win pays 1:1", px + 40, ry + 35);
        g.drawString("  Double Down on first two cards", px + 40, ry + 50);

        g.setFont(F_KEY);
        g.setColor(TEXT_DIM);
        drawCentered(g, "[Enter] Deal   [Esc] Back", px, py + ph - 20, pw);
    }

    private void paintPlay(Graphics2D g, int px, int py, int pw, int ph) {
        // Felt background
        g.setColor(FELT);
        g.fillRect(px + 3, py + 3, pw - 6, ph - 6);
        g.setColor(BORDER_COL);
        g.drawRect(px, py, pw, ph);

        g.setFont(F_TITLE);
        g.setColor(GOLD_COL);
        drawCentered(g, "BLACKJACK", px, py + 25, pw);

        long dealElapsed = (state == State.DEAL)
                ? System.currentTimeMillis() - dealStartTime : Long.MAX_VALUE;

        // Dealer cards
        int dealerY = py + 45;
        int cardStartX = px + (pw - dealerHand.size() * CARD_FAN_OFFSET - (CARD_W - CARD_FAN_OFFSET)) / 2;
        g.setFont(F_ITEM);
        g.setColor(TEXT_BRIGHT);
        g.drawString("Dealer", px + 15, dealerY + CARD_H / 2);

        for (int i = 0; i < dealerHand.size(); i++) {
            if (state == State.DEAL) {
                int dealOrder = (i == 0) ? 1 : 3;
                if (dealElapsed < dealOrder * DEAL_CARD_MS) continue;
            }
            int cx = cardStartX + i * CARD_FAN_OFFSET;
            boolean faceUp = (i == 0 || dealerHoleRevealed);
            drawCard(g, dealerHand.get(i), cx, dealerY, faceUp);
        }

        if (dealerHoleRevealed) {
            g.setFont(F_ITEM);
            g.setColor(TEXT_BRIGHT);
            g.drawString("(" + handValue(dealerHand) + ")", px + pw - 60, dealerY + CARD_H / 2 + 5);
        }

        // Center info
        int centerY = py + ph / 2 - 10;
        g.setFont(F_ITEM);
        g.setColor(AMBER);
        drawCentered(g, "Bet: " + activeBet + "g", px, centerY, pw);

        // Player cards
        int playerY = py + ph - CARD_H - 60;
        cardStartX = px + (pw - playerHand.size() * CARD_FAN_OFFSET - (CARD_W - CARD_FAN_OFFSET)) / 2;
        g.setFont(F_ITEM);
        g.setColor(TEXT_BRIGHT);
        g.drawString("You", px + 15, playerY + CARD_H / 2);

        for (int i = 0; i < playerHand.size(); i++) {
            if (state == State.DEAL) {
                int dealOrder = (i == 0) ? 0 : 2;
                if (dealElapsed < dealOrder * DEAL_CARD_MS) continue;
            }
            int cx = cardStartX + i * CARD_FAN_OFFSET;
            drawCard(g, playerHand.get(i), cx, playerY, true);
        }

        // Player total
        int pVal = handValue(playerHand);
        g.setFont(F_ITEM);
        g.setColor(pVal > 21 ? DANGER : TEXT_BRIGHT);
        g.drawString("(" + pVal + ")", px + pw - 60, playerY + CARD_H / 2 + 5);

        // Action keys
        if (state == State.PLAY) {
            g.setFont(F_KEY);
            g.setColor(TEXT_BRIGHT);
            String actions = "[H] Hit   [S] Stand";
            if (playerHand.size() == 2 && game.getPlayer().getGold() >= activeBet
                    && activeBet * 2 <= game.getPlayer().getMaxBet()) {
                actions += "   [D] Double Down";
            }
            drawCentered(g, actions, px, py + ph - 15, pw);
        } else if (state == State.DEALER) {
            g.setFont(F_KEY);
            g.setColor(TEXT_DIM);
            drawCentered(g, "Dealer is drawing...", px, py + ph - 15, pw);
        } else {
            g.setFont(F_KEY);
            g.setColor(TEXT_DIM);
            drawCentered(g, "Dealing...", px, py + ph - 15, pw);
        }
    }

    private void paintResult(Graphics2D g, int px, int py, int pw, int ph) {
        paintPlay(g, px, py, pw, ph);

        int boxH = 80;
        int boxY = py + ph / 2 - boxH / 2;
        g.setColor(new Color(0, 0, 0, 180));
        g.fillRect(px + 20, boxY, pw - 40, boxH);
        g.setColor(BORDER_COL);
        g.drawRect(px + 20, boxY, pw - 40, boxH);

        boolean isWin = bjResultMessage.contains("win") || bjResultMessage.contains("Blackjack")
                     || bjResultMessage.contains("busts");
        boolean isPush = bjResultMessage.contains("Push");

        g.setFont(F_BIG);
        g.setColor(isWin ? GOOD : isPush ? AMBER : DANGER);
        drawCentered(g, bjResultMessage, px, boxY + 35, pw);

        g.setFont(F_ITEM);
        g.setColor(AMBER);
        drawCentered(g, "Gold: " + game.getPlayer().getGold() + "g", px, boxY + 58, pw);

        g.setFont(F_KEY);
        g.setColor(TEXT_DIM);
        drawCentered(g, "Press any key to continue", px, boxY + boxH - 5, pw);
    }

    // ── Card drawing ────────────────────────────────────────────────────────────

    private void drawCard(Graphics2D g, Card card, int x, int y, boolean faceUp) {
        if (!faceUp) {
            g.setColor(CARD_BACK);
            g.fillRect(x, y, CARD_W, CARD_H);
            Shape oldClip = g.getClip();
            g.setClip(x, y, CARD_W, CARD_H);
            g.setColor(CARD_HATCH);
            for (int i = 0; i < CARD_W + CARD_H; i += 8) {
                g.drawLine(x + i, y, x, y + i);
                g.drawLine(x + CARD_W - i, y, x + CARD_W, y + i);
            }
            g.setClip(oldClip);
            g.setColor(CARD_BORDER);
            g.drawRect(x, y, CARD_W, CARD_H);
            return;
        }

        g.setColor(CARD_WHITE);
        g.fillRect(x, y, CARD_W, CARD_H);
        g.setColor(CARD_BORDER);
        g.drawRect(x, y, CARD_W, CARD_H);

        Color suitColor = (card.suit() == 0 || card.suit() == 1) ? CARD_RED : CARD_BLACK;
        g.setColor(suitColor);

        String rankStr = switch (card.rank()) {
            case 1  -> "A";
            case 11 -> "J";
            case 12 -> "Q";
            case 13 -> "K";
            default -> String.valueOf(card.rank());
        };

        g.setFont(F_ITEM);
        g.drawString(rankStr, x + 5, y + 16);

        drawSuit(g, card.suit(), x + CARD_W / 2 - 7, y + CARD_H / 2 - 7, 3, suitColor);
    }

    private void drawSuit(Graphics2D g, int suit, int x, int y, int pixelSize, Color color) {
        if (suit < 0 || suit >= SUIT_ART.length) return;
        boolean[][] art = SUIT_ART[suit];
        g.setColor(color);
        for (int row = 0; row < art.length; row++) {
            for (int col = 0; col < art[row].length; col++) {
                if (art[row][col]) {
                    g.fillRect(x + col * pixelSize, y + row * pixelSize, pixelSize, pixelSize);
                }
            }
        }
    }

    private static boolean[][][] buildSuitArt() {
        String[][] patterns = {
            { // Hearts
                ".X.X.",
                "XXXXX",
                "XXXXX",
                "XXXXX",
                ".XXX.",
                "..X..",
                "....."
            },
            { // Diamonds
                "..X..",
                ".XXX.",
                "XXXXX",
                ".XXX.",
                "..X..",
                ".....",
                "....."
            },
            { // Clubs
                "..X..",
                ".XXX.",
                "X.X.X",
                ".XXX.",
                "..X..",
                ".XXX.",
                "....."
            },
            { // Spades
                "..X..",
                ".XXX.",
                "XXXXX",
                "XXXXX",
                ".XXX.",
                "..X..",
                ".XXX."
            }
        };

        boolean[][][] art = new boolean[4][7][5];
        for (int s = 0; s < 4; s++) {
            for (int r = 0; r < 7; r++) {
                for (int c = 0; c < 5; c++) {
                    art[s][r][c] = patterns[s][r].charAt(c) == 'X';
                }
            }
        }
        return art;
    }
}

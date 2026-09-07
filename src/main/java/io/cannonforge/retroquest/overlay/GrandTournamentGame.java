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
 * Grand Tournament: a reaction duel mini-game. The opponent briefly telegraphs
 * ATTACK, BLOCK, or DODGE. Then it's hidden — player must recall and respond
 * with the winning counter. Best of 5 rounds. Reveal window shrinks each round.
 * DEX bonus widens the reveal window.
 *
 * Counter rules: BLOCK beats ATTACK, ATTACK beats DODGE, DODGE beats BLOCK.
 */
class GrandTournamentGame implements MiniGame {

    private enum State { BET, OPPONENT_SHOW, PLAYER_PICK, RESOLVE, RESULT }

    private enum Move {
        ATTACK("ATTACK", new Color(220,  70,  60), "[Right]"),
        BLOCK ("BLOCK",  new Color( 60, 130, 220), "[Left]"),
        DODGE ("DODGE",  new Color( 60, 190, 100), "[Down]");

        final String label;
        final Color  col;
        final String key;
        Move(String label, Color col, String key) { this.label = label; this.col = col; this.key = key; }
    }

    // Counter table: what beats what
    private static Move counterFor(Move opponentMove) {
        return switch (opponentMove) {
            case ATTACK -> Move.BLOCK;   // Block beats Attack
            case DODGE  -> Move.ATTACK;  // Attack beats Dodge
            case BLOCK  -> Move.DODGE;   // Dodge beats Block
        };
    }

    private static final int   ROUNDS          = 5;
    private static final long  BASE_REVEAL_MS  = 420;   // ms opponent move is shown
    private static final long  MIN_REVEAL_MS   = 150;   // floor once the rounds tighten
    private static final long  DEX_REVEAL_BONUS_MS = 30; // extra reveal ms per DEX point over 10

    // -- Payout ladder --------------------------------------------------------
    // Multiple of the stake RETURNED (1.0 = break even, 0 = stake lost).
    // 2 wins or fewer now loses; only a clean sweep reaches the top tier.
    private static final float PAY_5_WINS = 2.5f, PAY_4_WINS = 1.2f, PAY_3_WINS = 0.5f;
    private static final long  PICK_TIMEOUT_MS = 2500;  // ms to pick before auto-loss
    private static final long  RESOLVE_MS      = 1200;  // ms to show round outcome

    // Reveal time fraction per round (shrinks each round)
    private static final float[] ROUND_REVEAL_MULT = { 1.0f, 0.85f, 0.70f, 0.55f, 0.42f };

    private static final Color PICK_HIGHLIGHT = new Color(220, 200, 80);
    private static final Color WIN_COL        = new Color( 80, 210, 100);
    private static final Color LOSE_COL       = new Color(210,  80,  80);
    private static final Color TIE_COL        = new Color(180, 180, 100);

    private final Retroquest game;
    private final WarGamesOverlay overlay;
    private final Random rng;

    private State state      = State.BET;
    private Move  opponentMove;
    private Move  playerMove;
    private int   round      = 1;     // 1-based
    private int   playerWins = 0;
    private long  revealStart;
    private long  pickStart;
    private long  resolveStart;
    private boolean roundWon;
    private boolean roundTied;
    private String resultMessage = "";

    GrandTournamentGame(Retroquest game, WarGamesOverlay overlay, Random rng) {
        this.game    = game;
        this.overlay = overlay;
        this.rng     = rng;
    }

    @Override public boolean isShowingResult() { return state == State.RESULT; }

    @Override
    public void reset(int betAmount) {
        state = State.BET;
        round = 1;
        playerWins = 0;
        resultMessage = "";
    }

    @Override
    public void handleKey(KeyEvent e) {
        switch (state) {
            case BET          -> handleBetKey(e);
            case PLAYER_PICK  -> handlePickKey(e);
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
                startRound();
            }
            case KeyEvent.VK_ESCAPE -> { overlay.backToMenu(); SoundManager.getInstance().play("menublip"); }
        }
    }

    private void handlePickKey(KeyEvent e) {
        Move choice = switch (e.getKeyCode()) {
            case KeyEvent.VK_LEFT  -> Move.BLOCK;
            case KeyEvent.VK_DOWN  -> Move.DODGE;
            case KeyEvent.VK_RIGHT -> Move.ATTACK;
            default -> null;
        };
        if (choice != null) resolveRound(choice);
    }

    private void handleResultKey(KeyEvent e) {
        overlay.backToMenu();
        if (game.getStatsPanel() != null) game.getStatsPanel().refresh();
    }

    private void startRound() {
        opponentMove = Move.values()[rng.nextInt(Move.values().length)];
        revealStart  = System.currentTimeMillis();
        state        = State.OPPONENT_SHOW;
    }

    private long revealMs() {
        int dex = game.getPlayer().getDex();
        long base = BASE_REVEAL_MS + Math.max(0, dex - 10) * DEX_REVEAL_BONUS_MS;
        float mult = ROUND_REVEAL_MULT[Math.min(round - 1, ROUND_REVEAL_MULT.length - 1)];
        return Math.max(MIN_REVEAL_MS, (long)(base * mult));
    }

    private void resolveRound(Move choice) {
        playerMove = choice;
        Move winning = counterFor(opponentMove);
        roundWon  = (choice == winning);
        roundTied = (choice == opponentMove);
        if (roundWon) {
            playerWins++;
            SoundManager.getInstance().play("coin");
        } else if (roundTied) {
            SoundManager.getInstance().play("menublip");
        } else {
            SoundManager.getInstance().play("hurt");
        }
        resolveStart = System.currentTimeMillis();
        state = State.RESOLVE;
    }

    @Override
    public void update() {
        long now = System.currentTimeMillis();
        if (state == State.OPPONENT_SHOW) {
            if (now - revealStart >= revealMs()) {
                pickStart = now;
                state = State.PLAYER_PICK;
            }
        } else if (state == State.PLAYER_PICK) {
            // Auto-lose if pick timeout expires
            if (now - pickStart >= PICK_TIMEOUT_MS) {
                playerMove = null;
                roundWon  = false;
                roundTied = false;
                SoundManager.getInstance().play("hurt");
                resolveStart = now;
                state = State.RESOLVE;
            }
        } else if (state == State.RESOLVE) {
            if (now - resolveStart >= RESOLVE_MS) {
                round++;
                if (round > ROUNDS) resolveGame();
                else startRound();
            }
        }
    }

    private void resolveGame() {
        int payout;
        if      (playerWins >= 5) payout = (int)(overlay.betAmount * PAY_5_WINS);
        else if (playerWins >= 4) payout = (int)(overlay.betAmount * PAY_4_WINS);
        else if (playerWins >= 3) payout = (int)(overlay.betAmount * PAY_3_WINS);
        else                      payout = 0;

        if (payout > 0) {
            game.getPlayer().addGold(payout);
            resultMessage = playerWins == 5 ? "TOURNAMENT CHAMPION! Won " + payout + "g!"
                                            : playerWins + " victories! Won " + payout + "g!";
            SoundManager.getInstance().play(playerWins >= 5 ? "victory" : "coin");
            game.log(resultMessage, MessageLog.Type.LOOT);
        } else {
            resultMessage = "Defeated. Lost " + overlay.betAmount + "g.";
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
        drawCentered(g, "GRAND TOURNAMENT", px, py + 30, pw);

        switch (state) {
            case BET           -> paintBet(g, px, py, pw, ph);
            case OPPONENT_SHOW -> paintDuel(g, px, py, pw, ph, true);
            case PLAYER_PICK   -> paintDuel(g, px, py, pw, ph, false);
            case RESOLVE       -> paintResolve(g, px, py, pw, ph);
            case RESULT        -> paintResult(g, px, py, pw, ph);
        }
    }

    private void paintBet(Graphics2D g, int px, int py, int pw, int ph) {
        g.setFont(F_MENU);
        g.setColor(TEXT_BRIGHT);
        drawCentered(g, "Watch the opponent's move, then counter it!", px, py + 70, pw);
        drawCentered(g, "Block beats Attack   Attack beats Dodge   Dodge beats Block", px, py + 90, pw);

        g.setFont(F_BIG);
        g.setColor(BATTLE_GOLD);
        drawCentered(g, "Bet: " + overlay.betAmount + "g", px, py + ph / 2, pw);

        g.setFont(F_ITEM);
        g.setColor(AMBER);
        drawCentered(g, "Gold: " + game.getPlayer().getGold() + "g", px, py + ph / 2 + 30, pw);

        g.setFont(F_SMALL);
        g.setColor(TEXT_DIM);
        int dex = game.getPlayer().getDex();
        long base = BASE_REVEAL_MS + Math.max(0, dex - 10) * 40L;
        drawCentered(g, "DEX " + dex + " (reveal window: " + base + "ms base)", px, py + ph / 2 + 60, pw);

        g.setFont(F_KEY);
        g.setColor(TEXT_DIM);
        g.drawString("[Left/Right] Bet   [Shift] x10   [Enter] Start   [Esc] Back", px + 20, py + ph - 20);
    }

    private void paintDuel(Graphics2D g, int px, int py, int pw, int ph, boolean showOpponent) {
        // Round / wins tracker
        g.setFont(F_ITEM);
        g.setColor(BATTLE_GOLD);
        g.drawString("Round: " + round + "/" + ROUNDS + "  Wins: " + playerWins, px + 20, py + 50);

        // Arena backdrop
        int arenaX = px + pw / 4;
        int arenaW = pw / 2;
        int arenaY = py + 65;
        int arenaH = ph - 140;
        g.setColor(BLOOD_DARK);
        g.fillRect(arenaX, arenaY, arenaW, arenaH);
        g.setColor(WAR_RED);
        g.drawRect(arenaX, arenaY, arenaW, arenaH);

        // Opponent silhouette (top half)
        int midX = arenaX + arenaW / 2;
        int oppY = arenaY + arenaH / 4;
        drawCombatant(g, midX, oppY, WAR_RED, "OPP");

        // Opponent move box
        int boxW = 120, boxH = 36;
        int boxX = midX - boxW / 2;
        int boxY = oppY + 30;
        if (showOpponent) {
            g.setColor(opponentMove.col);
            g.fillRoundRect(boxX, boxY, boxW, boxH, 8, 8);
            g.setFont(F_MENU);
            g.setColor(Color.WHITE);
            drawCentered(g, opponentMove.label, boxX, boxY + boxH / 2 + 6, boxW);
        } else {
            g.setColor(new Color(60, 20, 20));
            g.fillRoundRect(boxX, boxY, boxW, boxH, 8, 8);
            g.setColor(IRON_GREY);
            g.drawRoundRect(boxX, boxY, boxW, boxH, 8, 8);
            g.setFont(F_MENU);
            g.setColor(IRON_GREY);
            drawCentered(g, "???", boxX, boxY + boxH / 2 + 6, boxW);
        }

        // VS divider
        g.setFont(F_BIG);
        g.setColor(BATTLE_GOLD);
        drawCentered(g, "VS", arenaX, arenaY + arenaH / 2 + 6, arenaW);

        // Player silhouette (bottom half)
        int plyY = arenaY + 3 * arenaH / 4 - 20;
        drawCombatant(g, midX, plyY, IRON_GREY, "YOU");

        // Prompt / countdown
        if (showOpponent) {
            long elapsed = System.currentTimeMillis() - revealStart;
            long remaining = Math.max(0, revealMs() - elapsed);
            g.setFont(F_MENU);
            g.setColor(remaining < 200 ? DANGER : TEXT_BRIGHT);
            drawCentered(g, "Memorize! " + (remaining / 100 + 1) * 100 / 1000.0 + "s", px, py + ph - 45, pw);
        } else {
            long elapsed = System.currentTimeMillis() - pickStart;
            long remaining = Math.max(0, PICK_TIMEOUT_MS - elapsed);
            g.setFont(F_SMALL);
            g.setColor(remaining < 800 ? DANGER : TEXT_DIM);
            drawCentered(g, "Counter! " + (remaining / 100) / 10.0 + "s", px, py + ph - 55, pw);
        }

        // Key hints
        g.setFont(F_KEY);
        g.setColor(showOpponent ? TEXT_DIM : TEXT_BRIGHT);
        drawCentered(g, "[Left] BLOCK   [Down] DODGE   [Right] ATTACK", px, py + ph - 20, pw);
    }

    private void drawCombatant(Graphics2D g, int cx, int cy, Color col, String label) {
        // Simple stick figure silhouette
        g.setColor(col);
        g.fillOval(cx - 9, cy - 20, 18, 18);        // head
        g.drawLine(cx, cy - 2, cx, cy + 18);         // body
        g.drawLine(cx - 12, cy + 6, cx + 12, cy + 6); // arms
        g.drawLine(cx, cy + 18, cx - 10, cy + 32);   // left leg
        g.drawLine(cx, cy + 18, cx + 10, cy + 32);   // right leg
        g.setFont(F_SMALL);
        g.setColor(col);
        int tw = g.getFontMetrics().stringWidth(label);
        g.drawString(label, cx - tw / 2, cy - 24);
    }

    private void paintResolve(Graphics2D g, int px, int py, int pw, int ph) {
        // Round result banner
        String outcome;
        Color outcomeCol;
        if (roundTied) {
            outcome = "DRAW!";
            outcomeCol = TIE_COL;
        } else if (roundWon) {
            outcome = "YOU WIN THE ROUND!";
            outcomeCol = WIN_COL;
        } else {
            outcome = playerMove == null ? "TOO SLOW!" : "DEFEATED!";
            outcomeCol = LOSE_COL;
        }

        g.setFont(F_BIG);
        g.setColor(outcomeCol);
        drawCentered(g, outcome, px, py + ph / 2 - 20, pw);

        // Show what happened
        if (playerMove != null) {
            g.setFont(F_MENU);
            g.setColor(IRON_GREY);
            drawCentered(g, "Opponent: " + opponentMove.label + "   You: " + playerMove.label,
                    px, py + ph / 2 + 15, pw);
            if (!roundWon && !roundTied) {
                g.setColor(TEXT_DIM);
                drawCentered(g, "Counter was: " + counterFor(opponentMove).label,
                        px, py + ph / 2 + 40, pw);
            }
        }

        // Wins so far
        g.setFont(F_ITEM);
        g.setColor(BATTLE_GOLD);
        drawCentered(g, "Round " + round + "/" + ROUNDS + "  Wins: " + playerWins, px, py + 50, pw);
    }

    private void paintResult(Graphics2D g, int px, int py, int pw, int ph) {
        g.setFont(F_BIG);
        g.setColor(WAR_RED);
        drawCentered(g, "Wins: " + playerWins + "/" + ROUNDS, px, py + 130, pw);

        g.setFont(F_MENU);
        boolean won = resultMessage.contains("Won") || resultMessage.contains("CHAMPION");
        g.setColor(won ? GOOD : DANGER);
        drawCentered(g, resultMessage, px, py + 175, pw);

        g.setFont(F_KEY);
        g.setColor(TEXT_DIM);
        drawCentered(g, "[Any key] Continue", px, py + ph - 20, pw);
    }
}

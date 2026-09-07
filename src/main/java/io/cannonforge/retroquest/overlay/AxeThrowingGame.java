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
 * Axe Throwing mini-game: two-phase aim (horizontal then vertical) at a
 * wooden shield target. Three throws, each faster. DEX slows the bars;
 * STR can bump score up a tier.
 */
class AxeThrowingGame implements MiniGame {

    private enum State { BET, AIM_X, AIM_Y, LAND, RESULT }

    // Target colours (volcanic shield)
    private static final Color SHIELD_WOOD  = new Color( 80,  55,  25);
    private static final Color SHIELD_RIM   = new Color(100,  80,  40);
    private static final Color RING_OUTER   = new Color(120,  50,  20);
    private static final Color RING_MIDDLE  = new Color(180,  90,  30);
    private static final Color RING_INNER   = new Color(220, 150,  40);
    private static final Color RING_BULLS   = new Color(255, 200,  60);
    private static final Color AXE_HANDLE   = new Color( 90,  60,  25);
    private static final Color AXE_BLADE    = new Color(160, 160, 170);

    private static final int BOARD_RADIUS = 130;
    private static final int BULLS_R      =  10;   // tightened: a bullseye must stay ~1-in-2 for an expert
    private static final int INNER_R      =  36;
    private static final int MIDDLE_R     =  75;
    private static final int OUTER_R      = 110;
    private static final int TOTAL_THROWS = 3;

    // ── Payout ladder ─────────────────────────────────────────────────────────
    // Absolute score tiers (max 150 = three bullseyes); values are the multiple
    // of the stake RETURNED (1.0 = break even, 0 = stake lost).
    private static final int SCORE_TOP   = 150, SCORE_GREAT = 125, SCORE_GOOD = 100, SCORE_WEAK = 75;
    private static final float PAY_TOP   = 2.5f, PAY_GREAT = 1.3f, PAY_GOOD = 0.7f, PAY_WEAK = 0.3f;
    private static final long LAND_MS     = 700;

    private final Retroquest game;
    private final ForgeArenaOverlay overlay;
    private final Random rng;

    private State state = State.BET;
    private int throwNum = 0;
    private int lockedX = 0;
    private int lockedY = 0;
    private long barStartTime = 0;
    private int[] scores = new int[TOTAL_THROWS];
    private int[][] hits = new int[TOTAL_THROWS][2];
    private long landTime = 0;
    private float baseSpeed = 2.5f;
    private String resultMessage = "";

    AxeThrowingGame(Retroquest game, ForgeArenaOverlay overlay, Random rng) {
        this.game = game;
        this.overlay = overlay;
        this.rng = rng;
    }

    @Override
    public void reset(int betAmount) {
        state = State.BET;
        throwNum = 0;
        resultMessage = "";
        for (int i = 0; i < TOTAL_THROWS; i++) { scores[i] = 0; hits[i][0] = 0; hits[i][1] = 0; }
    }

    @Override
    public boolean isShowingResult() { return state == State.RESULT; }

    @Override
    public void handleKey(KeyEvent e) {
        switch (state) {
            case BET        -> handleBetKey(e);
            case AIM_X, AIM_Y -> handleAimKey(e);
            case LAND       -> {}
            case RESULT     -> handleResultKey(e);
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
                startThrow();
            }
            case KeyEvent.VK_ESCAPE -> { overlay.backToMenu(); SoundManager.getInstance().play("menublip"); }
        }
    }

    private void handleAimKey(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_ENTER || e.getKeyCode() == KeyEvent.VK_SPACE) {
            long elapsed = System.currentTimeMillis() - barStartTime;
            float dexMod = 1.0f - (game.getPlayer().getDex() - 10) * 0.03f;
            dexMod = Math.max(0.6f, Math.min(1.4f, dexMod));
            float speed = (baseSpeed + throwNum * 0.5f) * dexMod;

            if (state == State.AIM_X) {
                lockedX = barPosToBoard(elapsed, speed);
                SoundManager.getInstance().playTone(500, 40, 0.25f);
                barStartTime = System.currentTimeMillis();
                state = State.AIM_Y;
            } else {
                lockedY = barPosToBoard(elapsed, speed);
                SoundManager.getInstance().play("axethrow");
                hits[throwNum][0] = lockedX;
                hits[throwNum][1] = lockedY;
                scores[throwNum] = scoreHit(lockedX, lockedY);
                landTime = System.currentTimeMillis();
                state = State.LAND;
            }
        } else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
            resolveGame();
        }
    }

    private void handleResultKey(KeyEvent e) {
        overlay.backToMenu();
        if (game.getStatsPanel() != null) game.getStatsPanel().refresh();
    }

    // ── Logic ─────────────────────────────────────────────────────────────────

    private void startThrow() {
        barStartTime = System.currentTimeMillis();
        state = State.AIM_X;
    }

    private int barPosToBoard(long elapsed, float speed) {
        double t = elapsed * speed * 0.001;
        return (int)(Math.sin(t) * BOARD_RADIUS);
    }

    private int scoreHit(int bx, int by) {
        double dist = Math.sqrt((double)bx * bx + (double)by * by);

        int rawScore;
        if (dist <= BULLS_R)       rawScore = 50;
        else if (dist <= INNER_R)  rawScore = 25;
        else if (dist <= MIDDLE_R) rawScore = 10;
        else if (dist <= OUTER_R)  rawScore = 5;
        else                       rawScore = 0;

        // STR bonus: chance to bump up one tier if within inner ring
        if (rawScore > 0 && rawScore < 50) {
            int strBonus = game.getPlayer().getStr() - 10;
            if (strBonus > 0 && rng.nextInt(100) < strBonus * 5) {
                rawScore = switch (rawScore) {
                    case 5  -> 10;
                    case 10 -> 25;
                    case 25 -> 50;
                    default -> rawScore;
                };
            }
        }
        return rawScore;
    }

    private void resolveGame() {
        int total = 0;
        for (int s : scores) total += s;
        int maxPossible = 50 * TOTAL_THROWS;

        int payout = 0;
        if      (total >= SCORE_TOP)   payout = (int)(overlay.betAmount * PAY_TOP);
        else if (total >= SCORE_GREAT) payout = (int)(overlay.betAmount * PAY_GREAT);
        else if (total >= SCORE_GOOD)  payout = (int)(overlay.betAmount * PAY_GOOD);
        else if (total >= SCORE_WEAK)  payout = (int)(overlay.betAmount * PAY_WEAK);

        if (payout > 0) {
            game.getPlayer().addGold(payout);
            if (total >= maxPossible) {
                resultMessage = "TRIPLE BULLSEYE! Won " + payout + "g!";
                SoundManager.getInstance().play("victory");
            } else {
                resultMessage = "You won " + payout + "g!";
                SoundManager.getInstance().play("coin");
            }
            game.log(resultMessage, MessageLog.Type.LOOT);
        } else {
            resultMessage = "All axes missed! You lost " + overlay.betAmount + "g.";
            SoundManager.getInstance().play("hurt");
            game.log(resultMessage, MessageLog.Type.DANGER);
        }
        game.getPlayer().recordGamblingResult(payout - overlay.betAmount);
        state = State.RESULT;
    }

    @Override
    public void update() {
        if (state == State.LAND && System.currentTimeMillis() - landTime > LAND_MS) {
            throwNum++;
            if (throwNum >= TOTAL_THROWS) resolveGame();
            else startThrow();
        }
    }

    @Override
    public void paint(Graphics2D g, int px, int py, int pw, int ph) {
        g.setFont(F_TITLE);
        g.setColor(FORGE_GOLD);
        drawCentered(g, "AXE THROWING", px, py + 30, pw);

        switch (state) {
            case BET         -> paintBet(g, px, py, pw, ph);
            case AIM_X, AIM_Y -> paintAim(g, px, py, pw, ph);
            case LAND        -> paintLand(g, px, py, pw, ph);
            case RESULT      -> paintResult(g, px, py, pw, ph);
        }
    }

    private void paintBet(Graphics2D g, int px, int py, int pw, int ph) {
        g.setFont(F_MENU);
        g.setColor(TEXT_BRIGHT);
        drawCentered(g, "Three throws — aim true!", px, py + 70, pw);

        g.setFont(F_BIG);
        g.setColor(FORGE_GOLD);
        drawCentered(g, "Bet: " + overlay.betAmount + "g", px, py + ph / 2, pw);

        g.setFont(F_ITEM);
        g.setColor(AMBER);
        drawCentered(g, "Gold: " + game.getPlayer().getGold() + "g", px, py + ph / 2 + 30, pw);

        g.setFont(F_SMALL);
        g.setColor(TEXT_DIM);
        drawCentered(g, "DEX " + game.getPlayer().getDex() + " (aim)   STR " + game.getPlayer().getStr() + " (power)", px, py + ph / 2 + 60, pw);

        g.setFont(F_KEY);
        g.setColor(TEXT_DIM);
        g.drawString("[Left/Right] Bet   [Shift] x10   [Enter] Throw!   [Esc] Back", px + 20, py + ph - 20);
    }

    private void paintAim(Graphics2D g, int px, int py, int pw, int ph) {
        int cx = px + pw / 2;
        int cy = py + ph / 2 - 20;

        paintScoreStrip(g, px + 20, py + 55);
        paintTarget(g, cx, cy);
        paintPreviousHits(g, cx, cy);

        // Oscillating bar
        long elapsed = System.currentTimeMillis() - barStartTime;
        float dexMod = 1.0f - (game.getPlayer().getDex() - 10) * 0.03f;
        dexMod = Math.max(0.6f, Math.min(1.4f, dexMod));
        float speed = (baseSpeed + throwNum * 0.5f) * dexMod;
        int barPos = barPosToBoard(elapsed, speed);

        g.setColor(Color.WHITE);
        if (state == State.AIM_X) {
            // Vertical crosshair moves horizontally
            g.fillRect(cx + barPos - 1, cy - BOARD_RADIUS - 10, 3, BOARD_RADIUS * 2 + 20);
            g.setFont(F_KEY);
            g.setColor(TEXT_DIM);
            drawCentered(g, "[Enter/Space] Lock horizontal aim", px, py + ph - 20, pw);
        } else {
            // Locked X line
            g.setColor(new Color(255, 255, 255, 100));
            g.fillRect(cx + lockedX - 1, cy - BOARD_RADIUS - 10, 3, BOARD_RADIUS * 2 + 20);
            // Horizontal crosshair moves vertically
            g.setColor(Color.WHITE);
            g.fillRect(cx - BOARD_RADIUS - 10, cy + barPos - 1, BOARD_RADIUS * 2 + 20, 3);
            g.setFont(F_KEY);
            g.setColor(TEXT_DIM);
            drawCentered(g, "[Enter/Space] Lock vertical aim", px, py + ph - 20, pw);
        }
    }

    private void paintLand(Graphics2D g, int px, int py, int pw, int ph) {
        int cx = px + pw / 2;
        int cy = py + ph / 2 - 20;

        paintScoreStrip(g, px + 20, py + 55);
        paintTarget(g, cx, cy);
        paintPreviousHits(g, cx, cy);

        // Draw landed axe at hit position
        int hitX = cx + hits[throwNum][0];
        int hitY = cy + hits[throwNum][1];
        drawAxe(g, hitX, hitY);

        // Score feedback
        g.setFont(F_BIG);
        String label = scores[throwNum] >= 50 ? "BULLSEYE!" : scores[throwNum] >= 25 ? "Inner ring!" :
                       scores[throwNum] >= 10 ? "Middle ring" : scores[throwNum] >= 5 ? "Outer ring" : "Miss!";
        g.setColor(scores[throwNum] >= 25 ? FORGE_GOLD : scores[throwNum] >= 5 ? AMBER : DANGER);
        drawCentered(g, label + " (+" + scores[throwNum] + ")", px, py + ph - 50, pw);
    }

    private void paintResult(Graphics2D g, int px, int py, int pw, int ph) {
        int cx = px + pw / 2;
        int cy = py + ph / 2 - 40;

        paintTarget(g, cx, cy);
        // Show all hits
        for (int i = 0; i < TOTAL_THROWS; i++) {
            if (scores[i] > 0) drawAxe(g, cx + hits[i][0], cy + hits[i][1]);
        }

        int total = 0;
        for (int s : scores) total += s;

        g.setFont(F_BIG);
        g.setColor(FORGE_GOLD);
        drawCentered(g, "Total: " + total + " pts", px, py + ph - 80, pw);

        g.setFont(F_MENU);
        g.setColor(resultMessage.contains("won") || resultMessage.contains("BULLSEYE") ? GOOD : DANGER);
        drawCentered(g, resultMessage, px, py + ph - 50, pw);

        g.setFont(F_KEY);
        g.setColor(TEXT_DIM);
        drawCentered(g, "[Any key] Continue", px, py + ph - 20, pw);
    }

    // ── Drawing helpers ───────────────────────────────────────────────────────

    private void paintTarget(Graphics2D g, int cx, int cy) {
        // Wooden shield background
        g.setColor(SHIELD_WOOD);
        g.fillOval(cx - BOARD_RADIUS, cy - BOARD_RADIUS, BOARD_RADIUS * 2, BOARD_RADIUS * 2);
        g.setColor(SHIELD_RIM);
        g.drawOval(cx - BOARD_RADIUS, cy - BOARD_RADIUS, BOARD_RADIUS * 2, BOARD_RADIUS * 2);

        // Concentric scoring rings
        g.setColor(RING_OUTER);
        g.fillOval(cx - OUTER_R, cy - OUTER_R, OUTER_R * 2, OUTER_R * 2);
        g.setColor(RING_MIDDLE);
        g.fillOval(cx - MIDDLE_R, cy - MIDDLE_R, MIDDLE_R * 2, MIDDLE_R * 2);
        g.setColor(RING_INNER);
        g.fillOval(cx - INNER_R, cy - INNER_R, INNER_R * 2, INNER_R * 2);
        g.setColor(RING_BULLS);
        g.fillOval(cx - BULLS_R, cy - BULLS_R, BULLS_R * 2, BULLS_R * 2);

        // Ring labels
        g.setFont(F_SMALL);
        g.setColor(new Color(0, 0, 0, 150));
        g.drawString("50", cx - 6, cy + 4);
    }

    private void paintPreviousHits(Graphics2D g, int cx, int cy) {
        for (int i = 0; i < throwNum; i++) {
            drawAxe(g, cx + hits[i][0], cy + hits[i][1]);
        }
    }

    private void drawAxe(Graphics2D g, int x, int y) {
        // Handle
        g.setColor(AXE_HANDLE);
        g.fillRect(x - 1, y - 10, 3, 16);
        // Blade
        g.setColor(AXE_BLADE);
        g.fillRect(x - 5, y - 10, 6, 4);
        g.fillRect(x - 6, y - 8, 2, 2);
    }

    private void paintScoreStrip(Graphics2D g, int x, int y) {
        g.setFont(F_SMALL);
        for (int i = 0; i < TOTAL_THROWS; i++) {
            boolean done = (state == State.LAND || state == State.RESULT) ? i <= throwNum : i < throwNum;
            g.setColor(done ? (scores[i] >= 40 ? GOOD : scores[i] >= 10 ? AMBER : DANGER) : TEXT_DIM);
            g.drawString("Throw " + (i + 1) + ": " + (done ? scores[i] + " pts" : "---"), x + i * 120, y);
        }
    }
}

package io.cannonforge.retroquest.overlay;

import static io.cannonforge.retroquest.overlay.CasinoOverlay.F_BIG;
import static io.cannonforge.retroquest.overlay.CasinoOverlay.F_MENU;
import static io.cannonforge.retroquest.overlay.CasinoOverlay.F_TITLE;
import static io.cannonforge.retroquest.overlay.CasinoOverlay.GOLD_COL;
import static io.cannonforge.retroquest.overlay.CasinoOverlay.drawCentered;
import static io.cannonforge.retroquest.overlay.CasinoOverlay.drawCenteredAt;
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
 * Dragon's Darts mini-game for the casino overlay.
 */
class DragonsDartsGame implements MiniGame {

    private enum State { BET, THROW_Y, THROW_X, LAND, RESULT }

    // Board colours
    private static final Color BOARD_DARK  = new Color( 30,  60,  30);
    private static final Color BOARD_LIGHT = new Color( 50,  90,  50);
    private static final Color BOARD_WIRE  = new Color(140, 140, 120);
    private static final Color BULLS_RED   = new Color(180,  30,  30);
    private static final Color BULLS_GOLD  = new Color(220, 190,  40);
    private static final Color DART_COL    = new Color(255, 100,  60);

    private static final int DD_BOARD_RADIUS = 140;
    private static final int DD_BULLS_R      =  11;   // tightened: keep the bullseye ~1-in-2 for an expert
    private static final int DD_INNER_R      =  46;

    // ── Payout ladder ─────────────────────────────────────────────────────────
    // Absolute score tiers (max 150 = three bullseyes); values are the multiple
    // of the stake RETURNED (1.0 = break even, 0 = stake lost).
    private static final int   DD_SCORE_TOP = 150, DD_SCORE_GREAT = 125, DD_SCORE_GOOD = 100, DD_SCORE_WEAK = 75;
    private static final float DD_PAY_TOP   = 2.5f, DD_PAY_GREAT = 1.3f, DD_PAY_GOOD  = 0.7f, DD_PAY_WEAK  = 0.3f;
    private static final int DD_OUTER_R      =  95;
    private static final long DD_LAND_MS     = 600;

    private final Retroquest game;
    private final CasinoOverlay overlay;
    @SuppressWarnings("unused")
    private final Random rng;

    private State state = State.BET;

    private int ddThrow = 0;
    private int ddLockedY = 0;
    private int ddLockedX = 0;
    private long ddBarStartTime = 0;
    private int[] ddScores = new int[3];
    private int[][] ddHits = new int[3][2];
    private long ddLandTime = 0;
    private String ddResultMessage = "";
    private float ddBaseSpeed = 2.8f;

    DragonsDartsGame(Retroquest game, CasinoOverlay overlay, Random rng) {
        this.game = game;
        this.overlay = overlay;
        this.rng = rng;
    }

    @Override
    public void reset(int betAmount) {
        state = State.BET;
        ddThrow = 0;
        ddResultMessage = "";
        for (int i = 0; i < 3; i++) { ddScores[i] = 0; ddHits[i][0] = 0; ddHits[i][1] = 0; }
    }

    @Override
    public boolean isShowingResult() {
        return state == State.RESULT;
    }

    @Override
    public void handleKey(KeyEvent e) {
        switch (state) {
            case BET -> handleBetKey(e);
            case THROW_Y, THROW_X -> handleThrowKey(e);
            case LAND -> {} // no input during landing
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
                startThrow();
            }
            case KeyEvent.VK_ESCAPE -> { overlay.backToMenu(); SoundManager.getInstance().play("menublip"); }
        }
    }

    private void handleThrowKey(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_ENTER || e.getKeyCode() == KeyEvent.VK_SPACE) {
            if (state == State.THROW_Y) {
                long elapsed = System.currentTimeMillis() - ddBarStartTime;
                float speed = ddBaseSpeed + ddThrow * 0.6f;
                ddLockedY = barPosToBoard(elapsed, speed);
                SoundManager.getInstance().playTone(500, 40, 0.25f);
                ddBarStartTime = System.currentTimeMillis();
                state = State.THROW_X;
            } else if (state == State.THROW_X) {
                long elapsed = System.currentTimeMillis() - ddBarStartTime;
                float speed = ddBaseSpeed + ddThrow * 0.6f;
                ddLockedX = barPosToBoard(elapsed, speed);
                SoundManager.getInstance().playTone(700, 60, 0.3f);
                ddHits[ddThrow][0] = ddLockedX;
                ddHits[ddThrow][1] = ddLockedY;
                ddScores[ddThrow] = scoreDart(ddLockedX, ddLockedY);
                ddLandTime = System.currentTimeMillis();
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

    // ── Logic ───────────────────────────────────────────────────────────────────

    private void startThrow() {
        ddBarStartTime = System.currentTimeMillis();
        state = State.THROW_Y;
    }

    private int barPosToBoard(long elapsed, float speed) {
        double t = elapsed * speed * 0.001;
        return (int)(Math.sin(t) * DD_BOARD_RADIUS);
    }

    private int scoreDart(int bx, int by) {
        double dist = Math.sqrt((double)bx * bx + (double)by * by);
        if (dist <= DD_BULLS_R)  return 50;
        if (dist <= DD_INNER_R)  return 25;
        if (dist <= DD_OUTER_R)  return 10;
        if (dist <= DD_BOARD_RADIUS) return 5;
        return 0;
    }

    private void resolveGame() {
        int total = 0;
        for (int s : ddScores) total += s;

        int payout = 0;
        if      (total >= DD_SCORE_TOP)   payout = (int)(overlay.betAmount * DD_PAY_TOP);
        else if (total >= DD_SCORE_GREAT) payout = (int)(overlay.betAmount * DD_PAY_GREAT);
        else if (total >= DD_SCORE_GOOD)  payout = (int)(overlay.betAmount * DD_PAY_GOOD);
        else if (total >= DD_SCORE_WEAK)  payout = (int)(overlay.betAmount * DD_PAY_WEAK);

        if (payout > 0) {
            game.getPlayer().addGold(payout);
            if (total >= 150) {
                ddResultMessage = "TRIPLE BULLSEYE! +" + payout + "g!";
                SoundManager.getInstance().play("victory");
            } else {
                ddResultMessage = "Score: " + total + "  +" + payout + "g!";
                SoundManager.getInstance().play("coin");
            }
        } else {
            ddResultMessage = "Score: " + total + ". Better luck next time!";
            SoundManager.getInstance().play("hurt");
        }

        game.getPlayer().recordGamblingResult(payout - overlay.betAmount);
        state = State.RESULT;
        if (game.getStatsPanel() != null) game.getStatsPanel().refresh();
    }

    // ── Update ──────────────────────────────────────────────────────────────────

    @Override
    public void update() {
        if (state == State.LAND) updateLand();
    }

    private void updateLand() {
        long elapsed = System.currentTimeMillis() - ddLandTime;
        if (elapsed >= DD_LAND_MS) {
            ddThrow++;
            if (ddThrow >= 3) {
                resolveGame();
            } else {
                startThrow();
            }
        }
    }

    // ── Paint ───────────────────────────────────────────────────────────────────

    @Override
    public void paint(Graphics2D g, int px, int py, int pw, int ph) {
        switch (state) {
            case BET -> paintBet(g, px, py, pw, ph);
            case THROW_Y, THROW_X -> paintThrow(g, px, py, pw, ph);
            case LAND -> paintLand(g, px, py, pw, ph);
            case RESULT -> paintResult(g, px, py, pw, ph);
        }
    }

    private void paintBet(Graphics2D g, int px, int py, int pw, int ph) {
        g.setFont(F_TITLE);
        g.setColor(GOLD_COL);
        drawCentered(g, "DRAGON'S DARTS", px, py + 30, pw);

        g.setFont(F_MENU);
        g.setColor(AMBER);
        drawCentered(g, "Bet: " + overlay.betAmount + "g", px, py + 80, pw);

        g.setFont(F_ITEM);
        g.setColor(TEXT_DIM);
        drawCentered(g, "Left/Right to adjust (Shift for x10)", px, py + 105, pw);
        drawCentered(g, "Gold: " + game.getPlayer().getGold() + "g", px, py + 130, pw);

        // Rules
        g.setFont(F_SMALL);
        g.setColor(TEXT_DIM);
        int ry = py + 170;
        g.drawString("  3 throws at the dartboard!", px + 60, ry);
        g.drawString("  Lock Y-axis, then X-axis for each throw.", px + 60, ry + 18);
        ry += 44;
        g.setColor(BULLS_GOLD);
        g.drawString("    Bullseye = 50 pts", px + 60, ry);
        g.setColor(GOOD);
        g.drawString("    Inner Ring = 25 pts", px + 60, ry + 18);
        g.setColor(TEXT_BRIGHT);
        g.drawString("    Outer Ring = 10 pts", px + 60, ry + 36);
        g.setColor(TEXT_DIM);
        g.drawString("    Edge = 5 pts", px + 60, ry + 54);
        ry += 78;
        g.setColor(TEXT_DIM);
        g.drawString("  Payouts:", px + 60, ry);
        g.setColor(BULLS_GOLD);
        g.drawString("    150 pts = 2.5x  ", px + 60, ry + 18);
        g.setColor(GOOD);
        g.drawString("    125+ = 1.3x   100+ = 0.7x   75+ = 0.3x   under 75 = lost", px + 60, ry + 36);

        g.setFont(F_KEY);
        g.setColor(TEXT_DIM);
        drawCentered(g, "[Enter] Throw   [Esc] Back", px, py + ph - 20, pw);
    }

    private void paintThrow(Graphics2D g, int px, int py, int pw, int ph) {
        g.setFont(F_TITLE);
        g.setColor(GOLD_COL);
        drawCentered(g, "DRAGON'S DARTS", px, py + 25, pw);

        g.setFont(F_ITEM);
        g.setColor(AMBER);
        drawCentered(g, "Throw " + (ddThrow + 1) + " of 3   Bet: " + overlay.betAmount + "g", px, py + 48, pw);

        int totalSoFar = 0;
        for (int i = 0; i < ddThrow; i++) totalSoFar += ddScores[i];
        if (ddThrow > 0) {
            g.setFont(F_SMALL);
            g.setColor(TEXT_DIM);
            drawCentered(g, "Score so far: " + totalSoFar, px, py + 65, pw);
        }

        int boardCX = px + pw / 2;
        int boardCY = py + 72 + DD_BOARD_RADIUS;
        paintDartboard(g, boardCX, boardCY);

        for (int i = 0; i < ddThrow; i++) {
            paintDart(g, boardCX + ddHits[i][0], boardCY + ddHits[i][1], 1.0f);
        }

        float speed = ddBaseSpeed + ddThrow * 0.6f;
        long elapsed = System.currentTimeMillis() - ddBarStartTime;

        if (state == State.THROW_Y) {
            int barY = boardCY + barPosToBoard(elapsed, speed);
            g.setColor(DART_COL);
            g.fillRect(boardCX - DD_BOARD_RADIUS - 10, barY - 1, DD_BOARD_RADIUS * 2 + 20, 3);
            g.setFont(F_KEY);
            g.setColor(TEXT_BRIGHT);
            drawCentered(g, "[Enter/Space] Lock Y-axis", px, py + ph - 20, pw);
        } else {
            int lockedBarY = boardCY + ddLockedY;
            g.setColor(new Color(255, 100, 60, 80));
            g.fillRect(boardCX - DD_BOARD_RADIUS - 10, lockedBarY - 1, DD_BOARD_RADIUS * 2 + 20, 3);

            int barX = boardCX + barPosToBoard(elapsed, speed);
            g.setColor(DART_COL);
            g.fillRect(barX - 1, boardCY - DD_BOARD_RADIUS - 10, 3, DD_BOARD_RADIUS * 2 + 20);

            g.setColor(Color.WHITE);
            g.fillRect(barX - 2, lockedBarY - 2, 5, 5);

            g.setFont(F_KEY);
            g.setColor(TEXT_BRIGHT);
            drawCentered(g, "[Enter/Space] Lock X-axis", px, py + ph - 20, pw);
        }
    }

    private void paintLand(Graphics2D g, int px, int py, int pw, int ph) {
        g.setFont(F_TITLE);
        g.setColor(GOLD_COL);
        drawCentered(g, "DRAGON'S DARTS", px, py + 25, pw);

        g.setFont(F_ITEM);
        g.setColor(AMBER);
        drawCentered(g, "Throw " + (ddThrow + 1) + " of 3   Bet: " + overlay.betAmount + "g", px, py + 48, pw);

        int boardCX = px + pw / 2;
        int boardCY = py + 72 + DD_BOARD_RADIUS;
        paintDartboard(g, boardCX, boardCY);

        float landProgress = Math.min(1f, (System.currentTimeMillis() - ddLandTime) / (float) DD_LAND_MS);
        for (int i = 0; i <= ddThrow && i < 3; i++) {
            float alpha = (i == ddThrow) ? landProgress : 1.0f;
            paintDart(g, boardCX + ddHits[i][0], boardCY + ddHits[i][1], alpha);
        }

        int score = ddScores[ddThrow];
        String scoreLabel = score == 50 ? "BULLSEYE!" : score == 25 ? "Inner Ring!" : score == 10 ? "Outer Ring" : "Edge";
        Color scoreColor = score >= 50 ? BULLS_GOLD : score >= 25 ? GOOD : TEXT_BRIGHT;

        int popupY = boardCY + ddHits[ddThrow][1] - (int)(20 * landProgress);
        g.setFont(F_MENU);
        g.setColor(scoreColor);
        int sw = g.getFontMetrics().stringWidth(scoreLabel);
        g.drawString(scoreLabel, boardCX + ddHits[ddThrow][0] - sw / 2, popupY - 10);

        g.setFont(F_BIG);
        g.setColor(GOLD_COL);
        String pts = "+" + score;
        int ptsW = g.getFontMetrics().stringWidth(pts);
        g.drawString(pts, boardCX + ddHits[ddThrow][0] - ptsW / 2, popupY + 10);
    }

    private void paintResult(Graphics2D g, int px, int py, int pw, int ph) {
        g.setFont(F_TITLE);
        g.setColor(GOLD_COL);
        drawCentered(g, "DRAGON'S DARTS", px, py + 25, pw);

        int boardCX = px + pw / 2;
        int boardCY = py + 65 + DD_BOARD_RADIUS;
        paintDartboard(g, boardCX, boardCY);

        for (int i = 0; i < 3; i++) {
            if (ddScores[i] > 0 || (ddHits[i][0] != 0 || ddHits[i][1] != 0)) {
                paintDart(g, boardCX + ddHits[i][0], boardCY + ddHits[i][1], 1.0f);
            }
        }

        int total = ddScores[0] + ddScores[1] + ddScores[2];
        int infoY = boardCY + DD_BOARD_RADIUS + 20;
        g.setFont(F_ITEM);
        g.setColor(TEXT_DIM);
        drawCentered(g, "Throws: " + ddScores[0] + " + " + ddScores[1] + " + " + ddScores[2] + " = " + total, px, infoY, pw);

        boolean isWin = ddResultMessage.contains("+");
        g.setFont(F_MENU);
        g.setColor(isWin ? GOOD : DANGER);
        drawCentered(g, ddResultMessage, px, infoY + 25, pw);

        g.setFont(F_ITEM);
        g.setColor(AMBER);
        drawCentered(g, "Gold: " + game.getPlayer().getGold() + "g", px, infoY + 50, pw);

        g.setFont(F_KEY);
        g.setColor(TEXT_DIM);
        drawCentered(g, "Press any key to continue", px, py + ph - 20, pw);
    }

    // ── Dartboard & dart rendering ──────────────────────────────────────────────

    private void paintDartboard(Graphics2D g, int cx, int cy) {
        g.setColor(BOARD_DARK);
        fillCircle(g, cx, cy, DD_BOARD_RADIUS);

        g.setColor(BOARD_WIRE);
        drawCircle(g, cx, cy, DD_BOARD_RADIUS);

        for (int angle = 0; angle < 360; angle += 36) {
            g.setColor((angle / 36) % 2 == 0 ? BOARD_DARK : BOARD_LIGHT);
            g.fillArc(cx - DD_OUTER_R, cy - DD_OUTER_R, DD_OUTER_R * 2, DD_OUTER_R * 2, angle, 36);
        }
        g.setColor(BOARD_WIRE);
        drawCircle(g, cx, cy, DD_OUTER_R);

        for (int angle = 0; angle < 360; angle += 36) {
            g.setColor((angle / 36) % 2 == 0 ? BOARD_LIGHT : BOARD_DARK);
            g.fillArc(cx - DD_INNER_R, cy - DD_INNER_R, DD_INNER_R * 2, DD_INNER_R * 2, angle, 36);
        }
        g.setColor(BOARD_WIRE);
        drawCircle(g, cx, cy, DD_INNER_R);

        g.setColor(BULLS_RED);
        fillCircle(g, cx, cy, DD_BULLS_R);
        g.setColor(BOARD_WIRE);
        drawCircle(g, cx, cy, DD_BULLS_R);

        g.setColor(BULLS_GOLD);
        fillCircle(g, cx, cy, DD_BULLS_R / 2);

        for (int angle = 0; angle < 360; angle += 36) {
            double rad = Math.toRadians(angle);
            int x2 = cx + (int)(DD_BOARD_RADIUS * Math.cos(rad));
            int y2 = cy + (int)(DD_BOARD_RADIUS * Math.sin(rad));
            g.setColor(BOARD_WIRE);
            g.drawLine(cx, cy, x2, y2);
        }

        g.setFont(F_SMALL);
        g.setColor(new Color(200, 200, 180, 120));
        drawCenteredAt(g, "50", cx, cy + 4);
        drawCenteredAt(g, "25", cx + DD_INNER_R / 2 + 8, cy + 4);
        drawCenteredAt(g, "10", cx + DD_OUTER_R / 2 + 12, cy + 4);
        drawCenteredAt(g, "5", cx + (DD_OUTER_R + DD_BOARD_RADIUS) / 2, cy + 4);
    }

    private void paintDart(Graphics2D g, int x, int y, float alpha) {
        int a = (int)(255 * alpha);
        g.setColor(new Color(180, 140, 80, a));
        g.fillRect(x - 1, y - 8, 3, 12);
        g.setColor(new Color(200, 200, 200, a));
        g.fillRect(x - 1, y + 3, 3, 4);
        g.fillRect(x, y + 6, 1, 2);
        g.setColor(new Color(200, 50, 50, a));
        g.fillRect(x - 4, y - 8, 3, 5);
        g.fillRect(x + 2, y - 8, 3, 5);
        g.setColor(new Color(255, 255, 200, a / 2));
        g.fillRect(x - 1, y - 1, 3, 3);
    }

    private void fillCircle(Graphics2D g, int cx, int cy, int r) {
        g.fillOval(cx - r, cy - r, r * 2, r * 2);
    }

    private void drawCircle(Graphics2D g, int cx, int cy, int r) {
        g.drawOval(cx - r, cy - r, r * 2, r * 2);
    }
}

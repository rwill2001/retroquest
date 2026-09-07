package io.cannonforge.retroquest.overlay;

import static io.cannonforge.retroquest.overlay.CasinoOverlay.F_BIG;
import static io.cannonforge.retroquest.overlay.CasinoOverlay.F_MENU;
import static io.cannonforge.retroquest.overlay.CasinoOverlay.F_TITLE;
import static io.cannonforge.retroquest.overlay.CasinoOverlay.GOLD_COL;
import static io.cannonforge.retroquest.overlay.CasinoOverlay.drawCentered;
import static io.cannonforge.retroquest.overlay.OverlayTheme.AMBER;
import static io.cannonforge.retroquest.overlay.OverlayTheme.CYAN_ACC;
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
 * Wizard's Dice (craps-style) mini-game for the casino overlay.
 */
class WizardsDiceGame implements MiniGame {

    private enum State { BET, ROLL, POINT_ROLL, RESULT }

    // Dice colours
    private static final Color DICE_WHITE  = new Color(230, 230, 220);
    private static final Color DICE_BORDER = new Color( 60,  50,  40);
    private static final Color DICE_PIP    = new Color( 30,  20,  15);

    private static final long WD_ROLL_DURATION = 1200;

    private final Retroquest game;
    private final CasinoOverlay overlay;
    private final Random rng;

    private State state = State.BET;

    private int wdDie1 = 0, wdDie2 = 0;
    private int wdPoint = 0;
    private long wdRollStartTime = 0;
    private int wdAnimFrame = 0;
    private int wdAnimDie1 = 1, wdAnimDie2 = 1;
    private String wdResultMessage = "";
    private boolean wdIsPointPhase = false;

    WizardsDiceGame(Retroquest game, CasinoOverlay overlay, Random rng) {
        this.game = game;
        this.overlay = overlay;
        this.rng = rng;
    }

    @Override
    public void reset(int betAmount) {
        state = State.BET;
        wdPoint = 0;
        wdIsPointPhase = false;
        wdResultMessage = "";
    }

    @Override
    public boolean isShowingResult() {
        // Only "final" results — not point-phase interim results
        return state == State.RESULT && !wdIsPointPhase;
    }

    @Override
    public void handleKey(KeyEvent e) {
        switch (state) {
            case BET -> handleBetKey(e);
            case ROLL, POINT_ROLL -> {} // no input during roll
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
                startRoll(false);
            }
            case KeyEvent.VK_ESCAPE -> { overlay.backToMenu(); SoundManager.getInstance().play("menublip"); }
        }
    }

    private void handleResultKey(KeyEvent e) {
        if (wdIsPointPhase) {
            startRoll(true);
        } else {
            overlay.backToMenu();
            if (game.getStatsPanel() != null) game.getStatsPanel().refresh();
        }
    }

    private void startRoll(boolean isPointRoll) {
        wdDie1 = rng.nextInt(6) + 1;
        wdDie2 = rng.nextInt(6) + 1;
        wdRollStartTime = System.currentTimeMillis();
        wdAnimFrame = 0;
        state = isPointRoll ? State.POINT_ROLL : State.ROLL;
    }

    // ── Logic ───────────────────────────────────────────────────────────────────

    private void resolveRoll() {
        int total = wdDie1 + wdDie2;

        if (!wdIsPointPhase) {
            if (total == 7 || total == 11) {
                int payout = overlay.betAmount * 2;
                game.getPlayer().addGold(payout);
                wdResultMessage = "Natural " + total + "! You win +" + payout + "g!";
                wdIsPointPhase = false;
                game.getPlayer().recordGamblingResult(payout - overlay.betAmount);
                SoundManager.getInstance().play("victory");
            } else if (total == 2 || total == 3 || total == 12) {
                wdResultMessage = "Craps! Rolled " + total + ". You lose " + overlay.betAmount + "g.";
                wdIsPointPhase = false;
                game.getPlayer().recordGamblingResult(-overlay.betAmount);
                SoundManager.getInstance().play("hurt");
            } else {
                wdPoint = total;
                wdIsPointPhase = true;
                wdResultMessage = "Point is " + total + "! Roll again to hit it. (7 = lose)";
                SoundManager.getInstance().play("menublip");
            }
        } else {
            if (total == wdPoint) {
                int payout = overlay.betAmount * 2;
                game.getPlayer().addGold(payout);
                wdResultMessage = "You hit the point (" + wdPoint + ")! +" + payout + "g!";
                wdIsPointPhase = false;
                game.getPlayer().recordGamblingResult(payout - overlay.betAmount);
                SoundManager.getInstance().play("victory");
            } else if (total == 7) {
                wdResultMessage = "Seven out! You lose " + overlay.betAmount + "g.";
                wdIsPointPhase = false;
                game.getPlayer().recordGamblingResult(-overlay.betAmount);
                SoundManager.getInstance().play("hurt");
            } else {
                wdResultMessage = "Rolled " + total + ". Point is still " + wdPoint + ". Roll again!";
                SoundManager.getInstance().play("menublip");
            }
        }

        state = State.RESULT;
        if (game.getStatsPanel() != null) game.getStatsPanel().refresh();
    }

    // ── Update ──────────────────────────────────────────────────────────────────

    @Override
    public void update() {
        if (state == State.ROLL || state == State.POINT_ROLL) updateRoll();
    }

    private void updateRoll() {
        long elapsed = System.currentTimeMillis() - wdRollStartTime;

        int frameDur = 60 + (int)(elapsed * 0.15f);
        int frame = (int)(elapsed / Math.max(60, frameDur));
        if (frame != wdAnimFrame) {
            wdAnimFrame = frame;
            wdAnimDie1 = rng.nextInt(6) + 1;
            wdAnimDie2 = rng.nextInt(6) + 1;
            SoundManager.getInstance().playTone(300 + rng.nextInt(200), 25, 0.18f);
        }

        if (elapsed >= WD_ROLL_DURATION) {
            wdAnimDie1 = wdDie1;
            wdAnimDie2 = wdDie2;
            resolveRoll();
        }
    }

    // ── Paint ───────────────────────────────────────────────────────────────────

    @Override
    public void paint(Graphics2D g, int px, int py, int pw, int ph) {
        switch (state) {
            case BET -> paintBet(g, px, py, pw, ph);
            case ROLL, POINT_ROLL -> paintRoll(g, px, py, pw, ph);
            case RESULT -> paintResult(g, px, py, pw, ph);
        }
    }

    private void paintBet(Graphics2D g, int px, int py, int pw, int ph) {
        g.setFont(F_TITLE);
        g.setColor(GOLD_COL);
        drawCentered(g, "WIZARD'S DICE", px, py + 30, pw);

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
        g.drawString("  Come-Out Roll:", px + 60, ry);
        g.setColor(GOOD);
        g.drawString("    7 or 11 = Natural Win (2:1)", px + 60, ry + 18);
        g.setColor(DANGER);
        g.drawString("    2, 3, or 12 = Craps (lose)", px + 60, ry + 36);
        g.setColor(TEXT_DIM);
        g.drawString("    Other = that number becomes The Point", px + 60, ry + 54);
        g.drawString("  Point Phase: keep rolling", px + 60, ry + 78);
        g.setColor(GOOD);
        g.drawString("    Roll the Point again = Win (2:1)", px + 60, ry + 96);
        g.setColor(DANGER);
        g.drawString("    Roll a 7 = Seven Out (lose)", px + 60, ry + 114);
        g.setColor(TEXT_DIM);
        g.drawString("    Anything else = roll again", px + 60, ry + 132);

        g.setFont(F_KEY);
        g.setColor(TEXT_DIM);
        drawCentered(g, "[Enter] Roll   [Esc] Back", px, py + ph - 20, pw);
    }

    private void paintRoll(Graphics2D g, int px, int py, int pw, int ph) {
        g.setFont(F_TITLE);
        g.setColor(GOLD_COL);
        drawCentered(g, "WIZARD'S DICE", px, py + 30, pw);

        if (wdIsPointPhase || state == State.POINT_ROLL) {
            g.setFont(F_ITEM);
            g.setColor(CYAN_ACC);
            drawCentered(g, "The Point: " + wdPoint, px, py + 55, pw);
        }

        g.setFont(F_MENU);
        g.setColor(TEXT_BRIGHT);
        drawCentered(g, "Rolling...", px, py + 85, pw);

        long elapsed = System.currentTimeMillis() - wdRollStartTime;
        int bounceY = (int)(Math.abs(Math.sin(elapsed * 0.012)) * 20);

        int diceSize = 80;
        int gap = 30;
        int totalW = diceSize * 2 + gap;
        int dx = px + (pw - totalW) / 2;
        int dy = py + 130 - bounceY;

        drawDie(g, wdAnimDie1, dx, dy, diceSize);
        drawDie(g, wdAnimDie2, dx + diceSize + gap, dy, diceSize);

        g.setFont(F_ITEM);
        g.setColor(AMBER);
        drawCentered(g, "Bet: " + overlay.betAmount + "g", px, py + ph - 40, pw);
    }

    private void paintResult(Graphics2D g, int px, int py, int pw, int ph) {
        g.setFont(F_TITLE);
        g.setColor(GOLD_COL);
        drawCentered(g, "WIZARD'S DICE", px, py + 30, pw);

        if (wdIsPointPhase) {
            g.setFont(F_ITEM);
            g.setColor(CYAN_ACC);
            drawCentered(g, "The Point: " + wdPoint, px, py + 55, pw);
        }

        int diceSize = 80;
        int gap = 30;
        int totalW = diceSize * 2 + gap;
        int dx = px + (pw - totalW) / 2;
        int dy = py + 100;

        drawDie(g, wdDie1, dx, dy, diceSize);
        drawDie(g, wdDie2, dx + diceSize + gap, dy, diceSize);

        int total = wdDie1 + wdDie2;
        g.setFont(F_BIG);
        g.setColor(TEXT_BRIGHT);
        drawCentered(g, "= " + total, px, dy + diceSize + 30, pw);

        boolean isWin = wdResultMessage.contains("win") || wdResultMessage.contains("Win")
                     || wdResultMessage.contains("hit the point");
        boolean keepRolling = wdIsPointPhase;

        g.setFont(F_MENU);
        g.setColor(isWin ? GOOD : keepRolling ? CYAN_ACC : DANGER);
        drawCentered(g, wdResultMessage, px, dy + diceSize + 65, pw);

        g.setFont(F_ITEM);
        g.setColor(AMBER);
        drawCentered(g, "Gold: " + game.getPlayer().getGold() + "g", px, dy + diceSize + 92, pw);

        g.setFont(F_KEY);
        g.setColor(TEXT_DIM);
        if (wdIsPointPhase) {
            drawCentered(g, "Press any key to roll again", px, py + ph - 20, pw);
        } else {
            drawCentered(g, "Press any key to continue", px, py + ph - 20, pw);
        }
    }

    // ── Dice drawing ────────────────────────────────────────────────────────────

    private void drawDie(Graphics2D g, int value, int x, int y, int size) {
        g.setColor(DICE_WHITE);
        g.fillRect(x, y, size, size);
        g.setColor(new Color(255, 255, 250));
        g.drawLine(x, y, x + size, y);
        g.drawLine(x, y, x, y + size);
        g.setColor(new Color(180, 175, 165));
        g.drawLine(x + size, y, x + size, y + size);
        g.drawLine(x, y + size, x + size, y + size);
        g.setColor(DICE_BORDER);
        g.drawRect(x - 1, y - 1, size + 2, size + 2);

        int pipR = size / 10;
        int cx = x + size / 2;
        int cy = y + size / 2;
        int off = size / 4;

        g.setColor(DICE_PIP);

        if (value == 1 || value == 3 || value == 5) {
            fillPip(g, cx, cy, pipR);
        }
        if (value >= 2) {
            fillPip(g, cx + off, cy - off, pipR);
            fillPip(g, cx - off, cy + off, pipR);
        }
        if (value >= 4) {
            fillPip(g, cx - off, cy - off, pipR);
            fillPip(g, cx + off, cy + off, pipR);
        }
        if (value == 6) {
            fillPip(g, cx - off, cy, pipR);
            fillPip(g, cx + off, cy, pipR);
        }
    }

    private void fillPip(Graphics2D g, int cx, int cy, int r) {
        g.fillOval(cx - r, cy - r, r * 2, r * 2);
    }
}

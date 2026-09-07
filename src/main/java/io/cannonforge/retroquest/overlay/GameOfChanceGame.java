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
 * Game of Chance (roulette-style wheel) mini-game for the casino overlay.
 */
class GameOfChanceGame implements MiniGame {

    private enum State { BET, SPIN, RESULT }

    // Wheel slot colours
    private static final Color WHEEL_RED  = new Color(180,  40,  40);
    private static final Color WHEEL_BLUE = new Color( 40,  60, 180);
    private static final Color WHEEL_GOLD = new Color(200, 170,  30);

    private static final int WHEEL_SLOTS = 20;
    private static final long SPIN_DURATION = 5000;

    private final Retroquest game;
    private final CasinoOverlay overlay;
    private final Random rng;

    private State state = State.BET;

    // Betting
    private int gocBetType = -1;      // 1-6
    private int gocPickedNumber = -1;  // for bet type 1
    private boolean pickingNumber = false;
    private String pickBuffer = "";

    // Spin animation
    private float wheelPosition = 0;
    private float wheelStartPos = 0;
    private float wheelTotalTravel = 0;
    private int targetSlot = 0;
    private long spinStartTime = 0;
    private int lastTickedSlot = -1;
    private String gocResultMessage = "";

    GameOfChanceGame(Retroquest game, CasinoOverlay overlay, Random rng) {
        this.game = game;
        this.overlay = overlay;
        this.rng = rng;
    }

    @Override
    public void reset(int betAmount) {
        state = State.BET;
        gocBetType = -1;
        pickingNumber = false;
        pickBuffer = "";
    }

    @Override
    public boolean isShowingResult() {
        return state == State.RESULT;
    }

    @Override
    public void handleKey(KeyEvent e) {
        switch (state) {
            case BET    -> handleBetKey(e);
            case SPIN   -> {} // no input during spin
            case RESULT -> handleResultKey(e);
        }
    }

    private void handleBetKey(KeyEvent e) {
        if (pickingNumber) {
            handlePickNumber(e);
            return;
        }

        int code = e.getKeyCode();
        boolean shift = e.isShiftDown();

        switch (code) {
            case KeyEvent.VK_LEFT  -> overlay.adjustBet(shift ? -10 : -1);
            case KeyEvent.VK_RIGHT -> overlay.adjustBet(shift ?  10 :  1);
            case KeyEvent.VK_1 -> { gocBetType = 1; pickingNumber = true; pickBuffer = ""; SoundManager.getInstance().play("menublip"); }
            case KeyEvent.VK_2 -> selectBetAndSpin(2);
            case KeyEvent.VK_3 -> selectBetAndSpin(3);
            case KeyEvent.VK_4 -> selectBetAndSpin(4);
            case KeyEvent.VK_5 -> selectBetAndSpin(5);
            case KeyEvent.VK_6 -> selectBetAndSpin(6);
            case KeyEvent.VK_ESCAPE -> { overlay.backToMenu(); SoundManager.getInstance().play("menublip"); }
        }
    }

    private void handlePickNumber(KeyEvent e) {
        int code = e.getKeyCode();
        if (code >= KeyEvent.VK_0 && code <= KeyEvent.VK_9) {
            pickBuffer += (char)('0' + (code - KeyEvent.VK_0));
            if (pickBuffer.length() >= 2) finishPickNumber();
        } else if (code == KeyEvent.VK_ENTER) {
            finishPickNumber();
        } else if (code == KeyEvent.VK_ESCAPE) {
            pickingNumber = false;
            gocBetType = -1;
        }
    }

    private void finishPickNumber() {
        try {
            int num = Integer.parseInt(pickBuffer);
            if (num >= 1 && num <= 20) {
                gocPickedNumber = num;
                pickingNumber = false;
                selectBetAndSpin(1);
                return;
            }
        } catch (NumberFormatException ignored) {}
        pickBuffer = "";
        game.log("Pick a number 1-20!", MessageLog.Type.DANGER);
    }

    private void selectBetAndSpin(int betType) {
        if (game.getPlayer().getGold() < overlay.betAmount) {
            game.log("Not enough gold!", MessageLog.Type.DANGER);
            return;
        }
        gocBetType = betType;
        game.getPlayer().addGold(-overlay.betAmount);
        overlay.lastBet = overlay.betAmount;
        SoundManager.getInstance().play("coin");

        targetSlot = rng.nextInt(WHEEL_SLOTS);
        wheelStartPos = rng.nextFloat() * WHEEL_SLOTS;
        float distToTarget = (targetSlot - wheelStartPos + WHEEL_SLOTS) % WHEEL_SLOTS;
        wheelTotalTravel = WHEEL_SLOTS * 5 + distToTarget;
        wheelPosition = wheelStartPos;
        lastTickedSlot = -1;
        spinStartTime = System.currentTimeMillis();
        state = State.SPIN;
    }

    private void handleResultKey(KeyEvent e) {
        overlay.backToMenu();
        if (game.getStatsPanel() != null) game.getStatsPanel().refresh();
    }

    // ── Logic ───────────────────────────────────────────────────────────────────

    private int slotColor(int slot) {
        int num = slot + 1;
        if (num == 7 || num == 14) return 2; // gold
        return (num % 2 == 0) ? 1 : 0;      // 0=red, 1=blue
    }

    private Color getSlotColor(int slot) {
        return switch (slotColor(slot)) {
            case 0 -> WHEEL_RED;
            case 1 -> WHEEL_BLUE;
            case 2 -> WHEEL_GOLD;
            default -> WHEEL_RED;
        };
    }

    private void resolveGocSpin() {
        int winNum = targetSlot + 1;
        int color = slotColor(targetSlot);
        boolean isOdd = (winNum % 2 != 0);
        int payout = 0;
        int bet = overlay.betAmount;

        switch (gocBetType) {
            case 1 -> { if (winNum == gocPickedNumber) payout = bet * 15; }
            case 2 -> { if (color == 0) payout = bet * 2; }
            case 3 -> { if (color == 1) payout = bet * 2; }
            case 4 -> { if (color == 2) payout = bet * 8; }
            case 5 -> { if (isOdd && color != 2) payout = bet * 2; }
            case 6 -> { if (!isOdd && color != 2) payout = bet * 2; }
        }

        if (payout > 0) {
            game.getPlayer().addGold(payout);
            gocResultMessage = "Winner! +" + payout + "g";
            if (payout >= bet * 8) {
                SoundManager.getInstance().play("victory");
            } else {
                SoundManager.getInstance().play("coin");
            }
        } else {
            gocResultMessage = "No luck... The wheel landed on " + winNum + ".";
        }

        game.getPlayer().recordGamblingResult(payout - bet);
        state = State.RESULT;
        if (game.getStatsPanel() != null) game.getStatsPanel().refresh();
    }

    // ── Update ──────────────────────────────────────────────────────────────────

    @Override
    public void update() {
        if (state == State.SPIN) updateSpin();
    }

    private void updateSpin() {
        long elapsed = System.currentTimeMillis() - spinStartTime;
        if (elapsed >= SPIN_DURATION) {
            wheelPosition = targetSlot;
            resolveGocSpin();
            return;
        }

        float t = (float) elapsed / SPIN_DURATION;
        float inv = 1f - t;
        float eased = 1f - inv * inv * inv;
        wheelPosition = (wheelStartPos + wheelTotalTravel * eased) % WHEEL_SLOTS;

        int currentSlot = Math.round(wheelPosition) % WHEEL_SLOTS;
        if (currentSlot != lastTickedSlot) {
            lastTickedSlot = currentSlot;
            int freq = 600 + (int)(t * 400);
            int dur = 20 + (int)(t * 30);
            SoundManager.getInstance().playTone(freq, dur, 0.20f);
        }
    }

    // ── Paint ───────────────────────────────────────────────────────────────────

    @Override
    public void paint(Graphics2D g, int px, int py, int pw, int ph) {
        switch (state) {
            case BET    -> paintBet(g, px, py, pw, ph);
            case SPIN   -> paintSpin(g, px, py, pw, ph);
            case RESULT -> paintResult(g, px, py, pw, ph);
        }
    }

    private void paintBet(Graphics2D g, int px, int py, int pw, int ph) {
        g.setFont(F_TITLE);
        g.setColor(GOLD_COL);
        drawCentered(g, "GAME OF CHANCE", px, py + 30, pw);

        g.setFont(F_MENU);
        g.setColor(AMBER);
        drawCentered(g, "Bet: " + overlay.betAmount + "g   (Left/Right to adjust, Shift for x10)", px, py + 60, pw);

        g.setColor(TEXT_DIM);
        g.setFont(F_SMALL);
        drawCentered(g, "Gold: " + game.getPlayer().getGold() + "g", px, py + 80, pw);

        g.setFont(F_ITEM);
        int startY = py + 110;
        int lineH = 24;

        if (pickingNumber) {
            g.setColor(CYAN_ACC);
            drawCentered(g, "Type a number 1-20, then Enter: " + pickBuffer + "_", px, startY, pw);
        } else {
            String[][] bets = {
                {"[1] Pick a Number (1-20)", "15:1"},
                {"[2] Red",                   "2:1"},
                {"[3] Blue",                  "2:1"},
                {"[4] Gold (7 or 14)",        "8:1"},
                {"[5] Odd",                   "2:1"},
                {"[6] Even",                  "2:1"},
            };
            for (int i = 0; i < bets.length; i++) {
                int ly = startY + i * lineH;
                g.setColor(TEXT_BRIGHT);
                g.drawString(bets[i][0], px + 40, ly);
                g.setColor(GOLD_COL);
                g.drawString(bets[i][1], px + pw - 80, ly);
            }
        }

        // Wheel preview
        int wheelY = startY + 7 * lineH;
        int slotW = Math.max(20, (pw - 40) / WHEEL_SLOTS);
        int wheelX = px + (pw - slotW * WHEEL_SLOTS) / 2;

        for (int i = 0; i < WHEEL_SLOTS; i++) {
            int sx = wheelX + i * slotW;
            Color sc = getSlotColor(i);
            g.setColor(sc);
            g.fillRect(sx + 1, wheelY, slotW - 2, 24);
            g.setColor(sc.brighter());
            g.drawLine(sx + 1, wheelY, sx + slotW - 3, wheelY);
            g.drawLine(sx + 1, wheelY, sx + 1, wheelY + 23);
            g.setColor(sc.darker());
            g.drawLine(sx + slotW - 3, wheelY, sx + slotW - 3, wheelY + 23);
            g.drawLine(sx + 1, wheelY + 23, sx + slotW - 3, wheelY + 23);
            g.setColor(Color.WHITE);
            g.setFont(F_SMALL);
            String num = String.valueOf(i + 1);
            int nw = g.getFontMetrics().stringWidth(num);
            g.drawString(num, sx + (slotW - nw) / 2, wheelY + 16);
        }

        g.setFont(F_KEY);
        g.setColor(TEXT_DIM);
        g.drawString("[Esc] Back to Menu", px + 20, py + ph - 20);
    }

    private void paintSpin(Graphics2D g, int px, int py, int pw, int ph) {
        g.setFont(F_TITLE);
        g.setColor(GOLD_COL);
        drawCentered(g, "SPINNING...", px, py + 30, pw);

        int slotW = 120;
        int slotH = 50;
        int colX = px + (pw - slotW) / 2;
        int colY = py + 60;
        int visibleSlots = 5;

        // Indicator arrows
        g.setColor(GOLD_COL);
        int arrowY = colY + (visibleSlots / 2) * slotH + slotH / 2;
        g.fillRect(colX - 15, arrowY - 5, 10, 10);
        g.fillRect(colX + slotW + 5, arrowY - 5, 10, 10);

        int centerSlotIdx = Math.round(wheelPosition) % WHEEL_SLOTS;
        float frac = wheelPosition - Math.round(wheelPosition);

        for (int i = -3; i <= 3; i++) {
            int slotIdx = ((centerSlotIdx + i) % WHEEL_SLOTS + WHEEL_SLOTS) % WHEEL_SLOTS;
            int sy = colY + (i + 2) * slotH - (int)(frac * slotH);

            if (sy < colY - slotH || sy > colY + visibleSlots * slotH) continue;

            Color sc = getSlotColor(slotIdx);
            boolean isCenter = (i == 0);

            g.setColor(sc);
            g.fillRect(colX, sy, slotW, slotH - 2);
            g.setColor(sc.brighter());
            g.drawLine(colX, sy, colX + slotW, sy);
            g.drawLine(colX, sy, colX, sy + slotH - 3);
            g.setColor(sc.darker());
            g.drawLine(colX + slotW, sy, colX + slotW, sy + slotH - 3);
            g.drawLine(colX, sy + slotH - 3, colX + slotW, sy + slotH - 3);

            if (isCenter) {
                g.setColor(Color.WHITE);
                g.drawRect(colX - 2, sy - 1, slotW + 4, slotH);
            }

            g.setFont(F_BIG);
            g.setColor(Color.WHITE);
            String num = String.valueOf(slotIdx + 1);
            int nw = g.getFontMetrics().stringWidth(num);
            g.drawString(num, colX + (slotW - nw) / 2, sy + slotH / 2 + 6);
        }

        g.setFont(F_ITEM);
        g.setColor(TEXT_DIM);
        String betInfo = "Bet: " + overlay.betAmount + "g on " + getBetTypeName(gocBetType);
        drawCentered(g, betInfo, px, py + ph - 40, pw);
    }

    private String getBetTypeName(int type) {
        return switch (type) {
            case 1 -> "Number " + gocPickedNumber;
            case 2 -> "Red";
            case 3 -> "Blue";
            case 4 -> "Gold";
            case 5 -> "Odd";
            case 6 -> "Even";
            default -> "???";
        };
    }

    private void paintResult(Graphics2D g, int px, int py, int pw, int ph) {
        g.setFont(F_TITLE);
        g.setColor(GOLD_COL);
        drawCentered(g, "GAME OF CHANCE", px, py + 30, pw);

        int winNum = targetSlot + 1;
        Color winColor = getSlotColor(targetSlot);

        int slotW = 120;
        int slotH = 60;
        int slotX = px + (pw - slotW) / 2;
        int slotY = py + 80;

        g.setColor(winColor);
        g.fillRect(slotX, slotY, slotW, slotH);
        g.setColor(winColor.brighter());
        g.drawLine(slotX, slotY, slotX + slotW, slotY);
        g.drawLine(slotX, slotY, slotX, slotY + slotH);
        g.setColor(winColor.darker());
        g.drawLine(slotX + slotW, slotY, slotX + slotW, slotY + slotH);
        g.drawLine(slotX, slotY + slotH, slotX + slotW, slotY + slotH);

        g.setFont(F_BIG);
        g.setColor(Color.WHITE);
        String numStr = String.valueOf(winNum);
        int nw = g.getFontMetrics().stringWidth(numStr);
        g.drawString(numStr, slotX + (slotW - nw) / 2, slotY + slotH / 2 + 7);

        g.setFont(F_MENU);
        boolean isWin = gocResultMessage.startsWith("Winner");
        g.setColor(isWin ? GOOD : DANGER);
        drawCentered(g, gocResultMessage, px, slotY + slotH + 40, pw);

        g.setFont(F_ITEM);
        g.setColor(AMBER);
        drawCentered(g, "Gold: " + game.getPlayer().getGold() + "g", px, slotY + slotH + 70, pw);

        g.setFont(F_KEY);
        g.setColor(TEXT_DIM);
        drawCentered(g, "Press any key to continue", px, py + ph - 20, pw);
    }
}

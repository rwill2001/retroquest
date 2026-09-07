package io.cannonforge.retroquest.overlay;

import static io.cannonforge.retroquest.overlay.AbyssalDepthsOverlay.ABYSS_TEAL;
import static io.cannonforge.retroquest.overlay.AbyssalDepthsOverlay.BIOLUM;
import static io.cannonforge.retroquest.overlay.AbyssalDepthsOverlay.F_BIG;
import static io.cannonforge.retroquest.overlay.AbyssalDepthsOverlay.F_MENU;
import static io.cannonforge.retroquest.overlay.AbyssalDepthsOverlay.F_TITLE;
import static io.cannonforge.retroquest.overlay.AbyssalDepthsOverlay.PRESSURE_RED;
import static io.cannonforge.retroquest.overlay.AbyssalDepthsOverlay.drawCentered;
import static io.cannonforge.retroquest.overlay.OverlayTheme.AMBER;
import static io.cannonforge.retroquest.overlay.OverlayTheme.BORDER_COL;
import static io.cannonforge.retroquest.overlay.OverlayTheme.F_ITEM;
import static io.cannonforge.retroquest.overlay.OverlayTheme.F_KEY;
import static io.cannonforge.retroquest.overlay.OverlayTheme.F_SMALL;
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
 * Pressure Diving — press your luck. Descend through 10 depth zones.
 * Each zone increases risk (5% per depth) and reward (2x–20x bet).
 * Surface to collect, or get crushed and lose everything.
 */
class PressureDivingGame implements MiniGame {

    private enum State { BET, DIVING, CRUSHED, SURFACED }

    private static final int MAX_DEPTH = 10;
    /** Crush chance for the dive down to depth d is d * RISK_PER_DEPTH percent. */
    private static final int RISK_PER_DEPTH = 5;
    /**
     * Multiple of the stake banked once depth d (1-based) has been SURVIVED.
     * Each entry sits just under the fair price 1/P(reach d), so pressing on is
     * always tempting and always slightly -EV: best case is 0.98x at depth 2,
     * falling to 0.72x if you ride it all the way to the abyss.
     */
    private static final float[] DEPTH_MULT = {
        1.00f, 1.15f, 1.30f, 1.60f, 2.10f, 3.00f, 4.50f, 7.00f, 12.00f, 22.00f
    };
    private static final Color DEPTH_SAFE    = new Color(  0, 180, 180);
    private static final Color DEPTH_MID     = new Color(180, 180,   0);
    private static final Color DEPTH_DANGER  = new Color(200,  60,  60);
    private static final Color METER_BG      = new Color( 10,  20,  30);

    private final Retroquest game;
    private final AbyssalDepthsOverlay overlay;
    private final Random rng;

    private State state = State.BET;
    private int currentDepth;    // depths SURVIVED so far (0 = still at the surface)
    private int accumulated;     // gold accumulated so far
    private long resultTime;

    PressureDivingGame(Retroquest game, AbyssalDepthsOverlay overlay, Random rng) {
        this.game = game;
        this.overlay = overlay;
        this.rng = rng;
    }

    @Override
    public void reset(int betAmount) {
        state = State.BET;
        currentDepth = 0;
        accumulated = 0;
    }

    @Override public boolean isShowingResult() { return state == State.CRUSHED || state == State.SURFACED; }
    @Override public void update() {}

    @Override
    public void handleKey(KeyEvent e) {
        switch (state) {
            case BET      -> handleBetKey(e);
            case DIVING   -> handleDiveKey(e);
            case CRUSHED, SURFACED -> {
                overlay.backToMenu();
                if (game.getStatsPanel() != null) game.getStatsPanel().refresh();
            }
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
                currentDepth = 0;
                accumulated = 0; // nothing is banked until a depth has been survived
                state = State.DIVING;
            }
            case KeyEvent.VK_ESCAPE -> { overlay.backToMenu(); SoundManager.getInstance().play("menublip"); }
        }
    }

    private void handleDiveKey(KeyEvent e) {
        int code = e.getKeyCode();
        if (code == KeyEvent.VK_ENTER || code == KeyEvent.VK_D) {
            // Dive deeper — the roll happens FIRST, so nothing is ever banked risk-free.
            if (rng.nextInt(100) < nextRiskPercent()) {
                // Crushed!
                accumulated = 0;
                state = State.CRUSHED;
                resultTime = System.currentTimeMillis();
                SoundManager.getInstance().play("hurt");
                game.getPlayer().recordGamblingResult(-overlay.betAmount);
                game.log("CRUSHED! The pressure was too much! Lost all gold.", MessageLog.Type.DANGER);
            } else {
                currentDepth++;
                accumulated = (int)(overlay.betAmount * DEPTH_MULT[currentDepth - 1]);
                if (currentDepth >= MAX_DEPTH) {
                    // Hit the bottom — auto surface with the deepest reward
                    game.getPlayer().addGold(accumulated);
                    state = State.SURFACED;
                    resultTime = System.currentTimeMillis();
                    SoundManager.getInstance().play("victory");
                    game.getPlayer().recordGamblingResult(accumulated - overlay.betAmount);
                    game.log("You reached the deepest point! Won " + accumulated + "g!", MessageLog.Type.LOOT);
                } else {
                    SoundManager.getInstance().play("menublip");
                }
            }
        } else if (code == KeyEvent.VK_S || code == KeyEvent.VK_ESCAPE) {
            // Surface with whatever has actually been survived for
            game.getPlayer().addGold(accumulated);
            state = State.SURFACED;
            resultTime = System.currentTimeMillis();
            game.getPlayer().recordGamblingResult(accumulated - overlay.betAmount);
            if (accumulated > 0) {
                SoundManager.getInstance().play("coin");
                game.log("Surfaced safely! Won " + accumulated + "g!", MessageLog.Type.LOOT);
            } else {
                SoundManager.getInstance().play("hurt");
                game.log("You surfaced without diving. Lost " + overlay.betAmount + "g of air.", MessageLog.Type.DANGER);
            }
        }
    }

    /** Crush chance of the next dive, in percent. */
    private int nextRiskPercent() {
        return Math.min(MAX_DEPTH, currentDepth + 1) * RISK_PER_DEPTH;
    }

    @Override
    public void paint(Graphics2D g, int px, int py, int pw, int ph) {
        g.setFont(F_TITLE);
        g.setColor(ABYSS_TEAL);
        drawCentered(g, "PRESSURE DIVING", px, py + 30, pw);

        switch (state) {
            case BET      -> paintBet(g, px, py, pw, ph);
            case DIVING   -> paintDiving(g, px, py, pw, ph);
            case CRUSHED  -> paintCrushed(g, px, py, pw, ph);
            case SURFACED -> paintSurfaced(g, px, py, pw, ph);
        }
    }

    private void paintBet(Graphics2D g, int px, int py, int pw, int ph) {
        g.setFont(F_MENU);
        g.setColor(TEXT_BRIGHT);
        drawCentered(g, "Descend into the crushing deep. Surface before it breaks you.", px, py + 65, pw);

        g.setFont(F_BIG);
        g.setColor(ABYSS_TEAL);
        drawCentered(g, "Bet: " + overlay.betAmount + "g", px, py + ph / 2 - 10, pw);

        g.setFont(F_ITEM);
        g.setColor(AMBER);
        drawCentered(g, "Gold: " + game.getPlayer().getGold() + "g", px, py + ph / 2 + 20, pw);

        g.setFont(F_SMALL);
        g.setColor(TEXT_DIM);
        drawCentered(g, "Risk scales from 5% (Depth 1) to 50% (Depth 10)", px, py + ph / 2 + 50, pw);
        drawCentered(g, "Nothing is banked until you survive a depth: 1x at Depth 1, 22x at Depth 10", px, py + ph / 2 + 68, pw);

        g.setFont(F_KEY);
        g.setColor(TEXT_DIM);
        g.drawString("[Left/Right] Bet   [Shift] x10   [Enter] Dive!   [Esc] Back", px + 20, py + ph - 20);
    }

    private void paintDiving(Graphics2D g, int px, int py, int pw, int ph) {
        // Depth meter (left side)
        int meterX = px + 30;
        int meterY = py + 60;
        int meterW = 40;
        int meterH = ph - 120;
        g.setColor(METER_BG);
        g.fillRect(meterX, meterY, meterW, meterH);
        g.setColor(BORDER_COL);
        g.drawRect(meterX, meterY, meterW, meterH);

        int segH = meterH / MAX_DEPTH;
        for (int d = 0; d < MAX_DEPTH; d++) {
            int sy = meterY + d * segH;
            float t = d / (float)(MAX_DEPTH - 1);
            Color depthCol = d < currentDepth
                ? lerpColor(DEPTH_SAFE, DEPTH_DANGER, t)
                : METER_BG;
            g.setColor(depthCol);
            g.fillRect(meterX + 2, sy + 2, meterW - 4, segH - 4);
            if (d == currentDepth) {
                g.setColor(Color.WHITE);
                g.drawRect(meterX + 1, sy + 1, meterW - 2, segH - 2);
            }
        }

        // Depth labels
        g.setFont(F_SMALL);
        g.setColor(TEXT_DIM);
        g.drawString("Surface", meterX + meterW + 5, meterY + 12);
        g.drawString("Abyss", meterX + meterW + 5, meterY + meterH - 4);

        // Center info
        g.setFont(F_BIG);
        g.setColor(ABYSS_TEAL);
        drawCentered(g, "Depth " + currentDepth + " / " + MAX_DEPTH, px + 100, py + 100, pw - 140);

        // Accumulated gold
        g.setFont(F_TITLE);
        g.setColor(BIOLUM);
        drawCentered(g, "Accumulated: " + accumulated + "g", px + 100, py + 140, pw - 140);

        // Risk info — the cost of the NEXT dive, not the depth already survived
        int riskPercent = nextRiskPercent();
        g.setFont(F_MENU);
        Color riskCol = riskPercent <= 15 ? DEPTH_SAFE : riskPercent <= 30 ? DEPTH_MID : DEPTH_DANGER;
        g.setColor(riskCol);
        drawCentered(g, "Next Dive Crush Risk: " + riskPercent + "%", px + 100, py + 180, pw - 140);

        // Risk bar (right side)
        int barX = px + pw - 70;
        int barY = py + 60;
        int barW = 40;
        int barH = meterH;
        g.setColor(METER_BG);
        g.fillRect(barX, barY, barW, barH);
        g.setColor(BORDER_COL);
        g.drawRect(barX, barY, barW, barH);
        int fillH = (int)(barH * riskPercent / 100.0);
        g.setColor(DEPTH_DANGER);
        g.fillRect(barX + 2, barY + barH - fillH, barW - 4, fillH);

        // Next reward
        if (currentDepth < MAX_DEPTH) {
            float nextMult = DEPTH_MULT[currentDepth];
            int nextReward = (int)(overlay.betAmount * nextMult);
            g.setFont(F_ITEM);
            g.setColor(TEXT_BRIGHT);
            drawCentered(g, "Next depth: " + nextReward + "g (" + nextMult + "x bet)", px + 100, py + 220, pw - 140);
        }

        // Keys
        g.setFont(F_KEY);
        g.setColor(TEXT_DIM);
        drawCentered(g, "[Enter/D] Dive Deeper   [S/Esc] Surface Now", px, py + ph - 20, pw);
    }

    private void paintCrushed(Graphics2D g, int px, int py, int pw, int ph) {
        // Red flash
        long elapsed = System.currentTimeMillis() - resultTime;
        if (elapsed < 500 && (elapsed / 100) % 2 == 0) {
            g.setColor(new Color(200, 0, 0, 40));
            g.fillRect(px, py, pw, ph);
        }

        g.setFont(F_BIG);
        g.setColor(PRESSURE_RED);
        drawCentered(g, "C R U S H E D !", px, py + ph / 2 - 20, pw);

        g.setFont(F_MENU);
        g.setColor(TEXT_BRIGHT);
        drawCentered(g, "The pressure was too much at Depth " + (currentDepth + 1) + "!", px, py + ph / 2 + 20, pw);
        drawCentered(g, "Lost " + overlay.betAmount + "g", px, py + ph / 2 + 50, pw);

        g.setFont(F_KEY);
        g.setColor(TEXT_DIM);
        drawCentered(g, "[Any key] Continue", px, py + ph - 20, pw);
    }

    private void paintSurfaced(Graphics2D g, int px, int py, int pw, int ph) {
        g.setFont(F_BIG);
        g.setColor(BIOLUM);
        drawCentered(g, "SURFACED!", px, py + ph / 2 - 20, pw);

        g.setFont(F_MENU);
        g.setColor(ABYSS_TEAL);
        drawCentered(g, "Won " + accumulated + "g from Depth " + currentDepth + "!", px, py + ph / 2 + 20, pw);

        g.setFont(F_KEY);
        g.setColor(TEXT_DIM);
        drawCentered(g, "[Any key] Continue", px, py + ph - 20, pw);
    }

    private static Color lerpColor(Color a, Color b, float t) {
        t = Math.max(0, Math.min(1, t));
        return new Color(
            (int)(a.getRed()   + (b.getRed()   - a.getRed())   * t),
            (int)(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
            (int)(a.getBlue()  + (b.getBlue()  - a.getBlue())  * t)
        );
    }
}

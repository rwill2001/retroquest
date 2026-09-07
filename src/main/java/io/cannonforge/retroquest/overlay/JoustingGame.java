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
 * Jousting mini-game: time your lance strike as a vertical bar oscillates
 * across a targeting zone. Three passes, each faster than the last.
 * DEX slows the bar; STR multiplies score.
 */
class JoustingGame implements MiniGame {

    private enum State { BET, CHARGE, LAND, RESULT }

    // Zone colours
    private static final Color ZONE_MISS    = new Color( 40,  20,  15);
    private static final Color ZONE_EDGE    = new Color( 80,  50,  20);
    private static final Color ZONE_GOOD    = new Color(120,  90,  30);
    private static final Color ZONE_PERFECT = new Color(200, 160,  40);
    private static final Color BAR_COL      = new Color(255, 255, 255);
    private static final Color HORSE_BODY   = new Color(100,  60,  30);
    private static final Color HORSE_DARK   = new Color( 60,  35,  15);
    private static final Color KNIGHT_COL   = new Color(140, 140, 150);
    private static final Color LANCE_COL    = new Color(180, 140,  60);
    private static final Color OPPONENT_COL = new Color(120,  40,  40);

    private static final int TOTAL_PASSES = 3;

    // ── Scoring zones (fraction of half the strike bar) ────────────────────────
    private static final double ZONE_PERFECT_FRAC = 0.07;  // tightened from 0.10
    private static final double ZONE_GOOD_FRAC    = 0.28;
    private static final double ZONE_EDGE_FRAC    = 0.58;

    // ── Payout ladder ─────────────────────────────────────────────────────────
    // Tiers are a fraction of the STR-adjusted maximum, so a high-STR knight no
    // longer reaches the top tier on a merely good hit. Values are the multiple
    // of the stake RETURNED (1.0 = break even, 0 = stake lost).
    private static final float TIER_TOP   = 1.00f, PAY_TOP   = 2.5f;
    private static final float TIER_GREAT = 0.80f, PAY_GREAT = 1.3f;
    private static final float TIER_GOOD  = 0.60f, PAY_GOOD  = 0.7f;
    private static final float TIER_WEAK  = 0.40f, PAY_WEAK  = 0.3f;
    private static final long LAND_MS = 800;
    private static final int ZONE_W = 300;
    private static final int ZONE_H = 30;

    private final Retroquest game;
    private final ForgeArenaOverlay overlay;
    @SuppressWarnings("unused")
    private final Random rng;

    private State state = State.BET;
    private int pass = 0;
    private int[] scores = new int[TOTAL_PASSES];
    private long barStartTime = 0;
    private int lockedPos = 0;
    private long landTime = 0;
    private float baseSpeed = 3.0f;
    private String resultMessage = "";

    JoustingGame(Retroquest game, ForgeArenaOverlay overlay, Random rng) {
        this.game = game;
        this.overlay = overlay;
        this.rng = rng;
    }

    @Override
    public void reset(int betAmount) {
        state = State.BET;
        pass = 0;
        resultMessage = "";
        for (int i = 0; i < TOTAL_PASSES; i++) scores[i] = 0;
    }

    @Override
    public boolean isShowingResult() { return state == State.RESULT; }

    @Override
    public void handleKey(KeyEvent e) {
        switch (state) {
            case BET    -> handleBetKey(e);
            case CHARGE -> handleChargeKey(e);
            case LAND   -> {}
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
                startCharge();
            }
            case KeyEvent.VK_ESCAPE -> { overlay.backToMenu(); SoundManager.getInstance().play("menublip"); }
        }
    }

    private void handleChargeKey(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_ENTER || e.getKeyCode() == KeyEvent.VK_SPACE) {
            long elapsed = System.currentTimeMillis() - barStartTime;
            float dexMod = 1.0f - (game.getPlayer().getDex() - 10) * 0.03f;
            dexMod = Math.max(0.6f, Math.min(1.4f, dexMod));
            float speed = (baseSpeed + pass * 0.8f) * dexMod;
            double t = elapsed * speed * 0.001;
            lockedPos = (int)(Math.sin(t) * (ZONE_W / 2));

            int absDist = Math.abs(lockedPos);
            int halfW = ZONE_W / 2;

            int rawScore;
            if (absDist <= halfW * ZONE_PERFECT_FRAC) rawScore = 50;      // perfect center
            else if (absDist <= halfW * ZONE_GOOD_FRAC) rawScore = 25;    // good
            else if (absDist <= halfW * ZONE_EDGE_FRAC) rawScore = 10;    // edge
            else rawScore = 0;                                            // miss

            scores[pass] = (int)(rawScore * strMod());
            SoundManager.getInstance().play("lance");
            landTime = System.currentTimeMillis();
            state = State.LAND;
        } else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
            resolveGame();
        }
    }

    private void handleResultKey(KeyEvent e) {
        overlay.backToMenu();
        if (game.getStatsPanel() != null) game.getStatsPanel().refresh();
    }

    // ── Logic ─────────────────────────────────────────────────────────────────

    private void startCharge() {
        barStartTime = System.currentTimeMillis();
        state = State.CHARGE;
    }

    /** Lance-weight bonus from STR. Applied to BOTH the score and the ladder's maximum. */
    private float strMod() {
        float m = 1.0f + (game.getPlayer().getStr() - 10) * 0.05f;
        return Math.max(0.5f, Math.min(2.0f, m));
    }

    private void resolveGame() {
        int total = 0;
        for (int s : scores) total += s;
        // Scale the yardstick by the same STR modifier that scaled the score.
        int maxPossible = (int)(50 * TOTAL_PASSES * strMod());

        int payout = 0;
        if      (total >= maxPossible * TIER_TOP)   payout = (int)(overlay.betAmount * PAY_TOP);
        else if (total >= maxPossible * TIER_GREAT) payout = (int)(overlay.betAmount * PAY_GREAT);
        else if (total >= maxPossible * TIER_GOOD)  payout = (int)(overlay.betAmount * PAY_GOOD);
        else if (total >= maxPossible * TIER_WEAK)  payout = (int)(overlay.betAmount * PAY_WEAK);

        if (payout > 0) {
            game.getPlayer().addGold(payout);
            if (total >= maxPossible) {
                resultMessage = "PERFECT JOUST! Won " + payout + "g!";
                SoundManager.getInstance().play("victory");
            } else {
                resultMessage = "You won " + payout + "g!";
                SoundManager.getInstance().play("coin");
            }
            game.log(resultMessage, MessageLog.Type.LOOT);
        } else {
            resultMessage = "Unhorsed! You lost " + overlay.betAmount + "g.";
            SoundManager.getInstance().play("hurt");
            game.log(resultMessage, MessageLog.Type.DANGER);
        }
        game.getPlayer().recordGamblingResult(payout - overlay.betAmount);
        state = State.RESULT;
    }

    @Override
    public void update() {
        if (state == State.LAND) {
            if (System.currentTimeMillis() - landTime > LAND_MS) {
                pass++;
                if (pass >= TOTAL_PASSES) {
                    resolveGame();
                } else {
                    startCharge();
                }
            }
        }
    }

    @Override
    public void paint(Graphics2D g, int px, int py, int pw, int ph) {
        // Title
        g.setFont(F_TITLE);
        g.setColor(FORGE_GOLD);
        drawCentered(g, "JOUSTING", px, py + 30, pw);

        switch (state) {
            case BET    -> paintBet(g, px, py, pw, ph);
            case CHARGE -> paintCharge(g, px, py, pw, ph);
            case LAND   -> paintLand(g, px, py, pw, ph);
            case RESULT -> paintResult(g, px, py, pw, ph);
        }
    }

    private void paintBet(Graphics2D g, int px, int py, int pw, int ph) {
        g.setFont(F_MENU);
        g.setColor(TEXT_BRIGHT);
        drawCentered(g, "Three passes — time your lance strike!", px, py + 70, pw);

        // Bet display
        g.setFont(F_BIG);
        g.setColor(FORGE_GOLD);
        drawCentered(g, "Bet: " + overlay.betAmount + "g", px, py + ph / 2, pw);

        g.setFont(F_ITEM);
        g.setColor(AMBER);
        drawCentered(g, "Gold: " + game.getPlayer().getGold() + "g", px, py + ph / 2 + 30, pw);

        // DEX/STR info
        g.setFont(F_SMALL);
        g.setColor(TEXT_DIM);
        drawCentered(g, "DEX " + game.getPlayer().getDex() + " (timing)   STR " + game.getPlayer().getStr() + " (power)", px, py + ph / 2 + 60, pw);

        // Key bar
        g.setFont(F_KEY);
        g.setColor(TEXT_DIM);
        g.drawString("[Left/Right] Bet   [Shift] x10   [Enter] Charge!   [Esc] Back", px + 20, py + ph - 20);
    }

    private void paintCharge(Graphics2D g, int px, int py, int pw, int ph) {
        int cx = px + pw / 2;
        int arenaY = py + 80;

        // Pass indicator
        g.setFont(F_ITEM);
        g.setColor(AMBER);
        drawCentered(g, "Pass " + (pass + 1) + " of " + TOTAL_PASSES, px, py + 55, pw);

        // Previous scores
        paintScoreStrip(g, px + 20, py + 65, pw - 40);

        // Arena ground
        int groundY = arenaY + 140;
        g.setColor(new Color(60, 40, 20));
        g.fillRect(px + 20, groundY, pw - 40, 30);

        // Horses charging toward center
        long elapsed = System.currentTimeMillis() - barStartTime;
        float chargeProgress = Math.min(1.0f, elapsed / 3000.0f);

        // Player knight (left side, charges right)
        int playerX = px + 40 + (int)((cx - px - 80) * chargeProgress);
        drawKnight(g, playerX, groundY - 40, true, KNIGHT_COL);

        // Opponent knight (right side, charges left)
        int opponentX = px + pw - 40 - (int)((cx - px - 80) * chargeProgress);
        drawKnight(g, opponentX, groundY - 40, false, OPPONENT_COL);

        // Targeting zone
        int zoneX = cx - ZONE_W / 2;
        int zoneY = groundY + 50;

        // Zone bands
        g.setColor(ZONE_MISS);    g.fillRect(zoneX, zoneY, ZONE_W, ZONE_H);
        int edgeW = (int)(ZONE_W * ZONE_EDGE_FRAC);
        g.setColor(ZONE_EDGE);    g.fillRect(cx - edgeW/2, zoneY, edgeW, ZONE_H);
        int goodW = (int)(ZONE_W * ZONE_GOOD_FRAC);
        g.setColor(ZONE_GOOD);    g.fillRect(cx - goodW/2, zoneY, goodW, ZONE_H);
        int perfW = (int)(ZONE_W * ZONE_PERFECT_FRAC);
        g.setColor(ZONE_PERFECT); g.fillRect(cx - perfW/2, zoneY, perfW, ZONE_H);

        // Oscillating bar
        float dexMod = 1.0f - (game.getPlayer().getDex() - 10) * 0.03f;
        dexMod = Math.max(0.6f, Math.min(1.4f, dexMod));
        float speed = (baseSpeed + pass * 0.8f) * dexMod;
        double t = elapsed * speed * 0.001;
        int barPos = (int)(Math.sin(t) * (ZONE_W / 2));

        g.setColor(BAR_COL);
        g.fillRect(cx + barPos - 1, zoneY - 5, 3, ZONE_H + 10);

        // Instructions
        g.setFont(F_KEY);
        g.setColor(TEXT_DIM);
        drawCentered(g, "[Enter/Space] Strike!", px, py + ph - 20, pw);
    }

    private void paintLand(Graphics2D g, int px, int py, int pw, int ph) {
        int cx = px + pw / 2;

        g.setFont(F_ITEM);
        g.setColor(AMBER);
        drawCentered(g, "Pass " + (pass + 1) + " of " + TOTAL_PASSES, px, py + 55, pw);
        paintScoreStrip(g, px + 20, py + 65, pw - 40);

        // Show locked position result
        int arenaY = py + 80;
        int groundY = arenaY + 140;

        // Ground
        g.setColor(new Color(60, 40, 20));
        g.fillRect(px + 20, groundY, pw - 40, 30);

        // Knights at center (clash!)
        drawKnight(g, cx - 20, groundY - 40, true, KNIGHT_COL);
        drawKnight(g, cx + 20, groundY - 40, false, OPPONENT_COL);

        // Targeting zone with locked position
        int zoneX = cx - ZONE_W / 2;
        int zoneY = groundY + 50;

        g.setColor(ZONE_MISS);    g.fillRect(zoneX, zoneY, ZONE_W, ZONE_H);
        int edgeW = (int)(ZONE_W * ZONE_EDGE_FRAC);
        g.setColor(ZONE_EDGE);    g.fillRect(cx - edgeW/2, zoneY, edgeW, ZONE_H);
        int goodW = (int)(ZONE_W * ZONE_GOOD_FRAC);
        g.setColor(ZONE_GOOD);    g.fillRect(cx - goodW/2, zoneY, goodW, ZONE_H);
        int perfW = (int)(ZONE_W * ZONE_PERFECT_FRAC);
        g.setColor(ZONE_PERFECT); g.fillRect(cx - perfW/2, zoneY, perfW, ZONE_H);

        // Locked bar
        g.setColor(scores[pass] >= 40 ? GOOD : scores[pass] >= 15 ? AMBER : DANGER);
        g.fillRect(cx + lockedPos - 2, zoneY - 5, 5, ZONE_H + 10);

        // Score feedback
        g.setFont(F_BIG);
        String hitLabel = scores[pass] >= 40 ? "PERFECT!" : scores[pass] >= 20 ? "Good hit!" : scores[pass] >= 8 ? "Glancing blow" : "Miss!";
        g.setColor(scores[pass] >= 20 ? FORGE_GOLD : scores[pass] >= 8 ? AMBER : DANGER);
        drawCentered(g, hitLabel + " (+" + scores[pass] + ")", px, zoneY + ZONE_H + 30, pw);
    }

    private void paintResult(Graphics2D g, int px, int py, int pw, int ph) {
        paintScoreStrip(g, px + 20, py + 80, pw - 40);

        int total = 0;
        for (int s : scores) total += s;

        g.setFont(F_BIG);
        g.setColor(FORGE_GOLD);
        drawCentered(g, "Total: " + total + " pts", px, py + 180, pw);

        g.setFont(F_MENU);
        g.setColor(resultMessage.contains("won") || resultMessage.contains("PERFECT") ? GOOD : DANGER);
        drawCentered(g, resultMessage, px, py + 220, pw);

        g.setFont(F_KEY);
        g.setColor(TEXT_DIM);
        drawCentered(g, "[Any key] Continue", px, py + ph - 20, pw);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void paintScoreStrip(Graphics2D g, int x, int y, int w) {
        g.setFont(F_SMALL);
        int segW = w / TOTAL_PASSES;
        for (int i = 0; i < TOTAL_PASSES; i++) {
            int sx = x + i * segW;
            boolean done = (state == State.LAND || state == State.RESULT) ? i <= pass : i < pass;
            g.setColor(done ? (scores[i] >= 40 ? GOOD : scores[i] >= 15 ? AMBER : DANGER) : TEXT_DIM);
            g.drawString("Pass " + (i + 1) + ": " + (done ? scores[i] + " pts" : "---"), sx, y);
        }
    }

    private void drawKnight(Graphics2D g, int x, int y, boolean facingRight, Color armorCol) {
        // Horse body (20x12)
        g.setColor(HORSE_BODY);
        g.fillRect(x - 10, y, 20, 12);
        // Horse legs
        g.setColor(HORSE_DARK);
        g.fillRect(x - 8, y + 12, 3, 8);
        g.fillRect(x - 2, y + 12, 3, 8);
        g.fillRect(x + 4, y + 12, 3, 8);

        // Horse head
        int headX = facingRight ? x + 10 : x - 14;
        g.setColor(HORSE_BODY);
        g.fillRect(headX, y - 4, 4, 8);

        // Knight torso on horse
        g.setColor(armorCol);
        g.fillRect(x - 4, y - 12, 8, 12);

        // Knight head (helmet)
        g.setColor(armorCol.brighter());
        g.fillRect(x - 3, y - 18, 6, 6);

        // Lance
        g.setColor(LANCE_COL);
        int lanceEndX = facingRight ? x + 30 : x - 30;
        g.drawLine(x, y - 8, lanceEndX, y - 6);
    }
}

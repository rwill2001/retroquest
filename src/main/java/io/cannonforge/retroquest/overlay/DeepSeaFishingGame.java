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
import io.cannonforge.retroquest.model.Item;
import io.cannonforge.retroquest.registry.ItemRegistry;

/**
 * Deep Sea Fishing — cast a line into the abyss.
 * Wait for a bite, strike on time, reel with directional matching.
 * Catch quality is random from a weighted loot table.
 */
class DeepSeaFishingGame implements MiniGame {

    private enum State { BET, WAITING, STRIKE, REELING, CATCH_RESULT, ESCAPED }

    private static final long STRIKE_WINDOW = 1500;
    private static final int MIN_WAIT = 2000, MAX_WAIT = 6000;

    // Catch table: name, payout multiple of the BAIT STAKE, rarity, item ID (null = no item).
    // 1.0 = break even. Weighted table EV is 1.29x per landed fish; multiplied by the
    // chance of actually landing one (~70% for a good angler) this settles near 0.9x,
    // so fishing is a slow drain on gold that pays out mainly in rare items.
    private static final String[][] CATCH_TABLE = {
        {"Pale Anglerfish",     "0.5", "Common",   null},
        {"Glowing Jellyfish",   "1.2", "Uncommon", null},
        {"Ancient Coral Relic", "2.5", "Rare",     "ancient_coral_relic"},
        {"Leviathan Scale",     "4.0", "Epic",     "leviathan_scale"},
        {"Old Boot",            "0.1", "Trash",    null},
    };
    // Cumulative weights: 0-39 common, 40-64 uncommon, 65-79 rare, 80-89 epic, 90-99 trash
    private static final int[] CATCH_THRESHOLDS = {40, 65, 80, 90, 100};

    // Reel quick-time window: round n gets REEL_BASE_MS - n * REEL_STEP_MS (1100 → 620ms).
    private static final long REEL_BASE_MS = 1100;
    private static final long REEL_STEP_MS = 120;

    private static final Color COMMON_COL   = TEXT_BRIGHT;
    private static final Color UNCOMMON_COL = new Color(0, 200, 255);
    private static final Color RARE_COL     = ABYSS_TEAL;
    private static final Color EPIC_COL     = BIOLUM;
    private static final Color TRASH_COL    = TEXT_DIM;
    private static final Color WATER_COL    = new Color(10, 30, 50);

    private final Retroquest game;
    private final AbyssalDepthsOverlay overlay;
    private final Random rng;

    private State state = State.BET;
    private long waitStartTime, waitDuration;
    private long strikeTime;
    private int reelRound, reelTotal;
    private int reelDirection; // 0=left, 1=right
    private long reelShowTime;
    private int catchIndex;
    private int goldWon;
    private String itemName;

    DeepSeaFishingGame(Retroquest game, AbyssalDepthsOverlay overlay, Random rng) {
        this.game = game;
        this.overlay = overlay;
        this.rng = rng;
    }

    @Override
    public void reset(int betAmount) {
        state = State.BET;
        catchIndex = -1;
        goldWon = 0;
        itemName = null;
    }

    @Override public boolean isShowingResult() { return state == State.CATCH_RESULT || state == State.ESCAPED; }

    private long getReelWindow() {
        return REEL_BASE_MS - reelRound * REEL_STEP_MS;
    }

    @Override
    public void update() {
        if (state == State.WAITING) {
            if (System.currentTimeMillis() - waitStartTime >= waitDuration) {
                state = State.STRIKE;
                strikeTime = System.currentTimeMillis();
                SoundManager.getInstance().play("menublip");
            }
        } else if (state == State.STRIKE) {
            if (System.currentTimeMillis() - strikeTime > STRIKE_WINDOW) {
                escape("Too slow! The fish escaped.");
            }
        } else if (state == State.REELING) {
            if (System.currentTimeMillis() - reelShowTime > getReelWindow()) {
                escape("The line snapped! Fish escaped.");
            }
        }
    }

    @Override
    public void handleKey(KeyEvent e) {
        switch (state) {
            case BET          -> handleBetKey(e);
            case WAITING      -> { if (e.getKeyCode() == KeyEvent.VK_ESCAPE) { abandonCast(); } }
            case STRIKE       -> handleStrikeKey(e);
            case REELING      -> handleReelKey(e);
            case CATCH_RESULT -> handleResultKey(e);
            case ESCAPED      -> handleResultKey(e);
        }
    }

    private void handleBetKey(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_LEFT  -> overlay.adjustBet(e.isShiftDown() ? -10 : -1);
            case KeyEvent.VK_RIGHT -> overlay.adjustBet(e.isShiftDown() ?  10 :  1);
            case KeyEvent.VK_ENTER -> {
                if (game.getPlayer().getGold() < overlay.betAmount) {
                    game.log("Not enough gold for bait!", MessageLog.Type.DANGER);
                    return;
                }
                game.getPlayer().addGold(-overlay.betAmount);
                overlay.lastBet = overlay.betAmount;
                SoundManager.getInstance().play("coin");
                startWaiting();
            }
            case KeyEvent.VK_ESCAPE -> { overlay.backToMenu(); SoundManager.getInstance().play("menublip"); }
        }
    }

    private void handleStrikeKey(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_ENTER || e.getKeyCode() == KeyEvent.VK_SPACE) {
            // Successful strike — start reeling
            reelRound = 0;
            reelTotal = 3 + rng.nextInt(3); // 3-5 rounds
            startReelRound();
            SoundManager.getInstance().play("menublip");
        }
    }

    private void handleReelKey(KeyEvent e) {
        int answer = switch (e.getKeyCode()) {
            case KeyEvent.VK_A, KeyEvent.VK_LEFT  -> 0;
            case KeyEvent.VK_D, KeyEvent.VK_RIGHT -> 1;
            default -> -1;
        };
        if (answer < 0) return;

        if (answer == reelDirection) {
            reelRound++;
            if (reelRound >= reelTotal) {
                // Caught!
                determineCatch();
            } else {
                startReelRound();
                SoundManager.getInstance().play("menublip");
            }
        } else {
            escape("Wrong direction! The fish broke free.");
        }
    }

    private void handleResultKey(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_ENTER) {
            // Fish again with same bet
            if (game.getPlayer().isGamblingCapped()) {
                game.log("You've won enough for now. Rest at an inn to gamble again.", MessageLog.Type.INFO);
                overlay.backToMenu();
            } else if (game.getPlayer().getGold() >= overlay.betAmount) {
                game.getPlayer().addGold(-overlay.betAmount);
                SoundManager.getInstance().play("coin");
                startWaiting();
            } else {
                game.log("Not enough gold for bait!", MessageLog.Type.DANGER);
                overlay.backToMenu();
            }
        } else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
            overlay.backToMenu();
        }
        if (game.getStatsPanel() != null) game.getStatsPanel().refresh();
    }

    // ── Logic ───────────────────────────────────────────────────────────────

    private void startWaiting() {
        state = State.WAITING;
        waitStartTime = System.currentTimeMillis();
        waitDuration = MIN_WAIT + rng.nextInt(MAX_WAIT - MIN_WAIT);
    }

    private void startReelRound() {
        state = State.REELING;
        reelDirection = rng.nextInt(2);
        reelShowTime = System.currentTimeMillis();
    }

    /** Cut the line before a bite — the bait is still spent, so book the loss. */
    private void abandonCast() {
        game.getPlayer().recordGamblingResult(-overlay.betAmount);
        game.log("You reel in and cut bait. Lost " + overlay.betAmount + "g.", MessageLog.Type.DANGER);
        SoundManager.getInstance().play("menublip");
        state = State.BET;
    }

    private void escape(String msg) {
        state = State.ESCAPED;
        goldWon = 0;
        SoundManager.getInstance().play("hurt");
        game.log(msg, MessageLog.Type.DANGER);
    }

    private void determineCatch() {
        int roll = rng.nextInt(100);
        catchIndex = 0;
        for (int i = 0; i < CATCH_THRESHOLDS.length; i++) {
            if (roll < CATCH_THRESHOLDS[i]) { catchIndex = i; break; }
        }

        float multiplier = Float.parseFloat(CATCH_TABLE[catchIndex][1]);
        goldWon = Math.max(0, (int)(overlay.betAmount * multiplier));
        game.getPlayer().addGold(goldWon);
        game.getPlayer().recordGamblingResult(goldWon - overlay.betAmount);

        // Item reward for rare/epic
        String itemId = CATCH_TABLE[catchIndex][3];
        if (itemId != null) {
            Item item = ItemRegistry.getById(itemId);
            if (item != null && game.getPlayer().addItemAndProgress(item)) {
                itemName = item.getName();
                game.log("Caught: " + CATCH_TABLE[catchIndex][0] + "! +" + goldWon + "g + " + itemName, MessageLog.Type.LOOT);
            } else if (item != null) {
                // Pack was full — the catch slipped back into the dark. Do not claim it was received.
                itemName = null;
                game.log("Caught: " + CATCH_TABLE[catchIndex][0] + "! +" + goldWon + "g, but your pack is full — "
                        + item.getName() + " slipped away!", MessageLog.Type.DANGER);
            } else {
                itemName = null;
                game.log("Caught: " + CATCH_TABLE[catchIndex][0] + "! +" + goldWon + "g", MessageLog.Type.LOOT);
            }
        } else {
            itemName = null;
            if (catchIndex == 4) { // trash
                game.log("Caught: " + CATCH_TABLE[catchIndex][0] + ". Recovered " + goldWon + "g.", MessageLog.Type.INFO);
            } else {
                game.log("Caught: " + CATCH_TABLE[catchIndex][0] + "! +" + goldWon + "g", MessageLog.Type.LOOT);
            }
        }

        SoundManager.getInstance().play(catchIndex <= 1 ? "coin" : "victory");
        state = State.CATCH_RESULT;
    }

    // ── Paint ───────────────────────────────────────────────────────────────

    @Override
    public void paint(Graphics2D g, int px, int py, int pw, int ph) {
        g.setFont(F_TITLE);
        g.setColor(ABYSS_TEAL);
        drawCentered(g, "DEEP SEA FISHING", px, py + 30, pw);

        switch (state) {
            case BET          -> paintBet(g, px, py, pw, ph);
            case WAITING      -> paintWaiting(g, px, py, pw, ph);
            case STRIKE       -> paintStrike(g, px, py, pw, ph);
            case REELING      -> paintReeling(g, px, py, pw, ph);
            case CATCH_RESULT -> paintCatch(g, px, py, pw, ph);
            case ESCAPED      -> paintEscaped(g, px, py, pw, ph);
        }
    }

    private void paintBet(Graphics2D g, int px, int py, int pw, int ph) {
        g.setFont(F_MENU);
        g.setColor(TEXT_BRIGHT);
        drawCentered(g, "Cast your line into the abyss. What lurks below?", px, py + 65, pw);

        g.setFont(F_BIG);
        g.setColor(ABYSS_TEAL);
        drawCentered(g, "Bait Cost: " + overlay.betAmount + "g", px, py + ph / 2 - 10, pw);

        g.setFont(F_ITEM);
        g.setColor(AMBER);
        drawCentered(g, "Gold: " + game.getPlayer().getGold() + "g", px, py + ph / 2 + 20, pw);

        g.setFont(F_SMALL);
        g.setColor(TEXT_DIM);
        drawCentered(g, "Common (0.5x) \u2022 Uncommon (1.2x) \u2022 Rare (2.5x + item) \u2022 Epic (4x + item)", px, py + ph / 2 + 55, pw);
        drawCentered(g, "Most casts lose bait. The rare catches are what you fish for.", px, py + ph / 2 + 73, pw);

        g.setFont(F_KEY);
        g.setColor(TEXT_DIM);
        g.drawString("[Left/Right] Bet   [Shift] x10   [Enter] Cast!   [Esc] Back", px + 20, py + ph - 20);
    }

    private void paintWaiting(Graphics2D g, int px, int py, int pw, int ph) {
        // Water scene
        int waterY = py + 120;
        g.setColor(WATER_COL);
        g.fillRect(px + 40, waterY, pw - 80, 200);
        g.setColor(BORDER_COL);
        g.drawRect(px + 40, waterY, pw - 80, 200);

        // Fishing line
        int lineX = px + pw / 2;
        g.setColor(TEXT_DIM);
        g.drawLine(lineX, waterY - 20, lineX, waterY + 100);

        // Bobber
        long now = System.currentTimeMillis();
        int bobY = waterY + (int)(Math.sin(now * 0.003) * 4);
        g.setColor(PRESSURE_RED);
        g.fillOval(lineX - 4, bobY - 4, 8, 8);

        // Bubbles
        for (int i = 0; i < 5; i++) {
            int bx = px + 60 + ((int)(now * 0.02 + i * 47) % (pw - 120));
            int by = waterY + 20 + ((int)(now * 0.01 + i * 31) % 160);
            g.setColor(new Color(80, 200, 255, 40 + (i * 10)));
            g.fillOval(bx, by, 4 + i, 4 + i);
        }

        // Waiting text with animated dots
        int dots = (int)((now / 500) % 4);
        String dotStr = ".".repeat(dots);
        g.setFont(F_BIG);
        g.setColor(ABYSS_TEAL);
        drawCentered(g, "Waiting for a bite" + dotStr, px, py + 90, pw);

        g.setFont(F_KEY);
        g.setColor(TEXT_DIM);
        drawCentered(g, "[Esc] Cancel", px, py + ph - 20, pw);
    }

    private void paintStrike(Graphics2D g, int px, int py, int pw, int ph) {
        // Flashing STRIKE text
        long elapsed = System.currentTimeMillis() - strikeTime;
        boolean flash = (elapsed / 150) % 2 == 0;

        g.setFont(F_BIG);
        g.setColor(flash ? BIOLUM : ABYSS_TEAL);
        drawCentered(g, "S T R I K E !", px, py + ph / 2 - 30, pw);

        g.setFont(F_MENU);
        g.setColor(TEXT_BRIGHT);
        drawCentered(g, "[Enter] Reel it in!", px, py + ph / 2 + 10, pw);

        // Timer bar
        float progress = Math.max(0, 1.0f - (float) elapsed / STRIKE_WINDOW);
        int barW = pw - 100;
        int barX = px + 50;
        int barY = py + ph / 2 + 40;
        g.setColor(new Color(10, 20, 30));
        g.fillRect(barX, barY, barW, 14);
        g.setColor(progress > 0.4f ? BIOLUM : PRESSURE_RED);
        g.fillRect(barX, barY, (int)(barW * progress), 14);
        g.setColor(BORDER_COL);
        g.drawRect(barX, barY, barW, 14);
    }

    private void paintReeling(Graphics2D g, int px, int py, int pw, int ph) {
        // Round indicator
        g.setFont(F_ITEM);
        g.setColor(AMBER);
        drawCentered(g, "Pull " + (reelRound + 1) + " / " + reelTotal, px, py + 65, pw);

        // The fish lunges to one side — read the lunge, don't wait to be told.
        // (Deliberately no text naming the direction or the key: that was the answer.)
        g.setFont(F_BIG);
        g.setColor(BIOLUM);
        String arrow = reelDirection == 0 ? "\u25C0\u25C0\u25C0" : "\u25B6\u25B6\u25B6";
        int lunge = reelDirection == 0 ? -pw / 4 : pw / 4;
        drawCentered(g, arrow, px + lunge, py + ph / 2 - 20, pw);

        g.setFont(F_MENU);
        g.setColor(TEXT_DIM);
        drawCentered(g, "Haul against the lunge!", px, py + ph / 2 + 20, pw);

        // Timer bar
        long elapsed = System.currentTimeMillis() - reelShowTime;
        float progress = Math.max(0, 1.0f - (float) elapsed / getReelWindow());
        int barW = pw - 100;
        int barX = px + 50;
        int barY = py + ph / 2 + 50;
        g.setColor(new Color(10, 20, 30));
        g.fillRect(barX, barY, barW, 14);
        g.setColor(progress > 0.4f ? ABYSS_TEAL : PRESSURE_RED);
        g.fillRect(barX, barY, (int)(barW * progress), 14);
        g.setColor(BORDER_COL);
        g.drawRect(barX, barY, barW, 14);

        // Previous pulls
        g.setFont(F_SMALL);
        for (int i = 0; i < reelRound; i++) {
            g.setColor(BIOLUM);
            g.drawString("\u2713", px + 30 + i * 20, py + ph - 40);
        }

        g.setFont(F_KEY);
        g.setColor(TEXT_DIM);
        drawCentered(g, "[A / Left]   [D / Right]", px, py + ph - 20, pw);
    }

    private void paintCatch(Graphics2D g, int px, int py, int pw, int ph) {
        String catchName = CATCH_TABLE[catchIndex][0];
        String rarity = CATCH_TABLE[catchIndex][2];
        Color rarityCol = switch (rarity) {
            case "Common"   -> COMMON_COL;
            case "Uncommon" -> UNCOMMON_COL;
            case "Rare"     -> RARE_COL;
            case "Epic"     -> EPIC_COL;
            default         -> TRASH_COL;
        };

        // Fish ASCII art
        g.setFont(F_ITEM);
        g.setColor(rarityCol);
        drawCentered(g, "><((((\u00b0>", px, py + 100, pw);

        g.setFont(F_BIG);
        g.setColor(rarityCol);
        drawCentered(g, catchName, px, py + 140, pw);

        g.setFont(F_MENU);
        g.setColor(TEXT_DIM);
        drawCentered(g, "[" + rarity + "]", px, py + 165, pw);

        g.setFont(F_TITLE);
        g.setColor(ABYSS_TEAL);
        drawCentered(g, "+" + goldWon + "g", px, py + 210, pw);

        if (itemName != null) {
            g.setFont(F_ITEM);
            g.setColor(EPIC_COL);
            drawCentered(g, "Found: " + itemName + "!", px, py + 240, pw);
        }

        g.setFont(F_KEY);
        g.setColor(TEXT_DIM);
        drawCentered(g, "[Enter] Fish Again   [Esc] Menu", px, py + ph - 20, pw);
    }

    private void paintEscaped(Graphics2D g, int px, int py, int pw, int ph) {
        g.setFont(F_BIG);
        g.setColor(PRESSURE_RED);
        drawCentered(g, "The fish escaped!", px, py + ph / 2 - 20, pw);

        g.setFont(F_MENU);
        g.setColor(TEXT_DIM);
        drawCentered(g, "Lost " + overlay.betAmount + "g bait.", px, py + ph / 2 + 20, pw);

        g.setFont(F_KEY);
        g.setColor(TEXT_DIM);
        drawCentered(g, "[Enter] Try Again   [Esc] Menu", px, py + ph - 20, pw);
    }
}

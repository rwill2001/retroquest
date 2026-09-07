package io.cannonforge.retroquest.overlay;

import static io.cannonforge.retroquest.overlay.OverlayTheme.AMBER;
import static io.cannonforge.retroquest.overlay.OverlayTheme.BORDER_COL;
import static io.cannonforge.retroquest.overlay.OverlayTheme.F_ITEM;
import static io.cannonforge.retroquest.overlay.OverlayTheme.F_KEY;
import static io.cannonforge.retroquest.overlay.OverlayTheme.F_SMALL;
import static io.cannonforge.retroquest.overlay.OverlayTheme.TEXT_BRIGHT;
import static io.cannonforge.retroquest.overlay.OverlayTheme.TEXT_DIM;
import static io.cannonforge.retroquest.overlay.OverlayTheme.entryEase;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.util.Random;

import io.cannonforge.retroquest.core.Fonts;
import io.cannonforge.retroquest.core.MessageLog;
import io.cannonforge.retroquest.core.Retroquest;
import io.cannonforge.retroquest.core.SoundManager;

/**
 * Full-screen forge arena overlay with three martial mini-games: Jousting,
 * Axe Throwing, and Anvil Strike. All graphics are rendered via
 * {@code Graphics2D} — no external images. Follows the same pattern as
 * {@link CasinoOverlay}.
 */
public class ForgeArenaOverlay {

    // ── Volcanic palette ──────────────────────────────────────────────────────
    static final Color PANEL_BG   = new Color( 22,  10,   8);
    static final Color FORGE_GOLD = new Color(255, 180,  40);
    static final Color EMBER_RED  = new Color(200,  80,  30);

    static final Font F_TITLE = Fonts.monoBold(16);
    static final Font F_MENU  = Fonts.monoBold(14);
    static final Font F_BIG   = Fonts.monoBold(18);

    // ── State ─────────────────────────────────────────────────────────────────
    final Retroquest game;
    private boolean active = false;
    private boolean inMenu = true;

    // Menu
    private int menuSelection = 0;
    private static final String[] MENU_ITEMS = {"Jousting", "Axe Throwing", "Anvil Strike", "Leave"};

    // Betting (package-private so mini-games can access)
    int betAmount = 1;
    int lastBet = 1;

    // Active mini-game (null when in menu)
    private MiniGame currentGame;

    // Mini-game instances
    private final JoustingGame joustGame;
    private final AxeThrowingGame axeGame;
    private final AnvilStrikeGame anvilGame;

    // Geometry cache for mouse hit-testing
    private final Rectangle[] menuRects = new Rectangle[4];

    final Random rng = new Random();

    // ── Entry animation ───────────────────────────────────────────────────────
    private long entryTime = 0;
    private static final long ENTRY_MS = 280;

    // ── Constructor & Public API ──────────────────────────────────────────────

    public ForgeArenaOverlay(Retroquest game) {
        this.game = game;
        for (int i = 0; i < menuRects.length; i++) menuRects[i] = new Rectangle();

        joustGame = new JoustingGame(game, this, rng);
        axeGame   = new AxeThrowingGame(game, this, rng);
        anvilGame = new AnvilStrikeGame(game, this, rng);
    }

    public void open() {
        active = true;
        inMenu = true;
        currentGame = null;
        menuSelection = 0;
        betAmount = lastBet;
        entryTime = System.currentTimeMillis();
        if (game.getPlayer().getGold() <= 0) {
            betAmount = 0;
        }
    }

    public void close() { active = false; }
    public boolean isActive() { return active; }

    // ── Input ─────────────────────────────────────────────────────────────────

    public void handleKey(KeyEvent e) {
        // Escape belongs to the hub, not the table: a game that does not answer it in the
        // phase it is in would otherwise strand the player inside the overlay.
        if (e.getKeyCode() == KeyEvent.VK_ESCAPE && currentGame != null) {
            backToMenu();
            SoundManager.getInstance().play("menublip");
            return;
        }
        if (currentGame != null) {
            currentGame.handleKey(e);
        } else {
            handleMenuKey(e);
        }
    }

    public void handleClick(int mx, int my) {
        if (inMenu) {
            for (int i = 0; i < menuRects.length; i++) {
                if (menuRects[i].contains(mx, my)) {
                    menuSelection = i;
                    confirmMenu();
                    return;
                }
            }
        }
    }

    // ── Menu ──────────────────────────────────────────────────────────────────

    private void handleMenuKey(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_UP, KeyEvent.VK_K -> {
                menuSelection = (menuSelection - 1 + MENU_ITEMS.length) % MENU_ITEMS.length;
                SoundManager.getInstance().play("menublip");
            }
            case KeyEvent.VK_DOWN, KeyEvent.VK_J -> {
                menuSelection = (menuSelection + 1) % MENU_ITEMS.length;
                SoundManager.getInstance().play("menublip");
            }
            case KeyEvent.VK_ENTER -> confirmMenu();
            case KeyEvent.VK_ESCAPE -> close();
        }
    }

    private void confirmMenu() {
        if (menuSelection < 3) {
            if (!checkGold() || !checkGamblingCap()) return;
            betAmount = Math.min(lastBet, Math.min(game.getPlayer().getGold(), game.getPlayer().getMaxBet()));
            if (betAmount < 1) betAmount = 1;
            MiniGame selected = switch (menuSelection) {
                case 0 -> joustGame;
                case 1 -> axeGame;
                case 2 -> anvilGame;
                default -> null;
            };
            if (selected != null) {
                selected.reset(betAmount);
                currentGame = selected;
                inMenu = false;
            }
        } else {
            close();
        }
    }

    private boolean checkGold() {
        if (game.getPlayer().getGold() <= 0) {
            game.log("You have no gold to wager!", MessageLog.Type.DANGER);
            return false;
        }
        return true;
    }

    private boolean checkGamblingCap() {
        if (game.getPlayer().isGamblingCapped()) {
            game.log("You've won enough for now. Rest at an inn to gamble again.", MessageLog.Type.INFO);
            return false;
        }
        return true;
    }

    void backToMenu() {
        currentGame = null;
        inMenu = true;
    }

    void adjustBet(int delta) {
        int maxBet = Math.min(game.getPlayer().getGold(), game.getPlayer().getMaxBet());
        betAmount = Math.max(1, Math.min(maxBet, betAmount + delta));
        SoundManager.getInstance().play("menublip");
    }

    // ── Paint ─────────────────────────────────────────────────────────────────

    public void paint(Graphics2D g, int W, int H) {
        float ease = entryEase(entryTime, ENTRY_MS);

        g.setColor(new Color(0, 0, 0, (int)(200 * ease)));
        g.fillRect(0, 0, W, H);

        int pw = Math.min(700, W - 40);
        int ph = Math.min(520, H - 40);
        int px = (W - pw) / 2;
        int py = (int)((H - ph) / 2.0 * ease) + (int)((1f - ease) * H);

        drawPanel(g, px, py, pw, ph);

        if (currentGame != null) {
            currentGame.paint(g, px, py, pw, ph);
            currentGame.update();
        } else {
            paintMenu(g, px, py, pw, ph);
        }
    }

    // ── Panel drawing ─────────────────────────────────────────────────────────

    static void drawPanel(Graphics2D g, int x, int y, int w, int h) {
        g.setColor(PANEL_BG);
        g.fillRect(x, y, w, h);
        g.setColor(EMBER_RED);
        g.drawRect(x, y, w, h);
        g.setColor(BORDER_COL);
        g.drawRect(x + 1, y + 1, w - 2, h - 2);
    }

    // ── Menu paint ────────────────────────────────────────────────────────────

    private void paintMenu(Graphics2D g, int px, int py, int pw, int ph) {
        // Title
        g.setFont(F_TITLE);
        g.setColor(FORGE_GOLD);
        String title = "FORGE ARENA";
        int tw = g.getFontMetrics().stringWidth(title);
        g.drawString(title, px + (pw - tw) / 2, py + 35);

        // Subtitle
        g.setFont(F_SMALL);
        g.setColor(game.getPlayer().isGamblingCapped() ? new Color(200, 80, 80) : TEXT_DIM);
        String sub = game.getPlayer().isGamblingCapped()
            ? "You've won enough today. Rest at an inn to reset."
            : "Test your mettle, warrior!";
        int sw = g.getFontMetrics().stringWidth(sub);
        g.drawString(sub, px + (pw - sw) / 2, py + 55);

        // Gold display
        g.setFont(F_ITEM);
        g.setColor(AMBER);
        String gold = "Gold: " + game.getPlayer().getGold() + "g";
        g.drawString(gold, px + pw - g.getFontMetrics().stringWidth(gold) - 20, py + 35);

        // Winnings display
        g.setFont(F_SMALL);
        g.setColor(TEXT_DIM);
        String winnings = "Winnings: " + game.getPlayer().getGamblingNetWinnings()
            + "/" + game.getPlayer().getGamblingWinningsCap() + "g  (Max bet: " + game.getPlayer().getMaxBet() + "g)";
        g.drawString(winnings, px + pw - g.getFontMetrics().stringWidth(winnings) - 20, py + 55);

        // Menu items
        g.setFont(F_MENU);
        int itemH = 40;
        int startY = py + 100;

        for (int i = 0; i < MENU_ITEMS.length; i++) {
            int iy = startY + i * itemH;
            int iw = 280;
            int ix = px + (pw - iw) / 2;

            menuRects[i].setBounds(ix, iy, iw, itemH - 4);

            if (i == menuSelection) {
                g.setColor(new Color(50, 20, 5));
                g.fillRect(ix, iy, iw, itemH - 4);
                g.setColor(EMBER_RED);
                g.drawRect(ix, iy, iw, itemH - 4);
                g.setColor(FORGE_GOLD);
            } else {
                g.setColor(new Color(28, 14, 10));
                g.fillRect(ix, iy, iw, itemH - 4);
                g.setColor(BORDER_COL);
                g.drawRect(ix, iy, iw, itemH - 4);
                g.setColor(TEXT_BRIGHT);
            }

            String label = (i == menuSelection ? "> " : "  ") + MENU_ITEMS[i];
            int lw = g.getFontMetrics().stringWidth(label);
            g.drawString(label, ix + (iw - lw) / 2, iy + itemH / 2 + 2);
        }

        // Key bar
        g.setFont(F_KEY);
        g.setColor(TEXT_DIM);
        String keys = "[Up/Down] Select   [Enter] Confirm   [Esc] Leave";
        g.drawString(keys, px + 20, py + ph - 20);
    }

    // ── Utility (package-private static for mini-games) ───────────────────────

    static void drawCentered(Graphics2D g, String text, int areaX, int y, int areaW) {
        int tw = g.getFontMetrics().stringWidth(text);
        g.drawString(text, areaX + (areaW - tw) / 2, y);
    }

    static void drawCenteredAt(Graphics2D g, String text, int cx, int cy) {
        int tw = g.getFontMetrics().stringWidth(text);
        g.drawString(text, cx - tw / 2, cy);
    }
}

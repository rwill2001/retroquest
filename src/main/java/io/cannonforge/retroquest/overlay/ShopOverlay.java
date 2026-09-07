package io.cannonforge.retroquest.overlay;
import static io.cannonforge.retroquest.overlay.OverlayTheme.AMBER;
import static io.cannonforge.retroquest.overlay.OverlayTheme.BG;
import static io.cannonforge.retroquest.overlay.OverlayTheme.BORDER_COL;
import static io.cannonforge.retroquest.overlay.OverlayTheme.CYAN_ACC;
import static io.cannonforge.retroquest.overlay.OverlayTheme.F_DESC;
import static io.cannonforge.retroquest.overlay.OverlayTheme.F_ITEM;
import static io.cannonforge.retroquest.overlay.OverlayTheme.F_KEY;
import static io.cannonforge.retroquest.overlay.OverlayTheme.F_LABEL;
import static io.cannonforge.retroquest.overlay.OverlayTheme.F_SMALL;
import static io.cannonforge.retroquest.overlay.OverlayTheme.F_TITLE;
import static io.cannonforge.retroquest.overlay.OverlayTheme.PHOSPHOR;
import static io.cannonforge.retroquest.overlay.OverlayTheme.ROW_ALT;
import static io.cannonforge.retroquest.overlay.OverlayTheme.TEXT_BRIGHT;
import static io.cannonforge.retroquest.overlay.OverlayTheme.TEXT_DIM;
import static io.cannonforge.retroquest.overlay.OverlayTheme.entryEase;
import static io.cannonforge.retroquest.overlay.OverlayTheme.paintScrollIndicators;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

import io.cannonforge.retroquest.core.GamePanel;
import io.cannonforge.retroquest.core.MessageLog;
import io.cannonforge.retroquest.core.Retroquest;
import io.cannonforge.retroquest.model.InventorySlot;
import io.cannonforge.retroquest.model.Item;
import io.cannonforge.retroquest.model.Player;
import io.cannonforge.retroquest.registry.ItemRegistry;

/**
 * In-panel shop overlay — renders inside {@link GamePanel} without a JDialog.
 *
 * <p>Presents BUY and SELL tabs navigable with arrow keys and Tab. All
 * feedback is written to the {@link MessageLog}; no popup dialogs are used.
 *
 * <p>Layout:
 * <pre>
 * ┌─────────────────────────────────────────────┐
 * │  ◈  GENERAL STORE                 Gold: Xg  │  title
 * ├──────────────────────────────────────────────┤
 * │  [ BUY ]   [ SELL ]                          │  tab bar
 * ├──────────────────────────────────────────────┤
 * │  scrollable item list                        │  list
 * ├──────────────────────────────────────────────┤
 * │  detail line                                 │  detail
 * ├──────────────────────────────────────────────┤
 * │  [↑↓] Navigate  [Enter] Buy/Sell  [Tab] Mode │  key bar
 * └─────────────────────────────────────────────┘
 * </pre>
 */
public class ShopOverlay {

    private static final Color SEL_BG     = new Color(30, 22, 0);
    private static final Color TAB_ACTIVE = new Color(40, 30, 0);
    private static final Font  F_STAT     = F_DESC;  // alias

    // ── State ─────────────────────────────────────────────────────────────────
    private final Retroquest game;
    private boolean active    = false;
    private int     mode      = 0;        // 0 = BUY, 1 = SELL
    private int     selected  = 0;
    private int     scrollTop = 0;
    private long    entryTime = 0;
    private static final long ENTRY_MS = 280;

    // Shop stock (loaded once per open)
    private record ShopStock(Item item, int price) {}
    private final List<ShopStock> stock = new ArrayList<>();

    // Sell list — maps display-row → actual inventory slot index
    private record SellEntry(int slotIndex, Item item, int sellPrice, int quantity) {}
    private final List<SellEntry> sellList = new ArrayList<>();

    // Cached geometry for mouse hit-testing
    private int listX, listY, listW, rowH, maxRows;
    private int listRowTop, listRowCount;      // first painted row's y and how many are visible
    private Rectangle cardRect      = new Rectangle();   // whole panel — clicks outside are ignored
    private Rectangle tabBuyRect    = new Rectangle();
    private Rectangle tabSellRect   = new Rectangle();
    private Rectangle badgeAction   = new Rectangle();  // Enter / Buy / Sell
    private Rectangle badgeSwitchMode = new Rectangle(); // Tab
    private Rectangle badgeClose    = new Rectangle();  // Esc / Close

    // ── Public API ────────────────────────────────────────────────────────────

    public ShopOverlay(Retroquest game) {
        this.game = game;
    }

    public boolean isActive() { return active; }

    public void open(List<String> npcItemIds) {
        stock.clear();
        int playerLevel = game.getPlayer().getLevel();
        // CHA discount: up to 20% off buy prices (2% per point above 10)
        int chaBonus = Math.max(0, game.getPlayer().getCharisma() - 10);
        double buyMult = 1.0 - chaBonus * 0.02;  // e.g. CHA 15 → 0.90, CHA 20 → 0.80
        if (npcItemIds != null && !npcItemIds.isEmpty()) {
            for (String id : npcItemIds) {
                Item item = ItemRegistry.getById(id);
                if (item != null && item.getTier() <= playerLevel)
                    stock.add(new ShopStock(item, Math.max(1, (int)(item.getPrice() * buyMult))));
            }
        } else {
            for (Item item : ItemRegistry.getAllShopItems())
                if (item.getTier() <= playerLevel)
                    stock.add(new ShopStock(item, Math.max(1, (int)(item.getPrice() * buyMult))));
        }
        mode      = 0;
        selected  = 0;
        scrollTop = 0;
        entryTime = System.currentTimeMillis();
        active    = true;
        refreshSellList();
        game.log("Welcome, adventurer!  Browse our wares.", MessageLog.Type.GOOD);
    }

    public void close() {
        active = false;
        game.log("Safe travels.", MessageLog.Type.DIM);
    }

    // ── Read-only view of the counter ────────────────────────────────────────
    // The shop's contents are otherwise only visible as pixels, which leaves both the
    // playtest harness and any future UI unable to answer "what is on sale here".

    /** 0 while buying, 1 while selling. */
    public int getMode() { return mode; }

    /** Row the cursor is on, in whichever list {@link #getMode()} names. */
    public int getSelectedRow() { return selected; }

    /** Item ids on sale, in row order. */
    public java.util.List<String> getStockIds() {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (ShopStock s : stock) out.add(s.item().getId());
        return out;
    }

    /** Asking prices, in the same order as {@link #getStockIds()}. */
    public java.util.List<Integer> getStockPrices() {
        java.util.List<Integer> out = new java.util.ArrayList<>();
        for (ShopStock s : stock) out.add(s.price());
        return out;
    }

    /** Item ids the shop will buy back, in row order of the sell list. */
    public java.util.List<String> getSellIds() {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (SellEntry s : sellList) out.add(s.item().getId());
        return out;
    }

    /** What the shop pays for each row of {@link #getSellIds()}. */
    public java.util.List<Integer> getSellPrices() {
        java.util.List<Integer> out = new java.util.ArrayList<>();
        for (SellEntry s : sellList) out.add(s.sellPrice());
        return out;
    }

    // ── Input ─────────────────────────────────────────────────────────────────

    public void handleKey(KeyEvent e) {
        if (!active) return;
        int listSize = (mode == 0) ? stock.size() : sellList.size();
        switch (e.getKeyCode()) {
            case KeyEvent.VK_ESCAPE        -> close();
            case KeyEvent.VK_TAB           -> switchMode();
            case KeyEvent.VK_UP, KeyEvent.VK_K -> {
                if (selected > 0) { selected--; clampScroll(listSize); }
            }
            case KeyEvent.VK_DOWN, KeyEvent.VK_J -> {
                if (selected < listSize - 1) { selected++; clampScroll(listSize); }
            }
            case KeyEvent.VK_ENTER -> performAction();
        }
    }

    public void handleClick(int mx, int my) {
        if (!active) return;
        if (!cardRect.contains(mx, my)) return;   // clicks outside the panel do nothing

        // Key bar badge clicks
        if (badgeClose.contains(mx, my))      { close(); return; }
        if (badgeSwitchMode.contains(mx, my)) { switchMode(); return; }
        if (badgeAction.contains(mx, my))     { performAction(); return; }

        // Tab bar clicks
        if (tabBuyRect.contains(mx, my))  { setMode(0); return; }
        if (tabSellRect.contains(mx, my)) { setMode(1); return; }

        // List row clicks — single click selects, click on already-selected row acts
        int listSize = (mode == 0) ? stock.size() : sellList.size();
        if (rowH > 0 && listRowCount > 0
                && mx >= listX && mx < listX + listW
                && my >= listRowTop && my < listRowTop + listRowCount * rowH) {
            int row = scrollTop + (my - listRowTop) / rowH;
            if (row >= 0 && row < listSize) {
                if (row == selected) performAction();
                else                 { selected = row; clampScroll(listSize); }
            }
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void switchMode() { setMode(1 - mode); }

    private void setMode(int m) {
        mode      = m;
        selected  = 0;
        scrollTop = 0;
        if (mode == 1) refreshSellList();
    }

    private void refreshSellList() {
        sellList.clear();
        InventorySlot[] slots = game.getPlayer().getInventorySlots();
        for (int i = 0; i < slots.length; i++) {
            InventorySlot slot = slots[i];
            if (!slot.isEmpty()) {
                sellList.add(new SellEntry(i, slot.getItem(), sellPrice(slot.getItem()), slot.getQuantity()));
            }
        }
    }

    private void performAction() {
        if (mode == 0) buy();
        else           sell();
    }

    private void buy() {
        if (stock.isEmpty() || selected >= stock.size()) return;
        ShopStock s      = stock.get(selected);
        Player    player = game.getPlayer();

        if (player.getGold() < s.price()) {
            game.log("Not enough gold!  You need " + s.price() + "g.", MessageLog.Type.DANGER);
            return;
        }
        if (s.item().getType() == Item.Type.KEY) {
            if (player.hasKey(s.item().getId())) {
                game.log("You already have that key.", MessageLog.Type.DIM);
                return;
            }
            player.addKey(s.item().getId());
        } else {
            if (!player.addItemAndProgress(s.item())) {
                game.log("Your pack is full!  Drop something first.", MessageLog.Type.DANGER);
                return;
            }
        }
        player.addGold(-s.price());
        game.log("Purchased " + s.item().getName() + " for " + s.price() + "g.", MessageLog.Type.GOOD);
        game.getStatsPanel().refresh();
    }

    private void sell() {
        if (sellList.isEmpty() || selected >= sellList.size()) return;
        SellEntry entry  = sellList.get(selected);
        Player    player = game.getPlayer();

        // One unit per keypress — the price quoted is for a single item, so a
        // stack must not be wiped out by one sale.
        InventorySlot slot = player.getInventorySlots()[entry.slotIndex()];
        if (slot.isEmpty() || !slot.getItem().getId().equals(entry.item().getId())) {
            refreshSellList();   // inventory moved under us
            return;
        }
        if (slot.getQuantity() > 1) slot.setQuantity(slot.getQuantity() - 1);
        else                        player.removeItem(entry.slotIndex());

        player.addGold(entry.sellPrice());
        game.log("Sold " + entry.item().getName() + " for " + entry.sellPrice() + "g.", MessageLog.Type.GOOD);
        game.getStatsPanel().refresh();

        // Refresh and clamp selection
        refreshSellList();
        if (selected >= sellList.size()) selected = Math.max(0, sellList.size() - 1);
    }

    private void clampScroll(int listSize) {
        if (selected < scrollTop)           scrollTop = selected;
        if (selected >= scrollTop + maxRows) scrollTop = selected - maxRows + 1;
        scrollTop = Math.max(0, Math.min(scrollTop, Math.max(0, listSize - maxRows)));
    }

    private int sellPrice(Item i) {
        // CHA bonus: up to 10% more sell value (1% per point above 10)
        int chaBonus = Math.max(0, game.getPlayer().getCharisma() - 10);
        double sellMult = 1.0 + chaBonus * 0.01;  // e.g. CHA 15 → 1.05, CHA 20 → 1.10
        return Math.max(1, (int)(i.getSellPrice() * sellMult));
    }

    // ── Paint ─────────────────────────────────────────────────────────────────

    public void paint(Graphics2D g, int W, int H) {
        if (!active) return;

        float ease = entryEase(entryTime, ENTRY_MS);

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Dim background
        g.setColor(new Color(0, 0, 0, (int)(180 * ease)));
        g.fillRect(0, 0, W, H);

        int cW = Math.min(OverlayTheme.scaled(680), W - 40);
        int cH = Math.min(OverlayTheme.scaled(480), H - 40);
        int cX = (W - cW) / 2;
        int cY = (int)((H - cH) / 2.0 * ease) + (int)((1f - ease) * H);  // slide from bottom

        // Card shadow
        g.setColor(new Color(0, 0, 0, (int)(100 * ease)));
        g.fillRoundRect(cX + 4, cY + 4, cW, cH, 6, 6);

        // Card body
        g.setPaint(new GradientPaint(cX, cY, new Color(12, 10, 6), cX, cY + cH, BG));
        g.fillRoundRect(cX, cY, cW, cH, 6, 6);

        // Border — amber for shop flavour
        g.setStroke(new BasicStroke(1.5f));
        g.setColor(new Color(255, 200, 50, 160));
        g.drawRoundRect(cX, cY, cW, cH, 6, 6);
        g.setStroke(new BasicStroke(1f));

        int titleH  = OverlayTheme.scaled(36);
        int tabH    = OverlayTheme.scaled(30);
        int keybarH = OverlayTheme.scaled(34);
        int detailH = OverlayTheme.scaled(48);
        int bodyH   = cH - titleH - tabH - detailH - keybarH;

        // Cache geometry for mouse hit-testing
        cardRect = new Rectangle(cX, cY, cW, cH);
        listX = cX;
        listY = cY + titleH + tabH;
        listW = cW;

        paintTitle (g, cX, cY,                                   cW, titleH);
        paintTabs  (g, cX, cY + titleH,                          cW, tabH);
        paintList  (g, cX, cY + titleH + tabH,                   cW, bodyH);
        paintDetail(g, cX, cY + titleH + tabH + bodyH,           cW, detailH);
        paintKeyBar(g, cX, cY + titleH + tabH + bodyH + detailH, cW, keybarH);
    }

    private void paintTitle(Graphics2D g, int x, int y, int w, int h) {
        g.setPaint(new GradientPaint(x, y, new Color(22, 16, 4), x + w, y, new Color(8, 12, 16)));
        g.fillRoundRect(x, y, w, h, 6, 6);
        g.fillRect(x, y + h / 2, w, h / 2);
        g.setColor(BORDER_COL);
        g.drawLine(x, y + h, x + w, y + h);

        g.setFont(F_TITLE);
        FontMetrics fm = g.getFontMetrics();
        int ty = y + (h + fm.getAscent() - fm.getDescent()) / 2;

        g.setColor(new Color(255, 200, 50, 35));
        g.drawString("\u25c8  GENERAL  STORE", x + 15, ty + 1);
        g.setColor(AMBER);
        g.drawString("\u25c8  GENERAL  STORE", x + 14, ty);

        g.setFont(F_SMALL);
        FontMetrics fmS = g.getFontMetrics();
        String gold = "Gold: " + game.getPlayer().getGold() + "g";
        g.setColor(AMBER);
        g.drawString(gold, x + w - fmS.stringWidth(gold) - 14, ty);
    }

    private void paintTabs(Graphics2D g, int x, int y, int w, int h) {
        g.setColor(new Color(8, 6, 2));
        g.fillRect(x, y, w, h);
        g.setColor(BORDER_COL);
        g.drawLine(x, y + h, x + w, y + h);

        String[] labels = { "  BUY  ", "  SELL  " };
        g.setFont(F_KEY);
        int tabW = OverlayTheme.scaled(80);
        for (String label : labels)
            tabW = Math.max(tabW, g.getFontMetrics().stringWidth(label) + OverlayTheme.scaled(16));
        int tx   = x + 14;
        for (int i = 0; i < labels.length; i++) {
            boolean sel = (i == mode);
            Rectangle r = new Rectangle(tx, y + 4, tabW, h - 8);
            if (i == 0) tabBuyRect  = r;
            else        tabSellRect = r;

            if (sel) {
                g.setColor(TAB_ACTIVE);
                g.fillRoundRect(r.x, r.y, r.width, r.height, 4, 4);
                g.setColor(AMBER);
                g.setStroke(new BasicStroke(1.5f));
                g.drawRoundRect(r.x, r.y, r.width, r.height, 4, 4);
                g.setStroke(new BasicStroke(1f));
            } else {
                g.setColor(new Color(BORDER_COL.getRed(), BORDER_COL.getGreen(), BORDER_COL.getBlue(), 80));
                g.drawRoundRect(r.x, r.y, r.width, r.height, 4, 4);
            }

            g.setFont(F_KEY);
            FontMetrics fm = g.getFontMetrics();
            int lx = r.x + (r.width - fm.stringWidth(labels[i])) / 2;
            int ly = r.y + (r.height + fm.getAscent() - fm.getDescent()) / 2;
            g.setColor(sel ? AMBER : TEXT_DIM);
            g.drawString(labels[i], lx, ly);

            tx += tabW + 6;
        }
    }

    private void paintList(Graphics2D g, int x, int y, int w, int h) {
        g.setColor(BG);
        g.fillRect(x, y, w, h);

        g.setFont(F_ITEM);
        FontMetrics fm = g.getFontMetrics();
        rowH    = fm.getHeight() + OverlayTheme.scaled(5);
        maxRows = Math.max(1, (h - 6) / rowH);

        List<?> items = (mode == 0) ? stock : sellList;
        int n = items.size();

        // Clamp scroll
        if (selected >= scrollTop + maxRows) scrollTop = selected - maxRows + 1;
        if (selected < scrollTop)            scrollTop = selected;
        scrollTop = Math.max(0, Math.min(scrollTop, Math.max(0, n - maxRows)));

        listRowTop   = y + 4;
        listRowCount = Math.max(0, Math.min(maxRows, n - scrollTop));

        if (n == 0) {
            g.setFont(F_STAT);
            g.setColor(TEXT_DIM);
            String msg = mode == 0 ? "No items in stock." : "Nothing to sell.";
            g.drawString(msg, x + 14, y + 30);
            return;
        }

        int dy = y + 4;
        for (int i = scrollTop; i < n && i < scrollTop + maxRows; i++) {
            boolean sel = (i == selected);

            g.setColor(sel ? SEL_BG : (i % 2 == 0 ? BG : ROW_ALT));
            g.fillRect(x, dy, w, rowH);
            if (sel) { g.setColor(AMBER); g.fillRect(x, dy, 3, rowH); }

            int textY = dy + rowH - (rowH - fm.getAscent()) / 2 - 1;

            if (mode == 0) {
                ShopStock s = stock.get(i);
                // Icon
                g.setFont(F_ITEM);
                String icon = itemIcon(s.item());
                g.setColor(sel ? AMBER : new Color(AMBER.getRed(), AMBER.getGreen(), AMBER.getBlue(), 150));
                g.drawString(icon, x + 10, textY);
                int iconW = fm.stringWidth(icon) + 6;

                // Name
                g.setColor(sel ? AMBER : TEXT_BRIGHT);
                g.drawString(s.item().getName(), x + 10 + iconW, textY);

                // Price (right-aligned)
                g.setFont(F_KEY);
                FontMetrics fmK = g.getFontMetrics();
                String price = s.price() + "g";
                g.setColor(sel ? AMBER : PHOSPHOR);
                g.drawString(price, x + w - fmK.stringWidth(price) - 14, textY);
                g.setFont(F_ITEM);

            } else {
                SellEntry se = sellList.get(i);
                // Slot tag
                g.setFont(F_KEY);
                FontMetrics fmK = g.getFontMetrics();
                String slotTag = "[" + (se.slotIndex() + 1) + "]";
                g.setColor(sel ? AMBER : TEXT_DIM);
                g.drawString(slotTag, x + 10, textY);
                int tagW = fmK.stringWidth(slotTag) + 8;
                g.setFont(F_ITEM);

                // Icon + name
                String icon = itemIcon(se.item());
                g.setColor(sel ? AMBER : new Color(CYAN_ACC.getRed(), CYAN_ACC.getGreen(), CYAN_ACC.getBlue(), 150));
                g.drawString(icon, x + 10 + tagW, textY);
                int iconW = fm.stringWidth(icon) + 6;

                g.setColor(sel ? AMBER : TEXT_BRIGHT);
                g.drawString(se.item().getName() + (se.quantity() > 1 ? "  x" + se.quantity() : ""),
                             x + 10 + tagW + iconW, textY);

                // Sell value (right-aligned)
                g.setFont(F_KEY);
                String val = se.sellPrice() + "g";
                g.setColor(sel ? AMBER : CYAN_ACC);
                g.drawString(val, x + w - fmK.stringWidth(val) - 14, textY);
                g.setFont(F_ITEM);
            }

            dy += rowH;
        }

        paintScrollIndicators(g, scrollTop, maxRows, n, x, y, w, h);
    }

    private void paintDetail(Graphics2D g, int x, int y, int w, int h) {
        g.setColor(new Color(7, 6, 2));
        g.fillRect(x, y, w, h);
        g.setColor(BORDER_COL);
        g.drawLine(x, y, x + w, y);
        g.drawLine(x, y + h, x + w, y + h);

        List<?> items = (mode == 0) ? stock : sellList;
        if (items.isEmpty() || selected >= items.size()) {
            g.setFont(F_STAT); g.setColor(TEXT_DIM);
            g.drawString(mode == 0 ? "Select an item to buy." : "Select an item to sell.", x + 14, y + h / 2 + 4);
            return;
        }

        Item item; int price; String label;
        if (mode == 0) {
            ShopStock s = stock.get(selected);
            item  = s.item();
            price = s.price();
            label = "Buy for " + price + "g";
        } else {
            SellEntry se = sellList.get(selected);
            item  = se.item();
            price = se.sellPrice();
            label = "Sell for " + price + "g";
        }

        int midY = y + 10;
        g.setFont(F_LABEL);
        FontMetrics fmL = g.getFontMetrics();
        g.setColor(AMBER);
        g.drawString(item.getName(), x + 14, midY + fmL.getAscent());

        g.setFont(F_STAT);
        FontMetrics fmS = g.getFontMetrics();
        g.setColor(TEXT_BRIGHT);
        g.drawString(buildDetail(item), x + 14, midY + fmL.getHeight() + fmS.getAscent() + 2);

        // Price pill (right side)
        g.setFont(F_KEY);
        FontMetrics fmK = g.getFontMetrics();
        int pw = fmK.stringWidth(label) + 16;
        int ph = fmK.getHeight() + OverlayTheme.scaled(8);
        int px = x + w - pw - 12;
        int py = y + (h - ph) / 2;
        g.setColor(new Color(AMBER.getRed(), AMBER.getGreen(), AMBER.getBlue(), 30));
        g.fillRoundRect(px, py, pw, ph, 4, 4);
        g.setColor(AMBER);
        g.setStroke(new BasicStroke(1f));
        g.drawRoundRect(px, py, pw, ph, 4, 4);
        g.drawString(label, px + 8, py + (ph + fmK.getAscent() - fmK.getDescent()) / 2);
    }

    private void paintKeyBar(Graphics2D g, int x, int y, int w, int h) {
        g.setPaint(new GradientPaint(x, y, new Color(14, 10, 4), x, y + h, BG));
        g.fillRect(x, y, w, h);
        g.fillRoundRect(x, y + h / 2, w, h / 2 + 4, 6, 6);
        g.setColor(BORDER_COL);
        g.drawLine(x, y, x + w, y);

        String action = (mode == 0) ? "Buy" : "Sell";
        String[][] keys = {{"↑↓","Navigate"},{"Enter", action},{"Tab","Buy/Sell"},{"Esc","Close"}};
        Rectangle[] badges = OverlayTheme.paintKeyBadges(g, x, y, w, h, keys, AMBER);
        if (badges.length >= 4) {
            badgeAction     = badges[1];
            badgeSwitchMode = badges[2];
            badgeClose      = badges[3];
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String itemIcon(Item item) {
        return switch (item.getType()) {
            case WEAPON          -> "\u2694";
            case ARMOR, HELM     -> "\u25a3";
            case SHIELD          -> "\u25d1";
            case AMULET          -> "\u25c7";
            case POTION          -> "\u2665";
            case RING_PROTECTION,
                 RING_REGEN      -> "\u25ce";
            default              -> "\u25b8";
        };
    }

    private String buildDetail(Item item) {
        return switch (item.getType()) {
            case WEAPON          -> "Damage bonus: +" + item.getValue() + "  |  Weapon";
            case ARMOR           -> "AC bonus: +"     + item.getValue() + "  |  Armor";
            case HELM            -> "AC bonus: +"     + item.getValue() + "  |  Helm";
            case SHIELD          -> "AC bonus: +"     + item.getValue() + "  |  Shield";
            case AMULET          -> "Passive  |  Amulet" + (item.getValue() > 0 ? " +" + item.getValue() : "");
            case POTION          -> "Heals: "         + item.getValue() + " HP  |  Consumable";
            case RING_PROTECTION -> "Protection: +"   + item.getValue() + "  |  Ring";
            case RING_REGEN      -> "Regen: +"        + item.getValue() + "/turn  |  Ring";
            default              -> item.getType() != null ? item.getType().name() : "Misc";
        };
    }
}

package io.cannonforge.retroquest.overlay;
import static io.cannonforge.retroquest.overlay.OverlayTheme.AMBER;
import static io.cannonforge.retroquest.overlay.OverlayTheme.BG;
import static io.cannonforge.retroquest.overlay.OverlayTheme.BORDER_COL;
import static io.cannonforge.retroquest.overlay.OverlayTheme.CYAN_ACC;
import static io.cannonforge.retroquest.overlay.OverlayTheme.DANGER;
import static io.cannonforge.retroquest.overlay.OverlayTheme.F_DESC;
import static io.cannonforge.retroquest.overlay.OverlayTheme.F_ITEM;
import static io.cannonforge.retroquest.overlay.OverlayTheme.F_KEY;
import static io.cannonforge.retroquest.overlay.OverlayTheme.F_LABEL;
import static io.cannonforge.retroquest.overlay.OverlayTheme.F_SMALL;
import static io.cannonforge.retroquest.overlay.OverlayTheme.F_TITLE;
import static io.cannonforge.retroquest.overlay.OverlayTheme.GOOD;
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
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.util.List;

import io.cannonforge.retroquest.core.GamePanel;
import io.cannonforge.retroquest.core.MessageLog;
import io.cannonforge.retroquest.core.Retroquest;
import io.cannonforge.retroquest.core.SoundManager;
import io.cannonforge.retroquest.model.Effect;
import io.cannonforge.retroquest.model.InventorySlot;
import io.cannonforge.retroquest.model.Item;
import io.cannonforge.retroquest.model.ItemSlot;
import io.cannonforge.retroquest.model.Player;
import io.cannonforge.retroquest.model.Spell;
import io.cannonforge.retroquest.registry.ItemRegistry;

/**
 * In-panel inventory overlay — renders inside {@link GamePanel} without a JDialog.
 *
 * <p>Displays the player's 20-slot inventory alongside the equipment paper-doll.
 * Items can be selected and used/equipped by pressing Enter. Slides in from the right.
 */
public class InventoryOverlay {

    private static final Color SEL_BG = new Color(0, 45, 20);
    private static final Font  F_STAT = F_DESC;  // alias for readability

    private final Retroquest game;
    private Player  player;
    private boolean active       = false;
    private boolean awaitingDrop = false;
    private int     selected     = 0;
    private int     scrollTop    = 0;
    private long    entryTime    = 0;

    /** Paper-doll focus mode, entered with [X], used to take gear back off. */
    private boolean unequipMode  = false;
    private int     equipSel     = 0;

    private static final long ENTRY_MS = 280;

    /** Paper-doll order — paint, selection and unequip all read this one list. */
    private static final String[] EQUIP_TAGS = {"WPN", "ARM", "SHD", "HLM", "AMU", "RG1", "RG2"};
    /** Player field backing each paper-doll row, in {@link #EQUIP_TAGS} order. */
    private static final String[] EQUIP_FIELDS = {"weapon", "armor", "shield", "helm", "amulet", "ring1", "ring2"};
    /** Weapon and armour can never be null in the model, so they fall back to these. */
    private static final String DEFAULT_WEAPON_ID = "rusty_dagger";
    private static final String DEFAULT_ARMOR_ID  = "leather_armor";

    // Cached card/row geometry from last paint — used for mouse hit-testing
    private int lastListX, lastListY, lastListW, lastRowH, lastScrollTop;
    private int lastRowTop, lastRowCount;   // first painted row's y and how many are visible
    private java.awt.Rectangle lastCardRect = new java.awt.Rectangle();
    private java.awt.Rectangle[] lastKeyBarRects = new java.awt.Rectangle[0];
    private final java.awt.Rectangle[] lastEquipRects = new java.awt.Rectangle[7];

    public InventoryOverlay(Retroquest game) {
        this.game = game;
    }

    /** Decrements quantity of the item in the selected slot; removes the slot if depleted. */
    private void consumeSelectedItem() {
        InventorySlot slot = player.getInventorySlots()[selected];
        slot.setQuantity(slot.getQuantity() - 1);
        if (slot.getQuantity() <= 0) player.removeItem(selected);
    }

    public boolean isActive() { return active; }

    public void open() {
        this.player      = game.getPlayer();
        this.scrollTop   = 0;
        this.awaitingDrop = false;
        this.unequipMode = false;
        this.equipSel    = 0;
        this.selected    = 0;
        this.entryTime   = System.currentTimeMillis();
        this.active      = true;
    }

    public void close() {
        active       = false;
        awaitingDrop = false;
        unequipMode  = false;
    }

    public void handleKey(KeyEvent e) {
        if (!active) return;

        if (awaitingDrop) {
            if (e.getKeyCode() == KeyEvent.VK_Y) confirmDrop();
            else {
                awaitingDrop = false;
                game.log("You decide to keep the item.", MessageLog.Type.DIM);
            }
            return;
        }

        if (unequipMode) {
            switch (e.getKeyCode()) {
                case KeyEvent.VK_ESCAPE, KeyEvent.VK_X -> unequipMode = false;
                case KeyEvent.VK_I                     -> close();
                case KeyEvent.VK_UP,   KeyEvent.VK_K   -> { if (equipSel > 0) equipSel--; }
                case KeyEvent.VK_DOWN, KeyEvent.VK_J   -> { if (equipSel < EQUIP_TAGS.length - 1) equipSel++; }
                case KeyEvent.VK_U, KeyEvent.VK_ENTER  -> unequipAt(equipSel);
            }
            return;
        }

        int n = player.getInventorySlots().length;
        switch (e.getKeyCode()) {
            case KeyEvent.VK_ESCAPE, KeyEvent.VK_I -> close();
            case KeyEvent.VK_UP,   KeyEvent.VK_K   -> { if (selected > 0)     { selected--; clampScroll(n); } }
            case KeyEvent.VK_DOWN, KeyEvent.VK_J   -> { if (selected < n - 1) { selected++; clampScroll(n); } }
            case KeyEvent.VK_U, KeyEvent.VK_ENTER  -> useSelected();
            case KeyEvent.VK_D                     -> dropPrompt();
            case KeyEvent.VK_X                     -> { unequipMode = true; equipSel = 0; }
        }
    }

    // ── Unequip ───────────────────────────────────────────────────────────────

    /** The paper doll, in {@link #EQUIP_TAGS} order. */
    private Item[] equippedItems() {
        return new Item[] {
            player.getWeapon(), player.getArmor(), player.getShield(),
            player.getHelm(),   player.getAmulet(), player.getRing1(), player.getRing2()
        };
    }

    /** Takes off the piece in paper-doll row {@code i} and stows it in the pack. */
    private void unequipAt(int i) {
        Item worn = equippedItems()[i];
        if (worn == null) {
            game.log("That slot is already empty.", MessageLog.Type.DIM);
            return;
        }
        if (!hasFreeSlot()) {
            game.log("Your pack is full — no room to stow the " + worn.getName() + ".",
                     MessageLog.Type.DANGER);
            return;
        }

        boolean removed = switch (i) {
            // Player treats weapon and armour as never-null, so "unequip" for those
            // two means falling back to the starting gear rather than to nothing.
            case 0  -> swapToDefault(worn, DEFAULT_WEAPON_ID);
            case 1  -> swapToDefault(worn, DEFAULT_ARMOR_ID);
            default -> clearSlot(worn, i);
        };
        if (!removed) return;

        SoundManager.getInstance().play("coin");
        game.log("You take off the " + worn.getName() + " and stow it in your pack.",
                 MessageLog.Type.GOOD);
        game.getStatsPanel().refresh();
    }

    /** Re-equips the starting weapon/armour, which hands {@code worn} back to the pack. */
    private boolean swapToDefault(Item worn, String defaultId) {
        if (defaultId.equals(worn.getId())) {
            game.log("The " + worn.getName() + " is the plainest thing you own — keep it on.",
                     MessageLog.Type.DIM);
            return false;
        }
        Item bare = ItemRegistry.getById(defaultId);
        if (bare == null) {
            game.log("You have nothing to fall back on.", MessageLog.Type.DIM);
            return false;
        }
        player.equip(bare);   // returns the displaced piece to the pack
        return true;
    }

    /** Empties one of the nullable paper-doll slots (shield, helm, amulet, ring 1, ring 2). */
    private boolean clearSlot(Item worn, int slotIndex) {
        ItemSlot slot = switch (slotIndex) {
            case 2 -> ItemSlot.SHIELD;
            case 3 -> ItemSlot.HELM;
            case 4 -> ItemSlot.AMULET;
            default -> ItemSlot.RING;          // 5 = ring 1, 6 = ring 2
        };
        if (player.unequip(slot, slotIndex == 6) == null) {
            game.log("You cannot seem to get that off.", MessageLog.Type.DANGER);
            return false;
        }
        if (!player.addItem(worn)) {
            player.equip(worn);   // pack filled up underneath us — put it back on
            game.log("Your pack is full — the " + worn.getName() + " stays on.",
                     MessageLog.Type.DANGER);
            return false;
        }
        return true;
    }

    /**
     * Combat: an item was consumed, so the round is spent. No-op outside combat.
     * Closes the pack first, because the monster is about to act.
     */
    private void reportCombatItemUse() {
        if (game.getGamePanel() == null || !game.getGamePanel().isCombatActive()) return;
        io.cannonforge.retroquest.overlay.CombatOverlay co = game.getGamePanel().getCombatOverlay();
        if (co == null || !co.isItemMode()) return;
        close();
        co.onCombatItemUsed();
    }

    private void useSelected() {
        InventorySlot[] slots = player.getInventorySlots();
        if (selected >= slots.length) return;
        InventorySlot slot = slots[selected];
        if (slot.isEmpty()) {
            game.log("That slot is empty.", MessageLog.Type.DIM);
            return;
        }
        Item item = slot.getItem();

        if (item.getType() == Item.Type.SCROLL) {
            String spellName = item.getSpellName();
            if (spellName == null) {
                game.log("This scroll crumbles — no spell bound to it.", MessageLog.Type.DIM);
                return;
            }

            // Resurrection scroll — arms ward instead of teaching the spell
            if ("Resurrection".equals(spellName)) {
                if (player.isResurrectionCharged()) {
                    game.log("A resurrection ward is already active. The scroll remains intact.", MessageLog.Type.DIM);
                    return;
                }
                consumeSelectedItem();
                player.chargeResurrection();
                SoundManager.getInstance().play("heal");
                game.log("You invoke the scroll — a golden ward shimmers around you!", MessageLog.Type.GOOD);
                game.getStatsPanel().refresh();
                reportCombatItemUse();
                return;
            }

            // Consume scroll before checking
            consumeSelectedItem();

            if (player.isSpellLearned(spellName)) {
                game.log("You already know " + spellName + ". The scroll crumbles to dust.", MessageLog.Type.DIM);
            } else if (player.learnSpell(spellName)) {
                game.log("You read the " + item.getName() + " and learn " + spellName + "!", MessageLog.Type.GOOD);
                SoundManager.getInstance().play("altarChime");
            } else {
                game.log("The magic in this scroll is beyond your comprehension.", MessageLog.Type.DIM);
            }
            reportCombatItemUse();
            return;
        }

        if (item.getType() == Item.Type.WAND) {
            String spellName = item.getSpellName();
            if (spellName == null) {
                game.log("This " + item.getName() + " crumbles to dust — no spell bound to it.", MessageLog.Type.DIM);
                return;
            }
            Spell target = null;
            for (Spell s : player.getKnownSpells()) {
                if (s != null && s.getName().equals(spellName)) { target = s; break; }
            }
            if (target == null) {
                game.log("You cannot comprehend the magic in this " + item.getName() + ".", MessageLog.Type.DIM);
                return;
            }
            // Decrement wand charge
            boolean wandDepleted = slot.getQuantity() <= 1;
            consumeSelectedItem();
            SoundManager.getInstance().play("spellcast");

            if (!wandDepleted) {
                int rem = slot.getQuantity();
                game.log("You invoke the " + item.getName() + "! (" + rem
                        + " charge" + (rem == 1 ? "" : "s") + " remaining)", MessageLog.Type.GOOD);
            } else {
                game.log("You invoke the " + item.getName() + "! The wand crumbles to dust.", MessageLog.Type.GOOD);
            }

            if (game.getGamePanel().isCombatActive()) {
                close();   // the monster is about to act; do not leave the pack open over it
                game.getGamePanel().executeCombatSpell(target);
            } else {
                game.castMapSpell(target);
            }
            return;
        }

        if (item.getType() == Item.Type.FOOD) {
            int before = player.getFood();
            player.addFood(Math.max(1, item.getValue()));
            int gained = player.getFood() - before;
            consumeSelectedItem();
            SoundManager.getInstance().play("coin");
            game.log("You eat the " + item.getName() + "."
                    + (gained > 0 ? " (+" + gained + " food)" : " You are already full."),
                    MessageLog.Type.GOOD);
            game.getStatsPanel().refresh();
            reportCombatItemUse();
            return;
        }

        if (item.getType() == Item.Type.POTION && "antidote".equals(item.getId())) {
            if (!player.isPoisoned()) {
                game.log("You are not poisoned.", MessageLog.Type.DIM);
                return;
            }
            consumeSelectedItem();
            player.curePoison();
            SoundManager.getInstance().play("heal");
            game.log("You drink the Antidote. The poison is purged from your body!", MessageLog.Type.GOOD);
            game.getStatsPanel().refresh();
            reportCombatItemUse();
        }
        else if (item.getType() == Item.Type.POTION) {
            item.rebuildEffects();
            int healAmt = 0;
            for (Effect e : item.getEffects()) {
                if (e.getType() == Effect.EffectType.HEALING) {
                    healAmt += e.getValue();
                    e.onApply(player);
                }
            }
            if (healAmt == 0) healAmt = item.getValue(); // fallback for unexpected state
            consumeSelectedItem();
            SoundManager.getInstance().play("heal");

            // Enhanced flavorful message (retro "quaff" style + direct HP feedback)
            game.log("You quaff the " + item.getName() + ". You feel revitalized! (+" + healAmt + " HP)",
                     MessageLog.Type.GOOD);
            game.getStatsPanel().refresh();
            reportCombatItemUse();
        }
        else if (item.getSlotType() == ItemSlot.WEAPON ||
                 item.getSlotType() == ItemSlot.ARMOR  ||
                 item.getSlotType() == ItemSlot.HELM   ||
                 item.getSlotType() == ItemSlot.AMULET ||
                 item.getSlotType() == ItemSlot.SHIELD ||
                 item.getSlotType() == ItemSlot.RING) {

            // Free the pack slot *before* equipping: Player.equip() silently drops
            // the displaced piece when addItem() finds no room, so the swap needs a
            // free slot to land in. Removing this item first always provides one —
            // except when it is a stack, where the slot survives the removal.
            Item displaced = wouldDisplace(item);
            boolean stacked = slot.getQuantity() > 1;
            if (displaced != null && stacked && !hasFreeSlot()) {
                game.log("Your pack is full — no room for your " + displaced.getName() + ".",
                         MessageLog.Type.DANGER);
                return;
            }
            if (stacked) slot.setQuantity(slot.getQuantity() - 1);
            else         player.removeItem(selected);

            player.equip(item);
            SoundManager.getInstance().play("coin");

            String action = switch (item.getSlotType()) {
                case WEAPON -> "wield";
                case ARMOR  -> "don";
                case HELM   -> "put on";
                case SHIELD -> "raise";
                case AMULET -> "clasp on";
                default     -> "slip on";
            };
            game.log("You " + action + " the " + item.getName() + ".", MessageLog.Type.GOOD);
            game.getStatsPanel().refresh();
        }
        else {
            game.log("You have no use for that item right now.", MessageLog.Type.DIM);
        }
    }

    /** The piece currently worn in the slot {@code item} would occupy, or null if it is free. */
    private Item wouldDisplace(Item item) {
        return switch (item.getSlotType()) {
            case WEAPON -> player.getWeapon();
            case ARMOR  -> player.getArmor();
            case HELM   -> player.getHelm();
            case AMULET -> player.getAmulet();
            case SHIELD -> player.getShield();
            // Player.equip() fills an empty ring finger first and only swaps ring2
            case RING   -> (player.getRing1() == null || player.getRing2() == null) ? null : player.getRing2();
            default     -> null;
        };
    }

    private boolean hasFreeSlot() {
        for (InventorySlot s : player.getInventorySlots()) if (s.isEmpty()) return true;
        return false;
    }

    private void dropPrompt() {
        InventorySlot[] slots = player.getInventorySlots();
        if (selected >= slots.length || slots[selected].isEmpty()) {
            game.log("That slot is empty.", MessageLog.Type.DIM);
            return;
        }
        awaitingDrop = true;
        String name = slots[selected].getItem().getName();
        game.log("Drop the " + name + "? [Y] Yes  [Any other key] Cancel",
                 MessageLog.Type.DANGER);
    }

    private void confirmDrop() {
        awaitingDrop = false;
        InventorySlot[] slots = player.getInventorySlots();
        if (selected >= slots.length || slots[selected].isEmpty()) return;

        String name = slots[selected].getItem().getName();
        player.removeItem(selected);
        game.log("You drop the " + name + " to the ground.", MessageLog.Type.DIM);

        selected = Math.min(selected, player.getInventorySlots().length - 1);
    }

    private void clampScroll(int n) {
        if (selected < scrollTop) scrollTop = selected;
        // maxRows unknown here; paint() will fix it
    }


    // ── Mouse support ─────────────────────────────────────────────────────────

    public void handleClick(int mx, int my) {
        if (!active) return;
        if (!lastCardRect.contains(mx, my)) return;   // clicks outside the panel do nothing

        // Key bar buttons. Pack mode: [0]Navigate [1]Use [2]Unequip [3]Drop [4]Close.
        // Unequip mode: [0]Slot [1]Take off [2]Back [3]Cancel.
        for (int i = 0; i < lastKeyBarRects.length; i++) {
            if (lastKeyBarRects[i].contains(mx, my)) {
                if (unequipMode) {
                    switch (i) {
                        case 1 -> unequipAt(equipSel);
                        case 2 -> unequipMode = false;
                        case 3 -> close();
                    }
                } else {
                    switch (i) {
                        case 1 -> useSelected();
                        case 2 -> { unequipMode = true; equipSel = 0; }
                        case 3 -> dropPrompt();
                        case 4 -> close();
                    }
                }
                return;
            }
        }

        // Paper-doll rows: first click focuses the slot, a second one takes the piece off
        for (int i = 0; i < lastEquipRects.length; i++) {
            if (lastEquipRects[i] != null && lastEquipRects[i].contains(mx, my)) {
                if (unequipMode && equipSel == i) unequipAt(i);
                else { unequipMode = true; equipSel = i; }
                return;
            }
        }

        // Item list rows
        if (lastRowH > 0 && lastRowCount > 0
                && mx >= lastListX && mx < lastListX + lastListW
                && my >= lastRowTop && my < lastRowTop + lastRowCount * lastRowH) {
            int relY       = my - lastRowTop;
            int clickedRow = lastScrollTop + relY / lastRowH;
            int n          = player.getInventorySlots().length;
            if (clickedRow >= 0 && clickedRow < n) {
                if (unequipMode) { unequipMode = false; selected = clickedRow; }
                else if (clickedRow == selected) useSelected();
                else selected = clickedRow;
            }
        }
    }

    // ── Paint ─────────────────────────────────────────────────────────────────

    public void paint(Graphics2D g, int W, int H) {
        if (!active) return;
        try {
        float ease = entryEase(entryTime, ENTRY_MS);

        // Dim background
        g.setColor(new Color(0, 0, 0, (int)(180 * ease)));
        g.fillRect(0, 0, W, H);

        int cW = Math.min(OverlayTheme.scaled(700), W - 40);
        int cH = Math.min(OverlayTheme.scaled(500), H - 40);
        int cX = (int)((W - cW) / 2 * ease + (1f - ease) * W);  // slide from right
        int cY = (H - cH) / 2;

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Shadow
        g.setColor(new Color(0, 0, 0, (int)(100 * ease)));
        g.fillRoundRect(cX + 4, cY + 4, cW, cH, 6, 6);

        // Card background
        g.setPaint(new GradientPaint(cX, cY, new Color(10, 16, 12), cX, cY + cH, BG));
        g.fillRoundRect(cX, cY, cW, cH, 6, 6);

        // Border
        g.setStroke(new BasicStroke(1.5f));
        g.setColor(new Color(0, 255, 120, 160));
        g.drawRoundRect(cX, cY, cW, cH, 6, 6);
        g.setStroke(new BasicStroke(1f));

        int titleH  = OverlayTheme.scaled(36);
        int keybarH = OverlayTheme.scaled(34);
        int detailH = OverlayTheme.scaled(76);   // name+rarity, stats, comparison
        int bodyH   = cH - titleH - keybarH - detailH;
        int equipW  = cW / 2;

        // Cache for mouse hit-testing
        lastCardRect = new java.awt.Rectangle(cX, cY, cW, cH);
        lastListX = cX + equipW; lastListY = cY + titleH;
        lastListW = cW - equipW;

        paintTitle  (g, cX, cY,                           cW, titleH);
        paintEquip  (g, cX, cY + titleH,                  equipW, bodyH);
        paintList   (g, cX + equipW, cY + titleH,          cW - equipW, bodyH);
        paintDetail (g, cX, cY + titleH + bodyH,           cW, detailH);
        paintKeyBar (g, cX, cY + titleH + bodyH + detailH, cW, keybarH);
        } catch (Exception ex) {
            ex.printStackTrace();
            System.err.println("[INV] paint crashed: " + ex + " — closing overlay");
            active = false;
        }
    }

    private void paintTitle(Graphics2D g, int x, int y, int w, int h) {
        g.setPaint(new GradientPaint(x, y, new Color(6, 20, 10), x + w, y, new Color(8, 12, 16)));
        g.fillRoundRect(x, y, w, h, 6, 6);
        g.fillRect(x, y + h / 2, w, h / 2);
        g.setColor(BORDER_COL);
        g.drawLine(x, y + h, x + w, y + h);

        g.setFont(F_TITLE);
        FontMetrics fm = g.getFontMetrics();
        int ty = y + (h + fm.getAscent() - fm.getDescent()) / 2;

        g.setColor(new Color(0, 255, 120, 35));
        g.drawString("\u25c8  INVENTORY", x + 15, ty + 1);
        g.setColor(PHOSPHOR);
        g.drawString("\u25c8  INVENTORY", x + 14, ty);

        g.setFont(F_SMALL);
        FontMetrics fmS = g.getFontMetrics();
        String gold = "Gold: " + player.getGold();
        g.setColor(AMBER);
        int goldX = x + w - fmS.stringWidth(gold) - 14;
        g.drawString(gold, goldX, ty);

        // The pack is a fixed 20 slots, so say how many are actually in use rather
        // than making the player count the "— empty —" rows.
        int used = 0;
        for (InventorySlot s : player.getInventorySlots()) if (!s.isEmpty()) used++;
        String pack = "Pack " + used + "/" + player.getInventorySlots().length;
        g.setColor(TEXT_DIM);
        g.drawString(pack, goldX - fmS.stringWidth(pack) - 16, ty);
    }

    private void paintEquip(Graphics2D g, int x, int y, int w, int h) {
        g.setColor(new Color(8, 14, 10));
        g.fillRect(x, y, w, h);
        g.setColor(BORDER_COL);
        g.drawLine(x + w, y, x + w, y + h);

        g.setFont(F_KEY);
        FontMetrics fm = g.getFontMetrics();
        g.setColor(TEXT_DIM);
        int hdrY = y + OverlayTheme.scaled(10) + fm.getAscent();
        g.drawString("EQUIPPED", x + 10, hdrY);
        int divY = hdrY + fm.getDescent() + OverlayTheme.scaled(4);
        g.setColor(new Color(BORDER_COL.getRed(), BORDER_COL.getGreen(), BORDER_COL.getBlue(), 80));
        g.drawLine(x + 8, divY, x + w - 8, divY);

        String[] tags   = EQUIP_TAGS;
        Color[]  colors = {AMBER, CYAN_ACC, CYAN_ACC, CYAN_ACC, PHOSPHOR, PHOSPHOR, PHOSPHOR};
        Item[]   items  = equippedItems();

        // Reserve space for key ring at the bottom
        List<String> keys = player.getKeyRing();
        int keyRowH  = g.getFontMetrics(F_SMALL).getHeight() + OverlayTheme.scaled(4);
        int keyRingH = keys.isEmpty() ? 0
                     : Math.min(h / 4, OverlayTheme.scaled(20) + keys.size() * keyRowH);
        int equipH   = h - keyRingH;

        int headerH = divY - y + OverlayTheme.scaled(8);
        int slotH = (equipH - headerH) / 7;
        int sy    = y + headerH;

        for (int i = 0; i < 7; i++) {
            lastEquipRects[i] = new java.awt.Rectangle(x, sy, w, slotH);

            // Unequip-mode focus: highlight the row the player is about to strip
            if (unequipMode && equipSel == i) {
                g.setColor(SEL_BG);
                g.fillRect(x, sy, w, slotH);
                g.setColor(PHOSPHOR);
                g.fillRect(x, sy, 3, slotH);
            }

            // Tag badge
            g.setFont(F_KEY);
            FontMetrics fmK = g.getFontMetrics();
            int tw = fmK.stringWidth(tags[i]) + 10;

            g.setColor(new Color(colors[i].getRed(), colors[i].getGreen(), colors[i].getBlue(), 40));
            g.fillRoundRect(x + 8, sy + 2, tw, slotH - 4, 3, 3);
            g.setColor(new Color(colors[i].getRed(), colors[i].getGreen(), colors[i].getBlue(), 130));
            g.drawRoundRect(x + 8, sy + 2, tw, slotH - 4, 3, 3);
            g.setColor(colors[i]);
            int tby = sy + (slotH + fmK.getAscent() - fmK.getDescent()) / 2;
            g.drawString(tags[i], x + 12, tby);

            // Item name
            g.setFont(F_STAT);
            FontMetrics fmS = g.getFontMetrics();
            String label = (items[i] != null)
                ? items[i].getName() + (items[i].getValue() > 0 ? " +" + items[i].getValue() : "")
                : "\u2014 empty \u2014";
            int maxW = w - tw - 22;
            if (maxW > 0) while (fmS.stringWidth(label) > maxW && label.length() > 1)
                label = label.substring(0, label.length() - 1);
            if (maxW > 0 && fmS.stringWidth(label + "\u2026") <= maxW + fmS.stringWidth("\u2026")) label += "\u2026";
            g.setColor(items[i] != null ? rarityColor(items[i]) : TEXT_DIM);
            g.drawString(label, x + tw + 14, tby);

            sy += slotH;
        }

        // ── Key Ring section ──
        if (!keys.isEmpty()) {
            int ky = y + equipH;
            g.setColor(new Color(BORDER_COL.getRed(), BORDER_COL.getGreen(), BORDER_COL.getBlue(), 80));
            g.drawLine(x + 8, ky, x + w - 8, ky);

            g.setFont(F_KEY);
            FontMetrics fmK = g.getFontMetrics();
            g.setColor(AMBER);
            g.drawString("\u26BF KEYS", x + 10, ky + 4 + fmK.getAscent());

            g.setFont(F_SMALL);
            FontMetrics fmS = g.getFontMetrics();
            int ry = ky + OverlayTheme.scaled(4) + fmK.getHeight();
            int badgeH = fmS.getHeight() + OverlayTheme.scaled(2);
            Color keyBadgeBg = new Color(AMBER.getRed(), AMBER.getGreen(), AMBER.getBlue(), 30);
            Color keyBadgeBorder = new Color(AMBER.getRed(), AMBER.getGreen(), AMBER.getBlue(), 100);

            for (String keyId : keys) {
                if (ry + badgeH > y + h) break;  // clip if out of space
                Item keyItem = ItemRegistry.getById(keyId);
                String keyName = (keyItem != null) ? keyItem.getName() : keyId;

                int badgeW = fmS.stringWidth(keyName) + 16;
                g.setColor(keyBadgeBg);
                g.fillRoundRect(x + 10, ry, badgeW, badgeH, 3, 3);
                g.setColor(keyBadgeBorder);
                g.drawRoundRect(x + 10, ry, badgeW, badgeH, 3, 3);
                g.setColor(AMBER);
                g.drawString(keyName, x + 18, ry + fmS.getAscent() + 1);

                ry += keyRowH;
            }
        }

    }

    private void paintList(Graphics2D g, int x, int y, int w, int h) {
        g.setColor(BG);
        g.fillRect(x, y, w, h);

        g.setFont(F_ITEM);
        FontMetrics fm  = g.getFontMetrics();
        int rowH        = fm.getHeight() + OverlayTheme.scaled(5);
        int maxRows     = Math.max(1, (h - 6) / rowH);
        InventorySlot[] slots = player.getInventorySlots();
        int n           = slots.length;

        // Clamp scroll
        if (selected >= scrollTop + maxRows) scrollTop = selected - maxRows + 1;
        if (selected <  scrollTop)           scrollTop = selected;
        scrollTop = Math.max(0, Math.min(scrollTop, Math.max(0, n - maxRows)));

        // Cache for mouse hit-testing
        lastRowH = rowH; lastScrollTop = scrollTop;
        lastRowTop   = y + 4;
        lastRowCount = Math.max(0, Math.min(maxRows, n - scrollTop));

        int dy = y + 4;
        for (int i = scrollTop; i < n && i < scrollTop + maxRows; i++) {
            InventorySlot slot = slots[i];
            boolean sel   = (i == selected);
            boolean empty = slot.isEmpty();

            g.setColor(sel ? SEL_BG : (i % 2 == 0 ? BG : ROW_ALT));
            g.fillRect(x, dy, w, rowH);

            // Detect Magic highlight — pulsing purple tint on equippable magic items
            if (!empty && player.isDetectMagicActive() && hasBonus(slot.getItem())) {
                int alpha = 28 + (int)(22 * Math.sin(System.currentTimeMillis() / 220.0));
                g.setColor(new Color(150, 60, 240, alpha));
                g.fillRect(x, dy, w, rowH);
            }

            if (sel) { g.setColor(PHOSPHOR); g.fillRect(x, dy, 3, rowH); }

            // Slot number
            g.setFont(F_KEY);
            FontMetrics fmK = g.getFontMetrics();
            int numW = fmK.stringWidth("00") + 14;
            int textY = dy + rowH - (rowH - fmK.getAscent()) / 2 - 1;
            g.setColor(sel ? PHOSPHOR : TEXT_DIM);
            g.drawString(String.format("%2d", i + 1), x + 8, textY);

            if (empty) {
                g.setFont(F_ITEM);
                g.setColor(new Color(50, 65, 80));
                g.drawString("— empty —", x + numW, textY);
            } else {
                Item item = slot.getItem();
                g.setFont(F_ITEM);
                FontMetrics fmI = g.getFontMetrics();
                // Icon keeps the type colour; the name carries the rarity colour, so
                // a Legendary find reads at a glance without another column.
                Color iconCol = sel ? PHOSPHOR : itemColor(item);
                Color col     = sel ? PHOSPHOR : rarityColor(item);
                String icon = itemIcon(item);
                g.setColor(new Color(iconCol.getRed(), iconCol.getGreen(), iconCol.getBlue(), 180));
                g.drawString(icon, x + numW, textY);
                int iconW = fmI.stringWidth(icon) + 6;

                String label = item.getName()
                    + (hasBonus(item) ? " +" + item.getValue() : "")
                    + (item.getType() == Item.Type.WAND
                        ? " (" + slot.getQuantity() + ")"
                        : (slot.getQuantity() > 1 ? " x" + slot.getQuantity() : ""));
                int maxW = w - numW - iconW - 12;
                if (maxW > 0) { while (fmI.stringWidth(label) > maxW && label.length() > 1)
                    label = label.substring(0, label.length() - 1);
                if (fmI.stringWidth(label + "\u2026") <= maxW + fmI.stringWidth("\u2026")) label += "\u2026"; }
                g.setColor(col);
                g.drawString(label, x + numW + iconW, textY);
            }
            dy += rowH;
        }

        paintScrollIndicators(g, scrollTop, maxRows, n, x, y, w, h);
    }

    private void paintDetail(Graphics2D g, int x, int y, int w, int h) {
        g.setColor(new Color(7, 10, 16));
        g.fillRect(x, y, w, h);
        g.setColor(BORDER_COL);
        g.drawLine(x, y, x + w, y);
        g.drawLine(x, y + h, x + w, y + h);

        // In unequip mode the detail strip describes the paper-doll row under focus.
        InventorySlot slot = null;
        Item item;
        if (unequipMode) {
            item = equippedItems()[equipSel];
            if (item == null) {
                g.setFont(F_STAT);
                g.setColor(TEXT_DIM);
                g.drawString("Nothing worn in that slot.", x + 14, y + h / 2 + 5);
                return;
            }
        } else {
            InventorySlot[] slots = player.getInventorySlots();
            if (selected >= slots.length || slots[selected].isEmpty()) {
                g.setFont(F_STAT);
                g.setColor(TEXT_DIM);
                g.drawString("Empty slot.", x + 14, y + h / 2 + 5);
                return;
            }
            slot = slots[selected];
            item = slot.getItem();
        }

        int  padX  = 14;
        int  midY  = y + 12;

        g.setFont(F_LABEL);
        FontMetrics fm = g.getFontMetrics();
        g.setColor(AMBER);
        g.drawString(item.getName(), x + padX, midY + fm.getAscent());
        int nameW = fm.stringWidth(item.getName());

        // Rarity tag, right after the name
        g.setFont(F_SMALL);
        String rarityName = item.getRarity().getDisplayName().toUpperCase();
        g.setColor(rarityColor(item));
        g.drawString(rarityName, x + padX + nameW + 10, midY + fm.getAscent());

        g.setFont(F_STAT);
        FontMetrics fmS = g.getFontMetrics();
        g.setColor(TEXT_BRIGHT);
        int line2Y = midY + fm.getHeight() + fmS.getAscent() + 2;
        g.drawString(slot != null ? buildDetail(item, slot) : buildDetail(item, new InventorySlot(item, 1)),
                     x + padX, line2Y);

        // Third line: how this piece stacks up against what is already worn.
        String cmp = comparison(item);
        if (cmp != null && !unequipMode) {
            g.setFont(F_STAT);
            g.setColor(comparisonColor(item));
            g.drawString(cmp, x + padX, line2Y + fmS.getHeight() + 2);
        }

        if (awaitingDrop) {
            String msg = "[Y] Confirm drop  [Any] Cancel";
            g.setColor(DANGER);
            g.setFont(F_KEY);
            FontMetrics fmD = g.getFontMetrics();
            g.drawString(msg, x + w - fmD.stringWidth(msg) - 14, midY + fm.getAscent());
        }
    }

    private void paintKeyBar(Graphics2D g, int x, int y, int w, int h) {
        g.setPaint(new GradientPaint(x, y, new Color(8, 14, 10), x, y + h, BG));
        g.fillRect(x, y, w, h);
        g.fillRoundRect(x, y + h / 2, w, h / 2 + 4, 6, 6);
        g.setColor(BORDER_COL);
        g.drawLine(x, y, x + w, y);

        String[][] keys = unequipMode
            ? new String[][] {{"↑↓","Slot"},{"Enter","Take off"},{"X","Back"},{"Esc","Cancel"}}
            : new String[][] {{"↑↓","Navigate"},{"U","Use/Equip"},{"X","Unequip"},{"D","Drop"},{"Esc","Close"}};
        lastKeyBarRects = OverlayTheme.paintKeyBadges(g, x, y, w, h, keys, PHOSPHOR);
    }

    // ── Rarity + comparison helpers ───────────────────────────────────────────

    private static Color rarityColor(Item item) {
        return item.getRarity() != null ? item.getRarity().getColor() : TEXT_BRIGHT;
    }

    /**
     * "vs Iron Sword +2 : +4 dmg (+2)" for the piece already worn in this item's
     * slot, or null when the item is not gear. Rings compare against the finger
     * {@link Player#equip} would actually displace.
     */
    private String comparison(Item item) {
        if (!hasBonus(item)) return null;
        Item worn = wouldDisplace(item);
        String stat = switch (item.getSlotType()) {
            case WEAPON -> "dmg";
            case ARMOR, HELM, SHIELD -> "AC";
            default -> "bonus";
        };
        if (worn == null) return "vs empty slot: " + signed(item.getValue()) + " " + stat + " gained";
        int delta = item.getValue() - worn.getValue();
        return "vs " + worn.getName() + " " + signed(worn.getValue()) + ": "
             + signed(item.getValue()) + " " + stat + "  (" + signed(delta) + ")";
    }

    private Color comparisonColor(Item item) {
        Item worn = wouldDisplace(item);
        if (worn == null) return GOOD;
        int delta = item.getValue() - worn.getValue();
        return delta > 0 ? GOOD : delta < 0 ? DANGER : TEXT_DIM;
    }

    private static String signed(int v) { return (v >= 0 ? "+" : "") + v; }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Color itemColor(Item item) {
        return switch (item.getType()) {
            case WEAPON          -> AMBER;
            case ARMOR, HELM,
                 SHIELD          -> CYAN_ACC;
            case POTION          -> GOOD;
            case RING_PROTECTION,
                 RING_REGEN,
                 AMULET          -> PHOSPHOR;
            default              -> TEXT_BRIGHT;
        };
    }

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

    private boolean hasBonus(Item item) {
        return item.getSlotType() == ItemSlot.WEAPON ||
               item.getSlotType() == ItemSlot.ARMOR  ||
               item.getSlotType() == ItemSlot.HELM   ||
               item.getSlotType() == ItemSlot.AMULET ||
               item.getSlotType() == ItemSlot.SHIELD ||
               item.getSlotType() == ItemSlot.RING;
    }

    private String buildDetail(Item item, InventorySlot slot) {
        return switch (item.getType()) {
            case WEAPON          -> "Damage bonus: +" + item.getValue() + "  |  Weapon";
            case ARMOR           -> "AC bonus: +"     + item.getValue() + "  |  Armor";
            case HELM            -> "AC bonus: +"     + item.getValue() + "  |  Helm";
            case SHIELD          -> "AC bonus: +"     + item.getValue() + "  |  Shield";
            case AMULET          -> "Passive  |  Amulet" + (item.getValue() > 0 ? " +" + item.getValue() : "");
            case POTION          -> "Heals: "         + item.getValue() + " HP  |  Qty: " + slot.getQuantity();
            case RING_PROTECTION -> "Protection: +"   + item.getValue() + "  |  Ring";
            case RING_REGEN      -> "Regen: +"        + item.getValue() + "/turn  |  Ring";
            case SCROLL          -> "Casts: " + (item.getSpellName() != null ? item.getSpellName() : "?") + "  |  Scroll";
            case WAND            -> "Casts: " + (item.getSpellName() != null ? item.getSpellName() : "?")
                                    + "  |  Charges: " + slot.getQuantity();
            default              -> (item.getType() != null ? item.getType().name() : "Unknown");
        };
    }
}

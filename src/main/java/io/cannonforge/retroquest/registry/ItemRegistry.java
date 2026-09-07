package io.cannonforge.retroquest.registry;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import io.cannonforge.retroquest.model.Effect;
import io.cannonforge.retroquest.model.Item;
import io.cannonforge.retroquest.model.ItemSlot;
import io.cannonforge.retroquest.model.Rarity;
import io.cannonforge.retroquest.overlay.ShopOverlay;

/**
 * Singleton registry for all {@link Item} definitions, backed by {@code data/items.json}.
 *
 * <p>The registry is populated on class load: if the JSON file exists it is read,
 * otherwise a set of default items is created and saved. Use the provided mutating
 * methods ({@link #addItem}, {@link #removeItem}, {@link #updateItem}) to ensure
 * changes are persisted; do not mutate the list returned by {@link #getAllItems()}.
 */
public class ItemRegistry {

    private static final String ITEMS_FILE = System.getProperty("user.dir") + "/data/items.json";
    private static final List<Item> items = new ArrayList<>();

    /**
     * Set when items.json exists but could not be read. Defaults are still populated so the
     * game runs, but saving is refused — otherwise the seeded defaults would overwrite the
     * real (merely unreadable) content file.
     */
    private static boolean loadFailed = false;

    static {
        loadItems();
        if (items.isEmpty()) {
            createDefaultItems();
            if (!loadFailed) saveItems();
        }
    }

    private static void loadItems() {
        File file = new File(ITEMS_FILE);
        if (!file.exists()) return; // genuinely absent — safe to seed and write

        try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            Type listType = new TypeToken<ArrayList<Item>>(){}.getType();
            List<Item> loaded = new Gson().fromJson(reader, listType);
            if (loaded == null) {
                loadFailed = true;
                System.err.println("[ItemRegistry] *** items.json is present but contains no item list."
                        + " Running on built-in defaults; saving is DISABLED so the file is not overwritten.");
                return;
            }
            items.addAll(loaded);
            // Rebuild effects after loading from JSON
            for (Item item : items) {
                item.rebuildEffects();
            }
        } catch (Exception e) {
            loadFailed = true;
            items.clear(); // discard a partially-read list
            System.err.println("[ItemRegistry] *** FAILED TO PARSE items.json: " + e
                    + " — running on built-in defaults; saving is DISABLED so your item file is not overwritten.");
            e.printStackTrace();
        }
    }

    /**
     * Persists the registry to {@code data/items.json}.
     *
     * <p>Written to a sibling {@code .tmp} file and then moved into place, so a failure
     * mid-write cannot truncate the existing file. Refused outright if the file failed to
     * parse at startup.
     *
     * @return {@code true} if the file was written, {@code false} if the save was refused or failed
     */
    public static boolean saveItems() {
        if (loadFailed) {
            System.err.println("[ItemRegistry] REFUSING to save items.json — it failed to load at"
                    + " startup and saving now would replace it with defaults. Repair or remove the file.");
            return false;
        }

        File target = new File(ITEMS_FILE);
        if (target.getParentFile() != null) target.getParentFile().mkdirs();
        File tmp = new File(ITEMS_FILE + ".tmp");

        try {
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8)) {
                new GsonBuilder().setPrettyPrinting().create().toJson(items, writer);
            }
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (Exception e) {
            System.err.println("[ItemRegistry] FAILED to save items.json: " + e);
            e.printStackTrace();
            tmp.delete();
            return false;
        }
    }

    private static void createDefaultItems() {
        // ── Tier 1 — Depths 1-5 ──────────────────────────────────────────────
        items.add(new Item("healing_potion", "Healing Potion", Item.Type.POTION, ItemSlot.CONSUMABLE,
                25, 28, "Restores 25 HP.", "potion_red.png", 99, null)
                .withTier(1).withRarity(Rarity.COMMON).withShopAvailable(true));

        items.add(new Item("rusty_dagger", "Rusty Dagger", Item.Type.WEAPON, ItemSlot.WEAPON,
                3, 15, "A worn but serviceable dagger.", "dagger_rusty.png", 1, null)
                .withTier(1).withRarity(Rarity.COMMON));

        items.add(new Item("iron_sword", "Iron Sword", Item.Type.WEAPON, ItemSlot.WEAPON,
                8, 95, "A reliable iron sword.", "sword_iron.png", 1, null)
                .withTier(1).withRarity(Rarity.COMMON).withShopAvailable(true));

        items.add(new Item("leather_armor", "Leather Armor", Item.Type.ARMOR, ItemSlot.ARMOR,
                2, 45, "Light leather protection.", "armor_leather.png", 1, null)
                .withTier(1).withRarity(Rarity.COMMON).withShopAvailable(true));

        items.add(new Item("chain_mail", "Chain Mail", Item.Type.ARMOR, ItemSlot.ARMOR,
                4, 75, "Interlocked steel rings.", "armor_chain.png", 1, null)
                .withTier(1).withRarity(Rarity.UNCOMMON).withShopAvailable(true));

        // ── Tier 2 — Depths 6-10 ─────────────────────────────────────────────
        items.add(new Item("steel_sword", "Steel Sword", Item.Type.WEAPON, ItemSlot.WEAPON,
                14, 175, "Forged from tempered steel.", "sword_steel.png", 1, null)
                .withTier(2).withRarity(Rarity.COMMON).withShopAvailable(true));

        items.add(new Item("plate_mail", "Plate Mail", Item.Type.ARMOR, ItemSlot.ARMOR,
                7, 220, "Heavy steel plate armor.", "armor_plate.png", 1, null)
                .withTier(2).withRarity(Rarity.UNCOMMON).withShopAvailable(true));

        items.add(new Item("ring_protection", "Ring of Protection", Item.Type.RING_PROTECTION, ItemSlot.RING,
                3, 135, "+3 AC while worn.", "ring_gold.png", 1, new Effect(Effect.EffectType.PROTECTION, 3))
                .withTier(2).withRarity(Rarity.UNCOMMON).withShopAvailable(true));

        // ── Tier 3 — Depths 11-15 ────────────────────────────────────────────
        items.add(new Item("ring_regen", "Ring of Regeneration", Item.Type.RING_REGEN, ItemSlot.RING,
                1, 260, "Slowly regenerates HP over time.", "ring_silver.png", 1,
                new Effect(Effect.EffectType.REGENERATION, 1))
                .withTier(3).withRarity(Rarity.RARE).withShopAvailable(true));

        items.add(new Item("flame_sword", "Flame Sword", Item.Type.WEAPON, ItemSlot.WEAPON,
                20, 350, "A blade wreathed in magical fire.", "sword_flame.png", 1, null)
                .withTier(3).withRarity(Rarity.RARE));

        items.add(new Item("greater_healing", "Greater Healing Potion", Item.Type.POTION, ItemSlot.CONSUMABLE,
                60, 85, "Restores 60 HP.", "potion_blue.png", 20, null)
                .withTier(3).withRarity(Rarity.UNCOMMON).withShopAvailable(true));

        // ── Tier 5 — Depths 21-25 ────────────────────────────────────────────
        items.add(new Item("holy_avenger", "Holy Avenger", Item.Type.WEAPON, ItemSlot.WEAPON,
                32, 800, "A blessed sword that burns the undead.", "sword_holy.png", 1, null)
                .withTier(5).withRarity(Rarity.EPIC));

        items.add(new Item("ring_protection_5", "Greater Ring of Protection", Item.Type.RING_PROTECTION, ItemSlot.RING,
                5, 520, "+5 AC while worn.", "ring_gold.png", 1, new Effect(Effect.EffectType.PROTECTION, 5))
                .withTier(5).withRarity(Rarity.RARE));

        // ── Helms ────────────────────────────────────────────────────────────
        items.add(new Item("leather_cap", "Leather Cap", Item.Type.HELM, ItemSlot.HELM,
                1, 20, "A simple leather cap.", "unknown.png", 1, null)
                .withTier(1).withRarity(Rarity.COMMON));

        items.add(new Item("iron_helm", "Iron Helm", Item.Type.HELM, ItemSlot.HELM,
                3, 65, "A sturdy iron helm.", "unknown.png", 1, null)
                .withTier(2).withRarity(Rarity.COMMON).withShopAvailable(true));

        items.add(new Item("steel_helm", "Steel Helm", Item.Type.HELM, ItemSlot.HELM,
                5, 180, "Forged from tempered steel.", "unknown.png", 1, null)
                .withTier(3).withRarity(Rarity.UNCOMMON));

        items.add(new Item("crown_of_warding", "Crown of Warding", Item.Type.HELM, ItemSlot.HELM,
                8, 550, "A crown that deflects hostile magic.", "unknown.png", 1, null)
                .withTier(5).withRarity(Rarity.EPIC).withSpellResist(15));

        // ── Shields ─────────────────────────────────────────────────────────────
        items.add(new Item("wooden_shield", "Wooden Shield", Item.Type.SHIELD, ItemSlot.SHIELD,
                1, 25, "A battered wooden shield.", "unknown.png", 1, null)
                .withTier(1).withRarity(Rarity.COMMON).withShopAvailable(true));

        items.add(new Item("iron_shield", "Iron Shield", Item.Type.SHIELD, ItemSlot.SHIELD,
                3, 80, "A solid iron shield.", "unknown.png", 1, null)
                .withTier(2).withRarity(Rarity.UNCOMMON).withShopAvailable(true));

        items.add(new Item("tower_shield", "Tower Shield", Item.Type.SHIELD, ItemSlot.SHIELD,
                6, 240, "A massive shield offering great protection.", "unknown.png", 1, null)
                .withTier(3).withRarity(Rarity.UNCOMMON));

        items.add(new Item("aegis_of_light", "Aegis of Light", Item.Type.SHIELD, ItemSlot.SHIELD,
                9, 650, "A radiant shield blessed by the gods.", "unknown.png", 1, null)
                .withTier(5).withRarity(Rarity.EPIC).withSpellResist(10));

        // ── Amulets ─────────────────────────────────────────────────────────────
        items.add(new Item("bone_amulet", "Bone Amulet", Item.Type.AMULET, ItemSlot.AMULET,
                0, 15, "A crude amulet carved from bone.", "unknown.png", 1,
                new Effect(Effect.EffectType.PROTECTION, 1))
                .withTier(1).withRarity(Rarity.COMMON));

        items.add(new Item("amulet_of_vitality", "Amulet of Vitality", Item.Type.AMULET, ItemSlot.AMULET,
                0, 120, "Pulses with restorative energy.", "unknown.png", 1,
                new Effect(Effect.EffectType.REGENERATION, 1))
                .withTier(2).withRarity(Rarity.UNCOMMON).withShopAvailable(true));

        items.add(new Item("amulet_of_warding", "Amulet of Warding", Item.Type.AMULET, ItemSlot.AMULET,
                0, 280, "Shields the wearer from hostile spells.", "unknown.png", 1, null)
                .withTier(3).withRarity(Rarity.RARE).withSpellResist(15));

        items.add(new Item("amulet_of_the_arcane", "Amulet of the Arcane", Item.Type.AMULET, ItemSlot.AMULET,
                0, 700, "An ancient amulet thrumming with arcane power.", "unknown.png", 1,
                new Effect(Effect.EffectType.PROTECTION, 2))
                .withTier(5).withRarity(Rarity.EPIC).withSpellResist(25));

        // ── Shop-only items ───────────────────────────────────────────────────
        items.add(new Item("antidote", "Antidote", Item.Type.POTION, ItemSlot.CONSUMABLE,
                0, 40, "Cures poison.", "potion_green.png", 10, null)
                .withTier(1).withRarity(Rarity.UNCOMMON).withLootable(false).withShopAvailable(true));
    }

    /**
     * Returns an unmodifiable view of all registered items.
     * To mutate the list use {@link #addItem}, {@link #removeItem}, or {@link #updateItem}.
     *
     * @return unmodifiable list of items
     */
    public static List<Item> getAllItems() {
        return java.util.Collections.unmodifiableList(items);
    }

    /**
     * Returns the item with the given ID, or {@code null} if not found.
     *
     * @param id the item ID
     * @return the matching item, or {@code null}
     */
    public static Item getById(String id) {
        return items.stream().filter(i -> i.getId().equals(id)).findFirst().orElse(null);
    }

    /**
     * Adds a new item to the registry and persists the change.
     *
     * @param item the item to add
     */
    public static void addItem(Item item) {
        items.add(item);
        saveItems();
    }

    /**
     * Removes the item at the given index from the registry and persists the change.
     *
     * @param index the zero-based index to remove
     * @throws IndexOutOfBoundsException if {@code index} is out of range
     */
    public static void removeItem(int index) {
        items.remove(index);
        saveItems();
    }

    /**
     * Replaces the item at the given index with an updated version and persists the change.
     *
     * @param index   the zero-based index to replace
     * @param updated the replacement item
     * @throws IndexOutOfBoundsException if {@code index} is out of range
     */
    public static void updateItem(int index, Item updated) {
        items.set(index, updated);
        saveItems();
    }

    /**
     * Returns all items marked {@link Item#isShopAvailable()}, sorted by base price.
     * This is the stock list for {@link ShopOverlay}.
     *
     * @return unmodifiable list of shop-available items sorted cheapest-first
     */
    public static java.util.List<Item> getAllShopItems() {
        return java.util.Collections.unmodifiableList(
            items.stream()
                 .filter(Item::isShopAvailable)
                 .sorted(java.util.Comparator.comparingInt(Item::getBasePrice))
                 .collect(java.util.stream.Collectors.toList()));
    }

    /**
     * Returns an unmodifiable view of all items that can appear in dungeon loot
     * (i.e. {@link Item#isLootable()} returns {@code true}).
     *
     * @return unmodifiable filtered list
     */
    public static java.util.List<Item> getAllLootable() {
        return java.util.Collections.unmodifiableList(
            items.stream().filter(Item::isLootable)
                          .collect(java.util.stream.Collectors.toList()));
    }

}

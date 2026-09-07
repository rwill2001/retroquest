package io.cannonforge.retroquest.registry;
import java.util.ArrayList;
import java.util.List;

import io.cannonforge.retroquest.model.Item;
import io.cannonforge.retroquest.model.ItemSlot;
import io.cannonforge.retroquest.model.Rarity;

/**
 * Generates random item drops from {@link ItemRegistry} based on dungeon depth,
 * monster level, and player level.
 *
 * <p>Selection algorithm:
 * <ol>
 *   <li>Compute a <em>target tier</em> from the context (depth or monster level),
 *       capped at the player's level so no loot exceeds what the player can use.</li>
 *   <li>Pick a random tier from 1 to the capped tier (uniform distribution),
 *       so a level-5 player has equal chance of tiers 1–5.</li>
 *   <li>Collect all {@code lootable} items whose tier matches the chosen tier.</li>
 *   <li>Fall back to the tier window [chosen − 1, chosen + 1], then to all
 *       lootable items if still empty.</li>
 *   <li>Pick one item by weighted random using each item's
 *       {@link Rarity#getWeight()} — higher weight = more frequent.</li>
 * </ol>
 */
public class LootGenerator {

    /**
     * Returns a random loot item appropriate for the given dungeon depth,
     * capped at the player's level.
     *
     * @param dungeonDepth current dungeon level (1–50)
     * @param playerLevel  current player level (caps the maximum item tier)
     * @return a randomly selected item from the registry, never {@code null}
     */
    public static Item getRandomLoot(int dungeonDepth, int playerLevel) {
        int depthTier = Math.max(1, (dungeonDepth + 4) / 5);
        int maxTier   = Math.max(1, Math.min(depthTier, playerLevel));
        int chosenTier = 1 + (int)(Math.random() * maxTier); // uniform 1..maxTier
        return pickFromRegistry(chosenTier, maxTier);
    }

    /**
     * Returns a random loot item appropriate for a monster of the given level,
     * capped at the player's level.
     *
     * @param monsterLevel level of the defeated monster
     * @param playerLevel  current player level (caps the maximum item tier)
     * @return a randomly selected item, never {@code null}
     */
    public static Item getRandomLootForMonster(int monsterLevel, int playerLevel) {
        int monsterTier = Math.max(1, (monsterLevel + 1) / 2);
        int maxTier     = Math.max(1, Math.min(monsterTier, playerLevel));
        int chosenTier  = 1 + (int)(Math.random() * maxTier); // uniform 1..maxTier
        return pickFromRegistry(chosenTier, maxTier);
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private static Item pickFromRegistry(int chosenTier, int maxTier) {
        List<Item> all = ItemRegistry.getAllItems();

        // Build candidate list: lootable items matching the chosen tier exactly
        List<Item> candidates = new ArrayList<>();
        for (Item item : all) {
            if (!item.isLootable()) continue;
            if (item.getTier() == chosenTier) candidates.add(item);
        }

        // Widen to [chosenTier - 1, chosenTier + 1] if exact match is empty,
        // but never exceed maxTier
        if (candidates.isEmpty()) {
            int lo = Math.max(1, chosenTier - 1);
            int hi = Math.min(maxTier, chosenTier + 1);
            for (Item item : all) {
                if (!item.isLootable()) continue;
                int t = item.getTier();
                if (t >= lo && t <= hi) candidates.add(item);
            }
        }

        // Fallback: any lootable item at or below maxTier
        if (candidates.isEmpty()) {
            for (Item item : all) {
                if (item.isLootable() && item.getTier() <= maxTier) candidates.add(item);
            }
        }

        // Last-resort fallback: any lootable item (safety net)
        if (candidates.isEmpty()) {
            for (Item item : all) {
                if (item.isLootable()) candidates.add(item);
            }
        }

        // Absolute last resort: a plain healing potion (avoids null returns)
        if (candidates.isEmpty()) {
            return new Item("Healing Potion", Item.Type.POTION, ItemSlot.CONSUMABLE, 25, null);
        }

        // Weighted random by rarity weight, boosted at higher tiers
        int totalWeight = 0;
        for (Item item : candidates) totalWeight += effectiveWeight(item, chosenTier);

        int roll = (int)(Math.random() * totalWeight);
        for (Item item : candidates) {
            roll -= effectiveWeight(item, chosenTier);
            if (roll < 0) return item;
        }

        return candidates.get(candidates.size() - 1);
    }

    /**
     * Returns the effective drop weight for an item at the given target tier.
     * At tier 5+, RARE weight is doubled and EPIC weight is quadrupled so that
     * deeper dungeons feel meaningfully more rewarding without making commons disappear.
     */
    private static int effectiveWeight(Item item, int targetTier) {
        int base = item.getRarity().getWeight();
        if (targetTier < 5) return base;
        return switch (item.getRarity()) {
            case RARE      -> base * 2;
            case EPIC      -> base * 4;
            case LEGENDARY -> base * 6;
            default        -> base;
        };
    }
}

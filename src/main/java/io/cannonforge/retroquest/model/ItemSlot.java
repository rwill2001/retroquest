package io.cannonforge.retroquest.model;

/**
 * Defines which body slot or inventory category an item belongs to.
 * Used for visual equipping rules and future paper-doll graphics.
 */
public enum ItemSlot {
    WEAPON,      // Main hand
    ARMOR,       // Body armor
    HELM,        // Head slot
    AMULET,      // Neck slot
    SHIELD,      // Off-hand
    RING,        // Rings (we support two: left/right)
    CONSUMABLE,  // Potions, scrolls, food
    KEY,         // Quest keys
    MISC         // Everything else (gems, junk, etc.)
}

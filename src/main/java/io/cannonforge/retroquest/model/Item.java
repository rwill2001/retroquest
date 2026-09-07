package io.cannonforge.retroquest.model;
import java.util.ArrayList;
import java.util.List;

import io.cannonforge.retroquest.registry.LootGenerator;

/**
 * Represents a game item (weapon, armor, potion, ring, scroll, etc.).
 *
 * <p>Items have a unique string ID, a type, an equipment slot, a value, a sell price,
 * a description, a sprite name, a max stack size, and an optional passive {@link Effect}.
 *
 * <p><b>Loot system fields (added for data-driven drops):</b>
 * <ul>
 *   <li>{@code tier} — power level 1–12+; the {@link LootGenerator} uses this to match
 *       items to dungeon depth (depth ≈ tier × 5).</li>
 *   <li>{@code rarity} — {@link Rarity} enum controlling drop frequency and price scaling.</li>
 *   <li>{@code lootable} — when {@code false} the item is shop-only and never drops from chests.</li>
 * </ul>
 *
 * <p>Use the fluent setters {@link #withTier}, {@link #withRarity}, and {@link #withLootable}
 * to set these fields without changing existing constructor call sites.
 */
public class Item {

    /**
     * Broad category of the item.  Determines valid equipment slots and
     * how the item is handled in the inventory, shop, and loot tables.
     * RING_PROTECTION and RING_REGEN are kept for save-file compatibility;
     * new items should use RING with an explicit {@link Effect}.
     */
    public enum Type {
        WEAPON, ARMOR, POTION, RING_PROTECTION, RING_REGEN,
        SCROLL, WAND, KEY, MISC, RING, AMULET, HELM, SHIELD,
        /** Rations and the like. {@code value} is the food restored on eating. */
        FOOD
    }

    private String   id;
    private String   name;
    private Type     type;
    private ItemSlot slotType;
    private int      value;
    private int      price;
    private String   description;
    private String   spriteName;
    private int      maxStackSize = 1;

    /** Power tier (1 = weakest, 12+ = endgame). Used by {@link LootGenerator}. */
    private int    tier     = 1;
    /** Drop frequency and price multiplier. */
    private Rarity rarity   = Rarity.COMMON;
    /** If {@code false} this item never appears in dungeon chest loot. */
    private boolean lootable = true;
    /** If {@code true} this item appears in shop stock. */
    private boolean shopAvailable = false;

    private List<Effect> effects = new ArrayList<>();

    /** For SCROLL and WAND items: the name of the spell this item casts. */
    private String spellName = null;

    /** For WAND items: initial number of charges. 0 means not a charged item. */
    private int charges = 0;

    /** Spell resistance bonus (0–50%). Reduces chance of being affected by monster spells. */
    private int spellResist = 0;

    // ── Constructors ──────────────────────────────────────────────────────────

    /** No-args constructor for Gson deserialization. */
    @SuppressWarnings("unused")
	private Item() {}

    public Item(String id, String name, Type type, ItemSlot slotType, int value, int price,
                String description, String spriteName, int maxStackSize, Effect effect) {
        this.id          = id != null ? id : "auto_" + System.currentTimeMillis();
        this.name        = name;
        this.type        = type;
        this.slotType    = slotType != null ? slotType : ItemSlot.MISC;
        this.value       = value;
        this.price       = price > 0 ? price : value * 3;
        this.description = description != null ? description : "A " + name.toLowerCase() + ".";
        this.spriteName  = spriteName  != null ? spriteName  : "unknown.png";
        this.maxStackSize = Math.max(1, maxStackSize);

        if (effect != null) this.effects.add(effect);
    }

    /** Convenience constructor (no id, no price, no description, no sprite). */
    public Item(String name, Type type, ItemSlot slotType, int value, Effect effect) {
        this(null, name, type, slotType, value, 0, null, null, 1, effect);
    }

    /** Minimal legacy constructor — kept for Player starting-item initialisation. */
    public Item(String name, Type type, int value) {
        this(name, type, getDefaultSlot(type), value, null);
    }

    private static ItemSlot getDefaultSlot(Type type) {
        return switch (type) {
            case WEAPON                   -> ItemSlot.WEAPON;
            case ARMOR                    -> ItemSlot.ARMOR;
            case HELM                     -> ItemSlot.HELM;
            case AMULET                   -> ItemSlot.AMULET;
            case SHIELD                   -> ItemSlot.SHIELD;
            case RING, RING_PROTECTION,
                 RING_REGEN               -> ItemSlot.RING;
            case POTION, SCROLL, WAND,
                 FOOD                     -> ItemSlot.CONSUMABLE;
            case KEY                      -> ItemSlot.KEY;
            default                       -> ItemSlot.MISC;
        };
    }

    // ── Fluent Loot-System Setters ─────────────────────────────────────────────

    /**
     * Sets the power tier and returns {@code this} for chaining.
     * Tier 1 = shallow dungeon; tier 10+ = deep endgame.
     */
    public Item withTier(int tier)         { this.tier    = Math.max(1, tier); return this; }

    /** Sets the drop rarity and returns {@code this} for chaining. */
    public Item withRarity(Rarity rarity)  { this.rarity  = rarity != null ? rarity : Rarity.COMMON; return this; }

    /**
     * Controls whether this item can appear in dungeon chest loot.
     * Set to {@code false} for shop-exclusive or quest-reward items.
     */
    public Item withLootable(boolean l)    { this.lootable = l; return this; }

    /** Controls whether this item appears in shop stock. */
    public Item withShopAvailable(boolean s) { this.shopAvailable = s; return this; }

    /** Binds this scroll or wand to a named spell. Returns {@code this} for chaining. */
    public Item withSpellName(String s)      { this.spellName = s; return this; }

    /** Sets the initial charge count for a wand. Returns {@code this} for chaining. */
    public Item withCharges(int c)           { this.charges = Math.max(0, c); return this; }

    /** Sets the spell resistance bonus (0–50%). Returns {@code this} for chaining. */
    public Item withSpellResist(int sr)      { this.spellResist = Math.max(0, sr); return this; }

    /** Replaces the effects list. Returns {@code this} for chaining. */
    public Item withEffects(List<Effect> fx) {
        this.effects.clear();
        if (fx != null) this.effects.addAll(fx);
        return this;
    }

    // ── Post-load Effect Rebuild ──────────────────────────────────────────────

    /**
     * Re-attaches effects that are implied by the item type.
     * Called after deserialising from JSON (save files, item registry).
     */
    public void rebuildEffects() {
        // Legacy ring types store their bonus in `value` rather than the effects list —
        // rebuild the effect from value so old saves still work.
        switch (type) {
            case RING_PROTECTION -> { effects.clear(); effects.add(new Effect(Effect.EffectType.PROTECTION,   value)); }
            case RING_REGEN      -> { effects.clear(); effects.add(new Effect(Effect.EffectType.REGENERATION, value)); }
            default -> {}
        }
        // Migrate legacy spellResist field to a SPELL_RESIST effect (handles old save files).
        if (spellResist > 0 && effects.stream().noneMatch(e -> e.getType() == Effect.EffectType.SPELL_RESIST)) {
            effects.add(new Effect(Effect.EffectType.SPELL_RESIST, spellResist));
            spellResist = 0;
        }
        // Inject base stat effect from value for types that use it for combat math.
        if (value > 0) {
            switch (type) {
                case WEAPON ->
                    { if (effects.stream().noneMatch(e -> e.getType() == Effect.EffectType.DAMAGE))
                        effects.add(new Effect(Effect.EffectType.DAMAGE, value)); }
                // AMULET and plain RING are included here because the inventory and shop
                // UIs print "+value" for them; without this they would advertise a bonus
                // they never actually granted.  An explicit PROTECTION effect in the JSON
                // still wins, so hand-authored amulets are unaffected.
                case ARMOR, SHIELD, HELM, AMULET, RING ->
                    { if (effects.stream().noneMatch(e -> e.getType() == Effect.EffectType.PROTECTION))
                        effects.add(new Effect(Effect.EffectType.PROTECTION, value)); }
                case POTION ->
                    { if (effects.stream().noneMatch(e -> e.getType() == Effect.EffectType.HEALING))
                        effects.add(new Effect(Effect.EffectType.HEALING, value)); }
                default -> {}
            }
        }
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public String       getId()           { return id; }
    public String       getName()         { return name; }
    public Type         getType()         { return type; }
    public ItemSlot     getSlotType()     { return slotType; }
    public int          getValue()        { return value; }
    public int          getPrice()        { return (int)(price * (rarity != null ? rarity.getPriceMultiplier() : 1f)); }
    public int          getBasePrice()    { return price; }
    public String       getDescription()  { return description; }
    public String       getSpriteName()   { return spriteName; }
    public int          getMaxStackSize() { return maxStackSize; }
    public int          getTier()         { return tier; }
    public Rarity       getRarity()       { return rarity != null ? rarity : Rarity.COMMON; }
    public boolean      isLootable()        { return lootable; }
    /**
     * True when this item may be stocked by a shop.  A price of zero means a quest token
     * or key with no market value: a shop would list it at the one-gold floor and buy it
     * straight back, so such items are never stocked regardless of the flag.
     */
    public boolean      isShopAvailable()   { return shopAvailable && price > 0; }
    public String       getSpellName()      { return spellName; }
    public int          getCharges()        { return charges; }
    public int          getSpellResist()    { return spellResist; }
    public List<Effect> getEffects()      { return new ArrayList<>(effects); }

    /**
     * Returns the gold a shop pays for this item.
     *
     * <p>Always a fraction of the shop's own asking price ({@link #getPrice()}), never of
     * {@code value} — {@code value} is a combat stat (damage, AC, HP restored) and has no
     * fixed relation to what an item costs, so deriving the sell price from it let cheap
     * items sell back for more than they cost.  Because the sell fraction is at most 40%,
     * buy-then-sell is a loss even at maximum Charisma, where the shop discounts the buy
     * price to 0.80× and pays 1.10× the sell price (0.40 × 1.10 = 0.44 &lt; 0.80).
     *
     * <p>Consumables resell worst (you are returning a used-up good), quest tokens and
     * keys next, equipment best.
     */
    public int getSellPrice() {
        int buy = getPrice();
        if (buy <= 0 || type == null) return 0;   // zero-price quest tokens are worthless
        int pct = switch (type) {
            case POTION, SCROLL, WAND, FOOD -> 25;
            case KEY, MISC            -> 35;
            default                   -> 40;      // weapons, armour, helms, shields, rings, amulets
        };
        return Math.max(1, buy * pct / 100);
    }

    @Override
    public String toString() {
        return name + (value > 0 ? " (" + value + ")" : "");
    }
}

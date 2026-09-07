package io.cannonforge.retroquest.model;

/**
 * Permanent character boons granted by gods, trials, or exceptional deeds.
 * Each boon is stored as an id string in {@link Player#acquiredBoons} and
 * contributes flat bonuses to stats computed in {@code Player}'s stat methods.
 *
 * <p>Sources span all seven islands — see individual enum constants for details.
 */
public enum Boon {

    // ── Island 1 ─────────────────────────────────────────────────────────────

    MOONBLESSED(
        "moonblessed",
        "Moonblessed",
        "Lirandel's blessing: +10 max HP. Heals you freely at sacred altars.",
        10, 0, 0, 0, true
    ),

    PYRALIS_TEMPER(
        "pyralis_temper",
        "Pyralis's Temper",
        "Fire burns in your veins: +2 attack damage.",
        0, 2, 0, 0, false
    ),

    // ── Island 4 ─────────────────────────────────────────────────────────────

    SYLVANDAR_ROOTS(
        "sylvandar_roots",
        "Sylvandar's Roots",
        "Living roots grow beneath your skin: +5 max HP, +1 AC.",
        5, 0, 1, 0, false
    ),

    // ── Reserve — future islands ─────────────────────────────────────────────

    ZEPHYRION_GRACE(
        "zephyrion_grace",
        "Zephyrion's Grace",
        "Wind shields you: +1 AC.",
        0, 0, 1, 0, false
    ),

    TIDAL_ENDURANCE(
        "tidal_endurance",
        "Tidal Endurance",
        "The deep hardens your will: +5% spell resist.",
        0, 0, 0, 5, false
    ),

    // ── Island 6 ─────────────────────────────────────────────────────────────

    UMBRYN_MEMORY(
        "umbryn_memory",
        "Umbryn's Memory",
        "You share the weight of ages: +3 damage, +5% spell resist, −5 max HP.",
        0, 3, 0, 5, false
    ),

    // ── Island 7 ─────────────────────────────────────────────────────────────

    IRON_BROTHERHOOD(
        "iron_brotherhood",
        "Iron Brotherhood",
        "Bellorak's bond: +3 attack damage, +1 AC.",
        0, 3, 1, 0, false
    );

    // ─────────────────────────────────────────────────────────────────────────

    /** Key stored in {@link Player#acquiredBoons}. */
    public final String id;
    public final String displayName;
    public final String description;

    /** Flat bonus added to {@link Player#getMaxHp()}. */
    public final int maxHpBonus;

    /** Flat bonus added to {@link Player#getDamage()}. */
    public final int damageBonus;

    /** Flat bonus added to {@link Player#getAC()}. */
    public final int acBonus;

    /** Flat % bonus added to {@link Player#getSpellResist()}. */
    public final int spellResistBonus;

    /**
     * If {@code true}, the player is healed fully at dungeon altars without
     * the normal gold tithe or random outcome.
     */
    public final boolean altarGrace;

    Boon(String id, String displayName, String description,
         int maxHpBonus, int damageBonus, int acBonus, int spellResistBonus,
         boolean altarGrace) {
        this.id              = id;
        this.displayName     = displayName;
        this.description     = description;
        this.maxHpBonus      = maxHpBonus;
        this.damageBonus     = damageBonus;
        this.acBonus         = acBonus;
        this.spellResistBonus = spellResistBonus;
        this.altarGrace      = altarGrace;
    }

    /** Returns the boon with the given id, or {@code null} if not found. */
    public static Boon byId(String id) {
        for (Boon b : values()) {
            if (b.id.equals(id)) return b;
        }
        return null;
    }
}

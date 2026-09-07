package io.cannonforge.retroquest.registry;
import java.util.ArrayList;
import java.util.List;

import io.cannonforge.retroquest.model.Monster;
import io.cannonforge.retroquest.model.MonsterType;

/**
 * Procedurally generates {@link Monster} instances scaled to a target level (1–50),
 * using classic CRPG-style name tables and stat formulas.
 *
 * <h2>Name tiers</h2>
 * <p>The 50 levels are divided into 10 tiers of 5 levels each
 * ({@code tier = (level - 1) / 5}). A random name is chosen from that tier's pool:
 * <pre>
 *   Tier 0  ( 1– 5): Kobold, Giant Rat, Goblin
 *   Tier 1  ( 6–10): Orc, Gnoll, Hobgoblin, Goblin Sorcerer
 *   Tier 2  (11–15): Lizard Man, Bugbear, Ghoul
 *   Tier 3  (16–20): Ogre, Troll, Minotaur, Dark Mage
 *   Tier 4  (21–25): Wraith, Specter, Wight
 *   Tier 5  (26–30): Medusa, Gargoyle, Vampire, Necromancer
 *   Tier 6  (31–35): Manticore, Chimera, Hydra
 *   Tier 7  (36–40): Demon, Devil, Nightmare
 *   Tier 8  (41–45): Dragon, Fire Giant, Death Knight, Lich
 *   Tier 9  (46–50): Arch Demon, Ancient Dragon, Death
 * </pre>
 *
 * <h2>Stat formulas</h2>
 * <pre>
 *   HP     = level × 8  + rand(level × 8)   → level 1: 8–16,  level 50: 400–800
 *   Damage = max(1, level/2 + rand(level/2))
 *   Gold   = level × 2  + rand(level × 3)
 *   XP     = level × (25 + level) + rand(level × 10)  → mean level × (30 + level)
 *   AC     = 10 + level/3
 * </pre>
 *
 * <p>The XP curve is quadratic on purpose. The old {@code level × 25} mean sat below the
 * hand-authored monsters from level 28 up, so XP per kill <em>fell</em> 44% exactly where
 * fights get harder. {@code level × (30 + level)} tracks the authored entries at every tier
 * (level 8 → 304 vs an authored 300; level 27 → 1539 vs an authored 1500) and keeps rising.
 *
 * <h2>Spell assignment (by name)</h2>
 * <ul>
 *   <li><b>Mage/Wizard/Sorcerer:</b> 45% cast chance, power = {@code level*2+5},
 *       spells: Magic Missile, Sleep, Fireball, Fear.</li>
 *   <li><b>Lich/Vampire/Necromancer:</b> 50% cast chance, power = {@code level*3},
 *       spells: Drain, Fear, Blind, Poison; 20–40 spell resistance.</li>
 *   <li><b>Dragon:</b> 60% cast chance, power = {@code level} — lower than the other casters
 *       because Fire Breath multiplies power by another 1.5.</li>
 *   <li><b>Demon/Devil:</b> 40% cast chance, power = {@code level*2},
 *       spells: Fireball, Fear, Stun; 30–40 spell resistance.</li>
 *   <li><b>Wraith/Specter/Wight/Ghost:</b> 35% cast chance, power = {@code level+5},
 *       spells: Drain, Fear, Blind; 20–35 spell resistance.</li>
 * </ul>
 * <p>Every one of those is then clamped to {@code level × MAX_SPELL_POWER_PER_LEVEL}
 * (see {@link #capSpellPower(int, int)}); monster spell damage is {@code power + rand(power/2)},
 * so an unclamped {@code level*3} caster out-damaged a level-appropriate player’s entire HP bar
 * in a single cast.
 *
 * <h2>Seed defaults</h2>
 * <p>{@link #seedDefaults()} returns 34 canonical monsters (one per name in the table)
 * plus 2 friendly monsters (Forest Fairy, Wandering Merchant) for a total of 36,
 * with midpoint stats (no randomness), IDs of the form {@code "factory_<snake_name>"},
 * and levels at the midpoint of each tier ({@code tier * 5 + 3}).
 * Used by {@link MonsterRegistry} to pre-populate {@code data/monsters.json} on first run.
 */
public class MonsterFactory {

    /**
     * Hard ceiling on monster spell power, as a multiple of monster level. A cast deals
     * {@code power + rand(power/2)} and a player has roughly {@code 3.3 × level} max HP, so
     * anything above 2× level is a one-shot at parity.
     */
    private static final int MAX_SPELL_POWER_PER_LEVEL = 2;

    // Names grouped by tier — tier 0 = levels 1-5, tier 9 = levels 46-50.
    private static final String[][] NAMES = {
        { "Kobold",        "Giant Rat",      "Goblin"                        },  // tier 0  (1-5)
        { "Orc",           "Gnoll",          "Hobgoblin",  "Goblin Sorcerer"},  // tier 1  (6-10)
        { "Lizard Man",    "Bugbear",        "Ghoul"                        },  // tier 2  (11-15)
        { "Ogre",          "Troll",          "Minotaur",   "Dark Mage"     },  // tier 3  (16-20)
        { "Wraith",        "Specter",        "Wight"                        },  // tier 4  (21-25)
        { "Medusa",        "Gargoyle",       "Vampire",    "Necromancer"   },  // tier 5  (26-30)
        { "Manticore",     "Chimera",        "Hydra"                        },  // tier 6  (31-35)
        { "Demon",         "Devil",          "Nightmare"                    },  // tier 7  (36-40)
        { "Dragon",        "Fire Giant",     "Death Knight","Lich"          },  // tier 8  (41-45)
        { "Arch Demon",    "Ancient Dragon", "Death"                        },  // tier 9  (46-50)
    };

    /**
     * Generates a fresh monster scaled to the given level using classic CRPG-style
     * stat formulas.  Each call adds randomness so repeated encounters at the
     * same level vary in difficulty.
     *
     * <p>Stat formulas (approximate classic scaling):
     * <ul>
     *   <li>HP:     {@code level*8 + rand(level*8)} — level 1: 8–16, level 50: 400–800</li>
     *   <li>Damage: {@code max(1, level/2 + rand(level/2))}</li>
     *   <li>Gold:   {@code level*2 + rand(level*3)}</li>
     *   <li>XP:     {@code level*(25+level) + rand(level*10)} — mean {@code level*(30+level)}</li>
     *   <li>AC:     {@code 10 + level/3}</li>
     * </ul>
     *
     * @param level target monster level, clamped to 1–50
     * @return a new {@link Monster} ready for combat
     */
    static Monster generate(int level) {
        level = Math.max(1, Math.min(50, level));

        int      tier = (level - 1) / 5;
        String[] pool = NAMES[tier];
        String   name = pool[(int)(Math.random() * pool.length)];

        int hp     = level * 8  + (int)(Math.random() * level * 8);
        int damage = Math.max(1, level / 2 + (int)(Math.random() * Math.max(1, level / 2)));
        int gold   = level * 2 + (int)(Math.random() * level * 3);
        int xp     = level * (25 + level) + (int)(Math.random() * level * 10);
        int ac     = 10 + level / 3;

        Monster m = new Monster(name, level, hp, damage, gold, xp, ac);
        assignSpellsByName(m, level);
        assignTypeByName(m);
        return m;
    }

    /**
     * Assigns spells, spell resistance, cast chance, and spell power to a monster
     * based on name keywords.  Called by both {@link #generate(int)} and
     * {@link #seedDefaults()}.
     */
    private static void assignSpellsByName(Monster m, int level) {
        String lowerName = m.getName().toLowerCase();
        if (lowerName.contains("mage") || lowerName.contains("wizard") || lowerName.contains("sorcerer")) {
            m.setSpellCastChance(45);
            m.setSpellPower(capSpellPower(level, level * 2 + 5));
            if (level <= 2) m.setSpellNames(List.of("Magic Missile"));
            else if (level <= 4) m.setSpellNames(List.of("Magic Missile", "Sleep"));
            else if (level <= 10) m.setSpellNames(List.of("Fireball", "Sleep", "Fear"));
            else m.setSpellNames(List.of("Fireball", "Sleep", "Blind", "Stun"));
        }
        if (lowerName.contains("lich") || lowerName.contains("vampire") || lowerName.contains("necromancer")) {
            m.setSpellCastChance(50);
            m.setSpellPower(capSpellPower(level, level * 3));
            m.setSpellNames(List.of("Drain", "Fear", "Blind", "Poison"));
            m.setSpellResistance(Math.min(40, 20 + level));
        }
        if (lowerName.contains("demon") || lowerName.contains("devil")) {
            m.setSpellCastChance(40);
            m.setSpellPower(capSpellPower(level, level * 2));
            m.setSpellNames(List.of("Fireball", "Fear", "Stun"));
            m.setSpellResistance(Math.min(40, 30 + level / 5));
        }
        if (lowerName.contains("wraith") || lowerName.contains("specter")
                || lowerName.contains("wight") || lowerName.contains("ghost")) {
            m.setSpellCastChance(35);
            m.setSpellPower(capSpellPower(level, level + 5));
            m.setSpellNames(List.of("Drain", "Fear", "Blind"));
            m.setSpellResistance(Math.min(35, 20 + level / 2));
        }
        if (lowerName.contains("dragon")) {
            m.setSpellCastChance(60);
            // Fire Breath applies its own x1.5, so dragons start from level, not level*3.
            m.setSpellPower(capSpellPower(level, level));
            m.setSpellNames(List.of("Fire Breath", "Fear", "Stun"));
            m.setSpellResistance(Math.min(40, 25 + level / 3));
        }
        // Spiders/serpents/fungi — poisoners
        if (lowerName.contains("spider") || lowerName.contains("serpent")
                || lowerName.contains("scorpion") || lowerName.contains("spore")
                || lowerName.contains("fungi") || lowerName.contains("viper")) {
            if (m.getSpellCastChance() == 0) {
                m.setSpellCastChance(30);
                m.setSpellPower(capSpellPower(level, level + 3));
                m.setSpellNames(List.of("Poison"));
            }
        }
    }

    /** Clamps a computed spell power to {@link #MAX_SPELL_POWER_PER_LEVEL} × level. */
    private static int capSpellPower(int level, int power) {
        return Math.min(level * MAX_SPELL_POWER_PER_LEVEL, power);
    }

    private static void assignTypeByName(Monster m) {
        String n = m.getName().toLowerCase();
        if (n.contains("skeleton") || n.contains("zombie") || n.contains("ghoul")
                || n.contains("wraith") || n.contains("specter") || n.contains("wight")
                || n.contains("vampire") || n.contains("lich") || n.contains("death knight")
                || n.contains("death") || n.contains("necromancer")) {
            m.setMonsterType(MonsterType.UNDEAD);
        } else if (n.contains("dragon")) {
            m.setMonsterType(MonsterType.DRAGON);
        } else if (n.contains("demon") || n.contains("devil") || n.contains("nightmare")) {
            m.setMonsterType(MonsterType.DEMON);
        } else if (n.contains("rat") || n.contains("kobold")) {
            m.setMonsterType(MonsterType.BEAST);
        } else if (n.contains("medusa") || n.contains("gargoyle") || n.contains("chimera")
                || n.contains("manticore") || n.contains("hydra")) {
            m.setMonsterType(MonsterType.MAGICAL);
        } else {
            m.setMonsterType(MonsterType.HUMANOID);
        }
    }

    /**
     * Returns one canonical, deterministic {@link Monster} entry for every name
     * in the factory table — 34 monsters total (10 tiers × 3–4 names).
     *
     * <p>Stats are fixed at the midpoint of the tier's level range with no
     * randomness, making these suitable as editable registry defaults.  Each
     * entry has a stable ID of the form {@code "factory_<snake_case_name>"} so
     * the registry can detect and preserve user edits across restarts.
     *
     * @return list of 36 seed monsters (34 combat + 2 friendly)
     */
    static List<Monster> seedDefaults() {
        List<Monster> result = new ArrayList<>();
        for (int tier = 0; tier < NAMES.length; tier++) {
            // Mid-level of the tier: tier 0 → level 3, tier 1 → level 8, …
            int level = tier * 5 + 3;
            for (String name : NAMES[tier]) {
                String id    = "factory_" + name.toLowerCase().replace(" ", "_");
                String image = name.toLowerCase().replace(" ", "_") + ".png";

                int hp     = level * 8 + level * 4;           // midpoint of rand range
                int damage = Math.max(1, level / 2 + level / 4);
                int gold   = level * 2 + level * 2;
                int xp     = level * (30 + level);            // mean of generate()’s XP roll
                int ac     = 10 + level / 3;

                Monster m = new Monster(name, level, hp, damage, gold, xp, ac);
                m.setId(id);
                m.setImageFileName(image);
                assignSpellsByName(m, level);
                assignTypeByName(m);
                result.add(m);
            }
        }

        // Friendly monster defaults
        Monster fairy = new Monster("Forest Fairy", 5, 10, 1, 0, 0, 5);
        fairy.setId("factory_forest_fairy");
        fairy.setImageFileName("forest_fairy.png");
        fairy.setMonsterType(MonsterType.FRIENDLY);
        fairy.setFriendly(true);
        result.add(fairy);

        Monster merchant = new Monster("Wandering Merchant", 10, 20, 1, 0, 0, 8);
        merchant.setId("factory_wandering_merchant");
        merchant.setImageFileName("wandering_merchant.png");
        merchant.setMonsterType(MonsterType.FRIENDLY);
        merchant.setFriendly(true);
        result.add(merchant);

        return result;
    }
}

package io.cannonforge.retroquest.model;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.cannonforge.retroquest.registry.ImageAssetRegistry;
import io.cannonforge.retroquest.registry.MonsterFactory;

/**
 * Data model for a single monster, used both as a registry template and as a live combat instance.
 *
 * <h2>Constructors</h2>
 * <ul>
 *   <li>{@link #Monster(Monster)} — copy constructor; creates a combat-ready instance from a
 *       registry template (full HP, status effects reset, spell info preserved).</li>
 *   <li>{@link #Monster(String, String, String, int, int, int)} — basic constructor;
 *       derives {@code xpValue = hp * 2}, {@code level = max(1, hp/8)},
 *       {@code ac = 10 + level/2}.</li>
 *   <li>Level-explicit constructor — used by {@link MonsterFactory} for procedural generation.</li>
 * </ul>
 *
 * <h2>Combat stats</h2>
 * <ul>
 *   <li>{@code hp} / {@code maxHp} — current and maximum hit points.</li>
 *   <li>{@code damage} — base melee damage per hit (actual roll: {@code (damage/2+1) + rand(damage+1)}).</li>
 *   <li>{@code ac} — armor class; higher = harder to hit.</li>
 *   <li>{@code level} — scales to-hit rolls and XP/gold rewards.</li>
 * </ul>
 *
 * <h2>Spellcasting</h2>
 * <ul>
 *   <li>{@code spellNames} — list of spell names the monster may cast each turn.</li>
 *   <li>{@code spellCastChance} — percentage (0–100) chance per turn to cast instead of melee.</li>
 *   <li>{@code spellPower} — base damage/effect magnitude for spells.</li>
 *   <li>{@code spellResistance} — 0–40; percentage chance to resist incoming spell effects.</li>
 * </ul>
 *
 * <h2>Transient status effects (combat only, not persisted)</h2>
 * <ul>
 *   <li>{@code sleepTurns} — monster skips its turn each turn this is positive.</li>
 *   <li>{@code charmTurns} — monster stands idle instead of attacking while positive.</li>
 * </ul>
 *
 * <h2>Sprite key</h2>
 * <p>The {@code imageFileName} (e.g. {@code "dragon.png"}) maps to an
 * {@link ImageAssetRegistry} key via {@code "monsters/" + imageFileName.replace(".png", "")}.
 */
public class Monster {
    private String id;
    private String name;
    private String imageFileName;
    private int hp;
    private final int maxHp;
    private int damage;
    private int goldReward;
    private int xpValue;
    private int ac;
    private int level;

    // Status effects (not saved to JSON)
    private transient int sleepTurns = 0;
    private transient int charmTurns = 0;

    /** 0–40: percentage chance to resist a spell effect (0 = no resistance). */
    private int spellResistance = 0;
    /** Names of spells this monster can cast (must match player spell names). */
    private List<String> spellNames = new ArrayList<>();
    /** 0–100: percent chance per turn the monster tries to cast instead of melee. */
    private int spellCastChance = 0;
    /** Base power for monster spells (scales like basePower in Spell). */
    private int spellPower = 0;

    /** Monster category — affects special abilities and spell interactions. Null defaults to HUMANOID. */
    private MonsterType monsterType;
    /** If true, this monster offers a gift instead of fighting. */
    private boolean friendly;
    /** If true, this monster spawns preferentially near water tiles. */
    private boolean aquatic;

    // Gson no-arg constructor
    public Monster() {
        this.maxHp = 0; // required for Gson
    }
    // Copy constructor - creates a fresh monster with full health
    public Monster(Monster template) {
        this.id = template.id;
        this.name = template.name;
        this.imageFileName = template.imageFileName;
        this.hp = template.maxHp;           // always start at full health
        this.maxHp = template.maxHp;
        this.damage = template.damage;
        this.goldReward = template.goldReward;
        this.xpValue = template.xpValue;
        this.ac = template.ac;
        this.level = template.level;

        // Reset status effects
        this.sleepTurns = 0;
        this.charmTurns = 0;
        this.spellResistance = template.spellResistance;
        this.spellNames = new ArrayList<>(template.spellNames != null ? template.spellNames : new ArrayList<>());
        this.spellCastChance = template.spellCastChance;
        this.spellPower = template.spellPower;
        this.monsterType = template.monsterType;
        this.friendly = template.friendly;
        this.aquatic = template.aquatic;
    }
    // Full constructor used by editor + registry
    public Monster(String id, String name, String imageFileName, int hp, int damage, int goldReward) {
        this.id = id != null ? id : "monster_" + System.currentTimeMillis();
        this.name = name;
        this.imageFileName = imageFileName != null ? imageFileName : name.toLowerCase() + ".png";
        this.hp = hp;
        this.maxHp = hp;                    // ← maxHp is set once and never changes
        this.damage = damage;
        this.goldReward = goldReward;
        this.xpValue = hp * 2;
        this.level = Math.max(1, hp / 8);
        this.ac = 10 + this.level / 2;
    }

    // Backward compatibility constructor (used by old code and CombatDialog)
    public Monster(String name, int hp, int damage, int goldReward) {
        this(null, name, null, hp, damage, goldReward);
    }

    // Level-explicit constructor used by MonsterFactory for procedural generation
    public Monster(String name, int level, int hp, int damage, int goldReward, int xpValue, int ac) {
        this.id            = "generated_" + System.currentTimeMillis();
        this.name          = name;
        this.imageFileName = name.toLowerCase().replace(" ", "_") + ".png";
        this.hp            = hp;
        this.maxHp         = hp;
        this.damage        = damage;
        this.goldReward    = goldReward;
        this.xpValue       = xpValue;
        this.level         = level;
        this.ac            = ac;
        this.sleepTurns    = 0;
        this.charmTurns    = 0;
    }

    // ==================== GETTERS & SETTERS ====================
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getImageFileName() { return imageFileName; }
    public void setImageFileName(String imageFileName) { this.imageFileName = imageFileName; }

    public int getHp() { return hp; }
    public int getMaxHp() { return maxHp; }
    public void setHp(int hp) { this.hp = Math.min(maxHp, Math.max(0, hp)); }

    public int getDamage() { return damage; }
    public void setDamage(int damage) { this.damage = damage; }
    public int getGoldReward() { return goldReward; }
    public void setGoldReward(int goldReward) { this.goldReward = goldReward; }
    public int getXPValue() { return xpValue; }
    public void setXpValue(int xp) { this.xpValue = Math.max(0, xp); }
    public int getAC() { return ac; }
    public void setAC(int ac) { this.ac = Math.max(0, ac); }
    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = Math.max(1, Math.min(50, level)); }

    // Status effects
    public boolean isAsleep() { return sleepTurns > 0; }
    public boolean isCharmed() { return charmTurns > 0; }
    public void takeDamage(int dmg) { hp = Math.max(0, hp - dmg); }
    public boolean isDead() { return hp <= 0; }
    public void decrementSleep() { if (sleepTurns > 0) sleepTurns--; }
    public void setAsleep(int turns) { sleepTurns = turns; charmTurns = 0; }
    /** Charms the monster for a limited number of turns (it used to last forever). */
    public void setCharmed(int turns) { charmTurns = Math.max(1, turns); sleepTurns = 0; }
    public void decrementCharm() { if (charmTurns > 0) charmTurns--; }
    public void clearStatus() { sleepTurns = 0; charmTurns = 0; }

    // Spell resistance
    public int  getSpellResistance()      { return spellResistance; }
    public void setSpellResistance(int r) { this.spellResistance = Math.max(0, Math.min(40, r)); }

    // Spellcasting
    public List<String> getSpellNames()           { return Collections.unmodifiableList(spellNames != null ? spellNames : new ArrayList<>()); }
    public void setSpellNames(List<String> names) { this.spellNames = names != null ? names : new ArrayList<>(); }
    public int  getSpellCastChance()              { return spellCastChance; }
    public void setSpellCastChance(int c)         { this.spellCastChance = Math.max(0, Math.min(100, c)); }
    public int  getSpellPower()                   { return spellPower; }
    public void setSpellPower(int p)              { this.spellPower = Math.max(0, p); }

    public boolean canCastSpells() {
        return spellCastChance > 0 && spellNames != null && !spellNames.isEmpty();
    }

    public String pickRandomSpell() {
        if (spellNames == null || spellNames.isEmpty()) return null;
        return spellNames.get((int)(Math.random() * spellNames.size()));
    }

    // Monster type
    public MonsterType getMonsterType() { return monsterType != null ? monsterType : MonsterType.HUMANOID; }
    public void setMonsterType(MonsterType t) { this.monsterType = t; }
    public boolean isFriendly() { return friendly || monsterType == MonsterType.FRIENDLY; }
    public void setFriendly(boolean f) { this.friendly = f; }
    public boolean isAquatic() { return aquatic; }
    public void setAquatic(boolean a) { this.aquatic = a; }

    @Override
    public String toString() {
        return name + " (Lv" + level + ", " + hp + "/" + maxHp + "hp)";
    }
}

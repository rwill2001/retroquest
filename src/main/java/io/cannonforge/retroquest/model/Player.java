package io.cannonforge.retroquest.model;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import io.cannonforge.retroquest.registry.ItemRegistry;
import io.cannonforge.retroquest.registry.TileRegistry;

/**
 * Represents the player character in RetroQuest.
 *
 * <p>Manages stats (STR/DEX/CON/INT/WIS/CHA), a fixed 20-slot inventory,
 * equipped items, the 37-spell system, active effects, quests,
 * and tile-based movement with food consumption.
 *
 * <h2>Combat stats</h2>
 * <ul>
 *   <li>{@code getAC()} — {@code 10 + (DEX-10)/2 + min(gear+boon AC, level + 2)}.
 *       The DEX term is uncapped; everything that does <em>not</em> come from DEX (equipment
 *       PROTECTION effects and boons) is capped at {@code level + 2}. Monster to-hit is
 *       {@code d20 + monsterLevel >= playerAC}, so AC that outruns the level curve makes the
 *       player literally unhittable — best-in-slot gear alone sums to +44 AC, which used to
 *       switch melee off entirely from roughly level 15 to 35. The cap keeps AC growing at
 *       +1 per level so an even-level monster still lands roughly one swing in three.</li>
 *   <li>{@code getDamage()} — {@code (STR - 10) + level/3 + dmgBonus}; used for melee and
 *       Invisibility hits. The {@code level/3} term exists because the last weapon upgrade
 *       arrives around level 20 while monster HP keeps climbing to 400–800 at level 50.</li>
 *   <li>{@code getDamageReduction()} — {@code max(0, (CON-10)/3)}; subtracted from each incoming hit.</li>
 *   <li>{@code getTotalSpellResist()} — {@code min(50%, (WIS-10)/2 * 5% + itemBonuses)};
 *       chance to ignore a monster spell effect.</li>
 *   <li>{@code takeDamage(int)} — applies reduction, clamps to 0; triggers resurrection ward
 *       check externally via {@link #triggerResurrection()}.</li>
 * </ul>
 *
 * <h2>Spell buffs (step-countdown, persist across combat)</h2>
 * <p>Each buff is a step counter decremented by {@code move()}. Active when counter {@code > 0}.
 * <ul>
 *   <li><b>Haste</b> ({@code hasteSteps = 25}) — grants a second melee attack per round.</li>
 *   <li><b>Prayer</b> ({@code prayerSteps = 40}) — +2 AC, +2 to-hit.</li>
 *   <li><b>Holy Armor</b> ({@code holyArmorSteps = 50}) — +5 AC.</li>
 *   <li><b>Shield</b> ({@code shieldSteps = 35}) — +2 AC (mage defensive buff).</li>
 *   <li><b>Resist Elements</b> ({@code elemResSteps = 35}) — halves incoming spell/breath damage.</li>
 *   <li><b>Invisibility</b> ({@code invisSteps = 20}) — next attack auto-hits and is consumed.</li>
 *   <li><b>Prot. Ev.</b> ({@code protEvSteps = 35}) — +3 AC.</li>
 *   <li><b>Bless</b> ({@code blessSteps = 30}) — +2 damage, +1 more per 5 levels.</li>
 * </ul>
 * Combined bonuses: {@code getAcBonus()} (max +5 — the four AC wards do <em>not</em> stack,
 * the best one wins), {@code getHitBonus()} (max +2),
 * {@code getDmgBonus()} ({@code 2 + level/5}).
 *
 * <p>These counters are decremented by {@link #move()}, so a buff is spent by WALKING, not by
 * fighting: it survives the battle it was cast in and carries into the next one. That is
 * deliberate — the spell descriptions say "for a while" rather than "this battle" — and it is
 * why nothing clears them when combat ends.
 *
 * <h2>XP and levelling</h2>
 * <p>{@code xpToNextLevel = 900 × level² / (level + 4)} — see {@link #xpNeededFor(int)}.
 * On level-up: HP increases by {@code 3 + (CON-10)/3} and spell slots are recalculated.
 *
 * <h2>Resurrection ward</h2>
 * <p>If {@code resurrectionCharged} is true, the first lethal hit is intercepted:
 * {@link #triggerResurrection()} consumes the ward and restores HP to 50% of max.
 */
public class Player {
    // ── Spell buff durations (steps before expiry) ──
    private static final int SHIELD_DURATION      = 35;
    private static final int HASTE_DURATION      = 25;
    private static final int PRAYER_DURATION     = 40;
    private static final int HOLY_ARMOR_DURATION = 50;
    private static final int ELEM_RESIST_DURATION = 35;
    private static final int INVIS_DURATION      = 20;
    private static final int PROT_EV_DURATION    = 35;
    private static final int BLESS_DURATION      = 30;

    // ── Spell buff AC/hit/dmg bonuses ──
    private static final int SHIELD_AC_BONUS      = 2;
    private static final int PROT_EV_AC_BONUS    = 3;
    private static final int PRAYER_AC_BONUS     = 2;
    private static final int HOLY_ARMOR_AC_BONUS = 5;
    private static final int PRAYER_HIT_BONUS    = 2;
    private static final int BLESS_DMG_BONUS     = 2;

    // ── Combat scaling caps ──
    /** Gear + boon AC is capped at {@code level + this}; DEX-derived AC is never capped. */
    private static final int NON_DEX_AC_CAP_BASE  = 2;
    /** Melee damage gains {@code level / this} so it keeps pace with late-game monster HP. */
    private static final int DAMAGE_LEVEL_DIVISOR = 3;

    private final String name;
    public static Consumer<String> questCompleteLogger = null;
    public static Consumer<LevelUpResult> levelUpLogger = null;
    public static Consumer<Quest> questCompleteNotifier = null;
    public static Consumer<LevelUpResult> levelUpNotifier = null;

    private int x, y;
    /** Facing direction for first-person dungeon view: 0=N, 1=E, 2=S, 3=W. */
    private int facing = 0;
    private int hp, maxHp;
    private int level = 1;
    private int xp = 0;
    private int xpToNextLevel = xpNeededFor(1);

    // SIX CORE ATTRIBUTES
    private final int str, dex, con, intelligence, wisdom, charisma;

    private int gold = 25;
    private int food = 500;

    // Equipped items — rebuildEffects() called in constructors after ItemRegistry is ready
    private Item weapon = defaultWeapon();
    private Item armor  = defaultArmor();
    private Item ring1  = null;
    private Item ring2  = null;
    private Item helm   = null;
    private Item amulet = null;
    private Item shield = null;

    private InventorySlot[] inventorySlots = new InventorySlot[20];

    // 37-SPELL SYSTEM
    private Spell[] knownSpells;
    private int[] mageCastsRemaining = new int[7];
    private int[] clericCastsRemaining = new int[7];

    private final List<Quest> activeQuests = new ArrayList<>();
    private final List<Quest> completedQuests = new ArrayList<>();

    private transient final List<Effect> activeEffects = new ArrayList<>();

    // ── Active spell buffs (step-based; ticked on each move(), persist post-combat) ──
    private int shieldSteps     = 0;   // Shield: +2 AC (mage buff)
    private int hasteSteps      = 0;   // Haste: bonus attack each round
    private int prayerSteps     = 0;   // Prayer: +2 AC, +2 to-hit
    private int holyArmorSteps  = 0;   // Holy Armor: +5 AC
    private int elemResSteps    = 0;   // Resist Elements: halve spell/breath damage
    private int invisSteps      = 0;   // Invisibility: next attack auto-hits (consumed)
    private int protEvSteps     = 0;   // Protection from Evil: +3 AC
    private int blessSteps      = 0;   // Bless: +2 damage
    private int drunkSteps      = 0;   // Drunk: random movement misdirection
    private int poisonSteps     = 0;   // Poisoned: damage each step, cured by Antidote or Restoration

    /** Parallel to knownSpells[]; true = this spell has been permanently learned. */
    /** Must match {@code knownSpells.length} (38) — a short array here throws the moment a 38th spell is added. */
    private boolean[] spellLearned = new boolean[38];

    // ── Resurrection Ward ───────────────────────────────────────────────────
    private boolean resurrectionCharged = false;

    // ── Detect Magic (legacy — kept for old save compat, unused) ────────────
    private transient long detectMagicExpiry = 0;

    // ── Persistent dialogue flags (survives save/load; null-safe for old saves) ──
    private Map<String, String> flags;

    // ── Key ring (permanent keys that don't consume inventory slots) ──
    private List<String> keyRing;

    // ── Divine favor (0–100 per god; 0 = they have not noticed you yet) ───────
    private int[] favorScores;

    // ── Gambling limits (net winnings this rest cycle, reset on inn rest) ────
    private int gamblingNetWinnings;

    // ── Permanent boons (stored as id strings; survives save/load) ───────────
    private java.util.Set<String> acquiredBoons = new java.util.HashSet<>();


    // ==================== CONSTRUCTORS ====================

    // Main constructor
    public Player(int startX, int startY, int s, int d, int c, int i, int w, int ch, String name) {
        this.x = startX;
        this.y = startY;
        this.str = s; this.dex = d; this.con = c;
        this.intelligence = i; this.wisdom = w; this.charisma = ch;
        this.name = name;

        this.knownSpells = new Spell[38];
        // Starting HP. CON+6 left an average roll on 15 HP, which a single level-2 wolf beats
        // before the first town — scripted playthroughs died three times in the opening
        // minutes. CON+10 survives the walk to Moonhaven without making the early game safe.
        this.hp = con + 10;
        this.maxHp = hp;

        weapon.rebuildEffects();
        armor.rebuildEffects();
        // ...and fold them into the player, or the gear is worn but not felt.
        rebuildEffects();
        initInventory();
        initSpells();
        initLearnedSpells();
        refreshSpellSlots();
    }

    // Gson no-arg constructor
    public Player() {
        this.name = "Hero";
        this.str = this.dex = this.con = this.intelligence = this.wisdom = this.charisma = 10;
        this.x = 12; this.y = 12;
        this.level = 1;
        this.hp = this.maxHp = 15;
        this.gold = 25;
        this.food = 500;

        this.weapon = defaultWeapon();
        this.armor  = defaultArmor();

        this.knownSpells = new Spell[38];
        this.inventorySlots = new InventorySlot[20];
        this.mageCastsRemaining = new int[7];
        this.clericCastsRemaining = new int[7];

        weapon.rebuildEffects();
        armor.rebuildEffects();
        // ...and fold them into the player, or the gear is worn but not felt.
        rebuildEffects();
        initInventory();
        initSpells();
        initLearnedSpells();
        refreshSpellSlots();
    }

    private void initInventory() {
        for (int i = 0; i < 20; i++) {
            inventorySlots[i] = new InventorySlot();
        }
        // Starting supplies. A single potion was not enough to survive the first stretch of
        // beach: in scripted playthroughs a fresh character died repeatedly to ordinary
        // level-2 wanderers before reaching Moonhaven, with nothing to drink and no gold to
        // rest on. Three is a cushion, not a crutch — it is spent within the first dungeon.
        Item startPotion = ItemRegistry.getById("healing_potion");
        if (startPotion == null) startPotion = new Item("Healing Potion", Item.Type.POTION, 15);
        for (int i = 0; i < 3; i++) addItem(startPotion);   // stacks into one slot

        // Two rations, so the first time the food counter starts falling the player already
        // has the answer in the pack and knows to look for more of it on a shelf.
        Item startFood = ItemRegistry.getById("travel_rations");
        if (startFood != null) { addItem(startFood); addItem(startFood); }
    }

    private static Item defaultWeapon() {
        Item w = ItemRegistry.getById("rusty_dagger");
        return w != null ? w : new Item("Rusty Dagger", Item.Type.WEAPON, 4);
    }

    private static Item defaultArmor() {
        Item a = ItemRegistry.getById("leather_armor");
        return a != null ? a : new Item("Leather Armor", Item.Type.ARMOR, 2);
    }

    public void restoreAfterLoad() {
        // A save whose inventory array is short, absent, or has null holes in it. Gson writes
        // empty slots as objects, but it will faithfully restore a null if one is in the file,
        // and every pickup path calls slot.isEmpty() without a guard — so one null slot turned
        // opening a chest into an uncaught NPE on the event thread. Normalise here, next to the
        // other post-deserialization repairs, rather than null-checking at each of those sites.
        if (inventorySlots == null || inventorySlots.length < 20) {
            InventorySlot[] old = inventorySlots;
            inventorySlots = new InventorySlot[20];
            if (old != null) System.arraycopy(old, 0, inventorySlots, 0, old.length);
        }
        for (int i = 0; i < inventorySlots.length; i++) {
            if (inventorySlots[i] == null) inventorySlots[i] = new InventorySlot();
        }

        // Migrate old saves with smaller spell arrays (37 → 38)
        if (knownSpells.length < 38) {
            Spell[] old = knownSpells;
            knownSpells = new Spell[38];
            System.arraycopy(old, 0, knownSpells, 0, old.length);
        }
        if (spellLearned != null && spellLearned.length < 38) {
            boolean[] old = spellLearned;
            spellLearned = new boolean[38];
            System.arraycopy(old, 0, spellLearned, 0, old.length);
        }
        // Always reinitialize spell definitions (picks up renamed/rebalanced spells)
        initSpells();
        refreshSpellSlots();

        // Rebuild effects on all equipped items
        if (weapon != null) weapon.rebuildEffects();
        if (armor  != null) armor.rebuildEffects();
        if (ring1  != null) ring1.rebuildEffects();
        if (ring2  != null) ring2.rebuildEffects();
        if (helm   != null) helm.rebuildEffects();
        if (amulet != null) amulet.rebuildEffects();
        if (shield != null) shield.rebuildEffects();

        rebuildEffects();

        // Bring a character saved under an older experience curve onto the current one now,
        // rather than leaving the status panel quoting a number that changes the first time
        // they kill something.
        xpToNextLevel = xpNeededFor(level);
    }

    // ==================== SPELL LEARNING ====================

    /** Called for new characters: marks all Level-1 spells as learned. */
    private void initLearnedSpells() {
        if (spellLearned == null) spellLearned = new boolean[38];
        for (int i = 0; i < knownSpells.length; i++) {
            if (knownSpells[i] != null && knownSpells[i].getLevel() == 1)
                spellLearned[i] = true;
        }
    }

    /** Returns true if the given spell has been permanently learned. */
    public boolean isSpellLearned(Spell s) {
        if (s == null || spellLearned == null) return false;
        for (int i = 0; i < knownSpells.length && i < spellLearned.length; i++)
            if (knownSpells[i] == s) return spellLearned[i];
        return false;
    }

    /** Returns true if the spell with the given name has been permanently learned. */
    public boolean isSpellLearned(String name) {
        for (int i = 0; i < knownSpells.length; i++)
            if (knownSpells[i] != null && knownSpells[i].getName().equals(name))
                return spellLearned != null && spellLearned[i];
        return false;
    }

    /**
     * Takes off the piece in the given slot and returns it, or null if nothing was worn.
     * Weapon and armour fall back to the bare-handed defaults rather than null, because the
     * combat maths assumes both are always present.
     */
    public Item unequip(ItemSlot slot, boolean secondRing) {
        Item removed = null;
        switch (slot) {
            case WEAPON -> { removed = weapon; weapon = defaultWeapon(); if (removed == weapon) removed = null; }
            case ARMOR  -> { removed = armor;  armor  = defaultArmor();  if (removed == armor)  removed = null; }
            case SHIELD -> { removed = shield; shield = null; }
            case HELM   -> { removed = helm;   helm   = null; }
            case AMULET -> { removed = amulet; amulet = null; }
            case RING -> {
                if (secondRing) { removed = ring2; ring2 = null; }
                else            { removed = ring1; ring1 = null; }
            }
            default -> { return null; }
        }
        rebuildEffects();
        return removed;
    }

    /** Forgets every learned spell. Used before re-applying a saved set by name. */
    public void clearLearnedSpells() {
        spellLearned = new boolean[knownSpells.length];
    }

    /**
     * Replaces the learned set with exactly the named spells. Saves store names rather than
     * array positions, so inserting or reordering a spell can no longer shift a learned flag
     * onto a different spell.
     */
    public void setLearnedSpellNames(java.util.Collection<String> names) {
        clearLearnedSpells();
        if (names == null) return;
        for (String n : names) if (n != null && !n.isEmpty()) learnSpell(n);
    }

    /**
     * Teaches the named spell permanently. Returns true if newly learned,
     * false if already known or spell not found.
     */
    public boolean learnSpell(String name) {
        if (spellLearned == null) spellLearned = new boolean[38];
        for (int i = 0; i < knownSpells.length; i++) {
            if (knownSpells[i] != null && knownSpells[i].getName().equals(name)) {
                if (spellLearned[i]) return false;
                spellLearned[i] = true;
                return true;
            }
        }
        return false;
    }

    /**
     * Called by Retroquest.loadGame() after Gson deserialization.
     * Migrates old saves (null or all-false array) to level-1-only learned set.
     */
    public void restoreSpellLearning() {
        if (spellLearned == null) spellLearned = new boolean[38];
        boolean anyLearned = false;
        for (boolean b : spellLearned) if (b) { anyLearned = true; break; }
        if (!anyLearned) initLearnedSpells();
    }

    // ==================== DIVINE FAVOR API ====================

    /**
     * Ensures the favor array exists (null on old saves loaded from disk).
     * All 7 gods start at 50 (neutral) when first initialised.
     */
    private int[] favorArray() {
        if (favorScores == null) {
            favorScores = new int[7];
            // Starts at nothing: the gods learn who you are from what you do. The Cradle's
            // thresholds are the sums of the awards themselves, so any head start here
            // pushes every god to the 100 ceiling and flattens the endgame.
            java.util.Arrays.fill(favorScores, 0);
        }
        return favorScores;
    }

    /**
     * Returns the player's favor score with the given god (0–100).
     * Players who loaded an old save (no favor data) start from nothing, as a new one does.
     */
    public int getFavor(God god) {
        return favorArray()[god.index];
    }

    /**
     * Adjusts favor with the given god by {@code delta} (positive = gain, negative = loss).
     * The result is clamped to [0, 100].
     */
    public void addFavor(God god, int delta) {
        int[] arr = favorArray();
        arr[god.index] = Math.max(0, Math.min(100, arr[god.index] + delta));
    }

    /**
     * Returns the raw favor array for serialisation and UI rendering.
     * The array is guaranteed non-null after this call.
     */
    public int[] getFavorScores() { return favorArray().clone(); }

    // ==================== BOONS ====================

    /** Null-safe accessor — initialises an empty set on old saves. */
    private java.util.Set<String> boonSet() {
        if (acquiredBoons == null) acquiredBoons = new java.util.HashSet<>();
        return acquiredBoons;
    }

    /** Returns {@code true} if the player has already received this boon. */
    public boolean hasBoon(Boon boon) {
        return boonSet().contains(boon.id);
    }

    /**
     * Grants the boon permanently. Returns {@code false} (no-op) if already held.
     */
    public boolean grantBoon(Boon boon) {
        return boonSet().add(boon.id);
    }

    /**
     * Returns an unmodifiable snapshot of all acquired boon ids,
     * suitable for display in the UI.
     */
    public java.util.Set<String> getAcquiredBoons() {
        return java.util.Collections.unmodifiableSet(boonSet());
    }

    // ==================== INVENTORY API ====================

    public InventorySlot[] getInventorySlots() {
        return inventorySlots;
    }

    public boolean addItem(Item item) {
        if (item == null) return false;

        // First try to stack with existing items
        for (InventorySlot slot : inventorySlots) {
            if (!slot.isEmpty() &&
                slot.getItem().getId().equals(item.getId()) &&
                slot.getQuantity() < item.getMaxStackSize()) {
                slot.addQuantity(1);
                return true;
            }
        }

        // Find first empty slot
        for (int i = 0; i < inventorySlots.length; i++) {
            if (inventorySlots[i].isEmpty()) {
                int initialQty = (item.getType() == Item.Type.WAND && item.getCharges() > 0)
                        ? item.getCharges() : 1;
                inventorySlots[i] = new InventorySlot(item, initialQty);
                return true;
            }
        }

        return false; // inventory full
    }

    /** Adds item to inventory and auto-progresses any COLLECT or DELIVER quests for it. */
    public boolean addItemAndProgress(Item item) {
        if (addItem(item)) {
            progressQuest(Quest.Type.COLLECT, item.getName(), 1);
            for (Quest q : new ArrayList<>(activeQuests)) {
                if (q.getType() == Quest.Type.DELIVER
                        && q.getDeliverItemId() != null
                        && item.getId().equals(q.getDeliverItemId())
                        && !q.isComplete()) {
                    q.progress(1);
                    break;
                }
            }
            return true;
        }
        return false;
    }

    public void removeItem(int slotIndex) {
        if (slotIndex >= 0 && slotIndex < 20) {
            InventorySlot slot = inventorySlots[slotIndex];
            if (!slot.isEmpty()) {
                String removedId = slot.getItem().getId();
                for (Quest q : activeQuests) {
                    if (q.getType() == Quest.Type.DELIVER
                            && removedId != null
                            && removedId.equals(q.getDeliverItemId())
                            && !q.isComplete()) {
                        q.progress(-1);
                        break;
                    }
                }
            }
            inventorySlots[slotIndex] = new InventorySlot();
        }
    }

    // ==================== EQUIPPED ITEMS ====================

    public Item getWeapon() { return weapon; }
    public Item getArmor()  { return armor; }
    public Item getRing1()  { return ring1; }
    public Item getRing2()  { return ring2; }
    public Item getHelm()   { return helm; }
    public Item getAmulet() { return amulet; }
    public Item getShield() { return shield; }

    /**
     * Equips item and automatically returns the old item to inventory (if any).
     * Now works correctly for rings and future paper-doll swapping.
     */
    public void equip(Item item) {
        if (item == null) return;

        Item displacedItem = null;

        switch (item.getSlotType()) {
            case WEAPON -> { displacedItem = weapon; weapon = item; }
            case ARMOR  -> { displacedItem = armor;  armor  = item; }
            case HELM   -> { displacedItem = helm;   helm   = item; }
            case AMULET -> { displacedItem = amulet; amulet = item; }
            case SHIELD -> { displacedItem = shield; shield = item; }
            case RING   -> {
                if (ring1 == null) ring1 = item;
                else if (ring2 == null) ring2 = item;
                else { displacedItem = ring2; ring2 = item; }
            }
            default -> { addItem(item); return; }
        }

        if (displacedItem != null) {
            addItem(displacedItem);
        }

        item.rebuildEffects();   // ensure effect is attached
        rebuildEffects();
    }

    // ==================== EFFECTS ====================

    public void rebuildEffects() {
        activeEffects.clear();

        activeEffects.addAll(weapon.getEffects());
        activeEffects.addAll(armor.getEffects());
        if (ring1  != null) activeEffects.addAll(ring1.getEffects());
        if (ring2  != null) activeEffects.addAll(ring2.getEffects());
        if (helm   != null) activeEffects.addAll(helm.getEffects());
        if (amulet != null) activeEffects.addAll(amulet.getEffects());
        if (shield != null) activeEffects.addAll(shield.getEffects());
    }



    public List<Effect> getActiveEffects() {
        return new ArrayList<>(activeEffects);
    }

    public void applyEffect(Effect effect) {
        effect.onApply(this);
        if (!effect.isPermanent()) activeEffects.add(effect);
    }

    private void tickEffects() {
        for (Effect e : activeEffects) {
            e.onMove(this);
            e.tick(this);
        }

        activeEffects.removeIf(effect ->
            !effect.isPermanent() && effect.getTurnsRemaining() <= 0);
    }

    // ==================== SPELL BUFF API ====================

    // Appliers — called from CombatOverlay when spells are cast
    public void applyShield()       { shieldSteps    = SHIELD_DURATION; }
    public void applyHaste()        { hasteSteps     = HASTE_DURATION; }
    public void applyPrayer()       { prayerSteps    = PRAYER_DURATION; }
    public void applyHolyArmor()    { holyArmorSteps = HOLY_ARMOR_DURATION; }
    public void applyElemResist()   { elemResSteps   = ELEM_RESIST_DURATION; }
    public void applyInvisibility() { invisSteps     = INVIS_DURATION; }
    public void applyProtEv()       { protEvSteps    = PROT_EV_DURATION; }
    public void applyBless()        { blessSteps     = BLESS_DURATION; }
    public void clearInvisibility() { invisSteps     = 0; }

    public void clearSpellBuffs() {
        shieldSteps = 0; hasteSteps = 0; prayerSteps = 0; holyArmorSteps = 0;
        elemResSteps = 0; invisSteps = 0; protEvSteps = 0; blessSteps = 0;
    }

    // Boolean getters — active if steps > 0
    public boolean hasShield()     { return shieldSteps > 0; }
    public boolean isHasted()      { return hasteSteps > 0; }
    public boolean hasPrayer()     { return prayerSteps > 0; }
    public boolean hasHolyArmor()  { return holyArmorSteps > 0; }
    public boolean hasElemResist() { return elemResSteps > 0; }
    public boolean isInvisible()   { return invisSteps > 0; }
    public boolean hasProtEv()     { return protEvSteps > 0; }
    public boolean hasBlessed()    { return blessSteps > 0; }

    // Detect Magic — time-based flag (8 seconds)
    public void    activateDetectMagic()  { detectMagicExpiry = System.currentTimeMillis() + 8_000; }
    public boolean isDetectMagicActive()  { return System.currentTimeMillis() < detectMagicExpiry; }

    // Resurrection Ward — pre-cast ward that triggers on death
    public void chargeResurrection()       { resurrectionCharged = true; }
    public boolean isResurrectionCharged() { return resurrectionCharged; }
    /** If a ward is charged, consume it, heal to 50% max HP, return true. */
    public boolean triggerResurrection() {
        if (!resurrectionCharged) return false;
        resurrectionCharged = false;
        hp = Math.max(1, maxHp / 2);
        return true;
    }

    // Numeric bonus getters — AC wards take the best, hit/damage are single-source
    /** Best active AC ward — the four wards overlap rather than stack (max +5). */
    public int getAcBonus()  {
        int best = 0;
        if (shieldSteps    > 0) best = Math.max(best, SHIELD_AC_BONUS);
        if (protEvSteps    > 0) best = Math.max(best, PROT_EV_AC_BONUS);
        if (prayerSteps    > 0) best = Math.max(best, PRAYER_AC_BONUS);
        if (holyArmorSteps > 0) best = Math.max(best, HOLY_ARMOR_AC_BONUS);
        return best;
    }
    public int getHitBonus() { return prayerSteps>0 ? PRAYER_HIT_BONUS : 0; }
    /** Bless scales with level; a flat +2 stopped mattering the moment a swing did 20+. */
    public int getDmgBonus() { return blessSteps>0 ? BLESS_DMG_BONUS + level / 5 : 0; }

    /** Returns a human-readable summary of active spell buffs, or null if none. */
    public String getActiveBuffSummary() {
        StringBuilder sb = new StringBuilder();
        if (shieldSteps > 0)    sb.append("Shield(").append(shieldSteps).append(") ");
        if (hasteSteps > 0)     sb.append("Haste(").append(hasteSteps).append(") ");
        if (prayerSteps > 0)    sb.append("Prayer(").append(prayerSteps).append(") ");
        if (holyArmorSteps > 0) sb.append("Holy Armor(").append(holyArmorSteps).append(") ");
        if (elemResSteps > 0)   sb.append("Resist Elements(").append(elemResSteps).append(") ");
        if (invisSteps > 0)     sb.append("Invisibility(").append(invisSteps).append(") ");
        if (protEvSteps > 0)    sb.append("Protection(").append(protEvSteps).append(") ");
        if (blessSteps > 0)     sb.append("Bless(").append(blessSteps).append(") ");
        if (drunkSteps > 0)     sb.append("Drunk(").append(drunkSteps).append(") ");
        return sb.length() > 0 ? sb.toString().trim() : null;
    }

    // Step counts — for display in StatsPanel
    public int shieldStepsLeft()    { return shieldSteps; }
    public int hasteStepsLeft()     { return hasteSteps; }
    public int prayerStepsLeft()    { return prayerSteps; }
    public int holyArmorStepsLeft() { return holyArmorSteps; }
    public int elemResStepsLeft()   { return elemResSteps; }
    public int invisStepsLeft()     { return invisSteps; }
    public int protEvStepsLeft()    { return protEvSteps; }
    public int blessStepsLeft()     { return blessSteps; }

    // Drunk effect (from ale — not a spell buff, persists through combat)
    public void addDrunkSteps(int steps) { drunkSteps += steps; }
    public boolean isDrunk()             { return drunkSteps > 20; }
    public int drunkStepsLeft()          { return drunkSteps; }

    // Poison effect (from monster attacks — persists after combat, cured by Antidote/Restoration)
    public void applyPoison(int steps)   { poisonSteps = Math.max(poisonSteps, steps); }
    public void curePoison()             { poisonSteps = 0; }
    public boolean isPoisoned()          { return poisonSteps > 0; }
    public int poisonStepsLeft()         { return poisonSteps; }

    // ==================== DIALOGUE FLAGS ====================

    public Map<String, String> getFlags() {
        if (flags == null) flags = new HashMap<>();
        return java.util.Collections.unmodifiableMap(flags);
    }

    /** Package-private mutable access for migration utilities like {@link TileStateManager}. */
    Map<String, String> getMutableFlags() {
        if (flags == null) flags = new HashMap<>();
        return flags;
    }

    public String getFlag(String key) {
        return flags != null ? flags.get(key) : null;
    }

    public void setFlag(String key, String value) {
        if (flags == null) flags = new HashMap<>();
        flags.put(key, value);
    }

    public void clearFlag(String key) {
        if (flags != null) flags.remove(key);
    }

    public boolean hasFlag(String key) {
        return flags != null && flags.containsKey(key);
    }

    // ── Key Ring API (permanent, no inventory slots) ──

    private List<String> keyRing() {
        if (keyRing == null) keyRing = new ArrayList<>();
        return keyRing;
    }

    /** Adds a key to the permanent key ring. Returns false if already held. */
    public boolean addKey(String keyId) {
        if (keyId == null || keyId.isEmpty()) return false;
        if (keyRing().contains(keyId)) return false;
        keyRing().add(keyId);
        return true;
    }

    /** Checks if a key is on the key ring. */
    public boolean hasKey(String keyId) {
        return keyId != null && keyRing().contains(keyId);
    }

    /** Returns an unmodifiable view of the key ring. */
    public List<String> getKeyRing() {
        return java.util.Collections.unmodifiableList(keyRing());
    }

    // MOVEMENT
    public boolean move(int dx, int dy, char[][] map) {
        int newX = x + dx;
        int newY = y + dy;

        if (newX >= 0 && newX < map[0].length &&
            newY >= 0 && newY < map.length &&
            isWalkable(map[newY][newX])) {

            x = newX;
            y = newY;
            stepTaken();
            return true;
        }
        return false;
    }

    /**
     * Per-step bookkeeping: rations burned, item effects ticked, spell durations counted
     * down. Invisibility is combat-scoped and is deliberately not ticked here.
     *
     * <p>Public because first-person dungeon movement does not go through {@link #move}:
     * it validates against the wall bitmasks and calls {@link #setPosition} directly. That
     * skipped all of this on six of the seven islands — so underground, where nearly all
     * the game's fighting happens, Bless and Shield and Haste never ran out, poison never
     * wore off, and a Ring of Regeneration did nothing at all.
     */
    public void stepTaken() {
        food = Math.max(0, food - 1);

        tickEffects();           // triggers onMove() on every equipped effect

        if (shieldSteps    > 0) shieldSteps--;
        if (hasteSteps     > 0) hasteSteps--;
        if (prayerSteps    > 0) prayerSteps--;
        if (holyArmorSteps > 0) holyArmorSteps--;
        if (elemResSteps   > 0) elemResSteps--;
        if (protEvSteps    > 0) protEvSteps--;
        if (blessSteps     > 0) blessSteps--;
        if (drunkSteps     > 0) drunkSteps--;
        if (poisonSteps    > 0) poisonSteps--;
    }

    private boolean isWalkable(char tile) {
        return TileRegistry.getByIdSafe(tile).isWalkable();
    }

    /** Melee damage: {@code (STR-10) + level/3 + weapon and boon bonuses}. */
    public int getDamage() {
        int dmgBonus = 0;
        for (Effect e : activeEffects) dmgBonus += e.modifyDamage();
        for (Boon b : Boon.values()) if (hasBoon(b)) dmgBonus += b.damageBonus;
        return dmgBonus + (str - 10) + level / DAMAGE_LEVEL_DIVISOR;
    }

    /**
     * Armour class. The DEX term is uncapped; the gear + boon contribution is capped at
     * {@code level + NON_DEX_AC_CAP_BASE} so stacked equipment cannot outrun the
     * {@code d20 + monsterLevel} to-hit roll and make the player unhittable.
     */
    public int getAC() {
        return 10 + (dex - 10) / 2 + Math.min(rawProtection(), level + NON_DEX_AC_CAP_BASE);
    }

    /** Protection from worn gear and boons, before the AC cap is applied. */
    private int rawProtection() {
        int nonDex = 0;
        for (Effect e : activeEffects) nonDex += e.modifyAC(this);
        for (Boon b : Boon.values()) if (hasBoon(b)) nonDex += b.acBonus;
        return nonDex;
    }

    /** Protection the AC cap refuses; see {@link #getDamageReduction()}. */
    public int getSurplusProtection() {
        return Math.max(0, rawProtection() - (level + NON_DEX_AC_CAP_BASE));
    }

    /**
     * Flat reduction on every blow that lands.
     *
     * <p>The second term keeps armour worth buying. The AC cap deliberately stops gear from
     * outrunning the {@code d20 + monsterLevel} to-hit roll, but it binds hardest in the
     * early game — plate, an iron shield and a helm come to thirteen points at a level
     * where only eight of them count — so every upgrade from the second island on was gold
     * spent on a number nothing read. What will not make you harder to hit now makes the
     * hits hurt less, which is what heavier armour ought to do anyway.
     */
    public int getDamageReduction() {
        return Math.max(0, (con - 10) / 3) + getSurplusProtection() / 4;
    }

    /** Total spell resistance from WIS base + equipped item bonuses + boons, capped at 50%. */
    public int getTotalSpellResist() {
        int wisBase = Math.max(0, (wisdom - 10) / 2 * 5);
        int itemBonus = 0;
        for (Effect e : activeEffects) itemBonus += e.modifySpellResist();
        for (Boon b : Boon.values()) if (hasBoon(b)) itemBonus += b.spellResistBonus;
        return Math.min(50, wisBase + itemBonus);
    }

    public void takeDamage(int dmg) {
        int reduction = getDamageReduction();
        int finalDmg = Math.max(1, dmg - reduction);
        hp = Math.max(0, hp - finalDmg);
    }

    public void drainXP(int amount) {
        xp = Math.max(0, xp - amount);
    }

    /** Result details from one or more level-ups triggered by XP gain. */
    public record LevelUpResult(int levelsGained, int totalHpGained, int newLevel,
                                int oldMaxSpellLevel, int newMaxSpellLevel) {
        public boolean unlockedNewSpellTier() { return newMaxSpellLevel > oldMaxSpellLevel; }
    }

    /**
     * Adds XP and processes any resulting level-ups.
     * @return a {@link LevelUpResult} if at least one level was gained, otherwise {@code null}
     */
    public LevelUpResult addXP(int amount) {
        // Recompute first so a character rolled or saved under an older curve is brought
        // onto the current one the moment it earns anything.
        xpToNextLevel = xpNeededFor(level);
        xp += amount;
        if (xp < xpToNextLevel) return null;

        int oldMaxSpellLevel = Math.min(6, level);
        int startLevel = level;
        int hpBefore = maxHp;

        while (xp >= xpToNextLevel) {
            xp -= xpToNextLevel;
            levelUp();
        }

        return new LevelUpResult(
                level - startLevel,
                maxHp - hpBefore,
                level,
                oldMaxSpellLevel,
                Math.min(6, level));
    }

    /**
     * Experience to carry a character from {@code level} to the next one.
     *
     * <p>A flat {@code level × 900} charged a beginner the same 900 points for level 2 that
     * a tenth-level character pays per level, while the only monsters a beginner can face
     * are worth 30 — thirty kills for the first level, and closer to a hundred and seventy
     * to reach the level the second island opens at. Dividing by {@code level + 4} bends
     * the curve down where the rewards are smallest and leaves it near the old numbers once
     * monster values have caught up, which holds every level at roughly a dozen fights.
     */
    public static int xpNeededFor(int level) {
        int l = Math.max(1, level);
        return (int) (900L * l * l / (l + 4));
    }

    private void levelUp() {
        level++;
        int hpGain = 3 + (con - 10) / 3;
        maxHp += hpGain;
        hp = maxHp;   // use raw field, not getMaxHp() which includes boon bonuses
        xpToNextLevel = xpNeededFor(level);
        refreshSpellSlots();
    }

    public void heal(int amount) {
        hp = Math.min(getMaxHp(), hp + amount);
    }

    /**
     * Adds (or, with a negative amount, spends) gold. Clamped at zero: two inns charge
     * through a dialogue action that never checked the balance, so resting broke on a purse
     * that could not cover it and left the player owing money to no one.
     */
    public void addGold(int amount) { gold = Math.max(0, gold + amount); }
    public void addFood(int amount) { food = Math.min(999, food + amount); }

    // ── Gambling limits ─────────────────────────────────────────────────────
    public int getMaxBet() { return level * 50; }
    public int getGamblingWinningsCap() { return level * 100; }
    public int getGamblingNetWinnings() { return gamblingNetWinnings; }
    public void recordGamblingResult(int netAmount) { gamblingNetWinnings += netAmount; }
    public void resetGamblingWinnings() { gamblingNetWinnings = 0; }
    public boolean isGamblingCapped() { return gamblingNetWinnings >= getGamblingWinningsCap(); }


    // ==================== SPELLS ====================

    private void initSpells() {
        int idx = 0;

        // LEVEL 1
        knownSpells[idx++] = new Spell("Magic Missile", Spell.Type.MAGE, 1, 12, Spell.Usage.COMBAT,
                "hits for ", "Unerring darts of arcane force streak toward the target. Always hits — no magical resistance can deflect them.");
        knownSpells[idx++] = new Spell("Sleep", Spell.Type.MAGE, 1, 0, Spell.Usage.COMBAT,
                "puts monster to sleep", "Waves of drowsiness wash over the target. The creature falls asleep for several rounds, unable to act. Duration scales with your level.");
        knownSpells[idx++] = new Spell("Charm Monster", Spell.Type.MAGE, 1, 0, Spell.Usage.COMBAT,
                "monster may flee", "Weaves an enchantment that bends the creature's will. Chance to flee scales with your level (40% base, +3% per level, max 70%). May instead idle if charm partially takes hold.");
        knownSpells[idx++] = new Spell("Cure Light Wounds", Spell.Type.CLERIC, 1, 18, Spell.Usage.BOTH,
                "heals ", "Warm golden light flows from your hands, mending minor wounds. Usable in combat or on the map.");
        knownSpells[idx++] = new Spell("Protection from Evil", Spell.Type.CLERIC, 1, 0, Spell.Usage.COMBAT,
                "+3 AC for a while", "A circle of sacred light surrounds you, turning aside the blows of the wicked. It holds for a stretch of travel, not just this fight.");
        knownSpells[idx++] = new Spell("Shield", Spell.Type.MAGE, 1, 0, Spell.Usage.COMBAT,
                "+2 AC for a while", "Conjures a shimmering barrier of force that deflects blows. It holds for a stretch of travel, not just this fight.");

        // LEVEL 2
        knownSpells[idx++] = new Spell("Fireball", Spell.Type.MAGE, 2, 18, Spell.Usage.COMBAT,
                "explodes for ", "A roaring sphere of flame erupts from your fingertips and detonates on impact. The classic destruction spell.");
        knownSpells[idx++] = new Spell("Lightning Bolt", Spell.Type.MAGE, 2, 20, Spell.Usage.COMBAT,
                "strikes for ", "A searing bolt of electricity that pierces magical wards. Ignores half of the target's spell resistance.");
        knownSpells[idx++] = new Spell("Invisibility", Spell.Type.MAGE, 2, 0, Spell.Usage.COMBAT,
                "next attack auto-hits, harder", "Light bends around you until you are utterly unseen. Your next attack cannot miss and strikes for half again its damage.");
        knownSpells[idx++] = new Spell("Cure Serious Wounds", Spell.Type.CLERIC, 2, 25, Spell.Usage.BOTH,
                "heals ", "Deep wounds knit together under a surge of divine power. Restores a good amount of health.");
        knownSpells[idx++] = new Spell("Bless", Spell.Type.CLERIC, 2, 0, Spell.Usage.COMBAT,
                "+damage for a while", "A blessing of righteous fury — your strikes hit harder, and harder still as you grow (+2 damage, and +1 more every 5 levels). Stacks with Prayer.");
        knownSpells[idx++] = new Spell("Turn Undead", Spell.Type.CLERIC, 2, 0, Spell.Usage.COMBAT,
                "destroys or repels undead", "Holy radiance scours the corruption from undead flesh. 70% chance to instantly destroy undead; deals double damage on failure. Less effective against the living.");

        // LEVEL 3
        knownSpells[idx++] = new Spell("Ice Storm", Spell.Type.MAGE, 3, 28, Spell.Usage.COMBAT,
                "freezes for ", "A howling storm of ice and hail. Deals damage and has a 30% chance to slow the target, causing it to lose its next turn.");
        knownSpells[idx++] = new Spell("Dispel Magic", Spell.Type.MAGE, 3, 0, Spell.Usage.COMBAT,
                "shatters magical wards", "Tears away the target's magical defenses — halves their spell resistance for the rest of combat. Also purges any negative effects (poison, sleep, stun, blind) from you. Cast before your big damage spells to ensure they land.");
        knownSpells[idx++] = new Spell("Teleport", Spell.Type.MAGE, 3, 0, Spell.Usage.MAP,
                "return to town instantly", "Space folds around you and deposits you at the gates of the last town you visited. Essential for escaping the depths.");
        knownSpells[idx++] = new Spell("Cure Critical Wounds", Spell.Type.CLERIC, 3, 40, Spell.Usage.BOTH,
                "heals ", "A torrent of divine energy floods through you, mending even grievous injuries. The most powerful targeted heal before full restoration.");
        knownSpells[idx++] = new Spell("Prayer", Spell.Type.CLERIC, 3, 0, Spell.Usage.COMBAT,
                "+2 AC & to-hit for a while", "You beseech the gods for aid in battle. Grants +2 AC and +2 to-hit, holding for a stretch of travel. Only your strongest ward counts — this does not stack with Shield, Protection or Holy Armor — but it does stack with Bless.");
        knownSpells[idx++] = new Spell("Holy Word", Spell.Type.CLERIC, 3, 0, Spell.Usage.COMBAT,
                "damages evil creatures", "You speak a word of divine power that sears creatures of darkness. Deals double damage to undead and demons; normal damage to all others.");

        // LEVEL 4
        knownSpells[idx++] = new Spell("Cone of Cold", Spell.Type.MAGE, 4, 35, Spell.Usage.COMBAT,
                "freezes for ", "A blast of absolute zero that cannot be resisted by any magical ward. Moderate damage but guaranteed to hit.");
        knownSpells[idx++] = new Spell("Cloudkill", Spell.Type.MAGE, 4, 0, Spell.Usage.COMBAT,
                "poisons the monster", "A billowing cloud of sickly green death engulfs the target. Poisons the creature for 4-5 rounds, and the venom bites harder as you grow in power.");
        knownSpells[idx++] = new Spell("Haste", Spell.Type.MAGE, 4, 0, Spell.Usage.COMBAT,
                "a second strike each round", "Time warps around you — your blade moves with impossible speed. Every swing is followed by a second, and that extra blow can land a critical of its own.");
        knownSpells[idx++] = new Spell("Heal", Spell.Type.CLERIC, 4, 70, Spell.Usage.BOTH,
                "fully heals", "The purest divine magic floods every fiber of your being. Fully restores your health in a single miraculous instant.");
        knownSpells[idx++] = new Spell("Resist Elements", Spell.Type.CLERIC, 4, 0, Spell.Usage.COMBAT,
                "halves spell damage for a while", "Wraps you in a ward of elemental protection. All incoming spell and breath damage is halved, holding for a stretch of travel.");
        knownSpells[idx++] = new Spell("Restoration", Spell.Type.CLERIC, 4, 40, Spell.Usage.BOTH,
                "cleanses and heals", "Purifies body and spirit — removes all negative status effects (poison, sleep, stun, blind) and restores at least half your maximum health.");

        // LEVEL 5
        knownSpells[idx++] = new Spell("Chain Lightning", Spell.Type.MAGE, 5, 45, Spell.Usage.COMBAT,
                "strikes for ", "An arc of living lightning that leaps through magical barriers. Ignores half of the target's spell resistance. Higher damage than Lightning Bolt.");
        knownSpells[idx++] = new Spell("Death Spell", Spell.Type.MAGE, 5, 0, Spell.Usage.COMBAT,
                "instant kill chance", "Dark magic that rips the life force from the target. More effective against weaker foes — kill chance scales with your level advantage. Deals damage on failure.");
        knownSpells[idx++] = new Spell("Scry", Spell.Type.MAGE, 5, 0, Spell.Usage.MAP,
                "reveals the dungeon map", "Your mind's eye expands beyond mortal sight. In dungeons, reveals the entire layout of the current level. On the overworld, this spell has no effect.");
        knownSpells[idx++] = new Spell("Holy Armor", Spell.Type.CLERIC, 5, 0, Spell.Usage.COMBAT,
                "+5 AC for a while", "Plates of shimmering golden light materialize around you, turning aside even the most vicious blows. The strongest ward you can raise — wards do not stack, so this simply replaces a lesser one.");
        knownSpells[idx++] = new Spell("Flame Strike", Spell.Type.CLERIC, 5, 50, Spell.Usage.COMBAT,
                "burns for ", "A pillar of sacred fire descends from the heavens, incinerating the target. The cleric's most devastating offensive prayer.");

        // LEVEL 6
        knownSpells[idx++] = new Spell("Meteor Swarm", Spell.Type.MAGE, 6, 60, Spell.Usage.COMBAT,
                "devastates for ", "You rip stones from the fabric of reality itself and hurl them at the target. The mage's ultimate destructive spell.");
        knownSpells[idx++] = new Spell("Power Word Kill", Spell.Type.MAGE, 6, 0, Spell.Usage.COMBAT,
                "instant kill", "You speak a single word of absolute annihilation. Instantly destroys any creature whose remaining vitality is below your own maximum health. Deals damage otherwise, and such a death yields only a fraction of the usual spoils.");
        knownSpells[idx++] = new Spell("Wish", Spell.Type.MAGE, 6, 60, Spell.Usage.BOTH,
                "devastating power", "Reality bends to your will. In combat: either fully restores your health or unleashes triple-power devastation on the target (fate decides). On the map: fully heals you.");
        knownSpells[idx++] = new Spell("Time Stop", Spell.Type.MAGE, 6, 0, Spell.Usage.COMBAT,
                "monster skips its turn", "You tear a hole in the flow of time. The world freezes around you — the monster cannot act this round.");
        knownSpells[idx++] = new Spell("Divine Intervention", Spell.Type.CLERIC, 6, 0, Spell.Usage.COMBAT,
                "monster flees or dies", "You cry out to the gods and they answer. 60% chance of devastating divine wrath; 40% chance the creature flees in absolute terror. The ultimate clerical invocation.");
        knownSpells[idx++] = new Spell("Sanctuary", Spell.Type.CLERIC, 6, 0, Spell.Usage.COMBAT,
                "guaranteed safe escape", "Invokes divine protection so absolute that even the fiercest foe cannot pursue. Guarantees escape from combat — but you forfeit all experience and gold from the encounter.");
        knownSpells[idx++] = new Spell("Resurrection", Spell.Type.CLERIC, 6, 0, Spell.Usage.MAP,
                "arms a resurrection ward",
                "You weave a covenant with death itself. If slain in combat, the ward triggers — you rise at 50% HP instead of dying. The ward persists until used.");

        // Quest-reward spell — Sylvandar: rootspeaker_entangle
        knownSpells[idx++] = new Spell("Entangle", Spell.Type.MAGE, 2, 14, Spell.Usage.COMBAT,
                "roots erupt and bind", "Living roots erupt from the ground, crushing and binding the target. Deals half damage and immobilizes the creature for several rounds. Learned from Rootspeaker Thenna.");
    }

    public void refreshSpellSlots() {
        int maxSpellLevel = Math.min(6, level);

        int mageBonus   = Math.max(1, (intelligence - 10) / 2 + level / 3);
        int clericBonus = Math.max(1, (wisdom - 10) / 2 + level / 3);

        for (int l = 1; l <= 6; l++) {
            if (l > maxSpellLevel) {
                mageCastsRemaining[l] = 0;
                clericCastsRemaining[l] = 0;
            } else {
                int multiplier = (maxSpellLevel - l + 3);
                mageCastsRemaining[l]   = Math.min(9, mageBonus   * multiplier / 2 + 1);
                clericCastsRemaining[l] = Math.min(9, clericBonus * multiplier / 2 + 1);
            }
        }
    }

    public boolean canCast(Spell spell) {
        int[] casts = (spell.getType() == Spell.Type.MAGE) ? mageCastsRemaining : clericCastsRemaining;
        return casts[spell.getLevel()] > 0;
    }

    public void castSpell(Spell spell) {
        int[] casts = (spell.getType() == Spell.Type.MAGE) ? mageCastsRemaining : clericCastsRemaining;
        if (casts[spell.getLevel()] > 0) casts[spell.getLevel()]--;
    }

    public Spell[] getKnownSpells() { return knownSpells.clone(); }
    public int[] getMageCasts()     { return mageCastsRemaining.clone(); }
    public int[] getClericCasts()   { return clericCastsRemaining.clone(); }

    public void addBonusCastSlot() {
        // Grant +1 cast at the highest spell level that still has remaining casts
        for (int lv = Math.min(getLevel(), 6); lv >= 1; lv--) {
            if (mageCastsRemaining[lv] > 0 || clericCastsRemaining[lv] > 0) {
                if (mageCastsRemaining[lv] > 0) mageCastsRemaining[lv] = Math.min(9, mageCastsRemaining[lv] + 1);
                if (clericCastsRemaining[lv] > 0) clericCastsRemaining[lv] = Math.min(9, clericCastsRemaining[lv] + 1);
                return;
            }
        }
        // If all depleted, restore one level-1 slot each
        mageCastsRemaining[1] = Math.max(mageCastsRemaining[1], 1);
        clericCastsRemaining[1] = Math.max(clericCastsRemaining[1], 1);
    }

    // ==================== QUESTS ====================


    public void addQuest(Quest quest) {
        if (!activeQuests.contains(quest)) {
            activeQuests.add(quest);
            quest.setStatus(Quest.Status.IN_PROGRESS);
        }
    }

    /**
     * General progress method - call from anywhere (combat, talk, collect, etc.)
     */
    public void progressQuest(Quest.Type qType, String target, int amount) {
        for (Quest q : new ArrayList<>(activeQuests)) {   // copy to avoid concurrent modification
            if (q.getType() == qType &&
                    (q.getTarget().equalsIgnoreCase(target) ||
                     (qType == Quest.Type.KILL && q.getTarget().equalsIgnoreCase("any")))) {
                q.progress(amount);
                if (q.isComplete()) {
                    completeQuest(q);
                }
                return;
            }
        }
    }

    public void completeQuest(Quest q) {
        q.setStatus(Quest.Status.COMPLETED);
        activeQuests.remove(q);
        completedQuests.add(q);

        addGold(q.getGoldReward());
        LevelUpResult lvl = addXP(q.getXpReward());

        // Item reward support (works with new Quest.java)
        String itemMsg = "";
        Item rewardItem = q.getItemReward();
        if (rewardItem == null && q.getItemRewardId() != null && !q.getItemRewardId().isEmpty()) {
            rewardItem = ItemRegistry.getById(q.getItemRewardId());
        }
        if (rewardItem != null) {
            if (rewardItem.getType() == Item.Type.KEY) {
                addKey(rewardItem.getId());
            } else if (!addItem(rewardItem)) {
                itemMsg = " (inventory full — " + rewardItem.getName() + " lost!)";
            }
        }

        String spellMsg = "";
        if (q.getSpellRewardId() != null && !q.getSpellRewardId().isEmpty()) {
            if (learnSpell(q.getSpellRewardId())) {
                spellMsg = "\nYou learn the spell: " + q.getSpellRewardId() + "!";
            }
        }

        // Divine favor awards tied to specific quests
        String favorMsg = "";

        // Island 1 — Lirandel
        if ("moonbloom_collection".equals(q.getId()))   { addFavor(God.LIRANDEL, 10); favorMsg = " (+10 Lirandel favor)"; }
        if ("purge_dreamwake".equals(q.getId()))         { addFavor(God.LIRANDEL, 10); favorMsg = " (+10 Lirandel favor)"; }
        if ("temple_purification".equals(q.getId()))     { addFavor(God.LIRANDEL, 5);  favorMsg = " (+5 Lirandel favor)"; }
        if ("waterfall_haunting".equals(q.getId()))      { addFavor(God.LIRANDEL, 5);  favorMsg = " (+5 Lirandel favor)"; }

        // Island 2 — Pyralis
        if ("pyralis_crucible_trial".equals(q.getId()))  {
            addFavor(God.PYRALIS, 20); favorMsg = " (+20 Pyralis favor)";
            if (grantBoon(Boon.PYRALIS_TEMPER)) {
                favorMsg += " Pyralis's fire ignites within you! (+2 attack damage)";
            }
        }
        if ("shard_of_ambition".equals(q.getId()))       { addFavor(God.PYRALIS, 10); favorMsg = " (+10 Pyralis favor)"; }
        if ("the_missing_forger".equals(q.getId()))      { addFavor(God.PYRALIS, 10); favorMsg = " (+10 Pyralis favor)"; }
        if ("echoes_of_the_shard".equals(q.getId()))     { addFavor(God.PYRALIS, 5);  favorMsg = " (+5 Pyralis favor)"; }
        if ("slag_beast_bounty".equals(q.getId()))       { addFavor(God.PYRALIS, 5);  favorMsg = " (+5 Pyralis favor)"; }
        if ("forge_initiation".equals(q.getId()))        { addFavor(God.PYRALIS, 10); favorMsg = " (+10 Pyralis favor)"; }

        // Island 3 — Zephyrion
        if ("zephyrion_trial".equals(q.getId()))         {
            addFavor(God.ZEPHYRION, 20); favorMsg = " (+20 Zephyrion favor)";
            if (grantBoon(Boon.ZEPHYRION_GRACE)) {
                favorMsg += " Zephyrion's wind shields you! (+1 AC)";
            }
        }
        if ("windchime_restoration".equals(q.getId()))   { addFavor(God.ZEPHYRION, 10); favorMsg = " (+10 Zephyrion favor)"; }
        if ("sky_pirate_contact".equals(q.getId()))      { addFavor(God.ZEPHYRION, 10); favorMsg = " (+10 Zephyrion favor)"; }
        if ("storm_hawk_hunt".equals(q.getId()))         { addFavor(God.ZEPHYRION, 5);  favorMsg = " (+5 Zephyrion favor)"; }
        if ("stormspire_trial_reva".equals(q.getId()))   { addFavor(God.ZEPHYRION, 15); favorMsg = " (+15 Zephyrion favor)"; }
        if ("stormspire_trial_milo".equals(q.getId()))   { addFavor(God.ZEPHYRION, 15); favorMsg = " (+15 Zephyrion favor)"; }
        if ("weathervane_prophecy".equals(q.getId()))     { addFavor(God.ZEPHYRION, 5);  favorMsg = " (+5 Zephyrion favor)"; }

        // Island 4 — Sylvandar
        if ("the_spreading_bark".equals(q.getId()))      { addFavor(God.SYLVANDAR, 10); favorMsg = " (+10 Sylvandar favor)"; }
        if ("amber_sage_memory".equals(q.getId()))        { addFavor(God.SYLVANDAR, 10); favorMsg = " (+10 Sylvandar favor)"; }
        if ("the_necessary_rot".equals(q.getId()))        { addFavor(God.SYLVANDAR, 15); favorMsg = " (+15 Sylvandar favor)"; }
        if ("sylvandar_verdant_trial".equals(q.getId()))  {
            addFavor(God.SYLVANDAR, 20); favorMsg = " (+20 Sylvandar favor)";
            if (grantBoon(Boon.SYLVANDAR_ROOTS)) {
                favorMsg += " Sylvandar's roots grow beneath your skin! (+5 max HP, +1 AC)";
            }
        }
        if ("the_ledger_of_cuttings".equals(q.getId()))   { addFavor(God.SYLVANDAR, 10); favorMsg = " (+10 Sylvandar favor)"; }
        if ("the_child_who_is_becoming".equals(q.getId())){ addFavor(God.SYLVANDAR, 5);  favorMsg = " (+5 Sylvandar favor)"; }
        if ("the_grief_orchard".equals(q.getId()))        { addFavor(God.SYLVANDAR, 5);  favorMsg = " (+5 Sylvandar favor)"; }
        if ("the_ravine_closes".equals(q.getId()))        { addFavor(God.SYLVANDAR, 10); favorMsg = " (+10 Sylvandar favor)"; }
        if ("vine_strangler_bounty".equals(q.getId()))    { addFavor(God.SYLVANDAR, 5);  favorMsg = " (+5 Sylvandar favor)"; }

        // Island 5 — Thalorax
        if ("the_deep_reading".equals(q.getId()))        { addFavor(God.THALORAX, 10); favorMsg = " (+10 Thalorax favor)"; }
        if ("cracking_dome".equals(q.getId()))            { addFavor(God.THALORAX, 5);  favorMsg = " (+5 Thalorax favor)"; }
        if ("abyssal_wraith_bounty".equals(q.getId()))   { addFavor(God.THALORAX, 5);  favorMsg = " (+5 Thalorax favor)"; }
        if ("thalorax_trial".equals(q.getId()))          {
            addFavor(God.THALORAX, 20); favorMsg = " (+20 Thalorax favor)";
            if (grantBoon(Boon.TIDAL_ENDURANCE)) {
                favorMsg += " The deep\u2019s weight settles into you.";
            }
        }
        if ("the_ninth_ward".equals(q.getId()))          { addFavor(God.THALORAX, 10); favorMsg = " (+10 Thalorax favor)"; }
        if ("the_pressed_woman".equals(q.getId()))       { addFavor(God.THALORAX, 10); addFavor(God.UMBRYN, 5); favorMsg = " (+10 Thalorax, +5 Umbryn favor)"; }
        if ("the_leviathans_jaw".equals(q.getId()))      { addFavor(God.THALORAX, 5);  addFavor(God.UMBRYN, 5); favorMsg = " (+5 Thalorax, +5 Umbryn favor)"; }
        if ("brightcorals_first_stone".equals(q.getId())){ addFavor(God.THALORAX, 5);  addFavor(God.LIRANDEL, 5); favorMsg = " (+5 Thalorax, +5 Lirandel favor)"; }
        if ("varsa_choice".equals(q.getId())) {
            String choice = getFlag("varsa_choice_made");
            if ("extracted".equals(choice)) {
                addFavor(God.LIRANDEL, 10);
                favorMsg = " (+10 Lirandel favor)";
            } else if ("sent_deep".equals(choice)) {
                addFavor(God.THALORAX, 5);
                addFavor(God.LIRANDEL, -5);
                favorMsg = " (+5 Thalorax favor, -5 Lirandel favor)";
            }
        }

        // Island 6 \u2014 Umbryn
        if ("umbryn_trial".equals(q.getId()))            {
            addFavor(God.UMBRYN, 20); favorMsg = " (+20 Umbryn favor)";
            if (grantBoon(Boon.UMBRYN_MEMORY)) {
                favorMsg += " Umbryn keeps a copy of you.";
            }
        }
        if ("fading_names".equals(q.getId()))            { addFavor(God.UMBRYN, 10); favorMsg = " (+10 Umbryn favor)"; }
        if ("eighth_dream".equals(q.getId()))            { addFavor(God.UMBRYN, 10); favorMsg = " (+10 Umbryn favor)"; }
        if ("echo_of_seraphine".equals(q.getId()))       { addFavor(God.UMBRYN, 5);  favorMsg = " (+5 Umbryn favor)"; }
        if ("shadow_errand".equals(q.getId()))           { addFavor(God.UMBRYN, 5);  favorMsg = " (+5 Umbryn favor)"; }

        // Island 7 \u2014 Bellorak
        if ("the_name_she_used_here".equals(q.getId()))  { addFavor(God.UMBRYN, 10); favorMsg = " (+10 Umbryn favor)"; }
        if ("the_unmourned".equals(q.getId()))           { addFavor(God.UMBRYN, 5);  favorMsg = " (+5 Umbryn favor)"; }
        if ("the_well_of_names".equals(q.getId()))       { addFavor(God.UMBRYN, 5);  favorMsg = " (+5 Umbryn favor)"; }
        if ("the_one_who_stayed_awake".equals(q.getId())){ addFavor(God.UMBRYN, 5);  favorMsg = " (+5 Umbryn favor)"; }

        if ("bellorak_arena".equals(q.getId()))          {
            addFavor(God.BELLORAK, 20); favorMsg = " (+20 Bellorak favor)";
            if (grantBoon(Boon.IRON_BROTHERHOOD)) {
                favorMsg += " The arena remembers your name.";
            }
        }
        if ("eternal_siege".equals(q.getId()))           { addFavor(God.BELLORAK, 10); favorMsg = " (+10 Bellorak favor)"; }
        if ("brothers_three".equals(q.getId()))          { addFavor(God.BELLORAK, 10); favorMsg = " (+10 Bellorak favor)"; }
        if ("iron_ghost_truth".equals(q.getId()))        { addFavor(God.BELLORAK, 5);  favorMsg = " (+5 Bellorak favor)"; }

        if ("the_field_that_was_a_town".equals(q.getId())) { addFavor(God.BELLORAK, 5);  favorMsg = " (+5 Bellorak favor)"; }
        if ("what_glory_costs".equals(q.getId()))          { addFavor(God.BELLORAK, 10); favorMsg = " (+10 Bellorak favor)"; }
        if ("the_thirty_year_letter".equals(q.getId()))    { addFavor(God.BELLORAK, 10); favorMsg = " (+10 Bellorak favor)"; }
        if ("the_pit_masters_ledger".equals(q.getId()))    { addFavor(God.BELLORAK, 10); favorMsg = " (+10 Bellorak favor)"; }

        String msg = "Quest complete: " + q.getTitle()
                + " (+" + q.getXpReward() + " XP"
                + (q.getGoldReward() > 0 ? ", +" + q.getGoldReward() + " gold" : "")
                + (spellMsg.isEmpty() ? "" : ", " + spellMsg.trim())
                + ")" + itemMsg + favorMsg;
        if (questCompleteLogger != null) {
            questCompleteLogger.accept(msg);
        }
        if (lvl != null && levelUpLogger != null) {
            levelUpLogger.accept(lvl);
        }
        if (questCompleteNotifier != null) questCompleteNotifier.accept(q);
        if (lvl != null && levelUpNotifier != null) levelUpNotifier.accept(lvl);
    }

    // ==================== QUEST GETTERS ====================

    public List<Quest> getActiveQuests() {
        return java.util.Collections.unmodifiableList(activeQuests);
    }

    public List<Quest> getCompletedQuests() {
        return java.util.Collections.unmodifiableList(completedQuests);
    }

    /** Removes a completed quest by ID (used when re-giving repeatable quests). */
    public void removeCompletedQuest(String questId) {
        completedQuests.removeIf(q -> q.getId().equals(questId));
    }

    // ==================== GETTERS ====================

    /** Returns the player's vision radius based on environment. Future: modify via equipment/spells. */
    public int getVisionRadius(boolean inDungeon, boolean inTown) {
        int base = inDungeon ? 5 : 14;
        // Future: add bonuses from equipment, spells, lantern items here
        return base;
    }

    public String getName() { return name; }
    public int getX() { return x; }
    public int getY() { return y; }
    public int getHp() { return hp; }
    public int getMaxHp() {
        int bonus = 0;
        for (Boon b : Boon.values()) if (hasBoon(b)) bonus += b.maxHpBonus;
        int grief = "true".equals(getFlag("umbryn_grief")) ? 5 : 0;
        return maxHp + bonus - grief;
    }
    public int getGold() { return gold; }
    public int getFood() { return food; }
    public int getLevel() { return level; }
    public int getXp() { return xp; }
    public int getXpToNextLevel() { return xpToNextLevel; }
    public int getStr() { return str; }
    public int getDex() { return dex; }
    public int getCon() { return con; }
    public int getIntelligence() { return intelligence; }
    public int getWisdom() { return wisdom; }
    public int getCharisma() { return charisma; }

    public boolean isDead() { return hp <= 0; }

    public void setPosition(int newX, int newY) {
        this.x = newX; this.y = newY;
    }

    // ==================== FACING (first-person dungeon) ====================

    /** 0=NORTH, 1=EAST, 2=SOUTH, 3=WEST */
    public int  getFacing()             { return facing; }
    public void setFacing(int facing)   { this.facing = facing & 3; }
    public void turnLeft()              { facing = (facing + 3) & 3; }
    public void turnRight()             { facing = (facing + 1) & 3; }
}

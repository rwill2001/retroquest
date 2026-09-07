# Monster System Requirements – RetroQuest

**Last updated:** September 2026

## 1. Overview / Purpose
Monsters are the primary combat opponents in the game.  

The system supports both **hand-crafted** monsters (editable via GUI, persisted in JSON) and **procedural** monsters (classic CRPG-style random scaling).  

A hybrid selection algorithm prefers designer-created monsters at the exact level the player is exploring, falling back to procedural generation for variety.  

**Every monster instance used in combat is a fresh copy** (full HP, cleared status effects) created from either the registry or the factory.

## 2. Core Data Model (`Monster` class)

Every monster has:

### Core Stats (all persisted)
- `id` – unique string key (stable for registry edits)
- `name`
- `imageFileName` (PNG in `/tiles/monsters/`)
- `hp` / `maxHp` (current & immutable max)
- `damage` (melee)
- `goldReward`
- `xpValue`
- `ac` (armor class)
- `level` (1–50)

### Monster Type (persisted)
- `monsterType` — `BEAST`, `HUMANOID`, `UNDEAD`, `MAGICAL`, `DEMON`, `DRAGON`, `FRIENDLY`.
  A null value is treated as `HUMANOID`.
- All 78 monsters in `data/monsters.json` now carry one: BEAST 21, HUMANOID 19, UNDEAD 18,
  MAGICAL 12, DEMON 4, DRAGON 2, FRIENDLY 2.
- It gates exactly four behaviours:
  - **Turn Undead** — 70% instant destroy vs `UNDEAD` (rewards reduced to 50%); on the 30% failure,
    double damage. Against the living, a resistance check then plain damage.
  - **Holy Word** — double damage vs `UNDEAD` or `DEMON`, plain damage otherwise.
  - **Undead XP drain** — 15% chance per melee hit taken from an `UNDEAD` monster to lose
    `max(50, xpToNextLevel / 4)` XP.
  - **Friendly** — `isFriendly()` returns `friendly || monsterType == FRIENDLY`, which keeps the
    monster out of hostile spawn pools.

### Status Effects (transient – never saved)
Only two live on `Monster`, both `transient`:
- `sleepTurns` — `isAsleep()`, `setAsleep(int)`, `decrementSleep()`
- `charmTurns` — `isCharmed()`, `setCharmed(int)` (mutually exclusive with sleep), `decrementCharm()`

Poison, stun, fear and blind are **not** on `Monster` — they are per-fight fields on `CombatEngine`.

### Spellcasting System
- `spellResistance` – 0–40 % chance to resist any spell
- `spellNames` – list of spell strings (must match player spell names)
- `spellCastChance` – 0–100 % (chance per turn to attempt a spell instead of melee)
- `spellPower` – base power for monster spells (scales like player spells)

**Helper methods**:
- `canCastSpells()`
- `pickRandomSpell()`

### Derived / Auto-calculated values (default constructors only)
- `xpValue = hp * 2`
- `level = max(1, hp / 8)`
- `ac = 10 + level / 2`

These defaults are applied when creating a new monster via the main constructor. The editor exposes AC and XP as editable spinners, so values saved through the editor override the auto-calculated defaults (`setAC()` / `setXpValue()`).

### Combat API
- `takeDamage()`, `isDead()`, `setAsleep(int turns)`, `setCharmed()`, `clearStatus()`, `decrementSleep()`
- Copy constructor `Monster(Monster template)` → always returns a fresh combat-ready instance.

## 3. Persistence & Registry (`MonsterRegistry`)

- **Single source of truth**: `data/monsters.json` (Gson)
- On first run: automatically seeds **36 canonical monsters** from `MonsterFactory.seedDefaults()`
  (34 combat + 2 friendly, one per name in the tier table). `ensureFactoryDefaults()` re-inserts any
  missing `factory_*` id at startup and re-saves; a user-edited registry entry always wins.
- A `monsters.json` that exists but will not parse leaves the registry `loadFailed`, and saves are
  then refused so the file is not overwritten.
- Full CRUD: add / update / remove + auto-save on every change.
- `getAllMonsters()` returns a defensive copy for the editor.
- `getById(String id)`

### Random Monster Selection (`getRandomMonster(int targetLevel [, Map<String,Integer> spawnWeights])`)

Target level is first clamped to 1–50, then:

1. **5 %** chance to return a friendly monster instead of anything hostile.
2. If any hostile registry monster has an **exact** level match → **75 %** chance to pick one (weighted).
3. Else if any hostile monster is within **±3 levels** → **50 %** chance to pick one (weighted).
4. Otherwise → `MonsterFactory.generate(targetLevel)` (pure procedural).

`pickWeighted` is uniform when no `spawnWeights` map is supplied, else weights are
`max(1, weights.getOrDefault(id, 1))`. (The inline comment at step 3 still says "25 % chance"; the
code uses 0.50.)

`spawnWeights` (monster ID → relative weight) allows area-specific spawn tables.

## 4. Procedural Generation (`MonsterFactory`)

- `generate(level)` clamps level to 1–50. Name tier = `(level - 1) / 5`, giving **10 tiers**.
- **3 name pools** per tier (Kobold/Giant Rat/Goblin … Arch Demon/Ancient Dragon/Death)
- **78 total monsters** in `data/monsters.json`, 36 of them the `factory_*` seed set.
- **Stat formulas**:
  - HP: `level*8 + rand(level*8)`
  - Damage: `max(1, level/2 + rand(level/2))`
  - Gold: `level*2 + rand(level*3)`
  - XP: `level * (25 + level) + rand(level*10)` — quadratic; mean `level * (30 + level)`. The seed
    set uses that deterministic mean directly.
  - AC: `10 + level/3`
- Monster spell power is capped at `level * MAX_SPELL_POWER_PER_LEVEL` where the constant is 2.
- Automatic spell assignment based on name keywords (lich, vampire, demon, wraith, etc.), via the
  shared `assignSpellsByName()` used by both `generate()` and `seedDefaults()`.
- `MonsterFactory` has **no boss concept**. Boss stats are hardcoded at their call sites in
  `DungeonController` — the throne champion buffs a registry pick by +50% HP, +50% damage, ×2 gold,
  ×2 XP, +3 AC; the trial and Cradle bosses use literal values, with base numbers in
  `data/balance.json` via `BalanceConfig`.

`seedDefaults()` produces the 36 fixed template monsters used by the registry, one per tier name at
level `tier * 5 + 3` (3, 8, 13, … 48).

**Coverage gap**: the registry has no monsters at levels 29-32, 34-37, 39-42, 44-47, 49 or 50, so
encounters in that band always fall through to the factory.

## 5. Editing & Tooling (`MonsterEditorDialog`)

Full CRT-styled editor dialog with a left list panel and a right tabbed form.

### Monster List (left panel)
- **Search bar** — filters by name, ID, or level as you type; list displays `Lv## Name` for quick scanning.
- **NEW** — creates a blank monster and selects it.
- **CLONE** — duplicates the selected monster with a new ID and “(Copy)” suffix.
- **DEL** — prompts for confirmation, then permanently removes the monster from the registry.

### Stats Tab
Editable fields: ID, Name, Level, HP, Damage, Gold, **AC**, **XP Value**, Sprite.
- AC and XP Value are now fully editable spinners (overriding auto-calculated defaults).
- Live sprite preview panel (checkerboard transparency background, phosphor border glow).
- “EDIT SPRITE” opens the pixel-art `ImageEditor` for the selected sprite.

### Spells Tab
- Cast Chance (0–100%), Spell Power (0–999), Spell Resistance (0–40%).
- 34-checkbox spell picker (names must match player spell names — see Gap #1 below).
- Warning displayed in status bar if spells are assigned but Cast Chance is 0%.
- Warning on load if a monster has spells not in the known list (they would be dropped on save).

### Save
- Ctrl+S or the SAVE button writes to the registry and reloads `ImageAssetRegistry`.
- Selection is preserved by monster ID after refresh.

## 6. Integration Points
- Combat calls `MonsterRegistry.getRandomMonster(playerLevel, tileSpawnWeights)` then `new Monster(selected)` for a fresh copy.
- Uses: `isAsleep()`, `isCharmed()`, `canCastSpells()`, `pickRandomSpell()`, `takeDamage()`, `isDead()`, status decrement each turn.
- Images loaded from `src/main/resources/tiles/monsters/`.

---

## Documented Gaps & Issues

1. **Spell Name Mismatch (Critical)**
   `MonsterFactory` generates spells like `”Fear”` and `”Drain”` that are **not** in the editor’s `ALL_SPELL_NAMES` list. They are silently dropped if a factory-generated monster is loaded and re-saved in the editor.

2. **spawnWeights Support Unused**
   Fully implemented in `MonsterRegistry.getRandomMonster()` but never called with a non-empty map anywhere in the game code.

3. **No Validation Between Editor and Spell Engine**
   The editor allows any spell from its 34-name list; no guarantee the combat system handles every name correctly.

4. **Minor Technical Notes**
   - Factory “mage/wizard/sorcerer” keyword matching never triggers because factory name pools don’t include those words.
   - No elite/variant system yet (e.g., elite, champion, boss modifiers).

### Fixed Gaps (no longer issues)
- ~~No direct editing of XP or AC~~ — AC and XP spinners added to Stats tab (v1.2).
- ~~Missing Delete UI~~ — DEL button with confirmation dialog added (v1.2).
- ~~No Clone/Search~~ — CLONE button and search/filter bar added (v1.2).

---

**Created from source analysis of:**  
`Monster.java`, `MonsterEditorDialog.java`, `MonsterFactory.java`, `MonsterRegistry.java`

**Last updated:** March 2026 (v1.2 — editor features updated)
**Status:** Living documentation, current with source.
# RetroQuest — Spell System: Current State & Future Work

**Last Updated:** March 2026
**Covers:** All work through Entangle and spell visual effects

---

## Overview

RetroQuest implements a classic 37-spell system divided into MAGE and CLERIC schools across six levels. The system is fully functional end-to-end: players cast spells in combat and on the map, monsters resist and cast back, dungeon specials occasionally restore spell capacity, and scrolls/wands provide consumable access to any spell in the game.

---

## What Is Fully Implemented

### Core Architecture
- **`Spell.executeCombat(Player, Monster, CombatOverlay)`** — centralized dispatch method for all 37 combat spells. Returns `false` to skip the monster's turn (used by Time Stop, instant-kill outcomes, etc.).
- **`Spell.getPower(Player)`** — **spell power now scales with player level**:

  ```java
  int bonus  = (type == MAGE ? caster.getIntelligence() : caster.getWisdom()) - 10;
  int scaled = (int)(basePower * (1.0 + caster.getLevel() / 20.0));   // POWER_LEVEL_DIVISOR = 20
  return scaled + bonus / 2;
  ```

  That is ×1.05 at level 1, ×2 at level 20, ×3.5 at level 50. `executeCombat` then applies ±20%
  random variance on every cast.

  Note Turn Undead and Holy Word are both declared with `basePower = 0`, so their power comes
  entirely from the `(WIS − 10) / 2` term — the level multiplier has nothing to multiply.
- **`Spell.resisted(Monster)`** — checks monster's `spellResistance` field (0–40%) before applying effects.
- **`Spell.playSpellSound()`** — routes to the correct SoundManager key based on spell name keywords.
- **Buff/debuff state in `CombatOverlay`** — ten named fields (acBonus, toHitBonus, damageBonus, invisible, haste, fireResist, prayer, holyArmor, playerPoisonTurns, playerSleepTurns) plus four monster debuff counters (poisonTurns, stunTurns, fearTurns, blindTurns). All reset at combat start/end via `resetCombatBuffs()`.

### Spell Learning System
- **`Player.spellLearned[37]`** — parallel boolean array tracking which spells the player has permanently learned.
- Level-1 spells are auto-learned on character creation via `initLearnedSpells()`.
- Spells beyond level 1 must be learned from scrolls or quest rewards.
- `SpellbookOverlay` only shows spells where `isSpellLearned(spell)` is true and `spell.getLevel() <= player.getLevel()`.
- `Player.learnSpell(String)` teaches a spell permanently; `isSpellLearned(String|Spell)` checks status.
- Old saves without the `spellLearned` array are migrated via `restoreSpellLearning()` — defaults to level-1-only.

### All 37 Player Spells

| # | Level | School | Spell | Usage | Effect |
|---|-------|--------|-------|-------|--------|
| 1 | 1 | Mage | Magic Missile | Combat | Always hits; cannot be resisted; scaled damage |
| 2 | 1 | Mage | Sleep | Combat | Puts monster to sleep for 2 + level/3 turns |
| 3 | 1 | Mage | Charm Monster | Combat | 40% base flee chance, +3% per level (max 70%); or charmed idle |
| 4 | 1 | Cleric | Cure Light Wounds | Both | Heals scaled HP, shows exact amount |
| 5 | 1 | Cleric | Protection from Evil | Combat | +3 AC for full combat |
| 6 | 1 | Cleric | Shield | Combat | +2 AC for full combat |
| 7 | 2 | Mage | Fireball | Combat | Damage; resistance check; monster flash |
| 8 | 2 | Mage | Lightning Bolt | Combat | Damage; pierces 50% of spell resistance; monster flash |
| 9 | 2 | Mage | Invisibility | Combat | Guarantees next attack auto-hits; expires on use |
| 10 | 2 | Cleric | Cure Serious Wounds | Both | Heals more than level 1; shows exact amount |
| 11 | 2 | Cleric | Bless | Combat | +1 to-hit, +1 damage for full combat |
| 12 | 2 | Cleric | Turn Undead | Combat | Keys off `monsterType == UNDEAD`: 70% instant destroy (rewards cut to 50%), else double damage. Vs the living: resistance check then normal damage |
| 13 | 3 | Mage | Ice Storm | Combat | Damage; resistance check; monster flash |
| 14 | 3 | Mage | Dispel Magic | Combat | Removes all player buffs and all monster debuffs |
| 15 | 3 | Mage | Teleport | Map | Teleports player back to last safe town |
| 16 | 3 | Cleric | Cure Critical Wounds | Both | Strong heal; shows exact amount |
| 17 | 3 | Cleric | Prayer | Combat | +2 AC, +2 to-hit for full combat; cannot stack |
| 18 | 3 | Cleric | Holy Word | Combat | Double damage vs `UNDEAD` or `DEMON` `monsterType`; normal damage otherwise |
| 19 | 4 | Mage | Cone of Cold | Combat | Damage; cannot be resisted; monster flash |
| 20 | 4 | Mage | Cloudkill | Combat | Applies 3–5 turns of poison to monster |
| 21 | 4 | Mage | Haste | Combat | Grants one bonus attack this round |
| 22 | 4 | Cleric | Heal | Both | Fully restores player to max HP |
| 23 | 4 | Cleric | Resist Fire | Combat | Grants fire immunity for full combat |
| 24 | 4 | Cleric | Restoration | Both | Full heal + removes all player debuffs |
| 25 | 5 | Mage | Chain Lightning | Combat | Damage; pierces 50% of spell resistance; monster flash |
| 26 | 5 | Mage | Death Spell | Combat | Instant kill chance (50% − level×4%, min 5%); partial damage if resisted |
| 27 | 5 | Mage | Teleport Party | Map | Same behaviour as Teleport |
| 28 | 5 | Cleric | Holy Armor | Combat | +5 AC for full combat; cannot stack |
| 29 | 5 | Cleric | Flame Strike | Combat | Damage; resistance check; monster flash |
| 30 | 6 | Mage | Meteor Swarm | Combat | Damage; resistance check; monster flash |
| 31 | 6 | Mage | Power Word Kill | Combat | Instant kill if monster HP < 2× player max HP; partial damage otherwise |
| 32 | 6 | Mage | Wish | Both | Random: full heal / 3× damage / 100–500 gold / 25% XP to next level. Map: +50 gold |
| 33 | 6 | Mage | Time Stop | Combat | Skips monster's turn entirely |
| 34 | 6 | Cleric | Divine Intervention | Combat | 60% massive damage, 40% monster flees |
| 35 | 6 | Cleric | Word of Recall | Map | Teleports player back to last safe town |
| 36 | 6 | Cleric | Resurrection | Map | Arms a resurrection ward (see below) |
| 37 | -- | Mage | Entangle | Combat | Half damage + 2+level/5 round immobilization. Quest reward (Sylvandar) |

### Resurrection Ward
- **Design:** Pre-cast ward model. Player casts Resurrection on the map (spellbook or scroll) which sets a `resurrectionCharged` flag on Player. If the player dies in combat, the ward triggers automatically: revives at 50% max HP, combat continues.
- **Casting:** Map-only (`Spell.Usage.MAP`). Cannot be cast in combat — attempting to do so shows a redirect message.
- **Scroll:** "Scroll of Resurrection" (EPIC rarity, tier 6, lootable, not shop-available, stacks to 3). Using the scroll arms the ward directly — bypasses spell level/learning gates, making it a lucky find at any level.
- **Double-charge protection:** Both spellbook and scroll paths check `isResurrectionCharged()` first and warn if a ward is already active. The scroll is not consumed if the ward is already charged.
- **Death interception:** `CombatOverlay.checkPlayerDeath(String)` is called at all 4 death checkpoints (poison tick, melee hit, monster spell damage ×2). If the player is dead and a ward is charged, it triggers `player.triggerResurrection()`, plays the heal sound, logs the activation, refreshes StatsPanel, and returns false (combat continues). Otherwise runs the normal death sequence.
- **Persistence:** `resurrectionCharged` is a plain boolean on Player — Gson serializes it automatically. Defaults to `false` on old saves. Not cleared by `clearSpellBuffs()` — the ward persists across combats until triggered.
- **UI:** StatsPanel shows "Resurrect (WARD)" in gold in the buffs section when the ward is active.

### Map Spell Casting
- **`NpcController.castMapSpell(Spell)`** — resolves spells cast from the overworld/dungeon map.
- Healing spells (Cure *, Heal) heal directly using `spell.getPower()`.
- Teleport / Teleport Party / Word of Recall: teleports to last safe town.
- Detect Magic: activates 8-second highlight on magic items in inventory.
- Wish: grants +50 gold.
- Resurrection: charges the resurrection ward.
- Combat-only spells are rejected with a message.

### Monster Spell Resistance
- `spellResistance` field on Monster (0–40), persisted in monsters.json.
- Registry entries updated with meaningful values:
  - Undead (Ghoul 20, Wight 25, Wraith/Specter 28, Vampire 30): 20–30
  - Gargoyle 15, Medusa 5, humanoids (Orc/Gnoll/Hobgoblin/Bugbear) 5
  - Demons/Devils 35, Nightmare 30, Arch Demon/Ancient Dragon/Death 40
  - Corrupted Moon Guardian (boss) 35
- `MonsterFactory.generate()` assigns resistance by name keyword: demon/devil → min(40, 30+level/5), wraith/specter/wight/ghost → min(35, 20+level/2), lich/vampire → min(40, 20+level).

### Enemy Spellcasting

> Monster spell effects during combat are also documented in `CombatSystem.md` §5.

- `Monster` fields: `spellNames` (List\<String\>), `spellCastChance` (0–100), `spellPower`.
- `canCastSpells()` and `pickRandomSpell()` helpers on Monster.
- `CombatOverlay.monsterTurn()` checks spell casting after blind/fear/stun but before melee attack; calls `castMonsterSpell()` on a successful chance roll.
- `castMonsterSpell()` handles: Magic Missile, Fireball, Lightning Bolt, Ice Storm (damage), Sleep (2 turns), Poison (3–4 turns), Fear (1 turn), Drain (gold loss), Stun (1 turn, skips player attack), Blind (2–3 turns, -4 to-hit), Haste (monster also makes a melee attack this round), plus unknown spells fall back to damage.
- Player WIS-based resist check (0–25%) before each monster spell lands.
- Player debuffs: `playerPoisonTurns`, `playerSleepTurns`, `playerStunTurns`, `playerBlindTurns` — all checked at the top of `playerAttack()`. All cleared by Dispel Magic.
- Registry entries with spells assigned:

  | Monster | Chance | Power | Spells |
  |---------|--------|-------|--------|
  | Corrupted Moon Guardian | 55% | 40 | Fireball, Fear, Drain |
  | Wraith, Specter | 35% | 15 | Drain, Fear |
  | Vampire | 40% | 22 | Drain, Fear, Sleep |
  | Demon, Devil | 45% | 28 | Fireball, Fear |
  | Dragon | 40% | 35 | Fireball, Fear |
  | Death Knight | 35% | 25 | Fear, Drain |
  | Arch Demon | 50% | 40 | Fireball, Fear, Drain |
  | Ancient Dragon | 40% | 40 | Fireball, Fear |
  | Death | 60% | 45 | Drain, Fear, Magic Missile |

- `MonsterFactory` assigns spells by name keyword: mage/wizard/sorcerer, lich/vampire/necromancer, demon/devil, wraith/specter/wight/ghost.
- Spellcaster names added to NAMES table across four tiers:

  | Tier | Levels | Name | Keyword Group | Spells |
  |------|--------|------|---------------|--------|
  | 1 | 6–10 | Goblin Sorcerer | sorcerer | Fireball, Sleep, Fear |
  | 3 | 16–20 | Dark Mage | mage | Fireball, Sleep, Fear |
  | 5 | 26–30 | Necromancer | necromancer | Drain, Fear, Magic Missile |
  | 8 | 41–45 | Lich | lich | Drain, Fear, Magic Missile |

- Spell assignment logic extracted into shared `assignSpellsByName()` helper — used by both `generate()` and `seedDefaults()` so seeded registry entries get spells pre-assigned.

### Scrolls and Wands
- `Item.Type.SCROLL` and `Item.Type.WAND` exist; `Item.spellName` links item to a spell by name.
- **32 scrolls** in `data/items.json` covering all learnable spells. Most scrolls teach the spell permanently on use (if not already known). The Resurrection scroll is a special case — it arms the resurrection ward instead of teaching the spell.
- **3 wands**: Lightning Bolt (3 charges), Fire/Fireball (4 charges), Sleep (3 charges).
- `InventoryOverlay.useSelected()` handles USE on scrolls/wands: looks up spell by name, executes it (combat spells go through `CombatOverlay.executeCombatSpell()`; map spells go through `NpcController.castMapSpell()`).
- **Wand charge tracking fully implemented**: `Item.charges` field holds initial charge count; `Player.addItem()` initialises the slot quantity from charges (not 1). Each use decrements the charge count; the wand is removed when charges reach zero. The inventory row shows "(N)" and the detail pane shows "Charges: N". The use message reports remaining charges or announces depletion. `maxStackSize` is set to 1 for wands so individual wands do not merge stacks.

### Spell Buff Persistence
- Seven step-based buffs on Player: Haste, Prayer, Holy Armor, Resist Fire, Invisibility, Protection from Evil, Bless.
- Each has an `*Steps` counter decremented on every `move()` call (map movement). Active while steps > 0.
- Buffs persist across combats — they are NOT cleared by `clearSpellBuffs()` (which only clears them on death or rest).
- Invisibility is the exception — combat-scoped, cleared by `resetCombatBuffs()` at combat end.
- `StatsPanel` snapshots all buff counters in `refresh()` and renders them in a two-column "BUFFS" section with step counts.
- Resurrection ward is displayed alongside buffs in gold with "(WARD)" instead of a step count.

### Spell Visual Effects (`SpellEffectRenderer`)

Each spell triggers an animated visual effect during combat. `SpellEffectRenderer` maps spells to one of 18 effect types, each with a dedicated paint method animated over a normalized t (0→1) timeline.

| EffectType | Duration | Visual | Spells |
|---|---|---|---|
| `FIREBALL` | 700ms | Orange ball travels → explosion + sparks | Fireball |
| `LIGHTNING` | 700ms | Jagged 3-pass bolt + branches | Lightning Bolt, Chain Lightning |
| `ICE` | 700ms | 3 shards → snowflake burst | Ice Storm, Cone of Cold |
| `MISSILE` | 400ms | Purple orb + trail → impact ring | Magic Missile (+ default fallback) |
| `HEAL` | 700ms | Rising sparkles + pulsing green cross | Cure spells, Heal, Restoration |
| `SLEEP` | 700ms | Blue orb → floating Z's | Sleep, Charm Monster |
| `DEATH` | 700ms | Dark vignette → pulsing skull → red fade | Death Spell, Power Word Kill, Cloudkill |
| `BUFF` | 700ms | 6 golden orbits spinning | Shield, Dispel Magic, Resist Elements, Sanctuary, Haste, Prayer, etc. |
| `POISON` | 700ms | 5 green bubbles rising | Cloudkill, Poison |
| `FIRE_BREATH` | 900ms | 3-phase cone + embers + smoke | Fire Breath (monster) |
| `DRAIN` | 900ms | Dark bézier tendrils + purple particles | Drain (monster) |
| `GIFT` | 700ms | Amber glow + star-sparkles + diamond | Friendly encounter gifts |
| `HOLY` | 700ms | Golden light pillar descends → radiant burst → golden motes | Holy Word, Turn Undead, Divine Intervention, Flame Strike |
| `ENTANGLE` | 700ms | Green vines rise from below + leaves → constriction ring | Entangle |
| `TIME_STOP` | 800ms | Clock face → spinning hands → blue-white flash → cracks | Time Stop |
| `FEAR` | 700ms | Shadow wisps converge + dark overlay + ghostly red eyes | Fear (monster) |
| `WISH` | 800ms | Prismatic motes spiral → rainbow rings expand → sparkle fade | Wish |
| `METEOR_SWARM` | 900ms | 5 staggered fireballs from top → clustered explosions + red tint | Meteor Swarm |

Monster spells reuse the same effects. The `effectFromPlayer` flag determines direction (player→monster or monster→player).

### Spellbook UI
- Color-coded spell names: damage = orange, healing = green, buff/utility = cyan, grey if out of casts.
- Only shows spells that are learned (`isSpellLearned`) and at or below player level.
- Estimated power value shown in detail panel for the selected spell.
- Cast count per spell level displayed alongside each entry.

### Altar / Fountain Bonus Slot
- Altar: ~8% chance grants `player.addBonusCastSlot()` instead of a tithe or damage hit.
- Fountain: 1-in-11 outcome restores a spell slot.
- `Player.addBonusCastSlot()`: adds +1 to the highest level that still has casts; falls back to restoring one level-1 slot if all are depleted.

---

## Known Gaps and Future Tasks

These are ordered roughly by impact and effort.

### 1. Data-Driven Spell Definitions
**What:** Move spell definitions out of `Player.initSpells()` (hard-coded Java array) into a spells.json data file loaded at startup.
**Why:** Currently adding or tuning a spell requires a recompile.
**Effort:** High. Requires a SpellRegistry class (parallel to ItemRegistry/MonsterRegistry), Gson deserialization, and migrating all 37 spell definitions. The `executeCombat()` logic itself would remain in Java.

---

## Reference: Key Files

| File | Role |
|------|------|
| `Spell.java` | 37-spell `executeCombat()` dispatch, power scaling, sound routing |
| `CombatOverlay.java` | Buff/debuff state, `playerAttack()`, `monsterTurn()`, `castMonsterSpell()`, `checkPlayerDeath()` |
| `Player.java` | Spell slot arrays, `initSpells()`, `refreshSpellSlots()`, `addBonusCastSlot()`, spell learning, resurrection ward |
| `NpcController.java` | Map spell casting (`castMapSpell()`), Resurrection ward charging |
| `Monster.java` | `spellResistance`, `spellNames`, `spellCastChance`, `spellPower` |
| `MonsterFactory.java` | Procedural spellcaster assignment by name keyword via `assignSpellsByName()` |
| `SpellbookOverlay.java` | Color-coded spell list, power estimate display, spell learning gate |
| `InventoryOverlay.java` | Scroll/wand USE handling, Resurrection scroll special case |
| `StatsPanel.java` | Buff display including resurrection ward indicator |
| `DungeonController.java` | Altar/fountain bonus cast slot triggers |
| `data/monsters.json` | Resistance and spell assignments for registry monsters |
| `SpellEffectRenderer.java` | 18 animated effect types, spell→effect mapping |
| `data/items.json` | 32 scroll + 3 wand item definitions |

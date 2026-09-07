# Player System — RetroQuest

**Last updated:** September 2026

## 1. Overview

The player system is centered on three files:

- **`Player.java`** (1144 lines) — Central state object: stats, inventory, equipment, spells, buffs, quests, flags.
- **`Effect.java`** (98 lines) — Passive effect model (PROTECTION, REGENERATION, HEALING, DAMAGE, SPELL_RESIST) attached to equipment.
- **`CharacterCreationDialog.java`** (313 lines) — Modal dialog for stat rolling and name entry.

---

## 2. Base Stats (Classic 6-Attribute)

All six attributes are rolled at character creation and **never change** (final fields).

| Stat | Abbreviation | Roll | Combat Use |
|------|-------------|------|------------|
| Strength | STR | 3d6 | Melee damage: `+(STR − 10)` (weapon contributes through its `Effect`) |
| Dexterity | DEX | 3d6 | AC: `+(DEX − 10) / 2` bonus; to-hit bonus: `(DEX − 10) / 2` |
| Constitution | CON | 3d6 | Starting HP: `CON + 10`; HP/level: `3 + (CON − 10) / 3`; damage reduction: `max(0, (CON − 10) / 3)` |
| Intelligence | INT | 3d6 | Mage spell slots bonus: `max(1, (INT − 10) / 2 + level / 3)` |
| Wisdom | WIS | 3d6 | Cleric spell slots bonus: `max(1, (WIS − 10) / 2 + level / 3)`; spell resistance: `max(0, (WIS − 10) / 2 × 5)` % |
| Charisma | CHA | 3d6 | Monster reaction: `3d6 + (CHA − 10) / 2`; flee chance: `60 + (CHA − 10) × 3` % |

---

## 3. Leveling & XP

| Level | XP to Next |
|-------|-----------|
| 1 | 900 |
| 2 | 1,800 |
| 5 | 4,500 |
| 10 | 9,000 |
| 15 | 13,500 |
| 20 | 18,000 |
| 27 | 24,300 |

**Formula:** `xpToNextLevel = level × 900` (linear)

**On level-up:**
1. Level incremented.
2. Max HP increased by `3 + (CON − 10) / 3`.
3. HP healed to max.
4. Spell slots refreshed via `refreshSpellSlots()`.

---

## 4. Combat Derivations

| Stat | Formula |
|------|---------|
| Damage | `effectBonuses + boonBonuses + (STR − 10) + level / 3` |
| AC | `10 + (DEX − 10) / 2 + min(effectBonuses + boonBonuses, level + 2)` |
| Damage Reduction | `max(0, (CON − 10) / 3)` |
| To-Hit Roll | `d20 + level + (DEX − 10) / 2 + 3 + hitBonus − blindPenalty` |

**Damage scales with level** — `Player.getDamage()` adds `level / DAMAGE_LEVEL_DIVISOR` where the
divisor is 3, on top of gear effects, boons and the STR modifier. Bless adds a further +2 while
active (`getDmgBonus()`).

**AC is ascending** — higher is harder for monsters to hit. The monster hits if
`d20 + monsterLevel ≥ playerAC`. The gear-and-boon contribution is **capped at `level + 2`**
(`NON_DEX_AC_CAP_BASE = 2`), so stacking PROTECTION gear cannot outrun your level. The base 10 and
the DEX term are never capped.

Spell wards (Shield, Holy Armor, Sanctuary, Protection from Evil) are *not* part of `getAC()`.
`Player.getAcBonus()` takes the single best active ward (max +5) and `CombatEngine` adds it
separately as `player.getAC() + player.getAcBonus()`, so wards bypass the `level + 2` cap.

---

## 5. Resources

| Resource | Start | Max | Consumed By |
|----------|-------|-----|-------------|
| Gold | 25 | unbounded | Shops, monster drain spells |
| Food | 500 | 999 | −1 per movement step |

When food reaches 0, the player starves (damage per step, handled by movement logic).

---

## 6. Spell System (37 Spells, Levels 1–6)

### Spell Slot Calculation

```
maxSpellLevel = min(6, playerLevel)
mageBonus  = max(1, (INT − 10) / 2 + level / 3)
clericBonus = max(1, (WIS − 10) / 2 + level / 3)

For each spell level L (1–6):
  if L > maxSpellLevel: slots = 0
  else:
    multiplier = maxSpellLevel − L + 3
    mageSlots[L]   = min(9, mageBonus × multiplier / 2 + 1)
    clericSlots[L]  = min(9, clericBonus × multiplier / 2 + 1)
```

Slots are refreshed on level-up and on game load.

### Spell Learning

- Level 1 spells are auto-learned at character creation.
- Higher spells learned via scrolls, quest rewards, or NPC teaching (`TEACH_SPELL` dialogue action).
- `spellLearned` is a parallel boolean array to `knownSpells`. Both arrays are allocated at size **38** while `initSpells()` defines **37** spells, so the last slot is permanently `null` — `SpellbookOverlay` skips nulls. (The `spellLearned` field initialiser still says 37; every other allocation and the load migration use 38.)

### Bonus Casts

`addBonusCastSlot()` grants +1 to the highest spell level with remaining casts (both mage & cleric). If all depleted, restores one level-1 slot each.

### Spell List

37 spells across 6 levels (6 per level, plus Entangle as a quest reward). See `SpellSystem.md` for the complete spell table with effects, resistance behavior, and visual effects.

**Level distribution:** L1–L5 have 6 spells each; L6 has 7 spells. Schools split evenly between MAGE and CLERIC at each level.

---

## 7. Spell Buffs (Step-Based)

Spell buffs decrement each movement step (not during combat). Invisibility is **not** ticked on movement — only consumed by combat auto-hit.

| Buff | Steps | Effect |
|------|-------|--------|
| Haste | 25 | Bonus melee strike per combat round |
| Prayer | 40 | +2 AC, +2 to-hit |
| Holy Armor | 50 | +5 AC |
| Fire Resist | 35 | Negate fire damage |
| Invisibility | 20 | Next attack auto-hits (consumed) |
| Prot. from Evil | 35 | +3 AC |
| Bless | 30 | +1 to-hit, +1 damage |

### Stacking Bonuses

| Bonus | Sources | Max |
|-------|---------|-----|
| AC bonus | ProtEv (+3) + Prayer (+2) + HolyArmor (+5) | +10 |
| Hit bonus | Prayer (+2) + Bless (+1) | +3 |
| Dmg bonus | Bless (+1) | +1 |

> For combat-scoped buff/debuff mechanics (transient per-fight state), see `CombatSystem.md` §4.

### Special Abilities

- **Detect Magic**: 8-second real-time duration. Highlights magical items in inventory with pulsing purple tint.
- **Resurrection Ward**: Pre-charged; on death, revives at 50% max HP (one-shot, must be re-cast).

---

## 8. Effect System (`Effect.java`)

Effects are passive bonuses attached to equipped items. Any equipment slot can carry effects: rings, amulets, helms, and shields all use them.

### Effect Types

| Type | Behavior | Duration |
|------|----------|----------|
| PROTECTION | Adds `value` to `player.getAC()` | Permanent (−1) |
| REGENERATION | Heals `value` HP every 4 movement steps | Permanent (−1) |
| HEALING | Instant heal of `value` HP on apply | Immediate (0 turns) |

- `turnsRemaining = −1` means permanent (lasts while item equipped).
- `rebuildEffects()` on `Player` collects effects from all **7 equipment slots** (weapon, armor, helm, amulet, shield, ring1, ring2) into `activeEffects`.
- For legacy `RING_PROTECTION`/`RING_REGEN` types, `Item.rebuildEffects()` regenerates the effect from the item's `value` field on load. All other types (AMULET, HELM, SHIELD, RING) store effects directly in JSON.
- Regeneration uses a per-effect `moveCounter` that increments on each `onMove()` call; heals every 4 moves.

---

## 9. Quest Tracking

Quests are managed directly on the Player object.

- `activeQuests` — list of in-progress quests.
- `completedQuests` — list of finished quests.
- `addQuest(quest)` — adds if not duplicate; sets status IN_PROGRESS.
- `progressQuest(type, target, amount)` — finds matching quest, progresses it, auto-completes if threshold met.
- `completeQuest(quest)` — moves to completed list; grants gold/XP/item/spell rewards.

---

## 10. Dialogue Flags

Persistent key-value store (`Map<String, String>`) for NPC dialogue branching.

- `getFlag(key)` / `setFlag(key, value)` / `clearFlag(key)` / `hasFlag(key)`
- Null-safe — map created lazily on first write.
- Persisted across saves via Gson serialization.
- Used by `DialogueCondition` (FLAG_SET, FLAG_EQUALS, FLAG_NOT_SET) and `DialogueAction` (SET_FLAG, CLEAR_FLAG).

---

## 11. Character Creation (`CharacterCreationDialog.java`)

1. Modal dialog (420×520px), CRT-styled with green buttons.
2. Stats displayed as "??" until REROLL pressed.
3. `rollStats()` rolls 3d6 for each of the six attributes.
4. Keyboard navigation: LEFT/RIGHT cycle buttons (REROLL, ACCEPT, QUIT); ENTER activates.
5. On ACCEPT: prompts for name (defaults to "Hero"), creates `Player(12, 35, stats..., name)`.
6. Starting position (12, 35) = town center.
7. Launches `IntroCinematicDialog` after creation.
8. Closing dialog without accepting calls `System.exit(0)`.

---

## 12. Save/Load Integration

- `restoreAfterLoad()` called post-Gson deserialization:
  - Rebuilds the `knownSpells` array (size 38) if null or short.
  - Calls `restoreSpellLearning()` — migrates old saves where `spellLearned` was null.
  - Refreshes spell slots.
  - Rebuilds equipment effects.
- `flags` map null-checked on access for old save compatibility.

---

## 13. Key Ring

All `KEY`-type items are stored on a permanent key ring (`Player.keyRing`) rather than in the 20-slot inventory.

- Keys are never lost — immune to inventory full, cannot be sold or dropped.
- Keys persist forever across saves.
- Keys do not consume inventory slots.
- Old saves without `keyRing` field auto-initialize to an empty list.
- API: `addKey(id)`, `hasKey(id)`, `getKeyRing()`
- `completeQuest()` automatically routes KEY item rewards to the key ring.
- `NavigationController.playerHasItem()` and `tryUnlockTile()` check the key ring first.
- `DialogueOverlay` GIVE_ITEM action routes KEY items to `addKey()`.
- `DialogueCondition` HAS_ITEM checks key ring before inventory.

---

## 14. Divine Favor

Seven gods track individual favor scores on the player.

- `God.java` enum: LIRANDEL(0), PYRALIS(1), ZEPHYRION(2), SYLVANDAR(3), THALORAX(4), UMBRYN(5), BELLORAK(6)
- `Player.favorScores` — `int[7]`, neutral = 50, clamped 0–100, persisted via Gson.
- Old saves without favor data default to neutral (50) via `favorArray()`.
- API: `addFavor(God, int)`, `getFavor(God)`, `getFavorScores()`

---

## 15. Permanent Boons

Boons are permanent stat bonuses earned from island god trials.

- `Boon.java` enum with fields: `maxHpBonus`, `damageBonus`, `acBonus`, `spellResistBonus`, `altarGrace`.
- `Player.acquiredBoons` — `Set<String>`, persisted via Gson.
- API: `grantBoon(Boon)`, `hasBoon(Boon)`
- Stat methods (`getMaxHp`, `getDamage`, `getAC`, `getTotalSpellResist`) sum boon bonuses automatically.
- `StatsPanel` shows a BOONS section when any boon is held.

Current boons:
| Boon | Source | Effect |
|------|--------|--------|
| MOONBLESSED | Island 1 pure path | +10 max HP, free altar healing |
| PYRALIS_TEMPER | Island 1 corruption path | +2 damage |
| ZEPHYRION_GRACE | Island 3 trial | +1 AC |
| SYLVANDAR_ROOTS | Island 4 trial | +5 max HP, +5% spell resist |
| TIDAL_ENDURANCE | Island 5 trial | +5% spell resist |
| UMBRYN_MEMORY | Island 6 trial | +3 damage, +5% spell resist, -5 HP |
| IRON_BROTHERHOOD | Island 7 trial | +3 damage, +2 AC |

---

## 16. Known Gaps

1. **Stats never grow** — all six attributes are final after creation. No stat-boosting items or level-up attribute points.
2. **Food system underspecified** — starvation damage rate not documented; food consumption is always exactly 1 per step regardless of terrain.
3. **Inventory 20 vs 24 inconsistency** — Player has 20 slots but InventorySlot doc comment references 24. Actual array is 20.
4. **`regenTimer` field** — unused, kept for save compatibility.
5. **No respec mechanic** — cannot reallocate spell learning or undo equipment choices.
6. **Spell slot cap at 9** — high-level characters with high INT/WIS still max at 9 casts per spell level.

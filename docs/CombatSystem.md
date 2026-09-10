# Combat System — RetroQuest

**Last updated:** September 2026

## 1. Overview

Combat is the core gameplay loop. The system spans five files:

- **`CombatEngine.java`** (~680 lines) — **all** combat logic: turn order, attacks, spell dispatch, buff/debuff tracking, victory/defeat, rewards, and the combat log.
- **`CombatOverlay.java`** (~766 lines) — rendering, entry animation, keyboard/mouse input, and the public API that `Spell.executeCombat()` calls. Every game-logic method on it is a one-line delegate to the engine.
- **`SpellEffectRenderer.java`** (~1150 lines) — the animated spell effects painted inside the arena.
- **`EncounterController.java`** (~130 lines) — random encounter rolls on the overworld; derives monster level from tile spawn difficulty.
- **`LootGenerator.java`** (~129 lines) — weighted random item drops from `ItemRegistry` based on monster level or dungeon depth.

Combat renders directly inside `GamePanel` (no JDialog). `GamePanel` calls `overlay.paint(g2, w, h)` each frame and routes keyboard input via `handleKey()`.

All numeric constants marked *(balance)* below are loaded from `data/balance.json` via `BalanceConfig`; the values quoted are the current ones.

---

## 2. Encounter Triggering (`EncounterController`)

Called after each overworld move. Tiles whose `isSafeZone()` flag is set are skipped entirely. Dungeon encounters are handled by `DungeonController` — see §12.

### Encounter Rate Formula

```
baseRate        = 0.065 × tile.encounterRateMod            (balance: encounter.baseRate)
encounterChance = baseRate × (1.0 + spawnDifficulty × 0.006)
                             ^^^ difficultyBase   ^^^^^ difficultyPerPoint
```

- `spawnDifficulty` is a 0–99 value painted per-tile in RetroForge.
- At difficulty 0: chance = 6.5% × tileMod. At difficulty 99: ≈ 10.3% × tileMod.
- Tile modifier examples: Dirt Road = 0.5, Tall Grass = 2.0, Flower Field = 0.3.

### Monster Level Derivation

```
monsterLevel = 1 + (spawnDifficulty × 49) / 99
```

Linear mapping: difficulty 0 → level 1, difficulty 99 → level 50.

Monster selection uses `MonsterRegistry.getRandomMonster(level, weights)` — see `Monsters.md` §3 for the 75%/50%/factory algorithm.

### Aquatic Boost

If any of the four cardinal neighbours is a liquid tile, every aquatic, non-friendly monster within ±3 levels has its spawn weight overwritten with a boost: 1 adjacent water tile = 3, 2 = 5, 3 = 8, 4 = 12.

---

## 3. Turn Flow

### 3.1 Combat Start

1. `CombatOverlay.start(monster, callback, noEscape)` called by `GamePanel`; it forwards to `CombatEngine.start()`.
2. Monster max HP snapshotted; the log is cleared; `resetCombatBuffs()` clears every *combat-scoped* counter (see §4).
3. **Friendly monsters** (`isFriendly()` or `MonsterType.FRIENDLY`) skip combat entirely and hand out one of three gifts, then depart:
   - `2 + rand(monsterLevel)` gold, or
   - `5 + monsterLevel × 2` HP healed, or
   - `5 + monsterLevel × 3` XP (can level you up).
4. **Monster reaction roll**: `3d6 + min(3, (Charisma − 10) / 2)`
   - ≥ 15 *(balance: friendlyReactionThreshold)* **and** monster level < 40: flee chance = `max(10, 60 − (monsterLevel − playerLevel) × 5)` percent.
     - Equal level: 60%. Monster 5 levels higher: 35%. Monster 10+ higher: 10%.
     - A reaction flee ends the fight with **no XP and no gold**.
     - Boss monsters (level ≥ 40) never flee on reaction.
   - ≤ 6 *(balance: aggressiveReactionThreshold)*: monster is aggressive (flavour text only).
   - Otherwise: standard encounter message.

### 3.2 Player Turn (on pressing **A**)

Processed in order:

1. **Player poison tick** — `1 + rand(0,2)` damage *(balance: playerPoisonMin 1, playerPoisonRange 3)*; decrement counter. Can kill you before you swing.
2. **Sleep / stun gate** (`isIncapacitated()`) — lose the turn, the monster acts. Pressing **C** runs this gate *before* the spellbook opens, so a lost turn never also costs a spell slot.
3. **Blind tick** — if blinded, charge `−4` to-hit *(balance: blindnessToHitPenalty)* for the whole action and decrement the counter once (not once per swing).
4. **First swing** — `playerSwing()`. If Invisibility is active it is consumed here and the swing cannot miss, but it is otherwise an ordinary swing (it still rolls for a critical and still gets the Haste follow-up).
5. **Haste follow-up** — if the Haste buff is active, a second full `playerSwing()`, with the same to-hit and crit rules.
6. Monster death at any point → `victory()`; otherwise → `monsterTurn()`.

**`playerSwing()`**

```
rawRoll = d20
crit    = rawRoll >= 19                                   (balance: critThreshold)
toHit   = rawRoll + playerLevel + (Dex − 10)/2 + 3 + hitBonus − blindPenalty
                                                  ^ balance: baseToHitBonus
hit     = autoHit || toHit >= monster.getAC()

dmg     = player.getDamage() + player.getDmgBonus()
if autoHit: dmg += dmg × 50 / 100                          // struck from hiding
if crit:    dmg ×= 2
dmg     = max(1, dmg)
```

`autoHit` is set only by Invisibility. The sneak bonus and the crit multiply stack.

### 3.3 Monster Turn

Processed in order:

1. **Poison tick** — `poisonPower + rand(0,3)` damage *(balance: monsterPoisonRange 4)*, where `poisonPower` defaults to 2 *(balance: monsterPoisonMin)* and is raised by Cloudkill. Ticks first so it keeps burning through sleep and charm.
2. **Sleep** — decrement counter, skip the turn.
3. **Charm** — decrement counter; skip the turn while still charmed, otherwise log that the charm broke and carry on.
4. **Stun** — decrement counter, skip the turn.
5. **Fear** — decrement counter; 50% chance the monster bolts. A routed monster pays **25%** of its XP and gold (`ROUT_REWARD_PCT`) through the normal victory path, logged as "is routed!".
6. **Blind** — charge `−4` to-hit for this turn and decrement the counter.
7. **Haste** — if the monster's own Haste is running, decrement it. While it is up, casting a spell does **not** consume the melee attack.
8. **Spell casting** — if `canCastSpells()` and `random×100 < spellCastChance`:
   - Power: `spellPower + rand(0, spellPower/2)`, min 1.
   - Player spell resistance rolls first: `player.getTotalSpellResist()` percent (0–50%). On a resist the spell fizzles with no effect (see §5).
   - Casting normally *is* the monster's whole turn; it falls through to melee only while hasted.
9. **Melee attack**:
   - To-hit: `d20 + monsterLevel − blindPenalty`.
   - Target: `player.getAC() + player.getAcBonus()` (ascending AC — higher is harder to hit).
   - Raw damage: `baseDmg/2 + 1 + rand(0, baseDmg)`. `Player.takeDamage()` then subtracts `getDamageReduction()` = `max(0, (CON−10)/3)`, floored at 1. The log prints the HP actually lost, so reduction is applied exactly once.
   - **Undead level drain**: `MonsterType.UNDEAD` has a 15% chance per landed melee hit to drain `max(50, xpToNextLevel/4)` XP.
10. Player death → `checkPlayerDeath()` (§7).

### 3.4 Flee (on pressing **F**)

- Blocked entirely when the fight is flagged `unfleeable` (endgame boss fights): "There is no escape from the gods!"
- Base chance: `60 + (Charisma − 10) × 3` percent.
- **Dungeon penalty**: subtract `15 + currentDepth / 5`, clamped to a minimum of 5%.
  - CHA 10 at depth 1: 45%. Depth 25: 40%. Depth 50: 35%.
- Success: combat ends immediately, no XP/gold, overlay closes.
- Failure: the monster gets a free turn.
- `Spell`'s Sanctuary uses `guaranteedFlee()`, which always succeeds.

### 3.5 Use an Item (on pressing **U**, or **I**)

The fourth combat action. It reuses `InventoryOverlay` rather than adding a second item UI.

1. The sleep/stun gate (`isIncapacitated()`) runs **before** the pack opens, exactly as it does for the spellbook — an incapacitated player loses the turn instead of getting a free item use.
2. `GamePanel.openInventory()` opens the normal inventory. It paints after the combat card, so it lands on top; `CombatOverlay` sets an internal `itemMode` flag.
3. `Retroquest` routes keys to combat *before* the inventory, so while the pack is open `CombatOverlay.handleKey()` forwards every key straight to `handleInventoryKey()`. Mouse clicks are swallowed while the pack is open (there is no public click relay for the inventory), so item selection is keyboard-driven in combat.
4. Using a consumable closes the pack and calls `CombatOverlay.onCombatItemUsed()`, which spends the round: stats refresh, player poison ticks, then `monsterTurn()` — the same cost as a swing.
5. Closing the pack without using anything clears `itemMode` and costs **nothing**; the player still has their action.
6. A **wand** goes through `executeCombatSpell()` instead, which already spends the round, so it must not also report an item use.

### 3.6 Spellcasting (on pressing **C**)

Opens `SpellbookOverlay` (after the sleep/stun gate). The selected spell calls `executeCombatSpell(spell)` on the overlay, which delegates to `CombatEngine`:

1. Reject the spell if `spell.getLevel() > player.getLevel()`.
2. Tick player poison (which can kill first).
3. Trigger the spell animation, then run `Spell.executeCombat(player, monster, overlay)`.
4. If it returns `true` and the monster is alive and the fight is not finished, run `monsterTurn()`. Returning `false` skips the monster's turn — used by Time Stop and by every outcome that already ended the fight.

> For the complete 37-spell table with effects and resistance behaviour, see `SpellSystem.md`.

---

## 4. Buff & Debuff System

Two different lifetimes are in play, and they are deliberately different:

- **Combat-scoped counters** live on `CombatEngine` and are cleared by `resetCombatBuffs()` at the start and end of every fight: monster poison/stun/fear/blind, monster haste, player poison/sleep/stun/blind, and Invisibility.
- **Player spell buffs** live on `Player` as **step counters**, decremented once per map move by `Player.move()`. They deliberately survive the fight they were cast in and expire while walking — a Holy Armor cast in one ambush is still up for the next one. Combat turns do *not* tick them.

The one bridge between the two: leftover combat poison is converted on exit into overworld poison at roughly 5 steps per remaining combat turn.

### Player Buffs

| Buff | Effect | Duration (steps) | Cast by |
|------|--------|------------------|---------|
| Shield | +2 AC | 35 | Shield |
| Protection from Evil | +3 AC | 35 | Protection from Evil |
| Prayer | +2 AC **and** +2 to-hit | 40 | Prayer |
| Holy Armor | +5 AC | 50 | Holy Armor, Sanctuary |
| Bless | +2 damage | 30 | Bless |
| Haste | An extra full melee swing each round | 25 | Haste |
| Resist Elements | Halves incoming spell **and** breath damage | 35 | Resist Elements |
| Invisibility | Next swing cannot miss and deals +50% damage; consumed on use | combat-scoped | Invisibility |

- The four AC wards **do not stack** — `Player.getAcBonus()` returns the best active one (max +5).
- `getHitBonus()` is +2 from Prayer only. `getDmgBonus()` is +2 from Bless only.
- Each spell logs the bonus and remaining steps it actually produced by reading them back off `Player`, so the combat log cannot drift from the model.

### Player Debuffs (set by monster spells)

| Debuff | Duration | Effect |
|--------|----------|--------|
| Poison | 3 + rand(0,1) turns | 1–3 damage/turn; carries over as step-based overworld poison. Cured by Antidote, Dispel Magic, Restoration, Sanctuary, or inn rest. |
| Sleep | 2 turns (1 from monster Fear) | Skip turn |
| Stun | 1 turn | Skip turn |
| Blind | 2 + rand(0,1) turns | −4 to-hit |

### Monster Debuffs (set by player spells)

| Debuff | Duration | Effect | Source |
|--------|----------|--------|--------|
| Poison | 4–5 rounds | `poisonPower + rand(0,3)` per round; keeps burning through sleep and charm | Cloudkill |
| Stun | 1 round | Skip turn | Ice Storm (30%, resistible) |
| Fear | 2 rounds | 50% chance per round to bolt; a rout pays 25% rewards | Turn Undead (undead that survive the destroy roll) |
| Blind | 2 rounds | −4 to-hit | Holy Word (non-evil targets, resistible) |
| Sleep | 2 + level/3 rounds | Skip turn | Sleep |
| Sleep (bind) | 2 + level/5 rounds | Skip turn | Entangle |
| Charm | 3 + level/5 rounds | Stands idle; shakes it off when the counter runs out | Charm Monster |

### Monster Buffs

| Buff | Duration | Effect |
|------|----------|--------|
| Hasted | 3 rounds | Casting a spell no longer costs the melee attack — the monster casts *and* swings in the same round. Set by the monster's own Haste spell. |

> No monster in `data/monsters.json` currently lists `"Haste"` in its `spellNames`, and `MonsterFactory` never assigns it, so this buff is reachable only once content grants it.

---

## 5. Monster Spell Effects

When a monster casts, it picks a random name from its `spellNames` list. Power = `spellPower + rand(0, spellPower/2)`, min 1.

| Spell | Effect |
|-------|--------|
| Magic Missile, Fireball, Lightning Bolt, Ice Storm, Flame Strike | Direct damage (halved by Resist Elements) |
| Fire Breath | `power × 1.5` damage (halved by Resist Elements) |
| Sleep | Player sleeps 2 turns |
| Poison | Player poisoned 3–4 turns |
| Fear | Player frozen 1 turn (implemented as a 1-turn sleep) |
| Drain | Removes `min(gold, power × 2)` gold |
| Stun | Player stunned 1 turn |
| Blind | Player blinded 2–3 turns (−4 to-hit) |
| Haste | Monster gains the Hasted buff for 3 rounds |
| *anything else* | Direct damage (fallback, halved by Resist Elements) |

`MonsterFactory` hands out `Magic Missile`, `Sleep`, `Fireball`, `Fear`, `Blind`, `Stun`, `Drain`, `Poison` and `Fire Breath` depending on the monster's type and level.

### Player Spell Resistance (monster spells → player)

Binary: the spell either lands in full or fizzles completely.

```
wisBase   = max(0, (Wisdom − 10) / 2 × 5)          // 0–25% for WIS 10–20
itemBonus = sum of SPELL_RESIST effects on equipped gear + boon bonuses
total     = min(50, wisBase + itemBonus)
```

Notable spell-resist items: Helm of Wisdom (+15%), Amulet of Warding (+15%), Shield of Reflection (+20%), Amulet of the Archmage (+30%).

The `spell` sound plays **before** the resist roll, so a resisted cast is still audible.

### Monster Spell Resistance (player spells → monster)

`Monster.spellResistance` is clamped to 0–40 and is used two different ways:

- **Damage** is *blunted*, never cancelled — `Spell.afterResistance()` removes `spellResistance / 2` percent of the damage (`spellResistance / 4` for the piercing Lightning Bolt / Chain Lightning), floored at 1. So a 40%-resistant monster takes 20% less from a Fireball and 10% less from Chain Lightning, and the log appends "(its wards blunt the blow)".
- **Status effects** — sleep, charm, the Entangle bind, the Ice Storm freeze, the Holy Word blind, and the Death Spell kill roll — still use the all-or-nothing `resisted()` roll, because a monster is either asleep or it is not.

This is what keeps the tier ladder honest: under the old all-or-nothing model, half of a level-6 Meteor Swarm simply vanished while the unresistable Cone of Cold (level 4) always landed in full, so the cheaper spell won. Cone of Cold and Magic Missile still ignore resistance entirely — that is their whole selling point.

`Dispel Magic` halves the monster's resistance outright for the rest of the fight and purges the player's poison/sleep/stun/blind.

---

## 6. Victory & Loot

### Victory Sequence (`CombatEngine.victory()`)

1. Base gold = `monster.getGoldReward()`.
2. XP = `monster.getXPValue() × (1 + max(0, Charisma − 10) / 100)` — **1% bonus XP per point of CHA above 10**, logged separately when non-zero.
3. `player.addXP()`; a level-up logs the HP gain, any newly unlocked spell tier, and refreshed spell slots.
4. Progress `KILL`-type quests.
5. **7.35% chance** *(balance: lootDropChance)* to drop loot:
   - Item from `LootGenerator.getRandomLootForMonster(monsterLevel, playerLevel)`.
   - Bonus gold: `1 + rand(0,1)` — i.e. **1 or 2 gold**.
   - If the inventory is full the item is lost (logged); the bonus gold is still paid.
6. Add the gold, refresh the stats panel, set `finished = true` — "Press any key to continue".
7. Fire the `OnCombatEnd` callback exactly once through `endCombat()`.

Spells that end a fight without a real kill scale the reward down first, via `Spell.reduceRewards()`:

| Outcome | Reward |
|---------|--------|
| Turn Undead destroys an undead / Power Word Kill | 50% |
| Divine Intervention routs the monster | 25% |
| A monster routed by Fear | 25% |
| Charm Monster wanders it off / Sanctuary / normal Flee / reaction flee | 0% |

### Loot Generation (`LootGenerator`)

All static methods. Both public methods require a `playerLevel` parameter. Never returns null.

**Tier mapping (capped at player level):**
- From dungeon depth: `baseTier = max(1, (depth + 4) / 5)`, capped at `playerLevel`.
- From monster level: `baseTier = max(1, (level + 1) / 2)`, capped at `playerLevel`.

**Uniform tier distribution:** a random tier is chosen uniformly from 1 to `min(baseTier, playerLevel)`. A level-5 player gets an equal 20% chance per tier 1–5 regardless of dungeon depth.

**Selection:**
1. Filter `ItemRegistry` for `isLootable()` items matching the chosen tier exactly.
2. If empty, widen to `[chosenTier − 1, chosenTier + 1]` (capped at maxTier).
3. If still empty, fall back to all lootable items with `tier ≤ maxTier`.
4. Last resort: any lootable item, then a plain Healing Potion.
5. Weighted random pick using `Rarity.getWeight()`.

**High-tier weight boost (chosen tier ≥ 5):**

| Rarity | Multiplier |
|--------|------------|
| RARE | ×2 |
| EPIC | ×4 |
| LEGENDARY | ×6 |
| Others | ×1 |

---

## 7. Death Handling

`checkPlayerDeath()` runs after any damage to the player:

1. HP > 0: no action.
2. **Resurrection ward** charged: consume it, restore HP to 50% of max, log it, and combat continues.
3. Otherwise: play the death sound, clear lingering combat poison, set `finished = true`, close the overlay, then call `endCombat(false, …)` **before** starting the death animation — so multi-phase boss fights and ambush handlers get their callback and can unwind cleanly.

`endCombat()` is the single exit point. It sets `finished = true` first (a monster that flees used to leave the overlay live, letting the fight continue and the reward callback fire twice), converts leftover combat poison into overworld poison, clears the combat-scoped counters, fires the callback, and closes the overlay on any non-victory ending.

---

## 8. Combat UI Layout

```
┌──────────────────────────────────────────────┐
│  ⚔ COMBAT  ·  location label                │  title bar (34px)
├───────────────────┬──────────────────────────┤
│  [player sprite]  │  [monster sprite]        │  arena (180px)
│  ██████ HP        │  ████░░ HP               │  + HP bars
├───────────────────┴──────────────────────────┤
│  buff/debuff badges                          │  buff strip (22px)
├──────────────────────────────────────────────┤
│  scrolling battle log (age-faded)            │  log area
├──────────────────────────────────────────────┤
│  [A] Attack  [C] Spell  [U] Use  [F] Flee    │  key bar (38px)
└──────────────────────────────────────────────┘
```

- Card: `min(620, panelW − 40)` × `min(430, panelH − 40)`, centered, sliding up with a 350 ms ease-out.
- Player badges are drawn left-aligned, monster badges right-aligned; each side stops drawing at the centre line.
- HP bar colour: green (>60%), yellow (30–60%), red (<30%).
- Hit flash: red overlay for 220 ms on damage.
- Log entries fade over 8 seconds (minimum alpha 60); at most 60 entries are kept.
- Mouse clicks on the action buttons route through the same handler as the keys.

---

## 9. Interfaces & Dependencies

- `CombatOverlay` → `CombatEngine`, `SpellEffectRenderer`, `ImageAssetRegistry`, `Retroquest`
- `CombatEngine` → `Retroquest` (player, stats panel, message log, dungeon depth), `Monster`, `Player`, `Spell`, `LootGenerator`, `SoundManager`, `BalanceConfig`
- `Spell.executeCombat()` → `CombatOverlay`'s public delegate API (`setProtEv`, `setBless`, `setPrayer`, `setHolyArmor`, `setShield`, `setHaste`, `setElemResist`, `setInvisible`, `setPoisonTurns`, `setStunTurns`, `setFearTurns`, `setBlindTurns`, the player-debuff setters, `callVictory`, `callFlee`, and the log helpers)
- `EncounterController` → `Retroquest`, `TileRegistry`, `MonsterRegistry`, `GamePanel`
- `LootGenerator` → `ItemRegistry`, `Item`, `Rarity`
- `InventoryOverlay` ↔ `CombatOverlay`: `openInventory()` / `handleInventoryKey()` out, `getCombatOverlay().onCombatItemUsed()` back in (and `executeCombatSpell()` for wands)
- `OnCombatEnd` callback: `onEnd(boolean playerWon, int goldGained, String lootName, boolean leveledUp, int newLevel)`

There are no magic-number dispatch methods on the API: each ward has its own named setter, so a spell cannot advertise a bonus that the player model does not grant.

---

## 10. Combat Sound Effects

| Event | Sound Key | Location |
|-------|-----------|----------|
| Player attacks (swing) | `playAttack()` | `playerAttack()` |
| Normal hit (player → monster) | `hit` | `playerSwing()` |
| Critical hit | `explosion` | `playerSwing()` |
| Monster hits player | `playHit()` | `monsterTurn()` |
| Flee success | `flee` | `attemptFlee()`, `guaranteedFlee()` |
| Flee failure | `hurt` | `attemptFlee()` |
| Level up | `levelup` | `victory()`, friendly XP gift |
| Loot drop / friendly gold gift | `coin` | `victory()`, `handleFriendlyEncounter()` |
| Player death | `death` | `checkPlayerDeath()` |
| Resurrection ward triggers | `heal` | `checkPlayerDeath()` |
| Monster casts a spell | `spell` | `castMonsterSpell()` (fires **before** the resist roll) |
| Player poisoned by a spell | `hurt` | `castMonsterSpell()` Poison case |
| Player stunned by a spell | `hurt` | `castMonsterSpell()` Stun case |
| Victory | `playVictory()` | `victory()` |

Items used through the **[U]** action keep the sounds `InventoryOverlay` already plays (`heal` for a potion or antidote, `spellcast` for a wand).

Player spells pick their own sound in `Spell.playSpellSound()` (`fireball`, `lightning`, `icestorm`, `missile`, `heal`, `teleport`, `explosion`, or `spell`).

---

## 11. Spell Visual Effects

`SpellEffectRenderer` paints animated spell effects in the combat arena. Each effect has a dedicated paint method animated over a normalized `t` (0→1) timeline. Effects are triggered by `CombatEngine` and rendered during `CombatOverlay.paintArena()`.

### Effect Types (18)

| EffectType | Duration | Description |
|---|---|---|
| FIREBALL | 700ms | Orange ball travels to target → explosion with sparks at 45° intervals |
| LIGHTNING | 700ms | 8-segment jagged bolt with 3-pass glow (blue→cyan→white) + 3 branches |
| ICE | 700ms | 3 ice shards fly in cluster → 6-point snowflake burst |
| MISSILE | 400ms | Purple orb with trail → impact ring on arrival |
| HEAL | 700ms | 8 rising sparkles (HSB rainbow) + pulsing green cross |
| SLEEP | 700ms | Soft blue orb with arc trajectory → 3 floating "Z" letters |
| DEATH | 700ms | Dark vignette edges → pulsing skull (☠) → red fade-out |
| BUFF | 700ms | 6 golden orbs orbiting with sparkle crosses |
| POISON | 700ms | 5 green bubbles rising in sequence |
| FIRE_BREATH | 900ms | 3-phase: mouth glow → wide flame cone with 12 streams + ember particles → smoke dissipation |
| DRAIN | 900ms | 5 dark bézier tendrils arcing source→target + ghostly particles flowing back |
| GIFT | 700ms | Amber glow + 10 golden 4-pointed star sparkles + rotating diamond |
| HOLY | 700ms | Golden light pillar descends → 12-ray radiant burst → drifting golden motes |
| ENTANGLE | 700ms | 7 green vines rise from below with sway + leaves → constriction ring tightens |
| TIME_STOP | 800ms | Clock face appears → hands spin rapidly → blue-white flash → shatter cracks |
| FEAR | 700ms | Dark pulsing overlay + 8 shadow wisps converge + ghostly red eyes flash |
| WISH | 800ms | 12 prismatic motes spiral inward → 3 rainbow rings expand → sparkle fade |
| METEOR_SWARM | 900ms | 5 staggered fireballs from arena top → individual explosions + red tint pulses |

### Spell → Effect Mapping

| Spell | Effect |
|---|---|
| Fireball | FIREBALL |
| Meteor Swarm | METEOR_SWARM |
| Flame Strike | HOLY |
| Fire Breath (monster) | FIRE_BREATH |
| Lightning Bolt, Chain Lightning | LIGHTNING |
| Ice Storm, Cone of Cold | ICE |
| Magic Missile | MISSILE |
| Sleep, Charm Monster | SLEEP |
| Death Spell, Power Word Kill, Cloudkill | DEATH |
| Poison, Drain (monster) | POISON |
| Fear (monster) | FEAR |
| Holy Word, Turn Undead, Divine Intervention | HOLY |
| Entangle | ENTANGLE |
| Time Stop | TIME_STOP |
| Wish | WISH |
| Cure spells, Heal, Restoration | HEAL |
| Shield, Dispel Magic, Resist Elements, Sanctuary, Haste, Prayer, Bless, Holy Armor, Invisibility, Protection from Evil | BUFF |
| Default (unknown spells) | MISSILE |

`GIFT` is not driven by a spell name — `CombatEngine` triggers it directly for friendly encounters, and `DRAIN` for the undead XP drain. The `effectFromPlayer` flag sets direction: left→right for player spells, right→left for monster spells.

---

## 12. Dungeon Encounter Rate

Dungeon encounters are handled by `DungeonController.checkDungeonEncounter()`, not `EncounterController`. Rolls are skipped while a message-log prompt is active.

### Formula

```
tileMod = dungeonTileEncounterMod(special)
rate    = min(0.15, 0.10 × tileMod × (0.8 + currentDepth × 0.008))
```

Monster level is `clamp(1, 50, currentDepth + rand(−2, +2))`.

### Tile Modifiers

| Special Char | Tile | Modifier |
|---|---|---|
| `I` | Inn | 0.3× (safe zone) |
| `s` | Stairway | 0.5× |
| `A`, `f`, `H` | Altar, Fountain, Throne | 0.7× (sacred) |
| `P`, `t`, `g` | Pit, Teleporter, Glowing Cube | 1.2× (dangerous) |
| default | Normal corridor | 1.0× |

### Effective Rates

- Depth 1 corridor: ≈8%. Depth 25: ≈10%. Depth 50: ≈12%. Capped at 15%.
- Near inns: ≈3% at any depth.

---

## 13. Known Content Gaps

These are data gaps, not code bugs — the mechanics exist and work, nothing grants them:

- **Scry, Wish, Time Stop, Divine Intervention** have no scroll in `data/items.json`, no `spellRewardId` in `data/quests.json`, and no NPC `LEARN_SPELL` dialogue action. `SpellEffectRenderer` carries bespoke `TIME_STOP` and `WISH` animations that no player can currently trigger.
- **Entangle**'s quest (`rootspeaker_entangle`, giver *Rootspeaker Thenna*) exists in `quests.json` and the NPC exists on the map, but no dialogue tree contains a `GIVE_QUEST` action targeting that quest id, so the quest can never be started.
- No monster casts **Haste**, so the monster Haste buff and its "Hasted" badge never appear in play.

---

## 14. Boss Fights

Three shapes, in increasing order of how much code each one is.

### Multi-phase fights — `BossFightCoordinator`

Chains sequential combats with a typewriter cinematic between phases and an optional quarter-HP
heal. `addPhase(monster, transitionText, healBetween)` then `start(onComplete)`; `onComplete`
fires **only** when the final phase is won — fleeing or dying just stops the coordinator.

Always build phase monsters with the seven-argument `Monster(name, level, hp, damage, gold, xp, ac)`
constructor. The short one derives level from `hp/8`, which once turned a 65 HP shrine boss into a
level-8 monster with a level-8 monster's to-hit.

### Trials — hand-written, four of them

`pressure_temple` L3, `archive_of_tears` L3, `iron_pit` L3, `cradle_of_shards` L8. Each is a method
in `DungeonController` because each ends in a bespoke moral choice that awards favour, keys and
boons down different branches. Each claims its level's `'A'` altar with an early `return` in
`handleAltar()`.

### Bottom-of-dungeon bosses — data, seven of them

`data/dungeon_bosses.json`, read by `DungeonBossRegistry`, covering the seven authored dungeons
that used to end in an ordinary room: `ember_caverns`, `storm_spire`, `rootvault`,
`boneyard_trench`, `leviathan_eye`, `forgotten_city`, `war_beneath`.

These are data rather than seven more branches because they all end the same way — the place
explains itself — and that is text, not control flow. One `handleDungeonBoss` method runs any of
them:

```
altar 'A' → registry hit → MessageLog.prompt(intro)
          → BossFightCoordinator phases
          → flag + favour + RevelationOverlay card
```

The trigger is the level's existing altar; all seven already had one, so no map was edited. The
registry is consulted **after** the four trials, so a dungeon can never be claimed twice —
`tools/boss-audit.js` fails the build if a data boss is ever placed on a trial's level, on a level
that is not its dungeon's last, or on a level whose altars are walled off from the entry.

**The reward is the revelation, not loot.** No unique drop, and deliberately no boon: all seven
boons are already granted by their island's trial quest, so granting one here would either be a
no-op or would steal that quest's moment. Favour is awarded instead — it is the quantity the
ending is settled on, and the one currency a dungeon can hand out without touching the loot curve.
A `boss_<dungeon>` player flag makes the altar go quiet afterwards.

To read a card without fighting down to it:

```
RetroRecorder --scene dungeon --dungeon storm_spire --overlay revelation --out DIR
```

See `docs/UIOverlayArchitecture.md` for `RevelationOverlay` itself, which is the presentation half
of `DivineAudienceOverlay` lifted out so both screens share one typewriter.

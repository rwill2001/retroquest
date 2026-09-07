# Retroquest Design Document
**Game Title:** Retroquest – The Unbound: Shattered Dreams of Aqualon
**Engine:** Custom Java CRPG
**Target Length:** 35–45 hours main path | 65–80 hours completionist
**Version:** 0.9.0 – March 2026
**Author:** rwill2001

---

## Engine Capability Reference

This section is maintained to keep design honest. Features listed here as ✅ can be used
freely. Features listed as ❌ require new engine work before they can appear in a quest.
Update this table before designing new island content.

| Feature | Status | Notes |
|---|---|---|
| KILL / COLLECT / DELIVER / EXPLORE quest types | ✅ | All functional, tracked via `progressQuest()` |
| NPC quest giving & turn-in | ✅ | `QUESTGIVER`, `HEALER`, any NPC type works |
| Binary moral choice via prompt | ✅ | `messageLog.prompt()` with two options |
| Dungeon generation (depth 1–50) | ✅ | `Dungeon.generate(depth)` |
| Altar / fountain / cube / throne / inn specials | ✅ | All handled in `DungeonController` |
| Boss fight (named Monster + startCombat) | ✅ | See Throne Guard / Corrupted Moon Guardian pattern |
| `Item.Type.KEY` inventory slot | ✅ | KEY items display in StatsPanel KEYS section |
| Portal tile step-effect (island transition stub) | ✅ | `"island_portal"` tile effect type in TileRegistry |
| Overworld teleporter tiles (O) | ✅ | `OverworldTeleporter` records in .rfmap files |
| Auto-give quest on new game | ✅ | `Retroquest.startGame()` checks empty quest log |
| Random encounter roll | ✅ | `EncounterController.checkForRandomEncounter()` |
| Shop / inn / NPC dialog | ✅ | Full overlay system exists |
| Loot drops from chests + floor finds | ✅ | `LootGenerator`, `checkGroundLoot()` |
| Collect quest: item sourced from loot/drops | ✅ | Add `lootable:true` to item JSON |
| Timed dungeon events (water rising, etc.) | ❌ | No timer subsystem — **Island 3+ target** |
| Crate-push / pressure-plate puzzles | ❌ | No physics/interaction layer |
| Rune-matching / symbol puzzles | ❌ | No puzzle UI |
| Divine favor system (per-god reputation) | ✅ | `int[7] favorScores` on Player; `addFavor(God, int)` + `getFavor(God)` |
| Overworld item spawn at specific (x,y) | ❌ | Items only come from drops/chests/NPCs |
| Permanent player boons (altar grace, etc.) | ✅ | `Set<String> acquiredBoons` on Player; `Boon` enum with stat bonuses; shown in StatsPanel |
| Escort NPC (moving NPC companion) | ❌ | NPCs are stationary |
| Multi-day / in-game calendar | ❌ | No time system |
| Disguise / stealth mechanics | ❌ | Not in engine |
| Authored multi-level dungeons | ✅ | Storm Spire (5 levels), Rootvault, Archive of Tears, Forgotten City, Pressure Temple, War Beneath, Iron Pit, Cradle of Shards |
| Dungeon specials: spinner, chute, riddle door | ✅ | Chars 'n', 'c', 'q' — handled by DungeonController |
| Dark Zone special | ⚠️ Partial | Char 'k' — rendered in WireframeDungeonRenderer but no movement/minimap handler |
| Wireframe dungeon renderer | ✅ | Storm-themed color palettes per island in WireframeDungeonRenderer |
| Key ring (permanent key storage) | ✅ | Player.keyRing — keys never lost, separate from inventory |

---

## 1. Overall Story Summary

Long ago the World-Serpent Aqualon dreamed the islands of Aqualonia into existence. Seven of
its own dreams betrayed it, shattered their father into a thousand shards, and have been waging
war ever since. Mortals became their pawns and the ocean is rising faster than ever.

You are **the Unbound** — the only soul born under the rare Null Alignment. None of the gods
can read your mind or control you directly. That makes you the most valuable piece on the
board… and every single god wants you.

The game is structured as **7 islands**, each ruled by one god. You start on a tiny tutorial
island and progressively unlock larger, more dangerous islands by earning **Keys** from the
ruling deity.

---

## 2. Island Progression Overview

| Island | Name | God | Levels | Towns | Dungeons |
|---|---|---|---|---|---|
| 1 | The Awakening Isle (Lirandel) | Lirandel | 1–3 | 1 | 1 |
| 2 | The Forged Isles (Pyralis) | Pyralis | 6–10 | 3 | 2 |
| 3 | The Storm Archipelago (Zephyrion) | Zephyrion | 8–11 | 4 | 1 (Storm Spire, 5 levels) |
| 4 | The Verdant Mangroves (Sylvandar) | Sylvandar | 12–15 | 3 | 2 |
| 5 | The Abyssal Depths (Thalorax) | Thalorax | 16–19 | 3 | 3 |
| 6 | The Shadow Trenches (Umbryn) | Umbryn | 20–23 | 3 | 2 |
| 7 | The Golden War Isles (Bellorak) | Bellorak | 24–27 | 4 | 3 |
| — | The Cradle of Shards (World Trench) | All | 28+ | 0 | 1 mega |

> **Design note — divine favor:** ~~Not yet implemented.~~ **Done as of 2026-03-12.**
> `God` enum (7 values, `.index` 0–6) + `int[7] favorScores` on `Player` (neutral = 50,
> clamped 0–100). API: `player.addFavor(God.LIRANDEL, 20)` / `player.getFavor(God.PYRALIS)`.
> Old saves without favor data default to neutral (50) on first access.

> **Design note — permanent boons:** ~~Not yet implemented.~~ **Done as of 2026-03-12.**
> `Boon` enum with stat fields (`maxHpBonus`, `damageBonus`, `acBonus`, `spellResistBonus`,
> `altarGrace`) + `Set<String> acquiredBoons` on `Player` (persists in save data).
> API: `player.grantBoon(Boon.MOONBLESSED)` / `player.hasBoon(Boon.PYRALIS_TEMPER)`.
> Stat methods (`getMaxHp`, `getDamage`, `getAC`, `getTotalSpellResist`) sum boon bonuses
> automatically. StatsPanel shows a BOONS section when any boon is held.
> Old saves without boon data default to an empty set on first access.

---

## 3. Island 1 – The Awakening Isle (Lirandel)

> **Status: IMPLEMENTED** — Maps, quests, monsters, items, and NPCs are all in place.
> This section reflects the built state, not the original draft.

**Target playtime:** 2.5 – 3.5 hours (main path + light exploration)
**Level range:** 1–3
**Map size:** 80 × 50 tiles (overworld), 20 × 20 (Moonhaven interior)
**Danger level:** 1–2 on beach/plains, 3 dungeon entrance
**Theme:** Misty silver-lit island, glowing moonbloom flowers, half-sunken marble ruins.

### 3.1 Overworld Layout (lirandel.rfmap)

```
North (y=0–7)   : Ocean → Solid mountain wall (impassable cliffs)
                  Mountain pass at x=34–45 allows view but no shortcut
NW (y=10–30)    : Open plains, starting beach, cobblestone path network
Center-West     : Moonhaven town entrance 'E' at (20, 32)
South (y=38–47) : Dreamwake Caverns entrance 'D' at (24, 43)
East (x=48–74)  : Mixed then dense silver forest (Tree tiles)
SE (x=57–73, y=38–47): Cove — water inlet (Corrupted Crab territory)
Border          : Ocean on all sides
```

**Player start:** (12, 35) — open grass clearing west of Moonhaven.

**Cobblestone roads:**
- West road from start (12,35) east to town path
- Town path north to 'E' tile
- South road from town toward dungeon entrance
- Short east road from town plaza

### 3.2 Moonhaven (moonhaven.rfmap — 20×20)

Four buildings arranged in a 2×2 grid with open corridors between them:

```
[Inn — NW quad]      [Moon Temple — NE quad]   y=2..6
[Market Stall — SW]  [Healer's Cottage — SE]   y=9..13
        ↕                      ↕
   Main street (y=7–8, y=14–18) with Guard at (10,15)
        Exit door (d) at south wall center (10,19)
```

**Player enters at (10, 13)** — the corridor between the two lower buildings.

| NPC | Type | Position | Quest |
|---|---|---|---|
| Innkeeper Bram | INNKEEPER | (5, 4) | Rest 15g — full heal + food |
| Lirandel's Acolyte | QUESTGIVER | (14, 4) | Offers `rising_tide` (already auto-given) |
| Healer Mira | HEALER | (14, 11) | Gives + accepts `moonbloom_collection` |
| Town Guard | GUARD | (10, 15) | Flavor dialog |

### 3.3 Dreamwake Caverns

Uses the procedural dungeon generator limited to **depths 1–3**.

| Depth | Altar behavior | Encounter rate |
|---|---|---|
| 1 | Corrupted Shrine — destroy for quest progress + XP | 15% per step |
| 2 | Corrupted Shrine — destroy for quest progress + XP | 15% per step |
| 3 | Corrupted Moon Guardian boss fight | — |

Monsters scale with depth ±2. Primary monsters at these depths:
- Corrupted Crab (L1, 12 HP) — also drops Moonbloom Herb
- Brine Goblin (L2, 18 HP) — also drops Moonbloom Herb

### 3.4 The Four Quests

#### Quest 1 – "The Rising Tide"
**ID:** `rising_tide`
**Type:** KILL
**Auto-given:** Yes — awarded at new game start before intro cinematic.
**Goal:** Kill 5 Corrupted Crabs.
**Reward:** 150 gold, 400 XP.
**Teaches:** Combat, movement, food mechanic, step sounds.

> **Changed from original draft:** Brine Goblin kills do not count toward this quest (engine
> supports only one kill target per quest). If both should count, either make two kill quests
> or extend the quest engine to support a list of valid targets. For now, beach crabs are the
> primary enemy the Acolyte mentions.

#### Quest 2 – "Moonbloom for the Sick"
**ID:** `moonbloom_collection`
**Type:** COLLECT
**Giver:** Healer Mira (talk to her in Moonhaven)
**Goal:** Collect 4 Moonbloom Herbs, then return to Mira.
**Source:** Moonbloom Herb drops from Corrupted Crabs and Brine Goblins (`lootable:true`),
and appears in dungeon chests. Collected passively during normal play.
**Reward:** 100 gold, 300 XP, 1 Healing Potion.
**Teaches:** NPC dialog, inventory management, town interiors, quest log.

> **Changed from original draft:** Flowers no longer spawn at fixed overworld coordinates
> (stump, cliff, etc.) because that system doesn't exist. Drop-sourcing achieves the same
> collect-and-deliver loop using existing `LootGenerator` infrastructure. Fixed spawn points
> can be added later as a polish pass via a new "ground item" tile type.

> **Reward includes:** +10 Lirandel favor, awarded in `NpcController.checkForCollectTurnIn()`.

#### Quest 3 – "Purge the Dreamwake Caverns"
**ID:** `purge_dreamwake`
**Type:** EXPLORE (target: `"corrupted_shrine"`, required: 3)
**Goal:** Descend into the caverns and destroy 3 Corrupted Shrines (one per level).
**Mechanism:** Any altar tile ('A') at dungeon depths 1–3 triggers the Corrupted Shrine
dialog instead of the normal divine altar. Player confirms destruction → `progressQuest()`
called → XP awarded. At depth 3 the altar triggers the boss (see Quest 4).
**Reward:** 200 gold, 400 XP, Ring of Minor Protection (+1 AC).
**Teaches:** Dungeon navigation, altar specials, multi-level descent.

> **Changed from original draft — dropped features:**
> - Water rising every 2 minutes → **moved to Island 3** (Zephyrion timed storm dungeon
>   is a better thematic fit and will have the timer subsystem by then).
> - Crate-push puzzle → future stretch goal (requires physics layer).
> - Rune-matching puzzle → future stretch goal (requires puzzle UI).
>
> These cuts reduced Island 1 scope from 3 new subsystems to 0, making a clean launch
> possible. The quest is mechanically solid without them.

#### Quest 4 – "Guardian of the First Shard"
**ID:** `guardian_trial` (scripted — triggers at depth-3 altar, not a tracked quest template)
**Type:** Boss Fight + Moral Choice
**Location:** Dreamwake Caverns, depth 3 altar
**Boss:** Corrupted Moon Guardian (65 HP, 5 damage, AC 12)
**Flow:**
1. Player reaches depth-3 altar → prompt fires ("The Corrupted Moon Guardian rises!")
2. Confirm → combat begins
3. On victory → moral choice prompt:
   - **"Destroy it (pure path)"** → Key of Tides granted + full HP heal
   - **"Absorb its power (corruption)"** → Key of Tides granted + Shard of Corruption
     added to inventory (narrative seed for Pyralis storyline on Island 2)
4. Player carries Key of Tides to Moonhaven temple portal → "Island 2 coming soon" message

**Rewards:**
- Both paths award the **Key of Tides** (`Item.Type.KEY`) and a full HP heal.
- Pure path additionally grants **+20 Lirandel favor** and the **MOONBLESSED boon**
  (+10 max HP permanently; Lirandel heals the player for free at all future sacred altars).
- Corruption path additionally grants **+5 Pyralis favor**, the **PYRALIS_TEMPER boon**
  (+2 attack damage permanently), and the **Shard of Corruption** item.

> **Changed from original draft:**
> - Favor awards now live: pure path → +20 Lirandel; corruption path → +5 Pyralis.
>   Implemented in `DungeonController` boss callback.
> - **MOONBLESSED boon** (pure path): +10 max HP + free healing at sacred altars — implemented via `Boon.MOONBLESSED`, granted in `DungeonController`. Altar code checks `hasBoon(MOONBLESSED)` first.
> - **PYRALIS_TEMPER boon** (corruption path): +2 permanent attack damage — implemented via `Boon.PYRALIS_TEMPER`.

> **Quest 4 does NOT auto-complete a quest template** — completion is fully scripted through
> the boss combat callback. The Key of Tides grant happens in `DungeonController`.

### 3.5 Optional Content

All three pieces of optional Island 1 content have been implemented (2026-03-12):

- **Shipwreck cove** ✅ — "Ghostly Sailor" NPC at (65, 43) in the SE cove. Dialogue tree gives
  80 gold + Rusty Sword once (FLAG `sailor_rewarded`). Two dialogue paths: direct loot or
  crew backstory first.
- **Secret waterfall cave** ✅ — `waterfallcave.rfmap` (12×10). Cave entrance 'E' tile placed
  at (52, 22) in lirandel overworld (eastern forest). "Ghostly Woman" QUESTGIVER NPC at (6,3)
  gives `waterfall_haunting` COLLECT quest (Silver Locket, 1× from dungeon loot, rewards 50g
  + 150 XP). Legacy turn-in via `checkForCollectTurnIn`.
- **Talking seagull** ✅ — "Scraggly Seagull" TOWNSFOLK NPC at (10, 37) on the beach.

### 3.6 Starting Sequence (Recommended Future Work)

The original draft describes Lirandel appearing as soft silver light and speaking to the player.
This requires a new `IntroCinematicDialog`-style scripted scene and is not yet implemented.
Current new-game flow: character creation → `IntroCinematicDialog` → game starts on Lirandel
at (12, 35) with Rising Tide quest already in log.

When the Lirandel greeting scene is built, it should be inserted between character creation and
control handoff — either as a second cinematic page in `IntroCinematicDialog` or as a
dedicated `LirandelIntroDialog`.

**Starting items:** The draft mentions Rusty Dagger, Leather Jerkin, 1 Healing Potion, 25 gold,
and "glowing scar." The glowing scar is a narrative prop with no mechanical effect yet. Starting
equipment is set by `Player` constructor defaults — update those when starter gear is finalized.

---

## 4. Island 2 – The Forged Isles (Pyralis)

> **Status: IMPLEMENTED** — Overworld, 3 towns (Cinderport, Forge Keep, Ashfen Village), 5 monsters, quests, and NPCs all in place.

**Theme:** Volcanic industrial chain. Pyralis is the god of fire, forge, and ambition.
The Forged Isles are built on the backs of enslaved stone-shapers and run by a military forge
cult. Corrupted by Pyralis's desire to arm every shard for war.

**Level range:** 6–10
**Towns:** 3 (Cinderport, Forge Keep, Ashfen Village)
**Dungeons:** 2 (Pyralis's Crucible, The Slag Mines)

### What Island 2 Must Deliver (Engine Work)

> **Note:** Island 2 is fully implemented. All engine prerequisites were met.

1. ~~**Divine favor system**~~ — **Done.** `God` enum + `int[7] favorScores` on Player.
   Use `player.addFavor(God.PYRALIS, amount)` in Island 2 quest handlers.
2. ~~**Permanent boon system**~~ — **Done.** `Boon` enum + `Set<String> acquiredBoons` on Player.
   Island 2 god trials should call `player.grantBoon(Boon.ZEPHYRION_GRACE)` etc. when complete.
   The PYRALIS_TEMPER boon (corruption path, Island 1) is already active and visible here.
3. **Multi-target kill quests** — extend Quest to accept a `List<String> targets` so
   "kill cultists OR forge-golems" can count toward the same quest.
3. **Repeatable overworld encounter zones** — define encounter tables per overworld region
   (already partially supported via `spawnDifficulty` grid).

### Preliminary Quest Sketches

#### Quest A – "The Shard of Ambition"
**Type:** Moral Dilemma / Faction Choice
Two rival factions in Cinderport: Lirandel smugglers (trying to move refugees) vs. Pyralis
forge cult (controlling the docks). Player chooses whose cargo to destroy. Whichever side
you help gains +30 favor; the other loses –20.
This is the first time the player feels the gods pulling against each other.

#### Quest B – "The Missing Forger"
**Type:** Investigation / Clue Gathering
A master forger vanished. Interview 5 NPCs, find a blood-stained hammer, discover the Slag
Mines foreman had him killed for knowing about stolen shard dust.
Ends in a confrontation in the Slag Mines.

#### Quest C – "Pyralis's Crucible Trial"
**Type:** God Trial
Pyralis demands three tests: kill 3 forge-golems, retrieve a stolen ember-heart from the
dungeon, and make a moral choice at the crucible altar. Completing all three earns the
Key of Embers and Pyralis's grudging respect. Grant `Boon.ZEPHYRION_GRACE` (+1 AC) on
completion as a preview of Island 3 — or define a Pyralis-specific boon in `Boon.java`
for this trial's reward. Boon granting is fully implemented: `player.grantBoon(Boon.X)`.

#### Quest D – "Echoes of the Shard"
**Type:** Artifact Reconstruction
The Shard of Corruption (carried from Island 1's "absorb" path) resonates in the Crucible.
If the player has it, Pyralis offers a special dialog and an alternate path to the key —
faster but darker. Players who took the pure path on Island 1 miss this branch.
(This is the payoff for the Island 1 moral choice.)

---

## 5. Island 3 – The Storm Archipelago (Zephyrion)

> **Status: IMPLEMENTED** — Storm Spire (5-level authored classic-crawler dungeon), 4 towns, overworld map complete. Spinner, chute, and riddle door specials implemented.

**Theme:** Floating sky islands connected by rope bridges. Zephyrion is the god of wind,
storms, and change. Chaotic, beautiful, and constantly shifting.

**Level range:** 8–11
**Towns:** 4
**Dungeons:** 3

### Island 3 Is Where Timed Events Belong

The water-rising mechanic cut from Island 1 Quest 3 finds its natural home here.
Zephyrion's storm dungeons are the right context for timed events:
- **Timed storm chamber** — player has N turns before lightning floods a section
- **Bridge collapse sequence** — rope bridge breaks behind the player as they cross
- These require the timer subsystem deferred from Island 1

### Priority Engine Work Before Island 3

1. **Dungeon timer subsystem** — a countdown that fires events (flood room, collapse tile,
   spawn enemies) when it reaches zero.
2. **Tile state changes** — ability to flip a tile from walkable to blocked (flooded).
3. Optionally: **rune/pressure-plate puzzles** (the other Island 1 cut).

---

## 6. Islands 4–7 and Final Area (High-Level Sketches)

All islands 4–7 are now **implemented** with overworld maps, towns, monsters, quests, NPCs, and portal connections.

**Island 4 – The Verdant Mangroves (Sylvandar)** — Levels 12–15
**Status: IMPLEMENTED** — 230×190 overworld, 3 towns (Roothollow, Mossbridge, Amber Grove), Rootvault dungeon entrance placed.
Huge living jungle. 3 towns, 2 large dungeons. Ritual ingredient and artifact reconstruction
quests. Sylvandar is the god of nature, growth, and memory.

**Island 5 – The Abyssal Depths (Thalorax)** — Levels 16–19
**Status: IMPLEMENTED** — 230×190 overworld, 3 towns (Abyssport, Kelp Towers, Brightcoral), Pressure Temple entrance placed.
Mostly underwater atolls. 3 towns, 3 dungeons. Infiltration and timed-event quests.
Thalorax is the god of the deep, pressure, and inevitability.

> **Note:** Island 5's "Stop Thalorax's ritual before Abyssport sinks" quest (Quest Type 9)
> requires both the timer subsystem AND tile state changes. Plan Island 3 engine work to
> cover both islands.

**Island 6 – The Shadow Trenches (Umbryn)** — Levels 20–23
**Status: IMPLEMENTED** — 230×190 overworld, 3 towns (Dusthaven, The Hollow, Echo Point), Archive of Tears and Forgotten City dungeon entrances.
Dark sunken ruins. 3 towns, 2 deep dungeons. Ghost/spirit quests and infiltration.
Umbryn is the god of shadow, memory, and silence.

**Island 7 – The Golden War Isles (Bellorak)** — Levels 24–27
**Status: IMPLEMENTED** — 230×190 overworld, 3 towns (Gold Guard Camp, Iron Reckoner Camp, The Neutral Ground).
Militarized fortress islands. 4 towns, 3 dungeons. Arena and faction war quests.
Bellorak is the god of war, glory, and fire-forged iron.

**Final Area – The Cradle of Shards (World Trench)** — Levels 28+
Massive endgame mega-dungeon at the bottom of the ocean. All seven gods converge here.
The player's accumulated favor scores (with all gods) determine which god is waiting as a
potential ally or enemy at each gate. Best ending requires all 7 keys AND neutral-to-positive
favor with at least 4 gods simultaneously.

---

## 7. Endings (9 total)

1–7. Champion of one specific god (one per god — requires high favor ≥ 80 with that god,
     low favor ≤ 20 with all others)
8. True Unbound — banish all gods (best ending). Requires moderate favor 30–70 with all
   7 simultaneously — never maxing any one god. Hardest to achieve.
9. Secret New Serpent — absorb all 7 Shards yourself, reject every god. Hidden path
   unlocked only by collecting every Shard of Corruption item across all islands.

> **Design note:** Endings 1–7 and 9 require the favor system. Ending 9 requires the Shard
> of Corruption item to be collectible on every island (Island 1's shard is already in place).
> Plan one Shard of Corruption per island, each in a unique moral-choice moment.

---

## 8. Quest Type Reference

Every island should mix these structures to avoid monotony. Notes added where engine support
is currently partial or missing.

### Type 1 – Moral Dilemma / Faction Choice
**Engine support:** ✅ `messageLog.prompt()` handles binary choices. Multi-faction (3+ sides)
needs a custom dialog flow.
**Classic inspiration:** Virtue-based CRPGs, city faction systems
A town or shrine is split between two gods. Choose which side to support. The choice gives
+25–35 favor with one god and –15–25 with the other. Often changes monster spawns or unlocks
new NPCs.
**Example (Island 2):** Pyralis vs Lirandel cultists in Cinderport.

### Type 2 – Investigation / Clue Gathering
**Engine support:** ⚠️ Partial — NPC dialog exists but there is no "clue item" or journal
system. Clue objects must be implemented as MISC inventory items with descriptive text.
**Classic inspiration:** Classic CRPG mysteries and detective quests
Interview 4–6 NPCs, find hidden notes or blood-stained items, discover the culprit. Ends
with a confrontation and major favor swing.
**Example (Island 2):** Who desecrated Lirandel's moon shrine in Moonhaven? *(Note: this
example from the original draft conflicts with Moonhaven's built state — update the example
to an Island 2 location before implementation.)*

### Type 3 – Escort / Protect NPC
**Engine support:** ❌ NPCs are stationary. Escort requires a moving NPC companion system
that doesn't exist. **Do not design escort quests until this is built.**
Boon granting itself (✅) is ready — `player.grantBoon(Boon.X)` works and persists — but
the escort trigger condition requires NPC movement which is still ❌.
**Classic inspiration:** caravan escorts and escort missions from the era's party CRPGs
Safely escort a priest or artifact bearer through a dangerous area. Success = unique boon;
failure = revenge quest.
**Example (Island 3):** Escort a sky-priest across rope bridges during a storm.

### Type 4 – Ritual Ingredient Collection
**Engine support:** ✅ COLLECT quest type handles this natively. Item sourcing via drops
and chests is already working. Fixed spawn-point ingredients require future ground-item system.
**Classic inspiration:** spell-component and reagent hunts from the era's crawlers
The island's god needs 4–6 rare items for a ritual. Some in rival god territory.
**Example (Island 1 — built):** Healer Mira's Moonbloom collection quest.
**Example (Island 4):** Sylvandar needs living mangrove seeds from a rival's corrupted grove.

### Type 5 – Infiltration / Sabotage
**Engine support:** ❌ No stealth or disguise mechanics.
**Classic inspiration:** Classic CRPG stealth and infiltration
Disguise yourself to sneak into a rival god's temple. Steal a shard fragment, plant false
evidence, or sabotage a ritual.
**Example (Island 5):** Sneak into Thalorax's flooded cathedral to steal the Tideheart.

### Type 6 – God Trial / Puzzle Challenge
**Engine support:** ✅ for combat + altar trials + permanent boon grants. ❌ for pressure plates, rune puzzles.
**Classic inspiration:** Temple and statue puzzles in almost every old CRPG
The ruling god tests you with moral dilemmas, riddles, or timed challenges. Success grants
the island's Key and a permanent boon (use `player.grantBoon(Boon.X)` in the callback).
**Example (Island 1 — built):** Dreamwake Caverns — destroy 3 shrines, defeat Corrupted
Moon Guardian, choose your path. Pure path grants `MOONBLESSED`; corruption path grants
`PYRALIS_TEMPER`. Both boons are fully active across all future islands.

### Type 7 – Arena / Gladiatorial Tournament
**Engine support:** ✅ Boss combat is fully functional. An arena is just a scripted sequence
of startCombat calls with escalating enemies and a final reward.
**Classic inspiration:** arena fights and gladiatorial tournaments from the era's CRPGs
Fight 4–6 increasingly difficult rounds for glory, gold, and a legendary weapon.
**Example (Island 7):** The Grand Tournament of the Golden War Isles.

### Type 8 – Ghost / Spirit Quest
**Engine support:** ✅ A ghost is just a QUESTGIVER NPC with appropriate dialog and a
COLLECT or DELIVER quest. No new system needed.
**Classic inspiration:** Classic CRPG haunted locations and undead storylines
A restless spirit of a fallen champion needs help finishing unfinished business.
**Example (Island 1 — optional, built):** The waterfall cave ghost — `waterfallcave.rfmap`, Silver Locket COLLECT quest.
**Example (Island 6):** Lay to rest Umbryn's betrayed shadow knight.

### Type 9 – Time-Sensitive / Race Against the Tide
**Engine support:** ❌ No timer subsystem. **Target: Island 3 (Zephyrion).**
**Classic inspiration:** Classic CRPG timed events and rituals
A rival god is about to flood a town or perform a cataclysmic ritual. Limited turns to stop
it. Failure permanently changes the island.
**Example (Island 3):** Storm gate will collapse — reach the rune before it does.
**Example (Island 5):** Stop Thalorax's ritual before Abyssport sinks.

### Type 10 – Artifact Reconstruction
**Engine support:** ✅ COLLECT quest type supports gathering multiple items. Moral choice
on delivery uses `messageLog.prompt()`. Favor swing needs the favor system.
**Classic inspiration:** multi-piece artifact hunts from the era's CRPGs
Collect 5 scattered pieces of a broken divine relic. At the end choose which god to give
the completed artifact to.
**Example (Island 4):** Reassemble the Crown of Wild Growth — give to Sylvandar or destroy it.

---

## 9. Data Files Reference

| File | Purpose |
|---|---|
| `data/monsters.json` | All monster definitions (level, HP, damage, AC, XP, gold, sprite) |
| `data/items.json` | All item definitions (type, slot, effects, lootable, shopAvailable) |
| `data/quests.json` | Quest templates (type, target, amounts, rewards) |
| `data/overworlds/*.rfmap` | Overworld maps (tiles, NPCs, town entrances, teleporters) |
| `data/towns/*.rfmap` | Town interior maps (tiles, NPCs) |
| `saves/*.sav` | Player save files (Gson JSON) |
| `src/.../Boon.java` | Permanent boon enum — add new boons here; stat bonuses auto-apply |

### Island 1 Files (all created)

| File | Content |
|---|---|
| `data/overworlds/lirandel.rfmap` | 80×50 overworld — default starting map |
| `data/towns/moonhaven.rfmap` | 20×20 Moonhaven interior — 4 NPCs |
| `data/monsters.json` | Corrupted Crab (L1), Brine Goblin (L2), Corrupted Moon Guardian (L3) added |
| `data/items.json` | Moonbloom Herb, Ring of Minor Protection, Key of Tides, Shard of Corruption added |
| `data/quests.json` | `rising_tide`, `moonbloom_collection`, `purge_dreamwake` added |

### All Islands Data Summary

| Data | Count |
|---|---|
| Overworld maps | 7 (one per island) |
| Town maps | 30+ |
| Monsters | 72 |
| Items | 94 |
| Quests | 66 |
| Java source files | 126 |

### Key Sprite Notes

NPC sprites available: `npcs/innkeeper`, `npcs/shopkeeper`, `npcs/townsman`.
Custom sprites needed: healer, questgiver, guard, acolyte (currently using townsman/shopkeeper
as stand-ins). Add PNG files to `src/main/resources/tiles/npcs/` to add new NPC appearances.

Monster sprites for Island 1 use existing stand-ins:
- Corrupted Crab → `kobold.png`
- Brine Goblin → `goblin.png`
- Corrupted Moon Guardian → `wraith.png`

Custom crab/goblin sprites can replace these by adding files to
`src/main/resources/tiles/monsters/` and updating `monsters.json`.

---

## 10. Verification Checklist

### Island 1 Complete When:
- [ ] Start new game → "The Rising Tide" is in quest log immediately
- [ ] Kill 5 Corrupted Crabs → quest auto-completes, 150g + 400 XP awarded
- [ ] Moonbloom Herbs drop from crabs/goblins (lootable = true confirmed)
- [ ] Collect 4 Moonbloom Herbs → talk to Mira → "Moonbloom for the Sick" completes
- [ ] Enter Dreamwake Caverns → descend to depth 1 → interact with altar → Corrupted Shrine dialog fires
- [ ] Destroy 3 shrines across depths 1–3 → purge_dreamwake quest completes
- [ ] Reach depth-3 altar → boss combat fires → Guardian defeated
- [ ] After boss win → moral choice prompt appears → both paths award Key of Tides
- [ ] Pure path: MOONBLESSED boon granted → StatsPanel BOONS section appears → max HP increased by 10
- [ ] Pure path: walking into a dungeon altar (depth 4+) → instant free heal (no prompt, no random table)
- [ ] Corruption path: PYRALIS_TEMPER boon granted → +2 to getDamage() value
- [ ] "Absorb" path also gives Shard of Corruption
- [ ] Enter Moonhaven → 4 NPCs present at correct positions
- [ ] Talk to Innkeeper Bram → rest prompt appears for 15g
- [ ] Talk to Lirandel's Acolyte → quest-complete dialog shown (rising_tide already done)
- [ ] Portal tile (when placed in Moonhaven temple) → with Key of Tides: "coming soon" message
- [ ] Portal tile without Key of Tides → "sealed" message

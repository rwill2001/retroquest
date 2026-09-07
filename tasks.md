# RetroQuest & RetroForge — Task List

## Bugs & Fixes

### ~~1. Deliver Quest Editor Bug~~ DONE
- Added `npcTargetCombo` dropdown to `QuestEditorDialog` — populated from all NPC names across town and overworld `.rfmap` files
- DELIVER quests now show NPC dropdown for target (recipient), item dropdown only for COLLECT
- Fixed "Mazzy's Ring" quest in `quests.json` — target changed from "Healing Potion" to "Frederick", amount 5→1, cleaned up ID
- Improved `Quest.getProgressText()` for DELIVER: now shows "Deliver itemName to npcName: X/Y"
- Updated `QuestCreationGuide.md` — added DELIVER worked example, clarified target vs giver distinction

### ~~2. Dungeon Descent Animation~~ DONE
- Created `DungeonDescentAnimation.java` — 1.2s fade-to-black with "LEVEL N" / "Descending..." text, then fade back in
- `goDownDungeonLevel()` now uses `startDungeonDescentAnimation()` instead of `startDungeonEntryAnimation()`
- Full entry cinematic (3.2s with sky, bats, torches) now only plays on initial overworld→dungeon entry
- Pit falls also use the descent animation (via `goDownDungeonLevel()`)

### ~~3. Fix "Save As" in Image Editor~~ DONE
- Save As was broken: `currentSpriteName` included the category prefix (e.g., `"town/bookshelf"`), causing doubled paths like `tiles/town/town/bookshelf.png`
- Fixed `saveAsNew()` to split name from category before passing to `SaveSpriteDialog`
- `SaveSpriteDialog` now accepts and auto-selects the suggested category in the dropdown
- Save As kept — it serves as the sprite copy/duplicate mechanism

---

## RetroForge Editor — Input Validation

### ~~4. Replace Free-Text ID Fields with Dropdowns~~ DONE
- **DialogueTreeEditor** — already had combo switching for quest/item/spell/stat targets. No changes needed.
- **TileEditor** — replaced 5 free-text char fields (`tileIdField`, `openCharField`, `closeCharField`, `activateCharField`, `deactivateCharField`) with `JComboBox` tile dropdowns populated from `TileRegistry.getAllTiles()`. Added `populateTileCombo()` and `extractTileChar()` helpers. `requiredKeyCombo` was already dynamically populated.
- **ItemEditorDialog** — replaced `spriteField` JTextField with `spriteCombo` JComboBox populated from `ImageAssetRegistry.getAllSpriteKeys()`.
- Flag names (SET_FLAG, CLEAR_FLAG, FLAG_EQUALS) remain free text — flags are user-defined.

### ~~5. Save-Time Validation~~ DONE
- **QuestEditorDialog**: validates empty ID/title, DELIVER quests missing item or NPC, stale item reward and prereq quest IDs
- **TileEditor**: validates missing or invalid sprite keys against ImageAssetRegistry
- **NPCEditorDialog**: validates empty name, missing sprites, shop enabled with no items
- **DialogueTreeEditor**: already had comprehensive `runValidation()` (quest/item/node refs, reachability)
- All validations show warnings with option to save anyway — non-blocking

---

## RetroForge Editor — Improvements

### ~~6. Ctrl-S Saves Map~~ DONE
- Added `setAccelerator(Ctrl+S)` to the Save Map menu item in RetroForge.

### ~~7. Auto-Save Before Test~~ DONE
- `playtest()` now calls `saveMap()` before launching the game. Confirmation dialog updated to mention auto-save.

### ~~8. Group Selection & Copy~~ DONE
**Map Editor (MapCanvas):**
- Added `SELECT` tool to `Tool` enum with amber accent color
- Select tool button added to RetroForge toolbar and TOOLS menu
- Click and drag to select a rectangular region of tiles (cyan highlight overlay with size label)
- Ctrl+C copies selected tiles + spawn difficulty to clipboard, clears selection
- Ctrl+V pastes clipboard at hover position (dashed amber preview outline shown)
- Escape clears selection; paste is fully undoable

**Image Editor (ImageEditor):**
- Added `SELECT` tool with button in tools panel (shortcut: S)
- Click and drag to select pixel region (cyan overlay)
- Ctrl+C copies selected pixels to clipboard, Ctrl+V pastes at 0,0 (undoable)
- Escape clears selection

### ~~9. Save Indication in Image Editor~~ DONE
- `setStatus()` now flashes the status label green (PHOSPHOR) for save confirmations, resets to dim after 3 seconds. Timer set to non-repeating.

### ~~10. Starting Map Configuration~~ DONE
- Created `GameConfig.java` — singleton model loaded from `data/game_config.json` (Gson)
- Fields: `startingOverworld`, `startingX`, `startingY`, `startingAnimation`, `lastSafeTown`
- Created `GameSettingsDialog.java` — RetroForge dialog with dropdowns for overworld/town maps, spinners for X/Y, animation picker
- Added "Game Settings..." to RetroForge GAME menu
- Updated `CharacterCreationDialog` to use `GameConfig.get()` for player start position
- Updated `OverworldManager` to use config for default overworld name
- Updated `Retroquest.lastSafeTownName` to use config

### ~~11. Encounter Rate Modifier in TileEditor~~ ALREADY DONE
- `encounterRateSpinner` already exists in TileEditor with full load/save wiring. No changes needed.

---

## Portal System Unification

### ~~12. Unify Teleport System~~ DONE
- **Unified teleport handler** in `NavigationController.triggerTileEffect()` — checks per-instance TileState for `teleport_map`/`teleport_x`/`teleport_y` on ANY tile, with optional `requiredKeyId`/`lockedMessage` for key-gated portals
- **Absorbed `island_portal`** into the unified teleport handler — same code path, key gating supported via params
- **`checkOverworldTeleporter()`** → no-op (deprecated), all teleportation flows through `triggerTileEffect()`
- **Auto-migration** in `MapData.load()` — `migrateOverworldTeleporters()` converts legacy `overworldTeleporters` list entries into `initialTileStates` per-instance data, then clears the list
- **TileInstanceDialog** — added universal teleport destination fields (MAP_NAME dropdown, X/Y spinners, key/message) to ALL tiles regardless of effect type. New `MAP_NAME` ValueType with overworld dropdown.
- **Removed `TELEPORTER_PLACER`** tool from `Tool.java`, RetroForge toolbar, and MapCanvas
- **MapCanvas** now renders teleport markers (cyan "T" overlay) for tiles with `teleport_map` instance data instead of from `overworldTeleporters` list
- Dungeon stairs remain their own E-key mechanic (unchanged)
- `TeleporterPlacementDialog.java` still exists in codebase but is no longer referenced

---

## Vision & Lighting System

### ~~13. Replace Fog-of-War with Lighting~~ DONE
- Created `VisionSystem.java` — 8-octant recursive shadow casting with smooth brightness falloff
- Computes `float[][] brightness` per viewport (0.0=black, 1.0=full) every frame
- **Dungeons**: pitch black ambient, player radius 5, remembered tiles at 12% brightness, walls block vision via `Dungeon.getNorthWall/getWestWall`
- **Towns**: 70% ambient, player radius 10, `blocksVision` tiles create shadows
- **Overworld**: 85% ambient, player radius 12, subtle atmospheric dimming
- Light-emitting tiles (`lightRadius > 0`) cast independent light via shadow casting
- Multiple light sources combine with `max()` — no over-brightening
- GamePanel renders semi-transparent black overlay per tile based on brightness
- NPCs hidden when brightness < 0.08; player always full brightness
- `DungeonController.revealVisibleArea()` simplified — delegates to VisionSystem
- Dungeon revealed persistence unchanged (SaveData bitset format intact)
- Added `Player.getVisionRadius(inDungeon, inTown)` for future equipment/spell bonuses
- Removed legacy green debug grid from overworld rendering

---

## Combat & Monsters

### ~~14. Monster Special Types~~ DONE
- Created `MonsterType.java` enum: HUMANOID, BEAST, UNDEAD, DRAGON, DEMON, MAGICAL, FRIENDLY
- Added `monsterType` and `friendly` fields to `Monster.java` (null-safe backward compat)
- **Undead level drain**: 15% chance per melee hit to drain 25% of XP-to-next-level (min 50 XP). Added `Player.drainXP()`
- **Dragon Fire Breath**: new monster spell in `castMonsterSpell()`, 1.5× power (stronger than Fireball), blocked by Resist Fire. Dragons auto-assigned 60% cast chance with Fire Breath + Fear
- **Friendly encounters**: `handleFriendlyEncounter()` in CombatOverlay — rolls gold/heal/XP gift, no combat. 5% chance to spawn friendly instead of hostile. Factory seeds: Forest Fairy (Lv5), Wandering Merchant (Lv10)
- Refactored `Turn Undead` and `Holy Word` in Spell.java to use `MonsterType` instead of name-matching
- `MonsterFactory.assignTypeByName()` auto-assigns types to procedural monsters
- `MonsterEditorDialog`: added TYPE dropdown and FRIENDLY checkbox to Stats tab
- Friendly monsters excluded from normal combat pools in `MonsterRegistry.getRandomMonster()`

---

## Game Mechanics

### ~~15. Dungeon Level Limits~~ DONE
- Added `maxDungeonDepth` field to `MapData` (default 50, backward-compatible with old maps)
- `DungeonController.goDownDungeonLevel()` and `handleStairs()` now use dynamic max depth from current overworld's MapData
- RetroForge toolbar shows "MAX DEPTH" spinner (1-50) when editing overworld maps, writes to `MapData.maxDungeonDepth`
- Each overworld can have its own depth cap (e.g., starter island = 5, mainland = 50)

---

## Sound System

### ~~16. Remove Sound Editor from RetroForge~~ DONE
- Removed "Sound Editor" menu entry from RetroForge's EDITORS menu. `SoundEditor.java` kept in codebase for future reintegration.

---

## Content & Polish

### ~~18. Quest Creation Guide Update~~ DONE
- Rewrote `docs/QuestCreationGuide.md` from 298 to ~250 lines with restructured sections
- Added dedicated **Section 2: Giver vs. Recipient** explaining the two-NPC DELIVER pattern upfront
- Added **Section 6: Action Ordering Rules** with three explicit rules (GIVE_QUEST before GIVE_ITEM, COMPLETE_QUEST before TAKE_ITEM, COMPLETE_QUEST before rewards) based on real bugs (task 29)
- Clarified DELIVER quest auto-removes delivery items via COMPLETE_QUEST (no separate TAKE_ITEM needed)
- Documented COLLECT recount behavior (items found before quest acceptance still count)
- Updated COLLECT worked example to match actual moonbloom quest (amount 2, not 3)
- Expanded DELIVER worked example with both giver and recipient dialogue trees, field breakdown, and runtime walkthrough
- Added missing fields: `spellRewardId`, `prereqQuestId`, `repeatable` to template reference
- Added **Section 12-13: Quick Reference** tables for quest-related dialogue actions and conditions

**Files:** `docs/QuestCreationGuide.md`

---

## Minigames

### ~~19. Island 3 — Sky Games (Zephyrion / Storm Archipelago)~~ DONE
Three sky-themed minigames hosted in Galewick by NPC "Gusty".
- **Windchime Symphony** (memory): repeat growing wind-chime sequences using arrow keys
- **Gust Glider** (dodge/collect): side-scrolling glider ride — dodge storm clouds & lightning, collect gold rings, cash out anytime. DEX bonus for handling. Replaced Cloud Hopping.
- **Storm Rider** (dodge/collect): ride wind currents, dodge lightning, collect crystals

**Files:** `SkyGamesOverlay.java`, `WindchimeSymphonyGame.java`, `GustGliderGame.java`, `StormRiderGame.java`, `galewick.rfmap`

### ~~20. Island 4 — Nature Games (Sylvandar / The Verdant Mangroves)~~ DONE
Three nature-themed minigames hosted in Roothollow by NPC "Bark Weaver Fen".
- **Archery Range**: oscillating power bar, wind drift, DEX widens bullseye, 5 shots
- **Herbalist's Brew**: memorize a recipe, pick ingredients in order, INT adds viewing time
- **Beast Taming**: press Enter during CALM mood phase, WIS extends calm window, 5 rounds

**Files:** `NatureGamesOverlay.java`, `ArcheryGame.java`, `HerbalistBrewGame.java`, `BeastTamingGame.java`, `roothollow.rfmap`

### ~~21. Island 6 — Memory Games (Umbryn / The Shadow Trenches)~~ DONE
Three memory-themed minigames hosted in Dusthaven by new NPC "The Archivist".
- **Shadow Veil**: 4×4 card-flip memory grid, 8 symbol pairs, INT≥14 peek bonus
- **Echoes**: catch falling memory labels in 3 columns, 30s timed, WIS slows fall speed
- **Hollow Riddles**: 5 riddles with shrinking timer, 3-choice answers, WIS adds time

**Files:** `MemoryGamesOverlay.java`, `ShadowVeilGame.java`, `EchoesGame.java`, `HollowRiddlesGame.java`, `dusthaven.rfmap`

### ~~22. Island 7 — War Games (Bellorak / The Golden War Isles)~~ DONE
Three war-themed minigames hosted in Neutral Ground by new NPC "The Pit Master".
- **Siege Catapult**: aim angle + oscillating power bar, 5 shots, STR reduces error
- **Grand Tournament**: memorize opponent's telegraphed move, pick the counter, best of 5, DEX extends reveal
- **War Dice**: roll 3 dice, place into HIGH/MID/LOW slots, beat AI, best of 3 rounds, INT≥14 = 2 re-rolls

**Files:** `WarGamesOverlay.java`, `SiegeCatapultGame.java`, `GrandTournamentGame.java`, `WarDiceGame.java`, `neutral_ground.rfmap`

### ~~23. GameForge — right-click edit for town & dungeon entrances~~ DONE
- Right-click on 'E' tile: shows "Town Entrance: <name>" in header + "Edit Town Entrance…" menu item
- Right-click on 'D' tile: shows "Dungeon: <name>" or "Dungeon: Procedural" in header + "Edit Dungeon Entrance…" menu item
- TownPlacementDialog: detects existing entrance, pre-selects town, removes old entry before adding new (no duplicates), shows "EDIT" title
- DungeonPlacementDialog: new 2-arg constructor overload; pre-selects authored/procedural + dungeon name, shows "EDIT" title

### ~~24. select tool in game forge does not allow copy or paste.  just selection, which seems not very useful.~~ DONE
- Root cause: Ctrl+C/V were bound via `WHEN_IN_FOCUSED_WINDOW` on MapCanvas, but JTextComponents (palette search, property fields) consumed the events when they had focus
- Moved copy/paste/escape handling to a global `KeyEventDispatcher` in RetroForge (same pattern as Ctrl+Z/Y undo/redo)
- Made `copySelection()`, `pasteAtHover()`, `clearSelection()` public on MapCanvas
- Added status bar feedback: "COPIED 5×3" / "PASTED 5×3" with 3-second auto-clear

### ~~25.  limit weapon/armor and other item attributes to level for random loot drops and try to distibute more evenly, so, say level 5 player has 20 percent drops of +1, 20 percent +2, etc.  ensure no loot is ever given above player level by anyone. Also no purchases greater than level~~ DONE
- `LootGenerator`: Both `getRandomLoot()` and `getRandomLootForMonster()` now take `playerLevel` param; tier capped at `min(contextTier, playerLevel)`
- Uniform tier distribution: random tier chosen from 1 to capped max (e.g., level 5 = equal 20% chance per tier 1–5)
- Fallback chain: exact tier → ±1 window (capped at maxTier) → any lootable ≤ maxTier → safety net
- All 7 callers updated: DungeonController (6 sites), CombatEngine (1 site)
- `ShopOverlay.open()`: filters out items with `tier > playerLevel` so shops only sell level-appropriate gear

### ~~26. In retroforge get rid of the refresh button~~ DONE
- Removed the refresh button and its divider from the RetroForge toolbar

### ~~27. the dialog after opening the door in mooncrest and talking to lyren the pale is lacking.  Its part of a quest.~~ DONE
- Rewrote Lyren's dialogue tree from 5 nodes to 22 nodes with branching conversation paths
- **find_the_hermit path**: "Aldric sent me" → hermit_found → why_locked / ley_lines / energy_danger branches with world-building about ley line nexus beneath Mooncrest
- **alchemists_stone path**: stone_request → stone_consider (research angle) or stone_favor (personal debt angle) → stone_give → stone_aftermath with ley line warning and optional ley_warning flag
- **Exploration path**: who_are_you → ask about ley lines, why he's locked away, or his research
- **Return visits**: return_visit acknowledges quest completion; ask_menu with about_mooncrest, research_update, strange_things branches
- **Character depth**: Lyren is now a fleshed-out ley line scholar — pale from years indoors, prickly but warming over time, hints at deeper mystery
- Flags: `lyren_met` (persists across visits), `lyren_stone_given` (unchanged), `lyren_ley_warning` (foreshadowing)
- Updated `Island1_TestPlan.md` and fixed `QuestDesign.md` (Lyren the Hermit → Lyren the Pale)

### ~~28. need monsters to poison, so the antidote and other spells could have use.~~ DONE
- **Persistent poison**: Added `poisonSteps` to Player — poison now persists after combat as step-based damage (1d3 per step), decrements each move
- **Combat → overworld carry**: `CombatEngine.endCombat()` transfers remaining combat poison turns to Player (×5 steps per turn)
- **Antidote consumption**: InventoryOverlay now handles antidote by ID — cures poison, only consumed if actually poisoned
- **Cure sources**: Antidote item, Dispel Magic spell, Restoration spell, inn rest, HEAL_PLAYER dialogue action
- **StatsPanel indicator**: "POISON" shows in red in the buff strip with steps remaining
- **8 registered monsters now cast Poison**: Giant Rat (L3, 20%), Lizard Man (L13, 25%), Ghoul (L13, 30%+Fear), Lava Serpent (L10, +Fireball), Vine Strangler (L12, +Drain), Spore Phantom (L13, +Sleep/Fear/Drain), Blight Mother (L15, +Drain/Fear/Fireball), Anglerfish Horror (L17, +Fear/Sleep)
- **MonsterFactory** already assigns Poison to procedural spider/serpent/scorpion/spore/fungi/viper monsters
- **Medusa** (L28) also gets Poison+Fear+Blind
- Updated docs: CombatSystem.md, ItemInventory.md

### ~~29. bug in the waterfall haunting quest. i had found the locket before the quest, then when talking to the ghostly woman, it seems to resolve, but no reward and the quest shows incomplete.~~ DONE
- Reordered dialogue actions: COMPLETE_QUEST now runs before TAKE_ITEM in ghost_turnin node
- Added COLLECT quest recount in COMPLETE_QUEST handler — re-counts items in inventory before checking isComplete(), fixing the case where items were found before accepting the quest

### ~~30.  scrolls are no longer dropping as loot so many spells do not appear in the spell dialog~~ DONE
- Added 30 scroll items to items.json covering all 6 spell levels (tiers 1-6)
- Scrolls are lootable with appropriate rarity (COMMON→LEGENDARY) matching spell power
- Lower-level scrolls available in shops; higher-level scrolls dungeon-drop only
- Existing scroll consumption code in InventoryOverlay already handles learning spells

### ~~31.  moonbloom herb is not dropping at all or way too infrequently to finish the quest.~~ DONE
- Reduced quest requirement from 4 herbs to 2 (herb is tier 1 COMMON but competes with many items in the drop pool)
- Updated all NPC dialogue references in moonhaven.rfmap ("four" → "two")

### ~~32. corrupted shrine seems to always have the same result.  We need to mix this up a great deal. make it fun.~~ DONE
- Depths 1-2 now have 8 randomized outcomes: purifying blast (XP+heal), corruption lash (damage+bonus XP), gold cache, guardian spawn (combat), spell slot refresh, Lirandel favor, curse (damage+gold loss), eerie silence
- Depth 3 boss encounter unchanged (story-critical, grants key_of_tides)

### ~~33 text for the riddle in the dungeon is too long and doesnt always fit nicely in the panel~~ DONE
- Added word-wrapping to `paintPrompt()` in MessageLog.java using existing `wordWrap()` utility
- Long prompt text now flows across multiple lines; buttons shift down accordingly

### ~~34 slag beast quest tree from the forge guard captain seems to have a bug where it is repeated over and over~~ DONE
- Added QUEST_ACTIVE routing in greeting node for in-progress status updates
- Added QUEST_COMPLETE condition to bounties "I'll hunt" option to prevent re-offering completed quests
- Added "I'll hunt more slag beasts" option with QUEST_COMPLETE condition for explicit repeat acceptance
- Added `slag_repeat` dialogue node with thematic re-accept text and GIVE_QUEST action

---

## Overworld Content — Making Exploration Fun

### ~~35. Hidden Shrines~~ DONE
- New `shrine` step effect in NavigationController — one-time discovery grants divine favor + lore message + optional XP
- Configurable params: `god`, `favor`, `xp`, `message`, `discoveredTileId`
- Persisted via TileStateManager (`discovered` flag); plays `altarChime` sound
- Available in TileEditor dropdown for placement in RetroForge

### ~~36. Buried Treasure / Dig Sites~~ DONE
- New `treasure` step effect — one-time loot find with specific item (by `itemId`) or random loot (by `tier`)
- Optional `requiredItemId` + `lockedMessage` for gated treasure (e.g. sunken chests needing a diving item)
- Optional `gold` reward and `discoveredTileId` for visual change after looting
- Persisted via TileStateManager (`looted` flag); plays `coin` sound

### ~~37. Ambush Encounters (Overworld Elite Fights)~~ DONE
- New `ambush` step effect — guaranteed one-time fight against a specific monster (`monsterId`)
- Bonus loot on victory via `lootItemId` (specific) or `lootTier` (random), on top of normal combat drops
- Only marked `defeated` if the player wins; can retry on return
- Falls back to area-difficulty random monster if `monsterId` not specified

### ~~38. Water-Adjacent Monster Spawns~~ DONE
- `EncounterController` checks 4 cardinal neighbors for liquid tiles; boosts aquatic monster spawn weights
- Scaling: 1 water tile = 3×, 2 = 5×, 3 = 8×, 4 (surrounded) = 12× weight for aquatic monsters
- Added `aquatic` boolean to `Monster.java` + "AQUATIC" checkbox in MonsterEditorDialog
- Marked 8 existing monsters as aquatic; added 6 new: reef_lurker(L2), tide_eel(L4), shore_serpent(L7), bog_leech(L13), drowned_soldier(L21), war_kraken(L25)

### ~~39. Per-Island Thematic Overworld Features~~ DONE
- **Lirandel** (Moonbloom patches): use `treasure` effect with potion `itemId` — no new code needed
- **Pyralis** (Lava geysers): new `geyser` step effect — random `chance` (0-100) of `amount` damage per step, unlike always-hit `damage` tiles
- **Zephyrion** (Wind currents): new `wind_current` step effect — pushes player `distance` tiles in `direction` (north/south/east/west), stops at walls
- **Sylvandar** (Talking trees) + **Umbryn** (Memory echoes): new `lore` step effect — repeatable text display on step; optional `oneTime` param for one-shot variants
- **Thalorax** (Sunken chests): `treasure` effect now supports `requiredItemId` + `lockedMessage` for gated access
- **Bellorak** (War camps): use `ambush` effect with battlefield loot — no new code needed
- All 6 new effect types added to TileEditor dropdown: `shrine`, `treasure`, `ambush`, `geyser`, `wind_current`, `lore`
### ~~40 - Ensure we can place all the recent elements using retroforge~~ DONE
- Audit: 10 new tile defs (Hidden Shrine, Dig Site, Ambush Point, Lava Geyser, Wind Current, Ancient Tree, Memory Echo, Sunken Chest, Battle Standard, Locked Iron Gate Overworld) already have PUA char IDs ``–``, sprites exist, and appear in the Overworld palette. Aquatic monster flag was already wired in MonsterEditorDialog.
- **TileInstanceDialog**: added `EFFECT_PROPS` schemas for `shrine`, `treasure`, `ambush`, `geyser`, `wind_current`, `lore`. Introduced 4 new ValueTypes (`GOD`, `DIRECTION`, `ITEM_ANY`, `MONSTER`) with corresponding combo builders so per-coordinate overrides for `god`, `favor`, `xp`, `discoveredTileId`, `itemId`, `tier`, `gold`, `requiredItemId`, `lockedMessage`, `monsterId`, `lootItemId`, `lootTier`, `chance`, `amount`, `direction`, `distance`, `oneTime`, `message` are all editable through typed dropdowns/spinners.
- **TileEditor**: extended the Effects tab with tile-definition-level fields for `god` (God combo), `favor`/`xp`/`chance`/`distance`/`gold`/`tier`/`lootTier` (spinners), `direction` (combo), `itemId`/`requiredItemId`/`monsterId`/`lootItemId` (text), `discoveredTileId` (tile combo), and `oneTime` (checkbox). Save logic uses `putIfNotEmpty/putIfNonZero/putIfNot` to skip default values, keeping `tiles.json` clean.

---

## Review-and-Fix Pass — September 2026

### ~~41. Zephyrion restored and made traversable~~ DONE
- `data/overworlds/zephyrion.rfmap` had been overwritten with a blank map — restored from git
- Authored a sky-bridge network connecting the island's four town platforms
- Repainted two island portals that had landed on unreachable tiles: thalorax (170,45) and umbryn (60,30)
- Moved several portal arrival coordinates onto reachable ground
- Guarded the underlying cause: `OverworldManager.loadOverworld()` now falls back to an empty map **in memory only** when a `.rfmap` exists but will not parse, sets `loadFailed`, and `saveCurrent()` refuses to write. The same refuse-to-save-after-failed-parse guard is wired into `ItemRegistry`, `MonsterRegistry`, `QuestRegistry` and `TileRegistry`.

### ~~42. Bellorak dungeons wired; Cradle entrance re-gated~~ DONE
- `war_beneath`, `iron_pit` and `cradle_of_shards` now reachable — each entrance is a `'D'` tile whose `initialTileStates` entry carries `dungeonName`, consumed by `Retroquest.handleKey` then `DungeonController.enterAuthoredDungeon()`
- The Cradle of Shards entrance is now a **key-gated dungeon entrance** (`requiredKeyId: key_of_iron` plus `lockedMessage` in tile state), not a portal
- `rootvault` levels 1-5 gained entry points

### ~~43. Island key chain — one key per island~~ DONE
- Chain is now `key_of_tides` then `key_of_embers`, `key_of_gales`, `key_of_roots`, `key_of_depths`, `key_of_echoes`, `key_of_iron` — matching lirandel, pyralis, zephyrion, sylvandar, thalorax, umbryn, bellorak
- `zephyrion_trial` ("Trial of the Gale", KILL any x6, rewards `key_of_gales` and Chain Lightning) is offered by **Reva** in Stormspire, gated behind both of the town's smaller trials
- Note: `TileRegistry.createDefaultTiles()` still seeds the Thalorax portal with `key_of_gales` instead of `key_of_roots`. `data/tiles.json` is correct, so this only matters if that tile is regenerated.

### ~~44. Dungeon presentation follows an explicit island ladder~~ DONE
- `DungeonController.modeForIsland(overworld)`: `lirandel` gives `TOP_DOWN`, `sylvandar` gives `TEXTURED`, everything else gives `WIREFRAME`
- `enterAuthoredDungeon` promotes a `TOP_DOWN` result to `WIREFRAME`, so authored dungeons are never drawn overhead
- Previously islands 5-7 fell back to the island-1 top-down view

### ~~45. `q` Riddle Door tile added~~ DONE
- The riddle system existed in `DungeonController` (`handleRiddleDoor`, five riddle pools) but no tile carried the `q` id, so it could never fire
- Added to `data/tiles.json`: category Dungeon, sprite `dungeon/door`, `blocksVision: true`, `walkable: true`, no step effect
- `q` is authored into 16 shipped dungeon maps

### ~~46. Monster types backfilled~~ DONE
- 70 of the 78 monsters in `data/monsters.json` gained a `monsterType`; all 78 now have one
- Distribution: BEAST 21, HUMANOID 19, UNDEAD 18, MAGICAL 12, DEMON 4, DRAGON 2, FRIENDLY 2
- This activates Turn Undead (70% instant kill vs UNDEAD, otherwise double damage), Holy Word (double damage vs UNDEAD/DEMON) and the 15%-per-melee-hit undead XP drain, and excludes FRIENDLY entries from hostile spawn pools

### ~~47. Combat correctness~~ DONE
- Fixed the flee and victory paths, damage reduction, charm duration, sleep/stun gating, boss unfleeability, and death unwinding

### ~~48. Persistence hardening~~ DONE
- Failed map loads no longer overwrite the file (see 41)
- Saves write `<file>.tmp` then `Files.move(..., REPLACE_EXISTING)`; `saveGame(int)` returns `false` and logs a real failure message if either step fails, and `SaveLoadOverlay` refuses to quit on a failed save

### ~~49. Balance pass~~ DONE
- Gear and boon AC contribution capped at `level + 2` (`Player.NON_DEX_AC_CAP_BASE`); DEX-derived AC uncapped
- Player melee damage scales with level (`+ level / 3`); spell power scales `basePower * (1 + level/20)`
- `MonsterFactory` XP curve is now quadratic: `level * (25 + level) + rand(level * 10)`
- Boss stats raised, encounter rates retuned, mini-game payouts retuned to roughly break-even

### ~~50. Item economy~~ DONE
- Sell price is now a fraction of buy price (`Item.getSellPrice()`: 25% potions/scrolls/wands, 35% keys/misc, 40% everything else), so shop arbitrage is impossible even at max CHA
- Weapon and armour ladders re-priced, several dominated items fixed, shop stock repaired

### ~~51. UI geometry and data-destroying paths~~ DONE
- Overlay geometry and fonts now go through `OverlayTheme.scaled()` and `Fonts.mono*()`, both driven by `DisplayScale.SCALE` (screen-width bucket, overridable with `-Dretroquest.uiScale=<factor>`)
- Fixed stack selling, equip displacement, click hit-testing and save-slot confirmation

### ~~52. Documentation refreshed~~ DONE
- `CLAUDE.md` corrected: JDK is Corretto 26 at `C:/Program Files/Amazon Corretto/jdk26.0.0_35` (the documented 17.0.18 path no longer exists and broke `mvn`), Maven 3.9.14 at `C:/Program Files/Maven/apache-maven-3.9.14`, 148 source files not 81. Added the `data/` layout, map/dungeon wiring and island ladder.
- `Architecture.md`, `UIOverlayArchitecture.md`, `MapNavigation.md`, `Dungeon_Requirements.md` and `README.md` re-verified against source and corrected.

---

## KNOWN REMAINING — not fixed, do not lose

Identified during the September 2026 review and deliberately left open.

1. **Ending reachability** — the four endings' reachability conditions are being reworked. Do not
   treat the current conditions as final.
2. **Dungeon specials on non-walkable tiles** — `handleDungeonSpecial` dispatches `n` (spinner),
   `c` (chute) and `M` (memory pool), but in `data/tiles.json` those ids are Barrel, Counter and
   Pillar, all `walkable: false`. The player can never stand on one, so those three handlers cannot
   fire. Either move the specials to new walkable ids or make those ids walkable.
3. **Editor has no dirty tracking** — `RetroForge` has no unsaved-changes flag at all. Closing the
   window, FILE / Exit, Load Map and New Map all discard unsaved map edits silently. `ImageEditor`
   is the only editor with an `unsavedChanges` flag, and it only prompts on New Image.
4. **No test suite** — `src/test` does not exist, `pom.xml` has no JUnit or TestNG dependency and no
   surefire plugin. There is no automated test of any kind in the repository. The two
   `*_TestPlan.md` docs are manual plans.
5. **`data/` paths resolve relative to the working directory** — registries use
   `System.getProperty("user.dir") + "/data/..."` and everything else uses bare `new File("data/...")`
   or `new File("saves/...")`. Nothing validates the CWD, so launching either main class from
   elsewhere silently reads the wrong tree and writes saves to the wrong place. The `run-*` scripts
   work only because they `cd` to the project root first.

### Smaller open items found while verifying the docs

- `data/tiles.json` has duplicate ids: `?` appears 27 times (26 mis-encoded legacy letter tiles plus
  a real entry) and `^` twice (Bridge, walkable; and Tiled Roof, not walkable).
  `TileRegistry.getById` returns the first match, so the second `^` entry is unreachable.
- `q` Riddle Door exists in `data/tiles.json` but **not** in `TileRegistry.createDefaultTiles()`.
  If `tiles.json` is lost, the tile silently degrades to the Grass default.
- The `q` tile is `walkable: true` and is never replaced on a correct answer, so a riddle door does
  not actually block passage — answering only grants XP or costs HP.
- Dungeon tile-state keys are `"dungeon:" + depth` with no dungeon name, so an exhausted special at
  (x,y) on level 3 of one authored dungeon reads as exhausted at (x,y) on level 3 of every other.
  `TileStateManager`'s own javadoc documents the intended `dungeon:<name>:<depth>` form.
- `DungeonWallQuery.isSpecialTile()` is `"AfsHtPIBLnkcqM"` — it omits `g` and `R` (cube and puzzle
  never fire in authored dungeons) and includes `B`, `L`, `k`, which have no handler.
- `GamePanel` still has a dead `case 'B' -> "dungeon/box"` sprite branch; `Dungeon.getSpecial`
  cannot return `B` (spec value 9 is `R`).
- Locked doors never consume the key and `Player` has no `removeKey` at all, so a one-shot door is
  currently impossible.
- Five step-effect types are implemented but declared by zero tiles: `encounter`, `trap_once`,
  `set_tile`, `toggle_door`, `pressure_plate`.
- `Town.loadFailed` is set on a failed parse but never read — `Town.save()` has no guard, unlike
  `OverworldManager.saveCurrent()`.
- `autoSave()` swallows all exceptions, so a failing autosave is invisible to the player.
- `SoundEditor` is not reachable from any RetroForge menu — only via its own `main()`.
  (`TeleporterPlacementDialog` and `NewTileDialog`, also dead, have since been deleted.)
- `Player.spellLearned` is declared `new boolean[37]` while every other allocation and the load
  migration use 38.
- `data/monsters.json` has no monsters at levels 29-32, 34-37, 39-42, 44-47, 49 or 50, so encounters
  in that band always fall through to `MonsterFactory`.

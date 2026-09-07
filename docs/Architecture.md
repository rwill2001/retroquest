# RetroQuest — Architecture Overview

**Last updated:** September 2026

## Package Structure

```
src/main/java/io/cannonforge/retroquest/
├── core/           (15 files) — Game loop, main frame, rendering, dungeon renderers, vision, UI scale, crash log
├── controller/     (4 files)  — Game logic controllers (dungeon, navigation, NPC, encounters)
├── model/          (24 files) — Data models (Player, Monster, Item, Spell, Quest, NPC, Dungeon, etc.)
├── overlay/        (46 files) — 19 in-panel overlays + 23 mini-games + 4 support classes
├── registry/       (9 files)  — JSON-backed data registries (items, monsters, quests, tiles, images, loot)
├── editor/         (24 files) — RetroForge editor dialogs (map, tile, NPC, item, quest, monster, image, sound)
├── dialog/         (5 files)  — Modal game dialogs (character creation, intro cinematic, etc.)
├── dialogue/       (5 files)  — Dialogue tree data model (tree, node, choice, action, condition)
├── animation/      (16 files) — 15 full-screen animations + AnimationGuard
```

**Total: 148 Java files** — `find src/main/java -name '*.java' | wc -l`

The overlay package is 19 `*Overlay` classes, 23 `*Game` mini-games, and 4 support classes
(`CombatEngine`, `SpellEffectRenderer`, `OverlayTheme`, `BossFightCoordinator`).

## Core Loop

```
Retroquest.java (JFrame)
    │
    ├── handleKey() — input dispatch (real order in UIOverlayArchitecture.md §4)
    ├── handleMovement() — wall + walkability checks, step effects, autosave, encounters
    ├── saveGame() / loadGame() — persistence via SaveData + Gson, atomic move on write
    │
    └── GamePanel.java (JPanel)
            │
            ├── paintComponent() — rendering pipeline:
            │     1. Full-screen animations (if active, short-circuit)
            │     2. VisionSystem.computeBrightness() — per-tile light buffer
            │     3. Map: TexturedDungeonRenderer / WireframeDungeonRenderer /
            │        top-down dungeon / overworld-town tile pass
            │     4. NPCs + Player sprite (hidden below brightness thresholds)
            │     5. Overlays (combat → … → notifications → death)
            │
            └── 60fps repaint timer (combat/overlays), on-demand repaint (exploration)
```

## Core Package

| Class | Responsibility |
|---|---|
| `Retroquest` | JFrame, global game state, input dispatch, movement, save/load |
| `GamePanel` | All in-panel rendering and mouse routing; owns every overlay and animation |
| `StatsPanel` / `MessageLog` | Side stats panel; bottom log with Y/N, multi-option and free-text prompts |
| `VisionSystem` | Per-tile brightness for the viewport — radial light with line-of-sight |
| `DungeonWallQuery` | Unified wall/special/`canMove` query over procedural **and** authored dungeons |
| `WireframeDungeonRenderer` | Vector first-person line renderer, per-dungeon palettes |
| `TexturedDungeonRenderer` | Texture-mapped first-person renderer (Sylvandar) |
| `DisplayScale` / `Fonts` | Global UI scale factor and scaled font factory |
| `OverworldManager` / `Town` | `.rfmap` loading for islands and town interiors |
| `SaveData` / `SoundManager` | Save model + tile-state store; procedural audio synthesis |
| `CrashLogger` | Redirects `System.err` and uncaught exceptions to `logs/retroquest.log` (rotating), because the shipped `javaw.exe` launcher has no console |

## Controller Layer

Controllers hold a `Retroquest game` reference and access state via package-private fields.

| Controller | Responsibility |
|---|---|
| `DungeonController` | Dungeon navigation, view-mode selection, specials (`P` pit, `s` stairs, `t` teleporter, `A` altar, `f` fountain, `g` cube, `H` throne, `I` inn, `R` puzzle, `n` spinner, `c` chute, `q` riddle door, `M` memory pool), reveal, loot generation, island trial and boss handlers |
| `NavigationController` | Town entry/exit, overworld teleporters, tile step-effects (13 dispatched types plus `locked_door`), map switching, portal key checks, tile-state persistence |
| `NpcController` | NPC dialogue dispatch, quest giving/turn-in, inn, shop/casino opening, map spell casting |
| `EncounterController` | Random encounter rolls on overworld (spawn difficulty grid → monster level) |

## Overlay System

All overlays follow: `paint(Graphics2D, W, H)` + `isActive()` + `handleKey()` + `handleClick()`. Only one overlay receives input at a time (priority chain in `Retroquest.handleKey()`).

| Overlay | Purpose |
|---|---|
| `CombatOverlay` + `CombatEngine` + `SpellEffectRenderer` + `BossFightCoordinator` | Turn-based combat, spell execution, 18 animated effects, multi-phase boss transitions |
| `SpellbookOverlay` | Spell selection (combat + map casting) |
| `InventoryOverlay` | Item management, equip, use, drop |
| `ShopOverlay` | Buy/sell with per-NPC stock; CHA modifies both prices |
| `CasinoOverlay` | Casino mini-games |
| `ForgeArenaOverlay`, `AbyssalDepthsOverlay`, `SkyGamesOverlay`, `NatureGamesOverlay`, `MemoryGamesOverlay`, `WarGamesOverlay` | Per-island mini-game hubs, each hosting several `*Game` classes over the shared `MiniGame` base |
| `DialogueOverlay` | Branching NPC dialogue with conditions/actions |
| `QuestLogOverlay` | Active/completed quest tracking |
| `SaveLoadOverlay` | 8 manual slots + autosave slot, with metadata |
| `DivineAudienceOverlay` | Departing-god farewell shown once per island on first forward portal use |
| `CradleChoiceOverlay` | Endgame choice at the Cradle of Shards |
| `HelpOverlay`, `NotificationOverlay` | F1 help; transient toasts |
| `DeathOverlay` | Death sequence (always topmost) |

Shared styling via `OverlayTheme.java` (colors, fonts, animations, scroll indicators). All overlay
geometry goes through `OverlayTheme.scaled()` and `Fonts.mono*()`, which apply `DisplayScale.SCALE`
— a screen-width-derived factor (1.00 / 1.25 / 1.50 / 1.75) overridable with
`-Dretroquest.uiScale=<factor>`.

## Data Flow

```
data/items.json      ←→  ItemRegistry      ←→  Player.inventory / ShopOverlay      (140 items)
data/monsters.json   ←→  MonsterRegistry   ←→  CombatOverlay / EncounterController (78 monsters)
data/quests.json     ←→  QuestRegistry     ←→  Player.activeQuests / NpcController (66 quests)
data/tiles.json      ←→  TileRegistry      ←→  GamePanel / NavigationController
data/balance.json    ←→  BalanceConfig     ←→  CombatEngine / DungeonController
data/game_config.json ←→ GameConfig        ←→  OverworldManager (starting overworld)
data/overworlds/*.rfmap  ←→  OverworldManager  ←→  GamePanel / NavigationController (7 islands)
data/towns/*.rfmap       ←→  Town              ←→  GamePanel / NavigationController
data/dungeons/*.rfmap    ←→  MapData           ←→  DungeonController.enterAuthoredDungeon
saves/*.sav          ←→  SaveData + Gson   ←→  Retroquest.saveGame/loadGame
```

All registries are JSON-backed via Gson, auto-seeded on first run, and editable through RetroForge.
Every path is resolved **relative to the process working directory**, so the game and the editor
must be launched from the repo root.

A registry or map that exists on disk but fails to parse is replaced by an in-memory placeholder
and marked `loadFailed`; subsequent saves are refused so the unreadable authored file survives.
Runtime tile mutations are held in `SaveData.tileStates` via `TileStateManager`, keyed
`"<mapKey>:<x>,<y>"` where mapKey is `overworld:<name>` / `town:<name>` / `dungeon:<depth>`.
Saves are written to `<file>.tmp` and then moved over the target with `REPLACE_EXISTING`, and
`saveGame` returns `false` (and logs) if either step fails.

## Key Model Relationships

```
Player
├── inventory: InventorySlot[20]
├── equipment: weapon, armor, helm, amulet, shield, ring1, ring2
├── keyRing: List<String>          — permanent key storage; KEY rewards bypass inventory
├── activeQuests / completedQuests
├── knownSpells[38] + spellLearned[38]  — 37 spells defined in Player.initSpells(); last slot unused
├── favorScores: int[7]           — per-god reputation, starts at 50 (neutral), clamped 0–100
├── acquiredBoons: Set<String>    — permanent stat bonuses
├── flags: Map<String, String>    — dialogue and trial state persistence
└── activeEffects: List<Effect>   — transient; rebuilt from equipped items on load

Monster
├── stats (hp, damage, ac, level, goldReward, xpValue)
├── spellcasting (spellNames, spellCastChance, spellPower, spellResistance 0–40)
├── monsterType: BEAST|HUMANOID|UNDEAD|MAGICAL|DEMON|DRAGON|FRIENDLY (null ⇒ HUMANOID)
└── status: sleepTurns, charmTurns (transient). Poison/stun/fear/blind live on CombatEngine.

NPC
├── type (SHOPKEEPER, INNKEEPER, CASINO, QUESTGIVER, TOWNSFOLK, ...)
├── dialogueTree: DialogueTree    — branching conversations
├── questId                       — legacy single-quest link
└── shopItemIds                   — per-NPC shop stock
```

## RetroForge Editor

Standalone Swing app (`RetroForge.java`) with 8 sub-editors accessible from the EDITORS menu. Edits `.rfmap` map files and all JSON registries. Includes a "Playtest" button that launches the game without closing the editor.

## Dungeon System

- **Procedural**: `Dungeon.java` — deterministic 200×200 generation (depths clamped 1–50), classic
  CRPG math. Walls are never stored — queried live via `getNorthWall()` / `getWestWall()` / etc.
- **Authored**: `data/dungeons/<name>_<level>.rfmap`, entered from a `'D'` overworld tile whose
  `initialTileStates` entry carries a `dungeonName` (plus an optional `requiredKeyId` gate).
- `DungeonWallQuery` unifies both: procedural coordinates are 1-based and delegate to `Dungeon`;
  authored coordinates are 0-based and infer WALL/DOOR/OPEN from tile walkability.
- **Presentation ladder** — `DungeonController.modeForIsland(overworld)`:
  `lirandel` → `TOP_DOWN`, `sylvandar` → `TEXTURED` (`TexturedDungeonRenderer`), everything else →
  `WIREFRAME` (`WireframeDungeonRenderer`, per-dungeon palettes). Authored dungeons use the same
  ladder but promote a `TOP_DOWN` result to `WIREFRAME`, so they are never rendered overhead.
- **Vision**: `VisionSystem.computeBrightness()` replaces the old binary fog. Ambient is 0.0 in
  dungeons, towns and overworld; light is cast radially from the player (`Player.getVisionRadius`)
  and from any tile with `lightRadius > 0`, with falloff `1 - (dist/radius)^1.5` and a
  Bresenham line-of-sight test (dungeon rays test wall segments, surface rays test
  `TileDefinition.blocksVision()`). Explored-but-unlit tiles are remembered at 0.12 (dungeon) /
  0.22 (town). Dungeon reveal is persisted per depth and town reveal per town in `SaveData`.

## Sound System

All audio is procedurally synthesized — no audio files. `SoundManager` generates chiptune SFX and music using square/sine/triangle/saw/noise waveforms via Java Sound API. Custom sounds can be defined in `sounds/custom_sounds.json`.

## Cross-Reference

| Topic | Primary Doc |
|---|---|
| Spells (full table, effects, visuals) | `SpellSystem.md` |
| Combat mechanics (turns, buffs, loot) | `CombatSystem.md` |
| Player stats, leveling, key ring, favor | `PlayerSystem.md` |
| Map navigation, tile effects, portals | `MapNavigation.md` |
| Monsters, procedural generation | `Monsters.md` |
| Items, inventory, shops, loot | `ItemInventory.md` |
| Quests (system + creation guide) | `QuestSystem.md`, `QuestDesign.md`, `QuestCreationGuide.md` |
| NPCs, dialogue trees, casino | `NPCRequirements.md` |
| Dungeon generation, specials | `Dungeon_Requirements.md` |
| Tiles, step effects, locked doors | `TileRequirements.md` |
| UI overlays, input routing | `UIOverlayArchitecture.md` |
| RetroForge editor | `RetroForgeEditor.md`, `RetroForge_Operators_Guide.md` |
| Sound synthesis | `SoundSystem.md` |
| Save/load, migration | `SaveLoadSystem.md` |
| Story, lore, gods | `STORY_BIBLE.md` |
| Game design, island progression | `RETROQUEST_DESIGN_DOC.md` |
| Build and installer | `InstallerBuild.md` |

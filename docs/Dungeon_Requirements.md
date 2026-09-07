# RetroQuest Dungeon Subsystem Requirements Document

**Version:** 1.3  
**Date:** September 2026  
**Author:** rwill2001
**Status:** Updated – rendering fully integrated; final gaps identified  

## 1. Introduction

The dungeon subsystem implements a 50-level procedurally generated dungeon using classic CRPG-style deterministic generation.  

- **`Dungeon.java`** – Pure static deterministic 200×200 map generator + wall/special queries.  
- **`DungeonWallQuery.java`** – Unifies wall/special/`canMove` queries across procedural (1-based coords, delegates to `Dungeon`) and authored (0-based, infers from tile walkability) dungeons.
- **`VisionSystem.java`** – Per-tile brightness with line-of-sight; replaces the old binary fog.
- **`DungeonController.java`** – Navigation, view-mode selection, specials, reveal, per-step events, island trials and bosses.
- **`Retroquest.java`** – Central state, input dispatch, movement, integration.  
- **`GamePanel.java`** – Top-down dungeon rendering (lighting, specials, thick walls/doors) + entry/exit/teleport animations.
- **`WireframeDungeonRenderer.java` / `TexturedDungeonRenderer.java`** – The two first-person views.

**Core Design**  
- Procedural generation and walls are **stateless** and 100 % deterministic.  
- For procedural dungeons `currentMap` stores only floor/special chars — the generator emits
  exactly `.`, `I`, `P`, `t`, `s`, `A`, `f`, `g`, `H`, `R`. Authored dungeons put arbitrary
  `tiles.json` ids in `currentMap`, including the additional specials `n`, `c`, `q`, `M`.
- Walls are **never stored** for procedural dungeons — queried live via `Dungeon.getNorthWall()` /
  `getWestWall()`. Authored dungeons have no wall data at all; walls are inferred from
  non-walkable tiles by `DungeonWallQuery`.
- Rendering in `GamePanel` draws N/W walls per cell + S/E only at unlit edges (prevents double-draw bug).  
- All gameplay flows through `Retroquest.handleKey()` → controller → `GamePanel.repaint()`.

## 2. Functional Requirements

### 2.1 Map Generation & Queries (`Dungeon.java`)

| ID       | Requirement |
|----------|-------------|
| FR-GEN-01 | `generate(int depth)` → `char[200][200]` (0-based). Cells = `'.'` or `I P t s A f g H R` (spec nibble 1-9). |
| FR-GEN-02 | Depth clamped 1–50. Deterministic 16-bit math (`XO/YO/ZO` constants). |
| FR-GEN-03 | Outer borders forced to walls. |
| FR-GEN-04 | `getNorthWall`/`getWestWall`/`getSouthWall`/`getEastWall` + `canMove*` methods. |
| FR-GEN-05 | Special filtering: `I` only depth 1; `P` suppressed depth 50. |

### 2.2 Navigation & Events (`DungeonController` + `Retroquest.handleKey`)

| ID       | Requirement |
|----------|-------------|
| FR-NAV-01 | Enter/exit, up/down levels, pit, teleporter (all with animations). |
| FR-NAV-02 | **Stairs `'s'`**: auto-prompt on step — `handleDungeonSpecial()` dispatches `case 's' -> handleStairs()` after every move. **E** on a stairway tile is a manual second route to the same prompt, not the only one. Options are context-sensitive: depth 1 offers "Exit to Surface" / "Go Down (to level 2)", the deepest level offers only "Go Up", middle levels offer both — plus a "Cancel" entry appended in every case. Max depth comes from the overworld's `MapData.maxDungeonDepth` (default 50). |
| FR-NAV-03 | Movement: **both** checks must pass. `Retroquest.handleMovement` first calls the appropriate `Dungeon.canMove*()`, then `Player.move(dx, dy, map)`, which independently requires `isWalkable(map[newY][newX])`. A cell can be wall-open but tile-blocked, and then the move fails. First-person movement goes through `DungeonWallQuery.canMove()` instead: procedural delegates to `Dungeon.canMove*`, authored tests `TileRegistry.getByIdSafe(dest).isWalkable()`. |
| FR-EVT-01 | After every top-down dungeon move: `revealVisibleArea()` → `handleDungeonSpecial()` → `checkGroundLoot()` → `checkDungeonEncounter()`. First-person (`wireframeMove`) reorders this deliberately: reveal → loot → **encounter first**, and only fires `handleDungeonSpecial()` if no combat and no prompt started, so a monster is fought before a special can stack a prompt on top of it. |

### 2.3 Special Feature Handling

`handleDungeonSpecial()` is auto-called every step and returns immediately if
`messageLog.isPromptActive()`. **E** re-invokes it manually. The dispatch chars are all as written
below — note they are the lowercase/uppercase forms the code actually uses, not the uppercase
shorthand earlier versions of this document used.

| Special | Handler | Behaviour |
|---------|---------|---------|
| `'P'` | `handlePit()` | Falls to the next level, damage scaled by depth. |
| `'s'` | `handleStairs()` | Context-sensitive up/down/exit prompt. |
| `'t'` | `handleTeleporter()` | Teleporter prompt. |
| `'A'` | `handleAltar()` | Depths 1-3: corrupted shrine (quest/boss); else divine favour. |
| `'f'` | `handleFountain()` | Fountain outcomes. |
| `'g'` | `handleGlowingCube()` | Glowing cube. Also reachable by **E** directly from `handleKey`. |
| `'H'` | `handleThrone()` | Throne outcomes, including the champion fight. |
| `'I'` | `handleInn()` | Dungeon inn (depth 1 only — the generator suppresses `I` elsewhere). |
| `'R'` | `handlePuzzle()` | Lava riddle (multiple choice, INT hint) or rune lock (free-text, WIS hint). |
| `'n'` | `handleSpinner()` | Randomises facing. |
| `'c'` | `handleChute()` | Drops the player to a lower level. |
| `'q'` | `handleRiddleDoor()` | See below. |
| `'M'` | `handleMemoryPool()` | Memory pool. |

### `'q'` Riddle Door

Added to `data/tiles.json` today — the riddle code already existed but had no tile. Category
Dungeon, sprite `dungeon/door`, `blocksVision: true`, `onStepEffect` `none`, and
**`walkable: true`**.

The riddle pool is chosen from the authored dungeon name: `rootvault` → `NATURE_RIDDLES`,
`archive_of_tears` / `forgotten_city` → `SHADOW_RIDDLES`, `war_beneath` / `iron_pit` →
`WAR_RIDDLES`, `cradle_of_shards` → `CRADLE_RIDDLES`, anything else (including `pressure_temple`,
which has three authored `'q'` tiles) → `STORM_RIDDLES`. Which riddle within the pool is a
deterministic hash of position and depth: `abs(px*31 + py*17 + depth*7) % riddles.length`.

Outcomes (`resolveRiddleDoor`):
- **Correct** → the feature is marked exhausted, `+50 + depth*20` XP. Re-stepping logs
  "The riddle door stands open."
- **Wrong** → `5 + depth*3` damage, `gameOver` if lethal. The feature is *not* exhausted, so the
  riddle can be attempted again.

**The tile does not actually block passage.** It is walkable, `DungeonWallQuery.isDoorTile()` does
not include `'q'`, and `resolveRiddleDoor` never replaces the tile — so the player can walk through
a riddle door whether or not they answer, and answering only buys XP or costs HP. The in-code
comment claiming the tile is replaced with floor is stale.

### 2.4 Vision & Persistence

- `VisionSystem.computeBrightness()` — radial light with line-of-sight, not BFS. Dungeon ambient is
  0.0; the player's light radius comes from `Player.getVisionRadius()`, tiles with `lightRadius > 0`
  add their own, falloff is `1 - (dist/radius)^1.5`, and the ray test walks wall segments between
  adjacent cells (both `WALL` and `DOOR` block).
- Explored-but-unlit dungeon tiles are remembered at brightness 0.12; anything brighter than that
  marks the cell revealed.
- Revealed cells are stored per depth in `Retroquest.revealed` and persisted in `SaveData` as
  base64 bitsets (`revealedBitset`; towns use `townRevealedBitset`).
- Cells below `VISIBILITY_THRESHOLD` (0.02) paint solid black; everything else gets a black alpha
  overlay of `(1 - brightness)`.

### 2.4a View Modes

`DungeonController.modeForIsland(overworldName)` picks the renderer, and it applies to authored
dungeons as well as procedural ones:

| Overworld | Mode | Renderer |
|---|---|---|
| `lirandel` | `TOP_DOWN` | `GamePanel`'s overhead grid pass |
| `sylvandar` | `TEXTURED` | `TexturedDungeonRenderer` |
| all others | `WIREFRAME` | `WireframeDungeonRenderer` |

`enterAuthoredDungeon` promotes a `TOP_DOWN` result to `WIREFRAME`, so an authored dungeon is never
drawn overhead. Islands 5-7 previously fell back to the island-1 top-down view; they now get
wireframe like every other non-Lirandel island. `DungeonViewState` holds the mode, the minimap
toggle and the authored dungeon name, and is **not** persisted — it is recomputed on entry.

### 2.5 Rendering (`GamePanel.paintComponent`)

**Top-down dungeon path only** — first-person modes bypass all of this and delegate to
`TexturedDungeonRenderer` / `WireframeDungeonRenderer`.

| Feature | Requirement |
|---------|-------------|
| REND-01 | Lighting: cells below `VISIBILITY_THRESHOLD` (0.02) paint solid black; every other cell gets a black alpha of `(1 - brightness)` from the `VisionSystem` buffer. |
| REND-02 | Floor: `dungeon/dungeon_floor` asset (fallback brown). |
| REND-03 | Specials: `Dungeon.getSpecial()` (1-based) → asset keys `dungeon/inn`, `/pit`, `/teleporter`, `/stairway`, `/altar`, `/fountain`, `/cube`, `/throne`, `/puzzle`. **Note**: the renderer also has a `'B'` → `dungeon/box` case that the generator can never produce — see gaps. |
| REND-04 | Walls: **only** draw North & West per cell (thick, ~30 % tile size). South & East drawn **only** if the neighbouring cell is below `VISIBILITY_THRESHOLD` (dark-edge border). |
| REND-05 | Wall/door visuals: custom `drawHorizWall` / `drawVertWall` with torch-lit stone palette, mortar lines, wooden planks, central gap for doors. |
| REND-06 | Animations: `startDungeonEntryAnimation`, `startDungeonDescentAnimation`, `startDungeonExitAnimation`, `startTeleportAnimation` (star-tunnel effect) skip normal map render. |
| REND-07 | Player drawn on top (asset or `@` fallback) — **skipped entirely** in first-person modes, where the player is the camera. |

**During animations**: map rendering skipped; only animation paints.

## 3. Non-Functional Requirements

| ID | Requirement |
|----|-------------|
| NFR-DET-01 | 100 % deterministic generation & wall queries. |
| NFR-PERF-01 | Instant generation; rendering ≤ 60 fps even at max zoom. |
| NFR-COMP-01 | Exact procedural room values + visual wall/door style. |
| NFR-SAVE-01 | Revealed map persisted per depth. |
| NFR-UI-01 | All interactions via `MessageLog`; animations triggered exactly as in controller. |

## 4. Interfaces & Dependencies (fully validated)

**Dungeon** (static)  
**DungeonController** → `Retroquest` (player, currentMap, revealed, log, animations, etc.)  
**Retroquest** → `GamePanel` (repaint, camera, revealed getter)  
**GamePanel** → `Dungeon` (getSpecial, wall queries), `ImageAssetRegistry`, animation classes.

## 5. Identified Gaps & Needs (final)

| Category | Status | Details / Recommendation |
|----------|--------|--------------------------|
| **Box 'B'** | Open | `GamePanel` still has a `case 'B' -> "dungeon/box"` sprite branch, but `Dungeon.getSpecial` never returns `'B'` — spec value 9 maps to `'R'` (Puzzle). Dead code in the renderer. |
| **Specials on non-walkable tiles** | **New Open** | `handleDungeonSpecial` dispatches `'n'` (spinner), `'c'` (chute) and `'M'` (memory pool), but in `data/tiles.json` those ids are Barrel, Counter and Pillar — all `walkable: false`. The player can never stand on one, so those three handlers cannot fire. |
| **Special-set mismatch** | **New Open** | `DungeonWallQuery.isSpecialTile()` is `"AfsHtPIBLnkcqM"`. It omits `'g'` and `'R'` (so cube and puzzle never fire in authored dungeons) and includes `'B'`, `'L'` and `'k'`, which `handleDungeonSpecial` does not handle. |
| **Dungeon tile-state key** | **New Open** | Exhausted-feature and override state is keyed `"dungeon:" + depth` with no dungeon name, so a solved special at (x,y) on level 3 of one authored dungeon reads as solved at (x,y) on level 3 of every other one. `TileStateManager`'s javadoc documents the intended `dungeon:<name>:<depth>` form; nothing produces it. |
| **Quest Hard-coding** | Open | Depths 1-3 + “corrupted_shrine” still baked. **Future**: data-driven. |
| **Testing** | Open | No unit tests anywhere in the repo — `src/test` does not exist and `pom.xml` has no test dependency or surefire plugin. |
| **Assets** | Open | Hard-coded sprite keys in renderer. **Recommendation**: Move to `TileDefinition` or enum for extensibility. |
| **Map Caching** | Low | Regeneration cheap; optional cache if >50 level changes become common. |

## 6. Acceptance Criteria

- Deterministic generation verified by `getRoomValue`.  
- Fog, walls, specials, and animations render exactly as coded (no double walls, correct fog edges).  
- Full dungeon loop (enter → move → specials → loot → stairs → exit) works on fresh game and after save/load.  
- No soft-locks at depth 1/50 or during animations.  

## 7. Next Steps / Open Questions

1. Resolve `'B'` box inconsistency (restore or remove).  
2. Add deterministic generation unit tests.  
3. Provide `SaveData.java` / animation classes only if save/animation edge cases arise.  
4. Consider extracting dungeon rendering into `DungeonRenderer` for cleaner separation.

## 8. Authored Dungeons

Authored dungeons live in `data/dungeons/` as `<name>_<level>.rfmap`. They are entered from a `'D'`
overworld tile whose `initialTileStates` entry carries `dungeonName` (see `MapNavigation.md`
§ Dungeon Entry). Level 1 loads on entry; deeper levels load as `<name>_<n>.rfmap`.

The files currently on disk, by level count:

| Dungeon | Levels | Island |
|---|---|---|
| `storm_spire` | 5 | Zephyrion (3) |
| `rootvault` | 5 | Sylvandar (4) — levels 1-5 all have entry points |
| `pressure_temple` | 3 | Thalorax (5) |
| `boneyard_trench` | 3 | Thalorax (5) |
| `leviathan_eye` | 1 | Thalorax (5) |
| `archive_of_tears` | 3 | Umbryn (6) |
| `forgotten_city` | 3 | Umbryn (6) |
| `ember_caverns` | 3 | Pyralis (2) |
| `war_beneath` | 3 | Bellorak (7) — wired via `dungeonName` tile state |
| `iron_pit` | 3 | Bellorak (7) — wired via `dungeonName` tile state |
| `cradle_of_shards` | 8 | Endgame; its entrance is a **key-gated dungeon entrance** (`requiredKeyId: key_of_iron`), not a portal |

Each dungeon has a dedicated colour palette in the wireframe/textured renderers (storm, deep ocean,
shadow, war, cradle themes).

### Additional Dungeon Special Chars
| Char | Name | Handler | Status |
|------|------|---------|--------|
| `'n'` | Spinner | `handleSpinner()` | Cannot fire — `'n'` is Barrel in `tiles.json`, `walkable: false` |
| `'c'` | Chute | `handleChute()` | Cannot fire — `'c'` is Counter, `walkable: false` |
| `'M'` | Memory Pool | `handleMemoryPool()` | Cannot fire — `'M'` is Pillar, `walkable: false` |
| `'q'` | Riddle Door | `handleRiddleDoor()` | Works. Walkable, does not block passage — see §2.3 |
| `'k'` | Dark Zone | (renderer only) | In `DungeonWallQuery`'s special set but no handler; `'k'` is Bookshelf, `walkable: false` |

---


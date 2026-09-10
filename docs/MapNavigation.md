# Map & Navigation System — RetroQuest

**Last updated:** September 2026

## 1. Overview

The map system manages a multi-map architecture: overworld maps, town interiors, and the procedural dungeon. Six files cover the core:

- **`MapData.java`** — Serializable map container (tile grid, spawn difficulty, NPCs, entrances, teleporters).
- **`OverworldManager.java`** — Loads/saves overworld `.rfmap` files from `data/overworlds/`.
- **`NavigationController.java`** — Town entry/exit, overworld teleporters, tile step-effects, map switching.
- **`Town.java`** — Town interior loading, default generation, NPC setup.
- **`TownEntrance.java`** — Record linking an overworld tile to a town name.
- **`OverworldTeleporter.java`** — Record linking an 'O' tile to a target overworld + position.

---

## 2. Map Data Model (`MapData.java`)

### MapType Enum

`OVERWORLD` | `TOWN`

### Fields

| Field | Type | Description |
|-------|------|-------------|
| `type` | MapType | Overworld or town |
| `name` | String | Map name (default: "New Map") |
| `width`, `height` | int | Dimensions (max 2048) |
| `tiles` | char[][] | 2D tile grid |
| `spawnDifficulty` | int[][] | Per-tile spawn level (0–99) |
| `npcs` | List\<NPC\> | NPC entities on this map |
| `townEntrances` | List\<TownEntrance\> | Town portals (overworld only) |
| `overworldTeleporters` | List\<OverworldTeleporter\> | 'O' tile destinations |
| `interiorEntryX/Y` | int | Player spawn inside a town or authored dungeon (default 10, 13) |
| `maxDungeonDepth` | int | Deepest level for dungeons entered from this overworld (default 50) |
| `dungeonLevel`, `dungeonGroup`, `levelName`, `level` | | Authored-dungeon metadata |
| `initialTileStates` | Map\<String, TileState\> | Per-coordinate authored data keyed `"x,y"` — carries `dungeonName`, `requiredKeyId`, `lockedMessage`, `open`, teleport overrides, etc. |

### Factory & Persistence

- `createNew(type, name, w, h)` — fills with `'.'` (overworld, spawnDifficulty=5) or `'F'` (town, spawnDifficulty=0).
- `save(File)` / `load(File)` — Gson JSON with `ColorAdapter`. Auto-initializes null lists on load.
- `resize(newW, newH)` — resizes grids; culls entities outside new bounds.

### File Format (`.rfmap`)

JSON containing all fields. Stored in `data/overworlds/` (overworlds) or `data/towns/` (towns).

---

## 3. Overworld Management (`OverworldManager.java`)

- Directory: `data/overworlds/`
- Starting map comes from `GameConfig.getStartingOverworld()`, default `lirandel`.
- `loadOverworld(name)` — loads `.rfmap`; creates and persists a new 140×90 map only if the file is
  genuinely missing. If the file **exists but will not parse**, it falls back to an empty map
  *in memory only*, sets `loadFailed`, and `saveCurrent()` then refuses to write — so an authored
  island is never destroyed by a bad parse. Check `isLoadFailed()` if you need to know.
- Seven overworlds exist, with these authored sizes: lirandel 120×100, pyralis 180×150, and
  zephyrion, sylvandar, thalorax, umbryn, bellorak all 230×190.
- `getSpawnDifficulty(x, y)` — bounds-checked; returns 5 if out of bounds.
- `getAllOverworldNames()` — scans directory for `.rfmap` files.
- `saveCurrent()` — persists current map to disk.

---

## 4. Town System (`Town.java`)

### Construction

`Town(name, worldDoorX, worldDoorY, interiorEntryX, interiorEntryY)` loads the town interior from `data/towns/{name}.rfmap`. If the file doesn't exist, generates a default classic-style interior (40×30) with:
- Floor (`F`) base, wall (`W`) border.
- Center exit door (`d`).
- Shop building (2–14 × 2–10) with counter.
- Inn building (25–38 × 2–10) with counter.
- Default Shopkeeper at (8,5) and Innkeeper at (31,5).

### Fields

| Field | Description |
|-------|-------------|
| `name` | Town name |
| `interiorMap` | char[][] tile grid |
| `worldDoorX/Y` | Overworld position of entrance |
| `interiorEntryX/Y` | Player spawn position inside town |
| `npcs` | List of NPCs in town |

---

## 5. Navigation Flow (`NavigationController.java`)

### Town Entry

1. Player steps on a tile with a `TownEntrance` → `enterTownAtPlayerPosition()`.
2. Plays `enter_town` sound, starts `TownEntryAnimation`.
3. On animation complete → `finishTownEntry()`:
   - Creates `Town` object (loads interior from file).
   - Sets `game.currentTown`, repositions player to `interiorEntryX/Y`.
   - Caches `lastSafeTownName` for Teleport spell.
   - Progresses EXPLORE-type quests.

### Town names: key vs. label

A town name is an **identifier**, not a label. `ashfen_village` names the `.rfmap` file, keys
the fog-of-war and tile-state maps as `town:<name>`, is written into saves as `currentTownName`
and `lastSafeTownName`, is matched against `TownEntrance.townName()`, and is the target of
EXPLORE quests such as `the_hollow`. It must stay exactly as authored.

`Town.displayName(String)` (and the instance `getDisplayName()`) turns it into `Ashfen Village`
**at the point of display, and nowhere else** — the entry and exit banners, the message log, the
combat header, the death and quest-log overlays, and the save-slot label. `Town.getName()`
remains the raw key and every caller of it is a key use. Saves written before this carry the raw
name in their baked slot label, so `SaveData.getDisplayName()` patches that one field on read
rather than rewriting anyone's save.

### Town Exit

1. Player triggers exit → `exitTown()`.
2. Plays `leave_town` sound, caches exit door coordinates, starts `TownExitAnimation`.
3. On animation complete → `finishTownExit()`:
   - Restores overworld as current map.
   - Repositions player to `worldDoorX/Y`.
   - Clears `currentTown`.

### Overworld Teleporter

1. Player steps on `'O'` tile → `checkOverworldTeleporter()`.
2. Looks up `OverworldTeleporter` at player position.
3. Plays `teleport` sound, starts 4200ms teleport animation.
4. `finishTeleport()` → loads target overworld, repositions player (uses map center if targetX/Y = −1).

### Tile Step Effects

`triggerTileEffect()` applies the tile's `TileEffect` after each step:

Thirteen effect types are dispatched in the `switch` at the end of `triggerTileEffect()`:

| Effect Type | Behavior |
|-------------|----------|
| `none` | No effect |
| `island_portal` / `teleport` | Same handler. Checks `requiredKeyId` against key ring then inventory; on failure logs `lockedMessage` and stops. Otherwise reads `map`/`x`/`y` and starts the transition — first-time forward island portals show `DivineAudienceOverlay` and defer the teleport to its dismissal |
| `damage` | `amount` HP loss with optional `message`; `gameOver` if lethal |
| `encounter` | Forces an immediate encounter using the tile's `spawnWeights` |
| `trap_once` | One-time damage trap; persists `"triggered"` on the tile state |
| `set_tile` | Writes `tileId` to an absolute `x,y`; persists a tile override |
| `toggle_door` | Flips an `open` flag, swapping between `openChar` and `closeChar` (defaults `'F'` / `'#'`) |
| `pressure_plate` | Toggles a *remote* `x,y` between `activateChar` and `deactivateChar`; `once=true` makes the plate inert afterwards |
| `shrine` | One-time: `message`, `god` + `favor` (default 5), optional `xp`, optional `discoveredTileId` swap |
| `treasure` | One-time: optional `requiredItemId` gate, then `itemId` or `LootGenerator` by `tier`, optional `gold`, optional `discoveredTileId` swap |
| `ambush` | One-time forced fight vs `monsterId`; on victory persists `"defeated"` and grants `lootItemId` / `lootTier` |
| `geyser` | `chance` % (default 50) to deal `amount` damage |
| `wind_current` | Pushes the player up to `distance` tiles in `direction` (north/south/east/west), stopping at the first non-walkable tile |
| `lore` | Logs `message`; `oneTime=true` persists `"read"` |

A per-instance `TileState` carrying a `teleport_map` key takes priority over the tile definition's
effect entirely, using `teleport_x` / `teleport_y` / `requiredKeyId` / `lockedMessage`.

`locked_door` is deliberately **not** in that switch — a locked door is non-walkable, so it is
handled *before* the move by `NavigationController.tryUnlockTile(x, y)`, called from
`Retroquest.handleMovement()` when the normal walkability check fails:

- Checks the key ring first, then the 20 inventory slots, for `requiredKeyId`.
- **The key is never consumed.** There is no `removeKey` on the key ring and `tryUnlockTile` does
  not clear the inventory slot — an island key opens its door as many times as you like.
- On success the tile becomes `unlockedTileId` (default `'F'`), and the change is written to
  `SaveData` via `TileStateManager.setOverride`, not to a player flag.
- A tile state authored with `open = true` grants passage with no key check at all.
- `tryUnlockTile` is only reached on the overworld/town movement branch. Neither the top-down
  dungeon branch nor `wireframeMove()` calls it, so locked-door tiles inside a dungeon cannot be
  opened.

Four locked-door tiles exist in `data/tiles.json`: `'|'` Locked Wooden Door, `'['` Locked Iron
Gate, `'{'` Magic Barrier and `''` Locked Iron Gate (Overworld).

`applyPersistedMutations()` re-applies every stored tile override on each map transition and then
seeds any `initialTileStates` authored in RetroForge that the save has not seen yet.

### Island Portal Chain

Island order is **lirandel → pyralis → zephyrion → sylvandar → thalorax → umbryn → bellorak**, and
each forward portal is gated by exactly one key earned on the island before it. The gates, as they
appear in `data/tiles.json`:

| Forward portal → | `requiredKeyId` |
|---|---|
| Pyralis | `key_of_tides` |
| Zephyrion (Storm Archipelago) | `key_of_embers` |
| Sylvandar (Verdant Mangroves) | `key_of_gales` |
| Thalorax (Abyssal Depths) | `key_of_roots` |
| Umbryn (Shadow Trenches) | `key_of_depths` |
| Bellorak (Golden War Isles) | `key_of_echoes` |
| Cradle of Shards | `key_of_iron` |

Return portals carry no key requirement. Keys live on `Player.keyRing`, are never consumed, and are
routed there automatically — `Player.completeQuest` sends any `Item.Type.KEY` reward to `addKey()`
instead of the inventory.

Note `TileRegistry.createDefaultTiles()` seeds the Thalorax portal with `key_of_gales` rather than
`key_of_roots`. The live `data/tiles.json` is correct; the seed only matters if that tile is ever
regenerated.

### Dungeon Entry

A `'D'` tile is examined with **E**. `Retroquest.handleKey` looks up `initialTileStates` at the
player's `"x,y"` on the **live MapData** (not the save's tile-state copy) and reads three keys:

- `requiredKeyId` (+ `lockedMessage`) — if set and the player lacks the key, logs and stops. Like
  every other key check, the key is not consumed. This is how the Cradle of Shards entrance is
  gated: it is a key-gated dungeon entrance, not a portal.
- `dungeonName` — present ⇒ `DungeonController.enterAuthoredDungeon(name)`, which loads
  `data/dungeons/<name>_1.rfmap` and spawns the player at that map's `interiorEntryX/Y`. Absent ⇒
  `enterDungeon()` and the procedural dungeon.

Entrances are authored in RetroForge with the Dungeon Placer tool
(`DungeonPlacementDialog.placeEntrance()`), which writes the `'D'` tile and the `dungeonName` tile
state together, and removes `dungeonName` when the entrance is set back to procedural.

### Map Switching

`switchOverworld(mapName)` — loads a different overworld by name, updates current map reference and camera.

---

## 6. Data Records

### TownEntrance

| Field | Type | Description |
|-------|------|-------------|
| `townName` | String | Target town name |
| `worldX`, `worldY` | int | Overworld tile position |

### OverworldTeleporter

| Field | Type | Description |
|-------|------|-------------|
| `x`, `y` | int | Position on current overworld |
| `targetOverworld` | String | Destination overworld name |
| `targetX`, `targetY` | int | Spawn position (−1 = map center) |

Both have no-arg Gson constructors with defaults.

---

## 7. Spawn Difficulty

The spawn difficulty grid controls encounter rate and monster level on the overworld:

- **Encounter rate**: `0.08 × tileModifier × (0.5 + difficulty × 0.05)` (see `CombatSystem.md` §2).
- **Monster level**: `1 + (difficulty × 49) / 99` — linear mapping from 0–99 to levels 1–50.
- Default value: 5 (overworld), 0 (towns — no encounters).
- Painted per-tile in RetroForge using the SPAWN_DIFFICULTY tool.
- Out-of-bounds queries return 5.

---

## 8. Map Architecture Diagram

```
┌─────────────────────────────────────────────┐
│              OverworldManager               │
│  loads/saves data/overworlds/*.rfmap        │
│  holds current MapData + map name           │
├─────────────────────────────────────────────┤
│                                             │
│  Overworld Map  ←──TownEntrance──→  Town    │
│  (tiles, NPCs,      (E tiles)      Interior │
│   spawnDifficulty,                  (loaded  │
│   teleporters)                      on entry)│
│                                             │
│  OverworldTeleporter                        │
│  (O tiles) ──→ Other Overworld Maps         │
│                                             │
├─────────────────────────────────────────────┤
│  Dungeon (procedural, separate system)      │
│  Entered via 'D' tile on overworld          │
│  Managed by DungeonController               │
└─────────────────────────────────────────────┘
```

---

## 9. Vision & Lighting (`VisionSystem`)

The old binary BFS fog-of-war is gone. `VisionSystem.computeBrightness(game)` builds a
`float[viewH][viewW]` brightness buffer every frame, 0.0 = black to 1.0 = full bright:

1. Fill with ambient. `DUNGEON_AMBIENT`, `TOWN_AMBIENT` and `OVERWORLD_AMBIENT` are **all 0.0f** —
   nothing is lit for free, on any map type.
2. Overlay remembered tiles: `REMEMBERED_DIM` 0.12 in dungeons, `REMEMBERED_DIM_TOWN` 0.22 in towns.
3. Cast light from the player at `Player.getVisionRadius(inDungeon, inTown)`, then from every tile
   in the viewport whose `TileDefinition.getLightRadius() > 0`.
4. Falloff is `1 - (dist/radius)^1.5` (`FALLOFF_EXPONENT = 1.5f`), and each cell takes the
   brightest contribution.
5. Every lit cell needs line of sight. Surface rays walk Bresenham and stop at the first tile whose
   `blocksVision()` is true; dungeon rays instead test the wall segment between each pair of cells
   (`WALL` and `DOOR` both block), allowing a diagonal step only when at least one of the two
   L-shaped paths is open.
6. Anything above the remembered-dim level is written back to the revealed map.

Thresholds: `VISIBILITY_THRESHOLD` 0.02 (below this a tile paints solid black),
`NPC_VISIBILITY_THRESHOLD` 0.08 (below this an NPC is not drawn at all).

Persistence: dungeon reveal per depth and town reveal per town name, both in `SaveData` as base64
bitsets (`revealedBitset`, `townRevealedBitset`). The overworld is lit the same way but its reveal
is not persisted.

---

## 10. Known Gaps

1. ~~**No overworld fog-of-war**~~ — **Fixed.** `VisionSystem` lights the overworld the same way it lights towns and dungeons; only the overworld's revealed set is not persisted.
2. **Town map state not persisted** — town NPCs and layout reset on each entry (loaded fresh from `.rfmap`); only tile overrides and revealed tiles survive.
3. ~~**No dynamic map changes**~~ — **Fixed.** Tiles can be modified at runtime via `set_tile`, `toggle_door`, `pressure_plate`, the one-time discovery effects and `locked_door`. Changes persist in `SaveData.tileStates` via `TileStateManager` and are re-applied on map transition.
4. **Single entry point per town** — each town has one `interiorEntryX/Y`. No multi-entrance support.
5. **Teleporter target validation** — no check that `targetOverworld` file exists before teleporting.
6. **Default spawn difficulty 5** — out-of-bounds returns 5, which means walking off-map edge still generates encounters.
7. **Locked doors do not consume keys** — `tryUnlockTile` never removes the key, and `Player` has no `removeKey` at all. Intentional for island keys, but it means a one-shot door is impossible today.
8. **Five effect types are unused** — `encounter`, `trap_once`, `set_tile`, `toggle_door` and `pressure_plate` are implemented but no tile in `data/tiles.json` declares them.
9. **Dungeon map keys omit the dungeon name** — tile state is keyed `dungeon:<depth>`, so the same coordinate at the same depth in two different authored dungeons shares one entry.

# RetroQuest - Tiles.md

**Tile Requirements Document**
*Version 1.6 — September 2026*
**Purpose**: Single source of truth for all tile definitions and implementation rules derived directly from the game's source code (`TileDefinition.java`, `TileEffect.java`, `TileRegistry.java`, `TileEditor.java`, and `tiles.json`).

---

## 1. Overall Tile Requirements (Extracted from Source Code)

Every tile in the game is an instance of `TileDefinition` and **must** conform to the following structure. These fields are loaded from / saved to `data/tiles.json` and fully editable in the in-game **Tile Editor**.

### Core Fields (all tiles)
- `id`: **Single character** (char) — **must be unique** across the entire registry.
- `name`: Human-readable display name (String).
- `category`: One of: `"Overworld"`, `"Town"`, `"Dungeon"`, `"Special"`, `"Custom"`, or `"System"`.
- `editorColor`: AWT `Color` (used only in the map editor palette).
- `walkable`: boolean — determines if the player can step on it.
- `spriteKey`: String (e.g. `"overworld/grass"`, `"town/bookshelf"`) — must match an entry in `ImageAssetRegistry`.

### Gameplay & Visual Fields

- `isLiquid`: boolean — **active**: `EncounterController` counts liquid tiles in the four cardinal
  neighbours and boosts aquatic monster spawn weights (1 tile = 3×, 2 = 5×, 3 = 8×, 4 = 12×)
- `blocksVision`: boolean — **active**: `VisionSystem`'s surface line-of-sight test stops at the
  first tile whose `blocksVision()` is true
- `isSafeZone`: boolean — **active**: suppresses random encounters on this tile
- `lightRadius`: int — **active**: any tile in the viewport with `lightRadius > 0` casts its own
  light in `VisionSystem`, with the same falloff and line-of-sight rules as the player's
- `stepSound`: String (e.g. `"step_default"`, `"step_lava"`, `"step_water"`) — still not read by
  game logic
- `encounterRateMod`: double (default 1.0) — multiplier for random monster encounter rolls (**active**)
- `description`: String (flavor text)

### Special Systems

- `onStepEffect`: `TileEffect` object — fires every time the player steps on this tile
  - `type`: one of the following (must be lowercase):

    | Type | Used by a tile today? | Params | Behavior |
    |------|---------|--------|----------|
    | `"none"` | 186 tiles | — | No effect |
    | `"island_portal"` / `"teleport"` | 12 tiles | `map`, `x`, `y`, `requiredKeyId` (optional), `lockedMessage` (optional) | Same handler. Checks key ring then inventory for `requiredKeyId`; **the key is not consumed**. First-time forward island portals show `DivineAudienceOverlay` and defer the teleport |
    | `"damage"` | 7 tiles | `amount`, `message` (optional) | Deals damage each step; logs message if provided |
    | `"locked_door"` | 4 tiles | `requiredKeyId`, `unlockedTileId`, `message`, `lockedMessage` | See below — handled pre-move, not in this switch |
    | `"treasure"` | 2 tiles | `itemId` or `tier`, `gold`, `requiredItemId`, `lockedMessage`, `discoveredTileId` | One-time loot find |
    | `"ambush"` | 2 tiles | `monsterId`, `lootItemId`, `lootTier` | One-time forced fight; only marked `defeated` on victory |
    | `"lore"` | 2 tiles | `message`, `oneTime` | Logs flavour text |
    | `"shrine"` | 1 tile | `god`, `favor` (default 5), `xp`, `message`, `discoveredTileId` | One-time favour grant |
    | `"geyser"` | 1 tile | `chance` (default 50), `amount` | Random chance of damage |
    | `"wind_current"` | 1 tile | `direction`, `distance` | Pushes the player, stopping at the first non-walkable tile |
    | `"encounter"` | **0 tiles** | `message` (optional) | Forces immediate combat, using the tile's `spawnWeights` |
    | `"trap_once"` | **0 tiles** | `amount`, `message` | Deals damage once; persists `"triggered"` on the tile state |
    | `"set_tile"` | **0 tiles** | `x`, `y`, `tileId`, `message` | Changes the tile at an absolute (x,y); persists a tile override |
    | `"toggle_door"` | **0 tiles** | `openChar`, `closeChar` | Flips an `open` flag between the two chars (defaults `'F'` / `'#'`) |
    | `"pressure_plate"` | **0 tiles** | `x`, `y`, `activateChar`, `deactivateChar`, `once` | Toggles a *remote* tile |

    The five "0 tiles" types are implemented and selectable in the Tile Editor but no shipped tile
    declares them.

  - `params`: Map<String, String> — the union of every key listed above.

- `spawnWeights`: Map<String, Integer> — optional per-tile monster spawn weight overrides (monsterID → relative weight, default 1).

### Persistence for Stateful Effects

State no longer lives in `Player.flags`. Every stateful effect writes through `TileStateManager`
into `SaveData.tileStates`, a `Map<String, TileState>` keyed `"<mapKey>:<x>,<y>"`:

| Effect | What it stores |
|---|---|
| `trap_once` | `"triggered"` = `"true"` |
| `shrine` | `"discovered"` |
| `treasure` | `"looted"` |
| `ambush` | `"defeated"` (only on victory) |
| `lore` (`oneTime`) | `"read"` |
| `toggle_door` / `pressure_plate` | `"open"` boolean |
| `set_tile`, unlocked door | a tile-char override via `TileStateManager.setOverride` |

Map key format: `"dungeon:<depth>"` | `"town:<name>"` | `"overworld:<name>"`. Note the dungeon form
carries no dungeon name, so authored dungeons at the same depth share tile-state coordinates.

`NavigationController.applyPersistedMutations()` re-applies every stored override on each map
transition (town entry/exit, overworld switch, teleport, game load) and then seeds any
`initialTileStates` authored in RetroForge that the save has not seen yet.
`TileStateManager.migrateFromPlayerFlags(data)` upgrades saves that used the old `mutated:*` /
`trap_once:*` player flags.

**Additional Rules from Code**
- Tiles with category `"System"` are hidden from the editor palette.
- `TileRegistry` loads/saves the JSON file automatically.
- The Tile Editor validates ID uniqueness on save and auto-assigns the next available char on creation.
- Tiles can be deleted via the DELETE button; maps using the deleted tile are rewritten with a chosen replacement.

---

## 2. Tile Inventory

`data/tiles.json` currently holds **218 tile definitions**, by category: Town 104, Overworld 86,
Dungeon 14, Special 13, System 1. The per-tile table that used to live here had drifted out of date
and has been removed — read `data/tiles.json` or the Tile Editor, which are the actual sources.

Notes worth knowing before you edit that file:

- **Duplicate ids exist.** `'?'` appears 27 times (26 mis-encoded legacy "Letter A"–"Letter Z" tiles
  plus one real entry; the correctly encoded PUA letter tiles ``–`` are a separate,
  working set). `'^'` appears twice — Bridge (Overworld, walkable) and Tiled Roof (Town, not
  walkable). `TileRegistry.getById` returns the **first** match, so the second `'^'` entry is
  unreachable and every `'?'` resolves to "Letter A".
- **Newly added:** `'q'` Riddle Door (Dungeon, sprite `dungeon/door`, `blocksVision: true`,
  `walkable: true`, no step effect). It has no counterpart in `TileRegistry.createDefaultTiles()`,
  so if `tiles.json` is lost the tile silently degrades to the Grass default.
- Locked doors are `'|'` Locked Wooden Door, `'['` Locked Iron Gate, `'{'` Magic Barrier and
  `''` Locked Iron Gate (Overworld) — all `walkable: false`.
- Island portals occupy 12 tiles: seven forward portals (each with a `requiredKeyId`) and five
  return portals (no key).

---

## 3. Orphan Sprites (Available but Unassigned)

These sprites exist in `src/main/resources/tiles/dungeon/` but are not assigned to any tile definition:
- ~~`dungeon/door`~~ — now used by the `q` Riddle Door tile
- `dungeon/dungeon_floor` — used directly by `GamePanel` dungeon renderer as floor background
- `dungeon/portal` — available for a portal tile variant

---

## 4. Known Gaps

### High Priority (bugs / misleading data)
- ~~**Lava (`L`) inconsistency**~~ — **Resolved.** `'L'` Lava is now `walkable: true` in
  `data/tiles.json`, so its `damage` onStepEffect, `stepSound` and `encounterRateMod` are live.
- **Dungeon specials on non-walkable tiles**: `DungeonController.handleDungeonSpecial()` dispatches
  `'n'` (spinner), `'c'` (chute) and `'M'` (memory pool), but those ids are Barrel, Counter and
  Pillar — all `walkable: false`. The player can never stand on them, so those handlers cannot fire.
  Either move the specials onto new walkable ids or make those ids walkable.
- **Duplicate tile ids**: see §2. `'?'` × 27 and `'^'` × 2 shadow real entries.
- **`damageOnStep` in tiles.json**: Some tile entries in `tiles.json` may carry a `damageOnStep` JSON field that has no corresponding field in `TileDefinition.java` (Gson ignores unknown fields silently). This is misleading for designers who may set it thinking it works. Audit tiles.json and remove any `damageOnStep` entries; use `onStepEffect.type="damage"` exclusively.

### Medium Priority
- **No runtime spriteKey validation**: Missing sprites fall back to `editorColor` silently. Add a startup warning in `TileRegistry.loadTiles()` or `GamePanel.loadAssets()` for each tile where `ImageAssetRegistry.get(spriteKey) == null`.
- **Spawn-weight editor not validated**: If a monster is deleted from `MonsterRegistry`, its entry persists silently in tile `spawnWeights` maps. Editor table is also unsorted. Fix: filter stale IDs and sort by name when opening the spawn weights panel in `TileEditor`.

### Low Priority / Reserved
- No animated tile or multi-frame sprite support — out of scope for retro tile style.
- No automatic migration for renamed spriteKeys — manual tiles.json update is acceptable.

---

## 5. Implementation Notes

- All new tiles **must** be added via the in-game **Tile Editor** (NEW TILE button + Ctrl+S).
- The editor auto-assigns a unique ID on creation.
- ID uniqueness is validated on save; duplicate IDs are rejected.
- Follow spriteKey naming: `overworld/xxx`, `town/xxx`, or `dungeon/xxx`.
- After editing, `TileRegistry.saveTiles()` persists changes to `data/tiles.json`.
- Use `onStepEffect.type="damage"` for all per-step damage. Do not add `damageOnStep` JSON fields.
- Use `onStepEffect.type="trap_once"` for one-shot traps. Use `"set_tile"` for pressure plates / door switches.
- Use `onStepEffect.type="locked_door"` for key-locked tiles. Set `requiredKeyId` to the item ID of
  the key that unlocks it, and `unlockedTileId` to the char of the tile to replace with.
  **Keys are never consumed** — `tryUnlockTile` checks the key ring then the inventory and removes
  nothing, and `Player` has no `removeKey` method at all. A door authored with `open = true` in its
  tile state opens with no key check.
- Locked doors are handled **before** the move, by `NavigationController.tryUnlockTile(x, y)` called
  from `Retroquest.handleMovement()` when the walkability check fails — so a locked-door tile must be
  `walkable: false` to work. That call site only exists on the overworld/town branch, so locked doors
  placed inside a dungeon cannot be opened.

### Locked Door Quick Reference

| Locked Tile | Char | Key Item | Unlocks To |
|-------------|------|----------|------------|
| Locked Wooden Door | `\|` | `rusty_key` | `d` (Wooden Door) |
| Locked Iron Gate | `[` | `iron_key` | `F` (Floor) |
| Magic Barrier | `{` | `magic_crystal` | `F` (Floor) |

Custom locked tiles can be created in TileEditor: set effect type to `locked_door`, fill in `requiredKeyId`, `unlockedTileId`, `message`, and `lockedMessage`.

**Next Steps**
1. Decide on Lava walkability (see gap §4).
2. Audit and clean `damageOnStep` entries from tiles.json.
3. Create real pixel art to replace placeholder sprites for locked tiles.
4. Add spriteKey validation warnings at startup.

Maintained by rwill2001.
Last updated: September 2026

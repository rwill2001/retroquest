# RetroForge Editor — RetroQuest

**Last updated:** September 2026

## 1. Overview

RetroForge is the standalone content creation tool for RetroQuest — 24 classes in `io.cannonforge.retroquest.editor`. It is a Swing-based map editor that loads and edits `.rfmap` files and provides access to seven sub-editors from its EDITORS menu (items, quests, monsters, NPCs, tiles, images, dialogue trees). Launch it as `io.cannonforge.retroquest.editor.RetroForge`, **from the repo root** — all `data/` paths are working-directory relative.

**Files:**
- **`RetroForge.java`** — Main JFrame with menu bar, toolbar, and sidebar.
- **`MapCanvas.java`** — Tile-editing canvas (34×21 viewport) with mouse/keyboard controls.
- **`TilePalette.java`** — Filtered tile selector panel.
- **`Tool.java`** — Enum for the 7 editing tools.

---

## 2. Tools

| Tool | Description | Controls |
|------|-------------|----------|
| `PENCIL` | Paint tiles with adjustable brush | Left-click/drag; mouse wheel = brush size (1–9) |
| `FILL` | BFS flood-fill contiguous tiles | Left-click |
| `SELECT` | Rectangular region select, copy and paste | Drag = select; Ctrl+C / Ctrl+V; Esc clears |
| `NPC_PLACER` | Place, edit, or delete NPCs | Left-click empty = create; left-click NPC = edit; right-click NPC = delete |
| `TOWN_PLACER` | Place town entrances ('E' tiles) | Left-click = place via `TownPlacementDialog`; double-click = open town |
| `DUNGEON_PLACER` | Place dungeon entrances ('D' tiles) | Left-click = place via `DungeonPlacementDialog`, which writes the `dungeonName` tile state for an authored dungeon (or removes it for a procedural one) |
| `SPAWN_DIFFICULTY` | Paint per-tile spawn difficulty (0–99) | Left-click/drag = paint; right-click = sample value |

That is the complete `Tool` enum — **seven values**. There is no teleporter placement tool; the
unused `TeleporterPlacementDialog` class has been removed.

---

## 3. MapCanvas

### Viewport

Fixed 34×21 tile viewport. Camera pans via arrow keys (smooth 16ms timer with ±2 tiles/tick velocity).

### Undo/Redo

Stack-based system with deep copies of the entire `MapData`:
- Max 40 undo states.
- `pushUndoState()` called before each edit; clears redo stack.
- `setMap()` clears both undo and redo stacks (prevents undo into a prior map).
- **Ctrl+Z** = undo, **Ctrl+Y** = redo.

### Rendering

For each visible tile:
1. Fill with `TileDefinition.getEditorColor()`.
2. If SPAWN_DIFFICULTY tool active: overlay red square with difficulty number.
3. Grid lines in dark gray.
4. NPCs: yellow circles with "N" label.
5. Town entrances: gold squares with "E" label.
6. Teleporters: cyan squares with "O" label.
7. Brush size indicator in bottom-right corner.

---

## 4. Tile Palette

Left sidebar panel showing tiles filtered by map type:

| Map Type | Tile Categories Shown |
|----------|----------------------|
| OVERWORLD | Overworld, Special, Custom |
| TOWN | Town, Special, Custom |
| DUNGEON | Dungeon, Special, Custom |

Each tile button shows the tile name (truncated to 22 chars) with its editor color as background. Tooltip shows full name + char ID. Clicking sets the canvas brush tile.

Palette refreshes when tiles are edited via the Tile Editor.

---

## 5. Menu Structure

### FILE
- **New Map** — creates OVERWORLD (140×90) or TOWN (40×30)
- **Load Map** — JFileChooser for `.rfmap` files
- **Save Map** — saves to remembered path or prompts
- **Resize Map** — `MapData.resize(w, h)`
- **Exit**

### EDIT
- **Undo** (Ctrl+Z) / **Redo** (Ctrl+Y)

### TOOLS
- All 7 `Tool` enum values, listed dynamically

### EDITORS (launches modal dialogs) — 7 entries

| Editor | Dialog Class | Data File |
|--------|-------------|-----------|
| Item Editor | `ItemEditorDialog` | `data/items.json` |
| Quest Editor | `QuestEditorDialog` | `data/quests.json` |
| Monster Editor | `MonsterEditorDialog` | `data/monsters.json` |
| NPC Editor | `NPCEditorDialog` | embedded in `.rfmap` |
| Tile Editor | `TileEditor` | `data/tiles.json` (then refreshes the palette) |
| Image Editor | `ImageEditor` | `src/main/resources/tiles/` |
| Dialogue Tree Editor | `DialogueTreeEditor` | embedded in NPC within `.rfmap`; shows "No NPCs on current map." if the map has none |

**Sound Editor is not on the menu.** `SoundEditor` is never instantiated from `RetroForge`; it has
its own `main()` and can only be launched as a standalone class.

Reachable through canvas interaction rather than a menu: `TownPlacementDialog`,
`DungeonPlacementDialog`, `TileInstanceDialog` (per-coordinate tile state) and `SaveSpriteDialog`.

### GAME
- **Playtest Now** — confirms, calls `saveMap()`, then runs `Retroquest.main()` via
  `SwingUtilities.invokeLater` — **in the same JVM and on the same EDT**, so the game and the editor
  share static registries and the working directory.
- **Game Settings...** — `GameSettingsDialog`, writes `data/game_config.json`.

---

## 6. Map File Format (`.rfmap`)

Gson-serialized JSON with `ColorAdapter` for `java.awt.Color`.

```json
{
  "type": "OVERWORLD",
  "name": "mainland",
  "width": 140,
  "height": 90,
  "tiles": [[".", "T", "#", ...], ...],
  "spawnDifficulty": [[5, 10, 3, ...], ...],
  "npcs": [
    {
      "name": "Kael",
      "spriteName": "wizard.png",
      "x": 70, "y": 45,
      "defaultDialog": "Welcome, traveler...",
      "dialogueTree": { ... }
    }
  ],
  "townEntrances": [
    { "townName": "stonehaven", "worldX": 50, "worldY": 30 }
  ],
  "overworldTeleporters": [
    { "x": 100, "y": 50, "targetOverworld": "mainland", "targetX": 70, "targetY": 45 }
  ]
}
```

### Storage Locations

| Map Type | Directory |
|----------|-----------|
| Overworld | `data/overworlds/` |
| Town | `data/towns/` |

---

## 7. Tile Editor (`TileEditor.java`)

Full tile definition editor launched from EDITORS menu. Edits `data/tiles.json`.

### Layout

```
TileEditor (JDialog)
├── Left: JTree tile browser (grouped by category)
├── Center: Property editor fields
└── Bottom: New / Save / Delete buttons
```

### Tile Tree

Tiles are organized in a `JTree` grouped by category:

| Category | Notes |
|----------|-------|
| Overworld | Standard terrain tiles |
| Town | Town interior tiles, plus a collapsed "Letters (A–Z)" sub-node |
| Dungeon | Dungeon tiles |
| Special | Portals, entrances, etc. |
| Custom | User-created tiles |
| System | Internal/reserved tiles |

Letter tiles (PUA `\uE041`–`\uE05A`, formula: `'\uE000' + letter`) are grouped under "Letters (A–Z)" within Town — collapsed by default to reduce clutter.

`TileCellRenderer`: category nodes render in amber bold; leaf nodes use alternating row colors.

### Editable Fields

| Field | Description |
|-------|-------------|
| Name | Display name |
| Char ID | Single-char map identifier (auto-assigned on new tile; validated unique on save) |
| Category | Tile category (Overworld / Town / Dungeon / Special / Custom / System) |
| Walkable | Whether the player can step on this tile |
| Sprite Key | Resource path under `src/main/resources/tiles/` |
| Editor Color | RGB hex — color shown in MapCanvas and TilePalette |
| On Step Effect | Effect type triggered when the player steps on this tile |

### Step Effect Types

Set via `effectTypeCombo`. Available types:

| Type | Description | Extra Fields |
|------|-------------|--------------|
| `none` | No effect | — |
| `teleport` | Teleports player to target coords | target map/x/y |
| `damage` | Deals damage on every step | damage amount |
| `island_portal` | Portal requiring a key item | `requiredKeyId` (optional; falls back to any KEY item) |
| `encounter` | Forces random encounter | encounter rate multiplier |
| `trap_once` | One-time damage trap; sets a flag so it only fires once | damage amount |
| `set_tile` | Mutates the tile at this position to another tile char | new tile char |
| `locked_door` | Blocks movement until player has the required key item; consumes key and mutates tile | `requiredKeyId`, `unlockedTileId`, `lockedMessage` |

`locked_door` tiles (e.g., `|`, `[`, `{`) are intercepted **before** the walkability check in `Retroquest.java` via `NavigationController.tryUnlockTile(x, y)`.

### Char ID Management

- New tiles: next available ASCII char auto-assigned.
- Uniqueness validated on save; duplicate IDs rejected with an error dialog.
- `TileRegistry.removeTile(char)` called on delete.

### Auto-Migration

`TileRegistry.migrateNewTiles()` runs on startup and inserts any missing built-in tiles without requiring a tiles.json delete/rebuild.

---

## 8. Dialogue Tree Editor (`DialogueTreeEditor.java`)

Launched from the EDITORS menu when an NPC is selected. Edits dialogue trees embedded inline in the NPC's `.rfmap` entry.

### Data Model

| Class | Role |
|-------|------|
| `DialogueTree` | Root; holds a `Map<String, DialogueNode>` keyed by node ID |
| `DialogueNode` | One screen of text; holds a list of `DialogueChoice` |
| `DialogueChoice` | A player-selectable option; holds a list of `DialogueCondition` and `DialogueAction`, plus `nextNodeId` |
| `DialogueCondition` | Gate on a choice (e.g., `HAS_ITEM`, `QUEST_COMPLETE`, `FLAG_EQUALS`) |
| `DialogueAction` | Side-effect on selection (e.g., `GIVE_ITEM`, `OPEN_SHOP`, `HEAL_PLAYER`) |

### Condition Types

`QUEST_COMPLETE`, `QUEST_ACTIVE`, `HAS_ITEM`, `HAS_GOLD`, `PLAYER_LEVEL`, `PLAYER_STAT`, `FLAG_EQUALS`, `FLAG_SET`, `FLAG_NOT_SET`

### Action Types

`GIVE_QUEST`, `GIVE_ITEM`, `GIVE_GOLD`, `TAKE_ITEM`, `SET_FLAG`, `CLEAR_FLAG`, `OPEN_SHOP`, `HEAL_PLAYER`, `TEACH_SPELL`, `LOG_MESSAGE`, `CLOSE_DIALOGUE`

### Runtime Behavior

`NpcController.talk()` checks for a `dialogueTree` on the NPC first; falls back to legacy `defaultDialog` string if absent. Player flags (`Player.flags`, a `Map<String, String>`) persist across saves and are used by `FLAG_EQUALS`/`FLAG_SET`/`FLAG_NOT_SET` conditions.

---

## 9. Image Editor (`ImageEditor.java`)

A CRT-themed pixel art editor for creating and editing sprite assets. Launched from the EDITORS menu or standalone via `ImageEditor.main()`.

### Layout

```
ImageEditor (JDialog)
├── Left: Sprite Library (scrollable, categorized by subfolder)
├── Center
│   ├── Title bar + SIZE combo
│   ├── Square PixelCanvas (auto-scales to fill)
│   ├── Canvas Info panel (size, format, file)
│   └── Preview strip (1× and 2× magnification)
├── Right: Tools panel
│   ├── Tool buttons (DRAW, ERASE, FILL, PICK, RECT, CIRCLE, LINE)
│   ├── Active color swatch + 256-color palette
│   ├── Transform (Flip H/V, Rotate L/R)
│   └── Undo/Redo
└── Bottom: Transport bar (NEW, LOAD, SAVE, SAVE AS, CLEAR)
```

### Tools

| Tool | Description | Controls |
|------|-------------|----------|
| DRAW | Paint pixels | Left-click/drag |
| ERASE | Clear pixels to transparent | Left-click/drag |
| FILL | BFS flood-fill contiguous pixels | Left-click |
| EYEDROPPER | Sample color from canvas, auto-switches to DRAW | Left-click |
| RECT | Draw rectangle outline | Press-drag-release; hold **Shift** for filled |
| CIRCLE | Draw ellipse outline (Bresenham) | Press-drag-release; hold **Shift** for filled |
| LINE | Draw line (Bresenham) | Press-drag-release |

Shape tools (RECT, CIRCLE, LINE) show a semi-transparent rubber-band preview during drag.

### Keyboard Shortcuts

| Key | Action |
|-----|--------|
| D | DRAW tool |
| E | ERASE tool |
| F | FILL tool |
| I | EYEDROPPER tool |
| R | RECT tool |
| C | CIRCLE tool |
| L | LINE tool |
| Ctrl+Z | Undo |
| Ctrl+Y | Redo |
| Ctrl+S | Save |

### Canvas Sizes

Supported grid sizes: 8×8, 16×16, 32×32, 64×64. Canvas auto-scales to fill its square container.

### File I/O

- Sprites saved as PNG under `src/main/resources/tiles/{category}/`.
- On save, automatically mirrored to `target/classes/tiles/` for immediate classpath availability.
- `ImageAssetRegistry.reloadAll()` called after save.
- Sprite library panel shows all PNGs grouped by subfolder, with thumbnails.

### Undo/Redo

Stack-based (max 40 states). Full canvas snapshot per undo state. Shape tool commits push a single undo entry at drag start.

---

## 10. CRT Theme

All editor dialogs share a retro terminal color scheme:

| Constant | RGB | Usage |
|----------|-----|-------|
| BG | (10, 12, 16) | Main background |
| PANEL_BG | (16, 20, 28) | Sidebar/panel background |
| PHOSPHOR | (0, 255, 120) | Default tool accent |
| PHOSPHOR2 | (0, 200, 255) | NPC/teleporter accent |
| DANGER | (220, 60, 60) | Spawn difficulty accent |
| TEXT_BRIGHT | (210, 230, 255) | Primary text |
| TEXT_DIM | (120, 140, 160) | Secondary text |

**Known debt:** These constants are duplicated across RetroForge, all 8+ editor dialogs, and all game overlays rather than shared from a common class. Game overlays share via `OverlayTheme.java`; editor dialogs do not yet use it.

---

## 11. Editor Architecture

```
RetroForge (JFrame)
├── Menu Bar (FILE, EDIT, TOOLS, EDITORS, GAME)
├── Toolbar (tool buttons, brush/spawn controls, undo/redo, playtest)
├── Left Sidebar
│   ├── LivePreviewPanel (minimap)
│   └── TilePalette (filtered tile buttons)
└── Center: MapCanvas (34×21 viewport)
    ├── Mouse handlers (paint, fill, place NPCs/towns/teleporters)
    ├── Keyboard handlers (pan, undo/redo)
    └── Undo/Redo stacks (deep-copy MapData, max 40)
```

### Opening Town from Overworld

`openTownFromEntrance(townName)`:
1. Prompts to save current map.
2. Loads `data/towns/{townName}.rfmap`.
3. Updates canvas and preview.
4. Hides the TOWN_PLACER and DUNGEON_PLACER tools (they are overworld-only).

---

## 12. Known Gaps

1. **No dirty tracking** — `RetroForge` has **no unsaved-changes flag at all**. `setDefaultCloseOperation(EXIT_ON_CLOSE)`, and the only window listener saves prefs, not the map. Closing the window, FILE ▸ Exit (`System.exit(0)`), Load Map and New Map all discard unsaved map edits silently. `ImageEditor` is the only editor with an `unsavedChanges` flag, and it only prompts on New Image.
2. ~~**No multi-select**~~ / ~~**No copy/paste region**~~ — **Fixed.** The `SELECT` tool does rectangular select with Ctrl+C / Ctrl+V (undoable) on both the map canvas and the image editor.
3. **No layer system** — single tile layer; no support for overlapping decorations.
4. **Undo is full-map copy** — each undo state duplicates the entire map, which is memory-heavy for large maps.
5. ~~**No zoom**~~ — **Fixed.** VIEW ▸ Zoom In / Zoom Out / Reset Zoom (Ctrl+= / Ctrl+- / Ctrl+0).
6. **No map validation on save** — doesn't check for orphan town entrances, `dungeonName` tile states pointing at a missing `data/dungeons/<name>_1.rfmap`, or teleporters pointing to non-existent maps.
7. **Shared color constants** — CRT theme colors are duplicated across the editor files; game overlays use `OverlayTheme.java` but editors use `EditorTheme.java` and local constants.
8. **No map preview thumbnails** — file chooser shows filenames only, no visual preview of map contents.
9. **Sound Editor unreachable** — `SoundEditor` is not on any menu and can only be run via its own `main()`. (`TeleporterPlacementDialog` and `NewTileDialog`, also dead, have since been deleted.)
10. **Riddle doors are not authorable** — the `'q'` tile can be painted, but nothing in the editor configures which riddle pool or riddle it uses; that is decided in `DungeonController` from the dungeon name and a position hash.


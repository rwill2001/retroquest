# RetroForge Operators Guide

**Version:** 0.9.0 — March 2026
**Audience:** Retro CRPG enthusiasts and world builders

---

## Table of Contents

1. [Overview](#1-overview)
2. [Main Window Layout](#2-main-window-layout)
3. [Tools Reference](#3-tools-reference)
4. [Map Management](#4-map-management)
5. [Tile Palette](#5-tile-palette)
6. [Tile Editor](#6-tile-editor)
7. [NPC Placement and Editor](#7-npc-placement-and-editor)
8. [Dialogue Tree Editor](#8-dialogue-tree-editor)
9. [Item Editor](#9-item-editor)
10. [Quest Editor](#10-quest-editor)
11. [Monster Editor](#11-monster-editor)
12. [Image Editor](#12-image-editor)
13. [Sound Editor](#13-sound-editor)
14. [Tile Instance States](#14-tile-instance-states)
15. [Town Placement](#15-town-placement)
16. [Teleporter Placement](#16-teleporter-placement)
17. [Spawn Difficulty](#17-spawn-difficulty)
18. [Playtesting](#18-playtesting)
19. [Keyboard Shortcuts Reference](#19-keyboard-shortcuts-reference)
20. [File Structure Reference](#20-file-structure-reference)

---

## Getting Started

New to RetroForge? Start here.

RetroForge is the world editor for RetroQuest. With it you can paint maps tile by tile, place towns, create NPCs with branching conversations, design quests, craft items, and draw your own sprites — no programming required.

**Your first five minutes:**

1. **Open a map** — Use *File → Load Map* and pick any `.rfmap` file from the `data/` folder. Try `data/towns/stonehaven.rfmap` to see a complete working example.
2. **Paint some tiles** — Click a tile in the left-side palette, then click and drag on the map canvas to paint.
3. **Place an NPC** — Switch to the **NPC PLACER** tool in the toolbar, pick a sprite, and click an empty tile on the map.
4. **Save your work** — *File → Save Map* writes your changes to disk.
5. **Try it in the game** — Click the **▶ PLAYTEST** button to launch RetroQuest and walk around your creation.

The rest of this guide explains every tool and editor in detail. Use the Table of Contents to jump to whatever you need.

---

## 1. Overview

RetroForge is the map and content editor for RetroQuest. It edits `.rfmap` map files and provides access to all game data registries: tiles, NPCs, dialogue trees, items, quests, monsters, sprites, and sounds.

**Launching RetroForge:** Launch the `RetroForge` class from your IDE (not the main `Retroquest` class). It opens in a large window and automatically picks up where you left off — loading the last map you were editing, or the main overworld if this is your first time.

**Session persistence:** On close, RetroForge saves the last map path, camera position, and zoom level to `data/.forge_prefs`. These are restored the next time you open.

---

## 2. Main Window Layout

```
┌─────────────────────────────────────────────────────────────────┐
│  FILE  EDIT  VIEW  TOOLS  EDITORS  GAME         ◈ PENCIL TOOL  │  ← Menu bar + status
│  [PENCIL] [FILL] [NPC] [TOWN] [TELEPORT] [SPAWN]  [↩] [↪] [▶] │  ← Toolbar
├──────────────┬──────────────────────────────────────────────────┤
│ ◈ RETROFORGE │                                                  │
│              │                                                  │
│ MINIMAP      │           MAP CANVAS                             │
│ PREVIEW      │           (34×21 tile viewport)                  │
│              │                                                  │
├──────────────┤                                                  │
│ TILE PALETTE │                                                  │
│ (scrollable) │                                                  │
│              │                                                  │
└──────────────┴──────────────────────────────────────────────────┘
```

### Left Sidebar (290px wide)

| Section | Description |
|---------|-------------|
| **Header** | "◈ RETROFORGE MAP EDITOR" |
| **Minimap Preview** | Live 270×180 thumbnail of the entire map; yellow box shows current viewport |
| **Tile Palette** | Scrollable grid of tile buttons, filtered by map type; click to select brush |

### Toolbar

- **Tool buttons** — PENCIL, FILL, NPC PLACER, TOWN PLACER, TELEPORTER PLACER, SPAWN DIFFICULTY
- **NPC combo** — visible in NPC PLACER mode; select which sprite template to place
- **SPAWN spinner** — visible in SPAWN DIFFICULTY mode; sets the value to paint (0–99)
- **ENTRY X/Y spinners** — visible on TOWN maps only; sets the player spawn point when entering this town
- **↩ UNDO / ↪ REDO** — 40-level undo stack
- **⟳ REFRESH** — reloads all sprites from disk; useful after editing in Image Editor
- **▶ PLAYTEST** — launches RetroQuest without closing RetroForge
- **Status** — right-aligned; shows current tool name

### Canvas Overlay (bottom-right corner)

While hovering over the canvas, a small overlay shows: `Tile: X, Y   Brush: N×N   Zoom: N%`

---

## 3. Tools Reference

### PENCIL

Paints tiles onto the map with a configurable square brush.

| Action | Result |
|--------|--------|
| `Left-click / drag` | Paint selected tile over the brush area |
| `Right-click` | Opens **Tile Context Menu** at that coordinate |
| `Mouse wheel` | Adjusts brush size (1×1 to 9×9) |
| `Ctrl + Mouse wheel` | Zooms canvas in/out |

**Brush size** is shown in the canvas overlay. Painting over an `E` (town entrance) or `O` (teleporter) tile with a different tile automatically removes the linked entrance/teleporter record.

### FILL

Flood-fills a region of identical tiles with the selected tile, spreading to fill all connected matching tiles at once.

| Action | Result |
|--------|--------|
| `Left-click` | Flood-fill from that coordinate |

> **Tip:** Switch to FILL after rough pencil work to quickly cover large uniform areas.

### NPC PLACER

Places, edits, and deletes NPCs on the current map.

| Action | Result |
|--------|--------|
| `Left-click empty tile` | Places a new NPC using the selected sprite from the NPC combo |
| `Left-click near existing NPC` | Does nothing (use double-click or the NPC Editor) |
| `Double-click near NPC` | Opens **NPC Editor** for that NPC (also works in PENCIL mode) |
| `Right-click near NPC` | Prompts to delete that NPC |

New NPCs default to type `TOWNSFOLK` and dialog `"Hello traveler..."`. Edit them to fill in real details.

### TOWN PLACER *(Overworld maps only)*

Places town entrance tiles (`E`) linked to a town `.rfmap` file.

| Action | Result |
|--------|--------|
| `Left-click` | Opens **Town Placement Dialog** to link a town file |
| `Left-click on existing E tile` | Does nothing (already linked) |
| `Double-click on existing E tile` | Saves current map (optional) and opens that town for editing |

Hidden when editing town maps.

### TELEPORTER PLACER *(Overworld maps only)*

Places overworld teleporter tiles (`O`) that move the player to another overworld map position.

| Action | Result |
|--------|--------|
| `Left-click empty tile` | Opens **Teleporter Placement Dialog** to configure destination |
| `Left-click on existing O tile` | Opens the dialog to edit that teleporter's destination |

Hidden when editing town maps.

### SPAWN DIFFICULTY

Paints a per-tile encounter difficulty value (0–99) used by `EncounterController` to scale which monsters can appear.

| Action | Result |
|--------|--------|
| `Left-click / drag` | Paints the current spinner value across the brush area |
| `Right-click` | Samples the difficulty value from that tile into the spinner |

Values are visualized on the canvas as red overlays — brighter = higher danger. Numeric labels appear at zoom ≥ 50%.

**Difficulty color scale:**

| Value | Color |
|-------|-------|
| 1 | Bright green |
| 2–5 | Yellow-green |
| 6–10 | Yellow |
| 11–20 | Orange-yellow |
| 21–35 | Orange |
| 36–55 | Dark orange |
| 56–75 | Red |
| 76–99 | Magenta |

---

## 4. Map Management

### Creating a New Map

**FILE → New Map...** or use the menu shortcut.

| Field | Description | Default |
|-------|-------------|---------|
| Name | Internal map identifier | `MyTown` |
| Type | `Overworld` or `Town` | `Overworld` |
| Width | Map columns | 140 (Overworld), 40 (Town) |
| Height | Map rows | 90 (Overworld), 30 (Town) |

**Town maps** auto-generate a starting layout:
- Floor fill (`F`) with border walls (`W`)
- Exit door (`d`) at bottom center
- Shop building (left side) with a counter row and SHOPKEEPER NPC
- Inn building (right side) with a counter row and INNKEEPER NPC
- Requires at least 26×20 tiles for the buildings to appear

### Loading a Map

**FILE → Load Map...** — Opens a file chooser starting in `data/`. Only `.rfmap` files are shown.

After loading:
- The canvas resets to origin (zoom and camera reset)
- TOWN PLACER and TELEPORTER PLACER hide if the map is a town
- ENTRY X/Y spinners appear if the map is a town

### Saving a Map

**FILE → Save Map** — saves to the same path that was loaded. If the map is new (never saved), prompts for a name.

Files are saved automatically to:
- **Overworld:** `data/overworlds/<name>.rfmap`
- **Town:** `data/towns/<name>.rfmap`

The map name is always derived from the filename, not an internal field. Rename by saving as a new file.

### Resizing a Map

**FILE → Resize Map** — Enter a new width and height. Resizing expands or crops the map. Content in the surviving region is preserved; new tiles default to `.` (empty).

> **Warning:** Resizing cannot be undone. Save before resizing.

---

## 5. Tile Palette

The left sidebar displays a filtered grid of tile buttons for the current map type:

| Map Type | Tile Categories Shown |
|----------|-----------------------|
| Overworld | Overworld, Special, Custom |
| Town | Town, Special, Custom |
| Dungeon | Dungeon, Special, Custom |

Each button shows the tile's name (truncated) with its editor color as the background. Hovering shows a tooltip with the full name and character ID.

**Clicking a tile** sets it as the active brush for PENCIL and FILL tools.

The palette refreshes automatically when tiles are added, edited, or deleted via the **Tile Editor**.

---

## 6. Tile Editor

**EDITORS → Tile Editor** — Opens a modal dialog for editing the global tile registry (`data/tiles.json`).

### Layout

```
┌─────────────────────────────────────────────────────────────────┐
│  ◈ TILE EDITOR                               [Ctrl+S to save]  │
├──────────────────┬──────────────────────────────────────────────┤
│  JTree           │  TILE PROPERTIES                             │
│  ▸ Overworld     │  Name:           [________________]          │
│  ▸ Town          │  Char ID:        [_]                         │
│    ▸ Letters A–Z │  Category:       [Overworld ▼]               │
│  ▸ Dungeon       │  Walkable:       [✓]                         │
│  ▸ Special       │  Sprite:         [________________ ▼]        │
│  ▸ Custom        │  Editor Color:   [■ #00FF78]                 │
│  ▸ System        │                                              │
│                  │  GAMEPLAY                                    │
│                  │  Is Liquid:      [ ]                         │
│                  │  Blocks Vision:  [ ]                         │
│                  │  Safe Zone:      [ ]                         │
│                  │  Light Radius:   [0 ▲▼]                      │
│                  │                                              │
│                  │  ADVANCED                                    │
│                  │  Step Sound:     [step_default ▼]            │
│                  │  Encounter Rate: [1.0 ▲▼]                    │
│                  │  Description:    [____________________]      │
│                  │                                              │
│                  │  STEP EFFECTS                                │
│                  │  Effect Type:    [none ▼]                    │
│                  │  … (type-specific fields) …                  │
├──────────────────┴──────────────────────────────────────────────┤
│  [★ NEW]  [✕ DELETE]  [💾 SAVE]   STATUS: Ready                 │
└─────────────────────────────────────────────────────────────────┘
```

### Tile Tree

Tiles are organized by category with collapsed sub-nodes. The `Letters (A–Z)` node under Town is collapsed by default to reduce clutter. Letter tiles (A–Z) use special internal character codes managed automatically by the editor — you do not need to know the exact values.

### Fields Reference

| Field | Description |
|-------|-------------|
| **Name** | Display name shown in palette and editor UI |
| **Char ID** | Single character map identifier; auto-assigned on new tiles; must be unique |
| **Category** | Overworld / Town / Dungeon / Special / Custom / System |
| **Walkable** | Whether the player can step onto this tile |
| **Sprite** | Dropdown of available sprites from `src/main/resources/tiles/` |
| **Editor Color** | RGB color shown in the canvas and palette; click to open color chooser |
| **Is Liquid** | Marks tile as water/lava for swimming/damage logic |
| **Blocks Vision** | Blocks dungeon fog-of-war line-of-sight |
| **Safe Zone** | Prevents random encounters on this tile |
| **Light Radius** | Tile emits light of this radius in dungeon (0 = none) |
| **Step Sound** | Sound key played when the player steps on this tile |
| **Encounter Rate** | Multiplier applied to encounter chance (0.0 = none, 2.0 = double) |
| **Description** | Flavor text; not shown in-game currently |

### Step Effect Types

| Type | What It Does | Extra Fields |
|------|-------------|--------------|
| `none` | No step effect | — |
| `teleport` | Moves player to another map position | Target map, X, Y |
| `damage` | Deals damage every step | Amount |
| `island_portal` | Portal requiring a key item | Optional `requiredKeyId` (falls back to any KEY item) |
| `encounter` | Forces a random encounter | — |
| `trap_once` | One-time damage trap | Amount, message |
| `set_tile` | Replaces this tile with another char | Target tile char |
| `locked_door` | Blocks movement; consumes key and converts tile | `requiredKeyId`, `unlockedTileId`, locked message |
| `toggle_door` | Toggles between open/closed tile chars | Open char, close char |
| `pressure_plate` | Activates/deactivates another tile when stepped | Target X/Y, activate char, deactivate char, once? |

> **Note:** `locked_door` and per-instance overrides are configured per-coordinate using the **Tile Instance State** dialog (see [Section 14](#14-tile-instance-states)).

### Char ID Management

- New tiles are auto-assigned the next available printable ASCII character
- Attempting to save with a duplicate ID shows an error and cancels the save
- Deleting a tile removes it from the registry; map files that referenced it will show a fallback tile

### Auto-Migration

`TileRegistry.migrateNewTiles()` runs on startup and inserts any missing built-in tiles. You do not need to delete `tiles.json` to pick up new system tiles.

---

## 7. NPC Placement and Editor

### Placing NPCs on the Canvas

1. Select **NPC PLACER** tool
2. Choose a sprite from the **NPC combo** in the toolbar
3. Left-click an empty tile to place an NPC with default settings
4. The NPC immediately appears as a yellow circle with `N` label

### Editing an NPC

- **Double-click** near any NPC (in any tool mode) to open the editor
- **EDITORS → NPC Editor** to open the editor for all map NPCs at once

### NPC Editor Fields

```
Left panel: scrollable list of all NPCs on the map
Right panel: form for the selected NPC
```

| Field | Description |
|-------|-------------|
| **NAME** | Display name shown in dialogue |
| **SPRITE** | Dropdown of all sprites in `src/main/resources/tiles/npcs/` |
| **TYPE** | `TOWNSFOLK`, `SHOPKEEPER`, `INNKEEPER`, `QUESTGIVER`, `GUARD`, `TRAINER`, `BLACKSMITH`, `HEALER`, `CASINO` |
| **QUEST** | Optional quest this NPC gives/tracks (from quest registry) |
| **OPTIONS** | "Is Shopkeeper" checkbox; enables **Edit Shop Items...** button |

Below the form, two text areas:

| Area | Used When |
|------|-----------|
| **NORMAL DIALOG** | Shown when the player talks to this NPC (no active quest, or NPC has no dialogue tree) |
| **POST-QUEST DIALOG** | Shown after the player completes the NPC's assigned quest |

> **Dialogue trees** take priority over these text areas at runtime. If the NPC has a tree, the NORMAL DIALOG text is not shown.

### Shop Item Editor

Click **Edit Shop Items...** (requires "Is Shopkeeper" checked).

- Left list: all items marked "In Shop" in the item registry
- Right list: items currently in this NPC's shop
- Arrow buttons move items between lists
- Click **OK** to apply; **Cancel** to discard


### Saving NPC Changes

Click **💾 SAVE NPC** or press `Ctrl+S`. Changes are written to the in-memory map data. The map file is only updated when you save the map from the main window.

---

## 8. Dialogue Tree Editor

**EDITORS → Dialogue Tree Editor** — Select an NPC from the list. If no NPCs are on the map, a warning appears.

Also accessible from inside the NPC Editor via **◈ Edit Dialogue Tree...**.

### Layout

```
┌─────────────────┬───────────────────────────────────────────────┐
│  JTree          │  CardLayout panel (switches based on selection)│
│  ▸ TREE         │                                                │
│  ▸ greeting     │  NODE EDITOR:                                  │
│    ▸ Choice 1   │  Node ID:  [greeting]                          │
│      └ Cond 1   │  Text:     [Hello, traveler!              ]    │
│    ▸ Choice 2   │            [                               ]   │
│    ▸ Action 1   │  CHOICES: [Ask for work] [Farewell] [+Add]     │
│  ▸ quest_node   │  ACTIONS: [GIVE_QUEST]              [+Add]     │
└─────────────────┴───────────────────────────────────────────────┘
│  [◀ BACK]  [+ NEW NODE]  [Apply to NPC]  [💾 Apply & Close]     │
└─────────────────────────────────────────────────────────────────┘
```

### Tree Panel (Left)

The navigator shows the dialogue tree structure as a collapsible tree:
- **TREE** root node — click to edit tree-level properties (start node)
- **Node entries** — click to open the Node Editor
- **Choices** (under nodes) — click to open the Choice Editor
- **Conditions** (under choices) — click to open the Condition Editor
- **Actions** (under nodes or choices) — click to open the Action Editor

### Tree Properties Card

| Field | Description |
|-------|-------------|
| **Start Node ID** | Dropdown of all node IDs; the entry point when the player talks to this NPC |
| **Validation** | Displays any structural problems (missing next-node references, etc.) |

### Node Editor Card

| Field | Description |
|-------|-------------|
| **Node ID** | Unique string identifier for this node |
| **Text** | The dialogue text displayed in the game's DialogueOverlay (supports line breaks) |
| **Choices** | List of player response options; click to edit, or **+ Add** to create |
| **Actions** | Effects triggered when this node is entered; click to edit, or **+ Add** |

### Choice Editor Card

| Field | Description |
|-------|-------------|
| **Label** | The text shown on the choice button |
| **Next Node** | Dropdown of all nodes; the node reached when this choice is selected (`(close)` ends dialogue) |
| **Conditions** | Optional list of gates; all must pass for the choice to appear |

### Condition Editor Card

Conditions gate whether a choice is visible to the player.

| Type | Target | Value | Amount |
|------|--------|-------|--------|
| `QUEST_COMPLETE` | Quest ID | — | — |
| `QUEST_ACTIVE` | Quest ID | — | — |
| `HAS_ITEM` | Item ID | — | — |
| `HAS_GOLD` | — | — | Minimum gold |
| `PLAYER_LEVEL` | — | — | Minimum level |
| `PLAYER_STAT` | Stat name | — | Minimum value |
| `FLAG_EQUALS` | Flag key | Expected value | — |
| `FLAG_SET` | Flag key | — | — |
| `FLAG_NOT_SET` | Flag key | — | — |

**Negate checkbox:** inverts the condition (e.g., `HAS_ITEM` + negate = "does NOT have item").

### Action Editor Card

Actions execute side effects when the player selects a choice or enters a node.

| Type | Target | Value | Amount |
|------|--------|-------|--------|
| `GIVE_QUEST` | Quest ID | — | — |
| `COMPLETE_QUEST` | Quest ID | — | — (auto-progresses EXPLORE quests via dialogue) |
| `GIVE_ITEM` | Item ID | — | Count |
| `GIVE_GOLD` | — | — | Amount |
| `TAKE_ITEM` | Item ID | — | Count |
| `SET_FLAG` | Flag key | Value to set | — |
| `CLEAR_FLAG` | Flag key | — | — |
| `OPEN_SHOP` | — | — | — |
| `OPEN_CASINO` | — | — | — |
| `HEAL_PLAYER` | — | — | Amount (0 = full heal) |
| `TEACH_SPELL` | Spell name | — | — |
| `LOG_MESSAGE` | Message text | Log type (`INFO`, `GOOD`, `DANGER`, `DIM`, `LOOT`) | — |
| `SERVE_DRINK` | — | — | Gold cost (charges player, applies drunk effect) |
| `CLOSE_DIALOGUE` | — | — | — |

### Saving the Tree

Click **Apply & Close** to write the edited tree back to the NPC. The map still needs to be saved to persist changes to disk.

---

## 9. Item Editor

**EDITORS → Item Editor** — Edits the global item database (`data/items.json`).

### Layout

Left panel: scrollable list of all items, colored by rarity:
- Common: white
- Uncommon: green
- Rare: blue
- Epic: purple
- Legendary: gold

Right panel: property form for the selected item.

### Item Fields

| Field | Description |
|-------|-------------|
| **ID** | Unique string identifier (e.g., `iron_sword`); used in quests, dialogue actions, locked doors |
| **NAME** | Display name shown in inventory |
| **TYPE** | `WEAPON`, `ARMOR`, `HELM`, `SHIELD`, `AMULET`, `RING`, `POTION`, `SCROLL`, `WAND`, `KEY`, `MISC` (legacy: `RING_PROTECTION`, `RING_REGEN`) |
| **SLOT** | Equipment slot: `WEAPON`, `ARMOR`, `HELM`, `SHIELD`, `AMULET`, `RING`, `CONSUMABLE`, `KEY`, `MISC` |
| **VALUE** | Interpreted by type: damage bonus (WEAPON), AC bonus (ARMOR/HELM/SHIELD), HP healed (POTION) |
| **PRICE** | Base shop buy price in gold |
| **TIER** | Power tier 1–20; affects loot generation weighting |
| **RARITY** | `COMMON`, `UNCOMMON`, `RARE`, `EPIC`, `LEGENDARY` |
| **MAX STACK** | Maximum stack size in inventory (1 = non-stackable) |
| **LOOTABLE** | Whether this item can appear in dungeon chest loot |
| **IN SHOP** | Whether this item appears in the shop inventory selector |
| **SPRITE** | Sprite resource key (relative to `src/main/resources/tiles/`) |
| **SPELL** | *(SCROLL/WAND only)* The spell this item casts when used |
| **CHARGES** | *(WAND only)* Number of uses before the wand is depleted |
| **EFFECTS** | List of stat/effect modifiers applied when equipped or used |
| **DESC** | Flavor text shown in item inspect screen |

### Adding Effects

The EFFECTS sub-panel lists active effects. Use **+ Add Effect** to add a modifier (e.g., `+5 STR while equipped`). Effect entries can be edited inline and removed with the delete button.

### Saving

**💾 SAVE ITEM** or `Ctrl+S`. Item changes are written immediately to `ItemRegistry`; the registry saves to `data/items.json` when the program exits or when explicitly saved.

---

## 10. Quest Editor

**EDITORS → Quest Editor** — Edits the global quest database (`data/quests.json`).

### Layout

Left panel: list of all quests. Right panel: property form.

### Quest Fields

| Field | Description |
|-------|-------------|
| **ID** | Unique string identifier (e.g., `hunt_wolves`); referenced by NPCs and dialogue actions |
| **TITLE** | Short quest name shown in the Quest Log |
| **DESC** | Full quest description (shown directly below Title in the editor for easy editing) |
| **TYPE** | Quest type (see below) |
| **TARGET** | The thing to kill/collect/deliver (monster name or item name/ID) |
| **GIVER NPC** | NPC the player returns to for turn-in. Dropdown populated from all town/overworld NPCs. Used by auto-turn-in logic for KILL/COLLECT quests. |
| **AMOUNT** | Required quantity to complete the quest |
| **MIN / MAX** | Random range for amount when the quest is generated procedurally |
| **OPTIONS** | "Repeatable Quest" checkbox — quest can be accepted again after completion |
| **GOLD** | Gold reward on completion |
| **XP** | Experience point reward on completion |
| **SPELL** | Optional spell taught on completion |
| **ITEM** | Optional item given on completion |
| **DELIVER ITEM** | *(DELIVER type only)* The item the player must bring to the NPC |
| **PREREQ** | Optional quest that must be completed before this one is available |

### Quest Types

| Type | Objective |
|------|-----------|
| `KILL` | Kill N of a named monster type (use `any` as target for any monster) |
| `COLLECT` | Obtain N of a specific item (matches by item name) |
| `TALK` | Talk to a named NPC (auto-completes when the player speaks to the target NPC) |
| `DELIVER` | Bring a specific item to the quest NPC |
| `EXPLORE` | Reach a location (trigger varies by implementation; can be force-completed via `COMPLETE_QUEST` dialogue action) |

> For `COLLECT` types, TARGET is the **item display name** (e.g., `Pyralis Fire Potion`), matched case-insensitively at runtime. For `DELIVER` types, TARGET is the **NPC name** who receives the delivery, and DELIVER ITEM selects the item ID to deliver.

### Saving

**💾 SAVE QUEST** or `Ctrl+S`. Deleting a quest removes it from the registry; NPCs still referencing the deleted quest ID will fall back to no quest at runtime.

---

## 11. Monster Editor

**EDITORS → Monster Editor** — Edits the global monster database (`data/monsters.json`).

### Layout

Left panel: searchable list of all monsters. Right panel: tabbed form (**STATS** | **SPELLS**).

**Search:** Type in the search field to filter monsters by name in real time.

**List actions:** **★ NEW** creates a blank monster, **⊠ CLONE** duplicates the selected monster, **✖ DEL** deletes it.

### Stats Tab

| Field | Description |
|-------|-------------|
| **ID** | Unique string identifier (e.g., `wolf_3`) |
| **NAME** | Display name shown in combat |
| **LEVEL** | Monster level 1–50; affects `MonsterRegistry` lookup |
| **HP** | Maximum hit points |
| **DAMAGE** | Base damage per attack |
| **GOLD** | Gold dropped on defeat |
| **AC** | Armor class (higher = harder to hit) |
| **XP VALUE** | Experience points awarded on defeat |
| **SPRITE** | Dropdown of sprites in `src/main/resources/tiles/monsters/` |

A sprite preview panel shows the selected sprite at the right.

### Spells Tab

| Field | Description |
|-------|-------------|
| **CAST CHANCE** | Probability (0–100%) the monster casts instead of melee each turn |
| **SPELL POWER** | Damage/heal multiplier for spells |
| **SPELL RESIST** | Resistance to player spells (0–40) |
| **Spell checkboxes** | Select which spells this monster can cast from the full spell list |

Available spells include: Magic Missile, Sleep, Charm Monster, Fireball, Lightning Bolt, Ice Storm, Chain Lightning, Death Spell, Heal, and many more.

### Saving

**💾 SAVE MONSTER** or `Ctrl+S`.

> **MonsterFactory** generates procedural monsters for levels that have no registry entry. Registered monsters at the exact level are preferred (75%) over factory-generated ones.

---

## 12. Image Editor

**EDITORS → Image Editor** — Opens the pixel art editor for creating and editing sprites.

Can also be opened directly from the **NPC Editor** or **Monster Editor** via the **✏ EDIT SPRITE** button.

### Layout

```
┌─────────────────────────────────────────────────────────────────┐
│  LEFT: Sprite Library     │  CENTER: Pixel Canvas               │
│  (categorized by folder)  │  BOTTOM: 1× and 2× previews        │
│  [⟳ REFRESH]             │                                     │
├───────────────────────────┤  RIGHT: Tools Panel                 │
│  Categories:              │  [DRAW] [ERASE] [FILL] [PICK]      │
│  monsters/ npcs/          │  [RECT] [CIRCLE] [LINE]             │
│  tiles/overworld/ etc.    │  ┌──────────────────────────┐      │
│                           │  │  Color swatch             │      │
│                           │  │  256-color palette grid   │      │
│                           │  └──────────────────────────┘      │
│                           │  [Flip H] [Flip V] [Rot L] [Rot R] │
│                           │  [↩ Undo]  [↪ Redo]                │
└─────────────────────────────────────────────────────────────────┘
│  [NEW]  [LOAD]  [SAVE]  [SAVE AS]  [CLEAR]    32×32 | DRAW     │
└─────────────────────────────────────────────────────────────────┘
```

### Canvas Size

Use the **SIZE** dropdown to set canvas dimensions:

| Option | Size | Use |
|--------|------|-----|
| 8×8 | 8×8 px | UI icons |
| 16×16 | 16×16 px | Small icons |
| **32×32** | 32×32 px | Standard tile/NPC/monster sprites |
| 64×64 | 64×64 px | Large sprites |

The canvas scales to fill its panel at the current zoom.

### Drawing Tools

| Tool | Key | Description |
|------|-----|-------------|
| **DRAW** | `D` | Paint pixels with the current color |
| **ERASE** | `E` | Clear pixels to transparent |
| **FILL** | `F` | Flood-fill all connected pixels of the same color |
| **EYEDROPPER** | `I` | Sample a pixel color; auto-switches to DRAW |
| **RECT** | `R` | Draw rectangle outline (hold `Shift` for filled) |
| **CIRCLE** | `C` | Draw ellipse (hold `Shift` for filled) |
| **LINE** | `L` | Draw a straight line |

Shape tools show a semi-transparent rubber-band preview while dragging.

### Sprite Library

The left panel lists all PNGs under `src/main/resources/tiles/`, grouped by subfolder. Clicking a sprite opens it for editing. Folders are listed alphabetically.

### Saving Sprites

**SAVE** writes to the current file. **SAVE AS** lets you choose a different path. On save:
1. The PNG is written to `src/main/resources/tiles/{category}/{name}.png`
2. The game's internal sprite cache is refreshed so the new image is available immediately
3. The editor updates to reflect the change right away

After editing a sprite, click **⟳ REFRESH** in the RetroForge toolbar to update the canvas rendering.

---

## 13. Sound Editor

**EDITORS → Sound Editor** — Opens the procedural sound designer.

All sounds in RetroQuest are synthesized at runtime — there are no audio files.

### Layout

```
┌──────────────────┬──────────────────────────┬────────────────────┐
│  SOUND LIBRARY   │  CRT OSCILLOSCOPE        │  DESIGNER TABS     │
│  (left panel)    │  (animated waveform)      │  TONE | CHORD | SEQ│
│                  │                          │                    │
└──────────────────┴──────────────────────────┴────────────────────┘
│  [▶ PLAY]  [🎲 RANDOMISE]  [⬆ EXPORT]  [💾 SAVE]                 │
└─────────────────────────────────────────────────────────────────┘
```

### Sound Library

Left panel lists all registered sounds, grouped by category. Click a sound to load its parameters into the designer. Click the play button next to a sound to preview it immediately.

### CRT Oscilloscope

Center panel shows an animated phosphor-glow waveform visualization of the current sound. Updates live as you adjust parameters.

### TONE Tab

| Control | Description |
|---------|-------------|
| **Waveform** | `Square`, `Triangle`, `Saw`, `Sine`, `Noise` |
| **Frequency** | 20–3000 Hz; note name displayed alongside (e.g., `C#5`) |
| **Duration** | 10–2000 ms |
| **Volume** | 1–100% |
| **Pulse Width** | 10–90% (pulse width modulation for Square wave) |
| **Sweep to Hz** | Optional frequency sweep to a target pitch (creates slide effects) |

### CHORD / ARP Tab

Build chords or arpeggios from individual notes.

| Control | Description |
|---------|-------------|
| **Note list** | Add/remove notes (e.g., `C4`, `E4`, `G4`) |
| **Mode** | `Arpeggio (sequential)` plays notes in sequence; `Chord (simultaneous)` plays all at once |
| **Tempo** | Milliseconds per note (arpeggio mode) |

A mini piano keyboard lets you click notes to add them.

### SEQUENCE Tab

Text-based sequence editor. Format: `NOTE:DURATION NOTE:DURATION ...`

Example: `C4:200 E4:200 G4:200 C5:400`

### Transport Bar

| Button | Action |
|--------|--------|
| **▶ PLAY** | Preview the current sound |
| **🎲 RANDOMISE** | Generate random parameters |
| **⬆ EXPORT** | Export the sound to a WAV file |
| **💾 SAVE** | Save the sound to `sounds/custom_sounds.json` |

---

## 14. Tile Instance States

Tile instance states let you configure **per-coordinate** behavior for tiles that have step effects. This allows one tile type (e.g., `Locked Wooden Door`) to be configured differently at each location — different keys, different messages, different initial states.

### Accessing the Dialog

1. Select the **PENCIL** tool
2. **Right-click** any tile on the canvas
3. A context menu appears: `[A] TileName at (X, Y)`
4. Click **"Edit Tile Instance State…"**

Tiles that already have authored instance states show a **small cyan dot** in the top-right corner of the tile on the canvas.

You can also **"Clear Instance State"** from the same menu to remove any overrides.

### Form Fields by Effect Type

The dialog shows only the fields relevant to the tile's effect type.

#### `locked_door`

| Field | Type | Description |
|-------|------|-------------|
| `open` | Boolean | Override: already unlocked when the map loads |
| `requiredKeyId` | Item key | Override the key type needed (from KEY-type items) |
| `unlockedTileId` | Tile char | Override the tile this converts to when unlocked |
| `message` | String | Message shown to the player on unlock |
| `lockedMessage` | String | Message shown when the player lacks the key |

#### `trap_once`

| Field | Type | Description |
|-------|------|-------------|
| `triggered` | Boolean | Mark the trap as already triggered (won't fire again) |
| `amount` | Int | Override damage amount |
| `message` | String | Message shown when the trap fires |

#### `set_tile`

| Field | Type | Description |
|-------|------|-------------|
| `x` | Int | X coordinate of the tile to mutate (defaults to this tile) |
| `y` | Int | Y coordinate of the tile to mutate |
| `tileId` | Tile char | The tile char to change the target to |
| `message` | String | Message shown when triggered |

#### `teleport`

| Field | Type | Description |
|-------|------|-------------|
| `x` | Int | Target X coordinate |
| `y` | Int | Target Y coordinate |
| `map` | String | Target map name (e.g., `dungeon:5`, `town:stonehaven`) |

#### `damage`

| Field | Type | Description |
|-------|------|-------------|
| `amount` | Int | Damage dealt per step |
| `message` | String | Message shown when damage occurs |

#### `island_portal`

| Field | Type | Description |
|-------|------|-------------|
| `requiredKeyId` | Item key | Required key (falls back to any KEY item if blank) |
| `lockedMessage` | String | Message shown if the player lacks the key |

#### `toggle_door`

| Field | Type | Description |
|-------|------|-------------|
| `open` | Boolean | Current door state |
| `openChar` | Tile char | The tile shown when open |
| `closeChar` | Tile char | The tile shown when closed |

#### `pressure_plate`

| Field | Type | Description |
|-------|------|-------------|
| `x` | Int | X coord of tile to activate/deactivate |
| `y` | Int | Y coord |
| `activateChar` | Tile char | Tile char when activated |
| `deactivateChar` | Tile char | Tile char when deactivated |
| `once` | Boolean | Only fires once |

### How Instance Data Persists

Instance states are saved inside the `.rfmap` file alongside the tile grid. When a player changes a tile during play — opening a locked door, triggering a trap, stepping on a pressure plate — that change is tracked and correctly restored when they save and reload their game.

---

## 15. Town Placement

Town placement links a tile on an overworld map to a town `.rfmap` file, creating a traversable entrance.

### Workflow

1. Switch to **TOWN PLACER** tool (overworld maps only)
2. Click any tile to open the **Town Placement Dialog**
3. Choose an existing town from `data/towns/` or enter a new town name
4. An `E` tile (gold square with `E` label) appears at that location

### Opening a Linked Town

**Double-click** an existing `E` tile in TOWN PLACER mode. RetroForge will:
1. Prompt to save the current overworld map
2. Load the town's `.rfmap` file
3. Switch to the town map in the canvas

### Town Entry Point (ENTRY X/Y)

When editing a **town map**, two spinners appear in the toolbar: **ENTRY X** and **ENTRY Y**.

These coordinates define where the player spawns when entering this town from the overworld. Adjust them to position the spawn point near the town exit door. Changes are saved with the map.

---

## 16. Teleporter Placement

Overworld teleporters move the player from one position on an overworld to another (on the same or a different overworld map).

### Workflow

1. Switch to **TELEPORTER PLACER** tool
2. Click any tile to open the **Teleporter Placement Dialog**
3. Set the target overworld name and target X/Y coordinates
4. An `O` tile (cyan square with `O` label) appears

### Editing an Existing Teleporter

Left-click an existing `O` tile in TELEPORTER PLACER mode to reopen the placement dialog and edit its destination.

---

## 17. Spawn Difficulty

Spawn difficulty is a per-tile integer (0–99) stored in `MapData.spawnDifficulty[][]`. It controls the monster level range that can appear in random encounters at each tile.

- **0** = no encounters at this tile (regardless of tile type)
- **1–99** = encounter level range centered on this value

### Painting Workflow

1. Switch to **SPAWN DIFFICULTY** tool
2. Set the desired level value in the **SPAWN spinner** (toolbar)
3. Left-click or drag to paint that value across the brush area
4. Right-click to sample the existing value at a tile into the spinner

### Strategies

- Paint water, mountains, and deep wilderness with high values (30–99)
- Paint roads and town outskirts with low values (1–10)
- Paint 0 on town interiors and safe areas to disable encounters entirely
- Use large brush sizes (scroll wheel to resize) to cover terrain quickly

---

## 18. Playtesting

**GAME → Playtest Now** or the **▶ PLAYTEST** button launches RetroQuest in a new thread while RetroForge stays open.

The game starts from `saves/` if a save file exists, otherwise it shows the character creation screen. Any map changes you've saved since the last playtest will be reflected immediately — the game loads map files fresh on each play session.

> **Tip:** Save your map before playtesting. Unsaved changes are not visible in the game.

---

## 19. Keyboard Shortcuts Reference

### Main Canvas (RetroForge)

| Shortcut | Action |
|----------|--------|
| `Arrow keys` | Pan camera (smooth, 2 tiles/tick) |
| `Ctrl+Z` | Undo |
| `Ctrl+Y` | Redo |
| `Ctrl+=` | Zoom in |
| `Ctrl+-` | Zoom out |
| `Ctrl+0` | Reset zoom to 100% |
| `Mouse wheel` | Resize brush (PENCIL / SPAWN DIFFICULTY tools) |
| `Ctrl+Mouse wheel` | Zoom in/out centered on cursor |

### All Editors (common)

| Shortcut | Action |
|----------|--------|
| `Ctrl+S` | Save current item/NPC/quest/monster/tile |
| `Del` | Delete selected entry (where supported) |

### Tile Editor

| Shortcut | Action |
|----------|--------|
| `Ctrl+S` | Save current tile definition |

### Image Editor

| Shortcut | Action |
|----------|--------|
| `D` | DRAW tool |
| `E` | ERASE tool |
| `F` | FILL tool |
| `I` | EYEDROPPER tool |
| `R` | RECT tool |
| `C` | CIRCLE tool |
| `L` | LINE tool |
| `Ctrl+Z` | Undo |
| `Ctrl+Y` | Redo |
| `Ctrl+S` | Save sprite |

---

## 20. File Structure Reference

```
retroquest/
├── data/
│   ├── overworlds/          ← Overworld map files (*.rfmap)
│   │   ├── lirandel.rfmap   ← Island 1 (starting island)
│   │   ├── pyralis.rfmap    ← Island 2 (The Forged Isles)
│   │   └── mainland.rfmap   ← Legacy overworld
│   ├── towns/               ← Town map files (*.rfmap)
│   │   ├── stonehaven.rfmap      ← Island 1
│   │   ├── moonhaven.rfmap       ← Island 1
│   │   ├── mooncrest.rfmap       ← Island 1
│   │   ├── cinderport.rfmap      ← Island 2
│   │   ├── forge_keep.rfmap      ← Island 2
│   │   └── ashfen_village.rfmap  ← Island 2
│   ├── tiles.json           ← Tile registry (edited by Tile Editor)
│   ├── items.json           ← Item registry (edited by Item Editor)
│   ├── quests.json          ← Quest registry (edited by Quest Editor)
│   ├── monsters.json        ← Monster registry (edited by Monster Editor)
│   ├── game_config.json     ← Startup config (starting overworld, spawn point)
│   ├── balance.json         ← Balance tuning (dungeon, combat, encounter constants)
│   └── .forge_prefs         ← Last opened map + camera/zoom state
│
├── saves/                   ← Player save files (*.json)
│
├── sounds/
│   └── custom_sounds.json   ← Custom sounds saved from Sound Editor
│
├── tools/                   ← One-shot sprite/map generators (compile & run, then delete)
│
└── src/main/resources/
    └── tiles/
        ├── overworld/       ← Overworld tile sprites (32×32 PNG)
        ├── town/            ← Town tile sprites (32×32 PNG)
        ├── dungeon/         ← Dungeon tile sprites (32×32 PNG)
        ├── monsters/        ← Monster combat sprites (32×32 PNG)
        ├── npcs/            ← NPC portrait sprites (32×32 PNG)
        ├── letters/         ← Letter tile sprites (A–Z, 32×32 PNG)
        └── special/         ← Special tile sprites (32×32 PNG)
```

### Map File Format (`.rfmap`)

Maps are stored as plain text files in JSON format. You can open them in any text editor, though RetroForge is the recommended way to edit them. Key fields:

```json
{
  "type": "OVERWORLD",
  "name": "mainland",
  "width": 140,
  "height": 90,
  "tiles": [[".", "T", "#"], ...],
  "spawnDifficulty": [[5, 10, 0], ...],
  "interiorEntryX": 10,
  "interiorEntryY": 13,
  "npcs": [
    {
      "name": "Kael",
      "spriteName": "npcs/wizard",
      "x": 70, "y": 45,
      "type": "QUESTGIVER",
      "defaultDialog": "Welcome, traveler...",
      "dialogueTree": { ... }
    }
  ],
  "townEntrances": [
    { "townName": "stonehaven", "worldX": 50, "worldY": 30 }
  ],
  "overworldTeleporters": [
    { "x": 100, "y": 50, "targetOverworld": "mainland", "targetX": 70, "targetY": 45 }
  ],
  "initialTileStates": {
    "15,8": { "data": { "requiredKeyId": "iron_key", "lockedMessage": "The gate is locked." } }
  }
}
```

### Editor Data Files

| File | Edited By | Contains |
|------|-----------|----------|
| `data/tiles.json` | Tile Editor | All tile definitions |
| `data/items.json` | Item Editor | All item definitions |
| `data/quests.json` | Quest Editor | All quest definitions |
| `data/monsters.json` | Monster Editor | All registered monsters |
| `data/game_config.json` | Game Settings dialog | Starting overworld, spawn point, starting animation |
| `data/balance.json` | Text editor (manual) | Dungeon, combat, and encounter balance constants |
| `data/*.rfmap` | Map canvas | Maps, NPCs, dialogue trees, entrances, teleporters |
| `sounds/custom_sounds.json` | Sound Editor | Custom synthesized sounds |
| `src/main/resources/tiles/**/*.png` | Image Editor | All sprite assets |

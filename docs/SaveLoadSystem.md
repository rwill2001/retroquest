# Save & Load System — RetroQuest

**Last updated:** September 2026

## 1. Overview

The save system spans two files plus integration in `Retroquest.java`:

- **`SaveData.java`** (299 lines) — Data transfer object holding all persistent game state.
- **`SaveLoadOverlay.java`** (496 lines) — In-panel UI for 8 manual save slots plus the autosave slot with save/load/delete.
- **`Retroquest.java`** — `saveGame(int slot)` and `loadGame(int slot)` orchestration.

Saves are JSON files serialized via Gson. The system supports 8 independent save slots.

---

## 2. Save File Format

### Location & Naming

```
saves/retroquest_save_01.sav   … through …   saves/retroquest_save_08.sav
saves/retroquest_save_auto.sav                (slot 0 — autosave, every 50 steps)
```

Files are plain JSON (Gson) with a `.sav` extension. The `saves/` directory is created
automatically if missing, **relative to the process working directory** — launch from the repo root.

### Write protocol

`saveGame(int slot)` never writes the target file directly:

1. Serialize to `<file>.tmp`. On failure: log `"Save failed: …"`, delete the tmp, return `false`.
2. `Files.move(tmp, target, REPLACE_EXISTING)`. On failure: log
   `"Save failed: could not replace slot N …"`, delete the tmp, return `false` — the previous save
   is still intact.

`SaveLoadOverlay` consumes that boolean: a failed save-and-quit logs
`"Not quitting: the save failed. Try another slot."` instead of exiting.

Two caveats: the move uses `REPLACE_EXISTING` only, not `StandardCopyOption.ATOMIC_MOVE`; and
`autoSave()` uses the same tmp-then-move pattern but swallows every exception, so a failing autosave
is silent.

### SaveData Fields

| Field | Type | Description |
|-------|------|-------------|
| `player` | Player | Complete player object (stats, inventory, equipment, spells, quests, flags) |
| `currentTownName` | String? | Town name if in a town; null for overworld/dungeon |
| `currentOverworldName` | String | Overworld map name (defaults to `"lirandel"`) |
| `playerX`, `playerY` | int | Player position on current map |
| `townDoorX`, `townDoorY` | int | Overworld coordinates of the town entrance (for town exit) |
| `inDungeon` | boolean | Whether player is in the dungeon |
| `currentDepth` | int | Dungeon depth (1–50) |
| `authoredDungeonName` | String? | Set when inside an authored `.rfmap` dungeon, so load can re-open `data/dungeons/<name>_<depth>.rfmap` |
| `dungeonEntryX`, `dungeonEntryY` | int | Overworld tile the player descended from |
| `saveVersion` | int | `SaveData.CURRENT_VERSION` = 1 |
| `revealedData` | Map\<Integer, List\<String\>\> | Legacy coordinate-string fog; null on new saves (see §3) |
| `revealedBitset` | Map\<Integer, String\> | Dungeon reveal per depth, base64 bitset |
| `townRevealedBitset` | Map\<String, String\> | Town reveal per town name, `"w,h:base64"` |
| `tileStates` | Map\<String, TileState\> | Runtime tile mutations, keyed `"<mapKey>:<x>,<y>"`; null on old saves |
| `slot` | int | Save slot number (1–8; slot 0 is the autosave) |
| `displayName` | String | Formatted: `"01 - Hero - Lvl 5 - Dungeon Lvl 3 - 2026-03-09"` |
| `timestamp` | String | `"yyyy-MM-dd HH:mm"` format |
| `playerName` | String | Copy of `player.getName()` |
| `playerLevel` | int | Copy of `player.getLevel()` |
| `location` | String | `"Dungeon Lvl N"` / `"Town Name"` / `"Overworld"` |

### Metadata Fields

`displayName`, `timestamp`, `playerName`, `playerLevel`, and `location` are snapshot copies made at save time for display in the save slot list without needing to deserialize the full Player object.

---

## 3. Revealed-Map Persistence

Reveal state is written as **base64 bitsets**, not coordinate strings.

- `revealedBitset` — one entry per dungeon depth (`Map<Integer, String>`), packing the
  `boolean[HEIGHT][WIDTH]` array for that depth.
- `townRevealedBitset` — one entry per town name (`Map<String, String>`), in the form
  `"<width>,<height>:<base64>"` so the dimensions travel with the data.
- The overworld's revealed state is not persisted.

`revealedData` (the old `Map<Integer, List<String>>` of `"x,y"` strings) is retained only so old
saves still load; new saves leave it null and `SaveData` falls back to it when the bitset is absent.

### Decoding (on load)

`SaveData.restore(game)` prefers `revealedBitset`, decoding the base64 into
`game.getRevealedForLevel(depth)`, and falls back to the legacy `revealedData` string list
(bounds-checked against `Dungeon.WIDTH` / `Dungeon.HEIGHT`) when the bitset is absent. Town reveal
is restored the same way from `townRevealedBitset`, using the width/height prefix.

---

## 4. What Is / Isn't Persisted

### Persisted (survives save/load)

- All Player fields (stats, HP, gold, food, XP, level, inventory, equipment)
- Spell learning (`spellLearned` boolean array, size 38)
- Spell casts remaining (mage and cleric, per level)
- Spell buff step counters (Haste, Prayer, Holy Armor, Resist Fire, Invisibility, Protection from Evil, Bless)
- Active and completed quests
- Dialogue flags (`Map<String, String>`)
- Dungeon depth and position
- Current town/overworld name
- Fog-of-war per dungeon depth (v1+ uses Base64 bitset encoding; v0 falls back to string-list)
- Town reveal per town name
- Runtime tile mutations (`tileStates`, via `TileStateManager`) — unlocked doors, sprung traps,
  looted treasure, exhausted dungeon specials
- Resurrection ward charge state
- Save version number (`saveVersion`; `SaveData.CURRENT_VERSION` is 1)

There is no downgrade path. `loadGame` only warns when `saveVersion > CURRENT_VERSION`
("saved by a newer version… some data may not load correctly"); everything else is handled by
field-shape migrations rather than version numbers, including
`TileStateManager.migrateFromPlayerFlags(data)` for saves that stored tile mutations as player flags.

### Not Persisted (transient / rebuilt on load)

- Active effects list (rebuilt from equipped items via `rebuildEffects()`)
- Detect Magic timer
- Combat state (combat ends on save/load)
- `regenTimer` field (unused, but saved as 0)
- `knownSpells` array, size 38 (rebuilt from the 37 hard-coded definitions in `Player.initSpells()`)
- Overlay states (all overlays closed on load)

---

## 5. Old Save Migration

The system handles saves from earlier game versions:

| Migration | Trigger | Action |
|-----------|---------|--------|
| Null `spellLearned` | Array is null or all-false | Calls `initLearnedSpells()` — marks all Level-1 spells as learned |
| Null or short `knownSpells` | Array is null or sized 37 | `restoreAfterLoad()` rebuilds all 37 spell definitions and grows both arrays to 38 |
| Null `flags` map | Map is null on access | Created lazily on first `setFlag()` call |
| `@Deprecated getCurrentMapName()` | Still used in `Retroquest.loadGame()` | Alias for `getCurrentOverworldName()` |

`Player.restoreAfterLoad()` is called after Gson deserialization to handle all migrations.

---

## 6. Save/Load Overlay UI

### Layout

```
┌──────────────────────────────────────────────┐
│  ◊ SAVE GAME  /  ◊ LOAD GAME               │  title (38px)
├──────────────────────────────────────────────┤
│  01 ◆ Hero - Lvl 5 - Town - 2026-03-09     │  slot list
│  02 □ [EMPTY]                                │  (8 rows)
│  03 ◆ Warrior - Lvl 12 - Dungeon Lvl 7     │
│  ...                                         │
├──────────────────────────────────────────────┤
│  [Enter=Save Here]  [D=Delete]  [Esc=Close] │  key bar (34px)
└──────────────────────────────────────────────┘
```

- Card: max 640×440px, centered. Slide-in animation from bottom (300ms ease-out).
- Save mode: amber border and icons. Load mode: cyan border and icons.
- Slot icons: `□` (empty), `⚠` (corrupt), `◆` (save mode), `▶` (load mode).

### Controls

| Key | Action |
|-----|--------|
| UP/K, DOWN/J | Navigate slots |
| ENTER | Save to / Load from selected slot |
| D | Delete selected slot (save mode only) |
| ESC | Close overlay |

### Confirmation Flow

- **Overwrite**: if saving to a non-empty slot, prompts `[Y] Overwrite / [Any] Cancel`.
- **Delete**: prompts `[Y] Delete / [Any] Cancel`.
- Both use `awaitingConfirm` flag to intercept next keypress.

### Corrupt File Handling

If Gson deserialization fails (malformed JSON, schema mismatch), the slot shows `[CORRUPT]` and cannot be loaded. The file is not deleted automatically.

### Quit-on-Save

`openSave(boolean quitAfter)` accepts a flag. If true, `System.exit(0)` is called after successful save — used by the quit confirmation flow.

---

## 7. Interfaces & Dependencies

- `SaveData` → `Player`, `Town`, `Dungeon` (constants only), `Retroquest` (revealed map access)
- `SaveLoadOverlay` → `Retroquest` (`saveGame()`, `loadGame()`), `MessageLog`, `SpellbookOverlay` (shared key badge painting)
- `Retroquest.saveGame(slot)` → creates `SaveData`, serializes to file
- `Retroquest.loadGame(slot)` → deserializes `SaveData`, calls `restore()`, calls `player.restoreAfterLoad()`

---


# RetroQuest — Claude Code Instructions

## Environment

### Java
- **JDK**: Amazon Corretto 26 (`C:/Program Files/Amazon Corretto/jdk26.0.0_35/`)
  — `java -version` reports `openjdk version "26"` / `Corretto-26.0.0.35.2`
- `java` and `javac` are on PATH — use directly
- The pom still targets **Java 17** bytecode (`maven.compiler.source`/`target` = 17), so compile
  with the JDK 26 toolchain but do not use language features newer than 17

### Build
- Build tool: Maven 3.9.14 (`C:/Program Files/Maven/apache-maven-3.9.14/`), `mvn` is on PATH
- **Important**: `mvn` requires `JAVA_HOME` in Git Bash (not inherited from Windows). Using the
  old Corretto 17 path fails with "The JAVA_HOME environment variable is not defined correctly":
  ```bash
  export JAVA_HOME="C:/Program Files/Amazon Corretto/jdk26.0.0_35"
  mvn compile
  ```
- Standard build: `mvn compile` — 148 source files (`find src/main/java -name "*.java" | wc -l`)
- To compile a standalone utility class:
  ```bash
  javac MyClass.java
  java MyClass
  ```
- Clean up generated `.class` files after running one-shot utilities

### Running
Two entry points, both launched from the repo root (see **Working directory** below):
- Game:   `io.cannonforge.retroquest.core.Retroquest`
- Editor: `io.cannonforge.retroquest.editor.RetroForge`

The `run-retroquest.bat` / `run-retroforge.bat` wrappers compile if needed and then run with the
classpath cached in `cp.txt`. (The `.sh` variants pick the newest JDK they can find rather than
pinning a version, so they do not need `JAVA_HOME` set by hand.)

### Window size and fullscreen

UI scale is resolved by `DisplayScale` from the primary screen width, then **clamped to the
largest scale whose packed window actually fits the desktop**. Without that ceiling `pack()`
cheerfully asks for a window taller than the display, the OS clamps it, and the fixed layout is
silently cropped — the stats column loses its right-hand edge. `maxScaleThatFits()` derives the
ceiling from `BASE_W`/`BASE_H`, which must track the three panels' preferred sizes.

```
run-retroquest.bat 1.5      # explicit factor (also: RETROQUEST_SCALE=1.5)
run-retroquest.bat fit      # largest the display can show
run-retroquest.bat          # auto — screen-width bucket, capped by the fit ceiling
```

`-Dretroquest.uiScale=<factor|fit>` still works directly; factors are clamped to 0.75–3.0 *and* to
the fit ceiling.

**F11 toggles borderless fullscreen.** It does not rescale — panels bake `DisplayScale.SCALE` into
`static final` fields at class-init, so the scale is fixed for the life of the JVM. It doesn't need
to: `GamePanel` is the `BorderLayout.CENTER` component, so a maximised window simply hands it more
room. The first-person renderers project from the panel's width and height, so they genuinely draw
more; the tile grid keeps **square** tiles (`tileSize()` takes the min of both divisions) and
centres itself in whatever it is given.

Two hazards if you touch this:
- `dispose()` is required before the decoration can change, and it fires `windowClosed`, whose
  handler calls `System.exit`. The guard is `isDisplayable()` — the event arrives *after* the
  window is back, so a flag set around the call has already been cleared by then.
- Drawing between the grid's `translate` and its inverse is in grid space; the HUD and every
  overlay run after the inverse, in screen space.

### Shell
- Shell: bash (MINGW64 / Git Bash on Windows)
- Use Unix paths with forward slashes (e.g. `/c/Users/<you>/...`)
- `python` and `python3` are NOT available — use Java for code generation tasks

## Project Structure
- Source root: `src/main/java/io/cannonforge/retroquest/`
  — `overlay/` (46), `model/` (25), `editor/` (23), `animation/` (17), `core/` (20),
    `registry/` (9), `dialogue/` (5), `dialog/` (5), `controller/` (4) — 154 total
- Resources:   `src/main/resources/` (`tiles/`, plus `retroquest.ico` / `retroquest_icon.png`)
- Monster sprites: `src/main/resources/tiles/monsters/` (32×32 PNG, key = `monsters/<name_without_ext>`)
- Save files:  `saves/*.sav` (slots 01-08 plus `retroquest_save_auto.sav`)
- Crash/diagnostic log: `logs/retroquest.log` (rotates to `.log.1`) — installed by
  `CrashLogger.install()`, because the shipped launcher uses `javaw.exe` and has no console
- Custom sounds (optional): `sounds/custom_sounds.json` — **hand-authored**. `SoundManager` reads it
  and lets an entry override any built-in sound by name, but no tool writes it: the editor that
  could have was deliberately dropped from RetroForge, and it only ever kept sounds for the
  session anyway. Read path only.

### Working directory
**All game data paths are resolved relative to the process working directory** (`new File("data/...")`,
`new File("saves/...")`). Launch the game and the editor from the repo root or they will read and
write the wrong `data/` tree — or create a fresh empty one.

## Data Layout (`data/`)

| Path | Contents |
|---|---|
| `tiles.json` | Tile definitions keyed by a single char (id, name, walkable, blocksVision, lightRadius, sprite key, editor colour, onStep `TileEffect`) |
| `items.json` | 126 items |
| `monsters.json` | 78 monsters |
| `quests.json` | Quest definitions |
| `balance.json` | Tunable combat/boss numbers, read via `BalanceConfig` |
| `game_config.json` | Starting overworld and other global config (`GameConfig`) |
| `overworlds/*.rfmap` | 7 island maps: lirandel, pyralis, zephyrion, sylvandar, thalorax, umbryn, bellorak |
| `towns/*.rfmap` | Town interiors, one file per town, loaded on entry |
| `dungeons/*.rfmap` | Authored dungeon levels, named `<dungeon>_<level>.rfmap` |

`.rfmap` files are Gson JSON `MapData` — tile grid, spawn-difficulty grid, NPCs, town entrances,
overworld teleporters, and `initialTileStates` (per-coordinate authored key/value data).

## How Maps and Dungeons Are Wired

- **Towns**: a `TownEntrance` on the overworld (tile `'E'`) names the town; `Town` loads
  `data/towns/<name>.rfmap` on entry.
- **Island portals**: tiles whose `onStep` effect type is `island_portal` (or `teleport`), carrying
  `map`, `x`, `y` and an optional `requiredKeyId`. Missing key → the portal logs `lockedMessage`
  and does nothing. First-time forward portals show the departing god's `DivineAudienceOverlay`
  and defer the teleport until it is dismissed.
- **Procedural dungeon**: tile `'D'` with no tile state → `DungeonController.enterDungeon()`.
- **Authored dungeon**: tile `'D'` whose `initialTileStates` entry (keyed `"x,y"`) carries
  `dungeonName` → `enterAuthoredDungeon(name)` loads `data/dungeons/<name>_1.rfmap`. The same tile
  state may carry `requiredKeyId` + `lockedMessage` to gate the entrance (this is how the Cradle of
  Shards entrance works — a key-gated dungeon entrance, not a portal).
- Runtime tile mutations (unlocked doors, `set_tile`, sprung traps, exhausted specials) persist in
  `SaveData` through `TileStateManager`, keyed `"<mapKey>:<x>,<y>"` where mapKey is
  `overworld:<name>` / `town:<name>` / `dungeon:<depth>`.

## Island Ladder

Island order (and portal key chain order) is
**lirandel → pyralis → zephyrion → sylvandar → thalorax → umbryn → bellorak**, one key per island.

Dungeon presentation is chosen by `DungeonController.modeForIsland(overworldName)`:

| Overworld | `DungeonViewState.Mode` | Look |
|---|---|---|
| `lirandel` | `TOP_DOWN` | Torch-lit overhead grid |
| `sylvandar` | `TEXTURED` | `TexturedDungeonRenderer` first-person — bark and root |
| `thalorax` | `TEXTURED` | `TexturedDungeonRenderer` first-person — chiselled crypt masonry |
| `umbryn` | `RAYCAST` | `RaycastDungeonRenderer` — texture-mapped, per-pixel lit, free-angle |
| `bellorak` | `RAYCAST` | as Umbryn, plus map-authored lights, shadows and the Cradle's mirror floor |
| everything else | `WIREFRAME` | `WireframeDungeonRenderer` first-person |

Authored dungeons use the same ladder but never fall back to `TOP_DOWN` — a `TOP_DOWN` result is
promoted to `WIREFRAME`. `Retroquest.loadGame` calls the same method, so a save restores into the
view the descent would have picked.

`RAYCAST` additionally falls back to `TexturedDungeonRenderer` when
`RaycastDungeonRenderer.canRender()` is false — see the wall-model note below.

Only Pyralis and Zephyrion still reach `WireframeDungeonRenderer`. Its colours come from
`WireframeDungeonRenderer.paletteFor(dungeonName)` — one `Palette` constant per dungeon, resolved
once per frame. The deep / shadow / war / cradle entries are unreachable given the table above but
are kept so routing an island back to `WIREFRAME` still gives it its own colours.

### The first-person view loop

`Retroquest.startDungeonViewLoop()` repaints a first-person dungeon at ~33fps and advances
`DungeonCamera`. It self-stops one tick after the player leaves. **Nothing repainted a standing
still dungeon before it existed** — the overlay timer only runs while an overlay is up — so
Rootvault's spores and Island 5's sconce flicker were frozen until you moved.

### Textured dungeon theming

`TexturedDungeonRenderer` is theme-driven, not hard-coded to one dungeon:

- **`DungeonTheme`** — palette + `WallStyle` + sconce and particle config, one factory constant per
  dungeon, resolved by `DungeonTheme.resolve(authoredDungeonName, overworldName)`. `CRYPT` is the
  fallback, so a dungeon promoted to `TEXTURED` before it has a theme still looks deliberate.
- **`DungeonWallPainter`** — draws the stone. Masonry is laid out in **world depth**, not screen
  space, and projected through `DungeonWallPainter.Corridor` (the same `1/(1+0.8z)` the renderer
  has always used), so courses foreshorten correctly. Relief comes from filled chamfer wedges plus
  a per-block stipple; cracks, staining, growth, veining and sconce placement all hash from the
  dungeon cell, so a wall looks identical every frame and from every distance with nothing stored.

| `WallStyle` | Used by | Look |
|---|---|---|
| `BARK` | `rootvault` | The original Sylvandar surfaces — unchanged, drawn by the renderer itself |
| `BLOCK` | `pressure_temple`, `CRYPT` | Large chiselled ashlar, 5 courses |
| `COBBLE` | `boneyard_trench` | Small jittered rubble in a deep mortar bed |
| `SLAB` | `leviathan_eye` | Few huge polished slabs with mineral veining |
| `CYCLOPEAN` | `forgotten_city` | Irregular polygonal stones fitted tight, no continuous joint |

Adding a textured dungeon is a `DungeonTheme` constant plus a line in `resolve` — not a renderer.

### Island 6: the raycaster

`RaycastDungeonRenderer` is a different animal from the band renderers. It casts one ray per screen
column across the tile grid (DDA), so the view can face **any** angle rather than four, and walls
are sampled from `DungeonTextures` pages rather than drawn shape by shape.

- **`DungeonTextures`** — procedurally generates one texture set per theme, once, into `int[]`
  pages: four wall variants (two plain, one theme *feature*, one damaged), floor, ceiling, door.
  Pages are **pure albedo** — never bake shadow into them, the renderer multiplies by light.
  They must tile horizontally, since a wall face is exactly one page wide.
- **Lighting** is per pixel: a warm lantern riding on the camera plus up to four coloured sconce
  point lights, each with a real surface-normal term, run through a soft-knee rolloff so two
  overlapping lights compress instead of clipping to white. Ambient is deliberately cold, which is
  what splits the frame into a warm pool and a blue dark.
- **`DungeonCamera`** (in `model/`) holds a floating position and heading that ease towards the
  player's tile and facing. Gameplay still resolves on the keypress — only the picture lags. It
  snaps for jumps too far to be a walk (teleport, chute, level change, load). **V** toggles it.
- **Wall model, and why this is authored-only**: procedural dungeons store walls as *edges between
  cells*, so two open cells can have a wall between them. A grid raycaster needs walls to be
  *cells*, which is exactly what a `.rfmap` gives. `canRender()` enforces this.

`DungeonTheme.Feature` picks what the feature wall carries — `SHELVES` for the Archive of Tears,
`FRESCO` for the Forgotten City.

### Island 7: lights the map owns, and shadows

**`DungeonLighting` reads lighting out of the map.** Every tile in `tiles.json` carries a
`lightRadius`, and 44 of them set it — lava channels, ember grates, wall torches, forges. Any such
tile placed in a dungeon *is* a light: its `lightRadius` is the reach and its `editorColor` is the
colour. **Lighting is therefore authorable in RetroForge.** A dungeon that authors no lights falls
back to the hashed sconces Island 6 invented, which is why Islands 4–6 are unchanged.

**Shadows are precomputed, and that is what makes them affordable.** Two facts:

- The lantern rides on the eye, and *a light at the eye casts no shadow you can see*. Every visible
  shadow comes from a fixture.
- Fixtures don't move and neither do walls — so per-light visibility is computed **once per level**
  into a small field (4 samples per tile, bilinearly filtered) and merely sampled while drawing.

The cache is keyed on a fingerprint of the tile grid, so opening a door rebuilds the lighting
rather than leaving a shadow hanging in the air. When shading a **wall**, sample the visibility
field offset along the surface normal — the wall point itself is on the boundary of a solid cell
and would always read as shadowed.

**Tile identity drives the surface.** `DungeonTextures.pageForTile` maps `k`→bookshelf,
`M`→pillar, `[`→iron gate, `n`→barrels, `#`→rough rock, `-`/`+`/`v`/`N`→emissive fixtures;
anything unrecognised falls through to the hashed plain/feature/damaged rotation. This is a
back-port as much as a feature — the Archive of Tears authors 272 bookshelf tiles that every
previous renderer drew as anonymous stone.

**The Cradle of Shards escalates.** `DungeonTheme.mirror(reflectivity, prismatic, escalate)` and
`.glow(amount)`. A flat mirror floor reflects a wall as that same wall flipped about the line where
it meets the floor, which the column has already computed — so the reflection is one extra pass,
not a second render. With `escalate` on, reflectivity and the prismatic split ramp with dungeon
depth across its 8 levels. `selfGlow` exists because crystal halls are too open for a carried
lantern to reach the walls at all.

**Emissive fixtures in the Bellorak maps** were placed by swapping non-walkable `W`/`#` wall tiles
for other non-walkable tiles that carry a `lightRadius`. Wall-for-wall, so reachability is
bit-identical — verify that property against `tiles.json`, not a hardcoded wall list, if you redo
it. The Cradle is deliberately unlit by fixtures; its light is its own walls.

## Key Conventions
- All overlays: `paint(Graphics2D g, int W, int H)` called from `GamePanel.paintComponent`, plus
  `isActive()`, `handleKey(KeyEvent)`, `handleClick(int,int)`, `open`/`close`
- Overlay geometry and fonts go through `OverlayTheme.scaled(int)` / `Fonts.mono*` so everything
  respects `DisplayScale.SCALE` — never hardcode raw pixel sizes in an overlay
- Monster image key in `CombatOverlay`: `"monsters/" + monster.getImageFileName().replace(".png", "")`
- `MonsterFactory.generate(level)` clamps level 1–50 and is the last-resort spawn source;
  `MonsterRegistry.getRandomMonster(level)` rolls 5% friendly → exact-level registry entry (75%)
  → ±3 levels (50%) → factory
- `monsterType` (`BEAST, HUMANOID, UNDEAD, MAGICAL, DEMON, DRAGON, FRIENDLY`; null ⇒ `HUMANOID`)
  drives Turn Undead, Holy Word, undead XP drain, and friendly-spawn exclusion
- Maps and registries serialize as JSON via Gson with `ColorAdapter` for `java.awt.Color`
- Prompts and free-text input are `MessageLog.prompt(...)` / `MessageLog.promptInput(...)`;
  while `messageLog.isPromptActive()` the log consumes all keys, so guard auto-triggers with it
- Fog-of-war/lighting is `VisionSystem.computeBrightness(game)` — radial light with line-of-sight,
  not a binary revealed flag
- Sound is procedurally synthesized — no audio files

## Content checks

`sh tools/check-content.sh` (needs `node`) walks the shipped data and fails if the game
cannot be finished. Run it after touching maps, tiles, quests or NPC placement.

| Check | What it proves |
|---|---|
| `critical-path.js` | arrival -> towns -> dungeons -> key -> exit portal is walkable, island by island, and no island is a one-way trip |
| `quest-audit.js` | every quest is offered by a reachable NPC and its target exists (KILL target `"any"` is a wildcard) |
| `npc-audit.js` | no NPC is entombed in solid terrain or hidden behind an earlier NPC — `NpcController.findNearbyNPC` returns the FIRST match within Chebyshev 1, so two NPCs on nearby tiles means only one is ever reachable |
| `dungeon-audit.js` | every walkable feature in all 40 authored dungeon levels is reachable from that level entry |
| `xp-ladder.js` | informational: how much of each island climb its own quests pay for |

Each exists because a scripted playthrough walked into the problem it looks for. The eight
town files no island links to (`stonehaven`, `high town`, `lowtown`, `north town`,
`riverside`, `south riverside`, `north riverside`, `st marks`) are reported separately and
do not fail the check.

## Docs
`docs/Architecture.md` is the entry point and cross-references every subsystem document.

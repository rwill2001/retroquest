# The Recorder — how the footage is made

**No screen capture is involved.** The game renders its own frames to PNG, offscreen, with no
window on the desktop, at whatever resolution and frame rate the shot asks for. ffmpeg turns the
sequence into an MP4.

This is the same shape as the recorder in the seaglass-globe project: the *application* owns the
frame dump, and a shell wrapper supplies the scene. The alternative — pointing OBS at a running
game — records whatever the desktop is doing, at whatever rate the encoder keeps up with, can't
render larger than the window, and takes over the machine for the length of every take.

---

## The three pieces

| | |
|---|---|
| `core/RetroRecorder.java` | The frame dump. Builds the game with no window, sets up a scene, paints `GamePanel` into a `BufferedImage` once per frame, writes `frame_%06d.png`. |
| `tools/record.sh` | Renders one or more shots and encodes each to `out/<id>/<id>.mp4`. |
| `tools/shots/<id>.shot` | A shot: sourced bash setting only what makes this shot different. |
| `tools/assemble.sh` + `*.edl` | Cuts recorded clips together into a sequence to watch. |

```bash
sh tools/record.sh --list                 # what shots exist
sh tools/record.sh --all                  # regenerate every clip from scratch
sh tools/record.sh s25-raycast            # render and encode one
sh tools/record.sh s21-topdown s22-wireframe s23-bark
sh tools/assemble.sh tools/act4.edl out/_release/act4-descent.mp4
```

**out/ is gitignored, so a fresh checkout has every shot recipe and no footage.**
`sh tools/record.sh --all` rebuilds all of it, then `sh tools/assemble.sh tools/reel.edl <out.mp4>`
cuts the reel. Takes a few minutes — the loop paces to the wall clock, so it costs roughly the
runtime of the footage plus JVM startup per shot.

Prerequisites: `mvn compile`, a `cp.txt` (`mvn dependency:build-classpath -Dmdep.outputFile=cp.txt`),
and ffmpeg on PATH. **Always run from the repo root** — every data path in the game resolves against
the working directory.

---

## Why this works at all

The three first-person renderers have the signature

```java
public static void paint(Graphics2D g2, int W, int H, Retroquest game)
```

and read nothing from the window — only game *state*: `getPlayer()`, `getCurrentMap()`,
`getCurrentDepth()`, `getDungeonViewState()`. So the recorder can populate that state, hand them a
`Graphics2D` that belongs to a `BufferedImage`, and get a frame. `GamePanel` is painted rather than
the renderers directly, so overlays and the HUD come along too.

Two additions to `Retroquest` make it possible, both additive and both used only by the recorder:

- `Retroquest(boolean showStartMenu)` — the public constructor ends with a **modal**
  `StartMenuDialog`, which would block the EDT forever and put a window on the operator's screen.
- `recordingOffscreen` — `startGame` calls `setVisible(true)`; this suppresses it so nothing
  flashes on the desktop during a render.

Nothing else in the game changed.

---

## Two clocks, deliberately

- **The camera is driven.** `DungeonCamera.update(long deltaMs)` takes an explicit delta, so the
  ease runs at exactly `1000/fps` per frame and is reproducible.
- **Ambient motion is not.** Rootvault's spores, the sconce flicker and the CRT phosphor all read
  `System.currentTimeMillis()` directly. Nothing can drive those, so the loop **paces itself to the
  wall clock** by default — an 8-second shot takes 8 seconds to render, and the ambient motion comes
  out at true speed.

  If a render can't keep up (the raycaster at 1080p runs about 80ms a frame, against a 33ms budget),
  the pacing has nothing to slow down and ambient motion plays roughly 2.4× fast in the clip. It's
  subtle — a slightly hurried sconce flicker — but it is real. Rendering those shots at 1280×720 and
  upscaling brings them back under budget if it ever matters.

`--camera-rate` is a separate control: it feeds `update()` a *fraction* of the frame delta, easing
the same interpolation more slowly. The game's turn resolves in 150ms, which at 30fps is five frames
and reads as a snap on camera; `--camera-rate 0.25` stretches it to about a second so the
intermediate angles are actually visible. **This changes only how fast the picture catches up.** The
frames are real renderer output at real off-axis angles — which is the entire point of the Island 6
shot, and would be a lie if it were faked.

---

## The fixed-size UI trap

The first-person renderers scale to the frame. **The tile map, the top-down dungeon and every
overlay do not** — they draw at a fixed pixel size off `DisplayScale.SCALE` and centre themselves.
Asking the recorder for 1920×1080 directly leaves a combat panel sitting small in a sea of black.

The fix, and the right one for pixel art: render at the size where the content fills the frame, then
integer-upscale with **nearest neighbour**.

```bash
WIDTH=800; HEIGHT=450; UPSCALE=1920:1080     # combat overlay
WIDTH=640; HEIGHT=360; UPSCALE=1920:1080     # top-down dungeon and tile map
ZOOM=7                                        # and shrink the tile viewport (24x18 -> 10x10)
```

Never let ffmpeg use its default scaler on this footage. Bilinear on 32×32 sprite art undoes the
whole look.

---

## What is recorded, and what is not

### Recorded — 33 shots, 141 seconds, all real renderer output

| Shot | What |
|---|---|
| `s05-chargen` | Character creation — 3d6, what each roll buys, three live rerolls |
| `s08-crawl` | The intro crawl typing itself in phosphor green |
| `s09-overworld` | Lirandel tile overworld, walking east |
| `s10-town` | Moonhaven interior — shopfront, inn, NPCs at their posts |
| `s11-dialogue` | Innkeeper Bram, five branching choices, one highlighted |
| `s12-shop` | General Store — stock, prices, buy/sell tabs |
| `s13a`–`s13e` | Five venues driven past their menus into the playfield: casino, sky, nature, war, arena |
| `s14-divine` | A god addressing the Unbound |
| `s17-spellbook` | The combat spellbook, 34 spells, scrolling |
| `s16-combat` | Turn-based combat, three attack rounds, readable log |
| `s21-topdown` | Island 1 — top-down grid, procedural, torch-lit fog |
| `s22-wireframe` | Islands 2–3 — vector wireframe, through a door, turn left |
| `s23-bark` | Island 4 — Rootvault bark, three motionless seconds of drifting spores |
| `s24a`–`s24d` | The four wall styles: BLOCK, COBBLE, SLAB, CYCLOPEAN |
| `s25-raycast` | Island 6 — the raycaster turning through off-axis angles |
| `s26-lit` | Island 7 — map-authored lighting in a Bellorak war tunnel |
| `s27-cradle` | The Cradle of Shards, level 6 — mirror floor, prismatic split |
| `s30-forge` | RetroForge at rest — menu bar, toolbar, palette, canvas |
| `s31-paint` | Pencil painting a real coastline, then a flood-filled bay |
| `s36-spawn` | Spawn difficulty painted in four bands, 12 → 88 |
| `s32-dialogue` | The dialogue tree editor — Bram's real tree, choices, actions, flow graph |
| `s32-image` | The 32x32 sprite editor |
| `s33-quest` `s33-item` `s33-monster` `s33-tile` | The other four sub-editors |

### Not recorded, and why

| Missing | Why |
|---|---|
| **S20** dungeon entry | A time-driven animation, not state-driven: it reads the wall clock and runs on its own schedule, and the recorder skips it (`-Dretroquest.skipAnimations=true`) rather than driving it. Adding an animation scene is straightforward — start it and let the realtime loop run — but it is not done. (The wash-ashore animation was the other one, and it has since been cut from the video.) |

| **S15, S18, S19** encounter, low-HP, boss | Combat variants. `s16-combat` covers the mechanic; these three need a scripted damage state and a boss via `BossFightCoordinator`, neither wired up yet. |
| **The seven sub-editors** (item, quest, monster, NPC, tile, image, dialogue) | Each is a modal `JDialog` opened from the EDITORS menu. Modal means it blocks the thread that opens it, so filming one needs the same `show` flag `RetroForge` and `Retroquest` now have. The editor *frame* films fine; its dialogs do not, yet. |

### Two shots that were re-directed

Both rendered correctly the first time and both were of the wrong thing — which is the failure
mode to watch for here. A clip that looks deliberate and is not what the script asked for gets
past a review in a way a black frame never would.

- **`s26-lit`** was shot in a `war_beneath` corridor with no occluder between the camera and any
  fixture, so the cast shadow the Island 7 beat is *about* never appeared. Re-shot along
  `iron_pit_1` at y=8 — a 14-tile run between two wall fixtures (`-` r=4 at 24,9 and at 30,7)
  whose pools sweep past the camera in turn, with masonry between them to occlude.
- **`s23-bark`** was shot in Rootvault’s entry room, which is eight tiles wide. The lantern rides
  on the eye, so walls that far away are barely lit and the bark grain did not read at all.
  Re-shot in the 1-wide corridor at x=20, y=7–13, where the walls are close enough to light.

Neither needed a code change. Both were `AT`/`MOVE` edits in the shot file, which is the point of
keeping shots as data rather than as a recorded session.

---

## Paint on the EDT, always

Every frame is painted through `SwingUtilities.invokeAndWait`. This is not optional politeness.
Swing timers mutate the component tree from the EDT while the render loop runs — the intro crawl's
typewriter, the overlay repaint timer, and at the end of the crawl `startGame()`, which tears down
and re-packs every panel. Painting a container from the recorder's own thread at the same time
races for the AWT tree lock, and **the intro shot deadlocked the whole recorder** until this was
serialised. Every scene was already relying on getting lucky.

## Driving a modal dialog

`--scene chargen` and `--scene intro` film Act 1, and `--editor NAME` films a RetroForge
sub-editor. All of them are modal `JDialog`s whose public constructors block the caller until a
person dismisses them, so each got the same `show` flag `Retroquest` and `RetroForge` have:
`CharacterCreationDialog`, `IntroCinematicDialog`, `ItemEditorDialog`, `QuestEditorDialog`,
`MonsterEditorDialog`, `TileEditor`, `ImageEditor`, `DialogueTreeEditor`. Built with `show=false`
they lay out but never display. All seven sub-editors are filmable.

Two of those files carry **mixed CRLF and LF line endings**, which is why a literal-string edit and
an `
`-only regex both silently matched nothing. Anything scripted against this source has to use
`?
`.

In a dialog scene the move script takes `BTN:REROLL` — a depth-first search for a button whose
label contains that text, then `doClick()`. That is how the character-creation shot rerolls without
reaching into the dialog's private methods.

## Driving the editor

`--scene forge` builds `RetroForge` with its window suppressed and paints its **root pane**, which
is what carries the menu bar as well as the content. One trap: `pack()` before sizing. A frame that
was never shown has no peer, and without a peer `validate()` does not lay out its descendants — the
first take of the editor came back as a single flat grey rectangle. `pack()` calls `addNotify`,
which realises the window without displaying it.

The move script becomes editor commands, dispatched as real `MouseEvent`s to `MapCanvas` so they go
through the editor's own listeners and paint tiles for real — undo stack and all:

| Command | Does |
|---|---|
| `T:PENCIL` | Selects a tool through `RetroForge.selectTool`, so the toolbar highlight follows. `canvas.setTool` alone switches the canvas and leaves the toolbar showing the old tool. |
| `B:~` | Sets the brush tile by its character |
| `S:60` | Sets the spawn-difficulty value. The overlay alpha is `diff*2`, so the default of 5 paints something very nearly invisible |
| `P:8,14` | Clicks one viewport tile (0–33, 0–20) |
| `D:4,3,12,7` | Drags a stroke between two viewport tiles |

**Coordinates are viewport tiles, not world tiles**, so a shot does not need to know where the
editor parked its camera when it restored the last session.

**Nothing is ever saved.** The editor edits in memory and the recorder never calls save, so a shot
that paints a coastline across Lirandel leaves `data/overworlds/lirandel.rfmap` untouched. Verified
with `git status data/` after a take.

**Move scripts separate on `;` when the script contains one**, and on `,` otherwise. Both exist
because commas are also coordinate separators — `D:4,3,12,7` split on commas becomes four
unparseable fragments, which is exactly how the first editor shot came back with nothing painted.

## Driving an overlay

`--overlay NAME` opens one on top of whatever scene was built — the way the game shows it, a shop
drawn over the town you are standing in. Opening it directly is the only way to film it: most need
an NPC, a tile or a quest state in front of you before a keystroke can reach them.

Names: `shop`, `inventory`, `spellbook`, `questlog`, `dialogue`, `divine`, `casino`, `arena`,
`depths`, `sky`, `nature`, `memory`, `war`.

With an overlay open the move script becomes **keystrokes**, routed topmost-first exactly as the
game routes them. Single letters, or the named keys `ENTER`, `ESC`, `SPACE`, `UP`, `DOWN`,
`LEFT`, `RIGHT` — the venue menus are driven with arrows and Enter, not letters, and a shot that
has to get past one needs those.

Two state flags matter for these:

- `XP=200000` — a fresh Player is level 1 with 54 HP. Combat defaults this high on its own.
- `LEARN_ALL=on` — **level does not open the spellbook.** Spells are learned one at a time and a
  new character knows only the level-1 ones, so a spellbook shot without this is a shot of a
  single line. Note also that the book filters by context: outside combat it lists the nine
  map-usable spells; the combat book lists 34.

## Adding a shot

Create `tools/shots/<id>.shot`. Only override what differs from the defaults in `record.sh`:

```bash
# One line saying what the shot is of — this is what --list prints.
DESC="Island 6 · Archive of Tears · RAYCAST"
ISLAND=umbryn
DUNGEON=archive_of_tears
SECONDS_LEN=8
CAMERA_RATE=0.25
MOVE="1.2:F,2.4:L,4.6:R,6.4:F"
```

Move scripts are `seconds:command` pairs — `F`/`B` step, `L`/`R` turn, `@x,y` teleport, and in a
combat scene a single letter is a keystroke. **A move that would walk into a wall is refused and
reported**, so a bad path shows up as a warning rather than as a frame inside solid rock. Author
paths against the real map rather than guessing.

`RaycastDungeonRenderer.canRender()` returning false makes the game fall back to the textured
renderer *silently* — the clip still renders and still looks deliberate, but is of the wrong
renderer. The recorder checks this and warns.

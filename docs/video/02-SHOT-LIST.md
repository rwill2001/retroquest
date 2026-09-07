# Capture Checklist — RetroQuest × RetroForge Hype Video

Everything in `01-SCRIPT.md` is capturable from the real build. Nothing needs to be recreated in a
motion-graphics tool. This document is the operator's list: what to launch, what state to be in,
and which keys to press.

---

## 0. Before you record anything

### Launch settings

Run **from the repository root** — every `data/` and `saves/` path is resolved relative to the
working directory, and launching from anywhere else reads the wrong content tree.

```bash
run-retroquest.bat fit     # game, largest scale the display can show
run-retroforge.bat         # editor
```

- `fit` gives the crispest capture. If the desktop is 1920×1080, `run-retroquest.bat 2` is
  usually the cleanest whole-number scale.
- **F11** toggles borderless fullscreen. Use it for every gameplay shot. It does not rescale the
  UI — `DisplayScale.SCALE` is baked in at class-init — it just gives `GamePanel` more room, which
  is exactly what you want: the first-person renderers genuinely draw more, and the tile grid keeps
  square tiles and re-centres.
- **V** toggles `DungeonCamera` easing in first-person dungeons. **Leave easing ON** for all of Act
  4 — the lag on turns is what makes S25's continuous sweep read as smooth rather than snapped.
- **M** opens the dungeon minimap. Don't — it is not in any shot.

### Recording spec

| Setting | Value |
|---|---|
| Resolution | 1920×1080, native, no scaling in the capture tool |
| Frame rate | 60fps constant |
| Codec | Lossless or near-lossless (ProRes / UT Video / CRF 12 h264). **Not** a streaming preset |
| Scaling filter | **Nearest-neighbour everywhere.** 32×32 sprites bilinear-filtered look like a phone game |
| Cursor | Hidden for all game shots; **visible** for all RetroForge shots — the cursor is the story there |
| Audio | Record the game's synthesized audio to a separate track. Some of it is usable as SFX |

### Save slots to build first

Act 4 needs the player standing in seven different dungeons. Don't try to play there live. Build
these saves once, then load them per shot (`S` saves; the load menu is on the start screen).

| Slot | Where the player stands | Used by |
|---|---|---|
| 01 | Lirandel overworld, a few tiles from a `D` tile, daylight terrain around | S09, S15, S20 |
| 02 | Inside a town on Lirandel, facing an NPC with a dialogue tree | S10, S11, S12 |
| 03 | Lirandel authored dungeon, level 1, at the mouth of a long corridor | S21 |
| 04 | Pyralis or Zephyrion dungeon, 3 cells from a corner with a door beyond it | S22 |
| 05 | Rootvault (Sylvandar), in a chamber where spores are visible against a dark wall | S23 |
| 06 | Four sub-saves: Pressure Temple, Boneyard Trench, Leviathan Eye, Forgotten City — each facing a wall flat-on from the same distance | S24 |
| 07 | Archive of Tears (Umbryn), in a room with a sconce and bookshelf tiles on two sides | S25 |
| 08 | Bellorak dungeon with lava/ember/torch fixtures, with a pillar between the light and the camera | S26 |
| auto | Cradle of Shards, level 6+ (reflectivity has ramped by then) | S27 |

For slot 06 the *framing must match across all four*. Note the exact tile and facing for the first
one and reproduce it in the other three — the whole point of the shot is that only the masonry
changes.

---

## 1. Act 0 — Cold open

**S01 · CRT boot** — Not gameplay. Built in the edit from the boot text in `01-SCRIPT.md`. The
numbers in it are real (148 classes, 192 tiles, synthesized audio); keep them accurate.

**S02–S03 · Cut.** The wash-ashore animation is no longer in the video. Act 0 is the boot sequence
and the title, both built in the edit — there is nothing to capture in this act at all.

**S04 · Title** — Design element, not capture.

---

## 2. Act 1 — Character creation

Start a new game. `CharacterCreationDialog` opens as `RETROQUEST CHARACTER CREATION`.

| Shot | Do this |
|---|---|
| S05 | Let the dialog sit. Record 8s of it untouched so the edit has room for the push-in |
| S06 | Press REROLL three times, ~2s apart. Watch that the right-hand effect column changes each time — if a reroll produces near-identical numbers, roll again; the shot needs visible movement |
| S07 | Move to ACCEPT and pause 1s before clicking. Record the name prompt and type a short name |
| S08 | `IntroCinematicDialog` types at 900ms per line. **Do not press anything** — a key press dumps the whole crawl instantly. Record until `You are the Unbound.` is on screen, plus 2s |

The crawl is skipped entirely on a restart within the same JVM session. Fresh launch for each take.

---

## 3. Act 2 — The world above

Load **slot 01**.

- **S09** — Walk east 10–12 tiles at a steady rhythm. Don't stop, don't turn. The shot is the light
  radius travelling with the player and terrain resolving ahead of it.
- **S10** — Walk onto the `E` tile. `TownEntryAnimation` plays automatically; keep recording
  through it into the town interior. Then walk a short arc past the shop and inn.
- **S11** — Stand adjacent to an NPC (Chebyshev 1) and press **T**. In `DialogueOverlay`, arrow
  down the choice list slowly, then Enter on a branch. If you can find an NPC whose tree has a
  condition-gated choice, use that one — a greyed-out option sells "this actually branches" in a
  way nothing else in the shot does.
- **S12** — Enter a shop, scroll the stock, buy something. Then **I** for inventory, **U** to equip,
  and hold so `StatsPanel` visibly updates on the right.
- **S13** — Six separate captures, ~4s each, taken mid-action rather than at a menu:
  Blackjack, Archery, Jousting, Storm Rider, Deep Sea Fishing, Anvil Strike. These live in the
  casino / arena / sky / nature / war game overlays — reach them through the town or arena NPC that
  offers each. The edit uses 1.17s of each.
- **S14** — `DivineAudienceOverlay` fires on a first-time forward island portal. Capture it on any
  island transition; the god shown doesn't matter for the cut.

---

## 4. Act 3 — Combat

Load **slot 01** and walk on high-spawn-difficulty terrain (tall grass has a 2.0 tile modifier)
until an encounter rolls.

| Shot | Do this |
|---|---|
| S15 | Record from the step *before* the encounter so the flash and the slide-in are one continuous take |
| S16 | **A** to attack. Let the monster answer. Then hold — don't press anything for 3s, so the log is readable |
| S17 | **C** for the spellbook. Scroll the whole list once (the length is the point), then cast three: one fire, one lightning, then Turn Undead **on an undead monster** — check `monsterType` is `UNDEAD` or it won't do anything visible |
| S18 | Take damage until HP is red. Press **F** to raise the flee prompt, then decline it. Potion, then kill. Hold on the XP and loot award |
| S19 | A boss encounter via `BossFightCoordinator`. Note the no-escape state — that's the shot |

Turn Undead needs a genuinely undead target. Skeletons and wraiths qualify; check the monster's
type in the Monster Editor if you're unsure.

---

## 5. Act 4 — The descent ★

This is the section the video exists for. Budget more capture time here than everywhere else
combined.

**General direction for every shot in this act:**
- Keep camera easing **on** (V).
- Move slowly and deliberately. No hesitation, no backtracking, no bumping into walls.
- Record 15s per shot even though only 6–8s is used. The edit needs the choice.
- Let the frame breathe at the head and tail of every take — 2s of stillness at each end.

| Shot | Dungeon / mode | The move |
|---|---|---|
| S20 | Overworld `D` tile | Step onto it, let `DungeonEntryAnimation` run to completion |
| S21 | Lirandel · `TOP_DOWN` | Walk a corridor, turn a corner, enter a room. The torch radius eating into black **is** the shot |
| S22 | Pyralis/Zephyrion · `WIREFRAME` | Forward ×3, turn left, forward ×2, open a door. Pause 1s before each turn so the line-work resolves |
| S23 | Rootvault · `TEXTURED / BARK` | **Stand completely still for 5s first.** The spores drift on their own — the standing-still view loop repaints at ~33fps whether or not you move. Then walk forward 3 cells |
| S24 | Four dungeons · `TEXTURED` | Stand facing a wall flat-on, 2 cells back. Identical framing in all four. No movement — these are held frames |
| S25 | Archive of Tears · `RAYCAST` | **One continuous slow turn through 100–120°.** This is the only free-angle shot in the video. Turn slowly enough that it obviously is not snapping to 90°. Frame so a sconce and a bookshelf wall both pass through |
| S26 | Bellorak · `RAYCAST` + authored lights | Walk laterally past a pillar that sits between the camera and a fixture, so the cast shadow swings across the floor. Do this pass 3–4 times at different distances |
| S27 | Cradle of Shards, level 6+ | Slow forward drift down a hall where the mirror floor is reflecting a lit wall. Deeper levels have more reflectivity and more prismatic split — go as deep as you can |

If `RaycastDungeonRenderer.canRender()` returns false for a level you pick, it silently falls back
to the textured renderer and S25/S26/S27 lose their entire point. **Check the frame before you
record**: a raycast frame has free-angle walls and per-pixel light falloff; a textured frame has
four-direction bands. If you see bands, pick a different level.

---

## 6. Act 5 — The turn

S28–S29 are typography on black — built in the edit, not captured.

**S30** — Launch RetroForge with a map already loaded (it restores your last map from
`data/.forge_prefs` on open). Record the window in its resting state: menu bar, toolbar, tile
palette on the left, canvas filling the rest, minimap in the sidebar. 6s, no interaction.

---

## 7. Act 6 — RetroForge

Cursor **visible** for all of these. Move it deliberately — every one of these shots is really a
shot of a cursor doing something on purpose.

| Shot | Do this |
|---|---|
| S31 | Pick a water tile, pencil a coastline. Scroll the wheel to take the brush from 1 to 9 (the size indicator is bottom-right). Switch to FILL, click once inside the bay. Then Ctrl+Z, pause, Ctrl+Y |
| S32 | NPC_PLACER tool, left-click an empty tile → `NPCEditorDialog`. Fill a name and pick a sprite. Then EDITORS → Dialogue Tree Editor and open a tree that already has depth — a node with three choices, one gated by a condition, one firing an action. **Use an existing authored NPC**, not the one you just made; a real tree is far more convincing than an empty one |
| S33 | EDITORS → Quest Editor (show objectives and rewards) → Item Editor (stats, rarity) → Monster Editor (type, level, sprite). ~4s each; the edit takes 1.3s |
| S34 | EDITORS → Tile Editor. Create a tile: set the name, uncheck walkable, set blocksVision, set **lightRadius to something non-zero**, pick an editor colour, save. The palette refreshes behind the dialog — hold long enough to see that happen |
| S35 | EDITORS → Image Editor. Draw a recognisable 32×32 sprite — a torch or a chest reads fastest at this size. Use the mirror tool for the second half. Save. Then close and show the sprite already on the map (the editor mirrors saves into `target/classes/tiles`, so it appears without a rebuild) |
| S36 | SPAWN_DIFFICULTY tool. Paint a low value along a road and a high value in deep grass — the red overlay with numbers is the shot. Then DUNGEON_PLACER, click a tile, and name an authored dungeon in `DungeonPlacementDialog` |
| S37 | GAME → Playtest Now. Confirm. The game boots in the same JVM and lands in the map you were editing. Walk 4–5 tiles into it. **Record this in one unbroken take** — the whole point is that there is no build step between the two |

For S34's callback cut, no extra capture is needed — it reuses 0.5s of S26.

---

## 8. Act 7 — Close

**S38** — Twelve still frames. Nine of them are already in the material above; capture the missing
island establishing shots by loading each overworld and framing a characteristic piece of terrain.
One frame per island, in ladder order, then one per renderer, then the editor canvas.

**S39** — Design element, not capture.

---

## 9. Things that will bite you

- **Launch from the repo root.** From anywhere else the game creates a fresh empty `data/` tree and
  every shot in this list becomes impossible.
- **The intro crawl only plays once per JVM session.** Restart between takes.
- **`WashAshoreAnimation` only plays for a brand-new character.** Same.
- **Any key press skips the crawl.** Including the one you press to start your capture tool. Start
  recording first, then click into the game window.
- **Nearest-neighbour scaling, always.** Bilinear filtering on 32×32 sprites is the single fastest
  way to make this footage look wrong to the audience it is aimed at.
- **All 22 town files are reachable** — the eight orphaned ones were deleted from `data/towns/`, so
  anything in that folder is fair game. Note that `RetroForge_Operators_Guide.md` still tells you to
  open `data/towns/stonehaven.rfmap` as an example; that file no longer exists. Use
  `data/towns/moonhaven.rfmap` or `data/towns/forge_keep.rfmap` instead.
- **Check `logs/retroquest.log`** if a shot behaves oddly. The launcher uses `javaw.exe` and has no
  console, so that file is the only place errors surface.

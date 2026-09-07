# RetroQuest × RetroForge — Master Script

**Runtime 3:15 (195s) · 37 shots · 16:9 1920×1080 @60fps**

Column key — **TC** start timecode · **DUR** seconds · **PICTURE** what is on screen ·
**VO** narration · **TEXT** on-screen typography · **AUDIO** music + SFX.

VO lines marked **[T]** must survive as title cards if the voice track is dropped.
Lines quoted `like this` are verbatim from the shipped game and must not be reworded.

---

## ACT 0 — COLD OPEN · 0:00–0:08

### S01 · 0:00 · 5s · CRT BOOT
**PICTURE** Pure black. At 0:01 a single horizontal white line snaps open across the frame and
blooms vertically into a phosphor-green field, the way a CRT strikes. Six lines of boot text type
themselves, one character at a time, in `#00FF78` on `#06080E`.

**TEXT**
```
CANNONFORGE SYSTEMS
RETROQUEST v1.0.0
JAVA RUNTIME OK ...... 148 CLASSES
TILE REGISTRY ........ 192
AUDIO ................ SYNTHESIZED
READY.
```

**VO** — (none)

**AUDIO** Degauss thunk. 60Hz mains hum under everything. One mechanical key clack per character.

**NOTE** This shot now carries the entire cold open. The wash-ashore animation that used to
follow it is cut — it was the weakest thirteen seconds in the video and the only Act 0 material
the recorder could not produce. Boot straight to title: the boot text *is* the thesis, and the
video is in character creation by 0:08.



### S04 · 0:05 · 3s · TITLE
**PICTURE** Logo snaps on in one frame — no fade. Scanlines roll once, top to bottom.

**TEXT** `RETROQUEST` / sub: `THE UNBOUND — SHATTERED DREAMS OF AQUALON`

**AUDIO** Chiptune stinger: three-note minor arpeggio, square lead. Music proper starts on the
downbeat at 0:20.

---

## ACT 1 — ROLL YOUR HERO · 0:08–0:33

### S05 · 0:08 · 6s · 3d6
**PICTURE** `CharacterCreationDialog`. Slow 4% push in on the header. The six attribute rows sit
below, each showing its roll and what that roll buys.

**VO [T]** It starts the way it did in 1982. Six attributes. Three dice each.

**TEXT** Lower third: `STR · DEX · CON · INT · WIS · CHA`

**AUDIO** Music enters — sparse, bass and hat only.

### S06 · 0:14 · 7s · WHAT THE NUMBER BUYS
**PICTURE** REROLL pressed three times. Numbers tumble; the right-hand effect column re-derives
live — starting HP, damage soak, armor class, spell power, spell resist, shop discount, flee
chance. Punch in on the effect column for the third reroll.

**VO** Every roll tells you what it buys. Hit points, armor class, spell power — even your discount
at the shop.

**AUDIO** A dice-clatter SFX per reroll, pitched down. Lead synth enters.

### S07 · 0:21 · 5s · PERMANENT
**PICTURE** Cursor moves to ACCEPT. Freeze one beat on the warning line before the click.

**VO** Reroll as long as your nerve holds. Accept, and that's who you are.

**TEXT** The dialog's own amber warning, isolated and held: `3d6 per attribute · these rolls are
PERMANENT`. One-frame RGB split glitch on "PERMANENT."

**AUDIO** Music drops out for 400ms on the glitch, then returns.

### S08 · 0:26 · 7s · YOU ARE THE UNBOUND
**PICTURE** `IntroCinematicDialog` — the green crawl typing over black inside its cyan border.

**NOTE** Three seconds longer than it was, to carry the two lines the wash-ashore cut freed.
"And it knows your name" is the game's own writing and too good to lose with the animation.

**VO [T]** Seven dreams murdered the god that dreamed them. Something is very wrong with the
world — and it knows your name.

**TEXT** Held from the crawl: `You are the Unbound.` — then, on the last beat and unspoken:
`BORN UNDER THE ONE ALIGNMENT NONE OF THEM CAN TOUCH`

**AUDIO** Typewriter ticks. Music builds toward the Act 2 downbeat.

---

## ACT 2 — THE WORLD ABOVE · 0:33–1:05

### S09 · 0:33 · 5s · SEVEN ISLANDS
**PICTURE** Hard cut on the beat to the Lirandel overworld. Player walks east; the `VisionSystem`
light radius travels with them and terrain resolves out of the dark ahead.

**VO [T]** Seven islands, each sealed behind a key from the island before it.

**TEXT** The ladder types in across the lower third, one name per 300ms:
`LIRANDEL → PYRALIS → ZEPHYRION → SYLVANDAR → THALORAX → UMBRYN → BELLORAK`

**AUDIO** Full music. Footstep SFX quantized to the walk cycle.

### S10 · 0:38 · 5s · TOWNS
**PICTURE** Step onto an `E` tile → `TownEntryAnimation` plays → town interior resolves. Whip-pan
across the town: shopfront, inn, NPCs standing at their posts.

**VO** Twenty-two towns, and a hundred and fifty-four people standing in them.

**TEXT** Lower third: `22 TOWNS · 154 NPCs`

### S11 · 0:43 · 5s · CONVERSATION
**PICTURE** Talk to an NPC → `DialogueOverlay`. The highlight moves down the choice list; pick a
branch; the reply changes. Show one gated choice greyed out.

**VO** The conversations branch. The quests remember. There are ninety-one of them.

**TEXT** Lower third: `91 QUESTS · BRANCHING DIALOGUE TREES`

### S12 · 0:48 · 5s · GOODS
**PICTURE** `ShopOverlay` — scroll the stock list, buy something. Cut to `InventoryOverlay`, equip
it, and let `StatsPanel` visibly update.

**VO** A hundred and forty items to buy, and to fail to afford.

**TEXT** Lower third: `140 ITEMS`

### S13 · 0:53 · 7s · TWENTY-TWO MINI-GAMES
**PICTURE** Six-cut montage, 1.17s each, cut on the beat:
Blackjack → Archery → Jousting → Storm Rider → Deep Sea Fishing → Anvil Strike.
Each cut lands mid-action, never on a menu.

**VO** And when you want to stop being a hero for ten minutes — casinos, arenas, sky races.
Twenty-two of them.

**TEXT** Each game's name flashes bottom-left for 500ms on its own cut.

**AUDIO** Music doubles to double-time under the montage.

### S14 · 1:00 · 5s · THE GODS
**PICTURE** `DivineAudienceOverlay` — a god addresses the player. Hold on the portrait; the favor
readout ticks.

**VO [T]** Seven gods are watching. All of them want you.

**TEXT** Held on the portrait, unspoken: `ONLY ONE OF YOU DECIDES HOW THIS ENDS`

**AUDIO** Music thins to a single sustained pad — setting up the Act 3 hit.

---

## ACT 3 — COMBAT · 1:05–1:31

### S15 · 1:05 · 4s · ENCOUNTER
**PICTURE** One more step on the overworld → encounter flash → `CombatOverlay` slides in with its
ease-out cubic, monster sprite at 32×32 scaled up crisp (nearest-neighbour, never smoothed).

**VO** Combat is turn-based. It doesn't care how long you've been playing.

**AUDIO** Encounter sting. Music restarts hard on the slide-in.

### S16 · 1:09 · 6s · THE EXCHANGE
**PICTURE** Attack. Damage number floats. The monster hits back. The combat log scrolls — hold
long enough that a viewer can actually read two lines of it.

**VO** Roll to hit. Roll damage. Read the log and decide whether you're still winning.

### S17 · 1:15 · 7s · THIRTY-SEVEN SPELLS
**PICTURE** `SpellbookOverlay` opens — scroll the list so its length registers. Cast three in a
row, letting `SpellEffectRenderer` finish each: a fire effect, a lightning effect, and Turn Undead
on a skeleton, which flees.

**VO [T]** Thirty-seven spells, each with its own animation. Turn Undead actually knows what's
undead.

**TEXT** Lower third: `37 SPELLS · 78 MONSTERS · LEVELS 1–50`

### S18 · 1:22 · 5s · THE EDGE
**PICTURE** Player HP bar into the red, the flee prompt appearing and being declined, a potion, the
killing blow, XP award and a loot drop from `LootGenerator`.

**VO** Seventy-eight monsters, and a level range that runs to fifty.

### S19 · 1:27 · 4s · BOSS
**PICTURE** A boss encounter via `BossFightCoordinator` — bigger sprite, the no-escape state.

**VO [T]** Some of them don't let you leave.

**AUDIO** Music cuts dead on the last word. Two frames of black.

---

## ACT 4 — THE DESCENT · 1:31–2:23 ★ HERO SECTION

> **Direction for the whole act.** Everything above this point was cut fast. Act 4 slows down.
> Shots run 6–8 seconds, the moves are longer, and the music is a single evolving drone until
> 2:18. The point being made is *visual escalation*, and the viewer needs time on each rung of the
> ladder to register that the renderer itself changed. Do not let a note shorten these shots.
>
> Every lower third in this act uses the same two-line form — island and dungeon on line one,
> renderer on line two, in `#00C8FF`.

### S20 · 1:31 · 4s · DOWN
**PICTURE** The player stands on a `D` tile on the overworld. `DungeonEntryAnimation` — the
stairwell opens and swallows the frame.

**VO [T]** And then you go down.

**AUDIO** Everything drops to a single low drone. No percussion for the next 47 seconds.

### S21 · 1:35 · 7s · ISLAND 1 — THE GRID
**PICTURE** Lirandel, `TOP_DOWN`. The torch-lit overhead dungeon grid. Walk a corridor so
the torch radius eats into the dark; turn a corner; a room resolves.

**VO** Island one gives you the grid. Overhead, torch-lit, one square at a time. This is 1982.

**TEXT** `ISLAND 1 · LIRANDEL` / `TOP-DOWN GRID`

### S22 · 1:42 · 7s · ISLANDS 2–3 — THE WIREFRAME
**PICTURE** `WireframeDungeonRenderer`. First-person vector corridor. Walk forward three cells,
turn 90° left, walk again, open a door. Let the line-work read.

**VO** Island two takes the ceiling off and stands you up. Vector lines, four directions. This one
is 1984.

**TEXT** `ISLANDS 2–3 · PYRALIS / ZEPHYRION` / `WIREFRAME`

### S23 · 1:49 · 8s · ISLAND 4 — SURFACE
**PICTURE** Rootvault, `TexturedDungeonRenderer` with `WallStyle.BARK`. **Stand still for the first
three seconds** — spores drift, and the fact that the frame is alive while the player is not is
the entire point of the shot. Then move forward.

**VO** Island four puts a surface on it. Bark and root — and the spores keep drifting whether you
move or not.

**TEXT** `ISLAND 4 · ROOTVAULT` / `TEXTURED — BARK`

### S24 · 1:57 · 7s · FIVE KINDS OF STONE
**PICTURE** Four cuts, 1.75s each, all `TEXTURED`, all from a standing start facing a wall:
Pressure Temple (`BLOCK`) → Boneyard Trench (`COBBLE`) → Leviathan Eye (`SLAB`) → Forgotten City
(`CYCLOPEAN`). Match the framing exactly across all four so the only thing that changes is the
masonry.

**VO** Five kinds of stone, laid out in world depth — so the courses foreshorten the way real
masonry does.

**TEXT** The style name replaces itself on each cut: `BLOCK` / `COBBLE` / `SLAB` / `CYCLOPEAN`

### S25 · 2:04 · 8s · ISLAND 6 — THE RAYCASTER
**PICTURE** Archive of Tears, `RaycastDungeonRenderer`. **The shot is one continuous slow turn
through more than 90°** — this is the only place in the video where the view is not locked to four
directions, and it has to be unmistakable. Sconce lights sweep across the walls; the bookshelf
tiles are visibly bookshelves.

**VO [T]** Island six throws out the four directions completely. One ray per screen column — so you
can face any angle at all.

**TEXT** `ISLAND 6 · ARCHIVE OF TEARS` / `RAYCAST — PER-PIXEL LIT`

### S26 · 2:12 · 6s · ISLAND 7 — LIGHT THE MAP OWNS
**PICTURE** A Bellorak dungeon with authored fixtures — lava channel, ember grate, wall torch.
Walk past a pillar so its cast shadow swings across the floor. **The shadow move is the shot.**

**VO** Island seven lets the map carry its own lights. Every torch you place casts a real shadow.

**TEXT** `ISLAND 7 · BELLORAK` / `MAP-AUTHORED LIGHTING + SHADOWS`

### S27 · 2:18 · 5s · THE CRADLE
**PICTURE** Cradle of Shards, a deep level. The mirror floor reflecting the walls, the prismatic
split at its most escalated, self-glowing crystal. Slow forward drift.

**VO [T]** And at the bottom, the floor starts reflecting back.

**TEXT** `THE CRADLE OF SHARDS` / `8 LEVELS · MIRRORED · ESCALATING`

**AUDIO** The drone swells for four seconds — and then cuts to **absolute silence** on the last
frame of the shot.

---

## ACT 5 — THE TURN · 2:23–2:35

> The most important twelve seconds in the video. Two of them have nothing on screen at all.

### S28 · 2:23 · 4s · BLACK
**PICTURE** Full black. Nothing for 1.2s. Then one line types in phosphor green, centered, one
character per 60ms.

**TEXT** `THAT'S THE GAME.`

**VO** — (none)

**AUDIO** **Silence.** No room tone, no hum. Only the key clacks of the typing.

### S29 · 2:27 · 4s · THE LINE
**PICTURE** A second line types beneath the first, in cyan.

**TEXT** `NOW BUILD YOUR OWN.`

**VO [T]** Everything you just watched is a file you can open.

**AUDIO** The 60Hz hum fades back in under the second line.

### S30 · 2:31 · 4s · RETROFORGE BOOTS
**PICTURE** The RetroForge window snaps up behind the text — menu bar, toolbar, tile palette, map
canvas — and the text burns off.

**TEXT** `RETROFORGE` locks up over the UI, then clears.

**AUDIO** Music restarts: the same theme, faster, brighter, major key.

---

## ACT 6 — RETROFORGE · 2:35–3:05

### S31 · 2:35 · 5s · PAINT A WORLD
**PICTURE** `MapCanvas`. The pencil draws a coastline; the mouse wheel scales the brush 1→9
visibly; the fill tool floods an entire bay in one click. Then Ctrl+Z undoes it and Ctrl+Y puts it
back.

**VO [T]** RetroForge is the editor the entire game was built in. It ships with it.

**TEXT** Lower third: `PENCIL · FILL · SELECT · NPC · TOWN · DUNGEON · SPAWN`

### S32 · 2:40 · 4s · PEOPLE WHO TALK BACK
**PICTURE** The NPC placer drops a character. `NPCEditorDialog` opens; cut to `DialogueTreeEditor`
showing a real branching tree with nodes, choices, conditions and actions wired up.

**VO** Write them a conversation that branches.

**TEXT** Lower third: `DIALOGUE TREE EDITOR` / `NODES · CHOICES · CONDITIONS · ACTIONS`

### S33 · 2:44 · 4s · QUEST, ITEM, MONSTER
**PICTURE** Three dialogs in sequence, ~1.3s each: `QuestEditorDialog` (objectives and rewards),
`ItemEditorDialog` (stats and rarity), `MonsterEditorDialog` (type, level, sprite).

**VO** Write the quest. Forge the sword. Stat the thing carrying it.

**TEXT** `data/quests.json · data/items.json · data/monsters.json` in small mono, bottom-right.

### S34 · 2:48 · 5s · TILES ARE RULES
**PICTURE** `TileEditor` — create a tile, toggle walkable, toggle blocksVision, set a light radius,
attach an onStep effect. Save; the palette refreshes.

**VO [T]** Make a tile. Give it a light radius — and it lights the dungeon.

**PICTURE (callback)** Hard cut for 0.5s to the lit dungeon frame from S26, then straight back to
the editor. **This is the single most important edit in Act 6** — it closes the loop between the
editor and the hero section, and it is what turns "an editor exists" into "you can make the thing
you just watched."

### S35 · 2:53 · 4s · DRAW IT YOURSELF
**PICTURE** `ImageEditor` — a 32×32 sprite drawn pixel by pixel on the zoomed canvas, the mirror
tool completing the other half, save. Cut to the map: the new sprite is already on it.

**VO** Draw the sprite yourself. Thirty-two pixels square.

**TEXT** Lower third: `IMAGE EDITOR · 32×32` / `SAVED = ALREADY IN THE GAME`

### S36 · 2:57 · 3s · DANGER, BY HAND
**PICTURE** The spawn-difficulty tool painting the red 0–99 overlay across a region — low near the
road, high in the deep grass. Then `DungeonPlacementDialog` naming an authored dungeon on a `D`
tile.

**VO** Paint how dangerous the grass is.

**TEXT** Lower third: `SPAWN DIFFICULTY 0–99` / `DUNGEON PLACER`

### S37 · 3:00 · 5s · PLAYTEST
**PICTURE** The cursor moves to the ▶ PLAYTEST button. Click. The game boots **in the same session**
and the player walks into the town that was being edited forty seconds ago.

**VO [T]** Then hit playtest, and walk into it.

**AUDIO** A single decisive click, then the game's boot chime. Music hits its final progression.

---

## ACT 7 — CLOSE · 3:05–3:15

### S38 · 3:05 · 5s · EVERYTHING
**PICTURE** Twelve frames, accelerating from 500ms to 180ms: the seven islands in ladder order,
then top-down / wireframe / textured / raycast, then the editor canvas. Ends on a hard freeze.

**VO [T]** Seven islands. Forty authored dungeon levels. Nine endings. And the editor for all of it.

### S39 · 3:10 · 5s · LOCKUP
**PICTURE** Final lockup on black. Then the CRT powers off — the image collapses to a horizontal
line, then to a single dot, then gone.

**TEXT**
```
RETROQUEST
+ RETROFORGE

Java · Swing · 32×32 sprites · zero audio files
```

**VO** RetroQuest. And RetroForge. Go make something.

**AUDIO** Final chord, then the CRT power-off whine and click. One second of black at the end.

---

## Appendix A — Every number, verified

All figures below were counted from shipped data in this repository on 2026-09-06. If the build
changes, recount before re-cutting — an inflated number in a trailer is the fastest way to lose
this particular audience.

| Claim in VO / on-screen text | Value | Source |
|---|---|---|
| Islands | 7 | `data/overworlds/*.rfmap` |
| Towns | 22 | `data/towns/*.rfmap`, and 22 `townEntrances` across all maps |
| NPCs | 154 | sum of `npcs[]` across all 69 maps |
| Quests | 91 | `data/quests.json` |
| Items | 140 | `data/items.json` |
| Monsters | 78 | `data/monsters.json` |
| Tiles | 192 | `data/tiles.json` |
| Spells | 37 | `new Spell(` in `model/Player.java` |
| Authored dungeon levels | 40 | `data/dungeons/*.rfmap` |
| Named authored dungeons | 11 | distinct prefixes in `data/dungeons/` |
| Mini-games | 22 | `overlay/*Game.java`, excluding the `MiniGame` base class |
| Full-screen animations | 15 | `animation/` less `AnimationGuard` and `AnimationSpeed` |
| Endings | 9 | `docs/STORY_BIBLE.md` §6 — seven Champion endings, True Unbound, New Serpent |
| Java source files | 148 | `find src/main/java -name '*.java'` |
| Max character / monster level | 50 | `MonsterFactory` clamp; difficulty→level mapping |
| Editor tools | 7 | the `Tool` enum |
| Sub-editors | 7 | the RetroForge EDITORS menu |

**Do not claim:** a sound editor (it was deliberately removed from RetroForge), a teleporter
placement tool (it does not exist), or any number of "hours of gameplay" — nothing in the repo
supports one.

---

## Appendix B — Cutdowns

### 0:59 vertical (9:16 — Shorts / TikTok / Reels)
Center-crop to 1080×1920. Re-set every lower third to two lines and move them to the upper third
so platform UI doesn't eat them. Open on the hook, not the boot sequence.

| New TC | From | Length | Content |
|---|---|---|---|
| 0:00 | S01 + S04 | 4s | Boot sequence into the title — the hook is the look |
| 0:05 | S05–S07 | 7s | 3d6, permanent |
| 0:12 | S09, S13 | 6s | Overworld plus two mini-game cuts |
| 0:18 | S15–S17 | 8s | Combat and one spell |
| 0:26 | S21–S27 | 22s | **The full renderer ladder, ~3s a rung** |
| 0:48 | S28–S29 | 4s | The turn |
| 0:52 | S31, S34, S37 | 5s | Paint, tile, playtest |
| 0:57 | S39 | 2s | Lockup |

### 0:30 pre-roll
S04 (2s) → S05 (3s) → S13 (3s) → S17 (4s) → the renderer ladder S21–S25 (11s) → S29 (2s) →
S37 (3s) → S39 (2s). Drop all VO except the four **[T]** lines that fall inside those shots.

# Launch Copy & Delivery — RetroQuest × RetroForge

Everything that ships *around* the video: thumbnail, platform metadata, captions, delivery spec and
the QC pass before it goes out.

---

## 1. Thumbnail

The video's whole argument is that the dungeon rendering escalates. A thumbnail can make that
argument in one frame, and no other game's thumbnail looks like this — which is the entire point of
picking it.

### Concept A — The Ladder *(recommended)*

Four vertical slices of the same corridor, edge to edge, no gaps:

```
┌─────────┬─────────┬─────────┬─────────┐
│ top-down│wireframe│ textured│ raycast │
│  grid   │ vectors │  stone  │   lit   │
└─────────┴─────────┴─────────┴─────────┘
        ONE GAME.  FOUR RENDERERS.
```

- Real frames from S21, S22, S24, S25 — same corridor position in each, so the eye reads it as one
  place changing rather than four screenshots.
- Caption in `Press Start 2P`, phosphor green, bottom third, two lines max.
- Works at 168×94px (YouTube's smallest surface), which almost no game thumbnail does. The four
  vertical bands survive any amount of scaling.

### Concept B — The Turn

Left half: the Cradle of Shards mirror frame. Right half: the RetroForge canvas mid-edit, cursor
visible. A single hard vertical seam, no gradient. Caption: `AND YOU CAN EDIT ALL OF IT.`

Stronger for an audience that already knows the game exists. Weaker cold, because it needs the
viewer to already care about the left half.

### Concept C — 3d6

Extreme close-up of the character creation dialog's amber warning line — `these rolls are
PERMANENT` — with the six attribute rows soft behind it.

The most distinctive thumbnail of the three and the one most likely to be scrolled past. Hold it
for a follow-up post rather than the launch.

**Rules for all three:** no face, no arrow, no circled element, no open-mouth reaction, no text
outside the caption. This audience reads those as a signal that the game isn't confident.

---

## 2. YouTube

### Title

> **RetroQuest — a 1982 CRPG built in 2026, and the editor it was made in**

62 characters. Names the era, the hook and the second half. Alternatives if the client wants
the mechanic forward:

- `RetroQuest — the dungeon renderer changes as you go deeper` (57)
- `I built an 80s-style CRPG where every island renders differently` (63)

### Description

The first two lines are what YouTube shows in search and suggested, so they lead with the hook
rather than a definition of the genre. Everything below the fold is for the person who already
clicked.

```
RetroQuest is a 1982-style CRPG where the dungeons change how they are RENDERED as you
go deeper — an overhead torch-lit grid on the first island, a per-pixel-lit raycaster on
the last. Same game. Seven islands. And the editor it was built in ships with it.

It is a tile-based fantasy CRPG for the desktop, written in Java and Swing, and an explicit
tribute to the CRPGs of 1982-85. Seven islands, each one sealed behind a key you
earn on the island before it. Turn-based combat, 37 spells, branching NPC dialogue,
91 quests, 22 mini-games, and nine endings.

THE DESCENT (1:31)
The part worth watching. The dungeon view escalates with the island ladder:

  Island 1     Overhead grid, torch-lit, one square at a time
  Islands 2-3  First-person vector wireframe, four directions, clean line work
  Islands 4-5  Texture-mapped, with five masonry styles laid out in world depth so the
               courses foreshorten the way real stone does when you walk at it
  Island 6     A raycaster. One ray per screen column, so you can face any angle at all,
               and the light lands per pixel
  Island 7     The map carries its own lights — any tile with a light radius IS a light,
               which means lighting is authorable in the editor. Real cast shadows,
               precomputed once per level
  The Cradle   A mirrored crystal dungeon whose reflectivity and prismatic split ramp
               with depth across eight levels

RETROFORGE (2:35)
Every one of those worlds is a file you can open. The editor the whole game was built in
ships with it: paint maps tile by tile, place towns and dungeons, write branching dialogue
trees with conditions and actions, forge items, stat monsters, author tiles, draw your own
32x32 sprites, paint per-tile encounter difficulty — then hit Playtest and walk into it.

All artwork is 32x32 sprites. All audio is synthesized at runtime: there is not a single
sound file anywhere in the project. The chiptune score in this trailer is generated from
the same four waveforms the game uses — square, triangle, saw and noise.

Every gameplay frame here was rendered by the game itself rather than screen-captured.

CHAPTERS
0:00  Cold open
0:08  Roll your hero - 3d6, and they're permanent
0:33  The world above - islands, towns, 154 NPCs
1:05  Combat - 37 spells, 78 monsters
1:31  The descent - four renderers, seven islands
2:23  There's more
2:35  RetroForge - the editor it was built in
3:05  Everything

BY THE NUMBERS
7 islands · 22 towns · 154 NPCs · 91 quests · 140 items · 78 monsters · 37 spells
192 tiles · 40 authored dungeon levels · 22 mini-games · 9 endings · 148 Java files
```

**Do not add a store link unless there is a real one.** An unbacked "wishlist now" at the end of
an otherwise honest video is the single thing most likely to lose this audience.

Two claims in here are load-bearing and both are true, so keep them: *no sound files anywhere in
the project* (`SoundManager` synthesizes everything at runtime), and *every gameplay frame was
rendered by the game rather than captured* (`core/RetroRecorder` paints `GamePanel` into a
`BufferedImage` offscreen). The second one is unusual enough that a technical audience will check
it — which is fine, because it holds.

### Tags

`crpg`, `retro rpg`, `80s crpg`, `dungeon crawler`, `raycaster`,
`java game`, `indie rpg`, `game editor`, `level editor`, `tile based rpg`, `first person dungeon`,
`80s rpg`, `gamedev`, `swing`, `procedural audio`, `pixel art`, `world builder`, `retro gaming`

---

## 3. Social cutdowns

### Shorts / TikTok / Reels — 0:59 vertical
Caption, ~180 characters:

> Same dungeon corridor. Four different renderers. That's not four games — it's one, and which
> renderer you get depends on which island you're standing on. 🗡️

Open on the renderer ladder, not the title. On these platforms the first 1.5 seconds is the whole
decision.

### Bluesky / Mastodon

> RetroQuest is a 1982-shaped CRPG I built in Java. Seven islands, and the dungeons change how
> they're *rendered* as you go: overhead grid → wireframe → textured → raycast with real cast
> shadows.
>
> The editor it was built in ships with it.

### Reddit — r/roguelikes, r/gamedev, r/IndieDev

Lead with the technical hook, not the trailer. These communities respond to the *how*:

> **The dungeon renderer changes as you progress through the game**
>
> Island 1 is a torch-lit overhead grid. Islands 2–3 are vector wireframe. Islands 4–5 are
> texture-mapped with masonry laid out in world depth so courses foreshorten properly. Islands 6–7
> are a raycaster with per-pixel lighting, where any tile with a `lightRadius` in the tile registry
> *is* a light — which means lighting is authorable in the map editor. Shadows are precomputed per
> level into a small visibility field and sampled while drawing.
>
> Trailer, and the editor it was all built in: [link]

### itch.io / store short description

> A 1982-style tile CRPG and the world editor it was built in. Seven islands, 40 authored dungeon
> levels, and a dungeon view that escalates from an overhead grid to a per-pixel-lit raycaster as
> you climb. Every map, tile, item, quest, monster and sprite is editable in RetroForge, which ships
> with the game.

---

## 4. Delivery

### Masters

| File | Spec |
|---|---|
| `retroquest_hype_1080p.mp4` | 1920×1080, h264, CRF 16, 60fps, AAC 320kbps |
| `retroquest_hype_1080p_master.mov` | ProRes 422 HQ, 48kHz 24-bit stereo — the archive copy |
| `retroquest_hype_vertical_1080x1920.mp4` | The 0:59 cutdown |
| `retroquest_hype_30s.mp4` | The pre-roll cut |
| `retroquest_hype_notext.mov` | Full cut, no titles or lower thirds — for localisation |
| `retroquest_hype.srt` | Burned-in-equivalent captions, all 33 VO lines |
| `thumbnail_ladder_1280x720.png` | Concept A |

### Bin structure for the edit

```
01_FOOTAGE/
   ACT0_COLDOPEN/     (nothing — the act is built in the edit)
   ACT1_CHARGEN/
   ACT2_WORLD/
   ACT3_COMBAT/
   ACT4_DESCENT/      S21_topdown_… S22_wire_… S23_bark_… S24_block_… S25_raycast_…
   ACT5_TURN/
   ACT6_FORGE/
   ACT7_CLOSE/
02_VO/                VO_S05.wav … VO_S39.wav, VO_ROOMTONE.wav
03_MUSIC/             theme_A_statement, theme_B_descent, theme_C_forge
04_SFX/               game_capture/ (synthesized, lifted from gameplay) + library/
05_GFX/               lower_thirds/, titles/, lockup/
```

Name every clip with its shot ID first. The Act 4 bin will hold 40+ takes and the ID is the only
thing that keeps them straight.

---

## 5. QC pass — before it goes out

Run this list against the final master. Every item on it is a real way this specific video can go
wrong.

**Accuracy**
- [ ] Every number on screen and in VO matches Appendix A of `01-SCRIPT.md`.
- [ ] No claim of a sound editor, a teleporter tool, or "hours of gameplay."
- [ ] No store or wishlist call-to-action unless the client supplied a real URL.
- [ ] The four renderer names in Act 4 match what is actually on screen in each shot — check S25,
      S26 and S27 are genuinely raycast frames and not a textured fallback.
- [ ] **The S17 lower third says 37 spells; the frame behind it says "34 spells available."** The
      combat book filters to combat-usable spells. Both numbers are true, but they are visible at
      the same time — either move the lower third off that frame, or say "37" over a different one.

**Picture**
- [ ] No bilinear smoothing anywhere. Pause on any 32×32 sprite and count the pixels.
- [ ] The lower third sits at the same x and y in all 37 shots.
- [ ] The RGB split glitch appears exactly three times.
- [ ] Amber appears exactly twice — `PERMANENT` at 0:21 and the `+` in the lockup.
- [ ] Act 4 has no colour grade applied.
- [ ] S34's half-second callback to the lit dungeon is present and lands on "lights the dungeon."

**Sound**
- [ ] 2:23–2:25 is *actually* silent — no room tone, no music tail, no reverb bleed.
- [ ] No percussion between 1:31 and 2:23.
- [ ] Master is −14 LUFS integrated, −1 dBTP true peak.
- [ ] VO is intelligible at phone-speaker volume with music at full.

**Text**
- [ ] Captions match the recorded VO, not the script — read what was actually said.
- [ ] Every island name is spelled correctly in the ladder crawl at 0:33.
- [ ] The title card reads `THE UNBOUND — SHATTERED DREAMS OF AQUALON`, em dash, no hyphen.

**Last check**
- [ ] Watch it once at 168px wide with the sound off. If the renderer ladder still reads as four
      different things, the video works. If it doesn't, the Act 4 shots need more contrast between
      rungs — that is the only note that matters.

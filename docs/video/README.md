# RetroQuest × RetroForge — Hype Video Handoff

**Package version:** 1.0 — September 2026
**Runtime:** 3:15 (195s)
**Aspect:** 16:9, 1920×1080 @ 60fps (see Style Guide for the vertical cutdown)
**Audience:** retro-CRPG players on YouTube / itch.io / Steam
**Handoff target:** Claude Design, for final edit and motion pass

---

## What's in here

| File | What it is | Who uses it |
|---|---|---|
| `01-SCRIPT.md` | The master script. 37 shots, timecoded, with VO, on-screen text, footage direction, and audio cues. **This is the spine — everything else serves it.** | Editor + VO |
| `02-SHOT-LIST.md` | Capture instructions. For every shot: which build to launch, the exact map/save state, and the literal keystrokes to record. | Whoever records the footage |
| `03-STYLE-GUIDE.md` | Palette, type, transitions, lower-third spec, motion grammar, and the audio bed. All colors are pulled from the game's own source, not invented. | Motion / design |
| `04-VO-RECORD-SCRIPT.md` | The clean read for the booth. 35 lines, per-line direction, a pronunciation guide for every invented name, and a words-per-second table proving each line fits its shot. | VO talent + director |
| `05-LAUNCH-COPY.md` | Thumbnail concepts, YouTube title/description/chapters/tags, social cutdown captions, delivery specs, bin structure, and the QC checklist. | Marketing + producer |
| `06-RECORDER.md` | **How the footage is actually made.** The game renders its own frames offscreen — no screen capture. Covers usage, the two-clocks problem, the fixed-size UI trap, and exactly what is and is not recorded. | Editor + engineering |
| `animatic.html` | A **playable animatic** of the whole cut. Press Space. Shows pacing, act structure, title cards and VO timing in real time, with a scrubber and a clickable shot table. | Everyone — watch this first |

Published animatic: see the Artifact link in the handoff message.

**Recorded footage lives in `out/`** — `out/<shot-id>/<shot-id>.mp4` for each shot, and assembled
sequences in `out/_release/`. 141 seconds are recorded — every act except the pure-typography ones (0, 5, 7), which are built in the edit rather than captured. Run
`sh tools/record.sh --list` to see the shots, and read `06-RECORDER.md` before adding one.

---

## The one-paragraph pitch

RetroQuest is a 1982-shaped CRPG built in 2026 — 148 Java files, 32×32 sprites, and audio
synthesized at runtime with no sound files anywhere. It is an explicit tribute to the CRPGs of
the early 1980s — the overhead torch-lit grid, the first-person wireframe, and the
tile overworld with towns and the branching people in them. The hook that makes it a *video*
rather than a screenshot: **the dungeons change how they are rendered as you climb the island
ladder.** Island 1 is a torch-lit grid. Islands 2–3 are vector wireframe. Islands 4–5 are
texture-mapped. Islands 6–7 are a per-pixel-lit raycaster with map-authored lights and real cast
shadows. That escalation is the middle of the video and it is 52 seconds long on purpose.

Then the turn: every one of those worlds is a `.rfmap` file, and **RetroForge** — the editor the
whole game was authored in — ships with it.

---

## Structure at a glance

```
0:00  ACT 0   COLD OPEN           8s   CRT boot → title. No voice, no gameplay.
0:08  ACT 1   ROLL YOUR HERO     25s   3d6, permanent, intro crawl
0:33  ACT 2   THE WORLD ABOVE    32s   islands, towns, dialogue, shops, mini-games, gods
1:05  ACT 3   COMBAT             26s   turn-based, 37 spells, 78 monsters, a boss
1:31  ACT 4   THE DESCENT        52s  ★ the renderer ladder — the reason this video exists
2:23  ACT 5   THE TURN           12s   silence. "That's the game. Now build your own."
2:35  ACT 6   RETROFORGE         30s   every editor, ending on PLAYTEST
3:05  ACT 7   CLOSE              10s   montage → lockup → CRT power-off
```

Act 4 is 27% of the runtime. If the cut has to lose time, take it from Act 2, never Act 4.

---

## Ground rules for the edit

1. **Never fake a frame.** Every shot in `02-SHOT-LIST.md` is capturable from the real build.
   No mockups, no After Effects recreations of gameplay. The game already looks like this.
2. **The CRT treatment is a grade, not a filter stack.** Scanlines + slight bloom + barrel warp
   at ~2%. If it reads as "Instagram VHS preset," it has gone too far. See Style Guide §4.
3. **Cut on the beat, not on the action.** The music is a 140 BPM chiptune; every cut in the
   montages lands on a beat (428ms grid). Act 4's cuts are deliberately *slower* than the beat —
   that's the tonal shift.
4. **The turn at 2:23 is the whole trick.** Four seconds of black and total silence. Resist the
   note that says it's too long. It is not too long.
5. **All numbers on screen are verified against shipped data** (see `01-SCRIPT.md` appendix).
   If a number changes in the build, change it here.

---

## Open decisions for the client

- **VO voice:** written for a dry, unhurried delivery — closer to a documentary narrator than a
  trailer announcer. No "IN A WORLD." If the VO is dropped, every line doubles as a title card;
  the script marks which lines survive as text with a **[T]**.
  Every line has been measured against its shot — see the table at the end of `04-VO-RECORD-SCRIPT.md`.
  Six lines were trimmed and four Act 6 shots re-timed to make them fit; the trimmed content moved
  to on-screen text rather than being cut.
- **Music:** spec'd as an original 140 BPM chiptune in three states (see Style Guide §5).
  Placeholder direction only — no track is licensed.
- **Vertical cutdown:** a 0:59 version for Shorts/TikTok is mapped in `01-SCRIPT.md` §Cutdowns.

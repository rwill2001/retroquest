# Style Guide — RetroQuest × RetroForge Hype Video

Every colour below is lifted from the game's own source. Nothing here was invented to look retro —
the game already is, and the video's job is to not fight it.

---

## 1. Palette

### Primary — from `overlay/OverlayTheme.java`

| Token | Hex | Java source | Use |
|---|---|---|---|
| `GROUND` | `#06080E` | `BG` | The only background. Never pure black except in Act 5 |
| `PANEL` | `#0A0E16` | `PANEL_BG` | Lower-third fills, caption plates |
| `RULE` | `#23344E` | `BORDER_COL` | Hairlines, dividers, plate borders |
| `PHOSPHOR` | `#00FF78` | `PHOSPHOR` | The game's voice. Boot text, `THAT'S THE GAME.`, all RetroQuest-side titles |
| `CYAN` | `#00C8FF` | `CYAN_ACC` | The editor's voice. All Act 4 renderer labels and all Act 6 titles |
| `AMBER` | `#FFC832` | `AMBER` | Warnings and emphasis only. Act 1's `PERMANENT`, nothing else |
| `TEXT` | `#C8DEFF` | `TEXT_BRIGHT` | Body and subtitle text |
| `DIM` | `#506987` | `TEXT_DIM` | Secondary lines, filenames, credits |
| `DANGER` | `#DC3737` | `DANGER` | Combat only — the HP bar in S18. Never as decoration |

### The two-voice rule

This is the structural idea the whole design rests on, and it is worth protecting:

- **`PHOSPHOR` green speaks for the game.** Acts 0–5.
- **`CYAN` speaks for the editor.** Acts 4 (renderer labels) and 6.

Cyan appearing in Act 4 is deliberate foreshadowing — those labels name a *technical* property of
what you're looking at, and by Act 6 the viewer has learned that cyan means "someone built this."
The reveal at 2:27 (`NOW BUILD YOUR OWN.` in cyan under `THAT'S THE GAME.` in green) is the payoff.
Don't let a colour note collapse the two into one accent.

`AMBER` appears exactly twice in the whole video: on `PERMANENT` in S07, and on the final `+` in
the S39 lockup. Its scarcity is the point.

### Act grades

Each act gets a very slight grade lift toward one hue. Keep them subtle — 4–8% at most. They should
be felt across a cut, not seen within a shot.

| Act | Lift toward |
|---|---|
| 0 Cold open | Cold blue, crushed blacks |
| 1 Character creation | Neutral, slightly warm — this is the only "friendly" act |
| 2 The world above | Warm, saturated. The one bright section |
| 3 Combat | Contrast up, saturation down, blacks crushed |
| 4 The descent | **Ungraded.** The renderers already have their own palettes and grading them muddies exactly what the act is showing |
| 5 The turn | Pure black. No grade because there is no image |
| 6 RetroForge | Cool, clean, higher key than anything else. This is a workshop, not a dungeon |
| 7 Close | Match whatever each montage frame came from |

---

## 2. Typography

Three faces, three jobs. The game itself renders everything in `Monospaced` via `core/Fonts.java` —
the video honours that but uses better mono than the JVM default.

| Role | Face | Where | Notes |
|---|---|---|---|
| **Display** | `Press Start 2P` | The `RETROQUEST` / `RETROFORGE` logos, the two Act 5 lines, `THAT'S THE GAME.` | Genuinely 8-bit. Illegible in quantity — never more than five words at a time |
| **Terminal** | `VT323` | Boot text, lower thirds, renderer labels, timecodes, file paths | Reads as a live CRT. Set at ≥28px or the phosphor bloom eats it |
| **Body** | `IBM Plex Mono` | Subtitles, credits, the closing spec line | Actually readable at 20px. This is where anything longer than a phrase goes |

### Scale (at 1920×1080)

```
Logo lockup      96px  Press Start 2P     letter-spacing 0.04em
Act 5 lines      54px  Press Start 2P     letter-spacing 0.06em
Lower third L1   34px  VT323 uppercase    letter-spacing 0.12em
Lower third L2   26px  VT323 uppercase    letter-spacing 0.16em   CYAN
Boot / terminal  30px  VT323              letter-spacing 0.02em
Subtitle         24px  IBM Plex Mono      line-height 1.45
Filename / meta  18px  IBM Plex Mono      DIM, bottom-right
```

Uppercase everywhere in `VT323` and `Press Start 2P`. Sentence case in `IBM Plex Mono`. Never mix
cases inside one element.

---

## 3. The band

**Type never sits on the picture.** The first cut used a translucent plate floating over the
footage and it was the first thing anyone noticed: a 72%-opacity panel over 32×32 pixel art reads
as a smudge, not as a graphic, and it lands on exactly the part of a dungeon shot the eye is
trying to read.

Labels live in a **solid, full-width band that owns the bottom of the frame.**

```
┌──────────────────────────────────────────────────────────┐
│                                                          │
│                     the footage                          │
│                                                          │
├══════════════════════════════════════════════════════════┤ ← 3px accent rule
│ ▌ ISLAND 6 · ARCHIVE OF TEARS                            │   L1, TEXT, VT323 38px
│ ▌ RAYCAST — PER-PIXEL LIT                                │   L2, accent, VT323 27px
└──────────────────────────────────────────────────────────┘
```

- **Solid `PANEL` (#0A0E16). Never translucent.** This is what the game itself does — it dims the
  background and then draws an *opaque* panel (`UIOverlayArchitecture.md`). Matching that is why
  the band reads as part of the same world instead of as an overlay bolted on afterwards.
- **Height:** 132px with two lines, 92px with one. Full width, flush to the bottom edge.
- **A 3px rule along the top** in the act accent, and a 6px vertical bar left of the text.
- **In:** the band rises from the bottom edge over 260ms (ease-out cubic, the game's own curve),
  then the text types at 18ms/character. **Out:** it drops back over 220ms. No fade.
- **Text starts at x=96.**

Two things the band buys beyond legibility:

1. **It masks the game's own HUD.** Every first-person dungeon clip carries a status strip and a
   compass along its bottom edge. That is in-game UI, not trailer material, and the band covers it.
2. **It gives the captions a floor.** VO subtitles sit at `band height + 28px`, so they never
   collide with a label.

---

## 4. The CRT treatment

The most likely way this video goes wrong is an over-applied VHS preset. The game is a *phosphor
CRT*, not a worn videotape. There is no tracking error, no chroma bleed, no tape warble, no dust
and scratches, no VHS date stamp. Ever.

What there is:

| Effect | Amount | Applied to |
|---|---|---|
| Scanlines | 1px dark line every 3px, 8% opacity | **The footage only.** Never the type — see below |
| Barrel distortion | 1.5–2% | Full frame, constant |
| Vignette | 12% at the corners | Full frame, constant |
| Bloom | Threshold 0.75, radius 6px, 20% | `PHOSPHOR` and `CYAN` text only — not gameplay |
| Chromatic aberration | 0.4px, edges only | Full frame, constant |
| Interlace flicker | ±2% luminance at 30Hz | **Act 0 only.** It reads as "powering up" |

**The grade sits under the typography, not over it.** Running scanlines across 27px monospace
shreds it. In the composition the footage lives in a graded layer and every band, card and caption
is a sibling *above* it. Getting this backwards is what makes broadcast graphics look cheap.

**RGB split glitch** — a one-frame ±6px horizontal channel offset. Used exactly three times:
on `PERMANENT` (S07), on the S30 RetroForge boot, and on the last frame before the S39 power-off.
Three is the budget. A fourth makes all four feel arbitrary.

### The power-off (S39)

Reverse of a real CRT collapse, in three stages over 900ms:
1. 0–200ms — vertical collapse to a 4px horizontal band, luminance boosting as it squeezes
2. 200–400ms — the band contracts horizontally to a single dot, still bright
3. 400–900ms — the dot decays to black over a long, slow tail

Do not ease this linearly. The luminance boost during the collapse is what sells it.

---

## 5. Audio

### Music

One original chiptune theme in three states. Square + triangle + noise channels only — no orchestral
layer, no hybrid trailer percussion.

| State | Where | Character |
|---|---|---|
| **A — Statement** | 0:08–1:05 | 140 BPM. Bass and hat, then lead. Builds through Act 2, double-time under the S13 montage |
| **B — Descent** | 1:31–2:23 | **Not the theme.** A single evolving low drone with no percussion at all, derived from the theme's root note. It swells once, at S27, and then stops dead |
| **C — Forge** | 2:31–3:15 | The theme again — faster, brighter, major key, full arrangement. The same melody the viewer heard in Act 1, resolved |

The point of the B state is that the viewer notices the music left. Fifty-two seconds without
percussion is a long time in a trailer, which is precisely why the return at 2:31 lands.

**Silence is scored.** 2:23–2:25 has nothing on the track — not even room tone. That gap is a cue.

### SFX

Pull from the game's own synthesized audio wherever possible (record it to a separate track during
capture). Specifically worth lifting: the encounter sting, the level-up chime, the spell casts, the
door open, the coin sounds.

Layer in from a library only where the game has no equivalent:
- CRT degauss thunk and 60Hz hum (S01, S29)
- Mechanical keyboard clacks for every typing effect
- CRT power-off whine and click (S39)

### Mix

- VO at −6 dBFS, always the top of the mix.
- Music ducks 4 dB under VO with a 120ms attack and a 400ms release.
- Master to −14 LUFS integrated, true peak −1 dBTP (YouTube's target — anything hotter gets turned
  down on upload and loses the dynamic range that Acts 4 and 5 depend on).

---

## 6. Motion grammar

| Rule | Why |
|---|---|
| **Never scale the picture. No push-ins, no punch-ins, no zooms, ever.** | This is 32×32 pixel art captured at exact integer factors. Any scale throws away the one thing the footage has going for it — a 1.35× punch-in on a 2×-upscaled clip is a 2.7× magnification of the original pixels, and it looks it. The first cut had pushes at 1.35×, 1.5× and 2.3×; all are gone. |
| **Cuts, not dissolves.** | The only dissolves in the video are the ones inside the game's own animations |
| **All eases are ease-out cubic, 220–350ms.** | The exact curve the game's overlays use for their own entry animations. Matching it means the video's motion and the game's motion are the same motion |
| **Acts 0–3 and 6 cut on the beat (428ms grid at 140 BPM). Act 4 does not.** | Breaking the grid is how Act 4 announces itself as different before a word is spoken |
| **No motion blur on gameplay.** | 32×32 sprites at 60fps are already legible. Blur destroys the pixel grid, which is the whole aesthetic |
| **Nothing bounces, nothing overshoots.** | A CRT has no springs in it |

### Resolution: three legal shapes, and nothing else

Every clip is delivered at 1920×1080, and gets there one of exactly three ways:

| Shape | Used by | Why |
|---|---|---|
| **1920×1080 native** | The three first-person dungeon renderers | They project from the panel's width and height, so they genuinely draw more at a larger frame |
| **960×540 upscaled 2×** | Every fixed-size overlay and modal dialog | These draw at a fixed pixel size off `DisplayScale.SCALE` and would sit small in a native 1080 frame |
| **640×360 upscaled 3×** | The tile map and the top-down dungeon | Same reason, at a tile size that needs more magnification |

**The factor must be an exact integer.** Nearest-neighbour at 2.4× duplicates some source pixels
twice and others three times, so monospace text and sprite edges shimmer unevenly and clips cut
together look like they came from different sources. Three shots shipped that way before anyone
noticed. `tools/record.sh` now refuses a non-integer factor rather than repeating it.

RetroForge is the exception that proves the rule: its canvas has a fixed tile viewport and cannot
fill 1920×1080, so those clips are **cropped to the region the UI actually occupies (1540×932) and
padded onto the ground colour** — lossless, where scaling to fit would resample every glyph. The
editor then reads as a window, which is what it is.

---

## 7. Logo lockup

```
        RETROQUEST                    Press Start 2P 96px, PHOSPHOR
      + RETROFORGE                    Press Start 2P 96px, CYAN — the "+" in AMBER

  Java · Swing · 32×32 sprites · zero audio files
                                      IBM Plex Mono 18px, DIM
```

- The two product names are the same size. RetroForge is not a footnote — the second half of the
  video exists to argue that.
- The `+` is the only amber in Act 7, and the only glyph that isn't one of the two names.
- Centred, on `GROUND`, with 140px of air above and below the block.
- The spec line is deliberately flat and technical. This audience reads "zero audio files" as a
  flex, and stating it plainly is more convincing than any adjective would be.

---

## 8. What not to do

- No lens flares, no light leaks, no film grain, no dust and scratches.
- No modern trailer sound design — no braams, no risers, no reverse-cymbal swells.
- No "IN A WORLD" voice. The VO is written dry and unhurried on purpose.
- No stock fantasy imagery. Every frame comes from the build.
- No review-quote cards, no fake press pull-quotes, no invented awards.
- No "hours of gameplay" claim, no wishlist/Steam call-to-action unless the client supplies a real
  URL — an unbacked storefront line at the end of an otherwise honest video undercuts all of it.
- Don't smooth the sprites. Ever.

# Sound System — RetroQuest

**Last updated:** September 2026

## 1. Overview

All audio in RetroQuest is **procedurally synthesized** — no audio files are shipped. The system uses the Java Sound API to generate waveforms in real-time on background threads.

- **`SoundManager.java`** (~1710 lines) — Singleton audio manager. Synthesizes chiptune SFX and music using square, sine, triangle, saw, and noise waveforms.
- **`SoundEditor.java`** — GUI editor in RetroForge for creating/testing custom sounds (saved to `sounds/custom_sounds.json`).

---

## 2. Architecture

### Singleton Access

```java
SoundManager.getInstance().play("attack");
```

### Sound Resolution Order

`SoundManager.play(name)` checks three tiers in order:

1. **Pre-opened clip cache** (`openedClips`) — highest priority. 21 common SFX are pre-generated and pre-opened at startup; playback is instant (`stop/setFramePosition(0)/start`), no allocation, no threads.
2. **Custom JSON** (`sounds/custom_sounds.json`) — user-defined overrides; played on a background thread.
3. **Built-in soundMap** — 43 hardcoded sound keys mapped to synthesis methods; played on a background thread.

Pre-cached clips play on the calling thread instantly. Dynamic sounds (soundMap and JSON) run on a background thread to avoid blocking the Swing EDT.

The named convenience methods for cached sounds (`playHit()`, `playAttack()`, `playStep()`, `playVictory()`, `playMenuBlip()`, …) hit the same clip cache before falling back to synthesis, so direct callers such as `CombatEngine` get the cached clip too.

`custom_sounds.json` is stat-ed and parsed **once** (during cache warm-up, on a background thread) and kept in memory. Call `reloadCustomSounds()` after editing the file to pick up changes without a restart.

### Core Engine

| Method | Description |
|--------|-------------|
| `playTone(freq, durMs, vol)` | Square wave at given frequency |
| `playWaveform(type, freq, durMs, vol, pulseWidth)` | Any waveform type: square, saw, triangle, sine, noise |
| `playNoise(durMs, vol)` | White noise |
| `playSweep(startFreq, endFreq, durMs, vol)` | Frequency sweep — one buffer, one clip |
| `playArpeggio(freqs[], durs[], vol)` | Sequential tones |
| `playNote(noteName, durMs, vol, waveform)` | Chromatic note (e.g. "C#4") |
| `playChord(notes[], durMs, vol)` | Simultaneous notes (volume × 0.75) |
| `playSequence(notes[], durs[], vol)` | Melody with rests ("-" = silence) |

**Audio format:** 44100 Hz, 8-bit, mono, signed, little-endian.

**Envelope:** every generated buffer gets an 8 ms fade-in and fade-out (capped at ¼ of the buffer) to kill the click from a hard waveform cutoff.

**Sweeps:** a sweep is rendered into a **single** buffer and played as one clip. The frequency still advances once per 8 ms step (the stepped retro glissando), but the phase is continuous across steps and one envelope covers the whole sweep. The old implementation opened a thread and a `Clip` for every 8 ms slice — roughly 400 of each for the teleport whoosh — and applied a full fade in/out to each slice, amplitude-gating the sweep at 125 Hz.

### Master Volume & Mute

| Method | Description |
|--------|-------------|
| `setMasterVolume(float)` / `getMasterVolume()` | 0.0–1.0, default **0.70** |
| `adjustMasterVolume(float delta)` | Nudge and return the new value |
| `setMuted(boolean)` / `isMuted()` / `toggleMute()` | Mute toggle; muting silences what is already playing |

Volume is applied as `MASTER_GAIN` on every clip the synthesiser opens, including cached clips and clips already playing. Both settings persist through `java.util.prefs.Preferences` (user node `io.cannonforge.retroquest.sound`), so they survive a restart. When muted, no clip is opened at all.

### Musical Note System

Full chromatic scale support via scientific pitch notation: `"C4"`, `"F#3"`, `"Bb5"`, etc. Octaves 1–7.

```
frequency = 440.0 × 2^((midiNote - 69) / 12)
```

---

## 3. Registered Sound Keys

43 keys in `soundMap`. Keys marked ★ are also pre-rendered into the clip cache.

### Core Game Sounds

| Key | Synthesis | Description |
|-----|-----------|-------------|
| `step` ★ | square 240 Hz, 45 ms | Footstep |
| `attack` ★ | noise 20 ms + square 520 Hz, 50 ms | Melee attack |
| `hit` ★ | noise 25 ms + square 380 Hz, 60 ms | Taking damage |
| `victory` ★ | arpeggio 520→1240 Hz (4 notes) | Combat victory fanfare |
| `enter_town` | D-major fanfare (~3 s) | Town entry (triangle bass + arpeggio + chord) |
| `leave_town` | D-major ascending (~3 s) | Town exit (A2 pedal + 3 phrases) |
| `spell` ★ | sweep 800→1400 Hz, 60 ms | Generic spellcast |
| `heal` ★ | sine 440 + triangle 660 + sine 880 | Healing |

### Retro SFX

| Key | Synthesis | Description |
|-----|-----------|-------------|
| `sword` ★ | sweep 900→400 Hz, 80 ms | Sword swing |
| `spellcast` ★ | arpeggio 600/900/1200 Hz | Spell cast |
| `fireball` | noise + sweep 480→160 Hz + triangle 80 Hz | Fireball |
| `icestorm` | triangle 520 Hz + arpeggio 800→1400 + sweep 500→140 | Ice storm |
| `lightning` | noise + square 1200 Hz + triangle 300 Hz | Lightning bolt |
| `explosion` | noise 40 ms + sweep 200→60 Hz + noise 300 ms | Explosion |
| `laser` ★ | sweep 1350→420 Hz, 70 ms | Laser zap |
| `pit` | sweep 620→140 Hz then 580→160 Hz | Falling into pit |
| `teleport` | multi-phase ~5.6 s | Star-tunnel whoosh (sweeps + triangle pings) |
| `door` | 4 alternating sweeps 280–520 Hz | Door creak |
| `coin` ★ | square 1200 Hz + square 1800 Hz | Coin pickup |
| `levelup` ★ | arpeggio 720/960/1280 Hz | Level up |
| `menublip` ★ | square 880 Hz, 25 ms | Menu selection |
| `hurt` ★ | sweep 600→200 Hz + noise 50 ms | Player hurt |
| `boss` | sweep 80→40 Hz + noise 280 ms + saw 120 Hz | Boss encounter |
| `death` ★ | triangle 180 Hz + triangle 90 Hz, 600 ms | Death knell |
| `dragon` ★ | sweep 400→100 Hz + noise 500 ms | Dragon breath |
| `powerup` ★ | arpeggio 620/880/1240 Hz | Power-up |
| `arrow` ★ | square 1000 Hz + sweep 800→200 Hz | Arrow whoosh |
| `missile` ★ | sweep 600→1200 Hz + triangle 1500 Hz | Magic missile |
| `chain` ★ | arpeggio 780/1120/1480 Hz | Chain lightning |
| `meteor` | sweep 600→80 Hz + noise 100 ms | Meteor |
| `dungeon_descent` | sequence A2→A1, slowing | Dungeon descent |
| `altarChime` ★ | triangle arpeggio 523→1047 Hz | Altar interaction |
| `throne` | 280 Hz rumble + royal arpeggio | Throne interaction |
| `flee` | 4 noise thuds + sweep 480→140 Hz | Fleeing combat |
| `diceroll` | 4 noise pops + 680 Hz blip | Dice tumbling |
| `puzzle` | triangle 880/1047/1319/1568 Hz | Puzzle solved |
| `anvil` | square 1200 Hz + noise 800 Hz | Anvil clang |
| `lance` | noise 80 ms + noise 60 ms | Lance whoosh |
| `axethrow` | noise 50 ms + square 200 Hz | Axe throw |
| `bump` | noise 12 ms + square 80 Hz | Walking into a wall |
| `shadow_descent` | sweep 120→40 Hz, ghost pings, 55 Hz drone, thud (~5 s) | Thalorax → Umbryn |
| `war_march` | 6 drum hits, lightning, 8 marching pings, crash (~5 s) | Umbryn → Bellorak |
| `ascension` | sweep 100→800 Hz, 7-god arpeggio, convergence (~6 s) | Bellorak → Cradle |

`textblip` (triangle 600 Hz, 14 ms) is cached but has no `soundMap` entry — play it with `play("textblip")`.

### Extended Sounds (not in soundMap, called directly)

| Method | Description |
|--------|-------------|
| `playDeathSong()` | D-minor funeral lament (~2.8 s, triangle waves) |
| `playFountain()` | Sparkling fountain sequence |
| `playChestOpen()` | Chest creak + coin jingle |
| `playTrap()` | Trap sting + poison hiss |
| `playCube()` | Glowing cube energy pulse |
| `playCradleAwaken()` | Ascending D-minor sequence (~3 s) |
| `playPhaseTransition()` | Boss phase impact + rising sweep |
| `playIntroTheme()` | ~45 s ambient intro (A minor) — sparse arpeggios building to climax |
| `playEpilogueTheme()` | ~20 s D-major epilogue melody |
| `playCreditsTheme()` | Looping ambient C-major chord bed |

---

## 4. Music Channels

Long-form themes are a stream of independent `Clip`s queued by a composing thread. Flipping a "playing" flag stops *new* notes but cannot silence a 20-second bass drone that is already open, so every clip built while a channel is current is tracked in `musicClips` and the matching `stop…()` closes it for real.

Channels: `title`, `intro`, `epilogue`, `credits`.

| Method | Notes |
|--------|-------|
| `playTitleTheme()` / `stopTitleTheme()` / `fadeOutTitleTheme(ms[, onComplete])` | Thread-safe via `musicLock` |
| `playIntroTheme()` / `stopIntroTheme()` | Stops queued clips, not just the flag |
| `playEpilogueTheme()` / `stopEpilogueTheme()` | Stops queued clips, not just the flag |
| `playCreditsTheme()` / `stopCreditsTheme()` | Stops queued clips, not just the flag |

**Fade-out:** `fadeOutTitleTheme()` keeps the title channel current for the whole fade, so notes already in flight are tracked and ramped too. A `titleGeneration` counter means calling `playTitleTheme()` during a fade supersedes the fade instead of leaving `titlePlaying == true` with no thread behind it (which used to kill title music for the rest of the session).

### Title Theme (Procedural Synthesis)

- **Key:** C major. **Tempo:** ~63 BPM (quarter note = 960 ms).
- **8 bars** of 4/4, looped continuously via `playTitleTheme()`.
- 3 simultaneous voices: slow bass (triangle, very quiet), rising arpeggios (sine), and a singable melody (sine).
- Chord progression: C – G – Am – F – C – Em – F – G → C.

---

## 5. Custom Sound Definitions (`sounds/custom_sounds.json`)

JSON array of sound objects. Each definition supports:

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `name` | String | required | Key name (matched case-insensitively) |
| `wave` | String | `"square"` | Waveform: square, sine, triangle, saw, noise |
| `freq` | int | 440 | Base frequency |
| `dur` | int | 200 | Duration in ms |
| `vol` | int | 28 | Volume (0–100, divided by 100) |
| `pulse` | int | 50 | Pulse width (square wave duty cycle) |
| `sweep` | boolean | false | Enable frequency sweep |
| `sweepEnd` | int | 220 | End frequency for sweep |
| `sequence` | String | null | Note sequence: `"C4:200 D4:200 -:100 E4:300"` |
| `arpNotes` | List | null | Arpeggio/chord notes: `["440,80", "C4,120"]` |
| `arpMode` | int | 0 | 0 = arpeggio (sequential), 1 = chord (simultaneous) |

Custom sounds override built-in sounds with the same name. The file is parsed once at start-up — call `reloadCustomSounds()` to re-read it.

---

## 6. Warmup

`warmup()` plays a 0.1-second silent clip on a background thread to force Java Sound API initialization, avoiding first-sound latency, and primes the custom-sound cache. Common SFX are additionally pre-opened by `initSoundCache()`.

---

## 7. Known Gaps

1. **Thread per dynamic sound** — each uncached `play()` spawns a Thread. Cached SFX (the common ones) spawn none.
2. **No spatial audio** — all sounds play at full volume regardless of distance.
3. **8-bit sample depth** — quantisation noise is audible at low master volumes. Intentional, for the retro character.
4. **No per-category volume** — one master level; there is no separate music/SFX slider.
5. **No UI for volume/mute yet** — the API exists (`setMasterVolume`, `toggleMute`) but nothing in `Retroquest.handleKey` is bound to it.

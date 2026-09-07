package io.cannonforge.retroquest.core;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.prefs.Preferences;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineEvent;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

/**
 * Singleton audio manager that synthesizes retro chiptune sound effects and music
 * using the Java Sound API with procedurally generated waveforms (square, sine, triangle, saw).
 * All audio is procedural — no audio files are used.
 *
 * <h2>Playing sounds</h2>
 * <p>Use {@link #play(String)} for any named sound. The lookup order is:
 * <ol>
 *   <li><b>Pre-opened clip cache</b> ({@code openedClips}) — zero-latency; just rewinds and
 *       restarts a pre-opened {@link Clip}. Covers all common SFX (see list below).</li>
 *   <li><b>Custom JSON override</b> ({@code sounds/custom_sounds.json}) — user-defined sounds
 *       that override built-ins by name.</li>
 *   <li><b>Built-in {@code soundMap}</b> — dynamic sounds generated on a background thread
 *       for complex, orchestrated, or long-form effects.</li>
 * </ol>
 *
 * <h2>Pre-cached sounds (zero-latency via {@code play()})</h2>
 * <pre>
 *   step, menublip, textblip, spell, laser, sword,
 *   attack, hit, hurt, death,
 *   victory, levelup, powerup, spellcast, chain,
 *   coin, heal, altarChime, arrow, missile, dragon
 * </pre>
 *
 * <h2>Dynamic sounds (background thread, slight latency)</h2>
 * <pre>
 *   enter_town, leave_town, teleport, dungeon_descent,
 *   fireball, icestorm, lightning, explosion, pit, door,
 *   boss, diceroll, flee, meteor, throne, puzzle, anvil,
 *   lance, axethrow, bump, shadow_descent, war_march, ascension
 * </pre>
 *
 * <h2>Volume &amp; mute</h2>
 * <p>{@link #setMasterVolume(float)} (0..1, default 0.70) and {@link #toggleMute()} /
 * {@link #setMuted(boolean)} apply to every clip the synthesiser opens, including
 * ones already playing. Both settings persist via {@link java.util.prefs.Preferences}.
 *
 * <h2>Music</h2>
 * <ul>
 *   <li>{@link #playTitleTheme()} / {@link #stopTitleTheme()} / {@link #fadeOutTitleTheme(int)} —
 *       looping ambient C-major menu theme (~63 BPM, sine/triangle)</li>
 *   <li>{@link #playIntroTheme()} / {@link #stopIntroTheme()} — 45-second A-minor intro</li>
 *   <li>{@link #playDeathSong()} — D-minor funeral lament (~2.8 s)</li>
 * </ul>
 *
 * <h2>Musical API</h2>
 * <p>Lower-level helpers for composing in-game sequences:
 * {@link #playNote(String, int, float)}, {@link #playSequence(String[], int[], float)},
 * {@link #playArpeggio(int[], int[], float)}, {@link #playChord(String[], int, float)},
 * {@link #playMidiNote(int, int, float)}, {@link #noteToFrequency(String)}.
 *
 * <h2>Buffer generation helpers (private)</h2>
 * <p>{@code genBuffer}, {@code genSweep}, {@code genNoise}, {@code silence},
 * {@code mix}, {@code concat}, {@code zeroPad} — used by {@code initSoundCache()}
 * to pre-build PCM byte arrays without playing them.
 *
 * <p>Obtain the singleton via {@link #getInstance()}.
 */
public class SoundManager {

    private static final SoundManager instance = new SoundManager();
    private final Map<String, Runnable> soundMap = new HashMap<>();

    private volatile boolean titlePlaying = false;
    private volatile Thread titleThread = null;
    private final Object musicLock = new Object();
    /** Bumped every time the title theme is (re)started or stopped, so an in-flight
     *  fade-out can tell that it has been superseded and must not clean up. */
    private volatile int titleGeneration = 0;

    private volatile boolean introPlaying = false;
    private volatile Thread introThread = null;
    private final Map<String, Clip> openedClips = new java.util.concurrent.ConcurrentHashMap<>();

    // ── Music channels ───────────────────────────────────────────────────────
    // A long-form theme is a stream of independent Clips queued by a composing
    // thread. Flipping the "playing" flag stops new notes but cannot silence a
    // 20-second bass drone that is already open, so every clip built while a
    // channel is current is tracked and can be stopped for real.
    static final String CH_TITLE    = "title";
    static final String CH_INTRO    = "intro";
    static final String CH_EPILOGUE = "epilogue";
    static final String CH_CREDITS  = "credits";
    /** Name of the theme currently being synthesised, or {@code null} for plain SFX. */
    private volatile String musicContext = null;
    private final Map<String, List<Clip>> musicClips = new java.util.concurrent.ConcurrentHashMap<>();

    // ── Master volume / mute ─────────────────────────────────────────────────
    private static final String PREF_NODE   = "io.cannonforge.retroquest.sound";
    private static final String PREF_VOLUME = "masterVolume";
    private static final String PREF_MUTED  = "muted";
    private volatile float   masterVolume = 0.70f;
    private volatile boolean muted        = false;

    private SoundManager() {
        loadPrefs();
        // === CORE GAME SOUNDS ===
        soundMap.put("step",          this::playStep);
        soundMap.put("attack",        this::playAttack);
        soundMap.put("hit",           this::playHit);
        soundMap.put("victory",       this::playVictory);
        soundMap.put("enter_town",    this::playEnterTown);
        soundMap.put("leave_town",    this::playLeaveTown);
        soundMap.put("spell",         this::playSpell);
        soundMap.put("heal",          this::playHeal);

        // === RETRO SFX ===
        soundMap.put("sword",         this::playSwordSwing);
        soundMap.put("spellcast",     this::playSpellCast);
        soundMap.put("fireball",      this::playFireball);
        soundMap.put("icestorm",      this::playIceStorm);
        soundMap.put("lightning",     this::playLightning);
        soundMap.put("explosion",     this::playExplosion);
        soundMap.put("laser",         this::playLaserZap);
        soundMap.put("pit",           this::playPitFall);
        soundMap.put("teleport",      this::playTeleportWhoosh);
        soundMap.put("door",          this::playDoorCreak);
        soundMap.put("coin",          this::playCoinDing);
        soundMap.put("levelup",       this::playLevelUp);
        soundMap.put("menublip",      this::playMenuBlip);
        soundMap.put("hurt",          this::playHurtScream);
        soundMap.put("boss",          this::playBossRoar);
        soundMap.put("death",         this::playDeathKnell);
        soundMap.put("dragon",        this::playDragonBreath);
        soundMap.put("powerup",       this::playPowerUp);
        soundMap.put("arrow",         this::playArrowWhoosh);
        soundMap.put("missile",       this::playMagicMissile);
        soundMap.put("chain",         this::playChainLightning);
        soundMap.put("meteor",        this::playMeteor);
        soundMap.put("dungeon_descent", this::dungeonDescent);
        soundMap.put("altarChime",    this::playAltarChime);
        soundMap.put("throne",        this::playThrone);
        soundMap.put("flee",          this::playFlee);
        soundMap.put("diceroll",      this::playDiceRoll);
        soundMap.put("puzzle",        this::playPuzzle);
        soundMap.put("anvil",         this::playAnvilClang);
        soundMap.put("lance",         this::playLanceWhoosh);
        soundMap.put("axethrow",      this::playAxeThrow);
        soundMap.put("shadow_descent", this::playShadowDescent);
        soundMap.put("war_march",      this::playWarMarch);
        soundMap.put("ascension",      this::playAscension);
        soundMap.put("bump",           this::playBump);
        initSoundCache();
    }

    public static SoundManager getInstance() { return instance; }

    // ====================== MASTER VOLUME / MUTE ======================

    /** Current master volume, 0.0 (silent) – 1.0 (full). Default 0.70. */
    public float getMasterVolume() { return masterVolume; }

    /**
     * Sets the master volume (clamped to 0..1) and persists it.
     * Applied to every clip the synthesiser opens, and to clips already playing.
     */
    public void setMasterVolume(float v) {
        masterVolume = Math.max(0f, Math.min(1f, v));
        savePrefs();
        refreshLiveGain();
    }

    /** Nudges the master volume by {@code delta} and returns the new value. */
    public float adjustMasterVolume(float delta) {
        setMasterVolume(masterVolume + delta);
        return masterVolume;
    }

    public boolean isMuted() { return muted; }

    /** Mutes / unmutes all audio and persists the choice. Muting silences what is playing now. */
    public void setMuted(boolean m) {
        muted = m;
        savePrefs();
        if (m) silenceEverything(); else refreshLiveGain();
    }

    /** Flips mute and returns the new state — handy for a single key binding. */
    public boolean toggleMute() {
        setMuted(!muted);
        return muted;
    }

    /** Master gain for a clip: {@code factor} (a local fade level) × the master volume. */
    private void applyVolume(Clip c, float factor) {
        try {
            FloatControl gain = (FloatControl) c.getControl(FloatControl.Type.MASTER_GAIN);
            float v  = Math.max(0f, Math.min(1f, factor * masterVolume));
            float db = (v <= 0.0001f) ? -80f : (float)(20.0 * Math.log10(v));
            gain.setValue(Math.max(gain.getMinimum(), Math.min(db, gain.getMaximum())));
        } catch (Exception ignored) {
            // Line has no gain control — fall back to the nominal synthesised level.
        }
    }

    /** Re-applies the master gain to every clip that is currently open. */
    private void refreshLiveGain() {
        for (Clip c : openedClips.values()) applyVolume(c, 1f);
        for (List<Clip> ch : musicClips.values())
            for (Clip c : ch) applyVolume(c, 1f);
    }

    /** Stops everything currently audible (used when mute is switched on). */
    private void silenceEverything() {
        for (Clip c : openedClips.values()) { try { c.stop(); } catch (Exception ignored) {} }
        for (String ch : musicClips.keySet()) silenceChannel(ch);
    }

    private void loadPrefs() {
        try {
            Preferences p = Preferences.userRoot().node(PREF_NODE);
            masterVolume = Math.max(0f, Math.min(1f, p.getFloat(PREF_VOLUME, 0.70f)));
            muted        = p.getBoolean(PREF_MUTED, false);
        } catch (Exception ignored) { /* headless / restricted JVM — keep defaults */ }
    }

    private void savePrefs() {
        try {
            Preferences p = Preferences.userRoot().node(PREF_NODE);
            p.putFloat(PREF_VOLUME, masterVolume);
            p.putBoolean(PREF_MUTED, muted);
        } catch (Exception ignored) {}
    }

    // ====================== MUSIC CHANNEL BOOKKEEPING ======================

    private List<Clip> channel(String name) {
        return musicClips.computeIfAbsent(name, k -> new CopyOnWriteArrayList<>());
    }

    /** Stops and closes every clip queued on a music channel, however long it is. */
    private void silenceChannel(String name) {
        List<Clip> clips = musicClips.get(name);
        if (clips == null) return;
        for (Clip c : clips) {
            try { c.stop(); c.close(); } catch (Exception ignored) {}
        }
        clips.clear();
    }

    /** Clears the current channel only if it is still the one named. */
    private void endContext(String name) {
        if (name.equals(musicContext)) musicContext = null;
    }

    private static final String SOUNDS_FILE = "sounds/custom_sounds.json";

    /**
     * Play a sound by name.
     * Checks custom_sounds.json FIRST — if a saved sound matches the name
     * it overrides the hardcoded version. Falls back to the built-in soundMap.
     * API is unchanged: all existing callers work exactly as before.
     */
    public void play(String name) {
        if (muted) return;
        if (playCached(name)) return;               // pre-opened clip — zero latency
        if (tryPlayFromJson(name)) return;          // custom JSON override wins
        Runnable r = soundMap.get(name);
        if (r != null) new Thread(r).start();       // built-in fallback
    }

    /**
     * Restarts a pre-opened clip if one is cached under {@code name}.
     * Returns true when the sound has been handled (including while muted, so the
     * caller does not fall through to regenerating a buffer nobody will hear).
     */
    private boolean playCached(String name) {
        Clip c = openedClips.get(name);
        if (c == null) return false;
        if (muted) return true;
        try {
            applyVolume(c, 1f);
            c.stop();
            c.setFramePosition(0);
            c.start();
        } catch (Exception ignored) {}
        return true;
    }

    // custom_sounds.json is parsed once, not on every uncached play() from the EDT.
    private volatile Map<String, Map<String, Object>> customSounds = null;
    private volatile boolean customSoundsLoaded = false;

    /** Drops the cached custom_sounds.json so the next play() re-reads it from disk. */
    public void reloadCustomSounds() {
        synchronized (SOUNDS_FILE) {
            customSounds = null;
            customSoundsLoaded = false;
        }
    }

    /** Lazily loads (once) the custom sound definitions, keyed by lower-case name. */
    private Map<String, Map<String, Object>> customSounds() {
        if (customSoundsLoaded) return customSounds;
        synchronized (SOUNDS_FILE) {
            if (customSoundsLoaded) return customSounds;
            Map<String, Map<String, Object>> byName = null;
            try {
                File f = new File(SOUNDS_FILE);
                if (f.exists()) {
                    String json = new String(Files.readAllBytes(f.toPath()));
                    List<Map<String, Object>> sounds = new Gson().fromJson(json,
                        new TypeToken<List<Map<String, Object>>>(){}.getType());
                    if (sounds != null) {
                        byName = new HashMap<>();
                        for (Map<String, Object> def : sounds) {
                            Object n = def.get("name");
                            if (n != null) byName.put(n.toString().toLowerCase(), def);
                        }
                    }
                }
            } catch (Exception e) {
                // Silently fall through to built-in sounds on any error
            }
            customSounds = byName;
            customSoundsLoaded = true;
            return customSounds;
        }
    }

    /**
     * Attempts to find and play a sound definition from custom_sounds.json.
     * Matches on the "name" field (case-insensitive).
     * Returns true if a match was found and playback was started.
     */
    private boolean tryPlayFromJson(String name) {
        Map<String, Map<String, Object>> defs = customSounds();
        if (defs == null || name == null) return false;
        Map<String, Object> def = defs.get(name.toLowerCase());
        if (def == null) return false;
        new Thread(() -> playFromDef(def)).start();
        return true;
    }

    /** Execute a sound from a JSON definition map using the existing engine. */
    @SuppressWarnings("unchecked")
    private void playFromDef(Map<String, Object> def) {
        String wave  = def.getOrDefault("wave",  "square").toString();
        int    freq  = ((Number) def.getOrDefault("freq",  440)).intValue();
        int    dur   = ((Number) def.getOrDefault("dur",   200)).intValue();
        float  vol   = ((Number) def.getOrDefault("vol",    28)).floatValue() / 100f;
        int    pulse = ((Number) def.getOrDefault("pulse",  50)).intValue();
        boolean sweep = Boolean.parseBoolean(def.getOrDefault("sweep", false).toString());

        // Sequence takes highest priority
        if (def.get("sequence") != null) {
            String[] tokens = def.get("sequence").toString().trim().split("\s+");
            String[] notes = new String[tokens.length];
            int[]    durs  = new int[tokens.length];
            for (int i = 0; i < tokens.length; i++) {
                String[] parts = tokens[i].split(":");
                notes[i] = parts[0].trim();
                try { durs[i] = Integer.parseInt(parts[1].trim()); }
                catch (Exception e) { durs[i] = 200; }
            }
            playSequence(notes, durs, vol);
            return;
        }

        // Arp / chord
        if (def.get("arpNotes") != null) {
            List<String> noteList = (List<String>) def.get("arpNotes");
            int arpMode = def.get("arpMode") != null
                ? ((Number) def.get("arpMode")).intValue() : 0;
            int[] freqs = new int[noteList.size()];
            int[] durs2 = new int[noteList.size()];
            for (int i = 0; i < noteList.size(); i++) {
                String[] parts = noteList.get(i).split(",");
                try { freqs[i] = Integer.parseInt(parts[0].trim()); }
                catch (NumberFormatException e) {
                    freqs[i] = (int) Math.round(noteToFrequency(parts[0].trim()));
                }
                durs2[i] = parts.length > 1
                    ? Integer.parseInt(parts[1].trim()) : dur;
            }
            if (arpMode == 1) {
                // Chord — play simultaneously
                float chordVol = vol * 0.7f;
                for (int freq2 : freqs)
					playWaveform(wave, freq2, dur, chordVol, pulse);
            } else {
                playArpeggio(freqs, durs2, vol);
            }
            return;
        }

        // Simple tone or sweep
        if (sweep) {
            int sweepEnd = Integer.parseInt(def.getOrDefault("sweepEnd", "220").toString());
            playSweep(freq, sweepEnd, dur, vol);
        } else if (wave.equals("noise")) {
            playNoise(dur, vol);
        } else {
            playWaveform(wave, freq, dur, vol, pulse);
        }
    }

    // ====================== 1980s TUNED DEFAULT SOUNDS ======================

    // Each of these has a pre-rendered clip in openedClips; the direct callers
    // (combat, movement, UI) must hit that cache instead of re-synthesising.
    public void playStep()          { if (playCached("step")) return; playTone(240, 45, 0.22f); }
    public void playAttack()        { if (playCached("attack")) return; playNoise(20, 0.35f); playTone(520, 50, 0.28f); }
    public void playHit()           { if (playCached("hit")) return; playNoise(25, 0.30f); playTone(380, 60, 0.25f); }
    public void playVictory()       { if (playCached("victory")) return; playArpeggio(new int[]{520,680,840,1240}, new int[]{70,70,70,260}, 0.28f); }

    public void playLeaveTown() {
        new Thread(() -> {
            int Q = 340;  // quarter note
            int E = 170;  // eighth note
            int H = 680;  // half note

            // Open A pedal — the 5th of D major feels expectant, outward-looking
            playNote("A2", 2900, 0.15f, "triangle");

            // Phrase 1 — stepping into daylight: bright ascending D major lift
            playNote("D4",  E, 0.28f); sleep(E);
            playNote("E4",  E, 0.28f); sleep(E);
            playNote("F#4", Q, 0.30f); sleep(Q);
            playNote("A4",  Q, 0.30f); sleep(Q);

            // Phrase 2 — wilderness panorama opens: graceful descent as world is revealed
            playNote("D5",  Q, 0.32f); sleep(Q);
            playNote("C#5", E, 0.26f); sleep(E);
            playNote("B4",  E, 0.26f); sleep(E);
            playNote("A4",  Q, 0.28f); sleep(Q);

            // Phrase 3 — the road ahead: D major arpeggio climbing to horizon
            playNote("D4",  E, 0.24f); sleep(E);
            playNote("F#4", E, 0.24f); sleep(E);
            playNote("A4",  E, 0.24f); sleep(E);
            playNote("D5",  H, 0.28f);
            playChord(new String[]{"D3", "A3"}, H, 0.16f);
        }).start();
    }
    public void playSpell()         { if (playCached("spell")) return; playSweep(800, 1400, 60, 0.32f); }
    public void playHeal() {
        if (playCached("heal")) return;
        new Thread(() -> {
            playWaveform("sine", 440, 50, 0.30f, 50); sleep(50);
            playWaveform("triangle", 660, 60, 0.32f, 50); sleep(60);
            playWaveform("sine", 880, 100, 0.35f, 50);
        }).start();
    }

    public void playSwordSwing()    { if (playCached("sword")) return; playSweep(900, 400, 80, 0.30f); }
    public void playSpellCast()     { if (playCached("spellcast")) return; playArpeggio(new int[]{600, 900, 1200}, new int[]{40, 40, 60}, 0.30f); }
    public void playFireball() {
        new Thread(() -> {
            playNoise(60, 0.30f);
            playSweep(480, 160, 260, 0.35f);
            sleep(200);
            playWaveform("triangle", 80, 120, 0.25f, 50);
        }).start();
    }
    public void playIceStorm() {
        new Thread(() -> {
            playWaveform("triangle", 520, 200, 0.28f, 50);
            playArpeggio(new int[]{800, 1100, 1400}, new int[]{50, 50, 80}, 0.25f);
            sleep(150);
            playSweep(500, 140, 200, 0.22f);
        }).start();
    }
    public void playLightning() {
        new Thread(() -> {
            playNoise(30, 0.45f); sleep(25);
            playWaveform("square", 1200, 40, 0.35f, 50); sleep(35);
            playWaveform("triangle", 300, 60, 0.25f, 50);
        }).start();
    }
    public void playExplosion() {
        new Thread(() -> {
            playNoise(40, 0.50f); sleep(30);
            playSweep(200, 60, 300, 0.38f);
            playNoise(300, 0.30f);
        }).start();
    }
    public void playLaserZap()      { if (playCached("laser")) return; playSweep(1350, 420, 70, 0.30f); }
    public void playPitFall() {
        new Thread(() -> {
            playSweep(620, 140, 460, 0.38f);
            sleep(300);
            playSweep(580, 160, 400, 0.18f);
        }).start();
    }
    public void playTeleportWhoosh() {
        new Thread(() -> {
            // === Warp activation — initial energy build ===
            playSweep(340, 1480, 420, 0.32f);
            sleep(420);

            // === Wormhole tunnel — slow descending oscillation in background ===
            playSweep(780, 140, 2200, 0.16f);

            // === Stars streaking past — short Doppler-shifted triangle pings ===
            int[] pitches = { 1600, 2200, 1800, 2500, 1400, 2100, 1900, 2400, 1700, 2300, 1500, 2000 };
            int[]   gaps  = {   90,  130,   80,  160,  100,  140,   90,  120,  180,   95,  150,  110 };
            for (int i = 0; i < pitches.length; i++) {
                playWaveform("triangle", pitches[i], 60, 0.11f, 50);
                sleep(gaps[i]);
            }

            // === Deep space hum — vast and empty ===
            playWaveform("triangle", 85, 1800, 0.14f, 50);
            sleep(300);

            // === Second star wave — faster, arriving at destination ===
            for (int i = 0; i < 10; i++) {
                int pitch = 1500 + (i * 120) % 1200;
                playWaveform("triangle", pitch, 40, 0.09f, 50);
                sleep(70 + (i % 3) * 40);
            }

            // === Arrival — bright upward sweep ===
            sleep(100);
            playSweep(200, 1100, 360, 0.24f);
        }).start();
    }
    public void playDoorCreak() {
        new Thread(() -> {
            playSweep(280, 420, 100, 0.26f); sleep(60);
            playSweep(400, 300, 80, 0.24f); sleep(40);
            playSweep(320, 520, 120, 0.28f); sleep(50);
            playSweep(480, 350, 80, 0.22f);
        }).start();
    }
    public void playCoinDing() {
        if (playCached("coin")) return;
        new Thread(() -> {
            playTone(1200, 20, 0.30f); sleep(15);
            playTone(1800, 40, 0.35f);
        }).start();
    }
    public void playLevelUp()       { if (playCached("levelup")) return; playArpeggio(new int[]{720,960,1280}, new int[]{80,80,120}, 0.32f); }
    public void playMenuBlip()      { if (playCached("menublip")) return; playTone(880, 25, 0.30f); }
    public void playBump()          { playNoise(12, 0.18f); playTone(80, 35, 0.20f); }
    public void playDiceRoll() {
        new Thread(() -> {
            // Rapid noise pops — dice tumbling across a table
            playNoise(16, 0.22f); sleep(30);
            playNoise(14, 0.20f); sleep(24);
            playNoise(18, 0.24f); sleep(20);
            playNoise(12, 0.18f); sleep(16);
            // Settle: brief light blip
            playTone(680, 18, 0.15f);
        }).start();
    }
    public void playHurtScream()    { if (playCached("hurt")) return; playSweep(600, 200, 120, 0.40f); playNoise(50, 0.22f); }
    public void playBossRoar() {
        new Thread(() -> {
            playSweep(80, 40, 400, 0.40f);
            playNoise(280, 0.35f);
            sleep(100);
            playWaveform("saw", 120, 350, 0.28f, 50);
        }).start();
    }
    public void playDeathKnell() {
        if (playCached("death")) return;
        playWaveform("triangle", 180, 600, 0.28f, 50);
        playWaveform("triangle", 90, 600, 0.22f, 50);
    }

    /**
     * Woeful D-minor funeral lament that plays through the death animation (~2.8s)
     * and lingers softly into the death overlay screen.
     */
    public void playDeathSong() {
        new Thread(() -> {
            int Q = 500;   // quarter note
            int H = 1000;  // half note

            // Funeral bell toll — low A drone under the whole phrase
            playNote("A2", H + Q + H, 0.20f, "triangle");

            // Descending D-minor lament — matches animation phases:
            playNote("D5",  Q, 0.26f, "triangle"); sleep(Q);   // red flash / shake
            playNote("C5",  Q, 0.24f, "triangle"); sleep(Q);   // sprite shatters
            playNote("Bb4", Q, 0.24f, "triangle"); sleep(Q);   // fragments fall
            playNote("A4",  H, 0.27f, "triangle");             // tombstone rises
            playNote("F4",  H, 0.18f, "triangle"); sleep(H);   // harmony third beneath
            playNote("G4",  Q, 0.22f, "triangle"); sleep(Q);   // R.I.P. appears
            playNote("F4",  Q, 0.20f, "triangle"); sleep(Q);   // chiselled letters
            playNote("E4",  Q, 0.18f, "triangle"); sleep(Q);   // fade to black

            // Final low D — very soft, lingers into the death overlay
            playNote("D4", H + Q, 0.15f, "triangle");
            playNote("D3", H + Q, 0.12f, "triangle");
        }).start();
    }
    public void playDragonBreath()  { if (playCached("dragon")) return; playSweep(400, 100, 500, 0.38f); playNoise(500, 0.22f); }
    public void playPowerUp()       { if (playCached("powerup")) return; playArpeggio(new int[]{620,880,1240}, new int[]{60,60,140}, 0.32f); }
    public void playArrowWhoosh() {
        if (playCached("arrow")) return;
        new Thread(() -> {
            playWaveform("square", 1000, 15, 0.28f, 50); sleep(10);
            playSweep(800, 200, 90, 0.30f);
        }).start();
    }
    public void playFlee() {
        new Thread(() -> {
            // Rapid panicked footstep thuds
            for (int i = 0; i < 4; i++) {
                playNoise(18, 0.22f);
                sleep(60);
            }
            // Descending Doppler whoosh — the player sprinting away
            playSweep(480, 140, 260, 0.26f);
        }).start();
    }
    public void playMagicMissile() {
        if (playCached("missile")) return;
        new Thread(() -> {
            playSweep(600, 1200, 45, 0.30f); sleep(35);
            playWaveform("triangle", 1500, 25, 0.25f, 50);
        }).start();
    }
    public void playChainLightning(){ if (playCached("chain")) return; playArpeggio(new int[]{780,1120,1480}, new int[]{40,40,80}, 0.35f); }
    public void playMeteor() {
        new Thread(() -> {
            playSweep(600, 80, 500, 0.36f);
            sleep(420);
            playNoise(100, 0.40f);
        }).start();
    }
    // =====================================================================
    public void playPuzzle() {
        new Thread(() -> {
            playWaveform("triangle", 880, 60, 0.25f, 50); sleep(70);
            playWaveform("triangle", 1047, 60, 0.28f, 50); sleep(70);
            playWaveform("triangle", 1319, 80, 0.30f, 50); sleep(90);
            playWaveform("triangle", 1568, 150, 0.32f, 50);
        }).start();
    }

    public void playAnvilClang() {
        new Thread(() -> {
            playWaveform("square", 1200, 30, 0.35f, 50);
            playWaveform("noise",  800, 40, 0.15f, 50);
        }).start();
    }

    public void playLanceWhoosh() {
        new Thread(() -> {
            playWaveform("noise", 400, 80, 0.20f, 50); sleep(20);
            playWaveform("noise", 600, 60, 0.15f, 50);
        }).start();
    }

    public void playAxeThrow() {
        new Thread(() -> {
            playWaveform("noise", 300, 50, 0.18f, 50); sleep(30);
            playWaveform("square", 200, 40, 0.25f, 50);
        }).start();
    }

    // ── Island transition sounds ─────────────────────────────────────────────

    /** Shadow Descent (Thalorax → Umbryn, ~5 s): deep rumble, ghost whispers, void hum, arrival thud. */
    public void playShadowDescent() {
        new Thread(() -> {
            // Deep rumble — descending bass sweep
            playSweep(120, 40, 1200, 0.20f);
            sleep(400);
            // Ghost whispers — rapid high-frequency triangle pings, quiet
            for (int i = 0; i < 8; i++) {
                playWaveform("triangle", 2800 + (i * 200) % 600, 80, 0.06f, 50);
                sleep(200 + (i % 3) * 100);
            }
            // Void hum — sustained low drone
            playWaveform("triangle", 55, 2000, 0.12f, 50);
            sleep(800);
            // Final thud — arrival impact
            playNoise(80, 0.30f);
            playSweep(60, 30, 200, 0.25f);
        }).start();
    }

    /** War March (Umbryn → Bellorak, ~5 s): war drums, lightning crack, marching rhythm, final crash. */
    public void playWarMarch() {
        new Thread(() -> {
            // War drums — 6 hits with increasing intensity
            for (int i = 0; i < 6; i++) {
                float vol = 0.18f + i * 0.04f;
                playNoise(40, vol);
                playSweep(80, 40, 60, vol * 0.8f);
                sleep(500 - i * 40);
            }
            // Lightning crack
            playNoise(30, 0.40f);
            playSweep(3000, 200, 100, 0.30f);
            sleep(200);
            // Marching rhythm — short metallic pings
            for (int i = 0; i < 8; i++) {
                playWaveform("square", 180, 30, 0.15f, 50);
                sleep(180);
            }
            // Final crash — impact
            playNoise(120, 0.38f);
            playTone(100, 200, 0.28f);
        }).start();
    }

    /** Ascension (Bellorak → Cradle, ~6 s): rising tone, 7-god arpeggio, convergence, blinding arrival. */
    public void playAscension() {
        new Thread(() -> {
            // Rising tone — slow upward sweep
            playSweep(100, 800, 2000, 0.18f);
            sleep(1000);
            // Seven god chimes — ascending arpeggio
            int[] godFreqs = {440, 523, 587, 659, 740, 830, 988};
            for (int f : godFreqs) {
                playWaveform("triangle", f, 300, 0.20f, 50);
                sleep(250);
            }
            // Convergence — all tones layered (rapid)
            for (int f : godFreqs) {
                playWaveform("triangle", f, 600, 0.10f, 50);
            }
            sleep(600);
            // Blinding arrival — bright upward sweep + harmonic
            playSweep(400, 2400, 500, 0.30f);
            playWaveform("triangle", 1200, 800, 0.15f, 50);
            sleep(400);
            // Fade — gentle low hum
            playWaveform("triangle", 220, 1200, 0.08f, 50);
        }).start();
    }

    public void playAltarChime() {
        if (playCached("altarChime")) return;
        new Thread(() -> {
            playWaveform("triangle", 523, 80, 0.30f, 50); sleep(80);
            playWaveform("triangle", 659, 100, 0.32f, 50); sleep(100);
            playWaveform("triangle", 784, 120, 0.34f, 50); sleep(120);
            playWaveform("triangle", 1047, 200, 0.36f, 50);
        }).start();
    }

    // ====================== ENDGAME SOUNDS ======================

    private volatile boolean epiloguePlaying = false;
    private volatile boolean creditsPlaying  = false;

    /** Ascending D-minor chord sequence (~3 s) for the Cradle awakening. */
    public void playCradleAwaken() {
        new Thread(() -> {
            String tw = "triangle";
            // Low drone
            playNote("D2", 3000, 0.12f, tw);
            sleep(100);
            // Ascending sequence
            playNote("D3", 300, 0.20f, tw); sleep(320);
            playNote("F3", 300, 0.22f, tw); sleep(320);
            playNote("A3", 300, 0.24f, tw); sleep(320);
            playNote("D4", 300, 0.26f, tw); sleep(320);
            playNote("F4", 300, 0.26f, tw); sleep(320);
            playNote("A4", 600, 0.28f, tw);
        }).start();
    }

    /** Dramatic low-D impact + rising sweep for boss phase transitions. */
    public void playPhaseTransition() {
        new Thread(() -> {
            // Impact
            playNoise(60, 0.40f);
            playNote("D2", 200, 0.35f, "square");
            sleep(250);
            // Rising sweep
            playNote("D3", 120, 0.20f, "triangle"); sleep(130);
            playNote("F3", 120, 0.22f, "triangle"); sleep(130);
            playNote("A3", 120, 0.24f, "triangle"); sleep(130);
            playNote("D4", 200, 0.26f, "triangle");
        }).start();
    }

    /** 20-second D-major epilogue melody (stoppable). */
    public void playEpilogueTheme() {
        epiloguePlaying = true;
        musicContext = CH_EPILOGUE;   // track clips so stopEpilogueTheme() can really stop them
        new Thread(() -> {
            String tw = "triangle";
            int H = 600, Q = 300, E = 150, W = 1200;
            float mv = 0.22f, bv = 0.12f;

            // Bass drone
            playNote("D2", 20000, bv, tw);
            sleep(300);

            // Phrase 1: rising — the world rebuilt
            if (!epiloguePlaying) return;
            playNote("D4", Q, mv, tw); sleep(Q);
            if (!epiloguePlaying) return;
            playNote("F#4", Q, mv, tw); sleep(Q);
            if (!epiloguePlaying) return;
            playNote("A4", H, 0.24f, tw); sleep(H);
            if (!epiloguePlaying) return;
            playNote("D5", H, 0.26f, tw); sleep(H);

            sleep(400);
            if (!epiloguePlaying) return;

            // Phrase 2: gentle descent — acceptance
            playNote("D5", Q, 0.24f, tw); sleep(Q);
            if (!epiloguePlaying) return;
            playNote("C#5", E, 0.20f, tw); sleep(E);
            if (!epiloguePlaying) return;
            playNote("B4", E, 0.20f, tw); sleep(E);
            if (!epiloguePlaying) return;
            playNote("A4", H, 0.22f, tw); sleep(H);

            sleep(600);
            if (!epiloguePlaying) return;

            // Phrase 3: walking motif — steady steps
            playNote("D4", E, 0.18f, tw); sleep(E);
            if (!epiloguePlaying) return;
            playNote("E4", E, 0.18f, tw); sleep(E);
            if (!epiloguePlaying) return;
            playNote("F#4", E, 0.18f, tw); sleep(E);
            if (!epiloguePlaying) return;
            playNote("A4", Q, 0.20f, tw); sleep(Q);
            if (!epiloguePlaying) return;
            playNote("F#4", Q, 0.20f, tw); sleep(Q);
            if (!epiloguePlaying) return;
            playNote("D4", H, 0.22f, tw); sleep(H);

            sleep(600);
            if (!epiloguePlaying) return;

            // Phrase 4: repeat rising, octave higher — sunset climax
            playNote("A4", Q, 0.22f, tw); sleep(Q);
            if (!epiloguePlaying) return;
            playNote("D5", Q, 0.24f, tw); sleep(Q);
            if (!epiloguePlaying) return;
            playNote("F#5", H, 0.26f, tw); sleep(H);
            if (!epiloguePlaying) return;
            playNote("A5", W, 0.28f, tw); sleep(W);

            sleep(600);
            if (!epiloguePlaying) return;

            // Phrase 5: resolution — peaceful close
            playNote("F#5", Q, 0.22f, tw); sleep(Q);
            if (!epiloguePlaying) return;
            playNote("E5", Q, 0.20f, tw); sleep(Q);
            if (!epiloguePlaying) return;
            playNote("D5", W, 0.24f, tw);
            playChord(new String[]{"D3", "A3"}, W, 0.14f);
            sleep(W);
            if (!epiloguePlaying) return;

            // Final hold
            playNote("D4", 2000, 0.16f, tw);
            playNote("A3", 2000, 0.10f, tw);
            sleep(2000);
            epiloguePlaying = false;
            endContext(CH_EPILOGUE);
        }).start();
    }

    /** Stops the epilogue theme, including any long note already queued. */
    public void stopEpilogueTheme() {
        epiloguePlaying = false;
        endContext(CH_EPILOGUE);
        silenceChannel(CH_EPILOGUE);
    }

    /** Gentle ambient C-major chord loop for credits (~18 s, stoppable). */
    public void playCreditsTheme() {
        creditsPlaying = true;
        musicContext = CH_CREDITS;   // track clips so stopCreditsTheme() can really stop them
        new Thread(() -> {
            int W = 2000;
            float bv = 0.07f;

            while (creditsPlaying) {
                playChord(new String[]{"C3", "E3", "G3"}, W, bv);
                sleep(W + 200);
                if (!creditsPlaying) return;
                playChord(new String[]{"A2", "C3", "E3"}, W, bv);
                sleep(W + 200);
                if (!creditsPlaying) return;
                playChord(new String[]{"F2", "A2", "C3"}, W, bv);
                sleep(W + 200);
                if (!creditsPlaying) return;
                playChord(new String[]{"G2", "B2", "D3"}, W, bv);
                sleep(W + 200);
            }
            endContext(CH_CREDITS);
        }).start();
    }

    /** Stops the credits theme, including any chord already queued. */
    public void stopCreditsTheme() {
        creditsPlaying = false;
        endContext(CH_CREDITS);
        silenceChannel(CH_CREDITS);
    }

    public void dungeonDescent() {
        playSequence(
            new String[]{"A2", "-", "G2", "-", "E2", "-", "D2", "-", "C2", "-", "B1", "-", "A1"},
            new int[]{80, 20, 100, 20, 120, 20, 150, 20, 200, 20, 250, 20, 300},
            0.28f);
    }

    /**
     * Forces Java Sound to initialise by opening and playing a 100 ms silent clip
     * on a background thread, so the first real sound has no start-up latency.
     * Common SFX are additionally pre-opened by {@code initSoundCache()}.
     */
    public void warmup() {
        new Thread(() -> {
            try {
                byte[] quiet = silence(100);
                AudioFormat fmt = new AudioFormat(44100, 8, 1, true, false);
                AudioInputStream ais =
                    new AudioInputStream(new ByteArrayInputStream(quiet), fmt, quiet.length);
                Clip clip = AudioSystem.getClip();
                clip.open(ais);
                clip.addLineListener(ev -> {
                    if (ev.getType() == LineEvent.Type.STOP) clip.close();
                });
                clip.start();
            } catch (Exception ignored) {}
            customSounds();   // parse custom_sounds.json once, off the EDT
        }).start();
    }

    // ====================== PRE-GENERATED CLIP CACHE ======================

    private byte[] genBuffer(String type, int freq, int durMs, float vol, int pw) {
        if (durMs <= 0) return new byte[0];
        int sr = 44100;
        int samples = Math.max(1, sr * durMs / 1000);
        byte[] buf = new byte[samples];
        int fade = Math.min(sr * 8 / 1000, samples / 4);
        for (int i = 0; i < samples; i++) {
            double t = (double) i / sr;
            double s = switch (type) {
                case "square"   -> ((t * freq) % 1 < pw / 100.0) ? 1 : -1;
                case "saw"      -> (t * freq % 1) * 2 - 1;
                case "triangle" -> Math.abs(((t * freq) % 2) - 1) * 2 - 1;
                case "sine"     -> Math.sin(2 * Math.PI * t * freq);
                case "noise"    -> Math.random() * 2 - 1;   // playWaveform("noise", ...) used to be digital silence
                default -> 0.0;
            };
            double env = (i < fade) ? (double) i / fade
                       : (i > samples - fade) ? (double)(samples - i) / fade : 1.0;
            buf[i] = (byte)(s * 127 * vol * env);
        }
        return buf;
    }

    /**
     * Renders a square-wave sweep into one continuous buffer.
     * The frequency still advances once per 8 ms step (same glissando as before),
     * but the phase runs unbroken across step boundaries and a single 8 ms
     * fade-in / fade-out envelope covers the whole sweep instead of gating every
     * slice — the old per-slice fade halved the level 125 times a second.
     */
    private byte[] genSweep(int startFreq, int endFreq, int durMs, float vol) {
        if (durMs <= 0) return new byte[0];
        final int sr = 44100;
        int samples     = Math.max(1, sr * durMs / 1000);
        int stepSamples = Math.max(1, sr * 8 / 1000);      // frequency updates every 8 ms
        int steps       = Math.max(1, durMs / 8);
        double freqStep = (endFreq - startFreq) / (double) steps;

        byte[] buf = new byte[samples];
        int fade   = Math.min(sr * 8 / 1000, samples / 4);
        double phase = 0.0;
        for (int i = 0; i < samples; i++) {
            int step  = Math.min(steps - 1, i / stepSamples);
            int freq  = Math.max(1, (int)(startFreq + freqStep * step));
            phase    += (double) freq / sr;                // continuous — no step clicks
            double s  = (phase % 1.0 < 0.5) ? 1 : -1;      // square, 50% duty
            double env = (fade <= 0) ? 1.0
                       : (i < fade) ? (double) i / fade
                       : (i > samples - fade) ? (double)(samples - i) / fade : 1.0;
            buf[i] = (byte)(s * 127 * vol * env);
        }
        return buf;
    }

    private byte[] genNoise(int durMs, float vol) {
        if (durMs <= 0) return new byte[0];
        int samples = Math.max(1, 44100 * durMs / 1000);
        byte[] buf = new byte[samples];
        for (int i = 0; i < samples; i++) {
            buf[i] = (byte)((Math.random() * 2 - 1) * 127 * vol);
        }
        return buf;
    }

    private byte[] silence(int durMs) {
        return new byte[Math.max(0, 44100 * durMs / 1000)];
    }

    private byte[] mix(byte[]... layers) {
        int len = 0;
        for (byte[] l : layers) len = Math.max(len, l.length);
        byte[] out = new byte[len];
        for (byte[] l : layers) {
            for (int i = 0; i < l.length; i++) {
                out[i] = (byte) Math.max(-127, Math.min(127, out[i] + l[i]));
            }
        }
        return out;
    }

    private byte[] concat(byte[]... parts) {
        int total = 0;
        for (byte[] p : parts) total += p.length;
        byte[] out = new byte[total];
        int pos = 0;
        for (byte[] p : parts) {
            System.arraycopy(p, 0, out, pos, p.length);
            pos += p.length;
        }
        return out;
    }

    private byte[] zeroPad(byte[] buf, int targetMs) {
        int target = 44100 * targetMs / 1000;
        if (buf.length >= target) return buf;
        byte[] out = new byte[target];
        System.arraycopy(buf, 0, out, 0, buf.length);
        return out;
    }

    private void cache(String name, byte[] buf) {
        try {
            AudioFormat fmt = new AudioFormat(44100, 8, 1, true, false);
            AudioInputStream ais = new AudioInputStream(new ByteArrayInputStream(buf), fmt, buf.length);
            Clip clip = AudioSystem.getClip();
            clip.open(ais);
            openedClips.put(name, clip);
        } catch (Exception ignored) {}
    }

    private void initSoundCache() {
        cache("step",       genBuffer("square", 240, 45, 0.22f, 50));
        new Thread(() -> {
            cache("menublip",   genBuffer("square", 880, 25, 0.30f, 50));
            cache("spell",      genSweep(800, 1400, 60, 0.32f));
            cache("laser",      genSweep(1350, 420, 70, 0.30f));
            cache("sword",      genSweep(900, 400, 80, 0.30f));
            cache("attack",     mix(zeroPad(genNoise(20, 0.35f), 50),
                                    genBuffer("square", 520, 50, 0.28f, 50)));
            cache("hit",        mix(zeroPad(genNoise(25, 0.30f), 60),
                                    genBuffer("square", 380, 60, 0.25f, 50)));
            cache("hurt",       mix(zeroPad(genSweep(600, 200, 120, 0.40f), 120),
                                    zeroPad(genNoise(50, 0.22f), 120)));
            cache("death",      mix(genBuffer("triangle", 180, 600, 0.28f, 50),
                                    genBuffer("triangle", 90, 600, 0.22f, 50)));
            cache("victory",    concat(genBuffer("square", 520, 70, 0.28f, 50),
                                       genBuffer("square", 680, 70, 0.28f, 50),
                                       genBuffer("square", 840, 70, 0.28f, 50),
                                       genBuffer("square", 1240, 260, 0.28f, 50)));
            cache("levelup",    concat(genBuffer("square", 720, 80, 0.32f, 50),
                                       genBuffer("square", 960, 80, 0.32f, 50),
                                       genBuffer("square", 1280, 120, 0.32f, 50)));
            cache("powerup",    concat(genBuffer("square", 620, 60, 0.32f, 50),
                                       genBuffer("square", 880, 60, 0.32f, 50),
                                       genBuffer("square", 1240, 140, 0.32f, 50)));
            cache("spellcast",  concat(genBuffer("square", 600, 40, 0.30f, 50),
                                       genBuffer("square", 900, 40, 0.30f, 50),
                                       genBuffer("square", 1200, 60, 0.30f, 50)));
            cache("chain",      concat(genBuffer("square", 780, 40, 0.35f, 50),
                                       genBuffer("square", 1120, 40, 0.35f, 50),
                                       genBuffer("square", 1480, 80, 0.35f, 50)));
            cache("coin",       concat(genBuffer("square", 1200, 20, 0.30f, 50),
                                       silence(15),
                                       genBuffer("square", 1800, 40, 0.35f, 50)));
            cache("heal",       concat(genBuffer("sine", 440, 50, 0.30f, 50),
                                       genBuffer("triangle", 660, 60, 0.32f, 50),
                                       genBuffer("sine", 880, 100, 0.35f, 50)));
            cache("altarChime", concat(genBuffer("triangle", 523, 80, 0.30f, 50),
                                       genBuffer("triangle", 659, 100, 0.32f, 50),
                                       genBuffer("triangle", 784, 120, 0.34f, 50),
                                       genBuffer("triangle", 1047, 200, 0.36f, 50)));
            cache("arrow",      concat(genBuffer("square", 1000, 15, 0.28f, 50),
                                       silence(10),
                                       genSweep(800, 200, 90, 0.30f)));
            cache("missile",    concat(genSweep(600, 1200, 45, 0.30f),
                                       silence(35),
                                       genBuffer("triangle", 1500, 25, 0.25f, 50)));
            cache("dragon",     mix(genSweep(400, 100, 500, 0.38f),
                                    zeroPad(genNoise(500, 0.22f), 500)));
            cache("textblip",   genBuffer("triangle", 600, 14, 0.13f, 50));
            customSounds();   // parse custom_sounds.json once, here, never on the EDT
        }).start();
    }

    public void playEnterTown() {
        new Thread(() -> {
            int Q = 350;  // quarter note (~171 BPM)
            int E = 175;  // eighth note
            int H = 700;  // half note

            // Bass D pedal beneath the whole fanfare
            playNote("D3", 3000, 0.18f, "triangle");

            // Phrase 1 — herald call: rising D major arpeggio (gate looms into view)
            playNote("D4",  Q, 0.30f); sleep(Q);
            playNote("F#4", Q, 0.30f); sleep(Q);
            playNote("A4",  Q, 0.30f); sleep(Q);
            playNote("D5",  H, 0.32f); sleep(H);

            // Phrase 2 — ascending fill as portcullis rises and arch approaches
            playNote("A4",  E, 0.26f); sleep(E);
            playNote("B4",  E, 0.26f); sleep(E);
            playNote("C#5", E, 0.26f); sleep(E);
            playNote("D5",  E, 0.26f); sleep(E);

            // Resolution — D5 held melody + D major chord (town interior revealed)
            playNote("D5", H + Q, 0.28f);
            playChord(new String[]{"D4", "F#4", "A4"}, H + Q, 0.20f);
        }).start();
    }

    public void playFountain() {
        new Thread(() -> {
            // Sparkling magical fountain — pure 1980s retro magic
            playTone(520, 80, 0.28f);   // bubbling start
            sleep(50);
            playTone(780, 120, 0.32f);  // rising sparkle
            sleep(80);
            playTone(640, 200, 0.25f);  // gentle fall
            sleep(60);
            playTone(920, 90, 0.22f);   // final shimmer
        }).start();
    }

    public void playChestOpen() {
        new Thread(() -> {
            // Classic creaky wooden chest + coin jingle
            playTone(180, 120, 0.25f);   // heavy lid creak
            sleep(80);
            playTone(420, 60, 0.22f);    // lid opens
            sleep(40);
            playArpeggio(new int[]{680,920,1240}, new int[]{30,40,80}, 0.35f); // coins!
        }).start();
    }

    public void playTrap() {
        new Thread(() -> {
            playTone(280, 180, 0.4f);    // painful sting
            sleep(60);
            playNoise(140, 0.35f);       // poison hiss
        }).start();
    }
    public void playCube() {
        new Thread(() -> {
            // Mysterious glowing cube — pure retro magic
            playTone(420, 140, 0.25f);   // low hum
            sleep(70);
            playTone(680, 90, 0.32f);    // rising energy
            sleep(50);
            playTone(920, 180, 0.28f);   // bright pulse
            sleep(60);
            playTone(1240, 70, 0.22f);   // final magical zap
        }).start();
    }

    public void playThrone() {
        new Thread(() -> {
            // Majestic royal throne sound — deep and powerful
            playTone(280, 140, 0.30f);   // deep throne rumble
            sleep(90);
            playArpeggio(new int[]{520, 680, 920, 1240}, new int[]{80, 100, 120, 240}, 0.35f); // royal fanfare
            sleep(60);
            playTone(640, 180, 0.28f);   // echoing final note
        }).start();
    }

    // ====================== TITLE THEME (SYNTH) ======================

    /**
     * Starts the soft ambient menu theme looping continuously using the synth engine.
     * C-major, ~63 BPM, sine/triangle waves — gentle and pleasant.
     * Loops until {@link #stopTitleTheme()} is called. Safe to call from the EDT.
     */
    public void playTitleTheme() {
        synchronized (musicLock) {
            if (titlePlaying) return;
            titlePlaying = true;
            musicContext = CH_TITLE;
            final int gen = ++titleGeneration;   // supersedes any fade-out still running
            titleThread = new Thread(() -> {
                while (titleLive(gen)) {
                    playTitleOnce(gen);
                }
            });
            titleThread.start();
        }
    }

    /** True while the title pass identified by {@code gen} is still the current one. */
    private boolean titleLive(int gen) {
        return titlePlaying && gen == titleGeneration;
    }

    /** Stops the title theme. Safe to call even if music is not playing. */
    public void stopTitleTheme() {
        synchronized (musicLock) {
            titlePlaying = false;
            titleGeneration++;                   // any in-flight fade must not clean up after us
            endContext(CH_TITLE);
            if (titleThread != null) {
                titleThread.interrupt();
                titleThread = null;
            }
        }
        silenceChannel(CH_TITLE);
    }

    /**
     * Fades out the title theme over the given duration, then stops it.
     * Safe to call from the EDT — runs fade on a background thread.
     */
    public void fadeOutTitleTheme(int durationMs) {
        fadeOutTitleTheme(durationMs, null);
    }

    public void fadeOutTitleTheme(int durationMs, Runnable onComplete) {
        final int gen;
        synchronized (musicLock) {
            titlePlaying = false;      // signal sub-threads to exit — no new notes
            gen = titleGeneration;
            // NOTE: the channel stays current for the whole fade. Clearing it up
            // front (as this used to) left notes already in flight untracked, so
            // ~3.8 s of title bass played on past the fade at full volume.
        }
        new Thread(() -> {
            int steps = Math.max(1, durationMs / 20);
            for (int i = 0; i < steps; i++) {
                if (gen != titleGeneration) break;      // a new title theme took over
                float fraction = 1.0f - (float)(i + 1) / steps;
                for (Clip c : channel(CH_TITLE)) {
                    try { if (c.isOpen()) applyVolume(c, fraction); } catch (Exception ignored) {}
                }
                sleep(20);
            }
            // Clean up — but only if this fade is still the current owner of the theme.
            boolean superseded;
            synchronized (musicLock) {
                superseded = (gen != titleGeneration);
                if (!superseded) {
                    titleGeneration++;
                    endContext(CH_TITLE);
                    if (titleThread != null) {
                        titleThread.interrupt();
                        titleThread = null;
                    }
                }
            }
            if (!superseded) silenceChannel(CH_TITLE);
            if (onComplete != null) onComplete.run();
        }).start();
    }

    /**
     * Plays one 8-bar pass of the soft ambient menu theme.
     * C major, ~63 BPM, sine/triangle waves at gentle volumes.
     * Progression: C – G – Am – F – C – Em – F – G→C.
     * Called in a loop by {@link #playTitleTheme()}.
     */
    private void playTitleOnce(final int gen) {
        // 8 bars of 4/4 at Q=960ms (~63 BPM) — very slow, peaceful
        int Q = 960, H = 1920, W = 3840;
        float mv = 0.18f;
        String sw = "sine";

        // === Deep slow bass — one whole-note per bar (triangle, very quiet) ===
        new Thread(() -> {
            float bv = 0.09f; String bw = "triangle";
            playNote("C2", W, bv, bw); sleep(W); if (!titleLive(gen)) return;
            playNote("G2", W, bv, bw); sleep(W); if (!titleLive(gen)) return;
            playNote("A2", W, bv, bw); sleep(W); if (!titleLive(gen)) return;
            playNote("F2", W, bv, bw); sleep(W); if (!titleLive(gen)) return;
            playNote("C2", W, bv, bw); sleep(W); if (!titleLive(gen)) return;
            playNote("E2", W, bv, bw); sleep(W); if (!titleLive(gen)) return;
            playNote("F2", W, bv, bw); sleep(W); if (!titleLive(gen)) return;
            playNote("G2", H, bv, bw); sleep(H);
            playNote("C2", H, bv, bw); sleep(H);
        }).start();

        // === Gentle rising arpeggios — one 4-note arp per bar (sine, very soft) ===
        new Thread(() -> {
            float cv = 0.07f; String cw = "sine";
            String[][] arps = {
                {"C3","E3","G3","E3"}, // bar 1: C major
                {"G3","B3","D4","B3"}, // bar 2: G major
                {"A3","C4","E4","C4"}, // bar 3: A minor
                {"F3","A3","C4","A3"}, // bar 4: F major
                {"C3","E3","G3","E3"}, // bar 5: C major
                {"E3","G3","B3","G3"}, // bar 6: E minor
                {"F3","A3","C4","A3"}, // bar 7: F major
                {"G3","B3","D4","B3"}, // bar 8: G major
            };
            for (String[] arp : arps) {
                for (String note : arp) {
                    if (!titleLive(gen)) return;
                    playNote(note, Q, cv, cw);
                    sleep(Q);
                }
            }
        }).start();

        // === Melody — flowing, singable (sine, this thread drives timing) ===
        // Guard before every note so a thread interrupt can't sneak a stray note through.
        // Bar 1 (C):  E4 half, G4 half
        if (!titleLive(gen)) return; playNote("E4", H, mv, sw); sleep(H);
        if (!titleLive(gen)) return; playNote("G4", H, mv, sw); sleep(H);
        // Bar 2 (G):  D4 quarter, rest quarter, D4 half
        if (!titleLive(gen)) return; playNote("D4", Q, mv, sw); sleep(Q);
        sleep(Q);
        if (!titleLive(gen)) return; playNote("D4", H, mv, sw); sleep(H);
        // Bar 3 (Am): C4 quarter, E4 quarter, C4 half
        if (!titleLive(gen)) return; playNote("C4", Q, mv, sw); sleep(Q);
        if (!titleLive(gen)) return; playNote("E4", Q, mv, sw); sleep(Q);
        if (!titleLive(gen)) return; playNote("C4", H, mv, sw); sleep(H);
        // Bar 4 (F):  A3 half, F3 half (step down for contrast)
        if (!titleLive(gen)) return; playNote("A3", H, mv, sw); sleep(H);
        if (!titleLive(gen)) return; playNote("F3", H, mv, sw); sleep(H);
        // Bar 5 (C):  G4 quarter, A4 quarter, G4 half
        if (!titleLive(gen)) return; playNote("G4", Q, mv, sw); sleep(Q);
        if (!titleLive(gen)) return; playNote("A4", Q, mv, sw); sleep(Q);
        if (!titleLive(gen)) return; playNote("G4", H, mv, sw); sleep(H);
        // Bar 6 (Em): E4 half, B3 half
        if (!titleLive(gen)) return; playNote("E4", H, mv, sw); sleep(H);
        if (!titleLive(gen)) return; playNote("B3", H, mv, sw); sleep(H);
        // Bar 7 (F):  C4 D4 E4 F4 — gentle ascending quarter-note run
        if (!titleLive(gen)) return; playNote("C4", Q, mv, sw); sleep(Q);
        if (!titleLive(gen)) return; playNote("D4", Q, mv, sw); sleep(Q);
        if (!titleLive(gen)) return; playNote("E4", Q, mv, sw); sleep(Q);
        if (!titleLive(gen)) return; playNote("F4", Q, mv, sw); sleep(Q);
        // Bar 8 (G→C): G4 half, C4 half (peaceful resolve)
        if (!titleLive(gen)) return; playNote("G4", H, mv, sw); sleep(H);
        if (!titleLive(gen)) return; playNote("C4", H, mv, sw); sleep(H);
    }

    // ====================== CORE ENGINE ======================
    public void playTone(int freq, int durationMs, float volume) {
        playWaveform("square", freq, durationMs, volume, 50);
    }

    public void playWaveform(String type, int freq, int durationMs, float volume, int pulseWidth) {
        new Thread(() -> generateAndPlay(type, freq, durationMs, volume, pulseWidth)).start();
    }

    public void playNoise(int durationMs, float volume) {
        new Thread(() -> {
            if (durationMs <= 0) return;
            int sr = 44100;
            long samplesLong = (long) sr * durationMs / 1000;
            if (samplesLong > Integer.MAX_VALUE / 2) samplesLong = Integer.MAX_VALUE / 2;

            byte[] buf = new byte[(int) samplesLong];
            for (int i = 0; i < buf.length; i++) {
                buf[i] = (byte)((Math.random()*2-1)*127*volume);
            }
            playBuffer(buf);
        }).start();
    }

    /**
     * Frequency sweep. Rendered into a single buffer and played as one clip —
     * the old version opened a thread and a Clip every 8 ms (≈400 of each for the
     * teleport whoosh) and applied a full fade-in/out to every 8 ms slice, which
     * amplitude-gated the sweep at 125 Hz. The stepped retro glissando is kept:
     * the frequency still advances in 8 ms steps, only the phase is continuous
     * and the envelope is applied once across the whole sweep.
     */
    public void playSweep(int startFreq, int endFreq, int durationMs, float volume) {
        if (durationMs <= 0) return;
        new Thread(() -> playBuffer(genSweep(startFreq, endFreq, durationMs, volume))).start();
    }

    /**
     * Plays a ~45-second dark intro theme in A minor.
     * Clean and atmospheric — at most 2 simultaneous voices, wider note spacing,
     * and low volumes to avoid mixer clipping. Triangle wave throughout.
     * Call {@link #stopIntroTheme()} to halt cleanly at any point.
     */
    public void playIntroTheme() {
        introPlaying = true;
        musicContext = CH_INTRO;   // track the clips so stopIntroTheme() can really stop them
        introThread = new Thread(() -> {
            String tw = "triangle";

            // === Opening: sparse Am arpeggios, single voice, slowly building ===
            for (int i = 0; i < 10; i++) {
                if (!introPlaying) return;
                float v = 0.10f + i * 0.007f;
                playNote("A3", 200, v, tw); sleep(400);
                if (!introPlaying) return;
                playNote("C4", 200, v, tw); sleep(400);
                if (!introPlaying) return;
                playNote("E4", 200, v, tw); sleep(400);
                if (!introPlaying) return;
                sleep(200); // breath between cycles
            }

            // === Section 2: Bass enters, melody over sparse arpeggios ===
            // Bass (short, won't persist too long)
            playNote("A2", 6000, 0.09f, tw);
            sleep(500);
            if (!introPlaying) return;

            String[] mel1 = {
                "E5", "-",  "D5", "C5", "-",  "B4",
                "A4", "-",  "C5", "B4", "A4", "-"
            };
            int[] md1 = {500,300, 400,500,300, 400,
                         500,400, 400,400,600, 500};
            playSequence(mel1, md1, 0.18f, tw);
            if (!introPlaying) return;
            sleep(500);

            // === Section 3: Tension — E bass, Em arpeggios ===
            playNote("E2", 5000, 0.09f, tw);
            sleep(400);
            if (!introPlaying) return;

            for (int i = 0; i < 7; i++) {
                if (!introPlaying) return;
                float v = 0.12f + i * 0.008f;
                playNote("E3", 200, v, tw); sleep(380);
                if (!introPlaying) return;
                playNote("G3", 200, v, tw); sleep(380);
                if (!introPlaying) return;
                playNote("B3", 200, v, tw); sleep(380);
                if (!introPlaying) return;
                sleep(160);
            }
            if (!introPlaying) return;

            // === Section 4: Climax — Am, single melodic voice ===
            playNote("A2", 7000, 0.10f, tw);
            sleep(400);
            if (!introPlaying) return;

            String[] mel2 = {
                "A4", "-",  "C5", "E5", "-",  "A5",
                "-",  "G5", "E5", "-",  "C5", "A4", "-"
            };
            int[] md2 = {400,300, 400,500,300, 550,
                         300, 400,500,300, 400,550,400};
            playSequence(mel2, md2, 0.20f, tw);
            if (!introPlaying) return;
            sleep(400);

            // === Outro: descending line fading to silence ===
            String[] outro = {
                "E5", "D5", "C5", "B4", "A4", "-", "G4", "A4", "-", "A3"
            };
            int[] od = {500,400,500,400,600,300,400,600,400,1400};
            playSequence(outro, od, 0.14f, tw);
            if (!introPlaying) return;

            sleep(400);
            if (!introPlaying) return;
            playNote("A2", 2000, 0.08f, tw);
            sleep(2000);
            introPlaying = false;
            endContext(CH_INTRO);
        });
        introThread.start();
    }

    /** Stops the intro theme immediately. Safe to call at any time. */
    public void stopIntroTheme() {
        introPlaying = false;
        endContext(CH_INTRO);
        if (introThread != null) {
            introThread.interrupt();
            introThread = null;
        }
        silenceChannel(CH_INTRO);   // a flag flip cannot stop a clip that is already open
    }

    public void playArpeggio(int[] freqs, int[] durations, float volume) {
        new Thread(() -> {
            for (int i = 0; i < freqs.length; i++) {
                playTone(freqs[i], durations[i], volume);
                sleep(durations[i]);
            }
        }).start();
    }

    private void generateAndPlay(String type, int freq, int durationMs, float volume, int pulseWidth) {
        if (durationMs <= 0) return;

        int sr = 44100;
        // Use long to prevent overflow (handles up to ~49 seconds safely)
        long samplesLong = (long) sr * durationMs / 1000;

        // Optional safety cap (~24 seconds max per clip - still plenty for retro sounds)
        if (samplesLong > Integer.MAX_VALUE / 2) {
            samplesLong = Integer.MAX_VALUE / 2;
        }

        int samples = (int) samplesLong;
        byte[] buffer = new byte[samples];

        // 8ms micro-fade in/out to eliminate the click caused by hard waveform cutoffs
        int fadeSamples = Math.min(sr * 8 / 1000, samples / 4);

        for (int i = 0; i < samples; i++) {
            double t = (double)i / sr;
            double sample = switch (type) {
                case "square" -> ((t * freq) % 1 < pulseWidth / 100.0) ? 1 : -1;
                case "saw" -> (t * freq % 1) * 2 - 1;
                case "triangle" -> Math.abs(((t * freq) % 2) - 1) * 2 - 1;
                case "sine" -> Math.sin(2 * Math.PI * t * freq);
                case "noise" -> Math.random() * 2 - 1;   // playWaveform("noise", ...) used to be digital silence
                default -> 0;
            };
            double env = 1.0;
            if (i < fadeSamples)              env = (double) i / fadeSamples;
            else if (i > samples - fadeSamples) env = (double)(samples - i) / fadeSamples;
            buffer[i] = (byte)(sample * 127 * volume * env);
        }
        playBuffer(buffer);
    }

    // ====================== MUSICAL NOTE SYSTEM - FULL CHROMATIC SCALE (1980s/90s Chiptune Ready) ======================

    private static final double CONCERT_A = 440.0;
    private static final int MIDI_A4 = 69;

    /**
     * Convert any note in scientific pitch notation to frequency.
     * Full chromatic support: "C4", "C#5", "Db5", "F#3", "Bb4", "A#6", "G2", etc.
     * Octaves 1–7 cover everything a retro CRPG needs (low bass to bright leads).
     */
    public double noteToFrequency(String noteName) {
        if (noteName == null || noteName.trim().isEmpty()) return 0;
        String note = noteName.trim().toUpperCase();
        String pitchClass = note.replaceAll("\\d", "");           // "C#4" → "C#", "Db5" → "DB"
        int octave;
        try {
            octave = Integer.parseInt(note.replaceAll("\\D", "")); // "C#4" → 4
        } catch (NumberFormatException e) {
            return 0;
        }
        int semitones = getSemitones(pitchClass);
        int midiNote = (octave + 1) * 12 + semitones;             // C4 = MIDI 60
        return CONCERT_A * Math.pow(2.0, (midiNote - MIDI_A4) / 12.0);
    }

    private int getSemitones(String pitch) {
        return switch (pitch) {
            case "C"                 -> 0;
            case "C#", "DB"          -> 1;
            case "D"                 -> 2;
            case "D#", "EB"          -> 3;
            case "E"                 -> 4;
            case "F"                 -> 5;
            case "F#", "GB"          -> 6;
            case "G"                 -> 7;
            case "G#", "AB"          -> 8;
            case "A"                 -> 9;
            case "A#", "BB"          -> 10;
            case "B"                 -> 11;
            default -> throw new IllegalArgumentException("Unknown note: " + pitch);
        };
    }

    // ====================== NEW MUSICAL PLAY METHODS ======================

    /** Play a single chromatic note – default square wave (classic retro lead sound) */
    public void playNote(String note, int durationMs, float volume) {
        playNote(note, durationMs, volume, "square");
    }

    /** Play a single chromatic note with any waveform – triangle/sine sound way more musical! */
    public void playNote(String note, int durationMs, float volume, String waveform) {
        double freq = noteToFrequency(note);
        if (freq > 20) {
            playWaveform(waveform.toLowerCase(), (int)Math.round(freq), durationMs, volume, 50);
        }
    }

    /** MIDI note version (super handy for composing) – C4 = 60, A4 = 69, etc. */
    public void playMidiNote(int midiNote, int durationMs, float volume) {
        playMidiNote(midiNote, durationMs, volume, "square");
    }

    public void playMidiNote(int midiNote, int durationMs, float volume, String waveform) {
        if (midiNote < 12 || midiNote > 127) return;
        double freq = CONCERT_A * Math.pow(2.0, (midiNote - MIDI_A4) / 12.0);
        playWaveform(waveform.toLowerCase(), (int)Math.round(freq), durationMs, volume, 50);
    }

    /** Play multiple notes at the same time (chords!) */
    public void playChord(String[] notes, int durationMs, float volume) {
        float v = volume * 0.75f; // slight volume reduction so chords don't clip
        for (String n : notes) {
            playNote(n, durationMs, v);
        }
    }

    /** Arpeggio using note names (much nicer than raw frequencies) */
    public void playArpeggio(String[] notes, int[] durationsMs, float volume) {
        new Thread(() -> {
            for (int i = 0; i < notes.length; i++) {
                playNote(notes[i], durationsMs[i], volume);
                sleep(durationsMs[i]);
            }
        }).start();
    }

    /** Full melody sequence with rests – THIS IS WHAT YOU'LL USE FOR CLASSIC SONGS */
    public void playSequence(String[] notes, int[] durationsMs, float volume) {
        new Thread(() -> {
            for (int i = 0; i < notes.length; i++) {
                if (!notes[i].equals("-")) {          // "-" = rest / silence
                    playNote(notes[i], durationsMs[i], volume);
                }
                sleep(durationsMs[i]);
            }
        }).start();
    }

    /** Melody sequence with a specific waveform (triangle, sine, etc.) */
    public void playSequence(String[] notes, int[] durationsMs, float volume, String waveform) {
        new Thread(() -> {
            for (int i = 0; i < notes.length; i++) {
                if (!notes[i].equals("-")) {
                    playNote(notes[i], durationsMs[i], volume, waveform);
                }
                sleep(durationsMs[i]);
            }
        }).start();
    }

    /** Quick test: play the entire chromatic scale starting from any note */
    public void playChromaticScale(String startNote, int durationMs, float volume, int octaves) {
        new Thread(() -> {
            double freq = noteToFrequency(startNote);
            if (freq < 20) return;
            for (int o = 0; o < octaves; o++) {
                for (int i = 0; i < 12; i++) {
                    playWaveform("square", (int)Math.round(freq), durationMs, volume, 50);
                    sleep(durationMs + 20);
                    freq *= 1.059463094; // exact semitone ratio
                }
            }
        }).start();
    }

    private void playBuffer(byte[] buffer) {
        if (muted || buffer == null || buffer.length == 0) return;
        try {
            AudioFormat format = new AudioFormat(44100, 8, 1, true, false);
            AudioInputStream ais = new AudioInputStream(new ByteArrayInputStream(buffer), format, buffer.length);
            Clip clip = AudioSystem.getClip();
            clip.open(ais);
            applyVolume(clip, 1f);
            String ch = musicContext;
            if (ch != null) {
                // Track it so stop/fade can silence a note that is already queued.
                List<Clip> tracked = channel(ch);
                tracked.add(clip);
                clip.addLineListener(event -> {
                    if (event.getType() == LineEvent.Type.STOP) {
                        tracked.remove(clip);
                        clip.close();
                    }
                });
            } else {
                clip.addLineListener(event -> {
                    if (event.getType() == LineEvent.Type.STOP) {
                        clip.close();
                    }
                });
            }
            clip.start();
        } catch (Exception ignored) {}
    }

    private void sleep(int ms) {
        try { Thread.sleep(ms); } catch (Exception ignored) {}
    }

}

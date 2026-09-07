import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Renders the RetroQuest hype video's score to WAV.
 *
 * <p>The game has no audio files — {@code SoundManager} synthesizes every sound at runtime from
 * square, triangle, saw and noise at 44100Hz. This tool uses the same four voices for the same
 * reason: a sampled orchestral bed under 32x32 sprite art would sound borrowed. Everything here
 * is generated, so the score belongs to the game rather than sitting on top of it.
 *
 * <p>Four channels, laid out like a tracker:
 * <ul>
 *   <li><b>PULSE1</b> — the lead. 25% duty, panned slightly right.</li>
 *   <li><b>PULSE2</b> — harmony a third or sixth below. 50% duty, panned slightly left.</li>
 *   <li><b>TRI</b> — bass. Triangle, centre. This is the NES bass channel and it does the same job.</li>
 *   <li><b>NOISE</b> — percussion. Filtered noise bursts; a "kick" is a low-rate noise, a hat is
 *       a short high-rate one.</li>
 * </ul>
 *
 * <p>Three states, cued to the act boundaries in {@code docs/video/01-SCRIPT.md}:
 * <b>A Statement</b> (0:08–1:30, 140 BPM, A minor), <b>B Descent</b> (1:31–2:23, a drone with no
 * percussion at all), <b>C Forge</b> (2:35–3:15, the same melody at 160 BPM in A major). The point
 * of B is that the viewer notices the music left; the point of C is that it is the tune they
 * already heard, resolved.
 *
 * <pre>  javac -d out/audio tools/ChiptuneScore.java &amp;&amp; java -cp out/audio ChiptuneScore out/audio</pre>
 */
public final class ChiptuneScore {

    static final int SR = 44100;
    static final double BPM_A = 140.0, BPM_C = 160.0;
    static final double BEAT_A = 60.0 / BPM_A;      // 0.4286s
    static final double BEAT_C = 60.0 / BPM_C;      // 0.3750s

    // Act cues (seconds) — must track docs/video/01-SCRIPT.md.
    static final double A0 = 0, A1 = 8, A2 = 33, A3 = 65, A4 = 91, A5 = 143, A6 = 155, A7 = 185, END = 195;

    // ── The buffer ───────────────────────────────────────────────────────────
    static double[] L, R;

    public static void main(String[] args) throws Exception {
        File dir = new File(args.length > 0 ? args[0] : "out/audio");
        if (!dir.isDirectory() && !dir.mkdirs()) { System.err.println("cannot create " + dir); System.exit(1); }

        int n = (int) (END * SR) + SR;
        L = new double[n]; R = new double[n];

        act0Boot();
        stateA();
        act3Combat();
        stateB();
        act5Turn();
        stateC();
        powerOff();

        normalise(0.89);
        writeWav(new File(dir, "score.wav"), L, R);
        System.out.printf("score.wav  %.1fs stereo 16-bit %dHz%n", END, SR);

        oneShots(dir);
        System.out.println("done — " + dir.getAbsolutePath());
    }

    // ═══ ACT 0 — the CRT boots ════════════════════════════════════════════════
    // 60Hz mains hum, a degauss thunk, one key clack per boot-text character, and the
    // three-note minor stinger on the title at 0:05.
    static void act0Boot() {
        hum(0.6, A1 - 0.2, 0.055);
        thunk(1.0);
        // Six lines typing from 1.4s at 0.55s a line, ~22ms a character.
        String[] lines = { "CANNONFORGE SYSTEMS", "RETROQUEST v1.0.0", "JAVA RUNTIME OK ...... 148 CLASSES",
                           "TILE REGISTRY ........ 192", "AUDIO ................ SYNTHESIZED", "READY." };
        for (int i = 0; i < lines.length; i++)
            for (int c = 0; c < lines[i].length(); c++)
                clack(1.4 + i * 0.55 + c * 0.022);

        // Title stinger: A minor, root-fifth-octave, square lead over a triangle root.
        double t = 5.0;
        note(PULSE1, t + 0.00, 0.16, "A4", 0.34, 0.25);
        note(PULSE1, t + 0.16, 0.16, "E5", 0.34, 0.25);
        note(PULSE1, t + 0.32, 0.90, "A5", 0.38, 0.25);
        note(TRI,    t + 0.00, 1.30, "A2", 0.42, 0);
        noiseHit(t + 0.32, 0.30, 0.30, 6000);
    }

    // ═══ STATE A — Statement. 140 BPM, A minor. ═══════════════════════════════
    // Bass and hat first; the lead enters at 0:14 and the arrangement fills through Act 2.
    // The eight-bar melody is the tune Act 6 resolves into a major key.
    static final String[] MELODY = {
        "A4","C5","E5","C5",  "D5","C5","B4","A4",
        "G4","B4","D5","B4",  "E5","D5","C5","B4",
        "A4","C5","E5","A5",  "G5","E5","D5","C5",
        "F4","A4","C5","E5",  "E5","D5","C5","A4",
    };
    static final String[] HARM = {
        "E4","A4","C5","A4",  "B4","A4","G4","E4",
        "D4","G4","B4","G4",  "C5","B4","A4","G4",
        "E4","A4","C5","E5",  "D5","C5","B4","A4",
        "C4","F4","A4","C5",  "B4","A4","G4","E4",
    };
    static final String[] BASS_A = { "A2","A2","F2","F2","G2","G2","E2","E2" };   // one per bar

    static void stateA() {
        double t = A1;
        int bar = 0;
        while (t < A3 - 0.4) {
            double barLen = BEAT_A * 4;
            boolean lead    = t >= A1 + 6;          // lead enters at 0:14
            boolean harmony = t >= A2;              // harmony from Act 2
            boolean dbl     = t >= A2 + 20 && t < A2 + 27;   // double-time under the S13 montage

            bassBar(t, BEAT_A, BASS_A[bar % BASS_A.length], 0.34);
            hatBar(t, BEAT_A, dbl ? 8 : 4, 0.16);
            if (bar % 2 == 0) kick(t, 0.34);
            kick(t + BEAT_A * 2, 0.28);

            if (lead) {
                for (int i = 0; i < 4; i++) {
                    int idx = (bar * 4 + i) % MELODY.length;
                    double d = BEAT_A * (i == 3 ? 0.9 : 0.82);
                    note(PULSE1, t + i * BEAT_A, d, MELODY[idx], 0.30, 0.25);
                    if (harmony) note(PULSE2, t + i * BEAT_A, d, HARM[idx], 0.17, 0.5);
                }
            }
            t += barLen; bar++;
        }
    }

    // ═══ ACT 3 — the same theme, harder, and it stops dead ════════════════════
    // "Some of them don't let you leave." The music cuts on the last word: nothing after 90.4.
    static void act3Combat() {
        double stop = A4 - 0.6;

        // The encounter sting, on the cut rather than on the act cue. Act 3 opens with 1.4s of
        // overworld before the flash and the slide-in, so the hit belongs at 66.4 — see the shot
        // table in tools/render-film.sh, where s09-overworld runs 65.0 to 66.5.
        double enc = A3 + 1.4;
        note(PULSE1, enc + 0.00, 0.07, "A4", 0.34, 0.125);
        note(PULSE1, enc + 0.07, 0.07, "D5", 0.34, 0.125);
        note(PULSE1, enc + 0.14, 0.07, "G5", 0.34, 0.125);
        note(PULSE1, enc + 0.21, 0.45, "A5", 0.38, 0.125);
        noiseHit(enc + 0.21, 0.30, 0.32, 5000);

        double t = A3; int bar = 0;
        while (t < stop) {
            bassBar(t, BEAT_A, BASS_A[bar % BASS_A.length], 0.40);
            hatBar(t, BEAT_A, 8, 0.13);
            kick(t, 0.40); kick(t + BEAT_A * 2, 0.34);
            for (int i = 0; i < 4; i++) {
                int idx = (bar * 4 + i) % MELODY.length;
                note(PULSE1, t + i * BEAT_A, BEAT_A * 0.8, MELODY[idx], 0.26, 0.125);  // thinner duty = reedier
                note(PULSE2, t + i * BEAT_A, BEAT_A * 0.8, HARM[idx], 0.15, 0.5);
            }
            t += BEAT_A * 4; bar++;
        }
        noiseHit(stop, 0.5, 0.34, 2200);   // the door closing on the act
    }

    // ═══ STATE B — Descent. No percussion for fifty-two seconds. ══════════════
    // A drone on the theme's root with a slowly detuned partner, so it beats against itself.
    // It swells once, under the Cradle at 2:18, and then stops dead into the silence.
    static void stateB() {
        double t0 = A4, t1 = A5;
        double f = midi(noteMidi("A1"));
        for (int i = (int) (t0 * SR); i < (int) (t1 * SR); i++) {
            double t = i / (double) SR;
            double p = (t - t0) / (t1 - t0);
            double env = Math.min(1, (t - t0) / 3.0) * Math.min(1, (t1 - t) / 1.2);
            double swell = 1 + 1.15 * Math.max(0, (t - (t1 - 5)) / 5.0);   // the Cradle
            double a = tri(f, t) * 0.30;
            double b = tri(f * 1.0037, t) * 0.24;                           // detune → slow beating
            double c = tri(f * 2, t) * 0.10 * (0.5 + 0.5 * Math.sin(2 * Math.PI * 0.06 * t));
            double s = (a + b + c) * env * swell * 0.5;
            L[i] += s; R[i] += s * 0.94;
        }
        // Three sparse bell tones, one per renderer rung, so the act is not featureless.
        note(PULSE2, A4 + 13, 2.2, "A4", 0.085, 0.5);
        note(PULSE2, A4 + 27, 2.2, "C5", 0.075, 0.5);
        note(PULSE2, A4 + 42, 2.6, "E5", 0.085, 0.5);
    }

    // ═══ ACT 5 — the turn. Two seconds of nothing, then the hum returns. ══════
    static void act5Turn() {
        hum(A5 + 2.0, A6 + 1.0, 0.05);         // silence from 2:23 to 2:25 is left genuinely empty
        clack(A5 + 4.4); clack(A5 + 4.7);      // the two lines typing
        thunk(A5 + 8.0);                        // RetroForge boots
    }

    // ═══ STATE C — Forge. The same melody at 160 BPM, in A major. ═════════════
    static void stateC() {
        double t = A6, bar = 0;
        while (t < END - 1.6) {
            bassBar(t, BEAT_C, major(BASS_A[(int) bar % BASS_A.length]), 0.36);
            hatBar(t, BEAT_C, 8, 0.15);
            kick(t, 0.38); kick(t + BEAT_C * 2, 0.30);
            for (int i = 0; i < 4; i++) {
                int idx = ((int) bar * 4 + i) % MELODY.length;
                double d = BEAT_C * 0.85;
                note(PULSE1, t + i * BEAT_C, d, major(MELODY[idx]), 0.31, 0.5);
                note(PULSE2, t + i * BEAT_C, d, major(HARM[idx]),   0.19, 0.25);
            }
            t += BEAT_C * 4; bar++;
        }
        // Final chord, held under the lockup.
        note(TRI,    END - 1.9, 1.5, "A2", 0.42, 0);
        note(PULSE1, END - 1.9, 1.5, "A4", 0.26, 0.5);
        note(PULSE2, END - 1.9, 1.5, "C#5", 0.20, 0.5);
        note(PULSE2, END - 1.9, 1.5, "E5", 0.18, 0.5);
    }

    /** A minor melody raised to A major: the third and seventh go up a semitone. */
    static String major(String n) {
        String p = n.replaceAll("[0-9]", "");
        String oct = n.replaceAll("[^0-9]", "");
        switch (p) { case "C": return "C#" + oct; case "G": return "G#" + oct; case "F": return "F#" + oct; }
        return n;
    }

    // ═══ The CRT powers off ═══════════════════════════════════════════════════
    static void powerOff() {
        double t0 = END - 1.2;
        for (int i = (int) (t0 * SR); i < (int) ((t0 + 0.9) * SR) && i < L.length; i++) {
            double t = i / (double) SR, p = (t - t0) / 0.9;
            double f = 8000 * (1 - p) + 400;                 // the flyback whine collapsing
            double s = Math.sin(2 * Math.PI * f * t) * 0.16 * (1 - p) * (1 - p);
            L[i] += s; R[i] += s;
        }
        noiseHit(t0 + 0.86, 0.05, 0.30, 3000);               // the click
    }

    // ═══ One-shot SFX, for the editor to place by hand ════════════════════════
    static void oneShots(File dir) throws Exception {
        emit(dir, "sfx-degauss.wav",  1.2, () -> thunk(0.05));
        emit(dir, "sfx-keyclack.wav", 0.12, () -> clack(0.01));
        emit(dir, "sfx-encounter.wav", 0.9, () -> {
            note(PULSE1, 0.00, 0.07, "A4", 0.34, 0.125);
            note(PULSE1, 0.07, 0.07, "D5", 0.34, 0.125);
            note(PULSE1, 0.14, 0.07, "G5", 0.34, 0.125);
            note(PULSE1, 0.21, 0.45, "A5", 0.38, 0.125);
            noiseHit(0.21, 0.30, 0.32, 5000);
        });
        emit(dir, "sfx-stinger.wav", 1.6, () -> {
            note(PULSE1, 0.00, 0.16, "A4", 0.34, 0.25);
            note(PULSE1, 0.16, 0.16, "E5", 0.34, 0.25);
            note(PULSE1, 0.32, 0.95, "A5", 0.38, 0.25);
            note(TRI,    0.00, 1.35, "A2", 0.42, 0);
        });
        emit(dir, "sfx-poweroff.wav", 1.1, () -> {
            for (int i = 0; i < (int) (0.9 * SR); i++) {
                double t = i / (double) SR, p = t / 0.9;
                double f = 8000 * (1 - p) + 400;
                double s = Math.sin(2 * Math.PI * f * t) * 0.2 * (1 - p) * (1 - p);
                L[i] += s; R[i] += s;
            }
            noiseHit(0.86, 0.05, 0.34, 3000);
        });
    }

    interface Painter { void paint(); }
    static void emit(File dir, String name, double secs, Painter p) throws Exception {
        double[] sl = L, sr = R;
        int n = (int) (secs * SR) + 64;
        L = new double[n]; R = new double[n];
        p.paint();
        normalise(0.9);
        writeWav(new File(dir, name), L, R);
        System.out.println("  " + name);
        L = sl; R = sr;
    }

    // ═══ Voices ══════════════════════════════════════════════════════════════
    static final int PULSE1 = 0, PULSE2 = 1, TRI = 2;

    /** duty 0 marks the triangle bass; anything else is a pulse of that duty cycle. */
    static void note(int voice, double at, double dur, String name, double vol, double duty) {
        int i0 = (int) (at * SR), n = (int) (dur * SR);
        if (i0 < 0 || n <= 0) return;
        double f = midi(noteMidi(name));
        double pan = voice == PULSE1 ? 0.16 : voice == PULSE2 ? -0.16 : 0;
        int atk = (int) (0.006 * SR), rel = Math.max(1, (int) (0.05 * SR));
        for (int i = 0; i < n && i0 + i < L.length; i++) {
            double t = i / (double) SR;
            double s = (voice == TRI) ? tri(f, t) : pulse(f, t, duty);
            double env = i < atk ? i / (double) atk
                       : i > n - rel ? Math.max(0, (n - i) / (double) rel) : 1.0;
            // A gentle decay keeps a held square from sounding like a test tone.
            env *= 1.0 - 0.35 * (i / (double) n);
            double v = s * vol * env;
            L[i0 + i] += v * (1 - Math.max(0, pan));
            R[i0 + i] += v * (1 + Math.min(0, pan));
        }
    }

    static void bassBar(double t, double beat, String root, double vol) {
        for (int i = 0; i < 8; i++) note(TRI, t + i * beat / 2, beat * 0.42, root, vol * (i % 2 == 0 ? 1 : 0.7), 0);
    }
    static void hatBar(double t, double beat, int per, double vol) {
        for (int i = 0; i < per; i++) noiseHit(t + i * (beat * 4 / per), 0.035, vol * (i % 2 == 0 ? 1 : 0.6), 9000);
    }
    static void kick(double t, double vol) { noiseHit(t, 0.09, vol, 260); }

    /** A noise burst with a one-pole lowpass; the cutoff is what separates a kick from a hat. */
    static void noiseHit(double at, double dur, double vol, double cutoff) {
        int i0 = (int) (at * SR), n = (int) (dur * SR);
        if (i0 < 0 || n <= 0) return;
        double a = Math.exp(-2 * Math.PI * cutoff / SR), z = 0;
        for (int i = 0; i < n && i0 + i < L.length; i++) {
            double w = Math.random() * 2 - 1;
            z = w * (1 - a) + z * a;
            double env = Math.pow(1.0 - i / (double) n, 2.2);
            double v = z * vol * env;
            L[i0 + i] += v; R[i0 + i] += v * 0.97;
        }
    }

    /** 60Hz mains hum with its third harmonic — what a CRT actually adds to a room. */
    static void hum(double from, double to, double vol) {
        for (int i = (int) (from * SR); i < (int) (to * SR) && i < L.length; i++) {
            double t = i / (double) SR;
            double env = Math.min(1, (t - from) / 0.4) * Math.min(1, (to - t) / 0.4);
            double s = (Math.sin(2 * Math.PI * 60 * t) + 0.3 * Math.sin(2 * Math.PI * 180 * t)) * vol * env;
            L[i] += s; R[i] += s;
        }
    }

    /** The degauss thunk: a pitch-dropping sine with a noise transient on top. */
    static void thunk(double at) {
        int i0 = (int) (at * SR), n = (int) (0.55 * SR);
        for (int i = 0; i < n && i0 + i < L.length; i++) {
            double t = i / (double) SR, p = i / (double) n;
            double f = 150 * (1 - p) + 42;
            double s = Math.sin(2 * Math.PI * f * t) * 0.42 * Math.pow(1 - p, 1.6);
            L[i0 + i] += s; R[i0 + i] += s;
        }
        noiseHit(at, 0.10, 0.22, 1400);
    }

    static void clack(double at) { noiseHit(at, 0.014, 0.075, 5200); }

    // ═══ Waveforms — the same four the game synthesizes ══════════════════════
    static double pulse(double f, double t, double duty) { return ((f * t) % 1.0) < duty ? 1 : -1; }
    static double tri(double f, double t) { double p = (f * t) % 1.0; return 4 * Math.abs(p - 0.5) - 1; }

    static double midi(int m) { return 440.0 * Math.pow(2, (m - 69) / 12.0); }
    static int noteMidi(String s) {
        String p = s.replaceAll("[0-9]", "");
        int oct = Integer.parseInt(s.replaceAll("[^0-9]", ""));
        int[] base = { 9, 11, 0, 2, 4, 5, 7 };                 // A B C D E F G
        int i = "ABCDEFG".indexOf(p.charAt(0));
        int semi = base[i] + (p.contains("#") ? 1 : 0) + (p.contains("b") ? -1 : 0);
        return 12 * (oct + 1) + semi;
    }

    // ═══ Output ══════════════════════════════════════════════════════════════
    static void normalise(double peak) {
        double max = 1e-9;
        for (int i = 0; i < L.length; i++) max = Math.max(max, Math.max(Math.abs(L[i]), Math.abs(R[i])));
        double g = peak / max;
        for (int i = 0; i < L.length; i++) { L[i] *= g; R[i] *= g; }
    }

    static void writeWav(File f, double[] l, double[] r) throws Exception {
        byte[] out = new byte[l.length * 4];
        for (int i = 0; i < l.length; i++) {
            short sl = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, l[i] * 32767));
            short sr = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, r[i] * 32767));
            out[i * 4]     = (byte) (sl & 0xff); out[i * 4 + 1] = (byte) ((sl >> 8) & 0xff);
            out[i * 4 + 2] = (byte) (sr & 0xff); out[i * 4 + 3] = (byte) ((sr >> 8) & 0xff);
        }
        AudioFormat fmt = new AudioFormat(SR, 16, 2, true, false);
        try (AudioInputStream in = new AudioInputStream(new ByteArrayInputStream(out), fmt, l.length)) {
            AudioSystem.write(in, AudioFileFormat.Type.WAVE, f);
        }
    }
}

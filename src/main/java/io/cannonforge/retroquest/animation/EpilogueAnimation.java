package io.cannonforge.retroquest.animation;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import io.cannonforge.retroquest.core.Fonts;
import io.cannonforge.retroquest.core.Retroquest;
import io.cannonforge.retroquest.core.SoundManager;
import io.cannonforge.retroquest.model.God;
import io.cannonforge.retroquest.overlay.OverlayTheme;

/**
 * Animated epilogue cinematic played after the final boss fight.
 *
 * <p>Scenes, in order, with a dip-to-black between each:
 * <ol>
 *   <li>The Cradle Crumbles — rocks fall, light pours in</li>
 *   <li>The World Changes — ending-specific vista</li>
 *   <li>Hero Walks Into the Sunset — the signature shot</li>
 *   <li>The Fates — one card per choice the player made and lived with,
 *       assembled at {@link #start()} from the flags recorded during play</li>
 *   <li>Title Card — ending name, then a hold for a keypress</li>
 * </ol>
 *
 * <p>Length depends on how many fates were earned. Any key skips to the next
 * beat, so nothing here can trap a player who has already read it.
 *
 * <p>Constructor parameters:
 * <ul>
 *   <li>{@code endingType} — one of {@code "new_serpent"}, {@code "champion"},
 *       {@code "true_unbound"}, {@code "generic"}</li>
 *   <li>{@code endingTitle} — display title for the ending (e.g. "The New Serpent")</li>
 *   <li>{@code championGod} — the god chosen (only for champion ending, null otherwise)</li>
 * </ul>
 */
public class EpilogueAnimation {

    // ── Scene lengths, in milliseconds ──────────────────────────────────────
    // The epilogue used to be a fixed 20 s with an unskippable 18 s beat. It is
    // now assembled from beats, its length depends on how many fates the player
    // earned, and ANY key jumps to the next beat (see {@link #pressKey()}).
    private static final long S0_MS    = 5000;   // the Cradle crumbles
    private static final long S1_MS    = 5000;   // the world changes
    private static final long S2_MS    = 5500;   // the walk into the sunset
    // A fate card runs long on purpose: a key advances it, but nothing can slow
    // it down, so the default must be comfortable for a slow reader.
    private static final long CARD_MS  = AnimationSpeed.scale(11000);  // one fate card
    private static final long TITLE_MS = AnimationSpeed.scale(4500);   // the title card

    /** Dip-to-black between scenes. */
    private static final long XFADE_MS = 400;

    // ── Palette ─────────────────────────────────────────────────────────────
    // Scene 0: Cradle interior
    private static final Color CAVERN_TOP = new Color(  4,   6,  12);
    private static final Color CAVERN_BOT = new Color( 30,  25,  20);
    private static final Color LIGHT_CORE = new Color(255, 240, 200);
    // Scene 2: Sunset
    private static final Color SKY_TOP    = new Color( 30,  20,  60);
    private static final Color SKY_MID    = new Color(180,  80,  40);
    private static final Color SKY_BOT    = new Color(255, 180,  80);
    private static final Color SUN_OUTER  = new Color(255, 200, 100);
    private static final Color SUN_INNER  = new Color(255, 240, 200);
    private static final Color ROAD_COL   = new Color(160, 130,  80);
    private static final Color ROAD_EDGE  = new Color(120,  95,  55);
    private static final Color GRASS_A    = new Color( 40, 100,  35);
    private static final Color GRASS_B    = new Color( 50, 120,  45);
    private static final Color HERO_BODY  = new Color( 25,  25,  30);
    private static final Color HERO_CAPE  = new Color(140,  28,  28);
    private static final Color DUST_COL   = new Color(200, 180, 140, 80);

    // Scene 3: Title card
    private static final Color SERPENT_COL = new Color(180, 120, 255);
    private static final Color UNBOUND_COL = new Color(220, 200, 140);
    private static final Color GENERIC_COL = new Color(160, 160, 170);

    // Text
    private static final Color TEXT_COL   = new Color(220, 200, 155);
    private static final Color TEXT_DIM   = new Color(130, 118,  88);
    private static final Font F_NARR     = Fonts.mono    (15);
    private static final Font F_BIG_NARR = Fonts.monoBold(14);
    private static final Font F_TITLE    = Fonts.monoBold(22);
    private static final Font F_FATE_HEAD = Fonts.monoBold(13);
    private static final Font F_SUB      = Fonts.mono    (12);
    private static final Font F_HINT     = Fonts.monoBold(11);

    // God accent colours
    private static final Color[] GOD_COLORS = {
        new Color(180, 200, 255), new Color(255, 120,  40), new Color(140, 180, 255),
        new Color( 80, 220, 100), new Color( 60, 180, 200), new Color(160, 170, 200),
        new Color(200, 160,  60),
    };

    // ── Rock fragments (Scene 0) ────────────────────────────────────────────
    private static final int ROCK_COUNT = 30;
    private final float[] rockX  = new float[ROCK_COUNT];
    private final float[] rockY  = new float[ROCK_COUNT];
    private final float[] rockVX = new float[ROCK_COUNT];
    private final float[] rockVY = new float[ROCK_COUNT];
    private final int[]   rockS  = new int[ROCK_COUNT];
    private final int[]   rockC  = new int[ROCK_COUNT]; // grey shade

    // ── Dust particles (Scene 2) ────────────────────────────────────────────
    private static final int DUST_COUNT = 15;
    private final float[] dustX  = new float[DUST_COUNT];
    private final float[] dustVX = new float[DUST_COUNT];
    private final int[]   dustA  = new int[DUST_COUNT];

    // ── State ───────────────────────────────────────────────────────────────
    private final Retroquest game;
    private final String endingType;
    private final String endingTitle;
    private final God championGod;

    private boolean active        = false;
    private boolean fired         = false;
    private boolean holding       = false;   // frozen on the title card, awaiting a key
    private long    startTime;
    private long    holdElapsed;
    private boolean soundPlayed   = false;
    private javax.swing.Timer animTimer;

    /** The fates earned this playthrough, built in {@link #start()}. */
    private final java.util.List<Fate> fates = new java.util.ArrayList<>();

    /** Boundaries a keypress may skip to, ascending. Last entry is {@link #holdMs}. */
    private long[] beats = new long[0];
    private long   fatesStart;    // ms at which the fate cards begin
    private long   titleStart;    // ms at which the title card begins
    private long   holdMs;        // ms at which the animation freezes for the last key
    private long   totalMs;       // full length of the assembled epilogue

    /**
     * One consequence of a choice the player made, paid off by name.
     *
     * <p>These are driven by the flags the game has been recording faithfully
     * all along — {@code bellorak_trial_complete} / {@code seraphine_freed},
     * {@code umbryn_trial_complete}, {@code thalorax_trial_complete} and
     * {@code varsa_choice_made} — none of which the ending had ever read.
     */
    private record Fate(String heading, String body, Color accent) {}

    // Champion-ending epilogue texts (reused from DungeonController)
    private static final String[] CHAMPION_EPILOGUES = {
        "The world becomes perfectly safe \u2014 and perfectly still.",
        "The world becomes a forge \u2014 endlessly producing, endlessly consuming.",
        "The world becomes a storm \u2014 beautiful, free, and utterly chaotic.",
        "The world becomes a garden \u2014 alive, growing, and merciless.",
        "The world becomes a library \u2014 every truth known, every lie exposed.",
        "The world becomes a memory \u2014 preserved, perfect, and dead.",
        "The world becomes an arena \u2014 endless glory, endless sacrifice.",
    };

    public EpilogueAnimation(Retroquest game, String endingType,
                              String endingTitle, God championGod) {
        this.game         = game;
        this.endingType   = endingType;
        this.endingTitle  = endingTitle;
        this.championGod  = championGod;
    }

    public boolean isActive() { return active; }

    /**
     * Reports "yes" for the whole run, not just the final hold, so that the key
     * dispatcher in {@code Retroquest.handleKey} — which only forwards a key
     * when this returns true — lets the player skip a beat at any point. A short
     * grace period keeps a key still held from the boss fight from eating the
     * opening shot.
     */
    public boolean isWaitingForKey() {
        if (!active || fired) return false;
        if (holding) return true;
        return (System.currentTimeMillis() - startTime) > SKIP_GRACE_MS;
    }

    /** Any key: jump to the next beat; on the final hold, roll the credits. */
    public void pressKey() {
        if (!active || fired) return;
        if (holding) { finish(); return; }

        long now     = System.currentTimeMillis();
        long elapsed = now - startTime;
        if (elapsed < SKIP_GRACE_MS) return;

        for (long beat : beats) {
            if (beat > elapsed + 120) {           // 120 ms so a beat just reached is skipped past
                startTime = now - beat;
                return;
            }
        }
        // Past every beat: go straight to the hold.
        startTime = now - holdMs;
    }

    /** Ends the epilogue and hands off to the credits exactly once. */
    private void finish() {
        if (fired) return;
        fired   = true;
        active  = false;
        holding = false;
        if (animTimer != null) animTimer.stop();
        SoundManager.getInstance().stopEpilogueTheme();
        game.getGamePanel().startCredits(endingType, endingTitle, championGod);
    }

    /** Keys pressed in the first moment of the epilogue are ignored. */
    private static final long SKIP_GRACE_MS = 700;

    public void start() {
        java.util.Random rng = new java.util.Random(0xE1110A);

        // Seed rocks
        for (int i = 0; i < ROCK_COUNT; i++) {
            rockX[i]  = 200 + rng.nextInt(600);
            rockY[i]  = rng.nextInt(100);
            rockVX[i] = (rng.nextFloat() - 0.5f) * 40;
            rockVY[i] = 50 + rng.nextFloat() * 100;
            rockS[i]  = 3 + rng.nextInt(6);
            rockC[i]  = 40 + rng.nextInt(50);
        }

        // Seed dust
        for (int i = 0; i < DUST_COUNT; i++) {
            dustX[i]  = rng.nextFloat() * 40 - 20;
            dustVX[i] = -(20 + rng.nextFloat() * 30);
            dustA[i]  = 100 + rng.nextInt(80);
        }

        // Assemble the running order from the choices this playthrough made.
        buildFates();
        fatesStart = S0_MS + S1_MS + S2_MS;
        titleStart = fatesStart + fates.size() * CARD_MS;
        totalMs    = titleStart + TITLE_MS;
        holdMs     = titleStart + (long)(TITLE_MS * 0.55f);

        beats = new long[3 + fates.size() + 1];
        beats[0] = S0_MS;
        beats[1] = S0_MS + S1_MS;
        beats[2] = fatesStart;
        for (int i = 0; i < fates.size(); i++) beats[3 + i] = fatesStart + (i + 1L) * CARD_MS;
        beats[beats.length - 1] = holdMs;

        fired       = false;
        soundPlayed = false;
        startTime   = System.currentTimeMillis();
        active      = true;
        holding     = false;

        if (animTimer != null) animTimer.stop();
        animTimer = new javax.swing.Timer(16, e -> {
            long elapsed = System.currentTimeMillis() - startTime;
            if (!holding && elapsed >= holdMs) {
                holding     = true;
                holdElapsed = holdMs;
            }
            if (!holding && elapsed > totalMs) finish();
            if (game.getGamePanel() != null) game.getGamePanel().repaint();
        });
        animTimer.start();
    }

    // ── The fates ───────────────────────────────────────────────────────────

    /**
     * Reads the choice flags recorded across the whole game and turns each into
     * a paragraph. Freeing Seraphine after thirty years and tearing her power
     * out of her used to produce byte-identical endings; they no longer do.
     */
    private void buildFates() {
        fates.clear();
        var p = game.getPlayer();
        if (p == null) return;

        // ── Seraphine, the Undying Champion (Bellorak's arena) ──────────────
        String bellorak = p.getFlag("bellorak_trial_complete");
        boolean freed   = p.hasFlag("seraphine_freed");
        if (freed || "free_her".equals(bellorak)) {
            fates.add(new Fate("SERAPHINE, WHO FOUGHT FOR THIRTY YEARS",
                "She lived. Grey, weak, mortal \u2014 and she walked out of the Iron Pit on "
              + "her own feet, which no champion had ever done. She stood at your back in "
              + "the dark of the Trench, then went home to a coast that had counted her "
              + "dead for thirty years. Bellorak watched her go, and said nothing at all.",
                GOD_COLORS[6]));
        } else if ("grant_peace".equals(bellorak)) {
            fates.add(new Fate("SERAPHINE, WHO FOUGHT FOR THIRTY YEARS",
                "They hung her helm on the gate of the Iron Pit and left the rest of the "
              + "armour empty. For one season the arena stayed quiet out of respect \u2014 "
              + "the longest silence in its history. She had won ten thousand fights and "
              + "never once been given an ending. You gave her that.",
                GOD_COLORS[6]));
        } else if ("absorb_power".equals(bellorak)) {
            fates.add(new Fate("SERAPHINE, WHO FOUGHT FOR THIRTY YEARS",
                "You did not free her. You spent her. Thirty years of the Iron Pit went "
              + "into your arms and stayed there, and ever after your sword moved a "
              + "half-beat before you did. Bellorak named you champion and meant it "
              + "kindly. He never mentioned that the Pit has no word for leaving.",
                GOD_COLORS[6]));
        }

        // ── Umbryn and the Archive of Tears ─────────────────────────────────
        String umbryn = p.getFlag("umbryn_trial_complete");
        if ("shared_burden".equals(umbryn)) {
            fates.add(new Fate("UMBRYN, WHO REMEMBERS EVERYTHING",
                "You took his hand and the archive halved its weight. You have carried "
              + "the other half ever since: every drowned city, every name the Shattering "
              + "swallowed. It costs you sleep. It also means that, for the first time in "
              + "ten thousand years, the remembering is something two people do.",
                GOD_COLORS[5]));
        } else if ("let_forget".equals(umbryn)) {
            fates.add(new Fate("UMBRYN, WHO REMEMBERS EVERYTHING",
                "He breathed out, and an age of grief went off him like dust off a shelf. "
              + "The Archive of Tears is a gentle place now, and its keeper is kind, and "
              + "light, and cannot tell you why the halls are empty. Whether the truth was "
              + "worth keeping is your question now. He will never think to ask it.",
                GOD_COLORS[5]));
        } else if ("must_endure".equals(umbryn)) {
            fates.add(new Fate("UMBRYN, WHO REMEMBERS EVERYTHING",
                "You told him the memories mattered more than his peace, and then you left "
              + "him holding them. He did not argue; he rarely does. Every name is still "
              + "there, every city, every drowned year \u2014 kept whole by a god you were "
              + "right about, at a price you were never asked to pay.",
                GOD_COLORS[5]));
        }

        // ── Thalorax and the answer given under pressure ────────────────────
        String thalorax = p.getFlag("thalorax_trial_complete");
        if ("one_god".equals(thalorax)) {
            fates.add(new Fate("THALORAX, WHO ASKED THE QUESTION",
                "You told the deep that one god should rule, and the deep approved, and "
              + "has not stopped approving since. Thalorax repeats your answer to every "
              + "mortal who reaches his altar \u2014 in your voice, in your words, as "
              + "though something had been settled.",
                GOD_COLORS[4]));
        } else if ("destroy_gods".equals(thalorax)) {
            fates.add(new Fate("THALORAX, WHO ASKED THE QUESTION",
                "You told the trench that mortals need no masters. Thalorax has been "
              + "turning it over ever since, down where thought moves slowly, and the "
              + "currents around the Pressure Temple run strange now. He may need a "
              + "thousand years to finish the thought. He has them.",
                GOD_COLORS[4]));
        } else if ("aqualon_wakes".equals(thalorax)) {
            fates.add(new Fate("THALORAX, WHO ASKED THE QUESTION",
                "You said: wake the Sleeper. The pressure shifted that day and it has "
              + "never settled. The fleets above the Trench report the water breathing. "
              + "Whatever you set moving is still moving, far down, in the dark \u2014 "
              + "and it is nowhere near finished.",
                GOD_COLORS[4]));
        } else if ("refused".equals(thalorax)) {
            fates.add(new Fate("THALORAX, WHO ASKED THE QUESTION",
                "You gave the deep silence, and the deep understood it perfectly. "
              + "Thalorax has said nothing since. His priests read it as approval, or as "
              + "judgement, or as grief, and cannot agree. That is the trouble with an "
              + "answer that is not one.",
                GOD_COLORS[4]));
        }

        // ── Tide-Priestess Varsa, in Abyssport ──────────────────────────────
        String varsa = p.getFlag("varsa_choice_made");
        if ("extracted".equals(varsa)) {
            fates.add(new Fate("TIDE-PRIESTESS VARSA",
                "She surfaced. It took a season before she stopped flinching at open sky, "
              + "and another before she could sleep without the weight of water on her "
              + "chest \u2014 but she reports to Moonhaven's temple now, and her hands are "
              + "steady. Lirandel learned less about the deep than she wanted. She got her "
              + "priestess back.",
                GOD_COLORS[0]));
        } else if ("sent_deep".equals(varsa)) {
            fates.add(new Fate("TIDE-PRIESTESS VARSA",
                "She went down, and she did not come back up. Something in the Pressure "
              + "Temple still wears her face and answers to her name, and it sends "
              + "patient, beautiful reports that nobody in Moonhaven can bear to finish. "
              + "She was right that the truth mattered. Nobody asked her whether it "
              + "mattered that much.",
                GOD_COLORS[0]));
        }
    }

    // ── PAINT ───────────────────────────────────────────────────────────────

    public void paint(Graphics2D g, int W, int H) {
        if (!active) return;
        try { doPaint(g, W, H); }
        catch (Exception ex) { ex.printStackTrace(); active = false; }
    }

    private void doPaint(Graphics2D g, int W, int H) {
        long now         = System.currentTimeMillis();
        long wallElapsed = now - startTime;
        long t           = Math.min(totalMs, holding ? holdElapsed : wallElapsed);

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Sound
        if (!soundPlayed && t > 300) {
            soundPlayed = true;
            SoundManager.getInstance().playEpilogueTheme();
        }

        long e0 = S0_MS;
        long e1 = S0_MS + S1_MS;
        long e2 = fatesStart;

        if (t < e0 - XFADE_MS) {
            paintScene0(g, W, H, (float) t / S0_MS, now);
        } else if (t < e0) {
            float blend = (float)(t - (e0 - XFADE_MS)) / XFADE_MS;
            paintScene0(g, W, H, 1f, now);
            fillBlack(g, W, H, blend);
            paintScene1(g, W, H, 0f, now, blend);
        } else if (t < e1 - XFADE_MS) {
            paintScene1(g, W, H, (float)(t - e0) / S1_MS, now, 1f);
        } else if (t < e1) {
            float blend = (float)(t - (e1 - XFADE_MS)) / XFADE_MS;
            paintScene1(g, W, H, 1f, now, 1f - blend);
            fillBlack(g, W, H, blend);
            paintScene2(g, W, H, 0f, now, blend);
        } else if (t < e2 - XFADE_MS) {
            paintScene2(g, W, H, (float)(t - e1) / S2_MS, now, 1f);
        } else if (t < e2) {
            float blend = (float)(t - (e2 - XFADE_MS)) / XFADE_MS;
            paintScene2(g, W, H, 1f, now, 1f - blend);
            fillBlack(g, W, H, blend);
        } else if (t < titleStart) {
            long into = t - fatesStart;
            int  idx  = (int) Math.min(fates.size() - 1L, into / CARD_MS);
            paintFateCard(g, W, H, fates.get(idx),
                          (float)(into - idx * CARD_MS) / CARD_MS,
                          idx + 1, fates.size());
        } else {
            paintScene3(g, W, H, (float)(t - titleStart) / TITLE_MS);
        }

        // ── Key hints ───────────────────────────────────────────────────────
        if (holding) {
            g.setFont(F_HINT);
            String hint = "[ PRESS ANY KEY TO CONTINUE ]";
            FontMetrics fm = g.getFontMetrics();
            int hx = (W - fm.stringWidth(hint)) / 2;
            int hy = H - 30;
            float pulse = 0.5f + 0.5f * (float)Math.sin(wallElapsed / 500.0);
            g.setColor(new Color(TEXT_DIM.getRed(), TEXT_DIM.getGreen(),
                                 TEXT_DIM.getBlue(), clamp((int)(pulse * 200))));
            g.drawString(hint, hx, hy);
        } else if (wallElapsed > SKIP_GRACE_MS) {
            // Quiet, always-there promise that nothing here traps the player.
            g.setFont(F_HINT);
            String hint = "ANY KEY \u25b8";
            FontMetrics fm = g.getFontMetrics();
            g.setColor(new Color(TEXT_DIM.getRed(), TEXT_DIM.getGreen(),
                                 TEXT_DIM.getBlue(), 90));
            g.drawString(hint, W - fm.stringWidth(hint) - 18, H - 16);
        }
    }

    private void fillBlack(Graphics2D g, int W, int H, float amount) {
        g.setColor(new Color(0, 0, 0, clamp((int)(amount * 255))));
        g.fillRect(0, 0, W, H);
    }

    // ── FATE CARDS: what became of the people you decided for ───────────────

    private void paintFateCard(Graphics2D g, int W, int H, Fate fate,
                                float sp, int index, int count) {
        // Fade in, hold, fade out inside the card's own slot.
        float alpha = 1f;
        if (sp < 0.10f)      alpha = sp / 0.10f;
        else if (sp > 0.92f) alpha = Math.max(0f, (1f - sp) / 0.08f);

        g.setColor(Color.BLACK);
        g.fillRect(0, 0, W, H);
        if (alpha <= 0.01f) return;

        Color acc = fate.accent();

        // Heading
        g.setFont(F_FATE_HEAD);
        FontMetrics fmH = g.getFontMetrics();
        java.util.List<String> body =
            OverlayTheme.wordWrap(g.getFontMetrics(F_NARR), fate.body(), W - 140);
        int lineH  = g.getFontMetrics(F_NARR).getHeight() + 4;
        int blockH = fmH.getHeight() + 18 + body.size() * lineH;
        int top    = (H - blockH) / 2;

        String head = fate.heading();
        int hx = (W - fmH.stringWidth(head)) / 2;
        int hy = top + fmH.getAscent();
        g.setColor(new Color(acc.getRed(), acc.getGreen(), acc.getBlue(),
                             clamp((int)(alpha * 235))));
        g.drawString(head, hx, hy);

        // Rule under the heading
        int ruleY = hy + 8;
        int ruleW = Math.min(W - 160, fmH.stringWidth(head) + 60);
        g.setColor(new Color(acc.getRed(), acc.getGreen(), acc.getBlue(),
                             clamp((int)(alpha * 70))));
        g.drawLine((W - ruleW) / 2, ruleY, (W + ruleW) / 2, ruleY);

        // Body
        g.setFont(F_NARR);
        FontMetrics fmB = g.getFontMetrics();
        int by = ruleY + 14 + fmB.getAscent();
        for (String line : body) {
            int lx = (W - fmB.stringWidth(line)) / 2;
            g.setColor(new Color(0, 0, 0, clamp((int)(alpha * 180))));
            g.drawString(line, lx + 1, by + 1);
            g.setColor(new Color(TEXT_COL.getRed(), TEXT_COL.getGreen(),
                                 TEXT_COL.getBlue(), clamp((int)(alpha * 240))));
            g.drawString(line, lx, by);
            by += lineH;
        }

        // Position in the roll, so the player knows how much is left
        if (count > 1) {
            g.setFont(F_HINT);
            FontMetrics fmN = g.getFontMetrics();
            String pos = index + " / " + count;
            g.setColor(new Color(TEXT_DIM.getRed(), TEXT_DIM.getGreen(),
                                 TEXT_DIM.getBlue(), clamp((int)(alpha * 120))));
            g.drawString(pos, (W - fmN.stringWidth(pos)) / 2, top + blockH + 26);
        }
    }

    // ── SCENE 0: The Cradle Crumbles ────────────────────────────────────────

    private void paintScene0(Graphics2D g, int W, int H, float sp, long now) {
        float fade = Math.min(1f, sp / 0.15f);

        // Cavern background
        for (int y = 0; y < H; y++) {
            float t = (float) y / H;
            int r = blend(CAVERN_TOP.getRed(),   CAVERN_BOT.getRed(),   t);
            int gr= blend(CAVERN_TOP.getGreen(), CAVERN_BOT.getGreen(), t);
            int b = blend(CAVERN_TOP.getBlue(),  CAVERN_BOT.getBlue(),  t);
            g.setColor(new Color(r, gr, b, clamp((int)(fade * 255))));
            g.drawLine(0, y, W, y);
        }

        // Light from above (expanding cone)
        float lightP = Math.min(1f, Math.max(0f, (sp - 0.2f) / 0.6f));
        if (lightP > 0) {
            int coneW = (int)(lightP * W * 0.4f);
            int coneH = (int)(lightP * H * 0.6f);
            for (int i = 10; i >= 0; i--) {
                float fr = (float) i / 10;
                int la = (int)(lightP * fade * (1f - fr) * 60);
                g.setColor(new Color(LIGHT_CORE.getRed(), LIGHT_CORE.getGreen(),
                                     LIGHT_CORE.getBlue(), clamp(la)));
                int w2 = (int)(coneW * (0.3f + fr * 0.7f));
                g.fillOval(W / 2 - w2, -coneH / 3, w2 * 2, coneH);
            }
        }

        // Falling rocks
        float rockP = Math.max(0f, (sp - 0.1f) / 0.9f);
        if (rockP > 0) {
            for (int i = 0; i < ROCK_COUNT; i++) {
                float rx = rockX[i] + rockVX[i] * rockP;
                float ry = rockY[i] + rockVY[i] * rockP + 200 * rockP * rockP; // gravity
                int sx = (int)(rx * W / 1000);
                int sy = (int)(ry * H / 1000);
                if (sy > H) continue;
                int c = rockC[i];
                g.setColor(new Color(c, c - 10, c - 15, clamp((int)(fade * 200))));
                g.fillRect(sx, sy, rockS[i], rockS[i]);
            }
        }

        // Ending-specific center element
        paintScene0Center(g, W, H, sp, fade, now);

        // Narration
        String narr = switch (endingType) {
            case "new_serpent"   -> "The Cradle shatters. Divine power floods through you like a river of stars.";
            case "champion"     -> "The Cradle cracks open, and " + championGod.displayName + "'s light fills the world.";
            case "true_unbound" -> "The Cradle crumbles. The gods scream \u2014 then fall silent. The world is free.";
            default             -> "The Cradle sighs. Something shifts. The world continues, changed but unbroken.";
        };
        float narrP = Math.min(1f, Math.max(0f, (sp - 0.35f) / 0.3f));
        if (narrP > 0) paintCenteredNarr(g, W, H, narr, narrP * fade, F_NARR, (int)(H * 0.82f));
    }

    private void paintScene0Center(Graphics2D g, int W, int H, float sp, float fade, long now) {
        int cx = W / 2, cy = H / 2;
        float elemP = Math.min(1f, Math.max(0f, (sp - 0.3f) / 0.4f));
        if (elemP <= 0) return;

        switch (endingType) {
            case "new_serpent" -> {
                // Hero levitates, 7 orbiting orbs
                int hx = cx, hy = cy - (int)(elemP * 30);
                paintSmallHero(g, hx, hy, (int)(H * 0.05f), elemP * fade, true);
                for (int i = 0; i < 7; i++) {
                    double angle = (Math.PI * 2 * i / 7) + now / 800.0;
                    int ox = hx + (int)(Math.cos(angle) * 50 * elemP);
                    int oy = hy + (int)(Math.sin(angle) * 25 * elemP);
                    Color c = GOD_COLORS[i];
                    g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(),
                                         clamp((int)(elemP * fade * 200))));
                    g.fillOval(ox - 4, oy - 4, 8, 8);
                }
            }
            case "champion" -> {
                // Massive god-colored pillar
                Color gc = GOD_COLORS[championGod.index];
                int pillarW = (int)(elemP * W * 0.08f);
                int pillarH = (int)(elemP * H * 0.7f);
                for (int i = 5; i >= 0; i--) {
                    float fr = (float) i / 5;
                    int pa = (int)(elemP * fade * (1f - fr) * 100);
                    g.setColor(new Color(gc.getRed(), gc.getGreen(), gc.getBlue(), clamp(pa)));
                    int pw = pillarW + i * 8;
                    g.fillRect(cx - pw / 2, cy - pillarH / 2, pw, pillarH);
                }
                // Hero kneeling
                paintSmallHero(g, cx, cy + (int)(H * 0.15f), (int)(H * 0.04f), elemP * fade, false);
            }
            case "true_unbound" -> {
                // 7 lights shattering outward
                for (int i = 0; i < 7; i++) {
                    double angle = (Math.PI * 2 * i / 7) + 0.3;
                    float dist = elemP * W * 0.25f;
                    int ox = cx + (int)(Math.cos(angle) * dist);
                    int oy = cy + (int)(Math.sin(angle) * dist * 0.5f);
                    Color c = GOD_COLORS[i];
                    // Trail
                    for (int t = 0; t < 5; t++) {
                        float tf = (float) t / 5;
                        int tx = cx + (int)(Math.cos(angle) * dist * (1f - tf));
                        int ty = cy + (int)(Math.sin(angle) * dist * 0.5f * (1f - tf));
                        g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(),
                                             clamp((int)(elemP * fade * (1f - tf) * 80))));
                        g.fillOval(tx - 3, ty - 3, 6, 6);
                    }
                    g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(),
                                         clamp((int)(elemP * fade * 220))));
                    g.fillOval(ox - 5, oy - 5, 10, 10);
                }
                // Hero standing firm
                paintSmallHero(g, cx, cy + (int)(H * 0.08f), (int)(H * 0.05f), elemP * fade, false);
            }
            default -> {
                // Soft golden glow
                for (int r = (int)(W * 0.2f); r >= 1; r -= 4) {
                    float fr = r / (W * 0.2f);
                    int ga = (int)(elemP * fade * (1f - fr) * 40);
                    g.setColor(new Color(255, 220, 140, clamp(ga)));
                    g.fillOval(cx - r, cy - r / 2, r * 2, r);
                }
            }
        }
    }

    // ── SCENE 1: The World Changes ──────────────────────────────────────────

    private void paintScene1(Graphics2D g, int W, int H, float sp, long now, float opacity) {
        int oa = clamp((int)(opacity * 255));

        switch (endingType) {
            case "new_serpent" -> {
                // Cosmos: nebula gradient + serpent silhouette + rising islands
                for (int y = 0; y < H; y++) {
                    float t = (float) y / H;
                    int r = blend(10, 60, t); int gr = blend(5, 20, t); int b = blend(30, 140, t);
                    g.setColor(new Color(r, gr, b, oa));
                    g.drawLine(0, y, W, y);
                }
                // Serpent silhouette (two ovals)
                float serpP = Math.min(1f, sp / 0.6f);
                if (serpP > 0) {
                    int sa = clamp((int)(serpP * opacity * 180));
                    g.setColor(new Color(255, 255, 255, sa));
                    g.setStroke(new BasicStroke(2f));
                    int sw = (int)(W * 0.35f * serpP), sh = (int)(H * 0.3f * serpP);
                    g.drawOval(W / 2 - sw, H / 3 - sh / 2, sw * 2, sh);
                    g.drawOval(W / 2 - sw + 15, H / 3 - sh / 2 + 10, sw * 2 - 30, sh - 20);
                }
                // Rising islands
                float islandP = Math.max(0f, (sp - 0.4f) / 0.6f);
                if (islandP > 0) {
                    int oceanY = (int)(H * 0.75f);
                    g.setColor(new Color(15, 40, 80, oa));
                    g.fillRect(0, oceanY, W, H - oceanY);
                    int[] ix = {W / 5, W * 2 / 5, W * 3 / 5, W * 4 / 5};
                    for (int i = 0; i < 4; i++) {
                        int iy = oceanY - (int)(islandP * (20 + i * 8));
                        g.setColor(new Color(60 + i * 15, 100 + i * 10, 50, oa));
                        int[] xp = {ix[i] - 20, ix[i], ix[i] + 20};
                        int[] yp = {oceanY, iy, oceanY};
                        g.fillPolygon(xp, yp, 3);
                    }
                }
                paintCenteredNarr(g, W, H, "You dream a new world into being. Islands rise where none existed.",
                                  Math.min(1f, Math.max(0f, (sp - 0.3f) / 0.3f)) * opacity, F_NARR, (int)(H * 0.88f));
            }
            case "champion" -> {
                // Monochrome in god's color
                Color gc = GOD_COLORS[championGod.index];
                for (int y = 0; y < H; y++) {
                    float t = (float) y / H;
                    int r = (int)(gc.getRed()   * t * 0.4f);
                    int gr= (int)(gc.getGreen() * t * 0.4f);
                    int b = (int)(gc.getBlue()  * t * 0.4f);
                    g.setColor(new Color(clamp(r), clamp(gr), clamp(b), oa));
                    g.drawLine(0, y, W, y);
                }
                // God-colored glow pulsing
                float pulse = 0.5f + 0.5f * (float)Math.sin(now / 600.0);
                int glowA = clamp((int)(pulse * opacity * 60));
                g.setColor(new Color(gc.getRed(), gc.getGreen(), gc.getBlue(), glowA));
                g.fillRect(0, 0, W, H);

                String epi = CHAMPION_EPILOGUES[championGod.index];
                paintCenteredNarr(g, W, H, epi,
                                  Math.min(1f, Math.max(0f, (sp - 0.2f) / 0.3f)) * opacity, F_NARR, (int)(H * 0.82f));
            }
            case "true_unbound" -> {
                // Ocean receding + 7 lights rising
                // Sky
                for (int y = 0; y < H / 2; y++) {
                    float t = (float) y / (H / 2);
                    g.setColor(new Color(blend(20, 60, t), blend(25, 80, t), blend(50, 140, t), oa));
                    g.drawLine(0, y, W, y);
                }
                // Ocean (receding)
                int oceanTop = H / 2 + (int)(sp * H * 0.15f);
                for (int y = oceanTop; y < H; y++) {
                    float t = (float)(y - oceanTop) / (H - oceanTop);
                    g.setColor(new Color(blend(12, 8, t), blend(38, 20, t), blend(75, 50, t), oa));
                    g.drawLine(0, y, W, y);
                }
                // Revealed land
                if (oceanTop > H / 2) {
                    g.setColor(new Color(130, 115, 80, oa));
                    g.fillRect(0, H / 2, W, oceanTop - H / 2);
                }
                // 7 lights rising like lanterns
                for (int i = 0; i < 7; i++) {
                    float lp = Math.max(0f, (sp - 0.2f - i * 0.05f) / 0.5f);
                    if (lp <= 0) continue;
                    int lx = W / 8 + i * W / 8;
                    int ly = (int)(H * 0.6f - lp * H * 0.5f);
                    Color c = GOD_COLORS[i];
                    g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(),
                                         clamp((int)(lp * opacity * 180))));
                    g.fillOval(lx - 4, ly - 4, 8, 8);
                    // Trail
                    g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(),
                                         clamp((int)(lp * opacity * 40))));
                    g.drawLine(lx, ly + 4, lx, ly + 20);
                }
                paintCenteredNarr(g, W, H, "The ocean recedes. The islands stabilize. Seven lights scatter to the stars.",
                                  Math.min(1f, Math.max(0f, (sp - 0.25f) / 0.3f)) * opacity, F_NARR, (int)(H * 0.88f));
            }
            default -> {
                // Gentle dawn
                for (int y = 0; y < H; y++) {
                    float t = (float) y / H;
                    int r = blend(18, 180, t); int gr = blend(22, 120, t); int b = blend(38, 60, t);
                    g.setColor(new Color(r, gr, b, oa));
                    g.drawLine(0, y, W, y);
                }
                // Sun
                float sunP = Math.min(1f, sp / 0.5f);
                int sunR = (int)(H * 0.08f * sunP);
                int sunX = (int)(W * 0.75f), sunY = (int)(H * 0.5f) - (int)(sunP * H * 0.1f);
                g.setColor(new Color(255, 220, 120, clamp((int)(sunP * opacity * 180))));
                g.fillOval(sunX - sunR, sunY - sunR, sunR * 2, sunR * 2);

                paintCenteredNarr(g, W, H, "The world is neither saved nor doomed. But something has changed.",
                                  Math.min(1f, Math.max(0f, (sp - 0.3f) / 0.3f)) * opacity, F_NARR, (int)(H * 0.85f));
            }
        }
    }

    // ── SCENE 2: Hero Walks Into the Sunset ─────────────────────────────────

    private void paintScene2(Graphics2D g, int W, int H, float sp, long now, float opacity) {
        int oa = clamp((int)(opacity * 255));
        int horizonY = (int)(H * 0.45f);

        // ── Sunset sky gradient ─────────────────────────────────────────────
        for (int y = 0; y < horizonY; y++) {
            float t = (float) y / horizonY;
            int r, gr, b;
            if (t < 0.5f) {
                float t2 = t / 0.5f;
                r = blend(SKY_TOP.getRed(),   SKY_MID.getRed(),   t2);
                gr= blend(SKY_TOP.getGreen(), SKY_MID.getGreen(), t2);
                b = blend(SKY_TOP.getBlue(),  SKY_MID.getBlue(),  t2);
            } else {
                float t2 = (t - 0.5f) / 0.5f;
                r = blend(SKY_MID.getRed(),   SKY_BOT.getRed(),   t2);
                gr= blend(SKY_MID.getGreen(), SKY_BOT.getGreen(), t2);
                b = blend(SKY_MID.getBlue(),  SKY_BOT.getBlue(),  t2);
            }
            g.setColor(new Color(r, gr, b, oa));
            g.drawLine(0, y, W, y);
        }

        // ── Ground ──────────────────────────────────────────────────────────
        for (int y = horizonY; y < H; y++) {
            float t = (float)(y - horizonY) / (H - horizonY);
            Color grass = (y % 4 < 2) ? GRASS_A : GRASS_B;
            int r = (int)(grass.getRed()   * (1f - t * 0.3f));
            int gr= (int)(grass.getGreen() * (1f - t * 0.3f));
            int b = (int)(grass.getBlue()  * (1f - t * 0.2f));
            g.setColor(new Color(clamp(r), clamp(gr), clamp(b), oa));
            g.drawLine(0, y, W, y);
        }

        // ── Sun ─────────────────────────────────────────────────────────────
        int sunX = (int)(W * 0.72f);
        int sunY = horizonY;
        int sunR = (int)(H * 0.12f);
        // Outer glow
        for (int i = 6; i >= 0; i--) {
            float fr = (float) i / 6;
            int ga = (int)(opacity * (1f - fr) * 40);
            g.setColor(new Color(SUN_OUTER.getRed(), SUN_OUTER.getGreen(),
                                 SUN_OUTER.getBlue(), clamp(ga)));
            int r = sunR + i * 10;
            g.fillOval(sunX - r, sunY - r, r * 2, r * 2);
        }
        // Core
        g.setColor(new Color(SUN_INNER.getRed(), SUN_INNER.getGreen(),
                             SUN_INNER.getBlue(), clamp((int)(opacity * 220))));
        g.fillOval(sunX - sunR / 2, sunY - sunR / 2, sunR, sunR);

        // ── Road (perspective) ──────────────────────────────────────────────
        int roadBottomW = (int)(W * 0.15f);
        int roadTopW    = 4;
        float vanishX   = W * 0.72f;
        float vanishY   = horizonY;
        for (int y = H; y > horizonY; y--) {
            float t = (float)(H - y) / (H - horizonY);
            int rw = (int)(roadBottomW * (1f - t) + roadTopW * t);
            int rx = (int)(W * 0.48f * (1f - t) + vanishX * t) - rw / 2;
            // Edge
            g.setColor(new Color(ROAD_EDGE.getRed(), ROAD_EDGE.getGreen(),
                                 ROAD_EDGE.getBlue(), oa));
            g.fillRect(rx - 2, y, rw + 4, 1);
            // Road
            g.setColor(new Color(ROAD_COL.getRed(), ROAD_COL.getGreen(),
                                 ROAD_COL.getBlue(), oa));
            g.fillRect(rx, y, rw, 1);
        }

        // ── Hero walking ────────────────────────────────────────────────────
        float heroT = Math.min(1f, sp);
        // Interpolate position along road toward vanishing point
        float hx = W * 0.48f + (vanishX - W * 0.48f) * heroT * 0.85f;
        float hy = H * 0.65f + (vanishY - H * 0.65f) * heroT * 0.85f;
        float heroScale = H * 0.06f * (1f - heroT * 0.7f);

        int s = Math.max(4, (int) heroScale);
        int ihx = (int) hx, ihy = (int) hy;

        // Walk cycle
        boolean legForward = ((now / 400) % 2 == 0);

        // Shadow
        g.setColor(new Color(0, 0, 0, clamp((int)(opacity * 40))));
        g.fillOval(ihx - s / 2, ihy + s + 1, s, s / 5);

        // Legs
        g.setColor(new Color(HERO_BODY.getRed(), HERO_BODY.getGreen(),
                             HERO_BODY.getBlue(), oa));
        int legOff = legForward ? s / 6 : -s / 6;
        g.fillRect(ihx - s / 5 + legOff, ihy + s / 2, s / 5, s / 2 + 2);
        g.fillRect(ihx + s / 10 - legOff, ihy + s / 2, s / 5, s / 2 + 2);

        // Body
        g.fillRect(ihx - s / 3, ihy - s / 6, s * 2 / 3, s * 2 / 3);

        // Cape (billowing)
        float capeOff = (float)Math.sin(now / 350.0) * s / 6;
        g.setColor(new Color(HERO_CAPE.getRed(), HERO_CAPE.getGreen(),
                             HERO_CAPE.getBlue(), oa));
        g.fillRect(ihx - s / 3 - s / 4 + (int) capeOff, ihy - s / 8,
                   s / 4, s / 2);

        // Head
        g.setColor(new Color(HERO_BODY.getRed() + 10, HERO_BODY.getGreen() + 10,
                             HERO_BODY.getBlue() + 10, oa));
        g.fillRect(ihx - s / 6, ihy - s / 2, s / 3, s / 3);

        // ── Dust particles at feet ──────────────────────────────────────────
        for (int i = 0; i < DUST_COUNT; i++) {
            float dx = dustX[i] + dustVX[i] * sp * 3;
            float da = (1f - sp) * dustA[i] / 255f;
            if (da < 0.05f) continue;
            int dpx = ihx + (int) dx;
            int dpy = ihy + s;
            g.setColor(new Color(DUST_COL.getRed(), DUST_COL.getGreen(),
                                 DUST_COL.getBlue(), clamp((int)(da * opacity * 200))));
            g.fillRect(dpx, dpy, 2, 1);
        }

        // ── Ending-specific tint ────────────────────────────────────────────
        Color tint = switch (endingType) {
            case "new_serpent"   -> new Color(100, 60, 160, 15);
            case "champion"     -> new Color(GOD_COLORS[championGod.index].getRed(),
                                             GOD_COLORS[championGod.index].getGreen(),
                                             GOD_COLORS[championGod.index].getBlue(), 12);
            case "true_unbound" -> new Color(200, 180, 100, 12);
            default             -> null;
        };
        if (tint != null) {
            g.setColor(new Color(tint.getRed(), tint.getGreen(), tint.getBlue(),
                                 clamp((int)(tint.getAlpha() * opacity))));
            g.fillRect(0, 0, W, H);
        }

        // ── Narration ───────────────────────────────────────────────────────
        String narr = switch (endingType) {
            case "new_serpent"   -> "You were mortal once. You remember.";
            case "champion"     -> "The world belongs to " + championGod.displayName + " now. You walk a road that has no end.";
            case "true_unbound" -> "You walk a road of your own making. No god guides your step.";
            default             -> "The road continues. That is enough.";
        };
        float narrP = Math.min(1f, Math.max(0f, (sp - 0.2f) / 0.3f));
        if (narrP > 0) paintCenteredNarr(g, W, H, narr, narrP * opacity, F_BIG_NARR, (int)(H * 0.88f));
    }

    // ── SCENE 3: Title Card ─────────────────────────────────────────────────

    private void paintScene3(Graphics2D g, int W, int H, float sp) {
        // Fade from sunset remnants to black
        float bgFade = Math.min(1f, sp / 0.3f);
        g.setColor(new Color(0, 0, 0, clamp((int)(bgFade * 255))));
        g.fillRect(0, 0, W, H);

        // Title
        float titleP = Math.min(1f, Math.max(0f, (sp - 0.15f) / 0.3f));
        if (titleP > 0) {
            Color titleCol = switch (endingType) {
                case "new_serpent"   -> SERPENT_COL;
                case "champion"     -> GOD_COLORS[championGod.index];
                case "true_unbound" -> UNBOUND_COL;
                default             -> GENERIC_COL;
            };

            g.setFont(F_TITLE);
            FontMetrics fm = g.getFontMetrics();
            String title = endingTitle.toUpperCase();
            int tx = (W - fm.stringWidth(title)) / 2;
            int ty = H / 2 - 10;

            // Glow
            g.setColor(new Color(titleCol.getRed(), titleCol.getGreen(),
                                 titleCol.getBlue(), clamp((int)(titleP * 50))));
            g.drawString(title, tx - 1, ty - 1);
            g.drawString(title, tx + 1, ty + 1);

            // Main
            g.setColor(new Color(titleCol.getRed(), titleCol.getGreen(),
                                 titleCol.getBlue(), clamp((int)(titleP * 255))));
            g.drawString(title, tx, ty);

            // Subtitle
            g.setFont(F_SUB);
            FontMetrics fmS = g.getFontMetrics();
            String sub = "A RetroQuest Ending";
            int sx = (W - fmS.stringWidth(sub)) / 2;
            g.setColor(new Color(TEXT_DIM.getRed(), TEXT_DIM.getGreen(),
                                 TEXT_DIM.getBlue(), clamp((int)(titleP * 180))));
            g.drawString(sub, sx, ty + fm.getHeight() + 8);
        }
    }

    // ── Shared helpers ──────────────────────────────────────────────────────

    private void paintSmallHero(Graphics2D g, int cx, int cy, int s, float alpha, boolean glowing) {
        int a = clamp((int)(alpha * 220));
        // Body
        g.setColor(new Color(40, 45, 55, a));
        g.fillRect(cx - s / 3, cy - s / 4, s * 2 / 3, s * 3 / 4);
        // Head
        g.setColor(new Color(50, 55, 65, a));
        g.fillRect(cx - s / 6, cy - s / 2, s / 3, s / 4);
        // Cape
        g.setColor(new Color(HERO_CAPE.getRed(), HERO_CAPE.getGreen(),
                             HERO_CAPE.getBlue(), a));
        g.fillRect(cx - s / 3 - 2, cy - s / 6, s / 5, s / 2);

        if (glowing) {
            g.setColor(new Color(200, 230, 255, clamp((int)(alpha * 100))));
            g.fillOval(cx - s, cy - s / 2, s * 2, s);
        }
    }

    private void paintCenteredNarr(Graphics2D g, int W, int H, String text,
                                    float alpha, Font font, int baseY) {
        if (alpha < 0.01f) return;
        g.setFont(font);
        FontMetrics fm = g.getFontMetrics();
        java.util.List<String> lines = OverlayTheme.wordWrap(fm, text, W - 80);
        int lineH = fm.getHeight() + 3;
        int blockH = lines.size() * lineH;
        int sy = baseY - blockH / 2;

        // Background
        int maxW = 0;
        for (String l : lines) { int w = fm.stringWidth(l); if (w > maxW) maxW = w; }
        g.setColor(new Color(4, 6, 12, clamp((int)(alpha * 160))));
        g.fillRoundRect((W - maxW) / 2 - 12, sy - 8, maxW + 24, blockH + 16, 8, 8);

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            int lx = (W - fm.stringWidth(line)) / 2;
            int ly = sy + i * lineH + fm.getAscent();
            // Shadow
            g.setColor(new Color(0, 0, 0, clamp((int)(alpha * 160))));
            g.drawString(line, lx + 1, ly + 1);
            // Text
            g.setColor(new Color(TEXT_COL.getRed(), TEXT_COL.getGreen(),
                                 TEXT_COL.getBlue(), clamp((int)(alpha * 240))));
            g.drawString(line, lx, ly);
        }
    }

    private static int blend(int a, int b, float t) { return clamp((int)(a + t * (b - a))); }
    private static int clamp(int v) { return Math.max(0, Math.min(255, v)); }
}

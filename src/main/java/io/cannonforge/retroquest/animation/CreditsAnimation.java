package io.cannonforge.retroquest.animation;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import io.cannonforge.retroquest.core.Fonts;
import io.cannonforge.retroquest.core.Retroquest;
import io.cannonforge.retroquest.core.SoundManager;
import io.cannonforge.retroquest.model.God;
import io.cannonforge.retroquest.model.Player;

/**
 * Scrolling credits over an animated starfield (~18 s).
 *
 * <p>Shows the game title, ending name, player stats, god favor scores,
 * and a closing "Thank you for playing" message that holds centered.
 * ESC or Enter skips at any time.
 */
public class CreditsAnimation {

    private static final long ANIM_MS = AnimationSpeed.scale(18000);

    // ── Palette ─────────────────────────────────────────────────────────────
    private static final Color AMBER      = new Color(220, 180, 80);
    private static final Color PHOSPHOR   = new Color(  0, 255, 120);
    private static final Color DIM        = new Color(130, 118, 88);
    private static final Color DIVIDER    = new Color( 80, 70, 55);
    private static final Color THANK_COL  = new Color(255, 220, 140);

    private static final Font F_LOGO    = Fonts.monoBold(24);
    private static final Font F_ENDING  = Fonts.monoBold(16);
    private static final Font F_HEADING = Fonts.monoBold(14);
    private static final Font F_BODY    = Fonts.mono    (12);
    private static final Font F_SMALL   = Fonts.mono    (11);

    // God accent colours
    private static final Color[] GOD_COLORS = {
        new Color(180, 200, 255), new Color(255, 120,  40), new Color(140, 180, 255),
        new Color( 80, 220, 100), new Color( 60, 180, 200), new Color(160, 170, 200),
        new Color(200, 160,  60),
    };

    private static final Color SERPENT_COL = new Color(180, 120, 255);
    private static final Color UNBOUND_COL = new Color(220, 200, 140);
    private static final Color GENERIC_COL = new Color(160, 160, 170);

    // ── Starfield ───────────────────────────────────────────────────────────
    private static final int STAR_COUNT = 60;
    private final int[]   starX      = new int[STAR_COUNT];
    private final float[] starY      = new float[STAR_COUNT];
    private final float[] starSpeed  = new float[STAR_COUNT];
    private final int[]   starBright = new int[STAR_COUNT];

    // ── State ───────────────────────────────────────────────────────────────
    private final Retroquest game;
    private final String endingType;
    private final String endingTitle;
    private final God    championGod;

    private boolean active        = false;
    private boolean fired         = false;
    private boolean soundPlayed   = false;
    private boolean fastForwarded = false;
    private long    startTime;
    private javax.swing.Timer animTimer;

    public CreditsAnimation(Retroquest game, String endingType,
                             String endingTitle, God championGod) {
        this.game         = game;
        this.endingType   = endingType;
        this.endingTitle  = endingTitle;
        this.championGod  = championGod;
    }

    public boolean isActive() { return active; }

    /**
     * Any key, in two stages: the first press runs the scroll out to the closing
     * line so a reflex keypress cannot destroy the roll; the second leaves.
     */
    public void skip() {
        if (!active) return;
        if (!fastForwarded) {
            fastForwarded = true;
            // Jump to the moment the scroll finishes and "Thank you" settles.
            startTime = System.currentTimeMillis() - (long)(0.86f * ANIM_MS);
            return;
        }
        active = false;
        fired  = true;
        if (animTimer != null) animTimer.stop();
        SoundManager.getInstance().stopCreditsTheme();
        game.returnToTitle();
    }

    public void start() {
        java.util.Random rng = new java.util.Random(0xC3ED1);
        for (int i = 0; i < STAR_COUNT; i++) {
            starX[i]      = rng.nextInt(1000);
            starY[i]      = rng.nextFloat() * 1000;
            starSpeed[i]  = 15 + rng.nextFloat() * 40;
            starBright[i] = 100 + rng.nextInt(155);
        }

        fired         = false;
        soundPlayed   = false;
        fastForwarded = false;
        startTime     = System.currentTimeMillis();
        active        = true;

        if (animTimer != null) animTimer.stop();
        animTimer = new javax.swing.Timer(16, e -> {
            long elapsed = System.currentTimeMillis() - startTime;
            if (elapsed > ANIM_MS && !fired) {
                fired  = true;
                active = false;
                animTimer.stop();
                SoundManager.getInstance().stopCreditsTheme();
                game.returnToTitle();
            }
            if (game.getGamePanel() != null) game.getGamePanel().repaint();
        });
        animTimer.start();
    }

    // ── PAINT ───────────────────────────────────────────────────────────────

    public void paint(Graphics2D g, int W, int H) {
        if (!active) return;
        try { doPaint(g, W, H); }
        catch (Exception ex) { ex.printStackTrace(); active = false; }
    }

    private void doPaint(Graphics2D g, int W, int H) {
        long now     = System.currentTimeMillis();
        long elapsed = now - startTime;
        float p      = Math.min(1f, (float) elapsed / ANIM_MS);

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Sound
        if (!soundPlayed) {
            soundPlayed = true;
            SoundManager.getInstance().playCreditsTheme();
        }

        // ── Black background ────────────────────────────────────────────────
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, W, H);

        // ── Animated starfield ──────────────────────────────────────────────
        float dt = elapsed / 1000f;
        for (int i = 0; i < STAR_COUNT; i++) {
            int sx = starX[i] * W / 1000;
            float sy = starY[i] - starSpeed[i] * dt;
            sy = ((sy % 1000) + 1000) % 1000; // wrap around
            int syi = (int)(sy * H / 1000);
            int a = starBright[i];
            int size = (a > 200) ? 2 : 1;
            g.setColor(new Color(200, 210, 240, a));
            g.fillRect(sx, syi, size, size);
        }

        // ── Build credit lines ──────────────────────────────────────────────
        // Each entry: {font, color, text, extraSpacingAfter}
        Player player = game.getPlayer();
        int[] favors  = player.getFavorScores();
        java.util.List<Decision> decisions = buildDecisions(player);

        Color endCol = switch (endingType) {
            case "new_serpent"   -> SERPENT_COL;
            case "champion"     -> GOD_COLORS[championGod != null ? championGod.index : 0];
            case "true_unbound" -> UNBOUND_COL;
            default             -> GENERIC_COL;
        };

        // We'll compute y-positions as we go, then offset by scroll amount
        g.setFont(F_LOGO);
        FontMetrics fmLogo    = g.getFontMetrics(F_LOGO);
        FontMetrics fmEnding  = g.getFontMetrics(F_ENDING);
        FontMetrics fmHeading = g.getFontMetrics(F_HEADING);
        FontMetrics fmBody    = g.getFontMetrics(F_BODY);
        FontMetrics fmSmall   = g.getFontMetrics(F_SMALL);

        // Total content height (approximate)
        int totalH = 0;
        totalH += fmLogo.getHeight() + 60;     // RETROQUEST + gap
        totalH += fmEnding.getHeight() + 30;    // ending title + gap
        totalH += 20;                            // divider + gap
        totalH += fmHeading.getHeight() + 10;   // YOUR JOURNEY
        totalH += fmBody.getHeight() * 5 + 30;  // 5 stat lines + gap
        totalH += 20;                            // divider
        totalH += fmHeading.getHeight() + 10;   // THE SEVEN GODS
        totalH += fmBody.getHeight() * 7 + 30;  // 7 god lines + gap
        if (!decisions.isEmpty()) {             // WHAT YOU DECIDED
            totalH += 20 + fmHeading.getHeight() + 10
                    + fmBody.getHeight() * decisions.size() + 30;
        }
        totalH += 20;                            // divider
        totalH += fmHeading.getHeight() + 10;   // CREATED BY
        totalH += fmBody.getHeight() + 20;      // CannonForge
        totalH += fmSmall.getHeight() + 40;      // Built with Java...
        totalH += fmBody.getHeight() + H / 2;   // Thank you + padding

        // Scroll: content starts at bottom of screen, scrolls up
        float scrollRange = totalH + H;
        float scrollY = H - (p * 0.85f * scrollRange); // 85% of time scrolling, 15% holding

        // "Thank you" line position (we want it to hold at center)
        float thankYTarget = scrollY + totalH - fmBody.getHeight();
        boolean holdingThank = thankYTarget < H / 2f;

        float cy = scrollY; // current y cursor

        // ── RETROQUEST ──────────────────────────────────────────────────────
        drawCentered(g, W, cy, "RETROQUEST", F_LOGO, AMBER);
        cy += fmLogo.getHeight() + 60;

        // ── Ending title ────────────────────────────────────────────────────
        drawCentered(g, W, cy, "A " + endingTitle + " Ending", F_ENDING, endCol);
        cy += fmEnding.getHeight() + 30;

        // ── Divider ─────────────────────────────────────────────────────────
        drawDivider(g, W, cy);
        cy += 20;

        // ── YOUR JOURNEY ────────────────────────────────────────────────────
        drawCentered(g, W, cy, "YOUR JOURNEY", F_HEADING, AMBER);
        cy += fmHeading.getHeight() + 10;

        drawCentered(g, W, cy, "Hero: " + player.getName(), F_BODY, PHOSPHOR);
        cy += fmBody.getHeight();
        drawCentered(g, W, cy, "Level: " + player.getLevel(), F_BODY, PHOSPHOR);
        cy += fmBody.getHeight();
        drawCentered(g, W, cy, "Final HP: " + player.getHp() + " / " + player.getMaxHp(), F_BODY, PHOSPHOR);
        cy += fmBody.getHeight();
        drawCentered(g, W, cy, "Gold: " + player.getGold(), F_BODY, PHOSPHOR);
        cy += fmBody.getHeight();
        drawCentered(g, W, cy, "Ending: " + endingTitle, F_BODY, PHOSPHOR);
        cy += fmBody.getHeight() + 30;

        // ── Divider ─────────────────────────────────────────────────────────
        drawDivider(g, W, cy);
        cy += 20;

        // ── THE SEVEN GODS ──────────────────────────────────────────────────
        drawCentered(g, W, cy, "THE SEVEN GODS", F_HEADING, AMBER);
        cy += fmHeading.getHeight() + 10;

        for (God god : God.values()) {
            String dots = " ";
            int nameLen = god.displayName.length();
            for (int i = nameLen; i < 12; i++) dots += ".";
            String line = god.displayName + dots + " " + favors[god.index];
            drawCentered(g, W, cy, line, F_BODY, GOD_COLORS[god.index]);
            cy += fmBody.getHeight();
        }
        cy += 30;

        // ── WHAT YOU DECIDED ────────────────────────────────────────────────
        if (!decisions.isEmpty()) {
            drawDivider(g, W, cy);
            cy += 20;

            drawCentered(g, W, cy, "WHAT YOU DECIDED", F_HEADING, AMBER);
            cy += fmHeading.getHeight() + 10;

            for (Decision d : decisions) {
                drawCentered(g, W, cy, d.text(), F_BODY, d.color());
                cy += fmBody.getHeight();
            }
            cy += 30;
        }

        // ── Divider ─────────────────────────────────────────────────────────
        drawDivider(g, W, cy);
        cy += 20;

        // ── CREATED BY ──────────────────────────────────────────────────────
        drawCentered(g, W, cy, "CREATED BY", F_HEADING, AMBER);
        cy += fmHeading.getHeight() + 10;

        drawCentered(g, W, cy, "CannonForge", F_BODY, AMBER);
        cy += fmBody.getHeight() + 20;

        drawCentered(g, W, cy, "Built with Java and dreams.", F_SMALL, DIM);
        cy += fmSmall.getHeight() + 40;

        // ── Thank you (holds centered) ──────────────────────────────────────
        float thankY = holdingThank ? H / 2f : cy;
        float pulse = 0.6f + 0.4f * (float)Math.sin(elapsed / 800.0);
        int ta = clamp((int)(pulse * 255));
        g.setFont(F_BODY);
        FontMetrics fmT = g.getFontMetrics();
        String thanks = "Thank you for playing.";
        int tx = (W - fmT.stringWidth(thanks)) / 2;
        g.setColor(new Color(THANK_COL.getRed(), THANK_COL.getGreen(),
                             THANK_COL.getBlue(), ta));
        g.drawString(thanks, tx, (int) thankY + fmT.getAscent());

        // ── Fade in from black at start ─────────────────────────────────────
        if (p < 0.05f) {
            int fa = (int)((1f - p / 0.05f) * 255);
            g.setColor(new Color(0, 0, 0, fa));
            g.fillRect(0, 0, W, H);
        }
    }

    // ── What the player decided ─────────────────────────────────────────────

    /** One line of the ledger: the choice, in the colour of the god who watched. */
    private record Decision(String text, Color color) {}

    /**
     * Reads the choice flags the game records all the way through and lists the
     * ones that stuck. The epilogue gives each of these a paragraph; here they
     * are the ledger, next to the favor scores, so the roll reflects a specific
     * playthrough rather than a generic one.
     */
    private java.util.List<Decision> buildDecisions(Player player) {
        java.util.List<Decision> out = new java.util.ArrayList<>();
        if (player == null) return out;

        String bellorak = player.getFlag("bellorak_trial_complete");
        if (player.hasFlag("seraphine_freed") || "free_her".equals(bellorak)) {
            out.add(new Decision(row("Seraphine", "freed"),      GOD_COLORS[6]));
        } else if ("grant_peace".equals(bellorak)) {
            out.add(new Decision(row("Seraphine", "released"),   GOD_COLORS[6]));
        } else if ("absorb_power".equals(bellorak)) {
            out.add(new Decision(row("Seraphine", "consumed"),   GOD_COLORS[6]));
        }

        String umbryn = player.getFlag("umbryn_trial_complete");
        if ("shared_burden".equals(umbryn)) {
            out.add(new Decision(row("The Archive", "shared"),   GOD_COLORS[5]));
        } else if ("let_forget".equals(umbryn)) {
            out.add(new Decision(row("The Archive", "let go"),   GOD_COLORS[5]));
        } else if ("must_endure".equals(umbryn)) {
            out.add(new Decision(row("The Archive", "kept"),     GOD_COLORS[5]));
        }

        String thalorax = player.getFlag("thalorax_trial_complete");
        if ("one_god".equals(thalorax)) {
            out.add(new Decision(row("The Deep", "one god"),     GOD_COLORS[4]));
        } else if ("destroy_gods".equals(thalorax)) {
            out.add(new Decision(row("The Deep", "no gods"),     GOD_COLORS[4]));
        } else if ("aqualon_wakes".equals(thalorax)) {
            out.add(new Decision(row("The Deep", "wake it"),     GOD_COLORS[4]));
        } else if ("refused".equals(thalorax)) {
            out.add(new Decision(row("The Deep", "silence"),     GOD_COLORS[4]));
        }

        String varsa = player.getFlag("varsa_choice_made");
        if ("extracted".equals(varsa)) {
            out.add(new Decision(row("Varsa", "surfaced"),       GOD_COLORS[0]));
        } else if ("sent_deep".equals(varsa)) {
            out.add(new Decision(row("Varsa", "lost below"),     GOD_COLORS[0]));
        }

        return out;
    }

    /** "Seraphine ...... freed" — same dotted shape as the god scores above. */
    private static String row(String label, String value) {
        StringBuilder sb = new StringBuilder(label).append(' ');
        for (int i = label.length(); i < 13; i++) sb.append('.');
        return sb.append(' ').append(value).toString();
    }

    // ── Drawing helpers ─────────────────────────────────────────────────────

    private void drawCentered(Graphics2D g, int W, float y, String text,
                               Font font, Color color) {
        if (y < -40 || y > g.getClipBounds().height + 40) return; // off-screen
        g.setFont(font);
        FontMetrics fm = g.getFontMetrics();
        int tx = (W - fm.stringWidth(text)) / 2;
        g.setColor(color);
        g.drawString(text, tx, (int) y + fm.getAscent());
    }

    private void drawDivider(Graphics2D g, int W, float y) {
        if (y < -10 || y > g.getClipBounds().height + 10) return;
        g.setColor(DIVIDER);
        int dw = Math.min(300, W - 60);
        g.drawLine((W - dw) / 2, (int) y, (W + dw) / 2, (int) y);
    }

    private static int clamp(int v) { return Math.max(0, Math.min(255, v)); }
}

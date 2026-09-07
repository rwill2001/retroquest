package io.cannonforge.retroquest.overlay;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.Map;

import io.cannonforge.retroquest.core.Fonts;
import io.cannonforge.retroquest.core.Retroquest;
import io.cannonforge.retroquest.core.SoundManager;
import io.cannonforge.retroquest.model.Boon;
import io.cannonforge.retroquest.model.God;
import io.cannonforge.retroquest.model.Player;

/**
 * Cinematic overlay that shows a god's farewell message the first time the
 * player uses a forward island portal (graduation).  Typewriter text, god-
 * specific accent colour, fade-in, then fires a callback on dismiss that
 * starts the actual teleport animation.
 */
public class DivineAudienceOverlay {

    /** Kept so the audience can read the player's standing with all seven gods. */
    private final Retroquest game;

    private boolean active;
    private long    entryTime;
    /** Wall time the typewriter finished, used to fade the standing panel in. */
    private long    revealTime;

    private God      currentGod;
    private Color    accentColor;
    private String   fullText = "";
    private int      revealedChars;
    private long     lastCharTime;
    private boolean  textFullyRevealed;
    private Runnable onDismiss;

    // ── Constants ────────────────────────────────────────────────────────────
    private static final long ENTRY_MS       = 400;
    private static final int  CHARS_PER_SEC  = 30;
    private static final long CHAR_DELAY_MS  = 1000 / CHARS_PER_SEC;
    private static final long CURSOR_BLINK   = 500;
    private static final Font F_GOD_NAME     = Fonts.monoBold(20);
    private static final Font F_DOMAIN       = OverlayTheme.F_DESC;
    private static final Font F_BODY         = OverlayTheme.F_ITEM;
    private static final Font F_STAND_HEAD   = Fonts.monoBold(10);
    private static final Font F_STAND        = Fonts.mono    (10);

    /** Fade-in for the standing panel once the god has finished speaking. */
    private static final long STAND_FADE_MS  = 450;

    // ── God accent colours ───────────────────────────────────────────────────
    private static final Map<God, Color> GOD_COLORS = Map.of(
        God.LIRANDEL,  new Color(180, 200, 255),   // silver-blue
        God.PYRALIS,   new Color(255, 120,  40),   // fiery orange
        God.ZEPHYRION, new Color(140, 180, 255),   // storm blue
        God.SYLVANDAR, new Color( 80, 220, 100),   // verdant green
        God.THALORAX,  new Color( 60, 180, 200),   // deep teal
        God.UMBRYN,    new Color(160, 170, 200),    // silver-grey
        God.BELLORAK,  new Color(200, 160,  60)    // war gold
    );

    // ── Departing-god lookup ─────────────────────────────────────────────────
    private static final Map<String, God> OVERWORLD_TO_GOD = Map.of(
        "lirandel",  God.LIRANDEL,
        "pyralis",   God.PYRALIS,
        "zephyrion", God.ZEPHYRION,
        "sylvandar", God.SYLVANDAR,
        "thalorax",  God.THALORAX,
        "umbryn",    God.UMBRYN,
        "bellorak",  God.BELLORAK
    );

    public DivineAudienceOverlay(Retroquest game) {
        this.game = game;
    }

    // ── Lifecycle ────────────────────────────────────────────────────────────

    public void open(God god, String message, Runnable onDismiss) {
        this.currentGod        = god;
        this.accentColor       = GOD_COLORS.getOrDefault(god, OverlayTheme.CYAN_ACC);
        this.fullText          = message;
        this.revealedChars     = 0;
        this.lastCharTime      = System.currentTimeMillis();
        this.textFullyRevealed = false;
        this.onDismiss         = onDismiss;
        this.entryTime         = System.currentTimeMillis();
        this.revealTime        = 0;
        this.active            = true;
        SoundManager.getInstance().playAltarChime();
    }

    public void close() {
        active = false;
        Runnable cb = onDismiss;
        onDismiss = null;
        if (cb != null) cb.run();
    }

    public boolean isActive() { return active; }

    // ── Input ────────────────────────────────────────────────────────────────

    public void handleKey(KeyEvent e) {
        if (!active) return;
        if (!textFullyRevealed) {
            revealedChars     = fullText.length();
            textFullyRevealed = true;
            return;
        }
        int code = e.getKeyCode();
        if (code == KeyEvent.VK_ENTER || code == KeyEvent.VK_ESCAPE
                || code == KeyEvent.VK_SPACE) {
            close();
        }
    }

    public void handleClick(int mx, int my) {
        if (!active) return;
        if (!textFullyRevealed) {
            revealedChars     = fullText.length();
            textFullyRevealed = true;
            return;
        }
        close();
    }

    // ── Rendering ────────────────────────────────────────────────────────────

    public void paint(Graphics2D g, int W, int H) {
        try { doPaint(g, W, H); }
        catch (Exception ignored) { /* never crash the render loop */ }
    }

    private void doPaint(Graphics2D g, int W, int H) {
        if (!active) return;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                           RenderingHints.VALUE_ANTIALIAS_ON);

        float ease = OverlayTheme.entryEase(entryTime, ENTRY_MS);

        // ── Advance typewriter ───────────────────────────────────────────────
        long now = System.currentTimeMillis();
        if (!textFullyRevealed && revealedChars < fullText.length()) {
            int prev = revealedChars;
            while (now - lastCharTime >= CHAR_DELAY_MS
                    && revealedChars < fullText.length()) {
                revealedChars++;
                lastCharTime += CHAR_DELAY_MS;
            }
            if (revealedChars > prev) {
                char c = fullText.charAt(revealedChars - 1);
                if (c != ' ' && c != '\n') {
                    SoundManager.getInstance().play("textblip");
                }
            }
            if (revealedChars >= fullText.length()) {
                textFullyRevealed = true;
            }
        }
        if (textFullyRevealed && revealTime == 0) revealTime = now;

        // ── Full-screen dim ──────────────────────────────────────────────────
        int dimAlpha = (int)(200 * ease);
        g.setColor(new Color(0, 0, 0, dimAlpha));
        g.fillRect(0, 0, W, H);

        // ── Standing panel metrics (drives the card height) ──────────────────
        FontMetrics fmStand = g.getFontMetrics(F_STAND);
        int standRowH   = fmStand.getHeight() + OverlayTheme.scaled(2);
        int standPanelH = fmStand.getHeight() + OverlayTheme.scaled(8) + 4 * standRowH;

        // ── Card geometry ────────────────────────────────────────────────────
        int cardW = Math.min(OverlayTheme.scaled(600), W - OverlayTheme.scaled(50));
        int cardH = Math.min(OverlayTheme.scaled(348) + standPanelH,
                             H - OverlayTheme.scaled(40));
        int cx    = (W - cardW) / 2;
        int cy    = (int)((H - cardH) / 2 - OverlayTheme.scaled(20) * (1f - ease));

        // ── Card background (gradient from dark accent tint to BG) ───────────
        Color darkTint = new Color(
            accentColor.getRed()   / 10,
            accentColor.getGreen() / 10,
            accentColor.getBlue()  / 10);
        g.setPaint(new GradientPaint(cx, cy, darkTint,
                                     cx, cy + cardH, OverlayTheme.BG));
        g.fillRoundRect(cx, cy, cardW, cardH, 12, 12);

        // ── Card border ──────────────────────────────────────────────────────
        g.setColor(new Color(accentColor.getRed(), accentColor.getGreen(),
                             accentColor.getBlue(), 160));
        g.setStroke(new BasicStroke(1.5f));
        g.drawRoundRect(cx, cy, cardW, cardH, 12, 12);

        // ── God name ─────────────────────────────────────────────────────────
        g.setFont(F_GOD_NAME);
        FontMetrics fmName = g.getFontMetrics();
        String name = currentGod.displayName;
        int nameX = cx + (cardW - fmName.stringWidth(name)) / 2;
        int nameY = cy + fmName.getAscent() + OverlayTheme.scaled(10);
        g.setColor(accentColor);
        g.drawString(name, nameX, nameY);

        // ── Domain subtitle ──────────────────────────────────────────────────
        g.setFont(F_DOMAIN);
        FontMetrics fmDom = g.getFontMetrics();
        String domain = currentGod.domain;
        int domX = cx + (cardW - fmDom.stringWidth(domain)) / 2;
        int domY = nameY + fmDom.getHeight() + 2;
        g.setColor(OverlayTheme.TEXT_DIM);
        g.drawString(domain, domX, domY);

        // ── Horizontal divider ───────────────────────────────────────────────
        int divY = domY + 10;
        g.setColor(new Color(accentColor.getRed(), accentColor.getGreen(),
                             accentColor.getBlue(), 80));
        g.setStroke(new BasicStroke(1f));
        g.drawLine(cx + 20, divY, cx + cardW - 20, divY);

        // ── Message body (typewriter) ────────────────────────────────────────
        g.setFont(F_BODY);
        FontMetrics fmBody = g.getFontMetrics();
        int bodyX = cx + 24;
        int bodyMaxW = cardW - 48;
        int bodyY = divY + 16;

        String visible = fullText.substring(0, revealedChars);
        List<String> lines = OverlayTheme.wordWrap(fmBody, visible, bodyMaxW);

        g.setColor(OverlayTheme.TEXT_BRIGHT);
        int lineH = fmBody.getHeight();
        for (int i = 0; i < lines.size(); i++) {
            g.drawString(lines.get(i), bodyX, bodyY + i * lineH);
        }

        // ── Blinking cursor ──────────────────────────────────────────────────
        if (!textFullyRevealed) {
            boolean blink = ((now - entryTime) / CURSOR_BLINK) % 2 == 0;
            if (blink && !lines.isEmpty()) {
                String last = lines.get(lines.size() - 1);
                int cursorX = bodyX + fmBody.stringWidth(last);
                int cursorY = bodyY + (lines.size() - 1) * lineH;
                g.setColor(accentColor);
                g.fillRect(cursorX + 2, cursorY - fmBody.getAscent() + 2,
                           2, fmBody.getHeight() - 2);
            }
        }

        // ── Standing with the seven ──────────────────────────────────────────
        int barY = cy + cardH - OverlayTheme.scaled(30);
        if (revealTime > 0) {
            float standAlpha = Math.min(1f, (float)(now - revealTime) / STAND_FADE_MS);
            int panelBottom  = barY - OverlayTheme.scaled(6);
            paintStandingPanel(g, cx, cardW, panelBottom - standPanelH,
                               standRowH, fmStand, standAlpha);
        }

        // ── Continue prompt ──────────────────────────────────────────────────
        if (textFullyRevealed) {
            OverlayTheme.paintKeyBadges(g, cx, barY, cardW, OverlayTheme.scaled(26),
                new String[][] { { "Enter", "Continue" } }, accentColor);
        }
    }

    /**
     * Draws the player's standing with all seven gods along the foot of the
     * audience card.
     *
     * <p>Favor is the quantity that decides the ending, and before this panel
     * existed the player never saw it once in fifteen hours of play. A god's
     * farewell is the natural place to read it: every island exit shows you the
     * ledger the Cradle will settle.
     */
    private void paintStandingPanel(Graphics2D g, int cx, int cardW, int top,
                                    int rowH, FontMetrics fmStand, float alpha) {
        if (game == null || game.getPlayer() == null) return;
        int[] favors = game.getPlayer().getFavorScores();

        // Divider above the panel
        g.setColor(new Color(accentColor.getRed(), accentColor.getGreen(),
                             accentColor.getBlue(), clampA(60 * alpha)));
        g.setStroke(new BasicStroke(1f));
        g.drawLine(cx + OverlayTheme.scaled(20), top - OverlayTheme.scaled(6),
                   cx + cardW - OverlayTheme.scaled(20), top - OverlayTheme.scaled(6));

        // Heading
        g.setFont(F_STAND_HEAD);
        FontMetrics fmHead = g.getFontMetrics();
        int headY = top + fmHead.getAscent();
        g.setColor(new Color(accentColor.getRed(), accentColor.getGreen(),
                             accentColor.getBlue(), clampA(200 * alpha)));
        g.drawString("AS THE SEVEN SEE YOU", cx + OverlayTheme.scaled(24), headY);

        String legend = "50 IS NEUTRAL";
        g.setColor(new Color(OverlayTheme.TEXT_DIM.getRed(), OverlayTheme.TEXT_DIM.getGreen(),
                             OverlayTheme.TEXT_DIM.getBlue(), clampA(150 * alpha)));
        g.drawString(legend,
                     cx + cardW - OverlayTheme.scaled(24) - fmHead.stringWidth(legend),
                     headY);

        // Two columns: Lirandel..Sylvandar, then Thalorax..Bellorak
        g.setFont(F_STAND);
        int colW    = (cardW - OverlayTheme.scaled(48)) / 2;
        int rowsTop = top + fmHead.getHeight() + OverlayTheme.scaled(6);
        int barW    = OverlayTheme.scaled(52);
        int barH    = Math.max(3, OverlayTheme.scaled(5));
        int nameW   = fmStand.stringWidth("Zephyrion ");

        for (God god : God.values()) {
            int col = god.index / 4;
            int row = god.index % 4;
            int colX = cx + OverlayTheme.scaled(24) + col * colW;
            int baseY = rowsTop + row * rowH + fmStand.getAscent();
            int favor = favors[god.index];
            boolean here = (god == currentGod);

            Color gc = GOD_COLORS.getOrDefault(god, OverlayTheme.TEXT_BRIGHT);

            // Name (the speaking god is marked and drawn bright)
            g.setColor(new Color(gc.getRed(), gc.getGreen(), gc.getBlue(),
                                 clampA((here ? 245 : 165) * alpha)));
            g.drawString((here ? "\u25b8" : " ") + god.displayName, colX, baseY);

            // Favor bar
            int barX = colX + nameW;
            int barY2 = baseY - fmStand.getAscent() / 2 - barH / 2;
            g.setColor(new Color(255, 255, 255, clampA(28 * alpha)));
            g.fillRect(barX, barY2, barW, barH);
            g.setColor(new Color(gc.getRed(), gc.getGreen(), gc.getBlue(),
                                 clampA((here ? 235 : 175) * alpha)));
            g.fillRect(barX, barY2, Math.max(1, barW * Math.max(0, Math.min(100, favor)) / 100), barH);

            // Number and the word for it
            String read = String.format("%3d %s", favor, God.standingLabel(favor));
            g.setColor(new Color(OverlayTheme.TEXT_BRIGHT.getRed(),
                                 OverlayTheme.TEXT_BRIGHT.getGreen(),
                                 OverlayTheme.TEXT_BRIGHT.getBlue(),
                                 clampA((here ? 230 : 155) * alpha)));
            g.drawString(read, barX + barW + OverlayTheme.scaled(6), baseY);
        }
    }

    private static int clampA(float v) {
        return Math.max(0, Math.min(255, (int) v));
    }

    // ── Static helpers ───────────────────────────────────────────────────────

    /** Returns the god associated with the given overworld, or {@code null}. */
    public static God getDepartingGod(String overworldName) {
        if (overworldName == null) return null;
        return OVERWORLD_TO_GOD.get(overworldName.toLowerCase());
    }

    /** Picks a message variant based on the player's choices on the god's island. */
    public static String selectMessage(God god, Player player) {
        return switch (god) {
            case LIRANDEL  -> selectLirandel(player);
            case PYRALIS   -> selectPyralis(player);
            case ZEPHYRION -> selectZephyrion(player);
            case SYLVANDAR -> selectSylvandar(player);
            case THALORAX  -> selectThalorax(player);
            case UMBRYN    -> selectUmbryn(player);
            case BELLORAK  -> selectBellorak(player);
            default        -> "Go forth, mortal. Your journey continues.";
        };
    }

    private static String selectLirandel(Player player) {
        if (player.hasBoon(Boon.MOONBLESSED)) {
            return "You chose the path of mercy, and my light will follow you "
                 + "into the flames. The forges of Pyralis will test your resolve, "
                 + "but remember \u2014 true strength is not in the strike, but in "
                 + "the hand that stays. Go with my blessing, child of the moon.";
        }
        if (player.hasBoon(Boon.PYRALIS_TEMPER)) {
            return "You took the fire's gift over my own. I do not condemn you "
                 + "\u2014 every mortal must choose their own path. But know that "
                 + "Pyralis's flames consume as readily as they forge. Tread "
                 + "carefully in the land of ambition.";
        }
        return "You pass through my domain like a leaf on the wind \u2014 unbound, "
             + "uncommitted. Perhaps that is wisdom, or perhaps it is avoidance. "
             + "The forges ahead will demand a decision you cannot defer.";
    }

    private static String selectPyralis(Player player) {
        int favor = player.getFavor(God.PYRALIS);
        if (favor >= 70) {
            return "You have earned the respect of the forge, mortal. Your ambition "
                 + "burns clean and bright. The storms of Zephyrion will try to "
                 + "scatter what I have tempered. Stand firm \u2014 you are steel "
                 + "now, not slag.";
        }
        if (favor <= 30) {
            return "You pass through my domain having refused the forge's lessons. "
                 + "Zephyrion's winds care nothing for the untempered. I wonder if "
                 + "you will bend or break.";
        }
        return "The forge judges all who enter, and you have survived its heat. "
             + "Whether you carry wisdom or merely scars, only the storms ahead "
             + "will reveal. Go, and let the wind test what fire has shaped.";
    }

    private static String selectZephyrion(Player player) {
        if (player.hasBoon(Boon.ZEPHYRION_GRACE)) {
            return "My winds have found you worthy. You danced with the storm and "
                 + "did not falter. The deep roots of Sylvandar's forest will feel "
                 + "strange after the open sky \u2014 but change, as always, is the "
                 + "only constant. Carry my grace into the green.";
        }
        if (player.getFavor(God.ZEPHYRION) >= 70) {
            return "You understood the lesson of the tempest \u2014 that change is "
                 + "not destruction but renewal. Sylvandar's ancient memory waits "
                 + "ahead. The forest remembers what the wind forgets.";
        }
        return "The storm passes, as all things must. You survived my domain, "
             + "though whether by skill or luck remains to be seen. Sylvandar's "
             + "roots run deep \u2014 deeper than any wind can reach. Perhaps there "
             + "you will find what the storm could not teach you.";
    }

    private static String selectSylvandar(Player player) {
        if (player.hasBoon(Boon.SYLVANDAR_ROOTS)) {
            return "My roots are woven into your flesh now, mortal. You carry the "
                 + "memory of the forest wherever you go. The abyss below knows "
                 + "nothing of growth \u2014 only pressure and darkness. But what "
                 + "grows in darkness grows strong. Descend without fear.";
        }
        return "You walked beneath my canopy and breathed the ancient air. Whether "
             + "you listened to the whispers of the roots, only you know. The deep "
             + "waters of Thalorax await \u2014 cold, crushing, and patient. What the "
             + "forest nurtures, the ocean devours. Go prepared.";
    }

    private static String selectThalorax(Player player) {
        String choice = player.getFlag("thalorax_trial_complete");
        if ("one_god".equals(choice)) {
            return "You chose a single god to rule. A bold choice \u2014 or a foolish "
                 + "one. The shadows ahead remember every throne that was ever claimed.";
        }
        if ("destroy_gods".equals(choice)) {
            return "You would unmake the divine. The abyss respects your audacity, "
                 + "if nothing else. What waits beyond my domain has no gods to "
                 + "destroy \u2014 only silence.";
        }
        if ("aqualon_wakes".equals(choice)) {
            return "You chose to awaken what sleeps beneath all things. The tremors "
                 + "have already begun. Go forward, mortal \u2014 you have set something "
                 + "in motion that cannot be stopped.";
        }
        if ("refused".equals(choice)) {
            return "You stood at the precipice and turned away. The deep does not "
                 + "judge \u2014 it simply waits. Perhaps you are wiser than the others. "
                 + "Or perhaps you are merely afraid.";
        }
        return "You crossed my domain without facing the trial. The pressure did not "
             + "break you, but neither did it forge you. What lies beyond will not be "
             + "so indifferent.";
    }

    private static String selectUmbryn(Player player) {
        String choice = player.getFlag("umbryn_trial_complete");
        if ("shared_burden".equals(choice)) {
            return "You chose to carry my grief alongside your own. Few mortals "
                 + "have the strength for that. The weight will never leave you "
                 + "\u2014 but neither will the understanding it brings. Bellorak's "
                 + "war awaits. Fight wisely, rememberer.";
        }
        if ("let_forget".equals(choice)) {
            return "You gave me the gift of release. The memories scatter like "
                 + "silver dust, and I am lighter than I have been in millennia. "
                 + "Whether the truth was worth preserving\u2026 that question now "
                 + "belongs to you. Go forward, and be kind.";
        }
        if ("must_endure".equals(choice)) {
            return "You told me the memories are too important to lose, then "
                 + "refused to share the cost. A practical choice. Perhaps the "
                 + "right one. I will endure \u2014 as I always have. The arena "
                 + "of Bellorak demands a different kind of strength.";
        }
        return "You passed through my domain of shadows and silence without "
             + "facing the trial. The memories remain unresolved. What waits "
             + "on Bellorak's war-torn shores will not grant you such distance.";
    }

    private static String selectBellorak(Player player) {
        String choice = player.getFlag("bellorak_trial_complete");
        if ("grant_peace".equals(choice)) {
            return "You gave my champion the death she earned. That is the "
                 + "warrior\u2019s mercy \u2014 the hardest mercy of all. You carry "
                 + "the Key of Iron now. The Cradle of Shards awaits, and what "
                 + "you find there will test bonds stronger than any sword.";
        }
        if ("absorb_power".equals(choice)) {
            return "You took what you wanted. I can respect that \u2014 a warrior "
                 + "takes. But you consumed the last of Seraphine, and that "
                 + "power will burn in you until the very end. The Cradle "
                 + "awaits. I wonder what you will take from it.";
        }
        if ("free_her".equals(choice)) {
            return "You did what I could not. You freed her without a blade. "
                 + "For the first time in ten thousand years, I remember what "
                 + "bonds felt like before they were forged in blood. Go to "
                 + "the Cradle, Unbound. You have earned what waits there.";
        }
        return "You left my arena without facing the trial. The war rages "
             + "on, and Seraphine still fights. The Cradle of Shards will "
             + "not be so patient with the undecided.";
    }
}

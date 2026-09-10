package io.cannonforge.retroquest.overlay;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.util.Map;

import io.cannonforge.retroquest.core.Fonts;
import io.cannonforge.retroquest.core.Retroquest;
import io.cannonforge.retroquest.model.Boon;
import io.cannonforge.retroquest.model.God;
import io.cannonforge.retroquest.model.Player;

/**
 * Cinematic overlay that shows a god's farewell message the first time the
 * player uses a forward island portal (graduation).  Typewriter text, god-
 * specific accent colour, fade-in, then fires a callback on dismiss that
 * starts the actual teleport animation.
 *
 * <p>The card itself is {@link RevelationOverlay}; what remains here is the part that is
 * actually about gods — which one is speaking, their colour, the message chosen from the
 * player's history with them, and the standing-with-the-seven panel drawn along the foot.
 * The presentation was pulled out so that anything else with something to say — a thing at
 * the bottom of a dungeon, for one — gets the same timing rather than a second copy of it.
 */
public class DivineAudienceOverlay {

    /** Kept so the audience can read the player's standing with all seven gods. */
    private final Retroquest game;

    /** The card. Everything about drawing, typing and dismissing lives in here. */
    private final RevelationOverlay card = new RevelationOverlay();

    private God   currentGod;
    private Color accentColor = OverlayTheme.CYAN_ACC;

    // ── Constants ────────────────────────────────────────────────────────────
    private static final Font F_STAND_HEAD   = Fonts.monoBold(10);
    private static final Font F_STAND        = Fonts.mono    (10);

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

    /** The accent colour a god speaks in. Shared so other screens can match it. */
    public static Color colorFor(God god) {
        return GOD_COLORS.getOrDefault(god, OverlayTheme.CYAN_ACC);
    }

    // ── Lifecycle ────────────────────────────────────────────────────────────

    public void open(God god, String message, Runnable onDismiss) {
        this.currentGod  = god;
        this.accentColor = colorFor(god);
        card.open(god.displayName, god.domain, accentColor, message,
                  new StandingFooter(), onDismiss);
    }

    public void close()      { card.close(); }
    public boolean isActive(){ return card.isActive(); }

    // ── Input ────────────────────────────────────────────────────────────────

    public void handleKey(KeyEvent e)          { card.handleKey(e); }
    public void handleClick(int mx, int my)    { card.handleClick(mx, my); }

    // ── Rendering ────────────────────────────────────────────────────────────

    public void paint(Graphics2D g, int W, int H) { card.paint(g, W, H); }

    /**
     * Draws the player's standing with all seven gods along the foot of the
     * audience card.
     *
     * <p>Favor is the quantity that decides the ending, and before this panel
     * existed the player never saw it once in fifteen hours of play. A god's
     * farewell is the natural place to read it: every island exit shows you the
     * ledger the Cradle will settle.
     */
    private final class StandingFooter implements RevelationOverlay.Footer {

        @Override
        public int height(Graphics2D g) {
            FontMetrics fmStand = g.getFontMetrics(F_STAND);
            return fmStand.getHeight() + OverlayTheme.scaled(8)
                 + 4 * (fmStand.getHeight() + OverlayTheme.scaled(2));
        }

        @Override
        public void paint(Graphics2D g, int cx, int cardW, int top, float alpha) {
            if (game == null || game.getPlayer() == null) return;
            FontMetrics fmStand = g.getFontMetrics(F_STAND);
            int rowH = fmStand.getHeight() + OverlayTheme.scaled(2);
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
                g.drawString((here ? "▸" : " ") + god.displayName, colX, baseY);

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
    }

    private static int clampA(float v) {
        return RevelationOverlay.clampAlpha(v);
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

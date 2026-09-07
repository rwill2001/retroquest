package io.cannonforge.retroquest.overlay;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

import io.cannonforge.retroquest.core.Fonts;
import io.cannonforge.retroquest.core.Retroquest;
import io.cannonforge.retroquest.core.SoundManager;
import io.cannonforge.retroquest.model.God;
import io.cannonforge.retroquest.model.InventorySlot;
import io.cannonforge.retroquest.model.Player;

/**
 * Full-screen choice overlay for the Cradle of Shards endgame.
 *
 * <p>Every path is always listed, whether or not the player qualifies. A path
 * the player cannot walk is drawn sealed, with the exact reason spelled out
 * in-fiction, so the final decision of the game is a decision and not a lottery
 * over bookkeeping the player was never shown.
 *
 * <p><b>The gates</b> (constants below) are tuned to what the shipped content
 * can actually produce:
 * <ul>
 *   <li><b>Champion</b> &mdash; favor only ever rises in this game (there is a
 *       single -5 in the entire codebase), so the old "80 with one god, 20 or
 *       less with all six others" could never be satisfied by anyone. The gate
 *       is now a <em>lead</em>: 80 with one god, 20 clear of every rival.</li>
 *   <li><b>True Unbound</b> &mdash; the old "everyone between 30 and 70" was
 *       true at character creation and was destroyed by playing the game. The
 *       gate is now a floor plus a spread: every god must stand at 55 (you
 *       sought all seven out) and none may tower more than 35 over the least
 *       (you kept no favourite).</li>
 *   <li><b>New Serpent</b> &mdash; the old check wanted seven items whose id
 *       began with {@code shard_of_corruption}; only two ids in the game do,
 *       and the per-island Shards of Ambition, Freedom and Endless Growth were
 *       never counted at all. The gate now counts every island Shard and asks
 *       for four of the seven.</li>
 * </ul>
 */
public class CradleChoiceOverlay {

    // -- Endgame gates -------------------------------------------------------

    /**
     * Favor a god must hold in you before they will name you their Champion.
     *
     * <p>Counted against what the world actually hands out, the old 80 was unreachable.
     * Every favor award in the game is tied to finishing a quest or picking a branch at
     * one of the seven trials, and a player who does everything ends on roughly
     * Lirandel 60, Pyralis 68, Zephyrion 80, Sylvandar 60, Thalorax 60, Umbryn 70,
     * Bellorak 65 — one god at the old threshold, and never 20 clear of the rest, so the
     * Champion ending could not be reached by any route. At 60 with a lead of 10 the
     * choice becomes real: a completionist opens Zephyrion's throne (and the Unbound
     * path beside it), while a player who works one island's side quests and leaves
     * another's alone can put the god of their choice out in front.
     */
    public static final int CHAMPION_FAVOR = 60;

    /** How far clear of every other god that favor must stand. */
    public static final int CHAMPION_LEAD = 10;

    /** Floor every one of the seven must clear before the Unbound path opens. */
    public static final int UNBOUND_FLOOR = 55;

    /** Most any god may stand above the least-regarded and still count as balance. */
    public static final int UNBOUND_SPREAD = 35;

    /**
     * Island Shards needed to open the Cradle's eighth path.
     *
     * <p>The Story Bible calls for all seven, one per island. Five are grantable
     * today (Lirandel's corrupted shrine, Pyralis's Shard of Ambition, and the
     * Thalorax / Umbryn / Bellorak trials); Zephyrion's Shard of Freedom and
     * Sylvandar's Shard of Endless Growth exist in {@code items.json} but are
     * never handed out. Four of seven therefore means "you took the corrupting
     * bargain on most of the islands that offered you one" and still leaves the
     * player one refusal. Raise this toward 7 when the two missing grants land.
     */
    public static final int SERPENT_SHARDS = 4;

    /** Every island Shard of Corruption, by item id. */
    private static final String[] SHARD_IDS = {
        "shard_of_corruption",              // Lirandel  - the corrupted shrine
        "shard_of_ambition",                // Pyralis   - the faction war
        "shard_of_freedom",                 // Zephyrion - defined, not yet granted
        "shard_of_endless_growth",          // Sylvandar - defined, not yet granted
        "shard_of_corruption_bellorak",     // Bellorak  - Seraphine's power
        // Thalorax and Umbryn both grant the generic "shard_of_corruption" above.
    };

    // -- Palette -------------------------------------------------------------
    private static final Color CARD_TOP     = new Color(12, 6, 18);
    private static final Color CARD_BOT     = new Color(6, 4, 10);
    private static final Color BORDER       = new Color(80, 60, 120, 160);
    private static final Color TITLE_COL    = new Color(240, 235, 255);
    private static final Color SUBTITLE_COL = OverlayTheme.TEXT_DIM;
    private static final Color DESC_COL     = new Color(160, 155, 140);
    private static final Color OPEN_COL     = new Color(110, 205, 120);
    private static final Color SEALED_COL   = new Color(165, 110, 110);
    private static final Color SEL_BG       = new Color(255, 255, 255, 18);
    private static final Color DENY_BG      = new Color(200, 60, 60, 60);

    private static final Color SERPENT_ACC  = new Color(160, 80, 220);
    private static final Color UNBOUND_ACC  = new Color(220, 200, 140);
    private static final Color GENERIC_ACC  = new Color(140, 140, 150);

    private static final Font F_TITLE    = Fonts.monoBold(18);
    private static final Font F_SUB      = Fonts.mono    (12);
    private static final Font F_OPT_NAME = Fonts.monoBold(14);
    private static final Font F_OPT_DESC = Fonts.mono    (11);
    private static final Font F_TAG      = Fonts.mono    (10);

    // -- God accent colours (same as DivineAudienceOverlay) ------------------
    private static final Color[] GOD_COLORS = {
        new Color(180, 200, 255),   // Lirandel
        new Color(255, 120,  40),   // Pyralis
        new Color(140, 180, 255),   // Zephyrion
        new Color( 80, 220, 100),   // Sylvandar
        new Color( 60, 180, 200),   // Thalorax
        new Color(160, 170, 200),   // Umbryn
        new Color(200, 160,  60),   // Bellorak
    };

    // -- Choice data ---------------------------------------------------------
    /**
     * @param requirement the in-fiction reading of the gate: what it took when
     *                    the path is open, what is missing when it is sealed.
     */
    private record Choice(String name, String description, String requirement,
                          boolean open, Color accent, Runnable action) {}

    // -- State ---------------------------------------------------------------
    private final Retroquest game;
    private boolean active    = false;
    private long    entryTime = 0;
    private int     selected  = 0;
    private float   fadeOut   = -1f; // -1 = not fading, 0..1 = fade progress
    private long    fadeStart = 0;
    private long    denyFlash = 0;   // wall time of the last refused confirm
    private Runnable pendingAction;

    private final List<Choice> choices = new ArrayList<>();

    private static final long ENTRY_MS = 400;
    private static final long FADE_MS  = 500;
    private static final long DENY_MS  = 320;

    public CradleChoiceOverlay(Retroquest game) {
        this.game = game;
    }

    // -- Lifecycle -----------------------------------------------------------

    public void open() {
        buildChoices();
        if (choices.isEmpty()) return;
        selected  = firstOpenChoice();
        fadeOut   = -1f;
        denyFlash = 0;
        entryTime = System.currentTimeMillis();
        active    = true;
        SoundManager.getInstance().play("menublip");
    }

    public void close() {
        active = false;
    }

    public boolean isActive() { return active; }

    private int firstOpenChoice() {
        for (int i = 0; i < choices.size(); i++) if (choices.get(i).open()) return i;
        return 0;
    }

    // -- Endgame gate maths (public so other screens quote the same rules) ---

    /**
     * Counts every island Shard of Corruption the player carries, honouring
     * stack quantity. The Shards sharing the {@code shard_of_corruption} id take
     * one inventory slot each (max stack size 1), so slot-counting is correct
     * today, but quantity is respected in case that ever changes.
     */
    public static int countShards(Player p) {
        int count = 0;
        for (InventorySlot slot : p.getInventorySlots()) {
            if (slot == null || slot.getItem() == null) continue;
            String id = slot.getItem().getId();
            if (id == null || !isShard(id)) continue;
            count += Math.max(1, slot.getQuantity());
        }
        return count;
    }

    private static boolean isShard(String id) {
        for (String s : SHARD_IDS) if (s.equals(id)) return true;
        // Any future per-god variant of the corrupted shrine shard.
        return id.startsWith("shard_of_corruption");
    }

    /** The god with the highest favor; ties resolve to the lower index. */
    public static God leadingGod(int[] favors) {
        int best = 0;
        for (int i = 1; i < 7; i++) if (favors[i] > favors[best]) best = i;
        return God.byIndex(best);
    }

    /** Highest favor held by any god other than the one at {@code except}. */
    private static int bestRival(int[] favors, int except) {
        int best = -1;
        for (int i = 0; i < 7; i++) if (i != except && favors[i] > best) best = favors[i];
        return best;
    }

    /** True when {@code g} may claim the player as Champion. */
    public static boolean isChampion(int[] favors, God g) {
        return favors[g.index] >= CHAMPION_FAVOR
            && favors[g.index] - bestRival(favors, g.index) >= CHAMPION_LEAD;
    }

    /** True when all seven gods have a claim on the player and none owns them. */
    public static boolean isUnbound(int[] favors) {
        int lo = favors[0], hi = favors[0];
        for (int f : favors) { lo = Math.min(lo, f); hi = Math.max(hi, f); }
        return lo >= UNBOUND_FLOOR && (hi - lo) <= UNBOUND_SPREAD;
    }

    // -- Build the choice list -----------------------------------------------

    private void buildChoices() {
        choices.clear();
        Player p     = game.getPlayer();
        int[] favors = p.getFavorScores();
        int shards   = countShards(p);

        // -- The New Serpent -------------------------------------------------
        boolean serpentOpen = shards >= SERPENT_SHARDS;
        choices.add(new Choice(
            "BECOME THE NEW SERPENT",
            "Feed the Shards into your Scar and dream a world of your own making.",
            serpentOpen
                ? "Shards borne: " + shards + "/" + SERPENT_SHARDS
                    + ". The Cradle knows what you are, and the eighth path opens."
                : "Shards borne: " + shards + "/" + SERPENT_SHARDS
                    + ". You refused too many bargains. The hidden path stays shut.",
            serpentOpen,
            SERPENT_ACC,
            () -> game.getDungeonController().endingNewSerpent()));

        // -- Champion of a god -----------------------------------------------
        God lead          = leadingGod(favors);
        boolean champOpen = isChampion(favors, lead);
        int leadFavor     = favors[lead.index];
        int rival         = bestRival(favors, lead.index);
        God rivalGod      = null;
        for (God g : God.values()) {
            if (g != lead && favors[g.index] == rival) { rivalGod = g; break; }
        }

        String champReq;
        if (champOpen) {
            champReq = lead.displayName + " holds you at " + leadFavor + ", "
                     + (leadFavor - rival) + " clear of any rival. The throne is offered.";
        } else if (leadFavor < CHAMPION_FAVOR) {
            champReq = lead.displayName + " leads at " + leadFavor + ", short of the "
                     + CHAMPION_FAVOR + " a god wants before naming a Champion.";
        } else {
            champReq = lead.displayName + " " + leadFavor + ", "
                     + (rivalGod != null ? rivalGod.displayName : "another") + " " + rival
                     + ". No god shares a throne; a lead of " + CHAMPION_LEAD + " is wanted.";
        }

        final God cg = lead;
        choices.add(new Choice(
            "CHAMPION OF " + lead.displayName.toUpperCase(),
            "Feed every Key into " + lead.displayName
                + "'s Shard. One dream swallows the other six.",
            champReq,
            champOpen,
            GOD_COLORS[lead.index],
            () -> game.getDungeonController().endingChampion(cg)));

        // -- True Unbound ----------------------------------------------------
        int lo = favors[0], hi = favors[0], loIdx = 0, hiIdx = 0;
        for (int i = 1; i < 7; i++) {
            if (favors[i] < lo) { lo = favors[i]; loIdx = i; }
            if (favors[i] > hi) { hi = favors[i]; hiIdx = i; }
        }
        God loGod = God.byIndex(loIdx);
        God hiGod = God.byIndex(hiIdx);
        boolean unboundOpen = isUnbound(favors);

        String unboundReq;
        if (unboundOpen) {
            unboundReq = "All seven have a claim on you \u2014 least of them "
                       + loGod.displayName + " at " + lo + " \u2014 and none of them owns you.";
        } else if (lo < UNBOUND_FLOOR) {
            unboundReq = loGod.displayName + " stands at " + lo + ". All seven must reach "
                       + UNBOUND_FLOOR + " before they consent to be judged by you.";
        } else {
            unboundReq = hiGod.displayName + " " + hi + " against " + loGod.displayName + " " + lo
                       + ". You kept a favourite; the seven will not be judged by a partisan.";
        }

        choices.add(new Choice(
            "REFUSE ALL GODS",
            "Return the Keys to the dreamer. Seven gods become dreams again.",
            unboundReq,
            unboundOpen,
            UNBOUND_ACC,
            () -> game.getDungeonController().endingTrueUnbound()));

        // -- The Unfinished Dream (always open) ------------------------------
        choices.add(new Choice(
            "LET THE CRADLE DECIDE",
            "Place the Keys, take your hands away, and accept what the dreaming makes.",
            "Always open. The Cradle will finish whatever you would not.",
            true,
            GENERIC_ACC,
            () -> game.getDungeonController().endingGeneric()));
    }

    // -- Input ---------------------------------------------------------------

    public void handleKey(KeyEvent e) {
        if (!active || fadeOut >= 0) return;

        switch (e.getKeyCode()) {
            case KeyEvent.VK_UP, KeyEvent.VK_K -> {
                selected = (selected - 1 + choices.size()) % choices.size();
                SoundManager.getInstance().play("menublip");
            }
            case KeyEvent.VK_DOWN, KeyEvent.VK_J -> {
                selected = (selected + 1) % choices.size();
                SoundManager.getInstance().play("menublip");
            }
            case KeyEvent.VK_ENTER, KeyEvent.VK_SPACE -> {
                Choice ch = choices.get(selected);
                if (!ch.open()) {
                    // Sealed: refuse and flash. The requirement line on the row
                    // already says exactly what is missing.
                    denyFlash = System.currentTimeMillis();
                    SoundManager.getInstance().play("menublip");
                    return;
                }
                SoundManager.getInstance().play("powerup");
                pendingAction = ch.action();
                fadeOut   = 0f;
                fadeStart = System.currentTimeMillis();
            }
        }
    }

    public void handleClick(int mx, int my) {
        // Not implemented - keyboard-only for this overlay
    }

    // -- Rendering -----------------------------------------------------------

    public void paint(Graphics2D g, int W, int H) {
        if (!active) return;
        try { doPaint(g, W, H); }
        catch (Exception ignored) {}
    }

    private void doPaint(Graphics2D g, int W, int H) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                           RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                           RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        long now   = System.currentTimeMillis();
        float ease = OverlayTheme.entryEase(entryTime, ENTRY_MS);

        // Advance fade-out
        if (fadeOut >= 0) {
            fadeOut = Math.min(1f, (float)(now - fadeStart) / FADE_MS);
            if (fadeOut >= 1f) {
                active = false;
                if (pendingAction != null) pendingAction.run();
                return;
            }
        }

        float masterAlpha = ease * (fadeOut >= 0 ? (1f - fadeOut) : 1f);

        // -- Full-screen dim -------------------------------------------------
        g.setColor(new Color(0, 0, 0, clamp((int)(200 * masterAlpha))));
        g.fillRect(0, 0, W, H);

        // -- Metrics ---------------------------------------------------------
        FontMetrics fmT   = g.getFontMetrics(F_TITLE);
        FontMetrics fmS   = g.getFontMetrics(F_SUB);
        FontMetrics fmN   = g.getFontMetrics(F_OPT_NAME);
        FontMetrics fmD   = g.getFontMetrics(F_OPT_DESC);
        FontMetrics fmTag = g.getFontMetrics(F_TAG);

        int pad     = OverlayTheme.scaled(10);
        int rowH    = fmN.getHeight() + fmD.getHeight() + fmTag.getHeight()
                    + OverlayTheme.scaled(16);
        int headerH = fmT.getHeight() + fmS.getHeight() + OverlayTheme.scaled(26);
        int footerH = OverlayTheme.scaled(38);

        int cardW = Math.min(OverlayTheme.scaled(690), W - OverlayTheme.scaled(24));
        int cardH = Math.min(headerH + choices.size() * rowH + footerH,
                             H - OverlayTheme.scaled(20));
        int cx    = (W - cardW) / 2;
        int cy    = (int)((H - cardH) / 2 - OverlayTheme.scaled(15) * (1f - ease));

        // -- Card ------------------------------------------------------------
        g.setPaint(new GradientPaint(cx, cy, CARD_TOP, cx, cy + cardH, CARD_BOT));
        g.fillRoundRect(cx, cy, cardW, cardH, 12, 12);

        g.setColor(new Color(BORDER.getRed(), BORDER.getGreen(), BORDER.getBlue(),
                             clamp((int)(BORDER.getAlpha() * masterAlpha))));
        g.setStroke(new BasicStroke(1.5f));
        g.drawRoundRect(cx, cy, cardW, cardH, 12, 12);

        // -- Title -----------------------------------------------------------
        g.setFont(F_TITLE);
        String title = "THE HEART OF THE CRADLE";
        int tx = cx + (cardW - fmT.stringWidth(title)) / 2;
        int ty = cy + fmT.getAscent() + OverlayTheme.scaled(12);
        g.setColor(new Color(TITLE_COL.getRed(), TITLE_COL.getGreen(), TITLE_COL.getBlue(),
                             clamp((int)(60 * masterAlpha))));
        g.drawString(title, tx - 1, ty - 1);
        g.drawString(title, tx + 1, ty + 1);
        g.setColor(new Color(TITLE_COL.getRed(), TITLE_COL.getGreen(), TITLE_COL.getBlue(),
                             clamp((int)(255 * masterAlpha))));
        g.drawString(title, tx, ty);

        // Subtitle
        g.setFont(F_SUB);
        String sub = "Seven Keys are placed. For one heartbeat every future is open.";
        int sx = cx + (cardW - fmS.stringWidth(sub)) / 2;
        g.setColor(new Color(SUBTITLE_COL.getRed(), SUBTITLE_COL.getGreen(),
                             SUBTITLE_COL.getBlue(), clamp((int)(200 * masterAlpha))));
        g.drawString(sub, sx, ty + fmS.getHeight() + OverlayTheme.scaled(3));

        // Divider
        int divY = ty + fmS.getHeight() + OverlayTheme.scaled(12);
        g.setColor(new Color(80, 60, 120, clamp((int)(80 * masterAlpha))));
        g.setStroke(new BasicStroke(1f));
        g.drawLine(cx + OverlayTheme.scaled(20), divY,
                   cx + cardW - OverlayTheme.scaled(20), divY);

        // -- Choice rows -----------------------------------------------------
        boolean denying = (now - denyFlash) < DENY_MS;
        int rowStartY   = divY + OverlayTheme.scaled(10);

        for (int i = 0; i < choices.size(); i++) {
            Choice ch   = choices.get(i);
            int ry      = rowStartY + i * rowH;
            boolean sel = (i == selected);
            Color ac    = ch.open() ? ch.accent() : dim(ch.accent());

            if (sel) {
                Color bg = (denying && !ch.open()) ? DENY_BG : SEL_BG;
                g.setColor(new Color(bg.getRed(), bg.getGreen(), bg.getBlue(),
                                     clamp((int)(bg.getAlpha() * masterAlpha))));
                g.fillRoundRect(cx + pad, ry, cardW - pad * 2,
                                rowH - OverlayTheme.scaled(4), 6, 6);

                g.setColor(new Color(ac.getRed(), ac.getGreen(), ac.getBlue(),
                                     clamp((int)(180 * masterAlpha))));
                g.setStroke(new BasicStroke(1.5f));
                g.drawRoundRect(cx + pad, ry, cardW - pad * 2,
                                rowH - OverlayTheme.scaled(4), 6, 6);
            }

            int textX = cx + pad + OverlayTheme.scaled(14);
            int nameY = ry + fmN.getAscent() + OverlayTheme.scaled(6);
            int descY = nameY + fmD.getHeight() + OverlayTheme.scaled(2);
            int reqY  = descY + fmTag.getHeight() + OverlayTheme.scaled(1);

            // Name
            g.setFont(F_OPT_NAME);
            int nameAlpha = sel ? 255 : (ch.open() ? 170 : 120);
            g.setColor(new Color(ac.getRed(), ac.getGreen(), ac.getBlue(),
                                 clamp((int)(nameAlpha * masterAlpha))));
            g.drawString(ch.name(), textX, nameY);

            // Status tag, right-aligned on the name line
            g.setFont(F_TAG);
            String tag   = ch.open() ? "OPEN" : "SEALED";
            Color tagCol = ch.open() ? OPEN_COL : SEALED_COL;
            int tagW     = fmTag.stringWidth(tag);
            g.setColor(new Color(tagCol.getRed(), tagCol.getGreen(), tagCol.getBlue(),
                                 clamp((int)((ch.open() ? 205 : 175) * masterAlpha))));
            g.drawString(tag, cx + cardW - pad - OverlayTheme.scaled(14) - tagW, nameY);

            // Description
            g.setFont(F_OPT_DESC);
            int descAlpha = ch.open() ? 200 : 130;
            g.setColor(new Color(DESC_COL.getRed(), DESC_COL.getGreen(), DESC_COL.getBlue(),
                                 clamp((int)(descAlpha * masterAlpha))));
            g.drawString(ch.description(), textX, descY);

            // Requirement / reason it is sealed
            g.setFont(F_TAG);
            Color reqCol = ch.open() ? OPEN_COL : SEALED_COL;
            g.setColor(new Color(reqCol.getRed(), reqCol.getGreen(), reqCol.getBlue(),
                                 clamp((int)((sel ? 230 : 170) * masterAlpha))));
            g.drawString(ch.requirement(), textX, reqY);
        }

        // -- Key hints -------------------------------------------------------
        Choice selCh = choices.get(selected);
        Color barAcc = selCh.open() ? selCh.accent() : dim(selCh.accent());
        int barY     = cy + cardH - OverlayTheme.scaled(28);
        OverlayTheme.paintKeyBadges(g, cx, barY, cardW, OverlayTheme.scaled(26),
            selCh.open()
                ? new String[][] { { "\u2191\u2193", "Consider" }, { "Enter", "Commit" } }
                : new String[][] { { "\u2191\u2193", "Consider" }, { "Enter", "Sealed" } },
            new Color(barAcc.getRed(), barAcc.getGreen(), barAcc.getBlue(),
                      clamp((int)(200 * masterAlpha))));

        // -- Fade-out overlay ------------------------------------------------
        if (fadeOut > 0) {
            g.setColor(new Color(0, 0, 0, clamp((int)(fadeOut * 255))));
            g.fillRect(0, 0, W, H);
        }
    }

    private static Color dim(Color c) {
        return new Color(c.getRed() / 2 + 30, c.getGreen() / 2 + 30, c.getBlue() / 2 + 30);
    }

    private static int clamp(int v) { return Math.max(0, Math.min(255, v)); }
}

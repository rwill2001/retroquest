package io.cannonforge.retroquest.overlay;

import io.cannonforge.retroquest.model.Player;
import io.cannonforge.retroquest.model.Player.LevelUpResult;
import io.cannonforge.retroquest.model.Quest;
import io.cannonforge.retroquest.registry.ItemRegistry;
import io.cannonforge.retroquest.model.Item;

import java.awt.*;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Non-blocking toast notifications that slide in from the top of the screen.
 *
 * <p>Two weights: loud toasts (quest completion, level-up) hold on screen, and
 * quiet ones — a buff running out, an autosave landing — flick past in about a
 * second so state never changes behind the player's back without a word.
 */
public class NotificationOverlay {

    private record Toast(String title, String[] lines, Color accent, boolean quiet) {}

    private final Deque<Toast> queue = new ArrayDeque<>();
    private Toast current;
    private long  currentShowTime;

    private static final int CARD_W       = OverlayTheme.scaled(280);
    private static final int CARD_Y_REST  = OverlayTheme.scaled(12);
    private static final int SLIDE_IN_MS  = 350;
    private static final int HOLD_MS      = 3200;
    private static final int QUIET_HOLD_MS= 1100;
    private static final int FADE_MS      = 800;
    private static final int QUIET_FADE_MS= 450;

    private int holdMs() { return current != null && current.quiet() ? QUIET_HOLD_MS : HOLD_MS; }
    private int fadeMs() { return current != null && current.quiet() ? QUIET_FADE_MS : FADE_MS; }
    private int totalMs() { return SLIDE_IN_MS + holdMs() + fadeMs(); }

    private static final int PAD_TOP    = OverlayTheme.scaled(10);
    private static final int PAD_BOTTOM = OverlayTheme.scaled(10);
    private static final int PAD_LEFT   = OverlayTheme.scaled(16);  // after accent bar
    private static final int ACCENT_W   = OverlayTheme.scaled(3);

    // ── Public API ──────────────────────────────────────────────────────────────

    public void enqueueQuestComplete(Quest q) {
        List<String> lines = new ArrayList<>();
        lines.add("\u2714 " + q.getTitle());
        StringBuilder rewards = new StringBuilder();
        rewards.append("+").append(q.getXpReward()).append(" XP");
        if (q.getGoldReward() > 0) rewards.append(", +").append(q.getGoldReward()).append(" Gold");
        lines.add(rewards.toString());
        // item reward
        String itemId = q.getItemRewardId();
        if (itemId != null && !itemId.isEmpty()) {
            Item item = ItemRegistry.getById(itemId);
            if (item != null) lines.add("+ " + item.getName());
        }
        queue.add(new Toast("Quest Complete!", lines.toArray(String[]::new), OverlayTheme.GOOD, false));
    }

    public void enqueueLevelUp(LevelUpResult lvl) {
        List<String> lines = new ArrayList<>();
        lines.add("You are now level " + lvl.newLevel());
        lines.add("HP +" + lvl.totalHpGained());
        if (lvl.unlockedNewSpellTier()) {
            lines.add("New spell tier: Lv" + lvl.newMaxSpellLevel() + "!");
        }
        queue.add(new Toast("\u2605 Level Up!", lines.toArray(String[]::new), OverlayTheme.AMBER, false));
    }

    // \u2500\u2500 Quiet toasts \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500

    /** "Autosaved" \u2014 one line, gone in a second, but never silent. */
    public void enqueueAutoSave() {
        queue.add(new Toast("Autosaved", new String[] {"Progress written to the auto slot."},
                            OverlayTheme.CYAN_ACC, true));
    }

    /** "Haste has run out." */
    public void enqueueBuffExpired(String buffName) {
        queue.add(new Toast("Spell ended", new String[] {buffName + " has run out."},
                            OverlayTheme.TEXT_DIM, true));
    }

    // \u2500\u2500 Buff-expiry watch \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500
    //
    // Buff counters tick down inside Player.move(); nothing tells the UI when one
    // reaches zero. Rather than reach into the model, this keeps a snapshot of which
    // buffs were up last time it was asked and reports the ones that have since
    // lapsed. Call it once per player step (see pollBuffExpiry).

    /** Buff display name \u2192 "is it still up?", in the order they should be reported. */
    private static final Map<String, Predicate<Player>> BUFFS = new LinkedHashMap<>();
    static {
        BUFFS.put("Shield",           Player::hasShield);
        BUFFS.put("Haste",            Player::isHasted);
        BUFFS.put("Prayer",           Player::hasPrayer);
        BUFFS.put("Holy Armor",       Player::hasHolyArmor);
        BUFFS.put("Resist Elements",  Player::hasElemResist);
        BUFFS.put("Invisibility",     Player::isInvisible);
        BUFFS.put("Poison",           p -> !p.isPoisoned());   // reported when it clears
    }

    private final Map<String, Boolean> buffWasUp = new LinkedHashMap<>();
    private Player watchedPlayer;

    /**
     * Compares the player's buffs against the previous call and queues a quiet toast
     * for each one that has lapsed since. Safe to call every step; it allocates
     * nothing on the common path and does nothing on the very first call for a
     * player (which only seeds the snapshot).
     */
    public void pollBuffExpiry(Player p) {
        if (p == null) return;
        boolean seeding = (watchedPlayer != p);
        if (seeding) { watchedPlayer = p; buffWasUp.clear(); }

        for (Map.Entry<String, Predicate<Player>> e : BUFFS.entrySet()) {
            boolean up   = e.getValue().test(p);
            Boolean prev = buffWasUp.put(e.getKey(), up);
            if (!seeding && Boolean.TRUE.equals(prev) && !up) {
                // "Poison" is inverted above: its predicate is true while clean, so a
                // true\u2192false edge there would be the player being poisoned, not cured.
                if (!"Poison".equals(e.getKey())) enqueueBuffExpired(e.getKey());
            } else if (!seeding && Boolean.FALSE.equals(prev) && up && "Poison".equals(e.getKey())) {
                queue.add(new Toast("Cured", new String[] {"The poison has run its course."},
                                    OverlayTheme.GOOD, true));
            }
        }
    }

    /**
     * True while a toast is on screen or waiting. Expiry is decided by the clock,
     * not by {@link #paint}, so a toast that never gets painted still retires and
     * releases the overlay repaint timer.
     */
    public boolean isActive() {
        expireCurrent();
        return current != null || !queue.isEmpty();
    }

    // ── Rendering ───────────────────────────────────────────────────────────────

    public void paint(Graphics2D g, int W, int H) {
        expireCurrent();
        if (current == null) return;

        long elapsed = System.currentTimeMillis() - currentShowTime;

        // Calculate card height dynamically
        FontMetrics fmT = g.getFontMetrics(OverlayTheme.F_LABEL);
        FontMetrics fmL = g.getFontMetrics(OverlayTheme.F_DESC);
        int titleH   = fmT.getHeight();
        int lineH    = fmL.getHeight() + OverlayTheme.scaled(3);
        int lineCount = current.lines.length;
        int cardH = PAD_TOP + titleH + (lineCount * lineH) + PAD_BOTTOM;

        // Y position (slide-in from top)
        float y;
        float alpha;
        if (elapsed < SLIDE_IN_MS) {
            // Slide in
            float ease = OverlayTheme.entryEase(currentShowTime, SLIDE_IN_MS);
            y = -cardH + (CARD_Y_REST + cardH) * ease;
            alpha = 1f;
        } else if (elapsed < SLIDE_IN_MS + holdMs()) {
            // Hold
            y = CARD_Y_REST;
            alpha = 1f;
        } else {
            // Fade out
            y = CARD_Y_REST;
            float fadeProgress = (float)(elapsed - SLIDE_IN_MS - holdMs()) / fadeMs();
            alpha = 1f - Math.min(1f, fadeProgress);
        }

        int cardX = (W - CARD_W) / 2;
        int cardY = Math.round(y);

        Composite oldComposite = g.getComposite();
        g.setComposite(AlphaComposite.SrcOver.derive(alpha));

        // Background
        g.setColor(new Color(10, 14, 22, 230));
        g.fillRoundRect(cardX, cardY, CARD_W, cardH, 6, 6);

        // Border
        g.setColor(new Color(current.accent.getRed(), current.accent.getGreen(),
                             current.accent.getBlue(), 100));
        g.setStroke(new BasicStroke(1));
        g.drawRoundRect(cardX, cardY, CARD_W, cardH, 6, 6);

        // Left accent bar
        g.setColor(current.accent);
        g.fillRect(cardX + 2, cardY + 3, ACCENT_W, cardH - 6);

        // Title
        int tx = cardX + ACCENT_W + PAD_LEFT;
        int ty = cardY + PAD_TOP + fmT.getAscent();
        g.setFont(OverlayTheme.F_LABEL);
        g.setColor(current.accent);
        g.drawString(current.title, tx, ty);

        // Detail lines
        g.setFont(OverlayTheme.F_DESC);
        int ly = cardY + PAD_TOP + titleH + fmL.getAscent();
        for (int i = 0; i < current.lines.length; i++) {
            g.setColor(i == 0 ? OverlayTheme.TEXT_BRIGHT : OverlayTheme.TEXT_DIM);
            g.drawString(current.lines[i], tx, ly + i * lineH);
        }

        g.setComposite(oldComposite);
    }

    // ── Internal ────────────────────────────────────────────────────────────────

    private void advance() {
        if (current == null && !queue.isEmpty()) {
            current = queue.poll();
            currentShowTime = System.currentTimeMillis();
        }
    }

    /** Retires the on-screen toast once its lifetime is up, then pulls the next one. */
    private void expireCurrent() {
        if (current != null && System.currentTimeMillis() - currentShowTime > totalMs()) current = null;
        advance();
    }
}

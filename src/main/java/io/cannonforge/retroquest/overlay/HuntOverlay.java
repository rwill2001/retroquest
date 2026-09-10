package io.cannonforge.retroquest.overlay;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.util.List;

import io.cannonforge.retroquest.core.MessageLog;
import io.cannonforge.retroquest.core.Retroquest;
import io.cannonforge.retroquest.core.SoundManager;

/**
 * Hosts one island's hunt, opened from a hunting ground on the overworld.
 *
 * <p>Three beats: a briefing card, the hunt itself, and a result that pays food into the player.
 * The host owns the frame, the title, the controls line and the payout; the {@link HuntGame} owns
 * everything inside the play area. Nothing here knows which island it is on beyond the accent
 * colour the game hands back.
 *
 * <p>Food, not gold, on purpose. {@code Player.stepTaken()} burns one food per step and the only
 * other supply is a town, so a 230x190 island cannot be crossed and ground on one belly. Paying
 * these in food is what turns the overworld from a resupply loop into somewhere you can stay.
 */
public class HuntOverlay {

    private enum Stage { BRIEF, PLAY, RESULT }

    private static final long ENTRY_MS = 260;

    private final Retroquest game;

    private HuntGame  hunt;
    private Stage     stage = Stage.RESULT;
    private boolean   active;
    private long      entryTime;
    private int       awarded;
    private String    groundName = "";
    private Runnable  onWorked;

    public HuntOverlay(Retroquest game) {
        this.game = game;
    }

    /**
     * Opens the hunt named by a hunting ground's {@code game} parameter.
     *
     * @param gameId  one of the seven hunt ids; an unknown id is refused
     * @param ground  the hunting ground's display name, shown on the briefing
     * @param onWorked run when the hunt has actually been played out. NOT run if the player
     *                 reads the briefing and walks away — a ground you looked at is not a
     *                 ground you worked.
     * @return false if {@code gameId} names no hunt, in which case nothing was opened
     */
    public boolean open(String gameId, String ground, Runnable onWorked) {
        HuntGame h = forId(gameId);
        if (h == null) return false;

        this.hunt       = h;
        this.groundName = (ground != null && !ground.isBlank()) ? ground : h.name();
        this.onWorked   = onWorked;
        this.awarded    = 0;
        this.stage      = Stage.BRIEF;
        this.entryTime  = System.currentTimeMillis();
        this.active     = true;
        hunt.reset(game.getPlayer() != null ? game.getPlayer().getLevel() : 1);
        SoundManager.getInstance().play("blip");
        return true;
    }

    /** The seven hunts, one per island. */
    private static HuntGame forId(String id) {
        if (id == null) return null;
        return switch (id.trim().toLowerCase()) {
            case "snare_line"      -> new SnareLineHunt();
            case "ember_flush"     -> new EmberFlushHunt();
            case "skyfish_net"     -> new SkyfishNetHunt();
            case "spore_lure"      -> new SporeLureHunt();
            case "pressure_line"   -> new PressureLineHunt();
            case "remembered_meal" -> new RememberedMealHunt();
            case "ration_run"      -> new RationRunHunt();
            default -> null;
        };
    }

    /** True if the given id names a hunt this overlay can run. Used by the content checks. */
    public static boolean isKnownHunt(String id) { return forId(id) != null; }

    public boolean isActive() { return active; }

    /** Leaves the hunt. The ground is only marked worked by {@link #finish()}, not by this. */
    public void close() {
        active = false;
        hunt   = null;
    }

    // ── Input ───────────────────────────────────────────────────────────────

    public void handleKey(KeyEvent e) {
        if (!active) return;
        int code = e.getKeyCode();

        switch (stage) {
            case BRIEF -> {
                if (code == KeyEvent.VK_ESCAPE) { close(); return; }
                if (code == KeyEvent.VK_ENTER || code == KeyEvent.VK_SPACE) {
                    stage = Stage.PLAY;
                    // reset() again so the hunt's clock starts now, not when the card came up
                    hunt.reset(game.getPlayer() != null ? game.getPlayer().getLevel() : 1);
                }
            }
            case PLAY -> {
                // No escape mid-hunt: walking out of a half-sprung snare to retry is not a hunt.
                hunt.handleKey(e);
            }
            case RESULT -> {
                if (code == KeyEvent.VK_ENTER || code == KeyEvent.VK_SPACE || code == KeyEvent.VK_ESCAPE) close();
            }
        }
    }

    public void handleClick(int mx, int my) {
        if (!active) return;
        if (stage == Stage.BRIEF)       stage = Stage.PLAY;
        else if (stage == Stage.RESULT) close();
    }

    /** Advances the hunt. Driven by the overlay repaint timer. */
    public void update() {
        if (!active || stage != Stage.PLAY || hunt == null) return;
        hunt.update();
        if (hunt.isFinished()) finish();
    }

    private void finish() {
        stage   = Stage.RESULT;
        // Worked, whatever the haul was — a hunt that went badly still used the ground up.
        Runnable cb = onWorked;
        onWorked = null;
        if (cb != null) cb.run();
        awarded = Math.max(0, hunt.foodYield());
        if (awarded > 0 && game.getPlayer() != null) {
            game.getPlayer().addFood(awarded);
            game.log("You come away with " + awarded + " food.", MessageLog.Type.GOOD);
            SoundManager.getInstance().play("pickup");
        } else {
            game.log("Nothing to carry back.", MessageLog.Type.DIM);
        }
        if (game.getStatsPanel() != null) game.getStatsPanel().refresh();
    }

    // ── Rendering ───────────────────────────────────────────────────────────

    public void paint(Graphics2D g, int W, int H) {
        try { doPaint(g, W, H); }
        catch (Exception ignored) { /* never crash the render loop */ }
    }

    private void doPaint(Graphics2D g, int W, int H) {
        if (!active || hunt == null) return;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        float ease = OverlayTheme.entryEase(entryTime, ENTRY_MS);
        Color accent = hunt.accent();

        g.setColor(new Color(0, 0, 0, (int)(210 * ease)));
        g.fillRect(0, 0, W, H);

        int cardW = Math.min(OverlayTheme.scaled(560), W - OverlayTheme.scaled(40));
        int cardH = Math.min(OverlayTheme.scaled(400), H - OverlayTheme.scaled(40));
        int cx = (W - cardW) / 2;
        int cy = (int)((H - cardH) / 2 - OverlayTheme.scaled(16) * (1f - ease));

        Color tint = new Color(accent.getRed() / 12, accent.getGreen() / 12, accent.getBlue() / 12);
        g.setPaint(new GradientPaint(cx, cy, tint, cx, cy + cardH, OverlayTheme.BG));
        g.fillRoundRect(cx, cy, cardW, cardH, 12, 12);
        g.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 170));
        g.setStroke(new BasicStroke(1.5f));
        g.drawRoundRect(cx, cy, cardW, cardH, 12, 12);

        // ── Header ───────────────────────────────────────────────────────────
        g.setFont(OverlayTheme.F_TITLE);
        FontMetrics fmT = g.getFontMetrics();
        String title = hunt.name().toUpperCase();
        int ty = cy + fmT.getAscent() + OverlayTheme.scaled(9);
        g.setColor(accent);
        g.drawString(title, cx + (cardW - fmT.stringWidth(title)) / 2, ty);

        g.setFont(OverlayTheme.F_SMALL);
        FontMetrics fmS = g.getFontMetrics();
        g.setColor(OverlayTheme.TEXT_DIM);
        String sub = groundName + "   ·   FOOD " + (game.getPlayer() != null ? game.getPlayer().getFood() : 0);
        g.drawString(sub, cx + (cardW - fmS.stringWidth(sub)) / 2, ty + fmS.getHeight() + 1);

        int divY = ty + fmS.getHeight() + OverlayTheme.scaled(9);
        g.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 80));
        g.setStroke(new BasicStroke(1f));
        g.drawLine(cx + OverlayTheme.scaled(16), divY, cx + cardW - OverlayTheme.scaled(16), divY);

        int barH  = OverlayTheme.scaled(26);
        int barY  = cy + cardH - OverlayTheme.scaled(30);
        int areaX = cx + OverlayTheme.scaled(16);
        int areaY = divY + OverlayTheme.scaled(10);
        int areaW = cardW - OverlayTheme.scaled(32);
        int areaH = barY - areaY - OverlayTheme.scaled(24);

        switch (stage) {
            case BRIEF  -> paintBrief(g, areaX, areaY, areaW, areaH, accent);
            case PLAY   -> paintPlay(g, areaX, areaY, areaW, areaH, accent);
            case RESULT -> paintResult(g, areaX, areaY, areaW, areaH, accent);
        }

        // ── Footer ───────────────────────────────────────────────────────────
        String[][] keys = switch (stage) {
            case BRIEF  -> new String[][] { { "Enter", "Begin" }, { "Esc", "Walk on" } };
            case PLAY   -> new String[][] { };
            case RESULT -> new String[][] { { "Enter", "Leave" } };
        };
        if (keys.length > 0) {
            OverlayTheme.paintKeyBadges(g, cx, barY, cardW, barH, keys, accent);
        } else {
            g.setFont(OverlayTheme.F_SMALL);
            g.setColor(OverlayTheme.TEXT_DIM);
            String c = hunt.controls();
            g.drawString(c, cx + (cardW - g.getFontMetrics().stringWidth(c)) / 2,
                         barY + barH / 2 + OverlayTheme.scaled(4));
        }
    }

    private void paintBrief(Graphics2D g, int x, int y, int w, int h, Color accent) {
        g.setFont(OverlayTheme.F_ITEM);
        FontMetrics fm = g.getFontMetrics();
        List<String> lines = OverlayTheme.wordWrap(fm, hunt.briefing(), w - OverlayTheme.scaled(16));
        int ly = y + h / 2 - (lines.size() * fm.getHeight()) / 2;
        g.setColor(OverlayTheme.TEXT_BRIGHT);
        for (int i = 0; i < lines.size(); i++) {
            String s = lines.get(i);
            g.drawString(s, x + (w - fm.stringWidth(s)) / 2, ly + i * fm.getHeight());
        }
        g.setFont(OverlayTheme.F_SMALL);
        g.setColor(accent);
        String c = hunt.controls();
        g.drawString(c, x + (w - g.getFontMetrics().stringWidth(c)) / 2,
                     ly + lines.size() * fm.getHeight() + OverlayTheme.scaled(16));
    }

    private void paintPlay(Graphics2D g, int x, int y, int w, int h, Color accent) {
        java.awt.Shape clip = g.getClip();
        g.setClip(x, y, w, h);
        hunt.paint(g, x, y, w, h);
        g.setClip(clip);
        g.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 110));
        g.drawRect(x, y, w, h);
    }

    private void paintResult(Graphics2D g, int x, int y, int w, int h, Color accent) {
        // The number first, big, then what happened
        g.setFont(io.cannonforge.retroquest.core.Fonts.monoBold(26));
        FontMetrics fmB = g.getFontMetrics();
        String num = (awarded > 0 ? "+" : "") + awarded + " FOOD";
        g.setColor(awarded > 0 ? OverlayTheme.GOOD : OverlayTheme.TEXT_DIM);
        g.drawString(num, x + (w - fmB.stringWidth(num)) / 2, y + h / 2 - OverlayTheme.scaled(6));

        g.setFont(OverlayTheme.F_ITEM);
        FontMetrics fm = g.getFontMetrics();
        List<String> lines = OverlayTheme.wordWrap(fm, hunt.resultText(), w - OverlayTheme.scaled(24));
        int ly = y + h / 2 + OverlayTheme.scaled(18);
        g.setColor(OverlayTheme.TEXT_BRIGHT);
        for (int i = 0; i < lines.size(); i++) {
            String s = lines.get(i);
            g.drawString(s, x + (w - fm.stringWidth(s)) / 2, ly + i * fm.getHeight());
        }

        if (game.getPlayer() != null) {
            g.setFont(OverlayTheme.F_SMALL);
            g.setColor(OverlayTheme.TEXT_DIM);
            String carry = "CARRYING " + game.getPlayer().getFood();
            g.drawString(carry, x + (w - g.getFontMetrics().stringWidth(carry)) / 2,
                         ly + lines.size() * fm.getHeight() + OverlayTheme.scaled(14));
        }
    }
}

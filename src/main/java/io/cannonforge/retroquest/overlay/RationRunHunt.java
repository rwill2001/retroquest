package io.cannonforge.retroquest.overlay;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.util.Random;

/**
 * Island 7 · Bellorak — <b>Ration Run</b>.
 *
 * <p>You do not hunt a battlefield, you rob it. A supply cache sits at the far end of a trench
 * and a watch-lamp sweeps the ground between. Move while the light is off you; freeze when it
 * comes round. Get caught and you are driven back down the trench.
 *
 * <p>The verb is <em>moving only while nobody is looking</em> — the only hunt where the correct
 * action is frequently to do nothing, and the only one you can lose ground in.
 */
class RationRunHunt implements HuntGame {

    private static final Color ACCENT = new Color(200, 160, 60);
    private static final Color EARTH  = new Color(38, 30, 22);
    private static final Color TRENCH = new Color(20, 16, 12);
    private static final Color LAMP   = new Color(255, 236, 170);

    private static final int  MAX_FOOD = 140;
    private static final long DURATION = 22_000;
    private static final double ADVANCE = 0.20;   // fraction of the run per second while moving

    private final Random rng = new Random();

    private double pos;            // 0 = trench mouth, 1 = the cache
    private double lampX;          // 0..1 sweep position
    private double lampDir = 1;
    private double lampSpeed;
    private double lampHalf;       // half-width of the cone at ground level
    private int    loads;          // caches broken open
    private int    spotted;
    private boolean moving;
    private long   start, lastTick, caughtUntil;
    private boolean finished;

    @Override public String name()     { return "Ration Run"; }
    @Override public String briefing() {
        return "There is nothing alive out here to eat, but both armies have to be fed. A cache "
             + "sits at the end of the trench and a watch-lamp sweeps the ground. Move in the "
             + "dark, freeze in the light.";
    }
    @Override public String controls() { return "[Space] toggle: advance / freeze"; }
    @Override public Color  accent()   { return ACCENT; }

    @Override
    public void reset(int level) {
        pos = 0; loads = 0; spotted = 0; finished = false;
        lampX = 0.5; lampDir = rng.nextBoolean() ? 1 : -1;
        lampSpeed = 0.34; lampHalf = 0.15;
        moving = false; caughtUntil = 0;
        start = lastTick = System.currentTimeMillis();
    }

    // Space TOGGLES movement rather than being held — same reason as the Pressure Line: a
    // faked hold built on key-repeat stalls for the OS repeat delay, and stalling in the open
    // is exactly what this hunt punishes.
    @Override
    public void handleKey(KeyEvent e) {
        if (finished) return;
        if (e.getKeyCode() == KeyEvent.VK_SPACE) moving = !moving;
    }

    @Override
    public void update() {
        if (finished) return;
        long now = System.currentTimeMillis();
        double dt = Math.min(0.1, (now - lastTick) / 1000.0);
        lastTick = now;

        // The lamp sweeps back and forth, faster and tighter the closer you get to the cache
        lampSpeed = 0.30 + 0.26 * pos;
        lampHalf  = 0.155 - 0.045 * pos;
        lampX += lampDir * lampSpeed * dt;
        if (lampX <= 0.06) { lampX = 0.06; lampDir = 1; }
        if (lampX >= 0.94) { lampX = 0.94; lampDir = -1; }

        boolean recovering = now < caughtUntil;
        boolean lit = Math.abs(lampX - pos) < lampHalf;

        if (!recovering && moving) {
            if (lit) {
                // Caught in the open — driven back
                spotted++;
                pos = Math.max(0, pos - 0.22);
                caughtUntil = now + 900;
                moving = false;
                io.cannonforge.retroquest.core.SoundManager.getInstance().play("miss");
            } else {
                pos += ADVANCE * dt;
            }
        }

        if (pos >= 1) {
            loads++;
            pos = 0;            // haul it back and go again while the light lasts
            io.cannonforge.retroquest.core.SoundManager.getInstance().play("hit");
        }

        if (now - start > DURATION) finished = true;
    }

    @Override public boolean isFinished() { return finished; }

    @Override
    public int foodYield() {
        // Full loads, plus credit for a run left unfinished when the clock ran out
        int base = (int)Math.round(MAX_FOOD * (loads + pos * 0.5));
        return Math.max(0, base - spotted * 8);
    }

    @Override
    public String resultText() {
        if (loads == 0 && pos < 0.3) return "You spent the whole watch in the mud, three yards from where you started.";
        if (loads == 0)               return "You got most of the way and the lamp came back. Nothing to show but bruises.";
        if (loads >= 2)               return loads + " caches broken open. Both armies are going to blame each other.";
        return "One cache carried off clean" + (spotted > 0 ? ", though the lamp had you " + spotted + " time" + (spotted == 1 ? "" : "s") + "." : ".");
    }

    @Override
    public void paint(Graphics2D g, int px, int py, int pw, int ph) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        long now = System.currentTimeMillis();
        boolean recovering = now < caughtUntil;

        // Night ground
        g.setColor(EARTH);
        g.fillRect(px, py, pw, ph);
        g.setColor(new Color(28, 22, 16));
        for (int i = 0; i < 40; i++) {
            int sx = px + (int)((Math.sin(i * 5.7) * 0.5 + 0.5) * pw);
            int sy = py + (int)((Math.cos(i * 3.1) * 0.5 + 0.5) * ph);
            g.fillRect(sx, sy, 2, 1);
        }

        int groundY = py + ph * 2 / 3;

        // The trench the run happens in
        g.setColor(TRENCH);
        g.fillRect(px, groundY - OverlayTheme.scaled(16), pw, OverlayTheme.scaled(34));
        g.setColor(new Color(54, 44, 30));
        g.fillRect(px, groundY - OverlayTheme.scaled(13), pw, 2);
        g.fillRect(px, groundY + OverlayTheme.scaled(13), pw, 2);
        // Duckboards
        g.setColor(new Color(70, 56, 34));
        for (int x = 0; x < pw; x += OverlayTheme.scaled(9))
            g.fillRect(px + x, groundY + OverlayTheme.scaled(6), OverlayTheme.scaled(6), 2);

        // ── The watch-lamp and its cone ──────────────────────────────────────
        int lampPx = px + (int)(lampX * pw);
        int lampPy = py + OverlayTheme.scaled(8);
        int coneHalf = (int)(lampHalf * pw);
        Polygon cone = new Polygon();
        cone.addPoint(lampPx, lampPy);
        cone.addPoint(lampPx - coneHalf, groundY + OverlayTheme.scaled(14));
        cone.addPoint(lampPx + coneHalf, groundY + OverlayTheme.scaled(14));
        g.setColor(new Color(255, 236, 170, 46));
        g.fillPolygon(cone);
        g.setColor(new Color(255, 236, 170, 90));
        g.drawPolygon(cone);
        g.setColor(LAMP);
        g.fillOval(lampPx - OverlayTheme.scaled(4), lampPy - OverlayTheme.scaled(4),
                   OverlayTheme.scaled(8), OverlayTheme.scaled(8));

        // ── The cache at the far end ─────────────────────────────────────────
        int cacheX = px + pw - OverlayTheme.scaled(20);
        g.setColor(new Color(96, 72, 40));
        g.fillRect(cacheX - OverlayTheme.scaled(8), groundY - OverlayTheme.scaled(2),
                   OverlayTheme.scaled(16), OverlayTheme.scaled(12));
        g.setColor(new Color(64, 48, 26));
        g.drawRect(cacheX - OverlayTheme.scaled(8), groundY - OverlayTheme.scaled(2),
                   OverlayTheme.scaled(16), OverlayTheme.scaled(12));
        g.setColor(new Color(70, 70, 76));
        g.fillRect(cacheX - OverlayTheme.scaled(8), groundY + OverlayTheme.scaled(3), OverlayTheme.scaled(16), 2);

        // ── The runner ───────────────────────────────────────────────────────
        int rx = px + OverlayTheme.scaled(6) + (int)(pos * (pw - OverlayTheme.scaled(30)));
        boolean lit = Math.abs(lampX - pos) < lampHalf;
        int bob = (moving && !recovering) ? (int)(Math.sin(now / 70.0) * 2) : 0;
        g.setColor(recovering ? OverlayTheme.DANGER : lit ? new Color(240, 220, 160) : new Color(120, 110, 96));
        int rw = OverlayTheme.scaled(5);
        g.fillOval(rx - rw, groundY - OverlayTheme.scaled(9) + bob, rw * 2, rw * 2);   // head
        g.fillRect(rx - rw, groundY + bob, rw * 2, OverlayTheme.scaled(11));           // body
        g.fillRect(rx - rw - 3, groundY + OverlayTheme.scaled(3) + bob, 3, OverlayTheme.scaled(7));
        g.fillRect(rx + rw,     groundY + OverlayTheme.scaled(3) + bob, 3, OverlayTheme.scaled(7));

        if (recovering) {
            g.setFont(OverlayTheme.F_SMALL);
            g.setColor(OverlayTheme.DANGER);
            String s = "SPOTTED";
            g.drawString(s, rx - g.getFontMetrics().stringWidth(s) / 2, groundY - OverlayTheme.scaled(14));
        }

        // ── HUD ──────────────────────────────────────────────────────────────
        int barW = pw - OverlayTheme.scaled(16), barH = OverlayTheme.scaled(4);
        int by = py + ph - OverlayTheme.scaled(10);
        g.setColor(new Color(255, 255, 255, 28));
        g.fillRect(px + OverlayTheme.scaled(8), by, barW, barH);
        long left = Math.max(0, DURATION - (now - start));
        g.setColor(left < 5000 ? OverlayTheme.DANGER : ACCENT);
        g.fillRect(px + OverlayTheme.scaled(8), by, (int)(barW * left / (double) DURATION), barH);

        g.setFont(OverlayTheme.F_SMALL);
        g.setColor(OverlayTheme.TEXT_DIM);
        String hud = "LOADS " + loads + (spotted > 0 ? "   SEEN " + spotted : "");
        g.drawString(hud, px + pw - g.getFontMetrics().stringWidth(hud) - OverlayTheme.scaled(8),
                     by - OverlayTheme.scaled(3));
    }
}

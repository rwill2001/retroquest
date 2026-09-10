package io.cannonforge.retroquest.overlay;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Island 3 · Zephyrion — <b>Skyfish Netting</b>.
 *
 * <p>A shoal drifts across the thermals above you. The net takes time to climb, so aiming at a
 * fish is aiming at where it used to be — you have to throw where it is going.
 *
 * <p>The verb is <em>leading a moving target</em>. Nothing else in the game asks for that, and it
 * is the one hunt where standing still and waiting for a good shot is the correct play.
 */
class SkyfishNetHunt implements HuntGame {

    private static final Color ACCENT = new Color(140, 180, 255);
    private static final Color NET    = new Color(226, 238, 255);
    private static final Color FISH   = new Color(240, 248, 255);

    private static final int CASTS     = 6;
    private static final int FOOD_EACH = 20;
    /** Net rise, in play-area heights per second. Slow enough that leading is mandatory. */
    private static final double NET_SPEED = 1.05;

    private static final class Fish {
        double x, y, vx; int size;
        boolean gone;
    }

    private static final class Net {
        double x, y; boolean live = true;
    }

    private final Random rng = new Random();
    private final List<Fish> shoal = new ArrayList<>();
    private final List<Net>  nets  = new ArrayList<>();

    private double launcherX = 0.5;
    private int  castsLeft, caught;
    private long lastTick;
    private boolean finished;
    private int flashFrames;

    @Override public String name()     { return "Skyfish Netting"; }
    @Override public String briefing() {
        return "The shoal rides the thermals and your net is slow off the ground. Throw where "
             + "the fish is going to be, not where it is.";
    }
    @Override public String controls() { return "[←] [→] move   [Space] cast"; }
    @Override public Color  accent()   { return ACCENT; }

    @Override
    public void reset(int level) {
        castsLeft = CASTS; caught = 0; finished = false; flashFrames = 0;
        launcherX = 0.5;
        lastTick = System.currentTimeMillis();
        shoal.clear(); nets.clear();
        for (int i = 0; i < 5; i++) shoal.add(spawnFish(true));
    }

    private Fish spawnFish(boolean anywhere) {
        Fish f = new Fish();
        boolean leftToRight = rng.nextBoolean();
        f.vx = (0.14 + rng.nextDouble() * 0.22) * (leftToRight ? 1 : -1);
        f.x  = anywhere ? rng.nextDouble() : (leftToRight ? -0.08 : 1.08);
        f.y  = 0.10 + rng.nextDouble() * 0.45;
        f.size = 1 + rng.nextInt(3);
        return f;
    }

    @Override
    public void handleKey(KeyEvent e) {
        if (finished) return;
        switch (e.getKeyCode()) {
            case KeyEvent.VK_LEFT,  KeyEvent.VK_A -> launcherX = Math.max(0.04, launcherX - 0.075);
            case KeyEvent.VK_RIGHT, KeyEvent.VK_D -> launcherX = Math.min(0.96, launcherX + 0.075);
            case KeyEvent.VK_SPACE -> cast();
            default -> { }
        }
    }

    private void cast() {
        if (castsLeft <= 0) return;
        castsLeft--;
        Net n = new Net();
        n.x = launcherX;
        n.y = 0.94;
        nets.add(n);
        io.cannonforge.retroquest.core.SoundManager.getInstance().play("swing");
    }

    @Override
    public void update() {
        if (finished) return;
        long now = System.currentTimeMillis();
        double dt = Math.min(0.1, (now - lastTick) / 1000.0);
        lastTick = now;
        if (flashFrames > 0) flashFrames--;

        for (Fish f : shoal) {
            f.x += f.vx * dt;
            f.y += Math.sin(now / 900.0 + f.x * 6) * 0.00035;
            if (f.x < -0.15 || f.x > 1.15) f.gone = true;
        }
        shoal.removeIf(f -> f.gone);
        while (shoal.size() < 5) shoal.add(spawnFish(false));

        for (Net n : nets) {
            n.y -= NET_SPEED * dt;
            if (n.y < -0.05) n.live = false;
            if (!n.live) continue;
            for (Fish f : shoal) {
                if (f.gone) continue;
                double dx = Math.abs(f.x - n.x), dy = Math.abs(f.y - n.y);
                if (dx < 0.055 + f.size * 0.008 && dy < 0.05) {
                    f.gone = true; n.live = false; caught++; flashFrames = 10;
                    io.cannonforge.retroquest.core.SoundManager.getInstance().play("hit");
                    break;
                }
            }
        }
        nets.removeIf(n -> !n.live);

        if (castsLeft <= 0 && nets.isEmpty()) finished = true;
    }

    @Override public boolean isFinished() { return finished; }
    @Override public int foodYield()      { return caught * FOOD_EACH; }

    @Override
    public String resultText() {
        if (caught == 0)     return "Six nets thrown at empty air. The shoal never even turned.";
        if (caught >= CASTS) return "Six casts, six fish. You were throwing where they had not got to yet.";
        if (caught >= 4)     return caught + " out of the thermals — the shoal is lighter for it.";
        return caught + " netted. The rest were somewhere else by the time the net arrived.";
    }

    @Override
    public void paint(Graphics2D g, int px, int py, int pw, int ph) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Banded sky, brighter at the top
        for (int y = 0; y < ph; y++) {
            float t = y / (float) ph;
            // Dusk over the spire: dark enough that a pale fish is the brightest thing in frame
            int v = (int)(58 - 34 * t);
            g.setColor(new Color(Math.max(0, v - 14), Math.max(0, v - 2), Math.min(255, v + 46)));
            g.drawLine(px, py + y, px + pw, py + y);
        }
        if (flashFrames > 0) {
            g.setColor(new Color(255, 255, 255, flashFrames * 8));
            g.fillRect(px, py, pw, ph);
        }

        // Wind streaks drifting with the shoal
        long now = System.currentTimeMillis();
        g.setColor(new Color(226, 238, 255, 60));
        for (int i = 0; i < 5; i++) {
            int sy = py + (int)(ph * (0.12 + i * 0.16));
            int off = (int)((now / 22 + i * 90) % (pw + 60)) - 60;
            g.drawLine(px + off, sy, px + off + OverlayTheme.scaled(26), sy - 2);
        }

        // Shoal
        for (Fish f : shoal) {
            int fx = px + (int)(f.x * pw), fy = py + (int)(f.y * ph);
            int w = OverlayTheme.scaled(9 + f.size * 3), h = Math.max(3, OverlayTheme.scaled(3 + f.size));
            boolean right = f.vx > 0;
            g.setColor(FISH);
            g.fillOval(fx - w / 2, fy - h / 2, w, h);
            g.fillRect(right ? fx - w / 2 - 2 : fx + w / 2, fy - 1, 3, 2);   // tail behind it
            g.setColor(new Color(90, 120, 170));
            g.fillRect(right ? fx + w / 2 - 2 : fx - w / 2 + 1, fy - 1, 1, 1);
        }

        // Nets in flight — an opening ring of cord
        for (Net n : nets) {
            int nx = px + (int)(n.x * pw), ny = py + (int)(n.y * ph);
            int r = OverlayTheme.scaled(7) + (int)((0.94 - n.y) * OverlayTheme.scaled(10));
            g.setColor(NET);
            g.drawOval(nx - r, ny - r / 2, r * 2, r);
            g.drawLine(nx - r, ny, nx + r, ny);
            g.drawLine(nx, ny - r / 2, nx, ny + r / 2);
        }

        // Launcher on the ground line
        int lx = px + (int)(launcherX * pw), ly = py + ph - OverlayTheme.scaled(6);
        g.setColor(new Color(60, 70, 90));
        g.fillRect(px, ly + 2, pw, OverlayTheme.scaled(4));
        g.setColor(ACCENT);
        g.fillRect(lx - OverlayTheme.scaled(5), ly - OverlayTheme.scaled(3), OverlayTheme.scaled(10), OverlayTheme.scaled(5));
        g.drawLine(lx, ly - OverlayTheme.scaled(3), lx, ly - OverlayTheme.scaled(9));

        g.setFont(OverlayTheme.F_SMALL);
        g.setColor(new Color(180, 200, 235));
        String hud = "NETS " + castsLeft + "   TAKEN " + caught;
        g.drawString(hud, px + pw - g.getFontMetrics().stringWidth(hud) - OverlayTheme.scaled(6),
                     py + OverlayTheme.scaled(12));
    }
}

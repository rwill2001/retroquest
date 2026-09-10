package io.cannonforge.retroquest.overlay;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.util.Random;

/**
 * Island 1 · Lirandel — <b>Snare Line</b>.
 *
 * <p>Three moonlit trails run left to right with a snare strung across each. Animals cross at
 * their own pace; you spring a snare with 1, 2 or 3 and catch whatever is standing in it.
 *
 * <p>The verb is <em>timing across lanes</em>: the difficulty is not the reaction, it is that
 * three lanes are running at once and springing an empty snare costs you the loop it was set on.
 */
class SnareLineHunt implements HuntGame {

    private static final Color ACCENT   = new Color(180, 200, 255);
    private static final Color NIGHT    = new Color(9, 15, 13);
    private static final Color TRAIL    = new Color(34, 44, 33);
    private static final Color WIRE     = new Color(186, 200, 220);
    private static final Color BEAST    = new Color(86, 76, 62);
    private static final Color BEAST_LIT= new Color(176, 188, 208);

    private static final int LANES      = 3;
    private static final int ATTEMPTS   = 5;
    private static final int FOOD_EACH  = 24;
    /** How far from the snare a beast can be and still be caught, in play-area fractions. */
    private static final double TOLERANCE = 0.055;
    /** A hunt has to end even if the player never touches a key — otherwise walking away
     *  from the overlay leaves it up forever with the game underneath it frozen. */
    private static final long DURATION = 40_000;

    private final Random rng = new Random();

    /** Position of each lane's animal, 0..1 across the trail; <0 means the lane is empty. */
    private final double[] pos   = new double[LANES];
    private final double[] speed = new double[LANES];
    private final int[]    kind  = new int[LANES];
    /** Frames left showing the sprung animation, per lane. */
    private final int[]    sprung = new int[LANES];
    private final boolean[] caught = new boolean[LANES];
    /** A lane whose snare has been spent has to be reset before it can catch again. */
    private final boolean[] armed = new boolean[LANES];

    private int  attemptsLeft;
    private int  caughtCount;
    private long lastTick, start;
    private boolean finished;

    @Override public String name()     { return "Snare Line"; }
    @Override public String briefing() {
        return "Three trails, three snares. Spring one while something is standing in it. "
             + "An empty snare has to be reset, and you only have five.";
    }
    @Override public String controls() { return "[1] [2] [3] spring that lane's snare"; }
    @Override public Color  accent()   { return ACCENT; }

    @Override
    public void reset(int level) {
        attemptsLeft = ATTEMPTS;
        caughtCount  = 0;
        finished     = false;
        lastTick = start = System.currentTimeMillis();
        for (int i = 0; i < LANES; i++) {
            armed[i]  = true;
            sprung[i] = 0;
            caught[i] = false;
            respawn(i, true);
        }
    }

    /** Sends a fresh animal down a lane, staggered so all three never arrive together. */
    private void respawn(int lane, boolean stagger) {
        pos[lane]   = stagger ? -rng.nextDouble() * 1.4 : -0.15 - rng.nextDouble() * 0.9;
        speed[lane] = 0.16 + rng.nextDouble() * 0.20;      // trail-widths per second
        kind[lane]  = rng.nextInt(3);
    }

    @Override
    public void handleKey(KeyEvent e) {
        if (finished) return;
        int lane = switch (e.getKeyCode()) {
            case KeyEvent.VK_1, KeyEvent.VK_NUMPAD1 -> 0;
            case KeyEvent.VK_2, KeyEvent.VK_NUMPAD2 -> 1;
            case KeyEvent.VK_3, KeyEvent.VK_NUMPAD3 -> 2;
            default -> -1;
        };
        if (lane < 0 || !armed[lane]) return;

        armed[lane]  = false;
        sprung[lane] = 14;
        attemptsLeft--;

        boolean hit = pos[lane] > 0 && Math.abs(pos[lane] - 0.5) < TOLERANCE;
        caught[lane] = hit;
        if (hit) {
            caughtCount++;
            io.cannonforge.retroquest.core.SoundManager.getInstance().play("hit");
        } else {
            io.cannonforge.retroquest.core.SoundManager.getInstance().play("miss");
        }
        if (attemptsLeft <= 0) finished = true;
    }

    @Override
    public void update() {
        if (finished) return;
        long now = System.currentTimeMillis();
        double dt = Math.min(0.1, (now - lastTick) / 1000.0);
        lastTick = now;

        for (int i = 0; i < LANES; i++) {
            if (sprung[i] > 0) {
                sprung[i]--;
                // Once the spring animation has played out, reset the loop and send a new animal
                if (sprung[i] == 0) { armed[i] = true; caught[i] = false; respawn(i, false); }
                continue;
            }
            pos[i] += speed[i] * dt;
            if (pos[i] > 1.15) respawn(i, false);
        }

        if (now - start > DURATION) finished = true;
    }

    @Override public boolean isFinished() { return finished; }
    @Override public int foodYield()      { return caughtCount * FOOD_EACH; }

    @Override
    public String resultText() {
        return switch (caughtCount) {
            case 0 -> "Five snares sprung on empty trail. The wood keeps what it has.";
            case 1 -> "One taken off the line. Thin, but it walks with you.";
            case 5 -> "Every loop closed on something. The trail is picked clean.";
            default -> caughtCount + " off the line — a fair night's work under the moon.";
        };
    }

    @Override
    public void paint(Graphics2D g, int px, int py, int pw, int ph) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g.setColor(NIGHT);
        g.fillRect(px, py, pw, ph);

        // A low moon behind the trees, so the silhouettes have something to be silhouettes against
        g.setColor(new Color(200, 214, 245, 26));
        g.fillOval(px + pw - OverlayTheme.scaled(70), py + OverlayTheme.scaled(6),
                   OverlayTheme.scaled(46), OverlayTheme.scaled(46));

        int laneH = ph / LANES;
        for (int i = 0; i < LANES; i++) {
            int ly = py + i * laneH;
            int mid = ly + laneH / 2;

            // Trail bed
            g.setColor(TRAIL);
            g.fillRect(px, mid - laneH / 5, pw, laneH * 2 / 5);
            g.setColor(new Color(20, 26, 20));
            g.drawLine(px, mid - laneH / 5, px + pw, mid - laneH / 5);

            // Lane number
            g.setFont(OverlayTheme.F_SMALL);
            g.setColor(armed[i] ? ACCENT : OverlayTheme.TEXT_DIM);
            g.drawString(String.valueOf(i + 1), px + OverlayTheme.scaled(4), mid + OverlayTheme.scaled(4));

            // The snare: a loop at the halfway mark, slack when armed, snapped when sprung
            int sx = px + pw / 2;
            int loopW = OverlayTheme.scaled(16), loopH = laneH / 3;
            g.setStroke(new BasicStroke(1.4f));
            if (sprung[i] > 0) {
                g.setColor(caught[i] ? OverlayTheme.GOOD : new Color(120, 130, 145));
                int shrink = (14 - sprung[i]);
                g.drawOval(sx - loopW / 2 + shrink / 2, mid - loopH / 2 + shrink / 3,
                           Math.max(2, loopW - shrink), Math.max(2, loopH - shrink * 2 / 3));
            } else {
                g.setColor(armed[i] ? WIRE : new Color(60, 66, 76));
                g.drawOval(sx - loopW / 2, mid - loopH / 2, loopW, loopH);
            }
            // Wire up to the bent sapling
            g.setColor(armed[i] ? new Color(150, 165, 190) : new Color(50, 56, 66));
            g.drawLine(sx, mid - loopH / 2, sx + OverlayTheme.scaled(6), ly + 2);

            // The animal
            if (pos[i] > -0.1 && pos[i] < 1.15 && sprung[i] == 0) {
                int ax = px + (int)(pos[i] * pw);
                boolean nearSnare = Math.abs(pos[i] - 0.5) < TOLERANCE;
                drawBeast(g, ax, mid, laneH, kind[i], nearSnare);
            }
        }

        // Attempts remaining, as unspent loops
        g.setFont(OverlayTheme.F_SMALL);
        g.setColor(OverlayTheme.TEXT_DIM);
        String left = "SNARES " + attemptsLeft + "   TAKEN " + caughtCount;
        g.drawString(left, px + pw - g.getFontMetrics().stringWidth(left) - OverlayTheme.scaled(6),
                     py + ph - OverlayTheme.scaled(4));
    }

    /** A low, dark shape — deliberately unreadable as any particular animal. */
    private void drawBeast(Graphics2D g, int cx, int cy, int laneH, int kind, boolean lit) {
        int w = OverlayTheme.scaled(kind == 0 ? 18 : kind == 1 ? 22 : 14);
        int h = Math.max(6, laneH / (kind == 2 ? 4 : 3));
        g.setColor(lit ? BEAST_LIT : BEAST);
        g.fillOval(cx - w / 2, cy - h / 2, w, h);
        // head and legs, just enough to read as alive
        g.fillOval(cx + w / 2 - 2, cy - h / 2 - h / 3, h * 2 / 3, h * 2 / 3);
        g.fillRect(cx - w / 4, cy + h / 2 - 1, 1, h / 2);
        g.fillRect(cx + w / 4, cy + h / 2 - 1, 1, h / 2);
        if (lit) {
            g.setColor(new Color(226, 236, 255, 120));
            g.fillRect(cx + w / 2 - 1, cy - h / 2 - h / 4, 1, 1);
        }
    }
}

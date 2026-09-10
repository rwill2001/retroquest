package io.cannonforge.retroquest.overlay;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.util.Random;

/**
 * Island 5 · Thalorax — <b>Pressure Line</b>.
 *
 * <p>Something heavy has taken the bait a long way down. Hold the line to reel and the tension
 * climbs; let go and it falls. Keep the tension inside the band the line can survive and the fish
 * comes up. Sit outside the band and the line frays, and a frayed line does not come back.
 *
 * <p>The verb is <em>holding a value inside a moving window</em> — a continuous control problem
 * rather than a discrete one, which is the opposite of every other hunt.
 */
class PressureLineHunt implements HuntGame {

    private static final Color ACCENT = new Color(60, 180, 200);
    private static final Color WATER_TOP = new Color(14, 40, 58);
    private static final Color WATER_BOT = new Color(3, 8, 16);

    private static final int  MAX_FOOD  = 140;
    private static final long DURATION  = 15_000;
    /** Tension moves this much per second while reeling / while slack. */
    private static final double PULL = 0.40, SLIP = 0.30;

    private final Random rng = new Random();

    private double tension;        // 0..1, the needle
    private double bandCentre;     // 0..1, middle of the safe band
    private double bandDrift;
    private double bandHalf;       // half-height of the band
    private double haul;           // 0..1, how far the fish has been brought up
    private double fray;           // 0..1, line damage; 1 snaps
    private boolean reeling;
    private long start, lastTick;
    private boolean finished, snapped, landed;

    @Override public String name()     { return "Pressure Line"; }
    @Override public String briefing() {
        return "Something heavy is on the line. Hold to reel and the tension climbs, let go and "
             + "it falls. Keep it inside the band — outside it, the line frays.";
    }
    @Override public String controls() { return "[Space] toggle: reel in / give line"; }
    @Override public Color  accent()   { return ACCENT; }

    @Override
    public void reset(int level) {
        tension    = 0.35;
        bandCentre = 0.45;
        bandDrift  = 0.10 + rng.nextDouble() * 0.06;
        bandHalf   = 0.13;
        haul = 0; fray = 0;
        reeling = false; finished = false; snapped = false; landed = false;
        start = lastTick = System.currentTimeMillis();
    }

    // Space TOGGLES the reel rather than being held down. Holding would be the natural verb,
    // but Swing does not deliver key-up reliably behind an overlay, so a hold has to be faked
    // from key-repeat — and the OS waits half a second before the first repeat, which reads as
    // the reel cutting out the instant you grab it.
    @Override
    public void handleKey(KeyEvent e) {
        if (finished) return;
        if (e.getKeyCode() == KeyEvent.VK_SPACE) reeling = !reeling;
    }

    @Override
    public void update() {
        if (finished) return;
        long now = System.currentTimeMillis();
        double dt = Math.min(0.1, (now - lastTick) / 1000.0);
        lastTick = now;

        // The fish fights: the band wanders, and wanders faster the closer it gets to the boat
        bandCentre += Math.sin(now / 900.0) * bandDrift * dt
                    + Math.sin(now / 331.0) * bandDrift * 0.5 * dt * (0.4 + haul);
        bandCentre = Math.max(0.18, Math.min(0.82, bandCentre));
        bandHalf   = 0.135 - 0.045 * haul;      // the window narrows as it tires

        tension += (reeling ? PULL : -SLIP) * dt;
        tension = Math.max(0, Math.min(1, tension));

        boolean inBand = Math.abs(tension - bandCentre) < bandHalf;
        if (inBand) {
            haul = Math.min(1, haul + 0.115 * dt);
            fray = Math.max(0, fray - 0.05 * dt);
        } else {
            double over = Math.abs(tension - bandCentre) - bandHalf;
            fray += (0.11 + over * 0.45) * dt;
            haul = Math.max(0, haul - 0.035 * dt);
        }

        if (fray >= 1)   { snapped = true; finished = true; }
        else if (haul >= 1) { landed = true; finished = true; }
        else if (now - start > DURATION) finished = true;
    }

    @Override public boolean isFinished() { return finished; }

    @Override
    public int foodYield() {
        if (snapped) return 0;
        return (int)Math.round(MAX_FOOD * haul);
    }

    @Override
    public String resultText() {
        if (snapped) return "The line went, and took the hook with it. Whatever that was, it is still down there.";
        if (landed)  return "It came over the lip whole. That is a week of eating.";
        if (haul > 0.5) return "You brought it most of the way before the light went. Enough came up to matter.";
        return "It sat on the bottom and waited you out.";
    }

    @Override
    public void paint(Graphics2D g, int px, int py, int pw, int ph) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Water column, darker with depth
        for (int y = 0; y < ph; y++) {
            float t = y / (float) ph;
            g.setColor(new Color(
                (int)(WATER_TOP.getRed()   + (WATER_BOT.getRed()   - WATER_TOP.getRed())   * t),
                (int)(WATER_TOP.getGreen() + (WATER_BOT.getGreen() - WATER_TOP.getGreen()) * t),
                (int)(WATER_TOP.getBlue()  + (WATER_BOT.getBlue()  - WATER_TOP.getBlue())  * t)));
            g.drawLine(px, py + y, px + pw, py + y);
        }

        long now = System.currentTimeMillis();
        // Marine snow drifting down
        g.setColor(new Color(180, 220, 235, 50));
        for (int i = 0; i < 30; i++) {
            int sx = px + (int)((Math.sin(i * 3.1) * 0.5 + 0.5) * pw);
            int sy = py + (int)(((now / 26.0 + i * 47) % ph));
            g.fillRect(sx, sy, 1, 1);
        }

        // ── Tension gauge, down the right-hand side ──────────────────────────
        int gw = OverlayTheme.scaled(26);
        int gx = px + pw - gw - OverlayTheme.scaled(12);
        int gy = py + OverlayTheme.scaled(10);
        int gh = ph - OverlayTheme.scaled(28);

        g.setColor(new Color(6, 16, 24));
        g.fillRect(gx, gy, gw, gh);
        g.setColor(new Color(40, 70, 86));
        g.drawRect(gx, gy, gw, gh);

        // Safe band — the thing you are actually playing
        int bandY = gy + (int)((1 - (bandCentre + bandHalf)) * gh);
        int bandH = Math.max(3, (int)(2 * bandHalf * gh));
        boolean inBand = Math.abs(tension - bandCentre) < bandHalf;
        g.setColor(inBand ? new Color(60, 200, 140, 120) : new Color(60, 140, 160, 70));
        g.fillRect(gx + 1, bandY, gw - 2, bandH);
        g.setColor(inBand ? OverlayTheme.GOOD : new Color(80, 150, 170));
        g.drawLine(gx + 1, bandY, gx + gw - 2, bandY);
        g.drawLine(gx + 1, bandY + bandH, gx + gw - 2, bandY + bandH);

        // Needle
        int ny = gy + (int)((1 - tension) * gh);
        g.setColor(reeling ? new Color(255, 220, 120) : OverlayTheme.TEXT_BRIGHT);
        g.setStroke(new BasicStroke(2f));
        g.drawLine(gx - OverlayTheme.scaled(4), ny, gx + gw + OverlayTheme.scaled(4), ny);
        g.setStroke(new BasicStroke(1f));

        g.setFont(OverlayTheme.F_SMALL);
        g.setColor(OverlayTheme.TEXT_DIM);
        String tl = "TENSION";
        g.drawString(tl, gx + gw - g.getFontMetrics().stringWidth(tl), gy - OverlayTheme.scaled(3));

        // ── The line and the fish, on the left ───────────────────────────────
        int lx = px + pw / 3;
        int surfaceY = py + OverlayTheme.scaled(10);
        int fishY = py + ph - OverlayTheme.scaled(24) - (int)(haul * (ph - OverlayTheme.scaled(44)));

        g.setColor(new Color(170, 200, 215, 200));
        for (int y = surfaceY; y < fishY; y++) {
            int wob = (int)(Math.sin(y / 7.0 + now / 260.0) * (1 + 2 * (reeling ? 1 : 0)));
            g.fillRect(lx + wob, y, 1, 1);
        }

        // The fish: a big dark shape that only resolves as it rises
        int fw = OverlayTheme.scaled(30) + (int)(haul * OverlayTheme.scaled(14));
        int fh = OverlayTheme.scaled(11) + (int)(haul * OverlayTheme.scaled(6));
        int alpha = 130 + (int)(haul * 110);
        g.setColor(new Color(20, 40, 50, alpha));
        g.fillOval(lx - fw / 2, fishY - fh / 2, fw, fh);
        g.setColor(new Color(90, 190, 200, alpha));
        g.fillOval(lx - fw / 2 + 2, fishY - fh / 2 + 2, fw - 6, fh - 5);
        g.setColor(new Color(220, 250, 255, alpha));
        g.fillOval(lx + fw / 4, fishY - 2, 3, 3);                 // eye
        g.setColor(new Color(20, 40, 50, alpha));
        int[] tx = { lx - fw / 2, lx - fw / 2 - OverlayTheme.scaled(8), lx - fw / 2 - OverlayTheme.scaled(8) };
        int[] ty = { fishY, fishY - fh / 2, fishY + fh / 2 };
        g.fillPolygon(tx, ty, 3);

        // ── Haul and fray bars along the bottom ──────────────────────────────
        int barW = pw / 3, barH = OverlayTheme.scaled(5);
        int bx = px + OverlayTheme.scaled(8), by = py + ph - OverlayTheme.scaled(12);
        g.setColor(new Color(255, 255, 255, 30)); g.fillRect(bx, by, barW, barH);
        g.setColor(ACCENT);                        g.fillRect(bx, by, (int)(barW * haul), barH);
        g.setColor(OverlayTheme.TEXT_DIM);
        g.setFont(OverlayTheme.F_SMALL);
        g.drawString("HAUL", bx, by - 2);

        int fx2 = bx + barW + OverlayTheme.scaled(16);
        g.setColor(new Color(255, 255, 255, 30)); g.fillRect(fx2, by, barW, barH);
        g.setColor(fray > 0.66 ? OverlayTheme.DANGER : new Color(210, 150, 60));
        g.fillRect(fx2, by, (int)(barW * Math.min(1, fray)), barH);
        g.setColor(OverlayTheme.TEXT_DIM);
        g.drawString("FRAY", fx2, by - 2);
    }
}

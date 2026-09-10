package io.cannonforge.retroquest.overlay;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.util.Random;

import io.cannonforge.retroquest.core.Fonts;

/**
 * Island 6 · Umbryn — <b>The Remembered Meal</b>.
 *
 * <p>Nothing lives on Umbryn, so there is nothing to hunt. What there is, at a cold ring of
 * stones, is the memory of a meal — six dishes surfacing at once and fading at their own rates.
 * Take them before they go. Reach for one that has already gone and you lose your grip on the
 * rest of the table for a moment.
 *
 * <p>The verb is <em>triage against a decay clock</em>: everything is available and everything is
 * expiring, so the skill is ordering, not reflex. It is the one hunt where the island refusing
 * to cooperate is the whole point.
 */
class RememberedMealHunt implements HuntGame {

    private static final Color ACCENT = new Color(160, 170, 200);
    private static final Color DARK   = new Color(16, 16, 22);

    private static final int SLOTS     = 6;
    private static final int ROUNDS    = 4;
    private static final int FOOD_EACH = 5;
    private static final long ROUND_MS = 5200;
    /** Reaching for a memory that has already gone costs you this long. */
    private static final long GRASP_PENALTY_MS = 750;

    private static final String[] DISHES = {
        "bread", "broth", "salt", "honey", "root", "cake", "milk", "apple", "stew", "pear"
    };

    private final Random rng = new Random();

    /** 1 = vivid, 0 = gone. Each slot fades at its own rate. */
    private final double[] life = new double[SLOTS];
    private final double[] rate = new double[SLOTS];
    private final boolean[] taken = new boolean[SLOTS];
    private final String[] label = new String[SLOTS];

    private int  round, takenCount, missedGrabs;
    private long roundStart, frozenUntil;
    private int  shakeFrames;
    private boolean finished;

    @Override public String name()     { return "The Remembered Meal"; }
    @Override public String briefing() {
        return "Nothing lives here. What is here is a meal somebody had, surfacing at a cold "
             + "fire — six dishes, all fading at their own speed. Take them before they go. "
             + "Reach for one that has already gone and the table slips.";
    }
    @Override public String controls() { return "[1]-[6] take that dish, while it is still there"; }
    @Override public Color  accent()   { return ACCENT; }

    @Override
    public void reset(int level) {
        round = 0; takenCount = 0; missedGrabs = 0; finished = false;
        frozenUntil = 0; shakeFrames = 0;
        startRound();
    }

    private void startRound() {
        java.util.List<String> menu = new java.util.ArrayList<>(java.util.List.of(DISHES));
        java.util.Collections.shuffle(menu, rng);
        for (int i = 0; i < SLOTS; i++) {
            life[i]  = 0.75 + rng.nextDouble() * 0.25;
            // The spread is the game: some dishes are gone in a second, some linger.
            rate[i]  = 0.10 + rng.nextDouble() * 0.30;
            taken[i] = false;
            label[i] = menu.get(i);
        }
        roundStart = System.currentTimeMillis();
    }

    @Override
    public void handleKey(KeyEvent e) {
        if (finished) return;
        if (System.currentTimeMillis() < frozenUntil) return;   // still recovering from a bad grab

        int s = -1;
        int code = e.getKeyCode();
        if (code >= KeyEvent.VK_1 && code <= KeyEvent.VK_6) s = code - KeyEvent.VK_1;
        else if (code >= KeyEvent.VK_NUMPAD1 && code <= KeyEvent.VK_NUMPAD6) s = code - KeyEvent.VK_NUMPAD1;
        if (s < 0 || s >= SLOTS) return;

        if (taken[s]) return;

        if (life[s] > 0.06) {
            taken[s] = true;
            takenCount++;
            io.cannonforge.retroquest.core.SoundManager.getInstance().play("blip");
        } else {
            missedGrabs++;
            frozenUntil = System.currentTimeMillis() + GRASP_PENALTY_MS;
            shakeFrames = 12;
            // A bad grab dims what is left — that is the cost, not lost time alone
            for (int i = 0; i < SLOTS; i++) if (!taken[i]) life[i] *= 0.82;
            io.cannonforge.retroquest.core.SoundManager.getInstance().play("miss");
        }
    }

    @Override
    public void update() {
        if (finished) return;
        long now = System.currentTimeMillis();
        if (shakeFrames > 0) shakeFrames--;

        double dt = 1 / 30.0;
        boolean anyLeft = false;
        for (int i = 0; i < SLOTS; i++) {
            if (taken[i]) continue;
            life[i] = Math.max(0, life[i] - rate[i] * dt);
            if (life[i] > 0.06) anyLeft = true;
        }

        if (!anyLeft || now - roundStart > ROUND_MS) {
            round++;
            if (round >= ROUNDS) finished = true;
            else startRound();
        }
    }

    @Override public boolean isFinished() { return finished; }

    @Override
    public int foodYield() {
        return Math.max(0, takenCount * FOOD_EACH - missedGrabs * 3);
    }

    @Override
    public String resultText() {
        if (takenCount == 0) return "You reached and there was nothing to reach for. It was somebody else's meal anyway.";
        if (missedGrabs == 0 && takenCount >= SLOTS * ROUNDS - 4)
            return "You took the whole table before any of it went. You have eaten something that did not happen.";
        return takenCount + " dish" + (takenCount == 1 ? "" : "es") + " off a table that was not there"
             + (missedGrabs > 0 ? ", and " + missedGrabs + " grasp" + (missedGrabs == 1 ? "" : "s") + " at nothing." : ".");
    }

    @Override
    public void paint(Graphics2D g, int px, int py, int pw, int ph) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int shake = shakeFrames > 0 ? (shakeFrames % 2 == 0 ? 2 : -2) : 0;

        g.setColor(DARK);
        g.fillRect(px, py, pw, ph);

        long now = System.currentTimeMillis();
        // The cold ring of stones, half-erased
        int rx = px + pw / 2 + shake, ry = py + ph - OverlayTheme.scaled(22);
        for (int i = 0; i < 7; i++) {
            double a = i * Math.PI * 2 / 7;
            int sx = rx + (int)(Math.cos(a) * OverlayTheme.scaled(34));
            int sy = ry + (int)(Math.sin(a) * OverlayTheme.scaled(10));
            g.setColor(new Color(70, 72, 84, i % 2 == 0 ? 210 : 90));
            g.fillOval(sx - 4, sy - 3, 8, 6);
        }
        // The fire that is not burning
        g.setColor(new Color(200, 214, 245, 55));
        g.drawOval(rx - OverlayTheme.scaled(9), ry - OverlayTheme.scaled(16),
                   OverlayTheme.scaled(18), OverlayTheme.scaled(20));

        // ── The table: six dishes across the top ─────────────────────────────
        int slotW = pw / SLOTS;
        Font fDish = Fonts.mono(11);
        for (int i = 0; i < SLOTS; i++) {
            int cx = px + slotW * i + slotW / 2 + shake;
            int cy = py + ph / 3;
            double l = taken[i] ? 0 : life[i];

            if (taken[i]) {
                // A taken dish leaves a solid, quiet mark
                g.setColor(new Color(120, 200, 160, 200));
                g.drawOval(cx - OverlayTheme.scaled(11), cy - OverlayTheme.scaled(8),
                           OverlayTheme.scaled(22), OverlayTheme.scaled(16));
                g.setColor(new Color(150, 230, 190, 220));
                g.drawLine(cx - 3, cy, cx - 1, cy + 3);
                g.drawLine(cx - 1, cy + 3, cx + 4, cy - 4);
            } else {
                int a = (int)(235 * l);
                // Flicker gets worse as the memory goes
                double flick = 1.0 - 0.35 * (1 - l) * Math.abs(Math.sin(now / (90.0 + i * 17)));
                a = (int)(a * flick);
                g.setColor(new Color(ACCENT.getRed(), ACCENT.getGreen(), ACCENT.getBlue(), Math.max(0, a)));
                g.fillOval(cx - OverlayTheme.scaled(11), cy - OverlayTheme.scaled(8),
                           OverlayTheme.scaled(22), OverlayTheme.scaled(16));
                g.setColor(new Color(240, 246, 255, Math.max(0, (int)(a * 0.7))));
                g.fillOval(cx - OverlayTheme.scaled(7), cy - OverlayTheme.scaled(6),
                           OverlayTheme.scaled(9), OverlayTheme.scaled(6));

                g.setFont(fDish);
                g.setColor(new Color(230, 236, 250, Math.max(0, (int)(a * 0.9))));
                String d = label[i];
                g.drawString(d, cx - g.getFontMetrics().stringWidth(d) / 2, cy + OverlayTheme.scaled(22));
            }

            // Slot number and its remaining life as a thin bar
            g.setFont(OverlayTheme.F_SMALL);
            g.setColor(OverlayTheme.TEXT_DIM);
            String n = String.valueOf(i + 1);
            g.drawString(n, cx - g.getFontMetrics().stringWidth(n) / 2, py + OverlayTheme.scaled(14));
            int bw = OverlayTheme.scaled(20);
            g.setColor(new Color(255, 255, 255, 24));
            g.fillRect(cx - bw / 2, py + OverlayTheme.scaled(18), bw, 2);
            g.setColor(taken[i] ? new Color(120, 200, 160)
                     : l > 0.4 ? ACCENT : new Color(190, 120, 120));
            g.fillRect(cx - bw / 2, py + OverlayTheme.scaled(18), (int)(bw * (taken[i] ? 1 : l)), 2);
        }

        if (System.currentTimeMillis() < frozenUntil) {
            g.setFont(OverlayTheme.F_SMALL);
            g.setColor(OverlayTheme.DANGER);
            String s = "THE TABLE SLIPS";
            g.drawString(s, px + (pw - g.getFontMetrics().stringWidth(s)) / 2, py + ph / 2 + OverlayTheme.scaled(28));
        }

        g.setFont(OverlayTheme.F_SMALL);
        g.setColor(OverlayTheme.TEXT_DIM);
        String hud = "SITTING " + Math.min(round + 1, ROUNDS) + "/" + ROUNDS + "   TAKEN " + takenCount;
        g.drawString(hud, px + pw - g.getFontMetrics().stringWidth(hud) - OverlayTheme.scaled(6),
                     py + ph - OverlayTheme.scaled(4));
    }
}

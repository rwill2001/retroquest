package io.cannonforge.retroquest.overlay;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Island 4 · Sylvandar — <b>Spore Lure</b>.
 *
 * <p>Four fungal caps. The forest puffs them in an order and the moths come to that order; puff
 * them back in the same order and the shoal of them settles on you instead of drifting off.
 *
 * <p>The verb is <em>repeating a sequence</em>, and each round adds one. The Sylvandar hunt is the
 * one you fail by rushing rather than by being slow.
 */
class SporeLureHunt implements HuntGame {

    private static final Color ACCENT = new Color(80, 220, 100);
    private static final Color GROUND = new Color(14, 28, 18);

    private static final int CAPS      = 4;
    private static final int ROUNDS    = 5;
    private static final int FOOD_ROUND = 24;
    private static final long LIT_MS   = 380;
    private static final long GAP_MS   = 160;
    /** How long you get to answer a round. Without it, a player who walks away from the
     *  WAITING phase leaves the overlay up forever. */
    private static final long ANSWER_MS = 9_000;

    /** The four caps, each its own colour so the sequence reads as a tune, not a list. */
    private static final Color[] CAP_COL = {
        new Color( 90, 220, 150), new Color(120, 200, 255),
        new Color(220, 190, 110), new Color(190, 130, 240)
    };

    private enum Phase { SHOWING, WAITING, GOOD, BAD, DONE }

    private final Random rng = new Random();
    private final List<Integer> sequence = new ArrayList<>();

    private Phase  phase;
    private int    showIndex;      // which step of the sequence is being puffed
    private long   phaseStart;
    private int    inputIndex;
    private int    round, cleared;
    private int    litCap = -1;    // cap currently glowing, -1 for none
    private int    flashCap = -1;
    private final double[] bloom = new double[CAPS];
    private boolean finished;

    @Override public String name()     { return "Spore Lure"; }
    @Override public String briefing() {
        return "The caps puff in an order and the moths answer it. Watch the order, then puff it "
             + "back. Every round the forest adds one more.";
    }
    @Override public String controls() { return "[1]-[4] puff that cap"; }
    @Override public Color  accent()   { return ACCENT; }

    @Override
    public void reset(int level) {
        sequence.clear(); round = 0; cleared = 0; finished = false; flashCap = -1;
        for (int i = 0; i < CAPS; i++) bloom[i] = 0;
        nextRound();
    }

    private void nextRound() {
        sequence.add(rng.nextInt(CAPS));
        showIndex = 0; inputIndex = 0; litCap = -1;
        phase = Phase.SHOWING;
        phaseStart = System.currentTimeMillis();
    }

    @Override
    public void handleKey(KeyEvent e) {
        if (finished || phase != Phase.WAITING) return;
        int cap = -1;
        int code = e.getKeyCode();
        if (code >= KeyEvent.VK_1 && code <= KeyEvent.VK_4) cap = code - KeyEvent.VK_1;
        else if (code >= KeyEvent.VK_NUMPAD1 && code <= KeyEvent.VK_NUMPAD4) cap = code - KeyEvent.VK_NUMPAD1;
        if (cap < 0) return;

        bloom[cap] = 1.0;
        flashCap = cap;

        if (sequence.get(inputIndex) == cap) {
            inputIndex++;
            io.cannonforge.retroquest.core.SoundManager.getInstance().play("blip");
            if (inputIndex >= sequence.size()) {
                cleared++;
                round++;
                phase = Phase.GOOD;
                phaseStart = System.currentTimeMillis();
            }
        } else {
            phase = Phase.BAD;
            phaseStart = System.currentTimeMillis();
            io.cannonforge.retroquest.core.SoundManager.getInstance().play("miss");
        }
    }

    @Override
    public void update() {
        if (finished) return;
        long since = System.currentTimeMillis() - phaseStart;
        for (int i = 0; i < CAPS; i++) bloom[i] = Math.max(0, bloom[i] - 0.06);

        switch (phase) {
            case SHOWING -> {
                // Alternate lit / dark so repeats in the sequence are distinguishable
                long cycle = LIT_MS + GAP_MS;
                int step = (int)(since / cycle);
                boolean lit = (since % cycle) < LIT_MS;
                if (step >= sequence.size()) {
                    litCap = -1;
                    phase = Phase.WAITING;
                    phaseStart = System.currentTimeMillis();
                } else {
                    int cap = sequence.get(step);
                    litCap = lit ? cap : -1;
                    if (lit) bloom[cap] = Math.max(bloom[cap], 0.85);
                    showIndex = step;
                }
            }
            case GOOD -> {
                if (since > 520) {
                    if (round >= ROUNDS) { phase = Phase.DONE; finished = true; }
                    else nextRound();
                }
            }
            case WAITING -> {
                if (since > ANSWER_MS) { phase = Phase.BAD; phaseStart = System.currentTimeMillis(); }
            }
            case BAD -> { if (since > 700) { phase = Phase.DONE; finished = true; } }
            default -> { }
        }
    }

    @Override public boolean isFinished() { return finished; }
    @Override public int foodYield()      { return cleared * FOOD_ROUND; }

    @Override
    public String resultText() {
        if (cleared == 0)      return "You answered wrong on the first puff. The moths went elsewhere.";
        if (cleared >= ROUNDS) return "Five rounds, note for note. They came down on you like snow.";
        return "You held the order for " + cleared + " round" + (cleared == 1 ? "" : "s")
             + " before the forest got ahead of you.";
    }

    @Override
    public void paint(Graphics2D g, int px, int py, int pw, int ph) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g.setColor(GROUND);
        g.fillRect(px, py, pw, ph);

        // Root mat running under everything
        g.setColor(new Color(30, 26, 18));
        for (int i = 0; i < 6; i++) {
            int y = py + ph * i / 6 + OverlayTheme.scaled(4);
            for (int x = 0; x < pw; x += 2)
                g.fillRect(px + x, y + (int)(3 * Math.sin(x / 9.0 + i)), 2, 1);
        }

        long now = System.currentTimeMillis();
        // Motes, denser when a cap is lit
        for (int i = 0; i < 26; i++) {
            double t = (now / 1400.0 + i * 0.37) % 1.0;
            int mx = px + (int)((Math.sin(i * 2.3) * 0.5 + 0.5) * pw);
            int my = py + ph - (int)(t * ph);
            g.setColor(new Color(170, 255, 200, (int)(90 * (1 - t))));
            g.fillRect(mx, my, 1, 1);
        }

        int capW = pw / CAPS;
        for (int i = 0; i < CAPS; i++) {
            int cx = px + capW * i + capW / 2;
            int cy = py + ph / 2 + OverlayTheme.scaled(6);
            boolean lit = (litCap == i) || bloom[i] > 0.05;
            double b = Math.max(litCap == i ? 0.9 : 0, bloom[i]);

            // Glow pool on the ground
            if (b > 0.02) {
                int gr = (int)(OverlayTheme.scaled(26) * b) + OverlayTheme.scaled(10);
                g.setColor(new Color(CAP_COL[i].getRed(), CAP_COL[i].getGreen(), CAP_COL[i].getBlue(),
                                     (int)(70 * b)));
                g.fillOval(cx - gr, cy - gr / 2, gr * 2, gr);
            }

            // Stalk
            g.setColor(new Color(70, 82, 62));
            g.fillRect(cx - 3, cy, 6, OverlayTheme.scaled(22));

            // Cap
            int w = OverlayTheme.scaled(44), h = OverlayTheme.scaled(24);
            Color c = CAP_COL[i];
            g.setColor(lit ? c : new Color(c.getRed() / 3, c.getGreen() / 3, c.getBlue() / 3));
            g.fillArc(cx - w / 2, cy - h, w, h * 2, 0, 180);
            g.setColor(new Color(255, 255, 255, (int)(150 * b)));
            g.fillArc(cx - w / 4, cy - h + 2, w / 2, h, 20, 140);

            // Gills
            g.setColor(new Color(20, 34, 24, 140));
            for (int k = -2; k <= 2; k++) g.drawLine(cx + k * w / 8, cy, cx + k * w / 6, cy - h / 3);

            // Number
            g.setFont(OverlayTheme.F_SMALL);
            g.setColor(flashCap == i && phase == Phase.BAD ? OverlayTheme.DANGER : OverlayTheme.TEXT_DIM);
            String n = String.valueOf(i + 1);
            g.drawString(n, cx - g.getFontMetrics().stringWidth(n) / 2, cy + OverlayTheme.scaled(34));
        }

        // Round pips along the top: what you have cleared, what is left
        g.setFont(OverlayTheme.F_SMALL);
        for (int i = 0; i < ROUNDS; i++) {
            int bx = px + OverlayTheme.scaled(8) + i * OverlayTheme.scaled(10);
            g.setColor(i < cleared ? ACCENT : new Color(40, 60, 46));
            g.fillOval(bx, py + OverlayTheme.scaled(6), OverlayTheme.scaled(6), OverlayTheme.scaled(6));
        }

        String state = switch (phase) {
            case SHOWING -> "WATCH  (" + (showIndex + 1) + "/" + sequence.size() + ")";
            case WAITING -> "ANSWER (" + inputIndex + "/" + sequence.size() + ")";
            case GOOD    -> "THE MOTHS COME";
            case BAD     -> "WRONG NOTE";
            case DONE    -> "";
        };
        g.setColor(phase == Phase.BAD ? OverlayTheme.DANGER
                 : phase == Phase.GOOD ? OverlayTheme.GOOD : OverlayTheme.TEXT_DIM);
        g.drawString(state, px + pw - g.getFontMetrics().stringWidth(state) - OverlayTheme.scaled(6),
                     py + OverlayTheme.scaled(12));
    }
}

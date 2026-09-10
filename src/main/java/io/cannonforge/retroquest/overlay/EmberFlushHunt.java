package io.cannonforge.retroquest.overlay;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.util.Random;

/**
 * Island 2 · Pyralis — <b>Ember Flush</b>.
 *
 * <p>Nine vents in the basalt. Cinder-crabs shelter in them and bolt when the heat rises, so the
 * vent brightens for a moment <em>before</em> anything comes out of it. Read the tell, have the
 * right number pressed when the crab shows.
 *
 * <p>The verb is <em>reacting to a warning, not to the thing itself</em>. Waiting for the crab is
 * always slightly too late; the game is learning to trust the glow.
 */
class EmberFlushHunt implements HuntGame {

    private static final Color ACCENT = new Color(255, 120, 40);
    private static final Color ROCK   = new Color(28, 20, 17);
    private static final Color SEAM   = new Color(18, 12, 10);
    private static final Color VENT_COLD = new Color(52, 34, 28);
    private static final Color CRAB   = new Color(40, 26, 22);

    private static final int COLS = 3, ROWS = 3, VENTS = COLS * ROWS;
    private static final int ROUNDS    = 8;
    private static final int FOOD_EACH = 16;

    /** Milliseconds the vent glows before the crab appears, and how long it is catchable. */
    private static final long TELL_MS  = 420;
    private static final long OUT_MS   = 430;
    private static final long GAP_MS   = 300;

    private enum Phase { GAP, TELL, OUT, RESOLVED }

    private final Random rng = new Random();
    /** 0..1 heat used only for the idle shimmer, per vent. */
    private final double[] heat = new double[VENTS];

    private Phase phase;
    private int   active;         // which vent is doing something
    private long  phaseStart;
    private int   round, caught;
    private boolean lastHit;
    private int   pressedVent = -1;
    private boolean finished;

    @Override public String name()     { return "Ember Flush"; }
    @Override public String briefing() {
        return "Cinder-crabs shelter in the vents and bolt when the heat comes up. The vent "
             + "brightens before the crab shows — press its number while the crab is out.";
    }
    @Override public String controls() { return "[1]-[9] strike that vent"; }
    @Override public Color  accent()   { return ACCENT; }

    @Override
    public void reset(int level) {
        round = 0; caught = 0; finished = false; lastHit = false; pressedVent = -1;
        for (int i = 0; i < VENTS; i++) heat[i] = rng.nextDouble();
        startGap();
    }

    private void startGap() {
        phase = Phase.GAP;
        phaseStart = System.currentTimeMillis();
        active = rng.nextInt(VENTS);
    }

    @Override
    public void handleKey(KeyEvent e) {
        if (finished) return;
        int v = -1;
        int code = e.getKeyCode();
        if (code >= KeyEvent.VK_1 && code <= KeyEvent.VK_9) v = code - KeyEvent.VK_1;
        else if (code >= KeyEvent.VK_NUMPAD1 && code <= KeyEvent.VK_NUMPAD9) v = code - KeyEvent.VK_NUMPAD1;
        if (v < 0 || v >= VENTS) return;

        // A strike lands only while the crab is actually out. Striking on the tell is too early.
        pressedVent = v;
        if (phase == Phase.OUT && v == active) {
            caught++; lastHit = true;
            io.cannonforge.retroquest.core.SoundManager.getInstance().play("hit");
        } else {
            lastHit = false;
            io.cannonforge.retroquest.core.SoundManager.getInstance().play("miss");
        }
        if (phase == Phase.TELL || phase == Phase.OUT) resolve();
    }

    private void resolve() {
        phase = Phase.RESOLVED;
        phaseStart = System.currentTimeMillis();
        round++;
        if (round >= ROUNDS) finished = true;
    }

    @Override
    public void update() {
        if (finished) return;
        long since = System.currentTimeMillis() - phaseStart;
        for (int i = 0; i < VENTS; i++) heat[i] = (heat[i] + 0.013) % 1.0;

        switch (phase) {
            case GAP      -> { if (since > GAP_MS)  { phase = Phase.TELL; phaseStart = System.currentTimeMillis(); } }
            case TELL     -> { if (since > TELL_MS) { phase = Phase.OUT;  phaseStart = System.currentTimeMillis(); } }
            case OUT      -> { if (since > OUT_MS)  { lastHit = false; resolve(); } }   // it got away
            case RESOLVED -> { if (since > 260) { pressedVent = -1; if (!finished) startGap(); } }
        }
    }

    @Override public boolean isFinished() { return finished; }
    @Override public int foodYield()      { return caught * FOOD_EACH; }

    @Override
    public String resultText() {
        if (caught == 0)      return "Eight vents, eight empty hands. The rock keeps its own.";
        if (caught >= ROUNDS) return "Every one of them, straight off the glow. Nothing down there is faster than you.";
        if (caught >= 5)      return caught + " crabs off the basalt — enough shell to walk on.";
        return caught + " taken. The rest read the heat before you did.";
    }

    @Override
    public void paint(Graphics2D g, int px, int py, int pw, int ph) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g.setColor(ROCK);
        g.fillRect(px, py, pw, ph);

        // Basalt seams, so the grid sits in rock rather than on a board
        g.setColor(SEAM);
        for (int i = 1; i < 5; i++) {
            int y = py + ph * i / 5;
            g.drawLine(px, y + (i % 2 == 0 ? 3 : -2), px + pw, y + (i % 2 == 0 ? -3 : 2));
        }

        int padX = OverlayTheme.scaled(14), padY = OverlayTheme.scaled(10);
        int cw = (pw - padX * 2) / COLS, chh = (ph - padY * 2) / ROWS;
        int cell = Math.min(cw, chh);
        int gx = px + (pw - cell * COLS) / 2;
        int gy = py + (ph - cell * ROWS) / 2;

        long since = System.currentTimeMillis() - phaseStart;

        for (int v = 0; v < VENTS; v++) {
            int cx = gx + (v % COLS) * cell + cell / 2;
            int cy = gy + (v / COLS) * cell + cell / 2;
            int r  = (int)(cell * 0.34);

            boolean isActive = (v == active);
            boolean telling  = isActive && phase == Phase.TELL;
            boolean out      = isActive && phase == Phase.OUT;

            // Idle vents barely breathe. The whole game is reading the tell, so a cold vent has
            // to be obviously cold — an idle glow anywhere near the tell's brightness and the
            // field just looks like nine hot holes.
            double idle = 0.05 + 0.05 * Math.sin(heat[v] * Math.PI * 2);
            double glow = telling ? 0.35 + 0.65 * (since / (double) TELL_MS)
                        : out    ? 1.0
                        : idle;

            // Vent throat
            g.setColor(VENT_COLD);
            g.fillOval(cx - r - 2, cy - r - 2, (r + 2) * 2, (r + 2) * 2);
            int gr = (int)( 58 + 197 * glow);
            int gg = (int)( 24 + 166 * glow);
            int gb = (int)( 16 +  74 * glow);
            g.setColor(new Color(Math.min(255, gr), Math.min(255, gg), Math.min(255, gb)));
            g.fillOval(cx - r, cy - r, r * 2, r * 2);
            g.setColor(new Color(255, 225, 165, (int)(220 * Math.max(0, glow - 0.18))));
            g.fillOval(cx - r / 3, cy - r / 3, Math.max(2, r * 2 / 3), Math.max(2, r * 2 / 3));
            // A vent about to blow throws light onto the rock around it
            if (glow > 0.4) {
                g.setColor(new Color(255, 140, 50, (int)(60 * (glow - 0.4))));
                g.fillOval(cx - r * 2, cy - r * 2, r * 4, r * 4);
            }

            // The crab, only while it is out
            if (out) drawCrab(g, cx, cy, r);

            // Feedback ring on the vent that was struck
            if (phase == Phase.RESOLVED && v == pressedVent) {
                g.setColor(lastHit ? OverlayTheme.GOOD : OverlayTheme.DANGER);
                g.drawOval(cx - r - 4, cy - r - 4, (r + 4) * 2, (r + 4) * 2);
            }

            // Number, always legible against the glow
            g.setFont(OverlayTheme.F_SMALL);
            g.setColor(new Color(255, 235, 210, 190));
            String n = String.valueOf(v + 1);
            g.drawString(n, cx - g.getFontMetrics().stringWidth(n) / 2, cy + cell / 2 - 2);
        }

        g.setFont(OverlayTheme.F_SMALL);
        g.setColor(OverlayTheme.TEXT_DIM);
        String hud = "VENT " + Math.min(round + 1, ROUNDS) + "/" + ROUNDS + "   TAKEN " + caught;
        g.drawString(hud, px + pw - g.getFontMetrics().stringWidth(hud) - OverlayTheme.scaled(6),
                     py + ph - OverlayTheme.scaled(4));
    }

    private void drawCrab(Graphics2D g, int cx, int cy, int r) {
        int w = (int)(r * 1.5), h = (int)(r * 1.1);
        g.setColor(CRAB);
        g.fillOval(cx - w / 2, cy - h / 2, w, h);
        // claws and legs
        g.fillRect(cx - w / 2 - 2, cy - 2, 3, 2);
        g.fillRect(cx + w / 2 - 1, cy - 2, 3, 2);
        for (int i = -1; i <= 1; i += 2) {
            g.fillRect(cx + i * w / 3, cy + h / 2 - 1, 1, 3);
        }
        g.setColor(new Color(255, 190, 120));
        g.fillRect(cx - 2, cy - 1, 1, 1);
        g.fillRect(cx + 2, cy - 1, 1, 1);
    }
}

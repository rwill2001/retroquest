import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Renders the film's typography layer to a transparent PNG sequence.
 *
 * <p>Everything in the cut that is not gameplay lives here: the CRT boot, the title, every
 * lower-third band, the Act 5 turn, the lockup and the power-off. ffmpeg composites this over the
 * picture track assembled from the recorder clips (see {@code tools/render-film.sh}), which is why
 * the final MP4 can be built without a browser at all.
 *
 * <p><b>Fonts.</b> The style guide specifies Press Start 2P, VT323 and IBM Plex Mono, which are
 * web fonts and are not installed here. This renders in Java's {@code Monospaced} — which is what
 * {@code core/Fonts.java} uses for every glyph in the game itself, so the titles match the footage
 * rather than the style guide. If the web fonts matter more than that consistency, the browser
 * export is the path; this one trades a typeface for being reproducible offline.
 *
 * <pre>  javac -d out/titles tools/FilmTitles.java &amp;&amp; java -cp out/titles FilmTitles out/titles/frames</pre>
 */
public final class FilmTitles {

    static final int W = 1920, H = 1080, FPS = 30;
    static final double END = 195;

    // Act cues — must track docs/video/01-SCRIPT.md and hype-video.jsx.
    static final double A0 = 0, A1 = 8, A2 = 33, A3 = 65, A4 = 91, A5 = 143, A6 = 155, A7 = 185;

    static final Color GROUND = new Color(0x06, 0x08, 0x0E);
    static final Color PANEL  = new Color(0x0A, 0x0E, 0x16);
    static final Color PHOS   = new Color(0x00, 0xFF, 0x78);
    static final Color CYAN   = new Color(0x00, 0xC8, 0xFF);
    static final Color AMBER  = new Color(0xFF, 0xC8, 0x32);
    static final Color TEXT   = new Color(0xC8, 0xDE, 0xFF);
    static final Color DIM    = new Color(0x50, 0x69, 0x87);

    static final int BAND_H2 = 132, BAND_H1 = 92;

    record BandCue(double at, double until, String l1, String l2, Color accent) {}
    static final List<BandCue> BANDS = new ArrayList<>();

    static void band(double at, double until, String l1, String l2, Color c) {
        BANDS.add(new BandCue(at, until, l1, l2, c));
    }

    static {
        band(A1 + 0.6, A1 + 13, "STR · DEX · CON · INT · WIS · CHA", null, PHOS);
        band(33.4, 38, "SEVEN ISLANDS, ONE KEY EACH",
             "LIRANDEL → PYRALIS → ZEPHYRION → SYLVANDAR → THALORAX → UMBRYN → BELLORAK", PHOS);
        band(38.4, 43, "22 TOWNS · 154 NPCs", null, PHOS);
        band(43.4, 48, "91 QUESTS", "BRANCHING DIALOGUE TREES", PHOS);
        band(48.4, 53, "140 ITEMS", null, PHOS);
        String[] venues = { "CASINO", "SKY RACES", "NATURE TRIALS", "WAR GAMES", "THE FORGE ARENA" };
        for (int i = 0; i < venues.length; i++) band(53 + i * 1.4, 53 + (i + 1) * 1.4, venues[i], null, PHOS);
        band(75.6, 82, "34 COMBAT SPELLS OF 37", "78 MONSTERS · LEVELS 1–50", PHOS);
        band(95.5, 102, "ISLAND 1 · LIRANDEL", "TOP-DOWN GRID", CYAN);
        band(102.5, 109, "ISLANDS 2–3 · PYRALIS / ZEPHYRION", "WIREFRAME", CYAN);
        band(109.5, 117, "ISLAND 4 · ROOTVAULT", "TEXTURED — BARK", CYAN);
        String[] styles = { "BLOCK", "COBBLE", "SLAB", "CYCLOPEAN" };
        for (int i = 0; i < styles.length; i++)
            band(117 + i * 1.75, 117 + (i + 1) * 1.75, "ISLANDS 4–5 · TEXTURED", styles[i], CYAN);
        band(124.5, 132, "ISLAND 6 · ARCHIVE OF TEARS", "RAYCAST — PER-PIXEL LIT", CYAN);
        band(132.5, 138, "ISLAND 7 · BELLORAK", "MAP-AUTHORED LIGHTING + SHADOWS", CYAN);
        band(138.5, 143, "THE CRADLE OF SHARDS", "8 LEVELS · MIRRORED · ESCALATING", CYAN);
        band(155.5, 160, "SEVEN TOOLS", "PENCIL · FILL · SELECT · NPC · TOWN · DUNGEON · SPAWN", CYAN);
        band(160.4, 164, "DIALOGUE TREE EDITOR", "NODES · CHOICES · CONDITIONS · ACTIONS", CYAN);
        band(164.2, 168, "SEVEN SUB-EDITORS", "QUEST · ITEM · MONSTER · TILE · NPC · SPRITE · DIALOGUE", CYAN);
        band(168.4, 173, "TILE EDITOR", "WALKABLE · BLOCKS VISION · LIGHT RADIUS · ON-STEP EFFECT", CYAN);
        band(173.4, 177, "IMAGE EDITOR · 32×32", "SAVED = ALREADY IN THE GAME", CYAN);
        band(177.3, 180, "SPAWN DIFFICULTY 0–99", "PAINTED TILE BY TILE", CYAN);
    }

    public static void main(String[] args) throws Exception {
        File dir = new File(args.length > 0 ? args[0] : "out/titles/frames");
        if (!dir.isDirectory() && !dir.mkdirs()) { System.err.println("cannot create " + dir); System.exit(1); }

        int total = (int) Math.round(END * FPS);
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        for (int f = 0; f < total; f++) {
            double t = f / (double) FPS;
            Graphics2D g = img.createGraphics();
            g.setComposite(AlphaComposite.Clear); g.fillRect(0, 0, W, H);
            g.setComposite(AlphaComposite.SrcOver);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            paint(g, t);
            g.dispose();
            ImageIO.write(img, "png", new File(dir, String.format("frame_%06d.png", f + 1)));
            if (f % 600 == 0) System.out.printf("  %5.1fs / %.0fs%n", t, END);
        }
        System.out.println("titles: " + total + " frames -> " + dir);
    }

    static void paint(Graphics2D g, double t) {
        if (t < A1)            { act0(g, t); return; }
        if (t >= 21 && t < 26) permanentCard(g, t);
        if (t >= A5 && t < A6) { act5(g, t); return; }
        if (t >= 190)          { lockup(g, t); return; }
        for (BandCue b : BANDS) if (t >= b.at() && t < b.until()) drawBand(g, t, b);
    }

    // ═══ ACT 0 — the CRT strikes, then the title ═════════════════════════════
    static void act0(Graphics2D g, double t) {
        g.setColor(GROUND); g.fillRect(0, 0, W, H);
        if (t < 5) {
            double strike = clamp((t - 1.0) / 0.35);
            if (t < 1.0) { g.setColor(Color.BLACK); g.fillRect(0, 0, W, H); return; }
            g.setColor(Color.BLACK); g.fillRect(0, 0, W, H);
            int h = (int) (2 + strike * (H - 2));
            g.setColor(strike < 0.15 ? Color.WHITE : GROUND);
            g.fillRect(0, H / 2 - h / 2, W, h);
            if (strike < 1) return;

            String[] lines = { "CANNONFORGE SYSTEMS", "RETROQUEST v0.9.0", "JAVA RUNTIME OK ...... 148 CLASSES",
                               "TILE REGISTRY ........ 192", "AUDIO ................ SYNTHESIZED", "READY." };
            g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 34));
            g.setColor(PHOS);
            for (int i = 0; i < lines.length; i++) {
                String s = typed(lines[i], t, 1.4 + i * 0.55, 0.022);
                g.drawString(s, 240, 330 + i * 52);
                if (!s.isEmpty() && s.length() < lines[i].length() && ((int) (t * 4)) % 2 == 0)
                    g.fillRect(240 + g.getFontMetrics().stringWidth(s) + 3, 330 + i * 52 - 26, 14, 30);
            }
        } else {
            centred(g, "RETROQUEST", new Font(Font.MONOSPACED, Font.BOLD, 92), PHOS, H / 2 - 30);
            centred(g, "THE UNBOUND — SHATTERED DREAMS OF AQUALON",
                    new Font(Font.MONOSPACED, Font.PLAIN, 30), DIM, H / 2 + 50);
            double roll = clamp((t - 5) / 0.8);                 // one scanline roll, top to bottom
            if (roll < 1) {
                int y = (int) (roll * H) - 40;
                g.setPaint(new GradientPaint(0, y, new Color(255, 255, 255, 0), 0, y + 40, new Color(255, 255, 255, 46)));
                g.fillRect(0, y, W, 80);
                g.setPaint(null);
            }
        }
    }

    // ═══ S07 — the amber warning, over the dimmed roll screen ════════════════
    static void permanentCard(Graphics2D g, double t) {
        double in = clamp((t - 21.6) / 0.3);
        g.setColor(new Color(0, 0, 0, (int) (170 * in)));
        g.fillRect(0, 0, W, H);
        Font f = new Font(Font.MONOSPACED, Font.BOLD, 44);
        String a = "3d6 PER ATTRIBUTE · THESE ROLLS ARE ";
        String b = "PERMANENT";
        g.setFont(f);
        FontMetrics fm = g.getFontMetrics();
        int wA = fm.stringWidth(a), wB = fm.stringWidth(b), x = (W - wA - wB) / 2, y = H / 2 + 16;
        g.setColor(new Color(AMBER.getRed(), AMBER.getGreen(), AMBER.getBlue(), (int) (255 * in)));
        g.drawString(a, x, y);
        // One-frame RGB split on PERMANENT — the glitch budget is three in the whole film.
        if (Math.abs(t - 24.2) < 1.0 / FPS) {
            g.setColor(new Color(0xDC, 0x37, 0x37)); g.drawString(b, x + wA - 6, y);
            g.setColor(CYAN);                        g.drawString(b, x + wA + 6, y);
        }
        g.setColor(new Color(AMBER.getRed(), AMBER.getGreen(), AMBER.getBlue(), (int) (255 * in)));
        g.drawString(b, x + wA, y);
    }

    // ═══ ACT 5 — the turn. Two lines on black, then RetroForge boots. ════════
    static void act5(Graphics2D g, double t) {
        if (t < 151) {
            g.setColor(Color.BLACK); g.fillRect(0, 0, W, H);
            centred(g, typed("THAT'S THE GAME.", t, A5 + 1.2, 0.06),
                    new Font(Font.MONOSPACED, Font.BOLD, 56), PHOS, H / 2 - 40);
            centred(g, typed("NOW BUILD YOUR OWN.", t, A5 + 4.3, 0.06),
                    new Font(Font.MONOSPACED, Font.BOLD, 56), CYAN, H / 2 + 60);
        } else if (t < 153.6) {
            // The editor is under this; the lock-up sits over it and burns off.
            double a = t < 151.5 ? clamp((t - 151) / 0.5) : 1 - clamp((t - 153.1) / 0.5);
            g.setColor(new Color(6, 8, 14, (int) (185 * a)));
            g.fillRect(0, 0, W, H);
            centred(g, "RETROFORGE", new Font(Font.MONOSPACED, Font.BOLD, 92),
                    new Color(CYAN.getRed(), CYAN.getGreen(), CYAN.getBlue(), (int) (255 * a)), H / 2 + 20);
        }
    }

    // ═══ ACT 7 — the lockup, then the CRT collapses ══════════════════════════
    static void lockup(Graphics2D g, double t) {
        double offT = END - 1.2;
        BufferedImage card = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D c = card.createGraphics();
        c.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        c.setColor(GROUND); c.fillRect(0, 0, W, H);
        centredOn(c, "RETROQUEST", new Font(Font.MONOSPACED, Font.BOLD, 92), PHOS, H / 2 - 90);
        Font big = new Font(Font.MONOSPACED, Font.BOLD, 92);
        c.setFont(big);
        FontMetrics fm = c.getFontMetrics();
        String plus = "+ ", rf = "RETROFORGE";
        int wTot = fm.stringWidth(plus + rf), x = (W - wTot) / 2;
        c.setColor(AMBER); c.drawString(plus, x, H / 2 + 30);
        c.setColor(CYAN);  c.drawString(rf, x + fm.stringWidth(plus), H / 2 + 30);
        centredOn(c, "Java · Swing · 32×32 sprites · zero audio files",
                  new Font(Font.MONOSPACED, Font.PLAIN, 20), DIM, H / 2 + 150);
        c.dispose();

        if (t < offT) { g.drawImage(card, 0, 0, null); return; }

        // Vertical collapse to a band, then a dot, then a long decay — with the luminance
        // boost that is what actually sells a CRT switching off.
        double p1 = clamp((t - offT) / 0.2), p2 = clamp((t - offT - 0.2) / 0.2), p3 = clamp((t - offT - 0.4) / 0.5);
        double sy = Math.max(0.004, 1 - ease(p1)), sx = 1 - ease(p2) * 0.999;
        g.setColor(Color.BLACK); g.fillRect(0, 0, W, H);
        Composite old = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) (1 - p3)));
        g.translate(W / 2.0, H / 2.0); g.scale(sx, sy); g.translate(-W / 2.0, -H / 2.0);
        g.drawImage(card, 0, 0, null);
        g.setTransform(new java.awt.geom.AffineTransform());
        g.setComposite(old);
        if (p1 > 0.1 && p3 < 1) {                       // the bloom as it squeezes
            int a = (int) (170 * (1 - p3) * Math.min(1, p1 * 1.4));
            g.setColor(new Color(255, 255, 255, Math.max(0, Math.min(255, a))));
            int bh = Math.max(2, (int) (H * sy * 0.06));
            g.fillRect((int) (W / 2 - W * sx / 2), H / 2 - bh / 2, (int) (W * sx), bh);
        }
    }

    // ═══ The band ════════════════════════════════════════════════════════════
    static void drawBand(Graphics2D g, double t, BandCue b) {
        int h = b.l2() != null ? BAND_H2 : BAND_H1;
        double rise = ease(clamp((t - b.at()) / 0.26));
        double out  = 1 - ease(clamp((t - (b.until() - 0.22)) / 0.22));
        int dy = (int) ((1 - rise * out) * h);
        int top = H - h + dy;

        g.setColor(new Color(0, 0, 0, 90));
        g.fillRect(0, top - 18, W, 18);                       // the drop shadow the CSS has
        g.setColor(PANEL);  g.fillRect(0, top, W, h);
        g.setColor(b.accent()); g.fillRect(0, top, W, 3);
        g.fillRect(96, top + 18, 6, h - 36);

        int tx = 96 + 6 + 28;
        g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 38));
        g.setColor(TEXT);
        String l1 = typed(b.l1(), t, b.at() + 0.12, 0.018);
        if (b.l2() == null) {
            g.drawString(l1, tx, top + h / 2 + 14);
        } else {
            g.drawString(l1, tx, top + 52);
            g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 27));
            g.setColor(b.accent());
            g.drawString(typed(b.l2(), t, b.at() + 0.12 + b.l1().length() * 0.018, 0.018), tx, top + 95);
        }
    }

    // ═══ helpers ═════════════════════════════════════════════════════════════
    static String typed(String s, double t, double t0, double per) {
        if (t < t0) return "";
        int n = (int) ((t - t0) / per);
        return n >= s.length() ? s : s.substring(0, Math.max(0, n));
    }
    static double clamp(double v) { return v < 0 ? 0 : v > 1 ? 1 : v; }
    static double ease(double p) { double q = 1 - clamp(p); return 1 - q * q * q; }

    static void centred(Graphics2D g, String s, Font f, Color c, int baseline) { centredOn(g, s, f, c, baseline); }
    static void centredOn(Graphics2D g, String s, Font f, Color c, int baseline) {
        if (s == null || s.isEmpty()) return;
        g.setFont(f); g.setColor(c);
        g.drawString(s, (W - g.getFontMetrics().stringWidth(s)) / 2, baseline);
    }
}

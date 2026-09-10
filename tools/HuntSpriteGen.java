import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Random;

/**
 * One-shot sprite generator for the seven overworld hunting grounds.
 *
 * <p>Each island gets one 32x32 tile that reads as "something lives here", drawn in that
 * island's palette so the spot looks native to the terrain it sits on rather than like a
 * marker dropped on top of it. They are placed on that island's flavour terrain — the tiles
 * that already carry {@code spawnWeights} — so the sprite has to sit against ash, cloud, moss,
 * kelp, dust or scorched earth without shouting.
 *
 * Run from the project root:
 *   javac tools/HuntSpriteGen.java -d tools/
 *   java -cp tools HuntSpriteGen
 */
public class HuntSpriteGen {

    static Random rng = new Random(0xF00D);

    static final String OW = "src/main/resources/tiles/overworld/";

    public static void main(String[] args) throws Exception {
        save(snareLine(),     OW + "hunt_snare_line.png");
        save(emberVent(),     OW + "hunt_ember_vent.png");
        save(skyShoal(),      OW + "hunt_sky_shoal.png");
        save(sporeLure(),     OW + "hunt_spore_lure.png");
        save(deepLine(),      OW + "hunt_deep_line.png");
        save(rememberedFire(),OW + "hunt_remembered_fire.png");
        save(rationCache(),   OW + "hunt_ration_cache.png");
        System.out.println("Hunting-ground sprites generated: 7 files.");
    }

    static void save(BufferedImage img, String path) throws Exception {
        File f = new File(path);
        f.getParentFile().mkdirs();
        ImageIO.write(img, "PNG", f);
        System.out.println("  wrote " + path);
    }

    private static Graphics2D open(BufferedImage img, Color base) {
        Graphics2D g = img.createGraphics();
        g.setColor(base);
        g.fillRect(0, 0, 32, 32);
        return g;
    }

    /** Speckle for ground texture. */
    private static void speckle(Graphics2D g, Color c, int n, int seed) {
        Random r = new Random(seed);
        g.setColor(c);
        for (int i = 0; i < n; i++) g.fillRect(r.nextInt(32), r.nextInt(32), 1, 1);
    }

    // ── Island 1 · Lirandel — a snare line strung across a forest trail ───────
    static BufferedImage snareLine() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = open(img, new Color(20, 34, 22));
        speckle(g, new Color(28, 46, 30), 90, 11);

        // Trodden path running north-south
        g.setColor(new Color(46, 40, 28));
        for (int y = 0; y < 32; y++) {
            int w = 7 + (int)(2 * Math.sin(y * Math.PI / 9.0));
            g.fillRect(12 + (int)(1.5 * Math.sin(y / 5.0)), y, w, 1);
        }

        // Two bent saplings with silver snare loops between them
        g.setColor(new Color(70, 58, 36));
        for (int y = 6; y < 24; y++) { g.fillRect(7 + (y - 6) / 6, y, 2, 1); g.fillRect(24 - (y - 6) / 6, y, 2, 1); }
        g.setColor(new Color(186, 200, 220));
        g.drawLine(8, 7, 25, 9);
        g.drawOval(13, 9, 7, 6);
        g.drawOval(15, 16, 5, 4);

        // Moonlight glints on the wire
        g.setColor(new Color(226, 236, 255));
        g.fillRect(16, 9, 1, 1);
        g.fillRect(19, 12, 1, 1);
        g.fillRect(17, 17, 1, 1);
        g.dispose();
        return img;
    }

    // ── Island 2 · Pyralis — a cracked vent field cinder-crabs shelter in ─────
    static BufferedImage emberVent() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = open(img, new Color(34, 24, 20));
        speckle(g, new Color(48, 34, 28), 110, 22);

        // Basalt plates with dark seams
        g.setColor(new Color(24, 17, 15));
        g.drawLine(0, 11, 31, 8);
        g.drawLine(0, 23, 31, 26);
        g.drawLine(13, 0, 9, 31);

        // Three vents, glowing from the inside out
        int[][] vents = { {8, 6, 5}, {21, 15, 6}, {12, 24, 4} };
        for (int[] v : vents) {
            int x = v[0], y = v[1], r = v[2];
            g.setColor(new Color(90, 40, 20));
            g.fillOval(x - r / 2, y - r / 2, r, r);
            g.setColor(new Color(190, 80, 25));
            g.fillOval(x - r / 2 + 1, y - r / 2 + 1, Math.max(1, r - 2), Math.max(1, r - 2));
            g.setColor(new Color(255, 178, 70));
            g.fillRect(x, y, 1, 1);
        }
        // Heat shimmer above the largest vent
        g.setColor(new Color(255, 140, 50, 90));
        g.drawLine(21, 10, 22, 7);
        g.drawLine(20, 11, 19, 8);
        g.dispose();
        return img;
    }

    // ── Island 3 · Zephyrion — a shoal of skyfish drifting on a current ──────
    static BufferedImage skyShoal() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = open(img, new Color(120, 150, 190));
        // Banded sky
        for (int y = 0; y < 32; y++) {
            int v = 150 + (int)(28 * Math.sin(y * Math.PI / 16.0));
            g.setColor(new Color(Math.min(255, v - 20), Math.min(255, v), Math.min(255, v + 30)));
            g.fillRect(0, y, 32, 1);
        }
        // Wind streaks
        g.setColor(new Color(226, 238, 255, 140));
        g.drawLine(2, 9, 15, 7);
        g.drawLine(17, 20, 30, 18);
        g.drawLine(6, 26, 20, 25);

        // Skyfish: little pale darts with a bright eye
        int[][] fish = { {8, 12}, {14, 10}, {19, 14}, {12, 18}, {23, 9} };
        for (int[] f : fish) {
            g.setColor(new Color(240, 248, 255));
            g.fillRect(f[0], f[1], 4, 2);
            g.fillRect(f[0] - 1, f[1] + 1, 1, 1);   // tail
            g.setColor(new Color(90, 120, 170));
            g.fillRect(f[0] + 3, f[1], 1, 1);       // eye
        }
        g.dispose();
        return img;
    }

    // ── Island 4 · Sylvandar — a lure of glowing spore-caps in the moss ──────
    static BufferedImage sporeLure() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = open(img, new Color(18, 34, 22));
        speckle(g, new Color(26, 52, 30), 120, 33);

        // Root mat
        g.setColor(new Color(40, 32, 22));
        for (int i = 0; i < 5; i++) {
            int y = 4 + i * 6;
            for (int x = 0; x < 32; x++) g.fillRect(x, y + (int)(2 * Math.sin(x / 4.0)), 1, 1);
        }

        // Fungal caps, four of them, each glowing a different amount
        int[][] caps = { {7, 20, 5}, {14, 13, 6}, {22, 22, 5}, {25, 11, 4} };
        int[] glow = { 150, 235, 120, 190 };
        for (int i = 0; i < caps.length; i++) {
            int x = caps[i][0], y = caps[i][1], w = caps[i][2];
            g.setColor(new Color(60, 70, 55));
            g.fillRect(x + w / 2 - 1, y, 2, 5);                    // stalk
            g.setColor(new Color(70, glow[i], 120));
            g.fillOval(x - w / 2, y - 3, w * 2, w);                 // cap
            g.setColor(new Color(180, 255, 210, 200));
            g.fillRect(x + w / 2 - 1, y - 2, 1, 1);                 // highlight
        }
        // Drifting motes
        g.setColor(new Color(170, 255, 200, 130));
        for (int i = 0; i < 8; i++) g.fillRect(rng.nextInt(32), rng.nextInt(32), 1, 1);
        g.dispose();
        return img;
    }

    // ── Island 5 · Thalorax — a baited deep line over a trench mouth ─────────
    static BufferedImage deepLine() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = open(img, new Color(6, 14, 26));
        // Trench mouth: darker the deeper in
        for (int i = 0; i < 7; i++) {
            g.setColor(new Color(6 - Math.min(6, i), 12 - Math.min(11, i), 24 - Math.min(20, i * 3)));
            g.fillOval(6 + i, 12 + i / 2, 20 - i * 2, 16 - i);
        }
        // Sediment lip
        g.setColor(new Color(22, 38, 52));
        g.drawOval(5, 11, 22, 18);
        speckle(g, new Color(30, 50, 66), 40, 44);

        // The line, dropping in from above, with a lure at the end
        g.setColor(new Color(150, 180, 200));
        for (int y = 0; y < 17; y++) g.fillRect(16 + (int)(1.5 * Math.sin(y / 4.0)), y, 1, 1);
        g.setColor(new Color(120, 230, 255));
        g.fillOval(14, 16, 5, 5);
        g.setColor(new Color(220, 250, 255));
        g.fillRect(16, 18, 1, 1);
        g.dispose();
        return img;
    }

    // ── Island 6 · Umbryn — a cold cook-fire that is only remembered ─────────
    static BufferedImage rememberedFire() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = open(img, new Color(22, 22, 28));
        speckle(g, new Color(32, 32, 40), 100, 55);

        // Stone ring, drawn as if half-erased
        g.setColor(new Color(70, 72, 84));
        int[][] stones = { {10, 20}, {14, 22}, {19, 22}, {23, 19}, {21, 15}, {12, 15} };
        for (int i = 0; i < stones.length; i++) {
            g.setColor(new Color(70, 72, 84, i % 2 == 0 ? 235 : 110));
            g.fillOval(stones[i][0], stones[i][1], 4, 3);
        }

        // The fire itself: present as an outline, absent as a fill
        g.setColor(new Color(150, 160, 190, 130));
        g.drawLine(16, 19, 14, 13);
        g.drawLine(16, 19, 18, 12);
        g.drawLine(16, 19, 16, 10);
        g.setColor(new Color(200, 214, 245, 90));
        g.drawOval(13, 9, 7, 10);

        // Ghost-silver embers rising where heat would be
        g.setColor(new Color(215, 228, 255, 170));
        g.fillRect(15, 7, 1, 1);
        g.fillRect(18, 5, 1, 1);
        g.fillRect(13, 4, 1, 1);
        g.dispose();
        return img;
    }

    // ── Island 7 · Bellorak — a supply cache beside a trench line ────────────
    static BufferedImage rationCache() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = open(img, new Color(38, 30, 22));
        speckle(g, new Color(28, 22, 16), 120, 66);

        // Trench cut across the tile
        g.setColor(new Color(20, 16, 12));
        g.fillRect(0, 4, 32, 6);
        g.setColor(new Color(52, 42, 30));
        g.fillRect(0, 3, 32, 1);
        g.fillRect(0, 10, 32, 1);

        // Crates, stacked and strapped
        int[][] crates = { {8, 17, 9, 8}, {18, 19, 7, 6}, {12, 12, 6, 5} };
        for (int[] c : crates) {
            g.setColor(new Color(96, 72, 40));
            g.fillRect(c[0], c[1], c[2], c[3]);
            g.setColor(new Color(64, 48, 26));
            g.drawRect(c[0], c[1], c[2] - 1, c[3] - 1);
            g.setColor(new Color(126, 98, 56));
            g.drawLine(c[0] + 1, c[1] + 1, c[0] + c[2] - 2, c[1] + 1);
            g.setColor(new Color(70, 70, 76));                       // iron strap
            g.fillRect(c[0], c[1] + c[3] / 2, c[2], 1);
        }
        // A dropped tin catching the light
        g.setColor(new Color(150, 152, 158));
        g.fillOval(26, 27, 4, 3);
        g.setColor(new Color(205, 208, 214));
        g.fillRect(27, 27, 1, 1);
        g.dispose();
        return img;
    }
}

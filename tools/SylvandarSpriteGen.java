import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Random;

/**
 * One-shot sprite generator for Island 4 (Sylvandar / The Verdant Mangroves).
 * Generates 32x32 PNG sprites for forest overworld, town, and monster tiles.
 *
 * Run from the project root:
 *   javac tools/SylvandarSpriteGen.java -d tools/
 *   java -cp tools SylvandarSpriteGen
 */
public class SylvandarSpriteGen {

    static Random rng = new Random(44);

    static final String OW  = "src/main/resources/tiles/overworld/";
    static final String TW  = "src/main/resources/tiles/town/";
    static final String MON = "src/main/resources/tiles/monsters/";

    public static void main(String[] args) throws Exception {
        // Overworld tiles
        save(denseForest(),       OW + "dense_forest.png");
        save(giantTree(),         OW + "giant_tree.png");
        save(mangroveSwamp(),     OW + "mangrove_swamp.png");
        save(mossGround(),        OW + "moss_ground.png");
        save(bioFungi(),          OW + "bioluminescent_fungi.png");
        save(ancientRoots(),      OW + "ancient_roots.png");
        save(thornHedge(),        OW + "thorn_hedge.png");
        save(petrifiedAmber(),    OW + "petrified_amber.png");

        // Town tiles
        save(rootFloor(),         TW + "root_floor.png");
        save(barkWall(),          TW + "bark_wall.png");
        save(vineCurtain(),       TW + "vine_curtain.png");
        save(amberCrystal(),      TW + "amber_crystal.png");
        save(fungalLamp(),        TW + "fungal_lamp.png");

        // Monster sprites
        save(vineStrangler(),     MON + "vine_strangler.png");
        save(sporePhantom(),      MON + "spore_phantom.png");
        save(rootGolem(),         MON + "root_golem.png");
        save(canopyStalker(),     MON + "canopy_stalker.png");
        save(blightMother(),      MON + "blight_mother.png");

        System.out.println("Sylvandar sprites generated: 18 files.");
    }

    static void save(BufferedImage img, String path) throws Exception {
        File f = new File(path);
        f.getParentFile().mkdirs();
        ImageIO.write(img, "PNG", f);
        System.out.println("  wrote " + path);
    }

    // ── OVERWORLD TILES ─────────────────────────────────────────────────────

    /** Dense canopy — layered green leaves with dark gaps. */
    static BufferedImage denseForest() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        Color base  = new Color(15, 45, 20);
        Color leaf1 = new Color(25, 70, 30);
        Color leaf2 = new Color(35, 90, 40);
        Color leaf3 = new Color(20, 60, 25);
        Color dark  = new Color(8, 25, 10);
        Color high  = new Color(50, 110, 50);

        g.setColor(base); g.fillRect(0, 0, 32, 32);

        // Canopy blobs
        rng.setSeed(401);
        for (int i = 0; i < 18; i++) {
            int x = rng.nextInt(28), y = rng.nextInt(28);
            int r = 3 + rng.nextInt(5);
            g.setColor(i % 3 == 0 ? leaf1 : i % 3 == 1 ? leaf2 : leaf3);
            g.fillOval(x, y, r, r);
        }

        // Dark gaps between canopy
        rng.setSeed(402);
        for (int i = 0; i < 40; i++) {
            g.setColor(dark);
            g.fillRect(rng.nextInt(32), rng.nextInt(32), 1, 1);
        }

        // Highlights on top leaves
        rng.setSeed(403);
        for (int i = 0; i < 15; i++) {
            g.setColor(high);
            g.fillRect(rng.nextInt(32), rng.nextInt(32), 1, 1);
        }

        g.dispose(); return img;
    }

    /** Massive trunk — thick brown trunk with root flares, dark bark texture. */
    static BufferedImage giantTree() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        Color bg     = new Color(15, 45, 20);
        Color trunk  = new Color(65, 45, 25);
        Color barkD  = new Color(45, 30, 15);
        Color barkL  = new Color(85, 60, 35);
        Color root   = new Color(55, 38, 20);
        Color canopy = new Color(30, 80, 35);
        Color canopH = new Color(45, 100, 45);

        g.setColor(bg); g.fillRect(0, 0, 32, 32);

        // Canopy crown (top half)
        g.setColor(canopy); g.fillOval(2, 0, 28, 16);
        g.setColor(canopH); g.fillOval(6, 2, 20, 10);
        rng.setSeed(404);
        for (int i = 0; i < 20; i++) {
            g.setColor(rng.nextBoolean() ? canopy : canopH);
            g.fillOval(2 + rng.nextInt(26), rng.nextInt(14), 3, 3);
        }

        // Thick trunk
        g.setColor(trunk); g.fillRect(10, 12, 12, 16);
        g.setColor(barkD); g.drawLine(12, 12, 12, 31); g.drawLine(16, 12, 16, 31);
        g.drawLine(20, 12, 20, 31);
        g.setColor(barkL); g.drawLine(13, 12, 13, 31); g.drawLine(19, 12, 19, 31);

        // Bark texture
        rng.setSeed(405);
        for (int i = 0; i < 30; i++) {
            int x = 10 + rng.nextInt(12), y = 12 + rng.nextInt(18);
            g.setColor(rng.nextBoolean() ? barkD : barkL);
            g.fillRect(x, y, 1, 1);
        }

        // Root flares at base
        g.setColor(root);
        g.fillRect(7, 28, 4, 4);   g.fillRect(21, 28, 4, 4);
        g.fillRect(5, 30, 3, 2);   g.fillRect(24, 30, 3, 2);

        g.dispose(); return img;
    }

    /** Mangrove swamp — tangled roots over murky water. */
    static BufferedImage mangroveSwamp() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        Color water  = new Color(30, 50, 35);
        Color waterL = new Color(40, 65, 45);
        Color mud    = new Color(50, 40, 25);
        Color root   = new Color(60, 42, 22);
        Color rootD  = new Color(40, 28, 12);
        Color algae  = new Color(45, 75, 35);

        g.setColor(water); g.fillRect(0, 0, 32, 32);

        // Murky water variation
        rng.setSeed(406);
        for (int i = 0; i < 80; i++) {
            g.setColor(rng.nextBoolean() ? waterL : mud);
            g.fillRect(rng.nextInt(32), rng.nextInt(32), 1 + rng.nextInt(2), 1);
        }

        // Tangled roots crossing the tile
        g.setColor(root);
        g.drawLine(0, 10, 12, 14); g.drawLine(12, 14, 20, 8);
        g.drawLine(20, 8, 31, 12);
        g.drawLine(5, 22, 15, 26); g.drawLine(15, 26, 28, 20);
        g.setColor(rootD);
        g.drawLine(0, 11, 12, 15); g.drawLine(5, 23, 15, 27);

        // Algae patches
        rng.setSeed(407);
        for (int i = 0; i < 10; i++) {
            g.setColor(algae);
            g.fillOval(rng.nextInt(30), rng.nextInt(30), 2 + rng.nextInt(3), 2);
        }

        g.dispose(); return img;
    }

    /** Soft moss-covered ground — bright green with texture. */
    static BufferedImage mossGround() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        Color base  = new Color(40, 85, 35);
        Color light = new Color(55, 105, 45);
        Color dark  = new Color(28, 65, 22);
        Color stone = new Color(70, 75, 60);

        g.setColor(base); g.fillRect(0, 0, 32, 32);

        // Moss texture
        rng.setSeed(408);
        for (int i = 0; i < 120; i++) {
            g.setColor(rng.nextBoolean() ? light : dark);
            g.fillRect(rng.nextInt(32), rng.nextInt(32), 1, 1);
        }

        // Scattered small stones
        rng.setSeed(409);
        for (int i = 0; i < 5; i++) {
            g.setColor(stone);
            g.fillRect(rng.nextInt(30), rng.nextInt(30), 2, 1);
        }

        g.dispose(); return img;
    }

    /** Bioluminescent fungi — dark ground with glowing cyan-green mushrooms. */
    static BufferedImage bioFungi() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        Color base  = new Color(12, 30, 18);
        Color dark  = new Color(8, 20, 12);
        Color stem  = new Color(60, 75, 55);
        Color cap   = new Color(30, 180, 140);
        Color glow  = new Color(80, 240, 200);
        Color glowD = new Color(20, 120, 90);

        g.setColor(base); g.fillRect(0, 0, 32, 32);

        // Ground noise
        rng.setSeed(410);
        for (int i = 0; i < 60; i++) {
            g.setColor(dark);
            g.fillRect(rng.nextInt(32), rng.nextInt(32), 1, 1);
        }

        // Mushrooms at fixed positions
        int[][] shrooms = {{6, 10}, {18, 8}, {12, 20}, {24, 16}, {4, 26}, {22, 28}};
        for (int[] s : shrooms) {
            int sx = s[0], sy = s[1];
            // Stem
            g.setColor(stem);
            g.fillRect(sx, sy + 2, 2, 3);
            // Cap
            g.setColor(cap);
            g.fillOval(sx - 2, sy, 6, 4);
            // Glow dot
            g.setColor(glow);
            g.fillRect(sx, sy + 1, 2, 1);
        }

        // Ambient glow spots on ground
        rng.setSeed(411);
        for (int i = 0; i < 12; i++) {
            g.setColor(glowD);
            g.fillRect(rng.nextInt(32), rng.nextInt(32), 1, 1);
        }

        g.dispose(); return img;
    }

    /** Massive ancient roots — thick tangled brown roots, impassable. */
    static BufferedImage ancientRoots() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        Color bg    = new Color(15, 40, 18);
        Color root  = new Color(70, 48, 25);
        Color rootD = new Color(50, 32, 15);
        Color rootL = new Color(90, 65, 38);
        Color moss  = new Color(40, 80, 35);

        g.setColor(bg); g.fillRect(0, 0, 32, 32);

        // Thick roots weaving across
        g.setColor(root);
        g.setStroke(new BasicStroke(4));
        g.drawLine(0, 8, 16, 16); g.drawLine(16, 16, 31, 10);
        g.drawLine(4, 20, 20, 28); g.drawLine(20, 28, 31, 22);
        g.drawLine(0, 28, 10, 24);
        g.setStroke(new BasicStroke(1));

        // Root highlights and shadows
        g.setColor(rootL);
        g.drawLine(1, 7, 15, 15); g.drawLine(5, 19, 19, 27);
        g.setColor(rootD);
        g.drawLine(1, 10, 16, 18); g.drawLine(5, 22, 20, 30);

        // Moss on roots
        rng.setSeed(412);
        for (int i = 0; i < 15; i++) {
            g.setColor(moss);
            g.fillRect(rng.nextInt(30), rng.nextInt(30), 2, 1);
        }

        g.dispose(); return img;
    }

    /** Thorny hedge wall — dark green mass with red thorns. */
    static BufferedImage thornHedge() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        Color base  = new Color(20, 50, 15);
        Color leafD = new Color(15, 38, 10);
        Color leafL = new Color(30, 68, 25);
        Color thorn = new Color(140, 50, 30);
        Color thornL= new Color(180, 80, 50);

        g.setColor(base); g.fillRect(0, 0, 32, 32);

        // Dense leaf mass
        rng.setSeed(413);
        for (int i = 0; i < 25; i++) {
            g.setColor(rng.nextBoolean() ? leafD : leafL);
            g.fillOval(rng.nextInt(28), rng.nextInt(28), 3 + rng.nextInt(4), 3 + rng.nextInt(4));
        }

        // Thorns
        rng.setSeed(414);
        for (int i = 0; i < 12; i++) {
            int tx = rng.nextInt(30), ty = rng.nextInt(30);
            g.setColor(thorn);
            g.drawLine(tx, ty, tx + 2, ty - 2);
            g.setColor(thornL);
            g.fillRect(tx + 2, ty - 2, 1, 1);
        }

        g.dispose(); return img;
    }

    /** Petrified amber — golden-orange crystallized stone. */
    static BufferedImage petrifiedAmber() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        Color base  = new Color(160, 110, 30);
        Color dark  = new Color(120, 80, 15);
        Color light = new Color(200, 150, 50);
        Color glow  = new Color(240, 190, 80);
        Color crack = new Color(90, 60, 10);

        g.setColor(base); g.fillRect(0, 0, 32, 32);

        // Crystal facets
        g.setColor(dark);
        g.fillRect(0, 0, 16, 16); g.fillRect(16, 16, 16, 16);
        g.setColor(light);
        g.fillRect(16, 0, 16, 16); g.fillRect(0, 16, 16, 16);

        // Amber grain texture
        rng.setSeed(415);
        for (int i = 0; i < 80; i++) {
            g.setColor(rng.nextInt(3) == 0 ? glow : rng.nextBoolean() ? dark : light);
            g.fillRect(rng.nextInt(32), rng.nextInt(32), 1, 1);
        }

        // Crack lines
        g.setColor(crack);
        g.drawLine(5, 3, 14, 12); g.drawLine(14, 12, 10, 20);
        g.drawLine(20, 5, 26, 16); g.drawLine(26, 16, 22, 28);

        // Bright glow centers
        g.setColor(glow);
        g.fillRect(14, 11, 2, 2); g.fillRect(25, 15, 2, 2);

        g.dispose(); return img;
    }

    // ── TOWN TILES ──────────────────────────────────────────────────────────

    /** Root floor — woven living roots forming a flat surface. */
    static BufferedImage rootFloor() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        Color base  = new Color(60, 42, 22);
        Color rootL = new Color(80, 58, 32);
        Color rootD = new Color(42, 28, 12);
        Color gap   = new Color(20, 35, 15);

        g.setColor(base); g.fillRect(0, 0, 32, 32);

        // Woven root pattern (horizontal)
        for (int y = 0; y < 32; y += 6) {
            for (int x = 0; x < 32; x++) {
                int wave = (int)(1.5 * Math.sin(x * Math.PI / 10.0 + y));
                g.setColor(rootL);
                g.fillRect(x, y + wave, 1, 3);
                g.setColor(rootD);
                g.fillRect(x, y + wave + 3, 1, 1);
            }
        }

        // Gaps between roots
        rng.setSeed(416);
        for (int i = 0; i < 20; i++) {
            g.setColor(gap);
            g.fillRect(rng.nextInt(32), rng.nextInt(32), 1, 1);
        }

        g.dispose(); return img;
    }

    /** Bark wall — thick tree bark, not walkable. */
    static BufferedImage barkWall() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        Color base  = new Color(55, 38, 20);
        Color barkD = new Color(38, 25, 12);
        Color barkL = new Color(72, 52, 30);
        Color ridge = new Color(80, 58, 35);

        g.setColor(base); g.fillRect(0, 0, 32, 32);

        // Vertical bark ridges
        for (int x = 0; x < 32; x += 5) {
            g.setColor(ridge);
            g.fillRect(x, 0, 2, 32);
            g.setColor(barkD);
            g.fillRect(x + 2, 0, 1, 32);
        }

        // Bark texture noise
        rng.setSeed(417);
        for (int i = 0; i < 100; i++) {
            g.setColor(rng.nextBoolean() ? barkD : barkL);
            g.fillRect(rng.nextInt(32), rng.nextInt(32), 1, 1);
        }

        // Horizontal bark lines
        g.setColor(barkD);
        g.drawLine(0, 8, 31, 8); g.drawLine(0, 20, 31, 20);

        g.dispose(); return img;
    }

    /** Vine curtain — hanging green vines over dark background. */
    static BufferedImage vineCurtain() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        Color bg    = new Color(20, 35, 18);
        Color vineD = new Color(30, 65, 25);
        Color vineL = new Color(50, 95, 40);
        Color leaf  = new Color(40, 80, 30);

        g.setColor(bg); g.fillRect(0, 0, 32, 32);

        // Hanging vines
        for (int vx = 3; vx < 32; vx += 5) {
            g.setColor(vineD);
            for (int y = 0; y < 32; y++) {
                int xoff = (int)(1.5 * Math.sin(y * Math.PI / 8.0));
                g.fillRect(vx + xoff, y, 1, 1);
            }
            g.setColor(vineL);
            for (int y = 0; y < 32; y++) {
                int xoff = (int)(1.5 * Math.sin(y * Math.PI / 8.0));
                g.fillRect(vx + xoff + 1, y, 1, 1);
            }
            // Small leaves
            g.setColor(leaf);
            g.fillOval(vx - 1, 8, 3, 2);
            g.fillOval(vx, 18, 3, 2);
            g.fillOval(vx - 1, 28, 3, 2);
        }

        g.dispose(); return img;
    }

    /** Amber crystal — glowing golden crystal formation. */
    static BufferedImage amberCrystal() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        Color bg    = new Color(40, 30, 15);
        Color amber = new Color(180, 130, 40);
        Color amberL= new Color(220, 170, 60);
        Color glow  = new Color(255, 210, 90);
        Color base  = new Color(100, 70, 30);

        g.setColor(bg); g.fillRect(0, 0, 32, 32);

        // Crystal base
        g.setColor(base); g.fillRect(8, 26, 16, 6);

        // Main crystal (tall)
        int[] cx = {12, 16, 20, 18, 14};
        int[] cy = {26, 4, 26, 26, 26};
        g.setColor(amber); g.fillPolygon(cx, cy, 5);

        // Left crystal
        int[] lx = {8, 11, 14, 12, 9};
        int[] ly = {26, 10, 26, 26, 26};
        g.setColor(amber); g.fillPolygon(lx, ly, 5);

        // Right crystal
        int[] rx = {18, 21, 24, 22, 19};
        int[] ry = {26, 12, 26, 26, 26};
        g.setColor(amber); g.fillPolygon(rx, ry, 5);

        // Highlights
        g.setColor(amberL);
        g.drawLine(14, 26, 16, 4); g.drawLine(9, 26, 11, 10); g.drawLine(19, 26, 21, 12);

        // Glow points
        g.setColor(glow);
        g.fillRect(15, 8, 2, 2); g.fillRect(10, 14, 1, 1); g.fillRect(20, 16, 1, 1);

        g.dispose(); return img;
    }

    /** Fungal lamp — small bioluminescent mushroom on a stand. */
    static BufferedImage fungalLamp() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        Color bg   = new Color(35, 50, 30);
        Color stem = new Color(70, 60, 45);
        Color cap  = new Color(40, 190, 150);
        Color glow = new Color(100, 255, 220);
        Color glowA= new Color(40, 150, 120, 80);

        g.setColor(bg); g.fillRect(0, 0, 32, 32);

        // Glow aura
        g.setColor(glowA);
        g.fillOval(6, 4, 20, 18);

        // Stem
        g.setColor(stem);
        g.fillRect(14, 16, 4, 14);

        // Mushroom cap
        g.setColor(cap);
        g.fillOval(8, 6, 16, 12);

        // Glow highlight
        g.setColor(glow);
        g.fillOval(12, 9, 8, 6);
        g.fillRect(15, 10, 2, 2);

        // Base
        g.setColor(stem);
        g.fillRect(12, 29, 8, 3);

        g.dispose(); return img;
    }

    // ── MONSTER SPRITES ─────────────────────────────────────────────────────

    /** Vine Strangler — writhing mass of vines with glowing eyes. */
    static BufferedImage vineStrangler() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        Color vineD = new Color(25, 55, 18);
        Color vineM = new Color(40, 80, 30);
        Color vineL = new Color(55, 100, 40);
        Color thorn = new Color(120, 45, 20);
        Color eye   = new Color(200, 255, 50);

        // Vine body mass
        g.setColor(vineD); g.fillOval(4, 6, 24, 24);
        g.setColor(vineM); g.fillOval(7, 9, 18, 18);

        // Writhing tendrils
        g.setColor(vineD);
        g.setStroke(new BasicStroke(2));
        g.drawLine(4, 14, 0, 6);   g.drawLine(0, 6, 2, 2);
        g.drawLine(27, 12, 31, 4);  g.drawLine(31, 4, 29, 0);
        g.drawLine(8, 28, 4, 31);
        g.drawLine(24, 28, 28, 31);
        g.drawLine(16, 6, 14, 0);
        g.setStroke(new BasicStroke(1));

        // Vine texture
        rng.setSeed(420);
        for (int i = 0; i < 30; i++) {
            g.setColor(rng.nextBoolean() ? vineL : vineD);
            g.fillRect(6 + rng.nextInt(20), 8 + rng.nextInt(18), 1, 2);
        }

        // Thorns on tendrils
        g.setColor(thorn);
        g.fillRect(1, 4, 1, 2); g.fillRect(30, 2, 1, 2);
        g.fillRect(5, 30, 1, 1); g.fillRect(27, 30, 1, 1);

        // Glowing eyes
        g.setColor(eye);
        g.fillRect(11, 15, 3, 3); g.fillRect(19, 15, 3, 3);
        g.setColor(new Color(255, 255, 100));
        g.fillRect(12, 16, 1, 1); g.fillRect(20, 16, 1, 1);

        g.dispose(); return img;
    }

    /** Spore Phantom — ghostly translucent figure trailing spore clouds. */
    static BufferedImage sporePhantom() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        Color ghostD = new Color(40, 80, 50, 160);
        Color ghostM = new Color(60, 120, 70, 140);
        Color ghostL = new Color(80, 160, 90, 120);
        Color spore  = new Color(180, 220, 100, 180);
        Color eye    = new Color(220, 40, 40);

        // Ghostly body (semi-transparent)
        g.setColor(ghostD); g.fillOval(8, 4, 16, 20);
        g.setColor(ghostM); g.fillOval(10, 6, 12, 14);
        g.setColor(ghostL); g.fillOval(12, 8, 8, 8);

        // Trailing bottom (wispy)
        g.setColor(ghostD);
        g.fillRect(10, 22, 3, 6); g.fillRect(15, 22, 3, 6);
        g.fillRect(19, 22, 3, 6);
        g.setColor(ghostM);
        g.fillRect(11, 24, 2, 5); g.fillRect(16, 25, 2, 4);

        // Eyes (red, menacing)
        g.setColor(eye);
        g.fillRect(12, 12, 2, 2); g.fillRect(18, 12, 2, 2);

        // Spore cloud particles
        rng.setSeed(421);
        for (int i = 0; i < 20; i++) {
            g.setColor(spore);
            int sx = rng.nextInt(32), sy = rng.nextInt(32);
            g.fillOval(sx, sy, 2, 2);
        }

        g.dispose(); return img;
    }

    /** Root Golem — hulking figure made of twisted wood and roots. */
    static BufferedImage rootGolem() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        Color wood  = new Color(65, 45, 22);
        Color woodL = new Color(85, 62, 32);
        Color woodD = new Color(45, 30, 12);
        Color moss  = new Color(40, 80, 30);
        Color eye   = new Color(100, 255, 80);

        // Head (blocky wooden)
        g.setColor(wood); g.fillRect(11, 1, 10, 8);
        g.setColor(woodL); g.drawLine(11, 1, 20, 1);

        // Eyes
        g.setColor(eye); g.fillRect(13, 3, 2, 3); g.fillRect(17, 3, 2, 3);

        // Neck (roots)
        g.setColor(woodD); g.fillRect(14, 9, 4, 2);

        // Torso (massive)
        g.setColor(wood); g.fillRect(6, 11, 20, 11);
        g.setColor(woodL); g.drawLine(6, 11, 25, 11); g.drawLine(6, 11, 6, 21);

        // Bark texture on torso
        rng.setSeed(422);
        for (int i = 0; i < 30; i++) {
            g.setColor(rng.nextBoolean() ? woodD : woodL);
            g.fillRect(7 + rng.nextInt(18), 12 + rng.nextInt(9), 1, 2);
        }

        // Moss patches
        g.setColor(moss);
        g.fillRect(8, 14, 3, 2); g.fillRect(20, 16, 3, 2);

        // Arms (thick root-like)
        g.setColor(wood);
        g.fillRect(1, 12, 5, 10); g.fillRect(26, 12, 5, 10);
        g.setColor(woodD);
        g.drawLine(1, 21, 5, 21); g.drawLine(26, 21, 30, 21);

        // Fists
        g.setColor(wood);
        g.fillRect(1, 22, 6, 4); g.fillRect(25, 22, 6, 4);

        // Legs
        g.setColor(wood);
        g.fillRect(8, 22, 6, 8); g.fillRect(18, 22, 6, 8);
        g.setColor(woodD);
        g.drawLine(8, 29, 13, 29); g.drawLine(18, 29, 23, 29);

        // Root feet
        g.setColor(woodD);
        g.fillRect(6, 29, 3, 3); g.fillRect(23, 29, 3, 3);

        g.dispose(); return img;
    }

    /** Canopy Stalker — spider-like creature with long legs, hangs from above. */
    static BufferedImage canopyStalker() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        Color body  = new Color(40, 55, 30);
        Color bodyL = new Color(55, 75, 40);
        Color leg   = new Color(50, 65, 35);
        Color legD  = new Color(30, 40, 20);
        Color eye   = new Color(255, 200, 0);
        Color fang  = new Color(180, 160, 120);

        // Thread from above
        g.setColor(new Color(120, 120, 100));
        g.drawLine(16, 0, 16, 8);

        // Body (oval)
        g.setColor(body); g.fillOval(9, 10, 14, 10);
        g.setColor(bodyL); g.fillOval(11, 11, 10, 7);

        // Abdomen
        g.setColor(body); g.fillOval(10, 18, 12, 8);

        // Pattern on abdomen
        g.setColor(bodyL);
        g.fillRect(14, 20, 4, 2); g.fillRect(15, 23, 2, 2);

        // 8 legs (4 each side)
        g.setColor(leg);
        // Left legs
        g.drawLine(9, 12, 2, 6);  g.drawLine(2, 6, 0, 8);
        g.drawLine(9, 15, 3, 14); g.drawLine(3, 14, 0, 18);
        g.drawLine(10, 18, 4, 22); g.drawLine(4, 22, 2, 28);
        g.drawLine(10, 20, 5, 26); g.drawLine(5, 26, 4, 31);
        // Right legs
        g.drawLine(23, 12, 30, 6);  g.drawLine(30, 6, 31, 8);
        g.drawLine(23, 15, 29, 14); g.drawLine(29, 14, 31, 18);
        g.drawLine(22, 18, 28, 22); g.drawLine(28, 22, 30, 28);
        g.drawLine(22, 20, 27, 26); g.drawLine(27, 26, 28, 31);

        // Eyes (cluster)
        g.setColor(eye);
        g.fillRect(12, 12, 2, 2); g.fillRect(14, 11, 1, 1);
        g.fillRect(18, 12, 2, 2); g.fillRect(17, 11, 1, 1);

        // Fangs
        g.setColor(fang);
        g.drawLine(13, 19, 12, 22); g.drawLine(19, 19, 20, 22);

        g.dispose(); return img;
    }

    /** Blight Mother — large corrupted tree-creature, boss-tier. */
    static BufferedImage blightMother() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        Color body  = new Color(50, 35, 18);
        Color bodyL = new Color(70, 50, 28);
        Color rot   = new Color(80, 100, 30);
        Color rotD  = new Color(60, 75, 20);
        Color eye   = new Color(255, 80, 40);
        Color glow  = new Color(120, 200, 40);
        Color vine  = new Color(35, 60, 20);

        // Massive trunk body
        g.setColor(body); g.fillRect(6, 4, 20, 24);
        g.setColor(bodyL); g.drawLine(6, 4, 25, 4);

        // Branch arms (reaching upward)
        g.setColor(body);
        g.setStroke(new BasicStroke(3));
        g.drawLine(6, 10, 0, 2);  g.drawLine(0, 2, 2, 0);
        g.drawLine(25, 10, 31, 2); g.drawLine(31, 2, 29, 0);
        g.setStroke(new BasicStroke(1));

        // Rot/blight patches
        g.setColor(rot);
        g.fillOval(10, 8, 6, 4); g.fillOval(18, 14, 5, 5);
        g.fillOval(8, 20, 7, 4);
        g.setColor(rotD);
        g.fillOval(11, 9, 4, 2); g.fillOval(19, 15, 3, 3);

        // Glowing spore vents
        g.setColor(glow);
        g.fillRect(12, 10, 2, 1); g.fillRect(20, 16, 1, 1);
        g.fillRect(10, 22, 1, 1);

        // Face — gaping mouth
        g.setColor(new Color(15, 10, 5));
        g.fillOval(11, 12, 10, 6);
        g.setColor(rot);
        g.drawLine(12, 15, 14, 14); g.drawLine(18, 14, 20, 15); // teeth-like

        // Eyes (burning)
        g.setColor(eye);
        g.fillRect(12, 8, 3, 2); g.fillRect(19, 8, 3, 2);
        g.setColor(new Color(255, 200, 80));
        g.fillRect(13, 8, 1, 1); g.fillRect(20, 8, 1, 1);

        // Trailing vines at base
        g.setColor(vine);
        g.fillRect(8, 27, 2, 5); g.fillRect(14, 28, 2, 4);
        g.fillRect(20, 27, 2, 5); g.fillRect(26, 28, 2, 4);

        // Root feet
        g.setColor(body);
        g.fillRect(4, 28, 5, 4); g.fillRect(23, 28, 5, 4);

        g.dispose(); return img;
    }
}

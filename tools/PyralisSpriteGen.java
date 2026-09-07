import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Random;

/**
 * One-shot sprite generator for Island 2 (Pyralis / The Forged Isles).
 * Generates 32x32 PNG sprites for volcanic overworld, town, and monster tiles.
 *
 * Run from the project root:
 *   javac tools/PyralisSpriteGen.java -d tools/
 *   java -cp tools PyralisSpriteGen
 */
public class PyralisSpriteGen {

    static Random rng = new Random(42);

    static final String OW  = "src/main/resources/tiles/overworld/";
    static final String TW  = "src/main/resources/tiles/town/";
    static final String MON = "src/main/resources/tiles/monsters/";

    public static void main(String[] args) throws Exception {
        // Overworld tiles
        save(volcanicRock(),    OW + "volcanic_rock.png");
        save(ashPlains(),       OW + "ash_plains.png");
        save(lavaFlow(),        OW + "lava_flow.png");
        save(volcanicMountain(),OW + "volcanic_mountain.png");
        save(slagHeap(),        OW + "slag_heap.png");
        save(volcanicSand(),    OW + "volcanic_sand.png");
        save(ashTree(),         OW + "ash_tree.png");

        // Town tiles
        save(obsidianFloor(),   TW + "obsidian_floor.png");
        save(forgeFloor(),      TW + "forge_floor.png");
        save(emberGrate(),      TW + "ember_grate.png");
        save(lavaChannel(),     TW + "lava_channel.png");

        // Monster sprites
        save(slagBeast(),       MON + "slag_beast.png");
        save(emberSprite(),     MON + "ember_sprite.png");
        save(forgeGolem(),      MON + "forge_golem.png");
        save(ashWraith(),       MON + "ash_wraith.png");
        save(lavaSerpent(),     MON + "lava_serpent.png");

        System.out.println("Pyralis sprites generated: 16 files.");
    }

    static void save(BufferedImage img, String path) throws Exception {
        File f = new File(path);
        f.getParentFile().mkdirs();
        ImageIO.write(img, "PNG", f);
        System.out.println("  wrote " + path);
    }

    // ── OVERWORLD TILES ─────────────────────────────────────────────────────

    /** Dark grey cracked basalt with subtle orange veins. */
    static BufferedImage volcanicRock() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        Color base  = new Color(55, 48, 42);
        Color dark  = new Color(40, 35, 30);
        Color light = new Color(70, 62, 55);
        Color vein  = new Color(180, 80, 20);
        Color veinD = new Color(140, 55, 10);

        g.setColor(base); g.fillRect(0, 0, 32, 32);

        // Rock texture noise
        rng.setSeed(301);
        for (int i = 0; i < 150; i++) {
            g.setColor(rng.nextBoolean() ? dark : light);
            g.fillRect(rng.nextInt(32), rng.nextInt(32), 1, 1);
        }

        // Cracks (dark lines)
        g.setColor(dark);
        g.drawLine(5, 0, 12, 10); g.drawLine(12, 10, 8, 20); g.drawLine(8, 20, 14, 31);
        g.drawLine(20, 0, 22, 8); g.drawLine(22, 8, 28, 18); g.drawLine(28, 18, 24, 31);

        // Orange veins along some cracks
        rng.setSeed(302);
        for (int i = 0; i < 20; i++) {
            int x = 3 + rng.nextInt(26), y = rng.nextInt(32);
            g.setColor(rng.nextBoolean() ? vein : veinD);
            g.fillRect(x, y, 1, 1);
        }

        g.dispose(); return img;
    }

    /** Grey-brown scorched terrain with ash particle noise. */
    static BufferedImage ashPlains() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        Color base  = new Color(95, 82, 68);
        Color light = new Color(115, 100, 82);
        Color dark  = new Color(75, 65, 52);
        Color ash   = new Color(140, 130, 118);
        Color soot  = new Color(55, 48, 38);

        g.setColor(base); g.fillRect(0, 0, 32, 32);

        // Ground texture
        rng.setSeed(303);
        for (int i = 0; i < 120; i++) {
            g.setColor(rng.nextBoolean() ? light : dark);
            g.fillRect(rng.nextInt(32), rng.nextInt(32), 1+rng.nextInt(2), 1);
        }

        // Ash particles (lighter spots)
        rng.setSeed(304);
        for (int i = 0; i < 30; i++) {
            g.setColor(rng.nextBoolean() ? ash : soot);
            g.fillRect(rng.nextInt(32), rng.nextInt(32), 1, 1);
        }

        // Scorch marks
        g.setColor(soot);
        g.drawLine(8, 14, 14, 16); g.drawLine(20, 6, 25, 9);

        g.dispose(); return img;
    }

    /** Orange/red flowing lava with bright yellow highlights. */
    static BufferedImage lavaFlow() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        Color deep = new Color(160, 30, 5);
        Color mid  = new Color(210, 70, 10);
        Color hot  = new Color(245, 140, 20);
        Color glow = new Color(255, 220, 80);
        Color crust= new Color(60, 25, 10);

        g.setColor(deep); g.fillRect(0, 0, 32, 32);

        // Lava blobs (warm mid-tone)
        rng.setSeed(305);
        for (int i = 0; i < 60; i++) {
            g.setColor(mid);
            int x = rng.nextInt(30), y = rng.nextInt(30);
            g.fillOval(x, y, 2 + rng.nextInt(3), 2 + rng.nextInt(2));
        }

        // Hot streaks
        for (int dy : new int[]{6, 15, 24}) {
            for (int x = 0; x < 32; x++) {
                int y = dy + (int)(2.0 * Math.sin(x * Math.PI * 2 / 14.0));
                if (y >= 0 && y < 32) {
                    g.setColor(hot); g.fillRect(x, y, 1, 1);
                }
            }
        }

        // Bright glow dots
        rng.setSeed(306);
        for (int i = 0; i < 15; i++) {
            g.setColor(glow);
            g.fillRect(rng.nextInt(32), rng.nextInt(32), 1, 1);
        }

        // Dark cooled crust patches
        for (int[] p : new int[][]{{2,2,5,3},{22,10,4,3},{8,22,6,3},{26,26,4,3}}) {
            g.setColor(crust); g.fillRect(p[0], p[1], p[2], p[3]);
        }

        g.dispose(); return img;
    }

    /** Dark peaked mountain with red glow at summit, smoke wisps. */
    static BufferedImage volcanicMountain() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        Color sky   = new Color(35, 30, 28);
        Color rockD = new Color(45, 38, 32);
        Color rockM = new Color(60, 52, 45);
        Color rockL = new Color(75, 65, 55);
        Color lavaG = new Color(200, 60, 10);
        Color glowH = new Color(255, 140, 30);
        Color smoke = new Color(100, 90, 80, 120);

        g.setColor(sky); g.fillRect(0, 0, 32, 32);

        // Mountain body (triangle)
        int[] xPts = {0, 16, 31, 31, 0};
        int[] yPts = {31, 4, 31, 31, 31};
        g.setColor(rockD); g.fillPolygon(xPts, yPts, 5);

        // Left face (lighter)
        int[] lx = {0, 16, 16, 0};
        int[] ly = {31, 4, 31, 31};
        g.setColor(rockM); g.fillPolygon(lx, ly, 4);

        // Highlight edge
        g.setColor(rockL);
        g.drawLine(2, 30, 16, 4);

        // Rock texture
        rng.setSeed(307);
        for (int i = 0; i < 60; i++) {
            int x = rng.nextInt(32), y = 8 + rng.nextInt(24);
            if (y > (4 + (Math.abs(x - 16) * 27 / 16))) continue; // inside triangle
            g.setColor(rng.nextBoolean() ? rockL : rockD);
            g.fillRect(x, y, 1, 1);
        }

        // Volcanic glow at summit
        g.setColor(lavaG); g.fillRect(14, 4, 5, 3);
        g.setColor(glowH); g.fillRect(15, 5, 3, 2);
        g.setColor(new Color(255, 200, 60)); g.fillRect(16, 5, 1, 1);

        // Smoke wisps
        g.setColor(smoke);
        g.drawLine(16, 3, 14, 0); g.drawLine(17, 3, 19, 0);
        g.drawLine(15, 2, 16, 0);

        g.dispose(); return img;
    }

    /** Jagged dark mound with metallic grey sheen, orange hot spots. */
    static BufferedImage slagHeap() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        Color base  = new Color(50, 45, 40);
        Color metal = new Color(90, 85, 75);
        Color dark  = new Color(35, 30, 25);
        Color hot   = new Color(180, 70, 15);
        Color glow  = new Color(220, 110, 20);

        g.setColor(base); g.fillRect(0, 0, 32, 32);

        // Jagged mound shape
        int[] xPts = {2, 8, 12, 16, 20, 24, 30, 31, 0};
        int[] yPts = {28, 14, 18, 10, 16, 12, 22, 31, 31};
        g.setColor(dark); g.fillPolygon(xPts, yPts, 9);

        // Metal sheen highlights
        g.setColor(metal);
        g.drawLine(8, 14, 12, 18); g.drawLine(16, 10, 20, 16);
        g.drawLine(24, 12, 28, 20);

        // Hot spots
        rng.setSeed(308);
        for (int i = 0; i < 8; i++) {
            int x = 5 + rng.nextInt(22), y = 14 + rng.nextInt(14);
            g.setColor(rng.nextBoolean() ? hot : glow);
            g.fillRect(x, y, 1, 1);
        }

        g.dispose(); return img;
    }

    /** Dark sand with obsidian flecks. */
    static BufferedImage volcanicSand() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        Color base  = new Color(85, 75, 58);
        Color light = new Color(100, 90, 70);
        Color dark  = new Color(70, 62, 48);
        Color obsid = new Color(20, 18, 22);
        Color glint = new Color(150, 145, 140);

        g.setColor(base); g.fillRect(0, 0, 32, 32);

        // Sand grain noise
        rng.setSeed(309);
        for (int i = 0; i < 130; i++) {
            g.setColor(rng.nextBoolean() ? light : dark);
            g.fillRect(rng.nextInt(32), rng.nextInt(32), 1, 1);
        }

        // Obsidian flecks (dark with occasional glint)
        rng.setSeed(310);
        for (int i = 0; i < 18; i++) {
            int x = rng.nextInt(32), y = rng.nextInt(32);
            g.setColor(obsid); g.fillRect(x, y, 1, 1);
            if (rng.nextInt(3) == 0) {
                g.setColor(glint); g.fillRect(x, y, 1, 1);
            }
        }

        g.dispose(); return img;
    }

    /** Charred trunk, no leaves, orange ember dots. */
    static BufferedImage ashTree() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        // Background matches volcanic rock
        Color bg    = new Color(55, 48, 42);
        Color trunk = new Color(40, 32, 25);
        Color bark  = new Color(55, 45, 35);
        Color ember = new Color(200, 80, 15);
        Color glow  = new Color(255, 140, 30);

        g.setColor(bg); g.fillRect(0, 0, 32, 32);

        // Trunk
        g.setColor(trunk); g.fillRect(14, 8, 4, 22);
        g.setColor(bark);  g.drawLine(14, 8, 14, 29); g.drawLine(17, 8, 17, 29);

        // Branches (bare, angular)
        g.setColor(trunk);
        g.drawLine(14, 12, 8, 6);  g.drawLine(8, 6, 5, 3);
        g.drawLine(17, 14, 24, 8); g.drawLine(24, 8, 27, 5);
        g.drawLine(14, 18, 10, 14);
        g.drawLine(17, 20, 22, 16); g.drawLine(22, 16, 25, 12);
        g.setColor(bark);
        g.drawLine(14, 11, 9, 6); g.drawLine(17, 13, 23, 8);

        // Ember dots on branches
        g.setColor(ember);
        g.fillRect(5, 3, 2, 1); g.fillRect(27, 5, 1, 1);
        g.fillRect(10, 14, 1, 1); g.fillRect(25, 12, 1, 1);
        g.setColor(glow);
        g.fillRect(6, 3, 1, 1); g.fillRect(24, 8, 1, 1);

        // Root base
        g.setColor(trunk);
        g.fillRect(12, 29, 2, 2); g.fillRect(18, 29, 2, 2);

        g.dispose(); return img;
    }

    // ── TOWN TILES ──────────────────────────────────────────────────────────

    /** Black glossy obsidian tiles with reflected highlight streaks. */
    static BufferedImage obsidianFloor() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        Color base   = new Color(15, 15, 20);
        Color tile1  = new Color(22, 22, 28);
        Color tile2  = new Color(18, 18, 24);
        Color grout  = new Color(8, 8, 12);
        Color sheen  = new Color(50, 50, 60);
        Color bright = new Color(70, 70, 85);

        g.setColor(base); g.fillRect(0, 0, 32, 32);

        // 4-tile grid
        g.setColor(tile1); g.fillRect(0, 0, 15, 15);
        g.setColor(tile2); g.fillRect(16, 0, 15, 15);
        g.setColor(tile2); g.fillRect(0, 16, 15, 15);
        g.setColor(tile1); g.fillRect(16, 16, 15, 15);

        // Grout lines
        g.setColor(grout);
        g.drawLine(15, 0, 15, 31); g.drawLine(0, 15, 31, 15);

        // Glossy sheen streaks
        g.setColor(sheen);
        g.drawLine(3, 3, 8, 8); g.drawLine(20, 4, 26, 10);
        g.drawLine(4, 20, 10, 26); g.drawLine(19, 19, 25, 25);
        g.setColor(bright);
        g.fillRect(5, 5, 1, 1); g.fillRect(22, 6, 1, 1);
        g.fillRect(6, 22, 1, 1); g.fillRect(22, 22, 1, 1);

        g.dispose(); return img;
    }

    /** Dark stone with amber warmth, embedded embers. */
    static BufferedImage forgeFloor() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        Color base  = new Color(65, 45, 28);
        Color light = new Color(85, 60, 35);
        Color dark  = new Color(50, 35, 22);
        Color ember = new Color(180, 80, 15);
        Color glow  = new Color(220, 120, 25);

        g.setColor(base); g.fillRect(0, 0, 32, 32);

        // Stone texture
        rng.setSeed(311);
        for (int i = 0; i < 100; i++) {
            g.setColor(rng.nextBoolean() ? light : dark);
            g.fillRect(rng.nextInt(32), rng.nextInt(32), 1, 1);
        }

        // Grout lines (warm-toned)
        g.setColor(dark);
        g.drawLine(15, 0, 15, 31); g.drawLine(0, 15, 31, 15);

        // Embedded embers
        rng.setSeed(312);
        for (int i = 0; i < 6; i++) {
            int x = rng.nextInt(30)+1, y = rng.nextInt(30)+1;
            g.setColor(ember); g.fillRect(x, y, 1, 1);
            if (rng.nextBoolean()) { g.setColor(glow); g.fillRect(x, y, 1, 1); }
        }

        g.dispose(); return img;
    }

    /** Iron grate pattern over glowing orange below. */
    static BufferedImage emberGrate() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        Color glow  = new Color(200, 80, 10);
        Color hot   = new Color(245, 140, 20);
        Color iron  = new Color(60, 58, 55);
        Color ironL = new Color(80, 78, 72);

        // Glowing embers background
        g.setColor(glow); g.fillRect(0, 0, 32, 32);
        rng.setSeed(313);
        for (int i = 0; i < 40; i++) {
            g.setColor(hot);
            g.fillRect(rng.nextInt(32), rng.nextInt(32), 1+rng.nextInt(2), 1+rng.nextInt(2));
        }

        // Iron grate bars (horizontal and vertical)
        g.setColor(iron);
        for (int x = 0; x < 32; x += 8) { g.fillRect(x, 0, 3, 32); }
        for (int y = 0; y < 32; y += 8) { g.fillRect(0, y, 32, 3); }

        // Highlights on grate
        g.setColor(ironL);
        for (int x = 0; x < 32; x += 8) { g.drawLine(x, 0, x, 31); }
        for (int y = 0; y < 32; y += 8) { g.drawLine(0, y, 31, y); }

        g.dispose(); return img;
    }

    /** Narrow lava stream in carved stone channel. */
    static BufferedImage lavaChannel() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        Color stone = new Color(55, 48, 42);
        Color edge  = new Color(40, 35, 30);
        Color lava  = new Color(210, 70, 10);
        Color hot   = new Color(245, 140, 20);
        Color glow  = new Color(255, 220, 80);

        // Stone sides
        g.setColor(stone); g.fillRect(0, 0, 32, 32);

        // Channel edges
        g.setColor(edge); g.fillRect(10, 0, 1, 32); g.fillRect(21, 0, 1, 32);

        // Lava in channel
        g.setColor(lava); g.fillRect(11, 0, 10, 32);

        // Hot streaks in lava
        for (int y = 0; y < 32; y += 2) {
            int xOff = (int)(1.5 * Math.sin(y * Math.PI / 8.0));
            g.setColor(hot); g.fillRect(15 + xOff, y, 2, 1);
        }

        // Bright glow center
        rng.setSeed(314);
        for (int i = 0; i < 10; i++) {
            g.setColor(glow);
            g.fillRect(12 + rng.nextInt(8), rng.nextInt(32), 1, 1);
        }

        g.dispose(); return img;
    }

    // ── MONSTER SPRITES ─────────────────────────────────────────────────────

    /** Semi-molten creature — blob-like body with glowing cracks. */
    static BufferedImage slagBeast() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        Color body  = new Color(60, 45, 35);
        Color bodyL = new Color(80, 60, 45);
        Color crack = new Color(200, 70, 10);
        Color glow  = new Color(245, 140, 20);
        Color eye   = new Color(255, 200, 40);

        // Body blob
        g.setColor(body); g.fillOval(5, 8, 22, 20);
        g.setColor(bodyL); g.fillOval(8, 10, 16, 14);

        // Molten cracks
        g.setColor(crack);
        g.drawLine(10, 14, 14, 20); g.drawLine(14, 20, 18, 16);
        g.drawLine(18, 16, 22, 22); g.drawLine(8, 22, 12, 26);
        g.setColor(glow);
        g.fillRect(14, 19, 1, 2); g.fillRect(18, 15, 1, 2);

        // Eyes
        g.setColor(eye); g.fillRect(12, 13, 2, 2); g.fillRect(18, 13, 2, 2);

        // Dripping bottom
        g.setColor(body);
        g.fillRect(10, 27, 3, 3); g.fillRect(19, 27, 3, 3);

        g.dispose(); return img;
    }

    /** Small fiery elemental — flame shape with bright core. */
    static BufferedImage emberSprite() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        Color outer = new Color(200, 50, 5);
        Color mid   = new Color(240, 120, 15);
        Color inner = new Color(255, 200, 50);
        Color white = new Color(255, 245, 200);
        Color eye   = new Color(20, 10, 5);

        // Outer flame
        g.setColor(outer);
        g.fillOval(6, 10, 20, 18);
        // Flame tips at top
        int[] fx = {10, 13, 16, 16, 12};
        int[] fy = {10, 3,  8,  10, 10};
        g.fillPolygon(fx, fy, 5);
        int[] fx2 = {16, 19, 22, 22, 16};
        int[] fy2 = {10, 5,  10, 10, 10};
        g.fillPolygon(fx2, fy2, 5);

        // Mid flame
        g.setColor(mid);
        g.fillOval(9, 13, 14, 12);
        g.fillPolygon(new int[]{13, 16, 18, 18, 13}, new int[]{13, 6, 13, 13, 13}, 5);

        // Inner core
        g.setColor(inner);
        g.fillOval(12, 15, 8, 8);

        // White hot center
        g.setColor(white);
        g.fillOval(14, 17, 4, 4);

        // Eyes
        g.setColor(eye);
        g.fillRect(13, 18, 2, 2); g.fillRect(18, 18, 2, 2);

        g.dispose(); return img;
    }

    /** Large humanoid made of dark metal with lava joints. */
    static BufferedImage forgeGolem() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        Color metal = new Color(50, 48, 45);
        Color metL  = new Color(75, 72, 68);
        Color lava  = new Color(200, 70, 10);
        Color glow  = new Color(245, 140, 20);
        Color eye   = new Color(255, 180, 30);

        // Head
        g.setColor(metal); g.fillRect(12, 2, 8, 7);
        g.setColor(metL);  g.drawLine(12, 2, 19, 2);

        // Eyes
        g.setColor(eye); g.fillRect(13, 4, 2, 2); g.fillRect(17, 4, 2, 2);

        // Neck joint (lava)
        g.setColor(lava); g.fillRect(14, 9, 4, 2);

        // Torso
        g.setColor(metal); g.fillRect(8, 11, 16, 10);
        g.setColor(metL); g.drawLine(8, 11, 23, 11); g.drawLine(8, 11, 8, 20);

        // Chest plate detail
        g.setColor(glow); g.fillRect(14, 14, 4, 3);

        // Shoulder joints (lava)
        g.setColor(lava); g.fillRect(6, 11, 2, 3); g.fillRect(24, 11, 2, 3);

        // Arms
        g.setColor(metal);
        g.fillRect(3, 14, 5, 8); g.fillRect(24, 14, 5, 8);
        g.setColor(metL);
        g.drawLine(3, 14, 3, 21); g.drawLine(24, 14, 24, 21);

        // Elbow joints
        g.setColor(lava); g.fillRect(4, 18, 3, 1); g.fillRect(25, 18, 3, 1);

        // Fists
        g.setColor(metal); g.fillRect(3, 22, 5, 3); g.fillRect(24, 22, 5, 3);

        // Hip joint
        g.setColor(lava); g.fillRect(10, 21, 12, 2);

        // Legs
        g.setColor(metal);
        g.fillRect(10, 23, 5, 6); g.fillRect(17, 23, 5, 6);
        g.setColor(metL);
        g.drawLine(10, 23, 10, 28); g.drawLine(17, 23, 17, 28);

        // Knee joints
        g.setColor(lava); g.fillRect(11, 26, 3, 1); g.fillRect(18, 26, 3, 1);

        // Feet
        g.setColor(metal); g.fillRect(9, 29, 6, 2); g.fillRect(17, 29, 6, 2);

        g.dispose(); return img;
    }

    /** Ghostly grey/black figure with ember eyes. */
    static BufferedImage ashWraith() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        Color ghost  = new Color(70, 65, 60, 180);
        Color ghostL = new Color(100, 95, 88, 140);
        Color ghostD = new Color(40, 35, 30, 200);
        Color eye    = new Color(255, 120, 20);
        Color eyeG   = new Color(255, 180, 40);
        Color wisp   = new Color(80, 75, 68, 80);

        // Hooded cloak body
        g.setColor(ghost);
        g.fillOval(8, 2, 16, 14); // hood
        g.fillRect(8, 10, 16, 16); // body
        // Tattered bottom
        g.setColor(ghostD);
        int[] bx = {6, 10, 14, 16, 18, 22, 26, 24, 16, 8};
        int[] by = {26, 30, 26, 31, 27, 30, 26, 26, 26, 26};
        g.fillPolygon(bx, by, 10);

        // Lighter robe front
        g.setColor(ghostL);
        g.fillRect(12, 12, 8, 10);

        // Hood shadow
        g.setColor(ghostD);
        g.fillOval(10, 4, 12, 8);

        // Ember eyes
        g.setColor(eye); g.fillRect(12, 7, 2, 2); g.fillRect(18, 7, 2, 2);
        g.setColor(eyeG); g.fillRect(13, 7, 1, 1); g.fillRect(19, 7, 1, 1);

        // Arms reaching out
        g.setColor(ghost);
        g.drawLine(8, 14, 3, 18); g.drawLine(3, 18, 2, 22);
        g.drawLine(24, 14, 29, 18); g.drawLine(29, 18, 30, 22);

        // Wispy trails
        g.setColor(wisp);
        g.drawLine(10, 28, 8, 31); g.drawLine(16, 29, 15, 31);
        g.drawLine(22, 28, 24, 31);

        g.dispose(); return img;
    }

    /** Coiled serpent with red/orange scales and fiery highlights. */
    static BufferedImage lavaSerpent() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        Color scaleD = new Color(160, 40, 10);
        Color scaleM = new Color(200, 70, 15);
        Color scaleL = new Color(240, 120, 25);
        Color belly  = new Color(220, 160, 40);
        Color eye    = new Color(255, 220, 50);
        Color tongue = new Color(220, 30, 20);

        // Coiled body (S-curve)
        g.setColor(scaleD);
        // Lower coil
        g.fillOval(4, 18, 18, 12);
        // Upper body curve
        g.fillOval(12, 8, 14, 10);
        // Head
        g.fillOval(4, 4, 12, 8);

        // Mid scales
        g.setColor(scaleM);
        g.fillOval(6, 20, 14, 8);
        g.fillOval(14, 10, 10, 6);
        g.fillOval(6, 5, 8, 6);

        // Light scales (highlights)
        g.setColor(scaleL);
        g.drawLine(8, 22, 16, 22); g.drawLine(16, 12, 22, 12);
        g.drawLine(7, 7, 12, 7);

        // Belly underside
        g.setColor(belly);
        g.drawLine(8, 25, 16, 25); g.drawLine(16, 15, 22, 15);

        // Scale pattern
        rng.setSeed(315);
        for (int i = 0; i < 12; i++) {
            g.setColor(scaleL);
            g.fillRect(6 + rng.nextInt(16), 8 + rng.nextInt(18), 1, 1);
        }

        // Eye
        g.setColor(eye); g.fillRect(7, 7, 2, 2);
        g.setColor(new Color(20, 10, 5)); g.fillRect(8, 7, 1, 1);

        // Forked tongue
        g.setColor(tongue);
        g.drawLine(4, 8, 1, 7); g.drawLine(4, 8, 1, 9);

        g.dispose(); return img;
    }
}

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Random;

/**
 * Sprite generator for Island 3 (Zephyrion / The Storm Archipelago).
 * Generates 32x32 PNG sprites for sky-themed overworld, town, and monster tiles.
 *
 * Run from the project root:
 *   javac tools/ZephyrionSpriteGen.java -d tools/
 *   java -cp tools ZephyrionSpriteGen
 */
public class ZephyrionSpriteGen {

    static Random rng = new Random(42);
    static final String OW  = "src/main/resources/tiles/overworld/";
    static final String TW  = "src/main/resources/tiles/town/";
    static final String MON = "src/main/resources/tiles/monsters/";

    public static void main(String[] args) throws Exception {
        // Overworld tiles
        save(skyVoid(),        OW + "sky_void.png");
        save(cloudPlatform(),  OW + "cloud_platform.png");
        save(skyGrass(),       OW + "sky_grass.png");
        save(skyRock(),        OW + "sky_rock.png");
        save(ropeBridge(),     OW + "rope_bridge.png");
        save(stormCloud(),     OW + "storm_cloud.png");
        save(lightningRod(),   OW + "lightning_rod.png");
        save(skyTree(),        OW + "sky_tree.png");
        save(windmill(),       OW + "windmill.png");
        save(crashedAirship(), OW + "crashed_airship.png");

        // Town tiles
        save(skyFloor(),       TW + "sky_floor.png");
        save(cloudBrick(),     TW + "cloud_brick.png");
        save(windchime(),      TW + "windchime.png");
        save(skyDock(),        TW + "sky_dock.png");

        // Monster sprites
        save(stormHawk(),        MON + "storm_hawk.png");
        save(galeSpirit(),       MON + "gale_spirit.png");
        save(skyRaider(),        MON + "sky_raider.png");
        save(thunderElemental(), MON + "thunder_elemental.png");
        save(stormSerpent(),     MON + "storm_serpent.png");
        save(cloudWraith(),      MON + "cloud_wraith.png");

        System.out.println("Zephyrion sprites generated: 20 files.");
    }

    static void save(BufferedImage img, String path) throws Exception {
        File f = new File(path);
        f.getParentFile().mkdirs();
        ImageIO.write(img, "PNG", f);
        System.out.println("  wrote " + path);
    }

    // ── OVERWORLD TILES ─────────────────────────────────────────────────────

    /** Deep blue-purple void between sky islands. */
    static BufferedImage skyVoid() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        Color bg   = new Color(20, 25, 50);
        Color star  = new Color(120, 140, 180);
        Color cloud = new Color(40, 45, 70);
        g.setColor(bg); g.fillRect(0, 0, 32, 32);
        // Wispy distant clouds
        rng.setSeed(501);
        for (int i = 0; i < 40; i++) {
            g.setColor(cloud);
            g.fillRect(rng.nextInt(32), rng.nextInt(32), 2+rng.nextInt(3), 1);
        }
        // Tiny stars
        rng.setSeed(502);
        for (int i = 0; i < 8; i++) {
            g.setColor(star);
            g.fillRect(rng.nextInt(32), rng.nextInt(32), 1, 1);
        }
        g.dispose(); return img;
    }

    /** White-blue cloud that's walkable. */
    static BufferedImage cloudPlatform() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        Color base  = new Color(200, 210, 230);
        Color light = new Color(230, 235, 245);
        Color shad  = new Color(170, 180, 205);
        Color glow  = new Color(245, 248, 255);
        g.setColor(base); g.fillRect(0, 0, 32, 32);
        rng.setSeed(503);
        for (int i = 0; i < 80; i++) {
            g.setColor(rng.nextBoolean() ? light : shad);
            g.fillOval(rng.nextInt(30), rng.nextInt(30), 3+rng.nextInt(4), 2+rng.nextInt(3));
        }
        // Bright highlights
        rng.setSeed(504);
        for (int i = 0; i < 12; i++) {
            g.setColor(glow);
            g.fillRect(rng.nextInt(30)+1, rng.nextInt(30)+1, 2, 1);
        }
        g.dispose(); return img;
    }

    /** Blue-green grass on floating islands. */
    static BufferedImage skyGrass() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        Color base  = new Color(60, 140, 90);
        Color light = new Color(80, 170, 110);
        Color dark  = new Color(45, 110, 70);
        Color sky   = new Color(100, 180, 140);
        g.setColor(base); g.fillRect(0, 0, 32, 32);
        rng.setSeed(505);
        for (int i = 0; i < 120; i++) {
            g.setColor(rng.nextBoolean() ? light : dark);
            g.fillRect(rng.nextInt(32), rng.nextInt(32), 1, 1+rng.nextInt(2));
        }
        // Wind-swept grass blades (diagonal strokes)
        g.setColor(sky);
        rng.setSeed(506);
        for (int i = 0; i < 10; i++) {
            int x = rng.nextInt(30)+1, y = rng.nextInt(30)+1;
            g.drawLine(x, y, x+2, y-1);
        }
        g.dispose(); return img;
    }

    /** Grey-blue stone on sky islands. */
    static BufferedImage skyRock() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        Color base  = new Color(100, 105, 120);
        Color light = new Color(130, 135, 150);
        Color dark  = new Color(75, 78, 90);
        Color vein  = new Color(80, 140, 200);
        g.setColor(base); g.fillRect(0, 0, 32, 32);
        rng.setSeed(507);
        for (int i = 0; i < 100; i++) {
            g.setColor(rng.nextBoolean() ? light : dark);
            g.fillRect(rng.nextInt(32), rng.nextInt(32), 1+rng.nextInt(2), 1);
        }
        // Lightning-blue veins
        g.setColor(vein);
        g.drawLine(5, 0, 10, 15); g.drawLine(10, 15, 6, 31);
        g.drawLine(22, 0, 20, 12); g.drawLine(20, 12, 26, 28);
        g.dispose(); return img;
    }

    /** Rope bridge over void — planks with rope rails. */
    static BufferedImage ropeBridge() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        Color void_ = new Color(25, 30, 55);
        Color plank = new Color(140, 100, 55);
        Color plnkD = new Color(110, 78, 42);
        Color rope  = new Color(160, 130, 80);
        Color ropeD = new Color(120, 95, 55);
        // Void background
        g.setColor(void_); g.fillRect(0, 0, 32, 32);
        // Rope rails
        g.setColor(rope);
        g.fillRect(4, 0, 2, 32); g.fillRect(26, 0, 2, 32);
        g.setColor(ropeD);
        g.fillRect(5, 0, 1, 32); g.fillRect(27, 0, 1, 32);
        // Planks (horizontal)
        for (int y = 1; y < 32; y += 4) {
            g.setColor(plank); g.fillRect(6, y, 20, 3);
            g.setColor(plnkD); g.drawLine(6, y+2, 25, y+2);
            // Gap between planks
        }
        // Rope ties
        g.setColor(ropeD);
        for (int y = 2; y < 32; y += 8) {
            g.drawLine(4, y, 6, y); g.drawLine(26, y, 28, y);
        }
        g.dispose(); return img;
    }

    /** Dark storm cloud — purple-grey with lightning flashes. */
    static BufferedImage stormCloud() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        Color base  = new Color(50, 45, 65);
        Color dark  = new Color(35, 30, 48);
        Color light = new Color(70, 65, 85);
        Color flash = new Color(200, 210, 255);
        Color bolt  = new Color(255, 255, 200);
        g.setColor(base); g.fillRect(0, 0, 32, 32);
        rng.setSeed(508);
        for (int i = 0; i < 60; i++) {
            g.setColor(rng.nextBoolean() ? dark : light);
            g.fillOval(rng.nextInt(30), rng.nextInt(30), 3+rng.nextInt(5), 2+rng.nextInt(3));
        }
        // Lightning bolt
        g.setColor(flash);
        g.drawLine(16, 5, 14, 12); g.drawLine(14, 12, 18, 16);
        g.drawLine(18, 16, 15, 24);
        g.setColor(bolt);
        g.drawLine(16, 6, 14, 11); g.drawLine(18, 15, 15, 23);
        g.dispose(); return img;
    }

    /** Tall lightning rod — metal pole with glowing tip, safe zone. */
    static BufferedImage lightningRod() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        Color grass = new Color(60, 140, 90);
        Color metal = new Color(140, 145, 155);
        Color metD  = new Color(100, 105, 115);
        Color glow  = new Color(180, 220, 255);
        Color spark = new Color(220, 240, 255);
        // Base grass
        g.setColor(grass); g.fillRect(0, 0, 32, 32);
        // Stone base
        g.setColor(new Color(90, 90, 100));
        g.fillRect(12, 26, 8, 6);
        // Metal pole
        g.setColor(metal); g.fillRect(15, 4, 2, 22);
        g.setColor(metD); g.drawLine(15, 4, 15, 25);
        // Glowing orb at top
        g.setColor(glow); g.fillOval(13, 1, 6, 6);
        g.setColor(spark); g.fillOval(14, 2, 4, 4);
        // Radiating sparks
        g.setColor(new Color(180, 220, 255, 120));
        g.drawLine(16, 0, 16, 1); g.drawLine(11, 4, 13, 4);
        g.drawLine(19, 4, 21, 4); g.drawLine(12, 1, 13, 2);
        g.drawLine(20, 1, 19, 2);
        g.dispose(); return img;
    }

    /** Wind-blown tree — trunk angled, leaves streaming right. */
    static BufferedImage skyTree() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        Color grass = new Color(60, 140, 90);
        Color trunk = new Color(80, 60, 40);
        Color bark  = new Color(95, 75, 50);
        Color leaf  = new Color(50, 150, 100);
        Color leafL = new Color(70, 180, 120);
        Color leafD = new Color(35, 120, 75);
        g.setColor(grass); g.fillRect(0, 0, 32, 32);
        // Trunk (angled right by wind)
        g.setColor(trunk);
        g.drawLine(12, 28, 14, 18); g.drawLine(14, 18, 16, 10);
        g.drawLine(13, 28, 15, 18); g.drawLine(15, 18, 17, 10);
        g.setColor(bark); g.drawLine(12, 27, 14, 17);
        // Canopy (blown right)
        g.setColor(leaf);
        g.fillOval(14, 4, 14, 10);
        g.fillOval(18, 2, 10, 8);
        g.setColor(leafL);
        g.fillOval(16, 5, 10, 6);
        g.setColor(leafD);
        g.fillOval(14, 8, 8, 5);
        // Wind streaks from leaves
        g.setColor(leafL);
        g.drawLine(28, 4, 31, 3); g.drawLine(27, 7, 31, 6);
        g.drawLine(26, 10, 30, 9);
        // Roots
        g.setColor(trunk);
        g.fillRect(10, 28, 3, 2); g.fillRect(14, 28, 3, 2);
        g.dispose(); return img;
    }

    /** Windmill — stone base with spinning blades. */
    static BufferedImage windmill() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        Color grass = new Color(60, 140, 90);
        Color stone = new Color(140, 135, 125);
        Color stonD = new Color(110, 105, 95);
        Color blade = new Color(180, 170, 150);
        Color bladD = new Color(150, 140, 120);
        Color hub   = new Color(100, 90, 75);
        g.setColor(grass); g.fillRect(0, 0, 32, 32);
        // Stone tower
        g.setColor(stone); g.fillRect(12, 12, 8, 18);
        g.setColor(stonD); g.drawLine(12, 12, 12, 29); g.drawLine(19, 12, 19, 29);
        // Roof
        g.setColor(new Color(120, 60, 40));
        int[] rx = {10, 16, 22}; int[] ry = {12, 7, 12};
        g.fillPolygon(rx, ry, 3);
        // Hub
        g.setColor(hub); g.fillOval(14, 8, 4, 4);
        // Blades (X shape)
        g.setColor(blade);
        g.fillRect(15, 0, 2, 8);   // top
        g.fillRect(15, 12, 2, 8);  // bottom
        g.fillRect(6, 9, 8, 2);    // left
        g.fillRect(18, 9, 8, 2);   // right
        g.setColor(bladD);
        g.drawLine(16, 0, 16, 7); g.drawLine(16, 13, 16, 19);
        g.drawLine(7, 10, 13, 10); g.drawLine(19, 10, 25, 10);
        // Door
        g.setColor(new Color(70, 50, 35));
        g.fillRect(14, 25, 4, 5);
        g.dispose(); return img;
    }

    /** Crashed airship wreckage. */
    static BufferedImage crashedAirship() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        Color grass = new Color(60, 140, 90);
        Color hull  = new Color(120, 90, 55);
        Color hullD = new Color(90, 65, 38);
        Color metal = new Color(130, 130, 135);
        Color sail  = new Color(180, 170, 150, 160);
        Color fire  = new Color(200, 80, 20);
        g.setColor(grass); g.fillRect(0, 0, 32, 32);
        // Hull wreckage (tilted)
        g.setColor(hull);
        int[] hx = {4, 8, 28, 26}; int[] hy = {20, 14, 18, 26};
        g.fillPolygon(hx, hy, 4);
        g.setColor(hullD); g.drawLine(4, 20, 8, 14); g.drawLine(26, 26, 28, 18);
        // Broken mast
        g.setColor(metal);
        g.drawLine(14, 12, 20, 4); g.drawLine(15, 12, 21, 4);
        // Torn sail
        g.setColor(sail);
        int[] sx = {18, 22, 26, 20}; int[] sy = {5, 3, 8, 10};
        g.fillPolygon(sx, sy, 4);
        // Smoldering fire
        g.setColor(fire);
        g.fillOval(8, 22, 5, 4);
        g.setColor(new Color(255, 140, 30));
        g.fillOval(9, 23, 3, 2);
        // Scattered debris
        g.setColor(hullD);
        g.fillRect(2, 26, 3, 2); g.fillRect(24, 14, 2, 3);
        g.dispose(); return img;
    }

    // ── TOWN TILES ──────────────────────────────────────────────────────────

    /** Light stone floor with sky-blue tint. */
    static BufferedImage skyFloor() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        Color base  = new Color(170, 175, 190);
        Color light = new Color(190, 195, 210);
        Color grout = new Color(140, 145, 160);
        g.setColor(base); g.fillRect(0, 0, 32, 32);
        g.setColor(light); g.fillRect(0, 0, 15, 15); g.fillRect(16, 16, 15, 15);
        g.setColor(grout);
        g.drawLine(15, 0, 15, 31); g.drawLine(0, 15, 31, 15);
        rng.setSeed(520);
        for (int i = 0; i < 30; i++) {
            g.setColor(rng.nextBoolean() ? light : grout);
            g.fillRect(rng.nextInt(32), rng.nextInt(32), 1, 1);
        }
        g.dispose(); return img;
    }

    /** White-blue cloud brick wall. */
    static BufferedImage cloudBrick() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        Color brick = new Color(180, 190, 210);
        Color brkL  = new Color(200, 210, 225);
        Color mortr = new Color(150, 160, 180);
        g.setColor(brick); g.fillRect(0, 0, 32, 32);
        // Brick pattern
        g.setColor(mortr);
        for (int y = 0; y < 32; y += 8) { g.drawLine(0, y, 31, y); }
        for (int row = 0; row < 4; row++) {
            int off = (row % 2 == 0) ? 0 : 8;
            for (int x = off; x < 32; x += 16) { g.drawLine(x, row*8, x, row*8+7); }
        }
        g.setColor(brkL);
        rng.setSeed(521);
        for (int i = 0; i < 20; i++) {
            g.fillRect(rng.nextInt(30)+1, rng.nextInt(30)+1, 2, 1);
        }
        g.dispose(); return img;
    }

    /** Hanging windchime decoration. */
    static BufferedImage windchime() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        Color sky   = new Color(170, 175, 190);
        Color metal = new Color(180, 185, 200);
        Color metL  = new Color(220, 225, 240);
        Color str   = new Color(140, 120, 90);
        g.setColor(sky); g.fillRect(0, 0, 32, 32);
        // Top bar
        g.setColor(str); g.fillRect(8, 4, 16, 2);
        // Hanging tubes
        int[] cx = {10, 14, 18, 22};
        int[] ch = {12, 16, 10, 14};
        for (int i = 0; i < 4; i++) {
            g.setColor(str); g.drawLine(cx[i], 6, cx[i], 8);
            g.setColor(metal); g.fillRect(cx[i]-1, 8, 2, ch[i]);
            g.setColor(metL); g.drawLine(cx[i], 8, cx[i], 8+ch[i]-1);
        }
        // Wind sail at bottom
        g.setColor(metL);
        int[] sx = {14, 16, 18, 16}; int[] sy = {26, 24, 26, 30};
        g.fillPolygon(sx, sy, 4);
        g.dispose(); return img;
    }

    /** Wooden sky dock planks with rope edge. */
    static BufferedImage skyDock() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        Color void_ = new Color(25, 30, 55);
        Color plank = new Color(150, 115, 65);
        Color plnkD = new Color(120, 90, 48);
        Color rope  = new Color(160, 130, 80);
        g.setColor(void_); g.fillRect(0, 0, 32, 32);
        // Planks
        for (int x = 0; x < 32; x += 5) {
            g.setColor(plank); g.fillRect(x, 2, 4, 28);
            g.setColor(plnkD); g.drawLine(x, 2, x, 29);
        }
        // Rope border top and bottom
        g.setColor(rope); g.fillRect(0, 0, 32, 2); g.fillRect(0, 30, 32, 2);
        g.dispose(); return img;
    }

    // ── MONSTER SPRITES ─────────────────────────────────────────────────────

    /** Storm Hawk — L8, aggressive raptor with electric plumage. */
    static BufferedImage stormHawk() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        Color body  = new Color(70, 80, 110);
        Color bodyL = new Color(100, 115, 150);
        Color wing  = new Color(55, 65, 95);
        Color wingL = new Color(80, 95, 130);
        Color beak  = new Color(200, 170, 40);
        Color eye   = new Color(255, 240, 100);
        Color spark = new Color(180, 210, 255);
        // Wings spread
        g.setColor(wing);
        g.fillOval(0, 8, 14, 8);   // left wing
        g.fillOval(18, 8, 14, 8);  // right wing
        g.setColor(wingL);
        g.fillOval(2, 9, 10, 5);
        g.fillOval(20, 9, 10, 5);
        // Body
        g.setColor(body); g.fillOval(10, 8, 12, 14);
        g.setColor(bodyL); g.fillOval(12, 10, 8, 8);
        // Head
        g.setColor(body); g.fillOval(12, 4, 8, 8);
        g.setColor(bodyL); g.fillOval(13, 5, 6, 6);
        // Beak
        g.setColor(beak);
        int[] bx = {16, 14, 18}; int[] by = {12, 10, 10};
        g.fillPolygon(bx, by, 3);
        // Eye
        g.setColor(eye); g.fillRect(14, 7, 2, 2);
        g.setColor(new Color(30, 20, 10)); g.fillRect(15, 7, 1, 1);
        // Tail feathers
        g.setColor(wing);
        g.fillRect(14, 22, 4, 6);
        g.drawLine(13, 26, 12, 30); g.drawLine(18, 26, 19, 30);
        // Electric sparks on wings
        g.setColor(spark);
        g.fillRect(3, 10, 1, 1); g.fillRect(7, 8, 1, 1);
        g.fillRect(24, 10, 1, 1); g.fillRect(28, 8, 1, 1);
        g.fillRect(5, 13, 1, 1); g.fillRect(26, 13, 1, 1);
        g.dispose(); return img;
    }

    /** Gale Spirit — L9, translucent wind elemental with spiral form. */
    static BufferedImage galeSpirit() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        Color core  = new Color(180, 210, 240, 200);
        Color coreL = new Color(220, 235, 255, 160);
        Color wind  = new Color(150, 185, 220, 120);
        Color windL = new Color(200, 220, 245, 80);
        Color eye   = new Color(255, 255, 255);
        // Spiral body form
        g.setColor(wind);
        g.fillOval(4, 4, 24, 24);
        g.setColor(core);
        g.fillOval(8, 8, 16, 16);
        g.setColor(coreL);
        g.fillOval(11, 11, 10, 10);
        // Spiral wind lines
        g.setColor(windL);
        g.drawArc(6, 6, 20, 20, 0, 270);
        g.drawArc(10, 10, 12, 12, 90, 270);
        g.drawArc(13, 13, 6, 6, 180, 270);
        // Eyes (two white dots)
        g.setColor(eye);
        g.fillRect(13, 14, 2, 2); g.fillRect(18, 14, 2, 2);
        // Trailing wisps
        g.setColor(new Color(150, 185, 220, 60));
        g.drawLine(4, 16, 0, 18); g.drawLine(28, 16, 31, 14);
        g.drawLine(16, 28, 14, 31); g.drawLine(16, 4, 18, 1);
        g.dispose(); return img;
    }

    /** Sky Raider — L9, human pirate with lightning cutlass. */
    static BufferedImage skyRaider() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        Color skin  = new Color(170, 140, 110);
        Color skinD = new Color(140, 110, 85);
        Color coat  = new Color(50, 55, 80);
        Color coatL = new Color(70, 75, 105);
        Color hat   = new Color(40, 40, 55);
        Color hatB  = new Color(60, 60, 78);
        Color blade = new Color(180, 200, 240);
        Color bladeG= new Color(220, 235, 255);
        Color eye   = new Color(30, 25, 20);
        Color boot  = new Color(50, 40, 30);
        // Tricorn hat
        g.setColor(hat);
        g.fillOval(8, 1, 16, 8);
        g.setColor(hatB);
        g.fillRect(6, 5, 20, 4);
        // Face
        g.setColor(skin); g.fillOval(11, 6, 10, 10);
        g.setColor(skinD); g.fillOval(12, 7, 8, 8);
        // Eyes + eyepatch
        g.setColor(eye); g.fillRect(13, 10, 2, 2);
        g.setColor(new Color(20, 20, 25));
        g.fillRect(17, 9, 3, 3); // eyepatch
        g.drawLine(17, 9, 20, 6); // strap
        // Grin
        g.setColor(skinD); g.drawLine(14, 14, 18, 14);
        // Coat body
        g.setColor(coat); g.fillRect(9, 16, 14, 10);
        g.setColor(coatL); g.fillRect(13, 16, 6, 10);
        // Belt
        g.setColor(new Color(120, 90, 40)); g.fillRect(9, 20, 14, 2);
        // Arms
        g.setColor(coat);
        g.fillRect(5, 17, 4, 2);  // left
        g.fillRect(23, 17, 4, 2); // right
        // Left hand with cutlass
        g.setColor(skin); g.fillRect(3, 18, 3, 3);
        // Lightning cutlass
        g.setColor(blade);
        g.fillRect(1, 10, 2, 9);
        g.setColor(bladeG);
        g.drawLine(2, 10, 2, 18);
        // Crossguard
        g.setColor(new Color(180, 170, 40));
        g.fillRect(0, 18, 4, 2);
        // Boots
        g.setColor(boot);
        g.fillRect(10, 26, 5, 4); g.fillRect(17, 26, 5, 4);
        g.dispose(); return img;
    }

    /** Thunder Elemental — L10, pure lightning being. */
    static BufferedImage thunderElemental() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        Color core  = new Color(255, 255, 220);
        Color mid   = new Color(180, 210, 255);
        Color outer = new Color(100, 140, 220, 180);
        Color bolt  = new Color(255, 255, 240);
        Color spark = new Color(200, 220, 255);
        Color eye   = new Color(60, 40, 120);
        // Outer glow
        g.setColor(outer); g.fillOval(4, 4, 24, 24);
        // Mid body
        g.setColor(mid); g.fillOval(8, 8, 16, 16);
        // Core
        g.setColor(core); g.fillOval(12, 12, 8, 8);
        // Lightning arms
        g.setColor(bolt);
        // Left arm
        g.drawLine(8, 16, 4, 12); g.drawLine(4, 12, 6, 8);
        g.drawLine(6, 8, 2, 4);
        // Right arm
        g.drawLine(24, 16, 28, 12); g.drawLine(28, 12, 26, 8);
        g.drawLine(26, 8, 30, 4);
        // Bottom bolts
        g.drawLine(14, 24, 12, 28); g.drawLine(12, 28, 14, 31);
        g.drawLine(18, 24, 20, 28); g.drawLine(20, 28, 18, 31);
        // Eyes
        g.setColor(eye);
        g.fillRect(13, 14, 2, 2); g.fillRect(18, 14, 2, 2);
        // Sparks
        g.setColor(spark);
        rng.setSeed(515);
        for (int i = 0; i < 12; i++) {
            g.fillRect(rng.nextInt(30)+1, rng.nextInt(30)+1, 1, 1);
        }
        g.dispose(); return img;
    }

    /** Storm Serpent — L11, flying serpent wreathed in storm clouds. */
    static BufferedImage stormSerpent() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        Color scaleD = new Color(50, 70, 110);
        Color scaleM = new Color(70, 95, 140);
        Color scaleL = new Color(100, 130, 180);
        Color belly  = new Color(140, 165, 200);
        Color eye    = new Color(255, 240, 100);
        Color spark  = new Color(200, 220, 255);
        Color cloud  = new Color(80, 90, 120, 120);
        // Coiled body (S-curve, flying)
        g.setColor(scaleD);
        g.fillOval(4, 16, 18, 12);  // lower coil
        g.fillOval(12, 6, 14, 10);  // upper body
        g.fillOval(4, 2, 12, 8);    // head
        g.setColor(scaleM);
        g.fillOval(6, 18, 14, 8);
        g.fillOval(14, 8, 10, 6);
        g.fillOval(6, 3, 8, 6);
        g.setColor(scaleL);
        g.drawLine(8, 20, 16, 20); g.drawLine(16, 10, 22, 10);
        g.drawLine(7, 5, 12, 5);
        g.setColor(belly);
        g.drawLine(8, 23, 16, 23); g.drawLine(16, 13, 22, 13);
        // Eye
        g.setColor(eye); g.fillRect(7, 5, 2, 2);
        g.setColor(new Color(20, 10, 5)); g.fillRect(8, 5, 1, 1);
        // Horn/crest
        g.setColor(scaleL);
        g.drawLine(6, 2, 4, 0); g.drawLine(10, 2, 12, 0);
        // Storm cloud aura
        g.setColor(cloud);
        g.fillOval(0, 12, 8, 6); g.fillOval(22, 4, 8, 6);
        g.fillOval(16, 22, 10, 6);
        // Lightning sparks
        g.setColor(spark);
        g.fillRect(2, 14, 1, 1); g.fillRect(25, 6, 1, 1);
        g.fillRect(20, 24, 1, 1); g.fillRect(10, 10, 1, 1);
        g.dispose(); return img;
    }

    /** Cloud Wraith — L10, ghostly figure phasing into wind. */
    static BufferedImage cloudWraith() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        Color ghost  = new Color(160, 175, 200, 160);
        Color ghostL = new Color(190, 205, 225, 120);
        Color ghostD = new Color(100, 115, 140, 180);
        Color eye    = new Color(200, 220, 255);
        Color eyeG   = new Color(240, 248, 255);
        Color wisp   = new Color(140, 160, 190, 60);
        // Hooded form
        g.setColor(ghost);
        g.fillOval(8, 2, 16, 14);
        g.fillRect(8, 10, 16, 14);
        // Tattered bottom (dissipating into wind)
        g.setColor(ghostD);
        int[] bx = {6, 10, 14, 18, 22, 26, 24, 20, 16, 12, 8};
        int[] by = {24, 28, 25, 30, 26, 24, 28, 31, 29, 31, 27};
        g.fillPolygon(bx, by, 11);
        // Hood shadow
        g.setColor(ghostD); g.fillOval(10, 4, 12, 8);
        // Lighter robe
        g.setColor(ghostL); g.fillRect(12, 12, 8, 8);
        // Glowing eyes
        g.setColor(eye); g.fillRect(12, 7, 2, 2); g.fillRect(18, 7, 2, 2);
        g.setColor(eyeG); g.fillRect(13, 7, 1, 1); g.fillRect(19, 7, 1, 1);
        // Arms reaching (partially transparent)
        g.setColor(ghost);
        g.drawLine(8, 14, 3, 18); g.drawLine(3, 18, 1, 22);
        g.drawLine(24, 14, 29, 18); g.drawLine(29, 18, 31, 22);
        // Dissipating wind wisps (right side, phasing out)
        g.setColor(wisp);
        g.drawLine(26, 10, 30, 8); g.drawLine(27, 14, 31, 12);
        g.drawLine(25, 18, 30, 16);
        g.drawLine(24, 22, 29, 20); g.drawLine(22, 26, 28, 24);
        g.dispose(); return img;
    }
}

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Random;

/**
 * One-shot sprite generator for Island 6 (Umbryn / The Shadow Trenches).
 * Generates 32x32 PNG sprites for shadow-themed overworld, town, and monster tiles.
 *
 * Run from the project root:
 *   javac tools/UmbrynSpriteGen.java -d tools/
 *   java -cp tools UmbrynSpriteGen
 */
public class UmbrynSpriteGen {

    static Random rng = new Random(66);

    static final String OW  = "src/main/resources/tiles/overworld/";
    static final String TW  = "src/main/resources/tiles/town/";
    static final String MON = "src/main/resources/tiles/monsters/";

    public static void main(String[] args) throws Exception {
        // Overworld tiles
        save(shadowTrenchFloor(),  OW + "shadow_trench_floor.png");
        save(memoryRift(),         OW + "memory_rift.png");
        save(ruinedSpire(),        OW + "ruined_spire.png");
        save(ghostPath(),          OW + "ghost_path.png");
        save(crumbledArchive(),    OW + "crumbled_archive.png");
        save(silverRuins(),        OW + "silver_ruins.png");
        save(voidTrench(),         OW + "void_trench.png");
        save(memoryWard(),         OW + "memory_ward.png");

        // Town tiles
        save(dustFloor(),          TW + "dust_floor.png");
        save(memoryCrystalWall(),  TW + "memory_crystal_wall.png");
        save(ghostlightLamp(),     TW + "ghostlight_lamp.png");
        save(fadedPillar(),        TW + "faded_pillar.png");
        save(echoTile(),           TW + "echo_tile.png");

        // Monster sprites
        save(memoryShade(),        MON + "memory_shade.png");
        save(hollowOne(),          MON + "hollow_one.png");
        save(echoKnight(),         MON + "echo_knight.png");
        save(archiveGuardian(),    MON + "archive_guardian.png");
        save(griefElemental(),     MON + "grief_elemental.png");

        System.out.println("Umbryn / Shadow Trenches sprites generated: 18 files.");
    }

    static void save(BufferedImage img, String path) throws Exception {
        File f = new File(path);
        f.getParentFile().mkdirs();
        ImageIO.write(img, "PNG", f);
        System.out.println("  wrote " + path);
    }

    // == OVERWORLD TILES ======================================================

    /** Shadow trench floor -- dark purple-grey stone with faint silver dust. */
    static BufferedImage shadowTrenchFloor() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        Color base   = new Color(18, 10, 28);
        Color grain1 = new Color(24, 16, 36);
        Color grain2 = new Color(14, 8, 22);
        Color silver = new Color(140, 148, 165, 80);

        g.setColor(base); g.fillRect(0, 0, 32, 32);

        // Stone grain texture
        rng.setSeed(601);
        for (int i = 0; i < 100; i++) {
            g.setColor(rng.nextBoolean() ? grain1 : grain2);
            g.fillRect(rng.nextInt(32), rng.nextInt(32), 1 + rng.nextInt(2), 1);
        }

        // Faint silver dust motes
        rng.setSeed(602);
        for (int i = 0; i < 10; i++) {
            g.setColor(silver);
            g.fillRect(rng.nextInt(32), rng.nextInt(32), 1, 1);
        }

        g.dispose(); return img;
    }

    /** Memory rift -- cracked ground with ghostly cyan light bleeding through. */
    static BufferedImage memoryRift() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        Color base  = new Color(18, 10, 28);
        Color crack = new Color(80, 180, 200);
        Color glow  = new Color(60, 140, 170, 100);
        Color deep  = new Color(40, 200, 220);
        Color grain = new Color(24, 16, 36);

        g.setColor(base); g.fillRect(0, 0, 32, 32);

        // Ground texture
        rng.setSeed(603);
        for (int i = 0; i < 60; i++) {
            g.setColor(grain);
            g.fillRect(rng.nextInt(32), rng.nextInt(32), 1, 1);
        }

        // Rift glow aura
        g.setColor(glow);
        g.fillOval(6, 4, 20, 24);

        // Main rift crack (jagged vertical)
        g.setColor(crack);
        g.drawLine(14, 2, 16, 8); g.drawLine(16, 8, 13, 14);
        g.drawLine(13, 14, 17, 20); g.drawLine(17, 20, 15, 28);
        // Branch cracks
        g.drawLine(16, 8, 22, 12); g.drawLine(13, 14, 8, 18);
        g.drawLine(17, 20, 23, 22);

        // Bright center of rift
        g.setColor(deep);
        g.fillRect(15, 10, 1, 1); g.fillRect(14, 16, 1, 1);
        g.fillRect(16, 22, 1, 1);

        g.dispose(); return img;
    }

    /** Ruined spire -- broken tower remnant, impassable. */
    static BufferedImage ruinedSpire() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        Color base   = new Color(18, 10, 28);
        Color stone  = new Color(60, 55, 70);
        Color stoneD = new Color(40, 35, 50);
        Color stoneL = new Color(80, 76, 92);
        Color silver = new Color(160, 168, 184);

        g.setColor(base); g.fillRect(0, 0, 32, 32);

        // Spire body (tapered, broken at top)
        g.setColor(stone);
        int[] sx = {8, 12, 10, 6, 16, 20, 24, 22};
        int[] sy = {31, 4, 0, 8, 2, 6, 31, 31};
        g.fillPolygon(sx, sy, sx.length);

        // Dark mortar lines
        g.setColor(stoneD);
        for (int y = 6; y < 31; y += 5) {
            g.drawLine(9, y, 23, y);
        }
        g.drawLine(16, 0, 16, 31);

        // Light edges (left face lit)
        g.setColor(stoneL);
        g.drawLine(8, 31, 12, 4); g.drawLine(12, 4, 10, 0);

        // Crumbled rubble at base
        g.setColor(stoneD);
        g.fillOval(2, 26, 6, 5); g.fillOval(24, 27, 5, 4);
        g.setColor(stone);
        g.fillOval(3, 28, 4, 3);

        // Silver glints on old stone
        rng.setSeed(604);
        for (int i = 0; i < 8; i++) {
            g.setColor(silver);
            g.fillRect(10 + rng.nextInt(12), 4 + rng.nextInt(24), 1, 1);
        }

        g.dispose(); return img;
    }

    /** Ghost path -- faintly luminous silver-traced walkway. */
    static BufferedImage ghostPath() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        Color base   = new Color(20, 14, 32);
        Color path   = new Color(30, 24, 42);
        Color silver = new Color(140, 148, 165, 60);
        Color trace  = new Color(160, 170, 190, 40);
        Color bright = new Color(180, 190, 210, 90);

        g.setColor(base); g.fillRect(0, 0, 32, 32);

        // Path surface (slightly lighter)
        g.setColor(path); g.fillRect(2, 2, 28, 28);

        // Silver trace lines (old road markings)
        g.setColor(trace);
        g.drawLine(4, 0, 4, 31); g.drawLine(27, 0, 27, 31);
        g.drawLine(0, 4, 31, 4); g.drawLine(0, 27, 31, 27);

        // Scattered silver luminescence
        rng.setSeed(605);
        for (int i = 0; i < 20; i++) {
            g.setColor(silver);
            g.fillRect(rng.nextInt(32), rng.nextInt(32), 1, 1);
        }

        // Occasional brighter motes (ghost footprints)
        rng.setSeed(606);
        for (int i = 0; i < 5; i++) {
            g.setColor(bright);
            int bx = 4 + rng.nextInt(24), by = 4 + rng.nextInt(24);
            g.fillOval(bx, by, 2, 2);
        }

        g.dispose(); return img;
    }

    /** Crumbled archive -- collapsed building rubble with exposed shelves. */
    static BufferedImage crumbledArchive() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        Color base   = new Color(18, 10, 28);
        Color stone  = new Color(50, 45, 60);
        Color stoneD = new Color(35, 30, 44);
        Color wood   = new Color(65, 45, 30);
        Color page   = new Color(160, 155, 140);
        Color gold   = new Color(184, 160, 64);

        g.setColor(base); g.fillRect(0, 0, 32, 32);

        // Rubble mound
        g.setColor(stone);
        g.fillOval(0, 12, 32, 20);
        g.setColor(stoneD);
        rng.setSeed(607);
        for (int i = 0; i < 30; i++) {
            int rx = rng.nextInt(28), ry = 14 + rng.nextInt(14);
            g.fillOval(rx, ry, 2 + rng.nextInt(4), 2 + rng.nextInt(3));
        }

        // Exposed shelf fragments
        g.setColor(wood);
        g.fillRect(4, 14, 10, 2); g.fillRect(18, 18, 12, 2);

        // Scattered pages / scrolls
        g.setColor(page);
        g.fillRect(6, 12, 3, 2); g.fillRect(22, 16, 2, 2);
        g.fillRect(12, 20, 4, 1); g.fillRect(26, 22, 3, 1);

        // Faded gold lettering on debris
        g.setColor(gold);
        g.fillRect(7, 12, 1, 1); g.fillRect(23, 16, 1, 1);

        g.dispose(); return img;
    }

    /** Silver ruins -- larger intact ruin fragments with silver patina. */
    static BufferedImage silverRuins() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        Color base   = new Color(18, 10, 28);
        Color stone  = new Color(65, 62, 75);
        Color stoneD = new Color(45, 42, 55);
        Color silver = new Color(160, 168, 184);
        Color silverD= new Color(120, 128, 145);

        g.setColor(base); g.fillRect(0, 0, 32, 32);

        // Ruin wall fragments
        g.setColor(stone);
        g.fillRect(2, 8, 12, 24);  // left wall
        g.fillRect(18, 12, 12, 20); // right wall

        // Wall top (broken)
        g.setColor(stoneD);
        int[] tx = {2, 6, 4, 10, 14, 12};
        int[] ty = {8, 4, 6, 2, 8, 8};
        g.fillPolygon(tx, ty, tx.length);

        // Mortar lines
        g.setColor(stoneD);
        for (int y = 12; y < 32; y += 4) {
            g.drawLine(2, y, 13, y); g.drawLine(18, y, 29, y);
        }

        // Silver patina streaks
        g.setColor(silver);
        g.drawLine(4, 10, 4, 30); g.drawLine(22, 14, 22, 30);
        g.setColor(silverD);
        g.drawLine(8, 12, 8, 28); g.drawLine(26, 16, 26, 30);

        // Archway between ruins
        g.setColor(stone);
        g.drawArc(6, 10, 20, 12, 0, 180);

        g.dispose(); return img;
    }

    /** Void trench -- absolute darkness, impassable chasm. */
    static BufferedImage voidTrench() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        Color void1  = new Color(4, 2, 8);
        Color void2  = new Color(8, 4, 14);
        Color edge   = new Color(20, 14, 32);
        Color deep   = new Color(2, 0, 4);

        g.setColor(void1); g.fillRect(0, 0, 32, 32);

        // Darker void center
        g.setColor(deep); g.fillOval(4, 4, 24, 24);

        // Edge gradient (slightly lighter rim)
        g.setColor(edge);
        g.drawRect(0, 0, 31, 31); g.drawRect(1, 1, 29, 29);

        // Void texture (near-invisible variation)
        rng.setSeed(608);
        for (int i = 0; i < 40; i++) {
            g.setColor(void2);
            g.fillRect(rng.nextInt(32), rng.nextInt(32), 1, 1);
        }

        // Faint silver stars in the void (distant memories)
        rng.setSeed(609);
        for (int i = 0; i < 4; i++) {
            g.setColor(new Color(100, 110, 130, 40));
            g.fillRect(4 + rng.nextInt(24), 4 + rng.nextInt(24), 1, 1);
        }

        g.dispose(); return img;
    }

    /** Memory ward -- glowing silver rune barrier, impassable. */
    static BufferedImage memoryWard() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        Color base  = new Color(18, 12, 30);
        Color rune  = new Color(160, 170, 200);
        Color runeB = new Color(200, 210, 240);
        Color aura  = new Color(120, 130, 170, 60);
        Color stone = new Color(30, 24, 42);

        g.setColor(base); g.fillRect(0, 0, 32, 32);

        // Stone texture underneath
        rng.setSeed(610);
        for (int i = 0; i < 40; i++) {
            g.setColor(stone);
            g.fillRect(rng.nextInt(32), rng.nextInt(32), 1 + rng.nextInt(2), 1);
        }

        // Ward glow aura
        g.setColor(aura);
        g.fillOval(2, 2, 28, 28);

        // Rune circle
        g.setColor(rune);
        g.setStroke(new BasicStroke(2));
        g.drawOval(4, 4, 24, 24);
        g.setStroke(new BasicStroke(1));

        // Inner ward symbol (eye / memory sigil)
        g.setColor(runeB);
        g.drawOval(10, 10, 12, 12);         // inner circle
        g.drawLine(16, 4, 16, 28);          // vertical axis
        g.drawLine(4, 16, 28, 16);          // horizontal axis

        // Center eye (the memory within)
        g.setColor(runeB);
        g.fillOval(13, 13, 6, 6);
        g.setColor(new Color(80, 200, 220));
        g.fillOval(14, 14, 4, 4);

        // Corner ward dots
        g.setColor(rune);
        g.fillRect(8, 8, 2, 2); g.fillRect(22, 8, 2, 2);
        g.fillRect(8, 22, 2, 2); g.fillRect(22, 22, 2, 2);

        g.dispose(); return img;
    }

    // == TOWN TILES ===========================================================

    /** Dust floor -- aged flagstone covered in silver-grey dust. */
    static BufferedImage dustFloor() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        Color base  = new Color(32, 28, 38);
        Color light = new Color(40, 36, 48);
        Color dark  = new Color(24, 20, 30);
        Color grout = new Color(18, 14, 24);
        Color dust  = new Color(130, 128, 120, 40);

        g.setColor(base); g.fillRect(0, 0, 32, 32);

        // Flagstone grid
        g.setColor(grout);
        for (int x = 0; x < 32; x += 8) g.drawLine(x, 0, x, 31);
        for (int y = 0; y < 32; y += 8) g.drawLine(0, y, 31, y);

        // Alternating tile brightness
        for (int tx = 0; tx < 4; tx++) {
            for (int ty = 0; ty < 4; ty++) {
                g.setColor((tx + ty) % 2 == 0 ? light : dark);
                g.fillRect(tx * 8 + 1, ty * 8 + 1, 7, 7);
            }
        }

        // Dust layer
        rng.setSeed(611);
        for (int i = 0; i < 50; i++) {
            g.setColor(dust);
            g.fillRect(rng.nextInt(32), rng.nextInt(32), 1 + rng.nextInt(2), 1);
        }

        g.dispose(); return img;
    }

    /** Memory crystal wall -- translucent crystalline wall with inner glow. */
    static BufferedImage memoryCrystalWall() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        Color base    = new Color(30, 28, 50);
        Color crystal = new Color(100, 105, 140);
        Color crystL  = new Color(140, 148, 180);
        Color crystD  = new Color(60, 62, 90);
        Color glow    = new Color(160, 170, 210, 80);
        Color bright  = new Color(200, 210, 240);

        g.setColor(base); g.fillRect(0, 0, 32, 32);

        // Crystal facets (vertical shards)
        g.setColor(crystal);
        g.fillRect(0, 0, 8, 32); g.fillRect(12, 0, 10, 32); g.fillRect(24, 0, 8, 32);
        g.setColor(crystD);
        g.fillRect(8, 0, 4, 32); g.fillRect(22, 0, 2, 32);

        // Light facet highlights
        g.setColor(crystL);
        g.fillRect(1, 0, 2, 32); g.fillRect(13, 0, 2, 32); g.fillRect(25, 0, 2, 32);

        // Inner glow
        g.setColor(glow);
        g.fillOval(6, 8, 20, 16);

        // Bright core point
        g.setColor(bright);
        g.fillRect(15, 14, 2, 2); g.fillRect(15, 15, 2, 2);

        // Crystal edge lines
        g.setColor(crystD);
        g.drawLine(0, 0, 31, 0); g.drawLine(0, 31, 31, 31);

        g.dispose(); return img;
    }

    /** Ghostlight lamp -- silver orb on a dark iron stand. */
    static BufferedImage ghostlightLamp() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        Color bg    = new Color(22, 16, 34);
        Color stand = new Color(50, 48, 58);
        Color orb   = new Color(160, 170, 200);
        Color glow  = new Color(200, 210, 240);
        Color aura  = new Color(140, 150, 190, 50);

        g.setColor(bg); g.fillRect(0, 0, 32, 32);

        // Glow aura
        g.setColor(aura);
        g.fillOval(4, 2, 24, 20);

        // Iron stand
        g.setColor(stand);
        g.fillRect(14, 18, 4, 12);
        g.fillRect(10, 29, 12, 3); // base

        // Orb bracket
        g.setColor(stand);
        g.fillRect(11, 14, 2, 4); g.fillRect(19, 14, 2, 4);

        // Silver orb
        g.setColor(orb);
        g.fillOval(10, 5, 12, 12);
        g.setColor(glow);
        g.fillOval(13, 8, 6, 6);

        // Bright center
        g.setColor(new Color(230, 235, 255));
        g.fillRect(15, 10, 2, 2);

        g.dispose(); return img;
    }

    /** Faded pillar -- crumbling stone column with silver veins. */
    static BufferedImage fadedPillar() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        Color bg     = new Color(22, 16, 34);
        Color stone  = new Color(65, 60, 75);
        Color stoneD = new Color(45, 40, 55);
        Color stoneL = new Color(85, 80, 95);
        Color silver = new Color(155, 162, 178);

        g.setColor(bg); g.fillRect(0, 0, 32, 32);

        // Column body
        g.setColor(stone); g.fillRect(8, 2, 16, 28);

        // Capital (top)
        g.setColor(stoneL); g.fillRect(6, 0, 20, 4);
        g.setColor(stoneD); g.drawLine(6, 4, 25, 4);

        // Base
        g.setColor(stoneL); g.fillRect(6, 28, 20, 4);
        g.setColor(stoneD); g.drawLine(6, 28, 25, 28);

        // Fluting lines
        g.setColor(stoneD);
        g.drawLine(12, 4, 12, 28); g.drawLine(16, 4, 16, 28);
        g.drawLine(20, 4, 20, 28);

        // Light edge
        g.setColor(stoneL);
        g.drawLine(9, 4, 9, 28);

        // Silver vein (cracked through column)
        g.setColor(silver);
        g.drawLine(14, 6, 15, 12); g.drawLine(15, 12, 13, 18);
        g.drawLine(13, 18, 16, 24);

        // Chip damage
        g.setColor(bg);
        g.fillRect(22, 10, 3, 3); g.fillRect(8, 20, 2, 4);

        g.dispose(); return img;
    }

    /** Echo tile -- floor tile that shimmers with faint silver ripples. */
    static BufferedImage echoTile() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        Color base   = new Color(28, 24, 40);
        Color tile   = new Color(36, 32, 50);
        Color silver = new Color(160, 168, 190, 50);
        Color ripple = new Color(180, 188, 210, 30);
        Color bright = new Color(200, 208, 230, 70);

        g.setColor(base); g.fillRect(0, 0, 32, 32);
        g.setColor(tile); g.fillRect(1, 1, 30, 30);

        // Border
        g.setColor(base);
        g.drawRect(0, 0, 31, 31);

        // Concentric echo ripples
        g.setColor(ripple);
        g.drawOval(6, 6, 20, 20);
        g.setColor(silver);
        g.drawOval(10, 10, 12, 12);
        g.setColor(bright);
        g.drawOval(14, 14, 4, 4);

        // Silver shimmer motes
        rng.setSeed(612);
        for (int i = 0; i < 10; i++) {
            g.setColor(silver);
            g.fillRect(rng.nextInt(30) + 1, rng.nextInt(30) + 1, 1, 1);
        }

        g.dispose(); return img;
    }

    // == MONSTER SPRITES ======================================================

    /** Memory Shade -- translucent humanoid ghost, psychic attacker. */
    static BufferedImage memoryShade() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        Color body   = new Color(80, 70, 120, 140);
        Color bodyL  = new Color(120, 110, 160, 120);
        Color eyes   = new Color(200, 210, 240);
        Color wisps  = new Color(140, 130, 180, 80);

        // Ghostly body (flowing robes)
        g.setColor(body);
        int[] bx = {10, 16, 22, 26, 28, 20, 16, 12, 4, 6};
        int[] by = {8, 6, 8, 16, 31, 31, 28, 31, 31, 16};
        g.fillPolygon(bx, by, bx.length);

        // Lighter inner body
        g.setColor(bodyL);
        g.fillOval(10, 10, 12, 16);

        // Head
        g.setColor(body);
        g.fillOval(11, 2, 10, 10);
        g.setColor(bodyL);
        g.fillOval(12, 3, 8, 8);

        // Eyes (bright silver, hollow)
        g.setColor(eyes);
        g.fillOval(13, 5, 3, 2); g.fillOval(18, 5, 3, 2);

        // Trailing wisps at bottom
        g.setColor(wisps);
        g.drawLine(6, 30, 2, 28); g.drawLine(12, 30, 8, 31);
        g.drawLine(20, 28, 22, 31); g.drawLine(26, 30, 30, 28);

        // Psychic aura particles
        rng.setSeed(613);
        for (int i = 0; i < 12; i++) {
            g.setColor(new Color(160, 150, 200, 40 + rng.nextInt(60)));
            g.fillRect(rng.nextInt(32), rng.nextInt(32), 1, 1);
        }

        g.dispose(); return img;
    }

    /** Hollow One -- featureless humanoid, identity erased. */
    static BufferedImage hollowOne() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        Color skin   = new Color(55, 50, 65);
        Color skinD  = new Color(35, 30, 45);
        Color skinL  = new Color(75, 70, 85);
        Color void_  = new Color(10, 6, 18);
        Color crack  = new Color(30, 25, 40);

        // Body (hunched, gaunt humanoid)
        g.setColor(skin);
        g.fillRect(10, 12, 12, 16); // torso

        // Head (smooth, featureless)
        g.setColor(skin);
        g.fillOval(11, 2, 10, 12);
        g.setColor(skinL);
        g.fillOval(12, 3, 8, 10);

        // NO eyes -- just a void where the face should be
        g.setColor(void_);
        g.fillOval(13, 5, 6, 5);

        // Arms (long, dangling)
        g.setColor(skin);
        g.fillRect(4, 14, 6, 14); g.fillRect(22, 14, 6, 14);
        g.setColor(skinD);
        g.drawLine(4, 27, 4, 14); g.drawLine(27, 27, 27, 14);

        // Fingers (clawed)
        g.setColor(skinL);
        g.drawLine(4, 28, 2, 31); g.drawLine(6, 28, 4, 31);
        g.drawLine(8, 28, 7, 31);
        g.drawLine(22, 28, 24, 31); g.drawLine(24, 28, 26, 31);
        g.drawLine(26, 28, 29, 31);

        // Legs (short, stumbling)
        g.setColor(skin);
        g.fillRect(10, 28, 5, 4); g.fillRect(17, 28, 5, 4);

        // Surface cracks (identity fractures)
        g.setColor(crack);
        g.drawLine(13, 14, 15, 20); g.drawLine(17, 16, 19, 22);

        g.dispose(); return img;
    }

    /** Echo Knight -- spectral warrior in ancient armor. */
    static BufferedImage echoKnight() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        Color armor  = new Color(100, 105, 130, 180);
        Color armorL = new Color(140, 148, 170, 180);
        Color armorD = new Color(65, 68, 90, 180);
        Color eye    = new Color(200, 210, 240);
        Color sword  = new Color(170, 178, 200);
        Color swordG = new Color(200, 210, 240);
        Color plume  = new Color(120, 100, 160, 120);

        // Body / breastplate
        g.setColor(armor); g.fillRect(8, 10, 16, 14);
        g.setColor(armorL); g.drawLine(8, 10, 23, 10);
        g.setColor(armorD); g.drawLine(8, 23, 23, 23);

        // Helmet
        g.setColor(armor); g.fillOval(10, 0, 12, 12);
        g.setColor(armorL); g.drawArc(10, 0, 12, 12, 45, 90);

        // Visor slit (eyes glow through)
        g.setColor(new Color(10, 8, 20));
        g.fillRect(12, 5, 8, 2);
        g.setColor(eye);
        g.fillRect(13, 5, 2, 2); g.fillRect(18, 5, 2, 2);

        // Plume
        g.setColor(plume);
        g.fillRect(15, 0, 2, 3);
        g.drawLine(15, 0, 12, 0); g.drawLine(17, 0, 20, 0);

        // Shield (left arm)
        g.setColor(armorD);
        g.fillOval(0, 10, 10, 14);
        g.setColor(armor);
        g.fillOval(1, 11, 8, 12);
        g.setColor(armorL);
        g.drawLine(5, 12, 5, 22); // shield boss line

        // Sword (right arm)
        g.setColor(sword);
        g.fillRect(26, 2, 2, 20);
        g.setColor(swordG);
        g.fillRect(27, 2, 1, 20);
        // Hilt
        g.setColor(armorD);
        g.fillRect(24, 20, 6, 2); // crossguard
        g.fillRect(26, 22, 2, 4); // grip

        // Legs (armored greaves)
        g.setColor(armor);
        g.fillRect(10, 24, 5, 8); g.fillRect(17, 24, 5, 8);
        g.setColor(armorD);
        g.drawLine(10, 31, 14, 31); g.drawLine(17, 31, 21, 31);

        g.dispose(); return img;
    }

    /** Archive Guardian -- crystallized memory construct, defensive. */
    static BufferedImage archiveGuardian() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        Color crystal = new Color(100, 108, 150);
        Color crystL  = new Color(150, 158, 200);
        Color crystD  = new Color(60, 65, 100);
        Color core    = new Color(200, 210, 240);
        Color glow    = new Color(140, 150, 200, 80);
        Color rune    = new Color(180, 190, 220);

        // Crystal body (angular, geometric)
        g.setColor(crystal);
        int[] bx = {8, 16, 24, 28, 24, 16, 8, 4};
        int[] by = {4, 0, 4, 16, 28, 31, 28, 16};
        g.fillPolygon(bx, by, bx.length);

        // Facet shading
        g.setColor(crystL);
        int[] lx = {8, 16, 16, 4};
        int[] ly = {4, 0, 16, 16};
        g.fillPolygon(lx, ly, lx.length);

        g.setColor(crystD);
        int[] dx = {24, 28, 24, 16};
        int[] dy = {4, 16, 28, 16};
        g.fillPolygon(dx, dy, dx.length);

        // Inner glow
        g.setColor(glow);
        g.fillOval(8, 8, 16, 16);

        // Core (bright eye-like center)
        g.setColor(core);
        g.fillOval(12, 12, 8, 8);
        g.setColor(new Color(220, 230, 255));
        g.fillOval(14, 14, 4, 4);

        // Floating rune fragments around body
        g.setColor(rune);
        g.fillRect(2, 6, 2, 2); g.fillRect(28, 6, 2, 2);
        g.fillRect(0, 16, 2, 2); g.fillRect(30, 16, 2, 2);
        g.fillRect(2, 26, 2, 2); g.fillRect(28, 26, 2, 2);

        // Crystal edge highlights
        g.setColor(crystL);
        g.drawLine(8, 4, 16, 0); g.drawLine(4, 16, 8, 28);

        g.dispose(); return img;
    }

    /** Grief Elemental -- swirling mass of concentrated sorrow. */
    static BufferedImage griefElemental() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        Color body   = new Color(40, 20, 60);
        Color bodyL  = new Color(60, 35, 85);
        Color bodyD  = new Color(25, 10, 40);
        Color tear   = new Color(120, 160, 200);
        Color tearB  = new Color(180, 200, 230);
        Color eye    = new Color(200, 80, 100);
        Color vortex = new Color(50, 25, 70, 120);

        // Swirling vortex body
        g.setColor(body);
        g.fillOval(2, 2, 28, 28);
        g.setColor(bodyL);
        g.fillOval(6, 6, 20, 20);

        // Dark vortex spirals
        g.setColor(bodyD);
        g.setStroke(new BasicStroke(2));
        g.drawArc(4, 4, 24, 24, 0, 270);
        g.drawArc(8, 8, 16, 16, 90, 270);
        g.setStroke(new BasicStroke(1));

        // Anguished face (in the vortex center)
        g.setColor(eye);
        g.fillOval(11, 10, 3, 4); g.fillOval(18, 10, 3, 4); // eyes (weeping)

        // Wailing mouth
        g.setColor(new Color(15, 5, 25));
        g.fillOval(12, 18, 8, 6);

        // Tear streaks
        g.setColor(tear);
        g.drawLine(12, 14, 10, 20); g.drawLine(20, 14, 22, 20);
        g.setColor(tearB);
        g.fillRect(10, 19, 1, 1); g.fillRect(22, 19, 1, 1);

        // Outer vortex tendrils
        g.setColor(vortex);
        g.drawLine(2, 16, 0, 10); g.drawLine(30, 16, 31, 22);
        g.drawLine(16, 2, 22, 0); g.drawLine(16, 30, 10, 31);

        // Sorrow particles
        rng.setSeed(614);
        for (int i = 0; i < 15; i++) {
            g.setColor(new Color(100, 130, 170, 30 + rng.nextInt(50)));
            g.fillRect(rng.nextInt(32), rng.nextInt(32), 1, 1);
        }

        g.dispose(); return img;
    }
}

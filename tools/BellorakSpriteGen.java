import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Random;

/**
 * One-shot sprite generator for Island 7 (Bellorak / The Golden War Isles).
 * Generates 32x32 PNG sprites for war-themed overworld, town, and monster tiles.
 *
 * Run from the project root:
 *   javac tools/BellorakSpriteGen.java -d tools/
 *   java -cp tools BellorakSpriteGen
 */
public class BellorakSpriteGen {

    static Random rng = new Random(77);

    static final String OW  = "src/main/resources/tiles/overworld/";
    static final String TW  = "src/main/resources/tiles/town/";
    static final String MON = "src/main/resources/tiles/monsters/";

    public static void main(String[] args) throws Exception {
        // Overworld tiles
        save(scorchedEarth(),    OW + "scorched_earth.png");
        save(trench(),           OW + "trench.png");
        save(ironBarricade(),    OW + "iron_barricade.png");
        save(crater(),           OW + "crater.png");
        save(warCampFloor(),     OW + "war_camp_floor.png");
        save(siegeWall(),        OW + "siege_wall.png");
        save(arenaStone(),       OW + "arena_stone.png");
        save(burningRuin(),      OW + "burning_ruin.png");

        // Town tiles
        save(warPlankFloor(),    TW + "war_plank_floor.png");
        save(ironWall(),         TW + "iron_wall.png");
        save(warBanner(),        TW + "war_banner.png");
        save(weaponRack(),       TW + "weapon_rack.png");
        save(ironLamp(),         TW + "iron_lamp.png");

        // Monster sprites
        save(warWraith(),        MON + "war_wraith.png");
        save(gloryHound(),       MON + "glory_hound.png");
        save(ironGolem(),        MON + "iron_golem.png");
        save(bloodOathBerserker(), MON + "blood_oath_berserker.png");
        save(theUndying(),       MON + "the_undying.png");

        System.out.println("Bellorak / Golden War Isles sprites generated: 18 files.");
    }

    static void save(BufferedImage img, String path) throws Exception {
        File f = new File(path);
        f.getParentFile().mkdirs();
        ImageIO.write(img, "PNG", f);
        System.out.println("  wrote " + path);
    }

    // == OVERWORLD TILES ======================================================

    /** Scorched earth -- charred, cracked war-blasted ground. */
    static BufferedImage scorchedEarth() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        Color base   = new Color(60, 40, 20);
        Color dark   = new Color(40, 25, 12);
        Color crack  = new Color(30, 18, 8);
        Color ember  = new Color(180, 80, 20, 60);

        g.setColor(base); g.fillRect(0, 0, 32, 32);

        // Charred texture
        rng.setSeed(701);
        for (int i = 0; i < 120; i++) {
            g.setColor(rng.nextBoolean() ? dark : new Color(50, 32, 16));
            g.fillRect(rng.nextInt(32), rng.nextInt(32), 1 + rng.nextInt(3), 1);
        }

        // Cracks
        g.setColor(crack);
        g.drawLine(4, 8, 20, 12); g.drawLine(20, 12, 28, 8);
        g.drawLine(10, 20, 24, 26); g.drawLine(14, 2, 16, 14);

        // Faint ember glow in cracks
        g.setColor(ember);
        g.fillRect(12, 10, 2, 1); g.fillRect(22, 24, 2, 1);

        g.dispose(); return img;
    }

    /** Trench -- dug fortification, dark and muddy. */
    static BufferedImage trench() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        Color ground = new Color(60, 40, 20);
        Color trenchBg = new Color(35, 22, 10);
        Color mud    = new Color(50, 35, 18);
        Color plank  = new Color(80, 60, 30);
        Color edge   = new Color(70, 50, 25);

        g.setColor(ground); g.fillRect(0, 0, 32, 32);

        // Trench channel (center horizontal)
        g.setColor(trenchBg); g.fillRect(0, 10, 32, 12);
        g.setColor(edge);
        g.drawLine(0, 10, 31, 10); g.drawLine(0, 21, 31, 21);

        // Mud texture inside
        rng.setSeed(702);
        for (int i = 0; i < 40; i++) {
            g.setColor(mud);
            g.fillRect(rng.nextInt(32), 11 + rng.nextInt(10), 2, 1);
        }

        // Plank supports
        g.setColor(plank);
        g.fillRect(6, 10, 2, 12); g.fillRect(16, 10, 2, 12);
        g.fillRect(26, 10, 2, 12);

        g.dispose(); return img;
    }

    /** Iron barricade -- rusted iron fortification. */
    static BufferedImage ironBarricade() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        Color bg    = new Color(60, 40, 20);
        Color iron  = new Color(100, 90, 80);
        Color ironD = new Color(70, 60, 50);
        Color rust  = new Color(130, 70, 30);
        Color rivet = new Color(140, 130, 110);

        g.setColor(bg); g.fillRect(0, 0, 32, 32);

        // Iron plates
        g.setColor(iron); g.fillRect(2, 4, 28, 24);
        g.setColor(ironD);
        g.drawLine(2, 4, 29, 4); g.drawLine(2, 27, 29, 27);
        g.drawLine(2, 16, 29, 16);

        // Rust patches
        g.setColor(rust);
        g.fillOval(6, 8, 8, 5); g.fillOval(20, 18, 6, 6);

        // Rivets
        g.setColor(rivet);
        for (int x = 5; x < 30; x += 7) {
            g.fillRect(x, 5, 2, 2); g.fillRect(x, 25, 2, 2);
        }

        // Spikes on top
        g.setColor(iron);
        for (int x = 4; x < 28; x += 6) {
            g.fillPolygon(new int[]{x, x+3, x+6}, new int[]{4, 0, 4}, 3);
        }

        g.dispose(); return img;
    }

    /** Crater -- explosion crater, dark center with raised rim. */
    static BufferedImage crater() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        Color ground = new Color(60, 40, 20);
        Color rim    = new Color(75, 55, 30);
        Color center = new Color(35, 22, 12);
        Color deep   = new Color(25, 15, 8);
        Color scorch = new Color(90, 50, 15, 80);

        g.setColor(ground); g.fillRect(0, 0, 32, 32);

        // Outer scorch ring
        g.setColor(scorch); g.fillOval(2, 2, 28, 28);

        // Rim
        g.setColor(rim); g.fillOval(4, 4, 24, 24);

        // Inner depression
        g.setColor(center); g.fillOval(8, 8, 16, 16);
        g.setColor(deep); g.fillOval(12, 12, 8, 8);

        g.dispose(); return img;
    }

    /** War camp floor -- packed earth with wooden planks. */
    static BufferedImage warCampFloor() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        Color base  = new Color(90, 75, 50);
        Color plank = new Color(100, 80, 45);
        Color plankD= new Color(75, 58, 30);
        Color nail  = new Color(130, 120, 100);

        g.setColor(base); g.fillRect(0, 0, 32, 32);

        // Horizontal planks
        for (int y = 0; y < 32; y += 8) {
            g.setColor(plank); g.fillRect(0, y, 32, 7);
            g.setColor(plankD); g.drawLine(0, y + 7, 31, y + 7);
            // Wood grain
            g.setColor(plankD);
            g.drawLine(2, y + 3, 30, y + 3);
        }

        // Nails
        g.setColor(nail);
        g.fillRect(4, 3, 1, 1); g.fillRect(28, 3, 1, 1);
        g.fillRect(4, 11, 1, 1); g.fillRect(28, 11, 1, 1);
        g.fillRect(4, 19, 1, 1); g.fillRect(28, 19, 1, 1);
        g.fillRect(4, 27, 1, 1); g.fillRect(28, 27, 1, 1);

        g.dispose(); return img;
    }

    /** Siege wall -- massive stone wall. */
    static BufferedImage siegeWall() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        Color stone  = new Color(80, 75, 70);
        Color stoneD = new Color(55, 50, 46);
        Color stoneL = new Color(100, 95, 88);
        Color mortar = new Color(40, 36, 32);

        g.setColor(stone); g.fillRect(0, 0, 32, 32);

        // Brick pattern
        g.setColor(mortar);
        for (int y = 0; y < 32; y += 8) {
            g.drawLine(0, y, 31, y);
            int off = (y / 8 % 2 == 0) ? 0 : 8;
            for (int x = off; x < 32; x += 16) g.drawLine(x, y, x, y + 7);
        }

        // Stone variation
        rng.setSeed(706);
        for (int i = 0; i < 40; i++) {
            g.setColor(rng.nextBoolean() ? stoneL : stoneD);
            g.fillRect(rng.nextInt(30) + 1, rng.nextInt(30) + 1, 3, 2);
        }

        // Crenellation top
        g.setColor(stoneL);
        for (int x = 0; x < 32; x += 8) g.fillRect(x, 0, 4, 3);

        g.dispose(); return img;
    }

    /** Arena stone -- polished golden stone floor. */
    static BufferedImage arenaStone() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        Color base  = new Color(140, 120, 70);
        Color light = new Color(160, 140, 85);
        Color dark  = new Color(110, 95, 55);
        Color grout = new Color(90, 75, 40);
        Color gold  = new Color(200, 170, 80, 60);

        g.setColor(base); g.fillRect(0, 0, 32, 32);

        // Large flagstone grid (4x4 tiles)
        g.setColor(grout);
        g.drawLine(16, 0, 16, 31); g.drawLine(0, 16, 31, 16);

        // Alternating brightness
        g.setColor(light); g.fillRect(1, 1, 15, 15); g.fillRect(17, 17, 15, 15);
        g.setColor(dark); g.fillRect(17, 1, 15, 15); g.fillRect(1, 17, 15, 15);

        // Gold sheen
        g.setColor(gold);
        g.fillOval(4, 4, 8, 8); g.fillOval(20, 20, 8, 8);

        g.dispose(); return img;
    }

    /** Burning ruin -- smoldering building. */
    static BufferedImage burningRuin() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        Color base   = new Color(60, 40, 20);
        Color stone  = new Color(70, 55, 40);
        Color char1  = new Color(30, 20, 10);
        Color flame  = new Color(220, 130, 30);
        Color flameD = new Color(180, 60, 15);
        Color smoke  = new Color(80, 70, 60, 100);

        g.setColor(base); g.fillRect(0, 0, 32, 32);

        // Ruined walls
        g.setColor(stone);
        g.fillRect(4, 12, 10, 20); g.fillRect(18, 16, 10, 16);

        // Charred beams
        g.setColor(char1);
        g.fillRect(6, 8, 3, 24); g.fillRect(22, 12, 3, 20);

        // Flames
        g.setColor(flameD);
        g.fillOval(8, 4, 8, 10); g.fillOval(20, 8, 8, 10);
        g.setColor(flame);
        g.fillOval(10, 5, 4, 6); g.fillOval(22, 9, 4, 6);

        // Flame tips
        g.setColor(new Color(255, 200, 60));
        g.fillRect(11, 4, 2, 3); g.fillRect(23, 8, 2, 3);

        // Smoke
        g.setColor(smoke);
        g.fillOval(6, 0, 12, 8); g.fillOval(18, 2, 10, 8);

        g.dispose(); return img;
    }

    // == TOWN TILES ===========================================================

    /** War plank floor -- rough wooden planks stained with mud and blood. */
    static BufferedImage warPlankFloor() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        Color base  = new Color(80, 60, 35);
        Color plank = new Color(90, 68, 40);
        Color gap   = new Color(50, 35, 18);
        Color stain = new Color(100, 30, 20, 60);
        Color mud   = new Color(60, 45, 25, 60);

        g.setColor(base); g.fillRect(0, 0, 32, 32);

        // Vertical planks
        for (int x = 0; x < 32; x += 8) {
            g.setColor(plank); g.fillRect(x, 0, 7, 32);
            g.setColor(gap); g.drawLine(x + 7, 0, x + 7, 31);
            // Grain
            g.setColor(new Color(70, 52, 28));
            g.drawLine(x + 3, 0, x + 3, 31);
        }

        // Blood stains
        g.setColor(stain);
        g.fillOval(4, 12, 6, 4); g.fillOval(18, 22, 8, 5);

        // Mud
        g.setColor(mud);
        g.fillOval(10, 2, 10, 4);

        g.dispose(); return img;
    }

    /** Iron wall -- riveted iron plate wall. */
    static BufferedImage ironWall() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        Color base  = new Color(70, 65, 60);
        Color light = new Color(85, 80, 74);
        Color dark  = new Color(50, 46, 42);
        Color rivet = new Color(130, 125, 115);
        Color rust  = new Color(110, 60, 25);

        g.setColor(base); g.fillRect(0, 0, 32, 32);

        // Plate seams
        g.setColor(dark);
        g.drawLine(0, 16, 31, 16); g.drawLine(16, 0, 16, 31);

        // Plate shading
        g.setColor(light);
        g.fillRect(1, 1, 15, 4); g.fillRect(17, 17, 14, 4);

        // Rivets
        g.setColor(rivet);
        for (int x = 3; x < 32; x += 8) {
            g.fillOval(x, 1, 2, 2); g.fillOval(x, 14, 2, 2);
            g.fillOval(x, 17, 2, 2); g.fillOval(x, 29, 2, 2);
        }

        // Rust spots
        g.setColor(rust);
        g.fillOval(8, 8, 4, 3); g.fillOval(22, 22, 5, 3);

        g.dispose(); return img;
    }

    /** War banner -- tattered red banner on a pole. */
    static BufferedImage warBanner() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        Color bg    = new Color(60, 40, 20);
        Color pole  = new Color(100, 90, 70);
        Color red   = new Color(160, 35, 30);
        Color redD  = new Color(120, 25, 20);
        Color gold  = new Color(200, 170, 80);

        g.setColor(bg); g.fillRect(0, 0, 32, 32);

        // Pole
        g.setColor(pole); g.fillRect(8, 0, 3, 32);

        // Banner fabric
        g.setColor(red);
        g.fillRect(11, 4, 16, 18);
        g.setColor(redD);
        // Tattered edge
        g.fillPolygon(new int[]{27, 25, 27, 23, 27}, new int[]{4, 10, 14, 18, 22}, 5);

        // Sigil (crossed swords)
        g.setColor(gold);
        g.drawLine(14, 8, 24, 18); g.drawLine(24, 8, 14, 18);
        g.fillRect(18, 7, 2, 2); // guard cross point

        // Shadow folds
        g.setColor(redD);
        g.drawLine(15, 4, 15, 22); g.drawLine(21, 4, 21, 22);

        g.dispose(); return img;
    }

    /** Weapon rack -- wall of swords, spears, axes. */
    static BufferedImage weaponRack() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        Color bg    = new Color(50, 38, 22);
        Color wood  = new Color(90, 70, 40);
        Color steel = new Color(160, 160, 165);
        Color steelD= new Color(110, 110, 118);
        Color grip  = new Color(80, 50, 25);

        g.setColor(bg); g.fillRect(0, 0, 32, 32);

        // Rack frame
        g.setColor(wood);
        g.fillRect(0, 8, 32, 3); g.fillRect(0, 22, 32, 3);

        // Sword 1
        g.setColor(steel); g.fillRect(5, 2, 2, 28);
        g.setColor(grip); g.fillRect(5, 22, 2, 6);
        g.setColor(steelD); g.fillRect(3, 22, 6, 1);

        // Axe
        g.setColor(steel);
        g.fillRect(14, 4, 2, 20);
        g.fillOval(10, 4, 10, 8);
        g.setColor(grip); g.fillRect(14, 20, 2, 8);

        // Spear
        g.setColor(steel);
        g.fillPolygon(new int[]{25, 27, 23}, new int[]{2, 8, 8}, 3);
        g.setColor(grip); g.fillRect(24, 8, 2, 22);

        g.dispose(); return img;
    }

    /** Iron lamp -- iron stand with war-gold flame. */
    static BufferedImage ironLamp() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        Color bg    = new Color(50, 38, 22);
        Color iron  = new Color(80, 75, 68);
        Color flame = new Color(200, 160, 60);
        Color flameD= new Color(180, 100, 30);
        Color glow  = new Color(200, 160, 60, 50);

        g.setColor(bg); g.fillRect(0, 0, 32, 32);

        // Glow aura
        g.setColor(glow); g.fillOval(6, 2, 20, 18);

        // Iron stand
        g.setColor(iron);
        g.fillRect(14, 16, 4, 14);
        g.fillRect(10, 29, 12, 3);

        // Lamp cage
        g.setColor(iron);
        g.drawOval(10, 6, 12, 14);
        g.drawLine(10, 13, 22, 13);

        // Flame
        g.setColor(flameD); g.fillOval(13, 7, 6, 8);
        g.setColor(flame); g.fillOval(14, 8, 4, 5);
        g.setColor(new Color(255, 220, 100));
        g.fillRect(15, 9, 2, 3);

        g.dispose(); return img;
    }

    // == MONSTER SPRITES ======================================================

    /** War Wraith -- ghostly soldier silhouette, red glow. */
    static BufferedImage warWraith() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        Color bg    = new Color(20, 15, 10);
        Color ghost = new Color(140, 60, 50, 160);
        Color ghostL= new Color(180, 80, 60, 120);
        Color eyes  = new Color(255, 100, 40);
        Color weapon= new Color(120, 115, 110);

        g.setColor(bg); g.fillRect(0, 0, 32, 32);

        // Spectral body
        g.setColor(ghost);
        g.fillOval(10, 2, 12, 12); // head
        g.fillRect(10, 12, 12, 14); // torso
        // Trailing wisps
        g.setColor(ghostL);
        g.fillOval(8, 22, 16, 10);
        g.fillOval(6, 26, 8, 6);
        g.fillOval(18, 26, 8, 6);

        // Red eyes
        g.setColor(eyes);
        g.fillRect(12, 6, 3, 2); g.fillRect(18, 6, 3, 2);

        // Ghostly sword
        g.setColor(weapon);
        g.fillRect(24, 4, 2, 20);
        g.fillRect(22, 14, 6, 1);

        g.dispose(); return img;
    }

    /** Glory Hound -- war-beast, fast and vicious, wolf-like. */
    static BufferedImage gloryHound() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        Color bg   = new Color(20, 15, 10);
        Color fur  = new Color(130, 70, 30);
        Color furD = new Color(90, 45, 18);
        Color eyes = new Color(255, 200, 40);
        Color teeth= new Color(220, 220, 210);
        Color armor= new Color(100, 90, 80);

        g.setColor(bg); g.fillRect(0, 0, 32, 32);

        // Body
        g.setColor(fur); g.fillOval(4, 12, 22, 14);
        g.setColor(furD); g.fillOval(6, 16, 18, 8);

        // Head
        g.setColor(fur); g.fillOval(20, 6, 12, 12);

        // Snout
        g.setColor(furD); g.fillOval(26, 10, 6, 6);

        // Teeth
        g.setColor(teeth);
        g.fillRect(28, 14, 3, 1); g.fillRect(29, 13, 2, 1);

        // Eyes
        g.setColor(eyes); g.fillRect(24, 8, 2, 2);

        // Iron armor plates
        g.setColor(armor);
        g.fillRect(8, 12, 14, 3);

        // Legs
        g.setColor(furD);
        g.fillRect(8, 24, 3, 8); g.fillRect(14, 24, 3, 8);
        g.fillRect(20, 24, 3, 8);

        g.dispose(); return img;
    }

    /** Iron Golem -- massive combat construct from recycled armor. */
    static BufferedImage ironGolem() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        Color bg    = new Color(20, 15, 10);
        Color iron  = new Color(100, 95, 88);
        Color ironD = new Color(65, 60, 55);
        Color ironL = new Color(140, 135, 125);
        Color eyes  = new Color(255, 140, 40);
        Color rust  = new Color(130, 65, 25);

        g.setColor(bg); g.fillRect(0, 0, 32, 32);

        // Torso (big blocky body)
        g.setColor(iron); g.fillRect(8, 8, 16, 16);
        g.setColor(ironD); g.drawRect(8, 8, 15, 15);

        // Head
        g.setColor(iron); g.fillRect(11, 1, 10, 8);
        g.setColor(ironL); g.fillRect(12, 2, 8, 2);

        // Eyes (glowing slits)
        g.setColor(eyes);
        g.fillRect(13, 4, 3, 2); g.fillRect(18, 4, 3, 2);

        // Arms
        g.setColor(iron);
        g.fillRect(2, 10, 6, 12); g.fillRect(24, 10, 6, 12);
        g.setColor(ironD);
        g.drawLine(5, 10, 5, 21); g.drawLine(27, 10, 27, 21);

        // Legs
        g.setColor(iron);
        g.fillRect(10, 24, 5, 8); g.fillRect(17, 24, 5, 8);

        // Rust patches
        g.setColor(rust);
        g.fillOval(10, 12, 5, 4); g.fillOval(18, 16, 4, 4);

        // Rivets
        g.setColor(ironL);
        g.fillRect(9, 9, 1, 1); g.fillRect(22, 9, 1, 1);
        g.fillRect(9, 22, 1, 1); g.fillRect(22, 22, 1, 1);

        g.dispose(); return img;
    }

    /** Blood Oath Berserker -- wild warrior, broken chains, red-tinted. */
    static BufferedImage bloodOathBerserker() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        Color bg    = new Color(20, 15, 10);
        Color skin  = new Color(160, 110, 70);
        Color skinD = new Color(120, 75, 45);
        Color blood = new Color(160, 30, 20);
        Color eyes  = new Color(255, 40, 40);
        Color chain = new Color(120, 115, 108);
        Color axe   = new Color(150, 145, 140);

        g.setColor(bg); g.fillRect(0, 0, 32, 32);

        // Body
        g.setColor(skin); g.fillRect(10, 10, 12, 14);
        g.setColor(skinD); g.fillRect(12, 14, 8, 6);

        // Head
        g.setColor(skin); g.fillOval(11, 1, 10, 10);

        // Wild hair
        g.setColor(new Color(60, 30, 15));
        g.fillOval(10, 0, 12, 6);

        // Glowing red eyes
        g.setColor(eyes);
        g.fillRect(13, 5, 2, 2); g.fillRect(18, 5, 2, 2);

        // Blood war-paint
        g.setColor(blood);
        g.drawLine(12, 3, 12, 9); g.drawLine(20, 3, 20, 9);
        g.fillRect(10, 12, 12, 2);

        // Arms
        g.setColor(skin);
        g.fillRect(4, 12, 6, 10); g.fillRect(22, 12, 6, 10);

        // Broken chains on wrists
        g.setColor(chain);
        g.fillRect(4, 20, 6, 2); g.fillRect(22, 20, 6, 2);
        g.fillRect(2, 22, 3, 4); g.fillRect(27, 22, 3, 4);

        // Legs
        g.setColor(skinD);
        g.fillRect(11, 24, 4, 8); g.fillRect(17, 24, 4, 8);

        // Dual axes
        g.setColor(axe);
        g.fillRect(2, 8, 2, 14);
        g.fillOval(0, 6, 6, 5);
        g.fillRect(28, 8, 2, 14);
        g.fillOval(26, 6, 6, 5);

        g.dispose(); return img;
    }

    /** The Undying -- skeletal arena champion with glowing golden eyes. */
    static BufferedImage theUndying() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        Color bg     = new Color(20, 15, 10);
        Color bone   = new Color(180, 170, 150);
        Color boneD  = new Color(130, 120, 100);
        Color armor  = new Color(120, 110, 80);
        Color armorD = new Color(80, 72, 50);
        Color eyes   = new Color(255, 200, 40);
        Color glow   = new Color(255, 200, 40, 60);
        Color sword  = new Color(170, 165, 155);

        g.setColor(bg); g.fillRect(0, 0, 32, 32);

        // Glow aura
        g.setColor(glow); g.fillOval(6, 0, 20, 20);

        // Skull head
        g.setColor(bone); g.fillOval(11, 1, 10, 10);
        g.setColor(boneD);
        g.fillRect(13, 7, 2, 2); // jaw detail
        g.fillRect(17, 7, 2, 2);

        // Eye sockets (glowing)
        g.setColor(eyes);
        g.fillRect(13, 3, 3, 3); g.fillRect(18, 3, 3, 3);

        // Armor torso
        g.setColor(armor); g.fillRect(9, 10, 14, 14);
        g.setColor(armorD);
        g.drawRect(9, 10, 13, 13);
        g.drawLine(16, 10, 16, 23);

        // Pauldrons
        g.setColor(armor);
        g.fillRect(5, 10, 5, 4); g.fillRect(22, 10, 5, 4);

        // Skeletal arms
        g.setColor(bone);
        g.fillRect(4, 14, 3, 10); g.fillRect(25, 14, 3, 10);

        // Legs
        g.setColor(armorD);
        g.fillRect(11, 24, 4, 8); g.fillRect(17, 24, 4, 8);

        // Greatsword
        g.setColor(sword);
        g.fillRect(26, 2, 2, 26);
        g.setColor(armor);
        g.fillRect(24, 14, 6, 2); // guard

        g.dispose(); return img;
    }
}

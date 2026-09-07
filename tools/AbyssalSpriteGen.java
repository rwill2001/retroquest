import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Random;

/**
 * One-shot sprite generator for Island 5 (Thalorax / The Abyssal Depths).
 * Generates 32x32 PNG sprites for deep ocean overworld, town, and monster tiles.
 *
 * Run from the project root:
 *   javac tools/AbyssalSpriteGen.java -d tools/
 *   java -cp tools AbyssalSpriteGen
 */
public class AbyssalSpriteGen {

    static Random rng = new Random(55);

    static final String OW  = "src/main/resources/tiles/overworld/";
    static final String TW  = "src/main/resources/tiles/town/";
    static final String MON = "src/main/resources/tiles/monsters/";

    public static void main(String[] args) throws Exception {
        // Overworld tiles
        save(deepOceanFloor(),      OW + "deep_ocean_floor.png");
        save(pressureVent(),        OW + "pressure_vent.png");
        save(coralReef(),           OW + "coral_reef.png");
        save(kelpForest(),          OW + "kelp_forest.png");
        save(bioluminescentSand(),  OW + "bioluminescent_sand.png");
        save(abyssalRock(),         OW + "abyssal_rock.png");
        save(boneField(),           OW + "bone_field.png");
        save(pressureWard(),        OW + "pressure_ward.png");

        // Town tiles
        save(domeFloor(),           TW + "dome_floor.png");
        save(coralWall(),           TW + "coral_wall.png");
        save(kelpPlatform(),        TW + "kelp_platform.png");
        save(pressureGlass(),       TW + "pressure_glass.png");
        save(bioluminescentLamp(),  TW + "bioluminescent_lamp.png");

        // Monster sprites
        save(pressureHulk(),        MON + "pressure_hulk.png");
        save(anglerfishHorror(),    MON + "anglerfish_horror.png");
        save(runeScarredShark(),    MON + "rune_scarred_shark.png");
        save(deepCoralConstruct(),  MON + "deep_coral_construct.png");
        save(abyssalWraith(),       MON + "abyssal_wraith.png");

        System.out.println("Abyssal Depths sprites generated: 18 files.");
    }

    static void save(BufferedImage img, String path) throws Exception {
        File f = new File(path);
        f.getParentFile().mkdirs();
        ImageIO.write(img, "PNG", f);
        System.out.println("  wrote " + path);
    }

    // == OVERWORLD TILES ======================================================

    /** Deep ocean floor — dark seabed with subtle sediment texture. */
    static BufferedImage deepOceanFloor() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        Color base   = new Color(8, 16, 30);
        Color sedim1 = new Color(12, 22, 38);
        Color sedim2 = new Color(6, 12, 24);
        Color spec   = new Color(20, 35, 55);

        g.setColor(base); g.fillRect(0, 0, 32, 32);

        // Sediment texture
        rng.setSeed(501);
        for (int i = 0; i < 100; i++) {
            g.setColor(rng.nextBoolean() ? sedim1 : sedim2);
            g.fillRect(rng.nextInt(32), rng.nextInt(32), 1 + rng.nextInt(2), 1);
        }

        // Faint specular highlights (distant bioluminescence reflections)
        rng.setSeed(502);
        for (int i = 0; i < 8; i++) {
            g.setColor(spec);
            g.fillRect(rng.nextInt(32), rng.nextInt(32), 1, 1);
        }

        g.dispose(); return img;
    }

    /** Pressure vent — volcanic vent with heat shimmer and orange glow. */
    static BufferedImage pressureVent() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        Color base   = new Color(8, 16, 30);
        Color rock   = new Color(30, 25, 20);
        Color rockD  = new Color(18, 14, 10);
        Color heat   = new Color(180, 80, 20);
        Color glow   = new Color(255, 140, 40);
        Color shimmer= new Color(60, 40, 20, 120);

        g.setColor(base); g.fillRect(0, 0, 32, 32);

        // Vent cone (rocky mound)
        g.setColor(rock);
        int[] vx = {6, 16, 26, 28, 4};
        int[] vy = {28, 10, 28, 31, 31};
        g.fillPolygon(vx, vy, 5);

        // Dark cracks on rock
        g.setColor(rockD);
        g.drawLine(10, 22, 14, 14); g.drawLine(18, 14, 22, 22);
        g.drawLine(12, 26, 16, 18); g.drawLine(16, 18, 20, 26);

        // Glowing vent opening at top
        g.setColor(heat); g.fillOval(12, 9, 8, 5);
        g.setColor(glow); g.fillOval(14, 10, 4, 3);

        // Rising heat shimmer particles
        rng.setSeed(503);
        for (int i = 0; i < 8; i++) {
            int sx = 12 + rng.nextInt(8), sy = rng.nextInt(10);
            g.setColor(shimmer);
            g.fillRect(sx, sy, 1, 2);
        }

        // Orange glow on surrounding seabed
        g.setColor(new Color(120, 50, 10, 60));
        g.fillOval(4, 24, 24, 8);

        g.dispose(); return img;
    }

    /** Coral reef — colorful coral formations, impassable barrier. */
    static BufferedImage coralReef() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        Color base   = new Color(8, 16, 30);
        Color coral1 = new Color(180, 60, 80);
        Color coral2 = new Color(80, 50, 160);
        Color coral3 = new Color(200, 120, 60);
        Color coral4 = new Color(60, 140, 130);
        Color high   = new Color(220, 160, 140);

        g.setColor(base); g.fillRect(0, 0, 32, 32);

        // Large coral branches
        g.setColor(coral1);
        g.fillOval(2, 14, 10, 16); g.fillOval(4, 10, 6, 8);
        g.setColor(coral2);
        g.fillOval(12, 8, 8, 22); g.fillOval(14, 4, 5, 8);
        g.setColor(coral3);
        g.fillOval(20, 12, 10, 18); g.fillOval(22, 8, 6, 8);
        g.setColor(coral4);
        g.fillOval(8, 18, 6, 12); g.fillOval(24, 16, 6, 6);

        // Branch tips / highlights
        g.setColor(high);
        rng.setSeed(504);
        for (int i = 0; i < 12; i++) {
            g.fillRect(2 + rng.nextInt(28), 6 + rng.nextInt(20), 1, 1);
        }

        // Texture dots on coral
        rng.setSeed(505);
        for (int i = 0; i < 30; i++) {
            Color c = switch (rng.nextInt(4)) {
                case 0 -> coral1; case 1 -> coral2; case 2 -> coral3; default -> coral4;
            };
            g.setColor(c);
            g.fillRect(rng.nextInt(32), 8 + rng.nextInt(22), 2, 2);
        }

        g.dispose(); return img;
    }

    /** Kelp forest — tall swaying kelp fronds on dark seabed. */
    static BufferedImage kelpForest() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        Color base  = new Color(8, 16, 30);
        Color kelpD = new Color(20, 60, 30);
        Color kelpM = new Color(30, 90, 45);
        Color kelpL = new Color(45, 120, 60);
        Color bulb  = new Color(60, 140, 70);

        g.setColor(base); g.fillRect(0, 0, 32, 32);

        // Kelp fronds (wavy vertical strands)
        int[] frondX = {4, 10, 16, 22, 28};
        for (int fx : frondX) {
            Color dark = kelpD, mid = kelpM;
            for (int y = 31; y >= 2; y--) {
                int xoff = (int)(2.0 * Math.sin(y * Math.PI / 10.0 + fx));
                g.setColor(dark);
                g.fillRect(fx + xoff, y, 2, 1);
                g.setColor(mid);
                g.fillRect(fx + xoff + 1, y, 1, 1);
            }
            // Leaf bulbs along strand
            g.setColor(bulb);
            g.fillOval(fx - 1, 6, 4, 3);
            g.fillOval(fx, 16, 3, 3);
        }

        // Light frond highlights
        g.setColor(kelpL);
        rng.setSeed(506);
        for (int i = 0; i < 15; i++) {
            g.fillRect(rng.nextInt(30), rng.nextInt(30), 1, 1);
        }

        g.dispose(); return img;
    }

    /** Bioluminescent sand — glowing sand patches with cyan-green light. */
    static BufferedImage bioluminescentSand() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        Color base  = new Color(15, 25, 40);
        Color sand  = new Color(25, 40, 50);
        Color sandL = new Color(35, 55, 65);
        Color glow  = new Color(40, 200, 180);
        Color glowB = new Color(60, 240, 210);
        Color glowD = new Color(20, 120, 100, 100);

        g.setColor(base); g.fillRect(0, 0, 32, 32);

        // Sandy ground texture
        rng.setSeed(507);
        for (int i = 0; i < 120; i++) {
            g.setColor(rng.nextBoolean() ? sand : sandL);
            g.fillRect(rng.nextInt(32), rng.nextInt(32), 1 + rng.nextInt(2), 1);
        }

        // Bioluminescent glow patches
        int[][] glows = {{6, 8}, {20, 5}, {14, 18}, {4, 24}, {26, 22}, {18, 28}};
        for (int[] gl : glows) {
            g.setColor(glowD);
            g.fillOval(gl[0] - 2, gl[1] - 2, 6, 6);
            g.setColor(glow);
            g.fillOval(gl[0], gl[1], 3, 2);
            g.setColor(glowB);
            g.fillRect(gl[0] + 1, gl[1], 1, 1);
        }

        g.dispose(); return img;
    }

    /** Abyssal rock — dark jagged rock formation, impassable. */
    static BufferedImage abyssalRock() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        Color base  = new Color(8, 16, 30);
        Color rock  = new Color(22, 28, 35);
        Color rockD = new Color(12, 16, 22);
        Color rockL = new Color(35, 42, 50);
        Color edge  = new Color(45, 55, 65);

        g.setColor(base); g.fillRect(0, 0, 32, 32);

        // Jagged rock mass
        int[] rx = {2, 8, 6, 14, 12, 20, 16, 26, 30, 28, 22, 10, 4};
        int[] ry = {28, 10, 6, 2, 8, 4, 12, 8, 28, 28, 28, 28, 28};
        g.setColor(rock); g.fillPolygon(rx, ry, rx.length);

        // Dark crevices
        g.setColor(rockD);
        g.drawLine(8, 12, 12, 20); g.drawLine(16, 10, 18, 22);
        g.drawLine(22, 12, 24, 24); g.drawLine(10, 8, 14, 16);

        // Light edges (top surfaces)
        g.setColor(edge);
        g.drawLine(2, 28, 8, 10); g.drawLine(8, 10, 6, 6);
        g.drawLine(12, 8, 14, 2); g.drawLine(16, 12, 20, 4);
        g.drawLine(20, 4, 26, 8);

        // Rock texture noise
        rng.setSeed(508);
        for (int i = 0; i < 40; i++) {
            g.setColor(rng.nextBoolean() ? rockD : rockL);
            g.fillRect(4 + rng.nextInt(24), 6 + rng.nextInt(20), 1, 1);
        }

        g.dispose(); return img;
    }

    /** Bone field — scattered leviathan bones on dark seabed. */
    static BufferedImage boneField() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        Color base  = new Color(10, 18, 32);
        Color sedim = new Color(14, 24, 38);
        Color bone  = new Color(180, 175, 160);
        Color boneD = new Color(130, 125, 110);
        Color boneL = new Color(210, 205, 190);

        g.setColor(base); g.fillRect(0, 0, 32, 32);

        // Sediment texture
        rng.setSeed(509);
        for (int i = 0; i < 60; i++) {
            g.setColor(sedim);
            g.fillRect(rng.nextInt(32), rng.nextInt(32), 1, 1);
        }

        // Large rib bones (curved)
        g.setColor(bone);
        g.setStroke(new BasicStroke(2));
        g.drawArc(2, 8, 14, 20, 30, 120);
        g.drawArc(16, 6, 14, 22, 20, 130);
        g.setStroke(new BasicStroke(1));

        // Vertebrae segments (horizontal)
        g.setColor(boneD);
        for (int x = 6; x < 28; x += 4) {
            g.fillOval(x, 20, 3, 2);
        }

        // Scattered bone fragments
        g.setColor(bone);
        g.drawLine(2, 4, 6, 6); g.drawLine(24, 2, 28, 5);
        g.drawLine(4, 28, 8, 30); g.drawLine(22, 28, 26, 26);

        // Skull fragment
        g.setColor(boneL);
        g.fillOval(12, 10, 6, 5);
        g.setColor(new Color(8, 16, 30));
        g.fillRect(13, 12, 1, 1); g.fillRect(16, 12, 1, 1); // eye sockets

        // Bone highlights
        rng.setSeed(510);
        for (int i = 0; i < 8; i++) {
            g.setColor(boneL);
            g.fillRect(rng.nextInt(30), rng.nextInt(30), 1, 1);
        }

        g.dispose(); return img;
    }

    /** Pressure ward — glowing rune circle on dark stone, impassable barrier. */
    static BufferedImage pressureWard() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        Color base  = new Color(12, 18, 28);
        Color stone = new Color(22, 28, 38);
        Color rune  = new Color(40, 160, 200);
        Color runeB = new Color(80, 220, 255);
        Color runeD = new Color(20, 80, 120, 120);

        g.setColor(base); g.fillRect(0, 0, 32, 32);

        // Stone texture
        rng.setSeed(511);
        for (int i = 0; i < 60; i++) {
            g.setColor(stone);
            g.fillRect(rng.nextInt(32), rng.nextInt(32), 1 + rng.nextInt(2), 1);
        }

        // Outer glow aura
        g.setColor(runeD);
        g.fillOval(2, 2, 28, 28);

        // Rune circle
        g.setColor(rune);
        g.setStroke(new BasicStroke(2));
        g.drawOval(4, 4, 24, 24);
        g.setStroke(new BasicStroke(1));

        // Inner ward symbol (cross-star pattern)
        g.setColor(runeB);
        g.drawLine(16, 6, 16, 26);  // vertical
        g.drawLine(6, 16, 26, 16);  // horizontal
        g.drawLine(9, 9, 23, 23);   // diagonal
        g.drawLine(23, 9, 9, 23);   // diagonal

        // Bright center point
        g.setColor(runeB);
        g.fillOval(14, 14, 4, 4);

        // Corner rune dots
        g.setColor(rune);
        g.fillRect(8, 8, 2, 2); g.fillRect(22, 8, 2, 2);
        g.fillRect(8, 22, 2, 2); g.fillRect(22, 22, 2, 2);

        g.dispose(); return img;
    }

    // == TOWN TILES ===========================================================

    /** Dome floor — polished blue-grey stone under pressure dome. */
    static BufferedImage domeFloor() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        Color base  = new Color(35, 45, 55);
        Color light = new Color(45, 58, 70);
        Color dark  = new Color(25, 32, 40);
        Color grout = new Color(20, 26, 34);

        g.setColor(base); g.fillRect(0, 0, 32, 32);

        // Tile grid (8x8 flagstone pattern)
        g.setColor(grout);
        for (int x = 0; x < 32; x += 8) g.drawLine(x, 0, x, 31);
        for (int y = 0; y < 32; y += 8) g.drawLine(0, y, 31, y);

        // Polished highlights on alternating tiles
        for (int tx = 0; tx < 4; tx++) {
            for (int ty = 0; ty < 4; ty++) {
                g.setColor((tx + ty) % 2 == 0 ? light : dark);
                g.fillRect(tx * 8 + 1, ty * 8 + 1, 7, 7);
            }
        }

        // Subtle surface reflections
        rng.setSeed(512);
        for (int i = 0; i < 20; i++) {
            g.setColor(light);
            g.fillRect(rng.nextInt(32), rng.nextInt(32), 1, 1);
        }

        g.dispose(); return img;
    }

    /** Coral wall — living coral wall, not walkable. */
    static BufferedImage coralWall() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        Color base   = new Color(120, 45, 60);
        Color coral1 = new Color(150, 60, 80);
        Color coral2 = new Color(100, 35, 50);
        Color polyp  = new Color(180, 90, 110);
        Color high   = new Color(200, 130, 140);

        g.setColor(base); g.fillRect(0, 0, 32, 32);

        // Coral bumps covering surface
        rng.setSeed(513);
        for (int i = 0; i < 25; i++) {
            int cx = rng.nextInt(28), cy = rng.nextInt(28);
            int r = 2 + rng.nextInt(4);
            g.setColor(rng.nextBoolean() ? coral1 : coral2);
            g.fillOval(cx, cy, r, r);
        }

        // Polyp details (tiny bright dots)
        rng.setSeed(514);
        for (int i = 0; i < 20; i++) {
            g.setColor(polyp);
            g.fillRect(rng.nextInt(32), rng.nextInt(32), 1, 1);
        }

        // Top-edge highlights
        g.setColor(high);
        rng.setSeed(515);
        for (int i = 0; i < 10; i++) {
            g.fillRect(rng.nextInt(32), rng.nextInt(32), 1, 1);
        }

        g.dispose(); return img;
    }

    /** Kelp platform — woven kelp forming a flat walkable surface. */
    static BufferedImage kelpPlatform() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        Color base  = new Color(25, 65, 35);
        Color weave = new Color(35, 85, 45);
        Color weave2= new Color(20, 55, 28);
        Color gap   = new Color(8, 20, 30);

        g.setColor(base); g.fillRect(0, 0, 32, 32);

        // Horizontal kelp strands
        for (int y = 0; y < 32; y += 4) {
            g.setColor(weave);
            g.fillRect(0, y, 32, 2);
            g.setColor(weave2);
            g.fillRect(0, y + 2, 32, 1);
        }

        // Vertical cross-weave
        for (int x = 0; x < 32; x += 6) {
            for (int y = 0; y < 32; y += 4) {
                g.setColor(weave2);
                g.fillRect(x, y, 2, 4);
            }
        }

        // Gaps showing water below
        rng.setSeed(516);
        for (int i = 0; i < 12; i++) {
            g.setColor(gap);
            g.fillRect(rng.nextInt(30), rng.nextInt(30), 1, 1);
        }

        g.dispose(); return img;
    }

    /** Pressure glass — transparent dome panel showing deep water beyond. */
    static BufferedImage pressureGlass() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        Color deep   = new Color(6, 12, 25);
        Color glass  = new Color(30, 60, 80, 140);
        Color frame  = new Color(50, 55, 65);
        Color gleam  = new Color(80, 140, 180, 160);
        Color bubble = new Color(60, 120, 160, 100);

        g.setColor(deep); g.fillRect(0, 0, 32, 32);

        // Glass panel (semi-transparent)
        g.setColor(glass); g.fillRect(2, 2, 28, 28);

        // Metal frame
        g.setColor(frame);
        g.fillRect(0, 0, 32, 2); g.fillRect(0, 30, 32, 2);
        g.fillRect(0, 0, 2, 32); g.fillRect(30, 0, 2, 32);
        // Cross brace
        g.drawLine(16, 2, 16, 30); g.drawLine(2, 16, 30, 16);

        // Light gleam (diagonal reflection)
        g.setColor(gleam);
        g.drawLine(4, 4, 12, 12); g.drawLine(5, 4, 13, 12);

        // Distant bubbles outside
        rng.setSeed(517);
        for (int i = 0; i < 6; i++) {
            g.setColor(bubble);
            int bx = 4 + rng.nextInt(24), by = 4 + rng.nextInt(24);
            g.drawOval(bx, by, 2, 2);
        }

        g.dispose(); return img;
    }

    /** Bioluminescent lamp — glowing cyan orb on a coral stand. */
    static BufferedImage bioluminescentLamp() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        Color bg    = new Color(15, 25, 40);
        Color stand = new Color(100, 55, 70);
        Color orb   = new Color(30, 180, 200);
        Color glow  = new Color(80, 240, 255);
        Color aura  = new Color(30, 140, 160, 60);

        g.setColor(bg); g.fillRect(0, 0, 32, 32);

        // Glow aura
        g.setColor(aura);
        g.fillOval(4, 2, 24, 20);

        // Coral stand
        g.setColor(stand);
        g.fillRect(14, 18, 4, 12);
        g.fillRect(10, 29, 12, 3); // base

        // Orb bracket
        g.setColor(stand);
        g.fillRect(11, 14, 2, 4); g.fillRect(19, 14, 2, 4);

        // Glowing orb
        g.setColor(orb);
        g.fillOval(10, 5, 12, 12);
        g.setColor(glow);
        g.fillOval(13, 8, 6, 6);

        // Bright center
        g.setColor(new Color(200, 255, 255));
        g.fillRect(15, 10, 2, 2);

        g.dispose(); return img;
    }

    // == MONSTER SPRITES ======================================================

    /** Pressure Hulk — compressed armored deep creature, heavy and slow. */
    static BufferedImage pressureHulk() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        Color armor  = new Color(40, 50, 65);
        Color armorL = new Color(55, 68, 85);
        Color armorD = new Color(25, 32, 42);
        Color eye    = new Color(40, 200, 220);
        Color crack  = new Color(30, 160, 180, 150);

        // Massive compressed body
        g.setColor(armor); g.fillRect(4, 6, 24, 22);
        g.setColor(armorL); g.drawLine(4, 6, 27, 6);
        g.setColor(armorD); g.drawLine(4, 27, 27, 27);

        // Head (small, hunched into body)
        g.setColor(armor); g.fillRect(10, 1, 12, 7);
        g.setColor(armorL); g.drawLine(10, 1, 21, 1);

        // Eyes (glowing slits)
        g.setColor(eye);
        g.fillRect(12, 3, 3, 2); g.fillRect(18, 3, 3, 2);

        // Pressure cracks glowing on body
        g.setColor(crack);
        g.drawLine(8, 10, 12, 18); g.drawLine(12, 18, 8, 24);
        g.drawLine(20, 10, 24, 16); g.drawLine(24, 16, 20, 24);
        g.drawLine(14, 12, 18, 20);

        // Armor plate texture
        rng.setSeed(518);
        for (int i = 0; i < 25; i++) {
            g.setColor(rng.nextBoolean() ? armorD : armorL);
            g.fillRect(5 + rng.nextInt(22), 7 + rng.nextInt(20), 1, 1);
        }

        // Thick arms
        g.setColor(armor);
        g.fillRect(0, 8, 4, 14); g.fillRect(28, 8, 4, 14);
        g.setColor(armorD);
        g.drawLine(0, 21, 3, 21); g.drawLine(28, 21, 31, 21);

        // Fists (claw-like)
        g.setColor(armorL);
        g.fillRect(0, 22, 5, 4); g.fillRect(27, 22, 5, 4);

        // Legs (short, wide)
        g.setColor(armor);
        g.fillRect(6, 28, 8, 4); g.fillRect(18, 28, 8, 4);

        g.dispose(); return img;
    }

    /** Anglerfish Horror — massive deep-sea fish with bioluminescent lure. */
    static BufferedImage anglerfishHorror() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        Color body  = new Color(20, 28, 40);
        Color bodyL = new Color(30, 40, 55);
        Color belly = new Color(35, 45, 60);
        Color eye   = new Color(200, 200, 40);
        Color lure  = new Color(80, 240, 255);
        Color lureG = new Color(40, 160, 200, 80);
        Color teeth = new Color(200, 200, 190);
        Color fin   = new Color(25, 35, 50);

        // Body (large oval, facing left)
        g.setColor(body); g.fillOval(2, 8, 26, 20);
        g.setColor(bodyL); g.fillOval(4, 10, 22, 14);
        g.setColor(belly); g.fillOval(6, 16, 18, 10);

        // Gaping jaw
        g.setColor(new Color(10, 5, 15));
        g.fillOval(0, 14, 14, 10);

        // Teeth (jagged)
        g.setColor(teeth);
        g.fillRect(2, 14, 1, 2); g.fillRect(5, 13, 1, 2);
        g.fillRect(8, 14, 1, 2); g.fillRect(11, 13, 1, 2);
        g.fillRect(2, 22, 1, 2); g.fillRect(5, 23, 1, 2);
        g.fillRect(8, 22, 1, 2); g.fillRect(11, 23, 1, 2);

        // Eye (large, menacing)
        g.setColor(eye); g.fillOval(14, 10, 5, 4);
        g.setColor(new Color(40, 40, 10));
        g.fillRect(16, 11, 2, 2); // pupil

        // Lure antenna
        g.setColor(body);
        g.setStroke(new BasicStroke(1));
        g.drawLine(8, 8, 4, 2); g.drawLine(4, 2, 6, 0);

        // Lure glow
        g.setColor(lureG); g.fillOval(2, 0, 8, 6);
        g.setColor(lure); g.fillOval(4, 1, 4, 3);
        g.setColor(new Color(200, 255, 255));
        g.fillRect(5, 2, 2, 1);

        // Tail fin
        g.setColor(fin);
        int[] tx = {26, 31, 31, 26};
        int[] ty = {14, 8, 28, 22};
        g.fillPolygon(tx, ty, 4);

        // Dorsal fin
        g.setColor(fin);
        int[] dx = {14, 18, 22, 20, 16};
        int[] dy = {8, 4, 8, 8, 8};
        g.fillPolygon(dx, dy, 5);

        g.dispose(); return img;
    }

    /** Rune-Scarred Shark — shark with glowing eldritch runes on its body. */
    static BufferedImage runeScarredShark() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        Color body  = new Color(45, 55, 70);
        Color bodyL = new Color(60, 72, 88);
        Color belly = new Color(80, 90, 100);
        Color eye   = new Color(255, 60, 40);
        Color rune  = new Color(60, 200, 220);
        Color runeB = new Color(100, 240, 255);
        Color teeth = new Color(210, 210, 200);
        Color fin   = new Color(35, 42, 55);

        // Shark body (streamlined oval, facing left)
        g.setColor(body); g.fillOval(0, 10, 28, 14);
        g.setColor(bodyL); g.fillOval(2, 12, 24, 8);
        g.setColor(belly); g.fillOval(4, 16, 20, 6);

        // Snout
        g.setColor(body);
        int[] sx = {0, 0, 6};
        int[] sy = {14, 20, 17};
        g.fillPolygon(sx, sy, 3);

        // Mouth / teeth
        g.setColor(new Color(10, 10, 15));
        g.drawLine(1, 17, 8, 17);
        g.setColor(teeth);
        g.fillRect(2, 16, 1, 1); g.fillRect(4, 16, 1, 1); g.fillRect(6, 16, 1, 1);
        g.fillRect(2, 18, 1, 1); g.fillRect(4, 18, 1, 1); g.fillRect(6, 18, 1, 1);

        // Eye (glowing red)
        g.setColor(eye); g.fillOval(7, 12, 3, 3);
        g.setColor(new Color(255, 200, 180));
        g.fillRect(8, 13, 1, 1);

        // Dorsal fin
        g.setColor(fin);
        int[] dx = {12, 16, 20, 18, 14};
        int[] dy = {10, 3, 10, 10, 10};
        g.fillPolygon(dx, dy, 5);

        // Tail fin
        g.setColor(fin);
        int[] tx = {26, 31, 31, 26};
        int[] ty = {13, 8, 26, 21};
        g.fillPolygon(tx, ty, 4);

        // Pectoral fins
        g.setColor(fin);
        g.fillOval(10, 22, 8, 4);

        // Glowing rune scars
        g.setColor(rune);
        g.drawLine(10, 14, 14, 12); g.drawLine(14, 12, 12, 16);
        g.drawLine(16, 14, 20, 12); g.drawLine(20, 12, 18, 18);
        g.drawLine(22, 14, 24, 16);
        g.setColor(runeB);
        g.fillRect(14, 12, 1, 1); g.fillRect(20, 12, 1, 1);

        // Rune glow dots
        rng.setSeed(519);
        for (int i = 0; i < 6; i++) {
            g.setColor(rune);
            g.fillRect(8 + rng.nextInt(16), 11 + rng.nextInt(8), 1, 1);
        }

        g.dispose(); return img;
    }

    /** Deep Coral Construct — animated coral guardian, humanoid shape. */
    static BufferedImage deepCoralConstruct() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        Color coral  = new Color(140, 55, 70);
        Color coralL = new Color(170, 80, 95);
        Color coralD = new Color(100, 35, 48);
        Color eye    = new Color(40, 220, 200);
        Color moss   = new Color(30, 100, 80);
        Color glow   = new Color(60, 180, 160, 100);

        // Head (rough coral dome)
        g.setColor(coral); g.fillOval(10, 0, 12, 10);
        g.setColor(coralL); g.fillOval(12, 1, 8, 6);

        // Eyes
        g.setColor(eye);
        g.fillRect(13, 4, 2, 2); g.fillRect(18, 4, 2, 2);

        // Torso (coral mass)
        g.setColor(coral); g.fillRect(7, 10, 18, 12);
        g.setColor(coralD);
        g.drawLine(7, 21, 24, 21);

        // Coral texture on torso
        rng.setSeed(520);
        for (int i = 0; i < 20; i++) {
            g.setColor(rng.nextBoolean() ? coralL : coralD);
            g.fillOval(8 + rng.nextInt(16), 11 + rng.nextInt(10), 2, 2);
        }

        // Moss/algae patches
        g.setColor(moss);
        g.fillRect(9, 14, 3, 2); g.fillRect(20, 16, 3, 2);

        // Arms (branching coral)
        g.setColor(coral);
        g.fillRect(1, 11, 6, 8); g.fillRect(25, 11, 6, 8);
        // Branch tips
        g.setColor(coralL);
        g.fillRect(0, 18, 3, 3); g.fillRect(29, 18, 3, 3);
        g.fillRect(0, 11, 2, 3); g.fillRect(30, 11, 2, 3);

        // Legs (thick coral pillars)
        g.setColor(coral);
        g.fillRect(9, 22, 6, 10); g.fillRect(17, 22, 6, 10);
        g.setColor(coralD);
        g.drawLine(9, 31, 14, 31); g.drawLine(17, 31, 22, 31);

        // Inner glow (magical core visible through cracks)
        g.setColor(glow);
        g.fillOval(13, 13, 6, 6);

        g.dispose(); return img;
    }

    /** Abyssal Wraith — ghostly leviathan spirit, translucent and terrifying. */
    static BufferedImage abyssalWraith() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        Color ghostD = new Color(15, 40, 60, 180);
        Color ghostM = new Color(25, 60, 90, 150);
        Color ghostL = new Color(40, 90, 130, 120);
        Color eye    = new Color(200, 255, 240);
        Color eyeD   = new Color(100, 200, 180);
        Color wisp   = new Color(30, 80, 120, 80);
        Color teeth  = new Color(160, 200, 220, 180);

        // Ghostly body mass (amorphous, flowing)
        g.setColor(ghostD); g.fillOval(4, 2, 24, 20);
        g.setColor(ghostM); g.fillOval(7, 4, 18, 14);
        g.setColor(ghostL); g.fillOval(10, 6, 12, 8);

        // Spectral tail wisps (trailing down)
        g.setColor(ghostD);
        g.fillRect(8, 20, 4, 8); g.fillRect(14, 20, 4, 10);
        g.fillRect(20, 20, 4, 8);
        g.setColor(ghostM);
        g.fillRect(9, 22, 3, 6); g.fillRect(15, 23, 3, 7);
        g.fillRect(21, 22, 3, 6);

        // Wispy tendrils fading out
        g.setColor(wisp);
        g.fillRect(6, 26, 2, 6); g.fillRect(24, 26, 2, 6);
        g.fillRect(12, 28, 2, 4); g.fillRect(18, 29, 2, 3);

        // Hollow eyes (large, glowing)
        g.setColor(eyeD); g.fillOval(10, 7, 5, 5); g.fillOval(18, 7, 5, 5);
        g.setColor(eye); g.fillOval(11, 8, 3, 3); g.fillOval(19, 8, 3, 3);

        // Gaping spectral mouth
        g.setColor(new Color(5, 15, 25, 200));
        g.fillOval(12, 14, 8, 5);
        // Ghost teeth
        g.setColor(teeth);
        g.fillRect(13, 14, 1, 1); g.fillRect(15, 14, 1, 1);
        g.fillRect(17, 14, 1, 1); g.fillRect(19, 14, 1, 1);

        // Spectral particle aura
        rng.setSeed(521);
        for (int i = 0; i < 15; i++) {
            g.setColor(wisp);
            g.fillRect(rng.nextInt(32), rng.nextInt(32), 1, 1);
        }

        g.dispose(); return img;
    }
}

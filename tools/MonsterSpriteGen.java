import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Random;

/**
 * One-shot sprite generator for the 6 missing monster sprites.
 * All 32x32 PNG, pixel-art style matching existing monsters.
 *
 * Run from the project root:
 *   javac tools/MonsterSpriteGen.java -d tools/
 *   java -cp tools MonsterSpriteGen
 */
public class MonsterSpriteGen {

    static Random rng = new Random(42);
    static final String MON = "src/main/resources/tiles/monsters/";

    public static void main(String[] args) throws Exception {
        save(darkMage(),           MON + "dark_mage.png");
        save(goblinSorcerer(),     MON + "goblin_sorcerer.png");
        save(necromancer(),        MON + "necromancer.png");
        save(lich(),               MON + "lich.png");
        save(forestFairy(),        MON + "forest_fairy.png");
        save(wanderingMerchant(),  MON + "wandering_merchant.png");

        System.out.println("Monster sprites generated: 6 files.");
    }

    static void save(BufferedImage img, String path) throws Exception {
        File f = new File(path);
        f.getParentFile().mkdirs();
        ImageIO.write(img, "PNG", f);
        System.out.println("  wrote " + path);
    }

    // ── MONSTER SPRITES ──────────────────────────────────────────────────────

    /** Dark Mage — Tier 3 (L16-20). Hooded figure in dark purple robes, staff, glowing eyes. */
    static BufferedImage darkMage() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        Color robe   = new Color(45, 20, 65);
        Color robeL  = new Color(65, 35, 90);
        Color robeD  = new Color(30, 12, 45);
        Color skin   = new Color(140, 120, 100);
        Color eye    = new Color(180, 50, 220);
        Color eyeG   = new Color(220, 120, 255);
        Color staff  = new Color(80, 55, 35);
        Color orb    = new Color(160, 40, 200);
        Color orbG   = new Color(220, 140, 255);

        // Hood
        g.setColor(robe);  g.fillOval(10, 1, 12, 12);
        g.setColor(robeD); g.fillOval(12, 3, 8, 7);

        // Glowing eyes in shadow
        g.setColor(eye);  g.fillRect(13, 6, 2, 2); g.fillRect(17, 6, 2, 2);
        g.setColor(eyeG); g.fillRect(14, 6, 1, 1); g.fillRect(18, 6, 1, 1);

        // Robe body
        g.setColor(robe);
        g.fillRect(10, 12, 12, 12);
        // Robe flare at bottom
        int[] bx = {8, 10, 22, 24, 22, 16, 10};
        int[] by = {28, 24, 24, 28, 31, 27, 31};
        g.fillPolygon(bx, by, 7);

        // Robe lighter front panel
        g.setColor(robeL);
        g.fillRect(13, 13, 6, 11);

        // Belt / sash
        g.setColor(new Color(100, 60, 30));
        g.fillRect(10, 18, 12, 2);

        // Hands (reaching out from robe)
        g.setColor(skin);
        g.fillRect(7, 17, 3, 3);
        g.fillRect(22, 17, 3, 3);

        // Staff in right hand
        g.setColor(staff);
        g.fillRect(24, 4, 2, 22);
        // Staff top orb
        g.setColor(orb);  g.fillOval(23, 1, 4, 4);
        g.setColor(orbG); g.fillRect(24, 2, 2, 2);

        // Dark energy particles
        rng.setSeed(401);
        g.setColor(new Color(160, 40, 200, 100));
        for (int i = 0; i < 8; i++) {
            g.fillRect(rng.nextInt(30) + 1, rng.nextInt(30) + 1, 1, 1);
        }

        g.dispose(); return img;
    }

    /** Goblin Sorcerer — Tier 1 (L6-10). Small green goblin with oversized hat and sparks. */
    static BufferedImage goblinSorcerer() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        Color skinD  = new Color(50, 100, 35);
        Color skin   = new Color(70, 130, 50);
        Color skinL  = new Color(90, 155, 65);
        Color hat    = new Color(55, 30, 80);
        Color hatL   = new Color(75, 45, 105);
        Color eye    = new Color(255, 200, 30);
        Color pupil  = new Color(20, 10, 5);
        Color robe   = new Color(60, 35, 85);
        Color spark  = new Color(255, 240, 100);
        Color sparkD = new Color(200, 160, 40);

        // Pointy wizard hat (oversized for comedic effect)
        g.setColor(hat);
        int[] hx = {16, 8, 24};
        int[] hy = {0, 12, 12};
        g.fillPolygon(hx, hy, 3);
        g.setColor(hatL); g.drawLine(16, 1, 11, 10);
        // Hat brim
        g.setColor(hat); g.fillRect(6, 12, 20, 3);

        // Head
        g.setColor(skin);  g.fillOval(10, 13, 12, 10);
        g.setColor(skinL); g.fillOval(12, 14, 8, 7);

        // Big ears
        g.setColor(skinD);
        g.fillOval(6, 15, 5, 4);  // left ear
        g.fillOval(21, 15, 5, 4); // right ear
        g.setColor(skin);
        g.fillOval(7, 16, 3, 2); g.fillOval(22, 16, 3, 2);

        // Eyes (big, yellow)
        g.setColor(eye);   g.fillRect(12, 17, 3, 3); g.fillRect(17, 17, 3, 3);
        g.setColor(pupil); g.fillRect(13, 18, 1, 1); g.fillRect(18, 18, 1, 1);

        // Mouth (toothy grin)
        g.setColor(new Color(30, 15, 10));
        g.drawLine(13, 21, 19, 21);
        g.setColor(new Color(220, 215, 200));
        g.fillRect(14, 21, 1, 1); g.fillRect(17, 21, 1, 1); // fangs

        // Small robe body
        g.setColor(robe);
        g.fillRect(11, 23, 10, 6);
        // Bottom tatter
        g.setColor(hat);
        g.fillRect(11, 28, 3, 2); g.fillRect(15, 29, 2, 2); g.fillRect(19, 28, 2, 2);

        // Arms (holding up hands with magic)
        g.setColor(skin);
        g.fillRect(7, 24, 4, 2);  // left arm
        g.fillRect(21, 24, 4, 2); // right arm
        // Hands
        g.fillRect(5, 23, 3, 3);
        g.fillRect(24, 23, 3, 3);

        // Magic sparks around hands
        g.setColor(spark);
        g.fillRect(4, 21, 1, 1); g.fillRect(7, 21, 1, 1); g.fillRect(5, 25, 1, 1);
        g.fillRect(25, 21, 1, 1); g.fillRect(27, 22, 1, 1); g.fillRect(26, 25, 1, 1);
        g.setColor(sparkD);
        g.fillRect(3, 22, 1, 1); g.fillRect(28, 23, 1, 1);

        g.dispose(); return img;
    }

    /** Necromancer — Tier 5 (L26-30). Dark-robed mage with skull staff and green death magic. */
    static BufferedImage necromancer() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        Color robe  = new Color(25, 25, 30);
        Color robeL = new Color(40, 40, 48);
        Color robeD = new Color(15, 15, 18);
        Color skin  = new Color(160, 155, 140);
        Color skinD = new Color(120, 115, 100);
        Color eye   = new Color(80, 220, 60);
        Color eyeG  = new Color(140, 255, 100);
        Color bone  = new Color(200, 190, 170);
        Color boneD = new Color(160, 150, 130);
        Color magic = new Color(60, 200, 40, 140);
        Color staff = new Color(50, 40, 30);

        // Hood (deep, shadowed)
        g.setColor(robe);  g.fillOval(9, 1, 14, 14);
        g.setColor(robeD); g.fillOval(11, 3, 10, 9);

        // Gaunt face visible
        g.setColor(skinD); g.fillOval(12, 4, 8, 8);
        g.setColor(skin);  g.fillOval(13, 5, 6, 6);

        // Sunken green eyes
        g.setColor(eye);  g.fillRect(14, 7, 2, 2); g.fillRect(18, 7, 2, 2);
        g.setColor(eyeG); g.fillRect(14, 7, 1, 1); g.fillRect(18, 7, 1, 1);

        // Thin mouth
        g.setColor(robeD); g.drawLine(15, 10, 18, 10);

        // Robe body
        g.setColor(robe);
        g.fillRect(9, 14, 14, 11);
        // Robe flare
        int[] bx = {7, 9, 23, 25, 23, 16, 9};
        int[] by = {29, 25, 25, 29, 31, 28, 31};
        g.fillPolygon(bx, by, 7);

        // Robe front seam
        g.setColor(robeL);
        g.drawLine(16, 14, 16, 28);

        // Skull belt buckle
        g.setColor(bone);  g.fillRect(14, 19, 4, 3);
        g.setColor(robeD);
        g.fillRect(15, 20, 1, 1); g.fillRect(17, 20, 1, 1); // eye sockets

        // Skeletal hand (left, raising magic)
        g.setColor(bone);
        g.fillRect(5, 16, 4, 2);
        // Finger bones
        g.setColor(boneD);
        g.fillRect(3, 15, 2, 1); g.fillRect(4, 14, 1, 1);
        g.fillRect(5, 14, 1, 1); g.fillRect(6, 15, 1, 1);

        // Staff in right hand
        g.setColor(staff); g.fillRect(25, 2, 2, 26);
        // Skull on top of staff
        g.setColor(bone);  g.fillOval(23, 0, 6, 6);
        g.setColor(robeD);
        g.fillRect(24, 2, 1, 1); g.fillRect(27, 2, 1, 1); // skull eyes
        g.setColor(boneD); g.drawLine(25, 4, 26, 4); // skull teeth

        // Green death magic swirl around left hand
        g.setColor(magic);
        g.drawOval(1, 11, 8, 8);
        g.setColor(new Color(80, 255, 60, 80));
        g.fillRect(3, 13, 1, 1); g.fillRect(6, 12, 1, 1);
        g.fillRect(2, 16, 1, 1); g.fillRect(7, 17, 1, 1);

        // Floating green particles
        rng.setSeed(402);
        g.setColor(new Color(60, 200, 40, 100));
        for (int i = 0; i < 6; i++) {
            g.fillRect(rng.nextInt(28) + 2, rng.nextInt(28) + 2, 1, 1);
        }

        g.dispose(); return img;
    }

    /** Lich — Tier 8 (L41-45). Skeletal king in tattered royal robes, crown, blue soul-fire eyes. */
    static BufferedImage lich() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        Color bone   = new Color(190, 180, 160);
        Color boneD  = new Color(140, 130, 110);
        Color boneDD = new Color(100, 90, 75);
        Color robe   = new Color(30, 20, 50);
        Color robeL  = new Color(45, 30, 70);
        Color crown  = new Color(200, 170, 40);
        Color crownG = new Color(255, 220, 60);
        Color eye    = new Color(40, 140, 255);
        Color eyeG   = new Color(100, 200, 255);
        Color soul   = new Color(40, 120, 220, 120);

        // Crown
        g.setColor(crown);
        g.fillRect(10, 1, 12, 3);
        g.setColor(crownG);
        // Crown points
        g.fillRect(11, 0, 2, 1); g.fillRect(15, 0, 2, 1); g.fillRect(19, 0, 2, 1);
        // Crown gem
        g.setColor(new Color(200, 30, 30)); g.fillRect(15, 2, 2, 1);

        // Skull head
        g.setColor(bone);  g.fillOval(10, 3, 12, 10);
        g.setColor(boneD); g.fillOval(12, 5, 8, 6);

        // Eye sockets (deep)
        g.setColor(boneDD); g.fillRect(12, 6, 3, 3); g.fillRect(17, 6, 3, 3);
        // Soul-fire eyes
        g.setColor(eye);  g.fillRect(13, 7, 2, 2); g.fillRect(18, 7, 2, 2);
        g.setColor(eyeG); g.fillRect(13, 7, 1, 1); g.fillRect(18, 7, 1, 1);
        // Fire wisps above eyes
        g.setColor(soul); g.fillRect(13, 5, 1, 2); g.fillRect(19, 5, 1, 2);

        // Nasal hole
        g.setColor(boneDD); g.fillRect(15, 9, 2, 1);

        // Teeth
        g.setColor(bone);
        g.fillRect(13, 11, 1, 1); g.fillRect(15, 11, 1, 1);
        g.fillRect(17, 11, 1, 1); g.fillRect(19, 11, 1, 1);
        g.setColor(boneDD);
        g.drawLine(12, 11, 20, 11);

        // Jaw
        g.setColor(boneD); g.fillRect(12, 12, 8, 2);

        // Tattered royal robe
        g.setColor(robe);
        g.fillRect(8, 14, 16, 10);
        // Robe tattered bottom
        int[] rx = {6, 8, 12, 14, 16, 18, 20, 24, 26, 24, 16, 8};
        int[] ry = {27, 24, 28, 24, 30, 25, 28, 24, 27, 31, 31, 31};
        g.fillPolygon(rx, ry, 12);

        // Robe trim / lighter panels
        g.setColor(robeL);
        g.drawLine(16, 14, 16, 26);
        g.drawLine(8, 14, 23, 14);

        // Ribcage visible through robe
        g.setColor(boneDD);
        g.drawLine(12, 16, 20, 16);
        g.drawLine(12, 18, 20, 18);
        g.drawLine(12, 20, 20, 20);

        // Skeletal arms
        g.setColor(bone);
        g.fillRect(4, 16, 4, 2);  // left arm
        g.fillRect(24, 16, 4, 2); // right arm
        // Forearms
        g.setColor(boneD);
        g.fillRect(3, 18, 3, 2);
        g.fillRect(26, 18, 3, 2);
        // Bony hands
        g.setColor(bone);
        g.fillRect(2, 20, 3, 2);
        g.fillRect(27, 20, 3, 2);
        // Fingers
        g.setColor(boneD);
        g.fillRect(1, 21, 1, 2); g.fillRect(3, 21, 1, 2); g.fillRect(5, 21, 1, 1);
        g.fillRect(27, 21, 1, 2); g.fillRect(29, 21, 1, 2); g.fillRect(30, 21, 1, 1);

        // Soul fire aura
        rng.setSeed(403);
        g.setColor(new Color(40, 140, 255, 60));
        for (int i = 0; i < 10; i++) {
            g.fillRect(rng.nextInt(30) + 1, rng.nextInt(30) + 1, 1, 1);
        }

        g.dispose(); return img;
    }

    /** Forest Fairy — L5, FRIENDLY. Tiny winged humanoid, glowing pastel, sparkle trail. */
    static BufferedImage forestFairy() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        Color bodyL  = new Color(200, 230, 180);
        Color body   = new Color(160, 210, 140);
        Color bodyD  = new Color(120, 180, 100);
        Color wing   = new Color(180, 230, 255, 160);
        Color wingL  = new Color(220, 245, 255, 120);
        Color hair   = new Color(255, 200, 80);
        Color eye    = new Color(40, 100, 60);
        Color glow   = new Color(255, 255, 200, 100);
        Color spark  = new Color(255, 255, 180);
        Color sparkD = new Color(200, 220, 140);

        // Soft glow halo around body
        g.setColor(glow);
        g.fillOval(8, 6, 16, 22);

        // Wings (left)
        g.setColor(wing);
        g.fillOval(2, 8, 10, 6);  // upper left wing
        g.fillOval(3, 14, 8, 5);  // lower left wing
        g.setColor(wingL);
        g.drawOval(3, 9, 8, 4);
        g.drawOval(4, 15, 6, 3);

        // Wings (right)
        g.setColor(wing);
        g.fillOval(20, 8, 10, 6);  // upper right wing
        g.fillOval(21, 14, 8, 5);  // lower right wing
        g.setColor(wingL);
        g.drawOval(21, 9, 8, 4);
        g.drawOval(22, 15, 6, 3);

        // Body (small and slender)
        g.setColor(body);
        g.fillOval(13, 11, 6, 10);
        g.setColor(bodyL);
        g.fillOval(14, 12, 4, 6);

        // Head
        g.setColor(bodyL); g.fillOval(12, 6, 8, 8);
        g.setColor(body);  g.fillOval(13, 7, 6, 6);

        // Hair (golden, flowing)
        g.setColor(hair);
        g.fillOval(12, 5, 8, 5);
        g.drawLine(12, 8, 10, 12);
        g.drawLine(20, 8, 22, 12);

        // Eyes (big, green)
        g.setColor(eye);
        g.fillRect(14, 9, 2, 2); g.fillRect(17, 9, 2, 2);
        g.setColor(new Color(200, 240, 200));
        g.fillRect(14, 9, 1, 1); g.fillRect(17, 9, 1, 1);

        // Smile
        g.setColor(new Color(180, 100, 100));
        g.drawLine(15, 12, 17, 12);

        // Little legs
        g.setColor(bodyD);
        g.fillRect(14, 21, 2, 4);
        g.fillRect(17, 21, 2, 4);
        // Tiny feet
        g.setColor(body);
        g.fillRect(13, 24, 3, 1);
        g.fillRect(17, 24, 3, 1);

        // Sparkle trail
        g.setColor(spark);
        g.fillRect(10, 25, 1, 1); g.fillRect(14, 27, 1, 1); g.fillRect(18, 26, 1, 1);
        g.fillRect(22, 28, 1, 1); g.fillRect(8, 28, 1, 1); g.fillRect(16, 29, 1, 1);
        g.setColor(sparkD);
        g.fillRect(12, 28, 1, 1); g.fillRect(20, 27, 1, 1); g.fillRect(6, 30, 1, 1);
        g.fillRect(24, 30, 1, 1); g.fillRect(15, 31, 1, 1);

        g.dispose(); return img;
    }

    /** Wandering Merchant — L10, FRIENDLY. Cloaked figure with pack, lantern, friendly face. */
    static BufferedImage wanderingMerchant() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        Color cloak   = new Color(100, 70, 40);
        Color cloakL  = new Color(130, 95, 55);
        Color cloakD  = new Color(70, 50, 30);
        Color skin    = new Color(185, 155, 120);
        Color skinD   = new Color(155, 125, 95);
        Color hat     = new Color(90, 60, 35);
        Color hatL    = new Color(115, 80, 50);
        Color pack    = new Color(75, 55, 35);
        Color packL   = new Color(95, 72, 48);
        Color buckle  = new Color(200, 170, 40);
        Color lantern = new Color(255, 200, 60);
        Color lantG   = new Color(255, 240, 140, 120);
        Color beard   = new Color(170, 155, 135);
        Color eye     = new Color(50, 40, 30);

        // Wide-brimmed hat
        g.setColor(hat);
        g.fillOval(6, 2, 20, 6);
        g.setColor(hatL);
        g.fillOval(10, 1, 12, 5);
        g.drawLine(8, 5, 24, 5);

        // Face
        g.setColor(skin);  g.fillOval(11, 6, 10, 10);
        g.setColor(skinD); g.fillOval(12, 7, 8, 8);

        // Eyes (friendly, squinting slightly)
        g.setColor(eye);
        g.fillRect(13, 9, 2, 2); g.fillRect(17, 9, 2, 2);
        g.setColor(new Color(200, 180, 160));
        g.fillRect(14, 9, 1, 1); g.fillRect(18, 9, 1, 1);

        // Nose
        g.setColor(skinD); g.fillRect(15, 11, 2, 2);

        // Bushy beard
        g.setColor(beard);
        g.fillOval(11, 12, 10, 6);
        g.fillRect(13, 16, 6, 2);

        // Cloak / tunic body
        g.setColor(cloak);
        g.fillRect(9, 17, 14, 10);
        // Cloak lighter front
        g.setColor(cloakL);
        g.fillRect(13, 17, 6, 10);
        // Belt
        g.setColor(cloakD); g.fillRect(9, 21, 14, 2);
        g.setColor(buckle); g.fillRect(14, 21, 4, 2);

        // Backpack (visible above right shoulder)
        g.setColor(pack);
        g.fillRect(22, 10, 7, 12);
        g.setColor(packL);
        g.drawLine(22, 10, 28, 10);
        g.drawLine(22, 10, 22, 21);
        // Pack straps
        g.setColor(cloakD);
        g.drawLine(22, 12, 20, 17);
        // Items poking out of pack
        g.setColor(new Color(180, 30, 30)); g.fillRect(24, 10, 2, 2); // potion
        g.setColor(new Color(160, 155, 145)); g.fillRect(27, 11, 1, 4); // scroll

        // Left arm holding lantern
        g.setColor(cloak);
        g.fillRect(5, 18, 4, 2);
        g.setColor(skin);
        g.fillRect(3, 19, 3, 2);

        // Lantern
        g.setColor(new Color(80, 70, 55));
        g.fillRect(2, 14, 4, 2); // top
        g.fillRect(2, 20, 4, 2); // bottom
        g.drawLine(2, 16, 2, 19); // left frame
        g.drawLine(5, 16, 5, 19); // right frame
        g.setColor(lantern);
        g.fillRect(3, 16, 2, 4); // glow
        // Lantern glow aura
        g.setColor(lantG);
        g.fillOval(0, 13, 8, 10);

        // Legs
        g.setColor(cloakD);
        g.fillRect(11, 27, 4, 4);
        g.fillRect(17, 27, 4, 4);
        // Boots
        g.setColor(new Color(60, 45, 30));
        g.fillRect(10, 29, 5, 2);
        g.fillRect(17, 29, 5, 2);

        g.dispose(); return img;
    }
}

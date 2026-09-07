import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Random;

/**
 * One-shot sprite generator for Lirandel overworld polish tiles.
 * Generates 32x32 PNGs: bridge, road intersections, river endpoints.
 *
 * Run from the project root:
 *   javac tools/LirandelPolishSpriteGen.java -d tools/
 *   java -cp tools LirandelPolishSpriteGen
 */
public class LirandelPolishSpriteGen {

    static final String OW = "src/main/resources/tiles/overworld/";
    static Random rng = new Random(999);

    // Palette matching existing overworld tiles
    static final Color GRASS      = new Color(56, 136, 38);
    static final Color GRASS_D    = new Color(44, 112, 30);
    static final Color GRASS_L    = new Color(68, 152, 48);
    static final Color ROAD       = new Color(140, 100, 50);
    static final Color ROAD_D     = new Color(105, 72, 35);
    static final Color ROAD_L     = new Color(165, 125, 70);
    static final Color RIVER      = new Color(30, 90, 200);
    static final Color RIVER_L    = new Color(60, 130, 230);
    static final Color RIVER_D    = new Color(20, 60, 160);
    static final Color ROCK       = new Color(100, 95, 85);
    static final Color ROCK_D     = new Color(70, 65, 58);
    static final Color ROCK_L     = new Color(130, 125, 115);
    static final Color OCEAN      = new Color(20, 55, 170);
    static final Color OCEAN_D    = new Color(15, 40, 140);
    static final Color PLANK      = new Color(150, 110, 55);
    static final Color PLANK_D    = new Color(115, 80, 40);

    public static void main(String[] args) throws Exception {
        save(bridgeEW(),       OW + "bridge_ew.png");
        save(roadCross(),      OW + "dirt_road_cross.png");
        save(roadTSouth(),     OW + "dirt_road_t_south.png");
        save(roadTEast(),      OW + "dirt_road_t_east.png");
        save(riverSource(),    OW + "river_source.png");
        save(riverMouth(),     OW + "river_mouth.png");
        System.out.println("Lirandel polish sprites generated: 6 files.");
    }

    static void save(BufferedImage img, String path) throws Exception {
        File f = new File(path);
        f.getParentFile().mkdirs();
        ImageIO.write(img, "PNG", f);
        System.out.println("  wrote " + path);
    }

    // ── BRIDGE EW ──────────────────────────────────────────────────────────────
    // E-W road crossing over N-S river. Road planks horizontal, river visible top/bottom.
    static BufferedImage bridgeEW() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        // Background: grass
        g.setColor(GRASS); g.fillRect(0, 0, 32, 32);
        grassNoise(g, 42);

        // River channel (NS) — 10px wide centered
        int rLeft = 11, rRight = 21;
        g.setColor(RIVER); g.fillRect(rLeft, 0, rRight - rLeft, 32);
        rng.setSeed(420);
        for (int i = 0; i < 30; i++) {
            g.setColor(rng.nextBoolean() ? RIVER_L : RIVER_D);
            g.fillRect(rLeft + rng.nextInt(rRight - rLeft), rng.nextInt(32), 1, 1);
        }

        // Road surface (EW) — 10px tall centered, covers full width
        int roadTop = 11, roadBot = 21;
        g.setColor(ROAD); g.fillRect(0, roadTop, 32, roadBot - roadTop);
        // Road edges
        g.setColor(ROAD_D); g.drawLine(0, roadTop, 31, roadTop);
        g.setColor(ROAD_D); g.drawLine(0, roadBot - 1, 31, roadBot - 1);
        // Road noise
        rng.setSeed(421);
        for (int i = 0; i < 40; i++) {
            g.setColor(rng.nextBoolean() ? ROAD_L : ROAD_D);
            g.fillRect(rng.nextInt(32), roadTop + 1 + rng.nextInt(roadBot - roadTop - 2), 1, 1);
        }

        // Wooden plank lines across bridge (over river section)
        g.setColor(PLANK_D);
        for (int x = rLeft; x < rRight; x += 3) {
            g.drawLine(x, roadTop, x, roadBot - 1);
        }
        // Railing hints at river edges
        g.setColor(PLANK);
        g.drawLine(rLeft, roadTop - 1, rRight - 1, roadTop - 1);
        g.drawLine(rLeft, roadBot, rRight - 1, roadBot);

        g.dispose();
        return img;
    }

    // ── ROAD CROSSROAD ─────────────────────────────────────────────────────────
    // 4-way intersection: NS + EW road, grass in corners.
    static BufferedImage roadCross() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        g.setColor(GRASS); g.fillRect(0, 0, 32, 32);
        grassNoise(g, 50);

        int left = 11, right = 21;
        // Vertical strip
        g.setColor(ROAD); g.fillRect(left, 0, right - left, 32);
        // Horizontal strip
        g.setColor(ROAD); g.fillRect(0, left, 32, right - left);

        // Edges
        g.setColor(ROAD_D);
        g.drawLine(left, 0, left, left);    // N-side left edge
        g.drawLine(right - 1, 0, right - 1, left); // N-side right edge
        g.drawLine(left, right - 1, left, 31);     // S-side left edge
        g.drawLine(right - 1, right - 1, right - 1, 31); // S-side right edge
        g.drawLine(0, left, left, left);    // W-side top edge
        g.drawLine(0, right - 1, left, right - 1); // W-side bottom edge
        g.drawLine(right - 1, left, 31, left);     // E-side top edge
        g.drawLine(right - 1, right - 1, 31, right - 1); // E-side bottom edge

        // Road noise
        rng.setSeed(501);
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 32; x++) {
                boolean inRoad = (x >= left && x < right) || (y >= left && y < right);
                if (inRoad && rng.nextInt(5) == 0) {
                    g.setColor(rng.nextBoolean() ? ROAD_L : ROAD_D);
                    g.fillRect(x, y, 1, 1);
                }
            }
        }

        g.dispose();
        return img;
    }

    // ── ROAD T-SOUTH ───────────────────────────────────────────────────────────
    // EW road with southward branch (inverted T shape).
    static BufferedImage roadTSouth() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        g.setColor(GRASS); g.fillRect(0, 0, 32, 32);
        grassNoise(g, 55);

        int left = 11, right = 21;
        // Horizontal strip (full width)
        g.setColor(ROAD); g.fillRect(0, left, 32, right - left);
        // Vertical strip going south from center
        g.setColor(ROAD); g.fillRect(left, right, right - left, 32 - right);

        // Edges
        g.setColor(ROAD_D);
        g.drawLine(0, left, 31, left);          // top edge of EW
        g.drawLine(0, right - 1, left, right - 1); // bottom edge left of branch
        g.drawLine(right - 1, right - 1, 31, right - 1); // bottom edge right of branch
        g.drawLine(left, right - 1, left, 31);   // left edge of south branch
        g.drawLine(right - 1, right - 1, right - 1, 31); // right edge of south branch

        // Road noise
        rng.setSeed(551);
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 32; x++) {
                boolean inRoad = (y >= left && y < right) || (x >= left && x < right && y >= right);
                if (inRoad && rng.nextInt(5) == 0) {
                    g.setColor(rng.nextBoolean() ? ROAD_L : ROAD_D);
                    g.fillRect(x, y, 1, 1);
                }
            }
        }

        g.dispose();
        return img;
    }

    // ── ROAD T-EAST ────────────────────────────────────────────────────────────
    // NS road with eastward branch (T rotated 90° CW).
    static BufferedImage roadTEast() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        g.setColor(GRASS); g.fillRect(0, 0, 32, 32);
        grassNoise(g, 60);

        int left = 11, right = 21;
        // Vertical strip (full height)
        g.setColor(ROAD); g.fillRect(left, 0, right - left, 32);
        // Horizontal strip going east from center
        g.setColor(ROAD); g.fillRect(right, left, 32 - right, right - left);

        // Edges
        g.setColor(ROAD_D);
        g.drawLine(left, 0, left, 31);          // left edge of NS
        g.drawLine(right - 1, 0, right - 1, left); // right edge above branch
        g.drawLine(right - 1, right - 1, right - 1, 31); // right edge below branch
        g.drawLine(right - 1, left, 31, left);   // top edge of east branch
        g.drawLine(right - 1, right - 1, 31, right - 1); // bottom edge of east branch

        // Road noise
        rng.setSeed(601);
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 32; x++) {
                boolean inRoad = (x >= left && x < right) || (y >= left && y < right && x >= right);
                if (inRoad && rng.nextInt(5) == 0) {
                    g.setColor(rng.nextBoolean() ? ROAD_L : ROAD_D);
                    g.fillRect(x, y, 1, 1);
                }
            }
        }

        g.dispose();
        return img;
    }

    // ── RIVER SOURCE ───────────────────────────────────────────────────────────
    // Rocky spring: stones at top, water pool emerging, channel flowing south.
    static BufferedImage riverSource() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        g.setColor(GRASS); g.fillRect(0, 0, 32, 32);
        grassNoise(g, 70);

        // Rock formation at top (semi-circle of stones)
        int cx = 16, cy = 8;
        g.setColor(ROCK_D); g.fillOval(cx - 9, cy - 6, 18, 12);
        g.setColor(ROCK);   g.fillOval(cx - 7, cy - 4, 14, 9);
        g.setColor(ROCK_L); g.fillOval(cx - 5, cy - 3, 3, 3);
        g.setColor(ROCK_L); g.fillOval(cx + 2, cy - 3, 3, 3);

        // Water pool emerging below rocks
        g.setColor(RIVER);  g.fillOval(cx - 6, cy + 1, 12, 8);
        g.setColor(RIVER_L); g.fillOval(cx - 4, cy + 2, 8, 5);
        // Bright highlight (spring surface)
        g.setColor(new Color(100, 180, 255)); g.fillOval(cx - 2, cy + 3, 4, 3);

        // Channel flowing south from pool
        int rLeft = 11, rRight = 21;
        g.setColor(RIVER); g.fillRect(rLeft, cy + 6, rRight - rLeft, 32 - (cy + 6));
        rng.setSeed(701);
        for (int i = 0; i < 20; i++) {
            g.setColor(rng.nextBoolean() ? RIVER_L : RIVER_D);
            g.fillRect(rLeft + rng.nextInt(rRight - rLeft), cy + 6 + rng.nextInt(32 - (cy + 6)), 1, 1);
        }

        g.dispose();
        return img;
    }

    // ── RIVER MOUTH ────────────────────────────────────────────────────────────
    // River channel (NS) widening at bottom into ocean. Green banks narrow.
    static BufferedImage riverMouth() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        // Ocean at bottom, grass at top
        g.setColor(OCEAN); g.fillRect(0, 0, 32, 32);

        // Draw grass banks that narrow from top to bottom
        for (int y = 0; y < 32; y++) {
            // River width expands from 10px (top) to 32px (bottom)
            float t = y / 31f;
            int halfWidth = (int)(5 + t * 11); // 5 to 16
            int bankLeft = 16 - halfWidth;
            int bankRight = 16 + halfWidth;

            // Grass on left bank
            if (bankLeft > 0) {
                g.setColor(GRASS);
                g.drawLine(0, y, bankLeft - 1, y);
            }
            // Grass on right bank
            if (bankRight < 32) {
                g.setColor(GRASS);
                g.drawLine(bankRight, y, 31, y);
            }

            // River water
            g.setColor(y < 20 ? RIVER : blendColor(RIVER, OCEAN, (y - 20) / 12f));
            g.drawLine(Math.max(0, bankLeft), y, Math.min(31, bankRight - 1), y);
        }

        // Grass noise on banks
        rng.setSeed(801);
        for (int i = 0; i < 60; i++) {
            int x = rng.nextInt(32), y = rng.nextInt(32);
            float t = y / 31f;
            int halfWidth = (int)(5 + t * 11);
            if (x < 16 - halfWidth || x >= 16 + halfWidth) {
                g.setColor(rng.nextBoolean() ? GRASS_L : GRASS_D);
                g.fillRect(x, y, 1, 1);
            }
        }

        // River highlights
        rng.setSeed(802);
        for (int i = 0; i < 30; i++) {
            int x = 8 + rng.nextInt(16), y = rng.nextInt(24);
            g.setColor(rng.nextBoolean() ? RIVER_L : RIVER_D);
            g.fillRect(x, y, 1, 1);
        }

        g.dispose();
        return img;
    }

    // ── HELPERS ─────────────────────────────────────────────────────────────────

    static void grassNoise(Graphics2D g, int seed) {
        rng.setSeed(seed);
        for (int i = 0; i < 80; i++) {
            g.setColor(rng.nextBoolean() ? GRASS_L : GRASS_D);
            g.fillRect(rng.nextInt(32), rng.nextInt(32), 1, 1);
        }
    }

    static Color blendColor(Color a, Color b, float t) {
        t = Math.max(0, Math.min(1, t));
        return new Color(
            (int)(a.getRed()   + (b.getRed()   - a.getRed())   * t),
            (int)(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
            (int)(a.getBlue()  + (b.getBlue()  - a.getBlue())  * t)
        );
    }
}

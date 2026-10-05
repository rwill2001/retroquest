import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Random;

/**
 * One-shot sprite generator for the Thalorax shelf edge.
 *
 * Thalorax is the one island with no coastline — it is seabed all the way out, so the
 * playable area used to run to the literal map edge. "Crushing Depths" is the boundary
 * tile that closes it: the water past the continental shelf, too deep to walk into.
 *
 * It is deliberately a darker sibling of {@code deep_ocean_floor.png} (which is
 * base 8,16,30) rather than a new kind of thing — the shelf should read as the seabed
 * falling away, not as a wall dropped onto the map.
 *
 * Run from the project root:
 *   javac tools/AbyssalEdgeSpriteGen.java -d tools/
 *   java -cp tools AbyssalEdgeSpriteGen
 */
public class AbyssalEdgeSpriteGen {

    static Random rng = new Random(57);

    static final String OW = "src/main/resources/tiles/overworld/";

    public static void main(String[] args) throws Exception {
        save(crushingDepths(), OW + "crushing_depths.png");
        System.out.println("Thalorax shelf-edge sprite generated: 1 file.");
    }

    static void save(BufferedImage img, String path) throws Exception {
        File f = new File(path);
        f.getParentFile().mkdirs();
        ImageIO.write(img, "PNG", f);
        System.out.println("  wrote " + path);
    }

    /**
     * Crushing depths — open water past the shelf. Near-black blue, a faint cold
     * downward gradient, a scatter of suspended silt and a few very dim motes so it
     * does not read as a dead flat fill next to the textured seabed.
     */
    static BufferedImage crushingDepths() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        Color base = new Color(4, 8, 18);
        Color silt = new Color(7, 12, 24);
        Color deep = new Color(2, 4, 11);
        Color mote = new Color(16, 28, 46);

        g.setColor(base);
        g.fillRect(0, 0, 32, 32);

        // Cold vertical falloff — the bottom of the tile is further down the shelf.
        // Drawn as rows rather than a GradientPaint so the tile stays seamless
        // left-to-right and the banding is exact at 32px.
        for (int y = 0; y < 32; y++) {
            float t = y / 31f;
            int r = Math.round(base.getRed()   + (deep.getRed()   - base.getRed())   * t);
            int gg = Math.round(base.getGreen() + (deep.getGreen() - base.getGreen()) * t);
            int b = Math.round(base.getBlue()  + (deep.getBlue()  - base.getBlue())  * t);
            g.setColor(new Color(r, gg, b));
            g.fillRect(0, y, 32, 1);
        }

        // Suspended silt, thinning towards the bottom.
        rng.setSeed(571);
        for (int i = 0; i < 70; i++) {
            int y = rng.nextInt(32);
            if (rng.nextInt(32) < y / 2) continue;   // fewer specks further down
            g.setColor(silt);
            g.fillRect(rng.nextInt(32), y, 1 + rng.nextInt(2), 1);
        }

        // A handful of dim motes — the last of the light that reaches this far out.
        rng.setSeed(572);
        for (int i = 0; i < 4; i++) {
            g.setColor(mote);
            g.fillRect(rng.nextInt(32), rng.nextInt(20), 1, 1);
        }

        g.dispose();
        return img;
    }
}

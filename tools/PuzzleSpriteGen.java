import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * One-shot sprite generator for dungeon puzzle tile.
 * Run from project root:
 *   javac tools/PuzzleSpriteGen.java -d tools/
 *   java -cp tools PuzzleSpriteGen
 *
 * Writes: src/main/resources/tiles/dungeon/puzzle.png
 * Delete this file and tools/PuzzleSpriteGen.class after use.
 */
public class PuzzleSpriteGen {

    public static void main(String[] args) throws Exception {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

        // Background: dark stone floor
        Color stoneDark  = new Color(22, 20, 28);
        Color stoneMid   = new Color(32, 30, 38);
        g.setColor(stoneDark);
        g.fillRect(0, 0, 32, 32);
        // Subtle stone texture
        g.setColor(stoneMid);
        for (int y = 0; y < 32; y += 4) {
            for (int x = 0; x < 32; x += 4) {
                if ((x + y) % 8 == 0) g.fillRect(x, y, 2, 2);
            }
        }

        // Obsidian tablet base (centered rectangle)
        Color obsidian    = new Color(12, 10, 18);
        Color obsidianEdge = new Color(40, 35, 55);
        g.setColor(obsidian);
        g.fillRect(6, 4, 20, 24);
        g.setColor(obsidianEdge);
        g.drawRect(6, 4, 19, 23);

        // Glowing rune lines (amber/orange)
        Color runeGlow   = new Color(255, 180, 40);
        Color runeBright = new Color(255, 220, 100);
        Color runeDim    = new Color(180, 120, 20);

        // Central vertical stroke
        g.setColor(runeGlow);
        g.drawLine(16, 7, 16, 24);

        // Top cross bar
        g.setColor(runeBright);
        g.drawLine(10, 10, 22, 10);

        // Angled legs from cross bar
        g.setColor(runeGlow);
        g.drawLine(10, 10, 8, 16);
        g.drawLine(22, 10, 24, 16);

        // Lower cross detail
        g.setColor(runeDim);
        g.drawLine(12, 18, 20, 18);

        // Diamond at top
        g.setColor(runeBright);
        g.drawLine(16, 6, 18, 8);
        g.drawLine(18, 8, 16, 10);
        g.drawLine(16, 10, 14, 8);
        g.drawLine(14, 8, 16, 6);

        // Small dots at rune intersections (glow spots)
        g.setColor(runeBright);
        g.fillRect(15, 9, 2, 2);
        g.fillRect(15, 17, 2, 2);

        // Subtle glow halo around rune center
        Color halo = new Color(255, 180, 40, 30);
        g.setColor(halo);
        g.fillOval(10, 6, 12, 20);

        g.dispose();

        File out = new File("src/main/resources/tiles/dungeon/puzzle.png");
        out.getParentFile().mkdirs();
        ImageIO.write(img, "png", out);
        System.out.println("Wrote: " + out.getAbsolutePath());
    }
}

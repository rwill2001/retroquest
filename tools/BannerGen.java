import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Renders the repository's promotional stills: the README header banner and the GitHub
 * social-preview card.
 *
 * <p>Both are drawn in the game's own idiom rather than a design tool's — the same near-black
 * ground {@code Retroquest} sets on its content pane, the same amber the overlays use, and Java's
 * {@code Monospaced}, which is the family {@code core/Fonts} resolves for every glyph in the game.
 * A banner built from web fonts would look like marketing for a different program.
 *
 * <p>The CRT treatment is three cheap passes, and the order is the whole trick: glow (concentric
 * translucent copies drawn before the solid glyph), then scanlines over everything, then a radial
 * vignette. Scanlines applied before the glow just get lit back up.
 *
 * <pre>  javac -d tools tools/BannerGen.java &amp;&amp; java -cp tools BannerGen docs/media</pre>
 */
public final class BannerGen {

    static final Color GROUND = new Color(0x0A, 0x0C, 0x10);
    static final Color AMBER  = new Color(0xFF, 0xB0, 0x00);
    static final Color CYAN   = new Color(0x4E, 0xC9, 0xD0);
    static final Color DIM    = new Color(0x6E, 0x7A, 0x8A);

    public static void main(String[] args) throws Exception {
        File out = new File(args.length > 0 ? args[0] : "docs/media");
        out.mkdirs();
        ImageIO.write(banner(1280, 340), "png", new File(out, "banner.png"));
        ImageIO.write(social(1280, 640), "png", new File(out, "social-preview.png"));
        System.out.println("wrote banner.png and social-preview.png to " + out);
    }

    /** README header: wordmark, one-line hook, spec line, sprite strip. */
    static BufferedImage banner(int w, int h) throws Exception {
        BufferedImage img = base(w, h);
        Graphics2D g = img.createGraphics();
        hint(g);
        int cx = w / 2;
        glowText(g, "RETROQUEST", mono(Font.BOLD, 76), AMBER, cx, 132);
        centre(g, "AN 80s CRPG WHERE EVERY ISLAND RENDERS DIFFERENTLY",
               mono(Font.PLAIN, 21), CYAN, cx, 178);
        rule(g, cx - 300, 206, 600);
        centre(g, "JAVA  ·  SWING  ·  32×32 SPRITES  ·  AUDIO SYNTHESIZED AT RUNTIME",
               mono(Font.PLAIN, 15), DIM, cx, 236);
        spriteRow(g, cx, 288, 7);
        g.dispose();
        return finish(img);
    }

    /** Social card: taller, so the renderer ladder can be spelled out. */
    static BufferedImage social(int w, int h) throws Exception {
        BufferedImage img = base(w, h);
        Graphics2D g = img.createGraphics();
        hint(g);
        int cx = w / 2;
        glowText(g, "RETROQUEST", mono(Font.BOLD, 92), AMBER, cx, 190);
        centre(g, "seven islands  ·  seven ways to draw a dungeon",
               mono(Font.PLAIN, 24), CYAN, cx, 244);
        rule(g, cx - 340, 282, 680);

        String[] rungs = {
            "ISLAND 1      top-down grid, torch-lit",
            "ISLANDS 2-3   first-person vector wireframe",
            "ISLANDS 4-5   textured masonry, laid out in world depth",
            "ISLANDS 6-7   raycast, per-pixel light, real cast shadows",
        };
        Font f = mono(Font.PLAIN, 19);
        g.setFont(f);
        int lx = cx - g.getFontMetrics(f).stringWidth(rungs[3]) / 2;
        for (int i = 0; i < rungs.length; i++) {
            g.setColor(i == rungs.length - 1 ? AMBER : DIM);
            g.drawString(rungs[i], lx, 330 + i * 32);
        }
        centre(g, "and the editor it was built in ships with it",
               mono(Font.PLAIN, 18), DIM, cx, 500);
        spriteRow(g, cx, 562, 9);
        g.dispose();
        return finish(img);
    }

    // -- pieces ---------------------------------------------------------------

    static BufferedImage base(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(GROUND);
        g.fillRect(0, 0, w, h);
        // A faint warm pool behind the wordmark, so the ground is not flat black.
        g.setPaint(new RadialGradientPaint(new Point(w / 2, h / 3), w * 0.55f,
                new float[]{0f, 1f},
                new Color[]{new Color(0xFF, 0xB0, 0x00, 26), new Color(0, 0, 0, 0)}));
        g.fillRect(0, 0, w, h);
        g.dispose();
        return img;
    }

    /** Scanlines then vignette - both must land after every glyph is drawn. */
    static BufferedImage finish(BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(0, 0, 0, 56));
        for (int y = 0; y < h; y += 3) g.fillRect(0, y, w, 1);
        g.setPaint(new RadialGradientPaint(new Point(w / 2, h / 2), Math.max(w, h) * 0.62f,
                new float[]{0.55f, 1f},
                new Color[]{new Color(0, 0, 0, 0), new Color(0, 0, 0, 165)}));
        g.fillRect(0, 0, w, h);
        g.dispose();
        return img;
    }

    static void glowText(Graphics2D g, String s, Font f, Color c, int cx, int baseline) {
        g.setFont(f);
        int x = cx - g.getFontMetrics().stringWidth(s) / 2;
        for (int r = 7; r >= 1; r--) {
            g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 12));
            for (int dx = -r; dx <= r; dx += r)
                for (int dy = -r; dy <= r; dy += r)
                    if (dx != 0 || dy != 0) g.drawString(s, x + dx, baseline + dy);
        }
        g.setColor(c);
        g.drawString(s, x, baseline);
    }

    static void centre(Graphics2D g, String s, Font f, Color c, int cx, int baseline) {
        g.setFont(f);
        g.setColor(c);
        g.drawString(s, cx - g.getFontMetrics().stringWidth(s) / 2, baseline);
    }

    static void rule(Graphics2D g, int x, int y, int w) {
        g.setPaint(new GradientPaint(x, y, new Color(0xFF, 0xB0, 0x00, 0),
                x + w / 2f, y, new Color(0xFF, 0xB0, 0x00, 120), true));
        g.fillRect(x, y, w, 1);
    }

    /** A strip of real 32x32 game sprites at 2x, nearest-neighbour so the pixels stay square. */
    static void spriteRow(Graphics2D g, int cx, int y, int count) throws Exception {
        String[] pool = {
            "tiles/player.png", "tiles/monsters/dragon.png", "tiles/monsters/death_knight.png",
            "tiles/monsters/demon.png", "tiles/monsters/bandit.png", "tiles/monsters/chimera.png",
            "tiles/monsters/dark_mage.png", "tiles/monsters/bugbear.png",
            "tiles/monsters/ancient_dragon.png",
        };
        int n = Math.min(count, pool.length), size = 64, gap = 18;
        int x = cx - (n * size + (n - 1) * gap) / 2;
        Object old = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        for (int i = 0; i < n; i++) {
            File f = new File("src/main/resources/" + pool[i]);
            if (f.exists()) g.drawImage(ImageIO.read(f), x, y - size / 2, size, size, null);
            x += size + gap;
        }
        if (old != null) g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, old);
    }

    static Font mono(int style, int size) { return new Font(Font.MONOSPACED, style, size); }

    static void hint(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    }

    private BannerGen() {}
}

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;

/**
 * Generates retroquest.ico (multi-size: 16, 32, 48 px) in src/main/resources/.
 * Run from project root:
 *   javac tools/IconGen.java -d tools/ && java -cp tools IconGen
 * Delete tools/IconGen.class when done.
 */
public class IconGen {

    // Palette
    static final int BG    = argb(10,  10,  14,  255);
    static final int BLADE = argb(255, 176, 0,   255);
    static final int SHINE = argb(255, 230, 110, 255);
    static final int EDGE  = argb(120, 80,  0,   255);
    static final int GUARD = argb(200, 130, 0,   255);
    static final int GDARK = argb(100, 60,  0,   255);
    static final int HANDL = argb(160, 80,  20,  255);
    static final int WRAP  = argb(90,  45,  10,  255);
    static final int GEM   = argb(210, 45,  45,  255);
    static final int GEMHI = argb(255, 130, 130, 255);
    static final int POMM  = argb(220, 150, 10,  255);

    public static void main(String[] args) throws Exception {
        BufferedImage img32 = createIcon32();
        BufferedImage img48 = scale(img32, 48);
        BufferedImage img16 = createIcon16();

        File out = new File("src/main/resources/retroquest.ico");
        out.getParentFile().mkdirs();
        writeIco(new BufferedImage[]{img16, img32, img48}, out);
        System.out.println("Written: " + out.getAbsolutePath());

        // Also write standalone PNG for JFrame icon use
        File png = new File("src/main/resources/retroquest_icon.png");
        ImageIO.write(img32, "PNG", png);
        System.out.println("Written: " + png.getAbsolutePath());
    }

    // ------------------------------------------------------------------
    // 32x32 pixel-art sword icon
    // ------------------------------------------------------------------
    static BufferedImage createIcon32() {
        int[] px = new int[32 * 32];
        fill(px, BG);

        // --- Blade (x=14..17, y=2..17) ---
        // Tip (2px at top)
        set(px, 15, 2, SHINE);
        set(px, 16, 2, SHINE);
        // Blade body
        for (int y = 3; y <= 17; y++) {
            set(px, 14, y, EDGE);
            set(px, 15, y, SHINE);
            set(px, 16, y, BLADE);
            set(px, 17, y, EDGE);
        }

        // --- Guard (x=6..25, y=18..20) ---
        for (int y = 18; y <= 20; y++) {
            for (int x = 6; x <= 25; x++) {
                int c = (x == 6 || x == 25) ? GDARK : GUARD;
                set(px, x, y, c);
            }
        }
        // Guard highlight top edge
        for (int x = 7; x <= 24; x++) set(px, x, 18, GUARD);
        // Gem at center of guard (4px)
        for (int y = 18; y <= 20; y++) {
            set(px, 14, y, GEM);
            set(px, 15, y, GEMHI);
            set(px, 16, y, GEM);
            set(px, 17, y, EDGE);
        }
        set(px, 15, 18, GEMHI);

        // --- Handle (x=14..17, y=21..27) ---
        for (int y = 21; y <= 27; y++) {
            int mid = (y % 2 == 1) ? HANDL : WRAP;
            set(px, 14, y, GDARK);
            set(px, 15, y, mid);
            set(px, 16, y, mid);
            set(px, 17, y, GDARK);
        }

        // --- Pommel (x=12..19, y=28..30) ---
        for (int x = 13; x <= 18; x++) { set(px, x, 28, POMM);  set(px, x, 29, GUARD); }
        for (int x = 14; x <= 17; x++) { set(px, x, 30, GDARK); }
        // Round corners
        set(px, 13, 28, GUARD); set(px, 18, 28, GUARD);
        set(px, 13, 29, GDARK); set(px, 18, 29, GDARK);

        return toImage(px, 32);
    }

    // ------------------------------------------------------------------
    // 16x16 simplified version
    // ------------------------------------------------------------------
    static BufferedImage createIcon16() {
        int[] px = new int[16 * 16];
        fill(px, BG);

        // Blade tip
        set16(px, 7, 1, SHINE);
        set16(px, 8, 1, SHINE);
        // Blade body x=7..8, y=2..8
        for (int y = 2; y <= 8; y++) {
            set16(px, 7, y, SHINE);
            set16(px, 8, y, BLADE);
        }
        // Guard x=3..12, y=9..10
        for (int x = 3; x <= 12; x++) {
            set16(px, x, 9,  GUARD);
            set16(px, x, 10, GDARK);
        }
        // Gem
        set16(px, 7, 9,  GEM);
        set16(px, 8, 9,  GEMHI);
        set16(px, 7, 10, GEM);
        set16(px, 8, 10, GEM);
        // Handle x=7..8, y=11..13
        for (int y = 11; y <= 13; y++) {
            set16(px, 7, y, (y % 2 == 1) ? HANDL : WRAP);
            set16(px, 8, y, (y % 2 == 1) ? HANDL : WRAP);
        }
        // Pommel x=6..9, y=14
        set16(px, 6, 14, GUARD);
        set16(px, 7, 14, POMM);
        set16(px, 8, 14, POMM);
        set16(px, 9, 14, GUARD);

        return toImage(px, 16);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    static int argb(int r, int g, int b, int a) {
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    static void fill(int[] px, int c) { java.util.Arrays.fill(px, c); }

    static void set(int[] px, int x, int y, int c)  { if (x >= 0 && x < 32 && y >= 0 && y < 32) px[y * 32 + x] = c; }
    static void set16(int[] px, int x, int y, int c) { if (x >= 0 && x < 16 && y >= 0 && y < 16) px[y * 16 + x] = c; }

    static BufferedImage toImage(int[] px, int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        img.setRGB(0, 0, size, size, px, 0, size);
        return img;
    }

    static BufferedImage scale(BufferedImage src, int size) {
        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(src, 0, 0, size, size, null);
        g.dispose();
        return out;
    }

    // ------------------------------------------------------------------
    // ICO writer (stores PNG chunks per image — Vista+ compatible)
    // ------------------------------------------------------------------
    static void writeIco(BufferedImage[] images, File output) throws Exception {
        byte[][] pngs = new byte[images.length][];
        for (int i = 0; i < images.length; i++) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            ImageIO.write(images[i], "PNG", b);
            pngs[i] = b.toByteArray();
        }

        int n = images.length;
        int dataOffset = 6 + n * 16;

        try (DataOutputStream out = new DataOutputStream(
                new BufferedOutputStream(new FileOutputStream(output)))) {
            // ICONDIR
            leShort(out, 0);  // Reserved
            leShort(out, 1);  // Type = icon
            leShort(out, n);  // Count

            // ICONDIRENTRY for each image
            int offset = dataOffset;
            for (int i = 0; i < n; i++) {
                int sz = images[i].getWidth();
                out.writeByte(sz >= 256 ? 0 : sz); // Width
                out.writeByte(sz >= 256 ? 0 : sz); // Height
                out.writeByte(0);                   // ColorCount (0 = truecolor)
                out.writeByte(0);                   // Reserved
                leShort(out, 1);                    // Planes
                leShort(out, 32);                   // BitCount
                leInt(out, pngs[i].length);         // SizeInBytes
                leInt(out, offset);                 // ImageOffset
                offset += pngs[i].length;
            }

            // Image data
            for (byte[] data : pngs) out.write(data);
        }
    }

    static void leShort(DataOutputStream o, int v) throws IOException {
        o.writeByte(v & 0xFF); o.writeByte((v >> 8) & 0xFF);
    }
    static void leInt(DataOutputStream o, int v) throws IOException {
        o.writeByte(v & 0xFF); o.writeByte((v >> 8) & 0xFF);
        o.writeByte((v >> 16) & 0xFF); o.writeByte((v >> 24) & 0xFF);
    }
}

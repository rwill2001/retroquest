package io.cannonforge.retroquest.core;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.HashMap;
import java.util.Map;

/**
 * Procedurally generated texture pages for {@link RaycastDungeonRenderer}.
 *
 * <p>Islands 4 and 5 draw their surfaces as vector polygons directly in perspective, which caps
 * how much detail a wall can carry: every crack and carving has to be projected by hand, and small
 * detail collapses at distance. From Island 6 the walls are texture-mapped instead, so the surface
 * is authored once, flat, at full brightness — and the renderer samples it. Detail becomes free.
 *
 * <p>Pages are pure albedo. Nothing here knows about distance or light; the renderer multiplies
 * every texel by the light reaching that pixel. Drawing shadows into the texture would fight the
 * lighting and make lit walls look dirty.
 *
 * <p>Textures wrap horizontally — a wall face is exactly one page wide — so any pattern that
 * crosses the right edge has to reappear on the left. The block generators handle this by keeping
 * column jitter identical at both edges.
 */
public final class DungeonTextures {

    /** Page edge length. Power of two so the renderer can mask instead of modulo. */
    public static final int SIZE = 128;
    public static final int MASK = SIZE - 1;

    /**
     * Wall page slots.
     *
     * <p>The first four are chosen by hashing a cell, so a stretch of plain wall is not one
     * repeated stamp. The rest are chosen by <em>tile identity</em>: the maps have always said
     * which blocks are bookshelves, pillars, gates and lava seams, and until Island 7 the
     * first-person renderers drew every one of them as anonymous stone. The Archive of Tears
     * alone authors 272 bookshelf tiles.
     */
    public static final int WALL_PLAIN_A  = 0;
    public static final int WALL_PLAIN_B  = 1;
    public static final int WALL_FEATURE  = 2;   // shelving, fresco — the theme's set piece
    public static final int WALL_DAMAGED  = 3;
    public static final int WALL_ROUGH    = 4;   // '#' natural rock, as opposed to dressed stone
    public static final int WALL_BOOKSHELF = 5;  // 'k'
    public static final int WALL_PILLAR   = 6;   // 'M'
    public static final int WALL_GATE     = 7;   // '[' locked iron gate
    public static final int WALL_BARREL   = 8;   // 'n'
    public static final int WALL_LAVA     = 9;   // '-' lava channel
    public static final int WALL_EMBER    = 10;  // '+' ember grate
    public static final int WALL_TORCH    = 11;  // 'v' wall torch
    public static final int WALL_FORGE    = 12;  // 'N'
    public static final int WALL_BARRIER  = 13;  // '{' magic barrier
    public static final int WALL_PAGES    = 14;

    /**
     * Which page a wall face shows. Tile identity wins; anything with no surface of its own
     * falls back to the hashed plain/feature/damaged rotation.
     */
    public static int pageForTile(char tile, int mapX, int mapY, int face) {
        switch (tile) {
            case 'k': return WALL_BOOKSHELF;
            case 'M': return WALL_PILLAR;
            case '[': return WALL_GATE;
            case 'n': return WALL_BARREL;
            case '-': return WALL_LAVA;
            case '+': return WALL_EMBER;
            case 'v': return WALL_TORCH;
            case 'N': return WALL_FORGE;
            case '{': return WALL_BARRIER;
            case '#': return WALL_ROUGH;
            default: break;
        }
        int r = hash(mapX, mapY, face, 7) % 16;
        if (r == 0 || r == 1) return WALL_FEATURE;
        if (r == 2) return WALL_DAMAGED;
        return (r % 2 == 0) ? WALL_PLAIN_A : WALL_PLAIN_B;
    }

    /** One theme's complete set of surfaces, generated once and shared. */
    public static final class Set {
        public final int[][] wall = new int[WALL_PAGES][];
        public int[] floor;
        public int[] ceiling;
        public int[] door;
    }

    private static final Map<DungeonTheme, Set> CACHE = new HashMap<>();

    private DungeonTextures() {}

    /** Returns the theme's pages, generating them on first use. */
    public static synchronized Set forTheme(DungeonTheme t) {
        return CACHE.computeIfAbsent(t, DungeonTextures::build);
    }

    private static Set build(DungeonTheme t) {
        Set s = new Set();
        boolean cyclopean = t.wallStyle == DungeonTheme.WallStyle.CYCLOPEAN;

        s.wall[WALL_PLAIN_A] = cyclopean ? cyclopeanWall(t, 11) : coursedWall(t, 4, 3, 7);
        s.wall[WALL_PLAIN_B] = cyclopean ? cyclopeanWall(t, 29) : coursedWall(t, 4, 3, 23);
        s.wall[WALL_FEATURE] = featureWall(t);
        s.wall[WALL_DAMAGED] = damagedWall(t, cyclopean);
        s.wall[WALL_ROUGH]     = roughWall(t);
        s.wall[WALL_BOOKSHELF] = bookshelfWall(t);
        s.wall[WALL_PILLAR]    = pillarWall(t);
        s.wall[WALL_GATE]      = gateWall(t);
        s.wall[WALL_BARREL]    = barrelWall(t);
        s.wall[WALL_LAVA]      = moltenWall(t, new Color(210, 62, 10), new Color(255, 190, 70), 0.55f);
        s.wall[WALL_EMBER]     = grateWall(t, new Color(205, 104, 22));
        s.wall[WALL_TORCH]     = torchWall(t);
        s.wall[WALL_FORGE]     = moltenWall(t, new Color(198, 82, 20), new Color(255, 208, 120), 0.32f);
        s.wall[WALL_BARRIER]   = barrierWall(t);
        s.floor   = flagstoneFloor(t);
        s.ceiling = vaultCeiling(t);
        s.door    = doorPage(t);
        return s;
    }

    // ── Wall generators ──────────────────────────────────────────────────────

    /** Regular coursed masonry: {@code courses} rows of {@code cols} blocks in running bond. */
    private static int[] coursedWall(DungeonTheme t, int courses, int cols, int seed) {
        Canvas c = new Canvas(t.mortar);
        int rowH = SIZE / courses + 1;

        for (int row = 0; row < courses; row++) {
            int y0 = row * SIZE / courses;
            int y1 = (row + 1) * SIZE / courses;
            int offset = (row % 2 == 0) ? 0 : SIZE / (cols * 2);

            for (int col = -1; col <= cols; col++) {
                int x0 = col * SIZE / cols + offset;
                int x1 = x0 + SIZE / cols;
                int h = hash(seed, row, col, 3);
                block(c, t, x0, y0, x1, y1, h, row / (float) courses);
            }
        }
        c.grime(t, seed);
        return c.pixels();
    }

    /**
     * Cyclopean masonry — irregular polygonal stones fitted tight with no continuous joints.
     * Built from a jittered lattice so neighbouring stones share exact corners; the first and
     * last lattice columns get identical jitter so the page still tiles.
     */
    private static int[] cyclopeanWall(DungeonTheme t, int seed) {
        Canvas c = new Canvas(t.mortar);
        final int gx = 4, gy = 5;
        float[][] px = new float[gy + 1][gx + 1];
        float[][] py = new float[gy + 1][gx + 1];

        for (int r = 0; r <= gy; r++) {
            for (int q = 0; q <= gx; q++) {
                int h = hash(seed, r, q % gx, 17);   // q % gx: column gx repeats column 0
                float jx = ((h & 255) / 255f - 0.5f) * (SIZE / (float) gx) * 0.55f;
                float jy = (((h >> 8) & 255) / 255f - 0.5f) * (SIZE / (float) gy) * 0.55f;
                px[r][q] = q * SIZE / (float) gx + (q == 0 || q == gx ? 0f : jx);
                py[r][q] = r * SIZE / (float) gy + (r == 0 || r == gy ? 0f : jy);
            }
        }

        for (int r = 0; r < gy; r++) {
            for (int q = 0; q < gx; q++) {
                int h = hash(seed, r, q, 31);
                Polygon poly = new Polygon(
                    new int[]{Math.round(px[r][q]), Math.round(px[r][q + 1]),
                              Math.round(px[r + 1][q + 1]), Math.round(px[r + 1][q])},
                    new int[]{Math.round(py[r][q]), Math.round(py[r][q + 1]),
                              Math.round(py[r + 1][q + 1]), Math.round(py[r + 1][q])}, 4);
                stone(c, t, poly, h, r / (float) gy, 95);
            }
        }
        c.grime(t, seed);
        return c.pixels();
    }

    /** The theme's set piece: carved shelving, or a faded fresco panel. */
    private static int[] featureWall(DungeonTheme t) {
        int[] base = (t.wallStyle == DungeonTheme.WallStyle.CYCLOPEAN)
                ? cyclopeanWall(t, 43) : coursedWall(t, 4, 3, 61);
        Canvas c = Canvas.over(base);

        switch (t.feature) {
            case SHELVES -> shelves(c, t);
            case FRESCO  -> fresco(c, t);
            case NONE    -> { }
        }
        return c.pixels();
    }

    /** Recessed shelving stacked with rotted scrolls — the Archive's whole identity. */
    private static void shelves(Canvas c, DungeonTheme t) {
        Graphics2D g = c.g;
        int m = SIZE / 10;
        int x0 = m, x1 = SIZE - m;
        int y0 = SIZE / 5, y1 = SIZE - SIZE / 6;

        // Recess: dark, with a lit lip along the top edge so it reads as cut into the wall.
        g.setColor(dark(t.wallDark, 0.55f));
        g.fillRect(x0, y0, x1 - x0, y1 - y0);
        g.setColor(new Color(t.wallLight.getRed(), t.wallLight.getGreen(), t.wallLight.getBlue(), 150));
        g.setStroke(new BasicStroke(2f));
        g.drawLine(x0, y0, x1, y0);

        int shelves = 3;
        for (int s = 0; s < shelves; s++) {
            int sy0 = y0 + (y1 - y0) * s / shelves;
            int sy1 = y0 + (y1 - y0) * (s + 1) / shelves;

            // Scroll ends, packed at varying depth so the row is not a comb.
            for (int i = 0; i < 9; i++) {
                int h = hash(71, s, i, 5);
                int sx = x0 + 3 + (x1 - x0 - 6) * i / 9;
                int w = Math.max(3, (x1 - x0) / 12 - (h & 1));
                int ih = (sy1 - sy0) - 6 - ((h >> 2) & 3);
                if ((h & 7) == 0) continue;                       // gaps: shelves are not full
                Color scroll = mix(t.wallHigh, t.wallMoss, ((h >> 4) & 7) / 14f);
                g.setColor(shade(scroll, 0.55f + ((h >> 7) & 7) / 18f));
                g.fillRect(sx, sy1 - ih - 2, w, ih);
                g.setColor(dark(t.wallDark, 0.7f));
                g.drawLine(sx + w, sy1 - ih - 2, sx + w, sy1 - 3);
            }

            // Shelf plank
            g.setColor(shade(t.doorNear, 0.8f));
            g.fillRect(x0, sy1 - 3, x1 - x0, 3);
            g.setColor(dark(t.wallDark, 0.8f));
            g.drawLine(x0, sy1, x1, sy1);
        }
    }

    /** A faded wall painting: figures worn down to silhouettes and flaking pigment. */
    private static void fresco(Canvas c, DungeonTheme t) {
        Graphics2D g = c.g;
        int x0 = SIZE / 8, x1 = SIZE - SIZE / 8;
        int y0 = SIZE / 6, y1 = SIZE - SIZE / 4;

        // Plaster ground, slightly warmer than the stone around it.
        g.setColor(mix(t.wallNear, t.wallHigh, 0.45f));
        g.fillRect(x0, y0, x1 - x0, y1 - y0);

        // Standing figures, deliberately crude — worn past recognition.
        int figures = 4;
        for (int i = 0; i < figures; i++) {
            int h = hash(97, i, 3, 11);
            int fx = x0 + (x1 - x0) * (i * 2 + 1) / (figures * 2);
            int fh = (y1 - y0) * (11 + (h & 3)) / 16;
            int fy = y1 - fh;
            int fw = Math.max(4, (x1 - x0) / 12);
            Color robe = mix(t.wallMoss, t.specialNear, ((h >> 3) & 7) / 10f);
            g.setColor(new Color(robe.getRed(), robe.getGreen(), robe.getBlue(), 120));
            g.fillRect(fx - fw / 2, fy + fh / 5, fw, fh * 4 / 5);          // robe
            g.fillOval(fx - fw / 3, fy, fw * 2 / 3, fh / 5);               // head
            if ((h & 4) == 0) {                                            // raised arm
                g.setStroke(new BasicStroke(2f));
                g.drawLine(fx + fw / 3, fy + fh / 3, fx + fw, fy + fh / 8);
            }
        }

        // Flaking: chips of plaster gone, showing the stone beneath.
        for (int i = 0; i < 40; i++) {
            int h = hash(131, i, 7, 2);
            int fx = x0 + (h & 127) % Math.max(1, x1 - x0);
            int fy = y0 + ((h >> 7) & 127) % Math.max(1, y1 - y0);
            int r = 2 + ((h >> 14) & 3);
            g.setColor(new Color(t.wallNear.getRed(), t.wallNear.getGreen(), t.wallNear.getBlue(), 190));
            g.fillOval(fx, fy, r, r);
        }

        // Border band
        g.setColor(new Color(t.wallHigh.getRed(), t.wallHigh.getGreen(), t.wallHigh.getBlue(), 90));
        g.setStroke(new BasicStroke(2f));
        g.drawRect(x0, y0, x1 - x0, y1 - y0);
    }

    /** A wall that has partly failed: missing stones, a spill of rubble, exposed core. */
    private static int[] damagedWall(DungeonTheme t, boolean cyclopean) {
        int[] base = cyclopean ? cyclopeanWall(t, 83) : coursedWall(t, 4, 3, 89);
        Canvas c = Canvas.over(base);
        Graphics2D g = c.g;

        // Stones missing from one course, showing the rubble core behind them. A flat dark
        // polygon here read as a hole punched through reality once the lighting hit it, so the
        // recess is only slightly darker than the face and is filled with broken stone.
        int bx = SIZE / 4, bw = SIZE / 2;
        int by = SIZE * 2 / 5, bh = SIZE / 5;
        int[] rx = {bx, bx + bw, bx + bw - 6, bx + 7};
        int[] ry = {by + 4, by, by + bh, by + bh - 7};

        g.setColor(shade(t.wallNear, 0.42f));
        g.fillPolygon(rx, ry, 4);

        // Core packing: small stones set in the fill, catching a little light.
        for (int i = 0; i < 26; i++) {
            int h = hash(163, i, 11, 4);
            int cxp = bx + 4 + ((h & 63) * (bw - 8) / 64);
            int cyp = by + 2 + (((h >> 6) & 31) * (bh - 4) / 32);
            int cw = 3 + ((h >> 11) & 3);
            g.setColor(shade(t.wallNear, 0.5f + ((h >> 14) & 7) / 22f));
            g.fillRect(cxp, cyp, cw, cw * 2 / 3);
            g.setColor(new Color(t.wallDark.getRed(), t.wallDark.getGreen(), t.wallDark.getBlue(), 140));
            g.drawLine(cxp, cyp + cw * 2 / 3, cxp + cw, cyp + cw * 2 / 3);
        }

        // Broken stone edges catching light along the top of the gap.
        g.setColor(new Color(t.wallLight.getRed(), t.wallLight.getGreen(), t.wallLight.getBlue(), 150));
        g.setStroke(new BasicStroke(1.5f));
        g.drawLine(rx[0], ry[0], rx[1], ry[1]);
        g.setColor(new Color(t.wallDark.getRed(), t.wallDark.getGreen(), t.wallDark.getBlue(), 190));
        g.drawLine(rx[3], ry[3], rx[2], ry[2]);

        // Rubble spilled at the foot of the wall
        for (int i = 0; i < 22; i++) {
            int h = hash(151, i, 5, 9);
            int px = bx + ((h & 63) * bw / 64);
            int py = by + bh - 4 + ((h >> 6) & 15);
            int pw = 3 + ((h >> 10) & 5);
            g.setColor(shade(t.wallNear, 0.6f + ((h >> 13) & 7) / 20f));
            g.fillRect(px, py, pw, pw * 2 / 3);
        }
        return c.pixels();
    }

    // ── Tile-identity surfaces ───────────────────────────────────────────────

    /** Natural rock rather than dressed stone: no courses, just fractured mass. */
    private static int[] roughWall(DungeonTheme t) {
        Canvas c = new Canvas(shade(t.wallNear, 0.72f));
        Graphics2D g = c.g;
        // Broad tonal blotches — bedrock has no joints, only shading.
        for (int i = 0; i < 26; i++) {
            int h = hash(311, i, 3, 5);
            int r = SIZE / 6 + ((h >> 4) & 31);
            g.setColor(shade(t.wallNear, 0.55f + ((h >> 9) & 15) / 26f));
            g.fillOval((h & MASK) - r / 2, ((h >> 16) & MASK) - r / 2, r, r);
        }
        // Fracture lines running mostly with the bedding plane.
        g.setStroke(new BasicStroke(1.6f));
        for (int i = 0; i < 12; i++) {
            int h = hash(313, i, 7, 2);
            int y = (h & MASK);
            int x = ((h >> 8) & MASK);
            int len = SIZE / 4 + ((h >> 16) & 31);
            g.setColor(new Color(t.wallDark.getRed(), t.wallDark.getGreen(), t.wallDark.getBlue(), 130));
            g.drawLine(x, y, x + len, y + ((h >> 20) & 7) - 3);
            g.setColor(new Color(t.wallHigh.getRed(), t.wallHigh.getGreen(), t.wallHigh.getBlue(), 60));
            g.drawLine(x, y - 2, x + len, y - 2 + ((h >> 20) & 7) - 3);
        }
        c.grime(t, 317);
        return c.pixels();
    }

    /** A stack of shelves filling the whole face — the authored 'k' tile, not the decorative one. */
    private static int[] bookshelfWall(DungeonTheme t) {
        Canvas c = new Canvas(dark(t.wallDark, 0.5f));
        Graphics2D g = c.g;

        Color frame = shade(t.doorNear, 0.85f);
        int rows = 4;
        for (int r = 0; r < rows; r++) {
            int y0 = r * SIZE / rows, y1 = (r + 1) * SIZE / rows;
            for (int i = 0; i < 11; i++) {
                int h = hash(331, r, i, 3);
                if ((h & 7) == 0) continue;                      // gaps in the run
                int x = 2 + (SIZE - 4) * i / 11;
                int w = Math.max(3, SIZE / 13 - (h & 1));
                int bh = (y1 - y0) - 5 - ((h >> 2) & 4);
                Color spine = mix(t.wallHigh, t.wallMoss, ((h >> 4) & 7) / 12f);
                g.setColor(shade(spine, 0.45f + ((h >> 8) & 7) / 16f));
                g.fillRect(x, y1 - bh - 3, w, bh);
                g.setColor(new Color(0, 0, 0, 120));
                g.drawLine(x + w, y1 - bh - 3, x + w, y1 - 4);
                if ((h & 16) != 0) {                              // a title band on some spines
                    g.setColor(new Color(t.specialNear.getRed(), t.specialNear.getGreen(),
                                         t.specialNear.getBlue(), 90));
                    g.drawLine(x + 1, y1 - bh / 2 - 3, x + w - 1, y1 - bh / 2 - 3);
                }
            }
            g.setColor(frame);
            g.fillRect(0, y1 - 3, SIZE, 3);
            g.setColor(new Color(0, 0, 0, 150));
            g.drawLine(0, y1, SIZE, y1);
        }
        // Uprights, so it reads as joinery rather than floating books
        g.setColor(frame);
        g.fillRect(0, 0, 3, SIZE);
        g.fillRect(SIZE - 3, 0, 3, SIZE);
        return c.pixels();
    }

    /** A column standing proud of the wall plane, with a base and capital. */
    private static int[] pillarWall(DungeonTheme t) {
        Canvas c = new Canvas(shade(t.wallNear, 0.5f));
        Graphics2D g = c.g;
        int x0 = SIZE / 5, x1 = SIZE - SIZE / 5;

        // Shaft, lit down the left edge to suggest a cylinder.
        for (int x = x0; x < x1; x++) {
            float u = (x - x0) / (float) (x1 - x0);
            float lit = 1.15f - 0.85f * Math.abs(u - 0.32f);
            g.setColor(shade(t.wallNear, Math.max(0.35f, lit)));
            g.drawLine(x, SIZE / 8, x, SIZE - SIZE / 8);
        }
        // Flutes
        g.setColor(new Color(t.wallDark.getRed(), t.wallDark.getGreen(), t.wallDark.getBlue(), 110));
        for (int i = 1; i < 5; i++) {
            int x = x0 + (x1 - x0) * i / 5;
            g.drawLine(x, SIZE / 8, x, SIZE - SIZE / 8);
        }
        // Capital and base
        g.setColor(shade(t.wallNear, 1.05f));
        g.fillRect(x0 - 6, SIZE / 12, x1 - x0 + 12, SIZE / 12);
        g.fillRect(x0 - 6, SIZE - SIZE / 6, x1 - x0 + 12, SIZE / 12);
        g.setColor(new Color(t.wallDark.getRed(), t.wallDark.getGreen(), t.wallDark.getBlue(), 160));
        g.drawRect(x0 - 6, SIZE / 12, x1 - x0 + 12, SIZE / 12);
        g.drawRect(x0 - 6, SIZE - SIZE / 6, x1 - x0 + 12, SIZE / 12);
        return c.pixels();
    }

    /** Iron bars over darkness — you can see that it is barred, and that it is shut. */
    private static int[] gateWall(DungeonTheme t) {
        Canvas c = new Canvas(new Color(6, 6, 8));
        Graphics2D g = c.g;
        g.setColor(shade(t.wallNear, 0.34f));
        g.fillRect(0, 0, SIZE, SIZE / 8);

        Color iron = shade(t.doorFrame, 0.75f);
        for (int i = 0; i < 7; i++) {
            int x = 6 + (SIZE - 12) * i / 7;
            g.setColor(iron);
            g.fillRect(x, SIZE / 10, 6, SIZE - SIZE / 10);
            g.setColor(shade(iron, 1.4f));
            g.fillRect(x, SIZE / 10, 2, SIZE - SIZE / 10);      // highlight down each bar
        }
        g.setColor(iron);
        g.fillRect(0, SIZE / 3, SIZE, 7);
        g.fillRect(0, SIZE * 2 / 3, SIZE, 7);
        g.setColor(shade(iron, 1.35f));
        g.fillRect(0, SIZE / 3, SIZE, 2);
        g.fillRect(0, SIZE * 2 / 3, SIZE, 2);
        return c.pixels();
    }

    /** Barrels stacked against the wall. */
    private static int[] barrelWall(DungeonTheme t) {
        Canvas c = new Canvas(shade(t.wallNear, 0.4f));
        Graphics2D g = c.g;
        Color wood = shade(t.doorNear, 0.9f);
        int[] bx = {SIZE / 6, SIZE / 2, SIZE * 5 / 6, SIZE / 3};
        int[] by = {SIZE / 2, SIZE * 5 / 12, SIZE / 2, SIZE - SIZE / 5};
        for (int i = 0; i < bx.length; i++) {
            int w = SIZE / 3, h = SIZE / 2;
            int x = bx[i] - w / 2, y = by[i] - h / 2;
            for (int px2 = 0; px2 < w; px2++) {
                float u = px2 / (float) w;
                g.setColor(shade(wood, 0.55f + 0.6f * (1f - Math.abs(u - 0.35f) * 2f)));
                g.drawLine(x + px2, y, x + px2, y + h);
            }
            g.setColor(shade(t.doorFrame, 0.8f));
            g.fillRect(x, y + h / 5, w, 4);
            g.fillRect(x, y + h * 3 / 5, w, 4);
            g.setColor(new Color(0, 0, 0, 150));
            g.drawRect(x, y, w, h);
        }
        return c.pixels();
    }

    /** A seam of molten rock running through the wall. Bright because it is a light source. */
    private static int[] moltenWall(DungeonTheme t, Color glow, Color hot, float coverage) {
        Canvas c = new Canvas(shade(t.wallNear, 0.30f));
        Graphics2D g = c.g;

        // Cooled crust around the seam
        for (int i = 0; i < 20; i++) {
            int h = hash(347, i, 5, 3);
            int r = SIZE / 8 + ((h >> 6) & 23);
            g.setColor(shade(t.wallNear, 0.24f + ((h >> 11) & 7) / 30f));
            g.fillOval((h & MASK) - r / 2, ((h >> 16) & MASK) - r / 2, r, r);
        }

        int band = (int) (SIZE * coverage);
        int top = (SIZE - band) / 2;
        // The molten channel itself, hottest at the centre.
        for (int y = top; y < top + band; y++) {
            float u = (y - top) / (float) band;
            float heat = 1f - Math.abs(u - 0.5f) * 2f;
            int wobble = (int) (Math.sin(y * 0.35) * SIZE * 0.06);
            g.setColor(mix(glow, hot, heat * heat));
            g.drawLine(wobble, y, SIZE + wobble, y);
            if (wobble > 0) g.drawLine(wobble - SIZE, y, wobble, y);
        }
        // Crust islands floating on the flow
        for (int i = 0; i < 14; i++) {
            int h = hash(349, i, 9, 7);
            int x = h & MASK;
            int y = top + ((h >> 8) % Math.max(1, band));
            int w = 4 + ((h >> 14) & 11);
            g.setColor(shade(t.wallNear, 0.30f));
            g.fillOval(x, y, w, Math.max(2, w / 2));
        }
        return c.pixels();
    }

    /** An iron grate with fire behind it. */
    private static int[] grateWall(DungeonTheme t, Color glow) {
        Canvas c = new Canvas(shade(t.wallNear, 0.34f));
        Graphics2D g = c.g;

        int x0 = SIZE / 6, x1 = SIZE - SIZE / 6;
        int y0 = SIZE / 4, y1 = SIZE - SIZE / 4;

        // Fire behind, hottest at the middle
        for (int y = y0; y < y1; y++) {
            float u = (y - y0) / (float) (y1 - y0);
            g.setColor(mix(glow, new Color(255, 226, 150), (1f - u) * 0.75f));
            g.drawLine(x0, y, x1, y);
        }
        // Grate bars in silhouette
        Color iron = new Color(24, 20, 18);
        g.setColor(iron);
        for (int i = 0; i <= 5; i++) {
            int x = x0 + (x1 - x0) * i / 5;
            g.fillRect(x - 2, y0, 4, y1 - y0);
        }
        for (int i = 0; i <= 3; i++) {
            int y = y0 + (y1 - y0) * i / 3;
            g.fillRect(x0, y - 2, x1 - x0, 4);
        }
        g.setColor(shade(t.wallNear, 0.9f));
        g.setStroke(new BasicStroke(3f));
        g.drawRect(x0 - 3, y0 - 3, x1 - x0 + 6, y1 - y0 + 6);
        return c.pixels();
    }

    /** A bracket torch mounted on dressed stone. */
    private static int[] torchWall(DungeonTheme t) {
        int[] base = coursedWall(t, 4, 3, 71);
        Canvas c = Canvas.over(base);
        Graphics2D g = c.g;
        int cx = SIZE / 2;

        // Soot plume climbing the wall above the flame
        for (int i = 0; i < 30; i++) {
            int h = hash(353, i, 4, 6);
            int w = 8 + ((h >> 3) & 23);
            int y = (h & 63);
            g.setColor(new Color(0, 0, 0, 26));
            g.fillOval(cx - w / 2 + (((h >> 9) & 15) - 8), y, w, w);
        }
        // Bracket and flame
        g.setColor(new Color(26, 22, 18));
        g.fillRect(cx - 7, SIZE / 2, 14, SIZE / 5);
        g.fillRect(cx - 3, SIZE / 2 + SIZE / 5, 6, SIZE / 8);
        g.setColor(new Color(236, 150, 44));
        g.fillOval(cx - 13, SIZE / 3, 26, SIZE / 3);
        g.setColor(new Color(255, 226, 160));
        g.fillOval(cx - 7, SIZE / 3 + 6, 14, SIZE / 5);
        return c.pixels();
    }

    /** A sheet of standing magic across the opening. */
    private static int[] barrierWall(DungeonTheme t) {
        Canvas c = new Canvas(new Color(10, 8, 22));
        Graphics2D g = c.g;
        for (int y = 0; y < SIZE; y++) {
            float u = y / (float) SIZE;
            float band = (float) (0.5 + 0.5 * Math.sin(u * Math.PI * 6));
            g.setColor(mix(new Color(48, 34, 120), new Color(150, 120, 255), band * 0.8f));
            g.drawLine(0, y, SIZE, y);
        }
        g.setStroke(new BasicStroke(2f));
        for (int i = 0; i < 9; i++) {
            int h = hash(359, i, 2, 8);
            g.setColor(new Color(210, 190, 255, 90));
            int x = h & MASK;
            g.drawLine(x, 0, x + ((h >> 8) & 31) - 16, SIZE);
        }
        return c.pixels();
    }

    // ── Floor and ceiling ────────────────────────────────────────────────────

    private static int[] flagstoneFloor(DungeonTheme t) {
        Canvas c = new Canvas(t.floorGap);
        int n = 3;
        for (int r = 0; r < n; r++) {
            for (int q = 0; q < n; q++) {
                int h = hash(181, r, q, 13);
                int x0 = q * SIZE / n + 1, x1 = (q + 1) * SIZE / n - 1;
                int y0 = r * SIZE / n + 1, y1 = (r + 1) * SIZE / n - 1;
                c.g.setColor(shade(t.floorNear, 0.82f + ((h & 15) / 42f)));
                c.g.fillRect(x0, y0, x1 - x0, y1 - y0);
                c.g.setColor(new Color(t.floorDetail.getRed(), t.floorDetail.getGreen(),
                                       t.floorDetail.getBlue(), 90));
                c.g.drawLine(x0, y0, x1, y0);
                // Wear: a scatter of darker pits
                for (int i = 0; i < 6; i++) {
                    int hh = hash(h, i, 3, 19);
                    c.g.setColor(new Color(t.floorGap.getRed(), t.floorGap.getGreen(),
                                           t.floorGap.getBlue(), 70));
                    c.g.fillRect(x0 + (hh & 31), y0 + ((hh >> 5) & 31), 1 + ((hh >> 10) & 1), 1);
                }
            }
        }
        return c.pixels();
    }

    private static int[] vaultCeiling(DungeonTheme t) {
        Canvas c = new Canvas(t.ceilFar);
        c.g.setColor(t.ceilNear);
        c.g.fillRect(0, 0, SIZE, SIZE);
        // Ribs crossing the vault
        c.g.setColor(t.ceilDetail);
        c.g.setStroke(new BasicStroke(SIZE / 22f));
        c.g.drawLine(0, SIZE / 2, SIZE, SIZE / 2);
        c.g.setStroke(new BasicStroke(SIZE / 30f));
        c.g.setColor(new Color(t.ceilDetail.getRed(), t.ceilDetail.getGreen(),
                               t.ceilDetail.getBlue(), 120));
        c.g.drawLine(SIZE / 2, 0, SIZE / 2, SIZE);
        // Soot and damp
        for (int i = 0; i < 26; i++) {
            int h = hash(211, i, 2, 7);
            c.g.setColor(new Color(0, 0, 0, 40));
            c.g.fillOval(h & MASK, (h >> 7) & MASK, 6 + ((h >> 14) & 11), 5 + ((h >> 17) & 9));
        }
        return c.pixels();
    }

    private static int[] doorPage(DungeonTheme t) {
        Canvas c = new Canvas(t.doorFar);
        Graphics2D g = c.g;
        g.setColor(t.doorNear);
        g.fillRect(0, 0, SIZE, SIZE);

        // Vertical planks
        g.setColor(dark(t.doorFar, 0.9f));
        g.setStroke(new BasicStroke(2f));
        for (int i = 1; i < 5; i++) g.drawLine(i * SIZE / 5, 0, i * SIZE / 5, SIZE);

        // Iron bands and studs
        g.setColor(t.doorFrame);
        g.fillRect(0, SIZE / 5, SIZE, SIZE / 14);
        g.fillRect(0, SIZE * 3 / 4, SIZE, SIZE / 14);
        g.setColor(shade(t.doorFrame, 1.3f));
        for (int i = 0; i < 5; i++) {
            int x = SIZE * (i * 2 + 1) / 10;
            g.fillOval(x - 3, SIZE / 5 + 1, 6, 6);
            g.fillOval(x - 3, SIZE * 3 / 4 + 1, 6, 6);
        }
        // Ring handle
        g.setStroke(new BasicStroke(4f));
        g.drawOval(SIZE * 5 / 8, SIZE / 2 - 10, 20, 20);
        return c.pixels();
    }

    // ── Block helpers ────────────────────────────────────────────────────────

    private static void block(Canvas c, DungeonTheme t, int x0, int y0, int x1, int y1,
                              int h, float heightFrac) {
        Polygon p = new Polygon(new int[]{x0 + 1, x1 - 1, x1 - 1, x0 + 1},
                                new int[]{y0 + 1, y0 + 1, y1 - 1, y1 - 1}, 4);
        stone(c, t, p, h, heightFrac);
    }

    /** Fills one stone and gives it a lit top-left edge, a shadowed bottom-right, and grain. */
    private static void stone(Canvas c, DungeonTheme t, Polygon p, int h, float heightFrac) {
        stone(c, t, p, h, heightFrac, 165);
    }

    /**
     * @param edgeAlpha strength of the lit chamfer. Irregular stones need this low: drawing a
     *                  bright edge on every polygon of a jittered lattice turns the wall into a
     *                  visible net instead of tight-fitted blocks.
     */
    private static void stone(Canvas c, DungeonTheme t, Polygon p, int h, float heightFrac,
                              int edgeAlpha) {
        Graphics2D g = c.g;
        float vary = 0.86f + ((h & 63) / 63f) * 0.30f;
        float ramp = 0.94f + 0.12f * (1f - heightFrac);
        boolean sunken = (h % 17) == 0;
        g.setColor(shade(t.wallNear, vary * ramp * (sunken ? 0.7f : 1f)));
        g.fillPolygon(p);

        g.setStroke(new BasicStroke(2f));
        g.setColor(new Color(t.wallLight.getRed(), t.wallLight.getGreen(), t.wallLight.getBlue(),
                             sunken ? edgeAlpha / 2 : edgeAlpha));
        g.drawLine(p.xpoints[0], p.ypoints[0], p.xpoints[1], p.ypoints[1]);
        g.drawLine(p.xpoints[0], p.ypoints[0], p.xpoints[3], p.ypoints[3]);
        g.setColor(new Color(t.wallDark.getRed(), t.wallDark.getGreen(), t.wallDark.getBlue(), 190));
        g.drawLine(p.xpoints[3], p.ypoints[3], p.xpoints[2], p.ypoints[2]);
        g.drawLine(p.xpoints[1], p.ypoints[1], p.xpoints[2], p.ypoints[2]);

        java.awt.Rectangle b = p.getBounds();
        // Grain
        for (int i = 0; i < 14; i++) {
            int hh = hash(h, i, 23, 5);
            int gx = b.x + (hh & 63) % Math.max(1, b.width);
            int gy = b.y + ((hh >> 6) & 63) % Math.max(1, b.height);
            if (!p.contains(gx, gy)) continue;
            g.setColor(((hh >> 12) & 1) == 0
                    ? new Color(t.wallDark.getRed(), t.wallDark.getGreen(), t.wallDark.getBlue(), 55)
                    : new Color(t.wallHigh.getRed(), t.wallHigh.getGreen(), t.wallHigh.getBlue(), 45));
            g.fillRect(gx, gy, 1 + ((hh >> 13) & 1), 1);
        }
        // Crack
        if ((h % 6) == 0 && b.width > 12) {
            g.setStroke(new BasicStroke(1f));
            g.setColor(new Color(t.wallDark.getRed(), t.wallDark.getGreen(), t.wallDark.getBlue(), 200));
            int cx = b.x + b.width / 2, cy = b.y + b.height / 2;
            g.drawLine(cx - b.width / 3, cy - b.height / 3, cx + ((h >> 5) & 3) - 1, cy);
            g.drawLine(cx + ((h >> 5) & 3) - 1, cy, cx + b.width / 3, cy + b.height / 3);
        }
    }

    // ── Canvas ───────────────────────────────────────────────────────────────

    /** A page under construction: a BufferedImage plus direct access to its int[] backing. */
    private static final class Canvas {
        final BufferedImage img;
        final Graphics2D g;

        Canvas(Color fill) {
            img = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_RGB);
            g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(fill);
            g.fillRect(0, 0, SIZE, SIZE);
        }

        static Canvas over(int[] existing) {
            Canvas c = new Canvas(Color.BLACK);
            System.arraycopy(existing, 0, c.pixelsRaw(), 0, existing.length);
            return c;
        }

        /** Large-scale damp staining, so a tiled wall does not read as one repeated stamp. */
        void grime(DungeonTheme t, int seed) {
            for (int i = 0; i < 5; i++) {
                int h = hash(seed, i, 41, 3);
                int r = SIZE / 4 + ((h >> 4) & 31);
                g.setColor(new Color(0, 0, 0, 26));
                g.fillOval((h & MASK) - r / 2, ((h >> 8) & MASK) - r / 2, r, r);
            }
            // Growth creeping from the bottom edge
            for (int i = 0; i < 7; i++) {
                int h = hash(seed, i, 59, 11);
                int w = SIZE / 8 + ((h >> 3) & 15);
                g.setColor(new Color(t.wallMoss.getRed(), t.wallMoss.getGreen(), t.wallMoss.getBlue(), 70));
                g.fillOval(h & MASK, SIZE - (h >> 9 & 15) - 8, w, 10 + ((h >> 12) & 7));
            }
        }

        int[] pixelsRaw() { return ((DataBufferInt) img.getRaster().getDataBuffer()).getData(); }

        int[] pixels() {
            g.dispose();
            return pixelsRaw();
        }
    }

    // ── Colour helpers ───────────────────────────────────────────────────────

    private static Color shade(Color c, float f) {
        return new Color(clamp(c.getRed() * f), clamp(c.getGreen() * f), clamp(c.getBlue() * f));
    }

    private static Color dark(Color c, float f) { return shade(c, f); }

    private static Color mix(Color a, Color b, float t) {
        return new Color(
            clamp(a.getRed()   + (b.getRed()   - a.getRed())   * t),
            clamp(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
            clamp(a.getBlue()  + (b.getBlue()  - a.getBlue())  * t));
    }

    private static int clamp(float v) { return (int) Math.max(0, Math.min(255, v)); }

    static int hash(int a, int b, int c, int d) {
        int h = a * 374761393 + b * 668265263 + c * 2147483647 + d * 1274126177;
        h = (h ^ (h >>> 13)) * 1274126177;
        return (h ^ (h >>> 16)) & 0x7fffffff;
    }
}

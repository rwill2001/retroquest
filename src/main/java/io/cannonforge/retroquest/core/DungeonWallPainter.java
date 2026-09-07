package io.cannonforge.retroquest.core;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.MultipleGradientPaint;
import java.awt.RadialGradientPaint;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Point2D;

/**
 * Draws stone surfaces for the first-person {@link TexturedDungeonRenderer}.
 *
 * <h2>Why this is not just "draw some lines on a trapezoid"</h2>
 * A corridor wall recedes from the camera, so masonry laid on it has to foreshorten: the courses
 * must bunch up towards the vanishing point, not divide the on-screen trapezoid evenly. Every
 * position here is therefore expressed in <em>world</em> depth {@code z} (1.0 = one dungeon tile)
 * and projected through {@link Corridor}, which reproduces the renderer's existing
 * {@code scale(z) = 1 / (1 + 0.8z)} exactly. Interpolating in screen space instead — which is what
 * the bark renderer does for its grain lines — makes near blocks too small and far blocks too big.
 *
 * <p>Relief comes from bevelling: each block is inset into a mortar bed and gets a light edge on
 * its top and near side and a dark edge on its bottom and far side. That single trick is most of
 * what makes early-90s crawler walls read as carved stone rather than as wallpaper.
 *
 * <p>Everything is deterministic — block colour variation, cracks, weathering and sconce placement
 * all hash from the dungeon cell coordinate, so a given wall looks identical every frame and from
 * every distance, and no per-cell state has to be stored.
 */
public final class DungeonWallPainter {

    /** Tiles ahead the corridor renders. Must match {@link TexturedDungeonRenderer}'s own depth. */
    public static final int MAX_DEPTH = 4;

    /** Below this on-screen block size the bevel and weathering detail is skipped. */
    private static final float DETAIL_CUTOFF_PX = 5f;

    private DungeonWallPainter() {}

    // ── Projection ───────────────────────────────────────────────────────────

    /**
     * The corridor's perspective projection. Reproduces the depth-slice maths the renderer has
     * always used, but as a continuous function of {@code z} so texture detail can be placed
     * between the slice boundaries.
     */
    public static final class Corridor {
        public final int cx, cy;
        public final float halfW, halfH;

        public Corridor(int w, int h) {
            this.cx = w / 2;
            this.cy = h / 2;
            this.halfW = w * 0.48f;
            this.halfH = h * 0.48f;
        }

        public float scale(float z)  { return 1f / (1f + z * 0.8f); }
        public float left(float z)   { return cx - halfW * scale(z); }
        public float right(float z)  { return cx + halfW * scale(z); }
        public float top(float z)    { return cy - halfH * scale(z); }
        public float bottom(float z) { return cy + halfH * scale(z); }

        /** X of the given side's wall plane at depth {@code z}. */
        public float edge(float z, boolean leftSide) { return leftSide ? left(z) : right(z); }

        /** Screen Y at depth {@code z} and wall-height fraction {@code h} (0 = ceiling, 1 = floor). */
        public float atHeight(float z, float h) {
            float t = top(z);
            return t + (bottom(z) - t) * h;
        }

        /** Screen X across the corridor at depth {@code z}, {@code u} 0 = left wall, 1 = right. */
        public float across(float z, float u) {
            float l = left(z);
            return l + (right(z) - l) * u;
        }

        // Integer forms, matching the renderer's existing (int) casts so the slice boundaries
        // it precomputes and the texture drawn here land on exactly the same pixels.
        public int leftI(float z)   { return cx - (int) (halfW * scale(z)); }
        public int rightI(float z)  { return cx + (int) (halfW * scale(z)); }
        public int topI(float z)    { return cy - (int) (halfH * scale(z)); }
        public int bottomI(float z) { return cy + (int) (halfH * scale(z)); }
    }

    /**
     * Distance darkening. Deliberately not linear: a torch-lit crypt falls off fast, so the first
     * tile keeps most of its brightness and everything past the second drops towards black. A
     * linear ramp left the far dead-end wall as legible as the stone at the player's shoulder,
     * which is what made the corridor read as flat.
     */
    private static float fogAt(float z) {
        float t = Math.min(1f, Math.max(0f, z / MAX_DEPTH));
        return (float) Math.pow(t, 0.72);
    }

    // ── Side walls ───────────────────────────────────────────────────────────

    /**
     * Paints one depth band of a side wall as foreshortened masonry.
     *
     * @param band      depth slice; the wall spans world depth {@code band} to {@code band + 1}
     * @param leftSide  true for the wall on the player's left
     * @param cellX,cellY dungeon cell the wall belongs to — seeds all deterministic detail
     * @param now       animation clock, for sconce flicker
     */
    public static void sideWall(Graphics2D g, Corridor c, int band, boolean leftSide,
                                DungeonTheme t, int cellX, int cellY, long now) {
        float z0 = band, z1 = band + 1;
        float xNear = c.edge(z0, leftSide), xFar = c.edge(z1, leftSide);
        float span = Math.abs(xFar - xNear);

        // Mortar bed — every block is inset into this, so the joints need no separate drawing.
        fillQuad(g, shade(t.mortar, t.wallFar, fogAt(z0 + 0.5f)),
                 xNear, c.top(z0), xFar, c.top(z1), xFar, c.bottom(z1), xNear, c.bottom(z0));

        if (span < 1.5f) return;

        Layout lay = Layout.forStyle(t.wallStyle);
        Object aa = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

        for (int row = 0; row < lay.courses; row++) {
            float h0 = row / (float) lay.courses;
            float h1 = (row + 1) / (float) lay.courses;
            float stagger = (row % 2 == 0) ? 0f : 0.5f / lay.cols;

            for (int col = -1; col <= lay.cols; col++) {
                float za = z0 + col / (float) lay.cols + stagger;
                float zb = za + 1f / lay.cols;
                za = Math.max(za, z0);
                zb = Math.min(zb, z1);
                if (zb - za < 0.002f) continue;

                block(g, c, t, lay, za, zb, h0, h1, leftSide, cellX, cellY, row, col);
            }
        }

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, aa);

        dampPatches(g, quad(xNear, c.top(z0), xFar, c.top(z1), xFar, c.bottom(z1), xNear, c.bottom(z0)),
                    cellX, cellY, fogAt(z0 + 0.5f), leftSide ? 601 : 607);
        contactShadow(g, c, z0, z1, leftSide);

        if (t.sconces && band < 3 && sconceHere(cellX, cellY, leftSide, t)) {
            sconce(g, c, t, (z0 + z1) * 0.5f, leftSide, cellX, cellY, now);
        }
    }

    /**
     * Darkens the wall where it meets the floor and ceiling. Light never reaches fully into a
     * corner, and without this the masonry, floor and ceiling all read as the same flat plane
     * meeting at a hard line.
     */
    private static void contactShadow(Graphics2D g, Corridor c, float z0, float z1, boolean leftSide) {
        float xa = c.edge(z0, leftSide), xb = c.edge(z1, leftSide);
        int steps = 4;
        for (int i = 0; i < steps; i++) {
            float f0 = i / (float) steps, f1 = (i + 1) / (float) steps;
            // Strips run outwards from the middle of the wall, so the last one — the one actually
            // touching the floor or ceiling — has to be the darkest.
            int a = (int) (46 * f1);
            if (a <= 2) continue;
            g.setColor(new Color(0, 0, 0, a));

            // Floor junction — the deeper, heavier of the two.
            float hb0 = 1f - 0.16f * (1f - f0), hb1 = 1f - 0.16f * (1f - f1);
            fillQuad(g, xa, c.atHeight(z0, hb0), xb, c.atHeight(z1, hb0),
                     xb, c.atHeight(z1, hb1), xa, c.atHeight(z0, hb1));

            // Ceiling junction.
            g.setColor(new Color(0, 0, 0, (int) (a * 0.75f)));
            float ht0 = 0.11f * (1f - f0), ht1 = 0.11f * (1f - f1);
            fillQuad(g, xa, c.atHeight(z0, ht0), xb, c.atHeight(z1, ht0),
                     xb, c.atHeight(z1, ht1), xa, c.atHeight(z0, ht1));
        }
    }

    /** One masonry block on a side wall, drawn between two depths and two wall-height fractions. */
    private static void block(Graphics2D g, Corridor c, DungeonTheme t, Layout lay,
                              float za, float zb, float h0, float h1, boolean leftSide,
                              int cellX, int cellY, int row, int col) {
        float xa = c.edge(za, leftSide), xb = c.edge(zb, leftSide);
        float ya0 = c.atHeight(za, h0), ya1 = c.atHeight(za, h1);
        float yb0 = c.atHeight(zb, h0), yb1 = c.atHeight(zb, h1);

        float w = Math.abs(xb - xa);
        float hPx = Math.abs(ya1 - ya0);
        if (w < 0.6f || hPx < 0.6f) return;

        int seed = hash(cellX, cellY, (leftSide ? 1 : 2) * 31 + row, col);
        float fog = fogAt((za + zb) * 0.5f);

        // Face colour: per-block variation, plus a soft vertical ramp so the wall is lighter
        // near the ceiling (ambient bounce) and grimier at the floor.
        float vary = 1f + ((seed & 63) / 63f - 0.5f) * lay.variance;
        float ramp = 0.90f + 0.18f * (1f - h0);
        // Every so often a block sits deeper in the course than its neighbours. Costs nothing and
        // does more to break up a repeating wall than any amount of extra noise.
        boolean sunken = (seed % 17) == 0;
        if (sunken) vary *= 0.62f;
        Color face = shade(scale(t.wallNear, vary * ramp), t.wallFar, fog);

        float inset = Math.min(Math.max(Math.min(w, hPx) * 0.07f, 0.35f), 2.2f);
        float[] px = {xa, xb, xb, xa};
        float[] py = {ya0, yb0, yb1, ya1};
        jitterQuad(px, py, lay.jitter, seed, w, hPx);
        insetQuad(px, py, inset);

        g.setColor(face);
        fillQuad(g, px, py);

        if (Math.min(w, hPx) >= DETAIL_CUTOFF_PX) {
            relief(g, t, lay, px, py, seed, fog, h0, w, hPx, sunken);
        }
    }

    /**
     * Turns a filled quad into a carved stone.
     *
     * <p>Vertex order is the same for side and front walls — 0 top-near, 1 top-far, 2 bottom-far,
     * 3 bottom-near — so the light always falls from above and from the viewer's side.
     *
     * <p>The chamfers are filled wedges rather than 1px lines: a hairline outline reads as a drawn
     * border, while a wedge several pixels deep reads as a bevelled edge catching the light. That
     * plus the stipple is the difference between painted rectangles and cut stone.
     */
    private static void relief(Graphics2D g, DungeonTheme t, Layout lay, float[] px, float[] py,
                               int seed, float fog, float h0, float w, float hPx, boolean sunken) {
        float cx = (px[0] + px[1] + px[2] + px[3]) / 4f;
        float cy = (py[0] + py[1] + py[2] + py[3]) / 4f;

        float radius = 0f;
        for (int i = 0; i < 4; i++) {
            radius += (float) Math.hypot(px[i] - cx, py[i] - cy);
        }
        radius /= 4f;
        if (radius < 0.5f) return;

        float chamfer = Math.min(4.5f, Math.max(1f, Math.min(w, hPx) * 0.15f));
        float f = Math.min(0.42f, chamfer / radius);

        Color lit   = alpha(shade(t.wallLight, t.wallFar, fog), 205);
        Color litLo = alpha(shade(t.wallLight, t.wallFar, fog), 120);
        Color dark  = alpha(shade(t.wallDark, t.wallFar, fog), 225);
        Color darkLo = alpha(shade(t.wallDark, t.wallFar, fog), 160);

        // A block set deeper than its course catches the light on the opposite edges, which is
        // what makes it read as recessed rather than merely darker.
        chamferEdge(g, px, py, 0, 1, cx, cy, f, sunken ? dark : lit);       // top
        chamferEdge(g, px, py, 0, 3, cx, cy, f, sunken ? darkLo : litLo);   // near side
        chamferEdge(g, px, py, 3, 2, cx, cy, f, sunken ? lit : dark);       // bottom
        chamferEdge(g, px, py, 1, 2, cx, cy, f, sunken ? litLo : darkLo);   // far side

        stipple(g, t, px, py, seed, fog, w, hPx);
        weather(g, t, lay, px, py, seed, fog, h0);
    }

    /**
     * Large-scale damp staining across a whole wall face. Per-block noise alone still leaves a
     * regular grid, because every block gets the same treatment; a few soft patches spanning
     * several courses is what makes one stretch of wall look unlike the next.
     */
    private static void dampPatches(Graphics2D g, Polygon area, int cellX, int cellY,
                                    float fog, int salt) {
        if (fog > 0.78f) return;
        int a = (int) (30 * (1f - fog));
        if (a < 3) return;

        Shape old = g.getClip();
        g.clip(area);
        Rectangle b = area.getBounds();
        try {
            for (int i = 0; i < 3; i++) {
                int h = hash(cellX, cellY, salt, i);
                float rx = b.x + (h & 255) / 255f * b.width;
                float ry = b.y + ((h >> 8) & 255) / 255f * b.height;
                float rad = Math.max(b.width, b.height) * (0.24f + ((h >> 16) & 15) / 60f);
                if (rad < 2f) continue;
                int pa = ((i & 1) == 0) ? a : a * 2 / 3;
                // Hard-edged ovals read as painted circles; the falloff is what makes them stain.
                g.setPaint(new RadialGradientPaint(
                        new Point2D.Float(rx, ry), rad,
                        new float[]{0f, 0.55f, 1f},
                        new Color[]{new Color(0, 0, 0, pa), new Color(0, 0, 0, pa / 2),
                                    new Color(0, 0, 0, 0)},
                        MultipleGradientPaint.CycleMethod.NO_CYCLE));
                g.fillOval((int) (rx - rad), (int) (ry - rad), (int) (rad * 2), (int) (rad * 2));
            }
        } finally {
            g.setPaint(null);
            g.setClip(old);
        }
    }

    private static Polygon quad(float x0, float y0, float x1, float y1,
                                float x2, float y2, float x3, float y3) {
        return new Polygon(
            new int[]{Math.round(x0), Math.round(x1), Math.round(x2), Math.round(x3)},
            new int[]{Math.round(y0), Math.round(y1), Math.round(y2), Math.round(y3)}, 4);
    }

    /** Fills a wedge along one edge of a quad, tapering inwards towards the centroid. */
    private static void chamferEdge(Graphics2D g, float[] px, float[] py, int i, int j,
                                    float cx, float cy, float f, Color col) {
        float ix = px[i] + (cx - px[i]) * f, iy = py[i] + (cy - py[i]) * f;
        float jx = px[j] + (cx - px[j]) * f, jy = py[j] + (cy - py[j]) * f;
        g.setColor(col);
        fillQuad(g, px[i], py[i], px[j], py[j], jx, jy, ix, iy);
    }

    /** Granite grain: a scatter of light and dark specks so the block face is never one flat tone. */
    private static void stipple(Graphics2D g, DungeonTheme t, float[] px, float[] py,
                                int seed, float fog, float w, float h) {
        if (w < 9f || h < 7f || fog > 0.82f) return;
        float cx = (px[0] + px[1] + px[2] + px[3]) / 4f;
        float cy = (py[0] + py[1] + py[2] + py[3]) / 4f;

        int n = 5 + (seed & 3);
        int fade = (int) (58 * (1f - fog));
        Color light = alpha(shade(t.wallHigh, t.wallFar, fog), Math.max(8, fade / 2));
        Color dark  = alpha(shade(t.wallDark, t.wallFar, fog), Math.max(10, fade));

        for (int i = 0; i < n; i++) {
            int bits = hash(seed, i, 29, 83);
            float rx = ((bits & 255) / 255f - 0.5f) * 0.76f;
            float ry = (((bits >> 8) & 255) / 255f - 0.5f) * 0.70f;
            int size = 1 + ((bits >> 16) & 1);
            g.setColor(((bits >> 17) & 1) == 0 ? dark : light);
            g.fillRect((int) (cx + rx * w), (int) (cy + ry * h), size, size);
        }
    }

    /** Cracks, chips and growth — the detail that keeps repeated blocks from reading as a grid. */
    private static void weather(Graphics2D g, DungeonTheme t, Layout lay,
                                float[] px, float[] py, int seed, float fog, float h0) {
        float cx = (px[0] + px[1] + px[2] + px[3]) / 4f;
        float cy = (py[0] + py[1] + py[2] + py[3]) / 4f;
        float w = Math.abs(px[1] - px[0]);
        float h = Math.abs(py[3] - py[0]);

        // Hairline crack across roughly one block in seven. The stroke has to be set explicitly:
        // the sconce leaves a stroke several pixels wide behind it, which turned these into
        // heavy black gashes on every wall drawn after a lit one.
        if ((seed % 7) == 0) {
            g.setStroke(new BasicStroke(1f));
            g.setColor(alpha(shade(t.wallDark, t.wallFar, fog), 190));
            float x1 = cx - w * 0.28f, y1 = cy - h * 0.30f;
            float xm = cx + w * ((seed >> 3 & 3) - 1.5f) * 0.10f, ym = cy;
            float x2 = cx + w * 0.24f, y2 = cy + h * 0.32f;
            g.drawLine((int) x1, (int) y1, (int) xm, (int) ym);
            g.drawLine((int) xm, (int) ym, (int) x2, (int) y2);
        }

        // Seepage stain running down from the joint above.
        if ((seed % 6) == 0 && w > 10 && h > 8 && fog < 0.7f) {
            int a = (int) (52 * (1f - fog));
            g.setColor(alpha(shade(t.wallDark, t.wallFar, fog), a));
            float sx = cx + w * (((seed >> 7) & 7) / 7f - 0.5f) * 0.6f;
            float sw = Math.max(1f, w * 0.10f);
            g.fillRect((int) sx, (int) (cy - h * 0.5f), (int) sw, (int) (h * 0.72f));
            g.setColor(alpha(shade(t.wallDark, t.wallFar, fog), a / 2));
            g.fillRect((int) (sx + sw), (int) (cy - h * 0.5f), (int) Math.max(1f, sw * 0.6f),
                       (int) (h * 0.45f));
        }

        // Chipped corner.
        if ((seed % 11) == 0 && w > 8) {
            g.setColor(alpha(shade(t.wallDark, t.wallFar, fog), 140));
            g.fillPolygon(
                new int[]{(int) px[1], (int) (px[1] - w * 0.22f), (int) px[1]},
                new int[]{(int) py[1], (int) py[1], (int) (py[1] + h * 0.26f)}, 3);
        }

        // Algae / moss / lichen — clings to the damp lower courses and creeps out of the joints.
        if (lay.growth && (seed % 4) == 0 && fog < 0.75f) {
            int a = (int) (105 * (1f - fog) * (0.40f + 0.60f * h0));
            if (a > 6) {
                g.setColor(alpha(t.wallMoss, a));
                float gw = w * 0.62f, gh = h * 0.34f;
                g.fillOval((int) (cx - gw / 2 + w * 0.10f), (int) (cy + h * 0.10f),
                           (int) gw, (int) gh);
                // A second, denser tuft sitting right on the bottom joint.
                g.setColor(alpha(t.wallMoss, Math.min(255, a * 3 / 2)));
                g.fillOval((int) (cx - w * 0.30f), (int) (cy + h * 0.28f),
                           (int) (w * 0.40f), (int) (h * 0.24f));
            }
        }

        // Mineral veining — polished stone gets threads of something else running through it
        // instead of the growth that clings to rough masonry.
        if (lay.veins && (seed % 3) == 0 && w > 8 && fog < 0.8f) {
            g.setStroke(new BasicStroke(Math.max(1f, w * 0.02f)));
            g.setColor(alpha(t.wallMoss, (int) (120 * (1f - fog))));
            int n = 1 + (seed >> 9 & 1);
            for (int i = 0; i < n; i++) {
                int b = hash(seed, i, 53, 97);
                float y0 = cy + h * (((b & 15) / 15f) - 0.5f) * 0.8f;
                float y1 = cy + h * ((((b >> 4) & 15) / 15f) - 0.5f) * 0.8f;
                float ym = (y0 + y1) / 2f + h * ((((b >> 8) & 7) / 7f) - 0.5f) * 0.3f;
                g.drawLine((int) (cx - w * 0.44f), (int) y0, (int) cx, (int) ym);
                g.drawLine((int) cx, (int) ym, (int) (cx + w * 0.44f), (int) y1);
            }
        }

        // Mineral bloom / barnacle crust — small bright specks, rarer and only up close.
        if ((seed % 13) == 0 && fog < 0.45f && w > 10) {
            g.setColor(alpha(t.wallHigh, 90));
            int n = 2 + (seed >> 5 & 1);
            for (int i = 0; i < n; i++) {
                float fx = cx + w * (((seed >> (i * 3 + 2)) & 7) / 7f - 0.5f) * 0.7f;
                float fy = cy + h * (((seed >> (i * 3 + 5)) & 7) / 7f - 0.5f) * 0.7f;
                int r = 1 + (i & 1);
                g.fillOval((int) fx, (int) fy, r + 1, r + 1);
            }
        }
    }

    // ── Front walls ──────────────────────────────────────────────────────────

    /** Paints a wall square-on to the camera: same masonry, no foreshortening, plus carving. */
    public static void frontWall(Graphics2D g, int l, int top, int r, int bot,
                                 DungeonTheme t, float fog, int cellX, int cellY, long now) {
        int w = r - l, h = bot - top;
        if (w <= 0 || h <= 0) return;

        g.setColor(shade(t.mortar, t.wallFar, fog));
        g.fillRect(l, top, w, h);

        Layout lay = Layout.forStyle(t.wallStyle);
        int courses = lay.courses;
        int cols = Math.max(2, lay.cols + 1);

        Object aa = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

        for (int row = 0; row < courses; row++) {
            float y0 = top + h * row / (float) courses;
            float y1 = top + h * (row + 1) / (float) courses;
            float stagger = (row % 2 == 0) ? 0f : 0.5f / cols;

            for (int col = -1; col <= cols; col++) {
                float x0 = l + w * (col / (float) cols + stagger);
                float x1 = x0 + w / (float) cols;
                x0 = Math.max(x0, l);
                x1 = Math.min(x1, r);
                if (x1 - x0 < 0.8f) continue;

                int seed = hash(cellX, cellY, 97 + row, col);
                float vary = 1f + ((seed & 63) / 63f - 0.5f) * lay.variance;
                float ramp = 0.90f + 0.18f * (1f - row / (float) courses);
                boolean sunken = (seed % 17) == 0;
                if (sunken) vary *= 0.62f;
                Color face = shade(scale(t.wallNear, vary * ramp), t.wallFar, fog);

                float bw = x1 - x0, bh = y1 - y0;
                float inset = Math.min(Math.max(Math.min(bw, bh) * 0.07f, 0.4f), 2.4f);
                float[] px = {x0, x1, x1, x0};
                float[] py = {y0, y0, y1, y1};
                jitterQuad(px, py, lay.jitter, seed, bw, bh);
                insetQuad(px, py, inset);

                g.setColor(face);
                fillQuad(g, px, py);

                if (Math.min(bw, bh) >= DETAIL_CUTOFF_PX) {
                    relief(g, t, lay, px, py, seed, fog, row / (float) courses, bw, bh, sunken);
                }
            }
        }

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, aa);

        dampPatches(g, quad(l, top, r, top, r, bot, l, bot), cellX, cellY, fog, 613);

        // A dead-end deserves something to look at.
        if (fog < 0.55f && w > 60 && (hash(cellX, cellY, 7, 7) % 3) == 0) {
            medallion(g, l + w / 2, top + h / 2, Math.min(w, h) / 5, t, fog);
        }

        g.setStroke(new BasicStroke(1.5f));
        g.setColor(alpha(shade(t.wallHigh, t.wallFar, fog), 70));
        g.drawRect(l, top, w, h);
    }

    /** A carved relief set into a dead-end wall: recessed disc, rim, and a chiselled sigil. */
    private static void medallion(Graphics2D g, int cx, int cy, int r, DungeonTheme t, float fog) {
        if (r < 8) return;
        g.setColor(alpha(shade(t.wallDark, t.wallFar, fog), 210));
        g.fillOval(cx - r, cy - r, r * 2, r * 2);
        g.setStroke(new BasicStroke(2f));
        g.setColor(alpha(shade(t.wallLight, t.wallFar, fog), 140));
        g.drawArc(cx - r, cy - r, r * 2, r * 2, 40, 180);
        g.setColor(alpha(shade(t.wallDark, t.wallFar, fog), 200));
        g.drawArc(cx - r, cy - r, r * 2, r * 2, 220, 180);

        g.setStroke(new BasicStroke(Math.max(1.5f, r / 8f)));
        g.setColor(alpha(shade(t.wallHigh, t.wallFar, fog), 120));
        int ir = r / 2;
        g.drawOval(cx - ir, cy - ir, ir * 2, ir * 2);
        g.drawLine(cx, cy - r + r / 5, cx, cy + r - r / 5);
        g.drawLine(cx - r + r / 5, cy, cx + r - r / 5, cy);
    }

    // ── Doors ────────────────────────────────────────────────────────────────

    /** A front door: arched stone surround with voussoirs over a banded, studded slab. */
    public static void frontDoor(Graphics2D g, int l, int top, int r, int bot,
                                 DungeonTheme t, float fog, int cellX, int cellY) {
        int w = r - l, h = bot - top;
        if (w <= 0 || h <= 0) return;

        frontWall(g, l, top, r, bot, t, fog, cellX * 7 + 3, cellY * 5 + 1, 0L);

        int inset = Math.max(4, w / 6);
        int dl = l + inset, dr = r - inset;
        int dt = top + inset, db = bot - Math.max(1, inset / 3);
        int dw = dr - dl, dh = db - dt;
        if (dw < 4 || dh < 4) return;

        // Recessed opening
        g.setColor(alpha(shade(t.wallDark, t.wallFar, fog), 235));
        g.fillRect(dl - 2, dt - 2, dw + 4, dh + 4);

        // Door leaf, darker towards the floor
        Color leaf = shade(t.doorNear, t.doorFar, fog);
        g.setColor(leaf);
        g.fillRect(dl, dt, dw, dh);
        g.setColor(alpha(scale(leaf, 0.55f), 170));
        g.fillRect(dl, dt + dh * 2 / 3, dw, dh / 3);

        // Vertical planks
        g.setStroke(new BasicStroke(1f));
        g.setColor(alpha(scale(leaf, 0.5f), 190));
        for (int i = 1; i < 4; i++) {
            int x = dl + dw * i / 4;
            g.drawLine(x, dt, x, db);
        }

        // Iron bands and studs
        Color iron = shade(t.doorFrame, t.doorFar, fog);
        g.setStroke(new BasicStroke(Math.max(2f, dh / 22f)));
        g.setColor(alpha(iron, 220));
        int b1 = dt + dh / 5, b2 = db - dh / 4;
        g.drawLine(dl, b1, dr, b1);
        g.drawLine(dl, b2, dr, b2);
        if (dw > 26) {
            g.setColor(alpha(scale(iron, 1.25f), 200));
            for (int i = 0; i < 4; i++) {
                int x = dl + dw * (i * 2 + 1) / 8;
                g.fillOval(x - 2, b1 - 2, 4, 4);
                g.fillOval(x - 2, b2 - 2, 4, 4);
            }
        }

        // Voussoir arch over the opening
        if (dw > 20) {
            int ar = dw / 2;
            int acy = dt;
            g.setStroke(new BasicStroke(1f));
            for (int i = 0; i < 7; i++) {
                double a0 = Math.PI * i / 7.0, a1 = Math.PI * (i + 1) / 7.0;
                int[] xs = {
                    (int) (dl + ar - Math.cos(a0) * ar), (int) (dl + ar - Math.cos(a1) * ar),
                    (int) (dl + ar - Math.cos(a1) * (ar + inset)), (int) (dl + ar - Math.cos(a0) * (ar + inset))};
                int[] ys = {
                    (int) (acy - Math.sin(a0) * ar * 0.55f), (int) (acy - Math.sin(a1) * ar * 0.55f),
                    (int) (acy - Math.sin(a1) * (ar + inset) * 0.55f), (int) (acy - Math.sin(a0) * (ar + inset) * 0.55f)};
                int seed = hash(cellX, cellY, 41, i);
                g.setColor(shade(scale(t.wallNear, 1f + ((seed & 31) / 31f - 0.5f) * 0.18f), t.wallFar, fog));
                g.fillPolygon(xs, ys, 4);
                g.setColor(alpha(shade(t.wallDark, t.wallFar, fog), 160));
                g.drawPolygon(xs, ys, 4);
            }
        }

        // Ring handle
        int knobX = dl + dw * 3 / 4, knobY = (dt + db) / 2;
        int kr = Math.max(3, dw / 14);
        g.setStroke(new BasicStroke(Math.max(1.5f, kr / 2.5f)));
        g.setColor(alpha(scale(iron, 1.3f), 230));
        g.drawOval(knobX - kr, knobY - kr, kr * 2, kr * 2);
    }

    /** A door seen edge-on in a side wall: masonry with a recessed, arched opening. */
    public static void sideDoor(Graphics2D g, Corridor c, int band, boolean leftSide,
                                DungeonTheme t, int cellX, int cellY, long now) {
        sideWall(g, c, band, leftSide, t, cellX * 3 + 11, cellY * 3 + 7, now);

        float z0 = band, z1 = band + 1;
        float za = z0 + 0.18f, zb = z1 - 0.18f;
        if (zb <= za) return;
        float fog = fogAt((za + zb) * 0.5f);

        float xa = c.edge(za, leftSide), xb = c.edge(zb, leftSide);
        float ha = 0.20f, hb = 0.94f;
        float ya0 = c.atHeight(za, ha), ya1 = c.atHeight(za, hb);
        float yb0 = c.atHeight(zb, ha), yb1 = c.atHeight(zb, hb);

        // Recess
        g.setColor(alpha(shade(t.wallDark, t.wallFar, fog), 240));
        fillQuad(g, xa, ya0, xb, yb0, xb, yb1, xa, ya1);

        // Leaf, inset into the recess
        float k = 0.10f;
        float ix0 = xa + (xb - xa) * k, ix1 = xb - (xb - xa) * k;
        float iy00 = ya0 + (ya1 - ya0) * k, iy01 = ya1;
        float iy10 = yb0 + (yb1 - yb0) * k, iy11 = yb1;
        g.setColor(shade(t.doorNear, t.doorFar, fog));
        fillQuad(g, ix0, iy00, ix1, iy10, ix1, iy11, ix0, iy01);

        // Bands
        g.setStroke(new BasicStroke(Math.max(1.5f, Math.abs(iy01 - iy00) / 20f)));
        g.setColor(alpha(shade(t.doorFrame, t.doorFar, fog), 210));
        for (float hh : new float[]{0.35f, 0.70f}) {
            float y0 = iy00 + (iy01 - iy00) * hh;
            float y1 = iy10 + (iy11 - iy10) * hh;
            g.drawLine((int) ix0, (int) y0, (int) ix1, (int) y1);
        }

        // Lintel highlight
        g.setStroke(new BasicStroke(1.5f));
        g.setColor(alpha(shade(t.wallLight, t.wallFar, fog), 130));
        g.drawLine((int) xa, (int) ya0, (int) xb, (int) yb0);
    }

    // ── Openings ─────────────────────────────────────────────────────────────

    /** A side passage: a dark recess with lit stone jambs, so the gap reads as an opening. */
    public static void sideOpening(Graphics2D g, Corridor c, int band, boolean leftSide,
                                   DungeonTheme t) {
        float z0 = band, z1 = band + 1;
        float xa = c.edge(z0, leftSide), xb = c.edge(z1, leftSide);
        float recessTop = c.top(z0) + (c.top(z1) - c.top(z0)) * 0.5f;
        float recessBot = c.bottom(z0) + (c.bottom(z1) - c.bottom(z0)) * 0.5f;
        float fog = fogAt(z0 + 0.5f);

        g.setColor(t.fog);
        fillQuad(g, xa, recessTop, xa, recessBot, xb, c.bottom(z1), xb, c.top(z1));

        g.setStroke(new BasicStroke(1.5f));
        g.setColor(alpha(shade(t.wallLight, t.wallFar, fog), 120));
        g.drawLine((int) xa, (int) c.top(z0), (int) xa, (int) recessTop);
        g.drawLine((int) xa, (int) c.bottom(z0), (int) xa, (int) recessBot);
        g.setColor(alpha(shade(t.wallDark, t.wallFar, fog), 150));
        g.drawLine((int) xa, (int) recessTop, (int) xb, (int) c.top(z1));
        g.drawLine((int) xa, (int) recessBot, (int) xb, (int) c.bottom(z1));
    }

    // ── Floor and ceiling ────────────────────────────────────────────────────

    /** Flagstone floor for one depth band, laid out in world space so it foreshortens correctly. */
    public static void floorBand(Graphics2D g, Corridor c, int band, DungeonTheme t,
                                 int cellX, int cellY) {
        stoneFloorOrCeiling(g, c, band, t, cellX, cellY, true);
    }

    /** Vaulted ceiling for one depth band, with a transverse rib at each tile boundary. */
    public static void ceilingBand(Graphics2D g, Corridor c, int band, DungeonTheme t,
                                   int cellX, int cellY) {
        stoneFloorOrCeiling(g, c, band, t, cellX, cellY, false);
    }

    private static void stoneFloorOrCeiling(Graphics2D g, Corridor c, int band, DungeonTheme t,
                                            int cellX, int cellY, boolean floor) {
        float z0 = band, z1 = band + 1;
        Color near = floor ? t.floorNear : t.ceilNear;
        Color far  = floor ? t.floorFar  : t.ceilFar;
        Color gap  = floor ? t.floorGap  : t.ceilFar;

        // Base slab, so any sub-pixel seam shows joint colour rather than background.
        g.setColor(shade(gap, far, fogAt(z0 + 0.5f)));
        fillQuad(g,
                 c.left(z0), edgeY(c, z0, floor), c.right(z0), edgeY(c, z0, floor),
                 c.right(z1), edgeY(c, z1, floor), c.left(z1), edgeY(c, z1, floor));

        // The nearest band covers most of the screen, so it gets an extra row of flagstones;
        // further bands would only be drawing sub-pixel joints.
        int rows = (band == 0) ? 3 : 2;
        int cols = floor ? 7 : 6;
        Object aa = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

        for (int rz = 0; rz < rows; rz++) {
            float za = z0 + rz / (float) rows;
            float zb = z0 + (rz + 1) / (float) rows;
            float fog = fogAt((za + zb) * 0.5f);
            float stagger = (rz % 2 == 0) ? 0f : 0.5f / cols;

            for (int col = -1; col <= cols; col++) {
                float u0 = Math.max(0f, col / (float) cols + stagger);
                float u1 = Math.min(1f, u0 + 1f / cols);
                if (u1 - u0 < 0.01f) continue;

                float xa0 = c.across(za, u0), xa1 = c.across(za, u1);
                float xb0 = c.across(zb, u0), xb1 = c.across(zb, u1);
                float ya = edgeY(c, za, floor), yb = edgeY(c, zb, floor);
                if (Math.abs(ya - yb) < 0.5f && Math.abs(xa1 - xa0) < 1.2f) continue;

                int seed = hash(cellX, cellY, floor ? 211 : 307, rz * 32 + col);
                float vary = 1f + ((seed & 63) / 63f - 0.5f) * 0.16f;
                g.setColor(shade(scale(near, vary), far, fog));

                float[] px = {xa0, xa1, xb1, xb0};
                float[] py = {ya, ya, yb, yb};
                insetQuad(px, py, Math.min(1.6f, Math.max(0.3f, Math.abs(ya - yb) * 0.06f)));
                fillQuad(g, px, py);

                // Near lip catches the light, far edge falls into the joint — a raised flagstone.
                if (Math.abs(ya - yb) > 4f) {
                    g.setColor(alpha(shade(floor ? t.floorDetail : t.ceilDetail, far, fog), 130));
                    g.drawLine((int) px[0], (int) py[0], (int) px[1], (int) py[1]);
                    g.setColor(alpha(shade(gap, far, fog), 190));
                    g.drawLine((int) px[3], (int) py[3], (int) px[2], (int) py[2]);
                    if (floor && band == 0) {
                        stipple(g, t, px, py, seed, fog,
                                Math.abs(px[1] - px[0]), Math.abs(py[3] - py[0]));
                    }
                }
            }
        }
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, aa);

        // Ceiling ribs mark each tile you pass under.
        if (!floor) {
            float fog = fogAt(z0);
            g.setStroke(new BasicStroke(Math.max(1.5f, (c.bottom(z0) - c.top(z0)) / 60f)));
            g.setColor(alpha(shade(t.ceilDetail, far, fog), 150));
            g.drawLine(c.leftI(z0), c.topI(z0), c.rightI(z0), c.topI(z0));
        }
    }

    private static float edgeY(Corridor c, float z, boolean floor) {
        return floor ? c.bottom(z) : c.top(z);
    }

    // ── Sconces ──────────────────────────────────────────────────────────────

    /** True when this wall face carries a light, decided from the cell so it never flickers on/off. */
    public static boolean sconceHere(int cellX, int cellY, boolean leftSide, DungeonTheme t) {
        if (!t.sconces) return false;
        return Math.floorMod(hash(cellX, cellY, leftSide ? 5 : 9, 17), t.sconceEvery) == 0;
    }

    /**
     * A wall light and the pool it throws. The glow is a radial gradient over everything already
     * drawn in this band, so it spills onto the floor and neighbouring stone the way a real
     * bracket lamp would — the single biggest contributor to the crypt look.
     */
    private static void sconce(Graphics2D g, Corridor c, DungeonTheme t, float z,
                               boolean leftSide, int cellX, int cellY, long now) {
        float x = c.edge(z, leftSide);
        float y = c.atHeight(z, 0.34f);
        float wallH = c.bottom(z) - c.top(z);
        float size = Math.max(2f, wallH * 0.075f);
        float dir = leftSide ? 1f : -1f;
        float fx = x + dir * size * 0.9f;

        int seed = hash(cellX, cellY, leftSide ? 5 : 9, 17);
        double phase = (now % 100000) / 95.0 + (seed & 15);
        float flick = 0.82f + 0.18f * (float) Math.sin(phase)
                    + 0.08f * (float) Math.sin(phase * 2.7);
        float fog = fogAt(z);
        float strength = Math.max(0f, 1f - fog * 0.85f);
        if (strength <= 0.02f) return;

        // Light pool. Drawn over the stone already laid down in this band, so it warms the
        // surrounding blocks and spills onto the floor instead of sitting on top as a decal.
        float radius = Math.max(6f, size * 9.5f * flick);
        try {
            g.setPaint(new RadialGradientPaint(
                    new Point2D.Float(fx, y), radius,
                    new float[]{0f, 0.22f, 0.55f, 1f},
                    new Color[]{
                        alpha(t.sconceGlow, (int) (150 * strength * flick)),
                        alpha(t.sconceGlow, (int) (86 * strength * flick)),
                        alpha(t.sconceGlow, (int) (30 * strength * flick)),
                        alpha(t.sconceGlow, 0)},
                    MultipleGradientPaint.CycleMethod.NO_CYCLE));
            g.fillOval((int) (fx - radius), (int) (y - radius), (int) (radius * 2), (int) (radius * 2));
        } finally {
            g.setPaint(null);
        }

        if (size < 3f) return;

        // Iron bracket: an arm off the wall carrying a shallow bowl.
        g.setStroke(new BasicStroke(Math.max(1.2f, size / 3.5f)));
        g.setColor(alpha(shade(t.wallDark, t.wallFar, fog * 0.5f), 245));
        g.drawLine((int) x, (int) (y + size * 1.5f), (int) fx, (int) (y + size * 0.55f));
        g.drawLine((int) x, (int) (y + size * 0.2f), (int) fx, (int) (y + size * 0.55f));
        g.fillPolygon(
            new int[]{(int) (fx - size * 0.7f), (int) (fx + size * 0.7f),
                      (int) (fx + size * 0.45f), (int) (fx - size * 0.45f)},
            new int[]{(int) (y + size * 0.15f), (int) (y + size * 0.15f),
                      (int) (y + size * 0.75f), (int) (y + size * 0.75f)}, 4);
        g.setColor(alpha(t.sconceGlow, (int) (120 * strength)));
        g.drawLine((int) (fx - size * 0.6f), (int) (y + size * 0.18f),
                   (int) (fx + size * 0.6f), (int) (y + size * 0.18f));

        // Flame: three tapered layers, swaying off the vertical with the flicker.
        float sway = size * 0.22f * (float) Math.sin(phase * 1.7);
        float fh = size * (2.1f + 0.55f * flick);

        // Soft halo tight around the flame, so the polygon layers below don't read as cut paper.
        float hr = fh * 0.95f;
        try {
            g.setPaint(new RadialGradientPaint(
                    new Point2D.Float(fx, y - fh * 0.35f), hr,
                    new float[]{0f, 1f},
                    new Color[]{alpha(t.flameOuter, (int) (110 * strength)), alpha(t.flameOuter, 0)},
                    MultipleGradientPaint.CycleMethod.NO_CYCLE));
            g.fillOval((int) (fx - hr), (int) (y - fh * 0.35f - hr), (int) (hr * 2), (int) (hr * 2));
        } finally {
            g.setPaint(null);
        }

        flameShape(g, fx, y + size * 0.2f, size * 0.85f, fh,
                   sway, alpha(t.flameOuter, (int) (185 * strength)));
        flameShape(g, fx, y + size * 0.15f, size * 0.55f, fh * 0.72f,
                   sway * 1.3f, alpha(blendColors(t.flameOuter, t.flameCore, 0.5f), (int) (225 * strength)));
        flameShape(g, fx, y + size * 0.08f, size * 0.28f, fh * 0.42f,
                   sway * 1.6f, alpha(t.flameCore, (int) (250 * strength)));
    }

    /** A teardrop flame, base-centred at (x, y), leaning by {@code sway} at the tip. */
    private static void flameShape(Graphics2D g, float x, float y, float w, float h,
                                   float sway, Color col) {
        if (w < 1f || h < 1f) return;
        g.setColor(col);
        g.fillPolygon(
            new int[]{(int) (x - w), (int) (x - w * 0.55f), (int) (x + sway),
                      (int) (x + w * 0.55f), (int) (x + w)},
            new int[]{(int) y, (int) (y - h * 0.5f), (int) (y - h),
                      (int) (y - h * 0.5f), (int) y}, 5);
    }

    private static Color blendColors(Color a, Color b, float t) {
        return new Color(
            (int) (a.getRed()   + (b.getRed()   - a.getRed())   * t),
            (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t),
            (int) (a.getBlue()  + (b.getBlue()  - a.getBlue())  * t));
    }

    // ── Layout table ─────────────────────────────────────────────────────────

    /** Block counts and weathering behaviour per wall style. */
    private static final class Layout {
        final int courses, cols;
        final float variance;
        final boolean growth;
        /** Corner displacement as a fraction of block size. Non-zero breaks the masonry grid. */
        final float jitter;
        /** Mineral veining across the face — for polished stone that takes no growth. */
        final boolean veins;

        private Layout(int courses, int cols, float variance, boolean growth, float jitter,
                       boolean veins) {
            this.courses = courses; this.cols = cols;
            this.variance = variance; this.growth = growth; this.jitter = jitter;
            this.veins = veins;
        }

        static Layout forStyle(DungeonTheme.WallStyle s) {
            return switch (s) {
                case BLOCK  -> new Layout(5, 2, 0.30f, true,  0.00f, false);
                case COBBLE -> new Layout(8, 4, 0.38f, true,  0.10f, false);
                case SLAB   -> new Layout(3, 2, 0.12f, false, 0.00f, true);
                // Island 6 is drawn by the raycaster; this entry only matters if a CYCLOPEAN
                // theme is ever shown through the band renderer as a fallback.
                case CYCLOPEAN -> new Layout(5, 3, 0.30f, true, 0.22f, false);
                case BARK   -> new Layout(4, 3, 0.20f, true,  0.00f, false);
            };
        }
    }

    // ── Primitives ───────────────────────────────────────────────────────────

    private static void fillQuad(Graphics2D g, Color c,
                                 float x0, float y0, float x1, float y1,
                                 float x2, float y2, float x3, float y3) {
        g.setColor(c);
        fillQuad(g, x0, y0, x1, y1, x2, y2, x3, y3);
    }

    private static void fillQuad(Graphics2D g,
                                 float x0, float y0, float x1, float y1,
                                 float x2, float y2, float x3, float y3) {
        g.fillPolygon(new int[]{Math.round(x0), Math.round(x1), Math.round(x2), Math.round(x3)},
                      new int[]{Math.round(y0), Math.round(y1), Math.round(y2), Math.round(y3)}, 4);
    }

    private static void fillQuad(Graphics2D g, float[] px, float[] py) {
        g.fillPolygon(new int[]{Math.round(px[0]), Math.round(px[1]), Math.round(px[2]), Math.round(px[3])},
                      new int[]{Math.round(py[0]), Math.round(py[1]), Math.round(py[2]), Math.round(py[3])}, 4);
    }

    /**
     * Displaces a quad's corners so the stone stops being a rectangle. Regular courses read as
     * cut ashlar; jittered ones read as rubble or cobble picked up off a riverbed. The offsets
     * come from the block's own seed, so neighbouring stones stay put relative to each other
     * frame to frame even though they no longer line up on a grid.
     */
    private static void jitterQuad(float[] px, float[] py, float amount, int seed, float w, float h) {
        if (amount <= 0f) return;
        for (int i = 0; i < 4; i++) {
            int bits = hash(seed, i, 149, 61);
            px[i] += ((bits & 15) / 15f - 0.5f) * w * amount;
            py[i] += (((bits >> 4) & 15) / 15f - 0.5f) * h * amount;
        }
    }

    /** Shrinks a quad towards its centroid by roughly {@code d} pixels, cutting the mortar joint. */
    private static void insetQuad(float[] px, float[] py, float d) {
        float cx = (px[0] + px[1] + px[2] + px[3]) / 4f;
        float cy = (py[0] + py[1] + py[2] + py[3]) / 4f;
        for (int i = 0; i < 4; i++) {
            float dx = px[i] - cx, dy = py[i] - cy;
            float len = (float) Math.sqrt(dx * dx + dy * dy);
            if (len < 0.001f) continue;
            float f = Math.max(0f, (len - d) / len);
            px[i] = cx + dx * f;
            py[i] = cy + dy * f;
        }
    }

    /** Deterministic per-block noise. */
    private static int hash(int a, int b, int c, int d) {
        int h = a * 374761393 + b * 668265263 + c * 2147483647 + d * 1274126177;
        h = (h ^ (h >>> 13)) * 1274126177;
        return (h ^ (h >>> 16)) & 0x7fffffff;
    }

    /** Fog blend towards the far colour. */
    private static Color shade(Color near, Color far, float t) {
        t = Math.max(0f, Math.min(1f, t));
        return new Color(
            (int) (near.getRed()   + (far.getRed()   - near.getRed())   * t),
            (int) (near.getGreen() + (far.getGreen() - near.getGreen()) * t),
            (int) (near.getBlue()  + (far.getBlue()  - near.getBlue())  * t));
    }

    private static Color scale(Color c, float f) {
        return new Color(clamp(c.getRed() * f), clamp(c.getGreen() * f), clamp(c.getBlue() * f));
    }

    private static Color alpha(Color c, int a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(0, Math.min(255, a)));
    }

    private static int clamp(float v) { return (int) Math.max(0, Math.min(255, v)); }
}

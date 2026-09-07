package io.cannonforge.retroquest.core;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import io.cannonforge.retroquest.model.TileDefinition;
import io.cannonforge.retroquest.registry.TileRegistry;

/**
 * Lights for {@link RaycastDungeonRenderer}, taken from the map and shadowed against it.
 *
 * <h2>Lights come from the tiles</h2>
 * Island 6 invented its sconces: the renderer hashed a cell coordinate and decided a flame lived
 * there. But every tile in {@code tiles.json} already carries a {@code lightRadius}, and the set
 * includes exactly the fixtures a dungeon needs — lava channels, ember grates, wall torches,
 * forges, magic barriers. So from Island 7 the lights <em>are</em> the map: place a torch in
 * RetroForge and it lights the room, with its radius and its editor colour. Dungeons that author
 * no lights fall back to the hashed sconces, so nothing earlier changes.
 *
 * <h2>Why shadows are affordable</h2>
 * The expensive part of a shadow is asking "can this light see this point", and the naive version
 * — a ray per light per pixel — is hopeless at 250k pixels a frame. Two facts rescue it:
 *
 * <ul>
 *   <li>The lantern rides on the eye, and <b>a light at the eye casts no shadow you can see</b>.
 *       Every visible shadow therefore comes from a fixture.</li>
 *   <li>Fixtures do not move and neither do walls. So visibility can be computed <b>once per
 *       level</b> into a small field per light, and merely sampled while drawing.</li>
 * </ul>
 *
 * <p>What that buys: light stops leaking around corners, and a pillar or a shelf stack throws a
 * shadow across the floor that swings as you walk past it.
 *
 * <p>The cache is keyed on a fingerprint of the tile grid, so a door opening or a wall being
 * dissolved rebuilds the lighting rather than leaving a shadow hanging in the air.
 */
public final class DungeonLighting {

    /** Visibility samples per tile edge. Bilinear filtering between them softens the penumbra. */
    private static final int RES = 4;

    /** Lights are dropped past this radius; the windowed falloff has reached zero anyway. */
    private static final float MAX_RADIUS = 7f;

    /** Above this many fixtures a level is almost certainly authored wrong; stop building patches. */
    private static final int MAX_LIGHTS_PER_LEVEL = 220;

    private DungeonLighting() {}

    /** One fixture: where it is, what colour, how far it reaches, and what it can see. */
    public static final class Light {
        public float x, y, z;
        public float r, g, b;
        public float radius;
        public float strength;
        /** Strength for this frame, after flicker. Set by the renderer each frame. */
        public float strengthNow;
        public boolean flicker;
        public int seed;

        /** Visibility field in sample space, 0 = fully shadowed, 1 = lit. */
        private float[] vis;
        private int sx0, sy0, sw, sh;

        /**
         * Fraction of this light reaching a world point, bilinearly filtered.
         * Callers shading a wall should offset the sample along the surface normal — the point
         * itself lies on the boundary of a solid cell and would always read as shadowed.
         */
        public float visibilityAt(float wx, float wy) {
            if (vis == null) return 1f;
            float fx = wx * RES - sx0;
            float fy = wy * RES - sy0;
            int ix = (int) Math.floor(fx), iy = (int) Math.floor(fy);
            if (ix < 0 || iy < 0 || ix >= sw - 1 || iy >= sh - 1) return 0f;
            float tx = fx - ix, ty = fy - iy;
            int i = iy * sw + ix;
            float a = vis[i], b2 = vis[i + 1], c = vis[i + sw], d = vis[i + sw + 1];
            return (a * (1 - tx) + b2 * tx) * (1 - ty) + (c * (1 - tx) + d * tx) * ty;
        }
    }

    /** Every fixture on one dungeon level, plus the grid fingerprint they were built against. */
    public static final class Level {
        public final Light[] lights;
        final long fingerprint;

        Level(Light[] lights, long fingerprint) {
            this.lights = lights;
            this.fingerprint = fingerprint;
        }

        /** The {@code n} fixtures nearest a point — the only ones a pixel loop should consider. */
        public Light[] nearest(float px, float py, int n) {
            if (lights.length <= n) return lights;
            Light[] best = new Light[n];
            float[] bestD = new float[n];
            int count = 0;
            for (Light l : lights) {
                float dx = l.x - px, dy = l.y - py;
                float d2 = dx * dx + dy * dy;
                if (d2 > (l.radius + 1f) * (l.radius + 1f)) continue;
                if (count < n) {
                    best[count] = l; bestD[count] = d2; count++;
                } else {
                    int worst = 0;
                    for (int i = 1; i < n; i++) if (bestD[i] > bestD[worst]) worst = i;
                    if (d2 < bestD[worst]) { best[worst] = l; bestD[worst] = d2; }
                }
            }
            Light[] out = new Light[count];
            System.arraycopy(best, 0, out, 0, count);
            return out;
        }
    }

    private static String cacheKey;
    private static Level cached;

    /**
     * The lighting for a level, built on first use and reused until the tile grid changes.
     *
     * @param key stable identity for the level (dungeon name and depth)
     */
    public static Level forMap(char[][] map, String key) {
        long fp = fingerprint(map);
        if (cached != null && key.equals(cacheKey) && cached.fingerprint == fp) return cached;
        cached = build(map, fp);
        cacheKey = key;
        return cached;
    }

    /** True when the map authors any lighting of its own. */
    public static boolean hasAuthoredLights(char[][] map, String key) {
        return forMap(map, key).lights.length > 0;
    }

    // ── Building ─────────────────────────────────────────────────────────────

    private static Level build(char[][] map, long fp) {
        List<Light> found = new ArrayList<>();
        int h = map.length, w = map[0].length;

        for (int y = 0; y < h && found.size() < MAX_LIGHTS_PER_LEVEL; y++) {
            for (int x = 0; x < w && found.size() < MAX_LIGHTS_PER_LEVEL; x++) {
                TileDefinition def = TileRegistry.getByIdSafe(map[y][x]);
                int radius = def.getLightRadius();
                if (radius <= 0) continue;

                Color c = def.getEditorColor();
                float rr = c.getRed() / 255f, gg = c.getGreen() / 255f, bb = c.getBlue() / 255f;
                // Normalise so a dim editor colour still reads as a light of its own hue rather
                // than as a dim white one.
                float peak = Math.max(0.25f, Math.max(rr, Math.max(gg, bb)));
                rr /= peak; gg /= peak; bb /= peak;

                if (def.isWalkable()) {
                    // Something glowing underfoot — lava, a vent. Sits low so it uplights.
                    found.add(make(x + 0.5f, y + 0.5f, 0.14f, rr, gg, bb, radius, x, y));
                } else {
                    // A fixture on a wall. It has to sit in the open air beside its own block, or
                    // every shadow ray would start inside a solid cell and report total darkness.
                    for (int f = 0; f < 4; f++) {
                        int ox = x + DX[f], oy = y + DY[f];
                        if (ox < 0 || oy < 0 || ox >= w || oy >= h) continue;
                        if (!TileRegistry.getByIdSafe(map[oy][ox]).isWalkable()) continue;
                        found.add(make(x + 0.5f + DX[f] * 0.55f, y + 0.5f + DY[f] * 0.55f,
                                       0.58f, rr, gg, bb, radius, x * 4 + f, y));
                    }
                }
            }
        }

        for (Light l : found) buildVisibility(l, map);
        return new Level(found.toArray(new Light[0]), fp);
    }

    private static Light make(float x, float y, float z, float r, float g, float b,
                              int radius, int sx, int sy) {
        Light l = new Light();
        l.x = x; l.y = y; l.z = z;
        l.r = r; l.g = g; l.b = b;
        l.radius = Math.min(MAX_RADIUS, radius);
        // Authored radius drives reach; strength is scaled so a big lamp is not also a sun.
        l.strength = 1.05f + radius * 0.16f;
        l.strengthNow = l.strength;
        l.flicker = true;
        l.seed = DungeonTextures.hash(sx, sy, radius, 19);
        return l;
    }

    private static final int[] DX = {1, -1, 0, 0};
    private static final int[] DY = {0, 0, 1, -1};

    /** Traces this light's reach once, recording what it can see. */
    private static void buildVisibility(Light l, char[][] map) {
        int mh = map.length, mw = map[0].length;
        int r = (int) Math.ceil(l.radius) + 1;

        l.sx0 = (int) Math.floor((l.x - r) * RES);
        l.sy0 = (int) Math.floor((l.y - r) * RES);
        l.sw = r * 2 * RES + 2;
        l.sh = r * 2 * RES + 2;
        l.vis = new float[l.sw * l.sh];

        for (int sy = 0; sy < l.sh; sy++) {
            float wy = (l.sy0 + sy) / (float) RES;
            for (int sx = 0; sx < l.sw; sx++) {
                float wx = (l.sx0 + sx) / (float) RES;
                l.vis[sy * l.sw + sx] = clearLineOfSight(l.x, l.y, wx, wy, map, mw, mh) ? 1f : 0f;
            }
        }
    }

    /**
     * Walks the segment from the light to a sample point, looking for a solid cell in between.
     *
     * <p>The endpoints are excluded deliberately: the sample may sit inside a wall (that is how a
     * wall face gets lit at all) and the light sits beside one.
     */
    private static boolean clearLineOfSight(float x0, float y0, float x1, float y1,
                                            char[][] map, int mw, int mh) {
        float dx = x1 - x0, dy = y1 - y0;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        if (dist < 0.001f) return true;

        int steps = (int) (dist * RES) + 1;
        float stepX = dx / steps, stepY = dy / steps;
        int startCX = (int) x0, startCY = (int) y0;
        int endCX = (int) x1, endCY = (int) y1;

        float px = x0, py = y0;
        for (int i = 1; i < steps; i++) {
            px += stepX; py += stepY;
            int cx = (int) px, cy = (int) py;
            if (cx == startCX && cy == startCY) continue;
            if (cx == endCX && cy == endCY) continue;
            if (cx < 0 || cy < 0 || cx >= mw || cy >= mh) return false;
            if (!TileRegistry.getByIdSafe(map[cy][cx]).isWalkable()) return false;
        }
        return true;
    }

    /** Cheap identity for a tile grid, so any runtime change to it invalidates the lighting. */
    private static long fingerprint(char[][] map) {
        long h = 1125899906842597L;
        for (char[] row : map) {
            for (char c : row) h = h * 31 + c;
        }
        return h;
    }
}

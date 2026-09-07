package io.cannonforge.retroquest.core;

import io.cannonforge.retroquest.model.Dungeon;
import io.cannonforge.retroquest.model.TileDefinition;
import io.cannonforge.retroquest.registry.TileRegistry;

/**
 * Computes per-tile brightness for the visible viewport using recursive shadow casting.
 *
 * <p>Replaces the binary dungeon fog-of-war with gradient visibility that works in
 * all environments. Light sources (player + tiles with {@code lightRadius > 0}) cast
 * light through open areas; walls and vision-blocking tiles create shadows.
 *
 * <p>Brightness values: 0.0 = pitch black, 1.0 = full bright.
 */
public class VisionSystem {

    // ── Ambient light per environment ────────────────────────────────────────
    // Dungeons stay pitch black — a torch radius in the dark is the point there.
    // Outdoors is the opposite: the era’s overworld CRPGs showed the whole viewport by
    // daylight, so the overworld is lit well enough to read the terrain and see
    // distant NPCs, with the player's light layered on top as a brighter pool.
    // Towns sit between the two: dim enough that exploring still reveals the
    // layout, bright enough that you are never walking a black screen.
    public static final float DUNGEON_AMBIENT   = 0.0f;
    public static final float TOWN_AMBIENT      = 0.18f;
    public static final float OVERWORLD_AMBIENT  = 0.60f;

    // ── Remembered dungeon tiles (explored but not currently lit) ─────────────
    public static final float REMEMBERED_DIM         = 0.12f;
    public static final float REMEMBERED_DIM_TOWN    = 0.22f;

    // ── Visibility thresholds ────────────────────────────────────────────────
    public static final float VISIBILITY_THRESHOLD     = 0.02f;
    public static final float NPC_VISIBILITY_THRESHOLD = 0.08f;

    // ── Falloff tuning ───────────────────────────────────────────────────────
    private static final float FALLOFF_EXPONENT = 1.5f;

    /**
     * Scratch buffer the lighting solution is computed into. Never handed out — see
     * {@link #published}.
     */
    private static float[][] buffer = null;

    /**
     * Snapshot returned to callers. Refreshed from {@link #buffer} on every call, so a
     * caller that writes into the array it was given cannot corrupt the cached solution
     * (the old code handed out the live scratch buffer).
     */
    private static float[][] published = null;

    // ── Recompute cache ──────────────────────────────────────────────────────
    // The raycaster was re-running on every paint, including 60 fps overlay/combat
    // repaints over a completely static map. Everything that can change the result
    // is folded into the key below; when it is unchanged we hand back the snapshot.
    private static boolean  cacheValid = false;
    private static int      kViewW, kViewH, kCamX, kCamY, kPx, kPy, kDepth, kRadius;
    private static boolean  kInDungeon;
    private static char[][] kMap;
    private static String   kTown;
    private static Object   kRevealed;
    private static long     kRegionHash;

    /** Drops the cached lighting solution; the next call recomputes from scratch. */
    public static void invalidate() { cacheValid = false; }

    /**
     * Computes brightness for every tile in the current viewport.
     *
     * @return {@code float[viewH][viewW]} where 0.0 = black, 1.0 = full bright.
     *         The array is a snapshot owned by the caller for the duration of the call.
     */
    public static float[][] computeBrightness(Retroquest game) {
        int viewW = game.getViewWidth();
        int viewH = game.getViewHeight();
        int camX  = game.getCameraX();
        int camY  = game.getCameraY();

        // Reuse buffer if dimensions match
        if (buffer == null || buffer.length != viewH || buffer[0].length != viewW) {
            buffer = new float[viewH][viewW];
            published = new float[viewH][viewW];
            cacheValid = false;
        }

        boolean inDungeon = game.isInDungeon();
        int depth = game.getCurrentDepth();
        char[][] map = game.getCurrentMap();
        if (map == null) return publish(viewH, viewW);

        int mapH = map.length;
        int mapW = map[0].length;
        int px = game.getPlayer().getX();
        int py = game.getPlayer().getY();

        // ── Cache check ──
        // Light sources are read from a band around the viewport, so the key hashes
        // exactly that band: any tile swap in it (door unlocked, lamp lit) misses.
        int margin      = maxTileLightRadius();
        int playerRadius = game.getPlayer().getVisionRadius(inDungeon, game.getCurrentTown() != null);
        String townName = game.getCurrentTown() != null ? game.getCurrentTown().getName() : null;
        boolean[][] revealedArr = inDungeon
                ? game.getRevealedForLevel(depth)
                : (townName != null ? game.getTownRevealed().computeIfAbsent(townName,
                        k -> new boolean[mapH][mapW]) : null);
        Object revealedRef = revealedArr;
        long regionHash = hashLightRegion(map, mapW, mapH, camX, camY, viewW, viewH, margin);

        if (cacheValid
                && kViewW == viewW && kViewH == viewH
                && kCamX == camX && kCamY == camY
                && kPx == px && kPy == py
                && kInDungeon == inDungeon && kDepth == depth
                && kRadius == playerRadius
                && kMap == map && kRevealed == revealedRef
                && java.util.Objects.equals(kTown, townName)
                && kRegionHash == regionHash) {
            return publish(viewH, viewW);
        }

        kViewW = viewW; kViewH = viewH; kCamX = camX; kCamY = camY;
        kPx = px; kPy = py; kInDungeon = inDungeon; kDepth = depth;
        kRadius = playerRadius; kMap = map; kTown = townName;
        kRevealed = revealedRef; kRegionHash = regionHash;
        cacheValid = true;

        // 1. Initialize with ambient light
        float ambient = inDungeon ? DUNGEON_AMBIENT
                      : (game.getCurrentTown() != null ? TOWN_AMBIENT : OVERWORLD_AMBIENT);

        for (int sy = 0; sy < viewH; sy++) {
            for (int sx = 0; sx < viewW; sx++) {
                buffer[sy][sx] = ambient;
            }
        }

        // 2. Mark remembered tiles (dungeons and towns)
        if (inDungeon) {
            boolean[][] revealed = revealedArr;
            for (int sy = 0; sy < viewH; sy++) {
                for (int sx = 0; sx < viewW; sx++) {
                    int wx = camX + sx, wy = camY + sy;
                    if (wx >= 0 && wx < Dungeon.WIDTH && wy >= 0 && wy < Dungeon.HEIGHT
                            && revealed[wy][wx]) {
                        buffer[sy][sx] = REMEMBERED_DIM;
                    }
                }
            }
        } else if (townName != null) {
            boolean[][] townVis = revealedArr;
            for (int sy = 0; sy < viewH; sy++) {
                for (int sx = 0; sx < viewW; sx++) {
                    int wx = camX + sx, wy = camY + sy;
                    if (wx >= 0 && wx < mapW && wy >= 0 && wy < mapH && townVis[wy][wx]) {
                        // max(), so remembering a tile can only ever brighten it —
                        // it must never pull a tile below the town's ambient floor.
                        buffer[sy][sx] = Math.max(buffer[sy][sx], REMEMBERED_DIM_TOWN);
                    }
                }
            }
        }

        // 3. Wall checker for line-of-sight
        WallChecker wallCheck;
        if (inDungeon) {
            wallCheck = (worldX, worldY) -> false; // unused for dungeons — wall edges checked separately
        } else {
            wallCheck = (worldX, worldY) -> {
                if (worldX < 0 || worldX >= mapW || worldY < 0 || worldY >= mapH) return false;
                char tileId = map[worldY][worldX];
                TileDefinition def = TileRegistry.getByIdSafe(tileId);
                return def.blocksVision();
            };
        }

        // 4. Cast light from player
        castLight(buffer, px, py, playerRadius, 1.0f, camX, camY, viewW, viewH, mapW, mapH,
                  wallCheck, inDungeon, depth);

        // 5. Cast light from light-emitting tiles in (and just outside) the viewport.
        //    The scan is widened by the largest light radius in the registry so a lamp
        //    a step off-screen still spills into view — castLight() clips to the
        //    viewport, so only genuinely visible cells are written.
        int lx0 = Math.max(0,    camX - margin);
        int ly0 = Math.max(0,    camY - margin);
        int lx1 = Math.min(mapW, camX + viewW + margin);
        int ly1 = Math.min(mapH, camY + viewH + margin);
        for (int wy = ly0; wy < ly1; wy++) {
            char[] row = map[wy];
            for (int wx = lx0; wx < lx1; wx++) {
                TileDefinition def = TileRegistry.getByIdSafe(row[wx]);
                int lr = def.getLightRadius();
                if (lr > 0) {
                    castLight(buffer, wx, wy, lr, 1.0f, camX, camY, viewW, viewH, mapW, mapH,
                              wallCheck, inDungeon, depth);
                }
            }
        }

        // 6. Update revealed state (dungeons and towns)
        if (inDungeon) {
            boolean[][] revealed = revealedArr;
            for (int sy = 0; sy < viewH; sy++) {
                for (int sx = 0; sx < viewW; sx++) {
                    int wx = camX + sx, wy = camY + sy;
                    if (wx >= 0 && wx < Dungeon.WIDTH && wy >= 0 && wy < Dungeon.HEIGHT
                            && buffer[sy][sx] > REMEMBERED_DIM) {
                        revealed[wy][wx] = true;
                    }
                }
            }
        } else if (townName != null) {
            boolean[][] townVis = revealedArr;
            for (int sy = 0; sy < viewH; sy++) {
                for (int sx = 0; sx < viewW; sx++) {
                    int wx = camX + sx, wy = camY + sy;
                    // Towns remember at REMEMBERED_DIM_TOWN, so that — not the dungeon
                    // constant — is the threshold a tile has to beat to count as seen.
                    if (wx >= 0 && wx < mapW && wy >= 0 && wy < mapH
                            && buffer[sy][sx] > REMEMBERED_DIM_TOWN) {
                        townVis[wy][wx] = true;
                    }
                }
            }
        }

        return publish(viewH, viewW);
    }

    // ── Cache plumbing ───────────────────────────────────────────────────────

    /** Copies the scratch solution into the caller-facing snapshot and returns it. */
    private static float[][] publish(int viewH, int viewW) {
        if (published == null || published.length != viewH || published[0].length != viewW) {
            published = new float[viewH][viewW];
        }
        for (int y = 0; y < viewH; y++) {
            System.arraycopy(buffer[y], 0, published[y], 0, viewW);
        }
        return published;
    }

    /** Largest {@code lightRadius} any registered tile can emit (the editor can change these). */
    private static int maxTileLightRadius() {
        int max = 0;
        for (TileDefinition d : TileRegistry.getAllTiles()) {
            int lr = d.getLightRadius();
            if (lr > max) max = lr;
        }
        return max;
    }

    /**
     * FNV-1a over the tile ids in the band the light scan reads. Cheap relative to a
     * single raycast, and it catches every in-place map edit (doors unlocked, tiles
     * swapped by an event) that would change the lighting solution.
     */
    private static long hashLightRegion(char[][] map, int mapW, int mapH,
                                        int camX, int camY, int viewW, int viewH, int margin) {
        int x0 = Math.max(0,    camX - margin);
        int y0 = Math.max(0,    camY - margin);
        int x1 = Math.min(mapW, camX + viewW + margin);
        int y1 = Math.min(mapH, camY + viewH + margin);
        long h = 0xcbf29ce484222325L;
        for (int y = y0; y < y1; y++) {
            char[] row = map[y];
            for (int x = x0; x < x1; x++) {
                h = (h ^ row[x]) * 0x100000001b3L;
            }
        }
        return h;
    }

    // ── Light casting ───────────────────────────────────────────────────────

    /**
     * Casts light from (cx, cy) into the brightness buffer.
     * Uses simple distance-based falloff with line-of-sight ray checks.
     */
    private static void castLight(float[][] brightness,
                                   int cx, int cy, int radius, float centerBright,
                                   int camX, int camY, int viewW, int viewH,
                                   int mapW, int mapH,
                                   WallChecker wallCheck,
                                   boolean inDungeon, int depth) {
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                if (dist > radius) continue;

                int wx = cx + dx, wy = cy + dy;
                int vx = wx - camX, vy = wy - camY;
                if (vx < 0 || vx >= viewW || vy < 0 || vy >= viewH) continue;
                if (wx < 0 || wx >= mapW || wy < 0 || wy >= mapH) continue;

                // Line-of-sight check
                boolean canSee = inDungeon
                    ? hasDungeonLineOfSight(cx, cy, wx, wy, depth)
                    : hasLineOfSight(cx, cy, wx, wy, mapW, mapH, wallCheck);
                if (!canSee) continue;

                float intensity = centerBright * Math.max(0f, 1.0f - (float) Math.pow(dist / radius, FALLOFF_EXPONENT));
                brightness[vy][vx] = Math.max(brightness[vy][vx], intensity);
            }
        }
    }

    /**
     * Bresenham-style line-of-sight for overworld/towns.
     * Returns false if any cell along the line has blocksVision = true.
     */
    private static boolean hasLineOfSight(int x0, int y0, int x1, int y1,
                                           int mapW, int mapH, WallChecker wallCheck) {
        int dx = Math.abs(x1 - x0), dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;

        int cx = x0, cy = y0;
        while (cx != x1 || cy != y1) {
            int e2 = 2 * err;
            if (e2 > -dy) { err -= dy; cx += sx; }
            if (e2 <  dx) { err += dx; cy += sy; }
            if (cx == x1 && cy == y1) break;
            if (wallCheck.blocksVision(cx, cy)) return false;
        }
        return true;
    }

    /**
     * Bresenham-style line-of-sight for dungeons.
     * Checks wall segments between adjacent cells as the ray steps through them.
     * Uses 0-based world coordinates; converts to 1-based for Dungeon API.
     */
    private static boolean hasDungeonLineOfSight(int x0, int y0, int x1, int y1, int depth) {
        int dx = Math.abs(x1 - x0), dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;

        int cx = x0, cy = y0;
        while (cx != x1 || cy != y1) {
            int prevX = cx, prevY = cy;
            int e2 = 2 * err;
            if (e2 > -dy) { err -= dy; cx += sx; }
            if (e2 <  dx) { err += dx; cy += sy; }

            // Check wall between prevCell and current cell
            if (cx != prevX && cy != prevY) {
                // Diagonal step — check both walls (horizontal and vertical)
                // Block if BOTH paths are walled
                boolean hBlocked = hasWallBetween(prevX, prevY, cx, prevY, depth)
                                || hasWallBetween(cx, prevY, cx, cy, depth);
                boolean vBlocked = hasWallBetween(prevX, prevY, prevX, cy, depth)
                                || hasWallBetween(prevX, cy, cx, cy, depth);
                if (hBlocked && vBlocked) return false;
            } else {
                // Cardinal step — check the single wall
                if (hasWallBetween(prevX, prevY, cx, cy, depth)) return false;
            }
        }
        return true;
    }

    /**
     * Checks if there is a WALL between two adjacent cells (0-based coords).
     * Returns true if movement from (ax,ay) to (bx,by) crosses a wall.
     */
    private static boolean hasWallBetween(int ax, int ay, int bx, int by, int depth) {
        int ddx = bx - ax, ddy = by - ay;
        // Convert to 1-based for Dungeon API. Both WALL and DOOR block vision.
        Dungeon.WallType wt;
        if (ddx == 1)       wt = Dungeon.getEastWall(ax + 1, ay + 1, depth);
        else if (ddx == -1) wt = Dungeon.getWestWall(ax + 1, ay + 1, depth);
        else if (ddy == 1)  wt = Dungeon.getSouthWall(ax + 1, ay + 1, depth);
        else if (ddy == -1) wt = Dungeon.getNorthWall(ax + 1, ay + 1, depth);
        else return false;
        return wt != Dungeon.WallType.OPEN;
    }

    // ── Wall checker interface ───────────────────────────────────────────────

    @FunctionalInterface
    interface WallChecker {
        /** Returns true if (worldX, worldY) blocks vision. */
        boolean blocksVision(int worldX, int worldY);
    }
}

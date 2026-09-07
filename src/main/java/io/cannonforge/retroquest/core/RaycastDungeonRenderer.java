package io.cannonforge.retroquest.core;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;

import io.cannonforge.retroquest.model.DungeonCamera;
import io.cannonforge.retroquest.model.Monster;
import io.cannonforge.retroquest.registry.TileRegistry;

/**
 * Island 6's first-person renderer: a texture-mapped, per-pixel-lit raycaster.
 *
 * <h2>How this differs from Islands 4 and 5</h2>
 * The band renderers draw four fixed depth slices as vector polygons, always square-on, always
 * from one of four headings, and shade a whole slice at a time. This one casts a ray per screen
 * column across the tile grid, so:
 *
 * <ul>
 *   <li>the view can face <em>any</em> angle, which is what lets the camera ease through turns
 *       instead of snapping ({@link DungeonCamera});</li>
 *   <li>walls are sampled from {@link DungeonTextures} pages rather than drawn shape by shape, so
 *       surface detail costs nothing extra at distance;</li>
 *   <li>light is evaluated per pixel from a carried lantern and coloured sconce point lights, with
 *       a real surface-normal term — so a wall angled away from a flame genuinely falls off, and
 *       light pools on the floor instead of being painted on.</li>
 * </ul>
 *
 * <h2>Why authored dungeons only</h2>
 * Procedural dungeons model walls as <em>edges between cells</em>: two open cells
 * can have a wall between them. A grid raycaster needs walls to be <em>cells</em>. Authored
 * {@code .rfmap} dungeons are exactly that — a non-walkable tile is a solid block — so the caller
 * falls back to {@link TexturedDungeonRenderer} for procedural maps rather than have this quietly
 * render the wrong maze.
 *
 * <p>Frame buffers and the depth buffer are static and reused; painting only ever happens on the
 * EDT, the same assumption the other renderers make.
 */
public final class RaycastDungeonRenderer {

    /** Horizontal pixels between lighting samples on floor and ceiling; between them it lerps. */
    private static final int FLOOR_LIGHT_STEP = 8;

    /** Point lights considered per frame, nearest first. Beyond a handful nothing is visible. */
    private static final int MAX_LIGHTS = 4;

    /** How far out sconces are gathered, in tiles. */
    private static final int LIGHT_RADIUS = 7;

    /** Wall height on screen at one tile's distance, as a multiple of the viewport height. */
    private static final float PLANE_SCALE = 1.20f;

    /** Never fully black — a floor of ambient keeps the far dark from flattening into a void. */
    private static final float AMBIENT = 0.030f;

    private static final int TEX = DungeonTextures.SIZE;
    private static final int TEXMASK = DungeonTextures.MASK;

    private static BufferedImage frame;
    private static int[] fb;
    private static int fbW, fbH;

    /** Per-column wall distance, kept so billboards can be depth-tested against the walls. */
    private static float[] depth = new float[0];

    private static Monster previewMonster;
    private static long previewStartMs;
    private static final long PREVIEW_DURATION_MS = 400;

    private RaycastDungeonRenderer() {}

    // ── Public API ───────────────────────────────────────────────────────────

    public static void showMonsterPreview(Monster m) {
        previewMonster = m;
        previewStartMs = System.currentTimeMillis();
    }

    public static boolean isPreviewActive() {
        return previewMonster != null
                && (System.currentTimeMillis() - previewStartMs) < PREVIEW_DURATION_MS;
    }

    /** True when this renderer can handle the current dungeon. See the class note on wall models. */
    public static boolean canRender(Retroquest game) {
        return game.getDungeonViewState().isAuthored() && game.getCurrentMap() != null;
    }

    // ── Frame ────────────────────────────────────────────────────────────────

    public static void paint(Graphics2D g2, int W, int H, Retroquest game) {
        DungeonTheme t = DungeonTheme.resolve(game.getDungeonViewState().getAuthoredDungeonName(),
                                              game.getCurrentOverworldName());
        char[][] map = game.getCurrentMap();
        int pz = game.getCurrentDepth();
        var player = game.getPlayer();
        DungeonCamera cam = game.getDungeonViewState().getCamera();

        char playerTile = DungeonWallQuery.getSpecial(player.getX(), player.getY(), pz, map, true);
        boolean inDarkZone = (playerTile == 'k');

        int hudH = DisplayScale.scaled(32);
        boolean showMinimap = game.getDungeonViewState().isMinimapVisible() && !inDarkZone;
        int minimapH = showMinimap ? Math.max(H * 2 / 5, 180) : 0;
        int corridorH = Math.max(16, H - hudH - minimapH);

        ensureFrame(W, corridorH);

        long now = System.currentTimeMillis();
        String levelKey = game.getDungeonViewState().getDungeonId() + ":" + pz;
        DungeonLighting.Light[] lights = inDarkZone
                ? new DungeonLighting.Light[0]
                : gatherLights(map, t, cam, levelKey, now);
        View v = new View(W, corridorH, cam);

        // The Cradle of Shards is the endgame, and it is eight levels deep. Its floor grows more
        // mirror-like and its light splits further into colour the further down you go, so the
        // rendering itself counts down to the end.
        float reflect = t.reflectivity;
        float chromaSplit = 0f;
        if (reflect > 0f) {
            float descent = t.escalateWithDepth ? Math.min(1f, (pz - 1) / 7f) : 1f;
            reflect *= 0.34f + 0.66f * descent;
            chromaSplit = t.prismatic ? (1f + 5f * descent) : 0f;
        }

        renderCorridor(map, t, v, lights, inDarkZone, reflect, chromaSplit);
        g2.drawImage(frame, 0, 0, null);

        // Billboards ride on top of the raster, depth-tested per column against the wall buffer.
        if (!inDarkZone) {
            paintSconceFlames(g2, t, v, lights, now);
            paintSpecials(g2, map, t, v, pz);
        }
        paintDust(g2, W, corridorH, t, now, inDarkZone);

        if (previewMonster != null) {
            long elapsed = now - previewStartMs;
            if (elapsed < PREVIEW_DURATION_MS) paintMonsterPreview(g2, W, corridorH, elapsed);
            else previewMonster = null;
        }

        TexturedDungeonRenderer.paintHudFor(g2, 0, corridorH, W, hudH, t,
                player.getX(), player.getY(), pz, facingOf(cam), map, true, inDarkZone);
        if (showMinimap) {
            TexturedDungeonRenderer.paintMinimapFor(g2, 0, corridorH + hudH, W, minimapH, t,
                    player.getX(), player.getY(), pz, facingOf(cam), game, map, true);
        }
    }

    private static void ensureFrame(int w, int h) {
        if (frame == null || fbW != w || fbH != h) {
            frame = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            fb = ((DataBufferInt) frame.getRaster().getDataBuffer()).getData();
            fbW = w;
            fbH = h;
            depth = new float[w];
        }
    }

    /** The camera basis for one frame: heading, projection plane, and derived screen constants. */
    private static final class View {
        final int w, h, horizon;
        final float camX, camY;
        final float dirX, dirY, planeX, planeY;
        final float planeDist;

        View(int w, int h, DungeonCamera cam) {
            this.w = w;
            this.h = h;
            this.horizon = h / 2;
            this.camX = cam.getX();
            this.camY = cam.getY();

            // Facing 0 is north (-Y), and angle grows clockwise through east.
            float a = cam.getAngle();
            this.dirX = (float) Math.sin(a);
            this.dirY = (float) -Math.cos(a);

            // Vertical field of view is fixed by PLANE_SCALE; the horizontal one falls out of the
            // viewport's aspect. The dungeon panel is a wide letterbox, so this is what keeps a
            // corridor from looking like a keyhole.
            this.planeDist = h * PLANE_SCALE;
            float halfPlane = (w * 0.5f) / planeDist;
            this.planeX = -dirY * halfPlane;
            this.planeY = dirX * halfPlane;
        }
    }

    // ── Corridor ─────────────────────────────────────────────────────────────

    private static void renderCorridor(char[][] map, DungeonTheme t, View v,
                                       DungeonLighting.Light[] lights, boolean dark,
                                       float reflect, float chromaSplit) {
        DungeonTextures.Set tex = DungeonTextures.forTheme(t);
        float lantern = dark ? 0f : 1f;

        castFloorAndCeiling(tex, t, v, lights, lantern);
        castWalls(map, tex, t, v, lights, lantern, reflect, chromaSplit);
    }

    /**
     * Floor and ceiling, one screen row at a time.
     *
     * <p>Every pixel on a given row is the same distance away, which is the whole reason floor
     * casting is done by row rather than by column: the world position steps linearly across the
     * row, so the inner loop is two adds and a texture fetch.
     */
    private static void castFloorAndCeiling(DungeonTextures.Set tex, DungeonTheme t, View v,
                                            DungeonLighting.Light[] lights, float lantern) {
        int[] floorTex = tex.floor;
        int[] ceilTex = tex.ceiling;

        float rayX0 = v.dirX - v.planeX, rayY0 = v.dirY - v.planeY;
        float rayX1 = v.dirX + v.planeX, rayY1 = v.dirY + v.planeY;

        for (int p = 1; p <= v.h - v.horizon; p++) {
            // Eye sits half a tile above the floor and half below the ceiling, so one distance
            // serves the row below the horizon and its mirror above it.
            float rowDist = (0.5f * v.planeDist) / p;
            if (rowDist > 64f) continue;

            float stepX = rowDist * (rayX1 - rayX0) / v.w;
            float stepY = rowDist * (rayY1 - rayY0) / v.w;
            float wx = v.camX + rowDist * rayX0;
            float wy = v.camY + rowDist * rayY0;

            int yFloor = v.horizon + p;
            int yCeil = v.horizon - p;
            boolean hasFloor = yFloor < v.h;
            boolean hasCeil = yCeil >= 0;
            if (!hasFloor && !hasCeil) continue;

            int rowF = yFloor * v.w;
            int rowC = yCeil * v.w;

            // Lighting varies smoothly along a row, so sample it every few pixels and interpolate.
            float fr0 = 0, fg0 = 0, fb0 = 0, cr0 = 0, cg0 = 0, cb0 = 0;
            float frS = 0, fgS = 0, fbS = 0, crS = 0, cgS = 0, cbS = 0;
            int sampleLeft = 0;

            for (int x = 0; x < v.w; x++) {
                if (sampleLeft == 0) {
                    int span = Math.min(FLOOR_LIGHT_STEP, v.w - x);
                    float ahead = span;
                    float ax = wx + stepX * ahead, ay = wy + stepY * ahead;

                    shadeSurface(shA, wx, wy, 0f, 0f, 1f, lights, lantern, v, t);
                    shadeSurface(shB, ax, ay, 0f, 0f, 1f, lights, lantern, v, t);
                    fr0 = shA[0]; fg0 = shA[1]; fb0 = shA[2];
                    frS = (shB[0] - shA[0]) / ahead;
                    fgS = (shB[1] - shA[1]) / ahead;
                    fbS = (shB[2] - shA[2]) / ahead;

                    shadeSurface(shA, wx, wy, 1f, 0f, -1f, lights, lantern, v, t);
                    shadeSurface(shB, ax, ay, 1f, 0f, -1f, lights, lantern, v, t);
                    cr0 = shA[0]; cg0 = shA[1]; cb0 = shA[2];
                    crS = (shB[0] - shA[0]) / ahead;
                    cgS = (shB[1] - shA[1]) / ahead;
                    cbS = (shB[2] - shA[2]) / ahead;
                    sampleLeft = span;
                }

                int tx = (int) (wx * TEX) & TEXMASK;
                int ty = (int) (wy * TEX) & TEXMASK;
                int idx = ty * TEX + tx;

                if (hasFloor) fb[rowF + x] = modulate(floorTex[idx], fr0, fg0, fb0);
                if (hasCeil)  fb[rowC + x] = modulate(ceilTex[idx], cr0, cg0, cb0);

                wx += stepX;
                wy += stepY;
                fr0 += frS; fg0 += fgS; fb0 += fbS;
                cr0 += crS; cg0 += cgS; cb0 += cbS;
                sampleLeft--;
            }
        }

        // Rows the loop could not reach (the horizon itself) stay at the theme's fog colour.
        int fog = t.fog.getRGB();
        int hy = v.horizon * v.w;
        for (int x = 0; x < v.w && v.horizon < v.h; x++) fb[hy + x] = fog;
    }

    /** Walls, one screen column at a time, by DDA across the tile grid. */
    private static void castWalls(char[][] map, DungeonTextures.Set tex, DungeonTheme t, View v,
                                  DungeonLighting.Light[] lights, float lantern,
                                  float reflect, float chromaSplit) {
        int mapH = map.length, mapW = map[0].length;

        for (int x = 0; x < v.w; x++) {
            float camScreen = 2f * x / v.w - 1f;
            float rayX = v.dirX + v.planeX * camScreen;
            float rayY = v.dirY + v.planeY * camScreen;

            int mapX = (int) v.camX, mapY = (int) v.camY;
            float deltaX = (rayX == 0f) ? Float.MAX_VALUE : Math.abs(1f / rayX);
            float deltaY = (rayY == 0f) ? Float.MAX_VALUE : Math.abs(1f / rayY);

            int stepX, stepY;
            float sideX, sideY;
            if (rayX < 0) { stepX = -1; sideX = (v.camX - mapX) * deltaX; }
            else          { stepX = 1;  sideX = (mapX + 1f - v.camX) * deltaX; }
            if (rayY < 0) { stepY = -1; sideY = (v.camY - mapY) * deltaY; }
            else          { stepY = 1;  sideY = (mapY + 1f - v.camY) * deltaY; }

            int side = 0;
            boolean hit = false, isDoor = false;
            char hitTile = 'W';
            int guard = 0;
            while (!hit && guard++ < 128) {
                if (sideX < sideY) { sideX += deltaX; mapX += stepX; side = 0; }
                else               { sideY += deltaY; mapY += stepY; side = 1; }

                if (mapX < 0 || mapY < 0 || mapX >= mapW || mapY >= mapH) { hit = true; break; }
                char tile = map[mapY][mapX];
                if (isDoorTile(tile)) { hit = true; isDoor = true; hitTile = tile; }
                else if (!TileRegistry.getByIdSafe(tile).isWalkable()) { hit = true; hitTile = tile; }
            }

            // Perpendicular distance, not euclidean — using the true ray length would bow the
            // walls outward at the edges of the screen (the classic fisheye).
            float perp = (side == 0) ? (sideX - deltaX) : (sideY - deltaY);
            if (perp < 0.0001f) perp = 0.0001f;
            depth[x] = perp;

            int lineHeight = (int) (v.planeDist / perp);
            int drawStart = -lineHeight / 2 + v.horizon;
            int drawEnd = lineHeight / 2 + v.horizon;

            float hitX = v.camX + perp * rayX;
            float hitY = v.camY + perp * rayY;

            float wallU = (side == 0) ? (v.camY + perp * rayY) : (v.camX + perp * rayX);
            wallU -= (float) Math.floor(wallU);
            int texX = (int) (wallU * TEX);
            if (side == 0 && rayX > 0) texX = TEX - texX - 1;
            if (side == 1 && rayY < 0) texX = TEX - texX - 1;
            texX &= TEXMASK;

            // The map has always known this block is a bookshelf, a pillar, a gate or a lava
            // seam; until Island 7 the renderer threw that away and drew anonymous stone.
            int face = (side == 0) ? (rayX > 0 ? 0 : 1) : (rayY > 0 ? 2 : 3);
            // Only a plain door gets the door page. A locked iron gate and a magic barrier are
            // doors to DungeonWallQuery but they do not look like one, and they now have
            // surfaces of their own.
            boolean plainDoor = (hitTile == 'd' || hitTile == '|');
            int[] page = plainDoor ? tex.door
                                   : tex.wall[DungeonTextures.pageForTile(hitTile, mapX, mapY, face)];

            // A block that emits light is drawn lit by itself, not only by what falls on it.
            float emR = 0f, emG = 0f, emB = 0f;
            if (t.selfGlow > 0f) {
                java.awt.Color sg = t.specialNear;
                float peak = Math.max(1f, Math.max(sg.getRed(), Math.max(sg.getGreen(), sg.getBlue())));
                emR = sg.getRed()   / peak * t.selfGlow;
                emG = sg.getGreen() / peak * t.selfGlow;
                emB = sg.getBlue()  / peak * t.selfGlow;
            }
            if (!plainDoor) {
                var def = TileRegistry.getByIdSafe(hitTile);
                if (def.getLightRadius() > 0) {
                    java.awt.Color ec = def.getEditorColor();
                    float peak = Math.max(1f, Math.max(ec.getRed(),
                                 Math.max(ec.getGreen(), ec.getBlue())));
                    float k = 0.72f;
                    emR = ec.getRed()   / peak * k;
                    emG = ec.getGreen() / peak * k;
                    emB = ec.getBlue()  / peak * k;
                }
            }

            // Surface normal — the face we actually hit. This is what makes a wall darken as it
            // turns away from a flame instead of every surface being lit by raw distance.
            float nx = 0f, ny = 0f;
            if (side == 0) nx = (rayX > 0) ? -1f : 1f;
            else           ny = (rayY > 0) ? -1f : 1f;

            float texStep = (float) TEX / lineHeight;
            float texPos = 0f;
            int y0 = drawStart, y1 = drawEnd;
            if (y0 < 0) { texPos = -y0 * texStep; y0 = 0; }
            if (y1 > v.h) y1 = v.h;

            // Lighting is sampled at a few heights up the column and interpolated between; the
            // normal and the horizontal distance are constant here, so only the vertical term moves.
            shadeSurface(shTop, hitX, hitY, 1f, nx, ny, lights, lantern, v, t);
            shadeSurface(shMid, hitX, hitY, 0.5f, nx, ny, lights, lantern, v, t);
            shadeSurface(shBot, hitX, hitY, 0f, nx, ny, lights, lantern, v, t);
            float[] top = shTop, mid = shMid, bot = shBot;

            for (int y = y0; y < y1; y++) {
                int ty = ((int) texPos) & TEXMASK;
                texPos += texStep;

                // Height up the wall: texture row 0 is the ceiling end.
                float wz = 1f - ((y - drawStart) / (float) lineHeight);
                lerpLight(wz, top, mid, bot, shPix);
                fb[y * v.w + x] = modulate(page[ty * TEX + texX],
                                           shPix[0] + emR, shPix[1] + emG, shPix[2] + emB);
            }

            // ── Reflection ──
            // A flat mirror floor reflects a wall as that same wall flipped about the line where
            // it meets the floor — which the column has already computed. So the whole effect is
            // one extra pass over rows below drawEnd, reading the column back upwards.
            if (reflect > 0.01f && drawEnd < v.h) {
                int rEnd = Math.min(v.h, drawEnd + lineHeight);
                int chroma = (int) chromaSplit;
                for (int y = Math.max(drawEnd, 0); y < rEnd; y++) {
                    int src = 2 * drawEnd - y;
                    if (src < drawStart) break;
                    float srcZ = 1f - ((src - drawStart) / (float) lineHeight);
                    int ty = ((int) ((src - drawStart) * texStep)) & TEXMASK;

                    lerpLight(srcZ, top, mid, bot, shPix);

                    // Prismatic split: red and blue are sampled a texel to either side, so the
                    // reflection fringes into colour the way light does through cut crystal.
                    int cR = page[ty * TEX + ((texX + chroma) & TEXMASK)];
                    int cG = page[ty * TEX + texX];
                    int cB = page[ty * TEX + ((texX - chroma) & TEXMASK)];
                    int mixed = (cR & 0xFF0000) | (cG & 0x00FF00) | (cB & 0x0000FF);
                    int refl = modulate(mixed, shPix[0] + emR, shPix[1] + emG, shPix[2] + emB);

                    float fade = 1f - (y - drawEnd) / (float) lineHeight;
                    float k = reflect * fade * fade;
                    fb[y * v.w + x] = blend(fb[y * v.w + x], refl, k);
                }
            }
        }
    }

    /** Interpolates the three sampled column heights down to one pixel's light. */
    private static void lerpLight(float wz, float[] top, float[] mid, float[] bot, float[] out) {
        if (wz >= 0.5f) {
            float f = (wz - 0.5f) * 2f;
            out[0] = mid[0] + (top[0] - mid[0]) * f;
            out[1] = mid[1] + (top[1] - mid[1]) * f;
            out[2] = mid[2] + (top[2] - mid[2]) * f;
        } else {
            float f = Math.max(0f, wz) * 2f;
            out[0] = bot[0] + (mid[0] - bot[0]) * f;
            out[1] = bot[1] + (mid[1] - bot[1]) * f;
            out[2] = bot[2] + (mid[2] - bot[2]) * f;
        }
    }

    /** Alpha-blends {@code src} over {@code dst} by {@code k}, per channel. */
    private static int blend(int dst, int src, float k) {
        if (k <= 0f) return dst;
        if (k >= 1f) return src;
        float inv = 1f - k;
        int r = (int) (((dst >> 16) & 255) * inv + ((src >> 16) & 255) * k);
        int g = (int) (((dst >> 8) & 255) * inv + ((src >> 8) & 255) * k);
        int b = (int) ((dst & 255) * inv + (src & 255) * k);
        return (r << 16) | (g << 8) | b;
    }

    // ── Lighting ─────────────────────────────────────────────────────────────

    // Shading scratch. One buffer per call site, not one shared buffer: shadeSurface used to
    // return a single ThreadLocal array, so "sample here, sample there, interpolate between them"
    // silently compared an array with itself and every gradient came out flat. Painting is
    // EDT-only, so plain statics are enough.
    private static final float[] shA = new float[3], shB = new float[3];
    private static final float[] shTop = new float[3], shMid = new float[3], shBot = new float[3];
    private static final float[] shPix = new float[3];

    /**
     * Light reaching one surface point, as an RGB multiplier.
     *
     * <p>Attenuation is inverse-square-ish rather than linear, and every source is multiplied by
     * the surface normal facing it. The lantern rides on the camera, which is what gives the
     * corridor its moving pool of light as the player walks.
     */
    private static void shadeSurface(float[] out, float wx, float wy, float wz, float nx, float ny,
                                     DungeonLighting.Light[] lights, float lantern, View v,
                                     DungeonTheme t) {
        // Ambient is deliberately cold. The lantern is warm, so tinting the light that reaches
        // nothing towards blue splits the frame into a warm pool and a cold dark instead of
        // leaving everything one sepia tone.
        float ar = AMBIENT * 0.62f, ag = AMBIENT * 0.86f, ab = AMBIENT * 1.75f;

        if (lantern > 0f) {
            float dx = v.camX - wx, dy = v.camY - wy, dz = 0.5f - wz;
            float d2 = dx * dx + dy * dy + dz * dz;
            float inv = 1f / (float) Math.sqrt(Math.max(d2, 1e-4f));
            float ndotl = nx * dx * inv + ny * dy * inv;
            if (nx == 0f && ny == 0f) ndotl = Math.abs(dz) * inv;   // floor / ceiling
            if (ndotl < 0f) ndotl = 0f;
            // A little wrap-around so surfaces edge-on to the lantern are not pitch black.
            ndotl = 0.20f + 0.80f * ndotl;
            float att = 0.88f * lantern * ndotl / (1f + 0.72f * d2);
            // A carried flame, not a torchlight app: strongly warm, so the near field reads amber
            // against stone that goes cold and blue as it falls out of the lantern's reach.
            ar += att; ag += att * 0.84f; ab += att * 0.58f;
        }

        // Fixtures. A wall point sits exactly on the boundary of its own solid cell, so the
        // shadow field is sampled a little way along the surface normal — otherwise every wall
        // face would read as standing in its own shadow.
        float visX = wx + nx * 0.3f, visY = wy + ny * 0.3f;

        for (DungeonLighting.Light l : lights) {
            float dx = l.x - wx, dy = l.y - wy, dz = l.z - wz;
            float d2 = dx * dx + dy * dy + dz * dz;
            float rad2 = l.radius * l.radius;
            if (d2 >= rad2) continue;

            float inv = 1f / (float) Math.sqrt(Math.max(d2, 1e-4f));
            float ndotl = nx * dx * inv + ny * dy * inv;
            if (nx == 0f && ny == 0f) ndotl = Math.abs(dz) * inv;
            if (ndotl < 0f) continue;
            ndotl = 0.14f + 0.86f * ndotl;

            float shadow = l.visibilityAt(visX, visY);
            if (shadow <= 0.001f) continue;

            // Windowed falloff, so a light genuinely stops at its authored radius instead of
            // trailing off forever and quietly lighting the next room.
            float window = 1f - d2 / rad2;
            float att = l.strengthNow * ndotl * window * window * shadow / (1f + 1.6f * d2);
            ar += l.r * att; ag += l.g * att; ab += l.b * att;
        }

        out[0] = tone(ar); out[1] = tone(ag); out[2] = tone(ab);
    }

    /**
     * Soft-knee highlight rolloff.
     *
     * <p>Two lights overlapping — the lantern plus a sconce you are standing next to — used to sum
     * past 1.0 and clip every channel straight to white, erasing the stone texture exactly where
     * the player was looking. This compresses the top end instead, so a hotspot stays hot and
     * coloured rather than turning into a flat white patch.
     */
    private static float tone(float x) {
        return (x / (1f + 0.55f * x)) * 1.45f;
    }

    /** Multiplies a texel by a light colour, clamping each channel. */
    private static int modulate(int rgb, float lr, float lg, float lb) {
        int r = (int) (((rgb >> 16) & 255) * lr);
        int g = (int) (((rgb >> 8) & 255) * lg);
        int b = (int) ((rgb & 255) * lb);
        if (r > 255) r = 255;
        if (g > 255) g = 255;
        if (b > 255) b = 255;
        return (r << 16) | (g << 8) | b;
    }

    /**
     * The fixtures lighting this frame.
     *
     * <p>Prefers what the map authors: any tile carrying a {@code lightRadius} is a real light,
     * with real shadows, placed by whoever built the level. Only when a dungeon authors none does
     * this fall back to the hashed sconces Island 6 invented — which is why Islands 4 to 6 are
     * unchanged by any of this.
     */
    private static DungeonLighting.Light[] gatherLights(char[][] map, DungeonTheme t,
                                                       DungeonCamera cam, String levelKey, long now) {
        DungeonLighting.Level authored = DungeonLighting.forMap(map, levelKey);
        if (authored.lights.length > 0) {
            DungeonLighting.Light[] near = authored.nearest(cam.getX(), cam.getY(), MAX_LIGHTS);
            for (DungeonLighting.Light l : near) {
                if (!l.flicker) continue;
                double phase = (now % 100000) / 95.0 + (l.seed & 15);
                l.strengthNow = l.strength * (0.86f + 0.14f * (float) Math.sin(phase)
                                            + 0.06f * (float) Math.sin(phase * 2.7));
            }
            return near;
        }
        if (!t.sconces) return new DungeonLighting.Light[0];
        int mapH = map.length, mapW = map[0].length;
        int cx = (int) cam.getX(), cy = (int) cam.getY();

        DungeonLighting.Light[] found = new DungeonLighting.Light[MAX_LIGHTS];
        float[] foundD2 = new float[MAX_LIGHTS];
        int n = 0;

        final int[] dx = {1, -1, 0, 0};
        final int[] dy = {0, 0, 1, -1};

        for (int y = cy - LIGHT_RADIUS; y <= cy + LIGHT_RADIUS; y++) {
            if (y < 0 || y >= mapH) continue;
            for (int x = cx - LIGHT_RADIUS; x <= cx + LIGHT_RADIUS; x++) {
                if (x < 0 || x >= mapW) continue;
                if (TileRegistry.getByIdSafe(map[y][x]).isWalkable()) continue;

                for (int f = 0; f < 4; f++) {
                    int ox = x + dx[f], oy = y + dy[f];
                    if (ox < 0 || oy < 0 || ox >= mapW || oy >= mapH) continue;
                    if (!TileRegistry.getByIdSafe(map[oy][ox]).isWalkable()) continue;
                    // Every wall cell has up to four faces, so the band renderer's one-in-four rate
                    // would light a corridor like a stadium. A quarter of that reads as sparse
                    // torches on a long wall, which is the point.
                    if (Math.floorMod(DungeonTextures.hash(x, y, f, 23), t.sconceEvery * 3) != 0) continue;

                    DungeonLighting.Light l = new DungeonLighting.Light();
                    // Sit just off the face, into the open cell.
                    l.x = x + 0.5f + dx[f] * 0.56f;
                    l.y = y + 0.5f + dy[f] * 0.56f;
                    l.z = 0.62f;
                    float ddx = l.x - cam.getX(), ddy = l.y - cam.getY();
                    float d2 = ddx * ddx + ddy * ddy;
                    if (d2 > (LIGHT_RADIUS + 1) * (LIGHT_RADIUS + 1)) continue;

                    int seed = DungeonTextures.hash(x, y, f, 23);
                    double phase = (now % 100000) / 95.0 + (seed & 15);
                    float flick = 0.84f + 0.16f * (float) Math.sin(phase)
                                + 0.07f * (float) Math.sin(phase * 2.7);
                    l.strength = 1.5f;
                    l.strengthNow = 1.5f * flick;
                    l.radius = LIGHT_RADIUS;
                    l.r = t.sconceGlow.getRed() / 255f;
                    l.g = t.sconceGlow.getGreen() / 255f;
                    l.b = t.sconceGlow.getBlue() / 255f;

                    // Keep only the nearest MAX_LIGHTS; anything further contributes nothing
                    // a player could see, and the per-pixel loop is the hot path.
                    if (n < MAX_LIGHTS) {
                        found[n] = l; foundD2[n] = d2; n++;
                    } else {
                        int worst = 0;
                        for (int i = 1; i < n; i++) if (foundD2[i] > foundD2[worst]) worst = i;
                        if (d2 < foundD2[worst]) { found[worst] = l; foundD2[worst] = d2; }
                    }
                }
            }
        }
        DungeonLighting.Light[] out = new DungeonLighting.Light[n];
        System.arraycopy(found, 0, out, 0, n);
        return out;
    }

    // ── Billboards ───────────────────────────────────────────────────────────

    /** Projects a world point. Returns null when it is behind the camera. */
    private static float[] project(View v, float wx, float wy) {
        float relX = wx - v.camX, relY = wy - v.camY;
        float det = v.planeX * v.dirY - v.dirX * v.planeY;
        if (Math.abs(det) < 1e-6f) return null;
        float inv = 1f / det;
        float tx = inv * (v.dirY * relX - v.dirX * relY);
        float ty = inv * (-v.planeY * relX + v.planeX * relY);
        if (ty <= 0.05f) return null;
        float sx = (v.w * 0.5f) * (1f + tx / ty);
        float size = v.planeDist / ty;
        return new float[]{sx, ty, size};
    }

    /** True when a billboard column is in front of the wall behind it. */
    private static boolean visible(View v, float screenX, float dist) {
        int col = (int) screenX;
        if (col < 0 || col >= v.w) return false;
        return dist < depth[col];
    }

    private static void paintSconceFlames(Graphics2D g, DungeonTheme t, View v,
                                          DungeonLighting.Light[] lights, long now) {
        for (DungeonLighting.Light l : lights) {
            float[] p = project(v, l.x, l.y);
            if (p == null || !visible(v, p[0], p[1])) continue;
            float size = p[2];
            float sx = p[0];
            float sy = v.horizon + (0.5f - l.z) * size;
            float s = size * 0.050f;
            if (s < 1.2f) continue;

            double phase = (now % 100000) / 90.0 + (int) (l.x * 7 + l.y * 13);
            float flick = 0.85f + 0.15f * (float) Math.sin(phase);
            float fh = s * (2.1f + 0.5f * flick);
            float sway = s * 0.2f * (float) Math.sin(phase * 1.7);

            // Bracket
            g.setColor(new Color(18, 16, 14, 235));
            g.fillRect((int) (sx - s * 0.5f), (int) (sy + s * 0.25f),
                       (int) Math.max(2f, s), (int) Math.max(2f, s * 0.8f));

            // Halo first, so the flame body sits inside a glow rather than being a hard cut-out
            // pasted on the wall. Without it the polygon layers read as coloured paper.
            float hr = fh * 1.9f;
            try {
                g.setPaint(new java.awt.RadialGradientPaint(
                        new java.awt.geom.Point2D.Float(sx, sy - fh * 0.35f), hr,
                        new float[]{0f, 0.45f, 1f},
                        new Color[]{alpha(t.flameOuter, 120), alpha(t.flameOuter, 42),
                                    alpha(t.flameOuter, 0)},
                        java.awt.MultipleGradientPaint.CycleMethod.NO_CYCLE));
                g.fillOval((int) (sx - hr), (int) (sy - fh * 0.35f - hr),
                           (int) (hr * 2), (int) (hr * 2));
            } finally {
                g.setPaint(null);
            }

            flame(g, sx, sy, s * 0.8f, fh, sway, alpha(t.flameOuter, 195));
            flame(g, sx, sy - s * 0.05f, s * 0.42f, fh * 0.62f, sway * 1.3f, alpha(t.flameCore, 240));
        }
    }

    private static void flame(Graphics2D g, float x, float y, float w, float h,
                              float sway, Color col) {
        if (w < 0.8f || h < 0.8f) return;
        g.setColor(col);
        g.fillPolygon(
            new int[]{(int) (x - w), (int) (x - w * 0.55f), (int) (x + sway),
                      (int) (x + w * 0.55f), (int) (x + w)},
            new int[]{(int) y, (int) (y - h * 0.5f), (int) (y - h),
                      (int) (y - h * 0.5f), (int) y}, 5);
    }

    /** Marks stairs, chests and the rest as billboards standing on their tile. */
    private static void paintSpecials(Graphics2D g, char[][] map, DungeonTheme t, View v, int pz) {
        int mapH = map.length, mapW = map[0].length;
        int cx = (int) v.camX, cy = (int) v.camY;
        int r = 6;

        for (int y = cy - r; y <= cy + r; y++) {
            if (y < 0 || y >= mapH) continue;
            for (int x = cx - r; x <= cx + r; x++) {
                if (x < 0 || x >= mapW) continue;
                char sp = DungeonWallQuery.getSpecial(x, y, pz, map, true);
                if (sp == '.') continue;
                float[] p = project(v, x + 0.5f, y + 0.5f);
                if (p == null || !visible(v, p[0], p[1])) continue;

                float size = p[2];
                float base = v.horizon + 0.5f * size;      // where the tile meets the floor
                float s = size * 0.16f;
                if (s < 2.5f) continue;
                float fade = Math.max(0.15f, Math.min(1f, 3.2f / p[1]));
                drawSpecial(g, sp, p[0], base, s, t, fade);
            }
        }
    }

    private static void drawSpecial(Graphics2D g, char sp, float x, float base, float s,
                                    DungeonTheme t, float fade) {
        Color bright = alpha(t.specialNear, (int) (235 * fade));
        Color dim = alpha(t.specialFar, (int) (200 * fade));
        g.setStroke(new java.awt.BasicStroke(Math.max(1f, s / 7f)));
        g.setColor(bright);

        switch (sp) {
            case 's' -> {                                   // stairway — receding steps
                for (int i = 0; i < 4; i++) {
                    float w = s * (1.5f - i * 0.22f);
                    float yy = base - i * s * 0.42f;
                    g.drawLine((int) (x - w), (int) yy, (int) (x + w), (int) yy);
                    g.setColor(i % 2 == 0 ? dim : bright);
                }
            }
            case 'B' -> {                                   // chest
                g.fillRect((int) (x - s), (int) (base - s), (int) (s * 2), (int) s);
                g.setColor(dim);
                g.drawLine((int) (x - s), (int) (base - s), (int) (x + s), (int) (base - s));
            }
            case 'A' -> {                                   // altar
                g.fillRect((int) (x - s), (int) (base - s * 0.7f), (int) (s * 2), (int) (s * 0.7f));
                g.setColor(dim);
                g.drawOval((int) (x - s * 0.5f), (int) (base - s * 1.6f), (int) s, (int) (s * 0.9f));
            }
            case 'f' -> {                                   // fountain
                g.drawOval((int) (x - s), (int) (base - s * 0.6f), (int) (s * 2), (int) (s * 0.8f));
                g.drawLine((int) x, (int) (base - s * 0.6f), (int) x, (int) (base - s * 2f));
            }
            case 't' -> {                                   // teleporter
                for (int i = 1; i <= 3; i++) {
                    float rr = s * i * 0.5f;
                    g.drawOval((int) (x - rr), (int) (base - rr * 0.4f), (int) (rr * 2), (int) (rr * 0.8f));
                }
            }
            case 'P', 'c' -> {                              // pit / chute
                g.setColor(new Color(0, 0, 0, (int) (220 * fade)));
                g.fillOval((int) (x - s * 1.2f), (int) (base - s * 0.5f),
                           (int) (s * 2.4f), (int) s);
                g.setColor(dim);
                g.drawOval((int) (x - s * 1.2f), (int) (base - s * 0.5f),
                           (int) (s * 2.4f), (int) s);
            }
            default -> {
                g.drawOval((int) (x - s * 0.6f), (int) (base - s * 1.2f),
                           (int) (s * 1.2f), (int) (s * 1.2f));
            }
        }
    }

    // ── Atmosphere ───────────────────────────────────────────────────────────

    private static final int DUST = 44;
    private static final float[] dustX = new float[DUST];
    private static final float[] dustY = new float[DUST];
    private static final float[] dustZ = new float[DUST];
    private static boolean dustInit;

    /** Motes hanging in the air. Depth-parallaxed so they read as being in the room, not on glass. */
    private static void paintDust(Graphics2D g, int w, int h, DungeonTheme t, long now,
                                  boolean dark) {
        if (dark) return;
        if (!dustInit) {
            java.util.Random r = new java.util.Random(4242);
            for (int i = 0; i < DUST; i++) {
                dustX[i] = r.nextFloat();
                dustY[i] = r.nextFloat();
                dustZ[i] = 0.25f + r.nextFloat() * 0.75f;
            }
            dustInit = true;
        }
        float time = (now % 60000) / 1000f;
        for (int i = 0; i < DUST; i++) {
            float drift = (float) Math.sin(time * 0.35f + i) * 0.012f;
            float fall = ((time * 0.010f * dustZ[i]) + dustY[i]) % 1f;
            int x = (int) ((dustX[i] + drift) * w);
            int y = (int) (fall * h);
            int a = (int) (70 * dustZ[i]);
            int sz = dustZ[i] > 0.75f ? 2 : 1;
            g.setColor(alpha(t.particleA, a));
            g.fillRect(x, y, sz, sz);
        }
    }

    private static void paintMonsterPreview(Graphics2D g, int w, int h, long elapsed) {
        float p = elapsed / (float) PREVIEW_DURATION_MS;
        float a = (p < 0.5f) ? p * 2f : (1f - p) * 2f;
        java.awt.Image img = io.cannonforge.retroquest.registry.ImageAssetRegistry.get(
                "monsters/" + previewMonster.getImageFileName().replace(".png", ""));
        int size = Math.min(w, h) / 2;
        int x = (w - size) / 2, y = (h - size) / 2;
        java.awt.Composite old = g.getComposite();
        g.setComposite(java.awt.AlphaComposite.getInstance(
                java.awt.AlphaComposite.SRC_OVER, Math.max(0f, Math.min(1f, a))));
        if (img != null) g.drawImage(img, x, y, size, size, null);
        g.setComposite(old);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static boolean isDoorTile(char c) {
        return c == 'd' || c == '|' || c == '[' || c == '{';
    }

    /** Nearest cardinal facing for the camera's angle — the HUD and minimap still think in four. */
    private static int facingOf(DungeonCamera cam) {
        double a = cam.getAngle() / (Math.PI / 2.0);
        return Math.floorMod((int) Math.round(a), 4);
    }

    private static Color alpha(Color c, int a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(0, Math.min(255, a)));
    }
}

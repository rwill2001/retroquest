package io.cannonforge.retroquest.core;

import java.awt.*;

import io.cannonforge.retroquest.model.Dungeon;
import io.cannonforge.retroquest.model.Monster;
import io.cannonforge.retroquest.model.Player;
import io.cannonforge.retroquest.model.Dungeon.WallType;
import io.cannonforge.retroquest.registry.ImageAssetRegistry;

/**
 * First-person wireframe dungeon renderer in the style of classic dungeon crawlers.
 *
 * <p>Draws a perspective corridor view using the existing {@link Dungeon} wall query API.
 * The renderer is stateless — all state comes from the {@link Retroquest} game instance.
 *
 * <h2>Coordinate conventions</h2>
 * <ul>
 *   <li>Player position: 0-based ({@code player.getX()}, {@code player.getY()})</li>
 *   <li>Wall queries: via {@link DungeonWallQuery}, which handles both authored
 *       (0-based) and procedural (1-based) dungeons transparently</li>
 *   <li>Facing: 0=NORTH (−Y), 1=EAST (+X), 2=SOUTH (+Y), 3=WEST (−X)</li>
 * </ul>
 */
public class WireframeDungeonRenderer {

    // ── Default Colors (green/amber CRT) ──────────────────────────────────────
    private static final Color BG           = new Color(0, 0, 0);
    private static final Color WALL_BRIGHT  = new Color(0, 255, 120);
    private static final Color WALL_DIM     = new Color(0, 100, 50);
    private static final Color WALL_FAR     = new Color(0, 60, 30);
    private static final Color DOOR_COLOR   = new Color(255, 200, 50);
    private static final Color DOOR_DIM     = new Color(160, 120, 30);
    private static final Color SPECIAL_COL  = new Color(64, 224, 208);
    private static final Color SPECIAL_DIM  = new Color(32, 112, 104);
    private static final Color HUD_BG       = new Color(0, 0, 0, 200);
    private static final Color HUD_TEXT     = new Color(255, 200, 60);
    private static final Color HUD_DIM      = new Color(120, 140, 100);
    private static final Color MINIMAP_BG   = new Color(5, 8, 15);
    private static final Color MINIMAP_WALL = new Color(0, 160, 80);
    private static final Color MINIMAP_DOOR = new Color(180, 140, 40);
    private static final Color MINIMAP_PLAYER = new Color(255, 255, 100);
    private static final Color MINIMAP_FOG  = new Color(0, 40, 20);

    // ── Storm Spire Colors (blue/purple lightning) ─────────────────────────────
    private static final Color STORM_WALL_BRIGHT = new Color(120, 140, 255);
    private static final Color STORM_WALL_FAR    = new Color(40, 30, 100);
    private static final Color STORM_DOOR_COLOR  = new Color(200, 160, 255);
    private static final Color STORM_DOOR_DIM    = new Color(100, 70, 140);
    private static final Color STORM_SPECIAL_COL = new Color(180, 200, 255);
    private static final Color STORM_HUD_TEXT    = new Color(180, 200, 255);
    private static final Color STORM_HUD_DIM     = new Color(80, 90, 140);
    private static final Color STORM_MINIMAP_WALL = new Color(80, 100, 200);
    private static final Color STORM_MINIMAP_FOG  = new Color(15, 15, 50);
    private static final Color STORM_GLOW        = new Color(100, 120, 255, 25);

    // ── Deep ocean palette (Pressure Temple / Thalorax) ──
    private static final Color DEEP_WALL_BRIGHT  = new Color(0, 200, 180);
    private static final Color DEEP_WALL_FAR     = new Color(0, 50, 60);
    private static final Color DEEP_DOOR_COLOR   = new Color(80, 255, 200);
    private static final Color DEEP_DOOR_DIM     = new Color(30, 120, 90);
    private static final Color DEEP_SPECIAL_COL  = new Color(50, 255, 220);
    private static final Color DEEP_HUD_TEXT     = new Color(80, 240, 200);
    private static final Color DEEP_HUD_DIM      = new Color(30, 100, 90);
    private static final Color DEEP_MINIMAP_WALL = new Color(0, 160, 140);
    private static final Color DEEP_MINIMAP_FOG  = new Color(0, 20, 30);
    private static final Color DEEP_GLOW         = new Color(0, 200, 180, 25);

    // ── Shadow palette (Archive of Tears / Forgotten City — Umbryn) ──
    private static final Color SHADOW_WALL_BRIGHT  = new Color(160, 168, 192);
    private static final Color SHADOW_WALL_FAR     = new Color(40, 35, 60);
    private static final Color SHADOW_DOOR_COLOR   = new Color(180, 160, 120);
    private static final Color SHADOW_DOOR_DIM     = new Color(90, 80, 60);
    private static final Color SHADOW_SPECIAL_COL  = new Color(120, 180, 210);
    private static final Color SHADOW_HUD_TEXT     = new Color(170, 178, 200);
    private static final Color SHADOW_HUD_DIM      = new Color(70, 72, 90);
    private static final Color SHADOW_MINIMAP_WALL = new Color(120, 125, 160);
    private static final Color SHADOW_MINIMAP_FOG  = new Color(12, 10, 22);
    private static final Color SHADOW_GLOW         = new Color(140, 145, 180, 20);

    // ── War palette (War Beneath / Iron Pit — Bellorak) ──
    private static final Color WAR_WALL_BRIGHT  = new Color(200, 160, 60);
    private static final Color WAR_WALL_FAR     = new Color(60, 40, 15);
    private static final Color WAR_DOOR_COLOR   = new Color(220, 180, 80);
    private static final Color WAR_DOOR_DIM     = new Color(100, 80, 30);
    private static final Color WAR_SPECIAL_COL  = new Color(255, 140, 40);
    private static final Color WAR_HUD_TEXT     = new Color(220, 190, 100);
    private static final Color WAR_HUD_DIM      = new Color(90, 75, 40);
    private static final Color WAR_MINIMAP_WALL = new Color(180, 140, 50);
    private static final Color WAR_MINIMAP_FOG  = new Color(20, 15, 5);
    private static final Color WAR_GLOW         = new Color(200, 150, 50, 25);

    // ── Cradle palette (Cradle of Shards — endgame) ──
    private static final Color CRADLE_WALL_BRIGHT  = new Color(220, 210, 240);
    private static final Color CRADLE_WALL_FAR     = new Color(50, 45, 70);
    private static final Color CRADLE_DOOR_COLOR   = new Color(255, 230, 180);
    private static final Color CRADLE_DOOR_DIM     = new Color(120, 100, 70);
    private static final Color CRADLE_SPECIAL_COL  = new Color(255, 240, 200);
    private static final Color CRADLE_HUD_TEXT     = new Color(230, 220, 240);
    private static final Color CRADLE_HUD_DIM      = new Color(100, 95, 120);
    private static final Color CRADLE_MINIMAP_WALL = new Color(180, 170, 210);
    private static final Color CRADLE_MINIMAP_FOG  = new Color(15, 12, 25);
    private static final Color CRADLE_GLOW         = new Color(220, 200, 240, 25);

    private static final Font F_HUD   = new Font("Monospaced", Font.BOLD, 14);
    private static final Font F_LABEL = new Font("Monospaced", Font.PLAIN, 11);

    // ── CRT glow colors ────────────────────────────────────────────────────
    private static final Color GLOW_GREEN  = new Color(0, 255, 120, 25);
    private static final Color SCANLINE    = new Color(0, 0, 0, 35);

    // ── Palettes ─────────────────────────────────────────────────────────────

    /**
     * One dungeon's colour scheme.
     *
     * <p>These used to be selected by threading five booleans through four paint methods and
     * asking each of a dozen expressions {@code cradle ? … : war ? … : shadow ? … : deep ? … :
     * storm ? … : default}. Every new dungeon meant editing all twelve. Resolving once into an
     * object instead means a new look is a constant and one line in {@link #paletteFor}.
     */
    private static final class Palette {
        final Color wallBright, wallFar, dim, doorColor, doorDim, special;
        final Color hudText, hudDim, minimapWall, minimapFog, glow;

        Palette(Color wallBright, Color wallFar, Color dim, Color doorColor, Color doorDim,
                Color special, Color hudText, Color hudDim, Color minimapWall, Color minimapFog,
                Color glow) {
            this.wallBright = wallBright; this.wallFar = wallFar; this.dim = dim;
            this.doorColor = doorColor; this.doorDim = doorDim; this.special = special;
            this.hudText = hudText; this.hudDim = hudDim;
            this.minimapWall = minimapWall; this.minimapFog = minimapFog; this.glow = glow;
        }
    }

    private static final Palette P_DEFAULT = new Palette(
            WALL_BRIGHT, WALL_FAR, WALL_DIM, DOOR_COLOR, DOOR_DIM, SPECIAL_COL,
            HUD_TEXT, HUD_DIM, MINIMAP_WALL, MINIMAP_FOG, GLOW_GREEN);

    /**
     * Pyralis's Ember Caverns. This is the palette the dungeon should always have had: it was the
     * only themed dungeon in the game with no entry in the table, so a cavern full of fire was
     * drawn in the default green phosphor.
     */
    private static final Palette P_EMBER = new Palette(
            new Color(255, 138, 48), new Color(74, 24, 8), new Color(132, 60, 20),
            new Color(255, 208, 96), new Color(150, 100, 36), new Color(255, 176, 72),
            new Color(255, 172, 84), new Color(138, 76, 38),
            new Color(214, 104, 36), new Color(42, 14, 5),
            new Color(255, 120, 40, 28));

    private static final Palette P_STORM = new Palette(
            STORM_WALL_BRIGHT, STORM_WALL_FAR, STORM_WALL_FAR, STORM_DOOR_COLOR, STORM_DOOR_DIM,
            STORM_SPECIAL_COL, STORM_HUD_TEXT, STORM_HUD_DIM, STORM_MINIMAP_WALL,
            STORM_MINIMAP_FOG, STORM_GLOW);

    private static final Palette P_DEEP = new Palette(
            DEEP_WALL_BRIGHT, DEEP_WALL_FAR, DEEP_WALL_FAR, DEEP_DOOR_COLOR, DEEP_DOOR_DIM,
            DEEP_SPECIAL_COL, DEEP_HUD_TEXT, DEEP_HUD_DIM, DEEP_MINIMAP_WALL,
            DEEP_MINIMAP_FOG, DEEP_GLOW);

    private static final Palette P_SHADOW = new Palette(
            SHADOW_WALL_BRIGHT, SHADOW_WALL_FAR, SHADOW_WALL_FAR, SHADOW_DOOR_COLOR,
            SHADOW_DOOR_DIM, SHADOW_SPECIAL_COL, SHADOW_HUD_TEXT, SHADOW_HUD_DIM,
            SHADOW_MINIMAP_WALL, SHADOW_MINIMAP_FOG, SHADOW_GLOW);

    private static final Palette P_WAR = new Palette(
            WAR_WALL_BRIGHT, WAR_WALL_FAR, WAR_WALL_FAR, WAR_DOOR_COLOR, WAR_DOOR_DIM,
            WAR_SPECIAL_COL, WAR_HUD_TEXT, WAR_HUD_DIM, WAR_MINIMAP_WALL,
            WAR_MINIMAP_FOG, WAR_GLOW);

    private static final Palette P_CRADLE = new Palette(
            CRADLE_WALL_BRIGHT, CRADLE_WALL_FAR, CRADLE_WALL_FAR, CRADLE_DOOR_COLOR,
            CRADLE_DOOR_DIM, CRADLE_SPECIAL_COL, CRADLE_HUD_TEXT, CRADLE_HUD_DIM,
            CRADLE_MINIMAP_WALL, CRADLE_MINIMAP_FOG, CRADLE_GLOW);

    /**
     * The palette for a dungeon.
     *
     * <p>Only Pyralis and Zephyrion still reach this renderer — Islands 4 to 7 moved to the
     * textured and raycast ones — but the deep, shadow, war and cradle entries are kept so that
     * routing an island back to {@code WIREFRAME} still gives it its own colours.
     */
    private static Palette paletteFor(String dungeonName) {
        if (dungeonName == null) return P_DEFAULT;
        return switch (dungeonName) {
            case "ember_caverns"    -> P_EMBER;
            case "storm_spire"      -> P_STORM;
            case "pressure_temple", "boneyard_trench", "leviathan_eye" -> P_DEEP;
            case "archive_of_tears", "forgotten_city" -> P_SHADOW;
            case "war_beneath", "iron_pit" -> P_WAR;
            case "cradle_of_shards" -> P_CRADLE;
            default -> P_DEFAULT;
        };
    }

    // ── Depth configuration ──────────────────────────────────────────────────
    private static final int MAX_DEPTH = 4; // How many tiles ahead we render
    private static final String[] FACING_NAMES = {"North", "East", "South", "West"};

    // Step offsets for each facing direction: N, E, S, W
    private static final int[] STEP_DX = { 0, 1, 0, -1};
    private static final int[] STEP_DY = {-1, 0, 1,  0};

    // ── Monster preview state ────────────────────────────────────────────────
    private static Monster previewMonster = null;
    private static long    previewStartMs = 0;
    private static final long PREVIEW_DURATION_MS = 400;

    // ── Public API for animation triggers ───────────────────────────────────

    /** Call before startCombat() to flash the monster sprite in the corridor. */
    public static void showMonsterPreview(Monster m) {
        previewMonster = m;
        previewStartMs = System.currentTimeMillis();
    }

    /** Returns true if a monster preview is currently animating. */
    public static boolean isPreviewActive() {
        return previewMonster != null && (System.currentTimeMillis() - previewStartMs) < PREVIEW_DURATION_MS;
    }

    /** Call on each movement to trigger the step slide animation. */
    public static void startStepAnimation(int fromX, int fromY, int toX, int toY) {
    }

    // ── Main paint method ────────────────────────────────────────────────────

    public static void paint(Graphics2D g2, int W, int H, Retroquest game) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        Player player = game.getPlayer();
        boolean authored = game.getDungeonViewState().isAuthored();
        char[][] map = game.getCurrentMap();
        // Authored = 0-based coords; Procedural = 1-based coords
        int px  = authored ? player.getX() : player.getX() + 1;
        int py  = authored ? player.getY() : player.getY() + 1;
        int pz  = game.getCurrentDepth();
        int facing = player.getFacing();

        // Check if player is in a dark zone (tile 'k') — suppresses minimap and compass
        char playerTile = DungeonWallQuery.getSpecial(px, py, pz, map, authored);
        boolean inDarkZone = (playerTile == 'k');

        // Detect themed dungeon for color palette swap
        String dName = game.getDungeonViewState().getAuthoredDungeonName();
        Palette p = paletteFor(dName);

        // Layout: 3D corridor on top, large minimap on bottom, HUD bar between them
        int hudH = 32;
        boolean showMinimap = game.getDungeonViewState().isMinimapVisible() && !inDarkZone;
        int minimapH = showMinimap ? Math.max(H * 2 / 5, 180) : 0; // 40% of height
        int corridorH = H - hudH - minimapH;

        // ── Black background ──
        g2.setColor(BG);
        g2.fillRect(0, 0, W, H);

        // ── 3D Wireframe corridor (top) ──
        paintCorridor(g2, W, corridorH, px, py, pz, facing, map, authored, p);

        // ── Monster preview flash ──
        if (previewMonster != null) {
            long elapsed = System.currentTimeMillis() - previewStartMs;
            if (elapsed < PREVIEW_DURATION_MS) {
                paintMonsterPreview(g2, W, corridorH, previewMonster, elapsed);
            } else {
                previewMonster = null;
            }
        }

        // ── CRT phosphor glow (on corridor area) ──
        paintCRTEffect(g2, W, corridorH, p);

        // ── HUD bar (middle) ──
        paintHUD(g2, 0, corridorH, W, hudH, px, py, pz, facing, map, authored, inDarkZone, p);

        // ── Minimap (bottom, full width) ──
        if (showMinimap) {
            paintMinimap(g2, 0, corridorH + hudH, W, minimapH, px, py, pz, facing, game, map, authored, p);
        }
    }

    // ── Corridor rendering ───────────────────────────────────────────────────

    private static void paintCorridor(Graphics2D g, int W, int H, int px, int py, int pz, int facing,
                                       char[][] map, boolean authored, Palette p) {
        // Resolve colors based on theme
        Color wallBright = p.wallBright;
        Color wallFar    = p.wallFar;
        Color doorColor  = p.doorColor;
        Color doorDim    = p.doorDim;
        // Vanishing point
        int cx = W / 2;
        int cy = H / 2;

        // Pre-compute depth-slice boundaries
        int[] left   = new int[MAX_DEPTH + 2];
        int[] right  = new int[MAX_DEPTH + 2];
        int[] top    = new int[MAX_DEPTH + 2];
        int[] bottom = new int[MAX_DEPTH + 2];

        for (int d = 0; d <= MAX_DEPTH + 1; d++) {
            float scale = 1.0f / (1.0f + d * 0.8f);
            int halfW = (int)(W * 0.48f * scale);
            int halfH = (int)(H * 0.48f * scale);
            left[d]   = cx - halfW;
            right[d]  = cx + halfW;
            top[d]    = cy - halfH;
            bottom[d] = cy + halfH;
        }

        // Track the nearest front wall — corridor edges stop here
        int frontWallDepth = MAX_DEPTH; // no wall by default

        // Render back-to-front (farthest depth first)
        for (int d = MAX_DEPTH - 1; d >= 0; d--) {
            // Band d spans planes d..d+1 and shows cell C(d) = player + facing*d,
            // so d == 0 is the player's OWN cell and the front wall drawn at plane
            // d+1 is the wall between C(d) and C(d+1) — the same wall canMove() tests.
            int cellX = px + STEP_DX[facing] * d;
            int cellY = py + STEP_DY[facing] * d;

            Color wallCol = depthColor(wallBright, wallFar, d, MAX_DEPTH);
            Color wallDim = new Color(wallCol.getRed(), wallCol.getGreen(), wallCol.getBlue(), 50);
            Color doorCol = depthColor(doorColor, doorDim, d, MAX_DEPTH);
            float thick = Math.max(1.0f, 2.5f - d * 0.5f);
            g.setStroke(new BasicStroke(thick));

            // If out of bounds, seal the view with a solid end wall at plane d (the
            // near face of the missing cell) and keep going — the loop runs far->near,
            // so the nearer slices still have to be painted on top of it.
            int mapW = DungeonWallQuery.getMapWidth(map, authored);
            int mapH = DungeonWallQuery.getMapHeight(map, authored);
            int minCoord = authored ? 0 : 1;
            if (cellX < minCoord || cellX >= (authored ? mapW : mapW + 1)
             || cellY < minCoord || cellY >= (authored ? mapH : mapH + 1)) {
                drawFrontWall(g, left[d], top[d], right[d], bottom[d],
                              WallType.WALL, wallCol, doorCol);
                frontWallDepth = Math.min(frontWallDepth, d - 1); // last visible band is d-1
                continue;
            }

            WallType leftWall  = DungeonWallQuery.getRelativeWall(cellX, cellY, pz, facing, -1, map, authored);
            WallType rightWall = DungeonWallQuery.getRelativeWall(cellX, cellY, pz, facing,  1, map, authored);
            WallType frontWall = DungeonWallQuery.getFrontWall(cellX, cellY, pz, facing, map, authored);

            // ── LEFT SIDE ──
            if (leftWall == WallType.WALL) {
                // Solid wall: draw filled trapezoid panel
                drawSideWall(g, left[d], top[d], bottom[d], left[d+1], top[d+1], bottom[d+1], wallCol);
            } else if (leftWall == WallType.DOOR) {
                drawSideDoor(g, left[d], top[d], bottom[d], left[d+1], top[d+1], bottom[d+1], doorCol);
            } else {
                // OPEN passage — draw a side opening (recessed alcove showing depth)
                drawSideOpening(g, left[d], top[d], bottom[d], left[d+1], top[d+1], bottom[d+1], wallDim, true);
            }

            // ── RIGHT SIDE ──
            if (rightWall == WallType.WALL) {
                drawSideWall(g, right[d], top[d], bottom[d], right[d+1], top[d+1], bottom[d+1], wallCol);
            } else if (rightWall == WallType.DOOR) {
                drawSideDoor(g, right[d], top[d], bottom[d], right[d+1], top[d+1], bottom[d+1], doorCol);
            } else {
                drawSideOpening(g, right[d], top[d], bottom[d], right[d+1], top[d+1], bottom[d+1], wallDim, false);
            }

            // ── FRONT WALL ──
            if (frontWall == WallType.WALL || frontWall == WallType.DOOR) {
                drawFrontWall(g, left[d+1], top[d+1], right[d+1], bottom[d+1],
                              frontWall, wallCol, doorCol);
                frontWallDepth = Math.min(frontWallDepth, d);
            }

            // ── SPECIAL FEATURE ──
            // Icons stand on plane d+1, which is the NEAR face of the next cell C(d+1),
            // so the icon belongs to that cell (never the tile the player stands on).
            char special = DungeonWallQuery.getSpecial(cellX + STEP_DX[facing],
                                                       cellY + STEP_DY[facing], pz, map, authored);
            if (special != '.') {
                drawSpecialIcon(g, left[d+1], right[d+1], bottom[d+1], top[d+1], special, d);
            }
        }

        // Draw corridor edge frame — only up to where a front wall blocks the view
        int edgeLimit = Math.min(MAX_DEPTH, frontWallDepth + 1);
        for (int d = 0; d < edgeLimit; d++) {
            Color edgeCol = depthColor(wallBright, wallFar, d, MAX_DEPTH);
            float edgeThick = Math.max(1.0f, 2.5f - d * 0.5f);
            g.setStroke(new BasicStroke(edgeThick));
            g.setColor(edgeCol);

            // Left vertical edge (near to far)
            g.drawLine(left[d], top[d], left[d+1], top[d+1]);
            g.drawLine(left[d], bottom[d], left[d+1], bottom[d+1]);
            // Right vertical edge
            g.drawLine(right[d], top[d], right[d+1], top[d+1]);
            g.drawLine(right[d], bottom[d], right[d+1], bottom[d+1]);

            // Floor and ceiling lines at each depth
            Color dimEdge = new Color(edgeCol.getRed(), edgeCol.getGreen(), edgeCol.getBlue(), 60);
            g.setColor(dimEdge);
            g.drawLine(left[d+1], top[d+1], right[d+1], top[d+1]);     // ceiling
            g.drawLine(left[d+1], bottom[d+1], right[d+1], bottom[d+1]); // floor
        }

        // Outer frame (brightest)
        g.setStroke(new BasicStroke(2.5f));
        g.setColor(wallBright);
        g.drawLine(left[0], top[0], left[0], bottom[0]);
        g.drawLine(right[0], top[0], right[0], bottom[0]);
        g.drawLine(left[0], top[0], right[0], top[0]);
        g.drawLine(left[0], bottom[0], right[0], bottom[0]);
    }

    // ── Wall drawing helpers ─────────────────────────────────────────────────

    /** Draws a side wall as a filled trapezoid with wireframe border. */
    private static void drawSideWall(Graphics2D g, int nearX, int nearTop, int nearBot,
                                      int farX, int farTop, int farBot, Color col) {
        // Filled trapezoid (dark interior)
        int[] xPts = {nearX, farX, farX, nearX};
        int[] yPts = {nearTop, farTop, farBot, nearBot};
        Color fill = new Color(col.getRed() / 6, col.getGreen() / 6, col.getBlue() / 6);
        g.setColor(fill);
        g.fillPolygon(xPts, yPts, 4);

        // Wireframe border
        g.setColor(col);
        g.drawLine(nearX, nearTop, farX, farTop);     // top edge
        g.drawLine(nearX, nearBot, farX, farBot);     // bottom edge
        g.drawLine(farX, farTop, farX, farBot);       // far vertical edge

        // Horizontal mortar lines
        int midY1 = nearTop + (nearBot - nearTop) / 3;
        int midY2 = nearTop + (nearBot - nearTop) * 2 / 3;
        int fmidY1 = farTop + (farBot - farTop) / 3;
        int fmidY2 = farTop + (farBot - farTop) * 2 / 3;
        Color dim = new Color(col.getRed(), col.getGreen(), col.getBlue(), 40);
        g.setColor(dim);
        g.drawLine(nearX, midY1, farX, fmidY1);
        g.drawLine(nearX, midY2, farX, fmidY2);
    }

    /** Draws a side door as a filled trapezoid with door frame detail. */
    private static void drawSideDoor(Graphics2D g, int nearX, int nearTop, int nearBot,
                                      int farX, int farTop, int farBot, Color col) {
        // Filled wall
        int[] xPts = {nearX, farX, farX, nearX};
        int[] yPts = {nearTop, farTop, farBot, nearBot};
        Color fill = new Color(col.getRed() / 5, col.getGreen() / 5, col.getBlue() / 5);
        g.setColor(fill);
        g.fillPolygon(xPts, yPts, 4);

        // Wireframe border
        g.setColor(col);
        g.drawLine(nearX, nearTop, farX, farTop);
        g.drawLine(nearX, nearBot, farX, farBot);
        g.drawLine(farX, farTop, farX, farBot);

        // Door frame (inner lines)
        int dNearTop = nearTop + (nearBot - nearTop) / 6;
        int dNearBot = nearBot - (nearBot - nearTop) / 10;
        int dFarTop  = farTop + (farBot - farTop) / 6;
        int dFarBot  = farBot - (farBot - farTop) / 10;
        Color doorFill = new Color(col.getRed() / 3, col.getGreen() / 3, col.getBlue() / 3);
        g.setColor(doorFill);
        int[] dx = {nearX, farX, farX, nearX};
        int[] dy = {dNearTop, dFarTop, dFarBot, dNearBot};
        g.fillPolygon(dx, dy, 4);
        g.setColor(col);
        g.drawLine(nearX, dNearTop, farX, dFarTop);
        g.drawLine(nearX, dNearBot, farX, dFarBot);
    }

    /** Draws a side opening (passage going left or right) as a recessed alcove with dark interior. */
    private static void drawSideOpening(Graphics2D g, int nearX, int nearTop, int nearBot,
                                         int farX, int farTop, int farBot, Color col, boolean isLeft) {
        int recessTop = nearTop + (farTop - nearTop) / 2;
        int recessBot = nearBot + (farBot - nearBot) / 2;

        // Dark fill inside the passage opening
        int[] xPts = {nearX, nearX, farX, farX};
        int[] yPts = {recessTop, recessBot, farBot, farTop};
        g.setColor(new Color(3, 5, 10));
        g.fillPolygon(xPts, yPts, 4);

        // Wireframe edges
        g.setColor(col);
        // Top edge of opening
        g.drawLine(nearX, nearTop, nearX, recessTop);
        g.drawLine(nearX, recessTop, farX, farTop);
        // Bottom edge of opening
        g.drawLine(nearX, nearBot, nearX, recessBot);
        g.drawLine(nearX, recessBot, farX, farBot);
        // Back wall of passage (faint)
        Color backWall = new Color(col.getRed(), col.getGreen(), col.getBlue(), 25);
        g.setColor(backWall);
        g.drawLine(nearX, recessTop, nearX, recessBot);
    }

    /** Draws a front wall (or door) as a filled rectangle with wireframe border. */
    private static void drawFrontWall(Graphics2D g, int l, int t, int r, int b,
                                       WallType type, Color wallCol, Color doorCol) {
        int fw = r - l, fh = b - t;
        if (type == WallType.WALL) {
            // Solid fill — blocks everything behind
            Color fill = new Color(wallCol.getRed() / 5, wallCol.getGreen() / 5, wallCol.getBlue() / 5);
            g.setColor(fill);
            g.fillRect(l, t, fw, fh);
            // Wireframe border
            g.setColor(wallCol);
            g.drawRect(l, t, fw, fh);
            // Brick/stone texture lines
            Color dim = new Color(wallCol.getRed(), wallCol.getGreen(), wallCol.getBlue(), 50);
            g.setColor(dim);
            int midX = (l + r) / 2;
            int midY = (t + b) / 2;
            g.drawLine(l, midY, r, midY);
            g.drawLine(midX, t, midX, b);
            // Additional mortar lines for larger walls
            if (fw > 40) {
                int q1 = l + fw / 4, q3 = l + fw * 3 / 4;
                int t1 = t + fh / 4, t3 = t + fh * 3 / 4;
                g.drawLine(q1, t, q1, b);
                g.drawLine(q3, t, q3, b);
                g.drawLine(l, t1, r, t1);
                g.drawLine(l, t3, r, t3);
            }
        } else if (type == WallType.DOOR) {
            // Solid wall around door
            Color fill = new Color(doorCol.getRed() / 5, doorCol.getGreen() / 5, doorCol.getBlue() / 5);
            g.setColor(fill);
            g.fillRect(l, t, fw, fh);
            // Door frame border
            g.setColor(doorCol);
            g.drawRect(l, t, fw, fh);
            // Door panel (inset, slightly brighter)
            int inset = Math.max(3, fw / 6);
            int doorTop = t + inset;
            int doorBot = b - inset / 2;
            int doorL = l + inset;
            int doorR = r - inset;
            Color doorFill = new Color(doorCol.getRed() / 3, doorCol.getGreen() / 3, doorCol.getBlue() / 3);
            g.setColor(doorFill);
            g.fillRect(doorL, doorTop, doorR - doorL, doorBot - doorTop);
            g.setColor(doorCol);
            g.drawRect(doorL, doorTop, doorR - doorL, doorBot - doorTop);
            // Door knob
            int knobX = doorL + (doorR - doorL) * 3 / 4;
            int knobY = (doorTop + doorBot) / 2;
            g.fillOval(knobX - 2, knobY - 2, 5, 5);
            // Arch at top
            g.drawArc(doorL, doorTop - inset, doorR - doorL, inset * 2, 0, 180);
        }
    }

    // ── Special feature icons ────────────────────────────────────────────────

    private static void drawSpecialIcon(Graphics2D g, int l, int r, int bot, int top,
                                         char special, int depth) {
        Color col = depthColor(SPECIAL_COL, SPECIAL_DIM, depth, MAX_DEPTH);
        g.setColor(col);
        g.setStroke(new BasicStroke(Math.max(1, 2 - depth * 0.4f)));

        int cx = (l + r) / 2;
        int floorY = bot - (bot - top) / 8; // slightly above floor line
        int sz = Math.max(4, (r - l) / 6);

        switch (special) {
            case 'A' -> { // Altar — diamond
                g.drawLine(cx, floorY - sz, cx + sz, floorY);
                g.drawLine(cx + sz, floorY, cx, floorY + sz);
                g.drawLine(cx, floorY + sz, cx - sz, floorY);
                g.drawLine(cx - sz, floorY, cx, floorY - sz);
            }
            case 'f' -> { // Fountain — circle with drops
                g.drawOval(cx - sz, floorY - sz/2, sz*2, sz);
                g.drawLine(cx, floorY - sz, cx, floorY - sz * 2);
            }
            case 's' -> { // Stairs — step lines
                for (int i = 0; i < 3; i++) {
                    int sy = floorY - i * sz / 2;
                    int sw = sz - i * sz / 4;
                    g.drawLine(cx - sw, sy, cx + sw, sy);
                }
            }
            case 'H' -> { // Throne — chair shape
                g.drawRect(cx - sz, floorY - sz, sz * 2, sz);
                g.drawLine(cx - sz, floorY - sz, cx - sz, floorY - sz * 2);
                g.drawLine(cx + sz, floorY - sz, cx + sz, floorY - sz * 2);
            }
            case 't' -> { // Teleporter — concentric circles
                g.drawOval(cx - sz, floorY - sz/2, sz*2, sz);
                g.drawOval(cx - sz/2, floorY - sz/4, sz, sz/2);
            }
            case 'P' -> { // Pit — downward arrow
                g.drawLine(cx, floorY - sz, cx, floorY + sz);
                g.drawLine(cx - sz/2, floorY + sz/2, cx, floorY + sz);
                g.drawLine(cx + sz/2, floorY + sz/2, cx, floorY + sz);
            }
            case 'I' -> { // Inn — bed shape
                g.drawRect(cx - sz, floorY - sz/2, sz * 2, sz);
                g.drawLine(cx - sz, floorY - sz, cx - sz + sz/3, floorY - sz/2);
            }
            case 'B' -> { // Treasure Chest — box with lid
                g.drawRect(cx - sz, floorY - sz/2, sz * 2, sz);
                g.drawLine(cx - sz, floorY - sz/2, cx - sz + sz/3, floorY - sz);
                g.drawLine(cx + sz, floorY - sz/2, cx + sz - sz/3, floorY - sz);
                g.drawLine(cx - sz + sz/3, floorY - sz, cx + sz - sz/3, floorY - sz);
            }
            case 'R' -> { // Puzzle — angular rune glyph
                g.drawLine(cx, floorY - sz * 2, cx, floorY + sz / 2);       // vertical stroke
                g.drawLine(cx - sz, floorY - sz, cx + sz, floorY - sz);      // cross bar
                g.drawLine(cx - sz, floorY - sz, cx - sz / 2, floorY);       // left leg
                g.drawLine(cx + sz, floorY - sz, cx + sz / 2, floorY);       // right leg
            }
            case 'n' -> { // Spinner — rotating arrows
                g.drawOval(cx - sz, floorY - sz, sz * 2, sz * 2);
                // Arrow tips at cardinal points
                g.drawLine(cx, floorY - sz, cx + sz/3, floorY - sz + sz/3);
                g.drawLine(cx + sz, floorY, cx + sz - sz/3, floorY - sz/3);
                g.drawLine(cx, floorY + sz, cx - sz/3, floorY + sz - sz/3);
                g.drawLine(cx - sz, floorY, cx - sz + sz/3, floorY + sz/3);
            }
            case 'k' -> { // Dark zone — eye with slash
                g.drawOval(cx - sz, floorY - sz/2, sz * 2, sz);
                g.fillOval(cx - sz/3, floorY - sz/4, sz*2/3, sz/2);
                g.drawLine(cx - sz, floorY + sz/2, cx + sz, floorY - sz);
            }
            case 'c' -> { // Chute — downward spiral
                g.drawOval(cx - sz, floorY - sz/2, sz * 2, sz);
                g.drawLine(cx, floorY, cx, floorY + sz * 2);
                g.drawLine(cx - sz/2, floorY + sz + sz/2, cx, floorY + sz * 2);
                g.drawLine(cx + sz/2, floorY + sz + sz/2, cx, floorY + sz * 2);
            }
            case 'q' -> { // Riddle door — question mark on door
                g.drawRect(cx - sz, floorY - sz * 2, sz * 2, sz * 3);
                g.setFont(F_LABEL);
                g.drawString("?", cx - 3, floorY);
            }
            default -> { // Unknown — question mark
                g.setFont(F_LABEL);
                g.drawString("?", cx - 3, floorY);
            }
        }
    }

    // ── Minimap ──────────────────────────────────────────────────────────────

    private static void paintMinimap(Graphics2D g, int x0, int y0, int w, int h,
                                      int px, int py, int pz, int facing, Retroquest game,
                                      char[][] map, boolean authored, Palette p) {
        Color mmBg   = MINIMAP_BG;
        Color mmWall = p.minimapWall;
        Color mmFog  = p.minimapFog;
        Color mmDim  = p.dim;
        Color mmHDim = p.hudDim;

        // Background
        g.setColor(mmBg);
        g.fillRect(x0, y0, w, h);
        g.setColor(mmDim);
        g.drawRect(x0, y0, w - 1, h - 1);

        // Label
        g.setColor(mmHDim);
        g.setFont(F_LABEL);
        g.drawString("MAP", x0 + 6, y0 + 14);

        // Scale tiles to fill available space — aim for ~30-50 tiles visible
        int margin = 8;
        int availW = w - margin * 2;
        int availH = h - 22 - margin;
        int targetTiles = 35; // how many tiles across to show
        int tileSize = Math.max(3, Math.min(availW / targetTiles, availH / targetTiles));
        int tilesW = availW / tileSize;
        int tilesH = availH / tileSize;
        int mapX0 = x0 + margin + (availW - tilesW * tileSize) / 2; // center
        int mapY0 = y0 + 20 + (availH - tilesH * tileSize) / 2;

        // Center on player
        int startX = px - tilesW / 2;
        int startY = py - tilesH / 2;

        boolean[][] revealed = game.getRevealedForLevel(pz);

        for (int ty = 0; ty < tilesH; ty++) {
            for (int tx = 0; tx < tilesW; tx++) {
                int wx = startX + tx;
                int wy = startY + ty;
                int sx = mapX0 + tx * tileSize;
                int sy = mapY0 + ty * tileSize;

                // Check if revealed — authored coords are already 0-based, procedural need -1
                int rx = authored ? wx : wx - 1;
                int ry = authored ? wy : wy - 1;
                boolean vis = (rx >= 0 && ry >= 0 && revealed != null
                        && ry < revealed.length && rx < revealed[0].length && revealed[ry][rx]);

                // For authored maps: also show wall tiles adjacent to any revealed floor
                if (!vis && authored && rx >= 0 && ry >= 0 && revealed != null
                        && ry < revealed.length && rx < revealed[0].length
                        && wx >= 0 && wx < map[0].length && wy >= 0 && wy < map.length
                        && !io.cannonforge.retroquest.registry.TileRegistry.getByIdSafe(map[wy][wx]).isWalkable()) {
                    // Check if any neighbor is revealed
                    for (int[] d : new int[][]{{-1,0},{1,0},{0,-1},{0,1}}) {
                        int nx = rx + d[0], ny = ry + d[1];
                        if (nx >= 0 && ny >= 0 && ny < revealed.length && nx < revealed[0].length && revealed[ny][nx]) {
                            vis = true;
                            break;
                        }
                    }
                }

                int mw = DungeonWallQuery.getMapWidth(map, authored);
                int mh = DungeonWallQuery.getMapHeight(map, authored);
                int lo = authored ? 0 : 1;
                if (!vis || wx < lo || wx >= (authored ? mw : mw + 1)
                         || wy < lo || wy >= (authored ? mh : mh + 1)) {
                    g.setColor(MINIMAP_BG);
                    g.fillRect(sx, sy, tileSize, tileSize);
                    continue;
                }

                // For authored maps: check if THIS tile is a wall (fill solid)
                if (authored && wy >= 0 && wy < map.length && wx >= 0 && wx < map[0].length) {
                    char tile = map[wy][wx];
                    if (!io.cannonforge.retroquest.registry.TileRegistry.getByIdSafe(tile).isWalkable()) {
                        g.setColor(mmWall);
                        g.fillRect(sx, sy, tileSize, tileSize);
                        continue; // wall tile — no need to check edges
                    }
                    // Door tiles get a special color
                    if (tile == 'd' || tile == '|' || tile == '[' || tile == '{') {
                        g.setColor(MINIMAP_DOOR);
                        g.fillRect(sx, sy, tileSize, tileSize);
                        continue;
                    }
                }

                // Floor
                g.setColor(mmFog);
                g.fillRect(sx, sy, tileSize, tileSize);

                if (!authored) {
                    // Procedural: draw edge-based walls
                    WallType n = DungeonWallQuery.getWall(wx, wy, pz, 0, map, authored);
                    WallType w2 = DungeonWallQuery.getWall(wx, wy, pz, 3, map, authored);
                    WallType s = DungeonWallQuery.getWall(wx, wy, pz, 2, map, authored);
                    WallType e2 = DungeonWallQuery.getWall(wx, wy, pz, 1, map, authored);

                    if (n == WallType.WALL)      { g.setColor(mmWall); g.drawLine(sx, sy, sx+tileSize-1, sy); }
                    else if (n == WallType.DOOR) { g.setColor(MINIMAP_DOOR); g.drawLine(sx, sy, sx+tileSize-1, sy); }
                    if (w2 == WallType.WALL)     { g.setColor(mmWall); g.drawLine(sx, sy, sx, sy+tileSize-1); }
                    else if (w2 == WallType.DOOR){ g.setColor(MINIMAP_DOOR); g.drawLine(sx, sy, sx, sy+tileSize-1); }
                    if (s == WallType.WALL)      { g.setColor(mmWall); g.drawLine(sx, sy+tileSize-1, sx+tileSize-1, sy+tileSize-1); }
                    if (e2 == WallType.WALL)     { g.setColor(mmWall); g.drawLine(sx+tileSize-1, sy, sx+tileSize-1, sy+tileSize-1); }
                }

                // Special features
                char spec = DungeonWallQuery.getSpecial(wx, wy, pz, map, authored);
                if (spec != '.') {
                    g.setColor(p.special);
                    g.fillRect(sx + 1, sy + 1, tileSize - 2, tileSize - 2);
                }
            }
        }

        // Player position + facing arrow
        int playerSx = mapX0 + (px - startX) * tileSize;
        int playerSy = mapY0 + (py - startY) * tileSize;
        g.setColor(MINIMAP_PLAYER);
        g.fillRect(playerSx, playerSy, tileSize, tileSize);

        // Facing arrow (triangle pointing in facing direction)
        int pcx = playerSx + tileSize / 2;
        int pcy = playerSy + tileSize / 2;
        int arrowLen = tileSize * 2;
        int ax = pcx + STEP_DX[facing] * arrowLen;
        int ay = pcy + STEP_DY[facing] * arrowLen;
        g.setStroke(new BasicStroke(2));
        g.drawLine(pcx, pcy, ax, ay);
        // Arrowhead
        int perpDx = STEP_DY[facing]; // perpendicular
        int perpDy = -STEP_DX[facing];
        int hx = ax - STEP_DX[facing] * tileSize / 2;
        int hy = ay - STEP_DY[facing] * tileSize / 2;
        g.drawLine(ax, ay, hx + perpDx * tileSize / 2, hy + perpDy * tileSize / 2);
        g.drawLine(ax, ay, hx - perpDx * tileSize / 2, hy - perpDy * tileSize / 2);
    }

    // ── HUD ──────────────────────────────────────────────────────────────────

    private static void paintHUD(Graphics2D g, int x, int y, int w, int h,
                                  int px, int py, int pz, int facing,
                                  char[][] map, boolean authored, boolean inDarkZone,
                                  Palette p) {
        // Background bar
        g.setColor(HUD_BG);
        g.fillRect(x, y, w, h);
        g.setColor(p.dim);
        g.drawLine(x, y, x + w, y);

        g.setFont(F_HUD);
        FontMetrics fm = g.getFontMetrics();

        // Depth + facing
        String info = inDarkZone
                ? "Level " + pz + " \u00b7 Facing ???"
                : "Level " + pz + " \u00b7 Facing " + FACING_NAMES[facing];
        Color hudText = p.hudText;
        Color hudDim  = p.hudDim;
        Color specCol = p.special;
        g.setColor(inDarkZone ? hudDim : hudText);
        g.drawString(info, x + 12, y + h / 2 + fm.getAscent() / 2 - 2);

        // Current tile special
        char special = DungeonWallQuery.getSpecial(px, py, pz, map, authored);
        String tileDesc = inDarkZone ? "Darkness..." : specialDescription(special);
        if (tileDesc != null) {
            g.setColor(inDarkZone ? new Color(100, 60, 120) : specCol);
            g.drawString("\u00b7 " + tileDesc, x + 12 + fm.stringWidth(info) + 16,
                         y + h / 2 + fm.getAscent() / 2 - 2);
        }

        // Compass (right side) — hidden in dark zones
        if (!inDarkZone) {
            int compassX = x + w - 60;
            int compassY = y + h / 2;
            int cr = 10;
            g.setColor(hudDim);
            g.drawOval(compassX - cr, compassY - cr, cr * 2, cr * 2);
            // Cardinal letters
            g.setFont(F_LABEL);
            g.setColor(facing == 0 ? hudText : hudDim); g.drawString("N", compassX - 3, compassY - cr - 2);
            g.setColor(facing == 2 ? hudText : hudDim); g.drawString("S", compassX - 3, compassY + cr + 11);
            g.setColor(facing == 1 ? hudText : hudDim); g.drawString("E", compassX + cr + 3, compassY + 4);
            g.setColor(facing == 3 ? hudText : hudDim); g.drawString("W", compassX - cr - 12, compassY + 4);
            // Facing indicator
            g.setColor(hudText);
            int arrowX = compassX + STEP_DX[facing] * (cr - 2);
            int arrowY = compassY + STEP_DY[facing] * (cr - 2);
            g.fillOval(arrowX - 3, arrowY - 3, 6, 6);
        }
    }

    // ── CRT Phosphor Glow Effect ────────────────────────────────────────────

    /**
     * Applies a retro CRT monitor effect: horizontal scanlines + subtle phosphor bloom.
     * The bloom is achieved by drawing a slightly enlarged, semi-transparent copy of the
     * wireframe lines using a wide stroke — cheap but effective.
     */
    private static void paintCRTEffect(Graphics2D g, int W, int H, Palette p) {
        // Scanlines — horizontal dark bands every 3 pixels
        g.setColor(SCANLINE);
        for (int y = 0; y < H; y += 3) {
            g.drawLine(0, y, W, y);
        }

        // Phosphor bloom — soft glow around the view edges
        // Top edge
        g.setColor(p.glow);
        g.fillRect(0, 0, W, 2);
        // Bottom edge
        g.fillRect(0, H - 2, W, 2);
        // Left edge
        g.fillRect(0, 0, 2, H);
        // Right edge
        g.fillRect(W - 2, 0, 2, H);

        // Vignette — darken corners for CRT curvature illusion
        Composite old = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.3f));
        g.setColor(Color.BLACK);
        int cornerR = Math.min(W, H) / 3;
        // Four corner triangles
        g.fillOval(-cornerR/2, -cornerR/2, cornerR, cornerR);
        g.fillOval(W - cornerR/2, -cornerR/2, cornerR, cornerR);
        g.fillOval(-cornerR/2, H - cornerR/2, cornerR, cornerR);
        g.fillOval(W - cornerR/2, H - cornerR/2, cornerR, cornerR);
        g.setComposite(old);
    }

    // ── Monster Preview Flash ────────────────────────────────────────────────

    /**
     * Draws the monster sprite in the corridor at depth 2, fading in then out.
     * Called when an encounter is about to trigger.
     */
    private static void paintMonsterPreview(Graphics2D g, int W, int H, Monster monster, long elapsedMs) {
        // Fade curve: quick fade-in (0-100ms), hold (100-250ms), fade-out (250-400ms)
        float alpha;
        if (elapsedMs < 100) {
            alpha = elapsedMs / 100f;
        } else if (elapsedMs < 250) {
            alpha = 1.0f;
        } else {
            alpha = 1.0f - (elapsedMs - 250f) / 150f;
        }
        alpha = Math.max(0f, Math.min(1f, alpha));

        // Load the monster sprite
        String spriteKey = "monsters/" + monster.getImageFileName().replace(".png", "");
        Image monImg = ImageAssetRegistry.get(spriteKey);
        if (monImg == null) monImg = ImageAssetRegistry.get("monsters/unknown");

        // Draw at depth ~2 (centered, scaled down)
        int cx = W / 2;
        int cy = H / 2;
        float scale = 0.35f; // depth-2 size
        int spriteW = (int)(W * scale);
        int spriteH = (int)(H * scale);
        int sx = cx - spriteW / 2;
        int sy = cy - spriteH / 2 + 20; // slightly below center (floor level)

        Composite oldComp = g.getComposite();

        if (monImg != null) {
            // Draw sprite with alpha
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            // Use nearest-neighbor scaling for pixel art
            Object oldInterp = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.drawImage(monImg, sx, sy, spriteW, spriteH, null);
            if (oldInterp != null) g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, oldInterp);
        }

        // Red warning flash border
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha * 0.3f));
        g.setColor(new Color(255, 40, 40));
        g.setStroke(new BasicStroke(4));
        g.drawRect(2, 2, W - 4, H - 4);

        // Monster name text
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        g.setFont(F_HUD);
        g.setColor(new Color(255, 80, 80));
        String name = monster.getName() + " appears!";
        FontMetrics fm = g.getFontMetrics();
        g.drawString(name, cx - fm.stringWidth(name) / 2, sy - 10);

        g.setComposite(oldComp);
    }

    // ── Utilities ────────────────────────────────────────────────────────────

    /** Interpolates a color between bright (near) and dim (far) based on depth. */
    private static Color depthColor(Color near, Color far, int depth, int maxDepth) {
        if (maxDepth <= 0) return near;
        float t = Math.min(1f, (float) depth / maxDepth);
        return new Color(
                (int)(near.getRed()   + (far.getRed()   - near.getRed())   * t),
                (int)(near.getGreen() + (far.getGreen() - near.getGreen()) * t),
                (int)(near.getBlue()  + (far.getBlue()  - near.getBlue())  * t));
    }

    /** Returns a human-readable description of a dungeon special feature. */
    private static String specialDescription(char special) {
        return switch (special) {
            case 'A' -> "Altar";
            case 'f' -> "Fountain";
            case 's' -> "Stairway";
            case 'H' -> "Throne";
            case 't' -> "Teleporter";
            case 'P' -> "Pit";
            case 'I' -> "Inn";
            case 'B' -> "Treasure Chest";
            case 'R' -> "Puzzle";
            case 'n' -> "Spinner";
            case 'k' -> "Dark Zone";
            case 'c' -> "Chute";
            case 'q' -> "Riddle Door";
            case 'M' -> "Memory Pool";
            default -> null;
        };
    }
}

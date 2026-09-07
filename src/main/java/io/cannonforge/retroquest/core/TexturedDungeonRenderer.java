package io.cannonforge.retroquest.core;

import java.awt.*;
import java.util.Random;

import io.cannonforge.retroquest.model.Monster;
import io.cannonforge.retroquest.model.Player;
import io.cannonforge.retroquest.model.Dungeon.WallType;
import io.cannonforge.retroquest.registry.ImageAssetRegistry;

/**
 * First-person textured dungeon renderer in the style of the early grid-based crawlers.
 *
 * <p>Draws solid filled walls with procedural bark/root textures, visible floor and ceiling,
 * depth-based fog, and atmospheric spore particles. Used for Island 4 (Sylvandar) Rootvault dungeon.
 *
 * <p>Same coordinate conventions and wall query API as {@link WireframeDungeonRenderer}.
 */
public class TexturedDungeonRenderer {

    /**
     * Palette and surface style for the dungeon currently being drawn, resolved once per frame in
     * {@link #paint}. Static because every paint method here is static and painting only ever
     * happens on the EDT — the same reason the spore and monster-preview state below is static.
     */
    private static DungeonTheme T = DungeonTheme.ROOTVAULT;

    private static Font hudFont()   { return Fonts.monoBold(14); }
    private static Font labelFont() { return Fonts.mono(11); }

    private static final int MAX_DEPTH = DungeonWallPainter.MAX_DEPTH;
    private static final String[] FACING_NAMES = {"North", "East", "South", "West"};
    private static final int[] STEP_DX = { 0, 1, 0, -1};
    private static final int[] STEP_DY = {-1, 0, 1,  0};

    // ── Monster preview state ───────────────────────────────────────────────
    private static Monster previewMonster = null;
    private static long    previewStartMs = 0;
    private static final long PREVIEW_DURATION_MS = 400;

    // ── Spore particle system ───────────────────────────────────────────────
    private static final int NUM_SPORES = 40;
    private static final float[] sporeX = new float[NUM_SPORES];
    private static final float[] sporeY = new float[NUM_SPORES];
    private static final float[] sporeDX = new float[NUM_SPORES];
    private static final float[] sporeDY = new float[NUM_SPORES];
    private static final float[] sporeAlpha = new float[NUM_SPORES];
    private static boolean sporesInited = false;
    private static DungeonTheme.ParticleStyle particleTheme = null;

    static {
        generateTextures();
    }

    // ── Public API ──────────────────────────────────────────────────────────

    public static void showMonsterPreview(Monster m) {
        previewMonster = m;
        previewStartMs = System.currentTimeMillis();
    }

    public static boolean isPreviewActive() {
        return previewMonster != null && (System.currentTimeMillis() - previewStartMs) < PREVIEW_DURATION_MS;
    }

    public static void startStepAnimation(int fromX, int fromY, int toX, int toY) {
    }

    // ── Main paint method ───────────────────────────────────────────────────

    public static void paint(Graphics2D g2, int W, int H, Retroquest game) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        Player player = game.getPlayer();
        boolean authored = game.getDungeonViewState().isAuthored();
        char[][] map = game.getCurrentMap();
        int px  = authored ? player.getX() : player.getX() + 1;
        int py  = authored ? player.getY() : player.getY() + 1;
        int pz  = game.getCurrentDepth();
        int facing = player.getFacing();

        T = DungeonTheme.resolve(game.getDungeonViewState().getAuthoredDungeonName(),
                                 game.getCurrentOverworldName());

        char playerTile = DungeonWallQuery.getSpecial(px, py, pz, map, authored);
        boolean inDarkZone = (playerTile == 'k');

        // The HUD bar and its fonts were fixed pixel sizes, so at 2x UI scale they shrank against
        // everything else on screen. DisplayScale is what the rest of the game measures with.
        int hudH = DisplayScale.scaled(32);
        boolean showMinimap = game.getDungeonViewState().isMinimapVisible() && !inDarkZone;
        int minimapH = showMinimap ? Math.max(H * 2 / 5, 180) : 0;
        int corridorH = H - hudH - minimapH;

        // ── Background fog gradient ──
        for (int y = 0; y < corridorH; y++) {
            float t = (float) y / corridorH;
            // Ceiling to floor gradient: dark green overhead → slightly warmer at floor
            Color bg = blend(T.ceilFar, T.floorFar, t);
            g2.setColor(bg);
            g2.drawLine(0, y, W, y);
        }

        // ── 3D Textured corridor ──
        paintCorridor(g2, W, corridorH, px, py, pz, facing, map, authored);

        // ── Atmospheric spore particles ──
        if (!inDarkZone) {
            paintSpores(g2, W, corridorH);
        }

        // ── Monster preview flash ──
        if (previewMonster != null) {
            long elapsed = System.currentTimeMillis() - previewStartMs;
            if (elapsed < PREVIEW_DURATION_MS) {
                paintMonsterPreview(g2, W, corridorH, previewMonster, elapsed);
            } else {
                previewMonster = null;
            }
        }

        // ── Depth fog overlay (subtle vignette) ──
        paintFogOverlay(g2, W, corridorH);

        // ── HUD bar ──
        paintHUD(g2, 0, corridorH, W, hudH, px, py, pz, facing, map, authored, inDarkZone);

        // ── Minimap ──
        if (showMinimap) {
            paintMinimap(g2, 0, corridorH + hudH, W, minimapH, px, py, pz, facing, game, map, authored);
        }
    }

    // ── Corridor rendering ──────────────────────────────────────────────────

    private static void paintCorridor(Graphics2D g, int W, int H, int px, int py, int pz, int facing,
                                       char[][] map, boolean authored) {
        // Depth-slice boundaries. Derived from the shared projection rather than recomputed, so
        // the masonry laid out in world space lands on exactly these pixel boundaries.
        DungeonWallPainter.Corridor c = new DungeonWallPainter.Corridor(W, H);
        int[] left   = new int[MAX_DEPTH + 2];
        int[] right  = new int[MAX_DEPTH + 2];
        int[] top    = new int[MAX_DEPTH + 2];
        int[] bottom = new int[MAX_DEPTH + 2];

        for (int d = 0; d <= MAX_DEPTH + 1; d++) {
            left[d]   = c.leftI(d);
            right[d]  = c.rightI(d);
            top[d]    = c.topI(d);
            bottom[d] = c.bottomI(d);
        }

        // Rootvault is grown, not built: it keeps the original bark/root surfaces. Every other
        // textured dungeon is masonry and goes through DungeonWallPainter.
        boolean stone = T.wallStyle != DungeonTheme.WallStyle.BARK;
        long now = System.currentTimeMillis();

        int frontWallDepth = MAX_DEPTH;

        // ── Draw floor and ceiling first (back to front) ──
        for (int d = MAX_DEPTH - 1; d >= 0; d--) {
            float fogT = (float) d / MAX_DEPTH;

            if (stone) {
                int fcX = px + STEP_DX[facing] * d;
                int fcY = py + STEP_DY[facing] * d;
                DungeonWallPainter.floorBand(g, c, d, T, fcX, fcY);
                DungeonWallPainter.ceilingBand(g, c, d, T, fcX, fcY);
                continue;
            }

            // Floor strip between depth d+1 and d
            Color floorCol = blend(T.floorNear, T.floorFar, fogT);
            int[] fx = {left[d], right[d], right[d+1], left[d+1]};
            int[] fy = {bottom[d], bottom[d], bottom[d+1], bottom[d+1]};
            g.setColor(floorCol);
            g.fillPolygon(fx, fy, 4);
            // Root grain lines on floor
            Color rootCol = blend(T.floorDetail, T.floorFar, fogT);
            g.setColor(rootCol);
            int floorMidY = (bottom[d] + bottom[d+1]) / 2;
            g.drawLine(left[d+1], floorMidY, right[d+1], floorMidY);
            // Gap lines (darker cracks between roots)
            Color gapCol = blend(T.floorGap, T.floorFar, fogT * 0.8f);
            g.setColor(gapCol);
            int thirdW = (right[d+1] - left[d+1]) / 3;
            g.drawLine(left[d+1] + thirdW, bottom[d+1], left[d] + (right[d]-left[d])/3, bottom[d]);
            g.drawLine(left[d+1] + thirdW*2, bottom[d+1], left[d] + (right[d]-left[d])*2/3, bottom[d]);

            // Ceiling strip between depth d+1 and d
            Color ceilCol = blend(T.ceilNear, T.ceilFar, fogT);
            int[] ccx = {left[d], right[d], right[d+1], left[d+1]};
            int[] ccy = {top[d], top[d], top[d+1], top[d+1]};
            g.setColor(ceilCol);
            g.fillPolygon(ccx, ccy, 4);
            // Hanging vine detail
            Color vineCol = blend(T.ceilDetail, T.ceilFar, fogT);
            g.setColor(vineCol);
            int vineSpacing = Math.max(8, (right[d+1] - left[d+1]) / 5);
            for (int vx = left[d+1] + vineSpacing/2; vx < right[d+1]; vx += vineSpacing) {
                int vineLen = 3 + (vx * 7 + d * 13) % 6; // pseudo-random length
                g.drawLine(vx, top[d+1], vx + (d % 2 == 0 ? 1 : -1), top[d+1] + vineLen);
            }
        }

        // ── Draw walls back to front ──
        for (int d = MAX_DEPTH - 1; d >= 0; d--) {
            // Band d spans planes d..d+1 and shows cell C(d) = player + facing*d,
            // so d == 0 is the player's OWN cell and the front wall drawn at plane
            // d+1 is the wall between C(d) and C(d+1) — the same wall canMove() tests.
            int cellX = px + STEP_DX[facing] * d;
            int cellY = py + STEP_DY[facing] * d;

            float fogT = (float) d / MAX_DEPTH;
            Color wallCol = blend(T.wallNear, T.wallFar, fogT);
            Color doorCol = blend(T.doorNear, T.doorFar, fogT);

            // Check bounds — seal the view with a solid end wall at plane d (the near
            // face of the missing cell) and keep going: the loop runs far->near, so the
            // nearer slices still have to be painted on top of it.
            int mapW = DungeonWallQuery.getMapWidth(map, authored);
            int mapH = DungeonWallQuery.getMapHeight(map, authored);
            int minCoord = authored ? 0 : 1;
            if (cellX < minCoord || cellX >= (authored ? mapW : mapW + 1)
             || cellY < minCoord || cellY >= (authored ? mapH : mapH + 1)) {
                if (stone) {
                    DungeonWallPainter.frontWall(g, left[d], top[d], right[d], bottom[d],
                                                 T, fogT, cellX, cellY, now);
                } else {
                    drawFrontWall(g, left[d], top[d], right[d], bottom[d],
                                  WallType.WALL, wallCol, doorCol, fogT);
                }
                frontWallDepth = Math.min(frontWallDepth, d - 1); // last visible band is d-1
                continue;
            }

            WallType leftWall  = DungeonWallQuery.getRelativeWall(cellX, cellY, pz, facing, -1, map, authored);
            WallType rightWall = DungeonWallQuery.getRelativeWall(cellX, cellY, pz, facing,  1, map, authored);
            WallType frontWall = DungeonWallQuery.getFrontWall(cellX, cellY, pz, facing, map, authored);

            // ── LEFT SIDE ──
            if (stone) {
                sideSurface(g, c, d, true, leftWall, cellX, cellY, now);
            } else if (leftWall == WallType.WALL) {
                drawSideWall(g, left[d], top[d], bottom[d], left[d+1], top[d+1], bottom[d+1], wallCol, fogT, true);
            } else if (leftWall == WallType.DOOR) {
                drawSideDoor(g, left[d], top[d], bottom[d], left[d+1], top[d+1], bottom[d+1], doorCol, fogT);
            } else {
                drawSideOpening(g, left[d], top[d], bottom[d], left[d+1], top[d+1], bottom[d+1], fogT, true);
            }

            // ── RIGHT SIDE ──
            if (stone) {
                sideSurface(g, c, d, false, rightWall, cellX, cellY, now);
            } else if (rightWall == WallType.WALL) {
                drawSideWall(g, right[d], top[d], bottom[d], right[d+1], top[d+1], bottom[d+1], wallCol, fogT, false);
            } else if (rightWall == WallType.DOOR) {
                drawSideDoor(g, right[d], top[d], bottom[d], right[d+1], top[d+1], bottom[d+1], doorCol, fogT);
            } else {
                drawSideOpening(g, right[d], top[d], bottom[d], right[d+1], top[d+1], bottom[d+1], fogT, false);
            }

            // ── FRONT WALL ──
            if (frontWall == WallType.WALL || frontWall == WallType.DOOR) {
                if (stone && frontWall == WallType.WALL) {
                    DungeonWallPainter.frontWall(g, left[d+1], top[d+1], right[d+1], bottom[d+1],
                                                 T, fogT, cellX, cellY, now);
                } else if (stone) {
                    DungeonWallPainter.frontDoor(g, left[d+1], top[d+1], right[d+1], bottom[d+1],
                                                 T, fogT, cellX, cellY);
                } else {
                    drawFrontWall(g, left[d+1], top[d+1], right[d+1], bottom[d+1],
                                  frontWall, wallCol, doorCol, fogT);
                }
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

        // ── Edge highlights (subtle ambient light on corridor edges) ──
        // Stone corridors get their corners from the painter's contact shadows and flagstone
        // lips instead; this amber line is a Rootvault detail.
        int edgeLimit = stone ? 0 : Math.min(MAX_DEPTH, frontWallDepth + 1);
        for (int d = 0; d < edgeLimit; d++) {
            float fogT = (float) d / MAX_DEPTH;
            Color edgeCol = blend(new Color(100, 75, 40, 60), new Color(20, 15, 8, 20), fogT);
            g.setColor(edgeCol);
            g.setStroke(new BasicStroke(1));
            // Floor edge lines
            g.drawLine(left[d+1], bottom[d+1], right[d+1], bottom[d+1]);
            // Ceiling edge lines
            g.drawLine(left[d+1], top[d+1], right[d+1], top[d+1]);
        }
    }

    /**
     * Lets {@link RaycastDungeonRenderer} reuse this renderer's HUD bar rather than grow a second
     * copy of it. {@link #T} is this class's per-frame theme slot; the raycaster paints its
     * corridor first and borrows the bar last, so the two never interleave — but the previous
     * value is restored anyway so a partially painted frame can never leave the wrong theme set.
     */
    static void paintHudFor(Graphics2D g, int x, int y, int w, int h, DungeonTheme theme,
                            int px, int py, int pz, int facing,
                            char[][] map, boolean authored, boolean inDarkZone) {
        DungeonTheme prev = T;
        T = theme;
        try {
            paintHUD(g, x, y, w, h, px, py, pz, facing, map, authored, inDarkZone);
        } finally {
            T = prev;
        }
    }

    /** Companion to {@link #paintHudFor} for the minimap panel. */
    static void paintMinimapFor(Graphics2D g, int x0, int y0, int w, int h, DungeonTheme theme,
                                int px, int py, int pz, int facing, Retroquest game,
                                char[][] map, boolean authored) {
        DungeonTheme prev = T;
        T = theme;
        try {
            paintMinimap(g, x0, y0, w, h, px, py, pz, facing, game, map, authored);
        } finally {
            T = prev;
        }
    }

    /** Dispatches one side of one depth band to the stone painter. */
    private static void sideSurface(Graphics2D g, DungeonWallPainter.Corridor c, int band,
                                    boolean leftSide, WallType type, int cellX, int cellY, long now) {
        if (type == WallType.WALL) {
            DungeonWallPainter.sideWall(g, c, band, leftSide, T, cellX, cellY, now);
        } else if (type == WallType.DOOR) {
            DungeonWallPainter.sideDoor(g, c, band, leftSide, T, cellX, cellY, now);
        } else {
            DungeonWallPainter.sideOpening(g, c, band, leftSide, T);
        }
    }

    // ── Wall drawing helpers ────────────────────────────────────────────────

    private static void drawSideWall(Graphics2D g, int nearX, int nearTop, int nearBot,
                                      int farX, int farTop, int farBot, Color wallCol,
                                      float fogT, boolean isLeft) {
        int[] xPts = {nearX, farX, farX, nearX};
        int[] yPts = {nearTop, farTop, farBot, nearBot};

        // Solid fill — slightly darker than face color for depth
        Color fill = darken(wallCol, 0.4f);
        g.setColor(fill);
        g.fillPolygon(xPts, yPts, 4);

        // Bark grain texture — vertical lines with slight wobble
        Color grain = blend(T.wallGrain, T.wallFar, fogT);
        g.setColor(grain);
        int panels = Math.max(2, Math.abs(nearX - farX) / 12);
        for (int i = 1; i < panels; i++) {
            float t = (float) i / panels;
            int x = nearX + (int)((farX - nearX) * t);
            int yt = nearTop + (int)((farTop - nearTop) * t);
            int yb = nearBot + (int)((farBot - nearBot) * t);
            g.drawLine(x, yt, x, yb);
        }

        // Horizontal mortar / bark ridge lines
        Color ridge = blend(T.wallHigh, T.wallFar, fogT);
        g.setColor(new Color(ridge.getRed(), ridge.getGreen(), ridge.getBlue(), 50));
        int thirds = (nearBot - nearTop) / 3;
        for (int row = 1; row < 3; row++) {
            int ny = nearTop + thirds * row;
            int fy = farTop + (farBot - farTop) * row / 3;
            g.drawLine(nearX, ny, farX, fy);
        }

        // Moss patches (green highlights on upper portion)
        if (fogT < 0.6f) {
            Color moss = blend(T.wallMoss, T.wallFar, fogT);
            g.setColor(new Color(moss.getRed(), moss.getGreen(), moss.getBlue(), 40));
            int mossY = nearTop + (nearBot - nearTop) / 5;
            int fmossY = farTop + (farBot - farTop) / 5;
            int mossH = (nearBot - nearTop) / 6;
            int fmossH = (farBot - farTop) / 6;
            g.fillPolygon(
                new int[]{nearX, farX, farX, nearX},
                new int[]{mossY, fmossY, fmossY + fmossH, mossY + mossH}, 4);
        }

        // Edge highlight (nearest edge of wall)
        Color edgeCol = blend(T.wallHigh, T.wallFar, fogT);
        g.setColor(new Color(edgeCol.getRed(), edgeCol.getGreen(), edgeCol.getBlue(), 80));
        g.setStroke(new BasicStroke(1.5f));
        g.drawLine(nearX, nearTop, nearX, nearBot);
    }

    private static void drawSideDoor(Graphics2D g, int nearX, int nearTop, int nearBot,
                                      int farX, int farTop, int farBot, Color doorCol, float fogT) {
        int[] xPts = {nearX, farX, farX, nearX};
        int[] yPts = {nearTop, farTop, farBot, nearBot};

        // Wall fill around door
        Color fill = darken(doorCol, 0.25f);
        g.setColor(fill);
        g.fillPolygon(xPts, yPts, 4);

        // Door frame inset
        int nearDoorTop = nearTop + (nearBot - nearTop) / 6;
        int nearDoorBot = nearBot - (nearBot - nearTop) / 10;
        int farDoorTop  = farTop + (farBot - farTop) / 6;
        int farDoorBot  = farBot - (farBot - farTop) / 10;

        // Door panel
        Color panelCol = blend(T.doorPanel, T.wallFar, fogT);
        g.setColor(panelCol);
        g.fillPolygon(
            new int[]{nearX, farX, farX, nearX},
            new int[]{nearDoorTop, farDoorTop, farDoorBot, nearDoorBot}, 4);

        // Door frame trim
        Color frameCol = blend(T.doorFrame, T.doorFar, fogT);
        g.setColor(frameCol);
        g.setStroke(new BasicStroke(2));
        g.drawLine(nearX, nearDoorTop, farX, farDoorTop);
        g.drawLine(nearX, nearDoorBot, farX, farDoorBot);
    }

    private static void drawSideOpening(Graphics2D g, int nearX, int nearTop, int nearBot,
                                          int farX, int farTop, int farBot, float fogT, boolean isLeft) {
        int recessTop = nearTop + (farTop - nearTop) / 2;
        int recessBot = nearBot + (farBot - nearBot) / 2;

        // Dark void inside passage
        g.setColor(T.fog);
        g.fillPolygon(
            new int[]{nearX, nearX, farX, farX},
            new int[]{recessTop, recessBot, farBot, farTop}, 4);

        // Edge trim where wall meets opening
        Color edgeCol = blend(T.wallHigh, T.wallFar, fogT);
        g.setColor(new Color(edgeCol.getRed(), edgeCol.getGreen(), edgeCol.getBlue(), 90));
        g.setStroke(new BasicStroke(1.5f));
        g.drawLine(nearX, nearTop, nearX, recessTop);
        g.drawLine(nearX, nearBot, nearX, recessBot);
        // Depth edge
        Color dimEdge = new Color(edgeCol.getRed(), edgeCol.getGreen(), edgeCol.getBlue(), 30);
        g.setColor(dimEdge);
        g.drawLine(nearX, recessTop, farX, farTop);
        g.drawLine(nearX, recessBot, farX, farBot);
    }

    private static void drawFrontWall(Graphics2D g, int l, int t, int r, int b,
                                       WallType type, Color wallCol, Color doorCol, float fogT) {
        int fw = r - l, fh = b - t;

        if (type == WallType.WALL) {
            // Solid wall fill with gradient (lighter at top for ambient light)
            Color fillTop = darken(wallCol, 0.35f);
            Color fillBot = darken(wallCol, 0.55f);
            GradientPaint gp = new GradientPaint(l, t, fillTop, l, b, fillBot);
            g.setPaint(gp);
            g.fillRect(l, t, fw, fh);
            g.setPaint(null);

            // Bark texture grid
            Color grain = blend(T.wallGrain, T.wallFar, fogT);
            g.setColor(new Color(grain.getRed(), grain.getGreen(), grain.getBlue(), 55));
            // Vertical bark lines
            int spacing = Math.max(8, fw / 6);
            for (int x = l + spacing; x < r; x += spacing) {
                g.drawLine(x, t, x, b);
            }
            // Horizontal ridges
            int hSpacing = Math.max(8, fh / 4);
            for (int y = t + hSpacing; y < b; y += hSpacing) {
                g.drawLine(l, y, r, y);
            }

            // Moss patches on front wall
            if (fogT < 0.7f) {
                Color moss = blend(T.wallMoss, T.wallFar, fogT);
                g.setColor(new Color(moss.getRed(), moss.getGreen(), moss.getBlue(), 35));
                // Upper left patch
                g.fillRect(l + fw/8, t + fh/8, fw/4, fh/5);
                // Lower right patch
                g.fillRect(l + fw*5/8, t + fh*3/5, fw/4, fh/5);
            }

            // Amber glow spot (occasional)
            if (fogT < 0.5f && ((l + t) % 7 < 2)) {
                g.setColor(T.particleC);
                int glowX = l + fw / 2 - 8;
                int glowY = t + fh / 2 - 8;
                g.fillOval(glowX, glowY, 16, 16);
            }

            // Edge border (subtle)
            Color edge = blend(T.wallHigh, T.wallFar, fogT);
            g.setColor(new Color(edge.getRed(), edge.getGreen(), edge.getBlue(), 70));
            g.setStroke(new BasicStroke(1.5f));
            g.drawRect(l, t, fw, fh);

        } else if (type == WallType.DOOR) {
            // Wall around door
            Color fillBg = darken(wallCol, 0.45f);
            g.setColor(fillBg);
            g.fillRect(l, t, fw, fh);

            // Door panel (inset)
            int inset = Math.max(4, fw / 6);
            int doorL = l + inset, doorR = r - inset;
            int doorT = t + inset, doorB = b - inset / 2;

            // Door fill with wood grain
            Color doorFill = blend(T.doorPanel, T.wallFar, fogT);
            GradientPaint dgp = new GradientPaint(doorL, doorT, doorFill,
                doorL, doorB, darken(doorFill, 0.6f));
            g.setPaint(dgp);
            g.fillRect(doorL, doorT, doorR - doorL, doorB - doorT);
            g.setPaint(null);

            // Wood grain on door
            Color grainD = blend(new Color(85, 60, 30), T.wallFar, fogT);
            g.setColor(new Color(grainD.getRed(), grainD.getGreen(), grainD.getBlue(), 40));
            int dw = doorR - doorL;
            for (int x = doorL + dw/5; x < doorR; x += dw/5) {
                g.drawLine(x, doorT, x, doorB);
            }

            // Door frame
            Color frameCol = blend(T.doorFrame, T.doorFar, fogT);
            g.setColor(frameCol);
            g.setStroke(new BasicStroke(2.5f));
            g.drawRect(doorL, doorT, doorR - doorL, doorB - doorT);

            // Door arch
            g.drawArc(doorL, doorT - inset, doorR - doorL, inset * 2, 0, 180);

            // Door ring/handle
            int knobX = doorL + (doorR - doorL) * 3 / 4;
            int knobY = (doorT + doorB) / 2;
            g.setColor(new Color(180, 140, 50));
            g.fillOval(knobX - 3, knobY - 3, 7, 7);
            g.setColor(new Color(220, 180, 70));
            g.drawOval(knobX - 3, knobY - 3, 7, 7);

            // Outer border
            Color edge = blend(T.wallHigh, T.wallFar, fogT);
            g.setColor(new Color(edge.getRed(), edge.getGreen(), edge.getBlue(), 50));
            g.setStroke(new BasicStroke(1));
            g.drawRect(l, t, fw, fh);
        }
    }

    // ── Special feature icons ───────────────────────────────────────────────

    private static void drawSpecialIcon(Graphics2D g, int l, int r, int bot, int top,
                                         char special, int depth) {
        float fogT = (float) depth / MAX_DEPTH;
        Color col = blend(T.specialNear, T.specialFar, fogT);
        Color glowCol = new Color(col.getRed(), col.getGreen(), col.getBlue(), 50);

        int cx = (l + r) / 2;
        int floorY = bot - (bot - top) / 8;
        int sz = Math.max(5, (r - l) / 6);

        g.setStroke(new BasicStroke(Math.max(1.5f, 2.5f - depth * 0.5f)));

        // Glow halo behind icon
        g.setColor(glowCol);
        g.fillOval(cx - sz * 2, floorY - sz * 2, sz * 4, sz * 3);

        g.setColor(col);
        switch (special) {
            case 'A' -> { // Altar — diamond with glow
                g.drawLine(cx, floorY - sz, cx + sz, floorY);
                g.drawLine(cx + sz, floorY, cx, floorY + sz);
                g.drawLine(cx, floorY + sz, cx - sz, floorY);
                g.drawLine(cx - sz, floorY, cx, floorY - sz);
                g.setColor(T.particleC);
                g.fillOval(cx - sz/2, floorY - sz/2, sz, sz);
            }
            case 'f' -> { // Fountain
                g.drawOval(cx - sz, floorY - sz/2, sz*2, sz);
                g.drawLine(cx, floorY - sz, cx, floorY - sz * 2);
                g.setColor(new Color(40, 150, 120, 80));
                g.fillOval(cx - sz + 2, floorY - sz/2 + 2, sz*2 - 4, sz - 4);
            }
            case 's' -> { // Stairs
                for (int i = 0; i < 4; i++) {
                    int sy = floorY - i * sz / 2;
                    int sw = sz + 2 - i * sz / 4;
                    g.drawLine(cx - sw, sy, cx + sw, sy);
                    if (i < 3) g.drawLine(cx - sw, sy, cx - sw, sy - sz/2);
                }
            }
            case 'H' -> { // Throne
                g.drawRect(cx - sz, floorY - sz, sz * 2, sz);
                g.drawLine(cx - sz, floorY - sz, cx - sz, floorY - sz * 2);
                g.drawLine(cx + sz, floorY - sz, cx + sz, floorY - sz * 2);
                g.drawLine(cx - sz, floorY - sz * 2, cx + sz, floorY - sz * 2);
            }
            case 't' -> { // Teleporter
                g.drawOval(cx - sz, floorY - sz/2, sz*2, sz);
                g.drawOval(cx - sz/2, floorY - sz/4, sz, sz/2);
                g.setColor(new Color(col.getRed(), col.getGreen(), col.getBlue(), 30));
                g.fillOval(cx - sz, floorY - sz/2, sz*2, sz);
            }
            case 'P' -> { // Pit
                g.setColor(new Color(15, 8, 4));
                g.fillOval(cx - sz, floorY - sz/3, sz*2, sz);
                g.setColor(col);
                g.drawOval(cx - sz, floorY - sz/3, sz*2, sz);
            }
            case 'n' -> { // Spinner
                g.drawOval(cx - sz, floorY - sz, sz * 2, sz * 2);
                g.drawLine(cx, floorY - sz, cx + sz/3, floorY - sz + sz/3);
                g.drawLine(cx + sz, floorY, cx + sz - sz/3, floorY - sz/3);
            }
            case 'k' -> { // Dark zone — eye with slash
                g.drawOval(cx - sz, floorY - sz/2, sz * 2, sz);
                g.fillOval(cx - sz/3, floorY - sz/4, sz*2/3, sz/2);
                g.drawLine(cx - sz, floorY + sz/2, cx + sz, floorY - sz);
            }
            case 'c' -> { // Chute
                g.setColor(new Color(15, 8, 4));
                g.fillOval(cx - sz, floorY - sz/3, sz*2, sz);
                g.setColor(col);
                g.drawOval(cx - sz, floorY - sz/3, sz*2, sz);
                g.drawLine(cx, floorY + sz/2, cx, floorY + sz * 2);
                g.drawLine(cx - sz/2, floorY + sz + sz/2, cx, floorY + sz * 2);
                g.drawLine(cx + sz/2, floorY + sz + sz/2, cx, floorY + sz * 2);
            }
            case 'q' -> { // Riddle door
                g.drawRect(cx - sz, floorY - sz * 2, sz * 2, sz * 3);
                g.setFont(hudFont());
                g.drawString("?", cx - 4, floorY);
            }
            case 'I' -> { // Inn
                g.drawRect(cx - sz, floorY - sz/2, sz * 2, sz);
                g.drawLine(cx - sz, floorY - sz, cx - sz + sz/3, floorY - sz/2);
            }
            case 'B' -> { // Treasure chest
                g.drawRect(cx - sz, floorY - sz/2, sz * 2, sz);
                g.drawLine(cx - sz, floorY - sz/2, cx - sz + sz/3, floorY - sz);
                g.drawLine(cx + sz, floorY - sz/2, cx + sz - sz/3, floorY - sz);
                g.drawLine(cx - sz + sz/3, floorY - sz, cx + sz - sz/3, floorY - sz);
                g.setColor(T.particleC);
                g.fillRect(cx - 2, floorY - sz/2 - 2, 4, 4);
            }
            case 'R' -> { // Puzzle — angular rune glyph. The wireframe renderer has always drawn
                          // this one; the textured renderer fell through to a bare "?".
                g.drawRect(cx - sz, floorY - sz * 2, sz * 2, sz * 2);
                g.drawLine(cx - sz/2, floorY - sz*2 + sz/3, cx + sz/2, floorY - sz/3);
                g.drawLine(cx + sz/2, floorY - sz*2 + sz/3, cx - sz/2, floorY - sz/3);
                g.drawLine(cx, floorY - sz*2 + sz/3, cx, floorY - sz/3);
            }
            default -> {
                g.setFont(labelFont());
                g.drawString("?", cx - 3, floorY);
            }
        }
    }

    // ── Atmospheric Effects ──────────────────────────────────────────────────

    private static void paintSpores(Graphics2D g, int W, int H) {
        int count = Math.min(NUM_SPORES, T.particleCount);

        // Re-seed when the dungeon changes, so bubbles do not inherit spore velocities.
        if (!sporesInited || particleTheme != T.particleStyle) {
            Random r = new Random(77);
            for (int i = 0; i < NUM_SPORES; i++) {
                sporeX[i] = r.nextFloat() * W;
                sporeY[i] = r.nextFloat() * H;
                switch (T.particleStyle) {
                    case BUBBLE -> {
                        sporeDX[i] = (r.nextFloat() - 0.5f) * 0.35f;
                        sporeDY[i] = -0.45f - r.nextFloat() * 0.85f;   // rise
                    }
                    case SILT -> {
                        sporeDX[i] = (r.nextFloat() - 0.5f) * 0.5f;
                        sporeDY[i] = 0.12f + r.nextFloat() * 0.30f;    // settle
                    }
                    default -> {
                        sporeDX[i] = (r.nextFloat() - 0.5f) * 0.8f;
                        sporeDY[i] = (r.nextFloat() - 0.5f) * 0.4f - 0.15f;
                    }
                }
                sporeAlpha[i] = 0.3f + r.nextFloat() * 0.7f;
            }
            sporesInited = true;
            particleTheme = T.particleStyle;
        }

        long now = System.currentTimeMillis();
        float time = (now % 10000) / 10000f;
        float wobble = T.particleStyle == DungeonTheme.ParticleStyle.BUBBLE ? 0.5f : 0.3f;

        for (int i = 0; i < count; i++) {
            // Drift motion
            sporeX[i] += sporeDX[i] + wobble * (float)Math.sin(time * Math.PI * 2 + i);
            sporeY[i] += sporeDY[i] + 0.2f * (float)Math.cos(time * Math.PI * 2 + i * 0.7f);

            // Wrap around
            if (sporeX[i] < 0) sporeX[i] += W;
            if (sporeX[i] >= W) sporeX[i] -= W;
            if (sporeY[i] < 0) sporeY[i] += H;
            if (sporeY[i] >= H) sporeY[i] -= H;

            // Pulsing alpha
            float pulse = 0.5f + 0.5f * (float)Math.sin(time * Math.PI * 4 + i * 1.3f);
            float alpha = sporeAlpha[i] * pulse;

            // Alternate between green spore and amber mote
            Color c = (i % 3 == 0) ? T.particleA : (i % 3 == 1) ? T.particleB : T.particleC;
            g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(),
                    (int)(alpha * c.getAlpha())));

            int sz = 1 + (i % 3);
            g.fillOval((int)sporeX[i], (int)sporeY[i], sz, sz);

            // Larger glow halo for some
            if (i % 5 == 0) {
                g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(),
                        (int)(alpha * 15)));
                g.fillOval((int)sporeX[i] - 3, (int)sporeY[i] - 3, sz + 6, sz + 6);
            }
        }
    }

    private static void paintFogOverlay(Graphics2D g, int W, int H) {
        // Vignette — darken edges for enclosed feeling
        Composite old = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.4f));
        g.setColor(T.fog);
        int cr = Math.min(W, H) / 3;
        g.fillOval(-cr/2, -cr/2, cr, cr);
        g.fillOval(W - cr/2, -cr/2, cr, cr);
        g.fillOval(-cr/2, H - cr/2, cr, cr);
        g.fillOval(W - cr/2, H - cr/2, cr, cr);

        // Top/bottom fog bands
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.2f));
        g.fillRect(0, 0, W, H / 8);
        g.fillRect(0, H - H / 8, W, H / 8);
        g.setComposite(old);
    }

    // ── Monster Preview ─────────────────────────────────────────────────────

    private static void paintMonsterPreview(Graphics2D g, int W, int H, Monster monster, long elapsedMs) {
        float alpha;
        if (elapsedMs < 100) alpha = elapsedMs / 100f;
        else if (elapsedMs < 250) alpha = 1.0f;
        else alpha = 1.0f - (elapsedMs - 250f) / 150f;
        alpha = Math.max(0f, Math.min(1f, alpha));

        String spriteKey = "monsters/" + monster.getImageFileName().replace(".png", "");
        Image monImg = ImageAssetRegistry.get(spriteKey);
        if (monImg == null) monImg = ImageAssetRegistry.get("monsters/unknown");

        int cx = W / 2, cy = H / 2;
        float scale = 0.35f;
        int spriteW = (int)(W * scale), spriteH = (int)(H * scale);
        int sx = cx - spriteW / 2, sy = cy - spriteH / 2 + 20;

        Composite oldComp = g.getComposite();

        if (monImg != null) {
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            Object oldInterp = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.drawImage(monImg, sx, sy, spriteW, spriteH, null);
            if (oldInterp != null) g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, oldInterp);
        }

        // Green warning flash (not red — forest theme)
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha * 0.25f));
        g.setColor(new Color(40, 200, 80));
        g.setStroke(new BasicStroke(4));
        g.drawRect(2, 2, W - 4, H - 4);

        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        g.setFont(hudFont());
        g.setColor(new Color(80, 220, 100));
        String name = monster.getName() + " appears!";
        FontMetrics fm = g.getFontMetrics();
        g.drawString(name, cx - fm.stringWidth(name) / 2, sy - 10);

        g.setComposite(oldComp);
    }

    // ── HUD ─────────────────────────────────────────────────────────────────

    private static void paintHUD(Graphics2D g, int x, int y, int w, int h,
                                  int px, int py, int pz, int facing,
                                  char[][] map, boolean authored, boolean inDarkZone) {
        g.setColor(T.hudBg);
        g.fillRect(x, y, w, h);
        g.setColor(new Color(80, 60, 25, 100));
        g.drawLine(x, y, x + w, y);

        g.setFont(hudFont());
        FontMetrics fm = g.getFontMetrics();

        String info = inDarkZone
                ? "Level " + pz + " \u00b7 Facing ???"
                : "Level " + pz + " \u00b7 Facing " + FACING_NAMES[facing];
        g.setColor(inDarkZone ? T.hudDim : T.hudText);
        g.drawString(info, x + 12, y + h / 2 + fm.getAscent() / 2 - 2);

        char special = DungeonWallQuery.getSpecial(px, py, pz, map, authored);
        String tileDesc = inDarkZone ? "Darkness..." : specialDescription(special);
        if (tileDesc != null) {
            g.setColor(inDarkZone ? new Color(60, 80, 40) : T.specialNear);
            g.drawString("\u00b7 " + tileDesc, x + 12 + fm.stringWidth(info) + 16,
                         y + h / 2 + fm.getAscent() / 2 - 2);
        }

        // Compass
        if (!inDarkZone) {
            int compassX = x + w - 60;
            int compassY = y + h / 2;
            int cr = 10;
            g.setColor(T.hudDim);
            g.drawOval(compassX - cr, compassY - cr, cr * 2, cr * 2);
            g.setFont(labelFont());
            g.setColor(facing == 0 ? T.hudText : T.hudDim); g.drawString("N", compassX - 3, compassY - cr - 2);
            g.setColor(facing == 2 ? T.hudText : T.hudDim); g.drawString("S", compassX - 3, compassY + cr + 11);
            g.setColor(facing == 1 ? T.hudText : T.hudDim); g.drawString("E", compassX + cr + 3, compassY + 4);
            g.setColor(facing == 3 ? T.hudText : T.hudDim); g.drawString("W", compassX - cr - 12, compassY + 4);
            g.setColor(T.hudText);
            int arrowX = compassX + STEP_DX[facing] * (cr - 2);
            int arrowY = compassY + STEP_DY[facing] * (cr - 2);
            g.fillOval(arrowX - 3, arrowY - 3, 6, 6);
        }
    }

    // ── Minimap ─────────────────────────────────────────────────────────────

    private static void paintMinimap(Graphics2D g, int x0, int y0, int w, int h,
                                      int px, int py, int pz, int facing, Retroquest game,
                                      char[][] map, boolean authored) {
        g.setColor(T.mmBg);
        g.fillRect(x0, y0, w, h);
        g.setColor(new Color(60, 45, 20, 100));
        g.drawRect(x0, y0, w - 1, h - 1);

        g.setColor(T.hudDim);
        g.setFont(labelFont());
        g.drawString("MAP", x0 + 6, y0 + 14);

        int margin = 8;
        int availW = w - margin * 2;
        int availH = h - 22 - margin;
        int targetTiles = 35;
        int tileSize = Math.max(3, Math.min(availW / targetTiles, availH / targetTiles));
        int tilesW = availW / tileSize;
        int tilesH = availH / tileSize;
        int mapX0 = x0 + margin + (availW - tilesW * tileSize) / 2;
        int mapY0 = y0 + 20 + (availH - tilesH * tileSize) / 2;

        int startX = px - tilesW / 2;
        int startY = py - tilesH / 2;
        boolean[][] revealed = game.getRevealedForLevel(pz);

        for (int ty = 0; ty < tilesH; ty++) {
            for (int tx = 0; tx < tilesW; tx++) {
                int wx = startX + tx, wy = startY + ty;
                int sx = mapX0 + tx * tileSize, sy = mapY0 + ty * tileSize;

                int rx = authored ? wx : wx - 1;
                int ry = authored ? wy : wy - 1;
                boolean vis = (rx >= 0 && ry >= 0 && revealed != null
                        && ry < revealed.length && rx < revealed[0].length && revealed[ry][rx]);

                if (!vis && authored && rx >= 0 && ry >= 0 && revealed != null
                        && ry < revealed.length && rx < revealed[0].length
                        && wx >= 0 && wx < map[0].length && wy >= 0 && wy < map.length
                        && !io.cannonforge.retroquest.registry.TileRegistry.getByIdSafe(map[wy][wx]).isWalkable()) {
                    for (int[] d : new int[][]{{-1,0},{1,0},{0,-1},{0,1}}) {
                        int nx = rx + d[0], ny = ry + d[1];
                        if (nx >= 0 && ny >= 0 && ny < revealed.length && nx < revealed[0].length && revealed[ny][nx]) {
                            vis = true; break;
                        }
                    }
                }

                int mw = DungeonWallQuery.getMapWidth(map, authored);
                int mh = DungeonWallQuery.getMapHeight(map, authored);
                int lo = authored ? 0 : 1;
                if (!vis || wx < lo || wx >= (authored ? mw : mw + 1)
                         || wy < lo || wy >= (authored ? mh : mh + 1)) {
                    g.setColor(T.mmBg);
                    g.fillRect(sx, sy, tileSize, tileSize);
                    continue;
                }

                if (authored && wy >= 0 && wy < map.length && wx >= 0 && wx < map[0].length) {
                    char tile = map[wy][wx];
                    if (!io.cannonforge.retroquest.registry.TileRegistry.getByIdSafe(tile).isWalkable()) {
                        g.setColor(T.mmWall);
                        g.fillRect(sx, sy, tileSize, tileSize);
                        continue;
                    }
                    if (tile == 'd' || tile == '|' || tile == '[' || tile == '{') {
                        g.setColor(T.mmDoor);
                        g.fillRect(sx, sy, tileSize, tileSize);
                        continue;
                    }
                }

                g.setColor(T.mmFog);
                g.fillRect(sx, sy, tileSize, tileSize);

                if (!authored) {
                    WallType n = DungeonWallQuery.getWall(wx, wy, pz, 0, map, authored);
                    WallType w2 = DungeonWallQuery.getWall(wx, wy, pz, 3, map, authored);
                    WallType s = DungeonWallQuery.getWall(wx, wy, pz, 2, map, authored);
                    WallType e2 = DungeonWallQuery.getWall(wx, wy, pz, 1, map, authored);

                    if (n == WallType.WALL)       { g.setColor(T.mmWall); g.drawLine(sx, sy, sx+tileSize-1, sy); }
                    else if (n == WallType.DOOR)  { g.setColor(T.mmDoor); g.drawLine(sx, sy, sx+tileSize-1, sy); }
                    if (w2 == WallType.WALL)      { g.setColor(T.mmWall); g.drawLine(sx, sy, sx, sy+tileSize-1); }
                    else if (w2 == WallType.DOOR) { g.setColor(T.mmDoor); g.drawLine(sx, sy, sx, sy+tileSize-1); }
                    if (s == WallType.WALL)       { g.setColor(T.mmWall); g.drawLine(sx, sy+tileSize-1, sx+tileSize-1, sy+tileSize-1); }
                    if (e2 == WallType.WALL)      { g.setColor(T.mmWall); g.drawLine(sx+tileSize-1, sy, sx+tileSize-1, sy+tileSize-1); }
                }

                char spec = DungeonWallQuery.getSpecial(wx, wy, pz, map, authored);
                if (spec != '.') {
                    g.setColor(T.mmSpecial);
                    g.fillRect(sx + 1, sy + 1, tileSize - 2, tileSize - 2);
                }
            }
        }

        // Player
        int playerSx = mapX0 + (px - startX) * tileSize;
        int playerSy = mapY0 + (py - startY) * tileSize;
        g.setColor(T.mmPlayer);
        g.fillRect(playerSx, playerSy, tileSize, tileSize);

        // Facing arrow
        int pcx = playerSx + tileSize / 2, pcy = playerSy + tileSize / 2;
        int arrowLen = tileSize * 2;
        int ax = pcx + STEP_DX[facing] * arrowLen;
        int ay = pcy + STEP_DY[facing] * arrowLen;
        g.setStroke(new BasicStroke(2));
        g.drawLine(pcx, pcy, ax, ay);
        int perpDx = STEP_DY[facing], perpDy = -STEP_DX[facing];
        int hx = ax - STEP_DX[facing] * tileSize / 2;
        int hy = ay - STEP_DY[facing] * tileSize / 2;
        g.drawLine(ax, ay, hx + perpDx * tileSize / 2, hy + perpDy * tileSize / 2);
        g.drawLine(ax, ay, hx - perpDx * tileSize / 2, hy - perpDy * tileSize / 2);
    }

    // ── Utilities ───────────────────────────────────────────────────────────

    private static Color blend(Color a, Color b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        return new Color(
            (int)(a.getRed()   + (b.getRed()   - a.getRed())   * t),
            (int)(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
            (int)(a.getBlue()  + (b.getBlue()  - a.getBlue())  * t),
            (int)(a.getAlpha() + (b.getAlpha() - a.getAlpha()) * t));
    }

    private static Color darken(Color c, float factor) {
        return new Color(
            (int)(c.getRed() * factor),
            (int)(c.getGreen() * factor),
            (int)(c.getBlue() * factor));
    }

    private static void generateTextures() {
        // Pre-generate small texture tiles for potential future use
        // Currently using procedural drawing in paint methods
    }

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
            case 'M' -> "Memory Pool";
            case 'n' -> "Spinner";
            case 'k' -> "Dark Zone";
            case 'c' -> "Chute";
            case 'q' -> "Riddle Door";
            default -> null;
        };
    }
}

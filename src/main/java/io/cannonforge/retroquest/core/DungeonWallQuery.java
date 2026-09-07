package io.cannonforge.retroquest.core;

import io.cannonforge.retroquest.model.Dungeon;
import io.cannonforge.retroquest.model.Dungeon.WallType;
import io.cannonforge.retroquest.registry.TileRegistry;

/**
 * Unified wall query abstraction that works for both procedural and authored dungeons.
 *
 * <p><b>Procedural dungeons</b>: delegates to {@link Dungeon} static methods.
 * Coordinates are 1-based (1..200).
 *
 * <p><b>Authored dungeons</b> (.rfmap files): infers WALL/DOOR/OPEN from the tile grid.
 * A non-walkable tile = WALL. A door tile ('d', '|', '[', '{') = DOOR. Everything else = OPEN.
 * Coordinates are 0-based (matching the array).
 */
public class DungeonWallQuery {

    // Step offsets: N=0, E=1, S=2, W=3
    private static final int[] DX = { 0, 1, 0, -1};
    private static final int[] DY = {-1, 0, 1,  0};

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Gets the wall type in the given absolute direction from cell (x,y).
     *
     * @param x       cell X (0-based for authored, 1-based for procedural)
     * @param y       cell Y
     * @param z       dungeon depth (only used for procedural)
     * @param dir     0=N, 1=E, 2=S, 3=W
     * @param map     the tile grid (only used for authored)
     * @param authored true = authored .rfmap, false = procedural
     */
    public static WallType getWall(int x, int y, int z, int dir, char[][] map, boolean authored) {
        if (!authored) {
            return switch (dir) {
                case 0 -> Dungeon.getNorthWall(x, y, z);
                case 1 -> Dungeon.getEastWall(x, y, z);
                case 2 -> Dungeon.getSouthWall(x, y, z);
                case 3 -> Dungeon.getWestWall(x, y, z);
                default -> WallType.WALL;
            };
        }
        // Authored: check the adjacent tile in the given direction
        int nx = x + DX[dir];
        int ny = y + DY[dir];
        return inferFromTile(nx, ny, map);
    }

    /**
     * Gets the wall on the relative left (-1) or right (+1) side of the player's view.
     */
    public static WallType getRelativeWall(int x, int y, int z, int facing, int side,
                                            char[][] map, boolean authored) {
        int wallDir = (facing + (side == -1 ? 3 : 1)) & 3;
        return getWall(x, y, z, wallDir, map, authored);
    }

    /**
     * Gets the wall ahead of the player in the facing direction from the given cell.
     */
    public static WallType getFrontWall(int x, int y, int z, int facing,
                                         char[][] map, boolean authored) {
        return getWall(x, y, z, facing, map, authored);
    }

    /**
     * Gets the special feature character at (x,y).
     */
    public static char getSpecial(int x, int y, int z, char[][] map, boolean authored) {
        if (!authored) return Dungeon.getSpecial(x, y, z);
        if (y < 0 || y >= map.length || x < 0 || x >= map[0].length) return '.';
        char tile = map[y][x];
        return isSpecialTile(tile) ? tile : '.';
    }

    /**
     * Checks if the player can move in the given direction.
     */
    public static boolean canMove(int x, int y, int z, int dir, char[][] map, boolean authored) {
        if (!authored) {
            return switch (dir) {
                case 0 -> Dungeon.canMoveNorth(x, y, z);
                case 1 -> Dungeon.canMoveEast(x, y, z);
                case 2 -> Dungeon.canMoveSouth(x, y, z);
                case 3 -> Dungeon.canMoveWest(x, y, z);
                default -> false;
            };
        }
        int nx = x + DX[dir];
        int ny = y + DY[dir];
        if (ny < 0 || ny >= map.length || nx < 0 || nx >= map[0].length) return false;
        return TileRegistry.getByIdSafe(map[ny][nx]).isWalkable();
    }

    /**
     * Returns the map width (for bounds checks).
     */
    public static int getMapWidth(char[][] map, boolean authored) {
        return authored ? map[0].length : Dungeon.WIDTH;
    }

    /**
     * Returns the map height (for bounds checks).
     */
    public static int getMapHeight(char[][] map, boolean authored) {
        return authored ? map.length : Dungeon.HEIGHT;
    }

    // ── Internal ─────────────────────────────────────────────────────────────

    private static WallType inferFromTile(int x, int y, char[][] map) {
        if (y < 0 || y >= map.length || x < 0 || x >= map[0].length) return WallType.WALL;
        char tile = map[y][x];
        if (isDoorTile(tile)) return WallType.DOOR;
        if (!TileRegistry.getByIdSafe(tile).isWalkable()) return WallType.WALL;
        return WallType.OPEN;
    }

    private static boolean isDoorTile(char c) {
        return c == 'd' || c == '|' || c == '[' || c == '{';
    }

    private static boolean isSpecialTile(char c) {
        // Must stay in step with DungeonController.handleDungeonSpecialAt's switch:
        // A f s H t P I B L n k c q M plus g (glowing cube) and R (puzzle), which had
        // cases in the dispatch but were missing here, so they never fired.
        return "AfsHtPIBLnkcqMgR".indexOf(c) >= 0;
    }
}

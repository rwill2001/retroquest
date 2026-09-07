package io.cannonforge.retroquest.model;

/**
 * Procedural dungeon generator using a deterministic mathematical formula
 * (derived from classic CRPG BASIC constants XO/YO/ZO).
 *
 * <p>Generates a multi-level dungeon where each level is a {@code char[][]} tile map
 * with walls, floors, special features (altars, fountains, chests), and stairways.
 * Use {@link #generate(int)} to produce a level map for the given depth.
 */
public class Dungeon {
    public static final int WIDTH = 200;
    public static final int HEIGHT = 200;

    private static final double XO = 1.6915;
    private static final double YO = 1.4278;
    private static final double ZO = 1.2462;


    public enum WallType {
        OPEN, DOOR, WALL
    }

    /**
     * Generates the exact 200×200 map.
     * Every cell = '.' (floor) or special letter.
     * Walls are queried separately via getNorthWall()/getWestWall() — use those in your renderer!
     */
    public static char[][] generate(int depth) {
        if (depth < 1) depth = 1;
        if (depth > 50) depth = 50;

        char[][] map = new char[HEIGHT][WIDTH];

        for (int yy = 0; yy < HEIGHT; yy++) {
            for (int xx = 0; xx < WIDTH; xx++) {
                int x = xx + 1; // 1..200 (original coordinates)
                int y = yy + 1;
                map[yy][xx] = getSpecial(x, y, depth);
            }
        }
        return map;
    }

    // ===================================================================
    // Exact original 16-bit room value (same as Atari BASIC line 10010-10040)
    // ===================================================================
    private static int getRoomValue(int x, int y, int z) {
        // Wall base (low byte)
        double q = x * XO + y * YO + z * ZO + x * YO + y * ZO + z * XO;
        int roomValue = (int) q & 0xFF;

        // Special feature (high byte)
        q = x * y * ZO + y * z * XO + z * x * YO;
        int special = (int) q;
        if ((special & 3) == 0) {
            special = (special >> 2) & 0x0F;
            if (special > 9) special -= 9;
        } else {
            special = 0;
        }
        roomValue |= (special << 8);

        // Force outer borders (original edge handling)
        if (x == 1 || x == 200) roomValue |= 0x0C;  // left/right walls
        if (y == 1 || y == 200) roomValue |= 0x03;  // upper/lower walls

        return roomValue;
    }

    public static boolean canMoveWest(int x, int y, int z) {
        return getWestWall(x, y, z) != WallType.WALL;
    }
    public static boolean canMoveEast(int x, int y, int z) {
        return getEastWall(x, y, z) != WallType.WALL;
    }
    public static boolean canMoveNorth(int x, int y, int z) {
        return getNorthWall(x, y, z) != WallType.WALL;
    }
    public static boolean canMoveSouth(int x, int y, int z) {
        return getSouthWall(x, y, z) != WallType.WALL;
    }

    // ===================================================================
    // Wall queries — deterministic wall generation from room values
    // ===================================================================
    public static WallType getNorthWall(int x, int y, int z) {  // x,y = 1..200
        int bits = getRoomValue(x, y, z) & 0x03;   // bits 0-1
        return switch (bits) {
            case 0, 1 -> WallType.OPEN;
            case 2 -> WallType.DOOR;
            case 3 -> WallType.WALL;
            default -> WallType.OPEN;
        };
    }

    public static WallType getWestWall(int x, int y, int z) {
        int bits = (getRoomValue(x, y, z) >> 2) & 0x03;  // bits 2-3
        return switch (bits) {
            case 0, 1 -> WallType.OPEN;
            case 2 -> WallType.DOOR;
            case 3 -> WallType.WALL;
            default -> WallType.OPEN;
        };
    }

    // Convenience for your renderer
    public static WallType getSouthWall(int x, int y, int z) {
        return getNorthWall(x, y + 1, z);   // south wall of this room = north wall of room below
    }

    public static WallType getEastWall(int x, int y, int z) {
        return getWestWall(x + 1, y, z);
    }

    // Special feature (Inn, Pit, etc.)
    public static char getSpecial(int x, int y, int z) {
        int spec = (getRoomValue(x, y, z) >> 8) & 0x0F;  // bits 8-11
        if (spec == 0) return '.';
        char base = switch (spec) {
            case 1 -> 'I'; // Inn
            case 2 -> 'P'; // Pit
            case 3 -> 't'; // Teleporter
            case 4 -> 's'; // Stairway
            case 5 -> 'A'; // Altar
            case 6 -> 'f'; // Fountain
            case 7 -> 'g'; // Cube
            case 8 -> 'H'; // Throne
            case 9 -> 'R'; // Puzzle
            default -> '?';
        };

        // Depth-specific restrictions:
        // Pits on level 50 would send the player below the deepest level — suppress them.
        // Inns only exist on level 1 (surface-adjacent refuge).
        if ((base == 'P' && z >= 50) || (base == 'I' && z != 1)) return '.';

        return base;
    }
}

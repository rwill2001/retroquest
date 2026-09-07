import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Patches data/overworlds/lirandel.rfmap to improve river and road layout.
 *
 * Changes:
 *   1. Replaces double-width river with single-width meandering path
 *   2. Adds river source (spring) and river mouth (delta)
 *   3. Adds bridge where Moonhaven road crosses river
 *   4. Fixes road connections: Waterfallcave, highway crossroad, Moonhaven→southern road
 *   5. Extends highway west to connect to Moonhaven area
 *   6. Routes southern road to portal
 *
 * Run from the project root:
 *   javac -encoding UTF-8 tools/LirandelMapPatch2.java -d tools/
 *   java -cp tools LirandelMapPatch2
 */
public class LirandelMapPatch2 {

    static final String RFMAP = "data/overworlds/lirandel.rfmap";
    static final int WIDTH = 120;
    static final int HEIGHT = 100;

    // New PUA tile chars
    static final String BRIDGE_EW     = "\uE0A0";
    static final String ROAD_CROSS    = "\uE0A1";
    static final String ROAD_T_SOUTH  = "\uE0A2";
    // static final String ROAD_T_EAST = "\uE0A3";  // defined but not used in this patch
    static final String RIVER_SOURCE  = "\uE0A4";
    static final String RIVER_MOUTH   = "\uE0A5";

    // River tile chars to clear
    static final String RIVER_CHARS = "w9abeh";

    // Protected waterfall inside barrier — do NOT clear
    static final int WATERFALL_COL = 65, WATERFALL_ROW = 27;

    // ─── River waypoints (col, row) ────────────────────────────────────────────
    // River flows south-southwest from hills to coast, single-width.
    static final int[][] RIVER_WAYPOINTS = {
        {48, 20},  // source — in the hills
        {48, 23},  // south 3, then turn west
        {46, 23},  // west 2, then turn south
        {46, 26},  // south 3, then turn west
        {44, 26},  // west 2, then turn south
        {44, 29},  // south 3, then turn west
        {41, 29},  // west 3, then turn south
        {41, 32},  // south 3, then turn west
        {39, 32},  // west 2, then turn south
        {39, 35},  // south 3, then turn west
        {37, 35},  // west 2, then turn south
        {37, 38},  // south 3, then turn west
        {35, 38},  // west 2, then turn south
        {35, 41},  // south 3, then turn west
        {33, 41},  // west 2, then turn south (long straight run)
        {33, 55},  // south 14 (bridge at row 48), then turn west
        {31, 55},  // west 2, then turn south
        {31, 60},  // south 5, then turn west
        {30, 60},  // west 1, then turn south
        {30, 63},  // south 3, then turn west
        {29, 63},  // west 1, then turn south
        {29, 65},  // south 2, then turn west
        {28, 65},  // west 1, then turn south
        {28, 67},  // south 2, then turn west
        {27, 67},  // west 1, then turn south
        {27, 69},  // south 2, then turn west
        {26, 69},  // west 1, then turn south
        {26, 71},  // mouth — at the coastline
    };

    // Bridge location (Moonhaven EW road crosses river)
    static final int BRIDGE_COL = 33, BRIDGE_ROW = 48;

    public static void main(String[] args) throws Exception {
        String content = Files.readString(Path.of(RFMAP));
        String[][] tiles = parseTiles(content);

        System.out.println("Loaded " + RFMAP + " (" + tiles.length + " rows x " + tiles[0].length + " cols)");

        // Phase 1: Clear old river tiles
        int cleared = clearOldRiver(tiles);
        System.out.println("Phase 1: Cleared " + cleared + " old river tiles");

        // Phase 2: Place new single-width river
        int placed = placeRiver(tiles);
        System.out.println("Phase 2: Placed " + placed + " new river tiles");

        // Phase 3: Place bridge
        tiles[BRIDGE_ROW][BRIDGE_COL] = BRIDGE_EW;
        System.out.println("Phase 3: Placed bridge at (" + BRIDGE_COL + "," + BRIDGE_ROW + ")");

        // Phase 4: Fix road connections
        int roadChanges = fixRoads(tiles);
        System.out.println("Phase 4: Made " + roadChanges + " road changes");

        // Phase 5: Write back
        String newContent = replaceTiles(content, tiles);
        Files.writeString(Path.of(RFMAP), newContent);
        System.out.println("Saved " + RFMAP);

        // Print the updated map for visual verification
        System.out.println("\n=== UPDATED MAP ===");
        for (int y = 0; y < HEIGHT; y++) {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("%3d: ", y));
            for (int x = 0; x < WIDTH; x++) {
                String t = tiles[y][x];
                // Show PUA chars as readable symbols
                if (t.length() == 1 && t.charAt(0) >= '\uE000') {
                    char c = t.charAt(0);
                    if (c == '\uE0A0') sb.append('B'); // bridge
                    else if (c == '\uE0A1') sb.append('+'); // crossroad
                    else if (c == '\uE0A2') sb.append('\u2534'); // T-south
                    else if (c == '\uE0A4') sb.append('S'); // source
                    else if (c == '\uE0A5') sb.append('M'); // mouth
                    else sb.append('?');
                } else {
                    sb.append(t);
                }
            }
            System.out.println(sb);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Phase 1: Clear old river
    // ═══════════════════════════════════════════════════════════════════════════

    static int clearOldRiver(String[][] tiles) {
        int count = 0;
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                if (x == WATERFALL_COL && y == WATERFALL_ROW) continue; // protect waterfall
                String t = tiles[y][x];
                if (t.length() == 1 && RIVER_CHARS.indexOf(t.charAt(0)) >= 0) {
                    tiles[y][x] = ".";
                    count++;
                }
            }
        }
        return count;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Phase 2: Place river
    // ═══════════════════════════════════════════════════════════════════════════

    static int placeRiver(String[][] tiles) {
        int count = 0;
        int[][] wp = RIVER_WAYPOINTS;

        // Place source at first waypoint
        tiles[wp[0][1]][wp[0][0]] = RIVER_SOURCE;
        count++;

        for (int i = 0; i < wp.length - 1; i++) {
            int x1 = wp[i][0], y1 = wp[i][1];
            int x2 = wp[i + 1][0], y2 = wp[i + 1][1];

            // Determine direction of this segment
            boolean vertical = (x1 == x2);
            boolean horizontal = (y1 == y2);

            if (vertical) {
                // Fill NS tiles between waypoints (exclusive of endpoints)
                int yMin = Math.min(y1, y2), yMax = Math.max(y1, y2);
                for (int y = yMin + 1; y < yMax; y++) {
                    tiles[y][x1] = "w";
                    count++;
                }
            } else if (horizontal) {
                // Fill EW tiles between waypoints (exclusive of endpoints)
                int xMin = Math.min(x1, x2), xMax = Math.max(x1, x2);
                for (int x = xMin + 1; x < xMax; x++) {
                    tiles[y1][x] = "9";
                    count++;
                }
            }

            // Place corner tile at the NEXT waypoint (unless it's the last one)
            if (i + 1 < wp.length - 1) {
                int x3 = wp[i + 2][0], y3 = wp[i + 2][1];
                String corner = getCornerTile(x1, y1, x2, y2, x3, y3);
                tiles[y2][x2] = corner;
                count++;
                // Skip the next segment's start since we just placed the corner
            }
        }

        // Place mouth at last waypoint
        tiles[wp[wp.length - 1][1]][wp[wp.length - 1][0]] = RIVER_MOUTH;
        count++;

        return count;
    }

    /**
     * Determines the correct corner tile at the middle waypoint of three consecutive waypoints.
     * The corner connects the incoming edge (from prev) to the outgoing edge (to next).
     */
    static String getCornerTile(int px, int py, int cx, int cy, int nx, int ny) {
        // Incoming: direction from prev to current
        // Outgoing: direction from current to next
        // The corner tile connects the edge where water ENTERS and the edge where water EXITS.

        // Incoming edge (which edge of current tile does water enter through?)
        char inEdge;
        if (py < cy) inEdge = 'N';      // prev is above → water enters from North
        else if (py > cy) inEdge = 'S';  // prev is below → water enters from South
        else if (px < cx) inEdge = 'W';  // prev is left → water enters from West
        else inEdge = 'E';               // prev is right → water enters from East

        // Outgoing edge
        char outEdge;
        if (ny > cy) outEdge = 'S';      // next is below → water exits through South
        else if (ny < cy) outEdge = 'N';
        else if (nx > cx) outEdge = 'E';
        else outEdge = 'W';

        // Map edge pair to corner tile
        String edges = "" + inEdge + outEdge;
        // Also handle reversed pairs (water flows both ways visually)
        switch (edges) {
            case "NE": case "EN": return "a"; // River NE
            case "NW": case "WN": return "b"; // River NW
            case "SE": case "ES": return "e"; // River SE
            case "SW": case "WS": return "h"; // River SW
            default:
                System.err.println("WARNING: unexpected edge pair: " + edges + " at (" + cx + "," + cy + ")");
                return "w"; // fallback to NS
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Phase 4: Fix roads
    // ═══════════════════════════════════════════════════════════════════════════

    static int fixRoads(String[][] tiles) {
        int count = 0;

        // --- Waterfallcave connection ---
        // Add corners connecting diagonal road to Waterfallcave town entrance
        tiles[29][62] = "7";  // Road SE: connects diagonal road (62,30)=r south to EW road (63,29)=4 east
        tiles[29][65] = "6";  // Road NW: connects EW road west to Waterfallcave E (65,28) north
        count += 2;
        System.out.println("  Waterfallcave: corners at (62,29) and (65,29)");

        // --- Highway extension west ---
        // Extend highway from (41,45) west to (37,45) with T-intersection
        tiles[45][37] = ROAD_T_SOUTH;  // T-south: EW highway + south branch to Moonhaven
        tiles[45][38] = "4";           // Road EW
        tiles[45][39] = "4";           // Road EW
        tiles[45][40] = "4";           // Road EW
        count += 4;
        System.out.println("  Highway extended west to x=37 with T-south junction");

        // --- Highway-to-Moonhaven vertical spur ---
        tiles[46][37] = "r";            // Road NS
        tiles[47][37] = ROAD_CROSS;     // Crossroad (replaces old floating EW road)
        tiles[48][37] = "6";            // Road NW: connects north spur to Moonhaven EW road
        count += 3;
        System.out.println("  Highway-Moonhaven spur: (37,46)→(37,47)→(37,48)");

        // --- Moonhaven east → southern road connection ---
        tiles[48][36] = ROAD_CROSS;     // Crossroad at junction (was terrain)
        tiles[48][35] = "4";            // Road EW (was NW corner '6' — straightened)
        tiles[49][36] = "r";            // Road NS (was NW corner '6' — straightened)
        tiles[49][35] = ".";            // Remove broken SE corner '7'
        count += 4;
        System.out.println("  Moonhaven→southern road: (36,48)=crossroad, (36,49)=r");

        // --- Remove floating road fragments ---
        tiles[46][39] = ".";            // Was EW road going nowhere
        tiles[46][40] = ".";            // Was EW road going nowhere
        count += 2;
        System.out.println("  Removed 2 floating road segments at row 46");

        // --- Highway crossroad where diagonal road meets ---
        tiles[45][51] = ROAD_CROSS;     // Was '4' EW, upgrade to crossroad
        count++;
        System.out.println("  Crossroad at (51,45) where diagonal road meets highway");

        // --- Southern road extension to portal ---
        tiles[80][45] = "5";            // Road NE: turn east (was 'r' NS)
        for (int x = 46; x <= 54; x++) {
            tiles[80][x] = "4";         // Road EW
        }
        tiles[80][55] = "8";            // Road SW: turn south
        for (int y = 81; y <= 84; y++) {
            tiles[y][55] = "r";         // Road NS to portal
        }
        count += 1 + 9 + 1 + 4;
        System.out.println("  Portal road: (45,80) east to (55,80), south to (55,84)");

        return count;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  JSON parsing helpers
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Parses the tiles 2D array from the rfmap JSON string.
     * Returns String[HEIGHT][WIDTH] where each element is a single-char tile string.
     */
    static String[][] parseTiles(String json) {
        String[][] result = new String[HEIGHT][WIDTH];

        // Find "tiles": [
        int tilesIdx = json.indexOf("\"tiles\"");
        int outerBracket = json.indexOf('[', tilesIdx);

        int pos = outerBracket + 1;
        for (int row = 0; row < HEIGHT; row++) {
            // Find the row's opening [
            int rowStart = json.indexOf('[', pos);
            int rowEnd = json.indexOf(']', rowStart);

            // Parse elements within this row
            String rowContent = json.substring(rowStart + 1, rowEnd);
            int col = 0;
            int i = 0;
            while (i < rowContent.length() && col < WIDTH) {
                int q1 = rowContent.indexOf('"', i);
                if (q1 < 0) break;
                int q2 = rowContent.indexOf('"', q1 + 1);

                // Handle unicode escapes like \uE0A0
                String raw = rowContent.substring(q1 + 1, q2);
                if (raw.startsWith("\\u") && raw.length() == 6) {
                    int codePoint = Integer.parseInt(raw.substring(2), 16);
                    result[row][col] = String.valueOf((char) codePoint);
                } else {
                    result[row][col] = raw;
                }
                col++;
                i = q2 + 1;
            }

            pos = rowEnd + 1;
        }
        return result;
    }

    /**
     * Replaces the tiles array in the original JSON with the modified tile data.
     */
    static String replaceTiles(String json, String[][] tiles) {
        // Find start and end of the tiles array
        int tilesIdx = json.indexOf("\"tiles\"");
        int outerStart = json.indexOf('[', tilesIdx);

        // Find the matching closing bracket of the outer array
        int depth = 0;
        int outerEnd = -1;
        for (int i = outerStart; i < json.length(); i++) {
            if (json.charAt(i) == '[') depth++;
            else if (json.charAt(i) == ']') {
                depth--;
                if (depth == 0) {
                    outerEnd = i + 1;
                    break;
                }
            }
        }

        // Build new tiles array string
        StringBuilder sb = new StringBuilder();
        sb.append("[\n");
        for (int y = 0; y < HEIGHT; y++) {
            sb.append("    [");
            for (int x = 0; x < WIDTH; x++) {
                if (x > 0) sb.append(",");
                sb.append('"');
                String t = tiles[y][x];
                if (t.length() == 1) {
                    char c = t.charAt(0);
                    if (c >= '\u0080') {
                        // Unicode escape for non-ASCII chars
                        sb.append(String.format("\\u%04X", (int) c));
                    } else {
                        sb.append(c);
                    }
                } else {
                    sb.append(t);
                }
                sb.append('"');
            }
            sb.append(']');
            if (y < HEIGHT - 1) sb.append(',');
            sb.append('\n');
        }
        sb.append("  ]");

        return json.substring(0, outerStart) + sb.toString() + json.substring(outerEnd);
    }
}

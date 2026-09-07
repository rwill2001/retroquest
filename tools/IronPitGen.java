import com.google.gson.*;
import java.io.*;
import java.util.*;

/**
 * Generates The Iron Pit — a 3-level authored dungeon for Bellorak
 * (Island 7). Each level is 40x40 tiles.
 *
 * Story themes (depths 25-27):
 *   Level 1: Arena Understructure — long corridors, iron gate doors
 *   Level 2: Gladiator Crypts — many small rooms, chests, dark zones
 *   Level 3: Trial Sanctum — open arena chamber, single altar for Bellorak trial
 *
 * Run:
 *   javac -encoding UTF-8 -cp "target/classes;GSON_JAR" tools/IronPitGen.java -d tools/
 *   java -cp "tools;target/classes;GSON_JAR" IronPitGen
 */
public class IronPitGen {

    static final int W = 40, H = 40;
    static final char WALL = 'W', FLOOR = '.', STAIR = 's', ALTAR = 'A',
            FOUNTAIN = 'f', THRONE = 'P', CHEST = 'C', DOOR = 'd',
            TELEPORTER = 't', SPINNER = 'n', DARK = 'k',
            RIDDLE = 'q', CUBE = 'g';
    static final String GROUP = "iron_pit";
    static Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public static void main(String[] args) throws Exception {
        new File("data/dungeons").mkdirs();
        generateLevel1();
        generateLevel2();
        generateLevel3();
        System.out.println("The Iron Pit generated: 3 levels.");
    }

    // == Level 1: Arena Understructure (depth 25) =================================
    // Long corridors mimicking the support tunnels beneath an arena. Iron gate
    // doors segment the corridors. Entry from overworld at south-center.
    // Down stairs in the northeast.

    static void generateLevel1() throws Exception {
        char[][] t = wall(W, H);

        // ── Entry room (south-center) ──
        carveRoom(t, 17, 34, 6, 5);              // entry chamber
        t[36][20] = STAIR;                        // entry from overworld

        // ── Main north-south corridor ──
        carveVCorridor(t, 20, 8, 34);
        t[30][20] = DOOR;                         // iron gate
        t[22][20] = DOOR;                         // iron gate
        t[14][20] = DOOR;                         // iron gate

        // ── East-west corridor at y=26 ──
        carveHCorridor(t, 4, 26, 36);
        t[26][10] = DOOR;
        t[26][30] = DOOR;

        // ── East-west corridor at y=14 ──
        carveHCorridor(t, 4, 14, 36);
        t[14][10] = DOOR;
        t[14][30] = DOOR;

        // ── West staging room (south) ──
        carveRoom(t, 2, 23, 7, 7);
        t[26][4] = DOOR;
        t[25][5] = CHEST;

        // ── East staging room (south) ──
        carveRoom(t, 31, 23, 7, 7);
        t[26][31] = DOOR;
        t[25][35] = CHEST;

        // ── West armory room (north) ──
        carveRoom(t, 2, 11, 7, 7);
        t[14][4] = DOOR;
        t[13][5] = CHEST;
        t[15][6] = FOUNTAIN;

        // ── East armory room (north) ──
        carveRoom(t, 31, 11, 7, 7);
        t[14][31] = DOOR;
        t[13][35] = CHEST;

        // ── North gallery corridor ──
        carveHCorridor(t, 8, 8, 32);
        carveVCorridor(t, 8, 8, 14);
        carveVCorridor(t, 32, 8, 14);
        t[8][8] = DOOR;
        t[8][32] = DOOR;

        // ── Central north room ──
        carveRoom(t, 16, 4, 9, 5);
        carveVCorridor(t, 20, 4, 8);
        t[8][20] = DOOR;

        // ── Long parallel corridors (west and east of main) ──
        carveVCorridor(t, 12, 14, 26);
        carveVCorridor(t, 28, 14, 26);
        t[20][12] = DOOR;
        t[20][28] = DOOR;

        // ── Cross connections ──
        carveHCorridor(t, 12, 20, 20);
        carveHCorridor(t, 20, 20, 28);

        // ── Small guard alcoves along east corridor ──
        carveRoom(t, 35, 16, 4, 3);
        carveHCorridor(t, 32, 17, 35);

        carveRoom(t, 35, 20, 4, 3);
        carveHCorridor(t, 32, 21, 35);

        // ── Northeast room with down stairs ──
        carveRoom(t, 30, 2, 8, 5);
        carveVCorridor(t, 33, 5, 8);
        t[5][33] = DOOR;
        t[4][34] = STAIR;                         // down to level 2

        saveLevel(t, 1, 20, 36);
        System.out.println("  Level 1: Arena Understructure");
    }

    // == Level 2: Gladiator Crypts (depth 26) =====================================
    // Many small crypt rooms arranged in a grid, with chests and dark zones.
    // Up stairs in northeast, down stairs in southwest.

    static void generateLevel2() throws Exception {
        char[][] t = wall(W, H);

        // ── Up stairs (northeast, matching L1 down) ──
        carveRoom(t, 30, 2, 8, 5);
        t[4][34] = STAIR;                         // up to level 1

        // ── Grid of crypt rooms ──
        // 5 columns x 4 rows of 5x5 rooms
        int[] colX = {2, 9, 16, 23, 30};
        int[] rowY = {2, 11, 20, 29};

        for (int r = 0; r < 4; r++) {
            for (int c = 0; c < 5; c++) {
                // Skip the NE corner (already has stairs room)
                if (r == 0 && c == 4) continue;
                carveRoom(t, colX[c], rowY[r], 5, 5);
            }
        }

        // ── Horizontal corridors connecting rooms in each row ──
        for (int r = 0; r < 4; r++) {
            int cy = rowY[r] + 2;                 // center y of row
            for (int c = 0; c < 4; c++) {
                int fromX = colX[c] + 4;
                int toX = colX[c + 1];
                carveHCorridor(t, fromX, cy, toX);
                t[cy][fromX] = DOOR;
            }
        }

        // ── Vertical corridors connecting rooms in each column ──
        for (int c = 0; c < 5; c++) {
            int cx = colX[c] + 2;                 // center x of column
            for (int r = 0; r < 3; r++) {
                int fromY = rowY[r] + 4;
                int toY = rowY[r + 1];
                carveVCorridor(t, cx, fromY, toY);
                t[fromY][cx] = DOOR;
            }
        }

        // Connect stairs room to grid
        carveHCorridor(t, 27, 4, 30);

        // ── Chests in various crypt rooms ──
        t[3][4]   = CHEST;                        // room (0,0)
        t[12][10] = CHEST;                        // room (1,1)
        t[21][17] = CHEST;                        // room (2,2)
        t[30][24] = CHEST;                        // room (3,3)
        t[3][17]  = CHEST;                        // room (2,0)
        t[30][10] = CHEST;                        // room (1,3)
        t[12][31] = CHEST;                        // room (4,1)
        t[21][3]  = CHEST;                        // room (0,2)

        // ── Dark zones in select rooms ──
        // Room (0,1) fully dark
        for (int y = 11; y < 16; y++)
            for (int x = 2; x < 7; x++)
                if (t[y][x] == FLOOR) t[y][x] = DARK;

        // Room (3,1) fully dark
        for (int y = 11; y < 16; y++)
            for (int x = 23; x < 28; x++)
                if (t[y][x] == FLOOR) t[y][x] = DARK;

        // Room (1,2) fully dark
        for (int y = 20; y < 25; y++)
            for (int x = 9; x < 14; x++)
                if (t[y][x] == FLOOR) t[y][x] = DARK;

        // Room (4,2) fully dark
        for (int y = 20; y < 25; y++)
            for (int x = 30; x < 35; x++)
                if (t[y][x] == FLOOR) t[y][x] = DARK;

        // Room (2,3) fully dark
        for (int y = 29; y < 34; y++)
            for (int x = 16; x < 21; x++)
                if (t[y][x] == FLOOR) t[y][x] = DARK;

        // ── Spinners in corridors ──
        t[4][13] = SPINNER;                       // corridor row 0
        t[22][27] = SPINNER;                      // corridor col 3

        // ── Fountain in room (4,3) ──
        t[31][32] = FOUNTAIN;

        // ── Teleporter pair ──
        t[3][24]  = TELEPORTER;                   // room (3,0) -> warps to (0,3)
        t[31][4]  = TELEPORTER;                   // room (0,3) -> warps to (3,0)

        // ── Down stairs (southwest, room (0,3)) ──
        t[33][4] = STAIR;                         // down to level 3

        saveLevel(t, 2, 34, 4);
        System.out.println("  Level 2: Gladiator Crypts");
    }

    // == Level 3: Trial Sanctum (depth 27) ========================================
    // Open arena-like chamber in the center. Single altar for the Bellorak trial.
    // This is the CRITICAL level — the altar triggers handleBellorakArenaTrial().

    static void generateLevel3() throws Exception {
        char[][] t = wall(W, H);

        // ── Up stairs (southwest, matching L2 down) ──
        carveRoom(t, 2, 31, 6, 6);
        t[33][4] = STAIR;                         // up to level 2

        // ── Corridor from stairs to outer ring ──
        carveHCorridor(t, 8, 34, 15);
        carveVCorridor(t, 15, 28, 34);

        // ── Outer arena ring ──
        // North
        carveHCorridor(t, 8, 6, 32);
        // South
        carveHCorridor(t, 8, 28, 32);
        // West
        carveVCorridor(t, 8, 6, 28);
        // East
        carveVCorridor(t, 32, 6, 28);

        // ── Corner preparation rooms ──
        carveRoom(t, 5, 3, 7, 6);                // NW
        carveRoom(t, 29, 3, 7, 6);               // NE
        carveRoom(t, 5, 26, 7, 5);               // SW
        carveRoom(t, 29, 26, 7, 5);              // SE

        // Doors at corners
        t[6][11] = DOOR;
        t[6][29] = DOOR;
        t[28][11] = DOOR;
        t[28][29] = DOOR;

        // Fountain in NW prep room (heal before trial)
        t[5][8] = FOUNTAIN;

        // Chests in NE and SE prep rooms
        t[5][33] = CHEST;
        t[28][33] = CHEST;

        // ── Inner approach corridors ──
        carveVCorridor(t, 20, 6, 11);            // north approach
        carveVCorridor(t, 20, 23, 28);           // south approach
        carveHCorridor(t, 8, 17, 12);            // west approach
        carveHCorridor(t, 28, 17, 32);           // east approach

        // ── Riddle door on south approach (test of worthiness) ──
        t[23][20] = RIDDLE;

        // Doors on other approaches
        t[11][20] = DOOR;
        t[17][12] = DOOR;
        t[17][28] = DOOR;

        // ── Central arena chamber (16x14) ──
        carveRoom(t, 12, 10, 16, 14);

        // Arena pillar columns (aesthetic walls)
        t[12][14] = WALL; t[12][26] = WALL;
        t[14][14] = WALL; t[14][26] = WALL;
        t[20][14] = WALL; t[20][26] = WALL;
        t[22][14] = WALL; t[22][26] = WALL;

        // Arena floor marks (decorative doors as gate arches)
        t[17][12] = DOOR;                         // west gate
        t[17][27] = DOOR;                         // east gate

        // ── THE BELLORAK TRIAL ALTAR — center of the arena ──
        t[17][20] = ALTAR;                        // *** CRITICAL: triggers handleBellorakArenaTrial() ***

        // ── Dark zones flanking the arena ──
        // West dark corridor
        for (int y = 10; y < 16; y++)
            t[y][8] = DARK;
        // East dark corridor
        for (int y = 18; y < 24; y++)
            t[y][32] = DARK;

        saveLevel(t, 3, 4, 33);
        System.out.println("  Level 3: Trial Sanctum");
    }

    // == Utility methods ======================================================

    static char[][] wall(int w, int h) {
        char[][] t = new char[h][w];
        for (char[] row : t) Arrays.fill(row, WALL);
        return t;
    }

    static void carveRoom(char[][] t, int x, int y, int w, int h) {
        for (int dy = 0; dy < h && y + dy < t.length; dy++)
            for (int dx = 0; dx < w && x + dx < t[0].length; dx++)
                t[y + dy][x + dx] = FLOOR;
    }

    static void carveHCorridor(char[][] t, int fromX, int y, int toX) {
        int lo = Math.min(fromX, toX), hi = Math.max(fromX, toX);
        for (int x = lo; x <= hi && x < t[0].length; x++) t[y][x] = FLOOR;
    }

    static void carveVCorridor(char[][] t, int x, int fromY, int toY) {
        int lo = Math.min(fromY, toY), hi = Math.max(fromY, toY);
        for (int y = lo; y <= hi && y < t.length; y++) t[y][x] = FLOOR;
    }

    static void saveLevel(char[][] t, int level, int entryX, int entryY) throws Exception {
        JsonObject map = new JsonObject();
        map.addProperty("type", "DUNGEON");
        map.addProperty("name", GROUP + "_" + level);
        map.addProperty("width", W);
        map.addProperty("height", H);
        map.addProperty("interiorEntryX", entryX);
        map.addProperty("interiorEntryY", entryY);
        map.addProperty("dungeonLevel", level);
        map.addProperty("dungeonGroup", GROUP);

        JsonArray tilesArr = new JsonArray();
        for (int y = 0; y < H; y++) {
            JsonArray row = new JsonArray();
            for (int x = 0; x < W; x++) row.add(String.valueOf(t[y][x]));
            tilesArr.add(row);
        }
        map.add("tiles", tilesArr);

        JsonArray spawnArr = new JsonArray();
        for (int y = 0; y < H; y++) {
            JsonArray row = new JsonArray();
            for (int x = 0; x < W; x++) row.add(0);
            spawnArr.add(row);
        }
        map.add("spawnDifficulty", spawnArr);

        map.add("npcs", new JsonArray());
        map.add("townEntrances", new JsonArray());
        map.add("overworldTeleporters", new JsonArray());

        JsonObject tileStates = new JsonObject();
        map.add("initialTileStates", tileStates);

        File out = new File("data/dungeons/" + GROUP + "_" + level + ".rfmap");
        out.getParentFile().mkdirs();
        try (OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(out), "UTF-8")) { gson.toJson(map, w); }
    }
}

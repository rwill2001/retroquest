import com.google.gson.*;
import java.io.*;
import java.util.*;

/**
 * Generates The War Beneath — a 3-level authored dungeon for Bellorak
 * (Island 7). Each level is 40x40 tiles.
 *
 * Story themes (depths 24-26):
 *   Level 1: Entry Tunnels — trench-like corridors, multiple paths
 *   Level 2: Cavern Network — wider rooms, dark zones, spinner traps
 *   Level 3: Ancient Forge Room — large central chamber, thrones, altars, riddle doors
 *
 * Run:
 *   javac -encoding UTF-8 -cp "target/classes;GSON_JAR" tools/WarBeneathGen.java -d tools/
 *   java -cp "tools;target/classes;GSON_JAR" WarBeneathGen
 */
public class WarBeneathGen {

    static final int W = 40, H = 40;
    static final char WALL = 'W', FLOOR = '.', STAIR = 's', ALTAR = 'A',
            FOUNTAIN = 'f', THRONE = 'P', CHEST = 'C', DOOR = 'd',
            TELEPORTER = 't', SPINNER = 'n', DARK = 'k',
            RIDDLE = 'q', CUBE = 'g';
    static final String GROUP = "war_beneath";
    static Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public static void main(String[] args) throws Exception {
        new File("data/dungeons").mkdirs();
        generateLevel1();
        generateLevel2();
        generateLevel3();
        System.out.println("The War Beneath generated: 3 levels.");
    }

    // == Level 1: Entry Tunnels (depth 24) ========================================
    // Trench-like corridors with multiple branching paths. Entry from overworld
    // at south. Down stairs in the northwest lead to Level 2.

    static void generateLevel1() throws Exception {
        char[][] t = wall(W, H);

        // ── Entry room (center-south) ──
        carveRoom(t, 17, 34, 6, 5);              // 6x5 starting room
        t[36][20] = STAIR;                        // entry from overworld (dungeon entrance)

        // ── Main trench north from entry ──
        carveVCorridor(t, 20, 20, 34);

        // ── Central crossroads ──
        carveRoom(t, 18, 18, 5, 4);              // crossroads room
        t[19][18] = DOOR;

        // ── West trench ──
        carveHCorridor(t, 5, 20, 18);            // corridor west from crossroads
        // West bunker room
        carveRoom(t, 3, 17, 6, 6);
        t[20][8] = DOOR;
        // Chest in west bunker
        t[19][5] = CHEST;

        // ── Southwest trench extension ──
        carveVCorridor(t, 6, 23, 32);
        carveRoom(t, 3, 30, 7, 5);               // southwest room
        t[32][6] = DOOR;
        // Fountain for healing
        t[32][5] = FOUNTAIN;

        // ── East trench ──
        carveHCorridor(t, 22, 20, 35);           // corridor east from crossroads
        // East guard room
        carveRoom(t, 30, 17, 7, 6);
        t[20][30] = DOOR;
        // Chest in east guard room
        t[19][34] = CHEST;

        // ── Northeast trench ──
        carveVCorridor(t, 33, 10, 17);
        carveRoom(t, 30, 8, 7, 5);               // northeast room
        t[10][33] = DOOR;

        // ── North trench from crossroads ──
        carveVCorridor(t, 20, 8, 18);
        t[18][20] = DOOR;                         // door north from crossroads

        // ── Northwest passage ──
        carveHCorridor(t, 8, 10, 20);
        carveRoom(t, 5, 5, 7, 7);                // northwest room with down stairs
        t[10][8] = DOOR;

        // ── Parallel trench (secondary path west side) ──
        carveVCorridor(t, 12, 14, 28);
        carveHCorridor(t, 12, 28, 20);           // connect to main at south
        carveHCorridor(t, 12, 14, 20);           // connect to main at north
        t[14][12] = DOOR;
        t[28][12] = DOOR;

        // ── Small alcove off parallel trench ──
        carveRoom(t, 10, 20, 4, 4);
        carveHCorridor(t, 10, 22, 12);
        t[22][12] = DOOR;

        // ── Down stairs (northwest room) ──
        t[7][8] = STAIR;                          // down to level 2

        saveLevel(t, 1, 20, 36);
        System.out.println("  Level 1: Entry Tunnels");
    }

    // == Level 2: Cavern Network (depth 25) =======================================
    // Wider rooms connected by winding corridors. Heavy dark zones and spinners.
    // Up stairs northeast, down stairs southeast.

    static void generateLevel2() throws Exception {
        char[][] t = wall(W, H);

        // ── Up stairs (northwest, matching L1 down) ──
        carveRoom(t, 5, 5, 7, 7);
        t[7][8] = STAIR;                          // up to level 1

        // ── Northern cavern corridor ──
        carveHCorridor(t, 12, 8, 28);
        t[8][12] = DOOR;

        // ── Large north-central cavern ──
        carveRoom(t, 15, 3, 10, 7);
        // Dark zone in north cavern
        for (int y = 4; y < 8; y++)
            for (int x = 16; x < 20; x++)
                if (t[y][x] == FLOOR) t[y][x] = DARK;
        // Spinner in dark zone
        t[6][18] = SPINNER;

        // ── Northeast cavern ──
        carveRoom(t, 28, 5, 8, 6);
        carveHCorridor(t, 25, 8, 28);
        t[8][28] = DOOR;
        // Chest in northeast
        t[7][33] = CHEST;

        // ── Central hub ──
        carveRoom(t, 14, 14, 12, 10);            // large 12x10 hub
        carveVCorridor(t, 20, 10, 14);            // connect to north corridor
        t[14][20] = DOOR;

        // Dark zones in central hub corners
        for (int y = 14; y < 17; y++)
            for (int x = 14; x < 17; x++)
                if (t[y][x] == FLOOR) t[y][x] = DARK;
        for (int y = 21; y < 24; y++)
            for (int x = 23; x < 26; x++)
                if (t[y][x] == FLOOR) t[y][x] = DARK;

        // Spinners in hub
        t[16][22] = SPINNER;
        t[20][16] = SPINNER;

        // Fountain in hub center
        t[18][20] = FOUNTAIN;

        // ── West cavern ──
        carveRoom(t, 2, 16, 8, 8);
        carveHCorridor(t, 10, 20, 14);
        t[20][10] = DOOR;
        // Dark zone in west cavern
        for (int y = 17; y < 22; y++)
            for (int x = 3; x < 7; x++)
                if (t[y][x] == FLOOR) t[y][x] = DARK;
        t[19][4] = SPINNER;
        // Chest hidden in dark
        t[20][5] = CHEST;

        // ── East cavern ──
        carveRoom(t, 30, 15, 8, 8);
        carveHCorridor(t, 25, 19, 30);
        t[19][30] = DOOR;
        // Dark zone
        for (int y = 16; y < 20; y++)
            for (int x = 33; x < 37; x++)
                if (t[y][x] == FLOOR) t[y][x] = DARK;

        // ── South corridor from hub ──
        carveVCorridor(t, 20, 23, 32);
        t[23][20] = DOOR;

        // ── Southwest cavern ──
        carveRoom(t, 3, 28, 8, 8);
        carveHCorridor(t, 11, 32, 20);
        t[32][11] = DOOR;
        // Spinner in southwest
        t[31][7] = SPINNER;
        // Chest
        t[30][5] = CHEST;

        // ── Southeast cavern (down stairs) ──
        carveRoom(t, 28, 28, 9, 9);
        carveHCorridor(t, 20, 32, 28);
        t[32][28] = DOOR;
        // Dark zone guarding stairs
        for (int y = 29; y < 33; y++)
            for (int x = 29; x < 33; x++)
                if (t[y][x] == FLOOR) t[y][x] = DARK;

        // ── Down stairs (southeast) ──
        t[34][34] = STAIR;                        // down to level 3

        saveLevel(t, 2, 8, 7);
        System.out.println("  Level 2: Cavern Network");
    }

    // == Level 3: Ancient Forge Room (depth 26) ===================================
    // Large central forge chamber with thrones, altars. Riddle doors guard
    // the inner sanctum. This is the deepest level.

    static void generateLevel3() throws Exception {
        char[][] t = wall(W, H);

        // ── Up stairs (southeast, matching L2 down) ──
        carveRoom(t, 32, 32, 6, 6);
        t[34][34] = STAIR;                        // up to level 2

        // ── Corridor from stairs to outer ring ──
        carveHCorridor(t, 20, 34, 32);
        carveVCorridor(t, 20, 28, 34);

        // ── Outer ring corridors ──
        // North
        carveHCorridor(t, 8, 6, 32);
        // South
        carveHCorridor(t, 8, 28, 32);
        // West
        carveVCorridor(t, 8, 6, 28);
        // East
        carveVCorridor(t, 32, 6, 28);

        // ── Corner guard rooms ──
        carveRoom(t, 5, 3, 7, 6);                // NW corner
        carveRoom(t, 29, 3, 7, 6);               // NE corner
        carveRoom(t, 5, 26, 7, 5);               // SW corner
        carveRoom(t, 29, 26, 7, 5);              // SE corner

        // Doors at corner entries
        t[6][11] = DOOR;                          // NW east exit
        t[6][29] = DOOR;                          // NE west exit
        t[28][11] = DOOR;                         // SW east exit
        t[28][29] = DOOR;                         // SE west exit

        // ── Thrones in corner rooms ──
        t[5][7] = THRONE;                         // NW throne
        t[5][33] = THRONE;                        // NE throne

        // Fountain in SW corner
        t[28][7] = FOUNTAIN;

        // Chest in SE corner
        t[28][33] = CHEST;

        // ── Inner approach corridors ──
        carveVCorridor(t, 20, 6, 12);            // north approach
        carveVCorridor(t, 20, 22, 28);           // south approach
        carveHCorridor(t, 8, 17, 13);            // west approach
        carveHCorridor(t, 27, 17, 32);           // east approach

        // ── Riddle doors guarding inner chamber ──
        t[12][20] = RIDDLE;                       // north riddle door
        t[22][20] = RIDDLE;                       // south riddle door
        t[17][13] = RIDDLE;                       // west riddle door
        t[17][27] = RIDDLE;                       // east riddle door

        // ── Central forge chamber (14x12) ──
        carveRoom(t, 13, 11, 14, 12);

        // Pillar columns in the forge chamber
        t[13][15] = WALL; t[13][25] = WALL;
        t[15][15] = WALL; t[15][25] = WALL;
        t[19][15] = WALL; t[19][25] = WALL;
        t[21][15] = WALL; t[21][25] = WALL;

        // ── The War Altar — center of the forge ──
        t[17][20] = ALTAR;                        // *** BELLORAK TRIAL (war_beneath) ***

        // ── Side alcoves off the forge chamber ──
        // West alcove
        carveRoom(t, 8, 14, 5, 6);
        // East alcove
        carveRoom(t, 27, 14, 5, 6);
        // Chest in west alcove
        t[16][10] = CHEST;
        // Fountain in east alcove
        t[16][30] = FOUNTAIN;

        // ── Dark zones in outer ring ──
        // North corridor dark zone
        for (int x = 14; x < 20; x++)
            t[6][x] = DARK;
        // South corridor dark zone
        for (int x = 22; x < 28; x++)
            t[28][x] = DARK;

        saveLevel(t, 3, 34, 34);
        System.out.println("  Level 3: Ancient Forge Room");
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

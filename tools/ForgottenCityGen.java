import com.google.gson.*;
import java.io.*;
import java.util.*;

/**
 * Generates the Forgotten City — a 3-level authored secondary dungeon for
 * Umbryn (Island 6). Each level is 40x40 tiles. Exploration-focused,
 * less puzzle-heavy than the Archive of Tears.
 *
 * Level 1: City Outskirts (depth 21) — city streets grid, residential blocks
 * Level 2: City Center (depth 22) — temple, library, market hall
 * Level 3: City Undercrypt (depth 23) — narrow corridors, optional boss
 *
 * Run:
 *   javac -encoding UTF-8 -cp "target/classes;%USERPROFILE%\.m2\repository\com\google\code\gson\gson\2.10.1\gson-2.10.1.jar" tools/ForgottenCityGen.java -d tools/
 *   java -cp "tools;target/classes;%USERPROFILE%\.m2\repository\com\google\code\gson\gson\2.10.1\gson-2.10.1.jar" ForgottenCityGen
 */
public class ForgottenCityGen {

    static final int W = 40, H = 40;
    static final char WALL = 'W', FLOOR = '.', STAIR = 's', ALTAR = 'A',
            FOUNTAIN = 'f', THRONE = 'H', PIT = 'P', DOOR = 'd',
            TELEPORTER = 't', SPINNER = 'n', DARK = 'k',
            CHUTE = 'c', RIDDLE = 'q', LOCKED_GATE = '[', CUBE = 'g';
    static final String GROUP = "forgotten_city";
    static Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public static void main(String[] args) throws Exception {
        new File("data/dungeons").mkdirs();
        generateLevel1();
        generateLevel2();
        generateLevel3();
        patchUmbrynEntrance();
        System.out.println("Forgotten City generated: 3 levels + Umbryn entrance patched.");
    }

    // == Level 1: City Outskirts ==============================================
    // A grid of city streets with residential buildings (rooms with doors).
    // Memory crystal #4 hidden in northeast behind a riddle door.
    // Scattered dark zones and a spinner in the confusing streets.

    static void generateLevel1() throws Exception {
        char[][] t = wall(W, H);

        // ── Entry area (center-south) ──
        carveRoom(t, 18, 31, 5, 5);            // entry vestibule
        t[33][20] = STAIR;                      // entry from overworld

        // ── Main north-south avenue ──
        carveVCorridor(t, 20, 5, 31);

        // ── Main east-west boulevard (y=20) ──
        carveHCorridor(t, 3, 20, 37);

        // ── Cross street north (y=12) ──
        carveHCorridor(t, 5, 12, 35);

        // ── Cross street south (y=28) ──
        carveHCorridor(t, 5, 28, 35);

        // ── Side avenue west (x=10) ──
        carveVCorridor(t, 10, 8, 32);

        // ── Side avenue east (x=30) ──
        carveVCorridor(t, 30, 8, 32);

        // ── Residential block: southwest (building 1) ──
        carveRoom(t, 5, 22, 4, 4);
        t[22][7] = DOOR;                        // door from boulevard

        // ── Residential block: southwest (building 2) ──
        carveRoom(t, 5, 30, 4, 3);
        t[30][7] = DOOR;

        // ── Residential block: northwest (building 3) ──
        carveRoom(t, 5, 8, 4, 3);
        t[10][7] = DOOR;
        // Dark zone inside — fog of forgetting
        for (int y = 8; y < 10; y++)
            for (int x = 5; x < 8; x++)
                if (t[y][x] == FLOOR) t[y][x] = DARK;

        // ── Residential block: west-center ──
        carveRoom(t, 12, 14, 5, 4);
        t[17][14] = DOOR;

        // ── Residential block: east-center ──
        carveRoom(t, 24, 14, 5, 4);
        t[17][26] = DOOR;
        // Dark zone inside
        for (int y = 14; y < 17; y++)
            for (int x = 24; x < 28; x++)
                if (t[y][x] == FLOOR) t[y][x] = DARK;

        // ── Residential block: southeast (building 4) ──
        carveRoom(t, 32, 22, 5, 4);
        t[22][32] = DOOR;

        // ── Residential block: southeast (building 5) ──
        carveRoom(t, 32, 30, 5, 3);
        t[30][34] = DOOR;

        // ── Fountain plaza (south-center) ──
        carveRoom(t, 17, 25, 7, 3);
        t[26][20] = FOUNTAIN;                   // city fountain

        // ── Spinner in confusing street intersection ──
        t[20][30] = SPINNER;                    // confusing streets

        // ── Northeast: Memory Crystal #4 chamber ──
        carveRoom(t, 33, 5, 5, 5);
        t[7][35] = THRONE;                      // memory crystal #4 — vision of the world reunited
        t[9][33] = RIDDLE;                      // riddle door guards the crystal
        // Connect to cross street north
        carveHCorridor(t, 30, 8, 33);

        // ── Dark zone on northern avenue ──
        for (int y = 6; y < 10; y++)
            if (t[y][20] == FLOOR) t[y][20] = DARK;

        // ── Down stairs to level 2 (northwest) ──
        carveRoom(t, 3, 3, 5, 4);
        t[5][5] = STAIR;                        // down to level 2
        t[6][7] = DOOR;
        carveHCorridor(t, 7, 5, 10);           // connect to west avenue

        saveLevel(t, 1, 20, 33);
        System.out.println("  Level 1: City Outskirts");
    }

    // == Level 2: City Center =================================================
    // Larger, more intact structures: temple, library, market hall.
    // Thrones trigger Aqualon-worship mural lore. More dark zones.

    static void generateLevel2() throws Exception {
        char[][] t = wall(W, H);

        // ── Up stairs (northwest, matching L1 down) ──
        carveRoom(t, 3, 3, 5, 4);
        t[5][5] = STAIR;                        // up to level 1

        // ── Main corridor east from stairs ──
        carveHCorridor(t, 8, 5, 25);

        // ── Main corridor south ──
        carveVCorridor(t, 20, 5, 35);

        // ── The Temple (north-center) ──
        carveRoom(t, 14, 8, 12, 8);            // large temple room 12x8
        t[15][20] = DOOR;                       // south entrance
        // Pillars inside the temple
        t[10][16] = WALL; t[10][23] = WALL;
        t[13][16] = WALL; t[13][23] = WALL;
        // Temple altar
        t[11][20] = ALTAR;
        // Aqualon mural throne (lore trigger #1)
        t[9][19] = THRONE;
        // Dark zone in temple alcoves
        for (int y = 9; y < 11; y++) {
            if (t[y][15] == FLOOR) t[y][15] = DARK;
            if (t[y][24] == FLOOR) t[y][24] = DARK;
        }

        // ── The Library (west wing) ──
        carveRoom(t, 3, 14, 8, 8);             // library 8x8
        t[18][11] = DOOR;                       // east entrance
        carveHCorridor(t, 11, 18, 20);         // connect to main corridor
        // Aqualon mural throne (lore trigger #2)
        t[16][7] = THRONE;
        // Internal bookshelves (walls as obstacles)
        t[16][5] = WALL; t[18][5] = WALL;
        t[16][9] = WALL; t[18][9] = WALL;
        // Dark zone — dimly lit stacks
        for (int y = 15; y < 18; y++)
            for (int x = 4; x < 7; x++)
                if (t[y][x] == FLOOR) t[y][x] = DARK;

        // ── The Market Hall (east wing) ──
        carveRoom(t, 27, 14, 10, 8);           // market hall 10x8
        t[18][27] = DOOR;                       // west entrance
        carveHCorridor(t, 20, 18, 27);         // connect to main corridor
        // Market stalls (walls as obstacles)
        t[16][29] = WALL; t[16][33] = WALL;
        t[19][29] = WALL; t[19][33] = WALL;
        // Fountain in market center
        t[17][31] = FOUNTAIN;
        // Riddle door guarding back room
        t[14][32] = RIDDLE;
        carveRoom(t, 30, 10, 5, 4);            // hidden storeroom behind riddle
        carveVCorridor(t, 32, 10, 14);

        // ── South plaza ──
        carveRoom(t, 15, 24, 10, 8);           // south plaza 10x8
        t[24][20] = DOOR;                       // north entrance
        // Dark zones in south plaza
        for (int y = 26; y < 30; y++)
            for (int x = 16; x < 20; x++)
                if (t[y][x] == FLOOR) t[y][x] = DARK;

        // ── Spinner in south corridor ──
        t[30][20] = SPINNER;

        // ── Southwest passage ──
        carveHCorridor(t, 5, 30, 20);
        carveRoom(t, 3, 28, 5, 5);
        t[30][7] = DOOR;

        // ── Southeast passage to down stairs ──
        carveHCorridor(t, 20, 32, 35);
        carveVCorridor(t, 35, 32, 37);

        // ── Down stairs (southeast, matching L3 up) ──
        carveRoom(t, 33, 33, 5, 5);
        t[35][35] = STAIR;                      // down to level 3
        t[33][35] = DOOR;

        saveLevel(t, 2, 5, 5);
        System.out.println("  Level 2: City Center");
    }

    // == Level 3: City Undercrypt =============================================
    // Narrow corridors, oppressive layout. Optional boss room at center
    // with CUBE reward. Heavy dark zone usage. Hardest encounters.

    static void generateLevel3() throws Exception {
        char[][] t = wall(W, H);

        // ── Up stairs (southeast, matching L2 down) ──
        carveRoom(t, 33, 33, 5, 5);
        t[35][35] = STAIR;                      // up to level 2

        // ── Corridor west from stairs ──
        carveHCorridor(t, 20, 35, 33);

        // ── Corridor north from junction ──
        carveVCorridor(t, 20, 10, 35);

        // ── South junction room ──
        carveRoom(t, 18, 33, 4, 3);

        // ── Southeast dark passage ──
        carveHCorridor(t, 24, 35, 33);
        carveRoom(t, 28, 34, 4, 3);
        for (int y = 34; y < 36; y++)
            for (int x = 29; x < 31; x++)
                if (t[y][x] == FLOOR) t[y][x] = DARK;

        // ── East wing: Dark gauntlet ──
        carveHCorridor(t, 20, 28, 35);
        carveRoom(t, 30, 25, 6, 5);
        t[28][30] = DOOR;
        // Heavy dark zone
        for (int y = 25; y < 29; y++)
            for (int x = 31; x < 35; x++)
                if (t[y][x] == FLOOR) t[y][x] = DARK;
        // Spinner inside dark gauntlet
        t[27][33] = SPINNER;
        // Throne (lore) at end of east wing
        carveRoom(t, 32, 21, 4, 3);
        carveVCorridor(t, 33, 21, 25);
        t[22][33] = THRONE;
        t[23][33] = DOOR;

        // ── West wing: Narrow catacombs ──
        carveHCorridor(t, 5, 28, 20);
        t[28][20] = DOOR;
        carveVCorridor(t, 5, 20, 28);
        // Catacomb rooms
        carveRoom(t, 3, 25, 4, 3);
        t[25][5] = DOOR;
        carveRoom(t, 3, 20, 4, 3);
        t[22][5] = DOOR;
        // Dark zone in catacombs
        for (int y = 22; y < 25; y++)
            if (t[y][5] == FLOOR) t[y][5] = DARK;
        // Spinner in catacombs
        t[24][5] = SPINNER;
        // Riddle door guarding west alcove
        carveRoom(t, 8, 22, 4, 3);
        carveHCorridor(t, 7, 23, 10);
        t[23][7] = RIDDLE;

        // ── Fountain alcove (west side of main corridor) ──
        carveRoom(t, 15, 22, 4, 3);
        carveHCorridor(t, 18, 23, 20);
        t[23][17] = FOUNTAIN;

        // ── North dark corridor ──
        for (int y = 12; y < 18; y++)
            if (t[y][20] == FLOOR) t[y][20] = DARK;

        // ── Central boss room ──
        carveRoom(t, 14, 14, 12, 10);          // large room 12x10
        // Pillars
        t[16][16] = WALL; t[16][23] = WALL;
        t[21][16] = WALL; t[21][23] = WALL;
        // Dark zone perimeter inside boss room
        for (int y = 15; y < 17; y++)
            for (int x = 15; x < 25; x++)
                if (t[y][x] == FLOOR) t[y][x] = DARK;
        for (int y = 22; y < 24; y++)
            for (int x = 15; x < 25; x++)
                if (t[y][x] == FLOOR) t[y][x] = DARK;
        // Boss room CUBE reward — center
        t[19][20] = CUBE;
        // South entrance to boss room
        t[23][20] = DOOR;

        // ── North exit from boss room to small chamber ──
        carveVCorridor(t, 20, 8, 14);
        carveRoom(t, 17, 5, 6, 4);
        t[8][20] = DOOR;
        // Dark zone in north chamber
        for (int y = 5; y < 8; y++)
            for (int x = 18; x < 22; x++)
                if (t[y][x] == FLOOR) t[y][x] = DARK;

        // ── Northeast passage ──
        carveHCorridor(t, 23, 10, 35);
        carveRoom(t, 32, 8, 5, 4);
        t[10][32] = DOOR;

        // ── Northwest passage ──
        carveHCorridor(t, 5, 10, 17);
        carveRoom(t, 3, 8, 5, 4);
        t[10][7] = DOOR;
        // Dark zone in northwest
        for (int y = 8; y < 11; y++)
            for (int x = 3; x < 7; x++)
                if (t[y][x] == FLOOR) t[y][x] = DARK;

        saveLevel(t, 3, 35, 35);
        System.out.println("  Level 3: City Undercrypt");
    }

    // == Patch Umbryn overworld entrance ======================================

    static void patchUmbrynEntrance() throws Exception {
        File file = new File("data/overworlds/umbryn.rfmap");
        JsonObject map = gson.fromJson(new InputStreamReader(new FileInputStream(file), "UTF-8"), JsonObject.class);

        JsonArray tiles = map.getAsJsonArray("tiles");

        // Place a 'D' tile at (90, 130) — silver ruins zone, south-west area
        int dungX = 90, dungY = 130;
        tiles.get(dungY).getAsJsonArray().set(dungX, new JsonPrimitive("D"));
        System.out.println("  Placed dungeon entrance 'D' at (" + dungX + "," + dungY + ")");

        // Wire the initialTileStates for the dungeon entrance
        JsonObject tileStates = map.has("initialTileStates")
                ? map.getAsJsonObject("initialTileStates") : new JsonObject();
        String key = dungX + "," + dungY;
        JsonObject state = new JsonObject();
        state.addProperty("overrideId", "\u0000");
        JsonObject data = new JsonObject();
        data.addProperty("dungeonName", GROUP);
        state.add("data", data);
        tileStates.add(key, state);
        map.add("initialTileStates", tileStates);

        try (OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(file), "UTF-8")) { gson.toJson(map, w); }
        System.out.println("  Umbryn entrance wired: " + key + " -> " + GROUP);
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

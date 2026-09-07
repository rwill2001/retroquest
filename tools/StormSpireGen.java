import com.google.gson.*;
import java.io.*;
import java.util.*;

/**
 * Generates the Storm Spire — a 5-level classic-crawler style authored
 * dungeon for Zephyrion (Island 3). Each level is 40x40 tiles.
 *
 * Crawler mechanics used:
 *   - Spinners ('n') — silently rotate the player
 *   - Dark zones ('k') — disable minimap and compass
 *   - Chutes ('c') — drop the player to lower levels
 *   - Riddle doors ('q') — answer to pass
 *   - Teleporter traps ('t') — silent repositioning
 *   - One-way paths — corridors you can enter but not exit the same way
 *   - Non-linear stairs — skip floors via stairwells
 *
 * Run:
 *   javac -cp "target/classes;%USERPROFILE%\.m2\repository\com\google\code\gson\gson\2.10.1\gson-2.10.1.jar" tools/StormSpireGen.java -d tools/
 *   java -cp "tools;target/classes;%USERPROFILE%\.m2\repository\com\google\code\gson\gson\2.10.1\gson-2.10.1.jar" StormSpireGen
 */
public class StormSpireGen {

    static final int W = 40, H = 40;
    static final char WALL = 'W', FLOOR = '.', STAIR = 's', ALTAR = 'A',
            FOUNTAIN = 'f', THRONE = 'H', PIT = 'P', DOOR = 'd',
            TELEPORTER = 't', SPINNER = 'n', DARK = 'k',
            CHUTE = 'c', RIDDLE = 'q', LOCKED_GATE = '[';
    static Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public static void main(String[] args) throws Exception {
        new File("data/dungeons").mkdirs();
        generateLevel1();
        generateLevel2();
        generateLevel3();
        generateLevel4();
        generateLevel5();
        patchZephyrionEntrance();
        System.out.println("Storm Spire generated: 5 levels + Zephyrion patched.");
    }

    // == Level 1: The Gale Vestibule -- introductory level, teaches the mechanics ==

    static void generateLevel1() throws Exception {
        char[][] t = wall(W, H);

        // Entry hall (center-south)
        carveRoom(t, 17, 30, 6, 6);     // 6x6 starting room
        t[33][20] = STAIR;               // entry from overworld + up to surface

        // North corridor from entry
        carveVCorridor(t, 20, 24, 30);

        // ── West wing: Spinner tutorial ──
        carveHCorridor(t, 14, 27, 20);   // branch west
        carveRoom(t, 10, 25, 5, 5);      // spinner room
        t[27][12] = SPINNER;             // first spinner — teaches the mechanic
        carveVCorridor(t, 12, 20, 25);
        carveRoom(t, 10, 17, 5, 4);      // fountain reward
        t[19][12] = FOUNTAIN;
        t[25][14] = DOOR;

        // ── East wing: Riddle door tutorial ──
        carveHCorridor(t, 20, 27, 30);   // branch east
        carveRoom(t, 28, 25, 5, 5);      // antechamber
        t[27][28] = RIDDLE;              // first riddle door
        carveRoom(t, 28, 20, 5, 4);      // altar reward
        t[22][30] = ALTAR;
        carveVCorridor(t, 30, 20, 25);
        t[25][30] = DOOR;

        // ── Central corridor north ──
        carveRoom(t, 17, 20, 6, 5);      // junction room
        t[20][20] = DOOR;

        // ── North maze (simple) ──
        carveVCorridor(t, 20, 10, 20);
        carveHCorridor(t, 15, 15, 25);
        carveHCorridor(t, 15, 10, 25);
        carveVCorridor(t, 15, 10, 15);
        carveVCorridor(t, 25, 10, 15);
        t[12][20] = SPINNER;             // spinner in the maze

        // ── Down stairs room (northwest) ──
        carveRoom(t, 5, 5, 6, 6);
        carveHCorridor(t, 8, 10, 15);
        t[7][8] = STAIR;                 // down to level 2
        t[10][14] = DOOR;

        // ── Throne room (northeast) — optional treasure ──
        carveRoom(t, 30, 5, 6, 6);
        carveHCorridor(t, 25, 10, 33);
        t[7][33] = THRONE;
        t[10][26] = DOOR;

        // ── Down to level 3 shortcut (chute in maze) ──
        t[15][20] = CHUTE;              // drops to level 3!

        saveLevel(t, 1, 20, 33);
        System.out.println("  Level 1: Gale Vestibule — spinner/riddle tutorials, maze, chute to L3");
    }

    // == Level 2: The Howling Galleries -- spinners and dark zones ==

    static void generateLevel2() throws Exception {
        char[][] t = wall(W, H);

        // Up stairs room (northwest, matching L1 down)
        carveRoom(t, 5, 5, 6, 6);
        t[7][8] = STAIR;                 // up to level 1

        // ── Central hub ──
        carveRoom(t, 17, 17, 6, 6);
        carveHCorridor(t, 11, 20, 17);   // connect to up-stairs area
        carveVCorridor(t, 11, 11, 20);
        carveHCorridor(t, 8, 11, 11);
        t[17][20] = DOOR;

        // ── North: Spinner gauntlet ──
        carveVCorridor(t, 20, 8, 17);
        carveHCorridor(t, 17, 8, 23);
        t[10][20] = SPINNER;
        t[8][19] = SPINNER;
        t[8][21] = SPINNER;
        // Reward at end of gauntlet
        carveRoom(t, 17, 3, 6, 4);
        t[5][20] = FOUNTAIN;
        t[7][20] = DOOR;

        // ── East: Dark zone labyrinth ──
        carveHCorridor(t, 23, 20, 35);
        t[20][23] = DOOR;
        // Dark zone area
        carveRoom(t, 27, 17, 8, 8);
        for (int y = 18; y < 24; y++)
            for (int x = 28; x < 34; x++)
                if (t[y][x] == FLOOR) t[y][x] = DARK;
        // Maze within dark zone
        t[19][30] = WALL; t[20][30] = WALL; // internal walls
        t[22][30] = WALL; t[23][30] = WALL;
        t[19][32] = WALL; t[21][32] = WALL;
        t[20][28] = WALL; t[22][28] = WALL;
        // Spinner hidden in darkness
        t[21][31] = SPINNER;
        // Exit to altar
        carveRoom(t, 35, 18, 4, 5);
        t[20][35] = ALTAR;

        // ── South: Teleporter trap maze ──
        carveVCorridor(t, 20, 23, 33);
        t[23][20] = DOOR;
        carveRoom(t, 15, 30, 10, 6);
        t[32][18] = TELEPORTER;          // sends player back to hub
        t[32][22] = TELEPORTER;          // sends player back
        // Real exit (hidden in corner)
        carveVCorridor(t, 15, 30, 36);
        carveRoom(t, 13, 35, 4, 4);
        t[37][15] = THRONE;              // reward throne

        // ── Down stairs (southeast) ──
        carveRoom(t, 33, 33, 5, 5);
        carveHCorridor(t, 23, 35, 33);
        carveVCorridor(t, 23, 23, 35);
        t[35][35] = STAIR;               // down to level 3
        t[35][24] = DOOR;

        saveLevel(t, 2, 8, 7);
        System.out.println("  Level 2: Howling Galleries — spinner gauntlet, dark labyrinth, teleporter traps");
    }

    // == Level 3: The Thundervault -- riddle doors and one-way corridors ==

    static void generateLevel3() throws Exception {
        char[][] t = wall(W, H);

        // Up stairs room (southeast, matching L2 down)
        carveRoom(t, 33, 33, 5, 5);
        t[35][35] = STAIR;               // up to level 2

        // Chute landing room (for L1 chute droppers)
        carveRoom(t, 17, 17, 6, 6);
        // Player lands here from chute — connect to main level
        t[20][20] = FOUNTAIN;             // mercy fountain after the fall

        // ── Central corridor ──
        carveHCorridor(t, 10, 20, 38);
        carveVCorridor(t, 20, 10, 30);

        // ── West: Riddle door gauntlet (3 riddle doors in sequence) ──
        carveRoom(t, 3, 18, 6, 5);
        t[20][9] = RIDDLE;               // riddle 1
        carveRoom(t, 3, 13, 6, 4);
        carveVCorridor(t, 6, 13, 18);
        t[17][6] = DOOR;
        t[13][6] = RIDDLE;               // riddle 2
        carveRoom(t, 3, 8, 6, 4);
        carveVCorridor(t, 6, 8, 13);
        t[8][6] = RIDDLE;                // riddle 3
        carveRoom(t, 3, 3, 6, 4);
        t[5][6] = ALTAR;                 // big reward altar
        carveVCorridor(t, 6, 3, 8);

        // ── East: Dark zone + spinners combo ──
        carveRoom(t, 30, 15, 8, 10);
        for (int y = 16; y < 24; y++)
            for (int x = 31; x < 37; x++)
                if (t[y][x] == FLOOR) t[y][x] = DARK;
        t[18][33] = SPINNER;
        t[22][34] = SPINNER;
        t[20][35] = SPINNER;
        // Connecting corridor
        carveHCorridor(t, 23, 20, 30);
        t[20][24] = DOOR;

        // ── North: Throne with chute trap before it ──
        carveRoom(t, 17, 5, 6, 6);
        carveVCorridor(t, 20, 5, 10);
        t[7][20] = THRONE;
        t[10][20] = DOOR;
        t[8][18] = CHUTE;                // trap! drops to level 5

        // ── South: Down stairs ──
        carveRoom(t, 17, 30, 6, 6);
        carveVCorridor(t, 20, 23, 30);
        t[33][20] = STAIR;               // down to level 4
        t[30][20] = DOOR;

        // Connect up-stairs to main area
        carveHCorridor(t, 23, 35, 35);
        carveVCorridor(t, 23, 20, 35);

        saveLevel(t, 3, 20, 20);
        System.out.println("  Level 3: Thundervault — riddle gauntlet, dark spinner combo, chute trap to L5");
    }

    // == Level 4: The Eye of the Storm -- deceptive level, everything misleads ==

    static void generateLevel4() throws Exception {
        char[][] t = wall(W, H);

        // Up stairs room (center-south, matching L3 down)
        carveRoom(t, 17, 30, 6, 6);
        t[33][20] = STAIR;               // up to level 3

        // ── Symmetrical cross layout (deceptive — looks simple) ──
        // Center room
        carveRoom(t, 17, 17, 6, 6);
        t[20][20] = FOUNTAIN;

        // Four corridors extending from center
        carveVCorridor(t, 20, 5, 17);    // north
        carveVCorridor(t, 20, 23, 35);   // south (connects to stairs)
        carveHCorridor(t, 5, 20, 17);    // west
        carveHCorridor(t, 23, 20, 35);   // east

        // ── North: Spinner room that looks like the south room ──
        carveRoom(t, 17, 3, 6, 5);
        t[5][20] = SPINNER;              // you think you're going south but...
        t[5][18] = SPINNER;
        t[5][22] = SPINNER;
        t[17][20] = DOOR;

        // ── South: (stairway corridor already exists) ──
        t[30][20] = DOOR;

        // ── West: Dark zone covering the entire wing ──
        carveRoom(t, 3, 17, 10, 6);
        for (int y = 18; y < 22; y++)
            for (int x = 4; x < 12; x++)
                if (t[y][x] == FLOOR) t[y][x] = DARK;
        t[20][8] = SPINNER;              // spinner in the dark
        t[20][5] = RIDDLE;               // riddle hidden in darkness
        carveRoom(t, 3, 12, 4, 4);       // riddle reward
        carveVCorridor(t, 5, 12, 17);
        t[14][5] = ALTAR;
        t[17][8] = DOOR;

        // ── East: Chute gauntlet — three consecutive rooms, each with a hidden chute ──
        carveRoom(t, 28, 18, 5, 5);
        t[23][20] = DOOR;
        t[20][30] = CHUTE;               // drops to L5 (hidden among floor tiles)
        carveRoom(t, 34, 18, 5, 5);
        carveHCorridor(t, 33, 20, 34);
        t[20][36] = CHUTE;               // another chute
        // Safe path around chutes
        carveVCorridor(t, 28, 13, 18);
        carveHCorridor(t, 28, 13, 36);
        carveVCorridor(t, 36, 13, 18);
        carveRoom(t, 33, 8, 6, 4);       // throne reward
        t[10][36] = THRONE;
        t[13][36] = DOOR;

        // ── Down stairs (northwest) — non-linear, connects to level 5 ──
        carveRoom(t, 3, 3, 6, 6);
        carveVCorridor(t, 6, 3, 12);
        carveHCorridor(t, 5, 12, 6);     // short link
        t[5][6] = STAIR;                 // down to level 5
        t[8][6] = DOOR;

        // Locked gate guarding stairs
        t[12][6] = LOCKED_GATE;

        saveLevel(t, 4, 20, 33);
        System.out.println("  Level 4: Eye of the Storm — deceptive symmetry, dark riddle, chute gauntlet");
    }

    // == Level 5: The Tempest Core -- boss level, everything at once ==

    static void generateLevel5() throws Exception {
        char[][] t = wall(W, H);

        // Up stairs room (northwest, matching L4 down)
        carveRoom(t, 3, 3, 6, 6);
        t[5][6] = STAIR;                 // up to level 4

        // Chute landing (for those who fell from L3 or L4)
        carveRoom(t, 17, 30, 6, 6);
        t[33][20] = FOUNTAIN;            // mercy fountain
        carveVCorridor(t, 20, 23, 30);

        // ── Central dark maze — the final gauntlet ──
        carveRoom(t, 10, 10, 20, 15);
        // Fill entire room with dark zone
        for (int y = 11; y < 24; y++)
            for (int x = 11; x < 29; x++)
                if (t[y][x] == FLOOR) t[y][x] = DARK;
        // Internal maze walls
        carveWallLine(t, 15, 11, 16);  // vertical divider
        carveWallLine(t, 20, 14, 24);  // vertical divider
        carveWallLine(t, 25, 11, 19);  // vertical divider
        // Openings in dividers
        t[14][15] = DARK; // passage through divider 1
        t[18][20] = DARK; // passage through divider 2
        t[15][25] = DARK; // passage through divider 3
        // Spinners scattered in darkness
        t[12][13] = SPINNER;
        t[16][18] = SPINNER;
        t[14][23] = SPINNER;
        t[20][27] = SPINNER;
        t[22][14] = SPINNER;

        // ── Connections to the dark maze ──
        // From up-stairs (northwest)
        carveHCorridor(t, 9, 5, 14);
        carveVCorridor(t, 14, 5, 10);
        t[10][14] = DOOR;

        // From chute landing (south)
        carveVCorridor(t, 20, 23, 30);
        // (already carved above, just door it)

        // ── Three riddle doors guarding the boss ──
        carveHCorridor(t, 29, 15, 35);
        t[15][29] = RIDDLE;              // exit from dark maze
        carveVCorridor(t, 35, 10, 15);
        t[10][35] = RIDDLE;              // second riddle
        carveHCorridor(t, 30, 10, 35);
        t[10][30] = RIDDLE;              // third riddle

        // ── Boss chamber ──
        carveRoom(t, 28, 3, 10, 6);
        t[6][33] = ALTAR;                // boss encounter altar (Storm Guardian)
        // Treasure alcove behind boss
        carveRoom(t, 30, 1, 6, 2);
        t[1][33] = THRONE;               // reward throne
        t[3][33] = DOOR;

        // Teleporter to exit (post-boss convenience)
        carveRoom(t, 35, 1, 4, 3);
        t[2][37] = TELEPORTER;           // warps to level 1 stairs area

        saveLevel(t, 5, 6, 5);
        System.out.println("  Level 5: Tempest Core — dark spinner maze, triple riddle, boss altar");
    }

    // ── Zephyrion Overworld Patch ─────────────────────────────────────────────

    static void patchZephyrionEntrance() throws Exception {
        File f = new File("data/overworlds/zephyrion.rfmap");
        if (!f.exists()) {
            System.out.println("  WARNING: zephyrion.rfmap not found, skipping patch.");
            return;
        }
        JsonObject zeph;
        try (FileReader r = new FileReader(f)) {
            zeph = JsonParser.parseReader(r).getAsJsonObject();
        }

        // The dungeon entrance 'D' is at (118, 115) on Zephyrion
        JsonObject states = zeph.has("initialTileStates")
                ? zeph.getAsJsonObject("initialTileStates") : new JsonObject();

        JsonObject tileState = new JsonObject();
        tileState.addProperty("overrideId", "\u0000");
        JsonObject data = new JsonObject();
        data.addProperty("dungeonName", "storm_spire");
        tileState.add("data", data);
        states.add("118,115", tileState);

        zeph.add("initialTileStates", states);

        try (FileWriter w = new FileWriter(f)) {
            gson.toJson(zeph, w);
        }
        System.out.println("  Patched Zephyrion D tile at (118,115) with dungeonName=storm_spire");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    static char[][] wall(int w, int h) {
        char[][] t = new char[h][w];
        for (char[] row : t) Arrays.fill(row, WALL);
        return t;
    }

    static void carveRoom(char[][] t, int x, int y, int w, int h) {
        for (int dy = y; dy < y + h && dy < t.length; dy++)
            for (int dx = x; dx < x + w && dx < t[0].length; dx++)
                t[dy][dx] = FLOOR;
    }

    static void carveHCorridor(char[][] t, int fromX, int y, int toX) {
        int lo = Math.min(fromX, toX), hi = Math.max(fromX, toX);
        for (int x = lo; x <= hi && x < t[0].length; x++) t[y][x] = FLOOR;
    }

    static void carveVCorridor(char[][] t, int x, int fromY, int toY) {
        int lo = Math.min(fromY, toY), hi = Math.max(fromY, toY);
        for (int y = lo; y <= hi && y < t.length; y++) t[y][x] = FLOOR;
    }

    /** Places wall tiles in a line — used to create internal maze dividers. */
    static void carveWallLine(char[][] t, int x, int fromY, int toY) {
        int lo = Math.min(fromY, toY), hi = Math.max(fromY, toY);
        for (int y = lo; y <= hi && y < t.length; y++) t[y][x] = WALL;
    }

    static void saveLevel(char[][] t, int level, int entryX, int entryY) throws Exception {
        JsonObject map = new JsonObject();
        map.addProperty("type", "DUNGEON");
        map.addProperty("name", "storm_spire_" + level);
        map.addProperty("width", W);
        map.addProperty("height", H);
        map.addProperty("interiorEntryX", entryX);
        map.addProperty("interiorEntryY", entryY);
        map.addProperty("dungeonLevel", level);
        map.addProperty("dungeonGroup", "storm_spire");

        JsonArray tilesArr = new JsonArray();
        for (int y = 0; y < H; y++) {
            JsonArray row = new JsonArray();
            for (int x = 0; x < W; x++) row.add(String.valueOf(t[y][x]));
            tilesArr.add(row);
        }
        map.add("tiles", tilesArr);

        // Empty spawn difficulty
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

        // Tile states for chutes with custom target levels
        JsonObject tileStates = new JsonObject();
        // Level 1: chute at (20,15) drops to level 3
        if (level == 1) {
            JsonObject chuteState = new JsonObject();
            chuteState.addProperty("overrideId", "\u0000");
            JsonObject chuteData = new JsonObject();
            chuteData.addProperty("chuteLevel", "3");
            chuteState.add("data", chuteData);
            tileStates.add("20,15", chuteState);
        }
        // Level 3: chute at (18,8) drops to level 5
        if (level == 3) {
            JsonObject chuteState = new JsonObject();
            chuteState.addProperty("overrideId", "\u0000");
            JsonObject chuteData = new JsonObject();
            chuteData.addProperty("chuteLevel", "5");
            chuteState.add("data", chuteData);
            tileStates.add("18,8", chuteState);
        }
        // Level 4: chutes at (30,20) and (36,20) drop to level 5
        if (level == 4) {
            for (String pos : new String[]{"30,20", "36,20"}) {
                JsonObject cs = new JsonObject();
                cs.addProperty("overrideId", "\u0000");
                JsonObject cd = new JsonObject();
                cd.addProperty("chuteLevel", "5");
                cs.add("data", cd);
                tileStates.add(pos, cs);
            }
        }
        map.add("initialTileStates", tileStates);

        File out = new File("data/dungeons/storm_spire_" + level + ".rfmap");
        out.getParentFile().mkdirs();
        try (FileWriter w = new FileWriter(out)) { gson.toJson(map, w); }
    }
}

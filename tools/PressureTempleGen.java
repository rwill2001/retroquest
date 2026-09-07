import com.google.gson.*;
import java.io.*;
import java.util.*;

/**
 * Generates the Pressure Temple — a 3-level authored dungeon for Thalorax
 * (Island 5 / The Abyssal Depths). Each level is 40x40 tiles.
 *
 * Story Bible themes (depths 16-18):
 *   Level 1: The Outer Sanctum — introduction, pressure wards, initial encounters
 *   Level 2: The Dream Corridors — Aqualon's memories, dark zones, spinners
 *   Level 3: The Leviathan's Eye — trial altar, boss encounter
 *
 * Mechanics: dark zones (increasing per level), spinners, teleporter traps,
 * pressure damage corridors (pit tiles repurposed), riddle doors.
 *
 * Run:
 *   javac -encoding UTF-8 -cp "target/classes;%USERPROFILE%\.m2\repository\com\google\code\gson\gson\2.10.1\gson-2.10.1.jar" tools/PressureTempleGen.java -d tools/
 *   java -cp "tools;target/classes;%USERPROFILE%\.m2\repository\com\google\code\gson\gson\2.10.1\gson-2.10.1.jar" PressureTempleGen
 */
public class PressureTempleGen {

    static final int W = 40, H = 40;
    static final char WALL = 'W', FLOOR = '.', STAIR = 's', ALTAR = 'A',
            FOUNTAIN = 'f', THRONE = 'H', PIT = 'P', DOOR = 'd',
            TELEPORTER = 't', SPINNER = 'n', DARK = 'k',
            CHUTE = 'c', RIDDLE = 'q', LOCKED_GATE = '[', CUBE = 'g';
    static final String GROUP = "pressure_temple";
    static Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public static void main(String[] args) throws Exception {
        new File("data/dungeons").mkdirs();
        generateLevel1();
        generateLevel2();
        generateLevel3();
        patchThaloraxEntrance();
        System.out.println("Pressure Temple generated: 3 levels + Thalorax entrance patched.");
    }

    // == Level 1: The Outer Sanctum =============================================
    // Introduction to the temple. Pressure wards line the walls. Relatively
    // straightforward layout with a few spinners and a dark zone teaser.

    static void generateLevel1() throws Exception {
        char[][] t = wall(W, H);

        // ── Entry hall (center-south) ──
        carveRoom(t, 17, 30, 6, 6);        // 6x6 starting room
        t[33][20] = STAIR;                  // entry from overworld + up to surface

        // ── Main corridor north ──
        carveVCorridor(t, 20, 20, 30);

        // ── West wing: Pressure Ward Hall ──
        carveHCorridor(t, 12, 25, 20);     // branch west
        carveRoom(t, 8, 22, 6, 6);         // ward hall
        t[25][10] = FOUNTAIN;              // healing fountain
        carveVCorridor(t, 10, 15, 22);     // north from ward hall
        carveRoom(t, 8, 12, 5, 4);         // treasure alcove
        t[14][10] = THRONE;                // loot
        t[15][10] = DOOR;

        // ── East wing: Guard chambers ──
        carveHCorridor(t, 20, 25, 30);     // branch east
        carveRoom(t, 27, 22, 6, 6);        // guard room
        t[25][30] = SPINNER;               // first spinner — disorienting
        carveVCorridor(t, 30, 15, 22);
        carveRoom(t, 28, 12, 5, 4);        // cube room
        t[14][30] = CUBE;                  // glowing cube
        t[15][30] = DOOR;

        // ── Central junction ──
        carveRoom(t, 17, 17, 6, 6);
        t[20][20] = DOOR;

        // ── North: Dark zone preview ──
        carveVCorridor(t, 20, 10, 17);
        carveRoom(t, 16, 7, 8, 4);
        // Small dark zone — just a taste
        for (int y = 8; y < 10; y++)
            for (int x = 17; x < 23; x++)
                if (t[y][x] == FLOOR) t[y][x] = DARK;
        t[9][20] = DOOR;                   // door into dark zone

        // ── Northwest: Riddle door to altar ──
        carveHCorridor(t, 10, 9, 16);
        carveRoom(t, 5, 5, 6, 6);
        t[9][10] = RIDDLE;                 // riddle gate
        t[7][8] = ALTAR;                   // regular altar

        // ── Northeast: Down stairs ──
        carveHCorridor(t, 24, 9, 35);
        carveRoom(t, 32, 5, 6, 6);
        t[8][35] = STAIR;                  // down to level 2
        t[9][32] = DOOR;

        // ── Teleporter trap near junction ──
        t[18][17] = TELEPORTER;            // warps back to entry hall

        saveLevel(t, 1, 20, 33);
        System.out.println("  Level 1: The Outer Sanctum");
    }

    // == Level 2: The Dream Corridors ==========================================
    // Aqualon's memories bleed through the walls. Heavy dark zone usage,
    // spinner gauntlets, and teleporter mazes. One chute drops to level 3.

    static void generateLevel2() throws Exception {
        char[][] t = wall(W, H);

        // ── Up stairs (northeast, matching L1 down) ──
        carveRoom(t, 32, 5, 6, 6);
        t[8][35] = STAIR;                  // up to level 1

        // ── South corridor from stairs ──
        carveVCorridor(t, 35, 11, 20);
        carveHCorridor(t, 25, 20, 35);

        // ── Central hub: The Memory Chamber ──
        carveRoom(t, 17, 17, 8, 8);
        t[20][20] = DOOR;
        // Fountain in center
        t[21][21] = FOUNTAIN;

        // ── West: Dream Corridor (dark zone gauntlet) ──
        carveHCorridor(t, 5, 20, 17);
        t[20][17] = DOOR;
        carveRoom(t, 3, 17, 8, 8);
        // Most of this room is dark
        for (int y = 18; y < 24; y++)
            for (int x = 4; x < 10; x++)
                if (t[y][x] == FLOOR) t[y][x] = DARK;
        // Hidden spinner inside dark zone
        t[21][7] = SPINNER;
        // North exit from dark room
        carveVCorridor(t, 7, 10, 17);
        carveRoom(t, 4, 7, 6, 4);
        t[9][7] = ALTAR;                   // altar reward for navigating dark zone
        t[10][7] = DOOR;

        // ── South from hub: Spinner gauntlet ──
        carveVCorridor(t, 20, 25, 35);
        t[25][20] = DOOR;
        t[28][20] = SPINNER;
        t[31][20] = SPINNER;
        // Branch east to treasure
        carveHCorridor(t, 20, 30, 30);
        carveRoom(t, 28, 28, 5, 5);
        t[30][30] = THRONE;               // treasure throne
        t[30][28] = DOOR;
        // South end: teleporter trap
        carveRoom(t, 17, 34, 6, 4);
        t[36][20] = TELEPORTER;            // warps back to hub
        t[36][18] = TELEPORTER;            // another trap

        // ── East from hub: Riddle passage ──
        carveHCorridor(t, 25, 21, 35);
        t[21][25] = DOOR;
        carveRoom(t, 30, 28, 6, 6);
        t[21][30] = RIDDLE;               // riddle door
        carveVCorridor(t, 33, 22, 28);
        t[30][33] = CUBE;                 // glowing cube reward

        // ── Northwest: Chute to level 3 ──
        carveHCorridor(t, 7, 7, 17);
        carveRoom(t, 15, 3, 5, 5);
        t[5][17] = CHUTE;                 // drops to level 3
        t[7][15] = DOOR;

        // ── Down stairs (southwest) ──
        carveRoom(t, 3, 30, 6, 6);
        carveHCorridor(t, 5, 25, 17);
        carveVCorridor(t, 5, 25, 30);
        t[33][5] = STAIR;                 // down to level 3
        t[25][5] = DOOR;

        saveLevel(t, 2, 35, 8);
        System.out.println("  Level 2: The Dream Corridors");
    }

    // == Level 3: The Leviathan's Eye ==========================================
    // Final level. The trial altar is here. Heavy dark zones, a dangerous
    // maze, and the Altar where Thalorax's trial triggers.

    static void generateLevel3() throws Exception {
        char[][] t = wall(W, H);

        // ── Up stairs (southwest, matching L2 down) ──
        carveRoom(t, 3, 30, 6, 6);
        t[33][5] = STAIR;                  // up to level 2

        // ── Corridor east from stairs ──
        carveHCorridor(t, 9, 33, 20);
        carveVCorridor(t, 20, 25, 33);

        // ── South junction room ──
        carveRoom(t, 17, 23, 6, 5);
        t[25][20] = DOOR;

        // ── West: Dark labyrinth ──
        carveHCorridor(t, 8, 25, 17);
        t[25][17] = DOOR;
        carveRoom(t, 4, 20, 10, 8);
        // Large dark zone maze
        for (int y = 21; y < 27; y++)
            for (int x = 5; x < 13; x++)
                if (t[y][x] == FLOOR) t[y][x] = DARK;
        // Internal walls in dark zone
        t[22][7] = WALL; t[23][7] = WALL;
        t[22][10] = WALL; t[24][10] = WALL;
        t[25][8] = WALL;
        // Spinners in the dark
        t[23][9] = SPINNER;
        t[25][6] = SPINNER;
        // Reward: throne behind dark maze
        carveRoom(t, 4, 16, 5, 5);
        carveVCorridor(t, 7, 16, 20);
        t[18][6] = THRONE;
        t[20][7] = DOOR;

        // ── East: Teleporter maze ──
        carveHCorridor(t, 23, 25, 35);
        t[25][23] = DOOR;
        carveRoom(t, 27, 22, 8, 8);
        t[25][30] = TELEPORTER;            // warps to southwest entry
        t[27][33] = TELEPORTER;            // warps to south junction
        // Real path through
        carveVCorridor(t, 29, 15, 22);
        carveRoom(t, 27, 12, 5, 5);
        t[14][29] = FOUNTAIN;              // healing before boss
        t[15][29] = DOOR;

        // ── Central: Corridor of Visions ──
        carveVCorridor(t, 20, 14, 23);
        t[23][20] = DOOR;
        // Dark corridor — the visions
        for (int y = 15; y < 22; y++)
            if (t[y][20] == FLOOR) t[y][20] = DARK;
        t[18][20] = FLOOR;                 // brief respite in the middle
        t[14][20] = DOOR;

        // ── The Leviathan's Eye: Trial chamber ──
        carveRoom(t, 14, 5, 12, 10);       // large boss room 12x10
        // Pillars
        t[7][16] = WALL; t[7][24] = WALL;
        t[11][16] = WALL; t[11][24] = WALL;
        // The Trial Altar — center of the room
        t[9][20] = ALTAR;                  // *** THALORAX TRIAL ***
        // Locked gate guarding the altar
        t[14][20] = LOCKED_GATE;

        // ── Riddle door shortcut from east wing ──
        carveHCorridor(t, 26, 9, 30);
        t[9][26] = RIDDLE;                 // alternate entry to boss room

        // ── Chute landing zone (from L2 chute) ──
        // Place near the east teleporter maze
        carveRoom(t, 15, 3, 4, 3);         // small landing room
        carveVCorridor(t, 17, 5, 12);
        t[5][17] = FLOOR;                  // chute lands here

        saveLevel(t, 3, 5, 33);
        System.out.println("  Level 3: The Leviathan's Eye");
    }

    // == Patch Thalorax overworld entrance =====================================

    static void patchThaloraxEntrance() throws Exception {
        File file = new File("data/overworlds/thalorax.rfmap");
        JsonObject map = gson.fromJson(new InputStreamReader(new FileInputStream(file), "UTF-8"), JsonObject.class);

        // Find dungeon entrance 'D' tile location on the map
        // The overworld gen should have placed a 'D' tile; find it
        JsonArray tiles = map.getAsJsonArray("tiles");
        int dungX = -1, dungY = -1;
        for (int y = 0; y < tiles.size(); y++) {
            JsonArray row = tiles.get(y).getAsJsonArray();
            for (int x = 0; x < row.size(); x++) {
                if ("D".equals(row.get(x).getAsString())) {
                    dungX = x; dungY = y;
                    break;
                }
            }
            if (dungX >= 0) break;
        }

        if (dungX < 0) {
            // No 'D' tile found — place dungeon entrance at central-south
            // (between towns, per the TODO spec)
            dungX = 115; dungY = 130;
            tiles.get(dungY).getAsJsonArray().set(dungX, new JsonPrimitive("D"));
            System.out.println("  Placed dungeon entrance 'D' at (" + dungX + "," + dungY + ")");
        } else {
            System.out.println("  Found existing dungeon entrance 'D' at (" + dungX + "," + dungY + ")");
        }

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
        System.out.println("  Thalorax entrance wired: " + key + " -> " + GROUP);
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

        // Tile states: chute on level 2 drops to level 3
        JsonObject tileStates = new JsonObject();
        if (level == 2) {
            JsonObject chuteState = new JsonObject();
            chuteState.addProperty("overrideId", "\u0000");
            JsonObject chuteData = new JsonObject();
            chuteData.addProperty("chuteLevel", "3");
            chuteState.add("data", chuteData);
            tileStates.add("17,5", chuteState);  // chute at (17,5) on L2
        }
        map.add("initialTileStates", tileStates);

        File out = new File("data/dungeons/" + GROUP + "_" + level + ".rfmap");
        out.getParentFile().mkdirs();
        try (OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(out), "UTF-8")) { gson.toJson(map, w); }
    }
}

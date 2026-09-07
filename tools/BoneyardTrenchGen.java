import com.google.gson.*;
import java.io.*;
import java.util.*;

/**
 * Generates the Boneyard Trench — a 3-level authored dungeon for Thalorax
 * (Island 5). A natural trench filled with leviathan skeletons whose bones
 * still hold residual dream-energy from Aqualon.
 *
 * Theme: bone corridors, dream-energy hazards, dark zones, ghostly encounters.
 * More exploration/combat focused than the Pressure Temple (no god trial here).
 *
 * Level range: 17-19 (harder than Pressure Temple's 16-18).
 *
 * Run:
 *   javac -encoding UTF-8 -cp "target/classes;%USERPROFILE%\.m2\repository\com\google\code\gson\gson\2.10.1\gson-2.10.1.jar" tools/BoneyardTrenchGen.java -d tools/
 *   java -cp "tools;target/classes;%USERPROFILE%\.m2\repository\com\google\code\gson\gson\2.10.1\gson-2.10.1.jar" BoneyardTrenchGen
 */
public class BoneyardTrenchGen {

    static final int W = 40, H = 40;
    static final char WALL = 'W', FLOOR = '.', STAIR = 's', ALTAR = 'A',
            FOUNTAIN = 'f', THRONE = 'H', PIT = 'P', DOOR = 'd',
            TELEPORTER = 't', SPINNER = 'n', DARK = 'k',
            CHUTE = 'c', RIDDLE = 'q', CUBE = 'g';
    static final String GROUP = "boneyard_trench";
    static Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public static void main(String[] args) throws Exception {
        new File("data/dungeons").mkdirs();
        generateLevel1();
        generateLevel2();
        generateLevel3();
        patchThaloraxEntrance();
        System.out.println("Boneyard Trench generated: 3 levels + Thalorax entrance patched.");
    }

    // == Level 1: The Bone Shelf ================================================
    // Upper level of the trench. Wide corridors carved through leviathan ribs.
    // Relatively open layout, introduces dream-energy dark zones.

    static void generateLevel1() throws Exception {
        char[][] t = wall(W, H);

        // ── Entry chamber (south-center) ──
        carveRoom(t, 16, 32, 8, 6);        // wide entry — feels like descending into a ribcage
        t[35][20] = STAIR;                  // entry from overworld + back up

        // ── Main trench corridor (runs north-south through center) ──
        carveVCorridor(t, 20, 10, 32);
        carveVCorridor(t, 19, 12, 30);      // wide corridor (2 tiles)

        // ── West: Rib Chamber ──
        carveHCorridor(t, 8, 25, 19);
        carveRoom(t, 4, 22, 8, 6);         // large rib room
        t[25][7] = FOUNTAIN;               // healing in the bones
        // Treasure alcove behind door
        carveRoom(t, 4, 17, 4, 4);
        carveVCorridor(t, 6, 17, 22);
        t[21][6] = DOOR;
        t[19][5] = THRONE;                 // bone throne — loot

        // ── East: Dream-Energy Grotto ──
        carveHCorridor(t, 20, 25, 32);
        carveRoom(t, 28, 22, 8, 6);
        // Dark zone — dream energy seeping from bones
        for (int y = 23; y < 27; y++)
            for (int x = 29; x < 35; x++)
                if (t[y][x] == FLOOR) t[y][x] = DARK;
        t[25][32] = SPINNER;               // disorienting dream-spinner
        // Exit from dark zone leads to altar
        carveVCorridor(t, 32, 17, 22);
        carveRoom(t, 30, 14, 5, 4);
        t[16][32] = ALTAR;                 // bone altar — standard divine favor
        t[17][32] = DOOR;

        // ── North: Junction ──
        carveRoom(t, 16, 8, 8, 4);         // wide junction
        t[10][20] = DOOR;

        // ── Northwest: Leviathan Skull ──
        carveHCorridor(t, 6, 10, 16);
        carveRoom(t, 3, 5, 8, 8);          // huge skull room
        // Pillars (teeth)
        t[7][5] = WALL; t[7][9] = WALL;
        t[10][5] = WALL; t[10][9] = WALL;
        t[8][7] = CUBE;                    // glowing cube in the skull
        t[5][7] = PIT;                     // pit — skull's eye socket, drops damage

        // ── Northeast: Stairs down ──
        carveHCorridor(t, 24, 10, 35);
        carveRoom(t, 32, 5, 6, 6);
        t[8][35] = STAIR;                  // down to level 2
        t[10][32] = DOOR;

        // ── Teleporter trap (in main corridor) ──
        t[18][19] = TELEPORTER;            // warps back to entry

        saveLevel(t, 1, 20, 35);
        System.out.println("  Level 1: The Bone Shelf");
    }

    // == Level 2: The Marrow Depths =============================================
    // Deeper into the trench. Tighter corridors, more dark zones.
    // Dream-energy visions cause spinner effects. Teleporter maze section.

    static void generateLevel2() throws Exception {
        char[][] t = wall(W, H);

        // ── Up stairs (northeast, matching L1 down) ──
        carveRoom(t, 32, 5, 6, 6);
        t[8][35] = STAIR;                  // up to level 1

        // ── Corridor south from stairs ──
        carveVCorridor(t, 35, 11, 20);
        carveHCorridor(t, 25, 20, 35);

        // ── Central Marrow Chamber ──
        carveRoom(t, 16, 16, 10, 10);      // large 10x10 room
        // Bone pillars
        t[18][18] = WALL; t[18][24] = WALL;
        t[23][18] = WALL; t[23][24] = WALL;
        t[20][21] = FOUNTAIN;              // dream-tainted fountain
        t[16][21] = DOOR;                  // north entrance

        // ── North: Dark corridor ──
        carveVCorridor(t, 21, 8, 16);
        // Full dark zone corridor
        for (int y = 9; y < 15; y++)
            if (t[y][21] == FLOOR) t[y][21] = DARK;
        carveRoom(t, 18, 4, 6, 5);
        t[6][21] = ALTAR;                  // altar at end of dark path
        t[8][21] = DOOR;

        // ── West: Spinner Gauntlet ──
        carveHCorridor(t, 5, 20, 16);
        t[20][16] = DOOR;
        carveVCorridor(t, 5, 14, 20);
        t[18][5] = SPINNER;
        t[16][5] = SPINNER;
        // Branch west rooms
        carveRoom(t, 3, 11, 5, 4);
        t[13][5] = THRONE;                 // treasure
        t[14][5] = DOOR;
        carveRoom(t, 3, 25, 5, 5);
        carveHCorridor(t, 5, 25, 16);
        t[25][16] = DOOR;
        t[27][5] = CUBE;                   // cube in side room

        // ── East: Teleporter Maze ──
        carveHCorridor(t, 26, 20, 35);
        t[20][26] = DOOR;
        carveRoom(t, 28, 25, 8, 8);
        t[28][32] = TELEPORTER;            // trap — warps to central chamber
        t[31][30] = TELEPORTER;            // trap — warps to entry stairs
        // Real path through southeast corner
        carveVCorridor(t, 34, 22, 33);
        carveRoom(t, 31, 33, 6, 4);
        t[35][34] = FOUNTAIN;              // reward fountain
        t[33][34] = DOOR;

        // ── South: Chute and stairs down ──
        carveVCorridor(t, 21, 26, 36);
        t[26][21] = DOOR;
        carveRoom(t, 18, 34, 6, 4);
        t[36][21] = CHUTE;                 // chute drops to level 3

        // Down stairs (southwest)
        carveRoom(t, 3, 32, 6, 6);
        carveHCorridor(t, 5, 30, 16);
        carveVCorridor(t, 5, 30, 32);
        t[35][5] = STAIR;                  // down to level 3
        t[30][5] = DOOR;

        saveLevel(t, 2, 35, 8);
        System.out.println("  Level 2: The Marrow Depths");
    }

    // == Level 3: The Dream Floor ===============================================
    // Deepest level. The bones here pulse with Aqualon's residual dreams.
    // Extensive dark zones, dangerous encounters. Seraphine's journal is here
    // (represented by a throne/loot spot). No trial — this is exploration/combat.

    static void generateLevel3() throws Exception {
        char[][] t = wall(W, H);

        // ── Up stairs (southwest, matching L2 down) ──
        carveRoom(t, 3, 32, 6, 6);
        t[35][5] = STAIR;                  // up to level 2

        // ── East from stairs ──
        carveHCorridor(t, 9, 35, 20);
        carveVCorridor(t, 20, 27, 35);

        // ── Central Dream Nexus ──
        carveRoom(t, 15, 20, 10, 8);       // large room
        // Dream energy — most of it is dark
        for (int y = 21; y < 27; y++)
            for (int x = 16; x < 24; x++)
                if (t[y][x] == FLOOR) t[y][x] = DARK;
        // Safe islands in the dark
        t[23][20] = FLOOR;                 // center clear
        t[21][17] = FLOOR; t[25][22] = FLOOR;
        // Spinners hidden in the dark
        t[22][19] = SPINNER;
        t[24][21] = SPINNER;
        t[27][20] = DOOR;                  // south entrance

        // ── West: Leviathan Spine Corridor ──
        carveHCorridor(t, 4, 23, 15);
        t[23][15] = DOOR;
        carveVCorridor(t, 4, 15, 23);
        // Bone walls create spine pattern
        t[17][4] = WALL; t[19][4] = WALL; t[21][4] = WALL;
        carveRoom(t, 2, 12, 5, 4);
        t[14][4] = ALTAR;                  // bone altar
        t[15][4] = DOOR;

        // ── Northwest: Seraphine's Cache ──
        carveHCorridor(t, 4, 12, 15);
        carveRoom(t, 12, 8, 6, 5);
        // Dark zone approach
        for (int y = 9; y < 12; y++)
            for (int x = 13; x < 17; x++)
                if (t[y][x] == FLOOR) t[y][x] = DARK;
        t[10][15] = RIDDLE;               // riddle door guards Seraphine's journal
        carveRoom(t, 5, 4, 5, 5);
        carveHCorridor(t, 9, 6, 12);
        t[6][7] = THRONE;                  // Seraphine's journal (seraphine_journal_5 loot)
        t[6][9] = DOOR;

        // ── East: Wraith Warren ──
        carveHCorridor(t, 25, 23, 35);
        t[23][25] = DOOR;
        carveRoom(t, 28, 20, 8, 8);
        // Dark and dangerous
        for (int y = 21; y < 27; y++)
            for (int x = 29; x < 35; x++)
                if (t[y][x] == FLOOR) t[y][x] = DARK;
        t[23][32] = SPINNER;
        t[25][30] = TELEPORTER;            // warps to stairs area (trap)
        // North exit from wraith warren
        carveVCorridor(t, 32, 14, 20);
        carveRoom(t, 30, 10, 5, 5);
        t[12][32] = FOUNTAIN;              // healing after wraiths
        t[14][32] = DOOR;

        // ── North: Aqualon's Coil (final treasure room) ──
        carveVCorridor(t, 20, 12, 20);
        t[20][20] = DOOR;
        carveRoom(t, 15, 4, 10, 8);        // large treasure hall
        // Bone pillars (coils)
        t[6][17] = WALL; t[6][22] = WALL;
        t[9][17] = WALL; t[9][22] = WALL;
        t[7][20] = THRONE;                 // main treasure — Aqualon dream-relic
        t[5][20] = CUBE;                   // glowing cube
        // Two pit traps guarding the treasure
        t[8][18] = PIT; t[8][21] = PIT;

        // ── Chute landing zone (from L2 chute at 21,36) ──
        carveRoom(t, 19, 28, 4, 3);        // small landing near south entrance
        t[29][21] = FLOOR;                 // chute lands here

        saveLevel(t, 3, 5, 35);
        System.out.println("  Level 3: The Dream Floor");
    }

    // == Patch thalorax overworld entrance =====================================

    static void patchThaloraxEntrance() throws Exception {
        File file = new File("data/overworlds/thalorax.rfmap");
        JsonObject map = gson.fromJson(new InputStreamReader(new FileInputStream(file), "UTF-8"), JsonObject.class);
        JsonArray tiles = map.getAsJsonArray("tiles");

        // Place dungeon entrance in the boneyard region (south, west side)
        int dungX = 60, dungY = 160;

        // Find nearest walkable tile to target location
        String bsand = "\uE074";  // bioluminescent sand
        String bfield = "\uE076"; // bone field
        String docean = "\uE070"; // deep ocean floor
        boolean placed = false;
        for (int r = 0; r < 15 && !placed; r++) {
            for (int dy = -r; dy <= r && !placed; dy++) {
                for (int dx = -r; dx <= r && !placed; dx++) {
                    if (Math.abs(dy) != r && Math.abs(dx) != r) continue;
                    int ny = dungY + dy, nx = dungX + dx;
                    if (ny < 0 || ny >= tiles.size() || nx < 0) continue;
                    JsonArray row = tiles.get(ny).getAsJsonArray();
                    if (nx >= row.size()) continue;
                    String ch = row.get(nx).getAsString();
                    if (ch.equals(bfield) || ch.equals(bsand) || ch.equals(docean)) {
                        dungX = nx; dungY = ny;
                        placed = true;
                    }
                }
            }
        }

        tiles.get(dungY).getAsJsonArray().set(dungX, new JsonPrimitive("D"));
        System.out.println("  Placed boneyard entrance 'D' at (" + dungX + "," + dungY + ")");

        // Wire initialTileStates
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
            tileStates.add("21,36", chuteState);  // chute at (21,36) on L2
        }
        map.add("initialTileStates", tileStates);

        File out = new File("data/dungeons/" + GROUP + "_" + level + ".rfmap");
        out.getParentFile().mkdirs();
        try (OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(out), "UTF-8")) { gson.toJson(map, w); }
    }
}

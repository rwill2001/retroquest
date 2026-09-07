import com.google.gson.*;
import java.io.*;
import java.util.*;

/**
 * Generates the Archive of Tears — a 3-level authored dungeon for Umbryn
 * (Island 6 / The Shrouded Isle). Each level is 40x40 tiles.
 *
 * Story themes (depths 20-22):
 *   Level 1: The Outer Archive — entry hall, three wings, memory crystals
 *   Level 2: The Dream Corridors — disorienting city grid, chutes, dark zones
 *   Level 3: Umbryn's Sanctum — circular sanctum, trial altar, boss room
 *
 * Mechanics: dark zones (heavy on L2/L3), spinners, riddle doors,
 * locked gates, chutes (L2 drops to L1), memory crystal thrones.
 *
 * Run:
 *   javac -encoding UTF-8 -cp "target/classes;%USERPROFILE%\.m2\repository\com\google\code\gson\gson\2.10.1\gson-2.10.1.jar" tools/ArchiveOfTearsGen.java -d tools/
 *   java -cp "tools;target/classes;%USERPROFILE%\.m2\repository\com\google\code\gson\gson\2.10.1\gson-2.10.1.jar" ArchiveOfTearsGen
 */
public class ArchiveOfTearsGen {

    static final int W = 40, H = 40;
    static final char WALL = 'W', FLOOR = '.', STAIR = 's', ALTAR = 'A',
            FOUNTAIN = 'f', THRONE = 'H', PIT = 'P', DOOR = 'd',
            TELEPORTER = 't', SPINNER = 'n', DARK = 'k',
            CHUTE = 'c', RIDDLE = 'q', LOCKED_GATE = '[', CUBE = 'g';
    static final String GROUP = "archive_of_tears";
    static Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public static void main(String[] args) throws Exception {
        new File("data/dungeons").mkdirs();
        generateLevel1();
        generateLevel2();
        generateLevel3();
        System.out.println("Archive of Tears generated: 3 levels.");
    }

    // == Level 1: The Outer Archive =============================================
    // Entry from overworld at center-south. Main hall branches into three wings:
    // West = Hall of Names, East = Gallery of Loss, North = central junction.
    // Down stairs in northeast lead to Level 2.

    static void generateLevel1() throws Exception {
        char[][] t = wall(W, H);

        // ── Entry hall (center-south) ──
        carveRoom(t, 17, 30, 7, 7);              // 7x7 starting room
        t[33][20] = STAIR;                        // entry from overworld

        // ── Main corridor north from entry ──
        carveVCorridor(t, 20, 18, 30);

        // ── Central junction (north of main corridor) ──
        carveRoom(t, 17, 15, 7, 4);              // junction room
        t[18][20] = DOOR;                         // door into junction from south
        t[17][20] = SPINNER;                      // spinner trap in junction

        // ── West wing: Hall of Names ──
        carveHCorridor(t, 5, 17, 17);            // corridor west from junction
        t[17][17] = DOOR;                         // door into west wing

        // Long corridor with alcoves (the Hall of Names)
        carveRoom(t, 3, 14, 10, 7);              // main hall room
        // Alcove 1 (north)
        carveRoom(t, 3, 10, 4, 4);
        carveVCorridor(t, 5, 10, 14);
        // Alcove 2 (south-west)
        carveRoom(t, 9, 21, 4, 4);
        carveVCorridor(t, 11, 20, 21);

        // Memory crystal #1 (throne) in north alcove
        t[11][5] = THRONE;
        t[13][5] = DOOR;

        // Dark zones representing fading memories
        for (int y = 15; y < 20; y++)
            for (int x = 4; x < 8; x++)
                if (t[y][x] == FLOOR) t[y][x] = DARK;

        // Altar in west wing (hidden behind dark zone)
        carveRoom(t, 3, 24, 5, 5);
        carveVCorridor(t, 5, 20, 24);
        t[26][5] = ALTAR;
        t[24][5] = DOOR;

        // ── East wing: Gallery of Loss ──
        carveHCorridor(t, 23, 17, 35);           // corridor east from junction
        t[17][23] = DOOR;                         // door into east wing

        // Gallery rooms connected by riddle door
        carveRoom(t, 27, 14, 6, 7);              // outer gallery room
        carveRoom(t, 33, 14, 5, 5);              // inner gallery room (past riddle)
        t[17][33] = RIDDLE;                       // riddle door between galleries

        // Fountain for healing in inner gallery
        t[16][35] = FOUNTAIN;

        // Dark zones in gallery
        for (int y = 15; y < 18; y++)
            for (int x = 28; x < 32; x++)
                if (t[y][x] == FLOOR) t[y][x] = DARK;

        // ── North passage to down stairs ──
        carveVCorridor(t, 20, 8, 15);
        t[15][20] = DOOR;                         // door north from junction

        // Northeast room with down stairs
        carveHCorridor(t, 20, 8, 35);
        carveRoom(t, 32, 5, 6, 6);
        t[8][35] = STAIR;                         // down to level 2
        t[8][32] = DOOR;

        saveLevel(t, 1, 20, 33);
        System.out.println("  Level 1: The Outer Archive");
    }

    // == Level 2: The Dream Corridors ==========================================
    // City grid layout, heavy dark zones, spinners for disorientation.
    // Two chutes drop back to Level 1. Teleporter pair connects city halves.
    // Down stairs in southwest to Level 3.

    static void generateLevel2() throws Exception {
        char[][] t = wall(W, H);

        // ── Up stairs (northeast, matching L1 down) ──
        carveRoom(t, 32, 5, 6, 6);
        t[8][35] = STAIR;                         // up to level 1

        // ── City grid: 4x4 block of rooms with corridors ──
        // Grid anchors at (3,3) with rooms spaced 9 apart
        // Row 0: y=3, Row 1: y=12, Row 2: y=21, Row 3: y=30
        // Col 0: x=3, Col 1: x=12, Col 2: x=21, Col 3: x=30

        // Row 0
        carveRoom(t, 3, 3, 5, 5);                // (0,0)
        carveRoom(t, 12, 3, 5, 5);               // (1,0)
        carveRoom(t, 21, 3, 5, 5);               // (2,0)
        // (3,0) is the up-stairs room already carved

        // Row 1
        carveRoom(t, 3, 12, 5, 5);               // (0,1)
        carveRoom(t, 12, 12, 5, 5);              // (1,1)
        carveRoom(t, 21, 12, 5, 5);              // (2,1)
        carveRoom(t, 30, 12, 5, 5);              // (3,1)

        // Row 2
        carveRoom(t, 3, 21, 5, 5);               // (0,2)
        carveRoom(t, 12, 21, 5, 5);              // (1,2)
        carveRoom(t, 21, 21, 5, 5);              // (2,2)
        carveRoom(t, 30, 21, 5, 5);              // (3,2)

        // Row 3
        carveRoom(t, 3, 30, 5, 5);               // (0,3) — down stairs room
        carveRoom(t, 12, 30, 5, 5);              // (1,3)
        carveRoom(t, 21, 30, 5, 5);              // (2,3)
        carveRoom(t, 30, 30, 5, 5);              // (3,3)

        // Horizontal corridors (connecting columns within rows)
        // Row 0
        carveHCorridor(t, 8, 5, 12);
        carveHCorridor(t, 17, 5, 21);
        carveHCorridor(t, 26, 5, 32);
        // Row 1
        carveHCorridor(t, 8, 14, 12);
        carveHCorridor(t, 17, 14, 21);
        carveHCorridor(t, 26, 14, 30);
        // Row 2
        carveHCorridor(t, 8, 23, 12);
        carveHCorridor(t, 17, 23, 21);
        carveHCorridor(t, 26, 23, 30);
        // Row 3
        carveHCorridor(t, 8, 32, 12);
        carveHCorridor(t, 17, 32, 21);
        carveHCorridor(t, 26, 32, 30);

        // Vertical corridors (connecting rows within columns)
        // Col 0
        carveVCorridor(t, 5, 8, 12);
        carveVCorridor(t, 5, 17, 21);
        carveVCorridor(t, 5, 26, 30);
        // Col 1
        carveVCorridor(t, 14, 8, 12);
        carveVCorridor(t, 14, 17, 21);
        carveVCorridor(t, 14, 26, 30);
        // Col 2
        carveVCorridor(t, 23, 8, 12);
        carveVCorridor(t, 23, 17, 21);
        carveVCorridor(t, 23, 26, 30);
        // Col 3
        carveVCorridor(t, 32, 17, 21);
        carveVCorridor(t, 32, 26, 30);

        // Connect stairs room to grid
        carveHCorridor(t, 26, 8, 32);            // connect (2,0) to stairs room

        // ── Doors at key junctions ──
        t[5][8] = DOOR;
        t[14][8] = DOOR;
        t[23][8] = DOOR;
        t[32][8] = DOOR;
        t[5][17] = DOOR;
        t[14][17] = DOOR;
        t[14][26] = DOOR;
        t[32][17] = DOOR;

        // ── Dark zones (entire blocks are dark) ──
        // Room (0,0) is fully dark
        for (int y = 3; y < 8; y++)
            for (int x = 3; x < 8; x++)
                if (t[y][x] == FLOOR) t[y][x] = DARK;

        // Room (1,1) is fully dark
        for (int y = 12; y < 17; y++)
            for (int x = 12; x < 17; x++)
                if (t[y][x] == FLOOR) t[y][x] = DARK;

        // Room (2,2) is fully dark
        for (int y = 21; y < 26; y++)
            for (int x = 21; x < 26; x++)
                if (t[y][x] == FLOOR) t[y][x] = DARK;

        // Room (3,3) is fully dark
        for (int y = 30; y < 35; y++)
            for (int x = 30; x < 35; x++)
                if (t[y][x] == FLOOR) t[y][x] = DARK;

        // Dark corridor between (0,1) and (1,1)
        for (int x = 8; x < 12; x++)
            if (t[14][x] == FLOOR) t[14][x] = DARK;

        // Dark corridor between (2,1) and (3,1)
        for (int x = 26; x < 30; x++)
            if (t[14][x] == FLOOR) t[14][x] = DARK;

        // ── Spinners (disorienting city) ──
        t[5][14] = SPINNER;                       // in room (1,0)
        t[23][14] = SPINNER;                      // in room (1,2)
        t[32][23] = SPINNER;                      // in corridor row 3

        // ── Chutes (drop back to Level 1) ──
        t[5][5] = CHUTE;                          // in dark room (0,0) at (5,5)
        t[32][32] = CHUTE;                        // in dark room (3,3) at (32,32)

        // ── Teleporter pair connecting city halves ──
        t[14][5] = TELEPORTER;                    // room (0,1) -> warps to (3,2)
        t[23][32] = TELEPORTER;                   // room (3,2) -> warps to (0,1)

        // ── Fountain in room (2,1) ──
        t[14][23] = FOUNTAIN;

        // ── Memory crystal #2: Throne in room (1,0) ──
        t[5][13] = THRONE;

        // ── Hidden chamber with locked gate ──
        // Room (3,1) has a locked gate leading to a hidden alcove
        carveRoom(t, 36, 12, 3, 3);              // hidden room behind locked gate
        t[13][35] = LOCKED_GATE;                  // locked gate into hidden room
        // eighth_dream_evidence location — memory throne
        t[13][37] = THRONE;

        // ── Riddle door guarding room (0,2) ──
        t[21][5] = RIDDLE;                        // riddle to enter room (0,2)

        // ── Down stairs (southwest) ──
        t[33][5] = STAIR;                         // down to level 3

        saveLevel(t, 2, 35, 8);
        System.out.println("  Level 2: The Dream Corridors");
    }

    // == Level 3: Umbryn's Sanctum =============================================
    // Smaller, focused layout. Outer guardian ring, inner memory ring,
    // central boss room with the trial altar.

    static void generateLevel3() throws Exception {
        char[][] t = wall(W, H);

        // ── Up stairs (southwest, matching L2 down) ──
        carveRoom(t, 3, 30, 6, 6);
        t[33][5] = STAIR;                         // up to level 2

        // ── Corridor from stairs to outer ring ──
        carveHCorridor(t, 9, 33, 20);
        carveVCorridor(t, 20, 28, 33);

        // ── Outer ring: Guardian corridors ──
        // North segment
        carveHCorridor(t, 10, 8, 30);
        // South segment
        carveHCorridor(t, 10, 28, 30);
        // West segment
        carveVCorridor(t, 10, 8, 28);
        // East segment
        carveVCorridor(t, 30, 8, 28);

        // Corner rooms on outer ring
        carveRoom(t, 8, 6, 5, 5);                // NW corner
        carveRoom(t, 28, 6, 5, 5);               // NE corner
        carveRoom(t, 8, 26, 5, 5);               // SW corner
        carveRoom(t, 28, 26, 5, 5);              // SE corner

        // Dark zones in outer ring corridors
        // North corridor dark zone
        for (int x = 14; x < 20; x++)
            t[8][x] = DARK;
        // South corridor dark zone
        for (int x = 22; x < 28; x++)
            t[28][x] = DARK;
        // West corridor dark zone
        for (int y = 14; y < 20; y++)
            t[y][10] = DARK;
        // East corridor dark zone
        for (int y = 20; y < 26; y++)
            t[y][30] = DARK;

        // Doors at outer ring entries
        t[28][20] = DOOR;                         // south entry from stairs corridor
        t[8][10] = DOOR;                          // NW corner entry
        t[8][30] = DOOR;                          // NE corner entry

        // ── Fountain in NE corner room (healing before boss) ──
        t[8][30] = FOUNTAIN;                      // overwrite door with fountain
        t[9][30] = DOOR;                          // door slightly south instead

        // ── Inner ring: Memory corridors ──
        // North inner segment
        carveHCorridor(t, 15, 13, 25);
        // South inner segment
        carveHCorridor(t, 15, 23, 25);
        // West inner segment
        carveVCorridor(t, 15, 13, 23);
        // East inner segment
        carveVCorridor(t, 25, 13, 23);

        // Connect outer ring to inner ring
        carveHCorridor(t, 10, 18, 15);           // west connection
        carveHCorridor(t, 25, 18, 30);           // east connection
        carveVCorridor(t, 20, 8, 13);            // north connection
        carveVCorridor(t, 20, 23, 28);           // south connection

        // Memory crystal #3 (throne) in inner ring
        t[13][20] = THRONE;

        // Riddle door guarding inner area from south
        t[23][20] = RIDDLE;

        // ── Central boss room: 12x10 ──
        carveRoom(t, 14, 14, 12, 8);

        // Pillars in the boss room (wall tiles for aesthetics)
        t[15][16] = WALL; t[15][24] = WALL;
        t[17][16] = WALL; t[17][24] = WALL;
        t[19][16] = WALL; t[19][24] = WALL;
        t[15][20] = WALL; t[19][20] = WALL;

        // The Trial Altar — center of the boss room
        t[17][20] = ALTAR;                        // *** UMBRYN TRIAL ***

        // Locked gate guarding the altar room from the south
        t[22][20] = LOCKED_GATE;

        saveLevel(t, 3, 5, 33);
        System.out.println("  Level 3: Umbryn's Sanctum");
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

        // Tile states: chutes on level 2 drop back to level 1
        JsonObject tileStates = new JsonObject();
        if (level == 2) {
            // Chute at (5,5) in dark room (0,0)
            JsonObject chuteState1 = new JsonObject();
            chuteState1.addProperty("overrideId", "\u0000");
            JsonObject chuteData1 = new JsonObject();
            chuteData1.addProperty("chuteLevel", "1");
            chuteState1.add("data", chuteData1);
            tileStates.add("5,5", chuteState1);

            // Chute at (32,32) in dark room (3,3)
            JsonObject chuteState2 = new JsonObject();
            chuteState2.addProperty("overrideId", "\u0000");
            JsonObject chuteData2 = new JsonObject();
            chuteData2.addProperty("chuteLevel", "1");
            chuteState2.add("data", chuteData2);
            tileStates.add("32,32", chuteState2);
        }
        map.add("initialTileStates", tileStates);

        File out = new File("data/dungeons/" + GROUP + "_" + level + ".rfmap");
        out.getParentFile().mkdirs();
        try (OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(out), "UTF-8")) { gson.toJson(map, w); }
    }
}

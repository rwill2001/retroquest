import com.google.gson.*;
import java.io.*;
import java.util.*;

/**
 * Generates the Cradle of Shards — an 8-level endgame mega-dungeon.
 * Each level is 40x40 tiles. Levels 1-7 represent each god's domain;
 * Level 8 is the Heart of the Cradle with the endgame trial altar.
 *
 * Depths 28-35. dungeonGroup = "cradle_of_shards"
 *
 * Run:
 *   export JAVA_HOME="$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")")"
 *   GSON_JAR=$(find $HOME/.m2 -name "gson-*.jar" | head -1)
 *   CP="target/classes;$GSON_JAR"
 *   javac -encoding UTF-8 -cp "$CP" tools/CradleOfShardsGen.java -d tools/
 *   java -cp "tools;$CP" CradleOfShardsGen
 */
public class CradleOfShardsGen {

    static final int W = 40, H = 40;
    static final char WALL = '#', FLOOR = '.', STAIR = 's', ALTAR = 'A',
            FOUNTAIN = 'F', THRONE = 'P', CHEST = 'C',
            TELEPORTER = 't', SPINNER = 'n', DARK = 'k',
            RIDDLE = 'q';
    static final String GROUP = "cradle_of_shards";
    static Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public static void main(String[] args) throws Exception {
        new File("data/dungeons").mkdirs();
        generateLevel1();
        generateLevel2();
        generateLevel3();
        generateLevel4();
        generateLevel5();
        generateLevel6();
        generateLevel7();
        generateLevel8();
        System.out.println("Cradle of Shards generated: 8 levels.");
    }

    // == Level 1 (depth 28) — Lirandel's Section ================================
    // Flowing corridor patterns, fountains, soft/open rooms. Entry at south wall.

    static void generateLevel1() throws Exception {
        char[][] t = wall(W, H);

        // Entry room at south center
        carveRoom(t, 16, 33, 8, 6);
        t[37][20] = STAIR;  // entry from overworld

        // Main corridor north from entry
        carveVCorridor(t, 20, 15, 33);

        // Large open gathering hall (Lirandel's flowing style)
        carveRoom(t, 12, 15, 16, 10);

        // West flowing corridor with alcoves
        carveHCorridor(t, 5, 20, 12);
        carveRoom(t, 3, 17, 5, 7);   // west alcove room
        t[20][5] = FOUNTAIN;          // fountain in west alcove

        // East flowing corridor with alcoves
        carveHCorridor(t, 28, 20, 35);
        carveRoom(t, 32, 17, 6, 7);  // east alcove room
        t[20][35] = FOUNTAIN;         // fountain in east alcove

        // North passage from hall to upper rooms
        carveVCorridor(t, 20, 5, 15);

        // Upper west room — peaceful chamber
        carveHCorridor(t, 8, 10, 20);
        carveRoom(t, 5, 7, 8, 7);
        t[10][8] = FOUNTAIN;          // third fountain

        // Upper east room — meditation room
        carveHCorridor(t, 20, 10, 32);
        carveRoom(t, 28, 7, 8, 7);
        t[10][32] = CHEST;

        // North stair room
        carveRoom(t, 17, 3, 7, 5);
        carveVCorridor(t, 20, 3, 7);
        t[4][20] = STAIR;             // up to level 2

        saveLevel(t, 1, 20, 37);
        System.out.println("  Level 1 (depth 28): Lirandel's Section");
    }

    // == Level 2 (depth 29) — Pyralis's Section ==================================
    // Forge-like rooms, angular corridors, altars, riddle doors.

    static void generateLevel2() throws Exception {
        char[][] t = wall(W, H);

        // Entry from level 1 (south)
        carveRoom(t, 17, 33, 7, 6);
        t[36][20] = STAIR;            // down to level 1

        // Angular main corridor north
        carveVCorridor(t, 20, 24, 33);
        carveHCorridor(t, 15, 24, 25);
        carveVCorridor(t, 15, 18, 24);
        carveHCorridor(t, 15, 18, 25);
        carveVCorridor(t, 25, 12, 18);

        // Forge room (west) — angular rectangular
        carveRoom(t, 3, 20, 10, 8);
        carveHCorridor(t, 13, 24, 15);
        t[23][8] = ALTAR;             // altar in forge room
        t[24][5] = RIDDLE;            // riddle door guarding inner forge

        // Inner forge chamber
        carveRoom(t, 3, 25, 6, 5);
        t[27][5] = ALTAR;             // second altar deep in forge

        // East angular workshop
        carveRoom(t, 28, 20, 10, 8);
        carveHCorridor(t, 25, 24, 28);
        t[23][33] = CHEST;

        // North corridor system
        carveVCorridor(t, 25, 5, 12);
        carveHCorridor(t, 10, 12, 25);

        // Northwest angular room
        carveRoom(t, 5, 5, 8, 8);
        carveVCorridor(t, 10, 5, 12);
        t[8][8] = RIDDLE;             // riddle door in NW room
        t[9][10] = ALTAR;             // third altar

        // Northeast room with stair
        carveRoom(t, 28, 5, 8, 8);
        carveHCorridor(t, 25, 9, 28);
        t[8][32] = STAIR;             // up to level 3

        saveLevel(t, 2, 20, 36);
        System.out.println("  Level 2 (depth 29): Pyralis's Section");
    }

    // == Level 3 (depth 30) — Zephyrion's Section ================================
    // Irregular/twisting corridors, spinners, open chambers.

    static void generateLevel3() throws Exception {
        char[][] t = wall(W, H);

        // Entry from level 2 (south-east)
        carveRoom(t, 30, 33, 6, 6);
        t[36][33] = STAIR;            // down to level 2

        // Twisting entry corridor
        carveVCorridor(t, 33, 28, 33);
        carveHCorridor(t, 20, 28, 33);
        carveVCorridor(t, 20, 22, 28);

        // Central open storm chamber
        carveRoom(t, 13, 15, 14, 10);
        carveVCorridor(t, 20, 15, 22);
        t[19][20] = SPINNER;           // spinner in center

        // West twisting passage
        carveHCorridor(t, 5, 20, 13);
        carveVCorridor(t, 5, 14, 20);
        carveHCorridor(t, 5, 14, 10);

        // Southwest chamber
        carveRoom(t, 3, 28, 8, 7);
        carveVCorridor(t, 7, 20, 28);
        t[31][5] = SPINNER;            // spinner trap
        t[30][8] = CHEST;

        // Northwest winding passage
        carveRoom(t, 3, 5, 9, 7);
        carveVCorridor(t, 7, 5, 14);
        t[8][7] = SPINNER;             // third spinner
        t[7][5] = FOUNTAIN;

        // East irregular corridor
        carveHCorridor(t, 27, 20, 37);
        carveVCorridor(t, 37, 10, 20);
        carveHCorridor(t, 30, 10, 37);

        // Northeast open chamber
        carveRoom(t, 28, 5, 10, 7);
        carveVCorridor(t, 33, 5, 10);
        t[8][33] = CHEST;

        // North stair room
        carveRoom(t, 17, 3, 7, 5);
        carveVCorridor(t, 20, 3, 15);
        t[4][20] = STAIR;             // up to level 4

        saveLevel(t, 3, 33, 36);
        System.out.println("  Level 3 (depth 30): Zephyrion's Section");
    }

    // == Level 4 (depth 31) — Sylvandar's Section ================================
    // Organic layouts with rounded rooms, fountains, chests.

    static void generateLevel4() throws Exception {
        char[][] t = wall(W, H);

        // Entry from level 3 (south center)
        carveRoom(t, 17, 34, 7, 5);
        t[37][20] = STAIR;            // down to level 3

        // Organic corridor north
        carveVCorridor(t, 20, 26, 34);

        // Large organic central room (rounded feel via overlapping rooms)
        carveRoom(t, 14, 20, 12, 8);
        carveRoom(t, 16, 18, 8, 12);  // overlap for rounded shape
        t[24][20] = FOUNTAIN;          // fountain in center

        // West organic branch
        carveHCorridor(t, 5, 24, 14);
        carveRoom(t, 3, 21, 7, 7);    // organic west room
        carveRoom(t, 4, 22, 5, 5);    // inner overlap for roundness
        t[24][6] = CHEST;             // chest in organic room
        t[23][5] = FOUNTAIN;          // fountain

        // East organic branch
        carveHCorridor(t, 26, 24, 35);
        carveRoom(t, 30, 21, 7, 7);
        carveRoom(t, 31, 22, 5, 5);
        t[24][34] = CHEST;            // chest
        t[25][33] = FOUNTAIN;         // third fountain

        // North passage through grove
        carveVCorridor(t, 20, 10, 18);

        // Northwest grove room
        carveRoom(t, 5, 8, 9, 7);
        carveHCorridor(t, 14, 12, 20);
        t[11][9] = CHEST;             // hidden chest in grove

        // Northeast grove room
        carveRoom(t, 27, 8, 9, 7);
        carveHCorridor(t, 20, 12, 27);
        t[11][31] = FOUNTAIN;

        // North stair room
        carveRoom(t, 17, 3, 7, 5);
        carveVCorridor(t, 20, 3, 10);
        t[4][20] = STAIR;             // up to level 5

        saveLevel(t, 4, 20, 37);
        System.out.println("  Level 4 (depth 31): Sylvandar's Section");
    }

    // == Level 5 (depth 32) — Thalorax's Section =================================
    // Dense corridors, dark zones, narrow passages, thrones.

    static void generateLevel5() throws Exception {
        char[][] t = wall(W, H);

        // Entry from level 4 (south center)
        carveRoom(t, 18, 35, 5, 4);
        t[37][20] = STAIR;            // down to level 4

        // Dense corridor network south
        carveVCorridor(t, 20, 28, 35);
        carveHCorridor(t, 10, 30, 30);
        carveVCorridor(t, 10, 28, 30);
        carveVCorridor(t, 30, 28, 30);

        // South chambers (narrow, dense)
        carveRoom(t, 8, 26, 5, 3);
        carveRoom(t, 28, 26, 5, 3);

        // Dark zones in south corridors
        for (int x = 12; x < 18; x++) t[30][x] = DARK;
        for (int x = 22; x < 28; x++) t[30][x] = DARK;

        // Central throne room
        carveRoom(t, 14, 18, 12, 8);
        carveVCorridor(t, 20, 18, 28);
        t[22][20] = THRONE;           // throne in center
        t[20][16] = THRONE;           // second throne

        // Dark zone corridors flanking throne room
        carveVCorridor(t, 12, 18, 26);
        for (int y = 19; y < 25; y++) t[y][12] = DARK;
        carveVCorridor(t, 28, 18, 26);
        for (int y = 19; y < 25; y++) t[y][28] = DARK;

        // West narrow passage system
        carveHCorridor(t, 3, 22, 12);
        carveRoom(t, 3, 20, 5, 5);
        t[22][5] = DARK;
        t[21][4] = THRONE;            // third throne, hidden in dark

        // East narrow passage system
        carveHCorridor(t, 28, 22, 37);
        carveRoom(t, 33, 20, 5, 5);
        for (int x = 34; x < 37; x++) t[22][x] = DARK;

        // North passage (narrow)
        carveVCorridor(t, 20, 8, 18);

        // Northwest dense room
        carveRoom(t, 5, 6, 8, 6);
        carveHCorridor(t, 13, 10, 20);
        for (int x = 6; x < 12; x++) t[8][x] = DARK;

        // Northeast dense room
        carveRoom(t, 28, 6, 8, 6);
        carveHCorridor(t, 20, 10, 28);

        // North stair room
        carveRoom(t, 17, 3, 7, 5);
        carveVCorridor(t, 20, 3, 8);
        t[4][20] = STAIR;             // up to level 6

        saveLevel(t, 5, 20, 37);
        System.out.println("  Level 5 (depth 32): Thalorax's Section");
    }

    // == Level 6 (depth 33) — Umbryn's Section ===================================
    // Symmetric/archival layout, riddle doors, thrones.

    static void generateLevel6() throws Exception {
        char[][] t = wall(W, H);

        // Entry from level 5 (south center)
        carveRoom(t, 17, 34, 7, 5);
        t[37][20] = STAIR;            // down to level 5

        // Symmetric main axis (vertical)
        carveVCorridor(t, 20, 10, 34);

        // South symmetric rooms
        carveRoom(t, 10, 28, 8, 6);   // SW room
        carveHCorridor(t, 10, 30, 20);
        carveRoom(t, 22, 28, 8, 6);   // SE room (mirror)
        carveHCorridor(t, 20, 30, 30);
        t[30][14] = THRONE;           // throne in SW
        t[30][26] = THRONE;           // throne in SE (symmetric)

        // Central archive hall (symmetric)
        carveRoom(t, 10, 18, 20, 8);
        t[22][15] = RIDDLE;           // riddle door west side
        t[22][25] = RIDDLE;           // riddle door east side (symmetric)

        // West archive wing
        carveRoom(t, 3, 18, 7, 8);
        carveHCorridor(t, 3, 22, 10);
        t[21][6] = THRONE;            // third throne

        // East archive wing (symmetric mirror)
        carveRoom(t, 30, 18, 7, 8);
        carveHCorridor(t, 30, 22, 37);
        t[21][34] = THRONE;           // fourth throne (symmetric)

        // North passage
        carveVCorridor(t, 20, 5, 18);

        // Northwest symmetric room
        carveRoom(t, 5, 6, 8, 7);
        carveHCorridor(t, 13, 10, 20);
        t[9][9] = RIDDLE;             // riddle door

        // Northeast symmetric room (mirror)
        carveRoom(t, 27, 6, 8, 7);
        carveHCorridor(t, 20, 10, 27);
        t[9][31] = RIDDLE;            // riddle door (symmetric)

        // North stair room
        carveRoom(t, 17, 2, 7, 5);
        carveVCorridor(t, 20, 2, 6);
        t[3][20] = STAIR;             // up to level 7

        saveLevel(t, 6, 20, 37);
        System.out.println("  Level 6 (depth 33): Umbryn's Section");
    }

    // == Level 7 (depth 34) — Bellorak's Section =================================
    // Arena-like open areas, spinners, many altars.

    static void generateLevel7() throws Exception {
        char[][] t = wall(W, H);

        // Entry from level 6 (south center)
        carveRoom(t, 17, 34, 7, 5);
        t[37][20] = STAIR;            // down to level 6

        // Corridor to arena
        carveVCorridor(t, 20, 26, 34);

        // Grand arena (large open area)
        carveRoom(t, 8, 14, 24, 14);

        // Arena altars (scattered around the arena)
        t[17][12] = ALTAR;
        t[17][28] = ALTAR;
        t[24][12] = ALTAR;
        t[24][28] = ALTAR;
        t[20][20] = ALTAR;            // center altar

        // Spinners in the arena
        t[16][16] = SPINNER;
        t[16][24] = SPINNER;
        t[25][16] = SPINNER;
        t[25][24] = SPINNER;

        // West side chamber
        carveRoom(t, 2, 16, 5, 8);
        carveHCorridor(t, 2, 20, 8);
        t[19][4] = ALTAR;             // sixth altar

        // East side chamber
        carveRoom(t, 33, 16, 5, 8);
        carveHCorridor(t, 32, 20, 38);
        t[19][35] = ALTAR;            // seventh altar

        // North passage from arena to stairs
        carveVCorridor(t, 20, 5, 14);

        // Northwest antechamber
        carveRoom(t, 5, 5, 8, 7);
        carveHCorridor(t, 13, 8, 20);
        t[8][9] = SPINNER;            // fifth spinner
        t[9][7] = ALTAR;              // eighth altar

        // Northeast antechamber
        carveRoom(t, 28, 5, 8, 7);
        carveHCorridor(t, 20, 8, 28);
        t[8][32] = SPINNER;           // sixth spinner

        // North stair room
        carveRoom(t, 17, 2, 7, 4);
        carveVCorridor(t, 20, 2, 5);
        t[3][20] = STAIR;             // up to level 8

        saveLevel(t, 7, 20, 37);
        System.out.println("  Level 7 (depth 34): Bellorak's Section");
    }

    // == Level 8 (depth 35) — Heart of the Cradle ================================
    // Single large open chamber (20x20+), one altar at exact center.
    // Triggers handleCradleOfShardsTrial(). Minimal obstacles.

    static void generateLevel8() throws Exception {
        char[][] t = wall(W, H);

        // Entry from level 7 (south center)
        carveRoom(t, 18, 34, 5, 5);
        t[37][20] = STAIR;            // down to level 7

        // Corridor into the Heart
        carveVCorridor(t, 20, 30, 34);

        // The Heart chamber — 24x24 open space centered in the map
        carveRoom(t, 8, 6, 24, 24);

        // The Trial Altar — exact center of the Heart
        t[18][20] = ALTAR;            // *** CRADLE OF SHARDS ENDGAME TRIAL ***

        saveLevel(t, 8, 20, 37);
        System.out.println("  Level 8 (depth 35): Heart of the Cradle");
    }

    // == Utility methods =========================================================

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
        map.add("initialTileStates", new JsonObject());

        File out = new File("data/dungeons/" + GROUP + "_" + level + ".rfmap");
        out.getParentFile().mkdirs();
        try (OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(out), "UTF-8")) { gson.toJson(map, w); }
    }
}

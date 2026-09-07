import com.google.gson.*;
import java.io.*;
import java.util.*;

/**
 * Generates the Ember Caverns — a 3-level authored dungeon for Pyralis (Island 2).
 * Each level is 40x40 tiles, saved as data/dungeons/ember_caverns_{1,2,3}.rfmap.
 * Also patches the Pyralis overworld 'D' tile with the dungeonName tile state.
 *
 * Run:
 *   javac -cp "target/classes;%USERPROFILE%\.m2\repository\com\google\code\gson\gson\2.10.1\gson-2.10.1.jar" tools/EmberCavernsGen.java -d tools/
 *   java -cp "tools;target/classes;%USERPROFILE%\.m2\repository\com\google\code\gson\gson\2.10.1\gson-2.10.1.jar" EmberCavernsGen
 */
public class EmberCavernsGen {

    static final int W = 40, H = 40;
    static final char WALL = 'W', FLOOR = '.', STAIR = 's', ALTAR = 'A',
            FOUNTAIN = 'f', THRONE = 'H', LAVA = 'L', PIT = 'P',
            DOOR = 'd', LOCKED_GATE = '[', TELEPORTER = 't';
    static Random rng = new Random(9999);
    static Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public static void main(String[] args) throws Exception {
        new File("data/dungeons").mkdirs();
        generateLevel1();
        generateLevel2();
        generateLevel3();
        patchPyralisEntrance();
        System.out.println("Ember Caverns generated: 3 levels + Pyralis patched.");
    }

    // ── Level 1: Entry level ─────────────────────────────────────────────────

    static void generateLevel1() throws Exception {
        char[][] t = wall(W, H);

        // Main entry hall (center)
        carveRoom(t, 17, 17, 6, 6);
        t[20][20] = STAIR; // entry point from overworld

        // North corridor leading to fountain room
        carveHCorridor(t, 20, 20, 10);  // go north
        carveVCorridor(t, 20, 10, 17);  // short bit
        carveRoom(t, 16, 5, 8, 5);     // fountain room
        t[7][20] = FOUNTAIN;

        // East corridor to altar alcove
        carveHCorridor(t, 23, 20, 20);  // corridor east from entry
        carveVCorridor(t, 32, 17, 23);
        carveRoom(t, 30, 14, 5, 4);    // altar room
        t[16][32] = ALTAR;

        // South corridor with lava borders
        carveVCorridor(t, 20, 23, 33);
        for (int y = 24; y < 33; y++) { t[y][19] = LAVA; t[y][21] = LAVA; }
        carveRoom(t, 17, 33, 6, 5);    // treasure room
        t[35][20] = 'g'; // glowing cube

        // West wing — winding path
        carveHCorridor(t, 17, 20, 5);
        carveVCorridor(t, 5, 18, 25);
        carveRoom(t, 3, 25, 5, 4);     // guard room
        t[27][5] = THRONE;

        // Down stairs (northeast)
        carveHCorridor(t, 23, 10, 35);
        carveRoom(t, 33, 7, 5, 5);
        t[9][35] = STAIR; // down to level 2

        // Doors at key junctions
        t[17][20] = DOOR;  // entry to fountain corridor
        t[20][23] = DOOR;  // entry to east wing
        t[23][20] = DOOR;  // entry to south corridor

        saveLevel(t, 1, 20, 20);
        System.out.println("  Level 1: entry hall, fountain, altar, throne, down-stairs");
    }

    // ── Level 2: Deeper caves ────────────────────────────────────────────────

    static void generateLevel2() throws Exception {
        char[][] t = wall(W, H);

        // Up stairs room (northeast, matching L1 down-stairs position)
        carveRoom(t, 33, 7, 5, 5);
        t[9][35] = STAIR; // up to level 1

        // Central throne chamber
        carveRoom(t, 14, 14, 12, 12);
        t[20][20] = THRONE;
        // Pillars
        t[16][16] = WALL; t[16][24] = WALL;
        t[24][16] = WALL; t[24][24] = WALL;

        // Corridor from up-stairs to central chamber
        carveHCorridor(t, 35, 10, 26);
        carveVCorridor(t, 26, 10, 14);

        // West labyrinth
        carveVCorridor(t, 14, 20, 8);
        carveHCorridor(t, 14, 8, 5);
        carveVCorridor(t, 5, 8, 20);
        carveHCorridor(t, 5, 20, 10);
        carveRoom(t, 3, 20, 4, 4);     // teleporter room
        t[22][5] = TELEPORTER;

        // South corridor to locked gate
        carveVCorridor(t, 20, 26, 34);
        t[28][20] = LOCKED_GATE; // requires iron_key
        carveRoom(t, 17, 34, 6, 4);    // boss antechamber
        t[36][20] = ALTAR;

        // East passage with pit traps
        carveHCorridor(t, 26, 20, 36);
        t[20][30] = PIT;
        t[20][33] = PIT;
        carveRoom(t, 34, 18, 4, 5);    // treasure room
        t[20][36] = 'g';

        // Down stairs (southwest)
        carveVCorridor(t, 8, 26, 35);
        carveRoom(t, 5, 33, 6, 5);
        t[35][8] = STAIR; // down to level 3

        // Doors
        t[14][20] = DOOR;  // entry to throne chamber
        t[26][20] = DOOR;  // south exit
        t[20][26] = DOOR;  // east exit

        saveLevel(t, 2, 35, 9);
        System.out.println("  Level 2: throne chamber, labyrinth, teleporter, locked gate, pits");
    }

    // ── Level 3: Boss level ──────────────────────────────────────────────────

    static void generateLevel3() throws Exception {
        char[][] t = wall(W, H);

        // Up stairs room (southwest, matching L2 down-stairs)
        carveRoom(t, 5, 33, 6, 5);
        t[35][8] = STAIR; // up to level 2

        // Narrow approach corridor
        carveVCorridor(t, 8, 33, 22);
        // Pit trap gauntlet
        t[30][8] = PIT;
        t[27][8] = PIT;
        t[24][8] = PIT;

        // Cross corridor
        carveHCorridor(t, 8, 22, 32);
        t[22][15] = DOOR;
        t[22][25] = DOOR;

        // Boss chamber (large, central-east)
        carveRoom(t, 18, 10, 14, 10);
        // Lava border inside
        for (int x = 19; x < 31; x++) { t[11][x] = LAVA; t[19][x] = LAVA; }
        for (int y = 11; y < 20; y++) { t[y][19] = LAVA; t[y][31] = LAVA; }
        // Inner arena
        carveRoom(t, 20, 12, 10, 6);
        t[15][25] = ALTAR; // boss encounter altar

        // Treasure alcove (behind boss)
        carveRoom(t, 22, 5, 6, 5);
        t[7][25] = 'g'; // reward cube
        t[10][25] = DOOR;

        // Fountain of respite (west of approach)
        carveRoom(t, 3, 18, 4, 5);
        carveHCorridor(t, 7, 20, 3);
        t[20][5] = FOUNTAIN;

        saveLevel(t, 3, 8, 35);
        System.out.println("  Level 3: pit gauntlet, lava arena, boss altar, treasure alcove");
    }

    // ── Pyralis Overworld Patch ──────────────────────────────────────────────

    static void patchPyralisEntrance() throws Exception {
        File f = new File("data/overworlds/pyralis.rfmap");
        JsonObject pyralis;
        try (FileReader r = new FileReader(f)) {
            pyralis = JsonParser.parseReader(r).getAsJsonObject();
        }

        // The dungeon entrance 'D' is at (85, 65) on Pyralis
        // Add initialTileStates with dungeonName
        JsonObject states = pyralis.has("initialTileStates")
                ? pyralis.getAsJsonObject("initialTileStates") : new JsonObject();

        JsonObject tileState = new JsonObject();
        tileState.addProperty("overrideId", "\u0000");
        JsonObject data = new JsonObject();
        data.addProperty("dungeonName", "ember_caverns");
        tileState.add("data", data);
        states.add("85,65", tileState);

        pyralis.add("initialTileStates", states);

        try (FileWriter w = new FileWriter(f)) {
            gson.toJson(pyralis, w);
        }
        System.out.println("  Patched Pyralis D tile at (85,65) with dungeonName=ember_caverns");
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

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

    static void saveLevel(char[][] t, int level, int entryX, int entryY) throws Exception {
        JsonObject map = new JsonObject();
        map.addProperty("type", "DUNGEON");
        map.addProperty("name", "ember_caverns_" + level);
        map.addProperty("width", W);
        map.addProperty("height", H);
        map.addProperty("interiorEntryX", entryX);
        map.addProperty("interiorEntryY", entryY);
        map.addProperty("dungeonLevel", level);
        map.addProperty("dungeonGroup", "ember_caverns");

        JsonArray tilesArr = new JsonArray();
        for (int y = 0; y < H; y++) {
            JsonArray row = new JsonArray();
            for (int x = 0; x < W; x++) row.add(String.valueOf(t[y][x]));
            tilesArr.add(row);
        }
        map.add("tiles", tilesArr);

        // Empty spawn difficulty (encounters use dungeon depth, not per-tile)
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

        File out = new File("data/dungeons/ember_caverns_" + level + ".rfmap");
        out.getParentFile().mkdirs();
        try (FileWriter w = new FileWriter(out)) { gson.toJson(map, w); }
    }
}

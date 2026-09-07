import com.google.gson.*;
import java.io.*;
import java.util.*;

/**
 * Generates the Leviathan's Eye — a single-level vision-quest dungeon for
 * Thalorax (Island 5). Contains a chamber built around Aqualon's actual eye.
 *
 * Story Bible: "A one-room dungeon that is really a vision-quest location."
 * The player enters, experiences Aqualon's dormant consciousness via lore
 * altars, and finds Seraphine's breadcrumb about the World Trench.
 *
 * Design: Short atmospheric approach through dark corridors, then one large
 * circular chamber (the Eye). Minimal combat, heavy on dark zones and lore.
 *
 * Run:
 *   javac -encoding UTF-8 -cp "target/classes;%USERPROFILE%\.m2\repository\com\google\code\gson\gson\2.10.1\gson-2.10.1.jar" tools/LeviathanEyeGen.java -d tools/
 *   java -cp "tools;target/classes;%USERPROFILE%\.m2\repository\com\google\code\gson\gson\2.10.1\gson-2.10.1.jar" LeviathanEyeGen
 */
public class LeviathanEyeGen {

    static final int W = 40, H = 40;
    static final char WALL = 'W', FLOOR = '.', STAIR = 's', ALTAR = 'A',
            FOUNTAIN = 'f', THRONE = 'H', PIT = 'P', DOOR = 'd',
            DARK = 'k', CUBE = 'g', SPINNER = 'n';
    static final String GROUP = "leviathan_eye";
    static Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public static void main(String[] args) throws Exception {
        new File("data/dungeons").mkdirs();
        generateLevel1();
        patchThaloraxEntrance();
        System.out.println("Leviathan's Eye generated: 1 level + Thalorax entrance patched.");
    }

    // == Level 1 (only level): The Leviathan's Eye ==============================
    // A descent through psychic pressure into a vast chamber containing
    // Aqualon's dormant eye. Dark, oppressive, more lore than combat.

    static void generateLevel1() throws Exception {
        char[][] t = wall(W, H);

        // ── Entry vestibule (south) ──
        carveRoom(t, 18, 34, 4, 4);        // small entry chamber
        t[36][20] = STAIR;                  // entry from overworld + back up

        // ── Descent corridor (dark, narrow, oppressive) ──
        carveVCorridor(t, 20, 26, 34);
        // Almost entirely dark — psychic pressure
        for (int y = 27; y < 34; y++)
            if (t[y][20] == FLOOR) t[y][20] = DARK;
        t[30][20] = FLOOR;                 // brief clear spot — catch your breath
        t[26][20] = DOOR;                  // door into antechamber

        // ── Antechamber of Visions ──
        carveRoom(t, 16, 22, 8, 5);
        // Vision alcoves on each side
        carveRoom(t, 12, 23, 4, 3);        // west alcove
        carveRoom(t, 24, 23, 4, 3);        // east alcove
        // Dark zone with spinners — visions assault the mind
        for (int y = 23; y < 26; y++) {
            for (int x = 13; x < 15; x++) t[y][x] = DARK;
            for (int x = 25; x < 27; x++) t[y][x] = DARK;
        }
        t[24][13] = SPINNER;               // vision-spinner (west)
        t[24][26] = SPINNER;               // vision-spinner (east)
        // Lore altars in alcoves — fragments of Aqualon's memory
        t[24][12] = ALTAR;                 // "The first dream — formless warmth"
        t[24][27] = ALTAR;                 // "The joy of creation"
        t[22][20] = DOOR;                  // north into the Eye

        // ── The Eye Chamber ──
        // Large oval room representing Aqualon's eye
        // Carved as a rough circle centered at (20, 13) with radius ~8
        for (int y = 5; y <= 21; y++) {
            for (int x = 12; x <= 28; x++) {
                double dx = (x - 20.0) / 8.5;
                double dy = (y - 13.0) / 8.5;
                if (dx * dx + dy * dy <= 1.0) {
                    t[y][x] = FLOOR;
                }
            }
        }

        // The pupil — dark zone center, 5x5
        for (int y = 11; y <= 15; y++)
            for (int x = 18; x <= 22; x++)
                if (t[y][x] == FLOOR) t[y][x] = DARK;
        // Clear the very center — the focal point
        t[13][20] = FLOOR;

        // Concentric ring of dark tiles (iris pattern)
        for (int y = 7; y <= 19; y++) {
            for (int x = 14; x <= 26; x++) {
                double dx = (x - 20.0) / 6.0;
                double dy = (y - 13.0) / 6.0;
                double dist = dx * dx + dy * dy;
                // Ring between 0.6 and 0.8 radius
                if (dist > 0.55 && dist < 0.75 && t[y][x] == FLOOR) {
                    t[y][x] = DARK;
                }
            }
        }

        // The focal point — altar at the very center of the Eye
        t[13][20] = ALTAR;                 // "The betrayal — the Shattering"

        // Bone pillars around the iris (eyelid supports)
        t[7][17] = WALL; t[7][23] = WALL;
        t[19][17] = WALL; t[19][23] = WALL;
        t[10][14] = WALL; t[10][26] = WALL;
        t[16][14] = WALL; t[16][26] = WALL;

        // ── Pit traps — psychic damage zones ──
        t[9][20] = PIT;                    // north of center
        t[17][20] = PIT;                   // south of center

        // ── Lore reward: Seraphine's final breadcrumb ──
        // Hidden behind the Eye — a small chamber to the north
        carveRoom(t, 18, 2, 4, 3);         // Seraphine's alcove
        carveVCorridor(t, 20, 4, 5);
        t[4][20] = DOOR;
        t[3][20] = THRONE;                 // Seraphine's pressure-resistant journal
        t[3][19] = CUBE;                   // dream-energy residue

        // ── Fountain — dream healing near entry to Eye ──
        t[21][20] = FOUNTAIN;

        saveLevel(t, 1, 20, 36);
        System.out.println("  Level 1: The Leviathan's Eye");
    }

    // == Patch thalorax overworld entrance =====================================

    static void patchThaloraxEntrance() throws Exception {
        File file = new File("data/overworlds/thalorax.rfmap");
        JsonObject map = gson.fromJson(new InputStreamReader(new FileInputStream(file), "UTF-8"), JsonObject.class);
        JsonArray tiles = map.getAsJsonArray("tiles");

        // Place in the deep south of the map, near the boneyard but distinct
        int dungX = 140, dungY = 170;

        // Find nearest walkable tile
        String bsand = "\uE074";
        String bfield = "\uE076";
        String docean = "\uE070";
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
        System.out.println("  Placed Leviathan's Eye entrance 'D' at (" + dungX + "," + dungY + ")");

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
        map.add("initialTileStates", new JsonObject());

        File out = new File("data/dungeons/" + GROUP + "_" + level + ".rfmap");
        out.getParentFile().mkdirs();
        try (OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(out), "UTF-8")) { gson.toJson(map, w); }
    }
}

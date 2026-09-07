import com.google.gson.*;
import java.io.*;
import java.util.*;

/**
 * Generates the Rootvault — a 5-level textured first-person style
 * authored dungeon for Sylvandar (Island 4). Each level is 40x40 tiles.
 *
 * The dungeon descends through the root system of the Great Root, becoming
 * more corrupted and dangerous with each level.
 *
 * Level 1: The Root Vestibule — introductory, teaches the environment
 * Level 2: The Spore Galleries — dark zones, spinners, fungi chambers
 * Level 3: The Amber Archive — riddle doors, memory crystals, complex layout
 * Level 4: The Blight Heart — damage tiles, chutes, teleporter maze
 * Level 5: The Deeproot Sanctum — boss area, altar, shard of endless growth
 *
 * Run:
 *   javac -encoding UTF-8 -cp "target/classes;%USERPROFILE%\.m2\repository\com\google\code\gson\gson\2.10.1\gson-2.10.1.jar" tools/RootvaultGen.java -d tools/
 *   java -cp "tools;target/classes;%USERPROFILE%\.m2\repository\com\google\code\gson\gson\2.10.1\gson-2.10.1.jar" RootvaultGen
 */
public class RootvaultGen {

    static final int W = 40, H = 40;
    static final char WALL = 'W', FLOOR = '.', STAIR = 's', ALTAR = 'A',
            FOUNTAIN = 'f', THRONE = 'H', PIT = 'P', DOOR = 'd',
            TELEPORTER = 't', SPINNER = 'n', DARK = 'k',
            CHUTE = 'c', RIDDLE = 'q', CHEST = 'B',
            LOCKED_GATE = '[', LOCKED_DOOR = '|';
    static Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public static void main(String[] args) throws Exception {
        new File("data/dungeons").mkdirs();
        generateLevel1();
        generateLevel2();
        generateLevel3();
        generateLevel4();
        generateLevel5();
        System.out.println("Rootvault generated: 5 levels.");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // LEVEL 1: THE ROOT VESTIBULE — introductory, teaches the environment
    // ═══════════════════════════════════════════════════════════════════════

    static void generateLevel1() throws Exception {
        char[][] t = wall(W, H);

        // ── Entry hall (south-center) ──
        carveRoom(t, 16, 30, 8, 6);     // 8x6 starting room
        t[33][20] = STAIR;              // entry from surface + stairs up

        // ── North corridor from entry ──
        carveVCorridor(t, 20, 20, 30);

        // ── West wing: Fountain chamber ──
        carveHCorridor(t, 12, 25, 20);
        carveRoom(t, 8, 22, 7, 6);      // fountain room
        t[25][11] = FOUNTAIN;
        t[22][8] = DOOR;
        // Small side alcove with chest
        carveRoom(t, 5, 24, 3, 4);
        carveHCorridor(t, 8, 26, 8);
        t[26][5] = CHEST;

        // ── East wing: Guard room ──
        carveHCorridor(t, 20, 25, 28);
        carveRoom(t, 26, 22, 6, 6);
        t[22][26] = DOOR;
        // Locked door to treasury
        carveRoom(t, 33, 22, 4, 4);
        carveHCorridor(t, 32, 24, 32);
        t[24][32] = LOCKED_DOOR;
        t[23][34] = CHEST;
        t[24][35] = CHEST;

        // ── Central chamber ──
        carveRoom(t, 15, 14, 10, 7);    // large central room
        t[14][15] = DOOR;               // south entrance
        t[20][20] = DOOR;               // from corridor

        // ── North corridor to stairs down ──
        carveVCorridor(t, 20, 6, 14);
        carveRoom(t, 17, 3, 6, 4);      // stair room
        t[5][20] = STAIR;               // stairs to level 2

        // ── Western passage with first spinner (teaching moment) ──
        carveHCorridor(t, 15, 16, 15);
        carveRoom(t, 10, 14, 5, 5);
        t[17][12] = SPINNER;            // first spinner — teaches the mechanic
        t[15][10] = DOOR;
        // Reward behind spinner
        carveRoom(t, 6, 15, 4, 4);
        carveVCorridor(t, 10, 14, 15);
        t[16][7] = CHEST;

        // ── Eastern passage with altar ──
        carveHCorridor(t, 25, 16, 24);
        carveRoom(t, 26, 14, 6, 5);
        t[14][26] = DOOR;
        t[16][29] = ALTAR;

        save(t, "data/dungeons/rootvault_1.rfmap", 1, "The Root Vestibule");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // LEVEL 2: THE SPORE GALLERIES — dark zones, spinners, fungi chambers
    // ═══════════════════════════════════════════════════════════════════════

    static void generateLevel2() throws Exception {
        char[][] t = wall(W, H);

        // ── Stairs from level 1 (match position) ──
        carveRoom(t, 17, 3, 6, 4);
        t[5][20] = STAIR;               // up to level 1

        // ── South corridor ──
        carveVCorridor(t, 20, 6, 14);

        // ── Central junction ──
        carveRoom(t, 16, 14, 8, 8);     // 8x8 hub room
        t[14][20] = DOOR;

        // ── West: Spore Gallery (dark zone corridor) ──
        carveHCorridor(t, 16, 18, 10);
        carveRoom(t, 6, 15, 4, 7);      // dark chamber
        t[18][8] = DARK;                // dark zone
        t[16][7] = DARK;
        t[19][7] = DARK;
        carveRoom(t, 3, 18, 4, 4);      // hidden treasure behind darkness
        carveVCorridor(t, 5, 15, 18);
        t[19][4] = CHEST;
        t[20][3] = CHEST;

        // ── East: Spinner maze ──
        carveHCorridor(t, 24, 18, 32);
        carveRoom(t, 28, 15, 8, 8);     // maze room
        t[18][30] = SPINNER;
        t[16][32] = SPINNER;
        t[20][28] = SPINNER;
        // Exit from spinner maze
        carveRoom(t, 33, 20, 4, 4);
        carveHCorridor(t, 33, 22, 35);
        t[22][34] = FOUNTAIN;           // reward fountain after maze

        // ── South wing: Fungi caverns ──
        carveVCorridor(t, 20, 22, 30);
        carveRoom(t, 14, 26, 12, 6);    // large fungi cavern
        t[29][16] = DARK;               // spore cloud patches
        t[28][22] = DARK;
        t[27][18] = CHEST;

        // ── Southwest: hidden passage ──
        carveHCorridor(t, 14, 30, 8);
        carveRoom(t, 5, 28, 4, 5);
        t[30][6] = ALTAR;               // hidden altar

        // ── Southeast: passage to stairs ──
        carveHCorridor(t, 26, 30, 32);
        carveRoom(t, 28, 32, 6, 5);
        t[34][31] = STAIR;              // stairs to level 3

        // ── Northeast: locked room with throne ──
        carveRoom(t, 30, 8, 6, 5);
        carveHCorridor(t, 30, 12, 35);
        t[12][30] = LOCKED_GATE;
        t[10][33] = THRONE;

        save(t, "data/dungeons/rootvault_2.rfmap", 2, "The Spore Galleries");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // LEVEL 3: THE AMBER ARCHIVE — riddle doors, complex layout
    // ═══════════════════════════════════════════════════════════════════════

    static void generateLevel3() throws Exception {
        char[][] t = wall(W, H);

        // ── Stairs from level 2 ──
        carveRoom(t, 28, 32, 6, 5);
        t[34][31] = STAIR;              // up to level 2

        // ── West corridor ──
        carveHCorridor(t, 28, 34, 20);

        // ── Central archive chamber ──
        carveRoom(t, 14, 28, 12, 10);   // huge archive room
        t[34][20] = DOOR;

        // Four amber crystal alcoves in the archive
        // NW alcove
        carveRoom(t, 10, 28, 4, 4);
        t[28][10] = DOOR;
        t[30][11] = ALTAR;              // memory crystal altar

        // NE alcove
        carveRoom(t, 26, 28, 4, 4);
        t[28][26] = DOOR;
        t[30][27] = ALTAR;

        // SW alcove
        carveRoom(t, 10, 34, 4, 4);
        t[34][10] = DOOR;
        t[36][11] = CHEST;

        // SE alcove
        carveRoom(t, 26, 34, 4, 4);
        t[34][26] = DOOR;
        t[36][27] = CHEST;

        // ── North wing: Riddle corridor ──
        carveVCorridor(t, 20, 20, 28);
        carveRoom(t, 17, 17, 6, 4);     // antechamber
        t[20][20] = RIDDLE;             // first riddle door
        // Beyond riddle 1
        carveVCorridor(t, 20, 12, 17);
        carveRoom(t, 16, 8, 8, 5);      // knowledge chamber
        t[12][20] = RIDDLE;             // second riddle door
        t[10][20] = FOUNTAIN;

        // ── West wing: Rootweave maze ──
        carveHCorridor(t, 14, 30, 6);
        carveRoom(t, 3, 28, 4, 6);
        carveVCorridor(t, 5, 22, 28);
        carveRoom(t, 3, 20, 5, 3);
        t[21][5] = SPINNER;
        // Dead end with treasure
        carveRoom(t, 3, 14, 4, 5);
        carveVCorridor(t, 5, 14, 20);
        t[15][4] = CHEST;
        t[16][5] = CHEST;

        // ── East wing: Throne room ──
        carveHCorridor(t, 26, 30, 34);
        carveRoom(t, 30, 26, 7, 5);
        t[26][30] = DOOR;
        t[28][33] = THRONE;
        // Secret passage from throne room
        carveVCorridor(t, 34, 22, 26);
        carveRoom(t, 32, 18, 5, 4);
        t[20][34] = CHEST;              // throne room treasure

        // ── Stairs down (behind second riddle door) ──
        carveRoom(t, 18, 4, 4, 5);
        carveVCorridor(t, 20, 4, 8);
        t[6][19] = STAIR;               // stairs to level 4

        save(t, "data/dungeons/rootvault_3.rfmap", 3, "The Amber Archive");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // LEVEL 4: THE BLIGHT HEART — chutes, teleporters, hostile
    // ═══════════════════════════════════════════════════════════════════════

    static void generateLevel4() throws Exception {
        char[][] t = wall(W, H);

        // ── Stairs from level 3 ──
        carveRoom(t, 18, 4, 4, 5);
        t[6][19] = STAIR;               // up to level 3

        // ── South corridor ──
        carveVCorridor(t, 20, 8, 16);

        // ── Central blight chamber ──
        carveRoom(t, 14, 16, 12, 10);   // corrupted central hub
        t[16][20] = DOOR;

        // ── Chute traps ──
        // West passage with chute
        carveHCorridor(t, 14, 20, 8);
        carveRoom(t, 5, 18, 4, 5);
        t[20][7] = CHUTE;               // drops to level 5
        t[19][6] = CHEST;               // bait chest before chute

        // East passage with chute
        carveHCorridor(t, 26, 20, 34);
        carveRoom(t, 30, 18, 5, 5);
        t[20][33] = CHUTE;              // drops to level 5
        t[19][32] = CHEST;

        // ── Teleporter puzzle (south) ──
        carveVCorridor(t, 20, 26, 34);
        carveRoom(t, 16, 30, 8, 6);     // teleporter room
        t[32][18] = TELEPORTER;         // teleports to east
        t[32][22] = TELEPORTER;         // teleports to west
        t[34][20] = TELEPORTER;         // teleports to solution room

        // Solution room (reached by correct teleporter)
        carveRoom(t, 30, 10, 6, 4);
        t[12][33] = FOUNTAIN;           // healing reward
        t[11][31] = CHEST;

        // ── West labyrinth ──
        carveVCorridor(t, 10, 22, 30);
        carveRoom(t, 6, 24, 5, 4);
        carveRoom(t, 6, 30, 5, 4);
        carveHCorridor(t, 6, 26, 10);
        carveHCorridor(t, 6, 32, 10);
        t[26][8] = DARK;                // dark zone
        t[32][8] = SPINNER;
        t[25][7] = ALTAR;

        // ── East labyrinth ──
        carveVCorridor(t, 30, 24, 34);
        carveRoom(t, 32, 26, 5, 4);
        carveRoom(t, 32, 32, 5, 4);
        carveHCorridor(t, 32, 28, 36);
        carveHCorridor(t, 32, 34, 36);
        t[28][34] = DARK;
        t[34][34] = SPINNER;
        t[27][35] = CHEST;

        // ── North passage to stairs down ──
        carveHCorridor(t, 14, 17, 6);
        carveRoom(t, 3, 14, 4, 5);
        // Riddle door guards the way down
        t[16][6] = RIDDLE;
        carveVCorridor(t, 5, 8, 14);
        carveRoom(t, 3, 4, 5, 5);
        t[6][5] = STAIR;                // stairs to level 5

        save(t, "data/dungeons/rootvault_4.rfmap", 4, "The Blight Heart");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // LEVEL 5: THE DEEPROOT SANCTUM — boss area, altar, shard
    // ═══════════════════════════════════════════════════════════════════════

    static void generateLevel5() throws Exception {
        char[][] t = wall(W, H);

        // ── Stairs from level 4 ──
        carveRoom(t, 3, 4, 5, 5);
        t[6][5] = STAIR;                // up to level 4

        // ── Chute landing zones (from level 4 chutes) ──
        carveRoom(t, 5, 18, 4, 5);      // west chute landing
        t[20][7] = STAIR;               // can go back up
        carveRoom(t, 30, 18, 5, 5);     // east chute landing
        t[20][33] = STAIR;              // can go back up

        // ── Connecting corridors to central area ──
        carveHCorridor(t, 9, 20, 16);
        carveVCorridor(t, 16, 14, 20);

        carveHCorridor(t, 30, 20, 24);
        carveVCorridor(t, 24, 14, 20);

        // ── Long south corridor ──
        carveVCorridor(t, 5, 8, 16);
        carveHCorridor(t, 5, 16, 20);

        // ── Grand sanctum (center) ──
        carveRoom(t, 12, 10, 16, 12);   // 16x12 boss chamber
        t[10][12] = DOOR; t[10][27] = DOOR; // doors from corridors

        // Pillars in sanctum
        t[13][15] = WALL; t[13][24] = WALL;
        t[18][15] = WALL; t[18][24] = WALL;

        // ── The Deeproot Altar (center of sanctum) ──
        t[15][19] = ALTAR;
        t[15][20] = ALTAR;
        t[16][19] = ALTAR;
        t[16][20] = ALTAR;

        // ── Throne of Sylvandar (north wall of sanctum) ──
        t[11][20] = THRONE;

        // ── Fountain flanking altar ──
        t[15][14] = FOUNTAIN;
        t[15][25] = FOUNTAIN;

        // ── West wing: Corruption chamber ──
        carveHCorridor(t, 12, 16, 6);
        carveRoom(t, 3, 14, 4, 6);
        t[14][3] = DOOR;
        t[17][5] = DARK;
        t[16][4] = CHEST;               // Shard of Endless Growth location

        // ── East wing: Memory vault ──
        carveHCorridor(t, 28, 16, 36);
        carveRoom(t, 32, 14, 5, 6);
        t[14][32] = DOOR;
        t[16][34] = CHEST;
        t[17][35] = ALTAR;              // final memory crystal

        // ── South passage: Escape route ──
        carveVCorridor(t, 20, 22, 32);
        carveRoom(t, 17, 32, 6, 5);
        t[34][20] = PIT;                // pit drops player to level 1 exit
        t[33][18] = FOUNTAIN;           // healing before departure

        // ── Secret NE room ──
        carveRoom(t, 32, 6, 5, 4);
        carveHCorridor(t, 28, 8, 32);
        t[8][28] = LOCKED_GATE;
        t[7][34] = CHEST;
        t[8][35] = CHEST;

        // ── Dark zone atmosphere in corners ──
        t[12][13] = DARK;               // sanctum atmosphere
        t[20][26] = DARK;

        save(t, "data/dungeons/rootvault_5.rfmap", 5, "The Deeproot Sanctum");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════════════

    static char[][] wall(int w, int h) {
        char[][] t = new char[h][w];
        for (char[] row : t) Arrays.fill(row, WALL);
        return t;
    }

    static void carveRoom(char[][] t, int x, int y, int w, int h) {
        for (int dy = 0; dy < h && (y + dy) < H; dy++)
            for (int dx = 0; dx < w && (x + dx) < W; dx++)
                t[y + dy][x + dx] = FLOOR;
    }

    static void carveVCorridor(char[][] t, int x, int y1, int y2) {
        int lo = Math.min(y1, y2), hi = Math.max(y1, y2);
        for (int y = lo; y <= hi; y++)
            if (y >= 0 && y < H && x >= 0 && x < W) t[y][x] = FLOOR;
    }

    static void carveHCorridor(char[][] t, int x, int y, int toX) {
        int lo = Math.min(x, toX), hi = Math.max(x, toX);
        for (int cx = lo; cx <= hi; cx++)
            if (y >= 0 && y < H && cx >= 0 && cx < W) t[y][cx] = FLOOR;
    }

    static void save(char[][] tiles, String path, int level, String levelName) throws Exception {
        JsonObject root = new JsonObject();
        root.addProperty("type", "DUNGEON");
        root.addProperty("name", "rootvault_" + level);
        root.addProperty("levelName", levelName);
        root.addProperty("width", W);
        root.addProperty("height", H);
        root.addProperty("level", level);

        // Tiles
        JsonArray tileArr = new JsonArray();
        for (int y = 0; y < H; y++) {
            JsonArray row = new JsonArray();
            for (int x = 0; x < W; x++) {
                row.add(String.valueOf(tiles[y][x]));
            }
            tileArr.add(row);
        }
        root.add("tiles", tileArr);

        // spawnDifficulty (level-based)
        JsonArray diffArr = new JsonArray();
        int baseDiff = 11 + level;
        for (int y = 0; y < H; y++) {
            JsonArray row = new JsonArray();
            for (int x = 0; x < W; x++) {
                row.add(tiles[y][x] == WALL ? 0 : baseDiff);
            }
            diffArr.add(row);
        }
        root.add("spawnDifficulty", diffArr);

        // NPCs (empty for dungeon levels)
        root.add("npcs", new JsonArray());
        root.add("townEntrances", new JsonArray());
        root.add("overworldTeleporters", new JsonArray());

        // Write
        File f = new File(path);
        f.getParentFile().mkdirs();
        try (Writer w = new OutputStreamWriter(new FileOutputStream(f), "UTF-8")) {
            gson.toJson(root, w);
        }
        System.out.println("  wrote " + path + " — " + levelName);
    }
}

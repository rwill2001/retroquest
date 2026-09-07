import java.io.*;
import java.util.Random;

/**
 * Generates data/overworlds/pyralis.rfmap — a 180x150 overworld map for
 * Island 2 (Pyralis / The Forged Isles).
 *
 * Volcanic island with three towns (Cinderport, Forge Keep, Ashfen Village),
 * a central volcano, lava rivers, ash plains, and connecting roads.
 *
 * Run from project root:
 *   javac tools/PyralisGen.java -d tools/
 *   java -cp tools PyralisGen
 */
public class PyralisGen {

    static final int COLS = 180, ROWS = 150;
    static char[][] g = new char[ROWS][COLS];
    static int[][] diff = new int[ROWS][COLS];
    static Random rng = new Random(42);

    // Tile constants
    static final char WATER   = '~';
    static final char VROCK   = '!';
    static final char ASH     = '$';
    static final char LAVA    = '%';
    static final char VMOUNT  = '&';
    static final char SLAG    = ')';
    static final char VSAND   = ':';
    static final char ATREE   = ';';
    static final char GRASS   = '.';
    static final char ROAD_NS = 'r';
    static final char ROAD_EW = '4';
    static final char ROAD_NE = '5';
    static final char ROAD_NW = '6';
    static final char ROAD_SE = '7';
    static final char ROAD_SW = '8';
    static final char TOWN    = 'E';
    static final char DUNGEON = 'D';
    static final char PORTAL  = 'U';

    public static void main(String[] args) throws Exception {

        // ── Step 1: Fill with water ────────────────────────────────────────
        fill(0, ROWS - 1, 0, COLS - 1, WATER);

        // ── Step 2: Island shape via ellipse + noise ───────────────────────
        for (int y = 0; y < ROWS; y++) {
            for (int x = 0; x < COLS; x++) {
                double dx = (x - 90.0) / 80.0;
                double dy = (y - 75.0) / 65.0;
                double dist = Math.sqrt(dx * dx + dy * dy);
                double noise = 0.15 * Math.sin(x * 0.3) * Math.cos(y * 0.25)
                             + 0.1  * Math.sin(x * 0.7 + y * 0.5);
                if (dist + noise < 0.92) {
                    g[y][x] = VROCK;
                }
            }
        }

        // ── Step 3: Beach border (2-pass, 2-tile wide in places) ──────────
        char[][] snap = copyGrid();
        for (int y = 0; y < ROWS; y++) {
            for (int x = 0; x < COLS; x++) {
                if (snap[y][x] != VROCK) continue;
                boolean nearWater = false;
                // Check 2-tile radius for water
                for (int dy = -2; dy <= 2 && !nearWater; dy++) {
                    for (int dx = -2; dx <= 2 && !nearWater; dx++) {
                        int ny = y + dy, nx = x + dx;
                        if (ny < 0 || ny >= ROWS || nx < 0 || nx >= COLS) {
                            nearWater = true;
                        } else if (snap[ny][nx] == WATER) {
                            nearWater = true;
                        }
                    }
                }
                if (nearWater) g[y][x] = VSAND;
            }
        }

        // ── Step 4: Central volcano ────────────────────────────────────────
        int vx = 90, vy = 55;

        // Mountain core
        for (int y = 0; y < ROWS; y++) {
            for (int x = 0; x < COLS; x++) {
                if (g[y][x] == WATER || g[y][x] == VSAND) continue;
                double d = Math.sqrt((x - vx) * (x - vx) + (y - vy) * (y - vy));
                double noiseV = 1.5 * Math.sin(x * 0.5) * Math.cos(y * 0.4);
                if (d < 12 + noiseV) {
                    g[y][x] = VMOUNT;
                }
            }
        }

        // Lava ring around volcano base (distance 12-14)
        for (int y = 0; y < ROWS; y++) {
            for (int x = 0; x < COLS; x++) {
                if (g[y][x] == WATER || g[y][x] == VSAND) continue;
                double d = Math.sqrt((x - vx) * (x - vx) + (y - vy) * (y - vy));
                if (d >= 12 && d < 14) {
                    g[y][x] = LAVA;
                }
            }
        }

        // Lava rivers flowing outward
        // South river
        for (int i = 0; i < 30; i++) {
            int rx = vx + (int)(2 * Math.sin(i * 0.3));
            int ry = vy + 14 + i;
            setIfLand(rx, ry, LAVA);
            setIfLand(rx + 1, ry, LAVA);
        }
        // East river
        for (int i = 0; i < 25; i++) {
            int rx = vx + 14 + i;
            int ry = vy + (int)(2 * Math.sin(i * 0.4));
            setIfLand(rx, ry, LAVA);
            setIfLand(rx, ry + 1, LAVA);
        }
        // West river
        for (int i = 0; i < 25; i++) {
            int rx = vx - 14 - i;
            int ry = vy + (int)(2 * Math.cos(i * 0.35));
            setIfLand(rx, ry, LAVA);
            setIfLand(rx, ry + 1, LAVA);
        }
        // Southeast river
        for (int i = 0; i < 22; i++) {
            int rx = vx + 10 + i;
            int ry = vy + 10 + i;
            setIfLand(rx, ry, LAVA);
            setIfLand(rx + 1, ry, LAVA);
        }

        // ── Step 5: Ash plains ─────────────────────────────────────────────
        // Eastern ash plains
        for (int y = 30; y <= 80; y++) {
            for (int x = 120; x <= 160; x++) {
                if (isLandBase(x, y)) {
                    double edgeNoise = 5 * Math.sin(y * 0.4) + 3 * Math.cos(x * 0.3);
                    if (x >= 120 + edgeNoise && rng.nextDouble() < 0.70) {
                        g[y][x] = ASH;
                    }
                }
            }
        }
        // Southern ash plains
        for (int y = 100; y <= 130; y++) {
            for (int x = 40; x <= 140; x++) {
                if (isLandBase(x, y)) {
                    double edgeNoise = 4 * Math.sin(x * 0.3) + 3 * Math.cos(y * 0.4);
                    if (y >= 100 + edgeNoise && rng.nextDouble() < 0.70) {
                        g[y][x] = ASH;
                    }
                }
            }
        }

        // ── Step 6: Ash trees ──────────────────────────────────────────────
        // Near Ashfen Village
        int treesPlaced = 0;
        int attempts = 0;
        while (treesPlaced < 30 && attempts < 5000) {
            int tx = 140 + rng.nextInt(21);
            int ty = 30 + rng.nextInt(21);
            attempts++;
            if (tx < COLS && ty < ROWS && isPlantable(tx, ty)) {
                g[ty][tx] = ATREE;
                treesPlaced++;
            }
        }
        // Scattered elsewhere
        treesPlaced = 0;
        attempts = 0;
        while (treesPlaced < 40 && attempts < 5000) {
            int tx = rng.nextInt(COLS);
            int ty = rng.nextInt(ROWS);
            attempts++;
            if (isPlantable(tx, ty)) {
                g[ty][tx] = ATREE;
                treesPlaced++;
            }
        }

        // ── Step 7: Slag heaps near lava ───────────────────────────────────
        snap = copyGrid();
        for (int y = 1; y < ROWS - 1; y++) {
            for (int x = 1; x < COLS - 1; x++) {
                if (snap[y][x] != LAVA) continue;
                if (rng.nextDouble() < 0.15) {
                    // Pick a random adjacent tile
                    int[][] adj = {{0,-1},{0,1},{-1,0},{1,0}};
                    int[] d = adj[rng.nextInt(4)];
                    int nx = x + d[0], ny = y + d[1];
                    if (ny >= 0 && ny < ROWS && nx >= 0 && nx < COLS
                        && (snap[ny][nx] == VROCK || snap[ny][nx] == ASH)) {
                        g[ny][nx] = SLAG;
                    }
                }
            }
        }

        // ── Step 8: Roads ──────────────────────────────────────────────────
        // Cinderport (30,130) to Forge Keep (90,70)
        //   Go north from Cinderport, then east
        roadNS(30, 80, 128);     // col 30, rows 80-128
        roadEW(80, 31, 88);      // row 80, cols 31-88
        g[80][30] = ROAD_NE;     // bend at (30,80): was going N, now going E
        g[80][89] = ROAD_NW;     // bend approaching Forge Keep col
        roadNS(89, 71, 79);      // col 89, rows 71-79 going up to Forge Keep

        // Forge Keep (90,70) to Ashfen Village (150,40)
        //   Go north then east
        roadNS(90, 42, 68);      // col 90, rows 42-68
        roadEW(42, 91, 148);     // row 42, cols 91-148
        g[42][90] = ROAD_NE;     // bend at (90,42)

        // Place bend tiles at key corners
        // Already placed above

        // ── Step 9: Town entrances ─────────────────────────────────────────
        g[130][30] = TOWN;
        g[70][90]  = TOWN;
        g[40][150] = TOWN;

        // ── Step 10: Dungeon entrance ──────────────────────────────────────
        g[65][85] = DUNGEON;

        // ── Step 11: Return portal ─────────────────────────────────────────
        g[132][28] = PORTAL;

        // ── Step 12: Spawn difficulty grid ─────────────────────────────────
        // Monster levels 6-10 → difficulty 11-20
        // Formula: monsterLevel = 1 + (difficulty * 49) / 99
        // Inverse: difficulty = ceil((level - 1) * 99.0 / 49.0)
        int[][] towns = {{22,92}, {90,70}, {150,40}};
        int minDiff = 11, maxDiff = 20; // levels 6-10
        for (int y = 0; y < ROWS; y++) {
            for (int x = 0; x < COLS; x++) {
                if (g[y][x] == WATER) {
                    diff[y][x] = 0;
                } else {
                    double dNearest = Double.MAX_VALUE;
                    for (int[] t : towns) {
                        double d = Math.sqrt((x-t[0])*(x-t[0]) + (y-t[1])*(y-t[1]));
                        if (d < dNearest) dNearest = d;
                    }
                    double t = Math.max(0, Math.min(1, (dNearest - 8) / 45.0));
                    int val = minDiff + (int)((maxDiff - minDiff) * t);
                    diff[y][x] = Math.max(minDiff, Math.min(maxDiff, val));
                }
            }
        }

        // ── Write JSON ─────────────────────────────────────────────────────
        writeJSON("data/overworlds/pyralis.rfmap");
        System.out.println("Done — data/overworlds/pyralis.rfmap written.");
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    static void fill(int r1, int r2, int c1, int c2, char tile) {
        for (int r = r1; r <= r2; r++)
            for (int c = c1; c <= c2; c++)
                g[r][c] = tile;
    }

    static char[][] copyGrid() {
        char[][] c = new char[ROWS][COLS];
        for (int r = 0; r < ROWS; r++)
            System.arraycopy(g[r], 0, c[r], 0, COLS);
        return c;
    }

    static void setIfLand(int x, int y, char tile) {
        if (x >= 0 && x < COLS && y >= 0 && y < ROWS) {
            char cur = g[y][x];
            if (cur != WATER && cur != VSAND && cur != VMOUNT) {
                g[y][x] = tile;
            }
        }
    }

    /** Returns true if tile is basic land (VROCK or ASH) — safe for overwriting. */
    static boolean isLandBase(int x, int y) {
        if (x < 0 || x >= COLS || y < 0 || y >= ROWS) return false;
        char c = g[y][x];
        return c == VROCK || c == ASH;
    }

    /** Returns true if tile can accept a tree. */
    static boolean isPlantable(int x, int y) {
        if (x < 0 || x >= COLS || y < 0 || y >= ROWS) return false;
        char c = g[y][x];
        return c == VROCK || c == ASH;
    }

    static void roadNS(int col, int rowStart, int rowEnd) {
        for (int r = rowStart; r <= rowEnd; r++) {
            if (col >= 0 && col < COLS && r >= 0 && r < ROWS) {
                g[r][col] = ROAD_NS;
            }
        }
    }

    static void roadEW(int row, int colStart, int colEnd) {
        for (int c = colStart; c <= colEnd; c++) {
            if (c >= 0 && c < COLS && row >= 0 && row < ROWS) {
                g[row][c] = ROAD_EW;
            }
        }
    }

    // ── JSON output ────────────────────────────────────────────────────────

    static void writeJSON(String path) throws Exception {
        new File(path).getParentFile().mkdirs();
        BufferedWriter bw = new BufferedWriter(new FileWriter(path), 1 << 20);
        StringBuilder sb = new StringBuilder(1 << 20);

        sb.append("{\n");
        sb.append("  \"type\": \"OVERWORLD\",\n");
        sb.append("  \"name\": \"pyralis\",\n");
        sb.append("  \"width\": ").append(COLS).append(",\n");
        sb.append("  \"height\": ").append(ROWS).append(",\n");

        // ── Tiles ──────────────────────────────────────────────────────────
        sb.append("  \"tiles\": [\n");
        for (int r = 0; r < ROWS; r++) {
            sb.append("    [");
            for (int c = 0; c < COLS; c++) {
                char ch = g[r][c];
                if (ch == '"') sb.append("\"\\\"\"");
                else if (ch == '\\') sb.append("\"\\\\\"");
                else sb.append('"').append(ch).append('"');
                if (c < COLS - 1) sb.append(',');
            }
            sb.append(']');
            if (r < ROWS - 1) sb.append(',');
            sb.append('\n');
            // Flush periodically to avoid huge memory usage
            if (r % 50 == 49) { bw.write(sb.toString()); sb.setLength(0); }
        }
        sb.append("  ],\n");
        bw.write(sb.toString()); sb.setLength(0);

        // ── Spawn difficulty ───────────────────────────────────────────────
        sb.append("  \"spawnDifficulty\": [\n");
        for (int r = 0; r < ROWS; r++) {
            sb.append("    [");
            for (int c = 0; c < COLS; c++) {
                sb.append(diff[r][c]);
                if (c < COLS - 1) sb.append(',');
            }
            sb.append(']');
            if (r < ROWS - 1) sb.append(',');
            sb.append('\n');
            if (r % 50 == 49) { bw.write(sb.toString()); sb.setLength(0); }
        }
        sb.append("  ],\n");
        bw.write(sb.toString()); sb.setLength(0);

        // ── NPCs ──────────────────────────────────────────────────────────
        sb.append("  \"npcs\": [\n");

        // NPC 1: Maren the Wanderer
        sb.append("    {\n");
        sb.append("      \"id\": \"npc_pyralis_maren\",\n");
        sb.append("      \"name\": \"Maren the Wanderer\",\n");
        sb.append("      \"spriteName\": \"npcs/townsman\",\n");
        sb.append("      \"type\": \"QUESTGIVER\",\n");
        sb.append("      \"defaultDialog\": \"Shh... keep your voice down.\",\n");
        sb.append("      \"questCompleteDialog\": \"\",\n");
        sb.append("      \"shopItemIds\": [],\n");
        sb.append("      \"dialogueTree\": {\n");
        sb.append("        \"startNodeId\": \"start\",\n");
        sb.append("        \"nodes\": {\n");
        // start node
        sb.append("          \"start\": {\n");
        sb.append("            \"id\": \"start\",\n");
        sb.append("            \"text\": \"Shh... keep your voice down. I\\u0027m Maren, and I need your help.\",\n");
        sb.append("            \"choices\": [\n");
        sb.append("              { \"label\": \"What kind of help?\", \"nextNodeId\": \"explain\" },\n");
        sb.append("              { \"label\": \"Not interested.\", \"nextNodeId\": \"decline\" }\n");
        sb.append("            ],\n");
        sb.append("            \"actions\": []\n");
        sb.append("          },\n");
        // explain node
        sb.append("          \"explain\": {\n");
        sb.append("            \"id\": \"explain\",\n");
        sb.append("            \"text\": \"Pyralis\\u0027s forge cult has enslaved the stone-shapers. I\\u0027m smuggling refugees out through the tide-caves. Will you help me?\",\n");
        sb.append("            \"choices\": [\n");
        sb.append("              { \"label\": \"I\\u0027ll help you.\", \"nextNodeId\": \"accept\" },\n");
        sb.append("              { \"label\": \"Not interested.\", \"nextNodeId\": \"decline\" }\n");
        sb.append("            ],\n");
        sb.append("            \"actions\": []\n");
        sb.append("          },\n");
        // accept node
        sb.append("          \"accept\": {\n");
        sb.append("            \"id\": \"accept\",\n");
        sb.append("            \"text\": \"Thank the tides. Meet me at the southern caves when you\\u0027re ready.\",\n");
        sb.append("            \"choices\": [\n");
        sb.append("              { \"label\": \"I\\u0027ll be there.\" }\n");
        sb.append("            ],\n");
        sb.append("            \"actions\": [\n");
        sb.append("              { \"type\": \"GIVE_QUEST\", \"target\": \"shard_of_ambition\", \"value\": \"\", \"amount\": 0 },\n");
        sb.append("              { \"type\": \"SET_FLAG\", \"target\": \"shard_quest_maren_path\", \"value\": \"true\", \"amount\": 0 }\n");
        sb.append("            ]\n");
        sb.append("          },\n");
        // decline node
        sb.append("          \"decline\": {\n");
        sb.append("            \"id\": \"decline\",\n");
        sb.append("            \"text\": \"Then keep walking. And keep your mouth shut about seeing me.\",\n");
        sb.append("            \"choices\": [\n");
        sb.append("              { \"label\": \"Farewell.\" }\n");
        sb.append("            ],\n");
        sb.append("            \"actions\": []\n");
        sb.append("          }\n");
        sb.append("        }\n");
        sb.append("      },\n");
        sb.append("      \"x\": 60,\n");
        sb.append("      \"y\": 100\n");
        sb.append("    },\n");

        // NPC 2: Dross
        sb.append("    {\n");
        sb.append("      \"id\": \"npc_pyralis_dross\",\n");
        sb.append("      \"name\": \"Dross\",\n");
        sb.append("      \"spriteName\": \"npcs/townsman\",\n");
        sb.append("      \"type\": \"TOWNSFOLK\",\n");
        sb.append("      \"defaultDialog\": \"...friend?\",\n");
        sb.append("      \"questCompleteDialog\": \"\",\n");
        sb.append("      \"shopItemIds\": [],\n");
        sb.append("      \"dialogueTree\": {\n");
        sb.append("        \"startNodeId\": \"start\",\n");
        sb.append("        \"nodes\": {\n");
        // start node
        sb.append("          \"start\": {\n");
        sb.append("            \"id\": \"start\",\n");
        sb.append("            \"text\": \"...friend? Dross... remembers... friends.\",\n");
        sb.append("            \"choices\": [\n");
        sb.append("              { \"label\": \"What are you?\", \"nextNodeId\": \"what_are_you\" },\n");
        sb.append("              { \"label\": \"What do you know about the mines?\", \"nextNodeId\": \"mines\" },\n");
        sb.append("              { \"label\": \"Goodbye.\", \"nextNodeId\": \"goodbye\" }\n");
        sb.append("            ],\n");
        sb.append("            \"actions\": []\n");
        sb.append("          },\n");
        // what_are_you node
        sb.append("          \"what_are_you\": {\n");
        sb.append("            \"id\": \"what_are_you\",\n");
        sb.append("            \"text\": \"Made. In forges. Not... supposed to think. But Dross thinks. Dross remembers.\",\n");
        sb.append("            \"choices\": [\n");
        sb.append("              { \"label\": \"What do you know about the mines?\", \"nextNodeId\": \"mines\" },\n");
        sb.append("              { \"label\": \"Goodbye.\", \"nextNodeId\": \"goodbye\" }\n");
        sb.append("            ],\n");
        sb.append("            \"actions\": []\n");
        sb.append("          },\n");
        // mines node
        sb.append("          \"mines\": {\n");
        sb.append("            \"id\": \"mines\",\n");
        sb.append("            \"text\": \"Dark. Hot. Others like Dross... but they don\\u0027t think. They just... work. Dross ran.\",\n");
        sb.append("            \"choices\": [\n");
        sb.append("              { \"label\": \"What are you?\", \"nextNodeId\": \"what_are_you\" },\n");
        sb.append("              { \"label\": \"Goodbye.\", \"nextNodeId\": \"goodbye\" }\n");
        sb.append("            ],\n");
        sb.append("            \"actions\": []\n");
        sb.append("          },\n");
        // goodbye node
        sb.append("          \"goodbye\": {\n");
        sb.append("            \"id\": \"goodbye\",\n");
        sb.append("            \"text\": \"Friend... go? Dross... wait here. Dross always waits.\",\n");
        sb.append("            \"choices\": [\n");
        sb.append("              { \"label\": \"Take care, Dross.\" }\n");
        sb.append("            ],\n");
        sb.append("            \"actions\": []\n");
        sb.append("          }\n");
        sb.append("        }\n");
        sb.append("      },\n");
        sb.append("      \"x\": 100,\n");
        sb.append("      \"y\": 85\n");
        sb.append("    }\n");

        sb.append("  ],\n");

        // ── Town entrances ─────────────────────────────────────────────────
        sb.append("  \"townEntrances\": [\n");
        sb.append("    { \"townName\": \"cinderport\", \"worldX\": 30, \"worldY\": 130 },\n");
        sb.append("    { \"townName\": \"forge_keep\", \"worldX\": 90, \"worldY\": 70 },\n");
        sb.append("    { \"townName\": \"ashfen_village\", \"worldX\": 150, \"worldY\": 40 }\n");
        sb.append("  ],\n");

        sb.append("  \"overworldTeleporters\": [],\n");
        sb.append("  \"maxDungeonDepth\": 50,\n");
        sb.append("  \"initialTileStates\": {}\n");
        sb.append("}\n");
        bw.write(sb.toString());
        bw.close();
        System.out.println("  wrote: " + path);
    }
}

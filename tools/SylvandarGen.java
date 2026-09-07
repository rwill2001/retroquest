import java.io.*;
import java.util.Random;

/**
 * Generates data/overworlds/sylvandar.rfmap — a 230x190 overworld map for
 * Island 4 (Sylvandar / The Verdant Mangroves).
 *
 * Single large forested island with ancient tree groves, bioluminescent
 * fungi zones, mangrove swamp borders, and petrified amber formations.
 * Three towns: Roothollow, Mossbridge, Amber Grove.
 *
 * Run from project root:
 *   javac -encoding UTF-8 tools/SylvandarGen.java -d tools/
 *   java -cp tools SylvandarGen
 */
public class SylvandarGen {

    static final int COLS = 230, ROWS = 190;
    static char[][] g = new char[ROWS][COLS];
    static int[][] diff = new int[ROWS][COLS];
    static Random rng = new Random(44);

    // Tile constants — Unicode PUA for Sylvandar tiles
    static final char WATER   = '~';
    static final char DFOREST = '\uE060';  // dense forest
    static final char GTREE   = '\uE061';  // giant tree (impassable)
    static final char MSWAMP  = '\uE062';  // mangrove swamp
    static final char MOSS    = '\uE063';  // moss ground
    static final char FUNGI   = '\uE064';  // bioluminescent fungi
    static final char AROOTS  = '\uE065';  // ancient roots (impassable)
    static final char THORN   = '\uE066';  // thorn hedge (impassable)
    static final char AMBER   = '\uE067';  // petrified amber (impassable)
    static final char PORTAL_RET = '\uE069'; // return portal to Zephyrion

    // Shared tiles
    static final char GRASS   = '.';
    static final char ROAD_NS = 'r';
    static final char ROAD_EW = '4';
    static final char ROAD_NE = '5';
    static final char ROAD_NW = '6';
    static final char ROAD_SE = '7';
    static final char ROAD_SW = '8';
    static final char TOWN    = 'E';
    static final char DUNGEON = 'D';
    static final char SAND    = '1';
    static final char FOREST  = '3';  // standard forest from Island 1

    public static void main(String[] args) throws Exception {

        // ── Step 1: Fill with water ────────────────────────────────────────
        fill(0, ROWS - 1, 0, COLS - 1, WATER);

        // ── Step 2: Island shape — single large landmass ───────────────────
        // Centered at (115, 95), roughly 200x160 ellipse with noise
        double cx = 115.0, cy = 95.0;
        double rx = 100.0, ry = 80.0;
        for (int y = 0; y < ROWS; y++) {
            for (int x = 0; x < COLS; x++) {
                double dx = (x - cx) / rx;
                double dy = (y - cy) / ry;
                double dist = Math.sqrt(dx * dx + dy * dy);
                double noise = 0.12 * Math.sin(x * 0.25) * Math.cos(y * 0.22)
                             + 0.08 * Math.sin(x * 0.6 + y * 0.4)
                             + 0.06 * Math.cos(x * 0.15 - y * 0.3);
                if (dist + noise < 0.90) {
                    g[y][x] = MOSS;  // base land = moss
                }
            }
        }

        // ── Step 3: Beach / mangrove swamp border ──────────────────────────
        char[][] snap = copyGrid();
        for (int y = 0; y < ROWS; y++) {
            for (int x = 0; x < COLS; x++) {
                if (snap[y][x] == WATER) continue;
                boolean nearWater = false;
                for (int dy = -3; dy <= 3 && !nearWater; dy++) {
                    for (int dx = -3; dx <= 3 && !nearWater; dx++) {
                        int ny = y + dy, nx = x + dx;
                        if (ny < 0 || ny >= ROWS || nx < 0 || nx >= COLS) {
                            nearWater = true;
                        } else if (snap[ny][nx] == WATER) {
                            nearWater = true;
                        }
                    }
                }
                if (nearWater) {
                    // Inner 2 tiles: mangrove; outer 1 tile: sand beach
                    boolean veryNear = false;
                    for (int dy = -1; dy <= 1 && !veryNear; dy++) {
                        for (int dx = -1; dx <= 1 && !veryNear; dx++) {
                            int ny = y + dy, nx = x + dx;
                            if (ny < 0 || ny >= ROWS || nx < 0 || nx >= COLS) {
                                veryNear = true;
                            } else if (snap[ny][nx] == WATER) {
                                veryNear = true;
                            }
                        }
                    }
                    g[y][x] = veryNear ? SAND : MSWAMP;
                }
            }
        }

        // ── Step 4: Dense forest (main interior) ───────────────────────────
        for (int y = 0; y < ROWS; y++) {
            for (int x = 0; x < COLS; x++) {
                if (g[y][x] != MOSS) continue;
                // Most of interior becomes dense forest
                double distC = Math.sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy));
                if (distC < 75 && rng.nextDouble() < 0.65) {
                    g[y][x] = DFOREST;
                }
            }
        }

        // ── Step 5: The Great Root — central ancient tree system ───────────
        // Center at (115, 85) — impassable ring of ancient roots + giant trees
        int grx = 115, gry = 85;
        for (int y = 0; y < ROWS; y++) {
            for (int x = 0; x < COLS; x++) {
                if (g[y][x] == WATER || g[y][x] == SAND) continue;
                double d = Math.sqrt((x - grx) * (x - grx) + (y - gry) * (y - gry));
                double noiseR = 2.0 * Math.sin(x * 0.4) * Math.cos(y * 0.35);
                if (d < 10 + noiseR) {
                    g[y][x] = GTREE;  // core = giant trees
                } else if (d < 16 + noiseR) {
                    g[y][x] = AROOTS; // ring of ancient roots
                }
            }
        }

        // ── Step 6: Bioluminescent fungi zones (dark forest pockets) ──────
        // Western fungi grove
        placeFungiZone(50, 80, 20);
        // Eastern fungi grove
        placeFungiZone(170, 100, 18);
        // Southern fungi pocket
        placeFungiZone(100, 140, 15);

        // ── Step 7: Petrified amber grove (north) ──────────────────────────
        for (int y = 25; y <= 55; y++) {
            for (int x = 85; x <= 145; x++) {
                if (!isLandBase(x, y)) continue;
                double edgeNoise = 5 * Math.sin(y * 0.4) + 3 * Math.cos(x * 0.3);
                double dx2 = (x - 115.0) / 35.0;
                double dy2 = (y - 40.0) / 18.0;
                if (dx2 * dx2 + dy2 * dy2 < 1.0 + edgeNoise * 0.05) {
                    if (rng.nextDouble() < 0.25) {
                        g[y][x] = AMBER;
                    } else if (rng.nextDouble() < 0.3) {
                        g[y][x] = MOSS;  // clearings within amber grove
                    }
                }
            }
        }

        // ── Step 8: Thorn hedges (scattered barriers) ─────────────────────
        // Southern thorn maze
        for (int y = 120; y <= 150; y++) {
            for (int x = 50; x <= 180; x++) {
                if (!isLandBase(x, y)) continue;
                double v = Math.sin(x * 0.2 + y * 0.15) * Math.cos(x * 0.1 - y * 0.2);
                if (v > 0.75 && rng.nextDouble() < 0.45) {
                    g[y][x] = THORN;
                }
            }
        }
        // Eastern thorn belt
        for (int y = 70; y <= 120; y++) {
            for (int x = 160; x <= 195; x++) {
                if (!isLandBase(x, y)) continue;
                double v = Math.sin(x * 0.25 + y * 0.2) * Math.cos(x * 0.12 - y * 0.18);
                if (v > 0.8 && rng.nextDouble() < 0.35) {
                    g[y][x] = THORN;
                }
            }
        }

        // ── Step 8b: Moss clearings in dense forest ──────────────────────
        // Create open glades that break up the monotony
        int[][] clearings = {{80, 60, 8}, {145, 75, 7}, {90, 120, 9}, {160, 130, 6}, {55, 110, 7}};
        for (int[] cl : clearings) {
            int ccx = cl[0], ccy = cl[1], cr = cl[2];
            for (int y = ccy - cr; y <= ccy + cr; y++) {
                for (int x = ccx - cr; x <= ccx + cr; x++) {
                    if (x < 0 || x >= COLS || y < 0 || y >= ROWS) continue;
                    double d = Math.sqrt((x - ccx) * (x - ccx) + (y - ccy) * (y - ccy));
                    if (d < cr && (g[y][x] == DFOREST || g[y][x] == MOSS)) {
                        g[y][x] = MOSS;
                    }
                }
            }
        }

        // ── Step 8c: Additional fungi zones ──────────────────────────────
        placeFungiZone(140, 55, 12);   // NE fungi pocket
        placeFungiZone(70, 130, 14);   // SW fungi cave area
        placeFungiZone(185, 85, 10);   // far east pocket

        // ── Step 8d: Blight zone around dungeon entrance ─────────────────
        // Corrupted area near (115, 102) — mix of thorn and fungi
        for (int y = 95; y <= 115; y++) {
            for (int x = 105; x <= 130; x++) {
                if (!isLandBase(x, y)) continue;
                double d = Math.sqrt((x - 118) * (x - 118) + (y - 105) * (y - 105));
                if (d < 12) {
                    if (rng.nextDouble() < 0.3) g[y][x] = THORN;
                    else if (rng.nextDouble() < 0.2) g[y][x] = FUNGI;
                }
            }
        }

        // ── Step 9: Giant trees scattered in forest ───────────────────────
        int treesPlaced = 0;
        int attempts = 0;
        while (treesPlaced < 60 && attempts < 8000) {
            int tx = 20 + rng.nextInt(190);
            int ty = 20 + rng.nextInt(150);
            attempts++;
            if (tx < COLS && ty < ROWS && g[ty][tx] == DFOREST) {
                // Don't place too close to other giant trees
                boolean tooClose = false;
                for (int dy = -4; dy <= 4 && !tooClose; dy++) {
                    for (int dx = -4; dx <= 4 && !tooClose; dx++) {
                        int ny = ty + dy, nx = tx + dx;
                        if (ny >= 0 && ny < ROWS && nx >= 0 && nx < COLS && g[ny][nx] == GTREE) {
                            tooClose = true;
                        }
                    }
                }
                if (!tooClose) {
                    g[ty][tx] = GTREE;
                    treesPlaced++;
                }
            }
        }

        // ── Step 10: Roads ─────────────────────────────────────────────────
        // Town positions:
        //   Roothollow:  (65, 95)  — west-center
        //   Mossbridge:  (165, 105) — east
        //   Amber Grove: (115, 38) — north

        // Roothollow to Amber Grove: go north then east
        roadNS(65, 50, 93);
        roadEW(50, 66, 113);
        g[50][65] = ROAD_NE;
        roadNS(114, 39, 49);
        g[50][114] = ROAD_NW;

        // Roothollow to Mossbridge: go east
        roadEW(95, 67, 163);

        // Mossbridge to Amber Grove: go north then west
        roadNS(165, 50, 103);
        roadEW(50, 116, 164);
        g[50][165] = ROAD_NW;

        // Clear roads of blocking tiles
        for (int y = 0; y < ROWS; y++) {
            for (int x = 0; x < COLS; x++) {
                char c = g[y][x];
                if (c == ROAD_NS || c == ROAD_EW || c == ROAD_NE || c == ROAD_NW
                    || c == ROAD_SE || c == ROAD_SW) {
                    // Make sure adjacent tiles aren't blocking the road view
                    // (roads override forest/roots)
                }
            }
        }

        // ── Step 11: Town entrances ────────────────────────────────────────
        g[95][65]   = TOWN;   // Roothollow
        g[105][165] = TOWN;   // Mossbridge
        g[38][115]  = TOWN;   // Amber Grove

        // ── Step 12: Dungeon entrance — The Rootvault ─────────────────────
        // Near the Great Root, south side
        g[102][115] = DUNGEON;

        // ── Step 13: Return portal to Zephyrion (free, on south coast) ────
        g[162][60] = PORTAL_RET;

        // ── Step 14: Spawn difficulty grid (12-15) ─────────────────────────
        // Monster levels 12-15 → difficulty 23-30
        // Formula: monsterLevel = 1 + (difficulty * 49) / 99
        int[][] towns = {{65,95}, {165,105}, {115,38}};
        int minDiff = 23, maxDiff = 30; // levels 12-15
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
        writeJSON("data/overworlds/sylvandar.rfmap");
        System.out.println("Done — data/overworlds/sylvandar.rfmap written.");
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

    static boolean isLandBase(int x, int y) {
        if (x < 0 || x >= COLS || y < 0 || y >= ROWS) return false;
        char c = g[y][x];
        return c == MOSS || c == DFOREST;
    }

    static void placeFungiZone(int cx, int cy, int radius) {
        for (int y = cy - radius; y <= cy + radius; y++) {
            for (int x = cx - radius; x <= cx + radius; x++) {
                if (x < 0 || x >= COLS || y < 0 || y >= ROWS) continue;
                if (!isLandBase(x, y)) continue;
                double d = Math.sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy));
                if (d < radius && rng.nextDouble() < 0.35) {
                    g[y][x] = FUNGI;
                }
            }
        }
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

    static String esc(char ch) {
        if (ch == '"') return "\"\\\"\"";
        if (ch == '\\') return "\"\\\\\"";
        if (ch > 127) return "\"\\u" + String.format("%04X", (int) ch) + "\"";
        return "\"" + ch + "\"";
    }

    static void writeJSON(String path) throws Exception {
        new File(path).getParentFile().mkdirs();
        BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(
            new FileOutputStream(path), "UTF-8"), 1 << 20);
        StringBuilder sb = new StringBuilder(1 << 20);

        sb.append("{\n");
        sb.append("  \"type\": \"OVERWORLD\",\n");
        sb.append("  \"name\": \"sylvandar\",\n");
        sb.append("  \"width\": ").append(COLS).append(",\n");
        sb.append("  \"height\": ").append(ROWS).append(",\n");

        // ── Tiles ──────────────────────────────────────────────────────────
        sb.append("  \"tiles\": [\n");
        for (int r = 0; r < ROWS; r++) {
            sb.append("    [");
            for (int c = 0; c < COLS; c++) {
                sb.append(esc(g[r][c]));
                if (c < COLS - 1) sb.append(',');
            }
            sb.append(']');
            if (r < ROWS - 1) sb.append(',');
            sb.append('\n');
            if (r % 40 == 39) { bw.write(sb.toString()); sb.setLength(0); }
        }
        sb.append("  ],\n");
        bw.write(sb.toString()); sb.setLength(0);

        // ── Spawn difficulty ─────────────────────────────────────────────
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
            if (r % 40 == 39) { bw.write(sb.toString()); sb.setLength(0); }
        }
        sb.append("  ],\n");
        bw.write(sb.toString()); sb.setLength(0);

        // ── NPCs ──────────────────────────────────────────────────────────
        sb.append("  \"npcs\": [\n");

        // NPC 1: Rootspeaker Thenna — overworld quest-giver near Great Root
        sb.append("    {\n");
        sb.append("      \"id\": \"npc_sylvandar_thenna\",\n");
        sb.append("      \"name\": \"Rootspeaker Thenna\",\n");
        sb.append("      \"spriteName\": \"npcs/townsman\",\n");
        sb.append("      \"type\": \"QUESTGIVER\",\n");
        sb.append("      \"defaultDialog\": \"The roots remember what the branches forget.\",\n");
        sb.append("      \"questCompleteDialog\": \"\",\n");
        sb.append("      \"shopItemIds\": [],\n");
        sb.append("      \"dialogueTree\": {\n");
        sb.append("        \"startNodeId\": \"start\",\n");
        sb.append("        \"nodes\": {\n");

        sb.append("          \"start\": {\n");
        sb.append("            \"id\": \"start\",\n");
        sb.append("            \"text\": \"You feel it, don\\u0027t you? The forest is... growing too fast. The bark reaches where it should not. I am Thenna, and I speak for the roots.\",\n");
        sb.append("            \"choices\": [\n");
        sb.append("              { \"label\": \"What\\u0027s happening to the forest?\", \"nextNodeId\": \"explain\" },\n");
        sb.append("              { \"label\": \"I\\u0027m looking for the Key of Roots.\", \"nextNodeId\": \"key_ask\" },\n");
        sb.append("              { \"label\": \"Farewell.\", \"nextNodeId\": \"farewell\" }\n");
        sb.append("            ],\n");
        sb.append("            \"actions\": []\n");
        sb.append("          },\n");

        sb.append("          \"explain\": {\n");
        sb.append("            \"id\": \"explain\",\n");
        sb.append("            \"text\": \"Sylvandar experiments with a Shard of the old world. He tries to regrow what was lost. But growth without end is not life \\u2014 it is suffocation. The vines strangle. The roots crush. People are... absorbed.\",\n");
        sb.append("            \"choices\": [\n");
        sb.append("              { \"label\": \"How can I help?\", \"nextNodeId\": \"help\" },\n");
        sb.append("              { \"label\": \"That sounds dangerous.\", \"nextNodeId\": \"farewell\" }\n");
        sb.append("            ],\n");
        sb.append("            \"actions\": []\n");
        sb.append("          },\n");

        sb.append("          \"help\": {\n");
        sb.append("            \"id\": \"help\",\n");
        sb.append("            \"text\": \"Investigate the spreading bark. The blight started near the Great Root and pushes outward. Find what feeds it. Then... you must choose whether to prune, or let it bloom.\",\n");
        sb.append("            \"choices\": [\n");
        sb.append("              { \"label\": \"I\\u0027ll investigate.\", \"nextNodeId\": \"accept\" },\n");
        sb.append("              { \"label\": \"Not yet.\", \"nextNodeId\": \"farewell\" }\n");
        sb.append("            ],\n");
        sb.append("            \"actions\": []\n");
        sb.append("          },\n");

        sb.append("          \"accept\": {\n");
        sb.append("            \"id\": \"accept\",\n");
        sb.append("            \"text\": \"The roots will guide you. Listen to the forest, Unbound. Not everything that grows is good, and not everything that dies is lost.\",\n");
        sb.append("            \"choices\": [\n");
        sb.append("              { \"label\": \"I understand.\" }\n");
        sb.append("            ],\n");
        sb.append("            \"actions\": [\n");
        sb.append("              { \"type\": \"GIVE_QUEST\", \"target\": \"the_spreading_bark\", \"value\": \"\", \"amount\": 0 },\n");
        sb.append("              { \"type\": \"SET_FLAG\", \"target\": \"sylvandar_bark_quest\", \"value\": \"true\", \"amount\": 0 }\n");
        sb.append("            ]\n");
        sb.append("          },\n");

        sb.append("          \"key_ask\": {\n");
        sb.append("            \"id\": \"key_ask\",\n");
        sb.append("            \"text\": \"The Key of Roots is not taken \\u2014 it is grown. Only one who has proven their understanding of the cycle may claim it. Speak to the Amber Sage in Amber Grove. She holds the trial.\",\n");
        sb.append("            \"choices\": [\n");
        sb.append("              { \"label\": \"Where is Amber Grove?\", \"nextNodeId\": \"directions\" },\n");
        sb.append("              { \"label\": \"Thank you.\", \"nextNodeId\": \"farewell\" }\n");
        sb.append("            ],\n");
        sb.append("            \"actions\": []\n");
        sb.append("          },\n");

        sb.append("          \"directions\": {\n");
        sb.append("            \"id\": \"directions\",\n");
        sb.append("            \"text\": \"North, through the petrified forest. Follow the amber glow. The Sage waits among the memories of the world before the Shattering.\",\n");
        sb.append("            \"choices\": [\n");
        sb.append("              { \"label\": \"I\\u0027ll find her.\" }\n");
        sb.append("            ],\n");
        sb.append("            \"actions\": []\n");
        sb.append("          },\n");

        sb.append("          \"farewell\": {\n");
        sb.append("            \"id\": \"farewell\",\n");
        sb.append("            \"text\": \"The roots are patient. They will be here when you return.\",\n");
        sb.append("            \"choices\": [],\n");
        sb.append("            \"actions\": [\n");
        sb.append("              { \"type\": \"CLOSE_DIALOGUE\", \"target\": \"\", \"value\": \"\", \"amount\": 0 }\n");
        sb.append("            ]\n");
        sb.append("          }\n");

        sb.append("        }\n");  // end nodes
        sb.append("      },\n");  // end dialogueTree
        sb.append("      \"x\": 100,\n");
        sb.append("      \"y\": 90\n");
        sb.append("    },\n");

        // NPC 2: Bloom — bark-skinned child (corruption evidence)
        sb.append("    {\n");
        sb.append("      \"id\": \"npc_sylvandar_bloom\",\n");
        sb.append("      \"name\": \"Bloom\",\n");
        sb.append("      \"spriteName\": \"npcs/townsman\",\n");
        sb.append("      \"type\": \"TOWNSFOLK\",\n");
        sb.append("      \"defaultDialog\": \"...the bark is warm...\",\n");
        sb.append("      \"questCompleteDialog\": \"\",\n");
        sb.append("      \"shopItemIds\": [],\n");
        sb.append("      \"dialogueTree\": {\n");
        sb.append("        \"startNodeId\": \"start\",\n");
        sb.append("        \"nodes\": {\n");

        sb.append("          \"start\": {\n");
        sb.append("            \"id\": \"start\",\n");
        sb.append("            \"text\": \"[A child stands before you, skin mottled with patches of living bark. Leaves sprout from her hair. She tilts her head.] ...you\\u0027re not a tree yet.\",\n");
        sb.append("            \"choices\": [\n");
        sb.append("              { \"label\": \"What happened to you?\", \"nextNodeId\": \"what\" },\n");
        sb.append("              { \"label\": \"Does it hurt?\", \"nextNodeId\": \"hurt\" },\n");
        sb.append("              { \"label\": \"Goodbye, little one.\", \"nextNodeId\": \"bye\" }\n");
        sb.append("            ],\n");
        sb.append("            \"actions\": []\n");
        sb.append("          },\n");

        sb.append("          \"what\": {\n");
        sb.append("            \"id\": \"what\",\n");
        sb.append("            \"text\": \"The forest wanted me. I was playing near the Great Root and... the roots held my feet. Now I\\u0027m partly a tree. [She flexes bark-covered fingers.] I can hear the sap singing.\",\n");
        sb.append("            \"choices\": [\n");
        sb.append("              { \"label\": \"Can you be cured?\", \"nextNodeId\": \"cure\" },\n");
        sb.append("              { \"label\": \"I\\u0027m sorry.\", \"nextNodeId\": \"bye\" }\n");
        sb.append("            ],\n");
        sb.append("            \"actions\": []\n");
        sb.append("          },\n");

        sb.append("          \"hurt\": {\n");
        sb.append("            \"id\": \"hurt\",\n");
        sb.append("            \"text\": \"No. It feels like... growing. Like spring inside my skin. Rootspeaker Thenna says I\\u0027m becoming something new. Not lost. Just... different.\",\n");
        sb.append("            \"choices\": [\n");
        sb.append("              { \"label\": \"That\\u0027s brave of you.\", \"nextNodeId\": \"bye\" }\n");
        sb.append("            ],\n");
        sb.append("            \"actions\": []\n");
        sb.append("          },\n");

        sb.append("          \"cure\": {\n");
        sb.append("            \"id\": \"cure\",\n");
        sb.append("            \"text\": \"Thenna tried. Ashwalker Dren tried fire. Nothing works. Maybe I don\\u0027t want it to. The trees are kind. They just don\\u0027t understand boundaries.\",\n");
        sb.append("            \"choices\": [\n");
        sb.append("              { \"label\": \"Stay strong, Bloom.\", \"nextNodeId\": \"bye\" }\n");
        sb.append("            ],\n");
        sb.append("            \"actions\": [\n");
        sb.append("              { \"type\": \"SET_FLAG\", \"target\": \"met_bloom\", \"value\": \"true\", \"amount\": 0 }\n");
        sb.append("            ]\n");
        sb.append("          },\n");

        sb.append("          \"bye\": {\n");
        sb.append("            \"id\": \"bye\",\n");
        sb.append("            \"text\": \"[She waves with a hand that is half-fingers, half-twigs.] Bye! Watch where you step. The roots are curious about strangers.\",\n");
        sb.append("            \"choices\": [],\n");
        sb.append("            \"actions\": [\n");
        sb.append("              { \"type\": \"CLOSE_DIALOGUE\", \"target\": \"\", \"value\": \"\", \"amount\": 0 }\n");
        sb.append("            ]\n");
        sb.append("          }\n");

        sb.append("        }\n");
        sb.append("      },\n");
        sb.append("      \"x\": 108,\n");
        sb.append("      \"y\": 92\n");
        sb.append("    },\n");

        // NPC 3: Ashwalker Dren — Pyralis exile (near south forest)
        sb.append("    {\n");
        sb.append("      \"id\": \"npc_sylvandar_dren\",\n");
        sb.append("      \"name\": \"Ashwalker Dren\",\n");
        sb.append("      \"spriteName\": \"npcs/townsman\",\n");
        sb.append("      \"type\": \"QUESTGIVER\",\n");
        sb.append("      \"defaultDialog\": \"Fire and forest... uneasy neighbors.\",\n");
        sb.append("      \"questCompleteDialog\": \"\",\n");
        sb.append("      \"shopItemIds\": [],\n");
        sb.append("      \"dialogueTree\": {\n");
        sb.append("        \"startNodeId\": \"start\",\n");
        sb.append("        \"nodes\": {\n");

        sb.append("          \"start\": {\n");
        sb.append("            \"id\": \"start\",\n");
        sb.append("            \"text\": \"[A scarred man tends a small, careful fire in a clearing.] Another traveler. I\\u0027m Dren. I came from the Forged Isles seeking... penance.\",\n");
        sb.append("            \"choices\": [\n");
        sb.append("              { \"label\": \"Penance for what?\", \"nextNodeId\": \"penance\" },\n");
        sb.append("              { \"label\": \"Can you help me with the overgrowth?\", \"nextNodeId\": \"overgrowth\" },\n");
        sb.append("              { \"label\": \"Safe travels.\", \"nextNodeId\": \"bye\" }\n");
        sb.append("            ],\n");
        sb.append("            \"actions\": []\n");
        sb.append("          },\n");

        sb.append("          \"penance\": {\n");
        sb.append("            \"id\": \"penance\",\n");
        sb.append("            \"text\": \"I was a forge-hand in the Crucible. We burned things that shouldn\\u0027t have burned. I came here to plant fire-resistant trees \\u2014 to prove that fire and growth can coexist. Pyralis and Sylvandar don\\u0027t have to be enemies.\",\n");
        sb.append("            \"choices\": [\n");
        sb.append("              { \"label\": \"That\\u0027s noble. Need help?\", \"nextNodeId\": \"help_offer\" },\n");
        sb.append("              { \"label\": \"Good luck.\", \"nextNodeId\": \"bye\" }\n");
        sb.append("            ],\n");
        sb.append("            \"actions\": []\n");
        sb.append("          },\n");

        sb.append("          \"help_offer\": {\n");
        sb.append("            \"id\": \"help_offer\",\n");
        sb.append("            \"text\": \"If you\\u0027re willing, I could use someone to clear the blight from my planting grounds. Kill the vine stranglers that choke the clearings, and I can plant my fire-oaks.\",\n");
        sb.append("            \"choices\": [\n");
        sb.append("              { \"label\": \"Consider it done.\", \"nextNodeId\": \"quest_accept\" },\n");
        sb.append("              { \"label\": \"Maybe later.\", \"nextNodeId\": \"bye\" }\n");
        sb.append("            ],\n");
        sb.append("            \"actions\": []\n");
        sb.append("          },\n");

        sb.append("          \"quest_accept\": {\n");
        sb.append("            \"id\": \"quest_accept\",\n");
        sb.append("            \"text\": \"You have my thanks, traveler. The vine stranglers lurk in the deep forest. Clear them out and this land may breathe again.\",\n");
        sb.append("            \"choices\": [\n");
        sb.append("              { \"label\": \"I\\u0027ll return when it\\u0027s done.\" }\n");
        sb.append("            ],\n");
        sb.append("            \"actions\": [\n");
        sb.append("              { \"type\": \"GIVE_QUEST\", \"target\": \"vine_strangler_bounty\", \"value\": \"\", \"amount\": 0 }\n");
        sb.append("            ]\n");
        sb.append("          },\n");

        sb.append("          \"overgrowth\": {\n");
        sb.append("            \"id\": \"overgrowth\",\n");
        sb.append("            \"text\": \"The overgrowth isn\\u0027t natural. Sylvandar pours too much of himself into the land. He\\u0027s trying to regrow the world \\u2014 but a god\\u0027s love is a heavy thing. It crushes what it embraces.\",\n");
        sb.append("            \"choices\": [\n");
        sb.append("              { \"label\": \"How do we stop it?\", \"nextNodeId\": \"stop\" },\n");
        sb.append("              { \"label\": \"I see.\", \"nextNodeId\": \"bye\" }\n");
        sb.append("            ],\n");
        sb.append("            \"actions\": []\n");
        sb.append("          },\n");

        sb.append("          \"stop\": {\n");
        sb.append("            \"id\": \"stop\",\n");
        sb.append("            \"text\": \"Maybe you don\\u0027t stop it. Maybe you teach it boundaries. Find Fungal Lord Mycos in the deep woods. He understands the necessary rot \\u2014 that decay feeds new life. Sylvandar has forgotten that half of the cycle.\",\n");
        sb.append("            \"choices\": [\n");
        sb.append("              { \"label\": \"Where is Mycos?\", \"nextNodeId\": \"mycos_dir\" },\n");
        sb.append("              { \"label\": \"I\\u0027ll keep that in mind.\", \"nextNodeId\": \"bye\" }\n");
        sb.append("            ],\n");
        sb.append("            \"actions\": []\n");
        sb.append("          },\n");

        sb.append("          \"mycos_dir\": {\n");
        sb.append("            \"id\": \"mycos_dir\",\n");
        sb.append("            \"text\": \"Follow the bioluminescent fungi southeast of here. Where the glow is brightest, you\\u0027ll find his grove. Don\\u0027t be alarmed by his appearance \\u2014 he is more philosopher than monster.\",\n");
        sb.append("            \"choices\": [\n");
        sb.append("              { \"label\": \"Thanks, Dren.\" }\n");
        sb.append("            ],\n");
        sb.append("            \"actions\": []\n");
        sb.append("          },\n");

        sb.append("          \"bye\": {\n");
        sb.append("            \"id\": \"bye\",\n");
        sb.append("            \"text\": \"Watch the roots. They\\u0027re always watching back.\",\n");
        sb.append("            \"choices\": [],\n");
        sb.append("            \"actions\": [\n");
        sb.append("              { \"type\": \"CLOSE_DIALOGUE\", \"target\": \"\", \"value\": \"\", \"amount\": 0 }\n");
        sb.append("            ]\n");
        sb.append("          }\n");

        sb.append("        }\n");
        sb.append("      },\n");
        sb.append("      \"x\": 85,\n");
        sb.append("      \"y\": 130\n");
        sb.append("    },\n");

        // NPC 4: Fungal Lord Mycos — philosopher in the fungi zone
        sb.append("    {\n");
        sb.append("      \"id\": \"npc_sylvandar_mycos\",\n");
        sb.append("      \"name\": \"Fungal Lord Mycos\",\n");
        sb.append("      \"spriteName\": \"npcs/townsman\",\n");
        sb.append("      \"type\": \"QUESTGIVER\",\n");
        sb.append("      \"defaultDialog\": \"All things rot. This is not tragedy \\u2014 it is promise.\",\n");
        sb.append("      \"questCompleteDialog\": \"\",\n");
        sb.append("      \"shopItemIds\": [],\n");
        sb.append("      \"dialogueTree\": {\n");
        sb.append("        \"startNodeId\": \"start\",\n");
        sb.append("        \"nodes\": {\n");

        sb.append("          \"start\": {\n");
        sb.append("            \"id\": \"start\",\n");
        sb.append("            \"text\": \"[A being of moss and mushroom regard you with ancient patience. Spores drift lazily from its cap.] Ah. A walker. Have you come to learn the necessary rot?\",\n");
        sb.append("            \"choices\": [\n");
        sb.append("              { \"label\": \"The necessary rot?\", \"nextNodeId\": \"philosophy\" },\n");
        sb.append("              { \"label\": \"I need your help with the overgrowth.\", \"nextNodeId\": \"overgrowth\" },\n");
        sb.append("              { \"label\": \"I should go.\", \"nextNodeId\": \"bye\" }\n");
        sb.append("            ],\n");
        sb.append("            \"actions\": []\n");
        sb.append("          },\n");

        sb.append("          \"philosophy\": {\n");
        sb.append("            \"id\": \"philosophy\",\n");
        sb.append("            \"text\": \"Growth without decay is cancer. A forest that never sheds its leaves suffocates under its own weight. Sylvandar remembers only spring \\u2014 he has forgotten autumn. The cycle requires both halves.\",\n");
        sb.append("            \"choices\": [\n");
        sb.append("              { \"label\": \"Can the cycle be restored?\", \"nextNodeId\": \"restore\" },\n");
        sb.append("              { \"label\": \"Deep words.\", \"nextNodeId\": \"bye\" }\n");
        sb.append("            ],\n");
        sb.append("            \"actions\": []\n");
        sb.append("          },\n");

        sb.append("          \"restore\": {\n");
        sb.append("            \"id\": \"restore\",\n");
        sb.append("            \"text\": \"Perhaps. My spores carry decomposition \\u2014 the balance to Sylvandar\\u0027s endless growth. Spread them at three blight-hearts in the forest, and the cycle may remember itself.\",\n");
        sb.append("            \"choices\": [\n");
        sb.append("              { \"label\": \"Give me the spores.\", \"nextNodeId\": \"spore_quest\" },\n");
        sb.append("              { \"label\": \"I need to think about this.\", \"nextNodeId\": \"bye\" }\n");
        sb.append("            ],\n");
        sb.append("            \"actions\": []\n");
        sb.append("          },\n");

        sb.append("          \"spore_quest\": {\n");
        sb.append("            \"id\": \"spore_quest\",\n");
        sb.append("            \"text\": \"Take these spore-pods. Plant them at the blight-hearts. But know this: decay is not gentle. Some of what Sylvandar has grown will die. That is the price of balance.\",\n");
        sb.append("            \"choices\": [\n");
        sb.append("              { \"label\": \"I accept the price.\" }\n");
        sb.append("            ],\n");
        sb.append("            \"actions\": [\n");
        sb.append("              { \"type\": \"GIVE_QUEST\", \"target\": \"the_necessary_rot\", \"value\": \"\", \"amount\": 0 },\n");
        sb.append("              { \"type\": \"GIVE_ITEM\", \"target\": \"mycos_spore_pod\", \"value\": \"\", \"amount\": 3 },\n");
        sb.append("              { \"type\": \"SET_FLAG\", \"target\": \"mycos_spore_quest\", \"value\": \"true\", \"amount\": 0 }\n");
        sb.append("            ]\n");
        sb.append("          },\n");

        sb.append("          \"overgrowth\": {\n");
        sb.append("            \"id\": \"overgrowth\",\n");
        sb.append("            \"text\": \"The overgrowth is Sylvandar\\u0027s grief made manifest. He lost the world once. Now he clings too tightly, growing life until it becomes a prison. The cure is not destruction \\u2014 it is acceptance of loss.\",\n");
        sb.append("            \"choices\": [\n");
        sb.append("              { \"label\": \"Can the cycle be restored?\", \"nextNodeId\": \"restore\" },\n");
        sb.append("              { \"label\": \"Wise words.\", \"nextNodeId\": \"bye\" }\n");
        sb.append("            ],\n");
        sb.append("            \"actions\": []\n");
        sb.append("          },\n");

        sb.append("          \"bye\": {\n");
        sb.append("            \"id\": \"bye\",\n");
        sb.append("            \"text\": \"Patience, walker. Even the rot takes time. [Spores drift upward in a gentle spiral.]\",\n");
        sb.append("            \"choices\": [],\n");
        sb.append("            \"actions\": [\n");
        sb.append("              { \"type\": \"CLOSE_DIALOGUE\", \"target\": \"\", \"value\": \"\", \"amount\": 0 }\n");
        sb.append("            ]\n");
        sb.append("          }\n");

        sb.append("        }\n");
        sb.append("      },\n");
        sb.append("      \"x\": 100,\n");
        sb.append("      \"y\": 138\n");
        sb.append("    }\n");

        sb.append("  ],\n");
        bw.write(sb.toString()); sb.setLength(0);

        // ── Town entrances ─────────────────────────────────────────────────
        sb.append("  \"townEntrances\": [\n");
        sb.append("    { \"townName\": \"roothollow\", \"worldX\": 65, \"worldY\": 95 },\n");
        sb.append("    { \"townName\": \"mossbridge\", \"worldX\": 165, \"worldY\": 105 },\n");
        sb.append("    { \"townName\": \"amber_grove\", \"worldX\": 115, \"worldY\": 38 }\n");
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

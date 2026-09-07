import java.io.*;
import java.util.Random;

/**
 * Generates data/overworlds/thalorax.rfmap — a 230x190 overworld map for
 * Island 5 (Thalorax / The Abyssal Depths).
 *
 * Deep ocean floor with coral atolls, kelp forests, pressure vents,
 * bioluminescent sand paths, and a boneyard trench in the south.
 * Three towns: Abyssport, Kelp Towers, Brightcoral.
 *
 * Run from project root:
 *   javac -encoding UTF-8 tools/AbyssalDepthsGen.java -d tools/
 *   java -cp tools AbyssalDepthsGen
 */
public class AbyssalDepthsGen {

    static final int COLS = 230, ROWS = 190;
    static char[][] g = new char[ROWS][COLS];
    static int[][] diff = new int[ROWS][COLS];
    static Random rng = new Random(55);

    // Tile constants — Unicode PUA for Thalorax tiles
    static final char DOCEAN  = '\uE070';  // deep ocean floor
    static final char PVENT   = '\uE071';  // pressure vent (damage)
    static final char CREEF   = '\uE072';  // coral reef (impassable)
    static final char KFOREST = '\uE073';  // kelp forest
    static final char BSAND   = '\uE074';  // bioluminescent sand
    static final char AROCK   = '\uE075';  // abyssal rock (impassable)
    static final char BFIELD  = '\uE076';  // bone field
    static final char PWARD   = '\uE077';  // pressure ward (impassable)
    static final char PORTAL_RET = '\uE079'; // return portal to Sylvandar

    // Shared tiles
    static final char WATER   = '~';
    static final char TOWN    = 'E';
    static final char DUNGEON = 'D';

    public static void main(String[] args) throws Exception {

        // ── Step 1: Fill with deep ocean floor ───────────────────────────────
        fill(0, ROWS - 1, 0, COLS - 1, DOCEAN);

        // ── Step 2: Central atoll cluster ────────────────────────────────────
        // Main central landmass — irregularly shaped atoll cluster
        // Center ~(115, 95), with three sub-atolls near town positions
        double cx = 115.0, cy = 95.0;
        double rx = 95.0, ry = 75.0;

        // Mark atoll interior as bioluminescent sand (base walkable terrain)
        for (int y = 0; y < ROWS; y++) {
            for (int x = 0; x < COLS; x++) {
                double dx = (x - cx) / rx;
                double dy = (y - cy) / ry;
                double dist = Math.sqrt(dx * dx + dy * dy);
                double noise = 0.10 * Math.sin(x * 0.20) * Math.cos(y * 0.18)
                             + 0.07 * Math.sin(x * 0.55 + y * 0.35)
                             + 0.05 * Math.cos(x * 0.12 - y * 0.28);
                if (dist + noise < 0.85) {
                    g[y][x] = BSAND;  // base atoll terrain
                }
            }
        }

        // Sub-atoll near Abyssport (SW) — extend land
        placeEllipse(65, 100, 30, 25, BSAND, 0.90);
        // Sub-atoll near Kelp Towers (E) — extend land
        placeEllipse(165, 105, 28, 22, BSAND, 0.88);
        // Sub-atoll near Brightcoral (N) — extend land
        placeEllipse(115, 42, 22, 18, BSAND, 0.85);

        // ── Step 3: Coral reef borders ───────────────────────────────────────
        // Ring of impassable coral around atoll edges with navigable gaps
        char[][] snap = copyGrid();
        for (int y = 0; y < ROWS; y++) {
            for (int x = 0; x < COLS; x++) {
                if (snap[y][x] == DOCEAN) continue;
                boolean nearOcean = false;
                for (int dy = -3; dy <= 3 && !nearOcean; dy++) {
                    for (int dx = -3; dx <= 3 && !nearOcean; dx++) {
                        int ny = y + dy, nx = x + dx;
                        if (ny < 0 || ny >= ROWS || nx < 0 || nx >= COLS) {
                            nearOcean = true;
                        } else if (snap[ny][nx] == DOCEAN) {
                            nearOcean = true;
                        }
                    }
                }
                if (nearOcean) {
                    boolean veryNear = false;
                    for (int dy = -1; dy <= 1 && !veryNear; dy++) {
                        for (int dx = -1; dx <= 1 && !veryNear; dx++) {
                            int ny = y + dy, nx = x + dx;
                            if (ny < 0 || ny >= ROWS || nx < 0 || nx >= COLS) {
                                veryNear = true;
                            } else if (snap[ny][nx] == DOCEAN) {
                                veryNear = true;
                            }
                        }
                    }
                    if (veryNear) {
                        // Outer edge: coral reef (impassable) with gaps
                        double gapNoise = Math.sin(x * 0.3 + y * 0.2) + Math.cos(x * 0.15 - y * 0.25);
                        if (gapNoise < 0.6) {
                            g[y][x] = CREEF;
                        } else {
                            g[y][x] = DOCEAN;  // gap in reef
                        }
                    }
                }
            }
        }

        // ── Step 4: Kelp forests in mid-depth zones ─────────────────────────
        // Large kelp forests between towns
        placeKelpZone(90, 75, 22);   // NW kelp forest
        placeKelpZone(140, 80, 20);  // NE kelp forest
        placeKelpZone(85, 120, 18);  // SW kelp forest
        placeKelpZone(150, 120, 16); // SE kelp forest
        placeKelpZone(115, 65, 15);  // central-north kelp
        placeKelpZone(130, 140, 14); // south kelp pocket

        // ── Step 5: Abyssal rock formations (impassable, scattered) ─────────
        // Western rock cluster
        placeRockFormation(40, 80, 18);
        // Eastern rock cluster
        placeRockFormation(190, 90, 16);
        // Northern scattered rocks
        placeRockFormation(115, 20, 14);
        // South-central rocks
        placeRockFormation(130, 155, 12);
        // Scattered individual rocks
        int rocksPlaced = 0, attempts = 0;
        while (rocksPlaced < 50 && attempts < 6000) {
            int rx2 = 10 + rng.nextInt(210);
            int ry2 = 10 + rng.nextInt(170);
            attempts++;
            if (rx2 < COLS && ry2 < ROWS && g[ry2][rx2] == DOCEAN) {
                boolean tooClose = false;
                for (int dy = -3; dy <= 3 && !tooClose; dy++) {
                    for (int dx = -3; dx <= 3 && !tooClose; dx++) {
                        int ny = ry2 + dy, nx = rx2 + dx;
                        if (ny >= 0 && ny < ROWS && nx >= 0 && nx < COLS && g[ny][nx] == AROCK) {
                            tooClose = true;
                        }
                    }
                }
                if (!tooClose) {
                    g[ry2][rx2] = AROCK;
                    rocksPlaced++;
                }
            }
        }

        // ── Step 6: Pressure vent clusters (damage hazard zones) ────────────
        placeVentCluster(75, 60, 12);    // NW vent field
        placeVentCluster(155, 70, 10);   // NE vent field
        placeVentCluster(100, 130, 14);  // south-central vent field
        placeVentCluster(170, 140, 11);  // SE vent field
        placeVentCluster(50, 110, 9);    // far west vents

        // ── Step 7: Boneyard Trench — long bone_field region in south ───────
        for (int y = 145; y <= 175; y++) {
            for (int x = 40; x <= 190; x++) {
                if (x < 0 || x >= COLS || y < 0 || y >= ROWS) continue;
                if (g[y][x] != DOCEAN && g[y][x] != BSAND) continue;
                double dx2 = (x - 115.0) / 80.0;
                double dy2 = (y - 160.0) / 18.0;
                double d = dx2 * dx2 + dy2 * dy2;
                double noise = 0.15 * Math.sin(x * 0.3) * Math.cos(y * 0.25);
                if (d + noise < 1.0) {
                    if (rng.nextDouble() < 0.55) {
                        g[y][x] = BFIELD;
                    }
                }
            }
        }

        // ── Step 8: Pressure ward ring around Pressure Temple ───────────────
        // Temple area at (115, 140) — central-south
        int tx = 115, ty = 140;
        for (int y = ty - 12; y <= ty + 12; y++) {
            for (int x = tx - 12; x <= tx + 12; x++) {
                if (x < 0 || x >= COLS || y < 0 || y >= ROWS) continue;
                double d = Math.sqrt((x - tx) * (x - tx) + (y - ty) * (y - ty));
                double noise = 1.5 * Math.sin(x * 0.5) * Math.cos(y * 0.4);
                if (d > 8 + noise && d < 11 + noise) {
                    g[y][x] = PWARD;
                }
            }
        }
        // Clear a walkable entrance gap to the south
        for (int y = ty + 8; y <= ty + 12; y++) {
            for (int x = tx - 2; x <= tx + 2; x++) {
                if (x >= 0 && x < COLS && y >= 0 && y < ROWS && g[y][x] == PWARD) {
                    g[y][x] = BSAND;
                }
            }
        }
        // Clear inside the ring
        for (int y = ty - 7; y <= ty + 7; y++) {
            for (int x = tx - 7; x <= tx + 7; x++) {
                if (x < 0 || x >= COLS || y < 0 || y >= ROWS) continue;
                double d = Math.sqrt((x - tx) * (x - tx) + (y - ty) * (y - ty));
                if (d < 7) {
                    g[y][x] = BSAND;
                }
            }
        }

        // ── Step 9: Bioluminescent sand paths connecting towns ──────────────
        // These are the "roads" of the deep ocean
        // Abyssport (65, 95) → Kelp Towers (165, 105)
        sandPath(65, 95, 115, 95);   // west to center
        sandPath(115, 95, 165, 105); // center to east

        // Abyssport (65, 95) → Brightcoral (115, 38)
        sandPath(65, 95, 65, 55);    // go north
        sandPath(65, 55, 115, 38);   // go NE

        // Kelp Towers (165, 105) → Brightcoral (115, 38)
        sandPath(165, 105, 165, 55); // go north
        sandPath(165, 55, 115, 38);  // go NW

        // Path to Pressure Temple (115, 140)
        sandPath(115, 95, 115, 140); // central road south to temple

        // Path to Boneyard Trench
        sandPath(115, 140, 115, 160);

        // ── Step 10: Clearings around towns ─────────────────────────────────
        // Clear terrain around town entrances to be walkable bioluminescent sand
        clearAroundTown(65, 95, 8);   // Abyssport
        clearAroundTown(165, 105, 7); // Kelp Towers
        clearAroundTown(115, 38, 6);  // Brightcoral

        // ── Step 11: Town entrances ─────────────────────────────────────────
        g[95][65]   = TOWN;   // Abyssport
        g[105][165] = TOWN;   // Kelp Towers
        g[38][115]  = TOWN;   // Brightcoral

        // ── Step 12: Dungeon entrance — Pressure Temple ─────────────────────
        g[140][115] = DUNGEON;

        // ── Step 13: Return portal to Sylvandar (free, near Abyssport) ──────
        g[98][58] = PORTAL_RET;

        // ── Step 14: Spawn difficulty grid (16–19) ──────────────────────────
        // Monster levels 16-19 → difficulty 31-38
        // Formula: monsterLevel = 1 + (difficulty * 49) / 99
        int[][] towns = {{65,95}, {165,105}, {115,38}};
        int minDiff = 31, maxDiff = 38; // levels 16-19
        for (int y = 0; y < ROWS; y++) {
            for (int x = 0; x < COLS; x++) {
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

        // ── Write JSON ──────────────────────────────────────────────────────
        writeJSON("data/overworlds/thalorax.rfmap");
        System.out.println("Done — data/overworlds/thalorax.rfmap written.");
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

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

    static void placeEllipse(int ecx, int ecy, int erx, int ery, char tile, double threshold) {
        for (int y = ecy - ery - 5; y <= ecy + ery + 5; y++) {
            for (int x = ecx - erx - 5; x <= ecx + erx + 5; x++) {
                if (x < 0 || x >= COLS || y < 0 || y >= ROWS) continue;
                double dx = (x - ecx) / (double) erx;
                double dy = (y - ecy) / (double) ery;
                double d = Math.sqrt(dx * dx + dy * dy);
                double noise = 0.08 * Math.sin(x * 0.3) * Math.cos(y * 0.25)
                             + 0.05 * Math.sin(x * 0.6 + y * 0.4);
                if (d + noise < threshold) {
                    g[y][x] = tile;
                }
            }
        }
    }

    static void placeKelpZone(int kcx, int kcy, int radius) {
        for (int y = kcy - radius; y <= kcy + radius; y++) {
            for (int x = kcx - radius; x <= kcx + radius; x++) {
                if (x < 0 || x >= COLS || y < 0 || y >= ROWS) continue;
                if (g[y][x] != BSAND && g[y][x] != DOCEAN) continue;
                double d = Math.sqrt((x - kcx) * (x - kcx) + (y - kcy) * (y - kcy));
                if (d < radius && rng.nextDouble() < 0.40) {
                    g[y][x] = KFOREST;
                }
            }
        }
    }

    static void placeRockFormation(int rcx, int rcy, int radius) {
        for (int y = rcy - radius; y <= rcy + radius; y++) {
            for (int x = rcx - radius; x <= rcx + radius; x++) {
                if (x < 0 || x >= COLS || y < 0 || y >= ROWS) continue;
                if (g[y][x] != DOCEAN) continue;
                double d = Math.sqrt((x - rcx) * (x - rcx) + (y - rcy) * (y - rcy));
                if (d < radius && rng.nextDouble() < 0.30) {
                    g[y][x] = AROCK;
                }
            }
        }
    }

    static void placeVentCluster(int vcx, int vcy, int radius) {
        for (int y = vcy - radius; y <= vcy + radius; y++) {
            for (int x = vcx - radius; x <= vcx + radius; x++) {
                if (x < 0 || x >= COLS || y < 0 || y >= ROWS) continue;
                char c = g[y][x];
                if (c != BSAND && c != DOCEAN) continue;
                double d = Math.sqrt((x - vcx) * (x - vcx) + (y - vcy) * (y - vcy));
                if (d < radius && rng.nextDouble() < 0.30) {
                    g[y][x] = PVENT;
                }
            }
        }
    }

    static void sandPath(int x1, int y1, int x2, int y2) {
        // Bresenham-style thick path (3 tiles wide)
        int steps = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1));
        if (steps == 0) return;
        for (int i = 0; i <= steps; i++) {
            int px = x1 + (x2 - x1) * i / steps;
            int py = y1 + (y2 - y1) * i / steps;
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    int nx = px + dx, ny = py + dy;
                    if (nx >= 0 && nx < COLS && ny >= 0 && ny < ROWS) {
                        char c = g[ny][nx];
                        // Only overwrite non-special tiles
                        if (c == DOCEAN || c == KFOREST || c == PVENT || c == BFIELD) {
                            g[ny][nx] = BSAND;
                        }
                    }
                }
            }
        }
    }

    static void clearAroundTown(int tcx, int tcy, int radius) {
        for (int y = tcy - radius; y <= tcy + radius; y++) {
            for (int x = tcx - radius; x <= tcx + radius; x++) {
                if (x < 0 || x >= COLS || y < 0 || y >= ROWS) continue;
                double d = Math.sqrt((x - tcx) * (x - tcx) + (y - tcy) * (y - tcy));
                if (d < radius) {
                    g[y][x] = BSAND;
                }
            }
        }
    }

    // ── JSON output ─────────────────────────────────────────────────────────

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
        sb.append("  \"name\": \"thalorax\",\n");
        sb.append("  \"width\": ").append(COLS).append(",\n");
        sb.append("  \"height\": ").append(ROWS).append(",\n");

        // ── Tiles ────────────────────────────────────────────────────────────
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

        // ── Spawn difficulty ────────────────────────────────────────────────
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

        // ── NPCs ────────────────────────────────────────────────────────────
        sb.append("  \"npcs\": [\n");

        // NPC 1: Depth-Warden Silt — overworld guide near Abyssport
        sb.append("    {\n");
        sb.append("      \"id\": \"npc_thalorax_silt\",\n");
        sb.append("      \"name\": \"Depth-Warden Silt\",\n");
        sb.append("      \"spriteName\": \"npcs/townsman\",\n");
        sb.append("      \"type\": \"QUESTGIVER\",\n");
        sb.append("      \"defaultDialog\": \"The deep remembers what the surface forgets.\",\n");
        sb.append("      \"questCompleteDialog\": \"\",\n");
        sb.append("      \"shopItemIds\": [],\n");
        sb.append("      \"dialogueTree\": {\n");
        sb.append("        \"startNodeId\": \"start\",\n");
        sb.append("        \"nodes\": {\n");

        sb.append("          \"start\": {\n");
        sb.append("            \"id\": \"start\",\n");
        sb.append("            \"text\": \"[A figure in barnacle-crusted armor stands watching the darkness.] You have descended to the Abyssal Depths. I am Silt, warden of the pressure wards. Thalorax\\u0027s domain is not kind to visitors.\",\n");
        sb.append("            \"choices\": [\n");
        sb.append("              { \"label\": \"What is this place?\", \"nextNodeId\": \"explain\" },\n");
        sb.append("              { \"label\": \"I seek the Key of Depths.\", \"nextNodeId\": \"key_ask\" },\n");
        sb.append("              { \"label\": \"I can handle it.\", \"nextNodeId\": \"farewell\" }\n");
        sb.append("            ],\n");
        sb.append("            \"actions\": []\n");
        sb.append("          },\n");

        sb.append("          \"explain\": {\n");
        sb.append("            \"id\": \"explain\",\n");
        sb.append("            \"text\": \"The Crushing Deep. Thalorax sank his domain beneath the waves after the Shattering. He believes that truth is found only under pressure \\u2014 that what survives compression is worthy. The wards keep the worst of it at bay, but they\\u0027re cracking.\",\n");
        sb.append("            \"choices\": [\n");
        sb.append("              { \"label\": \"The wards are failing?\", \"nextNodeId\": \"wards\" },\n");
        sb.append("              { \"label\": \"I\\u0027ll be careful.\", \"nextNodeId\": \"farewell\" }\n");
        sb.append("            ],\n");
        sb.append("            \"actions\": []\n");
        sb.append("          },\n");

        sb.append("          \"wards\": {\n");
        sb.append("            \"id\": \"wards\",\n");
        sb.append("            \"text\": \"Three ward fragments have drifted into the deep currents. Without them, the pressure dome over Abyssport will collapse. The Drowned Engineer in the city knows more \\u2014 speak with her.\",\n");
        sb.append("            \"choices\": [\n");
        sb.append("              { \"label\": \"I\\u0027ll find the Engineer.\", \"nextNodeId\": \"farewell\" }\n");
        sb.append("            ],\n");
        sb.append("            \"actions\": [\n");
        sb.append("              { \"type\": \"SET_FLAG\", \"target\": \"thalorax_wards_warned\", \"value\": \"true\", \"amount\": 0 }\n");
        sb.append("            ]\n");
        sb.append("          },\n");

        sb.append("          \"key_ask\": {\n");
        sb.append("            \"id\": \"key_ask\",\n");
        sb.append("            \"text\": \"The Key of Depths is earned in Thalorax\\u0027s trial, deep in the Pressure Temple to the south. But first you must understand what he values: endurance, sacrifice, and the willingness to be crushed and reformed.\",\n");
        sb.append("            \"choices\": [\n");
        sb.append("              { \"label\": \"Where is the Pressure Temple?\", \"nextNodeId\": \"temple_dir\" },\n");
        sb.append("              { \"label\": \"I\\u0027m ready.\", \"nextNodeId\": \"farewell\" }\n");
        sb.append("            ],\n");
        sb.append("            \"actions\": []\n");
        sb.append("          },\n");

        sb.append("          \"temple_dir\": {\n");
        sb.append("            \"id\": \"temple_dir\",\n");
        sb.append("            \"text\": \"South, past the pressure vents, beyond the boneyard. You will see the ward-ring \\u2014 a circle of glowing runes. The temple entrance lies within. The abyssal wraiths grow thicker near it. Be prepared.\",\n");
        sb.append("            \"choices\": [\n");
        sb.append("              { \"label\": \"Thank you, Silt.\" }\n");
        sb.append("            ],\n");
        sb.append("            \"actions\": []\n");
        sb.append("          },\n");

        sb.append("          \"farewell\": {\n");
        sb.append("            \"id\": \"farewell\",\n");
        sb.append("            \"text\": \"The deep is patient. It has all the time in the world to crush you. [He turns back to watching the darkness.]\",\n");
        sb.append("            \"choices\": [],\n");
        sb.append("            \"actions\": [\n");
        sb.append("              { \"type\": \"CLOSE_DIALOGUE\", \"target\": \"\", \"value\": \"\", \"amount\": 0 }\n");
        sb.append("            ]\n");
        sb.append("          }\n");

        sb.append("        }\n");  // end nodes
        sb.append("      },\n");  // end dialogueTree
        sb.append("      \"x\": 58,\n");
        sb.append("      \"y\": 92\n");
        sb.append("    },\n");

        // NPC 2: Lure — an anglerfish-touched child (eerie bioluminescent NPC)
        sb.append("    {\n");
        sb.append("      \"id\": \"npc_thalorax_lure\",\n");
        sb.append("      \"name\": \"Lure\",\n");
        sb.append("      \"spriteName\": \"npcs/townsman\",\n");
        sb.append("      \"type\": \"TOWNSFOLK\",\n");
        sb.append("      \"defaultDialog\": \"...the light shows the way down...\",\n");
        sb.append("      \"questCompleteDialog\": \"\",\n");
        sb.append("      \"shopItemIds\": [],\n");
        sb.append("      \"dialogueTree\": {\n");
        sb.append("        \"startNodeId\": \"start\",\n");
        sb.append("        \"nodes\": {\n");

        sb.append("          \"start\": {\n");
        sb.append("            \"id\": \"start\",\n");
        sb.append("            \"text\": \"[A pale child sits cross-legged on the ocean floor. A faint bioluminescent glow pulses from her forehead.] ...you followed the light too? Everyone follows the light.\",\n");
        sb.append("            \"choices\": [\n");
        sb.append("              { \"label\": \"What is that light?\", \"nextNodeId\": \"light\" },\n");
        sb.append("              { \"label\": \"Are you all right?\", \"nextNodeId\": \"okay\" },\n");
        sb.append("              { \"label\": \"Goodbye.\", \"nextNodeId\": \"bye\" }\n");
        sb.append("            ],\n");
        sb.append("            \"actions\": []\n");
        sb.append("          },\n");

        sb.append("          \"light\": {\n");
        sb.append("            \"id\": \"light\",\n");
        sb.append("            \"text\": \"Thalorax gave it to me. He said I was brave for coming down so deep. The light shows me things in the dark \\u2014 old bones, old ships, old truths. Things people tried to sink.\",\n");
        sb.append("            \"choices\": [\n");
        sb.append("              { \"label\": \"What kind of truths?\", \"nextNodeId\": \"truths\" },\n");
        sb.append("              { \"label\": \"Be careful down here.\", \"nextNodeId\": \"bye\" }\n");
        sb.append("            ],\n");
        sb.append("            \"actions\": []\n");
        sb.append("          },\n");

        sb.append("          \"truths\": {\n");
        sb.append("            \"id\": \"truths\",\n");
        sb.append("            \"text\": \"The kind people bury. Seraphine came this way once \\u2014 I saw her footprints in the sand. She was looking for something the gods hid. The Pressure Temple has her journal... if the wraiths haven\\u0027t eaten the pages.\",\n");
        sb.append("            \"choices\": [\n");
        sb.append("              { \"label\": \"Thank you, Lure.\", \"nextNodeId\": \"bye\" }\n");
        sb.append("            ],\n");
        sb.append("            \"actions\": [\n");
        sb.append("              { \"type\": \"SET_FLAG\", \"target\": \"lure_seraphine_hint\", \"value\": \"true\", \"amount\": 0 }\n");
        sb.append("            ]\n");
        sb.append("          },\n");

        sb.append("          \"okay\": {\n");
        sb.append("            \"id\": \"okay\",\n");
        sb.append("            \"text\": \"The pressure doesn\\u0027t bother me anymore. I think I\\u0027m becoming part of the deep. Like the coral becomes part of the reef. It\\u0027s not scary. It\\u0027s just... inevitable.\",\n");
        sb.append("            \"choices\": [\n");
        sb.append("              { \"label\": \"Stay safe, little one.\", \"nextNodeId\": \"bye\" }\n");
        sb.append("            ],\n");
        sb.append("            \"actions\": []\n");
        sb.append("          },\n");

        sb.append("          \"bye\": {\n");
        sb.append("            \"id\": \"bye\",\n");
        sb.append("            \"text\": \"[The glow on her forehead pulses once, illuminating the dark water around you.] Follow the bioluminescent sand. It\\u0027s the safe path. Mostly.\",\n");
        sb.append("            \"choices\": [],\n");
        sb.append("            \"actions\": [\n");
        sb.append("              { \"type\": \"CLOSE_DIALOGUE\", \"target\": \"\", \"value\": \"\", \"amount\": 0 }\n");
        sb.append("            ]\n");
        sb.append("          }\n");

        sb.append("        }\n");
        sb.append("      },\n");
        sb.append("      \"x\": 105,\n");
        sb.append("      \"y\": 110\n");
        sb.append("    },\n");

        // NPC 3: The Leviathan's Jaw — skeletal remains NPC in the Boneyard
        sb.append("    {\n");
        sb.append("      \"id\": \"npc_thalorax_jaw\",\n");
        sb.append("      \"name\": \"The Leviathan\\u0027s Jaw\",\n");
        sb.append("      \"spriteName\": \"npcs/townsman\",\n");
        sb.append("      \"type\": \"TOWNSFOLK\",\n");
        sb.append("      \"defaultDialog\": \"[Ancient bones hum with residual power.]\",\n");
        sb.append("      \"questCompleteDialog\": \"\",\n");
        sb.append("      \"shopItemIds\": [],\n");
        sb.append("      \"dialogueTree\": {\n");
        sb.append("        \"startNodeId\": \"start\",\n");
        sb.append("        \"nodes\": {\n");

        sb.append("          \"start\": {\n");
        sb.append("            \"id\": \"start\",\n");
        sb.append("            \"text\": \"[You stand before the fossilized jaw of an ancient leviathan. Runes carved into the bone glow faintly.] The bones whisper: \\u0027We were the first to sink. We were the first to understand.\\u0027\",\n");
        sb.append("            \"choices\": [\n");
        sb.append("              { \"label\": \"Touch the runes.\", \"nextNodeId\": \"touch\" },\n");
        sb.append("              { \"label\": \"Read the inscriptions.\", \"nextNodeId\": \"read\" },\n");
        sb.append("              { \"label\": \"Step back.\", \"nextNodeId\": \"bye\" }\n");
        sb.append("            ],\n");
        sb.append("            \"actions\": []\n");
        sb.append("          },\n");

        sb.append("          \"touch\": {\n");
        sb.append("            \"id\": \"touch\",\n");
        sb.append("            \"text\": \"[A vision floods your mind: a world before the Shattering, leviathans swimming through crystal seas, and Thalorax \\u2014 not a god of crushing darkness, but a guardian of the deep\\u0027s wonders. Then the Shattering came, and wonder became pressure, became survival, became inevitability.]\",\n");
        sb.append("            \"choices\": [\n");
        sb.append("              { \"label\": \"[Release the bone.]\", \"nextNodeId\": \"bye\" }\n");
        sb.append("            ],\n");
        sb.append("            \"actions\": [\n");
        sb.append("              { \"type\": \"SET_FLAG\", \"target\": \"thalorax_leviathan_vision\", \"value\": \"true\", \"amount\": 0 },\n");
        sb.append("              { \"type\": \"LOG_MESSAGE\", \"target\": \"A vision of the world before the Shattering fills your mind.\", \"value\": \"\", \"amount\": 0 }\n");
        sb.append("            ]\n");
        sb.append("          },\n");

        sb.append("          \"read\": {\n");
        sb.append("            \"id\": \"read\",\n");
        sb.append("            \"text\": \"The inscriptions are old \\u2014 older than the gods\\u0027 current forms. They speak of Aqualon, the drowned world beneath the world. \\u0027What sinks is not lost. What is crushed is made diamond. Thalorax remembers the weight of truth.\\u0027\",\n");
        sb.append("            \"choices\": [\n");
        sb.append("              { \"label\": \"Interesting.\", \"nextNodeId\": \"bye\" }\n");
        sb.append("            ],\n");
        sb.append("            \"actions\": [\n");
        sb.append("              { \"type\": \"SET_FLAG\", \"target\": \"read_leviathan_inscription\", \"value\": \"true\", \"amount\": 0 }\n");
        sb.append("            ]\n");
        sb.append("          },\n");

        sb.append("          \"bye\": {\n");
        sb.append("            \"id\": \"bye\",\n");
        sb.append("            \"text\": \"[The runes dim. The boneyard is silent once more.]\",\n");
        sb.append("            \"choices\": [],\n");
        sb.append("            \"actions\": [\n");
        sb.append("              { \"type\": \"CLOSE_DIALOGUE\", \"target\": \"\", \"value\": \"\", \"amount\": 0 }\n");
        sb.append("            ]\n");
        sb.append("          }\n");

        sb.append("        }\n");
        sb.append("      },\n");
        sb.append("      \"x\": 115,\n");
        sb.append("      \"y\": 160\n");
        sb.append("    }\n");

        sb.append("  ],\n");
        bw.write(sb.toString()); sb.setLength(0);

        // ── Town entrances ──────────────────────────────────────────────────
        sb.append("  \"townEntrances\": [\n");
        sb.append("    { \"townName\": \"abyssport\", \"worldX\": 65, \"worldY\": 95 },\n");
        sb.append("    { \"townName\": \"kelp_towers\", \"worldX\": 165, \"worldY\": 105 },\n");
        sb.append("    { \"townName\": \"brightcoral\", \"worldX\": 115, \"worldY\": 38 }\n");
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

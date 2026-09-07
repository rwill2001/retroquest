import java.io.*;
import java.util.Random;

/**
 * Generates data/overworlds/bellorak.rfmap — a 230x190 overworld map for
 * Island 7 (Bellorak / The Golden War Isles).
 *
 * War-torn landscape of scorched earth, trenches, craters, iron barricades,
 * siege walls, and a central Great Arena. Two factions: Gold Guard (north)
 * and Iron Reckoner (south). Three towns: Gold Guard Camp, Iron Reckoner Camp,
 * Neutral Ground.
 *
 * Run from project root:
 *   javac -encoding UTF-8 tools/BellorakGen.java -d tools/
 *   java -cp tools BellorakGen
 */
public class BellorakGen {

    static final int COLS = 230, ROWS = 190;
    static char[][] g = new char[ROWS][COLS];
    static int[][] diff = new int[ROWS][COLS];
    static Random rng = new Random(77);

    // Tile constants — Unicode PUA for Bellorak tiles
    static final char SCORCHED   = '\uE090';  // scorched earth (base walkable)
    static final char TRENCH     = '\uE091';  // trench (high encounters)
    static final char BARRICADE  = '\uE092';  // iron barricade (impassable)
    static final char CRATER     = '\uE093';  // crater (walkable)
    static final char WARCAMP    = '\uE094';  // war camp floor (low encounters)
    static final char SIEGE      = '\uE095';  // siege wall (impassable)
    static final char ARENA      = '\uE096';  // arena stone (walkable)
    static final char BURNING    = '\uE097';  // burning ruin (impassable)
    static final char PORTAL_RET = '\uE099';  // return portal to Umbryn
    static final char PORTAL_FWD = '\uE09F';  // portal to Cradle of Shards

    // Shared tiles
    static final char WATER   = '~';
    static final char TREE    = 'T';
    static final char MTN     = 'M';
    static final char TOWN    = 'E';
    static final char DUNGEON = 'D';

    public static void main(String[] args) throws Exception {

        // ── Step 1: Fill with scorched earth ─────────────────────────────
        fill(0, ROWS - 1, 0, COLS - 1, SCORCHED);

        // ── Step 2: Water border (5-8 tiles deep, irregular) ─────────────
        double cx = 115.0, cy = 95.0;
        double rx = 105.0, ry = 85.0;

        for (int y = 0; y < ROWS; y++) {
            for (int x = 0; x < COLS; x++) {
                double dx = (x - cx) / rx;
                double dy = (y - cy) / ry;
                double dist = Math.sqrt(dx * dx + dy * dy);
                double noise = 0.08 * Math.sin(x * 0.15) * Math.cos(y * 0.20)
                             + 0.06 * Math.sin(x * 0.45 + y * 0.25)
                             + 0.04 * Math.cos(x * 0.12 - y * 0.28);
                if (dist + noise > 0.90) {
                    g[y][x] = WATER;
                }
            }
        }

        // ── Step 3: Mountain perimeter inside water ──────────────────────
        for (int y = 0; y < ROWS; y++) {
            for (int x = 0; x < COLS; x++) {
                double dx = (x - cx) / rx;
                double dy = (y - cy) / ry;
                double dist = Math.sqrt(dx * dx + dy * dy);
                double noise = 0.06 * Math.sin(x * 0.20) * Math.cos(y * 0.18)
                             + 0.04 * Math.sin(x * 0.55 + y * 0.30);
                if (dist + noise > 0.80 && dist + noise <= 0.90 && g[y][x] != WATER) {
                    if (rng.nextDouble() < 0.55) {
                        g[y][x] = MTN;
                    }
                }
            }
        }

        // Extend walkable area around key locations
        placeEllipse(115, 95, 40, 35, SCORCHED, 0.92);  // center arena region
        placeEllipse(60, 40, 25, 20, SCORCHED, 0.90);   // Gold Guard Camp
        placeEllipse(170, 140, 25, 20, SCORCHED, 0.90);  // Iron Reckoner Camp
        placeEllipse(115, 180, 15, 8, SCORCHED, 0.88);   // arrival area

        // ── Step 4: North territory — Gold Guard (y 20-80) ───────────────
        // War camp floor zones
        placeCampZone(60, 40, 18);   // Main Gold Guard camp area
        placeCampZone(45, 55, 12);   // West outpost
        placeCampZone(80, 35, 10);   // East outpost
        placeCampZone(100, 50, 8);   // Forward post

        // Siege walls and barricades (Gold Guard fortifications)
        // Main defensive line across north territory
        for (int x = 35; x < 95; x++) {
            int wy = 65 + (int)(3 * Math.sin(x * 0.10));
            if (x >= 0 && x < COLS && wy >= 0 && wy < ROWS && g[wy][x] == SCORCHED) {
                g[wy][x] = (x % 5 == 0) ? BARRICADE : SIEGE;
            }
        }
        // Flanking walls around Gold Guard Camp
        placeBarricadeRing(60, 40, 12, 15);

        // Scattered barricade clusters in north
        placeBarricadeCluster(50, 30, 8);
        placeBarricadeCluster(75, 50, 6);
        placeBarricadeCluster(90, 60, 7);

        // ── Step 5: South territory — Iron Reckoner (y 110-170) ──────────
        // Trench networks
        placeTrenchLine(130, 120, 190, 120);  // Main east-west trench
        placeTrenchLine(170, 115, 170, 155);  // N-S trench near camp
        placeTrenchLine(140, 130, 180, 130);  // Secondary trench
        placeTrenchLine(150, 140, 150, 160);  // Forward trench

        // Crater fields
        placeCraterField(155, 125, 15);  // Central crater field
        placeCraterField(180, 135, 12);  // East craters
        placeCraterField(140, 150, 14);  // Southwest craters
        placeCraterField(160, 110, 10);  // North edge craters

        // War camp around Iron Reckoner
        placeCampZone(170, 140, 15);

        // Barricades in south
        placeBarricadeCluster(145, 135, 8);
        placeBarricadeCluster(185, 145, 6);

        // ── Step 6: No-man's-land (y 80-110) — craters, burning ruins ────
        // Crater field across center
        placeCraterField(90, 90, 18);
        placeCraterField(140, 95, 16);
        placeCraterField(70, 100, 12);
        placeCraterField(160, 85, 10);

        // Burning ruins scattered in no-man's-land
        placeBurningRuins(80, 85, 15);
        placeBurningRuins(130, 100, 12);
        placeBurningRuins(100, 105, 10);
        placeBurningRuins(155, 90, 8);

        // Some trees in sheltered areas
        placeTreeCluster(40, 70, 8);
        placeTreeCluster(190, 80, 7);
        placeTreeCluster(35, 130, 6);
        placeTreeCluster(195, 120, 5);

        // ── Step 7: Great Arena (centered x=115, y=95, ~20x20) ──────────
        int ax = 115, ay = 95;
        // Arena floor
        for (int y = ay - 10; y <= ay + 10; y++) {
            for (int x = ax - 10; x <= ax + 10; x++) {
                if (x < 0 || x >= COLS || y < 0 || y >= ROWS) continue;
                double d = Math.sqrt((x - ax) * (x - ax) + (y - ay) * (y - ay));
                if (d < 10) {
                    g[y][x] = ARENA;
                }
            }
        }
        // Arena wall ring (siege walls)
        for (int y = ay - 12; y <= ay + 12; y++) {
            for (int x = ax - 12; x <= ax + 12; x++) {
                if (x < 0 || x >= COLS || y < 0 || y >= ROWS) continue;
                double d = Math.sqrt((x - ax) * (x - ax) + (y - ay) * (y - ay));
                if (d >= 10 && d < 12) {
                    g[y][x] = SIEGE;
                }
            }
        }
        // Arena entrances (gaps in the wall) — N, S, E, W
        for (int i = -1; i <= 1; i++) {
            // North entrance
            if (ay - 11 >= 0 && ay - 11 < ROWS && ax + i >= 0 && ax + i < COLS)
                g[ay - 11][ax + i] = ARENA;
            if (ay - 10 >= 0 && ay - 10 < ROWS && ax + i >= 0 && ax + i < COLS)
                g[ay - 10][ax + i] = ARENA;
            // South entrance
            if (ay + 11 >= 0 && ay + 11 < ROWS && ax + i >= 0 && ax + i < COLS)
                g[ay + 11][ax + i] = ARENA;
            if (ay + 10 >= 0 && ay + 10 < ROWS && ax + i >= 0 && ax + i < COLS)
                g[ay + 10][ax + i] = ARENA;
            // East entrance
            if (ay + i >= 0 && ay + i < ROWS && ax + 11 >= 0 && ax + 11 < COLS)
                g[ay + i][ax + 11] = ARENA;
            if (ay + i >= 0 && ay + i < ROWS && ax + 10 >= 0 && ax + 10 < COLS)
                g[ay + i][ax + 10] = ARENA;
            // West entrance
            if (ay + i >= 0 && ay + i < ROWS && ax - 11 >= 0 && ax - 11 < COLS)
                g[ay + i][ax - 11] = ARENA;
            if (ay + i >= 0 && ay + i < ROWS && ax - 10 >= 0 && ax - 10 < COLS)
                g[ay + i][ax - 10] = ARENA;
        }

        // ── Step 8: Paths connecting key locations ───────────────────────
        // Arrival (115, 180) → Arena (115, 95)
        warPath(115, 178, 115, 95);
        // Arena → Gold Guard Camp (60, 40)
        warPath(115, 85, 85, 60);
        warPath(85, 60, 60, 40);
        // Arena → Iron Reckoner Camp (170, 140)
        warPath(115, 105, 140, 120);
        warPath(140, 120, 170, 140);
        // Arena → War Beneath dungeon (90, 70)
        warPath(105, 90, 90, 70);
        // Arena → Iron Pit dungeon (140, 120)
        warPath(125, 100, 140, 120);
        // Gold Guard → War Beneath
        warPath(60, 45, 90, 70);

        // ── Step 9: Clear areas around towns and key locations ───────────
        clearAroundTown(60, 40, 7);    // Gold Guard Camp
        clearAroundTown(170, 140, 7);  // Iron Reckoner Camp
        clearAroundTown(115, 95, 5);   // Neutral Ground (inside arena)
        clearAroundTown(115, 180, 5);  // Arrival portal area
        clearAroundTown(90, 70, 4);    // War Beneath dungeon
        clearAroundTown(140, 120, 4);  // Iron Pit dungeon

        // ── Step 10: Town entrances ──────────────────────────────────────
        g[40][60]   = TOWN;    // Gold Guard Camp
        g[140][170] = TOWN;    // Iron Reckoner Camp
        g[95][115]  = TOWN;    // Neutral Ground

        // ── Step 11: Dungeon entrances ───────────────────────────────────
        g[70][90]   = DUNGEON; // War Beneath
        g[120][140] = DUNGEON; // Iron Pit

        // ── Step 12: Portals ─────────────────────────────────────────────
        // Return portal to Umbryn
        g[182][115] = PORTAL_RET;
        // Portal to Cradle of Shards (inside arena center)
        g[95][116]  = PORTAL_FWD;

        // ── Step 13: Spawn difficulty grid (24–27) ───────────────────────
        // Monster levels 24-27 → difficulty 47-54
        // Formula: monsterLevel = 1 + (difficulty * 49) / 99
        int[][] towns = {{60,40}, {170,140}, {115,95}};
        int minDiff = 47, maxDiff = 54; // levels 24-27
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

        // ── Write JSON ──────────────────────────────────────────────────
        writeJSON("data/overworlds/bellorak.rfmap");
        System.out.println("Done — data/overworlds/bellorak.rfmap written.");
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    static void fill(int r1, int r2, int c1, int c2, char tile) {
        for (int r = r1; r <= r2; r++)
            for (int c = c1; c <= c2; c++)
                g[r][c] = tile;
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

    static void placeCampZone(int zcx, int zcy, int radius) {
        for (int y = zcy - radius; y <= zcy + radius; y++) {
            for (int x = zcx - radius; x <= zcx + radius; x++) {
                if (x < 0 || x >= COLS || y < 0 || y >= ROWS) continue;
                if (g[y][x] != SCORCHED) continue;
                double d = Math.sqrt((x - zcx) * (x - zcx) + (y - zcy) * (y - zcy));
                if (d < radius && rng.nextDouble() < 0.40) {
                    g[y][x] = WARCAMP;
                }
            }
        }
    }

    static void placeBarricadeRing(int rcx, int rcy, int innerR, int outerR) {
        for (int y = rcy - outerR; y <= rcy + outerR; y++) {
            for (int x = rcx - outerR; x <= rcx + outerR; x++) {
                if (x < 0 || x >= COLS || y < 0 || y >= ROWS) continue;
                double d = Math.sqrt((x - rcx) * (x - rcx) + (y - rcy) * (y - rcy));
                double noise = 1.5 * Math.sin(x * 0.5) * Math.cos(y * 0.4);
                if (d > innerR + noise && d < outerR + noise) {
                    char c = g[y][x];
                    if (c == SCORCHED || c == WARCAMP) {
                        if (rng.nextDouble() < 0.30) {
                            g[y][x] = (rng.nextDouble() < 0.5) ? BARRICADE : SIEGE;
                        }
                    }
                }
            }
        }
        // Clear walkable gap to the south
        for (int y = rcy + innerR - 2; y <= rcy + outerR; y++) {
            for (int x = rcx - 2; x <= rcx + 2; x++) {
                if (x >= 0 && x < COLS && y >= 0 && y < ROWS) {
                    char c = g[y][x];
                    if (c == BARRICADE || c == SIEGE) {
                        g[y][x] = SCORCHED;
                    }
                }
            }
        }
    }

    static void placeBarricadeCluster(int bcx, int bcy, int radius) {
        for (int y = bcy - radius; y <= bcy + radius; y++) {
            for (int x = bcx - radius; x <= bcx + radius; x++) {
                if (x < 0 || x >= COLS || y < 0 || y >= ROWS) continue;
                if (g[y][x] != SCORCHED && g[y][x] != WARCAMP) continue;
                double d = Math.sqrt((x - bcx) * (x - bcx) + (y - bcy) * (y - bcy));
                if (d < radius && rng.nextDouble() < 0.18) {
                    g[y][x] = BARRICADE;
                }
            }
        }
    }

    static void placeTrenchLine(int x1, int y1, int x2, int y2) {
        int steps = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1));
        if (steps == 0) return;
        for (int i = 0; i <= steps; i++) {
            int px = x1 + (x2 - x1) * i / steps;
            int py = y1 + (y2 - y1) * i / steps;
            int wobble = (int)(2 * Math.sin(i * 0.15));
            for (int dy = -1; dy <= 1; dy++) {
                int nx = px + wobble, ny = py + dy;
                if (nx >= 0 && nx < COLS && ny >= 0 && ny < ROWS) {
                    char c = g[ny][nx];
                    if (c == SCORCHED || c == CRATER) {
                        g[ny][nx] = TRENCH;
                    }
                }
            }
        }
    }

    static void placeCraterField(int fcx, int fcy, int radius) {
        for (int y = fcy - radius; y <= fcy + radius; y++) {
            for (int x = fcx - radius; x <= fcx + radius; x++) {
                if (x < 0 || x >= COLS || y < 0 || y >= ROWS) continue;
                if (g[y][x] != SCORCHED) continue;
                double d = Math.sqrt((x - fcx) * (x - fcx) + (y - fcy) * (y - fcy));
                if (d < radius && rng.nextDouble() < 0.25) {
                    g[y][x] = CRATER;
                }
            }
        }
    }

    static void placeBurningRuins(int rcx, int rcy, int radius) {
        for (int y = rcy - radius; y <= rcy + radius; y++) {
            for (int x = rcx - radius; x <= rcx + radius; x++) {
                if (x < 0 || x >= COLS || y < 0 || y >= ROWS) continue;
                char c = g[y][x];
                if (c != SCORCHED && c != CRATER) continue;
                double d = Math.sqrt((x - rcx) * (x - rcx) + (y - rcy) * (y - rcy));
                if (d < radius && rng.nextDouble() < 0.15) {
                    g[y][x] = BURNING;
                }
            }
        }
    }

    static void placeTreeCluster(int tcx, int tcy, int radius) {
        for (int y = tcy - radius; y <= tcy + radius; y++) {
            for (int x = tcx - radius; x <= tcx + radius; x++) {
                if (x < 0 || x >= COLS || y < 0 || y >= ROWS) continue;
                if (g[y][x] != SCORCHED) continue;
                double d = Math.sqrt((x - tcx) * (x - tcx) + (y - tcy) * (y - tcy));
                if (d < radius && rng.nextDouble() < 0.35) {
                    g[y][x] = TREE;
                }
            }
        }
    }

    static void warPath(int x1, int y1, int x2, int y2) {
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
                        if (c == BURNING || c == BARRICADE || c == SIEGE || c == MTN) {
                            g[ny][nx] = SCORCHED;
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
                    g[y][x] = SCORCHED;
                }
            }
        }
    }

    // ── JSON output ─────────────────────────────────────────────────────

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
        sb.append("  \"name\": \"bellorak\",\n");
        sb.append("  \"width\": ").append(COLS).append(",\n");
        sb.append("  \"height\": ").append(ROWS).append(",\n");

        // ── Tiles ────────────────────────────────────────────────────────
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

        // ── Spawn difficulty ────────────────────────────────────────────
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

        // ── NPCs ────────────────────────────────────────────────────────
        sb.append("  \"npcs\": [\n");

        // NPC 1: Brothers Three — questgiver between camps
        sb.append("    {\n");
        sb.append("      \"id\": \"npc_bellorak_brothers\",\n");
        sb.append("      \"name\": \"Brothers Three\",\n");
        sb.append("      \"spriteName\": \"npcs/townsman\",\n");
        sb.append("      \"type\": \"QUESTGIVER\",\n");
        sb.append("      \"defaultDialog\": \"We were three brothers \\u2014 one joined the Gold Guard, one the Iron Reckoners, and I remained. Now I search for them both.\",\n");
        sb.append("      \"questCompleteDialog\": \"\",\n");
        sb.append("      \"shopItemIds\": [],\n");
        sb.append("      \"dialogueTree\": {\n");
        sb.append("        \"startNodeId\": \"start\",\n");
        sb.append("        \"nodes\": {\n");

        sb.append("          \"start\": {\n");
        sb.append("            \"id\": \"start\",\n");
        sb.append("            \"text\": \"[Three bedrolls surround a dead fire. Only one figure sits here.] We were three brothers \\u2014 one joined the Gold Guard, one the Iron Reckoners, and I remained. The war took them both. I still search.\",\n");
        sb.append("            \"choices\": [\n");
        sb.append("              { \"label\": \"What happened to them?\", \"nextNodeId\": \"brothers\" },\n");
        sb.append("              { \"label\": \"Why do you stay?\", \"nextNodeId\": \"stay\" },\n");
        sb.append("              { \"label\": \"[Leave]\", \"nextNodeId\": \"end\" }\n");
        sb.append("            ],\n");
        sb.append("            \"actions\": []\n");
        sb.append("          },\n");

        sb.append("          \"brothers\": {\n");
        sb.append("            \"id\": \"brothers\",\n");
        sb.append("            \"text\": \"Aldric marched north with the Gold Guard. Corran dug trenches for the Iron Reckoners in the south. Neither has sent word in months. If you find dog tags bearing their names in the dungeons beneath, bring them to me.\",\n");
        sb.append("            \"choices\": [\n");
        sb.append("              { \"label\": \"I\\u0027ll look for them.\", \"nextNodeId\": \"end\" }\n");
        sb.append("            ],\n");
        sb.append("            \"actions\": []\n");
        sb.append("          },\n");

        sb.append("          \"stay\": {\n");
        sb.append("            \"id\": \"stay\",\n");
        sb.append("            \"text\": \"Because someone must remember that this war is not glory. It is brothers killing brothers. The Arena at the center \\u2014 they call it sport. I call it Bellorak\\u0027s cruelest joke.\",\n");
        sb.append("            \"choices\": [\n");
        sb.append("              { \"label\": \"The Arena?\", \"nextNodeId\": \"arena\" },\n");
        sb.append("              { \"label\": \"[Leave]\", \"nextNodeId\": \"end\" }\n");
        sb.append("            ],\n");
        sb.append("            \"actions\": []\n");
        sb.append("          },\n");

        sb.append("          \"arena\": {\n");
        sb.append("            \"id\": \"arena\",\n");
        sb.append("            \"text\": \"The Great Arena. Where both factions send their champions to settle disputes. At its heart lies a portal to the Cradle of Shards \\u2014 where Bellorak\\u0027s trial awaits. Only the worthy pass through.\",\n");
        sb.append("            \"choices\": [\n");
        sb.append("              { \"label\": \"I\\u0027ll find your brothers.\", \"nextNodeId\": \"end\" }\n");
        sb.append("            ],\n");
        sb.append("            \"actions\": []\n");
        sb.append("          },\n");

        sb.append("          \"end\": {\n");
        sb.append("            \"id\": \"end\",\n");
        sb.append("            \"text\": \"[He stares at the cold fire, lost in thought.]\",\n");
        sb.append("            \"choices\": [],\n");
        sb.append("            \"actions\": [{ \"type\": \"CLOSE_DIALOGUE\", \"target\": \"\", \"value\": \"\", \"amount\": 0 }]\n");
        sb.append("          }\n");

        sb.append("        }\n");
        sb.append("      },\n");
        sb.append("      \"x\": 115, \"y\": 130\n");
        sb.append("    },\n");

        // NPC 2: Iron Ghost — lore NPC near Iron Pit
        sb.append("    {\n");
        sb.append("      \"id\": \"npc_bellorak_iron_ghost\",\n");
        sb.append("      \"name\": \"Iron Ghost\",\n");
        sb.append("      \"spriteName\": \"npcs/townsman\",\n");
        sb.append("      \"type\": \"NPC\",\n");
        sb.append("      \"defaultDialog\": \"The Iron Pit swallows soldiers whole. What comes back up is not the same.\",\n");
        sb.append("      \"questCompleteDialog\": \"\",\n");
        sb.append("      \"shopItemIds\": [],\n");
        sb.append("      \"dialogueTree\": {\n");
        sb.append("        \"startNodeId\": \"start\",\n");
        sb.append("        \"nodes\": {\n");

        sb.append("          \"start\": {\n");
        sb.append("            \"id\": \"start\",\n");
        sb.append("            \"text\": \"[A scarred veteran stands near the pit entrance, leaning on a broken pike.] I went down there once. Came back missing three days and half my squad. The Iron Pit swallows soldiers whole.\",\n");
        sb.append("            \"choices\": [\n");
        sb.append("              { \"label\": \"What\\u0027s down there?\", \"nextNodeId\": \"below\" },\n");
        sb.append("              { \"label\": \"Who are you?\", \"nextNodeId\": \"who\" },\n");
        sb.append("              { \"label\": \"[Leave]\", \"nextNodeId\": \"end\" }\n");
        sb.append("            ],\n");
        sb.append("            \"actions\": []\n");
        sb.append("          },\n");

        sb.append("          \"below\": {\n");
        sb.append("            \"id\": \"below\",\n");
        sb.append("            \"text\": \"Old weapons. Old bones. The war has been going on longer than anyone admits. Bellorak didn\\u0027t start it \\u2014 he just made sure it never ends. Down there you\\u0027ll see: the dead of a hundred campaigns, still fighting.\",\n");
        sb.append("            \"choices\": [\n");
        sb.append("              { \"label\": \"I\\u0027ll be careful.\", \"nextNodeId\": \"end\" }\n");
        sb.append("            ],\n");
        sb.append("            \"actions\": []\n");
        sb.append("          },\n");

        sb.append("          \"who\": {\n");
        sb.append("            \"id\": \"who\",\n");
        sb.append("            \"text\": \"They call me Iron Ghost. I was an Iron Reckoner once. Now I\\u0027m neither side. I just watch the pit and warn fools like you. Not that it ever helps.\",\n");
        sb.append("            \"choices\": [\n");
        sb.append("              { \"label\": \"Thanks for the warning.\", \"nextNodeId\": \"end\" }\n");
        sb.append("            ],\n");
        sb.append("            \"actions\": []\n");
        sb.append("          },\n");

        sb.append("          \"end\": {\n");
        sb.append("            \"id\": \"end\",\n");
        sb.append("            \"text\": \"[Iron Ghost turns back to the pit, watching the darkness below.]\",\n");
        sb.append("            \"choices\": [],\n");
        sb.append("            \"actions\": [{ \"type\": \"CLOSE_DIALOGUE\", \"target\": \"\", \"value\": \"\", \"amount\": 0 }]\n");
        sb.append("          }\n");

        sb.append("        }\n");
        sb.append("      },\n");
        sb.append("      \"x\": 145, \"y\": 118\n");
        sb.append("    }\n");

        sb.append("  ],\n");

        // ── Town entrances ──────────────────────────────────────────────
        sb.append("  \"townEntrances\": [\n");
        sb.append("    { \"townName\": \"gold_guard_camp\", \"worldX\": 60, \"worldY\": 40 },\n");
        sb.append("    { \"townName\": \"iron_reckoner_camp\", \"worldX\": 170, \"worldY\": 140 },\n");
        sb.append("    { \"townName\": \"neutral_ground\", \"worldX\": 115, \"worldY\": 95 }\n");
        sb.append("  ],\n");

        // ── Initial tile states (empty) ────────────────────────────────
        sb.append("  \"initialTileStates\": {}\n");
        sb.append("}\n");

        bw.write(sb.toString());
        bw.close();
    }
}

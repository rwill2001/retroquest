import java.io.*;
import java.util.Random;

/**
 * Generates data/overworlds/umbryn.rfmap — a 230x190 overworld map for
 * Island 6 (Umbryn / The Shadow Trenches).
 *
 * Shadow-trenched landscape of ruined spires, ghost paths, memory rifts,
 * void trenches, and silver ruins. Three towns: Dusthaven, The Hollow, Echo Point.
 *
 * Run from project root:
 *   javac -encoding UTF-8 tools/UmbrynGen.java -d tools/
 *   java -cp tools UmbrynGen
 */
public class UmbrynGen {

    static final int COLS = 230, ROWS = 190;
    static char[][] g = new char[ROWS][COLS];
    static int[][] diff = new int[ROWS][COLS];
    static Random rng = new Random(66);

    // Tile constants — Unicode PUA for Umbryn tiles
    static final char SFLOOR  = '\uE080';  // shadow trench floor (base walkable)
    static final char MRIFT   = '\uE081';  // memory rift (damage)
    static final char RSPIRE  = '\uE082';  // ruined spire (impassable)
    static final char GPATH   = '\uE083';  // ghost path (low encounter)
    static final char CARCH   = '\uE084';  // crumbled archive (high encounter)
    static final char SRUINS  = '\uE085';  // silver ruins
    static final char VTRENCH = '\uE086';  // void trench (impassable)
    static final char MWARD   = '\uE087';  // memory ward (impassable)
    static final char PORTAL_RET = '\uE089'; // return portal to Thalorax

    // Shared tiles
    static final char TOWN    = 'E';
    static final char DUNGEON = 'D';

    public static void main(String[] args) throws Exception {

        // ── Step 1: Fill with shadow trench floor ──────────────────────────
        fill(0, ROWS - 1, 0, COLS - 1, SFLOOR);

        // ── Step 2: Main landmass — irregularly shaped shadow continent ────
        double cx = 115.0, cy = 95.0;
        double rx = 100.0, ry = 80.0;

        for (int y = 0; y < ROWS; y++) {
            for (int x = 0; x < COLS; x++) {
                double dx = (x - cx) / rx;
                double dy = (y - cy) / ry;
                double dist = Math.sqrt(dx * dx + dy * dy);
                double noise = 0.10 * Math.sin(x * 0.18) * Math.cos(y * 0.22)
                             + 0.07 * Math.sin(x * 0.50 + y * 0.30)
                             + 0.05 * Math.cos(x * 0.14 - y * 0.32);
                if (dist + noise > 0.88) {
                    g[y][x] = VTRENCH;  // void trenches form the border
                }
            }
        }

        // Sub-region near Dusthaven (center) — extend walkable area
        placeEllipse(115, 95, 35, 30, SFLOOR, 0.92);
        // Sub-region near The Hollow (west) — extend walkable area
        placeEllipse(55, 110, 25, 20, SFLOOR, 0.88);
        // Sub-region near Echo Point (northeast) — extend walkable area
        placeEllipse(175, 50, 28, 22, SFLOOR, 0.90);

        // ── Step 3: Void trench canyons (impassable chasms) ───────────────
        // Major N-S canyon (west side)
        for (int y = 20; y < 170; y++) {
            int cx2 = 30 + (int)(5 * Math.sin(y * 0.08));
            for (int dx2 = -3; dx2 <= 3; dx2++) {
                int nx = cx2 + dx2;
                if (nx >= 0 && nx < COLS && g[y][nx] == SFLOOR) {
                    g[y][nx] = VTRENCH;
                }
            }
        }
        // Major E-W canyon (south)
        for (int x = 50; x < 180; x++) {
            int cy2 = 150 + (int)(4 * Math.sin(x * 0.06));
            for (int dy2 = -2; dy2 <= 2; dy2++) {
                int ny = cy2 + dy2;
                if (ny >= 0 && ny < ROWS && g[ny][x] == SFLOOR) {
                    g[ny][x] = VTRENCH;
                }
            }
        }

        // ── Step 4: Ruined spire formations (impassable, scattered) ───────
        placeSpireCluster(80, 60, 18);   // NW cluster
        placeSpireCluster(160, 80, 15);  // E cluster
        placeSpireCluster(100, 140, 16); // SW cluster
        placeSpireCluster(180, 120, 12); // Far east
        placeSpireCluster(60, 40, 10);   // Far NW

        // Scattered individual spires
        int spiresPlaced = 0, attempts = 0;
        while (spiresPlaced < 60 && attempts < 6000) {
            int sx = 10 + rng.nextInt(210);
            int sy = 10 + rng.nextInt(170);
            attempts++;
            if (sx < COLS && sy < ROWS && g[sy][sx] == SFLOOR) {
                boolean tooClose = false;
                for (int dy = -3; dy <= 3 && !tooClose; dy++) {
                    for (int dx = -3; dx <= 3 && !tooClose; dx++) {
                        int ny = sy + dy, nx = sx + dx;
                        if (ny >= 0 && ny < ROWS && nx >= 0 && nx < COLS && g[ny][nx] == RSPIRE) {
                            tooClose = true;
                        }
                    }
                }
                if (!tooClose) {
                    g[sy][sx] = RSPIRE;
                    spiresPlaced++;
                }
            }
        }

        // ── Step 5: Silver ruins zones (medium encounter, blocks vision) ──
        placeRuinsZone(90, 75, 15);   // NW ruins
        placeRuinsZone(140, 70, 12);  // NE ruins
        placeRuinsZone(80, 125, 14);  // SW ruins
        placeRuinsZone(160, 115, 10); // E ruins
        placeRuinsZone(115, 130, 13); // South-central ruins

        // ── Step 6: Crumbled archives (high encounter, exploration zones) ──
        placeArchiveZone(95, 65, 10);   // NW archive ruins
        placeArchiveZone(150, 85, 8);   // E archive ruins
        placeArchiveZone(70, 135, 9);   // SW archive ruins
        placeArchiveZone(175, 60, 7);   // Near Echo Point

        // ── Step 7: Memory rift clusters (damage hazard zones) ────────────
        placeRiftCluster(85, 80, 12);    // NW rifts
        placeRiftCluster(145, 95, 10);   // Central-east rifts
        placeRiftCluster(110, 135, 12);  // South-central rifts
        placeRiftCluster(65, 55, 8);     // Far NW rifts
        placeRiftCluster(185, 70, 9);    // Far NE rifts

        // ── Step 8: Memory ward ring around Archive of Tears entrance ─────
        int tx = 180, ty = 55;
        for (int y = ty - 10; y <= ty + 10; y++) {
            for (int x = tx - 10; x <= tx + 10; x++) {
                if (x < 0 || x >= COLS || y < 0 || y >= ROWS) continue;
                double d = Math.sqrt((x - tx) * (x - tx) + (y - ty) * (y - ty));
                double noise = 1.5 * Math.sin(x * 0.5) * Math.cos(y * 0.4);
                if (d > 7 + noise && d < 10 + noise) {
                    g[y][x] = MWARD;
                }
            }
        }
        // Clear walkable entrance gap to the south
        for (int y = ty + 6; y <= ty + 10; y++) {
            for (int x = tx - 2; x <= tx + 2; x++) {
                if (x >= 0 && x < COLS && y >= 0 && y < ROWS && g[y][x] == MWARD) {
                    g[y][x] = SFLOOR;
                }
            }
        }
        // Clear inside the ring
        for (int y = ty - 6; y <= ty + 6; y++) {
            for (int x = tx - 6; x <= tx + 6; x++) {
                if (x < 0 || x >= COLS || y < 0 || y >= ROWS) continue;
                double d = Math.sqrt((x - tx) * (x - tx) + (y - ty) * (y - ty));
                if (d < 6) {
                    g[y][x] = SFLOOR;
                }
            }
        }

        // ── Step 9: Ghost paths connecting towns ──────────────────────────
        // Arrival (115, 180) → Dusthaven (115, 95)
        ghostPath(115, 178, 115, 95);
        // Dusthaven (115, 95) → The Hollow (55, 110)
        ghostPath(115, 95, 85, 100);
        ghostPath(85, 100, 55, 110);
        // Dusthaven (115, 95) → Echo Point (175, 50)
        ghostPath(115, 95, 145, 72);
        ghostPath(145, 72, 175, 50);
        // The Hollow (55, 110) → south exploration
        ghostPath(55, 110, 70, 135);
        // Echo Point (175, 50) → Dungeon entrance (180, 55)
        ghostPath(175, 50, 180, 55);

        // ── Step 10: Clearings around towns ───────────────────────────────
        clearAroundTown(115, 95, 8);   // Dusthaven
        clearAroundTown(55, 110, 7);   // The Hollow
        clearAroundTown(175, 50, 6);   // Echo Point

        // Clear around arrival portal
        clearAroundTown(115, 180, 5);

        // ── Step 11: Town entrances ───────────────────────────────────────
        g[95][115]  = TOWN;   // Dusthaven
        g[110][55]  = TOWN;   // The Hollow
        g[50][175]  = TOWN;   // Echo Point

        // ── Step 12: Dungeon entrance — Archive of Tears ──────────────────
        g[55][180] = DUNGEON;

        // ── Step 13: Portals ──────────────────────────────────────────────
        // Return portal to Thalorax (free, near arrival)
        g[182][115] = PORTAL_RET;

        // ── Step 14: Spawn difficulty grid (20–23) ────────────────────────
        // Monster levels 20-23 → difficulty 39-46
        // Formula: monsterLevel = 1 + (difficulty * 49) / 99
        int[][] towns = {{115,95}, {55,110}, {175,50}};
        int minDiff = 39, maxDiff = 46; // levels 20-23
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

        // ── Write JSON ────────────────────────────────────────────────────
        writeJSON("data/overworlds/umbryn.rfmap");
        System.out.println("Done — data/overworlds/umbryn.rfmap written.");
    }

    // ── Helpers ───────────────────────────────────────────────────────────

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

    static void placeSpireCluster(int scx, int scy, int radius) {
        for (int y = scy - radius; y <= scy + radius; y++) {
            for (int x = scx - radius; x <= scx + radius; x++) {
                if (x < 0 || x >= COLS || y < 0 || y >= ROWS) continue;
                if (g[y][x] != SFLOOR) continue;
                double d = Math.sqrt((x - scx) * (x - scx) + (y - scy) * (y - scy));
                if (d < radius && rng.nextDouble() < 0.25) {
                    g[y][x] = RSPIRE;
                }
            }
        }
    }

    static void placeRuinsZone(int zcx, int zcy, int radius) {
        for (int y = zcy - radius; y <= zcy + radius; y++) {
            for (int x = zcx - radius; x <= zcx + radius; x++) {
                if (x < 0 || x >= COLS || y < 0 || y >= ROWS) continue;
                if (g[y][x] != SFLOOR) continue;
                double d = Math.sqrt((x - zcx) * (x - zcx) + (y - zcy) * (y - zcy));
                if (d < radius && rng.nextDouble() < 0.35) {
                    g[y][x] = SRUINS;
                }
            }
        }
    }

    static void placeArchiveZone(int zcx, int zcy, int radius) {
        for (int y = zcy - radius; y <= zcy + radius; y++) {
            for (int x = zcx - radius; x <= zcx + radius; x++) {
                if (x < 0 || x >= COLS || y < 0 || y >= ROWS) continue;
                if (g[y][x] != SFLOOR && g[y][x] != SRUINS) continue;
                double d = Math.sqrt((x - zcx) * (x - zcx) + (y - zcy) * (y - zcy));
                if (d < radius && rng.nextDouble() < 0.30) {
                    g[y][x] = CARCH;
                }
            }
        }
    }

    static void placeRiftCluster(int rcx, int rcy, int radius) {
        for (int y = rcy - radius; y <= rcy + radius; y++) {
            for (int x = rcx - radius; x <= rcx + radius; x++) {
                if (x < 0 || x >= COLS || y < 0 || y >= ROWS) continue;
                char c = g[y][x];
                if (c != SFLOOR && c != CARCH) continue;
                double d = Math.sqrt((x - rcx) * (x - rcx) + (y - rcy) * (y - rcy));
                if (d < radius && rng.nextDouble() < 0.20) {
                    g[y][x] = MRIFT;
                }
            }
        }
    }

    static void ghostPath(int x1, int y1, int x2, int y2) {
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
                        if (c == SFLOOR || c == MRIFT || c == CARCH || c == SRUINS) {
                            g[ny][nx] = GPATH;
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
                    g[y][x] = GPATH;
                }
            }
        }
    }

    // ── JSON output ───────────────────────────────────────────────────────

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
        sb.append("  \"name\": \"umbryn\",\n");
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

        // ── Spawn difficulty ──────────────────────────────────────────────
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

        // NPC 1: Shade of Eryn — lore NPC near Dusthaven
        sb.append("    {\n");
        sb.append("      \"id\": \"npc_umbryn_eryn\",\n");
        sb.append("      \"name\": \"Shade of Eryn\",\n");
        sb.append("      \"spriteName\": \"npcs/townsman\",\n");
        sb.append("      \"type\": \"LORE\",\n");
        sb.append("      \"defaultDialog\": \"I died in the Shattering. Umbryn caught my last thought and kept it. I exist only as a memory now.\",\n");
        sb.append("      \"questCompleteDialog\": \"\",\n");
        sb.append("      \"shopItemIds\": [],\n");
        sb.append("      \"dialogueTree\": {\n");
        sb.append("        \"startNodeId\": \"start\",\n");
        sb.append("        \"nodes\": {\n");

        sb.append("          \"start\": {\n");
        sb.append("            \"id\": \"start\",\n");
        sb.append("            \"text\": \"[A translucent figure stands motionless, gazing at nothing.] I died in the Shattering. Umbryn caught my last thought and kept it. I exist only as a memory now. Is that enough?\",\n");
        sb.append("            \"choices\": [\n");
        sb.append("              { \"label\": \"What do you remember?\", \"nextNodeId\": \"remember\" },\n");
        sb.append("              { \"label\": \"I\\u0027m sorry.\", \"nextNodeId\": \"sorry\" },\n");
        sb.append("              { \"label\": \"[Leave]\", \"nextNodeId\": \"end\" }\n");
        sb.append("            ],\n");
        sb.append("            \"actions\": []\n");
        sb.append("          },\n");

        sb.append("          \"remember\": {\n");
        sb.append("            \"id\": \"remember\",\n");
        sb.append("            \"text\": \"The sound of rain on stone. A child laughing. The warmth of bread from the oven. These are all I am now \\u2014 fragments of a life that once meant everything.\",\n");
        sb.append("            \"choices\": [\n");
        sb.append("              { \"label\": \"Those are worth keeping.\", \"nextNodeId\": \"end\" }\n");
        sb.append("            ],\n");
        sb.append("            \"actions\": []\n");
        sb.append("          },\n");

        sb.append("          \"sorry\": {\n");
        sb.append("            \"id\": \"sorry\",\n");
        sb.append("            \"text\": \"Don\\u0027t be. I had a life. Most memories here can\\u0027t say even that much. The Nameless in The Hollow to the west \\u2014 they\\u0027ve lost everything. Even their names.\",\n");
        sb.append("            \"choices\": [\n");
        sb.append("              { \"label\": \"The Nameless?\", \"nextNodeId\": \"nameless\" },\n");
        sb.append("              { \"label\": \"[Leave]\", \"nextNodeId\": \"end\" }\n");
        sb.append("            ],\n");
        sb.append("            \"actions\": []\n");
        sb.append("          },\n");

        sb.append("          \"nameless\": {\n");
        sb.append("            \"id\": \"nameless\",\n");
        sb.append("            \"text\": \"Mourner Vel tends them. She is the only one who still tries to coax their identities back. Speak with her if you seek understanding of what Umbryn\\u0027s domain truly costs.\",\n");
        sb.append("            \"choices\": [\n");
        sb.append("              { \"label\": \"I will.\", \"nextNodeId\": \"end\" }\n");
        sb.append("            ],\n");
        sb.append("            \"actions\": []\n");
        sb.append("          },\n");

        sb.append("          \"end\": {\n");
        sb.append("            \"id\": \"end\",\n");
        sb.append("            \"text\": \"[The shade turns back to the darkness, already forgetting you were here.]\",\n");
        sb.append("            \"choices\": [],\n");
        sb.append("            \"actions\": [{ \"type\": \"CLOSE_DIALOGUE\", \"target\": \"\", \"value\": \"\", \"amount\": 0 }]\n");
        sb.append("          }\n");

        sb.append("        }\n");
        sb.append("      },\n");
        sb.append("      \"x\": 100, \"y\": 130\n");
        sb.append("    },\n");

        // NPC 2: The Chronicler — quest hint NPC near dungeon
        sb.append("    {\n");
        sb.append("      \"id\": \"npc_umbryn_chronicler\",\n");
        sb.append("      \"name\": \"The Chronicler\",\n");
        sb.append("      \"spriteName\": \"npcs/townsman\",\n");
        sb.append("      \"type\": \"LORE\",\n");
        sb.append("      \"defaultDialog\": \"The Archive holds memories too precious to lose. Seek Archivist Sable in Dusthaven.\",\n");
        sb.append("      \"questCompleteDialog\": \"\",\n");
        sb.append("      \"shopItemIds\": [],\n");
        sb.append("      \"dialogueTree\": {\n");
        sb.append("        \"startNodeId\": \"start\",\n");
        sb.append("        \"nodes\": {\n");

        sb.append("          \"start\": {\n");
        sb.append("            \"id\": \"start\",\n");
        sb.append("            \"text\": \"[A hooded figure studies the ward-circle.] You seek entry to the Archive of Tears? Echo Point lies just to the south. But first, speak with Archivist Sable in Dusthaven \\u2014 four memory crystals are fading, and the knowledge they hold must not be lost.\",\n");
        sb.append("            \"choices\": [\n");
        sb.append("              { \"label\": \"What are memory crystals?\", \"nextNodeId\": \"crystals\" },\n");
        sb.append("              { \"label\": \"Where is Dusthaven?\", \"nextNodeId\": \"directions\" },\n");
        sb.append("              { \"label\": \"[Leave]\", \"nextNodeId\": \"end\" }\n");
        sb.append("            ],\n");
        sb.append("            \"actions\": []\n");
        sb.append("          },\n");

        sb.append("          \"crystals\": {\n");
        sb.append("            \"id\": \"crystals\",\n");
        sb.append("            \"text\": \"Umbryn stores the world\\u0027s most important memories in crystallized form. But the crystals degrade over time. When a crystal fails, the memory it holds is lost forever \\u2014 as if it never happened.\",\n");
        sb.append("            \"choices\": [\n");
        sb.append("              { \"label\": \"I\\u0027ll find Sable.\", \"nextNodeId\": \"end\" }\n");
        sb.append("            ],\n");
        sb.append("            \"actions\": []\n");
        sb.append("          },\n");

        sb.append("          \"directions\": {\n");
        sb.append("            \"id\": \"directions\",\n");
        sb.append("            \"text\": \"Follow the ghost path south and then west. Dusthaven is the largest settlement on this island \\u2014 built among the ruins of a much greater city. You cannot miss it.\",\n");
        sb.append("            \"choices\": [\n");
        sb.append("              { \"label\": \"Thank you.\", \"nextNodeId\": \"end\" }\n");
        sb.append("            ],\n");
        sb.append("            \"actions\": []\n");
        sb.append("          },\n");

        sb.append("          \"end\": {\n");
        sb.append("            \"id\": \"end\",\n");
        sb.append("            \"text\": \"[The Chronicler returns to studying the wards, muttering about failing resonances.]\",\n");
        sb.append("            \"choices\": [],\n");
        sb.append("            \"actions\": [{ \"type\": \"CLOSE_DIALOGUE\", \"target\": \"\", \"value\": \"\", \"amount\": 0 }]\n");
        sb.append("          }\n");

        sb.append("        }\n");
        sb.append("      },\n");
        sb.append("      \"x\": 170, \"y\": 65\n");
        sb.append("    }\n");

        sb.append("  ],\n");

        // ── Town entrances ────────────────────────────────────────────────
        sb.append("  \"townEntrances\": [\n");
        sb.append("    { \"townName\": \"dusthaven\", \"worldX\": 115, \"worldY\": 95 },\n");
        sb.append("    { \"townName\": \"the_hollow\", \"worldX\": 55, \"worldY\": 110 },\n");
        sb.append("    { \"townName\": \"echo_point\", \"worldX\": 175, \"worldY\": 50 }\n");
        sb.append("  ],\n");

        // ── Initial tile states (empty) ──────────────────────────────────
        sb.append("  \"initialTileStates\": {}\n");
        sb.append("}\n");

        bw.write(sb.toString());
        bw.close();
    }
}

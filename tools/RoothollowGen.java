import java.io.*;

/**
 * Generates data/towns/roothollow.rfmap — a 30x30 town built inside a
 * hollowed-out titan tree. Domain of Sylvandar.
 *
 * Run from project root:
 *   javac tools/RoothollowGen.java -d tools/
 *   java -cp tools RoothollowGen
 */
public class RoothollowGen {

    static final int COLS = 30, ROWS = 30;
    static char[][] g = new char[ROWS][COLS];

    // Tile IDs
    static final char RFLOOR  = '\uE06A';  // root floor
    static final char BWALL   = '\uE06B';  // bark wall
    static final char VINE    = '\uE06C';  // vine curtain
    static final char ACRYST  = '\uE06D';  // amber crystal (light)
    static final char FLAMP   = '\uE06E';  // fungal lamp
    static final char MOSS    = '\uE063';  // moss ground (exterior)
    static final char DFOREST = '\uE060';  // dense forest (exterior)
    static final char GTREE   = '\uE061';  // giant tree
    static final char WALL    = 'W';       // stone wall
    static final char DOOR    = 'd';       // door
    static final char COUNTER = 'c';       // counter
    static final char BARREL  = 'n';       // barrel
    static final char SHELF   = 'k';       // bookshelf
    static final char BED     = 'l';       // bed
    static final char TABLE   = 'i';       // table
    static final char CHAIR   = 'j';       // chair
    static final char FIRE    = 'u';       // fireplace
    static final char TORCH   = 'v';       // torch
    static final char ALTAR   = 'x';       // altar

    public static void main(String[] args) throws Exception {

        // ── 1. Exterior: dense forest everywhere ──────────────────────────
        fill(0, 29, 0, 29, DFOREST);

        // ── 2. Giant tree trunk ring (exterior bark wall) ─────────────────
        // The town is INSIDE a titan tree — bark walls form the perimeter
        // Roughly oval shape centered at (15,15), radius ~12
        for (int y = 0; y < ROWS; y++) {
            for (int x = 0; x < COLS; x++) {
                double dx = (x - 15.0) / 13.0;
                double dy = (y - 15.0) / 13.0;
                double dist = Math.sqrt(dx * dx + dy * dy);
                if (dist < 0.85) {
                    g[y][x] = RFLOOR;  // interior
                }
                if (dist >= 0.82 && dist < 0.92) {
                    g[y][x] = BWALL;   // bark wall ring
                }
            }
        }

        // ── 3. Entry: south opening ───────────────────────────────────────
        g[27][14] = DOOR; g[27][15] = DOOR;
        g[26][14] = RFLOOR; g[26][15] = RFLOOR;

        // ── 4. Central chamber — Sylvandar altar ──────────────────────────
        // Altar at center with amber crystals flanking
        g[13][14] = ALTAR; g[13][15] = ALTAR;
        g[13][12] = ACRYST; g[13][17] = ACRYST;
        g[11][14] = FLAMP; g[11][15] = FLAMP;

        // ── 5. Fungal lamps for lighting ──────────────────────────────────
        g[7][10] = FLAMP;  g[7][19] = FLAMP;
        g[15][8] = FLAMP;  g[15][21] = FLAMP;
        g[22][10] = FLAMP; g[22][19] = FLAMP;

        // ── 6. Inn area (northwest) ───────────────────────────────────────
        // Bark wall partition
        hwall(8, 5, 12); vwall(12, 5, 8);
        g[8][9] = DOOR;  // doorway
        // Interior
        g[5][5] = BED; g[5][6] = BED;
        g[6][5] = BED; g[6][6] = BED;
        g[5][9] = FIRE;
        g[7][5] = TABLE; g[7][6] = CHAIR; g[7][7] = CHAIR;

        // ── 7. Shop area (northeast) ──────────────────────────────────────
        hwall(8, 17, 24); vwall(17, 5, 8);
        g[8][20] = DOOR;
        // Shop interior
        g[5][18] = COUNTER; g[5][19] = COUNTER; g[5][20] = COUNTER;
        g[6][18] = BARREL; g[6][19] = BARREL;
        g[5][22] = SHELF; g[5][23] = SHELF;
        g[7][22] = SHELF; g[7][23] = SHELF;

        // ── 8. Herbalist area (southwest) ─────────────────────────────────
        hwall(19, 5, 12); vwall(12, 19, 23);
        g[19][9] = DOOR;
        g[20][5] = TABLE; g[20][6] = TABLE;
        g[21][5] = BARREL; g[21][6] = BARREL;
        g[20][9] = SHELF; g[20][10] = SHELF;

        // ── 9. Vine curtain accents ───────────────────────────────────────
        // Hanging vines at interior doorways
        g[9][9] = VINE; g[9][20] = VINE;
        g[20][9] = VINE;

        // ── 10. Amber crystal pillars ─────────────────────────────────────
        g[10][10] = ACRYST; g[10][19] = ACRYST;
        g[18][10] = ACRYST; g[18][19] = ACRYST;

        // ── 11. Torch lighting along walls ────────────────────────────────
        g[5][12] = TORCH; g[5][17] = TORCH;
        g[19][5] = TORCH; g[19][12] = TORCH;

        // ── Write JSON ────────────────────────────────────────────────────
        writeJSON("data/towns/roothollow.rfmap");
        System.out.println("Done — data/towns/roothollow.rfmap written.");
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    static void fill(int r1, int r2, int c1, int c2, char tile) {
        for (int r = r1; r <= r2; r++)
            for (int c = c1; c <= c2; c++)
                g[r][c] = tile;
    }

    static void hwall(int row, int c1, int c2) {
        for (int c = c1; c <= c2; c++) g[row][c] = BWALL;
    }

    static void vwall(int col, int r1, int r2) {
        for (int r = r1; r <= r2; r++) g[r][col] = BWALL;
    }

    static String esc(char ch) {
        if (ch == '"') return "\"\\\"\"";
        if (ch == '\\') return "\"\\\\\"";
        if (ch > 127) return "\"\\u" + String.format("%04X", (int) ch) + "\"";
        return "\"" + ch + "\"";
    }

    static void writeJSON(String path) throws Exception {
        new File(path).getParentFile().mkdirs();
        PrintWriter pw = new PrintWriter(new OutputStreamWriter(
            new FileOutputStream(path), "UTF-8"));
        pw.println("{");
        pw.println("  \"type\": \"TOWN\",");
        pw.println("  \"name\": \"roothollow\",");
        pw.println("  \"width\": 30,");
        pw.println("  \"height\": 30,");
        pw.println("  \"interiorEntryX\": 14,");
        pw.println("  \"interiorEntryY\": 26,");
        pw.println("  \"tiles\": [");
        for (int r = 0; r < ROWS; r++) {
            pw.print("    [");
            for (int c = 0; c < COLS; c++) {
                pw.print(esc(g[r][c]));
                if (c < COLS - 1) pw.print(",");
            }
            pw.print("]");
            if (r < ROWS - 1) pw.print(",");
            pw.println();
        }
        pw.println("  ],");

        // spawnDifficulty: all zeros
        pw.println("  \"spawnDifficulty\": [");
        for (int r = 0; r < ROWS; r++) {
            pw.print("    [");
            for (int c = 0; c < COLS; c++) {
                pw.print("0");
                if (c < COLS - 1) pw.print(",");
            }
            pw.print("]");
            if (r < ROWS - 1) pw.print(",");
            pw.println();
        }
        pw.println("  ],");

        // NPCs
        pw.println("  \"npcs\": [");

        // NPC 1: Elder Varen — innkeeper
        pw.println("    {");
        pw.println("      \"id\": \"npc_roothollow_varen\",");
        pw.println("      \"name\": \"Elder Varen\",");
        pw.println("      \"spriteName\": \"npcs/townsman\",");
        pw.println("      \"type\": \"INNKEEPER\",");
        pw.println("      \"defaultDialog\": \"Welcome to Roothollow, traveler. Rest your bones within the Great Tree.\",");
        pw.println("      \"questCompleteDialog\": \"\",");
        pw.println("      \"shopItemIds\": [],");
        pw.println("      \"x\": 7,");
        pw.println("      \"y\": 7,");
        pw.println("      \"dialogueTree\": {");
        pw.println("        \"startNodeId\": \"greeting\",");
        pw.println("        \"nodes\": {");
        pw.println("          \"greeting\": {");
        pw.println("            \"id\": \"greeting\",");
        pw.println("            \"text\": \"Welcome to Roothollow, traveler. The Great Tree shelters all who seek peace. Would you like to rest?\",");
        pw.println("            \"choices\": [");
        pw.println("              {\"label\": \"I\\u0027d like to rest. (Full heal, 20g)\", \"nextNodeId\": \"rest\", \"conditions\": [{\"type\": \"HAS_GOLD\", \"target\": \"\", \"value\": \"\", \"amount\": 20}]},");
        pw.println("              {\"label\": \"Tell me about Roothollow.\", \"nextNodeId\": \"about\", \"conditions\": []},");
        pw.println("              {\"label\": \"Farewell.\", \"nextNodeId\": \"farewell\", \"conditions\": []}");
        pw.println("            ],");
        pw.println("            \"actions\": []");
        pw.println("          },");
        pw.println("          \"rest\": {");
        pw.println("            \"id\": \"rest\",");
        pw.println("            \"text\": \"Sleep well. The roots will watch over you.\",");
        pw.println("            \"choices\": [],");
        pw.println("            \"actions\": [");
        pw.println("              {\"type\": \"GIVE_GOLD\", \"target\": \"\", \"value\": \"\", \"amount\": -20},");
        pw.println("              {\"type\": \"HEAL_PLAYER\", \"target\": \"\", \"value\": \"\", \"amount\": 0},");
        pw.println("              {\"type\": \"LOG_MESSAGE\", \"target\": \"You rest within the Great Tree and awaken fully restored.\", \"value\": \"GOOD\", \"amount\": 0},");
        pw.println("              {\"type\": \"CLOSE_DIALOGUE\", \"target\": \"\", \"value\": \"\", \"amount\": 0}");
        pw.println("            ]");
        pw.println("          },");
        pw.println("          \"about\": {");
        pw.println("            \"id\": \"about\",");
        pw.println("            \"text\": \"This tree was ancient before the Shattering. When the world broke, we hollowed it out and made it home. But lately... it grows too fast. New rooms appear overnight. Walls shift. The tree has a will of its own.\",");
        pw.println("            \"choices\": [");
        pw.println("              {\"label\": \"Sounds unsettling.\", \"nextNodeId\": \"farewell\", \"conditions\": []}");
        pw.println("            ],");
        pw.println("            \"actions\": []");
        pw.println("          },");
        pw.println("          \"farewell\": {");
        pw.println("            \"id\": \"farewell\",");
        pw.println("            \"text\": \"May the roots hold you steady, traveler.\",");
        pw.println("            \"choices\": [],");
        pw.println("            \"actions\": [{\"type\": \"CLOSE_DIALOGUE\", \"target\": \"\", \"value\": \"\", \"amount\": 0}]");
        pw.println("          }");
        pw.println("        }");
        pw.println("      }");
        pw.println("    },");

        // NPC 2: Sylva — shopkeeper
        pw.println("    {");
        pw.println("      \"id\": \"npc_roothollow_sylva\",");
        pw.println("      \"name\": \"Sylva the Herbalist\",");
        pw.println("      \"spriteName\": \"npcs/townsman\",");
        pw.println("      \"type\": \"SHOPKEEPER\",");
        pw.println("      \"defaultDialog\": \"The forest provides. Browse my wares.\",");
        pw.println("      \"questCompleteDialog\": \"\",");
        pw.println("      \"shopItemIds\": [\"healing_potion\", \"greater_healing_potion\", \"sylvan_healing_draught\", \"antidote\"],");
        pw.println("      \"x\": 20,");
        pw.println("      \"y\": 7,");
        pw.println("      \"dialogueTree\": {");
        pw.println("        \"startNodeId\": \"greeting\",");
        pw.println("        \"nodes\": {");
        pw.println("          \"greeting\": {");
        pw.println("            \"id\": \"greeting\",");
        pw.println("            \"text\": \"The forest provides all that we need. Herbs, tinctures, remedies \\u2014 what do you seek?\",");
        pw.println("            \"choices\": [");
        pw.println("              {\"label\": \"Show me your wares.\", \"nextNodeId\": \"shop\", \"conditions\": []},");
        pw.println("              {\"label\": \"Just browsing.\", \"nextNodeId\": \"farewell\", \"conditions\": []}");
        pw.println("            ],");
        pw.println("            \"actions\": []");
        pw.println("          },");
        pw.println("          \"shop\": {");
        pw.println("            \"id\": \"shop\",");
        pw.println("            \"text\": \"Take what you need.\",");
        pw.println("            \"choices\": [],");
        pw.println("            \"actions\": [{\"type\": \"OPEN_SHOP\", \"target\": \"\", \"value\": \"\", \"amount\": 0}]");
        pw.println("          },");
        pw.println("          \"farewell\": {");
        pw.println("            \"id\": \"farewell\",");
        pw.println("            \"text\": \"May the canopy shelter you.\",");
        pw.println("            \"choices\": [],");
        pw.println("            \"actions\": [{\"type\": \"CLOSE_DIALOGUE\", \"target\": \"\", \"value\": \"\", \"amount\": 0}]");
        pw.println("          }");
        pw.println("        }");
        pw.println("      }");
        pw.println("    },");

        // NPC 3: Root Warden
        pw.println("    {");
        pw.println("      \"id\": \"npc_roothollow_warden\",");
        pw.println("      \"name\": \"Root Warden\",");
        pw.println("      \"spriteName\": \"npcs/townsman\",");
        pw.println("      \"type\": \"GUARD\",");
        pw.println("      \"defaultDialog\": \"The Great Tree watches. Tread carefully within its heartwood.\",");
        pw.println("      \"questCompleteDialog\": \"\",");
        pw.println("      \"shopItemIds\": [],");
        pw.println("      \"x\": 14,");
        pw.println("      \"y\": 24");
        pw.println("    }");

        pw.println("  ],");
        pw.println("  \"townEntrances\": [],");
        pw.println("  \"overworldTeleporters\": []");
        pw.println("}");
        pw.close();
        System.out.println("  wrote: " + path);
    }
}

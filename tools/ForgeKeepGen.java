import java.io.*;

/**
 * Generates data/towns/forge_keep.rfmap — a 25×25 temple-fortress town
 * built over a volcanic vent, domain of Pyralis.
 *
 * Run from project root:
 *   javac tools/ForgeKeepGen.java -d tools/
 *   java -cp tools ForgeKeepGen
 */
public class ForgeKeepGen {

    static final int COLS = 25, ROWS = 25;
    static char[][] g = new char[ROWS][COLS];

    // Tile IDs
    static final char VROCK   = '!';  // volcanic rock (exterior)
    static final char OBSID   = '(';  // obsidian floor
    static final char FFLOOR  = '*';  // forge floor (main halls)
    static final char WALL    = 'W';  // stone wall
    static final char DOOR    = 'd';  // wooden door
    static final char COUNTER = 'c';  // counter
    static final char BARREL  = 'n';  // barrel
    static final char FORGE   = 'N';  // forge
    static final char TORCH   = 'v';  // wall torch
    static final char EGRATE  = '+';  // ember grate
    static final char LCHAN   = '-';  // lava channel
    static final char PILLAR  = 'M';  // stone pillar
    static final char ALTAR   = 'x';  // Pyralis altar
    static final char SHELF   = 'k';  // bookshelf
    static final char FIRE    = 'u';  // fireplace

    public static void main(String[] args) throws Exception {

        // ── 1. Base layer: volcanic rock everywhere ────────────────────────
        fill(0, 24, 0, 24, VROCK);

        // ── 2. Double-thick stone walls (perimeter) ────────────────────────
        hwall(0,  0, 24);   hwall(1,  0, 24);
        hwall(23, 0, 24);   hwall(24, 0, 24);
        vwall(0,  0, 24);   vwall(1,  0, 24);
        vwall(23, 0, 24);   vwall(24, 0, 24);

        // ── 3. Entry: south wall doors at cols 11-12 ───────────────────────
        g[24][11] = DOOR;  g[24][12] = DOOR;
        g[23][11] = DOOR;  g[23][12] = DOOR;

        // ── 4. Outer hall: forge floor ─────────────────────────────────────
        fill(3, 22, 3, 22, FFLOOR);

        // ── 5. Torches along outer hall walls ──────────────────────────────
        // North wall torches (row 2, inside perimeter)
        g[2][5]  = TORCH;  g[2][9]  = TORCH;  g[2][15] = TORCH;  g[2][19] = TORCH;
        // South wall torches (row 22, inside perimeter)
        g[22][5] = TORCH;  g[22][9] = TORCH;  g[22][15] = TORCH;  g[22][19] = TORCH;
        // West wall torches (col 2)
        g[5][2]  = TORCH;  g[10][2] = TORCH;  g[15][2] = TORCH;  g[20][2] = TORCH;
        // East wall torches (col 22)
        g[5][22] = TORCH;  g[10][22] = TORCH;  g[15][22] = TORCH;  g[20][22] = TORCH;

        // ── 6. Guard posts: SW and SE corners of outer hall ────────────────
        // SW guard post: rows 21-22, cols 3-6
        g[21][3] = COUNTER;  g[21][4] = COUNTER;  g[21][5] = COUNTER;  g[21][6] = COUNTER;
        g[22][3] = BARREL;   g[22][4] = BARREL;   g[22][5] = BARREL;   g[22][6] = BARREL;
        // SE guard post: rows 21-22, cols 19-22
        g[21][19] = COUNTER;  g[21][20] = COUNTER;  g[21][21] = COUNTER;  g[21][22] = COUNTER;
        g[22][19] = BARREL;   g[22][20] = BARREL;   g[22][21] = BARREL;   g[22][22] = BARREL;

        // ── 7. Inner sanctum wall ring ─────────────────────────────────────
        // rows 3-15, cols 7-17
        hwall(3,  7, 17);
        hwall(15, 7, 17);
        vwall(7,  3, 15);
        vwall(17, 3, 15);

        // Inner sanctum door: row 15, cols 11-12
        g[15][11] = DOOR;  g[15][12] = DOOR;

        // Inner sanctum floor: obsidian
        fill(4, 14, 8, 16, OBSID);

        // ── 8. Lava channels along inner sanctum ───────────────────────────
        // North lava channel: row 4, cols 8-16
        for (int c = 8; c <= 16; c++) g[4][c] = LCHAN;
        // South lava channel: row 14, cols 8-16
        for (int c = 8; c <= 16; c++) g[14][c] = LCHAN;

        // Ember grates at corners
        g[4][8]  = EGRATE;  g[4][16]  = EGRATE;
        g[14][8] = EGRATE;  g[14][16] = EGRATE;

        // ── 9. Central altar and flanking pillars ──────────────────────────
        g[8][11] = ALTAR;   g[8][12] = ALTAR;
        g[8][9]  = PILLAR;  g[8][14] = PILLAR;

        // ── 10. Forge area: rows 17-20, cols 8-16 ─────────────────────────
        // Forges along north edge
        g[17][8]  = FORGE;  g[17][9]  = FORGE;  g[17][10] = FORGE;
        g[17][14] = FORGE;  g[17][15] = FORGE;  g[17][16] = FORGE;
        // Central forge
        g[18][11] = FORGE;  g[18][12] = FORGE;
        // Barrels (raw materials)
        g[19][8]  = BARREL;  g[19][9]  = BARREL;
        g[19][15] = BARREL;  g[19][16] = BARREL;
        // Counters (workbenches)
        g[20][8]  = COUNTER; g[20][9]  = COUNTER; g[20][10] = COUNTER;
        g[20][14] = COUNTER; g[20][15] = COUNTER; g[20][16] = COUNTER;

        // ── 11. Shelves and fireplace accents in inner sanctum ─────────────
        g[5][8]  = SHELF;  g[5][9]  = SHELF;
        g[5][15] = SHELF;  g[5][16] = SHELF;
        g[6][8]  = FIRE;   g[6][16] = FIRE;

        // ── 12. Pillars in outer hall ──────────────────────────────────────
        g[6][4]  = PILLAR;  g[6][20]  = PILLAR;
        g[12][4] = PILLAR;  g[12][20] = PILLAR;
        g[18][4] = PILLAR;  g[18][20] = PILLAR;

        // ── 13. Write JSON ─────────────────────────────────────────────────
        writeJSON("data/towns/forge_keep.rfmap");
        System.out.println("Done — data/towns/forge_keep.rfmap written.");
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    static void fill(int r1, int r2, int c1, int c2, char tile) {
        for (int r = r1; r <= r2; r++)
            for (int c = c1; c <= c2; c++)
                g[r][c] = tile;
    }

    static void hwall(int row, int c1, int c2) {
        for (int c = c1; c <= c2; c++) g[row][c] = WALL;
    }

    static void vwall(int col, int r1, int r2) {
        for (int r = r1; r <= r2; r++) g[r][col] = WALL;
    }

    // ── JSON output ────────────────────────────────────────────────────────

    static void writeJSON(String path) throws Exception {
        new File(path).getParentFile().mkdirs();
        PrintWriter pw = new PrintWriter(new FileWriter(path));
        pw.println("{");
        pw.println("  \"type\": \"TOWN\",");
        pw.println("  \"name\": \"forge_keep\",");
        pw.println("  \"width\": 25,");
        pw.println("  \"height\": 25,");
        pw.println("  \"interiorEntryX\": 11,");
        pw.println("  \"interiorEntryY\": 23,");
        pw.println("  \"tiles\": [");
        for (int r = 0; r < ROWS; r++) {
            pw.print("    [");
            for (int c = 0; c < COLS; c++) {
                char ch = g[r][c];
                String s;
                if (ch == '"') s = "\"\\\"\"";
                else if (ch == '\\') s = "\"\\\\\"";
                else s = "\"" + ch + "\"";
                pw.print(s);
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

        // NPC 1: Emberpriest Cael (with full dialogue tree)
        pw.println("    {");
        pw.println("      \"id\": \"npc_forge_keep_cael\",");
        pw.println("      \"name\": \"Emberpriest Cael\",");
        pw.println("      \"spriteName\": \"npcs/townsman\",");
        pw.println("      \"type\": \"QUESTGIVER\",");
        pw.println("      \"defaultDialog\": \"The forge burns eternal. You stand in Pyralis\\u0027s domain, Unbound.\",");
        pw.println("      \"questCompleteDialog\": \"\",");
        pw.println("      \"shopItemIds\": [],");
        pw.println("      \"x\": 11,");
        pw.println("      \"y\": 9,");
        pw.println("      \"dialogueTree\": {");
        pw.println("        \"startNodeId\": \"greeting\",");
        pw.println("        \"nodes\": {");

        // greeting node
        pw.println("          \"greeting\": {");
        pw.println("            \"id\": \"greeting\",");
        pw.println("            \"text\": \"The forge burns eternal. You stand in Pyralis\\u0027s domain, Unbound.\",");
        pw.println("            \"choices\": [");
        pw.println("              {\"label\": \"Tell me about the Crucible Trial.\", \"nextNodeId\": \"crucible_ask\", \"conditions\": []},");
        pw.println("              {\"label\": \"I seek the Shard of Ambition.\", \"nextNodeId\": \"shard_ask\", \"conditions\": []},");
        pw.println("              {\"label\": \"I have completed the Crucible Trial.\", \"nextNodeId\": \"crucible_done\", \"conditions\": [");
        pw.println("                {\"type\": \"QUEST_COMPLETE\", \"target\": \"pyralis_crucible_trial\", \"value\": \"\", \"amount\": 0}");
        pw.println("              ]},");
        pw.println("              {\"label\": \"[The shard pulses in your pack...]\", \"nextNodeId\": \"corruption_path\", \"conditions\": [");
        pw.println("                {\"type\": \"FLAG_SET\", \"target\": \"has_shard_of_corruption\", \"value\": \"\", \"amount\": 0}");
        pw.println("              ]},");
        pw.println("              {\"label\": \"Farewell.\", \"nextNodeId\": \"farewell\", \"conditions\": []}");
        pw.println("            ],");
        pw.println("            \"actions\": []");
        pw.println("          },");

        // crucible_ask node
        pw.println("          \"crucible_ask\": {");
        pw.println("            \"id\": \"crucible_ask\",");
        pw.println("            \"text\": \"The Crucible is Pyralis\\u0027s test of will and flame. Only those who endure its trials may earn his favor. Descend into the volcanic depths and return with proof of your resolve.\",");
        pw.println("            \"choices\": [");
        pw.println("              {\"label\": \"I accept the trial.\", \"nextNodeId\": \"crucible_accepted\", \"conditions\": [");
        pw.println("                {\"type\": \"FLAG_NOT_SET\", \"target\": \"pyralis_crucible_done\", \"value\": \"\", \"amount\": 0}");
        pw.println("              ]},");
        pw.println("              {\"label\": \"Not yet. Tell me more.\", \"nextNodeId\": \"greeting\", \"conditions\": []}");
        pw.println("            ],");
        pw.println("            \"actions\": []");
        pw.println("          },");

        // crucible_accepted node
        pw.println("          \"crucible_accepted\": {");
        pw.println("            \"id\": \"crucible_accepted\",");
        pw.println("            \"text\": \"Then steel yourself, Unbound. The flames do not forgive weakness. Return when the trial is complete.\",");
        pw.println("            \"choices\": [");
        pw.println("              {\"label\": \"I will not fail.\", \"nextNodeId\": \"farewell\", \"conditions\": []}");
        pw.println("            ],");
        pw.println("            \"actions\": [");
        pw.println("              {\"type\": \"GIVE_QUEST\", \"target\": \"pyralis_crucible_trial\", \"value\": \"\", \"amount\": 0}");
        pw.println("            ]");
        pw.println("          },");

        // shard_ask node
        pw.println("          \"shard_ask\": {");
        pw.println("            \"id\": \"shard_ask\",");
        pw.println("            \"text\": \"The Shard of Ambition is not given freely. It must be claimed from the heart of the forge-depths, where Pyralis\\u0027s power runs hottest. Few return unchanged.\",");
        pw.println("            \"choices\": [");
        pw.println("              {\"label\": \"I will seek the Shard.\", \"nextNodeId\": \"shard_accepted\", \"conditions\": [");
        pw.println("                {\"type\": \"FLAG_NOT_SET\", \"target\": \"pyralis_crucible_done\", \"value\": \"\", \"amount\": 0}");
        pw.println("              ]},");
        pw.println("              {\"label\": \"Perhaps another time.\", \"nextNodeId\": \"greeting\", \"conditions\": []}");
        pw.println("            ],");
        pw.println("            \"actions\": []");
        pw.println("          },");

        // shard_accepted node
        pw.println("          \"shard_accepted\": {");
        pw.println("            \"id\": \"shard_accepted\",");
        pw.println("            \"text\": \"Ambition without temperance is destruction. Remember that when the flames call to you.\",");
        pw.println("            \"choices\": [");
        pw.println("              {\"label\": \"Understood.\", \"nextNodeId\": \"farewell\", \"conditions\": []}");
        pw.println("            ],");
        pw.println("            \"actions\": [");
        pw.println("              {\"type\": \"GIVE_QUEST\", \"target\": \"shard_of_ambition\", \"value\": \"\", \"amount\": 0}");
        pw.println("            ]");
        pw.println("          },");

        // crucible_done node
        pw.println("          \"crucible_done\": {");
        pw.println("            \"id\": \"crucible_done\",");
        pw.println("            \"text\": \"You have walked through fire and emerged unbroken. Pyralis is pleased. Take this — the Key of Embers. It will open paths that were sealed to you before.\",");
        pw.println("            \"choices\": [],");
        pw.println("            \"actions\": [");
        pw.println("              {\"type\": \"GIVE_ITEM\", \"target\": \"key_of_embers\", \"value\": \"\", \"amount\": 1},");
        pw.println("              {\"type\": \"SET_FLAG\", \"target\": \"pyralis_crucible_done\", \"value\": \"true\", \"amount\": 0},");
        pw.println("              {\"type\": \"LOG_MESSAGE\", \"target\": \"Emberpriest Cael presents you with the Key of Embers.\", \"value\": \"GOOD\", \"amount\": 0},");
        pw.println("              {\"type\": \"CLOSE_DIALOGUE\", \"target\": \"\", \"value\": \"\", \"amount\": 0}");
        pw.println("            ]");
        pw.println("          },");

        // corruption_path node
        pw.println("          \"corruption_path\": {");
        pw.println("            \"id\": \"corruption_path\",");
        pw.println("            \"text\": \"That darkness you carry... Pyralis sees it. The Shard of Corruption whispers lies dressed as ambition. Be wary, Unbound — the forge can purify, but only if you are willing to let go.\",");
        pw.println("            \"choices\": [");
        pw.println("              {\"label\": \"I will consider your words.\", \"nextNodeId\": \"farewell\", \"conditions\": []}");
        pw.println("            ],");
        pw.println("            \"actions\": []");
        pw.println("          },");

        // farewell node
        pw.println("          \"farewell\": {");
        pw.println("            \"id\": \"farewell\",");
        pw.println("            \"text\": \"May the forge light your path, Unbound.\",");
        pw.println("            \"choices\": [],");
        pw.println("            \"actions\": [");
        pw.println("              {\"type\": \"CLOSE_DIALOGUE\", \"target\": \"\", \"value\": \"\", \"amount\": 0}");
        pw.println("            ]");
        pw.println("          }");

        pw.println("        }");  // end nodes
        pw.println("      }");    // end dialogueTree
        pw.println("    },");     // end Emberpriest Cael

        // NPC 2: Forge Guard Captain
        pw.println("    {");
        pw.println("      \"id\": \"npc_forge_keep_guard\",");
        pw.println("      \"name\": \"Forge Guard Captain\",");
        pw.println("      \"spriteName\": \"npcs/townsman\",");
        pw.println("      \"type\": \"GUARD\",");
        pw.println("      \"defaultDialog\": \"No weapons drawn in the Keep. Pyralis watches.\",");
        pw.println("      \"questCompleteDialog\": \"\",");
        pw.println("      \"shopItemIds\": [],");
        pw.println("      \"x\": 5,");
        pw.println("      \"y\": 21");
        pw.println("    },");

        // NPC 3: Acolyte
        pw.println("    {");
        pw.println("      \"id\": \"npc_forge_keep_acolyte\",");
        pw.println("      \"name\": \"Acolyte\",");
        pw.println("      \"spriteName\": \"npcs/townsman\",");
        pw.println("      \"type\": \"TOWNSFOLK\",");
        pw.println("      \"defaultDialog\": \"The forges never rest. Pyralis demands perfection in all things.\",");
        pw.println("      \"questCompleteDialog\": \"\",");
        pw.println("      \"shopItemIds\": [],");
        pw.println("      \"x\": 20,");
        pw.println("      \"y\": 10");
        pw.println("    }");

        pw.println("  ],");
        pw.println("  \"townEntrances\": [],");
        pw.println("  \"overworldTeleporters\": []");
        pw.println("}");
        pw.close();
        System.out.println("  wrote: " + path);
    }
}

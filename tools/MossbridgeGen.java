import java.io.*;

/**
 * Generates data/towns/mossbridge.rfmap — a 25x25 settlement on living
 * root-bridges over a ravine. Eastern town of Sylvandar.
 *
 * Run from project root:
 *   javac tools/MossbridgeGen.java -d tools/
 *   java -cp tools MossbridgeGen
 */
public class MossbridgeGen {

    static final int COLS = 25, ROWS = 25;
    static char[][] g = new char[ROWS][COLS];

    static final char RFLOOR = '\uE06A';
    static final char BWALL  = '\uE06B';
    static final char VINE   = '\uE06C';
    static final char ACRYST = '\uE06D';
    static final char FLAMP  = '\uE06E';
    static final char MOSS   = '\uE063';
    static final char DFOREST= '\uE060';
    static final char MSWAMP = '\uE062';
    static final char WALL   = 'W';
    static final char DOOR   = 'd';
    static final char COUNTER= 'c';
    static final char BARREL = 'n';
    static final char SHELF  = 'k';
    static final char BED    = 'l';
    static final char TABLE  = 'i';
    static final char CHAIR  = 'j';
    static final char FIRE   = 'u';
    static final char TORCH  = 'v';

    public static void main(String[] args) throws Exception {

        // ── 1. Base: dense forest ─────────────────────────────────────────
        fill(0, 24, 0, 24, DFOREST);

        // ── 2. Ravine running north-south through center ──────────────────
        // Cols 10-14 are the ravine (mangrove swamp / void below)
        for (int r = 0; r < ROWS; r++) {
            for (int c = 10; c <= 14; c++) {
                g[r][c] = MSWAMP;
            }
        }

        // ── 3. West platform (main settlement) ───────────────────────────
        fill(2, 22, 1, 9, RFLOOR);
        // Bark wall border
        hwall(2, 1, 9); hwall(22, 1, 9);
        vwall(1, 2, 22);

        // ── 4. East platform (smaller, shop/lodge) ────────────────────────
        fill(4, 20, 15, 23, RFLOOR);
        hwall(4, 15, 23); hwall(20, 15, 23);
        vwall(23, 4, 20);

        // ── 5. Root bridges connecting platforms ──────────────────────────
        // North bridge (row 8)
        for (int c = 9; c <= 15; c++) g[8][c] = RFLOOR;
        // South bridge (row 16)
        for (int c = 9; c <= 15; c++) g[16][c] = RFLOOR;

        // ── 6. Entry from south (west side) ───────────────────────────────
        g[22][5] = DOOR; g[22][6] = DOOR;

        // ── 7. West buildings ─────────────────────────────────────────────

        // Lodge (NW corner)
        hwall(3, 2, 8); hwall(7, 2, 8);
        vwall(8, 3, 7);
        g[7][4] = DOOR;
        fill(4, 6, 2, 7, RFLOOR);
        g[4][2] = BED; g[4][3] = BED;
        g[5][2] = BED; g[5][3] = BED;
        g[4][6] = FIRE;
        g[6][5] = TABLE; g[6][6] = CHAIR;

        // Meeting hall (middle west)
        hwall(10, 2, 8); hwall(14, 2, 8);
        vwall(8, 10, 14);
        g[14][5] = DOOR;
        fill(11, 13, 2, 7, RFLOOR);
        g[11][4] = TABLE; g[11][5] = TABLE;
        g[12][3] = CHAIR; g[12][6] = CHAIR;
        g[11][2] = TORCH; g[11][7] = TORCH;

        // ── 8. East buildings ─────────────────────────────────────────────

        // Shop (NE of east platform)
        hwall(5, 16, 22); hwall(9, 16, 22);
        vwall(16, 5, 9);
        g[9][19] = DOOR;
        fill(6, 8, 17, 21, RFLOOR);
        g[6][17] = COUNTER; g[6][18] = COUNTER; g[6][19] = COUNTER;
        g[7][17] = SHELF; g[7][18] = SHELF;
        g[7][21] = BARREL; g[8][21] = BARREL;

        // Herbalist hut (SE of east platform)
        hwall(13, 16, 22); hwall(18, 16, 22);
        vwall(16, 13, 18);
        g[13][19] = DOOR;
        fill(14, 17, 17, 21, RFLOOR);
        g[14][17] = TABLE; g[14][18] = BARREL;
        g[15][17] = SHELF; g[15][18] = SHELF;
        g[16][21] = FLAMP;

        // ── 9. Lighting ───────────────────────────────────────────────────
        g[3][5] = FLAMP;  g[3][9] = FLAMP;
        g[10][5] = FLAMP;
        g[19][8] = FLAMP; g[19][16] = FLAMP;
        g[5][22] = FLAMP;
        g[8][12] = FLAMP;  // on north bridge
        g[16][12] = FLAMP; // on south bridge

        // ── 10. Amber crystal accents ─────────────────────────────────────
        g[9][2] = ACRYST; g[9][8] = ACRYST;
        g[15][2] = ACRYST;

        // ── Write JSON ────────────────────────────────────────────────────
        writeJSON("data/towns/mossbridge.rfmap");
        System.out.println("Done — data/towns/mossbridge.rfmap written.");
    }

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
        pw.println("  \"name\": \"mossbridge\",");
        pw.println("  \"width\": 25,");
        pw.println("  \"height\": 25,");
        pw.println("  \"interiorEntryX\": 5,");
        pw.println("  \"interiorEntryY\": 21,");
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

        // NPC 1: Bridgekeeper Orin — innkeeper
        pw.println("    {");
        pw.println("      \"id\": \"npc_mossbridge_orin\",");
        pw.println("      \"name\": \"Bridgekeeper Orin\",");
        pw.println("      \"spriteName\": \"npcs/townsman\",");
        pw.println("      \"type\": \"INNKEEPER\",");
        pw.println("      \"defaultDialog\": \"The bridges hold. They always hold. Rest here if you\\u0027re weary.\",");
        pw.println("      \"questCompleteDialog\": \"\",");
        pw.println("      \"shopItemIds\": [],");
        pw.println("      \"x\": 5,");
        pw.println("      \"y\": 5,");
        pw.println("      \"dialogueTree\": {");
        pw.println("        \"startNodeId\": \"greeting\",");
        pw.println("        \"nodes\": {");
        pw.println("          \"greeting\": {");
        pw.println("            \"id\": \"greeting\",");
        pw.println("            \"text\": \"Welcome to Mossbridge. The roots grew these bridges themselves \\u2014 strong as stone, alive as spring. Need a bed?\",");
        pw.println("            \"choices\": [");
        pw.println("              {\"label\": \"I\\u0027d like to rest. (Full heal, 25g)\", \"nextNodeId\": \"rest\", \"conditions\": [{\"type\": \"HAS_GOLD\", \"target\": \"\", \"value\": \"\", \"amount\": 25}]},");
        pw.println("              {\"label\": \"Tell me about the ravine.\", \"nextNodeId\": \"ravine\", \"conditions\": []},");
        pw.println("              {\"label\": \"Goodbye.\", \"nextNodeId\": \"farewell\", \"conditions\": []}");
        pw.println("            ],");
        pw.println("            \"actions\": []");
        pw.println("          },");
        pw.println("          \"rest\": {");
        pw.println("            \"id\": \"rest\",");
        pw.println("            \"text\": \"The roots will rock you to sleep. Rest well.\",");
        pw.println("            \"choices\": [],");
        pw.println("            \"actions\": [");
        pw.println("              {\"type\": \"GIVE_GOLD\", \"target\": \"\", \"value\": \"\", \"amount\": -25},");
        pw.println("              {\"type\": \"HEAL_PLAYER\", \"target\": \"\", \"value\": \"\", \"amount\": 0},");
        pw.println("              {\"type\": \"LOG_MESSAGE\", \"target\": \"You sleep on a bed of woven roots and awaken fully healed.\", \"value\": \"GOOD\", \"amount\": 0},");
        pw.println("              {\"type\": \"CLOSE_DIALOGUE\", \"target\": \"\", \"value\": \"\", \"amount\": 0}");
        pw.println("            ]");
        pw.println("          },");
        pw.println("          \"ravine\": {");
        pw.println("            \"id\": \"ravine\",");
        pw.println("            \"text\": \"The ravine appeared during the Shattering \\u2014 the land split clean through. But Sylvandar\\u0027s roots grew across it in a single night. The bridges have held for thirty years.\",");
        pw.println("            \"choices\": [");
        pw.println("              {\"label\": \"Impressive.\", \"nextNodeId\": \"farewell\", \"conditions\": []}");
        pw.println("            ],");
        pw.println("            \"actions\": []");
        pw.println("          },");
        pw.println("          \"farewell\": {");
        pw.println("            \"id\": \"farewell\",");
        pw.println("            \"text\": \"Watch your step on the bridges. They\\u0027re alive, but they\\u0027re steady.\",");
        pw.println("            \"choices\": [],");
        pw.println("            \"actions\": [{\"type\": \"CLOSE_DIALOGUE\", \"target\": \"\", \"value\": \"\", \"amount\": 0}]");
        pw.println("          }");
        pw.println("        }");
        pw.println("      }");
        pw.println("    },");

        // NPC 2: Trader Fenn — shopkeeper
        pw.println("    {");
        pw.println("      \"id\": \"npc_mossbridge_fenn\",");
        pw.println("      \"name\": \"Trader Fenn\",");
        pw.println("      \"spriteName\": \"npcs/townsman\",");
        pw.println("      \"type\": \"SHOPKEEPER\",");
        pw.println("      \"defaultDialog\": \"Goods from three islands, friend. Take a look.\",");
        pw.println("      \"questCompleteDialog\": \"\",");
        pw.println("      \"shopItemIds\": [\"healing_potion\", \"greater_healing_potion\", \"verdant_blade\", \"rootweave_armor\", \"amber_shield\", \"thornwood_staff\"],");
        pw.println("      \"x\": 19,");
        pw.println("      \"y\": 8,");
        pw.println("      \"dialogueTree\": {");
        pw.println("        \"startNodeId\": \"greeting\",");
        pw.println("        \"nodes\": {");
        pw.println("          \"greeting\": {");
        pw.println("            \"id\": \"greeting\",");
        pw.println("            \"text\": \"Goods from three islands! Weapons, armor, potions \\u2014 what catches your eye?\",");
        pw.println("            \"choices\": [");
        pw.println("              {\"label\": \"Show me what you have.\", \"nextNodeId\": \"shop\", \"conditions\": []},");
        pw.println("              {\"label\": \"Not today.\", \"nextNodeId\": \"farewell\", \"conditions\": []}");
        pw.println("            ],");
        pw.println("            \"actions\": []");
        pw.println("          },");
        pw.println("          \"shop\": {");
        pw.println("            \"id\": \"shop\",");
        pw.println("            \"text\": \"Take your pick, friend.\",");
        pw.println("            \"choices\": [],");
        pw.println("            \"actions\": [{\"type\": \"OPEN_SHOP\", \"target\": \"\", \"value\": \"\", \"amount\": 0}]");
        pw.println("          },");
        pw.println("          \"farewell\": {");
        pw.println("            \"id\": \"farewell\",");
        pw.println("            \"text\": \"I\\u0027ll be here. The bridges aren\\u0027t going anywhere.\",");
        pw.println("            \"choices\": [],");
        pw.println("            \"actions\": [{\"type\": \"CLOSE_DIALOGUE\", \"target\": \"\", \"value\": \"\", \"amount\": 0}]");
        pw.println("          }");
        pw.println("        }");
        pw.println("      }");
        pw.println("    },");

        // NPC 3: Moss Tender — townsfolk
        pw.println("    {");
        pw.println("      \"id\": \"npc_mossbridge_tender\",");
        pw.println("      \"name\": \"Moss Tender\",");
        pw.println("      \"spriteName\": \"npcs/townsman\",");
        pw.println("      \"type\": \"TOWNSFOLK\",");
        pw.println("      \"defaultDialog\": \"I tend the bridge-roots. They grow a little each day. Sometimes I think they\\u0027re reaching for the other side of something we can\\u0027t see.\",");
        pw.println("      \"questCompleteDialog\": \"\",");
        pw.println("      \"shopItemIds\": [],");
        pw.println("      \"x\": 12,");
        pw.println("      \"y\": 8");
        pw.println("    }");

        pw.println("  ],");
        pw.println("  \"townEntrances\": [],");
        pw.println("  \"overworldTeleporters\": []");
        pw.println("}");
        pw.close();
        System.out.println("  wrote: " + path);
    }
}

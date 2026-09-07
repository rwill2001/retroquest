import java.io.*;

/**
 * Generates data/towns/brightcoral.rfmap — a 20x20 bioluminescent coral village.
 * Beautiful but eerie — the coral is slowly replacing the residents.
 *
 * Run from project root:
 *   javac -encoding UTF-8 tools/BrightcoralGen.java -d tools/
 *   java -cp tools BrightcoralGen
 */
public class BrightcoralGen {

    static final int COLS = 20, ROWS = 20;
    static char[][] g = new char[ROWS][COLS];

    // Island 5 tile IDs
    static final char DOCEAN = '\uE070';  // deep ocean floor (exterior)
    static final char DFLOOR = '\uE07A';  // dome floor
    static final char CWALL  = '\uE07B';  // coral wall
    static final char KPLAT  = '\uE07C';  // kelp platform
    static final char PGLASS = '\uE07D';  // pressure glass
    static final char BLAMP  = '\uE07E';  // bioluminescent lamp
    static final char CREEF  = '\uE072';  // coral reef (impassable)
    static final char BSAND  = '\uE074';  // bioluminescent sand

    // Standard tile IDs
    static final char DOOR    = 'd';
    static final char COUNTER = 'c';
    static final char BARREL  = 'n';
    static final char SHELF   = 'k';
    static final char BED     = 'l';
    static final char TABLE   = 'i';
    static final char CHAIR   = 'j';
    static final char TORCH   = 'v';
    static final char ALTAR   = 'x';

    public static void main(String[] args) throws Exception {

        // ── 1. Exterior: bioluminescent sand everywhere ─────────────────
        fill(0, 19, 0, 19, BSAND);

        // ── 2. Coral reef border ────────────────────────────────────────
        // Irregular coral reef perimeter
        for (int y = 0; y < ROWS; y++) {
            for (int x = 0; x < COLS; x++) {
                double dx = (x - 10.0) / 9.5;
                double dy = (y - 10.0) / 9.5;
                double dist = Math.sqrt(dx * dx + dy * dy);
                if (dist < 0.80) {
                    g[y][x] = DFLOOR;
                }
                if (dist >= 0.78 && dist < 0.90) {
                    g[y][x] = CREEF;
                }
            }
        }

        // ── 3. Entry: south opening ─────────────────────────────────────
        g[18][10] = DOOR;
        g[17][10] = DFLOOR;
        g[19][10] = DFLOOR;

        // ── 4. Bioluminescent lamps — many, giving eerie glow ───────────
        g[4][10] = BLAMP;
        g[6][6] = BLAMP; g[6][14] = BLAMP;
        g[10][4] = BLAMP; g[10][16] = BLAMP;
        g[14][6] = BLAMP; g[14][14] = BLAMP;
        g[8][10] = BLAMP;
        g[12][10] = BLAMP;
        g[16][10] = BLAMP;

        // ── 5. Central coral altar ──────────────────────────────────────
        g[9][10] = ALTAR;
        g[9][9] = CWALL; g[9][11] = CWALL;
        g[10][9] = BLAMP; g[10][11] = BLAMP;

        // ── 6. Elder's dwelling (northwest) ─────────────────────────────
        hwall(5, 4, 8); vwall(8, 5, 8);
        g[5][6] = DOOR;
        g[6][4] = TABLE; g[6][5] = CHAIR;
        g[7][4] = SHELF; g[7][5] = SHELF;

        // ── 7. Merchant hut (northeast) ─────────────────────────────────
        hwall(5, 12, 16); vwall(12, 5, 8);
        g[5][14] = DOOR;
        g[6][13] = COUNTER; g[6][14] = COUNTER;
        g[7][13] = BARREL; g[7][14] = BARREL;
        g[7][15] = SHELF;

        // ── 8. Coral-child's grotto (south-center) ──────────────────────
        hwall(14, 7, 13); vwall(7, 14, 16); vwall(13, 14, 16);
        g[14][10] = DOOR;
        g[15][8] = KPLAT; g[15][9] = KPLAT; g[15][10] = KPLAT;
        g[15][11] = KPLAT; g[15][12] = KPLAT;
        g[16][10] = BLAMP;

        // ── 9. Scattered coral growths (decorative, eerie) ──────────────
        g[4][7] = CWALL; g[4][13] = CWALL;
        g[11][5] = CWALL; g[11][15] = CWALL;
        g[13][4] = CWALL; g[13][16] = CWALL;

        // ── Write JSON ──────────────────────────────────────────────────
        writeJSON("data/towns/brightcoral.rfmap");
        System.out.println("Done \u2014 data/towns/brightcoral.rfmap written.");
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    static void fill(int r1, int r2, int c1, int c2, char tile) {
        for (int r = r1; r <= r2; r++)
            for (int c = c1; c <= c2; c++)
                g[r][c] = tile;
    }

    static void hwall(int row, int c1, int c2) {
        for (int c = c1; c <= c2; c++) g[row][c] = CWALL;
    }

    static void vwall(int col, int r1, int r2) {
        for (int r = r1; r <= r2; r++) g[r][col] = CWALL;
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
        pw.println("  \"name\": \"brightcoral\",");
        pw.println("  \"width\": 20,");
        pw.println("  \"height\": 20,");
        pw.println("  \"interiorEntryX\": 10,");
        pw.println("  \"interiorEntryY\": 17,");
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

        // NPC 1: Coral Builder Elder — lore about transformation
        pw.println("    {");
        pw.println("      \"id\": \"npc_brightcoral_elder\",");
        pw.println("      \"name\": \"Coral Builder Elder\",");
        pw.println("      \"spriteName\": \"npcs/townsman\",");
        pw.println("      \"type\": \"TOWNSFOLK\",");
        pw.println("      \"defaultDialog\": \"The coral grows through us. We are becoming something new.\",");
        pw.println("      \"questCompleteDialog\": \"\",");
        pw.println("      \"shopItemIds\": [],");
        pw.println("      \"x\": 6,");
        pw.println("      \"y\": 7,");
        pw.println("      \"dialogueTree\": {");
        pw.println("        \"startNodeId\": \"greeting\",");
        pw.println("        \"nodes\": {");
        // greeting
        pw.println("          \"greeting\": {");
        pw.println("            \"id\": \"greeting\",");
        pw.println("            \"text\": \"Welcome to Brightcoral, traveler. Beautiful, isn\\u0027t it? The glow. The warmth. The coral embraces everything here \\u2014 the walls, the lamps... and us. Slowly. Gently. We are becoming part of the reef.\",");
        pw.println("            \"choices\": [");
        pw.println("              {\"label\": \"That sounds... alarming.\", \"nextNodeId\": \"alarming\", \"conditions\": []},");
        pw.println("              {\"label\": \"Tell me about the coral.\", \"nextNodeId\": \"coral_lore\", \"conditions\": []},");
        pw.println("              {\"label\": \"Is there a way to stop it?\", \"nextNodeId\": \"stop_it\", \"conditions\": []},");
        pw.println("              {\"label\": \"Farewell.\", \"nextNodeId\": \"farewell\", \"conditions\": []}");
        pw.println("            ],");
        pw.println("            \"actions\": []");
        pw.println("          },");
        // alarming
        pw.println("          \"alarming\": {");
        pw.println("            \"id\": \"alarming\",");
        pw.println("            \"text\": \"Is it? My fingers are coral now \\u2014 have been for years. They don\\u0027t hurt. They glow in the dark. I can feel the reef\\u0027s pulse through them. Is that alarming, or is it... communion?\",");
        pw.println("            \"choices\": [");
        pw.println("              {\"label\": \"That\\u0027s unsettling.\", \"nextNodeId\": \"farewell\", \"conditions\": []},");
        pw.println("              {\"label\": \"Tell me more about the coral.\", \"nextNodeId\": \"coral_lore\", \"conditions\": []}");
        pw.println("            ],");
        pw.println("            \"actions\": []");
        pw.println("          },");
        // coral_lore
        pw.println("          \"coral_lore\": {");
        pw.println("            \"id\": \"coral_lore\",");
        pw.println("            \"text\": \"The reef was here before us. It grew from the blood of the deep \\u2014 from whatever Thalorax crushed and remade. The coral is alive in ways we don\\u0027t understand. It doesn\\u0027t consume us. It... includes us. Our children are born with coral in their skin now. They think it\\u0027s natural.\",");
        pw.println("            \"choices\": [");
        pw.println("              {\"label\": \"The children...\", \"nextNodeId\": \"children\", \"conditions\": []},");
        pw.println("              {\"label\": \"I\\u0027ve heard enough.\", \"nextNodeId\": \"farewell\", \"conditions\": []}");
        pw.println("            ],");
        pw.println("            \"actions\": []");
        pw.println("          },");
        // children
        pw.println("          \"children\": {");
        pw.println("            \"id\": \"children\",");
        pw.println("            \"text\": \"They glow, traveler. In the dark, they glow. And they hear things in the reef we cannot. Perhaps they are the next step. Perhaps Brightcoral is not a village \\u2014 it\\u0027s a cocoon.\",");
        pw.println("            \"choices\": [");
        pw.println("              {\"label\": \"A cocoon for what?\", \"nextNodeId\": \"farewell\", \"conditions\": []}");
        pw.println("            ],");
        pw.println("            \"actions\": []");
        pw.println("          },");
        // stop_it
        pw.println("          \"stop_it\": {");
        pw.println("            \"id\": \"stop_it\",");
        pw.println("            \"text\": \"Stop it? Why would we stop it? Look around you. This is the most beautiful place in the Abyssal Depths. The coral protects us from the pressure, feeds us, lights our way. What it takes in return... well. We\\u0027ve made our peace.\",");
        pw.println("            \"choices\": [");
        pw.println("              {\"label\": \"I understand.\", \"nextNodeId\": \"farewell\", \"conditions\": []}");
        pw.println("            ],");
        pw.println("            \"actions\": []");
        pw.println("          },");
        // farewell
        pw.println("          \"farewell\": {");
        pw.println("            \"id\": \"farewell\",");
        pw.println("            \"text\": \"Stay in the glow, traveler. The dark between the coral... that\\u0027s where the real deep begins.\",");
        pw.println("            \"choices\": [],");
        pw.println("            \"actions\": [{\"type\": \"CLOSE_DIALOGUE\", \"target\": \"\", \"value\": \"\", \"amount\": 0}]");
        pw.println("          }");
        pw.println("        }");
        pw.println("      }");
        pw.println("    },");

        // NPC 2: Brightcoral Merchant
        pw.println("    {");
        pw.println("      \"id\": \"npc_brightcoral_merchant\",");
        pw.println("      \"name\": \"Coral Merchant\",");
        pw.println("      \"spriteName\": \"npcs/townsman\",");
        pw.println("      \"type\": \"SHOPKEEPER\",");
        pw.println("      \"defaultDialog\": \"The reef provides. Take what glows.\",");
        pw.println("      \"questCompleteDialog\": \"\",");
        pw.println("      \"shopItemIds\": [\"abyssal_healing_draught\", \"coral_shield\", \"healing_potion\", \"greater_healing_potion\"],");
        pw.println("      \"x\": 14,");
        pw.println("      \"y\": 7,");
        pw.println("      \"dialogueTree\": {");
        pw.println("        \"startNodeId\": \"greeting\",");
        pw.println("        \"nodes\": {");
        pw.println("          \"greeting\": {");
        pw.println("            \"id\": \"greeting\",");
        pw.println("            \"text\": \"Everything here is coral-grown. Shields, draughts, remedies \\u2014 the reef shapes them for us. We just harvest.\",");
        pw.println("            \"choices\": [");
        pw.println("              {\"label\": \"Show me what the reef offers.\", \"nextNodeId\": \"shop\", \"conditions\": []},");
        pw.println("              {\"label\": \"Not today.\", \"nextNodeId\": \"farewell\", \"conditions\": []}");
        pw.println("            ],");
        pw.println("            \"actions\": []");
        pw.println("          },");
        pw.println("          \"shop\": {");
        pw.println("            \"id\": \"shop\",");
        pw.println("            \"text\": \"Coral-forged. Pressure-hardened. The deep makes things strong.\",");
        pw.println("            \"choices\": [],");
        pw.println("            \"actions\": [{\"type\": \"OPEN_SHOP\", \"target\": \"\", \"value\": \"\", \"amount\": 0}]");
        pw.println("          },");
        pw.println("          \"farewell\": {");
        pw.println("            \"id\": \"farewell\",");
        pw.println("            \"text\": \"The glow guides. Follow it home.\",");
        pw.println("            \"choices\": [],");
        pw.println("            \"actions\": [{\"type\": \"CLOSE_DIALOGUE\", \"target\": \"\", \"value\": \"\", \"amount\": 0}]");
        pw.println("          }");
        pw.println("        }");
        pw.println("      }");
        pw.println("    },");

        // NPC 3: Coral-Child — unsettling innocence
        pw.println("    {");
        pw.println("      \"id\": \"npc_brightcoral_child\",");
        pw.println("      \"name\": \"Coral-Child\",");
        pw.println("      \"spriteName\": \"npcs/townsman\",");
        pw.println("      \"type\": \"TOWNSFOLK\",");
        pw.println("      \"defaultDialog\": \"The reef sings to me. Can you hear it?\",");
        pw.println("      \"questCompleteDialog\": \"\",");
        pw.println("      \"shopItemIds\": [],");
        pw.println("      \"x\": 10,");
        pw.println("      \"y\": 15,");
        pw.println("      \"dialogueTree\": {");
        pw.println("        \"startNodeId\": \"greeting\",");
        pw.println("        \"nodes\": {");
        // greeting
        pw.println("          \"greeting\": {");
        pw.println("            \"id\": \"greeting\",");
        pw.println("            \"text\": \"Hello! Your skin is so soft. Doesn\\u0027t it hurt without coral? The reef says you\\u0027re from the dry place. The place with no pressure. That sounds scary.\",");
        pw.println("            \"choices\": [");
        pw.println("              {\"label\": \"The reef talks to you?\", \"nextNodeId\": \"reef_talks\", \"conditions\": []},");
        pw.println("              {\"label\": \"Don\\u0027t you want to see the surface?\", \"nextNodeId\": \"surface\", \"conditions\": []},");
        pw.println("              {\"label\": \"Goodbye, little one.\", \"nextNodeId\": \"farewell\", \"conditions\": []}");
        pw.println("            ],");
        pw.println("            \"actions\": []");
        pw.println("          },");
        // reef_talks
        pw.println("          \"reef_talks\": {");
        pw.println("            \"id\": \"reef_talks\",");
        pw.println("            \"text\": \"Always! It hums in my bones. The coral in my fingers tells me where the fish are, when the pressure changes, when something big swims overhead. It told me you were coming. It says you carry something heavy inside you \\u2014 heavier than the ocean.\",");
        pw.println("            \"choices\": [");
        pw.println("              {\"label\": \"That\\u0027s... perceptive.\", \"nextNodeId\": \"farewell\", \"conditions\": []},");
        pw.println("              {\"label\": \"What else does the reef say?\", \"nextNodeId\": \"reef_more\", \"conditions\": []}");
        pw.println("            ],");
        pw.println("            \"actions\": []");
        pw.println("          },");
        // reef_more
        pw.println("          \"reef_more\": {");
        pw.println("            \"id\": \"reef_more\",");
        pw.println("            \"text\": \"It says the Crushing Deep is watching you. Not angry-watching. Curious-watching. Like when I find a new shell. It wants to see what you\\u0027ll do when the pressure gets too heavy. Everyone breaks eventually. The reef just wants to know how.\",");
        pw.println("            \"choices\": [");
        pw.println("              {\"label\": \"...\", \"nextNodeId\": \"farewell\", \"conditions\": []}");
        pw.println("            ],");
        pw.println("            \"actions\": []");
        pw.println("          },");
        // surface
        pw.println("          \"surface\": {");
        pw.println("            \"id\": \"surface\",");
        pw.println("            \"text\": \"The dry place? No! The reef says there\\u0027s nothing to hold you up there. No pressure, no glow, no singing. How do you know where you are if the water doesn\\u0027t press against you? That sounds like floating forever with nothing touching you. That sounds like being alone.\",");
        pw.println("            \"choices\": [");
        pw.println("              {\"label\": \"I suppose it does.\", \"nextNodeId\": \"farewell\", \"conditions\": []}");
        pw.println("            ],");
        pw.println("            \"actions\": []");
        pw.println("          },");
        // farewell
        pw.println("          \"farewell\": {");
        pw.println("            \"id\": \"farewell\",");
        pw.println("            \"text\": \"Bye-bye, soft one! The reef will remember you. It remembers everything.\",");
        pw.println("            \"choices\": [],");
        pw.println("            \"actions\": [{\"type\": \"CLOSE_DIALOGUE\", \"target\": \"\", \"value\": \"\", \"amount\": 0}]");
        pw.println("          }");
        pw.println("        }");
        pw.println("      }");
        pw.println("    }");

        pw.println("  ],");
        pw.println("  \"townEntrances\": [],");
        pw.println("  \"overworldTeleporters\": []");
        pw.println("}");
        pw.close();
        System.out.println("  wrote: " + path);
    }
}

import java.io.*;

/**
 * Generates data/towns/amber_grove.rfmap — a 20x20 settlement in a
 * petrified forest containing pre-Shattering memory crystals.
 *
 * Run from project root:
 *   javac tools/AmberGroveGen.java -d tools/
 *   java -cp tools AmberGroveGen
 */
public class AmberGroveGen {

    static final int COLS = 20, ROWS = 20;
    static char[][] g = new char[ROWS][COLS];

    static final char RFLOOR = '\uE06A';
    static final char BWALL  = '\uE06B';
    static final char VINE   = '\uE06C';
    static final char ACRYST = '\uE06D';
    static final char FLAMP  = '\uE06E';
    static final char MOSS   = '\uE063';
    static final char AMBER  = '\uE067';
    static final char WALL   = 'W';
    static final char DOOR   = 'd';
    static final char COUNTER= 'c';
    static final char BARREL = 'n';
    static final char SHELF  = 'k';
    static final char TABLE  = 'i';
    static final char CHAIR  = 'j';
    static final char TORCH  = 'v';
    static final char ALTAR  = 'x';

    public static void main(String[] args) throws Exception {

        // ── 1. Base: moss ground with amber formations scattered ──────────
        fill(0, 19, 0, 19, MOSS);

        // Petrified amber formations (impassable, decorative)
        g[0][3] = AMBER; g[0][4] = AMBER; g[0][16] = AMBER;
        g[1][0] = AMBER; g[1][17] = AMBER; g[1][18] = AMBER;
        g[3][0] = AMBER; g[3][19] = AMBER;
        g[7][0] = AMBER; g[7][19] = AMBER;
        g[12][0] = AMBER; g[16][0] = AMBER;
        g[16][19] = AMBER; g[19][0] = AMBER; g[19][19] = AMBER;

        // ── 2. Central archive building (the Amber Archive) ──────────────
        // Rows 3-11, cols 5-14
        hwall(3, 5, 14); hwall(11, 5, 14);
        vwall(5, 3, 11); vwall(14, 3, 11);
        fill(4, 10, 6, 13, RFLOOR);
        g[11][9] = DOOR; g[11][10] = DOOR;  // south entrance

        // Memory altar at center
        g[6][9] = ALTAR; g[6][10] = ALTAR;

        // Amber crystal displays (memory holders)
        g[4][7] = ACRYST; g[4][12] = ACRYST;
        g[8][7] = ACRYST; g[8][12] = ACRYST;

        // Bookshelves (records)
        g[4][6] = SHELF; g[5][6] = SHELF; g[6][6] = SHELF;
        g[4][13] = SHELF; g[5][13] = SHELF; g[6][13] = SHELF;

        // Reading tables
        g[9][8] = TABLE; g[9][9] = CHAIR;
        g[9][11] = TABLE; g[9][10] = CHAIR;

        // Torches
        g[4][8] = TORCH; g[4][11] = TORCH;
        g[10][6] = TORCH; g[10][13] = TORCH;

        // ── 3. Small lodge (south) ────────────────────────────────────────
        hwall(14, 2, 8); hwall(18, 2, 8);
        vwall(2, 14, 18); vwall(8, 14, 18);
        fill(15, 17, 3, 7, RFLOOR);
        g[14][5] = DOOR;

        // Fungal lamp lighting
        g[15][3] = FLAMP; g[17][7] = FLAMP;
        g[12][9] = FLAMP; g[12][10] = FLAMP;
        g[2][9] = FLAMP;

        // ── 4. Entry from south ───────────────────────────────────────────
        // (player enters from the overworld at bottom-center)

        // ── Write JSON ────────────────────────────────────────────────────
        writeJSON("data/towns/amber_grove.rfmap");
        System.out.println("Done — data/towns/amber_grove.rfmap written.");
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
        pw.println("  \"name\": \"amber_grove\",");
        pw.println("  \"width\": 20,");
        pw.println("  \"height\": 20,");
        pw.println("  \"interiorEntryX\": 9,");
        pw.println("  \"interiorEntryY\": 18,");
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

        // NPC 1: The Amber Sage — key quest-giver
        pw.println("    {");
        pw.println("      \"id\": \"npc_amber_grove_sage\",");
        pw.println("      \"name\": \"The Amber Sage\",");
        pw.println("      \"spriteName\": \"npcs/townsman\",");
        pw.println("      \"type\": \"QUESTGIVER\",");
        pw.println("      \"defaultDialog\": \"The amber remembers what we have forgotten.\",");
        pw.println("      \"questCompleteDialog\": \"\",");
        pw.println("      \"shopItemIds\": [],");
        pw.println("      \"x\": 9,");
        pw.println("      \"y\": 5,");
        pw.println("      \"dialogueTree\": {");
        pw.println("        \"startNodeId\": \"greeting\",");
        pw.println("        \"nodes\": {");

        // greeting
        pw.println("          \"greeting\": {");
        pw.println("            \"id\": \"greeting\",");
        pw.println("            \"text\": \"[An ancient woman sits cross-legged before the altar, eyes the color of amber. The air hums with preserved memories.] You seek the Key of Roots. I can see it in your bearing.\",");
        pw.println("            \"choices\": [");
        pw.println("              {\"label\": \"How do you know that?\", \"nextNodeId\": \"how\", \"conditions\": []},");
        pw.println("              {\"label\": \"Yes. How do I earn it?\", \"nextNodeId\": \"trial\", \"conditions\": []},");
        pw.println("              {\"label\": \"Tell me about the memory crystals.\", \"nextNodeId\": \"crystals\", \"conditions\": []},");
        pw.println("              {\"label\": \"I\\u0027ve collected all four memories.\", \"nextNodeId\": \"trial_done\", \"conditions\": [");
        pw.println("                {\"type\": \"QUEST_COMPLETE\", \"target\": \"amber_sage_memory\", \"value\": \"\", \"amount\": 0}");
        pw.println("              ]},");
        pw.println("              {\"label\": \"I\\u0027ve proven my understanding of the cycle.\", \"nextNodeId\": \"verdant_done\", \"conditions\": [");
        pw.println("                {\"type\": \"QUEST_COMPLETE\", \"target\": \"sylvandar_verdant_trial\", \"value\": \"\", \"amount\": 0}");
        pw.println("              ]},");
        pw.println("              {\"label\": \"Farewell.\", \"nextNodeId\": \"farewell\", \"conditions\": []}");
        pw.println("            ],");
        pw.println("            \"actions\": []");
        pw.println("          },");

        // how
        pw.println("          \"how\": {");
        pw.println("            \"id\": \"how\",");
        pw.println("            \"text\": \"The amber shows me things \\u2014 past, present, paths not yet walked. Every Unbound who reaches these shores seeks a Key. The question is whether you understand what it costs.\",");
        pw.println("            \"choices\": [");
        pw.println("              {\"label\": \"What does it cost?\", \"nextNodeId\": \"trial\", \"conditions\": []},");
        pw.println("              {\"label\": \"I see.\", \"nextNodeId\": \"farewell\", \"conditions\": []}");
        pw.println("            ],");
        pw.println("            \"actions\": []");
        pw.println("          },");

        // trial
        pw.println("          \"trial\": {");
        pw.println("            \"id\": \"trial\",");
        pw.println("            \"text\": \"Two tasks. First: collect the four memory crystals scattered in this grove. They hold fragments of the world before the Shattering. Learn what was lost. Then return to me for the Verdant Trial.\",");
        pw.println("            \"choices\": [");
        pw.println("              {\"label\": \"I\\u0027ll find the memory crystals.\", \"nextNodeId\": \"memory_accept\", \"conditions\": [{\"type\": \"FLAG_NOT_SET\", \"target\": \"amber_memory_quest\", \"value\": \"\", \"amount\": 0}]},");
        pw.println("              {\"label\": \"I need time to prepare.\", \"nextNodeId\": \"farewell\", \"conditions\": []}");
        pw.println("            ],");
        pw.println("            \"actions\": []");
        pw.println("          },");

        // memory_accept
        pw.println("          \"memory_accept\": {");
        pw.println("            \"id\": \"memory_accept\",");
        pw.println("            \"text\": \"The crystals glow faintly \\u2014 you will feel them when you are near. Each one holds a truth. Do not look away from what they show you.\",");
        pw.println("            \"choices\": [");
        pw.println("              {\"label\": \"I will find them all.\"}");
        pw.println("            ],");
        pw.println("            \"actions\": [");
        pw.println("              {\"type\": \"GIVE_QUEST\", \"target\": \"amber_sage_memory\", \"value\": \"\", \"amount\": 0},");
        pw.println("              {\"type\": \"SET_FLAG\", \"target\": \"amber_memory_quest\", \"value\": \"true\", \"amount\": 0}");
        pw.println("            ]");
        pw.println("          },");

        // trial_done
        pw.println("          \"trial_done\": {");
        pw.println("            \"id\": \"trial_done\",");
        pw.println("            \"text\": \"Good. You have seen what was. Now you must prove you understand the cycle. Slay the corrupted growth \\u2014 the Blight Mothers that feed the overgrowth. This is the Verdant Trial.\",");
        pw.println("            \"choices\": [");
        pw.println("              {\"label\": \"I accept the Verdant Trial.\", \"nextNodeId\": \"verdant_accept\", \"conditions\": [{\"type\": \"FLAG_NOT_SET\", \"target\": \"verdant_trial_active\", \"value\": \"\", \"amount\": 0}]},");
        pw.println("              {\"label\": \"I need to prepare first.\", \"nextNodeId\": \"farewell\", \"conditions\": []}");
        pw.println("            ],");
        pw.println("            \"actions\": []");
        pw.println("          },");

        // verdant_accept
        pw.println("          \"verdant_accept\": {");
        pw.println("            \"id\": \"verdant_accept\",");
        pw.println("            \"text\": \"The Blight Mothers are Sylvandar\\u0027s grief given form. Destroying them will not anger him \\u2014 it will remind him that pruning is an act of love. Go.\",");
        pw.println("            \"choices\": [");
        pw.println("              {\"label\": \"I will return victorious.\"}");
        pw.println("            ],");
        pw.println("            \"actions\": [");
        pw.println("              {\"type\": \"GIVE_QUEST\", \"target\": \"sylvandar_verdant_trial\", \"value\": \"\", \"amount\": 0},");
        pw.println("              {\"type\": \"SET_FLAG\", \"target\": \"verdant_trial_active\", \"value\": \"true\", \"amount\": 0}");
        pw.println("            ]");
        pw.println("          },");

        // verdant_done — gives Key of Roots
        pw.println("          \"verdant_done\": {");
        pw.println("            \"id\": \"verdant_done\",");
        pw.println("            \"text\": \"You have pruned the corruption and restored balance. The cycle turns again. Sylvandar weeps \\u2014 not from anger, but from remembrance. Take this. The Key of Roots has grown for you.\",");
        pw.println("            \"choices\": [],");
        pw.println("            \"actions\": [");
        pw.println("              {\"type\": \"GIVE_ITEM\", \"target\": \"key_of_roots\", \"value\": \"\", \"amount\": 1},");
        pw.println("              {\"type\": \"SET_FLAG\", \"target\": \"sylvandar_trial_done\", \"value\": \"true\", \"amount\": 0},");
        pw.println("              {\"type\": \"LOG_MESSAGE\", \"target\": \"The Amber Sage presents you with the Key of Roots.\", \"value\": \"GOOD\", \"amount\": 0},");
        pw.println("              {\"type\": \"CLOSE_DIALOGUE\", \"target\": \"\", \"value\": \"\", \"amount\": 0}");
        pw.println("            ]");
        pw.println("          },");

        // crystals
        pw.println("          \"crystals\": {");
        pw.println("            \"id\": \"crystals\",");
        pw.println("            \"text\": \"Before the Shattering, this forest was the heart of the world. The amber preserved fragments of that time \\u2014 memories frozen in golden stone. Each crystal holds a truth about what Aqualon was, and what we lost.\",");
        pw.println("            \"choices\": [");
        pw.println("              {\"label\": \"How do I earn the Key?\", \"nextNodeId\": \"trial\", \"conditions\": []},");
        pw.println("              {\"label\": \"Fascinating.\", \"nextNodeId\": \"farewell\", \"conditions\": []}");
        pw.println("            ],");
        pw.println("            \"actions\": []");
        pw.println("          },");

        // farewell
        pw.println("          \"farewell\": {");
        pw.println("            \"id\": \"farewell\",");
        pw.println("            \"text\": \"The amber is patient. It has waited an age. It can wait a little longer.\",");
        pw.println("            \"choices\": [],");
        pw.println("            \"actions\": [{\"type\": \"CLOSE_DIALOGUE\", \"target\": \"\", \"value\": \"\", \"amount\": 0}]");
        pw.println("          }");

        pw.println("        }");
        pw.println("      }");
        pw.println("    },");

        // NPC 2: Archivist
        pw.println("    {");
        pw.println("      \"id\": \"npc_amber_grove_archivist\",");
        pw.println("      \"name\": \"Archivist Kael\",");
        pw.println("      \"spriteName\": \"npcs/townsman\",");
        pw.println("      \"type\": \"TOWNSFOLK\",");
        pw.println("      \"defaultDialog\": \"Every crystal here holds a lifetime. Touch one, and you\\u0027ll see what the world was before it broke.\",");
        pw.println("      \"questCompleteDialog\": \"\",");
        pw.println("      \"shopItemIds\": [],");
        pw.println("      \"x\": 7,");
        pw.println("      \"y\": 9");
        pw.println("    }");

        pw.println("  ],");
        pw.println("  \"townEntrances\": [],");
        pw.println("  \"overworldTeleporters\": []");
        pw.println("}");
        pw.close();
        System.out.println("  wrote: " + path);
    }
}

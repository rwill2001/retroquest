import java.io.*;

/**
 * Generates data/towns/abyssport.rfmap — a 30x30 domed underwater city.
 * Main hub of Thalorax (Island 5). Pressure-ward walls form the dome outline.
 *
 * Run from project root:
 *   javac -encoding UTF-8 tools/AbyssportGen.java -d tools/
 *   java -cp tools AbyssportGen
 */
public class AbyssportGen {

    static final int COLS = 30, ROWS = 30;
    static char[][] g = new char[ROWS][COLS];

    // Island 5 tile IDs
    static final char DOCEAN = '\uE070';  // deep ocean floor (exterior)
    static final char DFLOOR = '\uE07A';  // dome floor
    static final char CWALL  = '\uE07B';  // coral wall
    static final char KPLAT  = '\uE07C';  // kelp platform
    static final char PGLASS = '\uE07D';  // pressure glass
    static final char BLAMP  = '\uE07E';  // bioluminescent lamp
    static final char PWARD  = '\uE077';  // pressure ward (dome outline)

    // Standard tile IDs
    static final char DOOR    = 'd';
    static final char COUNTER = 'c';
    static final char BARREL  = 'n';
    static final char SHELF   = 'k';
    static final char BED     = 'l';
    static final char TABLE   = 'i';
    static final char CHAIR   = 'j';
    static final char FIRE    = 'u';
    static final char TORCH   = 'v';
    static final char ALTAR   = 'x';

    public static void main(String[] args) throws Exception {

        // ── 1. Exterior: deep ocean everywhere ──────────────────────────
        fill(0, 29, 0, 29, DOCEAN);

        // ── 2. Dome outline — pressure ward ring ────────────────────────
        // Roughly oval dome centered at (15,15), radius ~13
        for (int y = 0; y < ROWS; y++) {
            for (int x = 0; x < COLS; x++) {
                double dx = (x - 15.0) / 13.5;
                double dy = (y - 15.0) / 13.0;
                double dist = Math.sqrt(dx * dx + dy * dy);
                if (dist < 0.85) {
                    g[y][x] = DFLOOR;
                }
                if (dist >= 0.82 && dist < 0.92) {
                    g[y][x] = PWARD;
                }
            }
        }

        // ── 3. Pressure glass viewports in dome wall ────────────────────
        g[3][15] = PGLASS; g[3][14] = PGLASS;
        g[15][3] = PGLASS; g[15][27] = PGLASS;
        g[8][5] = PGLASS;  g[8][25] = PGLASS;
        g[22][5] = PGLASS; g[22][25] = PGLASS;

        // ── 4. Entry: south opening ─────────────────────────────────────
        g[27][14] = DOOR; g[27][15] = DOOR;
        g[26][14] = DFLOOR; g[26][15] = DFLOOR;

        // ── 5. Central plaza — Thalorax altar ───────────────────────────
        // Open area with altar at center
        g[14][14] = ALTAR; g[14][15] = ALTAR;
        g[14][12] = BLAMP; g[14][17] = BLAMP;
        g[12][14] = BLAMP; g[12][15] = BLAMP;
        g[16][14] = BLAMP; g[16][15] = BLAMP;

        // ── 6. Bioluminescent lamps throughout dome ─────────────────────
        g[7][10] = BLAMP;  g[7][19] = BLAMP;
        g[10][7] = BLAMP;  g[10][22] = BLAMP;
        g[19][7] = BLAMP;  g[19][22] = BLAMP;
        g[22][10] = BLAMP; g[22][19] = BLAMP;

        // ── 7. Drowned Engineer's Workshop (northwest) ──────────────────
        hwall(8, 5, 13); vwall(13, 5, 8);
        g[8][10] = DOOR;
        // Interior
        g[5][5] = TABLE; g[5][6] = TABLE; g[5][7] = TABLE;
        g[6][5] = BARREL; g[6][6] = BARREL;
        g[7][5] = SHELF; g[7][6] = SHELF;
        g[5][10] = COUNTER; g[5][11] = COUNTER;
        g[7][10] = BLAMP;

        // ── 8. Inn (northeast) ──────────────────────────────────────────
        hwall(8, 17, 25); vwall(17, 5, 8);
        g[8][20] = DOOR;
        // Interior
        g[5][18] = BED; g[5][19] = BED;
        g[6][18] = BED; g[6][19] = BED;
        g[5][22] = FIRE;
        g[7][18] = TABLE; g[7][19] = CHAIR; g[7][20] = CHAIR;
        g[5][24] = BLAMP;

        // ── 9. Shop (southwest) ─────────────────────────────────────────
        hwall(20, 5, 13); vwall(13, 20, 24);
        g[20][10] = DOOR;
        g[21][5] = COUNTER; g[21][6] = COUNTER; g[21][7] = COUNTER;
        g[22][5] = SHELF; g[22][6] = SHELF;
        g[23][5] = BARREL; g[23][6] = BARREL;
        g[21][10] = BLAMP;

        // ── 10. Varsa's Alcove (southeast) ──────────────────────────────
        hwall(20, 17, 25); vwall(17, 20, 24);
        g[20][20] = DOOR;
        g[21][18] = KPLAT; g[21][19] = KPLAT;
        g[22][18] = ALTAR;
        g[21][22] = SHELF; g[21][23] = SHELF;
        g[23][22] = BLAMP;

        // ── 11. Coral wall accents ──────────────────────────────────────
        // Decorative coral pillars in plaza
        g[11][11] = CWALL; g[11][18] = CWALL;
        g[18][11] = CWALL; g[18][18] = CWALL;

        // ── Write JSON ──────────────────────────────────────────────────
        writeJSON("data/towns/abyssport.rfmap");
        System.out.println("Done \u2014 data/towns/abyssport.rfmap written.");
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
        pw.println("  \"name\": \"abyssport\",");
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

        // NPC 1: The Drowned Engineer — questgiver (Cracking Dome)
        pw.println("    {");
        pw.println("      \"id\": \"npc_abyssport_engineer\",");
        pw.println("      \"name\": \"The Drowned Engineer\",");
        pw.println("      \"spriteName\": \"npcs/townsman\",");
        pw.println("      \"type\": \"QUESTGIVER\",");
        pw.println("      \"defaultDialog\": \"The dome is cracking. We need those ward fragments before the pressure kills us all.\",");
        pw.println("      \"questCompleteDialog\": \"\",");
        pw.println("      \"shopItemIds\": [],");
        pw.println("      \"x\": 9,");
        pw.println("      \"y\": 6,");
        pw.println("      \"dialogueTree\": {");
        pw.println("        \"startNodeId\": \"greeting\",");
        pw.println("        \"nodes\": {");
        // greeting
        pw.println("          \"greeting\": {");
        pw.println("            \"id\": \"greeting\",");
        pw.println("            \"text\": \"You\\u2014you\\u0027re a surface-walker? Down here? Listen, I don\\u0027t care where you came from. The dome is failing. Pressure\\u0027s building and I can\\u0027t hold it alone.\",");
        pw.println("            \"choices\": [");
        pw.println("              {\"label\": \"What\\u0027s happening to the dome?\", \"nextNodeId\": \"dome_info\", \"conditions\": []},");
        pw.println("              {\"label\": \"I can help. What do you need?\", \"nextNodeId\": \"quest_offer\", \"conditions\": [{\"type\": \"FLAG_NOT_SET\", \"target\": \"cracking_dome_accepted\", \"value\": \"\", \"amount\": 0}]},");
        pw.println("              {\"label\": \"[Quest Active] I\\u0027m gathering the fragments.\", \"nextNodeId\": \"quest_progress\", \"conditions\": [{\"type\": \"QUEST_ACTIVE\", \"target\": \"cracking_dome\", \"value\": \"\", \"amount\": 0}]},");
        pw.println("              {\"label\": \"[Quest Complete] I have the ward fragments.\", \"nextNodeId\": \"quest_complete\", \"conditions\": [{\"type\": \"HAS_ITEM\", \"target\": \"pressure_ward_fragment\", \"value\": \"\", \"amount\": 3}]},");
        pw.println("              {\"label\": \"Farewell.\", \"nextNodeId\": \"farewell\", \"conditions\": []}");
        pw.println("            ],");
        pw.println("            \"actions\": []");
        pw.println("          },");
        // dome_info
        pw.println("          \"dome_info\": {");
        pw.println("            \"id\": \"dome_info\",");
        pw.println("            \"text\": \"The pressure wards that keep this dome intact are ancient \\u2014 older than anyone here. Three of them have fractured. Without replacements, the ocean will crush Abyssport flat. I\\u0027ve located fragments scattered in the deep trenches, but the creatures out there...\",");
        pw.println("            \"choices\": [");
        pw.println("              {\"label\": \"I\\u0027ll find the fragments.\", \"nextNodeId\": \"quest_offer\", \"conditions\": [{\"type\": \"FLAG_NOT_SET\", \"target\": \"cracking_dome_accepted\", \"value\": \"\", \"amount\": 0}]},");
        pw.println("              {\"label\": \"That sounds dire.\", \"nextNodeId\": \"farewell\", \"conditions\": []}");
        pw.println("            ],");
        pw.println("            \"actions\": []");
        pw.println("          },");
        // quest_offer
        pw.println("          \"quest_offer\": {");
        pw.println("            \"id\": \"quest_offer\",");
        pw.println("            \"text\": \"You\\u0027d do that? I need three Pressure Ward Fragments. They\\u0027re scattered in the deep trenches south and east of here. The pressure hulks guard them \\u2014 be careful.\",");
        pw.println("            \"choices\": [],");
        pw.println("            \"actions\": [");
        pw.println("              {\"type\": \"GIVE_QUEST\", \"target\": \"cracking_dome\", \"value\": \"\", \"amount\": 0},");
        pw.println("              {\"type\": \"SET_FLAG\", \"target\": \"cracking_dome_accepted\", \"value\": \"true\", \"amount\": 0},");
        pw.println("              {\"type\": \"LOG_MESSAGE\", \"target\": \"Quest accepted: The Cracking Dome\", \"value\": \"GOOD\", \"amount\": 0},");
        pw.println("              {\"type\": \"CLOSE_DIALOGUE\", \"target\": \"\", \"value\": \"\", \"amount\": 0}");
        pw.println("            ]");
        pw.println("          },");
        // quest_progress
        pw.println("          \"quest_progress\": {");
        pw.println("            \"id\": \"quest_progress\",");
        pw.println("            \"text\": \"The dome groans louder each day. Please hurry \\u2014 find those three ward fragments before it\\u0027s too late.\",");
        pw.println("            \"choices\": [");
        pw.println("              {\"label\": \"I\\u0027ll keep looking.\", \"nextNodeId\": \"farewell\", \"conditions\": []}");
        pw.println("            ],");
        pw.println("            \"actions\": []");
        pw.println("          },");
        // quest_complete
        pw.println("          \"quest_complete\": {");
        pw.println("            \"id\": \"quest_complete\",");
        pw.println("            \"text\": \"You found them! All three fragments \\u2014 I can feel the resonance already. Let me fuse them into the dome\\u0027s ward matrix... there. The pressure is stabilizing. You\\u0027ve saved Abyssport, surface-walker. Thalorax watches us all down here, but today, we endure.\",");
        pw.println("            \"choices\": [],");
        pw.println("            \"actions\": [");
        pw.println("              {\"type\": \"TAKE_ITEM\", \"target\": \"pressure_ward_fragment\", \"value\": \"\", \"amount\": 3},");
        pw.println("              {\"type\": \"GIVE_GOLD\", \"target\": \"\", \"value\": \"\", \"amount\": 60},");
        pw.println("              {\"type\": \"LOG_MESSAGE\", \"target\": \"The dome\\u0027s wards are restored. Abyssport is saved!\", \"value\": \"GOOD\", \"amount\": 0},");
        pw.println("              {\"type\": \"CLOSE_DIALOGUE\", \"target\": \"\", \"value\": \"\", \"amount\": 0}");
        pw.println("            ]");
        pw.println("          },");
        // farewell
        pw.println("          \"farewell\": {");
        pw.println("            \"id\": \"farewell\",");
        pw.println("            \"text\": \"The deep doesn\\u0027t forgive. Watch yourself out there.\",");
        pw.println("            \"choices\": [],");
        pw.println("            \"actions\": [{\"type\": \"CLOSE_DIALOGUE\", \"target\": \"\", \"value\": \"\", \"amount\": 0}]");
        pw.println("          }");
        pw.println("        }");
        pw.println("      }");
        pw.println("    },");

        // NPC 2: Tide-Priestess Varsa — questgiver (Varsa's Choice)
        pw.println("    {");
        pw.println("      \"id\": \"npc_abyssport_varsa\",");
        pw.println("      \"name\": \"Tide-Priestess Varsa\",");
        pw.println("      \"spriteName\": \"npcs/townsman\",");
        pw.println("      \"type\": \"QUESTGIVER\",");
        pw.println("      \"defaultDialog\": \"The tides speak of Lirandel even here, in Thalorax\\u0027s domain.\",");
        pw.println("      \"questCompleteDialog\": \"\",");
        pw.println("      \"shopItemIds\": [],");
        pw.println("      \"x\": 20,");
        pw.println("      \"y\": 22,");
        pw.println("      \"dialogueTree\": {");
        pw.println("        \"startNodeId\": \"greeting\",");
        pw.println("        \"nodes\": {");
        // greeting
        pw.println("          \"greeting\": {");
        pw.println("            \"id\": \"greeting\",");
        pw.println("            \"text\": \"Shh. Speak softly here \\u2014 the walls listen in the deep. I am Varsa, servant of Lirandel. I was sent to learn Thalorax\\u0027s secrets, but I\\u0027ve been here too long. The pressure changes you.\",");
        pw.println("            \"choices\": [");
        pw.println("              {\"label\": \"Why is Lirandel\\u0027s priestess in Thalorax\\u0027s domain?\", \"nextNodeId\": \"explain\", \"conditions\": []},");
        pw.println("              {\"label\": \"Can I help you?\", \"nextNodeId\": \"quest_offer\", \"conditions\": [{\"type\": \"FLAG_NOT_SET\", \"target\": \"varsa_choice_made\", \"value\": \"\", \"amount\": 0}]},");
        pw.println("              {\"label\": \"Farewell, priestess.\", \"nextNodeId\": \"farewell\", \"conditions\": []}");
        pw.println("            ],");
        pw.println("            \"actions\": []");
        pw.println("          },");
        // explain
        pw.println("          \"explain\": {");
        pw.println("            \"id\": \"explain\",");
        pw.println("            \"text\": \"Lirandel senses something stirring in the deep \\u2014 something that frightens even Thalorax. I came to investigate, but the crushing dark makes it hard to remember why. I need someone to remind me who I serve.\",");
        pw.println("            \"choices\": [");
        pw.println("              {\"label\": \"Let me help.\", \"nextNodeId\": \"quest_offer\", \"conditions\": [{\"type\": \"FLAG_NOT_SET\", \"target\": \"varsa_choice_made\", \"value\": \"\", \"amount\": 0}]},");
        pw.println("              {\"label\": \"I see.\", \"nextNodeId\": \"farewell\", \"conditions\": []}");
        pw.println("            ],");
        pw.println("            \"actions\": []");
        pw.println("          },");
        // quest_offer
        pw.println("          \"quest_offer\": {");
        pw.println("            \"id\": \"quest_offer\",");
        pw.println("            \"text\": \"There are two paths. You could extract me \\u2014 help me return to Lirandel\\u0027s light above. Or... you could send me deeper. Into the Pressure Temple itself. I might learn what Thalorax hides, but I may never return. The choice is yours, traveler.\",");
        pw.println("            \"choices\": [");
        pw.println("              {\"label\": \"Come with me. Lirandel needs you alive.\", \"nextNodeId\": \"extract\", \"conditions\": []},");
        pw.println("              {\"label\": \"Go deeper. We need to know what\\u0027s down there.\", \"nextNodeId\": \"send_deep\", \"conditions\": []}");
        pw.println("            ],");
        pw.println("            \"actions\": [");
        pw.println("              {\"type\": \"GIVE_QUEST\", \"target\": \"varsa_choice\", \"value\": \"\", \"amount\": 0}");
        pw.println("            ]");
        pw.println("          },");
        // extract
        pw.println("          \"extract\": {");
        pw.println("            \"id\": \"extract\",");
        pw.println("            \"text\": \"Thank you. The light above \\u2014 I\\u0027d almost forgotten what it looks like. Lirandel\\u0027s tides will carry me home. Take this as thanks.\",");
        pw.println("            \"choices\": [],");
        pw.println("            \"actions\": [");
        pw.println("              {\"type\": \"SET_FLAG\", \"target\": \"varsa_choice_made\", \"value\": \"extracted\", \"amount\": 0},");
        pw.println("              {\"type\": \"GIVE_GOLD\", \"target\": \"\", \"value\": \"\", \"amount\": 40},");
        pw.println("              {\"type\": \"LOG_MESSAGE\", \"target\": \"Varsa departs for the surface. Lirandel\\u0027s favor grows.\", \"value\": \"GOOD\", \"amount\": 0},");
        pw.println("              {\"type\": \"CLOSE_DIALOGUE\", \"target\": \"\", \"value\": \"\", \"amount\": 0}");
        pw.println("            ]");
        pw.println("          },");
        // send_deep
        pw.println("          \"send_deep\": {");
        pw.println("            \"id\": \"send_deep\",");
        pw.println("            \"text\": \"...You\\u0027re right. Someone has to know. The pressure will take me, but the truth matters more. Pray for me, traveler \\u2014 or don\\u0027t. The deep doesn\\u0027t care either way.\",");
        pw.println("            \"choices\": [],");
        pw.println("            \"actions\": [");
        pw.println("              {\"type\": \"SET_FLAG\", \"target\": \"varsa_choice_made\", \"value\": \"sent_deep\", \"amount\": 0},");
        pw.println("              {\"type\": \"GIVE_GOLD\", \"target\": \"\", \"value\": \"\", \"amount\": 40},");
        pw.println("              {\"type\": \"LOG_MESSAGE\", \"target\": \"Varsa descends into the Pressure Temple. Thalorax takes notice.\", \"value\": \"WARN\", \"amount\": 0},");
        pw.println("              {\"type\": \"CLOSE_DIALOGUE\", \"target\": \"\", \"value\": \"\", \"amount\": 0}");
        pw.println("            ]");
        pw.println("          },");
        // farewell
        pw.println("          \"farewell\": {");
        pw.println("            \"id\": \"farewell\",");
        pw.println("            \"text\": \"May the tides guide you \\u2014 even down here where they cannot reach.\",");
        pw.println("            \"choices\": [],");
        pw.println("            \"actions\": [{\"type\": \"CLOSE_DIALOGUE\", \"target\": \"\", \"value\": \"\", \"amount\": 0}]");
        pw.println("          }");
        pw.println("        }");
        pw.println("      }");
        pw.println("    },");

        // NPC 3: Abyssport Shopkeeper
        pw.println("    {");
        pw.println("      \"id\": \"npc_abyssport_shop\",");
        pw.println("      \"name\": \"Abyssport Trader\",");
        pw.println("      \"spriteName\": \"npcs/townsman\",");
        pw.println("      \"type\": \"SHOPKEEPER\",");
        pw.println("      \"defaultDialog\": \"Salvage from the deep. Everything has a price down here.\",");
        pw.println("      \"questCompleteDialog\": \"\",");
        pw.println("      \"shopItemIds\": [\"pressure_blade\", \"abyssal_plate\", \"coral_shield\", \"deep_trident\", \"abyssal_healing_draught\", \"healing_potion\", \"greater_healing_potion\"],");
        pw.println("      \"x\": 9,");
        pw.println("      \"y\": 22,");
        pw.println("      \"dialogueTree\": {");
        pw.println("        \"startNodeId\": \"greeting\",");
        pw.println("        \"nodes\": {");
        pw.println("          \"greeting\": {");
        pw.println("            \"id\": \"greeting\",");
        pw.println("            \"text\": \"Salvaged from wrecks, forged under pressure, pulled from the jaws of things best left unnamed. What\\u0027ll it be?\",");
        pw.println("            \"choices\": [");
        pw.println("              {\"label\": \"Show me what you have.\", \"nextNodeId\": \"shop\", \"conditions\": []},");
        pw.println("              {\"label\": \"Just passing through.\", \"nextNodeId\": \"farewell\", \"conditions\": []}");
        pw.println("            ],");
        pw.println("            \"actions\": []");
        pw.println("          },");
        pw.println("          \"shop\": {");
        pw.println("            \"id\": \"shop\",");
        pw.println("            \"text\": \"Take your pick. Everything\\u0027s pressure-tested.\",");
        pw.println("            \"choices\": [],");
        pw.println("            \"actions\": [{\"type\": \"OPEN_SHOP\", \"target\": \"\", \"value\": \"\", \"amount\": 0}]");
        pw.println("          },");
        pw.println("          \"farewell\": {");
        pw.println("            \"id\": \"farewell\",");
        pw.println("            \"text\": \"Don\\u0027t wander too far from the dome. The deep takes the careless.\",");
        pw.println("            \"choices\": [],");
        pw.println("            \"actions\": [{\"type\": \"CLOSE_DIALOGUE\", \"target\": \"\", \"value\": \"\", \"amount\": 0}]");
        pw.println("          }");
        pw.println("        }");
        pw.println("      }");
        pw.println("    },");

        // NPC 4: Abyssport Innkeeper
        pw.println("    {");
        pw.println("      \"id\": \"npc_abyssport_inn\",");
        pw.println("      \"name\": \"Dome-Keeper Rill\",");
        pw.println("      \"spriteName\": \"npcs/townsman\",");
        pw.println("      \"type\": \"INNKEEPER\",");
        pw.println("      \"defaultDialog\": \"Rest here. The dome holds \\u2014 for now.\",");
        pw.println("      \"questCompleteDialog\": \"\",");
        pw.println("      \"shopItemIds\": [],");
        pw.println("      \"x\": 20,");
        pw.println("      \"y\": 7,");
        pw.println("      \"dialogueTree\": {");
        pw.println("        \"startNodeId\": \"greeting\",");
        pw.println("        \"nodes\": {");
        pw.println("          \"greeting\": {");
        pw.println("            \"id\": \"greeting\",");
        pw.println("            \"text\": \"The dome holds the ocean back, and I hold the beds warm. Rest costs 25 gold \\u2014 cheap for survival.\",");
        pw.println("            \"choices\": [");
        pw.println("              {\"label\": \"I\\u0027ll rest. (Full heal, 25g)\", \"nextNodeId\": \"rest\", \"conditions\": [{\"type\": \"HAS_GOLD\", \"target\": \"\", \"value\": \"\", \"amount\": 25}]},");
        pw.println("              {\"label\": \"What\\u0027s it like living under the ocean?\", \"nextNodeId\": \"about\", \"conditions\": []},");
        pw.println("              {\"label\": \"Not now.\", \"nextNodeId\": \"farewell\", \"conditions\": []}");
        pw.println("            ],");
        pw.println("            \"actions\": []");
        pw.println("          },");
        pw.println("          \"rest\": {");
        pw.println("            \"id\": \"rest\",");
        pw.println("            \"text\": \"Sleep deep \\u2014 but not too deep. The ocean dreams are strange.\",");
        pw.println("            \"choices\": [],");
        pw.println("            \"actions\": [");
        pw.println("              {\"type\": \"GIVE_GOLD\", \"target\": \"\", \"value\": \"\", \"amount\": -25},");
        pw.println("              {\"type\": \"HEAL_PLAYER\", \"target\": \"\", \"value\": \"\", \"amount\": 0},");
        pw.println("              {\"type\": \"LOG_MESSAGE\", \"target\": \"You rest within the dome. The ocean\\u0027s hum lulls you to sleep.\", \"value\": \"GOOD\", \"amount\": 0},");
        pw.println("              {\"type\": \"CLOSE_DIALOGUE\", \"target\": \"\", \"value\": \"\", \"amount\": 0}");
        pw.println("            ]");
        pw.println("          },");
        pw.println("          \"about\": {");
        pw.println("            \"id\": \"about\",");
        pw.println("            \"text\": \"You get used to the pressure. The darkness. The sounds the dome makes when something large passes overhead. What you don\\u0027t get used to is the knowing \\u2014 that Thalorax watches everything down here. Every crack, every breath.\",");
        pw.println("            \"choices\": [");
        pw.println("              {\"label\": \"Unsettling.\", \"nextNodeId\": \"farewell\", \"conditions\": []}");
        pw.println("            ],");
        pw.println("            \"actions\": []");
        pw.println("          },");
        pw.println("          \"farewell\": {");
        pw.println("            \"id\": \"farewell\",");
        pw.println("            \"text\": \"Stay in the light. The bioluminescence keeps the worst things away.\",");
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

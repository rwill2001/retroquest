import java.io.*;

/**
 * Generates data/towns/neutral_ground.rfmap — a 20x20 small trading post
 * in the no-man's land of Island 7 (Bellorak / The Golden War Isles).
 * No inn, no shop. Sparse, tense atmosphere.
 *
 * Run from project root:
 *   javac -encoding UTF-8 tools/NeutralGroundGen.java -d tools/
 *   java -cp tools NeutralGroundGen
 */
public class NeutralGroundGen {

    static final int COLS = 20, ROWS = 20;
    static char[][] g = new char[ROWS][COLS];

    // Island 7 tile IDs
    static final char WFLOOR  = '\uE09A';  // war plank floor (walkable interior)
    static final char IWALL   = '\uE09B';  // iron wall (impassable)
    static final char WBANNER = '\uE09C';  // war banner (walkable)
    static final char WRACK   = '\uE09D';  // weapon rack (impassable)
    static final char ILAMP   = '\uE09E';  // iron lamp (walkable)

    // Standard tile IDs
    static final char DOOR    = 'd';
    static final char EXIT    = 'E';
    static final char BARREL  = 'n';
    static final char TABLE   = 'i';
    static final char CHAIR   = 'j';

    public static void main(String[] args) throws Exception {

        // ── 1. Exterior: war plank floor with iron wall perimeter ────────
        fill(0, 19, 0, 19, WFLOOR);
        hwall(0, 0, 19);   // top perimeter
        hwall(19, 0, 19);  // bottom perimeter
        vwall(0, 0, 19);   // left perimeter
        vwall(19, 0, 19);  // right perimeter

        // ── 2. Entry: south wall opening ─────────────────────────────────
        g[19][9] = EXIT; g[19][10] = EXIT;
        g[18][9] = WFLOOR; g[18][10] = WFLOOR;

        // ── 3. Sparse interior — broken furniture, sparse lamps ──────────
        // A few iron lamps for dim lighting
        g[1][1] = ILAMP;   g[1][18] = ILAMP;
        g[18][1] = ILAMP;  g[18][18] = ILAMP;
        g[9][9] = ILAMP;   g[9][10] = ILAMP;

        // ── 4. NW corner: Deserter Quinn's hiding spot ───────────────────
        // Partial walls forming a ruined shelter
        hwall(2, 2, 7);    // top wall
        hwall(7, 2, 7);    // bottom wall
        vwall(2, 2, 7);    // left wall
        vwall(7, 2, 7);    // right wall
        g[7][4] = DOOR;    // entrance on south wall
        // Interior: sparse, desperate
        fill(3, 6, 3, 6, WFLOOR);
        g[3][3] = BARREL;
        g[3][5] = BARREL;
        g[5][3] = TABLE; g[5][4] = CHAIR;
        g[4][6] = ILAMP;

        // ── 5. SE area: Grik's lean-to ──────────────────────────────────
        // Partial walls
        hwall(8, 13, 17);  // top wall
        hwall(12, 13, 17); // bottom wall
        vwall(13, 8, 12);  // left wall
        vwall(17, 8, 12);  // right wall
        g[12][15] = DOOR;  // entrance on south wall
        // Interior
        fill(9, 11, 14, 16, WFLOOR);
        g[9][14] = WRACK;
        g[9][16] = BARREL;
        g[10][14] = TABLE; g[10][15] = CHAIR;
        g[11][16] = ILAMP;

        // ── 6. Center: scattered debris ──────────────────────────────────
        g[10][5] = BARREL; g[11][7] = BARREL;
        g[14][6] = WRACK;  g[15][12] = WRACK;
        g[16][4] = BARREL; g[16][15] = BARREL;

        // ── 7. War banners (faded, torn) at entrance ─────────────────────
        g[17][8] = WBANNER; g[17][11] = WBANNER;

        // ── Write JSON ──────────────────────────────────────────────────
        writeJSON("data/towns/neutral_ground.rfmap");
        System.out.println("Done \u2014 data/towns/neutral_ground.rfmap written.");
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    static void fill(int r1, int r2, int c1, int c2, char tile) {
        for (int r = r1; r <= r2; r++)
            for (int c = c1; c <= c2; c++)
                g[r][c] = tile;
    }

    static void hwall(int row, int c1, int c2) {
        for (int c = c1; c <= c2; c++) g[row][c] = IWALL;
    }

    static void vwall(int col, int r1, int r2) {
        for (int r = r1; r <= r2; r++) g[r][col] = IWALL;
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
        pw.println("  \"name\": \"neutral_ground\",");
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

        // NPC 1: Deserter Quinn — questgiver (iron_ghost_truth)
        pw.println("    {");
        pw.println("      \"id\": \"npc_neutral_ground_quinn\",");
        pw.println("      \"name\": \"Deserter Quinn\",");
        pw.println("      \"spriteName\": \"npcs/townsman\",");
        pw.println("      \"type\": \"QUESTGIVER\",");
        pw.println("      \"defaultDialog\": \"Keep your voice down. They\\u0027ll hear you.\",");
        pw.println("      \"questCompleteDialog\": \"\",");
        pw.println("      \"shopItemIds\": [],");
        pw.println("      \"x\": 10,");
        pw.println("      \"y\": 5,");
        pw.println("      \"dialogueTree\": {");
        pw.println("        \"startNodeId\": \"greeting\",");
        pw.println("        \"nodes\": {");
        // greeting
        pw.println("          \"greeting\": {");
        pw.println("            \"id\": \"greeting\",");
        pw.println("            \"text\": \"Keep your voice down. I\\u0027m Quinn. I deserted from the Gold Guard three months ago. They\\u0027d kill me if they found me. But I know something \\u2014 something about the arena that both sides need to hear.\",");
        pw.println("            \"choices\": [");
        pw.println("              {\"label\": \"What do you know about the arena?\", \"nextNodeId\": \"arena_truth\", \"conditions\": [{\"type\": \"FLAG_NOT_SET\", \"target\": \"iron_ghost_accepted\", \"value\": \"\", \"amount\": 0}]},");
        pw.println("              {\"label\": \"[Quest Active] I\\u0027m investigating the arena.\", \"nextNodeId\": \"quest_progress\", \"conditions\": [{\"type\": \"QUEST_ACTIVE\", \"target\": \"iron_ghost_truth\", \"value\": \"\", \"amount\": 0}]},");
        pw.println("              {\"label\": \"[Quest Complete] I\\u0027ve seen the truth.\", \"nextNodeId\": \"quest_complete\", \"conditions\": [{\"type\": \"QUEST_COMPLETE\", \"target\": \"iron_ghost_truth\", \"value\": \"\", \"amount\": 0}]},");
        pw.println("              {\"label\": \"Why did you desert?\", \"nextNodeId\": \"desertion\", \"conditions\": []},");
        pw.println("              {\"label\": \"I should go.\", \"nextNodeId\": \"farewell\", \"conditions\": []}");
        pw.println("            ],");
        pw.println("            \"actions\": []");
        pw.println("          },");
        // arena_truth
        pw.println("          \"arena_truth\": {");
        pw.println("            \"id\": \"arena_truth\",");
        pw.println("            \"text\": \"The champion in Bellorak\\u0027s arena \\u2014 the one they call the Iron Ghost? That\\u0027s Seraphine. She came here thirty years ago... entered the arena... and never left. She\\u0027s still fighting. She can\\u0027t die, she can\\u0027t stop. Bellorak keeps her trapped in an endless cycle of combat. She\\u0027s the engine that drives this whole war.\",");
        pw.println("            \"choices\": [");
        pw.println("              {\"label\": \"Seraphine? From the old stories?\", \"nextNodeId\": \"seraphine_detail\", \"conditions\": []},");
        pw.println("              {\"label\": \"How do you know this?\", \"nextNodeId\": \"how_know\", \"conditions\": []}");
        pw.println("            ],");
        pw.println("            \"actions\": []");
        pw.println("          },");
        // seraphine_detail
        pw.println("          \"seraphine_detail\": {");
        pw.println("            \"id\": \"seraphine_detail\",");
        pw.println("            \"text\": \"The same. Hero of the old age. She came to challenge Bellorak, thinking she could end the cycle of violence. Instead, he trapped her. Her endless fighting generates the conflict that sustains him. Every soldier who dies in the Golden War feeds Bellorak\\u0027s power \\u2014 and Seraphine\\u0027s suffering fuels it all.\",");
        pw.println("            \"choices\": [");
        pw.println("              {\"label\": \"She needs to be freed.\", \"nextNodeId\": \"quest_offer\", \"conditions\": []},");
        pw.println("              {\"label\": \"This is too dangerous.\", \"nextNodeId\": \"farewell\", \"conditions\": []}");
        pw.println("            ],");
        pw.println("            \"actions\": []");
        pw.println("          },");
        // how_know
        pw.println("          \"how_know\": {");
        pw.println("            \"id\": \"how_know\",");
        pw.println("            \"text\": \"I was posted to guard the arena\\u0027s outer ring. I saw her through the bars \\u2014 fighting, dying, rising again. Over and over. The guards told me to forget what I saw. I couldn\\u0027t. So I ran.\",");
        pw.println("            \"choices\": [");
        pw.println("              {\"label\": \"Someone needs to stop this.\", \"nextNodeId\": \"quest_offer\", \"conditions\": []},");
        pw.println("              {\"label\": \"I understand why you ran.\", \"nextNodeId\": \"farewell\", \"conditions\": []}");
        pw.println("            ],");
        pw.println("            \"actions\": []");
        pw.println("          },");
        // quest_offer
        pw.println("          \"quest_offer\": {");
        pw.println("            \"id\": \"quest_offer\",");
        pw.println("            \"text\": \"If you\\u0027re brave enough \\u2014 or foolish enough \\u2014 enter the arena and find her. There\\u0027s a way to break Bellorak\\u0027s hold, but you\\u0027ll have to face the god himself. Bring proof of what\\u0027s happening. The truth is the only weapon that can end this war.\",");
        pw.println("            \"choices\": [");
        pw.println("              {\"label\": \"I\\u0027ll find the truth.\", \"nextNodeId\": \"quest_accept\", \"conditions\": []},");
        pw.println("              {\"label\": \"I need to think about this.\", \"nextNodeId\": \"farewell\", \"conditions\": []}");
        pw.println("            ],");
        pw.println("            \"actions\": []");
        pw.println("          },");
        // quest_accept
        pw.println("          \"quest_accept\": {");
        pw.println("            \"id\": \"quest_accept\",");
        pw.println("            \"text\": \"Then go. The arena is at the center of the island. Fight your way in, find Seraphine, and bring the truth back to both sides. Maybe then this war can finally end.\",");
        pw.println("            \"choices\": [],");
        pw.println("            \"actions\": [");
        pw.println("              {\"type\": \"GIVE_QUEST\", \"target\": \"iron_ghost_truth\", \"value\": \"\", \"amount\": 0},");
        pw.println("              {\"type\": \"SET_FLAG\", \"target\": \"iron_ghost_accepted\", \"value\": \"true\", \"amount\": 0},");
        pw.println("              {\"type\": \"SET_FLAG\", \"target\": \"seraphine_arena_known\", \"value\": \"true\", \"amount\": 0},");
        pw.println("              {\"type\": \"LOG_MESSAGE\", \"target\": \"Quest accepted: The Iron Ghost\\u0027s Truth\", \"value\": \"GOOD\", \"amount\": 0},");
        pw.println("              {\"type\": \"CLOSE_DIALOGUE\", \"target\": \"\", \"value\": \"\", \"amount\": 0}");
        pw.println("            ]");
        pw.println("          },");
        // quest_progress
        pw.println("          \"quest_progress\": {");
        pw.println("            \"id\": \"quest_progress\",");
        pw.println("            \"text\": \"Have you reached the arena? Seraphine is in there \\u2014 trapped, fighting endlessly. Find her. Free her. End this.\",");
        pw.println("            \"choices\": [");
        pw.println("              {\"label\": \"I\\u0027m working on it.\", \"nextNodeId\": \"farewell\", \"conditions\": []}");
        pw.println("            ],");
        pw.println("            \"actions\": []");
        pw.println("          },");
        // quest_complete
        pw.println("          \"quest_complete\": {");
        pw.println("            \"id\": \"quest_complete\",");
        pw.println("            \"text\": \"You did it? You actually... the war. Is it over? Can it be over? ...Thank you. I can stop hiding now. Maybe we all can.\",");
        pw.println("            \"choices\": [],");
        pw.println("            \"actions\": [");
        pw.println("              {\"type\": \"LOG_MESSAGE\", \"target\": \"Quinn weeps with relief. The truth is known.\", \"value\": \"GOOD\", \"amount\": 0},");
        pw.println("              {\"type\": \"CLOSE_DIALOGUE\", \"target\": \"\", \"value\": \"\", \"amount\": 0}");
        pw.println("            ]");
        pw.println("          },");
        // desertion
        pw.println("          \"desertion\": {");
        pw.println("            \"id\": \"desertion\",");
        pw.println("            \"text\": \"I saw something I wasn\\u0027t supposed to see. The arena \\u2014 Bellorak\\u0027s arena \\u2014 it\\u0027s not just a place for glory matches. It\\u0027s a prison. And the prisoner inside is the reason this war never ends.\",");
        pw.println("            \"choices\": [");
        pw.println("              {\"label\": \"Tell me more.\", \"nextNodeId\": \"arena_truth\", \"conditions\": [{\"type\": \"FLAG_NOT_SET\", \"target\": \"iron_ghost_accepted\", \"value\": \"\", \"amount\": 0}]},");
        pw.println("              {\"label\": \"I see.\", \"nextNodeId\": \"farewell\", \"conditions\": []}");
        pw.println("            ],");
        pw.println("            \"actions\": []");
        pw.println("          },");
        // farewell
        pw.println("          \"farewell\": {");
        pw.println("            \"id\": \"farewell\",");
        pw.println("            \"text\": \"Be careful. The no-man\\u0027s land is full of Bellorak\\u0027s war-beasts. And if you see Gold Guard patrols... you never saw me.\",");
        pw.println("            \"choices\": [],");
        pw.println("            \"actions\": [{\"type\": \"CLOSE_DIALOGUE\", \"target\": \"\", \"value\": \"\", \"amount\": 0}]");
        pw.println("          }");
        pw.println("        }");
        pw.println("      }");
        pw.println("    },");

        // NPC 2: Grik — lore NPC
        pw.println("    {");
        pw.println("      \"id\": \"npc_neutral_ground_grik\",");
        pw.println("      \"name\": \"Grik\",");
        pw.println("      \"spriteName\": \"npcs/townsman\",");
        pw.println("      \"type\": \"LORE\",");
        pw.println("      \"defaultDialog\": \"Grik knows iron. Grik knows war. Grik is tired of both.\",");
        pw.println("      \"questCompleteDialog\": \"\",");
        pw.println("      \"shopItemIds\": [],");
        pw.println("      \"x\": 14,");
        pw.println("      \"y\": 10,");
        pw.println("      \"dialogueTree\": {");
        pw.println("        \"startNodeId\": \"greeting\",");
        pw.println("        \"nodes\": {");
        // greeting
        pw.println("          \"greeting\": {");
        pw.println("            \"id\": \"greeting\",");
        pw.println("            \"text\": \"Heh. Another traveler. I am Grik \\u2014 you may have heard of me. Grounded Grik, they called me on the Storm Spires. I came here thinking a war island would need a good smith. I was right. But even my forge can\\u0027t keep up with the demand for weapons.\",");
        pw.println("            \"choices\": [");
        pw.println("              {\"label\": \"You\\u0027re from the Storm Spires?\", \"nextNodeId\": \"origin\", \"conditions\": []},");
        pw.println("              {\"label\": \"Tell me about the war.\", \"nextNodeId\": \"war_lore\", \"conditions\": []},");
        pw.println("              {\"label\": \"Farewell, Grik.\", \"nextNodeId\": \"farewell\", \"conditions\": []}");
        pw.println("            ],");
        pw.println("            \"actions\": []");
        pw.println("          },");
        // origin
        pw.println("          \"origin\": {");
        pw.println("            \"id\": \"origin\",");
        pw.println("            \"text\": \"Aye. Zephyrion\\u0027s winds blew me here. I thought war meant opportunity \\u2014 soldiers always need blades. But this war... it consumes everything. Iron, steel, lives. Nothing lasts. I forge a hundred blades, and a hundred blades break. It\\u0027s like the island eats what I make.\",");
        pw.println("            \"choices\": [");
        pw.println("              {\"label\": \"Why stay?\", \"nextNodeId\": \"why_stay\", \"conditions\": []},");
        pw.println("              {\"label\": \"I see.\", \"nextNodeId\": \"farewell\", \"conditions\": []}");
        pw.println("            ],");
        pw.println("            \"actions\": []");
        pw.println("          },");
        // why_stay
        pw.println("          \"why_stay\": {");
        pw.println("            \"id\": \"why_stay\",");
        pw.println("            \"text\": \"Where else would Grik go? The Neutral Ground is the only place on this island where nobody\\u0027s trying to kill anybody. Both sides leave this place alone \\u2014 some old agreement. Even Bellorak honors it. Makes you wonder why a war god would allow a place of peace to exist.\",");
        pw.println("            \"choices\": [");
        pw.println("              {\"label\": \"Maybe it serves his purpose.\", \"nextNodeId\": \"farewell\", \"conditions\": []}");
        pw.println("            ],");
        pw.println("            \"actions\": []");
        pw.println("          },");
        // war_lore
        pw.println("          \"war_lore\": {");
        pw.println("            \"id\": \"war_lore\",");
        pw.println("            \"text\": \"The Golden War is a meat grinder with no end. Gold Guard on one side, Iron Reckoners on the other. They fight, they die, new recruits appear from nowhere to replace them. Grik has seen wars. This one is different. It\\u0027s too... organized. Too balanced. Like someone\\u0027s keeping score.\",");
        pw.println("            \"choices\": [");
        pw.println("              {\"label\": \"Bellorak\\u0027s doing?\", \"nextNodeId\": \"bellorak\", \"conditions\": []},");
        pw.println("              {\"label\": \"Interesting.\", \"nextNodeId\": \"farewell\", \"conditions\": []}");
        pw.println("            ],");
        pw.println("            \"actions\": []");
        pw.println("          },");
        // bellorak
        pw.println("          \"bellorak\": {");
        pw.println("            \"id\": \"bellorak\",");
        pw.println("            \"text\": \"Grik thinks so. The god of war and glory sits in his arena at the center of the island and watches. He doesn\\u0027t intervene. He doesn\\u0027t pick a side. He just... watches. And the war goes on. Grik\\u0027s forge burns day and night, and it\\u0027s never enough. That\\u0027s not war. That\\u0027s farming.\",");
        pw.println("            \"choices\": [");
        pw.println("              {\"label\": \"A grim thought.\", \"nextNodeId\": \"farewell\", \"conditions\": []}");
        pw.println("            ],");
        pw.println("            \"actions\": []");
        pw.println("          },");
        // farewell
        pw.println("          \"farewell\": {");
        pw.println("            \"id\": \"farewell\",");
        pw.println("            \"text\": \"Grik knows iron. Grik knows war. Grik is tired of both. Safe travels, friend.\",");
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

import java.io.*;

/**
 * Generates data/towns/echo_point.rfmap — a 20x20 sacred threshold town
 * for Island 6 (Umbryn / The Archive of Echoes). Echo Point is the last
 * settlement before the Archive dungeon.
 *
 * Run from project root:
 *   javac -encoding UTF-8 tools/EchoPointGen.java -d tools/
 *   java -cp tools EchoPointGen
 */
public class EchoPointGen {

    static final int COLS = 20, ROWS = 20;
    static char[][] g = new char[ROWS][COLS];

    // Island 6 tile IDs
    static final char SFLOOR  = '\uE080';  // shadow trench floor (exterior)
    static final char DFLOOR  = '\uE08A';  // dust floor
    static final char MCWALL  = '\uE08B';  // memory crystal wall (perimeter)
    static final char GLAMP   = '\uE08C';  // ghostlight lamp
    static final char FPILLAR = '\uE08D';  // faded pillar
    static final char ETILE   = '\uE08E';  // echo tile (main floor)
    static final char GPATH   = '\uE083';  // ghost path

    // Standard tile IDs
    static final char DOOR    = 'd';
    static final char DUNGEON = 'D';       // dungeon entrance
    static final char LOCKED_GATE = '[';   // locked iron gate (requires archive_key)
    static final char CARPET  = 'o';
    static final char SHELF   = 'k';
    static final char TABLE   = 'i';
    static final char CHAIR   = 'j';

    public static void main(String[] args) throws Exception {

        // ── 1. Exterior: shadow trench floor everywhere ────────────────
        fill(0, 19, 0, 19, SFLOOR);

        // ── 2. Perimeter: memory crystal walls ─────────────────────────
        hwall(0, 0, 19);
        hwall(19, 0, 19);
        vwall(0, 0, 19);
        vwall(19, 0, 19);

        // ── 3. Interior: echo tile floor ───────────────────────────────
        fill(1, 18, 1, 18, ETILE);

        // ── 4. Entry: south opening at (9,18)/(10,18) ──────────────────
        g[18][9]  = ETILE;
        g[18][10] = ETILE;
        g[19][9]  = SFLOOR;
        g[19][10] = SFLOOR;

        // ── 5. Ghostlight lamps along perimeter corners and edges ──────
        // Corners
        g[1][1]   = GLAMP;
        g[1][18]  = GLAMP;
        g[18][1]  = GLAMP;
        g[18][18] = GLAMP;
        // Mid-perimeter
        g[1][9]   = GLAMP;
        g[1][10]  = GLAMP;
        g[9][1]   = GLAMP;
        g[10][1]  = GLAMP;
        g[9][18]  = GLAMP;
        g[10][18] = GLAMP;
        g[17][9]  = GLAMP;
        g[17][10] = GLAMP;

        // ── 6. Center platform (x:9-10, y:8-9): The Last Witness ──────
        g[7][8]  = FPILLAR; g[7][11] = FPILLAR;
        g[10][8] = FPILLAR; g[10][11] = FPILLAR;
        g[8][9]  = CARPET;  g[8][10] = CARPET;
        g[9][9]  = CARPET;  g[9][10] = CARPET;

        // ── 7. North: locked gate and dungeon entrance ─────────────────
        g[2][9]  = LOCKED_GATE;
        g[1][9]  = DUNGEON;

        // ── 8. East alcove (x:15-17, y:7-10): Lorekeeper Iris room ────
        // Walls
        hwall(6, 14, 18);
        hwall(11, 14, 18);
        vwall(14, 6, 11);
        vwall(18, 6, 11);
        // Door on south wall at x=15
        g[11][15] = DOOR;
        // Interior
        g[7][15] = SHELF; g[7][16] = SHELF; g[7][17] = SHELF;
        g[8][17] = SHELF;
        g[9][15] = TABLE; g[9][16] = CHAIR;
        g[10][17] = GLAMP;

        // ── 9. West alcove (x:3-5, y:7-10): Memory crystal display ────
        // Walls
        hwall(6, 1, 6);
        hwall(11, 1, 6);
        vwall(1, 6, 11);
        vwall(6, 6, 11);
        // Door on south wall at x=5
        g[11][5] = DOOR;
        // Interior
        g[7][2] = SHELF; g[7][3] = SHELF; g[7][4] = SHELF;
        g[8][2] = GLAMP;
        g[9][3] = SHELF; g[9][4] = SHELF;
        g[10][2] = GLAMP;

        // ── 10. Ghost path leading to dungeon entrance ─────────────────
        g[3][9]  = GPATH;
        g[4][9]  = GPATH;
        g[5][9]  = GPATH;

        // ── Write JSON ─────────────────────────────────────────────────
        writeJSON("data/towns/echo_point.rfmap");
        System.out.println("Done \u2014 data/towns/echo_point.rfmap written.");
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    static void fill(int r1, int r2, int c1, int c2, char tile) {
        for (int r = r1; r <= r2; r++)
            for (int c = c1; c <= c2; c++)
                g[r][c] = tile;
    }

    static void hwall(int row, int c1, int c2) {
        for (int c = c1; c <= c2; c++) g[row][c] = MCWALL;
    }

    static void vwall(int col, int r1, int r2) {
        for (int r = r1; r <= r2; r++) g[r][col] = MCWALL;
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
        pw.println("  \"name\": \"echo_point\",");
        pw.println("  \"width\": 20,");
        pw.println("  \"height\": 20,");
        pw.println("  \"interiorEntryX\": 9,");
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

        // NPC 1: The Last Witness — questgiver (archive access, Umbryn trial, Eighth Dream)
        pw.println("    {");
        pw.println("      \"id\": \"npc_echo_point_witness\",");
        pw.println("      \"name\": \"The Last Witness\",");
        pw.println("      \"spriteName\": \"npcs/townsman\",");
        pw.println("      \"type\": \"QUESTGIVER\",");
        pw.println("      \"defaultDialog\": \"I am the Last Witness. I have watched this archive since before the Shattering.\",");
        pw.println("      \"questCompleteDialog\": \"\",");
        pw.println("      \"shopItemIds\": [],");
        pw.println("      \"x\": 10,");
        pw.println("      \"y\": 8,");
        pw.println("      \"dialogueTree\": {");
        pw.println("        \"startNodeId\": \"greeting\",");
        pw.println("        \"nodes\": {");
        // greeting
        pw.println("          \"greeting\": {");
        pw.println("            \"id\": \"greeting\",");
        pw.println("            \"text\": \"I am the Last Witness. I have watched this archive since before the Shattering. Few come here. Fewer still are worthy of what lies beyond the gate.\",");
        pw.println("            \"choices\": [");
        pw.println("              {\"label\": \"I seek entry to the Archive.\", \"nextNodeId\": \"first_visit\", \"conditions\": [{\"type\": \"FLAG_NOT_SET\", \"target\": \"archive_access\", \"value\": \"\", \"amount\": 0}]},");
        pw.println("              {\"label\": \"I bring evidence of the Eighth Dream.\", \"nextNodeId\": \"eighth_dream\", \"conditions\": [{\"type\": \"FLAG_EQUALS\", \"target\": \"eighth_dream_ready\", \"value\": \"true\", \"amount\": 0}]},");
        pw.println("              {\"label\": \"What awaits me in the Archive?\", \"nextNodeId\": \"trial_info\", \"conditions\": [{\"type\": \"FLAG_EQUALS\", \"target\": \"archive_access\", \"value\": \"true\", \"amount\": 0}, {\"type\": \"FLAG_NOT_SET\", \"target\": \"umbryn_trial_accepted\", \"value\": \"\", \"amount\": 0}]},");
        pw.println("              {\"label\": \"I carry the weight of knowing.\", \"nextNodeId\": \"all_done\", \"conditions\": [{\"type\": \"FLAG_EQUALS\", \"target\": \"eighth_dream_complete\", \"value\": \"true\", \"amount\": 0}]},");
        pw.println("              {\"label\": \"Farewell.\", \"nextNodeId\": \"farewell\", \"conditions\": []}");
        pw.println("            ],");
        pw.println("            \"actions\": []");
        pw.println("          },");
        // first_visit — archive access branch
        pw.println("          \"first_visit\": {");
        pw.println("            \"id\": \"first_visit\",");
        pw.println("            \"text\": \"The Archive does not open for the curious. It opens for those who have suffered, who have lost, and who continue forward despite it. Tell me \\u2014 what have you lost?\",");
        pw.println("            \"choices\": [");
        pw.println("              {\"label\": \"I have lost companions to the dangers of this world.\", \"nextNodeId\": \"loss_check\", \"conditions\": [{\"type\": \"PLAYER_LEVEL\", \"target\": \"\", \"value\": \"\", \"amount\": 20}]},");
        pw.println("              {\"label\": \"I have lost nothing.\", \"nextNodeId\": \"not_ready\", \"conditions\": []}");
        pw.println("            ],");
        pw.println("            \"actions\": []");
        pw.println("          },");
        // loss_check — level gate
        pw.println("          \"loss_check\": {");
        pw.println("            \"id\": \"loss_check\",");
        pw.println("            \"text\": \"Your scars speak truth. The Archive recognizes those who have endured. Take this key \\u2014 it will open the iron gate to the north. What waits beyond is Umbryn\\u0027s domain.\",");
        pw.println("            \"choices\": [],");
        pw.println("            \"actions\": [");
        pw.println("              {\"type\": \"GIVE_ITEM\", \"target\": \"archive_key\", \"value\": \"\", \"amount\": 1},");
        pw.println("              {\"type\": \"SET_FLAG\", \"target\": \"archive_access\", \"value\": \"true\", \"amount\": 0},");
        pw.println("              {\"type\": \"LOG_MESSAGE\", \"target\": \"Archive Key received. The way is open.\", \"value\": \"GOOD\", \"amount\": 0},");
        pw.println("              {\"type\": \"CLOSE_DIALOGUE\", \"target\": \"\", \"value\": \"\", \"amount\": 0}");
        pw.println("            ],");
        pw.println("            \"conditions\": []");
        pw.println("          },");
        // not_ready
        pw.println("          \"not_ready\": {");
        pw.println("            \"id\": \"not_ready\",");
        pw.println("            \"text\": \"Then you are not ready. Return when you have.\",");
        pw.println("            \"choices\": [],");
        pw.println("            \"actions\": [{\"type\": \"CLOSE_DIALOGUE\", \"target\": \"\", \"value\": \"\", \"amount\": 0}]");
        pw.println("          },");
        // eighth_dream
        pw.println("          \"eighth_dream\": {");
        pw.println("            \"id\": \"eighth_dream\",");
        pw.println("            \"text\": \"You bring evidence of the Eighth Dream? ...Hope. Yes. I remember now. Umbryn locked this memory away because it was the one he could not bear. The dream that even death could be undone \\u2014 not through remembrance, but through forgiveness.\",");
        pw.println("            \"choices\": [],");
        pw.println("            \"actions\": [");
        pw.println("              {\"type\": \"SET_FLAG\", \"target\": \"eighth_dream_complete\", \"value\": \"true\", \"amount\": 0},");
        pw.println("              {\"type\": \"LOG_MESSAGE\", \"target\": \"The Eighth Dream is revealed. The Last Witness remembers.\", \"value\": \"GOOD\", \"amount\": 0},");
        pw.println("              {\"type\": \"CLOSE_DIALOGUE\", \"target\": \"\", \"value\": \"\", \"amount\": 0}");
        pw.println("            ]");
        pw.println("          },");
        // trial_info — give quest
        pw.println("          \"trial_info\": {");
        pw.println("            \"id\": \"trial_info\",");
        pw.println("            \"text\": \"Umbryn waits in the deepest sanctum. He will test you. Not your strength \\u2014 your memory. He wants to know if you are worthy of carrying the truths the Archive preserves. Steel yourself, traveler.\",");
        pw.println("            \"choices\": [],");
        pw.println("            \"actions\": [");
        pw.println("              {\"type\": \"GIVE_QUEST\", \"target\": \"umbryn_trial\", \"value\": \"\", \"amount\": 0},");
        pw.println("              {\"type\": \"SET_FLAG\", \"target\": \"umbryn_trial_accepted\", \"value\": \"true\", \"amount\": 0},");
        pw.println("              {\"type\": \"LOG_MESSAGE\", \"target\": \"Quest accepted: Umbryn\\u0027s Trial\", \"value\": \"GOOD\", \"amount\": 0},");
        pw.println("              {\"type\": \"CLOSE_DIALOGUE\", \"target\": \"\", \"value\": \"\", \"amount\": 0}");
        pw.println("            ]");
        pw.println("          },");
        // all_done
        pw.println("          \"all_done\": {");
        pw.println("            \"id\": \"all_done\",");
        pw.println("            \"text\": \"You carry the weight of knowing. May it serve you better than it served us.\",");
        pw.println("            \"choices\": [],");
        pw.println("            \"actions\": [{\"type\": \"CLOSE_DIALOGUE\", \"target\": \"\", \"value\": \"\", \"amount\": 0}]");
        pw.println("          },");
        // farewell
        pw.println("          \"farewell\": {");
        pw.println("            \"id\": \"farewell\",");
        pw.println("            \"text\": \"The Archive remembers all who enter. Even those who do not return.\",");
        pw.println("            \"choices\": [],");
        pw.println("            \"actions\": [{\"type\": \"CLOSE_DIALOGUE\", \"target\": \"\", \"value\": \"\", \"amount\": 0}]");
        pw.println("          }");
        pw.println("        }");
        pw.println("      }");
        pw.println("    },");

        // NPC 2: Lorekeeper Iris — lore NPC (teaches shadow_ward spell)
        pw.println("    {");
        pw.println("      \"id\": \"npc_echo_point_iris\",");
        pw.println("      \"name\": \"Lorekeeper Iris\",");
        pw.println("      \"spriteName\": \"npcs/townsman\",");
        pw.println("      \"type\": \"LORE\",");
        pw.println("      \"defaultDialog\": \"The Archive preserves not just memory but knowledge.\",");
        pw.println("      \"questCompleteDialog\": \"\",");
        pw.println("      \"shopItemIds\": [],");
        pw.println("      \"x\": 16,");
        pw.println("      \"y\": 8,");
        pw.println("      \"dialogueTree\": {");
        pw.println("        \"startNodeId\": \"greeting\",");
        pw.println("        \"nodes\": {");
        // greeting
        pw.println("          \"greeting\": {");
        pw.println("            \"id\": \"greeting\",");
        pw.println("            \"text\": \"The Archive preserves not just memory but knowledge. I can share what I\\u0027ve learned, if you\\u0027re willing to listen.\",");
        pw.println("            \"choices\": [");
        pw.println("              {\"label\": \"Teach me what you know.\", \"nextNodeId\": \"teach\", \"conditions\": []},");
        pw.println("              {\"label\": \"Tell me about Umbryn.\", \"nextNodeId\": \"lore\", \"conditions\": []},");
        pw.println("              {\"label\": \"Farewell.\", \"nextNodeId\": \"farewell\", \"conditions\": []}");
        pw.println("            ],");
        pw.println("            \"actions\": []");
        pw.println("          },");
        // teach
        pw.println("          \"teach\": {");
        pw.println("            \"id\": \"teach\",");
        pw.println("            \"text\": \"The shadows here are not empty \\u2014 they are filled with echoes of the dead. I can teach you to shape them into a ward. Focus your will, and the echoes will shield you.\",");
        pw.println("            \"choices\": [],");
        pw.println("            \"actions\": [");
        pw.println("              {\"type\": \"TEACH_SPELL\", \"target\": \"shadow_ward\", \"value\": \"\", \"amount\": 0},");
        pw.println("              {\"type\": \"LOG_MESSAGE\", \"target\": \"Lorekeeper Iris teaches you the Shadow Ward spell.\", \"value\": \"GOOD\", \"amount\": 0},");
        pw.println("              {\"type\": \"CLOSE_DIALOGUE\", \"target\": \"\", \"value\": \"\", \"amount\": 0}");
        pw.println("            ]");
        pw.println("          },");
        // lore
        pw.println("          \"lore\": {");
        pw.println("            \"id\": \"lore\",");
        pw.println("            \"text\": \"Umbryn was the first to remember. Before him, death was the end. He made death merely a transition \\u2014 from living to remembered. The Archive is his gift and his curse. Every soul that passes through the world leaves an echo here. Umbryn hears them all.\",");
        pw.println("            \"choices\": [");
        pw.println("              {\"label\": \"That is a heavy burden.\", \"nextNodeId\": \"farewell\", \"conditions\": []}");
        pw.println("            ],");
        pw.println("            \"actions\": []");
        pw.println("          },");
        // farewell
        pw.println("          \"farewell\": {");
        pw.println("            \"id\": \"farewell\",");
        pw.println("            \"text\": \"May the echoes guide you, traveler. The Archive forgets nothing.\",");
        pw.println("            \"choices\": [],");
        pw.println("            \"actions\": [{\"type\": \"CLOSE_DIALOGUE\", \"target\": \"\", \"value\": \"\", \"amount\": 0}]");
        pw.println("          }");
        pw.println("        }");
        pw.println("      }");
        pw.println("    }");

        pw.println("  ],");
        pw.println("  \"townEntrances\": [],");
        pw.println("  \"initialTileStates\": {},");
        pw.println("  \"overworldTeleporters\": []");
        pw.println("}");
        pw.close();
        System.out.println("  wrote: " + path);
    }
}

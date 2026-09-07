import java.io.*;

/**
 * Generates data/towns/the_hollow.rfmap — a 25x25 desolate settlement.
 * The Hollow is Island 6's only town: a dark, nearly-abandoned bowl
 * where the Nameless wander and Mourner Vel keeps vigil.
 *
 * Run from project root:
 *   javac -encoding UTF-8 tools/HollowGen.java -d tools/
 *   java -cp tools HollowGen
 */
public class HollowGen {

    static final int COLS = 25, ROWS = 25;
    static char[][] g = new char[ROWS][COLS];

    // Island 6 tile IDs
    static final char SFLOOR  = '\uE080';  // shadow trench floor (exterior)
    static final char DFLOOR  = '\uE08A';  // dust floor
    static final char MCWALL  = '\uE08B';  // memory crystal wall
    static final char GLAMP   = '\uE08C';  // ghostlight lamp
    static final char FPILLAR = '\uE08D';  // faded pillar
    static final char ETILE   = '\uE08E';  // echo tile
    static final char VTRENCH = '\uE086';  // void trench (impassable border)
    static final char GPATH   = '\uE083';  // ghost path

    // Standard tile IDs
    static final char DOOR    = 'd';
    static final char WELL    = 'Q';       // Well of Names (standard well tile)
    static final char CARPET  = 'o';       // carpet
    static final char TABLE   = 'i';
    static final char CHAIR   = 'j';
    static final char LOCKED  = '|';       // locked wooden door

    public static void main(String[] args) throws Exception {

        // ── 1. Fill everything with void trench (impassable border) ────────
        fill(0, 24, 0, 24, VTRENCH);

        // ── 2. Interior: dust floor base (inside the 2-tile-thick border) ──
        fill(2, 22, 2, 22, DFLOOR);

        // ── 3. Scattered echo tiles throughout the interior ────────────────
        g[6][8]   = ETILE;
        g[9][14]  = ETILE;
        g[10][6]  = ETILE;
        g[13][9]  = ETILE;
        g[14][18] = ETILE;
        g[15][5]  = ETILE;
        g[17][14] = ETILE;
        g[18][8]  = ETILE;
        g[19][20] = ETILE;
        g[20][11] = ETILE;

        // ── 4. Entry: south opening at (12,23)/(12,22) ────────────────────
        g[23][12] = GPATH;   // gap in outer void trench ring
        g[22][12] = DFLOOR;  // entry floor

        // ── 5. Mourner Vel's tent (west, x:4-7, y:8-11) ───────────────────
        // 4x4 open-air area with carpet, faded pillars at corners, ghostlight
        fill(8, 11, 4, 7, CARPET);
        g[8][4]   = FPILLAR;
        g[8][7]   = FPILLAR;
        g[11][4]  = FPILLAR;
        g[11][7]  = FPILLAR;
        g[8][6]   = GLAMP;   // single ghostlight — the only light source

        // ── 6. Nameless wanderer area (east, x:16-19, y:8-13) ─────────────
        // Scattered echo tiles among dust floor
        g[8][17]  = ETILE;
        g[9][16]  = ETILE;
        g[10][19] = ETILE;
        g[11][17] = ETILE;
        g[12][18] = ETILE;
        g[13][16] = ETILE;

        // ── 7. Well of Names at center ─────────────────────────────────────
        g[12][12] = WELL;

        // ── 8. Locked door and northern room (quest item location) ─────────
        // Locked wooden door at (12, 4)
        g[4][12]  = LOCKED;
        // 3x3 room behind the door (y:2-4, x:11-13) — already DFLOOR from fill
        // Memory crystal walls forming the room's boundary
        g[1][11]  = MCWALL;
        g[1][12]  = MCWALL;
        g[1][13]  = MCWALL;
        g[2][11]  = MCWALL;
        g[2][13]  = MCWALL;
        g[3][11]  = MCWALL;
        g[3][13]  = MCWALL;
        // Echo tiles inside the room (y:2-3, x:12)
        g[2][12]  = ETILE;
        g[3][12]  = ETILE;

        // ── Write JSON ─────────────────────────────────────────────────────
        writeJSON("data/towns/the_hollow.rfmap");
        System.out.println("Done \u2014 data/towns/the_hollow.rfmap written.");
    }

    // ── Helpers ────────────────────────────────────────────────────────────

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
        pw.println("  \"name\": \"the_hollow\",");
        pw.println("  \"width\": 25,");
        pw.println("  \"height\": 25,");
        pw.println("  \"interiorEntryX\": 12,");
        pw.println("  \"interiorEntryY\": 22,");
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

        // NPC 1: Mourner Vel — questgiver (Eighth Dream)
        pw.println("    {");
        pw.println("      \"id\": \"npc_hollow_vel\",");
        pw.println("      \"name\": \"Mourner Vel\",");
        pw.println("      \"spriteName\": \"npcs/townsman\",");
        pw.println("      \"type\": \"QUESTGIVER\",");
        pw.println("      \"defaultDialog\": \"You can speak? You still have a name? Then you are not yet lost.\",");
        pw.println("      \"questCompleteDialog\": \"\",");
        pw.println("      \"shopItemIds\": [],");
        pw.println("      \"x\": 5,");
        pw.println("      \"y\": 9,");
        pw.println("      \"dialogueTree\": {");
        pw.println("        \"startNodeId\": \"greeting\",");
        pw.println("        \"nodes\": {");
        // greeting
        pw.println("          \"greeting\": {");
        pw.println("            \"id\": \"greeting\",");
        pw.println("            \"text\": \"You can speak? You still have a name? Then you are not yet lost.\",");
        pw.println("            \"choices\": [");
        pw.println("              {\"label\": \"What is this place?\", \"nextNodeId\": \"no_quest\", \"conditions\": [{\"type\": \"FLAG_NOT_SET\", \"target\": \"eighth_dream_accepted\", \"value\": \"\", \"amount\": 0}]},");
        pw.println("              {\"label\": \"[Quest Active] I found something.\", \"nextNodeId\": \"quest_has_evidence\", \"conditions\": [{\"type\": \"QUEST_ACTIVE\", \"target\": \"eighth_dream\", \"value\": \"\", \"amount\": 0}, {\"type\": \"HAS_ITEM\", \"target\": \"eighth_dream_evidence\", \"value\": \"\", \"amount\": 1}]},");
        pw.println("              {\"label\": \"[Quest Active] Still searching.\", \"nextNodeId\": \"quest_no_evidence\", \"conditions\": [{\"type\": \"QUEST_ACTIVE\", \"target\": \"eighth_dream\", \"value\": \"\", \"amount\": 0}, {\"type\": \"FLAG_NOT_SET\", \"target\": \"eighth_dream_ready\", \"value\": \"\", \"amount\": 0}]},");
        pw.println("              {\"label\": \"[Quest Done] About the eighth dream...\", \"nextNodeId\": \"quest_done\", \"conditions\": [{\"type\": \"FLAG_EQUALS\", \"target\": \"eighth_dream_ready\", \"value\": \"true\", \"amount\": 0}]},");
        pw.println("              {\"label\": \"Farewell.\", \"nextNodeId\": \"farewell\", \"conditions\": []}");
        pw.println("            ],");
        pw.println("            \"actions\": []");
        pw.println("          },");
        // no_quest
        pw.println("          \"no_quest\": {");
        pw.println("            \"id\": \"no_quest\",");
        pw.println("            \"text\": \"The Last Witness speaks of an eighth dream \\u2014 one the other seven consumed. I have seen fragments in the Nameless\\u0027 memories. Will you investigate?\",");
        pw.println("            \"choices\": [");
        pw.println("              {\"label\": \"I will look into it.\", \"nextNodeId\": \"quest_accept\", \"conditions\": []},");
        pw.println("              {\"label\": \"Not now.\", \"nextNodeId\": \"farewell\", \"conditions\": []}");
        pw.println("            ],");
        pw.println("            \"actions\": []");
        pw.println("          },");
        // quest_accept
        pw.println("          \"quest_accept\": {");
        pw.println("            \"id\": \"quest_accept\",");
        pw.println("            \"text\": \"Then go. The Archive of Tears holds what remains of the old dreams. Search its second level.\",");
        pw.println("            \"choices\": [],");
        pw.println("            \"actions\": [");
        pw.println("              {\"type\": \"GIVE_QUEST\", \"target\": \"eighth_dream\", \"value\": \"\", \"amount\": 0},");
        pw.println("              {\"type\": \"SET_FLAG\", \"target\": \"eighth_dream_accepted\", \"value\": \"true\", \"amount\": 0},");
        pw.println("              {\"type\": \"LOG_MESSAGE\", \"target\": \"Quest accepted: The Eighth Dream\", \"value\": \"GOOD\", \"amount\": 0},");
        pw.println("              {\"type\": \"CLOSE_DIALOGUE\", \"target\": \"\", \"value\": \"\", \"amount\": 0}");
        pw.println("            ]");
        pw.println("          },");
        // quest_has_evidence
        pw.println("          \"quest_has_evidence\": {");
        pw.println("            \"id\": \"quest_has_evidence\",");
        pw.println("            \"text\": \"You found it. Hope. The dream the gods destroyed. Take this to the Last Witness at Echo Point.\",");
        pw.println("            \"choices\": [],");
        pw.println("            \"actions\": [");
        pw.println("              {\"type\": \"SET_FLAG\", \"target\": \"eighth_dream_ready\", \"value\": \"true\", \"amount\": 0},");
        pw.println("              {\"type\": \"LOG_MESSAGE\", \"target\": \"Vel recognizes the evidence. Bring it to the Last Witness at Echo Point.\", \"value\": \"GOOD\", \"amount\": 0},");
        pw.println("              {\"type\": \"CLOSE_DIALOGUE\", \"target\": \"\", \"value\": \"\", \"amount\": 0}");
        pw.println("            ]");
        pw.println("          },");
        // quest_no_evidence
        pw.println("          \"quest_no_evidence\": {");
        pw.println("            \"id\": \"quest_no_evidence\",");
        pw.println("            \"text\": \"Search the Archive of Tears\\u0027 second level. The evidence is there, locked away.\",");
        pw.println("            \"choices\": [");
        pw.println("              {\"label\": \"I\\u0027ll keep looking.\", \"nextNodeId\": \"farewell\", \"conditions\": []}");
        pw.println("            ],");
        pw.println("            \"actions\": []");
        pw.println("          },");
        // quest_done
        pw.println("          \"quest_done\": {");
        pw.println("            \"id\": \"quest_done\",");
        pw.println("            \"text\": \"Hope was consumed but not destroyed. It lives in fragments, in every act of kindness.\",");
        pw.println("            \"choices\": [");
        pw.println("              {\"label\": \"A comforting thought.\", \"nextNodeId\": \"farewell\", \"conditions\": []}");
        pw.println("            ],");
        pw.println("            \"actions\": []");
        pw.println("          },");
        // farewell
        pw.println("          \"farewell\": {");
        pw.println("            \"id\": \"farewell\",");
        pw.println("            \"text\": \"Remember your name. That is all any of us can do.\",");
        pw.println("            \"choices\": [],");
        pw.println("            \"actions\": [{\"type\": \"CLOSE_DIALOGUE\", \"target\": \"\", \"value\": \"\", \"amount\": 0}]");
        pw.println("          }");
        pw.println("        }");
        pw.println("      }");
        pw.println("    },");

        // NPC 2: Nameless One — lore
        pw.println("    {");
        pw.println("      \"id\": \"npc_hollow_nameless1\",");
        pw.println("      \"name\": \"Nameless One\",");
        pw.println("      \"spriteName\": \"npcs/townsman\",");
        pw.println("      \"type\": \"LORE\",");
        pw.println("      \"defaultDialog\": \"...\",");
        pw.println("      \"questCompleteDialog\": \"\",");
        pw.println("      \"shopItemIds\": [],");
        pw.println("      \"x\": 17,");
        pw.println("      \"y\": 9,");
        pw.println("      \"dialogueTree\": {");
        pw.println("        \"startNodeId\": \"greeting\",");
        pw.println("        \"nodes\": {");
        pw.println("          \"greeting\": {");
        pw.println("            \"id\": \"greeting\",");
        pw.println("            \"text\": \"...\",");
        pw.println("            \"choices\": [],");
        pw.println("            \"actions\": [{\"type\": \"CLOSE_DIALOGUE\", \"target\": \"\", \"value\": \"\", \"amount\": 0}]");
        pw.println("          }");
        pw.println("        }");
        pw.println("      }");
        pw.println("    },");

        // NPC 3: Nameless Two — lore
        pw.println("    {");
        pw.println("      \"id\": \"npc_hollow_nameless2\",");
        pw.println("      \"name\": \"Nameless Two\",");
        pw.println("      \"spriteName\": \"npcs/townsman\",");
        pw.println("      \"type\": \"LORE\",");
        pw.println("      \"defaultDialog\": \"Name... had a... who?\",");
        pw.println("      \"questCompleteDialog\": \"\",");
        pw.println("      \"shopItemIds\": [],");
        pw.println("      \"x\": 18,");
        pw.println("      \"y\": 11,");
        pw.println("      \"dialogueTree\": {");
        pw.println("        \"startNodeId\": \"greeting\",");
        pw.println("        \"nodes\": {");
        pw.println("          \"greeting\": {");
        pw.println("            \"id\": \"greeting\",");
        pw.println("            \"text\": \"Name... had a... who?\",");
        pw.println("            \"choices\": [],");
        pw.println("            \"actions\": [{\"type\": \"CLOSE_DIALOGUE\", \"target\": \"\", \"value\": \"\", \"amount\": 0}]");
        pw.println("          }");
        pw.println("        }");
        pw.println("      }");
        pw.println("    },");

        // NPC 4: Nameless Three — lore
        pw.println("    {");
        pw.println("      \"id\": \"npc_hollow_nameless3\",");
        pw.println("      \"name\": \"Nameless Three\",");
        pw.println("      \"spriteName\": \"npcs/townsman\",");
        pw.println("      \"type\": \"LORE\",");
        pw.println("      \"defaultDialog\": \"The music. I remember the music. But not the song.\",");
        pw.println("      \"questCompleteDialog\": \"\",");
        pw.println("      \"shopItemIds\": [],");
        pw.println("      \"x\": 16,");
        pw.println("      \"y\": 13,");
        pw.println("      \"dialogueTree\": {");
        pw.println("        \"startNodeId\": \"greeting\",");
        pw.println("        \"nodes\": {");
        pw.println("          \"greeting\": {");
        pw.println("            \"id\": \"greeting\",");
        pw.println("            \"text\": \"The music. I remember the music. But not the song.\",");
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

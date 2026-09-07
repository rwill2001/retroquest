import java.io.*;

/**
 * Generates data/towns/dusthaven.rfmap — a 30x30 shadow-shrouded town.
 * Main hub of Umbryn (Island 6). Memory crystal walls form the buildings.
 *
 * Run from project root:
 *   javac -encoding UTF-8 tools/DusthavenGen.java -d tools/
 *   java -cp tools DusthavenGen
 */
public class DusthavenGen {

    static final int COLS = 30, ROWS = 30;
    static char[][] g = new char[ROWS][COLS];

    // Island 6 tile IDs
    static final char SFLOOR  = '\uE080';  // shadow trench floor (exterior)
    static final char DFLOOR  = '\uE08A';  // dust floor (interior base)
    static final char MCWALL  = '\uE08B';  // memory crystal wall
    static final char GLAMP   = '\uE08C';  // ghostlight lamp
    static final char FPILLAR = '\uE08D';  // faded pillar
    static final char ETILE   = '\uE08E';  // echo tile
    static final char GPATH   = '\uE083';  // ghost path

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

        // ── 1. Exterior: shadow trench floor everywhere ───────────────────
        fill(0, 29, 0, 29, SFLOOR);

        // ── 2. Ghost path bleeding through exterior ───────────────────────
        g[2][5] = GPATH;  g[3][6] = GPATH;  g[4][5] = GPATH;
        g[1][20] = GPATH; g[2][21] = GPATH; g[3][22] = GPATH;
        g[25][3] = GPATH; g[26][4] = GPATH; g[27][3] = GPATH;
        g[24][22] = GPATH; g[25][23] = GPATH; g[26][24] = GPATH;
        g[10][0] = GPATH; g[11][1] = GPATH; g[12][0] = GPATH;
        g[16][28] = GPATH; g[17][29] = GPATH; g[18][28] = GPATH;
        g[0][14] = GPATH; g[0][15] = GPATH;
        g[29][8] = GPATH; g[29][22] = GPATH;

        // ── 3. Entry: south opening ───────────────────────────────────────
        g[28][14] = DOOR; g[28][15] = DOOR;
        g[27][14] = DFLOOR; g[27][15] = DFLOOR;

        // ── 4. Central town square ────────────────────────────────────────
        fill(12, 17, 12, 17, DFLOOR);
        // Altar at center
        g[14][14] = ALTAR; g[14][15] = ALTAR;
        // Ghostlight lamps at corners
        g[12][12] = GLAMP; g[12][17] = GLAMP;
        g[17][12] = GLAMP; g[17][17] = GLAMP;
        // Faded pillars as decoration
        g[13][12] = FPILLAR; g[13][17] = FPILLAR;
        g[16][12] = FPILLAR; g[16][17] = FPILLAR;

        // ── 5. NW quadrant: Archivist Sable's Library (8x6) ──────────────
        // Building from (3,3) to (8,10)  — 8 wide, 6 tall
        fill(3, 8, 3, 10, DFLOOR);
        hwall(3, 3, 10);   // top wall
        hwall(8, 3, 10);   // bottom wall
        vwall(3, 3, 8);    // left wall
        vwall(10, 3, 8);   // right wall
        g[8][7] = DOOR;    // entrance on south wall
        // Interior furnishings
        g[4][4] = SHELF; g[4][5] = SHELF; g[4][6] = SHELF; g[4][7] = SHELF;
        g[5][4] = SHELF; g[5][5] = SHELF;
        g[6][4] = TABLE; g[6][5] = TABLE; g[6][6] = CHAIR; g[6][7] = CHAIR;
        g[4][9] = GLAMP;
        g[7][9] = GLAMP;
        g[5][8] = BARREL;

        // ── 6. NE quadrant: Inn "The Fading Rest" (6x6) ──────────────────
        // Building from (3,19) to (8,24)  — 6 wide, 6 tall
        fill(3, 8, 19, 24, DFLOOR);
        hwall(3, 19, 24);  // top wall
        hwall(8, 19, 24);  // bottom wall
        vwall(19, 3, 8);   // left wall
        vwall(24, 3, 8);   // right wall
        g[8][21] = DOOR;   // entrance on south wall
        // Interior furnishings
        g[4][20] = BED; g[4][21] = BED;
        g[5][20] = BED; g[5][21] = BED;
        g[4][23] = FIRE;
        g[6][20] = TABLE; g[6][21] = CHAIR; g[6][22] = CHAIR;
        g[7][23] = GLAMP;

        // ── 7. SW quadrant: Shop "The Dustmarket" (6x6) ──────────────────
        // Building from (20,3) to (25,8)  — 6 wide, 6 tall
        fill(20, 25, 3, 8, DFLOOR);
        hwall(20, 3, 8);   // top wall
        hwall(25, 3, 8);   // bottom wall
        vwall(3, 20, 25);  // left wall
        vwall(8, 20, 25);  // right wall
        g[20][6] = DOOR;   // entrance on north wall
        // Interior furnishings
        g[21][4] = COUNTER; g[21][5] = COUNTER; g[21][6] = COUNTER;
        g[22][4] = SHELF; g[22][5] = SHELF;
        g[23][4] = BARREL; g[23][5] = BARREL;
        g[24][7] = GLAMP;

        // ── 8. SE quadrant: Residential ruins ─────────────────────────────
        // Scattered faded pillars and ghost path
        g[20][20] = FPILLAR; g[20][25] = FPILLAR;
        g[23][22] = FPILLAR; g[25][20] = FPILLAR;
        g[21][21] = GPATH; g[22][23] = GPATH; g[24][21] = GPATH;
        g[23][24] = GPATH; g[25][23] = GPATH; g[21][24] = GPATH;
        g[22][20] = GPATH; g[24][25] = GPATH;
        g[22][22] = ETILE; g[24][24] = ETILE;

        // ── 9. Scattered ghostlight lamps for lighting ────────────────────
        g[10][8] = GLAMP;  g[10][21] = GLAMP;
        g[19][8] = GLAMP;  g[19][21] = GLAMP;
        g[15][5] = GLAMP;  g[15][24] = GLAMP;
        g[27][10] = GLAMP; g[27][19] = GLAMP;

        // ── 10. Dust floor paths connecting buildings to center ───────────
        // Path from library south door to center
        fill(9, 11, 7, 7, DFLOOR);
        fill(11, 12, 7, 12, DFLOOR);
        // Path from inn south door to center
        fill(9, 11, 21, 21, DFLOOR);
        fill(11, 12, 17, 21, DFLOOR);
        // Path from shop north door to center
        fill(18, 19, 6, 6, DFLOOR);
        fill(17, 18, 6, 12, DFLOOR);
        // Path from center to south entry
        fill(18, 27, 14, 15, DFLOOR);

        // ── Write JSON ────────────────────────────────────────────────────
        writeJSON("data/towns/dusthaven.rfmap");
        System.out.println("Done \u2014 data/towns/dusthaven.rfmap written.");
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
        pw.println("  \"name\": \"dusthaven\",");
        pw.println("  \"width\": 30,");
        pw.println("  \"height\": 30,");
        pw.println("  \"interiorEntryX\": 14,");
        pw.println("  \"interiorEntryY\": 27,");
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

        // NPC 1: Archivist Sable — questgiver (fading_names + echo_of_seraphine)
        pw.println("    {");
        pw.println("      \"id\": \"npc_dusthaven_sable\",");
        pw.println("      \"name\": \"Archivist Sable\",");
        pw.println("      \"spriteName\": \"npcs/townsman\",");
        pw.println("      \"type\": \"QUESTGIVER\",");
        pw.println("      \"defaultDialog\": \"The memories endure because someone cares enough to save them.\",");
        pw.println("      \"questCompleteDialog\": \"\",");
        pw.println("      \"shopItemIds\": [],");
        pw.println("      \"x\": 7,");
        pw.println("      \"y\": 6,");
        pw.println("      \"dialogueTree\": {");
        pw.println("        \"startNodeId\": \"greeting\",");
        pw.println("        \"nodes\": {");
        // greeting
        pw.println("          \"greeting\": {");
        pw.println("            \"id\": \"greeting\",");
        pw.println("            \"text\": \"Welcome to what remains of the Grand Archive. I am Sable, keeper of what Umbryn would preserve.\",");
        pw.println("            \"choices\": [");
        pw.println("              {\"label\": \"What is this place?\", \"nextNodeId\": \"about\", \"conditions\": []},");
        pw.println("              {\"label\": \"Do you need help?\", \"nextNodeId\": \"quest_offer\", \"conditions\": [{\"type\": \"FLAG_NOT_SET\", \"target\": \"fading_names_accepted\", \"value\": \"\", \"amount\": 0}]},");
        pw.println("              {\"label\": \"[Quest Active] I\\u0027m searching for the crystals.\", \"nextNodeId\": \"quest_progress\", \"conditions\": [{\"type\": \"QUEST_ACTIVE\", \"target\": \"fading_names\", \"value\": \"\", \"amount\": 0}]},");
        pw.println("              {\"label\": \"[Quest Complete] I\\u0027ve recovered the memory crystals.\", \"nextNodeId\": \"quest_complete\", \"conditions\": [{\"type\": \"QUEST_COMPLETE\", \"target\": \"fading_names\", \"value\": \"\", \"amount\": 0}, {\"type\": \"FLAG_NOT_SET\", \"target\": \"echo_seraphine_accepted\", \"value\": \"\", \"amount\": 0}]},");
        pw.println("              {\"label\": \"[Echo Quest Active] Where should I go?\", \"nextNodeId\": \"echo_progress\", \"conditions\": [{\"type\": \"QUEST_ACTIVE\", \"target\": \"echo_of_seraphine\", \"value\": \"\", \"amount\": 0}]},");
        pw.println("              {\"label\": \"Farewell.\", \"nextNodeId\": \"farewell\", \"conditions\": []}");
        pw.println("            ],");
        pw.println("            \"actions\": []");
        pw.println("          },");
        // about
        pw.println("          \"about\": {");
        pw.println("            \"id\": \"about\",");
        pw.println("            \"text\": \"Dusthaven was once a center of learning \\u2014 scholars came from every island to study in the Grand Archive. Now the shadows have swallowed most of it. Only this fragment remains, and even it is fading.\",");
        pw.println("            \"choices\": [");
        pw.println("              {\"label\": \"Can I help preserve it?\", \"nextNodeId\": \"quest_offer\", \"conditions\": [{\"type\": \"FLAG_NOT_SET\", \"target\": \"fading_names_accepted\", \"value\": \"\", \"amount\": 0}]},");
        pw.println("              {\"label\": \"I see.\", \"nextNodeId\": \"farewell\", \"conditions\": []}");
        pw.println("            ],");
        pw.println("            \"actions\": []");
        pw.println("          },");
        // quest_offer
        pw.println("          \"quest_offer\": {");
        pw.println("            \"id\": \"quest_offer\",");
        pw.println("            \"text\": \"My memory crystals are degrading. Four of them, scattered through the Archive of Tears, hold memories too important to lose. Will you recover them?\",");
        pw.println("            \"choices\": [");
        pw.println("              {\"label\": \"I\\u0027ll find the crystals.\", \"nextNodeId\": \"quest_accept\", \"conditions\": []},");
        pw.println("              {\"label\": \"Not right now.\", \"nextNodeId\": \"farewell\", \"conditions\": []}");
        pw.println("            ],");
        pw.println("            \"actions\": []");
        pw.println("          },");
        // quest_accept
        pw.println("          \"quest_accept\": {");
        pw.println("            \"id\": \"quest_accept\",");
        pw.println("            \"text\": \"Thank you. The Archive of Tears lies to the north \\u2014 the crystals pulse with a faint blue light. You\\u0027ll know them when you see them. Be wary of what lurks in the deeper halls.\",");
        pw.println("            \"choices\": [],");
        pw.println("            \"actions\": [");
        pw.println("              {\"type\": \"GIVE_QUEST\", \"target\": \"fading_names\", \"value\": \"\", \"amount\": 0},");
        pw.println("              {\"type\": \"SET_FLAG\", \"target\": \"fading_names_accepted\", \"value\": \"true\", \"amount\": 0},");
        pw.println("              {\"type\": \"LOG_MESSAGE\", \"target\": \"Quest accepted: The Fading Names\", \"value\": \"GOOD\", \"amount\": 0},");
        pw.println("              {\"type\": \"CLOSE_DIALOGUE\", \"target\": \"\", \"value\": \"\", \"amount\": 0}");
        pw.println("            ]");
        pw.println("          },");
        // quest_progress
        pw.println("          \"quest_progress\": {");
        pw.println("            \"id\": \"quest_progress\",");
        pw.println("            \"text\": \"The crystals grow dimmer with each passing day. Please \\u2014 the Archive of Tears holds what remains. Four crystals, each with a name that must not be forgotten.\",");
        pw.println("            \"choices\": [");
        pw.println("              {\"label\": \"I\\u0027ll keep searching.\", \"nextNodeId\": \"farewell\", \"conditions\": []}");
        pw.println("            ],");
        pw.println("            \"actions\": []");
        pw.println("          },");
        // quest_complete
        pw.println("          \"quest_complete\": {");
        pw.println("            \"id\": \"quest_complete\",");
        pw.println("            \"text\": \"You\\u0027ve restored the crystals... the names are clear again. Aldric. Seraphine. Tomas. Veyla. They will not be forgotten. There is something else. Seraphine came here thirty years ago. I can summon her echo if you wish.\",");
        pw.println("            \"choices\": [");
        pw.println("              {\"label\": \"Summon the echo. I want to know her story.\", \"nextNodeId\": \"echo_accept\", \"conditions\": []},");
        pw.println("              {\"label\": \"Not yet.\", \"nextNodeId\": \"farewell\", \"conditions\": []}");
        pw.println("            ],");
        pw.println("            \"actions\": []");
        pw.println("          },");
        // echo_accept
        pw.println("          \"echo_accept\": {");
        pw.println("            \"id\": \"echo_accept\",");
        pw.println("            \"text\": \"Very well. I will attune the crystals to Seraphine\\u0027s frequency. Seek the Well of Names in The Hollow \\u2014 I will project her echo there. Speak with her, and learn what Umbryn took from us.\",");
        pw.println("            \"choices\": [],");
        pw.println("            \"actions\": [");
        pw.println("              {\"type\": \"GIVE_QUEST\", \"target\": \"echo_of_seraphine\", \"value\": \"\", \"amount\": 0},");
        pw.println("              {\"type\": \"SET_FLAG\", \"target\": \"echo_seraphine_accepted\", \"value\": \"true\", \"amount\": 0},");
        pw.println("              {\"type\": \"LOG_MESSAGE\", \"target\": \"Quest accepted: Echo of Seraphine\", \"value\": \"GOOD\", \"amount\": 0},");
        pw.println("              {\"type\": \"CLOSE_DIALOGUE\", \"target\": \"\", \"value\": \"\", \"amount\": 0}");
        pw.println("            ]");
        pw.println("          },");
        // echo_progress
        pw.println("          \"echo_progress\": {");
        pw.println("            \"id\": \"echo_progress\",");
        pw.println("            \"text\": \"Stand before the Well of Names in The Hollow. I will project the echo there. Seraphine\\u0027s memory awaits you.\",");
        pw.println("            \"choices\": [],");
        pw.println("            \"actions\": [");
        pw.println("              {\"type\": \"SET_FLAG\", \"target\": \"seraphine_echo_ready\", \"value\": \"true\", \"amount\": 0},");
        pw.println("              {\"type\": \"CLOSE_DIALOGUE\", \"target\": \"\", \"value\": \"\", \"amount\": 0}");
        pw.println("            ]");
        pw.println("          },");
        // farewell
        pw.println("          \"farewell\": {");
        pw.println("            \"id\": \"farewell\",");
        pw.println("            \"text\": \"The memories endure because someone cares enough to save them.\",");
        pw.println("            \"choices\": [],");
        pw.println("            \"actions\": [{\"type\": \"CLOSE_DIALOGUE\", \"target\": \"\", \"value\": \"\", \"amount\": 0}]");
        pw.println("          }");
        pw.println("        }");
        pw.println("      }");
        pw.println("    },");

        // NPC 2: Keeper Mord — shopkeeper
        pw.println("    {");
        pw.println("      \"id\": \"npc_dusthaven_mord\",");
        pw.println("      \"name\": \"Keeper Mord\",");
        pw.println("      \"spriteName\": \"npcs/townsman\",");
        pw.println("      \"type\": \"SHOPKEEPER\",");
        pw.println("      \"defaultDialog\": \"Goods from the surface still find their way here. Browse, if you\\u0027ve coin.\",");
        pw.println("      \"questCompleteDialog\": \"\",");
        pw.println("      \"shopItemIds\": [\"shadow_blade\", \"memory_mail\", \"echo_shield\", \"ring_of_remembrance\", \"shadow_tonic\"],");
        pw.println("      \"x\": 7,");
        pw.println("      \"y\": 22,");
        pw.println("      \"dialogueTree\": {");
        pw.println("        \"startNodeId\": \"greeting\",");
        pw.println("        \"nodes\": {");
        // greeting
        pw.println("          \"greeting\": {");
        pw.println("            \"id\": \"greeting\",");
        pw.println("            \"text\": \"Goods from the surface still find their way here. Browse, if you\\u0027ve coin.\",");
        pw.println("            \"choices\": [");
        pw.println("              {\"label\": \"Show me your wares.\", \"nextNodeId\": \"shop\", \"conditions\": []},");
        pw.println("              {\"label\": \"Tell me about Dusthaven.\", \"nextNodeId\": \"lore\", \"conditions\": []},");
        pw.println("              {\"label\": \"Never mind.\", \"nextNodeId\": \"farewell\", \"conditions\": []}");
        pw.println("            ],");
        pw.println("            \"actions\": []");
        pw.println("          },");
        // shop
        pw.println("          \"shop\": {");
        pw.println("            \"id\": \"shop\",");
        pw.println("            \"text\": \"Everything\\u0027s tested against shadow-rot. Take your pick.\",");
        pw.println("            \"choices\": [],");
        pw.println("            \"actions\": [{\"type\": \"OPEN_SHOP\", \"target\": \"\", \"value\": \"\", \"amount\": 0}]");
        pw.println("          },");
        // lore
        pw.println("          \"lore\": {");
        pw.println("            \"id\": \"lore\",");
        pw.println("            \"text\": \"Dusthaven was three times this size, once. The ghosts remember what we\\u0027ve forgotten. Sometimes at night you can see them \\u2014 walking the old streets, opening doors that aren\\u0027t there anymore.\",");
        pw.println("            \"choices\": [");
        pw.println("              {\"label\": \"Interesting.\", \"nextNodeId\": \"farewell\", \"conditions\": []}");
        pw.println("            ],");
        pw.println("            \"actions\": []");
        pw.println("          },");
        // farewell
        pw.println("          \"farewell\": {");
        pw.println("            \"id\": \"farewell\",");
        pw.println("            \"text\": \"Watch for the shadow-things. They don\\u0027t like the ghostlight, but they\\u0027re patient.\",");
        pw.println("            \"choices\": [],");
        pw.println("            \"actions\": [{\"type\": \"CLOSE_DIALOGUE\", \"target\": \"\", \"value\": \"\", \"amount\": 0}]");
        pw.println("          }");
        pw.println("        }");
        pw.println("      }");
        pw.println("    },");

        // NPC 3: Whisper — innkeeper
        pw.println("    {");
        pw.println("      \"id\": \"npc_dusthaven_whisper\",");
        pw.println("      \"name\": \"Whisper\",");
        pw.println("      \"spriteName\": \"npcs/townsman\",");
        pw.println("      \"type\": \"INNKEEPER\",");
        pw.println("      \"defaultDialog\": \"A bed and warmth, such as it is. 15 gold for a night\\u0027s rest.\",");
        pw.println("      \"questCompleteDialog\": \"\",");
        pw.println("      \"shopItemIds\": [],");
        pw.println("      \"x\": 22,");
        pw.println("      \"y\": 6,");
        pw.println("      \"dialogueTree\": {");
        pw.println("        \"startNodeId\": \"greeting\",");
        pw.println("        \"nodes\": {");
        // greeting
        pw.println("          \"greeting\": {");
        pw.println("            \"id\": \"greeting\",");
        pw.println("            \"text\": \"A bed and warmth, such as it is. 15 gold for a night\\u0027s rest.\",");
        pw.println("            \"choices\": [");
        pw.println("              {\"label\": \"I\\u0027ll rest. (Full heal, 15g)\", \"nextNodeId\": \"rest\", \"conditions\": [{\"type\": \"HAS_GOLD\", \"target\": \"\", \"value\": \"\", \"amount\": 15}]},");
        pw.println("              {\"label\": \"What\\u0027s it like living here?\", \"nextNodeId\": \"lore\", \"conditions\": []},");
        pw.println("              {\"label\": \"Not now.\", \"nextNodeId\": \"farewell\", \"conditions\": []}");
        pw.println("            ],");
        pw.println("            \"actions\": []");
        pw.println("          },");
        // rest
        pw.println("          \"rest\": {");
        pw.println("            \"id\": \"rest\",");
        pw.println("            \"text\": \"Sleep well. The ghostlight will keep the worst of it away. Probably.\",");
        pw.println("            \"choices\": [],");
        pw.println("            \"actions\": [");
        pw.println("              {\"type\": \"GIVE_GOLD\", \"target\": \"\", \"value\": \"\", \"amount\": -15},");
        pw.println("              {\"type\": \"HEAL_PLAYER\", \"target\": \"\", \"value\": \"\", \"amount\": 0},");
        pw.println("              {\"type\": \"LOG_MESSAGE\", \"target\": \"You rest in the Fading Rest. The ghostlight flickers softly through the night.\", \"value\": \"GOOD\", \"amount\": 0},");
        pw.println("              {\"type\": \"CLOSE_DIALOGUE\", \"target\": \"\", \"value\": \"\", \"amount\": 0}");
        pw.println("            ]");
        pw.println("          },");
        // lore
        pw.println("          \"lore\": {");
        pw.println("            \"id\": \"lore\",");
        pw.println("            \"text\": \"The ghosts here aren\\u0027t dangerous. They\\u0027re just... doing what they always did. Shopping. Talking. Living lives that ended decades ago. It\\u0027s the shadow-things you need to worry about \\u2014 they come from deeper in, where Umbryn\\u0027s grip is strongest.\",");
        pw.println("            \"choices\": [");
        pw.println("              {\"label\": \"I\\u0027ll be careful.\", \"nextNodeId\": \"farewell\", \"conditions\": []}");
        pw.println("            ],");
        pw.println("            \"actions\": []");
        pw.println("          },");
        // farewell
        pw.println("          \"farewell\": {");
        pw.println("            \"id\": \"farewell\",");
        pw.println("            \"text\": \"Keep to the ghostlight. The dark remembers everything you\\u0027d rather forget.\",");
        pw.println("            \"choices\": [],");
        pw.println("            \"actions\": [{\"type\": \"CLOSE_DIALOGUE\", \"target\": \"\", \"value\": \"\", \"amount\": 0}]");
        pw.println("          }");
        pw.println("        }");
        pw.println("      }");
        pw.println("    },");

        // NPC 4: Ghost of Aldric — lore
        pw.println("    {");
        pw.println("      \"id\": \"npc_dusthaven_aldric\",");
        pw.println("      \"name\": \"Ghost of Aldric\",");
        pw.println("      \"spriteName\": \"npcs/townsman\",");
        pw.println("      \"type\": \"LORE\",");
        pw.println("      \"defaultDialog\": \"[The ghost of a merchant arranges invisible wares on an empty stall. He does not see you.]\",");
        pw.println("      \"questCompleteDialog\": \"\",");
        pw.println("      \"shopItemIds\": [],");
        pw.println("      \"x\": 22,");
        pw.println("      \"y\": 22,");
        pw.println("      \"dialogueTree\": {");
        pw.println("        \"startNodeId\": \"greeting\",");
        pw.println("        \"nodes\": {");
        // greeting
        pw.println("          \"greeting\": {");
        pw.println("            \"id\": \"greeting\",");
        pw.println("            \"text\": \"[The ghost of a merchant arranges invisible wares on an empty stall. He does not see you.]\",");
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

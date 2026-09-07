import java.io.*;

/**
 * Generates data/towns/gold_guard_camp.rfmap — a 30x30 military command post
 * for Island 7 (Bellorak / The Golden War Isles). Main hub of the Gold Guard faction.
 *
 * Run from project root:
 *   javac -encoding UTF-8 tools/GoldGuardCampGen.java -d tools/
 *   java -cp tools GoldGuardCampGen
 */
public class GoldGuardCampGen {

    static final int COLS = 30, ROWS = 30;
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
    static final char COUNTER = 'c';
    static final char BARREL  = 'n';
    static final char SHELF   = 'k';
    static final char BED     = 'l';
    static final char TABLE   = 'i';
    static final char CHAIR   = 'j';
    static final char FIRE    = 'u';
    static final char TORCH   = 'v';

    public static void main(String[] args) throws Exception {

        // ── 1. Exterior: iron wall border, war plank floor interior ──────
        fill(0, 29, 0, 29, WFLOOR);
        hwall(0, 0, 29);   // top perimeter
        hwall(29, 0, 29);  // bottom perimeter
        vwall(0, 0, 29);   // left perimeter
        vwall(29, 0, 29);  // right perimeter

        // ── 2. Entry: south wall opening ─────────────────────────────────
        g[29][14] = EXIT; g[29][15] = EXIT;
        g[28][14] = WFLOOR; g[28][15] = WFLOOR;

        // ── 3. War banners flanking the entrance ─────────────────────────
        g[28][13] = WBANNER; g[28][16] = WBANNER;
        g[27][13] = WBANNER; g[27][16] = WBANNER;

        // ── 4. Central parade ground with war banners ────────────────────
        fill(13, 17, 13, 17, WFLOOR);
        g[13][13] = WBANNER; g[13][17] = WBANNER;
        g[17][13] = WBANNER; g[17][17] = WBANNER;
        g[15][15] = WBANNER;  // center banner
        // Iron lamps at parade ground corners
        g[12][12] = ILAMP; g[12][18] = ILAMP;
        g[18][12] = ILAMP; g[18][18] = ILAMP;

        // ── 5. NW: Command Post (War-Marshal Korrath) 8x7 ───────────────
        // Building from (2,2) to (8,9)
        fill(2, 8, 2, 9, WFLOOR);
        hwall(2, 2, 9);    // top wall
        hwall(8, 2, 9);    // bottom wall
        vwall(2, 2, 8);    // left wall
        vwall(9, 2, 8);    // right wall
        g[8][6] = DOOR;    // entrance on south wall
        // War banners at entrance
        g[9][5] = WBANNER; g[9][7] = WBANNER;
        // Interior furnishings
        g[3][3] = TABLE; g[3][4] = TABLE; g[3][5] = TABLE;  // war table
        g[4][3] = CHAIR; g[4][5] = CHAIR;
        g[3][8] = WRACK; g[4][8] = WRACK;  // weapon display
        g[5][3] = SHELF; g[5][4] = SHELF;  // maps / documents
        g[6][8] = ILAMP;
        g[7][3] = BARREL; g[7][4] = BARREL;
        g[3][7] = ILAMP;

        // ── 6. NE: Quartermaster's Store (Quartermaster Ives) 7x7 ───────
        // Building from (2,20) to (8,27)
        fill(2, 8, 20, 27, WFLOOR);
        hwall(2, 20, 27);  // top wall
        hwall(8, 20, 27);  // bottom wall
        vwall(20, 2, 8);   // left wall
        vwall(27, 2, 8);   // right wall
        g[8][23] = DOOR;   // entrance on south wall
        // Interior furnishings
        g[3][21] = COUNTER; g[3][22] = COUNTER; g[3][23] = COUNTER;
        g[4][21] = SHELF; g[4][22] = SHELF; g[4][23] = SHELF;
        g[5][21] = BARREL; g[5][22] = BARREL;
        g[3][26] = WRACK; g[4][26] = WRACK; g[5][26] = WRACK;
        g[6][26] = ILAMP;
        g[7][21] = ILAMP;

        // ── 7. SW: Barracks / Iron Keeper Bram (7x7) ────────────────────
        // Building from (20,2) to (26,8)
        fill(20, 26, 2, 8, WFLOOR);
        hwall(20, 2, 8);   // top wall
        hwall(26, 2, 8);   // bottom wall
        vwall(2, 20, 26);  // left wall
        vwall(8, 20, 26);  // right wall
        g[20][5] = DOOR;   // entrance on north wall
        // Interior furnishings
        g[21][3] = BED; g[21][4] = BED;
        g[22][3] = BED; g[22][4] = BED;
        g[21][7] = BED; g[22][7] = BED;
        g[23][3] = FIRE;
        g[24][7] = ILAMP;
        g[25][3] = TABLE; g[25][4] = CHAIR; g[25][5] = CHAIR;

        // ── 8. SE: Training yard (open area with weapon racks) ───────────
        g[21][22] = WRACK; g[21][26] = WRACK;
        g[24][22] = WRACK; g[24][26] = WRACK;
        g[22][24] = WFLOOR; g[23][24] = WFLOOR;  // sparring area
        g[25][24] = ILAMP;
        g[21][24] = ILAMP;

        // ── 9. Connecting paths and lamps ────────────────────────────────
        // Path from command post to parade ground
        fill(9, 12, 6, 6, WFLOOR);
        // Path from quartermaster to parade ground
        fill(9, 12, 23, 23, WFLOOR);
        // Path from barracks to parade ground
        fill(18, 19, 5, 5, WFLOOR);
        // Path from parade ground to south exit
        fill(18, 27, 14, 15, WFLOOR);

        // Perimeter lamps
        g[1][1] = ILAMP;   g[1][28] = ILAMP;
        g[28][1] = ILAMP;  g[28][28] = ILAMP;
        g[10][14] = ILAMP; g[10][15] = ILAMP;
        g[19][7] = ILAMP;  g[19][22] = ILAMP;

        // ── Write JSON ──────────────────────────────────────────────────
        writeJSON("data/towns/gold_guard_camp.rfmap");
        System.out.println("Done \u2014 data/towns/gold_guard_camp.rfmap written.");
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
        pw.println("  \"name\": \"gold_guard_camp\",");
        pw.println("  \"width\": 30,");
        pw.println("  \"height\": 30,");
        pw.println("  \"interiorEntryX\": 14,");
        pw.println("  \"interiorEntryY\": 28,");
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

        // NPC 1: War-Marshal Korrath — questgiver
        pw.println("    {");
        pw.println("      \"id\": \"npc_gold_guard_korrath\",");
        pw.println("      \"name\": \"War-Marshal Korrath\",");
        pw.println("      \"spriteName\": \"npcs/townsman\",");
        pw.println("      \"type\": \"QUESTGIVER\",");
        pw.println("      \"defaultDialog\": \"Stand fast! The Golden War has no room for cowards.\",");
        pw.println("      \"questCompleteDialog\": \"\",");
        pw.println("      \"shopItemIds\": [],");
        pw.println("      \"x\": 15,");
        pw.println("      \"y\": 8,");
        pw.println("      \"dialogueTree\": {");
        pw.println("        \"startNodeId\": \"greeting\",");
        pw.println("        \"nodes\": {");
        // greeting
        pw.println("          \"greeting\": {");
        pw.println("            \"id\": \"greeting\",");
        pw.println("            \"text\": \"Stand fast! I am Korrath, War-Marshal of the Gold Guard. Every blade in this camp answers to me \\u2014 and if you\\u0027re here, you\\u0027ll answer too. Good steel beside you is worth more than gold.\",");
        pw.println("            \"choices\": [");
        pw.println("              {\"label\": \"I\\u0027m ready to fight.\", \"nextNodeId\": \"quest_offer\", \"conditions\": [{\"type\": \"FLAG_NOT_SET\", \"target\": \"eternal_siege_accepted\", \"value\": \"\", \"amount\": 0}]},");
        pw.println("              {\"label\": \"[Quest Active] I\\u0027m pushing the front.\", \"nextNodeId\": \"quest_progress\", \"conditions\": [{\"type\": \"QUEST_ACTIVE\", \"target\": \"eternal_siege\", \"value\": \"\", \"amount\": 0}]},");
        pw.println("              {\"label\": \"[Siege Complete] The front is secure.\", \"nextNodeId\": \"siege_complete\", \"conditions\": [{\"type\": \"QUEST_COMPLETE\", \"target\": \"eternal_siege\", \"value\": \"\", \"amount\": 0}, {\"type\": \"FLAG_NOT_SET\", \"target\": \"arena_offered\", \"value\": \"\", \"amount\": 0}]},");
        pw.println("              {\"label\": \"Tell me about this war.\", \"nextNodeId\": \"war_lore\", \"conditions\": []},");
        pw.println("              {\"label\": \"Farewell, Marshal.\", \"nextNodeId\": \"farewell\", \"conditions\": []}");
        pw.println("            ],");
        pw.println("            \"actions\": []");
        pw.println("          },");
        // quest_offer
        pw.println("          \"quest_offer\": {");
        pw.println("            \"id\": \"quest_offer\",");
        pw.println("            \"text\": \"Good. The Iron Reckoners are pushing our eastern flank. I need someone who can break through their siege lines and destroy the war engines. This fight\\u0027s been going on longer than anyone can remember \\u2014 but today, we end it.\",");
        pw.println("            \"choices\": [");
        pw.println("              {\"label\": \"I\\u0027ll break their siege.\", \"nextNodeId\": \"quest_accept\", \"conditions\": []},");
        pw.println("              {\"label\": \"Not yet.\", \"nextNodeId\": \"farewell\", \"conditions\": []}");
        pw.println("            ],");
        pw.println("            \"actions\": []");
        pw.println("          },");
        // quest_accept
        pw.println("          \"quest_accept\": {");
        pw.println("            \"id\": \"quest_accept\",");
        pw.println("            \"text\": \"That\\u0027s the spirit. Head east through the war fields. Destroy three siege engines and scatter their forward camp. And soldier \\u2014 something about this war doesn\\u0027t sit right with me. Thirty years and neither side gains ground? Keep your eyes open.\",");
        pw.println("            \"choices\": [],");
        pw.println("            \"actions\": [");
        pw.println("              {\"type\": \"GIVE_QUEST\", \"target\": \"eternal_siege\", \"value\": \"\", \"amount\": 0},");
        pw.println("              {\"type\": \"SET_FLAG\", \"target\": \"eternal_siege_accepted\", \"value\": \"true\", \"amount\": 0},");
        pw.println("              {\"type\": \"LOG_MESSAGE\", \"target\": \"Quest accepted: The Eternal Siege\", \"value\": \"GOOD\", \"amount\": 0},");
        pw.println("              {\"type\": \"CLOSE_DIALOGUE\", \"target\": \"\", \"value\": \"\", \"amount\": 0}");
        pw.println("            ]");
        pw.println("          },");
        // quest_progress
        pw.println("          \"quest_progress\": {");
        pw.println("            \"id\": \"quest_progress\",");
        pw.println("            \"text\": \"The siege lines won\\u0027t break themselves, soldier. Get back out there. Destroy those war engines and scatter their camp. We\\u0027re counting on you.\",");
        pw.println("            \"choices\": [");
        pw.println("              {\"label\": \"On my way.\", \"nextNodeId\": \"farewell\", \"conditions\": []}");
        pw.println("            ],");
        pw.println("            \"actions\": []");
        pw.println("          },");
        // siege_complete
        pw.println("          \"siege_complete\": {");
        pw.println("            \"id\": \"siege_complete\",");
        pw.println("            \"text\": \"You did it. The eastern flank is ours. But... I\\u0027ve been thinking. Thirty years, neither side wins. The Reckoners rebuild as fast as we destroy. Something\\u0027s feeding this war. There\\u0027s an arena at the center of the island \\u2014 Bellorak\\u0027s arena. I hear the god himself watches the fights. Maybe the answer is there.\",");
        pw.println("            \"choices\": [");
        pw.println("              {\"label\": \"I\\u0027ll investigate the arena.\", \"nextNodeId\": \"arena_accept\", \"conditions\": []},");
        pw.println("              {\"label\": \"I need time to think.\", \"nextNodeId\": \"farewell\", \"conditions\": []}");
        pw.println("            ],");
        pw.println("            \"actions\": []");
        pw.println("          },");
        // arena_accept
        pw.println("          \"arena_accept\": {");
        pw.println("            \"id\": \"arena_accept\",");
        pw.println("            \"text\": \"Bellorak\\u0027s Arena lies in the center of the island. Fight your way through, and demand an audience with the god. If this war is a game to him \\u2014 we deserve to know.\",");
        pw.println("            \"choices\": [],");
        pw.println("            \"actions\": [");
        pw.println("              {\"type\": \"GIVE_QUEST\", \"target\": \"bellorak_arena\", \"value\": \"\", \"amount\": 0},");
        pw.println("              {\"type\": \"SET_FLAG\", \"target\": \"arena_offered\", \"value\": \"true\", \"amount\": 0},");
        pw.println("              {\"type\": \"LOG_MESSAGE\", \"target\": \"Quest accepted: Bellorak\\u0027s Arena\", \"value\": \"GOOD\", \"amount\": 0},");
        pw.println("              {\"type\": \"CLOSE_DIALOGUE\", \"target\": \"\", \"value\": \"\", \"amount\": 0}");
        pw.println("            ]");
        pw.println("          },");
        // war_lore
        pw.println("          \"war_lore\": {");
        pw.println("            \"id\": \"war_lore\",");
        pw.println("            \"text\": \"The Golden War has raged for thirty years between the Gold Guard and the Iron Reckoners. We fight for Bellorak\\u0027s favor \\u2014 or so we\\u0027re told. Honestly? I\\u0027ve lost my eye and half my friends, and I\\u0027m no closer to understanding why. Something about this war stinks, and it\\u0027s not just the corpses.\",");
        pw.println("            \"choices\": [");
        pw.println("              {\"label\": \"You suspect the war is engineered?\", \"nextNodeId\": \"suspicion\", \"conditions\": []},");
        pw.println("              {\"label\": \"I see.\", \"nextNodeId\": \"farewell\", \"conditions\": []}");
        pw.println("            ],");
        pw.println("            \"actions\": []");
        pw.println("          },");
        // suspicion
        pw.println("          \"suspicion\": {");
        pw.println("            \"id\": \"suspicion\",");
        pw.println("            \"text\": \"Aye. Every time one side gains an advantage, something shifts. New weapons appear for the other side. Fresh recruits come from nowhere. It\\u0027s like someone\\u0027s keeping the scales balanced on purpose. And Bellorak\\u0027s arena sits right in the middle, watching us bleed.\",");
        pw.println("            \"choices\": [");
        pw.println("              {\"label\": \"I\\u0027ll look into it.\", \"nextNodeId\": \"farewell\", \"conditions\": []}");
        pw.println("            ],");
        pw.println("            \"actions\": []");
        pw.println("          },");
        // farewell
        pw.println("          \"farewell\": {");
        pw.println("            \"id\": \"farewell\",");
        pw.println("            \"text\": \"Stand fast, soldier. Good steel beside you.\",");
        pw.println("            \"choices\": [],");
        pw.println("            \"actions\": [{\"type\": \"CLOSE_DIALOGUE\", \"target\": \"\", \"value\": \"\", \"amount\": 0}]");
        pw.println("          }");
        pw.println("        }");
        pw.println("      }");
        pw.println("    },");

        // NPC 2: Quartermaster Ives — shopkeeper
        pw.println("    {");
        pw.println("      \"id\": \"npc_gold_guard_ives\",");
        pw.println("      \"name\": \"Quartermaster Ives\",");
        pw.println("      \"spriteName\": \"npcs/townsman\",");
        pw.println("      \"type\": \"SHOPKEEPER\",");
        pw.println("      \"defaultDialog\": \"Need gear? I\\u0027ve got the best iron on the island.\",");
        pw.println("      \"questCompleteDialog\": \"\",");
        pw.println("      \"shopItemIds\": [\"iron_warsword\", \"battle_plate\", \"champion_shield\", \"war_tonic\"],");
        pw.println("      \"x\": 22,");
        pw.println("      \"y\": 15,");
        pw.println("      \"dialogueTree\": {");
        pw.println("        \"startNodeId\": \"greeting\",");
        pw.println("        \"nodes\": {");
        // greeting
        pw.println("          \"greeting\": {");
        pw.println("            \"id\": \"greeting\",");
        pw.println("            \"text\": \"Quartermaster Ives, at your service. Every blade and breastplate in this camp passes through my hands. You look like you could use an upgrade \\u2014 war\\u0027s no place for dull steel.\",");
        pw.println("            \"choices\": [");
        pw.println("              {\"label\": \"Show me your wares.\", \"nextNodeId\": \"shop\", \"conditions\": []},");
        pw.println("              {\"label\": \"How\\u0027s the supply line?\", \"nextNodeId\": \"lore\", \"conditions\": []},");
        pw.println("              {\"label\": \"Never mind.\", \"nextNodeId\": \"farewell\", \"conditions\": []}");
        pw.println("            ],");
        pw.println("            \"actions\": []");
        pw.println("          },");
        // shop
        pw.println("          \"shop\": {");
        pw.println("            \"id\": \"shop\",");
        pw.println("            \"text\": \"Finest war-grade equipment this side of the front. Everything\\u0027s battle-tested \\u2014 some of it more than once.\",");
        pw.println("            \"choices\": [],");
        pw.println("            \"actions\": [{\"type\": \"OPEN_SHOP\", \"target\": \"\", \"value\": \"\", \"amount\": 0}]");
        pw.println("          },");
        // lore
        pw.println("          \"lore\": {");
        pw.println("            \"id\": \"lore\",");
        pw.println("            \"text\": \"Supply line? Ha! There is no supply line. Everything we have, we forge or we take. The Reckoners have a forge-hand who can shape iron like butter \\u2014 we\\u0027ve got numbers and discipline. It evens out. Always does.\",");
        pw.println("            \"choices\": [");
        pw.println("              {\"label\": \"Interesting.\", \"nextNodeId\": \"farewell\", \"conditions\": []}");
        pw.println("            ],");
        pw.println("            \"actions\": []");
        pw.println("          },");
        // farewell
        pw.println("          \"farewell\": {");
        pw.println("            \"id\": \"farewell\",");
        pw.println("            \"text\": \"Keep your blade sharp and your armor tight. War waits for no one.\",");
        pw.println("            \"choices\": [],");
        pw.println("            \"actions\": [{\"type\": \"CLOSE_DIALOGUE\", \"target\": \"\", \"value\": \"\", \"amount\": 0}]");
        pw.println("          }");
        pw.println("        }");
        pw.println("      }");
        pw.println("    },");

        // NPC 3: Iron Keeper Bram — innkeeper
        pw.println("    {");
        pw.println("      \"id\": \"npc_gold_guard_bram\",");
        pw.println("      \"name\": \"Iron Keeper Bram\",");
        pw.println("      \"spriteName\": \"npcs/townsman\",");
        pw.println("      \"type\": \"INNKEEPER\",");
        pw.println("      \"defaultDialog\": \"The barracks are open. Rest while you can \\u2014 the war won\\u0027t wait.\",");
        pw.println("      \"questCompleteDialog\": \"\",");
        pw.println("      \"shopItemIds\": [],");
        pw.println("      \"x\": 8,");
        pw.println("      \"y\": 15,");
        pw.println("      \"dialogueTree\": {");
        pw.println("        \"startNodeId\": \"greeting\",");
        pw.println("        \"nodes\": {");
        // greeting
        pw.println("          \"greeting\": {");
        pw.println("            \"id\": \"greeting\",");
        pw.println("            \"text\": \"I\\u0027m Bram, keeper of the barracks. You look like you\\u0027ve been through it. A hot meal and a cot will set you right \\u2014 20 gold for a full night\\u0027s rest.\",");
        pw.println("            \"choices\": [");
        pw.println("              {\"label\": \"I\\u0027ll rest. (Full heal, 20g)\", \"nextNodeId\": \"rest\", \"conditions\": [{\"type\": \"HAS_GOLD\", \"target\": \"\", \"value\": \"\", \"amount\": 20}]},");
        pw.println("              {\"label\": \"What\\u0027s life like in camp?\", \"nextNodeId\": \"lore\", \"conditions\": []},");
        pw.println("              {\"label\": \"Not now.\", \"nextNodeId\": \"farewell\", \"conditions\": []}");
        pw.println("            ],");
        pw.println("            \"actions\": []");
        pw.println("          },");
        // rest
        pw.println("          \"rest\": {");
        pw.println("            \"id\": \"rest\",");
        pw.println("            \"text\": \"Get some sleep. I\\u0027ll keep the fire going. Tomorrow\\u0027s another day of fighting, so rest well while the iron\\u0027s quiet.\",");
        pw.println("            \"choices\": [],");
        pw.println("            \"actions\": [");
        pw.println("              {\"type\": \"GIVE_GOLD\", \"target\": \"\", \"value\": \"\", \"amount\": -20},");
        pw.println("              {\"type\": \"HEAL_PLAYER\", \"target\": \"\", \"value\": \"\", \"amount\": 0},");
        pw.println("              {\"type\": \"LOG_MESSAGE\", \"target\": \"You rest in the Gold Guard barracks. The sounds of the camp fade as you sleep.\", \"value\": \"GOOD\", \"amount\": 0},");
        pw.println("              {\"type\": \"CLOSE_DIALOGUE\", \"target\": \"\", \"value\": \"\", \"amount\": 0}");
        pw.println("            ]");
        pw.println("          },");
        // lore
        pw.println("          \"lore\": {");
        pw.println("            \"id\": \"lore\",");
        pw.println("            \"text\": \"Camp life is simple. Train. Fight. Rest. Repeat. The Marshal keeps us sharp, and the Quartermaster keeps us armed. We\\u0027ve been at this so long most of us don\\u0027t remember anything else. That\\u0027s the war for you \\u2014 it becomes your whole world.\",");
        pw.println("            \"choices\": [");
        pw.println("              {\"label\": \"Sounds exhausting.\", \"nextNodeId\": \"farewell\", \"conditions\": []}");
        pw.println("            ],");
        pw.println("            \"actions\": []");
        pw.println("          },");
        // farewell
        pw.println("          \"farewell\": {");
        pw.println("            \"id\": \"farewell\",");
        pw.println("            \"text\": \"Stay sharp out there. The barracks are always open when you need them.\",");
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

import java.io.*;

/**
 * Generates data/towns/iron_reckoner_camp.rfmap — a 25x25 rougher military camp
 * for Island 7 (Bellorak / The Golden War Isles). The Iron Reckoner faction hub.
 *
 * Run from project root:
 *   javac -encoding UTF-8 tools/IronReckonerCampGen.java -d tools/
 *   java -cp tools IronReckonerCampGen
 */
public class IronReckonerCampGen {

    static final int COLS = 25, ROWS = 25;
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

    public static void main(String[] args) throws Exception {

        // ── 1. Exterior: war plank floor with iron wall perimeter ────────
        fill(0, 24, 0, 24, WFLOOR);
        hwall(0, 0, 24);   // top perimeter
        hwall(24, 0, 24);  // bottom perimeter
        vwall(0, 0, 24);   // left perimeter
        vwall(24, 0, 24);  // right perimeter

        // ── 2. Entry: south wall opening ─────────────────────────────────
        g[24][12] = EXIT; g[24][13] = EXIT;
        g[23][12] = WFLOOR; g[23][13] = WFLOOR;

        // ── 3. War banners at entrance ───────────────────────────────────
        g[23][11] = WBANNER; g[23][14] = WBANNER;

        // ── 4. Central fire pit / gathering area ─────────────────────────
        fill(10, 14, 10, 14, WFLOOR);
        g[12][12] = FIRE;  // central campfire
        g[11][11] = BARREL; g[11][13] = BARREL;
        g[13][11] = BARREL; g[13][13] = BARREL;
        g[10][10] = ILAMP; g[10][14] = ILAMP;
        g[14][10] = ILAMP; g[14][14] = ILAMP;

        // ── 5. NW: Shield-Mother Brynn's quarters (7x6) ─────────────────
        // Building from (2,2) to (7,8)
        fill(2, 7, 2, 8, WFLOOR);
        hwall(2, 2, 8);    // top wall
        hwall(7, 2, 8);    // bottom wall
        vwall(2, 2, 7);    // left wall
        vwall(8, 2, 7);    // right wall
        g[7][5] = DOOR;    // entrance on south wall
        // Interior furnishings
        g[3][3] = TABLE; g[3][4] = TABLE;
        g[4][3] = CHAIR; g[4][4] = CHAIR;
        g[3][7] = SHELF; g[4][7] = SHELF;
        g[5][3] = BED; g[5][4] = BED;
        g[6][7] = ILAMP;
        // War banner at entrance
        g[8][4] = WBANNER; g[8][6] = WBANNER;

        // ── 6. East: Hospital / Surgeon Hask (6x7) ──────────────────────
        // Building from (2,16) to (8,22)
        fill(2, 8, 16, 22, WFLOOR);
        hwall(2, 16, 22);  // top wall
        hwall(8, 16, 22);  // bottom wall
        vwall(16, 2, 8);   // left wall
        vwall(22, 2, 8);   // right wall
        g[8][19] = DOOR;   // entrance on south wall
        // Interior: beds for wounded
        g[3][17] = BED; g[3][18] = BED;
        g[4][17] = BED; g[4][18] = BED;
        g[5][17] = BED; g[5][18] = BED;
        g[3][21] = SHELF; g[4][21] = SHELF;  // medical supplies
        g[5][21] = BARREL;
        g[6][17] = TABLE; g[6][18] = CHAIR;
        g[7][21] = ILAMP;
        g[3][20] = ILAMP;

        // ── 7. SW: Forge-Hand Dara's Workshop (6x6) ─────────────────────
        // Building from (16,2) to (21,7)
        fill(16, 21, 2, 7, WFLOOR);
        hwall(16, 2, 7);   // top wall
        hwall(21, 2, 7);   // bottom wall
        vwall(2, 16, 21);  // left wall
        vwall(7, 16, 21);  // right wall
        g[16][5] = DOOR;   // entrance on north wall
        // Interior: forge workshop
        g[17][3] = COUNTER; g[17][4] = COUNTER; g[17][5] = COUNTER;
        g[18][3] = WRACK; g[18][4] = WRACK; g[18][5] = WRACK;
        g[19][3] = BARREL; g[19][4] = BARREL;
        g[20][6] = ILAMP;
        g[17][6] = ILAMP;

        // ── 8. SE: Makeshift storage / weapon racks ──────────────────────
        g[17][18] = WRACK; g[17][21] = WRACK;
        g[19][18] = WRACK; g[19][21] = WRACK;
        g[20][19] = BARREL; g[20][20] = BARREL;
        g[18][20] = ILAMP;
        g[21][18] = WBANNER; g[21][21] = WBANNER;

        // ── 9. Connecting paths ──────────────────────────────────────────
        // Path from Brynn to center
        fill(8, 9, 5, 5, WFLOOR);
        fill(9, 10, 5, 10, WFLOOR);
        // Path from hospital to center
        fill(9, 10, 14, 19, WFLOOR);
        // Path from forge to center
        fill(14, 15, 5, 5, WFLOOR);
        // Path from center to south exit
        fill(15, 22, 12, 13, WFLOOR);

        // Perimeter lamps
        g[1][1] = ILAMP;   g[1][23] = ILAMP;
        g[23][1] = ILAMP;  g[23][23] = ILAMP;

        // ── Write JSON ──────────────────────────────────────────────────
        writeJSON("data/towns/iron_reckoner_camp.rfmap");
        System.out.println("Done \u2014 data/towns/iron_reckoner_camp.rfmap written.");
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
        pw.println("  \"name\": \"iron_reckoner_camp\",");
        pw.println("  \"width\": 25,");
        pw.println("  \"height\": 25,");
        pw.println("  \"interiorEntryX\": 12,");
        pw.println("  \"interiorEntryY\": 23,");
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

        // NPC 1: Shield-Mother Brynn — questgiver
        pw.println("    {");
        pw.println("      \"id\": \"npc_iron_reckoner_brynn\",");
        pw.println("      \"name\": \"Shield-Mother Brynn\",");
        pw.println("      \"spriteName\": \"npcs/townsman\",");
        pw.println("      \"type\": \"QUESTGIVER\",");
        pw.println("      \"defaultDialog\": \"If the fighting never stops, maybe the fighting is the problem.\",");
        pw.println("      \"questCompleteDialog\": \"\",");
        pw.println("      \"shopItemIds\": [],");
        pw.println("      \"x\": 12,");
        pw.println("      \"y\": 6,");
        pw.println("      \"dialogueTree\": {");
        pw.println("        \"startNodeId\": \"greeting\",");
        pw.println("        \"nodes\": {");
        // greeting
        pw.println("          \"greeting\": {");
        pw.println("            \"id\": \"greeting\",");
        pw.println("            \"text\": \"You\\u0027re not one of ours. Good \\u2014 maybe fresh eyes will see what we\\u0027ve been too tired to notice. I\\u0027m Brynn. Shield-Mother, they call me, though I haven\\u0027t held a shield in years. These old bones have seen too many die for nothing.\",");
        pw.println("            \"choices\": [");
        pw.println("              {\"label\": \"What do you mean, for nothing?\", \"nextNodeId\": \"war_cost\", \"conditions\": []},");
        pw.println("              {\"label\": \"Do you need help?\", \"nextNodeId\": \"quest_offer\", \"conditions\": [{\"type\": \"FLAG_NOT_SET\", \"target\": \"brothers_three_accepted\", \"value\": \"\", \"amount\": 0}]},");
        pw.println("              {\"label\": \"[Quest Active] I\\u0027m searching for the brothers.\", \"nextNodeId\": \"quest_progress\", \"conditions\": [{\"type\": \"QUEST_ACTIVE\", \"target\": \"brothers_three\", \"value\": \"\", \"amount\": 0}]},");
        pw.println("              {\"label\": \"[Quest Complete] I found them.\", \"nextNodeId\": \"quest_complete\", \"conditions\": [{\"type\": \"QUEST_COMPLETE\", \"target\": \"brothers_three\", \"value\": \"\", \"amount\": 0}]},");
        pw.println("              {\"label\": \"Farewell.\", \"nextNodeId\": \"farewell\", \"conditions\": []}");
        pw.println("            ],");
        pw.println("            \"actions\": []");
        pw.println("          },");
        // war_cost
        pw.println("          \"war_cost\": {");
        pw.println("            \"id\": \"war_cost\",");
        pw.println("            \"text\": \"Thirty years of the Golden War. Thirty years of burying friends. And for what? The front line hasn\\u0027t moved in a decade. If the fighting never stops, maybe the fighting is the problem. Maybe someone wants it this way.\",");
        pw.println("            \"choices\": [");
        pw.println("              {\"label\": \"Who would want eternal war?\", \"nextNodeId\": \"blame\", \"conditions\": []},");
        pw.println("              {\"label\": \"I understand.\", \"nextNodeId\": \"farewell\", \"conditions\": []}");
        pw.println("            ],");
        pw.println("            \"actions\": []");
        pw.println("          },");
        // blame
        pw.println("          \"blame\": {");
        pw.println("            \"id\": \"blame\",");
        pw.println("            \"text\": \"Bellorak. The god of war and glory. He feeds on conflict \\u2014 every death in his name makes him stronger. His arena sits at the center of the island like a spider in its web. We\\u0027re not soldiers. We\\u0027re livestock.\",");
        pw.println("            \"choices\": [");
        pw.println("              {\"label\": \"Can anything be done?\", \"nextNodeId\": \"quest_offer\", \"conditions\": [{\"type\": \"FLAG_NOT_SET\", \"target\": \"brothers_three_accepted\", \"value\": \"\", \"amount\": 0}]},");
        pw.println("              {\"label\": \"A grim thought.\", \"nextNodeId\": \"farewell\", \"conditions\": []}");
        pw.println("            ],");
        pw.println("            \"actions\": []");
        pw.println("          },");
        // quest_offer
        pw.println("          \"quest_offer\": {");
        pw.println("            \"id\": \"quest_offer\",");
        pw.println("            \"text\": \"There is something. Three brothers \\u2014 Holt, Marren, and Tor \\u2014 left camp a week ago to scout the no-man\\u0027s land. They haven\\u0027t returned. They were good lads. If they\\u0027re alive, bring them home. If they\\u0027re not... bring me their tags so I can remember their names.\",");
        pw.println("            \"choices\": [");
        pw.println("              {\"label\": \"I\\u0027ll find them.\", \"nextNodeId\": \"quest_accept\", \"conditions\": []},");
        pw.println("              {\"label\": \"Not right now.\", \"nextNodeId\": \"farewell\", \"conditions\": []}");
        pw.println("            ],");
        pw.println("            \"actions\": []");
        pw.println("          },");
        // quest_accept
        pw.println("          \"quest_accept\": {");
        pw.println("            \"id\": \"quest_accept\",");
        pw.println("            \"text\": \"Thank you. They were heading south through the war fields toward the Neutral Ground. Be careful \\u2014 the no-man\\u0027s land is crawling with war-beasts that answer to neither side. Bellorak\\u0027s creatures, I think.\",");
        pw.println("            \"choices\": [],");
        pw.println("            \"actions\": [");
        pw.println("              {\"type\": \"GIVE_QUEST\", \"target\": \"brothers_three\", \"value\": \"\", \"amount\": 0},");
        pw.println("              {\"type\": \"SET_FLAG\", \"target\": \"brothers_three_accepted\", \"value\": \"true\", \"amount\": 0},");
        pw.println("              {\"type\": \"LOG_MESSAGE\", \"target\": \"Quest accepted: The Brothers Three\", \"value\": \"GOOD\", \"amount\": 0},");
        pw.println("              {\"type\": \"CLOSE_DIALOGUE\", \"target\": \"\", \"value\": \"\", \"amount\": 0}");
        pw.println("            ]");
        pw.println("          },");
        // quest_progress
        pw.println("          \"quest_progress\": {");
        pw.println("            \"id\": \"quest_progress\",");
        pw.println("            \"text\": \"Have you found Holt, Marren, and Tor? They went south toward the Neutral Ground. Please \\u2014 every day they\\u0027re out there is another day the war takes from us.\",");
        pw.println("            \"choices\": [");
        pw.println("              {\"label\": \"I\\u0027m still looking.\", \"nextNodeId\": \"farewell\", \"conditions\": []}");
        pw.println("            ],");
        pw.println("            \"actions\": []");
        pw.println("          },");
        // quest_complete
        pw.println("          \"quest_complete\": {");
        pw.println("            \"id\": \"quest_complete\",");
        pw.println("            \"text\": \"You found them? ...I see. The war takes and takes. Thank you for bringing word. Their names will be remembered here, at least. That\\u0027s more than most get.\",");
        pw.println("            \"choices\": [],");
        pw.println("            \"actions\": [");
        pw.println("              {\"type\": \"LOG_MESSAGE\", \"target\": \"Shield-Mother Brynn mourns the lost brothers.\", \"value\": \"GOOD\", \"amount\": 0},");
        pw.println("              {\"type\": \"CLOSE_DIALOGUE\", \"target\": \"\", \"value\": \"\", \"amount\": 0}");
        pw.println("            ]");
        pw.println("          },");
        // farewell
        pw.println("          \"farewell\": {");
        pw.println("            \"id\": \"farewell\",");
        pw.println("            \"text\": \"Be safe out there. The war doesn\\u0027t care whose side you\\u0027re on.\",");
        pw.println("            \"choices\": [],");
        pw.println("            \"actions\": [{\"type\": \"CLOSE_DIALOGUE\", \"target\": \"\", \"value\": \"\", \"amount\": 0}]");
        pw.println("          }");
        pw.println("        }");
        pw.println("      }");
        pw.println("    },");

        // NPC 2: Surgeon Hask — innkeeper (healer)
        pw.println("    {");
        pw.println("      \"id\": \"npc_iron_reckoner_hask\",");
        pw.println("      \"name\": \"Surgeon Hask\",");
        pw.println("      \"spriteName\": \"npcs/townsman\",");
        pw.println("      \"type\": \"INNKEEPER\",");
        pw.println("      \"defaultDialog\": \"Sit down. Let me look at your wounds. 20 gold for a full patch-up.\",");
        pw.println("      \"questCompleteDialog\": \"\",");
        pw.println("      \"shopItemIds\": [],");
        pw.println("      \"x\": 8,");
        pw.println("      \"y\": 14,");
        pw.println("      \"dialogueTree\": {");
        pw.println("        \"startNodeId\": \"greeting\",");
        pw.println("        \"nodes\": {");
        // greeting
        pw.println("          \"greeting\": {");
        pw.println("            \"id\": \"greeting\",");
        pw.println("            \"text\": \"Another one. Sit down, let me look at you. I\\u0027m Hask, the camp surgeon. I\\u0027ve stitched more wounds than I can count \\u2014 20 gold and I\\u0027ll have you battle-ready.\",");
        pw.println("            \"choices\": [");
        pw.println("              {\"label\": \"Patch me up. (Full heal, 20g)\", \"nextNodeId\": \"heal\", \"conditions\": [{\"type\": \"HAS_GOLD\", \"target\": \"\", \"value\": \"\", \"amount\": 20}]},");
        pw.println("              {\"label\": \"How bad are the casualties?\", \"nextNodeId\": \"lore\", \"conditions\": []},");
        pw.println("              {\"label\": \"I\\u0027m fine.\", \"nextNodeId\": \"farewell\", \"conditions\": []}");
        pw.println("            ],");
        pw.println("            \"actions\": []");
        pw.println("          },");
        // heal
        pw.println("          \"heal\": {");
        pw.println("            \"id\": \"heal\",");
        pw.println("            \"text\": \"Hold still. ...There. Good as new \\u2014 or as close to it as anyone gets in this war. Try not to come back too soon.\",");
        pw.println("            \"choices\": [],");
        pw.println("            \"actions\": [");
        pw.println("              {\"type\": \"GIVE_GOLD\", \"target\": \"\", \"value\": \"\", \"amount\": -20},");
        pw.println("              {\"type\": \"HEAL_PLAYER\", \"target\": \"\", \"value\": \"\", \"amount\": 0},");
        pw.println("              {\"type\": \"LOG_MESSAGE\", \"target\": \"Surgeon Hask patches your wounds. You feel restored.\", \"value\": \"GOOD\", \"amount\": 0},");
        pw.println("              {\"type\": \"CLOSE_DIALOGUE\", \"target\": \"\", \"value\": \"\", \"amount\": 0}");
        pw.println("            ]");
        pw.println("          },");
        // lore
        pw.println("          \"lore\": {");
        pw.println("            \"id\": \"lore\",");
        pw.println("            \"text\": \"Bad. Always bad. The strange thing is \\u2014 they never get worse. I lose the same number every month. Not more, not less. It\\u0027s like the war has a... rhythm. A quota. That\\u0027s not natural. Wars escalate or they end. This one does neither.\",");
        pw.println("            \"choices\": [");
        pw.println("              {\"label\": \"That is disturbing.\", \"nextNodeId\": \"farewell\", \"conditions\": []}");
        pw.println("            ],");
        pw.println("            \"actions\": []");
        pw.println("          },");
        // farewell
        pw.println("          \"farewell\": {");
        pw.println("            \"id\": \"farewell\",");
        pw.println("            \"text\": \"Keep your head down. I\\u0027d rather not see you on my table again.\",");
        pw.println("            \"choices\": [],");
        pw.println("            \"actions\": [{\"type\": \"CLOSE_DIALOGUE\", \"target\": \"\", \"value\": \"\", \"amount\": 0}]");
        pw.println("          }");
        pw.println("        }");
        pw.println("      }");
        pw.println("    },");

        // NPC 3: Forge-Hand Dara — shopkeeper
        pw.println("    {");
        pw.println("      \"id\": \"npc_iron_reckoner_dara\",");
        pw.println("      \"name\": \"Forge-Hand Dara\",");
        pw.println("      \"spriteName\": \"npcs/townsman\",");
        pw.println("      \"type\": \"SHOPKEEPER\",");
        pw.println("      \"defaultDialog\": \"Iron bends to my will. Tell me what you need.\",");
        pw.println("      \"questCompleteDialog\": \"\",");
        pw.println("      \"shopItemIds\": [\"iron_warsword\", \"battle_plate\", \"champion_shield\", \"war_tonic\"],");
        pw.println("      \"x\": 18,");
        pw.println("      \"y\": 14,");
        pw.println("      \"dialogueTree\": {");
        pw.println("        \"startNodeId\": \"greeting\",");
        pw.println("        \"nodes\": {");
        // greeting
        pw.println("          \"greeting\": {");
        pw.println("            \"id\": \"greeting\",");
        pw.println("            \"text\": \"I\\u0027m Dara. I shape iron into whatever the camp needs \\u2014 blades, armor, nails for the barricades. If you\\u0027ve got gold, I\\u0027ve got steel.\",");
        pw.println("            \"choices\": [");
        pw.println("              {\"label\": \"Show me what you\\u0027ve forged.\", \"nextNodeId\": \"shop\", \"conditions\": []},");
        pw.println("              {\"label\": \"How do you keep up with demand?\", \"nextNodeId\": \"lore\", \"conditions\": []},");
        pw.println("              {\"label\": \"Just passing through.\", \"nextNodeId\": \"farewell\", \"conditions\": []}");
        pw.println("            ],");
        pw.println("            \"actions\": []");
        pw.println("          },");
        // shop
        pw.println("          \"shop\": {");
        pw.println("            \"id\": \"shop\",");
        pw.println("            \"text\": \"Every piece forged by hand, tempered in war-fire. Take your pick.\",");
        pw.println("            \"choices\": [],");
        pw.println("            \"actions\": [{\"type\": \"OPEN_SHOP\", \"target\": \"\", \"value\": \"\", \"amount\": 0}]");
        pw.println("          },");
        // lore
        pw.println("          \"lore\": {");
        pw.println("            \"id\": \"lore\",");
        pw.println("            \"text\": \"I don\\u0027t. That\\u0027s the truth of it. Every blade I forge breaks within a week. Every shield dents beyond repair. And somehow there\\u0027s always just enough iron to keep going, never enough to get ahead. It\\u0027s like the island itself rations what we need.\",");
        pw.println("            \"choices\": [");
        pw.println("              {\"label\": \"Strange indeed.\", \"nextNodeId\": \"farewell\", \"conditions\": []}");
        pw.println("            ],");
        pw.println("            \"actions\": []");
        pw.println("          },");
        // farewell
        pw.println("          \"farewell\": {");
        pw.println("            \"id\": \"farewell\",");
        pw.println("            \"text\": \"Iron bends to my will. Come back when you need it.\",");
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

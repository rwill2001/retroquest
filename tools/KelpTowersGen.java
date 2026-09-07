import java.io.*;

/**
 * Generates data/towns/kelp_towers.rfmap — a 25x25 vertical kelp settlement.
 * Multi-level feel with kelp platforms and vine doorways.
 *
 * Run from project root:
 *   javac -encoding UTF-8 tools/KelpTowersGen.java -d tools/
 *   java -cp tools KelpTowersGen
 */
public class KelpTowersGen {

    static final int COLS = 25, ROWS = 25;
    static char[][] g = new char[ROWS][COLS];

    // Island 5 tile IDs
    static final char DOCEAN = '\uE070';  // deep ocean floor (exterior)
    static final char DFLOOR = '\uE07A';  // dome floor
    static final char CWALL  = '\uE07B';  // coral wall
    static final char KPLAT  = '\uE07C';  // kelp platform
    static final char PGLASS = '\uE07D';  // pressure glass
    static final char BLAMP  = '\uE07E';  // bioluminescent lamp
    static final char KFOREST = '\uE073'; // kelp forest (exterior)
    static final char PWARD  = '\uE077';  // pressure ward

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
    static final char VINE    = '\uE06C';  // vine curtain (doorway)

    public static void main(String[] args) throws Exception {

        // ── 1. Exterior: kelp forest everywhere ─────────────────────────
        fill(0, 24, 0, 24, KFOREST);

        // ── 2. Main structure — kelp platforms with coral walls ──────────
        // Vertical multi-tier layout: three "levels" connected by ramps
        // Lower tier (south, rows 16-23)
        fill(16, 23, 3, 21, KPLAT);
        hwall(16, 3, 21);
        vwall(3, 16, 23); vwall(21, 16, 23);
        hwall(23, 3, 21);

        // Middle tier (center, rows 8-15)
        fill(8, 15, 2, 22, KPLAT);
        hwall(8, 2, 22);
        vwall(2, 8, 15); vwall(22, 8, 15);

        // Upper tier (north, rows 2-7)
        fill(2, 7, 5, 19, KPLAT);
        hwall(2, 5, 19);
        vwall(5, 2, 7); vwall(19, 2, 7);
        hwall(7, 5, 19);

        // ── 3. Ramp connections between tiers ───────────────────────────
        // Upper-to-middle ramp (center)
        g[7][12] = DOOR;
        // Middle-to-lower ramp (center)
        g[16][12] = DOOR;

        // ── 4. Entry: south opening ─────────────────────────────────────
        g[23][12] = DOOR;
        g[24][12] = KPLAT;

        // ── 5. Vine curtain doorways ────────────────────────────────────
        g[8][12] = VINE;
        g[16][8] = VINE;
        g[16][16] = VINE;

        // ── 6. Lower tier — Merchant area (SW) ─────────────────────────
        vwall(11, 17, 22);
        g[19][11] = DOOR;
        g[17][4] = COUNTER; g[17][5] = COUNTER; g[17][6] = COUNTER;
        g[18][4] = BARREL; g[18][5] = BARREL;
        g[19][4] = SHELF; g[19][5] = SHELF;
        g[17][9] = BLAMP;

        // ── 7. Lower tier — Lore keeper area (SE) ──────────────────────
        g[17][13] = SHELF; g[17][14] = SHELF; g[17][15] = SHELF;
        g[18][13] = TABLE; g[18][14] = CHAIR;
        g[19][19] = BLAMP;
        g[21][18] = SHELF; g[21][19] = SHELF; g[21][20] = SHELF;

        // ── 8. Middle tier — Presskeeper Nym's chamber (west) ───────────
        vwall(10, 9, 14);
        g[11][10] = DOOR;
        g[9][3] = TABLE; g[9][4] = TABLE;
        g[10][3] = SHELF; g[10][4] = SHELF;
        g[11][3] = BARREL;
        g[9][7] = BLAMP;
        g[13][3] = ALTAR;
        g[13][7] = BLAMP;

        // ── 9. Middle tier — open area (east) ───────────────────────────
        g[10][15] = BLAMP; g[10][20] = BLAMP;
        g[13][15] = BLAMP; g[13][20] = BLAMP;
        // Decorative coral pillars
        g[11][16] = CWALL; g[11][19] = CWALL;
        g[13][16] = CWALL; g[13][19] = CWALL;

        // ── 10. Upper tier — observation deck ───────────────────────────
        g[3][6] = PGLASS; g[3][18] = PGLASS;
        g[4][6] = PGLASS; g[4][18] = PGLASS;
        g[5][12] = ALTAR;
        g[3][12] = BLAMP;
        g[6][8] = BLAMP; g[6][16] = BLAMP;

        // ── 11. Bioluminescent lamps throughout ─────────────────────────
        g[20][6] = BLAMP; g[20][17] = BLAMP;
        g[22][12] = BLAMP;

        // ── Write JSON ──────────────────────────────────────────────────
        writeJSON("data/towns/kelp_towers.rfmap");
        System.out.println("Done \u2014 data/towns/kelp_towers.rfmap written.");
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
        pw.println("  \"name\": \"kelp_towers\",");
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

        // NPC 1: Presskeeper Nym — questgiver (Deep Reading + Wraith Bounty)
        pw.println("    {");
        pw.println("      \"id\": \"npc_kelptowers_nym\",");
        pw.println("      \"name\": \"Presskeeper Nym\",");
        pw.println("      \"spriteName\": \"npcs/townsman\",");
        pw.println("      \"type\": \"QUESTGIVER\",");
        pw.println("      \"defaultDialog\": \"The deep reads us all. The question is whether we read it back.\",");
        pw.println("      \"questCompleteDialog\": \"\",");
        pw.println("      \"shopItemIds\": [],");
        pw.println("      \"x\": 6,");
        pw.println("      \"y\": 12,");
        pw.println("      \"dialogueTree\": {");
        pw.println("        \"startNodeId\": \"greeting\",");
        pw.println("        \"nodes\": {");
        // greeting
        pw.println("          \"greeting\": {");
        pw.println("            \"id\": \"greeting\",");
        pw.println("            \"text\": \"I am Nym, keeper of the deep\\u0027s pressure \\u2014 and its secrets. The Pressure Temple below holds truths that Thalorax guards jealously. I\\u0027ve spent decades trying to read them.\",");
        pw.println("            \"choices\": [");
        pw.println("              {\"label\": \"What truths are in the temple?\", \"nextNodeId\": \"temple_info\", \"conditions\": []},");
        pw.println("              {\"label\": \"I\\u0027ll help you investigate. [The Deep Reading]\", \"nextNodeId\": \"deep_reading_offer\", \"conditions\": [{\"type\": \"FLAG_NOT_SET\", \"target\": \"deep_reading_accepted\", \"value\": \"\", \"amount\": 0}]},");
        pw.println("              {\"label\": \"Any work that pays? [Wraith Bounty]\", \"nextNodeId\": \"bounty_offer\", \"conditions\": [{\"type\": \"FLAG_NOT_SET\", \"target\": \"wraith_bounty_accepted\", \"value\": \"\", \"amount\": 0}]},");
        pw.println("              {\"label\": \"[Quest Active] I\\u0027m working on the Deep Reading.\", \"nextNodeId\": \"reading_progress\", \"conditions\": [{\"type\": \"QUEST_ACTIVE\", \"target\": \"the_deep_reading\", \"value\": \"\", \"amount\": 0}]},");
        pw.println("              {\"label\": \"[Quest Active] Hunting wraiths for the bounty.\", \"nextNodeId\": \"bounty_progress\", \"conditions\": [{\"type\": \"QUEST_ACTIVE\", \"target\": \"abyssal_wraith_bounty\", \"value\": \"\", \"amount\": 0}]},");
        pw.println("              {\"label\": \"Farewell, Presskeeper.\", \"nextNodeId\": \"farewell\", \"conditions\": []}");
        pw.println("            ],");
        pw.println("            \"actions\": []");
        pw.println("          },");
        // temple_info
        pw.println("          \"temple_info\": {");
        pw.println("            \"id\": \"temple_info\",");
        pw.println("            \"text\": \"The Pressure Temple is Thalorax\\u0027s sanctum. Within it lies knowledge of the Shattering \\u2014 why the world broke, how the gods divided. Thalorax knows the answer, but he crushes anyone who gets too close. The abyssal wraiths are his sentinels.\",");
        pw.println("            \"choices\": [");
        pw.println("              {\"label\": \"I\\u0027ll investigate for you.\", \"nextNodeId\": \"deep_reading_offer\", \"conditions\": [{\"type\": \"FLAG_NOT_SET\", \"target\": \"deep_reading_accepted\", \"value\": \"\", \"amount\": 0}]},");
        pw.println("              {\"label\": \"Interesting.\", \"nextNodeId\": \"farewell\", \"conditions\": []}");
        pw.println("            ],");
        pw.println("            \"actions\": []");
        pw.println("          },");
        // deep_reading_offer
        pw.println("          \"deep_reading_offer\": {");
        pw.println("            \"id\": \"deep_reading_offer\",");
        pw.println("            \"text\": \"Enter the Pressure Temple and find the inscription chamber. Read what\\u0027s written on the walls and return to me. Be warned \\u2014 Thalorax does not share his knowledge willingly.\",");
        pw.println("            \"choices\": [],");
        pw.println("            \"actions\": [");
        pw.println("              {\"type\": \"GIVE_QUEST\", \"target\": \"the_deep_reading\", \"value\": \"\", \"amount\": 0},");
        pw.println("              {\"type\": \"SET_FLAG\", \"target\": \"deep_reading_accepted\", \"value\": \"true\", \"amount\": 0},");
        pw.println("              {\"type\": \"LOG_MESSAGE\", \"target\": \"Quest accepted: The Deep Reading\", \"value\": \"GOOD\", \"amount\": 0},");
        pw.println("              {\"type\": \"CLOSE_DIALOGUE\", \"target\": \"\", \"value\": \"\", \"amount\": 0}");
        pw.println("            ]");
        pw.println("          },");
        // bounty_offer
        pw.println("          \"bounty_offer\": {");
        pw.println("            \"id\": \"bounty_offer\",");
        pw.println("            \"text\": \"The abyssal wraiths grow bolder. They\\u0027ve been drifting closer to Kelp Towers, testing our wards. Kill five of them and I\\u0027ll make it worth your while.\",");
        pw.println("            \"choices\": [],");
        pw.println("            \"actions\": [");
        pw.println("              {\"type\": \"GIVE_QUEST\", \"target\": \"abyssal_wraith_bounty\", \"value\": \"\", \"amount\": 0},");
        pw.println("              {\"type\": \"SET_FLAG\", \"target\": \"wraith_bounty_accepted\", \"value\": \"true\", \"amount\": 0},");
        pw.println("              {\"type\": \"LOG_MESSAGE\", \"target\": \"Quest accepted: Abyssal Wraith Bounty\", \"value\": \"GOOD\", \"amount\": 0},");
        pw.println("              {\"type\": \"CLOSE_DIALOGUE\", \"target\": \"\", \"value\": \"\", \"amount\": 0}");
        pw.println("            ]");
        pw.println("          },");
        // reading_progress
        pw.println("          \"reading_progress\": {");
        pw.println("            \"id\": \"reading_progress\",");
        pw.println("            \"text\": \"The temple awaits. Find the inscriptions and return. The pressure down there \\u2014 it\\u0027s more than physical. Be prepared.\",");
        pw.println("            \"choices\": [");
        pw.println("              {\"label\": \"I\\u0027ll press on.\", \"nextNodeId\": \"farewell\", \"conditions\": []}");
        pw.println("            ],");
        pw.println("            \"actions\": []");
        pw.println("          },");
        // bounty_progress
        pw.println("          \"bounty_progress\": {");
        pw.println("            \"id\": \"bounty_progress\",");
        pw.println("            \"text\": \"Keep hunting those wraiths. Five of them, and the deep will think twice before testing us again.\",");
        pw.println("            \"choices\": [");
        pw.println("              {\"label\": \"On it.\", \"nextNodeId\": \"farewell\", \"conditions\": []}");
        pw.println("            ],");
        pw.println("            \"actions\": []");
        pw.println("          },");
        // farewell
        pw.println("          \"farewell\": {");
        pw.println("            \"id\": \"farewell\",");
        pw.println("            \"text\": \"The pressure never rests. Neither should you.\",");
        pw.println("            \"choices\": [],");
        pw.println("            \"actions\": [{\"type\": \"CLOSE_DIALOGUE\", \"target\": \"\", \"value\": \"\", \"amount\": 0}]");
        pw.println("          }");
        pw.println("        }");
        pw.println("      }");
        pw.println("    },");

        // NPC 2: Kelp Towers Merchant
        pw.println("    {");
        pw.println("      \"id\": \"npc_kelptowers_merchant\",");
        pw.println("      \"name\": \"Kelp Weaver\",");
        pw.println("      \"spriteName\": \"npcs/townsman\",");
        pw.println("      \"type\": \"SHOPKEEPER\",");
        pw.println("      \"defaultDialog\": \"Woven from the deep. Stronger than it looks.\",");
        pw.println("      \"questCompleteDialog\": \"\",");
        pw.println("      \"shopItemIds\": [\"coral_shield\", \"deep_trident\", \"abyssal_healing_draught\", \"healing_potion\", \"greater_healing_potion\", \"antidote\"],");
        pw.println("      \"x\": 7,");
        pw.println("      \"y\": 19,");
        pw.println("      \"dialogueTree\": {");
        pw.println("        \"startNodeId\": \"greeting\",");
        pw.println("        \"nodes\": {");
        pw.println("          \"greeting\": {");
        pw.println("            \"id\": \"greeting\",");
        pw.println("            \"text\": \"Kelp\\u0027s tougher than steel at this depth. I weave it, cure it, shape it. Need something woven?\",");
        pw.println("            \"choices\": [");
        pw.println("              {\"label\": \"Show me your wares.\", \"nextNodeId\": \"shop\", \"conditions\": []},");
        pw.println("              {\"label\": \"No thanks.\", \"nextNodeId\": \"farewell\", \"conditions\": []}");
        pw.println("            ],");
        pw.println("            \"actions\": []");
        pw.println("          },");
        pw.println("          \"shop\": {");
        pw.println("            \"id\": \"shop\",");
        pw.println("            \"text\": \"Everything\\u0027s deep-cured. Won\\u0027t rot, won\\u0027t rust.\",");
        pw.println("            \"choices\": [],");
        pw.println("            \"actions\": [{\"type\": \"OPEN_SHOP\", \"target\": \"\", \"value\": \"\", \"amount\": 0}]");
        pw.println("          },");
        pw.println("          \"farewell\": {");
        pw.println("            \"id\": \"farewell\",");
        pw.println("            \"text\": \"Mind the currents between the towers.\",");
        pw.println("            \"choices\": [],");
        pw.println("            \"actions\": [{\"type\": \"CLOSE_DIALOGUE\", \"target\": \"\", \"value\": \"\", \"amount\": 0}]");
        pw.println("          }");
        pw.println("        }");
        pw.println("      }");
        pw.println("    },");

        // NPC 3: Ancient Kelp-Keeper (lore)
        pw.println("    {");
        pw.println("      \"id\": \"npc_kelptowers_keeper\",");
        pw.println("      \"name\": \"The Ancient Kelp-Keeper\",");
        pw.println("      \"spriteName\": \"npcs/townsman\",");
        pw.println("      \"type\": \"TOWNSFOLK\",");
        pw.println("      \"defaultDialog\": \"The kelp remembers what stone forgets.\",");
        pw.println("      \"questCompleteDialog\": \"\",");
        pw.println("      \"shopItemIds\": [],");
        pw.println("      \"x\": 17,");
        pw.println("      \"y\": 19,");
        pw.println("      \"dialogueTree\": {");
        pw.println("        \"startNodeId\": \"greeting\",");
        pw.println("        \"nodes\": {");
        pw.println("          \"greeting\": {");
        pw.println("            \"id\": \"greeting\",");
        pw.println("            \"text\": \"I\\u0027ve tended these towers since before the dome-folk came. The kelp was here first \\u2014 it grew from the bones of the leviathans that fell in the Shattering. Each strand holds a memory.\",");
        pw.println("            \"choices\": [");
        pw.println("              {\"label\": \"Tell me about the leviathans.\", \"nextNodeId\": \"leviathans\", \"conditions\": []},");
        pw.println("              {\"label\": \"What do you know about Thalorax?\", \"nextNodeId\": \"thalorax\", \"conditions\": []},");
        pw.println("              {\"label\": \"Fascinating.\", \"nextNodeId\": \"farewell\", \"conditions\": []}");
        pw.println("            ],");
        pw.println("            \"actions\": []");
        pw.println("          },");
        pw.println("          \"leviathans\": {");
        pw.println("            \"id\": \"leviathans\",");
        pw.println("            \"text\": \"Creatures vast enough to blot out the bioluminescence. When the world shattered, they died \\u2014 their bones became the trenches, their blood became the pressure vents. The Boneyard Trench to the south is one such grave. The wraiths that haunt it are their echoes.\",");
        pw.println("            \"choices\": [");
        pw.println("              {\"label\": \"And Thalorax?\", \"nextNodeId\": \"thalorax\", \"conditions\": []},");
        pw.println("              {\"label\": \"I see.\", \"nextNodeId\": \"farewell\", \"conditions\": []}");
        pw.println("            ],");
        pw.println("            \"actions\": []");
        pw.println("          },");
        pw.println("          \"thalorax\": {");
        pw.println("            \"id\": \"thalorax\",");
        pw.println("            \"text\": \"Thalorax is not evil. He is inevitable. Pressure, darkness, the weight of knowledge too heavy to bear \\u2014 that is his nature. He crushes because that is what depth does. But he also preserves. The deepest things endure the longest.\",");
        pw.println("            \"choices\": [");
        pw.println("              {\"label\": \"A strange god.\", \"nextNodeId\": \"farewell\", \"conditions\": []}");
        pw.println("            ],");
        pw.println("            \"actions\": []");
        pw.println("          },");
        pw.println("          \"farewell\": {");
        pw.println("            \"id\": \"farewell\",");
        pw.println("            \"text\": \"Listen to the kelp. It sways with truths the stone won\\u0027t tell.\",");
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

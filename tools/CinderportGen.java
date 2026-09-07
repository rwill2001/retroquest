import java.io.*;

/**
 * Generates data/towns/cinderport.rfmap — a 30x30 volcanic harbor town.
 *
 * Cinderport is the arrival point for Island 2. Built on volcanic rock
 * with a harbor to the north, forge district, and obsidian-floored buildings.
 *
 * Layout:
 *   - Rows 0-3: Water (harbor/ocean)
 *   - Rows 4-5: Volcanic sand (beach/dock)
 *   - Row 6: Volcanic rock border
 *   - Rows 7-29: Main town (stone wall perimeter, obsidian interiors)
 *   - Buildings: Harbor Master, Forge Shop, Inn, General Store, open plaza
 *
 * Run from project root:
 *   javac tools/CinderportGen.java -d tools/
 *   java -cp tools CinderportGen
 */
public class CinderportGen {

    static final int COLS = 30, ROWS = 30;
    static char[][] g = new char[ROWS][COLS];

    // Tile IDs
    static final char VROCK   = '!';  // volcanic rock (exterior ground)
    static final char OBSID   = '(';  // obsidian floor (interior)
    static final char FFLOOR  = '*';  // forge floor
    static final char WALL    = 'W';  // stone wall
    static final char DOOR    = 'd';  // wooden door
    static final char WATER   = '~';  // water (harbor)
    static final char COUNTER = 'c';  // counter
    static final char BARREL  = 'n';  // barrel
    static final char BED     = 'l';  // bed
    static final char FIRE    = 'u';  // fireplace
    static final char TABLE   = 'i';  // table
    static final char CHAIR   = 'j';  // chair
    static final char SHELF   = 'k';  // bookshelf
    static final char FORGE   = 'N';  // forge
    static final char TORCH   = 'v';  // wall torch
    static final char EGRATE  = '+';  // ember grate
    static final char LCHAN   = '-';  // lava channel
    static final char VSAND   = ':';  // volcanic sand (beach)
    static final char PLANT   = 'Y';  // plant

    public static void main(String[] args) throws Exception {

        // ── 1. Base layer: volcanic rock everywhere ────────────────────────
        fill(0, 29, 0, 29, VROCK);

        // ── 2. Harbor / water (rows 0-3) ───────────────────────────────────
        fill(0, 3, 0, 29, WATER);

        // ── 3. Beach / volcanic sand (rows 4-5) ───────────────────────────
        fill(4, 5, 0, 29, VSAND);

        // Dock area — wooden planks represented as obsidian for contrast
        // Dock pier extending into water at cols 13-16
        g[3][13] = VSAND;  g[3][14] = VSAND;  g[3][15] = VSAND;  g[3][16] = VSAND;
        g[2][14] = VSAND;  g[2][15] = VSAND;

        // Barrels on the dock
        g[4][10] = BARREL;  g[4][11] = BARREL;
        g[4][18] = BARREL;  g[4][19] = BARREL;
        g[5][10] = BARREL;  g[5][19] = BARREL;

        // ── 4. Lava channels flanking the town (decorative) ────────────────
        // West lava channel
        for (int r = 7; r <= 29; r++) g[r][0] = LCHAN;
        for (int r = 7; r <= 29; r++) g[r][1] = LCHAN;
        // East lava channel
        for (int r = 7; r <= 29; r++) g[r][28] = LCHAN;
        for (int r = 7; r <= 29; r++) g[r][29] = LCHAN;

        // ── 5. Perimeter wall (rows 7 and 29, cols 2-27) ──────────────────
        hwall(7,  2, 27);
        hwall(29, 2, 27);
        vwall(2,  7, 29);
        vwall(27, 7, 29);

        // Entry gap at north wall (from beach) — cols 13-16
        g[7][13] = DOOR;  g[7][14] = DOOR;  g[7][15] = DOOR;  g[7][16] = DOOR;

        // Exit gap at south wall — cols 14-15 (town exit to overworld)
        g[29][14] = DOOR;  g[29][15] = DOOR;

        // ── 6. Interior volcanic rock floor ────────────────────────────────
        fill(8, 28, 3, 26, VROCK);

        // ── 7. Torch-lit border inside walls ───────────────────────────────
        g[8][4]  = TORCH;  g[8][12] = TORCH;  g[8][17] = TORCH;  g[8][25] = TORCH;
        g[28][4] = TORCH;  g[28][12] = TORCH;  g[28][17] = TORCH;  g[28][25] = TORCH;
        g[14][3] = TORCH;  g[20][3] = TORCH;  g[26][3] = TORCH;
        g[14][26] = TORCH; g[20][26] = TORCH; g[26][26] = TORCH;

        // ── 8. BUILDING 1: Harbor Master's Office (rows 8-13, cols 3-10) ──
        hwall(8,  3, 10);
        hwall(13, 3, 10);
        vwall(3,  8, 13);
        vwall(10, 8, 13);
        g[13][7] = DOOR;   // south door

        fill(9, 12, 4, 9, OBSID);  // interior floor

        // Furnishings
        for (int c = 4; c <= 8; c++) g[9][c] = COUNTER;   // counter along north
        g[9][9]  = SHELF;
        g[10][4] = SHELF;  g[11][4] = SHELF;              // shelves on west wall
        g[12][9] = BARREL; g[12][8] = BARREL;             // barrels in SE corner
        g[11][9] = BARREL;
        g[10][7] = TABLE;  g[10][8] = TABLE;              // desk

        // Clear NPC position (Voss at 6,10)
        g[10][6] = OBSID;

        // ── 9. BUILDING 2: Forge Shop (rows 8-13, cols 14-21) ─────────────
        hwall(8,  14, 21);
        hwall(13, 14, 21);
        vwall(14, 8, 13);
        vwall(21, 8, 13);
        g[13][17] = DOOR;  // south door

        fill(9, 12, 15, 20, FFLOOR);  // forge floor interior

        // Forge equipment
        g[9][15]  = FORGE;  g[9][16]  = FORGE;            // forges at north
        g[9][19]  = FORGE;  g[9][20]  = FORGE;
        g[10][15] = EGRATE; g[10][20] = EGRATE;           // ember grates
        for (int c = 16; c <= 19; c++) g[11][c] = COUNTER; // display counter
        g[12][15] = BARREL; g[12][20] = BARREL;           // material barrels

        // Clear NPC position (Kira at 17,10)
        g[10][17] = FFLOOR;

        // ── 10. BUILDING 3: Inn (rows 8-13, cols 22-27) ───────────────────
        hwall(8,  22, 27);
        hwall(13, 22, 27);
        vwall(22, 8, 13);
        // East wall is already the perimeter wall at col 27

        g[13][24] = DOOR;  // south door

        fill(9, 12, 23, 26, OBSID);  // interior floor

        // Furnishings
        g[9][23]  = BED;   g[9][24]  = BED;              // beds along north
        g[10][23] = BED;   g[10][24] = BED;
        g[9][26]  = BED;   g[10][26] = BED;
        g[11][26] = FIRE;                                  // fireplace
        for (int c = 23; c <= 25; c++) g[12][c] = COUNTER; // innkeeper counter

        // Clear NPC position (Innkeeper at 24,10)
        g[10][24] = OBSID;

        // ── 11. Open plaza (rows 15-17, cols 8-21) ────────────────────────
        fill(15, 17, 8, 21, OBSID);

        // Torches and plants in plaza
        g[15][8]  = TORCH;  g[15][21] = TORCH;
        g[17][8]  = TORCH;  g[17][21] = TORCH;
        g[16][10] = PLANT;  g[16][19] = PLANT;
        g[15][14] = EGRATE; g[15][15] = EGRATE;           // central ember grate
        g[17][14] = EGRATE; g[17][15] = EGRATE;

        // ── 12. BUILDING 4: General Store (rows 18-25, cols 3-10) ─────────
        hwall(18, 3, 10);
        hwall(25, 3, 10);
        vwall(3,  18, 25);
        vwall(10, 18, 25);
        g[18][7] = DOOR;   // north door

        fill(19, 24, 4, 9, OBSID);  // interior floor

        // Furnishings
        for (int c = 4; c <= 8; c++) g[19][c] = COUNTER;  // counter along north
        g[20][4] = SHELF;  g[21][4] = SHELF;  g[22][4] = SHELF;  // shelves west
        g[20][9] = SHELF;  g[21][9] = SHELF;  g[22][9] = SHELF;  // shelves east
        g[23][5] = BARREL; g[23][6] = BARREL; g[23][7] = BARREL; // barrels south
        g[24][5] = BARREL; g[24][6] = BARREL; g[24][7] = BARREL;
        g[24][4] = PLANT;  g[24][9] = PLANT;                     // plants

        // Clear NPC position (Bryn at 6,22)
        g[22][6] = OBSID;

        // ── 13. South-east open area: ember grates and lava feel ──────────
        // Decorative lava channels / ember grates in SE area
        fill(20, 24, 18, 24, VROCK);   // clear area
        g[21][19] = EGRATE;  g[21][23] = EGRATE;
        g[23][19] = EGRATE;  g[23][23] = EGRATE;
        g[22][21] = LCHAN;                                 // central lava feature

        // Plants and torches near south exit
        g[28][12] = TORCH;  g[28][17] = TORCH;
        g[27][13] = PLANT;  g[27][16] = PLANT;

        // ── 14. Write JSON ─────────────────────────────────────────────────
        writeJSON("data/towns/cinderport.rfmap");
        System.out.println("Done — data/towns/cinderport.rfmap written.");
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    static void fill(int r1, int r2, int c1, int c2, char tile) {
        for (int r = r1; r <= r2; r++)
            for (int c = c1; c <= c2; c++)
                g[r][c] = tile;
    }

    static void hwall(int row, int c1, int c2) {
        for (int c = c1; c <= c2; c++) g[row][c] = WALL;
    }

    static void vwall(int col, int r1, int r2) {
        for (int r = r1; r <= r2; r++) g[r][col] = WALL;
    }

    // ── JSON output ────────────────────────────────────────────────────────

    static void writeJSON(String path) throws Exception {
        new File(path).getParentFile().mkdirs();
        PrintWriter pw = new PrintWriter(new FileWriter(path));
        pw.println("{");
        pw.println("  \"type\": \"TOWN\",");
        pw.println("  \"name\": \"cinderport\",");
        pw.println("  \"width\": 30,");
        pw.println("  \"height\": 30,");
        pw.println("  \"interiorEntryX\": 14,");
        pw.println("  \"interiorEntryY\": 28,");
        pw.println("  \"tiles\": [");
        for (int r = 0; r < ROWS; r++) {
            pw.print("    [");
            for (int c = 0; c < COLS; c++) {
                char ch = g[r][c];
                String s = "\"" + (ch == '\'' ? "'" : ch == '"' ? "\\\"" : String.valueOf(ch)) + "\"";
                pw.print(s);
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

        // ── NPC 1: Harbor Captain Voss (with dialogue tree) ────────────────
        pw.println("    {");
        pw.println("      \"id\": \"npc_cinderport_voss\",");
        pw.println("      \"name\": \"Harbor Captain Voss\",");
        pw.println("      \"spriteName\": \"npcs/townsman\",");
        pw.println("      \"type\": \"QUESTGIVER\",");
        pw.println("      \"defaultDialog\": \"\",");
        pw.println("      \"questCompleteDialog\": \"\",");
        pw.println("      \"shopItemIds\": [],");
        pw.println("      \"x\": 6,");
        pw.println("      \"y\": 10,");
        pw.println("      \"dialogueTree\": {");
        pw.println("        \"startNodeId\": \"greeting\",");
        pw.println("        \"nodes\": {");
        // greeting
        pw.println("          \"greeting\": {");
        pw.println("            \"id\": \"greeting\",");
        pw.println("            \"text\": \"Welcome to Cinderport, stranger. I\\u0027m Captain Voss — I run the harbor. Not many ships come through anymore, not since the forge went quiet.\",");
        pw.println("            \"choices\": [");
        pw.println("              {\"label\": \"What happened to the forge?\", \"nextNodeId\": \"forge_info\", \"conditions\": []},");
        pw.println("              {\"label\": \"I\\u0027m looking for work.\", \"nextNodeId\": \"work\", \"conditions\": []},");
        pw.println("              {\"label\": \"Just passing through.\", \"nextNodeId\": \"farewell\", \"conditions\": []}");
        pw.println("            ],");
        pw.println("            \"actions\": []");
        pw.println("          },");
        // forge_info
        pw.println("          \"forge_info\": {");
        pw.println("            \"id\": \"forge_info\",");
        pw.println("            \"text\": \"The old forgemaster vanished weeks ago. Kira\\u0027s been keeping things running, but she\\u0027s stretched thin. Without his expertise, the obsidian blades are losing their edge.\",");
        pw.println("            \"choices\": [");
        pw.println("              {\"label\": \"Where did he go?\", \"nextNodeId\": \"bribe_hint\", \"conditions\": []},");
        pw.println("              {\"label\": \"I\\u0027ll talk to Kira.\", \"nextNodeId\": \"farewell\", \"conditions\": []}");
        pw.println("            ],");
        pw.println("            \"actions\": []");
        pw.println("          },");
        // bribe_hint
        pw.println("          \"bribe_hint\": {");
        pw.println("            \"id\": \"bribe_hint\",");
        pw.println("            \"text\": \"Some say he went into the volcanic caves east of town. Others say he was taken. A dockhand saw torchlight near the old lava tubes... but he won\\u0027t talk unless you grease his palm. Try asking Kira — she knew the old man best.\",");
        pw.println("            \"choices\": [");
        pw.println("              {\"label\": \"Thanks for the tip.\", \"nextNodeId\": \"farewell\", \"conditions\": []}");
        pw.println("            ],");
        pw.println("            \"actions\": []");
        pw.println("          },");
        // work
        pw.println("          \"work\": {");
        pw.println("            \"id\": \"work\",");
        pw.println("            \"text\": \"Work? Ha! Plenty of trouble to go around. Talk to Kira at the forge — she\\u0027s been asking everyone who docks here for help. Something about a missing person.\",");
        pw.println("            \"choices\": [");
        pw.println("              {\"label\": \"I\\u0027ll check it out.\", \"nextNodeId\": \"farewell\", \"conditions\": []}");
        pw.println("            ],");
        pw.println("            \"actions\": []");
        pw.println("          },");
        // farewell
        pw.println("          \"farewell\": {");
        pw.println("            \"id\": \"farewell\",");
        pw.println("            \"text\": \"Watch yourself out there. The ground\\u0027s not as stable as it looks on this island.\",");
        pw.println("            \"choices\": [],");
        pw.println("            \"actions\": [{\"type\": \"CLOSE_DIALOGUE\", \"target\": \"\", \"value\": \"\", \"amount\": 0}]");
        pw.println("          }");
        pw.println("        }");
        pw.println("      }");
        pw.println("    },");

        // ── NPC 2: Forgemaster Kira (with dialogue tree) ───────────────────
        pw.println("    {");
        pw.println("      \"id\": \"npc_cinderport_kira\",");
        pw.println("      \"name\": \"Forgemaster Kira\",");
        pw.println("      \"spriteName\": \"npcs/shopkeeper\",");
        pw.println("      \"type\": \"SHOPKEEPER\",");
        pw.println("      \"defaultDialog\": \"\",");
        pw.println("      \"questCompleteDialog\": \"\",");
        pw.println("      \"shopItemIds\": [\"obsidian_blade\", \"forged_steel_plate\", \"ember_shield\", \"healing_potion\", \"pyralis_fire_potion\"],");
        pw.println("      \"x\": 17,");
        pw.println("      \"y\": 10,");
        pw.println("      \"dialogueTree\": {");
        pw.println("        \"startNodeId\": \"greeting\",");
        pw.println("        \"nodes\": {");
        // greeting
        pw.println("          \"greeting\": {");
        pw.println("            \"id\": \"greeting\",");
        pw.println("            \"text\": \"The forge burns day and night. I\\u0027m Kira — I keep Cinderport armed and armored. What brings you to my shop?\",");
        pw.println("            \"choices\": [");
        pw.println("              {\"label\": \"I\\u0027d like to browse your wares.\", \"nextNodeId\": \"shop\", \"conditions\": []},");
        pw.println("              {\"label\": \"I heard about a missing forger.\", \"nextNodeId\": \"quest_intro\", \"conditions\": [{\"type\": \"QUEST_ACTIVE\", \"target\": \"the_missing_forger\", \"value\": \"\", \"amount\": 0, \"negate\": true}, {\"type\": \"QUEST_COMPLETE\", \"target\": \"the_missing_forger\", \"value\": \"\", \"amount\": 0, \"negate\": true}]},");
        pw.println("              {\"label\": \"Any news about the missing forger?\", \"nextNodeId\": \"quest_active\", \"conditions\": [{\"type\": \"QUEST_ACTIVE\", \"target\": \"the_missing_forger\", \"value\": \"\", \"amount\": 0}]},");
        pw.println("              {\"label\": \"Tell me about this island.\", \"nextNodeId\": \"lore\", \"conditions\": []},");
        pw.println("              {\"label\": \"Farewell.\", \"nextNodeId\": \"farewell\", \"conditions\": []}");
        pw.println("            ],");
        pw.println("            \"actions\": []");
        pw.println("          },");
        // shop
        pw.println("          \"shop\": {");
        pw.println("            \"id\": \"shop\",");
        pw.println("            \"text\": \"Take a look. Everything\\u0027s forged right here in Cinderport\\u0027s own fires.\",");
        pw.println("            \"choices\": [],");
        pw.println("            \"actions\": [{\"type\": \"OPEN_SHOP\", \"target\": \"\", \"value\": \"\", \"amount\": 0}]");
        pw.println("          },");
        // quest_intro
        pw.println("          \"quest_intro\": {");
        pw.println("            \"id\": \"quest_intro\",");
        pw.println("            \"text\": \"My mentor, Master Aldric, disappeared three weeks ago. He went to harvest obsidian from the volcanic caves and never returned. I\\u0027ve been running the forge alone, but I can\\u0027t keep this up. Please — find him and bring him back.\",");
        pw.println("            \"choices\": [");
        pw.println("              {\"label\": \"I\\u0027ll find him.\", \"nextNodeId\": \"quest_accepted\", \"conditions\": []},");
        pw.println("              {\"label\": \"Not right now.\", \"nextNodeId\": \"farewell\", \"conditions\": []}");
        pw.println("            ],");
        pw.println("            \"actions\": []");
        pw.println("          },");
        // quest_accepted
        pw.println("          \"quest_accepted\": {");
        pw.println("            \"id\": \"quest_accepted\",");
        pw.println("            \"text\": \"Thank the flames. The volcanic caves are east of town — be careful, the tunnels shift with the heat. Aldric always carried a firesteel pendant. If you find it, you\\u0027ll know you\\u0027re on his trail.\",");
        pw.println("            \"choices\": [");
        pw.println("              {\"label\": \"I\\u0027ll head out.\", \"nextNodeId\": \"farewell\", \"conditions\": []}");
        pw.println("            ],");
        pw.println("            \"actions\": [{\"type\": \"GIVE_QUEST\", \"target\": \"the_missing_forger\", \"value\": \"\", \"amount\": 0}]");
        pw.println("          },");
        // quest_active
        pw.println("          \"quest_active\": {");
        pw.println("            \"id\": \"quest_active\",");
        pw.println("            \"text\": \"No word yet. The volcanic caves are dangerous — please hurry. Every day without Aldric, the forge grows colder.\",");
        pw.println("            \"choices\": [");
        pw.println("              {\"label\": \"I\\u0027m working on it.\", \"nextNodeId\": \"farewell\", \"conditions\": []}");
        pw.println("            ],");
        pw.println("            \"actions\": []");
        pw.println("          },");
        // lore
        pw.println("          \"lore\": {");
        pw.println("            \"id\": \"lore\",");
        pw.println("            \"text\": \"This island was born from fire. Pyralis\\u0027s breath, the old folk say. The volcano hasn\\u0027t erupted in a century, but the earth still rumbles. The obsidian here is the finest in the realm — harder than steel when properly forged.\",");
        pw.println("            \"choices\": [");
        pw.println("              {\"label\": \"Interesting. Let me see your shop.\", \"nextNodeId\": \"shop\", \"conditions\": []},");
        pw.println("              {\"label\": \"Thanks for the history.\", \"nextNodeId\": \"farewell\", \"conditions\": []}");
        pw.println("            ],");
        pw.println("            \"actions\": []");
        pw.println("          },");
        // farewell
        pw.println("          \"farewell\": {");
        pw.println("            \"id\": \"farewell\",");
        pw.println("            \"text\": \"May the forge\\u0027s fire light your path.\",");
        pw.println("            \"choices\": [],");
        pw.println("            \"actions\": [{\"type\": \"CLOSE_DIALOGUE\", \"target\": \"\", \"value\": \"\", \"amount\": 0}]");
        pw.println("          }");
        pw.println("        }");
        pw.println("      }");
        pw.println("    },");

        // ── NPC 3: Cinderport Innkeeper (with dialogue tree) ───────────────
        pw.println("    {");
        pw.println("      \"id\": \"npc_cinderport_innkeeper\",");
        pw.println("      \"name\": \"Cinderport Innkeeper\",");
        pw.println("      \"spriteName\": \"npcs/innkeeper\",");
        pw.println("      \"type\": \"INNKEEPER\",");
        pw.println("      \"defaultDialog\": \"\",");
        pw.println("      \"questCompleteDialog\": \"\",");
        pw.println("      \"shopItemIds\": [],");
        pw.println("      \"x\": 24,");
        pw.println("      \"y\": 10,");
        pw.println("      \"dialogueTree\": {");
        pw.println("        \"startNodeId\": \"greeting\",");
        pw.println("        \"nodes\": {");
        // greeting
        pw.println("          \"greeting\": {");
        pw.println("            \"id\": \"greeting\",");
        pw.println("            \"text\": \"Welcome, weary traveler. The beds are simple but the walls are thick — keeps out the heat and the noise. What\\u0027ll it be?\",");
        pw.println("            \"choices\": [");
        pw.println("              {\"label\": \"I\\u0027d like to rest.\", \"nextNodeId\": \"rest\", \"conditions\": []},");
        pw.println("              {\"label\": \"Any local gossip?\", \"nextNodeId\": \"gossip\", \"conditions\": []},");
        pw.println("              {\"label\": \"Nothing, thanks.\", \"nextNodeId\": \"farewell\", \"conditions\": []}");
        pw.println("            ],");
        pw.println("            \"actions\": []");
        pw.println("          },");
        // rest
        pw.println("          \"rest\": {");
        pw.println("            \"id\": \"rest\",");
        pw.println("            \"text\": \"Sleep well. The forge\\u0027s glow will keep the dark things at bay.\",");
        pw.println("            \"choices\": [],");
        pw.println("            \"actions\": [{\"type\": \"HEAL_PLAYER\", \"target\": \"\", \"value\": \"\", \"amount\": 0}]");
        pw.println("          },");
        // gossip
        pw.println("          \"gossip\": {");
        pw.println("            \"id\": \"gossip\",");
        pw.println("            \"text\": \"Strange things happening lately. Tremors in the night, more than usual. Some miners swear they\\u0027ve seen shapes moving in the lava tubes. And old Aldric — the master forger — just vanished. Kira\\u0027s worried sick about him.\",");
        pw.println("            \"choices\": [");
        pw.println("              {\"label\": \"Sounds dangerous. I\\u0027ll rest up first.\", \"nextNodeId\": \"rest\", \"conditions\": []},");
        pw.println("              {\"label\": \"Thanks for the warning.\", \"nextNodeId\": \"farewell\", \"conditions\": []}");
        pw.println("            ],");
        pw.println("            \"actions\": []");
        pw.println("          },");
        // farewell
        pw.println("          \"farewell\": {");
        pw.println("            \"id\": \"farewell\",");
        pw.println("            \"text\": \"Door\\u0027s always open. Well, mostly.\",");
        pw.println("            \"choices\": [],");
        pw.println("            \"actions\": [{\"type\": \"CLOSE_DIALOGUE\", \"target\": \"\", \"value\": \"\", \"amount\": 0}]");
        pw.println("          }");
        pw.println("        }");
        pw.println("      }");
        pw.println("    },");

        // ── NPC 4: Merchant Bryn (simple defaultDialog) ────────────────────
        npc(pw, "npc_cinderport_bryn", "Merchant Bryn", "npcs/shopkeeper", "SHOPKEEPER",
            "Supplies for the brave and the foolish — same price either way.",
            new String[]{"healing_potion", "pyralis_fire_potion"},
            6, 22, false);

        pw.println("  ],");
        pw.println("  \"townEntrances\": [],");
        pw.println("  \"overworldTeleporters\": []");
        pw.println("}");
        pw.close();
        System.out.println("  wrote: " + path);
    }

    static void npc(PrintWriter pw, String id, String name, String sprite, String type,
                    String dialog, String[] shopItems, int x, int y, boolean comma) {
        pw.println("    {");
        pw.println("      \"id\": \"" + id + "\",");
        pw.println("      \"name\": \"" + name + "\",");
        pw.println("      \"spriteName\": \"" + sprite + "\",");
        pw.println("      \"type\": \"" + type + "\",");
        pw.println("      \"defaultDialog\": \"" + dialog + "\",");
        pw.println("      \"questCompleteDialog\": \"\",");
        pw.print("      \"shopItemIds\": [");
        for (int i = 0; i < shopItems.length; i++) {
            pw.print("\"" + shopItems[i] + "\"");
            if (i < shopItems.length - 1) pw.print(", ");
        }
        pw.println("],");
        pw.println("      \"x\": " + x + ",");
        pw.println("      \"y\": " + y);
        pw.print("    }");
        if (comma) pw.println(","); else pw.println();
    }
}

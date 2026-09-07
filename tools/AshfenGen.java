import java.io.*;

/**
 * Generates data/towns/ashfen_village.rfmap — a 20×20 small farming hamlet
 * in the ash wastes, remote and peaceful.
 *
 * Run from project root:
 *   javac tools/AshfenGen.java -d tools/
 *   java -cp tools AshfenGen
 */
public class AshfenGen {

    static final int COLS = 20, ROWS = 20;
    static char[][] g = new char[ROWS][COLS];

    // Tile IDs
    static final char VROCK  = '!';  // volcanic rock (exterior ground)
    static final char WOOD   = 'm';  // wood floor (house interiors)
    static final char WALL   = 'W';  // stone wall
    static final char DOOR   = 'd';  // wooden door
    static final char TABLE  = 'i';  // table
    static final char CHAIR  = 'j';  // chair
    static final char BED    = 'l';  // bed
    static final char BARREL = 'n';  // barrel
    static final char FIRE   = 'u';  // fireplace
    static final char PLANT  = 'Y';  // plant (farm plots)
    static final char ATREE  = ';';  // ash tree
    static final char FFLOOR = '*';  // forge floor
    static final char TORCH  = 'v';  // wall torch
    static final char SHELF  = 'k';  // bookshelf

    public static void main(String[] args) throws Exception {

        // ── 1. Base layer: volcanic rock everywhere ─────────────────────────
        fill(0, 19, 0, 19, VROCK);

        // ── 2. Ash trees scattered along edges ──────────────────────────────
        // NW cluster
        g[0][0] = ATREE;  g[0][1] = ATREE;  g[1][0] = ATREE;  g[2][1] = ATREE;
        // NE cluster
        g[0][18] = ATREE;  g[0][19] = ATREE;  g[1][19] = ATREE;  g[2][18] = ATREE;
        // SW cluster
        g[17][0] = ATREE;  g[18][0] = ATREE;  g[19][0] = ATREE;  g[18][1] = ATREE;
        // SE cluster
        g[17][19] = ATREE;  g[18][19] = ATREE;  g[19][19] = ATREE;  g[18][18] = ATREE;
        // Extra scattered
        g[1][2] = ATREE;  g[0][10] = ATREE;  g[0][5] = ATREE;
        g[10][0] = ATREE;  g[11][19] = ATREE;  g[19][5] = ATREE;

        // ── 3. Elder's House: rows 3-8, cols 2-8 ───────────────────────────
        // Walls (perimeter)
        hwall(3, 2, 8);
        hwall(8, 2, 8);
        vwall(2, 3, 8);
        vwall(8, 3, 8);
        // Interior wood floor
        fill(4, 7, 3, 7, WOOD);
        // Door on south wall
        g[8][5] = DOOR;
        // Furniture
        g[4][3] = FIRE;     // fireplace NW corner
        g[4][7] = BED;      // bed NE corner
        g[5][7] = BED;      // bed below
        g[6][5] = TABLE;    // table center
        g[6][6] = CHAIR;    // chair beside table
        g[4][4] = TORCH;    // wall torch

        // ── 4. Farmer's House: rows 3-8, cols 11-17 ────────────────────────
        // Walls (perimeter)
        hwall(3, 11, 17);
        hwall(8, 11, 17);
        vwall(11, 3, 8);
        vwall(17, 3, 8);
        // Interior wood floor
        fill(4, 7, 12, 16, WOOD);
        // Door on south wall
        g[8][14] = DOOR;
        // Furniture
        g[4][12] = BED;     // bed NW
        g[5][12] = BED;     // bed below
        g[4][16] = BARREL;  // barrel NE
        g[6][14] = TABLE;   // table center
        g[6][15] = CHAIR;   // chair beside table
        g[7][16] = BARREL;  // another barrel

        // ── 5. Old Soot's Hut: rows 12-16, cols 2-7 ────────────────────────
        // Walls (perimeter)
        hwall(12, 2, 7);
        hwall(16, 2, 7);
        vwall(2, 12, 16);
        vwall(7, 12, 16);
        // Interior wood floor
        fill(13, 15, 3, 6, WOOD);
        // Door on south wall
        g[16][4] = DOOR;
        // Furniture — messy/cluttered
        g[13][3] = SHELF;    // bookshelf NW
        g[13][4] = SHELF;    // bookshelf
        g[13][6] = FIRE;     // fireplace NE
        g[15][3] = BARREL;   // barrel SW
        g[15][6] = BARREL;   // barrel SE
        g[14][5] = BARREL;   // barrel center-ish (clutter)

        // ── 6. Farm plots: rows 14-18, cols 12-18 ──────────────────────────
        // Plant tiles in alternating rows
        for (int c = 12; c <= 18; c++) {
            g[14][c] = PLANT;
            g[16][c] = PLANT;
            g[18][c] = PLANT;
        }

        // ── 7. Town entry: south edge ───────────────────────────────────────
        g[19][9]  = DOOR;
        g[19][10] = DOOR;

        // ── 8. Write JSON ───────────────────────────────────────────────────
        writeJSON("data/towns/ashfen_village.rfmap");
        System.out.println("Done — data/towns/ashfen_village.rfmap written.");
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

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

    // ── JSON output ─────────────────────────────────────────────────────────

    static void writeJSON(String path) throws Exception {
        new File(path).getParentFile().mkdirs();
        PrintWriter pw = new PrintWriter(new FileWriter(path));
        pw.println("{");
        pw.println("  \"type\": \"TOWN\",");
        pw.println("  \"name\": \"ashfen_village\",");
        pw.println("  \"width\": 20,");
        pw.println("  \"height\": 20,");
        pw.println("  \"interiorEntryX\": 9,");
        pw.println("  \"interiorEntryY\": 19,");

        // tiles
        pw.println("  \"tiles\": [");
        for (int r = 0; r < ROWS; r++) {
            pw.print("    [");
            for (int c = 0; c < COLS; c++) {
                char ch = g[r][c];
                String s;
                if (ch == '"') s = "\"\\\"\"";
                else if (ch == '\\') s = "\"\\\\\"";
                else s = "\"" + ch + "\"";
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

        // ── NPC 1: Old Soot (with dialogue tree) ────────────────────────────
        pw.println("    {");
        pw.println("      \"id\": \"npc_ashfen_old_soot\",");
        pw.println("      \"name\": \"Old Soot\",");
        pw.println("      \"spriteName\": \"npcs/townsman\",");
        pw.println("      \"type\": \"TOWNSFOLK\",");
        pw.println("      \"defaultDialog\": \"\",");
        pw.println("      \"questCompleteDialog\": \"\",");
        pw.println("      \"shopItemIds\": [],");
        pw.println("      \"x\": 4,");
        pw.println("      \"y\": 14,");
        pw.println("      \"dialogueTree\": {");
        pw.println("        \"startNodeId\": \"greeting\",");
        pw.println("        \"nodes\": {");
        // greeting node
        pw.println("          \"greeting\": {");
        pw.println("            \"id\": \"greeting\",");
        pw.println("            \"text\": \"Heh... heh... the metal remembers, even when we forget. You smell of salt. Lirandel\\u0027s child, are you?\",");
        pw.println("            \"choices\": [");
        pw.println("              {\"label\": \"What do you know about this island?\", \"nextNodeId\": \"island_lore\", \"conditions\": []},");
        pw.println("              {\"label\": \"Have you seen anyone else pass through here?\", \"nextNodeId\": \"seraphine_hint\", \"conditions\": []},");
        pw.println("              {\"label\": \"Goodbye.\", \"nextNodeId\": \"farewell\", \"conditions\": []}");
        pw.println("            ],");
        pw.println("            \"actions\": []");
        pw.println("          },");
        // island_lore node
        pw.println("          \"island_lore\": {");
        pw.println("            \"id\": \"island_lore\",");
        pw.println("            \"text\": \"Fire makes. Fire takes. Pyralis built these forges on stolen dreams \\u2014 stole them right from the Serpent\\u0027s belly. But dreams don\\u0027t die easy... heh.\",");
        pw.println("            \"choices\": [");
        pw.println("              {\"label\": \"Tell me more.\", \"nextNodeId\": \"greeting\", \"conditions\": []},");
        pw.println("              {\"label\": \"Goodbye.\", \"nextNodeId\": \"farewell\", \"conditions\": []}");
        pw.println("            ],");
        pw.println("            \"actions\": []");
        pw.println("          },");
        // seraphine_hint node
        pw.println("          \"seraphine_hint\": {");
        pw.println("            \"id\": \"seraphine_hint\",");
        pw.println("            \"text\": \"A woman... thirty winters past. Silver in her hair, fire in her eyes. She asked about \\u0027the dreamer beneath the mountain.\\u0027 Haven\\u0027t stopped thinking about it since.\",");
        pw.println("            \"choices\": [");
        pw.println("              {\"label\": \"Interesting...\", \"nextNodeId\": \"greeting\", \"conditions\": []},");
        pw.println("              {\"label\": \"Goodbye.\", \"nextNodeId\": \"farewell\", \"conditions\": []}");
        pw.println("            ],");
        pw.println("            \"actions\": []");
        pw.println("          },");
        // farewell node
        pw.println("          \"farewell\": {");
        pw.println("            \"id\": \"farewell\",");
        pw.println("            \"text\": \"Heh... safe travels, salt-child.\",");
        pw.println("            \"choices\": [],");
        pw.println("            \"actions\": [{\"type\": \"CLOSE_DIALOGUE\", \"target\": \"\", \"value\": \"\", \"amount\": 0}]");
        pw.println("          }");
        pw.println("        }");
        pw.println("      }");
        pw.println("    },");

        // ── NPC 2: Village Elder (with dialogue tree) ───────────────────────
        pw.println("    {");
        pw.println("      \"id\": \"npc_ashfen_elder\",");
        pw.println("      \"name\": \"Village Elder\",");
        pw.println("      \"spriteName\": \"npcs/townsman\",");
        pw.println("      \"type\": \"HEALER\",");
        pw.println("      \"defaultDialog\": \"\",");
        pw.println("      \"questCompleteDialog\": \"\",");
        pw.println("      \"shopItemIds\": [],");
        pw.println("      \"x\": 5,");
        pw.println("      \"y\": 5,");
        pw.println("      \"dialogueTree\": {");
        pw.println("        \"startNodeId\": \"greeting\",");
        pw.println("        \"nodes\": {");
        // greeting node
        pw.println("          \"greeting\": {");
        pw.println("            \"id\": \"greeting\",");
        pw.println("            \"text\": \"Welcome to Ashfen, traveler. The ash feeds our crops, and the volcano keeps us warm.\",");
        pw.println("            \"choices\": [");
        pw.println("              {\"label\": \"I need healing.\", \"nextNodeId\": \"heal\", \"conditions\": []},");
        pw.println("              {\"label\": \"Tell me about Ashfen.\", \"nextNodeId\": \"ashfen_lore\", \"conditions\": []},");
        pw.println("              {\"label\": \"Goodbye.\", \"nextNodeId\": \"farewell\", \"conditions\": []}");
        pw.println("            ],");
        pw.println("            \"actions\": []");
        pw.println("          },");
        // heal node
        pw.println("          \"heal\": {");
        pw.println("            \"id\": \"heal\",");
        pw.println("            \"text\": \"Rest now. The earth provides.\",");
        pw.println("            \"choices\": [");
        pw.println("              {\"label\": \"Thank you.\", \"nextNodeId\": \"greeting\", \"conditions\": []}");
        pw.println("            ],");
        pw.println("            \"actions\": [{\"type\": \"HEAL_PLAYER\", \"target\": \"\", \"value\": \"\", \"amount\": 0}]");
        pw.println("          },");
        // ashfen_lore node
        pw.println("          \"ashfen_lore\": {");
        pw.println("            \"id\": \"ashfen_lore\",");
        pw.println("            \"text\": \"We\\u0027ve farmed this soil for generations. The volcanic ash makes it richer than any lowland dirt. Pyralis watches over us from the mountain \\u2014 some fear him, but we owe our harvest to his fire.\",");
        pw.println("            \"choices\": [");
        pw.println("              {\"label\": \"I see.\", \"nextNodeId\": \"greeting\", \"conditions\": []},");
        pw.println("              {\"label\": \"Goodbye.\", \"nextNodeId\": \"farewell\", \"conditions\": []}");
        pw.println("            ],");
        pw.println("            \"actions\": []");
        pw.println("          },");
        // farewell node
        pw.println("          \"farewell\": {");
        pw.println("            \"id\": \"farewell\",");
        pw.println("            \"text\": \"May the ash be gentle on your path.\",");
        pw.println("            \"choices\": [],");
        pw.println("            \"actions\": [{\"type\": \"CLOSE_DIALOGUE\", \"target\": \"\", \"value\": \"\", \"amount\": 0}]");
        pw.println("          }");
        pw.println("        }");
        pw.println("      }");
        pw.println("    },");

        // ── NPC 3: Farmer (simple defaultDialog) ───────────────────────────
        pw.println("    {");
        pw.println("      \"id\": \"npc_ashfen_farmer\",");
        pw.println("      \"name\": \"Farmer\",");
        pw.println("      \"spriteName\": \"npcs/townsman\",");
        pw.println("      \"type\": \"TOWNSFOLK\",");
        pw.println("      \"defaultDialog\": \"The soil here is the richest in all the islands. Volcanic ash makes everything grow twice as fast.\",");
        pw.println("      \"questCompleteDialog\": \"\",");
        pw.println("      \"shopItemIds\": [],");
        pw.println("      \"x\": 14,");
        pw.println("      \"y\": 5");
        pw.println("    }");

        pw.println("  ],");
        pw.println("  \"townEntrances\": [],");
        pw.println("  \"overworldTeleporters\": []");
        pw.println("}");
        pw.close();
        System.out.println("  wrote: " + path);
    }
}

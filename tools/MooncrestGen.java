import java.io.*;

/**
 * Generates data/towns/mooncrest.rfmap — a 64×64 Dawn-inspired night-market town.
 *
 * Layout:
 *   - Exterior grass + exterior wetlands (dark floor outside perimeter)
 *   - White stone perimeter wall (thick border)
 *   - Dark cobblestone interior plaza
 *   - Upper building cluster: Tavern, Magic Guild, Armory+Stables (connected row)
 *   - Lower building cluster: Healer's, Grocery/Thieves, Fortune Teller (connected row)
 *   - Central open plaza with fountain
 *   - Entry from south (center gap)
 *
 * Run from project root:
 *   javac tools/MooncrestGen.java -d tools/
 *   java -cp tools MooncrestGen
 */
public class MooncrestGen {

    static final int COLS = 64, ROWS = 64;
    static char[][] g = new char[ROWS][COLS];

    // Tile IDs
    static final char GRASS   = '\'';  // town grass (exterior)
    static final char WALL    = 'W';   // stone wall
    static final char FLOOR   = 'F';   // dark stone floor (interior + exterior moat)
    static final char WOOD    = 'm';   // wood floor (tavern/pub)
    static final char DOOR    = 'd';   // wooden door
    static final char TABLE   = 'j';   // table
    static final char COUNTER = 'c';   // counter / bar
    static final char SHELF   = 'k';   // bookshelf
    static final char BED     = 'e';   // bed
    static final char BARREL  = 'l';   // barrel
    static final char FIRE    = 'i';   // fireplace
    static final char FORGE   = 'N';   // forge
    static final char PLANT   = 'Y';   // potted plant
    static final char TREE    = 'X';   // town tree
    static final char PILLAR  = 'M';   // stone pillar
    static final char WELL    = 'Q';   // well
    static final char FOUNT   = '@';   // fountain
    static final char SIGN    = 'S';   // signpost

    public static void main(String[] args) throws Exception {

        // ── 1. Base layer: grass everywhere ──────────────────────────────────
        fill(0, 63, 0, 63, GRASS);

        // ── 2. Exterior wetlands / dark margin outside perimeter ─────────────
        // Narrow wedge on east & west that tapers — mimics Dawn's rounded look
        // West taper:  full grass col 0-3; col 4 partial floor
        // East taper:  col 59-63 grass; col 58 partial floor
        // South moat strip (rows 28-34 are deepest on east side)
        // We'll approximate with floor blobs outside the wall
        floodExterior();

        // ── 3. Perimeter wall — thick white border ───────────────────────────
        // Outer ring at rows 5/58 and cols 5/58
        hwall(5,  5, 58);   hwall(58, 5, 58);
        vwall(5,  5, 58);   vwall(58, 5, 58);
        // Second inner ring (makes it feel thick)
        hwall(6,  6, 57);   hwall(57, 6, 57);
        vwall(6,  6, 57);   vwall(57, 6, 57);

        // Entry gap in south wall — player enters here (center)
        g[58][30] = DOOR;  g[58][31] = DOOR;  g[58][32] = DOOR;  g[58][33] = DOOR;
        g[57][30] = DOOR;  g[57][31] = DOOR;  g[57][32] = DOOR;  g[57][33] = DOOR;

        // West entry gap (like Dawn's west entrance)
        g[31][5]  = DOOR;  g[32][5]  = DOOR;
        g[31][6]  = DOOR;  g[32][6]  = DOOR;

        // ── 4. Interior dark cobblestone floor ───────────────────────────────
        fill(7, 56, 7, 56, FLOOR);

        // ── 5. Interior corner tree clusters ─────────────────────────────────
        // NW cluster (rows 7-10, cols 7-10)
        treeCluster(7, 10, 7, 10);
        // NE cluster (rows 7-10, cols 53-56)
        treeCluster(7, 10, 53, 56);
        // SW cluster (rows 53-56, cols 7-10)
        treeCluster(53, 56, 7, 10);
        // SE cluster (rows 53-56, cols 53-56)
        treeCluster(53, 56, 53, 56);

        // Mid-west tree cluster (rows 27-30, cols 7-9) — beside west entry
        g[28][8] = TREE;  g[29][8] = TREE;  g[30][8] = TREE;
        g[29][9] = TREE;

        // ── 6. UPPER BUILDING CLUSTER ────────────────────────────────────────
        //
        //   Three connected buildings share a common outer wall.
        //   They sit at rows 11–25, with irregular footprints like Dawn.
        //
        //   Building A — Tavern "The Crescent Cup"   cols 10–24  (15w × 15h)
        //   Building B — Magic Guild                  cols 25–38  (14w × 15h)
        //   Building C — Armory + Stables (combined)  cols 38–54  (17w × 15h)
        //      (shared interior wall divides Armory left / Stables right)

        // Outer shell: north wall row 11, south wall row 25
        hwall(11, 10, 54);
        hwall(25, 10, 54);
        vwall(10, 11, 25);
        vwall(54, 11, 25);

        // Internal dividers between buildings
        vwall(24, 11, 25);   // between Tavern and Magic Guild
        vwall(38, 11, 25);   // between Magic Guild and Armory/Stables

        // Internal divider INSIDE the Armory/Stables building
        vwall(46, 13, 25);   // Armory (left) vs Stables (right)

        // Building A – Tavern south door
        g[25][17] = DOOR;
        // Building B – Magic Guild south door
        g[25][31] = DOOR;
        // Building C – Armory south door
        g[25][42] = DOOR;
        // Building C – Stables south door
        g[25][50] = DOOR;

        // Tavern has a north-side alcove (small annex like Dawn)
        // Extend Tavern north by 2 rows on left half
        hwall(9,  10, 17);
        vwall(10, 9, 11);
        vwall(17, 9, 11);
        fill(10, 10, 11, 16, WOOD);   // alcove floor (row 10)

        // Interior fills
        buildTavern();       // rows 12-24, cols 11-23
        buildMagicGuild();   // rows 12-24, cols 25-37
        buildArmory();       // rows 12-24, cols 39-45
        buildStables();      // rows 12-24, cols 47-53

        // ── 7. LOWER BUILDING CLUSTER ────────────────────────────────────────
        //
        //   Building D — Healer's House    cols 10–23  (14w × 14h)
        //   Building E — Thieves Guild     cols 23–37  (15w × 14h)
        //   Building F — Fortune Teller    cols 37–54  (18w × 14h, wider + irregular)
        //
        //   The lower cluster is slightly lower and offset to match Dawn asymmetry.
        //   F has an inner courtyard alcove (open top section).

        // Outer shell: north wall row 37, south wall row 51
        hwall(37, 10, 54);
        hwall(51, 10, 54);
        vwall(10, 37, 51);
        vwall(54, 37, 51);

        // Internal dividers
        vwall(23, 37, 51);   // between Healer and Thieves
        vwall(37, 37, 51);   // between Thieves and Fortune Teller

        // Fortune Teller has an inner alcove notch (rows 37-41, cols 46-54 = open interior)
        // Leave that as is but add inner wall for the alcove
        hwall(41, 46, 54);   // creates a smaller inner room at north of Fortune Teller
        vwall(46, 37, 41);   // west wall of the alcove inner section

        // Doors (north-facing — player approaches from central plaza)
        g[37][16] = DOOR;    // Healer's north door
        g[37][30] = DOOR;    // Thieves Guild north door
        g[37][44] = DOOR;    // Fortune Teller north door

        // Healer also has a south door (accessible from south plaza)
        g[51][16] = DOOR;
        // Fortune teller south door
        g[51][48] = DOOR;

        // Interior fills
        buildHealer();         // rows 38-50, cols 11-22
        buildThievesGuild();   // rows 38-50, cols 24-36
        buildFortuneTeller();  // rows 38-50, cols 38-53

        // ── 8. INN (standalone building in center-east, between clusters) ────
        //
        //   Mimics Dawn's standalone Inn in the central area.
        //   Small building: rows 27–34, cols 44–54
        hwall(27, 44, 54);
        hwall(34, 44, 54);
        vwall(44, 27, 34);
        vwall(54, 27, 34);
        g[34][49] = DOOR;    // south door
        buildInn();          // rows 28-33, cols 45-53

        // ── 9. CENTRAL PLAZA FEATURES ────────────────────────────────────────

        // Fountain — center of plaza between the two clusters
        g[31][31] = FOUNT;

        // Well — west side of plaza
        g[29][19] = WELL;
        g[33][19] = WELL;

        // Pillars flanking building doors
        // Upper cluster flanks
        g[26][12] = PILLAR;   g[26][22] = PILLAR;   // Tavern flanks
        g[26][26] = PILLAR;   g[26][36] = PILLAR;   // Magic Guild flanks
        g[26][40] = PILLAR;   g[26][52] = PILLAR;   // Armory/Stables flanks
        // Lower cluster flanks
        g[36][12] = PILLAR;   g[36][21] = PILLAR;   // Healer flanks
        g[36][25] = PILLAR;   g[36][35] = PILLAR;   // Thieves Guild flanks
        g[36][39] = PILLAR;   g[36][52] = PILLAR;   // Fortune Teller flanks

        // Signposts (building labels)
        g[26][17] = SIGN;     // Tavern sign
        g[26][31] = SIGN;     // Magic Guild sign
        g[26][42] = SIGN;     // Armory sign
        g[26][50] = SIGN;     // Stables sign
        g[36][16] = SIGN;     // Healer sign
        g[36][30] = SIGN;     // Thieves sign
        g[36][48] = SIGN;     // Fortune Teller sign
        g[35][49] = SIGN;     // Inn sign (beside inn)

        // Plants scattered in plaza
        g[29][14] = PLANT;    g[29][41] = PLANT;
        g[33][14] = PLANT;    g[33][41] = PLANT;
        g[31][22] = PLANT;    g[31][38] = PLANT;

        // ── 10. South entry corridor ─────────────────────────────────────────
        // Pillars flanking south entry
        g[55][27] = PILLAR;   g[55][36] = PILLAR;
        g[54][27] = PILLAR;   g[54][36] = PILLAR;
        // Trees flanking south approach
        g[52][29] = TREE;     g[52][34] = TREE;

        // ── 11. Write JSON ────────────────────────────────────────────────────
        writeJSON("data/towns/mooncrest.rfmap");
        System.out.println("Done — data/towns/mooncrest.rfmap written.");
    }

    // ── Building interiors ───────────────────────────────────────────────────

    static void buildTavern() {
        // rows 12-24, cols 11-23  (13w × 13h) — wood floor throughout
        fill(12, 24, 11, 23, WOOD);

        // Fireplace pair on north wall (row 12)
        g[12][11] = FIRE;   g[12][23] = FIRE;

        // Bar counter: L-shaped along north + east
        for (int c = 12; c <= 22; c++) g[12][c] = COUNTER;  // north bar
        for (int r = 13; r <= 15; r++) g[r][22] = COUNTER;  // east bar return

        // Bar interior (behind counter) — keep as wood
        // Patron seating area: rows 14-23, cols 11-21
        // Tables in groups of 2×2, with walkway aisles
        tableBlock(15, 16, 12, 13);
        tableBlock(15, 16, 15, 16);
        tableBlock(15, 16, 18, 19);
        tableBlock(18, 19, 12, 13);
        tableBlock(18, 19, 15, 16);
        tableBlock(18, 19, 18, 19);
        tableBlock(21, 22, 12, 13);
        tableBlock(21, 22, 15, 16);
        tableBlock(21, 22, 18, 19);

        // Plants in SE corner
        g[23][21] = PLANT;  g[22][21] = PLANT;

        // Clear NPC position
        g[16][17] = WOOD;
    }

    static void buildMagicGuild() {
        // rows 12-24, cols 25-37  (13w × 13h) — stone floor
        fill(12, 24, 25, 37, FLOOR);

        // Bookshelves lining north wall
        for (int c = 25; c <= 37; c++) g[12][c] = SHELF;

        // Bookshelves on east wall
        for (int r = 13; r <= 23; r++) g[r][37] = SHELF;

        // Bookshelves on west wall (inner)
        for (int r = 13; r <= 18; r++) g[r][25] = SHELF;

        // Central study table
        tableBlock(17, 18, 28, 29);
        tableBlock(17, 18, 32, 33);

        // Fireplace on south wall
        g[24][31] = FIRE;

        // Plants
        g[13][26] = PLANT;  g[23][26] = PLANT;

        // Arcane circle (pillars)
        g[20][30] = PILLAR; g[20][32] = PILLAR;
        g[22][30] = PILLAR; g[22][32] = PILLAR;

        // Clear NPC position
        g[15][31] = FLOOR;
    }

    static void buildArmory() {
        // rows 12-24, cols 39-45  (7w × 13h) — stone floor
        fill(12, 24, 39, 45, FLOOR);

        // Forge at back (north)
        g[12][41] = FORGE;  g[12][42] = FORGE;
        g[13][41] = FORGE;  g[13][42] = FORGE;

        // Fireplace on west wall
        g[15][39] = FIRE;

        // Counter (display cases)
        for (int c = 39; c <= 45; c++) g[17][c] = COUNTER;

        // Barrels — weapon storage (south area)
        fill(19, 23, 40, 44, BARREL);
        g[19][39] = BARREL;

        // Clear NPC position
        g[15][42] = FLOOR;
    }

    static void buildStables() {
        // rows 12-24, cols 47-53  (7w × 13h) — stone floor
        fill(12, 24, 47, 53, FLOOR);

        // Hay bales (barrels) at north
        fill(12, 13, 48, 52, BARREL);

        // Stall dividers using barrels
        // Left stall
        g[16][47] = BARREL;  g[17][47] = BARREL;  g[18][47] = BARREL;
        // Right stall
        g[16][53] = BARREL;  g[17][53] = BARREL;  g[18][53] = BARREL;
        // Center stall row
        for (int c = 47; c <= 53; c++) g[21][c] = BARREL;

        // Open floor in stall areas
        fill(16, 20, 48, 52, FLOOR);
        fill(22, 24, 47, 53, FLOOR);

        // Clear NPC position
        g[19][50] = FLOOR;
    }

    static void buildHealer() {
        // rows 38-50, cols 11-22  (12w × 13h) — stone floor
        fill(38, 50, 11, 22, FLOOR);

        // Beds along west and east walls
        for (int r = 38; r <= 43; r += 3) {
            g[r][11] = BED;  g[r][12] = BED;
            g[r+1][11] = BED; g[r+1][12] = BED;
            g[r][20] = BED;  g[r][21] = BED;
            g[r+1][20] = BED; g[r+1][21] = BED;
        }
        g[47][11] = BED; g[47][12] = BED;
        g[48][11] = BED; g[48][12] = BED;
        g[47][20] = BED; g[47][21] = BED;
        g[48][20] = BED; g[48][21] = BED;

        // Herb plants along center corridor
        for (int r = 38; r <= 48; r += 2) g[r][16] = PLANT;

        // Counter (medicine prep) near south
        for (int c = 12; c <= 21; c++) g[49][c] = COUNTER;

        // Fireplace at north center
        g[38][16] = FIRE;

        // Clear NPC position (center aisle)
        g[43][16] = FLOOR;
    }

    static void buildThievesGuild() {
        // rows 38-50, cols 24-36  (13w × 13h) — stone floor
        fill(38, 50, 24, 36, FLOOR);

        // Bookshelves on all four inner walls
        for (int c = 24; c <= 36; c++) { g[38][c] = SHELF; g[50][c] = SHELF; }
        for (int r = 39; r <= 49; r++) { g[r][24] = SHELF; g[r][36] = SHELF; }

        // Central planning table
        tableBlock(42, 43, 27, 28);
        tableBlock(42, 43, 32, 33);
        tableBlock(45, 46, 27, 28);
        tableBlock(45, 46, 32, 33);

        // Fireplace south
        g[50][30] = FIRE;

        // Clear NPC position
        g[44][30] = FLOOR;
    }

    static void buildFortuneTeller() {
        // rows 38-50, cols 38-53 (south section, below the alcove wall at row 41)
        // Alcove (north inner room): rows 38-40, cols 47-53
        fill(38, 40, 47, 53, FLOOR);
        // Main room: rows 42-50, cols 38-53
        fill(42, 50, 38, 53, FLOOR);

        // Carpets/shelves around north alcove
        for (int c = 47; c <= 53; c++) g[38][c] = SHELF;
        for (int r = 38; r <= 40; r++) { g[r][47] = SHELF; g[r][53] = SHELF; }

        // Crystal table in alcove center
        g[39][50] = TABLE; g[39][51] = TABLE;

        // Main room decor
        // Bookshelves east wall
        for (int r = 42; r <= 49; r++) g[r][53] = SHELF;
        // Bookshelves west wall (inner)
        for (int r = 42; r <= 49; r++) g[r][38] = SHELF;

        // Fireplace in main room
        g[42][45] = FIRE;

        // Candles/plants scattered
        g[44][41] = PLANT;  g[44][50] = PLANT;
        g[47][41] = PLANT;  g[47][50] = PLANT;

        // Central reading table
        tableBlock(45, 46, 44, 45);
        tableBlock(45, 46, 48, 49);

        // Clear NPC position
        g[47][46] = FLOOR;
    }

    static void buildInn() {
        // rows 28-33, cols 45-53  (9w × 6h)
        fill(28, 33, 45, 53, WOOD);

        // Beds on north wall
        g[28][45] = BED;  g[28][46] = BED;
        g[29][45] = BED;  g[29][46] = BED;
        g[28][49] = BED;  g[28][50] = BED;
        g[29][49] = BED;  g[29][50] = BED;
        g[28][52] = BED;  g[28][53] = BED;
        g[29][52] = BED;  g[29][53] = BED;

        // Counter (innkeeper desk)
        for (int c = 46; c <= 52; c++) g[32][c] = COUNTER;

        // Fireplace on south
        g[33][49] = FIRE;

        // Clear NPC position
        g[30][49] = WOOD;
    }

    // ── Exterior wetlands ────────────────────────────────────────────────────
    // Mimics the `.` areas outside the perimeter wall in Dawn's ASCII map.
    // The dark areas on the SW/NW sides and partial east side.
    static void floodExterior() {
        // West side wetlands (rows 19-35, cols 0-4)
        fill(19, 35, 0, 4, FLOOR);
        // SW extension (rows 24-34, cols 0-5)
        fill(24, 34, 0, 5, FLOOR);
        // NW small patch (rows 17-21, cols 0-3)
        fill(17, 21, 0, 3, FLOOR);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

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

    static void treeCluster(int r1, int r2, int c1, int c2) {
        for (int r = r1; r <= r2; r++)
            for (int c = c1; c <= c2; c++)
                g[r][c] = TREE;
    }

    static void tableBlock(int r1, int r2, int c1, int c2) {
        g[r1][c1] = TABLE; g[r1][c2] = TABLE;
        g[r2][c1] = TABLE; g[r2][c2] = TABLE;
    }

    // ── JSON output ──────────────────────────────────────────────────────────

    static void writeJSON(String path) throws Exception {
        new File(path).getParentFile().mkdirs();
        PrintWriter pw = new PrintWriter(new FileWriter(path));
        pw.println("{");
        pw.println("  \"type\": \"TOWN\",");
        pw.println("  \"name\": \"mooncrest\",");
        pw.println("  \"width\": 64,");
        pw.println("  \"height\": 64,");
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
        // Oswin — Tavern, near bar (row 16, col 17)
        npc(pw, "npc_mooncrest_innkeeper", "Oswin", "npcs/innkeeper", "INNKEEPER",
            "Rest your bones, traveler. The Crescent Cup welcomes all — day or night.", 17, 16, true);
        // Lyren the Pale — Magic Guild (row 15, col 31)
        npc(pw, "npc_mooncrest_lyren", "Lyren the Pale", "npcs/townsman", "TOWNSFOLK",
            "Knowledge has a price. The moon reveals what the sun hides.", 31, 15, true);
        // Dagan — Armory (row 15, col 42)
        npc(pw, "npc_mooncrest_dagan", "Dagan", "npcs/shopkeeper", "SHOPKEEPER",
            "Finest steel in the region. Name your need.", 42, 15, true);
        // Rook — Stables (row 19, col 50)
        npc(pw, "npc_mooncrest_rook", "Rook", "npcs/townsman", "TOWNSFOLK",
            "Fast horses, no questions asked. That\u0027s my trade.", 50, 19, true);
        // Sister Caela — Healer (row 43, col 16)
        npc(pw, "npc_mooncrest_caela", "Sister Caela", "npcs/shopkeeper", "HEALER",
            "The body heals when the spirit is willing. Let me help.", 16, 43, true);
        // The Veiled One — Thieves Guild / Fortune Teller (row 44, col 30)
        npc(pw, "npc_mooncrest_veiled", "The Veiled One", "npcs/townsman", "TOWNSFOLK",
            "Mooncrest only wakes at night. Some of us never sleep.", 30, 44, true);
        // Innkeeper Aldren — Inn (row 30, col 49)
        npc(pw, "npc_mooncrest_aldren", "Aldren", "npcs/innkeeper", "INNKEEPER",
            "Quiet room available. Safer than sleeping in the plaza.", 49, 30, false);
        pw.println("  ],");
        pw.println("  \"townEntrances\": [],");
        pw.println("  \"overworldTeleporters\": []");
        pw.println("}");
        pw.close();
        System.out.println("  wrote: " + path);
    }

    static void npc(PrintWriter pw, String id, String name, String sprite, String type,
                    String dialog, int x, int y, boolean comma) {
        pw.println("    {");
        pw.println("      \"id\": \"" + id + "\",");
        pw.println("      \"name\": \"" + name + "\",");
        pw.println("      \"spriteName\": \"" + sprite + "\",");
        pw.println("      \"type\": \"" + type + "\",");
        pw.println("      \"defaultDialog\": \"" + dialog + "\",");
        pw.println("      \"questCompleteDialog\": \"\",");
        pw.println("      \"shopItemIds\": [],");
        pw.println("      \"x\": " + x + ",");
        pw.println("      \"y\": " + y);
        pw.print("    }");
        if (comma) pw.println(","); else pw.println();
    }
}

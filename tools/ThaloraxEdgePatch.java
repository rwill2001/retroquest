import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Gives Thalorax a shelf edge.
 *
 * Thalorax is the only island with no impassable boundary: it is seabed all the way
 * out, so its walkable area ran to the literal edge of the 230x190 grid, where a move
 * is silently swallowed by the bounds check in Retroquest.movePlayer. Every other
 * island is ringed by an impassable tile (Water / Sky Void / Void Trench).
 *
 * This replaces the empty open water outside the authored content with the impassable
 * "Crushing Depths" tile (U+E07F).
 *
 * The region is deliberately defined as *everything outside the bounding box of
 * authored content*, not as a distance from the nearest feature. Thalorax speckles
 * scattered rock outcrops, coral islets and lone features (a shrine, two dig sites and
 * the forward portal to Umbryn) right out to the map margins, so any distance rule
 * produces a ragged coastline and severs the open-water crossings those features are
 * reached by. The bounding box provably touches no authored tile: every cell outside
 * it is Deep Ocean Floor by construction.
 *
 * The patch is idempotent - Crushing Depths is excluded when computing the box, so a
 * second run finds the same box and converts nothing.
 *
 * Run from the project root:
 *   javac tools/ThaloraxEdgePatch.java -d tools/
 *   java -cp tools ThaloraxEdgePatch
 */
public class ThaloraxEdgePatch {

    static final char DEEP_OCEAN_FLOOR = '\uE070';
    static final char CRUSHING_DEPTHS  = '\uE07F';

    static final String PATH = "data/overworlds/thalorax.rfmap";

    public static void main(String[] args) throws Exception {
        Path path = Paths.get(PATH);
        String content = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);

        // Keep CRLF intact: split on \n only, so each line keeps its trailing \r.
        List<String> lines = new ArrayList<>(Arrays.asList(content.split("\n", -1)));

        // Pass 1 - read the grid and note which line holds each cell.
        Grid grid = readGrid(lines);
        System.out.println("read " + grid.w + "x" + grid.h + " tile grid");

        // Bounding box of authored content: anything that is neither open seabed nor
        // an edge tile this patch has already placed.
        int x0 = grid.w, y0 = grid.h, x1 = -1, y1 = -1;
        for (int y = 0; y < grid.h; y++) {
            for (int x = 0; x < grid.w; x++) {
                char c = grid.cell[y][x];
                if (c == DEEP_OCEAN_FLOOR || c == CRUSHING_DEPTHS) continue;
                if (x < x0) x0 = x;
                if (x > x1) x1 = x;
                if (y < y0) y0 = y;
                if (y > y1) y1 = y;
            }
        }
        System.out.println("content bounding box: x " + x0 + ".." + x1 + ", y " + y0 + ".." + y1);
        System.out.println("margins kept walkable: left=" + x0 + " right=" + (grid.w - 1 - x1)
                + " top=" + y0 + " bottom=" + (grid.h - 1 - y1));

        // Pass 2 - convert the open water outside the box.
        int converted = 0, skipped = 0;
        for (int y = 0; y < grid.h; y++) {
            for (int x = 0; x < grid.w; x++) {
                boolean outside = x < x0 || x > x1 || y < y0 || y > y1;
                if (!outside) continue;
                char c = grid.cell[y][x];
                if (c == CRUSHING_DEPTHS) continue;          // already patched
                if (c != DEEP_OCEAN_FLOOR) { skipped++; continue; }  // never touch authored terrain
                int li = grid.line[y][x];
                lines.set(li, replaceTile(lines.get(li), CRUSHING_DEPTHS));
                converted++;
            }
        }

        if (skipped > 0) {
            // Cannot happen while the box is derived from the content itself; loud if it does.
            System.out.println("WARNING: " + skipped + " non-seabed tiles outside the box were left alone");
        }

        if (converted == 0) {
            System.out.println("nothing to do - edge already in place");
            return;
        }

        Files.write(path, String.join("\n", lines).getBytes(StandardCharsets.UTF_8));
        System.out.println("converted " + converted + " Deep Ocean Floor tiles to Crushing Depths");
        System.out.println("wrote " + PATH);
    }

    /** The tile grid plus, for each cell, the index of the line it lives on. */
    static class Grid {
        int w, h;
        char[][] cell;
        int[][] line;
    }

    /**
     * Walks the pretty-printed "tiles" array. Each row opens with a bare "[" and each
     * cell is its own line holding a quoted single character.
     */
    static Grid readGrid(List<String> lines) {
        int w = intField(lines, "width");
        int h = intField(lines, "height");

        Grid g = new Grid();
        g.w = w; g.h = h;
        g.cell = new char[h][w];
        g.line = new int[h][w];

        int start = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).trim().startsWith("\"tiles\"")) { start = i; break; }
        }
        if (start < 0) throw new IllegalStateException("no \"tiles\" array found");

        int row = -1, col = 0;
        for (int i = start + 1; i < lines.size(); i++) {
            String t = lines.get(i).trim();
            if (t.equals("[")) { row++; col = 0; continue; }
            if (t.startsWith("]")) {
                if (row == h - 1) break;                 // end of the last row
                continue;
            }
            if (!t.startsWith("\"")) continue;

            if (row < 0 || row >= h) throw new IllegalStateException("row out of range: " + row);
            if (col >= w) throw new IllegalStateException("row " + row + " has more than " + w + " cells");

            g.cell[row][col] = unquote(t);
            g.line[row][col] = i;
            col++;
        }
        if (row != h - 1) throw new IllegalStateException("read " + (row + 1) + " rows, expected " + h);
        return g;
    }

    /**
     * The tile character on a cell line. Most are written raw, but Gson escaped a
     * handful (the eight Baited Deep Line hunt tiles come out as "\ue014"), so both
     * forms have to be understood.
     */
    static char unquote(String trimmed) {
        int a = trimmed.indexOf('"');
        int b = trimmed.indexOf('"', a + 1);
        String s = trimmed.substring(a + 1, b);
        if (s.length() == 1) return s.charAt(0);
        if (s.length() == 6 && s.startsWith("\\u")) {
            return (char) Integer.parseInt(s.substring(2), 16);
        }
        throw new IllegalStateException("unexpected tile literal: " + trimmed);
    }

    /** Swaps the quoted tile character on a cell line, leaving indent and comma alone. */
    static String replaceTile(String line, char tile) {
        int a = line.indexOf('"');
        int b = line.indexOf('"', a + 1);
        return line.substring(0, a + 1) + tile + line.substring(b);
    }

    static int intField(List<String> lines, String name) {
        String key = "\"" + name + "\":";
        for (String l : lines) {
            String t = l.trim();
            if (t.startsWith(key)) {
                String v = t.substring(key.length()).replace(",", "").trim();
                return Integer.parseInt(v);
            }
        }
        throw new IllegalStateException("no \"" + name + "\" field");
    }
}

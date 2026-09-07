import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Patches spawnDifficulty arrays in all island overworld rfmap files.
 *
 * BUG: All generators set spawnDifficulty = intended monster level, but
 * the encounter formula is: monsterLevel = 1 + (difficulty * 49) / 99.
 * So difficulty 12 produces level ~7, not level 12.
 *
 * FIX: Apply inverse formula: difficulty = (level - 1) * 99 / 49
 * Zone by distance from towns (easier near towns, harder far away).
 *
 * Run from project root:
 *   javac -encoding UTF-8 tools/SpawnDifficultyPatch.java -d tools/
 *   java -cp tools SpawnDifficultyPatch
 */
public class SpawnDifficultyPatch {

    // Minimum spawnDifficulty that produces a given monster level.
    // Forward formula (integer): monsterLevel = 1 + (difficulty * 49) / 99
    // Inverse: need ceil to account for integer division truncation.
    static int diffForLevel(int level) {
        return (int) Math.ceil((level - 1) * 99.0 / 49.0);
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Island definitions
    // ═══════════════════════════════════════════════════════════════════════

    static class Island {
        String name;
        String rfmapPath;
        int width, height;
        int minLevel, maxLevel;
        int[][] towns; // {x, y} for each town
        boolean hasStormWall; // Zephyrion special case

        Island(String name, String path, int w, int h, int minLv, int maxLv, int[][] towns) {
            this.name = name;
            this.rfmapPath = path;
            this.width = w;
            this.height = h;
            this.minLevel = minLv;
            this.maxLevel = maxLv;
            this.towns = towns;
        }
    }

    static final Island[] ISLANDS = {
        new Island("Pyralis", "data/overworlds/pyralis.rfmap",
            180, 150, 6, 10,
            new int[][]{{22,92}, {90,70}, {150,40}}),

        new Island("Zephyrion", "data/overworlds/zephyrion.rfmap",
            230, 190, 8, 11,
            new int[][]{{115,45}, {180,60}, {175,132}, {50,97}}),

        new Island("Sylvandar", "data/overworlds/sylvandar.rfmap",
            230, 190, 12, 15,
            new int[][]{{65,95}, {165,105}, {115,38}}),

        new Island("Thalorax", "data/overworlds/thalorax.rfmap",
            230, 190, 16, 19,
            new int[][]{{65,95}, {165,105}, {115,38}}),

        new Island("Umbryn", "data/overworlds/umbryn.rfmap",
            230, 190, 20, 23,
            new int[][]{{115,95}, {55,110}, {175,50}}),

        new Island("Bellorak", "data/overworlds/bellorak.rfmap",
            230, 190, 24, 27,
            new int[][]{{60,40}, {170,140}, {115,95}}),
    };

    static {
        ISLANDS[1].hasStormWall = true; // Zephyrion
    }

    public static void main(String[] args) throws Exception {
        for (Island island : ISLANDS) {
            System.out.println("\n══════════════════════════════════════════");
            System.out.println("  Patching: " + island.name);
            System.out.println("  Levels " + island.minLevel + "-" + island.maxLevel
                + " → difficulty " + diffForLevel(island.minLevel) + "-" + diffForLevel(island.maxLevel));
            System.out.println("══════════════════════════════════════════");
            patchIsland(island);
        }
        System.out.println("\nDone! All islands patched.");
    }

    static void patchIsland(Island island) throws Exception {
        // Some rfmap files may contain non-UTF-8 bytes; read as ISO-8859-1 to avoid errors
        byte[] bytes = Files.readAllBytes(Path.of(island.rfmapPath));
        String json = new String(bytes, StandardCharsets.ISO_8859_1);

        // Parse existing spawnDifficulty
        int[][] oldDiff = parseSpawnDifficulty(json, island.width, island.height);

        // Calculate new difficulty range
        // minDiff = first difficulty that produces minLevel
        // maxDiff = last difficulty that still produces maxLevel
        int minDiff = diffForLevel(island.minLevel);
        int maxDiff = diffForLevel(island.maxLevel + 1) - 1;
        int[][] newDiff = new int[island.height][island.width];

        int patched = 0, skipped = 0, kept = 0;
        for (int y = 0; y < island.height; y++) {
            for (int x = 0; x < island.width; x++) {
                if (oldDiff[y][x] == 0) {
                    // Water, town, portal, etc. — keep at 0
                    newDiff[y][x] = 0;
                    skipped++;
                    continue;
                }

                // Zephyrion: preserve intentionally high storm wall values
                if (island.hasStormWall && oldDiff[y][x] >= 40) {
                    newDiff[y][x] = oldDiff[y][x];
                    kept++;
                    continue;
                }

                // Distance to nearest town
                double dNearest = Double.MAX_VALUE;
                for (int[] town : island.towns) {
                    double d = Math.sqrt((x - town[0]) * (x - town[0])
                                       + (y - town[1]) * (y - town[1]));
                    if (d < dNearest) dNearest = d;
                }

                // Gradient: near town = minDiff, far from town = maxDiff
                double range = maxDiff - minDiff;
                double t = Math.max(0, Math.min(1, (dNearest - 8) / 45.0));
                int val = minDiff + (int)(range * t);

                // Clamp
                if (val < minDiff) val = minDiff;
                if (val > maxDiff) val = maxDiff;

                newDiff[y][x] = val;
                patched++;
            }
        }

        // Replace in JSON
        String newJson = replaceSpawnDifficulty(json, newDiff, island.width, island.height);
        Files.write(Path.of(island.rfmapPath), newJson.getBytes(StandardCharsets.ISO_8859_1));

        // Print stats
        System.out.println("  Patched: " + patched + " tiles, Skipped (zero): " + skipped
            + (kept > 0 ? ", Kept (storm): " + kept : ""));

        // Distribution
        Map<Integer, Integer> dist = new TreeMap<>();
        for (int y = 0; y < island.height; y++) {
            for (int x = 0; x < island.width; x++) {
                if (newDiff[y][x] > 0) {
                    dist.merge(newDiff[y][x], 1, Integer::sum);
                }
            }
        }
        System.out.println("  Difficulty distribution:");
        for (Map.Entry<Integer, Integer> e : dist.entrySet()) {
            int lvl = 1 + (e.getKey() * 49) / 99;
            System.out.printf("    diff %2d → level %2d : %5d tiles%n",
                e.getKey(), lvl, e.getValue());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  JSON parsing / replacement for spawnDifficulty
    // ═══════════════════════════════════════════════════════════════════════

    static int[][] parseSpawnDifficulty(String json, int width, int height) {
        int[][] result = new int[height][width];
        int idx = json.indexOf("\"spawnDifficulty\"");
        if (idx < 0) throw new RuntimeException("spawnDifficulty not found in JSON");

        int outerBracket = json.indexOf('[', idx);
        int pos = outerBracket + 1;

        for (int row = 0; row < height; row++) {
            int rowStart = json.indexOf('[', pos);
            int rowEnd = json.indexOf(']', rowStart);
            String rowContent = json.substring(rowStart + 1, rowEnd);
            String[] parts = rowContent.split(",");
            for (int col = 0; col < width && col < parts.length; col++) {
                result[row][col] = Integer.parseInt(parts[col].trim());
            }
            pos = rowEnd + 1;
        }
        return result;
    }

    static String replaceSpawnDifficulty(String json, int[][] diff, int width, int height) {
        int idx = json.indexOf("\"spawnDifficulty\"");
        int outerStart = json.indexOf('[', idx);

        // Find matching closing bracket
        int depth = 0;
        int outerEnd = -1;
        for (int i = outerStart; i < json.length(); i++) {
            if (json.charAt(i) == '[') depth++;
            else if (json.charAt(i) == ']') {
                depth--;
                if (depth == 0) { outerEnd = i + 1; break; }
            }
        }

        // Build replacement
        StringBuilder sb = new StringBuilder();
        sb.append("[\n");
        for (int y = 0; y < height; y++) {
            sb.append("    [");
            for (int x = 0; x < width; x++) {
                sb.append(diff[y][x]);
                if (x < width - 1) sb.append(',');
            }
            sb.append(']');
            if (y < height - 1) sb.append(',');
            sb.append('\n');
        }
        sb.append("  ]");

        return json.substring(0, outerStart) + sb.toString() + json.substring(outerEnd);
    }
}

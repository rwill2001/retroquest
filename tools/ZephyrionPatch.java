import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/**
 * Patches data/overworlds/zephyrion.rfmap to add the Portal to Verdant
 * Mangroves (\uE068) on the southwest coast at tile position (25, 165).
 *
 * Run from the project root:
 *   javac -encoding UTF-8 tools/ZephyrionPatch.java -d tools/
 *   java -cp tools ZephyrionPatch
 */
public class ZephyrionPatch {

    static final int TARGET_COL = 25;
    static final int TARGET_ROW = 165;
    static final String RFMAP = "data/overworlds/zephyrion.rfmap";
    // Unicode escape for portal tile \uE068
    static final String PORTAL_CHAR = "\\uE068";

    public static void main(String[] args) throws Exception {
        // Read as ISO-8859-1 to handle any byte values safely
        String content = Files.readString(Path.of(RFMAP), StandardCharsets.ISO_8859_1);

        int tilesStart = content.indexOf("\"tiles\"");
        if (tilesStart < 0) { System.out.println("ERROR: 'tiles' not found"); return; }

        int arrStart = content.indexOf('[', tilesStart);
        int pos = arrStart + 1;
        int currentRow = 0;

        while (currentRow < TARGET_ROW) {
            pos = content.indexOf('[', pos);
            pos = content.indexOf(']', pos) + 1;
            currentRow++;
        }

        int rowStart = content.indexOf('[', pos);
        int colPos = rowStart + 1;
        int currentCol = 0;

        while (currentCol < TARGET_COL) {
            int q1 = content.indexOf('"', colPos);
            int q2 = content.indexOf('"', q1 + 1);
            colPos = q2 + 1;
            if (colPos < content.length() && content.charAt(colPos) == ',') colPos++;
            currentCol++;
        }

        int q1 = content.indexOf('"', colPos);
        int q2 = content.indexOf('"', q1 + 1);

        String before = content.substring(0, q1 + 1);
        String after = content.substring(q2);
        String patched = before + PORTAL_CHAR + after;

        Files.writeString(Path.of(RFMAP), patched, StandardCharsets.ISO_8859_1);
        System.out.println("Patched " + RFMAP + ": placed portal to Sylvandar at (" + TARGET_COL + ", " + TARGET_ROW + ")");
    }
}

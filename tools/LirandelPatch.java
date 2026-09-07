import java.io.*;
import java.nio.file.*;

/**
 * Patches data/overworlds/lirandel.rfmap to add the Portal to Pyralis ('G')
 * on the south coast at tile position (55, 85).
 *
 * Run from the project root:
 *   javac tools/LirandelPatch.java -d tools/
 *   java -cp tools LirandelPatch
 */
public class LirandelPatch {

    static final int TARGET_COL = 55;
    static final int TARGET_ROW = 85;
    static final String RFMAP = "data/overworlds/lirandel.rfmap";

    public static void main(String[] args) throws Exception {
        String content = Files.readString(Path.of(RFMAP));

        // Parse the tiles array to find the exact character position to replace.
        // The tiles array is: "tiles": [ [row0], [row1], ... ]
        // Each row is: ["c","c","c",...]
        // We need to find row TARGET_ROW and replace the char at TARGET_COL.

        int tilesStart = content.indexOf("\"tiles\"");
        if (tilesStart < 0) { System.out.println("ERROR: 'tiles' not found"); return; }

        // Find the start of the array
        int arrStart = content.indexOf('[', tilesStart);

        // Walk through rows
        int pos = arrStart + 1; // skip the outer '['
        int currentRow = 0;

        while (currentRow < TARGET_ROW) {
            // Find the next row array (opening '[')
            pos = content.indexOf('[', pos);
            // Skip past the closing ']'
            pos = content.indexOf(']', pos) + 1;
            currentRow++;
        }

        // Now find the opening '[' of TARGET_ROW
        int rowStart = content.indexOf('[', pos);

        // Walk through columns in this row
        int colPos = rowStart + 1; // skip '['
        int currentCol = 0;

        while (currentCol < TARGET_COL) {
            // Each element is "c" possibly followed by comma
            // Find the opening quote
            int q1 = content.indexOf('"', colPos);
            // Find the closing quote
            int q2 = content.indexOf('"', q1 + 1);
            colPos = q2 + 1;
            // Skip comma if present
            if (colPos < content.length() && content.charAt(colPos) == ',') colPos++;
            currentCol++;
        }

        // Now colPos points to just before the TARGET_COL element
        int q1 = content.indexOf('"', colPos);
        int q2 = content.indexOf('"', q1 + 1);

        // Replace the character between q1 and q2 with 'G'
        String before = content.substring(0, q1 + 1);
        String after = content.substring(q2);
        String patched = before + "G" + after;

        Files.writeString(Path.of(RFMAP), patched);
        System.out.println("Patched " + RFMAP + ": placed 'G' (Portal to Pyralis) at (" + TARGET_COL + ", " + TARGET_ROW + ")");
    }
}

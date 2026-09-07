import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/**
 * Patches data/overworlds/thalorax.rfmap to add the forward portal tile
 * to Umbryn at position (170, 45).
 *
 * Run from project root:
 *   javac -cp src/main/resources:$(find ~/.m2 -name "gson-*.jar" | head -1) tools/ThaloraxPatch.java -d tools/
 *   java -cp tools:$(find ~/.m2 -name "gson-*.jar" | head -1) ThaloraxPatch
 *
 * Or simpler — just uses string manipulation:
 *   javac tools/ThaloraxPatch.java -d tools/
 *   java -cp tools ThaloraxPatch
 */
public class ThaloraxPatch {

    public static void main(String[] args) throws Exception {
        String path = "data/overworlds/thalorax.rfmap";
        String content = new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);

        // The file is multi-line JSON with one tile element per line inside
        // a "tiles" 2D array. Structure:
        //   "tiles": [
        //     [           <-- row 0 start
        //       "~",      <-- col 0
        //       "~",      <-- col 1
        //       ...
        //     ],          <-- row 0 end
        //     [           <-- row 1 start
        //       ...

        // Strategy: Find the tiles array, count rows/columns, replace the target.
        // Target: row 45, col 170 -> replace with "\uE088"

        int targetRow = 45;
        int targetCol = 170;

        // Find "tiles": [
        int tilesIdx = content.indexOf("\"tiles\"");
        int firstBracket = content.indexOf('[', tilesIdx);

        // Now count row starts (each row starts with '[')
        int pos = firstBracket + 1; // skip outer [
        int currentRow = 0;

        while (currentRow < targetRow) {
            // Find next row's opening [
            pos = content.indexOf('[', pos);
            if (pos < 0) break;
            // Find closing ] of this row
            pos = content.indexOf(']', pos);
            if (pos < 0) break;
            pos++;
            currentRow++;
        }

        // Now find the target row's opening [
        int rowStart = content.indexOf('[', pos);
        int rowEnd = content.indexOf(']', rowStart);

        // Extract row content
        String rowContent = content.substring(rowStart + 1, rowEnd);

        // Split by comma to find elements
        // Elements are like: "\n      \"~\"" (with whitespace)
        String[] elems = rowContent.split(",");

        // Replace element at targetCol
        // Keep same whitespace pattern
        String oldElem = elems[targetCol];
        // Replace the quoted value, preserving leading whitespace
        int quoteStart = oldElem.indexOf('"');
        int quoteEnd = oldElem.lastIndexOf('"');
        String prefix = oldElem.substring(0, quoteStart);
        elems[targetCol] = prefix + "\"\\uE088\"";

        // Reconstruct
        String newRowContent = String.join(",", elems);
        String newContent = content.substring(0, rowStart + 1)
                          + newRowContent
                          + content.substring(rowEnd);

        Files.write(Paths.get(path), newContent.getBytes(StandardCharsets.UTF_8));
        System.out.println("Patched thalorax.rfmap: placed portal \\uE088 at (" + targetCol + ", " + targetRow + ")");
    }
}

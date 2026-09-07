import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/**
 * Patches data/overworlds/umbryn.rfmap to add the forward portal tile
 * to Bellorak at position (60, 30).
 *
 * Run from project root:
 *   javac tools/UmbrynBellorakPatch.java -d tools/
 *   java -cp tools UmbrynBellorakPatch
 */
public class UmbrynBellorakPatch {

    public static void main(String[] args) throws Exception {
        String path = "data/overworlds/umbryn.rfmap";
        String content = new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);

        int targetRow = 30;
        int targetCol = 60;

        // Find "tiles": [
        int tilesIdx = content.indexOf("\"tiles\"");
        int firstBracket = content.indexOf('[', tilesIdx);

        // Count rows to reach target
        int pos = firstBracket + 1;
        int currentRow = 0;

        while (currentRow < targetRow) {
            pos = content.indexOf('[', pos);
            if (pos < 0) break;
            pos = content.indexOf(']', pos);
            if (pos < 0) break;
            pos++;
            currentRow++;
        }

        // Find the target row
        int rowStart = content.indexOf('[', pos);
        int rowEnd = content.indexOf(']', rowStart);

        // Extract row content and split
        String rowContent = content.substring(rowStart + 1, rowEnd);
        String[] elems = rowContent.split(",");

        // Replace element at targetCol with portal char \uE098
        String oldElem = elems[targetCol];
        int quoteStart = oldElem.indexOf('"');
        String prefix = oldElem.substring(0, quoteStart);
        elems[targetCol] = prefix + "\"\\uE098\"";

        // Reconstruct
        String newRowContent = String.join(",", elems);
        String newContent = content.substring(0, rowStart + 1)
                          + newRowContent
                          + content.substring(rowEnd);

        Files.write(Paths.get(path), newContent.getBytes(StandardCharsets.UTF_8));
        System.out.println("Patched umbryn.rfmap: placed portal \\uE098 at (" + targetCol + ", " + targetRow + ")");
    }
}

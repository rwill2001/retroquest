import com.google.gson.*;
import java.io.*;

/**
 * Third pass: find floor tiles near target areas and place Memory Pools.
 */
public class MemoryPoolPatch3 {

    static Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public static void main(String[] args) throws Exception {
        // For remaining dungeons, search outward from target center until we find floor
        placeNearFloor("data/dungeons/archive_of_tears_2.rfmap", 20, 20, "M");
        placeNearFloor("data/dungeons/archive_of_tears_2.rfmap", 8, 22, "M");
        placeNearFloor("data/dungeons/archive_of_tears_3.rfmap", 16, 10, "M");
        System.out.println("Third pass complete.");
    }

    static void placeNearFloor(String path, int cx, int cy, String tileChar) throws Exception {
        File file = new File(path);
        JsonObject map = gson.fromJson(new InputStreamReader(new FileInputStream(file), "UTF-8"), JsonObject.class);
        JsonArray tiles = map.getAsJsonArray("tiles");

        // Spiral outward from (cx, cy) to find nearest floor tile
        for (int r = 0; r < 10; r++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (Math.abs(dx) != r && Math.abs(dy) != r) continue; // only check perimeter
                    int x = cx + dx, y = cy + dy;
                    if (y < 0 || y >= tiles.size()) continue;
                    JsonArray row = tiles.get(y).getAsJsonArray();
                    if (x < 0 || x >= row.size()) continue;
                    if (".".equals(row.get(x).getAsString())) {
                        row.set(x, new JsonPrimitive(tileChar));
                        try (OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(file), "UTF-8")) {
                            gson.toJson(map, w);
                        }
                        System.out.println("  Placed 'M' at (" + x + "," + y + ") in " + path);
                        return;
                    }
                }
            }
        }
        System.out.println("  FAIL: No floor tile near (" + cx + "," + cy + ") in " + path);
    }
}

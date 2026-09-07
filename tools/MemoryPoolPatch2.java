import com.google.gson.*;
import java.io.*;

/**
 * Second pass: places Memory Pool tiles that were skipped in first pass
 * due to non-floor tiles at target coordinates. Uses adjusted positions.
 */
public class MemoryPoolPatch2 {

    static Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public static void main(String[] args) throws Exception {
        // Archive of Tears L2: try nearby positions
        placeTile("data/dungeons/archive_of_tears_2.rfmap", 20, 22, "M");
        placeTile("data/dungeons/archive_of_tears_2.rfmap", 6, 20, "M");
        // Archive of Tears L3: try nearby positions
        placeTile("data/dungeons/archive_of_tears_3.rfmap", 20, 12, "M");
        placeTile("data/dungeons/archive_of_tears_3.rfmap", 18, 10, "M");
        // Forgotten City L2: try nearby positions
        placeTile("data/dungeons/forgotten_city_2.rfmap", 22, 15, "M");
        placeTile("data/dungeons/forgotten_city_2.rfmap", 8, 20, "M");

        System.out.println("Second pass complete.");
    }

    static void placeTile(String path, int x, int y, String tileChar) throws Exception {
        File file = new File(path);
        JsonObject map = gson.fromJson(new InputStreamReader(new FileInputStream(file), "UTF-8"), JsonObject.class);
        JsonArray tiles = map.getAsJsonArray("tiles");

        if (y >= tiles.size()) { System.out.println("  WARN: " + path + " y=" + y + " OOB"); return; }
        JsonArray row = tiles.get(y).getAsJsonArray();
        if (x >= row.size()) { System.out.println("  WARN: " + path + " x=" + x + " OOB"); return; }

        String existing = row.get(x).getAsString();
        if (!".".equals(existing)) {
            System.out.println("  SKIP: " + path + " (" + x + "," + y + ") = '" + existing + "'");
            return;
        }

        row.set(x, new JsonPrimitive(tileChar));
        try (OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(file), "UTF-8")) { gson.toJson(map, w); }
        System.out.println("  Placed 'M' at (" + x + "," + y + ") in " + path);
    }
}

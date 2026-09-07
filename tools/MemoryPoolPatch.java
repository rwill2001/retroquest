import com.google.gson.*;
import java.io.*;

/**
 * Patches Archive of Tears and Forgotten City dungeon maps to add
 * Memory Pool ('M') tiles at select locations.
 *
 * Run:
 *   javac -cp "%USERPROFILE%\.m2\repository\com\google\code\gson\gson\2.10.1\gson-2.10.1.jar" tools/MemoryPoolPatch.java -d tools/
 *   java -cp "tools;%USERPROFILE%\.m2\repository\com\google\code\gson\gson\2.10.1\gson-2.10.1.jar" MemoryPoolPatch
 */
public class MemoryPoolPatch {

    static Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public static void main(String[] args) throws Exception {
        // Archive of Tears: 2 Memory Pools per level (6 total)
        placeTile("data/dungeons/archive_of_tears_1.rfmap", 12, 20, "M"); // west wing alcove
        placeTile("data/dungeons/archive_of_tears_1.rfmap", 28, 18, "M"); // east wing alcove
        placeTile("data/dungeons/archive_of_tears_2.rfmap", 18, 22, "M"); // central hub
        placeTile("data/dungeons/archive_of_tears_2.rfmap", 8, 18, "M");  // west dark corridor
        placeTile("data/dungeons/archive_of_tears_3.rfmap", 22, 12, "M"); // outer ring
        placeTile("data/dungeons/archive_of_tears_3.rfmap", 16, 8, "M");  // near inner sanctum

        // Forgotten City: 2 Memory Pools per level (6 total)
        placeTile("data/dungeons/forgotten_city_1.rfmap", 15, 15, "M");   // residential area
        placeTile("data/dungeons/forgotten_city_1.rfmap", 25, 20, "M");   // city street
        placeTile("data/dungeons/forgotten_city_2.rfmap", 20, 15, "M");   // temple area
        placeTile("data/dungeons/forgotten_city_2.rfmap", 10, 25, "M");   // library area
        placeTile("data/dungeons/forgotten_city_3.rfmap", 18, 18, "M");   // undercrypt center
        placeTile("data/dungeons/forgotten_city_3.rfmap", 30, 25, "M");   // east passage

        System.out.println("Memory Pool tiles placed: 12 total across 6 dungeon levels.");
    }

    static void placeTile(String path, int x, int y, String tileChar) throws Exception {
        File file = new File(path);
        JsonObject map = gson.fromJson(new InputStreamReader(new FileInputStream(file), "UTF-8"), JsonObject.class);
        JsonArray tiles = map.getAsJsonArray("tiles");

        if (y >= tiles.size()) {
            System.out.println("  WARN: " + path + " y=" + y + " out of bounds (" + tiles.size() + " rows)");
            return;
        }

        JsonArray row = tiles.get(y).getAsJsonArray();
        if (x >= row.size()) {
            System.out.println("  WARN: " + path + " x=" + x + " out of bounds (" + row.size() + " cols)");
            return;
        }

        // Only place on floor tiles — don't overwrite walls/specials
        String existing = row.get(x).getAsString();
        if (!".".equals(existing)) {
            System.out.println("  SKIP: " + path + " (" + x + "," + y + ") = '" + existing + "' (not floor)");
            return;
        }

        row.set(x, new JsonPrimitive(tileChar));
        try (OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(file), "UTF-8")) {
            gson.toJson(map, w);
        }
        System.out.println("  Placed 'M' at (" + x + "," + y + ") in " + path);
    }
}

import com.google.gson.*;
import java.io.*;

/**
 * Patches data/overworlds/umbryn.rfmap to wire the Archive of Tears
 * dungeon entrance at (180, 55) to the "archive_of_tears" dungeon group.
 *
 * Run from project root:
 *   javac -cp "%USERPROFILE%\.m2\repository\com\google\code\gson\gson\2.10.1\gson-2.10.1.jar" tools/UmbrynPatch.java -d tools/
 *   java -cp "tools;%USERPROFILE%\.m2\repository\com\google\code\gson\gson\2.10.1\gson-2.10.1.jar" UmbrynPatch
 */
public class UmbrynPatch {

    static Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public static void main(String[] args) throws Exception {
        File file = new File("data/overworlds/umbryn.rfmap");
        JsonObject map = gson.fromJson(new InputStreamReader(new FileInputStream(file), "UTF-8"), JsonObject.class);

        JsonObject tileStates = map.has("initialTileStates")
                ? map.getAsJsonObject("initialTileStates") : new JsonObject();

        // Wire Archive of Tears entrance at (180, 55)
        JsonObject state = new JsonObject();
        state.addProperty("overrideId", "\u0000");
        JsonObject data = new JsonObject();
        data.addProperty("dungeonName", "archive_of_tears");
        state.add("data", data);
        tileStates.add("180,55", state);

        map.add("initialTileStates", tileStates);

        try (OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(file), "UTF-8")) {
            gson.toJson(map, w);
        }
        System.out.println("Umbryn entrance wired: 180,55 -> archive_of_tears");
    }
}

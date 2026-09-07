import com.google.gson.*;
import java.io.*;

/**
 * Fixes townEntrances in umbryn.rfmap — the original generator used wrong
 * field names ("name"/"x"/"y" instead of "townName"/"worldX"/"worldY"),
 * which got zeroed out during Gson re-serialization.
 */
public class UmbrynTownPatch {

    static Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public static void main(String[] args) throws Exception {
        File file = new File("data/overworlds/umbryn.rfmap");
        JsonObject map = gson.fromJson(new InputStreamReader(new FileInputStream(file), "UTF-8"), JsonObject.class);

        // Replace the broken townEntrances with correct field names
        JsonArray entrances = new JsonArray();

        JsonObject dusthaven = new JsonObject();
        dusthaven.addProperty("townName", "dusthaven");
        dusthaven.addProperty("worldX", 115);
        dusthaven.addProperty("worldY", 95);
        entrances.add(dusthaven);

        JsonObject hollow = new JsonObject();
        hollow.addProperty("townName", "the_hollow");
        hollow.addProperty("worldX", 55);
        hollow.addProperty("worldY", 110);
        entrances.add(hollow);

        JsonObject echoPoint = new JsonObject();
        echoPoint.addProperty("townName", "echo_point");
        echoPoint.addProperty("worldX", 175);
        echoPoint.addProperty("worldY", 50);
        entrances.add(echoPoint);

        map.add("townEntrances", entrances);

        try (OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(file), "UTF-8")) {
            gson.toJson(map, w);
        }
        System.out.println("Fixed townEntrances in umbryn.rfmap:");
        System.out.println("  dusthaven   -> (115, 95)");
        System.out.println("  the_hollow  -> (55, 110)");
        System.out.println("  echo_point  -> (175, 50)");
    }
}

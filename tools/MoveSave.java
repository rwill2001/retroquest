import com.google.gson.*;
import java.io.*;

/**
 * Moves a save file's player to a specific overworld position.
 * Usage: java MoveSave <savefile> <overworldName> <x> <y>
 */
public class MoveSave {
    public static void main(String[] args) throws Exception {
        if (args.length < 4) {
            System.out.println("Usage: java MoveSave <savefile> <overworldName> <x> <y>");
            return;
        }
        String savePath = args[0];
        String overworld = args[1];
        int x = Integer.parseInt(args[2]);
        int y = Integer.parseInt(args[3]);

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        JsonObject save;
        try (FileReader r = new FileReader(savePath)) {
            save = JsonParser.parseReader(r).getAsJsonObject();
        }

        // Update position
        save.addProperty("playerX", x);
        save.addProperty("playerY", y);
        save.addProperty("currentOverworldName", overworld);
        // Clear town (put on overworld)
        save.remove("currentTownName");
        save.addProperty("inDungeon", false);
        save.addProperty("currentDepth", 0);

        // Update player object position too
        if (save.has("player")) {
            JsonObject player = save.getAsJsonObject("player");
            player.addProperty("x", x);
            player.addProperty("y", y);
        }

        // Write back (compact, single line like original)
        try (FileWriter w = new FileWriter(savePath)) {
            new Gson().toJson(save, w);
        }

        String name = save.has("player") ? save.getAsJsonObject("player").get("name").getAsString() : "?";
        System.out.println("Moved " + name + " to " + overworld + " at (" + x + ", " + y + ")");
    }
}

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/**
 * Fixes save file to use correct top-level playerX/playerY fields
 * and places Gary on Umbryn next to the Archive of Tears dungeon.
 */
public class SavePatch2 {

    public static void main(String[] args) throws Exception {
        String path = "saves/retroquest_save_01.sav";

        Gson gson = new GsonBuilder().create();
        JsonObject save = gson.fromJson(
            new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8),
            JsonObject.class);

        // Set overworld to umbryn
        save.addProperty("currentOverworldName", "umbryn");

        // Set top-level playerX/playerY (this is what loadGame reads)
        // Dungeon entrance is at x=180, y=55; place Gary one tile west
        save.addProperty("playerX", 179);
        save.addProperty("playerY", 55);

        // Also update player.x/y for consistency
        JsonObject player = save.getAsJsonObject("player");
        player.addProperty("x", 179);
        player.addProperty("y", 55);

        // Not in dungeon or town
        save.addProperty("inDungeon", false);
        save.addProperty("currentDepth", 0);
        save.addProperty("currentTownName", (String) null);

        Files.write(Paths.get(path), gson.toJson(save).getBytes(StandardCharsets.UTF_8));
        System.out.println("Fixed: Gary at playerX=179, playerY=55 on umbryn overworld.");
    }
}

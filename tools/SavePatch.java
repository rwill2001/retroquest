import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/**
 * Patches a save file to place the player on the Umbryn overworld
 * next to the Archive of Tears dungeon entrance at (180, 55).
 *
 * Run:
 *   javac -cp "%USERPROFILE%\.m2\repository\com\google\code\gson\gson\2.10.1\gson-2.10.1.jar" tools/SavePatch.java -d tools/
 *   java -cp "tools;%USERPROFILE%\.m2\repository\com\google\code\gson\gson\2.10.1\gson-2.10.1.jar" SavePatch
 */
public class SavePatch {

    public static void main(String[] args) throws Exception {
        String path = "saves/retroquest_save_01.sav";
        String backup = path + ".prepatch";

        // Backup first
        Files.copy(Paths.get(path), Paths.get(backup), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        System.out.println("Backup: " + backup);

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        JsonObject save = gson.fromJson(
            new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8),
            JsonObject.class);

        // Set overworld to umbryn
        save.addProperty("currentOverworldName", "umbryn");

        // Set player position next to dungeon entrance (180, 55)
        // Place at (179, 55) — one tile west of the 'D' tile
        JsonObject player = save.getAsJsonObject("player");
        player.addProperty("x", 179);
        player.addProperty("y", 55);

        // Make sure not in dungeon or town
        save.addProperty("inDungeon", false);
        save.addProperty("currentDepth", 0);
        if (save.has("currentTown")) save.add("currentTown", JsonNull.INSTANCE);

        // Give key_of_depths if not already in inventory (needed to get here)
        // and archive_key (needed for dungeon gate)
        // Just ensure the player has reasonable stats for L20 area
        System.out.println("Player: " + player.get("name").getAsString()
            + ", Level " + player.get("level").getAsInt()
            + ", HP " + player.get("hp").getAsInt() + "/" + player.get("maxHp").getAsInt());

        Files.write(Paths.get(path), gson.toJson(save).getBytes(StandardCharsets.UTF_8));
        System.out.println("Patched " + path + ": Gary placed at (179, 55) on umbryn overworld.");
    }
}

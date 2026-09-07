import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Adds spawnWeights to island-specific tiles in data/tiles.json.
 * This ensures island-native monsters are heavily favored on their home islands.
 *
 * Run from project root:
 *   javac -encoding UTF-8 tools/SpawnWeightsPatch.java -d tools/
 *   java -cp tools SpawnWeightsPatch
 */
public class SpawnWeightsPatch {

    static final String TILES_FILE = "data/tiles.json";

    // Map of tile name → spawnWeights JSON to set
    static final Map<String, String> WEIGHTS = new LinkedHashMap<>();
    static {
        // Pyralis (already done via manual edit, but include for completeness/safety)
        String pyralis = "{\"slag_beast\":4,\"ember_sprite\":4,\"forge_golem\":6,\"ash_wraith\":4,\"lava_serpent\":4}";
        WEIGHTS.put("Volcanic Rock", pyralis);
        WEIGHTS.put("Ash Plains", "{\"slag_beast\":3,\"ember_sprite\":6,\"ash_wraith\":6,\"forge_golem\":4,\"lava_serpent\":3}");
        WEIGHTS.put("Volcanic Sand", "{\"slag_beast\":5,\"ember_sprite\":3,\"forge_golem\":2,\"ash_wraith\":2,\"lava_serpent\":2}");

        // Zephyrion (already done via manual edit)
        String zephyrion = "{\"storm_hawk\":5,\"gale_spirit\":4,\"sky_raider\":4,\"thunder_elemental\":4,\"cloud_wraith\":4}";
        WEIGHTS.put("Cloud Platform", zephyrion);
        WEIGHTS.put("Sky Grass", zephyrion);
        WEIGHTS.put("Sky Rock", zephyrion);
        WEIGHTS.put("Storm Cloud", zephyrion);

        // Sylvandar (already done via manual edit)
        String sylvandar = "{\"vine_strangler\":4,\"spore_phantom\":4,\"canopy_stalker\":4,\"root_golem\":4,\"blight_mother\":4}";
        WEIGHTS.put("Dense Forest", sylvandar);
        WEIGHTS.put("Mangrove Swamp", sylvandar);
        WEIGHTS.put("Moss Ground", sylvandar);
        WEIGHTS.put("Bioluminescent Fungi", sylvandar);

        // Thalorax
        String thalorax = "{\"pressure_hulk\":4,\"deep_coral_construct\":4,\"anglerfish_horror\":4,\"rune_scarred_shark\":4,\"abyssal_wraith\":4}";
        WEIGHTS.put("Deep Ocean Floor", thalorax);
        WEIGHTS.put("Kelp Forest", thalorax);
        WEIGHTS.put("Bioluminescent Sand", thalorax);
        WEIGHTS.put("Bone Field", thalorax);

        // Umbryn
        String umbryn = "{\"memory_shade\":4,\"hollow_one\":4,\"echo_knight\":4,\"archive_guardian\":4,\"grief_elemental\":4}";
        WEIGHTS.put("Shadow Trench Floor", umbryn);
        WEIGHTS.put("Memory Rift", umbryn);
        WEIGHTS.put("Ghost Path", umbryn);
        WEIGHTS.put("Crumbled Archive", umbryn);
        WEIGHTS.put("Silver Ruins", umbryn);

        // Bellorak
        String bellorak = "{\"war_wraith\":4,\"glory_hound\":4,\"iron_golem\":4,\"blood_oath_berserker\":4,\"the_undying\":4}";
        WEIGHTS.put("Scorched Earth", bellorak);
        WEIGHTS.put("Trench", bellorak);
        WEIGHTS.put("Crater", bellorak);
        WEIGHTS.put("War Camp Floor", bellorak);
        WEIGHTS.put("Arena Stone", bellorak);
    }

    public static void main(String[] args) throws Exception {
        String content = Files.readString(Path.of(TILES_FILE), StandardCharsets.UTF_8);

        int patched = 0;
        for (Map.Entry<String, String> entry : WEIGHTS.entrySet()) {
            String tileName = entry.getKey();
            String weights = entry.getValue();

            // Find the tile by name
            String namePattern = "\"name\": \"" + tileName + "\"";
            int nameIdx = content.indexOf(namePattern);
            if (nameIdx < 0) {
                System.out.println("  SKIP (not found): " + tileName);
                continue;
            }

            // Find the spawnWeights for this tile (search forward from name)
            String swKey = "\"spawnWeights\":";
            int swIdx = content.indexOf(swKey, nameIdx);
            if (swIdx < 0 || swIdx - nameIdx > 600) {
                System.out.println("  SKIP (no spawnWeights nearby): " + tileName);
                continue;
            }

            // Find the opening { and closing } of the current value
            int braceStart = content.indexOf('{', swIdx + swKey.length());
            int braceEnd = content.indexOf('}', braceStart);

            String current = content.substring(braceStart, braceEnd + 1);
            if (!current.equals("{}") && !current.contains("slag_beast") && !current.contains("storm_hawk")
                && !current.contains("vine_strangler") && !current.contains("pressure_hulk")
                && !current.contains("memory_shade") && !current.contains("war_wraith")) {
                // Has custom weights we shouldn't overwrite
                System.out.println("  SKIP (has custom weights): " + tileName + " → " + current);
                continue;
            }

            content = content.substring(0, braceStart) + weights + content.substring(braceEnd + 1);
            patched++;
            System.out.println("  SET: " + tileName + " → " + weights);
        }

        Files.writeString(Path.of(TILES_FILE), content, StandardCharsets.UTF_8);
        System.out.println("\nPatched " + patched + " tiles in " + TILES_FILE);
    }
}

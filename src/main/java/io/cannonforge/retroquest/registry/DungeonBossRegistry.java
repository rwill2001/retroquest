package io.cannonforge.retroquest.registry;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import io.cannonforge.retroquest.model.DungeonBoss;

/**
 * Loads {@code data/dungeon_bosses.json} — the bottom-of-dungeon bosses for the authored
 * dungeons that used to end in an ordinary room.
 *
 * <p>Read-only, and deliberately so. Nothing in the game writes bosses back, which is why there
 * is no save path here and no default set: a missing or unreadable file means those dungeons
 * simply end the way they did before, rather than the game refusing to start.
 */
public class DungeonBossRegistry {

    private static final List<DungeonBoss> bosses = new ArrayList<>();

    static {
        load();
    }

    private DungeonBossRegistry() {}

    private static void load() {
        File file = new File(System.getProperty("user.dir") + "/data/dungeon_bosses.json");
        if (!file.exists()) return;
        try (Reader r = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            Type listType = new TypeToken<List<DungeonBoss>>() {}.getType();
            List<DungeonBoss> loaded = new Gson().fromJson(r, listType);
            if (loaded != null) {
                for (DungeonBoss b : loaded) {
                    if (b != null && b.isPlayable()) bosses.add(b);
                }
            }
        } catch (Exception e) {
            // A malformed file costs the seven set pieces, not the game.
            System.err.println("[DungeonBossRegistry] Could not read dungeon_bosses.json ("
                    + e.getMessage() + "). Those dungeons will end without a boss.");
        }
    }

    /**
     * The boss waiting on this level, or {@code null}.
     *
     * @param dungeonName authored dungeon name, e.g. {@code "rootvault"}; null is safe
     * @param level       current depth
     */
    public static DungeonBoss find(String dungeonName, int level) {
        if (dungeonName == null) return null;
        for (DungeonBoss b : bosses) {
            if (b.level == level && dungeonName.equalsIgnoreCase(b.dungeon)) return b;
        }
        return null;
    }

    /**
     * The boss for this dungeon whatever level it sits on, or {@code null}. For callers that
     * know the dungeon but not the depth — the recorder's revelation preview, for one.
     */
    public static DungeonBoss findByDungeon(String dungeonName) {
        if (dungeonName == null) return null;
        for (DungeonBoss b : bosses) {
            if (dungeonName.equalsIgnoreCase(b.dungeon)) return b;
        }
        return null;
    }

    /** Every loaded boss, for the content checks. */
    public static List<DungeonBoss> all() {
        return java.util.Collections.unmodifiableList(bosses);
    }
}

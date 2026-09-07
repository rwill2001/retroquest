package io.cannonforge.retroquest.registry;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import io.cannonforge.retroquest.model.Monster;

/**
 * Singleton registry for all {@link Monster} definitions, backed by {@code data/monsters.json}.
 *
 * <p>On class load the registry reads from the JSON file (or seeds defaults via
 * {@link MonsterFactory#seedDefaults()} if absent). All mutations auto-save.
 * {@link #getAllMonsters()} returns a defensive copy.
 *
 * <h2>Random monster selection — {@code getRandomMonster(int targetLevel)}</h2>
 * <p>Level is clamped to 1–50, then the following chain is attempted in order:
 * <ol>
 *   <li><b>Exact-level pool (75%):</b> collect all registry monsters at {@code targetLevel};
 *       if non-empty, 75% chance to pick one via weighted selection.</li>
 *   <li><b>±3-level window (50%):</b> collect monsters within 3 levels of target;
 *       if non-empty, 50% chance to pick one.</li>
 *   <li><b>Procedural fallback:</b> {@link MonsterFactory#generate(int)} creates a
 *       stat-scaled monster on the fly.</li>
 * </ol>
 *
 * <h2>Weighted selection</h2>
 * <p>If the tile provides a {@code spawnWeights} map ({@code monsterId → weight}),
 * {@code pickWeighted()} accumulates weights and rolls a proportional random result,
 * enabling biome-specific encounter skew. Uniform selection is used when no weights exist.
 *
 * <h2>Public API</h2>
 * <pre>
 *   MonsterRegistry.getRandomMonster(level)      // combat-ready copy
 *   MonsterRegistry.getAllMonsters()              // defensive copy of registry
 *   MonsterRegistry.addMonster(m)                // add + save
 *   MonsterRegistry.updateMonster(m)             // replace by ID + save
 *   MonsterRegistry.removeMonster(id)            // remove + save
 * </pre>
 */
public class MonsterRegistry {

    private static final String MONSTERS_FILE = System.getProperty("user.dir") + "/data/monsters.json";
    private static final List<Monster> monsters = new ArrayList<>();

    /**
     * Set when monsters.json exists but could not be read. Factory defaults are still seeded
     * in memory so the game runs, but saving is refused — otherwise those defaults would
     * overwrite the real (merely unreadable) bestiary.
     */
    private static boolean loadFailed = false;

    static {
        loadMonsters();
        ensureFactoryDefaults();
    }

    private static void loadMonsters() {
        File file = new File(MONSTERS_FILE);
        if (!file.exists()) return; // genuinely absent — safe to seed and write

        try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            Type listType = new TypeToken<ArrayList<Monster>>(){}.getType();
            List<Monster> loaded = new Gson().fromJson(reader, listType);
            if (loaded == null) {
                loadFailed = true;
                System.err.println("[MonsterRegistry] *** monsters.json is present but contains no monster"
                        + " list. Running on factory defaults; saving is DISABLED so the file is not overwritten.");
                return;
            }
            monsters.addAll(loaded);
        } catch (Exception e) {
            loadFailed = true;
            monsters.clear(); // discard a partially-read list
            System.err.println("[MonsterRegistry] *** FAILED TO PARSE monsters.json: " + e
                    + " — running on factory defaults; saving is DISABLED so your bestiary is not overwritten.");
            e.printStackTrace();
        }
    }

    /**
     * Persists the registry to {@code data/monsters.json}.
     *
     * <p>Written to a sibling {@code .tmp} file and then moved into place, so a failure
     * mid-write cannot truncate the existing file. Refused outright if the file failed to
     * parse at startup.
     *
     * @return {@code true} if the file was written, {@code false} if the save was refused or failed
     */
    public static boolean saveMonsters() {
        if (loadFailed) {
            System.err.println("[MonsterRegistry] REFUSING to save monsters.json — it failed to load at"
                    + " startup and saving now would replace it with defaults. Repair or remove the file.");
            return false;
        }

        File target = new File(MONSTERS_FILE);
        if (target.getParentFile() != null) target.getParentFile().mkdirs();
        File tmp = new File(MONSTERS_FILE + ".tmp");

        try {
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8)) {
                new GsonBuilder().setPrettyPrinting().create().toJson(monsters, writer);
            }
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (Exception e) {
            System.err.println("[MonsterRegistry] FAILED to save monsters.json: " + e);
            e.printStackTrace();
            tmp.delete();
            return false;
        }
    }

    /**
     * Ensures every MonsterFactory default has an entry in the registry.
     *
     * <p>Each factory default has a stable ID like {@code "factory_kobold"}.
     * If an entry with that ID already exists (meaning the user has edited it),
     * it is left untouched — registry always wins.  Only genuinely missing
     * entries are inserted.  A single save is performed if anything was added.
     */
    private static void ensureFactoryDefaults() {
        List<Monster> defaults = MonsterFactory.seedDefaults();
        boolean added = false;
        for (Monster def : defaults) {
            boolean exists = monsters.stream().anyMatch(m -> m.getId().equals(def.getId()));
            if (!exists) {
                monsters.add(def);
                added = true;
            }
        }
        if (added) {
            saveMonsters();
        }
    }

    public static List<Monster> getAllMonsters() {
        return new ArrayList<>(monsters);
    }

    public static Monster getById(String id) {
        return monsters.stream().filter(m -> m.getId().equals(id)).findFirst().orElse(null);
    }

    public static void addMonster(Monster m) {
        monsters.add(m);
        saveMonsters();
    }

    public static void updateMonster(Monster updated) {
        for (int i = 0; i < monsters.size(); i++) {
            if (monsters.get(i).getId().equals(updated.getId())) {
                monsters.set(i, updated);
                saveMonsters();
                return;
            }
        }
        addMonster(updated);
    }

    public static void removeMonster(String id) {
        monsters.removeIf(m -> m.getId().equals(id));
        saveMonsters();
    }

    /**
     * Returns a new monster scaled to the given target level.
     *
     * <p>Registry monsters (created in MonsterEditorDialog) whose level falls
     * within ±3 of {@code targetLevel} are eligible for selection.  When
     * exact-level matches exist there is a 75% chance one is returned;
     * when only ±3 matches exist there is a 50% chance; otherwise
     * {@link MonsterFactory} generates a procedural monster.  This blends
     * hand-crafted monsters with the procedural pool so that custom entries
     * appear naturally in encounters.
     *
     * @param targetLevel desired monster level (1–50)
     * @return a new combat-ready {@link Monster}
     */
    public static Monster getRandomMonster(int targetLevel) {
        return getRandomMonster(targetLevel, java.util.Collections.emptyMap());
    }

    /**
     * Selects a monster for combat scaled to {@code targetLevel}, optionally
     * biased by the tile's {@code spawnWeights} map (monster ID → relative weight;
     * default weight is 1).  A weight of 4 makes that monster four times as likely
     * to be chosen from the registry pool as an unweighted monster.
     */
    public static Monster getRandomMonster(int targetLevel, java.util.Map<String, Integer> spawnWeights) {
        targetLevel = Math.max(1, Math.min(50, targetLevel));
        final int tl = targetLevel;

        // 5% chance to spawn a friendly monster instead of a hostile one
        if (Math.random() < 0.05) {
            List<Monster> friendlies = new ArrayList<>();
            for (Monster m : monsters) if (m.isFriendly()) friendlies.add(m);
            if (!friendlies.isEmpty()) {
                return new Monster(friendlies.get((int)(Math.random() * friendlies.size())));
            }
        }

        // Prefer exact-level registry matches (75% chance) — ensures named monsters
        // like Corrupted Crab dominate the level they're designed for.
        List<Monster> exact = new ArrayList<>();
        for (Monster m : monsters) {
            if (m.getLevel() == tl && !m.isFriendly()) exact.add(m);
        }
        if (!exact.isEmpty() && Math.random() < 0.75) {
            return pickWeighted(exact, spawnWeights);
        }

        // Fall back to a level window (25% chance) for variety.
        //
        // The window is symmetric (±3) once the player has some levels behind them, but it is
        // capped ABOVE at low levels: a level-1 beach could otherwise roll a level-4 monster
        // at a 14 HP starting character, which killed new characters over and over in testing.
        // Below is not clamped the same way — an easy monster is never the problem.
        // Asymmetric on purpose. An island's spawn band already spans five or six monster
        // levels from its beach to its far side, so the window does not need to supply the
        // variety — and a symmetric +3 on top of the band's own top reached into the NEXT
        // island's roster: Sky Raiders on Lirandel, Root Golems on Pyralis, The Undying on
        // Umbryn. Two is a hard encounter; three was another island's problem.
        final int up = Math.min(2, Math.max(1, tl - 1));
        List<Monster> nearby = new ArrayList<>();
        for (Monster m : monsters) {
            int d = m.getLevel() - tl;
            if (d >= -3 && d <= up && !m.isFriendly()) nearby.add(m);
        }
        if (!nearby.isEmpty() && Math.random() < 0.50) {
            return pickWeighted(nearby, spawnWeights);
        }

        return MonsterFactory.generate(targetLevel);
    }

    /** Picks one monster from {@code pool} using weighted random selection. */
    private static Monster pickWeighted(List<Monster> pool, java.util.Map<String, Integer> weights) {
        if (weights.isEmpty()) {
            return new Monster(pool.get((int)(Math.random() * pool.size())));
        }
        int total = 0;
        for (Monster m : pool) total += Math.max(1, weights.getOrDefault(m.getId(), 1));
        int roll = (int)(Math.random() * total);
        for (Monster m : pool) {
            roll -= Math.max(1, weights.getOrDefault(m.getId(), 1));
            if (roll < 0) return new Monster(m);
        }
        return new Monster(pool.get(pool.size() - 1));
    }
}

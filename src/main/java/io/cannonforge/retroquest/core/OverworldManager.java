package io.cannonforge.retroquest.core;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import io.cannonforge.retroquest.model.MapData;
import io.cannonforge.retroquest.model.NPC;
import io.cannonforge.retroquest.model.TownEntrance;

/**
 * Manages loading and saving overworld {@link MapData} files from the file system.
 *
 * <p>Overworld maps are stored as {@code .rfmap} JSON files under the configured
 * overworlds directory. This class provides methods to load a named overworld,
 * save the current one, and enumerate all available overworld names.
 */
public class OverworldManager {

    private static final String OVERWORLDS_DIR = "data/overworlds/";
    private MapData currentOverworld;
    private String currentMapName;
    /** Set when the overworld on disk could not be parsed; blocks saving over it. */
    private boolean loadFailed = false;

    public OverworldManager() {
        loadOrCreateDefault();
    }

    private void loadOrCreateDefault() {
        currentMapName = io.cannonforge.retroquest.model.GameConfig.get().getStartingOverworld();
        loadOverworld(currentMapName);
    }

    public void loadOverworld(String mapName) {
        File file = new File(OVERWORLDS_DIR + mapName.toLowerCase() + ".rfmap");
        new File(OVERWORLDS_DIR).mkdirs();

        if (file.exists()) {
            try {
                currentOverworld = MapData.load(file);
                currentMapName = mapName;
                return;
            } catch (Exception e) {
                // The file is there but unreadable. Fall back to an empty map IN MEMORY ONLY —
                // writing here would destroy the authored island we just failed to parse.
                System.err.println("[OverworldManager] Failed to load " + mapName
                        + " (" + e.getMessage() + "). Using an empty map; the file is left untouched.");
                currentOverworld = MapData.createNew(MapData.MapType.OVERWORLD, mapName, 140, 90);
                currentMapName = mapName;
                loadFailed = true;
                return;
            }
        }

        // Genuinely new overworld — safe to create and persist
        currentOverworld = MapData.createNew(MapData.MapType.OVERWORLD, mapName, 140, 90);
        currentMapName = mapName;
        loadFailed = false;
        saveCurrent();
    }

    /** True when the current map is an in-memory placeholder for a file that would not parse. */
    public boolean isLoadFailed() { return loadFailed; }

    public void saveCurrent() {
        if (loadFailed) {
            System.err.println("[OverworldManager] Refusing to save over unreadable map " + currentMapName);
            return;
        }
        new File(OVERWORLDS_DIR).mkdirs();
        File file = new File(OVERWORLDS_DIR + currentMapName.toLowerCase() + ".rfmap");
        try {
            currentOverworld.save(file);
        } catch (Exception e) {
            System.err.println("[OverworldManager] Failed to save overworld " + currentMapName);
        }
    }

    public char[][] getCurrentMap() {
        return currentOverworld.tiles;
    }

    public List<NPC> getNpcs() {
        return currentOverworld.npcs;
    }

    public List<TownEntrance> getTownEntrances() {
        return currentOverworld.townEntrances != null ? currentOverworld.townEntrances : java.util.Collections.emptyList();
    }

    public int getSpawnDifficulty(int x, int y) {
        if (y < 0 || y >= currentOverworld.height || x < 0 || x >= currentOverworld.width) return 5;
        return currentOverworld.spawnDifficulty[y][x];
    }

    public String getCurrentMapName() {
        return currentMapName;
    }

    public MapData getMapData() {
        return currentOverworld;
    }

    // For future teleporters
    public List<String> getAllOverworldNames() {
        File dir = new File(OVERWORLDS_DIR);
        List<String> names = new ArrayList<>();
        if (dir.exists()) {
            File[] files = dir.listFiles((d, n) -> n.endsWith(".rfmap"));
            if (files != null) {
                for (File f : files) {
                    names.add(f.getName().replace(".rfmap", ""));
                }
            }
        }
        return names;
    }
}

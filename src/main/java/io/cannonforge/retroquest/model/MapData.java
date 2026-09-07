package io.cannonforge.retroquest.model;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Serializable representation of a map (overworld or town).
 *
 * <p>Contains the tile grid, spawn-difficulty grid, NPC list, town entrances,
 * and overworld teleporters. Maps are persisted as pretty-printed JSON via Gson.
 */
public class MapData {
    public enum MapType { OVERWORLD, TOWN, DUNGEON }

    public MapType type;
    public String name = "New Map";
    public int width, height;
    public char[][] tiles;
    public int[][] spawnDifficulty;

    /** Player spawn position when entering this town. Ignored for OVERWORLD maps. */
    public int interiorEntryX = 10;
    public int interiorEntryY = 13;

    public List<NPC> npcs = new ArrayList<>();
    public List<TownEntrance> townEntrances = new ArrayList<>();
    public List<OverworldTeleporter> overworldTeleporters = new ArrayList<>();

    /** Maximum dungeon depth accessible from this overworld. Ignored for TOWN maps. */
    public int maxDungeonDepth = 50;

    /** For DUNGEON maps: which level this map represents (1-based). */
    public int dungeonLevel = 1;

    /** For DUNGEON maps: group name linking multiple levels (e.g. "ember_caverns"). */
    public String dungeonGroup = null;

    /**
     * For DUNGEON maps: authored display name for this level (e.g. "The Root Vestibule").
     * Present in some .rfmap files; kept so an editor round-trip does not drop it.
     * {@code null} when unused, so Gson omits it from maps that never had it.
     */
    public String levelName = null;

    /**
     * For DUNGEON maps: authored level number as written by older tooling — a duplicate of
     * {@link #dungeonLevel} that exists in some .rfmap files. Kept so an editor round-trip
     * does not drop it. {@code null} when unused.
     */
    public Integer level = null;

    /** Per-coordinate initial tile states authored in RetroForge. Key = "x,y". */
    public Map<String, TileState> initialTileStates = null;

    // Legacy (kept for smooth migration)
    public static class NPCPlacement {
        public String name;
        public NPC.Type type;
        public int x, y;
        public String dialog = "Hello traveler...";
    }

    public MapData() {} // Gson

    public static MapData createNew(MapType type, String name, int w, int h) {
        MapData md = new MapData();
        md.type = type;
        md.name = name;
        md.width = Math.min(w, 2048);
        md.height = Math.min(h, 2048);
        md.tiles = new char[md.height][md.width];
        md.spawnDifficulty = new int[md.height][md.width];
        md.npcs = new ArrayList<>();
        md.townEntrances = new ArrayList<>();
        md.initialTileStates = new HashMap<>();

        char base = switch (type) {
            case OVERWORLD -> '.';
            case TOWN      -> 'F';
            case DUNGEON   -> 'W';  // dungeons default to walls; carve rooms from them
        };
        int defaultSpawn = (type == MapType.OVERWORLD) ? 5 : 0;
        for (int y = 0; y < md.height; y++) {
            for (int x = 0; x < md.width; x++) {
                md.tiles[y][x] = base;
                md.spawnDifficulty[y][x] = defaultSpawn;
            }
        }
        // Carve a small starter room for new dungeon maps
        if (type == MapType.DUNGEON) {
            int cx = md.width / 2, cy = md.height / 2;
            for (int dy = cy - 2; dy <= cy + 2; dy++)
                for (int dx = cx - 2; dx <= cx + 2; dx++)
                    if (dy >= 0 && dy < md.height && dx >= 0 && dx < md.width)
                        md.tiles[dy][dx] = '.';
            md.tiles[cy][cx] = 's'; // stairway at center
            md.interiorEntryX = cx;
            md.interiorEntryY = cy;
        }
        return md;
    }

    public void save(File file) throws IOException {
        new File(file.getParent()).mkdirs();
        try (Writer w = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            new GsonBuilder().setPrettyPrinting().create().toJson(this, w);
        }
    }

    public static MapData load(File file) throws IOException {
        try (Reader r = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            MapData md = new Gson().fromJson(r, MapData.class);
            // An empty or truncated file deserialises to null (or to a map with no tiles).
            // Callers treat any failure as "brand new map", so this MUST throw rather than
            // hand back an empty map that would then be written over the authored file.
            if (md == null || md.tiles == null || md.tiles.length == 0)
                throw new IOException("Malformed or empty map file: " + file.getPath());
            if (md.npcs == null) md.npcs = new ArrayList<>();
            if (md.townEntrances == null) md.townEntrances = new ArrayList<>();
            if (md.overworldTeleporters == null) md.overworldTeleporters = new ArrayList<>();
            if (md.initialTileStates == null) md.initialTileStates = new HashMap<>();
            if (md.spawnDifficulty == null && md.tiles != null) {
                md.spawnDifficulty = new int[md.height][md.width];
            }
            // Auto-migrate legacy OverworldTeleporters to per-instance TileState
            md.migrateOverworldTeleporters();
            return md;
        }
    }

    /** Converts legacy {@code overworldTeleporters} to per-instance TileState entries. */
    private void migrateOverworldTeleporters() {
        if (overworldTeleporters == null || overworldTeleporters.isEmpty()) return;
        for (OverworldTeleporter tp : overworldTeleporters) {
            String key = tp.x() + "," + tp.y();
            TileState ts = initialTileStates.get(key);
            if (ts == null) { ts = new TileState(); initialTileStates.put(key, ts); }
            if (!ts.data.containsKey("teleport_map")) {
                ts.set("teleport_map", tp.targetOverworld());
                ts.set("teleport_x", String.valueOf(tp.targetX() == -1 ? width / 2 : tp.targetX()));
                ts.set("teleport_y", String.valueOf(tp.targetY() == -1 ? height / 2 : tp.targetY()));
            }
        }
        overworldTeleporters.clear();
    }

    public void resize(int newW, int newH) {
        char[][] newTiles = new char[newH][newW];
        int[][] newDiff = new int[newH][newW];
        for (int y = 0; y < Math.min(height, newH); y++) {
            System.arraycopy(tiles[y], 0, newTiles[y], 0, Math.min(width, newW));
            System.arraycopy(spawnDifficulty[y], 0, newDiff[y], 0, Math.min(width, newW));
        }
        tiles = newTiles;
        spawnDifficulty = newDiff;
        width = newW;
        height = newH;

        // Remove NPCs, Town Entrances, Teleporters, and initial tile states outside new bounds
        npcs.removeIf(n -> n.getX() >= newW || n.getY() >= newH);
        townEntrances.removeIf(t -> t.worldX() >= newW || t.worldY() >= newH);
        overworldTeleporters.removeIf(tp -> tp.x() >= newW || tp.y() >= newH);
        if (initialTileStates != null) {
            initialTileStates.entrySet().removeIf(entry -> {
                String[] parts = entry.getKey().split(",");
                if (parts.length != 2) return true;
                try {
                    int tx = Integer.parseInt(parts[0]);
                    int ty = Integer.parseInt(parts[1]);
                    return tx >= newW || ty >= newH;
                } catch (NumberFormatException e) { return true; }
            });
        }
    }

    public List<NPC> getNpcs() { return npcs; }
}

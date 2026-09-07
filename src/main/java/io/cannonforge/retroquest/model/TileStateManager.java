package io.cannonforge.retroquest.model;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import io.cannonforge.retroquest.core.SaveData;

/**
 * Static helper for reading and writing per-tile instance state stored in
 * {@link SaveData#getOrCreateTileStates()}.
 *
 * <p>State map key format: {@code "<mapKey>:<x>,<y>"}. Because {@code mapKey}
 * itself may contain colons (e.g. {@code "dungeon:storm_spire:3"} or
 * {@code "town:Stonehaven"}), the coordinate suffix is always parsed from the last
 * {@code ':'}.
 */
public class TileStateManager {

    private TileStateManager() {}

    // ── Key helper ────────────────────────────────────────────────────────────

    public static String stateKey(String mapKey, int x, int y) {
        return mapKey + ":" + x + "," + y;
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    /** Returns the state for this coordinate, or {@code null} if absent. */
    public static TileState get(SaveData sd, String mapKey, int x, int y) {
        if (sd == null) return null;
        return sd.getOrCreateTileStates().get(stateKey(mapKey, x, y));
    }

    /** Returns the state for this coordinate, creating an empty one if absent. */
    public static TileState getOrCreate(SaveData sd, String mapKey, int x, int y) {
        if (sd == null) return new TileState();   // safety — not persisted
        return sd.getOrCreateTileStates()
                 .computeIfAbsent(stateKey(mapKey, x, y), k -> new TileState());
    }

    public static void setOverride(SaveData sd, String mapKey, int x, int y, char c) {
        getOrCreate(sd, mapKey, x, y).setOverride(c);
    }

    public static void setProperty(SaveData sd, String mapKey, int x, int y, String key, String value) {
        getOrCreate(sd, mapKey, x, y).set(key, value);
    }

    public static boolean getBoolProperty(SaveData sd, String mapKey, int x, int y, String key) {
        TileState ts = get(sd, mapKey, x, y);
        return ts != null && ts.getBool(key, false);
    }

    // ── Map application ───────────────────────────────────────────────────────

    /**
     * Pushes all stored char overrides for {@code mapKey} back into the live
     * {@code char[][]} grid.  Replaces the old {@code applyPersistedMutations}.
     */
    public static void applyTilesToMap(SaveData sd, String mapKey, char[][] map) {
        if (sd == null || map == null) return;
        String prefix = mapKey + ":";
        for (Map.Entry<String, TileState> entry : sd.getOrCreateTileStates().entrySet()) {
            if (!entry.getKey().startsWith(prefix)) continue;
            TileState ts = entry.getValue();
            if (!ts.hasOverride()) continue;
            // Coords follow the last ':' in the key
            String coords = entry.getKey().substring(entry.getKey().lastIndexOf(':') + 1);
            String[] parts = coords.split(",");
            if (parts.length != 2) continue;
            try {
                int x = Integer.parseInt(parts[0]);
                int y = Integer.parseInt(parts[1]);
                if (y >= 0 && y < map.length && x >= 0 && x < map[0].length)
                    map[y][x] = ts.overrideId;
            } catch (NumberFormatException ignored) {}
        }
    }

    // ── Migration ─────────────────────────────────────────────────────────────

    /**
     * Migrates saves written before dungeon map keys carried the dungeon's identity.
     * The old key was {@code "dungeon:<depth>:<x>,<y>"}, shared by all 13 authored dungeons
     * and the procedural one; the new key is {@code "dungeon:<dungeonId>:<depth>:<x>,<y>"}.
     *
     * <p>A legacy key cannot say which dungeon it came from, so every one of them is
     * attributed to {@code dungeonId} — the dungeon the save was written in. State belonging
     * to any other dungeon is lost rather than bleeding into the wrong map, which is the
     * graceful degradation an old save gets. Already-namespaced keys are left alone, so this
     * is a no-op on saves written by this version.
     */
    public static void migrateLegacyDungeonKeys(SaveData sd, String dungeonId) {
        if (sd == null || dungeonId == null || dungeonId.isEmpty()) return;
        Map<String, TileState> states = sd.getTileStates();
        if (states == null || states.isEmpty()) return;

        Map<String, TileState> migrated = new HashMap<>();
        Iterator<Map.Entry<String, TileState>> it = states.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, TileState> e = it.next();
            String key = e.getKey();
            if (!key.startsWith("dungeon:")) continue;
            String rest = key.substring("dungeon:".length());
            int colon = rest.indexOf(':');
            if (colon <= 0) continue;
            // A numeric first segment means the old depth-only namespace
            if (!isAllDigits(rest.substring(0, colon))) continue;
            migrated.put("dungeon:" + dungeonId + ":" + rest, e.getValue());
            it.remove();
        }
        for (Map.Entry<String, TileState> e : migrated.entrySet()) {
            states.putIfAbsent(e.getKey(), e.getValue());
        }
    }

    private static boolean isAllDigits(String s) {
        if (s.isEmpty()) return false;
        for (int i = 0; i < s.length(); i++)
            if (!Character.isDigit(s.charAt(i))) return false;
        return true;
    }

    /**
     * One-time migration: converts old {@code "mutated:*"} and
     * {@code "trap_once:*"} player flags into {@link TileState} entries, then
     * removes the migrated keys from {@link Player#getFlags()}.
     *
     * <p>Safe to call on saves that have no such flags — it is a no-op.
     */
    public static void migrateFromPlayerFlags(SaveData sd) {
        if (sd == null) return;
        Player player = sd.getPlayer();
        if (player == null) return;
        Map<String, String> flags = player.getMutableFlags();
        if (flags == null || flags.isEmpty()) return;

        Iterator<Map.Entry<String, String>> it = flags.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, String> e = it.next();
            String key = e.getKey();

            if (key.startsWith("mutated:")) {
                // Format: "mutated:<mapKey>:<x>,<y>"
                String rest = key.substring("mutated:".length());
                int lastColon = rest.lastIndexOf(':');
                if (lastColon < 0) continue;
                String mapKey = rest.substring(0, lastColon);
                String[] parts = rest.substring(lastColon + 1).split(",");
                if (parts.length != 2 || e.getValue().isEmpty()) continue;
                try {
                    int x = Integer.parseInt(parts[0]);
                    int y = Integer.parseInt(parts[1]);
                    setOverride(sd, mapKey, x, y, e.getValue().charAt(0));
                    it.remove();
                } catch (NumberFormatException ignored) {}

            } else if (key.startsWith("trap_once:")) {
                // Format: "trap_once:<mapKey>:<x>,<y>"
                String rest = key.substring("trap_once:".length());
                int lastColon = rest.lastIndexOf(':');
                if (lastColon < 0) continue;
                String mapKey = rest.substring(0, lastColon);
                String[] parts = rest.substring(lastColon + 1).split(",");
                if (parts.length != 2) continue;
                try {
                    int x = Integer.parseInt(parts[0]);
                    int y = Integer.parseInt(parts[1]);
                    setProperty(sd, mapKey, x, y, "triggered", "true");
                    it.remove();
                } catch (NumberFormatException ignored) {}
            }
        }
    }
}

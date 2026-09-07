package io.cannonforge.retroquest.core;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.cannonforge.retroquest.model.Dungeon;
import io.cannonforge.retroquest.model.Player;
import io.cannonforge.retroquest.model.TileState;

/**
 * Serializable snapshot of the full game state, written to and read from save files.
 *
 * <p>Stores the player, current location (town, overworld, or dungeon depth),
 * fog-of-war data, and metadata for the save-slot display name.
 */
public class SaveData {

    public static final int CURRENT_VERSION = 1;

    /**
     * Version of the format this save was written in. Deliberately defaults to 0 rather than
     * {@link #CURRENT_VERSION}: Gson uses the no-arg constructor, so a file that predates the
     * field would otherwise silently claim to be current and defeat any future migration.
     * The full constructor stamps the real version.
     */
    private int saveVersion = 0;

    private Player player;
    private String currentTownName;           // null = overworld
    private int playerX;
    private int playerY;
    private int townDoorX;                   // overworld position of the town entrance
    private int townDoorY;

    private boolean inDungeon;
    private int currentDepth;

    // Non-null when saved inside an authored dungeon (e.g. "storm_spire", "rootvault")
    private String authoredDungeonName;

    // Overworld position where the player entered the dungeon (for exit placement)
    private int dungeonEntryX;
    private int dungeonEntryY;

    // Set when the dungeon was entered from inside a town ('D' tiles exist in town maps);
    // the exit puts the player back in that town instead of at town coords on the overworld.
    private String dungeonEntryTownName;
    private int dungeonEntryTownDoorX;
    private int dungeonEntryTownDoorY;

    // NEW: Remembers which overworld map we were on
    private String currentOverworldName;

    // Last town the player safely entered — destination of the Teleport / Word of Recall spells.
    // Null on old saves; the loader then keeps the global config default.
    private String lastSafeTownName;

    // Permanently learned spells, stored by NAME. The Player's boolean[] is positional and its
    // spell table is rebuilt from code on load, so inserting or reordering a spell shifts every
    // flag; names survive that. Null on old saves — the positional array is then authoritative.
    private List<String> learnedSpells = null;

    // Fog of War persistence — legacy string-list format (kept for old-save compat)
    private Map<Integer, List<String>> revealedData = null;

    // Fog of War persistence — compact bitset format.
    // Key is "<dungeonId>:<depth>" (e.g. "storm_spire:2", "procedural:7"). Saves written before
    // dungeons were namespaced use a bare depth ("2") — see restore().
    private Map<String, String> revealedBitset = new HashMap<>();

    // Town fog of war persistence — bitset per town name (same encoding as dungeon)
    private Map<String, String> townRevealedBitset = new HashMap<>();

    // Per-tile instance state (door open/closed, trap triggered, etc.)
    // Null for old save files — getOrCreateTileStates() initialises on first access.
    private Map<String, TileState> tileStates = null;

    // Metadata for nice save slot names
    private int slot;
    private String displayName;
    private String timestamp;
    private String playerName;
    private int playerLevel;
    private String location;

    // Gson no-arg constructor
    public SaveData() {}

    // Full constructor
    public SaveData(Player player, Town currentTown, boolean inDungeon, int currentDepth,
                    Map<String, boolean[][]> revealed, int slot, String currentOverworldName,
                    Map<String, boolean[][]> townRevealed) {

        this.saveVersion = CURRENT_VERSION;
        this.player = player;
        this.currentTownName = (currentTown != null) ? currentTown.getName() : null;
        this.playerX = player.getX();
        this.playerY = player.getY();
        this.townDoorX = (currentTown != null) ? currentTown.getWorldDoorX() : 0;
        this.townDoorY = (currentTown != null) ? currentTown.getWorldDoorY() : 0;
        this.inDungeon = inDungeon;
        this.currentDepth = currentDepth;
        this.slot = slot;
        this.currentOverworldName = (currentOverworldName != null) ? currentOverworldName : "lirandel";

        this.playerName = player.getName();
        this.playerLevel = player.getLevel();
        this.location = getLocationString(currentTown, inDungeon, currentDepth);
        this.timestamp = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(new java.util.Date());

        this.displayName = (slot == 0)
                ? String.format("AUTO - %s - Lvl %d - %s - %s",
                        playerName, playerLevel, location, timestamp.split(" ")[0])
                : String.format("%02d - %s - Lvl %d - %s - %s",
                        slot, playerName, playerLevel, location, timestamp.split(" ")[0]);

        // Save fog of war — compact bitset encoding, one entry per "<dungeonId>:<depth>"
        for (Map.Entry<String, boolean[][]> entry : revealed.entrySet()) {
            boolean[][] map = entry.getValue();
            if (map == null || map.length == 0) continue;
            byte[] bits = new byte[(Dungeon.WIDTH * Dungeon.HEIGHT + 7) / 8];
            for (int y = 0; y < Dungeon.HEIGHT && y < map.length; y++) {
                for (int x = 0; x < Dungeon.WIDTH && x < map[y].length; x++) {
                    if (map[y][x]) {
                        int idx = y * Dungeon.WIDTH + x;
                        bits[idx / 8] |= (1 << (idx % 8));
                    }
                }
            }
            revealedBitset.put(entry.getKey(), Base64.getEncoder().encodeToString(bits));
        }

        // Save town fog of war
        if (townRevealed != null) {
            for (Map.Entry<String, boolean[][]> entry : townRevealed.entrySet()) {
                String townName = entry.getKey();
                boolean[][] map = entry.getValue();
                int h = map.length, w = map[0].length;
                // Encode dimensions + bitset: "w,h:base64"
                byte[] bits = new byte[(w * h + 7) / 8];
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        if (map[y][x]) {
                            int idx = y * w + x;
                            bits[idx / 8] |= (1 << (idx % 8));
                        }
                    }
                }
                townRevealedBitset.put(townName, w + "," + h + ":" + Base64.getEncoder().encodeToString(bits));
            }
        }
    }

    private String getLocationString(Town town, boolean inDungeon, int depth) {
        if (inDungeon) return "Dungeon Lvl " + depth;
        if (town != null) return town.getName();
        return "Overworld";
    }

    /**
     * Returns the dungeon namespace a legacy (depth-only) fog or tile-state key belongs to.
     * Old saves cannot say which dungeon a depth referred to, so the dungeon this save was
     * written in is the best available answer.
     */
    public String legacyDungeonId() {
        return (authoredDungeonName != null && !authoredDungeonName.isEmpty())
                ? authoredDungeonName
                : io.cannonforge.retroquest.model.DungeonViewState.PROCEDURAL_ID;
    }

    /** Expands a bare-depth fog key from an old save into the namespaced form. */
    private String fogKey(String rawKey) {
        return (rawKey.indexOf(':') < 0) ? legacyDungeonId() + ":" + rawKey : rawKey;
    }

    // Restore fog of war after loading
    public void restore(Retroquest game) {
        // Prefer new bitset format (v1+)
        if (revealedBitset != null && !revealedBitset.isEmpty()) {
            for (Map.Entry<String, String> entry : revealedBitset.entrySet()) {
                byte[] bits = Base64.getDecoder().decode(entry.getValue());
                boolean[][] vis = game.getRevealedForKey(fogKey(entry.getKey()));
                for (int y = 0; y < Dungeon.HEIGHT; y++) {
                    for (int x = 0; x < Dungeon.WIDTH; x++) {
                        int idx = y * Dungeon.WIDTH + x;
                        if (idx / 8 < bits.length && (bits[idx / 8] & (1 << (idx % 8))) != 0) {
                            vis[y][x] = true;
                        }
                    }
                }
            }
        } else if (revealedData != null) {
            // Fall back to legacy string-list format (v0 saves)
            for (Map.Entry<Integer, List<String>> entry : revealedData.entrySet()) {
                int depth = entry.getKey();
                List<String> list = entry.getValue();
                boolean[][] vis = game.getRevealedForKey(legacyDungeonId() + ":" + depth);
                for (String s : list) {
                    String[] parts = s.split(",");
                    if (parts.length < 2) continue;
                    int x = Integer.parseInt(parts[0]);
                    int y = Integer.parseInt(parts[1]);
                    if (x >= 0 && x < Dungeon.WIDTH && y >= 0 && y < Dungeon.HEIGHT) {
                        vis[y][x] = true;
                    }
                }
            }
        }

        // Restore town fog of war
        if (townRevealedBitset != null) {
            for (Map.Entry<String, String> entry : townRevealedBitset.entrySet()) {
                String townName = entry.getKey();
                String encoded = entry.getValue();
                int colonIdx = encoded.indexOf(':');
                if (colonIdx < 0) continue;
                String[] dims = encoded.substring(0, colonIdx).split(",");
                if (dims.length < 2) continue;
                int w = Integer.parseInt(dims[0]), h = Integer.parseInt(dims[1]);
                byte[] bits = Base64.getDecoder().decode(encoded.substring(colonIdx + 1));
                boolean[][] vis = game.getTownRevealed().computeIfAbsent(townName,
                        k -> new boolean[h][w]);
                for (int y = 0; y < h && y < vis.length; y++) {
                    for (int x = 0; x < w && x < vis[0].length; x++) {
                        int idx = y * w + x;
                        if (idx / 8 < bits.length && (bits[idx / 8] & (1 << (idx % 8))) != 0) {
                            vis[y][x] = true;
                        }
                    }
                }
            }
        }
    }

    // ==================== TILE STATE ====================

    /** Returns the tile-state map, or {@code null} if not yet initialised. */
    public Map<String, TileState> getTileStates() { return tileStates; }

    /**
     * Returns the tile-state map, creating an empty one if {@code null}.
     * Use this for all writes so old save files (where Gson deserialises the
     * field as {@code null}) are handled transparently.
     */
    public Map<String, TileState> getOrCreateTileStates() {
        if (tileStates == null) tileStates = new HashMap<>();
        return tileStates;
    }

    // ==================== GETTERS ====================
    public int getSaveVersion() { return saveVersion; }
    public Player getPlayer() { return player; }
    public String getCurrentTownName() { return currentTownName; }
    public int getPlayerX() { return playerX; }
    public int getPlayerY() { return playerY; }
    public int getTownDoorX() { return townDoorX; }
    public int getTownDoorY() { return townDoorY; }
    public boolean isInDungeon() { return inDungeon; }
    public int getCurrentDepth() { return currentDepth; }
    public String getAuthoredDungeonName() { return authoredDungeonName; }
    public void setAuthoredDungeonName(String name) { this.authoredDungeonName = name; }
    public int getDungeonEntryX() { return dungeonEntryX; }
    public int getDungeonEntryY() { return dungeonEntryY; }
    public void setDungeonEntry(int x, int y) { this.dungeonEntryX = x; this.dungeonEntryY = y; }

    /** Town the player was inside when they entered the dungeon, or {@code null}. */
    public String getDungeonEntryTownName() { return dungeonEntryTownName; }
    public int getDungeonEntryTownDoorX()   { return dungeonEntryTownDoorX; }
    public int getDungeonEntryTownDoorY()   { return dungeonEntryTownDoorY; }
    public void setDungeonEntryTown(String name, int doorX, int doorY) {
        this.dungeonEntryTownName = name;
        this.dungeonEntryTownDoorX = doorX;
        this.dungeonEntryTownDoorY = doorY;
    }

    /** Teleport / Word of Recall destination; {@code null} on saves written before it was stored. */
    public String getLastSafeTownName() { return lastSafeTownName; }
    public void setLastSafeTownName(String n) { this.lastSafeTownName = n; }

    /** Permanently learned spell names; {@code null} on saves that only have the positional array. */
    public List<String> getLearnedSpells() { return learnedSpells; }
    public void setLearnedSpells(List<String> names) { this.learnedSpells = names; }

    public String getCurrentOverworldName() { return currentOverworldName; }
    public int getSlot() { return slot; }
    public String getDisplayName() { return displayName; }

    /**
     * Returns the name of the current overworld map.
     *
     * @return the overworld map name
     * @deprecated Use {@link #getCurrentOverworldName()} instead.
     */
    @Deprecated
    public String getCurrentMapName() {
        return currentOverworldName;
    }
}

package io.cannonforge.retroquest.registry;
import java.awt.Color;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import io.cannonforge.retroquest.model.TileDefinition;
import io.cannonforge.retroquest.model.TileEffect;

/**
 * Singleton registry for all {@link TileDefinition} entries, backed by {@code data/tiles.json}.
 *
 * <h2>Tile IDs</h2>
 * <p>Each tile has a single-{@code char} ID that must be unique across all categories.
 * Dungeon tiles use lowercase variants to avoid collisions:
 * {@code 't'} Teleporter, {@code 's'} Stairway, {@code 'f'} Fountain, {@code 'g'} Glowing Cube.
 * Letter tiles (A–Z) use Unicode PUA chars {@code \uE041–\uE05A}
 * (formula: {@code (char)('\uE000' + letter)}).
 *
 * <h2>Sprite keys</h2>
 * <p>Every {@link TileDefinition} has a {@code spriteKey} that must exist in
 * {@link ImageAssetRegistry}. The key follows the {@code category/name} convention:
 * <pre>
 *   "overworld/grass"            "town/stone_wall"        "dungeon/altar"
 *   "town/locked_wooden_door"    "dungeon/locked_iron_gate"
 *   "letters/A"                  (letter tiles, A–Z)
 * </pre>
 * {@link #validateSprites()} is called from {@code GamePanel.loadAssets()} at startup and logs
 * a console warning for any tile whose sprite key is absent from the registry.
 *
 * <h2>Forward-compatibility migrations</h2>
 * <p>{@link #migrateNewTiles()} runs on class load and adds any tiles introduced after a
 * player's {@code tiles.json} was last written — so new tile releases are picked up
 * automatically without requiring players to delete their save data.
 * Current migration batches:
 * <ul>
 *   <li><b>v2 town tiles</b> — Wood Floor {@code m}, Forge {@code N}, Pillar {@code M},
 *       Well {@code Q}, Town Tree {@code X}, Plant {@code Y}, Brick Wall {@code Z},
 *       Town Grass {@code '}, Fountain {@code @}</li>
 *   <li><b>v3 letter tiles</b> — A–Z via Unicode PUA ({@code \uE041–\uE05A}),
 *       sprite keys {@code letters/A} … {@code letters/Z}</li>
 *   <li><b>v3 locked tiles</b> — Locked Wooden Door {@code |} (town),
 *       Locked Iron Gate {@code [} (dungeon), Magic Barrier {@code \{} (dungeon);
 *       each carries a {@code locked_door} step effect with {@code requiredKeyId} and
 *       {@code unlockedTileId} parameters</li>
 * </ul>
 *
 * <h2>Public API</h2>
 * <pre>
 *   TileRegistry.getById('g')                    // find by char ID
 *   TileRegistry.getByIdSafe('g')                // same, returns default tile if missing
 *   TileRegistry.getTilesByCategory("Dungeon")   // filter by category
 *   TileRegistry.getAllTiles()                    // unmodifiable list of all tiles
 *   TileRegistry.addTile(def)                    // add + auto-save
 *   TileRegistry.removeTile('g')                 // remove + auto-save
 *   TileRegistry.validateSprites()               // warn on missing sprite keys
 * </pre>
 *
 * <p>See {@code docs/TileRequirements.md} for the full tile reference table.
 */
public class TileRegistry {

    private static final String TILES_FILE = System.getProperty("user.dir") + "/data/tiles.json";

    // ---- char -> TileDefinition index ------------------------------------
    // getById() is called per Bresenham ray step by VisionSystem and once per
    // visible tile per frame by GamePanel; a linear scan of ~218 definitions
    // there costs six figures of char comparisons per frame. The index below
    // makes it O(1) with no allocation on the lookup path.
    //
    // Tile IDs are ASCII punctuation/letters plus Unicode PUA chars in the
    // 0xE041-0xE0CF band, so two flat arrays cover every id actually in use;
    // the map is a correctness fallback for ids outside both windows (the
    // editor may create any char).
    private static final int LOW_SIZE  = 0x100;   // ids 0x0000-0x00FF
    private static final int PUA_BASE  = 0xE000;
    private static final int PUA_SIZE  = 0x100;   // ids 0xE000-0xE0FF
    private static final TileDefinition[] lowIndex = new TileDefinition[LOW_SIZE];
    private static final TileDefinition[] puaIndex = new TileDefinition[PUA_SIZE];
    private static final Map<Character, TileDefinition> otherIndex = new java.util.HashMap<>();
    private static boolean indexValid = false;

    /** Marks the id index stale. Called from every mutation of {@link #tiles}. */
    private static void invalidateIndex() { indexValid = false; }

    /**
     * (Re)builds the char → definition index. FIRST definition wins for duplicate
     * ids, matching the original linear scan exactly.
     */
    private static void rebuildIndex() {
        Arrays.fill(lowIndex, null);
        Arrays.fill(puaIndex, null);
        otherIndex.clear();
        for (int i = 0, n = tiles.size(); i < n; i++) {
            TileDefinition t = tiles.get(i);
            char id = t.getId();
            if (id < LOW_SIZE) {
                if (lowIndex[id] == null) lowIndex[id] = t;
            } else if (id >= PUA_BASE && id < PUA_BASE + PUA_SIZE) {
                int k = id - PUA_BASE;
                if (puaIndex[k] == null) puaIndex[k] = t;
            } else {
                otherIndex.putIfAbsent(id, t);
            }
        }
        indexValid = true;
    }

    /**
     * Backing list. Every structural/positional mutation invalidates the id index,
     * so callers cannot forget to do it.
     */
    private static final List<TileDefinition> tiles = new ArrayList<>() {
        private static final long serialVersionUID = 1L;
        @Override public boolean add(TileDefinition e) { invalidateIndex(); return super.add(e); }
        @Override public void add(int i, TileDefinition e) { invalidateIndex(); super.add(i, e); }
        @Override public TileDefinition set(int i, TileDefinition e) { invalidateIndex(); return super.set(i, e); }
        @Override public TileDefinition remove(int i) { invalidateIndex(); return super.remove(i); }
        @Override public boolean remove(Object o) { invalidateIndex(); return super.remove(o); }
        @Override public boolean addAll(java.util.Collection<? extends TileDefinition> c) { invalidateIndex(); return super.addAll(c); }
        @Override public boolean addAll(int i, java.util.Collection<? extends TileDefinition> c) { invalidateIndex(); return super.addAll(i, c); }
        @Override public boolean removeAll(java.util.Collection<?> c) { invalidateIndex(); return super.removeAll(c); }
        @Override public boolean retainAll(java.util.Collection<?> c) { invalidateIndex(); return super.retainAll(c); }
        @Override public boolean removeIf(java.util.function.Predicate<? super TileDefinition> f) { invalidateIndex(); return super.removeIf(f); }
        @Override public void replaceAll(java.util.function.UnaryOperator<TileDefinition> op) { invalidateIndex(); super.replaceAll(op); }
        @Override public void sort(java.util.Comparator<? super TileDefinition> c) { invalidateIndex(); super.sort(c); }
        @Override public void clear() { invalidateIndex(); super.clear(); }
    };

    /**
     * Set when tiles.json exists but could not be read. Defaults are still populated so the
     * game runs, but saving is refused — otherwise the ~40 defaults would overwrite the
     * full (merely unreadable) tile set.
     */
    private static boolean loadFailed = false;

    static {
        loadTiles();
        if (tiles.isEmpty()) {
            createDefaultTiles();
            if (!loadFailed) saveTiles();
        }
        migrateOverworldPortal();
        migrateNewTiles();
    }

    /**
     * Ensures the 'O' (Overworld Portal) tile is in the "System" category with no TileEffect,
     * so it is hidden from the tile palette and doesn't double-fire the teleport logic.
     * Migrates any existing tiles.json that still has it under "Overworld".
     */
    private static void migrateOverworldPortal() {
        for (int i = 0; i < tiles.size(); i++) {
            TileDefinition t = tiles.get(i);
            if (t.getId() == 'O' && !"System".equals(t.getCategory())) {
                tiles.set(i, new TileDefinition('O', t.getName(), "System",
                        t.getEditorColor(), t.isWalkable(), t.getSpriteKey()));
                saveTiles();
                break;
            }
        }
    }

    /**
     * Adds any new tiles introduced after the initial tiles.json was written.
     * Checks by char ID and inserts missing entries, then re-saves if anything changed.
     */
    private static void migrateNewTiles() {
        boolean changed = false;
        changed |= renameTiles();
        changed |= migrateOverworldTerrainTiles();
        changed |= migrateTownTiles();
        changed |= migrateLetterTiles();
        changed |= migrateLockedTiles();
        changed |= migrateOverworldConnectorTiles();
        changed |= migrateIsland2Tiles();
        changed |= migrateIsland3Tiles();
        changed |= migrateIsland4Tiles();
        changed |= migrateIsland5Tiles();
        changed |= migrateIsland6Tiles();
        changed |= migrateIsland7Tiles();
        changed |= migratePuzzleTile();
        changed |= migrateOverworldFeatureTiles();
        if (changed) saveTiles();
    }

    /** Renames tiles that were registered with old names. */
    private static boolean renameTiles() {
        boolean changed = false;
        changed |= renameTile('w', "River NS", "overworld/river_ns");
        changed |= renameTile('r', "Road NS", "overworld/dirt_road");
        return changed;
    }

    private static boolean renameTile(char id, String newName, String newSpriteKey) {
        TileDefinition t = getById(id);
        if (t == null) return false;
        if (t.getName().equals(newName) && t.getSpriteKey().equals(newSpriteKey)) return false;
        for (int i = 0; i < tiles.size(); i++) {
            if (tiles.get(i).getId() == id) {
                TileDefinition old = tiles.get(i);
                tiles.set(i, new TileDefinition(id, newName, old.getCategory(), old.getEditorColor(),
                        old.isWalkable(), newSpriteKey, old.isLiquid(), old.blocksVision(),
                        old.isSafeZone(), old.getLightRadius(), old.getStepSound(),
                        old.getEncounterRateMod(), old.getDescription(), old.getOnStepEffect()));
                return true;
            }
        }
        return false;
    }

    /** Adds v4 overworld terrain tiles if missing: river, road, sand, hills, forest, etc. */
    private static boolean migrateOverworldTerrainTiles() {
        boolean added = false;
        // River: w=NS, 9=EW, a=NE, b=NW, e=SE, h=SW
        added |= addIfMissing('w', "River NS", "Overworld", new Color(40, 100, 200), false, "overworld/river_ns",
                true, false, false, 0, "step_default", 0.0, "A flowing river (north-south).", new TileEffect("none"));
        added |= addIfMissing('9', "River EW", "Overworld", new Color(40, 100, 200), false, "overworld/river_ew",
                true, false, false, 0, "step_default", 0.0, "A flowing river (east-west).", new TileEffect("none"));
        added |= addIfMissing('a', "River NE", "Overworld", new Color(40, 100, 200), false, "overworld/river_ne",
                true, false, false, 0, "step_default", 0.0, "River bend (north to east).", new TileEffect("none"));
        added |= addIfMissing('b', "River NW", "Overworld", new Color(40, 100, 200), false, "overworld/river_nw",
                true, false, false, 0, "step_default", 0.0, "River bend (north to west).", new TileEffect("none"));
        added |= addIfMissing('e', "River SE", "Overworld", new Color(40, 100, 200), false, "overworld/river_se",
                true, false, false, 0, "step_default", 0.0, "River bend (south to east).", new TileEffect("none"));
        added |= addIfMissing('h', "River SW", "Overworld", new Color(40, 100, 200), false, "overworld/river_sw",
                true, false, false, 0, "step_default", 0.0, "River bend (south to west).", new TileEffect("none"));
        // Road: r=NS, 4=EW, 5=NE, 6=NW, 7=SE, 8=SW
        added |= addIfMissing('r', "Road NS", "Overworld", new Color(160, 120, 70), true, "overworld/dirt_road",
                false, false, false, 0, "step_default", 0.8, "A dirt road (north-south).", new TileEffect("none"));
        added |= addIfMissing('4', "Road EW", "Overworld", new Color(160, 120, 70), true, "overworld/dirt_road_ew",
                false, false, false, 0, "step_default", 0.8, "A dirt road (east-west).", new TileEffect("none"));
        added |= addIfMissing('5', "Road NE", "Overworld", new Color(160, 120, 70), true, "overworld/dirt_road_ne",
                false, false, false, 0, "step_default", 0.8, "Road bend (north to east).", new TileEffect("none"));
        added |= addIfMissing('6', "Road NW", "Overworld", new Color(160, 120, 70), true, "overworld/dirt_road_nw",
                false, false, false, 0, "step_default", 0.8, "Road bend (north to west).", new TileEffect("none"));
        added |= addIfMissing('7', "Road SE", "Overworld", new Color(160, 120, 70), true, "overworld/dirt_road_se",
                false, false, false, 0, "step_default", 0.8, "Road bend (south to east).", new TileEffect("none"));
        added |= addIfMissing('8', "Road SW", "Overworld", new Color(160, 120, 70), true, "overworld/dirt_road_sw",
                false, false, false, 0, "step_default", 0.8, "Road bend (south to west).", new TileEffect("none"));
        // Other terrain
        added |= addIfMissing('1', "Sand", "Overworld", new Color(210, 190, 130), true, "overworld/sand",
                false, false, false, 0, "step_default", 0.6, "Sandy beach terrain.", new TileEffect("none"));
        added |= addIfMissing('2', "Hills", "Overworld", new Color(80, 140, 50), true, "overworld/hills",
                false, false, false, 0, "step_default", 1.2, "Rolling green hills.", new TileEffect("none"));
        added |= addIfMissing('3', "Dense Forest", "Overworld", new Color(20, 80, 15), true, "overworld/tall_grass",
                false, true, false, 0, "step_default", 1.5, "Thick forest undergrowth.", new TileEffect("none"));
        added |= addIfMissing('p', "Poison Marsh", "Overworld", new Color(100, 60, 120), true, "overworld/poisongrass",
                false, false, false, 0, "step_default", 1.3, "Noxious swamp grass.", new TileEffect("damage", Map.of("amount", "2")));
        added |= addIfMissing('^', "Bridge", "Overworld", new Color(140, 100, 40), true, "overworld/bridge",
                false, false, false, 0, "step_wood", 0.0, "A wooden bridge.", new TileEffect("none"));
        added |= addIfMissing('J', "Ruins Interior", "Overworld", new Color(130, 130, 120), true, "overworld/cobblestone",
                false, false, true, 0, "step_default", 0.0, "Interior of an ancient ruin.", new TileEffect("none"));
        return added;
    }

    /** Adds overworld connector tiles (bridges, intersections, river endpoints). PUA \uE0A0-\uE0A5. */
    private static boolean migrateOverworldConnectorTiles() {
        boolean added = false;
        added |= addIfMissing('\uE0A0', "Bridge EW", "Overworld", new Color(140, 100, 40), true, "overworld/bridge_ew",
                false, false, false, 0, "step_wood", 0.0, "A wooden bridge carrying an east-west road over a river.", new TileEffect("none"));
        added |= addIfMissing('\uE0A1', "Road Crossroad", "Overworld", new Color(160, 120, 70), true, "overworld/dirt_road_cross",
                false, false, false, 0, "step_default", 0.8, "A four-way dirt road intersection.", new TileEffect("none"));
        added |= addIfMissing('\uE0A2', "Road T-South", "Overworld", new Color(160, 120, 70), true, "overworld/dirt_road_t_south",
                false, false, false, 0, "step_default", 0.8, "East-west road with a southward branch.", new TileEffect("none"));
        added |= addIfMissing('\uE0A3', "Road T-East", "Overworld", new Color(160, 120, 70), true, "overworld/dirt_road_t_east",
                false, false, false, 0, "step_default", 0.8, "North-south road with an eastward branch.", new TileEffect("none"));
        added |= addIfMissing('\uE0A4', "River Source", "Overworld", new Color(40, 100, 200), false, "overworld/river_source",
                true, false, false, 0, "step_default", 0.0, "A rocky spring where water emerges.", new TileEffect("none"));
        added |= addIfMissing('\uE0A5', "River Mouth", "Overworld", new Color(40, 100, 200), false, "overworld/river_mouth",
                true, false, false, 0, "step_default", 0.0, "A river delta flowing into the sea.", new TileEffect("none"));
        return added;
    }

    /** Adds v2 classic-style town tiles if missing. Returns true if any were added. */
    private static boolean migrateTownTiles() {
        boolean added = false;
        added |= addIfMissing('m', "Wood Floor", "Town", new Color(160, 100, 50), true, "town/wood_floor",
                false, false, true, 0, "step_wood", 1.0, "Wooden plank floor.", new TileEffect("none"));
        added |= addIfMissing('N', "Forge", "Town", new Color(200, 80, 20), false, "town/forge",
                false, false, false, 2, "step_default", 0.0, "A blacksmith's forge.", new TileEffect("none"));
        added |= addIfMissing('M', "Pillar", "Town", new Color(190, 185, 175), false, "town/pillar",
                false, true, false, 0, "step_default", 0.0, "A stone column.", new TileEffect("none"));
        added |= addIfMissing('Q', "Well", "Town", new Color(100, 90, 70), false, "town/well",
                false, false, false, 0, "step_default", 0.0, "A stone well.", new TileEffect("none"));
        added |= addIfMissing('X', "Town Tree", "Town", new Color(0, 120, 0), false, "town/town_tree",
                false, false, false, 0, "step_default", 0.0, "A small tree.", new TileEffect("none"));
        added |= addIfMissing('Y', "Plant", "Town", new Color(50, 160, 50), false, "town/plant",
                false, false, false, 0, "step_default", 0.0, "A decorative potted plant.", new TileEffect("none"));
        added |= addIfMissing('Z', "Brick Wall", "Town", new Color(160, 55, 30), false, "town/brick_wall",
                false, true, false, 0, "step_default", 0.0, "Red brick wall.", new TileEffect("none"));
        added |= addIfMissing('\'', "Town Grass", "Town", new Color(58, 90, 38), true, "town/grass",
                false, false, true, 0, "step_default", 1.0, "Well-tended garden grass.", new TileEffect("none"));
        added |= addIfMissing('@', "Fountain", "Town", new Color(30, 80, 140), false, "town/fountain",
                false, false, false, 1, "step_default", 0.0, "A stone fountain with flowing water.", new TileEffect("none"));
        added |= addIfMissing('K', "Brick Floor", "Town", new Color(128, 82, 62), true, "town/brick_floor",
                false, false, true, 0, "step_default", 1.0, "A floor of laid brick.", new TileEffect("none"));
        // Furniture tiles (sprites existed but had no tile definitions)
        added |= addIfMissing('k', "Bookshelf", "Town", new Color(90, 60, 30), false, "town/bookshelf",
                false, true, true, 0, "step_default", 0.0, "A tall bookshelf packed with tomes.", new TileEffect("none"));
        added |= addIfMissing('j', "Chair", "Town", new Color(120, 80, 40), true, "town/chair",
                false, false, true, 0, "step_wood", 0.0, "A wooden chair.", new TileEffect("none"));
        added |= addIfMissing('i', "Table", "Town", new Color(130, 90, 50), false, "town/table",
                false, false, true, 0, "step_default", 0.0, "A sturdy wooden table.", new TileEffect("none"));
        added |= addIfMissing('l', "Bed", "Town", new Color(140, 50, 50), false, "town/bed",
                false, false, true, 0, "step_default", 0.0, "A bed with a red blanket.", new TileEffect("none"));
        added |= addIfMissing('n', "Barrel", "Town", new Color(100, 70, 35), false, "town/barrel",
                false, false, true, 0, "step_default", 0.0, "A wooden storage barrel.", new TileEffect("none"));
        added |= addIfMissing('o', "Carpet", "Town", new Color(160, 40, 40), true, "town/carpet",
                false, false, true, 0, "step_default", 0.0, "A decorative woven carpet.", new TileEffect("none"));
        added |= addIfMissing('u', "Fireplace", "Town", new Color(200, 100, 30), false, "town/fireplace",
                false, true, true, 3, "step_default", 0.0, "A stone fireplace with a warm fire.", new TileEffect("none"));
        // Themed tiles for Mooncrest
        added |= addIfMissing('v', "Wall Torch", "Town", new Color(200, 160, 60), false, "town/wall_torch",
                false, true, true, 3, "step_default", 0.0, "A torch mounted on the wall.", new TileEffect("none"));
        added |= addIfMissing('x', "Moon Altar", "Town", new Color(140, 160, 200), false, "town/moon_altar",
                false, false, true, 2, "step_default", 0.0, "A crescent altar radiating silver light.", new TileEffect("none"));
        added |= addIfMissing('y', "Stained Glass", "Town", new Color(60, 80, 160), false, "town/stained_glass",
                false, true, true, 0, "step_default", 0.0, "A stained glass window depicting the moon.", new TileEffect("none"));
        added |= addIfMissing('z', "Ornate Rug", "Town", new Color(100, 40, 120), true, "town/ornate_rug",
                false, false, true, 0, "step_default", 0.0, "A purple rug with silver crescent motif.", new TileEffect("none"));
        return added;
    }

    /** Adds A–Z letter tiles in Unicode PUA range if missing. Returns true if any were added. */
    private static boolean migrateLetterTiles() {
        boolean added = false;
        for (char c = 'A'; c <= 'Z'; c++) {
            char tileId = (char) ('\uE000' + c);
            added |= addIfMissing(tileId, "Letter " + c, "Town", new Color(255, 200, 80), true, "letters/" + c,
                    false, false, false, 0, "step_default", 1.0, "Decorative letter tile.", new TileEffect("none"));
        }
        return added;
    }

    /** Adds v3 locked door tiles if missing. Returns true if any were added. */
    private static boolean migrateLockedTiles() {
        boolean added = false;
        added |= addLockedTile('|', "Locked Wooden Door", "Town", new Color(100, 60, 20),
                "town/locked_wooden_door", "A locked wooden door.",
                "rusty_key", "d", "You unlock the door.", "The door is locked. You need a rusty key.", 0);
        added |= addLockedTile('[', "Locked Iron Gate", "Dungeon", new Color(80, 85, 90),
                "dungeon/locked_iron_gate", "A locked iron gate.",
                "iron_key", "F", "The gate swings open.", "The iron gate is locked. You need an iron key.", 0);
        added |= addLockedTile('\uE0B0', "Locked Iron Gate (Overworld)", "Overworld", new Color(80, 85, 90),
                "dungeon/locked_iron_gate", "A locked iron gate blocking the path.",
                "iron_key", ".", "The gate swings open.", "The iron gate is locked. You need an iron key.", 0);
        added |= addLockedTile('{', "Magic Barrier", "Dungeon", new Color(80, 60, 180),
                "dungeon/magic_barrier", "A shimmering magical barrier.",
                "magic_crystal", "F", "The barrier dissolves in a flash of light.",
                "The barrier pulses with arcane energy. A magic crystal might break it.", 3);
        return added;
    }

    /** Adds Island 2 (Pyralis / Forged Isles) volcanic and portal tiles if missing. */
    private static boolean migrateIsland2Tiles() {
        boolean added = false;
        // Overworld volcanic terrain
        added |= addIfMissing('!', "Volcanic Rock", "Overworld", new Color(60, 50, 45), true, "overworld/volcanic_rock",
                false, false, false, 0, "step_default", 1.0, "Dark basalt rock with orange veins.", new TileEffect("none"));
        added |= addIfMissing('$', "Ash Plains", "Overworld", new Color(110, 95, 80), true, "overworld/ash_plains",
                false, false, false, 0, "step_default", 1.8, "Scorched ash-covered terrain.", new TileEffect("none"));
        added |= addIfMissing('%', "Lava Flow", "Overworld", new Color(220, 80, 20), true, "overworld/lava_flow",
                true, false, false, 3, "step_default", 0.0, "Flowing molten lava.", new TileEffect("damage", Map.of("amount", "5", "message", "The lava scorches you!")));
        added |= addIfMissing('&', "Volcanic Mountain", "Overworld", new Color(50, 40, 35), false, "overworld/volcanic_mountain",
                false, true, false, 0, "step_default", 0.0, "A dark volcanic peak.", new TileEffect("none"));
        added |= addIfMissing(')', "Slag Heap", "Overworld", new Color(70, 65, 55), false, "overworld/slag_heap",
                false, false, false, 0, "step_default", 0.0, "A jagged mound of metallic slag.", new TileEffect("none"));
        added |= addIfMissing(':', "Volcanic Sand", "Overworld", new Color(90, 80, 60), true, "overworld/volcanic_sand",
                false, false, false, 0, "step_default", 0.6, "Dark sand with obsidian flecks.", new TileEffect("none"));
        added |= addIfMissing(';', "Ash Tree", "Overworld", new Color(80, 70, 60), false, "overworld/ash_tree",
                false, false, false, 0, "step_default", 0.0, "A charred, leafless tree.", new TileEffect("none"));
        // Town volcanic tiles
        added |= addIfMissing('(', "Obsidian Floor", "Town", new Color(20, 20, 25), true, "town/obsidian_floor",
                false, false, true, 0, "step_default", 1.0, "Black glossy obsidian tiles.", new TileEffect("none"));
        added |= addIfMissing('*', "Forge Floor", "Town", new Color(80, 50, 30), true, "town/forge_floor",
                false, false, true, 1, "step_default", 1.0, "Warm stone floor with embedded embers.", new TileEffect("none"));
        added |= addIfMissing('+', "Ember Grate", "Town", new Color(200, 100, 20), false, "town/ember_grate",
                false, false, false, 3, "step_default", 0.0, "An iron grate over glowing embers.", new TileEffect("none"));
        added |= addIfMissing('-', "Lava Channel", "Town", new Color(200, 60, 10), false, "town/lava_channel",
                true, false, false, 4, "step_default", 0.0, "A narrow channel of flowing lava.", new TileEffect("none"));
        // Portal tiles
        added |= addIfMissing('G', "Portal to Pyralis", "Special", new Color(220, 120, 30), true, "overworld/island_portal",
                false, false, false, 2, "step_default", 0.0, "A shimmering portal to the Forged Isles.",
                new TileEffect("island_portal", Map.of("requiredKeyId", "key_of_tides", "map", "pyralis",
                        "x", "21", "y", "93", "lockedMessage", "The portal shimmers but remains sealed. You need the Key of Tides.")));
        added |= addIfMissing('U', "Return Portal", "Special", new Color(30, 120, 220), true, "overworld/island_portal",
                false, false, false, 2, "step_default", 0.0, "A shimmering portal back to Lirandel.",
                new TileEffect("island_portal", Map.of("map", "lirandel", "x", "55", "y", "85")));
        return added;
    }

    /** Adds Island 3 (Zephyrion / Storm Archipelago) sky and portal tiles if missing. */
    private static boolean migrateIsland3Tiles() {
        boolean added = false;
        // Sky overworld terrain
        added |= addIfMissing('`', "Sky Void", "Overworld", new Color(20, 25, 50), false, "overworld/sky_void",
                false, false, false, 0, "step_default", 0.0, "The endless sky between floating islands.", new TileEffect("none"));
        added |= addIfMissing(']', "Cloud Platform", "Overworld", new Color(200, 210, 230), true, "overworld/cloud_platform",
                false, false, false, 0, "step_default", 0.5, "A solid cloud formation, walkable.", new TileEffect("none"));
        added |= addIfMissing('_', "Sky Grass", "Overworld", new Color(60, 140, 90), true, "overworld/sky_grass",
                false, false, false, 0, "step_default", 1.0, "Blue-green grass on a floating island.", new TileEffect("none"));
        added |= addIfMissing('=', "Sky Rock", "Overworld", new Color(100, 105, 120), true, "overworld/sky_rock",
                false, false, false, 0, "step_default", 1.2, "Grey-blue stone veined with lightning.", new TileEffect("none"));
        added |= addIfMissing('^', "Rope Bridge", "Overworld", new Color(160, 130, 80), true, "overworld/rope_bridge",
                false, false, false, 0, "step_default", 1.5, "A swaying rope bridge over the void.", new TileEffect("none"));
        added |= addIfMissing('<', "Storm Cloud", "Overworld", new Color(50, 45, 65), true, "overworld/storm_cloud",
                false, false, false, 0, "step_default", 2.0, "A crackling storm cloud. Lightning strikes the unwary.",
                new TileEffect("damage", Map.of("amount", "3", "message", "Lightning crackles through the storm cloud!")));
        added |= addIfMissing('>', "Lightning Rod", "Overworld", new Color(180, 220, 255), true, "overworld/lightning_rod",
                false, false, true, 2, "step_default", 0.0, "A tall lightning rod. Safe from storms.", new TileEffect("none"));
        added |= addIfMissing('\\', "Sky Tree", "Overworld", new Color(50, 150, 100), false, "overworld/sky_tree",
                false, false, false, 0, "step_default", 0.0, "A wind-blown tree with streaming leaves.", new TileEffect("none"));
        // Portal tiles (Pyralis ↔ Zephyrion)
        added |= addIfMissing('V', "Portal to Storm Archipelago", "Special", new Color(100, 180, 255), true, "overworld/island_portal",
                false, false, false, 2, "step_default", 0.0, "A crackling portal to the Storm Archipelago.",
                new TileEffect("island_portal", Map.of("requiredKeyId", "key_of_embers", "map", "zephyrion",
                        "x", "110", "y", "78", "lockedMessage", "The portal crackles with static but remains sealed. You need the Key of Embers.")));
        added |= addIfMissing('K', "Return Portal to Pyralis", "Special", new Color(220, 120, 30), true, "overworld/island_portal",
                false, false, false, 2, "step_default", 0.0, "A shimmering portal back to the Forged Isles.",
                new TileEffect("island_portal", Map.of("map", "pyralis", "x", "90", "y", "25")));
        return added;
    }

    private static boolean migrateIsland4Tiles() {
        boolean added = false;
        // Overworld forest terrain
        added |= addIfMissing('\uE060', "Dense Forest", "Overworld", new Color(15, 45, 20), true, "overworld/dense_forest",
                false, true, false, 0, "step_default", 1.5, "Thick canopy of ancient trees.", new TileEffect("none"));
        added |= addIfMissing('\uE061', "Giant Tree", "Overworld", new Color(65, 45, 25), false, "overworld/giant_tree",
                false, true, false, 0, "step_default", 0.0, "A massive ancient tree, towering above the canopy.", new TileEffect("none"));
        added |= addIfMissing('\uE062', "Mangrove Swamp", "Overworld", new Color(30, 50, 35), true, "overworld/mangrove_swamp",
                false, false, false, 0, "step_default", 1.8, "Murky swamp with tangled roots.",
                new TileEffect("damage", Map.of("amount", "2", "message", "The swamp water stings your legs!")));
        added |= addIfMissing('\uE063', "Moss Ground", "Overworld", new Color(40, 85, 35), true, "overworld/moss_ground",
                false, false, false, 0, "step_default", 0.8, "Soft moss-covered ground.", new TileEffect("none"));
        added |= addIfMissing('\uE064', "Bioluminescent Fungi", "Overworld", new Color(30, 180, 140), true, "overworld/bioluminescent_fungi",
                false, false, false, 1, "step_default", 1.2, "Glowing mushrooms light the forest floor.", new TileEffect("none"));
        added |= addIfMissing('\uE065', "Ancient Roots", "Overworld", new Color(70, 48, 25), false, "overworld/ancient_roots",
                false, true, false, 0, "step_default", 0.0, "Massive tangled roots, impassable.", new TileEffect("none"));
        added |= addIfMissing('\uE066', "Thorn Hedge", "Overworld", new Color(20, 50, 15), false, "overworld/thorn_hedge",
                false, true, false, 0, "step_default", 0.0, "A dense wall of thorny vines.", new TileEffect("none"));
        added |= addIfMissing('\uE067', "Petrified Amber", "Overworld", new Color(160, 110, 30), false, "overworld/petrified_amber",
                false, false, false, 1, "step_default", 0.0, "Golden crystallized tree sap from before the Shattering.", new TileEffect("none"));
        // Town tiles
        added |= addIfMissing('\uE06A', "Root Floor", "Town", new Color(60, 42, 22), true, "town/root_floor",
                false, false, true, 0, "step_default", 0.0, "Woven living roots forming a flat surface.", new TileEffect("none"));
        added |= addIfMissing('\uE06B', "Bark Wall", "Town", new Color(55, 38, 20), false, "town/bark_wall",
                false, true, true, 0, "step_default", 0.0, "Thick living bark wall.", new TileEffect("none"));
        added |= addIfMissing('\uE06C', "Vine Curtain", "Town", new Color(30, 65, 25), true, "town/vine_curtain",
                false, false, true, 0, "step_default", 0.0, "Hanging vines forming a natural curtain.", new TileEffect("none"));
        added |= addIfMissing('\uE06D', "Amber Crystal", "Town", new Color(180, 130, 40), false, "town/amber_crystal",
                false, false, true, 2, "step_default", 0.0, "A glowing amber crystal formation.", new TileEffect("none"));
        added |= addIfMissing('\uE06E', "Fungal Lamp", "Town", new Color(40, 190, 150), true, "town/fungal_lamp",
                false, false, true, 3, "step_default", 0.0, "A bioluminescent mushroom used as a light source.", new TileEffect("none"));
        // Portal tiles (Zephyrion ↔ Sylvandar)
        added |= addIfMissing('\uE068', "Portal to Verdant Mangroves", "Special", new Color(40, 160, 80), true, "overworld/island_portal",
                false, false, false, 2, "step_default", 0.0, "A shimmering green portal wreathed in living vines.",
                new TileEffect("island_portal", Map.of("requiredKeyId", "key_of_gales", "map", "sylvandar",
                        "x", "65", "y", "160", "lockedMessage", "The portal pulses with verdant energy but remains sealed. You need the Key of Gales.")));
        added |= addIfMissing('\uE069', "Return Portal to Zephyrion", "Special", new Color(100, 180, 255), true, "overworld/island_portal",
                false, false, false, 2, "step_default", 0.0, "A crackling portal back to the Storm Archipelago.",
                new TileEffect("island_portal", Map.of("map", "zephyrion", "x", "115", "y", "95")));
        return added;
    }

    /** Adds Island 5 (Thalorax / The Abyssal Depths) deep ocean and portal tiles if missing. */
    private static boolean migrateIsland5Tiles() {
        boolean added = false;
        // Overworld deep ocean terrain
        added |= addIfMissing('\uE070', "Deep Ocean Floor", "Overworld", new Color(10, 20, 40), true, "overworld/deep_ocean_floor",
                false, false, false, 0, "step_default", 1.0, "Dark seabed with subtle texture.", new TileEffect("none"));
        added |= addIfMissing('\uE071', "Pressure Vent", "Overworld", new Color(80, 30, 20), true, "overworld/pressure_vent",
                false, false, false, 2, "step_default", 0.0, "A volcanic vent releasing scalding water.",
                new TileEffect("damage", Map.of("amount", "4", "message", "Superheated water scalds you!")));
        added |= addIfMissing('\uE072', "Coral Reef", "Overworld", new Color(180, 60, 120), false, "overworld/coral_reef",
                false, true, false, 0, "step_default", 0.0, "Colorful coral formations, impassable.", new TileEffect("none"));
        added |= addIfMissing('\uE073', "Kelp Forest", "Overworld", new Color(20, 80, 50), true, "overworld/kelp_forest",
                false, true, false, 0, "step_default", 1.5, "Tall swaying kelp obscuring vision.", new TileEffect("none"));
        added |= addIfMissing('\uE074', "Bioluminescent Sand", "Overworld", new Color(40, 180, 160), true, "overworld/bioluminescent_sand",
                false, false, false, 1, "step_default", 0.6, "Glowing sand patches light the deep.", new TileEffect("none"));
        added |= addIfMissing('\uE075', "Abyssal Rock", "Overworld", new Color(25, 20, 35), false, "overworld/abyssal_rock",
                false, true, false, 0, "step_default", 0.0, "Dark jagged rock formation.", new TileEffect("none"));
        added |= addIfMissing('\uE076', "Bone Field", "Overworld", new Color(160, 150, 130), true, "overworld/bone_field",
                false, false, false, 0, "step_default", 1.8, "Scattered leviathan bones.", new TileEffect("none"));
        added |= addIfMissing('\uE077', "Pressure Ward", "Overworld", new Color(60, 200, 220), false, "overworld/pressure_ward",
                false, true, false, 2, "step_default", 0.0, "A glowing ward rune on stone.", new TileEffect("none"));
        // Town tiles
        added |= addIfMissing('\uE07A', "Dome Floor", "Town", new Color(50, 55, 70), true, "town/dome_floor",
                false, false, true, 0, "step_default", 1.0, "Polished stone under a protective dome.", new TileEffect("none"));
        added |= addIfMissing('\uE07B', "Coral Wall", "Town", new Color(150, 50, 80), false, "town/coral_wall",
                false, true, true, 0, "step_default", 0.0, "A wall of living coral.", new TileEffect("none"));
        added |= addIfMissing('\uE07C', "Kelp Platform", "Town", new Color(25, 70, 45), true, "town/kelp_platform",
                false, false, true, 0, "step_default", 1.0, "A woven kelp platform.", new TileEffect("none"));
        added |= addIfMissing('\uE07D', "Pressure Glass", "Town", new Color(100, 180, 210), false, "town/pressure_glass",
                false, false, true, 0, "step_default", 0.0, "A transparent dome panel showing the ocean beyond.", new TileEffect("none"));
        added |= addIfMissing('\uE07E', "Bioluminescent Lamp", "Town", new Color(50, 220, 180), true, "town/bioluminescent_lamp",
                false, false, true, 3, "step_default", 0.0, "A glowing deep-sea lamp fixture.", new TileEffect("none"));
        // Portal tiles (Sylvandar ↔ Thalorax)
        added |= addIfMissing('\uE078', "Portal to Abyssal Depths", "Special", new Color(20, 80, 120), true, "overworld/island_portal",
                false, false, false, 2, "step_default", 0.0, "A portal wreathed in crushing pressure.",
                new TileEffect("island_portal", Map.of("requiredKeyId", "key_of_gales", "map", "thalorax",
                        "x", "60", "y", "98", "lockedMessage", "The portal pulses with crushing pressure but remains sealed. You need the Key of Gales.")));
        added |= addIfMissing('\uE079', "Return Portal to Sylvandar", "Special", new Color(40, 160, 80), true, "overworld/island_portal",
                false, false, false, 2, "step_default", 0.0, "A shimmering green portal back to the Verdant Mangroves.",
                new TileEffect("island_portal", Map.of("map", "sylvandar", "x", "162", "y", "159")));
        return added;
    }

    private static boolean migrateIsland6Tiles() {
        boolean added = false;
        // Overworld shadow trench terrain
        added |= addIfMissing('\uE080', "Shadow Trench Floor", "Overworld", new Color(18, 10, 28), true, "overworld/shadow_trench_floor",
                false, false, false, 0, "step_default", 1.0, "Dark purple-grey stone dusted with silver.", new TileEffect("none"));
        added |= addIfMissing('\uE081', "Memory Rift", "Overworld", new Color(80, 180, 200), true, "overworld/memory_rift",
                false, false, false, 1, "step_default", 1.0, "Cracked ground bleeding ghostly light.",
                new TileEffect("damage", Map.of("amount", "3", "message", "Memories claw at your mind!")));
        added |= addIfMissing('\uE082', "Ruined Spire", "Overworld", new Color(60, 55, 70), false, "overworld/ruined_spire",
                false, true, false, 0, "step_default", 0.0, "A broken tower from a forgotten age.", new TileEffect("none"));
        added |= addIfMissing('\uE083', "Ghost Path", "Overworld", new Color(140, 148, 165), true, "overworld/ghost_path",
                false, false, false, 1, "step_default", 0.5, "A faintly luminous silver-traced walkway.", new TileEffect("none"));
        added |= addIfMissing('\uE084', "Crumbled Archive", "Overworld", new Color(50, 45, 60), true, "overworld/crumbled_archive",
                false, false, false, 0, "step_default", 1.8, "Collapsed ruins of an ancient library.", new TileEffect("none"));
        added |= addIfMissing('\uE085', "Silver Ruins", "Overworld", new Color(120, 128, 145), true, "overworld/silver_ruins",
                false, true, false, 0, "step_default", 1.3, "Larger ruin fragments with silver patina.", new TileEffect("none"));
        added |= addIfMissing('\uE086', "Void Trench", "Overworld", new Color(4, 2, 8), false, "overworld/void_trench",
                false, true, false, 0, "step_default", 0.0, "Absolute darkness — an impassable chasm.", new TileEffect("none"));
        added |= addIfMissing('\uE087', "Memory Ward", "Overworld", new Color(160, 170, 200), false, "overworld/memory_ward",
                false, true, false, 2, "step_default", 0.0, "A glowing silver barrier warding the depths.", new TileEffect("none"));
        // Town tiles
        added |= addIfMissing('\uE08A', "Dust Floor", "Town", new Color(32, 28, 38), true, "town/dust_floor",
                false, false, true, 0, "step_default", 0.0, "Aged flagstone covered in silver-grey dust.", new TileEffect("none"));
        added |= addIfMissing('\uE08B', "Memory Crystal Wall", "Town", new Color(100, 108, 150), false, "town/memory_crystal_wall",
                false, true, true, 1, "step_default", 0.0, "Translucent crystalline wall with inner glow.", new TileEffect("none"));
        added |= addIfMissing('\uE08C', "Ghostlight Lamp", "Town", new Color(200, 210, 240), true, "town/ghostlight_lamp",
                false, false, true, 3, "step_default", 0.0, "A silver orb casting gentle light.", new TileEffect("none"));
        added |= addIfMissing('\uE08D', "Faded Pillar", "Town", new Color(65, 60, 75), false, "town/faded_pillar",
                false, true, true, 0, "step_default", 0.0, "A crumbling column with silver veins.", new TileEffect("none"));
        added |= addIfMissing('\uE08E', "Echo Tile", "Town", new Color(160, 168, 190), true, "town/echo_tile",
                false, false, true, 0, "step_default", 0.0, "A floor tile that shimmers with faint silver ripples.", new TileEffect("none"));
        // Portal tiles (Thalorax ↔ Umbryn)
        added |= addIfMissing('\uE088', "Portal to Shadow Trenches", "Special", new Color(140, 130, 180), true, "overworld/island_portal",
                false, false, false, 2, "step_default", 0.0, "A portal wreathed in silver mist.",
                new TileEffect("island_portal", Map.of("requiredKeyId", "key_of_depths", "map", "umbryn",
                        "x", "115", "y", "180", "lockedMessage", "The portal flickers with silver light but remains sealed. You need the Key of Depths.")));
        added |= addIfMissing('\uE089', "Return Portal to Thalorax", "Special", new Color(40, 160, 200), true, "overworld/island_portal",
                false, false, false, 2, "step_default", 0.0, "A shimmering portal back to the Abyssal Depths.",
                new TileEffect("island_portal", Map.of("map", "thalorax", "x", "170", "y", "46")));
        return added;
    }

    private static boolean migrateIsland7Tiles() {
        boolean added = false;
        // Overworld war terrain
        added |= addIfMissing('\uE090', "Scorched Earth", "Overworld", new Color(60, 40, 20), true, "overworld/scorched_earth",
                false, false, false, 0, "step_default", 1.5, "War-blasted ground, charred and cracked.", new TileEffect("none"));
        added |= addIfMissing('\uE091', "Trench", "Overworld", new Color(45, 35, 25), true, "overworld/trench",
                false, false, false, 0, "step_default", 2.0, "A dug fortification scarred by decades of fighting.", new TileEffect("none"));
        added |= addIfMissing('\uE092', "Iron Barricade", "Overworld", new Color(100, 90, 80), false, "overworld/iron_barricade",
                false, true, false, 0, "step_default", 0.0, "Rusted iron fortification, impassable.", new TileEffect("none"));
        added |= addIfMissing('\uE093', "Crater", "Overworld", new Color(50, 35, 20), true, "overworld/crater",
                false, false, false, 0, "step_default", 1.8, "An explosion crater, still warm.", new TileEffect("none"));
        added |= addIfMissing('\uE094', "War Camp Floor", "Overworld", new Color(90, 75, 50), true, "overworld/war_camp_floor",
                false, false, false, 1, "step_default", 0.5, "Packed earth and wooden planks of a military camp.", new TileEffect("none"));
        added |= addIfMissing('\uE095', "Siege Wall", "Overworld", new Color(80, 75, 70), false, "overworld/siege_wall",
                false, true, false, 0, "step_default", 0.0, "A massive stone wall built for war.", new TileEffect("none"));
        added |= addIfMissing('\uE096', "Arena Stone", "Overworld", new Color(140, 120, 70), true, "overworld/arena_stone",
                false, false, false, 1, "step_default", 0.3, "Polished stone of the Great Arena.", new TileEffect("none"));
        added |= addIfMissing('\uE097', "Burning Ruin", "Overworld", new Color(130, 50, 20), false, "overworld/burning_ruin",
                false, true, false, 2, "step_default", 0.0, "A smoldering building, still on fire.", new TileEffect("none"));
        // Portal tiles (Umbryn ↔ Bellorak)
        added |= addIfMissing('\uE098', "Portal to Golden War Isles", "Special", new Color(200, 160, 60), true, "overworld/island_portal",
                false, false, false, 2, "step_default", 0.0, "A portal wreathed in iron sparks and war-gold light.",
                new TileEffect("island_portal", Map.of("requiredKeyId", "key_of_echoes", "map", "bellorak",
                        "x", "115", "y", "180", "lockedMessage", "The portal crackles with iron sparks but remains sealed. You need the Key of Echoes.")));
        added |= addIfMissing('\uE099', "Return Portal to Umbryn", "Special", new Color(140, 130, 180), true, "overworld/island_portal",
                false, false, false, 2, "step_default", 0.0, "A shimmering silver portal back to the Shadow Trenches.",
                new TileEffect("island_portal", Map.of("map", "umbryn", "x", "60", "y", "30")));
        // Town tiles
        added |= addIfMissing('\uE09A', "War Plank Floor", "Town", new Color(80, 60, 35), true, "town/war_plank_floor",
                false, false, true, 0, "step_default", 0.0, "Rough wooden planks stained with mud and blood.", new TileEffect("none"));
        added |= addIfMissing('\uE09B', "Iron Wall", "Town", new Color(70, 65, 60), false, "town/iron_wall",
                false, true, true, 0, "step_default", 0.0, "A wall of riveted iron plates.", new TileEffect("none"));
        added |= addIfMissing('\uE09C', "War Banner", "Town", new Color(180, 40, 40), true, "town/war_banner",
                false, false, true, 0, "step_default", 0.0, "A tattered war banner hanging from a pole.", new TileEffect("none"));
        added |= addIfMissing('\uE09D', "Weapon Rack", "Town", new Color(110, 90, 60), false, "town/weapon_rack",
                false, true, true, 0, "step_default", 0.0, "A rack of swords, spears, and axes.", new TileEffect("none"));
        added |= addIfMissing('\uE09E', "Iron Lamp", "Town", new Color(200, 160, 60), true, "town/iron_lamp",
                false, false, true, 3, "step_default", 0.0, "An iron lamp with a war-gold flame.", new TileEffect("none"));
        // Cradle of Shards portal
        added |= addIfMissing('\uE09F', "Portal to Cradle of Shards", "Special", new Color(255, 240, 200), true, "overworld/island_portal",
                false, false, false, 3, "step_default", 0.0, "A blinding nexus of all seven divine colors. The final passage.",
                new TileEffect("island_portal", Map.of("requiredKeyId", "key_of_iron", "map", "bellorak",
                        "x", "115", "y", "95", "lockedMessage", "The seven colors swirl but the passage remains sealed. You need the Key of Iron.")));
        return added;
    }

    /** Reassigns Tiled Roof from 'R' to '^' and adds the Puzzle dungeon tile as 'R'. */
    /**
     * Moves the Puzzle feature onto 'R'.
     *
     * <p>This migration used to park the displaced "Tiled Roof" on '^' — which is the
     * Bridge, and the Bridge is the only thing joining Zephyrion's floating platforms to
     * each other. The index is first-wins so the Bridge kept winning and nothing broke, but
     * a solid roof one reorder away from swallowing 155 bridge tiles is not a state to
     * leave a map in: with '^' solid, Zephyrion loses both its towns, its dungeon and its
     * exit portal, and the game cannot be finished. No map places a roof, so the roof is
     * simply gone rather than re-homed.
     */
    private static boolean migratePuzzleTile() {
        // Add Puzzle tile as 'R' if missing
        return addIfMissing('R', "Puzzle", "Dungeon", new Color(160, 120, 200), true, "dungeon/puzzle",
                false, false, false, 0, "step_default", 0.0, "A mysterious puzzle mechanism.", new TileEffect("none"));
    }

    /** Adds overworld interactive feature tiles: shrines, dig sites, ambush points, etc. PUA \uE0C0-\uE0CF. */
    private static boolean migrateOverworldFeatureTiles() {
        boolean added = false;
        added |= addIfMissing('\uE0C0', "Hidden Shrine", "Overworld", new Color(180, 160, 120), true, "overworld/hidden_shrine",
                false, false, false, 1, "step_default", 0.0, "A weathered stone shrine.", new TileEffect("shrine"));
        added |= addIfMissing('\uE0C1', "Dig Site", "Overworld", new Color(120, 90, 50), true, "overworld/dig_site",
                false, false, false, 0, "step_default", 0.0, "Disturbed earth hides something beneath.", new TileEffect("treasure"));
        added |= addIfMissing('\uE0C2', "Ambush Point", "Overworld", new Color(140, 40, 40), true, "overworld/ambush_point",
                false, false, false, 0, "step_default", 0.8, "A dangerous-looking area.", new TileEffect("ambush"));
        added |= addIfMissing('\uE0C3', "Lava Geyser", "Overworld", new Color(220, 120, 30), true, "overworld/lava_geyser",
                false, false, false, 2, "step_default", 0.0, "Cracks in the earth spew fire.",
                new TileEffect("geyser", Map.of("amount", "5", "chance", "50", "message", "A geyser erupts beneath you!")));
        added |= addIfMissing('\uE0C4', "Wind Current", "Overworld", new Color(180, 200, 240), true, "overworld/wind_current",
                false, false, false, 0, "step_default", 0.0, "A powerful wind current.",
                new TileEffect("wind_current", Map.of("direction", "north", "distance", "3")));
        added |= addIfMissing('\uE0C5', "Ancient Tree", "Overworld", new Color(60, 100, 40), true, "overworld/ancient_tree",
                false, false, false, 0, "step_default", 0.0, "A gnarled tree that seems to whisper.", new TileEffect("lore"));
        added |= addIfMissing('\uE0C6', "Memory Echo", "Overworld", new Color(140, 130, 180), true, "overworld/memory_echo",
                false, false, false, 1, "step_default", 0.0, "A faint ghostly shimmer.",
                new TileEffect("lore", Map.of("oneTime", "true")));
        added |= addIfMissing('\uE0C7', "Sunken Chest", "Overworld", new Color(60, 120, 160), true, "overworld/sunken_chest",
                false, false, false, 0, "step_default", 0.0, "A chest visible beneath the water.", new TileEffect("treasure"));
        added |= addIfMissing('\uE0C8', "Battle Standard", "Overworld", new Color(160, 50, 50), true, "overworld/battle_standard",
                false, false, false, 0, "step_default", 0.8, "A war-torn banner marks this ground.", new TileEffect("ambush"));
        return added;
    }

    /** Adds a tile if not already present. Returns true if it was added. */
    private static boolean addIfMissing(char id, String name, String category, Color color,
            boolean walkable, String spriteKey, boolean isSafeZone, boolean blocksVision,
            boolean indoors, int lightLevel, String stepSound, double encounterRateMod,
            String description, TileEffect effect) {
        if (getById(id) != null) return false;
        tiles.add(new TileDefinition(id, name, category, color, walkable, spriteKey,
                isSafeZone, blocksVision, indoors, lightLevel, stepSound, encounterRateMod,
                description, effect));
        return true;
    }

    /** Adds a locked-door tile if not already present. Returns true if it was added. */
    private static boolean addLockedTile(char id, String name, String category, Color color,
            String spriteKey, String desc, String keyId, String unlockedId,
            String message, String lockedMessage, int lightLevel) {
        if (getById(id) != null) return false;
        var p = new java.util.HashMap<String, String>();
        p.put("requiredKeyId", keyId);
        p.put("unlockedTileId", unlockedId);
        p.put("message", message);
        p.put("lockedMessage", lockedMessage);
        tiles.add(new TileDefinition(id, name, category, color, false, spriteKey,
                false, true, false, lightLevel, "step_default", 0.0,
                desc, new TileEffect("locked_door", p)));
        return true;
    }

    private static void loadTiles() {
        File file = new File(TILES_FILE);
        if (!file.exists()) return; // genuinely absent — safe to seed and write

        Gson gson = new GsonBuilder()
                .registerTypeAdapter(Color.class, new ColorAdapter())
                .create();

        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            Type listType = new TypeToken<ArrayList<TileDefinition>>(){}.getType();
            List<TileDefinition> loaded = gson.fromJson(reader, listType);
            if (loaded == null) {
                loadFailed = true;
                System.err.println("[TileRegistry] *** tiles.json is present but contains no tile list."
                        + " Running on built-in defaults; saving is DISABLED so the file is not overwritten.");
                return;
            }
            tiles.addAll(loaded);
        } catch (Exception e) {
            loadFailed = true;
            tiles.clear(); // discard a partially-read list
            System.err.println("[TileRegistry] *** FAILED TO PARSE tiles.json: " + e
                    + " — running on built-in defaults; saving is DISABLED so your tile set is not overwritten.");
            e.printStackTrace();
        }
    }

    /** Logs a warning for any tile whose spriteKey is not present in the image registry. */
    public static void validateSprites() {
        for (TileDefinition def : tiles) {
            if (ImageAssetRegistry.get(def.getSpriteKey()) == null) {
                System.err.println("[TileRegistry] WARNING: missing sprite: " + def.getSpriteKey()
                        + "  (tile '" + def.getId() + "' — " + def.getName() + ")");
            }
        }
    }

    /**
     * Persists the registry to {@code data/tiles.json}.
     *
     * <p>Written to a sibling {@code .tmp} file and then moved into place, so a failure
     * mid-write cannot truncate the existing file. Refused outright if the file failed to
     * parse at startup.
     *
     * @return {@code true} if the file was written, {@code false} if the save was refused or failed
     */
    public static boolean saveTiles() {
        if (loadFailed) {
            System.err.println("[TileRegistry] REFUSING to save tiles.json — it failed to load at"
                    + " startup and saving now would replace it with defaults. Repair or remove the file.");
            return false;
        }

        File target = new File(TILES_FILE);
        if (target.getParentFile() != null) target.getParentFile().mkdirs();
        File tmp = new File(TILES_FILE + ".tmp");

        Gson gson = new GsonBuilder()
                .registerTypeAdapter(Color.class, new ColorAdapter())
                .setPrettyPrinting()
                .create();

        try {
            try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8)) {
                gson.toJson(tiles, writer);
            }
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (Exception e) {
            System.err.println("[TileRegistry] FAILED to save tiles.json: " + e);
            e.printStackTrace();
            tmp.delete();
            return false;
        }
    }

    private static void createDefaultTiles() {
        // Overworld
        tiles.add(new TileDefinition('.', "Grass", "Overworld", new Color(0, 180, 0), true, "overworld/grass"));
        tiles.add(new TileDefinition('T', "Tree", "Overworld", new Color(0, 120, 0), false, "overworld/tree"));
        tiles.add(new TileDefinition('#', "Mountain", "Overworld", new Color(90, 90, 90), false, "overworld/mountain"));
        tiles.add(new TileDefinition('~', "Water", "Overworld", new Color(0, 80, 180), false, "overworld/water"));
        tiles.add(new TileDefinition('C', "Cobblestone", "Overworld", new Color(180, 180, 180), true, "overworld/cobblestone"));
        tiles.add(new TileDefinition('E', "Town Entrance", "Special", new Color(255, 215, 0), true, "overworld/town_entrance"));
        tiles.add(new TileDefinition('D', "Dungeon Entrance", "Overworld", new Color(180, 40, 20), true, "overworld/dungeon_entrance"));

        // Overworld Teleporter — category "System" keeps it off the tile palette.
        // Destination is per-cell via OverworldTeleporter records; no shared TileEffect needed.
        tiles.add(new TileDefinition('O', "Overworld Portal", "System",
                new Color(0, 200, 255), true, "overworld/teleporter"));

        // Town tiles
        tiles.add(new TileDefinition('W', "Stone Wall", "Town", new Color(100, 100, 110), false, "town/stone_wall"));
        tiles.add(new TileDefinition('F', "Floor", "Town", new Color(70, 70, 60), true, "town/floor"));
        tiles.add(new TileDefinition('c', "Counter", "Town", new Color(139, 90, 40), false, "town/counter"));
        tiles.add(new TileDefinition('d', "Wooden Door", "Town", new Color(139, 69, 19), true, "town/wooden_door"));
        tiles.add(new TileDefinition('^', "Tiled Roof", "Town", new Color(180, 40, 20), false, "town/tiled_roof"));
        tiles.add(new TileDefinition('S', "Signpost", "Town", new Color(205, 133, 63), true, "town/signpost"));

        // Dungeon tiles
        tiles.add(new TileDefinition('P', "Pit", "Dungeon", new Color(20, 15, 10), true, "dungeon/pit"));
        tiles.add(new TileDefinition('t', "Teleporter", "Dungeon", new Color(0, 200, 255), true, "dungeon/teleporter"));
        tiles.add(new TileDefinition('s', "Stairway", "Dungeon", new Color(100, 100, 90), true, "dungeon/stairway"));
        tiles.add(new TileDefinition('A', "Altar", "Dungeon", new Color(120, 120, 140), true, "dungeon/altar"));
        tiles.add(new TileDefinition('f', "Fountain", "Dungeon", new Color(60, 60, 80), true, "dungeon/fountain"));
        tiles.add(new TileDefinition('g', "Glowing Cube", "Dungeon", new Color(100, 200, 255), true, "dungeon/cube"));
        tiles.add(new TileDefinition('H', "Throne", "Dungeon", new Color(139, 69, 19), true, "dungeon/throne"));
        tiles.add(new TileDefinition('B', "Treasure Chest", "Dungeon", new Color(101, 67, 33), true, "dungeon/box"));
        tiles.add(new TileDefinition('I', "Inn", "Dungeon", new Color(180, 100, 60), true, "dungeon/inn"));
        tiles.add(new TileDefinition('R', "Puzzle", "Dungeon", new Color(160, 120, 200), true, "dungeon/puzzle"));

        // Lava — walkable hazard; 8 dmg/step via onStepEffect
        var lavaParams = new java.util.HashMap<String, String>();
        lavaParams.put("amount", "8");
        lavaParams.put("message", "The lava scorches you!");

        tiles.add(new TileDefinition('L', "Lava", "Dungeon", new Color(255, 80, 0), true,
                "dungeon/lava", true, true, false, 4,
                "step_lava", 1.8, "Molten lava burns anything that touches it.",
                new TileEffect("damage", lavaParams)));
    }

    public static List<TileDefinition> getAllTiles() {
        return Collections.unmodifiableList(tiles);
    }

    public static List<TileDefinition> getTilesByCategory(String... categories) {
        List<String> cats = Arrays.asList(categories);
        return tiles.stream()
                .filter(t -> cats.contains(t.getCategory()))
                .collect(Collectors.toList());
    }

    /**
     * Returns the first definition registered for {@code id}, or {@code null} if none.
     *
     * <p>O(1) via the char index; semantics are identical to the linear scan it
     * replaced (first definition wins for duplicate ids, {@code null} for unknown ids).
     */
    public static TileDefinition getById(char id) {
        if (!indexValid) rebuildIndex();
        if (id < LOW_SIZE) return lowIndex[id];
        if (id >= PUA_BASE && id < PUA_BASE + PUA_SIZE) return puaIndex[id - PUA_BASE];
        return otherIndex.isEmpty() ? null : otherIndex.get(id);
    }

    public static TileDefinition getByIdSafe(char id) {
        TileDefinition def = getById(id);
        return (def != null) ? def : getDefaultTile();
    }

    private static TileDefinition getDefaultTile() {
        // Same result as the old stream scan (first tile with id '.'), but O(1) —
        // this sits behind getByIdSafe() on the per-tile-per-frame paint path.
        TileDefinition d = getById('.');
        if (d != null) return d;
        return tiles.isEmpty() ? null : tiles.get(0);
    }

    public static void addTile(TileDefinition tile) {
        tiles.add(tile);
        saveTiles();
    }

    public static void removeTile(char id) {
        tiles.removeIf(t -> t.getId() == id);
        saveTiles();
    }

    public static void updateTile(TileDefinition updated) {
        for (int i = 0; i < tiles.size(); i++) {
            if (tiles.get(i).getId() == updated.getId()) {
                tiles.set(i, updated);
                saveTiles();
                return;
            }
        }
        tiles.add(updated);
        saveTiles();
    }
}

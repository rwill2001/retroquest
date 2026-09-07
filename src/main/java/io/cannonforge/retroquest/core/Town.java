package io.cannonforge.retroquest.core;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import io.cannonforge.retroquest.model.MapData;
import io.cannonforge.retroquest.model.NPC;

/**
 * Represents a town that the player can enter from the overworld.
 *
 * <p>Stores the town name, starting position within the town map, and lists of
 * NPCs and services available. Town maps are loaded from the towns directory as
 * {@code .rfmap} files.
 */
public class Town {
    private final String name;
    private char[][] interiorMap;
    private final int worldDoorX, worldDoorY;
    private int interiorEntryX = 10;
    private int interiorEntryY = 13;
    private final List<NPC> npcs = new ArrayList<>();
    private MapData mapData;
    /** Set when the town file on disk could not be parsed; blocks saving over it. */
    private boolean loadFailed = false;

    private static final String TOWNS_DIR = "data/towns/";

    public Town(String name, int worldDoorX, int worldDoorY) {
        this.name = name;
        this.worldDoorX = worldDoorX;
        this.worldDoorY = worldDoorY;
        loadOrCreateInterior();
    }

    private void loadOrCreateInterior() {
        new File(TOWNS_DIR).mkdirs();
        File file = new File(TOWNS_DIR + name.toLowerCase() + ".rfmap");

        if (file.exists()) {
            try {
                MapData md = MapData.load(file);
                this.mapData = md;
                this.interiorMap = md.tiles;
                this.interiorEntryX = md.interiorEntryX;
                this.interiorEntryY = md.interiorEntryY;
                this.npcs.clear();
                this.npcs.addAll(md.npcs);
                return;
            } catch (Exception e) {
                // The town file exists but will not parse. Build a placeholder interior in
                // memory and DO NOT save — saving would overwrite the authored town.
                System.err.println("[Town] Could not load town file for '" + name + "' ("
                        + e.getMessage() + "). Using a placeholder; the file is left untouched.");
                this.interiorMap = createClassicStyleInterior();
                MapData placeholder = new MapData();
                placeholder.initialTileStates = new java.util.HashMap<>();
                this.mapData = placeholder;
                this.loadFailed = true;
                return;
            }
        }

        // Only create defaults for brand new towns
        this.interiorMap = createClassicStyleInterior();
        createNPCs();                                 // Only runs for new towns
        MapData newMd = new MapData();
        newMd.initialTileStates = new java.util.HashMap<>();
        this.mapData = newMd;
        save();
    }

    private char[][] createClassicStyleInterior() {
        int w = 40;
        int h = 30;
        char[][] map = new char[h][w];

        // Floor fill
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                map[y][x] = 'F';

        // Border walls
        for (int x = 0; x < w; x++) { map[0][x] = 'W'; map[h-1][x] = 'W'; }
        for (int y = 0; y < h; y++) { map[y][0] = 'W'; map[y][w-1] = 'W'; }

        // Exit door at bottom centre
        map[h-1][w/2] = 'd';

        // Shop building: x=[2..14], y=[2..10]
        buildRoom(map, 2, 2, 14, 10);
        for (int x = 4; x <= 12; x++) map[4][x] = 'c';
        // Inn building: x=[25..38], y=[2..10]
        buildRoom(map, 25, 2, 38, 10);
        for (int x = 27; x <= 36; x++) map[4][x] = 'c';

        return map;
    }

    private void buildRoom(char[][] map, int x1, int y1, int x2, int y2) {
        for (int x = x1; x <= x2; x++) { map[y1][x] = 'W'; map[y2][x] = 'W'; }
        for (int y = y1; y <= y2; y++) { map[y][x1] = 'W'; map[y][x2] = 'W'; }
    }

    private void createNPCs() {
        npcs.clear();
        npcs.add(new NPC(null, "Merchant",  "npcs/shopkeeper", NPC.Type.SHOPKEEPER,
                "Welcome! Browse my wares, traveler.", 8, 5));
        npcs.add(new NPC(null, "Innkeeper", "npcs/innkeeper",  NPC.Type.INNKEEPER,
                "Rest your weary bones, traveler. 10 gold a night.", 31, 5));
    }

    public void save() {
        if (loadFailed) {
            // The file on disk would not parse, so what is in memory is a placeholder.
            // Writing it would destroy the authored town we failed to read.
            System.err.println("[Town] Refusing to save over unreadable town file '" + name + "'");
            return;
        }
        new File(TOWNS_DIR).mkdirs();
        MapData md = new MapData();
        md.type = MapData.MapType.TOWN;
        md.name = name;
        md.width = interiorMap[0].length;
        md.height = interiorMap.length;
        md.tiles = interiorMap;
        md.spawnDifficulty = new int[md.height][md.width];
        md.interiorEntryX = interiorEntryX;
        md.interiorEntryY = interiorEntryY;
        md.npcs.clear();
        md.npcs.addAll(npcs);
        if (mapData != null && mapData.initialTileStates != null) {
            md.initialTileStates = mapData.initialTileStates;
        }

        try {
            md.save(new File(TOWNS_DIR + name.toLowerCase() + ".rfmap"));
        } catch (Exception e) {
            System.err.println("[Town] Failed to save town " + name);
        }
    }

    public String getName() { return name; }
    public char[][] getInteriorMap() { return interiorMap; }
    public int getWorldDoorX() { return worldDoorX; }
    public int getWorldDoorY() { return worldDoorY; }
    public int getInteriorEntryX() { return interiorEntryX; }
    public int getInteriorEntryY() { return interiorEntryY; }
    public List<NPC> getNpcs() { return npcs; }
    public MapData getMapData() { return mapData; }
}

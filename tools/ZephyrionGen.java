import com.google.gson.*;
import java.awt.Color;
import java.io.*;
import java.util.*;

/**
 * Generates Island 3 (Zephyrion / The Storm Archipelago):
 * - Overworld map (230x190) with floating islands, rope bridges, storms
 * - 4 town maps: Windhaven (35x30), Stormspire (25x25), Galewick (20x20), Cloudrest (20x20)
 * - Patches Pyralis overworld with exit portal
 *
 * Run:
 *   javac -cp "target/classes;%USERPROFILE%\.m2\repository\com\google\code\gson\gson\2.10.1\gson-2.10.1.jar" tools/ZephyrionGen.java -d tools/
 *   java -cp "tools;target/classes;%USERPROFILE%\.m2\repository\com\google\code\gson\gson\2.10.1\gson-2.10.1.jar" ZephyrionGen
 */
public class ZephyrionGen {

    static final int OW_W = 230, OW_H = 190;
    static Random rng = new Random(777);
    static Gson gson = new GsonBuilder().setPrettyPrinting().create();

    // Tile chars for Zephyrion overworld
    static final char SKY_VOID     = '`';
    static final char CLOUD        = ']';
    static final char SKY_GRASS    = '_';
    static final char SKY_ROCK     = '=';
    static final char ROPE_BRIDGE  = '^';
    static final char STORM_CLOUD  = '<';
    static final char LIGHTNING_ROD= '>';
    static final char SKY_TREE     = '\\';
    static final char WINDMILL_CH  = '~'; // reuse water char visually for windmill POI
    static final char TOWN_ENTRY   = 'E';
    static final char DUNGEON_ENT  = 'D';
    static final char PORTAL_FWD   = 'V'; // Pyralis→Zephyrion portal on Pyralis map
    static final char PORTAL_RET   = 'K'; // Zephyrion→Pyralis return portal

    public static void main(String[] args) throws Exception {
        System.out.println("=== Generating Island 3: The Storm Archipelago ===");

        generateOverworld();
        generateWindhaven();
        generateStormspire();
        generateGalewick();
        generateCloudrest();
        patchPyralisPortal();

        System.out.println("\n=== Island 3 generation complete! ===");
    }

    // ── OVERWORLD ────────────────────────────────────────────────────────────

    static void generateOverworld() throws Exception {
        char[][] tiles = new char[OW_H][OW_W];
        int[][] spawn = new int[OW_H][OW_W];

        // Fill with sky void
        for (char[] row : tiles) Arrays.fill(row, SKY_VOID);

        // Define island regions: {centerX, centerY, radiusX, radiusY, name}
        int[][] islands = {
            {115, 50, 45, 30},   // Main Island (Windhaven) — largest, center-north
            {180, 65, 25, 20},   // Temple Peak (Stormspire) — east
            {175, 135, 22, 18},  // Cliff Edge (Galewick) — south-east
            {50, 100, 20, 18},   // Hermit Isle (Cloudrest) — west
            {115, 120, 30, 22},  // Central Plateau — mid area, connects islands
            {40, 40, 18, 15},    // Crashed Airship Island — north-west
            {165, 30, 12, 10},   // Lightning Tower Islet — small, far north-east
            {80, 155, 15, 12},   // Wind Garden Islet — small, south-west
        };

        // Generate island terrain
        for (int[] island : islands) {
            generateIsland(tiles, spawn, island[0], island[1], island[2], island[3]);
        }

        // Storm Wall region (east edge) — dangerous storm clouds
        for (int y = 20; y < 170; y++) {
            for (int x = 210; x < 228; x++) {
                if (rng.nextDouble() < 0.7) {
                    tiles[y][x] = STORM_CLOUD;
                    spawn[y][x] = 50 + rng.nextInt(40); // high danger
                } else {
                    tiles[y][x] = CLOUD;
                    spawn[y][x] = 30 + rng.nextInt(20);
                }
            }
        }

        // Cloud maze region (center-south)
        for (int y = 130; y < 160; y++) {
            for (int x = 95; x < 140; x++) {
                if (tiles[y][x] == SKY_VOID && rng.nextDouble() < 0.35) {
                    tiles[y][x] = CLOUD;
                    spawn[y][x] = 15 + rng.nextInt(15);
                }
            }
        }

        // Connect islands with rope bridges
        drawBridge(tiles, 140, 50, 158, 60);     // Main → Temple Peak
        drawBridge(tiles, 115, 75, 115, 100);     // Main → Central Plateau
        drawBridge(tiles, 75, 100, 95, 105);      // Cloudrest → Central
        drawBridge(tiles, 140, 120, 155, 130);    // Central → Galewick
        drawBridge(tiles, 95, 50, 75, 45);        // Main → Airship Island
        drawBridge(tiles, 160, 50, 165, 38);      // Temple area → Lightning Islet
        drawBridge(tiles, 100, 120, 85, 148);     // Central → Wind Garden
        drawBridge(tiles, 175, 90, 175, 118);     // Temple → Galewick path

        // Place towns
        int[][] townPos = {
            {115, 45},   // Windhaven on Main Island
            {180, 60},   // Stormspire on Temple Peak
            {175, 132},  // Galewick on Cliff Edge
            {50, 97},    // Cloudrest on Hermit Isle
        };
        for (int[] tp : townPos) {
            tiles[tp[1]][tp[0]] = TOWN_ENTRY;
            spawn[tp[1]][tp[0]] = 0;
        }

        // Dungeon entrance on Central Plateau
        tiles[115][118] = DUNGEON_ENT;
        spawn[115][118] = 0;

        // Return portal to Pyralis (south edge of main island)
        tiles[78][110] = PORTAL_RET;
        spawn[78][110] = 0;

        // Lightning Rod safe points
        int[][] rods = {{100, 48}, {130, 55}, {160, 70}, {120, 110}, {55, 95}, {170, 128}};
        for (int[] r : rods) {
            if (r[1] < OW_H && r[0] < OW_W) {
                tiles[r[1]][r[0]] = LIGHTNING_ROD;
                spawn[r[1]][r[0]] = 0;
            }
        }

        // Crashed airship POI
        tiles[38][40] = '?'; // special/interactable

        // Windmill on Main Island
        tiles[55][105] = WINDMILL_CH;

        // Build map data
        JsonObject map = new JsonObject();
        map.addProperty("type", "OVERWORLD");
        map.addProperty("name", "zephyrion");
        map.addProperty("width", OW_W);
        map.addProperty("height", OW_H);

        // Tiles as 2D array of strings
        JsonArray tilesArr = new JsonArray();
        for (int y = 0; y < OW_H; y++) {
            JsonArray row = new JsonArray();
            for (int x = 0; x < OW_W; x++) {
                row.add(String.valueOf(tiles[y][x]));
            }
            tilesArr.add(row);
        }
        map.add("tiles", tilesArr);

        // Spawn difficulty
        JsonArray spawnArr = new JsonArray();
        for (int y = 0; y < OW_H; y++) {
            JsonArray row = new JsonArray();
            for (int x = 0; x < OW_W; x++) {
                row.add(spawn[y][x]);
            }
            spawnArr.add(row);
        }
        map.add("spawnDifficulty", spawnArr);

        // Town entrances
        JsonArray towns = new JsonArray();
        String[] townNames = {"windhaven", "stormspire", "galewick", "cloudrest"};
        for (int i = 0; i < 4; i++) {
            JsonObject te = new JsonObject();
            te.addProperty("townName", townNames[i]);
            te.addProperty("worldX", townPos[i][0]);
            te.addProperty("worldY", townPos[i][1]);
            towns.add(te);
        }
        map.add("townEntrances", towns);
        map.add("overworldTeleporters", new JsonArray());
        map.addProperty("maxDungeonDepth", 50);
        map.add("initialTileStates", new JsonObject());

        // NPCs
        JsonArray npcs = new JsonArray();
        npcs.add(makeNpc("npc_zeph_oren", "Skykeeper Oren", "npcs/sage",
            "QUESTGIVER", 125, 52,
            "The winds carry words, traveler. I am Oren, keeper of the sky-bridges. Zephyrion sends you, yes? The Laughing Gale has been expecting someone like you.",
            makeDialogueTree("greeting",
                "The winds carry words, traveler. I am Oren, keeper of the sky-bridges. Zephyrion has been expecting someone like you.",
                new String[][]{
                    {"Who is Zephyrion?", "zephyrion_lore"},
                    {"What is this place?", "island_lore"},
                    {"I'm looking for the Key of Gales.", "key_quest"},
                    {"Farewell.", "farewell"}
                },
                new String[][]{
                    {"zephyrion_lore", "Zephyrion is the Laughing Gale \u2014 god of wind, storms, change, and freedom. He shaped these islands from clouds and lightning. Unlike the other gods, he actually enjoys existing. Finds mortality delightful, he says. Of course, his idea of 'help' is often... unpredictable.",
                        "Tell me more.|greeting", "Farewell.|farewell"},
                    {"island_lore", "The Storm Archipelago \u2014 floating islands held aloft by Zephyrion\u0027s breath. Rope bridges connect the major landmasses. The storms between them are deadly, but the lightning rods keep the settlements safe. Four towns survive up here: Windhaven, Stormspire, Galewick, and Cloudrest.",
                        "Interesting.|greeting", "Farewell.|farewell"},
                    {"key_quest", "The Key of Gales... crystallized lightning, humming with static. It\u0027s held in the Stormspire temple, but Zephyrion won\u0027t release it to just anyone. You\u0027ll need to prove yourself worthy. Speak to the Tempest Twins at Stormspire \u2014 they oversee the trials.",
                        "I\u0027ll head to Stormspire.|farewell", "Tell me about the Twins.|twins_lore"},
                    {"twins_lore", "Reva and Milo \u2014 brother and sister stormcallers. Reva is precise, calculating. Milo is... reckless. Brilliant, but reckless. They don\u0027t always agree on how the trials should be run. Be careful which one you side with.",
                        "Thanks for the warning.|farewell"},
                    {"farewell", "May the wind be at your back, traveler. And if it\u0027s not... well, that\u0027s Zephyrion\u0027s idea of humor."}
                }
            )));

        npcs.add(makeNpc("npc_zeph_windlass", "Captain Windlass", "npcs/pirate",
            "TOWNSFOLK", 170, 140,
            "Ahoy! You look like someone who doesn't belong up here. Good — neither do I.",
            makeDialogueTree("greeting",
                "Ahoy! Captain Windlass, at your service. Sky-pirate, smuggler, and the only person on these islands with a working ship. Well... mostly working.",
                new String[][]{
                    {"You're a sky-pirate?", "pirate_lore"},
                    {"What do you smuggle?", "smuggling"},
                    {"Know anything useful?", "useful_info"},
                    {"Fair winds.", "farewell"}
                },
                new String[][]{
                    {"pirate_lore", "The Storm Archipelago has currents of wind between the islands that can carry a ship \u2014 if you know the routes. Most people use the rope bridges like sensible folk. I prefer the scenic route. Faster, too, if the storms cooperate.",
                        "Sounds dangerous.|greeting", "Fair winds.|farewell"},
                    {"smuggling", "Information, mostly. Goods between islands the temples don\u0027t want traded. People who need to disappear. Nothing that\u0027ll keep me up at night. The Stormspire priests don\u0027t approve, but Zephyrion himself? I think he finds it amusing.",
                        "Interesting.|greeting"},
                    {"useful_info", "There\u0027s an island Zephyrion forgot. Or pretends to have forgotten. Northwest of here, shrouded in permanent fog. My compass goes haywire near it. Something\u0027s there \u2014 something old. But every time I try to sail close, the wind pushes me back.",
                        "A hidden island?|hidden_island", "Good to know.|farewell"},
                    {"hidden_island", "I\u0027ve been trying to reach it for years. The fog... it\u0027s not natural. It moves against the wind. Whatever\u0027s there, Zephyrion doesn\u0027t want anyone finding it. Which, naturally, makes me want to find it even more.",
                        "Maybe I can help someday.|farewell"},
                    {"farewell", "Keep your eyes on the sky, friend. Storms come fast up here."}
                }
            )));

        npcs.add(makeNpc("npc_zeph_weathervane", "The Weathervane", "npcs/oracle",
            "TOWNSFOLK", 48, 105,
            "The wind... it speaks... but not in words...",
            makeDialogueTree("greeting",
                "Rain tomorrow. Sun never. The wind speaks in spirals and I... I listen. You... you are the still point. The eye of every storm. Null. Null. Null.",
                new String[][]{
                    {"What do you mean, 'Null'?", "null_alignment"},
                    {"Are you alright?", "condition"},
                    {"What do you see in my future?", "prophecy"},
                    {"I should go.", "farewell"}
                },
                new String[][]{
                    {"null_alignment", "The seven gods each pull. Lirandel pulls toward mercy. Pyralis toward ambition. Zephyrion toward freedom. But you... you resist all pulls equally. Null Alignment \u2014 beholden to no god, claimed by none. It makes you dangerous. It makes you \u2026 necessary. The Forge Eternal cannot be relit by a partisan. Only the unaligned can touch all seven shards without being consumed.",
                        "The Forge Eternal?|forge_eternal", "That\u0027s unsettling.|farewell"},
                    {"forge_eternal", "Seven shards. Seven gods. Seven islands. The Forge that shaped the world itself, shattered in the Godswar. Each god kept a piece \u2014 Lirandel\u0027s Shard of Corruption, Pyralis\u0027s Shard of Ambition, Zephyrion\u0027s Shard of Freedom. Someone is gathering them. Someone who is not you. The countdown continues.",
                        "Who is gathering them?|gatherer", "I\u0027ll stop it.|farewell"},
                    {"gatherer", "I see... a figure in shadow. Not Umbryn \u2014 Umbryn IS shadow. This one WEARS it. They move between islands unseen. They have already claimed two shards. Time spirals. The storm approaches.",
                        "I need to hurry.|farewell"},
                    {"condition", "My mind was shattered when I touched the Eye of the Storm \u2014 Zephyrion\u0027s sacred relic. I see all weather, all winds, all possible skies. It is... too much. But in the noise, there are patterns. And you are the biggest pattern of all.",
                        "What patterns?|prophecy", "I\u0027m sorry.|farewell"},
                    {"prophecy", "I see seven keys. Seven doors. Seven tests. You have passed two. Five remain. The wind says you will face a choice on every island \u2014 mercy or power, order or chaos, truth or comfort. Choose carefully. The Forge remembers every decision.",
                        "I\u0027ll remember.|farewell"},
                    {"farewell", "The barometer falls. Storm coming. Always a storm coming."}
                }
            )));

        npcs.add(makeNpc("npc_zeph_grik", "Grounded Grik", "npcs/dwarf",
            "SHOPKEEPER", 55, 48,
            "Bah! Heights. Who decided to build a civilization in the SKY?",
            null));

        map.add("npcs", npcs);

        // Write
        writeJson(map, "data/overworlds/zephyrion.rfmap");
        System.out.println("Generated: data/overworlds/zephyrion.rfmap (" + OW_W + "x" + OW_H + ")");
    }

    static void generateIsland(char[][] tiles, int[][] spawn, int cx, int cy, int rx, int ry) {
        for (int y = cy - ry; y <= cy + ry; y++) {
            for (int x = cx - rx; x <= cx + rx; x++) {
                if (y < 0 || y >= OW_H || x < 0 || x >= OW_W) continue;
                double dx = (double)(x - cx) / rx;
                double dy = (double)(y - cy) / ry;
                double dist = dx*dx + dy*dy;
                // Add noise for irregular edges
                double noise = (rng.nextDouble() - 0.5) * 0.3;
                if (dist + noise < 0.85) {
                    // Interior
                    double r = rng.nextDouble();
                    if (dist + noise > 0.65) {
                        // Rocky edge — higher difficulty
                        tiles[y][x] = SKY_ROCK;
                        spawn[y][x] = 17 + rng.nextInt(5); // levels 9-11
                    } else if (r < 0.08) {
                        tiles[y][x] = SKY_TREE;
                        spawn[y][x] = 15 + rng.nextInt(5); // levels 8-10
                    } else {
                        tiles[y][x] = SKY_GRASS;
                        spawn[y][x] = 15 + rng.nextInt(5); // levels 8-10
                    }
                } else if (dist + noise < 1.0) {
                    // Cloud fringe
                    tiles[y][x] = CLOUD;
                    spawn[y][x] = 15 + rng.nextInt(4); // levels 8-9
                }
            }
        }
    }

    static void drawBridge(char[][] tiles, int x1, int y1, int x2, int y2) {
        int dx = x2 - x1, dy = y2 - y1;
        int steps = Math.max(Math.abs(dx), Math.abs(dy));
        if (steps == 0) return;
        for (int i = 0; i <= steps; i++) {
            int x = x1 + dx * i / steps;
            int y = y1 + dy * i / steps;
            if (y >= 0 && y < OW_H && x >= 0 && x < OW_W) {
                if (tiles[y][x] == SKY_VOID || tiles[y][x] == CLOUD) {
                    tiles[y][x] = ROPE_BRIDGE;
                }
            }
        }
    }

    // ── TOWN GENERATORS ──────────────────────────────────────────────────────

    static void generateWindhaven() throws Exception {
        int w = 35, h = 30;
        char[][] t = makeTownBase(w, h, 'F', 'W');
        // Sky floor interior
        for (int y = 1; y < h-1; y++)
            for (int x = 1; x < w-1; x++)
                if (t[y][x] == 'F') t[y][x] = 'F'; // keep floor

        // Shop building (left)
        fillRect(t, 2, 2, 10, 8, 'W'); fillRect(t, 3, 3, 8, 6, 'F');
        t[8][6] = 'd'; // door

        // Inn building (right)
        fillRect(t, 23, 2, 10, 8, 'W'); fillRect(t, 24, 3, 8, 6, 'F');
        t[8][27] = 'd'; // door

        // Central plaza (open sky floor)
        fillRect(t, 12, 10, 11, 8, 'F');

        // Sky dock (bottom)
        for (int x = 14; x < 21; x++) t[h-2][x] = 'F';

        // Exit
        t[h-1][w/2] = 'd';

        JsonObject map = buildTownMap("windhaven", w, h, t, w/2, h-2);

        JsonArray npcs = new JsonArray();
        npcs.add(makeTownNpc("npc_wh_shopkeeper", "Sky Merchant Lira", "npcs/shopkeeper",
            "SHOPKEEPER", 6, 4,
            new String[]{"obsidian_blade", "healing_potion", "pyralis_fire_potion", "stormcaller_blade", "windweave_armor", "gale_shield", "vial_of_lightning"},
            "Welcome to the highest shop in the realm! Everything\u0027s a bit more expensive up here \u2014 shipping costs, you understand."));
        npcs.add(makeTownNpc("npc_wh_innkeeper", "Innkeeper Brisa", "npcs/innkeeper",
            "INNKEEPER", 27, 4, null,
            "Rest your wings, traveler. The beds are soft and the altitude sickness passes after the first night."));
        npcs.add(makeTownNpc("npc_wh_guard", "Sky Guard", "npcs/guard",
            "GUARD", 16, 12, null,
            "Windhaven stands as the heart of the Archipelago. The rope bridges connect all settlements through here. Watch your step near the edges \u2014 it\u0027s a long way down."));
        npcs.add(makeTownNpc("npc_wh_child", "Kite Boy", "npcs/child",
            "TOWNSFOLK", 20, 15, null, null));
        map.add("npcs", npcs);

        writeJson(map, "data/towns/windhaven.rfmap");
        System.out.println("Generated: data/towns/windhaven.rfmap (35x30)");
    }

    static void generateStormspire() throws Exception {
        int w = 25, h = 25;
        char[][] t = makeTownBase(w, h, 'F', 'W');

        // Temple interior (large central chamber)
        fillRect(t, 4, 3, 17, 15, 'W'); fillRect(t, 5, 4, 15, 13, 'F');
        t[16][12] = 'd'; // temple door

        // Altar area at top of temple
        t[5][12] = 'A'; // altar

        // Side chambers
        fillRect(t, 2, 19, 8, 4, 'W'); fillRect(t, 3, 20, 6, 2, 'F');
        t[19][5] = 'd';
        fillRect(t, 15, 19, 8, 4, 'W'); fillRect(t, 16, 20, 6, 2, 'F');
        t[19][19] = 'd';

        t[h-1][w/2] = 'd'; // exit

        JsonObject map = buildTownMap("stormspire", w, h, t, w/2, h-2);

        JsonArray npcs = new JsonArray();
        npcs.add(makeTownNpc("npc_ss_reva", "Reva", "npcs/wizard",
            "QUESTGIVER", 10, 6, null,
            "Precision in all things. The storm obeys those who understand its patterns."));
        npcs.add(makeTownNpc("npc_ss_milo", "Milo", "npcs/bard",
            "QUESTGIVER", 14, 6, null,
            "Rules? Ha! The storm doesn\u0027t follow rules. Why should we?"));
        npcs.add(makeTownNpc("npc_ss_acolyte", "Storm Acolyte", "npcs/priest",
            "TOWNSFOLK", 12, 10, null,
            "The Stormspire channels Zephyrion\u0027s power. The trials test those who would claim the Key of Gales."));
        map.add("npcs", npcs);

        writeJson(map, "data/towns/stormspire.rfmap");
        System.out.println("Generated: data/towns/stormspire.rfmap (25x25)");
    }

    static void generateGalewick() throws Exception {
        int w = 20, h = 20;
        char[][] t = makeTownBase(w, h, 'F', 'W');

        // Fishing hut (left)
        fillRect(t, 2, 2, 6, 5, 'W'); fillRect(t, 3, 3, 4, 3, 'F');
        t[6][4] = 'd';

        // Tavern (right)
        fillRect(t, 12, 2, 6, 5, 'W'); fillRect(t, 13, 3, 4, 3, 'F');
        t[6][14] = 'd';

        // Dock area (bottom)
        for (int x = 4; x < 16; x++) t[h-3][x] = 'F';

        t[h-1][w/2] = 'd';

        JsonObject map = buildTownMap("galewick", w, h, t, w/2, h-2);

        JsonArray npcs = new JsonArray();
        npcs.add(makeTownNpc("npc_gw_fisher", "Old Fisher Nol", "npcs/fisher",
            "TOWNSFOLK", 4, 4, null,
            "We fish the cloud streams. Aye, cloud fish \u2014 translucent things that swim through vapor. Taste like chicken if you squint."));
        npcs.add(makeTownNpc("npc_gw_barmaid", "Gust", "npcs/barmaid",
            "TOWNSFOLK", 14, 4, null, null));
        npcs.add(makeTownNpc("npc_gw_dockmaster", "Dockmaster Vael", "npcs/pirate",
            "QUESTGIVER", 10, h-4, null,
            "The sky docks are mostly for Windlass\u0027s ship. She comes and goes like the weather."));
        map.add("npcs", npcs);

        writeJson(map, "data/towns/galewick.rfmap");
        System.out.println("Generated: data/towns/galewick.rfmap (20x20)");
    }

    static void generateCloudrest() throws Exception {
        int w = 20, h = 20;
        char[][] t = makeTownBase(w, h, 'F', 'W');

        // Hermit dwelling (center)
        fillRect(t, 6, 4, 8, 6, 'W'); fillRect(t, 7, 5, 6, 4, 'F');
        t[9][10] = 'd';

        // Meditation circle (open area)
        for (int y = 12; y < 16; y++)
            for (int x = 6; x < 14; x++)
                t[y][x] = 'F';

        t[h-1][w/2] = 'd';

        JsonObject map = buildTownMap("cloudrest", w, h, t, w/2, h-2);

        JsonArray npcs = new JsonArray();
        npcs.add(makeTownNpc("npc_cr_hermit", "Cloud Hermit Alis", "npcs/old_woman",
            "HEALER", 9, 6, null,
            "The clouds teach patience. Sit. Breathe. The sky will show you what you need."));
        npcs.add(makeTownNpc("npc_cr_monk", "Silent Monk", "npcs/monk",
            "TOWNSFOLK", 10, 13, null,
            "..."));
        map.add("npcs", npcs);

        writeJson(map, "data/towns/cloudrest.rfmap");
        System.out.println("Generated: data/towns/cloudrest.rfmap (20x20)");
    }

    // ── PYRALIS PORTAL PATCH ─────────────────────────────────────────────────

    static void patchPyralisPortal() throws Exception {
        File f = new File("data/overworlds/pyralis.rfmap");
        JsonObject pyralis;
        try (FileReader r = new FileReader(f)) {
            pyralis = JsonParser.parseReader(r).getAsJsonObject();
        }

        // Place forward portal tile at a suitable location on Pyralis
        // Near the north edge, accessible after getting key_of_embers
        JsonArray tiles = pyralis.getAsJsonArray("tiles");
        // Place at (90, 25) — north of Forge Keep
        JsonArray row25 = tiles.get(25).getAsJsonArray();
        row25.set(90, new JsonPrimitive(String.valueOf(PORTAL_FWD)));

        // Clear spawn difficulty at portal
        JsonArray spawnDiff = pyralis.getAsJsonArray("spawnDifficulty");
        if (spawnDiff != null && spawnDiff.size() > 25) {
            JsonArray srow25 = spawnDiff.get(25).getAsJsonArray();
            srow25.set(90, new JsonPrimitive(0));
        }

        try (FileWriter w = new FileWriter(f)) {
            gson.toJson(pyralis, w);
        }
        System.out.println("Patched: data/overworlds/pyralis.rfmap (added Zephyrion portal at 90,25)");
    }

    // ── HELPERS ──────────────────────────────────────────────────────────────

    static char[][] makeTownBase(int w, int h, char floor, char wall) {
        char[][] t = new char[h][w];
        for (char[] row : t) Arrays.fill(row, wall);
        for (int y = 1; y < h-1; y++)
            for (int x = 1; x < w-1; x++)
                t[y][x] = floor;
        return t;
    }

    static void fillRect(char[][] t, int x, int y, int w, int h, char c) {
        for (int dy = y; dy < y+h && dy < t.length; dy++)
            for (int dx = x; dx < x+w && dx < t[0].length; dx++)
                t[dy][dx] = c;
    }

    static JsonObject buildTownMap(String name, int w, int h, char[][] t, int entryX, int entryY) {
        JsonObject map = new JsonObject();
        map.addProperty("type", "TOWN");
        map.addProperty("name", name);
        map.addProperty("width", w);
        map.addProperty("height", h);
        map.addProperty("interiorEntryX", entryX);
        map.addProperty("interiorEntryY", entryY);

        JsonArray tilesArr = new JsonArray();
        for (int y = 0; y < h; y++) {
            JsonArray row = new JsonArray();
            for (int x = 0; x < w; x++) row.add(String.valueOf(t[y][x]));
            tilesArr.add(row);
        }
        map.add("tiles", tilesArr);
        map.add("townEntrances", new JsonArray());
        map.add("overworldTeleporters", new JsonArray());
        map.add("initialTileStates", new JsonObject());
        return map;
    }

    static JsonObject makeNpc(String id, String name, String sprite, String type,
                              int x, int y, String defaultDialog, JsonObject dialogueTree) {
        JsonObject npc = new JsonObject();
        npc.addProperty("id", id);
        npc.addProperty("name", name);
        npc.addProperty("spriteName", sprite);
        npc.addProperty("type", type);
        npc.addProperty("defaultDialog", defaultDialog != null ? defaultDialog : "");
        npc.addProperty("questCompleteDialog", "");
        npc.add("shopItemIds", new JsonArray());
        npc.addProperty("x", x);
        npc.addProperty("y", y);
        if (dialogueTree != null) npc.add("dialogueTree", dialogueTree);
        return npc;
    }

    static JsonObject makeTownNpc(String id, String name, String sprite, String type,
                                   int x, int y, String[] shopItems, String dialog) {
        JsonObject npc = new JsonObject();
        npc.addProperty("id", id);
        npc.addProperty("name", name);
        npc.addProperty("spriteName", sprite);
        npc.addProperty("type", type);
        npc.addProperty("defaultDialog", dialog != null ? dialog : "Hello, traveler.");
        npc.addProperty("questCompleteDialog", "");
        JsonArray items = new JsonArray();
        if (shopItems != null) for (String s : shopItems) items.add(s);
        npc.add("shopItemIds", items);
        npc.addProperty("x", x);
        npc.addProperty("y", y);
        return npc;
    }

    /** Builds a dialogue tree from a compact description. */
    static JsonObject makeDialogueTree(String startId, String startText,
                                        String[][] startChoices, String[][] nodeDescs) {
        JsonObject tree = new JsonObject();
        tree.addProperty("startNodeId", startId);
        JsonObject nodes = new JsonObject();

        // Start node
        nodes.add(startId, makeNode(startId, startText, startChoices));

        // Additional nodes
        for (String[] desc : nodeDescs) {
            String nodeId = desc[0];
            String text = desc[1];
            if (nodeId.equals("farewell")) {
                JsonObject fw = new JsonObject();
                fw.addProperty("id", "farewell");
                fw.addProperty("text", text);
                fw.add("choices", new JsonArray());
                JsonArray actions = new JsonArray();
                JsonObject close = new JsonObject();
                close.addProperty("type", "CLOSE_DIALOGUE");
                close.addProperty("target", ""); close.addProperty("value", ""); close.addProperty("amount", 0);
                actions.add(close);
                fw.add("actions", actions);
                nodes.add("farewell", fw);
                continue;
            }
            // Parse choices from remaining elements: "label|nextNode"
            String[][] choices = new String[desc.length - 2][];
            for (int i = 2; i < desc.length; i++) {
                String[] parts = desc[i].split("\\|");
                choices[i-2] = parts;
            }
            nodes.add(nodeId, makeNode(nodeId, text, choices));
        }

        tree.add("nodes", nodes);
        return tree;
    }

    static JsonObject makeNode(String id, String text, String[][] choices) {
        JsonObject node = new JsonObject();
        node.addProperty("id", id);
        node.addProperty("text", text);
        JsonArray choicesArr = new JsonArray();
        if (choices != null) {
            for (String[] c : choices) {
                JsonObject ch = new JsonObject();
                ch.addProperty("label", c[0]);
                if (c.length > 1) ch.addProperty("nextNodeId", c[1]);
                ch.add("conditions", new JsonArray());
                choicesArr.add(ch);
            }
        }
        node.add("choices", choicesArr);
        node.add("actions", new JsonArray());
        return node;
    }

    static void writeJson(JsonObject obj, String path) throws Exception {
        new File(path).getParentFile().mkdirs();
        try (FileWriter w = new FileWriter(path)) {
            gson.toJson(obj, w);
        }
    }
}

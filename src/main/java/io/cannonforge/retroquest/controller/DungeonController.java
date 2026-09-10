package io.cannonforge.retroquest.controller;
import io.cannonforge.retroquest.animation.DungeonEntryAnimation;
import io.cannonforge.retroquest.animation.DungeonExitAnimation;
import io.cannonforge.retroquest.core.DungeonWallQuery;
import io.cannonforge.retroquest.core.MessageLog;
import io.cannonforge.retroquest.core.Retroquest;
import io.cannonforge.retroquest.core.SoundManager;
import io.cannonforge.retroquest.core.Town;
import io.cannonforge.retroquest.core.VisionSystem;
import io.cannonforge.retroquest.model.BalanceConfig;
import io.cannonforge.retroquest.model.Boon;
import io.cannonforge.retroquest.model.Dungeon;
import io.cannonforge.retroquest.model.DungeonBoss;
import io.cannonforge.retroquest.model.God;
import io.cannonforge.retroquest.model.Item;
import io.cannonforge.retroquest.model.MapData;
import io.cannonforge.retroquest.model.Monster;
import io.cannonforge.retroquest.model.Player;
import io.cannonforge.retroquest.model.Quest;
import io.cannonforge.retroquest.model.TileState;
import io.cannonforge.retroquest.model.TileStateManager;
import io.cannonforge.retroquest.registry.DungeonBossRegistry;
import io.cannonforge.retroquest.registry.ItemRegistry;
import io.cannonforge.retroquest.registry.LootGenerator;
import io.cannonforge.retroquest.registry.MonsterRegistry;

/**
 * Manages all dungeon-related gameplay: entering and exiting the dungeon,
 * moving between levels via stairs or pit falls, handling dungeon special
 * events (altars, fountains, ground loot, glowing cubes, thrones, teleporters,
 * pits, and inns), fog-of-war reveal, and loot generation.
 *
 * <p>All state mutations go through the {@link Retroquest} instance passed
 * at construction time; this class itself holds only the two entry-position
 * fields that only it ever reads or writes.
 */
public class DungeonController {

    private final Retroquest game;

    /** Overworld tile position where the player entered the dungeon. */
    private int lastDungeonEntryX;
    private int lastDungeonEntryY;

    /** Pending exit state — populated before the exit animation starts. */
    private int pendingExitX;
    private int pendingExitY;

    /**
     * Town the player was standing in when they entered the dungeon, or {@code null} for the
     * usual overworld entrance. Four town maps contain 'D' dungeon entrances, and the entry
     * coordinates recorded for those are town-interior coordinates — meaningless on the
     * overworld — so the exit has to put the player back where they came from.
     */
    private String entryTownName;
    private int    entryTownDoorX;
    private int    entryTownDoorY;

    public int getLastDungeonEntryX() { return lastDungeonEntryX; }
    public int getLastDungeonEntryY() { return lastDungeonEntryY; }
    public void setLastDungeonEntry(int x, int y) { lastDungeonEntryX = x; lastDungeonEntryY = y; }

    public String getEntryTownName()  { return entryTownName; }
    public int    getEntryTownDoorX() { return entryTownDoorX; }
    public int    getEntryTownDoorY() { return entryTownDoorY; }
    public void   setEntryTown(String name, int doorX, int doorY) {
        this.entryTownName  = name;
        this.entryTownDoorX = doorX;
        this.entryTownDoorY = doorY;
    }

    /**
     * Records where the player came from and leaves the surface behind. Entering a dungeon
     * from inside a town used to leave {@code currentTown} set for the whole descent, which
     * disabled random encounters and map spellcasting for the rest of the session.
     */
    private void leaveSurfaceForDungeon() {
        lastDungeonEntryX = game.getPlayer().getX();
        lastDungeonEntryY = game.getPlayer().getY();
        Town town = game.getCurrentTown();
        if (town != null) {
            setEntryTown(town.getName(), town.getWorldDoorX(), town.getWorldDoorY());
            game.setCurrentTown(null);
        } else {
            setEntryTown(null, 0, 0);
        }
    }

    // ── All balance constants loaded from data/balance.json ─────────────────
    private static final BalanceConfig.Dungeon B = BalanceConfig.get().getDungeon();

    // ── Dungeon limits ────────────────────────────────────────────────────────
    private static final int DEFAULT_MAX_DEPTH       = B.maxDepth;
    private static final int MAX_LEVEL_RANGE         = B.maxDepth;

    // ── Pit ───────────────────────────────────────────────────────────────────
    private static final int PIT_DMG_PER_DEPTH       = B.pitDmgPerDepth;
    private static final int PIT_DMG_BASE            = B.pitDmgBase;

    // ── Altar ─────────────────────────────────────────────────────────────────
    private static final int ALTAR_HEAL_CHANCE        = B.altarHealChance;
    private static final int ALTAR_XP_CHANCE          = B.altarXpChance;
    private static final int ALTAR_SILENT_CHANCE      = B.altarSilentChance;
    private static final int ALTAR_TITHE_CHANCE       = B.altarTitheChance;
    private static final int ALTAR_SPELL_SLOT_CHANCE  = B.altarSpellSlotChance;
    private static final int ALTAR_XP_BASE            = B.altarXpBase;
    private static final int ALTAR_XP_PER_DEPTH       = B.altarXpPerDepth;
    private static final int ALTAR_TITHE_BASE         = B.altarTitheBase;
    private static final int ALTAR_TITHE_RANDOM       = B.altarTitheRandom;
    private static final int ALTAR_WRATH_DMG_BASE     = B.altarWrathDmgBase;
    private static final int ALTAR_WRATH_DMG_PER_DEPTH = B.altarWrathDmgPerDepth;

    // ── Corrupted Shrine ──────────────────────────────────────────────────────
    private static final int SHRINE_BOSS_HP           = B.shrineBossHp;
    private static final int SHRINE_BOSS_ATK          = B.shrineBossAtk;
    private static final int SHRINE_BOSS_GOLD         = B.shrineBossGold;
    /**
     * The depth-3 shrine boss is pinned to level 4 / AC 12. The short {@code Monster} constructor
     * derives level from {@code hp/8}, which turned a 65 HP boss into a level-8 monster sitting on
     * the third floor of the game’s first dungeon — unwinnable for the level 3–5 character who
     * meets it.
     */
    private static final int SHRINE_BOSS_LEVEL        = 4;
    private static final int SHRINE_BOSS_AC           = 12;
    private static final int SHRINE_BOSS_XP           = 250;
    private static final int SHRINE_XP_BASE           = B.shrineXpBase;
    private static final int SHRINE_XP_PER_DEPTH      = B.shrineXpPerDepth;

    // ── Fountain ──────────────────────────────────────────────────────────────
    private static final int FOUNTAIN_OUTCOMES        = B.fountainOutcomes;
    private static final int FOUNTAIN_XP_SMALL_BASE   = B.fountainXpSmallBase;
    private static final int FOUNTAIN_XP_SMALL_SCALE  = B.fountainXpSmallScale;
    private static final int FOUNTAIN_XP_LARGE_BASE   = B.fountainXpLargeBase;
    private static final int FOUNTAIN_XP_LARGE_SCALE  = B.fountainXpLargeScale;
    private static final int FOUNTAIN_GOLD_BASE       = B.fountainGoldBase;
    private static final int FOUNTAIN_GOLD_PER_DEPTH  = B.fountainGoldPerDepth;
    private static final int FOUNTAIN_GOLD_RANDOM     = B.fountainGoldRandom;
    private static final int FOUNTAIN_POISON_BASE     = B.fountainPoisonBase;
    private static final int FOUNTAIN_POISON_RANDOM   = B.fountainPoisonRandom;
    private static final int FOUNTAIN_ACID_BASE       = B.fountainAcidBase;
    private static final int FOUNTAIN_ACID_PER_DEPTH  = B.fountainAcidPerDepth;
    private static final int FOUNTAIN_DIVINE_XP_BASE  = B.fountainDivineXpBase;
    private static final int FOUNTAIN_DIVINE_XP_SCALE = B.fountainDivineXpScale;

    // ── Ground Loot ───────────────────────────────────────────────────────────
    private static final double LOOT_CHANCE           = B.lootChance;
    private static final int LOOT_REFUSE_CUTOFF       = B.lootRefuseCutoff;
    private static final int LOOT_SILVER_CUTOFF       = B.lootSilverCutoff;
    private static final int LOOT_GOLD_CUTOFF         = B.lootGoldCutoff;
    private static final int LOOT_ITEM_CUTOFF         = B.lootItemCutoff;
    private static final int LOOT_CHEST_CUTOFF        = B.lootChestCutoff;
    private static final int SILVER_BASE              = B.silverBase;
    private static final int SILVER_DEPTH_SCALE       = B.silverDepthScale;
    private static final int SILVER_RANDOM_BASE       = B.silverRandomBase;
    private static final int GOLD_DEPTH_SCALE         = B.goldDepthScale;
    private static final int GOLD_BASE                = B.goldBase;
    private static final int GOLD_RANDOM_DEPTH_SCALE  = B.goldRandomDepthScale;
    private static final int GOLD_RANDOM_BASE         = B.goldRandomBase;
    private static final double ITEM_TRAP_CHANCE      = B.itemTrapChance;
    private static final int ITEM_TRAP_DEPTH_SCALE    = B.itemTrapDepthScale;
    private static final double CHEST_TRAP_CHANCE     = B.chestTrapChance;
    private static final int CHEST_TRAP_DMG_BASE      = B.chestTrapDmgBase;
    private static final int CHEST_TRAP_DMG_PER_DEPTH = B.chestTrapDmgPerDepth;
    private static final int CHEST_TRAP_GOLD_DEPTH_SCALE = B.chestTrapGoldDepthScale;
    private static final int CHEST_TRAP_GOLD_BASE     = B.chestTrapGoldBase;
    private static final int CHEST_TRAP_GOLD_RANDOM   = B.chestTrapGoldRandom;
    private static final int CHEST_GOLD_DEPTH_SCALE   = B.chestGoldDepthScale;
    private static final int CHEST_GOLD_BASE          = B.chestGoldBase;
    private static final int CHEST_GOLD_RANDOM_DEPTH  = B.chestGoldRandomDepth;
    private static final int CHEST_GOLD_RANDOM_BASE   = B.chestGoldRandomBase;
    private static final int GEM_VALUE_DEPTH_SCALE    = B.gemValueDepthScale;
    private static final int GEM_VALUE_BASE           = B.gemValueBase;
    private static final int GEM_VALUE_RANDOM_SCALE   = B.gemValueRandomScale;

    // ── Encounter ─────────────────────────────────────────────────────────────
    private static final double ENCOUNTER_BASE_RATE   = B.encounterBaseRate;
    private static final double ENCOUNTER_DEPTH_SCALE = B.encounterDepthScale;
    private static final int ENCOUNTER_VARIANCE       = B.encounterVariance;
    /** Hard ceiling on the per-step encounter chance — never worse than one fight per ~7 steps. */
    private static final double ENCOUNTER_MAX_RATE    = 0.15;

    // ── Throne ────────────────────────────────────────────────────────────────
    private static final int THRONE_HEAL_CHANCE       = B.throneHealChance;
    private static final int THRONE_GOLD_CHANCE       = B.throneGoldChance;
    private static final int THRONE_ITEM_CHANCE       = B.throneItemChance;
    private static final int THRONE_GUARD_CHANCE      = B.throneGuardChance;
    private static final int THRONE_XP_BASE           = B.throneXpBase;
    private static final int THRONE_XP_PER_DEPTH      = B.throneXpPerDepth;
    private static final int THRONE_GOLD_DEPTH_SCALE  = B.throneGoldDepthScale;
    private static final int THRONE_GOLD_BASE         = B.throneGoldBase;
    private static final int THRONE_GOLD_RANDOM_SCALE = B.throneGoldRandomScale;
    private static final int THRONE_ITEM_XP_BASE      = B.throneItemXpBase;
    private static final int THRONE_ITEM_XP_PER_DEPTH = B.throneItemXpPerDepth;
    private static final int THRONE_ITEM_LEVEL_BONUS  = B.throneItemLevelBonus;
    private static final int THRONE_GUARD_HP_BASE     = B.throneGuardHpBase;
    private static final int THRONE_GUARD_HP_PER_DEPTH = B.throneGuardHpPerDepth;
    private static final int THRONE_GUARD_ATK_BASE    = B.throneGuardAtkBase;
    private static final int THRONE_GUARD_ATK_PER_DEPTH = B.throneGuardAtkPerDepth;
    private static final int THRONE_GUARD_GOLD        = B.throneGuardGold;
    private static final int THRONE_CHAMPION_CHANCE    = B.throneChampionChance;
    private static final int THRONE_WRATH_DMG_BASE    = B.throneWrathDmgBase;
    private static final int THRONE_WRATH_DMG_PER_DEPTH = B.throneWrathDmgPerDepth;

    // ── Inn ───────────────────────────────────────────────────────────────────
    private static final int INN_COST_BASE            = B.innCostBase;
    private static final int INN_COST_PER_DEPTH       = B.innCostPerDepth;

    // ── Puzzle ──────────────────────────────────────────────────────────────
    private static final int PZ_FORGE_XP_BASE         = B.puzzleForgeXpBase;
    private static final int PZ_FORGE_XP_PER_DEPTH    = B.puzzleForgeXpPerDepth;
    private static final int PZ_FORGE_LOOT_BONUS      = B.puzzleForgeLootBonus;
    private static final int PZ_RIDDLE_GOLD_BASE      = B.puzzleRiddleGoldBase;
    private static final int PZ_RIDDLE_GOLD_PER_DEPTH = B.puzzleRiddleGoldPerDepth;
    private static final int PZ_RIDDLE_XP_BASE        = B.puzzleRiddleXpBase;
    private static final int PZ_RIDDLE_XP_PER_DEPTH   = B.puzzleRiddleXpPerDepth;
    private static final int PZ_RIDDLE_INT_HINT_BASE  = B.puzzleRiddleIntHintBase;
    private static final int PZ_RIDDLE_FAVOR          = B.puzzleRiddleFavor;
    private static final int PZ_RUNE_XP_BASE          = B.puzzleRuneLockXpBase;
    private static final int PZ_RUNE_XP_PER_DEPTH     = B.puzzleRuneLockXpPerDepth;
    private static final int PZ_RUNE_LOOT_BONUS       = B.puzzleRuneLockLootBonus;
    private static final int PZ_RUNE_WIS_HINT         = B.puzzleRuneLockWisHint;
    private static final int PZ_TRIAL_COPPER_XP_BASE  = B.puzzleTrialCopperXpBase;
    private static final int PZ_TRIAL_COPPER_XP_PER   = B.puzzleTrialCopperXpPerDepth;
    private static final int PZ_TRIAL_SILVER_XP_BASE  = B.puzzleTrialSilverXpBase;
    private static final int PZ_TRIAL_SILVER_XP_PER   = B.puzzleTrialSilverXpPerDepth;
    private static final int PZ_TRIAL_SILVER_GOLD_BASE = B.puzzleTrialSilverGoldBase;
    private static final int PZ_TRIAL_SILVER_GOLD_PER = B.puzzleTrialSilverGoldPerDepth;
    private static final int PZ_TRIAL_SILVER_DMG_PER  = B.puzzleTrialSilverDmgPerDepth;
    private static final int PZ_TRIAL_GOLD_DMG_PER    = B.puzzleTrialGoldDmgPerDepth;
    private static final int PZ_TRIAL_GOLD_XP_BASE    = B.puzzleTrialGoldXpBase;
    private static final int PZ_TRIAL_GOLD_XP_PER     = B.puzzleTrialGoldXpPerDepth;
    private static final int PZ_TRIAL_GOLD_LOOT_BONUS = B.puzzleTrialGoldLootBonus;
    private static final int PZ_TRIAL_GOLD_FAVOR      = B.puzzleTrialGoldFavor;
    private static final int PZ_TRIAL_CON_MODERATE    = B.puzzleTrialConBaseModerate;
    private static final int PZ_TRIAL_CON_HARD        = B.puzzleTrialConBaseHard;

    // ── Puzzle data ─────────────────────────────────────────────────────────
    private static final String[] FORGE_RUNES = {"FIRE", "IRON", "ASH", "EMBER", "SLAG", "OBSIDIAN"};

    /** Riddles: {question, answer1, answer2, answer3, correctIndex (0-based)} */
    private static final String[][] RIDDLES = {
        {"I am born in the mountain's heart, shaped by hammer but not by art. I cut through stone but rust in rain.",
         "A river", "An iron blade", "Lightning", "1"},
        {"I devour all I touch yet have no mouth. I fear water but am born from earth.",
         "Fire", "A worm", "Darkness", "0"},
        {"The smith strikes me a thousand times, yet I grow stronger with each blow.",
         "An anvil", "Steel", "A grudge", "1"},
        {"I flow like water but burn like the sun. No vessel can hold me for long.",
         "Molten lava", "Quicksilver", "Time", "0"},
        {"I breathe but have no lungs. I eat charcoal but never grow fat.",
         "A forge bellows", "A dragon", "The wind", "0"},
        {"I am harder than stone yet born from sand. The forge gives me life, the quench gives me strength.",
         "Glass", "Steel", "Obsidian", "1"},
        {"I dance without legs and die without murder. Feed me and I live, give me drink and I die.",
         "A shadow", "Fire", "A candle flame", "1"},
        {"I am pulled from the earth, purified by flame, and made useful by beating.",
         "Ore", "Clay", "A slave", "0"},
        {"The hotter I get, the harder I become. The colder I get, the softer I stay.",
         "Iron", "Wax", "Ice", "0"},
        {"I protect the smith's hands but fear the flame I guard against.",
         "Leather gloves", "Water", "An apron", "0"},
        {"I am the smith's song — neither music nor words, but all who forge know my rhythm.",
         "The hammer's ring", "A bellows' wheeze", "Crackling coals", "0"},
        {"I have teeth but cannot bite. I cut through metal with patient might.",
         "A file", "A saw", "Rust", "0"},
        {"Born in fire, I sleep in water. Wake me and I'll cut your daughter.",
         "A quenched blade", "A volcano", "Steam", "0"},
        {"I am black when bought, red when used, and grey when thrown away.",
         "Coal", "Iron", "Blood", "0"},
        {"The mountain bleeds me, the crucible drinks me, the mold shapes me, the water wakes me.",
         "Ore becoming metal", "Lava becoming stone", "Clay becoming pottery", "0"},
    };

    /** Rune lock clues: {clue, answer} — answers are case-insensitive single words */
    private static final String[][] RUNE_CLUES = {
        {"The smith's breath that makes iron weep", "bellows"},
        {"What the anvil endures but the blade does not", "hammer"},
        {"The mountain's blood that hardens into darkness", "obsidian"},
        {"Born from ore, I am the forge's proudest child", "steel"},
        {"I am the forge's thirst — without me, no blade is born", "water"},
        {"The black stone that feeds the flame", "coal"},
        {"I bind the hilt to the blade, leather to steel", "rivets"},
        {"The forge's bed where metal dreams take shape", "mold"},
        {"The smith's second skin, scarred by a thousand sparks", "apron"},
        {"I am the final test — plunge the blade and hear me scream", "quench"},
        {"What remains when the fire dies and the forge grows cold", "ash"},
        {"The mountain's fury made liquid, the earth's bright wound", "lava"},
        {"I am struck but never cry, heated but never burn", "anvil"},
        {"The invisible killer that eats iron slowly", "rust"},
        {"A river of fire that flows beneath the mountain's skin", "magma"},
    };

    public DungeonController(Retroquest game) {
        this.game = game;
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    /**
     * Dungeon presentation escalates island by island, the way the era's crawlers did:
     * Island 1 is an overhead torch-lit grid, Island 2 moves to a vector-drawn
     * first-person line view, and from Island 3 on the first-person renderers take over.
     * Islands 4 and 5 are textured — Sylvandar's Rootvault in bark and root, Thalorax's three
     * dungeons in chiselled crypt masonry — and Islands 6 and 7 are raycast. Only Pyralis and
     * Zephyrion still reach the wireframe renderer, in its ember and storm palettes.
     *
     * <p>Before this existed the fall-through sent Islands 5-7 back to the Island 1 view.
     *
     * <p>Public and static because {@link io.cannonforge.retroquest.core.Retroquest} has to make
     * the same decision when restoring a save; it used to keep a second, narrower copy of this
     * table, which quietly disagreed with this one for procedural dungeons past Island 4.
     */
    public static io.cannonforge.retroquest.model.DungeonViewState.Mode modeForIsland(String overworld) {
        if (overworld == null) return io.cannonforge.retroquest.model.DungeonViewState.Mode.TOP_DOWN;
        return switch (overworld) {
            case "lirandel"  -> io.cannonforge.retroquest.model.DungeonViewState.Mode.TOP_DOWN;
            case "sylvandar" -> io.cannonforge.retroquest.model.DungeonViewState.Mode.TEXTURED;
            case "thalorax"  -> io.cannonforge.retroquest.model.DungeonViewState.Mode.TEXTURED;
            case "umbryn"    -> io.cannonforge.retroquest.model.DungeonViewState.Mode.RAYCAST;
            case "bellorak"  -> io.cannonforge.retroquest.model.DungeonViewState.Mode.RAYCAST;
            default          -> io.cannonforge.retroquest.model.DungeonViewState.Mode.WIREFRAME;
        };
    }

    public void enterDungeon() {
        leaveSurfaceForDungeon();

        // Procedural dungeon: any authored state left over from the last descent would send
        // goUp/goDown into a different dungeon's 40x40 map while movement still used the
        // procedural wall maths.
        game.getDungeonViewState().setAuthoredDungeonName(null);
        game.setCurrentDungeonMapData(null);

        SoundManager.getInstance().dungeonDescent();
        game.getGamePanel().startDungeonEntryAnimation();

        game.setInDungeon(true);
        game.setCurrentDepth(1);
        game.setCurrentMap(Dungeon.generate(game.getCurrentDepth()));

        var mode = modeForIsland(game.getCurrentOverworldName());
        game.getDungeonViewState().setMode(mode);
        boolean firstPerson = mode != io.cannonforge.retroquest.model.DungeonViewState.Mode.TOP_DOWN;
        if (firstPerson) game.getPlayer().setFacing(0); // face north on entry

        game.getPlayer().setPosition(Dungeon.WIDTH / 2, Dungeon.HEIGHT / 2);

        revealVisibleArea();
        game.updateCamera();
        game.snapDungeonCamera();
        game.startDungeonViewLoop();
        game.getGamePanel().repaint();
        game.getStatsPanel().refresh();
        game.getPlayer().progressQuest(Quest.Type.EXPLORE, "Dungeon Level " + game.getCurrentDepth(), 1);
    }

    /**
     * Enters an authored .rfmap dungeon by name. Loads from data/dungeons/{name}_1.rfmap.
     */
    public void enterAuthoredDungeon(String dungeonName) {
        java.io.File file = new java.io.File("data/dungeons/" + dungeonName + "_1.rfmap");
        if (!file.exists()) {
            game.log("Dungeon not found: " + dungeonName, MessageLog.Type.DANGER);
            return;
        }

        try {
            MapData md = MapData.load(file);

            // Only commit to the descent once the first level has actually loaded
            leaveSurfaceForDungeon();

            // Set authored state BEFORE starting animation so it can detect scary mode
            game.getDungeonViewState().setAuthoredDungeonName(dungeonName);
            // Same island ladder as procedural dungeons; never fall back to the Island 1 view
            var authoredMode = modeForIsland(game.getCurrentOverworldName());
            if (authoredMode == io.cannonforge.retroquest.model.DungeonViewState.Mode.TOP_DOWN)
                authoredMode = io.cannonforge.retroquest.model.DungeonViewState.Mode.WIREFRAME;
            game.getDungeonViewState().setMode(authoredMode);

            SoundManager.getInstance().dungeonDescent();
            game.getGamePanel().startDungeonEntryAnimation();

            game.setInDungeon(true);
            game.setCurrentDepth(1);
            game.setCurrentMap(md.tiles);
            game.setCurrentDungeonMapData(md);

            game.getPlayer().setFacing(0);
            game.getPlayer().setPosition(md.interiorEntryX, md.interiorEntryY);

            revealVisibleArea();
            game.updateCamera();
            game.snapDungeonCamera();
            game.startDungeonViewLoop();
            game.getGamePanel().repaint();
            game.getStatsPanel().refresh();
            game.getPlayer().progressQuest(Quest.Type.EXPLORE, dungeonName + " Level 1", 1);
            game.getPlayer().progressQuest(Quest.Type.EXPLORE, "Dungeon Level 1", 1);
        } catch (Exception e) {
            game.log("Failed to load dungeon: " + e.getMessage(), MessageLog.Type.DANGER);
        }
    }

    public void exitDungeon() {
        SoundManager.getInstance().playLeaveTown();
        pendingExitX = lastDungeonEntryX;
        pendingExitY = lastDungeonEntryY;
        game.getGamePanel().startDungeonExitAnimation();
        game.startOverlayRepaintTimer();
    }

    /** Called by {@link DungeonExitAnimation} when its animation completes. */
    public void finishDungeonExit() {
        game.stopDungeonViewLoop();
        game.getDungeonViewState().setMode(
                io.cannonforge.retroquest.model.DungeonViewState.Mode.TOP_DOWN);
        game.getDungeonViewState().setAuthoredDungeonName(null);
        game.setCurrentDungeonMapData(null);
        game.setInDungeon(false);
        game.setCurrentDepth(0);

        // Entered from inside a town? The recorded entry position is a town-interior
        // coordinate, so return to that town rather than dropping it on the overworld.
        if (entryTownName != null) {
            Town town = new Town(entryTownName, entryTownDoorX, entryTownDoorY);
            game.setCurrentTown(town);
            game.setCurrentMap(town.getInteriorMap());
            game.getPlayer().setPosition(pendingExitX, pendingExitY);
            setEntryTown(null, 0, 0);
            game.log("You climb out of the dungeon, back into " + town.getDisplayName() + ".", MessageLog.Type.INFO);
            game.updateCamera();
            if (game.getGamePanel()  != null) game.getGamePanel().repaint();
            if (game.getStatsPanel() != null) game.getStatsPanel().refresh();
            return;
        }

        char[][] overworldMap = game.getOverworldManager().getCurrentMap();
        game.setCurrentMap(overworldMap);

        int exitX = pendingExitX;
        int exitY = pendingExitY;

        // Fallback: if exit coords are (0,0) — likely a save that lost the entry position —
        // scan the overworld for the 'D' tile and place the player there instead.
        if (exitX == 0 && exitY == 0 && overworldMap != null) {
            for (int sy = 0; sy < overworldMap.length; sy++) {
                for (int sx = 0; sx < overworldMap[sy].length; sx++) {
                    if (overworldMap[sy][sx] == 'D') {
                        exitX = sx;
                        exitY = sy;
                        break;
                    }
                }
                if (exitX != 0 || exitY != 0) break;
            }
        }

        game.getPlayer().setPosition(exitX, exitY);
        game.log("You climb out of the dungeon and return to the surface.", MessageLog.Type.INFO);
        game.updateCamera();
        if (game.getGamePanel()  != null) game.getGamePanel().repaint();
        if (game.getStatsPanel() != null) game.getStatsPanel().refresh();
    }

    /** Called by {@link DungeonEntryAnimation} when its animation completes. */
    public void finishDungeonEntry() {
        if (game.getGamePanel()  != null) game.getGamePanel().repaint();
        if (game.getStatsPanel() != null) game.getStatsPanel().refresh();
    }

    public void goUpDungeonLevel() {
        if (game.getCurrentDepth() <= 1) {
            game.log("You are already at the top of the dungeon.", MessageLog.Type.DANGER);
            return;
        }
        int newDepth = game.getCurrentDepth() - 1;

        if (game.getDungeonViewState().isAuthored()) {
            if (!loadAuthoredLevel(game.getDungeonViewState().getAuthoredDungeonName(), newDepth)) {
                game.log("There is nothing above...", MessageLog.Type.DIM);
                return;
            }
        } else {
            game.setCurrentDepth(newDepth);
            game.setCurrentMap(Dungeon.generate(newDepth));
            game.getPlayer().setPosition(game.getPlayer().getX(), game.getPlayer().getY());
        }

        revealVisibleArea();
        game.updateCamera();
        game.snapDungeonCamera();
        game.log("You climb the stairs... now on level " + game.getCurrentDepth(), MessageLog.Type.INFO);
        game.getGamePanel().repaint();
    }

    public void goDownDungeonLevel() {
        int maxDepth = getMaxDungeonDepth();
        if (!game.getDungeonViewState().isAuthored() && game.getCurrentDepth() >= maxDepth) {
            game.log("You have reached the deepest level...", MessageLog.Type.DANGER);
            return;
        }
        int newDepth = game.getCurrentDepth() + 1;

        if (game.getDungeonViewState().isAuthored()) {
            if (!loadAuthoredLevel(game.getDungeonViewState().getAuthoredDungeonName(), newDepth)) {
                game.log("There is nothing below...", MessageLog.Type.DIM);
                return;
            }
        } else {
            game.setCurrentDepth(newDepth);
            game.setCurrentMap(Dungeon.generate(newDepth));
            game.getPlayer().setPosition(game.getPlayer().getX(), game.getPlayer().getY());
        }

        revealVisibleArea();
        game.snapDungeonCamera();
        game.log("You descend deeper... level " + game.getCurrentDepth(), MessageLog.Type.INFO);
        game.updateCamera();
        game.getGamePanel().startDungeonDescentAnimation(game.getCurrentDepth());
        // Authored levels tick inside loadAuthoredLevel(); only the procedural branch needs it here.
        if (!game.getDungeonViewState().isAuthored()) {
            game.getPlayer().progressQuest(Quest.Type.EXPLORE, "Dungeon Level " + game.getCurrentDepth(), 1);
        }
    }

    /** Loads an authored dungeon level. Returns true on success. */
    private boolean loadAuthoredLevel(String name, int level) {
        java.io.File file = new java.io.File("data/dungeons/" + name + "_" + level + ".rfmap");
        if (!file.exists()) return false;
        try {
            MapData md = MapData.load(file);
            game.setCurrentDepth(level);
            game.setCurrentMap(md.tiles);
            game.setCurrentDungeonMapData(md);
            game.getPlayer().setPosition(md.interiorEntryX, md.interiorEntryY);

            // War Beneath: Bellorak war tunnels lore
            if ("war_beneath".equals(name)) {
                if (level == 1) {
                    game.log("You descend into tunnels dug by generations of soldiers. The walls are shored with splintered wood and bent iron.", MessageLog.Type.INFO);
                } else if (level == 2) {
                    game.log("The tunnels connect both factions. You find evidence of underground skirmishes \u2014 and mechanisms that ensure neither side ever wins.", MessageLog.Type.INFO);
                } else if (level == 3) {
                    game.log("An ancient forge room. The walls bear inscriptions older than the war: \"Before the crucible, there were only bonds.\"", MessageLog.Type.INFO);
                }
            }

            // Iron Pit: Arena underworks and Bellorak's trial
            if ("iron_pit".equals(name)) {
                if (level == 1) {
                    game.log("Beneath the Great Arena, iron corridors stretch into darkness. The roar of the crowd above vibrates through the ceiling.", MessageLog.Type.INFO);
                } else if (level == 2) {
                    game.log("Gladiator crypts line the walls. Names and kill counts are etched into every door. Some doors have been sealed for decades.", MessageLog.Type.INFO);
                } else if (level == 3) {
                    game.log("The sanctum of the arena. Bellorak\u2019s presence fills the chamber like heat from a forge.", MessageLog.Type.DANGER);
                    game.log("\"You have come far, warrior. Now prove yourself in the arena.\"", MessageLog.Type.INFO);
                    // Auto-give trial quest if not already active/complete
                    boolean trialActive = game.getPlayer().getActiveQuests().stream().anyMatch(q -> "bellorak_arena".equals(q.getId()));
                    boolean trialDone = game.getPlayer().getCompletedQuests().stream().anyMatch(q -> "bellorak_arena".equals(q.getId()));
                    if (!trialActive && !trialDone) {
                        Quest template = io.cannonforge.retroquest.registry.QuestRegistry.getById("bellorak_arena");
                        if (template != null) {
                            Quest instance = template.createInstance();
                            instance.setGiverName("Bellorak");
                            game.getPlayer().addQuest(instance);
                            game.log("Quest: " + instance.getTitle(), MessageLog.Type.INFO);
                        }
                    }
                }
            }

            // Cradle of Shards: endgame dungeon lore
            if ("cradle_of_shards".equals(name)) {
                if (level <= 7) {
                    String[] godNames = {"Lirandel", "Pyralis", "Zephyrion", "Sylvandar", "Thalorax", "Umbryn", "Bellorak"};
                    String godName = godNames[level - 1];
                    game.log("You pass through " + godName + "\u2019s gate. The walls shift to reflect their domain.", MessageLog.Type.INFO);
                } else if (level == 8) {
                    game.log("The Heart of the Cradle. All seven currents of divine energy converge here.", MessageLog.Type.DANGER);
                    game.log("The Scar on your chest burns. Aqualon\u2019s dormant consciousness stirs beneath you.", MessageLog.Type.INFO);
                }
            }

            // Pressure Temple: depth-specific Aqualon memory lore + Deep Reading quest
            if ("pressure_temple".equals(name)) {
                if (level == 1) {
                    game.log("The water thickens. Pressure wards hum on every surface.", MessageLog.Type.INFO);
                } else if (level == 2) {
                    game.log("Bioluminescent runes sear into your mind. You see: a formless warmth that became the world\u2019s core. Aqualon\u2019s first dream.", MessageLog.Type.INFO);
                    game.log("The joy of creation \u2014 Aqualon\u2019s delight in dreaming new things into being.", MessageLog.Type.INFO);
                    if (!game.getPlayer().hasFlag("pressure_temple_explored")) {
                        game.getPlayer().setFlag("pressure_temple_explored", "true");
                        game.getPlayer().progressQuest(Quest.Type.EXPLORE, "pressure_temple_inscriptions", 1);
                    }
                } else if (level == 3) {
                    game.log("The final inscription burns: the betrayal. Seven Dreams turning against their creator. The Shattering, felt from inside Aqualon\u2019s consciousness.", MessageLog.Type.DANGER);
                    game.log("You feel crushing grief. Not yours. The serpent\u2019s.", MessageLog.Type.INFO);
                    // Auto-give trial quest if not already active/complete
                    boolean trialActive = game.getPlayer().getActiveQuests().stream().anyMatch(q -> "thalorax_trial".equals(q.getId()));
                    boolean trialDone = game.getPlayer().getCompletedQuests().stream().anyMatch(q -> "thalorax_trial".equals(q.getId()));
                    if (!trialActive && !trialDone) {
                        Quest template = io.cannonforge.retroquest.registry.QuestRegistry.getById("thalorax_trial");
                        if (template != null) {
                            Quest instance = template.createInstance();
                            instance.setGiverName("Thalorax");
                            game.getPlayer().addQuest(instance);
                            game.log("A voice fills the pressure: \"Prove you can bear the weight of truth.\"", MessageLog.Type.INFO);
                            game.log("Quest: " + instance.getTitle(), MessageLog.Type.INFO);
                        }
                    }
                }
            }

            // Every authored level arrival ticks 'Dungeon Level N'. Chutes, teleporters and
            // the glowing cube reach this method directly rather than through
            // goDownDungeonLevel(), so hooking the descent alone let a chute that skips a
            // level leave an EXPLORE quest for that depth permanently unticked.
            game.getPlayer().progressQuest(Quest.Type.EXPLORE, "Dungeon Level " + level, 1);

            // A level change is never a walk, so the camera must not ease across it. Stairs
            // snapped in goDown/goUpDungeonLevel, but chutes, teleporters and the glowing cube
            // land here directly and glided the camera over the whole map instead.
            game.snapDungeonCamera();

            return true;
        } catch (Exception e) {
            game.log("Failed to load level: " + e.getMessage(), MessageLog.Type.DANGER);
            return false;
        }
    }

    public void handleStairs() {
        // Belt and braces: the key handler already refuses input while a monster is
        // arriving, but this is public and the question it raises is the one that ended up
        // buried under a wolf.
        if (game.getMessageLog().isPromptActive() || encounterPending) return;
        SoundManager.getInstance().playStep();
        int maxDepth = getMaxDungeonDepth();

        String[] options;
        if (game.getCurrentDepth() == 1) {
            options = new String[]{"Exit to Surface", "Go Down (to level 2)"};
        } else if (game.getCurrentDepth() >= maxDepth) {
            options = new String[]{"Go Up (to level " + (game.getCurrentDepth() - 1) + ")"};
        } else {
            options = new String[]{"Go Up", "Go Down"};
        }

        Runnable[] stairActions = new Runnable[options.length];
        for (int i = 0; i < options.length; i++) {
            final int idx = i;
            final String opt = options[i];
            if (game.getCurrentDepth() == 1 && idx == 0) {
                stairActions[idx] = this::exitDungeon;
            } else if (opt.contains("Up")) {
                stairActions[idx] = this::goUpDungeonLevel;
            } else {
                stairActions[idx] = this::goDownDungeonLevel;
            }
        }

        String[] optionsWithCancel = java.util.Arrays.copyOf(options, options.length + 1);
        optionsWithCancel[options.length] = "Cancel";
        Runnable[] actionsWithCancel = java.util.Arrays.copyOf(stairActions, stairActions.length + 1);
        actionsWithCancel[stairActions.length] = null;

        game.getMessageLog().prompt("You found a stairway. Which way?", optionsWithCancel, actionsWithCancel);
    }

    /** Returns the max dungeon depth for the current overworld (from MapData), defaulting to 50. */
    private int getMaxDungeonDepth() {
        MapData md = game.getOverworldManager().getMapData();
        return (md != null && md.maxDungeonDepth > 0) ? md.maxDungeonDepth : DEFAULT_MAX_DEPTH;
    }

    // ── Dungeon Specials ──────────────────────────────────────────────────────

    public void handleDungeonSpecial() {
        if (game.getMessageLog().isPromptActive()) return; // don't double-trigger
        if (encounterPending) return;                      // a fight is a step away
        boolean authored = game.getDungeonViewState().isAuthored();
        int px = authored ? game.getPlayer().getX() : game.getPlayer().getX() + 1;
        int py = authored ? game.getPlayer().getY() : game.getPlayer().getY() + 1;
        char special = DungeonWallQuery.getSpecial(px, py, game.getCurrentDepth(),
                game.getCurrentMap(), authored);
        handleDungeonSpecialAt(px, py, special);
    }

    /**
     * Fires the special feature at an explicit tile.
     *
     * <p>Normally that is the tile the player just stepped onto, but chutes ('c'), spinners
     * ('n') and memory pools ('M') are authored on tile ids that {@code data/tiles.json}
     * defines as solid furniture, so they can only ever be walked into — {@code Retroquest}
     * calls this from the bump path with the blocking tile's coordinates.
     */
    public void handleDungeonSpecialAt(int px, int py, char special) {
        if (game.getMessageLog().isPromptActive()) return; // don't double-trigger

        switch (special) {
            case 'P' -> handlePit();
            case 's' -> handleStairs();
            case 't' -> handleTeleporter();
            case 'A' -> handleAltar();
            case 'f' -> handleFountain();
            case 'g' -> handleGlowingCube();
            case 'H' -> handleThrone();
            case 'I' -> handleInn();
            case 'R' -> handlePuzzle();
            case 'n' -> handleSpinner();
            case 'c' -> handleChute(px, py);
            case 'q' -> handleRiddleDoor();
            case 'M' -> handleMemoryPool(px, py);
            case 'B' -> handleChest(px, py);
        }

        game.getStatsPanel().refresh();
    }

    // \u2500\u2500 Treasure Chest (authored 'B' tiles) \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500
    // The procedural dungeon rolls chests as a random ground-loot outcome; authored maps
    // place them as real tiles, which had no case in the dispatch. Same rewards and traps,
    // but a placed chest is looted once and stays open.

    private void handleChest(int px, int py) {
        String mapKey = game.currentMapKey();
        if (isFeatureExhausted(mapKey, px, py)) {
            game.log("The chest stands open and empty.", MessageLog.Type.DIM);
            return;
        }
        SoundManager.getInstance().playChestOpen();
        game.getMessageLog().prompt("A heavy chest sits here. Open it?",
            () -> {
                exhaustFeature(mapKey, px, py);
                openChestContents();
                game.getStatsPanel().refresh();
            }, null);
    }

    /**
     * Rolls a chest's trap and contents. Shared by the authored 'B' tiles and the
     * procedural ground-loot chest so both behave identically.
     */
    private void openChestContents() {
        if (Math.random() < CHEST_TRAP_CHANCE) {
            int intBonus = Math.max(0, game.getPlayer().getIntelligence() - 10);
            int gold = game.getCurrentDepth() * CHEST_TRAP_GOLD_DEPTH_SCALE + CHEST_TRAP_GOLD_BASE
                     + (int)(Math.random() * CHEST_TRAP_GOLD_RANDOM);
            if (Math.random() * 100 < intBonus * 4) {
                SoundManager.getInstance().play("spell");
                game.log("You notice poison needles in the lock \u2014 trap disarmed!", MessageLog.Type.GOOD);
                game.getPlayer().addGold(gold);
                game.log("Inside: " + gold + " gold.", MessageLog.Type.LOOT);
                return;
            }
            int dmg = CHEST_TRAP_DMG_BASE + game.getCurrentDepth() * CHEST_TRAP_DMG_PER_DEPTH;
            int dexBonus = Math.max(0, game.getPlayer().getDex() - 10);
            if (Math.random() * 100 < dexBonus * 3) {
                SoundManager.getInstance().playTrap();
                game.log("The chest was trapped! You leap back just in time \u2014 no damage!", MessageLog.Type.INFO);
                dmg = 0;
            } else {
                if (Math.random() * 100 < dexBonus * 5) dmg = Math.max(1, dmg / 2);
                game.getPlayer().takeDamage(dmg);
                SoundManager.getInstance().playTrap();
            }
            game.getPlayer().addGold(gold);
            if (dmg > 0 && !game.getPlayer().isDead())
                game.log("The chest was trapped! -" + dmg + " HP. Inside: " + gold + " gold.", MessageLog.Type.DANGER);
            if (game.getPlayer().isDead()) game.killPlayer("Killed by a trapped chest...");
            return;
        }
        Item loot = LootGenerator.getRandomLoot(game.getCurrentDepth(), game.getPlayer().getLevel());
        int gold  = game.getCurrentDepth() * CHEST_GOLD_DEPTH_SCALE + CHEST_GOLD_BASE
                  + (int)(Math.random() * (game.getCurrentDepth() * CHEST_GOLD_RANDOM_DEPTH + CHEST_GOLD_RANDOM_BASE));
        int strBonus = Math.max(0, game.getPlayer().getStr() - 10);
        int bonusGold = gold * strBonus * 5 / 100;
        gold += bonusGold;
        game.getPlayer().addGold(gold);
        String strMsg = bonusGold > 0 ? " You pry open a hidden compartment \u2014 +" + bonusGold + " extra gold!" : "";
        if (loot != null && game.getPlayer().addItemAndProgress(loot)) {
            game.log("The chest contains " + gold + " gold and a " + loot.getName() + "!" + strMsg, MessageLog.Type.LOOT);
        } else {
            game.log("The chest contains " + gold + " gold!" + strMsg
                    + (loot != null ? " (Inventory full \u2014 item left behind)" : ""), MessageLog.Type.LOOT);
        }
    }

    private void handlePit() {
        SoundManager.getInstance().playPitFall();
        int damage = PIT_DMG_PER_DEPTH * game.getCurrentDepth() + PIT_DMG_BASE;
        game.getPlayer().takeDamage(damage);
        game.log("You fall into a pit! -" + damage + " HP.", MessageLog.Type.DANGER);
        // A Resurrection Ward saves the fall — the player still lands on the level below
        if (game.getPlayer().isDead() && game.killPlayer("You died falling into a pit...")) return;
        goDownDungeonLevel();
    }

    // ── Teleporter ────────────────────────────────────────────────────────────
    // Asks first (like the original), then sends to a random level and position.

    private void handleTeleporter() {
        SoundManager.getInstance().playTeleportWhoosh();
        game.getMessageLog().prompt("A shimmering teleporter stands before you. Step in?",
            () -> {
                game.getGamePanel().startTeleportAnimation();
                if (game.getDungeonViewState().isAuthored()) {
                    // Stay inside this dungeon. Swapping in a generated level here used to
                    // leave the authored state pointing at a procedural map — a hybrid with
                    // no walls, because authored movement reads walkability from the tiles.
                    String name = game.getDungeonViewState().getAuthoredDungeonName();
                    int levels  = countAuthoredLevels(name);
                    int target  = (levels > 1) ? 1 + (int)(Math.random() * levels)
                                               : game.getCurrentDepth();
                    if (target != game.getCurrentDepth() && loadAuthoredLevel(name, target)) {
                        game.log("You are whisked away to dungeon level " + target + "!",
                                MessageLog.Type.INFO);
                    } else {
                        int[] pos = findRandomWalkablePosition();
                        game.getPlayer().setPosition(pos[0], pos[1]);
                        game.log("The teleporter deposits you somewhere else entirely.",
                                MessageLog.Type.INFO);
                    }
                } else {
                    int maxDepth = Math.max(1, getMaxDungeonDepth());
                    int newDepth = randomTeleportDepth(maxDepth);
                    game.setCurrentDepth(newDepth);
                    game.setCurrentMap(Dungeon.generate(newDepth));
                    int[] pos = findRandomWalkablePosition();
                    game.getPlayer().setPosition(pos[0], pos[1]);
                    game.log("You are whisked away to dungeon level " + newDepth + "!",
                            MessageLog.Type.INFO);
                    game.getPlayer().progressQuest(Quest.Type.EXPLORE, "Dungeon Level " + newDepth, 1);
                }
                revealVisibleArea();
                // Both branches move the player further than a step — even the same-level
                // reposition — so the camera must snap rather than ease across the map.
                game.snapDungeonCamera();
                game.updateCamera();
                game.getStatsPanel().refresh();
            }, null);
    }

    /** Number of consecutively-numbered levels an authored dungeon has on disk (at least 1). */
    private int countAuthoredLevels(String name) {
        if (name == null) return 1;
        int levels = 0;
        while (levels < MAX_LEVEL_RANGE
                && new java.io.File("data/dungeons/" + name + "_" + (levels + 1) + ".rfmap").exists()) {
            levels++;
        }
        return Math.max(1, levels);
    }

    // ── Spinner ───────────────────────────────────────────────────────────────
    // Silently rotates the player's facing. The player gets no warning — just
    // disorientation. A classic dungeon-crawler mechanic.

    private void handleSpinner() {
        int rotations = 1 + (int)(Math.random() * 3); // 1-3 quarter turns
        int newFacing = (game.getPlayer().getFacing() + rotations) & 3;
        game.getPlayer().setFacing(newFacing);
        // No message! That's the point — the player doesn't know they've been spun
        if (game.getGamePanel() != null) game.getGamePanel().repaint();
    }

    // ── Chute ─────────────────────────────────────────────────────────────────
    // Drops the player to a lower level. In authored dungeons, the target level
    // and position can be specified via tile state data.

    private void handleChute(int tileX, int tileY) {
        SoundManager.getInstance().playPitFall();
        int damage = PIT_DMG_BASE + game.getCurrentDepth() * PIT_DMG_PER_DEPTH / 2;
        game.getPlayer().takeDamage(damage);
        game.log("The floor gives way beneath you! -" + damage + " HP.", MessageLog.Type.DANGER);
        if (game.getPlayer().isDead()
                && game.killPlayer("You fell through a chute to your death...")) return;

        if (game.getDungeonViewState().isAuthored()) {
            // Authored: try to load the next level
            String name = game.getDungeonViewState().getAuthoredDungeonName();
            int targetLevel = game.getCurrentDepth() + 1;
            // Check the chute tile's own state for a custom target level
            MapData md = game.getCurrentDungeonMapData();
            if (md != null && md.initialTileStates != null) {
                TileState ts = md.initialTileStates.get(tileX + "," + tileY);
                if (ts != null && ts.data != null && ts.data.containsKey("chuteLevel")) {
                    try { targetLevel = Integer.parseInt(ts.data.get("chuteLevel")); }
                    catch (NumberFormatException ignored) {}
                }
            }
            if (loadAuthoredLevel(name, targetLevel)) {
                game.log("You tumble down to level " + targetLevel + "!", MessageLog.Type.INFO);
            } else {
                game.log("You land in a dead end... the chute seals behind you.", MessageLog.Type.DIM);
            }
        } else {
            goDownDungeonLevel();
        }

        revealVisibleArea();
        game.updateCamera();
        if (game.getGamePanel() != null) game.getGamePanel().repaint();
        game.getStatsPanel().refresh();
    }

    // ── Riddle Door ───────────────────────────────────────────────────────────
    // Blocks passage until the player answers a riddle. On correct answer, the
    // tile is replaced with floor and the answer persisted.

    /** Rootvault riddles — nature/growth/decay themed */
    private static final String[][] NATURE_RIDDLES = {
        {"I am not alive, yet I grow. I have no lungs, yet I breathe. I have no mouth, yet I drink.",
         "A tree", "Fire", "A river", "0"},
        {"The more you take from me, the larger I get.",
         "A hole", "A shadow", "A debt", "0"},
        {"I follow the sun but never see it. I die in autumn and am reborn in spring.",
         "A leaf", "A flower", "A shadow", "0"},
        {"I build without hands. I destroy without malice. I am patient beyond measure.",
         "Time", "Nature", "Water", "0"},
        {"What has roots that nobody sees, grows taller than trees, yet never blooms?",
         "A mountain", "The sky", "A river", "0"},
        {"I am the end of growth and the beginning of life. Without me, the forest suffocates.",
         "Decay", "Winter", "Fire", "0"},
    };

    /** Storm Spire riddles — wind/sky/storm themed */
    private static final String[][] STORM_RIDDLES = {
        {"I howl through the mountains but have no voice. I push ships across the sea but have no hands.",
         "Thunder", "The wind", "A ghost", "1"},
        {"I strike without warning, vanish without a trace. I am born between clouds and die touching earth.",
         "Lightning", "Rain", "A meteor", "0"},
        {"I can carry a feather across an ocean but cannot lift a stone from the ground.",
         "A breeze", "A bird", "The tide", "0"},
        {"The higher you climb, the more of me you lose. At the summit, I am nearly gone.",
         "Air", "Courage", "Weight", "0"},
        {"I am louder than any drum yet I am just air. I follow the flash but I am always late.",
         "Thunder", "An echo", "The wind", "0"},
        {"I build castles in the sky that vanish by noon. I weep without sadness and rage without anger.",
         "A cloud", "A dream", "The sea", "0"},
        {"I spin but have no wheel. I destroy but have no weapon. I am born from heat and die over water.",
         "A tornado", "A fire", "A wave", "0"},
        {"You cannot see me but you can see my work. You cannot hold me but you can feel my touch.",
         "The wind", "Time", "Gravity", "0"},
        {"I fall from the sky but I am not rain. I crackle and branch but I am not a tree.",
         "Lightning", "Hail", "Snow", "0"},
        {"I am the sky's breath, the sail's master, and the kite's only friend.",
         "The wind", "The sun", "A string", "0"},
    };

    /** War riddles — battle/honor/bonds themed (War Beneath, Iron Pit) */
    private static final String[][] WAR_RIDDLES = {
        {"I am forged in fire but broken by betrayal. I bind warriors but weigh nothing.",
         "An oath", "A sword", "A chain", "0"},
        {"I grow stronger with every wound but weaker with every victory.",
         "Resolve", "Armor", "Fear", "0"},
        {"Two enter as strangers, fight side by side, and leave as one. What bound them?",
         "Battle", "A chain", "Love", "0"},
        {"I am the space between shield and shield, between heartbeat and heartbeat.",
         "Trust", "Air", "Time", "0"},
        {"I am wielded by the brave and feared by the wise. I solve nothing but decide everything.",
         "A sword", "Courage", "War", "2"},
        {"The more soldiers I claim, the louder my silence. What am I?",
         "A graveyard", "A battle", "Glory", "0"},
        {"I am won not by the strongest but by the last one standing.",
         "Endurance", "A war", "A crown", "0"},
        {"Brothers share me before battle. Enemies share me after. What am I?",
         "Respect", "Blood", "Silence", "0"},
    };

    /** Cradle riddles — cosmic/dream/divine themed (Cradle of Shards) */
    private static final String[][] CRADLE_RIDDLES = {
        {"I dreamed the world into being. My children killed me in my sleep. What am I?",
         "Aqualon", "Hope", "The sea", "0"},
        {"Seven broke from one. One sleeps beneath all. What connects them still?",
         "The keys", "Memory", "The ocean", "0"},
        {"I am the eighth, consumed before the betrayal. My name is what the gods lack.",
         "Hope", "Love", "Memory", "0"},
        {"I am carried by the Unbound, given by no god, and older than all seven.",
         "The Scar", "The truth", "A key", "0"},
        {"What rises when the gods war, and falls when they are at peace?",
         "The ocean", "The wind", "The dead", "0"},
    };

    /** Shadow riddles — memory/grief/silence themed (Archive of Tears, Forgotten City) */
    private static final String[][] SHADOW_RIDDLES = {
        {"What is heavier than stone but carried by the willing?",
         "Grief", "Gold", "A mountain", "0"},
        {"I am lost the moment I am spoken. I am kept only in silence.",
         "A secret", "A breath", "A name", "0"},
        {"I grow stronger the more you share me, yet weaker the more you keep me.",
         "A memory", "A flame", "A song", "0"},
        {"I can fill a room yet take up no space. I can break a heart yet leave no mark.",
         "Silence", "Darkness", "Cold", "0"},
        {"The dead carry me and the living fear me. I am lighter than air yet no one can escape me.",
         "A memory", "A shadow", "A curse", "0"},
    };

    private void handleRiddleDoor() {
        boolean authored = game.getDungeonViewState().isAuthored();
        int px = authored ? game.getPlayer().getX() : game.getPlayer().getX() + 1;
        int py = authored ? game.getPlayer().getY() : game.getPlayer().getY() + 1;
        String mapKey = game.currentMapKey();

        if (isFeatureExhausted(mapKey, px, py)) {
            game.log("The riddle door stands open.", MessageLog.Type.DIM);
            return;
        }

        // Pick a riddle based on position hash (deterministic per tile)
        String dGroup = game.getDungeonViewState().getAuthoredDungeonName();
        String[][] riddles = "rootvault".equals(dGroup) ? NATURE_RIDDLES
                : ("archive_of_tears".equals(dGroup) || "forgotten_city".equals(dGroup))
                ? SHADOW_RIDDLES
                : ("war_beneath".equals(dGroup) || "iron_pit".equals(dGroup))
                ? WAR_RIDDLES
                : "cradle_of_shards".equals(dGroup) ? CRADLE_RIDDLES
                : STORM_RIDDLES;
        int riddleIdx = Math.abs((px * 31 + py * 17 + game.getCurrentDepth() * 7)) % riddles.length;
        String[] riddle = riddles[riddleIdx];
        String question = riddle[0];
        String[] answers = {riddle[1], riddle[2], riddle[3]};
        int correctIdx = Integer.parseInt(riddle[4]);

        game.getMessageLog().prompt(
            "A door etched with glowing runes blocks your path.\n\"" + question + "\"",
            answers,
            new Runnable[] {
                () -> resolveRiddleDoor(mapKey, px, py, correctIdx == 0),
                () -> resolveRiddleDoor(mapKey, px, py, correctIdx == 1),
                () -> resolveRiddleDoor(mapKey, px, py, correctIdx == 2),
            });
    }

    private void resolveRiddleDoor(String mapKey, int px, int py, boolean correct) {
        if (correct) {
            exhaustFeature(mapKey, px, py);
            int xp = 50 + game.getCurrentDepth() * 20;
            var lvl = game.getPlayer().addXP(xp);
            SoundManager.getInstance().playStep();
            game.log("The runes flash and the door swings open! +" + xp + " XP.", MessageLog.Type.GOOD);
            game.logLevelUp(lvl);
        } else {
            int dmg = 5 + game.getCurrentDepth() * 3;
            game.getPlayer().takeDamage(dmg);
            SoundManager.getInstance().playTrap();
            game.log("Wrong! The runes crackle with energy. -" + dmg + " HP.", MessageLog.Type.DANGER);
            if (game.getPlayer().isDead()) {
                game.killPlayer("Struck down by the riddle door's ward...");
            }
        }
        game.getStatsPanel().refresh();
    }

    // ── Altar ─────────────────────────────────────────────────────────────────
    // Depths 1-3: Corrupted Shrines for the Dreamwake Caverns quest.
    // Depth 3: Corrupted Moon Guardian boss fight.
    // All other depths: standard altar with random divine favour.

    private void handleAltar() {
        SoundManager.getInstance().playAltarChime();

        // Same tile convention as every other special: authored maps are 0-based, the
        // procedural dungeon is 1-based. Mixing the two let one feature spend another's
        // "used" flag on the neighbouring tile.
        boolean authored = game.getDungeonViewState().isAuthored();
        int px = authored ? game.getPlayer().getX() : game.getPlayer().getX() + 1;
        int py = authored ? game.getPlayer().getY() : game.getPlayer().getY() + 1;
        String mapKey = game.currentMapKey();

        // Pressure Temple trial altar (Island 5 — Thalorax)
        if (game.getDungeonViewState().isAuthored()
                && "pressure_temple".equals(game.getDungeonViewState().getAuthoredDungeonName())
                && game.getCurrentDepth() == 3) {
            handlePressureTempleTrial();
            return;
        }

        // Archive of Tears trial altar (Island 6 — Umbryn)
        if (game.getDungeonViewState().isAuthored()
                && "archive_of_tears".equals(game.getDungeonViewState().getAuthoredDungeonName())
                && game.getCurrentDepth() == 3) {
            handleArchiveOfTearsTrial();
            return;
        }

        // Iron Pit trial altar (Island 7 — Bellorak)
        if (game.getDungeonViewState().isAuthored()
                && "iron_pit".equals(game.getDungeonViewState().getAuthoredDungeonName())
                && game.getCurrentDepth() == 3) {
            handleBellorakArenaTrial();
            return;
        }

        // Cradle of Shards trial altar (Endgame) — start cinematic sequence
        if (game.getDungeonViewState().isAuthored()
                && "cradle_of_shards".equals(game.getDungeonViewState().getAuthoredDungeonName())
                && game.getCurrentDepth() == 8) {
            if (game.getPlayer().hasFlag("game_complete")) {
                game.log("The Heart is still. The choice has been made.", MessageLog.Type.DIM);
                return;
            }
            game.getGamePanel().startEndgameCinematic();
            return;
        }

        // Bottom-of-dungeon bosses for the seven authored dungeons that had no climax
        // (data/dungeon_bosses.json). Checked after the four hand-written trials above, which
        // own their own altars, so a dungeon can never be claimed by both.
        if (game.getDungeonViewState().isAuthored()) {
            DungeonBoss boss = DungeonBossRegistry.find(
                    game.getDungeonViewState().getAuthoredDungeonName(), game.getCurrentDepth());
            if (boss != null) {
                handleDungeonBoss(boss);
                return;
            }
        }

        // Corrupted Shrines belong to the Dreamwake Caverns — the procedural dungeon under
        // Island 1. Gating on depth alone ran the shrine (and handed out the Key of Tides)
        // from any dungeon in the game whose altar happened to sit on levels 1-3.
        if (isDreamwakeCaverns() && game.getCurrentDepth() >= 1 && game.getCurrentDepth() <= 3) {
            if (isFeatureExhausted(mapKey, px, py)) {
                game.log("The shrine's corruption has already been dealt with.", MessageLog.Type.DIM);
                return;
            }
            handleCorruptedShrine();
            exhaustFeature(mapKey, px, py);
            return;
        }

        if (isFeatureExhausted(mapKey, px, py)) {
            game.log("The altar's divine power has been spent.", MessageLog.Type.DIM);
            return;
        }

        // Moonblessed: Lirandel heals the player freely at sacred altars
        if (game.getPlayer().hasBoon(Boon.MOONBLESSED)) {
            int healed = game.getPlayer().getMaxHp() - game.getPlayer().getHp();
            game.getPlayer().heal(game.getPlayer().getMaxHp());
            game.getPlayer().refreshSpellSlots();
            game.log("Lirandel's grace washes over you. +" + healed + " HP restored. Spells refreshed.",
                    MessageLog.Type.GOOD);
            game.getStatsPanel().refresh();
            exhaustFeature(mapKey, px, py);
            return;
        }

        game.getMessageLog().prompt("You stand before a sacred altar. Pray?",
            () -> {
                int roll = (int)(Math.random() * 100);
                if (roll < ALTAR_HEAL_CHANCE) {
                    int healed = game.getPlayer().getMaxHp() - game.getPlayer().getHp();
                    game.getPlayer().heal(game.getPlayer().getMaxHp());
                    game.getPlayer().refreshSpellSlots();
                    game.log("The gods answer your prayer! +" + healed + " HP restored. Spells refreshed.",
                            MessageLog.Type.GOOD);
                } else if (roll < ALTAR_XP_CHANCE) {
                    int xp = ALTAR_XP_BASE + game.getCurrentDepth() * ALTAR_XP_PER_DEPTH;
                    var lvl = game.getPlayer().addXP(xp);
                    game.log("Your devotion is rewarded with +" + xp + " experience.", MessageLog.Type.GOOD);
                    game.logLevelUp(lvl);
                } else if (roll < ALTAR_SILENT_CHANCE) {
                    game.log("The altar remains silent.", MessageLog.Type.DIM);
                } else if (roll < ALTAR_TITHE_CHANCE) {
                    int tithe = Math.min(game.getPlayer().getGold(),
                            ALTAR_TITHE_BASE + (int)(Math.random() * ALTAR_TITHE_RANDOM));
                    game.getPlayer().addGold(-tithe);
                    game.log("The gods demand a tithe of " + tithe + " gold!", MessageLog.Type.DANGER);
                } else if (roll < ALTAR_SPELL_SLOT_CHANCE) {
                    game.getPlayer().addBonusCastSlot();
                    game.log("The altar glows with arcane light — your magical reserves are bolstered!", MessageLog.Type.GOOD);
                } else {
                    int dmg = ALTAR_WRATH_DMG_BASE + game.getCurrentDepth() * ALTAR_WRATH_DMG_PER_DEPTH;
                    game.getPlayer().takeDamage(dmg);
                    game.log("The gods are displeased! You take " + dmg + " damage!", MessageLog.Type.DANGER);
                    if (game.getPlayer().isDead()) game.killPlayer("Struck down at the altar...");
                }
                exhaustFeature(mapKey, px, py);
                game.getStatsPanel().refresh();
            }, null);
    }

    /**
     * True only in the Dreamwake Caverns: the procedural dungeon reached from Lirandel,
     * the one island with no authored dungeon of its own.
     */
    private boolean isDreamwakeCaverns() {
        return !game.getDungeonViewState().isAuthored()
                && "lirandel".equals(game.getCurrentOverworldName());
    }

    // ── Corrupted Shrine (Dreamwake Caverns, depths 1-3) ─────────────────────

    private void handleCorruptedShrine() {
        if (game.getCurrentDepth() == 3) {
            // Depth 3: Boss encounter
            game.getMessageLog().prompt(
                "A Corrupted Shrine pulses with dark energy. The Corrupted Moon Guardian rises!",
                () -> {
                    Monster boss = new Monster("Corrupted Moon Guardian", SHRINE_BOSS_LEVEL,
                            SHRINE_BOSS_HP, SHRINE_BOSS_ATK, SHRINE_BOSS_GOLD,
                            SHRINE_BOSS_XP, SHRINE_BOSS_AC);
                    game.getGamePanel().startCombat(boss, (won, gold, loot, leveled, newLevel) -> {
                        if (won) {
                            game.getPlayer().progressQuest(Quest.Type.EXPLORE, "corrupted_shrine", 1);
                            game.getMessageLog().prompt(
                                "The Guardian falls! A shard of divine power floats before you.\nDestroy it, or absorb its corruption?",
                                new String[]{"Destroy it (pure path)", "Absorb its power (corruption)"},
                                new Runnable[]{
                                    () -> {
                                        game.getPlayer().addKey("key_of_tides");
                                        game.getPlayer().heal(game.getPlayer().getMaxHp());
                                        game.getPlayer().addFavor(God.LIRANDEL, 20);
                                        if (game.getPlayer().grantBoon(Boon.MOONBLESSED)) {
                                            game.log("Lirandel's light fills you. You are Moonblessed! (+10 max HP, free healing at sacred altars)", MessageLog.Type.GOOD);
                                        }
                                        game.log("The shard dissolves in holy light. You feel cleansed. Key of Tides received!", MessageLog.Type.GOOD);
                                        game.log("Lirandel is pleased with your purity. (+20 Lirandel favor)", MessageLog.Type.GOOD);
                                        game.getStatsPanel().refresh();
                                    },
                                    () -> {
                                        game.getPlayer().addKey("key_of_tides");
                                        Item shard = ItemRegistry.getById("shard_of_corruption");
                                        if (shard != null) game.getPlayer().addItemAndProgress(shard);
                                        // Emberpriest Cael on Pyralis has a whole branch gated on this flag
                                        // that nothing ever set, so taking the dark bargain here never
                                        // showed up in the conversation it was written for.
                                        game.getPlayer().setFlag("has_shard_of_corruption", "true");
                                        game.getPlayer().addFavor(God.PYRALIS, 5);
                                        if (game.getPlayer().grantBoon(Boon.PYRALIS_TEMPER)) {
                                            game.log("Pyralis's fire ignites within you. (+2 attack damage permanently)", MessageLog.Type.DANGER);
                                        }
                                        game.log("Dark power floods your veins. Key of Tides received. A dark seed takes root...", MessageLog.Type.DANGER);
                                        game.log("Pyralis stirs with interest. (+5 Pyralis favor)", MessageLog.Type.INFO);
                                        game.getStatsPanel().refresh();
                                    }
                                });
                        }
                        if (game.getGamePanel() != null) game.getGamePanel().repaint();
                    });
                }, null);
        } else {
            // Depths 1-2: Randomized corrupted shrine encounters
            game.getMessageLog().prompt(
                "A Corrupted Shrine pulses with sickly energy. Approach it?",
                () -> {
                    game.getPlayer().progressQuest(Quest.Type.EXPLORE, "corrupted_shrine", 1);
                    Player p = game.getPlayer();
                    int roll = (int)(Math.random() * 100);
                    int depth = game.getCurrentDepth();

                    if (roll < 20) {
                        // Purifying blast — XP + heal
                        int xp = SHRINE_XP_BASE + depth * SHRINE_XP_PER_DEPTH;
                        var lvl = p.addXP(xp);
                        int healed = Math.min(p.getMaxHp() - p.getHp(), 15 + depth * 5);
                        p.heal(healed);
                        game.log("The shrine shatters in a flash of moonlight! +" + xp + " XP, +" + healed + " HP restored.", MessageLog.Type.GOOD);
                        game.logLevelUp(lvl);
                    } else if (roll < 35) {
                        // Corruption lashes out — damage + XP
                        int dmg = 5 + depth * 3;
                        p.takeDamage(dmg);
                        int xp = (int)((SHRINE_XP_BASE + depth * SHRINE_XP_PER_DEPTH) * 1.5);
                        var lvl = p.addXP(xp);
                        game.log("Dark energy lashes out! You take " + dmg + " damage but absorb +" + xp + " XP from the shrine's destruction.", MessageLog.Type.DANGER);
                        game.logLevelUp(lvl);
                        if (p.isDead()) game.killPlayer("Consumed by corruption...");
                    } else if (roll < 50) {
                        // Gold cache hidden inside
                        int gold = 10 + depth * 8 + (int)(Math.random() * 15);
                        p.addGold(gold);
                        game.log("The shrine crumbles, revealing a cache of " + gold + " gold hidden within!", MessageLog.Type.LOOT);
                    } else if (roll < 62) {
                        // Spawn a corrupted guardian
                        int monsterHp = 20 + depth * 10;
                        int monsterAtk = 4 + depth * 2;
                        Monster guardian = new Monster("Corrupted Shrine Guardian", monsterHp, monsterAtk, 8 + depth * 4);
                        game.log("A guardian materializes from the shrine's corruption!", MessageLog.Type.DANGER);
                        game.getGamePanel().startCombat(guardian, (won, gold, loot, leveled, newLevel) -> {
                            if (won) {
                                int xp = SHRINE_XP_BASE + depth * SHRINE_XP_PER_DEPTH + 20;
                                var lvl = p.addXP(xp);
                                game.log("The guardian dissolves. +" + xp + " XP.", MessageLog.Type.GOOD);
                                game.logLevelUp(lvl);
                            }
                            game.getStatsPanel().refresh();
                            if (game.getGamePanel() != null) game.getGamePanel().repaint();
                        });
                        return; // combat handles stats refresh
                    } else if (roll < 74) {
                        // Spell slots refreshed
                        p.refreshSpellSlots();
                        int xp = SHRINE_XP_BASE + depth * SHRINE_XP_PER_DEPTH / 2;
                        var lvl = p.addXP(xp);
                        game.log("The corruption unravels and arcane energy floods in! Spell slots refreshed. +" + xp + " XP.", MessageLog.Type.GOOD);
                        game.logLevelUp(lvl);
                    } else if (roll < 84) {
                        // Favor gain — Lirandel rewards the purge
                        int favor = 3 + depth * 2;
                        p.addFavor(God.LIRANDEL, favor);
                        int xp = SHRINE_XP_BASE + depth * SHRINE_XP_PER_DEPTH;
                        var lvl = p.addXP(xp);
                        game.log("Lirandel's light floods the chamber as the corruption recedes. +" + favor + " Lirandel favor, +" + xp + " XP.", MessageLog.Type.GOOD);
                        game.logLevelUp(lvl);
                    } else if (roll < 92) {
                        // Cursed — take damage and lose some gold
                        int dmg = 3 + depth * 2;
                        int goldLost = Math.min(p.getGold(), 5 + (int)(Math.random() * 10));
                        p.takeDamage(dmg);
                        p.addGold(-goldLost);
                        game.log("The shrine explodes in a burst of dark energy! You take " + dmg + " damage and lose " + goldLost + " gold!", MessageLog.Type.DANGER);
                        if (p.isDead()) game.killPlayer("Consumed by corruption...");
                    } else {
                        // Nothing happens — eerie silence
                        game.log("The shrine crumbles to dust. Nothing happens... but the silence feels wrong.", MessageLog.Type.DIM);
                    }
                    game.getStatsPanel().refresh();
                }, null);
        }
    }

    // ── Pressure Temple Trial (Thalorax, Island 5) ────────────────────────────

    private static final int PRESSURE_TRIAL_BOSS_HP   = 400;
    private static final int PRESSURE_TRIAL_BOSS_ATK  = 22;
    private static final int PRESSURE_TRIAL_BOSS_GOLD = 120;

    /**
     * Called when the player activates the Pressure Temple trial altar.
     * A boss fight followed by a four-way philosophical choice that determines
     * favor awards and whether the player receives the Tidal Endurance boon.
     */
    public void handlePressureTempleTrial() {
        if (game.getPlayer().hasFlag("thalorax_trial_complete")) {
            game.log("The altar is silent. Thalorax has already rendered his judgment.", MessageLog.Type.DIM);
            return;
        }

        game.getMessageLog().prompt(
            "The water around the altar compresses to glass. A voice echoes from the trench:\n\"You seek depth. Prove you can bear it.\"",
            () -> {
                Monster boss = new Monster("Abyssal Leviathan",
                        PRESSURE_TRIAL_BOSS_HP, PRESSURE_TRIAL_BOSS_ATK, PRESSURE_TRIAL_BOSS_GOLD);
                game.getGamePanel().startCombat(boss, (won, gold, loot, leveled, newLevel) -> {
                    if (won) {
                        game.getPlayer().progressQuest(Quest.Type.KILL, "thalorax_trial", 1);
                        game.getMessageLog().prompt(
                            "The Leviathan dissolves into pressure and silence. Thalorax speaks:\n\"Now. What should become of the gods?\"",
                            new String[]{
                                "One god should rule all.",
                                "Destroy all gods — mortals need no masters.",
                                "Wake Aqualon. Let the old world return.",
                                "I refuse to choose."
                            },
                            new Runnable[]{
                                // Choice 1: One god rules — +10 Thalorax
                                () -> {
                                    game.getPlayer().addFavor(God.THALORAX, 10);
                                    game.getPlayer().setFlag("thalorax_trial_complete", "one_god");
                                    game.getPlayer().addKey("key_of_depths");
                                    game.log("\"A tyrant's answer. Efficient. I approve.\" Key of Depths received!", MessageLog.Type.GOOD);
                                    game.log("+10 Thalorax favor.", MessageLog.Type.INFO);
                                    maybeGrantTidalEndurance();
                                    game.getStatsPanel().refresh();
                                },
                                // Choice 2: Destroy all gods — +15 Thalorax, +5 Lirandel
                                () -> {
                                    game.getPlayer().addFavor(God.THALORAX, 15);
                                    game.getPlayer().addFavor(God.LIRANDEL, 5);
                                    game.getPlayer().setFlag("thalorax_trial_complete", "destroy_gods");
                                    game.getPlayer().addKey("key_of_depths");
                                    game.log("\"Bold. Even Lirandel respects that courage.\" Key of Depths received!", MessageLog.Type.GOOD);
                                    game.log("+15 Thalorax favor, +5 Lirandel favor.", MessageLog.Type.INFO);
                                    maybeGrantTidalEndurance();
                                    game.getStatsPanel().refresh();
                                },
                                // Choice 3: Aqualon wakes — +5 Thalorax (he fears this)
                                () -> {
                                    game.getPlayer().addFavor(God.THALORAX, 5);
                                    game.getPlayer().setFlag("thalorax_trial_complete", "aqualon_wakes");
                                    game.getPlayer().addKey("key_of_depths");
                                    Item shard = ItemRegistry.getById("shard_of_corruption");
                                    if (shard != null) game.getPlayer().addItemAndProgress(shard);
                                    game.log("\"...You would wake the Sleeper? The pressure shifts uneasily.\" Key of Depths received. A dark shard clings to your hand.", MessageLog.Type.DANGER);
                                    game.log("+5 Thalorax favor.", MessageLog.Type.INFO);
                                    game.getStatsPanel().refresh();
                                },
                                // Choice 4: Refuse to choose — +20 Thalorax (highest)
                                () -> {
                                    game.getPlayer().addFavor(God.THALORAX, 20);
                                    game.getPlayer().setFlag("thalorax_trial_complete", "refused");
                                    game.getPlayer().addKey("key_of_depths");
                                    game.log("\"Silence. The deepest answer. You understand pressure.\" Key of Depths received!", MessageLog.Type.GOOD);
                                    game.log("+20 Thalorax favor.", MessageLog.Type.INFO);
                                    maybeGrantTidalEndurance();
                                    game.getStatsPanel().refresh();
                                }
                            });
                    }
                    if (game.getGamePanel() != null) game.getGamePanel().repaint();
                });
            }, null);
    }

    /** Grants the Tidal Endurance boon if the player doesn't already have it. */
    private void maybeGrantTidalEndurance() {
        if (game.getPlayer().grantBoon(Boon.TIDAL_ENDURANCE)) {
            game.log("The crushing deep hardens your will. Tidal Endurance gained! (+5% spell resist)", MessageLog.Type.GOOD);
        }
    }

    // ── Archive of Tears Trial (Umbryn, Island 6) ────────────────────────────

    private static final int UMBRYN_TRIAL_BOSS_HP   = 500;
    private static final int UMBRYN_TRIAL_BOSS_ATK  = 24;
    private static final int UMBRYN_TRIAL_BOSS_GOLD = 140;

    /**
     * Called when the player activates the Archive of Tears trial altar.
     * A boss fight followed by a three-way choice about memory and grief.
     */
    public void handleArchiveOfTearsTrial() {
        if (game.getPlayer().hasFlag("umbryn_trial_complete")) {
            game.log("The sanctum is silent. Umbryn has already spoken.", MessageLog.Type.DIM);
            return;
        }

        game.getMessageLog().prompt(
            "Silver light pools around the altar. A voice like dust settling speaks:\n\"You have reached the heart of memory. Now you must decide what is worth keeping.\"",
            () -> {
                Monster boss = new Monster("Grief Incarnate",
                        UMBRYN_TRIAL_BOSS_HP, UMBRYN_TRIAL_BOSS_ATK, UMBRYN_TRIAL_BOSS_GOLD);
                game.getGamePanel().startCombat(boss, (won, gold, loot, leveled, newLevel) -> {
                    if (won) {
                        game.getPlayer().progressQuest(Quest.Type.KILL, "Grief Incarnate", 1);
                        game.getMessageLog().prompt(
                            "Grief dissolves into silver motes. Umbryn materializes \u2014 a figure of shadow and starlight.\n\"I have held every memory since the Shattering. The weight is unbearable. I am considering... forgetting. What would you have me do?\"",
                            new String[]{
                                "Remember with me. I will share the burden.",
                                "Let yourself forget. You deserve peace.",
                                "You must endure. The memories are too important."
                            },
                            new Runnable[]{
                                // Choice 1: Share the burden — +20 Umbryn, boon, -5 HP
                                () -> {
                                    game.getPlayer().addFavor(God.UMBRYN, 20);
                                    game.getPlayer().setFlag("umbryn_trial_complete", "shared_burden");
                                    game.getPlayer().setFlag("umbryn_grief", "true");
                                    game.getPlayer().addKey("key_of_echoes");
                                    if (game.getPlayer().grantBoon(Boon.UMBRYN_MEMORY)) {
                                        game.log("Umbryn's Memory gained! (+3 damage, +5% spell resist)", MessageLog.Type.GOOD);
                                    }
                                    game.log("\"You take my hand. Millennia of grief flow into you.\" Key of Echoes received!", MessageLog.Type.GOOD);
                                    game.log("+20 Umbryn favor. Your max HP is permanently reduced by 5, but you gain knowledge of the gods' weaknesses.", MessageLog.Type.INFO);
                                    game.getStatsPanel().refresh();
                                },
                                // Choice 2: Let Umbryn forget — +10 Umbryn, shard of corruption
                                () -> {
                                    game.getPlayer().addFavor(God.UMBRYN, 10);
                                    game.getPlayer().setFlag("umbryn_trial_complete", "let_forget");
                                    game.getPlayer().addKey("key_of_echoes");
                                    Item shard = ItemRegistry.getById("shard_of_corruption");
                                    if (shard != null) game.getPlayer().addItemAndProgress(shard);
                                    game.log("\"Umbryn exhales. The archive trembles. Memories scatter like silver dust.\" Key of Echoes received. A dark shard materializes.", MessageLog.Type.DANGER);
                                    game.log("+10 Umbryn favor. Shard of Corruption acquired.", MessageLog.Type.INFO);
                                    game.getStatsPanel().refresh();
                                },
                                // Choice 3: Must endure — +5 Umbryn
                                () -> {
                                    game.getPlayer().addFavor(God.UMBRYN, 5);
                                    game.getPlayer().setFlag("umbryn_trial_complete", "must_endure");
                                    game.getPlayer().addKey("key_of_echoes");
                                    game.log("\"Easy for you to say. You do not carry what I carry.\" But he nods. Key of Echoes received.", MessageLog.Type.GOOD);
                                    game.log("+5 Umbryn favor.", MessageLog.Type.INFO);
                                    game.getStatsPanel().refresh();
                                }
                            });
                    }
                    if (game.getGamePanel() != null) game.getGamePanel().repaint();
                });
            }, null);
    }

    // ── Bellorak Arena Trial (Island 7) ──────────────────────────────────────

    private static final int BELLORAK_TRIAL_BOSS_HP   = 600;
    private static final int BELLORAK_TRIAL_BOSS_ATK  = 30;
    private static final int BELLORAK_TRIAL_BOSS_GOLD = 160;

    /**
     * Called when the player activates the Iron Pit trial altar.
     * A boss fight against Seraphine followed by a three-way moral choice.
     */
    public void handleBellorakArenaTrial() {
        if (game.getPlayer().hasFlag("bellorak_trial_complete")) {
            game.log("The arena is silent. Bellorak has rendered his judgment.", MessageLog.Type.DIM);
            return;
        }

        game.getMessageLog().prompt(
            "Iron sparks cascade from the ceiling. Bellorak\u2019s voice booms:\n\"You have earned the right to stand in my arena. Now face my champion \u2014 the one who has fought here for thirty years.\"",
            () -> {
                Monster boss = new Monster("Seraphine, Undying Champion",
                        BELLORAK_TRIAL_BOSS_HP, BELLORAK_TRIAL_BOSS_ATK, BELLORAK_TRIAL_BOSS_GOLD);
                game.getGamePanel().startCombat(boss, (won, gold, loot, leveled, newLevel) -> {
                    if (won) {
                        game.getPlayer().progressQuest(Quest.Type.KILL, "Seraphine, Undying Champion", 1);
                        game.getMessageLog().prompt(
                            "Seraphine collapses. Her eyes clear for a moment \u2014 lucid, desperate.\n\"The keys... carry the dreams... End it... please...\"\nBellorak speaks: \"A worthy fight. Now choose her fate.\"",
                            new String[]{
                                "Grant her peace. Let her die, truly and finally.",
                                "Absorb her power. Take what she has gathered.",
                                "Free her. Purge the corruption without destroying her."
                            },
                            new Runnable[]{
                                // Choice 1: Grant peace — +20 Bellorak, boon, key
                                () -> {
                                    game.getPlayer().addFavor(God.BELLORAK, 20);
                                    game.getPlayer().setFlag("bellorak_trial_complete", "grant_peace");
                                    game.getPlayer().addKey("key_of_iron");
                                    game.log("Seraphine sighs. \"Thank you.\" Her form dissolves into light. Key of Iron received!", MessageLog.Type.GOOD);
                                    game.log("Bellorak: \"A warrior\u2019s death. I respect that.\"", MessageLog.Type.INFO);
                                    game.log("+20 Bellorak favor.", MessageLog.Type.INFO);
                                    maybeGrantIronBrotherhood();
                                    game.getStatsPanel().refresh();
                                },
                                // Choice 2: Absorb power (corruption path) — +5 Bellorak, key + shard
                                () -> {
                                    game.getPlayer().addFavor(God.BELLORAK, 5);
                                    game.getPlayer().setFlag("bellorak_trial_complete", "absorb_power");
                                    game.getPlayer().addKey("key_of_iron");
                                    Item shard = ItemRegistry.getById("shard_of_corruption_bellorak");
                                    if (shard != null) game.getPlayer().addItemAndProgress(shard);
                                    game.log("You reach into Seraphine\u2019s fading form and rip the arena\u2019s power into yourself. She screams \u2014 and is consumed.", MessageLog.Type.DANGER);
                                    game.log("Key of Iron received. Shard of Corruption acquired.", MessageLog.Type.INFO);
                                    game.log("Bellorak laughs. \"Bold. Ruthless. You will make a fine champion.\"", MessageLog.Type.INFO);
                                    game.log("+5 Bellorak favor.", MessageLog.Type.INFO);
                                    game.getStatsPanel().refresh();
                                },
                                // Choice 3: Free her — +15 Bellorak, boon, key, seraphine_freed flag
                                () -> {
                                    game.getPlayer().addFavor(God.BELLORAK, 15);
                                    game.getPlayer().setFlag("bellorak_trial_complete", "free_her");
                                    game.getPlayer().setFlag("seraphine_freed", "true");
                                    game.getPlayer().addKey("key_of_iron");
                                    game.log("You reach out \u2014 not to take, but to give. Using the echo of Umbryn\u2019s archive, you unravel the corruption thread by thread.", MessageLog.Type.GOOD);
                                    game.log("Seraphine gasps. Color returns to her face. She is mortal again \u2014 weakened, but free. Key of Iron received!", MessageLog.Type.GOOD);
                                    game.log("Bellorak is silent for a long moment. \"...I had forgotten what that looked like. A bond that doesn\u2019t require blood.\"", MessageLog.Type.INFO);
                                    game.log("+15 Bellorak favor.", MessageLog.Type.INFO);
                                    maybeGrantIronBrotherhood();
                                    game.getStatsPanel().refresh();
                                }
                            });
                    }
                    if (game.getGamePanel() != null) game.getGamePanel().repaint();
                });
            }, null);
    }

    /** Grants the Iron Brotherhood boon if the player doesn't already have it. */
    private void maybeGrantIronBrotherhood() {
        if (game.getPlayer().grantBoon(Boon.IRON_BROTHERHOOD)) {
            game.log("Bellorak\u2019s bond forged in iron. Iron Brotherhood gained! (+3 damage, +1 AC)", MessageLog.Type.GOOD);
        }
    }

    // ── Bottom-of-dungeon bosses (data/dungeon_bosses.json) ────────────────────
    //
    // Four dungeons already ended in a trial; seven ended in a room. These are those seven.
    // They are deliberately not four more copies of the trial methods below: a trial ends in a
    // bespoke moral choice and has to be code, whereas these end in the place explaining
    // itself, which is text — so the whole encounter is a JSON entry and this one method runs
    // any of them.
    //
    // The reward is the explanation. No unique drop, and no boon: every boon is already tied
    // to its island's trial quest, so granting one here would either be a no-op or would steal
    // that quest's moment. Favour is awarded instead, because favour is what the ending is
    // settled on and it is the one currency a dungeon can hand out without unbalancing loot.

    private void handleDungeonBoss(DungeonBoss boss) {
        if (boss.flag != null && game.getPlayer().hasFlag(boss.flag)) {
            String done = (boss.defeatedMessage != null && !boss.defeatedMessage.isBlank())
                    ? boss.defeatedMessage : "Whatever waited here has already been answered.";
            game.log(done, MessageLog.Type.DIM);
            return;
        }

        String intro = (boss.intro != null && !boss.intro.isBlank())
                ? boss.intro : "Something down here notices you.";
        game.getMessageLog().prompt(intro,
                () -> startDungeonBossFight(boss),
                () -> game.log("You step back from the altar. It waits.", MessageLog.Type.DIM));
    }

    private void startDungeonBossFight(DungeonBoss boss) {
        var coord = new io.cannonforge.retroquest.overlay.BossFightCoordinator(game);
        for (int i = 0; i < boss.phases.size(); i++) {
            DungeonBoss.Phase p = boss.phases.get(i);
            boolean last = (i == boss.phases.size() - 1);
            // Always the seven-argument constructor: the short one derives level from hp/8,
            // which is what once turned a 65 HP shrine boss into a level 8 monster.
            Monster m = new Monster(p.name, p.level, p.hp, p.attack, p.gold, p.xp, p.ac);
            coord.addPhase(m, last ? null : p.transition, !last && p.healBetween);
        }
        game.getGamePanel().setBossFightCoordinator(coord);
        game.getGamePanel().getCombatOverlay().setUnfleeable(true);
        coord.start(() -> {
            game.getGamePanel().getCombatOverlay().setUnfleeable(false);
            onDungeonBossDefeated(boss);
        });
    }

    /** Sets the flag, pays the favour, then lets the place say what it is. */
    private void onDungeonBossDefeated(DungeonBoss boss) {
        if (boss.flag != null && !boss.flag.isBlank()) {
            game.getPlayer().setFlag(boss.flag, "true");
        }

        God god = boss.godOrNull();
        java.util.List<String> footerLines = new java.util.ArrayList<>();
        if (boss.revelation != null && boss.revelation.footerLines != null) {
            footerLines.addAll(boss.revelation.footerLines);
        }
        if (god != null && boss.favor > 0) {
            game.getPlayer().addFavor(god, boss.favor);
            game.log("+" + boss.favor + " " + god.displayName + " favor.", MessageLog.Type.INFO);
            footerLines.add("+" + boss.favor + " " + god.displayName + " favor");
        }
        game.getStatsPanel().refresh();

        if (boss.revelation == null || boss.revelation.body == null
                || boss.revelation.body.isBlank()) {
            return;   // a boss with nothing to say is still a legal boss
        }

        java.awt.Color accent = (god != null)
                ? io.cannonforge.retroquest.overlay.DivineAudienceOverlay.colorFor(god)
                : io.cannonforge.retroquest.overlay.OverlayTheme.CYAN_ACC;

        game.getGamePanel().openRevelation(
                boss.revelation.title,
                boss.revelation.subtitle,
                accent,
                boss.revelation.body,
                new io.cannonforge.retroquest.overlay.RevelationOverlay.LinesFooter(
                        boss.revelation.footerHeading, footerLines, accent),
                () -> {
                    if (game.getGamePanel() != null) game.getGamePanel().repaint();
                    if (game.getStatsPanel() != null) game.getStatsPanel().refresh();
                });
    }

    // ── Cradle of Shards Endings (Endgame) ─────────────────────────────────────
    // Called by CradleChoiceOverlay after the player selects an ending path.
    // Each method creates a BossFightCoordinator with multi-phase bosses,
    // then chains into the EpilogueAnimation → CreditsAnimation on victory.

    private Monster makeBoss(String name, int hp, int damage, int ac, int level,
                              java.util.List<String> spells, int castChance,
                              int spellPower, int spellResist) {
        Monster m = new Monster(name, hp, damage, 0);
        m.setAC(ac);
        m.setLevel(level);
        m.setSpellNames(spells);
        m.setSpellCastChance(castChance);
        m.setSpellPower(spellPower);
        m.setSpellResistance(spellResist);
        return m;
    }

    private void startBossFight(io.cannonforge.retroquest.overlay.BossFightCoordinator coord,
                                 String endingType, String endingTitle, God championGod) {
        game.getGamePanel().setBossFightCoordinator(coord);
        game.getGamePanel().getCombatOverlay().setUnfleeable(true);
        coord.start(() -> {
            game.getGamePanel().getCombatOverlay().setUnfleeable(false);
            game.getPlayer().setFlag("cradle_ending", endingType);
            game.getPlayer().setFlag("game_complete", "true");
            game.getStatsPanel().refresh();
            game.getGamePanel().startEpilogue(endingType, endingTitle, championGod);
        });
    }

    // The Cradle of Shards fights are the last thing in the game and must out-stat every
    // random spawn that reaches it: a level-30 boss cannot land a hit on a level-40 character,
    // and 800 HP / 30 damage was weaker than the level-27 trash mob The Undying (500 HP /
    // 28 damage / power 30). All phases are now level 46–50 with AC and spell power to match.

    /** New Serpent: 3-phase fight against the united gods — the hardest fight in the game. */
    public void endingNewSerpent() {
        var coord = new io.cannonforge.retroquest.overlay.BossFightCoordinator(game);
        coord.addPhase(
            makeBoss("The United Seven", 2200, 55, 30, 50,
                     java.util.List.of("Fireball", "Lightning Bolt", "Ice Storm"), 40, 90, 25),
            "The entity SCREAMS. Seven divine forms tear free from the writhing mass!",
            true);
        coord.addPhase(
            makeBoss("The Raging Seven", 1600, 65, 32, 50,
                     java.util.List.of("Meteor Swarm", "Chain Lightning", "Cone of Cold"), 45, 100, 30),
            "DESPERATE UNITY \u2014 The gods burn through their last divine reserves!",
            true);
        coord.addPhase(
            makeBoss("The Desperate Unity", 1200, 80, 34, 50,
                     java.util.List.of("Meteor Swarm", "Flame Strike"), 55, 115, 20),
            null, false);
        startBossFight(coord, "new_serpent", "The New Serpent", null);
    }

    /** Champion: 2-phase fight against the forsaken six gods. */
    public void endingChampion(God god) {
        var coord = new io.cannonforge.retroquest.overlay.BossFightCoordinator(game);
        coord.addPhase(
            makeBoss("The Forsaken Six", 1800, 48, 28, 48,
                     java.util.List.of("Fireball", "Ice Storm"), 35, 75, 20),
            "The six gods falter\u2026 then surge with desperate rage!",
            true);
        coord.addPhase(
            makeBoss("The Forsaken Six, Enraged", 1400, 62, 30, 50,
                     java.util.List.of("Meteor Swarm", "Chain Lightning"), 45, 95, 25),
            null, false);
        startBossFight(coord, "champion", "Champion of " + god.displayName, god);
    }

    /** True Unbound: gauntlet of 3 sequential god echoes, NO heal between. */
    public void endingTrueUnbound() {
        var coord = new io.cannonforge.retroquest.overlay.BossFightCoordinator(game);
        coord.addPhase(
            makeBoss("Echo of Pyralis", 900, 38, 26, 46,
                     java.util.List.of("Fireball", "Flame Strike"), 40, 55, 0),
            "Another god steps forward. The trial continues.",
            false);  // no heal between
        coord.addPhase(
            makeBoss("Echo of Thalorax", 1100, 46, 28, 48,
                     java.util.List.of("Ice Storm", "Lightning Bolt"), 45, 68, 0),
            "The final echo approaches. Everything you have left.",
            false);
        coord.addPhase(
            makeBoss("Echo of Bellorak", 1300, 55, 30, 50,
                     java.util.List.of("Meteor Swarm"), 30, 80, 0),
            null, false);
        startBossFight(coord, "true_unbound", "The True Unbound", null);
    }

    /** Generic: single fight against a dreaming guardian. */
    public void endingGeneric() {
        var coord = new io.cannonforge.retroquest.overlay.BossFightCoordinator(game);
        coord.addPhase(
            makeBoss("The Dreaming Guardian", 1600, 45, 26, 46,
                     java.util.List.of("Fireball"), 30, 70, 0),
            null, false);
        startBossFight(coord, "generic", "The Unfinished Dream", null);
    }

    // ── Fountain ──────────────────────────────────────────────────────────────
    // Ten outcomes: heals, XP, gold, poison, acid, and enchanted teleport.

    private void handleFountain() {
        SoundManager.getInstance().playFountain();

        // Same tile convention as every other special: authored maps are 0-based, the
        // procedural dungeon is 1-based. Mixing the two let one feature spend another's
        // "used" flag on the neighbouring tile.
        boolean authored = game.getDungeonViewState().isAuthored();
        int px = authored ? game.getPlayer().getX() : game.getPlayer().getX() + 1;
        int py = authored ? game.getPlayer().getY() : game.getPlayer().getY() + 1;
        String mapKey = game.currentMapKey();

        if (isFeatureExhausted(mapKey, px, py)) {
            game.log("The fountain has run dry.", MessageLog.Type.DIM);
            return;
        }

        game.getMessageLog().prompt("Drink from the sparkling fountain?",
            () -> {
                exhaustFeature(mapKey, px, py);
                int roll = (int)(Math.random() * FOUNTAIN_OUTCOMES);
                switch (roll) {
                    case 0 -> {
                        int healed = game.getPlayer().getMaxHp() - game.getPlayer().getHp();
                        game.getPlayer().heal(game.getPlayer().getMaxHp());
                        game.getPlayer().refreshSpellSlots();
                        game.log("The waters are holy! +" + healed + " HP restored. Spells refreshed.",
                                MessageLog.Type.GOOD);
                    }
                    case 1 -> {
                        int healed = (int)(game.getPlayer().getMaxHp() * 0.5);
                        game.getPlayer().heal(healed);
                        game.log("The water is invigorating! +" + healed + " HP.", MessageLog.Type.GOOD);
                    }
                    case 2 -> {
                        int xp = FOUNTAIN_XP_SMALL_BASE + game.getCurrentDepth() * FOUNTAIN_XP_SMALL_SCALE;
                        var lvl = game.getPlayer().addXP(xp);
                        game.log("Visions of ancient battles fill your mind! +" + xp + " XP.",
                                MessageLog.Type.GOOD);
                        game.logLevelUp(lvl);
                    }
                    case 3 -> {
                        int xp = FOUNTAIN_XP_LARGE_BASE + game.getCurrentDepth() * FOUNTAIN_XP_LARGE_SCALE;
                        var lvl = game.getPlayer().addXP(xp);
                        game.log("The ancient gods smile upon you! +" + xp + " XP.", MessageLog.Type.LOOT);
                        game.logLevelUp(lvl);
                    }
                    case 4 -> {
                        int gold = FOUNTAIN_GOLD_BASE + game.getCurrentDepth() * FOUNTAIN_GOLD_PER_DEPTH
                                + (int)(Math.random() * FOUNTAIN_GOLD_RANDOM);
                        game.getPlayer().addGold(gold);
                        game.log("Coins glint at the bottom of the fountain! +" + gold + " gold.",
                                MessageLog.Type.LOOT);
                    }
                    case 5 -> game.log("The water is cool and refreshing, but nothing happens.",
                            MessageLog.Type.DIM);
                    case 6 -> {
                        int dmg = FOUNTAIN_POISON_BASE + (int)(Math.random() * FOUNTAIN_POISON_RANDOM);
                        game.getPlayer().takeDamage(dmg);
                        game.log("The water is poisoned! -" + dmg + " HP.", MessageLog.Type.DANGER);
                        if (game.getPlayer().isDead()) game.killPlayer("Poisoned by the cursed fountain...");
                    }
                    case 7 -> {
                        int dmg = FOUNTAIN_ACID_BASE + game.getCurrentDepth() * FOUNTAIN_ACID_PER_DEPTH;
                        game.getPlayer().takeDamage(dmg);
                        game.log("The water burns like acid! -" + dmg + " HP.", MessageLog.Type.DANGER);
                        if (game.getPlayer().isDead()) game.killPlayer("Dissolved by the acid fountain...");
                    }
                    case 8 -> {
                        SoundManager.getInstance().playTeleportWhoosh();
                        game.getGamePanel().startTeleportAnimation();
                        int[] pos = findRandomWalkablePosition();
                        game.getPlayer().setPosition(pos[0], pos[1]);
                        revealVisibleArea();
                        game.updateCamera();
                        game.log("The enchanted water teleports you!", MessageLog.Type.INFO);
                    }
                    case 9 -> {
                        int healed = game.getPlayer().getMaxHp() - game.getPlayer().getHp();
                        game.getPlayer().heal(game.getPlayer().getMaxHp());
                        int xp = FOUNTAIN_DIVINE_XP_BASE + game.getCurrentDepth() * FOUNTAIN_DIVINE_XP_SCALE;
                        var lvl = game.getPlayer().addXP(xp);
                        game.log("A divine spring! +" + healed + " HP, +" + xp + " XP.",
                                MessageLog.Type.GOOD);
                        game.logLevelUp(lvl);
                    }
                    case 10 -> {
                        game.getPlayer().addBonusCastSlot();
                        game.log("The fountain shimmers with spellfire. A spell slot is restored!", MessageLog.Type.GOOD);
                    }
                }
                game.getStatsPanel().refresh();
            }, null);
    }

    // ── Ground Loot ───────────────────────────────────────────────────────────
    // Classic floor-loot system: refuse, silver, gold, items,
    // chests, and gems — all with depth-scaled values.
    // Triggered by a random per-step roll, not tied to any map tile.
    // Most drops are instant; only a chest or item asks before taking.

    /** Called every dungeon step — ~12% chance of finding something on the floor. */
    public void checkGroundLoot() {
        if (game.getMessageLog().isPromptActive() || encounterPending) return;
        if (Math.random() < LOOT_CHANCE) handleBox();
    }

    /**
     * Called every dungeon step — encounter rate varies by tile type and depth.
     * Base ~10%, scaled by dungeon special tile modifier and depth.
     * Monster level is the current dungeon depth ± 2 (clamped 1–50).
     * Skipped if a prompt is already active to avoid UI collision.
     */
    /**
     * True from the moment an encounter is rolled until its combat screen is actually up.
     * In a first-person dungeon that gap is the length of the monster preview, and anything
     * that checks {@code isCombatActive()} during it gets the wrong answer.
     */
    private boolean encounterPending = false;

    public boolean isEncounterPending() { return encounterPending; }

    public void checkDungeonEncounter() {
        if (game.getMessageLog().isPromptActive() || encounterPending) return;
        boolean authored = game.getDungeonViewState().isAuthored();
        int px = authored ? game.getPlayer().getX() : game.getPlayer().getX() + 1;
        int py = authored ? game.getPlayer().getY() : game.getPlayer().getY() + 1;
        char special = DungeonWallQuery.getSpecial(px, py, game.getCurrentDepth(),
                game.getCurrentMap(), authored);
        double tileMod = dungeonTileEncounterMod(special);
        double rate = ENCOUNTER_BASE_RATE * tileMod * (0.8 + game.getCurrentDepth() * ENCOUNTER_DEPTH_SCALE);
        rate = Math.min(ENCOUNTER_MAX_RATE, rate);
        if (Math.random() >= rate) return;

        int variance     = (int)(Math.random() * ENCOUNTER_VARIANCE) - 2;   // -2 to +2
        int monsterLevel = Math.max(1, Math.min(MAX_LEVEL_RANGE, dungeonMonsterLevel() + variance));
        Monster m = MonsterRegistry.getRandomMonster(monsterLevel);

        // In first-person mode, flash the monster sprite in the corridor before combat
        if (game.getDungeonViewState().isFirstPerson()) {
            if (game.getDungeonViewState().isRaycast()) {
                io.cannonforge.retroquest.core.RaycastDungeonRenderer.showMonsterPreview(m);
            } else if (game.getDungeonViewState().isTextured()) {
                io.cannonforge.retroquest.core.TexturedDungeonRenderer.showMonsterPreview(m);
            } else {
                io.cannonforge.retroquest.core.WireframeDungeonRenderer.showMonsterPreview(m);
            }
            // Delay combat start to let the preview play (400ms). Nothing else may raise a
            // screen of its own in the meantime.
            encounterPending = true;
            javax.swing.Timer previewTimer = new javax.swing.Timer(420, e2 -> {
                encounterPending = false;
                game.getGamePanel().startCombat(m, (won, gold, loot, leveled, newLevel) -> {
                    if (game.getGamePanel() != null) game.getGamePanel().repaint();
                });
            });
            previewTimer.setRepeats(false);
            previewTimer.start();
            // Trigger repaints during the preview
            javax.swing.Timer repaintTimer = new javax.swing.Timer(30, e2 -> {
                if (game.getGamePanel() != null) game.getGamePanel().repaint();
            });
            repaintTimer.setRepeats(true);
            repaintTimer.start();
            javax.swing.Timer stopRepaint = new javax.swing.Timer(450, e2 -> repaintTimer.stop());
            stopRepaint.setRepeats(false);
            stopRepaint.start();
            return;
        }

        game.getGamePanel().startCombat(m, (won, gold, loot, leveled, newLevel) -> {
            if (game.getGamePanel() != null) game.getGamePanel().repaint();
        });
    }

    /**
     * The level of thing that lives on this dungeon floor.
     *
     * <p>For the procedural Dreamwake the answer is the depth: fifty floors that get worse
     * the further you go, which is the point of it. An authored dungeon is different — it
     * is three to eight floors belonging to a particular island, and using the raw depth
     * there filled the endgame with level-two wolves. Take the danger of the ground at the
     * entrance and add a floor's worth per level down.
     */
    private int dungeonMonsterLevel() {
        int depth = game.getCurrentDepth();
        if (!game.getDungeonViewState().isAuthored()) return depth;
        int surface = game.getOverworldManager()
                .getSpawnDifficulty(lastDungeonEntryX, lastDungeonEntryY);
        int base = 1 + (surface * 49) / 99;
        return base + Math.max(0, depth - 1);
    }

    /** Encounter rate modifier based on dungeon special tile type. */
    private static double dungeonTileEncounterMod(char special) {
        return switch (special) {
            case 'I'          -> 0.3;   // Inn — safe zone
            case 's'          -> 0.5;   // Stairway — transitional
            case 'A', 'f', 'H' -> 0.7;  // Altar, Fountain, Throne — sacred areas
            case 'P', 't', 'g' -> 1.2;  // Pit, Teleporter, Glowing Cube — dangerous
            default           -> 1.0;   // Normal corridor
        };
    }

    private static final String[] GEM_NAMES =
            {"ruby", "emerald", "sapphire", "diamond", "topaz", "amethyst", "opal"};

    private void handleBox() {
        int roll = (int)(Math.random() * 100);

        if (roll < LOOT_REFUSE_CUTOFF) {
            // ── Refuse ──
            game.log("You find some refuse on the floor. Nothing of value.", MessageLog.Type.DIM);
            game.getStatsPanel().refresh();

        } else if (roll < LOOT_SILVER_CUTOFF) {
            // ── Silver pieces ──
            int silver = SILVER_BASE + (int)(Math.random() * (game.getCurrentDepth() * SILVER_DEPTH_SCALE + SILVER_RANDOM_BASE));
            game.getPlayer().addGold(silver);
            game.log("You find " + silver + " silver pieces.", MessageLog.Type.LOOT);
            game.getStatsPanel().refresh();

        } else if (roll < LOOT_GOLD_CUTOFF) {
            // ── Gold pieces ──
            int gold = game.getCurrentDepth() * GOLD_DEPTH_SCALE + GOLD_BASE
                     + (int)(Math.random() * (game.getCurrentDepth() * GOLD_RANDOM_DEPTH_SCALE + GOLD_RANDOM_BASE));
            game.getPlayer().addGold(gold);
            game.log("You find " + gold + " gold pieces!", MessageLog.Type.LOOT);
            game.getStatsPanel().refresh();

        } else if (roll < LOOT_ITEM_CUTOFF) {
            // ── Item on the floor — prompt to pick up, may be trapped ──
            Item item = LootGenerator.getRandomLoot(game.getCurrentDepth(), game.getPlayer().getLevel());
            game.getMessageLog().prompt("You find a " + item.getName() + " lying on the floor. Pick it up?",
                () -> {
                    // Trap damage formula: 1 + RND * (level * 4)
                    if (Math.random() < ITEM_TRAP_CHANCE) {
                        // INT detection: (INT-10)*4% chance to spot trap
                        int intBonus = Math.max(0, game.getPlayer().getIntelligence() - 10);
                        if (Math.random() * 100 < intBonus * 4) {
                            SoundManager.getInstance().play("spell");
                            game.log("Your keen mind spots a hidden trap mechanism — you disarm it!",
                                    MessageLog.Type.GOOD);
                        } else {
                            int dmg = 1 + (int)(Math.random() * (game.getCurrentDepth() * ITEM_TRAP_DEPTH_SCALE));
                            // DEX evasion: (DEX-10)*3% full dodge, else (DEX-10)*5% halve
                            int dexBonus = Math.max(0, game.getPlayer().getDex() - 10);
                            if (Math.random() * 100 < dexBonus * 3) {
                                SoundManager.getInstance().playTrap();
                                game.log("It was trapped! You nimbly dodge the mechanism — no damage!",
                                        MessageLog.Type.INFO);
                            } else {
                                if (Math.random() * 100 < dexBonus * 5) dmg = Math.max(1, dmg / 2);
                                game.getPlayer().takeDamage(dmg);
                                SoundManager.getInstance().playTrap();
                                game.log("It was trapped! You take " + dmg + " damage.",
                                        MessageLog.Type.DANGER);
                                if (game.getPlayer().isDead()) {
                                    game.killPlayer("Killed by a trapped item...");
                                    return;
                                }
                            }
                        }
                    }
                    if (game.getPlayer().addItemAndProgress(item)) {
                        game.log("You pick up the " + item.getName() + ".", MessageLog.Type.LOOT);
                    } else {
                        game.log("Your inventory is full — " + item.getName() + " left behind.",
                                MessageLog.Type.DIM);
                    }
                    game.getStatsPanel().refresh();
                }, null);

        } else if (roll < LOOT_CHEST_CUTOFF) {
            // ── Chest — prompt before opening ──
            SoundManager.getInstance().playChestOpen();
            game.getMessageLog().prompt("You find a chest here. Open it?",
                () -> {
                    if (Math.random() < CHEST_TRAP_CHANCE) {
                        // INT detection: (INT-10)*4% chance to spot trap
                        int intBonus = Math.max(0, game.getPlayer().getIntelligence() - 10);
                        if (Math.random() * 100 < intBonus * 4) {
                            SoundManager.getInstance().play("spell");
                            game.log("You notice poison needles in the lock — trap disarmed!",
                                    MessageLog.Type.GOOD);
                            // Still get the gold reward
                            int gold = game.getCurrentDepth() * CHEST_TRAP_GOLD_DEPTH_SCALE + CHEST_TRAP_GOLD_BASE
                                     + (int)(Math.random() * CHEST_TRAP_GOLD_RANDOM);
                            game.getPlayer().addGold(gold);
                            game.log("Inside: " + gold + " gold.", MessageLog.Type.LOOT);
                        } else {
                            // Trapped — DEX evasion
                            int dmg = CHEST_TRAP_DMG_BASE + game.getCurrentDepth() * CHEST_TRAP_DMG_PER_DEPTH;
                            int dexBonus = Math.max(0, game.getPlayer().getDex() - 10);
                            if (Math.random() * 100 < dexBonus * 3) {
                                SoundManager.getInstance().playTrap();
                                game.log("The chest was trapped! You leap back just in time — no damage!",
                                        MessageLog.Type.INFO);
                            } else {
                                if (Math.random() * 100 < dexBonus * 5) dmg = Math.max(1, dmg / 2);
                                game.getPlayer().takeDamage(dmg);
                                SoundManager.getInstance().playTrap();
                            }
                            int gold = game.getCurrentDepth() * CHEST_TRAP_GOLD_DEPTH_SCALE + CHEST_TRAP_GOLD_BASE
                                     + (int)(Math.random() * CHEST_TRAP_GOLD_RANDOM);
                            game.getPlayer().addGold(gold);
                            if (dmg > 0 && !game.getPlayer().isDead()) {
                                game.log("The chest was trapped! -" + dmg + " HP. Inside: "
                                        + gold + " gold.", MessageLog.Type.DANGER);
                            }
                            if (game.getPlayer().isDead()) game.killPlayer("Killed by a trapped chest...");
                        }
                    } else {
                        // Safe chest — STR bonus gold: (STR-10)*5% extra
                        Item loot = LootGenerator.getRandomLoot(game.getCurrentDepth(), game.getPlayer().getLevel());
                        int gold  = game.getCurrentDepth() * CHEST_GOLD_DEPTH_SCALE + CHEST_GOLD_BASE
                                  + (int)(Math.random() * (game.getCurrentDepth() * CHEST_GOLD_RANDOM_DEPTH + CHEST_GOLD_RANDOM_BASE));
                        int strBonus = Math.max(0, game.getPlayer().getStr() - 10);
                        int bonusGold = gold * strBonus * 5 / 100;
                        gold += bonusGold;
                        game.getPlayer().addGold(gold);
                        String strMsg = bonusGold > 0 ? " You pry open a hidden compartment — +" + bonusGold + " extra gold!" : "";
                        if (game.getPlayer().addItemAndProgress(loot)) {
                            game.log("The chest contains " + gold + " gold and a "
                                    + loot.getName() + "!" + strMsg, MessageLog.Type.LOOT);
                        } else {
                            game.log("The chest contains " + gold
                                    + " gold!" + strMsg + " (Inventory full — item left behind)",
                                    MessageLog.Type.LOOT);
                        }
                    }
                    game.getStatsPanel().refresh();
                }, null);

        } else {
            // ── Gem or jewellery ──
            String gem      = GEM_NAMES[(int)(Math.random() * GEM_NAMES.length)];
            int    gemValue = game.getCurrentDepth() * GEM_VALUE_DEPTH_SCALE + GEM_VALUE_BASE
                            + (int)(Math.random() * (game.getCurrentDepth() * GEM_VALUE_RANDOM_SCALE));
            game.getPlayer().addGold(gemValue);
            game.log("You find a " + gem + " worth " + gemValue + " gold!",
                    MessageLog.Type.LOOT);
            game.getStatsPanel().refresh();
        }
    }

    /**
     * Depth for a teleport the player did not choose.
     *
     * <p>Rolling freely over the whole dungeon is faithful to the genre, but here it ended
     * first trips: a level-1 character stepping into a depth-2 teleporter landed on depth 36
     * and was killed in one round by the first thing it met, with no counterplay and no way
     * back up. The surprise is the point, so keep the roll — just bound it to a level the
     * character can run from. Choosing a level deliberately (the cube's other branch) is
     * still unbounded, so diving deep on purpose remains possible.
     */
    private int randomTeleportDepth(int maxLevel) {
        int reach = Math.max(game.getCurrentDepth() + 3, game.getPlayer().getLevel() + 3);
        return 1 + (int)(Math.random() * Math.max(1, Math.min(maxLevel, reach)));
    }

    // ── Glowing Cube ──────────────────────────────────────────────────────────
    // A misty grey cube — touching it lets you choose a destination level
    // (1-50) or teleport to a random level.

    private void handleGlowingCube() {
        SoundManager.getInstance().playCube();
        // Inside an authored dungeon the cube can only reach that dungeon's own levels
        boolean authored = game.getDungeonViewState().isAuthored();
        int maxLevel = authored
                ? countAuthoredLevels(game.getDungeonViewState().getAuthoredDungeonName())
                : Math.max(1, getMaxDungeonDepth());
        game.getMessageLog().prompt(
            "A misty grey cube floats before you. Touch it?",
            () -> {
                // The cube randomly decides whether to let the player choose
                // a level or to teleport them somewhere without warning.
                if (Math.random() < 0.5) {
                    // Player gets to pick a level
                    game.getMessageLog().promptInput(
                        "The cube responds to your will. Enter destination level (1-" + maxLevel + "):",
                        input -> {
                            int level = parseLevel(input);
                            if (level < 1 || level > maxLevel) {
                                game.log("The cube fades — invalid level.", MessageLog.Type.DIM);
                                return;
                            }
                            cubeTransport(level);
                        });
                } else {
                    // Random level, no choice
                    cubeTransport(randomTeleportDepth(maxLevel));
                }
            },
            null);
    }

    /** Teleports the player to the given dungeon level (same x,y). */
    private void cubeTransport(int targetLevel) {
        SoundManager.getInstance().playTeleportWhoosh();
        game.getGamePanel().startTeleportAnimation();

        if (game.getDungeonViewState().isAuthored()) {
            // Load the authored level — generating one here would leave authored movement
            // rules running over a procedural map with no walls.
            String name = game.getDungeonViewState().getAuthoredDungeonName();
            if (targetLevel == game.getCurrentDepth() || !loadAuthoredLevel(name, targetLevel)) {
                game.log("The cube dims. Nothing happens.", MessageLog.Type.DIM);
                return;
            }
            revealVisibleArea();
            game.updateCamera();
            game.log("The cube dissolves and you materialise on level " + targetLevel + "!",
                    MessageLog.Type.INFO);
            game.getStatsPanel().refresh();
            return;
        }

        game.setCurrentDepth(targetLevel);
        game.setCurrentMap(Dungeon.generate(targetLevel));
        game.getPlayer().progressQuest(Quest.Type.EXPLORE, "Dungeon Level " + targetLevel, 1);
        // Keep same x,y so position is consistent across levels
        revealVisibleArea();
        game.snapDungeonCamera();
        game.updateCamera();
        game.log("The cube dissolves and you materialise on level " + targetLevel + "!",
                MessageLog.Type.INFO);
        game.getStatsPanel().refresh();
    }

    /** Parses a level string — returns the int if 1-50, otherwise -1. */
    private int parseLevel(String input) {
        if (input == null || input.isBlank()) return -1;
        try {
            int v = Integer.parseInt(input.trim());
            return (v >= 1 && v <= MAX_LEVEL_RANGE) ? v : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    // ── Throne ────────────────────────────────────────────────────────────────
    // Royal seat: restore + XP, gold, item + XP, throne guards, or punishment.

    private void handleThrone() {
        SoundManager.getInstance().playThrone();

        // Same tile convention as every other special: authored maps are 0-based, the
        // procedural dungeon is 1-based. Mixing the two let one feature spend another's
        // "used" flag on the neighbouring tile.
        boolean authored = game.getDungeonViewState().isAuthored();
        int px = authored ? game.getPlayer().getX() : game.getPlayer().getX() + 1;
        int py = authored ? game.getPlayer().getY() : game.getPlayer().getY() + 1;
        String mapKey = game.currentMapKey();

        if (isFeatureExhausted(mapKey, px, py)) {
            game.log("The throne's magic is exhausted.", MessageLog.Type.DIM);
            return;
        }

        // Leviathan's Eye / Boneyard Trench: guaranteed Seraphine journal drop
        if (game.getDungeonViewState().isAuthored()
                && ("leviathan_eye".equals(game.getDungeonViewState().getAuthoredDungeonName())
                    || "boneyard_trench".equals(game.getDungeonViewState().getAuthoredDungeonName()))
                && !game.getPlayer().hasFlag("seraphine_journal_5_found")) {
            exhaustFeature(mapKey, px, py);
            Item journal = ItemRegistry.getById("seraphine_journal_5");
            if (journal != null) {
                game.getPlayer().addItemAndProgress(journal);
                game.getPlayer().setFlag("seraphine_journal_5_found", "true");
                game.log("You find a waterlogged journal wedged between the bones. Seraphine was here.", MessageLog.Type.LOOT);
                game.log("\"The heart is not beneath one island \u2014 it is beneath ALL of them. The serpent is the sea floor itself.\"", MessageLog.Type.INFO);
                game.getStatsPanel().refresh();
            }
            return;
        }

        // Archive of Tears / Forgotten City: memory vignettes instead of loot
        if (game.getDungeonViewState().isAuthored()) {
            String dg = game.getDungeonViewState().getAuthoredDungeonName();
            if ("archive_of_tears".equals(dg) || "forgotten_city".equals(dg)) {
                exhaustFeature(mapKey, px, py);
                String vignette = pickMemoryVignette(px, py, game.getCurrentDepth());
                int xp = THRONE_XP_BASE + game.getCurrentDepth() * THRONE_XP_PER_DEPTH;
                game.getMessageLog().prompt(vignette, () -> {
                    var lvl = game.getPlayer().addXP(xp);
                    game.log("The memory fades. +" + xp + " XP.", MessageLog.Type.GOOD);
                    game.logLevelUp(lvl);
                    game.getStatsPanel().refresh();
                }, null);
                return;
            }
        }

        game.getMessageLog().prompt("An ancient throne dominates this chamber. Sit upon it?",
            () -> {
                exhaustFeature(mapKey, px, py);

                // 50% chance: a champion monster guards the throne
                if ((int)(Math.random() * 100) < THRONE_CHAMPION_CHANCE) {
                    spawnThroneChampion();
                    return;
                }

                int roll = (int)(Math.random() * 100);
                if (roll < THRONE_HEAL_CHANCE) {
                    int healed = game.getPlayer().getMaxHp() - game.getPlayer().getHp();
                    game.getPlayer().heal(game.getPlayer().getMaxHp());
                    game.getPlayer().refreshSpellSlots();
                    int xp = THRONE_XP_BASE + game.getCurrentDepth() * THRONE_XP_PER_DEPTH;
                    var lvl = game.getPlayer().addXP(xp);
                    game.log("The throne accepts you as royalty! +" + healed + " HP, spells refreshed, +"
                            + xp + " XP.", MessageLog.Type.GOOD);
                    game.logLevelUp(lvl);
                } else if (roll < THRONE_GOLD_CHANCE) {
                    int gold = game.getCurrentDepth() * THRONE_GOLD_DEPTH_SCALE + THRONE_GOLD_BASE
                             + (int)(Math.random() * (game.getCurrentDepth() * THRONE_GOLD_RANDOM_SCALE));
                    game.getPlayer().addGold(gold);
                    game.log("The throne showers you with ancient riches! +" + gold + " gold.",
                            MessageLog.Type.LOOT);
                } else if (roll < THRONE_ITEM_CHANCE) {
                    int xp      = THRONE_ITEM_XP_BASE + game.getCurrentDepth() * THRONE_ITEM_XP_PER_DEPTH;
                    Item reward = LootGenerator.getRandomLoot(game.getCurrentDepth() + THRONE_ITEM_LEVEL_BONUS, game.getPlayer().getLevel());
                    var lvl = game.getPlayer().addXP(xp);
                    game.getPlayer().addItemAndProgress(reward);
                    game.log("Ancient knowledge flows into you! +" + xp + " XP. Found: "
                            + reward.getName() + "!", MessageLog.Type.LOOT);
                    game.logLevelUp(lvl);
                } else if (roll < THRONE_GUARD_CHANCE) {
                    game.log("The throne summons its guards! Prepare for battle!", MessageLog.Type.DANGER);
                    Monster guard = new Monster("Throne Guard",
                            THRONE_GUARD_HP_BASE + game.getCurrentDepth() * THRONE_GUARD_HP_PER_DEPTH,
                            THRONE_GUARD_ATK_BASE + game.getCurrentDepth() * THRONE_GUARD_ATK_PER_DEPTH,
                            THRONE_GUARD_GOLD);
                    game.getGamePanel().startCombat(guard, (cWon, cGold, cLoot, cLeveled, cLevel) -> {
                        if (game.getGamePanel() != null) game.getGamePanel().repaint();
                    });
                } else {
                    int dmg = THRONE_WRATH_DMG_BASE + game.getCurrentDepth() * THRONE_WRATH_DMG_PER_DEPTH;
                    game.getPlayer().takeDamage(dmg);
                    game.log("The throne rejects you with violent force! -" + dmg + " HP.",
                            MessageLog.Type.DANGER);
                    if (game.getPlayer().isDead()) game.killPlayer("Destroyed by the ancient throne...");
                }
                game.getStatsPanel().refresh();
            }, null);
    }

    private static final String[] CHAMPION_TITLES = {
        "King", "Sovereign", "Overlord", "Warlord", "Tyrant",
        "Emperor", "Dread Lord", "Ancient King", "Undying Regent"
    };

    private void spawnThroneChampion() {
        int depth = game.getCurrentDepth();
        // Depth alone put a level-7 champion on level 2 of the first island's caverns, where
        // the character is level 3 and the prompt gives no warning — a coin flip that ends
        // the run. Take the harder of what the depth suggests and what the character can
        // actually face, so sitting on a throne is a gamble rather than an execution.
        int depthLevel = 1 + (depth * 49) / 50;
        int championLevel = Math.min(50, Math.max(depthLevel, game.getPlayer().getLevel() + 2));
        Monster base = MonsterRegistry.getRandomMonster(championLevel);
        String title = CHAMPION_TITLES[(int)(Math.random() * CHAMPION_TITLES.length)];
        String name = title + " " + base.getName();
        // Scale stats: +50% HP, +50% damage, double gold and XP
        int hp  = base.getMaxHp() + base.getMaxHp() / 2;
        int dmg = base.getDamage() + base.getDamage() / 2;
        int gold = base.getGoldReward() * 2;
        int xp  = base.getXPValue() * 2;
        int ac  = base.getAC() + 3;
        Monster champion = new Monster(name, championLevel, hp, dmg, gold, xp, ac);
        champion.setImageFileName(base.getImageFileName());
        game.log("The throne erupts with dark energy! " + name + " rises to defend it!", MessageLog.Type.DANGER);
        SoundManager.getInstance().play("boss");
        game.getGamePanel().startCombat(champion, (cWon, cGold, cLoot, cLeveled, cLevel) -> {
            if (game.getGamePanel() != null) game.getGamePanel().repaint();
        });
    }

    // ── Memory Vignettes (Archive of Tears / Forgotten City) ──────────────────
    // Throne tiles in Umbryn's dungeons show narrative memories instead of loot.

    private static final String[] MEMORY_VIGNETTES = {
        "A crystal throne flickers to life. You see a city beneath the waves, its people singing to a serpent that coils beneath the world. They are happy. They do not know what the Seven Dreams are planning.",
        "Silver light floods the chamber. A woman kneels before an archive shelf, weeping. She has just learned that her civilization was erased \u2014 not destroyed, but forgotten. As if it never existed.",
        "The throne shows you a memory of rain. Just rain, falling on stone. Somewhere, a child is laughing. You feel the warmth of bread from an oven. These are fragments of a life that someone once loved.",
        "You see the Shattering from Umbryn's eyes. The other six dreams tear Aqualon apart while Umbryn catches the scream of a dying eighth dream \u2014 Hope \u2014 and holds it, the first memory he ever kept.",
        "A market square bustles with ghosts who do not know they are dead. A merchant arranges invisible wares. A mother calls a child whose name she has forgotten. The memory loops, endlessly.",
        "The throne reveals an ancient library stretching beyond sight. Every book is a life. Every shelf is a generation. You understand, briefly, the weight of remembering everything that was ever lost.",
        "Silver motes drift from the throne. You see Seraphine, thirty years younger, standing in this very chamber. She writes in her journal: \"The keys are not just keys. They are the dreams themselves, crystallized.\"",
        "A vision of the world as it could be: the seven islands joined, the ocean calm, Aqualon whole beneath the waves. People walk freely between lands that were once separated by divine jealousy.",
        "You see two gods arguing. One wants to warn Aqualon. The other says freedom is worth any price. The first god is consumed by the other six. Its name was Hope.",
        "The memory shows a funeral procession for a world. Umbryn walks at the rear, catching every tear, every whispered name, every last breath. He will carry them all. He always has.",
    };

    private static String pickMemoryVignette(int px, int py, int depth) {
        int idx = Math.abs((px * 31 + py * 17 + depth * 13)) % MEMORY_VIGNETTES.length;
        return MEMORY_VIGNETTES[idx];
    }

    // ── Memory Pool (Island 6 — unique dungeon special) ────────────────────────
    // Stepping on 'M' tile shows a memory fragment and grants XP on first visit.
    // No combat risk, no random outcomes — pure narrative exploration reward.

    private static final String[] MEMORY_POOL_FRAGMENTS = {
        "The pool shimmers. You glimpse a child being told a bedtime story about a serpent that dreamed the world. The child asks: \"Is the serpent still dreaming?\" The parent doesn't answer.",
        "Silver light rises from the pool. You see a craftsman building a ship. He sings as he works. The song is about a world where the sea never rises. He does not know the song is a prayer.",
        "The pool shows a battlefield long forgotten. Warriors on both sides pause, confused. They cannot remember why they are fighting. One by one, they lay down their arms.",
        "You see a library burning. A woman runs through the flames, clutching a single book. She cannot save them all. She chose the one that holds the names of everyone who ever lived in her city.",
        "The pool reveals a garden beneath the waves, tended by shadows that were once gardeners. They still prune. They still water. They have forgotten they are dead, and the garden thrives.",
        "A vision of two lovers separated by the Shattering. Each stands on a different island, facing the sea. Neither knows the other survived. They wait anyway.",
        "You see a god \u2014 young, uncertain \u2014 kneeling beside a dying mortal. The god whispers: \"I will remember you.\" It is the first promise Umbryn ever made. He has kept it for millennia.",
        "The pool shows a classroom. A teacher draws the shape of the world on a board: seven islands, connected by currents. A student asks what lies beneath. The teacher erases the board.",
    };

    private void handleMemoryPool(int px, int py) {
        String mapKey = game.currentMapKey();

        if (isFeatureExhausted(mapKey, px, py)) {
            game.log("The pool is still. Its memory has been witnessed.", MessageLog.Type.DIM);
            return;
        }

        exhaustFeature(mapKey, px, py);
        int idx = Math.abs((px * 37 + py * 23 + game.getCurrentDepth() * 11)) % MEMORY_POOL_FRAGMENTS.length;
        String fragment = MEMORY_POOL_FRAGMENTS[idx];
        int xp = 80 + game.getCurrentDepth() * 15;

        game.getMessageLog().prompt(fragment, () -> {
            var lvl = game.getPlayer().addXP(xp);
            game.log("The pool fades to silver glass. +" + xp + " XP.", MessageLog.Type.GOOD);
            game.logLevelUp(lvl);
            game.getStatsPanel().refresh();
        }, null);
    }

    // ── Inn ───────────────────────────────────────────────────────────────────
    // Dungeon inns restore HP and spells for a gold fee.

    private void handleInn() {
        int cost = INN_COST_BASE + game.getCurrentDepth() * INN_COST_PER_DEPTH;
        game.getMessageLog().prompt("A dungeon inn! Rest and recover for " + cost + " gold?",
            () -> {
                if (game.getPlayer().getGold() < cost) {
                    game.log("You cannot afford the " + cost + " gold to stay.", MessageLog.Type.DANGER);
                    return;
                }
                game.getPlayer().addGold(-cost);
                int healed = game.getPlayer().getMaxHp() - game.getPlayer().getHp();
                game.getPlayer().heal(game.getPlayer().getMaxHp());
                game.getPlayer().refreshSpellSlots();
                game.log("You rest at the inn for " + cost + " gold. +" + healed
                        + " HP restored. Spells refreshed.", MessageLog.Type.GOOD);
                game.getStatsPanel().refresh();
            }, null);
    }

    // ── Puzzles ─────────────────────────────────────────────────────────────────

    private void handlePuzzle() {
        SoundManager.getInstance().play("puzzle");

        boolean authored = game.getDungeonViewState().isAuthored();
        int px = authored ? game.getPlayer().getX() : game.getPlayer().getX() + 1;
        int py = authored ? game.getPlayer().getY() : game.getPlayer().getY() + 1;
        String mapKey = game.currentMapKey();

        if (isFeatureExhausted(mapKey, px, py)) {
            game.log("The puzzle mechanism is spent.", MessageLog.Type.DIM);
            return;
        }

        int depth = game.getCurrentDepth();
        int type = Math.abs(px * 31 + py * 17 + depth * 7) % 4;
        switch (type) {
            case 0 -> handlePuzzleForgeSequence(mapKey, px, py, depth);
            case 1 -> handlePuzzleLavaRiddle(mapKey, px, py, depth);
            case 2 -> handlePuzzleRuneLock(mapKey, px, py, depth);
            case 3 -> handlePuzzleCrucibleTrial(mapKey, px, py, depth);
        }
    }

    private void handlePuzzleForgeSequence(String mapKey, int px, int py, int depth) {
        int seqLen = 3 + depth / 10;
        // Build deterministic sequence from tile coords
        int seed = Math.abs(px * 13 + py * 23 + depth * 3);
        String[] sequence = new String[seqLen];
        for (int i = 0; i < seqLen; i++) {
            sequence[i] = FORGE_RUNES[(seed + i * 7) % FORGE_RUNES.length];
        }

        // Display the sequence in a dialog, then start prompts after dismissal
        StringBuilder sb = new StringBuilder();
        sb.append("A row of forge bellows lines the wall. Runes glow in sequence:\n\n");
        for (int i = 0; i < seqLen; i++) {
            if (i > 0) sb.append(" — ");
            sb.append(sequence[i]);
        }
        sb.append("\n\nRepeat the sequence from memory!");
        game.getMessageLog().prompt(sb.toString(), new String[]{"Begin"}, new Runnable[] {
            () -> chainForgePrompt(mapKey, px, py, depth, sequence, 0)
        });
    }

    private void chainForgePrompt(String mapKey, int px, int py, int depth,
                                   String[] sequence, int step) {
        if (step >= sequence.length) {
            // All correct — reward!
            exhaustFeature(mapKey, px, py);
            int xp = PZ_FORGE_XP_BASE + depth * PZ_FORGE_XP_PER_DEPTH;
            var lvl = game.getPlayer().addXP(xp);
            Item loot = LootGenerator.getRandomLoot(depth + PZ_FORGE_LOOT_BONUS, game.getPlayer().getLevel());
            game.getPlayer().addItemAndProgress(loot);
            game.log("The runes flash with approval! +" + xp + " XP. Found: "
                    + loot.getName() + "!", MessageLog.Type.GOOD);
            game.logLevelUp(lvl);
            game.getStatsPanel().refresh();
            return;
        }

        String correct = sequence[step];
        // Build 4 shuffled choices including the correct one
        String[] choices = new String[4];
        choices[0] = correct;
        int seed = Math.abs(px * 11 + py * 19 + depth * 5 + step * 37);
        int idx = 1;
        for (int i = 0; i < FORGE_RUNES.length && idx < 4; i++) {
            String r = FORGE_RUNES[(seed + i) % FORGE_RUNES.length];
            if (!r.equals(correct)) choices[idx++] = r;
        }
        // Deterministic shuffle based on step
        for (int i = choices.length - 1; i > 0; i--) {
            int j = Math.abs(seed + step * 3 + i * 13) % (i + 1);
            String tmp = choices[i]; choices[i] = choices[j]; choices[j] = tmp;
        }

        game.getMessageLog().prompt("Rune " + (step + 1) + " of " + sequence.length + "?",
                choices, new Runnable[] {
                    () -> checkForgeAnswer(mapKey, px, py, depth, sequence, step, choices[0]),
                    () -> checkForgeAnswer(mapKey, px, py, depth, sequence, step, choices[1]),
                    () -> checkForgeAnswer(mapKey, px, py, depth, sequence, step, choices[2]),
                    () -> checkForgeAnswer(mapKey, px, py, depth, sequence, step, choices[3]),
                });
    }

    private void checkForgeAnswer(String mapKey, int px, int py, int depth,
                                   String[] sequence, int step, String chosen) {
        if (chosen.equals(sequence[step])) {
            game.log(chosen + " — correct!", MessageLog.Type.GOOD);
            chainForgePrompt(mapKey, px, py, depth, sequence, step + 1);
        } else {
            exhaustFeature(mapKey, px, py);
            game.log("Wrong! The runes fade to darkness.", MessageLog.Type.DANGER);
        }
    }

    private void handlePuzzleLavaRiddle(String mapKey, int px, int py, int depth) {
        int riddleIdx = Math.abs(px * 13 + py * 23 + depth * 3) % RIDDLES.length;
        String[] riddle = RIDDLES[riddleIdx];
        String question = riddle[0];
        String[] answers = {riddle[1], riddle[2], riddle[3]};
        int correctIdx = Integer.parseInt(riddle[4]);

        // INT hint: mark one wrong answer as unlikely
        int intThreshold = PZ_RIDDLE_INT_HINT_BASE + depth / 5;
        if (game.getPlayer().getIntelligence() >= intThreshold) {
            for (int i = 0; i < 3; i++) {
                if (i != correctIdx) {
                    answers[i] = answers[i] + " (unlikely)";
                    break;
                }
            }
        }

        game.getMessageLog().prompt("An obsidian tablet bears carved text:\n\n\"" + question + "\"", answers, new Runnable[] {
            () -> resolveRiddle(mapKey, px, py, depth, correctIdx == 0),
            () -> resolveRiddle(mapKey, px, py, depth, correctIdx == 1),
            () -> resolveRiddle(mapKey, px, py, depth, correctIdx == 2),
        });
    }

    private void resolveRiddle(String mapKey, int px, int py, int depth, boolean correct) {
        exhaustFeature(mapKey, px, py);
        if (correct) {
            int xp = PZ_RIDDLE_XP_BASE + depth * PZ_RIDDLE_XP_PER_DEPTH;
            int gold = PZ_RIDDLE_GOLD_BASE + depth * PZ_RIDDLE_GOLD_PER_DEPTH;
            var lvl = game.getPlayer().addXP(xp);
            game.getPlayer().addGold(gold);
            game.getPlayer().addFavor(God.PYRALIS, PZ_RIDDLE_FAVOR);
            game.log("The tablet glows with approval! +" + xp + " XP, +" + gold + " gold. Pyralis is pleased.",
                    MessageLog.Type.GOOD);
            game.logLevelUp(lvl);
        } else {
            game.log("The tablet cracks and crumbles. Wrong answer.", MessageLog.Type.DANGER);
        }
        game.getStatsPanel().refresh();
    }

    private void handlePuzzleRuneLock(String mapKey, int px, int py, int depth) {
        int clueIdx = Math.abs(px * 13 + py * 23 + depth * 3) % RUNE_CLUES.length;
        String clue = RUNE_CLUES[clueIdx][0];
        String answer = RUNE_CLUES[clueIdx][1];

        // WIS hint
        String hint = "";
        if (game.getPlayer().getWisdom() >= PZ_RUNE_WIS_HINT) {
            hint = "\n\n(Hint: " + answer.length() + " letters, begins with '"
                    + answer.substring(0, 1).toUpperCase() + "')";
        }

        game.getMessageLog().promptInput("A sealed vault door bears three rune slots. An inscription reads:\n\n\"" + clue + "\"" + hint + "\n\nSpeak the word:", input -> {
            exhaustFeature(mapKey, px, py);
            if (input != null && input.trim().equalsIgnoreCase(answer)) {
                int xp = PZ_RUNE_XP_BASE + depth * PZ_RUNE_XP_PER_DEPTH;
                var lvl = game.getPlayer().addXP(xp);
                Item loot = LootGenerator.getRandomLoot(depth + PZ_RUNE_LOOT_BONUS, game.getPlayer().getLevel());
                game.getPlayer().addItemAndProgress(loot);
                game.log("The vault opens! +" + xp + " XP. Found: " + loot.getName() + "!",
                        MessageLog.Type.GOOD);
                game.logLevelUp(lvl);
            } else {
                game.log("The runes dim. The vault remains sealed.", MessageLog.Type.DANGER);
            }
            game.getStatsPanel().refresh();
        });
    }

    private void handlePuzzleCrucibleTrial(String mapKey, int px, int py, int depth) {
        game.getMessageLog().prompt("Three forge crucibles glow before you — copper, silver, and gold.\nEach radiates heat. Choose wisely.",
                new String[]{"Copper (safe)", "Silver (risky)", "Gold (dangerous)"},
                new Runnable[] {
                    () -> resolveCrucibleCopper(mapKey, px, py, depth),
                    () -> resolveCrucibleSilver(mapKey, px, py, depth),
                    () -> resolveCrucibleGold(mapKey, px, py, depth),
                });
    }

    private void resolveCrucibleCopper(String mapKey, int px, int py, int depth) {
        exhaustFeature(mapKey, px, py);
        int xp = PZ_TRIAL_COPPER_XP_BASE + depth * PZ_TRIAL_COPPER_XP_PER;
        var lvl = game.getPlayer().addXP(xp);
        game.log("The copper draught warms you gently. +" + xp + " XP.", MessageLog.Type.GOOD);
        game.logLevelUp(lvl);
        game.getStatsPanel().refresh();
    }

    private void resolveCrucibleSilver(String mapKey, int px, int py, int depth) {
        exhaustFeature(mapKey, px, py);
        int conReq = PZ_TRIAL_CON_MODERATE + depth / 5;
        if (game.getPlayer().getCon() >= conReq) {
            int xp = PZ_TRIAL_SILVER_XP_BASE + depth * PZ_TRIAL_SILVER_XP_PER;
            int gold = PZ_TRIAL_SILVER_GOLD_BASE + depth * PZ_TRIAL_SILVER_GOLD_PER;
            var lvl = game.getPlayer().addXP(xp);
            game.getPlayer().addGold(gold);
            game.log("The silver fire courses through you! +" + xp + " XP, +" + gold + " gold.",
                    MessageLog.Type.GOOD);
            game.logLevelUp(lvl);
        } else {
            int dmg = 10 + depth * PZ_TRIAL_SILVER_DMG_PER;
            game.getPlayer().takeDamage(dmg);
            game.log("The silver fire scorches your insides! -" + dmg + " HP.", MessageLog.Type.DANGER);
            if (game.getPlayer().isDead()) game.killPlayer("Consumed by the silver crucible...");
        }
        game.getStatsPanel().refresh();
    }

    private void resolveCrucibleGold(String mapKey, int px, int py, int depth) {
        exhaustFeature(mapKey, px, py);
        int conReq = PZ_TRIAL_CON_HARD + depth / 4;
        if (game.getPlayer().getCon() >= conReq) {
            int xp = PZ_TRIAL_GOLD_XP_BASE + depth * PZ_TRIAL_GOLD_XP_PER;
            var lvl = game.getPlayer().addXP(xp);
            Item loot = LootGenerator.getRandomLoot(depth + PZ_TRIAL_GOLD_LOOT_BONUS, game.getPlayer().getLevel());
            game.getPlayer().addItemAndProgress(loot);
            game.getPlayer().addFavor(God.PYRALIS, PZ_TRIAL_GOLD_FAVOR);
            game.log("The golden flames forge you anew! +" + xp + " XP. Found: "
                    + loot.getName() + "! Pyralis burns with approval.", MessageLog.Type.GOOD);
            game.logLevelUp(lvl);
        } else {
            int dmg = 15 + depth * PZ_TRIAL_GOLD_DMG_PER;
            game.getPlayer().takeDamage(dmg);
            game.log("The golden fire overwhelms you! -" + dmg + " HP.", MessageLog.Type.DANGER);
            if (game.getPlayer().isDead()) game.killPlayer("Incinerated by the golden crucible...");
        }
        game.getStatsPanel().refresh();
    }

    // ── Feature exhaustion ─────────────────────────────────────────────────────

    private boolean isFeatureExhausted(String mapKey, int px, int py) {
        return TileStateManager.getBoolProperty(game.getSaveData(), mapKey, px, py, "used");
    }

    private void exhaustFeature(String mapKey, int px, int py) {
        TileStateManager.setProperty(game.getSaveData(), mapKey, px, py, "used", "true");
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    /**
     * Returns a random walkable position on the current dungeon level.
     *
     * <p>Bounds come from the live map: authored levels are 40x40, and scanning the
     * procedural 200x200 range over one of them threw {@code ArrayIndexOutOfBounds}.
     */
    private int[] findRandomWalkablePosition() {
        char[][] map = game.getCurrentMap();
        if (map == null || map.length == 0) return new int[]{0, 0};
        boolean authored = game.getDungeonViewState().isAuthored();
        int w = Math.min(DungeonWallQuery.getMapWidth(map, authored),  map[0].length);
        int h = Math.min(DungeonWallQuery.getMapHeight(map, authored), map.length);

        for (int attempts = 0; attempts < 2000; attempts++) {
            int x = (int)(Math.random() * w);
            int y = (int)(Math.random() * h);
            if (game.isWalkable(map[y][x])) return new int[]{x, y};
        }
        return new int[]{w / 2, h / 2}; // fallback; should never be reached
    }

    // ── Fog of War ────────────────────────────────────────────────────────────

    /**
     * Updates the dungeon revealed state using the vision system.
     * Called after player movement, level changes, and teleporter events.
     * The actual visibility computation and revealed[][] update is done by
     * {@link VisionSystem#computeBrightness(Retroquest)} as a side effect.
     */
    public void revealVisibleArea() {
        if (!game.isInDungeon()) return;
        // VisionSystem.computeBrightness() updates revealed[][] as a side effect
        // during each paint cycle. This call ensures it runs at least once for
        // non-rendering contexts (e.g., after teleport before repaint).
        VisionSystem.computeBrightness(game);
    }

    /**
     * Returns the persistent revealed-tiles array for the given dungeon level,
     * creating it on first access.
     */
    public boolean[][] getRevealed(int depth) {
        // Keyed by dungeon identity as well as depth — otherwise every authored dungeon and
        // the procedural one share one fog map per depth.
        return game.getRevealedForKey(game.revealedKey(depth));
    }
}

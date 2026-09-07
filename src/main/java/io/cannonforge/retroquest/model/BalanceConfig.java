package io.cannonforge.retroquest.model;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Externalized balance constants loaded from {@code data/balance.json}.
 *
 * <p>Every field has a hardcoded default matching the original {@code static final}
 * values in DungeonController, CombatEngine, and EncounterController. If the JSON
 * file is missing or a field is absent, the default applies — so deleting
 * balance.json reverts to factory settings.
 *
 * <p>Editable at runtime: change balance.json and call {@link #reload()} (or
 * restart). No recompile needed.
 */
public class BalanceConfig {

    private static final File CONFIG_FILE = new File("data/balance.json");
    private static BalanceConfig instance;

    // ── Sections ─────────────────────────────────────────────────────────────

    private Dungeon   dungeon   = new Dungeon();
    private Combat    combat    = new Combat();
    private Encounter encounter = new Encounter();

    // ── Dungeon balance ──────────────────────────────────────────────────────

    public static class Dungeon {
        // Limits
        public int maxDepth              = 50;

        // Pit
        public int pitDmgBase            = 1;
        public int pitDmgPerDepth        = 3;

        // Altar
        public int altarHealChance       = 40;
        public int altarXpChance         = 65;
        public int altarSilentChance     = 80;
        public int altarTitheChance      = 88;
        public int altarSpellSlotChance  = 96;
        public int altarXpBase           = 100;
        public int altarXpPerDepth       = 15;
        public int altarTitheBase        = 2;
        public int altarTitheRandom      = 6;
        public int altarWrathDmgBase     = 15;
        public int altarWrathDmgPerDepth = 2;

        // Corrupted Shrine
        public int shrineBossHp          = 65;
        public int shrineBossAtk         = 5;
        public int shrineBossGold        = 100;
        public int shrineXpBase          = 50;
        public int shrineXpPerDepth      = 20;

        // Fountain
        public int fountainOutcomes      = 11;
        public int fountainXpSmallBase   = 150;
        public int fountainXpSmallScale  = 10;
        public int fountainXpLargeBase   = 300;
        public int fountainXpLargeScale  = 20;
        public int fountainGoldBase      = 3;
        public int fountainGoldPerDepth  = 1;
        public int fountainGoldRandom    = 5;
        public int fountainPoisonBase    = 10;
        public int fountainPoisonRandom  = 15;
        public int fountainAcidBase      = 15;
        public int fountainAcidPerDepth  = 2;
        public int fountainDivineXpBase  = 200;
        public int fountainDivineXpScale = 12;

        // Ground Loot
        public double lootChance              = 0.12;
        public int    lootRefuseCutoff        = 20;
        public int    lootSilverCutoff        = 35;
        public int    lootGoldCutoff          = 55;
        public int    lootItemCutoff          = 70;
        public int    lootChestCutoff         = 85;
        public int    silverBase              = 1;
        public int    silverDepthScale        = 1;
        public int    silverRandomBase        = 1;
        public int    goldDepthScale          = 2;
        public int    goldBase                = 3;
        public int    goldRandomDepthScale    = 1;
        public int    goldRandomBase          = 5;
        public double itemTrapChance          = 0.20;
        public int    itemTrapDepthScale      = 4;
        public double chestTrapChance         = 0.15;
        public int    chestTrapDmgBase        = 5;
        public int    chestTrapDmgPerDepth    = 2;
        public int    chestTrapGoldDepthScale = 1;
        public int    chestTrapGoldBase       = 2;
        public int    chestTrapGoldRandom     = 4;
        public int    chestGoldDepthScale     = 2;
        public int    chestGoldBase           = 6;
        public int    chestGoldRandomDepth    = 1;
        public int    chestGoldRandomBase     = 4;
        public int    gemValueDepthScale      = 3;
        public int    gemValueBase            = 8;
        public int    gemValueRandomScale     = 2;

        // Dungeon Encounter (used by DungeonController)
        public double encounterBaseRate       = 0.10;
        public double encounterDepthScale     = 0.008;
        public int    encounterVariance       = 5;

        // Throne
        public int throneHealChance           = 30;
        public int throneGoldChance           = 55;
        public int throneItemChance           = 72;
        public int throneGuardChance          = 88;
        public int throneXpBase               = 50;
        public int throneXpPerDepth           = 8;
        public int throneGoldDepthScale       = 10;
        public int throneGoldBase             = 27;
        public int throneGoldRandomScale      = 6;
        public int throneItemXpBase           = 75;
        public int throneItemXpPerDepth       = 10;
        public int throneChampionChance       = 50;
        public int throneItemLevelBonus       = 6;
        public int throneGuardHpBase          = 60;
        public int throneGuardHpPerDepth      = 5;
        public int throneGuardAtkBase         = 14;
        public int throneGuardAtkPerDepth     = 2;
        public int throneGuardGold            = 15;
        public int throneWrathDmgBase         = 20;
        public int throneWrathDmgPerDepth     = 3;

        // Inn
        public int innCostBase                = 2;
        public int innCostPerDepth            = 1;

        // Puzzle — Forge Sequence
        public int puzzleForgeXpBase          = 150;
        public int puzzleForgeXpPerDepth      = 20;
        public int puzzleForgeLootBonus       = 3;

        // Puzzle — Lava Riddle
        public int puzzleRiddleGoldBase       = 50;
        public int puzzleRiddleGoldPerDepth   = 15;
        public int puzzleRiddleXpBase         = 100;
        public int puzzleRiddleXpPerDepth     = 15;
        public int puzzleRiddleIntHintBase    = 12;
        public int puzzleRiddleFavor          = 3;

        // Puzzle — Rune Lock
        public int puzzleRuneLockXpBase       = 200;
        public int puzzleRuneLockXpPerDepth   = 25;
        public int puzzleRuneLockLootBonus    = 6;
        public int puzzleRuneLockWisHint      = 15;

        // Puzzle — Crucible Trial
        public int puzzleTrialCopperXpBase    = 50;
        public int puzzleTrialCopperXpPerDepth = 8;
        public int puzzleTrialSilverXpBase    = 120;
        public int puzzleTrialSilverXpPerDepth = 15;
        public int puzzleTrialSilverGoldBase  = 30;
        public int puzzleTrialSilverGoldPerDepth = 10;
        public int puzzleTrialSilverDmgPerDepth  = 3;
        public int puzzleTrialGoldDmgPerDepth    = 5;
        public int puzzleTrialGoldXpBase      = 250;
        public int puzzleTrialGoldXpPerDepth  = 30;
        public int puzzleTrialGoldLootBonus   = 5;
        public int puzzleTrialGoldFavor       = 5;
        public int puzzleTrialConBaseModerate = 10;
        public int puzzleTrialConBaseHard     = 14;
    }

    // ── Combat balance ───────────────────────────────────────────────────────

    public static class Combat {
        public int    blindnessToHitPenalty       = 4;
        public int    critThreshold               = 19;
        public int    baseToHitBonus              = 3;
        public int    friendlyReactionThreshold   = 15;
        public int    aggressiveReactionThreshold = 6;
        public int    playerPoisonMin             = 1;
        public int    playerPoisonRange           = 3;
        public int    monsterPoisonMin            = 2;
        public int    monsterPoisonRange          = 4;
        public double lootDropChance              = 0.0735;
    }

    // ── Overworld Encounter balance ──────────────────────────────────────────

    public static class Encounter {
        public double baseRate          = 0.08;
        public double difficultyBase    = 0.5;
        public double difficultyPerPoint = 0.05;
        public int    maxMonsterLevel   = 50;
    }

    // ── Singleton / IO ───────────────────────────────────────────────────────

    public BalanceConfig() {}

    public static BalanceConfig get() {
        if (instance == null) instance = load();
        return instance;
    }

    public static BalanceConfig load() {
        if (CONFIG_FILE.exists()) {
            try (InputStreamReader r = new InputStreamReader(new FileInputStream(CONFIG_FILE), StandardCharsets.UTF_8)) {
                BalanceConfig cfg = new Gson().fromJson(r, BalanceConfig.class);
                if (cfg != null) { instance = cfg; return cfg; }
            } catch (Exception e) {
                System.err.println("[BalanceConfig] Failed to load: " + e.getMessage());
            }
        }
        instance = new BalanceConfig();
        return instance;
    }

    public static void reload() { instance = null; load(); }

    public void save() {
        try {
            CONFIG_FILE.getParentFile().mkdirs();
            try (OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(CONFIG_FILE), StandardCharsets.UTF_8)) {
                new GsonBuilder().setPrettyPrinting().create().toJson(this, w);
            }
        } catch (IOException e) {
            System.err.println("[BalanceConfig] Failed to save: " + e.getMessage());
        }
    }

    // ── Accessors ────────────────────────────────────────────────────────────

    public Dungeon   getDungeon()   { return dungeon; }
    public Combat    getCombat()    { return combat; }
    public Encounter getEncounter() { return encounter; }
}

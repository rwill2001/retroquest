package io.cannonforge.retroquest.controller;
import io.cannonforge.retroquest.core.Retroquest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.cannonforge.retroquest.model.BalanceConfig;
import io.cannonforge.retroquest.model.Monster;
import io.cannonforge.retroquest.model.TileDefinition;
import io.cannonforge.retroquest.registry.MonsterRegistry;
import io.cannonforge.retroquest.registry.TileRegistry;

/**
 * Handles random monster encounter rolls for the overworld and dungeon.
 *
 * <h2>Encounter formula</h2>
 * <p>Each step on a non-safe tile rolls:
 * <pre>
 *   baseRate        = BASE_ENCOUNTER_RATE × tile.encounterRateMod
 *   encounterChance = baseRate × (DIFFICULTY_BASE_SCALE + spawnDifficulty × DIFFICULTY_PER_POINT)
 * </pre>
 * All coefficients are loaded from {@link BalanceConfig} ({@code balance.json}).
 * {@code spawnDifficulty} (0–99) is painted per-tile in RetroForge.
 * Tiles whose {@code isSafeZone()} flag is set (towns, certain terrain) are skipped entirely.
 *
 * <h2>Monster level mapping</h2>
 * <p>{@code spawnDifficulty} maps linearly to monster level 1–50:
 * {@code level = 1 + (difficulty × 49) / 99}.
 * The level is passed to {@link MonsterRegistry#getRandomMonster(int)}, which applies
 * the 75% exact-level / 50% ±3-window / factory fallback selection chain.
 *
 * <h2>Tile spawn weights</h2>
 * <p>If a tile defines a {@code spawnWeights} map ({@code monsterId → relativeWeight}),
 * the registry's weighted selection skews results toward those monster types, enabling
 * biome-specific encounters (e.g. undead-heavy crypts, beast-heavy forests).
 */
public class EncounterController {

    private static final BalanceConfig.Encounter BE = BalanceConfig.get().getEncounter();
    private static final double BASE_ENCOUNTER_RATE   = BE.baseRate;
    private static final double DIFFICULTY_BASE_SCALE = BE.difficultyBase;
    private static final double DIFFICULTY_PER_POINT  = BE.difficultyPerPoint;
    private static final int    MAX_MONSTER_LEVEL     = BE.maxMonsterLevel;

    private final Retroquest game;

    public EncounterController(Retroquest game) {
        this.game = game;
    }

    /** Rolls for a random encounter at the player's current overworld tile. */
    public void checkForRandomEncounter() {
        if (game.getCurrentMap() == null || game.getPlayer() == null) return;

        int    px         = game.getPlayer().getX();
        int    py         = game.getPlayer().getY();
        char   tile       = game.getCurrentMap()[py][px];
        int    difficulty = game.getOverworldManager().getSpawnDifficulty(px, py);

        // Base rate: forest/trees a little higher; scaled by tile's encounterRateMod
        TileDefinition def  = TileRegistry.getByIdSafe(tile);
        if (def.isSafeZone()) return;
        double baseRate     = BASE_ENCOUNTER_RATE * def.getEncounterRateMod();
        double encounterChance = baseRate * (DIFFICULTY_BASE_SCALE + difficulty * DIFFICULTY_PER_POINT);

        if (Math.random() < encounterChance) {
            triggerEncounter(difficulty);
        }
    }

    /**
     * Spawns a monster whose level is derived from the tile's spawn difficulty.
     * spawnDifficulty 0–99 maps linearly to monster level 1–50.
     * If adjacent tiles contain water, aquatic monsters are heavily favored.
     */
    private void triggerEncounter(int spawnDifficulty) {
        // Linear mapping: difficulty 0 → level 1, difficulty 99 → level 50
        int monsterLevel = 1 + (spawnDifficulty * (MAX_MONSTER_LEVEL - 1)) / 99;
        int px = game.getPlayer().getX();
        int py = game.getPlayer().getY();
        char tile = game.getCurrentMap()[py][px];
        TileDefinition def = TileRegistry.getByIdSafe(tile);

        Map<String, Integer> weights = new HashMap<>(def.getSpawnWeights());
        int waterCount = countAdjacentWater(px, py);
        if (waterCount > 0) {
            boostAquaticSpawns(weights, waterCount, monsterLevel);
        }

        Monster m = MonsterRegistry.getRandomMonster(monsterLevel, weights);
        game.getGamePanel().startCombat(m, (won, gold, loot, leveled, newLevel) -> {
            if (game.getGamePanel() != null) game.getGamePanel().repaint();
        });
    }

    /** Counts how many of the 4 cardinal neighbors are liquid tiles. */
    private int countAdjacentWater(int px, int py) {
        char[][] map = game.getCurrentMap();
        int h = map.length, w = map[0].length;
        int count = 0;
        int[][] dirs = {{0,-1},{1,0},{0,1},{-1,0}};
        for (int[] d : dirs) {
            int nx = px + d[0], ny = py + d[1];
            if (nx >= 0 && nx < w && ny >= 0 && ny < h) {
                if (TileRegistry.getByIdSafe(map[ny][nx]).isLiquid()) count++;
            }
        }
        return count;
    }

    /**
     * Boosts spawn weights for aquatic monsters when near water.
     * Weight boost scales with number of adjacent water tiles:
     * 1 tile = 3×, 2 = 5×, 3 = 8×, 4 = 12× (surrounded by water).
     */
    private void boostAquaticSpawns(Map<String, Integer> weights, int waterCount, int monsterLevel) {
        int boost = switch (waterCount) {
            case 1 -> 3;
            case 2 -> 5;
            case 3 -> 8;
            default -> 12;
        };
        List<Monster> all = MonsterRegistry.getAllMonsters();
        for (Monster m : all) {
            if (m.isAquatic() && !m.isFriendly() && Math.abs(m.getLevel() - monsterLevel) <= 3) {
                weights.put(m.getId(), boost);
            }
        }
    }
}

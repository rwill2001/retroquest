package io.cannonforge.retroquest.model;

import java.awt.Color;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Definition of a single tile type, loaded from the tile registry.
 *
 * <p>Each tile has a single-character ID, a display name, category (Overworld/Town/Dungeon),
 * a render color, walkability, an optional sprite path, and an optional {@link TileEffect}
 * that triggers when the player steps on it.
 */
public class TileDefinition {

    private char id;
    private String name;
    private String category;

    private Color editorColor;
    private boolean walkable;

    private String spriteKey;

    private boolean isLiquid;

    private boolean blocksVision;
    private boolean isSafeZone;
    private int lightRadius;

    private String stepSound;
    private double encounterRateMod;
    private String description;

    // === GENERIC EFFECTS SYSTEM ===
    private TileEffect onStepEffect;

    /**
     * Optional per-tile monster spawn weight overrides.
     * Keys are monster IDs (e.g. {@code "corrupted_crab"}); values are relative
     * weights — the default weight for every monster is 1, so a value of 4 makes
     * that monster four times as likely to appear on this tile type.
     */
    private Map<String, Integer> spawnWeights = new HashMap<>();

    // Gson no-arg constructor
    public TileDefinition() {
        this.encounterRateMod = 1.0;
        this.stepSound = "step_default";
        this.onStepEffect = new TileEffect("none");
    }

    // Short constructor (used for most tiles)
    public TileDefinition(char id, String name, String category, Color editorColor,
                          boolean walkable, String spriteKey) {
        this(id, name, category, editorColor, walkable, spriteKey,
             false, false, false, 0,
             "step_default", 1.0, "", new TileEffect("none"));
    }

    // Full constructor
    public TileDefinition(char id, String name, String category, Color editorColor,
                          boolean walkable, String spriteKey,
                          boolean isLiquid,
                          boolean blocksVision, boolean isSafeZone, int lightRadius,
                          String stepSound, double encounterRateMod, String description,
                          TileEffect onStepEffect) {

        this.id = id;
        this.name = name;
        this.category = category;
        this.editorColor = editorColor;
        this.walkable = walkable;
        this.spriteKey = spriteKey != null ? spriteKey : "default";

        this.isLiquid = isLiquid;
        this.blocksVision = blocksVision;
        this.isSafeZone = isSafeZone;
        this.lightRadius = lightRadius;

        this.stepSound = stepSound != null ? stepSound : "step_default";
        this.encounterRateMod = encounterRateMod;
        this.description = description != null ? description : "";

        this.onStepEffect = (onStepEffect != null) ? onStepEffect : new TileEffect("none");
    }

    // Getters
    public char getId() { return id; }
    public String getName() { return name; }
    public String getCategory() { return category; }
    public Color getEditorColor() { return editorColor; }
    public boolean isWalkable() { return walkable; }
    public String getSpriteKey() { return spriteKey; }
    public boolean isLiquid() { return isLiquid; }
    public boolean blocksVision() { return blocksVision; }
    public boolean isSafeZone() { return isSafeZone; }
    public int getLightRadius() { return lightRadius; }
    public String getStepSound() { return stepSound; }
    public double getEncounterRateMod() { return encounterRateMod; }
    public String getDescription() { return description; }
    public TileEffect getOnStepEffect() { return onStepEffect; }

    /** Returns the spawn-weight map (never null; empty = use default selection). */
    public Map<String, Integer> getSpawnWeights() {
        return spawnWeights != null ? spawnWeights : Collections.emptyMap();
    }

    /** Replaces the entire spawn-weight map (used by RetroForge tile editor). */
    public void setSpawnWeights(Map<String, Integer> weights) {
        this.spawnWeights = weights != null ? weights : new HashMap<>();
    }
}

package io.cannonforge.retroquest.model;

import java.awt.Color;

/**
 * Item rarity tiers used by the loot system.
 *
 * <p>Each rarity has a {@link #getWeight()} used for weighted-random selection
 * (higher weight = more frequently drops), a {@link #getPriceMultiplier()} applied
 * on top of the base item price, and a {@link #getColor()} for UI display.
 */
public enum Rarity {

    COMMON    (100, 1.0f,  new Color(200, 220, 255), "Common"),
    UNCOMMON  ( 40, 1.5f,  new Color(  0, 200,  80), "Uncommon"),
    RARE      ( 15, 2.5f,  new Color(  0, 120, 255), "Rare"),
    EPIC      (  4, 5.0f,  new Color(160,  50, 255), "Epic"),
    LEGENDARY (  1, 12.0f, new Color(255, 160,  30), "Legendary");

    private final int    weight;
    private final float  priceMultiplier;
    private final Color  color;
    private final String displayName;

    Rarity(int weight, float priceMultiplier, Color color, String displayName) {
        this.weight          = weight;
        this.priceMultiplier = priceMultiplier;
        this.color           = color;
        this.displayName     = displayName;
    }

    /** Relative drop frequency — higher means more common. */
    public int    getWeight()          { return weight; }

    /** Multiplier applied to the item's base price. */
    public float  getPriceMultiplier() { return priceMultiplier; }

    /** UI display colour for rarity-coloured text/badges. */
    public Color  getColor()           { return color; }

    /** Human-readable name shown in the item editor and inventory. */
    public String getDisplayName()     { return displayName; }
}

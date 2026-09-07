package io.cannonforge.retroquest.model;

import java.util.HashMap;
import java.util.Map;

/**
 * Describes the special effect triggered when a player steps on a tile.
 *
 * <p>The {@code type} field identifies the effect category (e.g., {@code "teleport"},
 * {@code "damage"}, {@code "none"}). Additional parameters are stored in {@code params}
 * as string key-value pairs parsed by the game engine.
 *
 * <p>Note: Java records auto-generate a {@code type()} accessor; the explicit
 * {@link #getType()} method is provided for consistency with the rest of the codebase.
 */
public record TileEffect(String type, Map<String, String> params) {

    // Gson no-arg constructor
    public TileEffect() {
        this("none", new HashMap<>());
    }

    public TileEffect(String type) {
        this(type, new HashMap<>());
    }

    public TileEffect(String type, Map<String, String> params) {
        this.type = (type != null) ? type.toLowerCase() : "none";
        this.params = (params != null) ? params : new HashMap<>();
    }

    // Nice getter for consistency with the rest of the code
    public String getType() {
        return type;
    }

    public String getParam(String key) {
        return params.getOrDefault(key, "");
    }

    public int getIntParam(String key, int defaultValue) {
        try {
            return Integer.parseInt(getParam(key));
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public boolean isNone() {
        return "none".equals(type);
    }
}

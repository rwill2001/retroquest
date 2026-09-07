package io.cannonforge.retroquest.model;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-coordinate runtime state for a single map tile, created on demand.
 *
 * <p>Stores a char override that is pushed into the live {@code char[][]} grid
 * on every map load, plus a flexible string→string property bag for door-open,
 * trap-triggered, pressure-plate-active, etc.
 */
public class TileState {

    /** '\0' means no override — tile shows its registry definition. */
    public char overrideId = '\0';

    /** Flexible properties: "open", "triggered", "active", "charges", etc. */
    public Map<String, String> data = new HashMap<>();

    public TileState() {}

    public boolean hasOverride() { return overrideId != '\0'; }

    public String getString(String key, String def) {
        return data.getOrDefault(key, def);
    }

    public int getInt(String key, int def) {
        try { return Integer.parseInt(data.get(key)); }
        catch (Exception e) { return def; }
    }

    public boolean getBool(String key, boolean def) {
        String v = data.get(key);
        if (v == null) return def;
        return "true".equalsIgnoreCase(v);
    }

    public void set(String key, String value) {
        data.put(key, value);
    }

    public void setOverride(char c) {
        this.overrideId = c;
    }
}

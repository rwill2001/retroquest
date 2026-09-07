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
 * Global game configuration loaded from {@code data/game_config.json}.
 * Editable via RetroForge's Game Settings dialog.
 */
public class GameConfig {

    private static final File CONFIG_FILE = new File("data/game_config.json");

    private String startingOverworld = "lirandel";
    private int startingX = 20;
    private int startingY = 48;
    private String startingAnimation = "WASH_ASHORE";
    private String lastSafeTown = "Moonhaven";   // must be a town an island actually links to

    // Singleton
    private static GameConfig instance;

    public GameConfig() {}

    public static GameConfig get() {
        if (instance == null) instance = load();
        return instance;
    }

    public static GameConfig load() {
        if (CONFIG_FILE.exists()) {
            try (InputStreamReader r = new InputStreamReader(new FileInputStream(CONFIG_FILE), StandardCharsets.UTF_8)) {
                GameConfig cfg = new Gson().fromJson(r, GameConfig.class);
                if (cfg != null) { instance = cfg; return cfg; }
            } catch (Exception e) {
                System.err.println("[GameConfig] Failed to load: " + e.getMessage());
            }
        }
        instance = new GameConfig();
        return instance;
    }

    public void save() {
        try {
            CONFIG_FILE.getParentFile().mkdirs();
            try (OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(CONFIG_FILE), StandardCharsets.UTF_8)) {
                new GsonBuilder().setPrettyPrinting().create().toJson(this, w);
            }
        } catch (IOException e) {
            System.err.println("[GameConfig] Failed to save: " + e.getMessage());
        }
    }

    // Getters
    public String getStartingOverworld() { return startingOverworld; }
    public int getStartingX() { return startingX; }
    public int getStartingY() { return startingY; }
    public String getStartingAnimation() { return startingAnimation; }
    public String getLastSafeTown() { return lastSafeTown; }

    // Setters
    public void setStartingOverworld(String v) { this.startingOverworld = v; }
    public void setStartingX(int v) { this.startingX = v; }
    public void setStartingY(int v) { this.startingY = v; }
    public void setStartingAnimation(String v) { this.startingAnimation = v; }
    public void setLastSafeTown(String v) { this.lastSafeTown = v; }
}

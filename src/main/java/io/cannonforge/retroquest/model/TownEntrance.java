package io.cannonforge.retroquest.model;

public record TownEntrance(String townName, int worldX, int worldY) {
    // Gson no-arg constructor (required for deserialization)
    public TownEntrance() {
        this("", 0, 0);
    }
}

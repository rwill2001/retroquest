package io.cannonforge.retroquest.model;

// Gson no-arg constructor required; targetX/Y of -1 means "use map centre" (backward compat)
public record OverworldTeleporter(int x, int y, String targetOverworld, int targetX, int targetY) {
    public OverworldTeleporter() {
        this(0, 0, "lirandel", -1, -1);
    }
}

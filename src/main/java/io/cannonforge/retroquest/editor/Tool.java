package io.cannonforge.retroquest.editor;

public enum Tool {
    PENCIL("Pencil Tool"),
    FILL("Fill Tool"),
    SELECT("Select Tool"),
    NPC_PLACER("NPC Placer"),
    TOWN_PLACER("Town Placer"),
    DUNGEON_PLACER("Dungeon Placer"),
    SPAWN_DIFFICULTY("Spawn Difficulty");

    private final String displayName;

    Tool(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}

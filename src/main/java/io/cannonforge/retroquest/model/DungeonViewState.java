package io.cannonforge.retroquest.model;

/**
 * Tracks whether the dungeon is rendered in top-down or first-person wireframe mode.
 * Lives on {@link io.cannonforge.retroquest.core.Retroquest}, not persisted in saves —
 * the mode is set dynamically when entering a dungeon based on which island the player is on.
 */
public class DungeonViewState {

    /** Persistence namespace used when the player is in the procedural dungeon. */
    public static final String PROCEDURAL_ID = "procedural";

    public enum Mode { TOP_DOWN, WIREFRAME, TEXTURED, RAYCAST }

    private Mode mode = Mode.TOP_DOWN;
    private boolean minimapVisible = true;
    /** Non-null when inside an authored .rfmap dungeon (e.g. "ember_caverns"). */
    private String authoredDungeonName = null;

    /**
     * Where the {@link Mode#RAYCAST} view is looking from. Only that renderer can draw an
     * arbitrary heading, so the band renderers ignore it and keep reading the player directly.
     */
    private final DungeonCamera camera = new DungeonCamera();

    public Mode    getMode()           { return mode; }
    public void    setMode(Mode mode)  { this.mode = mode; }
    public boolean isWireframe()       { return mode == Mode.WIREFRAME; }
    public boolean isTextured()        { return mode == Mode.TEXTURED; }
    public boolean isRaycast()         { return mode == Mode.RAYCAST; }
    public boolean isFirstPerson()     {
        return mode == Mode.WIREFRAME || mode == Mode.TEXTURED || mode == Mode.RAYCAST;
    }

    public DungeonCamera getCamera()   { return camera; }

    public boolean isMinimapVisible()          { return minimapVisible; }
    public void    setMinimapVisible(boolean v) { this.minimapVisible = v; }
    public void    toggleMinimap()              { minimapVisible = !minimapVisible; }

    public String  getAuthoredDungeonName()             { return authoredDungeonName; }
    public void    setAuthoredDungeonName(String name)  { this.authoredDungeonName = name; }
    public boolean isAuthored()                         { return authoredDungeonName != null; }

    /**
     * Identity of the dungeon the player is in, used to namespace persisted tile state and
     * fog of war. Authored dungeons use their own name; the procedural dungeon uses
     * {@link #PROCEDURAL_ID}. Never {@code null}.
     */
    public String getDungeonId() {
        return (authoredDungeonName != null && !authoredDungeonName.isEmpty())
                ? authoredDungeonName : PROCEDURAL_ID;
    }
}

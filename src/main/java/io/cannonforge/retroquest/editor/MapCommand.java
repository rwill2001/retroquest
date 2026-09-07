package io.cannonforge.retroquest.editor;
import java.util.List;

import io.cannonforge.retroquest.model.MapData;
import io.cannonforge.retroquest.model.NPC;
import io.cannonforge.retroquest.model.OverworldTeleporter;
import io.cannonforge.retroquest.model.TileState;
import io.cannonforge.retroquest.model.TownEntrance;

/**
 * Command-pattern records for MapCanvas undo/redo.
 *
 * <p>Each record stores only the delta (what changed), not a full map snapshot.
 * {@link MapCanvas#apply} and {@link MapCanvas#unapply} use these to mutate
 * {@link MapData} forward or backward.
 */
public sealed interface MapCommand
        permits MapCommand.TileCommand,
                MapCommand.NpcAddCommand, MapCommand.NpcRemoveCommand,
                MapCommand.EntranceAddCommand, MapCommand.EntranceRemoveCommand,
                MapCommand.DungeonAddCommand, MapCommand.DungeonRemoveCommand,
                MapCommand.TileStateCommand {

    /**
     * Per-cell tile delta used by {@link TileCommand}.
     *
     * <p>{@code removedEntrance} / {@code removedTeleporter} are non-null only when
     * the pencil painted over an 'E' or 'O' tile, so that undo can restore the
     * entrance or teleporter object to the map lists.  {@code removedState} is the
     * authored instance state (portal destination, dungeon name, ...) that painting
     * over the cell discarded, so undo can put it back.
     */
    record TileChange(int x, int y,
                      char oldTile, char newTile,
                      int oldDiff, int newDiff,
                      TownEntrance removedEntrance,
                      OverworldTeleporter removedTeleporter,
                      TileState removedState) {

        /** Convenience for changes that cannot drop instance state. */
        TileChange(int x, int y, char oldTile, char newTile, int oldDiff, int newDiff,
                   TownEntrance removedEntrance, OverworldTeleporter removedTeleporter) {
            this(x, y, oldTile, newTile, oldDiff, newDiff, removedEntrance, removedTeleporter, null);
        }
    }

    /** One or more tile/spawn-difficulty changes (pencil stroke, fill, spawn-diff paint). */
    record TileCommand(List<TileChange> changes)              implements MapCommand {}

    record NpcAddCommand(NPC npc)                             implements MapCommand {}
    record NpcRemoveCommand(NPC npc)                          implements MapCommand {}

    /**
     * Placement (or replacement) of a town entrance via {@link TownPlacementDialog}.
     * {@code replaced} is the entrance this one superseded, so undo can restore it.
     */
    record EntranceAddCommand(char oldTile, TownEntrance replaced,
                              TownEntrance entrance)          implements MapCommand {}

    /** Explicit removal of a town entrance (reserved for future UI). */
    record EntranceRemoveCommand(TownEntrance entrance)       implements MapCommand {}

    /**
     * Placement of a dungeon entrance via {@link DungeonPlacementDialog}. dungeonName is
     * null for procedural; {@code oldDungeonName} is the name that was there before, so
     * undo can restore an authored dungeon that was switched to procedural.
     */
    record DungeonAddCommand(char oldTile, int x, int y,
                             String oldDungeonName, String dungeonName) implements MapCommand {}

    /** Explicit removal of a dungeon entrance. */
    record DungeonRemoveCommand(char oldTile, int x, int y, String dungeonName) implements MapCommand {}

    /**
     * Change to the authored instance state of one cell (TileInstanceDialog save,
     * "Clear Instance State").  Either side may be null, meaning "no state".
     */
    record TileStateCommand(int x, int y,
                            TileState oldState, TileState newState) implements MapCommand {}
}

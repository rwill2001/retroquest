package io.cannonforge.retroquest.model;

import java.util.ArrayList;
import java.util.List;

/**
 * A bottom-of-dungeon boss, loaded from {@code data/dungeon_bosses.json}.
 *
 * <p>Four dungeons ended in a trial before this existed — Pressure Temple, Archive of Tears,
 * the Iron Pit and the Cradle — and each of those is a hand-written method in
 * {@code DungeonController} because each ends in a bespoke moral choice. The other seven
 * authored dungeons ended in a room. These fill that gap, and they are data rather than seven
 * more branches of the same {@code if} chain: a boss is a dungeon name, a level, some phases
 * and something the place has to tell you.
 *
 * <p>The reward is the telling. There is no unique drop — the payoff is a
 * {@code RevelationOverlay} card explaining what the dungeon actually was, plus favour with the
 * island's god, which is the quantity the ending is settled on. Boons are not granted here:
 * every one of the seven is already tied to its island's trial quest.
 */
public class DungeonBoss {

    /** One sequential combat in the fight. */
    public static class Phase {
        public String  name;
        public int     level;
        public int     hp;
        public int     attack;
        public int     ac;
        public int     gold;
        public int     xp;
        /** Shown between this phase and the next; null or blank on the last one. */
        public String  transition;
        /** Heals the player a quarter of max HP before the next phase. */
        public boolean healBetween;
    }

    /** What the place says once it stops fighting. */
    public static class Revelation {
        public String       title;
        public String       subtitle;
        public String       body;
        public String       footerHeading;
        public List<String> footerLines;
    }

    /** Authored dungeon this belongs to, e.g. {@code "storm_spire"}. */
    public String dungeon;
    /** Level the boss waits on — always the dungeon's last. */
    public int    level;
    /** Island god who gains favour, as a {@link God} name; may be null for none. */
    public String god;
    /** Player flag set on victory, so the altar only pays out once. */
    public String flag;
    /** Favour awarded to {@link #god} on victory. */
    public int    favor;
    /** Prompt shown when the altar is touched, before the first phase. */
    public String intro;
    /** Logged instead when the boss is already dead. */
    public String defeatedMessage;

    public List<Phase> phases = new ArrayList<>();
    public Revelation  revelation;

    /** Resolves {@link #god} to the enum, or null if unset or unrecognised. */
    public God godOrNull() {
        if (god == null || god.isBlank()) return null;
        try { return God.valueOf(god.trim().toUpperCase()); }
        catch (IllegalArgumentException e) { return null; }
    }

    /** True if this entry has everything the controller needs to run a fight. */
    public boolean isPlayable() {
        return dungeon != null && !dungeon.isBlank() && level > 0
            && phases != null && !phases.isEmpty();
    }
}

package io.cannonforge.retroquest.model;

/**
 * The seven gods of Aqualonia, each ruling one island.
 *
 * <p>The {@link #index} field maps directly to the {@code favorScores[]}
 * array on {@link Player}, so {@code player.getFavor(God.LIRANDEL)} returns
 * the player's standing with Lirandel.
 *
 * <p>Favor is an integer in the range 0–100 (clamped).
 * All players start at 50 (neutral) with every god.
 */
public enum God {
    LIRANDEL (0, "Lirandel",  "Moon, dreams, silver light"),
    PYRALIS  (1, "Pyralis",   "Fire, forge, ambition"),
    ZEPHYRION(2, "Zephyrion", "Wind, storms, change"),
    SYLVANDAR(3, "Sylvandar", "Nature, growth, memory"),
    THALORAX (4, "Thalorax",  "The deep, pressure, inevitability"),
    UMBRYN   (5, "Umbryn",    "Shadow, memory, silence"),
    BELLORAK (6, "Bellorak",  "War, glory, fire-forged iron");

    /** Array index into {@link Player#getFavorScores()}. */
    public final int index;

    /** Human-readable name shown in UI. */
    public final String displayName;

    /** Flavor description of the god's domain. */
    public final String domain;

    God(int index, String displayName, String domain) {
        this.index       = index;
        this.displayName = displayName;
        this.domain      = domain;
    }

    /** Returns the God whose {@link #index} matches, or {@code null} if out of range. */
    public static God byIndex(int index) {
        for (God g : values()) if (g.index == index) return g;
        return null;
    }

    /**
     * In-fiction label for a favor score, shared by the Divine Audience screen,
     * the Cradle of Shards choice screen and the credits so the player reads the
     * same vocabulary everywhere.
     *
     * <p>The thresholds are the ones the endgame actually tests: 80 is the
     * Champion gate at the Cradle, 55 is the floor every god must clear before
     * the True Unbound path will open.
     */
    public static String standingLabel(int favor) {
        if (favor >= 90) return "Exalted";
        if (favor >= 80) return "Champion";
        if (favor >= 65) return "Favored";
        if (favor >= 55) return "Known";
        if (favor >= 45) return "Watched";
        if (favor >= 30) return "Doubted";
        return "Forsworn";
    }
}

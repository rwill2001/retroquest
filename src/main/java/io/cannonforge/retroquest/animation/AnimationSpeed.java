package io.cannonforge.retroquest.animation;

/**
 * Global speed control for the cinematics.
 *
 * <p>Every animation's nominal duration goes through {@link #scale(long)}, so one switch
 * changes them all:
 *
 * <ul>
 *   <li>{@code -Dretroquest.animationSpeed=2} — everything plays at double speed.</li>
 *   <li>{@code -Dretroquest.skipAnimations=true} — cinematics collapse to a single frame.
 *       They still run, still fire their completion callbacks and still hand off state
 *       correctly; they simply do not linger. This is what a scripted playthrough wants,
 *       and it is also the honest way to test the transitions themselves.</li>
 * </ul>
 *
 * <p>Skipping does not bypass the animations' key-waits: a scene that holds for a keypress
 * still holds, it just gets there immediately.
 */
public final class AnimationSpeed {

    private AnimationSpeed() {}

    /** Multiplier applied to every cinematic; >1 is faster. */
    public static final float SPEED = readSpeed();

    /** True when cinematics should collapse to a frame instead of playing out. */
    public static final boolean SKIP =
            Boolean.getBoolean("retroquest.skipAnimations") || SPEED >= 1000f;

    private static float readSpeed() {
        String raw = System.getProperty("retroquest.animationSpeed");
        if (raw == null || raw.isBlank()) return 1f;
        try {
            float f = Float.parseFloat(raw.trim());
            return f > 0f ? f : 1f;
        } catch (NumberFormatException e) {
            System.err.println("[AnimationSpeed] ignoring bad retroquest.animationSpeed=" + raw);
            return 1f;
        }
    }

    /** Scales a nominal duration in milliseconds. Never returns 0 — a zero-length scene divides. */
    public static long scale(long nominalMs) {
        if (SKIP) return 1L;
        if (SPEED == 1f) return nominalMs;
        return Math.max(1L, (long) (nominalMs / SPEED));
    }
}

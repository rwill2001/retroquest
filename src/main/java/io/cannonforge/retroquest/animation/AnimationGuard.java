package io.cannonforge.retroquest.animation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps at most one cinematic live per completion callback.
 *
 * <p>{@code GamePanel} starts every cinematic with {@code field = new X(); field.start();}
 * and nothing cancels the instance that was in the field before — its Swing Timer keeps
 * running and still fires its callback. Stepping on a portal tile twice during a
 * five-second descent therefore ran {@code finishTeleport()} twice.
 *
 * <p>Each animation claims a <i>slot</i> when it starts. Slots are named after the
 * completion callback rather than the class, so animations that finish the same way
 * (a town and a waterfall-cave entry, a dungeon entry and a descent) can never both be
 * live. Claiming a slot cancels whoever held it, suppressing that run's callback.
 *
 * <p>All calls happen on the EDT (start, Swing Timer ticks); the map is concurrent and
 * the mutators synchronized so a stray background caller cannot interleave a claim.
 */
final class AnimationGuard {

    /** Slot names — one per completion callback in {@code Retroquest}. */
    static final String TOWN_ENTRY     = "townEntry";
    static final String TOWN_EXIT      = "townExit";
    static final String DUNGEON_ENTRY  = "dungeonEntry";
    static final String DUNGEON_EXIT   = "dungeonExit";
    static final String SHADOW_DESCENT = "shadowDescent";
    static final String WAR_MARCH      = "warMarch";
    static final String WASH_ASHORE    = "washAshore";
    static final String PLAYER_DEATH   = "playerDeath";
    // Reserved for the endgame cinematics, which have the same double-start hazard.
    static final String ASCENSION      = "ascension";
    static final String ENDGAME        = "endgameCinematic";
    static final String EPILOGUE       = "epilogue";
    static final String CREDITS        = "credits";

    /** An animation that can be stopped without firing its completion callback. */
    interface Cancellable {
        void cancel();
    }

    private static final Map<String, Cancellable> LIVE = new ConcurrentHashMap<>();

    private AnimationGuard() {}

    /** Hands {@code slot} to {@code claimant}, cancelling whatever held it. */
    static synchronized void claim(String slot, Cancellable claimant) {
        Cancellable prev = LIVE.put(slot, claimant);
        if (prev != null && prev != claimant) prev.cancel();
    }

    /** Releases {@code slot}, but only if {@code owner} still holds it. */
    static synchronized void release(String slot, Cancellable owner) {
        LIVE.remove(slot, owner);
    }
}

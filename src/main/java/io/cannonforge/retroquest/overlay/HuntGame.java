package io.cannonforge.retroquest.overlay;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.event.KeyEvent;

/**
 * One island's hunt, played from a hunting ground on the overworld.
 *
 * <p>Deliberately not {@link MiniGame}. That interface was built for the town venues: it takes a
 * bet, pays gold, and sits inside a room the player travelled to on purpose. A hunt is the
 * opposite on every count — it is stumbled onto mid-journey, it pays food, and it has to be over
 * in well under a minute or it becomes the very thing it was added to relieve.
 *
 * <p>Every hunt is a different verb. Lirandel is timing across lanes, Pyralis is reacting to a
 * tell, Zephyrion is leading a moving target, Sylvandar is repeating a sequence, Thalorax is
 * holding a value inside a band, Umbryn is triage against a decay clock, and Bellorak is moving
 * only while nobody is looking. Nothing here is a reskin of anything in the venues.
 *
 * <p>Implementations own their whole play area and may paint anything inside it. The host draws
 * the frame, the title, the hint line and the result — see {@link HuntOverlay}.
 */
interface HuntGame {

    /** Display name, e.g. {@code "Snare Line"}. */
    String name();

    /** One line telling the player what they are about to do, shown before the hunt starts. */
    String briefing();

    /** One short line of controls, shown under the play area throughout. */
    String controls();

    /** The colour this hunt is drawn in — the island's, so the screen reads as that place. */
    Color accent();

    /** Starts a fresh hunt. {@code level} is the player's level, for gentle difficulty scaling. */
    void reset(int level);

    void handleKey(KeyEvent e);

    /** Advances animation and timing. Called every frame while the hunt is up. */
    void update();

    /** Paints the play area. The host has already framed and cleared it. */
    void paint(Graphics2D g, int px, int py, int pw, int ph);

    /** True once the hunt has played out and the host should show the result. */
    boolean isFinished();

    /** Food earned, 0 for a clean miss. The host adds it to the player. */
    int foodYield();

    /** One line describing how it went, shown on the result card. */
    String resultText();
}

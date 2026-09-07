package io.cannonforge.retroquest.overlay;

import java.awt.Graphics2D;
import java.awt.event.KeyEvent;

/**
 * Interface for casino mini-games managed by {@link CasinoOverlay}.
 */
interface MiniGame {
    /** Reset game state for a new round. */
    void reset(int betAmount);
    /** Handle keyboard input. */
    void handleKey(KeyEvent e);
    /** Paint the current game state. */
    void paint(Graphics2D g, int px, int py, int pw, int ph);
    /** Update timed animations. Called every frame while active. */
    void update();
    /** True if the game is waiting for "any key" at a result screen. */
    boolean isShowingResult();
}

package io.cannonforge.retroquest.overlay;

import static io.cannonforge.retroquest.overlay.SkyGamesOverlay.SKY_BLUE;
import static io.cannonforge.retroquest.overlay.SkyGamesOverlay.STORM_GREY;
import static io.cannonforge.retroquest.overlay.SkyGamesOverlay.ELEC_WHITE;
import static io.cannonforge.retroquest.overlay.SkyGamesOverlay.WIND_GOLD;
import static io.cannonforge.retroquest.overlay.SkyGamesOverlay.F_TITLE;
import static io.cannonforge.retroquest.overlay.SkyGamesOverlay.F_MENU;
import static io.cannonforge.retroquest.overlay.SkyGamesOverlay.F_BIG;
import static io.cannonforge.retroquest.overlay.SkyGamesOverlay.drawCentered;
import static io.cannonforge.retroquest.overlay.SkyGamesOverlay.drawCenteredAt;
import static io.cannonforge.retroquest.overlay.OverlayTheme.*;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import io.cannonforge.retroquest.core.MessageLog;
import io.cannonforge.retroquest.core.Retroquest;
import io.cannonforge.retroquest.core.SoundManager;

/**
 * Windchime Symphony: a memory game. The wind plays a growing sequence of
 * directional chimes (Up/Down/Left/Right). The player must repeat each
 * sequence from memory. Each round adds one note. Wrong note = game over.
 * WIS bonus: briefly highlights the next chime on rounds 6+.
 */
class WindchimeSymphonyGame implements MiniGame {

    private enum State { BET, PLAYING, INPUT, HINT, RESULT }

    private static final Color[] CHIME_COLORS = {
        new Color(100, 200, 255),  // Up    — sky blue
        new Color(255, 180, 60),   // Right — sunset gold
        new Color(80, 220, 140),   // Down  — wind green
        new Color(200, 130, 255),  // Left  — storm purple
    };
    private static final Color CHIME_DIM   = new Color(30, 40, 55);
    private static final Color CHIME_FLASH = new Color(255, 255, 255);
    private static final String[] CHIME_LABELS = {"\u2191", "\u2192", "\u2193", "\u2190"}; // ↑ → ↓ ←
    private static final String[] CHIME_NAMES  = {"Up", "Right", "Down", "Left"};

    private static final int CHIME_SIZE   = 60;
    private static final long PLAY_NOTE_MS  = 500;
    private static final long PLAY_GAP_MS   = 200;
    private static final long HINT_MS       = 600;
    private static final long FLASH_MS      = 300;

    /** The symphony ends here. Without a cap the only way out was to lose on purpose. */
    private static final int MAX_ROUNDS = 14;

    // -- Payout ladder --------------------------------------------------------
    // Rounds completed -> multiple of the stake RETURNED (1.0 = break even).
    // Esc banks the current tier, so the player chooses when to stop pushing.
    private static final int   ROUNDS_TOP = MAX_ROUNDS, ROUNDS_GREAT = 11, ROUNDS_GOOD = 8, ROUNDS_WEAK = 6;
    private static final float PAY_TOP = 2.5f, PAY_GREAT = 1.2f, PAY_GOOD = 0.5f, PAY_WEAK = 0.2f;

    private final Retroquest game;
    private final SkyGamesOverlay overlay;
    private final Random rng;

    private State state = State.BET;
    private final List<Integer> sequence = new ArrayList<>();
    private int inputIndex = 0;
    private int round = 0;

    // Playback state (PLAYING)
    private int playIndex = 0;
    private long playTime = 0;
    private boolean playingNote = false;

    // Hint state
    private long hintTime = 0;

    // Flash feedback for player input
    private int flashChime = -1;
    private long flashTime = 0;
    private boolean flashCorrect = false;

    private String resultMessage = "";

    WindchimeSymphonyGame(Retroquest game, SkyGamesOverlay overlay, Random rng) {
        this.game = game;
        this.overlay = overlay;
        this.rng = rng;
    }

    @Override
    public void reset(int betAmount) {
        state = State.BET;
        sequence.clear();
        round = 0;
        resultMessage = "";
        flashChime = -1;
    }

    @Override
    public boolean isShowingResult() { return state == State.RESULT; }

    @Override
    public void handleKey(KeyEvent e) {
        switch (state) {
            case BET    -> handleBetKey(e);
            case INPUT  -> handleInputKey(e);
            case RESULT -> handleResultKey(e);
            default -> {}
        }
    }

    private void handleBetKey(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_LEFT  -> overlay.adjustBet(e.isShiftDown() ? -10 : -1);
            case KeyEvent.VK_RIGHT -> overlay.adjustBet(e.isShiftDown() ?  10 :  1);
            case KeyEvent.VK_ENTER -> {
                if (game.getPlayer().getGold() < overlay.betAmount) {
                    game.log("Not enough gold!", MessageLog.Type.DANGER);
                    return;
                }
                game.getPlayer().addGold(-overlay.betAmount);
                overlay.lastBet = overlay.betAmount;
                SoundManager.getInstance().play("coin");
                startNewRound();
            }
            case KeyEvent.VK_ESCAPE -> { overlay.backToMenu(); SoundManager.getInstance().play("menublip"); }
        }
    }

    private void handleInputKey(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
            // Cash out: bank whatever tier the completed rounds have earned.
            SoundManager.getInstance().play("menublip");
            resolveGame();
            return;
        }
        int chime = switch (e.getKeyCode()) {
            case KeyEvent.VK_UP    -> 0;
            case KeyEvent.VK_RIGHT -> 1;
            case KeyEvent.VK_DOWN  -> 2;
            case KeyEvent.VK_LEFT  -> 3;
            default -> -1;
        };
        if (chime < 0) return;

        flashChime = chime;
        flashTime = System.currentTimeMillis();

        if (chime == sequence.get(inputIndex)) {
            flashCorrect = true;
            SoundManager.getInstance().play("menublip");
            inputIndex++;
            if (inputIndex >= sequence.size()) {
                // Round complete
                round++;
                if (round >= MAX_ROUNDS) { resolveGame(); return; }
                startNewRound();
            }
        } else {
            flashCorrect = false;
            SoundManager.getInstance().play("hurt");
            resolveGame();
        }
    }

    private void handleResultKey(KeyEvent e) {
        overlay.backToMenu();
        if (game.getStatsPanel() != null) game.getStatsPanel().refresh();
    }

    // ── Logic ─────────────────────────────────────────────────────────────────

    private void startNewRound() {
        sequence.add(rng.nextInt(4));
        playIndex = 0;
        playTime = System.currentTimeMillis() + 400; // brief pause before playback
        playingNote = false;
        inputIndex = 0;
        flashChime = -1;

        // WIS hint: show the first note to repeat on rounds 6+
        int wisThreshold = 14;
        if (round >= 3 && game.getPlayer().getWisdom() >= wisThreshold) {
            state = State.HINT;
            hintTime = System.currentTimeMillis();
        } else {
            state = State.PLAYING;
        }
    }

    private void resolveGame() {
        // Payout: number of sequences repeated back without a mistake.
        int payout;
        if      (round >= ROUNDS_TOP)   payout = (int)(overlay.betAmount * PAY_TOP);
        else if (round >= ROUNDS_GREAT) payout = (int)(overlay.betAmount * PAY_GREAT);
        else if (round >= ROUNDS_GOOD)  payout = (int)(overlay.betAmount * PAY_GOOD);
        else if (round >= ROUNDS_WEAK)  payout = (int)(overlay.betAmount * PAY_WEAK);
        else payout = 0;

        if (payout > 0) {
            game.getPlayer().addGold(payout);
            resultMessage = round >= ROUNDS_TOP
                ? "MASTERFUL! Won " + payout + "g!"
                : "You won " + payout + "g!";
            SoundManager.getInstance().play(round >= ROUNDS_TOP ? "victory" : "coin");
            game.log(resultMessage, MessageLog.Type.LOOT);
        } else {
            resultMessage = "The wind falls silent. You lost " + overlay.betAmount + "g.";
            SoundManager.getInstance().play("hurt");
            game.log(resultMessage, MessageLog.Type.DANGER);
        }
        game.getPlayer().recordGamblingResult(payout - overlay.betAmount);
        state = State.RESULT;
    }

    @Override
    public void update() {
        long now = System.currentTimeMillis();

        if (state == State.HINT) {
            if (now - hintTime > HINT_MS) {
                state = State.PLAYING;
                playTime = now + 200;
            }
        }

        if (state == State.PLAYING) {
            if (!playingNote) {
                // Wait for gap
                if (now >= playTime) {
                    playingNote = true;
                    playTime = now;
                    SoundManager.getInstance().play("menublip");
                }
            } else {
                if (now - playTime >= PLAY_NOTE_MS) {
                    playIndex++;
                    playingNote = false;
                    playTime = now + PLAY_GAP_MS;
                    if (playIndex >= sequence.size()) {
                        state = State.INPUT;
                    }
                }
            }
        }

        // Clear flash
        if (flashChime >= 0 && now - flashTime > FLASH_MS) {
            flashChime = -1;
        }
    }

    @Override
    public void paint(Graphics2D g, int px, int py, int pw, int ph) {
        g.setFont(F_TITLE);
        g.setColor(SKY_BLUE);
        drawCentered(g, "WINDCHIME SYMPHONY", px, py + 30, pw);

        switch (state) {
            case BET     -> paintBet(g, px, py, pw, ph);
            case PLAYING -> paintChimes(g, px, py, pw, ph, true);
            case HINT    -> paintHint(g, px, py, pw, ph);
            case INPUT   -> paintChimes(g, px, py, pw, ph, false);
            case RESULT  -> paintResult(g, px, py, pw, ph);
        }
    }

    private void paintBet(Graphics2D g, int px, int py, int pw, int ph) {
        g.setFont(F_MENU);
        g.setColor(TEXT_BRIGHT);
        drawCentered(g, "Repeat the wind's melody!", px, py + 70, pw);
        drawCentered(g, "Sequences grow each round, up to " + MAX_ROUNDS + " rounds.", px, py + 90, pw);
        drawCentered(g, MAX_ROUNDS + " = 2.5x   " + ROUNDS_GREAT + " = 1.2x   " + ROUNDS_GOOD
                + " = 0.5x   " + ROUNDS_WEAK + " = 0.2x   [Esc] banks your tier", px, py + 110, pw);

        g.setFont(F_BIG);
        g.setColor(WIND_GOLD);
        drawCentered(g, "Bet: " + overlay.betAmount + "g", px, py + ph / 2, pw);

        g.setFont(F_ITEM);
        g.setColor(AMBER);
        drawCentered(g, "Gold: " + game.getPlayer().getGold() + "g", px, py + ph / 2 + 30, pw);

        g.setFont(F_SMALL);
        g.setColor(TEXT_DIM);
        drawCentered(g, "WIS " + game.getPlayer().getWisdom() + " (hints at 14+)", px, py + ph / 2 + 60, pw);

        g.setFont(F_KEY);
        g.setColor(TEXT_DIM);
        g.drawString("[Left/Right] Bet   [Shift] x10   [Enter] Start   [Esc] Back", px + 20, py + ph - 20);
    }

    private void paintChimes(Graphics2D g, int px, int py, int pw, int ph, boolean playback) {
        int cx = px + pw / 2;
        int cy = py + ph / 2 - 20;

        // Round info
        g.setFont(F_ITEM);
        g.setColor(AMBER);
        drawCentered(g, "Round " + (round + 1) + "  (" + sequence.size() + " notes)", px, py + 55, pw);

        if (playback) {
            g.setFont(F_MENU);
            g.setColor(STORM_GREY);
            drawCentered(g, "Listen...", px, py + 75, pw);
        } else {
            g.setFont(F_MENU);
            g.setColor(ELEC_WHITE);
            drawCentered(g, "Your turn! (" + inputIndex + "/" + sequence.size() + ")   [Esc] Cash out", px, py + 75, pw);
        }

        // Draw 4 chimes in diamond: Up(top), Right(right), Down(bottom), Left(left)
        int[][] positions = {
            {cx, cy - CHIME_SIZE - 10},  // Up
            {cx + CHIME_SIZE + 10, cy},  // Right
            {cx, cy + CHIME_SIZE + 10},  // Down
            {cx - CHIME_SIZE - 10, cy},  // Left
        };

        long now = System.currentTimeMillis();
        for (int i = 0; i < 4; i++) {
            boolean lit = false;

            // During playback, light up the current note
            if (playback && playingNote && playIndex < sequence.size() && sequence.get(playIndex) == i) {
                lit = true;
            }
            // During input, flash on player press
            if (!playback && flashChime == i && now - flashTime < FLASH_MS) {
                lit = true;
            }

            int bx = positions[i][0] - CHIME_SIZE / 2;
            int by = positions[i][1] - CHIME_SIZE / 2;

            // Chime body
            g.setColor(lit ? CHIME_COLORS[i] : CHIME_DIM);
            g.fillRoundRect(bx, by, CHIME_SIZE, CHIME_SIZE, 12, 12);

            // Border
            g.setColor(lit ? CHIME_FLASH : CHIME_COLORS[i].darker());
            g.drawRoundRect(bx, by, CHIME_SIZE, CHIME_SIZE, 12, 12);

            // Label
            g.setFont(F_BIG);
            g.setColor(lit ? Color.WHITE : CHIME_COLORS[i]);
            drawCenteredAt(g, CHIME_LABELS[i], positions[i][0], positions[i][1] + 7);

            // Name below
            g.setFont(F_SMALL);
            g.setColor(TEXT_DIM);
            drawCenteredAt(g, CHIME_NAMES[i], positions[i][0], positions[i][1] + CHIME_SIZE / 2 + 14);
        }

        // Flash feedback color on wrong note
        if (!playback && flashChime >= 0 && !flashCorrect && now - flashTime < FLASH_MS) {
            g.setFont(F_MENU);
            g.setColor(DANGER);
            drawCentered(g, "WRONG!", px, py + ph - 50, pw);
        }

        g.setFont(F_KEY);
        g.setColor(TEXT_DIM);
        if (!playback) {
            g.drawString("[Arrow keys] Play chime", px + 20, py + ph - 20);
        }
    }

    private void paintHint(Graphics2D g, int px, int py, int pw, int ph) {
        // Show the first note highlighted
        g.setFont(F_MENU);
        g.setColor(WIND_GOLD);
        drawCentered(g, "WIS hint: first note is " + CHIME_NAMES[sequence.get(0)] + "...", px, py + 75, pw);
        paintChimes(g, px, py, pw, ph, false);
    }

    private void paintResult(Graphics2D g, int px, int py, int pw, int ph) {
        g.setFont(F_BIG);
        g.setColor(SKY_BLUE);
        drawCentered(g, "Rounds completed: " + round, px, py + 140, pw);

        g.setFont(F_MENU);
        g.setColor(resultMessage.contains("won") || resultMessage.contains("MASTERFUL") ? GOOD : DANGER);
        drawCentered(g, resultMessage, px, py + 180, pw);

        g.setFont(F_KEY);
        g.setColor(TEXT_DIM);
        drawCentered(g, "[Any key] Continue", px, py + ph - 20, pw);
    }
}

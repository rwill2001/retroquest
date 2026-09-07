package io.cannonforge.retroquest.overlay;

import static io.cannonforge.retroquest.overlay.AbyssalDepthsOverlay.ABYSS_TEAL;
import static io.cannonforge.retroquest.overlay.AbyssalDepthsOverlay.BIOLUM;
import static io.cannonforge.retroquest.overlay.AbyssalDepthsOverlay.F_BIG;
import static io.cannonforge.retroquest.overlay.AbyssalDepthsOverlay.F_MENU;
import static io.cannonforge.retroquest.overlay.AbyssalDepthsOverlay.F_TITLE;
import static io.cannonforge.retroquest.overlay.AbyssalDepthsOverlay.PRESSURE_RED;
import static io.cannonforge.retroquest.overlay.AbyssalDepthsOverlay.drawCentered;
import static io.cannonforge.retroquest.overlay.OverlayTheme.AMBER;
import static io.cannonforge.retroquest.overlay.OverlayTheme.BORDER_COL;
import static io.cannonforge.retroquest.overlay.OverlayTheme.F_ITEM;
import static io.cannonforge.retroquest.overlay.OverlayTheme.F_KEY;
import static io.cannonforge.retroquest.overlay.OverlayTheme.F_SMALL;
import static io.cannonforge.retroquest.overlay.OverlayTheme.TEXT_BRIGHT;
import static io.cannonforge.retroquest.overlay.OverlayTheme.TEXT_DIM;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.util.Random;

import io.cannonforge.retroquest.core.MessageLog;
import io.cannonforge.retroquest.core.Retroquest;
import io.cannonforge.retroquest.core.SoundManager;

/**
 * Leviathan Bone Racing — 5-stage reaction time obstacle course.
 * Each stage: obstacle appears, player picks A (left), D (right), or S (duck)
 * within a shrinking time window. Payout scales with correct answers.
 */
class BoneRacingGame implements MiniGame {

    private enum State { BET, READY, OBSTACLE, STAGE_RESULT, FINAL_RESULT }

    private static final int TOTAL_STAGES = 5;
    private static final long READY_MS = 1200;
    private static final long STAGE_RESULT_MS = 1000;
    private static final Color TIMER_FULL = new Color(0, 200, 180);
    private static final Color TIMER_LOW  = new Color(200, 60, 60);

    private static final String[] OBSTACLE_NAMES = {
        "Coral Wall", "Pressure Vent", "Abyssal Rock", "Kelp Tangle", "Rune-Scarred Shark"
    };
    private static final String[][] OBSTACLE_ART = {
        {"  /|||||\\  ", " /||||||||\\ ", "/||||||||||\\", "||||||||||||", "||||||||||||"},  // Coral Wall
        {"    /\\    ", "   /^^\\   ", "  /~~~~\\  ", " / ~~~~ \\ ", "/~~~~~~~~\\"},          // Pressure Vent
        {"   ___    ", "  /   \\   ", " | ~~~ |  ", " |     |  ", " \\_____/  "},            // Abyssal Rock
        {"  ~|~ ~|~ ", " ~|~~ ~|~ ", "~~|~~~~|~~", " ~|~ ~~|~ ", "  ~|~ ~|~ "},             // Kelp Tangle
        {"    /\\    ", "   /  \\   ", "  / @  \\==", " /______\\ ", "  \\    /  "}           // Shark
    };
    private static final String[] EVASION_NAMES = {"LEFT", "RIGHT", "DUCK"};
    private static final int LEFT = 0, RIGHT = 1, DUCK = 2;
    /**
     * Fixed evasion for each obstacle (indexes match OBSTACLE_NAMES/OBSTACLE_ART).
     * The obstacle itself is the tell — the answer is never printed during the
     * reaction window. The legend on the bet screen lets a racer learn the course.
     */
    private static final int[] OBSTACLE_EVASION = { DUCK, LEFT, RIGHT, DUCK, LEFT };

    // ── Reaction windows ──────────────────────────────────────────────────────
    // Stage n window = REACT_BASE_MS - n * REACT_STEP_MS (1300 → 700ms).
    private static final long REACT_BASE_MS = 1300;
    private static final long REACT_STEP_MS = 150;

    // ── Payout ladder ─────────────────────────────────────────────────────────
    // Multiple of the stake RETURNED (1.0 = break even, 0 = stake lost).
    private static final float PAY_5_OF_5 = 2.5f;
    private static final float PAY_4_OF_5 = 1.2f;
    private static final float PAY_3_OF_5 = 0.5f;

    private final Retroquest game;
    private final AbyssalDepthsOverlay overlay;
    private final Random rng;

    private State state = State.BET;
    private int currentStage;
    private int correctCount;
    private int[] obstacleOrder;     // which obstacle at each stage
    private int[] correctEvasion;    // correct answer per stage
    private boolean[] stageResults;
    private long obstacleShowTime;
    private long readyTime;
    private long stageResultTime;
    private String resultMessage;

    BoneRacingGame(Retroquest game, AbyssalDepthsOverlay overlay, Random rng) {
        this.game = game;
        this.overlay = overlay;
        this.rng = rng;
    }

    @Override
    public void reset(int betAmount) {
        state = State.BET;
        currentStage = 0;
        correctCount = 0;
        resultMessage = "";
        obstacleOrder = new int[TOTAL_STAGES];
        correctEvasion = new int[TOTAL_STAGES];
        stageResults = new boolean[TOTAL_STAGES];
        for (int i = 0; i < TOTAL_STAGES; i++) {
            obstacleOrder[i] = rng.nextInt(OBSTACLE_NAMES.length);
            correctEvasion[i] = OBSTACLE_EVASION[obstacleOrder[i]];
        }
    }

    @Override public boolean isShowingResult() { return state == State.FINAL_RESULT; }

    private long getTimeWindow() {
        return REACT_BASE_MS - currentStage * REACT_STEP_MS;
    }

    @Override
    public void update() {
        if (state == State.READY) {
            if (System.currentTimeMillis() - readyTime > READY_MS) {
                state = State.OBSTACLE;
                obstacleShowTime = System.currentTimeMillis();
            }
        } else if (state == State.OBSTACLE) {
            if (System.currentTimeMillis() - obstacleShowTime > getTimeWindow()) {
                // Time's up — auto-fail
                stageResults[currentStage] = false;
                state = State.STAGE_RESULT;
                stageResultTime = System.currentTimeMillis();
                SoundManager.getInstance().play("hurt");
            }
        } else if (state == State.STAGE_RESULT) {
            if (System.currentTimeMillis() - stageResultTime > STAGE_RESULT_MS) {
                currentStage++;
                if (currentStage >= TOTAL_STAGES) {
                    resolveGame();
                } else {
                    readyTime = System.currentTimeMillis();
                    state = State.READY;
                }
            }
        }
    }

    @Override
    public void handleKey(KeyEvent e) {
        switch (state) {
            case BET          -> handleBetKey(e);
            case READY        -> {} // wait
            case OBSTACLE     -> handleObstacleKey(e);
            case STAGE_RESULT -> {} // auto-advance
            case FINAL_RESULT -> {
                overlay.backToMenu();
                if (game.getStatsPanel() != null) game.getStatsPanel().refresh();
            }
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
                readyTime = System.currentTimeMillis();
                state = State.READY;
            }
            case KeyEvent.VK_ESCAPE -> { overlay.backToMenu(); SoundManager.getInstance().play("menublip"); }
        }
    }

    private void handleObstacleKey(KeyEvent e) {
        int answer = switch (e.getKeyCode()) {
            case KeyEvent.VK_A, KeyEvent.VK_LEFT  -> LEFT;
            case KeyEvent.VK_D, KeyEvent.VK_RIGHT -> RIGHT;
            case KeyEvent.VK_S, KeyEvent.VK_DOWN  -> DUCK;
            default -> -1;
        };
        if (answer < 0) return;

        stageResults[currentStage] = (answer == correctEvasion[currentStage]);
        if (stageResults[currentStage]) {
            correctCount++;
            SoundManager.getInstance().play("menublip");
        } else {
            SoundManager.getInstance().play("hurt");
        }
        state = State.STAGE_RESULT;
        stageResultTime = System.currentTimeMillis();
    }

    private void resolveGame() {
        float multiplier = switch (correctCount) {
            case 5 -> PAY_5_OF_5;
            case 4 -> PAY_4_OF_5;
            case 3 -> PAY_3_OF_5;
            default -> 0f;
        };
        int payout = (int)(overlay.betAmount * multiplier);
        if (payout > 0) {
            game.getPlayer().addGold(payout);
            if (correctCount == 5) {
                resultMessage = "PERFECT RUN! Won " + payout + "g!";
                SoundManager.getInstance().play("victory");
            } else {
                resultMessage = "Won " + payout + "g! (" + correctCount + "/5 correct)";
                SoundManager.getInstance().play("coin");
            }
            game.log(resultMessage, MessageLog.Type.LOOT);
        } else {
            resultMessage = "Crashed! Lost " + overlay.betAmount + "g. (" + correctCount + "/5)";
            SoundManager.getInstance().play("hurt");
            game.log(resultMessage, MessageLog.Type.DANGER);
        }
        game.getPlayer().recordGamblingResult(payout - overlay.betAmount);
        state = State.FINAL_RESULT;
    }

    @Override
    public void paint(Graphics2D g, int px, int py, int pw, int ph) {
        g.setFont(F_TITLE);
        g.setColor(ABYSS_TEAL);
        drawCentered(g, "BONE RACING", px, py + 30, pw);

        switch (state) {
            case BET          -> paintBet(g, px, py, pw, ph);
            case READY        -> paintReady(g, px, py, pw, ph);
            case OBSTACLE     -> paintObstacle(g, px, py, pw, ph);
            case STAGE_RESULT -> paintStageResult(g, px, py, pw, ph);
            case FINAL_RESULT -> paintFinalResult(g, px, py, pw, ph);
        }

        // Stage indicators (except during BET)
        if (state != State.BET) {
            paintStageIndicators(g, px + 20, py + 48, pw - 40);
        }
    }

    private void paintBet(Graphics2D g, int px, int py, int pw, int ph) {
        g.setFont(F_MENU);
        g.setColor(TEXT_BRIGHT);
        drawCentered(g, "Race through 5 obstacles on a leviathan bone!", px, py + 65, pw);

        g.setFont(F_BIG);
        g.setColor(ABYSS_TEAL);
        drawCentered(g, "Bet: " + overlay.betAmount + "g", px, py + ph / 2 - 10, pw);

        g.setFont(F_ITEM);
        g.setColor(AMBER);
        drawCentered(g, "Gold: " + game.getPlayer().getGold() + "g", px, py + ph / 2 + 20, pw);

        g.setFont(F_SMALL);
        g.setColor(TEXT_DIM);
        drawCentered(g, "5/5 = 2.5x   4/5 = 1.2x   3/5 = 0.5x   2/5 or less = lost", px, py + ph / 2 + 55, pw);
        // Course legend: which evasion each obstacle needs. Learn it here, because
        // during the race you only get the obstacle itself, never the answer.
        g.setColor(BIOLUM);
        drawCentered(g, "Learn the course:", px, py + ph / 2 + 76, pw);
        StringBuilder line = new StringBuilder();
        int y = py + ph / 2 + 92;
        for (int i = 0; i < OBSTACLE_NAMES.length; i++) {
            if (line.length() > 0) line.append("    ");
            line.append(OBSTACLE_NAMES[i]).append(" = ").append(EVASION_NAMES[OBSTACLE_EVASION[i]]);
            if (i % 2 == 1 || i == OBSTACLE_NAMES.length - 1) {
                drawCentered(g, line.toString(), px, y, pw);
                line.setLength(0);
                y += 16;
            }
        }

        g.setFont(F_KEY);
        g.setColor(TEXT_DIM);
        g.drawString("[Left/Right] Bet   [Shift] x10   [Enter] Race!   [Esc] Back", px + 20, py + ph - 20);
    }

    private void paintReady(Graphics2D g, int px, int py, int pw, int ph) {
        g.setFont(F_BIG);
        g.setColor(BIOLUM);
        long elapsed = System.currentTimeMillis() - readyTime;
        String countdown = elapsed < 400 ? "3..." : elapsed < 800 ? "2..." : "1...";
        drawCentered(g, "Stage " + (currentStage + 1) + " — " + countdown, px, py + ph / 2, pw);
    }

    private void paintObstacle(Graphics2D g, int px, int py, int pw, int ph) {
        int obstIdx = obstacleOrder[currentStage];

        // Obstacle name
        g.setFont(F_MENU);
        g.setColor(PRESSURE_RED);
        drawCentered(g, OBSTACLE_NAMES[obstIdx] + " ahead!", px, py + 80, pw);

        // ASCII art
        g.setFont(F_ITEM);
        g.setColor(ABYSS_TEAL);
        String[] art = OBSTACLE_ART[obstIdx];
        int artY = py + 110;
        for (String line : art) {
            drawCentered(g, line, px, artY, pw);
            artY += 18;
        }

        // No hint here — the obstacle is the tell. Read it and react.
        g.setFont(F_BIG);
        g.setColor(BIOLUM);
        drawCentered(g, "EVADE!", px, py + 240, pw);

        // Timer bar
        long elapsed = System.currentTimeMillis() - obstacleShowTime;
        long window = getTimeWindow();
        float progress = Math.max(0, 1.0f - (float) elapsed / window);
        int barW = pw - 100;
        int barX = px + 50;
        int barY = py + 270;
        g.setColor(new Color(10, 20, 30));
        g.fillRect(barX, barY, barW, 14);
        Color timerCol = progress > 0.4f ? TIMER_FULL : TIMER_LOW;
        g.setColor(timerCol);
        g.fillRect(barX, barY, (int)(barW * progress), 14);
        g.setColor(BORDER_COL);
        g.drawRect(barX, barY, barW, 14);

        // Controls
        g.setFont(F_KEY);
        g.setColor(TEXT_DIM);
        drawCentered(g, "[A] Left   [D] Right   [S] Duck", px, py + ph - 20, pw);
    }

    private void paintStageResult(Graphics2D g, int px, int py, int pw, int ph) {
        boolean correct = stageResults[currentStage];
        g.setFont(F_BIG);
        g.setColor(correct ? BIOLUM : PRESSURE_RED);
        drawCentered(g, correct ? "DODGED!" : "CRASHED!", px, py + ph / 2 - 10, pw);

        g.setFont(F_MENU);
        g.setColor(TEXT_BRIGHT);
        String needed = "Needed: " + EVASION_NAMES[correctEvasion[currentStage]];
        drawCentered(g, needed, px, py + ph / 2 + 25, pw);
    }

    private void paintFinalResult(Graphics2D g, int px, int py, int pw, int ph) {
        g.setFont(F_BIG);
        g.setColor(correctCount >= 3 ? BIOLUM : PRESSURE_RED);
        drawCentered(g, correctCount + " / " + TOTAL_STAGES + " obstacles dodged", px, py + ph / 2 - 30, pw);

        g.setFont(F_MENU);
        g.setColor(resultMessage.contains("Won") ? ABYSS_TEAL : PRESSURE_RED);
        drawCentered(g, resultMessage, px, py + ph / 2 + 10, pw);

        g.setFont(F_KEY);
        g.setColor(TEXT_DIM);
        drawCentered(g, "[Any key] Continue", px, py + ph - 20, pw);
    }

    private void paintStageIndicators(Graphics2D g, int x, int y, int w) {
        g.setFont(F_SMALL);
        int segW = w / TOTAL_STAGES;
        for (int i = 0; i < TOTAL_STAGES; i++) {
            int sx = x + i * segW;
            boolean done = i < currentStage || (i == currentStage && (state == State.STAGE_RESULT || state == State.FINAL_RESULT));
            if (done) {
                g.setColor(stageResults[i] ? BIOLUM : PRESSURE_RED);
                g.drawString((stageResults[i] ? "\u2713 " : "\u2717 ") + "Stage " + (i + 1), sx, y);
            } else if (i == currentStage) {
                g.setColor(ABYSS_TEAL);
                g.drawString("> Stage " + (i + 1), sx, y);
            } else {
                g.setColor(TEXT_DIM);
                g.drawString("  Stage " + (i + 1), sx, y);
            }
        }
    }
}

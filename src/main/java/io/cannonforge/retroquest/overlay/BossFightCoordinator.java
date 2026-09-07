package io.cannonforge.retroquest.overlay;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.List;

import io.cannonforge.retroquest.core.Fonts;
import io.cannonforge.retroquest.core.Retroquest;
import io.cannonforge.retroquest.core.SoundManager;
import io.cannonforge.retroquest.model.Monster;

/**
 * Coordinates multi-phase boss fights by chaining sequential combats
 * with brief transition cinematics between each phase.
 *
 * <p>Usage:
 * <pre>
 *   BossFightCoordinator coord = new BossFightCoordinator(game);
 *   coord.addPhase(monster1, "Phase transition text!", true);
 *   coord.addPhase(monster2, "Another transition!", true);
 *   coord.addPhase(monster3, null, false);  // final phase, no transition after
 *   coord.start(onComplete);
 * </pre>
 */
public class BossFightCoordinator {

    // ── Phase definition ────────────────────────────────────────────────────
    private record BossPhase(Monster monster, String transitionText, boolean healBetween) {}

    // ── Transition visual constants ─────────────────────────────────────────
    private static final long   TRANSITION_MS = 2500;
    private static final Color  FLASH_COL     = new Color(255, 255, 255);
    private static final Color  TEXT_COL      = new Color(255, 200, 80);
    private static final Color  TEXT_SHADOW   = new Color(0, 0, 0);
    private static final Font   F_TRANS       = Fonts.monoBold(16);

    // ── State ───────────────────────────────────────────────────────────────
    private final Retroquest game;
    private final List<BossPhase> phases = new ArrayList<>();
    private Runnable onAllComplete;
    private boolean  active         = false;

    // Transition animation state
    private boolean  transitionActive = false;
    private long     transitionStart  = 0;
    private String   transitionText   = "";
    private int      revealedChars    = 0;
    private long     lastCharTime     = 0;
    private javax.swing.Timer transitionTimer;

    public BossFightCoordinator(Retroquest game) {
        this.game = game;
    }

    // ── Configuration ───────────────────────────────────────────────────────

    /**
     * Adds a boss phase.
     *
     * @param monster        the monster for this phase
     * @param transitionText text shown between this phase and the next (null for final phase)
     * @param healBetween    if true, heal player 25% between this phase and the next
     */
    public void addPhase(Monster monster, String transitionText, boolean healBetween) {
        phases.add(new BossPhase(monster, transitionText, healBetween));
    }

    // ── Lifecycle ───────────────────────────────────────────────────────────

    public void start(Runnable onComplete) {
        this.onAllComplete = onComplete;
        this.active = true;
        startPhase(0);
    }

    public boolean isActive()           { return active; }
    public boolean isTransitionActive() { return transitionActive; }

    // ── Phase management ────────────────────────────────────────────────────

    private void startPhase(int index) {
        BossPhase phase = phases.get(index);

        game.getGamePanel().startCombat(phase.monster(), true,
            (won, gold, loot, leveled, newLevel) -> {
                if (won && index + 1 < phases.size()) {
                    // More phases remain — heal if configured, then transition
                    if (phase.healBetween()) {
                        int heal = game.getPlayer().getMaxHp() / 4;
                        game.getPlayer().heal(heal);
                        game.log("A moment of respite. +" + heal + " HP.",
                                 io.cannonforge.retroquest.core.MessageLog.Type.GOOD);
                    }
                    String txt = phase.transitionText();
                    if (txt != null && !txt.isEmpty()) {
                        playTransition(txt, () -> startPhase(index + 1));
                    } else {
                        startPhase(index + 1);
                    }
                } else if (won) {
                    // All phases defeated
                    active = false;
                    if (onAllComplete != null) onAllComplete.run();
                } else {
                    // Player fled or died — coordinator stops
                    active = false;
                }
                if (game.getGamePanel() != null) game.getGamePanel().repaint();
            });
    }

    // ── Transition cinematic ────────────────────────────────────────────────

    private void playTransition(String text, Runnable onDone) {
        transitionActive = true;
        transitionText   = text;
        transitionStart  = System.currentTimeMillis();
        revealedChars    = 0;
        lastCharTime     = transitionStart + 400; // delay text start until after flash peak

        SoundManager.getInstance().playPhaseTransition();

        if (transitionTimer != null) transitionTimer.stop();
        transitionTimer = new javax.swing.Timer(16, e -> {
            long elapsed = System.currentTimeMillis() - transitionStart;

            // Advance typewriter
            long now = System.currentTimeMillis();
            while (now - lastCharTime >= 25 && revealedChars < transitionText.length()) {
                revealedChars++;
                lastCharTime += 25;
            }

            if (elapsed > TRANSITION_MS) {
                transitionActive = false;
                transitionTimer.stop();
                onDone.run();
            }
            if (game.getGamePanel() != null) game.getGamePanel().repaint();
        });
        transitionTimer.start();
    }

    /**
     * Paints the phase transition overlay. Called by GamePanel after combat overlay.
     */
    public void paintTransition(Graphics2D g, int W, int H) {
        if (!transitionActive) return;

        long elapsed = System.currentTimeMillis() - transitionStart;
        float p = Math.min(1f, (float) elapsed / TRANSITION_MS);

        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                           RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // ── White flash (peaks at 15%, fades by 40%) ────────────────────────
        float flashP = p < 0.15f ? p / 0.15f : Math.max(0f, 1f - (p - 0.15f) / 0.25f);
        if (flashP > 0) {
            int alpha = (int)(flashP * 220);
            g.setColor(new Color(FLASH_COL.getRed(), FLASH_COL.getGreen(),
                                 FLASH_COL.getBlue(), clamp(alpha)));
            g.fillRect(0, 0, W, H);
        }

        // ── Dark background fade in ────────────────────────────────────────
        float bgFade = Math.min(1f, Math.max(0f, (p - 0.10f) / 0.15f));
        float bgOut  = Math.max(0f, 1f - Math.max(0f, (p - 0.85f) / 0.15f));
        float bgA    = bgFade * bgOut;
        g.setColor(new Color(0, 0, 0, clamp((int)(bgA * 200))));
        g.fillRect(0, 0, W, H);

        // ── Transition text (typewriter, centered) ──────────────────────────
        if (revealedChars > 0 && bgA > 0.1f) {
            String visible = transitionText.substring(0,
                    Math.min(revealedChars, transitionText.length()));

            g.setFont(F_TRANS);
            FontMetrics fm = g.getFontMetrics();

            // Word wrap
            List<String> lines = OverlayTheme.wordWrap(fm, visible, W - 80);
            int lineH  = fm.getHeight() + 4;
            int blockH = lines.size() * lineH;
            int startY = (H - blockH) / 2;

            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                int lx = (W - fm.stringWidth(line)) / 2;
                int ly = startY + i * lineH + fm.getAscent();

                // Shadow
                int sa = clamp((int)(bgA * 200));
                g.setColor(new Color(TEXT_SHADOW.getRed(), TEXT_SHADOW.getGreen(),
                                     TEXT_SHADOW.getBlue(), sa));
                g.drawString(line, lx + 2, ly + 2);

                // Main text
                int ta = clamp((int)(bgA * 255));
                g.setColor(new Color(TEXT_COL.getRed(), TEXT_COL.getGreen(),
                                     TEXT_COL.getBlue(), ta));
                g.drawString(line, lx, ly);
            }

            // Pulsing border lines
            float pulse = 0.5f + 0.5f * (float) Math.sin(elapsed / 200.0);
            int bAlpha = clamp((int)(bgA * pulse * 120));
            g.setColor(new Color(TEXT_COL.getRed(), TEXT_COL.getGreen(),
                                 TEXT_COL.getBlue(), bAlpha));
            g.setStroke(new BasicStroke(2f));
            int lineY1 = startY - 20;
            int lineY2 = startY + blockH + 10;
            g.drawLine(W / 4, lineY1, W * 3 / 4, lineY1);
            g.drawLine(W / 4, lineY2, W * 3 / 4, lineY2);
        }
    }

    private static int clamp(int v) { return Math.max(0, Math.min(255, v)); }
}

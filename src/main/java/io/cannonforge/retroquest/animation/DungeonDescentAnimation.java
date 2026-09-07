package io.cannonforge.retroquest.animation;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import io.cannonforge.retroquest.core.Fonts;
import io.cannonforge.retroquest.core.Retroquest;

/**
 * Brief dungeon descent animation for moving between dungeon levels.
 *
 * Sequence (~1.2 s):
 *   0–40%  : screen fades to black from the edges inward
 *  30–70%  : "LEVEL N" text fades in and holds
 *  60–100% : screen fades back from black, revealing the new level
 */
public class DungeonDescentAnimation implements AnimationGuard.Cancellable {

    private static final long ANIM_MS = AnimationSpeed.scale(1200);

    private static final Color TEXT_COL  = new Color(255, 200, 50);
    private static final Color TEXT_DIM  = new Color(120, 140, 160);
    private static final Font  F_LEVEL   = Fonts.monoBold(22);
    private static final Font  F_SUB     = Fonts.mono    (12);

    private final Retroquest game;
    private final int        targetDepth;
    private boolean          active = false;
    private boolean          fired  = false;
    private boolean paintFailed = false;   // doPaint() threw — stop drawing, but stay active
    private long             startTime;
    private javax.swing.Timer animTimer;

    public DungeonDescentAnimation(Retroquest game, int targetDepth) {
        this.game        = game;
        this.targetDepth = targetDepth;
    }

    public boolean isActive() { return active; }

    /** Stops this run without firing its completion callback (a newer run superseded it). */
    @Override public void cancel() {
        fired  = true;
        active = false;
        if (animTimer != null) animTimer.stop();
    }

    public void start() {
        if (active) return;   // idempotent: never stack a second timer or a second callback
        AnimationGuard.claim(AnimationGuard.DUNGEON_ENTRY, this);

        fired     = false;
        paintFailed = false;
        startTime = System.currentTimeMillis();
        active    = true;

        if (animTimer != null) animTimer.stop();
        animTimer = new javax.swing.Timer(16, e -> {
            if (!active) return;
            long elapsed = System.currentTimeMillis() - startTime;
            if (elapsed > ANIM_MS && !fired) {
                fired  = true;
                active = false;
                animTimer.stop();
                AnimationGuard.release(AnimationGuard.DUNGEON_ENTRY, this);
                game.finishDungeonEntry();
            }
            if (game.getGamePanel() != null) game.getGamePanel().repaint();
        });
        animTimer.start();
    }

    public void paint(Graphics2D g, int W, int H) {
        if (!active || paintFailed) return;
        // A paint failure must not clear `active`: the timer is still running and its
        // completion callback is still pending, so isActive() has to keep saying true
        // (that is what blocks input for the rest of the run). Just stop drawing.
        try { doPaint(g, W, H); } catch (Exception ex) { ex.printStackTrace(); paintFailed = true; }
    }

    private void doPaint(Graphics2D g, int W, int H) {
        float p = Math.min(1f, (float)(System.currentTimeMillis() - startTime) / ANIM_MS);

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,  RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Darkness envelope: fades in 0–40%, holds 40–60%, fades out 60–100%
        float darkness;
        if (p < 0.40f) {
            darkness = p / 0.40f;
        } else if (p < 0.60f) {
            darkness = 1f;
        } else {
            darkness = 1f - (p - 0.60f) / 0.40f;
        }
        darkness = Math.max(0f, Math.min(1f, darkness));

        g.setColor(new Color(0, 0, 0, (int)(255 * darkness)));
        g.fillRect(0, 0, W, H);

        // Text: visible 30–70%
        if (p >= 0.25f && p <= 0.75f) {
            float textAlpha;
            if (p < 0.35f) {
                textAlpha = (p - 0.25f) / 0.10f;
            } else if (p > 0.65f) {
                textAlpha = 1f - (p - 0.65f) / 0.10f;
            } else {
                textAlpha = 1f;
            }
            textAlpha = Math.max(0f, Math.min(1f, textAlpha));
            int alpha = (int)(255 * textAlpha);

            // "LEVEL N"
            g.setFont(F_LEVEL);
            FontMetrics fm = g.getFontMetrics();
            String levelText = "LEVEL  " + targetDepth;
            int tx = (W - fm.stringWidth(levelText)) / 2;
            int ty = H / 2 - 4;

            // Glow shadow
            g.setColor(new Color(TEXT_COL.getRed(), TEXT_COL.getGreen(), TEXT_COL.getBlue(), alpha / 3));
            g.drawString(levelText, tx + 1, ty + 1);
            g.setColor(new Color(TEXT_COL.getRed(), TEXT_COL.getGreen(), TEXT_COL.getBlue(), alpha));
            g.drawString(levelText, tx, ty);

            // "Descending..."
            g.setFont(F_SUB);
            FontMetrics fmS = g.getFontMetrics();
            String sub = "Descending...";
            int sx = (W - fmS.stringWidth(sub)) / 2;
            g.setColor(new Color(TEXT_DIM.getRed(), TEXT_DIM.getGreen(), TEXT_DIM.getBlue(), alpha));
            g.drawString(sub, sx, ty + fm.getDescent() + fmS.getHeight() + 4);
        }
    }
}

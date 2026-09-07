package io.cannonforge.retroquest.core;

import java.awt.Dimension;
import java.awt.Toolkit;

/**
 * Global UI scale factor. Multiply every hardcoded pixel dimension and font
 * point size by this value so the whole window grows proportionally on larger
 * displays.
 *
 * <h2>Resolution order</h2>
 * <ol>
 *   <li>{@code -Dretroquest.uiScale=<factor>} — explicit override (e.g. {@code 1.5}).</li>
 *   <li>Screen-resolution heuristic — picks a bucket from the primary screen's
 *       width (see {@link #bucketFor(int)}).</li>
 *   <li>{@code 1.25} fallback if the screen size can't be read.</li>
 * </ol>
 *
 * Why not query the OS DPI directly? Because on Windows the standard Java APIs
 * for reading the display scale only work when the process is marked DPI-aware,
 * which the bundled-JRE + batch-file launcher doesn't guarantee. Screen width
 * is always reliable, and a handful of buckets is close enough for this game.
 */
public final class DisplayScale {
    private DisplayScale() {}

    static {
        // We do our own scaling — disable Java2D's auto-scaler so we don't
        // stack with it. Setting here (before any Swing class is referenced,
        // since this class is touched from Retroquest.main before SwingUtilities)
        // guarantees the property takes effect regardless of import order.
        if (System.getProperty("sun.java2d.uiScale") == null) {
            System.setProperty("sun.java2d.uiScale", "1.0");
        }
    }

    // ── Window footprint ─────────────────────────────────────────────────────
    // The packed window is exactly linear in SCALE, because all three panels size themselves
    // through scaled(). These are their preferred sizes at scale 1.0, laid out by BorderLayout:
    // GamePanel (CENTER) beside StatsPanel (EAST), with MessageLog (SOUTH) underneath.
    // Keep in step with GamePanel, StatsPanel and MessageLog or maxScaleThatFits() drifts.
    private static final int BASE_W = 24 * 32 + 230;              // game panel + stats column
    private static final int BASE_H = Math.max(18 * 32, 600) + 90; // taller of the two + log

    /** Window chrome — title bar and borders. OS-drawn, so it does not scale with the game. */
    private static final int CHROME_W = 16;
    private static final int CHROME_H = 40;

    public static final double SCALE = resolveScale();

    private static double resolveScale() {
        double fit = maxScaleThatFits();

        String override = System.getProperty("retroquest.uiScale");
        if (override != null && !override.isBlank()) {
            String raw = override.trim();
            // "fit" asks for the largest scale the display can actually show.
            if (raw.equalsIgnoreCase("fit") || raw.equalsIgnoreCase("max")) {
                double v = clamp(fit);
                log("fit to screen", v, -1, -1);
                return v;
            }
            try {
                double asked = clamp(Double.parseDouble(raw));
                double v = Math.min(asked, fit);
                if (v < asked) {
                    System.out.println("[RetroQuest] Requested UI scale " + asked
                            + " would not fit this display — using " + round2(v) + " instead.");
                }
                log("override (-Dretroquest.uiScale)", v, -1, -1);
                return v;
            } catch (NumberFormatException e) {
                System.err.println("[RetroQuest] Invalid -Dretroquest.uiScale value: "
                        + override + " — falling back to auto-detect");
            }
        }

        try {
            Dimension s = Toolkit.getDefaultToolkit().getScreenSize();
            // The bucket is a preference; the fit is a hard ceiling. Without the ceiling, pack()
            // happily asks for a window taller than the display, the OS clamps it, and the fixed
            // layout is silently cropped — the stats column loses its right-hand edge.
            double v = Math.min(bucketFor(s.width), fit);
            log("screen-resolution bucket", v, s.width, s.height);
            return v;
        } catch (Throwable t) {
            log("fallback (screen query failed)", 1.25, -1, -1);
            return 1.25;
        }
    }

    /**
     * Largest scale whose packed window still fits the usable desktop.
     *
     * <p>Uses {@code getMaximumWindowBounds} rather than the raw screen size so the taskbar is
     * excluded — a window that fits the screen but not the desktop still gets clamped and cropped.
     */
    private static double maxScaleThatFits() {
        try {
            java.awt.Rectangle usable = java.awt.GraphicsEnvironment
                    .getLocalGraphicsEnvironment().getMaximumWindowBounds();
            double byWidth  = (usable.width  - CHROME_W) / (double) BASE_W;
            double byHeight = (usable.height - CHROME_H) / (double) BASE_H;
            return Math.max(0.75, Math.min(byWidth, byHeight));
        } catch (Throwable t) {
            return 3.0;   // no display info: do not stand in the way of an explicit request
        }
    }

    /** Largest scale this display can show, for callers offering a "fit" option. */
    public static double maxFitScale() { return clamp(maxScaleThatFits()); }

    private static double round2(double v) { return Math.round(v * 100.0) / 100.0; }

    /**
     * Maps primary-screen width to a scale bucket. Buckets are conservative —
     * if you want something else, use {@code -Dretroquest.uiScale=<value>}.
     */
    private static double bucketFor(int screenWidth) {
        if (screenWidth >= 3440) return 1.75;  // 4K ultrawide and up
        if (screenWidth >= 2560) return 1.50;  // 1440p / 1600p
        if (screenWidth >= 1920) return 1.25;  // 1080p / 1200p
        return 1.00;                            // below 1080p
    }

    private static double clamp(double v) {
        return Math.max(0.75, Math.min(3.0, v));
    }

    private static void log(String source, double scale, int w, int h) {
        String dims = (w > 0 && h > 0) ? (" — screen " + w + "×" + h) : "";
        System.out.println("[RetroQuest] UI scale: " + scale + " (" + source + ")"
                + dims + " — override with -Dretroquest.uiScale=<factor>");
    }

    public static int scaled(int v) { return (int) Math.round(v * SCALE); }
}

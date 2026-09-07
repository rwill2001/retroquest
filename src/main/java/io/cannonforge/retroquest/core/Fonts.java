package io.cannonforge.retroquest.core;

import java.awt.Font;

/**
 * Centralized font factory. All fonts in the game should flow through here so
 * a single {@link DisplayScale#SCALE} bump resizes every piece of text.
 */
public final class Fonts {
    private Fonts() {}

    public static Font mono(int pt)     { return new Font("Monospaced", Font.PLAIN, DisplayScale.scaled(pt)); }
    public static Font monoBold(int pt) { return new Font("Monospaced", Font.BOLD,  DisplayScale.scaled(pt)); }
    public static Font monoItalic(int pt){ return new Font("Monospaced", Font.ITALIC, DisplayScale.scaled(pt)); }

    public static Font of(String family, int style, int pt) {
        return new Font(family, style, DisplayScale.scaled(pt));
    }
}

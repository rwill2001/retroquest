package io.cannonforge.retroquest.editor;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.plaf.basic.BasicScrollBarUI;

/**
 * CRT-styled scrollbar used across all RetroForge editor dialogs.
 * Green thumb on dark track with no arrow buttons.
 */
public class RetroScrollBarUI extends BasicScrollBarUI {

    @Override protected void configureScrollBarColors() {
        thumbColor = new Color(0, 100, 60);
        trackColor = new Color(10, 18, 14);
    }

    @Override protected JButton createDecreaseButton(int o) { return zeroButton(); }
    @Override protected JButton createIncreaseButton(int o) { return zeroButton(); }

    private JButton zeroButton() {
        JButton b = new JButton();
        b.setPreferredSize(new Dimension(0, 0));
        return b;
    }

    @Override protected void paintThumb(Graphics g, JComponent c, Rectangle r) {
        ((Graphics2D) g).setColor(new Color(0, 140, 80));
        ((Graphics2D) g).fillRoundRect(r.x + 2, r.y + 2, r.width - 4, r.height - 4, 4, 4);
    }

    @Override protected void paintTrack(Graphics g, JComponent c, Rectangle r) {
        g.setColor(new Color(8, 14, 10));
        g.fillRect(r.x, r.y, r.width, r.height);
    }
}

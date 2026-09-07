package io.cannonforge.retroquest.editor;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;

/**
 * Shared CRT-style color palette, fonts, and widget factories
 * for all RetroForge editor dialogs.
 */
public final class EditorTheme {

    private EditorTheme() {} // utility class

    // ── CRT Palette ─────────────────────────────────────────────────────────
    public static final Color BG          = new Color(10,  12,  16);
    public static final Color PANEL_BG    = new Color(16,  20,  28);
    public static final Color BORDER_COL  = new Color(40,  55,  75);
    public static final Color PHOSPHOR    = new Color( 0, 255, 120);
    public static final Color PHOSPHOR2   = new Color( 0, 200, 255);
    public static final Color PHOSPHOR3   = new Color(255, 200,  50);
    public static final Color AMBER       = new Color(255, 160,  30);
    public static final Color TEXT_DIM    = new Color(120, 140, 160);
    public static final Color TEXT_BRIGHT = new Color(210, 230, 255);
    public static final Color ACCENT      = new Color( 0, 200, 255);
    public static final Color SEL_BG      = new Color( 0,  60,  90);
    public static final Color DANGER      = new Color(220,  60,  60);

    // ── Fonts ───────────────────────────────────────────────────────────────
    public static final Font MONO_LG = new Font("Monospaced", Font.BOLD,  14);
    public static final Font MONO_MD = new Font("Monospaced", Font.BOLD,  12);
    public static final Font MONO_SM = new Font("Monospaced", Font.PLAIN, 11);
    public static final Font MONO_XS = new Font("Monospaced", Font.PLAIN, 10);

    // ── Common colors used in title/status bars ─────────────────────────────
    private static final Color TITLE_BG  = new Color(8, 12, 20);
    private static final Color STATUS_BG = new Color(6,  9, 14);

    // ── Title Bar ───────────────────────────────────────────────────────────

    /** Builds a standard editor title bar with a colored title and dim subtitle. */
    public static JPanel buildTitleBar(String titleText, Color titleColor, String subtitle) {
        return buildTitleBar(titleText, titleColor, subtitle, TEXT_DIM, MONO_XS);
    }

    /** Builds a title bar with custom subtitle color and font. */
    public static JPanel buildTitleBar(String titleText, Color titleColor,
                                       String subtitle, Color subColor, Font subFont) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(TITLE_BG);
        p.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_COL));
        p.setPreferredSize(new Dimension(0, 42));

        JLabel title = makeLabel("\u25c8  " + titleText, titleColor);
        title.setFont(MONO_LG);
        title.setBorder(BorderFactory.createEmptyBorder(0, 16, 0, 0));

        JLabel sub = makeLabel(subtitle, subColor);
        sub.setFont(subFont);
        sub.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 16));

        p.add(title, BorderLayout.WEST);
        p.add(sub,   BorderLayout.EAST);
        return p;
    }

    // ── Status Bar ──────────────────────────────────────────────────────────

    /** Builds a standard status bar. The returned label is the status label (set its text to update). */
    public static JPanel buildStatusBar(JLabel statusLabel, String hintText) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(STATUS_BG);
        p.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER_COL));
        p.setPreferredSize(new Dimension(0, 26));

        statusLabel.setText("  Ready");
        statusLabel.setForeground(TEXT_DIM);
        statusLabel.setFont(MONO_XS);
        p.add(statusLabel, BorderLayout.WEST);

        JLabel hint = makeLabel(hintText, TEXT_DIM);
        hint.setFont(MONO_XS);
        p.add(hint, BorderLayout.EAST);
        return p;
    }

    /** Updates a status label with the standard prefix. */
    public static void setStatus(JLabel statusLabel, String msg) {
        if (statusLabel != null) statusLabel.setText("  " + msg);
    }

    // ── Widget Factories ────────────────────────────────────────────────────

    public static JLabel makeLabel(String txt, Color c) {
        JLabel l = new JLabel(txt);
        l.setForeground(c);
        l.setFont(MONO_SM);
        return l;
    }

    public static JLabel sectionLabel(String txt) {
        JLabel l = new JLabel(txt);
        l.setForeground(ACCENT);
        l.setFont(new Font("Monospaced", Font.BOLD, 10));
        return l;
    }

    public static JTextField darkField(int cols) {
        JTextField f = new JTextField(cols);
        f.setBackground(TITLE_BG);
        f.setForeground(PHOSPHOR);
        f.setCaretColor(PHOSPHOR);
        f.setFont(MONO_SM);
        f.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_COL),
            BorderFactory.createEmptyBorder(3, 6, 3, 6)));
        return f;
    }

    public static <T> JComboBox<T> darkCombo(T[] items) {
        JComboBox<T> cb = new JComboBox<>(items);
        cb.setBackground(PANEL_BG);
        cb.setForeground(TEXT_BRIGHT);
        cb.setFont(MONO_SM);
        return cb;
    }

    public static JSpinner darkSpinner(int val, int min, int max, int step) {
        JSpinner s = new JSpinner(new SpinnerNumberModel(val, min, max, step));
        s.setFont(MONO_SM);
        JComponent ed = s.getEditor();
        if (ed instanceof JSpinner.DefaultEditor de) {
            de.getTextField().setBackground(TITLE_BG);
            de.getTextField().setForeground(PHOSPHOR);
            de.getTextField().setCaretColor(PHOSPHOR);
            de.getTextField().setFont(MONO_SM);
        }
        return s;
    }

    public static JTextArea darkTextArea(int rows, int cols) {
        JTextArea ta = new JTextArea(rows, cols);
        ta.setBackground(TITLE_BG);
        ta.setForeground(PHOSPHOR);
        ta.setCaretColor(PHOSPHOR);
        ta.setFont(MONO_SM);
        ta.setLineWrap(true);
        ta.setWrapStyleWord(true);
        ta.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        return ta;
    }

    public static JCheckBox darkCheckBox(String label) {
        JCheckBox cb = new JCheckBox(label, true);
        cb.setBackground(TITLE_BG);
        cb.setForeground(PHOSPHOR);
        cb.setFont(MONO_SM);
        cb.setFocusPainted(false);
        return cb;
    }

    public static JButton retroButton(String txt) {
        @SuppressWarnings("serial")
        JButton b = new JButton(txt) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setColor(getModel().isPressed() ? new Color(0, 30, 50) : getBackground());
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setColor(getForeground());
                g2.setFont(getFont());
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(getText(),
                    (getWidth() - fm.stringWidth(getText())) / 2,
                    (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
            }
        };
        b.setFont(MONO_XS);
        b.setBackground(new Color(12, 20, 32));
        b.setForeground(TEXT_BRIGHT);
        b.setBorder(BorderFactory.createLineBorder(BORDER_COL));
        b.setFocusPainted(false);
        b.setContentAreaFilled(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    public static JButton bigButton(String txt, Color fg, Color bg) {
        JButton b = retroButton(txt);
        b.setFont(MONO_MD);
        b.setForeground(fg);
        b.setBackground(bg);
        b.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(fg.darker(), 1),
            BorderFactory.createEmptyBorder(5, 14, 5, 14)));
        return b;
    }
}

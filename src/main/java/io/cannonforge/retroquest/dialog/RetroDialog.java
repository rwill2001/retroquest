package io.cannonforge.retroquest.dialog;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Window;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

import io.cannonforge.retroquest.core.DisplayScale;
import io.cannonforge.retroquest.core.Fonts;

/**
 * Drop-in replacement for JOptionPane that renders with the shared CRT palette.
 *
 * Usage is identical to JOptionPane:
 *   RetroDialog.showMessage(parent, "You found gold!");
 *   int c = RetroDialog.showConfirm(parent, "Rest for 15 gold?", "The Inn");
 *   int c = RetroDialog.showOptions(parent, "What do you do?", "Choose", opts);
 *   String s = RetroDialog.showInput(parent, "Enter name:", "Name");
 */
public class RetroDialog {

    // ── CRT Palette ───────────────────────────────────────────────────────────
    static final Color BG          = new Color(10,  12,  16);
    static final Color PANEL_BG    = new Color(16,  20,  28);
    static final Color BORDER_COL  = new Color(40,  55,  75);
    static final Color PHOSPHOR    = new Color( 0, 255, 120);
    static final Color PHOSPHOR2   = new Color( 0, 200, 255);
    static final Color PHOSPHOR3   = new Color(255, 200,  50);
    static final Color AMBER       = new Color(255, 160,  30);
    static final Color TEXT_DIM    = new Color(120, 140, 160);
    static final Color TEXT_BRIGHT = new Color(210, 230, 255);
    static final Color ACCENT      = new Color( 0, 200, 255);
    static final Color DANGER      = new Color(220,  60,  60);

    static final Font MONO_LG = Fonts.monoBold(14);
    static final Font MONO_MD = Fonts.monoBold(12);
    static final Font MONO_SM = Fonts.mono    (11);
    static final Font MONO_XS = Fonts.mono    (10);

    // ── Public API — mirrors JOptionPane ─────────────────────────────────────

    /** Simple message dialog (replaces JOptionPane.showMessageDialog). */
    public static void showMessage(Component parent, String message) {
        showMessage(parent, message, null, false);
    }

    public static void showMessage(Component parent, String message, String title) {
        showMessage(parent, message, title, false);
    }

    public static void showMessage(Component parent, String message, String title, boolean warning) {
        JDialog d = buildDialog(parent, title != null ? title : "RETROQUEST", warning ? DANGER : PHOSPHOR);
        JPanel content = buildContentPanel(message, warning ? DANGER : PHOSPHOR2);

        JButton okBtn = bigButton("  OK  ", PHOSPHOR, new Color(0, 40, 20));
        okBtn.addActionListener(e -> d.dispose());

        JPanel btnRow = btnRow(okBtn);
        assemble(d, content, btnRow);
        d.setVisible(true);
    }

    /** Yes/No confirm (replaces JOptionPane.showConfirmDialog — returns YES_OPTION or NO_OPTION). */
    public static int showConfirm(Component parent, String message, String title) {
        int[] result = {JOptionPane.NO_OPTION};
        JDialog d = buildDialog(parent, title != null ? title : "RETROQUEST", PHOSPHOR3);
        JPanel content = buildContentPanel(message, TEXT_BRIGHT);

        JButton yesBtn = bigButton("  YES  ", PHOSPHOR,  new Color(0, 40, 20));
        JButton noBtn  = bigButton("  NO   ", DANGER,    new Color(40,  0,  0));
        yesBtn.addActionListener(e -> { result[0] = JOptionPane.YES_OPTION; d.dispose(); });
        noBtn.addActionListener(e  -> { result[0] = JOptionPane.NO_OPTION;  d.dispose(); });

        assemble(d, content, btnRow(yesBtn, noBtn));
        d.setVisible(true);
        return result[0];
    }

    /**
     * Option chooser (replaces JOptionPane.showOptionDialog).
     * Returns index of chosen option, or -1 if closed.
     */
    public static int showOptions(Component parent, String message, String title, String[] options) {
        int[] result = {-1};
        JDialog d = buildDialog(parent, title != null ? title : "RETROQUEST", PHOSPHOR3);
        JPanel content = buildContentPanel(message, TEXT_BRIGHT);

        JButton[] btns = new JButton[options.length];
        for (int i = 0; i < options.length; i++) {
            final int idx = i;
            Color fg = (i == 0) ? PHOSPHOR : (i == options.length - 1) ? TEXT_DIM : PHOSPHOR3;
            Color bg = (i == 0) ? new Color(0, 40, 20) : (i == options.length - 1)
                ? new Color(20, 20, 20) : new Color(40, 35, 0);
            btns[i] = bigButton("  " + options[i] + "  ", fg, bg);
            btns[i].addActionListener(e -> { result[0] = idx; d.dispose(); });
        }

        assemble(d, content, btnRow(btns));
        d.setVisible(true);
        return result[0];
    }

    /** Input dialog (replaces JOptionPane.showInputDialog). Returns null if cancelled. */
    public static String showInput(Component parent, String message, String title) {
        String[] result = {null};
        JDialog d = buildDialog(parent, title != null ? title : "RETROQUEST", PHOSPHOR2);
        JPanel content = buildContentPanel(message, TEXT_BRIGHT);

        JTextField field = new JTextField(20);
        field.setBackground(new Color(8, 12, 20));
        field.setForeground(PHOSPHOR);
        field.setCaretColor(PHOSPHOR);
        field.setFont(MONO_SM);
        field.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_COL),
            BorderFactory.createEmptyBorder(4, 8, 4, 8)));

        JPanel fieldWrap = new JPanel(new BorderLayout());
        fieldWrap.setBackground(PANEL_BG);
        fieldWrap.setBorder(BorderFactory.createEmptyBorder(0, 24, 8, 24));
        fieldWrap.add(field, BorderLayout.CENTER);
        content.add(fieldWrap, BorderLayout.SOUTH);

        JButton okBtn     = bigButton("  OK  ",     PHOSPHOR, new Color(0, 40, 20));
        JButton cancelBtn = bigButton("  CANCEL  ", DANGER,   new Color(40, 0, 0));
        okBtn.addActionListener(e     -> { result[0] = field.getText(); d.dispose(); });
        cancelBtn.addActionListener(e -> { result[0] = null; d.dispose(); });

        // Enter key submits
        field.addActionListener(e -> { result[0] = field.getText(); d.dispose(); });

        assemble(d, content, btnRow(okBtn, cancelBtn));
        SwingUtilities.invokeLater(field::requestFocusInWindow);
        d.setVisible(true);
        return result[0];
    }

    // ── Builder helpers ───────────────────────────────────────────────────────

    private static JDialog buildDialog(Component parent, String title, Color accentColor) {
        Window owner = parent instanceof Window ? (Window) parent
            : SwingUtilities.getWindowAncestor(parent);
        JDialog d = (owner instanceof Frame)
            ? new JDialog((Frame) owner, title, true)
            : new JDialog((Dialog) owner, title, true);

        d.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        d.setLayout(new BorderLayout(0, 0));
        d.getContentPane().setBackground(BG);

        // ── Title bar ──
        JPanel titleBar = new JPanel(new BorderLayout());
        titleBar.setBackground(new Color(8, 12, 20));
        titleBar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_COL));
        titleBar.setPreferredSize(new Dimension(0, DisplayScale.scaled(38)));

        JLabel titleLbl = new JLabel("  \u25c8  " + title.toUpperCase());
        titleLbl.setFont(MONO_MD);
        titleLbl.setForeground(accentColor);
        titleBar.add(titleLbl, BorderLayout.WEST);

        d.add(titleBar, BorderLayout.NORTH);

        // ── Outer border ──
        d.getRootPane().setBorder(BorderFactory.createLineBorder(BORDER_COL, 1));

        return d;
    }

    private static JPanel buildContentPanel(String message, Color textColor) {
        JPanel p = new JPanel(new BorderLayout(0, 0));
        p.setBackground(PANEL_BG);
        p.setBorder(BorderFactory.createEmptyBorder(20, 28, 16, 28));

        // Render message — support multi-line via \n
        String[] lines = message.split("\n");
        JPanel textPanel = new JPanel();
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
        textPanel.setOpaque(false);

        for (String line : lines) {
            JLabel lbl = new JLabel(line.isEmpty() ? " " : line);
            lbl.setFont(line.isEmpty() ? MONO_XS : MONO_SM);
            lbl.setForeground(line.startsWith("+") || line.startsWith("You found") ? PHOSPHOR3
                : line.startsWith("You lose") || line.startsWith("Wrong") ? DANGER
                : line.startsWith("HP") || line.startsWith("+") ? PHOSPHOR
                : textColor);
            textPanel.add(lbl);
            textPanel.add(Box.createVerticalStrut(2));
        }

        p.add(textPanel, BorderLayout.CENTER);
        return p;
    }

    private static JPanel btnRow(JButton... buttons) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        row.setBackground(new Color(8, 12, 20));
        row.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER_COL));
        for (JButton b : buttons) row.add(b);
        return row;
    }

    private static void assemble(JDialog d, JPanel content, JPanel buttons) {
        d.add(content, BorderLayout.CENTER);
        d.add(buttons, BorderLayout.SOUTH);
        d.pack();
        d.setMinimumSize(new Dimension(Math.max(d.getWidth(), 360), d.getHeight()));
        d.setLocationRelativeTo(d.getOwner());
    }

    private static JButton bigButton(String txt, Color fg, Color bg) {
        @SuppressWarnings("serial")
        JButton b = new JButton(txt) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setColor(getModel().isPressed() ? bg.darker() : getBackground());
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setColor(getForeground());
                g2.setFont(getFont());
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(getText(),
                    (getWidth() - fm.stringWidth(getText())) / 2,
                    (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
            }
        };
        b.setFont(MONO_MD);
        b.setBackground(bg);
        b.setForeground(fg);
        b.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(fg.darker(), 1),
            BorderFactory.createEmptyBorder(6, 16, 6, 16)));
        b.setFocusPainted(false);
        b.setContentAreaFilled(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }
}

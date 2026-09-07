package io.cannonforge.retroquest.dialog;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.MultipleGradientPaint;
import java.awt.RadialGradientPaint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;

import io.cannonforge.retroquest.core.DisplayScale;
import io.cannonforge.retroquest.core.Fonts;
import io.cannonforge.retroquest.core.Retroquest;
import io.cannonforge.retroquest.core.SoundManager;

/**
 * Modal startup dialog shown before the game begins.
 *
 * <p>Presents New Game and Load Game options in a CRT-styled panel with
 * scanline and vignette effects. Arrow keys move selection, Enter confirms,
 * mouse click/hover also work. Escape exits the application.
 */
@SuppressWarnings("serial")
public class StartMenuDialog extends JDialog {

    private static final Color BG         = new Color( 10,  12,  16);
    private static final Color BORDER     = new Color( 35,  50,  72);
    private static final Color PHOSPHOR   = new Color(  0, 255, 120);
    private static final Color CYAN_ACC   = new Color(  0, 200, 255);
    private static final Color AMBER      = new Color(255, 200,  50);
    private static final Color TEXT_DIM   = new Color( 80, 100, 130);

    // 0 = New Game, 1 = Load Game
    private int selectedIndex = 0;

    public StartMenuDialog(Retroquest game) {
        super(game, "RETROQUEST", true);
        setSize(DisplayScale.scaled(540), DisplayScale.scaled(400));
        setLocationRelativeTo(game);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { System.exit(0); }
        });
        setResizable(false);

        JPanel main = new JPanel(new BorderLayout(0, 0)) {
            @Override protected void paintComponent(Graphics g0) {
                super.paintComponent(g0);
                Graphics2D g = (Graphics2D) g0;
                g.setPaint(new GradientPaint(0,0,new Color(8,10,18),0,getHeight(),BG));
                g.fillRect(0,0,getWidth(),getHeight());
                g.setColor(new Color(0,0,0,22));
                for (int y = 0; y < getHeight(); y += 2) g.drawLine(0,y,getWidth(),y);
                float[] fractions = {0f, 0.6f, 1f};
                Color[] colours = {new Color(0,0,0,0), new Color(0,0,0,0), new Color(0,0,0,140)};
                g.setPaint(new RadialGradientPaint(
                    new Rectangle(0,0,getWidth(),getHeight()),
                    fractions, colours,
                    MultipleGradientPaint.CycleMethod.NO_CYCLE));
                g.fillRect(0,0,getWidth(),getHeight());
            }
        };
        main.setOpaque(false);
        main.setBorder(BorderFactory.createEmptyBorder(50, 50, 40, 50));

        // ── Title block ──────────────────────────────────────────────────────
        JPanel titleBlock = new JPanel() {
            @Override protected void paintComponent(Graphics g0) { /* transparent */ }
            @Override public boolean isOpaque() { return false; }
        };
        titleBlock.setLayout(new BoxLayout(titleBlock, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("RETROQUEST") {
            @Override protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0;
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g.setFont(getFont());
                FontMetrics fm = g.getFontMetrics();
                String t = getText();
                int tx = (getWidth() - fm.stringWidth(t)) / 2;
                int ty = fm.getAscent();
                for (int r = 12; r >= 1; r--) {
                    g.setColor(new Color(AMBER.getRed(), AMBER.getGreen(), AMBER.getBlue(),
                        (int)(18 * (1f - r / 13f))));
                    g.drawString(t, tx, ty + r/3);
                }
                g.setColor(AMBER);
                g.drawString(t, tx, ty);
            }
        };
        title.setFont(Fonts.monoBold(48));
        title.setForeground(AMBER);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        title.setPreferredSize(new Dimension(DisplayScale.scaled(440), DisplayScale.scaled(72)));
        title.setMaximumSize(new Dimension(DisplayScale.scaled(440), DisplayScale.scaled(72)));

        JLabel subtitle = new JLabel("A  CLASSIC  DUNGEON  CRAWL  REVIVAL");
        subtitle.setForeground(CYAN_ACC);
        subtitle.setFont(Fonts.mono(13));
        subtitle.setAlignmentX(Component.CENTER_ALIGNMENT);

        JSeparator sep = new JSeparator() {
            @Override protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0;
                g.setPaint(new GradientPaint(0,0,new Color(0,0,0,0),
                    getWidth()/2, 0, BORDER, false));
                g.fillRect(0, 0, getWidth()/2, getHeight());
                g.setPaint(new GradientPaint(getWidth()/2,0,BORDER,
                    getWidth(),0, new Color(0,0,0,0), false));
                g.fillRect(getWidth()/2, 0, getWidth()/2, getHeight());
            }
        };
        sep.setPreferredSize(new Dimension(DisplayScale.scaled(440), 1));
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        sep.setAlignmentX(Component.CENTER_ALIGNMENT);

        titleBlock.add(title);
        titleBlock.add(Box.createVerticalStrut(8));
        titleBlock.add(subtitle);
        titleBlock.add(Box.createVerticalStrut(20));
        titleBlock.add(sep);
        main.add(titleBlock, BorderLayout.NORTH);

        // ── Buttons ──────────────────────────────────────────────────────────
        JPanel buttons = new JPanel(new GridLayout(2, 1, 0, 14)) {
            @Override public boolean isOpaque() { return false; }
        };
        buttons.setBorder(BorderFactory.createEmptyBorder(28, 20, 0, 20));

        JButton newGameBtn  = makeButton(0, "  \u25b6  NEW GAME  ",  PHOSPHOR, new Color(0,40,20), e -> {
            dispose();
            new CharacterCreationDialog(game);
        });
        JButton loadGameBtn = makeButton(1, "  \u25b6  LOAD GAME  ", CYAN_ACC, new Color(0,20,40), e -> {
            setVisible(false);
            game.loadGame();
        });

        JButton[] btns = { newGameBtn, loadGameBtn };

        buttons.add(newGameBtn);
        buttons.add(loadGameBtn);
        main.add(buttons, BorderLayout.CENTER);

        // ── Footer ───────────────────────────────────────────────────────────
        JLabel ver = new JLabel("v1.0.0  \u00b7  \u00a9 2026 CannonForge");
        ver.setForeground(TEXT_DIM);
        ver.setFont(Fonts.mono(10));
        ver.setHorizontalAlignment(SwingConstants.CENTER);
        JPanel footer = new JPanel(new BorderLayout()) {
            @Override public boolean isOpaque() { return false; }
        };
        footer.add(ver, BorderLayout.CENTER);
        footer.setBorder(BorderFactory.createEmptyBorder(14, 0, 0, 0));
        main.add(footer, BorderLayout.SOUTH);

        // ── Keyboard navigation ───────────────────────────────────────────────
        // UP / DOWN move selection
        getRootPane().registerKeyboardAction(e -> {
            selectedIndex = (selectedIndex + btns.length - 1) % btns.length;
            for (JButton b : btns) b.repaint();
        }, KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);

        getRootPane().registerKeyboardAction(e -> {
            selectedIndex = (selectedIndex + 1) % btns.length;
            for (JButton b : btns) b.repaint();
        }, KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);

        // ENTER activates the current selection
        getRootPane().registerKeyboardAction(e -> btns[selectedIndex].doClick(),
            KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);

        // Escape = quit
        getRootPane().registerKeyboardAction(e -> System.exit(0),
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);

        // ── Border / background ──────────────────────────────────────────────
        getRootPane().setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER, 1),
            BorderFactory.createEmptyBorder(0,0,0,0)));

        setBackground(BG);
        getContentPane().setBackground(BG);
        setContentPane(main);
        SoundManager.getInstance().playTitleTheme();
        setVisible(true);
    }

    /**
     * Creates a styled menu button.  {@code index} is this button's position in
     * the list so it can check {@link #selectedIndex} during painting.
     */
    private JButton makeButton(int index, String text, Color fg, Color bg, ActionListener listener) {
        JButton b = new JButton(text) {
            boolean hovered = false;
            {
                addMouseListener(new java.awt.event.MouseAdapter() {
                    @Override public void mouseEntered(java.awt.event.MouseEvent e) {
                        hovered = true;
                        selectedIndex = index;   // sync keyboard highlight to mouse
                        repaint();
                    }
                    @Override public void mouseExited(java.awt.event.MouseEvent e) {
                        hovered = false;
                        repaint();
                    }
                });
            }

            @Override protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0;
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                boolean kbSelected = (selectedIndex == index);
                boolean active     = hovered || kbSelected;

                // Background fill
                Color base = active ? bg.brighter() : bg;
                g.setPaint(new GradientPaint(0,0,base.brighter(),0,getHeight(),base));
                g.fillRoundRect(0,0,getWidth(),getHeight(),6,6);

                // Keyboard-selection: brighter, thicker border + left arrow indicator
                if (kbSelected) {
                    // Outer glow
                    g.setColor(new Color(fg.getRed(), fg.getGreen(), fg.getBlue(), 60));
                    g.setStroke(new BasicStroke(4f));
                    g.drawRoundRect(1,1,getWidth()-3,getHeight()-3,6,6);
                    // Inner border
                    g.setColor(fg);
                    g.setStroke(new BasicStroke(1.5f));
                    g.drawRoundRect(0,0,getWidth()-1,getHeight()-1,6,6);
                    // Left-side selection bar
                    g.setColor(fg);
                    g.fillRoundRect(0, 6, 4, getHeight()-12, 2, 2);
                } else {
                    g.setColor(new Color(fg.getRed(), fg.getGreen(), fg.getBlue(), 110));
                    g.setStroke(new BasicStroke(1f));
                    g.drawRoundRect(0,0,getWidth()-1,getHeight()-1,6,6);
                }

                // Label text
                g.setFont(getFont());
                g.setColor(active ? Color.WHITE : fg);
                FontMetrics fm = g.getFontMetrics();
                g.drawString(getText(),
                    (getWidth() - fm.stringWidth(getText())) / 2,
                    (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
            }
        };
        b.setFont(Fonts.monoBold(17));
        b.setBackground(bg);
        b.setForeground(fg);
        b.setFocusPainted(false);
        b.setContentAreaFilled(false);
        b.setBorderPainted(false);
        b.setPreferredSize(new Dimension(0, DisplayScale.scaled(52)));
        b.addActionListener(listener);
        return b;
    }
}

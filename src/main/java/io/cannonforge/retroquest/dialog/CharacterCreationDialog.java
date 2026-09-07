package io.cannonforge.retroquest.dialog;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
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
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;

import io.cannonforge.retroquest.core.DisplayScale;
import io.cannonforge.retroquest.core.Fonts;
import io.cannonforge.retroquest.core.Retroquest;
import io.cannonforge.retroquest.core.SoundManager;
import io.cannonforge.retroquest.model.GameConfig;
import io.cannonforge.retroquest.model.Player;

/**
 * Modal dialog for creating a new player character.
 *
 * <p>Displays six classic CRPG-style attribute rolls (STR, DEX, CON, INT, WIS, CHA)
 * alongside what each one actually buys — every number shown is derived with the
 * same formula the game uses at runtime — lets the player re-roll, prompts for a
 * name, then launches {@link IntroCinematicDialog}.
 *
 * <p>Formula sources (kept in step with the model):
 * <ul>
 *   <li>starting HP = {@code CON + 10} — {@code Player} constructor</li>
 *   <li>HP per level = {@code 3 + (CON-10)/3} — {@code Player.levelUp()}</li>
 *   <li>damage soak = {@code max(0,(CON-10)/3)} — {@code Player.getDamageReduction()}</li>
 *   <li>melee damage = {@code STR-10} (+ level/3, + weapon) — {@code Player.getDamage()}</li>
 *   <li>AC = {@code 10 + (DEX-10)/2} (+ capped gear) — {@code Player.getAC()}</li>
 *   <li>to-hit = {@code (DEX-10)/2} (+ level, + gear) — {@code CombatEngine}</li>
 *   <li>spell power = {@code max(1,(INT-10)/2 + level/3)} — {@code Player}</li>
 *   <li>spell resist = {@code max(0,(WIS-10)/2*5)}% capped at 50 — {@code Player.getTotalSpellResist()}</li>
 *   <li>shop discount = {@code 2%} per CHA over 10 — {@code ShopOverlay.open()}</li>
 *   <li>flee chance = {@code 60 + (CHA-10)*3}% — {@code CombatEngine.attemptFlee()}</li>
 * </ul>
 */
@SuppressWarnings("serial")
public class CharacterCreationDialog extends JDialog {

    private static final Color BG        = new Color(  0,   0,  40);
    private static final Color CYAN_ACC  = new Color(  0, 200, 255);
    private static final Color TEXT_DIM  = new Color(110, 130, 160);
    private static final Color PHOSPHOR  = new Color(  0, 255, 120);

    /** REROLL / ACCEPT / QUIT — ACCEPT is the default so Enter never quits by accident. */
    private static final int BTN_ACCEPT = 1;

    private final Retroquest game;   // final → set ONLY in constructor
    private int str, dex, con, intel, wis, cha;

    /** The current roll, as STR, DEX, CON, INT, WIS, CHA. Read-only; used by tooling. */
    public int[] getRolledStats() { return new int[]{ str, dex, con, intel, wis, cha }; }
    private String playerName = "Hero";
    private final JLabel[] statLabels   = new JLabel[6];
    private final JLabel[] effectLabels = new JLabel[6];
    private final JLabel summary;
    private int selectedIndex = BTN_ACCEPT;

    public CharacterCreationDialog(Retroquest game) { this(game, true); }

    /**
     * @param show false builds the dialog laid out but never displayed — the offscreen entry
     *        point {@code RetroRecorder} uses to film it. This dialog is modal, so the public
     *        constructor blocks its caller until the player dismisses it.
     */
    public CharacterCreationDialog(Retroquest game, boolean show) {
        super(game, "RETROQUEST CHARACTER CREATION", true);
        this.game = game;   // ← only place we assign the final field

        setSize(DisplayScale.scaled(700), DisplayScale.scaled(580));
        setLocationRelativeTo(game);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { quit(); }
        });

        JPanel main = new JPanel(new BorderLayout(10, 10));
        main.setBackground(BG);
        main.setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));

        // ── Header ────────────────────────────────────────────────────────────
        JPanel header = new JPanel();
        header.setBackground(BG);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("ROLL YOUR HERO", SwingConstants.CENTER);
        title.setForeground(Color.YELLOW);
        title.setFont(Fonts.monoBold(20));
        title.setAlignmentX(CENTER_ALIGNMENT);

        JLabel warn = new JLabel("3d6 per attribute \u00b7 these rolls are PERMANENT", SwingConstants.CENTER);
        warn.setForeground(new Color(255, 160, 60));
        warn.setFont(Fonts.mono(12));
        warn.setAlignmentX(CENTER_ALIGNMENT);
        warn.setBorder(BorderFactory.createEmptyBorder(4, 0, 8, 0));

        header.add(title);
        header.add(warn);
        main.add(header, BorderLayout.NORTH);

        // ── Attribute rows: NAME  ROLL | what it buys ─────────────────────────
        // A plain 3-column grid gives every column the same width and clips the
        // effect text, so each row is its own name/roll block plus a wide remainder.
        JPanel statsPanel = new JPanel(new GridLayout(6, 1, 0, 6));
        statsPanel.setBackground(BG);

        String[] names = {"STRENGTH", "DEXTERITY", "CONSTITUTION", "INTELLIGENCE", "WISDOM", "CHARISMA"};
        for (int i = 0; i < 6; i++) {
            JPanel row = new JPanel(new BorderLayout(12, 0));
            row.setBackground(BG);

            JPanel head = new JPanel(new BorderLayout(8, 0));
            head.setBackground(BG);
            head.setPreferredSize(new Dimension(DisplayScale.scaled(210), DisplayScale.scaled(24)));

            JLabel nameLbl = new JLabel(names[i]);
            nameLbl.setForeground(CYAN_ACC);
            nameLbl.setFont(Fonts.monoBold(13));
            head.add(nameLbl, BorderLayout.CENTER);

            statLabels[i] = new JLabel("??", SwingConstants.RIGHT);
            statLabels[i].setForeground(Color.WHITE);
            statLabels[i].setFont(Fonts.monoBold(18));
            statLabels[i].setPreferredSize(new Dimension(DisplayScale.scaled(34), DisplayScale.scaled(24)));
            head.add(statLabels[i], BorderLayout.EAST);

            effectLabels[i] = new JLabel("");
            effectLabels[i].setForeground(TEXT_DIM);
            effectLabels[i].setFont(Fonts.mono(11));

            row.add(head, BorderLayout.WEST);
            row.add(effectLabels[i], BorderLayout.CENTER);
            statsPanel.add(row);
        }
        main.add(statsPanel, BorderLayout.CENTER);

        // ── Derived preview + buttons ─────────────────────────────────────────
        JPanel south = new JPanel();
        south.setBackground(BG);
        south.setLayout(new BoxLayout(south, BoxLayout.Y_AXIS));

        summary = new JLabel("", SwingConstants.CENTER);
        summary.setForeground(PHOSPHOR);
        summary.setFont(Fonts.monoBold(14));
        summary.setAlignmentX(CENTER_ALIGNMENT);
        summary.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));

        JPanel btnPanel = new JPanel(new GridLayout(1, 3, 10, 0));
        btnPanel.setBackground(BG);

        JButton rerollBtn = createButton("REROLL", 0, e -> rerollWithSound());
        JButton acceptBtn = createButton("ACCEPT", BTN_ACCEPT, e -> accept());
        JButton quitBtn   = createButton("QUIT",   2, e -> quit());

        btnPanel.add(rerollBtn);
        btnPanel.add(acceptBtn);
        btnPanel.add(quitBtn);

        JLabel keys = new JLabel("\u2190\u2192 / \u2191\u2193 Select    ENTER Confirm", SwingConstants.CENTER);
        keys.setForeground(TEXT_DIM);
        keys.setFont(Fonts.mono(11));
        keys.setAlignmentX(CENTER_ALIGNMENT);
        keys.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));

        south.add(summary);
        south.add(btnPanel);
        south.add(Box.createVerticalStrut(2));
        south.add(keys);
        main.add(south, BorderLayout.SOUTH);

        setContentPane(main);

        JButton[] btns = { rerollBtn, acceptBtn, quitBtn };

        // UP/LEFT go backwards, DOWN/RIGHT go forwards — matching StartMenuDialog.
        bind(btns, KeyEvent.VK_LEFT,  -1);
        bind(btns, KeyEvent.VK_UP,    -1);
        bind(btns, KeyEvent.VK_RIGHT, +1);
        bind(btns, KeyEvent.VK_DOWN,  +1);

        getRootPane().registerKeyboardAction(e -> btns[selectedIndex].doClick(),
            KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);

        rollStats();                 // first roll
        setVisible(show);
    }

    private void bind(JButton[] btns, int keyCode, int delta) {
        getRootPane().registerKeyboardAction(e -> {
            selectedIndex = (selectedIndex + btns.length + delta) % btns.length;
            for (JButton b : btns) b.repaint();
        }, KeyStroke.getKeyStroke(keyCode, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    private JButton createButton(String text, int index, ActionListener l) {
        JButton b = new JButton(text) {
            @Override protected void paintComponent(java.awt.Graphics g0) {
                java.awt.Graphics2D g = (java.awt.Graphics2D) g0;
                boolean sel = (selectedIndex == index);
                g.setColor(sel ? new Color(0, 120, 0) : new Color(0, 60, 0));
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setColor(sel ? Color.WHITE : Color.GREEN);
                g.setFont(getFont());
                java.awt.FontMetrics fm = g.getFontMetrics();
                g.drawString(getText(),
                    (getWidth() - fm.stringWidth(getText())) / 2,
                    (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
                if (sel) {
                    g.setColor(Color.YELLOW);
                    g.drawRect(1, 1, getWidth() - 3, getHeight() - 3);
                }
            }
        };
        b.setBackground(new Color(0, 60, 0));
        b.setForeground(Color.GREEN);
        b.setFont(Fonts.monoBold(14));
        b.setFocusPainted(false);
        b.setContentAreaFilled(false);
        b.setBorderPainted(false);
        b.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseEntered(java.awt.event.MouseEvent e) {
                selectedIndex = index;
                b.repaint();
            }
        });
        b.addActionListener(l);
        return b;
    }

    private long lastRollTime = 0;

    private void rerollWithSound() {
        if (System.currentTimeMillis() - lastRollTime < 500) return;
        lastRollTime = System.currentTimeMillis();
        SoundManager.getInstance().play("diceroll");
        rollStats();
    }

    private void rollStats() {
        str = roll3d6(); dex = roll3d6(); con = roll3d6();
        intel = roll3d6(); wis = roll3d6(); cha = roll3d6();

        statLabels[0].setText(String.valueOf(str));
        statLabels[1].setText(String.valueOf(dex));
        statLabels[2].setText(String.valueOf(con));
        statLabels[3].setText(String.valueOf(intel));
        statLabels[4].setText(String.valueOf(wis));
        statLabels[5].setText(String.valueOf(cha));

        refreshDerived();
    }

    // ── Derived-stat preview ──────────────────────────────────────────────────

    /** Recomputes every "what this buys you" line using the model's own formulas. */
    private void refreshDerived() {
        int startHp   = con + 10;                          // Player(...) constructor
        int hpPerLvl  = 3 + (con - 10) / 3;               // Player.levelUp()
        int soak      = Math.max(0, (con - 10) / 3);      // Player.getDamageReduction()
        int meleeDmg  = str - 10;                         // Player.getDamage(), level 1
        int ac        = 10 + (dex - 10) / 2;              // Player.getAC(), no gear
        int toHit     = (dex - 10) / 2;                   // CombatEngine to-hit term
        int spellPwr  = Math.max(1, (intel - 10) / 2);    // Player mage bonus at level 1
        int resist    = Math.min(50, Math.max(0, (wis - 10) / 2 * 5));
        int discount  = Math.max(0, cha - 10) * 2;        // ShopOverlay buy multiplier
        int flee      = 60 + (cha - 10) * 3;              // CombatEngine.attemptFlee()

        effectLabels[0].setText("melee damage " + signed(meleeDmg));
        effectLabels[1].setText("armour class " + ac + ", to-hit " + signed(toHit));
        effectLabels[2].setText("starting HP " + startHp + ", " + signed(hpPerLvl) + " HP/level, soak " + soak);
        effectLabels[3].setText("mage spell power " + signed(spellPwr));
        effectLabels[4].setText("spell resistance " + resist + "%");
        effectLabels[5].setText("shop prices -" + discount + "%, flee " + clampPct(flee) + "%");

        summary.setText("START:  HP " + startHp + "   AC " + ac + "   DMG " + signed(meleeDmg)
                + "   FLEE " + clampPct(flee) + "%");
    }

    private static String signed(int v) { return (v >= 0 ? "+" : "") + v; }

    /** Flee is rolled against 0–99, so anything outside that range is a certainty. */
    private static int clampPct(int v) { return Math.max(0, Math.min(100, v)); }

    private int roll3d6() {
        return (int)(Math.random()*6)+1 + (int)(Math.random()*6)+1 + (int)(Math.random()*6)+1;
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    private void quit() {
        int confirm = RetroDialog.showConfirm(this,
                "Quit RetroQuest?\nYour hero has not been created yet.", "QUIT");
        if (confirm == JOptionPane.YES_OPTION) System.exit(0);
    }

    private void accept() {
        playerName = RetroDialog.showInput(this, "Enter your hero's name:", "NAME YOUR HERO");
        if (playerName == null) return;
        if (playerName.trim().isEmpty()) playerName = "Hero";

        GameConfig cfg = GameConfig.get();
        Player p = new Player(cfg.getStartingX(), cfg.getStartingY(), str, dex, con, intel, wis, cha, playerName.trim());

        dispose();
        new IntroCinematicDialog(game, p);
    }

}

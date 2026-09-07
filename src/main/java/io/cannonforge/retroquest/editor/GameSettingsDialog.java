package io.cannonforge.retroquest.editor;
import static io.cannonforge.retroquest.editor.EditorTheme.ACCENT;
import static io.cannonforge.retroquest.editor.EditorTheme.AMBER;
import static io.cannonforge.retroquest.editor.EditorTheme.BG;
import static io.cannonforge.retroquest.editor.EditorTheme.BORDER_COL;
import static io.cannonforge.retroquest.editor.EditorTheme.MONO_LG;
import static io.cannonforge.retroquest.editor.EditorTheme.MONO_MD;
import static io.cannonforge.retroquest.editor.EditorTheme.MONO_SM;
import static io.cannonforge.retroquest.editor.EditorTheme.PANEL_BG;
import static io.cannonforge.retroquest.editor.EditorTheme.PHOSPHOR;
import static io.cannonforge.retroquest.editor.EditorTheme.TEXT_BRIGHT;
import static io.cannonforge.retroquest.editor.EditorTheme.TEXT_DIM;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

import io.cannonforge.retroquest.model.GameConfig;

/**
 * Dialog for editing global game configuration (starting map, position, animation).
 * Persists to {@code data/game_config.json} via {@link GameConfig}.
 */
@SuppressWarnings("serial")
public class GameSettingsDialog extends JDialog {

    private final JComboBox<String> overworldCombo;
    private final JSpinner          xSpinner;
    private final JSpinner          ySpinner;
    private final JComboBox<String> animationCombo;
    private final JComboBox<String> townCombo;

    public GameSettingsDialog(RetroForge parent) {
        super(parent, "RETROQUEST  \u00b7  GAME  SETTINGS", true);
        setSize(480, 340);
        setLocationRelativeTo(parent);
        getContentPane().setBackground(BG);

        GameConfig cfg = GameConfig.get();

        // Build overworld list from data/overworlds/
        overworldCombo = buildOverworldCombo(cfg.getStartingOverworld());

        // Build town list from data/towns/
        townCombo = buildTownCombo(cfg.getLastSafeTown());

        xSpinner = darkSpinner(cfg.getStartingX(), 0, 2048, 1);
        ySpinner = darkSpinner(cfg.getStartingY(), 0, 2048, 1);

        animationCombo = darkCombo(new String[]{"WASH_ASHORE", "NONE"});
        animationCombo.setSelectedItem(cfg.getStartingAnimation());

        // Form
        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(PANEL_BG);
        form.setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));

        GridBagConstraints gl = new GridBagConstraints();
        gl.gridx = 0; gl.anchor = GridBagConstraints.WEST;
        gl.insets = new Insets(6, 0, 6, 14);

        GridBagConstraints gf = new GridBagConstraints();
        gf.gridx = 1; gf.fill = GridBagConstraints.HORIZONTAL;
        gf.weightx = 1.0; gf.insets = new Insets(6, 0, 6, 0);

        int row = 0;
        gl.gridy = gf.gridy = row++;
        form.add(label("STARTING MAP:"), gl); form.add(overworldCombo, gf);

        gl.gridy = gf.gridy = row++;
        form.add(label("START X:"), gl); form.add(xSpinner, gf);

        gl.gridy = gf.gridy = row++;
        form.add(label("START Y:"), gl); form.add(ySpinner, gf);

        gl.gridy = gf.gridy = row++;
        form.add(label("ANIMATION:"), gl); form.add(animationCombo, gf);

        gl.gridy = gf.gridy = row++;
        form.add(label("SAFE TOWN:"), gl); form.add(townCombo, gf);

        // Vertical filler
        gl.gridy = gf.gridy = row;
        gf.weighty = 1.0;
        form.add(Box.createVerticalGlue(), gf);

        // Title bar
        JPanel titleBar = new JPanel(new BorderLayout());
        titleBar.setBackground(new Color(8, 12, 20));
        titleBar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_COL));
        titleBar.setPreferredSize(new Dimension(0, 42));
        JLabel title = new JLabel("\u25c8  GAME  SETTINGS");
        title.setForeground(AMBER);
        title.setFont(MONO_LG);
        title.setBorder(BorderFactory.createEmptyBorder(0, 16, 0, 0));
        titleBar.add(title, BorderLayout.WEST);

        // Buttons
        JPanel btnBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 8));
        btnBar.setBackground(new Color(8, 12, 20));
        btnBar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER_COL));

        JButton saveBtn = new JButton("\ud83d\udcbe  SAVE");
        saveBtn.setFont(MONO_MD);
        saveBtn.setForeground(AMBER);
        saveBtn.addActionListener(e -> save());
        JButton cancelBtn = new JButton("CANCEL");
        cancelBtn.setFont(MONO_MD);
        cancelBtn.setForeground(TEXT_DIM);
        cancelBtn.addActionListener(e -> dispose());
        btnBar.add(cancelBtn);
        btnBar.add(saveBtn);

        setLayout(new BorderLayout());
        add(titleBar, BorderLayout.NORTH);
        add(form, BorderLayout.CENTER);
        add(btnBar, BorderLayout.SOUTH);

        setVisible(true);
    }

    private void save() {
        GameConfig cfg = GameConfig.get();
        cfg.setStartingOverworld((String) overworldCombo.getSelectedItem());
        cfg.setStartingX((Integer) xSpinner.getValue());
        cfg.setStartingY((Integer) ySpinner.getValue());
        cfg.setStartingAnimation((String) animationCombo.getSelectedItem());
        cfg.setLastSafeTown((String) townCombo.getSelectedItem());
        cfg.save();
        dispose();
    }

    private JComboBox<String> buildOverworldCombo(String selected) {
        JComboBox<String> cb = new JComboBox<>();
        File dir = new File("data/overworlds");
        if (dir.isDirectory()) {
            File[] files = dir.listFiles((d, n) -> n.endsWith(".rfmap"));
            if (files != null) {
                java.util.Arrays.sort(files);
                for (File f : files)
                    cb.addItem(f.getName().replace(".rfmap", ""));
            }
        }
        if (cb.getItemCount() == 0) cb.addItem("lirandel");
        if (selected != null) cb.setSelectedItem(selected);
        styleCombo(cb);
        return cb;
    }

    private JComboBox<String> buildTownCombo(String selected) {
        JComboBox<String> cb = new JComboBox<>();
        File dir = new File("data/towns");
        if (dir.isDirectory()) {
            File[] files = dir.listFiles((d, n) -> n.endsWith(".rfmap"));
            if (files != null) {
                java.util.Arrays.sort(files);
                for (File f : files)
                    cb.addItem(f.getName().replace(".rfmap", ""));
            }
        }
        if (cb.getItemCount() == 0) cb.addItem("Moonhaven");
        if (selected != null) cb.setSelectedItem(selected);
        styleCombo(cb);
        return cb;
    }

    private void styleCombo(JComboBox<String> cb) {
        cb.setBackground(PANEL_BG);
        cb.setForeground(TEXT_BRIGHT);
        cb.setFont(MONO_SM);
    }

    private static JLabel label(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(ACCENT);
        l.setFont(new Font("Monospaced", Font.BOLD, 10));
        return l;
    }

    private static JSpinner darkSpinner(int val, int min, int max, int step) {
        JSpinner s = new JSpinner(new SpinnerNumberModel(val, min, max, step));
        s.setFont(MONO_SM);
        JComponent ed = s.getEditor();
        if (ed instanceof JSpinner.DefaultEditor de) {
            de.getTextField().setBackground(new Color(8, 12, 20));
            de.getTextField().setForeground(PHOSPHOR);
            de.getTextField().setCaretColor(PHOSPHOR);
            de.getTextField().setFont(MONO_SM);
        }
        return s;
    }

    private static <T> JComboBox<T> darkCombo(T[] items) {
        JComboBox<T> cb = new JComboBox<>(items);
        cb.setBackground(PANEL_BG);
        cb.setForeground(TEXT_BRIGHT);
        cb.setFont(MONO_SM);
        return cb;
    }
}

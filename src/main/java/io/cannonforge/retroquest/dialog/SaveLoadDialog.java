package io.cannonforge.retroquest.dialog;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;

import com.google.gson.Gson;

import io.cannonforge.retroquest.core.DisplayScale;
import io.cannonforge.retroquest.core.Fonts;
import io.cannonforge.retroquest.core.Retroquest;
import io.cannonforge.retroquest.core.SaveData;

@SuppressWarnings("serial")
public class SaveLoadDialog extends JDialog {
    private final Retroquest game;
    private final boolean isSaveMode;
    private final JList<String> list;
    private final DefaultListModel<String> model = new DefaultListModel<>();
    private final List<SaveData> saves = new ArrayList<>();
    private boolean hasAutoSave = false;

    public SaveLoadDialog(Retroquest game, boolean isSaveMode) {
        super(game, isSaveMode ? "SAVE GAME" : "LOAD GAME", true);
        this.game = game;
        this.isSaveMode = isSaveMode;

        setSize(DisplayScale.scaled(760), DisplayScale.scaled(520));
        setLocationRelativeTo(game);

        JPanel main = new JPanel(new BorderLayout(10, 10));
        main.setBackground(Color.BLACK);
        main.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        JLabel title = new JLabel(isSaveMode ? "CHOOSE SAVE SLOT" : "SELECT SAVE TO LOAD",
                SwingConstants.CENTER);
        title.setForeground(Color.YELLOW);
        title.setFont(Fonts.monoBold(24));
        main.add(title, BorderLayout.NORTH);

        // In load mode, show auto-save as the first entry
        if (!isSaveMode) {
            File autoFile = new File("saves/retroquest_save_auto.sav");
            if (autoFile.exists()) {
                hasAutoSave = true;
                try (InputStreamReader r = new InputStreamReader(new FileInputStream(autoFile), StandardCharsets.UTF_8)) {
                    SaveData sd = new Gson().fromJson(r, SaveData.class);
                    saves.add(sd);
                    model.addElement("\u27f3 " + sd.getDisplayName());
                } catch (Exception ignored) {
                    saves.add(null);
                    model.addElement("\u27f3 AUTO - [CORRUPT]");
                }
            }
        }

        // Scan all 8 slots
        for (int i = 1; i <= 8; i++) {
            File file = new File("saves/retroquest_save_" + String.format("%02d", i) + ".sav");
            if (file.exists()) {
                try (InputStreamReader r = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
                    SaveData sd = new Gson().fromJson(r, SaveData.class);
                    saves.add(sd);
                    model.addElement(sd.getDisplayName());
                } catch (Exception ignored) {
                    // Must still push a placeholder: `saves` is indexed in lockstep
                    // with `model`, and a short list mis-selects (and can throw from
                    // deleteSelected) for every slot after the corrupt one.
                    saves.add(null);
                    model.addElement(String.format("%02d - [CORRUPT]", i));
                }
            } else {
                model.addElement(String.format("%02d - [EMPTY]", i));
                saves.add(null);
            }
        }

        list = new JList<>(model);
        list.setBackground(new Color(0, 20, 0));
        list.setForeground(Color.GREEN);
        list.setFont(Fonts.mono(16));

        // Pre-select first usable slot: for load, first non-empty; for save, first slot
        int preselect = 0;
        if (!isSaveMode) {
            for (int i = 0; i < saves.size(); i++) {
                if (saves.get(i) != null) { preselect = i; break; }
            }
        }
        list.setSelectedIndex(preselect);

        // Double-click to confirm
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) performAction();
            }
        });

        // Enter key confirms current selection
        getRootPane().registerKeyboardAction(e -> performAction(),
            KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0),
            JComponent.WHEN_IN_FOCUSED_WINDOW);

        main.add(new JScrollPane(list), BorderLayout.CENTER);

        JPanel btnPanel = new JPanel(new GridLayout(1, isSaveMode ? 3 : 2, 10, 0));
        btnPanel.setBackground(Color.BLACK);

        JButton actionBtn = new JButton(isSaveMode ? "SAVE HERE" : "LOAD");
        actionBtn.setBackground(new Color(0, 80, 0));
        actionBtn.setForeground(Color.GREEN);
        actionBtn.addActionListener(e -> performAction());

        JButton deleteBtn = new JButton("DELETE");
        deleteBtn.setBackground(new Color(80, 0, 0));
        deleteBtn.setForeground(Color.WHITE);
        deleteBtn.addActionListener(e -> deleteSelected());

        JButton closeBtn = new JButton(game.getGamePanel() == null ? "BACK" : "CLOSE");
        closeBtn.addActionListener(e -> returnToStartMenu());

        btnPanel.add(actionBtn);
        if (isSaveMode) btnPanel.add(deleteBtn);
        btnPanel.add(closeBtn);

        main.add(btnPanel, BorderLayout.SOUTH);

        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                returnToStartMenu();
            }
        });

        // ESC key closes the dialog
        getRootPane().registerKeyboardAction(e -> returnToStartMenu(),
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
            JComponent.WHEN_IN_FOCUSED_WINDOW);

        setContentPane(main);
        setVisible(true);
    }

    private void performAction() {
        int idx = list.getSelectedIndex();
        if (idx == -1) return;

        int slot = slotForIndex(idx);
        File file = (slot == 0)
            ? new File("saves/retroquest_save_auto.sav")
            : new File("saves/retroquest_save_" + String.format("%02d", slot) + ".sav");

        if (isSaveMode) {
            if (file.exists()) {
                int confirm = JOptionPane.showConfirmDialog(this,
                        "Overwrite this save?", "Confirm Overwrite", JOptionPane.YES_NO_OPTION);
                if (confirm != JOptionPane.YES_OPTION) return;
            }
            dispose();
            game.saveGame(slot);
        } else {
            if (!file.exists()) {
                JOptionPane.showMessageDialog(this,
                        "That slot is empty — please select a saved game.",
                        "No Save Found", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            dispose();
            game.loadGame(slot);
        }
    }

    /** Maps list index to save slot number, accounting for auto-save entry in load mode. */
    private int slotForIndex(int idx) {
        if (!isSaveMode && hasAutoSave) {
            return idx == 0 ? 0 : idx;  // index 0→auto (slot 0), 1-8→slots 1-8
        }
        return idx + 1;  // save mode: 0-7→slots 1-8
    }

    private void returnToStartMenu() {
        dispose();
        if (game.getGamePanel() == null) {
            // Called from startup — reopen the start menu (which re-plays title theme)
            new StartMenuDialog(game);
        }
    }

    private void deleteSelected() {
        int idx = list.getSelectedIndex();
        if (idx == -1) return;
        int slot = slotForIndex(idx);
        if (slot == 0) return;  // cannot delete auto-save from this dialog
        File file = new File("saves/retroquest_save_" + String.format("%02d", slot) + ".sav");
        if (file.exists() && JOptionPane.showConfirmDialog(this, "Delete this save permanently?", "Delete",
                JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
            file.delete();
            model.set(idx, String.format("%02d - [EMPTY]", slot));
            saves.set(idx, null);
        }
    }
}

package io.cannonforge.retroquest.editor;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.io.File;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

import io.cannonforge.retroquest.core.Town;
import io.cannonforge.retroquest.model.MapData;
import io.cannonforge.retroquest.model.TownEntrance;

@SuppressWarnings("serial")
public class TownPlacementDialog extends JDialog {

    private final RetroForge editor;
    private final MapCanvas canvas;
    private final MapData map;
    private final int worldX, worldY;

    private final JComboBox<String> townCombo = new JComboBox<>();
    private TownEntrance existingEntrance = null;

    public TownPlacementDialog(RetroForge editor, MapCanvas canvas, MapData map, int worldX, int worldY) {
        super(editor, "PLACE TOWN ENTRANCE", true);
        this.editor = editor;
        this.canvas = canvas;
        this.map = map;
        this.worldX = worldX;
        this.worldY = worldY;

        for (TownEntrance te : map.townEntrances) {
            if (te.worldX() == worldX && te.worldY() == worldY) { existingEntrance = te; break; }
        }
        if (existingEntrance != null) setTitle("EDIT TOWN ENTRANCE");

        buildUI();
        loadExistingTowns();

        setSize(520, 340);
        setLocationRelativeTo(editor);
        setVisible(true);
    }

    private void buildUI() {
        JPanel main = new JPanel(new BorderLayout(10, 10));
        main.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        JLabel title = new JLabel((existingEntrance != null ? "Edit" : "Place") + " Town Entrance at (" + worldX + ", " + worldY + ")", SwingConstants.CENTER);
        title.setFont(new Font("Monospaced", Font.BOLD, 16));
        main.add(title, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridLayout(0, 1, 5, 10));
        form.add(new JLabel("Choose existing town or create new:"));

        townCombo.setFont(new Font("Monospaced", Font.PLAIN, 14));
        form.add(townCombo);

        JButton newBtn = new JButton("Create New Town...");
        newBtn.addActionListener(e -> createNewTown());
        form.add(newBtn);

        JButton placeBtn = new JButton(existingEntrance != null ? "UPDATE ENTRANCE" : "PLACE ENTRANCE HERE");
        placeBtn.setBackground(new Color(0, 120, 0));
        placeBtn.setForeground(Color.WHITE);
        placeBtn.setFont(new Font("Monospaced", Font.BOLD, 14));
        placeBtn.addActionListener(e -> placeEntrance());

        JPanel bottom = new JPanel(new FlowLayout());
        bottom.add(placeBtn);

        main.add(form, BorderLayout.CENTER);
        main.add(bottom, BorderLayout.SOUTH);

        setContentPane(main);
    }

    private void loadExistingTowns() {
        townCombo.removeAllItems();
        townCombo.addItem("(Create New Town)");

        File townsDir = new File("data/towns");
        if (townsDir.exists()) {
            File[] files = townsDir.listFiles((d, n) -> n.endsWith(".rfmap"));
            if (files != null) {
                for (File f : files) {
                    townCombo.addItem(f.getName().replace(".rfmap", ""));
                }
            }
        }
        if (existingEntrance != null) townCombo.setSelectedItem(existingEntrance.townName());
    }

    private void createNewTown() {
        String name = JOptionPane.showInputDialog(this, "New town name:", "MyTown");
        if (name == null || name.trim().isEmpty()) return;
        name = name.trim();

        // This now properly creates the town with default NPCs and saves it
        new Town(name, worldX, worldY);

        JOptionPane.showMessageDialog(this, "✅ New town '" + name + "' created!");

        loadExistingTowns();
        townCombo.setSelectedItem(name);
    }

    private void placeEntrance() {
        String selected = (String) townCombo.getSelectedItem();
        if (selected == null || selected.equals("(Create New Town)")) {
            JOptionPane.showMessageDialog(this, "Please select or create a town first.");
            return;
        }

        if (existingEntrance != null) map.townEntrances.remove(existingEntrance);
        char oldTile = map.tiles[worldY][worldX];
        TownEntrance newEntrance = new TownEntrance(selected, worldX, worldY);
        map.tiles[worldY][worldX] = 'E';
        map.townEntrances.add(newEntrance);
        // Pass the replaced entrance so undo restores the town link it superseded
        canvas.pushCommand(new MapCommand.EntranceAddCommand(oldTile, existingEntrance, newEntrance));

        dispose();
        editor.refreshAll();

        JOptionPane.showMessageDialog(editor,
            "Town entrance for '" + selected + "' placed at (" + worldX + ", " + worldY + ")!\n\n" +
            "Walk on the gold E in-game to enter.",
            "Success", JOptionPane.INFORMATION_MESSAGE);
    }
}

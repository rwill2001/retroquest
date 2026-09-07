package io.cannonforge.retroquest.editor;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.io.File;
import java.util.Map;
import java.util.TreeMap;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.SwingConstants;

import io.cannonforge.retroquest.model.MapData;
import io.cannonforge.retroquest.model.TileState;

@SuppressWarnings("serial")
public class DungeonPlacementDialog extends JDialog {

    private final RetroForge editor;
    private final MapCanvas canvas;
    private final MapData map;
    private final int worldX, worldY;

    private final JRadioButton proceduralRadio = new JRadioButton("Procedural (infinite, 50-level)");
    private final JRadioButton authoredRadio   = new JRadioButton("Authored dungeon:");
    private final JComboBox<String> dungeonCombo = new JComboBox<>();

    /** Maps display name → internal dungeon group name. */
    private final Map<String, String> dungeonNames = new TreeMap<>();

    /** Non-null when editing an existing authored dungeon entrance; null for new placement or procedural. */
    private final String existingDungeonName;

    public DungeonPlacementDialog(RetroForge editor, MapCanvas canvas, MapData map, int worldX, int worldY) {
        this(editor, canvas, map, worldX, worldY, null);
    }

    public DungeonPlacementDialog(RetroForge editor, MapCanvas canvas, MapData map, int worldX, int worldY, String existingDungeonName) {
        super(editor, existingDungeonName != null ? "EDIT DUNGEON ENTRANCE" : "PLACE DUNGEON ENTRANCE", true);
        this.editor = editor;
        this.canvas = canvas;
        this.map = map;
        this.worldX = worldX;
        this.worldY = worldY;
        this.existingDungeonName = existingDungeonName;

        buildUI();
        loadAuthoredDungeons();

        setSize(540, 380);
        setLocationRelativeTo(editor);
        setVisible(true);
    }

    private void buildUI() {
        JPanel main = new JPanel(new BorderLayout(10, 10));
        main.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        JLabel title = new JLabel((existingDungeonName != null ? "Edit" : "Place") + " Dungeon Entrance at (" + worldX + ", " + worldY + ")", SwingConstants.CENTER);
        title.setFont(new Font("Monospaced", Font.BOLD, 16));
        main.add(title, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridLayout(0, 1, 5, 8));
        form.add(new JLabel("Choose dungeon type:"));

        ButtonGroup group = new ButtonGroup();
        group.add(proceduralRadio);
        group.add(authoredRadio);
        if (existingDungeonName != null) {
            authoredRadio.setSelected(true);
        } else {
            proceduralRadio.setSelected(true);
        }

        proceduralRadio.setFont(new Font("Monospaced", Font.PLAIN, 14));
        authoredRadio.setFont(new Font("Monospaced", Font.PLAIN, 14));

        proceduralRadio.addActionListener(e -> dungeonCombo.setEnabled(false));
        authoredRadio.addActionListener(e -> dungeonCombo.setEnabled(true));

        form.add(proceduralRadio);
        form.add(authoredRadio);

        dungeonCombo.setFont(new Font("Monospaced", Font.PLAIN, 14));
        dungeonCombo.setEnabled(existingDungeonName != null);
        form.add(dungeonCombo);

        JButton placeBtn = new JButton(existingDungeonName != null ? "UPDATE ENTRANCE" : "PLACE ENTRANCE HERE");
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

    private void loadAuthoredDungeons() {
        dungeonCombo.removeAllItems();
        dungeonNames.clear();

        File dungeonsDir = new File("data/dungeons");
        if (!dungeonsDir.exists()) return;

        // Scan for *_1.rfmap to find dungeon groups and count levels
        File[] files = dungeonsDir.listFiles((d, n) -> n.endsWith(".rfmap"));
        if (files == null) return;

        Map<String, Integer> levelCounts = new TreeMap<>();
        for (File f : files) {
            String name = f.getName().replace(".rfmap", "");
            int lastUnderscore = name.lastIndexOf('_');
            if (lastUnderscore < 0) continue;
            String groupName = name.substring(0, lastUnderscore);
            try {
                Integer.parseInt(name.substring(lastUnderscore + 1));
                levelCounts.merge(groupName, 1, Integer::sum);
            } catch (NumberFormatException ignored) {
                // Not a numbered dungeon file
            }
        }

        for (var entry : levelCounts.entrySet()) {
            String groupName = entry.getKey();
            int levels = entry.getValue();
            String displayName = groupName.replace('_', ' ') + " (" + levels + " level" + (levels > 1 ? "s" : "") + ")";
            dungeonNames.put(displayName, groupName);
            dungeonCombo.addItem(displayName);
        }

        if (dungeonCombo.getItemCount() == 0) {
            dungeonCombo.addItem("(no authored dungeons found)");
        }

        if (existingDungeonName != null) {
            for (var entry : dungeonNames.entrySet()) {
                if (entry.getValue().equals(existingDungeonName)) {
                    dungeonCombo.setSelectedItem(entry.getKey());
                    break;
                }
            }
        }
    }

    private void placeEntrance() {
        String dungeonName = null;

        if (authoredRadio.isSelected()) {
            String selected = (String) dungeonCombo.getSelectedItem();
            if (selected == null || !dungeonNames.containsKey(selected)) {
                JOptionPane.showMessageDialog(this, "Please select an authored dungeon.");
                return;
            }
            dungeonName = dungeonNames.get(selected);
        }

        char oldTile = map.tiles[worldY][worldX];

        // Remember the dungeon that was here so undo can restore it
        String oldDungeonName = null;
        if (map.initialTileStates != null) {
            TileState prev = map.initialTileStates.get(worldX + "," + worldY);
            if (prev != null && prev.data != null) oldDungeonName = prev.data.get("dungeonName");
        }

        // Set the tile to 'D'
        map.tiles[worldY][worldX] = 'D';

        // Update initialTileStates
        if (dungeonName != null) {
            if (map.initialTileStates == null) {
                map.initialTileStates = new java.util.HashMap<>();
            }
            String key = worldX + "," + worldY;
            TileState ts = map.initialTileStates.get(key);
            if (ts == null) {
                ts = new TileState();
                map.initialTileStates.put(key, ts);
            }
            ts.set("dungeonName", dungeonName);
        } else {
            // Procedural: remove any existing dungeonName at this position
            if (map.initialTileStates != null) {
                String key = worldX + "," + worldY;
                TileState ts = map.initialTileStates.get(key);
                if (ts != null && ts.data != null) {
                    ts.data.remove("dungeonName");
                }
            }
        }

        canvas.pushCommand(new MapCommand.DungeonAddCommand(oldTile, worldX, worldY, oldDungeonName, dungeonName));

        dispose();
        editor.refreshAll();

        String desc = dungeonName != null
                ? "Authored dungeon '" + dungeonName + "'"
                : "Procedural dungeon";
        JOptionPane.showMessageDialog(editor,
                desc + " entrance placed at (" + worldX + ", " + worldY + ")!\n\n" +
                "Walk on the D tile in-game and press E to enter.",
                "Success", JOptionPane.INFORMATION_MESSAGE);
    }
}

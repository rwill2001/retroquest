package io.cannonforge.retroquest.editor;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;

import io.cannonforge.retroquest.model.God;
import io.cannonforge.retroquest.model.Item;
import io.cannonforge.retroquest.model.ItemSlot;
import io.cannonforge.retroquest.model.MapData;
import io.cannonforge.retroquest.model.Monster;
import io.cannonforge.retroquest.model.TileDefinition;
import io.cannonforge.retroquest.model.TileState;
import io.cannonforge.retroquest.registry.ItemRegistry;
import io.cannonforge.retroquest.registry.MonsterRegistry;
import io.cannonforge.retroquest.registry.TileRegistry;

/**
 * Modal dialog for authoring per-coordinate initial tile state in RetroForge.
 *
 * <p>Shows a structured form with typed widgets for each property defined by
 * the tile's effect type.  Data is stored in {@link MapData#initialTileStates}
 * as a {@code Map<String,String>} — the data format is unchanged; only the UI
 * is structured.
 */
@SuppressWarnings("serial")
class TileInstanceDialog extends JDialog {

    // ── Colours / fonts ───────────────────────────────────────────────────────

    private static final Color BG       = new Color(18, 22, 32);
    private static final Color PANEL_BG = new Color(25, 30, 45);
    private static final Color BORDER   = new Color(80, 80, 120);
    private static final Color AMBER    = new Color(255, 200, 80);
    private static final Color TEXT_DIM = new Color(160, 160, 200);
    private static final Font  F_TITLE  = new Font("Monospaced", Font.BOLD, 13);
    private static final Font  F_LABEL  = new Font("Monospaced", Font.PLAIN, 12);

    // ── Property schema ───────────────────────────────────────────────────────

    private enum ValueType {
        BOOLEAN, INT, STRING, CHAR_TILE, ITEM_KEY, MAP_NAME,
        ITEM_ANY, MONSTER, GOD, DIRECTION
    }

    private record PropDef(String name, ValueType type) {}

    /** Effect-type → ordered list of property definitions. */
    private static final Map<String, List<PropDef>> EFFECT_PROPS = new LinkedHashMap<>();

    static {
        EFFECT_PROPS.put("locked_door", List.of(
                new PropDef("open",            ValueType.BOOLEAN),
                new PropDef("requiredKeyId",   ValueType.ITEM_KEY),
                new PropDef("unlockedTileId",  ValueType.CHAR_TILE),
                new PropDef("message",         ValueType.STRING),
                new PropDef("lockedMessage",   ValueType.STRING)
        ));
        EFFECT_PROPS.put("pressure_plate", List.of(
                new PropDef("x",              ValueType.INT),
                new PropDef("y",              ValueType.INT),
                new PropDef("activateChar",   ValueType.CHAR_TILE),
                new PropDef("deactivateChar", ValueType.CHAR_TILE),
                new PropDef("once",           ValueType.BOOLEAN)
        ));
        EFFECT_PROPS.put("toggle_door", List.of(
                new PropDef("open",      ValueType.BOOLEAN),
                new PropDef("openChar",  ValueType.CHAR_TILE),
                new PropDef("closeChar", ValueType.CHAR_TILE)
        ));
        EFFECT_PROPS.put("trap_once", List.of(
                new PropDef("triggered", ValueType.BOOLEAN),
                new PropDef("amount",    ValueType.INT),
                new PropDef("message",   ValueType.STRING)
        ));
        EFFECT_PROPS.put("set_tile", List.of(
                new PropDef("x",       ValueType.INT),
                new PropDef("y",       ValueType.INT),
                new PropDef("tileId",  ValueType.CHAR_TILE),
                new PropDef("message", ValueType.STRING)
        ));
        EFFECT_PROPS.put("island_portal", List.of(
                new PropDef("requiredKeyId",  ValueType.ITEM_KEY),
                new PropDef("lockedMessage",  ValueType.STRING)
        ));
        EFFECT_PROPS.put("teleport", List.of(
                new PropDef("x",   ValueType.INT),
                new PropDef("y",   ValueType.INT),
                new PropDef("map", ValueType.STRING)
        ));
        EFFECT_PROPS.put("damage", List.of(
                new PropDef("amount",  ValueType.INT),
                new PropDef("message", ValueType.STRING)
        ));
        EFFECT_PROPS.put("encounter", List.of(
                new PropDef("message", ValueType.STRING)
        ));
        EFFECT_PROPS.put("shrine", List.of(
                new PropDef("god",              ValueType.GOD),
                new PropDef("favor",            ValueType.INT),
                new PropDef("xp",               ValueType.INT),
                new PropDef("message",          ValueType.STRING),
                new PropDef("discoveredTileId", ValueType.CHAR_TILE)
        ));
        EFFECT_PROPS.put("treasure", List.of(
                new PropDef("itemId",           ValueType.ITEM_ANY),
                new PropDef("tier",             ValueType.INT),
                new PropDef("gold",             ValueType.INT),
                new PropDef("requiredItemId",   ValueType.ITEM_ANY),
                new PropDef("lockedMessage",    ValueType.STRING),
                new PropDef("message",          ValueType.STRING),
                new PropDef("discoveredTileId", ValueType.CHAR_TILE)
        ));
        EFFECT_PROPS.put("ambush", List.of(
                new PropDef("monsterId",        ValueType.MONSTER),
                new PropDef("lootItemId",       ValueType.ITEM_ANY),
                new PropDef("lootTier",         ValueType.INT),
                new PropDef("message",          ValueType.STRING)
        ));
        EFFECT_PROPS.put("geyser", List.of(
                new PropDef("chance",           ValueType.INT),
                new PropDef("amount",           ValueType.INT),
                new PropDef("message",          ValueType.STRING)
        ));
        EFFECT_PROPS.put("wind_current", List.of(
                new PropDef("direction",        ValueType.DIRECTION),
                new PropDef("distance",         ValueType.INT),
                new PropDef("message",          ValueType.STRING)
        ));
        EFFECT_PROPS.put("lore", List.of(
                new PropDef("oneTime",          ValueType.BOOLEAN),
                new PropDef("message",          ValueType.STRING)
        ));
        EFFECT_PROPS.put("none", List.of());
    }

    // ── Widget holder ─────────────────────────────────────────────────────────

    /**
     * Holds the live Swing widget(s) for one property row so that save logic
     * can extract the value without reflecting over component types.
     */
    private static class WidgetRow {
        final PropDef       def;
        JCheckBox           setCheck;   // only for INT — marks "include this value"
        JSpinner            spinner;    // INT
        JTextField          textField;  // STRING
        JComboBox<String>   combo;      // BOOLEAN | CHAR_TILE | ITEM_KEY

        WidgetRow(PropDef def) { this.def = def; }

        /** Returns the string value to persist, or {@code null} to omit the key. */
        String extractValue() {
            return switch (def.type()) {
                case BOOLEAN -> {
                    String sel = (String) combo.getSelectedItem();
                    yield "(default)".equals(sel) ? null : sel;
                }
                case INT -> {
                    if (setCheck != null && !setCheck.isSelected()) yield null;
                    yield String.valueOf(((Number) spinner.getValue()).intValue());
                }
                case STRING -> {
                    String t = textField.getText().trim();
                    yield t.isEmpty() ? null : t;
                }
                case CHAR_TILE -> {
                    String sel = (String) combo.getSelectedItem();
                    if (sel == null || sel.startsWith("(")) yield null;
                    // Format: "[F] Floor" — extract the char between [ and ]
                    if (sel.length() >= 3 && sel.charAt(0) == '[' && sel.charAt(2) == ']') {
                        yield String.valueOf(sel.charAt(1));
                    }
                    yield null;
                }
                case ITEM_KEY -> {
                    String sel = (String) combo.getSelectedItem();
                    yield (sel == null || sel.startsWith("(")) ? null : sel;
                }
                case MAP_NAME -> {
                    String sel = (String) combo.getSelectedItem();
                    yield (sel == null || sel.isEmpty() || sel.startsWith("(")) ? null : sel;
                }
                case ITEM_ANY, MONSTER -> {
                    // Format: "itemId — Display Name" (or just "itemId")
                    String sel = (String) combo.getSelectedItem();
                    if (sel == null || sel.startsWith("(")) yield null;
                    int dash = sel.indexOf(" — ");
                    yield dash > 0 ? sel.substring(0, dash) : sel;
                }
                case GOD -> {
                    String sel = (String) combo.getSelectedItem();
                    if (sel == null || sel.startsWith("(")) yield null;
                    int dash = sel.indexOf(" — ");
                    yield dash > 0 ? sel.substring(0, dash) : sel;
                }
                case DIRECTION -> {
                    String sel = (String) combo.getSelectedItem();
                    yield (sel == null || "(default)".equals(sel)) ? null : sel;
                }
            };
        }
    }

    // ── Instance state ────────────────────────────────────────────────────────

    private final MapCanvas         canvas;
    private final MapData           map;
    private final int               tx, ty;
    private final List<WidgetRow>   rows = new ArrayList<>();

    // ── Constructor ───────────────────────────────────────────────────────────

    TileInstanceDialog(RetroForge editor, MapCanvas canvas, MapData map, int tx, int ty) {
        super(editor, "Tile Instance State", true);
        this.canvas = canvas;
        this.map    = map;
        this.tx     = tx;
        this.ty     = ty;

        char           ch         = map.tiles[ty][tx];
        TileDefinition def        = TileRegistry.getByIdSafe(ch);
        String         effectType = def.getOnStepEffect().isNone()
                                        ? "none"
                                        : def.getOnStepEffect().getType();

        // ── Existing state ────────────────────────────────────────────────────
        Map<String, String> existing = new HashMap<>();
        if (map.initialTileStates != null) {
            TileState ts = map.initialTileStates.get(tx + "," + ty);
            if (ts != null) existing.putAll(ts.data);
        }

        // ── Pre-build combo data ──────────────────────────────────────────────
        String[] tileOptions  = buildTileOptions();
        String[] keyOptions   = buildKeyOptions();

        // ── Layout ───────────────────────────────────────────────────────────
        getContentPane().setBackground(BG);
        setLayout(new BorderLayout(8, 8));

        add(buildHeader(ch, def, effectType), BorderLayout.NORTH);
        add(buildForm(effectType, existing, tileOptions, keyOptions), BorderLayout.CENTER);
        add(buildActions(), BorderLayout.SOUTH);

        pack();
        setMinimumSize(new Dimension(440, 200));
        setLocationRelativeTo(editor);
        setVisible(true);
    }

    // ── Panel builders ────────────────────────────────────────────────────────

    private JPanel buildHeader(char ch, TileDefinition def, String effectType) {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(PANEL_BG);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)));

        JLabel title = new JLabel("TILE INSTANCE STATE");
        title.setFont(F_TITLE);
        title.setForeground(AMBER);
        header.add(title, BorderLayout.NORTH);

        JLabel info = new JLabel(
                "[" + ch + "] " + def.getName() + " at (" + tx + ", " + ty + ")"
                + "  \u2014  Effect type: " + effectType);
        info.setFont(F_LABEL);
        info.setForeground(TEXT_DIM);
        header.add(info, BorderLayout.SOUTH);

        return header;
    }

    private JPanel buildForm(String effectType,
                             Map<String, String> existing,
                             String[] tileOptions,
                             String[] keyOptions) {

        JPanel outer = new JPanel(new BorderLayout());
        outer.setBackground(BG);
        outer.setBorder(BorderFactory.createEmptyBorder(8, 12, 4, 12));

        List<PropDef> props = new java.util.ArrayList<>(EFFECT_PROPS.getOrDefault(effectType, List.of()));

        // Universal teleport destination — available on ANY tile regardless of effect type
        List<PropDef> teleportProps = List.of(
            new PropDef("teleport_map",   ValueType.MAP_NAME),
            new PropDef("teleport_x",     ValueType.INT),
            new PropDef("teleport_y",     ValueType.INT),
            new PropDef("requiredKeyId",  ValueType.ITEM_KEY),
            new PropDef("lockedMessage",  ValueType.STRING)
        );
        // Only add teleport props if not already covered by the effect type;
        // also skip any teleport prop whose name is already present (e.g. locked_door's
        // own lockedMessage/requiredKeyId, treasure's lockedMessage).
        if (!"teleport".equals(effectType) && !"island_portal".equals(effectType)) {
            java.util.Set<String> existingNames = new java.util.HashSet<>();
            for (PropDef pd : props) existingNames.add(pd.name());
            for (PropDef pd : teleportProps) {
                if (!existingNames.contains(pd.name())) props.add(pd);
            }
        }

        JPanel grid = new JPanel(new GridBagLayout());
        grid.setBackground(BG);

        GridBagConstraints lc = new GridBagConstraints();
        lc.anchor  = GridBagConstraints.EAST;
        lc.insets  = new Insets(4, 0, 4, 8);
        lc.gridx   = 0;
        lc.fill    = GridBagConstraints.NONE;

        GridBagConstraints wc = new GridBagConstraints();
        wc.anchor  = GridBagConstraints.WEST;
        wc.insets  = new Insets(4, 0, 4, 0);
        wc.gridx   = 1;
        wc.fill    = GridBagConstraints.HORIZONTAL;
        wc.weightx = 1.0;

        int row = 0;
        for (PropDef pd : props) {
            WidgetRow wr = new WidgetRow(pd);
            String    stored = existing.get(pd.name());

            lc.gridy = row;
            wc.gridy = row;
            row++;

            JLabel label = new JLabel(pd.name() + ":");
            label.setFont(F_LABEL);
            label.setForeground(TEXT_DIM);
            grid.add(label, lc);

            switch (pd.type()) {
                case BOOLEAN -> {
                    wr.combo = new JComboBox<>(new String[]{"(default)", "true", "false"});
                    styleCombo(wr.combo);
                    if ("true".equals(stored))        wr.combo.setSelectedItem("true");
                    else if ("false".equals(stored))  wr.combo.setSelectedItem("false");
                    grid.add(wr.combo, wc);
                }
                case INT -> {
                    JPanel intPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
                    intPanel.setBackground(BG);

                    wr.setCheck = new JCheckBox("set");
                    wr.setCheck.setBackground(BG);
                    wr.setCheck.setForeground(TEXT_DIM);
                    wr.setCheck.setFont(F_LABEL);
                    wr.setCheck.setFocusPainted(false);

                    wr.spinner = new JSpinner(new SpinnerNumberModel(0, 0, 9999, 1));
                    wr.spinner.setFont(F_LABEL);
                    wr.spinner.setPreferredSize(new Dimension(80, 24));
                    styleSpinner(wr.spinner);

                    if (stored != null) {
                        try {
                            wr.spinner.setValue(Integer.parseInt(stored));
                            wr.setCheck.setSelected(true);
                        } catch (NumberFormatException ignored) {}
                    }
                    wr.spinner.setEnabled(wr.setCheck.isSelected());
                    wr.setCheck.addActionListener(e ->
                            wr.spinner.setEnabled(wr.setCheck.isSelected()));

                    intPanel.add(wr.setCheck);
                    intPanel.add(wr.spinner);
                    grid.add(intPanel, wc);
                }
                case STRING -> {
                    wr.textField = new JTextField(stored != null ? stored : "", 20);
                    styleTextField(wr.textField);
                    grid.add(wr.textField, wc);
                }
                case CHAR_TILE -> {
                    wr.combo = new JComboBox<>(tileOptions);
                    styleCombo(wr.combo);
                    if (stored != null && stored.length() == 1) {
                        char target = stored.charAt(0);
                        for (String opt : tileOptions) {
                            if (opt.length() >= 2 && opt.charAt(1) == target) {
                                wr.combo.setSelectedItem(opt);
                                break;
                            }
                        }
                    }
                    grid.add(wr.combo, wc);
                }
                case ITEM_KEY -> {
                    wr.combo = new JComboBox<>(keyOptions);
                    styleCombo(wr.combo);
                    if (stored != null) wr.combo.setSelectedItem(stored);
                    grid.add(wr.combo, wc);
                }
                case MAP_NAME -> {
                    wr.combo = new JComboBox<>(buildMapOptions());
                    styleCombo(wr.combo);
                    if (stored != null && !stored.isEmpty()) wr.combo.setSelectedItem(stored);
                    grid.add(wr.combo, wc);
                }
                case ITEM_ANY -> {
                    wr.combo = new JComboBox<>(buildItemOptions());
                    styleCombo(wr.combo);
                    selectByPrefix(wr.combo, stored);
                    grid.add(wr.combo, wc);
                }
                case MONSTER -> {
                    wr.combo = new JComboBox<>(buildMonsterOptions());
                    styleCombo(wr.combo);
                    selectByPrefix(wr.combo, stored);
                    grid.add(wr.combo, wc);
                }
                case GOD -> {
                    wr.combo = new JComboBox<>(buildGodOptions());
                    styleCombo(wr.combo);
                    if (stored != null && !stored.isEmpty()) selectByPrefix(wr.combo, stored.toUpperCase());
                    grid.add(wr.combo, wc);
                }
                case DIRECTION -> {
                    wr.combo = new JComboBox<>(new String[]{"(default)", "north", "south", "east", "west"});
                    styleCombo(wr.combo);
                    if (stored != null && !stored.isEmpty()) wr.combo.setSelectedItem(stored.toLowerCase());
                    grid.add(wr.combo, wc);
                }
            }

            rows.add(wr);
        }

        outer.add(grid, BorderLayout.NORTH);
        return outer;
    }

    private JPanel buildActions() {
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 6));
        actions.setBackground(BG);
        actions.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER));

        JButton saveBtn   = makeButton("Save");
        JButton cancelBtn = makeButton("Cancel");
        saveBtn.addActionListener(e -> saveAndClose());
        cancelBtn.addActionListener(e -> dispose());

        actions.add(saveBtn);
        actions.add(cancelBtn);
        return actions;
    }

    // ── Save logic ────────────────────────────────────────────────────────────

    private void saveAndClose() {
        if (map.initialTileStates == null) map.initialTileStates = new HashMap<>();

        String    key    = tx + "," + ty;
        TileState before = map.initialTileStates.get(key);

        // Merge into the stored state rather than replacing it: keys this form does not
        // render (dungeonName, chuteLevel, ...) must survive an edit.  A rendered field
        // left empty clears just that key.
        TileState ts = new TileState();
        if (before != null) {
            ts.overrideId = before.overrideId;
            ts.data.putAll(before.data);
        }
        for (WidgetRow wr : rows) {
            String value = wr.extractValue();
            if (value != null) ts.data.put(wr.def.name(), value);
            else               ts.data.remove(wr.def.name());
        }

        boolean unchanged = before != null
                && before.overrideId == ts.overrideId
                && before.data.equals(ts.data);

        if (ts.data.isEmpty() && !ts.hasOverride()) {
            map.initialTileStates.remove(key);
            if (before != null) canvas.pushCommand(new MapCommand.TileStateCommand(tx, ty, before, null));
        } else {
            map.initialTileStates.put(key, ts);
            if (!unchanged) canvas.pushCommand(new MapCommand.TileStateCommand(tx, ty, before, ts));
        }

        canvas.repaint();
        dispose();
    }

    // ── Combo/option builders ─────────────────────────────────────────────────

    private static String[] buildTileOptions() {
        List<TileDefinition> tiles = TileRegistry.getAllTiles();
        String[] opts = new String[tiles.size() + 1];
        opts[0] = "(default)";
        for (int i = 0; i < tiles.size(); i++) {
            TileDefinition t = tiles.get(i);
            opts[i + 1] = "[" + t.getId() + "] " + t.getName();
        }
        return opts;
    }

    private static String[] buildKeyOptions() {
        List<String> keys = new ArrayList<>();
        keys.add("(default)");
        // Filter on the item TYPE, matching TileEditor.populateKeyCombo. Filtering on slotType
        // instead excluded rusty_key, iron_key and magic_crystal — all three are type=KEY with
        // slotType=MISC, and two of them lock cells in shipped maps. Their combo stayed on
        // "(default)", so merely opening a locked cell and saving stripped its requiredKeyId.
        for (Item item : ItemRegistry.getAllItems()) {
            if (item.getType() == Item.Type.KEY) {
                keys.add(item.getId());
            }
        }
        return keys.toArray(new String[0]);
    }

    private static String[] buildItemOptions() {
        List<String> opts = new ArrayList<>();
        opts.add("(default)");
        List<Item> items = new ArrayList<>(ItemRegistry.getAllItems());
        items.sort(java.util.Comparator.comparing(Item::getName, String.CASE_INSENSITIVE_ORDER));
        for (Item item : items) {
            opts.add(item.getId() + " — " + item.getName());
        }
        return opts.toArray(new String[0]);
    }

    private static String[] buildMonsterOptions() {
        List<String> opts = new ArrayList<>();
        opts.add("(default)");
        List<Monster> mons = new ArrayList<>(MonsterRegistry.getAllMonsters());
        mons.sort(java.util.Comparator.comparing(Monster::getName, String.CASE_INSENSITIVE_ORDER));
        for (Monster m : mons) {
            opts.add(m.getId() + " — " + m.getName() + " (L" + m.getLevel() + ")");
        }
        return opts.toArray(new String[0]);
    }

    private static String[] buildGodOptions() {
        List<String> opts = new ArrayList<>();
        opts.add("(default)");
        for (God g : God.values()) {
            opts.add(g.name() + " — " + g.displayName);
        }
        return opts.toArray(new String[0]);
    }

    /** Selects the first option whose prefix (before " — ") equals the given id. */
    private static void selectByPrefix(JComboBox<String> combo, String id) {
        if (id == null || id.isEmpty()) return;
        for (int i = 0; i < combo.getItemCount(); i++) {
            String opt = combo.getItemAt(i);
            int dash = opt.indexOf(" — ");
            String prefix = dash > 0 ? opt.substring(0, dash) : opt;
            if (prefix.equals(id)) {
                combo.setSelectedIndex(i);
                return;
            }
        }
    }

    private static String[] buildMapOptions() {
        List<String> maps = new ArrayList<>();
        maps.add("(none)");
        java.io.File owDir = new java.io.File("data/overworlds");
        if (owDir.isDirectory()) {
            java.io.File[] files = owDir.listFiles((d, n) -> n.endsWith(".rfmap"));
            if (files != null) {
                java.util.Arrays.sort(files);
                for (java.io.File f : files) maps.add(f.getName().replace(".rfmap", ""));
            }
        }
        return maps.toArray(new String[0]);
    }

    // ── Widget styling helpers ────────────────────────────────────────────────

    private void styleCombo(JComboBox<String> combo) {
        combo.setBackground(new Color(35, 42, 65));
        combo.setForeground(new Color(220, 220, 255));
        combo.setFont(F_LABEL);
    }

    private void styleSpinner(JSpinner spinner) {
        spinner.setBackground(new Color(35, 42, 65));
        spinner.setForeground(new Color(220, 220, 255));
        spinner.getEditor().setBackground(new Color(35, 42, 65));
        ((JSpinner.DefaultEditor) spinner.getEditor()).getTextField()
                .setBackground(new Color(35, 42, 65));
        ((JSpinner.DefaultEditor) spinner.getEditor()).getTextField()
                .setForeground(new Color(220, 220, 255));
        ((JSpinner.DefaultEditor) spinner.getEditor()).getTextField()
                .setCaretColor(AMBER);
    }

    private void styleTextField(JTextField tf) {
        tf.setBackground(new Color(35, 42, 65));
        tf.setForeground(new Color(220, 220, 255));
        tf.setCaretColor(AMBER);
        tf.setFont(F_LABEL);
        tf.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                BorderFactory.createEmptyBorder(2, 4, 2, 4)));
    }

    private JButton makeButton(String label) {
        JButton btn = new JButton(label);
        btn.setBackground(new Color(35, 42, 65));
        btn.setForeground(AMBER);
        btn.setFont(F_LABEL);
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                BorderFactory.createEmptyBorder(3, 8, 3, 8)));
        return btn;
    }
}

package io.cannonforge.retroquest.editor;
import static io.cannonforge.retroquest.editor.EditorTheme.ACCENT;
import static io.cannonforge.retroquest.editor.EditorTheme.AMBER;
import static io.cannonforge.retroquest.editor.EditorTheme.BG;
import static io.cannonforge.retroquest.editor.EditorTheme.BORDER_COL;
import static io.cannonforge.retroquest.editor.EditorTheme.DANGER;
import static io.cannonforge.retroquest.editor.EditorTheme.MONO_MD;
import static io.cannonforge.retroquest.editor.EditorTheme.MONO_SM;
import static io.cannonforge.retroquest.editor.EditorTheme.MONO_XS;
import static io.cannonforge.retroquest.editor.EditorTheme.PANEL_BG;
import static io.cannonforge.retroquest.editor.EditorTheme.PHOSPHOR;
import static io.cannonforge.retroquest.editor.EditorTheme.PHOSPHOR2;
import static io.cannonforge.retroquest.editor.EditorTheme.SEL_BG;
import static io.cannonforge.retroquest.editor.EditorTheme.TEXT_BRIGHT;
import static io.cannonforge.retroquest.editor.EditorTheme.TEXT_DIM;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ScrollPaneConstants;
import javax.swing.SpinnerNumberModel;
import javax.swing.plaf.basic.BasicSplitPaneDivider;

import io.cannonforge.retroquest.model.Effect;
import io.cannonforge.retroquest.model.Item;
import io.cannonforge.retroquest.model.ItemSlot;
import io.cannonforge.retroquest.model.Player;
import io.cannonforge.retroquest.model.Rarity;
import io.cannonforge.retroquest.model.Spell;
import io.cannonforge.retroquest.registry.ImageAssetRegistry;
import io.cannonforge.retroquest.registry.ItemRegistry;

@SuppressWarnings("serial")
public class ItemEditorDialog extends JDialog {

    // ── Fields ────────────────────────────────────────────────────────────────
    private final DefaultListModel<String> listModel = new DefaultListModel<>();
    private final JList<String> itemList = new JList<>(listModel);

    private final JTextField           idField       = darkField(15);
    private final JTextField           nameField     = darkField(25);
    private final JComboBox<Item.Type> typeCombo     = darkCombo(Item.Type.values());
    private final JComboBox<ItemSlot>  slotCombo     = darkCombo(ItemSlot.values());
    private final JSpinner             valueSpinner  = darkSpinner(10,  0,  1000,  5);
    private final JSpinner             priceSpinner  = darkSpinner(50,  0, 10000, 25);
    private final JSpinner             tierSpinner   = darkSpinner( 1,  1,    20,  1);
    private final JComboBox<Rarity>    rarityCombo   = darkCombo(Rarity.values());
    private final JSpinner             stackSpinner  = darkSpinner( 1,  1,   999,  1);
    private final JCheckBox            lootableCheck  = darkCheckBox("Can drop in dungeon chests");
    private final JCheckBox            shopCheck      = darkCheckBox("Available in shops");
    private final JTextArea            descArea      = darkTextArea(5, 40);
    private final JComboBox<String>    spriteCombo   = buildSpriteCombo();
    private final JComboBox<String>    spellCombo    = buildSpellCombo();
    private final JSpinner             chargeSpinner = darkSpinner(0, 0, 99, 1);
    private final JLabel               spellLabel    = sectionLabel("SPELL:");
    private final JLabel               chargeLabel   = sectionLabel("CHARGES:");

    // Effects editing state
    private final DefaultListModel<String>    effectListModel  = new DefaultListModel<>();
    private final JList<String>               effectJList      = new JList<>(effectListModel);
    private final java.util.List<Effect>      editingEffects   = new java.util.ArrayList<>();

    private JLabel statusLabel;
    private JLabel valueLabelRef;

    // ── Constructor ───────────────────────────────────────────────────────────
    public ItemEditorDialog(RetroForge editor) { this(editor, true); }

    /**
     * @param show false builds the dialog without showing it — the offscreen entry point
     *        {@code RetroRecorder} uses to film this editor. It is modal, so a recorder that
     *        used the public constructor would block forever on the calling thread.
     */
    public ItemEditorDialog(RetroForge editor, boolean show) {
        super(editor, "RETROQUEST  \u00b7  ITEM  EDITOR", true);
        setSize(980, 680);
        setMinimumSize(new Dimension(820, 540));
        setLocationRelativeTo(editor);
        getContentPane().setBackground(BG);

        buildUI();
        refreshItemList();

        // Wire list selection
        itemList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) loadSelectedItem();
        });

        // Wire type combo to show/hide spell fields
        typeCombo.addActionListener(e -> updateSpellFieldVisibility());
        updateSpellFieldVisibility();

        // Keyboard shortcut: Ctrl+S saves
        getRootPane().registerKeyboardAction(
            e -> saveCurrentItem(),
            KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK),
            JComponent.WHEN_IN_FOCUSED_WINDOW);

        setVisible(show);
    }

    // ── UI ────────────────────────────────────────────────────────────────────
    private void buildUI() {
        setLayout(new BorderLayout(0, 0));
        add(buildTitleBar(),  BorderLayout.NORTH);
        add(buildMainArea(),  BorderLayout.CENTER);
        add(buildStatusBar(), BorderLayout.SOUTH);
    }

    private JPanel buildTitleBar() {
        return EditorTheme.buildTitleBar("ITEM  EDITOR", PHOSPHOR, "RETROQUEST  ITEM  REGISTRY");
    }

    private JPanel buildMainArea() {
        JPanel p = new JPanel(new BorderLayout(0, 0));
        p.setBackground(BG);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                                          buildItemListPanel(),
                                          buildFormPanel());
        split.setDividerLocation(300);
        split.setDividerSize(3);
        split.setBorder(null);
        var splitUI = new javax.swing.plaf.basic.BasicSplitPaneUI() {
            @Override public BasicSplitPaneDivider createDefaultDivider() {
                var divider = new BasicSplitPaneDivider(this) {
                    @Override public void paint(Graphics g) {
                        g.setColor(BORDER_COL);
                        g.fillRect(0, 0, getWidth(), getHeight());
                    }
                };
                return divider;
            }
        };
        split.setUI(splitUI);

        p.add(split, BorderLayout.CENTER);
        return p;
    }

    // ── Item list (left) ──────────────────────────────────────────────────────
    private JPanel buildItemListPanel() {
        JPanel outer = new JPanel(new BorderLayout(0, 0));
        outer.setBackground(PANEL_BG);
        outer.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, BORDER_COL));
        outer.setPreferredSize(new Dimension(300, 0));

        JLabel hdr = makeLabel("\u25c8  ALL  ITEMS", PHOSPHOR);
        hdr.setFont(MONO_MD);
        hdr.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 8));
        outer.add(hdr, BorderLayout.NORTH);

        // Styled list
        itemList.setBackground(BG);
        itemList.setForeground(TEXT_BRIGHT);
        itemList.setFont(MONO_SM);
        itemList.setSelectionBackground(SEL_BG);
        itemList.setSelectionForeground(PHOSPHOR);
        itemList.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
        var cellRenderer = new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value,
                    int index, boolean isSelected, boolean cellHasFocus) {
                JLabel l = (JLabel) super.getListCellRendererComponent(
                        list, value, index, isSelected, cellHasFocus);
                l.setBackground(isSelected ? SEL_BG : (index % 2 == 0 ? BG : new Color(13, 16, 22)));
                l.setFont(MONO_SM);

                // Colour by rarity when not selected
                if (!isSelected && index < ItemRegistry.getAllItems().size()) {
                    Rarity r = ItemRegistry.getAllItems().get(index).getRarity();
                    l.setForeground(r != Rarity.COMMON ? r.getColor() : TEXT_BRIGHT);
                } else {
                    l.setForeground(isSelected ? PHOSPHOR : TEXT_BRIGHT);
                }

                if (isSelected) {
                    l.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 3, 0, 0, PHOSPHOR),
                        BorderFactory.createEmptyBorder(3, 6, 3, 8)));
                } else {
                    l.setBorder(BorderFactory.createEmptyBorder(3, 10, 3, 8));
                }
                return l;
            }
        };
        itemList.setCellRenderer(cellRenderer);

        JScrollPane sp = new JScrollPane(itemList);
        sp.setBorder(BorderFactory.createEmptyBorder());
        sp.setBackground(BG);
        sp.getViewport().setBackground(BG);
        sp.getVerticalScrollBar().setUI(new RetroScrollBarUI());
        outer.add(sp, BorderLayout.CENTER);

        // Bottom buttons
        JPanel btns = new JPanel(new GridLayout(1, 2, 6, 0));
        btns.setBackground(PANEL_BG);
        btns.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER_COL),
            BorderFactory.createEmptyBorder(8, 10, 8, 10)));

        JButton newBtn    = bigButton("\u2736  NEW ITEM", PHOSPHOR, new Color(0, 40, 20));
        JButton deleteBtn = bigButton("\u2715  DELETE",   DANGER,   new Color(40, 0, 0));
        newBtn.addActionListener(e    -> createNewItem());
        deleteBtn.addActionListener(e -> deleteSelected());
        btns.add(newBtn);
        btns.add(deleteBtn);
        outer.add(btns, BorderLayout.SOUTH);

        return outer;
    }

    // ── Form panel (right) ────────────────────────────────────────────────────
    private JPanel buildFormPanel() {
        JPanel outer = new JPanel(new BorderLayout(0, 0));
        outer.setBackground(PANEL_BG);

        JLabel hdr = makeLabel("\u25c8  ITEM  PROPERTIES", PHOSPHOR);
        hdr.setFont(MONO_MD);
        hdr.setBorder(BorderFactory.createEmptyBorder(10, 16, 10, 8));
        outer.add(hdr, BorderLayout.NORTH);

        // GridBag form
        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(PANEL_BG);
        form.setBorder(BorderFactory.createEmptyBorder(4, 16, 4, 16));

        GridBagConstraints gl = new GridBagConstraints();
        gl.gridx = 0; gl.anchor = GridBagConstraints.WEST;
        gl.insets = new Insets(5, 0, 5, 14);

        GridBagConstraints gf = new GridBagConstraints();
        gf.gridx = 1; gf.fill = GridBagConstraints.HORIZONTAL;
        gf.weightx = 1.0; gf.insets = new Insets(5, 0, 5, 0);

        String[] labels = {
            "ID:", "NAME:", "TYPE:", "SLOT:",
            "VALUE:", "PRICE:", "TIER:", "RARITY:",
            "MAX STACK:", "LOOTABLE:", "IN SHOP:", "SPRITE:"
        };
        JComponent[] fields = {
            idField, nameField, typeCombo, slotCombo,
            valueSpinner, priceSpinner, tierSpinner, rarityCombo,
            stackSpinner, lootableCheck, shopCheck, spriteCombo
        };

        for (int i = 0; i < fields.length; i++) {
            gl.gridy = gf.gridy = i;
            JLabel rowLabel = sectionLabel(labels[i]);
            if (labels[i].equals("VALUE:")) valueLabelRef = rowLabel;
            form.add(rowLabel, gl);
            form.add(fields[i], gf);
        }

        // Spell name (for SCROLL/WAND)
        int nextRow = fields.length;
        gl.gridy = gf.gridy = nextRow;
        form.add(spellLabel, gl);
        form.add(spellCombo, gf);

        // Charges (for WAND)
        nextRow++;
        gl.gridy = gf.gridy = nextRow;
        form.add(chargeLabel, gl);
        form.add(chargeSpinner, gf);

        // Effects
        nextRow++;
        gl.gridy = gf.gridy = nextRow;
        gl.anchor = GridBagConstraints.NORTHWEST;
        form.add(sectionLabel("EFFECTS:"), gl);
        gf.fill = GridBagConstraints.HORIZONTAL;
        form.add(buildEffectsPanel(), gf);

        // Description
        nextRow++;
        gl.gridy = gf.gridy = nextRow;
        form.add(sectionLabel("DESC:"), gl);

        JScrollPane descScroll = new JScrollPane(descArea);
        descScroll.setBorder(BorderFactory.createLineBorder(BORDER_COL));
        descScroll.setBackground(BG);
        descScroll.getViewport().setBackground(BG);
        descScroll.getVerticalScrollBar().setUI(new RetroScrollBarUI());
        gf.fill = GridBagConstraints.BOTH;
        gf.weighty = 1.0;
        form.add(descScroll, gf);

        JScrollPane formScroll = new JScrollPane(form,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        formScroll.setBorder(BorderFactory.createEmptyBorder());
        formScroll.setBackground(PANEL_BG);
        formScroll.getViewport().setBackground(PANEL_BG);
        formScroll.getVerticalScrollBar().setUI(new RetroScrollBarUI());
        outer.add(formScroll, BorderLayout.CENTER);

        // Save bar
        JPanel savebar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 8));
        savebar.setBackground(new Color(8, 12, 20));
        savebar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER_COL));

        JLabel hint = makeLabel("Ctrl+S to save", TEXT_DIM);
        hint.setFont(MONO_XS);
        savebar.add(hint);

        JButton saveBtn = bigButton("\ud83d\udcbe  SAVE  ITEM", AMBER, new Color(40, 25, 0));
        saveBtn.setPreferredSize(new Dimension(200, 34));
        saveBtn.addActionListener(e -> saveCurrentItem());
        savebar.add(saveBtn);

        outer.add(savebar, BorderLayout.SOUTH);
        return outer;
    }

    // ── Status bar ────────────────────────────────────────────────────────────
    private JPanel buildStatusBar() {
        statusLabel = new JLabel();
        return EditorTheme.buildStatusBar(statusLabel, "Ctrl+S  save  |  Del  delete  ");
    }

    // ── Business logic ────────────────────────────────────────────────────────
    private void refreshItemList() {
        listModel.clear();
        for (Item i : ItemRegistry.getAllItems())
            listModel.addElement(String.format("[T%d] %s  \u2014  %s",
                i.getTier(), i.getRarity().getDisplayName(), i.getName()));
    }

    private void loadSelectedItem() {
        int idx = itemList.getSelectedIndex();
        if (idx < 0 || idx >= ItemRegistry.getAllItems().size()) return;
        Item i = ItemRegistry.getAllItems().get(idx);
        idField.setText(i.getId());
        nameField.setText(i.getName());
        typeCombo.setSelectedItem(i.getType());
        slotCombo.setSelectedItem(i.getSlotType());
        valueSpinner.setValue(i.getValue());
        priceSpinner.setValue(i.getBasePrice());
        tierSpinner.setValue(i.getTier());
        rarityCombo.setSelectedItem(i.getRarity());
        stackSpinner.setValue(i.getMaxStackSize());
        lootableCheck.setSelected(i.isLootable());
        shopCheck.setSelected(i.isShopAvailable());
        descArea.setText(i.getDescription());
        // Item sprite names never match a registry key — there is no items/ folder under
        // src/main/resources/tiles at all — so a plain setSelectedItem was a no-op on every one
        // of the 140 items and the next save wrote the previous item's sprite over this one.
        EditorCombos.selectOrKeep(spriteCombo, i.getSpriteName() != null ? i.getSpriteName() : "");
        spellCombo.setSelectedItem(i.getSpellName() != null ? i.getSpellName() : "");
        chargeSpinner.setValue(i.getCharges());
        editingEffects.clear();
        editingEffects.addAll(i.getEffects());
        effectListModel.clear();
        for (Effect e : editingEffects) effectListModel.addElement(effectDisplayString(e));
        updateSpellFieldVisibility();
        setStatus("Editing: " + i.getName());
    }

    private void createNewItem() {
        Item item = new Item("New Item", Item.Type.MISC, ItemSlot.MISC, 10, null);
        ItemRegistry.addItem(item);
        refreshItemList();
        itemList.setSelectedIndex(listModel.size() - 1);
        nameField.requestFocus();
        nameField.selectAll();
        setStatus("New item created — fill in details and save");
    }

    private void deleteSelected() {
        int idx = itemList.getSelectedIndex();
        if (idx < 0 || idx >= ItemRegistry.getAllItems().size()) return;
        String id = ItemRegistry.getAllItems().get(idx).getId();
        if (JOptionPane.showConfirmDialog(this,
                "Delete item \"" + id + "\"?", "Confirm Delete",
                JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
            ItemRegistry.removeItem(idx);
            refreshItemList();
            setStatus("Deleted: " + id);
        }
    }

    private void saveCurrentItem() {
        int idx = itemList.getSelectedIndex();
        if (idx < 0) { setStatus("Select an item first"); return; }

        String id = idField.getText().trim();
        if (id.isEmpty()) { setStatus("ID cannot be empty"); return; }

        // ID uniqueness check — prevent duplicate IDs across items
        String originalId = ItemRegistry.getAllItems().get(idx).getId();
        if (!id.equals(originalId)) {
            if (ItemRegistry.getById(id) != null) {
                setStatus("\u26a0  Item ID '" + id + "' already exists \u2014 choose a different ID");
                return;
            }
        }

        Item updated = new Item(
            id,
            nameField.getText().trim(),
            (Item.Type) typeCombo.getSelectedItem(),
            (ItemSlot)  slotCombo.getSelectedItem(),
            (Integer)   valueSpinner.getValue(),
            (Integer)   priceSpinner.getValue(),
            descArea.getText().trim(),
            spriteCombo.getSelectedItem() != null ? ((String) spriteCombo.getSelectedItem()).trim() : "",
            (Integer)   stackSpinner.getValue(),
            null
        )
        .withEffects(editingEffects)
        .withTier((Integer) tierSpinner.getValue())
        .withRarity((Rarity) rarityCombo.getSelectedItem())
        .withLootable(lootableCheck.isSelected())
        .withShopAvailable(shopCheck.isSelected())
        .withSpellName(spellCombo.getSelectedItem() instanceof String s && !s.isEmpty() ? s : null)
        .withCharges((Integer) chargeSpinner.getValue());

        ItemRegistry.updateItem(idx, updated);
        refreshItemList();
        itemList.setSelectedIndex(idx);
        setStatus("Saved: " + updated.getName());
    }

    private void updateSpellFieldVisibility() {
        Item.Type type = (Item.Type) typeCombo.getSelectedItem();
        boolean showSpell   = type == Item.Type.SCROLL || type == Item.Type.WAND;
        boolean showCharges = type == Item.Type.WAND;
        spellLabel.setVisible(showSpell);
        spellCombo.setVisible(showSpell);
        chargeLabel.setVisible(showCharges);
        chargeSpinner.setVisible(showCharges);
        if (valueLabelRef != null) {
            String vLabel = switch (type) {
                case WEAPON              -> "DMG BONUS:";
                case ARMOR, HELM, SHIELD -> "AC BONUS:";
                case POTION              -> "HEALS HP:";
                case FOOD                -> "RESTORES FOOD:";
                default                  -> "VALUE:";
            };
            valueLabelRef.setText(vLabel);
        }
    }

    private static JComboBox<String> buildSpriteCombo() {
        JComboBox<String> cb = new JComboBox<>();
        cb.addItem("");  // none
        java.util.List<String> keys = new java.util.ArrayList<>(ImageAssetRegistry.getAllSpriteKeys());
        java.util.Collections.sort(keys);
        for (String key : keys) cb.addItem(key);
        cb.setBackground(PANEL_BG);
        cb.setForeground(TEXT_BRIGHT);
        cb.setFont(MONO_SM);
        cb.setEditable(false);
        return cb;
    }

    private static JComboBox<String> buildSpellCombo() {
        JComboBox<String> cb = new JComboBox<>();
        cb.addItem("");  // none
        Player tmp = new Player();
        for (Spell s : tmp.getKnownSpells()) {
            if (s != null) cb.addItem(s.getName());
        }
        cb.setBackground(PANEL_BG);
        cb.setForeground(TEXT_BRIGHT);
        cb.setFont(MONO_SM);
        return cb;
    }

    private void setStatus(String msg) {
        EditorTheme.setStatus(statusLabel, msg);
    }

    // ── Effects panel ─────────────────────────────────────────────────────────
    private JPanel buildEffectsPanel() {
        effectJList.setBackground(new Color(8, 12, 20));
        effectJList.setForeground(PHOSPHOR2);
        effectJList.setFont(MONO_SM);
        effectJList.setSelectionBackground(SEL_BG);
        effectJList.setSelectionForeground(PHOSPHOR);
        effectJList.setVisibleRowCount(3);

        JScrollPane sp = new JScrollPane(effectJList);
        sp.setBorder(BorderFactory.createLineBorder(BORDER_COL));
        sp.setBackground(new Color(8, 12, 20));
        sp.getViewport().setBackground(new Color(8, 12, 20));

        JButton addBtn = bigButton("+", PHOSPHOR, new Color(0, 40, 20));
        addBtn.setPreferredSize(new Dimension(36, 28));
        addBtn.addActionListener(e -> addEffect());

        JButton removeBtn = bigButton("\u2212", DANGER, new Color(40, 0, 0));
        removeBtn.setPreferredSize(new Dimension(36, 28));
        removeBtn.addActionListener(e -> removeSelectedEffect());

        JPanel btns = new JPanel(new GridLayout(2, 1, 0, 4));
        btns.setBackground(PANEL_BG);
        btns.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 0));
        btns.add(addBtn);
        btns.add(removeBtn);

        JPanel p = new JPanel(new BorderLayout(0, 0));
        p.setBackground(PANEL_BG);
        p.add(sp,   BorderLayout.CENTER);
        p.add(btns, BorderLayout.EAST);
        return p;
    }

    private void addEffect() {
        JComboBox<Effect.EffectType> typeBox = new JComboBox<>(Effect.EffectType.values());
        typeBox.setSelectedItem(Effect.EffectType.PROTECTION);
        JSpinner valSpin = new JSpinner(new SpinnerNumberModel(1, 1, 20, 1));

        JPanel panel = new JPanel(new GridLayout(2, 2, 8, 6));
        panel.add(new JLabel("Type:"));  panel.add(typeBox);
        panel.add(new JLabel("Value:")); panel.add(valSpin);

        int result = JOptionPane.showConfirmDialog(this, panel, "Add Effect",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result == JOptionPane.OK_OPTION) {
            Effect.EffectType type = (Effect.EffectType) typeBox.getSelectedItem();
            int val = (Integer) valSpin.getValue();
            Effect fx = new Effect(type, val);
            editingEffects.add(fx);
            effectListModel.addElement(effectDisplayString(fx));
        }
    }

    private void removeSelectedEffect() {
        int idx = effectJList.getSelectedIndex();
        if (idx >= 0) {
            editingEffects.remove(idx);
            effectListModel.remove(idx);
        }
    }

    private static String effectDisplayString(Effect e) {
        String perm = e.isPermanent() ? " (permanent)" : "";
        return switch (e.getType()) {
            case PROTECTION   -> "PROTECTION  +" + e.getValue() + " AC" + perm;
            case REGENERATION -> "REGENERATION  +" + e.getValue() + " HP/move" + perm;
            case HEALING      -> "HEALING  +" + e.getValue() + " HP";
            case SPELL_RESIST -> "SPELL_RESIST  +" + e.getValue() + "%" + perm;
            case DAMAGE       -> "DAMAGE  +" + e.getValue() + perm;
        };
    }

    // ── Styled component factories ────────────────────────────────────────────
    private static JTextField darkField(int cols) {
        JTextField f = new JTextField(cols);
        f.setBackground(new Color(8, 12, 20));
        f.setForeground(PHOSPHOR);
        f.setCaretColor(PHOSPHOR);
        f.setFont(MONO_SM);
        f.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_COL),
            BorderFactory.createEmptyBorder(3, 6, 3, 6)));
        return f;
    }

    private static <T> JComboBox<T> darkCombo(T[] items) {
        JComboBox<T> cb = new JComboBox<>(items);
        cb.setBackground(PANEL_BG);
        cb.setForeground(TEXT_BRIGHT);
        cb.setFont(MONO_SM);
        return cb;
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

    private static JTextArea darkTextArea(int rows, int cols) {
        JTextArea ta = new JTextArea(rows, cols);
        ta.setBackground(new Color(8, 12, 20));
        ta.setForeground(PHOSPHOR);
        ta.setCaretColor(PHOSPHOR);
        ta.setFont(MONO_SM);
        ta.setLineWrap(true);
        ta.setWrapStyleWord(true);
        ta.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        return ta;
    }

    private static JCheckBox darkCheckBox(String label) {
        JCheckBox cb = new JCheckBox(label, true);
        cb.setBackground(new Color(8, 12, 20));
        cb.setForeground(PHOSPHOR);
        cb.setFont(MONO_SM);
        cb.setFocusPainted(false);
        return cb;
    }

    private static JLabel makeLabel(String txt, Color c) {
        JLabel l = new JLabel(txt); l.setForeground(c); l.setFont(MONO_SM); return l;
    }

    private static JLabel sectionLabel(String txt) {
        JLabel l = new JLabel(txt);
        l.setForeground(ACCENT);
        l.setFont(new Font("Monospaced", Font.BOLD, 10));
        return l;
    }

    private static JButton retroButton(String txt) {
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

    private static JButton bigButton(String txt, Color fg, Color bg) {
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

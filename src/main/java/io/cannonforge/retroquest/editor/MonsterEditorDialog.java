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
import static io.cannonforge.retroquest.editor.EditorTheme.PHOSPHOR3;
import static io.cannonforge.retroquest.editor.EditorTheme.TEXT_BRIGHT;
import static io.cannonforge.retroquest.editor.EditorTheme.TEXT_DIM;

import java.awt.BasicStroke;
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
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
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
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.plaf.basic.BasicSplitPaneDivider;

import io.cannonforge.retroquest.model.Monster;
import io.cannonforge.retroquest.model.MonsterType;
import io.cannonforge.retroquest.registry.ImageAssetRegistry;
import io.cannonforge.retroquest.registry.MonsterRegistry;

@SuppressWarnings("serial")
public class MonsterEditorDialog extends JDialog {

    // ── Spell names known to the combat system ────────────────────────────────
    private static final List<String> ALL_SPELL_NAMES = List.of(
        "Magic Missile", "Sleep", "Charm Monster",
        "Cure Light Wounds", "Protection from Evil", "Shield",
        "Fireball", "Lightning Bolt", "Invisibility",
        "Cure Serious Wounds", "Bless", "Turn Undead", "Entangle",
        "Ice Storm", "Dispel Magic", "Teleport",
        "Cure Critical Wounds", "Prayer", "Holy Word",
        "Cone of Cold", "Cloudkill", "Haste",
        "Heal", "Resist Elements", "Restoration",
        "Chain Lightning", "Death Spell", "Scry",
        "Holy Armor", "Flame Strike",
        "Meteor Swarm", "Power Word Kill", "Wish",
        "Divine Intervention", "Sanctuary",
        "Fire Breath"
    );

    // ── Fields ────────────────────────────────────────────────────────────────
    private final DefaultListModel<String> listModel = new DefaultListModel<>();
    private final JList<String> monsterList = new JList<>(listModel);
    private final List<Monster> filteredMonsters = new ArrayList<>();
    private final JTextField searchField = darkField(20);

    private final JTextField  idField      = darkField(15);
    private final JTextField  nameField    = darkField(25);
    private final JSpinner    levelSpinner = darkSpinner( 1,  1,   50,   1);
    private final JSpinner    hpSpinner    = darkSpinner(30,  5,  500,   5);
    private final JSpinner    dmgSpinner   = darkSpinner(10,  1,  100,   1);
    private final JSpinner    goldSpinner  = darkSpinner(20,  0,  500,   5);
    private final JSpinner    acSpinner    = darkSpinner(10,  0,   60,   1);
    private final JSpinner    xpSpinner    = darkSpinner(60,  0, 5000,  10);
    private final JComboBox<String> spriteCombo = new JComboBox<>();
    private final JComboBox<MonsterType> typeCombo = new JComboBox<>(MonsterType.values());
    private final JCheckBox friendlyCheck = new JCheckBox("Offers gift, no combat");
    private final JCheckBox aquaticCheck  = new JCheckBox("Spawns near water tiles");

    // Spell fields
    private final JSpinner castChanceSpinner = darkSpinner(0, 0, 100, 5);
    private final JSpinner spellPowerSpinner  = darkSpinner(0, 0, 999, 5);
    private final JSpinner spellResistSpinner = darkSpinner(0, 0,  40, 1);
    private final Map<String, JCheckBox> spellCheckboxes = new LinkedHashMap<>();

    // Sprite preview panel — custom painted for CRT look
    private BufferedImage previewImage = null;
    private final JPanel spritePreviewPanel = new JPanel() {
        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

            // Checkerboard background (transparency indicator)
            int cs = 8;
            for (int py = 0; py < getHeight(); py += cs)
                for (int px = 0; px < getWidth(); px += cs) {
                    g2.setColor((px/cs + py/cs) % 2 == 0
                        ? new Color(40, 44, 52) : new Color(28, 32, 40));
                    g2.fillRect(px, py, cs, cs);
                }

            if (previewImage != null) {
                // Scale to fill, preserving aspect
                int iw = previewImage.getWidth(), ih = previewImage.getHeight();
                int pw = getWidth() - 8, ph = getHeight() - 8;
                float scale = Math.min((float) pw / iw, (float) ph / ih);
                int dw = (int)(iw * scale), dh = (int)(ih * scale);
                int ox = (getWidth() - dw) / 2, oy = (getHeight() - dh) / 2;
                g2.drawImage(previewImage, ox, oy, dw, dh, null);
                // Phosphor border glow
                g2.setColor(new Color(0, 180, 80, 80));
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawRect(ox - 1, oy - 1, dw + 2, dh + 2);
            } else {
                g2.setColor(TEXT_DIM);
                g2.setFont(MONO_XS);
                String msg = "NO SPRITE";
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(msg,
                    (getWidth() - fm.stringWidth(msg)) / 2,
                    getHeight() / 2 + fm.getAscent() / 2);
            }

            // Outer border
            g2.setColor(BORDER_COL);
            g2.setStroke(new BasicStroke(1f));
            g2.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
        }
    };

    private JLabel statusLabel;

    // ── Constructor ───────────────────────────────────────────────────────────
    public MonsterEditorDialog(RetroForge editor) { this(editor, true); }

    /**
     * @param show false builds the dialog without showing it — the offscreen entry point
     *        {@code RetroRecorder} uses to film this editor. It is modal, so a recorder that
     *        used the public constructor would block forever on the calling thread.
     */
    public MonsterEditorDialog(RetroForge editor, boolean show) {
        super(editor, "RETROQUEST  \u00b7  MONSTER  EDITOR", true);
        setSize(1020, 720);
        setMinimumSize(new Dimension(860, 560));
        setLocationRelativeTo(editor);
        getContentPane().setBackground(BG);

        // Style sprite combo
        spriteCombo.setBackground(PANEL_BG);
        spriteCombo.setForeground(TEXT_BRIGHT);
        spriteCombo.setFont(MONO_SM);

        // Style type combo
        typeCombo.setBackground(PANEL_BG);
        typeCombo.setForeground(TEXT_BRIGHT);
        typeCombo.setFont(MONO_SM);

        // Style friendly checkbox
        friendlyCheck.setBackground(PANEL_BG);
        friendlyCheck.setForeground(PHOSPHOR);
        friendlyCheck.setFont(MONO_SM);

        // Style aquatic checkbox
        aquaticCheck.setBackground(PANEL_BG);
        aquaticCheck.setForeground(PHOSPHOR);
        aquaticCheck.setFont(MONO_SM);

        loadSpriteCombo();
        buildUI();
        refreshMonsterList();

        // Listeners
        monsterList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) loadSelectedMonster();
        });
        spriteCombo.addActionListener(e -> updateSpritePreview());
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
			public void insertUpdate(DocumentEvent e)  { refreshMonsterList(); }
            @Override
			public void removeUpdate(DocumentEvent e)  { refreshMonsterList(); }
            @Override
			public void changedUpdate(DocumentEvent e) { refreshMonsterList(); }
        });

        // Ctrl+S saves
        getRootPane().registerKeyboardAction(
            e -> saveCurrentMonster(),
            KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_S,
                java.awt.event.InputEvent.CTRL_DOWN_MASK),
            JComponent.WHEN_IN_FOCUSED_WINDOW);

        setVisible(show);
    }

    // ── UI construction ───────────────────────────────────────────────────────
    private void buildUI() {
        setLayout(new BorderLayout(0, 0));
        add(buildTitleBar(),  BorderLayout.NORTH);
        add(buildMainArea(),  BorderLayout.CENTER);
        add(buildStatusBar(), BorderLayout.SOUTH);
    }

    private JPanel buildTitleBar() {
        return EditorTheme.buildTitleBar("MONSTER  EDITOR", DANGER, "RETROQUEST  MONSTER  REGISTRY");
    }

    private JPanel buildMainArea() {
        JPanel p = new JPanel(new BorderLayout(0, 0));
        p.setBackground(BG);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                                          buildMonsterListPanel(),
                                          buildFormPanel());
        split.setDividerLocation(320);
        split.setDividerSize(3);
        split.setBorder(null);
        split.setUI(new javax.swing.plaf.basic.BasicSplitPaneUI() {
            @Override public BasicSplitPaneDivider createDefaultDivider() {
                return new BasicSplitPaneDivider(this) {
                    @Override public void paint(Graphics g) {
                        g.setColor(BORDER_COL);
                        g.fillRect(0, 0, getWidth(), getHeight());
                    }
                };
            }
        });
        p.add(split, BorderLayout.CENTER);
        return p;
    }

    // ── Monster list (left) ───────────────────────────────────────────────────
    private JPanel buildMonsterListPanel() {
        JPanel outer = new JPanel(new BorderLayout(0, 0));
        outer.setBackground(PANEL_BG);
        outer.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, BORDER_COL));
        outer.setPreferredSize(new Dimension(320, 0));

        // Header + search bar
        JPanel northWrap = new JPanel(new BorderLayout(0, 0));
        northWrap.setBackground(PANEL_BG);

        JLabel hdr = makeLabel("\u25c8  ALL  MONSTERS", DANGER);
        hdr.setFont(MONO_MD);
        hdr.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 8));
        northWrap.add(hdr, BorderLayout.NORTH);

        JPanel searchRow = new JPanel(new BorderLayout(6, 0));
        searchRow.setBackground(PANEL_BG);
        searchRow.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 1, 0, BORDER_COL),
            BorderFactory.createEmptyBorder(5, 8, 5, 8)));
        JLabel searchIcon = makeLabel("\u26b2", TEXT_DIM);
        searchIcon.setFont(MONO_MD);
        searchRow.add(searchIcon, BorderLayout.WEST);
        searchRow.add(searchField, BorderLayout.CENTER);
        northWrap.add(searchRow, BorderLayout.SOUTH);

        outer.add(northWrap, BorderLayout.NORTH);

        monsterList.setBackground(BG);
        monsterList.setForeground(TEXT_BRIGHT);
        monsterList.setFont(MONO_SM);
        monsterList.setSelectionBackground(new Color(60, 0, 0));
        monsterList.setSelectionForeground(DANGER);
        monsterList.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
        monsterList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value,
                    int index, boolean isSelected, boolean cellHasFocus) {
                JLabel l = (JLabel) super.getListCellRendererComponent(
                        list, value, index, isSelected, cellHasFocus);
                l.setBackground(isSelected ? new Color(60, 0, 0)
                    : (index % 2 == 0 ? BG : new Color(13, 16, 22)));
                l.setForeground(isSelected ? DANGER : TEXT_BRIGHT);
                l.setFont(MONO_SM);
                if (isSelected) {
                    l.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 3, 0, 0, DANGER),
                        BorderFactory.createEmptyBorder(3, 6, 3, 8)));
                } else {
                    l.setBorder(BorderFactory.createEmptyBorder(3, 10, 3, 8));
                }
                return l;
            }
        });

        JScrollPane sp = new JScrollPane(monsterList);
        sp.setBorder(BorderFactory.createEmptyBorder());
        sp.setBackground(BG);
        sp.getViewport().setBackground(BG);
        sp.getVerticalScrollBar().setUI(new RetroScrollBarUI());
        outer.add(sp, BorderLayout.CENTER);

        JPanel btns = new JPanel(new GridLayout(1, 3, 6, 0));
        btns.setBackground(PANEL_BG);
        btns.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER_COL),
            BorderFactory.createEmptyBorder(8, 10, 8, 10)));

        JButton newBtn   = bigButton("\u2736 NEW",   PHOSPHOR,  new Color(0, 40, 20));
        JButton cloneBtn = bigButton("\u2750 CLONE", PHOSPHOR2, new Color(0, 20, 40));
        JButton delBtn   = bigButton("\u2716 DEL",   DANGER,    new Color(50, 0, 0));
        newBtn.addActionListener(e -> createNewMonster());
        cloneBtn.addActionListener(e -> cloneMonster());
        delBtn.addActionListener(e -> deleteMonster());
        btns.add(newBtn);
        btns.add(cloneBtn);
        btns.add(delBtn);
        outer.add(btns, BorderLayout.SOUTH);

        return outer;
    }

    // ── Form + preview (right) ────────────────────────────────────────────────
    private JPanel buildFormPanel() {
        JPanel outer = new JPanel(new BorderLayout(0, 0));
        outer.setBackground(PANEL_BG);

        JLabel hdr = makeLabel("\u25c8  MONSTER  PROPERTIES", DANGER);
        hdr.setFont(MONO_MD);
        hdr.setBorder(BorderFactory.createEmptyBorder(10, 16, 10, 8));
        outer.add(hdr, BorderLayout.NORTH);

        // ── Tabbed pane: Stats | Spells ───────────────────────────────────────
        JTabbedPane tabs = new JTabbedPane();
        tabs.setBackground(PANEL_BG);
        tabs.setForeground(TEXT_BRIGHT);
        tabs.setFont(MONO_MD);
        tabs.addTab("STATS",  buildStatsTab());
        tabs.addTab("SPELLS", buildSpellsTab());
        outer.add(tabs, BorderLayout.CENTER);

        // Save bar
        JPanel savebar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 8));
        savebar.setBackground(new Color(8, 12, 20));
        savebar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER_COL));

        JLabel hint = makeLabel("Ctrl+S to save", TEXT_DIM);
        hint.setFont(MONO_XS);
        savebar.add(hint);

        JButton saveBtn = bigButton("\ud83d\udcbe  SAVE  MONSTER", AMBER, new Color(40, 25, 0));
        saveBtn.setPreferredSize(new Dimension(210, 34));
        saveBtn.addActionListener(e -> saveCurrentMonster());
        savebar.add(saveBtn);

        outer.add(savebar, BorderLayout.SOUTH);
        return outer;
    }

    private JPanel buildStatsTab() {
        // ── Split: form LEFT, preview RIGHT ──────────────────────────────────
        JPanel formAndPreview = new JPanel(new BorderLayout(12, 0));
        formAndPreview.setBackground(PANEL_BG);
        formAndPreview.setBorder(BorderFactory.createEmptyBorder(4, 16, 4, 16));

        // Form
        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(PANEL_BG);

        GridBagConstraints gl = new GridBagConstraints();
        gl.gridx = 0; gl.anchor = GridBagConstraints.WEST;
        gl.insets = new Insets(5, 0, 5, 14);

        GridBagConstraints gf = new GridBagConstraints();
        gf.gridx = 1; gf.fill = GridBagConstraints.HORIZONTAL;
        gf.weightx = 1.0; gf.insets = new Insets(5, 0, 5, 0);

        String[] labels = {"ID:", "NAME:", "LEVEL:", "HP:", "DAMAGE:", "GOLD:", "AC:", "XP VALUE:", "SPRITE:", "TYPE:", "FRIENDLY:", "AQUATIC:"};
        JComponent[] fields = {idField, nameField, levelSpinner, hpSpinner, dmgSpinner, goldSpinner, acSpinner, xpSpinner, spriteCombo, typeCombo, friendlyCheck, aquaticCheck};
        for (int i = 0; i < fields.length; i++) {
            gl.gridy = gf.gridy = i;
            form.add(sectionLabel(labels[i]), gl);
            form.add(fields[i], gf);
        }

        // Spacer to push form to top
        GridBagConstraints spacer = new GridBagConstraints();
        spacer.gridx = 0; spacer.gridy = fields.length;
        spacer.gridwidth = 2; spacer.weighty = 1.0;
        spacer.fill = GridBagConstraints.VERTICAL;
        form.add(Box.createVerticalGlue(), spacer);

        // Sprite preview
        JPanel previewWrap = new JPanel(new BorderLayout(0, 6));
        previewWrap.setBackground(PANEL_BG);
        previewWrap.setPreferredSize(new Dimension(160, 0));

        JLabel previewTitle = makeLabel("SPRITE  PREVIEW", ACCENT);
        previewTitle.setFont(MONO_XS);
        previewTitle.setHorizontalAlignment(SwingConstants.CENTER);
        previewWrap.add(previewTitle, BorderLayout.NORTH);

        spritePreviewPanel.setBackground(BG);
        spritePreviewPanel.setPreferredSize(new Dimension(160, 160));
        previewWrap.add(spritePreviewPanel, BorderLayout.CENTER);

        JButton editBtn = bigButton("\u270f  EDIT  SPRITE", PHOSPHOR2, new Color(0, 20, 40));
        editBtn.addActionListener(e -> openSpriteEditor());
        previewWrap.add(editBtn, BorderLayout.SOUTH);

        formAndPreview.add(form,        BorderLayout.CENTER);
        formAndPreview.add(previewWrap, BorderLayout.EAST);
        return formAndPreview;
    }

    private JPanel buildSpellsTab() {
        JPanel outer = new JPanel(new BorderLayout(0, 8));
        outer.setBackground(PANEL_BG);
        outer.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));

        // ── Spell casting stats ───────────────────────────────────────────────
        JPanel statsGrid = new JPanel(new GridBagLayout());
        statsGrid.setBackground(PANEL_BG);

        GridBagConstraints gl = new GridBagConstraints();
        gl.gridx = 0; gl.anchor = GridBagConstraints.WEST;
        gl.insets = new Insets(4, 0, 4, 14);

        GridBagConstraints gf = new GridBagConstraints();
        gf.gridx = 1; gf.fill = GridBagConstraints.HORIZONTAL;
        gf.weightx = 1.0; gf.insets = new Insets(4, 0, 4, 0);

        String[] statLabels = {"CAST CHANCE (%):", "SPELL POWER:", "SPELL RESIST (%):"};
        JSpinner[] statSpinners = {castChanceSpinner, spellPowerSpinner, spellResistSpinner};
        for (int i = 0; i < statLabels.length; i++) {
            gl.gridy = gf.gridy = i;
            statsGrid.add(sectionLabel(statLabels[i]), gl);
            statsGrid.add(statSpinners[i], gf);
        }
        outer.add(statsGrid, BorderLayout.NORTH);

        // ── Spell checklist ───────────────────────────────────────────────────
        JPanel checklistWrap = new JPanel(new BorderLayout(0, 4));
        checklistWrap.setBackground(PANEL_BG);

        JLabel spellsHdr = makeLabel("\u25c6  ASSIGN  SPELLS", PHOSPHOR3);
        spellsHdr.setFont(new Font("Monospaced", Font.BOLD, 10));
        spellsHdr.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER_COL));
        spellsHdr.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER_COL),
            BorderFactory.createEmptyBorder(4, 0, 4, 0)));
        checklistWrap.add(spellsHdr, BorderLayout.NORTH);

        JPanel grid = new JPanel(new GridLayout(0, 3, 4, 2));
        grid.setBackground(BG);
        grid.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        for (String name : ALL_SPELL_NAMES) {
            JCheckBox cb = new JCheckBox(name);
            cb.setBackground(BG);
            cb.setForeground(TEXT_BRIGHT);
            cb.setFont(MONO_XS);
            cb.setFocusPainted(false);
            spellCheckboxes.put(name, cb);
            grid.add(cb);
        }

        JScrollPane sp = new JScrollPane(grid);
        sp.setBorder(BorderFactory.createLineBorder(BORDER_COL));
        sp.setBackground(BG);
        sp.getViewport().setBackground(BG);
        sp.getVerticalScrollBar().setUI(new RetroScrollBarUI());
        checklistWrap.add(sp, BorderLayout.CENTER);

        outer.add(checklistWrap, BorderLayout.CENTER);
        return outer;
    }

    // ── Status bar ────────────────────────────────────────────────────────────
    private JPanel buildStatusBar() {
        statusLabel = new JLabel();
        return EditorTheme.buildStatusBar(statusLabel, "Ctrl+S  save  ");
    }

    // ── Business logic ────────────────────────────────────────────────────────
    private void loadSpriteCombo() {
        spriteCombo.removeAllItems();
        File monsterDir = new File("src/main/resources/tiles/monsters");
        if (monsterDir.exists()) {
            File[] files = monsterDir.listFiles((d, n) -> n.toLowerCase().endsWith(".png"));
            if (files != null) {
                java.util.Arrays.sort(files, java.util.Comparator.comparing(File::getName));
                for (File f : files) spriteCombo.addItem(f.getName());
            }
        }
        if (spriteCombo.getItemCount() == 0) spriteCombo.addItem("unknown.png");
    }

    private void updateSpritePreview() {
        String spriteName = (String) spriteCombo.getSelectedItem();
        if (spriteName == null) { previewImage = null; spritePreviewPanel.repaint(); return; }

        File spriteFile = new File("src/main/resources/tiles/monsters/" + spriteName);
        if (!spriteFile.exists())
            spriteFile = new File("src/main/resources/tiles/" + spriteName);

        try {
            previewImage = spriteFile.exists() ? ImageIO.read(spriteFile) : null;
        } catch (Exception ex) {
            previewImage = null;
        }
        spritePreviewPanel.repaint();
        setStatus("Sprite: " + spriteName);
    }

    private void openSpriteEditor() {
        String sel = (String) spriteCombo.getSelectedItem();
        if (sel != null && !sel.trim().isEmpty())
            ImageEditor.openSprite("monsters/" + sel.trim());
        else
            new ImageEditor();
    }

    private void refreshMonsterList() {
        String filter = searchField.getText().toLowerCase().trim();
        int prevSelected = monsterList.getSelectedIndex();
        String prevId = (prevSelected >= 0 && prevSelected < filteredMonsters.size())
            ? filteredMonsters.get(prevSelected).getId() : null;

        filteredMonsters.clear();
        listModel.clear();
        for (Monster m : MonsterRegistry.getAllMonsters()) {
            if (filter.isEmpty()
                    || m.getName().toLowerCase().contains(filter)
                    || m.getId().toLowerCase().contains(filter)
                    || String.valueOf(m.getLevel()).contains(filter)) {
                filteredMonsters.add(m);
                listModel.addElement(String.format("Lv%-2d  %s", m.getLevel(), m.getName()));
            }
        }

        // Restore selection by ID after refresh
        if (prevId != null) {
            for (int i = 0; i < filteredMonsters.size(); i++) {
                if (filteredMonsters.get(i).getId().equals(prevId)) {
                    monsterList.setSelectedIndex(i);
                    return;
                }
            }
        }
        setStatus("Monsters: " + filteredMonsters.size()
            + (filter.isEmpty() ? "" : "  (filtered)"));
    }

    private void loadSelectedMonster() {
        int idx = monsterList.getSelectedIndex();
        if (idx < 0 || idx >= filteredMonsters.size()) return;
        Monster m = filteredMonsters.get(idx);
        idField.setText(m.getId());
        nameField.setText(m.getName());
        levelSpinner.setValue(Math.max(1, m.getLevel()));
        hpSpinner.setValue(m.getMaxHp());
        dmgSpinner.setValue(m.getDamage());
        goldSpinner.setValue(m.getGoldReward());
        acSpinner.setValue(m.getAC());
        xpSpinner.setValue(m.getXPValue());
        // Latent today (all 78 monsters resolve) but one renamed sprite file would otherwise
        // make every later save adopt the previously inspected monster's sprite.
        EditorCombos.selectOrKeep(spriteCombo, m.getImageFileName());
        typeCombo.setSelectedItem(m.getMonsterType());
        friendlyCheck.setSelected(m.isFriendly());
        aquaticCheck.setSelected(m.isAquatic());
        updateSpritePreview();

        // Populate spell tab
        castChanceSpinner.setValue(m.getSpellCastChance());
        spellPowerSpinner.setValue(m.getSpellPower());
        spellResistSpinner.setValue(m.getSpellResistance());
        List<String> assigned = m.getSpellNames();
        List<String> unknown = new ArrayList<>();
        for (Map.Entry<String, JCheckBox> e : spellCheckboxes.entrySet())
            e.getValue().setSelected(assigned.contains(e.getKey()));
        for (String s : assigned)
            if (!spellCheckboxes.containsKey(s)) unknown.add(s);

        if (!unknown.isEmpty())
            setStatus("\u26a0  Unknown spells (removed on save): " + unknown);
        else
            setStatus("Editing: " + m.getName());
    }

    private void createNewMonster() {
        Monster m = new Monster(
            "new_monster_" + System.currentTimeMillis(),
            "New Monster", "unknown.png", 30, 10, 20);
        MonsterRegistry.addMonster(m);
        searchField.setText("");
        refreshMonsterList();
        // Select the new monster by ID (list may be sorted/filtered)
        for (int i = 0; i < filteredMonsters.size(); i++) {
            if (filteredMonsters.get(i).getId().equals(m.getId())) {
                monsterList.setSelectedIndex(i);
                break;
            }
        }
        nameField.requestFocus(); nameField.selectAll();
        setStatus("New monster created \u2014 fill in details and save");
    }

    private void cloneMonster() {
        int idx = monsterList.getSelectedIndex();
        if (idx < 0 || idx >= filteredMonsters.size()) { setStatus("\u26a0  Select a monster to clone"); return; }
        Monster src = filteredMonsters.get(idx);
        Monster clone = new Monster(src);
        clone.setId("copy_" + src.getId() + "_" + System.currentTimeMillis() % 10000);
        clone.setName(src.getName() + " (Copy)");
        MonsterRegistry.addMonster(clone);
        searchField.setText("");
        refreshMonsterList();
        // Select the clone
        for (int i = 0; i < filteredMonsters.size(); i++) {
            if (filteredMonsters.get(i).getId().equals(clone.getId())) {
                monsterList.setSelectedIndex(i);
                break;
            }
        }
        setStatus("Cloned: " + clone.getName());
    }

    private void deleteMonster() {
        int idx = monsterList.getSelectedIndex();
        if (idx < 0 || idx >= filteredMonsters.size()) { setStatus("\u26a0  Select a monster to delete"); return; }
        Monster m = filteredMonsters.get(idx);
        int confirm = JOptionPane.showConfirmDialog(this,
            "Delete \"" + m.getName() + "\" (" + m.getId() + ")?\nThis cannot be undone.",
            "Confirm Delete", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) return;
        MonsterRegistry.removeMonster(m.getId());
        refreshMonsterList();
        int newSel = Math.min(idx, listModel.size() - 1);
        if (newSel >= 0) monsterList.setSelectedIndex(newSel);
        setStatus("Deleted: " + m.getName());
    }

    private void saveCurrentMonster() {
        int idx = monsterList.getSelectedIndex();
        if (idx < 0 || idx >= filteredMonsters.size()) { setStatus("\u26a0  Select a monster first"); return; }

        // ID uniqueness check — prevent silent overwrite of another monster
        String newId = idField.getText().trim();
        String originalId = filteredMonsters.get(idx).getId();
        if (!newId.equals(originalId) && MonsterRegistry.getById(newId) != null) {
            setStatus("\u26a0  Monster ID '" + newId + "' already exists \u2014 choose a different ID");
            return;
        }

        String spriteSel = (String) spriteCombo.getSelectedItem();
        Monster updated = new Monster(
            idField.getText().trim(),
            nameField.getText().trim(),
            spriteSel != null ? spriteSel : "unknown.png",
            (Integer) hpSpinner.getValue(),
            (Integer) dmgSpinner.getValue(),
            (Integer) goldSpinner.getValue()
        );
        updated.setLevel((Integer) levelSpinner.getValue());
        updated.setAC((Integer) acSpinner.getValue());
        updated.setXpValue((Integer) xpSpinner.getValue());
        updated.setMonsterType((MonsterType) typeCombo.getSelectedItem());
        updated.setFriendly(friendlyCheck.isSelected());
        updated.setAquatic(aquaticCheck.isSelected());

        // Collect spell assignments
        List<String> selectedSpells = new ArrayList<>();
        for (Map.Entry<String, JCheckBox> e : spellCheckboxes.entrySet())
            if (e.getValue().isSelected()) selectedSpells.add(e.getKey());
        updated.setSpellNames(selectedSpells);
        updated.setSpellCastChance((Integer) castChanceSpinner.getValue());
        updated.setSpellPower((Integer) spellPowerSpinner.getValue());
        updated.setSpellResistance((Integer) spellResistSpinner.getValue());

        // Validate: spells set but no cast chance
        if (!selectedSpells.isEmpty() && (Integer) castChanceSpinner.getValue() == 0)
            setStatus("\u26a0  Saved " + updated.getName() + " — spells assigned but cast chance is 0%");
        else
            setStatus("Saved: " + updated.getName());

        // updateMonster() matches on the NEW id, so a rename would append and leave the
        // old entry behind as a duplicate — drop it first.
        if (!newId.equals(originalId)) MonsterRegistry.removeMonster(originalId);
        MonsterRegistry.updateMonster(updated);
        ImageAssetRegistry.reloadAll();
        refreshMonsterList();
    }

    private void setStatus(String msg) {
        if (statusLabel != null) {
            statusLabel.setText("  " + msg);
            statusLabel.setForeground(msg.startsWith("\u26a0") ? DANGER : TEXT_DIM);
        }
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

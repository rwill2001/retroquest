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
import static io.cannonforge.retroquest.editor.EditorTheme.PHOSPHOR3;
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
import javax.swing.BoxLayout;
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
import javax.swing.SpinnerNumberModel;
import javax.swing.plaf.basic.BasicSplitPaneDivider;

import io.cannonforge.retroquest.model.Item;
import io.cannonforge.retroquest.model.MapData;
import io.cannonforge.retroquest.model.Monster;
import io.cannonforge.retroquest.model.NPC;
import io.cannonforge.retroquest.model.Quest;
import io.cannonforge.retroquest.registry.ItemRegistry;
import io.cannonforge.retroquest.registry.MonsterRegistry;
import io.cannonforge.retroquest.registry.QuestRegistry;

@SuppressWarnings("serial")
public class QuestEditorDialog extends JDialog {

    // ── Fields ────────────────────────────────────────────────────────────────
    private final DefaultListModel<String> listModel = new DefaultListModel<>();
    private final JList<String> questList = new JList<>(listModel);

    private final JTextField           idField        = darkField(15);
    private final JTextField           titleField     = darkField(30);
    private final JTextArea            descArea       = darkTextArea(8, 40);
    private final JComboBox<Quest.Type> typeCombo     = darkCombo(Quest.Type.values());
    private final JTextField           targetField    = darkField(20);
    private final JComboBox<String>    itemTargetCombo = new JComboBox<>();
    private final JSpinner             amountSpinner  = darkSpinner(1,   1,   100,   1);
    private final JSpinner             goldSpinner    = darkSpinner(100, 0, 10000,  50);
    private final JSpinner             xpSpinner      = darkSpinner(250, 0, 10000,  50);
    private final JCheckBox            repeatableCheck = darkCheckBox("Repeatable Quest");
    private final JSpinner             minAmountSpinner  = darkSpinner(1, 1, 100, 1);
    private final JSpinner             maxAmountSpinner  = darkSpinner(8, 1, 100, 1);
    private final JComboBox<String>    spellRewardCombo  = darkCombo(buildSpellList());
    private final JComboBox<String>    itemRewardCombo   = darkCombo(buildItemRewardList());
    private final JComboBox<String>    deliverItemCombo  = darkCombo(buildItemRewardList());
    private final JComboBox<String>    prereqCombo       = darkCombo(buildQuestList());
    private final JComboBox<String>    monsterTargetCombo = new JComboBox<>();
    private final JComboBox<String>    npcTargetCombo     = new JComboBox<>();
    private final JComboBox<String>    giverNpcCombo      = new JComboBox<>();

    private JLabel statusLabel;

    // ── Constructor ───────────────────────────────────────────────────────────
    public QuestEditorDialog(RetroForge editor) { this(editor, true); }

    /**
     * @param show false builds the dialog without showing it — the offscreen entry point
     *        {@code RetroRecorder} uses to film this editor. It is modal, so a recorder that
     *        used the public constructor would block forever on the calling thread.
     */
    public QuestEditorDialog(RetroForge editor, boolean show) {
        super(editor, "RETROQUEST  \u00b7  QUEST  EDITOR", true);
        setSize(1020, 720);
        setMinimumSize(new Dimension(860, 560));
        setLocationRelativeTo(editor);
        getContentPane().setBackground(BG);

        // Style item / monster target combos to match
        itemTargetCombo.setBackground(PANEL_BG);
        itemTargetCombo.setForeground(TEXT_BRIGHT);
        itemTargetCombo.setFont(MONO_SM);
        monsterTargetCombo.setBackground(PANEL_BG);
        monsterTargetCombo.setForeground(TEXT_BRIGHT);
        monsterTargetCombo.setFont(MONO_SM);
        npcTargetCombo.setBackground(PANEL_BG);
        npcTargetCombo.setForeground(TEXT_BRIGHT);
        npcTargetCombo.setFont(MONO_SM);
        giverNpcCombo.setBackground(PANEL_BG);
        giverNpcCombo.setForeground(TEXT_BRIGHT);
        giverNpcCombo.setFont(MONO_SM);

        buildUI();
        updateTargetField();
        loadGiverNpcDropdown();
        refreshQuestList();

        // Wire listeners
        questList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) loadSelectedQuest();
        });
        typeCombo.addActionListener(e -> updateTargetField());

        // Ctrl+S saves
        getRootPane().registerKeyboardAction(
            e -> saveCurrentQuest(),
            KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK),
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
        return EditorTheme.buildTitleBar("QUEST  EDITOR", PHOSPHOR3, "RETROQUEST  GLOBAL  QUEST  REGISTRY");
    }

    private JPanel buildMainArea() {
        JPanel p = new JPanel(new BorderLayout(0, 0));
        p.setBackground(BG);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                                          buildQuestListPanel(),
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

    // ── Quest list (left) ─────────────────────────────────────────────────────
    private JPanel buildQuestListPanel() {
        JPanel outer = new JPanel(new BorderLayout(0, 0));
        outer.setBackground(PANEL_BG);
        outer.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, BORDER_COL));
        outer.setPreferredSize(new Dimension(320, 0));

        JLabel hdr = makeLabel("\u25c8  ALL  QUESTS", PHOSPHOR3);
        hdr.setFont(MONO_MD);
        hdr.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 8));
        outer.add(hdr, BorderLayout.NORTH);

        questList.setBackground(BG);
        questList.setForeground(TEXT_BRIGHT);
        questList.setFont(MONO_SM);
        questList.setSelectionBackground(SEL_BG);
        questList.setSelectionForeground(PHOSPHOR3);
        questList.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
        questList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value,
                    int index, boolean isSelected, boolean cellHasFocus) {
                JLabel l = (JLabel) super.getListCellRendererComponent(
                        list, value, index, isSelected, cellHasFocus);
                l.setBackground(isSelected ? SEL_BG : (index % 2 == 0 ? BG : new Color(13, 16, 22)));
                l.setForeground(isSelected ? PHOSPHOR3 : TEXT_BRIGHT);
                l.setFont(MONO_SM);
                if (isSelected) {
                    l.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 3, 0, 0, PHOSPHOR3),
                        BorderFactory.createEmptyBorder(3, 6, 3, 8)));
                } else {
                    l.setBorder(BorderFactory.createEmptyBorder(3, 10, 3, 8));
                }
                return l;
            }
        });

        JScrollPane sp = new JScrollPane(questList);
        sp.setBorder(BorderFactory.createEmptyBorder());
        sp.setBackground(BG);
        sp.getViewport().setBackground(BG);
        sp.getVerticalScrollBar().setUI(new RetroScrollBarUI());
        outer.add(sp, BorderLayout.CENTER);

        JPanel btns = new JPanel(new GridLayout(1, 2, 6, 0));
        btns.setBackground(PANEL_BG);
        btns.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER_COL),
            BorderFactory.createEmptyBorder(8, 10, 8, 10)));

        JButton newBtn    = bigButton("\u2736  NEW QUEST", PHOSPHOR3, new Color(40, 35, 0));
        JButton deleteBtn = bigButton("\u2715  DELETE",    DANGER,    new Color(40,  0, 0));
        newBtn.addActionListener(e    -> createNewQuest());
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

        JLabel hdr = makeLabel("\u25c8  QUEST  PROPERTIES", PHOSPHOR3);
        hdr.setFont(MONO_MD);
        hdr.setBorder(BorderFactory.createEmptyBorder(10, 16, 10, 8));
        outer.add(hdr, BorderLayout.NORTH);

        // Two-column GridBag form
        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(PANEL_BG);
        form.setBorder(BorderFactory.createEmptyBorder(4, 16, 4, 16));

        GridBagConstraints gl = new GridBagConstraints();
        gl.gridx = 0; gl.anchor = GridBagConstraints.WEST;
        gl.insets = new Insets(4, 0, 4, 14);

        GridBagConstraints gf = new GridBagConstraints();
        gf.gridx = 1; gf.fill = GridBagConstraints.HORIZONTAL;
        gf.weightx = 1.0; gf.insets = new Insets(4, 0, 4, 0);

        // Row helper
        int row = 0;

        // ID
        gl.gridy = gf.gridy = row++;
        form.add(sectionLabel("ID:"), gl); form.add(idField, gf);

        // Title
        gl.gridy = gf.gridy = row++;
        form.add(sectionLabel("TITLE:"), gl); form.add(titleField, gf);

        // Description — moved up for visibility
        gl.gridy = gf.gridy = row++;
        gl.anchor = GridBagConstraints.NORTHWEST;
        form.add(sectionLabel("DESC:"), gl);
        JScrollPane descScroll = new JScrollPane(descArea);
        descScroll.setBorder(BorderFactory.createLineBorder(BORDER_COL));
        descScroll.setBackground(BG);
        descScroll.getViewport().setBackground(BG);
        descScroll.getVerticalScrollBar().setUI(new RetroScrollBarUI());
        gf.fill = GridBagConstraints.BOTH;
        gf.weighty = 1.0;
        form.add(descScroll, gf);
        gl.anchor = GridBagConstraints.WEST;
        gf.fill = GridBagConstraints.HORIZONTAL;
        gf.weighty = 0;

        // Type
        gl.gridy = gf.gridy = row++;
        form.add(sectionLabel("TYPE:"), gl); form.add(typeCombo, gf);

        // Target row — shows text field, item dropdown, or monster dropdown depending on type
        gl.gridy = gf.gridy = row++;
        form.add(sectionLabel("TARGET:"), gl);
        JPanel targetWrap = new JPanel();
        targetWrap.setLayout(new BoxLayout(targetWrap, BoxLayout.Y_AXIS));
        targetWrap.setOpaque(false);
        targetWrap.add(targetField);
        targetWrap.add(itemTargetCombo);
        targetWrap.add(monsterTargetCombo);
        targetWrap.add(npcTargetCombo);
        form.add(targetWrap, gf);

        // Giver NPC — which NPC to turn the quest in to
        gl.gridy = gf.gridy = row++;
        form.add(sectionLabel("GIVER NPC:"), gl); form.add(giverNpcCombo, gf);

        // Amount
        gl.gridy = gf.gridy = row++;
        form.add(sectionLabel("AMOUNT:"), gl); form.add(amountSpinner, gf);

        // Min / Max amount in one row
        gl.gridy = gf.gridy = row++;
        form.add(sectionLabel("MIN / MAX:"), gl);
        JPanel minMaxRow = new JPanel(new GridLayout(1, 2, 6, 0));
        minMaxRow.setOpaque(false);
        minMaxRow.add(minAmountSpinner);
        minMaxRow.add(maxAmountSpinner);
        form.add(minMaxRow, gf);

        // Repeatable
        gl.gridy = gf.gridy = row++;
        form.add(sectionLabel("OPTIONS:"), gl); form.add(repeatableCheck, gf);

        // Gold reward
        gl.gridy = gf.gridy = row++;
        form.add(sectionLabel("GOLD:"), gl); form.add(goldSpinner, gf);

        // XP reward
        gl.gridy = gf.gridy = row++;
        form.add(sectionLabel("XP:"), gl); form.add(xpSpinner, gf);

        // Spell reward
        gl.gridy = gf.gridy = row++;
        form.add(sectionLabel("SPELL:"), gl); form.add(spellRewardCombo, gf);

        // Item reward
        gl.gridy = gf.gridy = row++;
        form.add(sectionLabel("ITEM:"), gl); form.add(itemRewardCombo, gf);

        // Deliver item (only visible for DELIVER type)
        gl.gridy = gf.gridy = row++;
        form.add(sectionLabel("DELIVER ITEM:"), gl); form.add(deliverItemCombo, gf);

        // Prerequisite quest
        gl.gridy = gf.gridy = row++;
        form.add(sectionLabel("PREREQ:"), gl); form.add(prereqCombo, gf);

        outer.add(form, BorderLayout.CENTER);

        // Save bar
        JPanel savebar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 8));
        savebar.setBackground(new Color(8, 12, 20));
        savebar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER_COL));

        JLabel hint = makeLabel("Ctrl+S to save", TEXT_DIM);
        hint.setFont(MONO_XS);
        savebar.add(hint);

        JButton saveBtn = bigButton("\ud83d\udcbe  SAVE  QUEST", AMBER, new Color(40, 25, 0));
        saveBtn.setPreferredSize(new Dimension(200, 34));
        saveBtn.addActionListener(e -> saveCurrentQuest());
        savebar.add(saveBtn);

        outer.add(savebar, BorderLayout.SOUTH);
        return outer;
    }

    // ── Status bar ────────────────────────────────────────────────────────────
    private JPanel buildStatusBar() {
        statusLabel = new JLabel();
        return EditorTheme.buildStatusBar(statusLabel, "Ctrl+S  save  |  Del  delete  ");
    }

    // ── Business logic (unchanged) ────────────────────────────────────────────
    private void updateTargetField() {
        Quest.Type type = (Quest.Type) typeCombo.getSelectedItem();
        boolean showItemDropdown  = (type == Quest.Type.COLLECT);
        boolean showMonsterCombo  = (type == Quest.Type.KILL);
        boolean showNpcCombo      = (type == Quest.Type.DELIVER);
        targetField.setVisible(!showItemDropdown && !showMonsterCombo && !showNpcCombo);
        itemTargetCombo.setVisible(showItemDropdown);
        monsterTargetCombo.setVisible(showMonsterCombo);
        npcTargetCombo.setVisible(showNpcCombo);
        deliverItemCombo.setVisible(type == Quest.Type.DELIVER);
        if (showItemDropdown)  loadItemDropdown();
        if (showMonsterCombo)  loadMonsterDropdown();
        if (showNpcCombo)      loadNpcDropdown();

        boolean supportsRandom = (type == Quest.Type.KILL || type == Quest.Type.COLLECT);
        minAmountSpinner.setEnabled(supportsRandom);
        maxAmountSpinner.setEnabled(supportsRandom);
        String tip = supportsRandom ? null : "Only used for KILL and COLLECT quest types";
        minAmountSpinner.setToolTipText(tip);
        maxAmountSpinner.setToolTipText(tip);
    }

    private void loadItemDropdown() {
        itemTargetCombo.removeAllItems();
        for (Item item : ItemRegistry.getAllItems())
            itemTargetCombo.addItem(item.getName());
        if (itemTargetCombo.getItemCount() == 0)
            itemTargetCombo.addItem("(No items defined — create some in Item Editor)");
    }

    private void loadMonsterDropdown() {
        String current = (String) monsterTargetCombo.getSelectedItem();
        monsterTargetCombo.removeAllItems();
        monsterTargetCombo.addItem("any");
        MonsterRegistry.getAllMonsters().stream()
            .map(Monster::getName)
            .filter(java.util.Objects::nonNull)
            .distinct()
            .sorted()
            .forEach(monsterTargetCombo::addItem);
        if (current != null) monsterTargetCombo.setSelectedItem(current);
    }

    /** Populates the NPC target dropdown with NPC names from all town and overworld .rfmap files. */
    private void loadNpcDropdown() {
        String current = (String) npcTargetCombo.getSelectedItem();
        npcTargetCombo.removeAllItems();
        java.util.TreeSet<String> names = new java.util.TreeSet<>();
        collectNpcNames(new java.io.File("data/towns"), names);
        collectNpcNames(new java.io.File("data/overworlds"), names);
        for (String name : names) npcTargetCombo.addItem(name);
        if (npcTargetCombo.getItemCount() == 0)
            npcTargetCombo.addItem("(No NPCs found — create some in a map)");
        if (current != null) npcTargetCombo.setSelectedItem(current);
    }

    private void loadGiverNpcDropdown() {
        String current = (String) giverNpcCombo.getSelectedItem();
        giverNpcCombo.removeAllItems();
        giverNpcCombo.addItem("");  // blank = no specific giver
        java.util.TreeSet<String> names = new java.util.TreeSet<>();
        collectNpcNames(new java.io.File("data/towns"), names);
        collectNpcNames(new java.io.File("data/overworlds"), names);
        for (String name : names) giverNpcCombo.addItem(name);
        if (current != null) giverNpcCombo.setSelectedItem(current);
    }

    private void collectNpcNames(java.io.File dir, java.util.TreeSet<String> names) {
        if (dir == null || !dir.isDirectory()) return;
        java.io.File[] files = dir.listFiles((d, n) -> n.endsWith(".rfmap"));
        if (files == null) return;
        for (java.io.File f : files) {
            try {
                MapData md = MapData.load(f);
                for (NPC npc : md.getNpcs()) {
                    if (npc.getName() != null && !npc.getName().isBlank())
                        names.add(npc.getName());
                }
            } catch (Exception ignored) { /* skip unreadable maps */ }
        }
    }

    private void refreshQuestList() {
        listModel.clear();
        java.util.List<Quest> sorted = new java.util.ArrayList<>(QuestRegistry.getAllQuests());
        sorted.sort(java.util.Comparator.comparing(Quest::getTitle, String.CASE_INSENSITIVE_ORDER));
        for (Quest q : sorted)
            listModel.addElement(q.getId() + "  \u2014  " + q.getTitle());
    }

    private Quest getSelectedQuestFromList() {
        int idx = questList.getSelectedIndex();
        if (idx < 0) return null;
        String entry = listModel.get(idx);
        String id = entry.split("  \u2014  ")[0].trim();
        return QuestRegistry.getById(id);
    }

    private void loadSelectedQuest() {
        Quest q = getSelectedQuestFromList();
        if (q == null) return;

        idField.setText(q.getId());
        titleField.setText(q.getTitle());
        descArea.setText(q.getDescription() != null ? q.getDescription() : "");
        typeCombo.setSelectedItem(q.getType());
        targetField.setText(q.getTarget() != null ? q.getTarget() : "");
        updateTargetField();  // populate combos before selecting
        // These combos are rebuilt by updateTargetField above, which preserves the PREVIOUS
        // quest's selection. A target the combo does not list — three shipped KILL quests aim at
        // "thalorax_trial", "Grief Incarnate" and "Seraphine, Undying Champion", none of which are
        // monster names — made the set a no-op, so the form showed the last quest's monster and
        // saving rewrote this quest's target to it.
        if (q.getType() == Quest.Type.KILL && q.getTarget() != null)
            EditorCombos.selectOrKeep(monsterTargetCombo, q.getTarget());
        if (q.getType() == Quest.Type.DELIVER && q.getTarget() != null)
            EditorCombos.selectOrKeep(npcTargetCombo, q.getTarget());
        EditorCombos.selectOrKeep(giverNpcCombo, q.getGiverName() != null ? q.getGiverName() : "");
        amountSpinner.setValue(q.getRequiredAmount());
        goldSpinner.setValue(q.getGoldReward());
        xpSpinner.setValue(q.getXpReward());
        repeatableCheck.setSelected(q.isRepeatable());
        minAmountSpinner.setValue(q.getMinAmount());
        maxAmountSpinner.setValue(q.getMaxAmount());
        String sr = q.getSpellRewardId();
        spellRewardCombo.setSelectedItem(sr != null ? sr : "");
        selectItemRewardById(q.getItemRewardId());
        selectDeliverItemById(q.getDeliverItemId());
        selectPrereqById(q.getPrereqQuestId());
        setStatus("Editing: " + q.getTitle());
    }

    private void createNewQuest() {
        Quest q = new Quest("new_quest_" + System.currentTimeMillis(), "New Quest",
                "Write your description here...", Quest.Type.KILL, "Wolf", 5, 100, 250, null);
        QuestRegistry.addQuest(q);
        refreshQuestList();
        // refreshQuestList sorts by title, so the new row is not the last one — selecting by
        // index loaded an unrelated existing quest into the form, and the next save overwrote
        // that quest while the placeholder stayed behind in quests.json. Select by identity.
        for (int i = 0; i < listModel.size(); i++) {
            if (String.valueOf(listModel.get(i)).contains(q.getTitle())) {
                questList.setSelectedIndex(i);
                break;
            }
        }
        titleField.requestFocus();
        titleField.selectAll();
        setStatus("New quest created — fill in details and save");
    }

    private void deleteSelected() {
        Quest q = getSelectedQuestFromList();
        if (q == null) return;
        if (JOptionPane.showConfirmDialog(this,
                "Delete quest \"" + q.getId() + "\" permanently?", "Confirm Delete",
                JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
            QuestRegistry.removeQuest(q.getId());
            refreshQuestList();
            setStatus("Deleted: " + q.getId());
        }
    }

    private void saveCurrentQuest() {
        Quest original = getSelectedQuestFromList();
        if (original == null) { setStatus("\u26a0  Select a quest first"); return; }

        // ── Pre-save validation ──────────────────────────────────────────────
        String id = idField.getText().trim();
        if (id.isEmpty()) { setStatus("\u26a0  Quest ID cannot be empty"); return; }
        if (titleField.getText().trim().isEmpty()) { setStatus("\u26a0  Quest title cannot be empty"); return; }

        // ID uniqueness check — prevent silent overwrite of another quest
        if (!id.equals(original.getId())) {
            Quest existing = QuestRegistry.getById(id);
            if (existing != null) {
                setStatus("\u26a0  Quest ID '" + id + "' already exists — choose a different ID");
                return;
            }
        }

        Quest.Type type = (Quest.Type) typeCombo.getSelectedItem();
        java.util.List<String> warnings = new java.util.ArrayList<>();

        if (type == Quest.Type.DELIVER) {
            String deliverEntry = (String) deliverItemCombo.getSelectedItem();
            if (deliverEntry == null || deliverEntry.isBlank())
                warnings.add("DELIVER quest has no deliver item set");
            String npcSel = (String) npcTargetCombo.getSelectedItem();
            if (npcSel == null || npcSel.isBlank() || npcSel.startsWith("("))
                warnings.add("DELIVER quest has no recipient NPC set");
        }

        String checkReward = (String) itemRewardCombo.getSelectedItem();
        if (checkReward != null && !checkReward.isBlank()) {
            int lp2 = checkReward.lastIndexOf('(');
            int rp2 = checkReward.lastIndexOf(')');
            if (lp2 >= 0 && rp2 > lp2) {
                String rewardId = checkReward.substring(lp2 + 1, rp2);
                if (ItemRegistry.getById(rewardId) == null)
                    warnings.add("Item reward '" + rewardId + "' not found in ItemRegistry");
            }
        }

        String checkPrereq = (String) prereqCombo.getSelectedItem();
        if (checkPrereq != null && !checkPrereq.isBlank()) {
            int lp2 = checkPrereq.lastIndexOf('(');
            int rp2 = checkPrereq.lastIndexOf(')');
            if (lp2 >= 0 && rp2 > lp2) {
                String prereqId = checkPrereq.substring(lp2 + 1, rp2);
                if (QuestRegistry.getById(prereqId) == null)
                    warnings.add("Prerequisite quest '" + prereqId + "' not found in QuestRegistry");
            }
        }

        if (!warnings.isEmpty()) {
            String msg = "Validation warnings:\n\n• " + String.join("\n• ", warnings)
                       + "\n\nSave anyway?";
            if (JOptionPane.showConfirmDialog(this, msg, "Validation", JOptionPane.YES_NO_OPTION)
                    != JOptionPane.YES_OPTION) return;
        }

        String target = targetField.getText().trim();
        if (itemTargetCombo.isVisible() && itemTargetCombo.getSelectedIndex() >= 0)
            target = (String) itemTargetCombo.getSelectedItem();
        if (monsterTargetCombo.isVisible() && monsterTargetCombo.getSelectedIndex() >= 0)
            target = (String) monsterTargetCombo.getSelectedItem();
        if (npcTargetCombo.isVisible() && npcTargetCombo.getSelectedIndex() >= 0)
            target = (String) npcTargetCombo.getSelectedItem();

        Quest updated = new Quest(
            idField.getText().trim(),
            titleField.getText().trim(),
            descArea.getText().trim(),
            (Quest.Type) typeCombo.getSelectedItem(),
            target,
            (Integer) amountSpinner.getValue(),
            (Integer) goldSpinner.getValue(),
            (Integer) xpSpinner.getValue(),
            null
        );
        String spellReward = (String) spellRewardCombo.getSelectedItem();
        if (spellReward != null && !spellReward.isBlank())
            updated.withSpellRewardId(spellReward);
        String itemRewardEntry = (String) itemRewardCombo.getSelectedItem();
        if (itemRewardEntry != null && !itemRewardEntry.isBlank()) {
            int lp = itemRewardEntry.lastIndexOf('(');
            int rp = itemRewardEntry.lastIndexOf(')');
            if (lp >= 0 && rp > lp)
                updated.withItemRewardId(itemRewardEntry.substring(lp + 1, rp));
        }
        String giverSel = (String) giverNpcCombo.getSelectedItem();
        if (giverSel != null && !giverSel.isBlank())
            updated.setGiverName(giverSel);
        updated.setRepeatable(repeatableCheck.isSelected());
        updated.setMinAmount((Integer) minAmountSpinner.getValue());
        updated.setMaxAmount((Integer) maxAmountSpinner.getValue());

        // Deliver item ID
        String deliverEntry = (String) deliverItemCombo.getSelectedItem();
        if (deliverEntry != null && !deliverEntry.isBlank()) {
            int lp = deliverEntry.lastIndexOf('(');
            int rp = deliverEntry.lastIndexOf(')');
            if (lp >= 0 && rp > lp)
                updated.withDeliverItemId(deliverEntry.substring(lp + 1, rp));
        }

        // Prerequisite quest ID
        String prereqEntry = (String) prereqCombo.getSelectedItem();
        if (prereqEntry != null && !prereqEntry.isBlank()) {
            int lp = prereqEntry.lastIndexOf('(');
            int rp = prereqEntry.lastIndexOf(')');
            if (lp >= 0 && rp > lp)
                updated.setPrereqQuestId(prereqEntry.substring(lp + 1, rp));
        }

        // updateQuest() matches on the NEW id, so a rename would append and leave the
        // old entry behind as a duplicate — drop it first.
        if (!id.equals(original.getId())) QuestRegistry.removeQuest(original.getId());
        QuestRegistry.updateQuest(updated);

        String savedId = updated.getId();
        refreshQuestList();
        // Reselect by ID after refresh (list is now sorted)
        for (int i = 0; i < listModel.size(); i++) {
            if (listModel.get(i).startsWith(savedId + "  ")) {
                questList.setSelectedIndex(i);
                break;
            }
        }
        setStatus("Saved: " + updated.getTitle() + "  \u2014  " + java.time.LocalTime.now().withNano(0));
    }

    private void setStatus(String msg) {
        if (statusLabel != null) {
            statusLabel.setText("  " + msg);
            statusLabel.setForeground(msg.startsWith("\u26a0") ? DANGER : TEXT_DIM);
        }
    }

    /** Returns the item list for the item reward combo (blank entry first = no reward). */
    private static String[] buildItemRewardList() {
        java.util.List<Item> items = ItemRegistry.getAllItems();
        String[] result = new String[items.size() + 1];
        result[0] = "";
        for (int i = 0; i < items.size(); i++) {
            Item it = items.get(i);
            String id = it.getId() != null ? it.getId() : it.getName().toLowerCase().replace(' ', '_');
            result[i + 1] = it.getName() + "  (" + id + ")";
        }
        return result;
    }

    private void selectItemRewardById(String id) {
        if (id == null || id.isBlank()) {
            itemRewardCombo.setSelectedIndex(0);
            return;
        }
        String suffix = "(" + id + ")";
        for (int i = 0; i < itemRewardCombo.getItemCount(); i++) {
            if (itemRewardCombo.getItemAt(i).endsWith(suffix)) {
                itemRewardCombo.setSelectedIndex(i);
                return;
            }
        }
        itemRewardCombo.setSelectedIndex(0);
    }

    private void selectDeliverItemById(String id) {
        if (id == null || id.isBlank()) {
            deliverItemCombo.setSelectedIndex(0);
            return;
        }
        String suffix = "(" + id + ")";
        for (int i = 0; i < deliverItemCombo.getItemCount(); i++) {
            if (deliverItemCombo.getItemAt(i).endsWith(suffix)) {
                deliverItemCombo.setSelectedIndex(i);
                return;
            }
        }
        deliverItemCombo.setSelectedIndex(0);
    }

    private void selectPrereqById(String id) {
        if (id == null || id.isBlank()) {
            prereqCombo.setSelectedIndex(0);
            return;
        }
        String suffix = "(" + id + ")";
        for (int i = 0; i < prereqCombo.getItemCount(); i++) {
            if (prereqCombo.getItemAt(i).endsWith(suffix)) {
                prereqCombo.setSelectedIndex(i);
                return;
            }
        }
        prereqCombo.setSelectedIndex(0);
    }

    /** Returns the quest list for the prereq combo (blank entry first = no prereq). */
    private static String[] buildQuestList() {
        java.util.List<Quest> quests = QuestRegistry.getAllQuests();
        String[] result = new String[quests.size() + 1];
        result[0] = "";
        for (int i = 0; i < quests.size(); i++) {
            Quest q = quests.get(i);
            result[i + 1] = q.getTitle() + "  (" + q.getId() + ")";
        }
        return result;
    }

    /** Returns the full ordered spell list (blank entry first = no reward). */
    private static String[] buildSpellList() {
        return new String[] {
            "",
            // Level 1
            "Magic Missile", "Sleep", "Charm Monster",
            "Cure Light Wounds", "Protection from Evil", "Shield",
            // Level 2
            "Fireball", "Lightning Bolt", "Invisibility",
            "Cure Serious Wounds", "Bless", "Turn Undead", "Entangle",
            // Level 3
            "Ice Storm", "Dispel Magic", "Teleport",
            "Cure Critical Wounds", "Prayer", "Holy Word",
            // Level 4
            "Cone of Cold", "Cloudkill", "Haste",
            "Heal", "Resist Elements", "Restoration",
            // Level 5
            "Chain Lightning", "Death Spell", "Scry",
            "Holy Armor", "Flame Strike",
            // Level 6
            "Meteor Swarm", "Power Word Kill", "Wish", "Time Stop",
            "Divine Intervention", "Sanctuary", "Resurrection"
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

    private static JCheckBox darkCheckBox(String txt) {
        JCheckBox cb = new JCheckBox(txt);
        cb.setBackground(PANEL_BG);
        cb.setForeground(TEXT_BRIGHT);
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

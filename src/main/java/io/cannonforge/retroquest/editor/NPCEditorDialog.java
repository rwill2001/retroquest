package io.cannonforge.retroquest.editor;
import static io.cannonforge.retroquest.editor.EditorTheme.ACCENT;
import static io.cannonforge.retroquest.editor.EditorTheme.AMBER;
import static io.cannonforge.retroquest.editor.EditorTheme.BG;
import static io.cannonforge.retroquest.editor.EditorTheme.BORDER_COL;
import static io.cannonforge.retroquest.editor.EditorTheme.DANGER;
import static io.cannonforge.retroquest.editor.EditorTheme.MONO_LG;
import static io.cannonforge.retroquest.editor.EditorTheme.MONO_MD;
import static io.cannonforge.retroquest.editor.EditorTheme.MONO_SM;
import static io.cannonforge.retroquest.editor.EditorTheme.MONO_XS;
import static io.cannonforge.retroquest.editor.EditorTheme.PANEL_BG;
import static io.cannonforge.retroquest.editor.EditorTheme.PHOSPHOR;
import static io.cannonforge.retroquest.editor.EditorTheme.PHOSPHOR2;
import static io.cannonforge.retroquest.editor.EditorTheme.SEL_BG;
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
import java.awt.Image;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

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
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.plaf.basic.BasicSplitPaneDivider;

import io.cannonforge.retroquest.dialogue.DialogueAction;
import io.cannonforge.retroquest.dialogue.DialogueNode;
import io.cannonforge.retroquest.dialogue.DialogueTree;
import io.cannonforge.retroquest.model.Item;
import io.cannonforge.retroquest.model.MapData;
import io.cannonforge.retroquest.model.NPC;
import io.cannonforge.retroquest.model.Quest;
import io.cannonforge.retroquest.registry.ImageAssetRegistry;
import io.cannonforge.retroquest.registry.ItemRegistry;
import io.cannonforge.retroquest.registry.QuestRegistry;

@SuppressWarnings("serial")
public class NPCEditorDialog extends JDialog {

    // ── State ─────────────────────────────────────────────────────────────────
    private final MapData    map;
    private final MapCanvas  canvas;   // null when opened from the menu (no map view)
    // Always present, unlike canvas. Dirty marking has to go through this or edits made from
    // the EDITORS menu never flag the map and RetroForge exits without offering to save them.
    private final RetroForge editor;
    private NPC selectedNPC = null;

    // ── List ──────────────────────────────────────────────────────────────────
    private final DefaultListModel<String> listModel = new DefaultListModel<>();
    private final JList<String> npcList = new JList<>(listModel);

    // ── Form fields ───────────────────────────────────────────────────────────
    private final JTextField          nameField    = darkField(20);
    private final JComboBox<String>   spriteCombo  = new JComboBox<>();
    private final JComboBox<NPC.Type> typeCombo    = darkCombo(NPC.Type.values());
    private final JComboBox<String>   questCombo   = new JComboBox<>();
    private final JTextArea           dialogArea   = darkTextArea(5, 35);
    private final JTextArea           postQuestArea= darkTextArea(5, 35);
    private final JCheckBox           shopCheck    = darkCheckBox("Is Shopkeeper");
    private final JButton             editShopItemsBtn = retroButton("Edit Shop Items...");

    // ── Dialogue-tree info (updated on NPC selection) ─────────────────────────
    private JLabel treeQuestsLabel;
    private JButton editTreeBtn;

    // ── Sprite preview ────────────────────────────────────────────────────────
    private BufferedImage previewImage = null;
    private final JPanel spritePreviewPanel = new JPanel() {
        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            // Checkerboard
            int cs = 8;
            for (int py = 0; py < getHeight(); py += cs)
                for (int px = 0; px < getWidth(); px += cs) {
                    g2.setColor((px/cs + py/cs) % 2 == 0
                        ? new Color(40, 44, 52) : new Color(28, 32, 40));
                    g2.fillRect(px, py, cs, cs);
                }
            if (previewImage != null) {
                int iw = previewImage.getWidth(), ih = previewImage.getHeight();
                int pw = getWidth() - 8, ph = getHeight() - 8;
                float scale = Math.min((float) pw / iw, (float) ph / ih);
                int dw = (int)(iw * scale), dh = (int)(ih * scale);
                int ox = (getWidth() - dw) / 2, oy = (getHeight() - dh) / 2;
                g2.drawImage(previewImage, ox, oy, dw, dh, null);
                g2.setColor(new Color(0, 200, 255, 80));
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawRect(ox - 1, oy - 1, dw + 2, dh + 2);
            } else {
                g2.setColor(TEXT_DIM);
                g2.setFont(MONO_XS);
                String msg = "NO SPRITE";
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(msg, (getWidth() - fm.stringWidth(msg)) / 2,
                    getHeight() / 2 + fm.getAscent() / 2);
            }
            g2.setColor(BORDER_COL);
            g2.setStroke(new BasicStroke(1f));
            g2.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
        }
    };

    private JLabel statusLabel;

    // ── Constructors ──────────────────────────────────────────────────────────
    public NPCEditorDialog(RetroForge editor, MapData map) {
        this(editor, null, map, null, 0, 0);
    }

    public NPCEditorDialog(RetroForge editor, MapCanvas canvas, MapData map,
                           NPC existing, int clickX, int clickY) {
        super(editor, "RETROQUEST  00b7  NPC  EDITOR", true);
        this.map    = map;
        this.canvas = canvas;
        this.editor = editor;

        if (existing != null) {
            this.selectedNPC = existing;
        } else {
            NPC newNPC = new NPC(null, "New Traveler", "npcs/townsman",
                NPC.Type.TOWNSFOLK, "Hello traveler...", clickX, clickY);
            map.npcs.add(newNPC);
            pushNpcCommand(new MapCommand.NpcAddCommand(newNPC));
            this.selectedNPC = newNPC;
        }

        // Style combos
        for (JComboBox<?> cb : new JComboBox[]{spriteCombo, questCombo}) {
            cb.setBackground(PANEL_BG);
            cb.setForeground(TEXT_BRIGHT);
            cb.setFont(MONO_SM);
        }

        buildUI();
        loadSpritesIntoCombo();
        loadQuestDropdown();
        refreshNPCList();

        setSize(1080, 760);
        setMinimumSize(new Dimension(900, 600));
        setLocationRelativeTo(editor);

        // Wire listeners
        npcList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) loadSelectedNPC();
        });
        spriteCombo.addActionListener(e -> updateSpritePreview());

        // Pre-select NPC after list is populated
        if (selectedNPC != null) {
            SwingUtilities.invokeLater(() -> {
                int index = map.npcs.indexOf(selectedNPC);
                if (index >= 0) {
                    npcList.setSelectedIndex(index);
                    npcList.ensureIndexIsVisible(index);
                }
            });
        }

        // Ctrl+S saves
        getRootPane().registerKeyboardAction(
            e -> saveCurrent(),
            KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK),
            JComponent.WHEN_IN_FOCUSED_WINDOW);

        setVisible(true);
    }

    // ── UI construction ───────────────────────────────────────────────────────
    private void buildUI() {
        setLayout(new BorderLayout(0, 0));
        getContentPane().setBackground(BG);
        add(buildTitleBar(),  BorderLayout.NORTH);
        add(buildMainArea(),  BorderLayout.CENTER);
        add(buildStatusBar(), BorderLayout.SOUTH);
    }

    private JPanel buildTitleBar() {
        return EditorTheme.buildTitleBar("NPC  EDITOR", PHOSPHOR2, "RETROQUEST  MAP  NPC  EDITOR");
    }

    private JPanel buildMainArea() {
        JPanel p = new JPanel(new BorderLayout(0, 0));
        p.setBackground(BG);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                                          buildNPCListPanel(),
                                          buildRightPanel());
        split.setDividerLocation(300);
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

    // ── NPC list (left) ───────────────────────────────────────────────────────
    private JPanel buildNPCListPanel() {
        JPanel outer = new JPanel(new BorderLayout(0, 0));
        outer.setBackground(PANEL_BG);
        outer.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, BORDER_COL));
        outer.setPreferredSize(new Dimension(300, 0));

        JLabel hdr = makeLabel("\u25c8  MAP  NPCS", PHOSPHOR2);
        hdr.setFont(MONO_MD);
        hdr.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 8));
        outer.add(hdr, BorderLayout.NORTH);

        npcList.setBackground(BG);
        npcList.setForeground(TEXT_BRIGHT);
        npcList.setFont(MONO_SM);
        npcList.setSelectionBackground(new Color(0, 40, 70));
        npcList.setSelectionForeground(PHOSPHOR2);
        npcList.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
        npcList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value,
                    int index, boolean isSelected, boolean cellHasFocus) {
                JLabel l = (JLabel) super.getListCellRendererComponent(
                        list, value, index, isSelected, cellHasFocus);
                l.setBackground(isSelected ? new Color(0, 40, 70)
                    : (index % 2 == 0 ? BG : new Color(13, 16, 22)));
                l.setForeground(isSelected ? PHOSPHOR2 : TEXT_BRIGHT);
                l.setFont(MONO_SM);
                if (isSelected) {
                    l.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 3, 0, 0, PHOSPHOR2),
                        BorderFactory.createEmptyBorder(3, 6, 3, 8)));
                } else {
                    l.setBorder(BorderFactory.createEmptyBorder(3, 10, 3, 8));
                }
                return l;
            }
        });

        JScrollPane sp = new JScrollPane(npcList);
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
        JButton newBtn    = bigButton("\u2736  NEW NPC", PHOSPHOR,  new Color(0, 40, 20));
        JButton deleteBtn = bigButton("\u2715  DELETE",  DANGER,    new Color(40,  0,  0));
        newBtn.addActionListener(e    -> createNewNPC());
        deleteBtn.addActionListener(e -> deleteSelected());
        btns.add(newBtn); btns.add(deleteBtn);
        outer.add(btns, BorderLayout.SOUTH);

        return outer;
    }

    // ── Right panel: form + preview + dialogs ─────────────────────────────────
    private JPanel buildRightPanel() {
        JPanel outer = new JPanel(new BorderLayout(0, 0));
        outer.setBackground(PANEL_BG);

        JLabel hdr = makeLabel("\u25c8  NPC  PROPERTIES", PHOSPHOR2);
        hdr.setFont(MONO_MD);
        hdr.setBorder(BorderFactory.createEmptyBorder(10, 16, 10, 8));
        outer.add(hdr, BorderLayout.NORTH);

        // ── Top area: form LEFT, preview RIGHT ───────────────────────────────
        JPanel topArea = new JPanel(new BorderLayout(16, 0));
        topArea.setBackground(PANEL_BG);
        topArea.setBorder(BorderFactory.createEmptyBorder(4, 16, 8, 16));

        // Form
        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(PANEL_BG);

        GridBagConstraints gl = new GridBagConstraints();
        gl.gridx = 0; gl.anchor = GridBagConstraints.WEST;
        gl.insets = new Insets(5, 0, 5, 14);

        GridBagConstraints gf = new GridBagConstraints();
        gf.gridx = 1; gf.fill = GridBagConstraints.HORIZONTAL;
        gf.weightx = 1.0; gf.insets = new Insets(5, 0, 5, 0);

        JPanel shopPanel = new JPanel(new BorderLayout(8, 0));
        shopPanel.setBackground(PANEL_BG);
        shopPanel.add(shopCheck, BorderLayout.WEST);
        shopPanel.add(editShopItemsBtn, BorderLayout.CENTER);
        editShopItemsBtn.setEnabled(shopCheck.isSelected());
        shopCheck.addActionListener(e -> editShopItemsBtn.setEnabled(shopCheck.isSelected()));
        editShopItemsBtn.addActionListener(e -> openShopItemEditor());

        String[] labels = {"NAME:", "SPRITE:", "TYPE:", "OPTIONS:"};
        JComponent[] fields = {nameField, spriteCombo, typeCombo, shopPanel};
        for (int i = 0; i < fields.length; i++) {
            gl.gridy = gf.gridy = i;
            form.add(sectionLabel(labels[i]), gl);
            form.add(fields[i], gf);
        }

        // TREE QUESTS row — shows quests assigned via dialogue tree (read-only)
        treeQuestsLabel = makeLabel("(none)", TEXT_DIM);
        treeQuestsLabel.setFont(MONO_SM);
        int treeRow = fields.length;
        gl.gridy = gf.gridy = treeRow;
        form.add(sectionLabel("TREE:"), gl);
        form.add(treeQuestsLabel, gf);

        // Push form to top
        GridBagConstraints spacer = new GridBagConstraints();
        spacer.gridx = 0; spacer.gridy = treeRow + 1; spacer.gridwidth = 2;
        spacer.weighty = 1.0; spacer.fill = GridBagConstraints.VERTICAL;
        form.add(Box.createVerticalGlue(), spacer);

        // Sprite preview panel
        JPanel previewWrap = new JPanel(new BorderLayout(0, 6));
        previewWrap.setBackground(PANEL_BG);
        previewWrap.setPreferredSize(new Dimension(164, 0));

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

        topArea.add(form,        BorderLayout.CENTER);
        topArea.add(previewWrap, BorderLayout.EAST);

        // ── Dialog text areas ─────────────────────────────────────────────────
        JPanel dialogsPanel = new JPanel(new GridLayout(1, 2, 8, 0));
        dialogsPanel.setBackground(PANEL_BG);
        dialogsPanel.setBorder(BorderFactory.createEmptyBorder(0, 16, 8, 16));

        dialogsPanel.add(buildDialogSection("NORMAL  DIALOG",     dialogArea));
        dialogsPanel.add(buildDialogSection("POST-QUEST  DIALOG", postQuestArea));

        // ── Legacy quest row (deprecated — use Dialogue Tree GIVE_QUEST instead) ─
        questCombo.setToolTipText("Deprecated \u2014 assign quests via Dialogue Tree GIVE_QUEST actions instead");
        JPanel legacyQuestPanel = new JPanel(new BorderLayout(8, 0));
        legacyQuestPanel.setBackground(PANEL_BG);
        legacyQuestPanel.setBorder(BorderFactory.createEmptyBorder(0, 16, 4, 16));
        JLabel legacyQuestLabel = makeLabel("QUEST (legacy):", TEXT_DIM);
        legacyQuestLabel.setFont(new Font("Monospaced", Font.PLAIN, 10));
        legacyQuestPanel.add(legacyQuestLabel, BorderLayout.WEST);
        legacyQuestPanel.add(questCombo, BorderLayout.CENTER);

        // ── Dialogue Tree button ──────────────────────────────────────────────
        JPanel treePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 4));
        treePanel.setBackground(PANEL_BG);
        editTreeBtn = bigButton("\u25c8  Edit Dialogue Tree...", TEXT_DIM, new Color(0, 20, 40));
        editTreeBtn.addActionListener(e -> {
            if (selectedNPC == null) return;
            DialogueTreeEditor dlg = new DialogueTreeEditor(NPCEditorDialog.this, selectedNPC);
            // applyAndClose mutates the NPC in place; without this the tree is edited and the
            // map never looks dirty, so closing RetroForge discards it with no prompt.
            if (dlg.isApplied()) editor.markDirty();
            refreshTreeInfo();
        });
        treePanel.add(editTreeBtn);

        JPanel southButtons = new JPanel(new BorderLayout(0, 0));
        southButtons.setBackground(PANEL_BG);
        southButtons.add(legacyQuestPanel, BorderLayout.NORTH);
        southButtons.add(treePanel, BorderLayout.SOUTH);

        // ── Assemble ──────────────────────────────────────────────────────────
        JPanel center = new JPanel(new BorderLayout(0, 0));
        center.setBackground(PANEL_BG);
        center.add(topArea,     BorderLayout.NORTH);
        center.add(dialogsPanel, BorderLayout.CENTER);
        center.add(southButtons, BorderLayout.SOUTH);
        outer.add(center, BorderLayout.CENTER);

        // Save bar
        JPanel savebar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 8));
        savebar.setBackground(new Color(8, 12, 20));
        savebar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER_COL));

        JLabel hint = makeLabel("Ctrl+S to save", TEXT_DIM);
        hint.setFont(MONO_XS);
        savebar.add(hint);

        JButton saveBtn = bigButton("\ud83d\udcbe  SAVE  NPC", AMBER, new Color(40, 25, 0));
        saveBtn.setPreferredSize(new Dimension(190, 34));
        saveBtn.addActionListener(e -> saveCurrent());
        savebar.add(saveBtn);

        outer.add(savebar, BorderLayout.SOUTH);
        return outer;
    }

    private JPanel buildDialogSection(String title, JTextArea area) {
        JPanel p = new JPanel(new BorderLayout(0, 4));
        p.setBackground(PANEL_BG);

        JLabel lbl = makeLabel(title, ACCENT);
        lbl.setFont(new Font("Monospaced", Font.BOLD, 10));
        lbl.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
        p.add(lbl, BorderLayout.NORTH);

        JScrollPane sp = new JScrollPane(area);
        sp.setBorder(BorderFactory.createLineBorder(BORDER_COL));
        sp.setBackground(BG);
        sp.getViewport().setBackground(BG);
        sp.getVerticalScrollBar().setUI(new RetroScrollBarUI());
        p.add(sp, BorderLayout.CENTER);
        return p;
    }

    // ── Status bar ────────────────────────────────────────────────────────────
    private JPanel buildStatusBar() {
        statusLabel = new JLabel();
        return EditorTheme.buildStatusBar(statusLabel, "Ctrl+S  save  |  Del  delete  ");
    }

    // ── Business logic ────────────────────────────────────────────────────────
    private void refreshNPCList() {
        listModel.clear();
        for (NPC npc : map.npcs)
            listModel.addElement(npc.getName() + "  (" + npc.getX() + "," + npc.getY() + ")");
        if (listModel.isEmpty())
            listModel.addElement("(No NPCs yet \u2014 click New NPC)");
    }

    private void loadSpritesIntoCombo() {
        spriteCombo.removeAllItems();
        for (String key : ImageAssetRegistry.getAllSpriteKeys())
            if (key.startsWith("npcs/")) spriteCombo.addItem(key);
        if (spriteCombo.getItemCount() == 0) spriteCombo.addItem("npcs/townsman");
    }

    private void loadQuestDropdown() {
        questCombo.removeAllItems();
        questCombo.addItem("(None \u2014 No Quest)");
        for (Quest q : QuestRegistry.getAllQuests())
            questCombo.addItem(q.getId() + " \u2014 " + q.getTitle());
    }

    private void loadSelectedNPC() {
        int idx = npcList.getSelectedIndex();
        if (idx < 0 || idx >= map.npcs.size()) return;

        selectedNPC = map.npcs.get(idx);
        nameField.setText(selectedNPC.getName());
        // Keep a sprite the combo does not list (NPC "Wolfie" in moonhaven uses "orc.png",
        // which is not an npcs/ key) rather than stamping the last NPC's sprite onto it.
        EditorCombos.selectOrKeep(spriteCombo, selectedNPC.getSpriteName());
        typeCombo.setSelectedItem(selectedNPC.getType());
        dialogArea.setText(selectedNPC.getDefaultDialog() != null
            ? selectedNPC.getDefaultDialog() : "");
        postQuestArea.setText(selectedNPC.getQuestCompleteDialog() != null
            ? selectedNPC.getQuestCompleteDialog() : "");
        shopCheck.setSelected(!selectedNPC.getShopItemIds().isEmpty());
        editShopItemsBtn.setEnabled(shopCheck.isSelected());

        String currentQuestId = selectedNPC.getQuestId();
        boolean found = false;
        if (currentQuestId != null && !currentQuestId.isEmpty()) {
            for (int i = 0; i < questCombo.getItemCount(); i++) {
                if (questCombo.getItemAt(i).startsWith(currentQuestId + " \u2014")) {
                    questCombo.setSelectedIndex(i); found = true; break;
                }
            }
        }
        if (!found) questCombo.setSelectedIndex(0);

        updateSpritePreview();
        refreshTreeInfo();
        setStatus("Editing: " + selectedNPC.getName());
    }

    private void updateSpritePreview() {
        String key = (String) spriteCombo.getSelectedItem();
        if (key == null) { previewImage = null; spritePreviewPanel.repaint(); return; }

        // Try ImageAssetRegistry first (cached), fall back to disk
        Image img = ImageAssetRegistry.get(key);
        if (img instanceof BufferedImage bi) {
            previewImage = bi;
        } else if (img != null) {
            // Convert Image to BufferedImage
            previewImage = new BufferedImage(img.getWidth(null), img.getHeight(null),
                BufferedImage.TYPE_INT_ARGB);
            previewImage.getGraphics().drawImage(img, 0, 0, null);
        } else {
            // Fall back to disk
            File f = new File("src/main/resources/tiles/" + key + ".png");
            if (!f.exists()) f = new File("src/main/resources/tiles/" + key);
            try { previewImage = f.exists() ? ImageIO.read(f) : null; }
            catch (Exception ex) { previewImage = null; }
        }
        spritePreviewPanel.repaint();
    }

    private void openSpriteEditor() {
        String key = (String) spriteCombo.getSelectedItem();
        if (key != null && !key.trim().isEmpty())
            ImageEditor.openSprite(key.trim());
        else
            new ImageEditor();
    }

    private void openShopItemEditor() {
        if (selectedNPC == null) return;

        JDialog dlg = new JDialog(this, "Edit Shop Items", true);
        dlg.setSize(600, 440);
        dlg.setLocationRelativeTo(this);
        dlg.getContentPane().setBackground(BG);
        dlg.setLayout(new BorderLayout(0, 0));

        // Title
        JLabel title = makeLabel("\u25c8  SHOP  INVENTORY", PHOSPHOR2);
        title.setFont(MONO_LG);
        title.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_COL),
            BorderFactory.createEmptyBorder(10, 16, 10, 16)));
        dlg.add(title, BorderLayout.NORTH);

        // Build available and selected item lists
        List<Item> allShopItems = ItemRegistry.getAllShopItems();
        List<String> currentIds = new ArrayList<>(selectedNPC.getShopItemIds());

        DefaultListModel<String> availModel = new DefaultListModel<>();
        DefaultListModel<String> selModel   = new DefaultListModel<>();

        // Map display names to IDs
        java.util.Map<String, String> displayToId = new java.util.LinkedHashMap<>();
        for (Item item : allShopItems) {
            displayToId.put(item.getName() + "  (" + item.getId() + ")", item.getId());
        }

        // Populate models
        for (String id : currentIds) {
            Item item = ItemRegistry.getById(id);
            if (item != null) selModel.addElement(item.getName() + "  (" + item.getId() + ")");
        }
        for (var entry : displayToId.entrySet()) {
            if (!currentIds.contains(entry.getValue())) availModel.addElement(entry.getKey());
        }

        JList<String> availList = new JList<>(availModel);
        JList<String> selList   = new JList<>(selModel);
        @SuppressWarnings("unchecked")
        JList<String>[] lists = new JList[]{availList, selList};
        for (JList<String> list : lists) {
            list.setBackground(BG);
            list.setForeground(TEXT_BRIGHT);
            list.setFont(MONO_SM);
            list.setSelectionBackground(SEL_BG);
            list.setSelectionForeground(PHOSPHOR);
        }

        // Center panel: Available | Buttons | Selected
        JPanel center = new JPanel(new BorderLayout(8, 0));
        center.setBackground(PANEL_BG);
        center.setBorder(BorderFactory.createEmptyBorder(10, 16, 10, 16));

        // Available panel
        JPanel availPanel = new JPanel(new BorderLayout(0, 4));
        availPanel.setBackground(PANEL_BG);
        availPanel.add(makeLabel("AVAILABLE", ACCENT), BorderLayout.NORTH);
        JScrollPane availSp = new JScrollPane(availList);
        availSp.setBorder(BorderFactory.createLineBorder(BORDER_COL));
        availSp.getVerticalScrollBar().setUI(new RetroScrollBarUI());
        availPanel.add(availSp, BorderLayout.CENTER);

        // Selected panel
        JPanel selPanel = new JPanel(new BorderLayout(0, 4));
        selPanel.setBackground(PANEL_BG);
        selPanel.add(makeLabel("SELECTED", AMBER), BorderLayout.NORTH);
        JScrollPane selSp = new JScrollPane(selList);
        selSp.setBorder(BorderFactory.createLineBorder(BORDER_COL));
        selSp.getVerticalScrollBar().setUI(new RetroScrollBarUI());
        selPanel.add(selSp, BorderLayout.CENTER);

        // Arrow buttons
        JPanel arrows = new JPanel(new GridBagLayout());
        arrows.setBackground(PANEL_BG);
        JButton addBtn    = bigButton("\u25b6  Add",    PHOSPHOR, new Color(0, 30, 20));
        JButton removeBtn = bigButton("\u25c0  Remove", DANGER,   new Color(40, 0, 0));
        GridBagConstraints abc = new GridBagConstraints();
        abc.gridx = 0; abc.gridy = 0; abc.insets = new Insets(4, 4, 4, 4);
        arrows.add(addBtn, abc);
        abc.gridy = 1;
        arrows.add(removeBtn, abc);

        addBtn.addActionListener(e -> {
            int[] indices = availList.getSelectedIndices();
            for (int i = indices.length - 1; i >= 0; i--) {
                String display = availModel.get(indices[i]);
                selModel.addElement(display);
                availModel.remove(indices[i]);
            }
        });

        removeBtn.addActionListener(e -> {
            int[] indices = selList.getSelectedIndices();
            for (int i = indices.length - 1; i >= 0; i--) {
                String display = selModel.get(indices[i]);
                availModel.addElement(display);
                selModel.remove(indices[i]);
            }
        });

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, availPanel, selPanel);
        split.setDividerLocation(220);
        split.setDividerSize(3);
        split.setBorder(null);
        split.setBackground(PANEL_BG);

        center.add(split, BorderLayout.CENTER);
        center.add(arrows, BorderLayout.EAST);
        dlg.add(center, BorderLayout.CENTER);

        // Bottom buttons
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 8));
        bottom.setBackground(new Color(8, 12, 20));
        bottom.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER_COL));

        JButton okBtn     = bigButton("OK",     PHOSPHOR, new Color(0, 30, 20));
        JButton cancelBtn = bigButton("Cancel", TEXT_DIM, new Color(20, 20, 20));

        cancelBtn.addActionListener(e -> dlg.dispose());
        okBtn.addActionListener(e -> {
            selectedNPC.getShopItemIds().clear();
            int skipped = 0;
            for (int i = 0; i < selModel.size(); i++) {
                String display = selModel.get(i);
                String id = displayToId.get(display);
                if (id != null) selectedNPC.getShopItemIds().add(id);
                else skipped++;
            }
            shopCheck.setSelected(!selectedNPC.getShopItemIds().isEmpty());
            editShopItemsBtn.setEnabled(shopCheck.isSelected());
            dlg.dispose();
            if (skipped > 0) setStatus("\u26a0  " + skipped + " shop item(s) skipped — ID not found in registry");
        });

        bottom.add(cancelBtn);
        bottom.add(okBtn);
        dlg.add(bottom, BorderLayout.SOUTH);

        dlg.setVisible(true);
    }

    /** Records a map command when this dialog was opened over a map view. */
    private void pushNpcCommand(MapCommand cmd) {
        if (canvas != null) canvas.pushCommand(cmd);
    }

    private void createNewNPC() {
        NPC newNPC = new NPC(null, "New NPC", "npcs/townsman",
            NPC.Type.TOWNSFOLK, "Hello traveler...", 10, 10);
        map.npcs.add(newNPC);
        pushNpcCommand(new MapCommand.NpcAddCommand(newNPC));
        refreshNPCList();
        npcList.setSelectedIndex(map.npcs.size() - 1);
        nameField.requestFocus();
        nameField.selectAll();
        setStatus("New NPC created \u2014 fill in details and save");
    }

    private void deleteSelected() {
        int idx = npcList.getSelectedIndex();
        if (idx < 0 || idx >= map.npcs.size()) return;
        if (JOptionPane.showConfirmDialog(this,
                "Delete this NPC permanently?", "Confirm Delete",
                JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
            NPC removed = map.npcs.remove(idx);
            pushNpcCommand(new MapCommand.NpcRemoveCommand(removed));
            selectedNPC = null;
            refreshNPCList();
            previewImage = null;
            spritePreviewPanel.repaint();
            if (!map.npcs.isEmpty()) {
                int newIdx = Math.min(idx, map.npcs.size() - 1);
                npcList.setSelectedIndex(newIdx);
            }
            setStatus("NPC deleted");
        }
    }

    private void saveCurrent() {
        if (selectedNPC == null) { setStatus("\u26a0  Select an NPC first"); return; }
        editor.markDirty();

        // ── Pre-save validation ──────────────────────────────────────────────
        String npcName = nameField.getText().trim();
        if (npcName.isEmpty()) { setStatus("\u26a0  NPC name cannot be empty"); return; }

        java.util.List<String> warnings = new java.util.ArrayList<>();
        String sprite = (String) spriteCombo.getSelectedItem();
        if (sprite != null && !sprite.isEmpty()
                && !ImageAssetRegistry.getAllSpriteKeys().contains(sprite)) {
            warnings.add("Sprite '" + sprite + "' not found in ImageAssetRegistry");
        }
        if (shopCheck.isSelected() && selectedNPC.getShopItemIds().isEmpty()) {
            warnings.add("Shop is enabled but no items are assigned");
        }
        if (!warnings.isEmpty()) {
            String msg = "Validation warnings:\n\n\u2022 " + String.join("\n\u2022 ", warnings)
                       + "\n\nSave anyway?";
            if (JOptionPane.showConfirmDialog(this, msg, "Validation", JOptionPane.YES_NO_OPTION)
                    != JOptionPane.YES_OPTION) return;
        }

        selectedNPC.setName(npcName);
        selectedNPC.setSpriteName(sprite != null ? sprite : "townsman.png");
        selectedNPC.setType((NPC.Type) typeCombo.getSelectedItem());
        selectedNPC.setDefaultDialog(dialogArea.getText().trim());
        selectedNPC.setQuestCompleteDialog(postQuestArea.getText().trim());

        String sel = (String) questCombo.getSelectedItem();
        selectedNPC.setQuestId(sel != null && !sel.startsWith("(None")
            ? sel.split(" \u2014 ")[0] : null);

        if (!shopCheck.isSelected()) {
            selectedNPC.getShopItemIds().clear();
        }

        refreshNPCList();
        setStatus("Saved: " + selectedNPC.getName());
    }

    private void refreshTreeInfo() {
        if (treeQuestsLabel == null || editTreeBtn == null) return;
        DialogueTree tree = selectedNPC != null ? selectedNPC.getDialogueTree() : null;
        int nodeCount = (tree != null && tree.getNodes() != null) ? tree.getNodes().size() : 0;
        List<String> titles = getTreeQuestTitles(tree);

        if (titles.isEmpty()) {
            treeQuestsLabel.setText("(none)");
            treeQuestsLabel.setForeground(TEXT_DIM);
        } else {
            treeQuestsLabel.setText(String.join(", ", titles));
            treeQuestsLabel.setForeground(AMBER);
        }

        if (nodeCount > 0) {
            editTreeBtn.setText("\u25c8  Edit Dialogue Tree  [" + nodeCount + " nodes]");
            editTreeBtn.setForeground(PHOSPHOR2);
            editTreeBtn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(PHOSPHOR2.darker(), 1),
                BorderFactory.createEmptyBorder(5, 14, 5, 14)));
        } else {
            editTreeBtn.setText("\u25c8  Edit Dialogue Tree...");
            editTreeBtn.setForeground(TEXT_DIM);
            editTreeBtn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(TEXT_DIM.darker(), 1),
                BorderFactory.createEmptyBorder(5, 14, 5, 14)));
        }
    }

    private List<String> getTreeQuestTitles(DialogueTree tree) {
        List<String> titles = new ArrayList<>();
        if (tree == null || tree.getNodes() == null) return titles;
        for (DialogueNode node : tree.getNodes().values()) {
            if (node.getActions() == null) continue;
            for (DialogueAction action : node.getActions()) {
                if (action.getType() == DialogueAction.Type.GIVE_QUEST
                        && action.getTarget() != null) {
                    Quest q = QuestRegistry.getById(action.getTarget());
                    String title = q != null ? q.getTitle() : action.getTarget();
                    if (!titles.contains(title)) titles.add(title);
                }
            }
        }
        return titles;
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

    private static <T> JComboBox<T> darkCombo(T[] items) {
        JComboBox<T> cb = new JComboBox<>(items);
        cb.setBackground(PANEL_BG);
        cb.setForeground(TEXT_BRIGHT);
        cb.setFont(MONO_SM);
        return cb;
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

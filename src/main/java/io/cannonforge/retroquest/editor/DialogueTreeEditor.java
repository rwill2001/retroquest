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

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.CardLayout;
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
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.plaf.basic.BasicSplitPaneDivider;
import javax.swing.plaf.basic.BasicSplitPaneUI;
import javax.swing.JSplitPane;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeSelectionModel;

import com.google.gson.Gson;

import io.cannonforge.retroquest.dialogue.DialogueAction;
import io.cannonforge.retroquest.dialogue.DialogueChoice;
import io.cannonforge.retroquest.dialogue.DialogueCondition;
import io.cannonforge.retroquest.dialogue.DialogueNode;
import io.cannonforge.retroquest.dialogue.DialogueTree;
import io.cannonforge.retroquest.model.Item;
import io.cannonforge.retroquest.model.NPC;
import io.cannonforge.retroquest.model.Quest;
import io.cannonforge.retroquest.registry.ItemRegistry;
import io.cannonforge.retroquest.registry.QuestRegistry;

/**
 * Visual editor for NPC dialogue trees in RetroForge.
 * Uses a JTree navigator (left) + CardLayout form panels (right).
 */
@SuppressWarnings("serial")
public class DialogueTreeEditor extends JDialog {

    private static final String CARD_TREE   = "TREE";
    private static final String CARD_NODE   = "NODE";
    private static final String CARD_CHOICE = "CHOICE";
    private static final String CARD_ACTION = "ACTION";
    private static final String CARD_COND   = "CONDITION";

    // ── State ───────────────────────────────────────────────────────────────
    private final NPC npc;
    private DialogueTree editTree;  // deep copy being edited
    private boolean applied = false;
    private boolean treeEdited = false;   // any structural edit since the tree was opened
    private boolean uiReady    = false;   // true once the initial navigator build is done

    // ── Navigator ───────────────────────────────────────────────────────────
    private DefaultMutableTreeNode treeRoot;
    private DefaultTreeModel treeModel;
    private JTree navTree;

    // ── Right panel cards ───────────────────────────────────────────────────
    private JPanel cardPanel;
    private CardLayout cardLayout;

    // ── Tree Properties card ────────────────────────────────────────────────
    private JComboBox<String> startNodeCombo;
    private JTextArea validationArea;

    // ── Node Editor card ────────────────────────────────────────────────────
    private JTextField nodeIdField;
    private JTextArea nodeTextField;
    private DefaultListModel<String> choicesListModel;
    private JList<String> choicesList;
    private DefaultListModel<String> actionsListModel;
    private JList<String> actionsList;

    // ── Extracted sub-panels ────────────────────────────────────────────────
    private ChoiceEditorPanel choicePanel;
    private ActionEditorPanel actionPanel;
    private ConditionEditorPanel conditionPanel;

    // ── Currently selected items (for saving edits back) ────────────────────
    private DialogueNode currentNode;

    // ── Flow preview ────────────────────────────────────────────────────────
    private FlowPreviewPanel flowPreview;

    private JLabel statusLabel;

    // ── Constructor ─────────────────────────────────────────────────────────
    public DialogueTreeEditor(JDialog parent, NPC npc) {
        super(parent, "DIALOGUE TREE EDITOR", true);
        this.npc = npc;

        // Deep-copy tree via Gson round-trip
        Gson gson = new Gson();
        if (npc.getDialogueTree() != null) {
            String json = gson.toJson(npc.getDialogueTree());
            editTree = gson.fromJson(json, DialogueTree.class);
        } else {
            editTree = createTemplate();
        }

        buildUI();
        refreshNavigator();
        uiReady = true;
        setSize(1200, 738);
        setMinimumSize(new Dimension(960, 576));
        setLocationRelativeTo(parent);
        setVisible(true);
    }

    /** Standalone constructor (from EDITORS menu, no parent dialog). */
    public DialogueTreeEditor(JFrame parent, NPC npc) { this(parent, npc, true); }

    /**
     * @param show false builds the editor without displaying it — the offscreen entry
     *        point {@code RetroRecorder} uses to film it. Modal, so the public
     *        constructor blocks its caller.
     */
    public DialogueTreeEditor(JFrame parent, NPC npc, boolean show) {
        super(parent, "DIALOGUE TREE EDITOR", true);
        this.npc = npc;

        Gson gson = new Gson();
        if (npc.getDialogueTree() != null) {
            String json = gson.toJson(npc.getDialogueTree());
            editTree = gson.fromJson(json, DialogueTree.class);
        } else {
            editTree = createTemplate();
        }

        buildUI();
        refreshNavigator();
        uiReady = true;
        setSize(1200, 738);
        setMinimumSize(new Dimension(960, 576));
        setLocationRelativeTo(parent);
        setVisible(show);
    }

    private DialogueTree createTemplate() {
        DialogueTree t = new DialogueTree();
        t.setStartNodeId("greeting");
        DialogueNode node = new DialogueNode();
        node.setId("greeting");
        node.setText("Hello, traveler.");
        DialogueChoice farewell = new DialogueChoice("Farewell.", null, null);
        node.setChoices(new ArrayList<>(List.of(farewell)));
        node.setActions(new ArrayList<>());
        t.putNode("greeting", node);
        return t;
    }

    // ── UI Construction ─────────────────────────────────────────────────────
    private void buildUI() {
        setLayout(new BorderLayout(0, 0));
        getContentPane().setBackground(BG);

        // Closing the window is Cancel, and Cancel throws the edited tree away
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosing(java.awt.event.WindowEvent e) { cancelAndClose(); }
        });

        add(buildTitleBar(), BorderLayout.NORTH);
        add(buildMainArea(), BorderLayout.CENTER);
        add(buildBottomBar(), BorderLayout.SOUTH);
    }

    private JPanel buildTitleBar() {
        return EditorTheme.buildTitleBar("DIALOGUE TREE EDITOR", PHOSPHOR2,
                "NPC: \"" + npc.getName() + "\"", AMBER, MONO_MD);
    }

    private JPanel buildMainArea() {
        JPanel p = new JPanel(new BorderLayout(0, 0));
        p.setBackground(BG);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                                          buildLeftPanel(), buildRightPanel());
        split.setDividerLocation(320);
        split.setDividerSize(3);
        split.setBorder(null);
        split.setUI(new BasicSplitPaneUI() {
            @Override public BasicSplitPaneDivider createDefaultDivider() {
                BasicSplitPaneDivider div = new BasicSplitPaneDivider(this) {
                    @Override public void paint(Graphics g) {
                        g.setColor(BORDER_COL);
                        g.fillRect(0, 0, getWidth(), getHeight());
                    }
                };
                return div;
            }
        });

        p.add(split, BorderLayout.CENTER);
        return p;
    }

    // ── Left Panel: Navigator + Flow Preview ────────────────────────────────
    private JPanel buildLeftPanel() {
        JPanel outer = new JPanel(new BorderLayout(0, 0));
        outer.setBackground(PANEL_BG);
        outer.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, BORDER_COL));
        outer.setPreferredSize(new Dimension(320, 0));

        JLabel hdr = makeLabel("\u25c8  TREE NAVIGATOR", PHOSPHOR2);
        hdr.setFont(MONO_MD);
        hdr.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 8));
        outer.add(hdr, BorderLayout.NORTH);

        // JTree
        treeRoot = new DefaultMutableTreeNode("DialogueTree");
        treeModel = new DefaultTreeModel(treeRoot);
        navTree = new JTree(treeModel);
        navTree.setBackground(BG);
        navTree.setFont(MONO_SM);
        navTree.setRootVisible(true);
        navTree.setShowsRootHandles(true);
        navTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        navTree.setCellRenderer(new CRTTreeCellRenderer());
        navTree.addTreeSelectionListener(e -> onTreeSelection());

        JScrollPane sp = new JScrollPane(navTree);
        sp.setBorder(BorderFactory.createEmptyBorder());
        sp.setBackground(BG);
        sp.getViewport().setBackground(BG);
        sp.getVerticalScrollBar().setUI(new RetroScrollBarUI());

        // Node CRUD buttons
        JPanel btns = new JPanel(new GridLayout(1, 3, 6, 0));
        btns.setBackground(PANEL_BG);
        btns.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER_COL),
            BorderFactory.createEmptyBorder(8, 10, 8, 10)));
        JButton addNodeBtn  = bigButton("+Node",     PHOSPHOR, new Color(0, 40, 20));
        JButton deleteBtn   = bigButton("Delete",    DANGER,   new Color(40, 0, 0));
        JButton setStartBtn = bigButton("Set Start", AMBER,    new Color(40, 25, 0));
        addNodeBtn.addActionListener(e -> addNode());
        deleteBtn.addActionListener(e -> deleteSelected());
        setStartBtn.addActionListener(e -> setSelectedAsStart());
        btns.add(addNodeBtn);
        btns.add(deleteBtn);
        btns.add(setStartBtn);

        // Flow preview
        flowPreview = new FlowPreviewPanel();
        flowPreview.setPreferredSize(new Dimension(0, 120));
        flowPreview.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER_COL),
            BorderFactory.createEmptyBorder(4, 4, 4, 4)));

        JPanel centerPanel = new JPanel(new BorderLayout(0, 0));
        centerPanel.setBackground(PANEL_BG);
        centerPanel.add(sp, BorderLayout.CENTER);
        centerPanel.add(btns, BorderLayout.SOUTH);

        // Collapsible flow preview
        JPanel flowWrap = new JPanel(new BorderLayout(0, 0));
        flowWrap.setBackground(PANEL_BG);
        JLabel flowHdr = makeLabel("  FLOW PREVIEW", ACCENT);
        flowHdr.setFont(MONO_XS);
        flowHdr.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER_COL));
        flowWrap.add(flowHdr, BorderLayout.NORTH);
        flowWrap.add(flowPreview, BorderLayout.CENTER);

        JPanel bottomPart = new JPanel(new BorderLayout(0, 0));
        bottomPart.setBackground(PANEL_BG);
        bottomPart.add(btns, BorderLayout.NORTH);
        bottomPart.add(flowWrap, BorderLayout.CENTER);

        outer.add(centerPanel, BorderLayout.CENTER);
        // Replace the south of centerPanel
        centerPanel.remove(btns);
        centerPanel.add(sp, BorderLayout.CENTER);
        outer.add(bottomPart, BorderLayout.SOUTH);

        return outer;
    }

    // ── Right Panel: CardLayout ─────────────────────────────────────────────
    private JPanel buildRightPanel() {
        JPanel outer = new JPanel(new BorderLayout(0, 0));
        outer.setBackground(PANEL_BG);

        cardLayout = new CardLayout();
        cardPanel = new JPanel(cardLayout);
        cardPanel.setBackground(PANEL_BG);

        // Create sub-panels with callbacks
        actionPanel = new ActionEditorPanel(new ActionEditorPanel.Callback() {
            @Override public void onActionSaved() {
                loadNode(currentNode);
                cardLayout.show(cardPanel, CARD_NODE);
                refreshNavigator();
            }
            @Override public void navigateToCurrentNode() {
                DialogueTreeEditor.this.navigateToCurrentNode();
            }
            @Override public void setStatus(String msg) {
                DialogueTreeEditor.this.setStatus(msg);
            }
            @Override public String[] buildQuestComboItems() {
                return DialogueTreeEditor.this.buildQuestComboItems();
            }
            @Override public String[] buildItemComboItems() {
                return DialogueTreeEditor.this.buildItemComboItems();
            }
        });

        conditionPanel = new ConditionEditorPanel(new ConditionEditorPanel.Callback() {
            @Override public void onConditionSaved() {
                DialogueChoice choice = choicePanel.getCurrentChoice();
                int index = choicePanel.getCurrentChoiceIndex();
                if (choice != null) {
                    choicePanel.loadChoice(choice, index, editTree);
                    cardLayout.show(cardPanel, CARD_CHOICE);
                }
            }
            @Override public void navigateBackToChoice() {
                DialogueChoice choice = choicePanel.getCurrentChoice();
                int index = choicePanel.getCurrentChoiceIndex();
                if (choice != null) {
                    choicePanel.loadChoice(choice, index, editTree);
                    cardLayout.show(cardPanel, CARD_CHOICE);
                }
            }
            @Override public void setStatus(String msg) {
                DialogueTreeEditor.this.setStatus(msg);
            }
            @Override public String[] buildQuestComboItems() {
                return DialogueTreeEditor.this.buildQuestComboItems();
            }
            @Override public String[] buildItemComboItems() {
                return DialogueTreeEditor.this.buildItemComboItems();
            }
        });

        choicePanel = new ChoiceEditorPanel(new ChoiceEditorPanel.Callback() {
            @Override public void onChoiceSaved() {
                loadNode(currentNode);
                cardLayout.show(cardPanel, CARD_NODE);
                refreshNavigator();
            }
            @Override public void navigateToCurrentNode() {
                DialogueTreeEditor.this.navigateToCurrentNode();
            }
            @Override public void showConditionEditor(DialogueCondition cond) {
                conditionPanel.loadCondition(cond);
                cardLayout.show(cardPanel, CARD_COND);
            }
            @Override public void setStatus(String msg) {
                DialogueTreeEditor.this.setStatus(msg);
            }
        });

        cardPanel.add(buildTreePropsCard(), CARD_TREE);
        cardPanel.add(buildNodeEditorCard(), CARD_NODE);
        cardPanel.add(choicePanel, CARD_CHOICE);
        cardPanel.add(actionPanel, CARD_ACTION);
        cardPanel.add(conditionPanel, CARD_COND);

        outer.add(cardPanel, BorderLayout.CENTER);
        return outer;
    }

    // ── Card 1: Tree Properties ─────────────────────────────────────────────
    private JPanel buildTreePropsCard() {
        JPanel p = new JPanel(new BorderLayout(0, 12));
        p.setBackground(PANEL_BG);
        p.setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));

        JLabel hdr = makeLabel("\u25c8  TREE PROPERTIES", PHOSPHOR2);
        hdr.setFont(MONO_MD);

        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(PANEL_BG);
        GridBagConstraints gl = new GridBagConstraints();
        gl.gridx = 0; gl.anchor = GridBagConstraints.WEST;
        gl.insets = new Insets(6, 0, 6, 14);
        GridBagConstraints gf = new GridBagConstraints();
        gf.gridx = 1; gf.fill = GridBagConstraints.HORIZONTAL;
        gf.weightx = 1.0; gf.insets = new Insets(6, 0, 6, 0);

        startNodeCombo = new JComboBox<>();
        startNodeCombo.setBackground(PANEL_BG);
        startNodeCombo.setForeground(TEXT_BRIGHT);
        startNodeCombo.setFont(MONO_SM);
        startNodeCombo.addActionListener(e -> {
            String sel = (String) startNodeCombo.getSelectedItem();
            if (sel != null && editTree != null) {
                editTree.setStartNodeId(sel);
                refreshNavigator();
            }
        });

        gl.gridy = gf.gridy = 0;
        form.add(sectionLabel("START NODE:"), gl);
        form.add(startNodeCombo, gf);

        // Node count info
        gl.gridy = gf.gridy = 1;
        JLabel infoLabel = makeLabel("Click [Validate Tree] to check for issues", TEXT_DIM);
        infoLabel.setFont(MONO_XS);
        gf.gridwidth = 2; gl.gridwidth = 2;
        form.add(infoLabel, gf);

        // Validate button
        JButton validateBtn = bigButton("Validate Tree", PHOSPHOR, new Color(0, 40, 20));
        validateBtn.addActionListener(e -> runValidation());

        JPanel topForm = new JPanel(new BorderLayout(0, 10));
        topForm.setBackground(PANEL_BG);
        topForm.add(form, BorderLayout.NORTH);
        topForm.add(validateBtn, BorderLayout.CENTER);

        // Validation results
        validationArea = new JTextArea(12, 40);
        validationArea.setBackground(new Color(8, 12, 20));
        validationArea.setForeground(PHOSPHOR);
        validationArea.setCaretColor(PHOSPHOR);
        validationArea.setFont(MONO_SM);
        validationArea.setEditable(false);
        validationArea.setLineWrap(true);
        validationArea.setWrapStyleWord(true);
        validationArea.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        validationArea.setText("No validation run yet.");

        JScrollPane vsp = new JScrollPane(validationArea);
        vsp.setBorder(BorderFactory.createLineBorder(BORDER_COL));
        vsp.getVerticalScrollBar().setUI(new RetroScrollBarUI());

        JPanel valWrap = new JPanel(new BorderLayout(0, 4));
        valWrap.setBackground(PANEL_BG);
        valWrap.add(sectionLabel("VALIDATION RESULTS:"), BorderLayout.NORTH);
        valWrap.add(vsp, BorderLayout.CENTER);

        p.add(hdr, BorderLayout.NORTH);
        p.add(topForm, BorderLayout.CENTER);

        JPanel south = new JPanel(new BorderLayout(0, 0));
        south.setBackground(PANEL_BG);
        south.add(valWrap, BorderLayout.CENTER);
        p.add(south, BorderLayout.SOUTH);

        return p;
    }

    // ── Card 2: Node Editor ─────────────────────────────────────────────────
    private JPanel buildNodeEditorCard() {
        JPanel p = new JPanel(new BorderLayout(0, 8));
        p.setBackground(PANEL_BG);
        p.setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));

        JLabel hdr = makeLabel("\u25c8  NODE EDITOR", PHOSPHOR2);
        hdr.setFont(MONO_MD);
        p.add(hdr, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(PANEL_BG);
        GridBagConstraints gl = new GridBagConstraints();
        gl.gridx = 0; gl.anchor = GridBagConstraints.NORTHWEST;
        gl.insets = new Insets(6, 0, 6, 14);
        GridBagConstraints gf = new GridBagConstraints();
        gf.gridx = 1; gf.fill = GridBagConstraints.HORIZONTAL;
        gf.weightx = 1.0; gf.insets = new Insets(6, 0, 6, 0);

        nodeIdField = darkField(20);
        nodeTextField = darkTextArea(4, 35);

        gl.gridy = gf.gridy = 0;
        form.add(sectionLabel("NODE ID:"), gl);
        form.add(nodeIdField, gf);

        gl.gridy = gf.gridy = 1;
        gf.fill = GridBagConstraints.BOTH; gf.weighty = 0.3;
        JScrollPane textSp = new JScrollPane(nodeTextField);
        textSp.setBorder(BorderFactory.createLineBorder(BORDER_COL));
        textSp.getVerticalScrollBar().setUI(new RetroScrollBarUI());
        form.add(sectionLabel("TEXT:"), gl);
        form.add(textSp, gf);
        gf.fill = GridBagConstraints.HORIZONTAL; gf.weighty = 0;

        // Choices section
        gl.gridy = gf.gridy = 2;
        form.add(sectionLabel("CHOICES:"), gl);

        choicesListModel = new DefaultListModel<>();
        choicesList = new JList<>(choicesListModel);
        styleList(choicesList);
        choicesList.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) editSelectedChoice();
            }
        });

        JPanel choicesPanel = new JPanel(new BorderLayout(0, 4));
        choicesPanel.setBackground(PANEL_BG);
        JScrollPane csp = new JScrollPane(choicesList);
        csp.setBorder(BorderFactory.createLineBorder(BORDER_COL));
        csp.setPreferredSize(new Dimension(0, 100));
        csp.getVerticalScrollBar().setUI(new RetroScrollBarUI());
        choicesPanel.add(csp, BorderLayout.CENTER);

        JPanel choiceBtns = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        choiceBtns.setBackground(PANEL_BG);
        JButton addChoiceBtn = smallButton("+Choice", PHOSPHOR);
        JButton editChoiceBtn = smallButton("Edit", ACCENT);
        JButton delChoiceBtn = smallButton("Delete", DANGER);
        JButton moveUpChoiceBtn = smallButton("\u25b2", TEXT_BRIGHT);
        JButton moveDownChoiceBtn = smallButton("\u25bc", TEXT_BRIGHT);
        addChoiceBtn.addActionListener(e -> addChoice());
        editChoiceBtn.addActionListener(e -> editSelectedChoice());
        delChoiceBtn.addActionListener(e -> deleteSelectedChoice());
        moveUpChoiceBtn.addActionListener(e -> moveChoice(-1));
        moveDownChoiceBtn.addActionListener(e -> moveChoice(1));
        choiceBtns.add(addChoiceBtn);
        choiceBtns.add(editChoiceBtn);
        choiceBtns.add(delChoiceBtn);
        choiceBtns.add(moveUpChoiceBtn);
        choiceBtns.add(moveDownChoiceBtn);
        choicesPanel.add(choiceBtns, BorderLayout.SOUTH);

        gf.gridy = 2; gf.fill = GridBagConstraints.BOTH; gf.weighty = 0.3;
        form.add(choicesPanel, gf);
        gf.fill = GridBagConstraints.HORIZONTAL; gf.weighty = 0;

        // Actions section
        gl.gridy = gf.gridy = 3;
        form.add(sectionLabel("ACTIONS:"), gl);

        actionsListModel = new DefaultListModel<>();
        actionsList = new JList<>(actionsListModel);
        styleList(actionsList);
        actionsList.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) editSelectedAction();
            }
        });

        JPanel actionsPanel = new JPanel(new BorderLayout(0, 4));
        actionsPanel.setBackground(PANEL_BG);
        JScrollPane asp = new JScrollPane(actionsList);
        asp.setBorder(BorderFactory.createLineBorder(BORDER_COL));
        asp.setPreferredSize(new Dimension(0, 80));
        asp.getVerticalScrollBar().setUI(new RetroScrollBarUI());
        actionsPanel.add(asp, BorderLayout.CENTER);

        JPanel actionBtns = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        actionBtns.setBackground(PANEL_BG);
        JButton addActionBtn = smallButton("+Action", PHOSPHOR);
        JButton editActionBtn = smallButton("Edit", ACCENT);
        JButton delActionBtn = smallButton("Delete", DANGER);
        addActionBtn.addActionListener(e -> addAction());
        editActionBtn.addActionListener(e -> editSelectedAction());
        delActionBtn.addActionListener(e -> deleteSelectedAction());
        actionBtns.add(addActionBtn);
        actionBtns.add(editActionBtn);
        actionBtns.add(delActionBtn);
        actionsPanel.add(actionBtns, BorderLayout.SOUTH);

        gf.gridy = 3; gf.fill = GridBagConstraints.BOTH; gf.weighty = 0.2;
        form.add(actionsPanel, gf);

        // Save node button
        JButton saveNodeBtn = bigButton("Save Node Changes", AMBER, new Color(40, 25, 0));
        saveNodeBtn.addActionListener(e -> saveCurrentNode());

        JPanel center = new JPanel(new BorderLayout(0, 8));
        center.setBackground(PANEL_BG);
        center.add(form, BorderLayout.CENTER);
        center.add(saveNodeBtn, BorderLayout.SOUTH);
        p.add(center, BorderLayout.CENTER);

        return p;
    }

    // ── Bottom Bar: Cancel + OK ─────────────────────────────────────────────
    private JPanel buildBottomBar() {
        JPanel outer = new JPanel(new BorderLayout(0, 0));
        outer.setBackground(new Color(6, 9, 14));

        statusLabel = makeLabel("  Ready", TEXT_DIM);
        statusLabel.setFont(MONO_XS);
        statusLabel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER_COL),
            BorderFactory.createEmptyBorder(4, 8, 4, 8)));

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 8));
        btns.setBackground(new Color(8, 12, 20));
        btns.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER_COL));

        JButton cancelBtn = bigButton("Cancel", TEXT_DIM, new Color(20, 20, 20));
        JButton okBtn = bigButton("OK - Apply", PHOSPHOR, new Color(0, 40, 20));
        cancelBtn.addActionListener(e -> cancelAndClose());
        okBtn.addActionListener(e -> applyAndClose());
        btns.add(cancelBtn);
        btns.add(okBtn);

        outer.add(statusLabel, BorderLayout.WEST);
        outer.add(btns, BorderLayout.EAST);
        outer.setPreferredSize(new Dimension(0, 44));
        return outer;
    }

    // ── Navigator refresh ───────────────────────────────────────────────────
    private void refreshNavigator() {
        if (uiReady) treeEdited = true;   // only mutations refresh after the initial build
        treeRoot.removeAllChildren();
        if (editTree == null || editTree.getNodes() == null) {
            treeModel.reload();
            return;
        }

        String startId = editTree.getStartNodeId();
        for (Map.Entry<String, DialogueNode> entry : editTree.getNodes().entrySet()) {
            String nodeId = entry.getKey();
            DialogueNode node = entry.getValue();
            boolean isStart = nodeId.equals(startId);
            String nodeLabel = (isStart ? "[*] " : "[ ] ") + nodeId;
            DefaultMutableTreeNode nodeTreeNode = new DefaultMutableTreeNode(
                new NavItem(NavItem.Kind.NODE, nodeId, nodeLabel));

            // Choices folder
            if (node.getChoices() != null && !node.getChoices().isEmpty()) {
                DefaultMutableTreeNode choicesFolder = new DefaultMutableTreeNode(
                    new NavItem(NavItem.Kind.FOLDER, nodeId, "Choices (" + node.getChoices().size() + ")"));
                for (int i = 0; i < node.getChoices().size(); i++) {
                    DialogueChoice c = node.getChoices().get(i);
                    String label = "\"" + truncate(c.getLabel(), 25) + "\"";
                    choicesFolder.add(new DefaultMutableTreeNode(
                        new NavItem(NavItem.Kind.CHOICE, nodeId, label, i)));
                }
                nodeTreeNode.add(choicesFolder);
            }

            // Actions folder
            if (node.getActions() != null && !node.getActions().isEmpty()) {
                DefaultMutableTreeNode actionsFolder = new DefaultMutableTreeNode(
                    new NavItem(NavItem.Kind.FOLDER, nodeId, "Actions (" + node.getActions().size() + ")"));
                for (int i = 0; i < node.getActions().size(); i++) {
                    DialogueAction a = node.getActions().get(i);
                    actionsFolder.add(new DefaultMutableTreeNode(
                        new NavItem(NavItem.Kind.ACTION, nodeId, a.getType().name(), i)));
                }
                nodeTreeNode.add(actionsFolder);
            }

            treeRoot.add(nodeTreeNode);
        }

        treeModel.reload();
        // Expand all
        for (int i = 0; i < navTree.getRowCount(); i++) {
            navTree.expandRow(i);
        }

        // Update start node combo
        refreshStartNodeCombo();
        // Update flow preview
        flowPreview.repaint();
    }

    private void refreshStartNodeCombo() {
        if (startNodeCombo == null) return;
        String current = editTree.getStartNodeId();
        startNodeCombo.removeAllItems();
        if (editTree.getNodes() != null) {
            for (String id : editTree.getNodes().keySet()) {
                startNodeCombo.addItem(id);
            }
        }
        if (current != null) startNodeCombo.setSelectedItem(current);
    }

    // ── Tree selection handler ──────────────────────────────────────────────
    private void onTreeSelection() {
        DefaultMutableTreeNode selected =
            (DefaultMutableTreeNode) navTree.getLastSelectedPathComponent();
        if (selected == null || selected == treeRoot) {
            cardLayout.show(cardPanel, CARD_TREE);
            return;
        }

        Object userObj = selected.getUserObject();
        if (!(userObj instanceof NavItem item)) {
            cardLayout.show(cardPanel, CARD_TREE);
            return;
        }

        DialogueNode node = editTree.getNode(item.nodeId);
        if (node == null) return;

        switch (item.kind) {
            case NODE -> {
                loadNode(node);
                cardLayout.show(cardPanel, CARD_NODE);
            }
            case FOLDER -> {
                loadNode(node);
                cardLayout.show(cardPanel, CARD_NODE);
            }
            case CHOICE -> {
                currentNode = node;
                if (node.getChoices() != null && item.index < node.getChoices().size()) {
                    choicePanel.loadChoice(node.getChoices().get(item.index),
                        item.index, editTree);
                    cardLayout.show(cardPanel, CARD_CHOICE);
                }
            }
            case ACTION -> {
                currentNode = node;
                if (node.getActions() != null && item.index < node.getActions().size()) {
                    actionPanel.loadAction(node.getActions().get(item.index));
                    cardLayout.show(cardPanel, CARD_ACTION);
                }
            }
        }
    }

    // ── Load data into node card ────────────────────────────────────────────
    private void loadNode(DialogueNode node) {
        currentNode = node;
        nodeIdField.setText(node.getId());
        nodeTextField.setText(node.getText() != null ? node.getText() : "");

        choicesListModel.clear();
        if (node.getChoices() != null) {
            for (DialogueChoice c : node.getChoices()) {
                String next = c.getNextNodeId() != null ? " \u2192 " + c.getNextNodeId() : " \u2192 (End)";
                int condCount = c.getConditions() != null ? c.getConditions().size() : 0;
                String condStr = condCount > 0 ? " [" + condCount + " cond]" : "";
                choicesListModel.addElement("\"" + truncate(c.getLabel(), 30) + "\"" + next + condStr);
            }
        }

        actionsListModel.clear();
        if (node.getActions() != null) {
            for (DialogueAction a : node.getActions()) {
                actionsListModel.addElement(formatAction(a));
            }
        }
    }

    // ── Save operations ─────────────────────────────────────────────────────
    private void saveCurrentNode() {
        if (currentNode == null) return;

        String newId = nodeIdField.getText().trim();
        String oldId = currentNode.getId();

        if (newId.isEmpty()) {
            setStatus("\u26a0 Node ID cannot be empty");
            return;
        }

        // Handle rename
        if (!newId.equals(oldId)) {
            if (editTree.getNode(newId) != null) {
                setStatus("\u26a0 Node ID '" + newId + "' already exists");
                return;
            }
            // Update all nextNodeId references
            for (DialogueNode n : editTree.getNodes().values()) {
                if (n.getChoices() != null) {
                    for (DialogueChoice c : n.getChoices()) {
                        if (oldId.equals(c.getNextNodeId())) {
                            c.setNextNodeId(newId);
                        }
                    }
                }
            }
            // Update start node ref
            if (oldId.equals(editTree.getStartNodeId())) {
                editTree.setStartNodeId(newId);
            }
            // Re-key in map
            editTree.removeNode(oldId);
            currentNode.setId(newId);
            editTree.putNode(newId, currentNode);
        }

        currentNode.setText(nodeTextField.getText());
        refreshNavigator();
        setStatus("Node '" + newId + "' saved");
    }

    // ── CRUD operations ─────────────────────────────────────────────────────
    private void addNode() {
        String id = JOptionPane.showInputDialog(this, "New node ID:", "greeting_" +
            (editTree.getNodes() != null ? editTree.getNodes().size() : 0));
        if (id == null || id.trim().isEmpty()) return;
        id = id.trim().replaceAll("\\s+", "_");

        if (editTree.getNode(id) != null) {
            setStatus("\u26a0 Node '" + id + "' already exists");
            return;
        }

        DialogueNode node = new DialogueNode();
        node.setId(id);
        node.setText("Enter text here...");
        node.setChoices(new ArrayList<>());
        node.setActions(new ArrayList<>());
        editTree.putNode(id, node);

        refreshNavigator();
        setStatus("Node '" + id + "' added");
    }

    private void deleteSelected() {
        DefaultMutableTreeNode selected =
            (DefaultMutableTreeNode) navTree.getLastSelectedPathComponent();
        if (selected == null || selected == treeRoot) return;

        Object userObj = selected.getUserObject();
        if (!(userObj instanceof NavItem item)) return;

        DialogueNode node = editTree.getNode(item.nodeId);
        if (node == null) return;

        switch (item.kind) {
            case NODE -> {
                // Warn about what points at it before asking, and clear those references after.
                // The rename path above already fixes both nextNodeId back-references and
                // startNodeId; deletion fixed neither, so a choice could be left aiming at a node
                // that no longer exists. DialogueOverlay.selectChoice then silently closes the
                // conversation, and for a "..." auto-route node renders an empty dialogue box.
                int refs = 0;
                for (DialogueNode n : editTree.getNodes().values()) {
                    if (n.getChoices() == null) continue;
                    for (DialogueChoice c : n.getChoices()) {
                        if (item.nodeId.equals(c.getNextNodeId())) refs++;
                    }
                }
                boolean isStart = item.nodeId.equals(editTree.getStartNodeId());
                String warn = "Delete node '" + item.nodeId + "'?";
                if (refs > 0) warn += "\n\n" + refs + " choice(s) point here; they will be cleared.";
                if (isStart) warn += "\n\nThis is the START node — the tree will have no entry point.";

                if (JOptionPane.showConfirmDialog(this, warn, "Confirm",
                        JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                    for (DialogueNode n : editTree.getNodes().values()) {
                        if (n.getChoices() == null) continue;
                        for (DialogueChoice c : n.getChoices()) {
                            if (item.nodeId.equals(c.getNextNodeId())) c.setNextNodeId(null);
                        }
                    }
                    if (isStart) editTree.setStartNodeId(null);
                    editTree.removeNode(item.nodeId);
                    refreshNavigator();
                    cardLayout.show(cardPanel, CARD_TREE);
                    setStatus(refs > 0 ? "Node deleted — " + refs + " reference(s) cleared"
                                       : "Node deleted");
                }
            }
            case CHOICE -> {
                if (node.getChoices() != null && item.index < node.getChoices().size()) {
                    node.getChoices().remove(item.index);
                    refreshNavigator();
                    loadNode(node);
                    setStatus("Choice deleted");
                }
            }
            case ACTION -> {
                if (node.getActions() != null && item.index < node.getActions().size()) {
                    node.getActions().remove(item.index);
                    refreshNavigator();
                    loadNode(node);
                    setStatus("Action deleted");
                }
            }
            default -> {}
        }
    }

    private void setSelectedAsStart() {
        DefaultMutableTreeNode selected =
            (DefaultMutableTreeNode) navTree.getLastSelectedPathComponent();
        if (selected == null) return;
        Object userObj = selected.getUserObject();
        if (!(userObj instanceof NavItem item) || item.kind != NavItem.Kind.NODE) return;

        editTree.setStartNodeId(item.nodeId);
        refreshNavigator();
        setStatus("Start node set to '" + item.nodeId + "'");
    }

    private void addChoice() {
        if (currentNode == null) return;
        if (currentNode.getChoices() == null) currentNode.setChoices(new ArrayList<>());

        DialogueChoice choice = new DialogueChoice("New choice", null, new ArrayList<>());
        currentNode.getChoices().add(choice);
        loadNode(currentNode);
        refreshNavigator();
    }

    private void editSelectedChoice() {
        int idx = choicesList.getSelectedIndex();
        if (idx < 0 || currentNode == null || currentNode.getChoices() == null || (idx >= currentNode.getChoices().size())) return;

        choicePanel.loadChoice(currentNode.getChoices().get(idx), idx, editTree);
        cardLayout.show(cardPanel, CARD_CHOICE);
    }

    private void deleteSelectedChoice() {
        int idx = choicesList.getSelectedIndex();
        if (idx < 0 || currentNode == null || currentNode.getChoices() == null) return;
        currentNode.getChoices().remove(idx);
        loadNode(currentNode);
        refreshNavigator();
    }

    private void moveChoice(int dir) {
        int idx = choicesList.getSelectedIndex();
        if (idx < 0 || currentNode == null || currentNode.getChoices() == null) return;
        int newIdx = idx + dir;
        if (newIdx < 0 || newIdx >= currentNode.getChoices().size()) return;
        Collections.swap(currentNode.getChoices(), idx, newIdx);
        loadNode(currentNode);
        choicesList.setSelectedIndex(newIdx);
        refreshNavigator();
    }

    private void addAction() {
        if (currentNode == null) return;
        if (currentNode.getActions() == null) currentNode.setActions(new ArrayList<>());

        DialogueAction action = new DialogueAction(DialogueAction.Type.LOG_MESSAGE,
            "message", "INFO", 0);
        currentNode.getActions().add(action);
        loadNode(currentNode);
        refreshNavigator();
    }

    private void editSelectedAction() {
        int idx = actionsList.getSelectedIndex();
        if (idx < 0 || currentNode == null || currentNode.getActions() == null || (idx >= currentNode.getActions().size())) return;

        actionPanel.loadAction(currentNode.getActions().get(idx));
        cardLayout.show(cardPanel, CARD_ACTION);
    }

    private void deleteSelectedAction() {
        int idx = actionsList.getSelectedIndex();
        if (idx < 0 || currentNode == null || currentNode.getActions() == null) return;
        currentNode.getActions().remove(idx);
        loadNode(currentNode);
        refreshNavigator();
    }

    private void navigateToCurrentNode() {
        if (currentNode != null) {
            loadNode(currentNode);
            cardLayout.show(cardPanel, CARD_NODE);
        }
    }

    // ── Validation ──────────────────────────────────────────────────────────
    private void runValidation() {
        List<String> warnings = new ArrayList<>();
        Map<String, DialogueNode> nodes = editTree.getNodes();

        if (nodes == null || nodes.isEmpty()) {
            warnings.add("ERROR: Tree has no nodes");
            showValidation(warnings);
            return;
        }

        // Check start node
        String startId = editTree.getStartNodeId();
        if (startId == null || startId.isEmpty()) {
            warnings.add("ERROR: No start node ID set");
        } else if (editTree.getNode(startId) == null) {
            warnings.add("ERROR: Start node '" + startId + "' does not exist");
        }

        // Check each node
        for (Map.Entry<String, DialogueNode> entry : nodes.entrySet()) {
            String nodeId = entry.getKey();
            DialogueNode node = entry.getValue();

            if (node.getText() == null || node.getText().trim().isEmpty()) {
                warnings.add("WARN: Node '" + nodeId + "' has empty text");
            }

            if (node.getChoices() != null) {
                for (int i = 0; i < node.getChoices().size(); i++) {
                    DialogueChoice c = node.getChoices().get(i);
                    if (c.getLabel() == null || c.getLabel().trim().isEmpty()) {
                        warnings.add("WARN: Node '" + nodeId + "' choice #" + i + " has empty label");
                    }
                    String nextId = c.getNextNodeId();
                    if (nextId != null && editTree.getNode(nextId) == null) {
                        warnings.add("ERROR: Node '" + nodeId + "' choice #" + i +
                            " refs missing node '" + nextId + "'");
                    }
                }
            }
        }

        // Check for orphaned nodes (BFS from start)
        if (startId != null && editTree.getNode(startId) != null) {
            Set<String> reachable = new LinkedHashSet<>();
            Queue<String> queue = new LinkedList<>();
            queue.add(startId);
            reachable.add(startId);
            while (!queue.isEmpty()) {
                String id = queue.poll();
                DialogueNode n = editTree.getNode(id);
                if (n == null || n.getChoices() == null) continue;
                for (DialogueChoice c : n.getChoices()) {
                    String next = c.getNextNodeId();
                    if (next != null && !reachable.contains(next) && editTree.getNode(next) != null) {
                        reachable.add(next);
                        queue.add(next);
                    }
                }
            }
            for (String id : nodes.keySet()) {
                if (!reachable.contains(id)) {
                    warnings.add("WARN: Node '" + id + "' is unreachable from start");
                }
            }
        }

        // Check action/condition registry IDs
        for (Map.Entry<String, DialogueNode> entry : nodes.entrySet()) {
            String nodeId = entry.getKey();
            DialogueNode node = entry.getValue();
            if (node.getActions() != null) {
                for (int i = 0; i < node.getActions().size(); i++) {
                    DialogueAction a = node.getActions().get(i);
                    if (a.getType() == null) continue;
                    String tgt = a.getTarget();
                    if ((a.getType() == DialogueAction.Type.GIVE_QUEST || a.getType() == DialogueAction.Type.COMPLETE_QUEST)
                            && tgt != null && !tgt.isEmpty()) {
                        if (QuestRegistry.getById(tgt) == null) {
                            warnings.add("WARN: Node '" + nodeId + "' action #" + i
                                + " references unknown quest '" + tgt + "'");
                        }
                    } else if ((a.getType() == DialogueAction.Type.GIVE_ITEM || a.getType() == DialogueAction.Type.TAKE_ITEM)
                            && tgt != null && !tgt.isEmpty()) {
                        if (ItemRegistry.getById(tgt) == null) {
                            warnings.add("WARN: Node '" + nodeId + "' action #" + i
                                + " references unknown item '" + tgt + "'");
                        }
                    }
                }
            }
            if (node.getChoices() != null) {
                for (int ci = 0; ci < node.getChoices().size(); ci++) {
                    DialogueChoice c = node.getChoices().get(ci);
                    if (c.getConditions() == null) continue;
                    for (int cdi = 0; cdi < c.getConditions().size(); cdi++) {
                        DialogueCondition cond = c.getConditions().get(cdi);
                        if (cond.getType() == null) continue;
                        String tgt = cond.getTarget();
                        if ((cond.getType() == DialogueCondition.Type.QUEST_COMPLETE || cond.getType() == DialogueCondition.Type.QUEST_ACTIVE)
                                && tgt != null && !tgt.isEmpty()) {
                            if (QuestRegistry.getById(tgt) == null) {
                                warnings.add("WARN: Node '" + nodeId + "' choice #" + ci + " condition #" + cdi
                                    + " references unknown quest '" + tgt + "'");
                            }
                        } else if (cond.getType() == DialogueCondition.Type.HAS_ITEM
                                && tgt != null && !tgt.isEmpty()) {
                            if (ItemRegistry.getById(tgt) == null) {
                                warnings.add("WARN: Node '" + nodeId + "' choice #" + ci + " condition #" + cdi
                                    + " references unknown item '" + tgt + "'");
                            }
                        }
                    }
                }
            }
        }

        if (warnings.isEmpty()) {
            warnings.add("OK: All checks passed (" + nodes.size() + " nodes)");
        }

        showValidation(warnings);
    }

    private void showValidation(List<String> warnings) {
        StringBuilder sb = new StringBuilder();
        for (String w : warnings) {
            sb.append(w).append("\n");
        }
        validationArea.setText(sb.toString());
        validationArea.setForeground(
            sb.toString().contains("ERROR") ? DANGER :
            sb.toString().contains("WARN") ? AMBER : PHOSPHOR);
        cardLayout.show(cardPanel, CARD_TREE);
        setStatus("Validation complete: " + warnings.size() + " result(s)");
    }

    // ── Apply and close ─────────────────────────────────────────────────────
    /** Cancel and the window's X both discard every edit — ask first. */
    private void cancelAndClose() {
        if (treeEdited && JOptionPane.showConfirmDialog(this,
                "Discard all changes to this dialogue tree?", "Discard Changes",
                JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        dispose();
    }

    private void applyAndClose() {
        npc.setDialogueTree(editTree);
        applied = true;
        dispose();
    }

    public boolean isApplied() { return applied; }

    // ── Format helpers ──────────────────────────────────────────────────────
    private String formatAction(DialogueAction a) {
        if (a.getType() == null) return "(no type)";
        String s = a.getType().name();
        if (a.getTarget() != null && !a.getTarget().isEmpty())
            s += ": " + truncate(a.getTarget(), 20);
        if (a.getAmount() != 0) s += " (" + a.getAmount() + ")";
        return s;
    }

    static String formatCondition(DialogueCondition c) {
        if (c.getType() == null) return "(no type)";
        String s = (c.isNegate() ? "NOT " : "") + c.getType().name();
        if (c.getTarget() != null && !c.getTarget().isEmpty())
            s += ": " + truncate(c.getTarget(), 20);
        if (c.getAmount() != 0) s += " (" + c.getAmount() + ")";
        return s;
    }

    static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }

    private void setStatus(String msg) {
        if (statusLabel != null) {
            statusLabel.setText("  " + msg);
            statusLabel.setForeground(msg.startsWith("\u26a0") ? DANGER : TEXT_DIM);
        }
    }

    // ── Registry combo helpers (package-private for sub-panels) ─────────────
    String[] buildQuestComboItems() {
        List<Quest> quests = new ArrayList<>(QuestRegistry.getAllQuests());
        quests.sort(Comparator.comparing(Quest::getId));
        String[] items = new String[quests.size() + 1];
        items[0] = "";
        for (int i = 0; i < quests.size(); i++) {
            items[i + 1] = quests.get(i).getId() + "  \u2014  " + quests.get(i).getTitle();
        }
        return items;
    }

    String[] buildItemComboItems() {
        List<Item> itemList = new ArrayList<>(ItemRegistry.getAllItems());
        itemList.sort(Comparator.comparing(Item::getId));
        String[] arr = new String[itemList.size() + 1];
        arr[0] = "";
        for (int i = 0; i < itemList.size(); i++) {
            arr[i + 1] = itemList.get(i).getId() + "  \u2014  " + itemList.get(i).getName();
        }
        return arr;
    }

    static String[] buildSpellList() {
        return new String[] {
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
            "Meteor Swarm", "Power Word Kill", "Wish", "Time Stop",
            "Divine Intervention", "Sanctuary", "Resurrection"
        };
    }

    static String extractId(String comboValue) {
        if (comboValue == null || comboValue.isEmpty()) return "";
        int sep = comboValue.indexOf("  \u2014  ");
        return sep >= 0 ? comboValue.substring(0, sep) : comboValue;
    }

    static void selectComboById(JComboBox<String> combo, String id) {
        if (id == null || id.isEmpty()) { combo.setSelectedIndex(0); return; }
        for (int i = 1; i < combo.getItemCount(); i++) {
            if (combo.getItemAt(i).startsWith(id + "  ")) {
                combo.setSelectedIndex(i);
                return;
            }
        }
        combo.setSelectedIndex(0);
    }

    static void refreshCombo(JComboBox<String> combo, String[] items) {
        combo.removeAllItems();
        for (String item : items) combo.addItem(item);
    }

    // ── Styled component factories (package-private for sub-panels) ─────────
    static JTextField darkField(int cols) {
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

    static JLabel makeLabel(String txt, Color c) {
        JLabel l = new JLabel(txt); l.setForeground(c); l.setFont(MONO_SM); return l;
    }

    static JLabel sectionLabel(String txt) {
        JLabel l = new JLabel(txt);
        l.setForeground(ACCENT);
        l.setFont(new Font("Monospaced", Font.BOLD, 10));
        return l;
    }

    static JButton bigButton(String txt, Color fg, Color bg) {
        JButton b = new JButton(txt) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setColor(getModel().isPressed() ? bg.brighter() : getBackground());
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setColor(getForeground());
                g2.setFont(getFont());
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(getText(),
                    (getWidth() - fm.stringWidth(getText())) / 2,
                    (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
            }
        };
        b.setFont(MONO_MD);
        b.setForeground(fg);
        b.setBackground(bg);
        b.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(fg.darker(), 1),
            BorderFactory.createEmptyBorder(5, 14, 5, 14)));
        b.setFocusPainted(false);
        b.setContentAreaFilled(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    static JButton smallButton(String txt, Color fg) {
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
        b.setForeground(fg);
        b.setBackground(new Color(12, 20, 32));
        b.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_COL),
            BorderFactory.createEmptyBorder(2, 8, 2, 8)));
        b.setFocusPainted(false);
        b.setContentAreaFilled(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    static void styleList(JList<String> list) {
        list.setBackground(BG);
        list.setForeground(TEXT_BRIGHT);
        list.setFont(MONO_SM);
        list.setSelectionBackground(SEL_BG);
        list.setSelectionForeground(PHOSPHOR2);
    }

    // ── Nav item for JTree nodes ────────────────────────────────────────────
    private static class NavItem {
        enum Kind { NODE, FOLDER, CHOICE, ACTION }
        final Kind kind;
        final String nodeId;
        final String label;
        final int index;

        NavItem(Kind kind, String nodeId, String label) {
            this(kind, nodeId, label, -1);
        }
        NavItem(Kind kind, String nodeId, String label, int index) {
            this.kind = kind;
            this.nodeId = nodeId;
            this.label = label;
            this.index = index;
        }
        @Override public String toString() { return label; }
    }

    // ── CRT-styled tree cell renderer ───────────────────────────────────────
    private class CRTTreeCellRenderer extends DefaultTreeCellRenderer {
        CRTTreeCellRenderer() {
            setBackgroundNonSelectionColor(BG);
            setBackgroundSelectionColor(SEL_BG);
            setTextNonSelectionColor(TEXT_BRIGHT);
            setTextSelectionColor(PHOSPHOR2);
            setBorderSelectionColor(PHOSPHOR2.darker());
            setFont(MONO_SM);
        }

        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value,
                boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
            Component c = super.getTreeCellRendererComponent(
                tree, value, sel, expanded, leaf, row, hasFocus);
            if (c instanceof JLabel lbl) {
                lbl.setFont(MONO_SM);
                if (value instanceof DefaultMutableTreeNode dmtn) {
                    Object uo = dmtn.getUserObject();
                    if (uo instanceof NavItem item) {
                        switch (item.kind) {
                            case NODE -> lbl.setForeground(sel ? PHOSPHOR2 : PHOSPHOR);
                            case FOLDER -> lbl.setForeground(sel ? PHOSPHOR2 : ACCENT);
                            case CHOICE -> lbl.setForeground(sel ? PHOSPHOR2 : TEXT_BRIGHT);
                            case ACTION -> lbl.setForeground(sel ? PHOSPHOR2 : AMBER);
                        }
                    }
                }
                setIcon(null);
            }
            return c;
        }
    }

    // ── Flow Preview Panel ──────────────────────────────────────────────────
    private class FlowPreviewPanel extends JPanel {
        FlowPreviewPanel() {
            setBackground(BG);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);

            if (editTree == null || editTree.getNodes() == null || editTree.getNodes().isEmpty()) {
                g2.setColor(TEXT_DIM);
                g2.setFont(MONO_XS);
                g2.drawString("No nodes", 10, 20);
                return;
            }

            // BFS from start node, draw boxes + arrows
            String startId = editTree.getStartNodeId();
            if (startId == null || editTree.getNode(startId) == null) {
                g2.setColor(TEXT_DIM);
                g2.setFont(MONO_XS);
                g2.drawString("No valid start node", 10, 20);
                return;
            }

            // BFS to get layout levels
            Map<String, Integer> levels = new LinkedHashMap<>();
            Map<Integer, List<String>> levelNodes = new LinkedHashMap<>();
            Queue<String> queue = new LinkedList<>();
            queue.add(startId);
            levels.put(startId, 0);

            while (!queue.isEmpty()) {
                String id = queue.poll();
                int lvl = levels.get(id);
                levelNodes.computeIfAbsent(lvl, k -> new ArrayList<>()).add(id);

                DialogueNode node = editTree.getNode(id);
                if (node == null || node.getChoices() == null) continue;
                for (DialogueChoice c : node.getChoices()) {
                    String next = c.getNextNodeId();
                    if (next != null && !levels.containsKey(next) && editTree.getNode(next) != null) {
                        levels.put(next, lvl + 1);
                        queue.add(next);
                    }
                }
            }

            // Draw
            int boxW = 80, boxH = 20, gapX = 30, gapY = 8;
            int startX = 8, startY = 8;
            Map<String, Point> centers = new HashMap<>();
            g2.setFont(MONO_XS);
            FontMetrics fm = g2.getFontMetrics();

            for (Map.Entry<Integer, List<String>> entry : levelNodes.entrySet()) {
                int col = entry.getKey();
                List<String> ids = entry.getValue();
                for (int row = 0; row < ids.size(); row++) {
                    String id = ids.get(row);
                    int x = startX + col * (boxW + gapX);
                    int y = startY + row * (boxH + gapY);
                    boolean isStart = id.equals(startId);

                    g2.setColor(isStart ? new Color(0, 60, 40) : new Color(20, 30, 45));
                    g2.fillRoundRect(x, y, boxW, boxH, 6, 6);
                    g2.setColor(isStart ? PHOSPHOR : PHOSPHOR2);
                    g2.drawRoundRect(x, y, boxW, boxH, 6, 6);

                    String label = truncate(id, 9);
                    g2.setColor(isStart ? PHOSPHOR : TEXT_BRIGHT);
                    int tw = fm.stringWidth(label);
                    g2.drawString(label, x + (boxW - tw) / 2, y + boxH / 2 + fm.getAscent() / 2 - 1);

                    centers.put(id, new Point(x + boxW, y + boxH / 2));
                }
            }

            // Draw arrows
            g2.setColor(new Color(0, 150, 100, 120));
            g2.setStroke(new BasicStroke(1.5f));
            for (Map.Entry<String, Integer> entry : levels.entrySet()) {
                String fromId = entry.getKey();
                DialogueNode node = editTree.getNode(fromId);
                if (node == null || node.getChoices() == null) continue;
                Point fromPt = centers.get(fromId);
                if (fromPt == null) continue;

                for (DialogueChoice c : node.getChoices()) {
                    String toId = c.getNextNodeId();
                    if (toId == null || !levels.containsKey(toId)) continue;
                    int col = levels.get(toId);
                    List<String> colIds = levelNodes.get(col);
                    if (colIds == null) continue;
                    int row = colIds.indexOf(toId);
                    if (row < 0) continue;

                    int toX = startX + col * (boxW + gapX);
                    int toY = startY + row * (boxH + gapY) + boxH / 2;

                    g2.drawLine(fromPt.x, fromPt.y, toX, toY);
                    // Arrowhead
                    int ax = toX - 5, ay1 = toY - 3, ay2 = toY + 3;
                    g2.fillPolygon(new int[]{toX, ax, ax}, new int[]{toY, ay1, ay2}, 3);
                }
            }
        }
    }
}

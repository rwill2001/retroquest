package io.cannonforge.retroquest.editor;

import static io.cannonforge.retroquest.editor.EditorTheme.ACCENT;
import static io.cannonforge.retroquest.editor.EditorTheme.AMBER;
import static io.cannonforge.retroquest.editor.EditorTheme.BORDER_COL;
import static io.cannonforge.retroquest.editor.EditorTheme.DANGER;
import static io.cannonforge.retroquest.editor.EditorTheme.MONO_SM;
import static io.cannonforge.retroquest.editor.EditorTheme.PANEL_BG;
import static io.cannonforge.retroquest.editor.EditorTheme.PHOSPHOR;
import static io.cannonforge.retroquest.editor.EditorTheme.PHOSPHOR2;
import static io.cannonforge.retroquest.editor.EditorTheme.TEXT_BRIGHT;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;

import io.cannonforge.retroquest.dialogue.DialogueChoice;
import io.cannonforge.retroquest.dialogue.DialogueCondition;
import io.cannonforge.retroquest.dialogue.DialogueTree;

/**
 * Sub-panel for editing a single {@link DialogueChoice} and its conditions.
 */
@SuppressWarnings("serial")
class ChoiceEditorPanel extends JPanel {

    /** Callback for communicating with the parent editor. */
    interface Callback {
        void onChoiceSaved();
        void navigateToCurrentNode();
        void showConditionEditor(DialogueCondition cond);
        void setStatus(String msg);
    }

    private final Callback callback;

    // ── Fields ──────────────────────────────────────────────────────────────
    private JTextField choiceLabelField;
    private JComboBox<String> nextNodeCombo;
    private DefaultListModel<String> conditionsListModel;
    private JList<String> conditionsList;

    private DialogueChoice currentChoice;
    private int currentChoiceIndex = -1;

    ChoiceEditorPanel(Callback callback) {
        super(new BorderLayout(0, 8));
        this.callback = callback;
        setBackground(PANEL_BG);
        setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));
        buildUI();
    }

    private void buildUI() {
        JLabel hdr = DialogueTreeEditor.makeLabel("\u25c8  CHOICE EDITOR", PHOSPHOR2);
        hdr.setFont(EditorTheme.MONO_MD);
        add(hdr, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(PANEL_BG);
        GridBagConstraints gl = new GridBagConstraints();
        gl.gridx = 0; gl.anchor = GridBagConstraints.NORTHWEST;
        gl.insets = new Insets(6, 0, 6, 14);
        GridBagConstraints gf = new GridBagConstraints();
        gf.gridx = 1; gf.fill = GridBagConstraints.HORIZONTAL;
        gf.weightx = 1.0; gf.insets = new Insets(6, 0, 6, 0);

        choiceLabelField = DialogueTreeEditor.darkField(30);
        nextNodeCombo = new JComboBox<>();
        nextNodeCombo.setBackground(PANEL_BG);
        nextNodeCombo.setForeground(TEXT_BRIGHT);
        nextNodeCombo.setFont(MONO_SM);

        gl.gridy = gf.gridy = 0;
        form.add(sectionLabel("LABEL:"), gl);
        form.add(choiceLabelField, gf);

        gl.gridy = gf.gridy = 1;
        form.add(sectionLabel("NEXT NODE:"), gl);
        form.add(nextNodeCombo, gf);

        // Conditions section
        gl.gridy = gf.gridy = 2;
        form.add(sectionLabel("CONDITIONS:"), gl);

        conditionsListModel = new DefaultListModel<>();
        conditionsList = new JList<>(conditionsListModel);
        DialogueTreeEditor.styleList(conditionsList);
        conditionsList.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) editSelectedCondition();
            }
        });

        JPanel condPanel = new JPanel(new BorderLayout(0, 4));
        condPanel.setBackground(PANEL_BG);
        JScrollPane csp = new JScrollPane(conditionsList);
        csp.setBorder(BorderFactory.createLineBorder(BORDER_COL));
        csp.setPreferredSize(new Dimension(0, 120));
        csp.getVerticalScrollBar().setUI(new RetroScrollBarUI());
        condPanel.add(csp, BorderLayout.CENTER);

        JPanel condBtns = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        condBtns.setBackground(PANEL_BG);
        JButton addCondBtn = DialogueTreeEditor.smallButton("+Condition", PHOSPHOR);
        JButton editCondBtn = DialogueTreeEditor.smallButton("Edit", ACCENT);
        JButton delCondBtn = DialogueTreeEditor.smallButton("Delete", DANGER);
        addCondBtn.addActionListener(e -> addCondition());
        editCondBtn.addActionListener(e -> editSelectedCondition());
        delCondBtn.addActionListener(e -> deleteSelectedCondition());
        condBtns.add(addCondBtn);
        condBtns.add(editCondBtn);
        condBtns.add(delCondBtn);
        condPanel.add(condBtns, BorderLayout.SOUTH);

        gf.gridy = 2; gf.fill = GridBagConstraints.BOTH; gf.weighty = 1.0;
        form.add(condPanel, gf);

        // Back + Save
        JPanel btns = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        btns.setBackground(PANEL_BG);
        JButton backBtn = DialogueTreeEditor.bigButton(
            "\u25c0 Back to Node", TEXT_BRIGHT, new Color(20, 25, 35));
        JButton saveBtn = DialogueTreeEditor.bigButton(
            "Save Choice", AMBER, new Color(40, 25, 0));
        backBtn.addActionListener(e -> callback.navigateToCurrentNode());
        saveBtn.addActionListener(e -> saveCurrentChoice());
        btns.add(backBtn);
        btns.add(saveBtn);

        JPanel center = new JPanel(new BorderLayout(0, 8));
        center.setBackground(PANEL_BG);
        center.add(form, BorderLayout.CENTER);
        center.add(btns, BorderLayout.SOUTH);
        add(center, BorderLayout.CENTER);
    }

    // ── Load ────────────────────────────────────────────────────────────────

    void loadChoice(DialogueChoice choice, int index, DialogueTree editTree) {
        currentChoice = choice;
        currentChoiceIndex = index;
        choiceLabelField.setText(choice.getLabel() != null ? choice.getLabel() : "");

        refreshNextNodeCombo(editTree);
        if (choice.getNextNodeId() != null) {
            nextNodeCombo.setSelectedItem(choice.getNextNodeId());
        } else {
            nextNodeCombo.setSelectedIndex(0);
        }

        conditionsListModel.clear();
        if (choice.getConditions() != null) {
            for (DialogueCondition c : choice.getConditions()) {
                conditionsListModel.addElement(DialogueTreeEditor.formatCondition(c));
            }
        }
    }

    /** Re-populates the conditions list from the current choice. */
    void reloadConditions() {
        if (currentChoice == null) return;
        conditionsListModel.clear();
        if (currentChoice.getConditions() != null) {
            for (DialogueCondition c : currentChoice.getConditions()) {
                conditionsListModel.addElement(DialogueTreeEditor.formatCondition(c));
            }
        }
    }

    DialogueChoice getCurrentChoice() { return currentChoice; }
    int getCurrentChoiceIndex() { return currentChoiceIndex; }

    // ── Save ────────────────────────────────────────────────────────────────

    private void saveCurrentChoice() {
        if (currentChoice == null) return;

        currentChoice.setLabel(choiceLabelField.getText().trim());
        String sel = (String) nextNodeCombo.getSelectedItem();
        currentChoice.setNextNodeId(
            sel != null && !sel.startsWith("(End") ? sel : null);

        callback.onChoiceSaved();
        callback.setStatus("Choice saved");
    }

    // ── Condition CRUD ──────────────────────────────────────────────────────

    private void addCondition() {
        if (currentChoice == null) return;
        if (currentChoice.getConditions() == null)
            currentChoice.setConditions(new ArrayList<>());

        DialogueCondition cond = new DialogueCondition(
            DialogueCondition.Type.FLAG_SET, "flag_name", null, 0, false);
        currentChoice.getConditions().add(cond);
        reloadConditions();
    }

    private void editSelectedCondition() {
        int idx = conditionsList.getSelectedIndex();
        if (idx < 0 || currentChoice == null
            || currentChoice.getConditions() == null
            || idx >= currentChoice.getConditions().size()) return;

        callback.showConditionEditor(currentChoice.getConditions().get(idx));
    }

    private void deleteSelectedCondition() {
        int idx = conditionsList.getSelectedIndex();
        if (idx < 0 || currentChoice == null
            || currentChoice.getConditions() == null) return;
        currentChoice.getConditions().remove(idx);
        reloadConditions();
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private void refreshNextNodeCombo(DialogueTree editTree) {
        String current = (String) nextNodeCombo.getSelectedItem();
        nextNodeCombo.removeAllItems();
        nextNodeCombo.addItem("(End Conversation)");
        if (editTree != null && editTree.getNodes() != null) {
            for (String id : editTree.getNodes().keySet()) {
                nextNodeCombo.addItem(id);
            }
        }
        if (current != null) nextNodeCombo.setSelectedItem(current);
    }

    private static JLabel sectionLabel(String txt) {
        JLabel l = new JLabel(txt);
        l.setForeground(ACCENT);
        l.setFont(new Font("Monospaced", Font.BOLD, 10));
        return l;
    }
}

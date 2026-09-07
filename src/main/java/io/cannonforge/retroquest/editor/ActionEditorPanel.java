package io.cannonforge.retroquest.editor;

import static io.cannonforge.retroquest.editor.EditorTheme.ACCENT;
import static io.cannonforge.retroquest.editor.EditorTheme.AMBER;
import static io.cannonforge.retroquest.editor.EditorTheme.MONO_SM;
import static io.cannonforge.retroquest.editor.EditorTheme.PANEL_BG;
import static io.cannonforge.retroquest.editor.EditorTheme.PHOSPHOR2;
import static io.cannonforge.retroquest.editor.EditorTheme.TEXT_BRIGHT;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;

import io.cannonforge.retroquest.dialogue.DialogueAction;

/**
 * Sub-panel for editing a single {@link DialogueAction}.
 * Manages its own fields and dynamic visibility logic.
 */
@SuppressWarnings("serial")
class ActionEditorPanel extends JPanel {

    /** Callback for communicating with the parent editor. */
    interface Callback {
        void onActionSaved();
        void navigateToCurrentNode();
        void setStatus(String msg);
        String[] buildQuestComboItems();
        String[] buildItemComboItems();
    }

    private final Callback callback;

    // ── Fields ──────────────────────────────────────────────────────────────
    private JComboBox<DialogueAction.Type> actionTypeCombo;
    private JTextField actionTargetField;
    private JTextField actionValueField;
    private JSpinner actionAmountSpinner;
    private JPanel actionFieldsPanel;
    private JLabel actionTargetLabel, actionValueLabel, actionAmountLabel;

    private JComboBox<String> actionTargetCombo;
    private JComboBox<String> actionSpellCombo;
    private JComboBox<String> actionLogTypeCombo;

    private DialogueAction currentAction;

    ActionEditorPanel(Callback callback) {
        super(new BorderLayout(0, 8));
        this.callback = callback;
        setBackground(PANEL_BG);
        setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));
        buildUI();
    }

    private void buildUI() {
        JLabel hdr = DialogueTreeEditor.makeLabel("\u25c8  ACTION EDITOR", PHOSPHOR2);
        hdr.setFont(EditorTheme.MONO_MD);
        add(hdr, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(PANEL_BG);
        GridBagConstraints gl = new GridBagConstraints();
        gl.gridx = 0; gl.anchor = GridBagConstraints.WEST;
        gl.insets = new Insets(6, 0, 6, 14);
        GridBagConstraints gf = new GridBagConstraints();
        gf.gridx = 1; gf.fill = GridBagConstraints.HORIZONTAL;
        gf.weightx = 1.0; gf.insets = new Insets(6, 0, 6, 0);

        actionTypeCombo = new JComboBox<>(DialogueAction.Type.values());
        actionTypeCombo.setBackground(PANEL_BG);
        actionTypeCombo.setForeground(TEXT_BRIGHT);
        actionTypeCombo.setFont(MONO_SM);
        actionTypeCombo.addActionListener(e -> updateActionFieldVisibility());

        actionTargetField = DialogueTreeEditor.darkField(25);
        actionValueField = DialogueTreeEditor.darkField(25);
        actionAmountSpinner = new JSpinner(new SpinnerNumberModel(0, -9999, 9999, 1));
        actionAmountSpinner.setFont(MONO_SM);

        actionTargetLabel = sectionLabel("TARGET:");
        actionValueLabel = sectionLabel("VALUE:");
        actionAmountLabel = sectionLabel("AMOUNT:");

        actionTargetCombo = new JComboBox<>(new String[]{""});
        actionTargetCombo.setBackground(PANEL_BG);
        actionTargetCombo.setForeground(TEXT_BRIGHT);
        actionTargetCombo.setFont(MONO_SM);
        actionTargetCombo.setVisible(false);

        actionSpellCombo = new JComboBox<>(DialogueTreeEditor.buildSpellList());
        actionSpellCombo.setBackground(PANEL_BG);
        actionSpellCombo.setForeground(TEXT_BRIGHT);
        actionSpellCombo.setFont(MONO_SM);
        actionSpellCombo.setVisible(false);

        actionLogTypeCombo = new JComboBox<>(new String[]{
            "INFO", "GOOD", "LOOT", "DANGER", "DIM", "NORMAL", "SYSTEM"});
        actionLogTypeCombo.setBackground(PANEL_BG);
        actionLogTypeCombo.setForeground(TEXT_BRIGHT);
        actionLogTypeCombo.setFont(MONO_SM);
        actionLogTypeCombo.setVisible(false);

        gl.gridy = gf.gridy = 0;
        form.add(sectionLabel("TYPE:"), gl);
        form.add(actionTypeCombo, gf);

        gl.gridy = gf.gridy = 1;
        form.add(actionTargetLabel, gl);
        form.add(actionTargetField, gf);

        gl.gridy = gf.gridy = 1;
        form.add(actionTargetCombo, gf);

        gl.gridy = gf.gridy = 1;
        form.add(actionSpellCombo, gf);

        gl.gridy = gf.gridy = 2;
        form.add(actionValueLabel, gl);
        form.add(actionValueField, gf);

        gl.gridy = gf.gridy = 2;
        form.add(actionLogTypeCombo, gf);

        gl.gridy = gf.gridy = 3;
        form.add(actionAmountLabel, gl);
        form.add(actionAmountSpinner, gf);

        actionFieldsPanel = form;

        // Back + Save
        JPanel btns = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        btns.setBackground(PANEL_BG);
        JButton backBtn = DialogueTreeEditor.bigButton(
            "\u25c0 Back to Node", TEXT_BRIGHT, new Color(20, 25, 35));
        JButton saveBtn = DialogueTreeEditor.bigButton(
            "Save Action", AMBER, new Color(40, 25, 0));
        backBtn.addActionListener(e -> callback.navigateToCurrentNode());
        saveBtn.addActionListener(e -> saveCurrentAction());
        btns.add(backBtn);
        btns.add(saveBtn);

        JPanel center = new JPanel(new BorderLayout(0, 8));
        center.setBackground(PANEL_BG);
        center.add(form, BorderLayout.NORTH);
        center.add(btns, BorderLayout.SOUTH);
        add(center, BorderLayout.CENTER);
    }

    // ── Load ────────────────────────────────────────────────────────────────

    void loadAction(DialogueAction action) {
        currentAction = action;
        actionTypeCombo.setSelectedItem(action.getType());
        actionTargetField.setText(action.getTarget() != null ? action.getTarget() : "");
        actionValueField.setText(action.getValue() != null ? action.getValue() : "");
        actionAmountSpinner.setValue(action.getAmount());
        updateActionFieldVisibility();
        DialogueTreeEditor.selectComboById(actionTargetCombo, action.getTarget());
        if (actionSpellCombo.isVisible() && action.getTarget() != null)
            actionSpellCombo.setSelectedItem(action.getTarget());
        if (actionLogTypeCombo.isVisible())
            actionLogTypeCombo.setSelectedItem(
                action.getValue() != null ? action.getValue() : "INFO");
    }

    // ── Save ────────────────────────────────────────────────────────────────

    private void saveCurrentAction() {
        if (currentAction == null) return;

        currentAction.setType((DialogueAction.Type) actionTypeCombo.getSelectedItem());
        if (actionTargetCombo.isVisible()) {
            currentAction.setTarget(
                DialogueTreeEditor.extractId((String) actionTargetCombo.getSelectedItem()));
        } else if (actionSpellCombo.isVisible()) {
            currentAction.setTarget((String) actionSpellCombo.getSelectedItem());
        } else {
            currentAction.setTarget(actionTargetField.getText().trim());
        }
        if (actionLogTypeCombo.isVisible()) {
            currentAction.setValue((String) actionLogTypeCombo.getSelectedItem());
        } else {
            currentAction.setValue(actionValueField.getText().trim());
        }
        currentAction.setAmount((int) actionAmountSpinner.getValue());

        callback.onActionSaved();
        callback.setStatus("Action saved");
    }

    // ── Dynamic field visibility ────────────────────────────────────────────

    private void updateActionFieldVisibility() {
        DialogueAction.Type t = (DialogueAction.Type) actionTypeCombo.getSelectedItem();
        if (t == null) return;
        boolean showTarget = true, showValue = false, showAmount = false;
        String targetHint = "TARGET:";
        switch (t) {
            case GIVE_QUEST -> targetHint = "QUEST ID:";
            case COMPLETE_QUEST -> targetHint = "QUEST ID:";
            case GIVE_ITEM, TAKE_ITEM -> { targetHint = "ITEM ID:"; showAmount = true; }
            case GIVE_GOLD -> { showTarget = false; showAmount = true; }
            case SET_FLAG -> { targetHint = "FLAG KEY:"; showValue = true; }
            case CLEAR_FLAG -> targetHint = "FLAG KEY:";
            case HEAL_PLAYER -> { showTarget = false; showAmount = true; }
            case TEACH_SPELL -> targetHint = "SPELL NAME:";
            case LOG_MESSAGE -> { targetHint = "MESSAGE:"; showValue = true; }
            case OPEN_SHOP, OPEN_CASINO, OPEN_ARENA, CLOSE_DIALOGUE, SERVE_DRINK, OPEN_DEPTHS, OPEN_SKY_GAMES -> showTarget = false;
        }
        actionTargetLabel.setText(targetHint);
        actionTargetLabel.setVisible(showTarget);
        actionValueLabel.setVisible(showValue);
        actionAmountLabel.setVisible(showAmount);
        actionAmountSpinner.setVisible(showAmount);
        // Show combo for registry-backed IDs; text field for others
        boolean useQuestCombo = (t == DialogueAction.Type.GIVE_QUEST
            || t == DialogueAction.Type.COMPLETE_QUEST);
        boolean useItemCombo = (t == DialogueAction.Type.GIVE_ITEM
            || t == DialogueAction.Type.TAKE_ITEM);
        boolean useSpellCombo = (t == DialogueAction.Type.TEACH_SPELL);
        if (useQuestCombo) {
            DialogueTreeEditor.refreshCombo(actionTargetCombo,
                callback.buildQuestComboItems());
            actionTargetField.setVisible(false);
            actionTargetCombo.setVisible(showTarget);
            actionSpellCombo.setVisible(false);
        } else if (useItemCombo) {
            DialogueTreeEditor.refreshCombo(actionTargetCombo,
                callback.buildItemComboItems());
            actionTargetField.setVisible(false);
            actionTargetCombo.setVisible(showTarget);
            actionSpellCombo.setVisible(false);
        } else if (useSpellCombo) {
            actionTargetField.setVisible(false);
            actionTargetCombo.setVisible(false);
            actionSpellCombo.setVisible(showTarget);
        } else {
            actionTargetField.setVisible(showTarget);
            actionTargetCombo.setVisible(false);
            actionSpellCombo.setVisible(false);
        }
        // LOG_MESSAGE uses a type combo for value instead of free text
        boolean useLogTypeCombo = (t == DialogueAction.Type.LOG_MESSAGE);
        actionValueField.setVisible(showValue && !useLogTypeCombo);
        actionLogTypeCombo.setVisible(showValue && useLogTypeCombo);
        actionFieldsPanel.revalidate();
    }

    // ── Local helper ────────────────────────────────────────────────────────

    private static JLabel sectionLabel(String txt) {
        JLabel l = new JLabel(txt);
        l.setForeground(ACCENT);
        l.setFont(new Font("Monospaced", Font.BOLD, 10));
        return l;
    }
}

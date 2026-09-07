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
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;

import io.cannonforge.retroquest.dialogue.DialogueCondition;

/**
 * Sub-panel for editing a single {@link DialogueCondition}.
 * Manages its own fields and dynamic visibility logic.
 */
@SuppressWarnings("serial")
class ConditionEditorPanel extends JPanel {

    /** Callback for communicating with the parent editor. */
    interface Callback {
        void onConditionSaved();
        void navigateBackToChoice();
        void setStatus(String msg);
        String[] buildQuestComboItems();
        String[] buildItemComboItems();
    }

    private final Callback callback;

    // ── Fields ──────────────────────────────────────────────────────────────
    private JComboBox<DialogueCondition.Type> condTypeCombo;
    private JTextField condTargetField;
    private JTextField condValueField;
    private JSpinner condAmountSpinner;
    private JCheckBox condNegateCheck;
    private JPanel condFieldsPanel;
    private JLabel condTargetLabel, condValueLabel, condAmountLabel;

    private JComboBox<String> condTargetCombo;
    private JComboBox<String> condStatCombo;

    private DialogueCondition currentCondition;

    ConditionEditorPanel(Callback callback) {
        super(new BorderLayout(0, 8));
        this.callback = callback;
        setBackground(PANEL_BG);
        setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));
        buildUI();
    }

    private void buildUI() {
        JLabel hdr = DialogueTreeEditor.makeLabel("\u25c8  CONDITION EDITOR", PHOSPHOR2);
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

        condTypeCombo = new JComboBox<>(DialogueCondition.Type.values());
        condTypeCombo.setBackground(PANEL_BG);
        condTypeCombo.setForeground(TEXT_BRIGHT);
        condTypeCombo.setFont(MONO_SM);
        condTypeCombo.addActionListener(e -> updateCondFieldVisibility());

        condTargetField = DialogueTreeEditor.darkField(25);
        condValueField = DialogueTreeEditor.darkField(25);
        condAmountSpinner = new JSpinner(new SpinnerNumberModel(0, -9999, 9999, 1));
        condAmountSpinner.setFont(MONO_SM);
        condNegateCheck = new JCheckBox("Negate (invert result)");
        condNegateCheck.setBackground(PANEL_BG);
        condNegateCheck.setForeground(TEXT_BRIGHT);
        condNegateCheck.setFont(MONO_SM);
        condNegateCheck.setFocusPainted(false);

        condTargetLabel = sectionLabel("TARGET:");
        condValueLabel = sectionLabel("VALUE:");
        condAmountLabel = sectionLabel("AMOUNT:");

        condTargetCombo = new JComboBox<>(new String[]{""});
        condTargetCombo.setBackground(PANEL_BG);
        condTargetCombo.setForeground(TEXT_BRIGHT);
        condTargetCombo.setFont(MONO_SM);
        condTargetCombo.setVisible(false);

        condStatCombo = new JComboBox<>(new String[]{
            "str", "dex", "con", "int", "wis", "cha"});
        condStatCombo.setBackground(PANEL_BG);
        condStatCombo.setForeground(TEXT_BRIGHT);
        condStatCombo.setFont(MONO_SM);
        condStatCombo.setVisible(false);

        gl.gridy = gf.gridy = 0;
        form.add(sectionLabel("TYPE:"), gl);
        form.add(condTypeCombo, gf);

        gl.gridy = gf.gridy = 1;
        form.add(condTargetLabel, gl);
        form.add(condTargetField, gf);

        gl.gridy = gf.gridy = 1;
        form.add(condTargetCombo, gf);

        gl.gridy = gf.gridy = 1;
        form.add(condStatCombo, gf);

        gl.gridy = gf.gridy = 2;
        form.add(condValueLabel, gl);
        form.add(condValueField, gf);

        gl.gridy = gf.gridy = 3;
        form.add(condAmountLabel, gl);
        form.add(condAmountSpinner, gf);

        gl.gridy = gf.gridy = 4;
        gf.gridwidth = 2;
        form.add(condNegateCheck, gf);

        condFieldsPanel = form;

        // Back + Save
        JPanel btns = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        btns.setBackground(PANEL_BG);
        JButton backBtn = DialogueTreeEditor.bigButton(
            "\u25c0 Back to Choice", TEXT_BRIGHT, new Color(20, 25, 35));
        JButton saveBtn = DialogueTreeEditor.bigButton(
            "Save Condition", AMBER, new Color(40, 25, 0));
        backBtn.addActionListener(e -> callback.navigateBackToChoice());
        saveBtn.addActionListener(e -> saveCurrentCondition());
        btns.add(backBtn);
        btns.add(saveBtn);

        JPanel center = new JPanel(new BorderLayout(0, 8));
        center.setBackground(PANEL_BG);
        center.add(form, BorderLayout.NORTH);
        center.add(btns, BorderLayout.SOUTH);
        add(center, BorderLayout.CENTER);
    }

    // ── Load ────────────────────────────────────────────────────────────────

    void loadCondition(DialogueCondition cond) {
        currentCondition = cond;
        condTypeCombo.setSelectedItem(cond.getType());
        condTargetField.setText(cond.getTarget() != null ? cond.getTarget() : "");
        condValueField.setText(cond.getValue() != null ? cond.getValue() : "");
        condAmountSpinner.setValue(cond.getAmount());
        condNegateCheck.setSelected(cond.isNegate());
        updateCondFieldVisibility();
        DialogueTreeEditor.selectComboById(condTargetCombo, cond.getTarget());
        if (condStatCombo.isVisible() && cond.getTarget() != null)
            condStatCombo.setSelectedItem(cond.getTarget());
    }

    // ── Save ────────────────────────────────────────────────────────────────

    private void saveCurrentCondition() {
        if (currentCondition == null) return;

        currentCondition.setType(
            (DialogueCondition.Type) condTypeCombo.getSelectedItem());
        if (condTargetCombo.isVisible()) {
            currentCondition.setTarget(
                DialogueTreeEditor.extractId(
                    (String) condTargetCombo.getSelectedItem()));
        } else if (condStatCombo.isVisible()) {
            currentCondition.setTarget((String) condStatCombo.getSelectedItem());
        } else {
            currentCondition.setTarget(condTargetField.getText().trim());
        }
        currentCondition.setValue(condValueField.getText().trim());
        currentCondition.setAmount((int) condAmountSpinner.getValue());
        currentCondition.setNegate(condNegateCheck.isSelected());

        callback.onConditionSaved();
        callback.setStatus("Condition saved");
    }

    // ── Dynamic field visibility ────────────────────────────────────────────

    private void updateCondFieldVisibility() {
        DialogueCondition.Type t =
            (DialogueCondition.Type) condTypeCombo.getSelectedItem();
        if (t == null) return;
        boolean showTarget = true, showValue = false, showAmount = false;
        String targetHint = "TARGET:";
        switch (t) {
            case QUEST_COMPLETE, QUEST_ACTIVE -> targetHint = "QUEST ID:";
            case HAS_ITEM -> { targetHint = "ITEM ID:"; showAmount = true; }
            case HAS_GOLD, PLAYER_LEVEL -> { showTarget = false; showAmount = true; }
            case PLAYER_STAT -> {
                targetHint = "STAT (str/dex/...):"; showAmount = true;
            }
            case FLAG_EQUALS -> { targetHint = "FLAG KEY:"; showValue = true; }
            case FLAG_SET, FLAG_NOT_SET -> targetHint = "FLAG KEY:";
        }
        condTargetLabel.setText(targetHint);
        condTargetLabel.setVisible(showTarget);
        condValueLabel.setVisible(showValue);
        condValueField.setVisible(showValue);
        condAmountLabel.setVisible(showAmount);
        condAmountSpinner.setVisible(showAmount);
        // Show combo for registry-backed IDs; text field for others
        boolean useQuestCombo = (t == DialogueCondition.Type.QUEST_COMPLETE
            || t == DialogueCondition.Type.QUEST_ACTIVE);
        boolean useItemCombo = (t == DialogueCondition.Type.HAS_ITEM);
        boolean useStatCombo = (t == DialogueCondition.Type.PLAYER_STAT);
        if (useQuestCombo) {
            DialogueTreeEditor.refreshCombo(condTargetCombo,
                callback.buildQuestComboItems());
            condTargetField.setVisible(false);
            condTargetCombo.setVisible(showTarget);
            condStatCombo.setVisible(false);
        } else if (useItemCombo) {
            DialogueTreeEditor.refreshCombo(condTargetCombo,
                callback.buildItemComboItems());
            condTargetField.setVisible(false);
            condTargetCombo.setVisible(showTarget);
            condStatCombo.setVisible(false);
        } else if (useStatCombo) {
            condTargetField.setVisible(false);
            condTargetCombo.setVisible(false);
            condStatCombo.setVisible(showTarget);
        } else {
            condTargetField.setVisible(showTarget);
            condTargetCombo.setVisible(false);
            condStatCombo.setVisible(false);
        }
        condFieldsPanel.revalidate();
    }

    // ── Local helper ────────────────────────────────────────────────────────

    private static JLabel sectionLabel(String txt) {
        JLabel l = new JLabel(txt);
        l.setForeground(ACCENT);
        l.setFont(new Font("Monospaced", Font.BOLD, 10));
        return l;
    }
}

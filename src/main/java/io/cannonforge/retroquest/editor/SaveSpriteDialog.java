package io.cannonforge.retroquest.editor;
import java.awt.BorderLayout;
import java.awt.GridLayout;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;

@SuppressWarnings("serial")
public class SaveSpriteDialog extends JDialog {

    public record SaveResult(String name, String category) {}

    public SaveSpriteDialog(ImageEditor parent, String suggestedName, String suggestedCategory) {
        super(parent, "Save Sprite As...", true);
        setSize(420, 180);
        setLocationRelativeTo(parent);

        JPanel panel = new JPanel(new GridLayout(0, 2, 10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        JTextField nameField = new JTextField(suggestedName != null ? suggestedName.replace(".png", "") : "new_sprite");
        JComboBox<String> categoryCombo = new JComboBox<>(new String[]{
                "overworld", "town", "dungeon", "monsters", "npcs", "special", "custom"
        });
        if (suggestedCategory != null) categoryCombo.setSelectedItem(suggestedCategory);

        panel.add(new JLabel("Filename (no .png):"));
        panel.add(nameField);
        panel.add(new JLabel("Category / Folder:"));
        panel.add(categoryCombo);

        JButton okBtn = new JButton("Save");
        JButton cancelBtn = new JButton("Cancel");

        JPanel btnPanel = new JPanel();
        btnPanel.add(okBtn);
        btnPanel.add(cancelBtn);

        add(panel, BorderLayout.CENTER);
        add(btnPanel, BorderLayout.SOUTH);

        okBtn.addActionListener(e -> {
            String name = nameField.getText().trim();
            if (name.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Filename cannot be empty.");
                return;
            }
            dispose();
            parent.finishSave(new SaveResult(name, (String) categoryCombo.getSelectedItem()));
        });

        cancelBtn.addActionListener(e -> dispose());

        setVisible(true);
    }
}

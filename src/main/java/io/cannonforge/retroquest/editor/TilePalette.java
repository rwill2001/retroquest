package io.cannonforge.retroquest.editor;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.util.Comparator;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

import io.cannonforge.retroquest.model.MapData;
import io.cannonforge.retroquest.model.TileDefinition;
import io.cannonforge.retroquest.registry.ImageAssetRegistry;
import io.cannonforge.retroquest.registry.TileRegistry;

@SuppressWarnings("serial")
public class TilePalette extends JPanel {

    private static final int ICON_SIZE = 24;

    private final RetroForge editor;
    private final JPanel buttonPanel;

    public TilePalette(RetroForge editor) {
        this.editor = editor;

        setBackground(new Color(18, 18, 38));
        setBorder(BorderFactory.createEmptyBorder(10, 8, 10, 8));
        setLayout(new BorderLayout());

        // 2 columns — good for readable horizontal buttons showing names
        buttonPanel = new JPanel(new GridLayout(0, 2, 6, 6));
        buttonPanel.setBackground(new Color(18, 18, 38));

        buildButtons(MapData.MapType.OVERWORLD);
        add(buttonPanel, BorderLayout.NORTH);
    }

    public void refresh(MapData.MapType type) {
        buildButtons(type);
        revalidate();
        repaint();
    }

    private void buildButtons(MapData.MapType type) {
        buttonPanel.removeAll();

        List<TileDefinition> tiles;
        if (type == MapData.MapType.OVERWORLD) {
            tiles = TileRegistry.getTilesByCategory("Overworld", "Special", "Custom");
        } else if (type == MapData.MapType.DUNGEON) {
            tiles = TileRegistry.getTilesByCategory("Town", "Dungeon", "Special", "Custom");
        } else {
            tiles = TileRegistry.getTilesByCategory("Town", "Special", "Custom");
        }

        tiles.sort(Comparator.comparing(TileDefinition::getName, String.CASE_INSENSITIVE_ORDER));

        for (TileDefinition t : tiles) {
            buttonPanel.add(createTileButton(t));
        }
    }

    private JButton createTileButton(TileDefinition tile) {
        // Custom button with name on left, sprite preview on right
        JButton b = new JButton() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Image sprite = ImageAssetRegistry.get(tile.getSpriteKey());
                if (sprite != null) {
                    Graphics2D g2 = (Graphics2D) g;
                    g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                            RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                    int x = getWidth() - ICON_SIZE - 4;
                    int y = (getHeight() - ICON_SIZE) / 2;
                    g2.drawImage(sprite, x, y, ICON_SIZE, ICON_SIZE, null);
                    // Thin border around the sprite
                    g2.setColor(new Color(255, 255, 255, 50));
                    g2.drawRect(x, y, ICON_SIZE, ICON_SIZE);
                }
            }
        };

        String displayName = tile.getName() != null ? tile.getName() : "?";
        if (displayName.length() > 16) {
            displayName = displayName.substring(0, 14) + "..";
        }

        b.setText(displayName);
        b.setHorizontalAlignment(SwingConstants.LEFT);

        b.setBackground(tile.getEditorColor());
        b.setForeground(Color.WHITE);
        b.setFont(new Font("Monospaced", Font.BOLD, 11));
        b.setToolTipText(tile.getName() + "   [" + tile.getId() + "]");

        b.setPreferredSize(new Dimension(112, 34));
        b.setMinimumSize(new Dimension(112, 34));
        b.setMaximumSize(new Dimension(112, 34));

        b.setMargin(new Insets(2, 6, 2, ICON_SIZE + 8));
        b.setFocusPainted(false);
        b.setBorder(BorderFactory.createEmptyBorder(3, 6, 3, ICON_SIZE + 8));

        b.addActionListener(e -> {
            getCanvas().setBrush(tile.getId());
            editor.refreshAll();
        });

        return b;
    }

    private MapCanvas getCanvas() {
        return editor.getCanvas();
    }
}

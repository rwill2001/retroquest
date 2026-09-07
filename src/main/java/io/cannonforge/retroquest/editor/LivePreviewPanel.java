package io.cannonforge.retroquest.editor;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import io.cannonforge.retroquest.model.MapData;
import io.cannonforge.retroquest.model.NPC;
import io.cannonforge.retroquest.model.TileDefinition;
import io.cannonforge.retroquest.registry.TileRegistry;

@SuppressWarnings("serial")
public class LivePreviewPanel extends JPanel {

    private final RetroForge editor;
    private BufferedImage cachedPreview;

    public LivePreviewPanel(RetroForge editor) {
        this.editor = editor;
        setPreferredSize(new Dimension(245, 300));
        setMinimumSize(new Dimension(245, 280));
        setMaximumSize(new Dimension(245, 340));
        setBackground(Color.BLACK);
        setBorder(BorderFactory.createTitledBorder("LIVE PREVIEW — Click to Center"));

        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                refresh();
            }
        });

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                MapData map = editor.getCurrentMap();
                if (map == null) return;

                int pw = getWidth();
                int ph = getHeight();
                double scale = getFitScale(pw, ph, map);
                int drawnWidth = (int) (map.width * scale);
                int drawnHeight = (int) (map.height * scale);

                int offsetX = (pw - drawnWidth) / 2;
                int offsetY = (ph - drawnHeight) / 2;

                int clickInMapX = e.getX() - offsetX;
                int clickInMapY = e.getY() - offsetY;

                if (clickInMapX < 0 || clickInMapY < 0 ||
                    clickInMapX >= drawnWidth || clickInMapY >= drawnHeight) return;

                int worldX = (int) (clickInMapX / scale);
                int worldY = (int) (clickInMapY / scale);

                editor.centerViewOn(worldX, worldY);
            }
        });
    }

    private double getFitScale(int pw, int ph, MapData m) {
        if (m.width == 0 || m.height == 0) return 1.0;
        return Math.min((double) pw / m.width, (double) ph / m.height);
    }

    @Override
    public void addNotify() {
        super.addNotify();
        SwingUtilities.invokeLater(this::refresh);
    }

    public void refresh() {
        cachedPreview = null;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;

        MapData m = editor.getCurrentMap();
        if (m == null) return;

        if (cachedPreview == null) {
            cachedPreview = buildPreviewImage(m);
        }

        double scale = getFitScale(getWidth(), getHeight(), m);
        int drawnWidth = (int) (m.width * scale);
        int drawnHeight = (int) (m.height * scale);

        int x = (getWidth() - drawnWidth) / 2;
        int y = (getHeight() - drawnHeight) / 2;
        g2.drawImage(cachedPreview, x, y, null);

        // === VIEWPORT BOX (always drawn fresh) ===
        MapCanvas canvas = editor.getCanvas();
        if (canvas != null) {
            int camX = canvas.getCameraX();
            int camY = canvas.getCameraY();

            int boxX = x + (int) (camX * scale);
            int boxY = y + (int) (camY * scale);
            int viewW = canvas.getWidth() / canvas.getTileSize() + 1;
            int viewH = canvas.getHeight() / canvas.getTileSize() + 1;
            int boxW = (int) (viewW * scale);
            int boxH = (int) (viewH * scale);

            if (boxW > 3 && boxH > 3) {
                g2.setColor(new Color(255, 255, 0, 55));
                g2.fillRect(boxX, boxY, boxW, boxH);

                g2.setColor(new Color(255, 220, 0));
                g2.setStroke(new java.awt.BasicStroke(2.5f));
                g2.drawRect(boxX, boxY, boxW, boxH);

                g2.setFont(new java.awt.Font("Monospaced", java.awt.Font.BOLD, 10));
                g2.setColor(Color.BLACK);
                g2.drawString("VIEW", boxX + 5, boxY + 13);
                g2.setColor(new Color(255, 240, 100));
                g2.drawString("VIEW", boxX + 4, boxY + 12);
            }
        }
    }

    private BufferedImage buildPreviewImage(MapData m) {
        int pw = getWidth();
        int ph = getHeight();
        double scale = getFitScale(pw, ph, m);

        int w = (int) (m.width * scale);
        int h = (int) (m.height * scale);

        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();

        for (int y = 0; y < m.height; y++) {
            for (int x = 0; x < m.width; x++) {
                TileDefinition def = TileRegistry.getByIdSafe(m.tiles[y][x]);
                g.setColor(def.getEditorColor());
                g.fillRect((int) (x * scale), (int) (y * scale), (int) scale + 1, (int) scale + 1);
            }
        }

        for (NPC npc : m.npcs) {
            int nx = (int) (npc.getX() * scale);
            int ny = (int) (npc.getY() * scale);
            g.setColor(Color.YELLOW);
            g.fillOval(nx + (int) (scale / 4), ny + (int) (scale / 4), Math.max(3, (int) (scale / 2)), Math.max(3, (int) (scale / 2)));
        }

        g.dispose();
        return img;
    }
}

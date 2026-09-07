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
import static io.cannonforge.retroquest.editor.EditorTheme.PHOSPHOR3;
import static io.cannonforge.retroquest.editor.EditorTheme.TEXT_BRIGHT;
import static io.cannonforge.retroquest.editor.EditorTheme.TEXT_DIM;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Composite;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.KeyboardFocusManager;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

import io.cannonforge.retroquest.registry.ImageAssetRegistry;

/**
 * RETROQUEST IMAGE EDITOR — Full CRT Makeover
 *
 * Layout:
 *   LEFT   — Sprite Library (scrollable, categorised)
 *   CENTER — Pixel Canvas with zoom + preview
 *   RIGHT  — Tools panel (draw, fill, eyedropper, mirror, undo)
 *   BOTTOM — Transport bar (New, Load, Save, Save As, Clear)
 */
@SuppressWarnings("serial")
public class ImageEditor extends JDialog {

    // ── Canvas state ─────────────────────────────────────────────────────────
    private BufferedImage canvas;
    private int gridSize = 32;
    private Color currentColor = new Color(0, 255, 120); // phosphor green default

    // ── Tools ────────────────────────────────────────────────────────────────
    enum Tool { DRAW, ERASE, FILL, EYEDROPPER, RECT, CIRCLE, LINE, SELECT }
    private Tool currentTool = Tool.DRAW;

    // ── Shape tool state ─────────────────────────────────────────────────────
    private int shapeStartX = -1, shapeStartY = -1;
    private int shapeEndX = -1, shapeEndY = -1;
    private boolean shapeDragging = false;

    // ── Selection / clipboard state ──────────────────────────────────────────
    private int selPxX1 = -1, selPxY1 = -1, selPxX2 = -1, selPxY2 = -1;
    private boolean selDragging = false;
    private BufferedImage pixelClipboard = null;

    // ── Undo stack ───────────────────────────────────────────────────────────
    private final Deque<int[][]> undoStack = new ArrayDeque<>();
    private final Deque<int[][]> redoStack = new ArrayDeque<>();
    private static final int MAX_UNDO = 40;

    // ── File state ───────────────────────────────────────────────────────────
    private String currentSpriteName = null;
    private File   currentSpriteFile = null;
    private String pendingSpriteToLoad = null;
    private boolean unsavedChanges = false;

    // ── Sub-components ────────────────────────────────────────────────────────
    private PixelCanvas  pixelCanvas;
    private PreviewPanel previewPanel;
    private SpriteLibraryPanel spriteLibrary;
    private JLabel statusLabel;
    private JLabel colorSwatch;
    private JComboBox<String> sizeCombo;

    // Tool buttons for toggle highlight
    private final Map<Tool, JButton> toolButtons = new EnumMap<>(Tool.class);

    // ── 256-color palette ─────────────────────────────────────────────────────
    private final Color[] palette256 = new Color[256];

    /** Whether {@link #init()} shows the window. False only for offscreen recording. */
    private boolean showOnInit = true;

    // ── Constructors ──────────────────────────────────────────────────────────
    public ImageEditor() { this(null, true); }

    public ImageEditor(String spriteToLoad) { this(spriteToLoad, true); }

    /**
     * @param show false builds the editor laid out but never displayed — the offscreen
     *        entry point {@code RetroRecorder} uses to film it. This dialog is modal, so
     *        the public constructors block their caller until a person closes the window.
     */
    public ImageEditor(String spriteToLoad, boolean show) {
        super((Frame) null, "RETROQUEST  ·  IMAGE  EDITOR", true);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        this.pendingSpriteToLoad = spriteToLoad;
        this.showOnInit = show;
        init();
    }

    // ── Init ──────────────────────────────────────────────────────────────────
    private void init() {
        generateVGA256Palette();
        getContentPane().setBackground(BG);
        setLayout(new BorderLayout(0, 0));

        spriteLibrary = new SpriteLibraryPanel();
        spriteLibrary.setOnSelect(this::loadSpriteByName);
        pixelCanvas  = new PixelCanvas();
        previewPanel = new PreviewPanel();

        add(buildLibraryPanel(),  BorderLayout.WEST);
        add(buildCenterPanel(),   BorderLayout.CENTER);
        add(buildToolsPanel(),    BorderLayout.EAST);
        add(buildTransportBar(),  BorderLayout.SOUTH);

        // Keyboard shortcuts
        setupKeyBindings();

        setSize(1260, 700);
        setMinimumSize(new Dimension(1000, 580));
        setLocationRelativeTo(null);
        setBackground(BG);

        if (pendingSpriteToLoad != null) {
            autoLoadSprite(pendingSpriteToLoad);
        } else {
            changeSize(32);
        }

        setVisible(showOnInit);
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  LIBRARY PANEL (left)
    // ═════════════════════════════════════════════════════════════════════════
    private JPanel buildLibraryPanel() {
        JPanel outer = darkPanel();
        outer.setPreferredSize(new Dimension(200, 0));
        outer.setLayout(new BorderLayout(0, 0));
        outer.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, BORDER_COL));

        JLabel title = makeLabel("◈  SPRITE  LIBRARY", PHOSPHOR);
        title.setFont(MONO_MD);
        title.setBorder(BorderFactory.createEmptyBorder(12, 12, 10, 8));
        outer.add(title, BorderLayout.NORTH);

        JScrollPane sp = new JScrollPane();
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(BG);
        wrapper.add(spriteLibrary, BorderLayout.NORTH);
        sp.setViewportView(wrapper);
        sp.setBackground(BG);
        sp.getViewport().setBackground(BG);
        sp.setBorder(BorderFactory.createEmptyBorder());
        sp.getVerticalScrollBar().setUI(new RetroScrollBarUI());
        sp.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        outer.add(sp, BorderLayout.CENTER);

        // Refresh button
        JButton refreshBtn = retroButton("⟳  REFRESH");
        refreshBtn.addActionListener(e -> spriteLibrary.refresh());
        refreshBtn.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        outer.add(refreshBtn, BorderLayout.SOUTH);

        return outer;
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  CENTER — Canvas + Preview
    // ═════════════════════════════════════════════════════════════════════════
    private JPanel buildCenterPanel() {
        JPanel p = darkPanel();
        p.setLayout(new BorderLayout(0, 8));
        p.setBorder(BorderFactory.createEmptyBorder(10, 10, 0, 10));

        // ── Title bar ──
        JPanel titleBar = new JPanel(new BorderLayout());
        titleBar.setOpaque(false);
        JLabel lbl = makeLabel("PIXEL  CANVAS", PHOSPHOR);
        lbl.setFont(MONO_LG);
        statusLabel = makeLabel("32×32  |  DRAW", TEXT_DIM);
        statusLabel.setFont(MONO_XS);
        statusLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        titleBar.add(lbl, BorderLayout.WEST);
        titleBar.add(statusLabel, BorderLayout.EAST);

        // ── Canvas controls bar ──
        JPanel ctrlBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        ctrlBar.setOpaque(false);

        sizeCombo = new JComboBox<>(new String[]{"8×8","16×16","32×32","64×64"});
        sizeCombo.setSelectedIndex(2);
        styleCombo(sizeCombo);
        sizeCombo.addActionListener(e -> changeSizeFromCombo());

        ctrlBar.add(makeLabel("SIZE:", TEXT_DIM));
        ctrlBar.add(sizeCombo);

        JPanel top = new JPanel(new BorderLayout(0, 4));
        top.setOpaque(false);
        top.add(titleBar, BorderLayout.NORTH);
        top.add(ctrlBar,  BorderLayout.SOUTH);

        // ── Square canvas — sized to available height, placed WEST so it
        //    doesn't stretch. Remaining CENTER space is used by the info panel.
        JPanel squareContainer = new JPanel(new BorderLayout()) {
            @Override
            public Dimension getPreferredSize() {
                // Size to the available height of the parent so it stays square
                // getParent() here is the content row panel
                int side = getParent() != null ? getParent().getHeight() : 500;
                if (side <= 0) side = 500;
                return new Dimension(side, side);
            }
            @Override public Dimension getMinimumSize() { return getPreferredSize(); }
            @Override public Dimension getMaximumSize() { return getPreferredSize(); }
        };
        squareContainer.setBackground(new Color(5, 8, 12));
        squareContainer.setBorder(BorderFactory.createLineBorder(BORDER_COL));
        squareContainer.add(pixelCanvas, BorderLayout.CENTER);
        pixelCanvas.setSquareContainer(squareContainer);

        // ── Info panel — fills remaining horizontal space to the right of canvas ──
        JPanel infoPanel = buildInfoPanel();

        // ── Row containing canvas (fixed square) + info (fills rest) ──
        JPanel contentRow = new JPanel(new BorderLayout(8, 0));
        contentRow.setOpaque(false);
        contentRow.add(squareContainer, BorderLayout.WEST);
        contentRow.add(infoPanel,       BorderLayout.CENTER);

        // ── Preview strip at bottom ──
        JPanel previewBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 4));
        previewBar.setOpaque(false);
        previewBar.add(makeLabel("PREVIEW:", TEXT_DIM));
        previewBar.add(previewPanel);

        p.add(top,         BorderLayout.NORTH);
        p.add(contentRow,  BorderLayout.CENTER);
        p.add(previewBar,  BorderLayout.SOUTH);
        return p;
    }

    // ── Info panel: shown to the right of the square canvas ─────────────────
    private JPanel buildInfoPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBackground(PANEL_BG);
        p.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, BORDER_COL));

        GridBagConstraints gc = new GridBagConstraints();
        gc.fill    = GridBagConstraints.HORIZONTAL;
        gc.weightx = 1.0;
        gc.gridx   = 0;
        gc.gridy   = 0;
        gc.insets  = new Insets(10, 12, 4, 12);

        p.add(sectionLabel("CANVAS  INFO"), gc); gc.gridy++;

        gc.insets = new Insets(0, 12, 10, 12);
        JPanel infoGrid = new JPanel(new GridLayout(0, 2, 4, 4));
        infoGrid.setOpaque(false);
        infoGrid.add(makeLabel("Size:",     TEXT_DIM));   infoGrid.add(makeLabel(gridSize + "×" + gridSize + " px", TEXT_BRIGHT));
        infoGrid.add(makeLabel("Format:",   TEXT_DIM));   infoGrid.add(makeLabel("PNG / ARGB", TEXT_BRIGHT));
        infoGrid.add(makeLabel("File:",     TEXT_DIM));   infoGrid.add(makeLabel(currentSpriteName != null ? currentSpriteName : "unsaved", TEXT_BRIGHT));
        p.add(infoGrid, gc); gc.gridy++;

        // Vertical filler
        gc.weighty = 1.0;
        gc.fill    = GridBagConstraints.BOTH;
        p.add(Box.createVerticalGlue(), gc);
        return p;
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  TOOLS PANEL (right)
    // ═════════════════════════════════════════════════════════════════════════
    private JPanel buildToolsPanel() {
        JPanel outer = darkPanel();
        outer.setPreferredSize(new Dimension(220, 0));
        outer.setLayout(new GridBagLayout());
        outer.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, BORDER_COL));

        GridBagConstraints gc = new GridBagConstraints();
        gc.fill    = GridBagConstraints.HORIZONTAL;
        gc.weightx = 1.0;
        gc.gridx   = 0;
        gc.gridy   = 0;
        gc.insets  = new Insets(10, 10, 2, 10);

        // ── TOOLS ──
        outer.add(sectionLabel("TOOLS"), gc); gc.gridy++;
        gc.insets = new Insets(0, 10, 8, 10);

        JPanel toolRow1 = new JPanel(new GridLayout(1, 2, 4, 0));
        toolRow1.setOpaque(false);
        JButton drawBtn = toolButton("✏  DRAW",   Tool.DRAW);
        JButton eraseBtn = toolButton("◻  ERASE", Tool.ERASE);
        toolRow1.add(drawBtn);
        toolRow1.add(eraseBtn);
        outer.add(toolRow1, gc); gc.gridy++;

        gc.insets = new Insets(0, 10, 8, 10);
        JPanel toolRow2 = new JPanel(new GridLayout(1, 2, 4, 0));
        toolRow2.setOpaque(false);
        JButton fillBtn = toolButton("⬛  FILL",  Tool.FILL);
        JButton eyeBtn  = toolButton("👁  PICK",  Tool.EYEDROPPER);
        toolRow2.add(fillBtn);
        toolRow2.add(eyeBtn);
        outer.add(toolRow2, gc); gc.gridy++;

        gc.insets = new Insets(0, 10, 8, 10);
        JPanel toolRow3 = new JPanel(new GridLayout(1, 3, 4, 0));
        toolRow3.setOpaque(false);
        JButton rectBtn   = toolButton("▭ RECT",   Tool.RECT);
        JButton circleBtn = toolButton("○ CIRCLE", Tool.CIRCLE);
        JButton lineBtn   = toolButton("╱ LINE",   Tool.LINE);
        toolRow3.add(rectBtn);
        toolRow3.add(circleBtn);
        toolRow3.add(lineBtn);
        outer.add(toolRow3, gc); gc.gridy++;

        gc.insets = new Insets(0, 10, 8, 10);
        JPanel toolRow4 = new JPanel(new GridLayout(1, 1, 4, 0));
        toolRow4.setOpaque(false);
        JButton selectBtn = toolButton("⬚ SELECT", Tool.SELECT);
        toolRow4.add(selectBtn);
        outer.add(toolRow4, gc); gc.gridy++;

        // ── CURRENT COLOR ──
        gc.insets = new Insets(4, 10, 2, 10);
        outer.add(sectionLabel("ACTIVE COLOR"), gc); gc.gridy++;
        gc.insets = new Insets(0, 10, 8, 10);
        colorSwatch = new JLabel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setColor(currentColor);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 4, 4);
                g2.setColor(BORDER_COL);
                g2.drawRoundRect(0, 0, getWidth()-1, getHeight()-1, 4, 4);
                // Show hex value
                g2.setColor(currentColor.getRed() + currentColor.getGreen() + currentColor.getBlue() > 350
                    ? Color.BLACK : Color.WHITE);
                g2.setFont(MONO_XS);
                String hex = String.format("#%02X%02X%02X",
                    currentColor.getRed(), currentColor.getGreen(), currentColor.getBlue());
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(hex, (getWidth()-fm.stringWidth(hex))/2,
                    (getHeight()+fm.getAscent()-fm.getDescent())/2);
            }
        };
        colorSwatch.setPreferredSize(new Dimension(0, 36));
        colorSwatch.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        colorSwatch.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                Color c = JColorChooser.showDialog(ImageEditor.this, "Pick Color", currentColor);
                if (c != null) { currentColor = c; colorSwatch.repaint(); }
            }
        });
        outer.add(colorSwatch, gc); gc.gridy++;

        // ── PALETTE ──
        gc.insets = new Insets(0, 10, 2, 10);
        outer.add(sectionLabel("256 COLOR PALETTE"), gc); gc.gridy++;
        gc.insets = new Insets(0, 10, 8, 10);
        outer.add(buildPalettePanel(), gc); gc.gridy++;

        // ── MIRROR / TRANSFORM ──
        gc.insets = new Insets(0, 10, 2, 10);
        outer.add(sectionLabel("TRANSFORM"), gc); gc.gridy++;
        gc.insets = new Insets(0, 10, 4, 10);

        JPanel mirrorRow = new JPanel(new GridLayout(1, 2, 4, 0));
        mirrorRow.setOpaque(false);
        JButton flipHBtn = retroButton("↔ FLIP H");
        JButton flipVBtn = retroButton("↕ FLIP V");
        flipHBtn.addActionListener(e -> flipHorizontal());
        flipVBtn.addActionListener(e -> flipVertical());
        mirrorRow.add(flipHBtn); mirrorRow.add(flipVBtn);
        outer.add(mirrorRow, gc); gc.gridy++;

        gc.insets = new Insets(0, 10, 8, 10);
        JPanel rotRow = new JPanel(new GridLayout(1, 2, 4, 0));
        rotRow.setOpaque(false);
        JButton rotLBtn = retroButton("↺ ROT L");
        JButton rotRBtn = retroButton("↻ ROT R");
        rotLBtn.addActionListener(e -> rotate(false));
        rotRBtn.addActionListener(e -> rotate(true));
        rotRow.add(rotLBtn); rotRow.add(rotRBtn);
        outer.add(rotRow, gc); gc.gridy++;

        // ── UNDO / REDO ──
        gc.insets = new Insets(0, 10, 2, 10);
        outer.add(sectionLabel("HISTORY  (Ctrl+Z / Ctrl+Y)"), gc); gc.gridy++;
        gc.insets = new Insets(0, 10, 8, 10);
        JPanel undoRow = new JPanel(new GridLayout(1, 2, 4, 0));
        undoRow.setOpaque(false);
        JButton undoBtn = retroButton("↩ UNDO");
        JButton redoBtn = retroButton("↪ REDO");
        undoBtn.addActionListener(e -> undo());
        redoBtn.addActionListener(e -> redo());
        undoRow.add(undoBtn); undoRow.add(redoBtn);
        outer.add(undoRow, gc); gc.gridy++;

        // Vertical filler
        gc.weighty = 1.0;
        gc.fill    = GridBagConstraints.BOTH;
        gc.insets  = new Insets(0,0,0,0);
        outer.add(Box.createVerticalGlue(), gc);

        return outer;
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  TRANSPORT BAR (bottom)
    // ═════════════════════════════════════════════════════════════════════════
    private JPanel buildTransportBar() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(new Color(8, 11, 16));
        p.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER_COL));
        p.setPreferredSize(new Dimension(0, 68));

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
        btnRow.setOpaque(false);

        JButton newBtn    = bigButton("✦  NEW",      PHOSPHOR,  new Color(0, 40, 20));
        JButton loadBtn   = bigButton("📂  LOAD",    PHOSPHOR2, new Color(0, 20, 40));
        JButton saveBtn   = bigButton("💾  SAVE",    AMBER,     new Color(40, 25, 0));
        JButton saveAsBtn = bigButton("⬆  SAVE AS", PHOSPHOR3, new Color(30, 30, 0));
        JButton clearBtn  = bigButton("⬜  CLEAR",   DANGER,    new Color(40, 0, 0));

        newBtn.addActionListener(e    -> newImage());
        loadBtn.addActionListener(e   -> loadImage());
        saveBtn.addActionListener(e   -> saveImage());
        saveAsBtn.addActionListener(e -> saveAsNew());
        clearBtn.addActionListener(e  -> confirmClear());

        btnRow.add(newBtn); btnRow.add(loadBtn); btnRow.add(saveBtn);
        btnRow.add(saveAsBtn); btnRow.add(clearBtn);

        JLabel shortcutHint = makeLabel(
            "  Ctrl+Z undo | Ctrl+Y redo | D draw | E erase | F fill | I eyedrop | R rect | C circle | L line",
            TEXT_DIM);
        shortcutHint.setFont(MONO_XS);
        shortcutHint.setBorder(BorderFactory.createEmptyBorder(0, 8, 4, 8));

        p.add(btnRow,        BorderLayout.NORTH);
        p.add(shortcutHint,  BorderLayout.SOUTH);
        return p;
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  PALETTE PANEL
    // ═════════════════════════════════════════════════════════════════════════
    private JPanel buildPalettePanel() {
        JPanel grid = new JPanel(new GridLayout(16, 16, 1, 1));
        grid.setBackground(new Color(8, 12, 18));
        grid.setBorder(BorderFactory.createLineBorder(BORDER_COL));

        for (int i = 0; i < 256; i++) {
            final Color c = palette256[i];
            JPanel swatch = new JPanel() {
                boolean hover = false;
                { setBackground(c);
                  setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                  addMouseListener(new MouseAdapter() {
                    @Override public void mouseEntered(MouseEvent e) { hover = true;  repaint(); }
                    @Override public void mouseExited(MouseEvent e)  { hover = false; repaint(); }
                    @Override public void mouseClicked(MouseEvent e) {
                        currentColor = c;
                        colorSwatch.repaint();
                        if (currentTool == Tool.ERASE) {
                            currentTool = Tool.DRAW;
                            updateToolHighlight();
                        }
                    }
                  });
                }
                @Override protected void paintComponent(Graphics g) {
                    g.setColor(c);
                    g.fillRect(0, 0, getWidth(), getHeight());
                    if (hover) {
                        g.setColor(new Color(255,255,255,80));
                        g.fillRect(0, 0, getWidth(), getHeight());
                    }
                    if (c.equals(currentColor)) {
                        g.setColor(Color.WHITE);
                        g.drawRect(0, 0, getWidth()-1, getHeight()-1);
                    }
                }
            };
            swatch.setPreferredSize(new Dimension(10, 10));
            swatch.setToolTipText(String.format("#%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue()));
            grid.add(swatch);
        }
        return grid;
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  VGA 256 PALETTE
    // ═════════════════════════════════════════════════════════════════════════
    private void generateVGA256Palette() {
        // First 16: classic CGA/EGA colors
        int[][] cga = {
            {0,0,0},{170,0,0},{0,170,0},{170,170,0},
            {0,0,170},{170,0,170},{0,170,170},{170,170,170},
            {85,85,85},{255,85,85},{85,255,85},{255,255,85},
            {85,85,255},{255,85,255},{85,255,255},{255,255,255}
        };
        for (int i = 0; i < 16; i++)
            palette256[i] = new Color(cga[i][0], cga[i][1], cga[i][2]);

        // 216 web-safe colors (6×6×6 cube)
        int idx = 16;
        for (int r = 0; r < 6; r++)
            for (int g = 0; g < 6; g++)
                for (int b = 0; b < 6; b++)
                    palette256[idx++] = new Color(r==0?0:55+r*40, g==0?0:55+g*40, b==0?0:55+b*40);

        // 24 grayscale ramp
        for (int i = 0; i < 24; i++) {
            int v = 8 + i * 10;
            palette256[idx++] = new Color(v, v, v);
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  CANVAS OPERATIONS
    // ═════════════════════════════════════════════════════════════════════════
    private void changeSizeFromCombo() {
        String sel = (String) sizeCombo.getSelectedItem();
        int s = switch (sel) {
            case "8×8"   ->  8;
            case "16×16" -> 16;
            case "64×64" -> 64;
            default      -> 32;
        };
        if (s == gridSize) return;
        // Resizing rebuilds the canvas and clears undo — the pixels are gone for good.
        if (!confirmDiscardPixels("Resize canvas")) {
            String current = gridSize + "×" + gridSize;
            for (int i = 0; i < sizeCombo.getItemCount(); i++)
                if (sizeCombo.getItemAt(i).equals(current)) sizeCombo.setSelectedIndex(i);
            return;
        }
        changeSize(s);
    }

    /**
     * Asks before an action that throws away unpainted work.
     *
     * @return false if the user chose to keep what is on the canvas
     */
    private boolean confirmDiscardPixels(String action) {
        if (!unsavedChanges) return true;
        return JOptionPane.showConfirmDialog(this,
                "The current sprite has unsaved changes.\n\nDiscard them?",
                action, JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION;
    }

    private void changeSize(int newSize) {
        if (newSize == gridSize && canvas != null) return;
        gridSize = newSize;
        canvas = new BufferedImage(gridSize, gridSize, BufferedImage.TYPE_INT_ARGB);
        undoStack.clear(); redoStack.clear();
        clearCanvas(false);
        updateStatus();
        pixelCanvas.repaint();
        previewPanel.repaint();
    }

    private void newImage() {
        if (unsavedChanges) {
            int r = JOptionPane.showConfirmDialog(this,
                "Discard unsaved changes?", "New Image",
                JOptionPane.YES_NO_OPTION);
            if (r != JOptionPane.YES_OPTION) return;
        }
        currentSpriteFile = null;
        currentSpriteName = null;
        unsavedChanges = false;
        setTitle("RETROQUEST  ·  IMAGE  EDITOR");
        changeSize(32);
        sizeCombo.setSelectedIndex(2);
    }

    private void clearCanvas(boolean pushUndo) {
        if (canvas == null) return;
        if (pushUndo) pushUndo();
        Graphics2D g = canvas.createGraphics();
        g.setComposite(AlphaComposite.Clear);
        g.fillRect(0, 0, gridSize, gridSize);
        g.dispose();
        unsavedChanges = true;
        pixelCanvas.repaint();
        previewPanel.repaint();
    }

    private void confirmClear() {
        int r = JOptionPane.showConfirmDialog(this,
            "Clear the canvas?", "Clear", JOptionPane.YES_NO_OPTION);
        if (r == JOptionPane.YES_OPTION) clearCanvas(true);
    }

    // ── Flood fill (BFS) ──────────────────────────────────────────────────────
    private void floodFill(int startX, int startY, Color fillColor) {
        int targetRGB = canvas.getRGB(startX, startY);
        int fillRGB   = fillColor.getRGB();
        if (targetRGB == fillRGB) return;

        pushUndo();
        Queue<int[]> queue = new LinkedList<>();
        queue.add(new int[]{startX, startY});
        boolean[][] visited = new boolean[gridSize][gridSize];

        while (!queue.isEmpty()) {
            int[] pt = queue.poll();
            int x = pt[0], y = pt[1];
            if (x < 0 || x >= gridSize || y < 0 || y >= gridSize) continue;
            if (visited[x][y] || (canvas.getRGB(x, y) != targetRGB)) continue;
            visited[x][y] = true;
            canvas.setRGB(x, y, fillRGB);
            queue.add(new int[]{x+1, y});
            queue.add(new int[]{x-1, y});
            queue.add(new int[]{x, y+1});
            queue.add(new int[]{x, y-1});
        }
        pixelCanvas.repaint();
        previewPanel.repaint();
    }

    // ── Transforms ───────────────────────────────────────────────────────────
    private void flipHorizontal() {
        pushUndo();
        BufferedImage flipped = new BufferedImage(gridSize, gridSize, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < gridSize; y++)
            for (int x = 0; x < gridSize; x++)
                flipped.setRGB(gridSize - 1 - x, y, canvas.getRGB(x, y));
        copyImage(flipped, canvas);
        pixelCanvas.repaint(); previewPanel.repaint();
    }

    private void flipVertical() {
        pushUndo();
        BufferedImage flipped = new BufferedImage(gridSize, gridSize, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < gridSize; y++)
            for (int x = 0; x < gridSize; x++)
                flipped.setRGB(x, gridSize - 1 - y, canvas.getRGB(x, y));
        copyImage(flipped, canvas);
        pixelCanvas.repaint(); previewPanel.repaint();
    }

    private void rotate(boolean clockwise) {
        pushUndo();
        BufferedImage rotated = new BufferedImage(gridSize, gridSize, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < gridSize; y++)
            for (int x = 0; x < gridSize; x++) {
                int rgb = canvas.getRGB(x, y);
                if (clockwise)
                    rotated.setRGB(gridSize - 1 - y, x, rgb);
                else
                    rotated.setRGB(y, gridSize - 1 - x, rgb);
            }
        copyImage(rotated, canvas);
        pixelCanvas.repaint(); previewPanel.repaint();
    }

    private void copyImage(BufferedImage src, BufferedImage dst) {
        Graphics2D g = dst.createGraphics();
        g.setComposite(AlphaComposite.Src);
        g.drawImage(src, 0, 0, null);
        g.dispose();
    }

    // ── Undo / Redo ───────────────────────────────────────────────────────────
    private void pushUndo() {
        int[][] snapshot = captureCanvas();
        undoStack.push(snapshot);
        if (undoStack.size() > MAX_UNDO) {
            undoStack.removeLast();
        }
        redoStack.clear();
        unsavedChanges = true;
    }

    private void undo() {
        if (undoStack.isEmpty()) return;
        redoStack.push(captureCanvas());
        restoreCanvas(undoStack.pop());
        pixelCanvas.repaint(); previewPanel.repaint();
    }

    private void redo() {
        if (redoStack.isEmpty()) return;
        undoStack.push(captureCanvas());
        restoreCanvas(redoStack.pop());
        pixelCanvas.repaint(); previewPanel.repaint();
    }

    private int[][] captureCanvas() {
        int[][] snap = new int[gridSize][gridSize];
        for (int y = 0; y < gridSize; y++)
            for (int x = 0; x < gridSize; x++)
                snap[x][y] = canvas.getRGB(x, y);
        return snap;
    }

    private void restoreCanvas(int[][] snap) {
        for (int y = 0; y < gridSize; y++)
            for (int x = 0; x < gridSize; x++)
                canvas.setRGB(x, y, snap[x][y]);
        unsavedChanges = true;
    }

    // ── Shape tools ────────────────────────────────────────────────────────────
    private static boolean isShapeTool(Tool t) {
        return t == Tool.RECT || t == Tool.CIRCLE || t == Tool.LINE;
    }

    // ── Pixel selection / copy / paste ────────────────────────────────────────

    private void copyPixelSelection() {
        if (canvas == null || selPxX1 < 0 || selPxY1 < 0) return;
        int x1 = Math.min(selPxX1, selPxX2), x2 = Math.max(selPxX1, selPxX2);
        int y1 = Math.min(selPxY1, selPxY2), y2 = Math.max(selPxY1, selPxY2);
        int w = x2 - x1 + 1, h = y2 - y1 + 1;
        pixelClipboard = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int dy = 0; dy < h; dy++)
            for (int dx = 0; dx < w; dx++)
                pixelClipboard.setRGB(dx, dy, canvas.getRGB(x1 + dx, y1 + dy));
        clearPixelSelection();
        setStatus("Copied " + w + "×" + h + " pixels");
    }

    private void pastePixelClipboard() {
        if (canvas == null || pixelClipboard == null) return;
        pushUndo();
        int pw = pixelClipboard.getWidth(), ph = pixelClipboard.getHeight();
        // Paste at top-left (0,0) — user can undo if wrong
        for (int dy = 0; dy < ph && dy < gridSize; dy++)
            for (int dx = 0; dx < pw && dx < gridSize; dx++)
                canvas.setRGB(dx, dy, pixelClipboard.getRGB(dx, dy));
        unsavedChanges = true;
        pixelCanvas.repaint();
        previewPanel.repaint();
        setStatus("Pasted " + pw + "×" + ph + " pixels at 0,0");
    }

    private void clearPixelSelection() {
        selPxX1 = selPxY1 = selPxX2 = selPxY2 = -1;
        selDragging = false;
        if (pixelCanvas != null) pixelCanvas.repaint();
    }

    private void commitShape(Tool tool, int x0, int y0, int x1, int y1, boolean filled) {
        int rgb = currentColor.getRGB();
        switch (tool) {
            case RECT   -> drawRect(x0, y0, x1, y1, filled, rgb);
            case CIRCLE -> drawEllipse(x0, y0, x1, y1, filled, rgb);
            case LINE   -> drawLine(x0, y0, x1, y1, rgb);
            default -> {}
        }
    }

    private void setPixel(int x, int y, int rgb) {
        if (x >= 0 && x < gridSize && y >= 0 && y < gridSize)
            canvas.setRGB(x, y, rgb);
    }

    private void drawRect(int x0, int y0, int x1, int y1, boolean filled, int rgb) {
        int minX = Math.min(x0, x1), maxX = Math.max(x0, x1);
        int minY = Math.min(y0, y1), maxY = Math.max(y0, y1);
        if (filled) {
            for (int y = minY; y <= maxY; y++)
                for (int x = minX; x <= maxX; x++)
                    setPixel(x, y, rgb);
        } else {
            for (int x = minX; x <= maxX; x++) { setPixel(x, minY, rgb); setPixel(x, maxY, rgb); }
            for (int y = minY; y <= maxY; y++) { setPixel(minX, y, rgb); setPixel(maxX, y, rgb); }
        }
    }

    private void drawLine(int x0, int y0, int x1, int y1, int rgb) {
        int dx = Math.abs(x1 - x0), sx = x0 < x1 ? 1 : -1;
        int dy = -Math.abs(y1 - y0), sy = y0 < y1 ? 1 : -1;
        int err = dx + dy;
        while (true) {
            setPixel(x0, y0, rgb);
            if (x0 == x1 && y0 == y1) break;
            int e2 = 2 * err;
            if (e2 >= dy) { err += dy; x0 += sx; }
            if (e2 <= dx) { err += dx; y0 += sy; }
        }
    }

    private void drawEllipse(int x0, int y0, int x1, int y1, boolean filled, int rgb) {
        int minX = Math.min(x0, x1), maxX = Math.max(x0, x1);
        int minY = Math.min(y0, y1), maxY = Math.max(y0, y1);
        double cx = (minX + maxX) / 2.0, cy = (minY + maxY) / 2.0;
        double rx = (maxX - minX) / 2.0, ry = (maxY - minY) / 2.0;
        if (rx < 0.5) rx = 0.5;
        if (ry < 0.5) ry = 0.5;
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                double dx = (x - cx) / rx, dy = (y - cy) / ry;
                double dist = dx * dx + dy * dy;
                if (filled) {
                    if (dist <= 1.0) setPixel(x, y, rgb);
                } else {
                    // dist is the SQUARED normalised radius, so the old `dist >= 0.5` band
                    // started at 0.707r and made the "outline" a ring roughly a third of the
                    // radius thick — nearly a filled disc at 64x64. Compare the un-squared
                    // radius against a one-pixel tolerance instead.
                    double r = Math.sqrt(dist);
                    double tol = 1.0 / Math.max(1.0, Math.min(rx, ry));
                    if (r <= 1.0 && r >= 1.0 - tol) setPixel(x, y, rgb);
                }
            }
        }
    }

    private void drawShapePreview(Graphics2D g2, Tool tool, int x0, int y0, int x1, int y1, int cs, boolean filled) {
        switch (tool) {
            case RECT -> {
                int minX = Math.min(x0, x1), maxX = Math.max(x0, x1);
                int minY = Math.min(y0, y1), maxY = Math.max(y0, y1);
                for (int y = minY; y <= maxY; y++)
                    for (int x = minX; x <= maxX; x++) {
                        boolean edge = x == minX || x == maxX || y == minY || y == maxY;
                        if (edge) g2.fillRect(x * cs, y * cs, cs, cs);
                    }
            }
            case CIRCLE -> {
                int minX = Math.min(x0, x1), maxX = Math.max(x0, x1);
                int minY = Math.min(y0, y1), maxY = Math.max(y0, y1);
                double cx = (minX + maxX) / 2.0, cy = (minY + maxY) / 2.0;
                double rx = (maxX - minX) / 2.0, ry = (maxY - minY) / 2.0;
                if (rx < 0.5) rx = 0.5;
                if (ry < 0.5) ry = 0.5;
                for (int y = minY; y <= maxY; y++)
                    for (int x = minX; x <= maxX; x++) {
                        double dx = (x - cx) / rx, dy = (y - cy) / ry;
                        // Must match drawCircle exactly, or the rubber-band preview shows a
                        // different shape from the one that gets committed.
                        double r = Math.sqrt(dx * dx + dy * dy);
                        double tol = 1.0 / Math.max(1.0, Math.min(rx, ry));
                        if (r <= 1.0 && r >= 1.0 - tol) g2.fillRect(x * cs, y * cs, cs, cs);
                    }
            }
            case LINE -> {
                int lx = x0, ly = y0;
                int dxl = Math.abs(x1 - lx), sx = lx < x1 ? 1 : -1;
                int dyl = -Math.abs(y1 - ly), sy = ly < y1 ? 1 : -1;
                int err = dxl + dyl;
                while (true) {
                    if (lx >= 0 && lx < gridSize && ly >= 0 && ly < gridSize)
                        g2.fillRect(lx * cs, ly * cs, cs, cs);
                    if (lx == x1 && ly == y1) break;
                    int e2 = 2 * err;
                    if (e2 >= dyl) { err += dyl; lx += sx; }
                    if (e2 <= dxl) { err += dxl; ly += sy; }
                }
            }
            default -> {}
        }
    }

    // ── File I/O ─────────────────────────────────────────────────────────────
    private void loadImage() {
        // Build list of available sprites
        File root = tilesRoot();
        if (!root.exists()) { JOptionPane.showMessageDialog(this, "Tiles directory not found: " + root.getPath()); return; }

        // Collect all PNGs recursively, show relative path from tiles root
        List<File> allFiles = new ArrayList<>();
        collectPngs(root, allFiles);
        if (allFiles.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No PNG files found under: " + root.getPath());
            return;
        }
        String rootPath = root.getAbsolutePath() + File.separator;
        String[] names = allFiles.stream()
            .map(f -> f.getAbsolutePath().replace(rootPath, "").replace(File.separator, "/"))
            .toArray(String[]::new);
        String chosen = (String) JOptionPane.showInputDialog(this,
            "Select sprite to load:", "Load Sprite",
            JOptionPane.PLAIN_MESSAGE, null, names, names[0]);
        if (chosen == null) return;
        loadSpriteByName(chosen);
    }

    private void loadSpriteByName(String name) {
        if (!confirmDiscardPixels("Load Sprite")) return;

        // name may be a relative subpath like "dungeon/floor.png" or just "floor.png"
        File root = tilesRoot();
        File[] candidates = {
            new File(root, name),
            new File(root, name + ".png"),
            new File(name)   // absolute or cwd-relative fallback
        };
        for (File f : candidates) {
            if (f.exists()) {
                try {
                    BufferedImage img = ImageIO.read(f);
                    if (img == null) continue;
                    int w = img.getWidth(), h = img.getHeight();
                    // Snap to nearest supported grid size
                    int size = 32;
                    if (w <= 8 && h <= 8) size = 8;
                    else if (w <= 16 && h <= 16) size = 16;
                    else if (w <= 32 && h <= 32) size = 32;
                    else size = 64;

                    gridSize = size;
                    canvas = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D g = canvas.createGraphics();
                    g.drawImage(img, 0, 0, size, size, null);
                    g.dispose();

                    // Sync combo
                    String sizeStr = size + "×" + size;
                    for (int i = 0; i < sizeCombo.getItemCount(); i++)
                        if (sizeCombo.getItemAt(i).equals(sizeStr)) sizeCombo.setSelectedIndex(i);

                    currentSpriteFile = f;
                    currentSpriteName = name;
                    // The load above snapped this image to the nearest 8/16/32/64 grid and
                    // resampled it. Remember what it really was, so saving cannot quietly
                    // overwrite the original at the wrong resolution.
                    sourceW = img.getWidth();
                    sourceH = img.getHeight();
                    unsavedChanges = false;
                    undoStack.clear(); redoStack.clear();
                    setTitle("RETROQUEST  ·  IMAGE  EDITOR  —  " + name);
                    updateStatus();
                    pixelCanvas.repaint();
                    previewPanel.repaint();
                    spriteLibrary.repaint();
                    spriteLibrary.scrollToSelected();
                    return;
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(this, "Failed to load: " + ex.getMessage());
                }
            }
        }
        JOptionPane.showMessageDialog(this, "Sprite file not found: " + name);
    }

    /** Pixel size of the file behind {@code currentSpriteFile}, before any grid snapping. */
    private int sourceW, sourceH;

    private void saveImage() {
        if (currentSpriteFile == null) { saveAsNew(); return; }

        // Saving writes the snapped canvas straight back over the source. If the file on disk
        // was not one of the supported grid sizes, that silently replaces the artist's original
        // with a resampled copy — and mirrors the damaged version into target/classes too.
        if (sourceW > 0 && (sourceW != canvas.getWidth() || sourceH != canvas.getHeight())) {
            int choice = JOptionPane.showConfirmDialog(this,
                currentSpriteFile.getName() + " is " + sourceW + "×" + sourceH
              + " on disk, but the editor snapped it to "
              + canvas.getWidth() + "×" + canvas.getHeight() + ".\n\n"
              + "Saving REPLACES the original at that smaller size.\n"
              + "Choose No to keep it and use SAVE AS instead.",
                "Resolution mismatch", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (choice != JOptionPane.YES_OPTION) return;
        }

        try {
            currentSpriteFile.getParentFile().mkdirs();
            ImageIO.write(canvas, "png", currentSpriteFile);
            // Mirror to target/classes so classpath loader sees it immediately
            File rel = new File("src/main/resources/tiles");
            if (currentSpriteFile.getPath().startsWith(rel.getPath())) {
                String relative = rel.toURI().relativize(currentSpriteFile.toURI()).getPath();
                int slash = relative.lastIndexOf('/');
                String cat  = slash >= 0 ? relative.substring(0, slash) : "";
                String file = slash >= 0 ? relative.substring(slash + 1) : relative;
                mirrorToTarget(currentSpriteFile, cat, file);
            }
            try { ImageAssetRegistry.reloadAll(); } catch (Exception ignored) {}
            unsavedChanges = false;
            setStatus("Saved: " + currentSpriteFile.getName());
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Save failed: " + ex.getMessage());
        }
    }

    /**
     * Copies a saved sprite from src/main/resources/tiles into target/classes/tiles
     * so the classpath-based loader can find it immediately without a Maven build.
     */
    private void mirrorToTarget(File srcFile, String category, String fileName) {
        try {
            File targetTiles = new File("target/classes/tiles");
            File targetDir   = category.isEmpty() ? targetTiles : new File(targetTiles, category);
            targetDir.mkdirs();
            File dest = new File(targetDir, fileName);
            java.nio.file.Files.copy(srcFile.toPath(), dest.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            // Non-fatal — ImageAssetRegistry.loadFolderDirect() will still find the src copy
            System.err.println("[ImageEditor] Could not mirror to target/classes: " + e.getMessage());
        }
    }

    private void saveAsNew() {
        if (canvas == null) {
            JOptionPane.showMessageDialog(this, "Nothing to save yet.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        // Strip category prefix from sprite name so the dialog gets just the filename
        String suggestedName = currentSpriteName;
        String suggestedCategory = null;
        if (suggestedName != null && suggestedName.contains("/")) {
            int slash = suggestedName.lastIndexOf('/');
            suggestedCategory = suggestedName.substring(0, slash);
            suggestedName = suggestedName.substring(slash + 1);
        }
        new SaveSpriteDialog(this, suggestedName, suggestedCategory);
    }

    public void finishSave(SaveSpriteDialog.SaveResult result) {
        if (canvas == null) {
            JOptionPane.showMessageDialog(this, "Nothing to save yet.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String name = result.name();
        if (!name.endsWith(".png")) name += ".png";

        String category = result.category();
        String fullKey = category + "/" + name.replace(".png", "");

        // Save to src/main/resources/tiles (source of truth)
        File tilesRoot = new File("src/main/resources/tiles");
        File targetDir = new File(tilesRoot, category);
        targetDir.mkdirs();

        File targetFile = new File(targetDir, name);

        // Saving under an existing name replaces that sprite everywhere it is used
        if (targetFile.exists() && !targetFile.equals(currentSpriteFile)
                && JOptionPane.showConfirmDialog(this,
                    "tiles/" + fullKey + ".png already exists.\n\nOverwrite it?",
                    "File Exists", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            return;
        }

        try {
            ImageIO.write(canvas, "png", targetFile);

            // Mirror to target/classes/tiles so the classpath loader finds it
            // immediately without requiring a Maven build.
            mirrorToTarget(targetFile, category, name);

            currentSpriteName = fullKey;
            currentSpriteFile = targetFile;
            unsavedChanges = false;

            ImageAssetRegistry.reloadAll();
            spriteLibrary.refresh();

            JOptionPane.showMessageDialog(this,
                "✅ Sprite saved successfully!\n\n" +
                "Category: " + category + "\n" +
                "File: tiles/" + fullKey + ".png",
                "Saved", JOptionPane.INFORMATION_MESSAGE);

        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to save image:\n" + ex.getMessage(),
                "Save Error", JOptionPane.ERROR_MESSAGE);
            ex.printStackTrace();
        }
    }

    private void autoLoadSprite(String spriteName) {
        SwingUtilities.invokeLater(() -> loadSpriteByName(spriteName));
    }

    // ── Status / UI helpers ───────────────────────────────────────────────────
    private void updateStatus() {
        String toolName = switch (currentTool) {
            case DRAW -> "DRAW";
            case ERASE -> "ERASE";
            case FILL -> "FILL";
            case EYEDROPPER -> "EYEDROP";
            case RECT -> "RECT";
            case CIRCLE -> "CIRCLE";
            case LINE -> "LINE";
            case SELECT -> "SELECT";
        };
        String name = currentSpriteName != null ? "  |  " + currentSpriteName : "";
        String dirty = unsavedChanges ? " *" : "";
        statusLabel.setText(gridSize + "×" + gridSize + "  |  " + toolName + name + dirty);
    }

    private void setStatus(String msg) {
        statusLabel.setText(msg);
        // Flash green for save confirmations, then reset after 3 seconds
        boolean isSave = msg.startsWith("Saved:");
        statusLabel.setForeground(isSave ? PHOSPHOR : TEXT_DIM);
        javax.swing.Timer reset = new javax.swing.Timer(3000, e -> {
            statusLabel.setForeground(TEXT_DIM);
            updateStatus();
        });
        reset.setRepeats(false);
        reset.start();
    }

    private void updateToolHighlight() {
        toolButtons.forEach((tool, btn) -> {
            btn.setBackground(tool == currentTool
                ? new Color(0, 50, 30) : new Color(12, 20, 32));
            btn.setForeground(tool == currentTool ? PHOSPHOR : TEXT_BRIGHT);
        });
        updateStatus();
    }

    // ── Keyboard bindings ─────────────────────────────────────────────────────

    /**
     * Held so it can be unregistered. A {@code KeyEventDispatcher} is global to the focus
     * manager and the lambda captures this dialog, so one that is never removed keeps the
     * editor and its images alive for the life of the JVM and keeps intercepting keys.
     */
    private java.awt.KeyEventDispatcher keyDispatcher;

    private void setupKeyBindings() {
        keyDispatcher = e -> {
                if ((e.getID() != KeyEvent.KEY_PRESSED) || !isVisible()) return false;

                // Visibility alone was not enough. Every dialog this editor opens — Save As,
                // the colour chooser — sits on top of a still-visible ImageEditor, so their keys
                // were being stolen: Ctrl+C/V hit pixel copy/paste instead of the filename field,
                // Ctrl+S re-entered save, Escape cleared the selection instead of cancelling, and
                // the unmodified letter shortcuts below fired on ordinary typing.
                if (KeyboardFocusManager.getCurrentKeyboardFocusManager()
                        .getFocusedWindow() != ImageEditor.this) return false;
                if (e.getComponent() instanceof javax.swing.text.JTextComponent) return false;

                boolean ctrl = (e.getModifiersEx() & InputEvent.CTRL_DOWN_MASK) != 0;
                if (ctrl && e.getKeyCode() == KeyEvent.VK_Z) { undo(); return true; }
                if (ctrl && e.getKeyCode() == KeyEvent.VK_Y) { redo(); return true; }
                if (ctrl && e.getKeyCode() == KeyEvent.VK_S) { saveImage(); return true; }
                if (ctrl && e.getKeyCode() == KeyEvent.VK_C) { copyPixelSelection(); return true; }
                if (ctrl && e.getKeyCode() == KeyEvent.VK_V) { pastePixelClipboard(); return true; }
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) { clearPixelSelection(); return true; }
                if (!ctrl) switch (e.getKeyCode()) {
                    case KeyEvent.VK_UP   -> { spriteLibrary.navigateBy(-1); return true; }
                    case KeyEvent.VK_DOWN -> { spriteLibrary.navigateBy(+1); return true; }
                    case KeyEvent.VK_D -> { currentTool = Tool.DRAW;       updateToolHighlight(); return true; }
                    case KeyEvent.VK_E -> { currentTool = Tool.ERASE;      updateToolHighlight(); return true; }
                    case KeyEvent.VK_F -> { currentTool = Tool.FILL;       updateToolHighlight(); return true; }
                    case KeyEvent.VK_I -> { currentTool = Tool.EYEDROPPER; updateToolHighlight(); return true; }
                    case KeyEvent.VK_R -> { currentTool = Tool.RECT;       updateToolHighlight(); return true; }
                    case KeyEvent.VK_C -> { currentTool = Tool.CIRCLE;     updateToolHighlight(); return true; }
                    case KeyEvent.VK_L -> { currentTool = Tool.LINE;       updateToolHighlight(); return true; }
                    case KeyEvent.VK_S -> { currentTool = Tool.SELECT;     updateToolHighlight(); return true; }
                }
                return false;
        };
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(keyDispatcher);
    }

    @Override
    public void dispose() {
        if (keyDispatcher != null) {
            KeyboardFocusManager.getCurrentKeyboardFocusManager()
                    .removeKeyEventDispatcher(keyDispatcher);
            keyDispatcher = null;
        }
        super.dispose();
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  INNER CLASS — Pixel Canvas
    // ═════════════════════════════════════════════════════════════════════════
    class PixelCanvas extends JPanel {
        private int cellSize = 8;
        private boolean mouseDown = false;
        private JPanel squareContainer = null;

        void setSquareContainer(JPanel c) { this.squareContainer = c; }

        /** Compute cell size to fill the container perfectly (always square). */
        private int computeCellSize() {
            int side = Math.min(getWidth(), getHeight());
            if (side <= 0 || gridSize <= 0) return 8;
            return Math.max(1, side / gridSize);
        }

        PixelCanvas() {
            setBackground(new Color(5, 8, 12));
            MouseAdapter ma = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) {
                    mouseDown = true;
                    if (currentTool == Tool.SELECT) {
                        int x = e.getX() / cellSize, y = e.getY() / cellSize;
                        if (x >= 0 && x < gridSize && y >= 0 && y < gridSize) {
                            selPxX1 = x; selPxY1 = y;
                            selPxX2 = x; selPxY2 = y;
                            selDragging = true;
                            repaint();
                        }
                    } else if (isShapeTool(currentTool)) {
                        int x = e.getX() / cellSize, y = e.getY() / cellSize;
                        if (x >= 0 && x < gridSize && y >= 0 && y < gridSize) {
                            pushUndo();
                            shapeStartX = x; shapeStartY = y;
                            shapeEndX = x;   shapeEndY = y;
                            shapeDragging = true;
                            repaint();
                        }
                    } else {
                        handleMouse(e, true);
                    }
                }
                @Override public void mouseReleased(MouseEvent e) {
                    mouseDown = false;
                    if (selDragging) {
                        int x = e.getX() / cellSize, y = e.getY() / cellSize;
                        selPxX2 = Math.max(0, Math.min(gridSize - 1, x));
                        selPxY2 = Math.max(0, Math.min(gridSize - 1, y));
                        // Normalize
                        int x1 = Math.min(selPxX1, selPxX2), x2 = Math.max(selPxX1, selPxX2);
                        int y1 = Math.min(selPxY1, selPxY2), y2 = Math.max(selPxY1, selPxY2);
                        selPxX1 = x1; selPxY1 = y1; selPxX2 = x2; selPxY2 = y2;
                        selDragging = false;
                        repaint();
                    } else if (shapeDragging) {
                        int x = e.getX() / cellSize, y = e.getY() / cellSize;
                        shapeEndX = Math.max(0, Math.min(gridSize - 1, x));
                        shapeEndY = Math.max(0, Math.min(gridSize - 1, y));
                        boolean filled = (e.getModifiersEx() & InputEvent.SHIFT_DOWN_MASK) != 0;
                        commitShape(currentTool, shapeStartX, shapeStartY, shapeEndX, shapeEndY, filled);
                        shapeDragging = false;
                        shapeStartX = shapeStartY = shapeEndX = shapeEndY = -1;
                        unsavedChanges = true;
                        repaint(); previewPanel.repaint();
                    }
                }
                @Override public void mouseDragged(MouseEvent e) {
                    if (selDragging) {
                        int x = e.getX() / cellSize, y = e.getY() / cellSize;
                        selPxX2 = Math.max(0, Math.min(gridSize - 1, x));
                        selPxY2 = Math.max(0, Math.min(gridSize - 1, y));
                        repaint();
                    } else if (shapeDragging) {
                        int x = e.getX() / cellSize, y = e.getY() / cellSize;
                        shapeEndX = Math.max(0, Math.min(gridSize - 1, x));
                        shapeEndY = Math.max(0, Math.min(gridSize - 1, y));
                        repaint();
                    } else if (mouseDown) {
                        handleMouse(e, false);
                    }
                }
                @Override public void mouseMoved(MouseEvent e) {
                    int x = e.getX() / cellSize, y = e.getY() / cellSize;
                    if (x >= 0 && x < gridSize && y >= 0 && y < gridSize)
                        setStatus(x + ", " + y + "  #" + String.format("%02X%02X%02X",
                            new Color(canvas.getRGB(x,y),true).getRed(),
                            new Color(canvas.getRGB(x,y),true).getGreen(),
                            new Color(canvas.getRGB(x,y),true).getBlue()));
                }
            };
            addMouseListener(ma);
            addMouseMotionListener(ma);
        }

        void setCellSize(int s) { this.cellSize = s; }

        private void handleMouse(MouseEvent e, boolean firstClick) {
            if (canvas == null) return;
            int x = e.getX() / cellSize;
            int y = e.getY() / cellSize;
            if (x < 0 || x >= gridSize || y < 0 || y >= gridSize) return;

            switch (currentTool) {
                case DRAW -> {
                    if (firstClick) pushUndo();
                    canvas.setRGB(x, y, currentColor.getRGB());
                    unsavedChanges = true;
                    repaint(); previewPanel.repaint();
                }
                case ERASE -> {
                    if (firstClick) pushUndo();
                    canvas.setRGB(x, y, 0);
                    unsavedChanges = true;
                    repaint(); previewPanel.repaint();
                }
                case FILL -> {
                    if (firstClick) floodFill(x, y, currentColor);
                }
                case EYEDROPPER -> {
                    if (firstClick) {
                        currentColor = new Color(canvas.getRGB(x, y), true);
                        colorSwatch.repaint();
                        // Auto-switch back to draw
                        currentTool = Tool.DRAW;
                        updateToolHighlight();
                    }
                }
                default -> {}
            }
            updateStatus();
        }

        @Override
        public Dimension getPreferredSize() {
            // Fill parent completely — parent enforces the square constraint
            if (squareContainer != null) {
                Dimension d = squareContainer.getSize();
                if (d.width > 0) return new Dimension(d.width - 2, d.height - 2);
            }
            return new Dimension(gridSize * cellSize, gridSize * cellSize);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (canvas == null) return;
            Graphics2D g2 = (Graphics2D) g;

            // Always compute cell size from actual panel dimensions so it fills the square
            final int cs = computeCellSize();
            cellSize = cs; // keep in sync for mouse hit-testing

            // ── Checkerboard background (transparency indicator) ──
            for (int y = 0; y < gridSize; y++) {
                for (int x = 0; x < gridSize; x++) {
                    boolean light = (x + y) % 2 == 0;
                    g2.setColor(light ? new Color(40, 44, 52) : new Color(28, 32, 40));
                    g2.fillRect(x * cs, y * cs, cs, cs);
                }
            }

            // ── Pixels ──
            for (int y = 0; y < gridSize; y++) {
                for (int x = 0; x < gridSize; x++) {
                    int rgb = canvas.getRGB(x, y);
                    int alpha = (rgb >> 24) & 0xFF;
                    if (alpha > 0) {
                        g2.setColor(new Color(rgb, true));
                        g2.fillRect(x * cs, y * cs, cs, cs);
                    }
                }
            }

            // ── Shape preview (rubber-band) ──
            if (shapeDragging && shapeStartX >= 0) {
                Composite oldComp = g2.getComposite();
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f));
                g2.setColor(currentColor);
                boolean filled = false; // preview shows outline only
                drawShapePreview(g2, currentTool, shapeStartX, shapeStartY, shapeEndX, shapeEndY, cs, filled);
                g2.setComposite(oldComp);
            }

            // ── Selection overlay ──
            if (selPxX1 >= 0 && selPxY1 >= 0 && selPxX2 >= 0 && selPxY2 >= 0) {
                int rx1 = Math.min(selPxX1, selPxX2), rx2 = Math.max(selPxX1, selPxX2);
                int ry1 = Math.min(selPxY1, selPxY2), ry2 = Math.max(selPxY1, selPxY2);
                int sx = rx1 * cs, sy = ry1 * cs;
                int sw = (rx2 - rx1 + 1) * cs, sh = (ry2 - ry1 + 1) * cs;
                g2.setColor(new Color(0, 200, 255, 40));
                g2.fillRect(sx, sy, sw, sh);
                g2.setColor(new Color(0, 200, 255, 200));
                g2.setStroke(new BasicStroke(2f));
                g2.drawRect(sx, sy, sw, sh);
                g2.setStroke(new BasicStroke(1f));
            }

            // ── Grid lines ──
            if (cs >= 4) {
                g2.setColor(new Color(0, 80, 50, 60));
                g2.setStroke(new BasicStroke(0.5f));
                for (int x = 0; x <= gridSize; x++)
                    g2.drawLine(x * cs, 0, x * cs, gridSize * cs);
                for (int y = 0; y <= gridSize; y++)
                    g2.drawLine(0, y * cs, gridSize * cs, y * cs);
            }

            // ── Canvas border glow ──
            g2.setStroke(new BasicStroke(1.5f));
            g2.setColor(new Color(0, 180, 80, 80));
            g2.drawRect(0, 0, gridSize * cs, gridSize * cs);
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  INNER CLASS — Preview Panel (actual size + 2× magnification)
    // ═════════════════════════════════════════════════════════════════════════
    class PreviewPanel extends JPanel {
        PreviewPanel() {
            setOpaque(false);
            setPreferredSize(new Dimension(200, 48));
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (canvas == null) return;
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

            // Checkerboard under previews
            int sz1 = gridSize, sz2 = gridSize * 2;

            // 1× preview
            drawCheckerboard(g2, 0, 0, sz1, getHeight());
            g2.drawImage(canvas, 0, 0, sz1, sz1, null);
            g2.setColor(BORDER_COL);
            g2.drawRect(0, 0, sz1, sz1);

            // 2× preview
            int x2 = sz1 + 8;
            drawCheckerboard(g2, x2, 0, sz2, getHeight());
            g2.drawImage(canvas, x2, 0, sz2, sz2, null);
            g2.setColor(BORDER_COL);
            g2.drawRect(x2, 0, sz2, sz2);

            // Labels
            g2.setFont(MONO_XS);
            g2.setColor(TEXT_DIM);
            g2.drawString("1×", 0, sz1 + 12);
            g2.drawString("2×", x2, sz1 + 12);
        }

        private void drawCheckerboard(Graphics2D g2, int ox, int oy, int size, int h) {
            int cs = Math.max(2, size / 8);
            for (int y = 0; y * cs < size; y++)
                for (int x = 0; x * cs < size; x++) {
                    g2.setColor((x+y)%2==0 ? new Color(50,55,60) : new Color(35,38,44));
                    g2.fillRect(ox + x*cs, oy + y*cs, cs, cs);
                }
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  INNER CLASS — Sprite Library Panel
    // ═════════════════════════════════════════════════════════════════════════
    class SpriteLibraryPanel extends JPanel {
        interface SelectHandler { void onSelect(String name); }
        private SelectHandler onSelect;
        // Flat ordered list of all sprite relPaths for arrow-key navigation
        private final List<String> flatList = new ArrayList<>();

        SpriteLibraryPanel() {
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setBackground(BG);
            setMaximumSize(new Dimension(200, Integer.MAX_VALUE));
            refresh();
        }

        void setOnSelect(SelectHandler h) { this.onSelect = h; }

        /** Navigate by delta (-1 = up, +1 = down) relative to current selection. */
        void navigateBy(int delta) {
            if (flatList.isEmpty() || onSelect == null) return;
            int idx = currentSpriteName != null ? flatList.indexOf(currentSpriteName) : -1;
            // Also check by filename only as fallback
            if (idx < 0 && currentSpriteName != null) {
                for (int i = 0; i < flatList.size(); i++) {
                    String rel = flatList.get(i);
                    if (rel.endsWith("/" + currentSpriteName) || rel.equals(currentSpriteName)) { idx = i; break; }
                }
            }
            if (idx < 0) idx = delta > 0 ? -1 : 0;
            int next = Math.max(0, Math.min(flatList.size() - 1, idx + delta));
            onSelect.onSelect(flatList.get(next));
        }

        /** Scroll the JScrollPane ancestor so the selected row is visible. */
        void scrollToSelected() {
            if (currentSpriteName == null) return;
            // Find the row component matching currentSpriteName and scroll to it
            for (Component c : getComponents()) {
                if (!(c instanceof JPanel row)) continue;
                String tip = null;
                for (Component child : row.getComponents()) {
                    if (child instanceof JLabel lbl && lbl.getToolTipText() != null) {
                        tip = lbl.getToolTipText(); break;
                    }
                }
                if (tip != null && (tip.equals(currentSpriteName) ||
                        tip.endsWith("/" + currentSpriteName) || currentSpriteName.endsWith("/" + tip))) {
                    row.scrollRectToVisible(new Rectangle(0, 0, row.getWidth(), row.getHeight()));
                    break;
                }
            }
        }

        void refresh() {
            removeAll();
            flatList.clear();

            // Use filesystem as source of truth — scan subdirs, group by folder name
            File root = tilesRoot();
            if (!root.exists()) {
                add(makeLabel("  No tiles dir found", TEXT_DIM));
                revalidate(); repaint();
                return;
            }

            // Collect all PNGs recursively with their relative path from tiles root
            List<File> allFiles = new ArrayList<>();
            collectPngs(root, allFiles);

            if (allFiles.isEmpty()) {
                add(makeLabel("  No sprites found", TEXT_DIM));
                revalidate(); repaint();
                return;
            }

            String rootAbs = root.getAbsolutePath() + File.separator;

            // Group by immediate subdirectory name (e.g. "dungeon", "monsters")
            // Files directly in tiles/ go under "OTHER"
            Map<String, List<File>> groups = new LinkedHashMap<>();
            for (File f : allFiles) {
                String rel = f.getAbsolutePath().replace(rootAbs, "");
                String category;
                if (rel.contains(File.separator)) {
                    category = rel.substring(0, rel.indexOf(File.separator)).toUpperCase();
                } else if (rel.contains("/")) {
                    category = rel.substring(0, rel.indexOf("/")).toUpperCase();
                } else {
                    category = "OTHER";
                }
                groups.computeIfAbsent(category, k -> new ArrayList<>()).add(f);
            }

            // Also check registry for any classpath-only entries not on filesystem
            Set<String> fsNames = new java.util.HashSet<>();
            for (File f : allFiles) fsNames.add(f.getName().replace(".png",""));
            List<String> registryOnly = new ArrayList<>();
            for (String k : ImageAssetRegistry.getAllSpriteKeys()) {
                String simple = k.contains("/") ? k.substring(k.lastIndexOf("/")+1) : k;
                if (!fsNames.contains(simple)) registryOnly.add(k);
            }
            if (!registryOnly.isEmpty()) {
                groups.put("CLASSPATH", registryOnly.stream()
                    .map(k -> new File(root, k + ".png"))
                    .collect(java.util.stream.Collectors.toList()));
            }

            for (Map.Entry<String, List<File>> entry : groups.entrySet()) {
                add(categoryHeader("◈ " + entry.getKey()));
                for (File f : entry.getValue()) {
                    // Relative path from tiles root = what we pass to loadSpriteByName
                    String rel = f.getAbsolutePath().replace(rootAbs, "").replace(File.separator, "/");
                    flatList.add(rel);
                    add(spriteRow(f, rel));
                }
            }
            revalidate(); repaint();
        }

        private JLabel categoryHeader(String title) {
            JLabel l = new JLabel("  " + title);
            l.setFont(new Font("Monospaced", Font.BOLD, 10));
            l.setForeground(ACCENT);
            l.setOpaque(true);
            l.setBackground(new Color(0, 20, 35));
            l.setPreferredSize(new Dimension(200, 20));
            l.setMaximumSize(new Dimension(200, 20));
            l.setAlignmentX(Component.LEFT_ALIGNMENT);
            return l;
        }

        private JPanel spriteRow(File f, String relPath) {
            JPanel row = new JPanel(new BorderLayout()) {
                { addMouseListener(new MouseAdapter() {
                    @Override public void mouseClicked(MouseEvent e) {
                        if (onSelect != null) onSelect.onSelect(relPath);
                    }
                }); }
                @Override protected void paintComponent(Graphics g) {
                    boolean sel = relPath.equals(currentSpriteName) || f.getName().equals(currentSpriteName);
                    g.setColor(sel ? new Color(0, 90, 55) : BG);
                    g.fillRect(0, 0, getWidth(), getHeight());
                    if (sel) { g.setColor(PHOSPHOR); g.fillRect(0, 0, 3, getHeight()); }
                }
            };
            row.setOpaque(false);
            row.setPreferredSize(new Dimension(200, 28));
            row.setMaximumSize(new Dimension(200, 28));
            row.setAlignmentX(Component.LEFT_ALIGNMENT);
            row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            // Thumbnail — try registry first, then filesystem
            JPanel thumb = new JPanel() {
                BufferedImage img;
                {
                    // Try registry first (fast, no disk I/O)
                    String key = relPath.replace(".png", "");
                    java.awt.Image regImg = ImageAssetRegistry.get(key);
                    if (regImg instanceof BufferedImage) {
                        img = (BufferedImage) regImg;
                    } else if (regImg != null) {
                        img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
                        img.getGraphics().drawImage(regImg, 0, 0, 16, 16, null);
                    } else if (f.exists()) {
                        // Direct file read for filesystem sprites
                        try { img = ImageIO.read(f); } catch (Exception ignored) {}
                    }
                }
                @Override protected void paintComponent(Graphics g) {
                    g.setColor(new Color(20, 25, 35));
                    g.fillRect(0, 0, getWidth(), getHeight());
                    if (img != null)
                        ((Graphics2D)g).setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                            RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                    if (img != null)
                        g.drawImage(img, 1, 1, getWidth()-2, getHeight()-2, null);
                }
                @Override public Dimension getPreferredSize() { return new Dimension(24, 24); }
            };
            thumb.setOpaque(false);

            String displayName = "  " + f.getName().replace(".png","");
            JLabel lbl = new JLabel(displayName);
            lbl.setForeground(TEXT_BRIGHT);
            lbl.setFont(MONO_XS);
            lbl.setToolTipText(relPath);

            row.add(thumb, BorderLayout.WEST);
            row.add(lbl,   BorderLayout.CENTER);
            return row;
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  HELPERS
    // ═════════════════════════════════════════════════════════════════════════
    /** Finds the tiles root directory, checking common Maven/Eclipse layouts. */
    private static File tilesRoot() {
        for (String path : new String[]{
                "src/main/resources/tiles",
                "resources/tiles",
                "tiles"}) {
            File f = new File(path);
            if (f.exists() && f.isDirectory()) return f;
        }
        return new File("src/main/resources/tiles"); // default even if missing
    }

    /** Recursively collect all .png files under a directory into a flat list. */
    private static void collectPngs(File dir, List<File> result) {
        File[] children = dir.listFiles();
        if (children == null) return;
        Arrays.sort(children, Comparator.comparing(File::getName));
        for (File f : children) {
            if (f.isDirectory()) collectPngs(f, result);
            else if (f.getName().toLowerCase().endsWith(".png")) result.add(f);
        }
    }

    private static JPanel darkPanel() {
        JPanel p = new JPanel(); p.setBackground(PANEL_BG); return p;
    }

    private static JLabel makeLabel(String txt, Color c) {
        JLabel l = new JLabel(txt); l.setForeground(c); l.setFont(MONO_SM); return l;
    }

    private static JLabel sectionLabel(String txt) {
        JLabel l = new JLabel(txt);
        l.setForeground(ACCENT);
        l.setFont(new Font("Monospaced", Font.BOLD, 10));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        l.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
        return l;
    }

    private static JButton retroButton(String txt) {
        JButton b = new JButton(txt) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setColor(getModel().isPressed() ? new Color(0,30,50) : getBackground());
                g2.fillRect(0,0,getWidth(),getHeight());
                g2.setColor(getForeground());
                g2.setFont(getFont());
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(getText(),
                    (getWidth()-fm.stringWidth(getText()))/2,
                    (getHeight()+fm.getAscent()-fm.getDescent())/2);
            }
        };
        b.setFont(MONO_XS);
        b.setBackground(new Color(12,20,32));
        b.setForeground(TEXT_BRIGHT);
        b.setBorder(BorderFactory.createLineBorder(BORDER_COL));
        b.setFocusPainted(false);
        b.setContentAreaFilled(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    private JButton toolButton(String txt, Tool tool) {
        JButton b = retroButton(txt);
        b.setBackground(tool == currentTool ? new Color(0,50,30) : new Color(12,20,32));
        b.setForeground(tool == currentTool ? PHOSPHOR : TEXT_BRIGHT);
        b.addActionListener(e -> {
            currentTool = tool;
            updateToolHighlight();
        });
        toolButtons.put(tool, b);
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
        b.setPreferredSize(new Dimension(160, 34));
        return b;
    }

    private static void styleCombo(JComboBox<String> cb) {
        cb.setBackground(PANEL_BG);
        cb.setForeground(TEXT_BRIGHT);
        cb.setFont(MONO_XS);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(ImageEditor::new);
    }

    public static void openSprite(String spriteName) {
        new ImageEditor(spriteName);
    }
}

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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.nio.file.Files;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ScrollPaneConstants;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;

import io.cannonforge.retroquest.core.Retroquest;
import io.cannonforge.retroquest.model.MapData;
import io.cannonforge.retroquest.model.NPC;
import io.cannonforge.retroquest.registry.ImageAssetRegistry;

@SuppressWarnings("serial")
public class RetroForge extends JFrame {

    private MapData currentMap;
    private MapCanvas canvas;
    private TilePalette palette;
    private LivePreviewPanel preview;
    private Tool currentTool = Tool.PENCIL;
    private JSpinner spawnDifficultySpinner;
    private JLabel brushSizeLabel;
    private JLabel spawnValueLabel;   // shows current spawn difficulty when using that tool
    private JComboBox<String> npcTemplateCombo;
    private JLabel brushLabel;   // "BRUSH:" companion
    private JLabel spawnLabel;   // "SPAWN:" companion
    private JLabel npcLabel;     // "NPC:" companion
    private JLabel entryLabel;   // "ENTRY:" companion (town only)
    private JSpinner entryXSpinner;
    private JSpinner entryYSpinner;
    private JPanel entryDivider;
    private JLabel statusLabel;   // shows current tool
    private javax.swing.Timer statusTimer;
    private JLabel zoomLabel;     // shows current zoom %
    private File currentMapFile = null;
    private boolean dirty = false;   // unsaved map edits since the last save/load
    private java.util.Map<Tool, JButton> toolButtonMap;
 // 1. Constructor — simple default (creates mainland if nothing exists)
    private static final File PREFS_FILE = new File("data/.forge_prefs");

    // Prefs format: line 0 = absolute map path, line 1 = "cameraX cameraY zoomIndex"
    private void savePrefs() {
        if (currentMapFile == null) return;
        try {
            String view = canvas != null
                ? canvas.getCameraX() + " " + canvas.getCameraY() + " " + canvas.getZoomIndex()
                : "0 0 4";
            Files.writeString(PREFS_FILE.toPath(), currentMapFile.getAbsolutePath() + "\n" + view);
        } catch (Exception ignored) {}
    }

    /** Returns saved map file, or null if none / file missing. */
    private File loadLastMapPath() {
        try {
            if (PREFS_FILE.exists()) {
                String[] lines = Files.readString(PREFS_FILE.toPath()).split("\n", 2);
                File f = new File(lines[0].trim());
                if (f.exists()) return f;
            }
        } catch (Exception ignored) {}
        return null;
    }

    /** Returns saved view as int[]{camX, camY, zoomIndex}, or null if unavailable. */
    private int[] loadLastView() {
        try {
            if (PREFS_FILE.exists()) {
                String[] lines = Files.readString(PREFS_FILE.toPath()).split("\n", 2);
                if (lines.length >= 2) {
                    String[] parts = lines[1].trim().split(" ");
                    if (parts.length >= 3)
                        return new int[]{ Integer.parseInt(parts[0]),
                                          Integer.parseInt(parts[1]),
                                          Integer.parseInt(parts[2]) };
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    public RetroForge() { this(true); }

    /**
     * Builds the editor, optionally without showing the window.
     *
     * <p>{@code show == false} is the offscreen-rendering entry point used by
     * {@code RetroRecorder}: the editor is painted into a {@code BufferedImage} to film it, and a
     * real window would do nothing but flash across the operator's desktop — maximised, since the
     * constructor asks for {@code MAXIMIZED_BOTH}. A frame built this way is never realised, so a
     * caller has to size and validate it before painting.
     */
    public RetroForge(boolean show) {
        super("RETROFORGE — Edit Maps, NPCs, Items & Quests");
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);   // windowClosing asks to save first
        setExtendedState(MAXIMIZED_BOTH);

        // Load last edited map, falling back to lirandel, then a new blank map
        File lastFile = loadLastMapPath();
        File defaultFile = (lastFile != null) ? lastFile : new File("data/overworlds/lirandel.rfmap");
        if (defaultFile.exists()) {
            try {
                currentMap = MapData.load(defaultFile);
                currentMapFile = defaultFile;
            } catch (Exception e) {
                currentMap = MapData.createNew(MapData.MapType.OVERWORLD, "lirandel", 140, 90);
            }
        } else {
            currentMap = MapData.createNew(MapData.MapType.OVERWORLD, "lirandel", 140, 90);
        }

        if (currentMapFile != null) {
            setTitle("RETROFORGE — Editing: " + currentMap.name);
        }

        int[] savedView = loadLastView();

        palette = new TilePalette(this);
        canvas  = new MapCanvas(this);
        preview = new LivePreviewPanel(this);

        // ── Apply CRT theme to the frame itself ──
        getContentPane().setBackground(BG);
        setLayout(new BorderLayout(0, 0));

        // ── Left sidebar ──
        JPanel leftSidebar = buildLeftSidebar();
        add(leftSidebar, BorderLayout.WEST);
        add(canvas,      BorderLayout.CENTER);

        createEnhancedToolBar();
        styleMenuBar();
        setVisible(show);

        // ── Global key dispatcher ──────────────────────────────────────────
        // KeyEventDispatcher fires before Swing's normal WHEN_FOCUSED processing,
        // so it works regardless of which component currently has focus.
        // We skip text components so they keep their own built-in text undo/copy/paste.
        KeyEventDispatcher globalKeyDispatcher = e -> {
            if (e.getID() != KeyEvent.KEY_PRESSED || !isActive()) return false;
            boolean ctrl = e.isControlDown();
            boolean isText = e.getComponent() instanceof javax.swing.text.JTextComponent;
            // Ctrl+Z/Y: undo/redo (skip text components)
            if (ctrl && !isText) {
                if (e.getKeyCode() == KeyEvent.VK_Z) { canvas.undo(); e.consume(); return true; }
                if (e.getKeyCode() == KeyEvent.VK_Y) { canvas.redo(); e.consume(); return true; }
            }
            // Ctrl+C/V/Escape: select tool copy/paste/clear (skip text components)
            if (!isText) {
                if (ctrl && e.getKeyCode() == KeyEvent.VK_C && canvas.hasSelection()) {
                    canvas.copySelection();
                    setStatus("COPIED " + canvas.getClipboardSize());
                    e.consume(); return true;
                }
                if (ctrl && e.getKeyCode() == KeyEvent.VK_V && canvas.hasClipboard()) {
                    canvas.pasteAtHover();
                    setStatus("PASTED " + canvas.getClipboardSize());
                    e.consume(); return true;
                }
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    canvas.clearSelection();
                    e.consume(); return true;
                }
            }
            return false;
        };
        KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .addKeyEventDispatcher(globalKeyDispatcher);

        // Save view+map on close; remove the global dispatcher when window closes
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosing(java.awt.event.WindowEvent e) {
                if (!confirmDiscard("exiting")) return;
                savePrefs();
                System.exit(0);
            }
            @Override public void windowClosed(java.awt.event.WindowEvent e) {
                KeyboardFocusManager.getCurrentKeyboardFocusManager()
                        .removeKeyEventDispatcher(globalKeyDispatcher);
            }
        });

        SwingUtilities.invokeLater(() -> {
            if (savedView != null && currentMapFile != null) {
                canvas.restoreView(savedView[0], savedView[1], savedView[2]);
            }
            palette.refresh(currentMap.type);
            revalidate();
            repaint();
            preview.refresh();
            canvas.requestFocusInWindow();
        });
    }

    // ── Unsaved-changes tracking ─────────────────────────────────────────────

    /** Flags the current map as edited; called by MapCanvas and the map dialogs. */
    public void markDirty() {
        if (!dirty) { dirty = true; refreshTitle(); }
    }

    /** Clears the edited flag after a successful save or a fresh load. */
    private void clearDirty() {
        dirty = false;
        refreshTitle();
    }

    private void refreshTitle() {
        setTitle("RETROFORGE — Editing: " + currentMap.name + (dirty ? "  *" : ""));
    }

    /**
     * Offers to save unsaved work before an action that throws the current map away.
     *
     * @return false if the user cancelled — the caller must abort
     */
    private boolean confirmDiscard(String action) {
        if (!dirty) return true;
        int choice = JOptionPane.showConfirmDialog(this,
                "'" + currentMap.name + "' has unsaved changes.\n\nSave before " + action + "?",
                "Unsaved Changes", JOptionPane.YES_NO_CANCEL_OPTION);
        if (choice == JOptionPane.YES_OPTION) return saveMap();
        return choice == JOptionPane.NO_OPTION;
    }

    public void updateSpawnSpinner(int value) {
        if (spawnDifficultySpinner != null) {
            spawnDifficultySpinner.setValue(Math.max(0, Math.min(99, value)));
        }
    }

    // ── Styled menu bar ──────────────────────────────────────────────────────
    private void styleMenuBar() {
        JMenuBar bar = getJMenuBar();
        if (bar == null) return;
        bar.setBackground(new Color(8, 12, 20));
        bar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_COL));
        for (int i = 0; i < bar.getMenuCount(); i++) {
            JMenu m = bar.getMenu(i);
            m.setBackground(new Color(8, 12, 20));
            m.setForeground(TEXT_BRIGHT);
            m.setFont(MONO_MD);
            m.getPopupMenu().setBackground(PANEL_BG);
            m.getPopupMenu().setBorder(BorderFactory.createLineBorder(BORDER_COL));
            for (int j = 0; j < m.getItemCount(); j++) {
                JMenuItem item = m.getItem(j);
                if (item == null) continue;
                item.setBackground(PANEL_BG);
                item.setForeground(TEXT_BRIGHT);
                item.setFont(MONO_SM);
                item.setArmed(false);
            }
        }
    }

    private void createEnhancedToolBar() {
        // ── Menu bar (structure unchanged, styled in styleMenuBar()) ─────────
        JMenuBar menuBar = new JMenuBar();

        JMenu fileMenu = new JMenu("FILE");
        fileMenu.add(createMenuItem("✦  New Map...",  e -> newMapDialog()));
        fileMenu.add(createMenuItem("📂  Load Map...", e -> loadMap()));
        JMenuItem saveItem = createMenuItem("💾  Save Map", e -> saveMap());
        saveItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK));
        fileMenu.add(saveItem);
        fileMenu.addSeparator();
        fileMenu.add(createMenuItem("⇲  Resize Map",  e -> resizeDialog()));
        fileMenu.add(createMenuItem("✕  Exit",         e -> {
            if (!confirmDiscard("exiting")) return;
            savePrefs();
            System.exit(0);
        }));
        menuBar.add(fileMenu);

        JMenu editMenu = new JMenu("EDIT");
        JMenuItem undoItem = createMenuItem("↩  Undo", e -> canvas.undo());
        undoItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK));
        editMenu.add(undoItem);
        JMenuItem redoItem = createMenuItem("↪  Redo", e -> canvas.redo());
        redoItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK));
        editMenu.add(redoItem);
        menuBar.add(editMenu);

        JMenu viewMenu = new JMenu("VIEW");
        JMenuItem zoomInItem = createMenuItem("+  Zoom In", e -> canvas.zoom(1, canvas.getWidth() / 2, canvas.getHeight() / 2));
        zoomInItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, InputEvent.CTRL_DOWN_MASK));
        viewMenu.add(zoomInItem);
        JMenuItem zoomOutItem = createMenuItem("-  Zoom Out", e -> canvas.zoom(-1, canvas.getWidth() / 2, canvas.getHeight() / 2));
        zoomOutItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, InputEvent.CTRL_DOWN_MASK));
        viewMenu.add(zoomOutItem);
        JMenuItem zoomResetItem = createMenuItem("0  Reset Zoom", e -> canvas.resetZoom());
        zoomResetItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_0, InputEvent.CTRL_DOWN_MASK));
        viewMenu.add(zoomResetItem);
        menuBar.add(viewMenu);

        JMenu toolsMenu = new JMenu("TOOLS");
        for (Tool t : Tool.values()) {
            JMenuItem item = createMenuItem(t.getDisplayName(), e -> selectTool(t));
            toolsMenu.add(item);
        }
        menuBar.add(toolsMenu);

        JMenu editorsMenu = new JMenu("EDITORS");
        editorsMenu.add(createMenuItem("Item Editor",    e -> new ItemEditorDialog(this)));
        editorsMenu.add(createMenuItem("Quest Editor",   e -> new QuestEditorDialog(this)));
        editorsMenu.add(createMenuItem("Monster Editor", e -> new MonsterEditorDialog(this)));
        editorsMenu.add(createMenuItem("NPC Editor",     e -> new NPCEditorDialog(this, getCurrentMap())));
        editorsMenu.add(createMenuItem("Tile Editor",    e -> { new TileEditor(this); palette.refresh(currentMap.type); }));
        editorsMenu.add(createMenuItem("Image Editor",   e -> new ImageEditor()));
        editorsMenu.add(createMenuItem("Dialogue Tree Editor", e -> {
            if (currentMap == null || currentMap.npcs == null || currentMap.npcs.isEmpty()) {
                JOptionPane.showMessageDialog(this, "No NPCs on current map.", "No NPCs", JOptionPane.WARNING_MESSAGE);
                return;
            }
            String[] names = currentMap.npcs.stream().map(NPC::getName).toArray(String[]::new);
            String sel = (String) JOptionPane.showInputDialog(this, "Select NPC:", "Dialogue Tree Editor",
                JOptionPane.PLAIN_MESSAGE, null, names, names[0]);
            if (sel == null) return;
            for (NPC npc : currentMap.npcs) {
                if (npc.getName().equals(sel)) {
                    new DialogueTreeEditor(this, npc);
                    break;
                }
            }
        }));
        menuBar.add(editorsMenu);

        JMenu gameMenu = new JMenu("GAME");
        gameMenu.add(createMenuItem("▶  Playtest Now", e -> playtest()));
        gameMenu.addSeparator();
        gameMenu.add(createMenuItem("⚙  Game Settings...", e -> new GameSettingsDialog(this)));
        menuBar.add(gameMenu);

        JMenu helpMenu = new JMenu("HELP");
        helpMenu.add(createMenuItem("📖  Operators Guide", e -> showHelpGuide()));
        menuBar.add(helpMenu);

        setJMenuBar(menuBar);

        // ── Retro toolbar panel (replaces JToolBar) ───────────────────────
        JPanel tb = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 8));
        tb.setBackground(new Color(8, 12, 20));
        tb.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_COL));

        // ── Tool buttons ──
        JPanel toolGroup = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 0));
        toolGroup.setOpaque(false);
        toolButtonMap = new java.util.LinkedHashMap<>();
        for (Tool t : new Tool[]{Tool.PENCIL, Tool.FILL, Tool.SELECT, Tool.NPC_PLACER, Tool.TOWN_PLACER, Tool.DUNGEON_PLACER, Tool.SPAWN_DIFFICULTY}) {
            Color accent = switch (t) {
                case SELECT             -> AMBER;
                case TOWN_PLACER        -> new Color(180,  60, 255);
                case DUNGEON_PLACER     -> new Color(220, 100,  40);
                case SPAWN_DIFFICULTY   -> DANGER;
                case NPC_PLACER         -> PHOSPHOR2;
                default                 -> PHOSPHOR;
            };
            JButton b = toolBtn(t.getDisplayName(), accent);
            b.addActionListener(e -> selectTool(t));
            toolGroup.add(b);
            toolButtonMap.put(t, b);
        }
        tb.add(toolGroup);

        tb.add(vDivider());

        // ── NPC combo ──
        npcLabel = makeLabel("NPC:", TEXT_DIM);
        tb.add(npcLabel);
        npcTemplateCombo = new JComboBox<>();
        npcTemplateCombo.setPreferredSize(new Dimension(200, 26));
        npcTemplateCombo.setBackground(PANEL_BG);
        npcTemplateCombo.setForeground(TEXT_BRIGHT);
        npcTemplateCombo.setFont(MONO_XS);
        npcTemplateCombo.setVisible(false);
        tb.add(npcTemplateCombo);

        tb.add(vDivider());

        // ── Spawn difficulty spinner ──
        spawnLabel = makeLabel("SPAWN:", TEXT_DIM);
        tb.add(spawnLabel);
        spawnDifficultySpinner = new JSpinner(new SpinnerNumberModel(5, 0, 99, 1));
        spawnDifficultySpinner.setPreferredSize(new Dimension(70, 26));
        spawnDifficultySpinner.setBackground(PANEL_BG);
        spawnDifficultySpinner.setFont(MONO_SM);
        spawnDifficultySpinner.setEnabled(false);
        spawnDifficultySpinner.addChangeListener(e -> {
            if (canvas != null && currentTool == Tool.SPAWN_DIFFICULTY)
                canvas.setSpawnDifficultyValue((Integer) spawnDifficultySpinner.getValue());
        });
        tb.add(spawnDifficultySpinner);
        spawnValueLabel = makeLabel("   ", TEXT_DIM);
        spawnValueLabel.setVisible(false);
        tb.add(spawnValueLabel);

        tb.add(vDivider());

        // ── Brush size label ──
        brushLabel = makeLabel("BRUSH:", TEXT_DIM);
        brushLabel.setVisible(false);
        tb.add(brushLabel);
        brushSizeLabel = makeLabel("1\u00d71", PHOSPHOR);
        brushSizeLabel.setVisible(false);
        tb.add(brushSizeLabel);

        tb.add(vDivider());

        // ── Zoom label ──
        zoomLabel = makeLabel("ZOOM: 100%", TEXT_DIM);
        tb.add(zoomLabel);

        entryDivider = vDivider();
        entryDivider.setVisible(false);
        tb.add(entryDivider);

        // ── Town entry spawn point (TOWN maps only) ──
        entryLabel = makeLabel("ENTRY X/Y:", PHOSPHOR3);
        entryLabel.setVisible(false);
        tb.add(entryLabel);
        entryXSpinner = new JSpinner(new SpinnerNumberModel(10, 0, 2047, 1));
        entryXSpinner.setPreferredSize(new Dimension(70, 26));
        entryXSpinner.setBackground(PANEL_BG);
        entryXSpinner.setFont(MONO_SM);
        entryXSpinner.setToolTipText("Town entry X (column)");
        entryXSpinner.setVisible(false);
        entryXSpinner.addChangeListener(e -> {
            if (currentMap != null && currentMap.type == MapData.MapType.TOWN) {
                int v = (Integer) entryXSpinner.getValue();
                if (currentMap.interiorEntryX != v) { currentMap.interiorEntryX = v; markDirty(); }
            }
        });
        tb.add(entryXSpinner);

        entryYSpinner = new JSpinner(new SpinnerNumberModel(13, 0, 2047, 1));
        entryYSpinner.setPreferredSize(new Dimension(70, 26));
        entryYSpinner.setBackground(PANEL_BG);
        entryYSpinner.setFont(MONO_SM);
        entryYSpinner.setToolTipText("Town entry Y (row)");
        entryYSpinner.setVisible(false);
        entryYSpinner.addChangeListener(e -> {
            if (currentMap != null && currentMap.type == MapData.MapType.TOWN) {
                int v = (Integer) entryYSpinner.getValue();
                if (currentMap.interiorEntryY != v) { currentMap.interiorEntryY = v; markDirty(); }
            }
        });
        tb.add(entryYSpinner);

        // ── Status label (right-aligned via glue panel) ──
        JPanel statusWrap = new JPanel(new BorderLayout());
        statusWrap.setOpaque(false);
        statusLabel = makeLabel("◈  PENCIL  TOOL", PHOSPHOR);
        statusLabel.setFont(MONO_MD);
        statusLabel.setBorder(BorderFactory.createEmptyBorder(0, 16, 0, 12));
        statusWrap.add(statusLabel, BorderLayout.EAST);

        // Add tb + status in a wrapper so status floats right
        JPanel tbWrap = new JPanel(new BorderLayout());
        tbWrap.setBackground(new Color(8, 12, 20));
        tbWrap.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_COL));
        tbWrap.add(tb,         BorderLayout.WEST);
        tbWrap.add(statusWrap, BorderLayout.CENTER);

        add(tbWrap, BorderLayout.NORTH);

        refreshNPCTemplateCombo();
        canvas.setDoubleBuffered(true);
        selectTool(currentTool);
    }

    /** Central tool-select helper — keeps button highlights + labels in sync. */
    /** Selects a tool the way the toolbar does — updates the button state and the canvas.
     *  Public because { RetroRecorder} drives it to film the tools; { canvas.setTool}
     *  alone switches the canvas but leaves the toolbar showing the old one. */
    public void selectTool(Tool t) {
        currentTool = t;
        canvas.setTool(t);
        statusLabel.setText("◈  " + t.getDisplayName().toUpperCase());
        statusLabel.setForeground(switch (t) {
            case SELECT            -> AMBER;
            case TOWN_PLACER       -> new Color(180, 60, 255);
            case DUNGEON_PLACER    -> new Color(220, 100, 40);
            case SPAWN_DIFFICULTY  -> DANGER;
            case NPC_PLACER        -> PHOSPHOR2;
            default                -> PHOSPHOR;
        });
        boolean showBrush = (t == Tool.PENCIL || t == Tool.SPAWN_DIFFICULTY);
        boolean showSpawn = (t == Tool.SPAWN_DIFFICULTY);
        boolean showNpc   = (t == Tool.NPC_PLACER);

        if (brushLabel != null)             brushLabel.setVisible(showBrush);
        if (brushSizeLabel != null)         brushSizeLabel.setVisible(showBrush);
        if (spawnLabel != null)             spawnLabel.setVisible(showSpawn);
        if (spawnDifficultySpinner != null) {
            spawnDifficultySpinner.setVisible(showSpawn);
            spawnDifficultySpinner.setEnabled(showSpawn);
        }
        if (npcLabel != null)               npcLabel.setVisible(showNpc);
        if (npcTemplateCombo != null)       npcTemplateCombo.setVisible(showNpc);

        // Entry spawn controls visibility is driven by map type, not tool — keep unchanged here
        highlightActiveTool();
    }

    private void highlightActiveTool() {
        if (toolButtonMap == null) return;
        toolButtonMap.forEach((tool, btn) -> {
            boolean active = tool == currentTool;
            btn.setBackground(active ? new Color(0, 50, 30) : new Color(12, 20, 32));
            btn.setForeground(active ? PHOSPHOR : TEXT_BRIGHT);
        });
    }

    /** Vertical divider for the toolbar. */
    private JPanel vDivider() {
        JPanel d = new JPanel();
        d.setBackground(BORDER_COL);
        d.setPreferredSize(new Dimension(1, 28));
        d.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));
        return d;
    }

    // ── UI Helper methods (shared CRT style) ────────────────────────────────

    private static JLabel makeLabel(String txt, Color c) {
        JLabel l = new JLabel(txt);
        l.setForeground(c);
        l.setFont(MONO_SM);
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

    private static JButton toolBtn(String txt, Color accent) {
        JButton b = retroButton(txt);
        b.setPreferredSize(new Dimension(130, 28));
        b.setForeground(accent);
        return b;
    }

    private void setStatus(String msg) {
        if (statusLabel == null) return;
        statusLabel.setText("◈  " + msg.toUpperCase());
        if (statusTimer != null) statusTimer.stop();
        statusTimer = new javax.swing.Timer(3000, e -> updateStatus());
        statusTimer.setRepeats(false);
        statusTimer.start();
    }

    private JMenuItem createMenuItem(String text, ActionListener listener) {
        JMenuItem item = new JMenuItem(text);
        item.addActionListener(listener);
        return item;
    }

    // ── Left sidebar: preview + tile palette ────────────────────────────────
    private JPanel buildLeftSidebar() {
        JPanel outer = new JPanel(new BorderLayout(0, 0));
        outer.setBackground(PANEL_BG);
        outer.setPreferredSize(new Dimension(290, 0));
        outer.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, BORDER_COL));

        // ── Header ──
        JLabel title = makeLabel("◈  RETROFORGE  MAP  EDITOR", PHOSPHOR);
        title.setFont(MONO_MD);
        title.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 8));
        outer.add(title, BorderLayout.NORTH);

        // ── Preview panel ──
        JPanel previewWrap = new JPanel(new BorderLayout(0, 0));
        previewWrap.setBackground(PANEL_BG);
        previewWrap.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_COL));

        JLabel previewTitle = makeLabel("  MINIMAP  PREVIEW", ACCENT);
        previewTitle.setFont(MONO_XS);
        previewTitle.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        previewWrap.add(previewTitle, BorderLayout.NORTH);

        preview.setPreferredSize(new Dimension(270, 180));
        preview.setMinimumSize(new Dimension(270, 160));
        preview.setMaximumSize(new Dimension(270, 200));
        previewWrap.add(preview, BorderLayout.CENTER);

        // Compound north panel: title above preview
        JPanel northStack = new JPanel(new BorderLayout(0, 0));
        northStack.setOpaque(false);
        northStack.add(title,       BorderLayout.NORTH);
        northStack.add(previewWrap, BorderLayout.CENTER);
        outer.add(northStack, BorderLayout.NORTH);

        // ── Tile palette ──
        JLabel paletteTitle = makeLabel("  TILE  PALETTE", ACCENT);
        paletteTitle.setFont(MONO_XS);
        paletteTitle.setBorder(BorderFactory.createEmptyBorder(6, 8, 4, 8));
        paletteTitle.setOpaque(true);
        paletteTitle.setBackground(new Color(0, 20, 35));

        JScrollPane paletteScroll = new JScrollPane(palette);
        paletteScroll.setBorder(BorderFactory.createEmptyBorder());
        paletteScroll.setBackground(BG);
        paletteScroll.getViewport().setBackground(BG);
        paletteScroll.getVerticalScrollBar().setUI(new RetroScrollBarUI());

        JPanel paletteWrap = new JPanel(new BorderLayout(0, 0));
        paletteWrap.setOpaque(false);
        paletteWrap.add(paletteTitle,  BorderLayout.NORTH);
        paletteWrap.add(paletteScroll, BorderLayout.CENTER);
        outer.add(paletteWrap, BorderLayout.CENTER);

        return outer;
    }

    private void loadMap() {
        if (!confirmDiscard("opening another map")) return;

        JFileChooser fc = new JFileChooser();
        fc.setCurrentDirectory(new File("data"));
        fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("RetroForge Maps", "rfmap"));

        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                File selectedFile = fc.getSelectedFile();
                currentMap = MapData.load(selectedFile);
                currentMapFile = selectedFile;
                savePrefs();

                // Force name to match filename (this fixes the bug permanently)
                String cleanName = selectedFile.getName();
                if (cleanName.toLowerCase().endsWith(".rfmap")) {
                    cleanName = cleanName.substring(0, cleanName.length() - 6);
                }
                currentMap.name = cleanName;

                canvas.setMap(currentMap);
                preview.refresh();
                clearDirty();
                updateToolAvailability();

                JOptionPane.showMessageDialog(this,
                    "Loaded: " + currentMap.name + "\n(File: " + selectedFile.getName() + ")",
                    "Success", JOptionPane.INFORMATION_MESSAGE);

            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Failed to load map:\n" + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    public void updateZoomLabel(int tileSize) {
        if (zoomLabel != null) {
            zoomLabel.setText("ZOOM: " + (tileSize * 100 / 32) + "%");
        }
    }

    public void updateBrushLabel() {
        if (brushSizeLabel == null) return;

        brushSizeLabel.setText(canvas.getBrushSize() + "×" + canvas.getBrushSize());

        if (currentTool == Tool.SPAWN_DIFFICULTY && spawnValueLabel != null) {
            spawnValueLabel.setText("   Spawn: " + canvas.getSpawnDifficultyValue());
        } else if (spawnValueLabel != null) {
            spawnValueLabel.setText("   ");
        }
    }

    /**
     * Saves the current map, asking for a filename when it has never been saved.
     *
     * @return true when the map actually reached disk
     */
    private boolean saveMap() {
        try {
            File fileToSave;

            if (currentMapFile != null) {
                // We loaded from a real file → always save back to the same file
                fileToSave = currentMapFile;
            } else {
                // Brand new map — ask for name
                String name = JOptionPane.showInputDialog(this,
                    "Enter map name (without .rfmap):", currentMap.name);
                if (name == null || name.trim().isEmpty()) return false;

                String folder = switch (currentMap.type) {
                    case OVERWORLD -> "overworlds";
                    case DUNGEON   -> "dungeons";
                    default        -> "towns";
                };
                fileToSave = new File("data/" + folder + "/" + name.trim().toLowerCase() + ".rfmap");

                // Never overwrite an existing authored map without asking
                if (fileToSave.exists() && JOptionPane.showConfirmDialog(this,
                        fileToSave.getPath() + " already exists.\n\nOverwrite it?",
                        "File Exists", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
                    return false;
                }
            }

            fileToSave.getParentFile().mkdirs();
            writeMapSafely(fileToSave);
            currentMapFile = fileToSave;
            savePrefs();
            clearDirty();

            JOptionPane.showMessageDialog(this,
                "✅ Saved successfully!\n\nFile: " + fileToSave.getName(),
                "Map Saved", JOptionPane.INFORMATION_MESSAGE);

            preview.refresh();
            return true;
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Save failed: " + ex.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    /**
     * Writes the map to a sibling temp file and renames it over the target, so a
     * crash or a failure mid-write cannot leave the authored map truncated.
     */
    private void writeMapSafely(File target) throws java.io.IOException {
        File tmp = new File(target.getParentFile(), target.getName() + ".tmp");
        currentMap.save(tmp);
        try {
            Files.move(tmp.toPath(), target.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException ex) {
            Files.move(tmp.toPath(), target.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void newMapDialog() {
        if (!confirmDiscard("creating a new map")) return;

        JPanel p = new JPanel(new GridLayout(0, 2, 5, 5));
        JTextField nf = new JTextField("MyTown");
        JComboBox<String> tb = new JComboBox<>(new String[]{"Overworld", "Town", "Dungeon"});
        JSpinner ws = new JSpinner(new SpinnerNumberModel(140, 20, 2048, 10));
        JSpinner hs = new JSpinner(new SpinnerNumberModel(90, 20, 2048, 10));

        // Switch size defaults when type changes
        tb.addActionListener(e -> {
            switch (tb.getSelectedIndex()) {
                case 0 -> { ws.setValue(140); hs.setValue(90); }
                case 1 -> { ws.setValue(40);  hs.setValue(30); }
                case 2 -> { ws.setValue(40);  hs.setValue(40); }
            }
        });

        p.add(new JLabel("Name:"));   p.add(nf);
        p.add(new JLabel("Type:"));   p.add(tb);
        p.add(new JLabel("Width:"));  p.add(ws);
        p.add(new JLabel("Height:")); p.add(hs);

        if (JOptionPane.showConfirmDialog(this, p, "New Map", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
            MapData.MapType type = switch (tb.getSelectedIndex()) {
                case 0 -> MapData.MapType.OVERWORLD;
                case 2 -> MapData.MapType.DUNGEON;
                default -> MapData.MapType.TOWN;
            };
            currentMap = MapData.createNew(type, nf.getText(), (int) ws.getValue(), (int) hs.getValue());

            if (type == MapData.MapType.TOWN) {
                createBasicTownInterior();
            }

            // A brand-new map must not inherit the previous map's file, or the next
            // Save would silently overwrite the file we just stopped editing.
            currentMapFile = null;
            canvas.setMap(currentMap);
            preview.refresh();
            clearDirty();
            updateToolAvailability();
        }
    }

    private void createBasicTownInterior() {
        int w = currentMap.width;
        int h = currentMap.height;

        // Fill everything with floor
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                currentMap.tiles[y][x] = 'F';

        // Border walls
        for (int x = 0; x < w; x++) {
            currentMap.tiles[0][x]   = 'W';
            currentMap.tiles[h-1][x] = 'W';
        }
        for (int y = 0; y < h; y++) {
            currentMap.tiles[y][0]   = 'W';
            currentMap.tiles[y][w-1] = 'W';
        }

        // Exit door at bottom centre
        currentMap.tiles[h-1][w/2] = 'd';

        currentMap.npcs.clear();

        // Buildings need at least a 26×20 map
        if (w < 26 || h < 20) return;

        // --- SHOP (left side) ---
        int sX1 = 2, sX2 = Math.min(14, w/2 - 2);
        int sY1 = 2, sY2 = Math.min(10, h/2 - 1);
        buildRoom(sX1, sY1, sX2, sY2);
        for (int x = sX1+2; x <= sX2-2; x++)
            currentMap.tiles[sY1+2][x] = 'c';
        int shopMidX = (sX1 + sX2) / 2;
        currentMap.npcs.add(new NPC(null, "Merchant", "npcs/shopkeeper",
                NPC.Type.SHOPKEEPER,
                "Welcome! Browse my wares, traveler.",
                shopMidX, sY1 + 3));

        // --- INN (right side) ---
        int iX1 = Math.max(w/2 + 2, w - 15);
        int iX2 = w - 2;
        int iY1 = 2, iY2 = Math.min(10, h/2 - 1);
        buildRoom(iX1, iY1, iX2, iY2);
        for (int x = iX1+2; x <= iX2-2; x++)
            currentMap.tiles[iY1+2][x] = 'c';
        int innMidX = (iX1 + iX2) / 2;
        currentMap.npcs.add(new NPC(null, "Innkeeper", "npcs/innkeeper",
                NPC.Type.INNKEEPER,
                "Rest your weary bones, traveler. 10 gold a night.",
                innMidX, iY1 + 3));
    }

    /** Draws a hollow rectangle of stone walls from (x1,y1) to (x2,y2). */
    private void buildRoom(int x1, int y1, int x2, int y2) {
        for (int x = x1; x <= x2; x++) {
            currentMap.tiles[y1][x] = 'W';
            currentMap.tiles[y2][x] = 'W';
        }
        for (int y = y1; y <= y2; y++) {
            currentMap.tiles[y][x1] = 'W';
            currentMap.tiles[y][x2] = 'W';
        }
    }

    /** Called from MapCanvas double-click on an 'E' tile. */
    public void openTownFromEntrance(String townName) {
        if (!confirmDiscard("opening town '" + townName + "'")) return;

        File townFile = new File("data/towns/" + townName + ".rfmap");
        if (!townFile.exists()) {
            JOptionPane.showMessageDialog(this,
                    "Town file not found:\n" + townFile.getPath(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        try {
            currentMap = MapData.load(townFile);
            currentMapFile = townFile;
            currentMap.name = townName;
            canvas.setMap(currentMap);
            preview.refresh();
            clearDirty();
            updateToolAvailability();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to load town:\n" + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** Shows or hides overworld-only and town-only tools based on the current map type. */
    private void updateToolAvailability() {
        if (toolButtonMap == null) return;
        boolean isOverworld = currentMap.type == MapData.MapType.OVERWORLD;
        boolean isTown = currentMap.type == MapData.MapType.TOWN;

        JButton townBtn = toolButtonMap.get(Tool.TOWN_PLACER);
        if (townBtn != null) townBtn.setVisible(isOverworld);
        if (!isOverworld && currentTool == Tool.TOWN_PLACER) {
            selectTool(Tool.PENCIL);
        }

        JButton dungeonBtn = toolButtonMap.get(Tool.DUNGEON_PLACER);
        if (dungeonBtn != null) dungeonBtn.setVisible(isOverworld);
        if (!isOverworld && currentTool == Tool.DUNGEON_PLACER) {
            selectTool(Tool.PENCIL);
        }

        // Entry spawn controls — visible for TOWN maps only
        if (entryLabel    != null) entryLabel.setVisible(isTown);
        if (entryXSpinner != null) entryXSpinner.setVisible(isTown);
        if (entryYSpinner != null) entryYSpinner.setVisible(isTown);
        if (entryDivider  != null) entryDivider.setVisible(isTown);

        // Sync spinner values from the loaded map
        if (isTown && entryXSpinner != null) {
            entryXSpinner.setValue(currentMap.interiorEntryX);
            entryYSpinner.setValue(currentMap.interiorEntryY);
        }

        palette.refresh(currentMap.type);
    }

    private void resizeDialog() {
        JPanel p = new JPanel(new GridLayout(0, 2, 5, 5));
        JSpinner w = new JSpinner(new SpinnerNumberModel(currentMap.width, 20, 2048, 10));
        JSpinner h = new JSpinner(new SpinnerNumberModel(currentMap.height, 20, 2048, 10));
        p.add(new JLabel("Width:")); p.add(w);
        p.add(new JLabel("Height:")); p.add(h);

        if (JOptionPane.showConfirmDialog(this, p, "Resize Map", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
            currentMap.resize((int) w.getValue(), (int) h.getValue());
            canvas.setMap(currentMap);
            preview.refresh();
            markDirty();
            updateToolAvailability();
        }
    }

    private void playtest() {
        int confirm = JOptionPane.showConfirmDialog(this,
            "Launch Retroquest for playtesting?\n(Map will be saved automatically. RetroForge will stay open)",
            "Playtest", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            saveMap();
            javax.swing.SwingUtilities.invokeLater(() -> Retroquest.main(new String[0]));
        }
    }

    public void refreshNPCTemplateCombo() {
        if (npcTemplateCombo == null) return;
        npcTemplateCombo.removeAllItems();

        for (String key : ImageAssetRegistry.getAllSpriteKeys()) {
            if (key.startsWith("npcs/")) {
                String displayName = key.replace("npcs/", "").replace(".png", "");
                npcTemplateCombo.addItem(displayName + "  (" + key + ")");
            }
        }

        if (npcTemplateCombo.getItemCount() == 0) {
            npcTemplateCombo.addItem("townsman");
        }
    }

    public JComboBox<String> getNpcTemplateCombo() {
        return npcTemplateCombo;
    }

    private void updateStatus() {
        if (statusLabel == null) return;
        statusLabel.setText("◈  " + currentTool.getDisplayName().toUpperCase());
        statusLabel.setForeground(currentTool == Tool.SELECT ? AMBER :
            currentTool == Tool.TOWN_PLACER ? new Color(180,60,255) :
            currentTool == Tool.DUNGEON_PLACER ? new Color(220,100,40) :
            currentTool == Tool.SPAWN_DIFFICULTY ? DANGER :
            currentTool == Tool.NPC_PLACER ? PHOSPHOR2 : PHOSPHOR);
    }

    public void centerViewOn(int worldX, int worldY) {
        canvas.centerViewOn(worldX, worldY);
    }

    public MapData getCurrentMap() { return currentMap; }
    public Tool getTool() { return currentTool; }
    public void refreshAll() {
        canvas.repaint();
        preview.refresh();
    }

    public MapCanvas getCanvas() {
        return canvas;
    }

    // ── Help Guide viewer ────────────────────────────────────────────────────

    private void showHelpGuide() {
        java.io.File mdFile = new java.io.File("docs/RetroForge_Operators_Guide.md");
        String markdown;
        try {
            markdown = java.nio.file.Files.readString(mdFile.toPath());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                "Could not read docs/RetroForge_Operators_Guide.md\n" + ex.getMessage(),
                "Help", JOptionPane.ERROR_MESSAGE);
            return;
        }

        JDialog dlg = new JDialog(this, "RetroForge Operators Guide", false);
        dlg.setSize(980, 760);
        dlg.setMinimumSize(new java.awt.Dimension(700, 500));
        dlg.setLocationRelativeTo(this);
        dlg.getContentPane().setBackground(BG);
        dlg.setLayout(new BorderLayout(0, 0));

        // Title bar
        JPanel titleBar = new JPanel(new BorderLayout());
        titleBar.setBackground(new Color(8, 12, 20));
        titleBar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_COL));
        titleBar.setPreferredSize(new java.awt.Dimension(0, 42));
        JLabel titleLbl = makeLabel("◈  RETROFORGE  OPERATORS  GUIDE", PHOSPHOR3);
        titleLbl.setFont(MONO_LG);
        titleLbl.setBorder(BorderFactory.createEmptyBorder(0, 16, 0, 0));
        JLabel subLbl = makeLabel("Content creation & level design reference", TEXT_DIM);
        subLbl.setFont(MONO_XS);
        subLbl.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 16));
        titleBar.add(titleLbl, BorderLayout.WEST);
        titleBar.add(subLbl,   BorderLayout.EAST);
        dlg.add(titleBar, BorderLayout.NORTH);

        // Render markdown → HTML with CRT theme
        String html = markdownToHtml(markdown);
        javax.swing.JEditorPane pane = new javax.swing.JEditorPane("text/html", html);
        pane.setEditable(false);
        pane.setBackground(BG);
        pane.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        // Navigate TOC links internally
        pane.addHyperlinkListener(ev -> {
            if (ev.getEventType() == javax.swing.event.HyperlinkEvent.EventType.ACTIVATED) {
                String ref = ev.getDescription();
                if (ref != null && ref.startsWith("#")) {
                    // Scroll to the named anchor
                    pane.scrollToReference(ref.substring(1));
                }
            }
        });

        JScrollPane scroll = new JScrollPane(pane,
            ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setBackground(BG);
        scroll.getViewport().setBackground(BG);
        scroll.getVerticalScrollBar().setUI(new RetroScrollBarUI());
        scroll.getHorizontalScrollBar().setUI(new RetroScrollBarUI());
        dlg.add(scroll, BorderLayout.CENTER);

        // Close button bar
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 8));
        bar.setBackground(new Color(8, 12, 20));
        bar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER_COL));
        JButton closeBtn = retroButton("✕  CLOSE");
        closeBtn.setForeground(TEXT_DIM);
        closeBtn.setPreferredSize(new java.awt.Dimension(120, 30));
        closeBtn.addActionListener(e -> dlg.dispose());
        bar.add(closeBtn);
        dlg.add(bar, BorderLayout.SOUTH);

        SwingUtilities.invokeLater(() -> pane.setCaretPosition(0));
        dlg.setVisible(true);
    }

    /** Converts a subset of Markdown to CRT-themed HTML. */
    private static String markdownToHtml(String md) {
        // CRT color scheme as CSS variables
        String css =
            "body { background:#0a0c10; color:#d2e6ff; font-family:Monospaced,monospace; " +
            "  font-size:12px; margin:18px 24px; line-height:1.55; }" +
            "h1 { color:#ffc832; font-size:16px; border-bottom:1px solid #28374b; " +
            "  padding-bottom:4px; margin-top:22px; }" +
            "h2 { color:#00c8ff; font-size:14px; margin-top:20px; }" +
            "h3 { color:#00ff78; font-size:12px; margin-top:16px; }" +
            "code { background:#10141c; color:#00ff78; padding:1px 4px; " +
            "  border:1px solid #283747; }" +
            "pre { background:#10141c; color:#00ff78; padding:10px 14px; " +
            "  border:1px solid #283747; white-space:pre; overflow-x:auto; }" +
            "pre code { background:none; border:none; padding:0; }" +
            "table { border-collapse:collapse; width:100%; margin:8px 0; }" +
            "th { background:#101420; color:#00c8ff; border:1px solid #283747; " +
            "  padding:5px 10px; text-align:left; }" +
            "td { border:1px solid #1e2a38; padding:4px 10px; }" +
            "tr:nth-child(even) { background:#0d101a; }" +
            "a { color:#00c8ff; text-decoration:none; }" +
            "a:hover { text-decoration:underline; }" +
            "hr { border:none; border-top:1px solid #283747; margin:14px 0; }" +
            "li { margin:2px 0; }" +
            "blockquote { border-left:3px solid #ffa01e; margin:6px 0 6px 12px; " +
            "  padding-left:10px; color:#7890a0; }" +
            "strong { color:#ffc832; }" +
            ".toc a { color:#00ff78; }";

        StringBuilder sb = new StringBuilder();
        sb.append("<html><head><style>").append(css).append("</style></head><body>");

        String[] lines = md.split("\n", -1);
        boolean inTable = false;
        boolean inCode  = false;
        boolean inList  = false;
        boolean inOList = false;

        for (String raw : lines) {
            String line = raw;

            // Fenced code block
            if (line.startsWith("```")) {
                if (inCode) {
                    sb.append("</code></pre>");
                    inCode = false;
                } else {
                    closeListIfOpen(sb, inList, inOList);
                    inList = false; inOList = false;
                    sb.append("<pre><code>");
                    inCode = true;
                }
                continue;
            }
            if (inCode) {
                sb.append(escHtml(raw)).append("\n");
                continue;
            }

            // Close table if blank line after table
            if (inTable && line.trim().isEmpty()) {
                sb.append("</tbody></table>");
                inTable = false;
            }

            // Blank line
            if (line.trim().isEmpty()) {
                closeListIfOpen(sb, inList, inOList);
                inList = false; inOList = false;
                sb.append("<br>");
                continue;
            }

            // Horizontal rule
            if (line.matches("^---+$")) {
                sb.append("<hr>");
                continue;
            }

            // Headings
            if (line.startsWith("# ")) {
                String anchor = toAnchor(line.substring(2));
                sb.append("<h1><a name=\"").append(anchor).append("\">")
                  .append(inlineFormat(line.substring(2))).append("</a></h1>");
                continue;
            }
            if (line.startsWith("## ")) {
                String anchor = toAnchor(line.substring(3));
                sb.append("<h2><a name=\"").append(anchor).append("\">")
                  .append(inlineFormat(line.substring(3))).append("</a></h2>");
                continue;
            }
            if (line.startsWith("### ")) {
                String anchor = toAnchor(line.substring(4));
                sb.append("<h3><a name=\"").append(anchor).append("\">")
                  .append(inlineFormat(line.substring(4))).append("</a></h3>");
                continue;
            }

            // Blockquote
            if (line.startsWith("> ")) {
                sb.append("<blockquote>").append(inlineFormat(line.substring(2))).append("</blockquote>");
                continue;
            }

            // Table row
            if (line.startsWith("|")) {
                String[] cells = line.split("\\|", -1);
                // Skip separator row
                if (line.matches("\\|[-| :]+\\|")) {
                    continue;
                }
                if (!inTable) {
                    sb.append("<table><thead><tr>");
                    for (int c = 1; c < cells.length - 1; c++) {
                        sb.append("<th>").append(inlineFormat(cells[c].trim())).append("</th>");
                    }
                    sb.append("</tr></thead><tbody>");
                    inTable = true;
                } else {
                    sb.append("<tr>");
                    for (int c = 1; c < cells.length - 1; c++) {
                        sb.append("<td>").append(inlineFormat(cells[c].trim())).append("</td>");
                    }
                    sb.append("</tr>");
                }
                continue;
            }

            if (inTable) {
                sb.append("</tbody></table>");
                inTable = false;
            }

            // Unordered list
            if (line.matches("^[\\-\\*] .+")) {
                if (!inList) {
                    closeListIfOpen(sb, false, inOList);
                    inOList = false;
                    sb.append("<ul>");
                    inList = true;
                }
                sb.append("<li>").append(inlineFormat(line.substring(2))).append("</li>");
                continue;
            }

            // Ordered list
            if (line.matches("^\\d+\\. .+")) {
                if (!inOList) {
                    closeListIfOpen(sb, inList, false);
                    inList = false;
                    sb.append("<ol>");
                    inOList = true;
                }
                sb.append("<li>").append(inlineFormat(line.replaceFirst("^\\d+\\. ", ""))).append("</li>");
                continue;
            }

            // Regular paragraph / continuation
            closeListIfOpen(sb, inList, inOList);
            inList = false; inOList = false;
            sb.append("<p>").append(inlineFormat(line)).append("</p>");
        }

        if (inTable)  sb.append("</tbody></table>");
        if (inCode)   sb.append("</code></pre>");
        closeListIfOpen(sb, inList, inOList);

        sb.append("</body></html>");
        return sb.toString();
    }

    private static void closeListIfOpen(StringBuilder sb, boolean inList, boolean inOList) {
        if (inList)  sb.append("</ul>");
        if (inOList) sb.append("</ol>");
    }

    /** Apply inline markdown: bold, inline code, links. */
    private static String inlineFormat(String s) {
        s = escHtml(s);
        // Bold **text**
        s = s.replaceAll("\\*\\*(.+?)\\*\\*", "<strong>$1</strong>");
        // Italic *text*
        s = s.replaceAll("\\*(.+?)\\*", "<em>$1</em>");
        // Inline code `text`
        s = s.replaceAll("`([^`]+)`", "<code>$1</code>");
        // Links [text](#anchor) or [text](url)
        s = s.replaceAll("\\[([^\\]]+)\\]\\(([^)]+)\\)", "<a href=\"$2\">$1</a>");
        return s;
    }

    private static String escHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** Converts a heading string to a lowercase hyphenated anchor id. */
    private static String toAnchor(String heading) {
        return heading.toLowerCase()
            .replaceAll("[^a-z0-9\\s-]", "")
            .trim()
            .replaceAll("\\s+", "-");
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(RetroForge::new);
    }
}

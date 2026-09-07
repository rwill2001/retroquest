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
import static io.cannonforge.retroquest.editor.EditorTheme.PHOSPHOR3;
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
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.plaf.basic.BasicSplitPaneDivider;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import io.cannonforge.retroquest.model.MapData;
import io.cannonforge.retroquest.model.God;
import io.cannonforge.retroquest.model.Item;
import io.cannonforge.retroquest.model.Monster;
import io.cannonforge.retroquest.model.TileDefinition;
import io.cannonforge.retroquest.model.TileEffect;
import io.cannonforge.retroquest.registry.ImageAssetRegistry;
import io.cannonforge.retroquest.registry.ItemRegistry;
import io.cannonforge.retroquest.registry.MonsterRegistry;
import io.cannonforge.retroquest.registry.TileRegistry;

@SuppressWarnings("serial")
public class TileEditor extends JDialog {

    // ── State ─────────────────────────────────────────────────────────────────
    private Color selectedColor = PHOSPHOR;
    private boolean loadingTile = false;  // suppresses listeners during programmatic field population

    // ── Tree ──────────────────────────────────────────────────────────────────
    private final DefaultMutableTreeNode treeRoot  = new DefaultMutableTreeNode("Tiles");
    private final DefaultTreeModel       treeModel = new DefaultTreeModel(treeRoot);
    private final JTree                  tileTree  = new JTree(treeModel);

    // ── Basic fields ──────────────────────────────────────────────────────────
    private final JTextField       idField       = darkField(5);
    private final JTextField       nameField     = darkField(20);
    private final JComboBox<String> categoryCombo = darkCombo(new String[]{"Overworld","Town","Dungeon","Special","Custom"});
    private final JComboBox<String> spriteCombo   = new JComboBox<>();
    private final JCheckBox        walkableCheck  = darkCheckBox("Walkable");

    // ── Gameplay fields ───────────────────────────────────────────────────────
    private final JCheckBox isLiquidCheck        = darkCheckBox("Is Liquid");
    private final JCheckBox blocksVisionCheck    = darkCheckBox("Blocks Vision");
    private final JCheckBox isSafeZoneCheck      = darkCheckBox("Safe Zone");
    private final JSpinner  lightRadiusSpinner   = darkSpinner(0,   0,  8,   1);

    // ── Advanced fields ───────────────────────────────────────────────────────
    private final JComboBox<String> stepSoundCombo = darkCombo(new String[]{
        "step_default","step_grass","step_stone","step_water","step_lava"});
    private final JSpinner  encounterRateSpinner = new JSpinner(new SpinnerNumberModel(1.0, 0.0, 5.0, 0.1));
    private final JTextArea descriptionArea      = darkTextArea(4, 30);

    // ── Spawns fields ─────────────────────────────────────────────────────────
    private final javax.swing.table.DefaultTableModel spawnTableModel =
        new javax.swing.table.DefaultTableModel(new String[]{"Monster ID", "Weight"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return true; }
            @Override public Class<?> getColumnClass(int c) {
                return c == 1 ? Integer.class : String.class;
            }
        };
    private final JTable spawnTable = new JTable(spawnTableModel);

    // ── Effects fields ────────────────────────────────────────────────────────
    private final JComboBox<String> effectTypeCombo = darkCombo(new String[]{
        "none","teleport","damage","island_portal","encounter","trap_once","set_tile","locked_door",
        "toggle_door","pressure_plate","shrine","treasure","ambush",
        "geyser","wind_current","lore"});
    private final JComboBox<String> mapCombo           = new JComboBox<>();
    private final JTextField        xField             = darkField(5);
    private final JTextField        yField             = darkField(5);
    private final JTextField        amountField        = darkField(5);
    private final JComboBox<String>  tileIdCombo        = new JComboBox<>();
    private final JComboBox<String> requiredKeyCombo   = darkCombo(new String[]{"(none)"});
    private final JTextField        lockedMessageField = darkField(30);
    private final JTextArea         messageArea        = darkTextArea(3, 25);
    // toggle_door fields
    private final JComboBox<String> openCharCombo      = new JComboBox<>();
    private final JComboBox<String> closeCharCombo     = new JComboBox<>();
    // pressure_plate fields
    private final JComboBox<String> activateCharCombo  = new JComboBox<>();
    private final JComboBox<String> deactivateCharCombo = new JComboBox<>();
    private final JCheckBox         onceCheck          = darkCheckBox("Once (single trigger)");

    // ── New step-effect fields (shrine, treasure, ambush, geyser, wind_current, lore)
    private final JComboBox<String> godCombo           = darkCombo(buildGodComboItems());
    private final JSpinner          favorSpinner       = darkSpinner(5, 0, 100, 1);
    private final JSpinner          xpSpinner          = darkSpinner(0, 0, 9999, 10);
    private final JSpinner          chanceSpinner      = darkSpinner(50, 0, 100, 5);
    private final JComboBox<String> directionCombo     = darkCombo(new String[]{"(none)", "north", "south", "east", "west"});
    private final JSpinner          distanceSpinner    = darkSpinner(3, 0, 20, 1);
    private final JSpinner          goldSpinner        = darkSpinner(0, 0, 99999, 10);
    private final JSpinner          tierSpinner        = darkSpinner(1, 0, 10, 1);
    private final JSpinner          lootTierSpinner    = darkSpinner(0, 0, 10, 1);
    private final JComboBox<String> itemIdCombo         = darkCombo(new String[]{"(none)"});
    private final JComboBox<String> requiredItemIdCombo = darkCombo(new String[]{"(none)"});
    private final JComboBox<String> monsterIdCombo      = darkCombo(new String[]{"(none)"});
    private final JComboBox<String> lootItemIdCombo     = darkCombo(new String[]{"(none)"});
    private final JComboBox<String> discoveredTileCombo = new JComboBox<>();
    private final JCheckBox         oneTimeCheck       = darkCheckBox("One-time (read once)");

    private static String[] buildGodComboItems() {
        String[] items = new String[God.values().length + 1];
        items[0] = "(none)";
        for (int i = 0; i < God.values().length; i++) {
            God g = God.values()[i];
            items[i + 1] = g.name() + " — " + g.displayName;
        }
        return items;
    }

    // ── Color swatch button ───────────────────────────────────────────────────
    private JButton colorBtn;

    // ── Sprite preview ────────────────────────────────────────────────────────
    private BufferedImage previewImage = null;
    private final JPanel spritePreviewPanel = new JPanel() {
        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            int cs = 8;
            for (int py = 0; py < getHeight(); py += cs)
                for (int px = 0; px < getWidth(); px += cs) {
                    g2.setColor((px/cs + py/cs) % 2 == 0
                        ? new Color(40, 44, 52) : new Color(28, 32, 40));
                    g2.fillRect(px, py, cs, cs);
                }
            if (previewImage != null) {
                int iw = previewImage.getWidth(), ih = previewImage.getHeight();
                int pw = getWidth()-8, ph = getHeight()-8;
                float scale = Math.min((float)pw/iw, (float)ph/ih);
                int dw=(int)(iw*scale), dh=(int)(ih*scale);
                int ox=(getWidth()-dw)/2, oy=(getHeight()-dh)/2;
                g2.drawImage(previewImage, ox, oy, dw, dh, null);
                g2.setColor(new Color(PHOSPHOR3.getRed(), PHOSPHOR3.getGreen(), PHOSPHOR3.getBlue(), 80));
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawRect(ox-1, oy-1, dw+2, dh+2);
            } else {
                g2.setColor(TEXT_DIM); g2.setFont(MONO_XS);
                String msg = "NO SPRITE";
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(msg,(getWidth()-fm.stringWidth(msg))/2, getHeight()/2+fm.getAscent()/2);
            }
            g2.setColor(BORDER_COL); g2.setStroke(new BasicStroke(1f));
            g2.drawRect(0, 0, getWidth()-1, getHeight()-1);
        }
    };

    private JLabel statusLabel;

    // ── Constructor ───────────────────────────────────────────────────────────
    public TileEditor(RetroForge retroForge) { this(retroForge, true); }

    /**
     * @param show false builds the dialog without showing it — the offscreen entry point
     *        {@code RetroRecorder} uses to film this editor. It is modal, so a recorder that
     *        used the public constructor would block forever on the calling thread.
     */
    public TileEditor(RetroForge retroForge, boolean show) {
        super(retroForge, "RETROQUEST  \u00b7  TILE  EDITOR", true);

        // Style dynamic combos
        for (JComboBox<?> cb : new JComboBox[]{spriteCombo, mapCombo}) {
            cb.setBackground(PANEL_BG);
            cb.setForeground(TEXT_BRIGHT);
            cb.setFont(MONO_SM);
        }
        // encounterRate spinner
        encounterRateSpinner.setFont(MONO_SM);
        JComponent ed = encounterRateSpinner.getEditor();
        if (ed instanceof JSpinner.DefaultEditor de) {
            de.getTextField().setBackground(new Color(8,12,20));
            de.getTextField().setForeground(PHOSPHOR);
            de.getTextField().setFont(MONO_SM);
        }

        buildUI();
        refreshTileTree();
        setSize(1200, 756);
        setMinimumSize(new Dimension(980, 594));
        setLocationRelativeTo(retroForge);
        refreshMapList();

        // Ctrl+S saves
        getRootPane().registerKeyboardAction(
            e -> saveCurrentTile(),
            KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK),
            JComponent.WHEN_IN_FOCUSED_WINDOW);

        setVisible(show);
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
        return EditorTheme.buildTitleBar("TILE  EDITOR", PHOSPHOR3, "RETROQUEST  TILE  DEFINITION  EDITOR");
    }

    private JPanel buildMainArea() {
        JPanel p = new JPanel(new BorderLayout(0, 0));
        p.setBackground(BG);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                                          buildTileListPanel(),
                                          buildRightPanel());
        split.setDividerLocation(340);
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

    // ── Tile list (left) ──────────────────────────────────────────────────────
    private JPanel buildTileListPanel() {
        JPanel outer = new JPanel(new BorderLayout(0, 0));
        outer.setBackground(PANEL_BG);
        outer.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, BORDER_COL));
        outer.setPreferredSize(new Dimension(340, 0));

        JLabel hdr = makeLabel("\u25c8  ALL  TILE  DEFINITIONS", PHOSPHOR3);
        hdr.setFont(MONO_MD);
        hdr.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 8));
        outer.add(hdr, BorderLayout.NORTH);

        tileTree.setRootVisible(false);
        tileTree.setShowsRootHandles(true);
        tileTree.setBackground(BG);
        tileTree.setForeground(TEXT_BRIGHT);
        tileTree.setFont(MONO_SM);
        tileTree.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
        tileTree.setRowHeight(26);
        tileTree.setCellRenderer(new TileCellRenderer());
        tileTree.getSelectionModel().setSelectionMode(
            TreeSelectionModel.SINGLE_TREE_SELECTION);
        tileTree.addTreeSelectionListener(e -> loadSelectedTile());

        JScrollPane sp = new JScrollPane(tileTree);
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
        JButton newBtn    = bigButton("\u2736  NEW TILE", PHOSPHOR,  new Color(0, 40, 20));
        JButton deleteBtn = bigButton("\u2715  DELETE",   DANGER,    new Color(40,  0,  0));
        newBtn.addActionListener(e    -> createNewTile());
        deleteBtn.addActionListener(e -> deleteSelected());
        btns.add(newBtn); btns.add(deleteBtn);
        outer.add(btns, BorderLayout.SOUTH);

        return outer;
    }

    // ── Right panel: tabs + save ──────────────────────────────────────────────
    private JPanel buildRightPanel() {
        JPanel outer = new JPanel(new BorderLayout(0, 0));
        outer.setBackground(PANEL_BG);

        JLabel hdr = makeLabel("\u25c8  TILE  PROPERTIES", PHOSPHOR3);
        hdr.setFont(MONO_MD);
        hdr.setBorder(BorderFactory.createEmptyBorder(10, 16, 10, 8));
        outer.add(hdr, BorderLayout.NORTH);

        JTabbedPane tabs = buildStyledTabs();
        outer.add(tabs, BorderLayout.CENTER);

        // Save bar
        JPanel savebar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 8));
        savebar.setBackground(new Color(8, 12, 20));
        savebar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER_COL));

        JLabel hint = makeLabel("Ctrl+S to save", TEXT_DIM);
        hint.setFont(MONO_XS);
        savebar.add(hint);

        JButton saveBtn = bigButton("\ud83d\udcbe  SAVE  TILE", AMBER, new Color(40, 25, 0));
        saveBtn.setPreferredSize(new Dimension(190, 34));
        saveBtn.addActionListener(e -> saveCurrentTile());
        savebar.add(saveBtn);

        outer.add(savebar, BorderLayout.SOUTH);

        // Wire listeners
        categoryCombo.addActionListener(e -> refreshSpriteCombo());
        spriteCombo.addActionListener(e -> updateSpritePreview());

        return outer;
    }

    // ── Tabbed pane ───────────────────────────────────────────────────────────
    private JTabbedPane buildStyledTabs() {
        JTabbedPane tabs = new JTabbedPane();
        tabs.setBackground(PANEL_BG);
        tabs.setForeground(TEXT_BRIGHT);
        tabs.setFont(MONO_MD);
        tabs.setBorder(BorderFactory.createEmptyBorder());
        tabs.setUI(new javax.swing.plaf.basic.BasicTabbedPaneUI() {
            @Override protected void installDefaults() {
                super.installDefaults();
                highlight = BORDER_COL;
                lightHighlight = BORDER_COL;
                shadow = BORDER_COL;
                darkShadow = BORDER_COL;
                focus = PHOSPHOR3;
            }
            @Override protected void paintTabBackground(Graphics g, int tabPlacement,
                    int tabIndex, int x, int y, int w, int h, boolean isSelected) {
                g.setColor(isSelected ? new Color(30, 28, 5) : new Color(8, 12, 20));
                g.fillRect(x, y, w, h);
            }
            @Override protected void paintTabBorder(Graphics g, int tabPlacement,
                    int tabIndex, int x, int y, int w, int h, boolean isSelected) {
                g.setColor(isSelected ? PHOSPHOR3 : BORDER_COL);
                g.drawRect(x, y, w-1, h-1);
            }
            @Override protected void paintContentBorder(Graphics g, int tabPlacement, int selectedIndex) {
                g.setColor(BORDER_COL);
                Rectangle bounds = tabPane.getBounds();
                g.drawRect(0, 0, bounds.width-1, bounds.height-1);
            }
        });

        tabs.addTab("BASIC",    buildBasicTab());
        tabs.addTab("GAMEPLAY", buildGameplayTab());
        tabs.addTab("ADVANCED", buildAdvancedTab());
        tabs.addTab("EFFECTS",  buildEffectsTab());
        tabs.addTab("SPAWNS",   buildSpawnsTab());

        // Colour tab labels
        tabs.setForegroundAt(0, PHOSPHOR);
        tabs.setForegroundAt(1, PHOSPHOR2);
        tabs.setForegroundAt(2, PHOSPHOR3);
        tabs.setForegroundAt(3, AMBER);
        tabs.setForegroundAt(4, new Color(160, 100, 255));

        return tabs;
    }

    // ── BASIC tab ─────────────────────────────────────────────────────────────
    private JPanel buildBasicTab() {
        JPanel p = new JPanel(new BorderLayout(16, 0));
        p.setBackground(PANEL_BG);
        p.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));

        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(PANEL_BG);

        GridBagConstraints gl = gbc(0, GridBagConstraints.WEST);
        GridBagConstraints gf = gbc(1, GridBagConstraints.HORIZONTAL);

        int row = 0;

        // ID
        gl.gridy = gf.gridy = row++;
        form.add(sectionLabel("ID  (char):"), gl); form.add(idField, gf);

        // Name
        gl.gridy = gf.gridy = row++;
        form.add(sectionLabel("NAME:"), gl); form.add(nameField, gf);

        // Category
        gl.gridy = gf.gridy = row++;
        form.add(sectionLabel("CATEGORY:"), gl); form.add(categoryCombo, gf);

        // Sprite
        gl.gridy = gf.gridy = row++;
        form.add(sectionLabel("SPRITE:"), gl); form.add(spriteCombo, gf);

        // Editor color
        gl.gridy = gf.gridy = row++;
        form.add(sectionLabel("EDITOR  COLOR:"), gl);
        colorBtn = new JButton("  ") {
            @Override protected void paintComponent(Graphics g) {
                g.setColor(selectedColor);
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setColor(selectedColor.darker());
                g.drawRect(0, 0, getWidth()-1, getHeight()-1);
                g.setColor(TEXT_BRIGHT);
                g.setFont(MONO_XS);
                FontMetrics fm = g.getFontMetrics();
                String hex = String.format("#%02X%02X%02X",
                    selectedColor.getRed(), selectedColor.getGreen(), selectedColor.getBlue());
                g.drawString(hex, (getWidth()-fm.stringWidth(hex))/2,
                    (getHeight()+fm.getAscent()-fm.getDescent())/2);
            }
        };
        colorBtn.setPreferredSize(new Dimension(120, 28));
        colorBtn.setFocusPainted(false);
        colorBtn.setBorderPainted(false);
        colorBtn.addActionListener(e -> {
            Color c = JColorChooser.showDialog(this, "Choose Tile Color", selectedColor);
            if (c != null) { selectedColor = c; colorBtn.repaint(); }
        });
        form.add(colorBtn, gf);

        // Walkable
        gl.gridy = gf.gridy = row++;
        form.add(sectionLabel("FLAGS:"), gl);
        walkableCheck.setSelected(true);
        form.add(walkableCheck, gf);

        // Spacer
        GridBagConstraints sp = new GridBagConstraints();
        sp.gridx = 0; sp.gridy = row; sp.gridwidth = 2;
        sp.weighty = 1.0; sp.fill = GridBagConstraints.VERTICAL;
        form.add(Box.createVerticalGlue(), sp);

        // Preview panel
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

        JButton editBtn = bigButton("\u270f  EDIT  SPRITE", PHOSPHOR3, new Color(35, 28, 0));
        editBtn.addActionListener(e -> openSpriteEditor());
        previewWrap.add(editBtn, BorderLayout.SOUTH);

        p.add(form,        BorderLayout.CENTER);
        p.add(previewWrap, BorderLayout.EAST);
        return p;
    }

    // ── GAMEPLAY tab ──────────────────────────────────────────────────────────
    private JPanel buildGameplayTab() {
        JPanel p = tabPanel();

        GridBagConstraints gl = gbc(0, GridBagConstraints.WEST);
        GridBagConstraints gf = gbc(1, GridBagConstraints.HORIZONTAL);

        int row = 0;

        gl.gridy = gf.gridy = row++;
        p.add(sectionLabel("LIGHT  RADIUS:"), gl);    p.add(lightRadiusSpinner, gf);

        gl.gridy = gf.gridy = row++;
        p.add(sectionLabel("FLAGS:"), gl);
        JPanel flagRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        flagRow.setOpaque(false);
        flagRow.add(isLiquidCheck);
        flagRow.add(blocksVisionCheck);
        flagRow.add(isSafeZoneCheck);
        p.add(flagRow, gf);

        return wrapTab(p, row);
    }

    // ── ADVANCED tab ──────────────────────────────────────────────────────────
    private JPanel buildAdvancedTab() {
        JPanel p = tabPanel();

        GridBagConstraints gl = gbc(0, GridBagConstraints.WEST);
        GridBagConstraints gf = gbc(1, GridBagConstraints.HORIZONTAL);

        int row = 0;

        gl.gridy = gf.gridy = row++;
        p.add(sectionLabel("STEP  SOUND:"), gl);     p.add(stepSoundCombo, gf);

        gl.gridy = gf.gridy = row++;
        p.add(sectionLabel("ENCOUNTER  RATE:"), gl); p.add(encounterRateSpinner, gf);

        // Description
        gl.gridy = gf.gridy = row;
        gl.anchor = GridBagConstraints.NORTHWEST;
        p.add(sectionLabel("DESCRIPTION:"), gl);

        JScrollPane descScroll = new JScrollPane(descriptionArea);
        descScroll.setBorder(BorderFactory.createLineBorder(BORDER_COL));
        descScroll.getViewport().setBackground(BG);
        descScroll.getVerticalScrollBar().setUI(new RetroScrollBarUI());
        gf.fill = GridBagConstraints.BOTH;
        gf.weighty = 1.0;
        p.add(descScroll, gf);

        JPanel wrap = new JPanel(new BorderLayout(0, 0));
        wrap.setBackground(PANEL_BG);
        wrap.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
        wrap.add(p, BorderLayout.CENTER);
        return wrap;
    }

    // ── SPAWNS tab ────────────────────────────────────────────────────────────
    private JPanel buildSpawnsTab() {
        JPanel p = new JPanel(new BorderLayout(0, 10));
        p.setBackground(PANEL_BG);
        p.setBorder(BorderFactory.createEmptyBorder(14, 16, 12, 16));

        // Description
        JPanel descPanel = new JPanel(new GridLayout(3, 1, 0, 2));
        descPanel.setBackground(PANEL_BG);
        descPanel.add(sectionLabel("MONSTER SPAWN WEIGHTS FOR THIS TILE:"));
        JLabel d1 = makeLabel("Monster IDs must match entries in the Monster Registry.", TEXT_DIM);
        JLabel d2 = makeLabel("Default weight is 1.  Weight=4 means 4\u00d7 more likely.", TEXT_DIM);
        d1.setFont(MONO_XS); d2.setFont(MONO_XS);
        descPanel.add(d1); descPanel.add(d2);
        p.add(descPanel, BorderLayout.NORTH);

        // Table
        spawnTable.setBackground(new Color(8, 12, 20));
        spawnTable.setForeground(TEXT_BRIGHT);
        spawnTable.setFont(MONO_SM);
        spawnTable.setRowHeight(24);
        spawnTable.setShowGrid(false);
        spawnTable.setIntercellSpacing(new Dimension(0, 1));
        spawnTable.setSelectionBackground(SEL_BG);
        spawnTable.setSelectionForeground(PHOSPHOR2);
        spawnTable.getTableHeader().setBackground(new Color(16, 20, 32));
        spawnTable.getTableHeader().setForeground(new Color(160, 100, 255));
        spawnTable.getTableHeader().setFont(MONO_SM);
        spawnTable.getColumnModel().getColumn(0).setPreferredWidth(280);
        spawnTable.getColumnModel().getColumn(1).setPreferredWidth(80);

        // Style the cell editor fields
        JTextField cellEditor = new JTextField();
        cellEditor.setBackground(new Color(8, 12, 20));
        cellEditor.setForeground(PHOSPHOR);
        cellEditor.setFont(MONO_SM);
        cellEditor.setCaretColor(PHOSPHOR);
        cellEditor.setBorder(BorderFactory.createLineBorder(PHOSPHOR, 1));
        spawnTable.setDefaultEditor(String.class,
            new javax.swing.DefaultCellEditor(cellEditor));

        JScrollPane sp = new JScrollPane(spawnTable);
        sp.setBorder(BorderFactory.createLineBorder(BORDER_COL, 1));
        sp.setBackground(new Color(8, 12, 20));
        sp.getViewport().setBackground(new Color(8, 12, 20));
        sp.getVerticalScrollBar().setUI(new RetroScrollBarUI());
        p.add(sp, BorderLayout.CENTER);

        // Buttons
        JPanel btns = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        btns.setBackground(PANEL_BG);

        JButton addBtn = bigButton("\u271a  ADD ROW",    new Color(160, 100, 255), new Color(28, 10, 50));
        JButton remBtn = bigButton("\u2715  REMOVE ROW", DANGER,                  new Color(40,  0,  0));
        addBtn.setPreferredSize(new Dimension(150, 30));
        remBtn.setPreferredSize(new Dimension(150, 30));

        addBtn.addActionListener(e -> {
            // Stop any active edit first so the new row gets focus
            if (spawnTable.isEditing()) spawnTable.getCellEditor().stopCellEditing();
            spawnTableModel.addRow(new Object[]{"", 1});
            int newRow = spawnTableModel.getRowCount() - 1;
            spawnTable.setRowSelectionInterval(newRow, newRow);
            spawnTable.editCellAt(newRow, 0);
        });
        remBtn.addActionListener(e -> {
            if (spawnTable.isEditing()) spawnTable.getCellEditor().stopCellEditing();
            int row = spawnTable.getSelectedRow();
            if (row >= 0) spawnTableModel.removeRow(row);
        });

        btns.add(addBtn); btns.add(remBtn);
        p.add(btns, BorderLayout.SOUTH);

        return p;
    }

    // ── EFFECTS tab ───────────────────────────────────────────────────────────
    private JPanel buildEffectsTab() {
        JPanel p = tabPanel();

        GridBagConstraints gl = gbc(0, GridBagConstraints.WEST);
        GridBagConstraints gf = gbc(1, GridBagConstraints.HORIZONTAL);

        int row = 0;

        gl.gridy = gf.gridy = row++;
        p.add(sectionLabel("EFFECT  TYPE:"), gl); p.add(effectTypeCombo, gf);
        effectTypeCombo.addActionListener(e -> {
            if (loadingTile) return;
            if ("locked_door".equals(effectTypeCombo.getSelectedItem())) {
                walkableCheck.setSelected(false);
            }
        });

        gl.gridy = gf.gridy = row++;
        p.add(sectionLabel("TARGET  MAP:"), gl);  p.add(mapCombo, gf);

        gl.gridy = gf.gridy = row++;
        p.add(sectionLabel("DEST  X:"), gl);      p.add(xField, gf);

        gl.gridy = gf.gridy = row++;
        p.add(sectionLabel("DEST  Y:"), gl);      p.add(yField, gf);

        gl.gridy = gf.gridy = row++;
        p.add(sectionLabel("AMOUNT:"), gl);       p.add(amountField, gf);

        gl.gridy = gf.gridy = row++;
        p.add(sectionLabel("TILE  ID:"), gl);     p.add(tileIdCombo, gf);

        gl.gridy = gf.gridy = row++;
        p.add(sectionLabel("REQUIRED  KEY:"), gl); p.add(requiredKeyCombo, gf);

        gl.gridy = gf.gridy = row++;
        p.add(sectionLabel("LOCKED  MSG:"), gl);  p.add(lockedMessageField, gf);

        gl.gridy = gf.gridy = row++;
        p.add(sectionLabel("OPEN  CHAR:"), gl);   p.add(openCharCombo, gf);

        gl.gridy = gf.gridy = row++;
        p.add(sectionLabel("CLOSE  CHAR:"), gl);  p.add(closeCharCombo, gf);

        gl.gridy = gf.gridy = row++;
        p.add(sectionLabel("ACTIVATE  CHAR:"), gl);   p.add(activateCharCombo, gf);

        gl.gridy = gf.gridy = row++;
        p.add(sectionLabel("DEACTIVATE  CHAR:"), gl); p.add(deactivateCharCombo, gf);

        gl.gridy = gf.gridy = row++;
        p.add(sectionLabel("ONCE:"), gl); p.add(onceCheck, gf);

        // ── New step-effect params ─────────────────────────────────────────────
        gl.gridy = gf.gridy = row++;
        p.add(sectionLabel("GOD  (shrine):"), gl); p.add(godCombo, gf);

        gl.gridy = gf.gridy = row++;
        p.add(sectionLabel("FAVOR  (shrine):"), gl); p.add(favorSpinner, gf);

        gl.gridy = gf.gridy = row++;
        p.add(sectionLabel("XP  REWARD:"), gl); p.add(xpSpinner, gf);

        gl.gridy = gf.gridy = row++;
        p.add(sectionLabel("CHANCE  %  (geyser):"), gl); p.add(chanceSpinner, gf);

        gl.gridy = gf.gridy = row++;
        p.add(sectionLabel("DIRECTION  (wind):"), gl); p.add(directionCombo, gf);

        gl.gridy = gf.gridy = row++;
        p.add(sectionLabel("DISTANCE  (wind):"), gl); p.add(distanceSpinner, gf);

        gl.gridy = gf.gridy = row++;
        p.add(sectionLabel("GOLD  (treasure):"), gl); p.add(goldSpinner, gf);

        gl.gridy = gf.gridy = row++;
        p.add(sectionLabel("TIER  (treasure):"), gl); p.add(tierSpinner, gf);

        gl.gridy = gf.gridy = row++;
        p.add(sectionLabel("LOOT  TIER  (ambush):"), gl); p.add(lootTierSpinner, gf);

        gl.gridy = gf.gridy = row++;
        p.add(sectionLabel("ITEM  ID  (treasure):"), gl); p.add(itemIdCombo, gf);

        gl.gridy = gf.gridy = row++;
        p.add(sectionLabel("REQ  ITEM  ID:"), gl); p.add(requiredItemIdCombo, gf);

        gl.gridy = gf.gridy = row++;
        p.add(sectionLabel("MONSTER  ID  (ambush):"), gl); p.add(monsterIdCombo, gf);

        gl.gridy = gf.gridy = row++;
        p.add(sectionLabel("LOOT  ITEM  ID:"), gl); p.add(lootItemIdCombo, gf);

        gl.gridy = gf.gridy = row++;
        p.add(sectionLabel("DISCOVERED  TILE:"), gl); p.add(discoveredTileCombo, gf);

        gl.gridy = gf.gridy = row++;
        p.add(sectionLabel("ONE-TIME  (lore):"), gl); p.add(oneTimeCheck, gf);

        gl.gridy = gf.gridy = row;
        gl.anchor = GridBagConstraints.NORTHWEST;
        p.add(sectionLabel("MESSAGE:"), gl);

        JScrollPane msgScroll = new JScrollPane(messageArea);
        msgScroll.setBorder(BorderFactory.createLineBorder(BORDER_COL));
        msgScroll.getViewport().setBackground(BG);
        msgScroll.getVerticalScrollBar().setUI(new RetroScrollBarUI());
        gf.fill = GridBagConstraints.BOTH;
        gf.weighty = 1.0;
        p.add(msgScroll, gf);

        // The effects form has many rows — wrap in a scroll pane so nothing is clipped
        // on smaller screens. We deliberately skip wrapTab's vertical glue spacer
        // (rows already fill via the message row's weighty=1.0).
        JScrollPane formScroll = new JScrollPane(p);
        formScroll.setBorder(BorderFactory.createEmptyBorder());
        formScroll.setBackground(PANEL_BG);
        formScroll.getViewport().setBackground(PANEL_BG);
        formScroll.getVerticalScrollBar().setUI(new RetroScrollBarUI());
        formScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        formScroll.getVerticalScrollBar().setUnitIncrement(16);

        JPanel container = new JPanel(new BorderLayout());
        container.setBackground(PANEL_BG);
        container.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
        container.add(formScroll, BorderLayout.CENTER);
        return container;
    }

    // ── Status bar ────────────────────────────────────────────────────────────
    private JPanel buildStatusBar() {
        statusLabel = new JLabel();
        return EditorTheme.buildStatusBar(statusLabel, "Ctrl+S  save  |  Select tile to edit  ");
    }

    // ── Business logic ────────────────────────────────────────────────────────
    private void refreshTileTree() {
        treeRoot.removeAllChildren();
        String[] ORDER = {"Overworld","Town","Dungeon","Special","Custom","System"};
        for (String cat : ORDER) {
            List<TileDefinition> tiles = TileRegistry.getAllTiles().stream()
                .filter(t -> cat.equalsIgnoreCase(t.getCategory()))
                .sorted(java.util.Comparator.comparing(TileDefinition::getName, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());
            if (tiles.isEmpty()) continue;
            DefaultMutableTreeNode catNode = new DefaultMutableTreeNode(cat);
            if (cat.equals("Town")) {
                List<TileDefinition> letters = tiles.stream()
                    .filter(t -> t.getId() >= '\uE041' && t.getId() <= '\uE05A')
                    .collect(Collectors.toList());
                List<TileDefinition> nonLetters = tiles.stream()
                    .filter(t -> t.getId() < '\uE041' || t.getId() > '\uE05A')
                    .collect(Collectors.toList());
                for (TileDefinition t : nonLetters)
                    catNode.add(new DefaultMutableTreeNode(t));
                if (!letters.isEmpty()) {
                    DefaultMutableTreeNode lettersNode = new DefaultMutableTreeNode("Letters (A\u2013Z)");
                    for (TileDefinition t : letters)
                        lettersNode.add(new DefaultMutableTreeNode(t));
                    catNode.add(lettersNode);
                }
            } else {
                for (TileDefinition t : tiles)
                    catNode.add(new DefaultMutableTreeNode(t));
            }
            treeRoot.add(catNode);
        }
        treeModel.reload();
        for (int i = 0; i < tileTree.getRowCount(); i++) tileTree.expandRow(i);
        collapseLetterGroup();
    }

    private void collapseLetterGroup() {
        for (int i = 0; i < treeRoot.getChildCount(); i++) {
            DefaultMutableTreeNode catNode = (DefaultMutableTreeNode) treeRoot.getChildAt(i);
            for (int j = 0; j < catNode.getChildCount(); j++) {
                DefaultMutableTreeNode child = (DefaultMutableTreeNode) catNode.getChildAt(j);
                if (child.getUserObject() instanceof String s && s.startsWith("Letters")) {
                    tileTree.collapsePath(new TreePath(child.getPath()));
                }
            }
        }
    }

    private TileDefinition getSelectedTile() {
        TreePath path = tileTree.getSelectionPath();
        if (path == null) return null;
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
        if (node.getUserObject() instanceof TileDefinition td) return td;
        return null;
    }

    private void selectTileInTree(char id) {
        for (int i = 0; i < treeRoot.getChildCount(); i++) {
            DefaultMutableTreeNode catNode = (DefaultMutableTreeNode) treeRoot.getChildAt(i);
            TreePath found = searchSubtree(catNode, id);
            if (found != null) {
                tileTree.setSelectionPath(found);
                tileTree.scrollPathToVisible(found);
                return;
            }
        }
    }

    private TreePath searchSubtree(DefaultMutableTreeNode node, char id) {
        if (node.getUserObject() instanceof TileDefinition td && td.getId() == id)
            return new TreePath(node.getPath());
        for (int i = 0; i < node.getChildCount(); i++) {
            TreePath found = searchSubtree((DefaultMutableTreeNode) node.getChildAt(i), id);
            if (found != null) return found;
        }
        return null;
    }

    private void loadSelectedTile() {
        TileDefinition t = getSelectedTile();
        if (t == null) return;

        loadingTile = true;
        try {
        idField.setText(String.valueOf(t.getId()));
        nameField.setText(t.getName());
        categoryCombo.setSelectedItem(t.getCategory());
        walkableCheck.setSelected(t.isWalkable());
        selectedColor = t.getEditorColor();
        if (colorBtn != null) colorBtn.repaint();

        isLiquidCheck.setSelected(t.isLiquid());
        blocksVisionCheck.setSelected(t.blocksVision());
        isSafeZoneCheck.setSelected(t.isSafeZone());
        lightRadiusSpinner.setValue(t.getLightRadius());
        stepSoundCombo.setSelectedItem(t.getStepSound());
        encounterRateSpinner.setValue(t.getEncounterRateMod());
        descriptionArea.setText(t.getDescription());

        TileEffect effect = t.getOnStepEffect();
        effectTypeCombo.setSelectedItem(effect.getType());
        mapCombo.setSelectedItem(effect.getParam("map"));
        xField.setText(effect.getParam("x"));
        yField.setText(effect.getParam("y"));
        amountField.setText(effect.getParam("amount"));
        populateTileCombo(tileIdCombo, effect.getParam("tileId"));
        populateKeyCombo(effect.getParam("requiredKeyId"));
        lockedMessageField.setText(effect.getParam("lockedMessage"));
        populateTileCombo(openCharCombo, effect.getParam("openChar"));
        populateTileCombo(closeCharCombo, effect.getParam("closeChar"));
        populateTileCombo(activateCharCombo, effect.getParam("activateChar"));
        populateTileCombo(deactivateCharCombo, effect.getParam("deactivateChar"));
        onceCheck.setSelected("true".equals(effect.getParam("once")));
        messageArea.setText(effect.getParam("message"));

        // New step-effect params
        selectGodById(godCombo, effect.getParam("god"));
        setSpinnerClamped(favorSpinner,    parseIntOr(effect.getParam("favor"),    5));
        setSpinnerClamped(xpSpinner,       parseIntOr(effect.getParam("xp"),       0));
        setSpinnerClamped(chanceSpinner,   parseIntOr(effect.getParam("chance"),   50));
        String dirParam = effect.getParam("direction");
        String dirLower = (dirParam == null || dirParam.isEmpty()) ? "(none)" : dirParam.toLowerCase();
        // Reset to (none) first so an unknown stored value (non-editable combo ignores it)
        // doesn't leave the previous tile's value sticky.
        directionCombo.setSelectedItem("(none)");
        directionCombo.setSelectedItem(dirLower);
        setSpinnerClamped(distanceSpinner, parseIntOr(effect.getParam("distance"), 3));
        setSpinnerClamped(goldSpinner,     parseIntOr(effect.getParam("gold"),     0));
        setSpinnerClamped(tierSpinner,     parseIntOr(effect.getParam("tier"),     1));
        setSpinnerClamped(lootTierSpinner, parseIntOr(effect.getParam("lootTier"), 0));
        populateItemCombo(itemIdCombo,         effect.getParam("itemId"));
        populateItemCombo(requiredItemIdCombo, effect.getParam("requiredItemId"));
        populateMonsterCombo(monsterIdCombo,   effect.getParam("monsterId"));
        populateItemCombo(lootItemIdCombo,     effect.getParam("lootItemId"));
        populateTileCombo(discoveredTileCombo, effect.getParam("discoveredTileId"));
        oneTimeCheck.setSelected("true".equals(effect.getParam("oneTime")));

        // Spawn weights — filter stale IDs, sort by monster name
        spawnTableModel.setRowCount(0);
        t.getSpawnWeights().entrySet().stream()
            .filter(e -> MonsterRegistry.getById(e.getKey()) != null)
            .sorted(java.util.Comparator.comparing(e -> {
                Monster m = MonsterRegistry.getById(e.getKey());
                return m != null ? m.getName() : e.getKey();
            }))
            .forEach(e -> spawnTableModel.addRow(new Object[]{e.getKey(), e.getValue()}));

        refreshSpriteCombo();
        spriteCombo.setSelectedItem(t.getSpriteKey());
        updateSpritePreview();
        setStatus("Editing: " + t.getName() + "  [" + t.getId() + "]");
        } finally {
            loadingTile = false;
        }
    }

    private void refreshSpriteCombo() {
        spriteCombo.removeAllItems();
        String category = (String) categoryCombo.getSelectedItem();
        if (category == null) return;

        String prefix = switch (category) {
            case "Overworld" -> "overworld/";
            case "Town"      -> "town/";
            case "Dungeon"   -> "dungeon/";
            default          -> "";
        };

        List<String> allKeys = new java.util.ArrayList<>(ImageAssetRegistry.getAllSpriteKeys());
        java.util.Collections.sort(allKeys);
        for (String key : allKeys) {
            if (key.equals("default")) continue;
            boolean include = prefix.isEmpty() || key.startsWith(prefix);
            if (!include && "Town".equals(category)) include = key.startsWith("letters/");
            if (include) spriteCombo.addItem(key);
        }
        if (spriteCombo.getItemCount() == 0) spriteCombo.addItem("default");
    }

    private void refreshMapList() {
        mapCombo.removeAllItems();
        mapCombo.addItem("(None)");
        File[] dirs = {new File("data/overworlds"), new File("data/towns")};
        for (File dir : dirs) {
            if (!dir.exists()) continue;
            File[] files = dir.listFiles((d, n) -> n.endsWith(".rfmap"));
            if (files == null) continue;
            java.util.Arrays.sort(files);
            for (File f : files) mapCombo.addItem(f.getName().replace(".rfmap", ""));
        }
    }

    private void updateSpritePreview() {
        String key = (String) spriteCombo.getSelectedItem();
        if (key == null || key.isEmpty()) { previewImage = null; spritePreviewPanel.repaint(); return; }

        Image img = ImageAssetRegistry.get(key);
        if (img instanceof BufferedImage bi) {
            previewImage = bi;
        } else if (img != null) {
            previewImage = new BufferedImage(img.getWidth(null), img.getHeight(null),
                BufferedImage.TYPE_INT_ARGB);
            previewImage.getGraphics().drawImage(img, 0, 0, null);
        } else {
            // Disk fallback
            String[] paths = {
                "src/main/resources/tiles/" + key + ".png",
                "src/main/resources/tiles/" + key,
            };
            previewImage = null;
            for (String path : paths) {
                try {
                    File f = new File(path);
                    if (f.exists()) { previewImage = ImageIO.read(f); break; }
                } catch (Exception ex) { /* skip */ }
            }
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

    private char findNextAvailableChar() {
        java.util.Set<Character> used = new java.util.HashSet<>();
        for (TileDefinition t : TileRegistry.getAllTiles()) used.add(t.getId());
        // Try printable ASCII: digits, uppercase, lowercase, then symbols
        for (char c = '0'; c <= '9'; c++) if (!used.contains(c)) return c;
        for (char c = 'A'; c <= 'Z'; c++) if (!used.contains(c)) return c;
        for (char c = 'a'; c <= 'z'; c++) if (!used.contains(c)) return c;
        for (char c = '!'; c <= '/'; c++) if (!used.contains(c)) return c;
        return '?';
    }

    private void createNewTile() {
        char nextId = findNextAvailableChar();
        TileDefinition newTile = new TileDefinition(nextId, "New Tile", "Custom", Color.GRAY, true, "default");
        TileRegistry.addTile(newTile);
        refreshTileTree();
        selectTileInTree(newTile.getId());
        nameField.requestFocus(); nameField.selectAll();
        setStatus("New tile created with ID '" + nextId + "' \u2014 fill in details and save");
    }

    private void deleteSelected() {
        TileDefinition tile = getSelectedTile();
        if (tile == null) { setStatus("\u26a0  Select a tile first"); return; }
        char tileId = tile.getId();

        int confirm = JOptionPane.showConfirmDialog(this,
            "Delete tile '" + tile.getName() + "' [" + tileId + "]?",
            "Confirm Delete", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) return;

        // Scan .rfmap files for usage of this tile ID.
        // data/dungeons was missing here, and it is where nine tiles live exclusively —
        // Stairway, Teleporter, Treasure Chest, Throne, Lava among them. Deleting Stairway
        // reported "not used anywhere", then took the descent tile out of all 40 authored
        // dungeon levels. The scan has to cover every directory MapData is ever loaded from.
        java.util.List<File> affectedFiles = new java.util.ArrayList<>();
        File[] searchDirs = { new File("data/overworlds"), new File("data/towns"),
                              new File("data/dungeons"), new File("data") };
        for (File dir : searchDirs) {
            if (!dir.exists()) continue;
            File[] files = dir.listFiles((d, n) -> n.endsWith(".rfmap"));
            if (files == null) continue;
            for (File f : files) {
                try {
                    // Ask the grid, not the file text. A substring test for "x" also matches the
                    // JSON keys .rfmap uses for coordinates, so single-character ids like 'x' and
                    // 'y' were reported as used in maps that never placed them.
                    MapData md = MapData.load(f);
                    if (md.tiles == null) continue;
                    boolean used = false;
                    for (char[] row : md.tiles) {
                        if (row == null) continue;
                        for (char c : row) if (c == tileId) { used = true; break; }
                        if (used) break;
                    }
                    if (used) affectedFiles.add(f);
                } catch (Exception ex) { /* skip unreadable files */ }
            }
        }

        if (!affectedFiles.isEmpty()) {
            // Build replacement dropdown
            java.util.List<TileDefinition> replacements = new java.util.ArrayList<>();
            for (TileDefinition t : TileRegistry.getAllTiles()) {
                if (t.getId() != tileId) replacements.add(t);
            }
            if (replacements.isEmpty()) {
                setStatus("\u26a0  No replacement tiles available");
                return;
            }
            String[] options = replacements.stream()
                .map(t -> t.getId() + " \u2014 " + t.getName())
                .toArray(String[]::new);

            String chosen = (String) JOptionPane.showInputDialog(this,
                "Tile '" + tileId + "' is used in " + affectedFiles.size() + " map file(s).\n"
                + "Choose a replacement tile or Cancel:",
                "Replace Tile in Maps", JOptionPane.QUESTION_MESSAGE,
                null, options, options[0]);
            if (chosen == null) { setStatus("Delete cancelled"); return; }

            char replacementId = chosen.charAt(0);

            // Rewrite affected map files through MapData, one grid cell at a time.
            //
            // This used to be a raw string replace over the whole JSON:
            //     content.replace("\"" + tileId + "\"", "\"" + replacementId + "\"")
            // Tile ids are single characters and two of them \u2014 'x' (Moon Altar) and 'y'
            // (Stained Glass) \u2014 collide with the JSON *keys* .rfmap uses for NPC and
            // teleporter coordinates. Deleting the Moon Altar rewrote every "x" key across
            // every map to the replacement id; Gson then dropped the unknown field and silently
            // reset every NPC and teleporter X coordinate to 0, in place, with no backup.
            for (File f : affectedFiles) {
                try {
                    MapData md = MapData.load(f);
                    boolean changed = false;
                    if (md.tiles != null) {
                        for (int ty = 0; ty < md.tiles.length; ty++) {
                            if (md.tiles[ty] == null) continue;
                            for (int tx = 0; tx < md.tiles[ty].length; tx++) {
                                if (md.tiles[ty][tx] == tileId) {
                                    md.tiles[ty][tx] = replacementId;
                                    changed = true;
                                }
                            }
                        }
                    }
                    if (changed) md.save(f);
                } catch (Exception ex) {
                    setStatus("\u26a0  Failed to update " + f.getName());
                    return;
                }
            }
        }

        TileRegistry.removeTile(tileId);
        refreshTileTree();
        if (treeRoot.getChildCount() > 0) {
            DefaultMutableTreeNode firstCat = (DefaultMutableTreeNode) treeRoot.getChildAt(0);
            if (firstCat.getChildCount() > 0) {
                DefaultMutableTreeNode firstLeaf = (DefaultMutableTreeNode) firstCat.getChildAt(0);
                tileTree.setSelectionPath(new TreePath(firstLeaf.getPath()));
            }
        }
        setStatus("Deleted tile '" + tile.getName() + "' [" + tileId + "]");
    }

    private void saveCurrentTile() {
        TileDefinition current = getSelectedTile();
        if (current == null) { setStatus("\u26a0  Select a tile first"); return; }

        String idStr = idField.getText().trim();
        if (idStr.length() != 1) { setStatus("\u26a0  ID must be exactly one character"); return; }

        // Check for duplicate ID if the ID was changed
        char newId = idStr.charAt(0);
        if (newId != current.getId()) {
            TileDefinition existing = TileRegistry.getById(newId);
            if (existing != null) {
                setStatus("\u26a0  ID '" + newId + "' is already used by tile '" + existing.getName() + "'");
                return;
            }
        }

        // Start from the params already on the tile so keys this form has no widget for
        // (e.g. unlockedTileId on locked doors) are not wiped by an ordinary save.
        var params = new java.util.HashMap<String, String>(current.getOnStepEffect().params());
        String selectedMap = (String) mapCombo.getSelectedItem();
        if (selectedMap != null && !selectedMap.equals("(None)"))
            params.put("map", selectedMap);
        params.put("x",       xField.getText().trim());
        params.put("y",       yField.getText().trim());
        params.put("amount",  amountField.getText().trim());
        params.put("tileId",        extractTileChar(tileIdCombo));
        String selKey = (String) requiredKeyCombo.getSelectedItem();
        params.put("requiredKeyId", (selKey == null || selKey.equals("(none)")) ? "" : selKey.split(" ")[0]);
        params.put("lockedMessage",    lockedMessageField.getText().trim());
        params.put("openChar",         extractTileChar(openCharCombo));
        params.put("closeChar",        extractTileChar(closeCharCombo));
        params.put("activateChar",     extractTileChar(activateCharCombo));
        params.put("deactivateChar",   extractTileChar(deactivateCharCombo));
        params.put("once",             onceCheck.isSelected() ? "true" : "false");
        params.put("message",          messageArea.getText().trim());

        // New step-effect params — values equal to the engine default are removed
        // rather than written, so the JSON stays clean and no stale value lingers.
        putOrClear(params, "god",              extractGodId(godCombo));
        putOrClear(params, "favor",            favorSpinner.getValue().toString(),    "5");
        putOrClear(params, "xp",               xpSpinner.getValue().toString(),       "0");
        putOrClear(params, "chance",           chanceSpinner.getValue().toString(),   "50");
        putOrClear(params, "direction",        extractDirection(directionCombo));
        putOrClear(params, "distance",         distanceSpinner.getValue().toString(), "3");
        putOrClear(params, "gold",             goldSpinner.getValue().toString(),     "0");
        putOrClear(params, "tier",             tierSpinner.getValue().toString(),     "1");
        putOrClear(params, "lootTier",         lootTierSpinner.getValue().toString(), "0");
        putOrClear(params, "itemId",           extractIdPrefix(itemIdCombo));
        putOrClear(params, "requiredItemId",   extractIdPrefix(requiredItemIdCombo));
        putOrClear(params, "monsterId",        extractIdPrefix(monsterIdCombo));
        putOrClear(params, "lootItemId",       extractIdPrefix(lootItemIdCombo));
        putOrClear(params, "discoveredTileId", extractTileChar(discoveredTileCombo));
        putOrClear(params, "oneTime",          oneTimeCheck.isSelected() ? "true" : "");

        TileDefinition updated = new TileDefinition(
            idStr.charAt(0),
            nameField.getText().trim(),
            (String) categoryCombo.getSelectedItem(),
            selectedColor,
            walkableCheck.isSelected(),
            (String) spriteCombo.getSelectedItem(),
            isLiquidCheck.isSelected(),
            blocksVisionCheck.isSelected(),
            isSafeZoneCheck.isSelected(),
            (Integer) lightRadiusSpinner.getValue(),
            (String) stepSoundCombo.getSelectedItem(),
            (Double) encounterRateSpinner.getValue(),
            descriptionArea.getText().trim(),
            new TileEffect((String) effectTypeCombo.getSelectedItem(), params)
        );

        // Collect spawn weights from table
        if (spawnTable.isEditing()) spawnTable.getCellEditor().stopCellEditing();
        java.util.Map<String, Integer> weights = new java.util.HashMap<>();
        for (int i = 0; i < spawnTableModel.getRowCount(); i++) {
            Object idObj  = spawnTableModel.getValueAt(i, 0);
            Object wObj   = spawnTableModel.getValueAt(i, 1);
            if (idObj == null) continue;
            String mId = idObj.toString().trim();
            if (mId.isEmpty()) continue;
            try { weights.put(mId, Integer.parseInt(wObj != null ? wObj.toString() : "1")); }
            catch (NumberFormatException ignored) {}
        }
        updated.setSpawnWeights(weights);

        // ── Pre-save validation ──────────────────────────────────────────────
        java.util.List<String> warnings = new java.util.ArrayList<>();
        String spKey = (String) spriteCombo.getSelectedItem();
        if (spKey == null || spKey.isEmpty()) {
            warnings.add("No sprite key set — tile will use default placeholder");
        } else if (!ImageAssetRegistry.getAllSpriteKeys().contains(spKey)) {
            warnings.add("Sprite key '" + spKey + "' not found in ImageAssetRegistry");
        }
        if (!warnings.isEmpty()) {
            String msg = "Validation warnings:\n\n• " + String.join("\n• ", warnings)
                       + "\n\nSave anyway?";
            if (JOptionPane.showConfirmDialog(this, msg, "Validation", JOptionPane.YES_NO_OPTION)
                    != JOptionPane.YES_OPTION) return;
        }

        // updateTile() matches on the NEW id, so a rename would append and leave the old
        // entry behind as a duplicate — drop it first.
        if (newId != current.getId()) TileRegistry.removeTile(current.getId());
        TileRegistry.updateTile(updated);
        ImageAssetRegistry.reloadAll();
        refreshTileTree();
        selectTileInTree(updated.getId());
        setStatus("Saved: " + updated.getName() + "  [" + updated.getId() + "]");
    }

    private void setStatus(String msg) {
        if (statusLabel != null) {
            statusLabel.setText("  " + msg);
            statusLabel.setForeground(msg.startsWith("\u26a0") ? DANGER : TEXT_DIM);
        }
    }

    // ── Layout helpers ────────────────────────────────────────────────────────
    /** Returns a GridBag form panel ready to receive rows. */
    private JPanel tabPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBackground(PANEL_BG);
        return p;
    }

    /** Wraps a raw GridBag tab panel with padding and a vertical spacer. */
    private JPanel wrapTab(JPanel inner, int lastRow) {
        // Add spacer to push rows up
        GridBagConstraints sp = new GridBagConstraints();
        sp.gridx = 0; sp.gridy = lastRow + 1; sp.gridwidth = 2;
        sp.weighty = 1.0; sp.fill = GridBagConstraints.VERTICAL;
        inner.add(Box.createVerticalGlue(), sp);

        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setBackground(PANEL_BG);
        wrap.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
        wrap.add(inner, BorderLayout.CENTER);
        return wrap;
    }

    /** Creates a GridBagConstraints for the given column and fill mode. */
    private GridBagConstraints gbc(int col, int fill) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = col;
        c.anchor = (fill == GridBagConstraints.WEST) ? GridBagConstraints.WEST : GridBagConstraints.WEST;
        c.fill = (fill == GridBagConstraints.HORIZONTAL) ? GridBagConstraints.HORIZONTAL : GridBagConstraints.NONE;
        if (fill == GridBagConstraints.HORIZONTAL) c.weightx = 1.0;
        c.insets = (col == 0) ? new Insets(5, 0, 5, 14) : new Insets(5, 0, 5, 0);
        return c;
    }

    // ── Tile char combo population ────────────────────────────────────────────
    private void populateTileCombo(JComboBox<String> combo, String selectedChar) {
        combo.removeAllItems();
        combo.addItem("(none)");
        String toSelect = "(none)";
        for (TileDefinition td : TileRegistry.getAllTiles()) {
            String entry = "[" + td.getId() + "] " + td.getName();
            combo.addItem(entry);
            if (selectedChar != null && !selectedChar.isEmpty()
                    && selectedChar.charAt(0) == td.getId()) {
                toSelect = entry;
            }
        }
        combo.setSelectedItem(toSelect);
        combo.setBackground(PANEL_BG);
        combo.setForeground(TEXT_BRIGHT);
        combo.setFont(MONO_SM);
    }

    private String extractTileChar(JComboBox<String> combo) {
        String sel = (String) combo.getSelectedItem();
        if (sel == null || sel.equals("(none)")) return "";
        // Format is "[X] Tile Name" — extract char between brackets
        int open = sel.indexOf('[');
        int close = sel.indexOf(']');
        if (open >= 0 && close > open + 1) return sel.substring(open + 1, close);
        return "";
    }

    /** Extracts the God enum name (e.g. "LIRANDEL") from a "LIRANDEL — Moon..." combo entry. */
    private static String extractGodId(JComboBox<String> combo) {
        String sel = (String) combo.getSelectedItem();
        if (sel == null || sel.startsWith("(")) return "";
        int dash = sel.indexOf(" — ");
        return dash > 0 ? sel.substring(0, dash) : sel;
    }

    /** Returns the direction word ("north"/"south"/"east"/"west"), or "" for (none). */
    private static String extractDirection(JComboBox<String> combo) {
        String sel = (String) combo.getSelectedItem();
        return (sel == null || "(none)".equals(sel)) ? "" : sel;
    }

    /** Selects the combo row matching the given God name (case-insensitive), or "(none)" if unknown. */
    private static void selectGodById(JComboBox<String> combo, String godId) {
        if (godId == null || godId.isEmpty()) { combo.setSelectedItem("(none)"); return; }
        String upper = godId.toUpperCase();
        for (int i = 0; i < combo.getItemCount(); i++) {
            String opt = combo.getItemAt(i);
            int dash = opt.indexOf(" — ");
            String prefix = dash > 0 ? opt.substring(0, dash) : opt;
            if (prefix.equalsIgnoreCase(upper)) { combo.setSelectedIndex(i); return; }
        }
        combo.setSelectedItem("(none)");
    }

    /** Sets spinner to value, clamping to the SpinnerNumberModel's min/max so setValue can't throw. */
    private static void setSpinnerClamped(JSpinner spinner, int value) {
        if (spinner.getModel() instanceof SpinnerNumberModel m) {
            Comparable<?> min = m.getMinimum();
            Comparable<?> max = m.getMaximum();
            if (min instanceof Integer mi && value < mi) value = mi;
            if (max instanceof Integer ma && value > ma) value = ma;
        }
        spinner.setValue(value);
    }

    private static int parseIntOr(String s, int defaultVal) {
        if (s == null || s.isEmpty()) return defaultVal;
        try { return Integer.parseInt(s.trim()); }
        catch (NumberFormatException e) { return defaultVal; }
    }

    /** Writes a non-empty value, or removes the key so a stale value cannot linger. */
    private static void putOrClear(java.util.Map<String, String> params, String key, String value) {
        if (value != null && !value.isEmpty()) params.put(key, value);
        else params.remove(key);
    }

    /** As above, but a value equal to the engine default is stored as "not set". */
    private static void putOrClear(java.util.Map<String, String> params, String key,
                                   String value, String defaultVal) {
        putOrClear(params, key, defaultVal.equals(value) ? "" : value);
    }

    /** Returns the id half of an "id — Name" combo entry, or "" for (none). */
    private static String extractIdPrefix(JComboBox<String> combo) {
        String sel = (String) combo.getSelectedItem();
        if (sel == null || sel.equals("(none)")) return "";
        int dash = sel.indexOf(" — ");
        return dash > 0 ? sel.substring(0, dash) : sel;
    }

    /** Fills a combo with every item as "id — Name", selecting {@code selectedId}. */
    private void populateItemCombo(JComboBox<String> combo, String selectedId) {
        combo.removeAllItems();
        combo.addItem("(none)");
        String toSelect = "(none)";
        List<Item> items = new java.util.ArrayList<>(ItemRegistry.getAllItems());
        items.sort(java.util.Comparator.comparing(Item::getName, String.CASE_INSENSITIVE_ORDER));
        for (Item item : items) {
            String entry = item.getId() + " — " + item.getName();
            combo.addItem(entry);
            if (item.getId().equals(selectedId)) toSelect = entry;
        }
        combo.setSelectedItem(keepUnknownId(combo, selectedId, toSelect));
    }

    /** Fills a combo with every monster as "id — Name (Ln)", selecting {@code selectedId}. */
    private void populateMonsterCombo(JComboBox<String> combo, String selectedId) {
        combo.removeAllItems();
        combo.addItem("(none)");
        String toSelect = "(none)";
        List<Monster> mons = new java.util.ArrayList<>(MonsterRegistry.getAllMonsters());
        mons.sort(java.util.Comparator.comparing(Monster::getName, String.CASE_INSENSITIVE_ORDER));
        for (Monster m : mons) {
            String entry = m.getId() + " — " + m.getName() + " (L" + m.getLevel() + ")";
            combo.addItem(entry);
            if (m.getId().equals(selectedId)) toSelect = entry;
        }
        combo.setSelectedItem(keepUnknownId(combo, selectedId, toSelect));
    }

    /**
     * Keeps an authored id that is no longer in the registry selectable, so saving the
     * tile cannot silently erase it.
     */
    private static String keepUnknownId(JComboBox<String> combo, String selectedId, String toSelect) {
        if (selectedId == null || selectedId.isEmpty() || !"(none)".equals(toSelect)) return toSelect;
        String entry = selectedId + " — (not in registry)";
        combo.addItem(entry);
        return entry;
    }

    // ── Key combo population ──────────────────────────────────────────────────
    private void populateKeyCombo(String selectedId) {
        requiredKeyCombo.removeAllItems();
        requiredKeyCombo.addItem("(none)");
        String toSelect = "(none)";
        for (Item item : ItemRegistry.getAllItems()) {
            if (item.getType() == Item.Type.KEY) {
                String entry = item.getId() + " — " + item.getName();
                requiredKeyCombo.addItem(entry);
                if (item.getId().equals(selectedId)) toSelect = entry;
            }
        }
        requiredKeyCombo.setSelectedItem(toSelect);
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

    private static JSpinner darkSpinner(int val, int min, int max, int step) {
        JSpinner s = new JSpinner(new SpinnerNumberModel(val, min, max, step));
        s.setFont(MONO_SM);
        JComponent ed = s.getEditor();
        if (ed instanceof JSpinner.DefaultEditor de) {
            de.getTextField().setBackground(new Color(8, 12, 20));
            de.getTextField().setForeground(PHOSPHOR);
            de.getTextField().setCaretColor(PHOSPHOR);
            de.getTextField().setFont(MONO_SM);
        }
        return s;
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
                    (getWidth()-fm.stringWidth(getText()))/2,
                    (getHeight()+fm.getAscent()-fm.getDescent())/2);
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

    // ── TileCellRenderer ──────────────────────────────────────────────────────
    private class TileCellRenderer extends DefaultTreeCellRenderer {
        private static final Color SEL_TILE_BG  = new Color(40, 35, 0);
        private static final Color ALT_ROW_BG   = new Color(13, 16, 22);
        private static final Color CAT_BG        = new Color(18, 22, 32);
        private static final int ICON_SIZE = 20;

        private javax.swing.ImageIcon getSpriteIcon(TileDefinition td) {
            Image img = ImageAssetRegistry.get(td.getSpriteKey());
            if (img == null) return null;
            Image scaled = img.getScaledInstance(ICON_SIZE, ICON_SIZE, Image.SCALE_FAST);
            return new javax.swing.ImageIcon(scaled);
        }

        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value,
                boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
            JLabel l = (JLabel) super.getTreeCellRendererComponent(
                tree, value, selected, expanded, leaf, row, hasFocus);
            l.setIcon(null);

            DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
            Object obj = node.getUserObject();

            if (obj instanceof TileDefinition td) {
                // Leaf: tile entry with sprite preview
                l.setText(" " + td.getName());
                l.setFont(MONO_SM);
                javax.swing.ImageIcon icon = getSpriteIcon(td);
                if (icon != null) l.setIcon(icon);
                l.setIconTextGap(6);
                if (selected) {
                    l.setBackground(SEL_TILE_BG);
                    l.setForeground(PHOSPHOR3);
                    l.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 3, 0, 0, PHOSPHOR3),
                        BorderFactory.createEmptyBorder(2, 4, 2, 8)));
                } else {
                    l.setBackground(row % 2 == 0 ? BG : ALT_ROW_BG);
                    l.setForeground(TEXT_BRIGHT);
                    l.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
                }
            } else if (obj instanceof String cat) {
                // Category or sub-group node
                boolean isSubGroup = !treeRoot.equals(node.getParent());
                l.setText("  \u25c8  " + cat.toUpperCase());
                l.setFont(isSubGroup
                    ? new Font("Monospaced", Font.PLAIN, 11)
                    : new Font("Monospaced", Font.BOLD, 12));
                l.setBackground(CAT_BG);
                l.setForeground(selected ? TEXT_BRIGHT : PHOSPHOR3);
                l.setBorder(BorderFactory.createEmptyBorder(3, 4, 3, 4));
            }
            l.setOpaque(true);
            return l;
        }
    }
}

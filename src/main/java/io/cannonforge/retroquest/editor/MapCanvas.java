package io.cannonforge.retroquest.editor;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.JComboBox;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import io.cannonforge.retroquest.model.MapData;
import io.cannonforge.retroquest.model.NPC;
import io.cannonforge.retroquest.model.TileDefinition;
import io.cannonforge.retroquest.model.TileState;
import io.cannonforge.retroquest.model.TownEntrance;
import io.cannonforge.retroquest.registry.ImageAssetRegistry;
import io.cannonforge.retroquest.registry.TileRegistry;

/**
 * Tile-map editing canvas used inside RetroForge.
 *
 * <p>Renders the map with a fixed viewport and handles mouse painting, NPC/town placement,
 * spawn-difficulty painting, undo/redo (Ctrl+Z / Ctrl+Y), and smooth arrow-key camera panning.
 */
@SuppressWarnings("serial")
public class MapCanvas extends JPanel {

    private final RetroForge editor;
    private MapData map;

    // Default viewport constants (used by LivePreviewPanel at default zoom)
    public static final int VIEW_W = 34;
    public static final int VIEW_H = 21;   // was 18 (+3 tiles)
    private int cameraX = 0;
    private int cameraY = 0;

    // Zoom
    private static final int[] ZOOM_LEVELS = {4, 8, 16, 24, 32, 48, 64, 96, 128};
    private int zoomIndex = 4;
    private int tileSize = 32;

    private char brushTile = '.';
    private Tool tool = Tool.PENCIL;
    private int brushSize = 1;
    private int spawnDifficultyValue = 5;

    private int hoverTileX = -1;
    private int hoverTileY = -1;

    private final ArrayDeque<MapCommand> undoStack = new ArrayDeque<>();
    private final ArrayDeque<MapCommand> redoStack = new ArrayDeque<>();

    /**
     * Accumulates per-cell changes during a pencil or spawn-difficulty drag so that
     * the entire stroke is committed as a single undoable command on mouse release.
     * Key = {@code y * map.width + x}.  Null when no drag is in progress.
     */
    private LinkedHashMap<Integer, MapCommand.TileChange> pendingDrag = null;

    // ── Selection & clipboard ──────────────────────────────────────────────
    private int selX1 = -1, selY1 = -1, selX2 = -1, selY2 = -1;  // world coords
    private boolean selecting = false;
    private char[][] clipboard = null;
    private int[][] clipboardDiff = null;

    // Smooth arrow key panning
    private final Timer panTimer;
    private int panDX = 0;
    private int panDY = 0;

    public MapCanvas(RetroForge editor) {
        this.editor = editor;
        setMap(editor.getCurrentMap());

        setPreferredSize(new Dimension(VIEW_W * tileSize, VIEW_H * tileSize));
        setFocusable(true);
        requestFocusInWindow();

        // ==================== MOUSE ====================
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                int tx = cameraX + e.getX() / tileSize;
                int ty = cameraY + e.getY() / tileSize;
                if (tx < 0 || ty < 0 || tx >= map.width || ty >= map.height) return;

                if (e.getClickCount() == 2) {
                    for (NPC n : map.npcs) {
                        if (Math.abs(n.getX() - tx) <= 1 && Math.abs(n.getY() - ty) <= 1) {
                            new NPCEditorDialog(editor, MapCanvas.this, map, n, tx, ty);
                            return;
                        }
                    }
                }

                if (tool == Tool.NPC_PLACER) {
                    handleNPCPlacer(e, tx, ty);
                    return;
                }
                if (tool == Tool.TOWN_PLACER) {
                    if (map.tiles[ty][tx] == 'E') {
                        for (TownEntrance te : map.townEntrances) {
                            if (te.worldX() == tx && te.worldY() == ty) {
                                // Single-click: wait. Double-click: open the town.
                                if (e.getClickCount() == 2) {
                                    editor.openTownFromEntrance(te.townName());
                                }
                                return;
                            }
                        }
                    }
                    // No linked town at this tile — open placement dialog.
                    // The dialog calls canvas.pushCommand() on confirmation.
                    new TownPlacementDialog(editor, MapCanvas.this, map, tx, ty);
                    return;
                }
                if (tool == Tool.DUNGEON_PLACER) {
                    // Clicking existing 'D' tile re-opens placement dialog to reassign
                    new DungeonPlacementDialog(editor, MapCanvas.this, map, tx, ty);
                    return;
                }
                // TELEPORTER_PLACER removed — teleport destinations are now set via
                // TileInstanceDialog (right-click → instance properties on any tile)
                if (tool == Tool.SPAWN_DIFFICULTY) {
                    if (SwingUtilities.isRightMouseButton(e)) {
                        spawnDifficultyValue = map.spawnDifficulty[ty][tx];
                        editor.updateSpawnSpinner(spawnDifficultyValue);
                    } else {
                        pendingDrag = new LinkedHashMap<>();
                        recordAndPaintSpawnDiff(tx, ty);
                    }
                    return;
                }

                // Right-click opens tile instance dialog in all modes except SPAWN_DIFFICULTY
                if (SwingUtilities.isRightMouseButton(e) && tool != Tool.SPAWN_DIFFICULTY) {
                    showTileContextMenu(e, tx, ty);
                    return;
                }

                if (tool == Tool.SELECT) {
                    selX1 = tx; selY1 = ty;
                    selX2 = tx; selY2 = ty;
                    selecting = true;
                    repaint();
                    return;
                }

                if (tool == Tool.PENCIL) {
                    pendingDrag = new LinkedHashMap<>();
                    recordAndPaint(tx, ty);
                    return;
                }

                if (tool == Tool.FILL) {
                    // Snapshot tile array, run fill, diff to build TileCommand.
                    char[][] snapshot = new char[map.height][];
                    for (int i = 0; i < map.height; i++) snapshot[i] = map.tiles[i].clone();

                    paintAt(tx, ty); // runs floodFill, mutates map.tiles

                    List<MapCommand.TileChange> changes = new ArrayList<>();
                    for (int fy = 0; fy < map.height; fy++) {
                        for (int fx = 0; fx < map.width; fx++) {
                            if (snapshot[fy][fx] != map.tiles[fy][fx]) {
                                // A filled-over 'E' has to give up its entrance, exactly as the
                                // pencil does — otherwise flooding water across a coastline
                                // leaves a live TownEntrance sitting under the sea.
                                TownEntrance re = (snapshot[fy][fx] == 'E')
                                        ? takeEntrance(fx, fy) : null;
                                changes.add(new MapCommand.TileChange(
                                        fx, fy,
                                        snapshot[fy][fx], map.tiles[fy][fx],
                                        map.spawnDifficulty[fy][fx], map.spawnDifficulty[fy][fx],
                                        re, null, takeTileState(fx, fy)));
                            }
                        }
                    }
                    if (!changes.isEmpty()) pushCommand(new MapCommand.TileCommand(changes));
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (selecting) {
                    selecting = false;
                    // Normalize selection bounds
                    int x1 = Math.min(selX1, selX2), x2 = Math.max(selX1, selX2);
                    int y1 = Math.min(selY1, selY2), y2 = Math.max(selY1, selY2);
                    selX1 = x1; selY1 = y1; selX2 = x2; selY2 = y2;
                    repaint();
                    return;
                }
                if (pendingDrag != null && !pendingDrag.isEmpty()) {
                    pushCommand(new MapCommand.TileCommand(new ArrayList<>(pendingDrag.values())));
                }
                pendingDrag = null;
            }
        });

        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                int tx = cameraX + e.getX() / tileSize;
                int ty = cameraY + e.getY() / tileSize;
                hoverTileX = tx;
                hoverTileY = ty;
                repaint();
                if (tx < 0 || ty < 0 || tx >= map.width || ty >= map.height) return;

                if (selecting) {
                    selX2 = tx; selY2 = ty;
                    repaint();
                } else if (tool == Tool.PENCIL && pendingDrag != null) {
                    recordAndPaint(tx, ty);
                } else if (tool == Tool.SPAWN_DIFFICULTY && pendingDrag != null) {
                    recordAndPaintSpawnDiff(tx, ty);
                }
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                hoverTileX = cameraX + e.getX() / tileSize;
                hoverTileY = cameraY + e.getY() / tileSize;
                repaint();
            }
        });

        addMouseWheelListener(e -> {
            if (e.isControlDown()) {
                zoom(-e.getWheelRotation(), e.getX(), e.getY());
            } else {
                brushSize = Math.max(1, Math.min(9, brushSize - e.getWheelRotation()));
                editor.updateBrushLabel();
                repaint();
            }
        });

        // ==================== KEYBOARD PANNING ====================
        // Smooth arrow key panning + live update of viewport box on preview
        panTimer = new Timer(16, e -> {
            cameraX += panDX;
            cameraY += panDY;
            clampCamera();

            repaint();                    // redraw the main canvas
            editor.refreshAll();          // makes the yellow box move live
        });
        panTimer.setRepeats(true);
        // FORCE FOCUS whenever user clicks the canvas (fixes arrow keys after clicking elsewhere)
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
            }
        });

        // Make sure we start with focus
        SwingUtilities.invokeLater(this::requestFocusInWindow);

        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), "panUp");
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "panDown");
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), "panLeft");
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), "panRight");

        getActionMap().put("panUp", new PanAction(0, -1));
        getActionMap().put("panDown", new PanAction(0, 1));
        getActionMap().put("panLeft", new PanAction(-1, 0));
        getActionMap().put("panRight", new PanAction(1, 0));
        // Force initial viewport box to appear on startup
        SwingUtilities.invokeLater(editor::refreshAll);

        // Zoom shortcuts
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, InputEvent.CTRL_DOWN_MASK), "zoomIn");
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, InputEvent.CTRL_DOWN_MASK), "zoomOut");
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_0, InputEvent.CTRL_DOWN_MASK), "zoomReset");
        getActionMap().put("zoomIn", new AbstractAction() { @Override
		public void actionPerformed(ActionEvent e) { zoom(1, getWidth() / 2, getHeight() / 2); }});
        getActionMap().put("zoomOut", new AbstractAction() { @Override
		public void actionPerformed(ActionEvent e) { zoom(-1, getWidth() / 2, getHeight() / 2); }});
        getActionMap().put("zoomReset", new AbstractAction() { @Override
		public void actionPerformed(ActionEvent e) { resetZoom(); }});

        // Selection: Ctrl+C copy, Ctrl+V paste, Escape clear
        // Handled by global KeyEventDispatcher in RetroForge (works regardless of focus)
    }

    private class PanAction extends AbstractAction {
        private final int dx, dy;
        PanAction(int dx, int dy) { this.dx = dx; this.dy = dy; }
        @Override
		public void actionPerformed(ActionEvent e) {
            panDX = dx * 2;
            panDY = dy * 2;
            panTimer.start();
        }
    }

    // Release arrow keys = stop panning
    @Override
    protected void processKeyEvent(KeyEvent e) {
        if (e.getID() == KeyEvent.KEY_RELEASED) {
            panDX = 0;
            panDY = 0;
            panTimer.stop();
        }
        super.processKeyEvent(e);
    }

    // ==================== PUBLIC API ====================
    /**
     * Sets the map being edited, resets the camera to the origin, and clears
     * both the undo and redo history stacks.
     *
     * @param m the map to load into the editor
     */
    public void setMap(MapData m) {
        map = m;
        cameraX = 0;
        cameraY = 0;
        zoomIndex = 4;
        tileSize = 32;
        undoStack.clear();
        redoStack.clear();
        hoverTileX = -1;
        hoverTileY = -1;
        selX1 = -1; selY1 = -1; selX2 = -1; selY2 = -1;
        clipboard = null;
        clipboardDiff = null;
        repaint();
    }

    public void centerViewOn(int worldX, int worldY) {
        int vw = getViewW();
        int vh = getViewH();
        cameraX = worldX - vw / 2;
        cameraY = worldY - vh / 2;
        clampCamera();
        repaint();
        editor.refreshAll();
    }

    public void setTool(Tool t) { tool = t; }
    public void setBrush(char tile) { brushTile = tile; }

    // ==================== COMMAND PATTERN UNDO/REDO ====================

    /** Pushes a command onto the undo stack and clears redo history. Caps stack at 40. */
    public void pushCommand(MapCommand cmd) {
        undoStack.push(cmd);
        redoStack.clear();
        if (undoStack.size() > 40) undoStack.removeLast(); // O(1) on ArrayDeque
        editor.markDirty();
    }

    public void undo() {
        if (undoStack.isEmpty()) {
            JOptionPane.showMessageDialog(editor, "Nothing left to undo!", "Undo", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        MapCommand cmd = undoStack.pop();
        redoStack.push(cmd);
        unapply(cmd);
        editor.markDirty();
        repaint();
        editor.refreshAll();
    }

    public void redo() {
        if (redoStack.isEmpty()) {
            JOptionPane.showMessageDialog(editor, "Nothing left to redo!", "Redo", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        MapCommand cmd = redoStack.pop();
        undoStack.push(cmd);
        apply(cmd);
        editor.markDirty();
        repaint();
        editor.refreshAll();
    }

    // ==================== SELECTION / COPY / PASTE ====================

    public void copySelection() {
        if (selX1 < 0 || selY1 < 0 || selX2 < 0 || selY2 < 0) return;
        int x1 = Math.min(selX1, selX2), x2 = Math.max(selX1, selX2);
        int y1 = Math.min(selY1, selY2), y2 = Math.max(selY1, selY2);
        int w = x2 - x1 + 1, h = y2 - y1 + 1;
        clipboard = new char[h][w];
        clipboardDiff = new int[h][w];
        for (int dy = 0; dy < h; dy++) {
            for (int dx = 0; dx < w; dx++) {
                int wx = x1 + dx, wy = y1 + dy;
                if (wx >= 0 && wx < map.width && wy >= 0 && wy < map.height) {
                    clipboard[dy][dx] = map.tiles[wy][wx];
                    clipboardDiff[dy][dx] = map.spawnDifficulty[wy][wx];
                }
            }
        }
        clearSelection();
    }

    public void pasteAtHover() {
        if (clipboard == null || clipboard.length == 0 || hoverTileX < 0 || hoverTileY < 0) return;
        int pw = clipboard[0].length, ph = clipboard.length;
        List<MapCommand.TileChange> changes = new ArrayList<>();
        int unlinked = 0;
        for (int dy = 0; dy < ph; dy++) {
            for (int dx = 0; dx < pw; dx++) {
                int wx = hoverTileX + dx, wy = hoverTileY + dy;
                if (wx >= 0 && wx < map.width && wy >= 0 && wy < map.height) {
                    char oldTile = map.tiles[wy][wx];
                    int oldDiff = map.spawnDifficulty[wy][wx];
                    char newTile = clipboard[dy][dx];
                    int newDiff = clipboardDiff[dy][dx];
                    if (oldTile != newTile || oldDiff != newDiff) {
                        // Whatever was bound to this cell belonged to the tile being replaced.
                        // Paste used to write the tile and leave both behind: the destination's
                        // old TownEntrance and authored TileState survived on a cell that was no
                        // longer that tile, and the change recorded null so undo could not
                        // restore them either.
                        TownEntrance re = null;
                        TileState st = null;
                        if (oldTile != newTile) {
                            if (oldTile == 'E') re = takeEntrance(wx, wy);
                            st = takeTileState(wx, wy);
                        }
                        map.tiles[wy][wx] = newTile;
                        map.spawnDifficulty[wy][wx] = newDiff;
                        changes.add(new MapCommand.TileChange(wx, wy, oldTile, newTile,
                                                              oldDiff, newDiff, re, null, st));
                        if (newTile == 'E' || newTile == 'D') unlinked++;
                    }
                }
            }
        }
        if (!changes.isEmpty()) {
            pushCommand(new MapCommand.TileCommand(changes));
        }
        repaint();
        editor.refreshAll();

        // The clipboard copies tiles only, so a pasted 'E' or 'D' arrives with no TownEntrance
        // and no dungeonName — a doorway the player can walk onto that does nothing. Duplicating
        // the source entrance would be worse: two entrances naming one town is ambiguous about
        // which the town links back to. So say it plainly and let the author place it.
        if (unlinked > 0) {
            JOptionPane.showMessageDialog(this,
                unlinked + " pasted entrance tile(s) have no destination.\n\n"
              + "Copy carries tiles only, not town or dungeon links. Use the Town Placer or\n"
              + "Dungeon Placer on those cells, or they will do nothing in game.",
                "Pasted entrances are unlinked", JOptionPane.WARNING_MESSAGE);
        }
    }

    public void clearSelection() {
        selX1 = selY1 = selX2 = selY2 = -1;
        selecting = false;
        repaint();
    }

    public boolean hasSelection() { return selX1 >= 0 && selY1 >= 0 && selX2 >= 0 && selY2 >= 0; }
    public boolean hasClipboard() { return clipboard != null && clipboard.length > 0; }
    public String getClipboardSize() {
        if (clipboard == null || clipboard.length == 0) return "0×0";
        return clipboard[0].length + "×" + clipboard.length;
    }

    private void apply(MapCommand cmd) {
        if (cmd instanceof MapCommand.TileCommand tc) {
            tc.changes().forEach(c -> {
                map.tiles[c.y()][c.x()] = c.newTile();
                map.spawnDifficulty[c.y()][c.x()] = c.newDiff();
                if (c.removedEntrance() != null)   map.townEntrances.remove(c.removedEntrance());
                if (c.removedTeleporter() != null) map.overworldTeleporters.remove(c.removedTeleporter());
                if (c.removedState() != null)      putTileState(c.x(), c.y(), null);
            });
        } else if (cmd instanceof MapCommand.NpcAddCommand a) {
            map.npcs.add(a.npc());
        } else if (cmd instanceof MapCommand.NpcRemoveCommand r) {
            map.npcs.remove(r.npc());
        } else if (cmd instanceof MapCommand.EntranceAddCommand a) {
            map.tiles[a.entrance().worldY()][a.entrance().worldX()] = 'E';
            if (a.replaced() != null) map.townEntrances.remove(a.replaced());
            map.townEntrances.add(a.entrance());
        } else if (cmd instanceof MapCommand.EntranceRemoveCommand r) {
            map.townEntrances.remove(r.entrance());
        } else if (cmd instanceof MapCommand.DungeonAddCommand a) {
            map.tiles[a.y()][a.x()] = 'D';
            setDungeonName(a.x(), a.y(), a.dungeonName());
        } else if (cmd instanceof MapCommand.DungeonRemoveCommand r) {
            map.tiles[r.y()][r.x()] = r.oldTile();
            if (r.dungeonName() != null) setDungeonName(r.x(), r.y(), null);
        } else if (cmd instanceof MapCommand.TileStateCommand s) {
            putTileState(s.x(), s.y(), s.newState());
        }
    }

    private void unapply(MapCommand cmd) {
        if (cmd instanceof MapCommand.TileCommand tc) {
            tc.changes().forEach(c -> {
                map.tiles[c.y()][c.x()] = c.oldTile();
                map.spawnDifficulty[c.y()][c.x()] = c.oldDiff();
                if (c.removedEntrance() != null)   map.townEntrances.add(c.removedEntrance());
                if (c.removedTeleporter() != null) map.overworldTeleporters.add(c.removedTeleporter());
                if (c.removedState() != null)      putTileState(c.x(), c.y(), c.removedState());
            });
        } else if (cmd instanceof MapCommand.NpcAddCommand a) {
            map.npcs.remove(a.npc());
        } else if (cmd instanceof MapCommand.NpcRemoveCommand r) {
            map.npcs.add(r.npc());
        } else if (cmd instanceof MapCommand.EntranceAddCommand a) {
            map.townEntrances.remove(a.entrance());
            if (a.replaced() != null) map.townEntrances.add(a.replaced());
            map.tiles[a.entrance().worldY()][a.entrance().worldX()] = a.oldTile();
        } else if (cmd instanceof MapCommand.EntranceRemoveCommand r) {
            map.townEntrances.add(r.entrance());
        } else if (cmd instanceof MapCommand.DungeonAddCommand a) {
            map.tiles[a.y()][a.x()] = a.oldTile();
            setDungeonName(a.x(), a.y(), a.oldDungeonName());
        } else if (cmd instanceof MapCommand.DungeonRemoveCommand r) {
            map.tiles[r.y()][r.x()] = 'D';
            if (r.dungeonName() != null) setDungeonName(r.x(), r.y(), r.dungeonName());
        } else if (cmd instanceof MapCommand.TileStateCommand s) {
            putTileState(s.x(), s.y(), s.oldState());
        }
    }

    /** Flags the map as edited without recording an undo step (in-place field edits). */
    public void markDirty() {
        editor.markDirty();
    }

    // ==================== INSTANCE-STATE HELPERS ====================

    /** Stores {@code ts} as the instance state of a cell; {@code null} removes it. */
    private void putTileState(int x, int y, TileState ts) {
        String key = x + "," + y;
        if (ts == null) {
            if (map.initialTileStates != null) map.initialTileStates.remove(key);
        } else {
            if (map.initialTileStates == null) map.initialTileStates = new java.util.HashMap<>();
            map.initialTileStates.put(key, ts);
        }
    }

    /** Removes and returns the instance state of a cell, or null if it had none. */
    private TileState takeTileState(int x, int y) {
        return (map.initialTileStates == null) ? null : map.initialTileStates.remove(x + "," + y);
    }

    /**
     * Detaches the town entrance bound to a cell that is about to stop being an entrance,
     * returning it so the {@link MapCommand.TileChange} can carry it for undo.
     *
     * <p>The pencil has always done this inline; fill and paste did not, so a flood fill or a
     * paste over an 'E' left the {@code TownEntrance} object behind at a coordinate that was no
     * longer an entrance tile — invisible in game, still reported as linked by the editor's own
     * context menu, and not restorable by undo because the change recorded {@code null}.
     */
    private TownEntrance takeEntrance(int x, int y) {
        if (map.townEntrances == null) return null;
        TownEntrance found = null;
        for (TownEntrance te : map.townEntrances) {
            if (te.worldX() == x && te.worldY() == y) { found = te; break; }
        }
        if (found != null) map.townEntrances.remove(found);
        return found;
    }

    /** Sets (or clears, when {@code name} is null) the dungeonName of a cell's state. */
    private void setDungeonName(int x, int y, String name) {
        String key = x + "," + y;
        if (name != null) {
            if (map.initialTileStates == null) map.initialTileStates = new java.util.HashMap<>();
            map.initialTileStates.computeIfAbsent(key, k -> new TileState()).set("dungeonName", name);
        } else if (map.initialTileStates != null) {
            TileState ts = map.initialTileStates.get(key);
            if (ts != null && ts.data != null) ts.data.remove("dungeonName");
        }
    }

    // ==================== DRAG ACCUMULATION HELPERS ====================

    /**
     * Snapshots the brush-sized area, calls {@link #paintAt}, then records each changed
     * cell (including any entrance/teleporter implicitly removed by painting over 'E'/'O').
     * Only the first change at each map cell during a drag is kept, preserving the true
     * "before" state.
     */
    private void recordAndPaint(int tx, int ty) {
        int half = brushSize / 2;

        // Snapshot brush area before painting
        record CellSnap(int x, int y, char tile, int diff,
                        TownEntrance entrance, TileState state) {}
        List<CellSnap> snaps = new ArrayList<>();
        for (int dy = -half; dy <= half; dy++) {
            for (int dx = -half; dx <= half; dx++) {
                int x = tx + dx, y = ty + dy;
                if (x < 0 || x >= map.width || y < 0 || y >= map.height) continue;
                TownEntrance re = null;
                if (map.tiles[y][x] == 'E') {
                    final int fx = x, fy = y;
                    re = map.townEntrances.stream()
                            .filter(te -> te.worldX() == fx && te.worldY() == fy)
                            .findFirst().orElse(null);
                }
                int spawnDiff = (map.spawnDifficulty != null) ? map.spawnDifficulty[y][x] : 0;
                TileState st = (map.initialTileStates != null)
                        ? map.initialTileStates.get(x + "," + y) : null;
                snaps.add(new CellSnap(x, y, map.tiles[y][x], spawnDiff, re, st));
            }
        }

        paintAt(tx, ty); // mutates map in place

        // Record cells that changed, first-change-wins per cell during drag
        for (CellSnap s : snaps) {
            char newTile = map.tiles[s.y()][s.x()];
            int  newDiff = map.spawnDifficulty[s.y()][s.x()];
            if ((s.tile() != newTile || s.diff() != newDiff)) {
                int key = s.y() * map.width + s.x();
                pendingDrag.putIfAbsent(key, new MapCommand.TileChange(
                        s.x(), s.y(), s.tile(), newTile, s.diff(), newDiff,
                        s.entrance(), null,
                        (s.tile() != newTile) ? s.state() : null));
            }
        }
    }

    /**
     * Like {@link #recordAndPaint} but for the SPAWN_DIFFICULTY tool.
     * Snapshots the brush area, calls {@link #paintSpawnDifficulty}, then records diffs.
     */
    private void recordAndPaintSpawnDiff(int tx, int ty) {
        int half = brushSize / 2;

        record CellSnap(int x, int y, int diff) {}
        List<CellSnap> snaps = new ArrayList<>();
        for (int dy = -half; dy <= half; dy++) {
            for (int dx = -half; dx <= half; dx++) {
                int x = tx + dx, y = ty + dy;
                if (x >= 0 && x < map.width && y >= 0 && y < map.height) {
                    snaps.add(new CellSnap(x, y, map.spawnDifficulty[y][x]));
                }
            }
        }

        paintSpawnDifficulty(tx, ty); // mutates map in place

        for (CellSnap s : snaps) {
            int newDiff = map.spawnDifficulty[s.y()][s.x()];
            if (s.diff() != newDiff) {
                int key = s.y() * map.width + s.x();
                pendingDrag.putIfAbsent(key, new MapCommand.TileChange(
                        s.x(), s.y(),
                        map.tiles[s.y()][s.x()], map.tiles[s.y()][s.x()],
                        s.diff(), newDiff,
                        null, null));
            }
        }
    }

    // ==================== ZOOM ====================
    public int getTileSize() { return tileSize; }
    public int getCameraX()  { return cameraX; }
    public int getCameraY()  { return cameraY; }
    public int getZoomIndex(){ return zoomIndex; }

    public void restoreView(int camX, int camY, int zoom) {
        zoomIndex = Math.max(0, Math.min(zoom, ZOOM_LEVELS.length - 1));
        tileSize  = ZOOM_LEVELS[zoomIndex];
        cameraX   = camX;
        cameraY   = camY;
        clampCamera();
        editor.updateZoomLabel(tileSize);
        repaint();
    }

    public int getViewW() { return getWidth() / tileSize + 1; }
    public int getViewH() { return getHeight() / tileSize + 1; }

    private void clampCamera() {
        int vw = getViewW();
        int vh = getViewH();
        int maxX = vw >= map.width  ? 0 : map.width  - vw;
        int maxY = vh >= map.height ? 0 : map.height - vh;
        cameraX = Math.max(0, Math.min(cameraX, maxX));
        cameraY = Math.max(0, Math.min(cameraY, maxY));
    }

    public void zoom(int direction, int mouseX, int mouseY) {
        int newIndex = Math.max(0, Math.min(ZOOM_LEVELS.length - 1, zoomIndex + direction));
        if (newIndex == zoomIndex) return;

        // World point under cursor before zoom
        double worldX = cameraX + (double) mouseX / tileSize;
        double worldY = cameraY + (double) mouseY / tileSize;

        zoomIndex = newIndex;
        tileSize = ZOOM_LEVELS[zoomIndex];

        // Adjust camera so the same world point stays under cursor
        cameraX = (int) (worldX - (double) mouseX / tileSize);
        cameraY = (int) (worldY - (double) mouseY / tileSize);
        clampCamera();

        repaint();
        editor.refreshAll();
        editor.updateZoomLabel(tileSize);
    }

    public void resetZoom() {
        zoomIndex = 4;
        tileSize = 32;
        clampCamera();
        repaint();
        editor.refreshAll();
        editor.updateZoomLabel(tileSize);
    }

    // ==================== PAINTING (only visible area) ====================
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;

        g2.setColor(Color.BLACK);
        g2.fillRect(0, 0, getWidth(), getHeight());

        int vw = getViewW();
        int vh = getViewH();
        boolean drawGrid = tileSize >= 12;
        boolean drawText = tileSize >= 16;

        for (int sy = 0; sy < vh; sy++) {
            for (int sx = 0; sx < vw; sx++) {
                int wx = cameraX + sx;
                int wy = cameraY + sy;
                int screenX = sx * tileSize;
                int screenY = sy * tileSize;

                if (wx < 0 || wx >= map.width || wy < 0 || wy >= map.height) continue;

                char tileId = map.tiles[wy][wx];
                TileDefinition def = TileRegistry.getByIdSafe(tileId);

                boolean drewSprite = false;
                if (tileSize >= 16 && def.getSpriteKey() != null) {
                    Image sprite = ImageAssetRegistry.get(def.getSpriteKey());
                    if (sprite != null) {
                        g2.drawImage(sprite, screenX, screenY, tileSize, tileSize, null);
                        drewSprite = true;
                    }
                }
                if (!drewSprite) {
                    g2.setColor(def.getEditorColor());
                    g2.fillRect(screenX, screenY, tileSize, tileSize);
                }

                // Spawn difficulty overlay
                int diff = map.spawnDifficulty != null ? map.spawnDifficulty[wy][wx] : 0;
                if (diff > 0) {
                    int inset = Math.max(1, tileSize / 8);
                    g2.setColor(new Color(255, 40, 40, Math.min(200, diff * 2)));
                    g2.fillRect(screenX + inset, screenY + inset, tileSize - inset * 2, tileSize - inset * 2);

                    if (drawText) {
                        g2.setColor(getDifficultyTextColor(diff));
                        int fontSize = Math.max(8, tileSize * 13 / 32);
                        g2.setFont(new Font("Monospaced", Font.BOLD, fontSize));
                        String num = String.valueOf(diff);
                        int tw = g2.getFontMetrics().stringWidth(num);
                        g2.drawString(num, screenX + tileSize / 2 - tw / 2, screenY + tileSize * 23 / 32);
                    }
                }

                if (drawGrid) {
                    g2.setColor(new Color(40, 40, 40));
                    g2.drawRect(screenX, screenY, tileSize, tileSize);
                }

                // Cyan dot: tile has authored initial state
                if (map.initialTileStates != null
                        && map.initialTileStates.containsKey(wx + "," + wy)) {
                    g2.setColor(new Color(0, 220, 255, 200));
                    g2.fillOval(screenX + tileSize - 7, screenY + 1, 5, 5);
                }
            }
        }

        // NPCs & Town Entrances (only draw visible ones)
        int markerInset = Math.max(1, tileSize / 4);
        int markerSize = Math.max(2, tileSize / 2);

        for (NPC npc : map.npcs) {
            int sx = npc.getX() - cameraX;
            int sy = npc.getY() - cameraY;
            if (sx < 0 || sx >= vw || sy < 0 || sy >= vh) continue;
            g2.setColor(Color.YELLOW);
            g2.fillOval(sx * tileSize + markerInset, sy * tileSize + markerInset, markerSize, markerSize);
            if (drawText) {
                g2.setColor(Color.BLACK);
                g2.drawString("N", sx * tileSize + tileSize * 12 / 32, sy * tileSize + tileSize * 22 / 32);
            }
        }

        for (TownEntrance te : map.townEntrances) {
            int sx = te.worldX() - cameraX;
            int sy = te.worldY() - cameraY;
            if (sx < 0 || sx >= vw || sy < 0 || sy >= vh) continue;
            g2.setColor(new Color(255, 215, 0));
            int rectInset = Math.max(1, tileSize * 6 / 32);
            int rectSize = Math.max(2, tileSize * 20 / 32);
            g2.fillRect(sx * tileSize + rectInset, sy * tileSize + rectInset, rectSize, rectSize);
            if (drawText) {
                g2.setColor(Color.BLACK);
                g2.drawString("E", sx * tileSize + tileSize * 12 / 32, sy * tileSize + tileSize * 22 / 32);
            }
        }

        // Teleport destination markers — show tiles with teleport_map instance data
        if (map.initialTileStates != null) {
            for (var entry : map.initialTileStates.entrySet()) {
                TileState tst = entry.getValue();
                if (tst != null && tst.data.containsKey("teleport_map")) {
                    String[] parts = entry.getKey().split(",");
                    if (parts.length != 2) continue;
                    int wx, wy;
                    try { wx = Integer.parseInt(parts[0]); wy = Integer.parseInt(parts[1]); }
                    catch (NumberFormatException ignored) { continue; }
                    int sx = wx - cameraX, sy = wy - cameraY;
                    if (sx < 0 || sx >= vw || sy < 0 || sy >= vh) continue;
                    g2.setColor(new Color(0, 200, 255));
                    int rectInset = Math.max(1, tileSize * 6 / 32);
                    int rectSize = Math.max(2, tileSize * 20 / 32);
                    g2.fillRect(sx * tileSize + rectInset, sy * tileSize + rectInset, rectSize, rectSize);
                    if (drawText) {
                        g2.setColor(Color.BLACK);
                        g2.drawString("T", sx * tileSize + tileSize * 12 / 32, sy * tileSize + tileSize * 22 / 32);
                    }
                }
            }
        }

        // Selection rectangle overlay
        if (selX1 >= 0 && selY1 >= 0 && selX2 >= 0 && selY2 >= 0) {
            int rx1 = Math.min(selX1, selX2), rx2 = Math.max(selX1, selX2);
            int ry1 = Math.min(selY1, selY2), ry2 = Math.max(selY1, selY2);
            int sx1 = (rx1 - cameraX) * tileSize;
            int sy1 = (ry1 - cameraY) * tileSize;
            int sw = (rx2 - rx1 + 1) * tileSize;
            int sh = (ry2 - ry1 + 1) * tileSize;
            g2.setColor(new Color(0, 200, 255, 40));
            g2.fillRect(sx1, sy1, sw, sh);
            g2.setColor(new Color(0, 200, 255, 200));
            g2.setStroke(new java.awt.BasicStroke(2f));
            g2.drawRect(sx1, sy1, sw, sh);
            g2.setStroke(new java.awt.BasicStroke(1f));
            // Selection size label
            if (drawText) {
                String selLabel = (rx2 - rx1 + 1) + "×" + (ry2 - ry1 + 1);
                g2.setFont(new Font("Monospaced", Font.BOLD, 11));
                g2.setColor(new Color(0, 0, 0, 180));
                g2.fillRect(sx1, sy1 - 16, g2.getFontMetrics().stringWidth(selLabel) + 6, 15);
                g2.setColor(new Color(0, 200, 255));
                g2.drawString(selLabel, sx1 + 3, sy1 - 4);
            }
        }

        // Clipboard paste preview at hover position
        if (clipboard != null && clipboard.length > 0 && tool == Tool.SELECT && hoverTileX >= 0 && hoverTileY >= 0 && !selecting
                && selX1 < 0) {
            int pw = clipboard[0].length;
            int ph = clipboard.length;
            int px = (hoverTileX - cameraX) * tileSize;
            int py = (hoverTileY - cameraY) * tileSize;
            g2.setColor(new Color(255, 200, 50, 50));
            g2.fillRect(px, py, pw * tileSize, ph * tileSize);
            g2.setColor(new Color(255, 200, 50, 180));
            g2.setStroke(new java.awt.BasicStroke(2f, java.awt.BasicStroke.CAP_BUTT,
                    java.awt.BasicStroke.JOIN_MITER, 10f, new float[]{6f, 4f}, 0f));
            g2.drawRect(px, py, pw * tileSize, ph * tileSize);
            g2.setStroke(new java.awt.BasicStroke(1f));
        }

        // Bottom-right overlay: tile coords, brush size, zoom
        g2.setFont(new Font("Monospaced", Font.BOLD, 13));
        int zoomPct = tileSize * 100 / 32;
        String tileCoord = hoverTileX >= 0 && hoverTileY >= 0
            ? "Tile: " + hoverTileX + ", " + hoverTileY + "  " : "Tile: --  ";
        String overlayText = tileCoord + "Brush: " + brushSize + "×" + brushSize + "  Zoom: " + zoomPct + "%";
        int textW = g2.getFontMetrics().stringWidth(overlayText);
        g2.setColor(new Color(0, 0, 0, 160));
        g2.fillRect(getWidth() - textW - 16, getHeight() - 24, textW + 12, 20);
        g2.setColor(Color.WHITE);
        g2.drawString(overlayText, getWidth() - textW - 10, getHeight() - 8);
    }

    private Color getDifficultyTextColor(int diff) {
        if (diff == 1) return new Color(0, 255, 80);
        if (diff == 2) return new Color(80, 255, 0);
        if (diff <= 5) return new Color(180, 255, 0);
        if (diff <= 10) return Color.YELLOW;
        if (diff <= 20) return new Color(255, 200, 0);
        if (diff <= 35) return new Color(255, 120, 0);
        if (diff <= 55) return new Color(255, 60, 0);
        if (diff <= 75) return Color.RED;
        return new Color(255, 0, 200);
    }

    // ==================== PAINTING HELPERS (unchanged logic) ====================
    private void paintAt(int tx, int ty) {
        if (tx < 0 || ty < 0 || tx >= map.width || ty >= map.height) return;

        if (tool == Tool.PENCIL) {
            int half = brushSize / 2;
            for (int dy = -half; dy <= half; dy++) {
                for (int dx = -half; dx <= half; dx++) {
                    int x = tx + dx;
                    int y = ty + dy;
                    if (x >= 0 && x < map.width && y >= 0 && y < map.height) {
                        if (map.tiles[y][x] == brushTile) continue;
                        if (map.tiles[y][x] == 'E') {
                            final int fx = x, fy = y;
                            map.townEntrances.removeIf(te -> te.worldX() == fx && te.worldY() == fy);
                        }
                        // Teleport destinations live in initialTileStates — a repainted cell
                        // is no longer that tile, so its authored state goes with it.
                        takeTileState(x, y);
                        map.tiles[y][x] = brushTile;
                    }
                }
            }
        }
        else if (tool == Tool.FILL) {
            floodFill(tx, ty, brushTile);
        }
        // Rectangle tool can be expanded later if needed

        editor.refreshAll();
    }

    private void paintSpawnDifficulty(int tx, int ty) {
        if (tx < 0 || ty < 0 || tx >= map.width || ty >= map.height) return;

        int half = brushSize / 2;
        for (int dy = -half; dy <= half; dy++) {
            for (int dx = -half; dx <= half; dx++) {
                int x = tx + dx;
                int y = ty + dy;
                if (x >= 0 && x < map.width && y >= 0 && y < map.height) {
                    map.spawnDifficulty[y][x] = spawnDifficultyValue;
                }
            }
        }
        editor.refreshAll();
    }

    private void floodFill(int x, int y, char newTile) {
        char old = map.tiles[y][x];
        if (old == newTile) return;

        java.util.Queue<Point> q = new java.util.LinkedList<>();
        q.add(new Point(x, y));

        while (!q.isEmpty()) {
            Point p = q.poll();
            if (p.x < 0 || p.x >= map.width || p.y < 0 || p.y >= map.height
                || map.tiles[p.y][p.x] != old) continue;

            map.tiles[p.y][p.x] = newTile;

            q.add(new Point(p.x - 1, p.y));
            q.add(new Point(p.x + 1, p.y));
            q.add(new Point(p.x, p.y - 1));
            q.add(new Point(p.x, p.y + 1));
        }
    }

    private void showTileContextMenu(MouseEvent e, int tx, int ty) {
        char ch = map.tiles[ty][tx];
        TileDefinition def = TileRegistry.getByIdSafe(ch);
        if (def == null) return;

        JPopupMenu popup = new JPopupMenu();

        if (ch == 'E') {
            // Town entrance — show linked town name in header
            TownEntrance linked = null;
            for (TownEntrance te : map.townEntrances) {
                if (te.worldX() == tx && te.worldY() == ty) { linked = te; break; }
            }
            String townLabel = linked != null ? "Town Entrance: " + linked.townName() : "Town Entrance (unlinked)";
            JMenuItem header = new JMenuItem(townLabel + " at (" + tx + ", " + ty + ")");
            header.setEnabled(false);
            popup.add(header);
            popup.addSeparator();
            JMenuItem editTown = new JMenuItem("Edit Town Entrance\u2026");
            editTown.addActionListener(ae -> new TownPlacementDialog(editor, MapCanvas.this, map, tx, ty));
            popup.add(editTown);

        } else if (ch == 'D') {
            // Dungeon entrance — show dungeon name (or Procedural) in header
            String dungeonName = null;
            if (map.initialTileStates != null) {
                TileState ts = map.initialTileStates.get(tx + "," + ty);
                if (ts != null && ts.data != null) dungeonName = ts.data.get("dungeonName");
            }
            String dungeonLabel = dungeonName != null ? "Dungeon: " + dungeonName : "Dungeon: Procedural";
            JMenuItem header = new JMenuItem(dungeonLabel + " at (" + tx + ", " + ty + ")");
            header.setEnabled(false);
            popup.add(header);
            popup.addSeparator();
            final String resolvedName = dungeonName;
            JMenuItem editDungeon = new JMenuItem("Edit Dungeon Entrance\u2026");
            editDungeon.addActionListener(ae -> new DungeonPlacementDialog(editor, MapCanvas.this, map, tx, ty, resolvedName));
            popup.add(editDungeon);

        } else {
            JMenuItem header = new JMenuItem("[" + ch + "] " + def.getName() + " at (" + tx + ", " + ty + ")");
            header.setEnabled(false);
            popup.add(header);
            popup.addSeparator();
        }

        JMenuItem editItem = new JMenuItem("Edit Tile Instance State\u2026");
        editItem.addActionListener(ae -> new TileInstanceDialog(editor, MapCanvas.this, map, tx, ty));
        popup.add(editItem);

        boolean hasState = map.initialTileStates != null
                && map.initialTileStates.containsKey(tx + "," + ty);
        if (hasState) {
            JMenuItem clearItem = new JMenuItem("Clear Instance State");
            clearItem.addActionListener(ae -> {
                TileState removed = takeTileState(tx, ty);
                if (removed != null) {
                    pushCommand(new MapCommand.TileStateCommand(tx, ty, removed, null));
                }
                repaint();
            });
            popup.add(clearItem);
        }

        popup.show(this, e.getX(), e.getY());
    }

    private void handleNPCPlacer(MouseEvent e, int tx, int ty) {
        // Find if we clicked near an existing NPC
        NPC clickedNPC = null;
        for (NPC n : map.npcs) {
            if (Math.abs(n.getX() - tx) <= 1 && Math.abs(n.getY() - ty) <= 1) {
                clickedNPC = n;
                break;
            }
        }

        if (clickedNPC != null) {
            // Right-click = Delete
            if (SwingUtilities.isRightMouseButton(e)) {
                int confirm = JOptionPane.showConfirmDialog(editor,
                        "Delete NPC '" + clickedNPC.getName() + "'?",
                        "Confirm Delete", JOptionPane.YES_NO_OPTION);

                if (confirm == JOptionPane.YES_OPTION) {
                    pushCommand(new MapCommand.NpcRemoveCommand(clickedNPC));
                    map.npcs.remove(clickedNPC);
                    editor.refreshAll();
                }
                return;
            }
            // Left-click on existing NPC does nothing (double-click already opens editor)
            return;
        }

        // Place new NPC
        JComboBox<String> combo = editor.getNpcTemplateCombo();
        String selected = (String) combo.getSelectedItem();
        if (selected == null || selected.isEmpty()) selected = "townsman";

        // ImageAssetRegistry keys carry no file extension — appending ".png" here made every
        // placed NPC miss its sprite and fall back to the generic townsman.
        String spriteKey = "npcs/" + selected.split("\\(")[0].trim();

        String niceName = selected.split("\\(")[0].trim().replace("_", " ");
        niceName = niceName.substring(0, 1).toUpperCase() + niceName.substring(1);

        NPC newNPC = new NPC(null, niceName,
                            spriteKey, NPC.Type.TOWNSFOLK,
                            "Hello traveler...", tx, ty);

        map.npcs.add(newNPC);
        pushCommand(new MapCommand.NpcAddCommand(newNPC));
        editor.refreshAll();
    }

    public void setSpawnDifficultyValue(int value) {
        this.spawnDifficultyValue = Math.max(0, Math.min(99, value));
    }

    public int getBrushSize() { return brushSize; }
    public int getSpawnDifficultyValue() { return spawnDifficultyValue; }
}

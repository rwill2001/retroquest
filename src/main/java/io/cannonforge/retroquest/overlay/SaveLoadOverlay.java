package io.cannonforge.retroquest.overlay;
import static io.cannonforge.retroquest.overlay.OverlayTheme.AMBER;
import static io.cannonforge.retroquest.overlay.OverlayTheme.BG;
import static io.cannonforge.retroquest.overlay.OverlayTheme.BORDER_COL;
import static io.cannonforge.retroquest.overlay.OverlayTheme.CYAN_ACC;
import static io.cannonforge.retroquest.overlay.OverlayTheme.DANGER;
import static io.cannonforge.retroquest.overlay.OverlayTheme.F_ITEM;
import static io.cannonforge.retroquest.overlay.OverlayTheme.F_KEY;
import static io.cannonforge.retroquest.overlay.OverlayTheme.F_SMALL;
import static io.cannonforge.retroquest.overlay.OverlayTheme.F_TITLE;
import static io.cannonforge.retroquest.overlay.OverlayTheme.PHOSPHOR;
import static io.cannonforge.retroquest.overlay.OverlayTheme.ROW_ALT;
import static io.cannonforge.retroquest.overlay.OverlayTheme.TEXT_BRIGHT;
import static io.cannonforge.retroquest.overlay.OverlayTheme.TEXT_DIM;
import static io.cannonforge.retroquest.overlay.OverlayTheme.entryEase;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import com.google.gson.Gson;

import io.cannonforge.retroquest.core.GamePanel;
import io.cannonforge.retroquest.core.MessageLog;
import io.cannonforge.retroquest.core.Retroquest;
import io.cannonforge.retroquest.core.SaveData;

/**
 * In-panel save/load overlay — renders inside {@link GamePanel} without a JDialog.
 *
 * <p>Slides in from the bottom and shows 8 numbered save slots. Supports
 * saving, loading, and deleting slots with a confirmation step for destructive actions.
 * Open via {@link #openSave(boolean)} or {@link #openLoad()}.
 */
public class SaveLoadOverlay {

    private static final Color SEL_BG = new Color(0, 40, 60);

    private static final int SLOTS = 8;

    private final Retroquest game;
    private boolean active      = false;
    private boolean saveMode    = true;
    private boolean quitOnSave  = false;
    private int     selected    = 0;
    private long    entryTime   = 0;
    private static final long ENTRY_MS = 300;

    // Slot data
    private final String[]   slotLabels = new String[SLOTS];
    private final SaveData[] slotData   = new SaveData[SLOTS];

    // Auto-save data (load mode only)
    private SaveData autoSaveData;
    private String   autoSaveLabel;

    // Confirm-overwrite state — confirmSlot is captured when the prompt is raised so
    // that moving the selection afterwards cannot redirect the delete/overwrite.
    private boolean awaitingConfirm = false;
    private int     confirmSlot     = -1;


    // Cached layout (used for mouse hit-testing in handleClick)
    private int lastRowH, lastRowTop, lastRowCount;
    private java.awt.Rectangle lastCardRect = new java.awt.Rectangle();
    private java.awt.Rectangle[] lastKeyBarRects = new java.awt.Rectangle[0];

    public SaveLoadOverlay(Retroquest game) {
        this.game = game;
    }

    public boolean isActive() { return active; }

    // ── Open ──────────────────────────────────────────────────────────────────

    public void openSave(boolean quitAfter) {
        saveMode       = true;
        quitOnSave     = quitAfter;
        awaitingConfirm = false;
        pendingDelete  = false;
        confirmSlot    = -1;
        selected       = 0;
        entryTime      = System.currentTimeMillis();
        loadSlots();
        active = true;
    }

    public void openLoad() {
        saveMode       = false;
        quitOnSave     = false;
        awaitingConfirm = false;
        pendingDelete  = false;
        confirmSlot    = -1;
        selected       = 0;
        entryTime      = System.currentTimeMillis();
        loadSlots();
        active = true;
    }

    /** Dismissed without saving or loading — lets whoever opened us take over again. */
    public void close() { close(true); }

    private void close(boolean cancelled) {
        active          = false;
        awaitingConfirm = false;
        pendingDelete   = false;
        confirmSlot     = -1;
        if (cancelled) game.saveLoadCancelled();
        else           game.setSaveLoadCancelHook(null);
    }

    // ── Slot loading ──────────────────────────────────────────────────────────

    private void loadSlots() {
        for (int i = 0; i < SLOTS; i++) {
            File f = slotFile(i + 1);
            if (f.exists()) {
                try (InputStreamReader r = new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8)) {
                    SaveData sd = new Gson().fromJson(r, SaveData.class);
                    slotData[i]   = sd;
                    slotLabels[i] = sd.getDisplayName();
                } catch (Exception e) {
                    slotData[i]   = null;
                    slotLabels[i] = String.format("%02d — [CORRUPT]", i + 1);
                }
            } else {
                slotData[i]   = null;
                slotLabels[i] = String.format("%02d — [EMPTY]", i + 1);
            }
        }

        // Load auto-save file
        File autoFile = new File("saves/retroquest_save_auto.sav");
        if (autoFile.exists()) {
            try (InputStreamReader r = new InputStreamReader(new FileInputStream(autoFile), StandardCharsets.UTF_8)) {
                autoSaveData  = new Gson().fromJson(r, SaveData.class);
                autoSaveLabel = autoSaveData.getDisplayName();
            } catch (Exception e) {
                autoSaveData  = null;
                autoSaveLabel = "AUTO — [CORRUPT]";
            }
        } else {
            autoSaveData  = null;
            autoSaveLabel = null;
        }
    }

    private File slotFile(int slot) {
        if (slot == 0) return new File("saves/retroquest_save_auto.sav");
        return new File("saves/retroquest_save_" + String.format("%02d", slot) + ".sav");
    }

    /** Total visible rows: load mode shows auto-save row + 8 regular; save mode shows 8. */
    private int totalSlots() {
        return saveMode ? SLOTS : (autoSaveLabel != null ? SLOTS + 1 : SLOTS);
    }

    /** Maps a visible row index to a save slot number (0=auto, 1-8=regular). */
    private int slotForIndex(int index) {
        if (!saveMode && autoSaveLabel != null) {
            return index == 0 ? 0 : index;  // index 0→slot 0 (auto), 1-8→slots 1-8
        }
        return index + 1;  // save mode: index 0-7 → slots 1-8
    }

    // ── Input ─────────────────────────────────────────────────────────────────

    public void handleKey(KeyEvent e) {
        if (!active) return;

        if (awaitingConfirm) {
            handleConfirmKey(e);
            return;
        }

        switch (e.getKeyCode()) {
            case KeyEvent.VK_ESCAPE -> close();
            case KeyEvent.VK_UP,   KeyEvent.VK_K -> { if (selected > 0)              selected--; }
            case KeyEvent.VK_DOWN, KeyEvent.VK_J -> { if (selected < totalSlots() - 1) selected++; }
            case KeyEvent.VK_ENTER               -> performAction(slotForIndex(selected), false);
            case KeyEvent.VK_D                   -> { if (saveMode) deleteSelected(); }
        }
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    private void performAction(int slot, boolean confirmedOverwrite) {
        if (slot < 0) return;
        File f = slotFile(slot);

        if (saveMode) {
            if (f.exists() && !confirmedOverwrite) {
                awaitingConfirm = true;
                confirmSlot     = slot;
                game.log("Overwrite slot " + slot + "?  [Y] Yes  [Any] Cancel", MessageLog.Type.DANGER);
                return;
            }
            close(false);
            boolean saved = game.saveGame(slot);
            if (quitOnSave) {
                if (!saved) {   // never quit on a failed save — the session is all the player has
                    game.log("Not quitting: the save failed. Try another slot.", MessageLog.Type.DANGER);
                    return;
                }
                quitOnSave = false;
                System.exit(0);
            }
        } else {
            if (!f.exists()) { game.log("No save in that slot.", MessageLog.Type.DIM); return; }
            close(false);
            game.loadGame(slot);
        }
    }

    private void deleteSelected() {
        int slot = slotForIndex(selected);
        File f = slotFile(slot);
        if (!f.exists()) { game.log("That slot is already empty.", MessageLog.Type.DIM); return; }
        awaitingConfirm = true;
        confirmSlot     = slot;
        game.log("Delete slot " + slot + " permanently?  [Y] Yes  [Any] Cancel", MessageLog.Type.DANGER);
        // Temporarily repurpose awaitingConfirm to mean delete — track with a flag
        pendingDelete = true;
    }

    private boolean pendingDelete = false;
    // Override handleKey confirm for delete vs overwrite
    private void handleConfirmKey(KeyEvent e) {
        int slot = confirmSlot;   // the slot the prompt was raised for, not the current selection
        if (e.getKeyCode() == KeyEvent.VK_Y && slot >= 0) {
            if (pendingDelete) {
                slotFile(slot).delete();
                if (slot >= 1 && slot <= SLOTS) {
                    slotData[slot - 1]   = null;
                    slotLabels[slot - 1] = String.format("%02d — [EMPTY]", slot);
                } else {
                    autoSaveData  = null;
                    autoSaveLabel = null;
                }
                game.log("Slot " + slot + " deleted.", MessageLog.Type.DIM);
            } else {
                awaitingConfirm = false;
                pendingDelete   = false;
                confirmSlot     = -1;
                performAction(slot, true);
                return;
            }
        } else {
            game.log("Cancelled.", MessageLog.Type.DIM);
        }
        awaitingConfirm = false;
        pendingDelete   = false;
        confirmSlot     = -1;
    }

    // ── Mouse support ─────────────────────────────────────────────────────────

    public void handleClick(int mx, int my) {
        if (!active) return;
        if (awaitingConfirm) return;                  // answer the [Y]/[Any] prompt first
        if (!lastCardRect.contains(mx, my)) return;   // clicks outside the panel do nothing

        // Key bar buttons
        for (int i = 0; i < lastKeyBarRects.length; i++) {
            if (lastKeyBarRects[i].contains(mx, my)) {
                if (saveMode) {
                    // [Enter=Save, D=Delete, Esc=Close]
                    if (i == 0) performAction(slotForIndex(selected), false);
                    else if (i == 1) deleteSelected();
                    else close();
                } else {
                    // [Enter=Load, Esc=Close]
                    if (i == 0) performAction(slotForIndex(selected), false);
                    else close();
                }
                return;
            }
        }

        // List rows
        if (lastRowH > 0 && lastRowCount > 0
                && my >= lastRowTop && my < lastRowTop + lastRowCount * lastRowH) {
            int row = (my - lastRowTop) / lastRowH;
            if (row >= 0 && row < totalSlots()) {
                if (row == selected) performAction(slotForIndex(row), false);
                else selected = row;
            }
        }
    }

    // ── Paint ─────────────────────────────────────────────────────────────────

    public void paint(Graphics2D g, int W, int H) {
        if (!active) return;
        try {
            doPaint(g, W, H);
        } catch (Exception ex) {
            ex.printStackTrace();
            active = false;
        }
    }

    private void doPaint(Graphics2D g, int W, int H) {
        float ease = entryEase(entryTime, ENTRY_MS);

        // Dim
        g.setColor(new Color(0, 0, 0, (int)(180 * ease)));
        g.fillRect(0, 0, W, H);

        int cW = Math.min(OverlayTheme.scaled(640), W - 40);
        int cH = Math.min(OverlayTheme.scaled(440), H - 40);
        int cX = (W - cW) / 2;
        // Slide up from bottom
        int cY = (int)((H - cH) / 2.0 * ease) + (int)((1f - ease) * H);

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Shadow
        g.setColor(new Color(0, 0, 0, (int)(100 * ease)));
        g.fillRoundRect(cX + 4, cY + 4, cW, cH, 6, 6);

        // Card
        g.setPaint(new GradientPaint(cX, cY, new Color(8, 14, 22), cX, cY + cH, BG));
        g.fillRoundRect(cX, cY, cW, cH, 6, 6);

        // Border — amber for save, cyan for load
        g.setStroke(new BasicStroke(1.5f));
        Color borderCol = saveMode ? AMBER : CYAN_ACC;
        g.setColor(new Color(borderCol.getRed(), borderCol.getGreen(), borderCol.getBlue(), 160));
        g.drawRoundRect(cX, cY, cW, cH, 6, 6);
        g.setStroke(new BasicStroke(1f));

        int titleH  = OverlayTheme.scaled(38);
        int keybarH = OverlayTheme.scaled(34);
        int bodyH   = cH - titleH - keybarH;

        lastCardRect = new java.awt.Rectangle(cX, cY, cW, cH);

        paintTitle  (g, cX, cY,               cW, titleH);
        paintSlots  (g, cX, cY + titleH,       cW, bodyH);
        paintKeyBar (g, cX, cY + titleH + bodyH, cW, keybarH);
    }

    private void paintTitle(Graphics2D g, int x, int y, int w, int h) {
        g.setPaint(new GradientPaint(x, y,
            saveMode ? new Color(20, 16, 6) : new Color(6, 16, 22),
            x + w, y, new Color(8, 12, 16)));
        g.fillRoundRect(x, y, w, h, 6, 6);
        g.fillRect(x, y + h / 2, w, h / 2);
        g.setColor(BORDER_COL);
        g.drawLine(x, y + h, x + w, y + h);

        g.setFont(F_TITLE);
        FontMetrics fm = g.getFontMetrics();
        int ty = y + (h + fm.getAscent() - fm.getDescent()) / 2;
        Color titleCol = saveMode ? AMBER : CYAN_ACC;
        String titleStr = "\u25c8  " + (saveMode ? "SAVE GAME" : "LOAD GAME");

        g.setColor(new Color(titleCol.getRed(), titleCol.getGreen(), titleCol.getBlue(), 35));
        g.drawString(titleStr, x + 15, ty + 1);
        g.setColor(titleCol);
        g.drawString(titleStr, x + 14, ty);

        g.setFont(F_SMALL);
        FontMetrics fmS = g.getFontMetrics();
        String hint = saveMode ? "Select slot to save" : "Select slot to load";
        g.setColor(TEXT_DIM);
        g.drawString(hint, x + w - fmS.stringWidth(hint) - 14, ty);
    }

    private void paintSlots(Graphics2D g, int x, int y, int w, int h) {
        g.setColor(BG);
        g.fillRect(x, y, w, h);

        g.setFont(F_ITEM);
        FontMetrics fm = g.getFontMetrics();
        int rowH    = fm.getHeight() + OverlayTheme.scaled(8);
        int maxRows = Math.max(1, (h - 6) / rowH);
        int total   = totalSlots();

        lastRowH     = rowH;
        lastRowTop   = y + 4;
        lastRowCount = Math.min(total, maxRows);

        int dy = y + 4;
        for (int i = 0; i < total && i < maxRows; i++) {
            boolean sel = (i == selected);
            int slot = slotForIndex(i);
            boolean isAuto = (slot == 0);

            // Resolve label and data for this row
            String rowLabel;
            SaveData rowData;
            if (isAuto) {
                rowLabel = autoSaveLabel;
                rowData  = autoSaveData;
            } else {
                rowLabel = slotLabels[slot - 1];
                rowData  = slotData[slot - 1];
            }

            boolean empty = (!isAuto && rowData == null && !rowLabel.contains("CORRUPT"));

            g.setColor(sel ? SEL_BG : (i % 2 == 0 ? BG : ROW_ALT));
            g.fillRect(x, dy, w, rowH);

            if (sel) {
                Color bc = saveMode ? AMBER : CYAN_ACC;
                g.setColor(bc);
                g.fillRect(x, dy, 3, rowH);
            }

            int textY = dy + rowH - (rowH - fm.getAscent()) / 2 - 1;

            // Slot number badge
            g.setFont(F_KEY);
            FontMetrics fmK = g.getFontMetrics();
            String num = isAuto ? "\u27f3 " : String.format("%2d", slot);
            g.setColor(sel ? (saveMode ? AMBER : CYAN_ACC) : TEXT_DIM);
            g.drawString(num, x + 10, textY);
            int numW = fmK.stringWidth("00") + 18;

            // Status icon
            String icon;
            Color iconCol;
            if (isAuto) {
                boolean corrupt = rowLabel != null && rowLabel.contains("CORRUPT");
                icon    = corrupt ? "\u26a0" : "\u25b6";
                iconCol = corrupt ? DANGER : PHOSPHOR;
            } else {
                icon = empty ? "\u25a1"
                    : rowLabel.contains("CORRUPT") ? "\u26a0"
                    : saveMode ? "\u25c6" : "\u25b6";
                iconCol = empty ? TEXT_DIM
                    : rowLabel.contains("CORRUPT") ? DANGER
                    : saveMode ? AMBER : PHOSPHOR;
            }
            g.setFont(F_ITEM);
            g.setColor(iconCol);
            g.drawString(icon, x + numW, textY);
            int iconW = fm.stringWidth(icon) + 8;

            // Reserve space on the right for the confirm prompt if it will render here
            int confirmReserve = 0;
            String confirmMsg  = null;
            if (sel && awaitingConfirm) {
                confirmMsg = pendingDelete ? "[Y] Delete  [Any] Cancel" : "[Y] Overwrite  [Any] Cancel";
                FontMetrics fmC = g.getFontMetrics(F_KEY);
                confirmReserve = fmC.stringWidth(confirmMsg) + 24;
            }

            // Slot label
            g.setColor(sel ? TEXT_BRIGHT : (empty ? TEXT_DIM : TEXT_BRIGHT));
            String label = rowLabel != null ? rowLabel : "AUTO — [EMPTY]";
            int maxW = w - numW - iconW - 12 - confirmReserve;
            String fullLabel = label;
            while (fm.stringWidth(label) > maxW && label.length() > 1)
                label = label.substring(0, label.length() - 1);
            if (fm.stringWidth(fullLabel) > maxW) label += "\u2026";
            g.drawString(label, x + numW + iconW, textY);

            // Confirm prompt overlay on selected row
            if (confirmMsg != null) {
                g.setFont(F_KEY);
                FontMetrics fmC = g.getFontMetrics();
                g.setColor(DANGER);
                g.drawString(confirmMsg, x + w - fmC.stringWidth(confirmMsg) - 12, textY);
            }

            dy += rowH;
        }
    }

    private void paintKeyBar(Graphics2D g, int x, int y, int w, int h) {
        g.setPaint(new GradientPaint(x, y, new Color(8, 12, 18), x, y + h, BG));
        g.fillRect(x, y, w, h);
        g.fillRoundRect(x, y + h / 2, w, h / 2 + 4, 6, 6);
        g.setColor(BORDER_COL);
        g.drawLine(x, y, x + w, y);

        Color accent = saveMode ? AMBER : CYAN_ACC;
        String[][] keys = saveMode
            ? new String[][]{{"Enter", "Save Here"}, {"D", "Delete"}, {"Esc", "Close"}}
            : new String[][]{{"Enter", "Load"},                       {"Esc", "Close"}};
        lastKeyBarRects = OverlayTheme.paintKeyBadges(g, x, y, w, h, keys, accent);
    }
}

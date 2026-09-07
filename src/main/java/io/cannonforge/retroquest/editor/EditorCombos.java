package io.cannonforge.retroquest.editor;

import javax.swing.JComboBox;

/**
 * Combo-box selection that cannot silently discard authored data.
 *
 * <h2>The failure this exists to stop</h2>
 * Every registry editor follows the same shape: populate a combo from the current registry,
 * {@code setSelectedItem(theAuthoredValue)}, and on save write back whatever the combo holds.
 * That is safe only while the authored value is guaranteed to be one of the combo's entries.
 *
 * <p>{@code JComboBox.setSelectedItem} is a <b>silent no-op</b> when the value is absent — it
 * leaves the previous selection in place and reports nothing. So whenever the authored value has
 * drifted from the registry (a renamed sprite, a deleted item, a stored name in a different format
 * from the keys the combo was built with), the form silently shows the *previously inspected
 * record's* value, and the next save stamps that unrelated value onto this record.
 *
 * <p>It is not a hypothetical: no {@code items/} folder exists under
 * {@code src/main/resources/tiles}, so not one of the 140 sprite names in {@code items.json} could
 * ever match a key in {@link io.cannonforge.retroquest.registry.ImageAssetRegistry}. Opening the
 * Item Editor, changing a price and pressing SAVE replaced that item's sprite every single time.
 *
 * <p>{@code TileEditor.keepUnknownId} was the original, local answer to this. These are the same
 * idea made reusable, so a value the registry no longer knows about stays visible, stays
 * selectable, and survives a save.
 */
final class EditorCombos {

    private EditorCombos() {}

    /** Marks an entry that exists only to preserve an authored value the registry has lost. */
    static final String UNKNOWN_SUFFIX = "  (not in registry)";

    /**
     * Selects {@code value}, adding it to the combo first if it is not already there.
     *
     * <p>Use for combos whose entries are the stored values themselves (sprite names, node ids).
     *
     * @return true when the value had to be added, i.e. the registry no longer knows it
     */
    static boolean selectOrKeep(JComboBox<String> combo, String value) {
        if (value == null || value.isEmpty()) {
            combo.setSelectedItem("");
            return false;
        }
        for (int i = 0; i < combo.getItemCount(); i++) {
            if (value.equals(combo.getItemAt(i))) {
                combo.setSelectedItem(value);
                return false;
            }
        }
        // Absent. Adding it is what makes setSelectedItem take, and what makes the save
        // round-trip the authored value instead of the last record's.
        combo.addItem(value);
        combo.setSelectedItem(value);
        return true;
    }

    /**
     * Selects the entry for an id in a combo whose entries are {@code "id — Label"}, keeping the
     * raw id as a visible entry when the registry no longer contains it.
     *
     * @param placeholder the combo's "nothing selected" entry, e.g. {@code "(none)"}
     */
    static void selectByIdOrKeep(JComboBox<String> combo, String id, String placeholder) {
        if (id == null || id.isEmpty()) {
            combo.setSelectedItem(placeholder);
            return;
        }
        for (int i = 0; i < combo.getItemCount(); i++) {
            String entry = combo.getItemAt(i);
            if (entry != null && (entry.equals(id) || entry.startsWith(id + " "))) {
                combo.setSelectedItem(entry);
                return;
            }
        }
        String kept = id + UNKNOWN_SUFFIX;
        combo.addItem(kept);
        combo.setSelectedItem(kept);
    }

    /**
     * The id behind a selection made by {@link #selectByIdOrKeep}, or null for the placeholder.
     * Strips both the {@code " — Label"} suffix and the preserved-value marker.
     */
    static String idFromSelection(Object selection, String placeholder) {
        if (selection == null) return null;
        String s = selection.toString();
        if (s.isEmpty() || s.equals(placeholder)) return null;
        if (s.endsWith(UNKNOWN_SUFFIX)) return s.substring(0, s.length() - UNKNOWN_SUFFIX.length());
        int dash = s.indexOf(" — ");
        return (dash > 0 ? s.substring(0, dash) : s).trim();
    }
}

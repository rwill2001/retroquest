package io.cannonforge.retroquest.model;

/**
 * Represents one fixed slot in the player's 20-slot inventory.
 * Supports stacking for consumables.
 */
public class InventorySlot {
    private Item item;
    private int quantity;

    public InventorySlot() {} // Gson

    public InventorySlot(Item item, int quantity) {
        this.item = item;
        this.quantity = quantity;
    }

    public Item getItem() { return item; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public boolean isEmpty() { return item == null; }

    public void addQuantity(int amount) {
        if (item != null) quantity = Math.min(item.getMaxStackSize(), quantity + amount);
    }
}

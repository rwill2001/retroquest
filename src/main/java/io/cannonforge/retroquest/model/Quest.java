package io.cannonforge.retroquest.model;
import io.cannonforge.retroquest.registry.ItemRegistry;

/**
 * Represents a quest that the player can accept, progress, and complete.
 *
 * <p>Quests are identified by a unique string ID and have a type (KILL, COLLECT, TALK,
 * DELIVER, EXPLORE), a target, a required amount, and gold/XP/item rewards.
 */
public class Quest {
    public enum Type { KILL, COLLECT, TALK, DELIVER, EXPLORE }

    private String id;
    private String title;
    private String description;
    private Type type;
    private String target;
    private int requiredAmount;
    private int goldReward;
    private int xpReward;
    private String itemRewardId;
    private String spellRewardId;
    private String giverName;
    private String deliverItemId;
    private String prereqQuestId;

    private int progress = 0;
    private Status status = Status.AVAILABLE;

    private boolean repeatable = false;
    private int minAmount = 0;   // 0 = use fixed requiredAmount
    private int maxAmount = 0;

    public enum Status { AVAILABLE, IN_PROGRESS, COMPLETED }

    // Gson no-arg constructor
    public Quest() {
        this.progress = 0;
        this.status = Status.AVAILABLE;
    }

    // Full constructor for editor/registry (templates)
    public Quest(String id, String title, String description, Type type, String target,
                 int requiredAmount, int goldReward, int xpReward, String itemRewardId) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.type = type;
        this.target = target;
        this.requiredAmount = requiredAmount;
        this.goldReward = goldReward;
        this.xpReward = xpReward;
        this.itemRewardId = itemRewardId;
    }

    /** Creates a fresh playable instance (randomizes if configured) */
    public Quest createInstance() {
        Quest copy = new Quest(id, title, description, type, target,
                requiredAmount, goldReward, xpReward, itemRewardId);

        copy.repeatable = this.repeatable;
        copy.minAmount = this.minAmount;
        copy.maxAmount = this.maxAmount;
        copy.giverName = this.giverName;
        copy.deliverItemId = this.deliverItemId;
        copy.prereqQuestId = this.prereqQuestId;

        // Randomize amount for KILL and COLLECT quests
        if ((type == Type.KILL || type == Type.COLLECT) && minAmount > 0 && maxAmount >= minAmount) {
            copy.requiredAmount = minAmount + (int)(Math.random() * (maxAmount - minAmount + 1));
        }

        // Replace {amount} token in description with the actual number
        copy.description = copy.description.replace("{amount}", String.valueOf(copy.requiredAmount));

        // Always start fresh
        copy.progress = 0;
        copy.status = Status.IN_PROGRESS;

        return copy;
    }

    /**
     * Returns a nice, human-readable progress string for the Quest Log.
     */
    public String getProgressText() {
        if (isComplete()) {
            return "COMPLETED";
        }

        String targetName = target != null ? target : "objective";

        return switch (type) {
            case KILL -> String.format("%s killed: %d/%d", targetName, progress, requiredAmount);
            case COLLECT -> String.format("%s collected: %d/%d", targetName, progress, requiredAmount);
            case TALK -> "Talked to " + targetName;
            case DELIVER -> {
                String itemName = deliverItemId != null ? deliverItemId.replace('_', ' ') : "item";
                Item deliverItem = deliverItemId != null ? ItemRegistry.getById(deliverItemId) : null;
                if (deliverItem != null) itemName = deliverItem.getName();
                yield String.format("Deliver %s to %s: %d/%d", itemName, targetName, progress, requiredAmount);
            }
            case EXPLORE -> "Exploration progress: " + progress + "/" + requiredAmount;
            default -> progress + "/" + requiredAmount;
        };
    }

    public boolean isRepeatable() { return repeatable; }
    public void setRepeatable(boolean repeatable) { this.repeatable = repeatable; }
    public int getMinAmount() { return minAmount; }
    public void setMinAmount(int minAmount) { this.minAmount = minAmount; }
    public int getMaxAmount() { return maxAmount; }
    public void setMaxAmount(int maxAmount) { this.maxAmount = maxAmount; }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public Type getType() { return type; }
    public String getTarget() { return target; }
    public int getRequiredAmount() { return requiredAmount; }
    public int getProgress() { return progress; }
    public int getGoldReward() { return goldReward; }
    public int getXpReward() { return xpReward; }
    public String getItemRewardId() { return itemRewardId; }
    public String getSpellRewardId() { return spellRewardId; }
    public Quest withSpellRewardId(String name) { this.spellRewardId = name; return this; }
    public Quest withItemRewardId(String id) { this.itemRewardId = id; return this; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public String getGiverName() { return giverName; }
    public void setGiverName(String giverName) { this.giverName = giverName; }
    public String getDeliverItemId() { return deliverItemId; }
    public Quest withDeliverItemId(String id) { this.deliverItemId = id; return this; }
    public String getPrereqQuestId() { return prereqQuestId; }
    public void setPrereqQuestId(String id) { this.prereqQuestId = id; }

    public void progress(int amount) {
        if (status != Status.IN_PROGRESS) return;
        progress += amount;
        if (progress < 0) progress = 0;
        if (progress >= requiredAmount) progress = requiredAmount;
    }

    public boolean isComplete() {
        return progress >= requiredAmount;
    }

    public Item getItemReward() {
        if (itemRewardId == null || itemRewardId.trim().isEmpty()) return null;
        return ItemRegistry.getById(itemRewardId);
    }
}

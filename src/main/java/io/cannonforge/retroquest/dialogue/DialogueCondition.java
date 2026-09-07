package io.cannonforge.retroquest.dialogue;
import io.cannonforge.retroquest.model.InventorySlot;
import io.cannonforge.retroquest.model.Player;

/**
 * Branch predicate for dialogue trees -- evaluated to show/hide choices or gate content.
 */
public class DialogueCondition {

    public enum Type {
        QUEST_COMPLETE,   // target = questId
        QUEST_ACTIVE,     // target = questId
        HAS_ITEM,         // target = item ID, amount = min count
        HAS_GOLD,         // amount = min gold
        PLAYER_LEVEL,     // amount = min level
        PLAYER_STAT,      // target = "str"/"dex"/etc, amount = min
        FLAG_EQUALS,      // target = flag key, value = expected
        FLAG_SET,         // target = flag key (non-null)
        FLAG_NOT_SET      // target = flag key (null)
    }

    private Type type;
    private String target;
    private String value;
    private int amount;
    private boolean negate;

    // Gson no-arg constructor
    public DialogueCondition() {}

    public DialogueCondition(Type type, String target, String value, int amount, boolean negate) {
        this.type = type;
        this.target = target;
        this.value = value;
        this.amount = amount;
        this.negate = negate;
    }

    /**
     * Evaluates this condition against the given player state.
     *
     * @return true if the condition is met (respecting negate flag)
     */
    public boolean evaluate(Player player) {
        boolean result = evaluateRaw(player);
        return negate ? !result : result;
    }

    private boolean evaluateRaw(Player player) {
        if (type == null) return true;
        switch (type) {
            case QUEST_COMPLETE:
                return player.getCompletedQuests().stream()
                        .anyMatch(q -> q.getId().equals(target));

            case QUEST_ACTIVE:
                return player.getActiveQuests().stream()
                        .anyMatch(q -> q.getId().equals(target));

            case HAS_ITEM:
                if (player.hasKey(target)) return true;
                int count = 0;
                for (InventorySlot slot : player.getInventorySlots()) {
                    if (!slot.isEmpty() && slot.getItem().getId().equals(target)) {
                        count += slot.getQuantity();
                    }
                }
                return count >= Math.max(1, amount);

            case HAS_GOLD:
                return player.getGold() >= amount;

            case PLAYER_LEVEL:
                return player.getLevel() >= amount;

            case PLAYER_STAT:
                return getStatValue(player, target) >= amount;

            case FLAG_EQUALS:
                String flagVal = player.getFlag(target);
                return value != null && value.equals(flagVal);

            case FLAG_SET:
                return player.hasFlag(target);

            case FLAG_NOT_SET:
                return !player.hasFlag(target);

            default:
                return true;
        }
    }

    private int getStatValue(Player player, String stat) {
        if (stat == null) return 0;
        return switch (stat.toLowerCase()) {
            case "str" -> player.getStr();
            case "dex" -> player.getDex();
            case "con" -> player.getCon();
            case "int" -> player.getIntelligence();
            case "wis" -> player.getWisdom();
            case "cha" -> player.getCharisma();
            default -> 0;
        };
    }

    // Getters
    public Type getType() { return type; }
    public String getTarget() { return target; }
    public String getValue() { return value; }
    public int getAmount() { return amount; }
    public boolean isNegate() { return negate; }

    // Setters (used by DialogueTreeEditor)
    public void setType(Type type) { this.type = type; }
    public void setTarget(String target) { this.target = target; }
    public void setValue(String value) { this.value = value; }
    public void setAmount(int amount) { this.amount = amount; }
    public void setNegate(boolean negate) { this.negate = negate; }
}

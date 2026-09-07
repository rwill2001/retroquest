package io.cannonforge.retroquest.dialogue;

/**
 * Side-effect triggered when a dialogue node is entered.
 */
public class DialogueAction {

    public enum Type {
        GIVE_QUEST,      // target = quest ID
        COMPLETE_QUEST,  // target = quest ID; marks quest complete, applies registry rewards
        GIVE_ITEM,       // target = item ID, amount (default 1)
        GIVE_GOLD,       // amount (negative = take)
        TAKE_ITEM,       // target = item ID, amount
        SET_FLAG,        // target = key, value (default "true")
        CLEAR_FLAG,      // target = key
        OPEN_SHOP,       // closes dialogue, opens shop with NPC's shopItemIds
        OPEN_CASINO,     // closes dialogue, opens casino overlay
        OPEN_ARENA,      // closes dialogue, opens forge arena overlay
        HEAL_PLAYER,     // amount (0 = full)
        TEACH_SPELL,     // target = spell name
        LOG_MESSAGE,     // target = text, value = MessageLog.Type name
        CLOSE_DIALOGUE,  // ends conversation immediately
        SERVE_DRINK,     // amount = gold cost; adds 20 drunk steps
        OPEN_DEPTHS,     // closes dialogue, opens abyssal depths overlay
        OPEN_SKY_GAMES,    // closes dialogue, opens sky games overlay
        OPEN_NATURE_GAMES, // closes dialogue, opens nature games overlay
        OPEN_MEMORY_GAMES, // closes dialogue, opens memory games overlay
        OPEN_WAR_GAMES,    // closes dialogue, opens war games overlay
        ADD_FAVOR          // target = God enum name, amount = delta (may be negative)
    }

    private Type type;
    private String target;
    private String value;
    private int amount;

    // Gson no-arg constructor
    public DialogueAction() {}

    public DialogueAction(Type type, String target, String value, int amount) {
        this.type = type;
        this.target = target;
        this.value = value;
        this.amount = amount;
    }

    // Getters
    public Type getType() { return type; }
    public String getTarget() { return target; }
    public String getValue() { return value; }
    public int getAmount() { return amount; }

    // Setters (used by DialogueTreeEditor)
    public void setType(Type type) { this.type = type; }
    public void setTarget(String target) { this.target = target; }
    public void setValue(String value) { this.value = value; }
    public void setAmount(int amount) { this.amount = amount; }
}

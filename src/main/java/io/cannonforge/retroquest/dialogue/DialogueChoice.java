package io.cannonforge.retroquest.dialogue;
import java.util.List;

import io.cannonforge.retroquest.model.Player;

/**
 * A selectable player response in a dialogue node.
 */
public class DialogueChoice {

    private String label;                        // "What is this place?"
    private String nextNodeId;                   // null = end conversation
    private List<DialogueCondition> conditions;  // ALL must pass; hidden if any fail

    // Gson no-arg constructor
    public DialogueChoice() {}

    public DialogueChoice(String label, String nextNodeId, List<DialogueCondition> conditions) {
        this.label = label;
        this.nextNodeId = nextNodeId;
        this.conditions = conditions;
    }

    /**
     * Returns true if all conditions pass (or if there are no conditions).
     */
    public boolean isAvailable(Player player) {
        if (conditions == null || conditions.isEmpty()) return true;
        for (DialogueCondition c : conditions) {
            if (!c.evaluate(player)) return false;
        }
        return true;
    }

    // Getters
    public String getLabel() { return label; }
    public String getNextNodeId() { return nextNodeId; }
    public List<DialogueCondition> getConditions() { return conditions; }

    // Setters (used by DialogueTreeEditor)
    public void setLabel(String label) { this.label = label; }
    public void setNextNodeId(String nextNodeId) { this.nextNodeId = nextNodeId; }
    public void setConditions(List<DialogueCondition> conditions) { this.conditions = conditions; }
}

package io.cannonforge.retroquest.dialogue;

import java.util.List;

/**
 * A single dialogue screen -- NPC text plus player choices.
 */
public class DialogueNode {

    private String id;                        // unique key: "greeting", "quest_offer"
    private String text;                      // NPC speech (\n for line breaks)
    private List<DialogueChoice> choices;     // player options
    private List<DialogueAction> actions;     // execute on node entry

    // Gson no-arg constructor
    public DialogueNode() {}

    // Getters
    public String getId() { return id; }
    public String getText() { return text; }
    public List<DialogueChoice> getChoices() { return choices; }
    public List<DialogueAction> getActions() { return actions; }

    // Setters (used by DialogueTreeEditor)
    public void setId(String id) { this.id = id; }
    public void setText(String text) { this.text = text; }
    public void setChoices(List<DialogueChoice> choices) { this.choices = choices; }
    public void setActions(List<DialogueAction> actions) { this.actions = actions; }
}

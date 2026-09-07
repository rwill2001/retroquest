package io.cannonforge.retroquest.model;
import java.util.ArrayList;
import java.util.List;

import io.cannonforge.retroquest.dialogue.DialogueTree;

/**
 * Represents a non-player character (NPC) in a town map.
 *
 * <p>NPCs can be townspeople, shopkeepers, innkeepers, guards, trainers,
 * quest givers, blacksmiths, or healers. Each NPC has a screen position,
 * dialog text, and optional quest and shop associations.
 */
public class NPC {
    private String id;
    private String name;
    private String spriteName;
    private Type type;
    private String defaultDialog;
    private String questId;
    private String questCompleteDialog;
    private List<String> shopItemIds = new ArrayList<>();
    private DialogueTree dialogueTree;  // null = legacy fallback
    private int x, y;

    public enum Type {
        TOWNSFOLK, SHOPKEEPER, INNKEEPER, GUARD, TRAINER, QUESTGIVER, BLACKSMITH, HEALER, CASINO
    }

    // Gson no-arg constructor
    public NPC() {}

    // Full rich constructor (used by RetroForge)
    public NPC(String id, String name, String spriteName, Type type, String defaultDialog, int x, int y) {
        this.id = (id != null) ? id : "npc_" + System.currentTimeMillis();
        this.name = name;
        this.spriteName = (spriteName != null) ? spriteName : "npcs/townsman";
        this.type = type;
        this.defaultDialog = defaultDialog;
        this.x = x;
        this.y = y;
    }

    // Backward compatibility constructor (used by Town.java)
    public NPC(String name, Type type, int x, int y, String dialog) {
        this(null, name, "townsman.png", type, dialog, x, y);
    }

    // Getters & Setters
    public String getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSpriteName() { return spriteName; }
    public void setSpriteName(String spriteName) { this.spriteName = spriteName; }
    public Type getType() { return type; }
    public void setType(Type type) { this.type = type; }
    public String getDefaultDialog() { return defaultDialog; }
    public void setDefaultDialog(String dialog) { this.defaultDialog = dialog; }
    public String getQuestId() { return questId; }
    public void setQuestId(String questId) { this.questId = questId; }
    public String getQuestCompleteDialog() { return questCompleteDialog; }
    public void setQuestCompleteDialog(String dialog) { this.questCompleteDialog = dialog; }
    public List<String> getShopItemIds() { return shopItemIds; }
    public DialogueTree getDialogueTree() { return dialogueTree; }
    public void setDialogueTree(DialogueTree t) { this.dialogueTree = t; }
    public int getX() { return x; }
    public int getY() { return y; }
    public void setPosition(int x, int y) { this.x = x; this.y = y; }
}

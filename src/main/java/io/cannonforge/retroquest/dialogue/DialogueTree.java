package io.cannonforge.retroquest.dialogue;

import java.util.Map;

/**
 * Complete dialogue tree stored on an NPC. Uses a map for O(1) node lookup
 * and clean JSON serialization via Gson.
 */
public class DialogueTree {

    private String startNodeId;
    private Map<String, DialogueNode> nodes;  // nodeId -> node

    // Gson no-arg constructor
    public DialogueTree() {}

    public DialogueNode getStartNode() {
        return nodes != null ? nodes.get(startNodeId) : null;
    }

    public DialogueNode getNode(String id) {
        return nodes != null ? nodes.get(id) : null;
    }

    // Getters
    public String getStartNodeId() { return startNodeId; }
    public Map<String, DialogueNode> getNodes() { return nodes; }

    // Setters (used by DialogueTreeEditor)
    public void setStartNodeId(String id) { this.startNodeId = id; }
    public void setNodes(Map<String, DialogueNode> nodes) { this.nodes = nodes; }
    public void putNode(String id, DialogueNode node) {
        if (nodes == null) nodes = new java.util.LinkedHashMap<>();
        nodes.put(id, node);
    }
    public void removeNode(String id) {
        if (nodes != null) nodes.remove(id);
    }
}

package io.cannonforge.retroquest.overlay;
import static io.cannonforge.retroquest.overlay.OverlayTheme.AMBER;
import static io.cannonforge.retroquest.overlay.OverlayTheme.BG;
import static io.cannonforge.retroquest.overlay.OverlayTheme.BORDER_COL;
import static io.cannonforge.retroquest.overlay.OverlayTheme.F_ITEM;
import static io.cannonforge.retroquest.overlay.OverlayTheme.F_KEY;
import static io.cannonforge.retroquest.overlay.OverlayTheme.F_TITLE;
import static io.cannonforge.retroquest.overlay.OverlayTheme.PANEL_BG;
import static io.cannonforge.retroquest.overlay.OverlayTheme.PHOSPHOR;
import static io.cannonforge.retroquest.overlay.OverlayTheme.TEXT_BRIGHT;
import static io.cannonforge.retroquest.overlay.OverlayTheme.TEXT_DIM;
import static io.cannonforge.retroquest.overlay.OverlayTheme.entryEase;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

import io.cannonforge.retroquest.core.MessageLog;
import io.cannonforge.retroquest.core.Retroquest;
import io.cannonforge.retroquest.core.SoundManager;
import io.cannonforge.retroquest.dialogue.DialogueAction;
import io.cannonforge.retroquest.dialogue.DialogueChoice;
import io.cannonforge.retroquest.dialogue.DialogueNode;
import io.cannonforge.retroquest.dialogue.DialogueTree;
import io.cannonforge.retroquest.model.InventorySlot;
import io.cannonforge.retroquest.model.Item;
import io.cannonforge.retroquest.model.NPC;
import io.cannonforge.retroquest.model.Player;
import io.cannonforge.retroquest.model.Quest;
import io.cannonforge.retroquest.registry.ImageAssetRegistry;
import io.cannonforge.retroquest.registry.ItemRegistry;
import io.cannonforge.retroquest.registry.QuestRegistry;

/**
 * Full-screen dialogue overlay with typewriter text, NPC portrait, and branching choices.
 * Follows the established overlay pattern (paint/handleKey/handleClick called from GamePanel).
 */
public class DialogueOverlay {

    private static final Color SEL_BG   = new Color(0, 40, 55);
    private static final Font  F_TEXT   = F_ITEM;  // alias
    private static final Font  F_CHOICE = F_ITEM;  // alias

    // ── Typewriter settings ────────────────────────────────────────────────────
    private static final int CHARS_PER_SEC = 40;
    private static final long CHAR_DELAY_MS = 1000 / CHARS_PER_SEC;
    private static final long ENTRY_MS = 280;
    private static final long CURSOR_BLINK_MS = 500;

    // ── State ──────────────────────────────────────────────────────────────────
    private final Retroquest game;
    private boolean active = false;
    private long entryTime;

    private NPC currentNpc;
    private DialogueTree tree;
    private DialogueNode currentNode;
    private final List<DialogueChoice> visibleChoices = new ArrayList<>();
    private int selectedChoice = 0;

    // Typewriter
    private String fullText = "";
    private int revealedChars = 0;
    private long lastCharTime = 0;
    private boolean textFullyRevealed = false;

    // NPC portrait
    private Image npcPortrait;

    // Mouse hit-testing
    private Rectangle[] choiceRects = new Rectangle[0];

    // Track which actions have been executed for the current node
    private boolean actionsExecuted = false;

    public DialogueOverlay(Retroquest game) {
        this.game = game;
    }

    public boolean isActive() { return active; }

    // ── Open / Close ───────────────────────────────────────────────────────────

    public void open(NPC npc) {
        this.currentNpc = npc;
        this.tree = npc.getDialogueTree();
        if (tree == null || tree.getStartNode() == null) {
            active = false;
            return;
        }
        entryTime = System.currentTimeMillis();
        active = true;
        enterNode(tree.getStartNode());
    }

    public void close() {
        active = false;
        currentNpc = null;
        tree = null;
        currentNode = null;
        visibleChoices.clear();
        choiceRects = new Rectangle[0];
    }

    private void enterNode(DialogueNode node) {
        if (node == null) {
            close();
            return;
        }
        currentNode = node;
        fullText = node.getText() != null ? node.getText() : "";
        revealedChars = 0;
        lastCharTime = System.currentTimeMillis();
        textFullyRevealed = false;
        selectedChoice = 0;
        actionsExecuted = false;

        // Load NPC portrait
        if (currentNpc.getSpriteName() != null) {
            String raw = currentNpc.getSpriteName();
            String key = raw.contains("/") ? raw : "npcs/" + raw.replace(".png", "");
            npcPortrait = ImageAssetRegistry.get(key);
        }
        if (npcPortrait == null) {
            npcPortrait = ImageAssetRegistry.get("npcs/townsman");
        }

        // Build visible choices
        rebuildVisibleChoices();

        // Auto-route: "..." is a sentinel for routing-only nodes with no displayed text.
        // If the node text is "..." and conditions have filtered to exactly one choice,
        // execute any actions on this node first, then jump directly to the next node.
        if ("...".equals(fullText) && visibleChoices.size() == 1) {
            executeNodeActions(node);
            if (!active) return;  // action closed the dialogue (e.g. OPEN_SHOP, CLOSE_DIALOGUE)
            String nextId = visibleChoices.get(0).getNextNodeId();
            if (nextId != null && tree.getNode(nextId) != null) {
                enterNode(tree.getNode(nextId));
                return;
            }
        }

    }

    private void rebuildVisibleChoices() {
        visibleChoices.clear();
        if (currentNode.getChoices() != null) {
            for (DialogueChoice c : currentNode.getChoices()) {
                if (c.isAvailable(game.getPlayer())) {
                    visibleChoices.add(c);
                }
            }
        }
        // If all choices filtered out, add a farewell option
        if (visibleChoices.isEmpty()) {
            DialogueChoice farewell = new DialogueChoice("Farewell.", null, null);
            visibleChoices.add(farewell);
        }
        if (selectedChoice >= visibleChoices.size()) {
            selectedChoice = 0;
        }
        choiceRects = new Rectangle[visibleChoices.size()];
    }

    private void executeNodeActions(DialogueNode node) {
        if (actionsExecuted) return;
        actionsExecuted = true;
        if (node.getActions() == null) return;
        for (DialogueAction action : node.getActions()) {
            executeAction(action);
            // Stop processing further actions if the dialogue was closed
            // (OPEN_SHOP, OPEN_CASINO, OPEN_ARENA, OPEN_DEPTHS, OPEN_SKY_GAMES, and CLOSE_DIALOGUE all call close())
            if (!active) return;
        }
    }

    private void executeAction(DialogueAction action) {
        if (action == null || action.getType() == null) return;
        Player p = game.getPlayer();

        switch (action.getType()) {
            case GIVE_QUEST -> {
                Quest template = QuestRegistry.getById(action.getTarget());
                if (template != null) {
                    boolean alreadyActive = p.getActiveQuests().stream()
                            .anyMatch(q -> q.getId().equals(action.getTarget()));
                    boolean alreadyDone = p.getCompletedQuests().stream()
                            .anyMatch(q -> q.getId().equals(action.getTarget()));
                    // Check prerequisite quest
                    String prereq = template.getPrereqQuestId();
                    boolean prereqMet = prereq == null || prereq.isEmpty() ||
                            p.getCompletedQuests().stream().anyMatch(q -> q.getId().equals(prereq));
                    if (!alreadyActive && (!alreadyDone || template.isRepeatable()) && prereqMet) {
                        if (alreadyDone) {
                            // Clear completed entry so QUEST_COMPLETE conditions reset
                            p.removeCompletedQuest(action.getTarget());
                        }
                        Quest instance = template.createInstance();
                        if (currentNpc != null) instance.setGiverName(currentNpc.getName());
                        p.addQuest(instance);
                        game.log("Quest: " + instance.getTitle() + " -- " + instance.getDescription(),
                                MessageLog.Type.INFO);
                    }
                }
            }
            case COMPLETE_QUEST -> {
                String questId = action.getTarget();
                Quest activeQuest = p.getActiveQuests().stream()
                        .filter(q -> q.getId().equals(questId))
                        .findFirst().orElse(null);
                if (activeQuest != null) {
                    // Force-progress EXPLORE quests completed via dialogue interaction
                    if (!activeQuest.isComplete() && activeQuest.getType() == Quest.Type.EXPLORE) {
                        activeQuest.progress(activeQuest.getRequiredAmount() - activeQuest.getProgress());
                    }
                    // Re-count inventory for COLLECT quests (handles items found before quest was accepted)
                    if (!activeQuest.isComplete() && activeQuest.getType() == Quest.Type.COLLECT) {
                        int count = 0;
                        for (InventorySlot slot : p.getInventorySlots()) {
                            if (!slot.isEmpty() && slot.getItem().getName().equalsIgnoreCase(activeQuest.getTarget())) {
                                count += slot.getQuantity();
                            }
                        }
                        if (count > activeQuest.getProgress()) {
                            activeQuest.progress(count - activeQuest.getProgress());
                        }
                    }
                    if (activeQuest.isComplete()) {
                        // For DELIVER quests, remove the delivery items before granting rewards
                        if (activeQuest.getType() == Quest.Type.DELIVER && activeQuest.getDeliverItemId() != null) {
                            String deliverItemId = activeQuest.getDeliverItemId();
                            int toRemove = activeQuest.getRequiredAmount();
                            int removed = 0;
                            for (int i = p.getInventorySlots().length - 1; i >= 0 && removed < toRemove; i--) {
                                InventorySlot slot = p.getInventorySlots()[i];
                                if (!slot.isEmpty() && slot.getItem().getId().equals(deliverItemId)) {
                                    int take = Math.min(toRemove - removed, slot.getQuantity());
                                    slot.setQuantity(slot.getQuantity() - take);
                                    removed += take;
                                    if (slot.getQuantity() <= 0) p.removeItem(i);
                                }
                            }
                        }
                        p.completeQuest(activeQuest);
                    } else {
                        game.log("Not yet \u2014 " + activeQuest.getProgressText() + ".", MessageLog.Type.INFO);
                    }
                } else {
                    boolean alreadyDone = p.getCompletedQuests().stream()
                            .anyMatch(q -> q.getId().equals(questId));
                    if (!alreadyDone) {
                        game.log("Quest not active: " + questId, MessageLog.Type.DIM);
                    }
                }
            }
            case GIVE_ITEM -> {
                Item item = ItemRegistry.getById(action.getTarget());
                if (item != null) {
                    int qty = Math.max(1, action.getAmount());
                    if (item.getType() == Item.Type.KEY) {
                        p.addKey(item.getId());
                    } else {
                        for (int i = 0; i < qty; i++) {
                            p.addItemAndProgress(item);
                        }
                    }
                    game.log("Received: " + item.getName() + (qty > 1 ? " x" + qty : ""),
                            MessageLog.Type.LOOT);
                }
            }
            case GIVE_GOLD -> {
                p.addGold(action.getAmount());
                if (action.getAmount() > 0) {
                    game.log("Received " + action.getAmount() + " gold.", MessageLog.Type.LOOT);
                } else if (action.getAmount() < 0) {
                    game.log("Paid " + Math.abs(action.getAmount()) + " gold.", MessageLog.Type.INFO);
                }
            }
            case TAKE_ITEM -> {
                if (action.getTarget() == null) break;
                int toRemove = Math.max(1, action.getAmount());
                int removed = 0;
                for (int i = p.getInventorySlots().length - 1; i >= 0 && removed < toRemove; i--) {
                    InventorySlot slot = p.getInventorySlots()[i];
                    if (!slot.isEmpty() && slot.getItem().getId().equals(action.getTarget())) {
                        int take = Math.min(toRemove - removed, slot.getQuantity());
                        slot.setQuantity(slot.getQuantity() - take);
                        removed += take;
                        if (slot.getQuantity() <= 0) p.removeItem(i);
                    }
                }
            }
            case SET_FLAG -> {
                String val = (action.getValue() != null) ? action.getValue() : "true";
                p.setFlag(action.getTarget(), val);
            }
            case CLEAR_FLAG -> {
                p.clearFlag(action.getTarget());
            }
            case OPEN_SHOP -> {
                NPC shopNpc = currentNpc;
                close();
                if (shopNpc != null) {
                    game.getGamePanel().openShop(shopNpc.getShopItemIds());
                    game.startOverlayRepaintTimer();
                }
            }
            case OPEN_CASINO -> {
                close();
                game.getGamePanel().openCasino();
                game.startOverlayRepaintTimer();
            }
            case OPEN_ARENA -> {
                close();
                game.getGamePanel().openArena();
                game.startOverlayRepaintTimer();
            }
            case OPEN_DEPTHS -> {
                close();
                game.getGamePanel().openDepths();
                game.startOverlayRepaintTimer();
            }
            case OPEN_SKY_GAMES -> {
                close();
                game.getGamePanel().openSkyGames();
                game.startOverlayRepaintTimer();
            }
            case OPEN_NATURE_GAMES -> {
                close();
                game.getGamePanel().openNatureGames();
                game.startOverlayRepaintTimer();
            }
            case OPEN_MEMORY_GAMES -> {
                close();
                game.getGamePanel().openMemoryGames();
                game.startOverlayRepaintTimer();
            }
            case OPEN_WAR_GAMES -> {
                close();
                game.getGamePanel().openWarGames();
                game.startOverlayRepaintTimer();
            }
            case HEAL_PLAYER -> {
                if (action.getAmount() <= 0) {
                    int healed = p.getMaxHp() - p.getHp();
                    p.heal(p.getMaxHp());
                    p.refreshSpellSlots();
                    p.addFood(300);
                    p.curePoison();
                    p.resetGamblingWinnings();
                    if (healed > 0) game.log("You feel fully restored! +" + healed + " HP, +300 food.", MessageLog.Type.GOOD);
                    else game.log("You feel well-rested. +300 food.", MessageLog.Type.GOOD);
                } else {
                    p.heal(action.getAmount());
                    game.log("Healed " + action.getAmount() + " HP.", MessageLog.Type.GOOD);
                }
            }
            case ADD_FAVOR -> {
                if (action.getTarget() == null || action.getAmount() == 0) break;
                try {
                    io.cannonforge.retroquest.model.God god =
                            io.cannonforge.retroquest.model.God.valueOf(action.getTarget().trim().toUpperCase());
                    p.addFavor(god, action.getAmount());
                    game.log((action.getAmount() > 0 ? "+" : "") + action.getAmount()
                            + " " + god.displayName + " favor.",
                            action.getAmount() > 0 ? MessageLog.Type.GOOD : MessageLog.Type.DANGER);
                    if (game.getStatsPanel() != null) game.getStatsPanel().refresh();
                } catch (IllegalArgumentException ignored) { }
            }
            case TEACH_SPELL -> {
                if (p.learnSpell(action.getTarget())) {
                    game.log("You learned the spell: " + action.getTarget() + "!", MessageLog.Type.GOOD);
                } else {
                    game.log("You already know " + action.getTarget() + ".", MessageLog.Type.DIM);
                }
            }
            case LOG_MESSAGE -> {
                MessageLog.Type logType = MessageLog.Type.INFO;
                if (action.getValue() != null) {
                    try {
                        logType = MessageLog.Type.valueOf(action.getValue());
                    } catch (IllegalArgumentException ignored) {}
                }
                game.log(action.getTarget(), logType);
            }
            case CLOSE_DIALOGUE -> close();
            case SERVE_DRINK -> {
                int cost = Math.max(1, action.getAmount());
                if (p.getGold() >= cost) {
                    p.addGold(-cost);
                    p.addDrunkSteps(20);
                    game.log("You down a frothy ale. (-" + cost + " gold)", MessageLog.Type.INFO);
                    if (p.isDrunk()) {
                        game.log("The room starts to spin...", MessageLog.Type.DANGER);
                    } else {
                        game.log("A pleasant warmth spreads through you.", MessageLog.Type.GOOD);
                    }
                } else {
                    game.log("You can't afford a drink.", MessageLog.Type.DIM);
                }
            }
        }
        if (game.getStatsPanel() != null) game.getStatsPanel().refresh();
    }

    // ── Input ──────────────────────────────────────────────────────────────────

    /** The line the NPC is currently saying, or null when the overlay is closed. */
    public String getCurrentText() { return active ? fullText : null; }

    /** Labels of the replies on offer right now, in the order their number keys select them. */
    public java.util.List<String> getChoiceLabels() {
        java.util.List<String> out = new java.util.ArrayList<>();
        if (active) for (DialogueChoice c : visibleChoices) out.add(c.getLabel());
        return out;
    }

    /** Which reply the arrow keys have highlighted. */
    public int getSelectedChoice() { return selectedChoice; }

    public void handleKey(KeyEvent e) {
        if (!active) return;

        // If text is still revealing, any key skips to full reveal
        if (!textFullyRevealed) {
            textFullyRevealed = true;
            revealedChars = fullText.length();
            executeNodeActions(currentNode);
            return;
        }

        switch (e.getKeyCode()) {
            case KeyEvent.VK_ESCAPE -> close();
            case KeyEvent.VK_UP, KeyEvent.VK_K -> {
                if (selectedChoice > 0) selectedChoice--;
            }
            case KeyEvent.VK_DOWN, KeyEvent.VK_J -> {
                if (selectedChoice < visibleChoices.size() - 1) selectedChoice++;
            }
            case KeyEvent.VK_ENTER -> selectChoice(selectedChoice);
            default -> {
                // Number keys 1-9
                int keyNum = e.getKeyCode() - KeyEvent.VK_1;
                if (keyNum >= 0 && keyNum < visibleChoices.size()) {
                    selectChoice(keyNum);
                }
            }
        }
    }

    public void handleClick(int mx, int my) {
        if (!active) return;

        // If text is still revealing, click to skip
        if (!textFullyRevealed) {
            textFullyRevealed = true;
            revealedChars = fullText.length();
            executeNodeActions(currentNode);
            return;
        }

        for (int i = 0; i < choiceRects.length; i++) {
            if (choiceRects[i] != null && choiceRects[i].contains(mx, my)) {
                selectChoice(i);
                return;
            }
        }
    }

    private void selectChoice(int index) {
        if (index < 0 || index >= visibleChoices.size()) return;
        DialogueChoice choice = visibleChoices.get(index);
        String nextId = choice.getNextNodeId();
        if (nextId == null || tree.getNode(nextId) == null) {
            close();
        } else {
            enterNode(tree.getNode(nextId));
        }
    }

    // ── Paint ──────────────────────────────────────────────────────────────────

    public void paint(Graphics2D g, int W, int H) {
        if (!active) return;
        try { doPaint(g, W, H); } catch (Exception ex) { ex.printStackTrace(); active = false; }
    }

    private void doPaint(Graphics2D g, int W, int H) {
        // Update typewriter
        long now = System.currentTimeMillis();
        if (!textFullyRevealed && revealedChars < fullText.length()) {
            int prevChars = revealedChars;
            while (now - lastCharTime >= CHAR_DELAY_MS && revealedChars < fullText.length()) {
                revealedChars++;
                lastCharTime += CHAR_DELAY_MS;
            }
            if (revealedChars > prevChars) {
                char revealed = fullText.charAt(revealedChars - 1);
                if (revealed != ' ' && revealed != '\n') {
                    SoundManager.getInstance().play("textblip");
                }
            }
            if (revealedChars >= fullText.length()) {
                textFullyRevealed = true;
                executeNodeActions(currentNode);
            }
        }

        // Slide-in animation
        float slide = entryEase(entryTime, ENTRY_MS);

        // Dim background
        g.setColor(new Color(0, 0, 0, (int)(180 * slide)));
        g.fillRect(0, 0, W, H);

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Card dimensions
        int cW = Math.min(680, W - 40);
        int cH = Math.min(420, H - 40);
        int cX = (W - cW) / 2;
        int cY = (int)((H - cH) / 2 + (1f - slide) * -60);

        int titleH  = 36;
        int keybarH = 34;
        int bodyH   = cH - titleH - keybarH;

        // Shadow
        g.setColor(new Color(0, 0, 0, (int)(120 * slide)));
        g.fillRoundRect(cX + 4, cY + 4, cW, cH, 6, 6);

        // Card background
        g.setPaint(new GradientPaint(cX, cY, new Color(8, 14, 20), cX, cY + cH, BG));
        g.fillRoundRect(cX, cY, cW, cH, 6, 6);

        // Border
        g.setStroke(new BasicStroke(1.5f));
        g.setColor(new Color(AMBER.getRed(), AMBER.getGreen(), AMBER.getBlue(), 160));
        g.drawRoundRect(cX, cY, cW, cH, 6, 6);
        g.setStroke(new BasicStroke(1f));

        // Title bar
        paintTitle(g, cX, cY, cW, titleH);

        // Body: portrait + text + choices
        int bodyY = cY + titleH;
        paintBody(g, cX, bodyY, cW, bodyH);

        // Key bar
        paintKeyBar(g, cX, bodyY + bodyH, cW, keybarH);
    }

    private void paintTitle(Graphics2D g, int x, int y, int w, int h) {
        g.setPaint(new GradientPaint(x, y, new Color(20, 16, 4), x + w, y, new Color(8, 12, 16)));
        g.fillRoundRect(x, y, w, h, 6, 6);
        g.fillRect(x, y + h / 2, w, h / 2);
        g.setColor(BORDER_COL);
        g.drawLine(x, y + h, x + w, y + h);

        g.setFont(F_TITLE);
        FontMetrics fm = g.getFontMetrics();
        int ty = y + (h + fm.getAscent() - fm.getDescent()) / 2;

        // Shadow
        g.setColor(new Color(AMBER.getRed(), AMBER.getGreen(), AMBER.getBlue(), 35));
        g.drawString("\u25c8  DIALOGUE", x + 15, ty + 1);
        g.setColor(AMBER);
        g.drawString("\u25c8  DIALOGUE", x + 14, ty);

        // NPC name on right
        if (currentNpc != null) {
            String name = currentNpc.getName();
            g.setColor(TEXT_BRIGHT);
            g.drawString(name, x + w - fm.stringWidth(name) - 14, ty);
        }
    }

    private void paintBody(Graphics2D g, int x, int y, int w, int h) {
        g.setColor(PANEL_BG);
        g.fillRect(x, y, w, h);

        int pad = 12;
        int portraitSize = 64;
        int portraitPad = 10;
        int textX = x + pad + portraitSize + portraitPad;
        int textW = w - pad * 2 - portraitSize - portraitPad;

        // NPC portrait
        int portraitX = x + pad;
        int portraitY = y + pad;
        g.setColor(new Color(20, 25, 35));
        g.fillRect(portraitX, portraitY, portraitSize, portraitSize);
        g.setColor(BORDER_COL);
        g.drawRect(portraitX, portraitY, portraitSize, portraitSize);
        if (npcPortrait != null) {
            g.drawImage(npcPortrait, portraitX + 1, portraitY + 1,
                    portraitSize - 2, portraitSize - 2, null);
        }

        // NPC text with typewriter effect
        g.setFont(F_TEXT);
        FontMetrics fm = g.getFontMetrics();
        String displayText = fullText.substring(0, revealedChars);
        List<String> lines = OverlayTheme.wordWrap(fm, displayText, textW);

        int textY = y + pad + fm.getAscent();
        g.setColor(TEXT_BRIGHT);
        int maxTextLines = Math.max(1, (h / 2 - pad) / (fm.getHeight() + 2));
        for (int i = 0; i < Math.min(lines.size(), maxTextLines); i++) {
            g.drawString(lines.get(i), textX, textY);
            textY += fm.getHeight() + 2;
        }

        // Blinking cursor at end of text
        if (!textFullyRevealed) {
            boolean cursorVisible = ((System.currentTimeMillis() / CURSOR_BLINK_MS) % 2) == 0;
            if (cursorVisible) {
                String lastLine = lines.isEmpty() ? "" : lines.get(lines.size() - 1);
                int cursorX = textX + fm.stringWidth(lastLine);
                int cursorY = textY - fm.getHeight() - 2;
                g.setColor(PHOSPHOR);
                g.fillRect(cursorX + 2, cursorY, 8, fm.getHeight());
            }
        }

        // Divider between text and choices
        int dividerY = y + h / 2 + 4;
        g.setColor(BORDER_COL);
        g.drawLine(x + pad, dividerY, x + w - pad, dividerY);

        // Choices (only shown when text fully revealed)
        if (textFullyRevealed && !visibleChoices.isEmpty()) {
            g.setFont(F_CHOICE);
            FontMetrics fmC = g.getFontMetrics();
            int choiceY = dividerY + 10;
            int rowH = fmC.getHeight() + 6;
            choiceRects = new Rectangle[visibleChoices.size()];

            for (int i = 0; i < visibleChoices.size(); i++) {
                boolean sel = (i == selectedChoice);
                DialogueChoice choice = visibleChoices.get(i);
                int rowX = x + pad;
                int rowW = w - pad * 2;

                // Row background
                if (sel) {
                    g.setColor(SEL_BG);
                    g.fillRect(rowX, choiceY, rowW, rowH);
                    g.setColor(AMBER);
                    g.fillRect(rowX, choiceY, 3, rowH);
                }

                // Cache rect for mouse hit-testing
                choiceRects[i] = new Rectangle(rowX, choiceY, rowW, rowH);

                int textBaseY = choiceY + rowH - (rowH - fmC.getAscent()) / 2 - 1;

                // Arrow indicator for selected
                g.setColor(sel ? AMBER : TEXT_DIM);
                String prefix = sel ? "\u25b8 " : "  ";
                g.drawString(prefix + (i + 1) + ". " + choice.getLabel(), rowX + 6, textBaseY);

                choiceY += rowH;
            }
        } else if (!textFullyRevealed) {
            // Show "any key to continue" hint
            g.setFont(F_KEY);
            g.setColor(TEXT_DIM);
            g.drawString("[Any key to skip]", x + pad, dividerY + 20);
        }
    }

    private void paintKeyBar(Graphics2D g, int x, int y, int w, int h) {
        g.setPaint(new GradientPaint(x, y, new Color(14, 12, 6), x, y + h, BG));
        g.fillRect(x, y, w, h);
        g.fillRoundRect(x, y + h / 2, w, h / 2 + 4, 6, 6);
        g.setColor(BORDER_COL);
        g.drawLine(x, y, x + w, y);

        String[][] keys = {
            {"\u2191\u2193", "Select"},
            {"Enter", "Choose"},
            {"1-9", "Quick"},
            {"Esc", "Leave"}
        };
        OverlayTheme.paintKeyBadges(g, x, y, w, h, keys, AMBER);
    }
}

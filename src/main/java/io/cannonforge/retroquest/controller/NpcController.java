package io.cannonforge.retroquest.controller;
import java.util.ArrayList;

import io.cannonforge.retroquest.core.MessageLog;
import io.cannonforge.retroquest.core.Retroquest;
import io.cannonforge.retroquest.core.SoundManager;
import io.cannonforge.retroquest.core.Town;
import io.cannonforge.retroquest.model.InventorySlot;
import io.cannonforge.retroquest.model.Item;
import io.cannonforge.retroquest.model.NPC;
import io.cannonforge.retroquest.model.Player;
import io.cannonforge.retroquest.model.Quest;
import io.cannonforge.retroquest.model.Spell;
import io.cannonforge.retroquest.model.TownEntrance;
import io.cannonforge.retroquest.registry.ItemRegistry;
import io.cannonforge.retroquest.registry.QuestRegistry;

/**
 * Handles all NPC-related gameplay: conversation dispatch, quest offering,
 * deliver/collect/kill quest turn-in, inn resting, prerequisite checking,
 * and map-mode spell casting.
 *
 * <p>All state reads and writes go through the {@link Retroquest} instance
 * passed at construction; this class holds no state of its own.
 */
public class NpcController {

    private static final int INN_REST_COST = 2;

    private final Retroquest game;

    public NpcController(Retroquest game) {
        this.game = game;
    }

    // ── Conversation Entry Point ──────────────────────────────────────────────

    /**
     * Initiates a conversation with the nearest NPC.
     * Handles post-quest dialog, shop/inn dispatch, quest turn-in, and quest giving.
     */
    public void talk() {
        NPC nearby = findNearbyNPC();
        if (nearby == null) {
            game.log("No one nearby to talk to.", MessageLog.Type.DIM);
            return;
        }

        // If the NPC has a dialogue tree, use it instead of legacy logic
        if (nearby.getDialogueTree() != null && nearby.getDialogueTree().getStartNode() != null) {
            game.getGamePanel().openDialogue(nearby);
            game.startOverlayRepaintTimer();
            game.getPlayer().progressQuest(Quest.Type.TALK, nearby.getName(), 1);
            return;
        }

        // Post-quest completion dialog overrides everything else
        if (nearby.getQuestId() != null && !nearby.getQuestId().isEmpty()) {
            boolean questCompleted = game.getPlayer().getCompletedQuests().stream()
                    .anyMatch(q -> nearby.getQuestId().equals(q.getId()));
            if (questCompleted) {
                String customText = nearby.getQuestCompleteDialog();
                if (customText != null && !customText.trim().isEmpty()) {
                    game.log(nearby.getName() + ": " + customText, MessageLog.Type.INFO);
                    return;
                }
            }
        }

        String dialogText = nearby.getDefaultDialog();
        if (dialogText == null || dialogText.trim().isEmpty()) {
            dialogText = "Hello traveler...";
        }

        switch (nearby.getType()) {
            case SHOPKEEPER -> { game.getGamePanel().openShop(nearby.getShopItemIds()); game.startOverlayRepaintTimer(); return; }
            case CASINO     -> { game.getGamePanel().openCasino(); game.startOverlayRepaintTimer(); return; }
            case INNKEEPER  -> { visitInn(nearby); return; }
            case HEALER     -> { visitHealer(nearby); return; }
            default -> game.log(nearby.getName() + ": " + dialogText, MessageLog.Type.INFO);
        }

        // Only offer new quests when we did NOT just turn one in
        boolean turnedInThisTalk = checkForDeliverTurnIn(nearby) || checkForCollectTurnIn(nearby) || checkForKillQuestTurnIn(nearby);
        if (!turnedInThisTalk) {
            giveQuests(nearby);
        }

        game.getPlayer().progressQuest(Quest.Type.TALK, nearby.getName(), 1);
    }

    // ── NPC Lookup ────────────────────────────────────────────────────────────

    /**
     * Returns the first NPC within 1 tile of the player, or {@code null}.
     *
     * <p>Only the NPCs on the map the player is actually standing on are considered — an
     * overworld NPC must not be talkable from the same coordinates inside a town or dungeon.
     */
    public NPC findNearbyNPC() {
        int px = game.getPlayer().getX();
        int py = game.getPlayer().getY();

        if (game.isInDungeon()) {
            // Authored dungeon NPCs
            if (game.getCurrentDungeonMapData() != null && game.getCurrentDungeonMapData().npcs != null) {
                for (NPC npc : game.getCurrentDungeonMapData().npcs) {
                    if (Math.abs(px - npc.getX()) <= 1 && Math.abs(py - npc.getY()) <= 1) {
                        return npc;
                    }
                }
            }
        } else if (game.getCurrentTown() != null) {
            for (NPC npc : game.getCurrentTown().getNpcs()) {
                if (Math.abs(px - npc.getX()) <= 1 && Math.abs(py - npc.getY()) <= 1) {
                    return npc;
                }
            }
        } else {
            for (NPC npc : game.getOverworldManager().getNpcs()) {
                if (Math.abs(px - npc.getX()) <= 1 && Math.abs(py - npc.getY()) <= 1) {
                    return npc;
                }
            }
        }
        return null;
    }

    // ── Quest Logic ───────────────────────────────────────────────────────────

    /**
     * Offers the NPC's associated quest to the player if they don't already have
     * it active or completed (for non-repeatable quests).
     */
    private void giveQuests(NPC npc) {
        Player p       = game.getPlayer();
        String questId = npc.getQuestId();
        if (questId == null || questId.trim().isEmpty()) return;

        Quest template = QuestRegistry.getById(questId);
        if (template == null) {
            game.log("[Quest] WARNING: Quest ID '" + questId + "' not found!", MessageLog.Type.DIM);
            return;
        }

        boolean alreadyActive = p.getActiveQuests().stream().anyMatch(q -> q.getId().equals(questId));
        if (alreadyActive) return;

        if (!template.isRepeatable()) {
            boolean alreadyCompleted = p.getCompletedQuests().stream().anyMatch(q -> q.getId().equals(questId));
            if (alreadyCompleted) return;
        }

        String prereq = template.getPrereqQuestId();
        if (prereq != null && !prereq.isEmpty()) {
            boolean prereqDone = p.getCompletedQuests().stream().anyMatch(q -> q.getId().equals(prereq));
            if (!prereqDone) return;
        }

        if (template.isRepeatable()) {
            // Clear completed entry so QUEST_COMPLETE conditions reset
            p.removeCompletedQuest(questId);
        }
        Quest instance = template.createInstance();
        instance.setGiverName(npc.getName());
        p.addQuest(instance);

        game.log("Quest: " + instance.getTitle() + " — " + instance.getDescription(), MessageLog.Type.INFO);
    }

    /**
     * Checks for a completed COLLECT quest and prompts the player to turn it in.
     *
     * @return {@code true} if a turn-in was initiated during this conversation
     */
    private boolean checkForCollectTurnIn(NPC npc) {
        Player p = game.getPlayer();

        for (Quest q : new ArrayList<>(p.getActiveQuests())) {
            if (q.getType() != Quest.Type.COLLECT || !q.isComplete() || !npc.getName().equals(q.getGiverName())) continue;

            game.getMessageLog().prompt(
                npc.getName() + ": Take " + q.getRequiredAmount() + " " + q.getTarget() + " now?",
                () -> {
                    removeItemsFromInventory(p, q.getTarget(), q.getRequiredAmount(), false);
                    p.completeQuest(q);
                    // Favor is now handled in Player.completeQuest() for all islands
                    logQuestCompleteDialog(npc, q);
                }, null);
            return true; // prompt issued for this turn-in
        }
        return false;
    }

    /**
     * Checks for a completed KILL, EXPLORE, or TALK quest given by this NPC and
     * prompts the player to turn it in.
     *
     * @return {@code true} if a turn-in prompt was initiated during this conversation
     */
    private boolean checkForKillQuestTurnIn(NPC npc) {
        Player p = game.getPlayer();

        for (Quest q : new ArrayList<>(p.getActiveQuests())) {
            if (q.getType() == Quest.Type.COLLECT || q.getType() == Quest.Type.DELIVER || !q.isComplete() || !npc.getName().equals(q.getGiverName())) continue;

            game.getMessageLog().prompt(
                npc.getName() + ": Quest complete — " + q.getTitle() + ". Claim your reward?",
                () -> {
                    p.completeQuest(q);
                    logQuestCompleteDialog(npc, q);
                }, null);
            return true; // prompt issued for this turn-in
        }
        return false;
    }

    // ── Inn & Special NPC Interactions ────────────────────────────────────────

    /**
     * Checks for a completed DELIVER quest targeting this NPC and prompts
     * the player to hand over the required items.
     *
     * @return {@code true} if a turn-in was initiated during this conversation
     */
    private boolean checkForDeliverTurnIn(NPC npc) {
        Player p = game.getPlayer();

        for (Quest q : new ArrayList<>(p.getActiveQuests())) {
            if ((q.getType() != Quest.Type.DELIVER) || !q.getTarget().equalsIgnoreCase(npc.getName())) continue;

            // Look up the item name from the deliver item ID
            String deliverItemId = q.getDeliverItemId();
            if (deliverItemId == null || deliverItemId.isEmpty()) continue;
            Item deliverItem = ItemRegistry.getById(deliverItemId);
            if (deliverItem == null) continue;
            String itemName = deliverItem.getName();

            // Count how many the player has
            int count = 0;
            for (InventorySlot slot : p.getInventorySlots()) {
                if (!slot.isEmpty() && slot.getItem().getId().equals(deliverItemId)) {
                    count += slot.getQuantity();
                }
            }

            if (count >= q.getRequiredAmount()) {
                game.getMessageLog().prompt(
                    npc.getName() + ": Hand over " + q.getRequiredAmount() + " " + itemName + "?",
                    () -> {
                        removeItemsFromInventory(p, deliverItemId, q.getRequiredAmount(), true);
                        p.completeQuest(q);
                        logQuestCompleteDialog(npc, q);
                    }, null);
                return true; // prompt issued for this turn-in
            } else {
                game.log(npc.getName() + ": I'm still waiting for " + q.getRequiredAmount()
                        + " " + itemName + " (" + count + "/" + q.getRequiredAmount() + ").",
                        MessageLog.Type.INFO);
                return true; // handled this quest interaction
            }
        }
        return false;
    }

    /** Removes items from the player's inventory, matching by name (case-insensitive) or by ID. */
    private void removeItemsFromInventory(Player p, String match, int amount, boolean matchById) {
        int removed = 0;
        for (int i = p.getInventorySlots().length - 1; i >= 0 && removed < amount; i--) {
            InventorySlot slot = p.getInventorySlots()[i];
            if (slot.isEmpty()) continue;
            boolean matches = matchById
                    ? slot.getItem().getId().equals(match)
                    : slot.getItem().getName().equalsIgnoreCase(match);
            if (matches) {
                int take = Math.min(amount - removed, slot.getQuantity());
                slot.setQuantity(slot.getQuantity() - take);
                removed += take;
                if (slot.getQuantity() <= 0) p.removeItem(i);
            }
        }
    }

    /** Logs the NPC's quest-complete dialog if available. */
    private void logQuestCompleteDialog(NPC npc, Quest q) {
        if (q.getId().equals(npc.getQuestId())) {
            String cd = npc.getQuestCompleteDialog();
            if (cd != null && !cd.isEmpty()) {
                game.log(npc.getName() + ": " + cd, MessageLog.Type.INFO);
            }
        }
    }

    /** Prompts the player to rest at the inn for a fixed gold cost (full HP + food). */
    private void visitInn(NPC innkeeper) {
        int cost = INN_REST_COST;
        if (game.getPlayer().getGold() < cost) {
            game.log("You can't afford a room right now.", MessageLog.Type.DANGER);
            return;
        }
        game.getMessageLog().prompt(innkeeper.getDefaultDialog() + " — Rest for " + cost + " gold? (Full heal + food)",
            () -> {
                game.getPlayer().addGold(-cost);
                int healed = game.getPlayer().getMaxHp() - game.getPlayer().getHp();
                game.getPlayer().heal(game.getPlayer().getMaxHp());
                game.getPlayer().refreshSpellSlots();
                game.getPlayer().addFood(300);
                game.getPlayer().curePoison();
                game.getPlayer().resetGamblingWinnings();
                game.log("You slept like a log! +" + healed + " HP, +300 food.", MessageLog.Type.GOOD);
                game.getStatsPanel().refresh();
            }, null);
    }

    /** Prompts the player to pay for healing (HP only, cost scales with damage). */
    private void visitHealer(NPC healer) {
        int missing = game.getPlayer().getMaxHp() - game.getPlayer().getHp();
        if (missing <= 0) {
            game.log(healer.getName() + ": You are already in good health.", MessageLog.Type.INFO);
            return;
        }
        int cost = Math.max(1, missing / 5);
        if (game.getPlayer().getGold() < cost) {
            game.log("You can't afford healing right now. (" + cost + " gold needed)", MessageLog.Type.DANGER);
            return;
        }
        game.getMessageLog().prompt(healer.getDefaultDialog() + " — Heal for " + cost + " gold?",
            () -> {
                game.getPlayer().addGold(-cost);
                game.getPlayer().heal(game.getPlayer().getMaxHp());
                SoundManager.getInstance().play("heal");
                game.log(healer.getName() + " heals your wounds! +" + missing + " HP restored.", MessageLog.Type.GOOD);
                game.getStatsPanel().refresh();
            }, null);
    }

    // ── Map Spell Casting ─────────────────────────────────────────────────────

    /**
     * Resolves a spell cast from the overworld map (outside combat).
     * Combat-only spells are rejected; healing and utility spells are handled here.
     */
    public void castMapSpell(Spell spell) {
        if (spell.getUsage() == Spell.Usage.COMBAT) {
            game.log("That spell can only be used in combat!", MessageLog.Type.DANGER);
            return;
        }

        int power = spell.getPower(game.getPlayer());

        switch (spell.getName()) {
            case "Cure Light Wounds", "Cure Serious Wounds", "Cure Critical Wounds", "Heal" -> {
                int oldHp  = game.getPlayer().getHp();
                game.getPlayer().heal(power);
                int healed = game.getPlayer().getHp() - oldHp;
                game.log("You cast " + spell.getName() + "! +" + healed + " HP restored.", MessageLog.Type.GOOD);
            }
            case "Teleport" -> teleportToTown();
            case "Scry" -> {
                if (game.isInDungeon()) {
                    boolean[][] revealed = game.getRevealedForLevel(game.getCurrentDepth());
                    for (boolean[] row : revealed) java.util.Arrays.fill(row, true);
                    game.getGamePanel().repaint();
                    SoundManager.getInstance().play("spell");
                    game.log("Your mind's eye expands — the dungeon layout is revealed!", MessageLog.Type.GOOD);
                } else {
                    game.log("Scry has no effect on the overworld.", MessageLog.Type.DIM);
                }
            }
            case "Wish" -> {
                // Map Wish: always full heal
                game.getPlayer().heal(game.getPlayer().getMaxHp());
                game.log("Your wish is granted — you are fully healed!", MessageLog.Type.GOOD);
            }
            case "Restoration" -> {
                int oldHp  = game.getPlayer().getHp();
                game.getPlayer().heal(power);
                int healed = game.getPlayer().getHp() - oldHp;
                game.log("You cast Restoration! +" + healed + " HP restored. All ailments cleansed.", MessageLog.Type.GOOD);
            }
            case "Resurrection" -> {
                if (game.getPlayer().isResurrectionCharged()) {
                    game.log("A resurrection ward is already active.", MessageLog.Type.DIM);
                } else {
                    game.getPlayer().chargeResurrection();
                    SoundManager.getInstance().play("heal");
                    game.log("A golden ward shimmers around you. If slain in combat, you will rise again at half health.", MessageLog.Type.GOOD);
                }
            }
            default -> game.log(spell.getLongDescription(), MessageLog.Type.INFO);
        }

        game.getStatsPanel().refresh();
    }

    /**
     * Teleports the player to the last safe town (legacy default: the starting town).
     * Used by Teleport / Word of Recall map spells.
     */
    private void teleportToTown() {
        // Default has to name a town that exists: Stonehaven was removed as unreachable, and
        // a null lastSafeTown would have sent Recall to a map that is no longer there.
        String dest = (game.getLastSafeTownName() != null) ? game.getLastSafeTownName() : "Moonhaven";
        if (game.getCurrentTown() != null) {
            game.log("You are already in " + dest + "!", MessageLog.Type.DIM);
            return;
        }
        // Look up the town's overworld position from TownEntrance data
        int doorX = 0, doorY = 0;
        for (TownEntrance te : game.getOverworldManager().getTownEntrances()) {
            if (te.townName().equals(dest)) {
                doorX = te.worldX();
                doorY = te.worldY();
                break;
            }
        }
        SoundManager.getInstance().playEnterTown();
        // Leaving a dungeon this way has to clear ALL of its state: a stale authored dungeon
        // name would send the next descent's stairs into a different dungeon's 40x40 map,
        // and a stale depth would key its tile state and fog of war to the wrong level.
        game.setInDungeon(false);
        game.setCurrentDepth(0);
        game.getDungeonViewState().setAuthoredDungeonName(null);
        game.getDungeonViewState().setMode(
                io.cannonforge.retroquest.model.DungeonViewState.Mode.TOP_DOWN);
        game.setCurrentDungeonMapData(null);
        game.setCurrentTown(new Town(dest, doorX, doorY));
        game.setCurrentMap(game.getCurrentTown().getInteriorMap());
        game.getPlayer().setPosition(game.getCurrentTown().getInteriorEntryX(), game.getCurrentTown().getInteriorEntryY());
        game.log("A flash of light! You teleport back to " + dest + ".", MessageLog.Type.GOOD);
        game.updateCamera();
        if (game.getGamePanel()  != null) game.getGamePanel().repaint();
        if (game.getStatsPanel() != null) game.getStatsPanel().refresh();
    }
}

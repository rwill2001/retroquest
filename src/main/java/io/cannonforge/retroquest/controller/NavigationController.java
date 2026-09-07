package io.cannonforge.retroquest.controller;
import java.util.Map;

import javax.swing.Timer;

import io.cannonforge.retroquest.animation.TownEntryAnimation;
import io.cannonforge.retroquest.animation.TownExitAnimation;
import io.cannonforge.retroquest.core.MessageLog;
import io.cannonforge.retroquest.core.Retroquest;
import io.cannonforge.retroquest.core.SoundManager;
import io.cannonforge.retroquest.core.Town;
import io.cannonforge.retroquest.model.God;
import io.cannonforge.retroquest.model.InventorySlot;
import io.cannonforge.retroquest.model.Item;
import io.cannonforge.retroquest.model.MapData;
import io.cannonforge.retroquest.model.Monster;
import io.cannonforge.retroquest.model.Quest;
import io.cannonforge.retroquest.model.TileDefinition;
import io.cannonforge.retroquest.model.TileEffect;
import io.cannonforge.retroquest.model.TileState;
import io.cannonforge.retroquest.model.TileStateManager;
import io.cannonforge.retroquest.model.TownEntrance;
import io.cannonforge.retroquest.overlay.DivineAudienceOverlay;
import io.cannonforge.retroquest.registry.ItemRegistry;
import io.cannonforge.retroquest.registry.LootGenerator;
import io.cannonforge.retroquest.registry.MonsterRegistry;
import io.cannonforge.retroquest.registry.TileRegistry;

/**
 * Manages overworld and town navigation: entering/exiting towns, overworld
 * teleporter tiles, tile step-effects (damage, warp), and map switching.
 *
 * <p>Public callbacks ({@link #finishTownEntry()} and {@link #finishTownExit()})
 * are invoked by the town-entry/exit animation classes when their animations
 * complete; they are exposed through {@link Retroquest} to preserve the existing
 * public API that animation classes depend on.
 */
public class NavigationController {

    private final Retroquest game;

    /** Overworld name targeted by the currently-playing teleport animation. */
    private String teleportTargetOverworld = "";
    private int    teleportTargetX         = 0;
    private int    teleportTargetY         = 0;

    private String pendingTownName     = null;
    /** Reset on each town entry so the "press E to leave" hint shows once per visit. */
    private boolean townExitHintShown  = false;
    /** Player flag set once they have left a town themselves — the hint stops after that. */
    private static final String TOWN_EXIT_KNOWN = "town_exit_known";
    private String pendingExitTownName = null;
    private int    pendingExitDoorX    = 0;
    private int    pendingExitDoorY    = 0;

    public NavigationController(Retroquest game) {
        this.game = game;
    }

    /** Returns a tile-state override if present, otherwise falls back to the TileEffect param. */
    private String getTileParam(TileState ts, TileEffect effect, String key) {
        if (ts != null && ts.data.containsKey(key)) return ts.getString(key, "");
        return effect.getParam(key);
    }

    /** Returns a tile-state int override if present, otherwise falls back to the TileEffect param. */
    private int getTileIntParam(TileState ts, TileEffect effect, String key, int defaultVal) {
        if (ts != null && ts.data.containsKey(key)) return ts.getInt(key, defaultVal);
        return effect.getIntParam(key, defaultVal);
    }

    private void startTeleportAnimation(String targetMap, int targetX, int targetY) {
        // Stepping onto the portal again mid-animation used to start a second 4.2s timer,
        // and both of them then called finishTeleport().
        if (game.isShowingTeleportAnimation()) return;
        game.setShowingTeleportAnimation(true);
        teleportTargetOverworld = targetMap;
        teleportTargetX = targetX;
        teleportTargetY = targetY;

        String sourceMap = game.getCurrentOverworldName();

        // Custom island-to-island transitions
        if ("thalorax".equals(sourceMap) && "umbryn".equals(targetMap)) {
            SoundManager.getInstance().playShadowDescent();
            game.getGamePanel().startShadowDescentAnimation();
            return;
        }
        if ("umbryn".equals(sourceMap) && "bellorak".equals(targetMap)) {
            SoundManager.getInstance().playWarMarch();
            game.getGamePanel().startWarMarchAnimation();
            return;
        }
        if ("bellorak".equals(sourceMap) && !"umbryn".equals(targetMap)) {
            SoundManager.getInstance().playAscension();
            game.getGamePanel().startAscensionAnimation();
            return;
        }

        // Default: generic star tunnel
        SoundManager.getInstance().playTeleportWhoosh();
        game.getGamePanel().startTeleportAnimation();
        Timer timer = new Timer(4200, e -> finishTeleport());
        timer.setRepeats(false);
        timer.start();
    }

    public void finishTeleport() {
        game.setShowingTeleportAnimation(false);
        game.getOverworldManager().loadOverworld(teleportTargetOverworld);
        char[][] map = game.getOverworldManager().getCurrentMap();
        game.setCurrentMap(map);
        // Persistence, dungeon view mode and the divine audience all key off the island
        // name; without this it keeps pointing at the island we just left.
        game.setCurrentOverworldName(teleportTargetOverworld);
        int[] dest = findLandingSpot(map, teleportTargetX, teleportTargetY);
        if (dest[0] != teleportTargetX || dest[1] != teleportTargetY) {
            System.err.println("[Teleport] " + teleportTargetOverworld + " destination ("
                    + teleportTargetX + "," + teleportTargetY + ") is out of bounds or blocked; "
                    + "landing at (" + dest[0] + "," + dest[1] + ") instead.");
        }
        game.getPlayer().setPosition(dest[0], dest[1]);
        game.updateCamera();
        applyPersistedMutations();
        game.getGamePanel().repaint();
        game.getStatsPanel().refresh();
    }

    /**
     * Clamps a teleport destination into the map and, if that tile cannot be stood on,
     * spirals outwards to the nearest walkable tile.
     *
     * <p>Without this, a portal whose authored coordinates are out of bounds or land on
     * water/void leaves the player unable to move in any direction — an unrecoverable
     * softlock that the auto-save then persists.
     */
    private int[] findLandingSpot(char[][] map, int x, int y) {
        if (map == null || map.length == 0) return new int[]{x, y};
        int h = map.length, w = map[0].length;
        int cx = Math.max(0, Math.min(w - 1, x));
        int cy = Math.max(0, Math.min(h - 1, y));
        if (game.isWalkable(map[cy][cx]) && !isPortalTile(map[cy][cx])) return new int[]{cx, cy};

        for (int r = 1; r < Math.max(w, h); r++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (Math.abs(dx) != r && Math.abs(dy) != r) continue;   // ring only
                    int nx = cx + dx, ny = cy + dy;
                    if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue;
                    if (game.isWalkable(map[ny][nx]) && !isPortalTile(map[ny][nx])) return new int[]{nx, ny};
                }
            }
        }
        return new int[]{cx, cy};   // nothing walkable anywhere; keep the clamped spot
    }

    /**
     * True if this tile sends the player somewhere when stepped on.
     *
     * <p>Landing directly on the portal that leads back is how a player gets stuck bouncing
     * between two islands: step off, step on, and away they go again. Arrivals are nudged one
     * tile clear of any portal, so the way back is beside you rather than underfoot.
     */
    private boolean isPortalTile(char tile) {
        TileDefinition def = TileRegistry.getById(tile);
        if (def == null || def.getOnStepEffect() == null) return false;
        String type = def.getOnStepEffect().getType();
        return "island_portal".equals(type) || "teleport".equals(type);
    }

    // ── Town Entry ────────────────────────────────────────────────────────────

    /** Looks up the town entrance at the player's position and starts the entry animation. */
    public void enterTownAtPlayerPosition() {
        for (TownEntrance te : game.getOverworldManager().getTownEntrances()) {
            if (te.worldX() == game.getPlayer().getX() && te.worldY() == game.getPlayer().getY()) {
                enterTownByName(te.townName());
                return;
            }
        }
        game.log("No town is linked to this entrance yet.", MessageLog.Type.DANGER);
    }

    public void enterTownByName(String townName) {
        SoundManager.getInstance().playEnterTown();
        pendingTownName = townName;
        game.getGamePanel().startTownEntryAnimation(townName);
        game.startOverlayRepaintTimer();
    }

    /** Called by {@link TownEntryAnimation} when its animation finishes. */
    public void finishTownEntry() {
        if (pendingTownName == null) return;
        String townName = pendingTownName;
        pendingTownName  = null;
        townExitHintShown = false;
        game.setInDungeon(false);
        game.setCurrentTown(new Town(townName, game.getPlayer().getX(), game.getPlayer().getY()));
        game.setCurrentMap(game.getCurrentTown().getInteriorMap());
        game.getPlayer().setPosition(game.getCurrentTown().getInteriorEntryX(), game.getCurrentTown().getInteriorEntryY());
        game.setLastSafeTownName(townName);
        game.log("You enter the town of " + townName + "!", MessageLog.Type.GOOD);
        game.getPlayer().progressQuest(Quest.Type.EXPLORE, townName, 1);
        // The arrival tile is the gate — say so now, while the player is standing on it
        maybeHintTownExit();
        game.updateCamera();
        applyPersistedMutations();
        if (game.getGamePanel()  != null) game.getGamePanel().repaint();
        if (game.getStatsPanel() != null) game.getStatsPanel().refresh();
    }

    // ── Town Exit ─────────────────────────────────────────────────────────────

    /**
     * Leaves the town if the player is standing at its way out. Returns {@code false} when
     * they are not, so the caller can fall through to whatever else 'E' might do.
     *
     * <p>Walking off the edge of the map still works and is unchanged; this exists because
     * several authored towns have no walkable border tile at all, which left the only exit
     * unreachable.
     */
    public boolean tryExitTownByDoor() {
        if (game.getCurrentTown() == null || !isAtTownExit()) return false;
        exitTown();
        return true;
    }

    /**
     * True when the player is standing on, or beside, the way out of the current town:
     * the spot they arrived at, a door on the map border, or the border itself.
     */
    public boolean isAtTownExit() {
        Town town = game.getCurrentTown();
        char[][] map = game.getCurrentMap();
        if (town == null || map == null || map.length == 0) return false;

        int px = game.getPlayer().getX(), py = game.getPlayer().getY();
        // The tile the player arrived on is the town gate, whatever the map looks like
        if (Math.abs(px - town.getInteriorEntryX()) <= 1
                && Math.abs(py - town.getInteriorEntryY()) <= 1) return true;

        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                int x = px + dx, y = py + dy;
                // Off the map — the player is on the border, one step from walking out
                if (y < 0 || y >= map.length || x < 0 || x >= map[y].length) return true;
                if (isBorderDoor(x, y, map)) return true;
            }
        }
        return false;
    }

    /** A door tile sitting on the map border — the authored towns' gate. */
    private boolean isBorderDoor(int x, int y, char[][] map) {
        boolean onBorder = (x == 0 || y == 0 || y == map.length - 1 || x == map[y].length - 1);
        if (!onBorder) return false;
        char c = map[y][x];
        return c == 'd' || c == '|' || c == '[' || c == '{' || c == 'E';
    }

    /**
     * Tells the player how to get out the first time they stand at the gate. Shown at most
     * once per visit, and not at all once they have left a town under their own steam.
     */
    public void maybeHintTownExit() {
        if (townExitHintShown || game.getCurrentTown() == null) return;
        if (game.getPlayer().hasFlag(TOWN_EXIT_KNOWN)) return;
        if (!isAtTownExit()) return;
        townExitHintShown = true;
        game.log("Press [E] to step outside.", MessageLog.Type.SYSTEM);
    }

    public void exitTown() {
        game.getPlayer().setFlag(TOWN_EXIT_KNOWN, "true");
        SoundManager.getInstance().playLeaveTown();
        pendingExitTownName = game.getCurrentTown().getName();
        pendingExitDoorX    = game.getCurrentTown().getWorldDoorX();
        pendingExitDoorY    = game.getCurrentTown().getWorldDoorY();
        game.getGamePanel().startTownExitAnimation(pendingExitTownName);
        game.startOverlayRepaintTimer();
    }

    /** Called by {@link TownExitAnimation} when its animation finishes. */
    public void finishTownExit() {
        game.setCurrentMap(game.getOverworldManager().getCurrentMap());
        game.getPlayer().setPosition(pendingExitDoorX, pendingExitDoorY);
        game.log("You leave " + pendingExitTownName + " and return to the wilderness.", MessageLog.Type.INFO);
        game.setCurrentTown(null);
        pendingExitTownName = null;
        game.updateCamera();
        applyPersistedMutations();
        if (game.getGamePanel()  != null) game.getGamePanel().repaint();
        if (game.getStatsPanel() != null) game.getStatsPanel().refresh();
    }

    // ── Overworld Switching ───────────────────────────────────────────────────

    public void switchOverworld(String mapName) {
        game.getOverworldManager().loadOverworld(mapName);
        game.setCurrentOverworldName(mapName);
        game.setCurrentMap(game.getOverworldManager().getCurrentMap());
        game.updateCamera();
        applyPersistedMutations();
        game.getGamePanel().repaint();
    }

    // ── Tile Step Effects ─────────────────────────────────────────────────────

    /** Applies any {@link TileEffect} defined for the tile the player just stepped on. */
    public void triggerTileEffect() {
        maybeHintTownExit();
        int px = game.getPlayer().getX(), py = game.getPlayer().getY();
        char           tileId = game.getCurrentMap()[py][px];
        TileDefinition def    = TileRegistry.getByIdSafe(tileId);
        TileEffect     effect = def.getOnStepEffect();

        // Per-instance TileState teleport data takes priority over tile-definition effects
        TileState ts = TileStateManager.get(game.getSaveData(), currentMapKey(), px, py);
        if (ts != null && ts.data.containsKey("teleport_map")) {
            String targetMap = ts.getString("teleport_map", "");
            if (!targetMap.isEmpty()) {
                String requiredKeyId = ts.getString("requiredKeyId", "");
                if (!requiredKeyId.isEmpty() && !playerHasItem(requiredKeyId)) {
                    game.log(ts.getString("lockedMessage", "The portal is sealed."), MessageLog.Type.DANGER);
                    return;
                }
                int targetX = ts.getInt("teleport_x", game.getCurrentMap()[0].length / 2);
                int targetY = ts.getInt("teleport_y", game.getCurrentMap().length / 2);
                if (tryDivineAudience(requiredKeyId, targetMap, targetX, targetY)) return;
                startTeleportAnimation(targetMap, targetX, targetY);
                return;
            }
        }

        if (effect.isNone()) return;

        String type = effect.getType();
        switch (type) {
            case "island_portal", "teleport" -> handleTeleportEffect(effect, def, "island_portal".equals(type));
            case "damage"                    -> handleDamageEffect(effect, def);
            case "encounter"                 -> handleEncounterEffect(effect, def);
            case "trap_once"                 -> handleTrapOnceEffect(effect, px, py);
            case "set_tile"                  -> handleSetTileEffect(effect);
            case "toggle_door"               -> handleToggleDoor(px, py, effect);
            case "pressure_plate"            -> handlePressurePlate(px, py, effect);
            case "shrine"                    -> handleShrineEffect(effect, px, py);
            case "treasure"                  -> handleTreasureEffect(effect, px, py);
            case "ambush"                    -> handleAmbushEffect(effect, px, py);
            case "geyser"                    -> handleGeyserEffect(effect);
            case "wind_current"              -> handleWindCurrentEffect(effect, px, py);
            case "lore"                      -> handleLoreEffect(effect, px, py);
        }
    }

    private boolean playerHasItem(String itemId) {
        if (game.getPlayer().hasKey(itemId)) return true;
        for (InventorySlot slot : game.getPlayer().getInventorySlots()) {
            if (!slot.isEmpty() && slot.getItem().getId().equals(itemId)) return true;
        }
        return false;
    }

    private void handleTeleportEffect(TileEffect effect, TileDefinition def, boolean confirm) {
        String requiredKeyId = effect.getParam("requiredKeyId");
        if (!requiredKeyId.isEmpty() && !playerHasItem(requiredKeyId)) {
            String lockedMsg = effect.getParam("lockedMessage");
            game.log(!lockedMsg.isEmpty() ? lockedMsg : "The portal is sealed.", MessageLog.Type.DANGER);
            return;
        }
        String targetMap = effect.getParam("map");
        if (targetMap.isEmpty()) {
            game.log("Misconfigured teleport tile — missing map parameter.", MessageLog.Type.DANGER);
            return;
        }
        int targetX = effect.getIntParam("x", game.getCurrentMap()[0].length / 2);
        int targetY = effect.getIntParam("y", game.getCurrentMap().length / 2);

        // Island portals sit in the open, often a step or two from where the last one put
        // you down. Crossing the world the instant a foot lands on one turned walking
        // around the arrival beach into a shuttle ride, so ask first.
        if (confirm) {
            String name = def != null ? def.getName() : "the portal";
            game.getMessageLog().prompt(name + " stands open. Step through?",
                () -> {
                    if (tryDivineAudience(requiredKeyId, targetMap, targetX, targetY)) return;
                    startTeleportAnimation(targetMap, targetX, targetY);
                },
                () -> game.log("You stay where you are.", MessageLog.Type.DIM));
            return;
        }
        if (tryDivineAudience(requiredKeyId, targetMap, targetX, targetY)) return;
        startTeleportAnimation(targetMap, targetX, targetY);
    }

    /**
     * If this is a first-time forward island portal, shows the departing god's
     * farewell overlay and defers the teleport until the player dismisses it.
     * @return {@code true} if the overlay was shown (teleport deferred)
     */
    private boolean tryDivineAudience(String requiredKeyId, String targetMap,
                                       int targetX, int targetY) {
        if (requiredKeyId == null || requiredKeyId.isEmpty()) return false;
        God god = DivineAudienceOverlay.getDepartingGod(game.getCurrentOverworldName());
        if (god == null) return false;
        String flagKey = "divine_audience:" + god.name().toLowerCase();
        if (game.getPlayer().hasFlag(flagKey)) return false;

        game.getPlayer().setFlag(flagKey, "seen");
        String msg = DivineAudienceOverlay.selectMessage(god, game.getPlayer());
        game.getGamePanel().openDivineAudience(god, msg,
            () -> startTeleportAnimation(targetMap, targetX, targetY));
        game.startOverlayRepaintTimer();
        return true;
    }

    private void handleDamageEffect(TileEffect effect, TileDefinition def) {
        int    amount  = effect.getIntParam("amount", 0);
        String message = effect.getParam("message");
        if (amount <= 0) return;

        game.getPlayer().takeDamage(amount);
        SoundManager.getInstance().playTrap();
        if (message != null && !message.isEmpty()) {
            game.log(message, MessageLog.Type.DANGER);
        } else if (amount >= 3) {
            game.log("You took " + amount + " damage from " + def.getName() + "!", MessageLog.Type.DANGER);
        }
        if (game.getPlayer().isDead()) {
            game.killPlayer("You succumbed to the " + def.getName() + ".");
        }
    }

    private void handleEncounterEffect(TileEffect effect, TileDefinition def) {
        String message = effect.getParam("message");
        if (message != null && !message.isEmpty()) {
            game.log(message, MessageLog.Type.DANGER);
        }
        int difficulty = game.getOverworldManager().getSpawnDifficulty(
                game.getPlayer().getX(), game.getPlayer().getY());
        int monsterLevel = 1 + (difficulty * 49) / 99;
        Monster m = MonsterRegistry.getRandomMonster(monsterLevel, def.getSpawnWeights());
        game.getGamePanel().startCombat(m, (won, gold, loot, leveled, newLevel) -> {
            if (game.getGamePanel() != null) game.getGamePanel().repaint();
        });
    }

    private void handleTrapOnceEffect(TileEffect effect, int px, int py) {
        if (TileStateManager.getBoolProperty(game.getSaveData(), currentMapKey(), px, py, "triggered")) return;

        int    amount  = effect.getIntParam("amount", 0);
        String message = effect.getParam("message");
        TileStateManager.setProperty(game.getSaveData(), currentMapKey(), px, py, "triggered", "true");
        if (amount <= 0) return;

        game.getPlayer().takeDamage(amount);
        SoundManager.getInstance().playTrap();
        game.log(message != null && !message.isEmpty() ? message : "A trap springs!", MessageLog.Type.DANGER);
        if (game.getPlayer().isDead()) {
            game.killPlayer("You were killed by a trap.");
        }
    }

    /**
     * Hidden shrine: one-time discovery grants divine favor + lore message.
     * Params: god (name), favor (amount, default 5), message (lore text),
     *         discoveredTileId (optional: tile to change to after discovery).
     */
    private void handleShrineEffect(TileEffect effect, int px, int py) {
        if (TileStateManager.getBoolProperty(game.getSaveData(), currentMapKey(), px, py, "discovered")) return;

        TileState ts = TileStateManager.get(game.getSaveData(), currentMapKey(), px, py);
        TileStateManager.setProperty(game.getSaveData(), currentMapKey(), px, py, "discovered", "true");

        String message = getTileParam(ts, effect, "message");
        game.log(message != null && !message.isEmpty() ? message : "You discover a hidden shrine!", MessageLog.Type.GOOD);

        // Grant divine favor
        String godName = getTileParam(ts, effect, "god");
        int favorAmount = getTileIntParam(ts, effect, "favor", 5);
        if (godName != null && !godName.isEmpty()) {
            try {
                God god = God.valueOf(godName.toUpperCase());
                game.getPlayer().addFavor(god, favorAmount);
                game.log("You feel " + god.displayName + "'s favor.", MessageLog.Type.GOOD);
            } catch (IllegalArgumentException ignored) { }
        }

        // Grant XP if specified
        int xpReward = getTileIntParam(ts, effect, "xp", 0);
        if (xpReward > 0) {
            game.getPlayer().addXP(xpReward);
            game.log("+" + xpReward + " XP", MessageLog.Type.GOOD);
        }

        // Optionally change tile sprite to a "discovered" variant
        String discoveredTileId = getTileParam(ts, effect, "discoveredTileId");
        if (discoveredTileId != null && !discoveredTileId.isEmpty()) {
            char newId = discoveredTileId.charAt(0);
            game.getCurrentMap()[py][px] = newId;
            TileStateManager.setOverride(game.getSaveData(), currentMapKey(), px, py, newId);
            game.getGamePanel().repaint();
        }

        SoundManager.getInstance().play("altarChime");
    }

    /**
     * Buried treasure: one-time loot find. Gives a specific item (by ID) or random loot.
     * Params: itemId (specific item), tier (for random loot, default 1), message, discoveredTileId.
     */
    private void handleTreasureEffect(TileEffect effect, int px, int py) {
        if (TileStateManager.getBoolProperty(game.getSaveData(), currentMapKey(), px, py, "looted")) return;

        TileState ts = TileStateManager.get(game.getSaveData(), currentMapKey(), px, py);

        // Optional item requirement (e.g. sunken chests needing a diving item)
        String requiredItemId = getTileParam(ts, effect, "requiredItemId");
        if (requiredItemId != null && !requiredItemId.isEmpty() && !playerHasItem(requiredItemId)) {
            String lockedMsg = getTileParam(ts, effect, "lockedMessage");
            game.log(lockedMsg != null && !lockedMsg.isEmpty() ? lockedMsg : "You can't reach this treasure.", MessageLog.Type.INFO);
            return;
        }

        TileStateManager.setProperty(game.getSaveData(), currentMapKey(), px, py, "looted", "true");

        String message = getTileParam(ts, effect, "message");
        game.log(message != null && !message.isEmpty() ? message : "You find buried treasure!", MessageLog.Type.GOOD);

        // Try specific item first, then random loot
        String itemId = getTileParam(ts, effect, "itemId");
        Item loot = null;
        if (itemId != null && !itemId.isEmpty()) {
            loot = ItemRegistry.getById(itemId);
        }
        if (loot == null) {
            int tier = getTileIntParam(ts, effect, "tier", 1);
            loot = LootGenerator.getRandomLoot(tier * 5, game.getPlayer().getLevel());
        }

        if (loot != null) {
            if (game.getPlayer().addItem(loot)) {
                game.log("Found: " + loot.getName(), MessageLog.Type.GOOD);
            } else {
                game.log("Your inventory is full!", MessageLog.Type.DANGER);
            }
        }

        // Grant gold if specified
        int gold = getTileIntParam(ts, effect, "gold", 0);
        if (gold > 0) {
            game.getPlayer().addGold(gold);
            game.log("Found " + gold + " gold!", MessageLog.Type.GOOD);
        }

        // Optionally change tile to looted variant
        String discoveredTileId = getTileParam(ts, effect, "discoveredTileId");
        if (discoveredTileId != null && !discoveredTileId.isEmpty()) {
            char newId = discoveredTileId.charAt(0);
            game.getCurrentMap()[py][px] = newId;
            TileStateManager.setOverride(game.getSaveData(), currentMapKey(), px, py, newId);
            game.getGamePanel().repaint();
        }

        SoundManager.getInstance().play("coin");
    }

    /**
     * Ambush encounter: one-time guaranteed fight against a specific monster with guaranteed loot.
     * Params: monsterId (required), message, lootItemId (specific drop), lootTier (random drop tier).
     */
    private void handleAmbushEffect(TileEffect effect, int px, int py) {
        if (TileStateManager.getBoolProperty(game.getSaveData(), currentMapKey(), px, py, "defeated")) return;

        TileState ts = TileStateManager.get(game.getSaveData(), currentMapKey(), px, py);

        String message = getTileParam(ts, effect, "message");
        game.log(message != null && !message.isEmpty() ? message : "An ambush!", MessageLog.Type.DANGER);

        String monsterId = getTileParam(ts, effect, "monsterId");
        Monster template = (monsterId != null && !monsterId.isEmpty()) ? MonsterRegistry.getById(monsterId) : null;
        Monster m;
        if (template != null) {
            m = new Monster(template);
        } else {
            int difficulty = game.getOverworldManager().getSpawnDifficulty(px, py);
            int level = 1 + (difficulty * 49) / 99;
            m = MonsterRegistry.getRandomMonster(level);
        }

        game.getGamePanel().startCombat(m, (won, gold, loot, leveled, newLevel) -> {
            if (won) {
                TileStateManager.setProperty(game.getSaveData(), currentMapKey(), px, py, "defeated", "true");

                String lootItemId = getTileParam(ts, effect, "lootItemId");
                Item bonusLoot = null;
                if (lootItemId != null && !lootItemId.isEmpty()) {
                    bonusLoot = ItemRegistry.getById(lootItemId);
                } else {
                    int lootTier = getTileIntParam(ts, effect, "lootTier", 0);
                    if (lootTier > 0) {
                        bonusLoot = LootGenerator.getRandomLoot(lootTier * 5, game.getPlayer().getLevel());
                    }
                }
                if (bonusLoot != null && game.getPlayer().addItem(bonusLoot)) {
                    game.log("Bonus loot: " + bonusLoot.getName(), MessageLog.Type.GOOD);
                }
            }
            if (game.getGamePanel() != null) game.getGamePanel().repaint();
        });
    }

    /**
     * Lava geyser: random chance of damage each step. Unlike damage tiles (always hit),
     * geysers have a configurable probability. Params: amount, chance (0-100, default 50), message.
     */
    private void handleGeyserEffect(TileEffect effect) {
        int chance = effect.getIntParam("chance", 50);
        if ((int)(Math.random() * 100) >= chance) return;

        int amount = effect.getIntParam("amount", 5);
        String message = effect.getParam("message");
        game.getPlayer().takeDamage(amount);
        game.log(message != null && !message.isEmpty() ? message : "A geyser erupts beneath you!", MessageLog.Type.DANGER);
        if (game.getPlayer().isDead()) {
            game.killPlayer("You were killed by a geyser.");
        }
    }

    /**
     * Wind current: pushes the player N tiles in a direction. Stops at non-walkable tiles.
     * Params: direction (north/south/east/west), distance (default 3), message.
     */
    private void handleWindCurrentEffect(TileEffect effect, int px, int py) {
        TileState ts = TileStateManager.get(game.getSaveData(), currentMapKey(), px, py);
        String dir = getTileParam(ts, effect, "direction");
        int distance = getTileIntParam(ts, effect, "distance", 3);
        int dx = 0, dy = 0;
        switch (dir != null ? dir.toLowerCase() : "") {
            case "north" -> dy = -1;
            case "south" -> dy = 1;
            case "east"  -> dx = 1;
            case "west"  -> dx = -1;
            default -> { return; }
        }

        String message = getTileParam(ts, effect, "message");
        game.log(message != null && !message.isEmpty() ? message : "A powerful wind sweeps you away!", MessageLog.Type.INFO);

        char[][] map = game.getCurrentMap();
        int h = map.length, w = map[0].length;
        int nx = px, ny = py;
        for (int i = 0; i < distance; i++) {
            int testX = nx + dx, testY = ny + dy;
            if (testX < 0 || testX >= w || testY < 0 || testY >= h) break;
            TileDefinition td = TileRegistry.getByIdSafe(map[testY][testX]);
            if (!td.isWalkable()) break;
            nx = testX;
            ny = testY;
        }

        if (nx != px || ny != py) {
            game.getPlayer().setPosition(nx, ny);
            game.getGamePanel().repaint();
        }
    }

    /**
     * Lore stone / talking tree / memory echo: repeatable text display on step.
     * One-time variant available via "oneTime" param.
     * Params: message (required), oneTime (bool, default false).
     */
    private void handleLoreEffect(TileEffect effect, int px, int py) {
        TileState ts = TileStateManager.get(game.getSaveData(), currentMapKey(), px, py);
        boolean oneTime = "true".equalsIgnoreCase(getTileParam(ts, effect, "oneTime"));
        if (oneTime && TileStateManager.getBoolProperty(game.getSaveData(), currentMapKey(), px, py, "read")) return;

        String message = getTileParam(ts, effect, "message");
        if (message == null || message.isEmpty()) return;

        game.log(message, MessageLog.Type.INFO);

        if (oneTime) {
            TileStateManager.setProperty(game.getSaveData(), currentMapKey(), px, py, "read", "true");
        }
    }

    private void handleSetTileEffect(TileEffect effect) {
        int    targetX   = effect.getIntParam("x", -1);
        int    targetY   = effect.getIntParam("y", -1);
        String tileIdStr = effect.getParam("tileId");
        String message   = effect.getParam("message");
        if (targetX < 0 || targetY < 0 || tileIdStr == null || tileIdStr.isEmpty()) return;

        char newId = tileIdStr.charAt(0);
        if (targetY < game.getCurrentMap().length && targetX < game.getCurrentMap()[0].length) {
            game.getCurrentMap()[targetY][targetX] = newId;
            TileStateManager.setOverride(game.getSaveData(), currentMapKey(), targetX, targetY, newId);
            if (message != null && !message.isEmpty())
                game.log(message, MessageLog.Type.INFO);
            game.getGamePanel().repaint();
        }
    }

    // ── Locked door unlock ────────────────────────────────────────────────────

    /**
     * Called before movement to check if the destination tile is a locked door
     * that the player can open with a matching key in their inventory.
     *
     * @return {@code true} if the tile was unlocked (movement should be allowed)
     */
    public boolean tryUnlockTile(int x, int y) {
        if (game.getCurrentMap() == null) return false;
        if (y < 0 || y >= game.getCurrentMap().length || x < 0 || x >= game.getCurrentMap()[0].length) return false;

        char tileId = game.getCurrentMap()[y][x];
        TileDefinition def    = TileRegistry.getByIdSafe(tileId);
        TileEffect     effect = def.getOnStepEffect();
        if (!"locked_door".equals(effect.getType())) return false;

        // Instance-level TileState can override type-level effect params
        TileState ts = TileStateManager.get(game.getSaveData(), currentMapKey(), x, y);

        // If initial state marked the door as already open, allow passage without consuming a key
        if (ts != null && ts.getBool("open", false)) {
            return true;
        }

        String requiredKeyId  = getTileParam(ts, effect, "requiredKeyId");
        String unlockedTileId = getTileParam(ts, effect, "unlockedTileId");
        String message        = getTileParam(ts, effect, "message");
        String lockedMessage  = getTileParam(ts, effect, "lockedMessage");

        // Check key ring first, then inventory
        boolean hasKey = game.getPlayer().hasKey(requiredKeyId);
        if (!hasKey) {
            InventorySlot[] slots = game.getPlayer().getInventorySlots();
            for (int i = 0; i < slots.length; i++) {
                if (!slots[i].isEmpty() && slots[i].getItem().getId().equals(requiredKeyId)) {
                    hasKey = true;
                    break;
                }
            }
        }

        if (hasKey) {
            char newTileChar = (!unlockedTileId.isEmpty()) ? unlockedTileId.charAt(0) : 'F';
            game.getCurrentMap()[y][x] = newTileChar;
            TileStateManager.setOverride(game.getSaveData(), currentMapKey(), x, y, newTileChar);
            game.log(!message.isEmpty() ? message : "You unlock the door.", MessageLog.Type.GOOD);
            SoundManager.getInstance().play("door");
            game.getGamePanel().repaint();
            return true;
        } else {
            game.log(!lockedMessage.isEmpty() ? lockedMessage : "The door is locked.", MessageLog.Type.DANGER);
            return false;
        }
    }

    // ── Map context key ───────────────────────────────────────────────────────

    /**
     * Key the current map's per-tile state is stored under. Delegates to {@link Retroquest}
     * so dungeon keys carry the dungeon's identity as well as its depth — see
     * {@link Retroquest#currentMapKey()}.
     */
    private String currentMapKey() {
        return game.currentMapKey();
    }

    // ── Persisted tile mutations ───────────────────────────────────────────────

    /** Re-applies all stored tile overrides for the current map, then seeds any
     *  initial tile states authored in RetroForge that have not yet been set. */
    public void applyPersistedMutations() {
        if (game.getCurrentMap() == null) return;
        String mapKey = currentMapKey();
        TileStateManager.applyTilesToMap(game.getSaveData(), mapKey, game.getCurrentMap());

        Map<String, TileState> initial = getInitialStatesForCurrentMap();
        applyMapInitialStates(initial, mapKey);
    }

    private Map<String, TileState> getInitialStatesForCurrentMap() {
        if (game.isInDungeon()) return null;
        MapData md = (game.getCurrentTown() != null)
                ? game.getCurrentTown().getMapData()
                : game.getOverworldManager().getMapData();
        return (md != null) ? md.initialTileStates : null;
    }

    private void applyMapInitialStates(Map<String, TileState> initial, String mapKey) {
        if (initial == null || game.getSaveData() == null) return;
        Map<String, TileState> saveStates = game.getSaveData().getOrCreateTileStates();
        for (Map.Entry<String, TileState> entry : initial.entrySet()) {
            String saveKey = mapKey + ":" + entry.getKey();
            if (!saveStates.containsKey(saveKey)) {
                TileState copy = new TileState();
                copy.data.putAll(entry.getValue().data);
                saveStates.put(saveKey, copy);
            }
        }
    }

    // ── Toggle door ───────────────────────────────────────────────────────────

    /**
     * Called when a player steps on a {@code toggle_door} tile.
     * Reads the current open/closed state and flips it, updating both the live
     * map grid and the persisted tile state.
     */
    private void handleToggleDoor(int x, int y, TileEffect effect) {
        // Instance-level overrides from TileState take precedence over type-level params
        TileState dts = TileStateManager.get(game.getSaveData(), currentMapKey(), x, y);
        String openCharStr  = getTileParam(dts, effect, "openChar");
        String closeCharStr = getTileParam(dts, effect, "closeChar");

        boolean isOpen = dts != null ? dts.getBool("open", false)
                : TileStateManager.getBoolProperty(game.getSaveData(), currentMapKey(), x, y, "open");
        char newChar;
        if (isOpen) {
            newChar = closeCharStr.isEmpty() ? '#' : closeCharStr.charAt(0);
            TileStateManager.setProperty(game.getSaveData(), currentMapKey(), x, y, "open", "false");
            game.log("The door closes.", MessageLog.Type.INFO);
        } else {
            newChar = openCharStr.isEmpty() ? 'F' : openCharStr.charAt(0);
            TileStateManager.setProperty(game.getSaveData(), currentMapKey(), x, y, "open", "true");
            game.log("The door swings open.", MessageLog.Type.INFO);
        }
        game.getCurrentMap()[y][x] = newChar;
        TileStateManager.setOverride(game.getSaveData(), currentMapKey(), x, y, newChar);
        SoundManager.getInstance().play("door");
        game.getGamePanel().repaint();
    }

    // ── Pressure plate ────────────────────────────────────────────────────────

    /**
     * Called when a player steps on a {@code pressure_plate} tile.
     * Toggles the target tile between its active and inactive chars, persisting
     * the change.  If {@code once=true}, the plate is inert after first trigger.
     */
    private void handlePressurePlate(int px, int py, TileEffect effect) {
        boolean once = "true".equals(effect.getParam("once"));
        if (once && TileStateManager.getBoolProperty(game.getSaveData(), currentMapKey(), px, py, "triggered")) {
            return;
        }

        // Instance-level overrides from TileState take precedence over type-level params
        TileState pts = TileStateManager.get(game.getSaveData(), currentMapKey(), px, py);
        int targetX = getTileIntParam(pts, effect, "x", -1);
        int targetY = getTileIntParam(pts, effect, "y", -1);
        String activateStr   = getTileParam(pts, effect, "activateChar");
        String deactivateStr = getTileParam(pts, effect, "deactivateChar");

        if (targetX < 0 || targetY < 0 || targetY >= game.getCurrentMap().length || targetX >= game.getCurrentMap()[0].length) return;

        boolean isActive = TileStateManager.getBoolProperty(
                game.getSaveData(), currentMapKey(), targetX, targetY, "active");
        char newChar;
        if (isActive) {
            newChar = deactivateStr.isEmpty()
                    ? game.getCurrentMap()[targetY][targetX] : deactivateStr.charAt(0);
            TileStateManager.setProperty(game.getSaveData(), currentMapKey(), targetX, targetY, "active", "false");
        } else {
            newChar = activateStr.isEmpty()
                    ? game.getCurrentMap()[targetY][targetX] : activateStr.charAt(0);
            TileStateManager.setProperty(game.getSaveData(), currentMapKey(), targetX, targetY, "active", "true");
        }
        game.getCurrentMap()[targetY][targetX] = newChar;
        TileStateManager.setOverride(game.getSaveData(), currentMapKey(), targetX, targetY, newChar);

        String message = effect.getParam("message");
        if (message != null && !message.isEmpty()) game.log(message, MessageLog.Type.INFO);

        if (once) {
            TileStateManager.setProperty(game.getSaveData(), currentMapKey(), px, py, "triggered", "true");
        }
        game.getGamePanel().repaint();
    }
}

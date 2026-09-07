package io.cannonforge.retroquest.core;
import java.awt.BorderLayout;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import com.google.gson.Gson;

import io.cannonforge.retroquest.animation.DungeonEntryAnimation;
import io.cannonforge.retroquest.animation.DungeonExitAnimation;
import io.cannonforge.retroquest.animation.PlayerDeathAnimation;
import io.cannonforge.retroquest.animation.TownEntryAnimation;
import io.cannonforge.retroquest.animation.TownExitAnimation;
import io.cannonforge.retroquest.animation.WashAshoreAnimation;
import io.cannonforge.retroquest.controller.DungeonController;
import io.cannonforge.retroquest.controller.EncounterController;
import io.cannonforge.retroquest.controller.NavigationController;
import io.cannonforge.retroquest.controller.NpcController;
import io.cannonforge.retroquest.dialog.SaveLoadDialog;
import io.cannonforge.retroquest.dialog.StartMenuDialog;
import io.cannonforge.retroquest.editor.RetroForge;
import io.cannonforge.retroquest.model.Dungeon;
import io.cannonforge.retroquest.model.MapData;
import io.cannonforge.retroquest.model.NPC;
import io.cannonforge.retroquest.model.Player;
import io.cannonforge.retroquest.model.Quest;
import io.cannonforge.retroquest.model.Spell;
import io.cannonforge.retroquest.model.TileStateManager;
import io.cannonforge.retroquest.model.TownEntrance;
import io.cannonforge.retroquest.overlay.CombatOverlay;
import io.cannonforge.retroquest.overlay.SaveLoadOverlay;
import io.cannonforge.retroquest.registry.QuestRegistry;
import io.cannonforge.retroquest.registry.TileRegistry;

/**
 * Main game window and central controller for RetroQuest.
 *
 * <p>Manages the Swing frame, player state, input dispatch, camera, save/load,
 * zoom, and the overlay repaint timer. Gameplay sub-systems are delegated to
 * dedicated controllers:
 * <ul>
 *   <li>{@link DungeonController}   — dungeon navigation, specials, fog-of-war</li>
 *   <li>{@link NavigationController} — town/overworld navigation, tile effects</li>
 *   <li>{@link NpcController}        — NPC dialog, quests, inn, map spells</li>
 *   <li>{@link EncounterController}  — random encounter rolls</li>
 * </ul>
 *
 * <p>Entry point: {@link #main(String[])} creates the window; a
 * {@link StartMenuDialog} is shown if no auto-loaded save is present.
 */
@SuppressWarnings("serial")
public class Retroquest extends JFrame {

    // ── UI Components ─────────────────────────────────────────────────────────
    GamePanel   gamePanel;
    StatsPanel  statsPanel;
    MessageLog  messageLog;

    // ── Game State (package-private so controllers can read/write directly) ──
    Player   player;
    Town     currentTown  = null;  // null = overworld
    char[][] currentMap;
    boolean  inDungeon    = false;
    int      currentDepth = 0;
    /**
     * Permanently revealed tiles per dungeon level, keyed by {@code "<dungeonId>:<depth>"}.
     * The dungeon id keeps the 13 authored dungeons and the procedural one from sharing
     * one fog-of-war namespace — see {@link #revealedKey(int)}.
     */
    final Map<String, boolean[][]> revealed = new HashMap<>();
    /** Permanently revealed tiles per town (keyed by town name). */
    final Map<String, boolean[][]> townRevealed = new HashMap<>();
    OverworldManager overworldManager;
    String currentOverworldName = "lirandel";
    final io.cannonforge.retroquest.model.DungeonViewState dungeonViewState =
            new io.cannonforge.retroquest.model.DungeonViewState();

    /** Current authored dungeon MapData (null when not in an authored dungeon). */
    MapData currentDungeonMapData = null;

    /**
     * Set by {@link RetroRecorder} only. Keeps {@link #startGame} from showing the window: the
     * recorder paints {@link GamePanel} straight into a {@code BufferedImage}, so a visible frame
     * would do nothing but flash on the operator's desktop for the length of a render.
     */
    boolean recordingOffscreen = false;

    /** Set to {@code true} while a teleport animation is in progress. */
    boolean showingTeleportAnimation = false;

    /** Last town the player safely entered — used by Teleport/Word of Recall map spells. */
    String lastSafeTownName = io.cannonforge.retroquest.model.GameConfig.get().getLastSafeTown();

    /** Current session's tile-state store; set on new game and on load. */
    SaveData saveData = null;

    // ── Controllers ───────────────────────────────────────────────────────────
    private DungeonController    dungeonController;
    private NavigationController navController;
    private NpcController        npcController;
    private EncounterController  encounterController;

    // ── Camera & Zoom ─────────────────────────────────────────────────────────
    private int cameraX = 0;
    private int cameraY = 0;
    private int viewW   = 24;
    private int viewH   = 18;
    private static final int VIEW_MIN  = 10;
    private static final int VIEW_MAX  = 40;
    private static final int VIEW_STEP = 2;

    // ── Input / Timing ────────────────────────────────────────────────────────
    private KeyAdapter gameKeyListener;
    private long       lastMoveTime = 0;
    /**
     * Minimum milliseconds between accepted movement keys — the walk speed when a key is
     * held down. Overridable with {@code -Dretroquest.moveDelayMs=<n>} so a scripted
     * playthrough can run without waiting out the input cadence.
     */
    private static final long MOVE_DELAY = Long.getLong("retroquest.moveDelayMs", 180L);


    // ── Auto-Save ────────────────────────────────────────────────────────────
    private int stepsSinceAutoSave = 0;
    private static final int AUTO_SAVE_INTERVAL = 50;

    // ── Overlay Repaint Timer ─────────────────────────────────────────────────
    private Timer overlayRepaintTimer = null;

    /**
     * Set while {@link #restart()} is swapping one game window for another, so the
     * outgoing window's {@code windowClosed} handler doesn't kill the JVM out from
     * under the replacement (its modal start menu pumps that pending event).
     */
    private static boolean restarting = false;

    /** Runs when the save/load overlay is dismissed without saving or loading. */
    private Runnable saveLoadCancelHook = null;

    // ─────────────────────────────────────────────────────────────────────────
    // Constructor
    // ─────────────────────────────────────────────────────────────────────────

    public Retroquest() { this(true); }

    /**
     * Builds the game frame, optionally without opening the start menu.
     *
     * <p>{@code showStartMenu == false} is the offscreen-rendering entry point used by
     * {@link RetroRecorder}: the constructor's {@link StartMenuDialog} is modal, so a
     * recorder that used the public constructor would block on the EDT forever and would
     * put a window on the operator's screen. Nothing else should pass false — a game built
     * this way has no player, no map and no panel until a caller supplies them.
     */
    Retroquest(boolean showStartMenu) {
        setTitle("RETROQUEST");
        getContentPane().setBackground(new java.awt.Color(10, 12, 16));
        // Use DISPOSE_ON_CLOSE so closing the game window doesn't kill RetroForge
        // when launched via the playtest button (both share the same JVM).
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosed(java.awt.event.WindowEvent e) {
                // A restart is replacing this window — the new one owns the JVM now.
                if (restarting) return;
                // Toggling the window border requires dispose() and re-show, and windowClosed is
                // delivered through the event queue rather than synchronously — so by the time it
                // arrives the window is back on screen and a flag set around the call has already
                // been cleared. Asking whether this window is actually gone needs no timing at
                // all: if it is displayable, it was not really closed.
                if (togglingFullscreen || isDisplayable()) return;
                // If no RetroForge editor is open, terminate the JVM so the
                // process doesn't linger from non-daemon threads (timers, sound, etc.).
                for (java.awt.Window w : java.awt.Window.getWindows()) {
                    if (w instanceof RetroForge && w.isDisplayable()) return;
                    // Another game window is already up (restart) — leave the JVM alone.
                    if (w != Retroquest.this && w instanceof Retroquest && w.isDisplayable()) return;
                }
                System.exit(0);
            }
        });
        setResizable(false);
        SoundManager.getInstance().warmup();

        overworldManager    = new OverworldManager();
        dungeonController   = new DungeonController(this);
        navController       = new NavigationController(this);
        npcController       = new NpcController(this);
        encounterController = new EncounterController(this);

        if (showStartMenu) new StartMenuDialog(this);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Game Start / UI Setup
    // ─────────────────────────────────────────────────────────────────────────

    public void startGame(Player newPlayer) {
        startGame(newPlayer, false);
    }

    public void startGame(Player newPlayer, boolean isNewGame) {
        SoundManager.getInstance().warmup();
        this.player = newPlayer;
        if (isNewGame || saveData == null) saveData = new SaveData();
        if (isNewGame) {
            // A fresh character starts on the surface — never inherit dungeon state, fog or
            // an entry town from whatever game was running before.
            inDungeon = false;
            currentDepth = 0;
            currentDungeonMapData = null;
            dungeonViewState.setAuthoredDungeonName(null);
            dungeonViewState.setMode(io.cannonforge.retroquest.model.DungeonViewState.Mode.TOP_DOWN);
            dungeonController.setEntryTown(null, 0, 0);
            revealed.clear();
            townRevealed.clear();
        }
        Player.questCompleteLogger = msg -> log(msg, MessageLog.Type.GOOD);
        Player.levelUpLogger = this::logLevelUp;
        Player.questCompleteNotifier = q -> {
            gamePanel.getNotificationOverlay().enqueueQuestComplete(q);
            SoundManager.getInstance().play("powerup");
            startOverlayRepaintTimer();
        };
        Player.levelUpNotifier = lvl -> {
            gamePanel.getNotificationOverlay().enqueueLevelUp(lvl);
            startOverlayRepaintTimer();
        };

        // Auto-give the Island 1 starting quest on a fresh character (no quests yet)
        if (newPlayer.getActiveQuests().isEmpty() && newPlayer.getCompletedQuests().isEmpty()) {
            Quest risingTide = QuestRegistry.getById("rising_tide");
            if (risingTide != null) newPlayer.addQuest(risingTide.createInstance());
        }

        if (!inDungeon) {
            currentMap = (currentTown != null)
                ? currentTown.getInteriorMap()
                : overworldManager.getCurrentMap();
        }

        if (gameKeyListener != null) removeKeyListener(gameKeyListener);
        // Loading mid-game calls this again — drop the old panels or they stay stacked
        // on the content pane, painting over and swallowing clicks meant for the new ones.
        if (gamePanel  != null) getContentPane().remove(gamePanel);
        if (statsPanel != null) { getContentPane().remove(statsPanel); statsPanel.dispose(); }
        if (messageLog != null) { getContentPane().remove(messageLog); messageLog.dispose(); }

        gamePanel  = new GamePanel(this);
        statsPanel = new StatsPanel(this);
        messageLog = new MessageLog();

        setLayout(new BorderLayout());
        add(gamePanel,  BorderLayout.CENTER);
        add(statsPanel, BorderLayout.EAST);
        add(messageLog, BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(null);

        gameKeyListener = new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) { handleKey(e); }
        };
        addKeyListener(gameKeyListener);

        updateCamera();
        setFocusable(true);
        setFocusTraversalKeysEnabled(false);
        requestFocusInWindow();
        setVisible(!recordingOffscreen);
        statsPanel.refresh();

        navController.applyPersistedMutations();

        if (isNewGame) {
            gamePanel.startWashAshoreAnimation();
            startOverlayRepaintTimer();
        }
    }

    /** Called by {@link WashAshoreAnimation} when the intro sequence completes. */
    public void finishWashAshore() {
        log("You wash ashore, salt in your wounds.", MessageLog.Type.INFO);
        log("A strange scar burns on your chest — it glows faintly when you look at the stars.", MessageLog.Type.INFO);
        log("Something is very wrong with the world. And it knows your name.", MessageLog.Type.DANGER);
        log("As your eyes opened, a figure stood over you — radiant, clothed in tidal light.", MessageLog.Type.SYSTEM);
        log("Her voice was the sound of the deep sea: 'The island is under siege. You were not washed here by chance.'", MessageLog.Type.SYSTEM);
        log("'Prove yourself worthy, wanderer. Rid these shores of the Corrupted Crab swarms.'", MessageLog.Type.SYSTEM);
        log("QUEST RECEIVED: The Rising Tide — Kill 5 Corrupted Crabs on the beaches of Lirandel.", MessageLog.Type.GOOD);
        if (gamePanel != null) gamePanel.repaint();
        if (statsPanel != null) statsPanel.refresh();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Endgame orchestration
    // ─────────────────────────────────────────────────────────────────────────

    /** Called by EndgameCinematicAnimation when the approach cinematic finishes. */
    public void presentCradleChoice() {
        if (gamePanel != null) gamePanel.openCradleChoice();
    }

    /** Called by CreditsAnimation when credits finish or are skipped. */
    public void returnToTitle() {
        restart();
    }

    /**
     * Closes this window and opens a fresh title screen without taking the JVM with it.
     * The replacement is built on a later EDT pass so this window's queued
     * {@code windowClosed} event is handled (and skipped) first.
     */
    public void restart() {
        restarting = true;
        if (overlayRepaintTimer != null) { overlayRepaintTimer.stop(); overlayRepaintTimer = null; }
        dispose();
        SwingUtilities.invokeLater(() -> {
            // The Retroquest constructor opens the start menu itself.
            try { new Retroquest(); } finally { restarting = false; }
        });
    }

    public DungeonController getDungeonController() { return dungeonController; }

    // ─────────────────────────────────────────────────────────────────────────
    // Input Dispatch
    // ─────────────────────────────────────────────────────────────────────────

    private void handleKey(KeyEvent e) {
        // Fullscreen works everywhere, ahead of every overlay — being unable to leave it from
        // inside a shop would be a trap.
        if (e.getKeyCode() == KeyEvent.VK_F11) { toggleFullscreen(); return; }

        // Audio controls work everywhere, including inside menus and cinematics
        if (e.getKeyCode() == KeyEvent.VK_M && e.isControlDown()) {
            boolean muted = SoundManager.getInstance().toggleMute();
            log(muted ? "Sound muted." : "Sound on.", MessageLog.Type.SYSTEM);
            return;
        }
        if (e.getKeyCode() == KeyEvent.VK_OPEN_BRACKET || e.getKeyCode() == KeyEvent.VK_CLOSE_BRACKET) {
            float v = SoundManager.getInstance().adjustMasterVolume(
                    e.getKeyCode() == KeyEvent.VK_CLOSE_BRACKET ? 0.1f : -0.1f);
            log("Volume " + Math.round(v * 100) + "%.", MessageLog.Type.SYSTEM);
            return;
        }

        // Overlay priority (highest → lowest)
        // Endgame animations (full-screen, block all input)
        if (gamePanel != null && gamePanel.isCreditsActive()) {
            gamePanel.skipCredits();
            return;
        }
        if (gamePanel != null && gamePanel.isEpilogueActive()) {
            if (gamePanel.isEpilogueWaitingForKey()) gamePanel.advanceEpilogue();
            return;
        }
        if (gamePanel != null && gamePanel.isEndgameCinematicActive()) {
            if (gamePanel.isEndgameCinematicWaitingForKey()) gamePanel.advanceEndgameCinematic();
            return;
        }
        if (gamePanel != null && gamePanel.isCradleChoiceActive()) {
            gamePanel.handleCradleChoiceKey(e);
            return;
        }
        if (gamePanel != null && gamePanel.isWashAshoreAnimationActive()) {
            // Any key advances, not just one pressed after the scene reaches its hold —
            // pressing during the first seconds used to be silently ignored.
            gamePanel.advanceWashAshore();
            return;
        }
        if (gamePanel != null && gamePanel.isDivineAudienceActive()) { gamePanel.handleDivineAudienceKey(e); return; }
        if (gamePanel != null && gamePanel.isDialogueActive())     { gamePanel.handleDialogueKey(e);   return; }
        if (gamePanel != null && gamePanel.isQuestLogActive())     { gamePanel.handleQuestLogKey(e);   return; }
        if ((gamePanel != null && gamePanel.isDeathAnimationActive()) || (gamePanel != null && gamePanel.isTownEntryAnimationActive())) return;
        if (gamePanel != null && gamePanel.isTownExitAnimationActive()) return;
        // Full-screen transition cinematics block all input for their whole run — otherwise the
        // player walks, fights and triggers encounters blind behind the animation.
        if (showingTeleportAnimation) return;   // teleport, shadow descent, war march, ascension
        // A monster is stepping out of the dark and its combat screen is 420ms away. Acting
        // now opens a chest or a stairway question that the fight then lands on top of,
        // leaving a battle the player cannot answer because something underneath owns the
        // keys. Nothing may be started in that gap.
        if (inDungeon && dungeonController != null && dungeonController.isEncounterPending()) return;
        if (gamePanel != null && (gamePanel.isShowingDungeonEntryAnimation()
                               || gamePanel.isShowingDungeonDescentAnimation()
                               || gamePanel.isShowingDungeonExitAnimation())) return;
        if (gamePanel != null && gamePanel.isDeathOverlayActive()) { gamePanel.handleDeathKey(e);      return; }
        // A prompt normally owns the keyboard, but it must not do so from behind a fight.
        // Stepping onto an island portal raises "step through?" and the same step can spawn
        // a wandering monster; the combat screen then covers the message log while the
        // invisible prompt eats every key, and the fight looks frozen. While a fight is
        // genuinely in progress the fight gets the keys; the prompt is still waiting when it
        // ends (a boss’s parting choice is raised on a finished overlay and still works).
        boolean midFight = gamePanel != null && gamePanel.isCombatActive()
                        && gamePanel.getCombatOverlay() != null
                        && !gamePanel.getCombatOverlay().isFinished();
        if (!midFight && messageLog != null && messageLog.isPromptActive())
                                                                   { messageLog.handleKey(e);          return; }
        if (gamePanel != null && gamePanel.isSpellbookActive())    { gamePanel.handleSpellbookKey(e);  return; }
        if (gamePanel != null && gamePanel.isCombatActive())       { gamePanel.handleCombatKey(e);     return; }
        if (gamePanel != null && gamePanel.isSaveLoadActive())     { gamePanel.handleSaveLoadKey(e);   return; }
        if (gamePanel != null && gamePanel.isInventoryActive())    { gamePanel.handleInventoryKey(e);  return; }
        if (gamePanel != null && gamePanel.isShopActive())         { gamePanel.handleShopKey(e);        return; }
        if (gamePanel != null && gamePanel.isCasinoActive())      { gamePanel.handleCasinoKey(e);      return; }
        if (gamePanel != null && gamePanel.isArenaActive())       { gamePanel.handleArenaKey(e);       return; }
        if (gamePanel != null && gamePanel.isDepthsActive())     { gamePanel.handleDepthsKey(e);      return; }
        if (gamePanel != null && gamePanel.isSkyGamesActive())      { gamePanel.handleSkyGamesKey(e);    return; }
        if (gamePanel != null && gamePanel.isNatureGamesActive())  { gamePanel.handleNatureGamesKey(e); return; }
        if (gamePanel != null && gamePanel.isMemoryGamesActive())  { gamePanel.handleMemoryGamesKey(e); return; }
        if (gamePanel != null && gamePanel.isWarGamesActive())     { gamePanel.handleWarGamesKey(e);    return; }
        if (gamePanel != null && gamePanel.isHelpActive())        { gamePanel.handleHelpKey(e);         return; }

        // ── First-person dungeon input (wireframe or textured) ──
        if (inDungeon && dungeonViewState.isFirstPerson()) {
            handleWireframeKey(e);
            return;
        }

        int dx = 0, dy = 0;

        switch (e.getKeyCode()) {
            case KeyEvent.VK_UP,    KeyEvent.VK_K -> dy = -1;
            case KeyEvent.VK_DOWN,  KeyEvent.VK_J -> dy =  1;
            case KeyEvent.VK_LEFT,  KeyEvent.VK_H -> dx = -1;
            case KeyEvent.VK_RIGHT                -> dx =  1;

            case KeyEvent.VK_F1 -> { gamePanel.openHelp(); startOverlayRepaintTimer(); return; }
            case KeyEvent.VK_A -> { attack(); return; }
            case KeyEvent.VK_C -> {
                if (currentTown != null) { log("You cannot cast spells while inside town.", MessageLog.Type.DANGER); return; }
                gamePanel.openSpellbook();
                startOverlayRepaintTimer();
                return;
            }
            case KeyEvent.VK_T -> { npcController.talk(); return; }
            case KeyEvent.VK_I -> { gamePanel.openInventory(); startOverlayRepaintTimer(); return; }
            case KeyEvent.VK_E -> {
                char tile = currentMap[player.getY()][player.getX()];
                if      (tile == 'D' && !inDungeon) {
                    // Check for authored dungeon name (and an optional key gate) in tile state.
                    // Four town maps have their own 'D' entrances, so the states must come from
                    // the map the player is standing on, not always the overworld.
                    String key = player.getX() + "," + player.getY();
                    String dName = null, requiredKey = null, lockedMsg = null;
                    MapData entranceMap = (currentTown != null)
                            ? currentTown.getMapData()
                            : (overworldManager != null ? overworldManager.getMapData() : null);
                    if (entranceMap != null && entranceMap.initialTileStates != null) {
                        var ts = entranceMap.initialTileStates.get(key);
                        if (ts != null && ts.data != null) {
                            dName       = ts.data.get("dungeonName");
                            requiredKey = ts.data.get("requiredKeyId");
                            lockedMsg   = ts.data.get("lockedMessage");
                        }
                    }
                    if (requiredKey != null && !requiredKey.isEmpty() && !player.hasKey(requiredKey)) {
                        log(lockedMsg != null && !lockedMsg.isEmpty() ? lockedMsg
                                : "The way is sealed. You lack the key.", MessageLog.Type.DANGER);
                        return;
                    }
                    if (dName != null) dungeonController.enterAuthoredDungeon(dName);
                    else dungeonController.enterDungeon();
                }
                else if (tile == 'D' &&  inDungeon)      dungeonController.exitDungeon();
                // Leaving a town. Walking off the edge still works; this makes the way out
                // discoverable, and reachable at all in the towns whose border has no walkable
                // tile. It sits after the 'D' branches (a town dungeon entrance can be right
                // next to the gate) and before 'E', whose town-entrance lookup is meaningless
                // once the player is already inside.
                else if (currentTown != null && navController.tryExitTownByDoor()) { /* leaving */ }
                else if (tile == 'E')                    navController.enterTownAtPlayerPosition();
                else if (tile == 'g')                    dungeonController.handleDungeonSpecial();
                else if (tile == 's')                    dungeonController.handleStairs();
                return;
            }
            case KeyEvent.VK_S -> { saveGame(); return; }
            case KeyEvent.VK_EQUALS, KeyEvent.VK_ADD      -> { zoomIn();  return; }
            case KeyEvent.VK_MINUS,  KeyEvent.VK_SUBTRACT -> { zoomOut(); return; }
            case KeyEvent.VK_L -> { gamePanel.openQuestLog(); startOverlayRepaintTimer(); return; }
            case KeyEvent.VK_Q -> {
                if (messageLog.isPromptActive()) return;
                messageLog.prompt("Quit Retroquest?",
                    new String[]{"Save & Quit", "Quit Now", "Cancel"},
                    new Runnable[]{() -> saveGameAndQuit(), () -> System.exit(0), null});
                return;
            }
        }

        // Drunk stumble: 50% chance to go in a random direction
        if ((dx != 0 || dy != 0) && player.isDrunk()) {
            if (ThreadLocalRandom.current().nextBoolean()) {
                int[][] dirs = {{0,-1},{0,1},{-1,0},{1,0}};
                int[] pick = dirs[ThreadLocalRandom.current().nextInt(4)];
                dx = pick[0]; dy = pick[1];
                String[] msgs = {
                    "You stumble sideways...", "The ground seems uneven...",
                    "Was that wall always there?", "You lurch unsteadily...",
                    "Hic! Wrong way...", "Everything's spinning!",
                    "You trip over your own feet...", "Which way was north again?"
                };
                log(msgs[ThreadLocalRandom.current().nextInt(msgs.length)], MessageLog.Type.DIM);
            }
        }

        if (dx != 0 || dy != 0) {
            handleMovement(dx, dy);
        }

        updateCamera();
        gamePanel.repaint();
        statsPanel.refresh();
    }

    private void handleMovement(int dx, int dy) {
        long now = System.currentTimeMillis();
        if (now - lastMoveTime < MOVE_DELAY) return;

        boolean allowed = false;
        if (inDungeon) {
            int cx = player.getX() + 1;
            int cy = player.getY() + 1;
            if      (dx == -1) allowed = Dungeon.canMoveWest (cx, cy, currentDepth);
            else if (dx ==  1) allowed = Dungeon.canMoveEast (cx, cy, currentDepth);
            else if (dy == -1) allowed = Dungeon.canMoveNorth(cx, cy, currentDepth);
            else if (dy ==  1) allowed = Dungeon.canMoveSouth(cx, cy, currentDepth);
        } else {
            int newX = player.getX() + dx;
            int newY = player.getY() + dy;
            if (newX < 0 || newX >= currentMap[0].length || newY < 0 || newY >= currentMap.length) {
                if (currentTown != null) {
                    navController.exitTown();
                }
                return;
            }
            char destTile = currentMap[newY][newX];
            if (isWalkable(destTile) && !isNpcAt(newX, newY)) {
                allowed = true;
            } else if (!isNpcAt(newX, newY) && navController.tryUnlockTile(newX, newY)) {
                allowed = true;
            }
        }

        if (allowed && player.move(dx, dy, currentMap)) {
            SoundManager.getInstance().play("step");
            lastMoveTime = now;

            // Spell buffs are spent by walking, and used to expire in complete silence.
            if (gamePanel != null) {
                gamePanel.getNotificationOverlay().pollBuffExpiry(player);
                if (gamePanel.isNotificationActive()) startOverlayRepaintTimer();
            }

            stepsSinceAutoSave++;
            if (stepsSinceAutoSave >= AUTO_SAVE_INTERVAL) {
                autoSave();
                stepsSinceAutoSave = 0;
            }

            navController.triggerTileEffect();

            if (inDungeon) {
                dungeonController.revealVisibleArea();
                dungeonController.handleDungeonSpecial();
                dungeonController.checkGroundLoot();
                dungeonController.checkDungeonEncounter();
            }
            if (player.isPoisoned()) {
                int dmg = 1 + (int)(Math.random() * 3);
                player.takeDamage(dmg);
                log("Poison courses through you! (-" + dmg + " HP, " + player.poisonStepsLeft() + " steps left)", MessageLog.Type.DANGER);
                statsPanel.refresh();
                if (player.isDead()) { dieOfPoison(); return; }
            }
            if (player.getFood() <= 0) {
                player.takeDamage(1);
                log("You are starving! Find food or rest at an inn.", MessageLog.Type.DANGER);
                if (player.isDead()) { dieOfStarvation(); return; }
            }
            if (!inDungeon && currentTown == null) {
                encounterController.checkForRandomEncounter();
            }
        } else {
            SoundManager.getInstance().play("bump");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // First-Person Wireframe Dungeon Input
    // ─────────────────────────────────────────────────────────────────────────

    /** Step offsets for each facing direction: NORTH, EAST, SOUTH, WEST. */
    private static final int[] WF_DX = { 0, 1, 0, -1};
    private static final int[] WF_DY = {-1, 0, 1,  0};

    private void handleWireframeKey(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_UP    -> wireframeMove(1);   // forward
            case KeyEvent.VK_DOWN  -> wireframeMove(-1);  // backward
            case KeyEvent.VK_LEFT  -> { player.turnLeft();  gamePanel.repaint(); }
            case KeyEvent.VK_RIGHT -> { player.turnRight(); gamePanel.repaint(); }
            case KeyEvent.VK_A -> { attack(); }
            case KeyEvent.VK_S -> { saveGame(); }
            case KeyEvent.VK_M -> { dungeonViewState.toggleMinimap(); gamePanel.repaint(); }
            case KeyEvent.VK_V -> {
                boolean on = dungeonViewState.getCamera().toggleSmooth();
                log(on ? "Smooth view motion on." : "Smooth view motion off — the view snaps.",
                    MessageLog.Type.SYSTEM);
                gamePanel.repaint();
            }
            // Pass through to existing handlers for overlays and specials
            case KeyEvent.VK_E -> {
                char tile = currentMap[player.getY()][player.getX()];
                if (tile == 's') dungeonController.handleStairs();
                else if (tile == 'D') dungeonController.exitDungeon();
                else dungeonController.handleDungeonSpecial();
            }
            case KeyEvent.VK_T -> { npcController.talk(); }
            case KeyEvent.VK_C -> {
                gamePanel.openSpellbook();
                startOverlayRepaintTimer();
            }
            case KeyEvent.VK_I -> {
                gamePanel.openInventory();
                startOverlayRepaintTimer();
            }
            case KeyEvent.VK_O, KeyEvent.VK_L -> {
                gamePanel.openQuestLog();
                startOverlayRepaintTimer();
            }
            case KeyEvent.VK_Q -> {
                if (messageLog.isPromptActive()) return;
                messageLog.prompt("Quit Retroquest?",
                    new String[]{"Save & Quit", "Quit Now", "Cancel"},
                    new Runnable[]{() -> saveGameAndQuit(), () -> System.exit(0), null});
            }
            case KeyEvent.VK_F1 -> {
                gamePanel.openHelp();
                startOverlayRepaintTimer();
            }
        }
    }

    private void wireframeMove(int direction) {
        long now = System.currentTimeMillis();
        if (now - lastMoveTime < MOVE_DELAY) return;
        lastMoveTime = now;

        int facing = player.getFacing();
        int moveFacing = (direction == 1) ? facing : (facing + 2) & 3; // forward or backward

        int cx = player.getX() + 1; // 1-based for Dungeon API
        int cy = player.getY() + 1;

        // Validate movement — authored maps use tile walkability, procedural uses Dungeon wall checks
        boolean authored = dungeonViewState.isAuthored();
        int qx = authored ? player.getX() : cx;
        int qy = authored ? player.getY() : cy;
        boolean allowed = DungeonWallQuery.canMove(qx, qy, currentDepth, moveFacing, currentMap, authored);

        if (allowed) {
            int dx = WF_DX[moveFacing];
            int dy = WF_DY[moveFacing];
            int oldX = player.getX(), oldY = player.getY();
            player.setPosition(oldX + dx, oldY + dy);
            // setPosition is a bare move; everything a step on the surface does has to be
            // done by hand here, or nothing underground ever expires — buffs cast in a
            // dungeon lasted the whole dungeon, poison never ticked, and regeneration
            // rings did nothing on six of the seven islands.
            player.stepTaken();
            if (gamePanel != null) {
                gamePanel.getNotificationOverlay().pollBuffExpiry(player);
                if (gamePanel.isNotificationActive()) startOverlayRepaintTimer();
            }
            if (player.isPoisoned()) {
                int pdmg = 1 + (int)(Math.random() * 3);
                player.takeDamage(pdmg);
                log("Poison courses through you! (-" + pdmg + " HP, " + player.poisonStepsLeft()
                        + " steps left)", MessageLog.Type.DANGER);
                if (statsPanel != null) statsPanel.refresh();
                if (player.isDead()) { dieOfPoison(); return; }
            }
            if (player.getFood() <= 0) {
                player.takeDamage(1);
                log("You are starving! Find food or rest at an inn.", MessageLog.Type.DANGER);
                if (player.isDead()) { dieOfStarvation(); return; }
            }
            stepsSinceAutoSave++;
            if (stepsSinceAutoSave >= AUTO_SAVE_INTERVAL) {
                autoSave();
                stepsSinceAutoSave = 0;
            }
            if (dungeonViewState.isTextured()) {
                TexturedDungeonRenderer.startStepAnimation(oldX, oldY, player.getX(), player.getY());
            } else {
                WireframeDungeonRenderer.startStepAnimation(oldX, oldY, player.getX(), player.getY());
            }
            SoundManager.getInstance().play("step");
            updateCamera();
            dungeonController.revealVisibleArea();
            dungeonController.checkGroundLoot();
            // Encounters take priority over specials — if a monster appears, fight first
            dungeonController.checkDungeonEncounter();
            // Auto-trigger dungeon specials only if nothing else has claimed the screen.
            // isCombatActive() alone is not enough: a first-person encounter spends its
            // preview with no combat screen up yet, and a special raised in that gap ends
            // up buried under the fight that follows.
            if (gamePanel != null && !gamePanel.isCombatActive()
                    && !messageLog.isPromptActive()
                    && !dungeonController.isEncounterPending()) {
                dungeonController.handleDungeonSpecial();
            }
            if (gamePanel != null) gamePanel.repaint();
            if (statsPanel != null) statsPanel.refresh();
        } else {
            // Chutes ('c'), spinners ('n') and memory pools ('M') are authored on tile ids that
            // data/tiles.json defines as solid furniture, so they can never be stepped on —
            // walking into one triggers it instead of a plain bump.
            if (authored && !messageLog.isPromptActive()) {
                int tx = player.getX() + WF_DX[moveFacing];
                int ty = player.getY() + WF_DY[moveFacing];
                char bumped = DungeonWallQuery.getSpecial(tx, ty, currentDepth, currentMap, true);
                if (bumped == 'c' || bumped == 'n' || bumped == 'M') {
                    dungeonController.handleDungeonSpecialAt(tx, ty, bumped);
                    if (gamePanel  != null) gamePanel.repaint();
                    if (statsPanel != null) statsPanel.refresh();
                    return;
                }
            }
            SoundManager.getInstance().play("bump");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public Navigation Delegates (called by animation classes)
    // ─────────────────────────────────────────────────────────────────────────

    /** Called by {@link TownEntryAnimation} when its animation completes. */
    public void finishTownEntry() { navController.finishTownEntry(); }

    /** Called by {@link TownExitAnimation} when its animation completes. */
    public void finishTownExit()  { navController.finishTownExit();  }

    /** Called by {@link DungeonEntryAnimation} when its animation completes. */
    public void finishDungeonEntry() { dungeonController.finishDungeonEntry(); }

    /** Called by {@link DungeonExitAnimation} when its animation completes. */
    public void finishDungeonExit()  { dungeonController.finishDungeonExit();  }

    /** Called by ShadowDescentAnimation when its animation completes. */
    public void finishShadowDescent() { navController.finishTeleport(); }

    /** Called by WarMarchAnimation when its animation completes. */
    public void finishWarMarch() { navController.finishTeleport(); }

    /** Called by AscensionAnimation when its animation completes. */
    public void finishAscension() { navController.finishTeleport(); }

    public void switchOverworld(String mapName)       { navController.switchOverworld(mapName); }
    public void castMapSpell(Spell spell)             { npcController.castMapSpell(spell); }
    public boolean isShowingTeleportAnimation()       { return showingTeleportAnimation; }
    public String  getCurrentOverworldName()          { return currentOverworldName; }

    // ─────────────────────────────────────────────────────────────────────────
    // Zoom
    // ─────────────────────────────────────────────────────────────────────────

    public int getViewWidth()  { return viewW; }
    public int getViewHeight() { return viewH; }

    /** Zoom in: fewer tiles visible, each tile appears larger. */
    public void zoomIn() {
        if (viewW > VIEW_MIN) {
            viewW = Math.max(VIEW_MIN, viewW - VIEW_STEP);
            viewH = Math.max(VIEW_MIN, viewH - VIEW_STEP);
            updateCamera();
            gamePanel.repaint();
        }
    }

    /** Zoom out: more tiles visible, each tile appears smaller. */
    public void zoomOut() {
        if (viewW < VIEW_MAX) {
            viewW = Math.min(VIEW_MAX, viewW + VIEW_STEP);
            viewH = Math.min(VIEW_MAX, viewH + VIEW_STEP);
            updateCamera();
            gamePanel.repaint();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Camera
    // ─────────────────────────────────────────────────────────────────────────

    public void updateCamera() {
        cameraX = Math.max(0, Math.min(player.getX() - viewW / 2, currentMap[0].length - viewW));
        cameraY = Math.max(0, Math.min(player.getY() - viewH / 2, currentMap.length    - viewH));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Save / Load
    // ─────────────────────────────────────────────────────────────────────────

    /** Opens the save overlay (slot chooser). */
    public void saveGame() {
        if (gamePanel != null) gamePanel.openSaveDialog(false);
        startOverlayRepaintTimer();
    }

    /** Opens the save overlay and quits the application after a successful save. */
    public void saveGameAndQuit() {
        if (gamePanel != null) gamePanel.openSaveDialog(true);
        startOverlayRepaintTimer();
    }

    /**
     * Serialises current game state to the given save slot.
     * Called by {@link SaveLoadOverlay} once the player confirms a slot.
     */
    public boolean saveGame(int slot) {
        new File("saves").mkdirs();
        SaveData data = buildSaveData(slot);

        String filename = "saves/retroquest_save_" + String.format("%02d", slot) + ".sav";
        File target = new File(filename);
        File tmp    = new File(filename + ".tmp");
        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8)) {
            new Gson().toJson(data, writer);
        } catch (Exception e) {
            log("Save failed: " + e.getMessage(), MessageLog.Type.DANGER);
            tmp.delete();
            return false;
        }
        try {
            replaceFile(tmp, target);
        } catch (Exception e) {
            // The old save is still intact — say so instead of claiming success
            log("Save failed: could not replace slot " + slot + " (" + e.getMessage() + ")",
                MessageLog.Type.DANGER);
            tmp.delete();
            return false;
        }
        log("Game saved to slot " + slot + ".", MessageLog.Type.SYSTEM);
        return true;
    }

    /**
     * Builds the snapshot written to a save slot. Shared by {@link #saveGame(int)} and
     * {@link #autoSave()} so the two can never drift apart.
     */
    private SaveData buildSaveData(int slot) {
        SaveData data = new SaveData(
            player, currentTown, inDungeon, currentDepth, revealed, slot,
            overworldManager.getCurrentMapName(), townRevealed);
        // Only meaningful while actually inside an authored dungeon — writing it while on the
        // overworld would hand the next load a dungeon name it must not act on.
        data.setAuthoredDungeonName(inDungeon ? dungeonViewState.getAuthoredDungeonName() : null);
        // Always save dungeon entry coords so they survive save/load/re-entry cycles
        data.setDungeonEntry(dungeonController.getLastDungeonEntryX(),
                             dungeonController.getLastDungeonEntryY());
        data.setDungeonEntryTown(dungeonController.getEntryTownName(),
                                 dungeonController.getEntryTownDoorX(),
                                 dungeonController.getEntryTownDoorY());
        data.setLastSafeTownName(lastSafeTownName);
        data.setLearnedSpells(collectLearnedSpellNames());
        if (saveData != null && saveData.getTileStates() != null) {
            data.getOrCreateTileStates().putAll(saveData.getTileStates());
        }
        return data;
    }

    /**
     * Names of every permanently learned spell. Saved alongside the player's positional
     * {@code spellLearned[]} flags so that inserting or reordering a spell in the code-built
     * spell table cannot shift a flag onto a different spell.
     */
    private java.util.List<String> collectLearnedSpellNames() {
        java.util.List<String> names = new java.util.ArrayList<>();
        if (player == null) return names;
        for (Spell s : player.getKnownSpells()) {
            if (s != null && player.isSpellLearned(s.getName())) names.add(s.getName());
        }
        return names;
    }

    /** Silently writes an auto-save to slot 0 using temp-file-then-rename for safety. */
    private void autoSave() {
        try {
            new File("saves").mkdirs();
            SaveData data = buildSaveData(0);
            File target = new File("saves/retroquest_save_auto.sav");
            File tmp = new File("saves/retroquest_save_auto.sav.tmp");
            try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8)) {
                new Gson().toJson(data, writer);
            }
            if (gamePanel != null) {
                gamePanel.getNotificationOverlay().enqueueAutoSave();
                startOverlayRepaintTimer();
            }
            replaceFile(tmp, target);
        } catch (Exception ignored) {
            // Auto-save is best-effort; never interrupt gameplay
        }
    }

    /**
     * Registers a callback for when the save/load overlay is dismissed without
     * saving or loading. Used by the death screen so backing out of the slot
     * chooser returns there instead of resuming play with a dead player.
     * Passing {@code null} clears it.
     */
    public void setSaveLoadCancelHook(Runnable hook) { saveLoadCancelHook = hook; }

    /** Called by {@link SaveLoadOverlay} when the player backs out of the slot chooser. */
    public void saveLoadCancelled() {
        Runnable hook = saveLoadCancelHook;
        saveLoadCancelHook = null;
        if (hook != null) {
            hook.run();
            startOverlayRepaintTimer();
        }
    }

    /**
     * Replaces {@code target} with {@code tmp}, atomically where the filesystem allows it.
     *
     * <p>ATOMIC_MOVE is what makes a temp-file save actually safe: without it a crash or a
     * power cut between truncate and write leaves a half-written save. Windows can refuse it
     * across volumes or when a scanner holds the file, so a plain replacing move is the
     * documented fallback \u2014 still far better than writing over the live file in place.
     */
    private static void replaceFile(File tmp, File target) throws java.io.IOException {
        try {
            java.nio.file.Files.move(tmp.toPath(), target.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException | java.nio.file.AccessDeniedException e) {
            java.nio.file.Files.move(tmp.toPath(), target.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** Opens the load overlay (slot chooser). */
    public void loadGame() {
        if (gamePanel != null) {
            gamePanel.openLoadDialog();
            startOverlayRepaintTimer();
        } else {
            new SaveLoadDialog(this, false);
        }
    }

    /**
     * Deserialises a save slot and resumes the game from that state.
     * Called by {@link SaveLoadOverlay} once the player confirms a slot.
     */
    public void loadGame(int slot) {
        File file = (slot == 0)
            ? new File("saves/retroquest_save_auto.sav")
            : new File("saves/retroquest_save_" + String.format("%02d", slot) + ".sav");
        if (!file.exists()) {
            log("Save slot " + slot + " is empty.", MessageLog.Type.DANGER);
            return;
        }

        SaveData data;
        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            data = new Gson().fromJson(reader, SaveData.class);
        } catch (Exception e) {
            log("Load failed from slot " + slot + ": " + e.getMessage(), MessageLog.Type.DANGER);
            e.printStackTrace();
            return;
        }
        if (data == null) {
            log("Load failed from slot " + slot + ": file is empty or unreadable.", MessageLog.Type.DANGER);
            return;
        }
        if (data.getSaveVersion() > SaveData.CURRENT_VERSION) {
            log("Warning: slot " + slot + " was saved by a newer version (v" + data.getSaveVersion()
                    + "). Some data may not load correctly.", MessageLog.Type.DANGER);
        }
        Player loadedPlayer = data.getPlayer();
        if (loadedPlayer == null) {
            log("Load failed from slot " + slot + ": save contains no player data.", MessageLog.Type.DANGER);
            return;
        }

        // ── Build the new state in locals ────────────────────────────────────────────────
        // Nothing on the game is mutated until every piece has been built, so a save that
        // blows up half way through leaves the running game exactly as it was.
        boolean newInDungeon = data.isInDungeon();
        int     newDepth     = data.getCurrentDepth();
        String  newOverworld = (data.getCurrentOverworldName() != null)
                ? data.getCurrentOverworldName() : currentOverworldName;
        // An authored dungeon name only means anything while inside that dungeon
        String  newDungeonName = newInDungeon ? data.getAuthoredDungeonName() : null;
        Town     newTown = null;
        MapData  newDungeonMapData = null;
        char[][] newMap;

        try {
            loadedPlayer.restoreAfterLoad();
            loadedPlayer.restoreSpellLearning();
            applyLearnedSpellNames(loadedPlayer, data.getLearnedSpells());
            loadedPlayer.setPosition(data.getPlayerX(), data.getPlayerY());

            // Falls back to an in-memory map internally rather than throwing
            overworldManager.loadOverworld(newOverworld);

            if (data.getCurrentTownName() != null) {
                int doorX = data.getTownDoorX();
                int doorY = data.getTownDoorY();
                // Old saves lack door coords — look up from overworld TownEntrance data
                if (doorX == 0 && doorY == 0) {
                    for (TownEntrance te : overworldManager.getTownEntrances()) {
                        if (te.townName().equals(data.getCurrentTownName())) {
                            doorX = te.worldX();
                            doorY = te.worldY();
                            break;
                        }
                    }
                }
                newTown = new Town(data.getCurrentTownName(), doorX, doorY);
            }

            if (newInDungeon && newDungeonName != null) {
                // Authored dungeon — load the rfmap level
                File dungeonFile = new File("data/dungeons/" + newDungeonName + "_" + newDepth + ".rfmap");
                MapData md = null;
                if (dungeonFile.exists()) {
                    try {
                        md = MapData.load(dungeonFile);
                    } catch (Exception e) {
                        log("Dungeon level " + newDungeonName + "_" + newDepth + " would not load ("
                                + e.getMessage() + "); falling back to the procedural dungeon.",
                            MessageLog.Type.DANGER);
                    }
                }
                if (md != null) {
                    newMap = md.tiles;
                    newDungeonMapData = md;
                } else {
                    // Keeping the authored name here would run authored movement rules over a
                    // procedural map — a dungeon with no walls. Drop back to procedural wholesale.
                    newDungeonName = null;
                    newMap = Dungeon.generate(newDepth);
                }
            } else if (newInDungeon) {
                newMap = Dungeon.generate(newDepth);
            } else {
                newMap = (newTown != null) ? newTown.getInteriorMap() : overworldManager.getCurrentMap();
            }
        } catch (Exception e) {
            log("Load failed from slot " + slot + ": " + e.getMessage(), MessageLog.Type.DANGER);
            e.printStackTrace();
            return;
        }

        // ── Commit ───────────────────────────────────────────────────────────────────────
        this.inDungeon            = newInDungeon;
        this.currentDepth         = newDepth;
        this.currentOverworldName = newOverworld;
        this.currentTown          = newTown;
        this.currentMap           = newMap;
        this.currentDungeonMapData = newDungeonMapData;
        dungeonViewState.setAuthoredDungeonName(newDungeonName);

        // Always restore dungeon entry coords if present (needed for exit placement)
        if (data.getDungeonEntryX() != 0 || data.getDungeonEntryY() != 0) {
            dungeonController.setLastDungeonEntry(data.getDungeonEntryX(), data.getDungeonEntryY());
        }
        dungeonController.setEntryTown(data.getDungeonEntryTownName(),
                                       data.getDungeonEntryTownDoorX(),
                                       data.getDungeonEntryTownDoorY());
        // Per-slot, not per-session: without this the value leaks in from the previous game
        if (data.getLastSafeTownName() != null) lastSafeTownName = data.getLastSafeTownName();

        // Re-activate the right dungeon view if loading into a dungeon. This asks the same
        // island table the descent does — an independent copy here used to send procedural
        // dungeons on Islands 5-7 back to the top-down grid on load.
        if (inDungeon) {
            var mode = io.cannonforge.retroquest.controller.DungeonController
                    .modeForIsland(currentOverworldName);
            // Authored dungeons are never drawn top-down, exactly as on entry.
            if (newDungeonName != null
                    && mode == io.cannonforge.retroquest.model.DungeonViewState.Mode.TOP_DOWN) {
                mode = io.cannonforge.retroquest.model.DungeonViewState.Mode.WIREFRAME;
            }
            dungeonViewState.setMode(mode);
        } else {
            dungeonViewState.setMode(io.cannonforge.retroquest.model.DungeonViewState.Mode.TOP_DOWN);
        }

        // Fog of war belongs to the slot — drop whatever the previous game revealed
        revealed.clear();
        townRevealed.clear();
        TileStateManager.migrateFromPlayerFlags(data);
        TileStateManager.migrateLegacyDungeonKeys(data, data.legacyDungeonId());
        // Must be installed before startGame(), which re-applies tile overrides — otherwise
        // it would stamp the PREVIOUS save's overrides onto the map we just loaded.
        this.saveData = data;
        data.restore(this);

        SoundManager.getInstance().fadeOutTitleTheme(400);
        startGame(loadedPlayer);

        updateCamera();
        // Loading straight into a dungeon has to start the view loop the same way walking in does,
        // or the first-person view sits frozen until the player takes a step.
        if (inDungeon && dungeonViewState.isFirstPerson()) {
            snapDungeonCamera();
            startDungeonViewLoop();
        }
        if (gamePanel  != null) gamePanel.repaint();
        if (statsPanel != null) statsPanel.refresh();

        log("Game loaded from slot " + slot + ". Overworld: " + overworldManager.getCurrentMapName()
            + " (save v" + data.getSaveVersion() + ")", MessageLog.Type.SYSTEM);

        String buffSummary = loadedPlayer.getActiveBuffSummary();
        if (buffSummary != null) {
            log("Active buffs: " + buffSummary, MessageLog.Type.GOOD);
        }

        stepsSinceAutoSave = 0;
    }

    /**
     * Re-learns spells by name after a load. Names are authoritative when present because the
     * player's positional {@code spellLearned[]} indexes into a spell table rebuilt from code.
     *
     * <p>Old saves carry no name list; their positional flags are left as they are.
     */
    private void applyLearnedSpellNames(Player p, java.util.List<String> names) {
        if (p == null || names == null || names.isEmpty()) return;
        // Replace rather than add, so a flag that landed on the wrong spell in an older
        // index-based save is cleared instead of being kept forever.
        p.setLearnedSpellNames(names);
    }



    // ─────────────────────────────────────────────────────────────────────────
    // Death / Game Over
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Triggers the death sequence. If a death animation is already playing (e.g. from
     * combat) the overlay is shown immediately; otherwise the animation is started first.
     */
    public void gameOver(String deathReason) {
        if (gamePanel != null) {
            if (gamePanel.isDeathAnimationActive()) {
                gamePanel.showDeathOverlay(deathReason);
                startOverlayRepaintTimer();
                return;
            }
            if (overlayRepaintTimer != null) { overlayRepaintTimer.stop(); overlayRepaintTimer = null; }
            gamePanel.startDeathAnimation(deathReason);
        } else {
            dispose();
        }
    }

    /** Called by {@link PlayerDeathAnimation} when its animation finishes. */
    public void showDeathScreen(String deathReason) {
        if (gamePanel != null) {
            if (overlayRepaintTimer != null) { overlayRepaintTimer.stop(); overlayRepaintTimer = null; }
            gamePanel.showDeathOverlay(deathReason);
            startOverlayRepaintTimer();
        }
    }

    /**
     * The single death path for everything outside combat — pits, chutes, traps, geysers,
     * poison, starvation, altar wrath and the rest.
     *
     * <p>Consumes the Resurrection Ward first, exactly as {@code CombatEngine} does for a
     * death in a fight: before this existed the ward the Help text promises silently did
     * nothing anywhere else. Returns {@code true} if the player actually died.
     */
    public boolean killPlayer(String deathReason) {
        if (player != null && player.triggerResurrection()) {
            SoundManager.getInstance().play("heal");
            log("RESURRECTION WARD ACTIVATES!", MessageLog.Type.SYSTEM);
            log("You rise with " + player.getHp() + " HP!", MessageLog.Type.GOOD);
            if (statsPanel != null) statsPanel.refresh();
            if (gamePanel  != null) gamePanel.repaint();
            return false;
        }
        gameOver(deathReason);
        return true;
    }

    private void dieOfStarvation() { killPlayer("You have starved to death..."); }

    private void dieOfPoison() { killPlayer("The poison finishes its work..."); }

    // ─────────────────────────────────────────────────────────────────────────
    // Logging & Overlay Repaint
    // ─────────────────────────────────────────────────────────────────────────

    /** Appends a plain message to the on-screen message log. */
    public void log(String text) {
        if (messageLog != null) messageLog.log(text);
    }

    /** Appends a colour-coded message to the on-screen message log. */
    public void log(String text, MessageLog.Type type) {
        if (messageLog != null) messageLog.log(text, type);
    }

    /** Logs level-up fanfare to the message log, plays sound, and triggers screen flash. */
    public void logLevelUp(io.cannonforge.retroquest.model.Player.LevelUpResult lvl) {
        if (lvl == null) return;
        SoundManager.getInstance().play("levelup");
        if (gamePanel != null) gamePanel.triggerLevelUpFlash();
        // Keep this to three lines: the message log shows four, so the old six-line
        // banner with rules top and bottom wiped out everything the player had just read.
        log("★ LEVEL UP!  You are now level " + lvl.newLevel() + "!", MessageLog.Type.LOOT);
        log("  Max HP +" + lvl.totalHpGained() + "  (→ " + getPlayer().getMaxHp() + ")  —  HP fully restored!", MessageLog.Type.GOOD);
        if (lvl.unlockedNewSpellTier())
            log("  ✦ New spell tier unlocked: Level " + lvl.newMaxSpellLevel() + " spells!", MessageLog.Type.SYSTEM);
    }

    /** Exposes the message log so {@link CombatOverlay} and other panels can push messages. */
    public MessageLog getMessageLog() { return messageLog; }

    /**
     * Starts (or restarts) a 60 fps repaint timer that keeps running while any
     * overlay or animation is active, then stops automatically when all are idle.
     */
    /**
     * Frame interval while an overlay is up.
     *
     * <p>Every tick repaints the whole panel — the world, then the overlay on top of it,
     * because the combat card is a translucent scrim rather than an opaque screen. At the
     * old 16ms a thread dump taken during an ordinary fight showed the event thread
     * spending two thirds of its time inside {@code CombatOverlay.paint}, which on a laptop
     * is a spun-up fan for a static menu. Every animation here is driven by wall-clock time
     * rather than frame count, so a slower cadence draws fewer frames of the same motion:
     * the card still slides, the typewriter still runs at its 40 characters a second, and
     * the cost halves.
     */
    private static final int OVERLAY_FRAME_MS = 33;   // ~30fps

    /** Frame interval for the first-person dungeon loop. Fast enough for a 130ms camera ease. */
    private static final int DUNGEON_FRAME_MS = 30;

    private Timer dungeonViewTimer;
    private long lastDungeonTick;

    /**
     * Runs the first-person dungeon view.
     *
     * <p>Nothing used to repaint a dungeon while the player stood still — the overlay timer only
     * runs while an overlay or animation is up — so every animated thing down there was frozen
     * until you moved: Rootvault's spores, Island 5's sconce flicker, all of it. This loop is what
     * makes them move, and it is what advances {@link DungeonCamera} so Island 6 can ease through
     * turns and steps instead of snapping.
     *
     * <p>Self-stopping: one tick outside a first-person dungeon shuts it down, so no caller has to
     * remember to stop it on every exit path.
     */
    public void startDungeonViewLoop() {
        if (dungeonViewTimer != null && dungeonViewTimer.isRunning()) return;
        lastDungeonTick = System.currentTimeMillis();
        dungeonViewTimer = new Timer(DUNGEON_FRAME_MS, e -> {
            if (!inDungeon || gamePanel == null || !dungeonViewState.isFirstPerson()) {
                stopDungeonViewLoop();
                return;
            }
            long now = System.currentTimeMillis();
            long dt = Math.min(120, Math.max(1, now - lastDungeonTick));
            lastDungeonTick = now;

            // The camera chases the player rather than being pushed by every move site. Targets
            // are idempotent, so this picks up steps, turns, teleports and level changes alike —
            // and DungeonCamera snaps by itself when the jump is too far to have been a walk.
            var cam = dungeonViewState.getCamera();
            cam.moveTo(player.getX(), player.getY());
            cam.turnTo(player.getFacing());
            cam.update(dt);

            gamePanel.repaint();
        });
        dungeonViewTimer.start();
    }

    public void stopDungeonViewLoop() {
        if (dungeonViewTimer != null) { dungeonViewTimer.stop(); dungeonViewTimer = null; }
    }

    private boolean fullscreen = false;
    private boolean togglingFullscreen = false;

    /**
     * Toggles borderless fullscreen, bound to F11.
     *
     * <p>The UI scale cannot change at runtime — panels bake their sizes from
     * {@link DisplayScale#SCALE} into {@code static final} fields at class-init — so this does not
     * rescale anything. It does not have to: {@code GamePanel} is the {@code BorderLayout.CENTER}
     * component, so a maximised window simply hands it more room. The first-person renderers take
     * the panel's width and height and project from them, so they genuinely draw more; the tile
     * grid stays square and centres itself in whatever it is given.
     *
     * <p>Maximised-and-undecorated rather than {@code GraphicsDevice.setFullScreenWindow}: the
     * exclusive mode changes the display mode and behaves badly across multiple monitors, and this
     * is indistinguishable for a windowed 2D game.
     */
    public void toggleFullscreen() {
        fullscreen = !fullscreen;
        togglingFullscreen = true;
        try {
            dispose();                       // required before the decoration can change
            setUndecorated(fullscreen);
            setResizable(fullscreen);        // the OS needs this to maximise us
            setExtendedState(fullscreen ? java.awt.Frame.MAXIMIZED_BOTH : java.awt.Frame.NORMAL);
            if (!fullscreen) {
                pack();
                setLocationRelativeTo(null);
            }
            setVisible(true);
        } finally {
            // Cleared after the queue drains, not here: the windowClosed from dispose() is still
            // in flight. Belt and braces alongside the isDisplayable check in the listener.
            SwingUtilities.invokeLater(() -> togglingFullscreen = false);
        }
        requestFocusInWindow();
        if (gamePanel != null) gamePanel.repaint();
        log(fullscreen ? "Fullscreen on (F11 to exit)." : "Fullscreen off.", MessageLog.Type.SYSTEM);
    }

    /** Puts the camera exactly where the player is, with no easing. For entries and level changes. */
    public void snapDungeonCamera() {
        dungeonViewState.getCamera().snapTo(player.getX(), player.getY(), player.getFacing());
    }

    public void startOverlayRepaintTimer() {
        if (overlayRepaintTimer != null) { overlayRepaintTimer.stop(); overlayRepaintTimer = null; }
        overlayRepaintTimer = new Timer(OVERLAY_FRAME_MS, null);
        overlayRepaintTimer.addActionListener(e -> {
            if (gamePanel == null) { overlayRepaintTimer.stop(); return; }
            gamePanel.repaint();
            boolean anyActive =
                gamePanel.isInventoryActive()    ||
                gamePanel.isShopActive()          ||
                gamePanel.isCasinoActive()        ||
                gamePanel.isArenaActive()         ||
                gamePanel.isDepthsActive()        ||
                gamePanel.isSkyGamesActive()     ||
                gamePanel.isNatureGamesActive()  ||
                gamePanel.isMemoryGamesActive()  ||
                gamePanel.isWarGamesActive()     ||
                gamePanel.isCradleChoiceActive() ||
                gamePanel.isEndgameCinematicActive() ||
                gamePanel.isEpilogueActive()     ||
                gamePanel.isCreditsActive()      ||
                gamePanel.isWashAshoreAnimationActive() ||
                gamePanel.isShowingDungeonEntryAnimation()   ||
                gamePanel.isShowingDungeonDescentAnimation() ||
                gamePanel.isShowingDungeonExitAnimation()    ||
                gamePanel.isShowingShadowDescentAnimation()  ||
                gamePanel.isShowingWarMarchAnimation()       ||
                gamePanel.isShowingAscensionAnimation()      ||
                gamePanel.isSpellbookActive()    ||
                gamePanel.isCombatActive()        ||
                gamePanel.isDeathOverlayActive()  ||
                gamePanel.isSaveLoadActive()       ||
                gamePanel.isDivineAudienceActive()  ||
                gamePanel.isDialogueActive()       ||
                gamePanel.isQuestLogActive()       ||
                gamePanel.isHelpActive()           ||
                gamePanel.isTownEntryAnimationActive() ||
                gamePanel.isTownExitAnimationActive()  ||
                gamePanel.isDeathAnimationActive()     ||
                gamePanel.isNotificationActive();
            if (!anyActive) { overlayRepaintTimer.stop(); overlayRepaintTimer = null; }
        });
        overlayRepaintTimer.start();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Spellbook (combat mode)
    // ─────────────────────────────────────────────────────────────────────────

    /** Opens the spellbook in combat mode; result is routed through the given {@link CombatOverlay}. */
    public void openSpellbook(CombatOverlay overlay) {
        if (gamePanel != null) {
            gamePanel.openSpellbookForCombat(overlay);
            startOverlayRepaintTimer();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Combat
    // ─────────────────────────────────────────────────────────────────────────

    void attack() {
        log("Nothing to attack right now. Keep walking to trigger encounters.", MessageLog.Type.DIM);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Utility
    // ─────────────────────────────────────────────────────────────────────────

    public boolean isWalkable(char tile) {
        return TileRegistry.getByIdSafe(tile).isWalkable();
    }

    /**
     * Returns {@code true} if any NPC occupies the given tile coordinate.
     *
     * <p>Only the NPCs of the map the player is actually standing on are considered — an
     * overworld NPC standing at (12,9) must not block (12,9) of a town interior or a
     * dungeon level, which produced invisible collisions.
     */
    public boolean isNpcAt(int x, int y) {
        if (inDungeon) {
            if (currentDungeonMapData != null && currentDungeonMapData.npcs != null) {
                for (NPC npc : currentDungeonMapData.npcs) {
                    if (npc.getX() == x && npc.getY() == y) return true;
                }
            }
            return false;
        }
        if (currentTown != null) {
            for (NPC npc : currentTown.getNpcs()) {
                if (npc.getX() == x && npc.getY() == y) return true;
            }
            return false;
        }
        for (NPC npc : overworldManager.getNpcs()) {
            if (npc.getX() == x && npc.getY() == y) return true;
        }
        return false;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Getters (used by GamePanel, StatsPanel, overlays, and controllers)
    // ─────────────────────────────────────────────────────────────────────────

    public char[][]         getCurrentMap()           { return currentMap; }
    public void             setCurrentMap(char[][] m) { this.currentMap = m; }
    public Player           getPlayer()               { return player; }
    public int              getCameraX()              { return cameraX; }
    public int              getCameraY()              { return cameraY; }
    public StatsPanel       getStatsPanel()           { return statsPanel; }
    public Map<String, boolean[][]> getRevealed()     { return revealed; }
    public Map<String, boolean[][]> getTownRevealed() { return townRevealed; }
    public Town      getCurrentTown()          { return currentTown; }
    public void      setCurrentTown(Town t)    { this.currentTown = t; }
    public boolean   isInDungeon()             { return inDungeon; }
    public void      setInDungeon(boolean b)   { this.inDungeon = b; }
    public int       getCurrentDepth()         { return currentDepth; }
    public io.cannonforge.retroquest.model.DungeonViewState getDungeonViewState() { return dungeonViewState; }
    public MapData getCurrentDungeonMapData()         { return currentDungeonMapData; }
    public void    setCurrentDungeonMapData(MapData d) { this.currentDungeonMapData = d; }
    public void      setCurrentDepth(int d)    { this.currentDepth = d; }
    public GamePanel getGamePanel()            { return gamePanel; }
    public SaveData  getSaveData()             { return saveData; }
    public void      setCurrentOverworldName(String n) { this.currentOverworldName = n; }
    public void      setShowingTeleportAnimation(boolean b) { this.showingTeleportAnimation = b; }
    public String    getLastSafeTownName()     { return lastSafeTownName; }
    public void      setLastSafeTownName(String n) { this.lastSafeTownName = n; }

    /** Compatibility alias for {@link #getOverworldManager()}. */
    public NavigationController getNavController() { return navController; }
    public OverworldManager getWorld()             { return overworldManager; }
    public OverworldManager getOverworldManager()  { return overworldManager; }

    /**
     * Returns the permanently-revealed tile array for the given level of the dungeon the
     * player is currently in, creating it on first access.
     */
    public boolean[][] getRevealedForLevel(int depth) {
        return dungeonController.getRevealed(depth);
    }

    /** Returns the revealed-tile array stored under an explicit fog key, creating it if absent. */
    public boolean[][] getRevealedForKey(String key) {
        return revealed.computeIfAbsent(key, k -> new boolean[Dungeon.HEIGHT][Dungeon.WIDTH]);
    }

    /**
     * Identity of the dungeon the player is in — the authored dungeon's name, or
     * {@code "procedural"}. Used to namespace fog of war and tile state.
     */
    public String getDungeonId() { return dungeonViewState.getDungeonId(); }

    /** Fog-of-war key for a level of the dungeon the player is currently in. */
    public String revealedKey(int depth) { return getDungeonId() + ":" + depth; }

    /**
     * Persistence key for the map the player is standing on, used for all per-tile state
     * (looted chests, opened doors, spent altars, defeated encounters).
     *
     * <p>Dungeon keys carry the dungeon's identity so that state does not bleed between the
     * authored dungeons and the procedural one, which all share the same depth numbers.
     */
    public String currentMapKey() {
        if (inDungeon)             return "dungeon:" + getDungeonId() + ":" + currentDepth;
        if (currentTown != null)   return "town:" + currentTown.getName();
        return "overworld:" + currentOverworldName;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Entry Point
    // ─────────────────────────────────────────────────────────────────────────

    public static void main(String[] args) {
        // First: nothing else is diagnosable in a shipped build without this. The launchers
        // use javaw, so stderr goes nowhere; CrashLogger tees it to logs/retroquest.log and
        // installs the uncaught-exception handlers.
        CrashLogger.install();
        // Touching DisplayScale runs its static init, which disables Java2D's
        // built-in scaler before any Swing class loads.
        System.out.println("[RetroQuest] UI scale: " + DisplayScale.SCALE
                + " (override with -Dretroquest.uiScale=<factor>)");
        SwingUtilities.invokeLater(Retroquest::new);
    }
}

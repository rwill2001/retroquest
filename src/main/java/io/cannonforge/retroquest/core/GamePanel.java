package io.cannonforge.retroquest.core;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.util.List;

import javax.swing.JPanel;

import io.cannonforge.retroquest.animation.AscensionAnimation;
import io.cannonforge.retroquest.animation.CreditsAnimation;
import io.cannonforge.retroquest.animation.DungeonDescentAnimation;
import io.cannonforge.retroquest.animation.DungeonEntryAnimation;
import io.cannonforge.retroquest.animation.DungeonExitAnimation;
import io.cannonforge.retroquest.animation.EndgameCinematicAnimation;
import io.cannonforge.retroquest.animation.EpilogueAnimation;
import io.cannonforge.retroquest.animation.PlayerDeathAnimation;
import io.cannonforge.retroquest.animation.ShadowDescentAnimation;
import io.cannonforge.retroquest.animation.TownEntryAnimation;
import io.cannonforge.retroquest.animation.TownExitAnimation;
import io.cannonforge.retroquest.animation.WarMarchAnimation;
import io.cannonforge.retroquest.animation.WashAshoreAnimation;
import io.cannonforge.retroquest.animation.WaterfallCaveEntryAnimation;
import io.cannonforge.retroquest.animation.WaterfallCaveExitAnimation;
import io.cannonforge.retroquest.model.Dungeon;
import io.cannonforge.retroquest.model.Monster;
import io.cannonforge.retroquest.model.NPC;
import io.cannonforge.retroquest.model.Spell;
import io.cannonforge.retroquest.model.TileDefinition;
import io.cannonforge.retroquest.overlay.AbyssalDepthsOverlay;
import io.cannonforge.retroquest.overlay.SkyGamesOverlay;
import io.cannonforge.retroquest.overlay.NatureGamesOverlay;
import io.cannonforge.retroquest.overlay.MemoryGamesOverlay;
import io.cannonforge.retroquest.overlay.WarGamesOverlay;
import io.cannonforge.retroquest.overlay.BossFightCoordinator;
import io.cannonforge.retroquest.overlay.CasinoOverlay;
import io.cannonforge.retroquest.overlay.CombatOverlay;
import io.cannonforge.retroquest.overlay.CradleChoiceOverlay;
import io.cannonforge.retroquest.overlay.DeathOverlay;
import io.cannonforge.retroquest.overlay.DialogueOverlay;
import io.cannonforge.retroquest.overlay.DivineAudienceOverlay;
import io.cannonforge.retroquest.overlay.ForgeArenaOverlay;
import io.cannonforge.retroquest.overlay.HelpOverlay;
import io.cannonforge.retroquest.overlay.InventoryOverlay;
import io.cannonforge.retroquest.overlay.NotificationOverlay;
import io.cannonforge.retroquest.overlay.QuestLogOverlay;
import io.cannonforge.retroquest.overlay.SaveLoadOverlay;
import io.cannonforge.retroquest.overlay.ShopOverlay;
import io.cannonforge.retroquest.overlay.SpellbookOverlay;
import io.cannonforge.retroquest.registry.ImageAssetRegistry;
import io.cannonforge.retroquest.registry.TileRegistry;

/**
 * Primary game rendering panel — paints the overworld, town, or dungeon tile map
 * along with the player, NPCs, overlays (inventory, spellbook, combat, death, etc.)
 * and transition animations.
 *
 * <p>All game rendering flows through {@link #paintComponent(Graphics)}. Overlays
 * are drawn on top of the tile layer by delegating to their respective {@code paint()}
 * methods. Input is forwarded from the parent {@link Retroquest} frame.
 */
@SuppressWarnings("serial")
public class GamePanel extends JPanel {

    private final Retroquest game;
    private Image playerImage;

    // ── Teleport animation state ──────────────────────────────────────────────
    private boolean showingTeleport    = false;
    private long    teleportStartTime  = 0;
    private static final long TELEPORT_DURATION_MS = 4200;
    private javax.swing.Timer teleportTimer = null;

    // ── Dungeon entry / exit / descent animations ───────────────────────────
    private DungeonEntryAnimation   dungeonEntryAnimation   = null;
    private DungeonExitAnimation    dungeonExitAnimation    = null;
    private DungeonDescentAnimation dungeonDescentAnimation = null;
    private WashAshoreAnimation     washAshoreAnimation     = null;
    private ShadowDescentAnimation  shadowDescentAnimation  = null;
    private WarMarchAnimation       warMarchAnimation       = null;
    private AscensionAnimation      ascensionAnimation      = null;

    // ── Endgame animations ──────────────────────────────────────────────────
    private EndgameCinematicAnimation endgameCinematic = null;
    private EpilogueAnimation         epilogueAnimation = null;
    private CreditsAnimation          creditsAnimation  = null;
    private BossFightCoordinator      bossFightCoordinator = null;

    // ── Combat overlay ────────────────────────────────────────────────────────
    private final CombatOverlay    combatOverlay;
    private final SpellbookOverlay spellbookOverlay;
    private final InventoryOverlay inventoryOverlay;
    private final ShopOverlay       shopOverlay;
    private final CasinoOverlay     casinoOverlay;
    private final ForgeArenaOverlay  arenaOverlay;
    private final AbyssalDepthsOverlay depthsOverlay;
    private final SkyGamesOverlay    skyGamesOverlay;
    private final NatureGamesOverlay natureGamesOverlay;
    private final MemoryGamesOverlay memoryGamesOverlay;
    private final WarGamesOverlay    warGamesOverlay;
    private final SaveLoadOverlay   saveLoadOverlay;
    private final DeathOverlay       deathOverlay;
    private PlayerDeathAnimation     deathAnimation   = null;
    private TownEntryAnimation           townEntryAnimation          = null;
    private WaterfallCaveEntryAnimation  waterfallCaveEntryAnimation = null;
    private TownExitAnimation            townExitAnimation           = null;
    private WaterfallCaveExitAnimation   waterfallCaveExitAnimation  = null;
    private final QuestLogOverlay    questLogOverlay;
    private final DialogueOverlay   dialogueOverlay;
    private final HelpOverlay       helpOverlay;
    private final DivineAudienceOverlay divineAudienceOverlay;
    private final CradleChoiceOverlay  cradleChoiceOverlay;
    private final NotificationOverlay  notificationOverlay;
    private javax.swing.Timer   combatRepaintTimer = null;

    // ── Level-up flash effect ──────────────────────────────────────────────────
    private float levelUpFlashAlpha = 0f;
    private javax.swing.Timer levelUpFlashTimer = null;

    // Pre-seeded star tunnel geometry
    private static final int STAR_COUNT = 120;
    private final float[] starAngle  = new float[STAR_COUNT];
    private final float[] starSpeed  = new float[STAR_COUNT];
    private final float[] starOffset = new float[STAR_COUNT];
    private final int[]   starColor  = new int[STAR_COUNT];
    private final java.util.Random rng = new java.util.Random();

    /**
     * Black at every alpha, pre-built once. The darkness overlay used to allocate a
     * fresh {@link Color} per tile and per NPC per frame — on the order of 26,000
     * short-lived objects a second at 60 fps.
     */
    private static final Color[] DARKNESS = new Color[256];
    static {
        for (int a = 0; a < 256; a++) DARKNESS[a] = new Color(0, 0, 0, a);
    }

    /**
     * Stand-in handed to the paint loop when the active view discards lighting
     * (first-person dungeon). One empty row, so both {@code .length} and
     * {@code [0].length} bounds guards read cleanly and every lookup falls back.
     */
    private static final float[][] EMPTY_BRIGHTNESS = new float[1][0];

    /** Black at {@code alpha}, clamped into 0–255. Allocation-free. */
    private static Color darkness(int alpha) {
        if (alpha < 0)   alpha = 0;
        if (alpha > 255) alpha = 255;
        return DARKNESS[alpha];
    }

    private static final Color[] TUNNEL_COLORS = {
        new Color(255,  50, 200), new Color( 50, 255, 255),
        new Color(255, 220,  30), new Color( 80, 255,  80),
        new Color(200,  80, 255), new Color(255, 100,  40),
        new Color( 40, 120, 255), new Color(255, 255, 255),
    };

    public GamePanel(Retroquest game) {
        this.game = game;
        combatOverlay    = new CombatOverlay(game);
        spellbookOverlay = new SpellbookOverlay(game);
        inventoryOverlay = new InventoryOverlay(game);
        shopOverlay      = new ShopOverlay(game);
        casinoOverlay    = new CasinoOverlay(game);
        arenaOverlay     = new ForgeArenaOverlay(game);
        depthsOverlay    = new AbyssalDepthsOverlay(game);
        skyGamesOverlay      = new SkyGamesOverlay(game);
        natureGamesOverlay   = new NatureGamesOverlay(game);
        memoryGamesOverlay   = new MemoryGamesOverlay(game);
        warGamesOverlay      = new WarGamesOverlay(game);
        saveLoadOverlay  = new SaveLoadOverlay(game);
        deathOverlay     = new DeathOverlay(game);
        questLogOverlay  = new QuestLogOverlay(game);
        dialogueOverlay  = new DialogueOverlay(game);
        helpOverlay      = new HelpOverlay(game);
        divineAudienceOverlay = new DivineAudienceOverlay(game);
        cradleChoiceOverlay   = new CradleChoiceOverlay(game);
        notificationOverlay  = new NotificationOverlay();

        installTopDownMinimapKey();

        addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (isWashAshoreAnimationActive()) {
                    // swallow clicks during intro wash-ashore animation
                } else if (isTownEntryAnimationActive() || isTownExitAnimationActive()) {
                    // swallow clicks during town entry/exit animation
                } else if (isDeathAnimationActive()) {
                    // swallow clicks during death animation
                } else if (divineAudienceOverlay.isActive()) {
                    divineAudienceOverlay.handleClick(e.getX(), e.getY());
                    repaint();
                } else if (combatOverlay.isActive()) {
                    combatOverlay.handleClick(e.getX(), e.getY());
                    repaint();
                } else if (dialogueOverlay.isActive()) {
                    dialogueOverlay.handleClick(e.getX(), e.getY());
                    repaint();
                } else if (questLogOverlay.isActive()) {
                    questLogOverlay.handleClick(e.getX(), e.getY());
                    repaint();
                } else if (deathOverlay.isActive()) {
                    deathOverlay.handleClick(e.getX(), e.getY());
                    repaint();
                } else if (saveLoadOverlay.isActive()) {
                    saveLoadOverlay.handleClick(e.getX(), e.getY());
                    repaint();
                } else if (shopOverlay.isActive()) {
                    shopOverlay.handleClick(e.getX(), e.getY());
                    repaint();
                } else if (casinoOverlay.isActive()) {
                    casinoOverlay.handleClick(e.getX(), e.getY());
                    repaint();
                } else if (arenaOverlay.isActive()) {
                    arenaOverlay.handleClick(e.getX(), e.getY());
                    repaint();
                } else if (depthsOverlay.isActive()) {
                    depthsOverlay.handleClick(e.getX(), e.getY());
                    repaint();
                } else if (skyGamesOverlay.isActive()) {
                    skyGamesOverlay.handleClick(e.getX(), e.getY());
                    repaint();
                } else if (natureGamesOverlay.isActive()) {
                    natureGamesOverlay.handleClick(e.getX(), e.getY());
                    repaint();
                } else if (memoryGamesOverlay.isActive()) {
                    memoryGamesOverlay.handleClick(e.getX(), e.getY());
                    repaint();
                } else if (warGamesOverlay.isActive()) {
                    warGamesOverlay.handleClick(e.getX(), e.getY());
                    repaint();
                } else if (inventoryOverlay.isActive()) {
                    inventoryOverlay.handleClick(e.getX(), e.getY());
                    repaint();
                } else if (spellbookOverlay.isActive()) {
                    spellbookOverlay.handleClick(e.getX(), e.getY());
                    repaint();
                }
            }
        });
        setPreferredSize(new Dimension(DisplayScale.scaled(24 * 32), DisplayScale.scaled(18 * 32)));
        setBackground(Color.BLACK);
        loadAssets();
    }

    // ── Combat public API ─────────────────────────────────────────────────────

    /**
     * Starts an in-panel combat encounter.
     * Called by Retroquest.triggerEncounter() in place of new CombatDialog().
     */
    public void startCombat(Monster monster, CombatOverlay.OnCombatEnd callback) {
        startCombat(monster, false, callback);
    }

    /** Starts combat that cannot be fled (endgame boss phases). */
    public void startCombat(Monster monster, boolean noEscape, CombatOverlay.OnCombatEnd callback) {
        combatOverlay.start(monster, callback, noEscape);
        startCombatRepaintTimer();
    }

    public boolean isCombatActive() {
        return combatOverlay.isActive();
    }

    /**
     * Routes combat key input — called from Retroquest.handleKey() when combat is active.
     */
    public void handleCombatKey(KeyEvent e) {
        combatOverlay.handleKey(e);
    }

    /**
     * Relay for {@link SpellbookOverlay} — forwards the cast request to {@link CombatOverlay}.
     */
    public void executeCombatSpell(Spell spell) {
        combatOverlay.executeCombatSpell(spell);
    }

    private void startCombatRepaintTimer() {
        if (combatRepaintTimer != null) combatRepaintTimer.stop();
        combatRepaintTimer = new javax.swing.Timer(16, e -> {
            repaint();
            if (!combatOverlay.isActive()) {
                combatRepaintTimer.stop();
                combatRepaintTimer = null;
            }
        });
        combatRepaintTimer.start();
    }

    // ── Spellbook overlay ─────────────────────────────────────────────────────
    public void openSpellbook() {
        spellbookOverlay.open();
        repaint();
    }

    public void openSpellbookForCombat(CombatOverlay overlay) {
        spellbookOverlay.open(true, overlay);
        repaint();
    }

    public boolean isSpellbookActive() { return spellbookOverlay.isActive(); }

    public void handleSpellbookKey(java.awt.event.KeyEvent e) {
        spellbookOverlay.handleKey(e);
        repaint();
    }

    // ── Inventory overlay ─────────────────────────────────────────────────────
    public void openInventory() {
        inventoryOverlay.open();
        repaint();
    }

    public boolean isInventoryActive() { return inventoryOverlay.isActive(); }

    public void handleInventoryKey(java.awt.event.KeyEvent e) {
        inventoryOverlay.handleKey(e);
        repaint();
    }

    // ── Shop overlay ─────────────────────────────────────────────────────────
    public void openShop(List<String> npcItemIds) {
        shopOverlay.open(npcItemIds);
        repaint();
    }

    public ShopOverlay getShopOverlay() { return shopOverlay; }

    public boolean isShopActive() { return shopOverlay.isActive(); }

    public void handleShopKey(java.awt.event.KeyEvent e) {
        shopOverlay.handleKey(e);
        repaint();
    }

    // ── Casino overlay ──────────────────────────────────────────────────────────
    public void openCasino() {
        casinoOverlay.open();
        repaint();
    }

    public boolean isCasinoActive() { return casinoOverlay.isActive(); }

    public void handleCasinoKey(java.awt.event.KeyEvent e) {
        casinoOverlay.handleKey(e);
        repaint();
    }

    // ── Forge arena overlay ──────────────────────────────────────────────────
    public void openArena() {
        arenaOverlay.open();
        repaint();
    }

    public boolean isArenaActive() { return arenaOverlay.isActive(); }

    public void handleArenaKey(java.awt.event.KeyEvent e) {
        arenaOverlay.handleKey(e);
        repaint();
    }

    // ── Abyssal depths overlay ─────────────────────────────────────────────
    public void openDepths() {
        depthsOverlay.open();
        repaint();
    }

    public boolean isDepthsActive() { return depthsOverlay.isActive(); }

    public void handleDepthsKey(java.awt.event.KeyEvent e) {
        depthsOverlay.handleKey(e);
        repaint();
    }

    // ── Sky games overlay ────────────────────────────────────────────────────
    public void openSkyGames() {
        skyGamesOverlay.open();
        repaint();
    }

    public boolean isSkyGamesActive() { return skyGamesOverlay.isActive(); }

    public void handleSkyGamesKey(java.awt.event.KeyEvent e) {
        skyGamesOverlay.handleKey(e);
        repaint();
    }

    // ── Nature games overlay ─────────────────────────────────────────────────
    public void openNatureGames() {
        natureGamesOverlay.open();
        repaint();
    }

    public boolean isNatureGamesActive() { return natureGamesOverlay.isActive(); }

    public void handleNatureGamesKey(java.awt.event.KeyEvent e) {
        natureGamesOverlay.handleKey(e);
        repaint();
    }

    // ── Memory games overlay ─────────────────────────────────────────────────
    public void openMemoryGames() {
        memoryGamesOverlay.open();
        repaint();
    }

    public boolean isMemoryGamesActive() { return memoryGamesOverlay.isActive(); }

    public void handleMemoryGamesKey(java.awt.event.KeyEvent e) {
        memoryGamesOverlay.handleKey(e);
        repaint();
    }

    // ── War games overlay ────────────────────────────────────────────────────
    public void openWarGames() {
        warGamesOverlay.open();
        repaint();
    }

    public boolean isWarGamesActive() { return warGamesOverlay.isActive(); }

    public void handleWarGamesKey(java.awt.event.KeyEvent e) {
        warGamesOverlay.handleKey(e);
        repaint();
    }

    // ── Quest log overlay ────────────────────────────────────────────────────
    public void openQuestLog() {
        questLogOverlay.open();
        game.startOverlayRepaintTimer();
        repaint();
    }

    public boolean isQuestLogActive() { return questLogOverlay.isActive(); }

    public void handleQuestLogKey(java.awt.event.KeyEvent e) {
        questLogOverlay.handleKey(e);
        repaint();
    }

    // ── Help overlay ─────────────────────────────────────────────────────────
    public void openHelp() {
        helpOverlay.open();
        repaint();
    }

    public boolean isHelpActive() { return helpOverlay.isActive(); }

    public void handleHelpKey(KeyEvent e) {
        helpOverlay.handleKey(e);
        repaint();
    }

    // ── Dialogue overlay ────────────────────────────────────────────────────
    public void openDialogue(NPC npc) {
        dialogueOverlay.open(npc);
        repaint();
    }

    public DialogueOverlay getDialogueOverlay() { return dialogueOverlay; }

    public boolean isDialogueActive() { return dialogueOverlay.isActive(); }

    public void handleDialogueKey(java.awt.event.KeyEvent e) {
        dialogueOverlay.handleKey(e);
        repaint();
    }

    // ── Divine audience overlay ────────────────────────────────────────────
    public void openDivineAudience(io.cannonforge.retroquest.model.God god,
                                    String message, Runnable onDismiss) {
        divineAudienceOverlay.open(god, message, onDismiss);
        repaint();
    }

    public boolean isDivineAudienceActive() { return divineAudienceOverlay.isActive(); }

    public void handleDivineAudienceKey(java.awt.event.KeyEvent e) {
        divineAudienceOverlay.handleKey(e);
        repaint();
    }

    // ── Cradle choice overlay ────────────────────────────────────────────────
    public void openCradleChoice() {
        cradleChoiceOverlay.open();
        repaint();
    }

    public boolean isCradleChoiceActive() { return cradleChoiceOverlay.isActive(); }

    public void handleCradleChoiceKey(KeyEvent e) {
        cradleChoiceOverlay.handleKey(e);
        repaint();
    }

    // ── Endgame cinematic animation ─────────────────────────────────────────
    public void startEndgameCinematic() {
        endgameCinematic = new EndgameCinematicAnimation(game);
        endgameCinematic.start();
        repaint();
    }

    public boolean isEndgameCinematicActive() {
        return endgameCinematic != null && endgameCinematic.isActive();
    }

    public boolean isEndgameCinematicWaitingForKey() {
        return endgameCinematic != null && endgameCinematic.isWaitingForKey();
    }

    public void advanceEndgameCinematic() {
        if (endgameCinematic != null) endgameCinematic.pressKey();
    }

    // ── Epilogue animation ──────────────────────────────────────────────────
    public void startEpilogue(String endingType, String endingTitle,
                               io.cannonforge.retroquest.model.God championGod) {
        epilogueAnimation = new EpilogueAnimation(game, endingType, endingTitle, championGod);
        epilogueAnimation.start();
        repaint();
    }

    public boolean isEpilogueActive() {
        return epilogueAnimation != null && epilogueAnimation.isActive();
    }

    public boolean isEpilogueWaitingForKey() {
        return epilogueAnimation != null && epilogueAnimation.isWaitingForKey();
    }

    public void advanceEpilogue() {
        if (epilogueAnimation != null) epilogueAnimation.pressKey();
    }

    // ── Credits animation ───────────────────────────────────────────────────
    public void startCredits(String endingType, String endingTitle,
                              io.cannonforge.retroquest.model.God championGod) {
        creditsAnimation = new CreditsAnimation(game, endingType, endingTitle, championGod);
        creditsAnimation.start();
        repaint();
    }

    public boolean isCreditsActive() {
        return creditsAnimation != null && creditsAnimation.isActive();
    }

    public void skipCredits() {
        if (creditsAnimation != null) creditsAnimation.skip();
    }

    // ── Boss fight coordinator ───────────────────────────────────────────────
    public void setBossFightCoordinator(BossFightCoordinator c) {
        this.bossFightCoordinator = c;
    }

    public BossFightCoordinator getBossFightCoordinator() {
        return bossFightCoordinator;
    }

    public CombatOverlay getCombatOverlay() { return combatOverlay; }

    // ── Overlay accessors ─────────────────────────────────────────────────────
    // The game opens these through handleKey; nothing inside it needs a reference. They are
    // exposed for RetroRecorder, which has to open one directly to film it — there is no
    // keystroke that reaches, say, the shop without an NPC standing in front of you first.
    public SpellbookOverlay      getSpellbookOverlay()      { return spellbookOverlay; }
    public InventoryOverlay      getInventoryOverlay()      { return inventoryOverlay; }
    public QuestLogOverlay       getQuestLogOverlay()       { return questLogOverlay; }
    public CasinoOverlay         getCasinoOverlay()         { return casinoOverlay; }
    public ForgeArenaOverlay     getArenaOverlay()          { return arenaOverlay; }
    public AbyssalDepthsOverlay  getDepthsOverlay()         { return depthsOverlay; }
    public SkyGamesOverlay       getSkyGamesOverlay()       { return skyGamesOverlay; }
    public NatureGamesOverlay    getNatureGamesOverlay()    { return natureGamesOverlay; }
    public MemoryGamesOverlay    getMemoryGamesOverlay()    { return memoryGamesOverlay; }
    public WarGamesOverlay       getWarGamesOverlay()       { return warGamesOverlay; }
    public DivineAudienceOverlay getDivineAudienceOverlay() { return divineAudienceOverlay; }

    // ── Wash-ashore intro animation ───────────────────────────────────────────
    public void startWashAshoreAnimation() {
        washAshoreAnimation = new WashAshoreAnimation(game);
        washAshoreAnimation.start();
        repaint();
    }

    public boolean isWashAshoreAnimationActive() {
        return washAshoreAnimation != null && washAshoreAnimation.isActive();
    }

    public boolean isWashAshoreWaitingForKey() {
        return washAshoreAnimation != null && washAshoreAnimation.isWaitingForKey();
    }

    public void advanceWashAshore() {
        if (washAshoreAnimation != null) washAshoreAnimation.pressKey();
    }

    // ── Town entry animation ─────────────────────────────────────────────────
    private static boolean isCaveTown(String townName) {
        if (townName == null) return false;
        String n = townName.toLowerCase().trim();
        return n.endsWith("cave") || n.endsWith("caves") || n.endsWith("cavern") || n.endsWith("caverns");
    }

    public void startTownEntryAnimation(String townName) {
        if (isCaveTown(townName)) {
            waterfallCaveEntryAnimation = new WaterfallCaveEntryAnimation(game, townName);
            waterfallCaveEntryAnimation.start();
        } else {
            townEntryAnimation = new TownEntryAnimation(game, townName);
            townEntryAnimation.start();
        }
        repaint();
    }

    public boolean isTownEntryAnimationActive() {
        return (townEntryAnimation != null && townEntryAnimation.isActive())
            || (waterfallCaveEntryAnimation != null && waterfallCaveEntryAnimation.isActive());
    }

    public void startTownExitAnimation(String townName) {
        if (isCaveTown(townName)) {
            waterfallCaveExitAnimation = new WaterfallCaveExitAnimation(game, townName);
            waterfallCaveExitAnimation.start();
        } else {
            townExitAnimation = new TownExitAnimation(game, townName);
            townExitAnimation.start();
        }
        repaint();
    }

    public boolean isTownExitAnimationActive() {
        return (townExitAnimation != null && townExitAnimation.isActive())
            || (waterfallCaveExitAnimation != null && waterfallCaveExitAnimation.isActive());
    }

    // ── Player death animation ───────────────────────────────────────────────
    public void startDeathAnimation(String slayerName) {
        deathAnimation = new PlayerDeathAnimation(game, slayerName);
        deathAnimation.start();
        repaint();
    }

    public boolean isDeathAnimationActive() {
        return deathAnimation != null && deathAnimation.isActive();
    }

    // ── Death overlay ─────────────────────────────────────────────────────────
    public void showDeathOverlay(String reason) {
        deathOverlay.show(reason);
        game.startOverlayRepaintTimer();
        repaint();
    }

    public boolean isDeathOverlayActive() { return deathOverlay.isActive(); }
    public NotificationOverlay getNotificationOverlay() { return notificationOverlay; }
    public boolean isNotificationActive() { return notificationOverlay.isActive(); }

    public void handleDeathKey(java.awt.event.KeyEvent e) {
        deathOverlay.handleKey(e);
        repaint();
    }

    // ── SaveLoad overlay ──────────────────────────────────────────────────────
    public void openSaveDialog(boolean quitAfter) {
        saveLoadOverlay.openSave(quitAfter);
        game.startOverlayRepaintTimer();
        repaint();
    }

    public void openLoadDialog() {
        saveLoadOverlay.openLoad();
        game.startOverlayRepaintTimer();
        repaint();
    }

    public boolean isSaveLoadActive() { return saveLoadOverlay.isActive(); }

    public void handleSaveLoadKey(java.awt.event.KeyEvent e) {
        saveLoadOverlay.handleKey(e);
        repaint();
    }

    /**
     * Makes {@code M} toggle the minimap in the TOP-DOWN dungeon view.
     *
     * <p>{@code Retroquest.handleWireframeKey} already binds {@code M} for the two
     * first-person views, but its top-down switch has no {@code M} case, so the
     * first maze a player walks into has no way to bring the map up. This binding
     * fills exactly that gap and stands down whenever the first-person handler or
     * any overlay owns the keyboard, so no key is ever handled twice.
     *
     * <p>The tidier home for this is a {@code case KeyEvent.VK_M} in Retroquest's
     * top-down switch; that file belongs to another owner, so it lives here.
     */
    private void installTopDownMinimapKey() {
        getInputMap(WHEN_IN_FOCUSED_WINDOW)
                .put(javax.swing.KeyStroke.getKeyStroke(KeyEvent.VK_M, 0), "toggle-topdown-minimap");
        getActionMap().put("toggle-topdown-minimap", new javax.swing.AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                if (!game.isInDungeon()) return;
                if (game.getDungeonViewState().isFirstPerson()) return; // Retroquest handles it there
                if (game.getMessageLog() != null && game.getMessageLog().isPromptActive()) return;
                if (anyOverlayActive()) return;
                game.getDungeonViewState().toggleMinimap();
                repaint();
            }
        });
    }

    /** True while any overlay, prompt or animation owns the screen and the keyboard. */
    private boolean anyOverlayActive() {
        return isCombatActive()      || isSpellbookActive()   || isInventoryActive()
            || isShopActive()        || isCasinoActive()      || isArenaActive()
            || isDepthsActive()      || isSkyGamesActive()    || isNatureGamesActive()
            || isMemoryGamesActive() || isWarGamesActive()    || isSaveLoadActive()
            || isDeathOverlayActive()|| isQuestLogActive()    || isDialogueActive()
            || isHelpActive()        || isDivineAudienceActive() || isCradleChoiceActive()
            || isDeathAnimationActive() || isCreditsActive()  || isEpilogueActive()
            || isEndgameCinematicActive() || isWashAshoreAnimationActive()
            || isTownEntryAnimationActive() || isTownExitAnimationActive();
    }

    // ── Top-down dungeon HUD ─────────────────────────────────────────────────
    // Same palette and the same DungeonWallQuery / revealed[][] data the
    // first-person minimap uses, laid out as a corner panel instead of a
    // full-width strip.
    private static final Color MM_BG      = new Color(8, 6, 3);
    /** Beyond the edge of the map: no terrain, just the dark the world is drawn on. */
    private static final Color OFF_MAP    = new Color(6, 6, 9);
    private static final Color MM_WALL    = new Color(90, 65, 30);
    private static final Color MM_DOOR    = new Color(160, 120, 40);
    private static final Color MM_PLAYER  = new Color(80, 220, 120);
    private static final Color MM_FOG     = new Color(20, 15, 8);
    private static final Color MM_SPECIAL = new Color(200, 160, 50);
    private static final Color HUD_TEXT   = new Color(180, 140, 50);
    private static final Color HUD_DIM    = new Color(80, 65, 30);

    /** Depth readout, plus the minimap when {@code M} has it toggled on. */
    private void paintTopDownDungeonHud(Graphics2D g2, int W, int H) {
        var view = game.getDungeonViewState();
        String name  = view.isAuthored() ? view.getAuthoredDungeonName() : null;
        String label = (name != null && !name.isBlank() ? name + "  •  " : "")
                     + "DEPTH " + game.getCurrentDepth();

        g2.setFont(Fonts.monoBold(12));
        java.awt.FontMetrics fm = g2.getFontMetrics();
        int pad   = DisplayScale.scaled(6);
        int edge  = DisplayScale.scaled(8);
        int boxW  = fm.stringWidth(label) + pad * 2;
        int boxH  = fm.getHeight() + pad;
        int bx    = W - boxW - edge;
        int by    = edge;

        g2.setColor(new Color(8, 6, 3, 210));
        g2.fillRect(bx, by, boxW, boxH);
        g2.setColor(HUD_DIM);
        g2.drawRect(bx, by, boxW - 1, boxH - 1);
        g2.setColor(HUD_TEXT);
        g2.drawString(label, bx + pad, by + pad / 2 + fm.getAscent());

        if (!view.isMinimapVisible()) {
            g2.setFont(Fonts.mono(10));
            g2.setColor(HUD_DIM);
            String hint = "[M] MAP";
            g2.drawString(hint, W - edge - g2.getFontMetrics().stringWidth(hint), by + boxH + DisplayScale.scaled(12));
            return;
        }

        int size = Math.min(DisplayScale.scaled(150), Math.min(W, H) / 3);
        paintTopDownMinimap(g2, W - size - edge, by + boxH + DisplayScale.scaled(4), size);
    }

    /** Square minimap window centred on the player, drawn from {@code revealed[][]}. */
    private void paintTopDownMinimap(Graphics2D g2, int x0, int y0, int size) {
        char[][] map = game.getCurrentMap();
        if (map == null) return;

        boolean authored = game.getDungeonViewState().isAuthored();
        int pz = game.getCurrentDepth();
        // DungeonWallQuery uses 1-based coordinates for procedural dungeons.
        int px = authored ? game.getPlayer().getX() : game.getPlayer().getX() + 1;
        int py = authored ? game.getPlayer().getY() : game.getPlayer().getY() + 1;
        boolean[][] revealed = game.getRevealedForLevel(pz);

        g2.setColor(new Color(8, 6, 3, 230));
        g2.fillRect(x0, y0, size, size);
        g2.setColor(HUD_DIM);
        g2.drawRect(x0, y0, size - 1, size - 1);

        int inset = DisplayScale.scaled(4);
        int avail = size - inset * 2;
        int cell  = Math.max(2, avail / 21);          // ~21 tiles across
        int span  = avail / cell;
        int mx0   = x0 + inset + (avail - span * cell) / 2;
        int my0   = y0 + inset + (avail - span * cell) / 2;
        int startX = px - span / 2, startY = py - span / 2;

        for (int ty = 0; ty < span; ty++) {
            for (int tx = 0; tx < span; tx++) {
                int wx = startX + tx, wy = startY + ty;
                int sx = mx0 + tx * cell, sy = my0 + ty * cell;

                int rx = authored ? wx : wx - 1;
                int ry = authored ? wy : wy - 1;
                boolean vis = rx >= 0 && ry >= 0 && revealed != null
                        && ry < revealed.length && rx < revealed[0].length && revealed[ry][rx];
                if (!vis) {
                    g2.setColor(MM_BG);
                    g2.fillRect(sx, sy, cell, cell);
                    continue;
                }

                if (authored && wy >= 0 && wy < map.length && wx >= 0 && wx < map[0].length) {
                    char tile = map[wy][wx];
                    if (!TileRegistry.getByIdSafe(tile).isWalkable()) {
                        g2.setColor(MM_WALL);
                        g2.fillRect(sx, sy, cell, cell);
                        continue;
                    }
                    if (tile == 'd' || tile == '|' || tile == '[' || tile == '{') {
                        g2.setColor(MM_DOOR);
                        g2.fillRect(sx, sy, cell, cell);
                        continue;
                    }
                }

                g2.setColor(MM_FOG);
                g2.fillRect(sx, sy, cell, cell);

                if (!authored) {
                    Dungeon.WallType n = DungeonWallQuery.getWall(wx, wy, pz, 0, map, authored);
                    Dungeon.WallType w = DungeonWallQuery.getWall(wx, wy, pz, 3, map, authored);
                    Dungeon.WallType s = DungeonWallQuery.getWall(wx, wy, pz, 2, map, authored);
                    Dungeon.WallType e = DungeonWallQuery.getWall(wx, wy, pz, 1, map, authored);
                    if (n != Dungeon.WallType.OPEN) {
                        g2.setColor(n == Dungeon.WallType.DOOR ? MM_DOOR : MM_WALL);
                        g2.drawLine(sx, sy, sx + cell - 1, sy);
                    }
                    if (w != Dungeon.WallType.OPEN) {
                        g2.setColor(w == Dungeon.WallType.DOOR ? MM_DOOR : MM_WALL);
                        g2.drawLine(sx, sy, sx, sy + cell - 1);
                    }
                    if (s == Dungeon.WallType.WALL) {
                        g2.setColor(MM_WALL);
                        g2.drawLine(sx, sy + cell - 1, sx + cell - 1, sy + cell - 1);
                    }
                    if (e == Dungeon.WallType.WALL) {
                        g2.setColor(MM_WALL);
                        g2.drawLine(sx + cell - 1, sy, sx + cell - 1, sy + cell - 1);
                    }
                }

                if (cell > 3 && DungeonWallQuery.getSpecial(wx, wy, pz, map, authored) != '.') {
                    g2.setColor(MM_SPECIAL);
                    g2.fillRect(sx + 1, sy + 1, cell - 2, cell - 2);
                }
            }
        }

        g2.setColor(MM_PLAYER);
        g2.fillRect(mx0 + (px - startX) * cell, my0 + (py - startY) * cell, cell, cell);
    }

    // ── Tile helpers ──────────────────────────────────────────────────────────
    /**
     * Tile edge in pixels. Deliberately square.
     *
     * <p>Width and height used to be divided independently, which is exact only while the panel's
     * aspect happens to match {@code viewWidth : viewHeight}. It does at the packed size (768×576
     * over 24×18 tiles), so this changes nothing there — but it stops sprites stretching when the
     * panel is any other shape, which is what fullscreen and the +/- zoom both produce.
     */
    private int tileSize() {
        return Math.max(1, Math.min(getWidth()  / game.getViewWidth(),
                                    getHeight() / game.getViewHeight()));
    }

    private int tileW() { return tileSize(); }
    private int tileH() { return tileSize(); }

    /** Left inset that centres the square tile grid when the panel is wider than it needs. */
    private int gridOriginX() { return (getWidth()  - tileSize() * game.getViewWidth())  / 2; }

    /** Top inset that centres the square tile grid when the panel is taller than it needs. */
    private int gridOriginY() { return (getHeight() - tileSize() * game.getViewHeight()) / 2; }

    // ── Teleport API ──────────────────────────────────────────────────────────
    public void startTeleportAnimation() {
        for (int i = 0; i < STAR_COUNT; i++) {
            starAngle[i]  = rng.nextFloat() * 360f;
            starSpeed[i]  = 280f + rng.nextFloat() * 380f;
            starOffset[i] = rng.nextFloat() * 0.4f;
            starColor[i]  = rng.nextInt(360);
        }
        teleportStartTime = System.currentTimeMillis();
        showingTeleport   = true;

        if (teleportTimer != null) teleportTimer.stop();
        teleportTimer = new javax.swing.Timer(16, e -> {
            repaint();
            if (System.currentTimeMillis() - teleportStartTime > TELEPORT_DURATION_MS + 200) {
                showingTeleport = false;
                teleportTimer.stop();
            }
        });
        teleportTimer.start();
    }

    public boolean isShowingTeleportAnimation() { return showingTeleport; }

    // ── Level-up flash ─────────────────────────────────────────────────────────
    /** Triggers a brief golden screen flash to celebrate a level-up. */
    public void triggerLevelUpFlash() {
        levelUpFlashAlpha = 0.45f;
        if (levelUpFlashTimer != null) levelUpFlashTimer.stop();
        levelUpFlashTimer = new javax.swing.Timer(30, e -> {
            levelUpFlashAlpha -= 0.03f;
            if (levelUpFlashAlpha <= 0f) {
                levelUpFlashAlpha = 0f;
                levelUpFlashTimer.stop();
            }
            repaint();
        });
        levelUpFlashTimer.start();
        repaint();
    }

    // ── Dungeon entrance / exit API ───────────────────────────────────────────
    public void startDungeonEntryAnimation() {
        dungeonEntryAnimation = new DungeonEntryAnimation(game);
        dungeonEntryAnimation.start();
        repaint();
    }

    public boolean isShowingDungeonEntryAnimation() {
        return dungeonEntryAnimation != null && dungeonEntryAnimation.isActive();
    }

    public void startDungeonDescentAnimation(int targetDepth) {
        dungeonDescentAnimation = new DungeonDescentAnimation(game, targetDepth);
        dungeonDescentAnimation.start();
        repaint();
    }

    public boolean isShowingDungeonDescentAnimation() {
        return dungeonDescentAnimation != null && dungeonDescentAnimation.isActive();
    }

    public void startDungeonExitAnimation() {
        dungeonExitAnimation = new DungeonExitAnimation(game);
        dungeonExitAnimation.start();
        repaint();
    }

    public boolean isShowingDungeonExitAnimation() {
        return dungeonExitAnimation != null && dungeonExitAnimation.isActive();
    }

    // ── Island portal transition animations ──────────────────────────────────

    public void startShadowDescentAnimation() {
        shadowDescentAnimation = new ShadowDescentAnimation(game);
        shadowDescentAnimation.start();
        repaint();
    }

    public boolean isShowingShadowDescentAnimation() {
        return shadowDescentAnimation != null && shadowDescentAnimation.isActive();
    }

    public void startWarMarchAnimation() {
        warMarchAnimation = new WarMarchAnimation(game);
        warMarchAnimation.start();
        repaint();
    }

    public boolean isShowingWarMarchAnimation() {
        return warMarchAnimation != null && warMarchAnimation.isActive();
    }

    public void startAscensionAnimation() {
        ascensionAnimation = new AscensionAnimation(game);
        ascensionAnimation.start();
        repaint();
    }

    public boolean isShowingAscensionAnimation() {
        return ascensionAnimation != null && ascensionAnimation.isActive();
    }

    // ── Asset loading ─────────────────────────────────────────────────────────
    private void loadAssets() {
        ImageAssetRegistry.reloadAll();
        playerImage = ImageAssetRegistry.get("player");
        TileRegistry.validateSprites();
    }

    // ── Paint ─────────────────────────────────────────────────────────────────
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;

        // Keep pixel art crisp when the display scales the frame (high-DPI monitors).
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                            RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        g2.setColor(Color.BLACK);
        g2.fillRect(0, 0, getWidth(), getHeight());

        // Skip map rendering while endgame cinematic plays
        if (endgameCinematic != null && endgameCinematic.isActive()) {
            endgameCinematic.paint(g2, getWidth(), getHeight());
            return;
        }
        // Skip map rendering while epilogue plays
        if (epilogueAnimation != null && epilogueAnimation.isActive()) {
            epilogueAnimation.paint(g2, getWidth(), getHeight());
            return;
        }
        // Skip map rendering while credits roll
        if (creditsAnimation != null && creditsAnimation.isActive()) {
            creditsAnimation.paint(g2, getWidth(), getHeight());
            return;
        }
        // Skip map rendering while island transition animations play
        if (shadowDescentAnimation != null && shadowDescentAnimation.isActive()) {
            shadowDescentAnimation.paint(g2, getWidth(), getHeight());
            return;
        }
        if (warMarchAnimation != null && warMarchAnimation.isActive()) {
            warMarchAnimation.paint(g2, getWidth(), getHeight());
            return;
        }
        if (ascensionAnimation != null && ascensionAnimation.isActive()) {
            ascensionAnimation.paint(g2, getWidth(), getHeight());
            return;
        }
        // Skip map rendering while wash-ashore intro animation plays
        if (washAshoreAnimation != null && washAshoreAnimation.isActive()) {
            washAshoreAnimation.paint(g2, getWidth(), getHeight());
            return;
        }
        // Skip map rendering while dungeon entry animation plays
        if (dungeonEntryAnimation != null && dungeonEntryAnimation.isActive()) {
            dungeonEntryAnimation.paint(g2, getWidth(), getHeight());
            return;
        }
        // Skip map rendering while dungeon descent animation plays
        if (dungeonDescentAnimation != null && dungeonDescentAnimation.isActive()) {
            dungeonDescentAnimation.paint(g2, getWidth(), getHeight());
            return;
        }
        // Skip map rendering while dungeon exit animation plays
        if (dungeonExitAnimation != null && dungeonExitAnimation.isActive()) {
            dungeonExitAnimation.paint(g2, getWidth(), getHeight());
            return;
        }
        // Skip map rendering while town entry animation plays
        if (townEntryAnimation != null && townEntryAnimation.isActive()) {
            townEntryAnimation.paint(g2, getWidth(), getHeight());
            return;
        }
        if (waterfallCaveEntryAnimation != null && waterfallCaveEntryAnimation.isActive()) {
            waterfallCaveEntryAnimation.paint(g2, getWidth(), getHeight());
            return;
        }
        // Skip map rendering while town exit animation plays
        if (townExitAnimation != null && townExitAnimation.isActive()) {
            townExitAnimation.paint(g2, getWidth(), getHeight());
            return;
        }
        if (waterfallCaveExitAnimation != null && waterfallCaveExitAnimation.isActive()) {
            waterfallCaveExitAnimation.paint(g2, getWidth(), getHeight());
            return;
        }

        final int tw = tileW();
        final int th = tileH();

        char[][] map  = game.getCurrentMap();
        int camX      = game.getCameraX();
        int camY      = game.getCameraY();
        int viewW     = game.getViewWidth();
        int viewH     = game.getViewHeight();

        // ── Compute visibility/lighting ──
        // First-person (textured / wireframe) dungeon views draw none of the
        // top-down tile grid, so the whole lighting solution would be discarded.
        // revealed[][] is kept up to date by DungeonController.revealVisibleArea()
        // on every move/level change, so nothing is lost by not asking here.
        final boolean firstPerson = game.isInDungeon() && game.getDungeonViewState().isFirstPerson();
        float[][] brightness = firstPerson ? EMPTY_BRIGHTNESS
                                           : VisionSystem.computeBrightness(game);

        // The tile grid is square and centred, so a panel wider than the grid gets a margin
        // rather than stretched tiles. The first-person renderers are given the whole panel —
        // they derive their own projection from its width and height, so a wider view is simply
        // a wider view. Undone before the HUD and overlays, which are all screen-space.
        final int gridOX = firstPerson ? 0 : gridOriginX();
        final int gridOY = firstPerson ? 0 : gridOriginY();
        if (gridOX != 0 || gridOY != 0) g2.translate(gridOX, gridOY);

        // ── Map rendering ──
        if (game.isInDungeon() && game.getDungeonViewState().isRaycast()
                && RaycastDungeonRenderer.canRender(game)) {
            RaycastDungeonRenderer.paint(g2, getWidth(), getHeight(), game);
        } else if (game.isInDungeon()
                && (game.getDungeonViewState().isTextured() || game.getDungeonViewState().isRaycast())) {
            // A raycast island entered through a procedural dungeon falls back to the band
            // renderer: procedural walls are edges between cells, which a grid raycaster
            // cannot represent. See RaycastDungeonRenderer's class note.
            TexturedDungeonRenderer.paint(g2, getWidth(), getHeight(), game);
        } else if (game.isInDungeon() && game.getDungeonViewState().isWireframe()) {
            WireframeDungeonRenderer.paint(g2, getWidth(), getHeight(), game);
        } else if (game.isInDungeon()) {
            int depth = game.getCurrentDepth();

            // Wall thickness: chunky like the original — about 30% of tile size
            int wallThick = Math.max(3, tw * 10 / 32);

            for (int sy = 0; sy < viewH; sy++) {
                for (int sx = 0; sx < viewW; sx++) {
                    int wx = camX + sx, wy = camY + sy;
                    int screenX = sx * tw, screenY = sy * th;
                    float b = (sy < brightness.length && sx < brightness[0].length)
                            ? brightness[sy][sx] : 0f;

                    if (b < VisionSystem.VISIBILITY_THRESHOLD) {
                        g2.setColor(Color.BLACK);
                        g2.fillRect(screenX, screenY, tw, th);
                        continue;
                    }

                    int x = wx + 1, y = wy + 1; // 1-based dungeon coords

                    // ── Floor ──
                    Image floor = ImageAssetRegistry.get("dungeon/dungeon_floor");
                    if (floor != null) g2.drawImage(floor, screenX, screenY, tw, th, null);
                    else { g2.setColor(new Color(70, 55, 40)); g2.fillRect(screenX, screenY, tw, th); }

                    // ── Special feature ──
                    char special = Dungeon.getSpecial(x, y, depth);
                    if (special != '.') {
                        String key = switch (special) {
                            case 'I' -> "dungeon/inn";
                            case 'P' -> "dungeon/pit";
                            case 't' -> "dungeon/teleporter";
                            case 's' -> "dungeon/stairway";
                            case 'A' -> "dungeon/altar";
                            case 'f' -> "dungeon/fountain";
                            case 'g' -> "dungeon/cube";
                            case 'H' -> "dungeon/throne";
                            case 'B' -> "dungeon/box";
                            case 'R' -> "dungeon/puzzle";
                            default  -> "dungeon/dungeon_floor";
                        };
                        Image specImg = ImageAssetRegistry.get(key);
                        if (specImg != null) g2.drawImage(specImg, screenX, screenY, tw, th, null);
                    }

                    // ── Walls ──
                    Dungeon.WallType north = Dungeon.getNorthWall(x, y, depth);
                    Dungeon.WallType west  = Dungeon.getWestWall(x, y, depth);

                    // Neighbour brightness checks (for edge walls)
                    float southB = (sy + 1 < brightness.length) ? brightness[sy + 1][sx] : 0f;
                    float eastB  = (sx + 1 < brightness[0].length) ? brightness[sy][sx + 1] : 0f;

                    Dungeon.WallType south = (southB < VisionSystem.VISIBILITY_THRESHOLD)
                            ? Dungeon.getSouthWall(x, y, depth) : Dungeon.WallType.OPEN;
                    Dungeon.WallType east  = (eastB < VisionSystem.VISIBILITY_THRESHOLD)
                            ? Dungeon.getEastWall(x, y, depth)  : Dungeon.WallType.OPEN;

                    drawNorthWall(g2, screenX, screenY, tw, th, wallThick, north);
                    drawWestWall (g2, screenX, screenY, tw, th, wallThick, west);
                    drawSouthWall(g2, screenX, screenY, tw, th, wallThick, south);
                    drawEastWall (g2, screenX, screenY, tw, th, wallThick, east);

                    // ── Darkness overlay ──
                    if (b < 1.0f) {
                        g2.setColor(darkness((int)((1.0f - b) * 255)));
                        g2.fillRect(screenX, screenY, tw, th);
                    }
                }
            }
        } else {
            for (int sy = 0; sy < viewH; sy++) {
                for (int sx = 0; sx < viewW; sx++) {
                    int wx = camX + sx, wy = camY + sy;
                    int screenX = sx * tw, screenY = sy * th;

                    boolean offMap = wy < 0 || wy >= map.length || wx < 0 || wx >= map[0].length;
                    if (offMap) {
                        // Beyond the edge of the world there is nothing to draw. Filling it
                        // with a real tile made towns look walled in by mountains.
                        g2.setColor(OFF_MAP);
                        g2.fillRect(screenX, screenY, tw, th);
                        continue;
                    }
                    TileDefinition def = TileRegistry.getByIdSafe(map[wy][wx]);
                    Image img = ImageAssetRegistry.get(def.getSpriteKey());

                    if (img != null) g2.drawImage(img, screenX, screenY, tw, th, null);
                    else { g2.setColor(def.getEditorColor()); g2.fillRect(screenX, screenY, tw, th); }

                    // ── Darkness overlay ──
                    float b = (sy < brightness.length && sx < brightness[0].length)
                            ? brightness[sy][sx] : 1f;
                    if (b < 1.0f) {
                        g2.setColor(darkness((int)((1.0f - b) * 255)));
                        g2.fillRect(screenX, screenY, tw, th);
                    }
                }
            }
        }

        // ── NPCs ──
        if (!game.isInDungeon()) {
            List<NPC> npcs = game.getCurrentTown() != null
                    ? game.getCurrentTown().getNpcs()
                    : game.getWorld().getNpcs();

            for (NPC npc : npcs) {
                int sx = npc.getX() - game.getCameraX();
                int sy = npc.getY() - game.getCameraY();
                if (sx < 0 || sx >= viewW || sy < 0 || sy >= viewH) continue;
                // Hide NPCs in darkness
                float nb = (sy >= 0 && sy < brightness.length && sx >= 0 && sx < brightness[0].length)
                        ? brightness[sy][sx] : 1f;
                if (nb < VisionSystem.NPC_VISIBILITY_THRESHOLD) continue;
                String raw = npc.getSpriteName();
                Image img = ImageAssetRegistry.get(raw.contains("/") ? raw : "npcs/" + raw.replace(".png", ""));
                if (img == null) img = ImageAssetRegistry.get("npcs/townsman");
                if (img != null) {
                    g2.drawImage(img, sx * tw, sy * th, tw, th, null);
                    // Darken NPC sprite to match surroundings
                    if (nb < 1.0f) {
                        g2.setColor(darkness((int)((1.0f - nb) * 255)));
                        g2.fillRect(sx * tw, sy * th, tw, th);
                    }
                }
            }
        }

        // ── Player (always full brightness) — skip in wireframe mode (player is the camera) ──
        if (game.isInDungeon() && game.getDungeonViewState().isFirstPerson()) {
            // Skip player sprite — first-person renderer draws the camera view
        } else {
        int px = game.getPlayer().getX() - game.getCameraX();
        int py = game.getPlayer().getY() - game.getCameraY();
        if (playerImage != null) {
            g2.drawImage(playerImage, px * tw, py * th, tw, th, null);
        } else {
            g2.setColor(Color.YELLOW);
            g2.setFont(new Font("Monospaced", Font.BOLD, Math.min(tw, th)));
            g2.drawString("@", px * tw + 4, py * th + th - 4);
        }
        } // end wireframe skip

        if (gridOX != 0 || gridOY != 0) g2.translate(-gridOX, -gridOY);

        // ── Top-down dungeon HUD: depth readout + optional minimap ──
        // The first-person views have had both from the start; the top-down view —
        // the first maze anyone walks into — had neither.
        if (game.isInDungeon() && !game.getDungeonViewState().isFirstPerson()) {
            paintTopDownDungeonHud(g2, getWidth(), getHeight());
        }

        // ── Player death animation ──
        if (deathAnimation != null && deathAnimation.isActive()) {
            deathAnimation.paint(g2, getWidth(), getHeight());
        }

        // ── Teleport overlay ──
        if (showingTeleport) {
            drawTeleportAnimation(g2, tw, th);
        }

        // ── Level-up flash ──
        if (levelUpFlashAlpha > 0f) {
            g2.setColor(new Color(255, 200, 50, (int)(levelUpFlashAlpha * 255)));
            g2.fillRect(0, 0, getWidth(), getHeight());
        }

        // ── Combat overlay ──
        if (combatOverlay.isActive()) {
            combatOverlay.paint(g2, getWidth(), getHeight());
        }
        // ── Boss phase transition overlay ──
        if (bossFightCoordinator != null && bossFightCoordinator.isTransitionActive()) {
            bossFightCoordinator.paintTransition(g2, getWidth(), getHeight());
        }
        // ── Cradle choice overlay ──
        if (cradleChoiceOverlay.isActive()) {
            cradleChoiceOverlay.paint(g2, getWidth(), getHeight());
        }
        // ── Spellbook overlay ──
        if (spellbookOverlay.isActive()) {
            spellbookOverlay.paint(g2, getWidth(), getHeight());
        }
        // ── Inventory overlay ──
        if (inventoryOverlay.isActive()) {
            inventoryOverlay.paint(g2, getWidth(), getHeight());
        }
        // ── Shop overlay ──
        if (shopOverlay.isActive()) {
            shopOverlay.paint(g2, getWidth(), getHeight());
        }
        // ── Casino overlay ──
        if (casinoOverlay.isActive()) {
            casinoOverlay.paint(g2, getWidth(), getHeight());
        }
        // ── Forge arena overlay ──
        if (arenaOverlay.isActive()) {
            arenaOverlay.paint(g2, getWidth(), getHeight());
        }
        // ── Abyssal depths overlay ──
        if (depthsOverlay.isActive()) {
            depthsOverlay.paint(g2, getWidth(), getHeight());
        }
        // ── Sky games overlay ──
        if (skyGamesOverlay.isActive()) {
            skyGamesOverlay.paint(g2, getWidth(), getHeight());
        }
        // ── Nature games overlay ──
        if (natureGamesOverlay.isActive()) {
            natureGamesOverlay.paint(g2, getWidth(), getHeight());
        }
        // ── Memory games overlay ──
        if (memoryGamesOverlay.isActive()) {
            memoryGamesOverlay.paint(g2, getWidth(), getHeight());
        }
        // ── War games overlay ──
        if (warGamesOverlay.isActive()) {
            warGamesOverlay.paint(g2, getWidth(), getHeight());
        }
        // ── SaveLoad overlay ──
        if (saveLoadOverlay.isActive()) {
            saveLoadOverlay.paint(g2, getWidth(), getHeight());
        }
        // ── Dialogue overlay ──
        if (dialogueOverlay.isActive()) {
            dialogueOverlay.paint(g2, getWidth(), getHeight());
        }
        // ── Quest log overlay ──
        if (questLogOverlay.isActive()) {
            questLogOverlay.paint(g2, getWidth(), getHeight());
        }
        // ── Help overlay ──
        if (helpOverlay.isActive()) {
            helpOverlay.paint(g2, getWidth(), getHeight());
        }
        // ── Divine audience overlay ──
        if (divineAudienceOverlay.isActive()) {
            divineAudienceOverlay.paint(g2, getWidth(), getHeight());
        }
        // ── Notification toasts ──
        if (notificationOverlay.isActive()) {
            notificationOverlay.paint(g2, getWidth(), getHeight());
        }
        // ── Death overlay (topmost) ──
        if (deathOverlay.isActive()) {
            deathOverlay.paint(g2, getWidth(), getHeight());
        }
    }

    // ── Dungeon wall drawing ──────────────────────────────────────────────────
    //
    // WALL OWNERSHIP RULE: Each wall is owned by exactly one cell.
    //   - North wall  → owned by this cell   (y edge of cell)
    //   - West  wall  → owned by this cell   (x edge of cell)
    //   - South wall  → owned by the cell BELOW (only drawn here at fog edges)
    //   - East  wall  → owned by the cell to the RIGHT (only drawn here at fog edges)
    //
    // This eliminates the double-door/double-wall bug where adjacent revealed
    // cells each drew the shared wall, resulting in two overlapping doors.
    //
    // Visual style: thick stone blocks, dark mortar lines, warm torch-lit amber.
    // Doors: a narrow dark archway with a lighter central gap (passage feel).

    // Stone wall base and highlight colours — warm torchlit dungeon palette
    private static final Color WALL_BASE    = new Color(95,  72,  45);   // warm dark brown
    private static final Color WALL_MID     = new Color(115, 88,  55);   // mid stone
    private static final Color WALL_LIGHT   = new Color(140, 108, 68);   // highlight face
    private static final Color WALL_DARK    = new Color(55,  40,  22);   // deep shadow / mortar
    private static final Color WALL_SHADOW  = new Color(30,  22,  10);   // outer shadow edge
    private static final Color DOOR_ARCH    = new Color(60,  45,  25);   // door arch stone
    private static final Color DOOR_GAP     = new Color(18,  12,   6);   // passage darkness
    private static final Color DOOR_PLANK   = new Color(100, 68,  30);   // wooden plank colour
    private static final Color DOOR_PLANK2  = new Color(85,  56,  22);   // plank shadow stripe

    /** Horizontal wall bar (north or south edge of a tile). */
    private void drawHorizWall(Graphics2D g2, int rx, int ry, int rw, int rh,
                               Dungeon.WallType type) {
        if (type == Dungeon.WallType.OPEN) return;

        if (type == Dungeon.WallType.WALL) {
            // Outer shadow strip
            g2.setColor(WALL_SHADOW);
            g2.fillRect(rx, ry, rw, 1);
            // Main body — two tone for a stone block feel
            g2.setColor(WALL_BASE);
            g2.fillRect(rx, ry + 1, rw, rh - 2);
            // Horizontal mortar lines at thirds
            g2.setColor(WALL_DARK);
            int m1 = ry + rh / 3, m2 = ry + rh * 2 / 3;
            g2.drawLine(rx, m1, rx + rw, m1);
            g2.drawLine(rx, m2, rx + rw, m2);
            // Vertical brick joints — offset on alternate mortar rows
            int brickW = Math.max(6, rw / 5);
            for (int bx = rx; bx < rx + rw; bx += brickW) {
                g2.drawLine(bx, ry + 1, bx, m1 - 1);
            }
            for (int bx = rx + brickW / 2; bx < rx + rw; bx += brickW) {
                g2.drawLine(bx, m1 + 1, bx, m2 - 1);
            }
            for (int bx = rx; bx < rx + rw; bx += brickW) {
                g2.drawLine(bx, m2 + 1, bx, ry + rh - 2);
            }
            // Top highlight
            g2.setColor(WALL_LIGHT);
            g2.drawLine(rx, ry + 1, rx + rw, ry + 1);
            // Mid shine
            g2.setColor(WALL_MID);
            g2.drawLine(rx, ry + 2, rx + rw, ry + 2);
        } else { // DOOR
            // Stone arch wings on each side
            int wingW = Math.max(3, rw / 5);
            g2.setColor(WALL_BASE);
            g2.fillRect(rx,              ry, wingW,          rh); // left wing
            g2.fillRect(rx + rw - wingW, ry, wingW,          rh); // right wing
            // Arch highlight
            g2.setColor(WALL_LIGHT);
            g2.drawLine(rx, ry + 1, rx + wingW, ry + 1);
            g2.drawLine(rx + rw - wingW, ry + 1, rx + rw, ry + 1);
            g2.setColor(WALL_DARK);
            g2.drawLine(rx, ry, rx + wingW, ry);
            g2.drawLine(rx + rw - wingW, ry, rx + rw, ry);
            // Door frame
            int frameX = rx + wingW, frameW = rw - wingW * 2;
            g2.setColor(DOOR_ARCH);
            g2.fillRect(frameX, ry, frameW, rh);
            // Wooden planks in the frame
            int plankH = Math.max(1, (rh - 2) / 3);
            for (int pi = 0; pi < 3; pi++) {
                Color pc = (pi % 2 == 0) ? DOOR_PLANK : DOOR_PLANK2;
                g2.setColor(pc);
                g2.fillRect(frameX + 1, ry + 1 + pi * plankH, frameW - 2, plankH);
            }
            // Central gap (the open passage darkness)
            int gapW = Math.max(2, frameW / 3);
            g2.setColor(DOOR_GAP);
            g2.fillRect(frameX + (frameW - gapW) / 2, ry, gapW, rh);
        }
    }

    /** Vertical wall bar (west or east edge of a tile). */
    private void drawVertWall(Graphics2D g2, int rx, int ry, int rw, int rh,
                              Dungeon.WallType type) {
        if (type == Dungeon.WallType.OPEN) return;

        if (type == Dungeon.WallType.WALL) {
            g2.setColor(WALL_SHADOW);
            g2.fillRect(rx, ry, 1, rh);
            g2.setColor(WALL_BASE);
            g2.fillRect(rx + 1, ry, rw - 2, rh);
            // Vertical mortar lines at thirds
            g2.setColor(WALL_DARK);
            int m1 = rx + rw / 3, m2 = rx + rw * 2 / 3;
            g2.drawLine(m1, ry, m1, ry + rh);
            g2.drawLine(m2, ry, m2, ry + rh);
            // Horizontal brick joints
            int brickH = Math.max(6, rh / 5);
            for (int by = ry; by < ry + rh; by += brickH) {
                g2.drawLine(rx + 1, by, m1 - 1, by);
            }
            for (int by = ry + brickH / 2; by < ry + rh; by += brickH) {
                g2.drawLine(m1 + 1, by, m2 - 1, by);
            }
            for (int by = ry; by < ry + rh; by += brickH) {
                g2.drawLine(m2 + 1, by, rx + rw - 2, by);
            }
            // Left highlight
            g2.setColor(WALL_LIGHT);
            g2.drawLine(rx + 1, ry, rx + 1, ry + rh);
            g2.setColor(WALL_MID);
            g2.drawLine(rx + 2, ry, rx + 2, ry + rh);
        } else { // DOOR
            int wingH = Math.max(3, rh / 5);
            g2.setColor(WALL_BASE);
            g2.fillRect(rx, ry,              rw, wingH);           // top wing
            g2.fillRect(rx, ry + rh - wingH, rw, wingH);           // bottom wing
            g2.setColor(WALL_LIGHT);
            g2.drawLine(rx + 1, ry, rx + 1, ry + wingH);
            g2.drawLine(rx + 1, ry + rh - wingH, rx + 1, ry + rh);
            g2.setColor(WALL_DARK);
            g2.drawLine(rx, ry, rx, ry + wingH);
            g2.drawLine(rx, ry + rh - wingH, rx, ry + rh);
            // Door frame
            int frameY = ry + wingH, frameH = rh - wingH * 2;
            g2.setColor(DOOR_ARCH);
            g2.fillRect(rx, frameY, rw, frameH);
            // Wooden planks
            int plankW = Math.max(1, (rw - 2) / 3);
            for (int pi = 0; pi < 3; pi++) {
                Color pc = (pi % 2 == 0) ? DOOR_PLANK : DOOR_PLANK2;
                g2.setColor(pc);
                g2.fillRect(rx + 1 + pi * plankW, frameY + 1, plankW, frameH - 2);
            }
            // Central gap
            int gapH = Math.max(2, frameH / 3);
            g2.setColor(DOOR_GAP);
            g2.fillRect(rx, frameY + (frameH - gapH) / 2, rw, gapH);
        }
    }

    private void drawNorthWall(Graphics2D g2, int sx, int sy, int tw, int th,
                               int wallThick, Dungeon.WallType type) {
        drawHorizWall(g2, sx, sy, tw, wallThick, type);
    }

    private void drawSouthWall(Graphics2D g2, int sx, int sy, int tw, int th,
                               int wallThick, Dungeon.WallType type) {
        drawHorizWall(g2, sx, sy + th - wallThick, tw, wallThick, type);
    }

    private void drawWestWall(Graphics2D g2, int sx, int sy, int tw, int th,
                              int wallThick, Dungeon.WallType type) {
        drawVertWall(g2, sx, sy, wallThick, th, type);
    }

    private void drawEastWall(Graphics2D g2, int sx, int sy, int tw, int th,
                              int wallThick, Dungeon.WallType type) {
        drawVertWall(g2, sx + tw - wallThick, sy, wallThick, th, type);
    }

    // ── Teleport animation (unchanged) ────────────────────────────────────────
    private void drawTeleportAnimation(Graphics2D g2, int tw, int th) {
        long elapsed = System.currentTimeMillis() - teleportStartTime;
        float p      = Math.min(1.0f, (float) elapsed / TELEPORT_DURATION_MS);
        int w = getWidth(), h = getHeight(), cx = w/2, cy = h/2;

        g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                            java.awt.RenderingHints.VALUE_ANTIALIAS_OFF);

        if (p < 0.12f) {
            float flashP = 1f - (p / 0.12f);
            g2.setColor(new Color(1, 0, 12)); g2.fillRect(0, 0, w, h);
            g2.setColor(new Color(255, 255, 255, (int)(flashP * 255))); g2.fillRect(0, 0, w, h);
            return;
        }

        g2.setColor(new Color(1, 0, 12)); g2.fillRect(0, 0, w, h);

        int bands = 28;
        float tunnelP = Math.min(1f, (p - 0.08f) / 0.80f);
        if (tunnelP > 0) {
            for (int i = bands; i >= 0; i--) {
                float t = ((float) i / bands + tunnelP * 2.4f) % 1.0f;
                float scale = t * t;
                int bw = (int)(w * scale), bh = (int)(h * scale);
                if (bw < 2 || bh < 2) continue;
                int bx = cx - bw/2, by = cy - bh/2;
                int colorIdx = (int)((i + elapsed / 80f)) % TUNNEL_COLORS.length;
                Color base = TUNNEL_COLORS[(colorIdx + TUNNEL_COLORS.length) % TUNNEL_COLORS.length];
                float brightness = (float) Math.sin(t * Math.PI);
                int alpha = Math.max(0, Math.min(220, (int)(brightness * 220)));
                int thickness = Math.max(2, (int)(bw * 0.04f));
                g2.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), alpha));
                g2.setStroke(new java.awt.BasicStroke(thickness));
                g2.drawRect(bx, by, bw, bh);
                if (i % 3 == 0 && bw > 6) {
                    Color c2 = TUNNEL_COLORS[(colorIdx + 2) % TUNNEL_COLORS.length];
                    g2.setColor(new Color(c2.getRed(), c2.getGreen(), c2.getBlue(), alpha/3));
                    g2.setStroke(new java.awt.BasicStroke(1));
                    g2.drawRect(bx + thickness, by + thickness, bw - thickness*2, bh - thickness*2);
                }
            }
        }

        float starP = Math.min(1f, Math.max(0f, (p - 0.05f) / 0.90f));
        if (starP > 0) {
            for (int i = 0; i < STAR_COUNT; i++) {
                float localP = Math.min(1f, Math.max(0f, starP - starOffset[i]));
                if (localP <= 0) continue;
                double angle = Math.toRadians(starAngle[i]);
                float dist = localP * localP * starSpeed[i];
                int sx = (int)(cx + Math.cos(angle) * dist);
                int sy = (int)(cy + Math.sin(angle) * dist * 0.72f);
                if (sx < 0 || sx >= w || sy < 0 || sy >= h) continue;
                float prevDist = Math.max(0, dist - starSpeed[i] * 0.06f);
                int ox = (int)(cx + Math.cos(angle) * prevDist);
                int oy = (int)(cy + Math.sin(angle) * prevDist * 0.72f);
                Color sc = Color.getHSBColor(starColor[i] / 360f, 0.7f, 1.0f);
                int alpha = Math.max(60, Math.min(255, (int)(200 * (1 - starP * 0.5f))));
                g2.setStroke(new java.awt.BasicStroke(1));
                g2.setColor(new Color(sc.getRed(), sc.getGreen(), sc.getBlue(), alpha/3));
                g2.drawLine(ox, oy, sx, sy);
                int size = dist > 300 ? 3 : dist > 150 ? 2 : 1;
                g2.setColor(new Color(sc.getRed(), sc.getGreen(), sc.getBlue(), alpha));
                g2.fillRect(sx - size/2, sy - size/2, size, size);
            }
        }

        if (p > 0.30f && p < 0.88f) {
            float noiseAlpha = Math.min(0.55f, (p - 0.30f) * 1.4f);
            g2.setColor(darkness((int)(noiseAlpha * 80)));
            for (int sy2 = 0; sy2 < h; sy2 += 2) g2.drawLine(0, sy2, w, sy2);
            java.util.Random noiseRng = new java.util.Random(elapsed / 50);
            for (int n = 0; n < 600; n++) {
                int nx = noiseRng.nextInt(w), ny = noiseRng.nextInt(h);
                Color tc = TUNNEL_COLORS[noiseRng.nextInt(TUNNEL_COLORS.length)];
                g2.setColor(new Color(tc.getRed(), tc.getGreen(), tc.getBlue(), (int)(noiseAlpha * 160)));
                g2.fillRect(nx, ny, 1, 1);
            }
        }

        if (tunnelP > 0) {
            float coreAlpha = (float) Math.sin(tunnelP * Math.PI) * 0.9f;
            int coreR = (int)(Math.min(w, h) * 0.03f * (1 - tunnelP * 0.7f)) + 2;
            for (int r = coreR * 6; r >= 1; r--) {
                g2.setColor(new Color(255, 255, 255, (int)(coreAlpha * 80 * (1f - (float)r/(coreR*6)))));
                g2.fillOval(cx-r, cy-r, r*2, r*2);
            }
            g2.setColor(new Color(255, 255, 255, (int)(coreAlpha * 220)));
            g2.fillRect(cx - coreR, cy - coreR, coreR*2, coreR*2);
        }

        if (p > 0.80f) {
            float fadeP = (p - 0.80f) / 0.20f;
            g2.setColor(darkness((int)(fadeP * fadeP * 255)));
            g2.fillRect(0, 0, w, h);
        }
        if (p > 0.96f) {
            float flashA = (float) Math.sin(((p - 0.96f) / 0.04f) * Math.PI);
            g2.setColor(new Color(255, 255, 255, (int)(flashA * 200)));
            g2.fillRect(0, 0, w, h);
        }
    }

}

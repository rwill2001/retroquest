package io.cannonforge.retroquest.core;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import io.cannonforge.retroquest.model.DungeonViewState;
import io.cannonforge.retroquest.model.MapData;
import io.cannonforge.retroquest.model.Player;

/**
 * Renders a scene to a numbered PNG sequence, offscreen, for the capability films.
 *
 * <p>This is the same shape as the seaglass-globe recorder: the <em>application</em> owns the
 * frame dump and a shell wrapper (see {@code tools/record.sh}) supplies the scene and hands the
 * frames to ffmpeg. Screen capture was never an option here — it records whatever the desktop is
 * doing, at whatever rate the encoder can keep up with, and it cannot render at a size the window
 * is not.
 *
 * <p>What makes this possible at all is that the three first-person renderers take
 * {@code paint(Graphics2D, W, H, Retroquest)} and read nothing from the frame but game
 * <em>state</em> — player, map, depth, view state. So the window is never shown
 * ({@link Retroquest#recordingOffscreen}) and {@link GamePanel} is painted straight into a
 * {@code BufferedImage} at whatever resolution the shot asks for, which is how a 1920×1080 frame
 * comes out of a panel that was packed for a much smaller window.
 *
 * <h2>Two clocks, deliberately</h2>
 * <ul>
 *   <li>{@link io.cannonforge.retroquest.model.DungeonCamera#update(long)} takes an explicit
 *       delta, so the camera ease is driven at exactly {@code 1000/fps} per frame and is
 *       reproducible take to take.</li>
 *   <li>Everything ambient — Rootvault's spores, Island 5's sconce flicker, the CRT phosphor —
 *       reads {@code System.currentTimeMillis()} directly. Those cannot be driven, so the loop
 *       <b>paces itself to the wall clock</b> by default: a 7-second shot takes 7 seconds to
 *       render and the ambient motion comes out at true speed. {@code --realtime off} renders as
 *       fast as it can and is only correct for a scene with nothing ambient in it.</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>
 *   --out DIR          where frame_%06d.png goes            (required)
 *   --w N --h N        frame size                           (default 1920x1080)
 *   --fps N            frames per second                    (default 30)
 *   --seconds N        clip length                          (default 6)
 *   --scene S          dungeon | overworld                  (default dungeon)
 *   --island NAME      overworld name; picks the renderer   (default lirandel)
 *   --dungeon NAME     authored dungeon under data/dungeons (dungeon scene)
 *   --level N          dungeon level                        (default 1)
 *   --mode M           force TOP_DOWN|WIREFRAME|TEXTURED|RAYCAST, overriding the island ladder
 *   --at X,Y           start tile; omit to use the map's authored entry
 *   --facing F         0=N 1=E 2=S 3=W                      (default 0)
 *   --move SCRIPT      "t:CMD" pairs, comma separated — see below
 *   --minimap on|off   first-person minimap                 (default off; it is UI, not scenery)
 *   --smooth on|off    DungeonCamera easing                 (default on)
 *   --realtime on|off  pace to the wall clock               (default on)
 * </pre>
 *
 * <p>A move script is comma-separated {@code seconds:command}, applied once when the clip passes
 * that time: {@code F} forward, {@code B} back, {@code L}/{@code R} turn, {@code @x,y} teleport.
 * Example: {@code --move "1:F,2:F,3:L,4:F"}. Commands that would walk into a wall are refused and
 * reported at the end of the run rather than silently producing a frame inside solid rock.
 */
public final class RetroRecorder {

    private RetroRecorder() {}

    private static final int[] STEP_DX = { 0, 1, 0, -1};
    private static final int[] STEP_DY = {-1, 0, 1,  0};

    /** One scripted input, and the clip time it fires at. */
    private record Move(double at, String cmd) {}

    public static void main(String[] args) throws Exception {
        // Defaults chosen so a shot file only has to say what it is *of*.
        String out = null, scene = "dungeon", island = "lirandel", dungeon = null, modeName = null;
        String moveScript = "", at = null, monsterId = null, overlay = null, townName = "moonhaven";
        io.cannonforge.retroquest.editor.RetroForge forge = null;
        javax.swing.JDialog forgeDialog = null;
        String editorName = null;
        int monsterLevel = 12;
        int xp = 0, damage = 0;
        boolean noEscape = false;
        String animation = null;
        int forgeZoom = 0;
        int w = 1920, h = 1080, fps = 30, level = 1, facing = 0;
        double seconds = 6, cameraRate = 1.0;
        int zoom = 0;
        boolean minimap = false, smooth = true, realtime = true, learnAll = false;

        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            switch (a) {
                case "--out"      -> out        = args[++i];
                case "--w"        -> w          = Integer.parseInt(args[++i]);
                case "--h"        -> h          = Integer.parseInt(args[++i]);
                case "--fps"      -> fps        = Integer.parseInt(args[++i]);
                case "--seconds"  -> seconds    = Double.parseDouble(args[++i]);
                case "--scene"    -> scene      = args[++i];
                case "--island"   -> island     = args[++i];
                case "--dungeon"  -> dungeon    = args[++i];
                case "--level"    -> level      = Integer.parseInt(args[++i]);
                case "--mode"     -> modeName   = args[++i];
                case "--at"       -> at         = args[++i];
                case "--facing"   -> facing     = Integer.parseInt(args[++i]);
                case "--move"     -> moveScript = args[++i];
                case "--minimap"  -> minimap    = "on".equals(args[++i]);
                case "--smooth"   -> smooth     = "on".equals(args[++i]);
                case "--realtime" -> realtime   = "on".equals(args[++i]);
                case "--camera-rate" -> cameraRate = Double.parseDouble(args[++i]);
                case "--zoom"     -> zoom       = Integer.parseInt(args[++i]);
                case "--monster"  -> monsterId  = args[++i];
                case "--monster-level" -> monsterLevel = Integer.parseInt(args[++i]);
                case "--overlay"  -> overlay    = args[++i];
                case "--town"     -> townName   = args[++i];
                case "--editor"   -> editorName = args[++i];
                case "--xp"       -> xp         = Integer.parseInt(args[++i]);
                case "--learn-all" -> learnAll  = "on".equals(args[++i]);
                case "--damage"   -> damage     = Integer.parseInt(args[++i]);
                case "--no-escape" -> noEscape  = "on".equals(args[++i]);
                case "--animation" -> animation = args[++i];
                case "--forge-zoom" -> forgeZoom = Integer.parseInt(args[++i]);
                default -> { System.err.println("recorder: unknown flag " + a); System.exit(2); }
            }
        }
        if (out == null) { System.err.println("recorder: --out is required"); System.exit(2); }

        File outDir = new File(out);
        if (!outDir.isDirectory() && !outDir.mkdirs()) {
            System.err.println("recorder: cannot create " + outDir); System.exit(1);
        }

        // ── Build the game with no window and no start menu ──────────────────
        Retroquest game = new Retroquest(false);
        game.recordingOffscreen = true;
        Player player = new Player();   // name is irrelevant: no HUD in these frames shows it
        game.setCurrentOverworldName(island);
        game.startGame(player, false);

        // The overworld the island ladder is read from has to be the one loaded, or a Bellorak
        // dungeon gets Lirandel's renderer and the shot is of the wrong thing entirely.
        if (!"lirandel".equals(island)) game.switchOverworld(island);
        game.setCurrentOverworldName(island);

        List<String> problems = new ArrayList<>();

        // Spells, HP and gear all key off level, and a fresh Player knows exactly one spell — so a
        // spellbook clip of a level-1 character is a clip of one line. XP is the public road to a
        // level; levelUp() is private.
        //
        // Combat defaults high because a level-1 player dies in about three rounds to anything
        // worth filming, and a clip that ends in the death overlay is not the clip that was asked
        // for. This has to resolve *before* the scene is built: startCombat snapshots the player.
        if (xp == 0 && "combat".equals(scene)) xp = 60000;
        if (xp > 0) { player.addXP(xp); player.heal(9999); }

        // Level alone does not open the spellbook: spells are learned one at a time, and a new
        // character starts knowing only the level-1 ones. A spellbook shot of that is a shot of a
        // single line, so --learn-all marks the whole book learned.
        if (learnAll) {
            int learned = 0;
            for (var sp : player.getKnownSpells())
                if (sp != null && player.learnSpell(sp.getName())) learned++;
            System.out.println("recorder: learned " + learned + " additional spells");
        }

        if ("dungeon".equals(scene)) {
            if (dungeon == null) { System.err.println("recorder: --dungeon is required"); System.exit(2); }
            File f = new File("data/dungeons/" + dungeon + "_" + level + ".rfmap");
            if (!f.exists()) { System.err.println("recorder: no such level " + f); System.exit(1); }
            MapData md = MapData.load(f);

            game.setInDungeon(true);
            game.setCurrentDepth(level);
            game.setCurrentMap(md.tiles);
            game.setCurrentDungeonMapData(md);
            game.getDungeonViewState().setAuthoredDungeonName(dungeon);

            // Same ladder the descent uses, and the same promotion away from TOP_DOWN, so a
            // recorded clip is the view a player would actually get here.
            DungeonViewState.Mode mode = (modeName != null)
                    ? DungeonViewState.Mode.valueOf(modeName)
                    : io.cannonforge.retroquest.controller.DungeonController.modeForIsland(island);
            if (modeName == null && mode == DungeonViewState.Mode.TOP_DOWN)
                mode = DungeonViewState.Mode.WIREFRAME;
            game.getDungeonViewState().setMode(mode);

            int sx = md.interiorEntryX, sy = md.interiorEntryY;
            if (at != null) { String[] p = at.split(","); sx = Integer.parseInt(p[0].trim()); sy = Integer.parseInt(p[1].trim()); }
            player.setPosition(sx, sy);
            player.setFacing(facing);

            // RAYCAST silently falls back to the textured renderer when the level's wall model
            // is not cell-based. Silently is exactly the problem: the clip still renders, still
            // looks deliberate, and is of the wrong renderer. Say so.
            if (mode == DungeonViewState.Mode.RAYCAST && !RaycastDungeonRenderer.canRender(game))
                problems.add("RAYCAST requested but canRender()==false — this clip is the TEXTURED fallback");
        } else if ("procedural".equals(scene)) {
            // Lirandel authors no dungeons — which is exactly why it is the TOP_DOWN island, so
            // the top-down grid can only be recorded from a generated map. Mirrors
            // DungeonController.enterDungeon() minus the entry animation and the descent sound.
            game.getDungeonViewState().setAuthoredDungeonName(null);
            game.setCurrentDungeonMapData(null);
            game.setInDungeon(true);
            game.setCurrentDepth(level);
            game.setCurrentMap(io.cannonforge.retroquest.model.Dungeon.generate(level));

            DungeonViewState.Mode mode = (modeName != null)
                    ? DungeonViewState.Mode.valueOf(modeName)
                    : io.cannonforge.retroquest.controller.DungeonController.modeForIsland(island);
            game.getDungeonViewState().setMode(mode);

            // Dungeon.generate takes no seed, so two takes of this shot are two different
            // dungeons. That is fine for footage and useless for a diff — re-record until the
            // layout suits, do not expect to reproduce one.
            if (at != null) { String[] p = at.split(","); player.setPosition(Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim())); }
            else player.setPosition(io.cannonforge.retroquest.model.Dungeon.WIDTH / 2,
                                    io.cannonforge.retroquest.model.Dungeon.HEIGHT / 2);
            player.setFacing(facing);
        } else if ("combat".equals(scene)) {
            // CombatOverlay is painted by GamePanel like any other overlay, so a combat clip is
            // the same trick as a dungeon one: start it, then paint the panel every frame. The
            // move script sends keystrokes instead of steps — A attack, F flee, C spellbook.
            game.setInDungeon(false);
            io.cannonforge.retroquest.model.Monster m = (monsterId != null)
                    ? io.cannonforge.retroquest.registry.MonsterRegistry.getById(monsterId)
                    : io.cannonforge.retroquest.registry.MonsterRegistry.getRandomMonster(monsterLevel);
            if (m == null) { System.err.println("recorder: no monster '" + monsterId + "'"); System.exit(1); }
            // Damage is applied after the heal, and before startCombat snapshots the fight:
            // the low-HP shot needs a red bar on the first frame, not after three rounds.
            if (damage > 0) player.takeDamage(damage);
            game.getGamePanel().startCombat(m, noEscape, (won, gold, loot, up, lvl) -> { });
        } else if ("forge".equals(scene)) {
            // Same trick as the game, one layer out: RetroForge is a JFrame that is never shown,
            // sized and validated by hand, and painted through its root pane — which is what
            // carries the menu bar as well as the content. This is the only way to film the
            // editor without handing the operator's desktop over to a maximised window.
            forge = new io.cannonforge.retroquest.editor.RetroForge(false);
            // pack() before sizing, and this order matters. A frame that was never shown has no
            // peer, and without a peer validate() does not lay out its descendants — the first
            // take of this shot came back as a single flat grey rectangle. pack() calls addNotify,
            // which realises the window without displaying it; the layout then runs for real.
            forge.pack();
            forge.setSize(w, h);
            forge.validate();
            // The canvas draws a fixed 32px tile, so a native 1920x1080 editor frame leaves the map
            // small in a lot of chrome. Zooming the canvas fills it *without* scaling the render —
            // which is the whole point: every upscale that is not an exact integer duplicates some
            // source pixels twice and others three times, and on monospace UI that shimmers.
            for (int z = 0; z < forgeZoom; z++)
                forge.getCanvas().zoom(1, forge.getCanvas().getWidth() / 2, forge.getCanvas().getHeight() / 2);

            // A sub-editor is a modal dialog off the EDITORS menu. Built with show=false it is
            // laid out but never displayed, and the same pack/size/validate dance applies — so
            // the thing painted each frame becomes the dialog rather than the editor behind it.
            if (editorName != null) {
                forgeDialog = switch (editorName) {
                    case "item"    -> new io.cannonforge.retroquest.editor.ItemEditorDialog(forge, false);
                    case "quest"   -> new io.cannonforge.retroquest.editor.QuestEditorDialog(forge, false);
                    case "monster" -> new io.cannonforge.retroquest.editor.MonsterEditorDialog(forge, false);
                    case "tile"    -> new io.cannonforge.retroquest.editor.TileEditor(forge, false);
                    case "image"   -> new io.cannonforge.retroquest.editor.ImageEditor(null, false);
                    case "dialogue" -> {
                        // The tree editor needs an NPC, and only one with an authored tree shows
                        // the nodes, choices and conditions the shot is about. An empty template
                        // would render, and would be a picture of nothing.
                        MapData td = MapData.load(new File("data/towns/" + townName + ".rfmap"));
                        io.cannonforge.retroquest.model.NPC pick = null;
                        for (var n : td.npcs) if (n.getDialogueTree() != null) { pick = n; break; }
                        if (pick == null) {
                            System.err.println("recorder: no NPC with a dialogue tree in " + townName);
                            System.exit(1);
                        }
                        yield new io.cannonforge.retroquest.editor.DialogueTreeEditor(forge, pick, false);
                    }
                    default -> null;
                };
                if (forgeDialog == null) {
                    System.err.println("recorder: unknown --editor " + editorName); System.exit(2);
                }
                forgeDialog.pack();
                forgeDialog.setSize(w, h);
                forgeDialog.validate();
            }
        } else if ("animation".equals(scene)) {
            // The full-screen cinematics short-circuit GamePanel.paintComponent, so filming one
            // is just starting it and painting the panel. They are wall-clock driven and have no
            // progress input, so this scene only works with --realtime on and with
            // -Dretroquest.skipAnimations unset — record.sh sets SKIP_ANIMATIONS=off for it.
            game.setInDungeon(false);
            switch (animation == null ? "entry" : animation) {
                case "entry"   -> game.getGamePanel().startDungeonEntryAnimation();
                case "descent" -> game.getGamePanel().startDungeonDescentAnimation(2);
                case "exit"    -> game.getGamePanel().startDungeonExitAnimation();
                default -> { System.err.println("recorder: unknown --animation " + animation); System.exit(2); }
            }
            if (io.cannonforge.retroquest.animation.AnimationSpeed.SKIP)
                problems.add("animations are being skipped (-Dretroquest.skipAnimations) — this clip "
                           + "is one frame of a cinematic, not the cinematic");
        } else if ("chargen".equals(scene) || "intro".equals(scene)) {
            // Act 1 is two modal JDialogs rather than overlays. Built with show=false they lay out
            // but never display, so the same pack/size/validate/paint path works — and the public
            // constructor, which blocks its caller until a player dismisses the dialog, is avoided.
            forgeDialog = "chargen".equals(scene)
                    ? new io.cannonforge.retroquest.dialog.CharacterCreationDialog(game, false)
                    : new io.cannonforge.retroquest.dialog.IntroCinematicDialog(game, player, false);
            forgeDialog.pack();
            forgeDialog.setSize(w, h);
            forgeDialog.validate();
        } else if ("town".equals(scene)) {
            // Town interiors are their own .rfmap, loaded on entry. Constructing a Town loads it;
            // the door coordinates only matter for walking back out, which no clip does.
            Town t = new Town(townName, 0, 0);
            game.setInDungeon(false);
            game.setCurrentDepth(0);
            game.currentTown = t;
            game.setCurrentMap(t.getInteriorMap());
            if (at != null) { String[] p = at.split(","); player.setPosition(Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim())); }
            else player.setPosition(t.getInteriorEntryX(), t.getInteriorEntryY());
            player.setFacing(facing);
        } else if ("overworld".equals(scene)) {
            game.setInDungeon(false);
            game.setCurrentDepth(0);
            if (at != null) { String[] p = at.split(","); player.setPosition(Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim())); }
            player.setFacing(facing);
        } else {
            System.err.println("recorder: unknown --scene " + scene); System.exit(2);
        }

        // An overlay is opened on top of whatever scene was just built, because that is how the
        // game shows it: the shop is drawn over the town you are standing in. Opening one directly
        // is the only way to film it — most need an NPC, a tile or a quest state in front of you
        // before a keystroke can reach them.
        if (overlay != null) {
            GamePanel gp = game.getGamePanel();
            switch (overlay) {
                case "shop" -> gp.getShopOverlay().open(java.util.List.of(
                        "healing_potion", "iron_sword", "leather_armor", "chain_mail",
                        "steel_sword", "ring_protection"));
                case "inventory" -> gp.getInventoryOverlay().open();
                // Outside combat the book filters to map-usable spells, which is nine of them.
                // The combat-mode book is the whole list, and combat is where the script's
                // spellbook shot actually happens.
                case "spellbook" -> {
                    if ("combat".equals(scene)) gp.getSpellbookOverlay().open(true, gp.getCombatOverlay());
                    else                        gp.getSpellbookOverlay().open();
                }
                case "questlog"  -> gp.getQuestLogOverlay().open();
                case "casino"    -> gp.getCasinoOverlay().open();
                case "arena"     -> gp.getArenaOverlay().open();
                case "depths"    -> gp.getDepthsOverlay().open();
                case "sky"       -> gp.getSkyGamesOverlay().open();
                case "nature"    -> gp.getNatureGamesOverlay().open();
                case "memory"    -> gp.getMemoryGamesOverlay().open();
                case "war"       -> gp.getWarGamesOverlay().open();
                // One of the seven overworld hunts. --dungeon carries the hunt id, since a hunt
                // scene has no dungeon of its own: e.g. --overlay hunt --dungeon spore_lure.
                case "hunt" -> {
                    if (!gp.openHunt(dungeon, null, () -> { })) {
                        System.err.println("recorder: --dungeon must name a hunt "
                            + "(snare_line, ember_flush, skyfish_net, spore_lure, "
                            + "pressure_line, remembered_meal, ration_run)");
                        System.exit(2);
                    }
                }
                // The card a bottom-of-dungeon boss leaves behind. Which one is decided by
                // --dungeon, so every entry in data/dungeon_bosses.json can be read back
                // without fighting down to it first.
                case "revelation" -> {
                    var boss = io.cannonforge.retroquest.registry.DungeonBossRegistry
                            .findByDungeon(dungeon);
                    if (boss == null || boss.revelation == null) {
                        System.err.println("recorder: no boss revelation for --dungeon " + dungeon);
                        System.exit(2);
                    }
                    var bgod = boss.godOrNull();
                    java.awt.Color acc = (bgod != null)
                            ? io.cannonforge.retroquest.overlay.DivineAudienceOverlay.colorFor(bgod)
                            : io.cannonforge.retroquest.overlay.OverlayTheme.CYAN_ACC;
                    java.util.List<String> flines =
                            new java.util.ArrayList<>(boss.revelation.footerLines != null
                                    ? boss.revelation.footerLines : java.util.List.of());
                    if (bgod != null && boss.favor > 0) {
                        flines.add("+" + boss.favor + " " + bgod.displayName + " favor");
                    }
                    gp.openRevelation(boss.revelation.title, boss.revelation.subtitle, acc,
                            boss.revelation.body,
                            new io.cannonforge.retroquest.overlay.RevelationOverlay.LinesFooter(
                                    boss.revelation.footerHeading, flines, acc),
                            () -> { });
                }
                case "divine"    -> gp.getDivineAudienceOverlay().open(
                        io.cannonforge.retroquest.model.God.LIRANDEL,
                        "You are the one soul none of us can touch. That is why we all want you.",
                        () -> { });
                case "dialogue" -> {
                    // Any NPC will render, but one with an authored tree is the only kind that
                    // shows branching choices — which is the entire subject of the shot.
                    io.cannonforge.retroquest.model.NPC pick = null;
                    java.util.List<io.cannonforge.retroquest.model.NPC> pool =
                            (game.currentTown != null) ? game.currentTown.getNpcs() : java.util.List.of();
                    for (var n : pool) if (n.getDialogueTree() != null) { pick = n; break; }
                    if (pick == null && !pool.isEmpty()) {
                        pick = pool.get(0);
                        problems.add("no NPC on this map has a dialogue tree — filming a flat one");
                    }
                    if (pick == null) { System.err.println("recorder: no NPCs to talk to"); System.exit(1); }
                    gp.getDialogueOverlay().open(pick);
                }
                default -> { System.err.println("recorder: unknown --overlay " + overlay); System.exit(2); }
            }
            // Overlays animate in and then sit still; the overlay repaint timer is what the game
            // uses to keep them moving, and the render loop is doing that job here instead.
            game.startOverlayRepaintTimer();
        }

        // The tile viewport is a tile *count* (24x18, stepping by 2 down to 10), so zooming in
        // is how a top-down shot fills the frame: fewer tiles, each one bigger. Without it the
        // torch-lit island of explored map sits small in the middle of a 16:9 black frame.
        for (int z = 0; z < zoom; z++) game.zoomIn();

        game.getDungeonViewState().setMinimapVisible(minimap);
        game.getDungeonViewState().getCamera().setSmooth(smooth);
        game.getDungeonViewState().getCamera().snapTo(player.getX(), player.getY(), player.getFacing());
        if (game.isInDungeon()) game.getDungeonController().revealVisibleArea();
        game.updateCamera();

        // ── The panel is painted at the shot's resolution, not the window's ──
        GamePanel panel = game.getGamePanel();
        panel.setSize(w, h);
        panel.doLayout();

        // What actually gets painted each frame. The editor scene swaps the game panel out for
        // RetroForge's root pane; everything downstream is identical.
        javax.swing.JComponent target = (forgeDialog != null) ? forgeDialog.getRootPane()
                                     : (forge != null)       ? forge.getRootPane()
                                     : panel;

        List<Move> moves = parseMoves(moveScript);
        int nextMove = 0;

        int totalFrames = (int) Math.round(seconds * fps);
        long frameMs = Math.round(1000.0 / fps);
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);

        System.out.printf("recorder: %s %dx%d @%dfps  %.1fs = %d frames  scene=%s%s mode=%s%n",
                outDir.getName(), w, h, fps, seconds, totalFrames, scene,
                dungeon == null ? "" : "/" + dungeon + "_" + level,
                game.getDungeonViewState().getMode());

        long wallStart = System.currentTimeMillis();

        for (int i = 0; i < totalFrames; i++) {
            double t = i / (double) fps;

            while (nextMove < moves.size() && moves.get(nextMove).at() <= t) {
                String cmd = moves.get(nextMove).cmd();
                // With an overlay open the script is keystrokes; otherwise it is movement.
                String why = (forge != null) ? forgeCmd(forge, cmd)
                        : (forgeDialog != null) ? dialogCmd(forgeDialog, cmd)
                        : (overlay != null || "combat".equals(scene)) ? sendKey(panel, cmd)
                        : applyMove(game, player, cmd);
                if (why != null) problems.add(String.format("t=%.2fs %s: %s", t, moves.get(nextMove).cmd(), why));
                nextMove++;
            }

            var cam = game.getDungeonViewState().getCamera();
            cam.moveTo(player.getX(), player.getY());
            cam.turnTo(player.getFacing());
            // The camera's own MOVE_MS/TURN_MS are 130/150ms — tuned so a player can spam a
            // turn key, which at 30fps is about five frames and reads as a snap on camera.
            // Feeding update() a fraction of the frame delta eases the *same* interpolation more
            // slowly, so a turn plays through its intermediate angles long enough to see. This
            // changes only how fast the picture catches up; the game's tuning is untouched, and
            // the frames are of real renderer output at real off-axis angles.
            cam.update(Math.max(1L, Math.round(frameMs * cameraRate)));

            // Painting happens on the EDT, not here.
            //
            // Swing timers mutate the component tree from the EDT while this loop runs: the intro
            // crawl's typewriter, the overlay repaint timer, and — at the end of the crawl —
            // startGame(), which tears down and re-packs every panel. Painting a container from
            // this thread at the same time races for the AWT tree lock, and the intro shot hung
            // the whole recorder until this was serialised. invokeAndWait puts every frame in the
            // same queue as Swing's own work, which is where it always belonged.
            final Graphics2D g2 = img.createGraphics();
            javax.swing.SwingUtilities.invokeAndWait(() -> target.paint(g2));
            g2.dispose();

            ImageIO.write(img, "png", new File(outDir, String.format("frame_%06d.png", i + 1)));

            if (realtime) {
                long due = wallStart + (long) ((i + 1) * frameMs);
                long sleep = due - System.currentTimeMillis();
                if (sleep > 0) Thread.sleep(sleep);
            }
        }

        long took = System.currentTimeMillis() - wallStart;
        System.out.printf("recorder: wrote %d frames in %.1fs%n", totalFrames, took / 1000.0);
        for (String p : problems) System.out.println("recorder: WARNING — " + p);
        // Timers (sound, overlay repaint) are non-daemon; nothing else is going to stop them.
        System.exit(problems.isEmpty() ? 0 : 3);
    }

    /**
     * Sends one keystroke to the combat overlay. The overlay reads keys, not method calls, so a
     * combat clip is scripted the same way a player would play it — { A} attack, { F}
     * flee, { C} spellbook. The panel is the event source purely because KeyEvent demands a
     * Component; nothing is focused and no window exists.
     */
    /**
     * Applies one scripted editor command.
     *
     * <p>{@code T:PENCIL} picks a tool, {@code B:x} sets the brush tile, and {@code P:tx,ty} /
     * {@code D:x1,y1,x2,y2} click and drag on the canvas. The click is a real {@link
     * java.awt.event.MouseEvent} dispatched to {@link io.cannonforge.retroquest.editor.MapCanvas},
     * so it goes through the editor's own listeners and paints tiles for real — undo stack,
     * spawn-difficulty overlay and all. Coordinates are <em>viewport</em> tiles (0–33, 0–20), not
     * world tiles, so a shot does not have to know where the camera is parked.
     */
    private static String forgeCmd(io.cannonforge.retroquest.editor.RetroForge forge, String cmd) {
        var canvas = forge.getCanvas();
        int ts = canvas.getTileSize();
        int c = cmd.indexOf(':');
        String verb = (c < 0 ? cmd : cmd.substring(0, c)).toUpperCase();
        String rest = (c < 0) ? "" : cmd.substring(c + 1);
        try {
            switch (verb) {
                case "T" -> forge.selectTool(io.cannonforge.retroquest.editor.Tool.valueOf(rest.toUpperCase()));
                case "B" -> canvas.setBrush(rest.charAt(0));
                // The overlay alpha is diff*2, so the default value of 5 paints something very
                // nearly invisible. A shot about the overlay has to paint a value you can see.
                case "S" -> canvas.setSpawnDifficultyValue(Integer.parseInt(rest.trim()));
                case "P" -> {
                    String[] p = rest.split(",");
                    click(canvas, Integer.parseInt(p[0].trim()) * ts + ts / 2,
                                  Integer.parseInt(p[1].trim()) * ts + ts / 2);
                }
                case "D" -> {
                    String[] p = rest.split(",");
                    int x1 = Integer.parseInt(p[0].trim()), y1 = Integer.parseInt(p[1].trim());
                    int x2 = Integer.parseInt(p[2].trim()), y2 = Integer.parseInt(p[3].trim());
                    drag(canvas, x1 * ts + ts / 2, y1 * ts + ts / 2, x2 * ts + ts / 2, y2 * ts + ts / 2);
                }
                default -> { return "unknown editor command '" + verb + "'"; }
            }
        } catch (IllegalArgumentException | IndexOutOfBoundsException ex) {
            return "bad editor command '" + cmd + "': " + ex.getMessage();
        }
        canvas.repaint();
        return null;
    }

    private static void click(java.awt.Component c, int x, int y) {
        long t = System.currentTimeMillis();
        c.dispatchEvent(new java.awt.event.MouseEvent(c, java.awt.event.MouseEvent.MOUSE_PRESSED,
                t, java.awt.event.InputEvent.BUTTON1_DOWN_MASK, x, y, 1, false,
                java.awt.event.MouseEvent.BUTTON1));
        c.dispatchEvent(new java.awt.event.MouseEvent(c, java.awt.event.MouseEvent.MOUSE_RELEASED,
                t + 1, 0, x, y, 1, false, java.awt.event.MouseEvent.BUTTON1));
    }

    /** Press, then a run of drags along the line, then release — a stroke, not a teleport. */
    private static void drag(java.awt.Component c, int x1, int y1, int x2, int y2) {
        long t = System.currentTimeMillis();
        int steps = Math.max(1, (Math.abs(x2 - x1) + Math.abs(y2 - y1)) / 8);
        c.dispatchEvent(new java.awt.event.MouseEvent(c, java.awt.event.MouseEvent.MOUSE_PRESSED,
                t, java.awt.event.InputEvent.BUTTON1_DOWN_MASK, x1, y1, 1, false,
                java.awt.event.MouseEvent.BUTTON1));
        for (int i = 1; i <= steps; i++) {
            int x = x1 + (x2 - x1) * i / steps, y = y1 + (y2 - y1) * i / steps;
            c.dispatchEvent(new java.awt.event.MouseEvent(c, java.awt.event.MouseEvent.MOUSE_DRAGGED,
                    t + i, java.awt.event.InputEvent.BUTTON1_DOWN_MASK, x, y, 0, false));
        }
        c.dispatchEvent(new java.awt.event.MouseEvent(c, java.awt.event.MouseEvent.MOUSE_RELEASED,
                t + steps + 1, 0, x2, y2, 1, false, java.awt.event.MouseEvent.BUTTON1));
    }

    /**
     * Applies one scripted command to a dialog being filmed.
     *
     * <p>{@code BTN:REROLL} finds the button whose label contains that text and clicks it, which
     * is how a character-creation shot rerolls without reaching into the dialog's private methods.
     * Anything else is treated as a keystroke and dispatched to the dialog's focus owner.
     */
    private static String dialogCmd(javax.swing.JDialog dlg, String cmd) {
        if (cmd.toUpperCase().startsWith("BTN:")) {
            String want = cmd.substring(4).trim().toUpperCase();
            javax.swing.JButton b = findButton(dlg.getRootPane(), want);
            if (b == null) return "no button matching '" + want + "'";
            b.doClick();
            return null;
        }
        return "unsupported dialog command '" + cmd + "'";
    }

    /** Depth-first search for a button whose text contains {@code want}, case-insensitively. */
    private static javax.swing.JButton findButton(java.awt.Container root, String want) {
        for (java.awt.Component c : root.getComponents()) {
            if (c instanceof javax.swing.JButton b && b.getText() != null
                    && b.getText().toUpperCase().contains(want)) return b;
            if (c instanceof java.awt.Container inner) {
                javax.swing.JButton found = findButton(inner, want);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static String sendKey(GamePanel panel, String cmd) {
        // The venue menus are driven with Up/Down and Enter, not letters, so a shot that has to
        // get past one needs to name those keys.
        int code; char c;
        switch (cmd.toUpperCase()) {
            case "ENTER" -> { code = java.awt.event.KeyEvent.VK_ENTER; c = '\n'; }
            case "ESC"   -> { code = java.awt.event.KeyEvent.VK_ESCAPE; c = 27; }
            case "SPACE" -> { code = java.awt.event.KeyEvent.VK_SPACE; c = ' '; }
            case "UP"    -> { code = java.awt.event.KeyEvent.VK_UP;    c = java.awt.event.KeyEvent.CHAR_UNDEFINED; }
            case "DOWN"  -> { code = java.awt.event.KeyEvent.VK_DOWN;  c = java.awt.event.KeyEvent.CHAR_UNDEFINED; }
            case "LEFT"  -> { code = java.awt.event.KeyEvent.VK_LEFT;  c = java.awt.event.KeyEvent.CHAR_UNDEFINED; }
            case "RIGHT" -> { code = java.awt.event.KeyEvent.VK_RIGHT; c = java.awt.event.KeyEvent.CHAR_UNDEFINED; }
            default -> {
                if (cmd.length() != 1)
                    return "an overlay command is one key or a named key, not '" + cmd + "'";
                c = Character.toUpperCase(cmd.charAt(0));
                code = java.awt.event.KeyEvent.getExtendedKeyCodeForChar(c);
            }
        }
        var e = new java.awt.event.KeyEvent(panel, java.awt.event.KeyEvent.KEY_PRESSED,
                System.currentTimeMillis(), 0, code, c);
        // Topmost-first, mirroring the game's own input routing: the spellbook sits over combat,
        // so a J typed with both open belongs to the book. J/K move the selection in all of these,
        // which is what a list-scrolling shot needs.
        if (panel.getSpellbookOverlay().isActive()) { panel.getSpellbookOverlay().handleKey(e); return null; }
        if (panel.getDialogueOverlay().isActive())  { panel.getDialogueOverlay().handleKey(e);  return null; }
        if (panel.getShopOverlay().isActive())      { panel.getShopOverlay().handleKey(e);      return null; }
        if (panel.getInventoryOverlay().isActive()) { panel.getInventoryOverlay().handleKey(e); return null; }
        if (panel.getCombatOverlay().isActive())    { panel.getCombatOverlay().handleKey(e);    return null; }
        // The venue overlays open on a menu of their games; a number key is what picks one, and
        // "mid-action, never a menu" is what the script asks these shots for.
        if (panel.getCasinoOverlay().isActive())      { panel.getCasinoOverlay().handleKey(e);      return null; }
        if (panel.getArenaOverlay().isActive())       { panel.getArenaOverlay().handleKey(e);       return null; }
        if (panel.getDepthsOverlay().isActive())      { panel.getDepthsOverlay().handleKey(e);      return null; }
        if (panel.getSkyGamesOverlay().isActive())    { panel.getSkyGamesOverlay().handleKey(e);    return null; }
        if (panel.getNatureGamesOverlay().isActive()) { panel.getNatureGamesOverlay().handleKey(e); return null; }
        if (panel.getMemoryGamesOverlay().isActive()) { panel.getMemoryGamesOverlay().handleKey(e); return null; }
        if (panel.getWarGamesOverlay().isActive())    { panel.getWarGamesOverlay().handleKey(e);    return null; }
        if (panel.isHuntActive())                     { panel.handleHuntKey(e);                     return null; }
        if (panel.getQuestLogOverlay().isActive())    { panel.getQuestLogOverlay().handleKey(e);    return null; }
        return "no overlay is open to receive '" + cmd + "'";
    }

    /**
     * Parses a move script into timed commands.
     *
     * <p>Commands are separated by {@code ;} if the script contains one, and by {@code ,}
     * otherwise. Both exist because commas are also coordinate separators: {@code D:4,3,12,7}
     * split on commas becomes four unparseable fragments, which is exactly how the first editor
     * shot came back with nothing painted and four warnings. A script that needs coordinates uses
     * semicolons; the simple movement scripts that predate them keep working unchanged.
     */
    private static List<Move> parseMoves(String script) {
        List<Move> out = new ArrayList<>();
        if (script == null || script.isBlank()) return out;
        for (String part : script.split(script.contains(";") ? ";" : ",")) {
            String s = part.trim();
            if (s.isEmpty()) continue;
            int c = s.indexOf(':');
            if (c < 0) { System.err.println("recorder: bad move '" + s + "', want t:CMD"); continue; }
            out.add(new Move(Double.parseDouble(s.substring(0, c).trim()), s.substring(c + 1).trim()));
        }
        out.sort((a, b) -> Double.compare(a.at(), b.at()));
        return out;
    }

    /** Applies one scripted command. Returns null on success, or why it was refused. */
    private static String applyMove(Retroquest game, Player p, String cmd) {
        switch (cmd.toUpperCase()) {
            // A single letter in a combat clip is a keystroke, not a step.
            case "KEY" -> { return null; }
            case "L" -> { p.setFacing(p.getFacing() + 3); return null; }
            case "R" -> { p.setFacing(p.getFacing() + 1); return null; }
            case "F", "B" -> {
                int dir = "F".equalsIgnoreCase(cmd) ? p.getFacing() : (p.getFacing() + 2) & 3;
                int nx = p.getX() + STEP_DX[dir], ny = p.getY() + STEP_DY[dir];
                char[][] map = game.getCurrentMap();
                if (ny < 0 || ny >= map.length || nx < 0 || nx >= map[ny].length)
                    return "off the edge of the map at " + nx + "," + ny;
                if (!game.isWalkable(map[ny][nx]))
                    return "tile '" + map[ny][nx] + "' at " + nx + "," + ny + " is not walkable";
                p.setPosition(nx, ny);
                if (game.isInDungeon()) game.getDungeonController().revealVisibleArea();
                return null;
            }
            default -> {
                if (cmd.startsWith("@")) {
                    String[] xy = cmd.substring(1).split(",");
                    p.setPosition(Integer.parseInt(xy[0].trim()), Integer.parseInt(xy[1].trim()));
                    if (game.isInDungeon()) game.getDungeonController().revealVisibleArea();
                    return null;
                }
                return "unknown command";
            }
        }
    }
}

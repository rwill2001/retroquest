import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * Places overworld interactive features (shrines, treasure, ambush, geysers, wind currents,
 * talking trees, memory echoes, sunken chests, battle standards) on all island overworld maps.
 *
 * Also generates placeholder sprites for the new tile types.
 *
 * Run from project root:
 *   javac -encoding UTF-8 tools/OverworldFeaturePatch.java -d tools/
 *   java -cp tools OverworldFeaturePatch
 */
public class OverworldFeaturePatch {

    static final String SPRITE_DIR = "src/main/resources/tiles/overworld/";
    static final String MAP_DIR    = "data/overworlds/";

    // Tile chars
    static final char SHRINE        = '\uE0C0';
    static final char DIG_SITE      = '\uE0C1';
    static final char AMBUSH        = '\uE0C2';
    static final char GEYSER        = '\uE0C3';
    static final char WIND_CURRENT  = '\uE0C4';
    static final char ANCIENT_TREE  = '\uE0C5';
    static final char MEMORY_ECHO   = '\uE0C6';
    static final char SUNKEN_CHEST  = '\uE0C7';
    static final char BATTLE_STD    = '\uE0C8';

    // ── Sprites ──────────────────────────────────────────────────────────────

    public static void main(String[] args) throws Exception {
        System.out.println("=== Generating feature sprites ===");
        generateSprites();

        System.out.println("\n=== Patching Lirandel ===");
        patchLirandel();

        System.out.println("\n=== Patching Pyralis ===");
        patchPyralis();

        System.out.println("\n=== Patching Zephyrion ===");
        patchZephyrion();

        System.out.println("\n=== Patching Sylvandar ===");
        patchSylvandar();

        System.out.println("\n=== Patching Thalorax ===");
        patchThalorax();

        System.out.println("\n=== Patching Umbryn ===");
        patchUmbryn();

        System.out.println("\n=== Patching Bellorak ===");
        patchBellorak();

        System.out.println("\nDone! All overworld maps patched.");
    }

    static void generateSprites() throws Exception {
        new File(SPRITE_DIR).mkdirs();
        save(createShrine(),        "hidden_shrine");
        save(createDigSite(),       "dig_site");
        save(createAmbush(),        "ambush_point");
        save(createGeyser(),        "lava_geyser");
        save(createWindCurrent(),   "wind_current");
        save(createAncientTree(),   "ancient_tree");
        save(createMemoryEcho(),    "memory_echo");
        save(createSunkenChest(),   "sunken_chest");
        save(createBattleStandard(),"battle_standard");
    }

    static void save(BufferedImage img, String name) throws Exception {
        File f = new File(SPRITE_DIR + name + ".png");
        ImageIO.write(img, "PNG", f);
        System.out.println("  wrote " + f.getPath());
    }

    // Stone altar with faint glow
    static BufferedImage createShrine() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        // Grass background
        g.setColor(new Color(34, 80, 34));
        g.fillRect(0, 0, 32, 32);
        // Stone base
        g.setColor(new Color(130, 125, 110));
        g.fillRect(8, 18, 16, 12);
        // Stone pillar
        g.setColor(new Color(150, 145, 130));
        g.fillRect(11, 8, 10, 12);
        // Top capstone
        g.setColor(new Color(160, 155, 140));
        g.fillRect(9, 6, 14, 4);
        // Divine glow
        g.setColor(new Color(255, 220, 120, 80));
        g.fillOval(10, 4, 12, 12);
        // Symbol on pillar
        g.setColor(new Color(200, 180, 100));
        g.fillRect(14, 10, 4, 4);
        g.dispose();
        return img;
    }

    // Disturbed earth mound
    static BufferedImage createDigSite() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(34, 80, 34));
        g.fillRect(0, 0, 32, 32);
        // Dirt mound
        g.setColor(new Color(100, 75, 40));
        g.fillOval(6, 12, 20, 16);
        g.setColor(new Color(120, 90, 50));
        g.fillOval(8, 14, 16, 12);
        // Scattered stones
        g.setColor(new Color(140, 130, 110));
        g.fillRect(5, 22, 3, 3);
        g.fillRect(24, 20, 3, 3);
        g.fillRect(14, 10, 2, 2);
        // X mark
        g.setColor(new Color(180, 60, 40));
        g.drawLine(13, 16, 19, 22);
        g.drawLine(19, 16, 13, 22);
        g.dispose();
        return img;
    }

    // Skull/bones warning marker
    static BufferedImage createAmbush() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(34, 80, 34));
        g.fillRect(0, 0, 32, 32);
        // Skull
        g.setColor(new Color(220, 210, 190));
        g.fillOval(10, 8, 12, 12);
        // Eye sockets
        g.setColor(new Color(30, 20, 20));
        g.fillOval(12, 12, 3, 3);
        g.fillOval(17, 12, 3, 3);
        // Jaw
        g.setColor(new Color(200, 190, 170));
        g.fillRect(12, 18, 8, 3);
        // Bones crossed
        g.setColor(new Color(210, 200, 180));
        g.drawLine(6, 24, 26, 28);
        g.drawLine(6, 28, 26, 24);
        g.dispose();
        return img;
    }

    // Cracked earth with fire
    static BufferedImage createGeyser() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        // Dark volcanic ground
        g.setColor(new Color(50, 35, 25));
        g.fillRect(0, 0, 32, 32);
        // Cracks
        g.setColor(new Color(200, 100, 20));
        g.drawLine(16, 4, 16, 28);
        g.drawLine(10, 16, 22, 16);
        g.drawLine(8, 8, 24, 24);
        // Central glow
        g.setColor(new Color(255, 160, 40, 160));
        g.fillOval(11, 11, 10, 10);
        g.setColor(new Color(255, 220, 80, 120));
        g.fillOval(13, 13, 6, 6);
        // Steam wisps
        g.setColor(new Color(200, 200, 200, 80));
        g.fillOval(12, 2, 4, 6);
        g.fillOval(17, 4, 3, 5);
        g.dispose();
        return img;
    }

    // Swirling wind arrows
    static BufferedImage createWindCurrent() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(34, 80, 34));
        g.fillRect(0, 0, 32, 32);
        // Wind streaks
        g.setColor(new Color(180, 210, 240, 160));
        g.setStroke(new BasicStroke(2));
        g.drawLine(8, 8, 24, 8);
        g.drawLine(6, 16, 26, 16);
        g.drawLine(8, 24, 24, 24);
        // Arrow heads pointing north
        g.drawLine(16, 3, 12, 8);
        g.drawLine(16, 3, 20, 8);
        // Lighter streaks
        g.setColor(new Color(220, 235, 255, 120));
        g.drawLine(10, 12, 22, 12);
        g.drawLine(12, 20, 20, 20);
        g.dispose();
        return img;
    }

    // Gnarled whispering tree
    static BufferedImage createAncientTree() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(34, 80, 34));
        g.fillRect(0, 0, 32, 32);
        // Trunk
        g.setColor(new Color(80, 55, 30));
        g.fillRect(13, 14, 6, 16);
        // Roots
        g.setColor(new Color(70, 50, 25));
        g.fillRect(10, 26, 4, 4);
        g.fillRect(18, 26, 4, 4);
        // Canopy
        g.setColor(new Color(20, 60, 25));
        g.fillOval(4, 2, 24, 18);
        g.setColor(new Color(30, 75, 30));
        g.fillOval(6, 4, 20, 14);
        // Eyes in the trunk (it watches!)
        g.setColor(new Color(180, 200, 100));
        g.fillOval(14, 16, 2, 2);
        g.fillOval(17, 16, 2, 2);
        // Faint glow
        g.setColor(new Color(100, 200, 80, 40));
        g.fillOval(2, 0, 28, 22);
        g.dispose();
        return img;
    }

    // Ghostly shimmer
    static BufferedImage createMemoryEcho() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        // Dark shadow ground
        g.setColor(new Color(30, 25, 35));
        g.fillRect(0, 0, 32, 32);
        // Ghost figure
        g.setColor(new Color(160, 150, 200, 100));
        g.fillOval(10, 4, 12, 12);
        g.fillRect(12, 14, 8, 14);
        // Inner glow
        g.setColor(new Color(180, 170, 220, 80));
        g.fillOval(12, 6, 8, 8);
        // Shimmer particles
        g.setColor(new Color(200, 190, 240, 120));
        g.fillRect(8, 10, 2, 2);
        g.fillRect(22, 8, 2, 2);
        g.fillRect(6, 20, 2, 2);
        g.fillRect(24, 18, 2, 2);
        g.fillRect(14, 2, 2, 2);
        g.dispose();
        return img;
    }

    // Chest under water
    static BufferedImage createSunkenChest() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        // Water
        g.setColor(new Color(20, 50, 90));
        g.fillRect(0, 0, 32, 32);
        // Water ripples
        g.setColor(new Color(30, 70, 120));
        g.drawLine(2, 8, 30, 8);
        g.drawLine(4, 16, 28, 16);
        g.drawLine(2, 24, 30, 24);
        // Chest (visible through water)
        g.setColor(new Color(120, 80, 30, 180));
        g.fillRect(9, 14, 14, 10);
        g.setColor(new Color(180, 140, 50, 180));
        g.fillRect(9, 14, 14, 3);
        // Lock/clasp
        g.setColor(new Color(200, 180, 60, 200));
        g.fillRect(14, 18, 4, 3);
        // Glint
        g.setColor(new Color(255, 255, 200, 100));
        g.fillRect(11, 15, 2, 2);
        g.dispose();
        return img;
    }

    // War banner on scorched ground
    static BufferedImage createBattleStandard() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        // Scorched ground
        g.setColor(new Color(55, 40, 25));
        g.fillRect(0, 0, 32, 32);
        // Pole
        g.setColor(new Color(100, 90, 80));
        g.fillRect(15, 4, 2, 26);
        // Banner
        g.setColor(new Color(160, 30, 30));
        g.fillRect(17, 5, 10, 12);
        // Banner symbol
        g.setColor(new Color(200, 160, 40));
        g.fillRect(20, 8, 4, 4);
        // Tattered edge
        g.setColor(new Color(130, 25, 25));
        g.fillRect(25, 5, 2, 3);
        g.fillRect(26, 10, 2, 4);
        // Skull at base
        g.setColor(new Color(200, 190, 170));
        g.fillOval(7, 24, 6, 6);
        g.dispose();
        return img;
    }

    // ── Map Patching ──────────────────────────────────────────────────────────

    /**
     * Reads a map, patches it with features, and writes it back.
     * Features are placed by scanning for suitable tiles, respecting existing content.
     */
    static MapPatcher loadMap(String name) throws Exception {
        return new MapPatcher(MAP_DIR + name + ".rfmap");
    }

    // ── Lirandel: Shrines + Dig Sites + Ambush (levels 1-5) ──────────────

    static void patchLirandel() throws Exception {
        MapPatcher m = loadMap("lirandel");

        // 3 shrines to Lirandel scattered across the map
        m.placeFeatureNearTerrain(SHRINE, '.', 3, new String[][]{
            {"god","LIRANDEL","favor","5","xp","25","message","A moonstone shrine to Lirandel glows softly in the moonlight."},
            {"god","LIRANDEL","favor","5","xp","30","message","Ancient runes of Lirandel shimmer on this moss-covered altar."},
            {"god","LIRANDEL","favor","8","xp","40","message","You discover a hidden shrine to the Moon Goddess. Silver light washes over you."}
        });

        // 4 dig sites with low-tier loot
        m.placeFeatureNearTerrain(DIG_SITE, '.', 4, new String[][]{
            {"tier","1","gold","5","message","You dig through the loose earth and find something!"},
            {"tier","1","gold","8","message","Beneath a pile of stones, you uncover a buried cache."},
            {"tier","1","gold","3","message","The disturbed earth yields its treasure."},
            {"tier","1","gold","10","message","You find a traveler's stash buried under a rock."}
        });

        // 2 ambush encounters
        m.placeFeatureNearTerrain(AMBUSH, '.', 2, new String[][]{
            {"monsterId","corrupted_crab","lootTier","1","message","A massive crab bursts from the undergrowth!"},
            {"monsterId","wolf","lootTier","1","message","Wolves emerge from the shadows, teeth bared!"}
        });

        m.save();
        System.out.println("  Placed " + m.placed + " features on lirandel");
    }

    // ── Pyralis: Geysers + Shrines + Dig Sites + Ambush (levels 6-10) ────

    static void patchPyralis() throws Exception {
        MapPatcher m = loadMap("pyralis").withGround('!', ':', '$', '.', '4', 'r');

        // 5 geysers near volcanic terrain
        m.placeFeature(GEYSER, 5, new String[][]{
            {"amount","5","chance","50","message","A geyser of molten rock erupts beneath you!"},
            {"amount","6","chance","40","message","Steam and fire blast from a crack in the earth!"},
            {"amount","4","chance","60","message","Scalding steam jets from the volcanic vent!"},
            {"amount","5","chance","50","message","The ground shakes and lava spurts upward!"},
            {"amount","7","chance","30","message","A massive eruption of fire catches you off guard!"}
        });

        // 2 shrines to Pyralis
        m.placeFeature(SHRINE, 2, new String[][]{
            {"god","PYRALIS","favor","5","xp","50","message","An obsidian altar to Pyralis radiates intense heat."},
            {"god","PYRALIS","favor","8","xp","60","message","Flames dance around this ancient shrine to the Fire God."}
        });

        // 3 dig sites
        m.placeFeature(DIG_SITE, 3, new String[][]{
            {"tier","2","gold","15","message","Beneath the ash, you find a scorched treasure cache."},
            {"tier","2","gold","20","message","You dig through volcanic debris and uncover loot!"},
            {"tier","2","gold","12","message","A metallic glint in the ash catches your eye."}
        });

        // 2 ambush encounters
        m.placeFeature(AMBUSH, 2, new String[][]{
            {"monsterId","slag_beast","lootTier","2","message","A slag beast charges from behind a lava flow!"},
            {"monsterId","forge_golem","lootTier","2","message","A forge golem rises from the molten ground!"}
        });

        m.save();
        System.out.println("  Placed " + m.placed + " features on pyralis");
    }

    // ── Zephyrion: Wind Currents + Shrines + Treasure (levels 8-11) ──────

    static void patchZephyrion() throws Exception {
        MapPatcher m = loadMap("zephyrion").withGround(' ', '`', '_');

        // 6 wind currents in open areas
        m.placeFeature(WIND_CURRENT, 6, new String[][]{
            {"direction","north","distance","4","message","A fierce gale hurls you northward!"},
            {"direction","south","distance","3","message","The wind sweeps you southward!"},
            {"direction","east","distance","4","message","A blast of wind pushes you eastward!"},
            {"direction","west","distance","3","message","Storm winds fling you to the west!"},
            {"direction","north","distance","5","message","An updraft launches you forward!"},
            {"direction","east","distance","3","message","A cyclone of air carries you away!"}
        });

        // 2 shrines to Zephyrion
        m.placeFeature(SHRINE, 2, new String[][]{
            {"god","ZEPHYRION","favor","5","xp","60","message","A wind-carved monolith hums with Zephyrion's power."},
            {"god","ZEPHYRION","favor","8","xp","70","message","Lightning dances around this shrine to the Storm God."}
        });

        // 3 dig sites
        m.placeFeature(DIG_SITE, 3, new String[][]{
            {"tier","2","gold","18","message","The wind has uncovered something buried in the cliff face."},
            {"tier","3","gold","25","message","A sky-pirate's hidden stash!"},
            {"tier","2","gold","15","message","Storm debris conceals a traveler's lost belongings."}
        });

        // 2 ambush encounters
        m.placeFeature(AMBUSH, 2, new String[][]{
            {"monsterId","storm_hawk","lootTier","2","message","A massive storm hawk dives from the clouds!"},
            {"monsterId","sky_raider","lootTier","3","message","Sky raiders drop from their hiding spot above!"}
        });

        m.save();
        System.out.println("  Placed " + m.placed + " features on zephyrion");
    }

    // ── Sylvandar: Talking Trees + Shrines + Treasure (levels 12-15) ─────

    static void patchSylvandar() throws Exception {
        MapPatcher m = loadMap("sylvandar").withGround('\uE060', '\uE063', '\uE062', '\uE064', '1');

        // 5 talking trees scattered in forests
        m.placeFeature(ANCIENT_TREE, 5, new String[][]{
            {"message","The ancient tree creaks: 'The roots remember what the leaves forget...'"},
            {"message","A whispering oak speaks: 'The Rootvault holds secrets even I dare not recall.'"},
            {"message","The gnarled tree murmurs: 'Sylvandar weeps. The blight spreads from below.'"},
            {"message","A moss-covered elder tree sighs: 'Once these mangroves sang. Now they only mourn.'"},
            {"message","The tree's bark shifts like a face: 'Seek the Great Root. It knows the truth.'"}
        });

        // 2 shrines to Sylvandar
        m.placeFeature(SHRINE, 2, new String[][]{
            {"god","SYLVANDAR","favor","5","xp","80","message","A living shrine of woven roots pulses with Sylvandar's essence."},
            {"god","SYLVANDAR","favor","8","xp","90","message","Flowers bloom around this ancient altar to the Nature God."}
        });

        // 3 dig sites
        m.placeFeature(DIG_SITE, 3, new String[][]{
            {"tier","3","gold","25","message","Beneath tangled roots, you find a druid's hidden cache."},
            {"tier","3","gold","30","message","The forest floor yields an ancient offering."},
            {"tier","3","gold","20","message","You unearth a bundle wrapped in living leaves."}
        });

        // 2 ambush
        m.placeFeature(AMBUSH, 2, new String[][]{
            {"monsterId","vine_strangler","lootTier","3","message","Vines erupt from the ground, wrapping around you!"},
            {"monsterId","root_golem","lootTier","3","message","The earth splits open as a root golem emerges!"}
        });

        m.save();
        System.out.println("  Placed " + m.placed + " features on sylvandar");
    }

    // ── Thalorax: Sunken Chests + Shrines + Ambush (levels 16-19) ────────

    static void patchThalorax() throws Exception {
        MapPatcher m = loadMap("thalorax").withGround('\uE070', '\uE074', '\uE073', '\uE076');

        // 4 sunken chests near water
        m.placeFeatureNearLiquid(SUNKEN_CHEST, 4, new String[][]{
            {"tier","4","gold","40","message","You reach into the murky depths and pull out a chest!"},
            {"tier","4","gold","35","message","A barnacle-encrusted chest surfaces from the deep."},
            {"tier","3","gold","50","message","The sunken treasure yields its riches to you."},
            {"tier","4","gold","45","message","You pry open a waterlogged chest from the ocean floor."}
        });

        // 2 shrines to Thalorax
        m.placeFeature(SHRINE, 2, new String[][]{
            {"god","THALORAX","favor","5","xp","100","message","A coral-encrusted altar to Thalorax throbs with deep-sea pressure."},
            {"god","THALORAX","favor","8","xp","120","message","Bioluminescent runes mark this shrine to the God of the Deep."}
        });

        // 3 dig sites
        m.placeFeature(DIG_SITE, 3, new String[][]{
            {"tier","4","gold","35","message","Beneath the coral rubble, you find salvage from a sunken vessel."},
            {"tier","4","gold","40","message","An old sailor's cache, sealed against the water."},
            {"tier","3","gold","30","message","You uncover a pressure-sealed container."}
        });

        // 2 ambush
        m.placeFeature(AMBUSH, 2, new String[][]{
            {"monsterId","pressure_hulk","lootTier","4","message","A pressure hulk bursts from the seafloor!"},
            {"monsterId","rune_scarred_shark","lootTier","4","message","A massive rune-scarred shark lunges at you!"}
        });

        m.save();
        System.out.println("  Placed " + m.placed + " features on thalorax");
    }

    // ── Umbryn: Memory Echoes + Shrines + Treasure (levels 20-23) ────────

    static void patchUmbryn() throws Exception {
        MapPatcher m = loadMap("umbryn").withGround('\uE086', '\uE080', '\uE083', '\uE082');

        // 5 memory echoes
        m.placeFeature(MEMORY_ECHO, 5, new String[][]{
            {"message","A ghostly voice whispers: 'I left my child at the docks... I never came back.'","oneTime","true"},
            {"message","The echo of a soldier: 'We held the line for three days. On the fourth, the shadows took us.'","oneTime","true"},
            {"message","A woman's memory lingers: 'He promised to return from the Archive. The door never opened again.'","oneTime","true"},
            {"message","A child's echo: 'Mama said the shadows are just memories. But memories can hurt.'","oneTime","true"},
            {"message","An old scholar's ghost: 'The Archive of Tears contains every sorrow ever shed. I added mine.'","oneTime","true"}
        });

        // 2 shrines to Umbryn
        m.placeFeature(SHRINE, 2, new String[][]{
            {"god","UMBRYN","favor","5","xp","130","message","A shadow-wreathed altar to Umbryn absorbs the light around it."},
            {"god","UMBRYN","favor","8","xp","150","message","This shrine to the Shadow God is cold to the touch. Memories flood your mind."}
        });

        // 3 dig sites
        m.placeFeature(DIG_SITE, 3, new String[][]{
            {"tier","5","gold","50","message","Beneath layers of shadow-stained earth, you find forgotten treasures."},
            {"tier","5","gold","45","message","A grief-laden cache, buried by someone who wanted to forget."},
            {"tier","4","gold","40","message","The shadows reluctantly yield what they have swallowed."}
        });

        // 2 ambush
        m.placeFeature(AMBUSH, 2, new String[][]{
            {"monsterId","memory_shade","lootTier","5","message","A memory shade materializes before you, reliving its final battle!"},
            {"monsterId","echo_knight","lootTier","5","message","An echo knight raises its spectral blade!"}
        });

        m.save();
        System.out.println("  Placed " + m.placed + " features on umbryn");
    }

    // ── Bellorak: Battle Standards + Shrines + War Camps (levels 24-27) ──

    static void patchBellorak() throws Exception {
        MapPatcher m = loadMap("bellorak").withGround('\uE090', '\uE093', '\uE094', '\uE096', '\uE091');

        // 4 battle standards (war camp ambushes)
        m.placeFeature(BATTLE_STD, 4, new String[][]{
            {"monsterId","war_wraith","lootTier","5","message","You disturb a war camp! A wraith rises from the carnage!"},
            {"monsterId","glory_hound","lootTier","5","message","A glory hound charges from behind the battle standard!"},
            {"monsterId","iron_golem","lootTier","6","message","An iron golem activates, guarding the fallen camp!"},
            {"monsterId","blood_oath_berserker","lootTier","6","message","A blood oath berserker screams a battle cry!"}
        });

        // 2 shrines to Bellorak
        m.placeFeature(SHRINE, 2, new String[][]{
            {"god","BELLORAK","favor","5","xp","160","message","A blood-stained altar to Bellorak stands amid the wreckage of war."},
            {"god","BELLORAK","favor","8","xp","180","message","Iron and fire mark this shrine to the War God. Glory awaits the worthy."}
        });

        // 3 dig sites
        m.placeFeature(DIG_SITE, 3, new String[][]{
            {"tier","6","gold","60","message","Beneath the scorched battlefield, you find a soldier's hidden stash."},
            {"tier","5","gold","55","message","War spoils lie buried under a fallen siege wall."},
            {"tier","6","gold","70","message","You uncover a commander's locked chest from the rubble."}
        });

        m.save();
        System.out.println("  Placed " + m.placed + " features on bellorak");
    }

    // ── Map Patcher Utility ───────────────────────────────────────────────

    static class MapPatcher {
        String path;
        String rawJson;
        char[][] tiles;
        int width, height;
        Set<Character> groundTiles = new HashSet<>(); // walkable ground chars for this island
        Map<String, Map<String, String>> newStates = new LinkedHashMap<>();
        int placed = 0;
        Random rng = new Random(42); // deterministic for reproducibility

        MapPatcher(String path) throws Exception {
            this.path = path;
            this.rawJson = readFile(path);
            parseTiles();
        }

        MapPatcher withGround(char... chars) {
            for (char c : chars) groundTiles.add(c);
            return this;
        }

        void parseTiles() {
            // Extract width and height
            width  = extractInt("\"width\"");
            height = extractInt("\"height\"");
            tiles  = new char[height][width];

            // Find tiles array and parse it
            int tilesStart = rawJson.indexOf("\"tiles\"");
            int arrStart   = rawJson.indexOf('[', tilesStart) + 1; // outer [
            int row = 0;
            int pos = arrStart;
            while (row < height && pos < rawJson.length()) {
                int rowStart = rawJson.indexOf('[', pos);
                if (rowStart < 0) break;
                int rowEnd = rawJson.indexOf(']', rowStart);
                String rowStr = rawJson.substring(rowStart + 1, rowEnd);
                String[] cells = rowStr.split(",");
                for (int c = 0; c < Math.min(cells.length, width); c++) {
                    String cell = cells[c].trim().replace("\"", "");
                    tiles[row][c] = decodeChar(cell);
                }
                pos = rowEnd + 1;
                row++;
            }
        }

        char decodeChar(String cell) {
            if (cell.isEmpty()) return ' ';
            // Handle JSON unicode escapes like \uE060
            if (cell.length() >= 6 && cell.charAt(0) == '\\' && cell.charAt(1) == 'u') {
                try { return (char) Integer.parseInt(cell.substring(2, 6), 16); }
                catch (Exception e) { /* fall through */ }
            }
            // Handle raw unicode (if file was saved with actual unicode chars)
            return cell.charAt(0);
        }

        int extractInt(String key) {
            int i = rawJson.indexOf(key);
            int colon = rawJson.indexOf(':', i);
            int comma = rawJson.indexOf(',', colon);
            int nl = rawJson.indexOf('\n', colon);
            int end = Math.min(comma > 0 ? comma : rawJson.length(), nl > 0 ? nl : rawJson.length());
            return Integer.parseInt(rawJson.substring(colon + 1, end).trim());
        }

        /** Place feature tiles on walkable ground tiles. Uses the groundTiles set. */
        void placeFeature(char featureTile, int count, String[][] instanceData) {
            List<int[]> candidates = new ArrayList<>();
            for (int y = 3; y < height - 3; y++) {
                for (int x = 3; x < width - 3; x++) {
                    if (!groundTiles.contains(tiles[y][x])) continue;
                    if (!hasGroundNeighbor(x, y)) continue;
                    if (nearSpecial(x, y, 5)) continue;
                    candidates.add(new int[]{x, y});
                }
            }
            Collections.shuffle(candidates, rng);
            List<int[]> chosen = spaceOut(candidates, Math.min(count, instanceData.length), 15);
            placeChosen(featureTile, chosen, instanceData);
        }

        // Keep old method for backward compat
        void placeFeatureNearTerrain(char featureTile, char nearTile, int count, String[][] instanceData) {
            groundTiles.add(nearTile);
            placeFeature(featureTile, count, instanceData);
        }

        /** Place feature tiles adjacent to liquid tiles. */
        void placeFeatureNearLiquid(char featureTile, int count, String[][] instanceData) {
            List<int[]> candidates = new ArrayList<>();
            for (int y = 3; y < height - 3; y++) {
                for (int x = 3; x < width - 3; x++) {
                    if (!groundTiles.contains(tiles[y][x]) && tiles[y][x] != '.') continue;
                    if (isLiquid(tiles[y][x])) continue;
                    if (!hasLiquidNeighbor(x, y)) continue;
                    if (nearSpecial(x, y, 5)) continue;
                    candidates.add(new int[]{x, y});
                }
            }
            Collections.shuffle(candidates, rng);
            List<int[]> chosen = spaceOut(candidates, Math.min(count, instanceData.length), 15);
            placeChosen(featureTile, chosen, instanceData);
        }

        private void placeChosen(char featureTile, List<int[]> chosen, String[][] instanceData) {
            for (int i = 0; i < chosen.size(); i++) {
                int[] pos = chosen.get(i);
                tiles[pos[1]][pos[0]] = featureTile;
                if (i < instanceData.length && instanceData[i].length > 0) {
                    Map<String, String> data = new LinkedHashMap<>();
                    for (int j = 0; j < instanceData[i].length - 1; j += 2) {
                        data.put(instanceData[i][j], instanceData[i][j + 1]);
                    }
                    newStates.put(pos[0] + "," + pos[1], data);
                }
                placed++;
            }
        }

        boolean hasGroundNeighbor(int x, int y) {
            int[][] dirs = {{0,-1},{1,0},{0,1},{-1,0}};
            for (int[] d : dirs) {
                int nx = x + d[0], ny = y + d[1];
                if (nx >= 0 && nx < width && ny >= 0 && ny < height && groundTiles.contains(tiles[ny][nx])) return true;
            }
            return false;
        }

        boolean hasLiquidNeighbor(int x, int y) {
            int[][] dirs = {{0,-1},{1,0},{0,1},{-1,0}};
            for (int[] d : dirs) {
                int nx = x + d[0], ny = y + d[1];
                if (nx >= 0 && nx < width && ny >= 0 && ny < height && isLiquid(tiles[ny][nx])) return true;
            }
            return false;
        }

        boolean isLiquid(char c) {
            return c == 'w' || c == '~' || c == '9' || c == 'a' || c == 'b' || c == 'c' || c == 'd'
                    || c == 'e' || c == 'f' || c == 'g' || c == 'h';
        }

        boolean isWalkable(char c) {
            // Common walkable overworld tiles
            return c == '.' || c == 'r' || c == '4' || c == '1' || c == '3';
        }

        boolean nearSpecial(int x, int y, int radius) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    int nx = x + dx, ny = y + dy;
                    if (nx < 0 || nx >= width || ny < 0 || ny >= height) continue;
                    char c = tiles[ny][nx];
                    // Town entrances, dungeon entrances, portals, existing features
                    if (c == 'E' || c == 'D' || c == 'G' || c == 'U' || isFeatureTile(c)) return true;
                }
            }
            return false;
        }

        boolean isFeatureTile(char c) {
            return c == SHRINE || c == DIG_SITE || c == AMBUSH || c == GEYSER ||
                   c == WIND_CURRENT || c == ANCIENT_TREE || c == MEMORY_ECHO ||
                   c == SUNKEN_CHEST || c == BATTLE_STD;
        }

        List<int[]> spaceOut(List<int[]> candidates, int count, int minDist) {
            List<int[]> chosen = new ArrayList<>();
            for (int[] c : candidates) {
                if (chosen.size() >= count) break;
                boolean tooClose = false;
                for (int[] ch : chosen) {
                    if (Math.abs(c[0] - ch[0]) + Math.abs(c[1] - ch[1]) < minDist) {
                        tooClose = true; break;
                    }
                }
                if (!tooClose) chosen.add(c);
            }
            return chosen;
        }

        void save() throws Exception {
            // Replace tiles in the raw JSON
            StringBuilder sb = new StringBuilder(rawJson.length() + 4096);
            int tilesStart = rawJson.indexOf("\"tiles\"");
            int arrStart   = rawJson.indexOf('[', tilesStart);

            // Find the end of the tiles array (matching bracket)
            int depth = 0;
            int arrEnd = arrStart;
            for (int i = arrStart; i < rawJson.length(); i++) {
                if (rawJson.charAt(i) == '[') depth++;
                if (rawJson.charAt(i) == ']') {
                    depth--;
                    if (depth == 0) { arrEnd = i + 1; break; }
                }
            }

            sb.append(rawJson, 0, arrStart);
            // Write new tiles array
            sb.append("[\n");
            for (int r = 0; r < height; r++) {
                sb.append("    [");
                for (int c = 0; c < width; c++) {
                    char ch = tiles[r][c];
                    sb.append('"');
                    // Escape special JSON chars and unicode
                    if (ch == '"') sb.append("\\\"");
                    else if (ch == '\\') sb.append("\\\\");
                    else if (ch > 127) sb.append(String.format("\\u%04X", (int) ch));
                    else sb.append(ch);
                    sb.append('"');
                    if (c < width - 1) sb.append(',');
                }
                sb.append(']');
                if (r < height - 1) sb.append(',');
                sb.append('\n');
            }
            sb.append("  ]");

            // Append remainder after tiles array
            String remainder = rawJson.substring(arrEnd);

            // Merge new initialTileStates into existing ones
            if (!newStates.isEmpty()) {
                // Find initialTileStates in remainder (or in the original JSON)
                int itsIdx = remainder.indexOf("\"initialTileStates\"");
                if (itsIdx >= 0) {
                    // Find the opening { of the object
                    int objStart = remainder.indexOf('{', itsIdx);
                    int objEnd = findMatchingBrace(remainder, objStart);

                    String before = remainder.substring(0, objStart + 1);
                    String existing = remainder.substring(objStart + 1, objEnd).trim();
                    String after = remainder.substring(objEnd);

                    sb.append(before);
                    // Keep existing entries
                    if (!existing.isEmpty()) {
                        sb.append(existing);
                        sb.append(",\n");
                    } else {
                        sb.append('\n');
                    }
                    // Append new entries
                    appendStates(sb);
                    sb.append(after);
                } else {
                    // No initialTileStates field — need to add it before closing }
                    int lastBrace = remainder.lastIndexOf('}');
                    sb.append(remainder, 0, lastBrace);
                    sb.append(",\n  \"initialTileStates\": {\n");
                    appendStates(sb);
                    sb.append("  }\n");
                    sb.append(remainder.substring(lastBrace));
                }
            } else {
                sb.append(remainder);
            }

            writeFile(path, sb.toString());
        }

        void appendStates(StringBuilder sb) {
            int i = 0;
            for (var entry : newStates.entrySet()) {
                sb.append("    \"").append(entry.getKey()).append("\": {\"data\": {");
                int j = 0;
                for (var kv : entry.getValue().entrySet()) {
                    sb.append('"').append(escapeJson(kv.getKey())).append("\": \"").append(escapeJson(kv.getValue())).append('"');
                    if (++j < entry.getValue().size()) sb.append(", ");
                }
                sb.append("}}");
                if (++i < newStates.size()) sb.append(',');
                sb.append('\n');
            }
        }

        int findMatchingBrace(String s, int openPos) {
            int depth = 0;
            for (int i = openPos; i < s.length(); i++) {
                if (s.charAt(i) == '{') depth++;
                if (s.charAt(i) == '}') { depth--; if (depth == 0) return i; }
            }
            return s.length() - 1;
        }

        String escapeJson(String s) {
            return s.replace("\\", "\\\\").replace("\"", "\\\"");
        }
    }

    static String readFile(String path) throws Exception {
        byte[] bytes = new FileInputStream(path).readAllBytes();
        return new String(bytes, StandardCharsets.UTF_8);
    }

    static void writeFile(String path, String content) throws Exception {
        try (Writer w = new OutputStreamWriter(new FileOutputStream(path), StandardCharsets.UTF_8)) {
            w.write(content);
        }
    }
}

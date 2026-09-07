package io.cannonforge.retroquest.core;

import java.awt.Color;

/**
 * Look-and-feel descriptor for the first-person {@link TexturedDungeonRenderer}.
 *
 * <p>The wireframe renderer has carried per-dungeon palettes since Island 3, but the textured
 * renderer was hard-wired to Sylvandar's Rootvault — every colour, the bark texture and the
 * spore particles were {@code static final} constants. Adding Island 5 meant either a second
 * copy of a 950-line renderer or this: one theme object the renderer reads, so a new textured
 * dungeon is a factory method rather than a file.
 *
 * <p>Instances are immutable and shared; {@link #resolve} returns a cached singleton per dungeon.
 *
 * @see DungeonWallPainter for how {@link WallStyle} is actually drawn
 */
public final class DungeonTheme {

    /** How wall surfaces are textured. */
    public enum WallStyle {
        /** Vertical bark grain and moss — Rootvault (Sylvandar). The original textured look. */
        BARK,
        /** Chiselled masonry courses with bevelled relief — the crypt look. */
        BLOCK,
        /** Small irregular rounded stones in a deep mortar bed. */
        COBBLE,
        /** Few very large polished slabs with mineral veining. */
        SLAB,
        /** Irregular polygonal stones fitted tight with no continuous joint — Island 6. */
        CYCLOPEAN
    }

    /** The set piece carved into a theme's feature wall. */
    public enum Feature {
        NONE,
        /** Recessed shelving stacked with rotted scrolls. */
        SHELVES,
        /** A faded wall painting, worn past recognition. */
        FRESCO
    }

    /** How the drifting atmosphere particles behave. */
    public enum ParticleStyle {
        /** Slow omnidirectional drift with a slight upward bias. */
        SPORE,
        /** Fast rise, slight horizontal wobble, wraps at the ceiling. */
        BUBBLE,
        /** Heavy downward settle — suspended sediment. */
        SILT
    }

    // ── Walls ────────────────────────────────────────────────────────────────
    public final WallStyle wallStyle;
    /** What {@link DungeonTextures} carves into this theme's feature wall. */
    public final Feature feature;
    public final Color wallNear, wallFar, wallLight, wallDark, wallGrain, wallMoss, wallHigh, mortar;

    // ── Floor / ceiling ──────────────────────────────────────────────────────
    public final Color floorNear, floorFar, floorDetail, floorGap;
    public final Color ceilNear, ceilFar, ceilDetail;

    // ── Doors ────────────────────────────────────────────────────────────────
    public final Color doorNear, doorFar, doorFrame, doorPanel;

    // ── Specials, fog, sconces ───────────────────────────────────────────────
    public final Color specialNear, specialFar, fog;
    public final boolean sconces;
    /** A wall face carries a sconce when its hash is divisible by this. Higher = rarer. */
    public final int sconceEvery;
    public final Color flameCore, flameOuter, sconceGlow;

    // ── Particles ────────────────────────────────────────────────────────────
    public final ParticleStyle particleStyle;
    public final int particleCount;
    public final Color particleA, particleB, particleC;

    // ── HUD / minimap ────────────────────────────────────────────────────────
    public final Color hudBg, hudText, hudDim;
    public final Color mmBg, mmWall, mmDoor, mmPlayer, mmFog, mmSpecial;

    // ── Island 7 finale ──────────────────────────────────────────────────────
    /** How mirror-like the floor is, 0 = matte. Only the raycaster honours this. */
    public final float reflectivity;
    /** Split the reflection into colour fringes, as light through cut crystal. */
    public final boolean prismatic;
    /** Ramp reflectivity and splitting up with dungeon depth, so a descent escalates. */
    public final boolean escalateWithDepth;
    /**
     * How much the walls glow of their own accord, 0 = inert stone. Crystal halls are too open
     * for a carried lantern to reach the walls at all; giving the surface its own faint light is
     * what stops the finale being a black screen, and gives the mirror floor something to hold.
     */
    public final float selfGlow;

    private DungeonTheme(Builder b) {
        this.wallStyle = b.wallStyle;
        this.feature = b.feature;
        this.wallNear = b.wallNear; this.wallFar = b.wallFar;
        this.wallLight = b.wallLight; this.wallDark = b.wallDark;
        this.wallGrain = b.wallGrain; this.wallMoss = b.wallMoss;
        this.wallHigh = b.wallHigh; this.mortar = b.mortar;
        this.floorNear = b.floorNear; this.floorFar = b.floorFar;
        this.floorDetail = b.floorDetail; this.floorGap = b.floorGap;
        this.ceilNear = b.ceilNear; this.ceilFar = b.ceilFar; this.ceilDetail = b.ceilDetail;
        this.doorNear = b.doorNear; this.doorFar = b.doorFar;
        this.doorFrame = b.doorFrame; this.doorPanel = b.doorPanel;
        this.specialNear = b.specialNear; this.specialFar = b.specialFar; this.fog = b.fog;
        this.sconces = b.sconces; this.sconceEvery = b.sconceEvery;
        this.flameCore = b.flameCore; this.flameOuter = b.flameOuter; this.sconceGlow = b.sconceGlow;
        this.particleStyle = b.particleStyle; this.particleCount = b.particleCount;
        this.particleA = b.particleA; this.particleB = b.particleB; this.particleC = b.particleC;
        this.hudBg = b.hudBg; this.hudText = b.hudText; this.hudDim = b.hudDim;
        this.mmBg = b.mmBg; this.mmWall = b.mmWall; this.mmDoor = b.mmDoor;
        this.mmPlayer = b.mmPlayer; this.mmFog = b.mmFog; this.mmSpecial = b.mmSpecial;
        this.reflectivity = b.reflectivity;
        this.prismatic = b.prismatic;
        this.escalateWithDepth = b.escalateWithDepth;
        this.selfGlow = b.selfGlow;
    }

    // ── Resolution ───────────────────────────────────────────────────────────

    /**
     * Picks the theme for the dungeon currently being drawn.
     *
     * @param authoredDungeonName {@code data/dungeons/<name>_N.rfmap} group name, or null for a
     *                            procedurally generated dungeon
     * @param overworldName       island the dungeon was entered from, used as the fallback key
     */
    public static DungeonTheme resolve(String authoredDungeonName, String overworldName) {
        if (authoredDungeonName != null) {
            switch (authoredDungeonName) {
                case "rootvault":        return ROOTVAULT;
                case "pressure_temple":  return PRESSURE_TEMPLE;
                case "boneyard_trench":  return BONEYARD_TRENCH;
                case "leviathan_eye":    return LEVIATHAN_EYE;
                case "archive_of_tears": return ARCHIVE_OF_TEARS;
                case "forgotten_city":   return FORGOTTEN_CITY;
                case "war_beneath":      return WAR_BENEATH;
                case "iron_pit":         return IRON_PIT;
                case "cradle_of_shards": return CRADLE_OF_SHARDS;
                default: break;
            }
        }
        if ("sylvandar".equals(overworldName)) return ROOTVAULT;
        if ("thalorax".equals(overworldName))  return PRESSURE_TEMPLE;
        if ("umbryn".equals(overworldName))    return ARCHIVE_OF_TEARS;
        if ("bellorak".equals(overworldName))  return WAR_BENEATH;
        return CRYPT;
    }

    // ── Themes ───────────────────────────────────────────────────────────────

    /**
     * Sylvandar's Rootvault — the original textured look, preserved exactly. Living wood rather
     * than stone, so it keeps {@link WallStyle#BARK} and takes no sconces.
     */
    public static final DungeonTheme ROOTVAULT = new Builder(WallStyle.BARK)
            .wall(new Color(75, 52, 28), new Color(18, 12, 6),
                  new Color(95, 68, 38), new Color(30, 20, 10),
                  new Color(50, 35, 18), new Color(35, 65, 25),
                  new Color(95, 68, 38), new Color(28, 19, 9))
            .floor(new Color(55, 38, 18), new Color(12, 8, 4), new Color(68, 48, 25), new Color(8, 15, 6))
            .ceiling(new Color(12, 28, 14), new Color(4, 8, 4), new Color(20, 45, 18))
            .door(new Color(140, 100, 35), new Color(50, 35, 12),
                  new Color(160, 115, 40), new Color(65, 45, 22))
            .special(new Color(220, 170, 60), new Color(80, 60, 20))
            .fog(new Color(6, 12, 6))
            .noSconces()
            .particles(ParticleStyle.SPORE, 30,
                       new Color(40, 180, 130, 120), new Color(20, 90, 65, 60), new Color(200, 150, 50, 40))
            .hud(new Color(12, 8, 4, 220), new Color(180, 140, 50), new Color(80, 65, 30))
            .minimap(new Color(8, 6, 3), new Color(90, 65, 30), new Color(160, 120, 40),
                     new Color(80, 220, 120), new Color(20, 15, 8), new Color(200, 160, 50))
            .build();

    /**
     * Thalorax's Pressure Temple — drowned basalt masonry, algae in every joint, lit by
     * bioluminescent sconces because nothing burns this far down.
     */
    public static final DungeonTheme PRESSURE_TEMPLE = new Builder(WallStyle.BLOCK)
            .wall(new Color(88, 118, 122), new Color(7, 15, 19),
                  new Color(176, 210, 210), new Color(13, 23, 28),
                  new Color(52, 72, 76), new Color(44, 110, 86),
                  new Color(178, 224, 214), new Color(8, 15, 19))
            .floor(new Color(64, 84, 88), new Color(6, 12, 15), new Color(104, 128, 132), new Color(7, 14, 17))
            .ceiling(new Color(26, 42, 48), new Color(3, 7, 9), new Color(46, 70, 76))
            .door(new Color(74, 112, 98), new Color(16, 29, 27),
                  new Color(134, 178, 156), new Color(34, 58, 54))
            .special(new Color(130, 245, 232), new Color(38, 92, 90))
            .fog(new Color(6, 14, 17))
            .sconces(4, new Color(214, 255, 250), new Color(58, 202, 212), new Color(70, 210, 220))
            .particles(ParticleStyle.BUBBLE, 34,
                       new Color(160, 226, 234, 120), new Color(92, 172, 192, 70), new Color(210, 245, 245, 55))
            .hud(new Color(6, 13, 16, 224), new Color(112, 212, 202), new Color(44, 86, 86))
            .minimap(new Color(6, 12, 15), new Color(54, 88, 94), new Color(112, 172, 152),
                     new Color(140, 240, 200), new Color(12, 23, 27), new Color(112, 212, 202))
            .build();

    /**
     * Thalorax's Boneyard Trench — pale cobbles set in black silt, lit by whale-oil braziers that
     * still burn in sealed pockets.
     */
    public static final DungeonTheme BONEYARD_TRENCH = new Builder(WallStyle.COBBLE)
            .wall(new Color(132, 124, 105), new Color(13, 12, 10),
                  new Color(204, 192, 166), new Color(26, 23, 19),
                  new Color(86, 79, 66), new Color(64, 84, 56),
                  new Color(222, 212, 184), new Color(14, 12, 10))
            .floor(new Color(84, 77, 63), new Color(10, 9, 8), new Color(118, 108, 89), new Color(12, 10, 9))
            .ceiling(new Color(24, 22, 18), new Color(4, 4, 3), new Color(44, 40, 33))
            .door(new Color(100, 76, 46), new Color(27, 20, 12),
                  new Color(154, 124, 72), new Color(54, 40, 23))
            .special(new Color(240, 206, 128), new Color(84, 68, 34))
            .fog(new Color(9, 8, 7))
            .sconces(4, new Color(255, 234, 176), new Color(238, 148, 48), new Color(230, 150, 60))
            .particles(ParticleStyle.SILT, 38,
                       new Color(184, 170, 140, 95), new Color(122, 112, 92, 60), new Color(224, 204, 162, 45))
            .hud(new Color(10, 9, 7, 224), new Color(222, 192, 122), new Color(94, 80, 52))
            .minimap(new Color(9, 8, 6), new Color(96, 88, 72), new Color(154, 124, 72),
                     new Color(240, 210, 130), new Color(19, 17, 14), new Color(222, 192, 122))
            .build();

    /**
     * Thalorax's Leviathan Eye — a single level of polished obsidian slabs shot through with red
     * veining, lit by whatever the thing inside is still dreaming.
     */
    public static final DungeonTheme LEVIATHAN_EYE = new Builder(WallStyle.SLAB)
            .wall(new Color(76, 62, 92), new Color(9, 7, 13),
                  new Color(150, 128, 180), new Color(17, 13, 23),
                  new Color(50, 41, 62), new Color(168, 46, 80),
                  new Color(196, 172, 224), new Color(10, 8, 14))
            .floor(new Color(58, 48, 70), new Color(8, 6, 10), new Color(94, 78, 114), new Color(10, 8, 13))
            .ceiling(new Color(20, 16, 26), new Color(3, 2, 5), new Color(38, 30, 48))
            .door(new Color(78, 57, 94), new Color(18, 13, 23),
                  new Color(154, 124, 184), new Color(40, 30, 50))
            .special(new Color(246, 168, 214), new Color(88, 44, 78))
            .fog(new Color(8, 6, 11))
            .sconces(3, new Color(255, 198, 226), new Color(202, 62, 132), new Color(190, 70, 150))
            .particles(ParticleStyle.SPORE, 26,
                       new Color(212, 152, 240, 105), new Color(150, 92, 200, 65), new Color(255, 200, 230, 45))
            .hud(new Color(9, 7, 12, 224), new Color(208, 172, 232), new Color(86, 70, 106))
            .minimap(new Color(9, 7, 12), new Color(74, 61, 90), new Color(154, 124, 184),
                     new Color(226, 168, 244), new Color(17, 13, 22), new Color(208, 172, 232))
            .build();

    /**
     * Umbryn's Archive of Tears — a drowned library. Pale coursed limestone gone grey with damp,
     * shelving carved straight into the walls, and cold witchlight in the sconces because open
     * flame and a million scrolls never shared a room for long.
     */
    public static final DungeonTheme ARCHIVE_OF_TEARS = new Builder(WallStyle.BLOCK)
            .feature(Feature.SHELVES)
            .wall(new Color(150, 148, 138), new Color(15, 16, 18),
                  new Color(214, 212, 200), new Color(28, 29, 32),
                  new Color(96, 95, 88), new Color(72, 96, 90),
                  new Color(232, 230, 218), new Color(17, 18, 20))
            .floor(new Color(96, 94, 88), new Color(10, 11, 12), new Color(130, 128, 119), new Color(13, 14, 15))
            .ceiling(new Color(28, 29, 32), new Color(4, 4, 5), new Color(50, 51, 55))
            .door(new Color(104, 84, 58), new Color(26, 21, 15),
                  new Color(162, 138, 96), new Color(56, 45, 30))
            .special(new Color(198, 226, 240), new Color(64, 82, 96))
            .fog(new Color(9, 10, 12))
            .sconces(4, new Color(232, 246, 255), new Color(126, 176, 214), new Color(120, 172, 212))
            .particles(ParticleStyle.SILT, 34,
                       new Color(196, 210, 222, 90), new Color(130, 146, 160, 60), new Color(226, 238, 248, 45))
            .hud(new Color(9, 10, 12, 224), new Color(198, 214, 228), new Color(84, 94, 106))
            .minimap(new Color(9, 10, 12), new Color(112, 112, 106), new Color(162, 138, 96),
                     new Color(206, 230, 246), new Color(19, 20, 22), new Color(198, 214, 228))
            .build();

    /**
     * Umbryn's Forgotten City — cyclopean polygonal masonry with no continuous joint anywhere,
     * frescoes worn past reading, and the warm sodium glow of whatever the inhabitants left burning.
     */
    public static final DungeonTheme FORGOTTEN_CITY = new Builder(WallStyle.CYCLOPEAN)
            .feature(Feature.FRESCO)
            .wall(new Color(138, 124, 106), new Color(16, 14, 12),
                  new Color(206, 190, 164), new Color(30, 26, 22),
                  new Color(94, 84, 70), new Color(78, 92, 58),
                  new Color(226, 212, 186), new Color(18, 16, 13))
            .floor(new Color(102, 92, 78), new Color(11, 10, 9), new Color(138, 126, 108), new Color(14, 13, 11))
            .ceiling(new Color(30, 27, 23), new Color(4, 4, 3), new Color(54, 48, 40))
            .door(new Color(112, 86, 52), new Color(28, 22, 13),
                  new Color(172, 142, 88), new Color(60, 46, 28))
            .special(new Color(244, 206, 138), new Color(88, 70, 40))
            .fog(new Color(10, 9, 8))
            .sconces(4, new Color(255, 238, 190), new Color(238, 158, 62), new Color(232, 156, 68))
            .particles(ParticleStyle.SILT, 30,
                       new Color(206, 186, 152, 88), new Color(138, 124, 100, 58), new Color(238, 216, 176, 44))
            .hud(new Color(10, 9, 8, 224), new Color(228, 200, 140), new Color(98, 84, 58))
            .minimap(new Color(10, 9, 8), new Color(108, 98, 82), new Color(172, 142, 88),
                     new Color(244, 214, 150), new Color(20, 18, 15), new Color(228, 200, 140))
            .build();

    /**
     * Bellorak's War Beneath — a siege dug in under the field. Rough hewn rock shored with iron,
     * lit by the forge fires that never went out because the war never ended.
     */
    public static final DungeonTheme WAR_BENEATH = new Builder(WallStyle.BLOCK)
            .feature(Feature.FRESCO)
            .wall(new Color(126, 108, 88), new Color(15, 12, 10),
                  new Color(198, 172, 138), new Color(28, 23, 18),
                  new Color(86, 74, 60), new Color(74, 78, 44),
                  new Color(222, 198, 162), new Color(16, 13, 11))
            .floor(new Color(92, 80, 66), new Color(10, 9, 8), new Color(126, 110, 92), new Color(13, 11, 9))
            .ceiling(new Color(26, 22, 18), new Color(4, 3, 3), new Color(48, 41, 33))
            .door(new Color(108, 80, 46), new Color(27, 20, 12),
                  new Color(168, 138, 82), new Color(58, 44, 26))
            .special(new Color(250, 196, 110), new Color(90, 68, 36))
            .fog(new Color(11, 9, 8))
            .sconces(4, new Color(255, 232, 168), new Color(240, 138, 40), new Color(236, 140, 48))
            .particles(ParticleStyle.SPORE, 30,
                       new Color(255, 176, 82, 110), new Color(200, 110, 40, 70), new Color(255, 216, 150, 50))
            .hud(new Color(11, 9, 8, 224), new Color(236, 196, 126), new Color(102, 84, 54))
            .minimap(new Color(11, 9, 8), new Color(104, 90, 74), new Color(168, 138, 82),
                     new Color(250, 206, 132), new Color(21, 18, 15), new Color(236, 196, 126))
            .build();

    /**
     * Bellorak's Iron Pit — a foundry sunk into the rock. Plate and rivet rather than masonry,
     * with the red of hot metal coming up through everything.
     */
    public static final DungeonTheme IRON_PIT = new Builder(WallStyle.BLOCK)
            .feature(Feature.NONE)
            .wall(new Color(112, 96, 92), new Color(14, 11, 11),
                  new Color(186, 164, 156), new Color(26, 21, 20),
                  new Color(78, 66, 62), new Color(96, 66, 40),
                  new Color(214, 190, 178), new Color(15, 12, 12))
            .floor(new Color(84, 72, 68), new Color(9, 8, 8), new Color(118, 102, 96), new Color(12, 10, 10))
            .ceiling(new Color(24, 20, 19), new Color(3, 3, 3), new Color(44, 37, 35))
            .door(new Color(96, 74, 50), new Color(24, 18, 12),
                  new Color(176, 130, 70), new Color(52, 40, 26))
            .special(new Color(255, 174, 92), new Color(96, 60, 30))
            .fog(new Color(10, 8, 8))
            .sconces(4, new Color(255, 224, 158), new Color(244, 116, 28), new Color(240, 120, 36))
            .particles(ParticleStyle.SPORE, 34,
                       new Color(255, 150, 60, 120), new Color(214, 92, 28, 78), new Color(255, 206, 140, 54))
            .hud(new Color(10, 8, 8, 224), new Color(240, 178, 108), new Color(104, 78, 52))
            .minimap(new Color(10, 8, 8), new Color(98, 84, 80), new Color(176, 130, 70),
                     new Color(255, 186, 110), new Color(20, 17, 16), new Color(240, 178, 108))
            .build();

    /**
     * Bellorak's Cradle of Shards — eight levels, and the end of the game. Polished shard-faces
     * over a floor that grows more mirror-like the further down you go, with the light splitting
     * further into colour at every level, so the rendering itself counts down.
     */
    public static final DungeonTheme CRADLE_OF_SHARDS = new Builder(WallStyle.SLAB)
            .feature(Feature.NONE)
            .mirror(0.62f, true, true)
            .glow(0.44f)
            .wall(new Color(150, 148, 176), new Color(12, 11, 18),
                  new Color(230, 226, 255), new Color(22, 20, 32),
                  new Color(96, 94, 118), new Color(178, 92, 210),
                  new Color(246, 242, 255), new Color(13, 12, 20))
            .floor(new Color(112, 110, 138), new Color(9, 8, 13), new Color(168, 164, 202), new Color(11, 10, 17))
            .ceiling(new Color(24, 22, 34), new Color(3, 3, 5), new Color(52, 48, 74))
            .door(new Color(112, 104, 150), new Color(24, 22, 34),
                  new Color(196, 186, 246), new Color(56, 52, 78))
            .special(new Color(238, 216, 255), new Color(92, 78, 118))
            .fog(new Color(9, 8, 14))
            .sconces(2, new Color(255, 246, 255), new Color(196, 150, 255), new Color(188, 148, 255))
            .particles(ParticleStyle.SPORE, 32,
                       new Color(226, 192, 255, 118), new Color(158, 122, 226, 74), new Color(255, 232, 255, 52))
            .hud(new Color(9, 8, 14, 224), new Color(226, 210, 255), new Color(96, 88, 122))
            .minimap(new Color(9, 8, 14), new Color(110, 106, 140), new Color(196, 186, 246),
                     new Color(238, 216, 255), new Color(19, 17, 26), new Color(226, 210, 255))
            .build();

    /**
     * Generic torch-lit crypt — grey granite, amber flame. The fallback for any dungeon promoted
     * to the textured renderer before it has a theme of its own.
     */
    public static final DungeonTheme CRYPT = new Builder(WallStyle.BLOCK)
            .wall(new Color(122, 114, 102), new Color(14, 13, 12),
                  new Color(196, 185, 165), new Color(25, 23, 20),
                  new Color(78, 73, 64), new Color(58, 76, 42),
                  new Color(206, 198, 180), new Color(15, 14, 12))
            .floor(new Color(80, 75, 66), new Color(9, 9, 8), new Color(114, 107, 94), new Color(11, 10, 9))
            .ceiling(new Color(23, 21, 19), new Color(4, 4, 3), new Color(42, 39, 34))
            .door(new Color(104, 78, 46), new Color(28, 21, 12),
                  new Color(158, 128, 74), new Color(56, 42, 24))
            .special(new Color(240, 200, 120), new Color(84, 66, 34))
            .fog(new Color(9, 8, 8))
            .sconces(4, new Color(255, 232, 172), new Color(240, 146, 44), new Color(232, 148, 56))
            .particles(ParticleStyle.SILT, 26,
                       new Color(190, 176, 148, 80), new Color(126, 116, 96, 55), new Color(228, 208, 166, 40))
            .hud(new Color(10, 9, 8, 224), new Color(226, 194, 124), new Color(96, 82, 54))
            .minimap(new Color(9, 9, 8), new Color(98, 92, 80), new Color(158, 128, 74),
                     new Color(240, 212, 132), new Color(19, 18, 16), new Color(226, 194, 124))
            .build();

    // ── Builder ──────────────────────────────────────────────────────────────

    private static final class Builder {
        final WallStyle wallStyle;
        Feature feature = Feature.NONE;
        Color wallNear, wallFar, wallLight, wallDark, wallGrain, wallMoss, wallHigh, mortar;
        Color floorNear, floorFar, floorDetail, floorGap;
        Color ceilNear, ceilFar, ceilDetail;
        Color doorNear, doorFar, doorFrame, doorPanel;
        Color specialNear, specialFar, fog;
        boolean sconces; int sconceEvery = 4;
        Color flameCore = Color.WHITE, flameOuter = Color.ORANGE, sconceGlow = Color.ORANGE;
        ParticleStyle particleStyle = ParticleStyle.SPORE; int particleCount = 30;
        Color particleA, particleB, particleC;
        Color hudBg, hudText, hudDim;
        Color mmBg, mmWall, mmDoor, mmPlayer, mmFog, mmSpecial;
        float reflectivity = 0f;
        boolean prismatic = false;
        boolean escalateWithDepth = false;
        float selfGlow = 0f;

        Builder(WallStyle s) { this.wallStyle = s; }

        Builder feature(Feature f) { this.feature = f; return this; }

        Builder glow(float amount) { this.selfGlow = amount; return this; }

        Builder mirror(float reflectivity, boolean prismatic, boolean escalate) {
            this.reflectivity = reflectivity;
            this.prismatic = prismatic;
            this.escalateWithDepth = escalate;
            return this;
        }

        Builder wall(Color near, Color far, Color light, Color dark,
                     Color grain, Color moss, Color high, Color mortarCol) {
            wallNear = near; wallFar = far; wallLight = light; wallDark = dark;
            wallGrain = grain; wallMoss = moss; wallHigh = high; mortar = mortarCol; return this;
        }
        Builder floor(Color near, Color far, Color detail, Color gap) {
            floorNear = near; floorFar = far; floorDetail = detail; floorGap = gap; return this;
        }
        Builder ceiling(Color near, Color far, Color detail) {
            ceilNear = near; ceilFar = far; ceilDetail = detail; return this;
        }
        Builder door(Color near, Color far, Color frame, Color panel) {
            doorNear = near; doorFar = far; doorFrame = frame; doorPanel = panel; return this;
        }
        Builder special(Color near, Color far) { specialNear = near; specialFar = far; return this; }
        Builder fog(Color c) { fog = c; return this; }
        Builder noSconces() { sconces = false; return this; }
        Builder sconces(int every, Color core, Color outer, Color glow) {
            sconces = true; sconceEvery = every;
            flameCore = core; flameOuter = outer; sconceGlow = glow; return this;
        }
        Builder particles(ParticleStyle style, int count, Color a, Color b, Color c) {
            particleStyle = style; particleCount = count;
            particleA = a; particleB = b; particleC = c; return this;
        }
        Builder hud(Color bg, Color text, Color dim) {
            hudBg = bg; hudText = text; hudDim = dim; return this;
        }
        Builder minimap(Color bg, Color wall, Color door, Color player, Color fogCol, Color special) {
            mmBg = bg; mmWall = wall; mmDoor = door; mmPlayer = player;
            mmFog = fogCol; mmSpecial = special; return this;
        }
        DungeonTheme build() { return new DungeonTheme(this); }
    }
}

package io.cannonforge.retroquest.overlay;
import static io.cannonforge.retroquest.overlay.OverlayTheme.AMBER;
import static io.cannonforge.retroquest.overlay.OverlayTheme.BG;
import static io.cannonforge.retroquest.overlay.OverlayTheme.BORDER_COL;
import static io.cannonforge.retroquest.overlay.OverlayTheme.CYAN_ACC;
import static io.cannonforge.retroquest.overlay.OverlayTheme.F_DESC;
import static io.cannonforge.retroquest.overlay.OverlayTheme.F_ITEM;
import static io.cannonforge.retroquest.overlay.OverlayTheme.F_KEY;
import static io.cannonforge.retroquest.overlay.OverlayTheme.F_LABEL;
import static io.cannonforge.retroquest.overlay.OverlayTheme.F_SMALL;
import static io.cannonforge.retroquest.overlay.OverlayTheme.F_TITLE;
import static io.cannonforge.retroquest.overlay.OverlayTheme.GOOD;
import static io.cannonforge.retroquest.overlay.OverlayTheme.PANEL_BG;
import static io.cannonforge.retroquest.overlay.OverlayTheme.PHOSPHOR;
import static io.cannonforge.retroquest.overlay.OverlayTheme.TEXT_BRIGHT;
import static io.cannonforge.retroquest.overlay.OverlayTheme.TEXT_DIM;
import static io.cannonforge.retroquest.overlay.OverlayTheme.entryEase;

import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.event.KeyEvent;

import io.cannonforge.retroquest.core.Retroquest;

/**
 * In-panel help overlay — "The Adventurer's Compendium".
 *
 * <p>Four tabbed pages (Controls / Combat / Spells / Tips & Lore), opened with F1,
 * slides in from the bottom, closed with Esc or F1.
 */
public class HelpOverlay {

    // ── Dimensions ────────────────────────────────────────────────────────────
    // Scaled: the fonts grow with DisplayScale, so the box and its padding must too
    // or the tabs clip and the body text runs over the key bar.
    private static final int OW  = OverlayTheme.scaled(640);
    private static final int OH  = OverlayTheme.scaled(460);
    private static final int PAD = OverlayTheme.scaled(14);

    // ── Entry animation ───────────────────────────────────────────────────────
    private static final long ENTRY_MS = 280;
    private long entryTime;

    // ── State ─────────────────────────────────────────────────────────────────
    private boolean active;
    private int     tab;       // 0=Controls, 1=Combat, 2=Spells, 3=Tips
    private int     scrollTop;

    // ── Tab labels ────────────────────────────────────────────────────────────
    private static final String[] TAB_LABELS = { "Controls", "Combat", "Spells", "Items & Gear", "The World", "Tips & Lore" };

    // ── Controls tab ──────────────────────────────────────────────────────────
    private static final String[][] MOVEMENT_KEYS = {
        { "\u2191\u2193\u2190\u2192", "Move (1st-person: step/turn)" },
        { "H J K",                    "Move W / S / N  (E: \u2192 only)" },
        { "E",                        "Enter / Interact" },
        { "T",                        "Talk to NPC" },
        { "M",                        "Minimap (dungeon)" },
        { "+/-",                      "Zoom In / Out" },
    };
    private static final String[][] MENU_KEYS = {
        { "Esc",                      "Close / cancel" },
        { "Enter",                    "Confirm / use" },
        { "Tab",                      "Next page, Buy/Sell" },
        { "\u2191\u2193 J K",         "Move selection" },
        { "1-9",                      "Pick a numbered option" },
        { "U",                        "Use / equip item" },
        { "D",                        "Drop item / delete save" },
        { "Y",                        "Confirm a warning" },
    };
    private static final String[][] ACTION_KEYS = {
        { "A",     "Attack!" },
        { "F",     "Flee (in combat)" },
        { "I",     "Inventory" },
        { "C",     "Spellbook" },
        { "L / O", "Quest Log" },
        { "S",     "Save Game" },
        { "Q",     "Quit" },
        { "F1",    "You're already here." },
    };

    // ── Combat tab: { header, body } ──────────────────────────────────────────
    private static final String[][] COMBAT_SECTIONS = {
        { "HOW IT STARTS",
          "Monsters roam the overworld and lurk in every dungeon corridor. Step into one and the fight is on. There is no sneaking, no parleying, no running before the first blow lands." },
        { "ATTACKING",
          "[A]ttack swings your equipped weapon. Strength drives the damage roll. Dexterity determines whether the blow connects at all. A rusty dagger is better than bare hands — barely." },
        { "SPELLCASTING",
          "Press [C] during combat to open your spellbook. Spells cost casts from your per-level pool — not a mana bar. You have a fixed number of casts per tier per rest. When they're gone, they're gone until you sleep at an inn." },
        { "BUFFS & STATUS",
          "Many spells persist across rounds. Haste grants a bonus attack. Prayer gives +2 AC and to-hit. Holy Armor adds +5 AC. Bless adds +1 to both. Protection from Evil gives +3 AC. Invisibility makes your next strike auto-hit, then fades." },
        { "ENEMY RESISTANCES",
          "Not every foe is equally vulnerable. Undead take double damage from Cleric spells. Some monsters can resist spells outright — you'll see it in the log. If magic bounces, use steel." },
        { "STATUS EFFECTS",
          "Enemies can poison, stun, blind, or frighten you. Each effect wears off over turns. Restoration clears them all instantly. Dispel Magic resets the entire board — your buffs and theirs." },
        { "FLEEING",
          "Press [F] during combat to attempt escape. The enemy may still land a parting blow as you sprint for the exit. A coward who survives can return better-equipped. A dead hero cannot." },
        { "EXPERIENCE & LEVELING",
          "Monsters grant XP on death. Fill the XP bar and your level rises: HP and stats both improve, and new spells unlock automatically. Dungeon monsters grant more XP — because they are, in every measurable way, more dangerous." },
        { "RECOVERY",
          "Inns in towns and some dungeon levels fully restore HP and replenish spell casts. Dungeon fountains restore HP on the spot. Healing potions patch you up mid-crawl. Open every chest you find — they often contain potions." },
    };

    // ── Spells tab: { name, "Lv N", "COMBAT"/"MAP"/"BOTH", description } ──────
    // Entries with name=null are level headers; description holds the header text.
    private static final String[][] MAGE_SPELLS = {
        // Level headers use null name
        { null, null, null, "── LEVEL 1 ──" },
        { "Magic Missile",  "Lv 1", "COMBAT",
          "Never misses. A reliable bolt of arcane force — your bread and butter at early levels. INT bonus applies to damage." },
        { "Sleep",          "Lv 1", "COMBAT",
          "Puts the enemy to sleep for several turns. It just... stands there while you make decisions. Works on most low-level creatures." },
        { "Charm Monster",  "Lv 1", "COMBAT",
          "Chance the creature flees scales with level (40% base, +3% per level, max 70%). The other times it stands idle, charmed." },
        { "Shield",         "Lv 1", "COMBAT",
          "A shimmering barrier of force. +2 AC for the fight. The mage's answer to 'why are you in the front row?'" },
        { null, null, null, "── LEVEL 2 ──" },
        { "Fireball",       "Lv 2", "COMBAT",
          "The classic. A sphere of expanding flame for heavy damage. If you can only learn one spell, learn this one first." },
        { "Lightning Bolt", "Lv 2", "COMBAT",
          "A searing bolt that pierces magical wards — ignores half the target's spell resistance. Excellent against warded foes." },
        { "Invisibility",   "Lv 2", "COMBAT",
          "You vanish. Your next attack automatically hits — then you reappear, which is somewhat less impressive." },
        { null, null, null, "── LEVEL 3 ──" },
        { "Ice Storm",      "Lv 3", "COMBAT",
          "Hail and frozen winds hammer the target. 30% chance to slow the enemy, causing it to lose its next turn." },
        { "Dispel Magic",   "Lv 3", "COMBAT",
          "The anti-mage spell. Halves the enemy's spell resistance for the rest of combat AND cleanses your own debuffs. Cast before Fireball for guaranteed impact." },
        { "Teleport",       "Lv 3", "MAP",
          "Instantly returns you to the last town you visited. Best cast before you die rather than after." },
        { null, null, null, "── LEVEL 4 ──" },
        { "Cone of Cold",   "Lv 4", "COMBAT",
          "Absolute zero. Cannot be resisted by any magical ward — guaranteed to hit. Moderate damage but utterly reliable." },
        { "Cloudkill",      "Lv 4", "COMBAT",
          "Dense poisonous vapors fill the area. Poisons the enemy — they take damage each round for 3-5 turns." },
        { "Haste",          "Lv 4", "COMBAT",
          "Time warps around you. You get a bonus attack this combat. Best used when you need one more hit to finish things." },
        { null, null, null, "── LEVEL 5 ──" },
        { "Chain Lightning","Lv 5", "COMBAT",
          "An arc of living lightning that pierces wards. Ignores half spell resistance. Among the highest single-target damage available." },
        { "Death Spell",    "Lv 5", "COMBAT",
          "Kill chance scales with your level advantage over the target (20% base, +5% per level above). Deals damage on failure." },
        { "Scry",           "Lv 5", "MAP",
          "Your mind's eye expands. In dungeons, reveals the entire layout of the current level. Useless on the overworld." },
        { null, null, null, "── LEVEL 6 ──" },
        { "Meteor Swarm",   "Lv 6", "COMBAT",
          "The sky falls. Devastating damage from incoming meteors. Tends to end conversations permanently." },
        { "Power Word Kill","Lv 6", "COMBAT",
          "Instant death if enemy HP is below twice your max HP. Otherwise heavy damage. One word. Very few survive it." },
        { "Wish",           "Lv 6", "BOTH",
          "Reality bends to your will. In combat: 50% chance full heal, 50% chance triple-power devastation. On the map: always full heal." },
        { "Time Stop",      "Lv 6", "COMBAT",
          "The monster's turn simply does not happen this round. No roll. No resistance. Time just stops. Invaluable against high-damage foes." },
        { null, null, null, "\u2500\u2500 QUEST REWARD \u2500\u2500" },
        { "Entangle",       "Lv \u2014", "COMBAT",
          "Learned from Rootspeaker Thenna on Sylvandar. Vines erupt from the ground, dealing half damage and immobilizing the target for several rounds. The forest remembers its debts." },
    };

    private static final String[][] CLERIC_SPELLS = {
        { null, null, null, "── LEVEL 1 ──" },
        { "Cure Light Wounds",     "Lv 1", "BOTH",
          "Restores a small amount of HP in combat or on the map. Your workhorse survival spell. Cast early and often." },
        { "Protection from Evil",  "Lv 1", "COMBAT",
          "+3 AC for the entire fight. Best cast before the first blow lands. Stacks with Prayer, Shield, and Holy Armor." },
        { null, null, null, "── LEVEL 2 ──" },
        { "Cure Serious Wounds",   "Lv 2", "BOTH",
          "More HP than Cure Light. Cast when things are getting genuinely bad. Not just concerning — bad." },
        { "Bless",                 "Lv 2", "COMBAT",
          "+2 damage for the rest of combat. Pure offense — stacks with Prayer's to-hit bonus for devastating melee rounds." },
        { "Turn Undead",           "Lv 2", "COMBAT",
          "70% chance of instantly destroying skeletons, zombies, ghosts, vampires, and their kin. Against the living, it still deals damage — just considerably less." },
        { null, null, null, "── LEVEL 3 ──" },
        { "Cure Critical Wounds",  "Lv 3", "BOTH",
          "Significant healing. Cast when your health bar looks more red than green. Don't wait until you're at 3 HP." },
        { "Prayer",                "Lv 3", "COMBAT",
          "+2 AC and +2 to-hit for the rest of combat. Stack with Bless for offense, Protection from Evil for defense." },
        { "Holy Word",             "Lv 3", "COMBAT",
          "A sacred word of power. Deals double damage against demons, undead, and evil creatures. Single damage against everything else." },
        { null, null, null, "── LEVEL 4 ──" },
        { "Heal",                  "Lv 4", "BOTH",
          "Restores you to full HP. Full. Whatever your max is — you have it again. The spell that ends desperate stands." },
        { "Resist Elements",       "Lv 4", "COMBAT",
          "All incoming spell and breath damage halved for this combat. Works against fire, ice, lightning — everything magical." },
        { "Restoration",           "Lv 4", "BOTH",
          "Moderate healing plus all status effects cleared. Poisoned, blinded, stunned? One cast clears them all. Use Heal for raw HP." },
        { null, null, null, "── LEVEL 5 ──" },
        { "Holy Armor",            "Lv 5", "COMBAT",
          "+5 AC for the rest of combat. Stacked with Prayer and Protection from Evil, you become a walking fortress. Monsters will notice." },
        { "Flame Strike",          "Lv 5", "COMBAT",
          "Divine fire descends from above in a pillar of heavenly judgment. Like Fireball but sanctioned by the gods. Heavy damage." },
        { null, null, null, "── LEVEL 6 ──" },
        { "Divine Intervention",   "Lv 6", "COMBAT",
          "60% chance of massive damage to the enemy; 40% chance it simply flees in holy terror. The gods intervene as they see fit." },
        { "Sanctuary",             "Lv 6", "COMBAT",
          "Guaranteed escape from combat — no dice roll, no chance of failure. But you forfeit all XP and gold. The divine safety net." },
        { "Resurrection",          "Lv 6", "MAP",
          "Arms a divine ward. If slain while this is active, you awaken at 50% HP instead of dying. Cast it before every dangerous descent. Then cast it again." },
    };

    // ── Tips tab: { header (or null), body } ──────────────────────────────────
    private static final String[][] TIPS_SECTIONS = {
        { "SURVIVAL", null },
        { null, "\u2731 Your food supply depletes with every step on the overworld. Watch it. Running dry deals damage each step until you eat or rest at an inn." },
        { null, "\u2731 Always sleep at an inn before descending deeper. Full HP and a full spell pool are the difference between exploring level 5 and haunting it." },
        { null, "\u2731 Save often. Autosave fires every 50 steps, but a surprise fight can end things in three rounds. [S] is right there." },
        { null, "\u2731 Healing potions stack. Carry several. The merchant restocks. You do not get a second life — unless Resurrection says otherwise." },
        { null, "\u2731 If your HP drops below a third, retreat. A live adventurer who makes it to an inn is a success story." },
        { null, "\u2731 Spell slots refill completely at any inn. Rest is not a luxury — it is your ammunition." },
        { "EXPLORATION", null },
        { null, "\u2731 Talk to everyone. NPCs give quests, keys, and occasionally knowledge that isn't written down anywhere else." },
        { null, "\u2731 Locked doors need specific keys: wooden doors take a Rusty Key, iron gates need an Iron Key, magical barriers need a Magic Crystal. Ask around." },
        { null, "\u2731 The dungeon has 50 levels. Levels 1-5 are manageable. Levels 6-10 are where overconfidence gets punished. Below that, bring everything." },
        { null, "\u2731 Teleporters on dungeon floors move you somewhere random on the same level. Disorienting the first time; strategic once you know the layout." },
        { null, "\u2731 Pits drop you one level down without warning. Not always a disaster — sometimes an express elevator. Usually not." },
        { null, "\u2731 Dungeon inns appear on some floors. When you find one, use it. Don't save it for later. Later may not happen." },
        { null, "\u2731 The Altar blesses those who approach. The blessing scales with Wisdom. The Altar has neither confirmed nor denied this." },
        { null, "\u2731 The Glowing Cube is unpredictable. It has been known to heal, harm, teleport, transform, and on one recorded occasion, produce a wheel of cheese. Press [E] and find out." },
        { "QUESTS & ITEMS", null },
        { null, "\u2731 Check your [L]og often. Completed quests show a \u25ce icon — turn them in. NPCs do not come looking for you." },
        { null, "\u2731 Scrolls permanently teach the spell on them. Read them immediately — a scroll in your bag is wasted potential." },
        { null, "\u2731 Wands cast their spell a fixed number of times, then become expensive sticks. Check charges before relying on one." },
        { null, "\u2731 Equip better gear as you find it. A sword beats a dagger. Plate beats leather. You are not a museum." },
        { null, "\u2731 The Throne on the deepest reachable level has never been claimed by a living adventurer. Several have tried. The dungeon keeps excellent records." },
        { "LORE & SECRETS", null },
        { null, "\u2731 Moonhaven is built atop a ley line. Mages find the magic feels thicker there. Merchants find it just feels expensive." },
        { null, "\u2731 Kael, the wandering mage at the Lirandel crossroads, knows considerably more than he admits. His first quest is freely given. Subsequent ones require earning his trust." },
        { null, "\2731 Forge Keep's forge runs day and night. The smith does not appear to sleep. The residents have decided this is normal." },
        { null, "\u2731 The dungeon beneath the ancient fortress was sealed for reasons that historians describe as 'sufficient and not up for debate.'" },
        { null, "\u2731 Resurrection must be cast before you die. This is not a limitation of the spell. It is a limitation of death." },
        { null, "" },
        { null, "  \"The Compendium was compiled from the notes of" },
        { null, "   seventeen adventurers. Fourteen are dead." },
        { null, "   The other three refused to discuss what they saw.\"" },
    };

    // ── Items & Gear tab ──────────────────────────────────────────────────────
    private static final String[][] ITEMS_SECTIONS = {
        { "EQUIPMENT SLOTS",
          "You have seven equipment slots: weapon, armor, helm, amulet, shield, and two rings. Each piece contributes to your combat stats. Use [I] to open your inventory, highlight an item, and press [U] to equip it. The old piece returns to your bag." },
        { "ITEM TYPES",
          "Potions heal on use and stack. Scrolls permanently teach you the spell written on them — read them immediately. Wands cast a spell a fixed number of times, then crumble. Keys go to your permanent key ring and never take inventory space." },
        { "RARITY",
          "Items range from Common (white) to Legendary (gold). Rarer items drop less often but sell for more. The five tiers: Common, Uncommon (green), Rare (blue), Epic (purple), Legendary (gold). Dungeon depth and monster level determine what drops." },
        { "SHOPS",
          "Town shopkeepers stock items based on the local economy. Press [Tab] in the shop to switch between BUY and SELL. Sell prices vary by item type — weapons and armor fetch the best prices. Healing potions are always available and always worth buying." },
        { "EFFECTS & ENCHANTMENTS",
          "Some equipment carries passive effects: Protection adds to AC, Regeneration heals you every few steps, and Spell Resistance reduces the chance enemy magic lands. Amulets and rings are the primary carriers of magical effects. Check item descriptions carefully." },
        { "THE KEY RING",
          "Quest keys (Key of Tides, Key of Embers, etc.) are stored on a permanent key ring separate from your 20-slot inventory. Keys cannot be dropped, sold, or lost. Once earned, they persist forever. Locked doors and island portals check your key ring automatically." },
        { "INVENTORY TIPS",
          "You have 20 inventory slots. Consumables stack (potions up to 99). If your inventory is full when loot drops, you lose it — keep a few slots open. Drop junk you don't need with [D]. Better gear appears at deeper dungeon levels." },
    };

    // ── The World tab ───────────────────────────────────────────────────────────
    private static final String[][] WORLD_SECTIONS = {
        { "THE SEVEN ISLANDS",
          "Aqualonia is an archipelago of seven islands, each ruled by a god. You begin on Lirandel's Awakening Isle and progress by earning keys from each island's divine trial. Each island is harder than the last." },
        { "ISLAND PROGRESSION",
          "Lirandel (Lv 1-5) \u2192 Pyralis (Lv 6-10) \u2192 Zephyrion (Lv 8-11) \u2192 Sylvandar (Lv 12-15) \u2192 Thalorax (Lv 16-19) \u2192 Umbryn (Lv 20-23) \u2192 Bellorak (Lv 24-27) \u2192 The Cradle of Shards (Lv 28+). Each portal requires the previous island's key." },
        { "THE SEVEN GODS",
          "Each god embodies a primal dream of the World-Serpent Aqualon: Lirandel (protection), Pyralis (transformation), Zephyrion (freedom), Sylvandar (patience), Thalorax (depth), Umbryn (memory), and Bellorak (bonds). They shattered their creator and now war over the remains." },
        { "DIVINE FAVOR",
          "Your choices shift your standing with each god. Favor starts at nothing with all seven and climbs as you finish their quests and trials — and some choices raise one god by lowering another, so the question is who you favour AND who you are willing to offend. At the Cradle your standing decides which paths open: a Champion ending needs one god at 60 and ten clear of every rival; True Unbound needs all seven at 55 or more with no favourite; the New Serpent ignores favor entirely — it opens only for four Shards of Corruption." },
        { "PERMANENT BOONS",
          "Each island's god trial awards a permanent boon — a stat bonus that lasts the entire game. The pure path and corruption path on each island grant different boons. Choose wisely: these cannot be undone." },
        { "TOWNS & SERVICES",
          "Every island has 2-4 towns. Inns fully heal you and restore spell casts. Shops sell equipment and potions. NPCs give quests and share lore. Talk to everyone — some knowledge is not written down anywhere." },
        { "THE DUNGEON",
          "A shared procedural dungeon spans 50 levels beneath the world. Each level is a 200\u00d7200 maze of corridors, rooms, and hidden specials. Deeper levels hold better loot and deadlier monsters. Authored dungeons on later islands offer hand-crafted challenges." },
        { "DUNGEON SPECIALS",
          "Altars grant divine blessings (or curses). Fountains have 11 possible outcomes. The Glowing Cube teleports you to another level. Thrones reward the bold and punish the unlucky. Chests contain loot. Inns on level 1 offer rest. Pits drop you down a level." },
        { "THE UNBOUND",
          "You are the Unbound — the only mortal born under the Null Alignment. No god can read your mind or compel your actions. Every god wants you as an ally. None of them can be fully trusted. The choices you make across all seven islands determine whether you become a champion, a tyrant, or something else entirely." },
    };

    // ─────────────────────────────────────────────────────────────────────────

    public HelpOverlay(Retroquest game) {
    }

    // ── Public API ────────────────────────────────────────────────────────────
    public boolean isActive() { return active; }

    public void open() {
        if (active) return;
        active    = true;
        tab       = 0;
        scrollTop = 0;
        entryTime = System.currentTimeMillis();
    }

    public void close() {
        active = false;
    }

    // ── Key handling ─────────────────────────────────────────────────────────
    public void handleKey(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_ESCAPE, KeyEvent.VK_F1 -> close();
            case KeyEvent.VK_TAB, KeyEvent.VK_RIGHT, KeyEvent.VK_L -> nextTab();
            case KeyEvent.VK_LEFT,  KeyEvent.VK_H -> prevTab();
            case KeyEvent.VK_UP,    KeyEvent.VK_K -> scrollUp();
            case KeyEvent.VK_DOWN,  KeyEvent.VK_J -> scrollDown();
        }
    }

    private void nextTab() { tab = (tab + 1) % TAB_LABELS.length; scrollTop = 0; }
    private void prevTab() { tab = (tab + TAB_LABELS.length - 1) % TAB_LABELS.length; scrollTop = 0; }
    private void scrollUp()   { if (scrollTop > 0) scrollTop--; }
    private void scrollDown() { scrollTop++; }

    // ── Paint ─────────────────────────────────────────────────────────────────
    public void paint(Graphics2D g, int W, int H) {
        if (!active) return;
        float ease = entryEase(entryTime, ENTRY_MS);
        int ow = Math.min(OW, W - 20);
        int oh = Math.min(OH, H - 20);
        int ox = (W - ow) / 2;
        int oy = (H - oh) / 2 + (int)((1f - ease) * (oh + 40));
        paintPanel(g, ox, oy, ow, oh);
    }

    /** Row pitch for a font: its natural height plus a little breathing room. */
    private static int lineHeight(FontMetrics fm) {
        return fm.getHeight() + OverlayTheme.scaled(3);
    }

    private void paintPanel(Graphics2D g, int ox, int oy, int ow, int oh) {
        final int TITLE_H   = lineHeight(g.getFontMetrics(F_TITLE)) + OverlayTheme.scaled(10);
        final int TAB_H     = lineHeight(g.getFontMetrics(F_LABEL)) + OverlayTheme.scaled(8);
        final int KEY_BAR_H = lineHeight(g.getFontMetrics(F_KEY))   + OverlayTheme.scaled(10);
        final int CONTENT_Y = oy + TITLE_H + TAB_H + 4;
        final int CONTENT_H = oh - TITLE_H - TAB_H - KEY_BAR_H - 8;

        // ── Background ──
        g.setColor(BG);
        g.fillRect(ox, oy, ow, oh);
        g.setColor(BORDER_COL);
        g.drawRect(ox, oy, ow - 1, oh - 1);

        // ── Title bar ──
        g.setColor(new Color(20, 18, 6));
        g.fillRect(ox + 1, oy + 1, ow - 2, TITLE_H - 1);
        g.setColor(BORDER_COL);
        g.drawLine(ox, oy + TITLE_H, ox + ow - 1, oy + TITLE_H);
        g.setFont(F_TITLE);
        g.setColor(AMBER);
        String title = "\u2756 ADVENTURER'S COMPENDIUM  \u2756";
        FontMetrics fmT = g.getFontMetrics();
        g.drawString(title, ox + (ow - fmT.stringWidth(title)) / 2,
                     oy + (TITLE_H + fmT.getAscent() - fmT.getDescent()) / 2);

        // ── Tab bar ──
        int tabBarY = oy + TITLE_H;
        g.setColor(PANEL_BG);
        g.fillRect(ox + 1, tabBarY + 1, ow - 2, TAB_H - 1);
        g.setColor(BORDER_COL);
        g.drawLine(ox, tabBarY + TAB_H, ox + ow - 1, tabBarY + TAB_H);

        g.setFont(F_LABEL);
        FontMetrics fmLbl = g.getFontMetrics();
        int labelsW = 0;
        for (String label : TAB_LABELS) labelsW += fmLbl.stringWidth(label);
        // Squeeze the gaps rather than letting the last tabs run off the panel
        int slack  = ow - OverlayTheme.scaled(24) - labelsW;
        int tabGap = Math.max(OverlayTheme.scaled(6),
                        Math.min(OverlayTheme.scaled(22), slack / (TAB_LABELS.length - 1)));
        int tabX    = ox + OverlayTheme.scaled(12);
        int tabBase = tabBarY + (TAB_H + fmLbl.getAscent() - fmLbl.getDescent()) / 2;
        for (int i = 0; i < TAB_LABELS.length; i++) {
            String label = TAB_LABELS[i];
            int lw = fmLbl.stringWidth(label);
            g.setColor(i == tab ? AMBER : TEXT_DIM);
            g.drawString(label, tabX, tabBase);
            if (i == tab) {
                g.setColor(AMBER);
                g.fillRect(tabX, tabBarY + TAB_H - 2, lw, 2);
            }
            tabX += lw + tabGap;
        }

        // ── Content area (clipped) ──
        g.setClip(ox + 1, CONTENT_Y, ow - 2, CONTENT_H);
        switch (tab) {
            case 0 -> paintControls(g, ox, CONTENT_Y, ow, CONTENT_H);
            case 1 -> paintCombat(g, ox, CONTENT_Y, ow, CONTENT_H);
            case 2 -> paintSpells(g, ox, CONTENT_Y, ow, CONTENT_H);
            case 3 -> paintSections(g, ox, CONTENT_Y, ow, CONTENT_H, ITEMS_SECTIONS);
            case 4 -> paintSections(g, ox, CONTENT_Y, ow, CONTENT_H, WORLD_SECTIONS);
            case 5 -> paintTips(g, ox, CONTENT_Y, ow, CONTENT_H);
        }
        g.setClip(null);

        // ── Key bar ──
        int keyBarY = oy + oh - KEY_BAR_H;
        g.setColor(PANEL_BG);
        g.fillRect(ox + 1, keyBarY, ow - 2, KEY_BAR_H);
        g.setColor(BORDER_COL);
        g.drawLine(ox, keyBarY, ox + ow - 1, keyBarY);
        OverlayTheme.paintKeyBadges(g, ox, keyBarY, ow, KEY_BAR_H,
            new String[][]{{"Tab", "Next Page"}, {"\u2190\u2192", "Prev/Next"}, {"Esc", "Close"}}, CYAN_ACC);
    }

    // ── Tab 0: Controls ───────────────────────────────────────────────────────
    private void paintControls(Graphics2D g, int ox, int cy, int cw, int ch) {
        int x     = ox + PAD;
        int y     = cy + PAD;
        int lineH = lineHeight(g.getFontMetrics(F_ITEM));
        int colW  = (cw - PAD * 2) / 2;
        int leftY = paintControlSection(g, "MOVEMENT", MOVEMENT_KEYS, x, y, colW, lineH);
        leftY = paintControlSection(g, "MENUS", MENU_KEYS, x, leftY + lineH, colW, lineH);
        int rightY = paintControlSection(g, "ACTIONS", ACTION_KEYS, x + colW + 8, y, colW, lineH);

        g.setFont(F_DESC);
        g.setColor(TEXT_DIM);
        g.drawString("Autosaves every 50 steps to the AUTO slot.", x, Math.max(leftY, rightY));
    }

    private int paintControlSection(Graphics2D g, String header, String[][] keys,
                                     int x, int y, int colW, int lineH) {
        g.setFont(F_LABEL);
        g.setColor(AMBER);
        g.drawString(header, x, y);
        FontMetrics fmH = g.getFontMetrics();
        g.setColor(new Color(AMBER.getRed(), AMBER.getGreen(), AMBER.getBlue(), 60));
        g.drawLine(x + fmH.stringWidth(header) + 4, y - 4, x + colW - 4, y - 4);
        y += lineH + 2;

        g.setFont(F_ITEM);
        FontMetrics fmI = g.getFontMetrics();
        for (String[] pair : keys) {
            g.setColor(CYAN_ACC);
            String badge = "[" + pair[0] + "]";
            g.drawString(badge, x + 2, y);
            g.setColor(TEXT_BRIGHT);
            g.drawString(pair[1], x + fmI.stringWidth(badge) + 8, y);
            y += lineH;
        }
        return y;
    }

    // ── Tab 1: Combat ─────────────────────────────────────────────────────────
    private void paintCombat(Graphics2D g, int ox, int cy, int cw, int ch) {
        int x     = ox + PAD;
        int y     = cy + PAD;
        int lineH = lineHeight(g.getFontMetrics(F_DESC));
        int wrapW = cw - PAD * 2;

        g.setFont(F_DESC);
        FontMetrics fmD = g.getFontMetrics();
        g.setFont(F_LABEL);
        FontMetrics fmL = g.getFontMetrics();

        java.util.List<String>  lines    = new java.util.ArrayList<>();
        java.util.List<Boolean> isHeader = new java.util.ArrayList<>();

        for (String[] section : COMBAT_SECTIONS) {
            lines.add(section[0]);
            isHeader.add(true);
            for (String w : OverlayTheme.wordWrap(fmD, section[1], wrapW)) {
                lines.add(w);
                isHeader.add(false);
            }
            lines.add("");
            isHeader.add(false);
        }

        int maxVisible = ch / lineH;
        scrollTop = Math.max(0, Math.min(scrollTop, Math.max(0, lines.size() - maxVisible)));

        for (int i = scrollTop; i < lines.size() && y < cy + ch - 4; i++) {
            String line = lines.get(i);
            if (line.isEmpty()) { y += lineH / 2; continue; }
            if (isHeader.get(i)) {
                g.setFont(F_LABEL);
                g.setColor(AMBER);
                g.drawString(line, x, y);
                g.setColor(new Color(AMBER.getRed(), AMBER.getGreen(), AMBER.getBlue(), 50));
                g.drawLine(x + fmL.stringWidth(line) + 4, y - 4, x + wrapW - 4, y - 4);
            } else {
                g.setFont(F_DESC);
                g.setColor(TEXT_BRIGHT);
                g.drawString("  " + line, x, y);
            }
            y += lineH;
        }
        OverlayTheme.paintScrollIndicators(g, scrollTop, maxVisible, lines.size(), ox, cy, cw, ch);
    }

    // ── Tab 2: Spells ─────────────────────────────────────────────────────────
    private void paintSpells(Graphics2D g, int ox, int cy, int cw, int ch) {
        int x     = ox + PAD;
        int y     = cy + PAD;
        int lineH = lineHeight(g.getFontMetrics(F_DESC));
        int wrapW = cw - PAD * 2;

        g.setFont(F_DESC);
        FontMetrics fmD = g.getFontMetrics();
        g.setFont(F_LABEL);
        FontMetrics fmL = g.getFontMetrics();
        g.setFont(F_KEY);
        FontMetrics fmK = g.getFontMetrics();

        // line types: 0=body, 1=section divider, 2=spell name row
        java.util.List<String>  lines    = new java.util.ArrayList<>();
        java.util.List<Integer> types    = new java.util.ArrayList<>();
        java.util.List<String>  meta     = new java.util.ArrayList<>(); // "TYPE · USAGE" for spell rows

        // Build MAGE section
        lines.add("MAGE SPELLS"); types.add(1); meta.add("");
        buildSpellLines(MAGE_SPELLS, lines, types, meta, fmD, wrapW);
        lines.add(""); types.add(0); meta.add("");
        // Build CLERIC section
        lines.add("CLERIC SPELLS"); types.add(1); meta.add("");
        buildSpellLines(CLERIC_SPELLS, lines, types, meta, fmD, wrapW);

        int maxVisible = ch / lineH;
        scrollTop = Math.max(0, Math.min(scrollTop, Math.max(0, lines.size() - maxVisible)));

        for (int i = scrollTop; i < lines.size() && y < cy + ch - 4; i++) {
            String line = lines.get(i);
            int    type = types.get(i);
            String md   = meta.get(i);

            if (line.isEmpty()) { y += lineH / 2; continue; }

            switch (type) {
                case 1 -> { // major section header (MAGE / CLERIC)
                    g.setFont(F_LABEL);
                    g.setColor(AMBER);
                    g.drawString(line, x, y);
                    g.setColor(new Color(AMBER.getRed(), AMBER.getGreen(), AMBER.getBlue(), 60));
                    g.drawLine(x + fmL.stringWidth(line) + 4, y - 4, x + wrapW - 4, y - 4);
                }
                case 2 -> { // level sub-header
                    g.setFont(F_SMALL);
                    g.setColor(TEXT_DIM);
                    g.drawString(line, x + 4, y);
                }
                case 3 -> { // spell name row: name on left, badges on right
                    g.setFont(F_LABEL);
                    g.setColor(TEXT_BRIGHT);
                    g.drawString(line, x + 4, y);
                    // usage badge on far right
                    if (md != null && !md.isEmpty()) {
                        g.setFont(F_SMALL);
                        Color badgeCol = md.equals("MAP")  ? PHOSPHOR :
                                         md.equals("BOTH") ? GOOD     : CYAN_ACC;
                        g.setColor(badgeCol);
                        int bw = fmK.stringWidth(md);
                        g.drawString(md, ox + wrapW + PAD - bw, y);
                    }
                }
                default -> { // description body
                    g.setFont(F_DESC);
                    g.setColor(TEXT_DIM);
                    g.drawString("    " + line, x, y);
                }
            }
            y += lineH;
        }
        OverlayTheme.paintScrollIndicators(g, scrollTop, maxVisible, lines.size(), ox, cy, cw, ch);
    }

    /** Populates line/type/meta lists from a spell table. */
    private void buildSpellLines(String[][] spells,
                                  java.util.List<String> lines,
                                  java.util.List<Integer> types,
                                  java.util.List<String> meta,
                                  FontMetrics fmD, int wrapW) {
        for (String[] entry : spells) {
            if (entry[0] == null) {
                // Level sub-header stored in entry[3]
                lines.add(entry[3]); types.add(2); meta.add("");
                continue;
            }
            // Spell name row: name + level badge
            lines.add(entry[0] + "  [" + entry[1] + "]");
            types.add(3);
            meta.add(entry[2]);
            // Description wrapped
            for (String w : OverlayTheme.wordWrap(fmD, entry[3], wrapW - 8)) {
                lines.add(w);
                types.add(0);
                meta.add("");
            }
        }
    }

    // ── Tab 3: Tips & Lore ────────────────────────────────────────────────────
    private void paintTips(Graphics2D g, int ox, int cy, int cw, int ch) {
        int x     = ox + PAD;
        int y     = cy + PAD;
        int lineH = lineHeight(g.getFontMetrics(F_DESC));
        int wrapW = cw - PAD * 2;

        g.setFont(F_DESC);
        FontMetrics fmD = g.getFontMetrics();
        g.setFont(F_LABEL);
        FontMetrics fmL = g.getFontMetrics();

        // 0=body, 1=header, 2=quote
        java.util.List<String>  lines    = new java.util.ArrayList<>();
        java.util.List<Integer> lineType = new java.util.ArrayList<>();

        for (String[] entry : TIPS_SECTIONS) {
            String header = entry[0];
            String body   = entry[1];
            if (header != null) {
                lines.add(header); lineType.add(1);
                if (body == null) continue;
            }
            if (body == null) continue;
            if (body.isEmpty()) { lines.add(""); lineType.add(0); continue; }
            boolean isQuote = body.startsWith("  \"") || body.startsWith("   ");
            for (String w : OverlayTheme.wordWrap(fmD, body, wrapW)) {
                lines.add(w); lineType.add(isQuote ? 2 : 0);
            }
        }

        int maxVisible = ch / lineH;
        scrollTop = Math.max(0, Math.min(scrollTop, Math.max(0, lines.size() - maxVisible)));

        for (int i = scrollTop; i < lines.size() && y < cy + ch - 4; i++) {
            String line = lines.get(i);
            int    type = lineType.get(i);
            if (line.isEmpty()) { y += lineH / 2; continue; }
            switch (type) {
                case 1 -> {
                    g.setFont(F_LABEL); g.setColor(AMBER);
                    g.drawString(line, x, y);
                    g.setColor(new Color(AMBER.getRed(), AMBER.getGreen(), AMBER.getBlue(), 55));
                    g.drawLine(x + fmL.stringWidth(line) + 4, y - 4, x + wrapW - 4, y - 4);
                }
                case 2 -> { g.setFont(F_DESC); g.setColor(new Color(180, 140, 40)); g.drawString(line, x, y); }
                default -> { g.setFont(F_DESC); g.setColor(TEXT_BRIGHT); g.drawString(line, x, y); }
            }
            y += lineH;
        }
        OverlayTheme.paintScrollIndicators(g, scrollTop, maxVisible, lines.size(), ox, cy, cw, ch);
    }

    // ── Generic section painter (Items & Gear, The World) ────────────────────
    private void paintSections(Graphics2D g, int ox, int cy, int cw, int ch,
                                String[][] sections) {
        int x     = ox + PAD;
        int y     = cy + PAD;
        int lineH = lineHeight(g.getFontMetrics(F_DESC));
        int wrapW = cw - PAD * 2;

        g.setFont(F_DESC);
        FontMetrics fmD = g.getFontMetrics();
        g.setFont(F_LABEL);
        FontMetrics fmL = g.getFontMetrics();

        java.util.List<String>  lines    = new java.util.ArrayList<>();
        java.util.List<Boolean> isHeader = new java.util.ArrayList<>();

        for (String[] section : sections) {
            lines.add(section[0]);
            isHeader.add(true);
            for (String w : OverlayTheme.wordWrap(fmD, section[1], wrapW)) {
                lines.add(w);
                isHeader.add(false);
            }
            lines.add("");
            isHeader.add(false);
        }

        int maxVisible = ch / lineH;
        scrollTop = Math.max(0, Math.min(scrollTop, Math.max(0, lines.size() - maxVisible)));

        for (int i = scrollTop; i < lines.size() && y < cy + ch - 4; i++) {
            String line = lines.get(i);
            if (line.isEmpty()) { y += lineH / 2; continue; }
            if (isHeader.get(i)) {
                g.setFont(F_LABEL);
                g.setColor(AMBER);
                g.drawString(line, x, y);
                g.setColor(new Color(AMBER.getRed(), AMBER.getGreen(), AMBER.getBlue(), 50));
                g.drawLine(x + fmL.stringWidth(line) + 4, y - 4, x + wrapW - 4, y - 4);
            } else {
                g.setFont(F_DESC);
                g.setColor(TEXT_BRIGHT);
                g.drawString("  " + line, x, y);
            }
            y += lineH;
        }
        OverlayTheme.paintScrollIndicators(g, scrollTop, maxVisible, lines.size(), ox, cy, cw, ch);
    }
}

package io.cannonforge.retroquest.model;
import io.cannonforge.retroquest.core.SoundManager;
import io.cannonforge.retroquest.overlay.CombatOverlay;

/**
 * Represents a single spell in the spell system (36 base + quest-reward spells).
 *
 * <p>Spells belong to a class (MAGE or CLERIC), have a level (1–6), a base power,
 * and a usage context (COMBAT, MAP, or BOTH). Power is scaled by player level via
 * {@link #getPower(Player)}.
 *
 * <h2>Spell resistance</h2>
 * <p>A monster's {@code spellResistance} (0–40%) works two different ways. Against
 * <em>damage</em> it blunts — see {@link #afterResistance(Monster, int, boolean)} — so a bigger
 * spell always beats a smaller one and no slot is ever spent on nothing. Against a
 * <em>status</em> effect (sleep, charm, a bind, an instant kill) it is still the all-or-nothing
 * roll in {@link #resisted(Monster)}, because a monster is either asleep or it is not.
 *
 * <h2>Escape and instant-win outcomes</h2>
 * <p>Charm Monster and Divine Intervention make the monster <em>flee</em>; Turn Undead and
 * Power Word Kill destroy it outright without a fight. None of those are earned kills, so
 * they hand out no reward (flee) or a reduced one — see {@link #reduceRewards(Monster, int)}.
 */
public class Spell {

    public enum Type { MAGE, CLERIC }
    public enum Usage { COMBAT, MAP, BOTH }

    /** Base power is multiplied by {@code 1 + casterLevel / this}. */
    private static final int POWER_LEVEL_DIVISOR      = 20;
    /** Percentage of XP/gold granted when Turn Undead or Power Word Kill wins outright. */
    private static final int INSTANT_KILL_REWARD_PCT  = 50;
    /** Percentage of XP/gold granted when Divine Intervention routs the monster. */
    private static final int ROUT_REWARD_PCT          = 25;

    private final String name;
    private final Type type;                   // MAGE or CLERIC
    private final int level;
    private final int basePower;
    private final Usage usage;
    private final String shortEffect;          // used in combat log
    private final String longDescription;      // shown in spellbook

    /** No-args constructor for Gson deserialization. */
    @SuppressWarnings("unused")
    private Spell() { name = null; type = null; level = 0; basePower = 0; usage = null; shortEffect = null; longDescription = null; }

    public Spell(String name, Type type, int level, int basePower,
                 Usage usage, String shortEffect, String longDescription) {
        this.name = name;
        this.type = type;
        this.level = level;
        this.basePower = basePower;
        this.usage = usage;
        this.shortEffect = shortEffect;
        this.longDescription = longDescription;
    }

    public String getName() { return name; }
    public Type getType() { return type; }
    public int getLevel() { return level; }
    public Usage getUsage() { return usage; }
    public String getShortEffect() { return shortEffect; }
    public String getLongDescription() { return longDescription; }

    /**
     * Effective power of this spell for {@code caster}:
     * {@code basePower * (1 + level/20) + (INT|WIS - 10)/2}.
     *
     * <p>The {@code level/20} term (x1.05 at level 1, x2 at level 20, x3.5 at level 50) is what
     * keeps casting relevant late: without it a Fireball still hit for 18 against a level-50
     * monster with 400–800 HP.
     */
    public int getPower(Player caster) {
        int bonus = (type == Type.MAGE ? caster.getIntelligence() : caster.getWisdom()) - 10;
        int scaled = (int)(basePower * (1.0 + caster.getLevel() / (double)POWER_LEVEL_DIVISOR));
        return scaled + bonus / 2;
    }

    /**
     * Executes this spell during combat.
     *
     * @return {@code true} if the monster's turn should proceed normally;
     *         {@code false} to skip the monster's turn this round
     *         (used by Time Stop and instant-kill outcomes).
     */
    public boolean executeCombat(Player caster, Monster target, CombatOverlay co) {
        // ── MAP-only spells ──────────────────────────────────────────────────
        if (usage == Usage.MAP) {
            co.logInfo("Cast " + name + " outside of combat to use it.");
            return true;
        }

        // ── Power with ±20% variance ─────────────────────────────────────────
        int rawPower = getPower(caster);
        int variance = Math.max(1, (int)(rawPower * 0.20));
        int power    = rawPower - variance + (int)(Math.random() * (variance * 2 + 1));
        power        = Math.max(1, power);

        // ── Sound ────────────────────────────────────────────────────────────
        playSpellSound();

        // ── Per-spell logic ──────────────────────────────────────────────────
        switch (name) {

            // ── Level 1 ──────────────────────────────────────────────────────
            case "Magic Missile" -> {
                // Always hits — no resistance check
                int dmg = rawPower + (int)(Math.random() * variance);
                co.triggerMonsterFlash();
                target.takeDamage(dmg);
                co.logGood("The magic missile strikes the " + target.getName() + " for " + dmg + " damage!");
                if (target.isDead()) { co.callVictory(); return false; }
            }

            case "Sleep" -> {
                if (resisted(target)) { co.logDim("The " + target.getName() + " resists the sleep!"); return true; }
                int turns = 2 + caster.getLevel() / 3;
                target.setAsleep(turns);
                co.logCyan("The " + target.getName() + " falls into a magical slumber!");
            }

            case "Charm Monster" -> {
                if (resisted(target)) { co.logDim("The " + target.getName() + " resists the charm!"); return true; }
                // Scales with level: 40% base + 3% per level, capped at 70%
                double fleeChance = Math.min(0.70, 0.40 + caster.getLevel() * 0.03);
                if (Math.random() < fleeChance) {
                    // A charmed monster walks away — nothing was slain, so no XP or gold.
                    co.logGood("The " + target.getName() + " is charmed and wanders off!");
                    co.callFlee();
                    return false;
                }
                int charmTurns = 3 + caster.getLevel() / 5;
                target.setCharmed(charmTurns);
                co.logCyan("The " + target.getName() + " is charmed and stands idle for "
                           + charmTurns + " rounds.");
            }

            case "Cure Light Wounds" -> healPlayer(co, power, "Cure Light Wounds");

            case "Protection from Evil" -> {
                co.setProtEv(true);
                co.logCyan("A circle of sacred light surrounds you — AC bonus +"
                           + caster.getAcBonus() + " for " + caster.protEvStepsLeft() + " steps.");
            }

            case "Shield" -> {
                co.setShield(true);
                co.logCyan("A shimmering barrier of force surrounds you — AC bonus +"
                           + caster.getAcBonus() + " for " + caster.shieldStepsLeft() + " steps.");
            }

            // ── Level 2 ──────────────────────────────────────────────────────
            case "Fireball", "Flame Strike", "Meteor Swarm" -> {
                // Standard damage — wards blunt it, they never cancel it
                int dmg = afterResistance(target, power, false);
                if (!hitMonster(target, dmg, name + " blasts the " + target.getName() + " for "
                        + dmg + " damage!" + wardNote(dmg, power), co)) return false;
            }

            case "Lightning Bolt", "Chain Lightning" -> {
                // Pierces 50% of spell resistance
                int dmg = afterResistance(target, power, true);
                if (!hitMonster(target, dmg, name + " arcs through the " + target.getName() + " for "
                        + dmg + " damage!" + wardNote(dmg, power), co)) return false;
            }

            case "Ice Storm" -> {
                // Damage + 30% chance to lock the target up for a round
                int dmg = afterResistance(target, power, false);
                if (!hitMonster(target, dmg, "Ice Storm batters the " + target.getName() + " for "
                        + dmg + " damage!" + wardNote(dmg, power), co)) return false;
                if (Math.random() < 0.30 && !resisted(target)) {
                    co.setStunTurns(1);
                    co.logCyan("The " + target.getName() + " is frozen stiff and loses its next turn!");
                }
            }

            case "Cone of Cold" -> {
                // Cannot be resisted — guaranteed hit, no resistance check
                if (!hitMonster(target, power, "Absolute cold blasts the " + target.getName() + " for " + power + " damage!", co)) return false;
            }

            case "Invisibility" -> {
                co.setInvisible(true);
                co.logCyan("You vanish from sight. Your next strike cannot miss — and lands from hiding for half again the damage!");
            }

            case "Entangle" -> {
                int dmg = afterResistance(target, power / 2, false);
                if (!hitMonster(target, dmg, "Living roots erupt, crushing the " + target.getName()
                        + " for " + dmg + " damage!" + wardNote(dmg, power / 2), co)) return false;
                if (resisted(target)) { co.logDim("The " + target.getName() + " tears free of the roots!"); return true; }
                int turns = 2 + caster.getLevel() / 5;
                target.setAsleep(turns);
                co.logCyan("The " + target.getName() + " is entangled for " + turns + " rounds!");
            }

            case "Cure Serious Wounds" -> healPlayer(co, power, "Cure Serious Wounds");

            case "Bless" -> {
                co.setBless(true);
                co.logCyan("Righteous fury fills you! +" + caster.getDmgBonus()
                           + " damage for " + caster.blessStepsLeft() + " steps.");
            }

            case "Turn Undead" -> {
                boolean undead = target.getMonsterType() == MonsterType.UNDEAD;
                if (undead) {
                    if (Math.random() < 0.70) {
                        co.logGood("The holy light destroys the " + target.getName() + "!");
                        reduceRewards(target, INSTANT_KILL_REWARD_PCT);
                        co.callVictory();
                        return false;
                    }
                    int dmg = afterResistance(target, power * 2, false);
                    if (!hitMonster(target, dmg, "Holy power sears the " + target.getName() + " for "
                            + dmg + " damage!" + wardNote(dmg, power * 2), co)) return false;
                    // "Repels undead" is half the spell's promise — a survivor is turned, not just burnt.
                    co.setFearTurns(2);
                    co.logCyan("The " + target.getName() + " recoils from the holy light and is turned!");
                } else {
                    int dmg = afterResistance(target, power, false);
                    if (!hitMonster(target, dmg, "Turn Undead is less effective against the living. "
                            + dmg + " damage dealt." + wardNote(dmg, power), co)) return false;
                }
            }

            // ── Level 3 ──────────────────────────────────────────────────────
            case "Dispel Magic" -> {
                // 1) Halve monster's spell resistance (makes follow-up spells land)
                int oldResist = target.getSpellResistance();
                if (oldResist > 0) {
                    target.setSpellResistance(oldResist / 2);
                    co.logCyan("The " + target.getName() + "'s magical wards shatter! Spell resistance " + oldResist + "% → " + target.getSpellResistance() + "%.");
                } else {
                    co.logCyan("The " + target.getName() + " has no magical wards to strip.");
                }
                // 2) Cleanse all negative effects from the player
                boolean cleansed = false;
                if (co.getPlayerPoisonTurns() > 0 || co.getPlayerSleepTurns() > 0
                        || co.getPlayerStunTurns() > 0 || co.getPlayerBlindTurns() > 0) {
                    co.setPlayerPoisonTurns(0);
                    co.setPlayerSleepTurns(0);
                    co.setPlayerStunTurns(0);
                    co.setPlayerBlindTurns(0);
                    co.getPlayer().curePoison(); // also clear persistent overworld poison
                    cleansed = true;
                }
                if (cleansed) co.logCyan("Your ailments are purged by the dispelling wave!");
            }

            case "Prayer" -> {
                if (co.hasPrayer()) { co.logDim("Prayer is already active."); return true; }
                co.setPrayer(true);
                co.logCyan("The gods look upon you with favor — AC bonus +" + caster.getAcBonus()
                           + ", +" + caster.getHitBonus() + " to-hit for " + caster.prayerStepsLeft() + " steps.");
            }

            case "Holy Word" -> {
                MonsterType mt = target.getMonsterType();
                boolean evil = mt == MonsterType.UNDEAD || mt == MonsterType.DEMON;
                int raw = evil ? power * 2 : power;
                int dmg = afterResistance(target, raw, false);
                String msg = evil
                        ? "The Holy Word sears the evil " + target.getName() + " for " + dmg + " damage!"
                        : "The Holy Word strikes the " + target.getName() + " for " + dmg + " damage.";
                if (!hitMonster(target, dmg, msg + wardNote(dmg, raw), co)) return false;
                // Against the merely living the word is blinding radiance rather than searing fire.
                if (!evil && !resisted(target)) {
                    co.setBlindTurns(2);
                    co.logCyan("The " + target.getName() + " is blinded by the radiance!");
                }
            }

            case "Cure Critical Wounds" -> healPlayer(co, power, "Cure Critical Wounds");

            case "Teleport" -> {
                co.logInfo("Cast Teleport outside of combat to escape to safety.");
            }

            // ── Level 4 ──────────────────────────────────────────────────────
            case "Cloudkill" -> {
                // The cloud used to tick for the default 2-5, so a level-4 slot bought about
                // 12 damage next to Cone of Cold's 35. It now bites for a caster-scaled amount
                // and lingers a round longer; spread over 4-5 rounds it trades burst for reach
                // (it keeps burning through sleep, charm and spell resistance alike).
                int turns   = 4 + (int)(Math.random() * 2);
                int perTurn = Math.max(4 + caster.getLevel() / 2, power / 3);
                co.setPoisonTurns(turns, perTurn);
                co.logCyan("A deadly poison cloud engulfs the " + target.getName()
                           + " — roughly " + perTurn + " damage a round for " + turns + " rounds!");
            }

            case "Haste" -> {
                co.setHaste(true);
                co.logCyan("Time warps around you — an extra strike every round for "
                           + caster.hasteStepsLeft() + " steps.");
            }

            case "Heal" -> {
                co.getPlayer().heal(co.getPlayer().getMaxHp());
                co.logHpGreen("You are fully healed!");
                co.getGame().getStatsPanel().refresh();
            }

            case "Resist Elements" -> {
                co.setElemResist(true);
                co.logCyan("Elemental wards shimmer around you — spell and breath damage halved for "
                           + caster.elemResStepsLeft() + " steps.");
            }

            case "Restoration" -> {
                // Partial heal + full status cleanse (NOT full HP like Heal). A flat 30 was
                // dead weight beside same-tier Heal, so the floor is now half your maximum HP:
                // Heal restores more, Restoration is the one that also purges what is killing you.
                int healed = Math.max(power, co.getPlayer().getMaxHp() / 2);
                healPlayer(co, healed, "Restoration");
                co.setPlayerPoisonTurns(0);
                co.setPlayerSleepTurns(0);
                co.setPlayerStunTurns(0);
                co.setPlayerBlindTurns(0);
                co.getPlayer().curePoison(); // also clear persistent overworld poison
                co.logCyan("All ailments cleansed — body and spirit purified.");
            }

            // ── Level 5 ──────────────────────────────────────────────────────
            case "Death Spell" -> {
                if (resisted(target)) { co.logDim("The " + target.getName() + " resists the Death Spell!"); return true; }
                // Scales with level advantage: 20% + 5% per level above monster, clamped 5-50%
                double chance = Math.max(0.05, Math.min(0.50, 0.20 + (caster.getLevel() - target.getLevel()) * 0.05));
                if (Math.random() < chance) {
                    co.logAmber("The Death Spell claims its victim!");
                    co.callVictory();
                    return false;
                }
                int dmg = afterResistance(target, power, false);
                if (!hitMonster(target, dmg, "The Death Spell weakens the " + target.getName()
                        + " for " + dmg + " damage." + wardNote(dmg, power), co)) return false;
            }

            case "Holy Armor" -> {
                if (co.hasHolyArmor()) { co.logDim("Holy Armor is already active."); return true; }
                co.setHolyArmor(true);
                co.logCyan("You are armored by divine grace — AC bonus +" + caster.getAcBonus()
                           + " for " + caster.holyArmorStepsLeft() + " steps.");
            }

            // ── Level 6 ──────────────────────────────────────────────────────
            case "Power Word Kill" -> {
                // Ceiling is the caster's own max HP (was 2x, which at level 50 covered every
                // monster in the game); a full-health elite has to be worn down first.
                int threshold = co.getPlayer().getMaxHp();
                if (target.getHp() < threshold) {
                    co.logAmber("Power Word Kill! The " + target.getName() + " is annihilated!");
                    reduceRewards(target, INSTANT_KILL_REWARD_PCT);
                    co.callVictory();
                    return false;
                }
                int dmg = afterResistance(target, power, false);
                if (!hitMonster(target, dmg, "The creature resists total annihilation — "
                        + dmg + " damage dealt." + wardNote(dmg, power), co)) return false;
            }

            case "Wish" -> {
                // 50/50: full heal or triple-power devastation
                if (Math.random() < 0.50) {
                    co.getPlayer().heal(co.getPlayer().getMaxHp());
                    co.logHpGreen("Your wish is granted — you are fully healed!");
                    co.getGame().getStatsPanel().refresh();
                } else {
                    int dmg = afterResistance(target, power * 3, false);
                    if (!hitMonster(target, dmg, "Reality bends — Wish devastates the " + target.getName()
                            + " for " + dmg + " damage!" + wardNote(dmg, power * 3), co)) return false;
                }
            }

            case "Divine Intervention" -> {
                if (Math.random() < 0.60) {
                    int dmg = afterResistance(target, power * 2, false);
                    if (!hitMonster(target, dmg, "Divine wrath strikes the " + target.getName()
                            + " for " + dmg + " damage!" + wardNote(dmg, power * 2), co)) return false;
                } else {
                    co.logGood("The gods speak — the " + target.getName() + " flees in terror!");
                    reduceRewards(target, ROUT_REWARD_PCT);
                    co.callVictory();
                    return false;
                }
            }

            case "Time Stop" -> {
                co.logCyan("Time itself stops! The " + target.getName() + " cannot act this round.");
                return false;
            }

            case "Sanctuary" -> {
                // Guaranteed escape — no XP/gold. A free Flee attempt does most of that, so what
                // earns the level-6 slot is walking out clean: every ailment purged (including the
                // poison that would otherwise follow you onto the map) and still wearing a ward.
                co.setPlayerPoisonTurns(0);
                co.setPlayerSleepTurns(0);
                co.setPlayerStunTurns(0);
                co.setPlayerBlindTurns(0);
                co.getPlayer().curePoison();
                co.setHolyArmor(true);
                co.logCyan("Divine sanctuary envelops you — the " + target.getName() + " cannot pursue!");
                co.logHpGreen("You withdraw cleansed and warded (+" + caster.getAcBonus() + " AC).");
                co.callFlee();
                return false;
            }

            case "Resurrection" -> {
                co.logInfo("Resurrection must be cast outside of combat.");
            }

            default -> co.logInfo(shortEffect);
        }

        return true;
    }

    /**
     * Scales a monster's XP and gold down to {@code percent}% before an instant-win spell
     * routes through the normal victory path. These outcomes end the fight without the
     * player having actually fought it, so they must not pay a full kill's reward.
     */
    private void reduceRewards(Monster target, int percent) {
        target.setXpValue(target.getXPValue() * percent / 100);
        target.setGoldReward(target.getGoldReward() * percent / 100);
    }

    /**
     * Returns true if the monster shrugs off a <em>status</em> effect (sleep, charm, a bind,
     * an instant kill). All-or-nothing is the right model for those — a monster is either
     * asleep or it is not.
     */
    private boolean resisted(Monster target) {
        return (Math.random() * 100) < target.getSpellResistance();
    }

    /**
     * Spell resistance applied to <em>damage</em>: it blunts the blast rather than erasing it.
     *
     * <p>A monster shaves half its resistance value off the damage (a 40%-resistant monster
     * takes 20% less), and a piercing spell halves that again. Binary negation used to invert
     * the tier ladder — an unresistable Cone of Cold beat Chain Lightning one tier above it
     * because half of the bigger spell simply vanished. Blunting keeps the ladder intact and
     * means a level-6 slot is never spent on nothing.
     *
     * @param piercing true for Lightning Bolt / Chain Lightning, which ignore half of the wards
     */
    private int afterResistance(Monster target, int dmg, boolean piercing) {
        int sr = target.getSpellResistance();
        if (sr <= 0) return dmg;
        int cut = piercing ? sr / 4 : sr / 2;
        return Math.max(1, dmg - dmg * cut / 100);
    }

    /** Log suffix noting that the target's wards ate part of the damage. */
    private String wardNote(int dealt, int raw) {
        return dealt < raw ? " (its wards blunt the blow)" : "";
    }

    /**
     * Deals damage to a monster with flash + log. Returns {@code true} if the
     * monster survived; {@code false} if it died (victory already triggered).
     */
    private boolean hitMonster(Monster target, int dmg, String msg, CombatOverlay co) {
        co.triggerMonsterFlash();
        target.takeDamage(dmg);
        co.logAmber(msg);
        if (target.isDead()) { co.callVictory(); return false; }
        return true;
    }

    /** Heals the player, logs the amount restored, and refreshes the stats panel. */
    private void healPlayer(CombatOverlay co, int amount, String spellName) {
        int before = co.getPlayer().getHp();
        co.getPlayer().heal(amount);
        int healed = co.getPlayer().getHp() - before;
        co.logHpGreen("+" + healed + " HP restored by " + spellName + ".");
        co.getGame().getStatsPanel().refresh();
    }

    /** Plays an appropriate sound effect based on the spell name. */
    private void playSpellSound() {
        SoundManager sm = SoundManager.getInstance();
        String n = name.toLowerCase();
        if (n.contains("fire") || n.contains("meteor") || n.contains("flame") || n.contains("cloudkill"))
            sm.play("fireball");
        else if (n.contains("lightning") || n.contains("chain"))
            sm.play("lightning");
        else if (n.contains("ice") || n.contains("cold"))
            sm.play("icestorm");
        else if (n.contains("missile"))
            sm.play("missile");
        else if (n.contains("cure") || n.equals("heal") || n.contains("restoration") || n.contains("resurrection"))
            sm.play("heal");
        else if (n.contains("teleport") || n.equals("scry"))
            sm.play("teleport");
        else if (n.contains("death") || n.contains("kill") || n.contains("power word"))
            sm.play("explosion");
        else if (n.equals("sanctuary"))
            sm.play("heal");
        else
            sm.play("spell");
    }

    @Override
    public String toString() {
        return name + " (Lv" + level + ")";
    }
}

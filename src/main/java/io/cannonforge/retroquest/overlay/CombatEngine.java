package io.cannonforge.retroquest.overlay;

import static io.cannonforge.retroquest.overlay.OverlayTheme.AMBER;
import static io.cannonforge.retroquest.overlay.OverlayTheme.CYAN_ACC;
import static io.cannonforge.retroquest.overlay.OverlayTheme.DANGER;
import static io.cannonforge.retroquest.overlay.OverlayTheme.PHOSPHOR;
import static io.cannonforge.retroquest.overlay.OverlayTheme.TEXT_BRIGHT;

import java.awt.Color;

import io.cannonforge.retroquest.core.MessageLog;
import io.cannonforge.retroquest.core.Retroquest;
import io.cannonforge.retroquest.core.SoundManager;
import io.cannonforge.retroquest.model.BalanceConfig;
import io.cannonforge.retroquest.model.Item;
import io.cannonforge.retroquest.model.Monster;
import io.cannonforge.retroquest.model.MonsterType;
import io.cannonforge.retroquest.model.Player;
import io.cannonforge.retroquest.model.Quest;
import io.cannonforge.retroquest.model.Spell;
import io.cannonforge.retroquest.model.Player.LevelUpResult;
import io.cannonforge.retroquest.registry.LootGenerator;

/**
 * Turn-based combat logic engine extracted from {@link CombatOverlay}.
 *
 * <p>Handles all combat state, turns, attacks, spells, buff/debuff tracking,
 * victory/defeat, and the combat log. Rendering remains in {@code CombatOverlay},
 * which delegates to this engine for all game-logic operations.
 *
 * <p>The engine holds a reference back to {@code CombatOverlay} so it can trigger
 * visual effects (sprite flashes, spell animations) and route log output through
 * the overlay's message system.
 */
class CombatEngine {

    // ── Colors used in log output (must match CombatOverlay palette) ────────
    static final Color TEXT_DIM  = new Color( 90, 110, 135);
    static final Color HP_GREEN  = new Color( 50, 210,  80);

    // ── Combat formula constants (from data/balance.json) ───────────────────
    private static final BalanceConfig.Combat BC = BalanceConfig.get().getCombat();
    private static final int BLINDNESS_TO_HIT_PENALTY    = BC.blindnessToHitPenalty;
    private static final int CRIT_THRESHOLD              = BC.critThreshold;
    private static final int BASE_TO_HIT_BONUS           = BC.baseToHitBonus;
    private static final int FRIENDLY_REACTION_THRESHOLD = BC.friendlyReactionThreshold;
    private static final int AGGRESSIVE_REACTION_THRESHOLD = BC.aggressiveReactionThreshold;
    private static final int PLAYER_POISON_MIN           = BC.playerPoisonMin;
    private static final int PLAYER_POISON_RANGE         = BC.playerPoisonRange;
    private static final int MONSTER_POISON_MIN          = BC.monsterPoisonMin;
    private static final int MONSTER_POISON_RANGE        = BC.monsterPoisonRange;
    private static final double LOOT_DROP_CHANCE         = BC.lootDropChance;

    /** Turns a monster keeps its own Haste buff. */
    private static final int MONSTER_HASTE_TURNS = 3;
    /** Percentage of XP/gold paid when a frightened monster runs off instead of dying. */
    private static final int ROUT_REWARD_PCT     = 25;
    /** Extra damage (percent) on a strike made from Invisibility. */
    private static final int SNEAK_DAMAGE_PCT    = 50;

    // ── Log ─────────────────────────────────────────────────────────────────
    static final int MAX_LOG = 60;
    private final java.util.Deque<LogEntry> logEntries = new java.util.ArrayDeque<>();

    static class LogEntry {
        final String text;
        final Color  color;
        final long   time;
        LogEntry(String t, Color c) { text = t; color = c; time = System.currentTimeMillis(); }
    }

    // ── Callback ────────────────────────────────────────────────────────────
    public interface OnCombatEnd {
        void onEnd(boolean playerWon, int goldGained, String lootName, boolean leveledUp, int newLevel);
    }

    // ── State ───────────────────────────────────────────────────────────────
    private final CombatOverlay overlay;
    private final Retroquest    game;
    private Monster  monster;
    private Player   player;
    private boolean  finished = false;
    private OnCombatEnd endCallback;
    private int monsterMaxHpSnapshot = 1;

    // ── Buff/debuff state ───────────────────────────────────────────────────
    // Monster debuffs
    private int poisonTurns = 0;
    private int poisonPower = MONSTER_POISON_MIN;   // per-turn base damage of the cloud
    private int stunTurns   = 0;
    private int fearTurns   = 0;
    private int blindTurns  = 0;
    // Player debuffs (from monster spells)
    private int playerPoisonTurns = 0;
    private int playerSleepTurns  = 0;
    /** Turns during which sleep and fear cannot take hold again after waking. */
    private int sleepGraceTurns   = 0;
    private static final int SLEEP_GRACE = 2;
    private int playerStunTurns   = 0;
    private int playerBlindTurns  = 0;
    // Monster self-buffs
    private int monsterHasteTurns = 0;

    // ── Constructor ─────────────────────────────────────────────────────────
    CombatEngine(CombatOverlay overlay, Retroquest game) {
        this.overlay = overlay;
        this.game    = game;
    }

    // ── Start ───────────────────────────────────────────────────────────────
    void start(Monster monster, OnCombatEnd callback) {
        this.monster             = monster;
        this.player              = game.getPlayer();
        this.endCallback         = callback;
        this.finished            = false;
        this.monsterMaxHpSnapshot = Math.max(1, monster.getMaxHp());
        logEntries.clear();
        resetCombatBuffs();
        monsterReaction();
    }

    // ── Accessors ───────────────────────────────────────────────────────────
    boolean  isFinished()           { return finished; }
    Monster  getMonster()           { return monster; }
    Player   getPlayer()            { return player; }
    Retroquest getGame()            { return game; }
    int      getMonsterMaxHpSnapshot() { return monsterMaxHpSnapshot; }

    // Buff state getters (read by CombatOverlay.paintBuffStrip)
    int     getPoisonTurns()        { return poisonTurns; }
    int     getStunTurns()          { return stunTurns; }
    int     getFearTurns()          { return fearTurns; }
    int     getBlindTurns()         { return blindTurns; }
    int     getPlayerPoisonTurns()  { return playerPoisonTurns; }
    int     getPlayerSleepTurns()   { return playerSleepTurns; }
    int     getPlayerStunTurns()    { return playerStunTurns; }
    int     getPlayerBlindTurns()   { return playerBlindTurns; }
    boolean isMonsterHasted()       { return monsterHasteTurns > 0; }

    java.util.Deque<LogEntry> getLogEntries() { return logEntries; }

    // ── Buff/Debuff setters (called by Spell.executeCombat via CombatOverlay delegation) ──
    void setPoisonTurns(int t)       { poisonTurns = t; poisonPower = MONSTER_POISON_MIN; }
    /** Poison cloud with a caster-scaled bite (Cloudkill) rather than the default dice. */
    void setPoisonTurns(int t, int perTurn) { poisonTurns = t; poisonPower = Math.max(1, perTurn); }
    void setStunTurns(int t)         { stunTurns   = t; }
    void setFearTurns(int t)         { fearTurns   = t; }
    void setBlindTurns(int t)        { blindTurns  = t; }
    void clearMonsterDebuffs()       { poisonTurns = 0; stunTurns = 0; fearTurns = 0; blindTurns = 0; }
    void setPlayerPoisonTurns(int t) { playerPoisonTurns = t; }
    void setPlayerSleepTurns(int t)  { playerSleepTurns  = t; }
    void setPlayerStunTurns(int t)   { playerStunTurns   = t; }
    void setPlayerBlindTurns(int t)  { playerBlindTurns  = t; }

    // Each ward is its own named switch — the player owns the numbers (Player.*_AC_BONUS),
    // so nothing here can advertise a bonus the player model does not actually grant.
    void setProtEv(boolean b)        { if (b) player.applyProtEv(); }
    void setBless(boolean b)         { if (b) player.applyBless(); }
    void setInvisible(boolean b)     { if (b) player.applyInvisibility(); else player.clearInvisibility(); }
    void setHaste(boolean b)         { if (b) player.applyHaste(); }
    void setShield(boolean b)       { if (b) player.applyShield(); }
    void setElemResist(boolean b)   { if (b) player.applyElemResist(); }
    boolean hasPrayer()              { return player.hasPrayer(); }
    boolean hasHolyArmor()           { return player.hasHolyArmor(); }
    void setPrayer(boolean b)        { if (b) player.applyPrayer(); }
    void setHolyArmor(boolean b)     { if (b) player.applyHolyArmor(); }

    // ── Combat logic ────────────────────────────────────────────────────────
    private void monsterReaction() {
        if (monster.isFriendly()) {
            handleFriendlyEncounter();
            return;
        }
        int chaBonus = Math.min(3, (player.getCharisma() - 10) / 2);
        int reaction = roll3d6() + chaBonus;
        if (reaction >= FRIENDLY_REACTION_THRESHOLD && monster.getLevel() < 40) {
            log("The " + monster.getName() + " seems unnerved by your presence!", AMBER);
            int levelDiff = monster.getLevel() - player.getLevel();
            int fleeChance = Math.max(10, 60 - levelDiff * 5);
            if (Math.random() * 100 < fleeChance) {
                log("It turns and flees!", PHOSPHOR);
                log("Press any key to continue...", TEXT_DIM);
                endCombat(true, 0, null, false, player.getLevel());
                return;
            }
        } else if (reaction <= AGGRESSIVE_REACTION_THRESHOLD) {
            log("The " + monster.getName() + " snarls aggressively!", DANGER);
        } else {
            log("A " + monster.getName() + " blocks your path!", TEXT_BRIGHT);
        }
    }

    private void handleFriendlyEncounter() {
        SoundManager.getInstance().play("coin");
        overlay.triggerSpellEffect(SpellEffectRenderer.EffectType.GIFT, false);
        log("The " + monster.getName() + " approaches peacefully!", PHOSPHOR);
        int roll = (int)(Math.random() * 3);
        switch (roll) {
            case 0 -> {
                int gold = 2 + (int)(Math.random() * (monster.getLevel()));
                player.addGold(gold);
                log("\"Take this gold, traveler.\" +" + gold + " gold!", AMBER);
            }
            case 1 -> {
                int heal = 5 + monster.getLevel() * 2;
                int before = player.getHp();
                player.heal(heal);
                int healed = player.getHp() - before;
                if (healed > 0) log("The " + monster.getName() + " heals your wounds! +" + healed + " HP.", HP_GREEN);
                else log("The " + monster.getName() + " offers a blessing, but you are already healthy.", TEXT_DIM);
            }
            case 2 -> {
                int xpGift = 5 + monster.getLevel() * 3;
                LevelUpResult lvl = player.addXP(xpGift);
                log("The " + monster.getName() + " shares ancient wisdom. +" + xpGift + " XP!", AMBER);
                if (lvl != null) {
                    SoundManager.getInstance().play("levelup");
                    game.getGamePanel().triggerLevelUpFlash();
                    log("\u2605 LEVEL UP!  You are now level " + lvl.newLevel() + "!  Max HP +" + lvl.totalHpGained() + "  (\u2192 " + player.getMaxHp() + ")", AMBER);
                }
            }
        }
        game.getStatsPanel().refresh();
        log("The " + monster.getName() + " departs with a nod.", TEXT_DIM);
        log("Press any key to continue...", TEXT_DIM);
        finished = true;
        endCombat(true, 0, null, false, player.getLevel());
    }

    void playerAttack() {
        SoundManager.getInstance().playAttack();

        if (playerTurnBlocked()) return;

        // Blindness costs one turn's worth of counter for the whole action, not per swing.
        int blindMod = 0;
        if (playerBlindTurns > 0) {
            blindMod = BLINDNESS_TO_HIT_PENALTY;
            playerBlindTurns--;
            log("You are blinded \u2014 -" + BLINDNESS_TO_HIT_PENALTY + " to hit!", TEXT_DIM);
        }

        // Invisibility: the strike cannot miss (buff is consumed on use) but still rolls
        // for a critical and still gets the Haste follow-up \u2014 it is a swing, not a special case.
        boolean unseen = player.isInvisible();
        if (unseen) player.clearInvisibility();

        if (!playerSwing(unseen, blindMod, unseen ? "You strike unseen at " : "You strike ")) return;

        // Haste: bonus attack each round while active
        if (player.isHasted()) {
            log("Haste \u2014 you strike again!", AMBER);
            if (!playerSwing(false, blindMod, "Your swift second strike hits ")) return;
        }
        monsterTurn();
    }

    /**
     * Resolves one melee swing against the monster.
     *
     * @param autoHit  skip the to-hit comparison (Invisibility); the d20 is still rolled for crits
     * @param blindMod to-hit penalty already charged for this action
     * @param flavour  sentence opener used on a non-critical hit
     * @return {@code true} if the fight continues, {@code false} if the monster died
     */
    private boolean playerSwing(boolean autoHit, int blindMod, String flavour) {
        int rawRoll  = rollD20();
        boolean crit = rawRoll >= CRIT_THRESHOLD;
        int toHit  = rawRoll + player.getLevel() + (player.getDex() - 10) / 2
                   + BASE_TO_HIT_BONUS + player.getHitBonus() - blindMod;
        int needed = monster.getAC();

        if (!autoHit && toHit < needed) {
            log("Your attack misses the " + monster.getName() + "!", TEXT_DIM);
            return true;
        }

        int dmg = player.getDamage() + player.getDmgBonus();
        if (autoHit) dmg += dmg * SNEAK_DAMAGE_PCT / 100;   // struck from hiding
        if (crit)    dmg *= 2;
        dmg = Math.max(1, dmg);
        monster.takeDamage(dmg);
        overlay.triggerMonsterFlash();
        SoundManager.getInstance().play(crit ? "explosion" : "hit");
        log((crit ? "\u2605 CRITICAL HIT!  " : flavour) +
            "the " + monster.getName() + " for " + dmg + " damage!",
            crit ? AMBER : PHOSPHOR);
        if (monster.isDead()) { victory(); return false; }
        return true;
    }

    private void monsterTurn() {
        // Poison keeps burning while the monster is asleep or charmed
        if (poisonTurns > 0) {
            int poisonDmg = poisonPower + (int)(Math.random() * MONSTER_POISON_RANGE);
            monster.takeDamage(poisonDmg);
            poisonTurns--;
            log("Poison burns the " + monster.getName() + " for " + poisonDmg + " damage!", HP_GREEN);
            if (monster.isDead()) { victory(); return; }
        }

        if (monster.isAsleep()) {
            monster.decrementSleep();
            log("The " + monster.getName() + " sleeps on...", TEXT_DIM);
            return;
        }
        if (monster.isCharmed()) {
            monster.decrementCharm();
            if (monster.isCharmed()) {
                log("The charmed " + monster.getName() + " stands idle.", TEXT_DIM);
                return;
            }
            log("The " + monster.getName() + " shakes off the charm!", DANGER);
        }

        // Stun: skip attack this turn
        if (stunTurns > 0) {
            stunTurns--;
            log("The " + monster.getName() + " is stunned and cannot act!", TEXT_DIM);
            return;
        }

        // Fear: 50% chance to flee
        if (fearTurns > 0) {
            fearTurns--;
            if (Math.random() < 0.5) {
                log("The " + monster.getName() + " flees in terror!", PHOSPHOR);
                // Routing a monster is not a clean kill, but it is not nothing either:
                // pay a quarter reward, the same deal Divine Intervention's rout gives.
                monster.setXpValue(monster.getXPValue() * ROUT_REWARD_PCT / 100);
                monster.setGoldReward(monster.getGoldReward() * ROUT_REWARD_PCT / 100);
                victory("is routed!");
                return;
            }
        }

        // Blind: -4 to-hit penalty
        int blindPenalty = blindTurns > 0 ? BLINDNESS_TO_HIT_PENALTY : 0;
        if (blindTurns > 0) blindTurns--;

        // Monster spell casting. Casting normally *is* the monster's turn; while hasted it
        // gets the spell and a swing, so the buff has to outlive the turn it was cast on.
        boolean hastedThisTurn = monsterHasteTurns > 0;
        if (monsterHasteTurns > 0) monsterHasteTurns--;
        if (!finished && monster.canCastSpells()) {
            if (Math.random() * 100 < monster.getSpellCastChance()) {
                castMonsterSpell();
                if (finished) return;
                // Casting Haste this turn also frees up the melee attack.
                if (!hastedThisTurn && monsterHasteTurns <= 0) return;
            }
        }

        int toHit  = rollD20() + monster.getLevel() - blindPenalty;
        int needed = player.getAC() + player.getAcBonus();

        if (toHit >= needed) {
            SoundManager.getInstance().playHit();
            int base = monster.getDamage();
            int roll = base / 2 + 1 + (int)(Math.random() * (base + 1));
            int hpBefore = player.getHp();
            player.takeDamage(roll);          // takeDamage() applies damage reduction itself
            int dmg = Math.max(1, hpBefore - player.getHp());
            overlay.triggerPlayerFlash();
            log("The " + monster.getName() + " hits you for " + dmg + " damage!", DANGER);
            game.getStatsPanel().refresh();

            // Undead level drain: 15% chance per melee hit to drain XP
            if (monster.getMonsterType() == MonsterType.UNDEAD && Math.random() < 0.15) {
                int drainXP = Math.max(50, player.getXpToNextLevel() / 4);
                player.drainXP(drainXP);
                overlay.triggerSpellEffect(SpellEffectRenderer.EffectType.DRAIN, false);
                log("You feel your life force draining! -" + drainXP + " XP!", DANGER);
                game.getStatsPanel().refresh();
            }

            checkPlayerDeath("...");
        } else {
            if (blindPenalty > 0)
                log("The blinded " + monster.getName() + " swings wildly and misses!", TEXT_DIM);
            else
                log("The " + monster.getName() + " misses you!", TEXT_DIM);
        }
    }

    /** Applies the per-turn poison tick. @return true if the player died. */
    private boolean tickPlayerPoison() {
        if (playerPoisonTurns <= 0) return false;
        int dmg = PLAYER_POISON_MIN + (int)(Math.random() * PLAYER_POISON_RANGE);
        player.takeDamage(dmg);
        playerPoisonTurns--;
        log("Poison courses through you \u2014 " + dmg + " damage!", DANGER);
        game.getStatsPanel().refresh();
        return checkPlayerDeath(" by poison...");
    }

    /**
     * Sleep/stun gate. Called on its own for actions that open a submenu first
     * (the spellbook), so a lost turn does not also cost a spell slot.
     * @return true if the player loses this turn (the monster has already acted).
     */
    boolean isIncapacitated() {
        if (sleepGraceTurns > 0) sleepGraceTurns--;
        if (playerSleepTurns > 0) {
            playerSleepTurns--;
            if (playerSleepTurns == 0) {
                // Waking up buys a moment of clarity nothing can take away.
                sleepGraceTurns = SLEEP_GRACE;
                log("You jolt awake!", CYAN_ACC);
            } else {
                log("You are magically asleep and cannot act!", TEXT_DIM);
            }
            monsterTurn();
            return true;
        }
        if (playerStunTurns > 0) {
            playerStunTurns--;
            if (playerStunTurns == 0) sleepGraceTurns = SLEEP_GRACE;
            log("You are stunned and cannot act!", TEXT_DIM);
            monsterTurn();
            return true;
        }
        return false;
    }

    /** Poison tick plus sleep/stun gate, for actions that resolve immediately. */
    private boolean playerTurnBlocked() {
        if (tickPlayerPoison()) return true;
        return isIncapacitated();
    }

    void attemptFlee() {
        if (playerTurnBlocked()) return;
        int fleeChance = 60 + (player.getCharisma() - 10) * 3;
        if (game.isInDungeon()) {
            fleeChance -= 15 + game.getCurrentDepth() / 5;
            fleeChance = Math.max(5, fleeChance);
        }
        if (Math.random() * 100 < fleeChance) {
            SoundManager.getInstance().play("flee");
            log("You successfully flee!", CYAN_ACC);
            endCombat(false, 0, null, false, player.getLevel());
        } else {
            SoundManager.getInstance().play("hurt");
            log("You failed to escape!", DANGER);
            monsterTurn();
        }
    }

    /** Sanctuary spell: guaranteed flee, no XP/gold awarded. */
    void guaranteedFlee() {
        // Sanctuary is still an escape, and an escape must respect a fight that has no exit —
        // the F key already refuses these, so the spell cannot be the loophole.
        if (overlay.isUnfleeable()) {
            log("The sanctuary closes around you, but there is no way out of this place.", DANGER);
            monsterTurn();
            return;
        }
        SoundManager.getInstance().play("flee");
        endCombat(false, 0, null, false, player.getLevel());
    }

    /**
     * Resolves the round after the player used an item out of the pack. Combat used to offer no
     * way to reach the inventory at all, while the help text sells potions as the survival plan.
     *
     * <p>Only called once the item has actually been consumed \u2014 see
     * {@link CombatOverlay#onCombatItemUsed()}. Opening the pack and closing it again costs
     * nothing; the sleep/stun gate runs before the pack opens, so an incapacitated player loses
     * the turn rather than getting a free item use.
     */
    void itemUsedTurn() {
        if (finished) return;
        game.getStatsPanel().refresh();
        if (tickPlayerPoison()) return;                 // poison can still finish the job
        if (!monster.isDead() && !finished) monsterTurn();
    }

    void victory() { victory("is slain!"); }

    /** @param outcome how the fight ended, e.g. {@code "is slain!"} or {@code "is routed!"}. */
    void victory(String outcome) {
        SoundManager.getInstance().playVictory();
        int gold = monster.getGoldReward();
        String lootName = null;
        boolean leveled = false;

        // CHA bonus: up to 10% more XP (1% per point above 10)
        int baseXP = monster.getXPValue();
        int chaBonus = Math.max(0, player.getCharisma() - 10);
        int bonusXP = baseXP * chaBonus / 100;  // 1% per CHA above 10
        int totalXP = baseXP + bonusXP;
        String xpMsg = bonusXP > 0 ? "  +" + gold + " gold.  (+" + bonusXP + " XP from charisma)"
                                    : "  +" + gold + " gold.";
        log("The " + monster.getName() + " " + outcome + xpMsg, AMBER);

        LevelUpResult lvl = player.addXP(totalXP);
        if (lvl != null) {
            leveled = true;
            SoundManager.getInstance().play("levelup");
            game.getGamePanel().triggerLevelUpFlash();
            log("\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550", AMBER);
            log("\u2605 LEVEL UP!  You are now level " + lvl.newLevel() + "!", AMBER);
            log("  Max HP +" + lvl.totalHpGained() + "  (\u2192 " + player.getMaxHp() + ")  \u2014  HP fully restored!", PHOSPHOR);
            if (lvl.unlockedNewSpellTier())
                log("  \u2726 New spell tier unlocked: Level " + lvl.newMaxSpellLevel() + " spells!", CYAN_ACC);
            log("  Spell slots refreshed.", TEXT_DIM);
            log("\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550", AMBER);
        }

        player.progressQuest(Quest.Type.KILL, monster.getName(), 1);

        if (Math.random() < LOOT_DROP_CHANCE) {
            Item loot = generateLoot();
            int bonus = 1 + (int)(Math.random() * 2);
            gold += bonus;
            if (player.addItemAndProgress(loot)) {
                lootName = loot.getName();
                SoundManager.getInstance().play("coin");
                log("You found: " + loot.getName() + "  +" + bonus + " gold.", PHOSPHOR);
            } else {
                log("Found " + loot.getName() + " (inv. full)  +" + bonus + " gold.", TEXT_DIM);
            }
        }

        player.addGold(gold);
        game.getStatsPanel().refresh();

        log("Press any key to continue...", TEXT_DIM);
        finished = true;

        // Notify caller
        endCombat(true, gold, lootName, leveled, player.getLevel());
    }

    private void endCombat(boolean won, int gold, String loot, boolean leveled, int lvl) {
        // Combat is over however it ended. Without this a monster that flees leaves the
        // overlay live: the player fights on and the reward callback fires a second time.
        finished = true;
        // Carry poison from combat to overworld (persists as step-based damage)
        if (playerPoisonTurns > 0) {
            player.applyPoison(playerPoisonTurns * 5); // convert combat turns to ~5 steps each
        }
        resetCombatBuffs();
        if (endCallback != null)
            endCallback.onEnd(won, gold, loot, leveled, lvl);
        if (!won) overlay.setActive(false);  // defeat/flee closes immediately; victory waits for keypress
    }

    void executeCombatSpell(Spell spell) {
        if (finished) return;
        if (spell.getLevel() > player.getLevel()) {
            log("Not enough experience to cast " + spell.getName() + "!", DANGER);
            return;
        }
        if (tickPlayerPoison()) return;   // poison can kill before the spell resolves
        log("You cast " + spell.getName() + "!", CYAN_ACC);
        overlay.triggerSpellEffect(SpellEffectRenderer.effectTypeForSpell(spell.getName()), true);
        boolean monsterTurnProceeds = spell.executeCombat(player, monster, overlay);
        if (monsterTurnProceeds && !monster.isDead() && !finished) {
            monsterTurn();
        }
    }

    // ── Resurrection Ward death intercept ───────────────────────────────────
    private boolean checkPlayerDeath(String slayerSuffix) {
        if (!player.isDead()) return false;
        if (player.triggerResurrection()) {
            SoundManager.getInstance().play("heal");
            log("RESURRECTION WARD ACTIVATES!", AMBER);
            log("You rise with " + player.getHp() + " HP!", HP_GREEN);
            game.getStatsPanel().refresh();
            return false;
        }
        SoundManager.getInstance().play("death");
        log("You have been slain" + slayerSuffix, new Color(200, 30, 30));
        playerPoisonTurns = 0;            // death clears lingering combat poison
        finished = true;
        overlay.setActive(false);
        // Let the caller unwind (multi-phase boss fights, ambush handlers) before we die
        endCombat(false, 0, null, false, player.getLevel());
        game.getGamePanel().startDeathAnimation(monster.getName());
        return true;
    }

    // ── Buff/Debuff management ──────────────────────────────────────────────
    void resetCombatBuffs() {
        player.clearInvisibility();
        poisonTurns = 0; poisonPower = MONSTER_POISON_MIN;
        stunTurns = 0; fearTurns = 0; blindTurns = 0;
        playerPoisonTurns = 0; playerSleepTurns = 0; playerStunTurns = 0; playerBlindTurns = 0;
        sleepGraceTurns   = 0;
        monsterHasteTurns = 0;
    }

    // ── Monster spell AI ────────────────────────────────────────────────────
    private void castMonsterSpell() {
        String spellName = monster.pickRandomSpell();
        if (spellName == null) return;
        overlay.triggerSpellEffect(SpellEffectRenderer.effectTypeForSpell(spellName), false);
        int power = monster.getSpellPower() + (int)(Math.random() * (monster.getSpellPower() / 2 + 1));
        power = Math.max(1, power);

        // Player spell resistance
        SoundManager.getInstance().play("spell");
        int playerResist = player.getTotalSpellResist();
        if (Math.random() * 100 < playerResist) {
            log("The " + monster.getName() + " casts " + spellName + " but you resist!", CYAN_ACC);
            return;
        }

        log("The " + monster.getName() + " casts " + spellName + "!", DANGER);

        switch (spellName) {
            case "Magic Missile", "Fireball", "Lightning Bolt", "Ice Storm", "Flame Strike" -> {
                int spellDmg = player.hasElemResist() ? power / 2 : power;
                if (spellDmg < 1) spellDmg = 1;
                player.takeDamage(spellDmg);
                overlay.triggerPlayerFlash();
                String elemMsg = player.hasElemResist() ? " (halved by elemental ward)" : "";
                log("You take " + spellDmg + " damage from " + spellName + "!" + elemMsg, DANGER);
                game.getStatsPanel().refresh();
                checkPlayerDeath(" by " + monster.getName() + "'s magic...");
            }
            case "Sleep" -> {
                if (sleepGraceTurns > 0) {
                    log("You shake off the drowsiness before it takes hold!", CYAN_ACC);
                } else {
                    setPlayerSleepTurns(2);
                    log("You fall into a magical slumber!", TEXT_DIM);
                }
            }
            case "Poison" -> {
                SoundManager.getInstance().play("hurt");
                setPlayerPoisonTurns(3 + (int)(Math.random() * 2));
                log("You are poisoned!", DANGER);
            }
            case "Fear" -> {
                if (sleepGraceTurns > 0) {
                    log("Terror claws at you, and finds nothing to hold.", CYAN_ACC);
                } else {
                    setPlayerSleepTurns(1);
                    log("Terror grips your heart! You freeze for a moment.", TEXT_DIM);
                }
            }
            case "Drain" -> {
                int drain = Math.min(player.getGold(), power * 2);
                player.addGold(-drain);
                log("Life force drains from you!" + (drain > 0 ? " " + drain + " gold lost." : ""), DANGER);
                game.getStatsPanel().refresh();
            }
            case "Stun" -> {
                if (sleepGraceTurns > 0) {
                    log("The blast rocks you, but your feet hold.", CYAN_ACC);
                } else {
                    SoundManager.getInstance().play("hurt");
                    setPlayerStunTurns(1);
                    log("A concussive blast leaves you stunned!", DANGER);
                }
            }
            case "Blind" -> {
                setPlayerBlindTurns(2 + (int)(Math.random() * 2));
                log("Magical darkness blinds you! (-4 to-hit for " + playerBlindTurns + " turns)", DANGER);
            }
            case "Fire Breath" -> {
                int breathDmg = power + power / 2;
                if (player.hasElemResist()) breathDmg /= 2;
                if (breathDmg < 1) breathDmg = 1;
                player.takeDamage(breathDmg);
                overlay.triggerPlayerFlash();
                String breathMsg = player.hasElemResist() ? " (halved by elemental ward)" : "";
                log("The " + monster.getName() + " breathes fire for " + breathDmg + " damage!" + breathMsg, DANGER);
                game.getStatsPanel().refresh();
                checkPlayerDeath(" by " + monster.getName() + "'s fire breath...");
            }
            case "Haste" -> {
                monsterHasteTurns = MONSTER_HASTE_TURNS;
                log("The " + monster.getName() + " moves with supernatural speed!", DANGER);
            }
            default -> {
                int defDmg = player.hasElemResist() ? power / 2 : power;
                if (defDmg < 1) defDmg = 1;
                player.takeDamage(defDmg);
                overlay.triggerPlayerFlash();
                String defMsg = player.hasElemResist() ? " (halved by elemental ward)" : "";
                log("You take " + defDmg + " damage!" + defMsg, DANGER);
                game.getStatsPanel().refresh();
                checkPlayerDeath(" by " + monster.getName() + "'s magic...");
            }
        }
    }

    // ── LOG ─────────────────────────────────────────────────────────────────
    void log(String msg, Color col) {
        logEntries.addLast(new LogEntry(msg, col));
        while (logEntries.size() > MAX_LOG)
            logEntries.removeFirst();
        // Mirror to the persistent bottom message log
        if (game.getMessageLog() != null) {
            MessageLog.Type t = (col == DANGER)  ? MessageLog.Type.DANGER  :
                                (col == AMBER)   ? MessageLog.Type.LOOT    :
                                (col == PHOSPHOR || col == HP_GREEN) ? MessageLog.Type.GOOD :
                                (col == TEXT_DIM) ? MessageLog.Type.DIM    :
                                MessageLog.Type.INFO;
            game.getMessageLog().log(msg, t);
        }
    }

    void logGood(String msg)    { log(msg, PHOSPHOR);    }
    void logDanger(String msg)  { log(msg, DANGER);      }
    void logAmber(String msg)   { log(msg, AMBER);       }
    void logCyan(String msg)    { log(msg, CYAN_ACC);    }
    void logInfo(String msg)    { log(msg, TEXT_BRIGHT); }
    void logDim(String msg)     { log(msg, TEXT_DIM);    }
    void logHpGreen(String msg) { log(msg, HP_GREEN);    }

    // ── DICE ────────────────────────────────────────────────────────────────
    private int roll3d6() {
        return (int)(Math.random()*6)+1+(int)(Math.random()*6)+1+(int)(Math.random()*6)+1;
    }
    private int rollD20() { return (int)(Math.random()*20)+1; }

    // ── LOOT ────────────────────────────────────────────────────────────────
    private Item generateLoot() {
        return LootGenerator.getRandomLootForMonster(monster.getLevel(), player.getLevel());
    }
}

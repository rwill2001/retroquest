package io.cannonforge.retroquest.overlay;

import static io.cannonforge.retroquest.overlay.OverlayTheme.AMBER;
import static io.cannonforge.retroquest.overlay.OverlayTheme.CYAN_ACC;
import static io.cannonforge.retroquest.overlay.OverlayTheme.DANGER;
import static io.cannonforge.retroquest.overlay.OverlayTheme.F_KEY;
import static io.cannonforge.retroquest.overlay.OverlayTheme.F_SMALL;
import static io.cannonforge.retroquest.overlay.OverlayTheme.PHOSPHOR;
import static io.cannonforge.retroquest.overlay.OverlayTheme.TEXT_BRIGHT;
import static io.cannonforge.retroquest.overlay.OverlayTheme.entryEase;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;

import io.cannonforge.retroquest.core.Fonts;
import io.cannonforge.retroquest.core.Retroquest;
import io.cannonforge.retroquest.model.Monster;
import io.cannonforge.retroquest.model.Player;
import io.cannonforge.retroquest.model.Spell;
import io.cannonforge.retroquest.registry.ImageAssetRegistry;

/**
 * In-panel turn-based combat overlay — rendered directly inside
 * {@link io.cannonforge.retroquest.core.GamePanel GamePanel}.
 *
 * <p>All combat logic (turns, attacks, spells, victory/defeat, buffs, and the
 * combat log) is delegated to {@link CombatEngine}. This class retains only
 * rendering, entry animation, input dispatch, and the public API surface used
 * by {@link io.cannonforge.retroquest.model.Spell Spell} and
 * {@link SpellbookOverlay}.
 */
public class CombatOverlay {

    // ── Palette (custom tint — slightly different from standard OverlayTheme) ─
    private static final Color BG          = new Color(  6,   8,  12);
    private static final Color ARENA_BG    = new Color(  8,  10,  18);
    private static final Color BORDER_COL  = new Color( 35,  50,  72);
    private static final Color TEXT_DIM    = new Color( 90, 110, 135);
    private static final Color HP_GREEN    = new Color( 50, 210,  80);
    private static final Color HP_YELLOW   = new Color(215, 175,  35);
    private static final Color HP_RED      = new Color(210,  50,  50);

    private static final Font F_TITLE  = Fonts.monoBold(14);
    private static final Font F_STAT   = Fonts.monoBold(13);
    private static final Font F_LOG    = Fonts.mono    (12);
    private static final Font F_LABEL  = Fonts.mono    (11);

    // ── State ─────────────────────────────────────────────────────────────────
    private final Retroquest game;
    private boolean active = false;

    // ── Sprite flash (rendering-only) ─────────────────────────────────────────
    private long   playerHitTime  = 0;
    private long   monsterHitTime = 0;
    private static final long HIT_FLASH_MS = 220;

    // ── Spell effect animation (delegated to SpellEffectRenderer) ─────────────
    private final SpellEffectRenderer effectRenderer = new SpellEffectRenderer();

    // ── Entry animation ───────────────────────────────────────────────────────
    private long    entryTime = 0;
    private static final long ENTRY_MS = 350;

    // ── Cached button rects for mouse hit-testing ─────────────────────────────
    private final java.awt.Rectangle[] actionRects = new java.awt.Rectangle[4]; // Attack, Spell, Use, Flee

    // ── Unfleeable boss flag ───────────────────────────────────────────────────
    private boolean unfleeable = false;

    /** True while the pack is open as the [U] combat action, awaiting a use or a back-out. */
    private boolean itemMode = false;

    // ── Combat engine (all logic) ─────────────────────────────────────────────
    private final CombatEngine engine;

    // ── Callback (re-exported from engine for GamePanel.startCombat signature) ─
    public interface OnCombatEnd {
        void onEnd(boolean playerWon, int goldGained, String lootName, boolean leveledUp, int newLevel);
    }

    // ── Constructor ───────────────────────────────────────────────────────────
    public CombatOverlay(Retroquest game) {
        this.game   = game;
        this.engine = new CombatEngine(this, game);
    }

    // ── Start ─────────────────────────────────────────────────────────────────
    public void start(Monster monster, OnCombatEnd callback) {
        start(monster, callback, false);
    }

    /**
     * Starts combat, optionally unfleeable. Boss fights pass {@code true} here rather than
     * calling {@link #setUnfleeable} beforehand, which start() used to clear.
     */
    public void start(Monster monster, OnCombatEnd callback, boolean noEscape) {
        this.active     = true;
        this.unfleeable = noEscape;
        this.itemMode   = false;
        this.entryTime  = System.currentTimeMillis();
        // Wrap the public OnCombatEnd into the engine's internal interface
        CombatEngine.OnCombatEnd engineCb = callback == null ? null
                : (won, gold, loot, leveled, lvl) -> callback.onEnd(won, gold, loot, leveled, lvl);
        engine.start(monster, engineCb);
    }

    /** Mark the current combat as unfleeable (endgame boss fights). */
    public void setUnfleeable(boolean b) { this.unfleeable = b; }
    public boolean isUnfleeable()        { return unfleeable; }

    public boolean isActive()   { return active; }
    public boolean isFinished() { return engine.isFinished(); }

    /** Package-private — called by CombatEngine when combat ends without victory. */
    void setActive(boolean b) { this.active = b; if (!b) this.itemMode = false; }

    // ── Input ─────────────────────────────────────────────────────────────────
    public void handleKey(java.awt.event.KeyEvent e) {
        if (!active) return;

        // The pack paints on top of the combat card, but Retroquest routes keys to combat
        // before it reaches the inventory — so while it is open every key belongs to it.
        if (game.getGamePanel() != null && game.getGamePanel().isInventoryActive()) {
            game.getGamePanel().handleInventoryKey(e);
            // Closed without using anything: the round is not spent, the player acts again.
            if (!game.getGamePanel().isInventoryActive()) itemMode = false;
            return;
        }

        // If combat is over, any key dismisses
        if (engine.isFinished()) {
            active = false;
            return;
        }

        Monster monster = engine.getMonster();
        Player  player  = engine.getPlayer();
        if (monster.isDead() || player.isDead()) return;

        switch (e.getKeyCode()) {
            case java.awt.event.KeyEvent.VK_A -> engine.playerAttack();
            case java.awt.event.KeyEvent.VK_C -> { if (!engine.isIncapacitated()) game.openSpellbook(this); }
            case java.awt.event.KeyEvent.VK_U, java.awt.event.KeyEvent.VK_I -> openPackForCombat();
            case java.awt.event.KeyEvent.VK_F -> {
                if (unfleeable) {
                    engine.log("There is no escape from the gods!", DANGER);
                } else {
                    engine.attemptFlee();
                }
            }
        }
    }

    public void handleClick(int mx, int my) {
        if (!active) return;
        // Pack is open over the card and has no public click relay on GamePanel — swallow the
        // click rather than firing the combat action sitting underneath it.
        if (game.getGamePanel() != null && game.getGamePanel().isInventoryActive()) return;
        if (engine.isFinished() || (actionRects[0] == null)) return;
        if (actionRects[0].contains(mx, my)) handleKey(null, 'A');
        else if (actionRects[1].contains(mx, my)) handleKey(null, 'C');
        else if (actionRects[2] != null && actionRects[2].contains(mx, my)) handleKey(null, 'U');
        else if (actionRects[3] != null && actionRects[3].contains(mx, my)) handleKey(null, 'F');
    }

    // Internal key dispatch by char (for mouse clicks)
    private void handleKey(java.awt.event.KeyEvent e, char action) {
        switch (action) {
            case 'A' -> engine.playerAttack();
            case 'C' -> { if (!engine.isIncapacitated()) game.openSpellbook(this); }
            case 'U' -> openPackForCombat();
            case 'F' -> {
                if (unfleeable) {
                    engine.log("There is no escape from the gods!", DANGER);
                } else {
                    engine.attemptFlee();
                }
            }
        }
    }

    // ── Item relay (the [U] action, served by InventoryOverlay) ───────────────

    /**
     * Opens the player's pack as this round's action, reusing {@link InventoryOverlay} rather
     * than growing a second item UI. The sleep/stun gate runs first — exactly as it does for the
     * spellbook — so an incapacitated player loses the turn instead of getting a free item use.
     * The round is only spent if something is actually used; see {@link #onCombatItemUsed()}.
     */
    private void openPackForCombat() {
        if (engine.isIncapacitated() || game.getGamePanel() == null) return;
        // Opening an empty pack mid-fight just hides the battle behind a menu and leaves the
        // player wondering what happened — say it instead. No turn is spent either way.
        if (!hasUsableItem()) {
            engine.log("You have nothing you can use in a fight.", CombatEngine.TEXT_DIM);
            return;
        }
        itemMode = true;
        game.getGamePanel().openInventory();
    }

    /** True if the pack holds anything a fight can actually consume. */
    private boolean hasUsableItem() {
        Player p = engine.getPlayer();
        if (p == null) return false;
        for (var slot : p.getInventorySlots()) {
            if (slot == null || slot.isEmpty()) continue;
            io.cannonforge.retroquest.model.Item.Type t = slot.getItem().getType();
            if (t == io.cannonforge.retroquest.model.Item.Type.POTION
             || t == io.cannonforge.retroquest.model.Item.Type.SCROLL
             || t == io.cannonforge.retroquest.model.Item.Type.WAND) return true;
        }
        return false;
    }

    /**
     * Reported by {@link InventoryOverlay} once an item has actually been consumed during a
     * fight. Using something costs the round — the monster answers, exactly as after a swing.
     * Closing the pack without using anything never reaches here, so it costs nothing.
     *
     * <p>The caller is expected to have closed the pack first; a wand goes through
     * {@link #executeCombatSpell(Spell)} instead, which already spends the round.
     */
    public void onCombatItemUsed() {
        if (!active || !itemMode || engine.isFinished()) return;
        itemMode = false;
        engine.itemUsedTurn();
    }

    /** True while the pack is open as a combat action. */
    public boolean isItemMode() { return itemMode; }

    // ── Spell relay (called by SpellbookOverlay) ──────────────────────────────
    public void executeCombatSpell(Spell spell) {
        if (!active) return;
        itemMode = false;                // a wand or spell spends the round on its own
        engine.executeCombatSpell(spell);
    }

    // ── Visual effect triggers (called by CombatEngine) ───────────────────────
    public void triggerSpellEffect(SpellEffectRenderer.EffectType type, boolean fromPlayer) {
        effectRenderer.triggerEffect(type, fromPlayer);
    }

    public void triggerMonsterFlash() { monsterHitTime = System.currentTimeMillis(); }
    public void triggerPlayerFlash()  { playerHitTime  = System.currentTimeMillis(); }

    // ── Public API delegated to engine (called by Spell.executeCombat) ────────
    public Player    getPlayer()              { return engine.getPlayer(); }
    public Monster   getMonster()             { return engine.getMonster(); }
    public Retroquest getGame()               { return engine.getGame(); }

    public void setProtEv(boolean b)          { engine.setProtEv(b); }
    public void setBless(boolean b)           { engine.setBless(b); }
    public void setInvisible(boolean b)       { engine.setInvisible(b); }
    public void setHaste(boolean b)           { engine.setHaste(b); }
    public void setShield(boolean b)         { engine.setShield(b); }
    public void setElemResist(boolean b)     { engine.setElemResist(b); }
    public void setPoisonTurns(int t)         { engine.setPoisonTurns(t); }
    public void setPoisonTurns(int t, int perTurn) { engine.setPoisonTurns(t, perTurn); }
    public void setStunTurns(int t)           { engine.setStunTurns(t); }
    public void setFearTurns(int t)           { engine.setFearTurns(t); }
    public void setBlindTurns(int t)          { engine.setBlindTurns(t); }
    public void clearMonsterDebuffs()         { engine.clearMonsterDebuffs(); }
    public void setPlayerPoisonTurns(int t)   { engine.setPlayerPoisonTurns(t); }
    public void setPlayerSleepTurns(int t)    { engine.setPlayerSleepTurns(t); }
    public void setPlayerStunTurns(int t)     { engine.setPlayerStunTurns(t); }
    public void setPlayerBlindTurns(int t)    { engine.setPlayerBlindTurns(t); }
    public int  getPlayerPoisonTurns()        { return engine.getPlayerPoisonTurns(); }
    public int  getPlayerSleepTurns()         { return engine.getPlayerSleepTurns(); }
    public int  getPlayerStunTurns()          { return engine.getPlayerStunTurns(); }
    public int  getPlayerBlindTurns()         { return engine.getPlayerBlindTurns(); }
    public boolean hasPrayer()                { return engine.hasPrayer(); }
    public boolean hasHolyArmor()             { return engine.hasHolyArmor(); }
    public void setPrayer(boolean b)          { engine.setPrayer(b); }
    public void setHolyArmor(boolean b)       { engine.setHolyArmor(b); }
    public void callVictory()                 { engine.victory(); }
    public void callFlee()                    { engine.guaranteedFlee(); }
    public void resetCombatBuffs()            { engine.resetCombatBuffs(); }

    // ── Log delegation ────────────────────────────────────────────────────────
    public void log(String msg, Color col)    { engine.log(msg, col); }
    public void logGood(String msg)           { engine.logGood(msg); }
    public void logDanger(String msg)         { engine.logDanger(msg); }
    public void logAmber(String msg)          { engine.logAmber(msg); }
    public void logCyan(String msg)           { engine.logCyan(msg); }
    public void logInfo(String msg)           { engine.logInfo(msg); }
    public void logDim(String msg)            { engine.logDim(msg); }
    public void logHpGreen(String msg)        { engine.logHpGreen(msg); }

    // ══════════════════════════════════════════════════════════════════════════
    // ── RENDERING ─────────────────────────────────────────────────────────────
    // ══════════════════════════════════════════════════════════════════════════

    public void paint(Graphics2D g, int panelW, int panelH) {
        if (!active) return;

        long now = System.currentTimeMillis();

        // ── Entry slide-in ──
        float ease = entryEase(entryTime, ENTRY_MS);

        // ── Dim the map behind ──
        g.setColor(new Color(0, 0, 0, (int)(190 * ease)));
        g.fillRect(0, 0, panelW, panelH);

        // ── Overlay card dimensions ──
        int cardW = Math.min(620, panelW - 40);
        int cardH = Math.min(430, panelH - 40);
        int cardX = (panelW - cardW) / 2;
        int rawY  = (panelH - cardH) / 2;
        int cardY = rawY + (int)((1f - ease) * cardH);   // slides up from below

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // ── Card shadow ──
        g.setColor(new Color(0, 0, 0, (int)(120 * ease)));
        g.fillRoundRect(cardX + 6, cardY + 6, cardW, cardH, 8, 8);

        // ── Card background ──
        GradientPaint cardBg = new GradientPaint(cardX, cardY, new Color(12, 17, 26), cardX, cardY + cardH, BG);
        g.setPaint(cardBg);
        g.fillRoundRect(cardX, cardY, cardW, cardH, 6, 6);

        // ── Card border with phosphor glow ──
        g.setStroke(new BasicStroke(1.5f));
        g.setColor(new Color(DANGER.getRed(), DANGER.getGreen(), DANGER.getBlue(), 160));
        g.drawRoundRect(cardX, cardY, cardW, cardH, 6, 6);
        g.setStroke(new BasicStroke(1f));
        g.setColor(new Color(DANGER.getRed(), DANGER.getGreen(), DANGER.getBlue(), 40));
        g.drawRoundRect(cardX - 1, cardY - 1, cardW + 2, cardH + 2, 7, 7);

        // ── Section heights ──
        int titleH  = 34;
        int arenaH  = 180;
        int buffH   = 22;
        int logH    = cardH - titleH - arenaH - buffH - 38;
        int keybarH = 38;

        int ty = cardY;

        // 1. Title bar
        paintTitleBar(g, cardX, ty, cardW, titleH);
        ty += titleH;

        // 2. Arena
        paintArena(g, cardX, ty, cardW, arenaH, now);
        ty += arenaH;

        // 3. Buff strip
        paintBuffStrip(g, cardX, ty, cardW, buffH);
        ty += buffH;

        // 4. Log
        paintLog(g, cardX, ty, cardW, logH);
        ty += logH;

        // 5. Key bar
        paintKeyBar(g, cardX, ty, cardW, keybarH);

        g.setStroke(new BasicStroke(1f));
    }

    // ── TITLE BAR ─────────────────────────────────────────────────────────────
    private void paintTitleBar(Graphics2D g, int x, int y, int w, int h) {
        // Background
        GradientPaint bg = new GradientPaint(x, y, new Color(35, 8, 8), x + w, y, new Color(8, 10, 20));
        g.setPaint(bg);
        g.fillRoundRect(x, y, w, h, 6, 6);
        g.fillRect(x, y + h/2, w, h/2);  // flatten bottom corners

        // Separator
        g.setColor(BORDER_COL);
        g.setStroke(new BasicStroke(1f));
        g.drawLine(x, y + h, x + w, y + h);

        g.setFont(F_TITLE);
        FontMetrics fm = g.getFontMetrics();

        // Left: combat icon + title
        g.setColor(DANGER);
        String title = "\u2694  COMBAT";
        // Glow
        g.setColor(new Color(DANGER.getRed(), DANGER.getGreen(), DANGER.getBlue(), 40));
        g.drawString(title, x + 14 + 1, y + (h + fm.getAscent() - fm.getDescent()) / 2 + 1);
        g.setColor(DANGER);
        g.drawString(title, x + 14, y + (h + fm.getAscent() - fm.getDescent()) / 2);

        // Right: depth/location hint
        g.setFont(F_SMALL);
        g.setColor(TEXT_DIM);
        String loc = game.isInDungeon()
            ? "DUNGEON  LVL " + game.getCurrentDepth()
            : (game.getCurrentTown() != null ? game.getCurrentTown().getDisplayName().toUpperCase() : "OVERWORLD");
        int lx = x + w - g.getFontMetrics().stringWidth(loc) - 14;
        g.drawString(loc, lx, y + (h + g.getFontMetrics().getAscent() - g.getFontMetrics().getDescent()) / 2);
    }

    // ── ARENA ─────────────────────────────────────────────────────────────────
    private void paintArena(Graphics2D g, int x, int y, int w, int h, long now) {
        Monster monster = engine.getMonster();
        Player  player  = engine.getPlayer();

        // Arena background — darker, stone-floor feel
        g.setColor(ARENA_BG);
        g.fillRect(x, y, w, h);

        // Subtle horizontal scanlines
        g.setColor(new Color(0, 0, 0, 18));
        for (int ly = y; ly < y + h; ly += 3)
            g.drawLine(x, ly, x + w, ly);

        // Central divider
        g.setColor(new Color(BORDER_COL.getRed(), BORDER_COL.getGreen(), BORDER_COL.getBlue(), 80));
        g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{4, 6}, 0));
        g.drawLine(x + w/2, y + 10, x + w/2, y + h - 10);
        g.setStroke(new BasicStroke(1f));

        int halfW = w / 2;
        // ── Player side (left) ──
        boolean playerFlash = (now - playerHitTime) < HIT_FLASH_MS;
        paintCombatant(g,
            x, y, halfW, h,
            "YOU",  player.getName(),
            player.getHp(), player.getMaxHp(),
            ImageAssetRegistry.get("player"),
            playerFlash, PHOSPHOR, false);

        // ── Monster side (right) ──
        boolean monsterFlash = (now - monsterHitTime) < HIT_FLASH_MS;
        String monName = monster.getName();
        boolean isBossPortrait = monName.startsWith("The ") || monName.startsWith("Echo of ");
        Image monImg = null;
        if (!isBossPortrait) {
            String monKey = "monsters/" + monster.getImageFileName().replace(".png", "");
            monImg = ImageAssetRegistry.get(monKey);
            if (monImg == null) monImg = ImageAssetRegistry.get("monsters/unknown");
        }

        paintCombatant(g,
            x + halfW, y, halfW, h,
            monName.toUpperCase(), "",
            monster.getHp(), engine.getMonsterMaxHpSnapshot(),
            monImg,
            monsterFlash, DANGER, true);

        // Boss portrait overlay (procedural, on top of fallback glyph)
        if (isBossPortrait && !engine.isFinished()) {
            paintBossPortrait(g, x + halfW, y + 32, halfW,
                              h - 32 - 42, monName, now);
        }

        effectRenderer.paint(g, x, y, w, h, now);
    }

    // ── BUFF STRIP ────────────────────────────────────────────────────────────
    private void paintBuffStrip(Graphics2D g, int x, int y, int w, int h) {
        Player player = engine.getPlayer();

        // Background
        g.setColor(new Color(8, 12, 20));
        g.fillRect(x, y, w, h);

        // Top separator
        g.setColor(BORDER_COL);
        g.setStroke(new BasicStroke(1f));
        g.drawLine(x, y, x + w, y);

        g.setFont(F_SMALL);
        FontMetrics fm = g.getFontMetrics();
        int badgePadX = 4;
        int badgeGap  = 4;
        int badgeH    = h - 6;
        int badgeY    = y + 3;
        int textY     = badgeY + (badgeH + fm.getAscent() - fm.getDescent()) / 2;

        Color BUFF_GREEN = new Color(0x55, 0xCC, 0x55);
        Color DEBUFF_RED = new Color(0xCC, 0x44, 0x44);
        Color MON_DEBUFF = new Color(0x44, 0xAA, 0x44);
        Color MON_BUFF   = new Color(0xDD, 0x88, 0x22);

        // ── Player badges (left side) ──
        java.util.List<Object[]> playerBadges = new java.util.ArrayList<>();
        if (player.isHasted())      playerBadges.add(new Object[]{"Haste",                            BUFF_GREEN});
        if (player.hasPrayer())     playerBadges.add(new Object[]{"Prayer",                            BUFF_GREEN});
        if (player.hasHolyArmor())  playerBadges.add(new Object[]{"HolyArmor",                        BUFF_GREEN});
        if (player.hasElemResist()) playerBadges.add(new Object[]{"ElemRes",                           BUFF_GREEN});
        if (player.hasShield())    playerBadges.add(new Object[]{"Shield",                            BUFF_GREEN});
        if (player.isInvisible())   playerBadges.add(new Object[]{"Invis",                             BUFF_GREEN});
        if (player.getAcBonus() > 0)  playerBadges.add(new Object[]{"+AC "  + player.getAcBonus(),   BUFF_GREEN});
        if (player.getHitBonus() > 0) playerBadges.add(new Object[]{"+Hit " + player.getHitBonus(),  BUFF_GREEN});
        if (player.getDmgBonus() > 0) playerBadges.add(new Object[]{"+Dmg " + player.getDmgBonus(),  BUFF_GREEN});
        if (engine.getPlayerPoisonTurns()>0) playerBadges.add(new Object[]{"Poison " + engine.getPlayerPoisonTurns() + "t",DEBUFF_RED});
        if (engine.getPlayerSleepTurns() >0) playerBadges.add(new Object[]{"Sleep "  + engine.getPlayerSleepTurns()  + "t",DEBUFF_RED});
        if (engine.getPlayerStunTurns()  >0) playerBadges.add(new Object[]{"Stun "   + engine.getPlayerStunTurns()   + "t",DEBUFF_RED});
        if (engine.getPlayerBlindTurns() >0) playerBadges.add(new Object[]{"Blind "  + engine.getPlayerBlindTurns()  + "t",DEBUFF_RED});

        int bx = x + 6;
        for (Object[] badge : playerBadges) {
            String label = (String) badge[0];
            Color  col   = (Color)  badge[1];
            int bw = fm.stringWidth(label) + badgePadX * 2;
            if (bx + bw > x + w / 2 - 4) break;
            g.setColor(new Color(col.getRed(), col.getGreen(), col.getBlue(), 40));
            g.fillRoundRect(bx, badgeY, bw, badgeH, 4, 4);
            g.setColor(new Color(col.getRed(), col.getGreen(), col.getBlue(), 120));
            g.drawRoundRect(bx, badgeY, bw, badgeH, 4, 4);
            g.setColor(col);
            g.drawString(label, bx + badgePadX, textY);
            bx += bw + badgeGap;
        }

        // ── Monster badges (right side, right-aligned) ──
        java.util.List<Object[]> monsterBadges = new java.util.ArrayList<>();
        if (engine.getPoisonTurns() > 0) monsterBadges.add(new Object[]{"Poison " + engine.getPoisonTurns() + "t", MON_DEBUFF});
        if (engine.getStunTurns()   > 0) monsterBadges.add(new Object[]{"Stun "   + engine.getStunTurns()   + "t", MON_DEBUFF});
        if (engine.getFearTurns()   > 0) monsterBadges.add(new Object[]{"Fear "   + engine.getFearTurns()   + "t", MON_DEBUFF});
        if (engine.getBlindTurns()  > 0) monsterBadges.add(new Object[]{"Blind "  + engine.getBlindTurns()  + "t", MON_DEBUFF});
        if (engine.isMonsterHasted())    monsterBadges.add(new Object[]{"Hasted",                                    MON_BUFF});

        // Measure total width to right-align
        int totalMonW = 0;
        for (Object[] badge : monsterBadges)
            totalMonW += fm.stringWidth((String) badge[0]) + badgePadX * 2 + badgeGap;
        if (totalMonW > 0) totalMonW -= badgeGap;

        bx = x + w - 6 - totalMonW;
        int rightBound = x + w - 6;
        for (Object[] badge : monsterBadges) {
            String label = (String) badge[0];
            Color  col   = (Color)  badge[1];
            int bw = fm.stringWidth(label) + badgePadX * 2;
            if (bx < x + w / 2 + 4 || bx + bw > rightBound) break;
            g.setColor(new Color(col.getRed(), col.getGreen(), col.getBlue(), 40));
            g.fillRoundRect(bx, badgeY, bw, badgeH, 4, 4);
            g.setColor(new Color(col.getRed(), col.getGreen(), col.getBlue(), 120));
            g.drawRoundRect(bx, badgeY, bw, badgeH, 4, 4);
            g.setColor(col);
            g.drawString(label, bx + badgePadX, textY);
            bx += bw + badgeGap;
        }

        // Central divider (faint, aligns with arena divider)
        g.setColor(new Color(BORDER_COL.getRed(), BORDER_COL.getGreen(), BORDER_COL.getBlue(), 60));
        g.drawLine(x + w / 2, y + 3, x + w / 2, y + h - 3);

        // Bottom separator
        g.setColor(BORDER_COL);
        g.drawLine(x, y + h - 1, x + w, y + h - 1);
    }

    private void paintCombatant(Graphics2D g,
                                 int x, int y, int w, int h,
                                 String title, String subtitle,
                                 int hp, int maxHp,
                                 Image sprite,
                                 boolean hitFlash, Color accent, boolean flipSprite) {
        int cx      = x + w / 2;
        int spriteS = Math.min(88, w - 30);
        int spriteX = cx - spriteS / 2;
        int spriteY = y + 8;

        // ── Name label ──
        g.setFont(F_STAT);
        FontMetrics fm = g.getFontMetrics();
        // Glow
        g.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 30));
        g.drawString(title, cx - fm.stringWidth(title)/2 + 1, y + 16 + 1);
        g.setColor(accent);
        g.drawString(title, cx - fm.stringWidth(title)/2, y + 16);

        // Subtitle (player name under "YOU")
        if (!subtitle.isEmpty()) {
            g.setFont(F_SMALL);
            g.setColor(TEXT_DIM);
            FontMetrics fmS = g.getFontMetrics();
            g.drawString(subtitle, cx - fmS.stringWidth(subtitle)/2, y + 28);
        }

        // ── Sprite ──
        spriteY = y + 32;
        int sH = h - 32 - 42;    // room for HP bar at bottom
        spriteS = Math.min(spriteS, sH);
        spriteX = cx - spriteS / 2;

        // Hit flash — red overlay
        if (hitFlash && sprite != null) {
            g.drawImage(sprite, spriteX, spriteY, spriteS, spriteS, null);
            g.setColor(new Color(220, 30, 30, 140));
            g.fillRect(spriteX, spriteY, spriteS, spriteS);
        } else if (sprite != null) {
            g.drawImage(sprite, spriteX, spriteY, spriteS, spriteS, null);
        } else {
            // Fallback ASCII glyph
            g.setFont(new Font("Monospaced", Font.BOLD, spriteS * 2 / 3));
            g.setColor(hitFlash ? new Color(220, 50, 50) : accent);
            String glyph = flipSprite ? "?" : "@";
            FontMetrics fmG = g.getFontMetrics();
            g.drawString(glyph, cx - fmG.stringWidth(glyph)/2,
                spriteY + spriteS/2 + fmG.getAscent()/2);
        }

        // Dead overlay
        if (hp <= 0) {
            g.setColor(new Color(0, 0, 0, 160));
            g.fillRect(spriteX, spriteY, spriteS, spriteS);
            g.setFont(F_TITLE);
            g.setColor(DANGER);
            FontMetrics fmD = g.getFontMetrics();
            g.drawString("DEAD", cx - fmD.stringWidth("DEAD")/2,
                spriteY + spriteS/2 + fmD.getAscent()/2);
        }

        // ── HP bar ──
        int barY = y + h - 34;
        int barX = x + 12;
        int barW = w - 24;

        float frac     = maxHp > 0 ? Math.max(0f, (float) hp / maxHp) : 0f;
        Color hpColor  = frac > 0.6f ? HP_GREEN : frac > 0.3f ? HP_YELLOW : HP_RED;
        Color hpBg     = new Color(hpColor.getRed()/5, hpColor.getGreen()/5, hpColor.getBlue()/5);

        // Track
        g.setColor(hpBg);
        g.fillRoundRect(barX, barY, barW, 14, 4, 4);

        // Fill
        int fillW = Math.max(0, (int)(barW * frac));
        if (fillW > 0) {
            GradientPaint gp = new GradientPaint(barX, barY, hpColor.brighter(), barX, barY+14, hpColor.darker());
            g.setPaint(gp);
            g.fillRoundRect(barX, barY, fillW, 14, 4, 4);
            // Sheen
            g.setColor(new Color(255, 255, 255, 25));
            g.fillRoundRect(barX + 1, barY + 1, fillW - 2, 6, 3, 3);
        }

        // Quarter ticks
        g.setColor(new Color(0, 0, 0, 80));
        g.setStroke(new BasicStroke(1f));
        for (int pct = 25; pct < 100; pct += 25)
            g.drawLine(barX + barW * pct / 100, barY + 1, barX + barW * pct / 100, barY + 13);

        // Border
        g.setColor(new Color(hpColor.getRed(), hpColor.getGreen(), hpColor.getBlue(), 100));
        g.drawRoundRect(barX, barY, barW, 14, 4, 4);

        // HP text
        g.setFont(F_LABEL);
        g.setColor(Color.WHITE);
        FontMetrics fmH = g.getFontMetrics();
        String hpStr = "HP  " + hp + " / " + maxHp;
        g.drawString(hpStr, barX + (barW - fmH.stringWidth(hpStr))/2, barY + 11);
    }

    // ── BOSS PORTRAIT (procedural, for endgame bosses without sprites) ────────

    private static final Color[] BOSS_GOD_COLORS = {
        new Color(180, 200, 255), new Color(255, 120,  40), new Color(140, 180, 255),
        new Color( 80, 220, 100), new Color( 60, 180, 200), new Color(160, 170, 200),
        new Color(200, 160,  60),
    };

    private void paintBossPortrait(Graphics2D g, int x, int y, int w, int h,
                                    String name, long now) {
        int cx = x + w / 2;
        int cy = y + h / 2;
        int orbR = Math.min(w, h) / 8;

        if (name.contains("United") || name.contains("Raging") || name.contains("Forsaken")) {
            // Multiple overlapping god-colored circles
            int count = name.contains("Forsaken") ? 6 : 7;
            for (int i = 0; i < count; i++) {
                double angle = (Math.PI * 2 * i / count) + now / 1200.0;
                float spread = name.contains("Raging") ? 1.4f : 0.8f;
                int ox = cx + (int)(Math.cos(angle) * orbR * spread);
                int oy = cy + (int)(Math.sin(angle) * orbR * spread * 0.6f);
                Color c = BOSS_GOD_COLORS[i % 7];
                // Glow
                g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 40));
                g.fillOval(ox - orbR - 4, oy - orbR - 4, (orbR + 4) * 2, (orbR + 4) * 2);
                // Core
                float flicker = name.contains("Raging")
                        ? 0.6f + 0.4f * (float)Math.sin(now / 150.0 + i) : 1f;
                g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(),
                                     (int)(140 * flicker)));
                g.fillOval(ox - orbR, oy - orbR, orbR * 2, orbR * 2);
            }
        } else if (name.contains("Desperate")) {
            // Single white-hot circle with cracks
            float pulse = 0.7f + 0.3f * (float)Math.sin(now / 200.0);
            int r = (int)(orbR * 2.5f);
            g.setColor(new Color(255, 255, 255, (int)(60 * pulse)));
            g.fillOval(cx - r, cy - r, r * 2, r * 2);
            g.setColor(new Color(255, 240, 200, (int)(180 * pulse)));
            g.fillOval(cx - orbR, cy - orbR, orbR * 2, orbR * 2);
            // Cracks
            g.setColor(new Color(255, 120, 40, (int)(100 * pulse)));
            g.setStroke(new BasicStroke(2f));
            for (int i = 0; i < 5; i++) {
                double a = Math.PI * 2 * i / 5 + now / 2000.0;
                g.drawLine(cx, cy,
                           cx + (int)(Math.cos(a) * r * 0.9f),
                           cy + (int)(Math.sin(a) * r * 0.9f));
            }
        } else if (name.startsWith("Echo of")) {
            // Single god-colored circle with afterimage
            int godIdx = name.contains("Pyralis") ? 1
                       : name.contains("Thalorax") ? 4
                       : name.contains("Bellorak") ? 6 : 0;
            Color c = BOSS_GOD_COLORS[godIdx];
            // Afterimage
            g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 30));
            g.fillOval(cx - orbR * 2 - 3, cy - orbR * 2, orbR * 4, orbR * 4);
            // Core
            g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 180));
            g.fillOval(cx - orbR, cy - orbR, orbR * 2, orbR * 2);
        } else {
            // Generic: golden orb (Dreaming Guardian)
            float pulse = 0.6f + 0.4f * (float)Math.sin(now / 600.0);
            g.setColor(new Color(255, 220, 100, (int)(40 * pulse)));
            g.fillOval(cx - orbR * 2, cy - orbR * 2, orbR * 4, orbR * 4);
            g.setColor(new Color(255, 200, 80, (int)(160 * pulse)));
            g.fillOval(cx - orbR, cy - orbR, orbR * 2, orbR * 2);
        }
    }

    // ── LOG ───────────────────────────────────────────────────────────────────
    private void paintLog(Graphics2D g, int x, int y, int w, int h) {
        // Log area background
        g.setColor(new Color(6, 8, 14));
        g.fillRect(x, y, w, h);

        // Top/bottom separators
        g.setColor(BORDER_COL);
        g.setStroke(new BasicStroke(1f));
        g.drawLine(x, y, x + w, y);
        g.drawLine(x, y + h, x + w, y + h);

        // Render the most recent log lines that fit
        g.setFont(F_LOG);
        FontMetrics fm = g.getFontMetrics();
        int lineH   = fm.getHeight() + 1;
        int maxLines = (h - 10) / lineH;
        int pad     = 14;

        CombatEngine.LogEntry[] entries = engine.getLogEntries().toArray(new CombatEngine.LogEntry[0]);
        int start   = Math.max(0, entries.length - maxLines);
        int drawY   = y + 8;

        for (int i = start; i < entries.length; i++) {
            CombatEngine.LogEntry e = entries[i];
            // Age-based fade (newest = full alpha, older = dimmer)
            float age     = Math.min(1f, (System.currentTimeMillis() - e.time) / 8000f);
            int   alpha   = Math.max(60, (int)(255 * (1f - age * 0.6f)));
            Color col     = new Color(e.color.getRed(), e.color.getGreen(), e.color.getBlue(), alpha);

            // Bullet for newest entry
            if (i == entries.length - 1) {
                g.setColor(new Color(AMBER.getRed(), AMBER.getGreen(), AMBER.getBlue(), alpha));
                g.drawString("\u25b8", pad - 10 + x, drawY + fm.getAscent());
            }

            g.setColor(col);
            g.drawString(e.text, x + pad, drawY + fm.getAscent());
            drawY += lineH;
        }
    }

    // ── KEY BAR ───────────────────────────────────────────────────────────────
    private void paintKeyBar(Graphics2D g, int x, int y, int w, int h) {
        boolean finished = engine.isFinished();

        // Background
        GradientPaint bg = new GradientPaint(x, y, new Color(8, 10, 18), x, y + h, BG);
        g.setPaint(bg);
        g.fillRect(x, y, w, h);
        g.fillRoundRect(x, y + h/2, w, h/2 + 6, 6, 6);   // round bottom corners

        if (finished) {
            g.setFont(F_KEY);
            g.setColor(AMBER);
            FontMetrics fm = g.getFontMetrics();
            String msg = "Press any key to continue...";
            g.drawString(msg, x + (w - fm.stringWidth(msg)) / 2, y + (h + fm.getAscent() - fm.getDescent()) / 2);
            return;
        }

        // Key badges
        String[][] keys = {
            {"A", "Attack"},
            {"C", "Spell"},
            {"U", "Use"},
            {"F", "Flee"}
        };

        int btnW    = 110;
        int gap     = 18;
        int totalW  = keys.length * btnW + (keys.length - 1) * gap;
        int bx      = x + (w - totalW) / 2;
        int bh      = h - 12;
        int by      = y + 6;

        for (int ki = 0; ki < keys.length; ki++) {
            String[] k = keys[ki];
            // Cache rect for mouse hit-testing
            actionRects[ki] = new java.awt.Rectangle(bx, by, btnW, bh);

            // Badge background
            g.setColor(new java.awt.Color(12, 18, 30));
            g.fillRoundRect(bx, by, btnW, bh, 5, 5);
            g.setColor(new java.awt.Color(CYAN_ACC.getRed(), CYAN_ACC.getGreen(), CYAN_ACC.getBlue(), 100));
            g.setStroke(new BasicStroke(1f));
            g.drawRoundRect(bx, by, btnW, bh, 5, 5);

            // Key letter in bracket
            g.setFont(F_KEY);
            FontMetrics fm = g.getFontMetrics();
            String badge = "[" + k[0] + "]";
            g.setColor(CYAN_ACC);
            g.drawString(badge, bx + 10, by + (bh + fm.getAscent() - fm.getDescent()) / 2);

            // Action label
            g.setFont(F_LABEL);
            g.setColor(TEXT_BRIGHT);
            FontMetrics fmL = g.getFontMetrics();
            g.drawString(k[1], bx + 10 + fm.stringWidth(badge) + 6, by + (bh + fmL.getAscent() - fmL.getDescent()) / 2);

            bx += btnW + gap;
        }
    }
}

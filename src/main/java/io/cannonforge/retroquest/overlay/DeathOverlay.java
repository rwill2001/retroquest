package io.cannonforge.retroquest.overlay;
import static io.cannonforge.retroquest.overlay.OverlayTheme.entryEase;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;

import io.cannonforge.retroquest.core.Fonts;
import io.cannonforge.retroquest.core.Retroquest;
import io.cannonforge.retroquest.core.SaveData;
import io.cannonforge.retroquest.core.Town;
import io.cannonforge.retroquest.model.Player;

/**
 * Full-screen death overlay — replaces the RetroDialog gameOver popup.
 * Fades in over the frozen game world.
 *
 * <p>The default action is <em>Continue</em> from the most recent save, labelled with
 * that save's level, place and age, so the highlighted option can never be the one
 * that destroys the run. New Game stays available, further down the list.
 */
public class DeathOverlay {

    private static final Color DANGER     = new Color(220,  55,  55);
    private static final Color TEXT_DIM   = new Color(120,  80,  80);
    private static final Color TEXT_BRIGHT= new Color(220, 200, 200);
    private static final Color SEL_BG     = new Color( 60,  10,  10);

    private static final Font F_SKULL  = Fonts.monoBold(28);
    private static final Font F_REASON = Fonts.mono    (13);
    private static final Font F_OPT    = Fonts.monoBold(13);
    private static final Font F_KEY    = Fonts.monoBold(11);
    private static final Font F_SMALL  = Fonts.mono    (10);

    /** What an option actually does, so ordering can change without renumbering logic. */
    private enum Action { CONTINUE, LOAD, NEW_GAME, QUIT }

    private record Option(String label, Action action, int slot) {}

    private final Retroquest game;
    private boolean active      = false;
    private String  deathReason = "";
    private int     selected    = 0;
    private long    entryTime   = 0;
    private static final long ENTRY_MS = 400;

    private final List<Option> options = new ArrayList<>();

    // Cached for mouse hit-testing — sized to the current option list
    private java.awt.Rectangle[] optRects = new java.awt.Rectangle[0];

    public DeathOverlay(Retroquest game) {
        this.game = game;
    }

    public boolean isActive() { return active; }

    public void show(String reason) {
        this.deathReason = reason;
        buildOptions();
        this.selected    = 0;   // → Continue whenever a save exists
        this.entryTime   = System.currentTimeMillis();
        this.active      = true;
    }

    public void close() { active = false; }

    // ── Save-slot discovery ───────────────────────────────────────────────────

    /**
     * Builds the option list, putting the newest save on top. Slot files are read
     * once, here, rather than on every paint.
     */
    private void buildOptions() {
        options.clear();

        Option newest = newestSave();
        if (newest != null) options.add(newest);
        options.add(new Option("Load Saved Game", Action.LOAD,     -1));
        options.add(new Option("New Game",        Action.NEW_GAME, -1));
        options.add(new Option("Quit",            Action.QUIT,     -1));

        optRects = new java.awt.Rectangle[options.size()];
    }

    /** Most recently written of the auto-save and the eight numbered slots, or null. */
    private Option newestSave() {
        File   bestFile = null;
        int    bestSlot = -1;
        long   bestTime = Long.MIN_VALUE;

        for (int slot = 0; slot <= 8; slot++) {
            File f = saveFile(slot);
            if (!f.exists()) continue;
            if (f.lastModified() > bestTime) {
                bestTime = f.lastModified();
                bestFile = f;
                bestSlot = slot;
            }
        }
        if (bestFile == null) return null;

        String label = "Continue" + describe(bestFile, bestTime);
        return new Option(label, Action.CONTINUE, bestSlot);
    }

    private static File saveFile(int slot) {
        return (slot == 0)
            ? new File("saves/retroquest_save_auto.sav")
            : new File("saves/retroquest_save_" + String.format("%02d", slot) + ".sav");
    }

    /** " — Lv 4, Moonhaven, 12m ago", degrading gracefully if the slot won't parse. */
    private String describe(File file, long modified) {
        String age = ageText(System.currentTimeMillis() - modified);
        try (InputStreamReader r = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            SaveData sd = new Gson().fromJson(r, SaveData.class);
            if (sd == null) return " \u2014 " + age;
            Player p = sd.getPlayer();
            StringBuilder sb = new StringBuilder(" \u2014 ");
            if (p != null) sb.append("Lv ").append(p.getLevel()).append(", ");
            sb.append(place(sd)).append(", ").append(age);
            return sb.toString();
        } catch (Exception ignored) {
            return " \u2014 " + age;
        }
    }

    private static String place(SaveData sd) {
        if (sd.isInDungeon()) {
            String authored = sd.getAuthoredDungeonName();
            if (authored != null && !authored.isBlank()) return authored.replace('_', ' ');
            return "Dungeon Lv " + sd.getCurrentDepth();
        }
        String town = sd.getCurrentTownName();
        return (town != null && !town.isBlank()) ? Town.displayName(town) : "Overworld";
    }

    private static String ageText(long ms) {
        if (ms < 0) ms = 0;
        long mins = ms / 60_000;
        if (mins < 1)    return "just now";
        if (mins < 60)   return mins + "m ago";
        long hours = mins / 60;
        if (hours < 24)  return hours + "h ago";
        return (hours / 24) + "d ago";
    }

    // ── Input ─────────────────────────────────────────────────────────────────

    public void handleKey(KeyEvent e) {
        if (!active) return;
        switch (e.getKeyCode()) {
            case KeyEvent.VK_UP,   KeyEvent.VK_K -> { if (selected > 0) selected--; }
            case KeyEvent.VK_DOWN, KeyEvent.VK_J -> { if (selected < options.size() - 1) selected++; }
            case KeyEvent.VK_ENTER               -> confirm();
            case KeyEvent.VK_1                   -> select(0);
            case KeyEvent.VK_2                   -> select(1);
            case KeyEvent.VK_3                   -> select(2);
            case KeyEvent.VK_4                   -> select(3);
        }
    }

    private void select(int i) {
        if (i < 0 || i >= options.size()) return;
        selected = i;
        confirm();
    }

    public void handleClick(int mx, int my) {
        if (!active) return;
        for (int i = 0; i < optRects.length; i++) {
            if (optRects[i] != null && optRects[i].contains(mx, my)) {
                select(i);
                return;
            }
        }
    }

    private void confirm() {
        if (selected < 0 || selected >= options.size()) return;
        Option opt = options.get(selected);
        active = false;
        switch (opt.action()) {
            case CONTINUE -> {
                game.loadGame(opt.slot());
                game.startOverlayRepaintTimer();
            }
            case LOAD -> {
                // Open the load overlay directly (gamePanel still alive). If the player
                // backs out of it, come back here: otherwise they are left walking
                // around dead with no way to reopen the death screen.
                String reason = deathReason;
                game.setSaveLoadCancelHook(() -> show(reason));
                game.getGamePanel().openLoadDialog();
                game.startOverlayRepaintTimer();
            }
            case NEW_GAME -> game.restart();   // close this window, open a fresh title screen
            case QUIT     -> System.exit(0);
        }
    }

    // ── Paint ─────────────────────────────────────────────────────────────────

    public void paint(Graphics2D g, int W, int H) {
        if (!active) return;
        try { doPaint(g, W, H); } catch (Exception ex) { ex.printStackTrace(); active = false; }
    }

    private void doPaint(Graphics2D g, int W, int H) {
        float ease = entryEase(entryTime, ENTRY_MS);

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Dark blood-red overlay — fades in
        g.setColor(new Color(20, 0, 0, (int)(220 * ease)));
        g.fillRect(0, 0, W, H);

        // Scanlines
        g.setColor(new Color(0, 0, 0, (int)(40 * ease)));
        for (int y = 0; y < H; y += 3) g.drawLine(0, y, W, y);

        int cW = Math.min(OverlayTheme.scaled(520), W - 60);
        int cH = Math.min(OverlayTheme.scaled(360), H - 40);
        int cX = (W - cW) / 2;
        int cY = (H - cH) / 2;

        // Card shadow
        g.setColor(new Color(0, 0, 0, (int)(140 * ease)));
        g.fillRoundRect(cX + 6, cY + 6, cW, cH, 8, 8);

        // Card background — alpha-scaled during fade-in
        Composite origComp = g.getComposite();
        g.setComposite(AlphaComposite.SrcOver.derive(ease));
        g.setPaint(new GradientPaint(cX, cY, new Color(18, 4, 4), cX, cY + cH, new Color(6, 2, 2)));
        g.fillRoundRect(cX, cY, cW, cH, 8, 8);

        // Card border — pulsing red
        float pulse = 0.6f + 0.4f * (float)Math.sin(System.currentTimeMillis() / 400.0);
        g.setStroke(new BasicStroke(1.5f));
        g.setColor(new Color(180, 30, 30, (int)(160 * pulse)));
        g.drawRoundRect(cX, cY, cW, cH, 8, 8);
        g.setComposite(origComp);
        g.setStroke(new BasicStroke(1f));

        int y = cY + OverlayTheme.scaled(24);

        // Skull
        g.setFont(F_SKULL);
        FontMetrics fmS = g.getFontMetrics();
        String skull = "\u2620  YOU HAVE DIED  \u2620";
        int skullX = cX + (cW - fmS.stringWidth(skull)) / 2;
        // Glow
        g.setColor(new Color(180, 20, 20, 60));
        g.drawString(skull, skullX + 1, y + fmS.getAscent() + 1);
        g.setColor(new Color(220, 50, 50, 255));
        g.drawString(skull, skullX, y + fmS.getAscent());
        y += fmS.getHeight() + OverlayTheme.scaled(6);

        // Divider
        g.setColor(new Color(80, 20, 20, 160));
        g.drawLine(cX + 20, y, cX + cW - 20, y);
        y += OverlayTheme.scaled(12);

        // Death reason — word-wrapped
        g.setFont(F_REASON);
        FontMetrics fmR = g.getFontMetrics();
        for (String line : OverlayTheme.wordWrap(fmR, deathReason, cW - 40)) {
            int lx = cX + (cW - fmR.stringWidth(line)) / 2;
            g.setColor(new Color(200, 160, 160, 220));
            g.drawString(line, lx, y + fmR.getAscent());
            y += fmR.getHeight() + 1;
        }
        y += OverlayTheme.scaled(14);

        // Divider
        g.setColor(new Color(60, 20, 20, 120));
        g.drawLine(cX + 20, y, cX + cW - 20, y);
        y += OverlayTheme.scaled(12);

        // Options
        g.setFont(F_OPT);
        FontMetrics fmO = g.getFontMetrics();
        int optH = fmO.getHeight() + OverlayTheme.scaled(10);

        for (int i = 0; i < options.size(); i++) {
            boolean sel = (i == selected);
            int optW = cW - OverlayTheme.scaled(60);
            int ox   = cX + OverlayTheme.scaled(30);
            int oy   = y;

            // Store rect for mouse hit-testing
            optRects[i] = new java.awt.Rectangle(ox, oy, optW, optH);

            // Background
            if (sel) {
                g.setColor(SEL_BG);
                g.fillRoundRect(ox, oy, optW, optH, 4, 4);
                g.setColor(new Color(DANGER.getRed(), DANGER.getGreen(), DANGER.getBlue(), 120));
                g.setStroke(new BasicStroke(1f));
                g.drawRoundRect(ox, oy, optW, optH, 4, 4);
            }

            // Number badge
            g.setFont(F_KEY);
            FontMetrics fmK = g.getFontMetrics();
            String num = "[" + (i + 1) + "]";
            g.setColor(sel ? DANGER : TEXT_DIM);
            g.drawString(num, ox + 10, oy + (optH + fmK.getAscent() - fmK.getDescent()) / 2);
            int numW = fmK.stringWidth(num) + 14;

            // Arrow
            if (sel) {
                g.setColor(DANGER);
                g.drawString("\u25b6", ox + numW, oy + (optH + fmK.getAscent() - fmK.getDescent()) / 2);
                numW += fmK.stringWidth("\u25b6") + 6;
            }

            // Option text — trimmed to the card if the save description is long
            g.setFont(F_OPT);
            g.setColor(sel ? TEXT_BRIGHT : TEXT_DIM);
            String label = options.get(i).label();
            int maxW = optW - numW - OverlayTheme.scaled(12);
            while (fmO.stringWidth(label) > maxW && label.length() > 1)
                label = label.substring(0, label.length() - 1);
            g.drawString(label, ox + numW,
                oy + (optH + fmO.getAscent() - fmO.getDescent()) / 2);

            y += optH + OverlayTheme.scaled(3);
        }

        // Bottom hint
        g.setFont(F_SMALL);
        FontMetrics fmSm = g.getFontMetrics();
        String hint = "\u2191\u2193 Navigate   Enter / Click to confirm";
        g.setColor(new Color(100, 60, 60, 180));
        g.drawString(hint, cX + (cW - fmSm.stringWidth(hint)) / 2, cY + cH - fmSm.getDescent() - OverlayTheme.scaled(6));
    }

}

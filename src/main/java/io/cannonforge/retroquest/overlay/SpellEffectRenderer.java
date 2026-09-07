package io.cannonforge.retroquest.overlay;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.AffineTransform;

import io.cannonforge.retroquest.core.Fonts;

/**
 * Renders animated spell effects during combat. Each effect type has a dedicated
 * paint method that animates over a normalized t (0→1) timeline.
 */
class SpellEffectRenderer {

    /** Visual effect categories triggered by spells, items, and monster abilities. */
    public enum EffectType {
        FIREBALL, LIGHTNING, ICE, MISSILE,
        HEAL, SLEEP, DEATH, BUFF, POISON,
        FIRE_BREATH, DRAIN, GIFT,
        HOLY, ENTANGLE, TIME_STOP, FEAR, WISH, METEOR_SWARM
    }

    // ── State ──────────────────────────────────────────────────────────────────
    private EffectType activeEffect     = null;
    private long       effectStartMs    = 0;
    private boolean    effectFromPlayer = true;

    // ── Timing constants ───────────────────────────────────────────────────────
    static final int EFFECT_MS  = 700;
    static final int MISSILE_MS = 400;
    static final int BREATH_MS  = 900;

    private static final Font F_TITLE = Fonts.monoBold(14);

    // ── Public API ─────────────────────────────────────────────────────────────

    void triggerEffect(EffectType type, boolean fromPlayer) {
        activeEffect     = type;
        effectStartMs    = System.currentTimeMillis();
        effectFromPlayer = fromPlayer;
    }

    /** Paint active effect if any. Called from CombatOverlay.paintArena(). */
    void paint(Graphics2D g, int x, int y, int w, int h, long now) {
        if (activeEffect == null) return;
        int ms = activeEffect == EffectType.MISSILE ? MISSILE_MS
               : (activeEffect == EffectType.FIRE_BREATH || activeEffect == EffectType.DRAIN
                  || activeEffect == EffectType.METEOR_SWARM) ? BREATH_MS
               : (activeEffect == EffectType.TIME_STOP || activeEffect == EffectType.WISH) ? 800
               : EFFECT_MS;
        float t = (now - effectStartMs) / (float) ms;
        if (t >= 1.0f) { activeEffect = null; return; }
        t = Math.max(0f, Math.min(1f, t));

        Shape oldClip = g.getClip();
        g.clipRect(x, y, w, h);

        int halfW = w / 2;
        // Fire points: player is left side center, monster is right side center
        float playerFX = x + halfW * 0.42f;
        float playerFY = y + h * 0.52f;
        float monFX    = x + halfW * 1.58f;
        float monFY    = y + h * 0.52f;

        float startX = effectFromPlayer ? playerFX : monFX;
        float startY = effectFromPlayer ? playerFY : monFY;
        float endX   = effectFromPlayer ? monFX    : playerFX;
        float endY   = effectFromPlayer ? monFY    : playerFY;

        Composite origComp = g.getComposite();

        switch (activeEffect) {
            case FIREBALL    -> paintFireball(g, t, startX, startY, endX, endY);
            case LIGHTNING   -> paintLightning(g, t, startX, startY, endX, endY);
            case ICE         -> paintIce(g, t, startX, startY, endX, endY);
            case MISSILE     -> paintMissile(g, t, startX, startY, endX, endY);
            case HEAL        -> paintHeal(g, t, effectFromPlayer ? playerFX : monFX,
                                                effectFromPlayer ? playerFY : monFY);
            case SLEEP       -> paintSleep(g, t, startX, startY, endX, endY);
            case DEATH       -> paintDeath(g, t, x, y, w, h, endX, endY);
            case BUFF        -> paintBuff(g, t, effectFromPlayer ? playerFX : monFX,
                                                effectFromPlayer ? playerFY : monFY);
            case POISON      -> paintPoison(g, t, endX, endY);
            case FIRE_BREATH -> paintFireBreath(g, t, startX, startY, endX, endY);
            case DRAIN       -> paintDrain(g, t, startX, startY, endX, endY);
            case GIFT        -> paintGift(g, t, endX, endY);
            case HOLY        -> paintHoly(g, t, x, y, w, h, endX, endY);
            case ENTANGLE    -> paintEntangle(g, t, endX, endY);
            case TIME_STOP   -> paintTimeStop(g, t, x, y, w, h, endX, endY);
            case FEAR        -> paintFear(g, t, x, y, w, h, endX, endY);
            case WISH        -> paintWish(g, t, x, y, w, h,
                                          effectFromPlayer ? playerFX : monFX,
                                          effectFromPlayer ? playerFY : monFY);
            case METEOR_SWARM -> paintMeteorSwarm(g, t, x, y, w, h, endX, endY);
        }

        g.setComposite(origComp);
        g.setClip(oldClip);
        g.setStroke(new BasicStroke(1f));
    }

    /** Map a spell name string to an EffectType. */
    static EffectType effectTypeForSpell(String name) {
        if (name == null) return EffectType.MISSILE;
        return switch (name) {
            case "Fireball"                                    -> EffectType.FIREBALL;
            case "Meteor Swarm"                                -> EffectType.METEOR_SWARM;
            case "Flame Strike"                                -> EffectType.HOLY;
            case "Fire Breath" -> EffectType.FIRE_BREATH;
            case "Lightning Bolt", "Chain Lightning"        -> EffectType.LIGHTNING;
            case "Ice Storm", "Cone of Cold"                -> EffectType.ICE;
            case "Magic Missile"                            -> EffectType.MISSILE;
            case "Sleep", "Charm Monster"                   -> EffectType.SLEEP;
            case "Death Spell", "Power Word Kill", "Cloudkill" -> EffectType.DEATH;
            case "Poison", "Drain"                          -> EffectType.POISON;
            case "Fear"                                     -> EffectType.FEAR;
            case "Holy Word", "Turn Undead",
                 "Divine Intervention"                      -> EffectType.HOLY;
            case "Entangle"                                 -> EffectType.ENTANGLE;
            case "Time Stop"                                -> EffectType.TIME_STOP;
            case "Wish"                                     -> EffectType.WISH;
            case "Resist Elements", "Shield", "Dispel Magic",
                 "Sanctuary"                                -> EffectType.BUFF;
            default -> {
                String n = name.toLowerCase();
                if (n.startsWith("cure") || n.contains("heal") || n.contains("restoration")
                        || n.contains("divine intervention"))
                    yield EffectType.HEAL;
                if (n.contains("haste") || n.contains("prayer") || n.contains("bless")
                        || n.contains("holy armor") || n.contains("invisibility")
                        || n.startsWith("protect"))
                    yield EffectType.BUFF;
                yield EffectType.MISSILE;
            }
        };
    }

    // ── Math helpers ───────────────────────────────────────────────────────────

    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }

    private static float easeInOut(float t) { return t < 0.5f ? 2*t*t : -1+(4-2*t)*t; }

    private static float clamp(float v, float lo, float hi) { return Math.max(lo, Math.min(hi, v)); }

    // ── Individual effect paint methods ────────────────────────────────────────

    private void paintFireball(Graphics2D g, float t, float sx, float sy, float ex, float ey) {
        if (t < 0.65f) {
            // Travel phase
            float pt = easeInOut(t / 0.65f);
            float bx = lerp(sx, ex, pt);
            float by = lerp(sy, ey, pt);
            // Trail
            for (int k = 0; k < 4; k++) {
                float tt = clamp(t - 0.04f * (k + 1), 0f, 0.65f);
                float tpt = easeInOut(tt / 0.65f);
                float tx = lerp(sx, ex, tpt);
                float ty = lerp(sy, ey, tpt);
                int tr = 7 - k * 1;
                int ta = 80 - k * 18;
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0f, ta / 255f)));
                g.setColor(new Color(200, 80, 10));
                g.fillOval((int)(tx - tr), (int)(ty - tr), tr * 2, tr * 2);
            }
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
            // Glow
            g.setColor(new Color(220, 40, 0, 80));
            g.fillOval((int)(bx - 10), (int)(by - 10), 20, 20);
            // Mid
            g.setColor(new Color(240, 120, 0, 180));
            g.fillOval((int)(bx - 7), (int)(by - 7), 14, 14);
            // Core
            g.setColor(new Color(255, 240, 80));
            g.fillOval((int)(bx - 4), (int)(by - 4), 9, 9);
        } else {
            // Explosion phase
            float et = (t - 0.65f) / 0.35f;
            int radius = (int)(9 + et * 31);
            int alpha  = (int)(255 * (1f - et));
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha / 255f));
            // Outer red
            g.setColor(new Color(220, 30, 0, Math.min(255, alpha)));
            g.fillOval((int)(ex - radius), (int)(ey - radius), radius * 2, radius * 2);
            // Mid orange
            int r2 = (int)(radius * 0.65f);
            g.setColor(new Color(255, 130, 0, Math.min(255, alpha)));
            g.fillOval((int)(ex - r2), (int)(ey - r2), r2 * 2, r2 * 2);
            // Core yellow
            int r3 = (int)(radius * 0.3f);
            g.setColor(new Color(255, 240, 80, Math.min(255, alpha)));
            g.fillOval((int)(ex - r3), (int)(ey - r3), r3 * 2, r3 * 2);
            // Sparks at 45° intervals
            g.setColor(new Color(255, 200, 60, Math.min(255, alpha)));
            g.setStroke(new BasicStroke(1.5f));
            for (int i = 0; i < 8; i++) {
                double angle = i * Math.PI / 4;
                int sparkLen = (int)(6 + et * 14);
                int x1 = (int)(ex + Math.cos(angle) * radius * 0.6);
                int y1 = (int)(ey + Math.sin(angle) * radius * 0.6);
                int x2 = (int)(ex + Math.cos(angle) * (radius * 0.6 + sparkLen));
                int y2 = (int)(ey + Math.sin(angle) * (radius * 0.6 + sparkLen));
                g.drawLine(x1, y1, x2, y2);
            }
        }
    }

    private void paintLightning(Graphics2D g, float t, float sx, float sy, float ex, float ey) {
        float alpha = t < 0.25f ? 1f : 1f - (t - 0.25f) / 0.75f;
        java.util.Random rng = new java.util.Random(effectStartMs);
        // Build bolt: 8 segments
        int segs = 8;
        float[] bx = new float[segs + 1];
        float[] by = new float[segs + 1];
        bx[0] = sx; by[0] = sy;
        bx[segs] = ex; by[segs] = ey;
        for (int i = 1; i < segs; i++) {
            float frac = i / (float) segs;
            bx[i] = lerp(sx, ex, frac) + (rng.nextFloat() - 0.5f) * 12f;
            by[i] = lerp(sy, ey, frac) + (rng.nextFloat() - 0.5f) * 40f;
        }
        // Draw 3 passes: blue outer, cyan mid, white core
        float[] strokeWidths = {8f, 3f, 1.5f};
        Color[]   colors       = {
            new Color(60, 80, 255,  (int)(180 * alpha)),
            new Color(80, 200, 255, (int)(220 * alpha)),
            new Color(255, 255, 255,(int)(255 * alpha))
        };
        for (int pass = 0; pass < 3; pass++) {
            g.setStroke(new BasicStroke(strokeWidths[pass], BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(colors[pass]);
            for (int i = 0; i < segs; i++)
                g.drawLine((int)bx[i], (int)by[i], (int)bx[i+1], (int)by[i+1]);
        }
        // 3 branches from random mid-segments
        rng.setSeed(effectStartMs + 1);
        for (int b = 0; b < 3; b++) {
            int seg = 1 + rng.nextInt(segs - 2);
            float branchLen = 30 + rng.nextFloat() * 20f;
            double branchAngle = Math.atan2(by[seg+1] - by[seg], bx[seg+1] - bx[seg])
                                 + (rng.nextBoolean() ? 1 : -1) * (Math.PI / 4 + rng.nextFloat() * 0.3);
            int ex2 = (int)(bx[seg] + Math.cos(branchAngle) * branchLen);
            int ey2 = (int)(by[seg] + Math.sin(branchAngle) * branchLen);
            g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(new Color(80, 200, 255, (int)(160 * alpha)));
            g.drawLine((int)bx[seg], (int)by[seg], ex2, ey2);
        }
        g.setStroke(new BasicStroke(1f));
    }

    private void paintIce(Graphics2D g, float t, float sx, float sy, float ex, float ey) {
        if (t < 0.55f) {
            // 3 shards flying in cluster
            float pt = easeInOut(t / 0.55f);
            float cx = lerp(sx, ex, pt);
            float cy = lerp(sy, ey, pt);
            double baseAngle = Math.atan2(ey - sy, ex - sx);
            int[] offsets = {-15, 0, 15};
            for (int i = 0; i < 3; i++) {
                double angle = baseAngle + Math.toRadians(offsets[i]);
                AffineTransform old = g.getTransform();
                g.translate(cx, cy);
                g.rotate(angle + Math.PI / 2);
                // Shard: 5x14 rectangle
                g.setColor(new Color(40, 120, 200));
                g.fillRect(-2, -7, 5, 14);
                g.setColor(new Color(180, 220, 255));
                g.drawRect(-2, -7, 5, 14);
                g.setColor(new Color(220, 240, 255, 120));
                g.fillRect(-1, -6, 2, 8);
                g.setTransform(old);
            }
        } else {
            // Burst: 6-point snowflake
            float bt = (t - 0.55f) / 0.45f;
            int lineLen = (int)(4 + bt * 14);
            int dotR    = 2;
            float alpha = 1f - bt * 0.8f;
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            g.setColor(new Color(160, 210, 255));
            g.setStroke(new BasicStroke(1.5f));
            for (int i = 0; i < 6; i++) {
                double angle = i * Math.PI / 3;
                int x1 = (int) ex, y1 = (int) ey;
                int x2 = (int)(ex + Math.cos(angle) * lineLen);
                int y2 = (int)(ey + Math.sin(angle) * lineLen);
                g.drawLine(x1, y1, x2, y2);
                // dot at tip
                g.setColor(new Color(220, 240, 255));
                g.fillOval(x2 - dotR, y2 - dotR, dotR * 2, dotR * 2);
                g.setColor(new Color(160, 210, 255));
            }
        }
    }

    private void paintMissile(Graphics2D g, float t, float sx, float sy, float ex, float ey) {
        float pt = easeInOut(t);
        float bx = lerp(sx, ex, pt);
        float by = lerp(sy, ey, pt);
        // Trail
        for (int k = 0; k < 2; k++) {
            float tt = clamp(t - 0.06f * (k + 1), 0f, 1f);
            float tpt = easeInOut(tt);
            float tx = lerp(sx, ex, tpt);
            float ty = lerp(sy, ey, tpt);
            int ta = 80 - k * 30;
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0f, ta / 255f)));
            g.setColor(new Color(100, 60, 180));
            g.fillOval((int)(tx - 5), (int)(ty - 5), 10, 10);
        }
        // Orb
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
        g.setColor(new Color(60, 20, 120, 100));
        g.fillOval((int)(bx - 11), (int)(by - 11), 22, 22);
        g.setColor(new Color(140, 80, 220, 180));
        g.fillOval((int)(bx - 7), (int)(by - 7), 14, 14);
        g.setColor(new Color(255, 255, 255));
        g.fillOval((int)(bx - 3), (int)(by - 3), 7, 7);
        // Impact ring
        if (t > 0.85f) {
            float it = (t - 0.85f) / 0.15f;
            int r = (int)(it * 30);
            int a = (int)(255 * (1f - it));
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, a / 255f));
            g.setColor(new Color(200, 180, 255));
            g.setStroke(new BasicStroke(2f));
            g.drawOval((int)(ex - r), (int)(ey - r), r * 2, r * 2);
        }
    }

    private void paintHeal(Graphics2D g, float t, float cx, float cy) {
        // 8 sparkles rising upward
        for (int i = 0; i < 8; i++) {
            float delay = i / 8f * 0.5f;
            float pt    = clamp((t - delay) / (1f - delay), 0f, 1f);
            if (pt <= 0f) continue;
            double angle  = i * (Math.PI * 2 / 8);
            float rx = (float)(Math.cos(angle) * 16 * pt);
            float ry = (float)(Math.sin(angle) * 16 * pt) - 30 * pt;
            float hue   = (0.33f + t * 0.15f + i * 0.03f) % 1f;
            Color c = Color.getHSBColor(hue, 0.7f, 0.9f);
            int alpha = (int)(200 * (1f - pt * 0.8f));
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha / 255f));
            g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue()));
            int r = 3;
            g.fillOval((int)(cx + rx - r), (int)(cy + ry - r), r * 2, r * 2);
        }
        // Pulsing cross
        float crossT = t < 0.6f ? t / 0.6f : (1f - t) / 0.4f;
        int arm = (int)(4 + crossT * 16);
        float alpha = 0.9f * crossT;
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        g.setColor(new Color(80, 220, 80));
        g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine((int)cx - arm, (int)cy, (int)cx + arm, (int)cy);
        g.drawLine((int)cx, (int)cy - arm, (int)cx, (int)cy + arm);
    }

    private void paintSleep(Graphics2D g, float t, float sx, float sy, float ex, float ey) {
        // Soft blue orb traveling to target
        if (t < 0.6f) {
            float pt = easeInOut(t / 0.6f);
            float bx = lerp(sx, ex, pt);
            float by = lerp(sy, ey, pt) + (float)Math.sin(pt * Math.PI) * -12f;
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.9f));
            g.setColor(new Color(40, 80, 160, 80));
            g.fillOval((int)(bx - 10), (int)(by - 10), 20, 20);
            g.setColor(new Color(100, 150, 220, 180));
            g.fillOval((int)(bx - 5), (int)(by - 5), 11, 11);
            g.setColor(new Color(200, 220, 255));
            g.fillOval((int)(bx - 2), (int)(by - 2), 5, 5);
        }
        // 3 floating Z's after impact
        if (t > 0.6f) {
            g.setFont(F_TITLE);
            FontMetrics fm = g.getFontMetrics();
            for (int i = 0; i < 3; i++) {
                float delay = i * 0.13f;
                float zt    = clamp((t - 0.6f - delay) / 0.4f, 0f, 1f);
                if (zt <= 0f) continue;
                float zx = ex + (i - 1) * 12f + zt * 4;
                float zy = ey - 10 - zt * 20f;
                int alpha = (int)(220 * (1f - zt));
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha / 255f));
                g.setColor(new Color(100, 150, 220));
                g.drawString("Z", (int)zx - fm.stringWidth("Z") / 2, (int)zy);
            }
        }
    }

    private void paintDeath(Graphics2D g, float t, int ax, int ay, int aw, int ah,
                             float ex, float ey) {
        if (t < 0.3f) {
            float alpha = 180 * (t / 0.3f);
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha / 255f));
            g.setColor(new Color(80, 0, 0));
            // Vignette: dark edges creeping inward
            int shrink = (int)((1f - t / 0.3f) * Math.min(aw, ah) / 4);
            g.fillRect(ax, ay, shrink, ah);
            g.fillRect(ax + aw - shrink, ay, shrink, ah);
            g.fillRect(ax, ay, aw, shrink);
            g.fillRect(ax, ay + ah - shrink, aw, shrink);
        } else if (t < 0.7f) {
            float pt    = (t - 0.3f) / 0.4f;
            float pulse = (float)(0.5 + 0.5 * Math.sin(pt * Math.PI * 4));
            int alpha   = (int)(180 + 60 * pulse);
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha / 255f));
            g.setFont(Fonts.monoBold(24));
            g.setColor(new Color(160, 0, 0, alpha));
            FontMetrics fm = g.getFontMetrics();
            String skull = "\u2620";
            g.drawString(skull, (int)ex - fm.stringWidth(skull) / 2, (int)ey + fm.getAscent() / 2);
        } else {
            float alpha = 1f - (t - 0.7f) / 0.3f;
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha * 0.7f));
            g.setColor(new Color(80, 0, 0));
            g.fillRect(ax, ay, aw, ah);
        }
    }

    private void paintBuff(Graphics2D g, float t, float cx, float cy) {
        float baseAngle = t * (float)(Math.PI * 3);
        float radius    = 12 + 8 * (float)Math.sin(t * Math.PI * 4);
        for (int i = 0; i < 6; i++) {
            float angle = baseAngle + i * (float)(Math.PI * 2 / 6);
            float px    = cx + (float)Math.cos(angle) * radius;
            float py    = cy + (float)Math.sin(angle) * radius;
            float trail = t < 0.85f ? 1f : (1f - t) / 0.15f;
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.85f * trail));
            g.setColor(new Color(255, 200, 80));
            g.fillOval((int)(px - 3), (int)(py - 3), 6, 6);
            // Sparkle cross
            g.setColor(new Color(255, 240, 160, 140));
            g.setStroke(new BasicStroke(1f));
            g.drawLine((int)px - 4, (int)py, (int)px + 4, (int)py);
            g.drawLine((int)px, (int)py - 4, (int)px, (int)py + 4);
        }
    }

    private void paintPoison(Graphics2D g, float t, float cx, float cy) {
        for (int i = 0; i < 5; i++) {
            float delay = i * 0.12f;
            float pt    = clamp((t - delay) / (1f - delay), 0f, 1f);
            if (pt <= 0f) continue;
            float bx = cx + (i - 2) * 8f;
            float by = cy - pt * 30f;
            int alpha = (int)(200 * (1f - pt));
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha / 255f));
            g.setColor(new Color(60, 180, 60));
            int r = 4;
            g.fillOval((int)(bx - r), (int)(by - r), r * 2, r * 2);
        }
    }

    private void paintFireBreath(Graphics2D g, float t, float sx, float sy, float ex, float ey) {
        java.util.Random rng = new java.util.Random(effectStartMs);
        float dx = ex - sx, dy = ey - sy;
        float dist = (float)Math.sqrt(dx * dx + dy * dy);
        double baseAngle = Math.atan2(dy, dx);

        if (t < 0.15f) {
            // Phase 1: Fire builds at dragon's mouth — pulsing glow
            float pt = t / 0.15f;
            float pulse = 0.6f + 0.4f * (float)Math.sin(pt * Math.PI * 4);
            int glowR = (int)(8 + pt * 16);
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.6f * pulse));
            g.setColor(new Color(255, 60, 0));
            g.fillOval((int)(sx - glowR), (int)(sy - glowR), glowR * 2, glowR * 2);
            g.setColor(new Color(255, 200, 40));
            int innerR = glowR / 2;
            g.fillOval((int)(sx - innerR), (int)(sy - innerR), innerR * 2, innerR * 2);
        } else if (t < 0.75f) {
            // Phase 2: Wide cone of fire sweeps toward player
            float pt = (t - 0.15f) / 0.6f;
            float reach = pt * dist;
            float coneHalf = (float)Math.toRadians(22 + pt * 8);

            // Flame streams — 12 streams fanning out within the cone
            for (int i = 0; i < 12; i++) {
                float streamAngle = (float)(baseAngle + coneHalf * (2f * rng.nextFloat() - 1f));
                float streamLen = reach * (0.6f + 0.4f * rng.nextFloat());
                float fx = sx + (float)Math.cos(streamAngle) * streamLen;
                float fy = sy + (float)Math.sin(streamAngle) * streamLen;

                // Each stream is a chain of 5 overlapping circles
                for (int j = 0; j < 5; j++) {
                    float frac = (j + 1) / 5f;
                    float px = lerp(sx, fx, frac);
                    float py = lerp(sy, fy, frac);
                    // Wiggle
                    px += (rng.nextFloat() - 0.5f) * 6f;
                    py += (rng.nextFloat() - 0.5f) * 6f;
                    int r = 4 + (int)(frac * 6);
                    float hue = rng.nextFloat() * 0.12f; // 0 to 0.12 = red–orange–yellow
                    Color c = Color.getHSBColor(hue, 0.95f, 0.95f);
                    float alpha = (1f - frac * 0.3f) * Math.min(1f, pt * 3f);
                    g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, clamp(alpha, 0f, 1f)));
                    g.setColor(c);
                    g.fillOval((int)(px - r), (int)(py - r), r * 2, r * 2);
                }
            }

            // Bright core beam along center
            float coreLen = reach * 0.9f;
            float coreX = sx + (float)Math.cos(baseAngle) * coreLen;
            float coreY = sy + (float)Math.sin(baseAngle) * coreLen;
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.7f));
            g.setStroke(new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(new Color(255, 220, 80));
            g.drawLine((int)sx, (int)sy, (int)coreX, (int)coreY);
            g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(new Color(255, 255, 200));
            g.drawLine((int)sx, (int)sy, (int)coreX, (int)coreY);

            // Ember particles rising from the flame area
            for (int i = 0; i < 8; i++) {
                float embFrac = rng.nextFloat();
                float embAngle = (float)(baseAngle + coneHalf * (2f * rng.nextFloat() - 1f));
                float embDist = reach * embFrac;
                float embX = sx + (float)Math.cos(embAngle) * embDist;
                float embY = sy + (float)Math.sin(embAngle) * embDist;
                // Rise over time
                float rise = pt * 12f * rng.nextFloat();
                embY -= rise;
                int embR = 1 + rng.nextInt(2);
                float embA = (1f - embFrac) * 0.8f;
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, clamp(embA, 0f, 1f)));
                g.setColor(new Color(255, 180 + rng.nextInt(60), 20));
                g.fillOval((int)(embX - embR), (int)(embY - embR), embR * 2, embR * 2);
            }

            // Heat haze on player side — slight orange tint
            if (pt > 0.5f) {
                float hazeA = (pt - 0.5f) * 0.3f;
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, hazeA));
                g.setColor(new Color(255, 80, 0));
                int hazeR = 30 + (int)(pt * 20);
                g.fillOval((int)(ex - hazeR), (int)(ey - hazeR), hazeR * 2, hazeR * 2);
            }
        } else {
            // Phase 3: Dissipation — embers drift up, orange glow fades
            float pt = (t - 0.75f) / 0.25f;
            float fadeAlpha = 1f - pt;

            // Fading smoke wisps
            for (int i = 0; i < 6; i++) {
                float frac = (i + 1) / 6f;
                float wispAngle = (float)(baseAngle + (rng.nextFloat() - 0.5f) * 0.6f);
                float wispDist = dist * frac;
                float wx = sx + (float)Math.cos(wispAngle) * wispDist;
                float wy = sy + (float)Math.sin(wispAngle) * wispDist - pt * 20f;
                int r = 6 + (int)(pt * 8);
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, fadeAlpha * 0.4f));
                g.setColor(new Color(80, 40, 20));
                g.fillOval((int)(wx - r), (int)(wy - r), r * 2, r * 2);
            }

            // Lingering embers
            for (int i = 0; i < 10; i++) {
                float embX = lerp(sx, ex, rng.nextFloat()) + (rng.nextFloat() - 0.5f) * 40;
                float embY = lerp(sy, ey, rng.nextFloat()) - pt * (10 + rng.nextFloat() * 20);
                int embR = 1;
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, fadeAlpha * 0.9f));
                g.setColor(new Color(255, 160 + rng.nextInt(80), 20));
                g.fillOval((int)(embX - embR), (int)(embY - embR), embR * 2, embR * 2);
            }
        }
    }

    private void paintDrain(Graphics2D g, float t, float sx, float sy, float ex, float ey) {
        java.util.Random rng = new java.util.Random(effectStartMs);
        // 5 dark tendrils arcing from monster to player
        for (int i = 0; i < 5; i++) {
            float delay = i * 0.08f;
            float pt = clamp((t - delay) / (0.7f - delay), 0f, 1f);
            if (pt <= 0f) continue;

            float reach = easeInOut(pt);
            float sway = (rng.nextFloat() - 0.5f) * 60f;
            float midX = lerp(sx, ex, 0.5f) + sway;
            float midY = lerp(sy, ey, 0.5f) - 15 + rng.nextFloat() * 30f;

            // Quadratic bezier from source → mid → target
            int segs = 10;
            float prevX = sx, prevY = sy;
            for (int j = 1; j <= (int)(segs * reach); j++) {
                float bt = j / (float) segs;
                float oneMinus = 1f - bt;
                float bx = oneMinus * oneMinus * sx + 2 * oneMinus * bt * midX + bt * bt * ex;
                float by = oneMinus * oneMinus * sy + 2 * oneMinus * bt * midY + bt * bt * ey;

                // Outer dark tendril
                float tendrilAlpha = (1f - bt * 0.5f) * (t < 0.7f ? 1f : (1f - (t - 0.7f) / 0.3f));
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, clamp(tendrilAlpha * 0.7f, 0f, 1f)));
                g.setStroke(new BasicStroke(3.5f - i * 0.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.setColor(new Color(40, 0, 60));
                g.drawLine((int)prevX, (int)prevY, (int)bx, (int)by);

                // Inner purple glow
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, clamp(tendrilAlpha * 0.5f, 0f, 1f)));
                g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.setColor(new Color(140, 40, 180));
                g.drawLine((int)prevX, (int)prevY, (int)bx, (int)by);

                prevX = bx;
                prevY = by;
            }
        }

        // Dark vignette pulsing on the player side
        if (t > 0.2f) {
            float vt = clamp((t - 0.2f) / 0.6f, 0f, 1f);
            float pulse = 0.5f + 0.5f * (float)Math.sin(vt * Math.PI * 6);
            float vigAlpha = vt * 0.35f * pulse;
            if (t > 0.7f) vigAlpha *= (1f - (t - 0.7f) / 0.3f);
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, clamp(vigAlpha, 0f, 1f)));
            g.setColor(new Color(30, 0, 50));
            int vigR = 25 + (int)(vt * 15);
            g.fillOval((int)(ex - vigR), (int)(ey - vigR), vigR * 2, vigR * 2);
        }

        // Ghostly particles drifting from player toward monster
        if (t > 0.15f) {
            float particleT = clamp((t - 0.15f) / 0.7f, 0f, 1f);
            for (int i = 0; i < 6; i++) {
                float pDelay = rng.nextFloat() * 0.4f;
                float pProg = clamp((particleT - pDelay) / (1f - pDelay), 0f, 1f);
                if (pProg <= 0f) continue;
                // Particles travel from player back toward monster
                float px = lerp(ex, sx, pProg) + (rng.nextFloat() - 0.5f) * 20f;
                float py = lerp(ey, sy, pProg) + (rng.nextFloat() - 0.5f) * 20f - pProg * 8f;
                float pAlpha = (1f - pProg) * 0.7f;
                if (t > 0.7f) pAlpha *= (1f - (t - 0.7f) / 0.3f);
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, clamp(pAlpha, 0f, 1f)));
                g.setColor(new Color(180, 100, 220));
                int pr = 2;
                g.fillOval((int)(px - pr), (int)(py - pr), pr * 2, pr * 2);
            }
        }
    }

    private void paintHoly(Graphics2D g, float t, int ax, int ay, int aw, int ah,
                            float ex, float ey) {
        if (t < 0.25f) {
            // Phase 1: Light pillar descends from above
            float pt = t / 0.25f;
            float pillarTop = ay;
            float pillarBot = lerp(ay, ey, pt);
            int pillarW = 18;
            // Outer golden glow
            float alpha = 0.5f * pt;
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            g.setColor(new Color(255, 220, 80));
            g.fillRect((int)(ex - pillarW), (int)pillarTop, pillarW * 2, (int)(pillarBot - pillarTop));
            // Inner white core
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha * 1.4f));
            g.setColor(new Color(255, 255, 220));
            g.fillRect((int)(ex - pillarW / 3), (int)pillarTop, pillarW * 2 / 3, (int)(pillarBot - pillarTop));
        } else if (t < 0.6f) {
            // Phase 2: Full pillar + radiant burst expanding
            float pt = (t - 0.25f) / 0.35f;
            float pulse = 0.7f + 0.3f * (float)Math.sin(pt * Math.PI * 5);
            // Pillar
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.6f * pulse));
            g.setColor(new Color(255, 220, 80));
            g.fillRect((int)(ex - 18), ay, 36, (int)(ey - ay));
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.8f * pulse));
            g.setColor(new Color(255, 255, 220));
            g.fillRect((int)(ex - 6), ay, 12, (int)(ey - ay));
            // Radiant burst — rays at 30° intervals
            int rays = 12;
            int rayLen = (int)(8 + pt * 35);
            g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (int i = 0; i < rays; i++) {
                double angle = i * (Math.PI * 2 / rays);
                float ra = 0.7f * pulse * (1f - pt * 0.3f);
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, ra));
                g.setColor(new Color(255, 240, 140));
                int x1 = (int)(ex + Math.cos(angle) * 6);
                int y1 = (int)(ey + Math.sin(angle) * 6);
                int x2 = (int)(ex + Math.cos(angle) * rayLen);
                int y2 = (int)(ey + Math.sin(angle) * rayLen);
                g.drawLine(x1, y1, x2, y2);
            }
            // Central flare
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.9f * pulse));
            g.setColor(new Color(255, 255, 200));
            int flareR = (int)(6 + pt * 10);
            g.fillOval((int)(ex - flareR), (int)(ey - flareR), flareR * 2, flareR * 2);
        } else {
            // Phase 3: Fade out with drifting golden motes
            float pt = (t - 0.6f) / 0.4f;
            float fadeAlpha = 1f - pt;
            // Fading pillar
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, fadeAlpha * 0.3f));
            g.setColor(new Color(255, 220, 80));
            g.fillRect((int)(ex - 18), ay, 36, (int)(ey - ay));
            // Golden motes rising
            java.util.Random rng = new java.util.Random(effectStartMs);
            for (int i = 0; i < 10; i++) {
                float mx = ex + (rng.nextFloat() - 0.5f) * 40;
                float my = ey - pt * (20 + rng.nextFloat() * 30) - rng.nextFloat() * 20;
                float ma = fadeAlpha * (0.5f + rng.nextFloat() * 0.5f);
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, clamp(ma, 0f, 1f)));
                g.setColor(new Color(255, 230, 100));
                g.fillOval((int)(mx - 2), (int)(my - 2), 4, 4);
            }
        }
    }

    private void paintEntangle(Graphics2D g, float t, float cx, float cy) {
        java.util.Random rng = new java.util.Random(effectStartMs);
        int vineCount = 7;
        for (int i = 0; i < vineCount; i++) {
            float delay = i * 0.06f;
            float pt = clamp((t - delay) / (0.7f - delay), 0f, 1f);
            if (pt <= 0f) continue;

            // Each vine rises from below with a slight horizontal sway
            float baseX = cx + (i - vineCount / 2f) * 9f + (rng.nextFloat() - 0.5f) * 10f;
            float baseY = cy + 30;
            float tipY  = baseY - pt * 60f;
            float sway  = (float)Math.sin(pt * Math.PI * 2 + i) * 8f;

            // Fade in, then hold, then fade out at end
            float alpha = t < 0.7f ? clamp(pt * 2f, 0f, 1f) : (1f - (t - 0.7f) / 0.3f);
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, clamp(alpha, 0f, 1f)));

            // Draw vine as segmented curve
            int segs = 8;
            float prevX = baseX, prevY = baseY;
            g.setStroke(new BasicStroke(2.5f - i * 0.15f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(new Color(30 + rng.nextInt(30), 120 + rng.nextInt(40), 20));
            for (int j = 1; j <= segs; j++) {
                float frac = j / (float) segs;
                float segY = lerp(baseY, tipY, frac);
                float segX = baseX + sway * frac * (float)Math.sin(frac * Math.PI * 1.5f + i * 0.7f);
                g.drawLine((int)prevX, (int)prevY, (int)segX, (int)segY);
                prevX = segX;
                prevY = segY;
            }

            // Small leaves/thorns along the vine
            if (pt > 0.4f) {
                float leafAlpha = clamp((pt - 0.4f) * 3f, 0f, 1f) * alpha;
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, clamp(leafAlpha, 0f, 1f)));
                g.setColor(new Color(40, 160, 30));
                for (int l = 0; l < 3; l++) {
                    float lf = (l + 1) / 4f;
                    float lx = baseX + sway * lf * (float)Math.sin(lf * Math.PI * 1.5f + i * 0.7f);
                    float ly = lerp(baseY, tipY, lf);
                    int dir = (l % 2 == 0) ? -1 : 1;
                    g.fillOval((int)(lx + dir * 3 - 2), (int)(ly - 2), 5, 4);
                }
            }
        }

        // Central constriction pulse around target
        if (t > 0.3f && t < 0.85f) {
            float ct = (t - 0.3f) / 0.55f;
            float pulse = (float)(0.6 + 0.4 * Math.sin(ct * Math.PI * 4));
            int ringR = (int)(20 - ct * 8);
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.4f * pulse));
            g.setColor(new Color(40, 140, 20));
            g.setStroke(new BasicStroke(2f));
            g.drawOval((int)(cx - ringR), (int)(cy - ringR), ringR * 2, ringR * 2);
        }
    }

    private void paintTimeStop(Graphics2D g, float t, int ax, int ay, int aw, int ah,
                                float ex, float ey) {
        if (t < 0.2f) {
            // Phase 1: Clock face appears at target
            float pt = t / 0.2f;
            float alpha = pt * 0.8f;
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            int clockR = (int)(5 + pt * 20);
            // Clock circle
            g.setColor(new Color(180, 200, 255));
            g.setStroke(new BasicStroke(2f));
            g.drawOval((int)(ex - clockR), (int)(ey - clockR), clockR * 2, clockR * 2);
            // Hour marks
            for (int i = 0; i < 12; i++) {
                double angle = i * (Math.PI * 2 / 12) - Math.PI / 2;
                int x1 = (int)(ex + Math.cos(angle) * (clockR * 0.75));
                int y1 = (int)(ey + Math.sin(angle) * (clockR * 0.75));
                int x2 = (int)(ex + Math.cos(angle) * (clockR * 0.9));
                int y2 = (int)(ey + Math.sin(angle) * (clockR * 0.9));
                g.drawLine(x1, y1, x2, y2);
            }
        } else if (t < 0.5f) {
            // Phase 2: Hands spin rapidly, then freeze; blue-white flash
            float pt = (t - 0.2f) / 0.3f;
            int clockR = 25;
            float spinAngle = pt * (float)(Math.PI * 8); // rapid spin
            float alpha = 0.8f;

            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            g.setColor(new Color(180, 200, 255));
            g.setStroke(new BasicStroke(2f));
            g.drawOval((int)(ex - clockR), (int)(ey - clockR), clockR * 2, clockR * 2);
            // Hour marks
            for (int i = 0; i < 12; i++) {
                double angle = i * (Math.PI * 2 / 12) - Math.PI / 2;
                int x1 = (int)(ex + Math.cos(angle) * (clockR * 0.75));
                int y1 = (int)(ey + Math.sin(angle) * (clockR * 0.75));
                int x2 = (int)(ex + Math.cos(angle) * (clockR * 0.9));
                int y2 = (int)(ey + Math.sin(angle) * (clockR * 0.9));
                g.drawLine(x1, y1, x2, y2);
            }
            // Spinning hands
            g.setColor(new Color(220, 230, 255));
            g.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            // Minute hand
            int mx = (int)(ex + Math.cos(spinAngle - Math.PI / 2) * clockR * 0.7);
            int my = (int)(ey + Math.sin(spinAngle - Math.PI / 2) * clockR * 0.7);
            g.drawLine((int)ex, (int)ey, mx, my);
            // Hour hand
            int hx = (int)(ex + Math.cos(spinAngle * 0.3 - Math.PI / 2) * clockR * 0.45);
            int hy = (int)(ey + Math.sin(spinAngle * 0.3 - Math.PI / 2) * clockR * 0.45);
            g.drawLine((int)ex, (int)ey, hx, hy);

            // Blue-white flash at end of spin
            if (pt > 0.8f) {
                float flashAlpha = (pt - 0.8f) / 0.2f * 0.5f;
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, flashAlpha));
                g.setColor(new Color(200, 220, 255));
                g.fillRect(ax, ay, aw, ah);
            }
        } else if (t < 0.7f) {
            // Phase 3: Frozen — desaturated tint over arena + shatter cracks
            float pt = (t - 0.5f) / 0.2f;
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.25f));
            g.setColor(new Color(100, 120, 180));
            g.fillRect(ax, ay, aw, ah);
            // Crack lines radiating from clock center
            java.util.Random rng = new java.util.Random(effectStartMs);
            g.setColor(new Color(200, 220, 255, (int)(200 * (1f - pt * 0.5f))));
            g.setStroke(new BasicStroke(1.5f));
            for (int i = 0; i < 8; i++) {
                double angle = rng.nextDouble() * Math.PI * 2;
                int len = (int)(10 + pt * 40 + rng.nextFloat() * 20);
                int x2 = (int)(ex + Math.cos(angle) * len);
                int y2 = (int)(ey + Math.sin(angle) * len);
                g.drawLine((int)ex, (int)ey, x2, y2);
            }
        } else {
            // Phase 4: Fade out
            float pt = (t - 0.7f) / 0.3f;
            float fadeAlpha = (1f - pt) * 0.2f;
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, fadeAlpha));
            g.setColor(new Color(100, 120, 180));
            g.fillRect(ax, ay, aw, ah);
        }
    }

    private void paintFear(Graphics2D g, float t, int ax, int ay, int aw, int ah,
                            float ex, float ey) {
        java.util.Random rng = new java.util.Random(effectStartMs);

        // Darkening overlay that pulses
        if (t < 0.8f) {
            float darkness = t < 0.3f ? t / 0.3f : 1f;
            float pulse = 0.7f + 0.3f * (float)Math.sin(t * Math.PI * 6);
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, darkness * 0.3f * pulse));
            g.setColor(new Color(10, 0, 20));
            g.fillRect(ax, ay, aw, ah);
        } else {
            float fade = 1f - (t - 0.8f) / 0.2f;
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, fade * 0.3f));
            g.setColor(new Color(10, 0, 20));
            g.fillRect(ax, ay, aw, ah);
        }

        // Shadow wisps converging on target
        for (int i = 0; i < 8; i++) {
            float delay = i * 0.06f;
            float pt = clamp((t - delay) / (0.75f - delay), 0f, 1f);
            if (pt <= 0f) continue;

            // Wisp starts from random edge position, converges on target
            float startX = ex + (rng.nextFloat() - 0.5f) * 120f;
            float startY = ey + (rng.nextFloat() - 0.5f) * 80f;
            float wx = lerp(startX, ex, easeInOut(pt));
            float wy = lerp(startY, ey, easeInOut(pt));
            // Sway
            wx += (float)Math.sin(pt * Math.PI * 3 + i * 1.2f) * 6f;
            wy += (float)Math.cos(pt * Math.PI * 2 + i * 0.9f) * 4f;

            float alpha = pt < 0.7f ? clamp(pt * 2f, 0f, 1f)
                                    : (1f - pt) / 0.3f;
            if (t > 0.75f) alpha *= (1f - (t - 0.75f) / 0.25f);
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, clamp(alpha * 0.7f, 0f, 1f)));

            // Wisp body — elongated along direction of travel
            float dx = ex - wx, dy = ey - wy;
            double angle = Math.atan2(dy, dx);
            AffineTransform old = g.getTransform();
            g.translate(wx, wy);
            g.rotate(angle);
            g.setColor(new Color(40, 10, 60));
            g.fillOval(-8, -3, 16, 6);
            g.setColor(new Color(80, 20, 100, 120));
            g.fillOval(-5, -2, 10, 4);
            g.setTransform(old);
        }

        // Ghostly eyes flash near target at peak
        if (t > 0.35f && t < 0.7f) {
            float et = (t - 0.35f) / 0.35f;
            float eyeAlpha = et < 0.5f ? et * 2f : (1f - et) * 2f;
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, clamp(eyeAlpha * 0.8f, 0f, 1f)));
            g.setColor(new Color(180, 40, 40));
            g.fillOval((int)(ex - 8), (int)(ey - 4), 5, 4);
            g.fillOval((int)(ex + 3), (int)(ey - 4), 5, 4);
            // Tiny white pupils
            g.setColor(new Color(255, 200, 200));
            g.fillOval((int)(ex - 6), (int)(ey - 3), 2, 2);
            g.fillOval((int)(ex + 5), (int)(ey - 3), 2, 2);
        }
    }

    private void paintWish(Graphics2D g, float t, int ax, int ay, int aw, int ah,
                            float cx, float cy) {
        // Prismatic rainbow ripple expanding outward from caster
        java.util.Random rng = new java.util.Random(effectStartMs);

        // Phase 1: Build-up — swirling prismatic motes converge
        if (t < 0.3f) {
            float pt = t / 0.3f;
            for (int i = 0; i < 12; i++) {
                double angle = i * (Math.PI * 2 / 12) + pt * Math.PI;
                float dist = 50 * (1f - pt);
                float mx = cx + (float)Math.cos(angle) * dist;
                float my = cy + (float)Math.sin(angle) * dist;
                float hue = (i / 12f + t * 2f) % 1f;
                Color c = Color.getHSBColor(hue, 0.8f, 1f);
                float alpha = pt * 0.8f;
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
                g.setColor(c);
                g.fillOval((int)(mx - 3), (int)(my - 3), 6, 6);
            }
        }

        // Phase 2: Prismatic rings expand outward
        if (t >= 0.3f && t < 0.8f) {
            float pt = (t - 0.3f) / 0.5f;
            int ringCount = 3;
            for (int r = 0; r < ringCount; r++) {
                float ringDelay = r * 0.15f;
                float rt = clamp((pt - ringDelay) / (1f - ringDelay), 0f, 1f);
                if (rt <= 0f) continue;
                int radius = (int)(rt * 80);
                float hueBase = r * 0.33f + t * 1.5f;
                float alpha = (1f - rt) * 0.7f;
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, clamp(alpha, 0f, 1f)));
                // Draw ring in 24 segments, each a different hue
                int segCount = 24;
                g.setStroke(new BasicStroke(3f - r * 0.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                for (int s = 0; s < segCount; s++) {
                    float hue = (hueBase + s / (float)segCount) % 1f;
                    g.setColor(Color.getHSBColor(hue, 0.85f, 1f));
                    double a1 = s * (Math.PI * 2 / segCount);
                    double a2 = (s + 1) * (Math.PI * 2 / segCount);
                    int x1 = (int)(cx + Math.cos(a1) * radius);
                    int y1 = (int)(cy + Math.sin(a1) * radius);
                    int x2 = (int)(cx + Math.cos(a2) * radius);
                    int y2 = (int)(cy + Math.sin(a2) * radius);
                    g.drawLine(x1, y1, x2, y2);
                }
            }
            // Central white flash
            float flashAlpha = pt < 0.3f ? pt / 0.3f : clamp((1f - pt) * 1.5f, 0f, 1f);
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, flashAlpha * 0.6f));
            g.setColor(new Color(255, 255, 255));
            int flareR = (int)(8 + pt * 12);
            g.fillOval((int)(cx - flareR), (int)(cy - flareR), flareR * 2, flareR * 2);
        }

        // Phase 3: Lingering sparkle fade
        if (t >= 0.7f) {
            float pt = (t - 0.7f) / 0.3f;
            float fadeAlpha = 1f - pt;
            for (int i = 0; i < 16; i++) {
                float hue = (i / 16f + t * 3f) % 1f;
                Color c = Color.getHSBColor(hue, 0.7f, 1f);
                double angle = rng.nextDouble() * Math.PI * 2;
                float dist = 20 + rng.nextFloat() * 60;
                float sx = cx + (float)Math.cos(angle) * dist;
                float sy = cy + (float)Math.sin(angle) * dist - pt * 15;
                float sa = fadeAlpha * (0.3f + rng.nextFloat() * 0.5f);
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, clamp(sa, 0f, 1f)));
                g.setColor(c);
                int arm = 2 + rng.nextInt(2);
                g.setStroke(new BasicStroke(1f));
                g.drawLine((int)sx - arm, (int)sy, (int)sx + arm, (int)sy);
                g.drawLine((int)sx, (int)sy - arm, (int)sx, (int)sy + arm);
            }
        }
    }

    private void paintMeteorSwarm(Graphics2D g, float t, int ax, int ay, int aw, int ah,
                                   float ex, float ey) {
        java.util.Random rng = new java.util.Random(effectStartMs);
        int meteorCount = 5;

        for (int i = 0; i < meteorCount; i++) {
            // Each meteor has a staggered start time
            float delay = i * 0.12f;
            float mt = clamp((t - delay) / (0.55f - delay * 0.5f), 0f, 1f);
            if (mt <= 0f) continue;

            // Start position: scattered across top of arena
            float startX = ex + (i - meteorCount / 2f) * 25f + (rng.nextFloat() - 0.5f) * 20f;
            float startY = ay - 10;
            // End position: clustered around target
            float endMX = ex + (rng.nextFloat() - 0.5f) * 24f;
            float endMY = ey + (rng.nextFloat() - 0.5f) * 16f;

            if (mt < 0.7f) {
                // Travel phase — meteor falls with slight arc
                float pt = easeInOut(mt / 0.7f);
                float bx = lerp(startX, endMX, pt);
                float by = lerp(startY, endMY, pt);

                // Fiery trail
                for (int k = 0; k < 5; k++) {
                    float tt = clamp(mt - 0.03f * (k + 1), 0f, mt);
                    float tpt = easeInOut(clamp(tt / 0.7f, 0f, 1f));
                    float tx = lerp(startX, endMX, tpt);
                    float ty = lerp(startY, endMY, tpt);
                    int tr = 5 - k;
                    int ta = 120 - k * 25;
                    g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0f, ta / 255f)));
                    g.setColor(new Color(200, 60 + k * 15, 10));
                    g.fillOval((int)(tx - tr), (int)(ty - tr), tr * 2, tr * 2);
                }

                // Meteor core — layered glow
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.9f));
                g.setColor(new Color(220, 40, 0, 120));
                g.fillOval((int)(bx - 8), (int)(by - 8), 16, 16);
                g.setColor(new Color(240, 120, 0, 200));
                g.fillOval((int)(bx - 5), (int)(by - 5), 10, 10);
                g.setColor(new Color(255, 240, 80));
                g.fillOval((int)(bx - 3), (int)(by - 3), 6, 6);
            } else {
                // Impact explosion
                float et = (mt - 0.7f) / 0.3f;
                int radius = (int)(5 + et * 22);
                float alpha = 1f - et;
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, clamp(alpha, 0f, 1f)));
                // Outer red burst
                g.setColor(new Color(220, 30, 0));
                g.fillOval((int)(endMX - radius), (int)(endMY - radius), radius * 2, radius * 2);
                // Mid orange
                int r2 = (int)(radius * 0.6f);
                g.setColor(new Color(255, 140, 0));
                g.fillOval((int)(endMX - r2), (int)(endMY - r2), r2 * 2, r2 * 2);
                // Core white-yellow
                int r3 = (int)(radius * 0.25f);
                g.setColor(new Color(255, 240, 80));
                g.fillOval((int)(endMX - r3), (int)(endMY - r3), r3 * 2, r3 * 2);
                // Sparks flying outward
                g.setStroke(new BasicStroke(1.2f));
                g.setColor(new Color(255, 180, 40, (int)(200 * alpha)));
                for (int s = 0; s < 5; s++) {
                    double angle = rng.nextDouble() * Math.PI * 2;
                    int sparkLen = (int)(4 + et * 10);
                    int x1 = (int)(endMX + Math.cos(angle) * radius * 0.5);
                    int y1 = (int)(endMY + Math.sin(angle) * radius * 0.5);
                    int x2 = (int)(endMX + Math.cos(angle) * (radius * 0.5 + sparkLen));
                    int y2 = (int)(endMY + Math.sin(angle) * (radius * 0.5 + sparkLen));
                    g.drawLine(x1, y1, x2, y2);
                }
            }
        }

        // Screen shake simulation — slight red tint overlay during impacts
        if (t > 0.3f && t < 0.85f) {
            float shakeT = (t - 0.3f) / 0.55f;
            float intensity = (float)(0.08 * Math.sin(shakeT * Math.PI * 10) * (1f - shakeT));
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.abs(intensity)));
            g.setColor(new Color(180, 40, 0));
            g.fillRect(ax, ay, aw, ah);
        }
    }

    private void paintGift(Graphics2D g, float t, float cx, float cy) {
        java.util.Random rng = new java.util.Random(effectStartMs);

        // Warm amber glow radiating outward
        float glowT = t < 0.6f ? t / 0.6f : (1f - t) / 0.4f;
        int glowR = (int)(10 + glowT * 30);
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, glowT * 0.3f));
        g.setColor(new Color(255, 180, 40));
        g.fillOval((int)(cx - glowR), (int)(cy - glowR), glowR * 2, glowR * 2);

        // 10 golden star-sparkles rising and spreading
        for (int i = 0; i < 10; i++) {
            float delay = i * 0.06f;
            float pt = clamp((t - delay) / (1f - delay), 0f, 1f);
            if (pt <= 0f) continue;

            float angle = rng.nextFloat() * (float)(Math.PI * 2);
            float spread = 15 + rng.nextFloat() * 25f;
            float px = cx + (float)Math.cos(angle) * spread * pt;
            float py = cy + (float)Math.sin(angle) * spread * pt - pt * 25f; // drift upward

            float sparkleAlpha = pt < 0.3f ? pt / 0.3f : (1f - pt) / 0.7f;
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, clamp(sparkleAlpha * 0.9f, 0f, 1f)));

            // 4-pointed star
            int arm = 3 + (int)(rng.nextFloat() * 3);
            g.setColor(new Color(255, 220, 60));
            g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawLine((int)px - arm, (int)py, (int)px + arm, (int)py);
            g.drawLine((int)px, (int)py - arm, (int)px, (int)py + arm);
            // Diagonal arms (smaller)
            int dArm = arm / 2;
            g.setColor(new Color(255, 240, 140));
            g.drawLine((int)px - dArm, (int)py - dArm, (int)px + dArm, (int)py + dArm);
            g.drawLine((int)px + dArm, (int)py - dArm, (int)px - dArm, (int)py + dArm);
        }

        // Central diamond rotating
        float diamondT = t < 0.7f ? t / 0.7f : 1f;
        float diamondAlpha = t < 0.7f ? 1f : (1f - (t - 0.7f) / 0.3f);
        double rot = t * Math.PI * 2;
        int dSize = (int)(4 + diamondT * 6);
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, clamp(diamondAlpha * 0.85f, 0f, 1f)));
        AffineTransform old = g.getTransform();
        g.translate(cx, cy);
        g.rotate(rot);
        // Diamond shape
        int[] xPts = {0, dSize, 0, -dSize};
        int[] yPts = {-dSize, 0, dSize, 0};
        g.setColor(new Color(255, 200, 40));
        g.fillPolygon(xPts, yPts, 4);
        g.setColor(new Color(255, 255, 180));
        g.drawPolygon(xPts, yPts, 4);
        g.setTransform(old);
    }
}

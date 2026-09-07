package io.cannonforge.retroquest.model;

/**
 * Represents an active effect on the player, such as protection, regeneration, or healing.
 *
 * <p>Effects can be permanent (turnsRemaining == -1) or temporary. Permanent effects
 * last until the item providing them is unequipped. Temporary effects are ticked each
 * move and removed when {@code turnsRemaining} reaches zero.
 */
public class Effect {

    public enum EffectType {
        PROTECTION,
        REGENERATION,
        HEALING,
        SPELL_RESIST,
        DAMAGE
    }

    private final EffectType type;
    private final int value;

    private int turnsRemaining = -1;

    // Regeneration-specific counter (each ring has its own)
    private int moveCounter = 0;

    // Gson no-arg constructor
    public Effect() {
        this.type = EffectType.HEALING;
        this.value = 0;
    }

    public Effect(EffectType type, int value) {
        this.type = type;
        this.value = value;
        this.turnsRemaining = (type == EffectType.HEALING) ? 0 : -1;
    }

    public EffectType getType() { return type; }
    public int getValue() { return value; }

    public String getName() {
        return switch (type) {
            case PROTECTION   -> "Protection +" + value;
            case REGENERATION -> "Regeneration +" + value;
            case HEALING      -> "Healing";
            case SPELL_RESIST -> "Spell Resist +" + value + "%";
            case DAMAGE       -> "Damage +" + value;
        };
    }

    public boolean isPermanent() {
        return turnsRemaining < 0;
    }

    public void onApply(Player player) {
        if (type == EffectType.HEALING) {
            player.heal(value);
        }
    }

    public int getTurnsRemaining() {
    	return turnsRemaining;
    }

    public void onMove(Player player) {
        if (type == EffectType.REGENERATION) {
            moveCounter++;
            if (moveCounter >= 4) {           // heal every 4 moves
                player.heal(value);
                moveCounter = 0;
            }
        }
    }

    public void tick(Player player) {
        if (turnsRemaining > 0) {
            turnsRemaining--;
        }
    }

    public int modifyDamage() {
        return (type == EffectType.DAMAGE) ? value : 0;
    }

    public int modifyAC(Player player) {
        return (type == EffectType.PROTECTION) ? value : 0;
    }

    public int modifyRegeneration(Player player) {
        return (type == EffectType.REGENERATION) ? value : 0;
    }

    public int modifySpellResist() {
        return (type == EffectType.SPELL_RESIST) ? value : 0;
    }
}

package Attributes;

/**
 * Derived combat stats. Attributes feed them through the formulas in {@link AttributeRules};
 * a {@link Contribution} can also add to one directly (for example a future "+5% crit chance"
 * gear affix, or Weapon Mastery's lifesteal). All values are fractions (0.10 = +10%), except
 * {@link #CRIT_MULTIPLIER}, which adds to the multiplier itself.
 *
 * <p>The last five are the speed and handling stats (docs/design/ATTRIBUTE_EXPANSION.md): raised
 * by a primary attribute plus the skill that owns the action, then capped with diminishing
 * returns ({@link AttributeRules.Secondary}).
 */
public enum Stat {
    DAMAGE("Damage"),
    CRIT_CHANCE("Crit chance"),
    CRIT_MULTIPLIER("Crit damage"),
    LIFESTEAL("Lifesteal"),
    MAX_HP("Max health"),
    ARMOR("Armor"),
    DEFENSE_BAR("Defense bar"),
    RESIST("Resistances"),
    MAX_MANA("Max mana"),
    MANA_REGEN("Mana regeneration"),
    SPELL_DAMAGE("Spell damage"),
    ATTACK_SPEED("Attack speed"),
    DRAW_SPEED("Bow draw speed"),
    MOVE_SPEED("Movement speed"),
    COOLDOWN_RECOVERY("Spell cooldown recovery"),
    GATHER_SPEED("Gathering speed");

    /** The speed and handling stats, in display order. */
    public static final java.util.List<Stat> SECONDARY = java.util.List.of(ATTACK_SPEED, DRAW_SPEED, MOVE_SPEED, COOLDOWN_RECOVERY, GATHER_SPEED);

    public boolean isSecondary() { return SECONDARY.contains(this); }

    /** "attack-speed" / "attack_speed" / "ATTACK_SPEED" -> ATTACK_SPEED; null when unknown. */
    public static Stat parse(String s) {
        if (s == null || s.isBlank()) return null;
        String key = s.trim().toUpperCase(java.util.Locale.ROOT).replace('-', '_').replace(' ', '_');
        if (key.equals("MOVEMENT_SPEED")) key = "MOVE_SPEED";
        try {
            return valueOf(key);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public final String displayName;

    Stat(String displayName) {
        this.displayName = displayName;
    }
}

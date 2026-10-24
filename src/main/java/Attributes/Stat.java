package Attributes;

/**
 * Derived combat stats. Attributes feed them through the formulas in {@link AttributeRules};
 * a {@link Contribution} can also add to one directly (for example a future "+5% crit chance"
 * gear affix, or Weapon Mastery's lifesteal). All values are fractions (0.10 = +10%), except
 * {@link #CRIT_MULTIPLIER}, which adds to the multiplier itself.
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
    SPELL_DAMAGE("Spell damage");

    public final String displayName;

    Stat(String displayName) {
        this.displayName = displayName;
    }
}

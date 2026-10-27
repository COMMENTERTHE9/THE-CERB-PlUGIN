package Attributes;

/**
 * Derived combat stats. Attributes feed them through the formulas in {@link AttributeRules};
 * a {@link Contribution} can also add to one directly (for example a future "+5% crit chance"
 * gear affix, or Weapon Mastery's lifesteal). All values are fractions (0.10 = +10%), except
 * {@link #CRIT_MULTIPLIER}, which adds to the multiplier itself.
 *
 * <p>The last seven are the speed and handling stats (docs/design/ATTRIBUTE_EXPANSION.md): raised
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
    GATHER_SPEED("Gathering speed"),
    /** Gathering speed with an axe in hand (Woodcutting's); every other item uses {@link #GATHER_SPEED} (Mining's). */
    CHOP_SPEED("Chopping speed"),
    /** The chance each point of wear on a tool, weapon or piece of armour is avoided (Smithing's: a smith keeps gear in shape). */
    WEAR_RESISTANCE("Wear resistance"),
    /** How much sooner fish bite, with a rod in hand (Fishing's; docs/design/FISHING.md §6). */
    LURE_SPEED("Lure speed"),
    /** The chance a catch in open water is Cerberus treasure, from Treasure Sense (Fishing's). */
    TREASURE_CHANCE("Treasure chance"),
    /** The chance a catch banks a sea creature's lure (Fishing's). */
    SEA_CREATURE_CHANCE("Sea-creature chance");

    /** The speed and handling stats, in display order. */
    public static final java.util.List<Stat> SECONDARY = java.util.List.of(ATTACK_SPEED, DRAW_SPEED, MOVE_SPEED, COOLDOWN_RECOVERY, GATHER_SPEED, CHOP_SPEED,
            WEAR_RESISTANCE, LURE_SPEED, TREASURE_CHANCE, SEA_CREATURE_CHANCE);

    /** The chances among them: a value of 1 would be every time, so they are kept well below it. */
    public boolean isChance() { return this == WEAR_RESISTANCE || this == TREASURE_CHANCE || this == SEA_CREATURE_CHANCE; }

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

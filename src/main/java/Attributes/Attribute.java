package Attributes;

import org.bukkit.ChatColor;

/**
 * The six character attributes (docs/design/SYSTEMS_BASE_LAYER.md §4). Values are derived from
 * {@link Contribution}s (skills, realm, later gear and buffs); nothing stores them directly.
 */
public enum Attribute {
    VITALITY("Vitality", ChatColor.RED, "Max health"),
    ESSENCE("Essence", ChatColor.AQUA, "Max mana, mana regeneration, spell damage"),
    POWER("Power", ChatColor.GOLD, "Weapon damage (per weapon type)"),
    FORTITUDE("Fortitude", ChatColor.GRAY, "Armor, defense bar, fire/cold/drowning resistance"),
    AGILITY("Agility", ChatColor.YELLOW, "Critical hit chance and critical damage"),
    FORTUNE("Fortune", ChatColor.GREEN, "A chance of extra ore when mining (later luck and loot)");

    public final String displayName;
    public final ChatColor color;
    /** One line: what this attribute drives. */
    public final String drives;

    Attribute(String displayName, ChatColor color, String drives) {
        this.displayName = displayName;
        this.color = color;
        this.drives = drives;
    }

    /** Parse a config name ("power", "Power", "POWER"); null when unknown. */
    public static Attribute parse(String s) {
        if (s == null) return null;
        try {
            return valueOf(s.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}

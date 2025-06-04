package CustomTags;

import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;

public class ElementalTag {
    public enum ElementType {
        FIRE(ChatColor.RED),
        ICE(ChatColor.AQUA),
        LIGHTNING(ChatColor.YELLOW),
        ARCANE(ChatColor.DARK_PURPLE),
        DARK(ChatColor.DARK_GRAY),
        EARTH(ChatColor.GREEN),
        WIND(ChatColor.GRAY),
        BINDING(ChatColor.BLUE);

        private final ChatColor color;

        ElementType(ChatColor color) {
            this.color = color;
        }

        public ChatColor getColor() {
            return color;
        }
    }

    private final ElementType elementType;
    private final double damageMultiplier;
    private final double resistance;
    private final double specialEffect;

    public ElementalTag(ElementType elementType, double damageMultiplier, double resistance, double specialEffect) {
        this.elementType = elementType;
        this.damageMultiplier = damageMultiplier;
        this.resistance = resistance;
        this.specialEffect = specialEffect;
    }

    public ElementType getElementType() {
        return elementType;
    }

    public double getDamageMultiplier() {
        return damageMultiplier;
    }

    public double getResistance() {
        return resistance;
    }

    public double getSpecialEffect() {
        return specialEffect;
    }

    @Override
    public String toString() {
        return elementType.getColor() + elementType.name()
                + " [Damage Multiplier: " + damageMultiplier
                + ", Resistance: " + resistance
                + ", Effect: " + specialEffect + "]";
    }

    /**
     * Parse a stored element tag string back into an ElementalTag
     */
    public static ElementalTag fromString(String elementTypeName) {
        if (elementTypeName == null) return null;
        switch (elementTypeName.toUpperCase()) {
            case "FIRE":      return new ElementalTag(ElementType.FIRE, 1.2, 0.1, 0.5);
            case "ICE":       return new ElementalTag(ElementType.ICE, 1.1, 0.2, 0.4);
            case "LIGHTNING": return new ElementalTag(ElementType.LIGHTNING, 1.3, 0.0, 0.6);
            case "ARCANE":    return new ElementalTag(ElementType.ARCANE, 1.4, 0.0, 0.7);
            case "DARK":      return new ElementalTag(ElementType.DARK, 1.5, 0.0, 0.8);
            case "EARTH":     return new ElementalTag(ElementType.EARTH, 1.1, 0.3, 0.4);
            case "WIND":      return new ElementalTag(ElementType.WIND, 1.2, 0.0, 0.5);
            case "BINDING":   return new ElementalTag(ElementType.BINDING, 1.0, 0.0, 0.3);
            default:           return null;
        }
    }

    /**
     * Get the skill name associated with this elemental tag
     */
    public String getSkillName() {
        switch (elementType) {
            case FIRE:      return "Pyromancy";
            case ICE:       return "Cryomancy";
            case LIGHTNING: return "Electromancy";
            case ARCANE:    return "Arcane Knowledge";
            case DARK:      return "Dark Arts";
            case EARTH:     return "Geomancy";
            case WIND:      return "Aeromancy";
            case BINDING:   return "Binding Mastery";
            default:        return "";
        }
    }
}

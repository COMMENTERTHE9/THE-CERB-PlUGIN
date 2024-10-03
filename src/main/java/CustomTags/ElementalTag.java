package CustomTags;

import org.bukkit.ChatColor;

public class ElementalTag {

    public enum ElementType {
        FIRE(ChatColor.RED),
        ICE(ChatColor.AQUA),
        LIGHTNING(ChatColor.YELLOW),
        ARCANE(ChatColor.DARK_PURPLE),
        DARK(ChatColor.DARK_GRAY),
        EARTH(ChatColor.GREEN),
        WIND(ChatColor.GRAY),
        BINDING(ChatColor.BLUE);  // Added Binding element

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
    private final double specialEffect; // For special effects like burning, slowing, etc.

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
        return elementType.getColor() + elementType.name() + " [Damage Multiplier: " + damageMultiplier + ", Resistance: " + resistance + ", Effect: " + specialEffect + "]";
    }
}

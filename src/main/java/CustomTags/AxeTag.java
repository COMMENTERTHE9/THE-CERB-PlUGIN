package CustomTags;

import org.bukkit.ChatColor;

public class AxeTag {

    public enum AxeType {
        BASIC(ChatColor.WHITE, 1.0, 1.0),
        ENCHANTED(ChatColor.LIGHT_PURPLE, 1.2, 1.1),
        LEGENDARY(ChatColor.GOLD, 1.5, 1.3),
        MYTHICAL(ChatColor.DARK_PURPLE, 2.0, 1.5),
        CUSTOM(ChatColor.AQUA, 1.0, 1.0); // Custom tag that can be adjusted dynamically

        private final ChatColor color;
        private final double speedMultiplier;
        private final double durabilityMultiplier;

        AxeType(ChatColor color, double speedMultiplier, double durabilityMultiplier) {
            this.color = color;
            this.speedMultiplier = speedMultiplier;
            this.durabilityMultiplier = durabilityMultiplier;
        }

        public ChatColor getColor() {
            return color;
        }

        public double getSpeedMultiplier() {
            return speedMultiplier;
        }

        public double getDurabilityMultiplier() {
            return durabilityMultiplier;
        }
    }

    private final AxeType axeType;
    private final String customName;
    private final String specialEffect; // Description of the special effect, if any

    public AxeTag(AxeType axeType, String customName, String specialEffect) {
        this.axeType = axeType;
        this.customName = customName;
        this.specialEffect = specialEffect;
    }

    public AxeType getAxeType() {
        return axeType;
    }

    public String getCustomName() {
        return customName;
    }

    public String getSpecialEffect() {
        return specialEffect;
    }

    @Override
    public String toString() {
        return axeType.getColor() + customName + " [Speed: " + axeType.getSpeedMultiplier() + ", Durability: " + axeType.getDurabilityMultiplier() + ", Effect: " + specialEffect + "]";
    }
}

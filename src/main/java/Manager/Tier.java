package Manager;

import org.bukkit.ChatColor;

/**
 * Unified tier system used across all Cerberus systems:
 * items (crafting quality), mobs (classification), regions, loot, and combat (armor penetration).
 *
 * <p>Order matters — ordinal() is used for {@link #isAtLeast(Tier)} comparisons.
 */
public enum Tier {
    WEAK      (0.00, 0.5,  0.5,  ChatColor.GRAY,        "#7f7f7f", "Weak"),
    COMMON    (0.00, 1.0,  1.0,  ChatColor.WHITE,       "#ffffff", "Common"),
    UNCOMMON  (0.10, 1.5,  1.25, ChatColor.GREEN,       "#00ff00", "Uncommon"),
    RARE      (0.20, 2.0,  1.5,  ChatColor.BLUE,        "#0000ff", "Rare"),
    EPIC      (0.30, 3.0,  1.75, ChatColor.DARK_PURPLE, "#9400d3", "Epic"),
    LEGENDARY (0.40, 4.0,  2.0,  ChatColor.GOLD,        "#ffd700", "Legendary"),
    PERFECT   (0.50, 5.0,  2.5,  ChatColor.YELLOW,      "#ffaa00", "Perfect"),
    MYTHIC    (0.60, 7.0,  3.0,  ChatColor.RED,         "#ff2222", "Mythic"),
    GODLY     (0.70, 10.0, 3.5,  ChatColor.DARK_RED,    "#ff4500", "Godly"),
    PRIMORDIAL(0.80, 15.0, 4.0,  ChatColor.LIGHT_PURPLE,"#8a2be2", "Primordial");

    /** Fraction of the target's armor rating this tier ignores (0.0–1.0). */
    public final double armorPenetration;
    /** XP multiplier for kills of this tier. */
    public final double xpMultiplier;
    /** Item stat multiplier when crafting at this quality. */
    public final double craftStatMultiplier;
    /** Chat color for display names. */
    public final ChatColor displayColor;
    /** Hex color string for item lore / TextComponent. */
    public final String hexColor;
    /** Human-readable name. */
    public final String displayName;

    Tier(double armorPenetration, double xpMultiplier, double craftStatMultiplier,
         ChatColor displayColor, String hexColor, String displayName) {
        this.armorPenetration = armorPenetration;
        this.xpMultiplier = xpMultiplier;
        this.craftStatMultiplier = craftStatMultiplier;
        this.displayColor = displayColor;
        this.hexColor = hexColor;
        this.displayName = displayName;
    }

    /** True if this tier is equal to or higher than {@code other}. */
    public boolean isAtLeast(Tier other) {
        return this.ordinal() >= other.ordinal();
    }

    /** Parse a tier name from config/YML (case-insensitive). Returns COMMON if unrecognized. */
    public static Tier fromString(String name) {
        if (name == null || name.isBlank()) return COMMON;
        try {
            return valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return COMMON;
        }
    }

    /**
     * Map old CraftingManager quality names to the unified tier.
     * Handles the pre-unification 8-tier quality strings.
     */
    public static Tier fromLegacyQuality(String quality) {
        if (quality == null || quality.isBlank()) return COMMON;
        return switch (quality.trim().toLowerCase()) {
            case "weak", "poor" -> WEAK;
            case "common" -> COMMON;
            case "uncommon" -> UNCOMMON;
            case "rare" -> RARE;
            case "epic" -> EPIC;
            case "perfect" -> PERFECT;
            case "godly" -> GODLY;
            case "primordial" -> PRIMORDIAL;
            default -> fromString(quality);
        };
    }

    /**
     * Map old MobClassification names to the unified tier.
     * NORMAL→COMMON, RARE→RARE, BOSS→EPIC.
     */
    public static Tier fromLegacyClassification(String classification) {
        if (classification == null || classification.isBlank()) return COMMON;
        return switch (classification.trim().toUpperCase()) {
            case "NORMAL" -> COMMON;
            case "RARE" -> RARE;
            case "BOSS" -> EPIC;
            default -> fromString(classification);
        };
    }
}

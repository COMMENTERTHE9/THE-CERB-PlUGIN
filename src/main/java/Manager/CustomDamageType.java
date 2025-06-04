package Manager;

import net.kyori.adventure.text.format.NamedTextColor;

public enum CustomDamageType {
    // Original vanilla damage types
    ENTITY_ATTACK(true),
    ENTITY_SWEEP_ATTACK(true),
    PROJECTILE(true),
    ENTITY_EXPLOSION(true),
    BLOCK_EXPLOSION(true),
    THORNS(true),
    SONIC_BOOM(true),
    FALL(false),
    FIRE(false),
    FIRE_TICK(false),
    LAVA(false),
    DROWNING(false),
    SUFFOCATION(false),
    STARVATION(false),
    POISON(false),
    MAGIC(false),
    WITHER(false),
    FALLING_BLOCK(false),
    LIGHTNING(false),
    HOT_FLOOR(false),
    CRAMMING(false),
    DRAGON_BREATH(false),
    DRYOUT(false),
    FREEZE(false),
    VOID(false),
    FLY_INTO_WALL(false),
    WORLD_BORDER(false),
    CONTACT(false),
    MELTING(false),
    CAMPFIRE(false),
    SUICIDE(false),

    // Original custom types
    CUSTOM_MAGIC(false),
    CUSTOM_ENVIRONMENTAL(false),
    CUSTOM_EXPLOSION(true),
    OTHER(false),

    // New Elemental damage types
    ARCANE_BLAST(true),
    PYROMANCY(true),
    CRYOMANCY(false),
    ELECTROMANCY(true),
    DARK_ARTS(true),
    MYSTIC_BINDING(false),
    GEOMANCY(true),
    AEROMANCY(false),

    // Mana-related damage types
    MANA_BURN(false),
    MANA_DRAIN(false),
    MANA_FEEDBACK(true),
    MANA_EXPLOSION(true),
    MANA_VOID(false),
    MANA_CORRUPTION(false),

    // Status effect damage
    BURN_DOT(false),
    FREEZE_DOT(false),
    POISON_DOT(false),
    BLEED_DOT(false),

    // Trap damage types
    PHYSICAL_TRAP(true),
    MAGICAL_TRAP(false),
    ELEMENTAL_TRAP(true);

    private final boolean shouldApplyKnockback;

    CustomDamageType(boolean shouldApplyKnockback) {
        this.shouldApplyKnockback = shouldApplyKnockback;
    }

    public boolean shouldApplyKnockback() {
        return shouldApplyKnockback;
    }

    public NamedTextColor getColor() {
        return switch (this) {
            case ARCANE_BLAST -> NamedTextColor.DARK_PURPLE;
            case PYROMANCY, BURN_DOT -> NamedTextColor.GOLD;
            case CRYOMANCY, FREEZE_DOT -> NamedTextColor.AQUA;
            case ELECTROMANCY -> NamedTextColor.YELLOW;
            case DARK_ARTS -> NamedTextColor.DARK_GRAY;
            case MYSTIC_BINDING -> NamedTextColor.LIGHT_PURPLE;
            case GEOMANCY -> NamedTextColor.DARK_GREEN;
            case AEROMANCY -> NamedTextColor.WHITE;
            case MANA_BURN, MANA_DRAIN -> NamedTextColor.BLUE;
            case MANA_FEEDBACK -> NamedTextColor.DARK_BLUE;
            case MANA_EXPLOSION -> NamedTextColor.LIGHT_PURPLE;
            case MANA_VOID -> NamedTextColor.BLACK;
            case MANA_CORRUPTION -> NamedTextColor.DARK_PURPLE;
            case BLEED_DOT -> NamedTextColor.RED;
            case POISON_DOT -> NamedTextColor.GREEN;
            case PHYSICAL_TRAP -> NamedTextColor.RED;
            case MAGICAL_TRAP -> NamedTextColor.LIGHT_PURPLE;
            case ELEMENTAL_TRAP -> NamedTextColor.YELLOW;
            default -> NamedTextColor.GRAY;
        };
    }

    public String getSymbol() {
        return switch (this) {
            case ARCANE_BLAST -> "✯";
            case PYROMANCY, BURN_DOT -> "🔥";
            case CRYOMANCY, FREEZE_DOT -> "❄";
            case ELECTROMANCY -> "⚡";
            case DARK_ARTS -> "☠";
            case MYSTIC_BINDING -> "⛓";
            case GEOMANCY -> "◊";
            case AEROMANCY -> "๑";
            case MANA_BURN -> "✧";
            case MANA_DRAIN -> "⚡";
            case MANA_FEEDBACK -> "↺";
            case MANA_EXPLOSION -> "✸";
            case MANA_VOID -> "⬝";
            case MANA_CORRUPTION -> "❈";
            case BLEED_DOT -> "†";
            case POISON_DOT -> "☠";
            case PHYSICAL_TRAP, MAGICAL_TRAP, ELEMENTAL_TRAP -> "◈";
            default -> "";
        };
    }
}
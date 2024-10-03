package Manager;

public enum CustomDamageType {
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

    CUSTOM_MAGIC(false),        // Custom magic damage, for use with non-vanilla spells or effects
    CUSTOM_ENVIRONMENTAL(false),// Custom environmental hazards not covered by vanilla damage
    CUSTOM_EXPLOSION(true),     // Custom explosion damage
    OTHER(false);               // Any other damage not categorized, as a catch-all

    private final boolean shouldApplyKnockback;

    CustomDamageType(boolean shouldApplyKnockback) {
        this.shouldApplyKnockback = shouldApplyKnockback;
    }

    public boolean shouldApplyKnockback() {
        return shouldApplyKnockback;
    }
}

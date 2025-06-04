package Manager;

import org.bukkit.event.entity.EntityDamageEvent;

public class DamageTypeMapper {

    public static CustomDamageType mapToCustomDamageType(EntityDamageEvent.DamageCause cause) {
        switch (cause) {
            case ENTITY_ATTACK:
                return CustomDamageType.ENTITY_ATTACK;
            case ENTITY_SWEEP_ATTACK:
                return CustomDamageType.ENTITY_SWEEP_ATTACK;
            case PROJECTILE:
                return CustomDamageType.PROJECTILE;
            case ENTITY_EXPLOSION:
                return CustomDamageType.ENTITY_EXPLOSION;
            case BLOCK_EXPLOSION:
                return CustomDamageType.BLOCK_EXPLOSION;
            case THORNS:
                return CustomDamageType.THORNS;
            case SONIC_BOOM:
                return CustomDamageType.SONIC_BOOM;
            case FALL:
                return CustomDamageType.FALL;
            case FIRE:
                return CustomDamageType.FIRE;
            case FIRE_TICK:
                return CustomDamageType.FIRE_TICK;
            case LAVA:
                return CustomDamageType.LAVA;
            case DROWNING:
                return CustomDamageType.DROWNING;
            case SUFFOCATION:
                return CustomDamageType.SUFFOCATION;
            case STARVATION:
                return CustomDamageType.STARVATION;
            case POISON:
                return CustomDamageType.POISON;
            case MAGIC:
                return CustomDamageType.MAGIC;
            case WITHER:
                return CustomDamageType.WITHER;
            case FALLING_BLOCK:
                return CustomDamageType.FALLING_BLOCK;
            case LIGHTNING:
                return CustomDamageType.LIGHTNING;
            case HOT_FLOOR:
                return CustomDamageType.HOT_FLOOR;
            case CRAMMING:
                return CustomDamageType.CRAMMING;
            case DRAGON_BREATH:
                return CustomDamageType.DRAGON_BREATH;
            case DRYOUT:
                return CustomDamageType.DRYOUT;
            case FREEZE:
                return CustomDamageType.FREEZE;
            case VOID:
                return CustomDamageType.VOID;
            case FLY_INTO_WALL:
                return CustomDamageType.FLY_INTO_WALL;
            case WORLD_BORDER:
                return CustomDamageType.WORLD_BORDER;
            case CONTACT:
                return CustomDamageType.CONTACT;
            case MELTING:
                return CustomDamageType.MELTING;
            case CAMPFIRE:
                return CustomDamageType.CAMPFIRE;
            case SUICIDE:
                return CustomDamageType.SUICIDE;
            default:
                return CustomDamageType.OTHER;
        }
    }

    // Method to map your custom damage types manually
    public static CustomDamageType mapCustomDamageType(String customCause) {
        switch (customCause.toUpperCase()) {
            case "CUSTOM_MAGIC":
                return CustomDamageType.CUSTOM_MAGIC;
            case "CUSTOM_ENVIRONMENTAL":
                return CustomDamageType.CUSTOM_ENVIRONMENTAL;
            case "CUSTOM_EXPLOSION":
                return CustomDamageType.CUSTOM_EXPLOSION;
            default:
                return CustomDamageType.OTHER;
        }
    }
}

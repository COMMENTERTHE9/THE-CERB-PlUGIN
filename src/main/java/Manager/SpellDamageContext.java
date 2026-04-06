package Manager;

import javax.annotation.Nullable;

/**
 * Context registered by spells before dealing damage, so the damage pipeline
 * can identify the spell's damage type, knockback behaviour, and element.
 * <p>
 * Used via {@code Map<Integer, SpellDamageContext> pendingSpellDamage} in
 * VirtualHealthListener. Entity ID keys are cleaned immediately after reading
 * (entity IDs get reused).
 */
public record SpellDamageContext(
        CustomDamageType damageType,
        double knockbackMultiplier,     // 0 = no KB, 2.0 = extra KB
        @Nullable String elementalType,
        boolean bypassesArmor
) {}

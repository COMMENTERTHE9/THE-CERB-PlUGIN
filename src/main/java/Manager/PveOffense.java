package Manager;

import Attributes.AttributeService;
import Attributes.AttributeSnapshot;
import Attributes.WeaponClass;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Player → mob hits: the attacker's attributes scale the hit BEFORE it drains the mob's HP pool
 * (Power for the weapon class, an Agility crit roll, and the offense budget), and lifesteal is
 * worked out from the damage actually dealt. PvP does not come through here.
 *
 * <p>Reads only the attacker's cached {@link AttributeSnapshot} (thread-safe), so it works for
 * arrows whose shooter is in another region too.
 */
public final class PveOffense {

    private final AttributeService attributes;

    public PveOffense(AttributeService attributes) {
        this.attributes = attributes;
    }

    /** True when attributes.yml has PvE offense switched on. */
    public boolean enabled() {
        return attributes.rules().switches().pveOffense();
    }

    /** True when attributes.yml has the Essence integration (spell damage, mana) switched on. */
    public boolean spellsEnabled() {
        return attributes.rules().switches().essence();
    }

    /** A player's spell hitting a mob: Essence's spell-damage multiplier. */
    public double spellDamage(Player caster, double raw) {
        double d = raw * attributes.get(caster.getUniqueId()).spellDamageMultiplier();
        return Double.isFinite(d) && d >= 0 ? d : raw;
    }

    /**
     * Build the hit: modified damage, crit flag and multiplier. The readable trace is only built
     * when {@code trace} is set (the attacker has /cerb combatdebug on): no string work per hit otherwise.
     */
    public CombatContext hit(Player attacker, LivingEntity mob, CustomDamageType type, double raw, WeaponClass weapon,
                             boolean trace) {
        AttributeSnapshot s = attributes.get(attacker.getUniqueId());
        boolean crit = s.critChance() > 0 && ThreadLocalRandom.current().nextDouble() < s.critChance();
        double damage = raw * s.offenseMultiplier(weapon, crit);
        if (!Double.isFinite(damage) || damage < 0) damage = raw;

        CombatContext ctx = new CombatContext(attacker, mob, type, raw);
        ctx.attack().setModifiedDamage(damage);
        ctx.attack().setCritical(crit);
        ctx.attack().setCritMultiplier(crit ? s.critMultiplier() : 1.0);
        if (!trace) return ctx;
        double afterPower = raw * s.powerMultiplier(weapon);
        ctx.appendTrace("Power", raw, afterPower, String.format(Locale.ROOT, "%s, Power %.1f -> x%.2f",
                weapon.displayName, s.power(weapon), s.powerMultiplier(weapon)));
        if (damage != afterPower) {
            ctx.appendTrace(crit ? "Crit" : "Bonus", afterPower, damage, crit
                    ? String.format(Locale.ROOT, "x%.2f (%.1f%% chance)", s.critMultiplier(), s.critChance() * 100)
                    : null);
        }
        return ctx;
    }

    /** After the pool took the hit: record what was dealt and the lifesteal it earns. */
    public void dealt(CombatContext ctx, double dealt, boolean trace) {
        ctx.defense().setFinalDamage(dealt);
        Player attacker = ctx.getAttacker();
        if (attacker == null || dealt <= 0) return;
        double lifesteal = attributes.get(attacker.getUniqueId()).lifesteal();
        if (lifesteal > 0) {
            ctx.postDamage().setLifestealAmount(dealt * lifesteal);
            if (trace) ctx.appendTrace("Lifesteal", 0, dealt * lifesteal, String.format(Locale.ROOT, "%.1f%%", lifesteal * 100));
        }
    }

    /** Weapon class of a melee hit: the attacker's main hand (call on a thread that owns the attacker). */
    public static WeaponClass meleeWeapon(Player attacker) {
        return WeaponClass.ofMaterial(attacker.getInventory().getItemInMainHand().getType().name());
    }
}

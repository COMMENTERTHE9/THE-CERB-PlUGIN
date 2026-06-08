package Skills;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;

public class CriticalStrikeSkill extends CombatSkill {

    public CriticalStrikeSkill(String name) {
        super(name);
    }

    @Override
    public void applyEffect(Player player) {
        if (player == null) return;

        // Example: Increase critical hit chance and damage for this skill level
        double criticalHitChance = getCriticalHitChance(player);
        double criticalHitDamageMultiplier = getCriticalHitDamageMultiplier(player);

        // Add your custom logic here to apply the critical hit chance and damage multiplier
        // This could be tied into your custom combat system or use Bukkit events for critical hits

        // Example: Applying a multiplier to attack damage for critical strikes
        AttributeInstance attr = player.getAttribute(Attribute.ATTACK_DAMAGE);
        if (attr != null) {
            double baseValue = attr.getBaseValue();
            attr.setBaseValue(baseValue * criticalHitDamageMultiplier);
        }
    }

    /** Public getter for CombatSkillProcessor — crit chance based on skill level. */
    public double getCriticalHitChance() {
        return 0.1 + (getLevel() * 0.01);
    }

    /** Public getter for CombatSkillProcessor — crit damage multiplier based on skill level. */
    public double getCriticalHitDamageMultiplier() {
        return 1.5 + (getLevel() * 0.05);
    }

    private double getCriticalHitChance(Player player) {
        return getCriticalHitChance();
    }

    private double getCriticalHitDamageMultiplier(Player player) {
        return getCriticalHitDamageMultiplier();
    }
}

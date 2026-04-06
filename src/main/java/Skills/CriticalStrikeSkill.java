package Skills;

import cerberus.world.cerb.CustomPlayer;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;

public class CriticalStrikeSkill extends CombatSkill {

    public CriticalStrikeSkill(String name) {
        super(name);
    }

    @Override
    public void applyEffect(CustomPlayer player) {
        if (player == null) return;

        // Example: Increase critical hit chance and damage for this skill level
        double criticalHitChance = getCriticalHitChance(player);
        double criticalHitDamageMultiplier = getCriticalHitDamageMultiplier(player);

        // Add your custom logic here to apply the critical hit chance and damage multiplier
        // This could be tied into your custom combat system or use Bukkit events for critical hits

        // Example: Applying a multiplier to attack damage for critical strikes
        CustomPlayer.AttributeWrapper attributeWrapper = player.getCustomAttribute(Attribute.ATTACK_DAMAGE);
        if (attributeWrapper != null) {
            double baseValue = attributeWrapper.getBaseValue();
            attributeWrapper.setBaseValue(baseValue * criticalHitDamageMultiplier);
        }
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
        if (player.getAttribute(Attribute.ATTACK_DAMAGE) != null) {
            double currentAttackDamage = player.getAttribute(Attribute.ATTACK_DAMAGE).getBaseValue();
            player.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(currentAttackDamage * criticalHitDamageMultiplier);
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

    private double getCriticalHitChance(CustomPlayer player) {
        return getCriticalHitChance();
    }

    private double getCriticalHitChance(Player player) {
        return getCriticalHitChance();
    }

    private double getCriticalHitDamageMultiplier(CustomPlayer player) {
        return getCriticalHitDamageMultiplier();
    }

    private double getCriticalHitDamageMultiplier(Player player) {
        return getCriticalHitDamageMultiplier();
    }
}

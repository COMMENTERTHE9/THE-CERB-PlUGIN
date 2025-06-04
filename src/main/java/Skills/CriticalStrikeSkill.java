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
        CustomPlayer.AttributeWrapper attributeWrapper = player.getCustomAttribute(Attribute.GENERIC_ATTACK_DAMAGE);
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
        if (player.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE) != null) {
            double currentAttackDamage = player.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE).getBaseValue();
            player.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE).setBaseValue(currentAttackDamage * criticalHitDamageMultiplier);
        }
    }

    private double getCriticalHitChance(CustomPlayer player) {
        // Calculate the critical hit chance based on the player's level in this skill
        int level = getLevel();
        return 0.1 + (level * 0.01); // Example: 10% base chance + 1% per skill level
    }

    private double getCriticalHitChance(Player player) {
        // Calculate the critical hit chance based on the player's level in this skill
        int level = getLevel();
        return 0.1 + (level * 0.01); // Example: 10% base chance + 1% per skill level
    }

    private double getCriticalHitDamageMultiplier(CustomPlayer player) {
        // Calculate the critical hit damage multiplier based on the player's level in this skill
        int level = getLevel();
        return 1.5 + (level * 0.05); // Example: 50% extra damage + 5% per skill level
    }

    private double getCriticalHitDamageMultiplier(Player player) {
        // Calculate the critical hit damage multiplier based on the player's level in this skill
        int level = getLevel();
        return 1.5 + (level * 0.05); // Example: 50% extra damage + 5% per skill level
    }
}

package Skills;

import cerberus.world.cerb.CustomPlayer;
import Manager.PlayerVirtualHealthManager;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;

public class WeaponMasterySkill extends CombatSkill {
    private final PlayerVirtualHealthManager virtualHealthManager;

    public WeaponMasterySkill(String name, PlayerVirtualHealthManager virtualHealthManager) {
        super(name);
        this.virtualHealthManager = virtualHealthManager;
    }

    @Override
    public void applyEffect(CustomPlayer player) {
        if (player == null) return;

        Player bukkitPlayer = player.getBukkitPlayer();

        // Example: Increase weapon attack damage by 15% for this skill level
        CustomPlayer.AttributeWrapper attributeWrapper = player.getCustomAttribute(Attribute.GENERIC_ATTACK_DAMAGE);
        if (attributeWrapper != null) {
            double baseValue = attributeWrapper.getBaseValue();
            attributeWrapper.setBaseValue(baseValue * 1.15); // Increase attack damage by 15%
        }

        // Convert a portion of the damage dealt into Virtual Health
        double damageDealt = attributeWrapper != null ? attributeWrapper.getBaseValue() : 0;
        double virtualHealthGain = damageDealt * (0.09 * getLevel()); // 5% of damage dealt per level is converted to Virtual Health

        virtualHealthManager.increasePlayerVirtualHealth(bukkitPlayer, virtualHealthGain);

        // Potentially add other weapon-related effects (e.g., increased durability, faster swing speed)
    }

    @Override
    public void applyEffect(Player player) {
        if (player == null) return;

        // Example: Increase weapon attack damage by 15% for this skill level
        if (player.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE) != null) {
            double currentAttackDamage = player.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE).getBaseValue();
            player.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE).setBaseValue(currentAttackDamage * 1.15);
        }

        // Convert a portion of the damage dealt into Virtual Health
        double damageDealt = player.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE) != null ? player.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE).getBaseValue() : 0;
        double virtualHealthGain = damageDealt * (0.05 * getLevel()); // 5% of damage dealt per level is converted to Virtual Health

        virtualHealthManager.increasePlayerVirtualHealth(player, virtualHealthGain);
    }
}

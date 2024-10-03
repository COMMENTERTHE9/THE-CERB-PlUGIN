package Skills;

import cerberus.world.cerb.CustomPlayer;
import Manager.PlayerVirtualHealthManager;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;

public class BladeMasterySkill extends CombatSkill {
    private final PlayerVirtualHealthManager virtualHealthManager;

    public BladeMasterySkill(String name, PlayerVirtualHealthManager virtualHealthManager) {
        super(name);
        this.virtualHealthManager = virtualHealthManager;
    }

    @Override
    public void applyEffect(CustomPlayer player) {
        if (player == null) return;

        // Increase attack damage by 10% per level
        CustomPlayer.AttributeWrapper attackDamageAttribute = player.getCustomAttribute(Attribute.GENERIC_ATTACK_DAMAGE);
        if (attackDamageAttribute != null) {
            double baseValue = attackDamageAttribute.getBaseValue();
            double damageMultiplier = virtualHealthManager.getDamageMultiplier(player.getBukkitPlayer());
            attackDamageAttribute.setBaseValue(baseValue * (1.0 + (getLevel() * 0.10)) * damageMultiplier); // 10% per level increase + Virtual Health multiplier
        }

        // Increase attack speed by 5% per level
        CustomPlayer.AttributeWrapper attackSpeedAttribute = player.getCustomAttribute(Attribute.GENERIC_ATTACK_SPEED);
        if (attackSpeedAttribute != null) {
            double baseValue = attackSpeedAttribute.getBaseValue();
            attackSpeedAttribute.setBaseValue(baseValue * (1.0 + (getLevel() * 0.05))); // 5% per level increase
        }

        // Increase movement speed by 1% per level
        CustomPlayer.AttributeWrapper movementSpeedAttribute = player.getCustomAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
        if (movementSpeedAttribute != null) {
            double baseValue = movementSpeedAttribute.getBaseValue();
            movementSpeedAttribute.setBaseValue(baseValue * (1.0 + (getLevel() * 0.01))); // 1% per level increase
        }
    }

    @Override
    public void applyEffect(Player player) {
        if (player == null) return;

        // Increase attack damage by 10% per level
        if (player.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE) != null) {
            double baseValue = player.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE).getBaseValue();
            double damageMultiplier = virtualHealthManager.getDamageMultiplier(player);
            player.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE).setBaseValue(baseValue * (1.0 + (getLevel() * 0.10)) * damageMultiplier); // 10% per level increase + Virtual Health multiplier
        }

        // Increase attack speed by 5% per level
        if (player.getAttribute(Attribute.GENERIC_ATTACK_SPEED) != null) {
            double baseValue = player.getAttribute(Attribute.GENERIC_ATTACK_SPEED).getBaseValue();
            player.getAttribute(Attribute.GENERIC_ATTACK_SPEED).setBaseValue(baseValue * (1.0 + (getLevel() * 0.05))); // 5% per level increase
        }

        // Increase movement speed by 1% per level
        if (player.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED) != null) {
            double baseValue = player.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).getBaseValue();
            player.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(baseValue * (1.0 + (getLevel() * 0.01))); // 1% per level increase
        }
    }
}

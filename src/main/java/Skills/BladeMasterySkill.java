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
        CustomPlayer.AttributeWrapper attackDamageAttribute = player.getCustomAttribute(Attribute.ATTACK_DAMAGE);
        if (attackDamageAttribute != null) {
            double baseValue = attackDamageAttribute.getBaseValue();
            attackDamageAttribute.setBaseValue(baseValue * (1.0 + (getLevel() * 0.10))); // 10% per level increase
        }

        // Increase attack speed by 5% per level
        CustomPlayer.AttributeWrapper attackSpeedAttribute = player.getCustomAttribute(Attribute.ATTACK_SPEED);
        if (attackSpeedAttribute != null) {
            double baseValue = attackSpeedAttribute.getBaseValue();
            attackSpeedAttribute.setBaseValue(baseValue * (1.0 + (getLevel() * 0.05))); // 5% per level increase
        }

        // Increase movement speed by 1% per level
        CustomPlayer.AttributeWrapper movementSpeedAttribute = player.getCustomAttribute(Attribute.MOVEMENT_SPEED);
        if (movementSpeedAttribute != null) {
            double baseValue = movementSpeedAttribute.getBaseValue();
            movementSpeedAttribute.setBaseValue(baseValue * (1.0 + (getLevel() * 0.01))); // 1% per level increase
        }
    }

    @Override
    public void applyEffect(Player player) {
        if (player == null) return;

        // Increase attack damage by 10% per level
        if (player.getAttribute(Attribute.ATTACK_DAMAGE) != null) {
            double baseValue = player.getAttribute(Attribute.ATTACK_DAMAGE).getBaseValue();
            player.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(baseValue * (1.0 + (getLevel() * 0.10))); // 10% per level increase
        }

        // Increase attack speed by 5% per level
        if (player.getAttribute(Attribute.ATTACK_SPEED) != null) {
            double baseValue = player.getAttribute(Attribute.ATTACK_SPEED).getBaseValue();
            player.getAttribute(Attribute.ATTACK_SPEED).setBaseValue(baseValue * (1.0 + (getLevel() * 0.05))); // 5% per level increase
        }

        // Increase movement speed by 1% per level
        if (player.getAttribute(Attribute.MOVEMENT_SPEED) != null) {
            double baseValue = player.getAttribute(Attribute.MOVEMENT_SPEED).getBaseValue();
            player.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(baseValue * (1.0 + (getLevel() * 0.01))); // 1% per level increase
        }
    }

    /** Public getter for CombatSkillProcessor — damage bonus per level (0.10). */
    public double getDamagePerLevel() {
        return getLevel() * 0.10;
    }

    /** Public getter for CombatSkillProcessor — speed bonus per level (0.05). */
    public double getSpeedMultiplier() {
        return 1.0 + (getLevel() * 0.05);
    }
}

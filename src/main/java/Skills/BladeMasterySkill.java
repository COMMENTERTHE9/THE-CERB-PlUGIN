package Skills;

import Manager.PlayerVirtualHealthManager;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;

public class BladeMasterySkill extends CombatSkill {
    private final PlayerVirtualHealthManager virtualHealthManager;

    public BladeMasterySkill(String name, PlayerVirtualHealthManager virtualHealthManager) {
        super(name);
        this.virtualHealthManager = virtualHealthManager;
    }

    @Override
    public void applyEffect(Player player) {
        if (player == null) return;

        // Increase attack damage by 10% per level
        AttributeInstance attackDamageAttr = player.getAttribute(Attribute.ATTACK_DAMAGE);
        if (attackDamageAttr != null) {
            double baseValue = attackDamageAttr.getBaseValue();
            attackDamageAttr.setBaseValue(baseValue * (1.0 + (getLevel() * 0.10))); // 10% per level increase
        }

        // Increase attack speed by 5% per level
        AttributeInstance attackSpeedAttr = player.getAttribute(Attribute.ATTACK_SPEED);
        if (attackSpeedAttr != null) {
            double baseValue = attackSpeedAttr.getBaseValue();
            attackSpeedAttr.setBaseValue(baseValue * (1.0 + (getLevel() * 0.05))); // 5% per level increase
        }

        // Increase movement speed by 1% per level
        AttributeInstance movementSpeedAttr = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (movementSpeedAttr != null) {
            double baseValue = movementSpeedAttr.getBaseValue();
            movementSpeedAttr.setBaseValue(baseValue * (1.0 + (getLevel() * 0.01))); // 1% per level increase
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

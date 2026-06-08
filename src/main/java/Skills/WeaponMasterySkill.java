package Skills;

import Manager.PlayerVirtualHealthManager;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;

public class WeaponMasterySkill extends CombatSkill {
    private final PlayerVirtualHealthManager virtualHealthManager;

    public WeaponMasterySkill(String name, PlayerVirtualHealthManager virtualHealthManager) {
        super(name);
        this.virtualHealthManager = virtualHealthManager;
    }

    @Override
    public void applyEffect(Player player) {
        if (player == null) return;

        // Example: Increase weapon attack damage by 15% for this skill level
        AttributeInstance attackDamageAttr = player.getAttribute(Attribute.ATTACK_DAMAGE);
        if (attackDamageAttr != null) {
            double baseValue = attackDamageAttr.getBaseValue();
            attackDamageAttr.setBaseValue(baseValue * 1.15); // Increase attack damage by 15%
        }

        // Convert a portion of the damage dealt into Virtual Health
        double damageDealt = attackDamageAttr != null ? attackDamageAttr.getBaseValue() : 0;
        double virtualHealthGain = damageDealt * (0.05 * getLevel()); // 5% of damage dealt per level is converted to Virtual Health

        virtualHealthManager.increasePlayerVirtualHealth(player, virtualHealthGain);

        // Potentially add other weapon-related effects (e.g., increased durability, faster swing speed)
    }

    /** Public getter for CombatSkillProcessor — lifesteal percent per level. */
    public double getLifestealPercent() {
        return getLevel() * 0.05;
    }

    /** Public getter for CombatSkillProcessor — flat damage multiplier (1.15x). */
    public double getDamageMultiplier() {
        return 1.15;
    }
}

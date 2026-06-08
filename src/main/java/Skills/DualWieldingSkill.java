package Skills;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;

public class DualWieldingSkill extends CombatSkill {

    public DualWieldingSkill(String name) {
        super(name);
    }

    @Override
    public void applyEffect(Player player) {
        if (player == null) return;

        // Check if the player is dual-wielding
        if (isDualWielding(player)) {
            // Increase off-hand damage by a percentage based on skill level
            AttributeInstance attackDamageAttr = player.getAttribute(Attribute.ATTACK_DAMAGE);
            if (attackDamageAttr != null) {
                double baseValue = attackDamageAttr.getBaseValue();
                attackDamageAttr.setBaseValue(baseValue * 1.2); // Increase off-hand attack damage by 20%
            }

            // Increase attack speed by a percentage based on skill level
            AttributeInstance attackSpeedAttr = player.getAttribute(Attribute.ATTACK_SPEED);
            if (attackSpeedAttr != null) {
                double currentAttackSpeed = attackSpeedAttr.getBaseValue();
                attackSpeedAttr.setBaseValue(currentAttackSpeed * 1.1); // Increase attack speed by 10%
            }

            // Chance for additional strike (e.g., 5% chance per skill level)
            if (Math.random() < getAdditionalStrikeChance()) {
                // Trigger additional strike logic here
                triggerAdditionalStrike(player);
            }
        }
    }

    private boolean isDualWielding(Player player) {
        ItemStack offHandItem = player.getInventory().getItemInOffHand();
        ItemStack mainHandItem = player.getInventory().getItemInMainHand();

        return offHandItem != null && !offHandItem.getType().equals(Material.AIR) &&
                mainHandItem != null && !mainHandItem.getType().equals(Material.AIR);
    }

    private double getAdditionalStrikeChance() {
        int level = getLevel();
        return level * 0.05; // 5% chance per level
    }

    private void triggerAdditionalStrike(Player player) {
        // Logic for triggering an additional strike
        // This is placeholder logic, and you'll need to implement the actual mechanics for an additional strike
        player.sendMessage(org.bukkit.ChatColor.YELLOW + "You triggered an additional strike!");
    }
}


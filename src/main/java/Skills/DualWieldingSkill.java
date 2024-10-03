package Skills;

import cerberus.world.cerb.CustomPlayer;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;

public class DualWieldingSkill extends CombatSkill {

    public DualWieldingSkill(String name) {
        super(name);
    }

    @Override
    public void applyEffect(CustomPlayer player) {
        if (player == null) return;

        // Check if the player is dual-wielding
        if (isDualWielding(player.getBukkitPlayer())) {
            // Increase off-hand damage by a percentage based on skill level
            CustomPlayer.AttributeWrapper attributeWrapper = player.getCustomAttribute(Attribute.GENERIC_ATTACK_DAMAGE);
            if (attributeWrapper != null) {
                double baseValue = attributeWrapper.getBaseValue();
                attributeWrapper.setBaseValue(baseValue * 1.2); // Increase off-hand attack damage by 20%
            }

            // Increase attack speed by a percentage based on skill level
            double currentAttackSpeed = player.getBukkitPlayer().getAttribute(Attribute.GENERIC_ATTACK_SPEED).getBaseValue();
            player.getBukkitPlayer().getAttribute(Attribute.GENERIC_ATTACK_SPEED).setBaseValue(currentAttackSpeed * 1.1); // Increase attack speed by 10%

            // Chance for additional strike (e.g., 5% chance per skill level)
            if (Math.random() < getAdditionalStrikeChance()) {
                // Trigger additional strike logic here
                triggerAdditionalStrike(player.getBukkitPlayer());
            }
        }
    }

    @Override
    public void applyEffect(Player player) {
        if (player == null) return;

        // Check if the player is dual-wielding
        if (isDualWielding(player)) {
            // Increase off-hand damage by a percentage based on skill level
            if (player.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE) != null) {
                double currentAttackDamage = player.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE).getBaseValue();
                player.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE).setBaseValue(currentAttackDamage * 1.2); // Increase off-hand attack damage by 20%
            }

            // Increase attack speed by a percentage based on skill level
            double currentAttackSpeed = player.getAttribute(Attribute.GENERIC_ATTACK_SPEED).getBaseValue();
            player.getAttribute(Attribute.GENERIC_ATTACK_SPEED).setBaseValue(currentAttackSpeed * 1.1); // Increase attack speed by 10%

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
        player.sendMessage("double double You triggered an additional strike!");
    }
}


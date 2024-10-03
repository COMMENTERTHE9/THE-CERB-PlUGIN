package Skills;

import cerberus.world.cerb.CustomPlayer;
import cerberus.world.cerb.cerb;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;
import org.bukkit.scheduler.BukkitRunnable;

public class MartialExpertiseSkill extends CombatSkill {

    public MartialExpertiseSkill(String name) {
        super(name);
    }

    @Override
    public void applyEffect(CustomPlayer player) {
        if (player == null) return;

        // Knockback resistance
        CustomPlayer.AttributeWrapper knockbackResistance = player.getCustomAttribute(Attribute.GENERIC_KNOCKBACK_RESISTANCE);
        if (knockbackResistance != null) {
            double baseValue = knockbackResistance.getBaseValue();
            knockbackResistance.setBaseValue(baseValue + (getLevel() * 0.01)); // 1% knockback resistance per level
        }

        // Disarm chance (placeholder)
        if (Math.random() < getLevel() * 0.01) { // 1% chance to disarm per level
            disarmOpponent(player.getBukkitPlayer());
        }
    }

    @Override
    public void applyEffect(Player player) {
        if (player == null) return;

        // Knockback resistance
        if (player.getAttribute(Attribute.GENERIC_KNOCKBACK_RESISTANCE) != null) {
            double currentKnockbackResistance = player.getAttribute(Attribute.GENERIC_KNOCKBACK_RESISTANCE).getBaseValue();
            player.getAttribute(Attribute.GENERIC_KNOCKBACK_RESISTANCE).setBaseValue(currentKnockbackResistance + (getLevel() * 0.01)); // 1% knockback resistance per level
        }

        // Disarm chance (placeholder)
        if (Math.random() < getLevel() * 0.01) { // 1% chance to disarm per level
            disarmOpponent(player);
        }
    }

    private void disarmOpponent(Player player) {
        ItemStack weapon = player.getInventory().getItemInMainHand();
        if (weapon == null || weapon.getType().isAir()) return;

        // Temporarily disable the weapon by moving it to another slot and preventing its use
        player.getInventory().setItemInMainHand(null); // Remove weapon from main hand
        player.sendMessage("You have been disarmed!");

        // Schedule task to return the weapon after a delay
        new BukkitRunnable() {
            @Override
            public void run() {
                player.getInventory().setItemInMainHand(weapon);
                player.sendMessage("Your weapon is usable again.");
            }
        }.runTaskLater(cerb.getInstance(), 100L); // Delay of 5 seconds (100 ticks)
    }
}

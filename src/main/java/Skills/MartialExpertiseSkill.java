package Skills;

import cerberus.world.cerb.CustomPlayer;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;

public class MartialExpertiseSkill extends CombatSkill {

    private final JavaPlugin plugin;

    public MartialExpertiseSkill(String name, JavaPlugin plugin) {
        super(name);
        this.plugin = plugin;
    }

    @Override
    public void applyEffect(CustomPlayer player) {
        if (player == null) return;

        // Knockback resistance
        CustomPlayer.AttributeWrapper knockbackResistance = player.getCustomAttribute(Attribute.KNOCKBACK_RESISTANCE);
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
        if (player.getAttribute(Attribute.KNOCKBACK_RESISTANCE) != null) {
            double currentKnockbackResistance = player.getAttribute(Attribute.KNOCKBACK_RESISTANCE).getBaseValue();
            player.getAttribute(Attribute.KNOCKBACK_RESISTANCE).setBaseValue(currentKnockbackResistance + (getLevel() * 0.01)); // 1% knockback resistance per level
        }

        // Disarm chance (placeholder)
        if (Math.random() < getLevel() * 0.01) { // 1% chance to disarm per level
            disarmOpponent(player);
        }
    }

    /** Public getter for CombatSkillProcessor — knockback resistance bonus. */
    public double getKnockbackResistBonus() {
        return getLevel() * 0.01;
    }

    /** Public getter for CombatSkillProcessor — disarm chance. */
    public double getDisarmChance() {
        return getLevel() * 0.01;
    }

    private void disarmOpponent(Player player) {
        ItemStack weapon = player.getInventory().getItemInMainHand();
        if (weapon == null || weapon.getType().isAir()) return;

        // Temporarily disable the weapon by moving it to another slot and preventing its use
        player.getInventory().setItemInMainHand(null); // Remove weapon from main hand
        player.sendMessage("You have been disarmed!");

        // Schedule task to return the weapon after a delay (player-bound for Folia)
        player.getScheduler().runDelayed(plugin, task -> {
            player.getInventory().setItemInMainHand(weapon);
            player.sendMessage("Your weapon is usable again.");
        }, null, 100L); // Delay of 5 seconds (100 ticks)
    }
}

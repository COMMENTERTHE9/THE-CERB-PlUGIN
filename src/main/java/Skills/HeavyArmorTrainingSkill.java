package Skills;

import Manager.PlayerVirtualHealthManager;
import Manager.PlayerDefenseManager;
import cerberus.world.cerb.CustomPlayer;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;

public class HeavyArmorTrainingSkill extends CombatSkill {

    private final PlayerVirtualHealthManager virtualHealthManager;
    private final PlayerDefenseManager defenseManager;

    public HeavyArmorTrainingSkill(String name, PlayerVirtualHealthManager virtualHealthManager, PlayerDefenseManager defenseManager) {
        super(name);
        this.virtualHealthManager = virtualHealthManager;
        this.defenseManager = defenseManager;
    }

    @Override
    public void applyEffect(CustomPlayer customPlayer) {
        if (customPlayer == null) return;

        Player player = customPlayer.getBukkitPlayer();
        int level = this.getLevel(); // Get the current skill level

        // Calculate the percentage of armor worn
        double armorMultiplier = calculateArmorMultiplier(player);

        // Apply a more significant percentage of the full damage reduction effect
        CustomPlayer.AttributeWrapper attributeWrapper = customPlayer.getCustomAttribute(Attribute.GENERIC_ARMOR);
        if (attributeWrapper != null) {
            double baseValue = attributeWrapper.getBaseValue();
            attributeWrapper.setBaseValue(baseValue * (0.75 + 0.25 * armorMultiplier) * (1 + 0.01 * level)); // Scales with skill level
        }

        // Apply knockback resistance proportional to the armor worn and skill level
        double knockbackReduction = 1 - (0.05 * level * armorMultiplier);
        virtualHealthManager.setKnockbackReductionFactor(player, knockbackReduction);

        // Apply increased health proportional to the armor worn and skill level using the PlayerVirtualHealthManager
        double currentMaxHealth = virtualHealthManager.getPlayerMaxVirtualHealth(player);
        double additionalHealth = 2.0 * armorMultiplier * (1 + 0.01 * level);
        virtualHealthManager.setPlayerMaxVirtualHealth(player, currentMaxHealth + additionalHealth);

        // Reduce the Virtual Health damage taken based on the skill level
        virtualHealthManager.reduceVirtualHealthDamage(player, level * 0.05); // 5% damage reduction per level

        // Apply increased defense based on skill level using the PlayerDefenseManager
        double currentDefense = defenseManager.getDefense(player);
        double additionalDefense = currentDefense * (0.1 * level); // 10% per level increase
        defenseManager.setDefense(player, currentDefense + additionalDefense);
    }

    @Override
    public void applyEffect(Player player) {
        if (player == null) return;

        int level = this.getLevel(); // Get the current skill level

        // Calculate the percentage of armor worn
        double armorMultiplier = calculateArmorMultiplier(player);

        // Apply a more significant percentage of the full damage reduction effect
        if (player.getAttribute(Attribute.GENERIC_ARMOR) != null) {
            double currentArmor = player.getAttribute(Attribute.GENERIC_ARMOR).getBaseValue();
            player.getAttribute(Attribute.GENERIC_ARMOR).setBaseValue(currentArmor * (0.75 + 0.25 * armorMultiplier) * (1 + 0.01 * level)); // Scales with skill level
        }

        // Apply knockback resistance proportional to the armor worn and skill level
        double knockbackReduction = 1 - (0.05 * level * armorMultiplier);
        virtualHealthManager.setKnockbackReductionFactor(player, knockbackReduction);

        // Apply increased health proportional to the armor worn and skill level using the PlayerVirtualHealthManager
        double currentMaxHealth = virtualHealthManager.getPlayerMaxVirtualHealth(player);
        double additionalHealth = 2.0 * armorMultiplier * (1 + 0.5 * level);
        virtualHealthManager.setPlayerMaxVirtualHealth(player, currentMaxHealth + additionalHealth);

        // Reduce the Virtual Health damage taken based on the skill level
        virtualHealthManager.reduceVirtualHealthDamage(player, level * 0.05); // 5% damage reduction per level

        // Apply increased defense based on skill level using the PlayerDefenseManager
        double currentDefense = defenseManager.getDefense(player);
        double additionalDefense = currentDefense * (0.1 * level); // 10% per level increase
        defenseManager.setDefense(player, currentDefense + additionalDefense);
    }

    private double calculateArmorMultiplier(Player player) {
        int totalArmorPieces = 4;
        int wornArmorPieces = 0;

        ItemStack[] armor = player.getInventory().getArmorContents();
        for (ItemStack item : armor) {
            if (isArmor(item)) {
                wornArmorPieces++;
            }
        }

        // Return a multiplier based on the percentage of armor pieces worn
        return (double) wornArmorPieces / totalArmorPieces;
    }

    private boolean isArmor(ItemStack item) {
        return item != null && (item.getType() == Material.LEATHER_HELMET ||
                item.getType() == Material.LEATHER_CHESTPLATE ||
                item.getType() == Material.LEATHER_LEGGINGS ||
                item.getType() == Material.LEATHER_BOOTS ||
                item.getType() == Material.CHAINMAIL_HELMET ||
                item.getType() == Material.CHAINMAIL_CHESTPLATE ||
                item.getType() == Material.CHAINMAIL_LEGGINGS ||
                item.getType() == Material.CHAINMAIL_BOOTS ||
                item.getType() == Material.GOLDEN_HELMET ||
                item.getType() == Material.GOLDEN_CHESTPLATE ||
                item.getType() == Material.GOLDEN_LEGGINGS ||
                item.getType() == Material.GOLDEN_BOOTS ||
                item.getType() == Material.IRON_HELMET ||
                item.getType() == Material.IRON_CHESTPLATE ||
                item.getType() == Material.IRON_LEGGINGS ||
                item.getType() == Material.IRON_BOOTS ||
                item.getType() == Material.DIAMOND_HELMET ||
                item.getType() == Material.DIAMOND_CHESTPLATE ||
                item.getType() == Material.DIAMOND_LEGGINGS ||
                item.getType() == Material.DIAMOND_BOOTS ||
                item.getType() == Material.NETHERITE_HELMET ||
                item.getType() == Material.NETHERITE_CHESTPLATE ||
                item.getType() == Material.NETHERITE_LEGGINGS ||
                item.getType() == Material.NETHERITE_BOOTS);
    }
}

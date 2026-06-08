package Skills;

import Manager.PlayerVirtualHealthManager;
import Manager.PlayerDefenseManager;
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
    public void applyEffect(Player player) {
        if (player == null) return;

        // Defense bonuses are now computed automatically from gear + skill level
        // via PlayerDefenseManager.applySkillBonuses(). Trigger a recalc.
        defenseManager.updateDefense(player);
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

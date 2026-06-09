package Skills;

import Manager.CraftingManager;
import Manager.PlayerVirtualHealthManager;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class FirstAidSkill extends UtilitySkill {

    private final PlayerVirtualHealthManager healthManager;
    private final CraftingManager craftingManager;

    public FirstAidSkill(String name, PlayerVirtualHealthManager healthManager, CraftingManager craftingManager) {
        super(name);
        this.healthManager = healthManager;
        this.craftingManager = craftingManager;
    }

    @Override
    public void applyEffect(Player player) {
        if (player == null) return;

        int level = this.getLevel();

        // NOTE (Step 0 — landmine defused): the old formula granted level*1500 max HP
        // (+150,000 HP at level 100) plus level*1000 regen — an accidental runaway that made
        // combat untunable. First Aid's REAL contribution will flow through the Attribute
        // stat-bus (Vitality) once that layer is built (design Step 1-2). Until then these are
        // hard-capped so the runaway can never fire.
        double healthBoost = Math.min(level * 5.0, 500.0);   // capped (was level * 1500)
        healthManager.increaseMaxHealth(player, healthBoost);

        double regenBoost = Math.min(level * 2.0, 200.0);    // capped (was level * 1000)
        healthManager.applyHealthRegen(player, regenBoost, 20L * 10); // over 10 seconds

        // Enhance healing items — bounded healing-effectiveness bonus
        ItemStack healingItem = getHealingItem(player);
        if (healingItem != null) {
            double healingBonus = Math.min(level * 0.01, 0.50); // capped at +50% (was level*0.10)
            craftingManager.enhanceHealingItems(player, healingItem, healingBonus);
        }
    }

    // Example method to retrieve the healing item from the player's inventory
    private ItemStack getHealingItem(Player player) {
        return player.getInventory().getItemInMainHand(); // Example: Get the item in the player's main hand
    }
}

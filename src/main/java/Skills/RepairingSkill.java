package Skills;

import Manager.CraftingManager;
import cerberus.world.cerb.CustomPlayer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;

public class RepairingSkill extends UtilitySkill {

    private final CraftingManager craftingManager;
    private final Map<Block, String> machineTagMap = new HashMap<>(); // Map to store machine tags temporarily

    public RepairingSkill(String name, CraftingManager craftingManager) {
        super(name);
        this.craftingManager = craftingManager;
    }

    @Override
    public void applyEffect(CustomPlayer customPlayer) {
        if (customPlayer == null) return;
        Player player = customPlayer.getPlayer();
        int level = this.getLevel();
        ItemStack item = player.getInventory().getItemInMainHand(); // Assuming the item being repaired is in the main hand

        // Detect if the player is using a repair machine
        Block machine = player.getTargetBlockExact(5); // Get the block the player is targeting within 5 blocks
        if (machine != null && isRepairMachine(machine)) {
            // Handle machine-based repair logic
            handleMachineBasedRepair(player, machine, item, level);
        } else {
            // Apply regular repair logic using CraftingManager if no machine is used
            double repairCostReduction = level * 0.01; // 1% repair cost reduction per level
            double repairEffectivenessBonus = level * 0.02; // 2% increased durability restored per level
            craftingManager.applyRepairBonuses(player, repairCostReduction, repairEffectivenessBonus);

            if (level > 100) {
                double overRepairBonus = (level - 100) * 0.01; // 1% chance to over-repair per level above 100
                applyOverRepair(player, item, overRepairBonus);
            }
        }
    }

    @Override
    public void applyEffect(Player player) {
        // Direct player effects can be added here if needed
    }

    // Handle repair through a machine
    private void handleMachineBasedRepair(Player player, Block machine, ItemStack item, int level) {
        if (item == null || machine == null) return;

        // Automatically tag the block as a repair machine if it hasn't been tagged
        if (!isRepairMachine(machine)) {
            tagBlockAsRepairMachine(machine, "BasicRepairMachine");
        }

        // Now proceed with machine-based repair logic
        double machineBonus = level * 0.03; // 3% extra bonus for using machines
        craftingManager.applyRepairEffectiveness(item, machineBonus);

        player.sendMessage("Your item is being repaired using a machine! Extra bonuses applied.");

        // Check for over-repair logic if applicable
        if (level > 100) {
            double overRepairBonus = (level - 100) * 0.01; // Additional over-repair bonus
            applyOverRepair(player, item, overRepairBonus);
        }
    }

    // Check if the block is tagged as a repair machine (using a temporary map for now)
    private boolean isRepairMachine(Block block) {
        return machineTagMap.containsKey(block);
    }

    // Automatically tag a block as a repair machine (temporary in-memory tagging for now)
    private void tagBlockAsRepairMachine(Block block, String machineType) {
        machineTagMap.put(block, machineType);
    }

    // Apply over-repair logic, including tagging the item
    private void applyOverRepair(Player player, ItemStack item, double overRepairBonus) {
        if (item == null || overRepairBonus <= 0) return;

        // Check if item can be over-repaired (e.g., max durability exceeded)
        short maxDurability = item.getType().getMaxDurability();
        short currentDurability = item.getDurability();
        short overRepairedDurability = (short) Math.max(0, currentDurability - (maxDurability * (overRepairBonus * 1.5)));

        if (overRepairedDurability < 0) {
            // Cap durability and apply over-repair bonuses
            item.setDurability((short) 0); // Over-repair fully
            tagItemAsOverRepaired(item, player);

            // Give additional bonuses, such as temporary enchantments or boosts
            player.sendMessage("Your item has been over-repaired! Special bonuses applied.");
            item.addUnsafeEnchantment(Enchantment.SHARPNESS, 3); // Example: temporary boost
        } else {
            item.setDurability(overRepairedDurability);
        }
    }

    // Automatically tag the item as over-repaired
    private void tagItemAsOverRepaired(ItemStack item, Player player) {
        if (item == null) return;

        NamespacedKey key = new NamespacedKey(craftingManager.getPlugin(), "OverRepaired");
        item.getItemMeta().getPersistentDataContainer().set(key, PersistentDataType.STRING, "OverRepaired");

        // Apply any additional logic for over-repaired items
        player.sendMessage("The item is now tagged as over-repaired with special bonuses.");
    }
}

package Skills;

import cerberus.world.cerb.CustomPlayer;
import Manager.CraftingManager;
import Manager.PlayerManaManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Random;

public class HerbalismSkill extends UtilitySkill {

    private final CraftingManager craftingManager;
    private final PlayerManaManager manaManager;
    private final Random random = new Random(); // For random bonuses or rare drops

    public HerbalismSkill(String name, CraftingManager craftingManager, PlayerManaManager manaManager) {
        super(name);
        this.craftingManager = craftingManager;
        this.manaManager = manaManager;
    }

    @Override
    public void applyEffect(CustomPlayer customPlayer) {
        if (customPlayer == null) return;
        Player player = customPlayer.getPlayer();
        int level = this.getLevel();

        // Apply potion duration and effectiveness bonuses
        double potionDurationBonus = level * 0.02; // +2% duration per level

        // Create an ItemStack to represent the potion item
        ItemStack potionItem = new ItemStack(Material.POTION); // Example: You can replace this with a real potion item

        // Corrected method call to pass the potion item along with the player and the effectiveness bonus
        craftingManager.increasePotionEffectiveness(player, potionItem, potionDurationBonus);

        // Apply mana regeneration and max mana bonuses
        double manaRegenBonus = level * 0.5; // +0.5% mana regeneration per level
        double maxManaBonus = level * 2.0;   // +2 max mana per level
        manaManager.increaseManaRegenRate(player, manaRegenBonus);
        manaManager.increaseMaxMana(player, maxManaBonus);
    }

    // Method to handle block breaking and apply the herb yield bonus and specific herb effects
    public void handleBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Material blockType = event.getBlock().getType();

        if (isHerbBlock(blockType)) {
            int level = this.getLevel();
            double yieldMultiplier = 1.0 + (level * 0.1); // +10% yield per level

            // Cancel default drops to modify them
            event.setDropItems(false);

            // Apply the yield multiplier
            event.getBlock().getDrops().forEach(drop -> {
                drop.setAmount((int) (drop.getAmount() * yieldMultiplier));
                player.getWorld().dropItemNaturally(event.getBlock().getLocation(), drop);
            });

            // Apply special herb-specific bonuses
            applyHerbSpecificBonuses(player, blockType, level);

            player.sendMessage("Your herbalism skill grants you a yield multiplier of " + yieldMultiplier + " for " + blockType);
        }
    }

    // Check if the block is considered a herb block
    private boolean isHerbBlock(Material material) {
        switch (material) {
            case WHEAT:
            case CARROTS:
            case POTATOES:
            case BEETROOTS:
            case NETHER_WART:
            case SWEET_BERRIES:
            case SUGAR_CANE:
            case CACTUS:
            case BAMBOO:
            case DANDELION:
            case POPPY:
            case BLUE_ORCHID:
            case ALLIUM:
            case AZURE_BLUET:
            case RED_TULIP:
            case ORANGE_TULIP:
            case WHITE_TULIP:
            case PINK_TULIP:
            case OXEYE_DAISY:
            case CORNFLOWER:
            case LILY_OF_THE_VALLEY:
            case SUNFLOWER:
            case LILAC:
            case ROSE_BUSH:
            case PEONY:
            case SHORT_GRASS:
            case TALL_GRASS:
                return true;
            default:
                return false;
        }
    }

    // Apply specific herb bonuses, such as potion buffs, rare drops, or experience
    private void applyHerbSpecificBonuses(Player player, Material blockType, int level) {
        switch (blockType) {
            case NETHER_WART:
                applyPotionBuff(player, PotionEffectType.STRENGTH, level);  // Strength buff
                break;
            case WHEAT:
                applyPotionBuff(player, PotionEffectType.REGENERATION, level);  // Health regeneration
                break;
            case SUGAR_CANE:
                applyPotionBuff(player, PotionEffectType.SPEED, level);  // Speed boost
                break;
            case SWEET_BERRIES:
                if (random.nextDouble() < 0.1) {  // 10% chance for rare drop
                    player.getInventory().addItem(new ItemStack(Material.GOLDEN_APPLE));  // Example rare drop
                    player.sendMessage("You have found a rare golden apple!");
                }
                break;
            default:
                break;
        }
    }

    // Apply a temporary potion effect based on the herb type and player skill level
    private void applyPotionBuff(Player player, PotionEffectType effectType, int level) {
        int duration = 200 + (level * 20);  // +20 ticks (1 second) per skill level
        int amplifier = Math.min(level / 20, 2);  // Amplifier up to level 2 based on skill
        player.addPotionEffect(new PotionEffect(effectType, duration, amplifier));
        player.sendMessage("You received a temporary buff: " + effectType.getName());
    }

    @Override
    public void applyEffect(Player player) {
        // This method can be used for direct player effects if needed
    }
}

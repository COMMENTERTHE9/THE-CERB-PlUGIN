package Listener;

import GUIs.PlayerMenuGUI;
import GUIs.SkillGUI;
import Skills.SkillManager;
import cerberus.world.cerb.cerb;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.Arrays;

public class MenuListener implements Listener {
    private final PlayerMenuGUI playerMenuGUI;
    private final SkillGUI skillGUI;
    private final SkillManager skillManager;

    public MenuListener(cerb plugin, PlayerMenuGUI playerMenuGUI, SkillGUI skillGUI, SkillManager skillManager) {
        this.playerMenuGUI = playerMenuGUI;
        this.skillGUI = skillGUI;
        this.skillManager = skillManager;
    }

    // Event handling for inventory click events
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();
        Inventory inventory = event.getInventory();

        // Check if clicked item is valid
        if (clickedItem == null || clickedItem.getItemMeta() == null || !clickedItem.hasItemMeta()) {
            return;
        }

        // Check for playerMenuGUI being null
        if (playerMenuGUI == null) {
            player.sendMessage(ChatColor.RED + "Error: The player menu is currently unavailable.");
            return;
        }

        // Check for skillGUI being null
        if (skillGUI == null) {
            player.sendMessage(ChatColor.RED + "Error: The skill GUI is currently unavailable.");
            return;
        }

        String title = player.getOpenInventory().getTitle();
        String itemName = clickedItem.getItemMeta().getDisplayName();

        // Handle Player Menu interactions
        if (title.equals(ChatColor.DARK_PURPLE + "Player Menu")) {
            event.setCancelled(true);
            if (itemName.equals(ChatColor.DARK_RED + "Skills")) {
                playerMenuGUI.openSkillManagementMenu(player, skillGUI);
            } else if (itemName.equals(ChatColor.GREEN + "All Skills")) {
                skillGUI.show(player, "all");
            }
        }

        // Handle Skill Management Menu interactions
        else if (title.equals(ChatColor.RED + "Skill Management")) {
            event.setCancelled(true);
            if (itemName.equals(ChatColor.RED + "Combat Skills")) {
                skillGUI.show(player, "combat");
            } else if (itemName.equals(ChatColor.BLUE + "Magic Skills")) {
                skillGUI.show(player, "magic");
            } else if (itemName.equals(ChatColor.GREEN + "Utility Skills")) {
                skillGUI.show(player, "utility");
            } else if (itemName.equals(ChatColor.RED + "Back") || itemName.equals(ChatColor.YELLOW + "Back")) { // Adjusted for "Back" button
                playerMenuGUI.openMainMenu(player);
            }
        }

        else if (title.equals(ChatColor.RED + "Combat Skills") || title.equals(ChatColor.BLUE + "Magic Skills") || title.equals(ChatColor.GREEN + "Utility Skills")) {
            event.setCancelled(true);
            if (itemName.equals(ChatColor.YELLOW + "Back")) { // Adjusted for "Back" button
                playerMenuGUI.openSkillManagementMenu(player, skillGUI);
            } else {
                String skillName = ChatColor.stripColor(itemName.split(" \\(")[0]);
                skillManager.upgradeSkill(player, skillName); // Call to SkillManager to upgrade skill
                skillGUI.show(player, title.toLowerCase().split(" ")[0]);
            }
        }

        // Handle Combat Skills Menu interactions
        else if (title.equals(ChatColor.RED + "Combat Skills")) {
            event.setCancelled(true);
            if (itemName.equals(ChatColor.YELLOW + "Back")) { // Adjusted for "Back" button
                playerMenuGUI.openSkillManagementMenu(player, skillGUI);
            } else {
                String skillName = ChatColor.stripColor(itemName.split(" \\(")[0]);
                skillManager.upgradeSkill(player, skillName); // Call to SkillManager to upgrade skill
                skillGUI.show(player, "combat");
            }
        }

        // Handle Magic Skills Menu interactions
        else if (title.equals(ChatColor.BLUE + "Magic Skills")) {
            event.setCancelled(true);
            if (itemName.equals(ChatColor.YELLOW + "Back")) { // Adjusted for "Back" button
                playerMenuGUI.openSkillManagementMenu(player, skillGUI);
            } else {
                String skillName = ChatColor.stripColor(itemName.split(" \\(")[0]);
                skillManager.upgradeSkill(player, skillName); // Call to SkillManager to upgrade skill
                skillGUI.show(player, "magic");
            }
        }

        // Handle Utility Skills Menu interactions
        else if (title.equals(ChatColor.GREEN + "Utility Skills")) {
            event.setCancelled(true);
            if (itemName.equals(ChatColor.YELLOW + "Back")) { // Adjusted for "Back" button
                playerMenuGUI.openSkillManagementMenu(player, skillGUI);
            } else {
                String skillName = ChatColor.stripColor(itemName.split(" \\(")[0]);
                skillManager.upgradeSkill(player, skillName); // Call to SkillManager to upgrade skill
                skillGUI.show(player, "utility");
            }
        }
    }

    // Event handling for player join events
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Create the player's head for the menu
        ItemStack playerHead = new ItemStack(Material.PLAYER_HEAD, 1);
        playerHead = createPlayerMenuItem(player); // Assign player head with meta

        // Set the player head in the most right hotbar slot (slot 8)
        player.getInventory().setItem(8, playerHead);

        // Optionally send a message to the player
        player.sendMessage(ChatColor.GREEN + "You have been given your player menu. Use it to access various features!");
    }

    // Helper method to create the player's head with meta
    private ItemStack createPlayerMenuItem(Player player) {
        ItemStack playerHead = new ItemStack(Material.PLAYER_HEAD, 1);
        SkullMeta skullMeta = (SkullMeta) playerHead.getItemMeta();
        skullMeta.setOwningPlayer(player); // Set the skull to the player's head
        skullMeta.setDisplayName(ChatColor.LIGHT_PURPLE + "Player Menu");
        skullMeta.setLore(Arrays.asList(ChatColor.GOLD + "Right-click to open your player menu."));
        playerHead.setItemMeta(skullMeta);
        return playerHead;
    }

    // Event handling for entity death
    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        Entity entity = event.getEntity();

        // Check if the entity is a LivingEntity (to have a killer) and cast it
        if (entity instanceof LivingEntity livingEntity) {
            // Check if the killer is a player
            if (livingEntity.getKiller() instanceof Player player) {
                // Determine mob properties
                double health = livingEntity.getMaxHealth(); // Max health of the mob
                double damage = event.getEntity().getLastDamage(); // Damage done to the mob
                boolean isRare = determineIfRareMob(livingEntity); // Define this method to identify rare mobs
                boolean isBoss = determineIfBossMob(livingEntity); // Define this method to identify boss mobs

                // Add XP for the mob kill
                skillManager.addXpForMobKill("Combat", health, damage, isRare, isBoss);

                // Optional: Notify the player about XP gain
                player.sendMessage(ChatColor.GREEN + "You gained XP for defeating " + livingEntity.getType().name() + "!");

                // Get the PlayerMenuGUI from the skill manager
                PlayerMenuGUI playerMenu = skillManager.getPlayerMenu();

                // Check if the PlayerMenuGUI is null
                if (playerMenu == null) {
                    player.sendMessage("Error: Player menu is not available.");
                    return; // Stop execution if the player menu is null
                }

                // Assuming you're passing a skill type to refresh the menu
                String skillType = "combat"; // Example, adjust based on your logic
                playerMenu.refreshSkillMenu(player, skillGUI, skillType);
            }
        }
    }

    // Example methods to determine if a mob is rare or a boss
    private boolean determineIfRareMob(LivingEntity entity) {
        // Define criteria for rare mobs
        // Example: Check if the entity has a custom name or a specific type
        return entity.getCustomName() != null || entity.getType() == EntityType.WITHER_SKELETON;
    }

    private boolean determineIfBossMob(LivingEntity entity) {
        // Define criteria for boss mobs
        // Example: Check for certain mob types or specific health thresholds
        return entity.getType() == EntityType.ENDER_DRAGON || entity.getType() == EntityType.WITHER;
    }
}

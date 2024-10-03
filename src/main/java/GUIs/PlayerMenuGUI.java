package GUIs;

import Skills.SkillManager;
import cerberus.world.cerb.cerb;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Arrays;

public class PlayerMenuGUI {
    private final cerb plugin;
    private final SkillManager skillManager;
    private Inventory playerMenu; // Dynamic creation of the menu

    public PlayerMenuGUI(cerb plugin, SkillManager skillManager) {
        this.plugin = plugin;
        this.skillManager = skillManager;
    }

    // Method to open the main player menu
    public void openMainMenu(Player player) {
        // Create the playerMenu dynamically each time
        playerMenu = plugin.getServer().createInventory(null, 54, ChatColor.DARK_PURPLE + "Player Menu");

        // Create the player's head item using their own skin and set it at slot 13
        playerMenu.setItem(13, getPlayerMenuItem(player));

        // Set up main menu items
        playerMenu.setItem(14, createGuiItem(Material.NETHERITE_SWORD, ChatColor.DARK_RED + "Skills", "Manage your skills", ChatColor.GOLD + "View and upgrade your skills!"));
        playerMenu.setItem(22, createGuiItem(Material.CRAFTING_TABLE, ChatColor.GREEN + "Recipes", "View crafting recipes", ChatColor.GOLD + "Discover new recipes!"));
        playerMenu.setItem(15, createGuiItem(Material.CHEST, ChatColor.GOLD + "Collections", "View your collections", ChatColor.GOLD + "Track your collected items."));
        playerMenu.setItem(11, createGuiItem(Material.BOOKSHELF, ChatColor.DARK_BLUE + "General", "Access your pets, accessories, runes, and more", ChatColor.GOLD + "Manage various gameplay features!"));
        playerMenu.setItem(12, createGuiItem(Material.DRAGON_EGG, ChatColor.DARK_PURPLE + "Mobs", "View mobs information", ChatColor.GOLD + "Learn about different mobs."));
        playerMenu.setItem(21, createGuiItem(Material.COMPASS, ChatColor.DARK_AQUA + "Map", "View world map", ChatColor.GOLD + "Explore the world and discover locations!"));
        playerMenu.setItem(23, createGuiItem(Material.NETHERITE_HELMET, ChatColor.YELLOW + "Armor", "Quick swap armor", ChatColor.GOLD + "Change your armor quickly!"));

        // Add filler items for aesthetics (empty glass panes)
        for (int i = 0; i < playerMenu.getSize(); i++) {
            if (playerMenu.getItem(i) == null) {
                playerMenu.setItem(i, createGuiItem(Material.GRAY_STAINED_GLASS_PANE, " "));
            }
        }

        // Open the menu for the player
        player.openInventory(playerMenu);
    }

    // Create the player's head item using their skin
    public ItemStack getPlayerMenuItem(Player player) {
        ItemStack playerHead = new ItemStack(Material.PLAYER_HEAD, 1);
        SkullMeta skullMeta = (SkullMeta) playerHead.getItemMeta();
        skullMeta.setOwningPlayer(player);  // Set the player's skin
        skullMeta.setDisplayName(ChatColor.LIGHT_PURPLE + "Player Menu");
        skullMeta.setLore(Arrays.asList(ChatColor.GOLD + "Right-click to open your player menu."));
        playerHead.setItemMeta(skullMeta);
        return playerHead;
    }

    // Method to open the Skill Management Menu and connect with SkillGUI
    public void openSkillManagementMenu(Player player, SkillGUI skillGUI) {
        Inventory skillManagementMenu = plugin.getServer().createInventory(null, 27, ChatColor.RED + "Skill Management");

        // Add menu items for Combat, Magic, Utility Skills, and Back button
        skillManagementMenu.setItem(11, createGuiItem(Material.DIAMOND_SWORD, ChatColor.RED + "Combat Skills", "Manage combat-related skills"));
        skillManagementMenu.setItem(13, createGuiItem(Material.ENCHANTED_BOOK, ChatColor.BLUE + "Magic Skills", "Manage magic-related skills"));
        skillManagementMenu.setItem(15, createGuiItem(Material.IRON_PICKAXE, ChatColor.GREEN + "Utility Skills", "Manage utility-related skills"));
        skillManagementMenu.setItem(18, createGuiItem(Material.ARROW, ChatColor.RED + "Back", "Return to Player Menu"));

        player.openInventory(skillManagementMenu);
    }

    // Example for creating a generic GUI item
    private ItemStack createGuiItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(Arrays.asList(lore));
        hideVanillaStats(meta);
        item.setItemMeta(meta);
        return item;
    }

    // Add this method to check if an item is the player menu head
    public boolean isPlayerMenuHead(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();

        // Check if the display name matches the expected player menu head name
        return meta.hasDisplayName() && meta.getDisplayName().equals(ChatColor.LIGHT_PURPLE + "Player Menu");
    }

    // Update player skill info without forcing the menu open
    public void updatePlayerSkillInfo(Player player) {
        player.sendMessage(ChatColor.GREEN + "Your skill information has been updated.");
        // Logic to update skill info, but not opening the menu
    }

    // Refresh the player's skill menu in real-time, but only if the menu is open
    public void refreshSkillMenu(Player player, SkillGUI skillGUI, String skillType) {
        if (player.getOpenInventory().getTopInventory().equals(playerMenu)) {
            skillGUI.show(player, skillType);  // Only refresh if the player has the menu open
        }
    }

    // Start real-time XP update task, ensuring the menu is not opened automatically
    public void startRealTimeXPUpdate(Player player, SkillGUI skillGUI, String skillType) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (player.getOpenInventory().getTopInventory().equals(playerMenu)) {
                    refreshSkillMenu(player, skillGUI, skillType);  // Only refresh if menu is already open
                }
            }
        }.runTaskTimer(plugin, 0, 20);  // Run every second (20 ticks)
    }

    // Utility method to hide vanilla stats on GUI items
    private void hideVanillaStats(ItemMeta meta) {
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        meta.addItemFlags(ItemFlag.HIDE_DESTROYS);
        meta.addItemFlags(ItemFlag.HIDE_PLACED_ON);
    }
}

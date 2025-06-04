package GUIs;

import Skills.SkillManager;
import cerberus.world.cerb.CerberusPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class PlayerMenuGUI {
    private final CerberusPlugin plugin;
    private final SkillManager skillManager;
    private Inventory playerMenu;
    private static final int MENU_SIZE = 54;
    private static final int SKILL_MENU_SIZE = 27;
    private final Map<UUID, BukkitTask> updateTasks = new HashMap<>();

    // Menu slot constants
    private static final class MenuSlots {
        static final int PLAYER_HEAD = 13;
        static final int SKILLS = 14;
        static final int RECIPES = 22;
        static final int COLLECTIONS = 15;
        static final int GENERAL = 11;
        static final int BESTIARY = 12;
        static final int MAP = 21;
        static final int ARMOR = 23;
    }

    public PlayerMenuGUI(CerberusPlugin plugin, SkillManager skillManager) {
        this.plugin = plugin;
        this.skillManager = skillManager;
    }

    public void openMainMenu(Player player) {
        playerMenu = plugin.getServer().createInventory(null, MENU_SIZE, ChatColor.DARK_PURPLE + "Player Menu");

        playerMenu.setItem(MenuSlots.PLAYER_HEAD, getPlayerMenuItem(player));

        playerMenu.setItem(MenuSlots.SKILLS, createMenuButton(
                Material.NETHERITE_SWORD, ChatColor.DARK_RED, "Skills",
                "Manage your skills", "View and upgrade your skills!"
        ));

        playerMenu.setItem(MenuSlots.RECIPES, createMenuButton(
                Material.CRAFTING_TABLE, ChatColor.GREEN, "Recipes",
                "View crafting recipes", "Discover new recipes! YUM."
        ));

        playerMenu.setItem(MenuSlots.COLLECTIONS, createMenuButton(
                Material.CHEST, ChatColor.GOLD, "Collections and collectibles",
                "View your collections", "Track your collected items."
        ));

        playerMenu.setItem(MenuSlots.GENERAL, createMenuButton(
                Material.LODESTONE, ChatColor.DARK_BLUE, "Player Features",
                "Access player systems", "Character Stats, Unlockables, ETC."
        ));

        playerMenu.setItem(MenuSlots.BESTIARY, createMenuButton(
                Material.DRAGON_EGG, ChatColor.DARK_PURPLE, "Mobs, Monsters & More",
                "View mobs information", "Learn about different creatures."
        ));

        playerMenu.setItem(MenuSlots.MAP, createMenuButton(
                Material.COMPASS, ChatColor.DARK_AQUA, "Map",
                "View world map", "Explore the world and discover locations!"
        ));

        playerMenu.setItem(MenuSlots.ARMOR, createMenuButton(
                Material.NETHERITE_HELMET, ChatColor.YELLOW, "Armor",
                "Quick swap armor", "Change your armor quickly!"
        ));

        fillEmptySlots(playerMenu);
        player.openInventory(playerMenu);
    }

    public ItemStack getPlayerMenuItem(Player player) {
        ItemStack playerHead = new ItemStack(Material.PLAYER_HEAD, 1);
        SkullMeta skullMeta = (SkullMeta) playerHead.getItemMeta();
        if (skullMeta != null) {
            skullMeta.setOwningPlayer(player);
            skullMeta.setDisplayName(ChatColor.LIGHT_PURPLE + "Player Menu");
            skullMeta.setLore(Arrays.asList(
                    "",
                    ChatColor.GRAY + "Level: " + skillManager.getPlayerLevel(player),   // now resolves
                    ChatColor.GOLD + "Right-click to open your player menu."
            ));
            playerHead.setItemMeta(skullMeta);
        }
        return playerHead;
    }


    public void openSkillManagementMenu(Player player, SkillGUI skillGUI) {
        Inventory skillManagementMenu = plugin.getServer().createInventory(null, SKILL_MENU_SIZE,
                ChatColor.RED + "Skill Management");

        skillManagementMenu.setItem(11, createMenuButton(
                Material.DIAMOND_SWORD, ChatColor.RED, "Combat Skills",
                "Manage combat-related skills", "Master your combat abilities"
        ));

        skillManagementMenu.setItem(13, createMenuButton(
                Material.ENCHANTED_BOOK, ChatColor.BLUE, "Magic Skills",
                "Manage magic-related skills", "Harness magical powers"
        ));

        skillManagementMenu.setItem(15, createMenuButton(
                Material.IRON_PICKAXE, ChatColor.GREEN, "Utility Skills",
                "Manage utility-related skills", "Develop practical skills"
        ));

        skillManagementMenu.setItem(18, createMenuButton(
                Material.ARROW, ChatColor.RED, "Back",
                "Return to Player Menu", "Click to go back"
        ));

        fillEmptySlots(skillManagementMenu);
        player.openInventory(skillManagementMenu);
    }

    private ItemStack createMenuButton(Material material, ChatColor color, String name,
                                       String description, String action) {
        return createGuiItem(material,
                color + name,
                description,
                ChatColor.GOLD + action);
    }

    private ItemStack createGuiItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(Arrays.asList(lore));
            hideVanillaStats(meta);
            item.setItemMeta(meta);
        }
        return item;
    }

    private void fillEmptySlots(Inventory menu) {
        ItemStack filler = createGuiItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < menu.getSize(); i++) {
            if (menu.getItem(i) == null) {
                menu.setItem(i, filler);
            }
        }
    }

    public boolean isPlayerMenuHead(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta.hasDisplayName() && meta.getDisplayName().equals(ChatColor.LIGHT_PURPLE + "Player Menu");
    }

    public void updatePlayerSkillInfo(Player player) {
        if (player == null || !player.isOnline()) return;

        player.sendMessage(ChatColor.GREEN + "Your skill information has been updated.");
        if (player.getOpenInventory().getTopInventory().equals(playerMenu)) {
            playerMenu.setItem(MenuSlots.PLAYER_HEAD, getPlayerMenuItem(player));
        }
    }

    public void refreshSkillMenu(Player player, SkillGUI skillGUI, String skillType) {
        if (player.getOpenInventory().getTopInventory().equals(playerMenu)) {
            skillGUI.show(player, skillType);
        }
    }

    public void startRealTimeXPUpdate(Player player, SkillGUI skillGUI, String skillType) {
        if (updateTasks.containsKey(player.getUniqueId())) {
            updateTasks.get(player.getUniqueId()).cancel();
        }

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || !player.getOpenInventory().getTopInventory().equals(playerMenu)) {
                    this.cancel();
                    updateTasks.remove(player.getUniqueId());
                    return;
                }
                refreshSkillMenu(player, skillGUI, skillType);
            }
        }.runTaskTimer(plugin, 0, 20);

        updateTasks.put(player.getUniqueId(), task);
    }

    private void hideVanillaStats(ItemMeta meta) {
        for (ItemFlag flag : ItemFlag.values()) {
            meta.addItemFlags(flag);
        }
    }


    public void cleanup(Player player) {
        if (updateTasks.containsKey(player.getUniqueId())) {
            updateTasks.get(player.getUniqueId()).cancel();
            updateTasks.remove(player.getUniqueId());
        }
    }
}
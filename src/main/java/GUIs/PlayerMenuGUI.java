package GUIs;

import Skills.SkillManager;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * Player Menu GUI — the main hub accessed via the hotbar skull.
 * Stateless: no live-update tasks, GUI refreshes on reopen.
 */
public class PlayerMenuGUI {

    private final JavaPlugin plugin;
    private final SkillManager skillManager;

    public PlayerMenuGUI(JavaPlugin plugin, SkillManager skillManager) {
        this.plugin = plugin;
        this.skillManager = skillManager;
    }

    // ── Hotbar item (used by PlayerSessionManager) ──────────────

    /**
     * Creates the player skull item placed in the hotbar.
     * Called by PlayerSessionManager to give/refresh the menu item.
     */
    public ItemStack getPlayerMenuItem(Player player) {
        int level = skillManager.getPlayerLevel(player);
        return GuiBuilder.createSkull(player,
                ChatColor.LIGHT_PURPLE + "Player Menu",
                List.of(
                        "",
                        ChatColor.GRAY + "Level: " + ChatColor.WHITE + level,
                        "",
                        ChatColor.GOLD + "Right-click to open your player menu."
                ));
    }

    /**
     * Checks whether an item is the Player Menu hotbar skull.
     * Used by the interaction listener to detect right-click opens.
     */
    public boolean isPlayerMenuHead(ItemStack item) {
        if (item == null || item.getType() != Material.PLAYER_HEAD || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta.hasDisplayName()
                && meta.getDisplayName().equals(ChatColor.LIGHT_PURPLE + "Player Menu");
    }

    // ── Main Player Menu (54 slots) ─────────────────────────────

    /**
     * Opens the main player menu. Folia-safe.
     */
    public void open(Player player) {
        Inventory gui = GuiBuilder.createGui(GuiIdentity.PLAYER_MENU, 6,
                ChatColor.DARK_PURPLE.toString() + ChatColor.BOLD + "Player Menu");

        GuiBuilder.fillBorders(gui, Material.BLACK_STAINED_GLASS_PANE);

        // Slot 4: Player head with level info
        int level = skillManager.getPlayerLevel(player);
        gui.setItem(4, GuiBuilder.createSkull(player,
                ChatColor.LIGHT_PURPLE + player.getName(),
                List.of(
                        "",
                        ChatColor.GRAY + "Level: " + ChatColor.WHITE + level,
                        ""
                )));

        // Row 3: primary actions
        gui.setItem(20, GuiBuilder.createActionItem(Material.NETHERITE_SWORD,
                ChatColor.DARK_RED + "Skills",
                "open_skills", null,
                ChatColor.GRAY + "View and manage your skills."));

        gui.setItem(22, GuiBuilder.createActionItem(Material.CRAFTING_TABLE,
                ChatColor.GREEN + "Recipes",
                "coming_soon", "Recipes",
                ChatColor.GRAY + "Discover crafting recipes."));

        gui.setItem(24, GuiBuilder.createActionItem(Material.CHEST,
                ChatColor.GOLD + "Collections",
                "coming_soon", "Collections",
                ChatColor.GRAY + "Track your collected items."));

        // Row 4: secondary actions
        gui.setItem(29, GuiBuilder.createActionItem(Material.BOOK,
                ChatColor.DARK_BLUE + "General",
                "coming_soon", "General",
                ChatColor.GRAY + "Character stats and features."));

        gui.setItem(31, GuiBuilder.createActionItem(Material.DRAGON_EGG,
                ChatColor.DARK_PURPLE + "Mobs & Monsters",
                "coming_soon", "Mobs & Monsters",
                ChatColor.GRAY + "Learn about creatures."));

        gui.setItem(33, GuiBuilder.createActionItem(Material.COMPASS,
                ChatColor.DARK_AQUA + "World Map",
                "coming_soon", "World Map",
                ChatColor.GRAY + "Explore locations."));

        // Row 5: equipment
        gui.setItem(38, GuiBuilder.createActionItem(Material.NETHERITE_CHESTPLATE,
                ChatColor.YELLOW + "Armor",
                "coming_soon", "Armor",
                ChatColor.GRAY + "Quick-swap your armor."));

        GuiBuilder.fillEmpty(gui, Material.BLACK_STAINED_GLASS_PANE);

        // Folia-safe open
        player.getScheduler().run(plugin, task -> player.openInventory(gui), null);
    }

    // ── Skill Management sub-menu (27 slots) ────────────────────

    /**
     * Opens the skill category selection menu. Folia-safe.
     */
    public void openSkillManagement(Player player) {
        Inventory gui = GuiBuilder.createGui(GuiIdentity.SKILL_MANAGEMENT, 3,
                ChatColor.DARK_PURPLE.toString() + ChatColor.BOLD + "Skill Management");

        GuiBuilder.fillBorders(gui, Material.BLACK_STAINED_GLASS_PANE);

        gui.setItem(11, GuiBuilder.createActionItem(Material.DIAMOND_SWORD,
                ChatColor.RED + "Combat Skills",
                "open_combat", null,
                ChatColor.GRAY + "Master your combat abilities."));

        gui.setItem(13, GuiBuilder.createActionItem(Material.ENCHANTED_BOOK,
                ChatColor.DARK_PURPLE + "Magic Skills",
                "open_magic", null,
                ChatColor.GRAY + "Harness magical powers."));

        gui.setItem(15, GuiBuilder.createActionItem(Material.DIAMOND_PICKAXE,
                ChatColor.GREEN + "Utility Skills",
                "open_utility", null,
                ChatColor.GRAY + "Develop practical skills."));

        gui.setItem(22, GuiBuilder.createBackArrow("player_menu"));

        GuiBuilder.fillEmpty(gui, Material.BLACK_STAINED_GLASS_PANE);

        // Folia-safe open
        player.getScheduler().run(plugin, task -> player.openInventory(gui), null);
    }

    // ── Convenience back-arrow delegate ─────────────────────────

    private ItemStack createBackArrow(String target) {
        return GuiBuilder.createBackArrow(target);
    }
}

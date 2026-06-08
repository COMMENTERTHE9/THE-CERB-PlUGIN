package Listener;

import GUIs.*;
import GUIs.SkillDefinitions.SkillCategory;
import Skills.SkillManager;
import cerberus.world.cerb.CerberusPlugin;
import cerberus.world.cerb.CustomPlayer;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Single click handler for ALL Cerberus GUIs.
 * Routes by {@link GuiIdentity} enum carried in the inventory holder,
 * reads PDC action/data tags from clicked items, and dispatches to
 * the appropriate handler method.
 */
public class UnifiedGUIListener implements Listener {

    private final CerberusPlugin plugin;
    private final SkillManager skillManager;
    private final SkillGUI skillGUI;
    private final SkillDetailGUI skillDetailGUI;
    private final PlayerMenuGUI playerMenuGUI;
    private final AdminChestGUI adminChestGUI;

    public UnifiedGUIListener(CerberusPlugin plugin,
                              SkillManager skillManager,
                              SkillGUI skillGUI,
                              SkillDetailGUI skillDetailGUI,
                              PlayerMenuGUI playerMenuGUI,
                              AdminChestGUI adminChestGUI) {
        this.plugin = plugin;
        this.skillManager = skillManager;
        this.skillGUI = skillGUI;
        this.skillDetailGUI = skillDetailGUI;
        this.playerMenuGUI = playerMenuGUI;
        this.adminChestGUI = adminChestGUI;
    }

    // ── Click handler ───────────────────────────────────────────────

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof GuiIdentity.GuiHolder holder)) return;
        event.setCancelled(true); // BEFORE routing -- items never leave our GUIs

        if (!(event.getWhoClicked() instanceof Player player)) return;

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        // Read PDC tags from clicked item
        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) return;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String action = pdc.getOrDefault(GuiIdentity.CERB_ACTION, PersistentDataType.STRING, "");
        String data = pdc.getOrDefault(GuiIdentity.CERB_DATA, PersistentDataType.STRING, "");

        if (action.isEmpty()) return; // decorative item, no-op

        switch (holder.identity()) {
            case PLAYER_MENU         -> handlePlayerMenu(player, action, data);
            case SKILL_MANAGEMENT    -> handleSkillManagement(player, action, data);
            case SKILL_COMBAT,
                 SKILL_MAGIC,
                 SKILL_UTILITY       -> handleSkillCategory(player, action, data);
            case SKILL_DETAIL        -> handleSkillDetail(player, action, data);
            case ADMIN_PANEL         -> handleAdminPanel(player, action, data);
            case ADMIN_COMMANDS      -> handleAdminCommands(player, action, data);
            case COMING_SOON         -> handleComingSoon(player, action, data);
        }
    }

    // ── Drag handler ────────────────────────────────────────────────

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof GuiIdentity.GuiHolder) {
            event.setCancelled(true);
        }
    }

    // ── Close handler (future-proofing) ─────────────────────────────

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof GuiIdentity.GuiHolder holder)) return;
        if (event.getPlayer() instanceof Player player) {
            BorderAnimator.stop(player);
        }
    }

    // ── Route: Player Menu ──────────────────────────────────────────

    private void handlePlayerMenu(Player player, String action, String data) {
        switch (action) {
            case "open_skills" -> playerMenuGUI.openSkillManagement(player);
            case "open_recipes",
                 "open_collections",
                 "open_features",
                 "open_bestiary",
                 "open_map",
                 "open_armor" -> {
                String featureName = switch (action) {
                    case "open_recipes"     -> "Recipes";
                    case "open_collections" -> "Collections";
                    case "open_features"    -> "Player Features";
                    case "open_bestiary"    -> "Bestiary";
                    case "open_map"         -> "World Map";
                    case "open_armor"       -> "Armor Manager";
                    default                 -> "Unknown";
                };
                openFolia(player, () ->
                        new ComingSoonGUI(plugin).open(player, featureName,
                                "This feature is under development."));
            }
            default -> {} // unknown action, ignore
        }
    }

    // ── Route: Skill Management ─────────────────────────────────────

    private void handleSkillManagement(Player player, String action, String data) {
        switch (action) {
            case "open_combat" -> skillGUI.openCategoryMenu(player, SkillCategory.COMBAT);
            case "open_magic" -> skillGUI.openCategoryMenu(player, SkillCategory.MAGIC);
            case "open_utility" -> skillGUI.openCategoryMenu(player, SkillCategory.UTILITY);
            case "back" -> playerMenuGUI.open(player);
            default -> {}
        }
    }

    // ── Route: Skill Category (Combat / Magic / Utility) ────────────

    private void handleSkillCategory(Player player, String action, String data) {
        switch (action) {
            case "open_skill_detail" -> {
                if (data != null && !data.isEmpty()) {
                    skillDetailGUI.open(player, data);
                }
            }
            case "back" -> playerMenuGUI.openSkillManagement(player);
            default -> {}
        }
    }

    // ── Route: Skill Detail ─────────────────────────────────────────

    private void handleSkillDetail(Player player, String action, String data) {
        switch (action) {
            case "upgrade" -> {
                if (data != null && !data.isEmpty()) {
                    skillManager.addXpAndCheckLevelUp(data, 10, player);
                    skillDetailGUI.open(player, data); // reopen with updated values
                }
            }
            case "back" -> {
                if (data != null && !data.isEmpty()) {
                    try {
                        SkillCategory cat = SkillCategory.valueOf(data.toUpperCase());
                        skillGUI.openCategoryMenu(player, cat);
                    } catch (IllegalArgumentException e) {
                        playerMenuGUI.openSkillManagement(player);
                    }
                } else {
                    playerMenuGUI.openSkillManagement(player);
                }
            }
            default -> {}
        }
    }

    // ── Route: Admin Panel ──────────────────────────────────────────

    private void handleAdminPanel(Player player, String action, String data) {
        switch (action) {
            case "open_regions" -> openFolia(player, () ->
                    new ComingSoonGUI(plugin).open(player, "Region Management",
                            "Region management has been removed."));
            case "open_commands" -> openFolia(player, () ->
                    openAdminCommandsMenu(player));
            case "open_players" -> openFolia(player, () ->
                    new ComingSoonGUI(plugin).open(player, "Player Management",
                            "Admin player management tools coming soon."));
            case "back" -> player.closeInventory();
            default -> {}
        }
    }

    // ── Route: Admin Commands ───────────────────────────────────────

    private void handleAdminCommands(Player player, String action, String data) {
        switch (action) {
            case "give_player_menu" -> {
                adminChestGUI.givePlayerMenu(player);
            }
            case "back" -> openFolia(player, () ->
                    openAdminPanelMenu(player));
            default -> {}
        }
    }

    // ── Route: Coming Soon ──────────────────────────────────────────

    private void handleComingSoon(Player player, String action, String data) {
        switch (action) {
            case "back" -> {
                if ("player_menu".equals(data)) {
                    playerMenuGUI.open(player);
                } else {
                    player.closeInventory();
                }
            }
            default -> {}
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  GUI construction methods (admin — will be moved to GUI classes in Phase 5)
    // ═══════════════════════════════════════════════════════════════

    /** Open the admin control panel. */
    private void openAdminPanelMenu(Player player) {
        Inventory inv = GuiBuilder.createGui(GuiIdentity.ADMIN_PANEL, 3,
                ChatColor.DARK_PURPLE + "Admin Control Panel");

        inv.setItem(10, GuiBuilder.createActionItem(Material.BOOK,
                ChatColor.GOLD + "Manage Regions",
                "open_regions", null,
                ChatColor.GRAY + "View and manage regions"));

        inv.setItem(12, GuiBuilder.createActionItem(Material.COMMAND_BLOCK,
                ChatColor.RED + "Server Commands",
                "open_commands", null,
                ChatColor.GRAY + "Execute server commands"));

        inv.setItem(14, GuiBuilder.createActionItem(Material.PLAYER_HEAD,
                ChatColor.BLUE + "Manage Players",
                "open_players", null,
                ChatColor.GRAY + "Manage player settings"));

        inv.setItem(16, GuiBuilder.createActionItem(Material.BARRIER,
                ChatColor.RED + "Close",
                "back", null,
                ChatColor.GRAY + "Close the Admin Control Panel"));

        GuiBuilder.fillEmpty(inv, Material.GRAY_STAINED_GLASS_PANE);
        player.openInventory(inv);
    }

    /** Open the server commands sub-menu. */
    private void openAdminCommandsMenu(Player player) {
        Inventory inv = GuiBuilder.createGui(GuiIdentity.ADMIN_COMMANDS, 3,
                ChatColor.RED + "Server Commands");

        inv.setItem(10, GuiBuilder.createActionItem(Material.PLAYER_HEAD,
                ChatColor.DARK_PURPLE + "Give Player Menu",
                "give_player_menu", null,
                ChatColor.GRAY + "Gives the Player Menu item to hotbar slot 9"));

        inv.setItem(16, GuiBuilder.createBackArrow("admin_panel"));

        GuiBuilder.fillEmpty(inv, Material.GRAY_STAINED_GLASS_PANE);
        player.openInventory(inv);
    }

    // ── Helpers ──────────────────────────────────────────────────────

    /**
     * Wraps {@code player.openInventory()} in the player's Folia scheduler
     * so it runs on the player's region thread.
     */
    private void openFolia(Player player, Runnable task) {
        player.getScheduler().run(plugin, (ScheduledTask t) -> task.run(), null);
    }

}

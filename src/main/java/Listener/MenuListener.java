package Listener;

import GUIs.PlayerMenuGUI;
import GUIs.SkillGUI;
import Skills.SkillManager;
import cerberus.world.cerb.CerberusPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.EventHandler;

import org.bukkit.event.Listener;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.Arrays;

public class MenuListener implements Listener {

    private final PlayerMenuGUI playerMenuGUI;
    private final SkillGUI skillGUI;
    private final SkillManager skillManager;
    private final CerberusPlugin plugin;          // ← NEW


    public MenuListener(CerberusPlugin plugin, PlayerMenuGUI playerMenuGUI, SkillGUI skillGUI, SkillManager skillManager) {
        this.plugin        = plugin;
        this.playerMenuGUI = playerMenuGUI;
        this.skillGUI = skillGUI;
        this.skillManager = skillManager;
    }

    // ------------------------------------------------------------
    // Handle clicks inside the player menu / skill GUIs
    // ------------------------------------------------------------
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;            // EARLY EXIT
        Inventory inv = event.getInventory();
        String title = event.getView().getTitle();

        // Only care about our custom menus
        if (!title.equals(ChatColor.DARK_PURPLE + "Player Menu") &&
                !title.equals(ChatColor.RED + "Skill Management") &&
                !title.equals(ChatColor.RED + "Combat Skills") &&
                !title.equals(ChatColor.BLUE + "Magic Skills") &&
                !title.equals(ChatColor.GREEN + "Utility Skills")) {
            return;
        }

        event.setCancelled(true);  // lock GUI
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;                   // EARLY EXIT
        String itemName = clicked.getItemMeta().getDisplayName();

        // Player Menu
        if (title.equals(ChatColor.DARK_PURPLE + "Player Menu")) {
            if (itemName.equals(ChatColor.DARK_RED + "Skills")) {
                playerMenuGUI.openSkillManagementMenu(player, skillGUI);
            } else if (itemName.equals(ChatColor.GREEN + "All Skills")) {
                skillGUI.show(player, "all");
            }
        }

        // Skill Management
        else if (title.equals(ChatColor.RED + "Skill Management")) {
            if (itemName.equals(ChatColor.RED + "Combat Skills")) {
                skillGUI.show(player, "combat");
            } else if (itemName.equals(ChatColor.BLUE + "Magic Skills")) {
                skillGUI.show(player, "magic");
            } else if (itemName.equals(ChatColor.GREEN + "Utility Skills")) {
                skillGUI.show(player, "utility");
            } else if (itemName.equals(ChatColor.YELLOW + "Back")) {
                playerMenuGUI.openMainMenu(player);
            }
        }

        // Combat / Magic / Utility Skills menus share same back & upgrade logic
        else {
            // Back button
            if (itemName.equals(ChatColor.YELLOW + "Back")) {
                playerMenuGUI.openSkillManagementMenu(player, skillGUI);
                return;
            }
            // Upgrade chosen skill
            String skillName = ChatColor.stripColor(itemName.split(" \\(")[0]);
            skillManager.upgradeSkill(player, skillName);

            // Refresh the correct tab
            if (title.contains("Combat")) skillGUI.show(player, "combat");
            else if (title.contains("Magic"))  skillGUI.show(player, "magic");
            else if (title.contains("Utility"))skillGUI.show(player, "utility");
        }
    }

    // ------------------------------------------------------------
    // Prevent dragging items in our menus
    // ------------------------------------------------------------
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        String title = event.getView().getTitle();
        if (title.equals(ChatColor.DARK_PURPLE + "Player Menu") ||
                title.equals(ChatColor.RED + "Skill Management") ||
                title.equals(ChatColor.RED + "Combat Skills") ||
                title.equals(ChatColor.BLUE + "Magic Skills") ||
                title.equals(ChatColor.GREEN + "Utility Skills")) {
            event.setCancelled(true);
        }
    }
    // ------------------------------------------------------------
    // Prevent hoppers or other inventories moving items out
    // ------------------------------------------------------------
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInventoryMoveItem(InventoryMoveItemEvent event) {
        if (event.getInitiator().getHolder() instanceof Player player) {
            String title = player.getOpenInventory().getTitle();
            if (title.equals(ChatColor.DARK_PURPLE + "Player Menu") ||
                    title.equals(ChatColor.RED + "Skill Management") ||
                    title.equals(ChatColor.RED + "Combat Skills") ||
                    title.equals(ChatColor.BLUE + "Magic Skills") ||
                    title.equals(ChatColor.GREEN + "Utility Skills")) {
                event.setCancelled(true);
            }
        }
    }

    // ------------------------------------------------------------
    // Give the player their menu head on join
    // ------------------------------------------------------------
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        ItemStack head = createPlayerMenuItem(player);
        player.getInventory().setItem(8, head);
        player.sendMessage(ChatColor.GREEN + "Use the Player Menu (slot 9) to access your skills!");
    }

    private ItemStack createPlayerMenuItem(Player player) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD, 1);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        meta.setOwningPlayer(player);
        meta.setDisplayName(ChatColor.LIGHT_PURPLE + "Player Menu");
        meta.setLore(Arrays.asList(ChatColor.GOLD + "Right‑click to open"));
        head.setItemMeta(meta);
        return head;
    }

    // ------------------------------------------------------------
    // Award XP and refresh GUI on mob kill
    // ------------------------------------------------------------
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMobDeathByDamage(EntityDamageByEntityEvent e) {
        // 1) Figure out who the *player* damager is
        Player killer = null;
        if (e.getDamager() instanceof Player p) {
            killer = p;
        } else if (e.getDamager() instanceof Projectile proj
                && proj.getShooter() instanceof Player p2) {
            killer = p2;
        }
        if (killer == null) return;

        // 2) Check that the victim is a LivingEntity
        if (!(e.getEntity() instanceof LivingEntity victim)) return;

        // 3) Compute final‐blow damage & max health
        double damage = e.getFinalDamage();
        double maxHp  = victim.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();

        // 4) Only proceed if this hit actually kills them
        if (victim.getHealth() - damage > 0) return;

        // 5) Determine rarity/boss status
        boolean rare = victim.getCustomName() != null
                || victim.getType() == EntityType.WITHER_SKELETON;
        boolean boss = victim.getType() == EntityType.ENDER_DRAGON
                || victim.getType() == EntityType.WITHER;

        // 6) Award XP
        plugin.getSkillManager()
                .addXpForMobKill(killer,
                        "Combat",
                        maxHp,
                        damage,
                        rare,
                        boss);
        killer.sendMessage(ChatColor.GREEN
                + "You gained XP for defeating " + victim.getType().name() + "!");

        // 7) Refresh the combat tab in the menu
        plugin.getPlayerMenuGUI()
                .refreshSkillMenu(killer, plugin.getSkillGUI(), "combat");
    }
}
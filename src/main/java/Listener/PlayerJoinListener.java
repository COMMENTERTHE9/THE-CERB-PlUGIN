package Listener;

import Skills.SkillManager;
import cerberus.world.cerb.CerberusPlugin;
import cerberus.world.cerb.DatabaseManager;
import GUIs.SkillGUI;
import GUIs.PlayerMenuGUI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;


import java.util.Map;
import java.util.UUID;

public class PlayerJoinListener implements Listener {
    private final SkillManager skillManager;
    private final DatabaseManager databaseManager;
    private final CerberusPlugin plugin;
    private final SkillGUI skillGUI;
    private final PlayerMenuGUI playerMenuGUI;

    public PlayerJoinListener(SkillManager skillManager,
                              DatabaseManager databaseManager,
                              CerberusPlugin plugin,
                              SkillGUI skillGUI,
                              PlayerMenuGUI playerMenuGUI) {
        this.skillManager = skillManager;
        this.databaseManager = databaseManager;
        this.plugin = plugin;
        this.skillGUI = skillGUI;
        this.playerMenuGUI = playerMenuGUI;
    }


    // ---------------------------
// Player join → load & give menu head
// ---------------------------
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();

        Map<String,Integer> skillLevels = databaseManager.loadSkillLevels(playerUUID);
        Map<String,Integer> skillXP     = databaseManager.loadSkillXP   (playerUUID);


        // Update the player's skill levels and XP in SkillManager
        skillManager.setPlayerSkills(player, skillLevels, skillXP);

        // Add the player's head item to the inventory for menu access
        ItemStack playerMenuHead = playerMenuGUI.getPlayerMenuItem(player);
        player.getInventory().addItem(playerMenuHead);

        plugin.getLogger().info("Player " + playerUUID + " joined. Skills loaded from database.");
    }

    // ---------------------------
// Player interact → open skill GUI
// ---------------------------
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        // Only respond to right‑clicks
        if (event.getAction() != Action.RIGHT_CLICK_AIR
                && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack inHand = player.getInventory().getItemInMainHand();
        ItemStack menuHead = playerMenuGUI.getPlayerMenuItem(player);

        // EARLY EXIT if they’re not holding their menu head
        if (inHand == null || !inHand.isSimilar(menuHead)) return;

        // Open the “combat” tab as an example
        skillGUI.update(player, "combat");
    }

    // ---------------------------
// Player quit → save skills
// ---------------------------
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();

        // Retrieve the player's current skill data
        Map<String, Integer> skillLevels = skillManager.getPlayerSkillLevels(player);
        Map<String, Integer> skillXP = skillManager.getPlayerSkillXP(player);

        // Save skill data to the database
        String customId = databaseManager.getOrCreateCustomId(playerUUID, player.getName());
        databaseManager.saveSkillsByCustomId(customId, skillLevels, skillXP);
        
        // Clean up defense bar
        if (plugin.getDefenseBarManager() != null) {
            plugin.getDefenseBarManager().removePlayer(player);
        }

        plugin.getLogger().info("Player " + playerUUID + " left. Skills saved to database.");
    }
}

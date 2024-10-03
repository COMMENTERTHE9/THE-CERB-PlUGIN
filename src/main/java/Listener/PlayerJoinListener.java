package Listener;

import Skills.SkillManager;
import cerberus.world.cerb.cerb;
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

import java.util.Map;
import java.util.UUID;

public class PlayerJoinListener implements Listener {
    private final SkillManager skillManager;
    private final DatabaseManager databaseManager;
    private final cerb plugin;
    private final SkillGUI skillGUI;
    private final PlayerMenuGUI playerMenuGUI;

    // Constructor updated to accept all required dependencies
    public PlayerJoinListener(SkillManager skillManager, DatabaseManager databaseManager, cerb plugin, SkillGUI skillGUI, PlayerMenuGUI playerMenuGUI) {
        this.skillManager = skillManager;
        this.databaseManager = databaseManager;
        this.plugin = plugin;
        this.skillGUI = skillGUI;
        this.playerMenuGUI = playerMenuGUI;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();

        // Get or create the custom ID for the player
        String customId = databaseManager.getOrCreateCustomId(playerUUID, player.getName());

        // Load skill data using the customId
        Map<String, Integer> skillLevels = databaseManager.loadSkillLevelsByCustomId(customId);
        Map<String, Integer> skillXP = databaseManager.loadSkillXPByCustomId(customId);

        // Update the player's skill levels and XP in SkillManager
        skillManager.setPlayerSkills(player, skillLevels, skillXP);

        skillGUI.updatePlayerSkills(player, skillLevels, skillXP);


        // Add the player's head item to the inventory for menu access
        ItemStack playerMenuHead = playerMenuGUI.getPlayerMenuItem(player);
        player.getInventory().addItem(playerMenuHead);

        System.out.println("Player " + playerUUID + " joined. Skills loaded from database.");
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();

        // Check if the player interacted with their head (menu access)
        if (player.getInventory().getItemInMainHand().equals(playerMenuGUI.getPlayerMenuItem(player))) {
            skillGUI.update(player, "combat");  // Example: Open combat skills GUI
        }
    }

    // Handle player quitting the server and saving their skills
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();

        // Retrieve the player's current skill data
        Map<String, Integer> skillLevels = skillManager.getPlayerSkillLevels(player);
        Map<String, Integer> skillXP = skillManager.getPlayerSkillXP(player);

        // Save skill data to the database
        String customId = databaseManager.getOrCreateCustomId(playerUUID, player.getName());
        databaseManager.saveSkillsByCustomId(customId, skillLevels, skillXP);

        System.out.println("Player " + playerUUID + " left. Skills saved to database.");
    }

    // Save all players' skills to the database when called
    public void saveAllPlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID playerUUID = player.getUniqueId();
            Map<String, Integer> skillLevels = skillManager.getPlayerSkillLevels(player);
            Map<String, Integer> skillXP = skillManager.getPlayerSkillXP(player);

            // Save skill data to the database
            String customId = databaseManager.getOrCreateCustomId(playerUUID, player.getName());
            databaseManager.saveSkillsByCustomId(customId, skillLevels, skillXP);
        }

        System.out.println("All players' skills saved to the database.");
    }
}

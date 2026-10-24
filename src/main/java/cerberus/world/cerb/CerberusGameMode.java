package cerberus.world.cerb;

import Manager.PlayerVirtualHealthManager;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;


/**
 * Handles Cerberus game-mode setup for players (first join and restore).
 * Instance-based — no static initializer that crashes on early class load.
 */
public class CerberusGameMode {

    private final PlayerVirtualHealthManager virtualHealthManager;
    private final CerberusPlugin plugin;

    public CerberusGameMode(PlayerVirtualHealthManager virtualHealthManager, CerberusPlugin plugin) {
        this.virtualHealthManager = virtualHealthManager;
        this.plugin = plugin;
    }

    /** Set up a brand-new player with default virtual health. */
    public void setupPlayerForFirstTime(Player player, FileConfiguration playerDataConfig, String playerUUID) {
        virtualHealthManager.setMaxHealth(player, 100.0);
        player.setFoodLevel(20);

        // One shared YamlConfiguration is touched from every joining player's region thread
        // (Folia): mutate under its lock, and save off-thread via the coalescing saver (which
        // takes the same lock while writing) instead of blocking the join tick on disk I/O.
        synchronized (playerDataConfig) {
            playerDataConfig.set(playerUUID + ".joinedBefore", true);
        }
        plugin.savePlayerDataConfig();
    }

    /** Restore player virtual health (clamp to max). */
    public void restorePlayerHealth(Player player) {
        double maxHealth = virtualHealthManager.getMaxHealth(player);
        double current = virtualHealthManager.getPlayerVirtualHealth(player);
        if (current > maxHealth) {
            virtualHealthManager.setPlayerVirtualHealth(player, maxHealth);
        }
    }

    /** Check if the player has joined before. */
    public boolean hasPlayerJoinedBefore(Player player, FileConfiguration playerDataConfig) {
        String playerUUID = player.getUniqueId().toString();
        synchronized (playerDataConfig) {
            return playerDataConfig.getBoolean(playerUUID + ".joinedBefore", false);
        }
    }

    /** Enable Cerberus Game Mode for a player (first join or restore). */
    public void enable(Player player, FileConfiguration playerDataConfig) {
        String playerUUID = player.getUniqueId().toString();
        if (!hasPlayerJoinedBefore(player, playerDataConfig)) {
            setupPlayerForFirstTime(player, playerDataConfig, playerUUID);
            player.sendMessage("Welcome to Cerberus Mode! Your virtual health and hunger have been maximized.");
        } else {
            restorePlayerHealth(player);
        }
    }
}

package cerberus.world.cerb;

import Manager.PlayerVirtualHealthManager;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;

public class CerberusGameMode {

    private static final PlayerVirtualHealthManager virtualHealthManager = CerberusPlugin.getInstance().getPlayerVirtualHealthManager();

    // Method to set up a player for the first time
    public static void setupPlayerForFirstTime(Player player, FileConfiguration playerDataConfig, String playerUUID) {
        // Set initial virtual health to 100
        virtualHealthManager.setMaxHealth(player, 100.0); // Set the initial max virtual health
        virtualHealthManager.setPlayerVirtualHealth(player, 100.0); // Fully heal the player with virtual health
        player.setFoodLevel(20); // Max out food level

        // Mark the player as having joined before in playerData.yml
        playerDataConfig.set(playerUUID + ".joinedBefore", true);
        savePlayerData(playerDataConfig, playerUUID); // Save the data to the file
    }

    // Method to restore player virtual health
    public static void restorePlayerHealth(Player player) {
        double maxHealth = virtualHealthManager.getMaxHealth(player);

        // Ensure the player's virtual health is set to the max value
        if (virtualHealthManager.getPlayerVirtualHealth(player) > maxHealth) {
            virtualHealthManager.setPlayerVirtualHealth(player, maxHealth);
        } else {
            virtualHealthManager.setPlayerVirtualHealth(player, virtualHealthManager.getPlayerVirtualHealth(player));
        }
    }

    // Method to check if the player has joined before
    public static boolean hasPlayerJoinedBefore(Player player, FileConfiguration playerDataConfig) {
        String playerUUID = player.getUniqueId().toString();
        return playerDataConfig.getBoolean(playerUUID + ".joinedBefore", false);
    }

    // Method to save player data to the file
    private static void savePlayerData(FileConfiguration playerDataConfig, String playerUUID) {
        try {
            playerDataConfig.save(new File(CerberusPlugin.getInstance().getDataFolder(), "playerData.yml"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Method to enable Cerberus Game Mode for a player
    public static void enable(Player player, FileConfiguration playerDataConfig) {
        String playerUUID = player.getUniqueId().toString();

        // Check if the player is joining for the first time
        if (!hasPlayerJoinedBefore(player, playerDataConfig)) {
            setupPlayerForFirstTime(player, playerDataConfig, playerUUID);
            player.sendMessage("Welcome to Cerberus Mode! Your virtual health and hunger have been maximized.");
        } else {
            restorePlayerHealth(player);
        }
    }
}

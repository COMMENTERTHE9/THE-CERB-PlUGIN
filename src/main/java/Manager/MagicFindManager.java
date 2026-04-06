package Manager;

import cerberus.world.cerb.DatabaseManager;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MagicFindManager {

    private final DatabaseManager databaseManager;
    private final Map<UUID, Double> playerMagicFindMap;

    public MagicFindManager(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
        this.playerMagicFindMap = new HashMap<>();
    }

    // Method to increase the player's magic find and persist to database
    public void increaseMagicFind(Player player, double amount) {
        UUID playerUUID = player.getUniqueId();
        double newMagicFind = getMagicFind(player) + amount;
        playerMagicFindMap.put(playerUUID, newMagicFind);
        databaseManager.saveMagicFind(playerUUID, newMagicFind);
    }

    // Method to decrease the player's magic find and persist to database
    public void decreaseMagicFind(Player player, double amount) {
        UUID playerUUID = player.getUniqueId();
        double currentMagicFind = getMagicFind(player);
        double newMagicFind = currentMagicFind - amount;
        if (newMagicFind < 0) {
            newMagicFind = 0; // Ensure magic find doesn't go below zero
        }
        playerMagicFindMap.put(playerUUID, newMagicFind);
        databaseManager.saveMagicFind(playerUUID, newMagicFind);
    }

    // Method to get the current magic find of a player
    public double getMagicFind(Player player) {
        UUID playerUUID = player.getUniqueId();

        // First, check the in-memory cache
        if (playerMagicFindMap.containsKey(playerUUID)) {
            return playerMagicFindMap.get(playerUUID);
        }

        // If not found, load from the database
        double magicFind = databaseManager.loadMagicFind(playerUUID);
        playerMagicFindMap.put(playerUUID, magicFind);  // Cache the result in memory
        return magicFind;
    }

    // Method to set the magic find directly and persist to the database
    public void setMagicFind(Player player, double amount) {
        UUID playerUUID = player.getUniqueId();
        if (amount < 0) {
            amount = 0; // Ensure magic find doesn't go below zero
        }
        playerMagicFindMap.put(playerUUID, amount);
        databaseManager.saveMagicFind(playerUUID, amount);
    }

    // Method to reset a player's magic find to zero and persist to the database
    public void resetMagicFind(Player player) {
        UUID playerUUID = player.getUniqueId();
        playerMagicFindMap.put(playerUUID, 0.0);
        databaseManager.saveMagicFind(playerUUID, 0.0);
    }

}

package Manager;

import cerberus.world.cerb.DatabaseManager;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
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
        saveMagicFindToDatabase(playerUUID, newMagicFind);
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
        saveMagicFindToDatabase(playerUUID, newMagicFind);
    }

    // Method to get the current magic find of a player
    public double getMagicFind(Player player) {
        UUID playerUUID = player.getUniqueId();

        // First, check the in-memory cache
        if (playerMagicFindMap.containsKey(playerUUID)) {
            return playerMagicFindMap.get(playerUUID);
        }

        // If not found, load from the database
        double magicFind = loadMagicFindFromDatabase(playerUUID);
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
        saveMagicFindToDatabase(playerUUID, amount);
    }

    // Method to reset a player's magic find to zero and persist to the database
    public void resetMagicFind(Player player) {
        UUID playerUUID = player.getUniqueId();
        playerMagicFindMap.put(playerUUID, 0.0);
        saveMagicFindToDatabase(playerUUID, 0.0);
    }

    // Save the magic find value to the database
    private void saveMagicFindToDatabase(UUID playerUUID, double magicFind) {
        String query = "REPLACE INTO player_magic_find (player_uuid, magic_find) VALUES (?, ?)";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, playerUUID.toString());
            statement.setDouble(2, magicFind);
            statement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Load the magic find value from the database
    private double loadMagicFindFromDatabase(UUID playerUUID) {
        String query = "SELECT magic_find FROM player_magic_find WHERE player_uuid = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, playerUUID.toString());
            ResultSet resultSet = statement.executeQuery();
            if (resultSet.next()) {
                return resultSet.getDouble("magic_find");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0.0; // Default to 0 if no record found
    }
}

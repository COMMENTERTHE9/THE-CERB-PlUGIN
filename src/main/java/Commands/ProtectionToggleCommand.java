package Commands;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.UUID;

public class ProtectionToggleCommand implements CommandExecutor {

    // <<< NEW: permission and usage
    private static final String PERM        = "cerberus.toggle.protection";
    private static final String USAGE       = ChatColor.YELLOW +
            "Usage: /toggleprotection [on|off]";

    // Stores each player's toggle state (true = enabled checking)
    private final HashMap<UUID, Boolean> protectionToggles = new HashMap<>();

    @Override
    public boolean onCommand(CommandSender sender,
                             Command command,
                             String label,
                             String[] args) {
        // Only players may toggle protection
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command.");
            return true;
        }

        // Permission check
        if (!player.hasPermission(PERM)) {
            player.sendMessage(ChatColor.RED + "You lack permission to toggle protection.");
            return true;
        }

        // Bad args?
        if (args.length > 1) {
            player.sendMessage(USAGE);
            return true;
        }

        try {
            UUID id = player.getUniqueId();
            boolean newState;

            if (args.length == 1) {
                String sub = args[0].toLowerCase();
                switch (sub) {
                    case "on":
                        newState = true;
                        break;
                    case "off":
                        newState = false;
                        break;
                    default:
                        player.sendMessage(ChatColor.RED + "Invalid option: " + args[0]);
                        player.sendMessage(USAGE);
                        return true;
                }
            } else {
                // No arg → toggle
                newState = !protectionToggles.getOrDefault(id, true);
            }

            protectionToggles.put(id, newState);

            // Inform the player
            if (newState) {
                player.sendMessage(ChatColor.GREEN +
                        "Region protection checking ENABLED. You will be blocked by protected regions.");
            } else {
                player.sendMessage(ChatColor.YELLOW +
                        "Region protection checking DISABLED. You may now modify protected regions.");
            }
        } catch (Exception ex) {
            Bukkit.getLogger().severe("Error toggling protection for " + player.getName() + ": " + ex);
            ex.printStackTrace();
            player.sendMessage(ChatColor.RED + "An internal error occurred; see console.");
        }

        return true;
    }

    /**
     * Returns true if this player’s protection‐checking is currently enabled.
     */
    public boolean isProtectionEnabled(UUID playerId) {
        return protectionToggles.getOrDefault(playerId, true);
    }
}

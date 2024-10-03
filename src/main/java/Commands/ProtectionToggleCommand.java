package Commands;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.UUID;

public class ProtectionToggleCommand implements CommandExecutor {
    private final HashMap<UUID, Boolean> protectionToggles = new HashMap<>();

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;
        if (!player.hasPermission("cerberus.toggle.protection")) {
            player.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }

        UUID playerId = player.getUniqueId();
        boolean newState = !protectionToggles.getOrDefault(playerId, true);
        protectionToggles.put(playerId, newState);

        String message = newState
                ? ChatColor.GREEN + "Protection checking enabled. You will now be affected by region protections."
                : ChatColor.YELLOW + "Protection checking disabled. You can now modify protected regions.";
        player.sendMessage(message);

        return true;
    }

    public boolean isProtectionEnabled(UUID playerId) {
        return protectionToggles.getOrDefault(playerId, true);
    }
}
package Commands;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

/**
 * Disabled — DecentHolograms removed for Folia compatibility.
 * Damage numbers use Paper's native TextDisplay API instead.
 */
public class KillHologramsCommand implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        sender.sendMessage(ChatColor.YELLOW + "This command is disabled — DecentHolograms has been removed.");
        return true;
    }
}

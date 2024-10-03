package Commands;

import Manager.PlayerVirtualHealthManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class TestRegenCommand implements CommandExecutor {

    private final PlayerVirtualHealthManager virtualHealthManager;

    public TestRegenCommand(PlayerVirtualHealthManager virtualHealthManager) {
        this.virtualHealthManager = virtualHealthManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be run by a player.");
            return true;
        }

        Player player = (Player) sender;

        if (args.length > 0) {
            try {
                double damageAmount = Double.parseDouble(args[0]);
                virtualHealthManager.decreasePlayerVirtualHealth(player, damageAmount);
                player.sendMessage(ChatColor.RED + "You have taken " + damageAmount + " damage!");

                // Start regeneration
                double normalRegenRate = 2.0; // Amount of health regenerated per tick normally
                double reducedRegenRate = 1.0; // Amount of health regenerated per tick while taking damage
                long duration = 20L * 10; // Regenerate over 10 seconds
                long damageTimeout = 5000L; // 5 seconds after taking damage to switch to normal regen

                virtualHealthManager.applyHealthRegen(player, normalRegenRate, reducedRegenRate, duration, damageTimeout);
                player.sendMessage(ChatColor.GREEN + "Regeneration has started!");

            } catch (NumberFormatException e) {
                player.sendMessage(ChatColor.RED + "Please provide a valid number for damage.");
            }
        } else {
            player.sendMessage(ChatColor.RED + "Usage: /testregen <damage>");
        }

        return true;
    }
}

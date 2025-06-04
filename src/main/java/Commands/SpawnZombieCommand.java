package Commands;

import CustomEntities.CustomHighHealthZombie;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class SpawnZombieCommand implements CommandExecutor {

    private final JavaPlugin plugin;

    public SpawnZombieCommand(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            player.sendMessage("Usage: /spawncustomzombie <extraHealth>");
            return false;
        }

        int extraHealth;
        try {
            extraHealth = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            player.sendMessage("Please provide a valid number for extra health.");
            return false;
        }

        CustomHighHealthZombie.spawn(player.getLocation(), extraHealth);
        player.sendMessage("Spawned a custom high-health zombie!");

        return true;
    }
}

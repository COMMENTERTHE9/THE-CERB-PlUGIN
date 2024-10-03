package Commands;

import eu.decentsoftware.holograms.api.DHAPI;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public class KillHologramsCommand implements CommandExecutor {

    private final JavaPlugin plugin;

    public KillHologramsCommand(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Prefixes we want to remove
        String[] prefixes = {"damage-", "health-"};

        int count = 0;
        for (String prefix : prefixes) {
            for (int i = 0; i < 10000; i++) { // Assuming you won't have more than 10,000 holograms
                String hologramId = prefix + i;
                if (DHAPI.getHologram(hologramId) != null) {
                    DHAPI.removeHologram(hologramId);
                    count++;
                }
            }
        }

        sender.sendMessage("§aRemoved " + count + " damage/health holograms.");
        return true;
    }
}

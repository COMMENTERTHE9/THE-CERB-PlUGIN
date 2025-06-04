package Commands;

import eu.decentsoftware.holograms.api.DHAPI;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public class KillHologramsCommand implements CommandExecutor {

    private final JavaPlugin plugin;

    // <<< NEW: usage & permission constants
    private static final String USAGE =
            ChatColor.YELLOW + "Usage: /killholograms [damage|health|all]";
    private static final String PERM = "cerberus.admin.killholograms";

    public KillHologramsCommand(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender,
                             Command command,
                             String label,
                             String[] args) {

        // <<< NEW: permission check
        if (!sender.hasPermission(PERM)) {
            sender.sendMessage(ChatColor.RED + "You lack permission to run this command.");
            return true;
        }

        // <<< NEW: argument validation
        if (args.length > 1) {
            sender.sendMessage(USAGE);
            return true;
        }

        // <<< NEW: determine which prefixes to purge
        List<String> prefixes = new ArrayList<>();
        String mode = (args.length == 0 ? "all" : args[0].toLowerCase());

        switch (mode) {
            case "damage":
                prefixes.add("damage-");
                break;
            case "health":
                prefixes.add("health-");
                break;
            case "all":
                prefixes.add("damage-");
                prefixes.add("health-");
                break;
            default:
                sender.sendMessage(ChatColor.RED + "Invalid option: " + args[0]);
                sender.sendMessage(USAGE);
                return true;
        }

        int removed = 0;
        try {
            // <<< IMPROVED: scan IDs dynamically if API supports it
            // Here we fallback to the 0–9999 loop
            for (String prefix : prefixes) {
                for (int i = 0; i < 10000; i++) {
                    String id = prefix + i;
                    if (DHAPI.getHologram(id) != null) {
                        DHAPI.removeHologram(id);
                        removed++;
                    }
                }
            }
        } catch (Exception ex) {
            Bukkit.getLogger().severe("Error removing holograms: " + ex.getMessage());
            ex.printStackTrace();
            sender.sendMessage(ChatColor.RED + "An error occurred. See console.");
            return true;
        }

        sender.sendMessage(ChatColor.GREEN + "Removed " + removed + " hologram(s) [" + mode + "].");
        return true;
    }
}

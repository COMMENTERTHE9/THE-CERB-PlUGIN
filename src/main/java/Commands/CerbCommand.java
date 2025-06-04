package Commands;

import cerberus.world.cerb.CerberusPlugin;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class CerbCommand implements CommandExecutor {

    private static final String USAGE =
            ChatColor.YELLOW + "Usage: /cerb <reload|backup|debug|status|heal|mana|skill|resetdata|"
                    + "banregion|region|protection|worldgen|repairchunk|quest|event|teleportall|"
                    + "timings|profiler|listentities|showqueues>";
    private final CerberusPlugin plugin;

    public CerbCommand(CerberusPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender,
                             Command command,
                             String label,
                             String[] args) {
        // only players with the cerb.admin node can do anything
        if (!(sender instanceof Player) || !sender.hasPermission("cerb.admin")) {
            sender.sendMessage(ChatColor.RED + "You lack permission.");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(USAGE);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            // 1. Server & Configuration
            case "reload":
                // <<< NEW: reload everything
                plugin.reloadAll();
                sender.sendMessage(ChatColor.GREEN + "Reload complete.");
                break;

            case "backup":
                // <<< NEW: trigger async backup of all player data
                plugin.saveAllPlayers();
                sender.sendMessage(ChatColor.GREEN + "Backup started.");
                break;

            case "debug":
                // <<< NEW: toggle debug
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Use: /cerb debug <on|off>");
                } else {
                    plugin.toggleDebug(args[1].equalsIgnoreCase("on"));
                    sender.sendMessage(ChatColor.AQUA + "Debug: " + args[1]);
                }
                break;

            case "status":
                // <<< NEW: print runtime stats
                plugin.printStatus(sender);
                break;


            // 2. Player Management
            case "heal":
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Use: /cerb heal <player>");
                } else {
                    plugin.healPlayer(sender, args[1]);        // <<< FIXED: pass sender + name
                }
                break;

            case "mana":
                plugin.adjustMana(sender, args);               // <<< FIXED
                break;

            case "skill":
                plugin.adjustSkill(sender, args);              // <<< FIXED
                break;

            case "resetdata":
                plugin.resetPlayerData(sender, args);          // <<< FIXED
                break;

            case "banregion":
                plugin.banRegionForPlayer(sender, args);       // <<< FIXED
                break;


            // 3. World & Region Control
            case "region":
                // <<< NEW: /cerb region sub‑commands (reload/clear)
                plugin.handleRegionSubcommand(sender, args);
                break;

            case "protection":
                // <<< NEW: list protection toggles
                plugin.listProtections(sender);
                break;

            case "worldgen":
                // <<< NEW: toggle chunk‐populate hooks
                plugin.toggleWorldGen(sender, args);
                break;

            case "repairchunk":
                // <<< NEW: run nature auto‑repair in given chunk
                plugin.repairChunk(sender, args);
                break;


            // 4. Event & Quest Control
            case "quest":
                // <<< NEW: assign region objectives
                plugin.giveQuest(sender, args);
                break;

            case "event":
                // <<< NEW: start/stop dynamic event
                plugin.controlEvent(sender, args);
                break;

            case "teleportall":
                // <<< NEW: move everyone to region
                plugin.teleportAllToRegion(sender, args);
                break;


            // 5. Debug & Diagnostics
            case "timings":
                // <<< NEW: start/stop Bukkit timings
                plugin.handleTimings(sender, args);
                break;

            case "profiler":
                // <<< NEW: trigger Spark snapshot
                plugin.triggerProfiler(sender, args);
                break;

            case "listentities":
                // <<< NEW: show nearby entity counts
                plugin.listEntitiesAround(sender, args);
                break;

            case "showqueues":
                // <<< NEW: show scheduled tasks & HUD timers
                plugin.showQueues(sender);
                break;


            default:
                sender.sendMessage(ChatColor.RED + "Unknown sub‑command. " + USAGE);
        }

        return true;
    }
}

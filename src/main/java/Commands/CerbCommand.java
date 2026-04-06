package Commands;

import Manager.CombatConfig;
import Manager.ScalingFormula;
import Manager.SessionLogger;
import Skills.SkillManager;
import cerberus.world.cerb.CerberusPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;

public class CerbCommand implements CommandExecutor {

    private static final String USAGE =
            ChatColor.YELLOW + "Usage: /cerb <reload|backup|debug|combatdebug|scalingpreview|log|status|heal|mana|skill|resetdata|"
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

            case "combatdebug":
                // Toggle combat debug mode — shows full pipeline trace on each hit
                if (sender instanceof Player p) {
                    boolean enabled = plugin.toggleCombatDebug(p.getUniqueId());
                    p.sendMessage(ChatColor.AQUA + "Combat debug: " + (enabled ? ChatColor.GREEN + "ON" : ChatColor.RED + "OFF"));
                    if (enabled) {
                        p.sendMessage(ChatColor.GRAY + "You will see pipeline traces for hits you deal or receive.");
                    }
                }
                break;

            case "scalingpreview":
                // Show scaling curve preview for skill stats
                handleScalingPreview(sender, args);
                break;

            case "log":
                // Dump session log for a player
                handleLogDump(sender, args);
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

            case "speed":
                handleSpeedDiag((Player) sender, args);
                break;

            default:
                sender.sendMessage(ChatColor.RED + "Unknown sub‑command. " + USAGE);
        }

        return true;
    }

    /**
     * Handle /cerb scalingpreview [all | skill stat]
     * Shows scaling curve values at standard levels.
     */
    /**
     * Handle /cerb log <player>
     * Dumps the player's current ring buffer to a .toon file and prints the path.
     */
    private void handleLogDump(CommandSender sender, String[] args) {
        SessionLogger logger = plugin.getSessionLogger();
        if (logger == null) {
            sender.sendMessage(ChatColor.RED + "SessionLogger not initialized.");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Use: /cerb log <player>");
            return;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player not found: " + args[1]);
            return;
        }
        String path = logger.dumpLive(target.getUniqueId());
        if (path == null) {
            sender.sendMessage(ChatColor.YELLOW + "No log entries for " + target.getName() + " yet.");
        } else {
            sender.sendMessage(ChatColor.GREEN + "Log dumped: " + ChatColor.WHITE + path);
        }
    }

    /**
     * /cerb speed [player] — dump movement speed diagnostics.
     * Shows base value, final value, every modifier, and relevant skill levels.
     */
    private void handleSpeedDiag(Player sender, String[] args) {
        Player target = sender;
        if (args.length >= 2) {
            Player found = Bukkit.getPlayer(args[1]);
            if (found == null) {
                sender.sendMessage(ChatColor.RED + "Player not found: " + args[1]);
                return;
            }
            target = found;
        }

        AttributeInstance attr = target.getAttribute(Attribute.MOVEMENT_SPEED);
        if (attr == null) {
            sender.sendMessage(ChatColor.RED + "No movement speed attribute.");
            return;
        }

        sender.sendMessage(ChatColor.GOLD + "=== Speed Diagnostics: " + target.getName() + " ===");
        sender.sendMessage(ChatColor.YELLOW + "Base value: " + ChatColor.WHITE + String.format("%.4f", attr.getBaseValue())
                + ChatColor.GRAY + " (vanilla default: 0.1000)");
        sender.sendMessage(ChatColor.YELLOW + "Final value: " + ChatColor.WHITE + String.format("%.4f", attr.getValue())
                + ChatColor.GRAY + " (effective speed)");
        sender.sendMessage(ChatColor.YELLOW + "Walk speed: " + ChatColor.WHITE + String.format("%.4f", target.getWalkSpeed()));

        var modifiers = attr.getModifiers();
        if (modifiers.isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "  (no modifiers)");
        } else {
            sender.sendMessage(ChatColor.YELLOW + "Modifiers (" + modifiers.size() + "):");
            for (AttributeModifier mod : modifiers) {
                String op = switch (mod.getOperation()) {
                    case ADD_NUMBER -> "ADD";
                    case ADD_SCALAR -> "ADD%";
                    case MULTIPLY_SCALAR_1 -> "MULT";
                };
                sender.sendMessage(ChatColor.GRAY + "  " + mod.getName()
                        + ChatColor.WHITE + " " + String.format("%+.4f", mod.getAmount())
                        + ChatColor.DARK_GRAY + " [" + op + "]");
            }
        }

        // Relevant skill levels
        SkillManager sm = plugin.getSkillManager();
        if (sm != null) {
            int navLevel = sm.getSkillLevel("Navigation");
            int bladeLevel = sm.getSkillLevel("Blade Mastery");
            sender.sendMessage(ChatColor.YELLOW + "Skill levels: "
                    + ChatColor.WHITE + "Navigation=" + navLevel
                    + " BladeMastery=" + bladeLevel);
            if (navLevel > 0) {
                sender.sendMessage(ChatColor.GRAY + "  Nav speed bonus: +" + String.format("%.0f%%", navLevel * 1.0)
                        + " (1% per level)");
            }
        }

        // Active potion effects related to speed
        target.getActivePotionEffects().stream()
                .filter(e -> e.getType().getName().contains("SPEED") || e.getType().getName().contains("SLOW"))
                .forEach(e -> sender.sendMessage(ChatColor.YELLOW + "Potion: " + ChatColor.WHITE
                        + e.getType().getName() + " amp=" + e.getAmplifier()
                        + " dur=" + (e.getDuration() / 20) + "s"));
    }

    private void handleScalingPreview(CommandSender sender, String[] args) {
        CombatConfig combatConfig = plugin.getCombatConfig();
        if (combatConfig == null) {
            sender.sendMessage(ChatColor.RED + "CombatConfig not loaded.");
            return;
        }

        Map<String, ScalingFormula.ScalingParams> allParams = combatConfig.getAllScalingParams();

        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Use: /cerb scalingpreview <all | skill/stat>");
            sender.sendMessage(ChatColor.GRAY + "Available: " + String.join(", ", allParams.keySet()));
            return;
        }

        String query = args[1].toLowerCase();

        if (query.equals("all")) {
            // Dump all scaling curves
            for (Map.Entry<String, ScalingFormula.ScalingParams> entry : allParams.entrySet()) {
                sender.sendMessage(ScalingFormula.preview(entry.getKey(), entry.getValue()));
            }
            return;
        }

        // Try to match by partial name — "crit-chance" matches "critical-strike / crit-chance"
        // Also support "critical-strike/crit-chance" or "critical-strike crit-chance"
        String searchKey = query.replace("/", " / ").replace("  ", " ");
        if (args.length >= 3) {
            searchKey = args[1].toLowerCase() + " / " + args[2].toLowerCase();
        }

        String foundKey = null;
        for (String key : allParams.keySet()) {
            if (key.equalsIgnoreCase(searchKey) || key.toLowerCase().contains(searchKey)) {
                foundKey = key;
                break;
            }
        }

        if (foundKey != null) {
            sender.sendMessage(ScalingFormula.preview(foundKey, allParams.get(foundKey)));
        } else {
            sender.sendMessage(ChatColor.RED + "No scaling stat found matching: " + searchKey);
            sender.sendMessage(ChatColor.GRAY + "Available: " + String.join(", ", allParams.keySet()));
        }
    }
}

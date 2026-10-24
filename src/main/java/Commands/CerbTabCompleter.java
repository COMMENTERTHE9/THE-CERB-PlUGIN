package Commands;

import Manager.CombatConfig;
import Manager.ScalingFormula;
import Manager.Tier;
import Skills.SkillManager;
import cerberus.world.cerb.CerberusPlugin;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Tab completion for all /cerb subcommands and their arguments.
 */
public class CerbTabCompleter implements TabCompleter {

    private final CerberusPlugin plugin;

    private static final List<String> SUBCOMMANDS = List.of(
            "reload", "backup", "debug", "combatdebug",
            "scalingpreview", "log", "healthstate", "status",
            "heal", "mana", "skill", "resetdata",
            "worldgen", "repairchunk", "quest", "event",
            "timings", "profiler", "listentities", "showqueues",
            "speed", "spawn"
    );

    private static final List<String> ON_OFF = List.of("on", "off");
    private static final List<String> START_STOP = List.of("start", "stop");
    private static final List<String> LEVEL_XP = List.of("level", "xp");

    public CerbTabCompleter(CerberusPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("cerb.admin")) {
            return List.of();
        }

        if (args.length == 1) {
            return filter(SUBCOMMANDS, args[0]);
        }

        String sub = args[0].toLowerCase();
        return switch (sub) {
            // arg1 = on|off
            case "debug", "worldgen" -> args.length == 2 ? filter(ON_OFF, args[1]) : List.of();

            // arg1 = player name
            case "heal", "log", "healthstate", "resetdata", "speed" ->
                    args.length == 2 ? filterPlayers(args[1]) : List.of();

            // arg1 = player, arg2 = amount
            case "mana" -> args.length == 2 ? filterPlayers(args[1]) : List.of();

            // skill <player> <skillName> <level|xp> <value>
            case "skill" -> completeSkill(args);

            // scalingpreview <all | param-key>
            case "scalingpreview" -> completeScalingPreview(args);

            // event <start|stop> <name>
            case "event" -> args.length == 2 ? filter(START_STOP, args[1]) : List.of();

            // timings <start|stop>
            case "timings" -> args.length == 2 ? filter(START_STOP, args[1]) : List.of();

            // profiler <spark>
            case "profiler" -> args.length == 2 ? filter(List.of("spark"), args[1]) : List.of();

            // spawn <mobId>
            case "spawn" -> args.length == 2 ? filterMobIds(args[1]) : List.of();

            // No further args
            default -> List.of();
        };
    }

    private List<String> completeSkill(String[] args) {
        return switch (args.length) {
            case 2 -> filterPlayers(args[1]);
            case 3 -> filterSkillNames(args[2]);
            case 4 -> filter(LEVEL_XP, args[3]);
            default -> List.of();
        };
    }

    private List<String> completeScalingPreview(String[] args) {
        if (args.length != 2) return List.of();

        List<String> options = new ArrayList<>();
        options.add("all");
        CombatConfig cc = plugin.getCombatConfig();
        if (cc != null) {
            options.addAll(cc.getAllScalingParams().keySet());
        }
        return filter(options, args[1]);
    }

    // ── Helpers ──────────────────────────────────────────────────

    private List<String> filterPlayers(String partial) {
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase().startsWith(partial.toLowerCase()))
                .collect(Collectors.toList());
    }

    private List<String> filterMobIds(String partial) {
        Manager.MobRegistry registry = plugin.getMobRegistry();
        if (registry == null) return List.of();
        return filter(new ArrayList<>(registry.getEntryIds()), partial);
    }

    private List<String> filterSkillNames(String partial) {
        SkillManager sm = plugin.getSkillManager();
        if (sm == null) return List.of();
        return sm.getSkills().keySet().stream()
                .map(name -> name.replace(' ', '_'))   // one argument per skill name
                .filter(name -> name.toLowerCase().startsWith(partial.toLowerCase()))
                .sorted()
                .collect(Collectors.toList());
    }

    private static List<String> filter(List<String> options, String partial) {
        String lower = partial.toLowerCase();
        return options.stream()
                .filter(s -> s.toLowerCase().startsWith(lower))
                .collect(Collectors.toList());
    }
}

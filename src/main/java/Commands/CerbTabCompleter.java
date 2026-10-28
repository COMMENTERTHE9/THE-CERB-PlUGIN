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
            "panel", "heal", "mana", "skill", "class", "resetdata",
            "worldgen", "repairchunk", "quest", "event",
            "timings", "profiler", "listentities", "showqueues",
            "speed", "spawn", "mobs", "labels", "gather", "fish", "farm", "cook", "herb", "reprisal"
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

            // arg1 = player, arg2 = class | info, arg3 = rank
            case "class" -> plugin.getClassCommand() != null ? plugin.getClassCommand().adminComplete(args) : List.of();

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
            case "gather" -> completeGather(args);
            case "fish" -> completeFish(args);
            case "herb" -> args.length == 2 ? filter(HerbalismCommand.ADMIN, args[1])
                    : args.length == 3 && !args[1].equalsIgnoreCase("info") && !args[1].equalsIgnoreCase("stats") ? filterPlayers(args[2])
                    : args.length == 4 && args[1].equalsIgnoreCase("herbs") ? filter(List.of("reset"), args[3])
                    : args.length == 4 && args[1].equalsIgnoreCase("rare")
                        ? filter(Gathering.GatheringCatalog.current().herbalism().rares().stream().map(Gathering.HerbalismRules.Rare::id).toList(), args[3])
                    : args.length == 6 && args[1].equalsIgnoreCase("rare") ? filter(List.of("common", "fine", "superior", "masterwork"), args[5]) : List.of();
            case "cook" -> args.length == 2 ? filter(CookingCommand.ADMIN, args[1])
                    : args.length == 3 ? filterPlayers(args[2])
                    : args.length == 4 && args[1].equalsIgnoreCase("give") ? filter(Gathering.GatheringCatalog.current().cooking().meals().stream().map(Gathering.CookingRules.Meal::id).toList(), args[3])
                    : args.length == 5 && args[1].equalsIgnoreCase("give") ? filter(List.of("common", "fine", "superior", "masterwork"), args[4]) : List.of();
            case "farm" -> args.length == 2 ? filter(FarmingCommand.ADMIN, args[1])
                    : args.length == 3 && List.of("crops", "hoe", "notice").contains(args[1].toLowerCase()) ? filterPlayers(args[2])
                    : args.length == 4 && List.of("crops", "notice").contains(args[1].toLowerCase()) ? filter(List.of("reset"), args[3])
                    : args.length == 4 && args[1].equalsIgnoreCase("hoe")
                        ? filter(List.of("WOODEN_HOE", "STONE_HOE", "IRON_HOE", "GOLDEN_HOE", "DIAMOND_HOE", "NETHERITE_HOE"), args[3]) : List.of();

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
                .flatMap(name -> java.util.stream.Stream.of(name, Skills.SkillNames.display(name))) // old and new names both work
                .distinct()
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

    private List<String> completeFish(String[] args) {
        Gathering.FishingRules r = Gathering.GatheringCatalog.current().fishing();
        if (args.length == 2) return filter(FishingCommand.ADMIN, args[1]);
        String sub = args[1].toLowerCase(java.util.Locale.ROOT);
        if (args.length == 3 && !sub.equals("info") && !sub.equals("stats")) {
            return filter(org.bukkit.Bukkit.getOnlinePlayers().stream().map(org.bukkit.entity.Player::getName).toList(), args[2]);
        }
        if (args.length == 4 && sub.equals("lure")) return filter(List.copyOf(r.creatures().keySet()), args[3]);
        if (args.length == 4 && sub.equals("force")) return filter(List.of("species", "treasure", "lure", "apex", "clear"), args[3]);
        Gathering.TackleRules t = Gathering.GatheringCatalog.current().tackle();
        if (args.length == 4 && sub.equals("part")) return filter(List.copyOf(t.parts().keySet()), args[3]);
        if (args.length == 5 && sub.equals("part")) return filter(List.of("remove"), args[4]);
        if (args.length == 4 && sub.equals("bait")) return filter(List.copyOf(t.bait().keySet()), args[3]);
        if (args.length == 5 && sub.equals("bait")) return filter(List.of("1", "16", "64"), args[4]);
        if (args.length == 4 && sub.equals("legacyrod")) {
            return filter(Gathering.GatheringCatalog.current().upgrades().values().stream()
                    .filter(u -> u.on().contains("rod")).map(Gathering.GatheringCatalog.UpgradeDef::id).toList(), args[3]);
        }
        if (args.length == 5 && sub.equals("force")) {
            return switch (args[3].toLowerCase(java.util.Locale.ROOT)) {
                case "species" -> filter(List.copyOf(r.allSpecies().keySet()), args[4]);
                case "treasure" -> filter(r.treasure().stream().map(Gathering.FishingRules.Treasure::id).toList(), args[4]);
                case "lure", "apex" -> filter(List.copyOf(r.creatures().keySet()), args[4]);
                default -> List.of();
            };
        }
        return List.of();
    }

    private List<String> completeGather(String[] args) {
        var svc = plugin.getGatheringService();
        if (args.length == 2) return filter(GatherCommand.SUBS, args[1]);
        if (args.length == 3 && svc != null) {
            return switch (args[1].toLowerCase(java.util.Locale.ROOT)) {
                case "tool" -> filter(List.copyOf(svc.catalog().tools().keySet()), args[2]);
                case "item" -> filter(List.copyOf(svc.catalog().items().keySet()), args[2]);
                case "quality" -> filter(List.of("common", "fine", "superior", "masterwork", "none"), args[2]);
                case "credit" -> filter(List.copyOf(svc.catalog().needsSmelted()), args[2]);
                default -> List.of();
            };
        }
        return List.of();
    }
}

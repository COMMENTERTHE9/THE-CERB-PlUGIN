package Commands;

import Gathering.Cooking;
import Gathering.CookingRules;
import Gathering.GatheringCatalog;
import Gathering.Quality;
import cerberus.world.cerb.CerberusPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/**
 * {@code /cooking} (docs/design/FARMING_COOKING.md §4): the tier a player cooks at, their running meal and the cookbook.
 * {@code /cerb cook ...}: admin tools (the caller already holds {@code cerb.admin}, checked by /cerb on every use): give a
 * meal item at a tier, end a player's meal, counters. Changes to another player run on that player's thread.
 */
public final class CookingCommand implements CommandExecutor, TabCompleter {

    static final List<String> SUBS = List.of("book");
    static final List<String> ADMIN = List.of("give", "clear", "stats");

    private final CerberusPlugin plugin;

    public CookingCommand(CerberusPlugin plugin) {
        this.plugin = plugin;
    }

    private static CookingRules rules() { return GatheringCatalog.current().cooking(); }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Only players cook."); return true; }
        Cooking c = plugin.getCooking();
        CookingRules r = rules();
        if (c == null || !r.enabled()) { p.sendMessage(ChatColor.GRAY + "Cooking's rules are off on this server."); return true; }
        if (!plugin.getSkillManager().hasLoadedSkills(p.getUniqueId())) { p.sendMessage(ChatColor.GRAY + "Your skills are still loading. Try again in a moment."); return true; }
        if (args.length > 0 && args[0].equalsIgnoreCase("book")) {
            plugin.getMenuManager().openRoot(p, new GUIs.player.CookbookMenu(plugin));
            return true;
        }
        int level = plugin.getSkillManager().getSkillLevel(p, r.skill());
        Quality q = r.tierAt(level);
        long have = r.meals().stream().filter(m -> c.cooked(p).contains(m.id())).count();
        p.sendMessage(ChatColor.GOLD + "Cooking " + level + ChatColor.GRAY + " · you cook " + q.color + q.displayName + ChatColor.GRAY
                + " meals (effects ×" + trim(r.potency(q)) + ", lasting ×" + trim(r.durationFactor(q)) + ") · meals cooked " + ChatColor.WHITE
                + have + " of " + r.meals().size());
        p.sendMessage(ChatColor.GRAY + "  " + activeLine(c, r, p));
        p.sendMessage(ChatColor.DARK_GRAY + "Cookbook: /cooking book (or Player Menu → Skills → Cooking)");
        return true;
    }

    /** The running meal and its time left, or "No meal running". */
    public static String activeLine(Cooking c, CookingRules r, Player p) {
        Cooking.Active a = c.active(p.getUniqueId());
        CookingRules.Meal m = a == null ? null : r.meal(a.id());
        if (m == null) return "§7No meal running.";
        long left = a.end() - System.currentTimeMillis();
        return a.tier().color + a.tier().displayName + " §6" + m.name() + " §7· " + String.join(", ", Cooking.effectLines(m, a.tier(), r))
                + " §8· " + (left / 60_000) + " min " + (left / 1000 % 60) + " s left";
    }

    static String trim(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return args.length == 1 ? FarmingCommand.filter(SUBS, args[0]) : List.of();
    }

    // ── /cerb cook ───────────────────────────────────────────────

    public void admin(CommandSender sender, String[] args) {
        String sub = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        Cooking c = plugin.getCooking();
        CookingRules r = rules();
        if (c == null) { sender.sendMessage(ChatColor.RED + "Cooking isn't running."); return; }
        switch (sub) {
            case "stats" -> sender.sendMessage(ChatColor.GRAY + "[Cooking] " + (r.enabled() ? "" : "rules off; ") + c.stats());
            case "give" -> {
                Player t = args.length > 2 ? Bukkit.getPlayerExact(args[2]) : null;
                CookingRules.Meal m = args.length > 3 ? r.meal(args[3]) : null;
                Quality q = args.length > 4 ? Quality.parse(args[4]) : Quality.COMMON;
                int n = args.length > 5 ? Math.max(1, Math.min(64, parse(args[5]))) : 1;
                if (t == null || m == null || q == null) { sender.sendMessage(ChatColor.GRAY + "/cerb cook give <player> <meal> [tier] [amount]"); return; }
                t.getScheduler().run(plugin, task -> {
                    for (int i = 0; i < n; i++) t.getInventory().addItem(c.makeMeal(m, q));
                    sender.sendMessage(ChatColor.GRAY + "Gave " + t.getName() + " " + n + " " + q.displayName + " " + m.name());
                }, null);
            }
            case "clear" -> {
                Player t = args.length > 2 ? Bukkit.getPlayerExact(args[2]) : null;
                if (t == null) { sender.sendMessage(ChatColor.GRAY + "/cerb cook clear <player>"); return; }
                t.getScheduler().run(plugin, task -> { c.clear(t); sender.sendMessage(ChatColor.GRAY + t.getName() + ": no meal running"); }, null);
            }
            default -> sender.sendMessage(ChatColor.GRAY + "/cerb cook give <player> <meal> [tier] [amount] | clear <player> | stats");
        }
    }

    private static int parse(String s) {
        try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return 1; }
    }
}

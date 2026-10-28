package Commands;

import Gathering.Alchemy;
import Gathering.AlchemyRules;
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
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * {@code /alchemy} (docs/design/ALCHEMY.md §5): the tier you brew at, your running elixir and draught, brews made;
 * {@code /alchemy brews} opens the list. {@code /cerb alchemy ...}: admin tools (the caller already holds {@code cerb.admin},
 * checked by /cerb on every use): counters, brewed bottles for tests, a player's brews (shown or reset) and clearing an elixir.
 */
public final class AlchemyCommand implements CommandExecutor, TabCompleter {

    static final List<String> SUBS = List.of("brews");
    static final List<String> ADMIN = List.of("stats", "give", "brews", "clear");

    private final CerberusPlugin plugin;

    public AlchemyCommand(CerberusPlugin plugin) {
        this.plugin = plugin;
    }

    private static AlchemyRules rules() { return GatheringCatalog.current().alchemy(); }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Only players brew."); return true; }
        Alchemy a = plugin.getAlchemy();
        AlchemyRules r = rules();
        if (a == null || !r.enabled()) { p.sendMessage(ChatColor.GRAY + "Alchemy's brews are off on this server."); return true; }
        if (!plugin.getSkillManager().hasLoadedSkills(p.getUniqueId())) { p.sendMessage(ChatColor.GRAY + "Your skills are still loading. Try again in a moment."); return true; }
        if (args.length > 0 && args[0].equalsIgnoreCase("brews")) {
            plugin.getMenuManager().openRoot(p, new GUIs.player.BrewsMenu(plugin));
            return true;
        }
        int level = plugin.getSkillManager().getSkillLevel(p, r.skill());
        Quality q = r.tierAt(level);
        Set<String> made = a.made(p);
        long have = r.brews().stream().filter(b -> made.contains(b.id())).count();
        p.sendMessage(ChatColor.GOLD + "Alchemy " + level + ChatColor.GRAY + " · you brew at " + q.color + q.displayName + ChatColor.GRAY
                + " (x" + trim(r.potency(q)) + " strength, elixirs last x" + trim(r.durationFactor(q)) + ") · brews made " + ChatColor.WHITE
                + have + " of " + r.brews().size());
        Alchemy.Active e = a.active(p.getUniqueId());
        AlchemyRules.Brew b = e == null ? null : r.brew(e.id());
        p.sendMessage(b == null ? ChatColor.GRAY + "No elixir running."
                : ChatColor.LIGHT_PURPLE + b.name() + ChatColor.GRAY + " (" + e.tier().displayName + "): "
                + String.join(", ", Alchemy.effectLines(b, e.tier(), r).subList(0, b.effects().size())) + ", "
                + Effects.EffectsText.remaining(e.end() - System.currentTimeMillis()) + " left");
        int left = a.draughtLeft(p.getUniqueId());
        if (left > 0) p.sendMessage(ChatColor.LIGHT_PURPLE + "A draught is healing: " + left + " s left");
        p.sendMessage(ChatColor.DARK_GRAY + "Brews: /alchemy brews (or Player Menu → Skills → Alchemy)");
        return true;
    }

    private static String trim(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(Math.round(v * 100) / 100.0);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return args.length == 1 ? FarmingCommand.filter(SUBS, args[0]) : List.of();
    }

    // ── /cerb alchemy ────────────────────────────────────────────

    public void admin(CommandSender sender, String[] args) {
        String sub = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        Alchemy a = plugin.getAlchemy();
        AlchemyRules r = rules();
        if (a == null) { sender.sendMessage(ChatColor.RED + "Alchemy isn't running."); return; }
        switch (sub) {
            case "stats" -> sender.sendMessage(ChatColor.GRAY + "[Alchemy] " + (r.enabled() ? "" : "rules off; ") + a.stats());
            case "give" -> { // a brewed bottle, for tests
                Player t = args.length > 2 ? Bukkit.getPlayerExact(args[2]) : null;
                AlchemyRules.Brew b = args.length > 3 ? r.brew(args[3].toLowerCase(Locale.ROOT)) : null;
                Quality q = args.length > 4 ? Quality.parse(args[4]) : Quality.COMMON;
                int n = args.length > 5 ? parse(args[5], 1) : 1;
                if (t == null || b == null || q == null) {
                    sender.sendMessage(ChatColor.GRAY + "/cerb alchemy give <online player> <" + String.join("|", r.brews().stream().map(AlchemyRules.Brew::id).toList())
                            + "> [common|fine|superior|masterwork] [count]");
                    return;
                }
                int count = Math.max(1, Math.min(16, n));
                t.getScheduler().run(plugin, task -> {
                    for (int i = 0; i < count; i++) {
                        ItemStack it = Alchemy.makeBrew(b, q, r);
                        t.getInventory().addItem(it).values().forEach(left -> t.getWorld().dropItem(t.getLocation(), left));
                    }
                    sender.sendMessage(ChatColor.GRAY + "Gave " + t.getName() + " " + count + " " + q.displayName + " " + b.name() + ".");
                }, null);
            }
            case "brews" -> {
                Player t = args.length > 2 ? Bukkit.getPlayerExact(args[2]) : null;
                if (t == null) { sender.sendMessage(ChatColor.GRAY + "/cerb alchemy brews <online player> [reset]"); return; }
                boolean reset = args.length > 3 && args[3].equalsIgnoreCase("reset");
                t.getScheduler().run(plugin, task -> {
                    if (reset) a.resetMade(t);
                    sender.sendMessage(ChatColor.GRAY + t.getName() + (reset ? " (reset)" : "") + ": " + String.join(", ", a.made(t)));
                }, null);
            }
            case "clear" -> {
                Player t = args.length > 2 ? Bukkit.getPlayerExact(args[2]) : null;
                if (t == null) { sender.sendMessage(ChatColor.GRAY + "/cerb alchemy clear <online player>"); return; }
                t.getScheduler().run(plugin, task -> { a.clear(t); sender.sendMessage(ChatColor.GRAY + t.getName() + ": elixir and draught cleared."); }, null);
            }
            default -> sender.sendMessage(ChatColor.GRAY + "/cerb alchemy stats | give <player> <brew> [tier] [count] | brews <player> [reset] | clear <player>");
        }
    }

    private static int parse(String s, int fallback) {
        try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return fallback; }
    }
}

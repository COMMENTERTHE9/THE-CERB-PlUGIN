package Commands;

import Gathering.GatheringCatalog;
import Gathering.GearItems;
import Gathering.Herbalism;
import Gathering.HerbalismRules;
import Gathering.Quality;
import cerberus.world.cerb.CerberusPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * {@code /herbalism} (docs/design/HERBALISM.md §5): a player's herbs, rare reagents and techniques; {@code /herbalism herbs}
 * and {@code /herbalism rare} open the Herbarium. {@code /cerb herb ...}: admin tools (the caller already holds
 * {@code cerb.admin}, checked by /cerb on every use): the plant you look at as the rules see it, counters, a player's
 * discoveries (shown or reset on that player's thread) and rare reagents for tests.
 */
public final class HerbalismCommand implements CommandExecutor, TabCompleter {

    static final List<String> SUBS = List.of("herbs", "rare");
    static final List<String> ADMIN = List.of("info", "stats", "herbs", "rare");

    private final CerberusPlugin plugin;

    public HerbalismCommand(CerberusPlugin plugin) {
        this.plugin = plugin;
    }

    private static HerbalismRules rules() { return GatheringCatalog.current().herbalism(); }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Only players gather herbs."); return true; }
        Herbalism h = plugin.getHerbalism();
        HerbalismRules r = rules();
        if (h == null || !r.enabled()) { p.sendMessage(ChatColor.GRAY + "Herbalism's rules are off on this server."); return true; }
        if (!plugin.getSkillManager().hasLoadedSkills(p.getUniqueId())) { p.sendMessage(ChatColor.GRAY + "Your skills are still loading. Try again in a moment."); return true; }
        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";
        switch (sub) {
            case "herbs" -> plugin.getMenuManager().openRoot(p, new GUIs.player.HerbariumMenu(plugin));
            case "rare" -> plugin.getMenuManager().openRoot(p, GUIs.player.HerbariumMenu.rare(plugin));
            default -> summary(p, h, r);
        }
        return true;
    }

    private void summary(Player p, Herbalism h, HerbalismRules r) {
        int level = plugin.getSkillManager().getSkillLevel(p, r.skill());
        Set<String> found = h.discovered(p);
        long have = r.herbs().stream().filter(x -> found.contains(x.id())).count();
        Map<String, Integer> rares = h.rareFound(p);
        long rareHave = r.rares().stream().filter(x -> rares.containsKey(x.id())).count();
        p.sendMessage(ChatColor.GOLD + "Herbalism " + level + ChatColor.GRAY + " · herbs discovered " + ChatColor.WHITE + have + " of "
                + r.herbs().size() + ChatColor.GRAY + " · rare reagents " + ChatColor.WHITE + rareHave + " of " + r.rares().size());
        for (String line : techniques(r, level)) p.sendMessage(line);
        p.sendMessage(ChatColor.DARK_GRAY + "Herbarium: /herbalism herbs, /herbalism rare (or Player Menu → Skills → Herbalism)");
    }

    /** The techniques at {@code level}, one line each (the Herbarium and the skill page say the same). */
    public static List<String> techniques(HerbalismRules r, int level) {
        List<String> out = new java.util.ArrayList<>();
        int side = r.reach(level);
        if (side > 0) {
            Integer next = r.reachSteps().higherKey(level);
            out.add("§a✔ §fWildcrafting §7" + side + "×" + side + " (Sneak + shears)"
                    + (next != null ? "§8 · " + r.reachSteps().get(next) + "×" + r.reachSteps().get(next) + " at " + next : ""));
        } else if (r.wildcraftAt() > 0) {
            out.add("§8✖ Wildcrafting §7from Herbalism " + r.wildcraftAt());
        }
        if (r.rareAt() > 0 && level >= r.rareAt()) {
            out.add("§a✔ §fRare reagents §7" + FarmingCommand.pct(r.rareChance(level)) + " from a herb that has one");
        } else if (r.rareAt() > 0) {
            out.add("§8✖ Rare reagents §7from Herbalism " + r.rareAt());
        }
        if (r.qualityAt() > 0 && level >= r.qualityAt()) {
            out.add("§a✔ §fReagent quality §7" + Quality.FINE.color + FarmingCommand.pct(r.chance(Quality.FINE, level)) + " fine§7, "
                    + Quality.SUPERIOR.color + FarmingCommand.pct(r.chance(Quality.SUPERIOR, level)) + " superior§7, "
                    + Quality.MASTERWORK.color + FarmingCommand.pct(r.chance(Quality.MASTERWORK, level)) + " masterwork");
        } else if (r.qualityAt() > 0) {
            out.add("§8✖ Reagent quality §7from Herbalism " + r.qualityAt());
        }
        return out;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return args.length == 1 ? FarmingCommand.filter(SUBS, args[0]) : List.of();
    }

    // ── /cerb herb ───────────────────────────────────────────────

    public void admin(CommandSender sender, String[] args) {
        String sub = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        Herbalism h = plugin.getHerbalism();
        HerbalismRules r = rules();
        if (h == null) { sender.sendMessage(ChatColor.RED + "Herbalism isn't running."); return; }
        switch (sub) {
            case "stats" -> sender.sendMessage(ChatColor.GRAY + "[Herbalism] " + (r.enabled() ? "" : "rules off; ") + h.stats());
            case "info" -> {
                if (!(sender instanceof Player p)) { sender.sendMessage(ChatColor.RED + "Run /cerb herb info in-game."); return; }
                Block b = p.getTargetBlockExact(6);
                HerbalismRules.Herb herb = b == null ? null : r.herb(b.getType());
                if (herb == null) { p.sendMessage(ChatColor.GRAY + "Look at a herb (" + r.herbs().size() + " in resources.yml herbalism.herbs)."); return; }
                p.sendMessage(ChatColor.GOLD + herb.id() + ChatColor.GRAY + " · " + b.getBlockData().getAsString() + " · " + h.describe(p, b, herb));
            }
            case "herbs" -> {
                Player t = args.length > 2 ? Bukkit.getPlayerExact(args[2]) : null;
                if (t == null) { sender.sendMessage(ChatColor.GRAY + "/cerb herb herbs <online player> [reset]"); return; }
                boolean reset = args.length > 3 && args[3].equalsIgnoreCase("reset");
                t.getScheduler().run(plugin, task -> {
                    if (reset) h.resetDiscoveries(t);
                    sender.sendMessage(ChatColor.GRAY + t.getName() + (reset ? " (reset)" : "") + ": " + String.join(", ", h.discovered(t))
                            + " · rare " + h.rareFound(t));
                }, null);
            }
            case "rare" -> { // rare reagents as gathering makes them (for tests: Alchemy's strongest brews need them)
                Player t = args.length > 2 ? Bukkit.getPlayerExact(args[2]) : null;
                HerbalismRules.Rare rare = args.length > 3 ? r.rare(args[3].toLowerCase(Locale.ROOT)) : null;
                int n = args.length > 4 ? parse(args[4], 1) : 1;
                Quality q = args.length > 5 ? Quality.parse(args[5]) : Quality.COMMON;
                GatheringCatalog.ItemDef def = rare == null ? null : GatheringCatalog.current().item(rare.item());
                if (t == null || def == null || q == null) {
                    sender.sendMessage(ChatColor.GRAY + "/cerb herb rare <online player> <" + String.join("|", r.rares().stream().map(HerbalismRules.Rare::id).toList())
                            + "> [count] [common|fine|superior|masterwork]");
                    return;
                }
                ItemStack it = GearItems.makeItem(def, Math.max(1, Math.min(64, n)));
                if (q != Quality.COMMON) GearItems.setCropQuality(it, q);
                t.getScheduler().run(plugin, task -> {
                    t.getInventory().addItem(it).values().forEach(left -> t.getWorld().dropItem(t.getLocation(), left));
                    sender.sendMessage(ChatColor.GRAY + "Gave " + t.getName() + " " + it.getAmount() + " " + def.name() + (q != Quality.COMMON ? " (" + q.displayName + ")" : "") + ".");
                }, null);
            }
            default -> sender.sendMessage(ChatColor.GRAY + "/cerb herb info | stats | herbs <player> [reset] | rare <player> <reagent> [count] [quality]");
        }
    }

    private static int parse(String s, int fallback) {
        try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return fallback; }
    }
}

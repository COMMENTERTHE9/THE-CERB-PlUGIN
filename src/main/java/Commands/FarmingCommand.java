package Commands;

import Gathering.Farming;
import Gathering.FarmingRules;
import Gathering.GatheringCatalog;
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

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * {@code /farming} (docs/design/FARMING_COOKING.md §3): a player's crops and techniques, and Replant's switch.
 * {@code /cerb farm ...}: admin tools (the caller already holds {@code cerb.admin}, checked by /cerb on every use): the crop
 * you look at as the rules see it, counters, and a player's discoveries (shown or reset on that player's thread).
 */
public final class FarmingCommand implements CommandExecutor, TabCompleter {

    static final List<String> SUBS = List.of("crops", "replant");
    static final List<String> ADMIN = List.of("info", "stats", "crops", "hoe", "notice");

    private final CerberusPlugin plugin;

    public FarmingCommand(CerberusPlugin plugin) {
        this.plugin = plugin;
    }

    private static FarmingRules rules() { return GatheringCatalog.current().farming(); }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Only players farm."); return true; }
        Farming f = plugin.getFarming();
        FarmingRules r = rules();
        if (f == null || !r.enabled()) { p.sendMessage(ChatColor.GRAY + "Farming's rules are off on this server."); return true; }
        if (!plugin.getSkillManager().hasLoadedSkills(p.getUniqueId())) { p.sendMessage(ChatColor.GRAY + "Your skills are still loading. Try again in a moment."); return true; }
        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";
        switch (sub) {
            case "crops" -> plugin.getMenuManager().openRoot(p, new GUIs.player.CropsMenu(plugin));
            case "replant" -> {
                String v = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : (f.replanting(p) ? "off" : "on");
                if (!v.equals("on") && !v.equals("off")) { p.sendMessage(ChatColor.GRAY + "/farming replant on|off"); return true; }
                f.setReplanting(p, v.equals("on"));
                p.sendMessage(ChatColor.GRAY + "Replant " + (v.equals("on") ? ChatColor.GREEN + "on" : ChatColor.RED + "off")
                        + ChatColor.GRAY + " (it works with a hoe that has the Replanting upgrade)");
            }
            default -> summary(p, f, r);
        }
        return true;
    }

    private void summary(Player p, Farming f, FarmingRules r) {
        int level = plugin.getSkillManager().getSkillLevel(p, r.skill());
        Set<String> found = f.discovered(p);
        long have = r.crops().stream().filter(c -> found.contains(c.id())).count();
        p.sendMessage(ChatColor.GOLD + "Farming " + level + ChatColor.GRAY + " · crops discovered " + ChatColor.WHITE + have + " of " + r.crops().size());
        for (String line : techniques(r, level, f.replanting(p))) p.sendMessage(line);
        org.bukkit.inventory.ItemStack hand = p.getInventory().getItemInMainHand();
        if ("hoe".equals(Gathering.GearItems.kind(hand.getType()))) {
            p.sendMessage(f.replants(hand) ? "§7The hoe in your hand: §6✦ Replanting"
                    : "§7The hoe in your hand has no Replanting upgrade: " + Farming.howToReplant(GatheringCatalog.current()));
        }
        p.sendMessage(ChatColor.DARK_GRAY + "Crops: /farming crops (or Player Menu → Skills → Farming)");
    }

    /** The techniques at {@code level}, one line each (the crops screen and the skill page say the same). */
    public static List<String> techniques(FarmingRules r, int level, boolean replanting) {
        List<String> out = new java.util.ArrayList<>();
        if (!Farming.replantAvailable(GatheringCatalog.current())) out.add("§8✖ Replant §7isn't available on this server");
        else out.add(replanting ? "§a✔ §fReplant §7with a hoe that has the §6Replanting §7upgrade §8(/farming replant off)"
                : "§8✖ Replant §coff §7(/farming replant on)");
        int side = r.reach(level);
        if (side > 0) {
            Integer next = r.reachSteps().higherKey(level);
            out.add("§a✔ §fWide Harvest §7" + side + "×" + side + " (Sneak + a hoe)"
                    + (next != null ? "§8 · " + r.reachSteps().get(next) + "×" + r.reachSteps().get(next) + " at " + next : ""));
        } else if (r.wideAt() > 0) {
            out.add("§8✖ Wide Harvest §7from Farming " + r.wideAt());
        }
        if (r.qualityAt() > 0 && level >= r.qualityAt()) {
            out.add("§a✔ §fCrop quality §7" + Quality.FINE.color + pct(r.chance(Quality.FINE, level)) + " fine§7, "
                    + Quality.SUPERIOR.color + pct(r.chance(Quality.SUPERIOR, level)) + " superior§7, "
                    + Quality.MASTERWORK.color + pct(r.chance(Quality.MASTERWORK, level)) + " masterwork");
        } else if (r.qualityAt() > 0) {
            out.add("§8✖ Crop quality §7from Farming " + r.qualityAt());
        }
        return out;
    }

    static String pct(double chance) {
        double v = chance * 100;
        return (v == Math.rint(v) ? String.valueOf((long) v) : String.format(Locale.ROOT, "%.1f", v)) + "%";
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return filter(SUBS, args[0]);
        if (args.length == 2 && args[0].equalsIgnoreCase("replant")) return filter(List.of("on", "off"), args[1]);
        return List.of();
    }

    // ── /cerb farm ───────────────────────────────────────────────

    public void admin(CommandSender sender, String[] args) {
        String sub = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        Farming f = plugin.getFarming();
        FarmingRules r = rules();
        if (f == null) { sender.sendMessage(ChatColor.RED + "Farming isn't running."); return; }
        switch (sub) {
            case "stats" -> sender.sendMessage(ChatColor.GRAY + "[Farming] " + (r.enabled() ? "" : "rules off; ") + f.stats());
            case "info" -> {
                if (!(sender instanceof Player p)) { sender.sendMessage(ChatColor.RED + "Run /cerb farm info in-game."); return; }
                Block b = p.getTargetBlockExact(6);
                FarmingRules.Crop c = b == null ? null : r.crop(b.getType());
                if (c == null) { p.sendMessage(ChatColor.GRAY + "Look at a crop (" + r.crops().size() + " in resources.yml farming.crops)."); return; }
                p.sendMessage(ChatColor.GOLD + c.id() + ChatColor.GRAY + " · " + b.getBlockData().getAsString() + " · " + f.describe(p, b, c));
            }
            case "crops" -> {
                Player t = args.length > 2 ? Bukkit.getPlayerExact(args[2]) : null;
                if (t == null) { sender.sendMessage(ChatColor.GRAY + "/cerb farm crops <online player> [reset]"); return; }
                boolean reset = args.length > 3 && args[3].equalsIgnoreCase("reset");
                t.getScheduler().run(plugin, task -> {
                    if (reset) f.resetDiscoveries(t);
                    sender.sendMessage(ChatColor.GRAY + t.getName() + (reset ? " (reset)" : "") + ": " + String.join(", ", f.discovered(t)));
                }, null);
            }
            case "hoe" -> { // a hoe with the Replanting upgrade, as the crafting grid makes it (docs/design/FARMING_COOKING.md §9)
                Player t = args.length > 2 ? Bukkit.getPlayerExact(args[2]) : null;
                org.bukkit.Material m = args.length > 3 ? org.bukkit.Material.matchMaterial(args[3]) : org.bukkit.Material.IRON_HOE;
                GatheringCatalog c = GatheringCatalog.current();
                GatheringCatalog.UpgradeDef up = c.upgrades().values().stream().filter(u -> GatheringCatalog.REPLANT.equals(u.ability())).findFirst().orElse(null);
                if (t == null || m == null || !"hoe".equals(Gathering.GearItems.kind(m))) { sender.sendMessage(ChatColor.GRAY + "/cerb farm hoe <online player> [hoe material]"); return; }
                if (up == null) { sender.sendMessage(ChatColor.RED + "No Replanting upgrade in resources.yml."); return; }
                org.bukkit.inventory.ItemStack hoe = Gathering.GearItems.withUpgrade(new org.bukkit.inventory.ItemStack(m), up);
                t.getScheduler().run(plugin, task -> {
                    t.getInventory().addItem(hoe).values().forEach(left -> t.getWorld().dropItem(t.getLocation(), left));
                    sender.sendMessage(ChatColor.GRAY + "Gave " + t.getName() + " a " + m.name().toLowerCase(Locale.ROOT) + " with " + up.name() + ".");
                }, null);
            }
            case "notice" -> { // the one-time "Replant moved to the hoe" message: said again at the next join
                Player t = args.length > 3 && args[3].equalsIgnoreCase("reset") ? Bukkit.getPlayerExact(args[2]) : null;
                if (t == null) { sender.sendMessage(ChatColor.GRAY + "/cerb farm notice <online player> reset"); return; }
                t.getScheduler().run(plugin, task -> {
                    t.getPersistentDataContainer().remove(Farming.REPLANT_MOVED);
                    sender.sendMessage(ChatColor.GRAY + t.getName() + " will be told again at the next join (if at Farming 10 or more).");
                }, null);
            }
            default -> sender.sendMessage(ChatColor.GRAY + "/cerb farm info | stats | crops <player> [reset] | hoe <player> [material] | notice <player> reset");
        }
    }

    static List<String> filter(List<String> options, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(p)).toList();
    }
}

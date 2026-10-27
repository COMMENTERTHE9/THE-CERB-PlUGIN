package Commands;

import Gathering.Fishing;
import Gathering.FishingJournal;
import Gathering.FishingRules;
import Gathering.GatheringCatalog;
import cerberus.world.cerb.CerberusPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.FluidCollisionMode;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * {@code /fishing}: a player's journal (species by water, personal bests), their banked encounters and which one a
 * Sneak-cast calls (docs/design/FISHING.md), the species they track (docs/design/FISHING_EQUIPMENT.md). Players see
 * "encounters"; the old {@code lures} subcommand still works. {@code /cerb fish ...}: admin tools (the caller already
 * holds {@code cerb.admin}, checked by /cerb on every use): the water and odds where you look, encounters and a forced
 * next catch for tests, a player's journal, counters. Changes to another player's journal run on that player's thread.
 */
public final class FishingCommand implements CommandExecutor, TabCompleter {

    static final List<String> SUBS = List.of("journal", "encounters", "call", "track");
    static final List<String> ADMIN = List.of("info", "lure", "force", "journal", "stats");

    private final CerberusPlugin plugin;

    public FishingCommand(CerberusPlugin plugin) {
        this.plugin = plugin;
    }

    private FishingRules rules() { return GatheringCatalog.current().fishing(); }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Only players fish."); return true; }
        Fishing f = plugin.getFishing();
        FishingRules r = rules();
        if (f == null || !r.enabled()) { p.sendMessage(ChatColor.GRAY + "Fishing's rules are off on this server."); return true; }
        if (!plugin.getSkillManager().hasLoadedSkills(p.getUniqueId())) { p.sendMessage(ChatColor.GRAY + "Your skills are still loading. Try again in a moment."); return true; }
        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";
        switch (sub) {
            case "journal" -> journal(p, f.journal(p), r, args.length > 1 ? args[1] : null, p);
            case "encounters", "lures" -> lures(p, f, r);
            case "track" -> track(p, f, r, args.length > 1 ? String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)) : null);
            case "call" -> call(p, f, r, args.length > 1 ? String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)) : null);
            default -> summary(p, f, r);
        }
        return true;
    }

    private void summary(Player p, Fishing f, FishingRules r) {
        FishingJournal j = f.journal(p);
        int level = plugin.getSkillManager().getSkillLevel(p, r.skill());
        long found = r.allSpecies().keySet().stream().filter(j::caught).count();
        p.sendMessage(ChatColor.GOLD + "Fishing " + level + ChatColor.GRAY + " · species " + ChatColor.WHITE + found + " of " + r.allSpecies().size()
                + ChatColor.GRAY + " · creatures defeated " + ChatColor.WHITE + j.creaturesDefeated().size() + " of " + r.creatures().size());
        for (FishingRules.Water w : r.waters()) {
            List<FishingRules.Species> sp = r.species(w.id());
            if (sp.isEmpty()) continue;
            long have = sp.stream().filter(s -> j.caught(s.id())).count();
            p.sendMessage(ChatColor.GRAY + "  " + w.name() + ": " + (have == sp.size() ? ChatColor.GREEN : ChatColor.WHITE) + have + " of " + sp.size());
        }
        FishingRules.Species t = r.speciesById(j.tracked());
        if (t != null) p.sendMessage(ChatColor.LIGHT_PURPLE + "★ Tracking: " + t.rarity().color + itemName(t.item())
                + ChatColor.DARK_GRAY + " (Player Menu → Skills → Fishing → Journal)");
        lures(p, f, r);
        p.sendMessage(ChatColor.DARK_GRAY + "/fishing journal [water] · /fishing track <species|off> · /fishing call <creature>");
    }

    private static String itemName(String item) {
        GatheringCatalog.ItemDef d = GatheringCatalog.current().item(item);
        return d == null ? item : d.name();
    }

    /** {@code /fishing track <species|off>}: the species the journal checks and the cast hint follows (never the odds). */
    private void track(Player p, Fishing f, FishingRules r, String what) {
        FishingJournal j = f.journal(p);
        if (what == null) {
            FishingRules.Species t = r.speciesById(j.tracked());
            p.sendMessage(t == null ? ChatColor.YELLOW + "Use: /fishing track <species>  (or off)"
                    : ChatColor.LIGHT_PURPLE + "★ Tracking " + t.rarity().color + itemName(t.item()) + ChatColor.GRAY + ". /fishing track off stops.");
            return;
        }
        if (what.equalsIgnoreCase("off") || what.equalsIgnoreCase("none")) {
            j.track(null);
            f.saveJournal(p);
            p.sendMessage(ChatColor.GRAY + "No species tracked.");
            return;
        }
        FishingRules.Species s = findSpecies(r, what);
        if (s == null) { p.sendMessage(ChatColor.RED + "No species called " + what + "."); return; }
        j.track(s.id());
        f.saveJournal(p);
        p.sendMessage(ChatColor.LIGHT_PURPLE + "★ Tracking " + s.rarity().color + itemName(s.item()) + ChatColor.GRAY
                + ": the journal checks what it needs where you fish. Tracking never changes the odds.");
    }

    static FishingRules.Species findSpecies(FishingRules r, String what) {
        String k = what.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        FishingRules.Species s = r.speciesById(k);
        if (s != null) return s;
        for (FishingRules.Species x : r.allSpecies().values()) {
            if (itemName(x.item()).toLowerCase(Locale.ROOT).replace(' ', '_').equals(k)) return x;
        }
        return null;
    }

    static void journal(CommandSender to, FishingJournal j, FishingRules r, String water, Player whose) {
        boolean any = false;
        for (FishingRules.Water w : r.waters()) {
            if (water != null && !w.id().equalsIgnoreCase(water) && !w.name().equalsIgnoreCase(water)) continue;
            List<FishingRules.Species> sp = r.species(w.id());
            if (sp.isEmpty()) continue;
            any = true;
            to.sendMessage(ChatColor.GOLD + w.name() + ChatColor.GRAY + " (" + whose.getName() + ")");
            for (FishingRules.Species s : sp) {
                String name = GatheringCatalog.current().item(s.item()) == null ? s.id() : GatheringCatalog.current().item(s.item()).name();
                String when = s.when().describe();
                if (j.caught(s.id())) {
                    double kg = j.best(s.id());
                    to.sendMessage(ChatColor.GREEN + "  ✔ " + s.rarity().color + name + ChatColor.GRAY + " · " + s.rarity().displayName
                            + (kg > 0 ? " · best " + String.format(Locale.ROOT, "%.2f kg", kg) : ""));
                } else {
                    to.sendMessage(ChatColor.DARK_GRAY + "  ✘ " + ChatColor.GRAY + name + ChatColor.DARK_GRAY + " · " + s.rarity().displayName
                            + " · level " + s.level() + (when.isEmpty() ? "" : " · " + when));
                }
            }
        }
        if (!any) to.sendMessage(ChatColor.GRAY + "No such water. Waters: " + String.join(", ", r.waters().stream().map(FishingRules.Water::id).toList()));
    }

    private void lures(Player p, Fishing f, FishingRules r) {
        FishingJournal j = f.journal(p);
        if (j.totalLures() == 0) {
            int from = r.techniques().creatures();
            p.sendMessage(ChatColor.GRAY + "Encounters: none banked yet" + ChatColor.DARK_GRAY + " (from Fishing " + from + ", a catch can bank one)");
        } else {
            String sel = f.selected(p);
            List<String> parts = new ArrayList<>();
            for (Map.Entry<String, Integer> e : j.lures().entrySet()) {
                FishingRules.Creature c = r.creature(e.getKey());
                String name = c == null ? e.getKey() : c.name();
                parts.add((e.getKey().equals(sel) ? ChatColor.AQUA + "▶ " : ChatColor.WHITE + "") + name + ChatColor.GRAY + " ×" + e.getValue());
            }
            p.sendMessage(ChatColor.GRAY + "Encounters: " + String.join(ChatColor.DARK_GRAY + ", ", parts));
            p.sendMessage(ChatColor.DARK_GRAY + "Sneak and cast into water to call " + (sel != null ? "the chosen one" : "one") + "; /fishing call <creature> chooses.");
        }
        String fight = f.fightOf(p.getUniqueId());
        if (fight != null) p.sendMessage(ChatColor.YELLOW + "Fighting now: " + fight.substring(0, fight.lastIndexOf(' ')).replace('_', ' '));
    }

    private void call(Player p, Fishing f, FishingRules r, String what) {
        FishingJournal j = f.journal(p);
        if (what == null) { p.sendMessage(ChatColor.YELLOW + "Use: /fishing call <creature>  (one you have an encounter banked for)"); return; }
        FishingRules.Creature c = find(r, what);
        if (c == null) { p.sendMessage(ChatColor.RED + "No creature called " + what + "."); return; }
        if (j.lures(c.id()) <= 0) { p.sendMessage(ChatColor.RED + "You have no encounter banked for the " + c.name() + "."); return; }
        f.select(p, c.id());
        p.sendMessage(ChatColor.AQUA + "▶ " + c.name() + ChatColor.GRAY + " (" + j.lures(c.id()) + "): Sneak and cast into water to call it.");
    }

    static FishingRules.Creature find(FishingRules r, String what) {
        String k = what.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        FishingRules.Creature c = r.creature(k);
        if (c != null) return c;
        for (FishingRules.Creature x : r.creatures().values()) if (x.name().toLowerCase(Locale.ROOT).replace(' ', '_').equals(k)) return x;
        return null;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return filter(SUBS, args[0]);
        if (args.length == 2 && args[0].equalsIgnoreCase("call") && sender instanceof Player p && plugin.getFishing() != null) {
            return filter(new ArrayList<>(plugin.getFishing().journal(p).lures().keySet()), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("journal")) return filter(rules().waters().stream().map(FishingRules.Water::id).toList(), args[1]);
        if (args.length == 2 && args[0].equalsIgnoreCase("track")) {
            List<String> ids = new ArrayList<>(rules().allSpecies().keySet());
            ids.add("off");
            return filter(ids, args[1]);
        }
        return List.of();
    }

    static List<String> filter(List<String> all, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        return all.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(p)).toList();
    }

    // ── /cerb fish (admin) ────────────────────────────────────────

    public void admin(CommandSender sender, String[] args) {
        Fishing f = plugin.getFishing();
        FishingRules r = rules();
        if (f == null || !r.enabled()) { sender.sendMessage(ChatColor.RED + "Fishing's rules are off (resources.yml fishing:)."); return; }
        String sub = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        switch (sub) {
            case "info" -> { if (sender instanceof Player p) info(p, f, r); else sender.sendMessage("Run it in game."); }
            case "lure" -> lure(sender, f, r, args);
            case "force" -> force(sender, f, r, args);
            case "journal" -> {
                Player t = args.length > 2 ? Bukkit.getPlayerExact(args[2]) : null;
                if (t == null) { sender.sendMessage(ChatColor.RED + "Use: /cerb fish journal <online player>"); return; }
                t.getScheduler().run(plugin, task -> {
                    FishingJournal j = f.journal(t);
                    journal(sender, j, r, null, t);
                    sender.sendMessage(ChatColor.GRAY + "Encounters: " + (j.lures().isEmpty() ? "none" : j.lures()) + ChatColor.GRAY + " · defeated: "
                            + j.creaturesDefeated() + " · fight: " + (j.active() == null ? "none" : j.active().creature() + " " + j.active().entity()));
                    FishingJournal.Summary su = j.summary();
                    sender.sendMessage(ChatColor.GRAY + "Tracked: " + (j.tracked() == null ? "none" : j.tracked()) + " · personal bests set: " + j.bestsSet()
                            + " · unread summary: " + su.catches() + " catches, " + su.xp() + " XP, " + su.species() + " species, " + su.bests()
                            + " bests, " + su.encounters() + " encounters · notable: " + j.notable().size() + " (" + j.unreadNotable() + " unread)");
                }, null);
            }
            case "stats" -> { for (String l : f.stats()) sender.sendMessage(ChatColor.GRAY + l); }
            default -> sender.sendMessage(ChatColor.YELLOW + "Use: /cerb fish <info|lure <player> <creature> [n] (an encounter)|force <player> <species <id>|treasure [id]|lure [creature]|apex [creature]>|journal <player>|stats>");
        }
    }

    /** The water you look at (within 30 blocks), as a catch there would see it at your level now. */
    private void info(Player p, Fishing f, FishingRules r) {
        Block b = p.getTargetBlockExact(30, FluidCollisionMode.ALWAYS);
        if (b == null || b.getType() != org.bukkit.Material.WATER) { p.sendMessage(ChatColor.GRAY + "Look at water within 30 blocks."); return; }
        FishingRules.Moment m = f.moment(r, b.getLocation().add(0.5, 0.9, 0.5));
        int level = plugin.getSkillManager().getSkillLevel(p, r.skill());
        p.sendMessage(ChatColor.GOLD + "Water: " + ChatColor.WHITE + (m.water() == null ? "none" : r.water(m.water()).name()) + ChatColor.GRAY
                + " · biome " + m.biome() + (m.night() ? " · night" : " · day") + (m.thunder() ? " · thunder" : m.rain() ? " · rain" : ""));
        List<FishingRules.Species> can = r.eligible(m, level);
        double keep = can.isEmpty() ? 1.0 : r.vanillaShare(level);
        double total = can.stream().mapToDouble(FishingRules.Species::weight).sum();
        p.sendMessage(ChatColor.GRAY + "At your Fishing " + level + ": a fish or junk bite stays vanilla " + pct(keep) + ", else:");
        for (FishingRules.Species s : can) {
            p.sendMessage(ChatColor.GRAY + "  " + s.rarity().color + s.id().replace('_', ' ') + ChatColor.GRAY + " " + pct((1 - keep) * s.weight() / total));
        }
        Manager.SecondaryEffects sec = plugin.getSecondaryEffects();
        double creature = sec == null ? 0 : sec.active(p.getUniqueId(), Attributes.Stat.SEA_CREATURE_CHANCE);
        double treasure = sec == null ? 0 : sec.active(p.getUniqueId(), Attributes.Stat.TREASURE_CHANCE);
        List<FishingRules.Creature> cs = m.water() == null ? List.of() : r.creaturesOf(m.water(), level);
        p.sendMessage(ChatColor.GRAY + "Encounter chance per catch " + pct(creature) + ": "
                + (cs.isEmpty() ? "no creature here at your level" : String.join(", ", cs.stream().map(FishingRules.Creature::name).toList()))
                + (m.water() != null && r.apexOf(m.water(), level) != null ? "; apex " + r.apexOf(m.water(), level).name() : ""));
        p.sendMessage(ChatColor.GRAY + "Treasure per catch in open water " + (r.treasureSense(level) ? pct(treasure) : "none (Treasure Sense at " + r.techniques().treasureSense() + ")")
                + " · bites " + pct(1 - r.waitFactor(sec == null ? 0 : sec.active(p.getUniqueId(), Attributes.Stat.LURE_SPEED), level)) + " sooner");
    }

    private void lure(CommandSender sender, Fishing f, FishingRules r, String[] args) {
        Player t = args.length > 2 ? Bukkit.getPlayerExact(args[2]) : null;
        FishingRules.Creature c = args.length > 3 ? find(r, args[3]) : null;
        int n = 1;
        try { if (args.length > 4) n = Math.max(1, Math.min(100, Integer.parseInt(args[4]))); } catch (NumberFormatException ignored) { /* 1 */ }
        if (t == null || c == null) {
            sender.sendMessage(ChatColor.RED + "Use: /cerb fish lure <online player> <creature> [1-100] (banks encounters). Creatures: " + String.join(", ", r.creatures().keySet()));
            return;
        }
        int count = n;
        t.getScheduler().run(plugin, task -> {
            FishingJournal j = f.journal(t);
            int added = 0;
            for (int i = 0; i < count; i++) if (j.addLure(c.id(), 0)) added++;
            t.getPersistentDataContainer().set(Fishing.JOURNAL, org.bukkit.persistence.PersistentDataType.STRING, j.encode());
            sender.sendMessage(ChatColor.GREEN + t.getName() + ": +" + added + " " + c.name() + " encounter(s), now " + j.lures(c.id()) + ".");
            plugin.getLogger().info("[Fishing] " + sender.getName() + " gave " + t.getName() + " " + added + " " + c.id() + " encounter(s)");
        }, null);
    }

    private void force(CommandSender sender, Fishing f, FishingRules r, String[] args) {
        Player t = args.length > 2 ? Bukkit.getPlayerExact(args[2]) : null;
        String kind = args.length > 3 ? args[3].toLowerCase(Locale.ROOT) : "";
        String id = args.length > 4 ? args[4].toLowerCase(Locale.ROOT) : null;
        if (t != null && kind.equals("clear")) { f.force(t.getUniqueId(), null); sender.sendMessage(ChatColor.GREEN + t.getName() + ": no forced catch."); return; }
        boolean ok = switch (kind) {
            case "species" -> id != null && r.speciesById(id) != null;
            case "treasure" -> id == null || r.treasureById(id) != null;
            case "lure", "apex" -> id == null || r.creature(id) != null;
            default -> false;
        };
        if (t == null || !ok) {
            sender.sendMessage(ChatColor.RED + "Use: /cerb fish force <online player> <species <id>|treasure [id]|lure [creature]|apex [creature]|clear>");
            return;
        }
        f.force(t.getUniqueId(), new Fishing.Force(kind, id));
        sender.sendMessage(ChatColor.GREEN + t.getName() + "'s next catch: " + kind + (id == null ? "" : " " + id) + ".");
    }

    private static String pct(double v) {
        return String.format(Locale.ROOT, v < 0.01 && v > 0 ? "%.2f%%" : "%.1f%%", v * 100);
    }
}

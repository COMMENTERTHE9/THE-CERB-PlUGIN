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

    static final List<String> SUBS = List.of("journal", "encounters", "call", "track", "gear", "setup", "reel", "restock");
    static final List<String> ADMIN = List.of("info", "lure", "force", "journal", "stats", "part", "bait", "legacyrod", "gear", "summary");

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
            case "gear" -> plugin.getMenuManager().openRoot(p, new GUIs.player.FishingEquipmentMenu(plugin));
            case "setup" -> setup(p, args);
            case "reel", "restock" -> toggle(p, sub, args.length > 1 ? args[1] : "");
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

    /** {@code /fishing setup <1-3>} uses a preset from the next cast; {@code /fishing setup rename <1-3> <name>} renames one. */
    private void setup(Player p, String[] args) {
        Gathering.Tackle tk = plugin.getTackle();
        if (tk == null || !GatheringCatalog.current().tackle().enabled()) { p.sendMessage(ChatColor.GRAY + "Fishing equipment is off on this server."); return; }
        boolean rename = args.length > 1 && args[1].equalsIgnoreCase("rename");
        int at = rename ? 2 : 1;
        int n;
        try { n = Integer.parseInt(args.length > at ? args[at] : "") - 1; } catch (NumberFormatException e) { n = -1; }
        if (n < 0 || n >= Gathering.FishingGear.PRESETS) {
            p.sendMessage(ChatColor.YELLOW + "Use: /fishing setup <1-3>  or  /fishing setup rename <1-3> <name>");
            return;
        }
        Gathering.Tackle.Result res = rename
                ? tk.rename(p, n, args.length > 3 ? String.join(" ", java.util.Arrays.copyOfRange(args, 3, args.length)) : "")
                : tk.activate(p, n);
        p.sendMessage((res.ok() ? ChatColor.GREEN : ChatColor.RED) + res.message());
    }

    /** {@code /fishing reel on|off}, {@code /fishing restock on|off}. */
    private void toggle(Player p, String what, String value) {
        Gathering.Tackle tk = plugin.getTackle();
        if (tk == null || !GatheringCatalog.current().tackle().enabled()) { p.sendMessage(ChatColor.GRAY + "Fishing equipment is off on this server."); return; }
        boolean on = value.equalsIgnoreCase("on");
        if (!on && !value.equalsIgnoreCase("off")) { p.sendMessage(ChatColor.YELLOW + "Use: /fishing " + what + " on|off"); return; }
        Gathering.Tackle.Result res = what.equals("reel") ? tk.autoReel(p, on) : tk.restock(p, on);
        p.sendMessage((res.ok() ? ChatColor.GREEN : ChatColor.RED) + res.message());
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
        if (args.length == 2 && (args[0].equalsIgnoreCase("reel") || args[0].equalsIgnoreCase("restock"))) return filter(List.of("on", "off"), args[1]);
        if (args.length == 2 && args[0].equalsIgnoreCase("setup")) return filter(List.of("1", "2", "3", "rename"), args[1]);
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
                    sender.sendMessage(ChatColor.GRAY + "Milestones granted: " + (j.grantedRewards().isEmpty() ? "none" : j.grantedRewards())
                            + " · trophies kept: " + j.notable().stream().filter(FishingJournal.Notable::trophy).count());
                }, null);
            }
            case "stats" -> { for (String l : f.stats()) sender.sendMessage(ChatColor.GRAY + l); }
            case "part", "bait", "legacyrod", "gear", "summary" -> gearAdmin(sender, sub, args);
            default -> sender.sendMessage(ChatColor.YELLOW + "Use: /cerb fish <info|lure <player> <creature> [n] (an encounter)|force <player> <species <id>|treasure [id]|lure [creature]|apex [creature]>|journal <player>|stats"
                    + "|part <player> <id> [remove]|bait <player> <id> [n]|legacyrod <player> <upgrade,...>|gear <player>|summary <player>>");
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
        // The same shares a bite rolls with, with your specialist's factor (Surveyor's Float; bait is held per cast).
        Gathering.Tackle tk = plugin.getTackle();
        java.util.function.ToDoubleFunction<FishingRules.Species> factor = tk == null ? x -> 1.0 : tk.infoFactor(p);
        java.util.Map<FishingRules.Species, Double> shares = r.shares(m, level, factor);
        boolean favoured = shares.keySet().stream().anyMatch(x -> factor.applyAsDouble(x) > 1.0);
        p.sendMessage(ChatColor.GRAY + "At your Fishing " + level + ": a fish or junk bite stays vanilla " + pct(keep) + ", else"
                + (favoured ? " (with your Surveyor's Float: species you haven't caught ×" + trim(factor, shares) + ")" : "") + ":");
        for (java.util.Map.Entry<FishingRules.Species, Double> e : shares.entrySet()) {
            FishingRules.Species s = e.getKey();
            p.sendMessage(ChatColor.GRAY + "  " + s.rarity().color + s.id().replace('_', ' ') + ChatColor.GRAY + " " + pct(e.getValue())
                    + (factor.applyAsDouble(s) > 1.0 ? ChatColor.LIGHT_PURPLE + " ×" + String.format(Locale.ROOT, "%.2f", factor.applyAsDouble(s)) : ""));
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
            if (j.readOnly()) { sender.sendMessage(ChatColor.RED + t.getName() + "'s journal was saved by a newer version: left as it is."); return; }
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

    /**
     * Admin tools for Fishing equipment (docs/design/FISHING_EQUIPMENT.md; the caller holds cerb.admin): give or take a part,
     * put bait into a bag, make an old-style upgraded rod (to test converting), show a player's equipment or summary. Each
     * runs on that player's thread and is logged.
     */
    private void gearAdmin(CommandSender sender, String sub, String[] args) {
        Gathering.Tackle tk = plugin.getTackle();
        Player t = args.length > 2 ? Bukkit.getPlayerExact(args[2]) : null;
        if (tk == null || t == null) { sender.sendMessage(ChatColor.RED + "Use: /cerb fish " + sub + " <online player> ..."); return; }
        String a3 = args.length > 3 ? args[3].toLowerCase(Locale.ROOT) : null;
        String a4 = args.length > 4 ? args[4].toLowerCase(Locale.ROOT) : null;
        t.getScheduler().run(plugin, task -> {
            switch (sub) {
                case "part" -> {
                    if (a3 == null || GatheringCatalog.current().tackle().part(a3) == null && !"remove".equals(a4)) {
                        sender.sendMessage(ChatColor.RED + "Parts: " + String.join(", ", GatheringCatalog.current().tackle().parts().keySet()));
                        return;
                    }
                    boolean done = "remove".equals(a4) ? tk.adminDisown(t, a3) : tk.grantPart(t, a3);
                    sender.sendMessage((done ? ChatColor.GREEN : ChatColor.GRAY) + t.getName() + ": " + a3 + ("remove".equals(a4) ? " taken away" : " given")
                            + (done ? "." : " (nothing changed)."));
                    plugin.getLogger().info("[Fishing] " + sender.getName() + (("remove".equals(a4)) ? " took " : " gave ") + t.getName() + " part " + a3);
                }
                case "bait" -> {
                    int n = 16;
                    try { if (a4 != null) n = Math.max(1, Math.min(1024, Integer.parseInt(a4))); } catch (NumberFormatException ignored) { /* 16 */ }
                    int in = a3 == null ? 0 : tk.adminBait(t, a3, n);
                    sender.sendMessage((in > 0 ? ChatColor.GREEN : ChatColor.RED) + t.getName() + ": +" + in + " " + a3 + " in the Bait Bag"
                            + (in > 0 ? "." : ". Bait: " + String.join(", ", GatheringCatalog.current().tackle().bait().keySet())));
                    if (in > 0) plugin.getLogger().info("[Fishing] " + sender.getName() + " gave " + t.getName() + " " + in + " " + a3);
                }
                case "legacyrod" -> {
                    if (a3 == null) { sender.sendMessage(ChatColor.RED + "Use: /cerb fish legacyrod <player> <upgrade,upgrade,...>"); return; }
                    // The rod in their main hand gets the upgrades the old way (its name, enchantments and damage stay, as
                    // the old crafting recipe kept them); without one, a new rod.
                    org.bukkit.inventory.PlayerInventory inv = t.getInventory();
                    boolean inHand = inv.getItemInMainHand().getType() == org.bukkit.Material.FISHING_ROD;
                    org.bukkit.inventory.ItemStack rod = inHand ? inv.getItemInMainHand().clone() : new org.bukkit.inventory.ItemStack(org.bukkit.Material.FISHING_ROD);
                    List<String> made = new ArrayList<>();
                    for (String id : a3.split(",")) {
                        GatheringCatalog.UpgradeDef u = GatheringCatalog.current().upgrade(id.trim());
                        if (u == null || !u.on().contains("rod")) continue;
                        org.bukkit.inventory.ItemStack next = Gathering.GearItems.withUpgrade(rod, u);
                        if (next != null) { rod = next; made.add(u.id()); }
                    }
                    if (made.isEmpty()) { sender.sendMessage(ChatColor.RED + "No rod upgrade (new to this rod) among " + a3); return; }
                    if (inHand) inv.setItemInMainHand(rod);
                    else for (org.bukkit.inventory.ItemStack left : inv.addItem(rod).values()) t.getWorld().dropItem(t.getLocation(), left);
                    sender.sendMessage(ChatColor.GREEN + t.getName() + ": " + (inHand ? "the rod in hand" : "a new rod") + " upgraded the old way with " + made + ".");
                    plugin.getLogger().info("[Fishing] " + sender.getName() + " upgraded " + t.getName() + "'s rod the old way with " + made);
                }
                case "gear" -> { for (String l : tk.describe(t)) sender.sendMessage(ChatColor.GRAY + l); }
                case "summary" -> {
                    FishingJournal j = plugin.getFishing().journal(t);
                    FishingJournal.Summary su = j.summary();
                    sender.sendMessage(ChatColor.GRAY + t.getName() + ": " + su.catches() + " catches, " + su.xp() + " XP, " + su.species() + " species, "
                            + su.bests() + " bests, " + su.encounters() + " encounters; notable " + j.notable());
                }
                default -> { }
            }
        }, null);
    }

    /** The largest factor among {@code shares}' species, for the header line. */
    private static String trim(java.util.function.ToDoubleFunction<FishingRules.Species> f, java.util.Map<FishingRules.Species, Double> shares) {
        double max = 1.0;
        for (FishingRules.Species s : shares.keySet()) max = Math.max(max, f.applyAsDouble(s));
        return String.format(Locale.ROOT, "%.2f", max);
    }

    private static String pct(double v) {
        return String.format(Locale.ROOT, v < 0.01 && v > 0 ? "%.2f%%" : "%.1f%%", v * 100);
    }
}

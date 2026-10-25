package Commands;

import Classes.AbilityDef;
import Classes.ClassDef;
import Classes.ClassProfile;
import Classes.ClassRanks;
import Classes.ClassService;
import Classes.Loadout;
import Classes.WeaponFamily;
import cerberus.world.cerb.CerberusPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * /class: your classes, their abilities and your loadouts (docs/design/CLASSES.md). Until the
 * Player Menu screen exists this is how classes are used. Every change goes through
 * {@link ClassService}, which validates it on the server (combat lock, unlocks, slot rules).
 * Also the admin side, /cerb class &lt;player&gt; &lt;class&gt; rank &lt;n&gt; | info.
 */
public final class ClassCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = List.of("info", "abilities", "equip", "loadout", "name", "controls", "bar");
    private static final List<String> SLOTS = List.of("a1", "a2", "a3", "a4", "a5", "p1", "p2", "p3", "k");

    private final CerberusPlugin plugin;

    public ClassCommand(CerberusPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Only players have classes. Admins: /cerb class <player> info");
            return true;
        }
        ClassService svc = plugin.getClassService();
        if (svc == null || svc.catalog().classes().isEmpty()) {
            p.sendMessage(ChatColor.RED + "Classes are not available right now.");
            return true;
        }
        ClassProfile prof = svc.profile(p.getUniqueId());
        if (prof == null) {
            p.sendMessage(ChatColor.RED + "Your classes are still loading. Try again in a moment.");
            return true;
        }
        String sub = args.length == 0 ? "info" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "info" -> info(p, p.getUniqueId(), svc, prof);
            case "abilities", "tree" -> abilities(p, svc, prof, args);
            case "equip" -> equip(p, svc, prof, args);
            case "loadout", "use" -> {
                Integer n = args.length == 2 ? loadoutNumber(args[1], prof) : null;
                if (n == null) { p.sendMessage(ChatColor.RED + "Use: /class loadout <1-" + prof.loadouts().size() + ">"); return true; }
                String why = svc.switchTo(p, n);
                p.sendMessage(why == null ? ChatColor.GREEN + "Now using Loadout " + (n + 1) + " (" + prof.activeLoadout().name() + ")."
                        : ChatColor.RED + why);
            }
            case "name", "rename" -> {
                Integer n = args.length >= 3 ? loadoutNumber(args[1], prof) : null;
                if (n == null) { p.sendMessage(ChatColor.RED + "Use: /class name <1-" + prof.loadouts().size() + "> <name>"); return true; }
                String why = svc.rename(p, n, String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length)));
                p.sendMessage(why == null ? ChatColor.GREEN + "Loadout " + (n + 1) + " is now called " + prof.loadouts().get(n).name() + "."
                        : ChatColor.RED + why);
            }
            case "controls", "keys" -> {
                Classes.Controls.Scheme scheme = args.length == 2 ? Classes.Controls.Scheme.parse(args[1]) : null;
                if (args.length == 2 && scheme == null) { p.sendMessage(ChatColor.RED + "Use: /class controls [quick|combo]"); return true; }
                if (scheme != null) svc.setScheme(p, scheme);
                controls(p, svc);
            }
            case "bar" -> {
                Classes.Controls.BarMode mode = args.length == 2 ? Classes.Controls.BarMode.parse(args[1]) : null;
                if (mode == null) {
                    p.sendMessage(ChatColor.YELLOW + "Ability bar: " + svc.barMode(p.getUniqueId()).id()
                            + ChatColor.GRAY + "  (/class bar auto|on|off - auto: in a fight and just after a press)");
                    return true;
                }
                svc.setBarMode(p, mode);
                p.sendMessage(ChatColor.GREEN + "Ability bar: " + mode.id() + ".");
            }
            default -> p.sendMessage(ChatColor.YELLOW + "Use: /class [info] | abilities [class] | equip [loadout] <a1-a5|p1-p3|k> <ability|none>"
                    + " | loadout <n> | name <n> <name> | controls [quick|combo] | bar [auto|on|off]");
        }
        return true;
    }

    // ── screens (chat) ──────────────────────────────────────────

    /** The player's control scheme and every key, with the other scheme to switch to. */
    private void controls(Player p, ClassService svc) {
        Classes.Controls.Scheme scheme = svc.scheme(p.getUniqueId());
        p.sendMessage(ChatColor.GOLD + "✦ Controls: " + scheme.displayName + ChatColor.GRAY + " (with a weapon, a focus or an empty hand)");
        ClassProfile prof = svc.profile(p.getUniqueId());
        for (int i = 0; i < Loadout.ACTIVE_SLOTS; i++) {
            AbilityDef a = prof == null ? null : svc.catalog().ability(prof.activeLoadout().actives().get(i));
            p.sendMessage(ChatColor.WHITE + "  " + Classes.Controls.keyLabel(scheme, i, false) + ChatColor.GRAY + "  active " + (i + 1)
                    + (a == null ? "" : ChatColor.DARK_GRAY + " (" + a.displayName() + ")"));
        }
        p.sendMessage(ChatColor.DARK_GRAY + "  With a bow or crossbow, right and left swap. "
                + (scheme == Classes.Controls.Scheme.QUICK ? "Q never drops a weapon then." : "Q still drops."));
        p.sendMessage(ChatColor.DARK_GRAY + "  A shield, food or another usable item in the off hand keeps right-click for itself.");
        Classes.Controls.Scheme other = scheme == Classes.Controls.Scheme.QUICK ? Classes.Controls.Scheme.COMBO : Classes.Controls.Scheme.QUICK;
        p.sendMessage(ChatColor.GRAY + "  Try " + other.displayName + ": /class controls " + other.id());
    }

    /** Classes with rank and XP (or what's missing), the loadout in use and running cooldowns. Owner's thread. */
    void info(CommandSender to, UUID owner, ClassService svc, ClassProfile prof) {
        Classes.Controls.Scheme scheme = svc.scheme(owner);
        StringBuilder keys = new StringBuilder();
        for (int i = 0; i < Loadout.ACTIVE_SLOTS; i++) {
            if (i > 0) keys.append(", ");
            keys.append(Classes.Controls.keyLabel(scheme, i, false)).append(" A").append(i + 1);
        }
        to.sendMessage(ChatColor.GOLD + "✦ Classes " + ChatColor.DARK_GRAY + "(" + scheme.displayName + ": " + keys + "; /class controls)");
        for (ClassDef c : svc.catalog().classes().values()) {
            ClassProfile.Progress pr = prof.classes().get(c.id());
            if (pr != null) {
                String xp = pr.rank() >= ClassRanks.MAX_RANK ? "max rank"
                        : pr.xp() + "/" + ClassRanks.xpForRank(pr.rank()) + " XP";
                to.sendMessage(ChatColor.YELLOW + "  " + c.displayName() + ": rank " + pr.rank() + ChatColor.GRAY + " (" + xp + ") "
                        + ChatColor.DARK_GRAY + c.role());
            } else {
                to.sendMessage(ChatColor.DARK_GRAY + "  " + c.displayName() + ": not learned - needs " + svc.requirements(c, owner));
            }
        }
        for (int i = 0; i < prof.loadouts().size(); i++) {
            Loadout l = prof.loadouts().get(i);
            boolean active = i == prof.activeIndex();
            to.sendMessage((active ? ChatColor.GREEN + "  ▶ " : ChatColor.GRAY + "    ") + "Loadout " + (i + 1) + " (" + l.name() + ")"
                    + (active ? " in use" : ""));
            ChatColor c = active ? ChatColor.WHITE : ChatColor.GRAY;
            StringBuilder a = new StringBuilder("     ");
            for (int s = 0; s < Loadout.ACTIVE_SLOTS; s++) a.append(s == 0 ? "" : " | ").append("A").append(s + 1).append(' ').append(name(svc, l.actives().get(s)));
            to.sendMessage(c + a.toString());
            StringBuilder ps = new StringBuilder("     ");
            for (int s = 0; s < Loadout.PASSIVE_SLOTS; s++) ps.append(s == 0 ? "" : " | ").append("P").append(s + 1).append(' ').append(name(svc, l.passives().get(s)));
            ps.append(" | K ").append(name(svc, l.keystone()));
            to.sendMessage(c + ps.toString());
        }
        long now = System.currentTimeMillis();
        List<String> cds = new ArrayList<>();
        for (AbilityDef a : svc.catalog().abilities().values()) {
            long left = a.isActive() ? prof.cooldowns().remaining(a, now) : 0;
            if (left >= 500) cds.add(a.displayName() + " " + String.format(Locale.ROOT, "%.1f s", left / 1000.0));
        }
        if (!cds.isEmpty()) to.sendMessage(ChatColor.GRAY + "  Cooldowns: " + String.join(", ", cds));
        if (svc.inCombat(owner)) to.sendMessage(ChatColor.RED + "  In combat: loadout changes wait until 8 s after the last hit.");
    }

    /** One class's abilities: unlocked or the rank still needed, kind, hand, cost and cooldown. */
    private void abilities(Player p, ClassService svc, ClassProfile prof, String[] args) {
        List<ClassDef> which = new ArrayList<>();
        if (args.length >= 2) {
            ClassDef c = findClass(svc, args[1]);
            if (c == null) { p.sendMessage(ChatColor.RED + "Unknown class: " + args[1]); return; }
            which.add(c);
        } else {
            for (ClassDef c : svc.catalog().classes().values()) if (prof.knows(c.id())) which.add(c);
            if (which.isEmpty()) { p.sendMessage(ChatColor.GRAY + "You haven't learned a class yet. /class abilities <class> shows any class."); return; }
        }
        for (ClassDef c : which) {
            p.sendMessage(ChatColor.GOLD + "✦ " + c.displayName() + ChatColor.GRAY + " (rank " + prof.rank(c.id()) + ") - " + c.signature());
            for (String id : c.abilities()) {
                AbilityDef a = svc.catalog().ability(id);
                if (a == null) continue;
                boolean open = prof.unlocked(a);
                StringBuilder line = new StringBuilder();
                line.append(open ? ChatColor.GREEN + "  ✔ " : ChatColor.DARK_GRAY + "  ✖ ")
                        .append(open ? ChatColor.WHITE : ChatColor.GRAY).append(a.displayName())
                        .append(ChatColor.DARK_GRAY).append(" [").append(a.id()).append("] ")
                        .append(a.kind().name().toLowerCase(Locale.ROOT));
                if (!open) line.append(", rank ").append(a.unlockRank());
                if (a.isActive()) {
                    line.append(", ").append(hand(a)).append(", ").append(String.format(Locale.ROOT, "%.1f s", a.cooldownMs() / 1000.0));
                    if (a.essenceCost() > 0) line.append(", ").append(Math.round(a.essenceCost())).append(" Essence");
                }
                p.sendMessage(line.toString());
                p.sendMessage(ChatColor.GRAY + "      " + a.description());
            }
        }
    }

    /** /class equip [loadout] <a1-a5|p1-p3|k> <ability|none> */
    private void equip(Player p, ClassService svc, ClassProfile prof, String[] args) {
        int i = 1;
        int loadout = prof.activeIndex();
        if (args.length == 4) {
            Integer n = loadoutNumber(args[1], prof);
            if (n == null) { p.sendMessage(ChatColor.RED + "No such loadout: " + args[1]); return; }
            loadout = n;
            i = 2;
        } else if (args.length != 3) {
            p.sendMessage(ChatColor.RED + "Use: /class equip [loadout] <a1-a5|p1-p3|k> <ability|none>");
            return;
        }
        String slotArg = args[i].toLowerCase(Locale.ROOT);
        ClassService.Slot slot;
        int pos;
        if (slotArg.equals("k")) { slot = ClassService.Slot.KEYSTONE; pos = 0; }
        else if (slotArg.matches("[ap][1-5]")) {
            slot = slotArg.charAt(0) == 'a' ? ClassService.Slot.ACTIVE : ClassService.Slot.PASSIVE;
            pos = slotArg.charAt(1) - '1';
        } else { p.sendMessage(ChatColor.RED + "Slots: a1-a5 (actives), p1-p3 (passives), k (keystone)."); return; }
        String abilityId = null;
        if (!args[i + 1].equalsIgnoreCase("none")) {
            AbilityDef a = findAbility(svc, args[i + 1]);
            if (a == null) { p.sendMessage(ChatColor.RED + "Unknown ability: " + args[i + 1] + " (/class abilities lists them)"); return; }
            abilityId = a.id();
        }
        String why = svc.equip(p, loadout, slot, pos, abilityId);
        if (why != null) { p.sendMessage(ChatColor.RED + why); return; }
        p.sendMessage(ChatColor.GREEN + (abilityId == null ? "Cleared " : "Equipped " + name(svc, abilityId) + " in ")
                + slotArg.toUpperCase(Locale.ROOT) + " of Loadout " + (loadout + 1) + ".");
    }

    // ── admin: /cerb class <player> <class> rank <n> | /cerb class <player> info ──

    public void admin(CommandSender sender, String[] args) {
        String usage = ChatColor.RED + "Use: /cerb class <player> <class> rank <1-" + ClassRanks.MAX_RANK + ">  or  /cerb class <player> info|cooldowns";
        ClassService svc = plugin.getClassService();
        if (svc == null) { sender.sendMessage(ChatColor.RED + "Classes are not running."); return; }
        if (args.length < 3) { sender.sendMessage(usage); return; }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) { sender.sendMessage(ChatColor.RED + "Player not online: " + args[1]); return; }
        if (args.length == 3 && args[2].equalsIgnoreCase("info")) {
            target.getScheduler().run(plugin, t -> {
                ClassProfile prof = svc.profile(target.getUniqueId());
                if (prof == null) { sender.sendMessage(ChatColor.RED + target.getName() + "'s classes are not loaded."); return; }
                sender.sendMessage(ChatColor.GOLD + target.getName() + ":");
                info(sender, target.getUniqueId(), svc, prof);
            }, null);
            return;
        }
        if (args.length == 3 && args[2].equalsIgnoreCase("cooldowns")) {
            // Test tool: every ability is ready again (cooldowns only; nothing else changes).
            target.getScheduler().run(plugin, t -> {
                ClassProfile prof = svc.profile(target.getUniqueId());
                if (prof == null) { sender.sendMessage(ChatColor.RED + target.getName() + "'s classes are not loaded."); return; }
                prof.cooldowns().clear();
                sender.sendMessage(ChatColor.GREEN + target.getName() + "'s ability cooldowns are reset.");
            }, null);
            return;
        }
        if (args.length != 5 || !args[3].equalsIgnoreCase("rank")) { sender.sendMessage(usage); return; }
        ClassDef c = findClass(svc, args[2]);
        if (c == null) { sender.sendMessage(ChatColor.RED + "Unknown class: " + args[2]); return; }
        int rank;
        try { rank = Integer.parseInt(args[4]); } catch (NumberFormatException e) { sender.sendMessage(usage); return; }
        if (rank < 1 || rank > ClassRanks.MAX_RANK) { sender.sendMessage(usage); return; }
        target.getScheduler().run(plugin, t -> {
            if (svc.profile(target.getUniqueId()) == null) {
                sender.sendMessage(ChatColor.RED + target.getName() + "'s classes are not loaded - nothing changed.");
                return;
            }
            svc.setRank(target, c.id(), rank);
            sender.sendMessage(ChatColor.GREEN + target.getName() + ": " + c.displayName() + " is now rank "
                    + svc.profile(target.getUniqueId()).rank(c.id()));
        }, null);
    }

    public List<String> adminComplete(String[] args) {
        ClassService svc = plugin.getClassService();
        if (svc == null) return List.of();
        return switch (args.length) {
            case 2 -> filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[1]);
            case 3 -> { List<String> o = new ArrayList<>(svc.catalog().classes().keySet()); o.add("info"); o.add("cooldowns"); yield filter(o, args[2]); }
            case 4 -> args[2].equalsIgnoreCase("info") || args[2].equalsIgnoreCase("cooldowns") ? List.of() : filter(List.of("rank"), args[3]);
            default -> List.of();
        };
    }

    // ── helpers ─────────────────────────────────────────────────

    private static String hand(AbilityDef a) {
        if (a.weapons().isEmpty() || a.weapons().contains(WeaponFamily.ANY)) return "any hand";
        List<String> w = new ArrayList<>();
        for (WeaponFamily f : a.weapons()) w.add(f.name().toLowerCase(Locale.ROOT));
        return String.join("/", w);
    }

    private static String name(ClassService svc, String abilityId) {
        if (abilityId == null) return "-";
        AbilityDef a = svc.catalog().ability(abilityId);
        return a == null ? abilityId + " (unavailable)" : a.displayName();
    }

    private static Integer loadoutNumber(String s, ClassProfile prof) {
        try {
            int n = Integer.parseInt(s) - 1;
            return n >= 0 && n < prof.loadouts().size() ? n : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String norm(String s) { return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", ""); }

    static AbilityDef findAbility(ClassService svc, String input) {
        String n = norm(input);
        for (AbilityDef a : svc.catalog().abilities().values()) if (norm(a.id()).equals(n) || norm(a.displayName()).equals(n)) return a;
        return null;
    }

    static ClassDef findClass(ClassService svc, String input) {
        String n = norm(input);
        for (ClassDef c : svc.catalog().classes().values()) if (norm(c.id()).equals(n) || norm(c.displayName()).equals(n)) return c;
        return null;
    }

    private static List<String> filter(List<String> options, String typed) {
        String t = typed.toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(t)).toList();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player p)) return List.of();
        ClassService svc = plugin.getClassService();
        ClassProfile prof = svc == null ? null : svc.profile(p.getUniqueId());
        if (args.length == 1) return filter(SUBS, args[0]);
        if (svc == null || prof == null) return List.of();
        String sub = args[0].toLowerCase(Locale.ROOT);
        List<String> loadouts = new ArrayList<>();
        for (int i = 1; i <= prof.loadouts().size(); i++) loadouts.add(String.valueOf(i));
        return switch (sub) {
            case "abilities", "tree" -> args.length == 2 ? filter(new ArrayList<>(svc.catalog().classes().keySet()), args[1]) : List.of();
            case "loadout", "use", "name", "rename" -> args.length == 2 ? filter(loadouts, args[1]) : List.of();
            case "controls", "keys" -> args.length == 2 ? filter(List.of("quick", "combo"), args[1]) : List.of();
            case "bar" -> args.length == 2 ? filter(List.of("auto", "on", "off"), args[1]) : List.of();
            case "equip" -> {
                boolean withLoadout = args.length >= 2 && args[1].matches("\\d+");
                int slotIdx = withLoadout ? 2 : 1;
                if (args.length == 2) { List<String> o = new ArrayList<>(SLOTS); o.addAll(loadouts); yield filter(o, args[1]); }
                if (args.length == slotIdx + 1) yield filter(SLOTS, args[slotIdx]);
                if (args.length == slotIdx + 2) {
                    String slot = args[slotIdx].toLowerCase(Locale.ROOT);
                    AbilityDef.Kind kind = slot.startsWith("a") ? AbilityDef.Kind.ACTIVE : slot.startsWith("p") ? AbilityDef.Kind.PASSIVE : AbilityDef.Kind.KEYSTONE;
                    List<String> o = new ArrayList<>();
                    o.add("none");
                    for (AbilityDef a : svc.catalog().abilities().values()) if (a.kind() == kind && prof.unlocked(a)) o.add(a.id());
                    yield filter(o, args[slotIdx + 1]);
                }
                yield List.of();
            }
            default -> List.of();
        };
    }
}

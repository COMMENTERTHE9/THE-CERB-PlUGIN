package GUIs.admin;

import GUIs.SkillDefinitions;
import GUIs.menu.Fmt;
import GUIs.menu.MenuView;
import GUIs.menu.Ui;
import Manager.CombatConfig;
import Manager.MobRegistry;
import Manager.Tier;
import cerberus.world.cerb.BuildInfo;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.util.EnumMap;
import java.util.Map;
import java.util.stream.Collectors;

/** Read-only diagnostics. Nothing on this screen changes anything. */
final class ServerInfoMenu extends AdminMenu {

    ServerInfoMenu(AdminPanel panel) { super(panel); }

    @Override public String title() { return PREFIX + "Server & Plugin"; }

    @Override
    protected void build(MenuView v) {
        frame(v);
        v.refreshButton();
        v.set(MenuView.HEADER, Ui.item(Material.COMPARATOR, ChatColor.WHITE + "" + ChatColor.BOLD + "Server & Plugin",
                Ui.lore().text("Read-only. Press Refresh for new values.").build()));

        // Plugin
        File db = new File(plugin.getDataFolder(), "cerberus.db");
        v.set(19, Ui.item(Material.ENCHANTED_BOOK, ChatColor.LIGHT_PURPLE + "Cerberus plugin",
                Ui.lore().stat("Version", plugin.getPluginMeta().getVersion())
                        .stat("Built", BuildInfo.buildTime())
                        .stat("Data folder", "plugins/" + plugin.getDataFolder().getName())
                        .stat("Database", db.exists() ? "cerberus.db, " + Fmt.bytes(db.length()) : "missing").build()));

        // Server + JVM
        Runtime rt = Runtime.getRuntime();
        long used = rt.totalMemory() - rt.freeMemory();
        v.set(20, Ui.item(Material.BEACON, ChatColor.AQUA + "Server",
                Ui.lore().stat("Software", Bukkit.getName())
                        .stat("Version", Bukkit.getVersion())
                        .stat("Minecraft", Bukkit.getMinecraftVersion())
                        .stat("Java", System.getProperty("java.version") + " (" + System.getProperty("java.vendor") + ")")
                        .stat("Uptime", Fmt.duration(ManagementFactory.getRuntimeMXBean().getUptime()))
                        .stat("Memory", Fmt.bytes(used) + " used / " + Fmt.bytes(rt.maxMemory()) + " max")
                        .stat("CPU cores", String.valueOf(rt.availableProcessors())).build()));

        // Performance (Folia ticks per region; some global numbers may be unavailable)
        Ui.Lore perf = Ui.lore();
        try {
            double[] tps = Bukkit.getTPS();
            perf.stat("TPS 1m / 5m / 15m", Fmt.one(tps[0]) + " / " + Fmt.one(tps[1]) + " / " + Fmt.one(tps[2]));
        } catch (Throwable t) {
            perf.stat("TPS", "n/a on Folia (each region ticks separately)");
        }
        try {
            perf.stat("Average tick", Fmt.one(Bukkit.getAverageTickTime()) + " ms");
        } catch (Throwable t) {
            perf.stat("Average tick", "n/a on Folia");
        }
        perf.text("Use the spark profiler for deep checks.");
        v.set(21, Ui.item(Material.CLOCK, ChatColor.GREEN + "Performance", perf.build()));

        // Players
        Ui.Lore players = Ui.lore().stat("Online", Bukkit.getOnlinePlayers().size() + " / " + Bukkit.getMaxPlayers())
                .stat("Online mode", String.valueOf(Bukkit.getOnlineMode()));
        Bukkit.getOnlinePlayers().stream().limit(8).forEach(p -> players.text("  " + p.getName() + (p.isOp() ? " (op)" : "")));
        v.set(22, Ui.item(Material.PLAYER_HEAD, ChatColor.YELLOW + "Players", players.build()));

        // Worlds
        Ui.Lore worlds = Ui.lore();
        for (World w : Bukkit.getWorlds()) {
            var s = w.getSpawnLocation();
            worlds.raw(ChatColor.WHITE + w.getName() + ChatColor.GRAY + "  (" + Fmt.title(w.getEnvironment().name()) + ")");
            worlds.text("  spawn " + s.getBlockX() + ", " + s.getBlockY() + ", " + s.getBlockZ()
                    + "   players " + w.getPlayers().size());
        }
        v.set(23, Ui.item(Material.GRASS_BLOCK, ChatColor.GREEN + "Worlds", worlds.build()));

        // Combat config
        CombatConfig cc = plugin.getCombatConfig();
        var hs = cc.mobHealthScaling;
        v.set(24, Ui.item(Material.IRON_SWORD, ChatColor.GOLD + "Combat config (combat.yml)",
                Ui.lore().stat("Mob HP scaling", hs.isEnabled() ? "on, applies to " + hs.getApplyTo() : "off")
                        .stat("Mob max level", String.valueOf(hs.getMaxLevel()))
                        .stat("Level-1 Common pool", Fmt.compact(hs.calculate(Tier.COMMON, 1)))
                        .stat("Level-100 Primordial pool", Fmt.compact(hs.calculate(Tier.PRIMORDIAL, 100)))
                        .stat("Mob damage scaling", cc.mobDamageScaling.isEnabled() ? "on" : "off")
                        .stat("Hit invulnerability", cc.damageInvincibilityMs + " ms")
                        .stat("Armor reduction cap", Fmt.pct(cc.armorMaxReduction)).build()));

        // Registries
        MobRegistry reg = plugin.getMobRegistry();
        Map<Tier, Long> byTier = new EnumMap<>(Tier.class);
        reg.getEntries().forEach(e -> byTier.merge(e.getTier(), 1L, Long::sum));
        String tierLine = byTier.entrySet().stream().map(e -> e.getKey().displayName + " " + e.getValue()).collect(Collectors.joining(", "));
        v.set(25, Ui.item(Material.SPAWNER, ChatColor.DARK_PURPLE + "Registries",
                Ui.lore().stat("mobs.yml entries", reg.getEntries().size() + "  (" + tierLine + ")")
                        .stat("Skills registered", String.valueOf(plugin.getSkillManager().getSkills().size()))
                        .stat("Skill definitions", String.valueOf(SkillDefinitions.all().size())).build()));

        // Runtime state
        int sessions = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (plugin.getPlayerSessionManager().getSession(p.getUniqueId()) != null) sessions++;
        }
        v.set(31, Ui.item(Material.OBSERVER, ChatColor.WHITE + "Runtime state",
                Ui.lore().stat("Player sessions", String.valueOf(sessions))
                        .stat("Mob HP pools tracked", Fmt.grouped(plugin.getMobVirtualHealthManager().size()))
                        .stat("Players with a menu open", String.valueOf(panel.menus().activeViewers()))
                        .stat("Database connections since start", Fmt.grouped(plugin.getDatabaseManager().connectionsOpened())).build()));
    }
}

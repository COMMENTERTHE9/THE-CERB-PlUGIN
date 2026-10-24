package GUIs.admin;

import GUIs.menu.MenuView;
import GUIs.menu.Ui;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.List;

/** Hub for combat testing. */
final class CombatLabMenu extends AdminMenu {

    static final double NEARBY_RADIUS = 24;
    static final double CLEANUP_RADIUS = 64;

    CombatLabMenu(AdminPanel panel) { super(panel); }

    @Override public String title() { return PREFIX + "Combat Lab"; }

    @Override
    protected void build(MenuView v) {
        frame(v);
        v.refreshButton();
        Player me = v.viewer();
        List<LivingEntity> nearby = panel.mobTools().nearbyMobs(me, NEARBY_RADIUS);
        long tests = panel.mobTools().nearbyMobs(me, CLEANUP_RADIUS).stream().filter(plugin.getMobRegistry()::isTestSpawn).count();
        boolean debug = plugin.isCombatDebugEnabled(me.getUniqueId());

        v.set(MenuView.HEADER, Ui.item(Material.IRON_SWORD, ChatColor.GOLD + "" + ChatColor.BOLD + "Combat Lab",
                Ui.lore().stat("Mobs within " + (int) NEARBY_RADIUS + " blocks", String.valueOf(nearby.size()))
                        .stat("Your test mobs within " + (int) CLEANUP_RADIUS, String.valueOf(tests))
                        .stat("Mob HP pools tracked", String.valueOf(plugin.getMobVirtualHealthManager().size()))
                        .text("Spawns use the mob registry and the").text("normal scaling rules.").build()));

        v.button(20, Ui.item(Material.SPAWNER, ChatColor.GREEN + "Spawn a test mob",
                Ui.lore().text("Pick a registered or vanilla mob,").text("choose its level, AI and count.")
                        .hint("Click to choose a mob").build()),
                c -> open(c.player(), new MobPickerMenu(panel)));

        v.button(22, Ui.item(Material.SPYGLASS, ChatColor.AQUA + "Nearby mobs",
                Ui.lore().text("Tier, level, HP pool, damage multiplier.").text("Heal, set HP, freeze, highlight, remove.")
                        .stat("Within " + (int) NEARBY_RADIUS + " blocks", String.valueOf(nearby.size()))
                        .hint("Click to inspect").build()),
                c -> open(c.player(), new NearbyMobsMenu(panel)));

        v.button(24, Ui.item(Material.LAVA_BUCKET, ChatColor.RED + "Clean up my test mobs",
                Ui.lore().text("Removes only mobs spawned from this panel,").text("within " + (int) CLEANUP_RADIUS + " blocks of you. Nothing else.")
                        .stat("Test mobs nearby", String.valueOf(tests))
                        .hint("Click (asks to confirm)").build()),
                c -> confirm(c.player(), "Remove " + tests + " test mob(s)?",
                        List.of("Only mobs spawned from the Control Center,", "within " + (int) CLEANUP_RADIUS + " blocks of you."),
                        p -> {
                            int n = panel.mobTools().cleanupTestSpawns(p, CLEANUP_RADIUS);
                            panel.audit(p, "removed " + n + " test mob(s)");
                            Ui.ok(p, "Removed " + n + " test mob(s).");
                            menus.refresh(p);
                        }));

        v.button(29, Ui.item(Material.SHIELD, ChatColor.YELLOW + "My combat stats",
                Ui.lore().text("Your defense pipeline, resistances,").text("combat skills and the mob threat table.")
                        .hint("Click to open").build()),
                c -> open(c.player(), new CombatStatsMenu(panel, c.player().getUniqueId())));

        v.button(31, Ui.glow(Ui.item(debug ? Material.REDSTONE_TORCH : Material.LEVER,
                ChatColor.LIGHT_PURPLE + "Combat debug traces: " + (debug ? ChatColor.GREEN + "ON" : ChatColor.RED + "OFF"),
                Ui.lore().text("When on, every hit you take prints its").text("pipeline in chat: scaling -> armor ->")
                        .text("defense bar -> final damage.").hint("Click to turn " + (debug ? "off" : "on")).build()), debug),
                c -> {
                    boolean on = plugin.toggleCombatDebug(c.player().getUniqueId());
                    Ui.ok(c.player(), "Combat debug traces " + (on ? "on" : "off") + ".");
                    menus.refresh(c.player());
                });

        v.button(33, Ui.item(Material.BOOK, ChatColor.WHITE + "Tier reference",
                Ui.lore().text("HP pool, damage multiplier, armor").text("penetration and XP for every tier,").text("at any level.")
                        .hint("Click to open").build()),
                c -> open(c.player(), new TierTableMenu(panel)));
    }
}

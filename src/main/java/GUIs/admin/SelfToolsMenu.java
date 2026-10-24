package GUIs.admin;

import GUIs.menu.Fmt;
import GUIs.menu.MenuView;
import GUIs.menu.Ui;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

/** One-click tools for the admin's own test character. */
final class SelfToolsMenu extends AdminMenu {

    SelfToolsMenu(AdminPanel panel) { super(panel); }

    @Override public String title() { return PREFIX + "My test character"; }

    @Override
    protected void build(MenuView v) {
        frame(v);
        v.refreshButton();
        Player me = v.viewer();
        PlayerVitals pv = PlayerVitals.of(plugin, me);
        boolean debug = plugin.isCombatDebugEnabled(me.getUniqueId());
        boolean creative = me.getGameMode() == GameMode.CREATIVE;

        v.set(MenuView.HEADER, Ui.head(me, ChatColor.AQUA + "" + ChatColor.BOLD + me.getName(),
                Ui.lore().stat("Health", Fmt.compact(pv.hp()) + " / " + Fmt.compact(pv.maxHp()))
                        .stat("Mana", Fmt.compact(pv.mana()) + " / " + Fmt.compact(pv.maxMana()))
                        .stat("Total skill level", String.valueOf(pv.totalLevel()))
                        .stat("Game mode", Fmt.title(me.getGameMode().name())).build()));

        v.button(19, Ui.item(Material.GOLDEN_APPLE, ChatColor.GREEN + "Full restore",
                Ui.lore().text("Health, defense bar, mana, hunger, fire.").hint("Click").build()),
                c -> { PlayerInspectMenu.fullRestore(c.player()); Ui.ok(c.player(), "Fully restored."); menus.refresh(c.player()); });

        v.button(20, Ui.glow(Ui.item(debug ? Material.REDSTONE_TORCH : Material.LEVER,
                ChatColor.LIGHT_PURPLE + "Combat debug traces: " + (debug ? ChatColor.GREEN + "ON" : ChatColor.RED + "OFF"),
                Ui.lore().text("Prints each hit's pipeline in chat.").hint("Click to switch").build()), debug),
                c -> { boolean on = plugin.toggleCombatDebug(c.player().getUniqueId());
                       Ui.ok(c.player(), "Combat debug traces " + (on ? "on" : "off") + "."); menus.refresh(c.player()); });

        v.button(21, Ui.item(Material.NETHERITE_SWORD, ChatColor.GOLD + "Test kit",
                Ui.lore().text("Cerberus Blade, Styx Cleaver, Hydra's Reach,").text("Fireball Wand, Cerberus armor.")
                        .text("Fills empty slots; full inventory overflows.").hint("Click (runs /givetestitem kit)").build()),
                c -> { c.player().performCommand("givetestitem kit"); panel.audit(c.player(), "took the test kit"); });

        v.button(22, Ui.item(Material.PLAYER_HEAD, ChatColor.LIGHT_PURPLE + "Player Menu head",
                Ui.lore().text("Puts it back in hotbar slot 9;").text("whatever is there is moved, not lost.").hint("Click").build()),
                c -> { plugin.getPlayerMenuGUI().placeMenuHead(c.player()); Ui.ok(c.player(), "Player Menu head is in slot 9."); });

        v.button(23, Ui.item(Material.ENCHANTED_BOOK, ChatColor.GOLD + "My skills",
                Ui.lore().text("Set levels / XP for testing.").hint("Click to open").build()),
                c -> open(c.player(), new SkillListMenu(panel, c.player().getUniqueId())));

        v.button(24, Ui.item(Material.IRON_CHESTPLATE, ChatColor.YELLOW + "My combat stats",
                Ui.lore().hint("Click to open").build()),
                c -> open(c.player(), new CombatStatsMenu(panel, c.player().getUniqueId())));

        v.button(25, Ui.glow(Ui.item(creative ? Material.GRASS_BLOCK : Material.IRON_PICKAXE,
                ChatColor.WHITE + "Game mode: " + Fmt.title(me.getGameMode().name()),
                Ui.lore().text("Switch between Survival and Creative.").text("(Creative ignores mob damage.)")
                        .hint("Click to switch to " + (creative ? "Survival" : "Creative")).build()), creative),
                c -> { GameMode to = c.player().getGameMode() == GameMode.CREATIVE ? GameMode.SURVIVAL : GameMode.CREATIVE;
                       c.player().setGameMode(to); panel.audit(c.player(), "game mode -> " + to);
                       Ui.ok(c.player(), "Game mode: " + Fmt.title(to.name()) + "."); menus.refresh(c.player()); });

        v.button(30, Ui.item(Material.COMPASS, ChatColor.DARK_AQUA + "Teleport to world spawn",
                Ui.lore().text("Level-1 mobs live here; mob level rises").text("1 per 150 blocks away from it.").hint("Click").build()),
                c -> {
                    Player p = c.player();
                    Location spawn = p.getWorld().getSpawnLocation().add(0.5, 0, 0.5);
                    menus.close(p);
                    p.teleportAsync(spawn).thenAccept(ok -> menus.onPlayerThread(p, () -> {
                        if (ok) Ui.ok(p, "Teleported to world spawn."); else Ui.fail(p, "Teleport was refused.");
                    }));
                });

        v.button(32, Ui.item(Material.LAVA_BUCKET, ChatColor.RED + "Clean up my test mobs",
                Ui.lore().text("Removes only mobs spawned from this panel,").text("within " + (int) CombatLabMenu.CLEANUP_RADIUS + " blocks.")
                        .hint("Click (asks to confirm)").build()),
                c -> confirm(c.player(), "Remove your test mobs?", List.of("Only Control Center test spawns nearby."), p -> {
                    int n = panel.mobTools().cleanupTestSpawns(p, CombatLabMenu.CLEANUP_RADIUS);
                    panel.audit(p, "removed " + n + " test mob(s)");
                    Ui.ok(p, "Removed " + n + " test mob(s).");
                    menus.refresh(p);
                }));
    }
}

package GUIs.admin;

import GUIs.menu.Fmt;
import GUIs.menu.MenuView;
import GUIs.menu.Status;
import GUIs.menu.Ui;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;

/** One player's character: vitals, skills, combat stats, and admin actions. */
final class PlayerInspectMenu extends AdminMenu {

    private final UUID targetId;

    PlayerInspectMenu(AdminPanel panel, UUID targetId) {
        super(panel);
        this.targetId = targetId;
    }

    @Override public String title() {
        Player t = Bukkit.getPlayer(targetId);
        return PREFIX + "Player: " + (t != null ? t.getName() : "offline");
    }

    @Override
    protected void build(MenuView v) {
        frame(v);
        v.refreshButton();
        Player t = Bukkit.getPlayer(targetId);
        if (t == null) {
            v.set(22, Ui.item(Material.GRAY_DYE, ChatColor.GRAY + "This player is offline",
                    Ui.lore().text("Their saved progress is untouched.").build()));
            return;
        }
        PlayerVitals pv = PlayerVitals.of(plugin, t);
        boolean self = t.getUniqueId().equals(v.viewer().getUniqueId());

        v.set(MenuView.HEADER, Ui.head(t, ChatColor.AQUA + "" + ChatColor.BOLD + t.getName() + (self ? ChatColor.GRAY + " (you)" : ""),
                Ui.lore().stat("Total skill level", String.valueOf(pv.totalLevel()))
                        .stat("Session", pv.session())
                        .stat("World", t.getWorld().getName())
                        .stat("Game mode", Fmt.title(t.getGameMode().name()))
                        .stat("UUID", t.getUniqueId().toString().substring(0, 8) + "...").build()));

        // Health
        v.button(19, Ui.item(Material.GLISTERING_MELON_SLICE, ChatColor.RED + "Health",
                Ui.lore().stat("Custom HP", Fmt.compact(pv.hp()) + " / " + Fmt.compact(pv.maxHp()) + "  (" + Fmt.pct(pv.hp() / Math.max(1, pv.maxHp())) + ")")
                        .raw(ChatColor.RED + Fmt.bar(pv.hp(), pv.maxHp(), 20))
                        .stat("Lifecycle", pv.lifecycle())
                        .hint("Click: full restore (health, defense bar, mana, hunger)").build()),
                c -> changePlayer(c.player(), targetId, target -> {
                    fullRestore(target);
                    panel.audit(c.player(), "full restore of " + target.getName());
                    notifyTarget(c.player(), target, "fully restored your health, defense and mana.");
                    menus.onPlayerThread(c.player(), () -> Ui.ok(c.player(), "Restored " + target.getName() + "."));
                }));

        // Mana
        v.button(20, Ui.item(Material.LAPIS_LAZULI, ChatColor.BLUE + "Mana",
                Ui.lore().stat("Mana", Fmt.compact(pv.mana()) + " / " + Fmt.compact(pv.maxMana()))
                        .raw(ChatColor.BLUE + Fmt.bar(pv.mana(), pv.maxMana(), 20))
                        .stat("Regeneration", Fmt.one(pv.manaRegen()) + " / s")
                        .hint("Click: refill mana").build()),
                c -> changePlayer(c.player(), targetId, target -> {
                    plugin.getPlayerManaManager().setPlayerCurrentMana(target, plugin.getPlayerManaManager().getMaxMana(target));
                    panel.audit(c.player(), "refilled mana of " + target.getName());
                    menus.onPlayerThread(c.player(), () -> Ui.ok(c.player(), "Refilled " + target.getName() + "'s mana."));
                }));

        // Defense
        v.set(21, Ui.item(Material.SHIELD, ChatColor.GRAY + "Defense",
                Ui.lore().stat("Defense bar", Fmt.compact(pv.defense()) + " / " + Fmt.compact(pv.maxDefense()))
                        .raw(ChatColor.GRAY + Fmt.bar(pv.defense(), pv.maxDefense(), 20))
                        .stat("Armor reduction", Fmt.pct(pv.armorReduction()))
                        .stat("Fire / cold / drowning resist", Fmt.pct(pv.fireRes()) + " / " + Fmt.pct(pv.coldRes()) + " / " + Fmt.pct(pv.drownRes()))
                        .build()));

        // Skills
        Ui.Lore skills = Ui.lore().stat("Total level", String.valueOf(pv.totalLevel())).text("Top skills:");
        for (Map.Entry<String, Integer> e : pv.topSkills().entrySet()) skills.raw(ChatColor.WHITE + "  " + e.getKey() + ChatColor.GRAY + "  Lv " + e.getValue());
        v.button(23, Ui.item(Material.ENCHANTED_BOOK, ChatColor.GOLD + "Skills", skills.hint("Click to view and edit all skills").build()),
                c -> open(c.player(), new SkillListMenu(panel, targetId)));

        // Attributes (next milestone)
        v.button(24, Ui.item(Material.NETHER_STAR, ChatColor.GRAY + "Attributes",
                Ui.lore().text("Vitality, Essence, Power, Fortitude,").text("Agility, Fortune.")
                        .gap().status(Status.IN_DEVELOPMENT).hint("Click to see the plan").build()),
                c -> open(c.player(), new AttributesDevMenu(panel, targetId)));

        // Combat stats
        v.button(25, Ui.item(Material.IRON_CHESTPLATE, ChatColor.YELLOW + "Combat stats",
                Ui.lore().text("Defense pipeline numbers, resistances,").text("combat skill levels, mob threat table.")
                        .hint("Click to open").build()),
                c -> open(c.player(), new CombatStatsMenu(panel, targetId)));

        // Teleport
        if (!self) {
            v.button(30, Ui.item(Material.ENDER_PEARL, ChatColor.DARK_AQUA + "Teleport to " + t.getName(),
                    Ui.lore().text("Moves you, not them.").hint("Click to teleport").build()),
                    c -> {
                        Player admin = c.player();
                        Player target = Bukkit.getPlayer(targetId);
                        if (target == null) { Ui.fail(admin, "That player is no longer online."); return; }
                        menus.close(admin);
                        target.getScheduler().run(plugin, tt -> {
                            var loc = target.getLocation();
                            admin.getScheduler().run(plugin, at -> admin.teleportAsync(loc).thenAccept(ok ->
                                    menus.onPlayerThread(admin, () -> {
                                        if (ok) {
                                            panel.audit(admin, "teleported to " + target.getName());
                                            Ui.ok(admin, "Teleported to " + target.getName() + ".");
                                        } else Ui.fail(admin, "Teleport was refused.");
                                    })), null);
                        }, null);
                    });
        }

        // Session log
        v.button(32, Ui.item(Material.WRITABLE_BOOK, ChatColor.WHITE + "Dump combat/session log",
                Ui.lore().text("Writes this player's live log").text("(hits, damage, heals) to a file.")
                        .hint("Click to dump").build()),
                c -> {
                    Player admin = c.player();
                    var logger = plugin.getSessionLogger();
                    if (logger == null) { Ui.info(admin, "Session logging is off."); return; }
                    // File I/O off the region thread; the log buffer is synchronized.
                    Bukkit.getAsyncScheduler().runNow(plugin, task -> {
                        String path = logger.dumpLive(targetId);
                        menus.onPlayerThread(admin, () -> {
                            if (path == null) Ui.info(admin, "No log entries for this player yet.");
                            else Ui.ok(admin, "Log written: " + path);
                        });
                    });
                });
    }

    /** Full HP + defense bar (existing admin heal), mana, hunger, fire. Runs on the target's thread. */
    static void fullRestore(Player target) {
        var plugin = cerberus.world.cerb.CerberusPlugin.getInstance();
        plugin.getPlayerVirtualHealthManager().resetForAdminHeal(target);
        plugin.getPlayerManaManager().setPlayerCurrentMana(target, plugin.getPlayerManaManager().getMaxMana(target));
        target.setFoodLevel(20);
        target.setSaturation(20f);
        target.setFireTicks(0);
    }
}

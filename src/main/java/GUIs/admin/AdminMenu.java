package GUIs.admin;

import GUIs.menu.ConfirmMenu;
import GUIs.menu.Menu;
import GUIs.menu.MenuView;
import GUIs.menu.Ui;
import cerberus.world.cerb.CerberusPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/** Base for Control Center screens: admin permission, standard frame, safe cross-thread helpers. */
abstract class AdminMenu extends Menu {

    static final Material HEADER_GLASS = Material.PURPLE_STAINED_GLASS_PANE;
    static final String PREFIX = ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "Cerberus " + ChatColor.DARK_GRAY + "» " + ChatColor.DARK_GRAY;

    protected final AdminPanel panel;
    protected final CerberusPlugin plugin;

    AdminMenu(AdminPanel panel) {
        super(panel.menus());
        this.panel = panel;
        this.plugin = panel.plugin();
    }

    @Override public String permission() { return AdminPanel.PERMISSION; }

    /** Header row, navigation row, Back / Home / Close. */
    protected void frame(MenuView v) {
        v.frame(HEADER_GLASS, true, true);
    }

    protected void open(Player viewer, Menu next) {
        menus.open(viewer, next);
    }

    protected void confirm(Player viewer, String question, List<String> details, Consumer<Player> action) {
        menus.open(viewer, new ConfirmMenu(menus, AdminPanel.PERMISSION, question, details, action));
    }

    /**
     * Change another player safely (Folia): run {@code change} on the target's region thread,
     * then refresh the admin's screen on the admin's thread. Reports if the target left.
     */
    protected void changePlayer(Player admin, UUID targetId, Consumer<Player> change) {
        Player target = Bukkit.getPlayer(targetId);
        if (target == null) {
            Ui.fail(admin, "That player is no longer online.");
            menus.refresh(admin);
            return;
        }
        target.getScheduler().run(plugin, t -> {
            try {
                change.accept(target);
            } catch (Throwable e) {
                plugin.getLogger().warning("[Admin] change on " + target.getName() + " failed: " + e);
                menus.onPlayerThread(admin, () -> Ui.fail(admin, "Change failed: " + e.getMessage()));
            }
            menus.refresh(admin);
        }, () -> menus.onPlayerThread(admin, () -> Ui.fail(admin, "That player left before the change applied.")));
    }

    /** Change a mob on its own region thread, then refresh the admin's screen. */
    protected void changeMob(Player admin, LivingEntity mob, Consumer<LivingEntity> change) {
        if (!mob.isValid()) {
            Ui.fail(admin, "That mob is gone (died, despawned or was removed).");
            menus.refresh(admin);
            return;
        }
        mob.getScheduler().run(plugin, t -> {
            change.accept(mob);
            menus.refresh(admin);
        }, () -> menus.onPlayerThread(admin, () -> Ui.fail(admin, "That mob was removed before the change applied.")));
    }

    /** Tell a player an admin changed something about them (skipped when changing yourself). */
    protected static void notifyTarget(Player admin, Player target, String what) {
        if (!admin.getUniqueId().equals(target.getUniqueId())) {
            target.sendMessage(ChatColor.LIGHT_PURPLE + "[Admin] " + ChatColor.GRAY + admin.getName() + " " + what);
        }
    }
}

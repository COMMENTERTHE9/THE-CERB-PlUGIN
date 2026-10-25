package GUIs.player;

import GUIs.menu.Menu;
import GUIs.menu.MenuView;
import GUIs.menu.Ui;
import cerberus.world.cerb.CerberusPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * Base for every Player Menu screen. Runs on the shared menu framework (server-side slot
 * actions, every click cancelled, navigation history) but with the player look and one
 * navigation contract: Back returns to the previous screen (or the hub when there is none),
 * Home always opens the hub, Close always closes. Players never end up stuck.
 *
 * <p>No permission: these screens show the viewer's own data, read-only, and every action a
 * button takes is re-validated by the system it calls.
 */
public abstract class PlayerMenu extends Menu {

    protected final CerberusPlugin plugin;

    protected PlayerMenu(CerberusPlugin plugin) {
        super(plugin.getMenuManager());
        this.plugin = plugin;
    }

    @Override
    protected final void build(MenuView v) {
        v.row(0, headerGlass());
        nav(v);
        content(v);
        v.fill(PlayerTheme.FRAME);
        memory().shown(v.viewer(), plugin.getSkillManager()); // what "since you last looked" compares with next time
    }

    /** The screen's own items. Frame and navigation are already in place; empty slots get filled after. */
    protected abstract void content(MenuView v);

    /** Colour of the header row (skill categories use their category colour). */
    protected Material headerGlass() { return PlayerTheme.HEADER; }

    /** True for the hub itself (no Back / Home there). */
    protected boolean isHub() { return false; }

    // Player Menu 2.1: content appears centre-out on opening, a light sweeps the header on moving
    // between screens, page-turn sounds, and the menu remembers what the player saw when closing it.
    @Override public Material animationBackground() { return PlayerTheme.FRAME; }

    @Override public boolean navigationSounds() { return true; }

    @Override public void closed(Player player) {
        memory().remember(player, plugin.getSkillManager());
    }

    /** What this player last saw in the menu (this session). */
    protected PlayerMenuMemory memory() { return plugin.getPlayerMenuGUI().memory(); }

    private void nav(MenuView v) {
        if (!isHub()) {
            v.button(MenuView.BACK, Ui.item(Material.ARROW, ChatColor.YELLOW + "Back",
                    Ui.lore().text("Previous screen").build()), c -> back(c.player()));
            v.button(MenuView.HOME, Ui.item(Material.COMPASS, ChatColor.GOLD + "Player Menu",
                    Ui.lore().text("Back to the main menu").build()), c -> home(c.player()));
        }
        v.button(MenuView.CLOSE, Ui.item(Material.BARRIER, ChatColor.RED + "Close",
                Ui.lore().text("Close the menu").build()), c -> menus.close(c.player()));
    }

    protected void back(Player p) {
        if (menus.depth(p) > 1) menus.back(p);
        else home(p);
    }

    protected void home(Player p) {
        menus.openRoot(p, new PlayerHubMenu(plugin));
    }

    /** True once the player's saved progress has loaded (right after joining it may not have). */
    protected boolean loaded(Player p) {
        return plugin.getSkillManager().hasLoadedSkills(p.getUniqueId());
    }

    /** The placeholder shown while a player's data is still loading. */
    protected static void loading(MenuView v) {
        v.set(22, Ui.item(Material.CLOCK, ChatColor.YELLOW + "Loading your character...",
                Ui.lore().text("Your saved progress is still loading.").hint("Reopen in a moment").build()));
    }
}

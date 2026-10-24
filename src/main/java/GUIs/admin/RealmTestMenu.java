package GUIs.admin;

import GUIs.menu.Fmt;
import GUIs.menu.MenuClick;
import GUIs.menu.MenuView;
import GUIs.menu.Ui;
import Manager.Realm;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Put a player in any realm temporarily to test cross-realm math (×8 per realm) before Ascension
 * exists. Never saved; ends on logout or "Back to default". Audited; the player is told.
 */
final class RealmTestMenu extends AdminMenu {

    private static final int[] SLOTS = {19, 20, 21, 22, 23, 24, 25, 29, 30, 31};

    private final UUID targetId;

    RealmTestMenu(AdminPanel panel, UUID targetId) {
        super(panel);
        this.targetId = targetId;
    }

    @Override public String title() { return PREFIX + "Test realm"; }

    @Override
    protected void build(MenuView v) {
        frame(v);
        Player t = Bukkit.getPlayer(targetId);
        if (t == null) {
            v.set(22, Ui.item(Material.GRAY_DYE, ChatColor.GRAY + "This player is offline"));
            return;
        }
        var realms = plugin.getPlayerRealms();
        Realm current = realms.realmOf(targetId);
        boolean test = realms.hasTestRealm(targetId);
        var scaling = plugin.getCombatConfig().mobHealthScaling;
        boolean on = plugin.getAttributeService().rules().switches().realmMagnitude();

        v.set(MenuView.HEADER, Ui.item(Material.END_CRYSTAL, ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "Test realm: " + t.getName(),
                Ui.lore().stat("Now", current.displayName + (test ? " (test)" : " (default)"))
                        .stat("Default for everyone", realms.defaultRealm().displayName)
                        .gap().text("Multiplies their damage, max health,").text("defense bar, regen and spell damage")
                        .text("by the realm curve (mobs use the same).")
                        .text("Temporary: never saved, ends on logout.")
                        .raw(on ? ChatColor.GREEN + "Realm magnitude: on" : ChatColor.RED + "Realm magnitude is off in attributes.yml").build()));

        Realm[] all = Realm.values();
        for (int i = 0; i < all.length; i++) {
            Realm r = all[i];
            boolean here = r == current;
            v.button(SLOTS[i], Ui.glow(Ui.amount(Ui.item(here ? Material.NETHER_STAR : Material.AMETHYST_SHARD,
                    r.color() + "R" + r.number + " " + r.displayName + ChatColor.DARK_GRAY + "  " + r.epithet,
                    Ui.lore().stat("Magnitude", Fmt.mult(scaling.realmMultiplier(r.tier())))
                            .stat("Mobs of this realm", r.tier().name() + " tier")
                            .hint(here ? "Current realm" : "Click to set").build()), r.number), here),
                    c -> set(c, r));
        }
        v.button(40, Ui.item(Material.MILK_BUCKET, ChatColor.YELLOW + "Back to default",
                Ui.lore().text("Returns them to " + realms.defaultRealm().displayName + ".").hint("Click").build()), this::clear);
    }

    private void set(MenuClick c, Realm r) {
        Player admin = c.player();
        Player t = Bukkit.getPlayer(targetId);
        if (t == null) { Ui.fail(admin, "That player is no longer online."); return; }
        plugin.getPlayerRealms().setTestRealm(targetId, r);
        apply();
        panel.audit(admin, "set " + t.getName() + "'s test realm to " + r.displayName);
        notifyTarget(admin, t, "put you in the " + r.displayName + " realm for testing (until you log out).");
        Ui.ok(admin, t.getName() + " is now " + r.displayName + " (test).");
        menus.refresh(admin);
    }

    private void clear(MenuClick c) {
        Player admin = c.player();
        Player t = Bukkit.getPlayer(targetId);
        boolean had = plugin.getPlayerRealms().clearTestRealm(targetId);
        apply();
        if (had && t != null) {
            panel.audit(admin, "cleared " + t.getName() + "'s test realm");
            notifyTarget(admin, t, "returned you to your normal realm.");
        }
        Ui.ok(admin, (t != null ? t.getName() : "Player") + " is back to " + plugin.getPlayerRealms().defaultRealm().displayName + ".");
        menus.refresh(admin);
    }

    /** New realm: rebuild the snapshot and re-apply health, defense and mana on their thread. */
    private void apply() {
        plugin.getAttributeService().markDirty(targetId);
        plugin.getAttributeEffects().refreshLater(targetId);
    }
}

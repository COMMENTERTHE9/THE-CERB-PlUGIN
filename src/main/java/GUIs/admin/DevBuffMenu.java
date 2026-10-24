package GUIs.admin;

import Attributes.Attribute;
import Attributes.AttributeService;
import Attributes.BuffSource;
import GUIs.menu.Fmt;
import GUIs.menu.MenuClick;
import GUIs.menu.MenuView;
import GUIs.menu.Ui;
import GUIs.stats.AttributeView;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/**
 * Temporary attribute buffs for controlled tests ("+100 Power for 5 minutes"). They go through
 * the normal attribute engine, caps included, are never saved, and vanish on logout.
 */
final class DevBuffMenu extends AdminMenu {

    private static final long DURATION_MS = 5 * 60_000L;
    private static final double[] AMOUNTS = {25, 100};
    private static final Attribute[] ORDER = {Attribute.POWER, Attribute.AGILITY, Attribute.VITALITY,
            Attribute.FORTITUDE, Attribute.ESSENCE, Attribute.FORTUNE};

    private final UUID targetId;

    DevBuffMenu(AdminPanel panel, UUID targetId) {
        super(panel);
        this.targetId = targetId;
    }

    @Override public String title() { return PREFIX + "Dev buffs"; }

    @Override
    protected void build(MenuView v) {
        frame(v);
        v.refreshButton();
        Player t = Bukkit.getPlayer(targetId);
        if (t == null) {
            v.set(22, Ui.item(Material.GRAY_DYE, ChatColor.GRAY + "This player is offline"));
            return;
        }
        BuffSource buffs = plugin.getAttributeBuffs();
        List<BuffSource.Buff> active = buffs.active(targetId);

        v.set(MenuView.HEADER, Ui.item(Material.POTION, ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "Dev buffs: " + t.getName(),
                Ui.lore().text("Adds attribute points for " + Fmt.duration(DURATION_MS) + ".")
                        .text("Caps still apply. Never saved; gone")
                        .text("on logout. The player is told.").build()));

        for (int i = 0; i < ORDER.length; i++) {
            Attribute a = ORDER[i];
            for (int k = 0; k < AMOUNTS.length; k++) {
                double amount = AMOUNTS[k];
                int slot = (k == 0 ? 10 : 19) + i + (i >= 3 ? 1 : 0);
                v.button(slot, Ui.amount(Ui.item(AttributeView.icon(a), a.color + "+" + (int) amount + " " + a.displayName,
                        Ui.lore().text(a.drives).text("for " + Fmt.duration(DURATION_MS)).hint("Click to apply").build()),
                        (int) amount == 100 ? 1 : (int) amount), c -> apply(c, a, amount));
            }
        }

        Ui.Lore list = Ui.lore();
        if (active.isEmpty()) list.text("No active buffs.");
        long now = buffs.now();
        for (BuffSource.Buff b : active) {
            list.raw(ChatColor.WHITE + "  " + b.label() + ChatColor.GRAY + "  " + Fmt.duration(Math.max(0, b.expiresAt() - now)) + " left");
        }
        v.set(31, Ui.item(Material.CLOCK, ChatColor.YELLOW + "Active buffs (" + active.size() + ")", list.build()));
        v.button(40, Ui.item(Material.MILK_BUCKET, ChatColor.RED + "Clear all buffs",
                Ui.lore().text("Removes every dev buff from " + t.getName() + ".").hint("Click to clear").build()), this::clear);
    }

    private void apply(MenuClick c, Attribute a, double amount) {
        Player admin = c.player();
        Player t = Bukkit.getPlayer(targetId);
        if (t == null) { Ui.fail(admin, "That player is no longer online."); return; }
        BuffSource buffs = plugin.getAttributeBuffs();
        AttributeService svc = plugin.getAttributeService();
        String label = "Dev buff +" + (int) amount + " " + a.displayName;
        buffs.add(targetId, new BuffSource.Buff(UUID.randomUUID().toString().substring(0, 8), label, a, amount, buffs.now() + DURATION_MS));
        svc.markDirty(targetId);
        plugin.getAttributeEffects().refreshLater(targetId);
        // At expiry: drop it from the cached snapshot and re-apply health/defense/mana (target's thread).
        t.getScheduler().runDelayed(plugin, task -> {
            svc.markDirty(targetId);
            plugin.getAttributeEffects().refresh(t);
        }, null, DURATION_MS / 50 + 1);
        panel.audit(admin, "gave " + t.getName() + " a dev buff: +" + (int) amount + " " + a.displayName + " for 5m");
        notifyTarget(admin, t, "gave you a test buff: +" + (int) amount + " " + a.displayName + " for 5 minutes.");
        Ui.ok(admin, t.getName() + ": +" + (int) amount + " " + a.displayName + " for 5 minutes.");
        menus.refresh(admin);
    }

    private void clear(MenuClick c) {
        Player admin = c.player();
        int n = plugin.getAttributeBuffs().clear(targetId);
        plugin.getAttributeService().markDirty(targetId);
        plugin.getAttributeEffects().refreshLater(targetId);
        Player t = Bukkit.getPlayer(targetId);
        String name = t != null ? t.getName() : "player";
        if (n > 0) {
            panel.audit(admin, "cleared " + n + " dev buff(s) from " + name);
            if (t != null) notifyTarget(admin, t, "removed your test buffs.");
        }
        Ui.ok(admin, "Cleared " + n + " buff(s) from " + name + ".");
        menus.refresh(admin);
    }
}

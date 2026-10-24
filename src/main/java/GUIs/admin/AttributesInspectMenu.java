package GUIs.admin;

import Attributes.Attribute;
import Attributes.AttributeRules;
import Attributes.AttributeService;
import Attributes.AttributeSnapshot;
import Attributes.BuffSource;
import Attributes.WeaponClass;
import GUIs.menu.Fmt;
import GUIs.menu.MenuView;
import GUIs.menu.Status;
import GUIs.menu.Ui;
import GUIs.stats.AttributeView;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * One player's attributes as the engine computes them (the same snapshot combat and their
 * Stats page use), with the rules in force, plus the simulator and dev buffs.
 */
final class AttributesInspectMenu extends AdminMenu {

    private static final Attribute[] ORDER = {Attribute.POWER, Attribute.AGILITY, Attribute.VITALITY,
            null, Attribute.FORTITUDE, Attribute.ESSENCE, Attribute.FORTUNE};

    private final UUID targetId;

    AttributesInspectMenu(AdminPanel panel, UUID targetId) {
        super(panel);
        this.targetId = targetId;
    }

    @Override public String title() {
        Player t = Bukkit.getPlayer(targetId);
        return PREFIX + "Attributes: " + (t != null ? t.getName() : "offline");
    }

    @Override
    protected void build(MenuView v) {
        frame(v);
        v.refreshButton();
        Player t = Bukkit.getPlayer(targetId);
        if (t == null) {
            v.set(22, Ui.item(Material.GRAY_DYE, ChatColor.GRAY + "This player is offline",
                    Ui.lore().text("Attributes are computed while online.").build()));
            return;
        }
        AttributeService svc = plugin.getAttributeService();
        AttributeSnapshot s = svc.get(targetId);
        AttributeRules rules = s.rules();
        BuffSource buffs = plugin.getAttributeBuffs();
        int activeBuffs = buffs.active(targetId).size();

        v.set(MenuView.HEADER, Ui.head(t, ChatColor.AQUA + "" + ChatColor.BOLD + t.getName(), Ui.lore()
                .stat("Loaded", svc.isTracked(targetId) ? "yes" : "no (neutral values)")
                .stat("Realm", "Awakened (realms not built yet)")
                .stat("Active dev buffs", String.valueOf(activeBuffs))
                .gap().text("Same numbers as their Stats page").text("and the combat code.").build()));

        for (int i = 0; i < ORDER.length; i++) {
            Attribute a = ORDER[i];
            if (a != null) v.set(10 + i, AttributeView.tile(a, s, svc.effect(a)));
        }

        Ui.Lore out = Ui.lore().text("Damage by weapon (Power baseline / average):");
        for (WeaponClass w : WeaponClass.values()) {
            if (w == WeaponClass.OTHER) continue;
            out.raw(ChatColor.WHITE + "  " + w.displayName + ": " + Fmt.mult(s.powerMultiplier(w))
                    + ChatColor.GRAY + " / " + Fmt.mult(s.expectedOffenseMultiplier(w)));
        }
        out.stat("Crit", Fmt.pct(s.critChance()) + " for " + Fmt.mult(s.critMultiplier()))
                .stat("Lifesteal", Fmt.pct(s.lifesteal()))
                .stat("Max health / mana", Fmt.mult(s.maxHpMultiplier()) + " / " + Fmt.mult(s.maxManaMultiplier()))
                .stat("Spell damage vs mobs", Fmt.mult(s.spellDamageMultiplier()));
        v.set(13, Ui.item(Material.BLAZE_POWDER, ChatColor.GOLD + "Combat outcomes", out.build()));

        v.button(29, Ui.item(Material.COMPARATOR, ChatColor.GREEN + "Simulator",
                Ui.lore().text("Pick a skill and a level, see the").text("before/after attributes and derived")
                        .text("stats. Changes nothing.").hint("Click to open").build()),
                c -> open(c.player(), new AttributeSimulatorMenu(panel, targetId)));
        v.button(31, Ui.item(Material.POTION, ChatColor.LIGHT_PURPLE + "Dev buffs",
                Ui.lore().stat("Active", String.valueOf(activeBuffs))
                        .text("Temporary +attribute bonuses for").text("controlled tests. Never saved.")
                        .hint("Click to open").build()),
                c -> open(c.player(), new DevBuffMenu(panel, targetId)));
        v.button(33, Ui.item(Material.REDSTONE, ChatColor.YELLOW + "Re-apply to character",
                Ui.lore().text("Pushes max health, defense and mana").text("from these attributes again (also")
                        .text("happens on level-up and /cerb reload).").hint("Click to re-apply").build()),
                c -> {
                    plugin.getAttributeEffects().refreshLater(targetId);
                    Ui.ok(c.player(), "Re-applied attributes to " + t.getName() + ".");
                    menus.refresh(c.player());
                });

        AttributeRules.Limits lim = rules.limits();
        AttributeRules.Switches sw = rules.switches();
        AttributeRules.Derived d = rules.derived();
        v.set(40, Ui.item(Material.WRITABLE_BOOK, ChatColor.WHITE + "Rules in force (attributes.yml)", Ui.lore()
                .text("Integrations:")
                .raw(onOff("  PvE offense (Power, crit, lifesteal)", sw.pveOffense()))
                .raw(onOff("  Vitality -> max health", sw.vitality()))
                .raw(onOff("  Fortitude -> armor, bar, resist", sw.fortitude()))
                .raw(onOff("  Essence -> mana, spell damage", sw.essence()))
                .gap().text("Per point:")
                .stat("  Power damage", "+" + Fmt.pct(d.powerDamage()))
                .stat("  Vitality health", "+" + Fmt.pct(d.vitalityHp()))
                .stat("  Essence spell damage", "+" + Fmt.pct(d.essenceSpell()))
                .gap().text("Limits:")
                .stat("  Offense budget", Fmt.mult(lim.offenseBudget()) + " over the Power baseline")
                .stat("  Crit", Fmt.pct(lim.critChance()) + " / " + Fmt.mult(lim.critMultiplier()))
                .stat("  Lifesteal / armor / resist", Fmt.pct(lim.lifesteal()) + " / " + Fmt.pct(lim.armor()) + " / " + Fmt.pct(lim.resist()))
                .gap().stat("Players tracked", String.valueOf(svc.trackedCount()))
                .stat("Snapshots cached", String.valueOf(svc.cachedCount()))
                .stat("Rebuilds since start", Fmt.grouped(svc.rebuildCount()))
                .gap().status(Status.LIVE).build()));
    }

    private static String onOff(String label, boolean on) {
        return ChatColor.GRAY + label + ": " + (on ? ChatColor.GREEN + "on" : ChatColor.RED + "off");
    }
}

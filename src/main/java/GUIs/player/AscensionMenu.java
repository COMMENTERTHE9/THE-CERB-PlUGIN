package GUIs.player;

import Attributes.AttributeSnapshot;
import GUIs.menu.MenuView;
import GUIs.menu.Ui;
import Manager.MobHealthScaling;
import Manager.Numbers;
import Manager.Realm;
import cerberus.world.cerb.CerberusPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Player Menu → Ascension: the ten realms, where the player stands, and what a realm does today.
 * Breakthroughs are not built, so the screen says so instead of inventing a path.
 */
public final class AscensionMenu extends PlayerMenu {

    private static final int[] SLOTS = {11, 12, 13, 14, 15, 20, 21, 22, 23, 24};
    /** One block colour per realm, in ladder order (the name and number carry the meaning). */
    private static final Material[] ICONS = {Material.GRAY_CONCRETE, Material.WHITE_CONCRETE, Material.LIME_CONCRETE,
            Material.BLUE_CONCRETE, Material.PURPLE_CONCRETE, Material.ORANGE_CONCRETE, Material.YELLOW_CONCRETE,
            Material.RED_CONCRETE, Material.RED_TERRACOTTA, Material.MAGENTA_CONCRETE};

    public AscensionMenu(CerberusPlugin plugin) {
        super(plugin);
    }

    @Override public String title() { return PlayerTheme.title("Ascension"); }

    /** Big magnitudes compact (x2.1M); below 1 exact enough to read as a fraction (x0.125, not x0.1). */
    static String magnitude(double m) {
        if (m >= 1) return Numbers.compact(m);
        return String.format(java.util.Locale.ROOT, "%.3f", m).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    @Override
    protected void content(MenuView v) {
        Player p = v.viewer();
        UUID id = p.getUniqueId();
        AttributeSnapshot s = plugin.getAttributeService().get(id);
        Realm current = s.realm().realm();
        Realm start = plugin.getPlayerRealms().defaultRealm();
        boolean test = plugin.getPlayerRealms().hasTestRealm(id);
        MobHealthScaling scaling = plugin.getCombatConfig().mobHealthScaling;

        v.set(MenuView.HEADER, Ui.glow(Ui.item(Material.NETHER_STAR, PlayerTheme.REALM + "" + ChatColor.BOLD + "Ascension", Ui.lore()
                .stat("Your realm", current.color() + "R" + current.number + " " + current.displayName
                        + ChatColor.DARK_GRAY + "  (" + current.epithet + ")")
                .stat("Realm magnitude", "x" + magnitude(s.realmMultiplier()) + (test ? "  (test realm from an admin)" : ""))
                .gap().raw(Availability.State.IN_DEVELOPMENT.label + ChatColor.GRAY + ": moving up a realm")
                .text("is not built yet. Everyone starts")
                .text("and stays in " + start.displayName + " for now.").build()), true));

        Realm[] realms = Realm.values();
        for (int i = 0; i < realms.length && i < SLOTS.length; i++) {
            Realm r = realms[i];
            boolean here = r == current;
            Ui.Lore l = Ui.lore().raw(ChatColor.DARK_GRAY + r.epithet)
                    .gap().stat("Magnitude", "x" + magnitude(scaling.realmMultiplier(r.tier())) + ChatColor.DARK_GRAY + "  (Awakened = x1)")
                    .stat("Its creatures", r.tier().displayColor + r.tier().displayName + ChatColor.GRAY + " tier");
            if (i > 0) l.text("x" + magnitude(scaling.realmMultiplier(r.tier()) / scaling.realmMultiplier(realms[i - 1].tier()))
                    + " the realm before it.");
            l.gap();
            if (here) l.raw(PlayerTheme.REALM + "✦ You are here");
            else if (r.number < current.number) l.raw(ChatColor.GRAY + "Below your realm");
            else l.raw(ChatColor.GRAY + "Above your realm");
            if (r == start) l.raw(ChatColor.DARK_GRAY + "Everyone's realm for now");
            String name = r.color() + "R" + r.number + " " + r.displayName + (here ? ChatColor.LIGHT_PURPLE + "  ✦" : "");
            v.set(SLOTS[i], Ui.glow(Ui.item(here ? Material.NETHER_STAR : ICONS[i], name, l.build()), here));
        }

        v.set(31, Ui.item(Material.KNOWLEDGE_BOOK, ChatColor.GOLD + "What a realm does", Ui.lore()
                .text("Your realm multiplies your damage,")
                .text("health, defense bar and regeneration.")
                .gap().text("Creatures of a realm have health on")
                .text("the same scale, so a fight between")
                .text("equal realms takes about as many hits")
                .text("as a fight at the start of the game.")
                .gap().text("Inside a realm, skill levels make you")
                .text("stronger (see Skills and Stats).").build()));
        v.set(40, Ui.item(Material.GRAY_DYE, ChatColor.GRAY + "Breakthroughs", Ui.lore()
                .raw(Availability.State.IN_DEVELOPMENT.label)
                .text("How you rise from one realm to the")
                .text("next is not built yet.").build()));
    }
}

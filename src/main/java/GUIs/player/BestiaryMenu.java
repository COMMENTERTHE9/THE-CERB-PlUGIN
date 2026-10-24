package GUIs.player;

import GUIs.menu.Fmt;
import GUIs.menu.MenuView;
import GUIs.menu.Ui;
import Manager.MobHealthScaling;
import Manager.MobRegistry;
import Manager.Numbers;
import Manager.Realm;
import Manager.Tier;
import cerberus.world.cerb.CerberusPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Player Menu → Bestiary: the creatures the server knows by name (mobs.yml), with their tier,
 * realm, XP and health. Kill tracking is not built, and the header says so.
 */
public final class BestiaryMenu extends PlayerMenu {

    private static final Pattern NAME_PREFIX = Pattern.compile("^\\^?([A-Za-z][A-Za-z ']*?) ?\\.\\*\\$?$");

    private int page;

    public BestiaryMenu(CerberusPlugin plugin) {
        super(plugin);
    }

    @Override public String title() { return PlayerTheme.title("Bestiary"); }

    @Override
    protected void content(MenuView v) {
        MobRegistry registry = plugin.getMobRegistry();
        MobHealthScaling scaling = plugin.getCombatConfig().mobHealthScaling;
        List<MobRegistry.MobEntry> entries = new ArrayList<>(registry.getEntries());
        entries.sort(Comparator.comparing((MobRegistry.MobEntry e) -> e.getTier()).reversed().thenComparing(MobRegistry.MobEntry::getMobId));

        v.set(MenuView.HEADER, Ui.item(Material.ZOMBIE_HEAD, ChatColor.GOLD + "" + ChatColor.BOLD + "Bestiary", Ui.lore()
                .stat("Named creatures", String.valueOf(entries.size()))
                .text("Every other creature is " + Tier.COMMON.displayName + " (" + Realm.of(Tier.COMMON).displayName + ").")
                .text("Level rises with distance from spawn.")
                .gap().raw(Availability.State.IN_DEVELOPMENT.label + ChatColor.GRAY + ": kill counts and")
                .text("discoveries are not tracked yet.").build()));

        page = v.page(entries, page, e -> tile(e, scaling), null, np -> page = np);
        v.refreshButton();
    }

    private static ItemStack tile(MobRegistry.MobEntry e, MobHealthScaling scaling) {
        Tier tier = e.getTier();
        Ui.Lore l = Ui.lore()
                .stat("Tier", tier.displayColor + tier.displayName + ChatColor.GRAY + "  (" + Realm.of(tier).displayName + " realm)")
                .stat("Kill XP", Fmt.mult(e.getXpMultiplier()) + ChatColor.DARK_GRAY + " (common creature x1)")
                .stat("Health at level 1", Numbers.hud(scaling.calculate(tier, 1)))
                .stat("Health at level 50", Numbers.hud(scaling.calculate(tier, 50)));
        String what = describe(e);
        if (what != null) l.gap().text(what);
        Material icon = Material.NAME_TAG;
        if (e.getEntityType() != null) {
            Material egg = Material.matchMaterial(e.getEntityType().name() + "_SPAWN_EGG");
            if (egg != null) icon = egg;
            else if (e.getEntityType().name().equals("ENDER_DRAGON")) icon = Material.DRAGON_HEAD;
            else if (e.getEntityType().name().equals("WITHER")) icon = Material.WITHER_SKELETON_SKULL;
        }
        return Ui.item(icon, tier.displayColor + Fmt.title(e.getMobId()), l.build());
    }

    /** How to recognise it, in words: "Any Wither", "Named 'Sea Monster'", "A Zombie named 'Elite ...'". */
    static String describe(MobRegistry.MobEntry e) {
        String type = e.getEntityType() == null ? null : Fmt.title(e.getEntityType().name());
        String name = null;
        if (e.getCustomNameExact() != null) name = "named '" + e.getCustomNameExact() + "'";
        else if (e.getCustomNamePattern() != null) {
            Matcher m = NAME_PREFIX.matcher(e.getCustomNamePattern());
            name = m.matches() ? "named '" + m.group(1).trim() + " ...'" : "with a special name";
        }
        if (type != null && name != null) return (e.isMatchAll() ? "A " + type + " " : "A " + type + ", or anything ") + name + ".";
        if (type != null) return "Any " + type + ".";
        if (name != null) return "Any creature " + name + ".";
        return null;
    }
}

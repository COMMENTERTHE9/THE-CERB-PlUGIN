package GUIs.player;

import Classes.ClassDef;
import Classes.ClassProfile;
import Classes.ClassService;
import Classes.Controls;
import Classes.Loadout;
import GUIs.menu.MenuView;
import GUIs.menu.Ui;
import cerberus.world.cerb.CerberusPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Player Menu → Classes & Abilities (docs/design/CLASSES.md §7): every class (learned with rank and
 * XP, or what it still needs), the three loadouts, and how casting works. Read-only: changes are
 * made on the loadout screens, through {@link ClassService}, which validates them.
 */
public final class ClassesMenu extends PlayerMenu {

    private static final int[] CLASS_SLOTS = {20, 22, 24, 21, 23, 19, 25};
    private static final int[] LOADOUT_SLOTS = {38, 40, 42};

    public ClassesMenu(CerberusPlugin plugin) {
        super(plugin);
    }

    @Override public String title() { return PlayerTheme.title("Classes & Abilities"); }

    /** The Controls tile changes a setting at once: a stale click must never reach a reused window. */
    @Override public boolean reuseWindow() { return false; }

    @Override
    protected void content(MenuView v) {
        Player p = v.viewer();
        ClassService svc = plugin.getClassService();
        if (svc == null || svc.catalog().classes().isEmpty()) {
            v.set(22, Ui.item(Material.GRAY_DYE, ChatColor.GRAY + "Classes are not available right now",
                    Ui.lore().text("The class files could not be loaded.").text("Tell an admin.").build()));
            return;
        }
        ClassProfile prof = svc.profile(p.getUniqueId());
        if (prof == null) {
            loading(v);
            return;
        }
        List<ClassDef> classes = new ArrayList<>(svc.catalog().classes().values());
        int learned = 0;
        for (ClassDef c : classes) if (prof.knows(c.id())) learned++;
        Loadout inUse = prof.activeLoadout();
        v.set(MenuView.HEADER, Ui.item(Material.NETHER_STAR, ChatColor.GOLD + "" + ChatColor.BOLD + "Classes & Abilities",
                Ui.lore()
                        .stat("Classes learned", learned + " / " + classes.size())
                        .stat("In use", "Loadout " + (prof.activeIndex() + 1) + " (" + inUse.name() + ")")
                        .gap().text("Learn several classes and mix their")
                        .text("abilities in one loadout.").build()));

        for (int i = 0; i < classes.size() && i < CLASS_SLOTS.length; i++) {
            ClassDef c = classes.get(i);
            v.button(CLASS_SLOTS[i], ClassView.classTile(svc, prof, c, p.getUniqueId()),
                    k -> menus.open(k.player(), new ClassTreeMenu(plugin, c.id())));
        }

        v.button(31, controlsTile(svc, p), k -> {
            UUID id = k.player().getUniqueId();
            if (k.isRight()) {
                Controls.BarMode m = svc.barMode(id);
                svc.setBarMode(k.player(), m == Controls.BarMode.AUTO ? Controls.BarMode.ON : m == Controls.BarMode.ON ? Controls.BarMode.OFF : Controls.BarMode.AUTO);
            } else {
                svc.setScheme(k.player(), svc.scheme(id) == Controls.Scheme.QUICK ? Controls.Scheme.COMBO : Controls.Scheme.QUICK);
            }
            menus.open(k.player(), new ClassesMenu(plugin));
        });

        for (int i = 0; i < prof.loadouts().size() && i < LOADOUT_SLOTS.length; i++) {
            int index = i;
            v.button(LOADOUT_SLOTS[i], loadoutTile(svc, prof, i), k -> menus.open(k.player(), new LoadoutMenu(plugin, index)));
        }
    }

    /** How to cast with this player's scheme; click switches the scheme, right-click the ability bar. */
    private static org.bukkit.inventory.ItemStack controlsTile(ClassService svc, Player p) {
        UUID id = p.getUniqueId();
        Controls.Scheme scheme = svc.scheme(id);
        Ui.Lore lore = Ui.lore().text("With a weapon, a focus (blaze rod)").text("or an empty hand:");
        for (int s = 0; s < Loadout.ACTIVE_SLOTS; s++) {
            lore.raw(ChatColor.WHITE + "  " + Controls.keyLabel(scheme, s, false) + ChatColor.GRAY + "  active " + (s + 1));
        }
        lore.gap().text("With a bow, right and left swap.");
        lore.text(scheme == Controls.Scheme.QUICK ? "F and Q never swap or drop a weapon." : "Q still drops; F never swaps.");
        lore.text("A tool, block or food: keys as usual.");
        lore.text("A shield or food in the off hand keeps");
        lore.text("right-click for itself (a totem doesn't).");
        lore.gap().stat("Ability bar", svc.barMode(id).id());
        Controls.Scheme other = scheme == Controls.Scheme.QUICK ? Controls.Scheme.COMBO : Controls.Scheme.QUICK;
        lore.hint("Click: switch to " + other.displayName);
        lore.hint("Right-click: ability bar auto / on / off");
        return Ui.item(Material.LIGHT, ChatColor.YELLOW + "Controls: " + scheme.displayName, lore.build());
    }

    private org.bukkit.inventory.ItemStack loadoutTile(ClassService svc, ClassProfile prof, int i) {
        Loadout l = prof.loadouts().get(i);
        boolean active = i == prof.activeIndex();
        Ui.Lore lore = Ui.lore();
        lore.raw(active ? ChatColor.GREEN + "▶ In use" : ChatColor.GRAY + "Not in use");
        lore.gap();
        for (int s = 0; s < Loadout.ACTIVE_SLOTS; s++) lore.raw(ChatColor.GRAY + "A" + (s + 1) + " " + ChatColor.WHITE + name(svc, l.actives().get(s)));
        for (int s = 0; s < Loadout.PASSIVE_SLOTS; s++) lore.raw(ChatColor.GRAY + "P" + (s + 1) + " " + ChatColor.WHITE + name(svc, l.passives().get(s)));
        lore.raw(ChatColor.GRAY + "K  " + ChatColor.WHITE + name(svc, l.keystone()));
        lore.hint(active ? "Click: edit" : "Click: edit or use");
        return Ui.glow(Ui.item(active ? Material.WRITABLE_BOOK : Material.BOOK,
                (active ? ChatColor.GREEN : ChatColor.YELLOW) + "Loadout " + (i + 1) + ChatColor.GRAY + " (" + l.name() + ")", lore.build()), active);
    }

    private static String name(ClassService svc, String abilityId) {
        if (abilityId == null) return ChatColor.DARK_GRAY + "-";
        var a = svc.catalog().ability(abilityId);
        return a == null ? ChatColor.DARK_GRAY + abilityId + " (unavailable)" : a.displayName();
    }
}

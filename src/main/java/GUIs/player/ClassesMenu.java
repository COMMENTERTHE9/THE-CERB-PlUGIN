package GUIs.player;

import Classes.ClassDef;
import Classes.ClassProfile;
import Classes.ClassService;
import Classes.Loadout;
import GUIs.menu.MenuView;
import GUIs.menu.Ui;
import cerberus.world.cerb.CerberusPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

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

        v.set(31, Ui.item(Material.LIGHT, ChatColor.YELLOW + "How to cast", Ui.lore()
                .text("Hold Sneak, then press:")
                .raw(ChatColor.WHITE + "  right-click" + ChatColor.GRAY + " active 1")
                .raw(ChatColor.WHITE + "  left-click" + ChatColor.GRAY + " active 2")
                .raw(ChatColor.WHITE + "  F" + ChatColor.GRAY + " active 3 · " + ChatColor.WHITE + "Q" + ChatColor.GRAY + " active 4")
                .raw(ChatColor.WHITE + "  scroll" + ChatColor.GRAY + " active 5")
                .gap().text("With a weapon, a focus (blaze rod)")
                .text("or an empty hand. With a tool or")
                .text("a block, keys work as usual.")
                .gap().text("Loadouts change only out of combat.")
                .build()));

        for (int i = 0; i < prof.loadouts().size() && i < LOADOUT_SLOTS.length; i++) {
            int index = i;
            v.button(LOADOUT_SLOTS[i], loadoutTile(svc, prof, i), k -> menus.open(k.player(), new LoadoutMenu(plugin, index)));
        }
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
        var a = svc.catalog().ability(abilityId);
        return a == null ? ChatColor.DARK_GRAY + "-" : a.displayName();
    }
}

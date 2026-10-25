package GUIs.player;

import Classes.AbilityDef;
import Classes.ClassDef;
import Classes.ClassProfile;
import Classes.ClassService;
import GUIs.menu.MenuView;
import GUIs.menu.Ui;
import cerberus.world.cerb.CerberusPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * One class's abilities, in unlock order: unlocked ones in colour, the rest with the rank that
 * opens them (CLASSES.md §7). Read-only; abilities are equipped from a loadout.
 */
public final class ClassTreeMenu extends PlayerMenu {

    private static final int[] SLOTS = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};

    private final String classId;

    public ClassTreeMenu(CerberusPlugin plugin, String classId) {
        super(plugin);
        this.classId = classId;
    }

    @Override
    public String title() {
        ClassService svc = plugin.getClassService();
        ClassDef c = svc == null ? null : svc.catalog().classDef(classId);
        return PlayerTheme.title(c == null ? "Class" : c.displayName());
    }

    @Override
    protected void content(MenuView v) {
        Player p = v.viewer();
        ClassService svc = plugin.getClassService();
        ClassDef c = svc == null ? null : svc.catalog().classDef(classId);
        ClassProfile prof = svc == null ? null : svc.profile(p.getUniqueId());
        if (c == null) {
            v.set(22, Ui.item(Material.GRAY_DYE, ChatColor.GRAY + "This class is gone", Ui.lore().text("It was removed from the class files.").build()));
            return;
        }
        if (prof == null) {
            loading(v);
            return;
        }
        v.set(MenuView.HEADER, ClassView.classTile(svc, prof, c, p.getUniqueId()));
        List<AbilityDef> list = new ArrayList<>();
        for (String id : c.abilities()) {
            AbilityDef a = svc.catalog().ability(id);
            if (a != null) list.add(a);
        }
        list.sort(Comparator.comparingInt(AbilityDef::unlockRank));
        for (int i = 0; i < list.size() && i < SLOTS.length; i++) {
            v.set(SLOTS[i], ClassView.abilityTile(svc, prof, list.get(i), null));
        }
        v.set(40, Ui.item(Material.WRITABLE_BOOK, ChatColor.YELLOW + "Equipping", Ui.lore()
                .text("Open a loadout (Classes & Abilities)")
                .text("and click a slot to put any unlocked")
                .text("ability in it, from any class.").build()));
    }
}

package GUIs.player;

import Classes.AbilityDef;
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
 * Choose what goes in one loadout slot: every ability of the right kind from every class,
 * unlocked ones first (CLASSES.md §7). Picking one asks {@link ClassService#equip}, which checks
 * everything again on the server (unlocked, kind, one stance..., combat lock); the button itself
 * carries only the ability id bound here, never anything read from the item.
 */
public final class AbilityPickerMenu extends PlayerMenu {

    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};

    private final int index;
    private final ClassService.Slot slot;
    private final int pos;

    public AbilityPickerMenu(CerberusPlugin plugin, int index, ClassService.Slot slot, int pos) {
        super(plugin);
        this.index = index;
        this.slot = slot;
        this.pos = pos;
    }

    /** Its buttons change the loadout at once: a fresh window, so a click meant for the screen before never lands here. */
    @Override public boolean reuseWindow() { return false; }

    private String label() {
        return switch (slot) {
            case ACTIVE -> "Active " + (pos + 1);
            case PASSIVE -> "Passive " + (pos + 1);
            case KEYSTONE -> "Keystone";
        };
    }

    @Override public String title() { return PlayerTheme.title("Choose: " + label()); }

    @Override
    protected void content(MenuView v) {
        Player p = v.viewer();
        ClassService svc = plugin.getClassService();
        ClassProfile prof = svc == null ? null : svc.profile(p.getUniqueId());
        if (prof == null || index < 0 || index >= prof.loadouts().size()) {
            loading(v);
            return;
        }
        Loadout l = prof.loadouts().get(index);
        String current = switch (slot) {
            case ACTIVE -> l.actives().get(pos);
            case PASSIVE -> l.passives().get(pos);
            case KEYSTONE -> l.keystone();
        };
        AbilityDef.Kind kind = switch (slot) {
            case ACTIVE -> AbilityDef.Kind.ACTIVE;
            case PASSIVE -> AbilityDef.Kind.PASSIVE;
            case KEYSTONE -> AbilityDef.Kind.KEYSTONE;
        };
        Ui.Lore head = Ui.lore().stat("Loadout", (index + 1) + " (" + l.name() + ")");
        if (slot == ClassService.Slot.ACTIVE) head.stat("Key", ClassView.KEYS[pos]);
        head.gap().text("Any unlocked ability fits, from").text("any class you have learned.");
        v.set(MenuView.HEADER, Ui.item(Material.WRITABLE_BOOK, ChatColor.GOLD + "" + ChatColor.BOLD + "Choose for " + label(), head.build()));

        // Unlocked first (class order, then rank), then the locked ones of learned classes, then the rest.
        List<AbilityDef> open = new ArrayList<>(), closed = new ArrayList<>();
        for (ClassDef c : svc.catalog().classes().values()) {
            for (String id : c.abilities()) {
                AbilityDef a = svc.catalog().ability(id);
                if (a == null || a.kind() != kind) continue;
                (prof.unlocked(a) ? open : closed).add(a);
            }
        }
        closed.sort((a, b) -> Boolean.compare(!prof.knows(a.classId()), !prof.knows(b.classId())));
        int i = 0;
        for (AbilityDef a : open) {
            if (i >= SLOTS.length) break;
            boolean here = a.id().equals(current);
            var tile = ClassView.abilityTile(svc, prof, a, here ? "In this slot now" : "Click to put it in " + label());
            v.button(SLOTS[i++], Ui.glow(tile, here), k -> {
                String why = svc.equip(k.player(), index, slot, pos, a.id());
                if (why == null) {
                    Ui.ok(k.player(), a.displayName() + " is in " + label() + " of Loadout " + (index + 1) + ".");
                    back(k.player());
                } else {
                    Ui.fail(k.player(), why);
                    menus.refresh(k.player());
                }
            });
        }
        for (AbilityDef a : closed) {
            if (i >= SLOTS.length) break;
            v.set(SLOTS[i++], ClassView.abilityTile(svc, prof, a, null));
        }
        if (open.isEmpty()) {
            v.set(40, Ui.item(Material.GRAY_DYE, ChatColor.GRAY + "Nothing unlocked for this slot yet",
                    Ui.lore().text("Learn a class and raise its rank:").text("its abilities unlock as you go.").build()));
        }
        if (current != null) {
            v.button(41, Ui.item(Material.BUCKET, ChatColor.YELLOW + "Empty this slot",
                    Ui.lore().text("Take the ability out of " + label() + ".").hint("Click to empty").build()), k -> {
                String why = svc.equip(k.player(), index, slot, pos, null);
                if (why == null) {
                    Ui.ok(k.player(), label() + " of Loadout " + (index + 1) + " is empty.");
                    back(k.player());
                } else {
                    Ui.fail(k.player(), why);
                    menus.refresh(k.player());
                }
            });
        }
    }
}

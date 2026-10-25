package GUIs.player;

import Classes.AbilityDef;
import Classes.ClassProfile;
import Classes.ClassService;
import Classes.Loadout;
import GUIs.menu.MenuView;
import GUIs.menu.Ui;
import cerberus.world.cerb.CerberusPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * One loadout (CLASSES.md §6–7): its five actives (with the key that casts each), three passives
 * and keystone. Click a slot to choose what goes in it; "Use this loadout" switches to it. The
 * server checks every change (combat lock, switch gap, unlocks, slot rules) in {@link ClassService}.
 */
public final class LoadoutMenu extends PlayerMenu {

    static final int[] ACTIVE_SLOTS = {20, 21, 22, 23, 24};
    static final int[] PASSIVE_SLOTS = {29, 30, 31};
    static final int KEYSTONE_SLOT = 33;

    private final int index;

    public LoadoutMenu(CerberusPlugin plugin, int index) {
        super(plugin);
        this.index = index;
    }

    /** Its buttons change the loadout at once: a fresh window, so a click meant for the screen before never lands here. */
    @Override public boolean reuseWindow() { return false; }

    @Override public String title() { return PlayerTheme.title("Loadout " + (index + 1)); }

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
        boolean active = index == prof.activeIndex();
        v.set(MenuView.HEADER, Ui.item(active ? Material.WRITABLE_BOOK : Material.BOOK,
                ChatColor.GOLD + "" + ChatColor.BOLD + "Loadout " + (index + 1) + ChatColor.GRAY + " (" + l.name() + ")",
                Ui.lore().raw(active ? ChatColor.GREEN + "▶ In use" : ChatColor.GRAY + "Not in use")
                        .gap().text("5 actives, 3 passives, 1 keystone,")
                        .text("from any classes you have learned.")
                        .text("Rename: /class name " + (index + 1) + " <name>").build()));

        for (int s = 0; s < Loadout.ACTIVE_SLOTS; s++) {
            int pos = s;
            v.button(ACTIVE_SLOTS[s], ClassView.slotTile(svc, prof, "Active " + (s + 1), ClassView.KEYS[s], l.actives().get(s), "Click to choose"),
                    k -> menus.open(k.player(), new AbilityPickerMenu(plugin, index, ClassService.Slot.ACTIVE, pos)));
        }
        for (int s = 0; s < Loadout.PASSIVE_SLOTS; s++) {
            int pos = s;
            v.button(PASSIVE_SLOTS[s], ClassView.slotTile(svc, prof, "Passive " + (s + 1), null, l.passives().get(s), "Click to choose"),
                    k -> menus.open(k.player(), new AbilityPickerMenu(plugin, index, ClassService.Slot.PASSIVE, pos)));
        }
        v.button(KEYSTONE_SLOT, ClassView.slotTile(svc, prof, "Keystone", null, l.keystone(), "Click to choose"),
                k -> menus.open(k.player(), new AbilityPickerMenu(plugin, index, ClassService.Slot.KEYSTONE, 0)));

        // What the passives and keystone add while this loadout is in use.
        Ui.Lore bonus = Ui.lore().text("While this loadout is in use:");
        boolean any = false;
        for (String id : l.equipped()) {
            AbilityDef a = svc.catalog().ability(id);
            if (a == null || a.isActive()) continue;
            for (AbilityDef.Passive ps : a.passives()) {
                bonus.raw(ChatColor.WHITE + "  " + ClassService.bonusText(ps) + ChatColor.DARK_GRAY + " (" + a.displayName() + ")");
                any = true;
            }
        }
        if (!any) bonus.raw(ChatColor.DARK_GRAY + "  nothing yet");
        bonus.gap().text("Bonuses count inside the usual caps.");
        v.set(35, Ui.item(Material.BLAZE_POWDER, ChatColor.YELLOW + "Bonuses", bonus.build()));

        Ui.Lore rules = Ui.lore()
                .text("Switching and editing the loadout")
                .text("in use: only out of combat (8 s")
                .text("after the last hit), 10 s between")
                .text("switches, no casting for 1 s after.")
                .gap().text("Cooldowns follow you, not the")
                .text("loadout. Progress never changes.");
        if (active) {
            v.set(40, Ui.item(Material.LIME_DYE, ChatColor.GREEN + "In use", rules.build()));
        } else {
            v.button(40, Ui.item(Material.EMERALD, ChatColor.GREEN + "Use this loadout", rules.hint("Click to switch").build()), k -> {
                String why = svc.switchTo(k.player(), index);
                if (why == null) Ui.ok(k.player(), "Now using Loadout " + (index + 1) + " (" + prof.activeLoadout().name() + ").");
                else Ui.fail(k.player(), why);
                menus.refresh(k.player());
            });
        }
    }
}

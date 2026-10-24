package GUIs.admin;

import GUIs.menu.Fmt;
import GUIs.menu.MenuView;
import GUIs.menu.Ui;
import org.bukkit.ChatColor;
import org.bukkit.Material;

import java.util.List;

/** Choose what to spawn: mobs.yml entries first (highest tier first), then vanilla test mobs. */
final class MobPickerMenu extends AdminMenu {

    private int page;

    MobPickerMenu(AdminPanel panel) { super(panel); }

    @Override public String title() { return PREFIX + "Spawn: choose a mob"; }

    @Override
    protected void build(MenuView v) {
        frame(v);
        List<MobChoice> choices = MobChoice.all(plugin.getMobRegistry());
        v.set(MenuView.HEADER, Ui.item(Material.SPAWNER, ChatColor.GREEN + "" + ChatColor.BOLD + "Choose a mob",
                Ui.lore().stat("mobs.yml entries", String.valueOf(choices.stream().filter(c -> c.mobId() != null).count()))
                        .text("Vanilla mobs resolve through the registry").text("too (unlisted types are Common).").build()));
        page = v.page(choices, page, ch -> {
            Ui.Lore lore = Ui.lore()
                    .stat("Tier", ch.tier().displayColor + ch.tier().displayName)
                    .stat("Entity", ch.type() == null ? "?" : Fmt.title(ch.type().name()))
                    .stat("Kill XP multiplier", Fmt.mult(ch.xpMultiplier()))
                    .stat("Defined in", ch.source());
            if (ch.customName() != null) lore.stat("Name", ch.customName());
            if (ch.spawnable()) lore.hint("Click to set level and spawn");
            else lore.gap().warn(ch.blockedReason());
            ChatColor color = ch.spawnable() ? ch.tier().displayColor : ChatColor.DARK_GRAY;
            return Ui.item(ch.icon(), color + Fmt.title(ch.label()), lore.build());
        }, ch -> ch.spawnable() ? c -> open(c.player(), new SpawnSetupMenu(panel, ch)) : null, np -> page = np);
    }
}

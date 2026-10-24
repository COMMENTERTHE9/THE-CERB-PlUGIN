package GUIs.admin;

import GUIs.menu.Fmt;
import GUIs.menu.MenuView;
import GUIs.menu.Ui;
import Manager.MobTestService;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;

import java.util.List;

/** Mobs around the admin with their registry tier, level and HP pool. */
final class NearbyMobsMenu extends AdminMenu {

    private int page;

    NearbyMobsMenu(AdminPanel panel) { super(panel); }

    @Override public String title() { return PREFIX + "Nearby mobs"; }

    @Override
    protected void build(MenuView v) {
        frame(v);
        v.refreshButton();
        MobTestService tools = panel.mobTools();
        List<LivingEntity> mobs = tools.nearbyMobs(v.viewer(), CombatLabMenu.NEARBY_RADIUS);
        v.set(MenuView.HEADER, Ui.item(Material.SPYGLASS, ChatColor.AQUA + "" + ChatColor.BOLD + "Nearby mobs",
                Ui.lore().stat("Within " + (int) CombatLabMenu.NEARBY_RADIUS + " blocks", String.valueOf(mobs.size()))
                        .text("Nearest first. Click one to inspect.").build()));
        page = v.page(mobs, page, mob -> render(tools.describe(mob, v.viewer().getLocation())),
                mob -> c -> open(c.player(), new MobInspectMenu(panel, mob)), np -> page = np);
        if (mobs.isEmpty()) v.set(22, Ui.item(Material.GRAY_DYE, ChatColor.GRAY + "No mobs nearby",
                Ui.lore().text("Spawn one from the Combat Lab.").build()));
    }

    /** Shared tile for a mob (also used as the inspector header). */
    static org.bukkit.inventory.ItemStack render(MobTestService.MobInfo info) {
        Material egg = Material.matchMaterial(info.typeName() + "_SPAWN_EGG");
        String name = info.displayName() != null ? info.displayName() : Fmt.title(info.typeName());
        Ui.Lore lore = Ui.lore()
                .stat("Tier", info.tier().displayColor + info.tier().displayName + ChatColor.GRAY + "   Level " + ChatColor.WHITE + info.level())
                .stat("HP", Fmt.compact(info.poolCurrent()) + " / " + Fmt.compact(info.poolMax()) + "  (" + Fmt.pct(info.fraction()) + ")")
                .raw(ChatColor.RED + Fmt.bar(info.poolCurrent(), info.poolMax(), 20))
                .stat("Damage multiplier", Fmt.mult(info.damageMultiplier()))
                .stat("Kill XP multiplier", Fmt.mult(info.xpMultiplier()))
                .stat("Registry", info.mobId() + (info.pooled() ? "" : ChatColor.YELLOW + "  (no HP pool: vanilla HP)"))
                .stat("AI", info.ai() ? "on" : "off (punching bag)")
                .stat("Distance", info.distance() < 0 ? "?" : Fmt.one(info.distance()) + " blocks");
        if (info.testSpawn()) lore.raw(ChatColor.DARK_AQUA + "Test spawn (removed by cleanup)");
        return Ui.item(egg != null ? egg : Material.SPAWNER, info.tier().displayColor + name, lore.hint("Click to inspect").build());
    }
}

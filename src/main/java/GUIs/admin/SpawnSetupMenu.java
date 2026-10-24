package GUIs.admin;

import GUIs.menu.Fmt;
import GUIs.menu.MenuClick;
import GUIs.menu.MenuView;
import GUIs.menu.Ui;
import Manager.CombatConfig;
import Manager.MobTestService;
import Manager.MobVirtualHealthManager;
import Skills.SkillProgression;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/** Level / count / AI for one mob choice, a live preview, and Spawn. */
final class SpawnSetupMenu extends AdminMenu {

    private static final int[] PRESETS = {1, 5, 10, 25, 50, 75, 100};
    private static final int[] COUNTS = {1, 3, 5};

    private final MobChoice choice;
    private int level = 1;
    private int count = 1;
    private boolean ai = false;

    SpawnSetupMenu(AdminPanel panel, MobChoice choice) {
        super(panel);
        this.choice = choice;
    }

    @Override public String title() { return PREFIX + "Spawn " + Fmt.title(choice.label()); }

    @Override
    protected void build(MenuView v) {
        frame(v);
        CombatConfig cc = plugin.getCombatConfig();
        int maxLevel = cc.mobHealthScaling.getMaxLevel();
        level = Math.max(1, Math.min(maxLevel, level));
        boolean scaled = cc.mobHealthScaling.isEnabled();
        double pool = cc.mobHealthScaling.calculate(choice.tier(), level);
        double dmg = cc.mobDamageScaling.isEnabled() ? cc.mobDamageScaling.calculate(choice.tier(), level) : 1.0;
        var kx = plugin.getAttributesConfig().killXp();
        double killXp = kx.enabled()
                ? Attributes.KillXp.compute(level, choice.xpMultiplier(), 1, kx)
                : SkillProgression.killXp(MobVirtualHealthManager.COSMETIC_MAX_HEALTH, MobVirtualHealthManager.COSMETIC_MAX_HEALTH, choice.xpMultiplier());

        v.set(MenuView.HEADER, Ui.amount(Ui.item(choice.icon(), choice.tier().displayColor + "" + ChatColor.BOLD + Fmt.title(choice.label()),
                Ui.lore().stat("Tier", choice.tier().displayColor + choice.tier().displayName)
                        .stat("Level", level + " / " + maxLevel)
                        .stat("HP pool", scaled ? Fmt.compact(pool) + " each" : "vanilla (scaling disabled)")
                        .stat("Damage multiplier", Fmt.mult(dmg))
                        .stat("Kill XP", Fmt.grouped(Math.round(killXp)) + " to the weapon's skill" + (kx.enabled() ? "" : "  (old rule)"))
                        .stat("Will spawn", count + " x, " + (ai ? "normal AI" : "no AI (punching bag)"))
                        .stat("Where", "3 blocks in front of you")
                        .build()), Math.min(64, level)));

        // Level stepping + presets
        step(v, 19, Material.RED_CONCRETE, -10, maxLevel);
        step(v, 20, Material.PINK_CONCRETE, -1, maxLevel);
        v.set(22, Ui.amount(Ui.item(Material.EXPERIENCE_BOTTLE, ChatColor.YELLOW + "Level " + level,
                Ui.lore().text("Mob level 1-" + maxLevel + ". Normally it comes from").text("distance to world spawn.").build()), Math.min(64, level)));
        step(v, 24, Material.LIME_CONCRETE, +1, maxLevel);
        step(v, 25, Material.GREEN_CONCRETE, +10, maxLevel);
        for (int i = 0; i < PRESETS.length; i++) {
            int lv = Math.min(maxLevel, PRESETS[i]);
            v.button(28 + i, Ui.glow(Ui.amount(Ui.item(lv == level ? Material.GOLD_BLOCK : Material.IRON_BLOCK,
                    ChatColor.WHITE + "Level " + lv, Ui.lore().stat("HP pool", Fmt.compact(cc.mobHealthScaling.calculate(choice.tier(), lv))).build()),
                    Math.min(64, lv)), lv == level), c -> { level = lv; menus.refresh(c.player()); });
        }

        // Count
        for (int i = 0; i < COUNTS.length; i++) {
            int n = COUNTS[i];
            v.button(37 + i, Ui.glow(Ui.amount(Ui.item(Material.ZOMBIE_HEAD, ChatColor.WHITE + "Spawn " + n,
                    Ui.lore().text(n == count ? "Selected" : "Click to select").build()), n), n == count),
                    c -> { count = n; menus.refresh(c.player()); });
        }

        // AI toggle
        v.button(40, Ui.glow(Ui.item(ai ? Material.REDSTONE_TORCH : Material.LEVER,
                ChatColor.WHITE + "Behaviour: " + (ai ? ChatColor.RED + "hostile (AI on)" : ChatColor.AQUA + "punching bag (no AI)"),
                Ui.lore().text("No AI: stands still, takes knockback,").text("never attacks. Good for damage tests.")
                        .hint("Click to switch").build()), ai), c -> { ai = !ai; menus.refresh(c.player()); });

        // Spawn buttons
        v.button(42, Ui.item(Material.EMERALD, ChatColor.GREEN + "" + ChatColor.BOLD + "Spawn",
                Ui.lore().text("Spawns and keeps this screen open.").text("Test mobs are tagged for cleanup and").text("never saved into the world.").build()),
                c -> spawn(c, false));
        v.button(43, Ui.item(Material.EMERALD_BLOCK, ChatColor.GREEN + "" + ChatColor.BOLD + "Spawn & close",
                Ui.lore().text("Spawns and closes the menu so you").text("can fight right away.").build()),
                c -> spawn(c, true));
    }

    private void step(MenuView v, int slot, Material m, int delta, int maxLevel) {
        int target = Math.max(1, Math.min(maxLevel, level + delta));
        v.button(slot, Ui.item(m, (delta > 0 ? ChatColor.GREEN + "+" : ChatColor.RED + "") + delta + " level" + (Math.abs(delta) == 1 ? "" : "s"),
                Ui.lore().stat("Result", level + " -> " + target).build()), c -> { level = target; menus.refresh(c.player()); });
    }

    private void spawn(MenuClick c, boolean close) {
        Player admin = c.player();
        Location base = admin.getLocation();
        Vector fwd = base.getDirection().setY(0);
        if (fwd.lengthSquared() < 1e-6) fwd = new Vector(0, 0, 1);
        fwd.normalize();
        Vector side = new Vector(-fwd.getZ(), 0, fwd.getX());
        MobTestService.SpawnRequest req = new MobTestService.SpawnRequest(choice.type(), choice.mobId(),
                choice.customName(), level, ai, true);
        int spawned = 0;
        for (int i = 0; i < count; i++) {
            double offset = (i - (count - 1) / 2.0) * 1.5;
            Location at = base.clone().add(fwd.clone().multiply(3)).add(side.clone().multiply(offset));
            at.setYaw(base.getYaw() + 180f);
            if (Bukkit.isOwnedByCurrentRegion(at)) {
                panel.mobTools().spawn(at, req);
                spawned++;
            } else {
                Bukkit.getRegionScheduler().run(plugin, at, t -> panel.mobTools().spawn(at, req));
                spawned++;
            }
        }
        panel.audit(admin, "spawned " + spawned + " x " + choice.label() + " level " + level + (ai ? " (AI)" : " (no AI)"));
        Ui.ok(admin, "Spawned " + spawned + " x " + Fmt.title(choice.label()) + " (" + choice.tier().displayName + ") at level " + level + ".");
        if (close) menus.close(admin);
    }
}

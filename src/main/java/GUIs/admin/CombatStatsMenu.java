package GUIs.admin;

import GUIs.menu.Fmt;
import GUIs.menu.MenuView;
import GUIs.menu.Status;
import GUIs.menu.Ui;
import Manager.CombatConfig;
import Manager.Tier;
import Skills.SkillManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.UUID;

/** Read-only combat diagnostics for one player. */
final class CombatStatsMenu extends AdminMenu {

    private static final String[] COMBAT_SKILLS = {"Blade Mastery", "Weapon Mastery", "Martial Expertise",
            "Critical Strike", "Ranged Precision", "Heavy Armor Training", "Survival"};

    private final UUID targetId;

    CombatStatsMenu(AdminPanel panel, UUID targetId) {
        super(panel);
        this.targetId = targetId;
    }

    @Override public String title() { return PREFIX + "Combat stats"; }

    @Override
    protected void build(MenuView v) {
        frame(v);
        v.refreshButton();
        Player t = Bukkit.getPlayer(targetId);
        if (t == null) { v.set(22, Ui.item(Material.GRAY_DYE, ChatColor.GRAY + "This player is offline")); return; }
        PlayerVitals pv = PlayerVitals.of(plugin, t);
        CombatConfig cc = plugin.getCombatConfig();
        SkillManager sm = plugin.getSkillManager();

        v.set(MenuView.HEADER, Ui.head(t, ChatColor.YELLOW + "" + ChatColor.BOLD + t.getName() + ": combat",
                Ui.lore().text("Live numbers from the combat pipeline.").build()));

        v.set(19, Ui.item(Material.GLISTERING_MELON_SLICE, ChatColor.RED + "Health",
                Ui.lore().stat("Custom HP", Fmt.compact(pv.hp()) + " / " + Fmt.compact(pv.maxHp()))
                        .stat("Regen out of combat", Fmt.one(cc.healthRegenBaseRate) + " / s")
                        .stat("Regen in combat", Fmt.one(cc.healthRegenCombatRate) + " / s")
                        .stat("Hit invulnerability", cc.damageInvincibilityMs + " ms")
                        .stat("Respawn invulnerability", cc.respawnInvincibilityMs + " ms").build()));
        v.set(20, Ui.item(Material.SHIELD, ChatColor.GRAY + "Defense pipeline",
                Ui.lore().text("Incoming hit -> mob scaling -> resist ->").text("armor -> defense bar -> custom HP")
                        .stat("Armor reduction", Fmt.pct(pv.armorReduction()) + "  (cap " + Fmt.pct(cc.armorMaxReduction) + ")")
                        .stat("Defense bar", Fmt.compact(pv.defense()) + " / " + Fmt.compact(pv.maxDefense()))
                        .stat("Bar absorbs up to", Fmt.pct(cc.defBarMaxAbsorption) + " of a hit")
                        .stat("Bar regen", Fmt.one(cc.defBarRegenRate) + " / s after " + cc.defBarRegenDelayMs + " ms").build()));
        v.set(21, Ui.item(Material.MAGMA_CREAM, ChatColor.GOLD + "Resistances",
                Ui.lore().stat("Fire", Fmt.pct(pv.fireRes())).stat("Cold", Fmt.pct(pv.coldRes()))
                        .stat("Drowning", Fmt.pct(pv.drownRes())).build()));
        v.set(22, Ui.item(Material.LAPIS_LAZULI, ChatColor.BLUE + "Mana",
                Ui.lore().stat("Mana", Fmt.compact(pv.mana()) + " / " + Fmt.compact(pv.maxMana()))
                        .stat("Regeneration", Fmt.one(pv.manaRegen()) + " / s").build()));

        Ui.Lore skills = Ui.lore();
        for (String s : COMBAT_SKILLS) skills.stat(s, "Lv " + sm.getSkillLevel(t, s));
        skills.gap().status(Status.PARTIAL)
                .text("Skill damage bonuses (Blade Mastery,")
                .text("crits, lifesteal) apply in PvP only today;")
                .text("against mobs they arrive with Attributes.");
        v.set(24, Ui.item(Material.IRON_SWORD, ChatColor.YELLOW + "Offense & combat skills", skills.build()));

        Ui.Lore threat = Ui.lore().text("Mob damage multiplier by tier and level:");
        for (Tier tier : new Tier[]{Tier.COMMON, Tier.RARE, Tier.EPIC, Tier.LEGENDARY, Tier.PRIMORDIAL}) {
            threat.raw(tier.displayColor + "  " + tier.displayName + ChatColor.GRAY + "  L1 " + ChatColor.WHITE
                    + Fmt.mult(cc.mobDamageScaling.calculate(tier, 1)) + ChatColor.GRAY + "  L50 " + ChatColor.WHITE
                    + Fmt.mult(cc.mobDamageScaling.calculate(tier, 50)) + ChatColor.GRAY + "  L100 " + ChatColor.WHITE
                    + Fmt.mult(cc.mobDamageScaling.calculate(tier, 100)));
        }
        v.set(25, Ui.item(Material.ZOMBIE_HEAD, ChatColor.DARK_RED + "Mob threat table", threat.build()));
    }
}

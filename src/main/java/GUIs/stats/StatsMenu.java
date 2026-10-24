package GUIs.stats;

import Attributes.Attribute;
import Attributes.AttributeRules;
import Attributes.AttributeService;
import Attributes.AttributeSnapshot;
import Attributes.KillXp;
import Attributes.WeaponClass;
import GUIs.menu.Fmt;
import GUIs.menu.Menu;
import GUIs.menu.MenuManager;
import GUIs.menu.MenuView;
import GUIs.menu.Ui;
import Manager.Tier;
import cerberus.world.cerb.CerberusPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * The player's character sheet (Player Menu → Stats): attributes with their sources and effects,
 * what the held weapon does, survivability and magic, the next skill milestone, and kill XP.
 * Read-only; every number comes from the same {@link AttributeSnapshot} combat uses.
 */
public final class StatsMenu extends Menu {

    private static final Attribute[] ORDER = {Attribute.POWER, Attribute.AGILITY, Attribute.VITALITY,
            null, Attribute.FORTITUDE, Attribute.ESSENCE, Attribute.FORTUNE};

    private final CerberusPlugin plugin;

    public StatsMenu(MenuManager menus, CerberusPlugin plugin) {
        super(menus);
        this.plugin = plugin;
    }

    @Override public String title() { return ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "Your Stats"; }

    @Override
    protected void build(MenuView v) {
        Player p = v.viewer();
        UUID id = p.getUniqueId();
        AttributeService svc = plugin.getAttributeService();
        AttributeSnapshot s = svc.get(id);
        AttributeRules rules = s.rules();

        v.row(0, Material.PURPLE_STAINED_GLASS_PANE);
        v.row(5, Material.BLACK_STAINED_GLASS_PANE);
        v.set(MenuView.HEADER, Ui.head(p, ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + p.getName(), Ui.lore()
                .stat("Realm", "Awakened").raw(ChatColor.DARK_GRAY + "  (advancing realms is not built yet)")
                .stat("Total skill level", String.valueOf(plugin.getSkillManager().getPlayerLevel(p)))
                .gap().text("Your attributes grow by themselves").text("as your skills level up.").build()));

        if (!svc.isTracked(id)) {
            v.set(22, Ui.item(Material.CLOCK, ChatColor.YELLOW + "Loading your character...",
                    Ui.lore().text("Your saved data is still loading.").hint("Try again in a moment").build()));
            nav(v);
            return;
        }

        // Row 1: the six attributes, explanation in the middle
        for (int i = 0; i < ORDER.length; i++) {
            Attribute a = ORDER[i];
            if (a != null) v.set(10 + i, AttributeView.tile(a, s, svc.effect(a)));
        }
        v.set(13, Ui.item(Material.KNOWLEDGE_BOOK, ChatColor.GOLD + "How attributes work", Ui.lore()
                .text("Every skill level adds attribute points:")
                .text("Blade Mastery adds Power for swords,")
                .text("Survival adds Vitality, Intelligence adds")
                .text("Essence, and so on. Hover an attribute")
                .text("to see exactly which skills feed it.")
                .gap().text("Power keeps pace with mob levels: at")
                .text("Power 50 a level-50 mob takes about as")
                .text("many hits as a level-1 mob did at first.")
                .gap().text("Crits, bonuses and later gear stack on")
                .text("top, up to " + Fmt.mult(rules.limits().offenseBudget()) + " (crit chance max "
                        + Fmt.pct(rules.limits().critChance()) + ").").build()));

        // Row 3: outcomes
        ItemStack hand = p.getInventory().getItemInMainHand();
        WeaponClass wc = WeaponClass.ofMaterial(hand.getType().name());
        v.set(29, weaponTile(s, wc, hand.getType(), svc.effect(Attribute.POWER)));
        v.set(31, survivalTile(s, svc));
        v.set(33, magicTile(s, svc));

        // Row 4: progression
        v.set(38, milestoneTile(svc, id, s, wc));
        v.set(40, killXpTile(p, hand.getType()));
        v.button(42, Ui.item(Material.ENCHANTED_BOOK, ChatColor.GOLD + "Skills",
                Ui.lore().text("Levels and XP of every skill.").hint("Click to open").build()),
                c -> plugin.getPlayerMenuGUI().openSkillManagement(c.player()));
        nav(v);
    }

    private void nav(MenuView v) {
        v.button(MenuView.BACK, Ui.item(Material.ARROW, ChatColor.YELLOW + "Back",
                Ui.lore().text("Player Menu").build()), c -> plugin.getPlayerMenuGUI().open(c.player()));
        v.refreshButton();
        v.button(MenuView.CLOSE, Ui.item(Material.BARRIER, ChatColor.RED + "Close"), c -> menus.close(c.player()));
    }

    private static ItemStack weaponTile(AttributeSnapshot s, WeaponClass wc, Material held, AttributeService.Effect effect) {
        Ui.Lore l = Ui.lore().stat("Holding", held == Material.AIR ? "nothing (fists)" : Fmt.title(held.name()))
                .stat("Weapon type", wc.displayName);
        double power = s.power(wc);
        l.stat("Power", Fmt.one(power) + "  ->  " + Fmt.mult(s.powerMultiplier(wc)) + " damage")
                .stat("Crits", Fmt.pct(s.critChance()) + " for " + Fmt.mult(s.critMultiplier()))
                .stat("Average per hit", Fmt.mult(s.expectedOffenseMultiplier(wc)))
                .stat("Best possible hit", Fmt.mult(s.offenseMultiplier(wc, true)));
        if (s.lifesteal() > 0) l.stat("Lifesteal", Fmt.pct(s.lifesteal()));
        int even = (int) Math.min(100, Math.floor(1 + power));
        l.gap().text("Keeps pace with level-" + even + " mobs:")
                .text("they take about as many hits as a")
                .text("level-1 mob did with no Power.");
        if (wc == WeaponClass.OTHER) l.gap().warn("Not a weapon: only all-weapon Power counts.");
        l.gap().raw(AttributeView.effectLabel(effect, Attribute.POWER));
        return Ui.item(Material.NETHERITE_SWORD, ChatColor.GOLD + "Your weapon right now", l.build());
    }

    private static ItemStack survivalTile(AttributeSnapshot s, AttributeService svc) {
        Ui.Lore l = Ui.lore().text("From Vitality:")
                .stat("  Max health", Fmt.mult(s.maxHpMultiplier()))
                .raw(AttributeView.effectLabel(svc.effect(Attribute.VITALITY), Attribute.VITALITY))
                .gap().text("From Fortitude:")
                .stat("  Armor", "+" + Fmt.pct(s.armorBonus()) + " (all armor max " + Fmt.pct(s.rules().limits().armor()) + ")")
                .stat("  Defense bar", Fmt.mult(s.defenseBarMultiplier()))
                .stat("  Resistances", "+" + Fmt.pct(s.resist()))
                .raw(AttributeView.effectLabel(svc.effect(Attribute.FORTITUDE), Attribute.FORTITUDE));
        return Ui.item(Material.TOTEM_OF_UNDYING, ChatColor.RED + "Survivability", l.build());
    }

    private static ItemStack magicTile(AttributeSnapshot s, AttributeService svc) {
        Ui.Lore l = Ui.lore().text("From Essence:")
                .stat("  Max mana", Fmt.mult(s.maxManaMultiplier()))
                .stat("  Mana regeneration", Fmt.mult(s.manaRegenMultiplier()))
                .stat("  Spell damage", Fmt.mult(s.spellDamageMultiplier()))
                .raw(AttributeView.effectLabel(svc.effect(Attribute.ESSENCE), Attribute.ESSENCE));
        return Ui.item(Material.ENCHANTED_BOOK, ChatColor.AQUA + "Magic", l.build());
    }

    private ItemStack milestoneTile(AttributeService svc, UUID id, AttributeSnapshot s, WeaponClass wc) {
        AttributeRules rules = s.rules();
        Ui.Lore l = Ui.lore();
        String weaponSkill = AttributeView.weaponSkill(wc, rules);
        if (weaponSkill != null) {
            int lvl = plugin.getSkillManager().getSkillLevel(id, weaponSkill);
            if (lvl < Skills.SkillProgression.MAX_LEVEL) {
                AttributeSnapshot next = AttributeView.withSkillLevel(svc, id, weaponSkill, lvl + 1);
                AttributeSnapshot ten = AttributeView.withSkillLevel(svc, id, weaponSkill,
                        Math.min(Skills.SkillProgression.MAX_LEVEL, lvl + 10));
                l.text(weaponSkill + " " + lvl + " -> " + (lvl + 1) + ":")
                        .raw(ChatColor.WHITE + "  " + wc.displayName + " damage " + Fmt.mult(s.powerMultiplier(wc))
                                + ChatColor.GRAY + " -> " + ChatColor.GREEN + Fmt.mult(next.powerMultiplier(wc)))
                        .raw(ChatColor.GRAY + "  At level " + Math.min(Skills.SkillProgression.MAX_LEVEL, lvl + 10) + ": "
                                + ChatColor.GREEN + Fmt.mult(ten.powerMultiplier(wc)));
            } else {
                l.text(weaponSkill + " is at max level.");
            }
        } else {
            l.text("Hold a weapon to see what its skill's").text("next level gives you.");
        }
        int crit = plugin.getSkillManager().getSkillLevel(id, "Critical Strike");
        if (crit < Skills.SkillProgression.MAX_LEVEL && rules.skillWeights().containsKey("Critical Strike")) {
            AttributeSnapshot next = AttributeView.withSkillLevel(svc, id, "Critical Strike", crit + 1);
            l.gap().text("Critical Strike " + crit + " -> " + (crit + 1) + ":")
                    .raw(ChatColor.WHITE + "  Crit chance " + Fmt.pct(s.critChance()) + ChatColor.GRAY + " -> "
                            + ChatColor.GREEN + Fmt.pct(next.critChance()));
        }
        return Ui.item(Material.EXPERIENCE_BOTTLE, ChatColor.GREEN + "Next level gives", l.build());
    }

    private ItemStack killXpTile(Player p, Material held) {
        KillXp.Params kx = plugin.getAttributesConfig().killXp();
        var mapper = plugin.getSkillMapper();
        String skill = mapper == null ? "" : mapper.resolve(held == Material.AIR ? "AIR" : held.name());
        Ui.Lore l = Ui.lore();
        if (skill.isEmpty()) {
            l.text("This item trains no skill on kills.").text("Hold a weapon to see its kill XP.");
        } else if (!kx.enabled()) {
            l.text("Kill XP v2 is turned off on this server.");
        } else {
            int lvl = plugin.getSkillManager().getSkillLevel(p, skill);
            l.stat("Trains", skill + " (level " + lvl + ")").text("XP per kill:");
            for (int mobLevel : new int[]{1, 10, 25, 50, 100}) {
                long common = (long) KillXp.compute(mobLevel, Tier.COMMON.xpMultiplier, lvl, kx);
                long rare = (long) KillXp.compute(mobLevel, Tier.RARE.xpMultiplier, lvl, kx);
                boolean grey = KillXp.isGreyKill(mobLevel, lvl, kx);
                l.raw((grey ? ChatColor.DARK_GRAY : ChatColor.WHITE) + "  Level " + mobLevel + ": " + Fmt.grouped(common)
                        + ChatColor.GRAY + "  (Rare " + Fmt.grouped(rare) + ")" + (grey ? ChatColor.DARK_GRAY + " reduced" : ""));
            }
            if (kx.antiGrindGap() > 0) {
                l.gap().text("Mobs " + kx.antiGrindGap() + "+ levels below your skill")
                        .text("give " + Fmt.pct(kx.antiGrindFactor()).replace(".0%", "%") + " XP. Mob level rises")
                        .text("with distance from world spawn.");
            }
        }
        return Ui.item(Material.GOLD_NUGGET, ChatColor.YELLOW + "Kill XP", l.build());
    }
}

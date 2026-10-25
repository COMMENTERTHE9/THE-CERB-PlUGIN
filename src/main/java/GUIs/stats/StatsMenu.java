package GUIs.stats;

import Attributes.Attribute;
import Attributes.AttributeRules;
import Attributes.AttributeService;
import Attributes.AttributeSnapshot;
import Attributes.KillXp;
import Attributes.WeaponClass;
import GUIs.menu.Fmt;
import GUIs.menu.MenuView;
import GUIs.menu.Ui;
import GUIs.player.PlayerMenu;
import GUIs.player.PlayerTheme;
import Manager.Tier;
import cerberus.world.cerb.CerberusPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;

/**
 * The player's character sheet (Player Menu → Stats): attributes with their sources and effects,
 * what the held weapon does, survivability and magic, the next skill milestone, and kill XP.
 * Read-only; every number comes from the same {@link AttributeSnapshot} combat uses.
 */
public final class StatsMenu extends PlayerMenu {

    private static final Attribute[] ORDER = {Attribute.POWER, Attribute.AGILITY, Attribute.VITALITY,
            null, Attribute.FORTITUDE, Attribute.ESSENCE, Attribute.FORTUNE};
    /** Weapon types the weapon tile cycles through. */
    private static final WeaponClass[] WEAPONS = {WeaponClass.SWORD, WeaponClass.AXE, WeaponClass.MACE,
            WeaponClass.TRIDENT, WeaponClass.SPEAR, WeaponClass.BOW, WeaponClass.UNARMED};

    /** Weapon type shown in the weapon/next-level/kill-XP tiles; null = held weapon, else the strongest. */
    private WeaponClass selected;

    public StatsMenu(CerberusPlugin plugin) {
        super(plugin);
    }

    @Override public String title() { return PlayerTheme.title("Stats & Attributes"); }

    @Override
    protected void content(MenuView v) {
        Player p = v.viewer();
        UUID id = p.getUniqueId();
        AttributeService svc = plugin.getAttributeService();
        AttributeSnapshot s = svc.get(id);
        AttributeRules rules = s.rules();

        var realm = s.realm();
        boolean testRealm = plugin.getPlayerRealms().hasTestRealm(id);
        v.set(MenuView.HEADER, Ui.head(p, ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + p.getName(), Ui.lore()
                .stat("Realm", realm.realm().color() + "R" + realm.realm().number + " " + realm.realm().displayName
                        + ChatColor.GRAY + "  (" + realm.realm().epithet + ")")
                .stat("Realm magnitude", Fmt.mult(realm.multiplier()) + (testRealm ? "  (test realm set by an admin)" : ""))
                .raw(ChatColor.DARK_GRAY + "  Breakthroughs to higher realms are not built yet.")
                .stat("Total skill level", plugin.getSkillManager().hasLoadedSkills(id)
                        ? Fmt.grouped(GUIs.player.SkillView.totalLevel(plugin, id)) : "loading...")
                .gap().text("Your attributes grow by themselves").text("as your skills level up.").build()));

        if (!svc.isTracked(id)) {
            loading(v);
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

        // Row 3: outcomes. The weapon shown: the one in hand if it is a weapon, else the strongest
        // weapon type (the hand usually holds the menu head here); clicking cycles weapon types.
        ItemStack hand = p.getInventory().getItemInMainHand();
        WeaponClass heldClass = WeaponClass.ofMaterial(hand.getType().name());
        boolean holdingWeapon = heldClass != WeaponClass.OTHER && heldClass != WeaponClass.UNARMED;
        WeaponClass wc = selected != null ? selected
                : holdingWeapon ? heldClass : GUIs.player.PlayerHubMenu.bestPowerClass(s);
        Material shown = selected == null && holdingWeapon ? hand.getType() : representative(wc);
        v.button(29, weaponTile(s, wc, shown, selected == null && holdingWeapon, svc.effect(Attribute.POWER)),
                c -> { selected = next(wc, c.type() == org.bukkit.event.inventory.ClickType.RIGHT ? -1 : 1); menus.refresh(c.player()); });
        v.set(22, speedTile(p, s, svc, wc, selected == null && holdingWeapon));
        v.set(31, survivalTile(p, s, svc));
        v.set(33, magicTile(p, s, svc));

        // Row 4: progression
        v.set(38, milestoneTile(svc, id, s, wc));
        v.set(40, killXpTile(p, representative(wc), wc));
        v.button(42, Ui.item(Material.EXPERIENCE_BOTTLE, ChatColor.GOLD + "Skills",
                Ui.lore().text("Levels, XP and what every skill gives.").hint("Click to open").build()),
                c -> menus.open(c.player(), new GUIs.player.SkillsMenu(plugin)));
        v.refreshButton();
    }

    private static WeaponClass next(WeaponClass current, int step) {
        int i = 0;
        for (int k = 0; k < WEAPONS.length; k++) if (WEAPONS[k] == current) i = k;
        return WEAPONS[Math.floorMod(i + step, WEAPONS.length)];
    }

    /** An item of each weapon type, for the icon and for the kill-XP skill lookup (skills.yml). */
    private static Material representative(WeaponClass c) {
        return switch (c) {
            case SWORD -> Material.IRON_SWORD;
            case AXE -> Material.IRON_AXE;
            case MACE -> Material.MACE;
            case TRIDENT -> Material.TRIDENT;
            case SPEAR -> Material.STICK; // icon only; resolved by name below
            case BOW -> Material.BOW;
            case UNARMED, OTHER -> Material.AIR;
        };
    }

    private static ItemStack weaponTile(AttributeSnapshot s, WeaponClass wc, Material held, boolean inHand, AttributeService.Effect effect) {
        Ui.Lore l = Ui.lore().stat(inHand ? "Holding" : "Weapon type", inHand ? Fmt.title(held.name()) : wc.displayName);
        if (inHand) l.stat("Weapon type", wc.displayName);
        double power = s.power(wc);
        l.stat("Power", Fmt.one(power) + "  ->  " + Fmt.mult(s.powerMultiplier(wc)) + " damage")
                .stat("Crits", Fmt.pct(s.critChance()) + " for " + Fmt.mult(s.critMultiplier()))
                .stat("Average per hit", Fmt.mult(s.expectedOffenseMultiplier(wc)))
                .stat("Best possible hit", Fmt.mult(s.offenseMultiplier(wc, true)));
        if (s.realmMultiplier() != 1.0) l.stat("Realm magnitude", "all of that " + Fmt.mult(s.realmMultiplier()));
        if (s.lifesteal() > 0) l.stat("Lifesteal", Fmt.pct(s.lifesteal()));
        int even = (int) Math.min(100, Math.floor(1 + power));
        l.gap().text("Keeps pace with level-" + even + " mobs:")
                .text("they take about as many hits as a")
                .text("level-1 mob did with no Power.");
        if (wc == WeaponClass.OTHER) l.gap().warn("Not a weapon: only all-weapon Power counts.");
        l.gap().raw(AttributeView.effectLabel(effect, Attribute.POWER));
        l.hint("Left/right click: other weapon types");
        Material icon = held == Material.AIR || held == Material.STICK ? Material.NETHERITE_SWORD : held;
        return Ui.item(icon, ChatColor.GOLD + (inHand ? "Your weapon" : "Weapon: " + wc.displayName), l.build());
    }

    /**
     * Speed and handling: attack speed for the weapon type shown (the real attack-speed value when
     * that weapon is in hand, else computed from that item's own vanilla speed), bow draw time,
     * movement speed and spell cooldown, each with its sources and cap.
     */
    private ItemStack speedTile(Player p, AttributeSnapshot s, AttributeService svc, WeaponClass wc, boolean inHand) {
        var sec = plugin.getSecondaryEffects();
        boolean paused = sec != null && sec.paused(p.getUniqueId());
        AttributeRules.Secondary rules = s.rules().secondary();
        Ui.Lore l = Ui.lore();

        double as = s.attackSpeed(wc);
        l.raw(ChatColor.AQUA + "Attack speed" + ChatColor.GRAY + " (" + wc.displayName.toLowerCase(java.util.Locale.ROOT) + "): "
                + ChatColor.WHITE + "+" + Fmt.pct(as));
        l.raw(ChatColor.DARK_GRAY + "  sources " + Fmt.pct(s.attackSpeedRaw(wc)) + ", max " + Fmt.pct(rules.get(Attributes.Stat.ATTACK_SPEED).cap()));
        double perSecond = attacksPerSecond(p, wc, inHand, paused ? 0 : as);
        if (perSecond > 0) {
            l.raw(ChatColor.GRAY + "  " + String.format(java.util.Locale.ROOT, "%.2f attacks/s: a full swing every %.2f s", perSecond, 1 / perSecond)
                    + (inHand ? "" : ChatColor.DARK_GRAY + " (iron)"));
        }
        double ds = s.secondary(Attributes.Stat.DRAW_SPEED);
        l.raw(ChatColor.AQUA + "Bow draw speed: " + ChatColor.WHITE + "+" + Fmt.pct(ds)
                + ChatColor.GRAY + String.format(java.util.Locale.ROOT, "  full power in %.2f s", Attributes.BowDraw.fullDrawSeconds(paused ? 0 : ds)));
        l.raw(ChatColor.AQUA + "Movement speed: " + ChatColor.WHITE + "+" + Fmt.pct(s.secondary(Attributes.Stat.MOVE_SPEED)));
        double cd = s.secondary(Attributes.Stat.COOLDOWN_RECOVERY);
        long fireball = sec == null ? 1000 : sec.cooldownMs(p.getUniqueId(), 1000);
        l.raw(ChatColor.AQUA + "Spell cooldown recovery: " + ChatColor.WHITE + "+" + Fmt.pct(cd)
                + ChatColor.GRAY + String.format(java.util.Locale.ROOT, "  Fireball every %.2f s", fireball / 1000.0));
        l.gap().text("Raised by Agility (Essence for spells)")
                .text("and by the skill behind each action:")
                .text("Blade Mastery, Weapon Mastery, Ranged")
                .text("Precision, " + Skills.SkillNames.display("Navigation") + ", Spell Weaving.");
        l.gap();
        if (paused) l.raw(ChatColor.YELLOW + "⏸ Paused: you are in PvP combat");
        else {
            List<String> off = new java.util.ArrayList<>();
            for (Attributes.Stat st : Attributes.Stat.SECONDARY) if (svc.secondaryEffect(st) != AttributeService.Effect.ACTIVE) off.add(st.displayName.toLowerCase(java.util.Locale.ROOT));
            l.raw(off.isEmpty() ? ChatColor.GREEN + "● Active" : ChatColor.YELLOW + "○ Turned off: " + String.join(", ", off));
            if (!rules.inPvp()) l.raw(ChatColor.DARK_GRAY + "Pauses during PvP combat.");
        }
        l.raw(ChatColor.DARK_GRAY + "Not built yet: crossbow reload, knockback")
                .raw(ChatColor.DARK_GRAY + "resistance, gathering and casting speed.");
        return Ui.item(Material.FEATHER, ChatColor.AQUA + "Speed & handling", l.build());
    }

    /**
     * Attacks per second with {@code bonus}: the live attack-speed attribute when that weapon is in
     * hand (it already includes the bonus), else the player's base speed plus the iron weapon's
     * own vanilla speed modifier, times the bonus. 0 when it doesn't apply (bows).
     */
    private static double attacksPerSecond(Player p, WeaponClass wc, boolean inHand, double bonus) {
        org.bukkit.attribute.AttributeInstance a = p.getAttribute(org.bukkit.attribute.Attribute.ATTACK_SPEED);
        if (a == null || wc == WeaponClass.BOW || wc == WeaponClass.OTHER) return 0;
        if (inHand) return a.getValue();
        Material m = switch (wc) {
            case SWORD -> Material.IRON_SWORD;
            case AXE -> Material.IRON_AXE;
            case MACE -> Material.MACE;
            case TRIDENT -> Material.TRIDENT;
            case SPEAR -> Material.matchMaterial("IRON_SPEAR");
            default -> null;
        };
        double speed = a.getBaseValue();
        if (m != null) {
            for (var mod : m.getDefaultAttributeModifiers(org.bukkit.inventory.EquipmentSlot.HAND).get(org.bukkit.attribute.Attribute.ATTACK_SPEED)) {
                if (mod.getOperation() == org.bukkit.attribute.AttributeModifier.Operation.ADD_NUMBER) speed += mod.getAmount();
            }
        }
        return speed > 0 ? speed * (1 + bonus) : 0;
    }

    private ItemStack survivalTile(Player p, AttributeSnapshot s, AttributeService svc) {
        var vh = plugin.getPlayerVirtualHealthManager();
        var hs = vh.peekSnapshot(p.getUniqueId());
        var bar = plugin.getDefenseBarManager().peekDefenseBar(p.getUniqueId());
        Ui.Lore l = Ui.lore().text("From Vitality:")
                .stat("  Max health", (hs == null ? "?" : num(hs.maxHp())) + "  (" + Fmt.mult(s.maxHpMultiplier()) + ")")
                .raw(AttributeView.effectLabel(svc.effect(Attribute.VITALITY), Attribute.VITALITY))
                .gap().text("From Fortitude (on top of gear and skills):")
                .stat("  Armor bonus", "+" + Fmt.pct(s.armorBonus()))
                .stat("  Defense bar", Fmt.mult(s.defenseBarMultiplier()))
                .stat("  Resistances", "+" + Fmt.pct(s.resist()))
                .raw(AttributeView.effectLabel(svc.effect(Attribute.FORTITUDE), Attribute.FORTITUDE))
                .gap().text("Your totals now:")
                .stat("  Armor reduction", Fmt.pct(vh.getArmorReduction(p)) + " (max " + Fmt.pct(s.rules().limits().armor()) + ")")
                .stat("  Defense bar", bar == null ? "?" : num(bar.getMaximum()) + " points")
                .stat("  Fire / cold / drowning", Fmt.pct(vh.getPlayerFireResistance(p)) + " / "
                        + Fmt.pct(vh.getPlayerColdResistance(p)) + " / " + Fmt.pct(vh.getPlayerDrowningResistance(p)))
                .raw(ChatColor.DARK_GRAY + "  Fire: standing in fire and fire spells;")
                .raw(ChatColor.DARK_GRAY + "  not yet burning, lava or magma.")
                .raw(ChatColor.DARK_GRAY + "  Resistance and armor together block at")
                .raw(ChatColor.DARK_GRAY + "  most " + Fmt.pct(s.rules().limits().totalMitigation()) + " of a hit; the bar is extra.");
        if (!s.rules().switches().pvpDefense()) {
            l.raw(ChatColor.DARK_GRAY + "  PvP: these bonuses don't count yet;")
             .raw(ChatColor.DARK_GRAY + "  players hit you as before.");
        }
        return Ui.item(Material.TOTEM_OF_UNDYING, ChatColor.RED + "Survivability", l.build());
    }

    private ItemStack magicTile(Player p, AttributeSnapshot s, AttributeService svc) {
        double[] mana = plugin.getPlayerManaManager().peekMana(p.getUniqueId());
        Ui.Lore l = Ui.lore().text("From Essence:")
                .stat("  Max mana", (mana == null ? "?" : num(mana[1])) + "  (" + Fmt.mult(s.maxManaMultiplier()) + ")")
                .stat("  Mana regeneration", (mana == null ? "?" : Fmt.one(mana[2]) + " / s") + "  (" + Fmt.mult(s.manaRegenMultiplier()) + ")")
                .stat("  Spell damage vs mobs", Fmt.mult(s.spellDamageMultiplier()))
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
                l.text(Skills.SkillNames.display(weaponSkill) + " " + lvl + " -> " + (lvl + 1) + ":")
                        .raw(ChatColor.WHITE + "  " + wc.singular + " damage " + Fmt.mult(s.powerMultiplier(wc))
                                + ChatColor.GRAY + " -> " + ChatColor.GREEN + Fmt.mult(next.powerMultiplier(wc)))
                        .raw(ChatColor.GRAY + "  At level " + Math.min(Skills.SkillProgression.MAX_LEVEL, lvl + 10) + ": "
                                + ChatColor.GREEN + Fmt.mult(ten.powerMultiplier(wc)));
            } else {
                l.text(Skills.SkillNames.display(weaponSkill) + " is at max level.");
            }
        } else {
            l.text("No skill adds Power to this weapon type yet.");
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

    private ItemStack killXpTile(Player p, Material held, WeaponClass wc) {
        KillXp.Params kx = plugin.getAttributesConfig().killXp();
        var mapper = plugin.getSkillMapper();
        String material = wc == WeaponClass.SPEAR ? "IRON_SPEAR" : held == Material.AIR ? "AIR" : held.name();
        String skill = mapper == null ? "" : mapper.resolveKillSkill(material);
        Ui.Lore l = Ui.lore();
        if (skill.isEmpty()) {
            l.text("Kills with this weapon type train no")
                    .text("skill, except " + Skills.SkillNames.display("Heavy Armor Training") + " while")
                    .text("you wear heavy armor.");
        } else if (!kx.enabled()) {
            l.text("Kill XP v2 is turned off on this server.");
        } else {
            int lvl = plugin.getSkillManager().getSkillLevel(p, skill);
            l.stat("Trains", Skills.SkillNames.display(skill) + " (level " + lvl + ")").text("XP per kill:");
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

    /** Exact below 100,000 ("1,020"), compact above ("12.3M"), so small changes stay visible. */
    private static String num(double v) {
        return v < 100_000 ? Fmt.grouped(Math.round(v)) : Fmt.compact(v);
    }
}

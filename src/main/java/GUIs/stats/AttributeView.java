package GUIs.stats;

import Attributes.Attribute;
import Attributes.AttributeRules;
import Attributes.AttributeService;
import Attributes.AttributeSnapshot;
import Attributes.Contribution;
import Attributes.SkillAttributes;
import Attributes.WeaponClass;
import GUIs.menu.Fmt;
import GUIs.menu.Ui;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * How attributes are shown: shared by the player Stats page and the admin inspector, so both
 * always display the same numbers the same way.
 */
public final class AttributeView {

    private AttributeView() {}

    public static Material icon(Attribute a) {
        return switch (a) {
            case POWER -> Material.IRON_SWORD;
            case AGILITY -> Material.FEATHER;
            case VITALITY -> Material.GOLDEN_APPLE;
            case FORTITUDE -> Material.SHIELD;
            case ESSENCE -> Material.AMETHYST_SHARD;
            case FORTUNE -> Material.EMERALD;
        };
    }

    /** One attribute tile: value, what it does now, top sources, which skills feed it, status. */
    public static ItemStack tile(Attribute a, AttributeSnapshot s, AttributeService.Effect effect) {
        Ui.Lore l = Ui.lore();
        AttributeRules rules = s.rules();
        if (a == Attribute.POWER) {
            boolean any = false;
            for (WeaponClass w : WeaponClass.values()) {
                if (w == WeaponClass.OTHER || s.power(w) <= 0) continue;
                l.raw(ChatColor.WHITE + "  " + w.displayName + ": " + ChatColor.GOLD + Fmt.one(s.power(w))
                        + ChatColor.GRAY + "  ->  " + ChatColor.WHITE + Fmt.mult(s.powerMultiplier(w)) + " damage");
                any = true;
            }
            if (!any) l.stat("Power", "0");
        } else {
            l.stat(a.displayName, Fmt.one(s.get(a)) + ChatColor.DARK_GRAY + "  (max " + (int) rules.cap(a) + ")");
        }
        l.gap();
        for (String line : effects(a, s)) l.raw(line);
        l.gap().text("From:");
        List<Contribution> src = s.sources(a);
        if (src.isEmpty()) l.raw(ChatColor.DARK_GRAY + "  nothing yet");
        for (int i = 0; i < Math.min(4, src.size()); i++) {
            Contribution c = src.get(i);
            l.raw(ChatColor.WHITE + "  " + c.label() + ChatColor.GRAY + ": +" + Fmt.one(c.amount())
                    + (c.weapon() != null ? " (" + c.weapon().displayName.toLowerCase(Locale.ROOT) + ")" : ""));
        }
        if (src.size() > 4) l.raw(ChatColor.DARK_GRAY + "  and " + (src.size() - 4) + " more");
        l.gap();
        for (String line : feeders(a, rules)) l.raw(line);
        l.gap().raw(effectLabel(effect, a));
        return Ui.item(icon(a), a.color + "" + ChatColor.BOLD + a.displayName, l.build());
    }

    /** What the attribute's current value does, in plain outcomes. */
    public static List<String> effects(Attribute a, AttributeSnapshot s) {
        List<String> out = new ArrayList<>();
        String g = ChatColor.GRAY.toString(), w = ChatColor.WHITE.toString();
        switch (a) {
            case POWER -> out.add(g + "Each point: +" + Fmt.pct(s.rules().derived().powerDamage()) + " weapon damage");
            case AGILITY -> {
                out.add(g + "Crit chance: " + w + Fmt.pct(s.critChance()));
                out.add(g + "Crit damage: " + w + Fmt.mult(s.critMultiplier()));
            }
            case VITALITY -> out.add(g + "Max health: " + w + Fmt.mult(s.maxHpMultiplier()));
            case FORTITUDE -> {
                out.add(g + "Armor: " + w + "+" + Fmt.pct(s.armorBonus()));
                out.add(g + "Defense bar: " + w + Fmt.mult(s.defenseBarMultiplier()));
                out.add(g + "Fire / cold / drowning resist: " + w + "+" + Fmt.pct(s.resist()));
            }
            case ESSENCE -> {
                out.add(g + "Max mana: " + w + Fmt.mult(s.maxManaMultiplier()));
                out.add(g + "Mana regeneration: " + w + Fmt.mult(s.manaRegenMultiplier()));
                out.add(g + "Spell damage: " + w + Fmt.mult(s.spellDamageMultiplier()));
            }
            case FORTUNE -> out.add(g + "Will raise luck and magic find.");
        }
        return out;
    }

    /**
     * "Gained from" lines: every skill that feeds {@code a} with its weight, biggest first.
     * One line per skill and weight ("Weapon Mastery +1 (axes, maces, tridents, spears)").
     */
    public static List<String> feeders(Attribute a, AttributeRules rules) {
        record Row(String skill, double perLevel, List<String> weapons) {}
        List<Row> rows = new ArrayList<>();
        for (Map.Entry<String, List<AttributeRules.SkillWeight>> e : rules.skillWeights().entrySet()) {
            Map<Double, List<String>> byWeight = new java.util.LinkedHashMap<>();
            for (AttributeRules.SkillWeight sw : e.getValue()) {
                if (sw.attribute() != a) continue;
                List<String> ws = byWeight.computeIfAbsent(sw.perLevel(), k -> new ArrayList<>());
                if (sw.weapon() != null) ws.add(sw.weapon().displayName.toLowerCase(Locale.ROOT));
            }
            byWeight.forEach((w, ws) -> rows.add(new Row(e.getKey(), w, ws)));
        }
        rows.sort(java.util.Comparator.comparingDouble(Row::perLevel).reversed().thenComparing(Row::skill));
        List<String> out = new ArrayList<>();
        out.add(ChatColor.GRAY + "Gained from (per skill level):");
        for (Row r : rows) {
            out.add(ChatColor.DARK_AQUA + "  " + Skills.SkillNames.display(r.skill()) + ChatColor.GRAY + " +" + trim(r.perLevel())
                    + (r.weapons().isEmpty() ? "" : " (" + String.join(", ", r.weapons()) + ")"));
        }
        if (rows.isEmpty()) out.add(ChatColor.DARK_GRAY + "  no skill feeds it");
        return out;
    }

    public static String effectLabel(AttributeService.Effect e, Attribute a) {
        return switch (e) {
            case ACTIVE -> ChatColor.GREEN + "● Active" + (a == Attribute.POWER || a == Attribute.AGILITY ? " (fighting mobs)" : "");
            case SWITCHED_OFF -> ChatColor.YELLOW + "○ Turned off on this server (attributes.yml)";
            case NOT_YET -> ChatColor.GRAY + "○ Shown only - not used in combat yet";
        };
    }

    /** The skill whose Power applies to {@code w} most strongly (Blade Mastery for swords), or null. */
    public static String weaponSkill(WeaponClass w, AttributeRules rules) {
        String best = null;
        double weight = 0;
        for (Map.Entry<String, List<AttributeRules.SkillWeight>> e : rules.skillWeights().entrySet()) {
            for (AttributeRules.SkillWeight sw : e.getValue()) {
                if (sw.attribute() == Attribute.POWER && sw.weapon() == w && sw.perLevel() > weight) {
                    best = e.getKey();
                    weight = sw.perLevel();
                }
            }
        }
        return best;
    }

    /** {@code current} with {@code skill} at {@code level} instead (for "next level gives..." previews). */
    public static AttributeSnapshot withSkillLevel(AttributeService service, java.util.UUID player, String skill, int level) {
        AttributeRules rules = service.rules();
        String id = SkillAttributes.SOURCE_PREFIX + skill;
        return service.preview(player, cs -> {
            List<Contribution> out = new ArrayList<>();
            for (Contribution c : cs) if (!c.sourceId().equals(id)) out.add(c);
            out.addAll(SkillAttributes.contributions(Map.of(skill, level), rules));
            return out;
        });
    }

    private static String trim(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
    }
}

package Effects;

import Attributes.Attribute;
import Attributes.AttributeSnapshot;
import Attributes.Contribution;
import Attributes.Stat;
import Attributes.WeaponClass;
import Attributes.Yield;
import GUIs.menu.Fmt;
import Manager.Numbers;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleUnaryOperator;
import java.util.function.Predicate;

/**
 * What an effect's attribute-engine contributions actually change (docs/design/ACTIVE_EFFECTS.md §1): the player's
 * snapshot with them against one without them, so caps and diminishing returns are in the numbers. Every derived number
 * the game uses is compared, and each one that moves is listed ("max health 103 → 109", "attack speed +2.0% → +7.0%"),
 * so nothing an attribute does is left out. Pure: the menus pass the two snapshots.
 */
public final class EffectBenefits {

    private EffectBenefits() {}

    /**
     * What the lines need besides the snapshots: which attributes and stats this server applies, whether the player's
     * speed and handling bonuses are paused for PvP right now, Fortune's rule, the health base, the weapon kind damage is
     * shown for, the weapon kind in hand (attack speed counts for it), and the armor rule ({@code armor}: Fortitude's
     * armor bonus → the reduction the player really gets, gear and caps included; null: the bonus as it is).
     */
    public record Context(Predicate<Attribute> attributeOn, Predicate<Stat> statOn, boolean pvpPaused, double fortunePerPoint,
                          double fortuneMax, double baseMaxHealth, WeaponClass weapon, WeaponClass held, DoubleUnaryOperator armor) {}

    /** What an effect adds ({@code added}: "Vitality +6") and every number that moves because of it ({@code changes}). */
    public record Lines(List<String> added, List<String> changes) {
        public boolean isEmpty() { return added.isEmpty() && changes.isEmpty(); }
    }

    /** What {@code cs} add, and what they change: {@code with} them against {@code without} them. */
    public static Lines lines(List<Contribution> cs, AttributeSnapshot with, AttributeSnapshot without, Context ctx) {
        List<String> added = new ArrayList<>();
        for (Contribution c : cs) added.add(added(c, ctx));
        if (cs.isEmpty()) return new Lines(added, List.of());
        List<String> ch = new ArrayList<>();
        double h0 = maxHealth(without, ctx), h1 = maxHealth(with, ctx);
        if (moved(h0, h1)) ch.add("max health " + Numbers.hud(h0) + " → " + Numbers.hud(h1));
        WeaponClass w = ctx.weapon();
        String wn = w.singular.toLowerCase(java.util.Locale.ROOT);
        mult(ch, wn + " damage", without.offenseMultiplier(w, false), with.offenseMultiplier(w, false));
        pct(ch, "crit chance", without.critChance(), with.critChance());
        mult(ch, "crit damage", without.critMultiplier(), with.critMultiplier());
        pct(ch, "lifesteal", without.lifesteal(), with.lifesteal());
        double a0 = ctx.armor() != null ? ctx.armor().applyAsDouble(without.armorBonus()) : without.armorBonus();
        double a1 = ctx.armor() != null ? ctx.armor().applyAsDouble(with.armorBonus()) : with.armorBonus();
        if (moved(a0, a1)) ch.add("armor " + Fmt.pct(a0) + " → " + Fmt.pct(a1) + (ctx.armor() != null ? " (gear and caps included)" : ""));
        mult(ch, "defense bar", without.defenseBarMultiplier(), with.defenseBarMultiplier());
        if (moved(without.resist(), with.resist())) {
            ch.add("fire, cold and drowning resistance " + Fmt.signedPct(without.resist()) + " → " + Fmt.signedPct(with.resist()));
        }
        mult(ch, "max Essence", without.maxManaMultiplier(), with.maxManaMultiplier());
        mult(ch, "Essence regeneration", without.manaRegenMultiplier(), with.manaRegenMultiplier());
        mult(ch, "spell damage", without.spellDamageMultiplier(), with.spellDamageMultiplier());
        for (Stat s : Stat.SECONDARY) {
            // Attack speed counts for the weapon in hand (its skill's part on the same curve), as the game applies it.
            double v0 = s == Stat.ATTACK_SPEED ? without.attackSpeed(ctx.held()) : without.secondary(s);
            double v1 = s == Stat.ATTACK_SPEED ? with.attackSpeed(ctx.held()) : with.secondary(s);
            if (!moved(v0, v1)) continue;
            String what = s == Stat.ATTACK_SPEED ? "attack speed (" + ctx.held().displayName.toLowerCase(java.util.Locale.ROOT) + ")"
                    : s.displayName.toLowerCase(java.util.Locale.ROOT);
            String line = what + " " + (s.isChance() ? Fmt.pct(v0) + " → " + Fmt.pct(v1) : Fmt.signedPct(v0) + " → " + Fmt.signedPct(v1));
            if (!ctx.statOn().test(s)) line += " (switched off)";
            else if (ctx.pvpPaused()) line += " (paused: PvP)";
            ch.add(line);
        }
        for (Yield y : Yield.values()) {
            double c0 = chance(without.fortune(y), ctx), c1 = chance(with.fortune(y), ctx);
            if (moved(c0, c1)) ch.add(noun(y) + " chance " + Fmt.pct(c0) + " → " + Fmt.pct(c1));
        }
        return new Lines(added, ch);
    }

    /** "Vitality +6", "Fortune (crops) +10", "Movement speed +8%", with "switched off" where this server doesn't apply it. */
    static String added(Contribution c, Context ctx) {
        if (c.attribute() != null) {
            String s = c.attribute().displayName + (c.yield() != null ? " (" + c.yield().id + ")" : "") + " " + signed(c.amount());
            return ctx.attributeOn().test(c.attribute()) ? s : s + " · switched off on this server";
        }
        Stat st = c.stat();
        if (st == null) return "?";
        String s = st == Stat.CRIT_MULTIPLIER ? st.displayName + " " + signed(c.amount()) + "x" : st.displayName + " " + signedPctOf(c.amount());
        if (c.weapon() != null) s += " (" + c.weapon().displayName.toLowerCase(java.util.Locale.ROOT) + ")";
        return st.isSecondary() && !ctx.statOn().test(st) ? s + " · switched off on this server" : s;
    }

    private static boolean moved(double a, double b) {
        return Math.abs(a - b) > 1e-9 * Math.max(1.0, Math.abs(a));
    }

    private static void mult(List<String> out, String what, double a, double b) {
        if (moved(a, b)) out.add(what + " " + Fmt.mult(a) + " → " + Fmt.mult(b));
    }

    private static void pct(List<String> out, String what, double a, double b) {
        if (moved(a, b)) out.add(what + " " + Fmt.pct(a) + " → " + Fmt.pct(b));
    }

    private static String noun(Yield y) {
        return switch (y) {
            case ORE -> "extra ore";
            case WOOD -> "extra log";
            case FISH -> "extra fish";
            case CROP -> "extra crop";
            case HERB -> "extra herb";
        };
    }

    /** Max health for a snapshot: the realm's base times Vitality's multiplier (Manager.AttributeEffects.maxHealthFor). */
    private static double maxHealth(AttributeSnapshot s, Context ctx) {
        double mult = ctx.attributeOn().test(Attribute.VITALITY) ? s.maxHpMultiplier() : 1.0;
        return ctx.baseMaxHealth() * s.realmMultiplier() * mult;
    }

    private static double chance(double fortune, Context ctx) {
        return Gathering.GatherRules.bonusChance(fortune, ctx.fortunePerPoint(), ctx.fortuneMax());
    }

    /** +6, +7.2, -3 (no trailing ".0"). */
    static String signed(double v) {
        String n = v == Math.rint(v) ? String.valueOf((long) Math.abs(v)) : String.format(java.util.Locale.ROOT, "%.1f", Math.abs(v));
        return (v < 0 ? "-" : "+") + n;
    }

    /** A fraction as a signed percent without trailing zeros: 0.08 → "+8%", 0.096 → "+9.6%". */
    static String signedPctOf(double fraction) {
        double p = fraction * 100;
        String n = Math.abs(p - Math.rint(p)) < 1e-9 ? String.valueOf((long) Math.abs(Math.rint(p))) : String.format(java.util.Locale.ROOT, "%.1f", Math.abs(p));
        return (fraction < 0 ? "-" : "+") + n + "%";
    }
}

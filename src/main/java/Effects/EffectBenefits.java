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
import java.util.function.Predicate;

/**
 * What an effect's attribute-engine contributions actually change (docs/design/ACTIVE_EFFECTS.md §1): the player's
 * snapshot with them against one without them, so caps and diminishing returns are in the numbers ("Vitality +6 · max
 * health 103 → 109", "Movement speed +8% raw · +7.0% now"). Pure: the menus pass the two snapshots.
 */
public final class EffectBenefits {

    private EffectBenefits() {}

    /**
     * What the lines need besides the snapshots: which attributes and stats this server applies, whether the player's
     * speed and handling bonuses are paused for PvP right now, Fortune's rule, the health base and the weapon kind Power
     * and damage are shown for.
     */
    public record Context(Predicate<Attribute> attributeOn, Predicate<Stat> statOn, boolean pvpPaused, double fortunePerPoint,
                          double fortuneMax, double baseMaxHealth, WeaponClass weapon) {}

    /** One line per contribution in {@code cs}: what it is, then what it changes now. */
    public static List<String> lines(List<Contribution> cs, AttributeSnapshot with, AttributeSnapshot without, Context ctx) {
        List<String> out = new ArrayList<>();
        for (Contribution c : cs) {
            String line = c.attribute() != null ? attribute(c, with, without, ctx) : stat(c, with, without, ctx);
            if (line != null) out.add(line);
        }
        return out;
    }

    private static String attribute(Contribution c, AttributeSnapshot with, AttributeSnapshot without, Context ctx) {
        Attribute a = c.attribute();
        String head = a.displayName + (c.yield() != null ? " (" + c.yield().id + ")" : "") + " " + signed(c.amount());
        if (!ctx.attributeOn().test(a)) return head + " · switched off on this server";
        WeaponClass w = c.weapon() != null ? c.weapon() : ctx.weapon();
        return head + " · " + switch (a) {
            case VITALITY -> "max health " + Numbers.hud(maxHealth(without, ctx)) + " → " + Numbers.hud(maxHealth(with, ctx));
            case FORTITUDE -> "armor " + Fmt.signedPct(without.armorBonus()) + " → " + Fmt.signedPct(with.armorBonus())
                    + ", defense bar " + Fmt.mult(without.defenseBarMultiplier()) + " → " + Fmt.mult(with.defenseBarMultiplier());
            case POWER -> w.displayName.toLowerCase(java.util.Locale.ROOT) + " damage " + Fmt.mult(without.powerMultiplier(w))
                    + " → " + Fmt.mult(with.powerMultiplier(w));
            case AGILITY -> "crit chance " + Fmt.pct(without.critChance()) + " → " + Fmt.pct(with.critChance());
            case ESSENCE -> "max Essence " + Fmt.mult(without.maxManaMultiplier()) + " → " + Fmt.mult(with.maxManaMultiplier())
                    + ", spell damage " + Fmt.mult(without.spellDamageMultiplier()) + " → " + Fmt.mult(with.spellDamageMultiplier());
            case FORTUNE -> fortune(c.yield(), with, without, ctx);
        };
    }

    private static String fortune(Yield y, AttributeSnapshot with, AttributeSnapshot without, Context ctx) {
        if (y == null) {
            return "extra drop chance " + Fmt.pct(chance(without.get(Attribute.FORTUNE), ctx)) + " → "
                    + Fmt.pct(chance(with.get(Attribute.FORTUNE), ctx)) + " (every kind)";
        }
        String what = switch (y) {
            case ORE -> "extra ore";
            case WOOD -> "extra log";
            case FISH -> "extra fish";
            case CROP -> "extra crop";
        };
        return what + " chance " + Fmt.pct(chance(without.fortune(y), ctx)) + " → " + Fmt.pct(chance(with.fortune(y), ctx));
    }

    private static String stat(Contribution c, AttributeSnapshot with, AttributeSnapshot without, Context ctx) {
        Stat s = c.stat();
        if (s == null) return null;
        String raw = s.displayName + " " + signedPctOf(c.amount());
        if (s.isSecondary()) {
            if (!ctx.statOn().test(s)) return raw + " · switched off on this server";
            String now = raw + " raw · " + Fmt.signedPct(with.secondary(s) - without.secondary(s)) + " now";
            return ctx.pvpPaused() ? now + " (paused: PvP)" : now;
        }
        WeaponClass w = c.weapon() != null ? c.weapon() : ctx.weapon();
        return switch (s) {
            case DAMAGE -> raw + " · " + (c.weapon() != null ? w.displayName.toLowerCase(java.util.Locale.ROOT) : "weapon") + " damage bonus "
                    + Fmt.signedPct(without.damageBonus(w)) + " → " + Fmt.signedPct(with.damageBonus(w));
            case CRIT_CHANCE -> raw + " · crit chance " + Fmt.pct(without.critChance()) + " → " + Fmt.pct(with.critChance());
            case CRIT_MULTIPLIER -> s.displayName + " " + signed(c.amount()) + " · crits " + Fmt.mult(without.critMultiplier()) + " → " + Fmt.mult(with.critMultiplier());
            case LIFESTEAL -> raw + " · lifesteal " + Fmt.pct(without.lifesteal()) + " → " + Fmt.pct(with.lifesteal());
            case MAX_HP -> raw + " · max health " + Numbers.hud(maxHealth(without, ctx)) + " → " + Numbers.hud(maxHealth(with, ctx));
            case ARMOR -> raw + " · armor " + Fmt.signedPct(without.armorBonus()) + " → " + Fmt.signedPct(with.armorBonus());
            case DEFENSE_BAR -> raw + " · defense bar " + Fmt.mult(without.defenseBarMultiplier()) + " → " + Fmt.mult(with.defenseBarMultiplier());
            case RESIST -> raw + " · resistances " + Fmt.signedPct(without.resist()) + " → " + Fmt.signedPct(with.resist());
            case MAX_MANA -> raw + " · max Essence " + Fmt.mult(without.maxManaMultiplier()) + " → " + Fmt.mult(with.maxManaMultiplier());
            case MANA_REGEN -> raw + " · Essence regeneration " + Fmt.mult(without.manaRegenMultiplier()) + " → " + Fmt.mult(with.manaRegenMultiplier());
            case SPELL_DAMAGE -> raw + " · spell damage " + Fmt.mult(without.spellDamageMultiplier()) + " → " + Fmt.mult(with.spellDamageMultiplier());
            default -> raw;
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

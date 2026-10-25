package Manager;

/**
 * Pure defense math, unit-tested and used as-is by the damage pipeline
 * ({@code PlayerVirtualHealthManager.applyResolvedDamage}); docs/ATTRIBUTES_ENGINE.md,
 * "How a hit on a player is reduced".
 *
 * <p>A hit on a player passes three layers, in this order:
 * <ol>
 *   <li>resistance to its damage type (fire, cold or drowning);</li>
 *   <li>armor (gear, Heavy Armor Mastery and Fortitude, less the attacking mob's armor penetration);</li>
 *   <li>the defense bar.</li>
 * </ol>
 * The first two are <b>damage reductions</b>: each removes a share of the hit and costs nothing.
 * They stack by multiplying what gets through ({@code 1 - (1 - resist)(1 - armor)}), and together
 * they may remove at most the total-reduction cap (attributes.yml {@code limits.total-mitigation},
 * 75% by design).
 *
 * <p>The defense bar is a different mechanic: a <b>pool</b>. It takes a share of what the
 * reductions let through ({@code max-absorption} x how full it is) and drains by that amount,
 * refilling out of combat. Because it runs out, it is not a reduction and does not count toward
 * the cap.
 */
public final class DefenseMath {

    /** The design's total damage-reduction cap (docs/design/SYSTEMS_BASE_LAYER.md: 6x / 40% / 75%). */
    public static final double DEFAULT_REDUCTION_CAP = 0.75;

    private DefenseMath() {}

    /** Combined reduction of independent layers: {@code 1 - (1 - a)(1 - b)...}, each layer read as 0..1. */
    public static double combined(double... reductions) {
        double through = 1.0;
        for (double r : reductions) through *= 1.0 - clamp01(r);
        return 1.0 - through;
    }

    /** The reduction actually applied: {@code combined} limited to {@code cap} (a broken cap means the default). */
    public static double capped(double combined, double cap) {
        double c = Double.isFinite(cap) ? clamp01(cap) : DEFAULT_REDUCTION_CAP;
        return Math.min(clamp01(combined), c);
    }

    /**
     * Share of a hit the defense bar takes: {@code maxAbsorption x current / max}. The bar then
     * drains by what it took (never below zero).
     */
    public static double barRate(double current, double max, double maxAbsorption) {
        if (!(max > 0) || !(current > 0)) return 0.0;
        return clamp01(maxAbsorption) * Math.min(1.0, current / max);
    }

    /** {@code real / old}: how much bigger a pool is than before attributes (1 when unknown). */
    public static double shareScale(double real, double old) {
        double s = real / old;
        return Double.isFinite(s) && s > 0 ? s : 1.0;
    }

    /**
     * One hit through all three layers: what the damage pipeline applies. {@code barScale} and
     * {@code healthScale} are 1, except for PvP as before attributes: the hit is then measured on
     * the defender's old bar and health, so the real bar drains {@code barScale} x what the old one
     * would have, and real health takes {@code healthScale} x the old damage (the same shares).
     */
    public static Hit resolve(double damage, double resist, double armor, double cap,
                              double barCurrent, double barMax, double maxAbsorption,
                              double barScale, double healthScale) {
        double d = Double.isFinite(damage) ? Math.max(0.0, damage) : 0.0;
        double afterResist = d * (1.0 - clamp01(resist));
        double afterArmor = afterResist * (1.0 - clamp01(armor));
        double uncapped = combined(resist, armor);
        double reduction = capped(uncapped, cap);
        double afterReductions = reduction < uncapped ? d * (1.0 - reduction) : afterArmor;
        double rate = barRate(barCurrent, barMax, maxAbsorption);
        double taken = afterReductions * rate;                       // the hit's part the bar takes
        double drain = taken * shareScale(barScale, 1.0);             // bar points it loses for that
        double afterBar = afterReductions - taken;
        return new Hit(afterResist, afterArmor, uncapped, reduction, afterReductions, rate, drain,
                afterBar, afterBar * shareScale(healthScale, 1.0), Math.max(0.0, barCurrent - drain));
    }

    /** One hit through all three layers (no PvP scaling). */
    public static Hit hit(double damage, double resist, double armor, double cap,
                          double barCurrent, double barMax, double maxAbsorption) {
        return resolve(damage, resist, armor, cap, barCurrent, barMax, maxAbsorption, 1.0, 1.0);
    }

    /**
     * A PvP hit as before Attributes 2.0 (attributes.yml {@code integration.pvp-defense: false}):
     * worked out with the defender's pre-attribute resistance, armor, bar size and health, then
     * taken from their real bar and health as the same share. A defender whose attributes tripled
     * their health loses the same third of it per hit as before, so PvP plays exactly as it did.
     */
    public static Hit pvpAsBefore(double damage, double oldResist, double oldArmor, double cap,
                                  double barCurrent, double barMax, double oldBarMax, double maxAbsorption,
                                  double maxHp, double oldMaxHp) {
        return resolve(damage, oldResist, oldArmor, cap, barCurrent, barMax, maxAbsorption,
                shareScale(barMax, oldBarMax), shareScale(maxHp, oldMaxHp));
    }

    /**
     * @param afterResist       damage after resistance
     * @param afterArmor        after resistance and armor, before the cap
     * @param uncappedReduction what resistance and armor would remove together
     * @param reduction         what they do remove (after the cap)
     * @param afterReductions   damage that reaches the defense bar
     * @param barRate           share of that the bar takes
     * @param absorbed          bar points the bar loses
     * @param afterBar          damage left after the bar (in the hit's own terms)
     * @param toHealth          damage to health
     * @param barAfter          the bar's points after the hit
     */
    public record Hit(double afterResist, double afterArmor, double uncappedReduction, double reduction,
                      double afterReductions, double barRate, double absorbed, double afterBar,
                      double toHealth, double barAfter) {}

    private static double clamp01(double v) {
        return Double.isFinite(v) ? Math.max(0.0, Math.min(1.0, v)) : 0.0;
    }
}

package Manager;

import java.util.logging.Logger;

/**
 * Log-Sigmoid hybrid scaling engine.
 * <p>
 * Formula: {@code base + logScale * log1p(level) + lateBonus * sigmoid(level)}
 * <br>
 * Sigmoid: {@code 1 / (1 + exp(-steepness * (level - inflection)))}
 * <p>
 * Output is always clamped to {@code [0, hardCap]}. Mathematically safe:
 * no infinity, no NaN, no overflow — exponent is capped before exp().
 */
public class ScalingFormula {
    private static final Logger log = Logger.getLogger("Cerberus");

    /** Maximum magnitude for the sigmoid exponent (prevents exp overflow). */
    private static final double MAX_EXP_MAGNITUDE = 500.0;

    /**
     * Immutable parameter set for a single scaled stat.
     *
     * @param base       starting value at level 0
     * @param logScale   controls early/mid progression strength
     * @param steepness  how sharp the late-game surge is (0.05 = gradual, 0.15 = sharp)
     * @param inflection level where the power surge kicks in (e.g. 60)
     * @param lateBonus  maximum additional bonus from the sigmoid component
     * @param hardCap    absolute maximum value, never exceeded
     */
    public record ScalingParams(
            double base,
            double logScale,
            double steepness,
            double inflection,
            double lateBonus,
            double hardCap
    ) {
        /** Create a copy with only the specified overrides applied. */
        public ScalingParams withOverrides(
                Double baseOvr, Double logScaleOvr, Double steepnessOvr,
                Double inflectionOvr, Double lateBonusOvr, Double hardCapOvr
        ) {
            return new ScalingParams(
                    baseOvr != null ? baseOvr : base,
                    logScaleOvr != null ? logScaleOvr : logScale,
                    steepnessOvr != null ? steepnessOvr : steepness,
                    inflectionOvr != null ? inflectionOvr : inflection,
                    lateBonusOvr != null ? lateBonusOvr : lateBonus,
                    hardCapOvr != null ? hardCapOvr : hardCap
            );
        }
    }

    /**
     * Built-in presets for common scaling patterns.
     * Each preset provides sensible defaults for a category of stats.
     */
    public enum Preset {
        /** Steady growth, moderate cap. Good for: damage bonuses, defense. */
        STEADY(0.02, 0.04, 0.08, 60, 0.40, 1.5),
        /** Strong late surge, higher cap. Good for: damage multiplier, crit multiplier. */
        LATE_SURGE(0.02, 0.04, 0.10, 60, 0.80, 3.0),
        /** Careful growth, strict cap. Good for: crit chance, lifesteal, KB resist. */
        HARD_CAPPED(0.05, 0.03, 0.08, 60, 0.30, 0.75),
        /** Minimal growth, very strict cap. Good for: disarm chance, flat defensive stats. */
        FLAT_DEFENSIVE(0.01, 0.02, 0.06, 70, 0.15, 0.50);

        public final ScalingParams defaults;

        Preset(double base, double logScale, double steepness,
               double inflection, double lateBonus, double hardCap) {
            this.defaults = new ScalingParams(base, logScale, steepness,
                    inflection, lateBonus, hardCap);
        }
    }

    /**
     * Compute a scaled value for the given level.
     * <p>
     * Always safe: clamps level >= 0, caps exponent to prevent overflow,
     * clamps output to [0, hardCap]. If hardCap < base, logs a warning
     * and returns base.
     *
     * @param level  skill level (0+)
     * @param params scaling parameters
     * @return scaled value in [0, hardCap]
     */
    public static double compute(int level, ScalingParams params) {
        // Safety: validate params
        if (params.hardCap() < params.base()) {
            log.warning("[ScalingFormula] hardCap (" + params.hardCap()
                    + ") < base (" + params.base() + ") — returning base.");
            return params.base();
        }

        int safeLevel = Math.max(0, level);

        // Log component: strong early growth, diminishing returns
        double logPart = params.logScale() * Math.log1p(safeLevel);

        // Sigmoid component: late-game power surge
        double exponent = -params.steepness() * (safeLevel - params.inflection());
        // Clamp exponent to prevent exp() overflow
        exponent = Math.max(-MAX_EXP_MAGNITUDE, Math.min(MAX_EXP_MAGNITUDE, exponent));
        double sigmoid = 1.0 / (1.0 + Math.exp(exponent));

        double value = params.base() + logPart + (params.lateBonus() * sigmoid);

        // Clamp to [0, hardCap]
        return Math.max(0, Math.min(value, params.hardCap()));
    }

    /**
     * Generate a preview table showing scaled values at specific levels.
     *
     * @param label  display name (e.g. "critical-strike / crit-chance")
     * @param params scaling parameters
     * @param levels levels to preview (e.g. 1, 5, 10, 25, 50, 75, 100)
     * @return formatted multi-line string
     */
    public static String preview(String label, ScalingParams params, int... levels) {
        // Find which preset matches (if any) for display
        String presetName = "custom";
        for (Preset p : Preset.values()) {
            if (p.defaults.equals(params)) {
                presetName = p.name();
                break;
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("\u00a7e[Scaling] ").append(label);
        sb.append(" \u00a77(preset: ").append(presetName).append(")\n");

        for (int i = 0; i < levels.length; i++) {
            int lvl = levels[i];
            double val = compute(lvl, params);
            boolean atCap = val >= params.hardCap() - 0.0001;

            sb.append(String.format("  \u00a7fLv%3d: \u00a7a%.4f", lvl, val));
            if (atCap) sb.append(" \u00a7c(cap)");

            if (i < levels.length - 1) {
                // 3 entries per line
                if ((i + 1) % 3 == 0) {
                    sb.append("\n");
                } else {
                    sb.append("  \u00a77|  ");
                }
            }
        }
        return sb.toString();
    }

    /**
     * Preview all scaling params at standard levels (1, 5, 10, 25, 50, 75, 100).
     */
    public static String preview(String label, ScalingParams params) {
        return preview(label, params, 1, 5, 10, 25, 50, 75, 100);
    }

    private ScalingFormula() {} // Utility class
}

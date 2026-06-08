package Manager;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.EnumMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Computes a damage multiplier for melee mob-to-player damage based on
 * the mob's {@link Tier} and level.
 * <p>
 * Formula:
 * <pre>
 *   levelFactor     = 1 + levelScale * log1p(level / levelPivot)
 *   rawMultiplier   = tierBase * levelFactor
 *   finalMultiplier = min(rawMultiplier, maxDamageMultiplier)
 * </pre>
 * All constants are loaded from the {@code mob-damage-scaling} section of combat.yml.
 */
public final class MobDamageScaling {

    private static final Logger log = Logger.getLogger("Cerberus");

    private boolean enabled;
    private double levelScale;
    private double levelPivot;
    private double maxDamageMultiplier;
    private final Map<Tier, Double> tierBases = new EnumMap<>(Tier.class);

    // ── Hardcoded defaults ───────────────────────────────────────

    private static final double DEFAULT_LEVEL_SCALE = 0.55;
    private static final double DEFAULT_LEVEL_PIVOT = 25.0;
    private static final double DEFAULT_MAX_MULTIPLIER = 10.0;

    private static final Map<Tier, Double> DEFAULT_TIER_BASES = Map.ofEntries(
            Map.entry(Tier.WEAK,       0.85),
            Map.entry(Tier.COMMON,     1.0),
            Map.entry(Tier.UNCOMMON,   1.15),
            Map.entry(Tier.RARE,       1.35),
            Map.entry(Tier.EPIC,       1.75),
            Map.entry(Tier.LEGENDARY,  2.25),
            Map.entry(Tier.PERFECT,    2.5),
            Map.entry(Tier.MYTHIC,     3.0),
            Map.entry(Tier.GODLY,      3.75),
            Map.entry(Tier.PRIMORDIAL, 4.5)
    );

    // ── Construction + loading ────────────────────────────────────

    public MobDamageScaling() {
        applyDefaults();
    }

    private void applyDefaults() {
        this.enabled = true;
        this.levelScale = DEFAULT_LEVEL_SCALE;
        this.levelPivot = DEFAULT_LEVEL_PIVOT;
        this.maxDamageMultiplier = DEFAULT_MAX_MULTIPLIER;
        this.tierBases.clear();
        this.tierBases.putAll(DEFAULT_TIER_BASES);
    }

    /**
     * Load config from the {@code mob-damage-scaling} section of combat.yml.
     * Call on plugin enable and on {@code /cerb reload}.
     */
    public void loadConfig(FileConfiguration config) {
        ConfigurationSection section = config.getConfigurationSection("mob-damage-scaling");
        if (section == null) {
            log.info("[MobDamageScaling] No config section found — using defaults.");
            applyDefaults();
            return;
        }

        this.enabled = section.getBoolean("enabled", true);

        // levelScale
        double ls = section.getDouble("level-scale", DEFAULT_LEVEL_SCALE);
        if (ls <= 0) {
            log.warning("[MobDamageScaling] level-scale must be > 0, got " + ls + " — falling back to " + DEFAULT_LEVEL_SCALE);
            ls = DEFAULT_LEVEL_SCALE;
        }
        this.levelScale = ls;

        // levelPivot
        double lp = section.getDouble("level-pivot", DEFAULT_LEVEL_PIVOT);
        if (lp <= 0) {
            log.warning("[MobDamageScaling] level-pivot must be > 0, got " + lp + " — falling back to " + DEFAULT_LEVEL_PIVOT);
            lp = DEFAULT_LEVEL_PIVOT;
        }
        this.levelPivot = lp;

        // maxDamageMultiplier
        double max = section.getDouble("max-damage-multiplier", DEFAULT_MAX_MULTIPLIER);
        if (max < 1.0) {
            log.warning("[MobDamageScaling] max-damage-multiplier must be >= 1.0, got " + max + " — falling back to " + DEFAULT_MAX_MULTIPLIER);
            max = DEFAULT_MAX_MULTIPLIER;
        }
        this.maxDamageMultiplier = max;

        // Tier bases
        tierBases.clear();
        ConfigurationSection tierSection = section.getConfigurationSection("tier-base");
        double prevBase = -1.0;
        for (Tier tier : Tier.values()) {
            double base;
            if (tierSection != null && tierSection.contains(tier.name())) {
                base = tierSection.getDouble(tier.name());
            } else {
                base = DEFAULT_TIER_BASES.getOrDefault(tier, 1.0);
            }
            if (base < 1.0 && tier != Tier.WEAK) {
                log.warning("[MobDamageScaling] tier-base for " + tier.name() + " is " + base + " (< 1.0)");
            }
            if (base < prevBase) {
                log.warning("[MobDamageScaling] tier-base for " + tier.name() + " (" + base
                        + ") is less than previous tier (" + prevBase + ") — ordering is not monotonic");
            }
            tierBases.put(tier, base);
            prevBase = base;
        }

        log.info("[MobDamageScaling] Loaded — enabled=" + enabled + " levelScale=" + levelScale
                + " levelPivot=" + levelPivot + " maxMultiplier=" + maxDamageMultiplier
                + " tiers=" + tierBases.size());
    }

    // ── Core calculation ─────────────────────────────────────────

    /**
     * Calculate the damage multiplier for a mob with the given tier and level.
     *
     * @param tier  the mob's tier (null → COMMON)
     * @param level the mob's level (clamped to [1, 2000])
     * @return the final damage multiplier (>= 1.0, <= maxDamageMultiplier)
     */
    public double calculate(Tier tier, int level) {
        if (!enabled) return 1.0;

        if (tier == null) tier = Tier.COMMON;
        level = Math.max(1, Math.min(2000, level));

        double tierBase = tierBases.getOrDefault(tier, 1.0);
        double levelFactor = 1.0 + levelScale * Math.log1p(level / levelPivot);
        double rawMultiplier = tierBase * levelFactor;
        return Math.min(rawMultiplier, maxDamageMultiplier);
    }

    public boolean isEnabled() {
        return enabled;
    }
}

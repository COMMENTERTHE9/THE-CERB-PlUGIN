package Manager;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.EnumMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Computes a mob's scaled maximum health from its {@link Tier} (realm) and level,
 * and derives a mob's level from how far it spawned from world spawn.
 *
 * <p>Level (distance in blocks from world spawn, horizontal):
 * <pre>
 *   level = clamp( 1 + floor(distance / blocksPerLevel), 1, maxLevel )
 * </pre>
 * Health (controlled-hyperscaling realm curve — see docs/design/POWER_SCALING.md):
 * <pre>
 *   levelMult = 1 + levelSlope * (level - 1)
 *   maxHealth = clamp( baseHealth * tierMultiplier * levelMult, 1, healthCap )
 * </pre>
 * The tier multipliers are a geometric ×8/realm staircase (COMMON=1 … PRIMORDIAL=8^8),
 * so HP climbs from tens (Mortal) to billions (Primordial). The result can far exceed
 * the vanilla {@code MAX_HEALTH} attribute cap (1024) — callers store it in a
 * {@link MobVirtualHealthManager} double pool, not the attribute. {@code healthCap} is a
 * per-realm sanity rail (well under a double's 2^53 exact-integer limit), not a balance cap.
 */
public final class MobHealthScaling {

    private static final Logger log = Logger.getLogger("Cerberus");

    private boolean enabled;
    private double baseHealth;
    private double levelSlope;
    private double healthCap;
    private double blocksPerLevel;
    private int maxLevel;
    private String applyTo;            // MONSTERS | ALL
    private boolean showLevelInName;
    private boolean nameAlwaysVisible;
    private final Map<Tier, Double> tierMultipliers = new EnumMap<>(Tier.class);

    // ── Hardcoded defaults ───────────────────────────────────────

    private static final boolean DEFAULT_ENABLED = true;
    private static final double DEFAULT_BASE_HEALTH = 50.0;     // HP anchor: a COMMON L1 mob
    private static final double DEFAULT_LEVEL_SLOPE = 0.05;     // ×1.0 at L1 .. ×5.95 at L100
    private static final double DEFAULT_HEALTH_CAP = 1.0e14;    // sanity rail only (< 2^53)
    private static final double DEFAULT_BLOCKS_PER_LEVEL = 150.0;
    private static final int DEFAULT_MAX_LEVEL = 100;
    private static final String DEFAULT_APPLY_TO = "ALL";

    // Geometric ×8 per realm: 8^realmIndex (WEAK=8^-1 .. PRIMORDIAL=8^8).
    private static final Map<Tier, Double> DEFAULT_TIER_MULT = Map.ofEntries(
            Map.entry(Tier.WEAK,       0.125),
            Map.entry(Tier.COMMON,     1.0),
            Map.entry(Tier.UNCOMMON,   8.0),
            Map.entry(Tier.RARE,       64.0),
            Map.entry(Tier.EPIC,       512.0),
            Map.entry(Tier.LEGENDARY,  4096.0),
            Map.entry(Tier.PERFECT,    32768.0),
            Map.entry(Tier.MYTHIC,     262144.0),
            Map.entry(Tier.GODLY,      2097152.0),
            Map.entry(Tier.PRIMORDIAL, 16777216.0)
    );

    // ── Construction + loading ────────────────────────────────────

    public MobHealthScaling() {
        applyDefaults();
    }

    private void applyDefaults() {
        this.enabled = DEFAULT_ENABLED;
        this.baseHealth = DEFAULT_BASE_HEALTH;
        this.levelSlope = DEFAULT_LEVEL_SLOPE;
        this.healthCap = DEFAULT_HEALTH_CAP;
        this.blocksPerLevel = DEFAULT_BLOCKS_PER_LEVEL;
        this.maxLevel = DEFAULT_MAX_LEVEL;
        this.applyTo = DEFAULT_APPLY_TO;
        this.showLevelInName = true;
        this.nameAlwaysVisible = true;
        this.tierMultipliers.clear();
        this.tierMultipliers.putAll(DEFAULT_TIER_MULT);
    }

    /**
     * Load config from the {@code mob-health-scaling} section of combat.yml.
     * Call on plugin enable and on {@code /cerb reload}.
     */
    public void loadConfig(FileConfiguration config) {
        ConfigurationSection section = config.getConfigurationSection("mob-health-scaling");
        if (section == null) {
            log.info("[MobHealthScaling] No config section found — using defaults.");
            applyDefaults();
            return;
        }

        this.enabled = section.getBoolean("enabled", DEFAULT_ENABLED);

        double bh = section.getDouble("base-health", DEFAULT_BASE_HEALTH);
        if (bh < 1.0) {
            log.warning("[MobHealthScaling] base-health must be >= 1.0, got " + bh + " — falling back to " + DEFAULT_BASE_HEALTH);
            bh = DEFAULT_BASE_HEALTH;
        }
        this.baseHealth = bh;

        double slope = section.getDouble("level-slope", DEFAULT_LEVEL_SLOPE);
        if (slope < 0.0) {
            log.warning("[MobHealthScaling] level-slope must be >= 0, got " + slope + " — falling back to " + DEFAULT_LEVEL_SLOPE);
            slope = DEFAULT_LEVEL_SLOPE;
        }
        this.levelSlope = slope;

        double cap = section.getDouble("health-cap", DEFAULT_HEALTH_CAP);
        if (cap < 1.0) {
            log.warning("[MobHealthScaling] health-cap must be >= 1.0, got " + cap + " — falling back to " + DEFAULT_HEALTH_CAP);
            cap = DEFAULT_HEALTH_CAP;
        }
        this.healthCap = cap;

        double bpl = section.getDouble("blocks-per-level", DEFAULT_BLOCKS_PER_LEVEL);
        if (bpl <= 0.0) {
            log.warning("[MobHealthScaling] blocks-per-level must be > 0, got " + bpl + " — falling back to " + DEFAULT_BLOCKS_PER_LEVEL);
            bpl = DEFAULT_BLOCKS_PER_LEVEL;
        }
        this.blocksPerLevel = bpl;

        int ml = section.getInt("max-level", DEFAULT_MAX_LEVEL);
        if (ml < 1) {
            log.warning("[MobHealthScaling] max-level must be >= 1, got " + ml + " — falling back to " + DEFAULT_MAX_LEVEL);
            ml = DEFAULT_MAX_LEVEL;
        }
        this.maxLevel = ml;

        this.applyTo = section.getString("apply-to", DEFAULT_APPLY_TO).trim().toUpperCase();
        if (!applyTo.equals("MONSTERS") && !applyTo.equals("ALL")) {
            log.warning("[MobHealthScaling] apply-to must be MONSTERS or ALL, got '" + applyTo + "' — using " + DEFAULT_APPLY_TO);
            this.applyTo = DEFAULT_APPLY_TO;
        }
        this.showLevelInName = section.getBoolean("show-level-in-name", true);
        this.nameAlwaysVisible = section.getBoolean("name-always-visible", true);

        // Tier multipliers (geometric realm staircase)
        tierMultipliers.clear();
        ConfigurationSection tierSection = section.getConfigurationSection("tier-multiplier");
        double prev = -1.0;
        for (Tier tier : Tier.values()) {
            double mult;
            if (tierSection != null && tierSection.contains(tier.name())) {
                mult = tierSection.getDouble(tier.name());
            } else {
                mult = DEFAULT_TIER_MULT.getOrDefault(tier, 1.0);
            }
            if (mult <= 0) {
                log.warning("[MobHealthScaling] tier-multiplier for " + tier.name() + " is " + mult + " (<= 0) — using 1.0");
                mult = 1.0;
            }
            if (mult < prev) {
                log.warning("[MobHealthScaling] tier-multiplier for " + tier.name() + " (" + mult
                        + ") is less than previous tier (" + prev + ") — ordering is not monotonic");
            }
            tierMultipliers.put(tier, mult);
            prev = mult;
        }

        log.info("[MobHealthScaling] Loaded — enabled=" + enabled + " baseHealth=" + baseHealth
                + " levelSlope=" + levelSlope + " cap=" + healthCap + " blocksPerLevel=" + blocksPerLevel
                + " maxLevel=" + maxLevel + " applyTo=" + applyTo + " tiers=" + tierMultipliers.size());
    }

    // ── Core calculation ─────────────────────────────────────────

    /** Derive a mob level from its horizontal distance (blocks) to world spawn. */
    public int levelForDistance(double distance) {
        if (distance <= 0 || Double.isNaN(distance)) return 1;
        int level = 1 + (int) Math.floor(distance / blocksPerLevel);
        return Math.max(1, Math.min(maxLevel, level));
    }

    /** Compute scaled max health for a mob of the given tier (realm) and level. */
    public double calculate(Tier tier, int level) {
        if (tier == null) tier = Tier.COMMON;
        level = Math.max(1, Math.min(maxLevel, level));
        double tierMult = tierMultipliers.getOrDefault(tier, 1.0);
        double levelMult = 1.0 + levelSlope * (level - 1);
        double raw = baseHealth * tierMult * levelMult;
        return Math.max(1.0, Math.min(raw, healthCap));
    }

    /**
     * Realm magnitude relative to Awakened (COMMON = ×1): the realm curve (tier-multiplier,
     * ×8 per realm) that mob health uses, shared with players so same-realm fights stay even.
     */
    public double realmMultiplier(Tier tier) {
        double common = tierMultipliers.getOrDefault(Tier.COMMON, 1.0);
        double m = tierMultipliers.getOrDefault(tier == null ? Tier.COMMON : tier, 1.0);
        double r = common > 0 ? m / common : m;
        return Double.isFinite(r) && r > 0 ? r : 1.0;
    }

    public boolean isEnabled() { return enabled; }
    public int getMaxLevel() { return maxLevel; }
    public String getApplyTo() { return applyTo; }
    public boolean isShowLevelInName() { return showLevelInName; }
    public boolean isNameAlwaysVisible() { return nameAlwaysVisible; }
}

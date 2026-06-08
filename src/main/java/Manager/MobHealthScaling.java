package Manager;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.EnumMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Computes a mob's scaled maximum health from its {@link Tier} and level,
 * and derives a mob's level from how far it spawned from world spawn.
 *
 * <p>Level (distance in blocks from world spawn, horizontal):
 * <pre>
 *   level = clamp( 1 + floor(distance / blocksPerLevel), 1, maxLevel )
 * </pre>
 * Health:
 * <pre>
 *   maxHealth = clamp( (baseHealth + level * hpPerLevel) * tierMultiplier, 1, healthCap )
 * </pre>
 * All constants load from the {@code mob-health-scaling} section of combat.yml.
 * Mirrors {@link MobDamageScaling} so both halves of mob scaling tune the same way.
 */
public final class MobHealthScaling {

    private static final Logger log = Logger.getLogger("Cerberus");

    private boolean enabled;
    private double baseHealth;
    private double hpPerLevel;
    private double healthCap;
    private double blocksPerLevel;
    private int maxLevel;
    private String applyTo;            // MONSTERS | ALL
    private boolean showLevelInName;
    private boolean nameAlwaysVisible;
    private final Map<Tier, Double> tierMultipliers = new EnumMap<>(Tier.class);

    // ── Hardcoded defaults ───────────────────────────────────────

    private static final boolean DEFAULT_ENABLED = true;
    private static final double DEFAULT_BASE_HEALTH = 20.0;
    private static final double DEFAULT_HP_PER_LEVEL = 6.0;
    private static final double DEFAULT_HEALTH_CAP = 5000.0;
    private static final double DEFAULT_BLOCKS_PER_LEVEL = 150.0;
    private static final int DEFAULT_MAX_LEVEL = 100;
    private static final String DEFAULT_APPLY_TO = "MONSTERS";

    private static final Map<Tier, Double> DEFAULT_TIER_MULT = Map.ofEntries(
            Map.entry(Tier.WEAK,       0.5),
            Map.entry(Tier.COMMON,     1.0),
            Map.entry(Tier.UNCOMMON,   1.5),
            Map.entry(Tier.RARE,       3.0),
            Map.entry(Tier.EPIC,       6.0),
            Map.entry(Tier.LEGENDARY,  10.0),
            Map.entry(Tier.PERFECT,    16.0),
            Map.entry(Tier.MYTHIC,     25.0),
            Map.entry(Tier.GODLY,      40.0),
            Map.entry(Tier.PRIMORDIAL, 60.0)
    );

    // ── Construction + loading ────────────────────────────────────

    public MobHealthScaling() {
        applyDefaults();
    }

    private void applyDefaults() {
        this.enabled = DEFAULT_ENABLED;
        this.baseHealth = DEFAULT_BASE_HEALTH;
        this.hpPerLevel = DEFAULT_HP_PER_LEVEL;
        this.healthCap = DEFAULT_HEALTH_CAP;
        this.blocksPerLevel = DEFAULT_BLOCKS_PER_LEVEL;
        this.maxLevel = DEFAULT_MAX_LEVEL;
        this.applyTo = DEFAULT_APPLY_TO;
        this.showLevelInName = true;
        this.nameAlwaysVisible = false;
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

        double hpl = section.getDouble("hp-per-level", DEFAULT_HP_PER_LEVEL);
        if (hpl < 0.0) {
            log.warning("[MobHealthScaling] hp-per-level must be >= 0, got " + hpl + " — falling back to " + DEFAULT_HP_PER_LEVEL);
            hpl = DEFAULT_HP_PER_LEVEL;
        }
        this.hpPerLevel = hpl;

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
        this.nameAlwaysVisible = section.getBoolean("name-always-visible", false);

        // Tier multipliers
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
                + " hpPerLevel=" + hpPerLevel + " cap=" + healthCap + " blocksPerLevel=" + blocksPerLevel
                + " maxLevel=" + maxLevel + " applyTo=" + applyTo + " tiers=" + tierMultipliers.size());
    }

    // ── Core calculation ─────────────────────────────────────────

    /** Derive a mob level from its horizontal distance (blocks) to world spawn. */
    public int levelForDistance(double distance) {
        if (distance <= 0 || Double.isNaN(distance)) return 1;
        int level = 1 + (int) Math.floor(distance / blocksPerLevel);
        return Math.max(1, Math.min(maxLevel, level));
    }

    /** Compute scaled max health for a mob of the given tier and level. */
    public double calculate(Tier tier, int level) {
        if (tier == null) tier = Tier.COMMON;
        level = Math.max(1, Math.min(maxLevel, level));
        double tierMult = tierMultipliers.getOrDefault(tier, 1.0);
        double raw = (baseHealth + (level - 1) * hpPerLevel) * tierMult;
        return Math.max(1.0, Math.min(raw, healthCap));
    }

    public boolean isEnabled() { return enabled; }
    public int getMaxLevel() { return maxLevel; }
    public String getApplyTo() { return applyTo; }
    public boolean isShowLevelInName() { return showLevelInName; }
    public boolean isNameAlwaysVisible() { return nameAlwaysVisible; }
}

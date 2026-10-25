package Manager;

import Manager.ScalingFormula.Preset;
import Manager.ScalingFormula.ScalingParams;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Single source of truth for all combat tuning values.
 * Reads from {@code combat.yml} (bundled as a default resource on first run).
 * <p>
 * Every key has a hardcoded default fallback — missing keys auto-default safely.
 * Invalid values (negative where positive required) log a warning and fall back.
 * A {@code config-version} key at the top tracks schema version for future migrations.
 * <p>
 * Skill stats now use {@link ScalingFormula} with per-stat {@link ScalingParams}.
 */
public class CombatConfig {
    private static final Logger log = Logger.getLogger("Cerberus");

    private final JavaPlugin plugin;
    private final File combatFile;
    private FileConfiguration config;

    // ── Knockback ────────────────────────────────────────────────
    public double kbBaseStrength;
    public double kbDamageScale;
    public double kbSprintBonus;
    public double kbVerticalLift;
    public double kbMaxHorizontal;
    public double kbMaxVertical;
    public double kbMomentumBlend;
    public double kbProjectileBase;
    public double kbProjectileDamageScale;
    public double kbCooldown;

    // ── Armor (3-layer defense system) ─────────────────────────
    public double armorLeather, armorChainmail, armorGolden, armorIron, armorDiamond, armorNetherite;
    public double armorMaxReduction;

    // ── Defense Bar ──────────────────────────────────────────────
    public double defBarBaseMaxPoints;
    public double defBarMaxAbsorption;
    public double defBarRegenRate;
    public long defBarRegenDelayMs;

    // ── Health Regen ─────────────────────────────────────────────
    public double healthRegenBaseRate;
    public double healthRegenCombatRate;
    public long healthRegenCombatTimeoutMs;
    public long damageInvincibilityMs;
    public long respawnInvincibilityMs;

    // ── Mob Damage Scaling ──────────────────────────────────────
    public final MobDamageScaling mobDamageScaling = new MobDamageScaling();

    // ── Mob Health Scaling ──────────────────────────────────────
    public final MobHealthScaling mobHealthScaling = new MobHealthScaling();

    // ── Display ──────────────────────────────────────────────────
    public int displayComboTimeoutMs;
    public int displayDamageNumberTicks;
    public int displayCritDamageNumberTicks;
    public int displayComboThreshold;
    public boolean displayMobDamageNumbers;

    // ── Sound cooldowns (ms) ─────────────────────────────────────
    public int soundCooldownMelee;
    public int soundCooldownProjectile;
    public int soundCooldownContact;
    public int soundCooldownEnvironmental;
    public int soundCooldownStatusEffect;
    public int soundCooldownDefault;

    // ── Scaling defaults (fallback when neither preset nor explicit value is set) ──
    private double defaultLogScale;
    private double defaultSteepness;
    private double defaultInflection;

    // ── Skill Scaling Params ─────────────────────────────────────
    public ScalingParams critChanceScaling;
    public ScalingParams critMultiplierScaling;
    public ScalingParams wmDamageScaling;
    public ScalingParams wmLifestealScaling;
    public ScalingParams bmDamageScaling;
    public ScalingParams bmSpeedScaling;
    public ScalingParams meKbResistScaling;
    public ScalingParams meDisarmScaling;
    public ScalingParams hatKbReductionScaling;
    public ScalingParams hatArmorBonusScaling;
    public ScalingParams hatDefenseBarBonusScaling;

    // ── Non-scaling skill values ─────────────────────────────────
    public int meDisarmDurationTicks;

    /**
     * All named scaling params for iteration (preview command, debug).
     * Populated during reload(). Keys match "skill / stat" naming.
     */
    private final Map<String, ScalingParams> allScalingParams = new LinkedHashMap<>();

    public CombatConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        this.combatFile = new File(plugin.getDataFolder(), "combat.yml");
        reload();
    }

    /**
     * (Re)load all values from combat.yml. Safe to call at any time
     * (e.g. from {@code /cerb reload}).
     */
    public void reload() {
        if (!combatFile.exists()) {
            plugin.saveResource("combat.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(combatFile);

        // ── Config migration: add missing sections from bundled defaults ──
        migrateConfig();

        allScalingParams.clear();

        int version = config.getInt("config-version", 1);
        log.info("[CombatConfig] Loading combat.yml (version " + version + ")");

        // ── Knockback ────────────────────────────────────────────
        kbBaseStrength          = pos("knockback.base-strength",          0.4);
        kbDamageScale           = pos("knockback.damage-scale",           0.06);
        kbSprintBonus           = pos("knockback.sprint-bonus",           0.3);
        kbVerticalLift          = pos("knockback.vertical-lift",          0.35);
        kbMaxHorizontal         = pos("knockback.max-horizontal",         1.5);
        kbMaxVertical           = pos("knockback.max-vertical",           0.5);
        kbMomentumBlend         = pos("knockback.momentum-blend",         0.3);
        kbProjectileBase        = pos("knockback.projectile-base",        0.3);
        kbProjectileDamageScale = pos("knockback.projectile-damage-scale",0.02);
        kbCooldown              = pos("knockback.cooldown",              0.5);

        // ── Armor (3-layer system) ───────────────────────────────
        armorLeather    = config.getDouble("armor.reduction-per-piece.leather",   0.02);
        armorChainmail  = config.getDouble("armor.reduction-per-piece.chainmail", 0.03);
        armorGolden     = config.getDouble("armor.reduction-per-piece.gold",      0.02);
        armorIron       = config.getDouble("armor.reduction-per-piece.iron",      0.04);
        armorDiamond    = config.getDouble("armor.reduction-per-piece.diamond",   0.05);
        armorNetherite  = config.getDouble("armor.reduction-per-piece.netherite", 0.06);
        armorMaxReduction = pos("armor.max-reduction", 0.40);

        // ── Defense Bar ──────────────────────────────────────────
        defBarBaseMaxPoints  = pos("defense-bar.base-max-points", 300.0);
        defBarMaxAbsorption  = pos("defense-bar.max-absorption",  0.70);
        defBarRegenRate      = pos("defense-bar.regen-rate",      15.0);
        defBarRegenDelayMs   = config.getLong("defense-bar.regen-delay-ms", 7000);

        // ── Health Regen ─────────────────────────────────────────
        healthRegenBaseRate       = pos("health-regen.base-rate",          2.0);
        healthRegenCombatRate     = config.getDouble("health-regen.combat-rate", 0.0);
        healthRegenCombatTimeoutMs= config.getLong("health-regen.combat-timeout-ms", 8000);
        damageInvincibilityMs     = nonNegativeLong("damage-timing.invincibility-ms", 250);
        respawnInvincibilityMs    = nonNegativeLong("damage-timing.respawn-invincibility-ms", 3000);

        // ── Mob damage scaling ───────────────────────────────────
        mobDamageScaling.loadConfig(config);

        // ── Mob health scaling ───────────────────────────────────
        mobHealthScaling.loadConfig(config);

        // ── Legacy config detection ──────────────────────────────
        if (config.contains("defense.base-amount") || config.contains("defense.recovery-rate")) {
            log.warning("[CombatConfig] Detected legacy defense config. Old toughness/resistance/percent-reduction "
                    + "fields are no longer used. See combat.yml for the new 3-layer system (armor + defense-bar).");
        }

        // ── Display ──────────────────────────────────────────────
        displayComboTimeoutMs       = config.getInt("display.combo-timeout-ms",       5000);
        displayDamageNumberTicks    = config.getInt("display.damage-number-ticks",    20);
        displayCritDamageNumberTicks= config.getInt("display.crit-damage-number-ticks",30);
        displayComboThreshold       = config.getInt("display.combo-threshold",        2);
        displayMobDamageNumbers     = config.getBoolean("display.mob-damage-numbers", true);

        // ── Sound cooldowns ──────────────────────────────────────
        soundCooldownMelee        = config.getInt("sound.hit-cooldown-ms.melee",         200);
        soundCooldownProjectile   = config.getInt("sound.hit-cooldown-ms.projectile",    200);
        soundCooldownContact      = config.getInt("sound.hit-cooldown-ms.contact",       500);
        soundCooldownEnvironmental= config.getInt("sound.hit-cooldown-ms.environmental", 500);
        soundCooldownStatusEffect = config.getInt("sound.hit-cooldown-ms.status-effect", 800);
        soundCooldownDefault      = config.getInt("sound.hit-cooldown-ms.default",       400);

        // ── Scaling defaults ─────────────────────────────────────
        defaultLogScale   = config.getDouble("skills.scaling-defaults.log-scale",           0.04);
        defaultSteepness  = config.getDouble("skills.scaling-defaults.late-game-steepness", 0.08);
        defaultInflection = config.getDouble("skills.scaling-defaults.late-game-start",     60);

        // ── Skill scaling params ─────────────────────────────────
        critChanceScaling     = loadScaling("skills.critical-strike.crit-chance",     Preset.HARD_CAPPED);
        critMultiplierScaling = loadScaling("skills.critical-strike.crit-multiplier", Preset.LATE_SURGE);
        wmDamageScaling       = loadScaling("skills.weapon-mastery.damage-multiplier",Preset.LATE_SURGE);
        wmLifestealScaling    = loadScaling("skills.weapon-mastery.lifesteal",        Preset.HARD_CAPPED);
        bmDamageScaling       = loadScaling("skills.blade-mastery.damage-bonus",      Preset.STEADY);
        bmSpeedScaling        = loadScaling("skills.blade-mastery.speed-bonus",       Preset.FLAT_DEFENSIVE);
        meKbResistScaling     = loadScaling("skills.martial-expertise.kb-resist",     Preset.FLAT_DEFENSIVE);
        meDisarmScaling       = loadScaling("skills.martial-expertise.disarm-chance",  Preset.FLAT_DEFENSIVE);
        hatKbReductionScaling    = loadScaling("skills.heavy-armor-training.kb-reduction",      Preset.FLAT_DEFENSIVE);
        hatArmorBonusScaling     = loadScaling("skills.heavy-armor-training.armor-bonus",       Preset.FLAT_DEFENSIVE);
        hatDefenseBarBonusScaling= loadScaling("skills.heavy-armor-training.defense-bar-bonus", Preset.STEADY);

        // ── Non-scaling values ───────────────────────────────────
        meDisarmDurationTicks = config.getInt("skills.martial-expertise.disarm-duration-ticks", 100);

        log.info("[CombatConfig] Loaded " + allScalingParams.size() + " scaling params + combat values.");
    }

    /**
     * Load a ScalingParams from a config section path.
     * <ol>
     *   <li>Read {@code preset:} key, fall back to {@code defaultPreset}</li>
     *   <li>Start from preset defaults</li>
     *   <li>Override any explicitly set fields</li>
     *   <li>Apply scaling-defaults for logScale/steepness/inflection if not set by preset or explicit</li>
     *   <li>Validate: hardCap >= base, steepness > 0</li>
     * </ol>
     *
     * @param path          config path (e.g. "skills.critical-strike.crit-chance")
     * @param defaultPreset fallback preset if section missing or no preset key
     * @return validated ScalingParams
     */
    private ScalingParams loadScaling(String path, Preset defaultPreset) {
        ScalingParams result = scalingFrom(config.getConfigurationSection(path), defaultPreset, path);

        // Register for preview iteration
        // Extract a clean name from path: "skills.critical-strike.crit-chance" -> "critical-strike / crit-chance"
        String cleanName = path.replace("skills.", "");
        int lastDot = cleanName.lastIndexOf('.');
        if (lastDot > 0) {
            cleanName = cleanName.substring(0, lastDot) + " / " + cleanName.substring(lastDot + 1);
        }
        allScalingParams.put(cleanName, result);

        return result;
    }

    /**
     * One scaling curve from its combat.yml section (preset + explicit overrides, validated).
     * Static so tests can read the shipped combat.yml exactly as the server does.
     */
    static ScalingParams scalingFrom(ConfigurationSection section, Preset defaultPreset, String path) {
        ScalingParams result;
        if (section == null) {
            // No config section at all — use full preset defaults
            result = defaultPreset.defaults;
        } else {
            // Resolve preset
            String presetName = section.getString("preset", defaultPreset.name());
            Preset preset;
            try {
                preset = Preset.valueOf(presetName.toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warning("[CombatConfig] Unknown preset '" + presetName + "' at " + path
                        + " — falling back to " + defaultPreset.name());
                preset = defaultPreset;
            }

            // Start from preset, override explicit values
            ScalingParams base = preset.defaults;
            result = base.withOverrides(
                    section.contains("base")             ? section.getDouble("base")             : null,
                    section.contains("log-scale")        ? section.getDouble("log-scale")        : null,
                    section.contains("late-game-steepness") ? section.getDouble("late-game-steepness") : null,
                    section.contains("late-game-start")  ? section.getDouble("late-game-start")  : null,
                    section.contains("late-game-bonus")  ? section.getDouble("late-game-bonus")  : null,
                    section.contains("hard-cap")         ? section.getDouble("hard-cap")         : null
            );
        }

        // Validation
        if (result.hardCap() < result.base()) {
            log.warning("[CombatConfig] hardCap < base at " + path
                    + " (hardCap=" + result.hardCap() + ", base=" + result.base()
                    + ") — clamping hardCap to base.");
            result = new ScalingParams(result.base(), result.logScale(), result.steepness(),
                    result.inflection(), result.lateBonus(), result.base());
        }
        if (result.steepness() <= 0) {
            log.warning("[CombatConfig] steepness <= 0 at " + path + " — using default 0.08");
            result = new ScalingParams(result.base(), result.logScale(), 0.08,
                    result.inflection(), result.lateBonus(), result.hardCap());
        }
        return result;
    }

    /** Read a double, warn and fallback if negative (where positive expected). */
    private double pos(String path, double fallback) {
        double value = config.getDouble(path, fallback);
        if (value < 0) {
            log.warning("[CombatConfig] Invalid negative value for '" + path + "': " + value + " — using default " + fallback);
            return fallback;
        }
        return value;
    }

    private long nonNegativeLong(String path, long fallback) {
        long value = config.getLong(path, fallback);
        if (value < 0) {
            log.warning("[CombatConfig] Invalid negative value for '" + path + "': " + value + " â€” using default " + fallback);
            return fallback;
        }
        return value;
    }

    /**
     * Get all registered scaling params (name -> params) for preview/debug.
     * Keys are "skill / stat" format (e.g. "critical-strike / crit-chance").
     */
    public Map<String, ScalingParams> getAllScalingParams() {
        return Map.copyOf(allScalingParams);
    }

    /**
     * Get armor damage reduction for a single armor piece by material type.
     * Returns 0.0 for non-armor materials.
     */
    public double getArmorReductionForPiece(Material mat) {
        return switch (mat) {
            case LEATHER_HELMET, LEATHER_CHESTPLATE, LEATHER_LEGGINGS, LEATHER_BOOTS -> armorLeather;
            case CHAINMAIL_HELMET, CHAINMAIL_CHESTPLATE, CHAINMAIL_LEGGINGS, CHAINMAIL_BOOTS -> armorChainmail;
            case GOLDEN_HELMET, GOLDEN_CHESTPLATE, GOLDEN_LEGGINGS, GOLDEN_BOOTS -> armorGolden;
            case IRON_HELMET, IRON_CHESTPLATE, IRON_LEGGINGS, IRON_BOOTS -> armorIron;
            case DIAMOND_HELMET, DIAMOND_CHESTPLATE, DIAMOND_LEGGINGS, DIAMOND_BOOTS -> armorDiamond;
            case NETHERITE_HELMET, NETHERITE_CHESTPLATE, NETHERITE_LEGGINGS, NETHERITE_BOOTS -> armorNetherite;
            default -> 0.0;
        };
    }

    /**
     * Get the hit-sound cooldown in milliseconds for the given damage type.
     * Maps CustomDamageType to a sound category (melee, projectile, contact,
     * environmental, status-effect) and returns the configured cooldown.
     */
    public int getSoundCooldownMs(CustomDamageType type) {
        return switch (type) {
            // Melee combat
            case ENTITY_ATTACK, ENTITY_SWEEP_ATTACK, THORNS, SONIC_BOOM,
                 ARCANE_BLAST, PYROMANCY, CRYOMANCY, ELECTROMANCY,
                 DARK_ARTS, GEOMANCY, AEROMANCY, MYSTIC_BINDING,
                 MANA_FEEDBACK, MANA_EXPLOSION,
                 PHYSICAL_TRAP, MAGICAL_TRAP, ELEMENTAL_TRAP -> soundCooldownMelee;

            // Projectile
            case PROJECTILE -> soundCooldownProjectile;

            // Contact damage (mobs, blocks)
            case CONTACT, HOT_FLOOR, CAMPFIRE, CRAMMING,
                 ENTITY_EXPLOSION, BLOCK_EXPLOSION, CUSTOM_EXPLOSION -> soundCooldownContact;

            // Environmental
            case FIRE, FIRE_TICK, LAVA, DROWNING, SUFFOCATION, FALL,
                 FREEZE, VOID, FLY_INTO_WALL, WORLD_BORDER, LIGHTNING,
                 FALLING_BLOCK, DRAGON_BREATH, DRYOUT, MELTING,
                 CUSTOM_ENVIRONMENTAL -> soundCooldownEnvironmental;

            // Status effects / DOTs
            case POISON, MAGIC, WITHER, STARVATION, CUSTOM_MAGIC,
                 BURN_DOT, FREEZE_DOT, POISON_DOT, BLEED_DOT,
                 MANA_BURN, MANA_DRAIN, MANA_VOID, MANA_CORRUPTION -> soundCooldownStatusEffect;

            default -> soundCooldownDefault;
        };
    }

    /** Access the raw config if needed for custom reads. */
    public FileConfiguration getRawConfig() {
        return config;
    }

    // ── Config migration ─────────────────────────────────────────

    /**
     * Checks for top-level sections present in the bundled default combat.yml
     * (from the JAR) but missing from the on-disk combat.yml. Missing sections
     * are copied from defaults and the file is re-saved.
     * <p>
     * This ensures new config sections added in code updates reach existing
     * server installations without requiring manual config edits.
     * <p>
     * Existing sections are NEVER overwritten — only missing ones are added.
     */
    private void migrateConfig() {
        java.io.InputStream defaultStream = plugin.getResource("combat.yml");
        if (defaultStream == null) {
            return; // no bundled default — nothing to migrate from
        }

        YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                new java.io.InputStreamReader(defaultStream, java.nio.charset.StandardCharsets.UTF_8));

        boolean changed = false;
        for (String key : defaults.getKeys(false)) {
            if (!config.contains(key)) {
                config.set(key, defaults.get(key));
                log.info("[CombatConfig] Added missing config section: " + key);
                changed = true;
            }
        }

        if (changed) {
            try {
                config.save(combatFile);
                log.info("[CombatConfig] Saved migrated combat.yml to disk.");
            } catch (java.io.IOException e) {
                log.severe("[CombatConfig] Failed to save migrated combat.yml: " + e.getMessage());
            }
        }
    }
}

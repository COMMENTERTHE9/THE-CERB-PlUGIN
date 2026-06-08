package Manager;

import cerberus.world.cerb.CerberusPlugin;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.util.*;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Config-driven mob registry loaded from mobs.yml.
 * Provides O(1) entity resolution via indexed lookups,
 * priority-based matching, and atomic reload with fallback.
 */
public class MobRegistry {

    private final CerberusPlugin plugin;
    private final Logger log;
    private final NamespacedKey mobIdKey;

    // Primary store: mobId → entry
    private volatile Map<String, MobEntry> entriesById = Map.of();

    // Secondary indexes (rebuilt on every load/reload)
    private volatile Map<String, MobEntry> byCustomNameExact = Map.of();
    private volatile Map<EntityType, MobEntry> byEntityType = Map.of();
    private volatile List<MobEntry> patternEntries = List.of();

    // Default XP multipliers per tier (overridden by combat.yml tiers section)
    private volatile Map<Tier, Double> tierXpDefaults = new HashMap<>();

    private volatile boolean debugLogging = false;

    // ----------------------------------------------------------------
    // Inner class: parsed config entry
    // ----------------------------------------------------------------
    public static class MobEntry {
        final String mobId;
        final Tier classification;
        final double xpMultiplier;
        final EntityType entityType;          // nullable
        final String customNameExact;         // nullable
        final Pattern customNamePattern;      // nullable
        final boolean matchesPdc;
        final boolean matchAll;               // true = AND logic, false = OR logic

        MobEntry(String mobId, Tier classification, double xpMultiplier,
                 EntityType entityType, String customNameExact, Pattern customNamePattern,
                 boolean matchesPdc, boolean matchAll) {
            this.mobId = mobId;
            this.classification = classification;
            this.xpMultiplier = xpMultiplier;
            this.entityType = entityType;
            this.customNameExact = customNameExact;
            this.customNamePattern = customNamePattern;
            this.matchesPdc = matchesPdc;
            this.matchAll = matchAll;
        }

        public String getMobId() { return mobId; }
        public Tier getTier() { return classification; }
        public double getXpMultiplier() { return xpMultiplier; }
        public EntityType getEntityType() { return entityType; }
        public String getCustomNameExact() { return customNameExact; }

        /** Build the MobContext for this entry with the given match reason. */
        MobContext toContext(MatchReason reason) {
            return new MobContext(mobId, classification, xpMultiplier, reason); // classification IS a Tier now
        }

        /** Check if entity satisfies ALL match rules (AND mode). */
        boolean matchesAll(LivingEntity entity) {
            boolean hasAnyRule = false;

            if (entityType != null) {
                hasAnyRule = true;
                if (entity.getType() != entityType) return false;
            }
            if (customNameExact != null) {
                hasAnyRule = true;
                if (!customNameExact.equals(entity.getCustomName())) return false;
            }
            if (customNamePattern != null) {
                hasAnyRule = true;
                String name = entity.getCustomName();
                if (name == null || !customNamePattern.matcher(name).matches()) return false;
            }
            return hasAnyRule; // must have at least one rule
        }
    }

    // ----------------------------------------------------------------
    // Constructor — loads config immediately
    // ----------------------------------------------------------------
    public MobRegistry(CerberusPlugin plugin) {
        this.plugin = plugin;
        this.log = plugin.getLogger();
        this.mobIdKey = new NamespacedKey(plugin, "cerb_mob_id");
        reload();
    }

    // ----------------------------------------------------------------
    // Resolution: the single lookup method
    // ----------------------------------------------------------------
    public MobContext resolve(LivingEntity entity) {
        MobContext result = resolveInternal(entity);

        // Fire event for non-NORMAL mobs
        if (result.tier() != Tier.COMMON) {
            Bukkit.getPluginManager().callEvent(
                    new CerberusMobIdentifiedEvent(entity, result));
        }

        if (debugLogging) {
            log.info("[Cerb-MobRegistry] Resolved " + entity.getType()
                    + " \"" + entity.getCustomName() + "\" -> "
                    + result.mobId() + " (via " + result.matchReason() + ")");
        }

        return result;
    }

    private MobContext resolveInternal(LivingEntity entity) {
        // 1. PDC tag — highest priority, O(1)
        String pdcId = entity.getPersistentDataContainer()
                .get(mobIdKey, PersistentDataType.STRING);
        if (pdcId != null) {
            MobEntry entry = entriesById.get(pdcId);
            if (entry != null) {
                return entry.toContext(new MatchReason(MatchReason.Type.PDC_TAG, pdcId));
            }
            // PDC tag present but no matching entry — warn once, fall through
            if (debugLogging) {
                log.warning("[Cerb-MobRegistry] PDC tag '" + pdcId + "' has no matching entry");
            }
        }

        // 2. Custom name exact — O(1)
        String customName = entity.getCustomName();
        if (customName != null) {
            MobEntry exactMatch = byCustomNameExact.get(customName);
            if (exactMatch != null) {
                // If match-all mode, verify ALL rules pass
                if (exactMatch.matchAll) {
                    if (exactMatch.matchesAll(entity)) {
                        return exactMatch.toContext(
                                new MatchReason(MatchReason.Type.CUSTOM_NAME_EXACT, customName));
                    }
                    // AND mode failed — don't return, fall through
                } else {
                    return exactMatch.toContext(
                            new MatchReason(MatchReason.Type.CUSTOM_NAME_EXACT, customName));
                }
            }

            // 3. Custom name pattern — iterate small list
            for (MobEntry entry : patternEntries) {
                if (entry.customNamePattern.matcher(customName).matches()) {
                    if (entry.matchAll) {
                        if (entry.matchesAll(entity)) {
                            return entry.toContext(
                                    new MatchReason(MatchReason.Type.CUSTOM_NAME_PATTERN,
                                            entry.customNamePattern.pattern()));
                        }
                        // AND failed, try next pattern entry
                    } else {
                        return entry.toContext(
                                new MatchReason(MatchReason.Type.CUSTOM_NAME_PATTERN,
                                        entry.customNamePattern.pattern()));
                    }
                }
            }
        }

        // 4. Entity type — O(1)
        MobEntry typeMatch = byEntityType.get(entity.getType());
        if (typeMatch != null) {
            if (typeMatch.matchAll) {
                if (typeMatch.matchesAll(entity)) {
                    return typeMatch.toContext(
                            new MatchReason(MatchReason.Type.ENTITY_TYPE,
                                    entity.getType().name()));
                }
            } else {
                return typeMatch.toContext(
                        new MatchReason(MatchReason.Type.ENTITY_TYPE,
                                entity.getType().name()));
            }
        }

        // 5. Default
        return MobContext.NORMAL;
    }

    // ----------------------------------------------------------------
    // PDC tagging for plugin-spawned mobs
    // ----------------------------------------------------------------
    public void tagEntity(LivingEntity entity, String mobId) {
        entity.getPersistentDataContainer()
                .set(mobIdKey, PersistentDataType.STRING, mobId);
    }

    // ----------------------------------------------------------------
    // Direct lookup by mob ID
    // ----------------------------------------------------------------
    public MobEntry getEntry(String mobId) {
        return entriesById.get(mobId);
    }

    /** Returns all registered mob IDs (for tab completion). */
    public Set<String> getEntryIds() {
        return entriesById.keySet();
    }

    /** Returns the NamespacedKey used for PDC mob ID tagging. */
    public NamespacedKey getMobIdKey() {
        return mobIdKey;
    }

    // ----------------------------------------------------------------
    // Reload: atomic swap with fallback on failure
    // ----------------------------------------------------------------
    public void reload() {
        File file = new File(plugin.getDataFolder(), "mobs.yml");
        if (!file.exists()) {
            log.info("[Cerb-MobRegistry] mobs.yml not found, no mob entries loaded.");
            return;
        }

        try {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            parseConfig(yaml);
        } catch (Exception e) {
            log.severe("[Cerb-MobRegistry] Failed to reload mobs.yml — keeping previous config.");
            e.printStackTrace();
        }
    }

    private void parseConfig(YamlConfiguration yaml) {
        // --- Tier XP defaults (from Tier enum, overridable by config) ---
        Map<Tier, Double> newDefaults = new EnumMap<>(Tier.class);
        for (Tier t : Tier.values()) {
            newDefaults.put(t, t.xpMultiplier);
        }

        ConfigurationSection defaultsSection = yaml.getConfigurationSection("classification-defaults");
        if (defaultsSection != null) {
            for (String key : defaultsSection.getKeys(false)) {
                Tier tier = Tier.fromLegacyClassification(key);
                newDefaults.put(tier, defaultsSection.getDouble(key));
            }
        }

        // --- Debug flag ---
        boolean newDebug = yaml.getBoolean("debug", false);

        // --- Parse mobs section ---
        ConfigurationSection mobsSection = yaml.getConfigurationSection("mobs");
        if (mobsSection == null) {
            log.info("[Cerb-MobRegistry] No 'mobs' section found, loaded 0 entries.");
            // Atomic swap to empty
            this.entriesById = Map.of();
            this.byCustomNameExact = Map.of();
            this.byEntityType = Map.of();
            this.patternEntries = List.of();
            this.tierXpDefaults = newDefaults;
            this.debugLogging = newDebug;
            return;
        }

        Map<String, MobEntry> newEntriesById = new LinkedHashMap<>();
        Map<String, MobEntry> newByCustomName = new HashMap<>();
        Map<EntityType, MobEntry> newByEntityType = new EnumMap<>(EntityType.class);
        List<MobEntry> newPatternEntries = new ArrayList<>();

        int bossCount = 0, rareCount = 0;

        for (String mobId : mobsSection.getKeys(false)) {
            ConfigurationSection entry = mobsSection.getConfigurationSection(mobId);
            if (entry == null) {
                log.warning("[Cerb-MobRegistry] Entry '" + mobId + "' is not a section, skipping.");
                continue;
            }

            // Classification (supports legacy NORMAL/BOSS names via fromLegacyClassification)
            String classStr = entry.getString("classification", "COMMON");
            Tier classification = Tier.fromString(classStr);
            if (classification == Tier.COMMON && !classStr.equalsIgnoreCase("COMMON")) {
                // Try legacy mapping
                classification = Tier.fromLegacyClassification(classStr);
            }

            // XP multiplier: use entry override, or classification default
            double xpMultiplier;
            if (entry.contains("xp-multiplier")) {
                xpMultiplier = entry.getDouble("xp-multiplier");
            } else {
                xpMultiplier = newDefaults.getOrDefault(classification, classification.xpMultiplier);
            }

            // Match mode
            String matchModeStr = entry.getString("match-mode", "ANY").toUpperCase();
            boolean matchAll = matchModeStr.equals("ALL");

            // Match rules
            ConfigurationSection matchSection = entry.getConfigurationSection("match");
            if (matchSection == null) {
                log.warning("[Cerb-MobRegistry] Mob '" + mobId + "' has no 'match' section, skipping.");
                continue;
            }

            EntityType entityType = null;
            String customNameExact = null;
            Pattern customNamePattern = null;
            boolean matchesPdc = matchSection.getBoolean("pdc-tag", false);

            // entity-type
            String entityTypeStr = matchSection.getString("entity-type");
            if (entityTypeStr != null) {
                try {
                    entityType = EntityType.valueOf(entityTypeStr.toUpperCase());
                } catch (IllegalArgumentException e) {
                    log.warning("[Cerb-MobRegistry] Unknown entity type '" + entityTypeStr
                            + "' for mob '" + mobId + "', skipping that match rule.");
                }
            }

            // custom-name-exact
            customNameExact = matchSection.getString("custom-name-exact");

            // custom-name-pattern
            String patternStr = matchSection.getString("custom-name-pattern");
            if (patternStr != null) {
                try {
                    customNamePattern = Pattern.compile(patternStr);
                } catch (PatternSyntaxException e) {
                    log.warning("[Cerb-MobRegistry] Invalid regex '" + patternStr
                            + "' for mob '" + mobId + "', skipping that match rule.");
                }
            }

            // Must have at least one valid match rule
            if (entityType == null && customNameExact == null
                    && customNamePattern == null && !matchesPdc) {
                log.warning("[Cerb-MobRegistry] Mob '" + mobId
                        + "' has no valid match rules after validation, skipping.");
                continue;
            }

            MobEntry mobEntry = new MobEntry(mobId, classification, xpMultiplier,
                    entityType, customNameExact, customNamePattern, matchesPdc, matchAll);

            newEntriesById.put(mobId, mobEntry);

            // Build secondary indexes + conflict detection
            if (customNameExact != null) {
                MobEntry existing = newByCustomName.put(customNameExact, mobEntry);
                if (existing != null) {
                    log.warning("[Cerb-MobRegistry] Custom name conflict: '"
                            + customNameExact + "' matches both '"
                            + existing.mobId + "' and '" + mobId + "'. Last one wins.");
                }
            }

            if (entityType != null && !matchAll) {
                // Only index entity-type for ANY-mode entries.
                // ALL-mode entries are checked differently (need full matchesAll).
                MobEntry existing = newByEntityType.put(entityType, mobEntry);
                if (existing != null) {
                    log.warning("[Cerb-MobRegistry] Entity type conflict: "
                            + entityType + " matches both '"
                            + existing.mobId + "' and '" + mobId + "'. Last one wins.");
                }
            }

            if (customNamePattern != null) {
                newPatternEntries.add(mobEntry);
            }

            if (classification.isAtLeast(Tier.EPIC)) bossCount++;
            else if (classification.isAtLeast(Tier.RARE)) rareCount++;
        }

        // Atomic swap — all or nothing
        this.entriesById = Collections.unmodifiableMap(newEntriesById);
        this.byCustomNameExact = Collections.unmodifiableMap(newByCustomName);
        this.byEntityType = Collections.unmodifiableMap(newByEntityType);
        this.patternEntries = Collections.unmodifiableList(newPatternEntries);
        this.tierXpDefaults = Collections.unmodifiableMap(newDefaults);
        this.debugLogging = newDebug;

        log.info("[Cerb-MobRegistry] Loaded " + newEntriesById.size()
                + " mob entries (" + bossCount + " bosses, " + rareCount + " rares)");
    }
}

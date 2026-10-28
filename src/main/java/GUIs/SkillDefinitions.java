package GUIs;

import Manager.ChunkDiscoveryManager;
import Manager.CombatConfig;
import Manager.CombatSkillProcessor;
import Manager.PlayerDefenseManager;
import Skills.SkillNames;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * The skill catalog: what each skill is called, what it is, how it is trained and what it does,
 * in one place. Read by the Player Menu skill pages, the Stats page and the admin panel.
 *
 * <p>Honesty rules: training lines describe XP sources that exist in the code (amounts come from
 * the constants the gameplay code uses); every effect carries a status, and an effect's numbers
 * come from the gameplay formula itself ({@link EffectValue}), never from a copy here. Kill XP
 * routing is described live from skills.yml by the skill pages, so it is not repeated here.
 * Attribute points and speed bonuses (attack, draw, movement, cooldown) come from attributes.yml
 * and are shown from the attribute engine, so they are not listed as effects here either.
 *
 * <p>Skills keep their internal names (database rows, config keys, commands); the name players
 * read comes from {@link SkillNames}. skills.yml {@code skill-gui.<skill>} can override the icon,
 * description, category, display name and training text, or add a skill.
 */
public final class SkillDefinitions {

    private static final Logger log = Logger.getLogger("Cerberus");

    // ── Skill Category ───────────────────────────────────────────

    public enum SkillCategory {
        COMBAT(ChatColor.RED, Material.RED_STAINED_GLASS_PANE, "Combat Skills", Material.DIAMOND_SWORD),
        MAGIC(ChatColor.LIGHT_PURPLE, Material.PURPLE_STAINED_GLASS_PANE, "Magic Skills", Material.ENCHANTED_BOOK),
        UTILITY(ChatColor.GREEN, Material.GREEN_STAINED_GLASS_PANE, "Utility Skills", Material.DIAMOND_PICKAXE);

        public final ChatColor color;
        public final Material accentGlass;
        public final String displayTitle;
        public final Material icon;

        SkillCategory(ChatColor color, Material accentGlass, String displayTitle, Material icon) {
            this.color = color;
            this.accentGlass = accentGlass;
            this.displayTitle = displayTitle;
            this.icon = icon;
        }
    }

    // ── Effects ──────────────────────────────────────────────────

    /** Whether a skill effect works in the game today. Word + symbol, never colour alone. */
    public enum EffectStatus {
        LIVE(ChatColor.GREEN + "● Live"),
        PVP_ONLY(ChatColor.YELLOW + "◐ Against players only"),
        NOT_ACTIVE(ChatColor.GRAY + "○ Not active yet");

        public final String label;

        EffectStatus(String label) { this.label = label; }
    }

    /** How an effect value reads: +12.0%, x1.85, +340 points (realm-scaled points grow with the realm). */
    public enum Unit { PERCENT, MULTIPLIER, REALM_POINTS, LOGS, COUNT }

    /** The size of an effect at a skill level, computed by the gameplay code's own function. */
    @FunctionalInterface
    public interface EffectValue {
        double at(CombatConfig config, int level);
    }

    /**
     * Something a skill does beyond its attribute points.
     *
     * @param value null when there is no number to show (effects that are not built)
     * @param note  one optional extra line ("being rebuilt...")
     */
    public record SkillEffect(String label, EffectStatus status, EffectValue value, Unit unit, String note) {
        static SkillEffect live(String label, EffectValue v, Unit u) { return new SkillEffect(label, EffectStatus.LIVE, v, u, null); }
        static SkillEffect pvp(String label, EffectValue v, Unit u) { return new SkillEffect(label, EffectStatus.PVP_ONLY, v, u, null); }
        static SkillEffect planned(String label) { return new SkillEffect(label, EffectStatus.NOT_ACTIVE, null, null, null); }
        static SkillEffect planned(String label, String note) { return new SkillEffect(label, EffectStatus.NOT_ACTIVE, null, null, note); }
    }

    // ── Skill Definition ─────────────────────────────────────────

    /**
     * @param name        internal name: the database key, never shown when a display name exists
     * @param description what the skill is, one line
     * @param training    the ways to earn XP besides kills (kills are described from skills.yml)
     * @param effects     what the skill does beyond attribute points, each with its status
     */
    public record SkillDef(String name, String description, List<String> training, Material icon,
                           SkillCategory category, List<SkillEffect> effects) {
        public SkillDef {
            training = List.copyOf(training);
            effects = List.copyOf(effects);
        }

        /** The name players read (see {@link SkillNames}). */
        public String displayName() { return SkillNames.display(name); }
    }

    // ── Runtime state ────────────────────────────────────────────

    /** The catalog after skills.yml's overrides, before generated training lines. */
    private static volatile List<SkillDef> BASE = defaults();
    /** Skills whose training skills.yml sets itself (generated lines never replace those). */
    private static volatile Set<String> TRAINING_SET_BY_CONFIG = Set.of();
    /** Training worked out from other configs (resources.yml: gathering and forging), by skill. */
    private static volatile Map<String, List<String>> GENERATED = Map.of();
    private static volatile List<SkillDef> ALL = BASE;

    public static List<SkillDef> all() { return ALL; }

    /**
     * Training lines for skills trained through another config (resources.yml's gathering and
     * forging XP), so the numbers shown are the ones in use. skills.yml's own training text still wins.
     */
    public static void setGeneratedTraining(Map<String, List<String>> lines) {
        GENERATED = Map.copyOf(lines);
        ALL = withGenerated(BASE);
    }

    private static List<SkillDef> withGenerated(List<SkillDef> base) {
        Map<String, List<String>> gen = GENERATED;
        Set<String> fixed = TRAINING_SET_BY_CONFIG;
        if (gen.isEmpty()) return base;
        List<SkillDef> out = new ArrayList<>(base.size());
        for (SkillDef d : base) {
            List<String> t = gen.get(d.name());
            out.add(t == null || fixed.contains(d.name()) ? d
                    : new SkillDef(d.name(), d.description(), t, d.icon(), d.category(), d.effects()));
        }
        return List.copyOf(out);
    }

    public static List<SkillDef> byCategory(SkillCategory cat) {
        return ALL.stream().filter(s -> s.category == cat).collect(Collectors.toList());
    }

    /** By internal name or display name, ignoring case, spaces and underscores. */
    public static SkillDef byName(String name) {
        if (name == null) return null;
        String want = SkillNames.normalize(name);
        for (SkillDef d : ALL) if (SkillNames.normalize(d.name).equals(want)) return d;
        for (SkillDef d : ALL) if (SkillNames.normalize(d.displayName()).equals(want)) return d;
        return null;
    }

    // ── Loading ──────────────────────────────────────────────────

    /** Load/reload from skills.yml ({@code skill-gui} overrides on top of the defaults). Call on enable and /cerb reload. */
    public static void reload(JavaPlugin plugin) {
        File file = new File(plugin.getDataFolder(), "skills.yml");
        if (!file.exists()) {
            plugin.saveResource("skills.yml", false);
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection gui = yaml.getConfigurationSection("skill-gui");

        List<SkillDef> out = new ArrayList<>();
        Map<String, String> renames = new HashMap<>(SkillNames.DEFAULT_RENAMES);
        Set<String> trainingSet = new HashSet<>();
        for (SkillDef def : defaults()) {
            ConfigurationSection s = gui == null ? null : gui.getConfigurationSection(def.name());
            if (s != null && (s.isList("training") || s.isString("guide"))) trainingSet.add(def.name());
            out.add(s == null ? def : override(def, s, renames));
        }

        // Extra skills defined only in the config (shown, never invented: no effects, no training unless given)
        if (gui != null) {
            for (String key : gui.getKeys(false)) {
                if (out.stream().anyMatch(d -> d.name().equals(key))) continue;
                ConfigurationSection s = gui.getConfigurationSection(key);
                if (s == null) continue;
                SkillDef blank = new SkillDef(key, "A skill.", List.of(), Material.PAPER, SkillCategory.UTILITY, List.of());
                out.add(override(blank, s, renames));
            }
        }

        SkillNames.setRenames(renames);
        BASE = List.copyOf(out);
        TRAINING_SET_BY_CONFIG = Set.copyOf(trainingSet);
        ALL = withGenerated(BASE);
        log.info("[SkillDefs] Loaded " + out.size() + " skill definitions ("
                + byCategory(SkillCategory.COMBAT).size() + " combat, "
                + byCategory(SkillCategory.MAGIC).size() + " magic, "
                + byCategory(SkillCategory.UTILITY).size() + " utility)");
    }

    private static SkillDef override(SkillDef def, ConfigurationSection s, Map<String, String> renames) {
        Material icon = def.icon();
        String iconStr = s.getString("icon");
        if (iconStr != null) {
            Material m = Material.matchMaterial(iconStr);
            if (m != null && m.isItem()) icon = m;
            else log.warning("[SkillDefs] Invalid icon '" + iconStr + "' for " + def.name() + ", using default");
        }
        SkillCategory category = def.category();
        String catStr = s.getString("category");
        if (catStr != null) {
            try {
                category = SkillCategory.valueOf(catStr.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                log.warning("[SkillDefs] Invalid category '" + catStr + "' for " + def.name());
            }
        }
        List<String> training = def.training();
        if (s.isList("training")) training = s.getStringList("training");
        else if (s.isString("guide")) training = List.of(s.getString("guide"));
        String display = s.getString("display-name");
        if (display != null && !display.isBlank()) renames.put(def.name(), display.trim());
        return new SkillDef(def.name(), s.getString("description", def.description()), training, icon, category, def.effects());
    }

    // ── The catalog ──────────────────────────────────────────────

    /** The chance a piece forged at Smithing {@code level} comes out {@code q} or better (resources.yml's odds). */
    /** The chance of heartwood from one natural log of {@code wood} at {@code level} (0 when the wood isn't in resources.yml). */
    /** How many species (or regular creatures) Fishing's level allows, across every water (resources.yml). */
    private static double fishCount(int level, boolean species) {
        Gathering.FishingRules r = Gathering.GatheringCatalog.current().fishing();
        if (species) return r.allSpecies().values().stream().filter(s -> s.level() <= level).count();
        return level < r.techniques().creatures() ? 0 : r.creatures().values().stream().filter(c -> !c.apex() && c.level() <= level).count()
                + (r.apex(level) ? r.creatures().values().stream().filter(c -> c.apex()).count() : 0);
    }

    private static double heartwood(String wood, int level) {
        Gathering.GatheringCatalog c = Gathering.GatheringCatalog.current();
        Gathering.GatheringCatalog.ResourceDef r = c.resources().get(wood);
        return r == null || r.tree() == null ? 0 : c.wood().heartwoodChance(r.tree().heartwood(), level);
    }

    private static double cropQuality(int level) {
        Gathering.FarmingRules r = Gathering.GatheringCatalog.current().farming();
        return 1 - r.chance(Gathering.Quality.COMMON, level);
    }

    private static double forged(Gathering.Quality q, int level) {
        Gathering.GatheringCatalog.QualityRules r = Gathering.GatheringCatalog.current().quality();
        return Gathering.Quality.chanceAtLeast(q, level, r.oddsAt1(), r.oddsAt100());
    }

    private static String xp(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
    }

    static List<SkillDef> defaults() {
        SkillCategory C = SkillCategory.COMBAT, M = SkillCategory.MAGIC, U = SkillCategory.UTILITY;
        Unit PCT = Unit.PERCENT;
        List<SkillDef> d = new ArrayList<>();

        // ── Combat ──
        d.add(new SkillDef("Blade Mastery", "The art of the sword: footwork, edge and timing.",
                List.of(), Material.DIAMOND_SWORD, C, List.of(
                SkillEffect.pvp("Extra sword damage", (c, l) -> CombatSkillProcessor.bladeMasteryBonus(c, CombatSkillProcessor.pvpLevel(l)), PCT))));
        d.add(new SkillDef("Martial Expertise", "Fighting with bare hands.",
                List.of(), Material.IRON_SWORD, C, List.of(
                SkillEffect.planned("Knockback resistance"),
                SkillEffect.planned("Chance to disarm an opponent"))));
        d.add(new SkillDef("Weapon Mastery", "Handling axes, maces, tridents and spears.",
                List.of(), Material.NETHERITE_AXE, C, List.of(
                SkillEffect.live("Lifesteal: heals part of the damage you deal", CombatSkillProcessor::weaponMasteryLifesteal, PCT),
                SkillEffect.pvp("Extra damage", (c, l) -> CombatSkillProcessor.weaponMasteryBonus(c, CombatSkillProcessor.pvpLevel(l)), PCT))));
        d.add(new SkillDef("Ranged Precision", "Fighting at range with bows and crossbows.",
                List.of(), Material.BOW, C, List.of(
                SkillEffect.planned("Better arrow accuracy"))));
        d.add(new SkillDef("Heavy Armor Training", "Fighting in iron, diamond and netherite armor.",
                List.of("Take hits from mobs while wearing at least one iron, diamond or netherite armor piece: "
                                + "XP equal to half the damage, up to " + xp(CombatSkillProcessor.HEAVY_ARMOR_XP_MAX)
                                + " per hit, their arrows and other projectiles included.",
                        "Not while you have been idle for a minute; each mob's hits pay at most what its kill pays.",
                        "Kill mobs with a weapon that no other skill trains (tools...) while wearing such armor."),
                Material.NETHERITE_CHESTPLATE, C, List.of(
                SkillEffect.live("Extra armor (damage reduction)", PlayerDefenseManager::heavyArmorBonus, PCT),
                SkillEffect.live("Extra defense bar points", PlayerDefenseManager::heavyArmorBarBonus, Unit.REALM_POINTS))));
        d.add(new SkillDef("Dual Wielding", "Fighting with a weapon in each hand.",
                List.of(), Material.IRON_AXE, C, List.of(
                SkillEffect.planned("Off-hand attacks"))));
        d.add(new SkillDef("Critical Strike", "Finding the weak spot: more frequent, harder critical hits.",
                List.of("Hit mobs with melee attacks or spells: " + xp(CombatSkillProcessor.CRIT_STRIKE_HIT_XP)
                        + " XP per hit, " + xp(CombatSkillProcessor.CRIT_STRIKE_CRIT_XP) + " for a critical hit.",
                        "Each mob pays at most what its kill pays."),
                Material.GOLDEN_SWORD, C, List.of(
                SkillEffect.pvp("Critical hit chance", (c, l) -> CombatSkillProcessor.critChance(c, CombatSkillProcessor.pvpLevel(l)), PCT),
                SkillEffect.pvp("Critical hit damage", (c, l) -> CombatSkillProcessor.critMultiplier(c, CombatSkillProcessor.pvpLevel(l)), Unit.MULTIPLIER))));

        // ── Magic ──
        d.add(new SkillDef("Intelligence", "Understanding of magic itself.",
                List.of("Cast a spell that hits an enemy: " + xp(Manager.XpIntegrity.INTELLIGENCE_CAST_XP) + " XP."),
                Material.ENCHANTED_BOOK, M, List.of()));
        d.add(new SkillDef("Arcane Knowledge", "Lore of enchanted books and arcane items.",
                List.of("Cast an arcane spell that hits an enemy: " + xp(Manager.XpIntegrity.SCHOOL_CAST_XP) + " XP."), Material.EXPERIENCE_BOTTLE, M, List.of(
                SkillEffect.planned("Enchanting and magic item bonuses"))));
        d.add(new SkillDef("Elemental Mastery", "Command of fire and the other elements.",
                List.of("Cast a fire, frost, lightning or earth spell that hits an enemy: " + xp(Manager.XpIntegrity.SCHOOL_CAST_XP) + " XP."),
                Material.BLAZE_POWDER, M, List.of(
                SkillEffect.planned("Extra elemental spell damage"))));
        d.add(new SkillDef("Summoning", "Calling creatures to fight beside you.",
                List.of(), Material.SPAWNER, M, List.of(
                SkillEffect.planned("Summon creatures"))));
        d.add(new SkillDef("Spell Weaving", "Casting through a wand, rod or staff.",
                List.of("Cast a spell that hits an enemy: " + xp(Manager.XpIntegrity.SPELL_WEAVING_CAST_XP) + " XP."),
                Material.END_ROD, M, List.of(
                SkillEffect.planned("Cheaper spells"))));
        d.add(new SkillDef("Mana Regeneration", "Recovering Essence after spending it.",
                List.of(), Material.LAPIS_LAZULI, M, List.of()));
        d.add(new SkillDef("Defensive Magic", "Shields, wards and barriers.",
                List.of(), Material.SHIELD, M, List.of(
                SkillEffect.planned("Shields, wards and barriers"))));

        // ── Utility ──
        // Mining and Smithing train through resources.yml: their training lines come from it (setGeneratedTraining).
        d.add(new SkillDef("Mining", "Working stone and ore.", List.of(), Material.DIAMOND_PICKAXE, U, List.of()));
        // Farming trains through resources.yml's farming: section (docs/design/FARMING_COOKING.md): its crops and techniques.
        d.add(new SkillDef("Farming", "Growing and harvesting crops for the kitchen.", List.of(),
                Material.DIAMOND_HOE, U, List.of(
                SkillEffect.live("Wide Harvest: the square one Sneak + hoe harvests (side)", (cfg, lvl) -> Gathering.GatheringCatalog.current().farming().reach(lvl), Unit.COUNT),
                SkillEffect.live("Chance of a Fine or better crop", (cfg, lvl) -> cropQuality(lvl), PCT))));
        // Woodcutting trains through resources.yml too (its woods, techniques and heartwood: docs/design/WOODCUTTING.md).
        d.add(new SkillDef("Woodcutting", "Felling trees.", List.of(), Material.DIAMOND_AXE, U, List.of(
                SkillEffect.live("Felling: logs one Sneak + cut brings down", (cfg, lvl) -> Gathering.GatheringCatalog.current().wood().capacity(lvl), Unit.LOGS),
                SkillEffect.live("Heartwood chance per oak log", (cfg, lvl) -> heartwood("oak", lvl), PCT),
                SkillEffect.live("Heartwood chance per pale oak log", (cfg, lvl) -> heartwood("pale_oak", lvl), PCT))));
        // Fishing trains through resources.yml's fishing: section (docs/design/FISHING.md): its waters, species and creatures.
        d.add(new SkillDef("Fishing", "Rod, line and patience: every water's species, and what bites back.", List.of(), Material.FISHING_ROD, U, List.of(
                SkillEffect.live("Species you can catch", (cfg, lvl) -> fishCount(lvl, true), Unit.COUNT),
                SkillEffect.live("Sea creatures that can take your bait", (cfg, lvl) -> fishCount(lvl, false), Unit.COUNT))));
        d.add(new SkillDef("Crafting", "Making tools and goods.", List.of(), Material.CRAFTING_TABLE, U,
                List.of(SkillEffect.planned("Advanced recipes"))));
        d.add(new SkillDef("Smithing", "Smelting metal, forging gear and keeping it in shape.", List.of(), Material.ANVIL, U, List.of(
                SkillEffect.live("Chance of Superior or better forged gear", (cfg, lvl) -> forged(Gathering.Quality.SUPERIOR, lvl), PCT),
                SkillEffect.live("Chance of Masterwork forged gear", (cfg, lvl) -> forged(Gathering.Quality.MASTERWORK, lvl), PCT))));
        d.add(new SkillDef("Alchemy", "Brewing potions.", List.of(), Material.BREWING_STAND, U,
                List.of(SkillEffect.planned("Stronger potions"))));
        d.add(new SkillDef("Enchanting", "Binding enchantments to gear.", List.of(), Material.ENCHANTING_TABLE, U,
                List.of(SkillEffect.planned("Better enchantments"))));
        d.add(new SkillDef("Herbalism", "Gathering herbs, flowers and plants.", List.of(), Material.FERN, U,
                List.of(SkillEffect.planned("Better herb yields"))));
        d.add(new SkillDef("Cooking", "Preparing food.", List.of(), Material.CAMPFIRE, U,
                List.of(SkillEffect.planned("Better food"))));
        d.add(new SkillDef("First Aid", "Treating wounds and recovering faster.", List.of(), Material.GOLDEN_APPLE, U,
                List.of(SkillEffect.planned("Stronger healing and faster recovery"))));
        d.add(new SkillDef("Stealth", "Moving unseen.", List.of(), Material.LEATHER_BOOTS, U,
                List.of(SkillEffect.planned("Harder to notice while sneaking"))));
        d.add(new SkillDef("Trap Mastery", "Building and setting traps.", List.of(), Material.TRIPWIRE_HOOK, U,
                List.of(SkillEffect.planned("Stronger traps"))));
        d.add(new SkillDef("Scavenging", "Finding more in chests and on fallen foes.", List.of(), Material.CHEST, U,
                List.of(SkillEffect.planned("Better loot"))));
        d.add(new SkillDef("Repairing", "Mending damaged tools and armor.", List.of(), Material.IRON_INGOT, U,
                List.of(SkillEffect.planned("Cheaper repairs"))));
        d.add(new SkillDef("Trading", "Bargaining with villagers.", List.of(), Material.EMERALD, U,
                List.of(SkillEffect.planned("Better villager prices"))));
        d.add(new SkillDef("Navigation", "Finding your way through unknown land.",
                List.of("Enter a chunk you have never visited: " + xp(ChunkDiscoveryManager.XP_PER_CHUNK) + " XP."),
                Material.COMPASS, U, List.of()));
        d.add(new SkillDef("Animal Taming", "Taming and breeding animals.", List.of(), Material.LEAD, U,
                List.of(SkillEffect.planned("Faster taming"))));
        d.add(new SkillDef("Riding", "Handling mounts.", List.of(), Material.SADDLE, U,
                List.of(SkillEffect.planned("Faster mounts"))));
        d.add(new SkillDef("Lockpicking", "Opening what is locked.", List.of(), Material.IRON_DOOR, U,
                List.of(SkillEffect.planned("Open locked chests and doors"))));
        d.add(new SkillDef("Survival", "Enduring the wild: fire, cold and deep water.",
                List.of("Take hits from mobs: " + xp(CombatSkillProcessor.SURVIVAL_HIT_XP)
                                + " XP per hit, their arrows and other projectiles included.",
                        "Not while you have been idle for a minute; each mob's hits pay at most what its kill pays."),
                Material.FLINT_AND_STEEL, U, List.of(
                new SkillEffect("Fire resistance", EffectStatus.LIVE, (c, l) -> PlayerDefenseManager.survivalFireResist(l), PCT,
                        "Flames, burning afterwards, campfires, magma blocks and fire spells; lava only partly (combat.yml fire-resistance, half by default)."),
                new SkillEffect("Cold resistance", EffectStatus.LIVE, (c, l) -> PlayerDefenseManager.survivalColdResist(l), PCT,
                        "Freezing in powder snow and frost spells."),
                SkillEffect.live("Drowning resistance", (c, l) -> PlayerDefenseManager.survivalDrowningResist(l), PCT))));
        return d;
    }
}

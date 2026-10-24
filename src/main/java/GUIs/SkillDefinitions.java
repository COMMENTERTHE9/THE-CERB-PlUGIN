package GUIs;

import Manager.ChunkDiscoveryManager;
import Manager.CombatConfig;
import Manager.CombatSkillProcessor;
import Manager.PlayerDefenseManager;
import Skills.SkillNames;
import Spells.SpellManager;
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
    public enum Unit { PERCENT, MULTIPLIER, REALM_POINTS }

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

    private static volatile List<SkillDef> ALL = defaults();

    public static List<SkillDef> all() { return ALL; }

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
        for (SkillDef def : defaults()) {
            ConfigurationSection s = gui == null ? null : gui.getConfigurationSection(def.name());
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
        ALL = List.copyOf(out);
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
                SkillEffect.pvp("Extra sword damage", CombatSkillProcessor::bladeMasteryBonus, PCT),
                SkillEffect.planned("Faster sword attacks"))));
        d.add(new SkillDef("Martial Expertise", "Fighting with bare hands.",
                List.of(), Material.IRON_SWORD, C, List.of(
                SkillEffect.planned("Knockback resistance"),
                SkillEffect.planned("Chance to disarm an opponent"))));
        d.add(new SkillDef("Weapon Mastery", "Handling axes, maces, tridents and spears.",
                List.of(), Material.NETHERITE_AXE, C, List.of(
                SkillEffect.live("Lifesteal: heals part of the damage you deal", CombatSkillProcessor::weaponMasteryLifesteal, PCT),
                SkillEffect.pvp("Extra damage", CombatSkillProcessor::weaponMasteryBonus, PCT))));
        d.add(new SkillDef("Ranged Precision", "Fighting at range with bows and crossbows.",
                List.of(), Material.BOW, C, List.of(
                SkillEffect.planned("Better arrow accuracy"))));
        d.add(new SkillDef("Heavy Armor Training", "Fighting in iron, diamond and netherite armor.",
                List.of("Take damage while wearing at least one iron, diamond or netherite armor piece: "
                                + "XP equal to half the damage, up to " + xp(CombatSkillProcessor.HEAVY_ARMOR_XP_MAX) + " per hit.",
                        "Kill mobs with a weapon that no other skill trains (spears, tools...) while wearing such armor."),
                Material.NETHERITE_CHESTPLATE, C, List.of(
                SkillEffect.live("Extra armor (damage reduction)", PlayerDefenseManager::heavyArmorBonus, PCT),
                SkillEffect.live("Extra defense bar points", PlayerDefenseManager::heavyArmorBarBonus, Unit.REALM_POINTS))));
        d.add(new SkillDef("Dual Wielding", "Fighting with a weapon in each hand.",
                List.of(), Material.IRON_AXE, C, List.of(
                SkillEffect.planned("Off-hand attacks"))));
        d.add(new SkillDef("Critical Strike", "Finding the weak spot: more frequent, harder critical hits.",
                List.of("Hit mobs with melee attacks or spells: " + xp(CombatSkillProcessor.CRIT_STRIKE_HIT_XP)
                        + " XP per hit, " + xp(CombatSkillProcessor.CRIT_STRIKE_CRIT_XP) + " for a critical hit."),
                Material.GOLDEN_SWORD, C, List.of(
                SkillEffect.pvp("Critical hit chance", CombatSkillProcessor::critChance, PCT),
                SkillEffect.pvp("Critical hit damage", CombatSkillProcessor::critMultiplier, Unit.MULTIPLIER))));

        // ── Magic ──
        d.add(new SkillDef("Intelligence", "Understanding of magic itself.",
                List.of("Cast a spell successfully: " + xp(SpellManager.INTELLIGENCE_CAST_XP) + " XP."),
                Material.ENCHANTED_BOOK, M, List.of()));
        d.add(new SkillDef("Arcane Knowledge", "Lore of enchanted books and arcane items.",
                List.of(), Material.EXPERIENCE_BOTTLE, M, List.of(
                SkillEffect.planned("Enchanting and magic item bonuses"))));
        d.add(new SkillDef("Elemental Mastery", "Command of fire and the other elements.",
                List.of("Cast Fireball successfully: " + xp(SpellManager.ELEMENTAL_MASTERY_FIREBALL_XP) + " XP."),
                Material.BLAZE_POWDER, M, List.of(
                SkillEffect.planned("Extra elemental spell damage"))));
        d.add(new SkillDef("Summoning", "Calling creatures to fight beside you.",
                List.of(), Material.SPAWNER, M, List.of(
                SkillEffect.planned("Summon creatures"))));
        d.add(new SkillDef("Spell Weaving", "Casting through a wand, rod or staff.",
                List.of("Cast a spell successfully: " + xp(SpellManager.SPELL_WEAVING_CAST_XP) + " XP."),
                Material.END_ROD, M, List.of(
                SkillEffect.planned("Faster casting and cheaper spells"))));
        d.add(new SkillDef("Mana Regeneration", "Recovering Essence after spending it.",
                List.of(), Material.LAPIS_LAZULI, M, List.of()));
        d.add(new SkillDef("Defensive Magic", "Shields, wards and barriers.",
                List.of(), Material.SHIELD, M, List.of(
                SkillEffect.planned("Shields, wards and barriers"))));

        // ── Utility ──
        d.add(new SkillDef("Mining", "Working stone and ore.", List.of(), Material.DIAMOND_PICKAXE, U,
                List.of(SkillEffect.planned("Better ore yields and faster mining"))));
        d.add(new SkillDef("Farming", "Growing and harvesting crops.", List.of(), Material.DIAMOND_HOE, U,
                List.of(SkillEffect.planned("Better crop yields"))));
        d.add(new SkillDef("Woodcutting", "Felling trees.", List.of(), Material.DIAMOND_AXE, U,
                List.of(SkillEffect.planned("Better wood yields"))));
        d.add(new SkillDef("Fishing", "Rod, line and patience.", List.of(), Material.FISHING_ROD, U,
                List.of(SkillEffect.planned("Rarer catches"))));
        d.add(new SkillDef("Crafting", "Making tools and goods.", List.of(), Material.CRAFTING_TABLE, U,
                List.of(SkillEffect.planned("Advanced recipes"))));
        d.add(new SkillDef("Smithing", "Working metal at the anvil.", List.of(), Material.ANVIL, U,
                List.of(SkillEffect.planned("Better anvil results"))));
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
                Material.COMPASS, U,
                List.of(SkillEffect.planned("Faster movement", "Being rebuilt: the old bonus ignores your level."))));
        d.add(new SkillDef("Animal Taming", "Taming and breeding animals.", List.of(), Material.LEAD, U,
                List.of(SkillEffect.planned("Faster taming"))));
        d.add(new SkillDef("Riding", "Handling mounts.", List.of(), Material.SADDLE, U,
                List.of(SkillEffect.planned("Faster mounts"))));
        d.add(new SkillDef("Lockpicking", "Opening what is locked.", List.of(), Material.IRON_DOOR, U,
                List.of(SkillEffect.planned("Open locked chests and doors"))));
        d.add(new SkillDef("Survival", "Enduring the wild: fire, cold and deep water.",
                List.of("Take damage from anything (mobs, falls, fire, drowning...): "
                        + xp(CombatSkillProcessor.SURVIVAL_HIT_XP) + " XP per hit."),
                Material.FLINT_AND_STEEL, U, List.of(
                SkillEffect.live("Fire resistance", (c, l) -> PlayerDefenseManager.survivalFireResist(l), PCT),
                SkillEffect.live("Cold resistance", (c, l) -> PlayerDefenseManager.survivalColdResist(l), PCT),
                SkillEffect.live("Drowning resistance", (c, l) -> PlayerDefenseManager.survivalDrowningResist(l), PCT))));
        return d;
    }
}

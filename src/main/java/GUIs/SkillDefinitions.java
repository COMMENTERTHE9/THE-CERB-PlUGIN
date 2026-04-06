package GUIs;

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
 * Single source of truth for skill GUI metadata.
 * Loads from skills.yml {@code skill-gui} section with hardcoded defaults as fallback.
 * Add a skill to the config and it appears in the GUI automatically.
 */
public final class SkillDefinitions {

    private static final Logger log = Logger.getLogger("Cerberus");

    // ── Skill Category ───────────────────────────────────────────

    public enum SkillCategory {
        COMBAT(ChatColor.RED, Material.RED_STAINED_GLASS_PANE, "Combat Skills"),
        MAGIC(ChatColor.LIGHT_PURPLE, Material.PURPLE_STAINED_GLASS_PANE, "Magic Skills"),
        UTILITY(ChatColor.GREEN, Material.GREEN_STAINED_GLASS_PANE, "Utility Skills");

        public final ChatColor color;
        public final Material accentGlass;
        public final String displayTitle;

        SkillCategory(ChatColor color, Material accentGlass, String displayTitle) {
            this.color = color;
            this.accentGlass = accentGlass;
            this.displayTitle = displayTitle;
        }
    }

    // ── Skill Definition ─────────────────────────────────────────

    public record SkillDef(
            String name,
            String description,
            String guideText,        // short "how to level" text for the guide book icon
            Material icon,
            SkillCategory category,
            String[] scalingStatKeys // keys into CombatConfig.getAllScalingParams(); empty for no stats
    ) {}

    // ── Runtime state ────────────────────────────────────────────

    private static final List<SkillDef> ALL = new ArrayList<>();

    public static List<SkillDef> all() { return Collections.unmodifiableList(ALL); }

    public static List<SkillDef> byCategory(SkillCategory cat) {
        return ALL.stream().filter(s -> s.category == cat).collect(Collectors.toList());
    }

    public static SkillDef byName(String name) {
        return ALL.stream().filter(s -> s.name.equalsIgnoreCase(name)).findFirst().orElse(null);
    }

    // ── Loading ──────────────────────────────────────────────────

    /** Load/reload skill GUI definitions from skills.yml. Call on enable and /cerb reload. */
    public static void reload(JavaPlugin plugin) {
        ALL.clear();

        File file = new File(plugin.getDataFolder(), "skills.yml");
        if (!file.exists()) {
            plugin.saveResource("skills.yml", false);
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);

        ConfigurationSection guiSection = yaml.getConfigurationSection("skill-gui");

        // Load from config, falling back to defaults for missing entries
        for (DefaultSkill def : DEFAULTS) {
            Material icon = def.icon;
            String description = def.description;
            String guideText = def.guideText;
            SkillCategory category = def.category;
            String[] statKeys = def.statKeys;

            if (guiSection != null && guiSection.contains(def.name)) {
                ConfigurationSection s = guiSection.getConfigurationSection(def.name);
                if (s != null) {
                    String iconStr = s.getString("icon", def.icon.name());
                    try {
                        icon = Material.valueOf(iconStr.toUpperCase());
                    } catch (IllegalArgumentException e) {
                        log.warning("[SkillDefs] Invalid icon '" + iconStr + "' for " + def.name + ", using default");
                    }
                    description = s.getString("description", def.description);
                    guideText = s.getString("guide", def.guideText);
                    String catStr = s.getString("category", def.category.name());
                    try {
                        category = SkillCategory.valueOf(catStr.toUpperCase());
                    } catch (IllegalArgumentException e) {
                        log.warning("[SkillDefs] Invalid category '" + catStr + "' for " + def.name);
                    }
                }
            }

            ALL.add(new SkillDef(def.name, description, guideText, icon, category, statKeys));
        }

        // Load any extra skills defined in config but not in defaults
        if (guiSection != null) {
            for (String key : guiSection.getKeys(false)) {
                if (byName(key) != null) continue; // already loaded from defaults
                ConfigurationSection s = guiSection.getConfigurationSection(key);
                if (s == null) continue;

                String iconStr = s.getString("icon", "PAPER");
                Material icon;
                try {
                    icon = Material.valueOf(iconStr.toUpperCase());
                } catch (IllegalArgumentException e) {
                    icon = Material.PAPER;
                }
                String desc = s.getString("description", "A skill.");
                SkillCategory cat;
                try {
                    cat = SkillCategory.valueOf(s.getString("category", "UTILITY").toUpperCase());
                } catch (IllegalArgumentException e) {
                    cat = SkillCategory.UTILITY;
                }
                String guide = s.getString("guide", "Level up by using this skill.");
                ALL.add(new SkillDef(key, desc, guide, icon, cat, new String[0]));
            }
        }

        log.info("[SkillDefs] Loaded " + ALL.size() + " skill definitions ("
                + byCategory(SkillCategory.COMBAT).size() + " combat, "
                + byCategory(SkillCategory.MAGIC).size() + " magic, "
                + byCategory(SkillCategory.UTILITY).size() + " utility)");
    }

    // ── Hardcoded defaults (fallback when skills.yml has no skill-gui section) ──

    private record DefaultSkill(String name, String description, String guideText,
                                Material icon, SkillCategory category, String[] statKeys) {}

    private static final DefaultSkill[] DEFAULTS = {
            // ── Combat ──
            new DefaultSkill("Blade Mastery", "Increases sword damage and attack speed",
                    "Use swords in combat. Each hit with a sword grants XP.",
                    Material.DIAMOND_SWORD, SkillCategory.COMBAT,
                    new String[]{"blade-mastery / damage-bonus", "blade-mastery / speed-bonus"}),
            new DefaultSkill("Martial Expertise", "Knockback resistance and disarm chance",
                    "Fight unarmed or with fists. Take and deal melee hits.",
                    Material.IRON_SWORD, SkillCategory.COMBAT,
                    new String[]{"martial-expertise / kb-resist", "martial-expertise / disarm-chance"}),
            new DefaultSkill("Weapon Mastery", "Bonus damage and lifesteal with all weapons",
                    "Deal damage with any weapon. Axes and hammers grant XP.",
                    Material.NETHERITE_AXE, SkillCategory.COMBAT,
                    new String[]{"weapon-mastery / damage-multiplier", "weapon-mastery / lifesteal"}),
            new DefaultSkill("Ranged Precision", "Improves bow and crossbow accuracy",
                    "Hit targets with bows or crossbows. Longer shots give more XP.",
                    Material.BOW, SkillCategory.COMBAT, new String[0]),
            new DefaultSkill("Heavy Armor Training", "Armor bonus and defense bar boost",
                    "Take damage while wearing heavy armor (iron, diamond, netherite).",
                    Material.NETHERITE_CHESTPLATE, SkillCategory.COMBAT,
                    new String[]{"heavy-armor-training / armor-bonus", "heavy-armor-training / defense-bar-bonus"}),
            new DefaultSkill("Dual Wielding", "Off-hand attack bonuses",
                    "Fight with items in both hands. Off-hand hits grant XP.",
                    Material.IRON_AXE, SkillCategory.COMBAT, new String[0]),
            new DefaultSkill("Critical Strike", "Chance for critical hits with bonus damage",
                    "Land melee hits on mobs. Critical hits grant bonus XP.",
                    Material.GOLDEN_SWORD, SkillCategory.COMBAT,
                    new String[]{"critical-strike / crit-chance", "critical-strike / crit-multiplier"}),

            // ── Magic ──
            new DefaultSkill("Intelligence", "Increases mana pool and spell power",
                    "Cast any spell or use mana-consuming abilities.",
                    Material.ENCHANTED_BOOK, SkillCategory.MAGIC, new String[0]),
            new DefaultSkill("Arcane Knowledge", "Improves enchanting and magic item effects",
                    "Enchant items at an enchanting table. Higher enchants give more XP.",
                    Material.EXPERIENCE_BOTTLE, SkillCategory.MAGIC, new String[0]),
            new DefaultSkill("Elemental Mastery", "Enhances elemental spell damage",
                    "Deal fire, ice, or lightning damage with elemental spells or weapons.",
                    Material.BLAZE_POWDER, SkillCategory.MAGIC, new String[0]),
            new DefaultSkill("Summoning", "Summon creatures to aid in battle",
                    "Summon creatures using summoning abilities. Keep them alive for bonus XP.",
                    Material.SPAWNER, SkillCategory.MAGIC, new String[0]),
            new DefaultSkill("Spell Weaving", "Faster casting and reduced mana costs",
                    "Cast spells using a wand or stick. Rapid casting grants more XP.",
                    Material.END_ROD, SkillCategory.MAGIC, new String[0]),
            new DefaultSkill("Mana Regeneration", "Increases passive mana recovery rate",
                    "Spend mana and let it regenerate. Full mana cycles grant XP.",
                    Material.LAPIS_LAZULI, SkillCategory.MAGIC, new String[0]),
            new DefaultSkill("Defensive Magic", "Shields, wards, barriers, and mana drain",
                    "Activate shields, wards, or barriers. Block damage to gain XP.",
                    Material.SHIELD, SkillCategory.MAGIC, new String[0]),

            // ── Utility ──
            new DefaultSkill("Mining", "Better ore yields and faster mining",
                    "Mine ore blocks with a pickaxe. Rarer ores give more XP.",
                    Material.DIAMOND_PICKAXE, SkillCategory.UTILITY, new String[0]),
            new DefaultSkill("Farming", "Improved crop yields and growth rates",
                    "Harvest crops with a hoe. Fully grown crops give the most XP.",
                    Material.DIAMOND_HOE, SkillCategory.UTILITY, new String[0]),
            new DefaultSkill("Woodcutting", "Better wood yields and rare drops",
                    "Chop wood with an axe. Larger trees give more XP.",
                    Material.DIAMOND_AXE, SkillCategory.UTILITY, new String[0]),
            new DefaultSkill("Fishing", "Rare catches and improved fish quality",
                    "Catch fish with a fishing rod. Rare catches give bonus XP.",
                    Material.FISHING_ROD, SkillCategory.UTILITY, new String[0]),
            new DefaultSkill("Crafting", "Unlock advanced recipes and improve quality",
                    "Craft items at a crafting table. Complex recipes give more XP.",
                    Material.CRAFTING_TABLE, SkillCategory.UTILITY, new String[0]),
            new DefaultSkill("Smithing", "Better anvil results and reduced repair costs",
                    "Use anvils and grindstones to repair or upgrade gear.",
                    Material.ANVIL, SkillCategory.UTILITY, new String[0]),
            new DefaultSkill("Alchemy", "Stronger potions and new brewing recipes",
                    "Brew potions at a brewing stand. Stronger potions give more XP.",
                    Material.BREWING_STAND, SkillCategory.UTILITY, new String[0]),
            new DefaultSkill("Enchanting", "Higher enchant levels and better outcomes",
                    "Enchant items at an enchanting table. Higher level enchants give more XP.",
                    Material.ENCHANTING_TABLE, SkillCategory.UTILITY, new String[0]),
            new DefaultSkill("Herbalism", "Improved herb yields and plant knowledge",
                    "Harvest plants and flowers with shears. Rare plants give bonus XP.",
                    Material.FERN, SkillCategory.UTILITY, new String[0]),
            new DefaultSkill("Cooking", "Better food quality and special recipes",
                    "Cook food in furnaces or campfires. Complex meals give more XP.",
                    Material.CAMPFIRE, SkillCategory.UTILITY, new String[0]),
            new DefaultSkill("First Aid", "Healing effectiveness and recovery speed",
                    "Use potions or golden apples to heal. Healing others gives bonus XP.",
                    Material.GOLDEN_APPLE, SkillCategory.UTILITY, new String[0]),
            new DefaultSkill("Stealth", "Sneak speed and detection avoidance",
                    "Sneak near hostile mobs without being detected. Longer steaks give more XP.",
                    Material.LEATHER_BOOTS, SkillCategory.UTILITY, new String[0]),
            new DefaultSkill("Trap Mastery", "Build and arm more effective traps",
                    "Place and arm traps using tripwire hooks and levers. Triggered traps give XP.",
                    Material.TRIPWIRE_HOOK, SkillCategory.UTILITY, new String[0]),
            new DefaultSkill("Scavenging", "Better loot from chests and mob drops",
                    "Loot chests and collect mob drops. Rare finds give bonus XP.",
                    Material.CHEST, SkillCategory.UTILITY, new String[0]),
            new DefaultSkill("Repairing", "Repair items with less material cost",
                    "Repair damaged tools and armor at an anvil or grindstone.",
                    Material.IRON_INGOT, SkillCategory.UTILITY, new String[0]),
            new DefaultSkill("Trading", "Better prices and rare trade options",
                    "Trade with villagers using emeralds, gold, or diamonds.",
                    Material.EMERALD, SkillCategory.UTILITY, new String[0]),
            new DefaultSkill("Navigation", "Movement speed and environment bonuses",
                    "Travel long distances on foot. Exploring new areas gives bonus XP.",
                    Material.COMPASS, SkillCategory.UTILITY, new String[0]),
            new DefaultSkill("Animal Taming", "Tame animals faster with stronger bonds",
                    "Tame and breed animals using leads and saddles.",
                    Material.LEAD, SkillCategory.UTILITY, new String[0]),
            new DefaultSkill("Riding", "Mount speed and control bonuses",
                    "Ride tamed animals with a saddle. Distance traveled gives XP.",
                    Material.SADDLE, SkillCategory.UTILITY, new String[0]),
            new DefaultSkill("Lockpicking", "Bypass locked containers and doors",
                    "Attempt to open locked chests and doors. Success gives XP.",
                    Material.IRON_DOOR, SkillCategory.UTILITY, new String[0]),
            new DefaultSkill("Survival", "Fire, cold, and drowning resistance",
                    "Take environmental damage (fire, cold, drowning) and survive. Endure to gain XP.",
                    Material.FLINT_AND_STEEL, SkillCategory.UTILITY, new String[0]),
    };
}

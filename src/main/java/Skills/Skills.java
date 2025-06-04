package Skills;

public enum Skills {
    // Combat Skills
    BLADE_MASTERY("Blade Mastery", "Increases proficiency and damage with bladed weapons like swords and daggers", SkillCategory.COMBAT),
    MARTIAL_EXPERTISE("Martial Expertise", "Boosts skill and effectiveness in hand-to-hand combat and unarmed attacks", SkillCategory.COMBAT),
    WEAPON_MASTERY("Weapon Mastery", "Enhances damage, accuracy, and proficiency with a variety of weapons", SkillCategory.COMBAT),
    RANGED_PRECISION("Ranged Precision", "Improves accuracy and damage with ranged weapons, such as bows and crossbows", SkillCategory.COMBAT),
    HEAVY_ARMOR_TRAINING("Heavy Armor Training", "Increases defense and reduces mobility penalties when wearing heavy armor", SkillCategory.COMBAT),
    DUAL_WIELDING("Dual Wielding", "Increases attack speed and efficiency when wielding two weapons simultaneously", SkillCategory.COMBAT),
    CRITICAL_STRIKE("Critical Strike", "Enhances the chance of landing critical hits for increased damage", SkillCategory.COMBAT),

    // Magic Skills
    INTELLIGENCE("Intelligence", "Increases mana pool, spellcasting speed, and magic damage", SkillCategory.MAGIC),
    ARCANE_KNOWLEDGE("Arcane Knowledge", "Boosts the potency and range of spells, reduces mana cost", SkillCategory.MAGIC),
    ELEMENTAL_MASTERY("Elemental Mastery", "Increases damage and control over elemental magic", SkillCategory.MAGIC),
    SUMMONING("Summoning", "Enhances the strength and duration of summoned creatures", SkillCategory.MAGIC),
    SPELL_WEAVING("Spell Weaving", "Increases the effectiveness of combining multiple spells or magical effects", SkillCategory.MAGIC),
    MANA_REGENERATION("Mana Regeneration", "Increases the rate at which mana is regenerated over time", SkillCategory.MAGIC),
    DEFENSIVE_MAGIC("Defensive Magic", "Improves the strength and duration of magical shields, wards, and barriers", SkillCategory.MAGIC),
    DARK_MAGIC("Dark Magic", "Increases dark spell power and corruption resistance", SkillCategory.MAGIC),
    BLOOD_MAGIC("Blood Magic", "Increases blood ritual efficiency and life drain", SkillCategory.MAGIC),
    POTION_BREWING("Potion Brewing", "Enhances potion brewing speed and strength", SkillCategory.MAGIC),

    // Utility Skills
    MINING("Mining", "Increases efficiency and speed of mining operations", SkillCategory.UTILITY),
    FARMING("Farming", "Enhances crop yield and growth speed", SkillCategory.UTILITY),
    WOODCUTTING("Woodcutting", "Improves the speed and yield when cutting down trees", SkillCategory.UTILITY),
    FISHING("Fishing", "Increases the chances of catching rare fish and valuable items", SkillCategory.UTILITY),
    CRAFTING("Crafting", "Improves crafting efficiency, unlocks advanced recipes", SkillCategory.UTILITY),
    SMITHING("Smithing", "Enhances the ability to forge weapons and armor", SkillCategory.UTILITY),
    ALCHEMY("Alchemy", "Increases the potency and variety of potions that can be crafted", SkillCategory.UTILITY),
    ENCHANTING("Enchanting", "Enhances the strength of enchantments", SkillCategory.UTILITY),
    HERBALISM("Herbalism", "Improves the gathering of plants and herbs", SkillCategory.UTILITY),
    COOKING("Cooking", "Enhances cooking skills, improving the quality and effectiveness of food", SkillCategory.UTILITY);

    private final String name;
    private final String description;
    private final SkillCategory category;

    Skills(String name, String description, SkillCategory category) {
        this.name = name;
        this.description = description;
        this.category = category;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public SkillCategory getCategory() {
        return category;
    }

    public enum SkillCategory {
        COMBAT, MAGIC, UTILITY
    }
}

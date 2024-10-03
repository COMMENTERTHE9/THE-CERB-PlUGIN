package cerberus.world.cerb;

import Manager.PlayerVirtualHealthManager;
import Manager.PlayerDefenseManager;
import Manager.PlayerStrengthManager;
import Skills.Skill;
import Skills.SkillManager;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CustomPlayer {
    // Static map to store CustomPlayer instances
    private static final Map<UUID, CustomPlayer> customPlayers = new HashMap<>();

    private double attackDamage;
    private double magicDamageMultiplier;
    private Map<String, Integer> skillLevels;
    private Map<String, Integer> skillXP; // Add this field to store skill XP
    private SkillManager skillManager;
    private UUID uniqueId;
    private Player bukkitPlayer;
    private ItemStack currentCraftedItem; // Add field for current crafted item
    private String customId; // Add this field for custom ID

    // Managers for virtual health, defense, and strength
    private PlayerVirtualHealthManager healthManager;
    private PlayerDefenseManager defenseManager;
    private PlayerStrengthManager strengthManager;

    // Constructor
    public CustomPlayer(Player bukkitPlayer, SkillManager skillManager,
                        PlayerVirtualHealthManager healthManager, PlayerDefenseManager defenseManager,
                        PlayerStrengthManager strengthManager, String customId) {
        this.attackDamage = 1.0; // Default attack damage
        this.magicDamageMultiplier = 1.0; // Default magic damage multiplier
        this.skillLevels = new HashMap<>();
        this.skillXP = new HashMap<>(); // Initialize skillXP map
        this.skillManager = skillManager;
        this.uniqueId = bukkitPlayer.getUniqueId();
        this.bukkitPlayer = bukkitPlayer;
        this.healthManager = healthManager;
        this.defenseManager = defenseManager;
        this.strengthManager = strengthManager;
        this.customId = customId; // Set the custom ID

        // Register this CustomPlayer instance
        CustomPlayer.registerCustomPlayer(this);
    }

    // Method to retrieve a CustomPlayer instance
    public static CustomPlayer getCustomPlayer(Player player) {
        return customPlayers.get(player.getUniqueId());
    }

    // Method to register a CustomPlayer instance
    public static void registerCustomPlayer(CustomPlayer customPlayer) {
        customPlayers.put(customPlayer.getUniqueId(), customPlayer);
    }

    // Method to unregister a CustomPlayer instance
    public static void unregisterCustomPlayer(Player player) {
        customPlayers.remove(player.getUniqueId());
    }

    // Example method to wrap a custom attribute
    public AttributeWrapper getCustomAttribute(Attribute attribute) {
        if (attribute == Attribute.GENERIC_ATTACK_DAMAGE) {
            return new AttributeWrapper(attackDamage);
        }
        return null;
    }

    // Method to get the level of a specific skill
    public int getSkillLevel(String skillName) {
        return skillLevels.getOrDefault(skillName, 0);
    }

    // Method to set the level of a specific skill
    public void setSkillLevel(String skillName, int level) {
        if (level < 1) level = 1;
        if (level > 160) level = 160;
        skillLevels.put(skillName, level);
        recalculateAttackDamage();
        saveSkills(); // Save skills whenever they're updated
    }

    // Method to get the XP of a specific skill
    public int getSkillXP(String skillName) {
        return skillXP.getOrDefault(skillName, 0);
    }

    // Method to set the XP of a specific skill
    public void setSkillXP(String skillName, int xp) {
        skillXP.put(skillName, xp);
        saveSkills(); // Save skills whenever they're updated
    }

    // Method to get a specific skill from the SkillManager
    public Skill getSkill(String skillName) {
        return skillManager.getSkill(skillName); // This will call the getSkill method from SkillManager
    }

    // Recalculate attack damage based on skill levels
    private void recalculateAttackDamage() {
        double baseDamage = 100.0; // Example base damage
        int bladeMasteryLevel = getSkillLevel("Blade Mastery");
        int martialExpertiseLevel = getSkillLevel("Martial Expertise");
        int weaponMasteryLevel = getSkillLevel("Weapon Mastery");
        int dualWieldingLevel = getSkillLevel("Dual Wielding");
        int criticalStrikeLevel = getSkillLevel("Critical Strike");
        double criticalHitMultiplier = 2.0; // Example value

        double bladeMasteryDamage = baseDamage * (1 + 0.5 * bladeMasteryLevel / 100);
        double martialExpertiseDamage = baseDamage * (1 + 0.4 * martialExpertiseLevel / 100);
        double weaponMasteryDamage = baseDamage * (1 + 0.6 * weaponMasteryLevel / 100);
        double dualWieldingDamage = baseDamage * (1 + 0.35 * dualWieldingLevel / 100);
        double criticalStrikeDamage = baseDamage * criticalHitMultiplier * (1 + 0.75 * criticalStrikeLevel / 100);

        // Example of summing the bonuses
        this.attackDamage = baseDamage * (bladeMasteryDamage + martialExpertiseDamage + weaponMasteryDamage + dualWieldingDamage + criticalStrikeDamage);
    }

    // Save the player's skill levels and XP to the database using customId
    public void saveSkills() {
        // Use the DatabaseManager to save skills using customId
        DatabaseManager databaseManager = cerb.getInstance().getDatabaseManager();

        if (databaseManager != null && customId != null) {
            databaseManager.saveSkillsByCustomId(customId, skillLevels, skillXP);
        } else {
            System.err.println("[ERROR] Could not save skills for player: " + bukkitPlayer.getName() + " (customId or databaseManager is null)");
        }
    }

    // Method to set the current crafted item
    public void setCurrentCraftedItem(ItemStack item) {
        this.currentCraftedItem = item;
    }

    // Method to get the current crafted item
    public ItemStack getCurrentCraftedItem() {
        return currentCraftedItem;
    }

    // Getters and setters for other fields
    public UUID getUniqueId() {
        return uniqueId;
    }

    public Player getBukkitPlayer() {
        return bukkitPlayer;
    }

    public SkillManager getSkillManager() {
        return skillManager;
    }

    public double getMagicDamageMultiplier() {
        return magicDamageMultiplier;
    }

    public void setMagicDamageMultiplier(double magicDamageMultiplier) {
        this.magicDamageMultiplier = magicDamageMultiplier;
    }

    // This method retrieves the Bukkit Player object
    public Player getPlayer() {
        return bukkitPlayer;
    }

    public String getCustomId() {
        return customId;
    }

    // Methods to access the new managers
    public PlayerVirtualHealthManager getHealthManager() {
        return healthManager;
    }

    public PlayerDefenseManager getDefenseManager() {
        return defenseManager;
    }

    public PlayerStrengthManager getStrengthManager() {
        return strengthManager;
    }

    // Inner class to wrap custom attributes
    public class AttributeWrapper {
        private double baseValue;

        public AttributeWrapper(double baseValue) {
            this.baseValue = baseValue;
        }

        public double getBaseValue() {
            return baseValue;
        }

        public void setBaseValue(double baseValue) {
            this.baseValue = baseValue;
        }
    }
}

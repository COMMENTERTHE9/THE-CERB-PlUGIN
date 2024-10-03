package Skills;

import CustomEntities.SeaMonsterManager;
import GUIs.PlayerMenuGUI;
import Manager.*;
import Listener.CraftingListener;
import Manager.PlayerVirtualHealthManager;
import Manager.PlayerStrengthManager;
import org.bukkit.NamespacedKey;
import Traps.TrapManager;
import cerberus.world.cerb.CustomPlayer;
import cerberus.world.cerb.DatabaseManager;
import cerberus.world.cerb.cerb;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SkillManager {
    private final cerb plugin; // Reference to main plugin class
    private final Map<String, Skill> skills; // Holds all player skills
    private final Map<String, Integer> skillLevels; // Skill levels for each skill
    private final Map<String, Integer> skillXP; // XP for each skill
    private final Inventory skillMenu; // Inventory GUI for skills
    private final DatabaseManager databaseManager;
    private final String customId; // Use customId instead of playerUUID
    private final Map<UUID, CustomPlayer> customPlayers; // This will hold CustomPlayer instances
    private final SeaMonsterManager seaMonsterManager; // Custom manager example
    private final Map<String, Map<String, Integer>> playerSkillLevels = new HashMap<>();
    private final Map<String, Map<String, Integer>> playerSkillXP = new HashMap<>();

    // Other managers
    private final PlayerVirtualHealthManager virtualHealthManager;
    private PlayerDefenseManager playerDefenseManager;
    private PlayerManaManager playerManaManager;
    private ResourceYieldManager resourceYieldManager;
    private TrapManager trapManager;
    private LuckManager luckManager;
    private MagicFindManager magicFindManager;
    private final CraftingManager craftingManager;
    private final CraftingListener craftingListener; // Added craftingListener dependency
    private final PlayerStrengthManager strengthManager; // Added strengthManager dependency
    private PlayerMenuGUI playerMenuGUI; // New PlayerMenuGUI instance

    public SkillManager(cerb plugin, Map<String, Integer> skillLevels, Map<String, Integer> skillXP, Inventory skillMenu,
                        DatabaseManager databaseManager, UUID playerUUID, PlayerVirtualHealthManager playerVirtualHealthManager,
                        PlayerDefenseManager playerDefenseManager, CraftingManager craftingManager, CraftingListener craftingListener,
                        PlayerStrengthManager strengthManager, SeaMonsterManager seaMonsterManager) {
        this.plugin = plugin;
        this.skills = new HashMap<>();
        this.databaseManager = databaseManager;

        // Generate or retrieve the custom identifier for this player
        this.customId = databaseManager.getOrCreateCustomId(playerUUID, "player_skills"); // <-- Get or create customId

        // Load skills from the database using customId
        this.skillLevels = databaseManager.loadSkillLevelsByCustomId(this.customId);  // <-- Load skill levels
        this.skillXP = databaseManager.loadSkillXPByCustomId(this.customId);  // <-- Load skill XP

        // Debug messages for loading skills
        System.out.println("[DEBUG] Loaded skill levels for custom_id " + this.customId + ": " + this.skillLevels);
        System.out.println("[DEBUG] Loaded skill XP for custom_id " + this.customId + ": " + this.skillXP);

        this.skillMenu = skillMenu;
        this.customPlayers = new HashMap<>(); // Initialize customPlayers map
        this.virtualHealthManager = playerVirtualHealthManager;
        this.playerDefenseManager = playerDefenseManager;
        this.craftingManager = craftingManager;
        this.craftingListener = craftingListener;
        this.strengthManager = strengthManager;
        this.seaMonsterManager = seaMonsterManager;

        // Initialize the PlayerMenuGUI here
        this.playerMenuGUI = new PlayerMenuGUI(plugin, this); // Initialize PlayerMenuGUI

        initializeSkills(); // Initialize all skills
    }

    public void addPlayer(Player player) {
        UUID playerUUID = player.getUniqueId();

        if (!customPlayers.containsKey(playerUUID)) {
            // Load the player's skill levels and XP from the database
            Map<String, Integer> skillLevels = databaseManager.loadSkillLevelsByCustomId(this.customId);
            Map<String, Integer> skillXP = databaseManager.loadSkillXPByCustomId(this.customId);

            // Set these values in the SkillManager
            this.skillLevels.putAll(skillLevels);
            this.skillXP.putAll(skillXP);

            CustomPlayer customPlayer = new CustomPlayer(
                    player,
                    this,
                    virtualHealthManager,
                    playerDefenseManager,
                    strengthManager
            );
            customPlayers.put(playerUUID, customPlayer); // Add the player to the map
            System.out.println("CustomPlayer created and skills loaded for custom_id: " + this.customId);
        } else {
            System.out.println("CustomPlayer already exists for custom_id: " + this.customId);
        }
    }


    // Remove a custom player from the map
    public void removePlayer(UUID playerUUID) {
        customPlayers.remove(playerUUID);  // Remove player data when they leave the server
    }

    // Setters for additional managers
    public void setPlayerManaManager(PlayerManaManager playerManaManager) {
        this.playerManaManager = playerManaManager;
    }

    public void setResourceYieldManager(ResourceYieldManager resourceYieldManager) {
        this.resourceYieldManager = resourceYieldManager;
    }

    public void setTrapManager(TrapManager trapManager) {
        this.trapManager = trapManager;
    }

    public void setLuckManager(LuckManager luckManager) {
        this.luckManager = luckManager;
    }

    public void setMagicFindManager(MagicFindManager magicFindManager) {
        this.magicFindManager = magicFindManager;
    }

    // Initialize skills
    private void initializeSkills() {
        // Combat skills
        skills.put("Blade Mastery", new BladeMasterySkill("Blade Mastery", virtualHealthManager));
        skills.put("Martial Expertise", new MartialExpertiseSkill("Martial Expertise"));
        skills.put("Weapon Mastery", new WeaponMasterySkill("Weapon Mastery", virtualHealthManager));
        skills.put("Ranged Precision", new RangedPrecisionSkill("Ranged Precision"));
        skills.put("Heavy Armor Training", new HeavyArmorTrainingSkill("Heavy Armor Training", virtualHealthManager, playerDefenseManager));
        skills.put("Dual Wielding", new DualWieldingSkill("Dual Wielding"));
        skills.put("Critical Strike", new CriticalStrikeSkill("Critical Strike"));

        // Magic skills
        if (playerManaManager != null) {
            skills.put("Intelligence", new IntelligenceSkill("Intelligence", playerManaManager));
            skills.put("Arcane Knowledge", new ArcaneKnowledgeSkill("Arcane Knowledge", playerManaManager, databaseManager, plugin));
            skills.put("Elemental Mastery", new ElementalMasterySkill("Elemental Mastery", playerManaManager, plugin.getSpellManager()));
            skills.put("Summoning", new SummoningSkill("Summoning", playerManaManager, cerb.getInstance()));
            skills.put("Spell Weaving", new SpellWeavingSkill("Spell Weaving", playerManaManager, plugin));
            skills.put("Mana Regeneration", new ManaRegenerationSkill("Mana Regeneration", playerManaManager));
            skills.put("Defensive Magic", new DefensiveMagicSkill("Defensive Magic", playerManaManager, cerb.getInstance()));
        }

        // Utility skills
        if (resourceYieldManager != null) {
            skills.put("Mining", new MiningSkill("Mining", resourceYieldManager));
            skills.put("Farming", new FarmingSkill("Farming", resourceYieldManager));
            skills.put("Woodcutting", new WoodcuttingSkill("Woodcutting", resourceYieldManager));
            skills.put("Fishing", new FishingSkill("Fishing", resourceYieldManager, seaMonsterManager, plugin));
        }

        skills.put("Crafting", new CraftingSkill("Crafting", craftingManager, craftingListener));
        skills.put("Smithing", new SmithingSkill("Smithing", craftingManager, virtualHealthManager, strengthManager));
        skills.put("Alchemy", new AlchemySkill("Alchemy", craftingManager, playerManaManager));
        skills.put("Enchanting", new EnchantingSkill("Enchanting", craftingManager, playerManaManager));
        skills.put("Herbalism", new HerbalismSkill("Herbalism", craftingManager, playerManaManager));
        skills.put("Cooking", new CookingSkill("Cooking", craftingManager, virtualHealthManager));
        skills.put("First Aid", new FirstAidSkill("First Aid", virtualHealthManager, craftingManager));
        skills.put("Stealth", new StealthSkill("Stealth", craftingManager, new NamespacedKey(plugin, "stealth_item")));

        if (trapManager != null) {
            skills.put("Trap Mastery", new TrapMasterySkill("Trap Mastery", trapManager));
        }
        if (luckManager != null && magicFindManager != null) {
            skills.put("Scavenging", new ScavengingSkill("Scavenging", luckManager, magicFindManager, plugin)); // Pass 'plugin' instance
        }
        skills.put("Repairing", new RepairingSkill("Repairing", craftingManager));
        skills.put("Trading", new TradingSkill("Trading", craftingManager));
        skills.put("Navigation", new NavigationSkill("Navigation", craftingManager, this));
        skills.put("Animal Taming", new AnimalTamingSkill("Animal Taming", craftingManager));
        skills.put("Riding", new RidingSkill("Riding", craftingManager));
        skills.put("Lockpicking", new LockpickingSkill("Lockpicking", luckManager));
        skills.put("Survival", new SurvivalSkill("Survival", playerDefenseManager, craftingManager)); // Pass 'craftingManager' as well
    }

    // Retrieve skill by name
    public Skill getSkill(String skillName) {
        return skills.get(skillName);
    }

    // Add experience to a skill
    public void addExperience(String skillName, double amount) {
        addExperience(skillName, amount, 0, 0, false, false);
    }

    // Add experience to a skill (advanced)
    public void addExperience(String skillName, double amount, double health, double damage, boolean isRare, boolean isBoss) {
        Skill skill = skills.get(skillName);
        if (skill != null) {
            skill.addExperience(amount, health, damage, isRare, isBoss);
            skillLevels.put(skillName, skill.getLevel());
            skillXP.put(skillName, (int) skill.getExperience());
            saveSkills();
            refreshSkillMenu();
        }
    }

    private void saveSkills() {
        databaseManager.saveSkillsByCustomId(this.customId, skillLevels, skillXP);
    }

    // Calculate dynamic XP based on enemy properties
    public double calculateDynamicXp(double health, double damage, boolean isRare, boolean isBoss) {
        double baseXp = 0;
        double healthFactor = health * 0.50;
        double damageFactor = damage * 0.50;
        double rarityFactor = isRare ? 2.0 : 1.0;
        double bossFactor = isBoss ? 5.0 : 1.0;

        return baseXp + healthFactor + damageFactor * rarityFactor * bossFactor;
    }

    // Add XP for killing a mob
    public void addXpForMobKill(String skillName, double health, double damage, boolean isRare, boolean isBoss) {
        double xp = calculateDynamicXp(health, damage, isRare, isBoss);
        addExperience(skillName, xp, health, damage, isRare, isBoss);
    }

    // Get total effect multiplier for multiple skills
    public double getTotalEffectMultiplier(String... skillNames) {
        double totalMultiplier = 1.0;
        for (String skillName : skillNames) {
            Skill skill = skills.get(skillName);
            if (skill != null) {
                totalMultiplier *= skill.getEffectMultiplier();
            }
        }
        return totalMultiplier;
    }

    // Add a setter for PlayerMenuGUI
    public void setPlayerMenuGUI(PlayerMenuGUI playerMenuGUI) {
        this.playerMenuGUI = playerMenuGUI;
    }

    // Add a getter for PlayerMenuGUI
    public PlayerMenuGUI getPlayerMenu() {
        return playerMenuGUI;
    }

    // Refresh the skill menu in the inventory
    public void refreshSkillMenu() {
        int slot = 0;
        for (Map.Entry<String, Integer> entry : skillLevels.entrySet()) {
            skillMenu.setItem(slot++, createSkillItem(entry.getKey(), entry.getValue(), skillXP.getOrDefault(entry.getKey(), 0)));
        }
    }

    // Create a skill item in the inventory (placeholder logic)
    private ItemStack createSkillItem(String skillName, int level, int xp) {
        // Create ItemStack based on skill name, level, and XP
        return new ItemStack(Material.BOOK); // Placeholder, replace with actual item logic
    }

    // Add XP and check if the player levels up
    public boolean addXpAndCheckLevelUp(String skillName, double xpGained, Player player) {
        int currentLevel = skillLevels.getOrDefault(skillName, 1);
        int currentXP = skillXP.getOrDefault(skillName, 0);

        // Add the gained XP
        int newXP = currentXP + (int) xpGained;

        // Calculate XP needed for next level
        int requiredXPForNextLevel = calculateRequiredXpForNextLevel(currentLevel);

        boolean leveledUp = false;

        if (newXP >= requiredXPForNextLevel) {
            // Handle level-up and carry over XP
            int excessXP = newXP - requiredXPForNextLevel;
            skillLevels.put(skillName, currentLevel + 1);
            skillXP.put(skillName, excessXP);

            if (isMaxLevel(skillName, currentLevel + 1)) {
                skillXP.put(skillName, 0); // Reset XP at max level
            }

            saveSkills();
            refreshSkillDisplay(player);
            leveledUp = true;

            // Send level-up message
            String levelUpMessage = createLevelUpMessage(skillName, currentLevel + 1);
            player.sendMessage(levelUpMessage);
        } else {
            skillXP.put(skillName, newXP);
            saveSkills();
        }

        return leveledUp;
    }

    // Check if the skill has reached max level
    public boolean isMaxLevel(String skillName, int newLevel) {
        int maxLevel = 100;  // Define the max level for your skills
        return newLevel >= maxLevel;
    }

    // Refresh the player's skill display in GUI or HUD
    public void refreshSkillDisplay(Player player) {
        PlayerMenuGUI gui = new PlayerMenuGUI(plugin, this);
        gui.updatePlayerSkillInfo(player);
    }

    // Create a level-up message to notify the player
    public String createLevelUpMessage(String skillName, int level) {
        return "Congratulations! Your " + skillName + " skill has reached level " + level + "!";
    }

    public int calculateRequiredXpForNextLevel(int currentLevel) {
        int currentLevelXP = 100;
        int printCounter = 0; // Add a counter to limit the output frequency

        // Loop to calculate the XP for the current level using your formula
        for (int i = 1; i < currentLevel; i++) {
            currentLevelXP = currentLevelXP * 2;  // Double the XP required at each level
            printCounter++;

        }

        return currentLevelXP;
    }

    // Method to get XP for a specific skill
    public int getSkillXP(String skillName) {
        return skillXP.getOrDefault(skillName, 0);
    }

    // Method to set XP for a specific skill
    public void setSkillXP(String skillName, int xp) {
        skillXP.put(skillName, xp);
    }

    // Upgrade a skill (example logic)
    public void upgradeSkill(Player player, String skillName) {
        addExperience(skillName, 10, 100, 50, false, false); // Example XP increment
        saveSkills();
    }

    // Get the level of a specific skill
    public int getSkillLevel(String skillName) {
        return skillLevels.getOrDefault(skillName, 1); // Default to level 1 if skill not found
    }

    // Retrieve all skills
    public Map<String, Skill> getSkills() {
        return skills;
    }

    // Get a custom player by UUID
    public CustomPlayer getCustomPlayer(UUID playerUUID) {
        return customPlayers.get(playerUUID);
    }

    // Add a custom player to the manager
    public void addCustomPlayer(CustomPlayer customPlayer) {
        customPlayers.put(customPlayer.getUniqueId(), customPlayer);
    }

    @Override
    public String toString() {
        return null; // Placeholder
    }

    public void setPlayerDefenseManager(PlayerDefenseManager playerDefenseManager) {
        this.playerDefenseManager = playerDefenseManager;
    }

    // Method to get the player's skill levels
    public Map<String, Integer> getPlayerSkillLevels(Player player) {
        String customId = databaseManager.getOrCreateCustomId(player.getUniqueId(), player.getName());
        return playerSkillLevels.getOrDefault(customId, new HashMap<>());
    }

    // Method to get the player's skill XP
    public Map<String, Integer> getPlayerSkillXP(Player player) {
        String customId = databaseManager.getOrCreateCustomId(player.getUniqueId(), player.getName());
        return playerSkillXP.getOrDefault(customId, new HashMap<>());
    }

    // Set player skills manually (for testing or other purposes)
    public void setPlayerSkills(Player player, Map<String, Integer> skillLevels, Map<String, Integer> skillXP) {
        String customId = databaseManager.getOrCreateCustomId(player.getUniqueId(), player.getName());
        playerSkillLevels.put(customId, skillLevels);
        playerSkillXP.put(customId, skillXP);
    }

    public void loadPlayerSkills(Player player) {
        String customId = databaseManager.getOrCreateCustomId(player.getUniqueId(), player.getName());

        // Load from the database and populate the maps
        Map<String, Integer> skillLevels = databaseManager.loadSkillLevelsByCustomId(customId);
        Map<String, Integer> skillXP = databaseManager.loadSkillXPByCustomId(customId);

        // Store in memory for quick access
        playerSkillLevels.put(customId, skillLevels);
        playerSkillXP.put(customId, skillXP);
    }}

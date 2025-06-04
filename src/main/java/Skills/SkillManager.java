package Skills;

import CustomEntities.SeaMonsterManager;
import GUIs.PlayerMenuGUI;
import Manager.*;
import Listener.CraftingListener;
import Manager.PlayerVirtualHealthManager;
import java.util.UUID;
import Manager.PlayerStrengthManager;
import org.bukkit.NamespacedKey;
import Traps.TrapManager;
import cerberus.world.cerb.CustomPlayer;
import cerberus.world.cerb.DatabaseManager;
import cerberus.world.cerb.CerberusPlugin; // Use CerberusPlugin instead of cerb
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SkillManager {
    private final CerberusPlugin plugin; // Changed from cerb to CerberusPlugin
    private final AsyncSaveManager asyncSaver;
    private static String url;
    private final Map<String, Skill> skills;
    private final Map<String, Integer> skillLevels;
    private final Map<String, Integer> skillXP;
    private final Inventory skillMenu;
    private final DatabaseManager databaseManager;
    private final Map<UUID, CustomPlayer> customPlayers;
    private final SeaMonsterManager seaMonsterManager;
    private final Map<UUID, Map<String,Integer>> playerSkillLevels = new HashMap<>();
    private final Map<UUID, Map<String,Integer>> playerSkillXP     = new HashMap<>();

    // Other managers
    private final PlayerVirtualHealthManager virtualHealthManager;
    private PlayerDefenseManager playerDefenseManager;
    private PlayerManaManager playerManaManager; // Set this later with a setter if not provided in constructor
    private ResourceYieldManager resourceYieldManager;
    private TrapManager trapManager;
    private LuckManager luckManager;
    private MagicFindManager magicFindManager;
    private final CraftingManager craftingManager;
    private final CraftingListener craftingListener;
    private final PlayerStrengthManager strengthManager;
    private PlayerMenuGUI playerMenuGUI;

    public SkillManager(CerberusPlugin plugin,
                        Map<String, Integer> skillLevels,
                        Map<String, Integer> skillXP,
                        Inventory skillMenu,
                        DatabaseManager databaseManager,
                        UUID playerUUID,
                        PlayerVirtualHealthManager playerVirtualHealthManager,
                        PlayerDefenseManager playerDefenseManager,
                        CraftingManager craftingManager,
                        CraftingListener craftingListener,
                        PlayerStrengthManager strengthManager,
                        SeaMonsterManager seaMonsterManager) {

        this.asyncSaver = plugin.getAsyncSaver();  // ← initialize it here
        this.plugin = plugin;
        this.skills = new HashMap<>();
        this.databaseManager = databaseManager;

        UUID uuid = playerUUID;
        this.skillLevels = databaseManager.loadPlayerSkillLevels(uuid);
        this.skillXP = databaseManager.loadPlayerSkillXP(uuid);

        System.out.println("[DEBUG] Loaded skill levels for player " + uuid + ": " + this.skillLevels);
        System.out.println("[DEBUG] Loaded skill XP     for player " + uuid + ": " + this.skillXP);


        this.skillMenu = skillMenu;
        this.customPlayers = new HashMap<>();
        this.virtualHealthManager = playerVirtualHealthManager;
        this.playerDefenseManager = playerDefenseManager;
        this.craftingManager = craftingManager;
        this.craftingListener = craftingListener;
        this.strengthManager = strengthManager;
        this.seaMonsterManager = seaMonsterManager;

        // Initialize the PlayerMenuGUI here (if needed)
        this.playerMenuGUI = new PlayerMenuGUI(plugin, this);

        initializeSkills();
    }

    public String getCustomId(Player player) {
        return databaseManager.getOrCreateCustomId(player.getUniqueId(), player.getName());
    }


    public void removePlayer(UUID playerUUID) {
        customPlayers.remove(playerUUID);
    }

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

    private void initializeSkills() {
        // Combat skills
        skills.put("Blade Mastery", new BladeMasterySkill("Blade Mastery", virtualHealthManager));
        skills.put("Martial Expertise", new MartialExpertiseSkill("Martial Expertise"));
        skills.put("Weapon Mastery", new WeaponMasterySkill("Weapon Mastery", virtualHealthManager));
        skills.put("Ranged Precision", new RangedPrecisionSkill("Ranged Precision"));
        skills.put("Heavy Armor Training", new HeavyArmorTrainingSkill("Heavy Armor Training", virtualHealthManager, playerDefenseManager));
        skills.put("Dual Wielding", new DualWieldingSkill("Dual Wielding"));
        skills.put("Critical Strike", new CriticalStrikeSkill("Critical Strike"));

        // Magic skills (only if playerManaManager is available)
        if (playerManaManager != null) {
            skills.put("Intelligence", new IntelligenceSkill("Intelligence", playerManaManager));
            skills.put("Arcane Knowledge", new ArcaneKnowledgeSkill("Arcane Knowledge", playerManaManager, databaseManager, plugin));
            skills.put("Elemental Mastery", new ElementalMasterySkill("Elemental Mastery", playerManaManager, plugin.getSpellManager()));
            skills.put("Summoning", new SummoningSkill("Summoning", playerManaManager, plugin));
            skills.put("Spell Weaving", new SpellWeavingSkill("Spell Weaving", playerManaManager, plugin));
            skills.put("Mana Regeneration", new ManaRegenerationSkill("Mana Regeneration", playerManaManager));
            skills.put("Defensive Magic", new DefensiveMagicSkill("Defensive Magic", playerManaManager, plugin));
        }

        // Utility skills (only if resourceYieldManager is available)
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
            skills.put("Scavenging", new ScavengingSkill("Scavenging", luckManager, magicFindManager, plugin));
        }

        skills.put("Repairing", new RepairingSkill("Repairing", craftingManager));
        skills.put("Trading", new TradingSkill("Trading", craftingManager));
        skills.put("Navigation", new NavigationSkill("Navigation", craftingManager, this));
        skills.put("Animal Taming", new AnimalTamingSkill("Animal Taming", craftingManager));
        skills.put("Riding", new RidingSkill("Riding", craftingManager));
        skills.put("Lockpicking", new LockpickingSkill("Lockpicking", luckManager));
        skills.put("Survival", new SurvivalSkill("Survival", playerDefenseManager, craftingManager));
    }

    public Skill getSkill(String skillName) {
        return skills.get(skillName);
    }

    public void addExperience(Player player, String skillName, double amount) {
        addExperience(
                player,
                skillName,
                amount,
                0.0,    // health
                0.0,    // damage
                false,  // isRare
                false   // isBoss
        );
    }

    public void addExperience(
            Player player,                   // ← now we pass in the player
            String skillName,
            double amount, double health,
            double damage, boolean isRare,
            boolean isBoss
    ) {
        Skill skill = skills.get(skillName);
        if (skill != null) {
            skill.addExperience(amount, health, damage, isRare, isBoss);
            skillLevels.put(skillName, skill.getLevel());
            skillXP.put(skillName, (int) skill.getExperience());

            saveSkills(player);            // ← call the one that takes a player
            refreshSkillDisplay(player);   // ← ditto
        }
    }


    private void saveSkills(Player player) {
        databaseManager.savePlayerSkills(
                player.getUniqueId(),
                player.getName(),
                skillLevels,
                skillXP
        );
    }


    public double calculateDynamicXp(double health, double damage, boolean isRare, boolean isBoss) {
        double baseXp = 0;
        double healthFactor = health * 0.50;
        double damageFactor = damage * 0.50;
        double rarityFactor = isRare ? 2.0 : 1.0;
        double bossFactor = isBoss ? 5.0 : 1.0;

        return baseXp + healthFactor + damageFactor * rarityFactor * bossFactor;
    }

    public void addXpForMobKill(Player player,
                                String skillName,
                                double health,
                                double damage,
                                boolean isRare,
                                boolean isBoss) {
        double xp = calculateDynamicXp(health, damage, isRare, isBoss);
        addExperience(player, skillName, xp, health, damage, isRare, isBoss);
    }

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

    public void setPlayerMenuGUI(PlayerMenuGUI playerMenuGUI) {
        this.playerMenuGUI = playerMenuGUI;
    }

    public PlayerMenuGUI getPlayerMenu() {
        return playerMenuGUI;
    }

    public void refreshSkillMenu() {
        int slot = 0;
        for (Map.Entry<String, Integer> entry : skillLevels.entrySet()) {
            skillMenu.setItem(slot++, createSkillItem(entry.getKey(), entry.getValue(), skillXP.getOrDefault(entry.getKey(), 0)));
        }
    }

    private ItemStack createSkillItem(String skillName, int level, int xp) {
        // Placeholder logic
        return new ItemStack(Material.BOOK);
    }

    public boolean addXpAndCheckLevelUp(String skillName, double xpGained, Player player) {
        int currentLevel = skillLevels.getOrDefault(skillName, 1);
        int currentXP = skillXP.getOrDefault(skillName, 0);

        int newXP = currentXP + (int) xpGained;
        int requiredXPForNextLevel = calculateRequiredXpForNextLevel(currentLevel);

        boolean leveledUp = false;

        if (newXP >= requiredXPForNextLevel) {
            int excessXP = newXP - requiredXPForNextLevel;
            skillLevels.put(skillName, currentLevel + 1);
            skillXP.put(skillName, excessXP);

            if (isMaxLevel(skillName, currentLevel + 1)) {
                skillXP.put(skillName, 0);
            }

            saveSkills(player);
            refreshSkillDisplay(player);
            leveledUp = true;
            player.sendMessage(createLevelUpMessage(skillName, currentLevel + 1));
        } else {
            skillXP.put(skillName, newXP);
            saveSkills(player);
        }

        return leveledUp;
    }

    public boolean isMaxLevel(String skillName, int newLevel) {
        int maxLevel = 100;
        return newLevel >= maxLevel;
    }

    public void refreshSkillDisplay(Player player) {
        PlayerMenuGUI gui = new PlayerMenuGUI(plugin, this);
        gui.updatePlayerSkillInfo(player);
    }

    public String createLevelUpMessage(String skillName, int level) {
        return "Congratulations! Your " + skillName + " skill has reached level " + level + "!";
    }

    public int calculateRequiredXpForNextLevel(int currentLevel) {
        int currentLevelXP = 100;
        for (int i = 1; i < currentLevel; i++) {
            currentLevelXP = currentLevelXP * 2;
        }
        return currentLevelXP;
    }

    public int getSkillXP(String skillName) {
        return skillXP.getOrDefault(skillName, 0);
    }

    public void setSkillXP(String skillName, int xp) {
        skillXP.put(skillName, xp);
    }

    public void upgradeSkill(Player player, String skillName) {
        // pass the player into addExperience
        addExperience(player, skillName, 10, 100, 50, false, false);
        saveSkills(player);
    }

    public int getSkillLevel(String skillName) {
        return skillLevels.getOrDefault(skillName, 1);
    }

    public Map<String, Skill> getSkills() {
        return skills;
    }

    public void savePlayerSkills(Player player) {
        UUID id = player.getUniqueId();
        databaseManager.savePlayerSkills(
                id,
                player.getName(),
                playerSkillLevels.getOrDefault(id, Map.of()),
                playerSkillXP    .getOrDefault(id, Map.of())
        );
    }

    @Override
    public String toString() {
        return null;
    }

    public void setPlayerDefenseManager(PlayerDefenseManager playerDefenseManager) {
        this.playerDefenseManager = playerDefenseManager;
    }

    public Map<String,Integer> getPlayerSkillLevels(Player player) {
        UUID uid = player.getUniqueId();
        return playerSkillLevels.getOrDefault(uid, new HashMap<>());
    }

    public Map<String,Integer> getPlayerSkillXP(Player player) {
        UUID uid = player.getUniqueId();
        return playerSkillXP.getOrDefault(uid, new HashMap<>());
    }

    public void setPlayerSkills(Player player, Map<String,Integer> skillLevels, Map<String,Integer> skillXP) {
        UUID uid = player.getUniqueId();
        playerSkillLevels.put(uid, skillLevels);
        playerSkillXP    .put(uid, skillXP);
    }

    public int getPlayerLevel(Player player) {
        Map<String, Integer> levels = getPlayerSkillLevels(player);  // method already exists
        int total = 0;
        for (int lvl : levels.values()) total += lvl;
        return total;
    }

    public void loadPlayerSkills(Player player) {
        UUID playerUUID = player.getUniqueId();

        // Load by UUID, not by customId
        Map<String, Integer> skillLevels = databaseManager.loadPlayerSkillLevels(playerUUID);
        Map<String, Integer> skillXP = databaseManager.loadPlayerSkillXP(playerUUID);

        // Store them keyed off the UUID (or however your maps are keyed now)
        playerSkillLevels.put(playerUUID, skillLevels);
        playerSkillXP.put(playerUUID, skillXP);
    }
}
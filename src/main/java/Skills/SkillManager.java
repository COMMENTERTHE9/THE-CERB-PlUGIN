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
import java.util.concurrent.ConcurrentHashMap;

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
    private final Map<UUID, Map<String,Integer>> playerSkillLevels = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String,Integer>> playerSkillXP     = new ConcurrentHashMap<>();

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


    /** Lookup a CustomPlayer by UUID — delegates to static registry. */
    public CustomPlayer getCustomPlayer(UUID uuid) {
        return CustomPlayer.get(uuid);
    }

    /** Lookup a CustomPlayer by their custom-id string (iterates registry). */
    public CustomPlayer getCustomPlayerByCustomId(String customId) {
        // customId is derived from UUID via DatabaseManager, so iterate and match
        for (Map.Entry<UUID, CustomPlayer> entry : customPlayers.entrySet()) {
            CustomPlayer cp = entry.getValue();
            if (cp != null) {
                String id = databaseManager.getOrCreateCustomId(cp.getUuid(), cp.getBukkit().getName());
                if (id != null && id.equals(customId)) {
                    return cp;
                }
            }
        }
        // Fallback: check static registry
        // The customPlayers map might be empty — use the static CustomPlayer registry
        return null;
    }

    public void removePlayer(UUID playerUUID) {
        customPlayers.remove(playerUUID);
        playerSkillLevels.remove(playerUUID);
        playerSkillXP.remove(playerUUID);
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
        skills.put("Martial Expertise", new MartialExpertiseSkill("Martial Expertise", plugin));
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
            skills.put("Defensive Magic", new DefensiveMagicSkill("Defensive Magic", playerManaManager, plugin, virtualHealthManager));
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
            Player player,
            String skillName,
            double amount, double health,
            double damage, boolean isRare,
            boolean isBoss
    ) {
        UUID uid = player.getUniqueId();
        Map<String, Integer> levels = playerSkillLevels.computeIfAbsent(uid, k -> new ConcurrentHashMap<>());
        Map<String, Integer> xp = playerSkillXP.computeIfAbsent(uid, k -> new ConcurrentHashMap<>());

        int currentLevel = levels.getOrDefault(skillName, 1);
        int currentXP = xp.getOrDefault(skillName, 0);
        int newXP = currentXP + (int) amount;
        int reqXP = calculateRequiredXpForNextLevel(currentLevel);

        if (newXP >= reqXP && !isMaxLevel(skillName, currentLevel + 1)) {
            levels.put(skillName, currentLevel + 1);
            xp.put(skillName, newXP - reqXP);
            player.sendMessage(createLevelUpMessage(skillName, currentLevel + 1));
        } else {
            xp.put(skillName, newXP);
        }

        // Also sync into the Skill object for legacy code that reads skill.getLevel()
        Skill skill = skills.get(skillName);
        if (skill != null) {
            skill.setLevel(levels.getOrDefault(skillName, 1));
        }

        saveSkills(player);
    }

    private void saveSkills(Player player) {
        UUID uid = player.getUniqueId();
        databaseManager.savePlayerSkills(
                uid,
                player.getName(),
                playerSkillLevels.getOrDefault(uid, Map.of()),
                playerSkillXP.getOrDefault(uid, Map.of())
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

    /** Base XP from health + damage only — no classification multiplier. */
    private double calculateBaseXp(double health, double damage) {
        double healthFactor = health * 0.50;
        double damageFactor = damage * 0.50;
        return healthFactor + damageFactor;
    }

    /** MobContext-aware XP calculation — applies ONLY the config multiplier, no double-mult. */
    public double calculateDynamicXp(double health, double damage, MobContext ctx) {
        return calculateBaseXp(health, damage) * ctx.xpMultiplier();
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

    /** MobContext-aware kill XP — uses config multiplier, no double-mult bug. */
    public void addXpForMobKill(Player player, String skillName,
                                double health, double damage, MobContext ctx) {
        double xp = calculateDynamicXp(health, damage, ctx);
        addExperience(player, skillName, xp, health, damage, ctx.isRare(), ctx.isBoss());
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
        // Legacy no-op — GUI refreshes on reopen
    }

    private ItemStack createSkillItem(String skillName, int level, int xp) {
        // Placeholder logic
        return new ItemStack(Material.BOOK);
    }

    public boolean addXpAndCheckLevelUp(String skillName, double xpGained, Player player) {
        UUID uid = player.getUniqueId();
        Map<String, Integer> levels = playerSkillLevels.computeIfAbsent(uid, k -> new ConcurrentHashMap<>());
        Map<String, Integer> xp = playerSkillXP.computeIfAbsent(uid, k -> new ConcurrentHashMap<>());

        int currentLevel = levels.getOrDefault(skillName, 1);
        int currentXP = xp.getOrDefault(skillName, 0);
        int newXP = currentXP + (int) xpGained;
        int requiredXP = calculateRequiredXpForNextLevel(currentLevel);

        boolean leveledUp = false;

        if (newXP >= requiredXP) {
            int excessXP = newXP - requiredXP;
            int newLevel = currentLevel + 1;
            levels.put(skillName, newLevel);
            xp.put(skillName, isMaxLevel(skillName, newLevel) ? 0 : excessXP);

            // Sync into Skill object for legacy code
            Skill skill = skills.get(skillName);
            if (skill != null) skill.setLevel(newLevel);

            leveledUp = true;
            player.sendMessage(createLevelUpMessage(skillName, newLevel));
        } else {
            xp.put(skillName, newXP);
        }

        saveSkills(player);
        return leveledUp;
    }

    public boolean isMaxLevel(String skillName, int newLevel) {
        int maxLevel = 100;
        return newLevel >= maxLevel;
    }

    public void refreshSkillDisplay(Player player) {
        // GUI refreshes on reopen — no live push needed
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

    public int getSkillXP(Player player, String skillName) {
        Map<String, Integer> xp = playerSkillXP.get(player.getUniqueId());
        return xp != null ? xp.getOrDefault(skillName, 0) : 0;
    }

    public void setSkillXP(String skillName, int xp) {
        skillXP.put(skillName, xp);
    }

    public void upgradeSkill(Player player, String skillName) {
        addExperience(player, skillName, 10);
    }

    /**
     * Get skill level for the LAST active player (legacy — used by skills that
     * don't have a player reference). Returns 1 if no data.
     * Prefer getSkillLevel(Player, String) when possible.
     */
    public int getSkillLevel(String skillName) {
        // Legacy fallback: check flat map, then return default
        return skillLevels.getOrDefault(skillName, 1);
    }

    /** Get a specific player's level for a skill. */
    public int getSkillLevel(Player player, String skillName) {
        Map<String, Integer> levels = playerSkillLevels.get(player.getUniqueId());
        return levels != null ? levels.getOrDefault(skillName, 1) : 1;
    }

    public Map<String, Skill> getSkills() {
        return skills;
    }

    public void savePlayerSkills(Player player) {
        saveSkills(player);
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

        // Sync Skill objects so CombatSkillProcessor reads correct levels
        for (Map.Entry<String, Integer> entry : skillLevels.entrySet()) {
            Skill skill = skills.get(entry.getKey());
            if (skill != null) {
                skill.setLevel(entry.getValue());
            }
        }
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
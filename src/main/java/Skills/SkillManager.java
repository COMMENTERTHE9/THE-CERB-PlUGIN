package Skills;

import Manager.*;
import Listener.CraftingListener;
import Manager.PlayerVirtualHealthManager;
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
    private CraftingManager craftingManager;
    private final CraftingListener craftingListener;
    private final PlayerStrengthManager strengthManager;

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
                        PlayerStrengthManager strengthManager) {

        this.asyncSaver = plugin.getAsyncSaver();  // ← initialize it here
        this.plugin = plugin;
        this.skills = new HashMap<>();
        this.databaseManager = databaseManager;

        UUID uuid = playerUUID;
        this.skillLevels = databaseManager.loadPlayerSkillLevels(uuid);
        this.skillXP = databaseManager.loadPlayerSkillXP(uuid);


        this.skillMenu = skillMenu;
        this.customPlayers = new HashMap<>();
        this.virtualHealthManager = playerVirtualHealthManager;
        this.playerDefenseManager = playerDefenseManager;
        this.craftingManager = craftingManager;
        this.craftingListener = craftingListener;
        this.strengthManager = strengthManager;

        rebuildSkillRegistry();
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
        levelListener.unloaded(playerUUID);
    }

    /** Told when a player's skill levels are loaded, change, or are unloaded (quit). Any thread. */
    public interface LevelListener {
        void loaded(UUID player);
        void changed(UUID player);
        void unloaded(UUID player);
    }

    private static final LevelListener NO_LISTENER = new LevelListener() {
        public void loaded(UUID player) {}
        public void changed(UUID player) {}
        public void unloaded(UUID player) {}
    };
    private volatile LevelListener levelListener = NO_LISTENER;

    /** Every XP award that added something: the real-time XP display (Skills.XpDisplay). Called on the awarding thread. */
    public interface XpListener {
        void gained(Player player, String skill, long amount, SkillProgression.Result after);

        /** {@code player} did something that trains {@code skill} and it paid nothing on purpose, {@code why} ("in creative mode"). */
        default void withheld(Player player, String skill, String why) {}
    }

    private volatile XpListener xpListener;

    public void setXpListener(XpListener listener) {
        this.xpListener = listener;
    }

    /**
     * An action that trains {@code skill} paid nothing on purpose (creative mode, a mob from a spawn egg): the XP stays
     * withheld and the XP display says why, so "no progress" never looks like a fault. The rules stay where they are
     * (Manager.XpIntegrity, the sources); this only tells the player. Any thread.
     */
    public void xpWithheld(Player player, String skill, String why) {
        XpListener feed = xpListener;
        if (feed != null && player != null && skill != null && !skill.isEmpty() && why != null) feed.withheld(player, skill, why);
    }

    /**
     * Levels gained through real play, for the level-up presentation (Skills.Celebrations). Never told about loading or
     * admin corrections ({@link #addXpQuietly}, {@link #setSkillLevel}). Called on the awarding thread.
     */
    public interface LevelUpListener {
        void leveledUp(Player player, String skill, int from, int to);
    }

    private volatile LevelUpListener levelUps;

    public void setLevelUpListener(LevelUpListener listener) {
        this.levelUps = listener;
    }

    public void setLevelListener(LevelListener listener) {
        this.levelListener = listener == null ? NO_LISTENER : listener;
    }

    public void setPlayerManaManager(PlayerManaManager playerManaManager) {
        this.playerManaManager = playerManaManager;
    }

    public void setCraftingManager(CraftingManager craftingManager) {
        this.craftingManager = craftingManager;
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

    public void rebuildSkillRegistry() {
        skills.clear();
        initializeSkills();
        plugin.getLogger().info("[SkillManager] Registered " + skills.size() + " skills.");
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
            skills.put("Fishing", new FishingSkill("Fishing")); // fishing itself: Gathering.Fishing (docs/design/FISHING.md)
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
        Map<String, Integer> levels = playerSkillLevels.get(uid);
        Map<String, Integer> xp = playerSkillXP.get(uid);
        if (levels == null || xp == null) return; // data not loaded yet, or player already left

        applyAward(player, skillName, levels, xp, amount, true);
        saveSkills(player);
    }

    /**
     * Single award path for every XP source: overflow-safe, capped at {@link SkillProgression#MAX_LEVEL}. Levels gained
     * through real play ({@code celebrate}) go to the level-up presentation; an admin's correction changes them quietly.
     */
    private SkillProgression.Result applyAward(Player player, String skillName,
                                               Map<String, Integer> levels, Map<String, Integer> xp,
                                               double amount, boolean celebrate) {
        int currentLevel = levels.getOrDefault(skillName, 1);
        int currentXP = xp.getOrDefault(skillName, 0);
        SkillProgression.Result r = SkillProgression.award(currentLevel, currentXP, amount);
        levels.put(skillName, r.level());
        xp.put(skillName, r.xp());
        if (r.levelsGained() > 0) levelListener.changed(player.getUniqueId());
        if (r.levelsGained() > 0 && celebrate) {
            LevelUpListener ups = levelUps;
            if (ups != null) ups.leveledUp(player, skillName, currentLevel, r.level());
            else player.sendMessage(createLevelUpMessage(skillName, r.level()));
        }
        XpListener feed = xpListener;
        long whole = SkillProgression.wholeXp(amount);
        if (feed != null && whole > 0 && SkillProgression.clampLevel(currentLevel) < SkillProgression.MAX_LEVEL) {
            feed.gained(player, skillName, whole, r);
        }
        return r;
    }

    private void saveSkills(Player player) {
        UUID uid = player.getUniqueId();
        String playerName = player.getName();
        Map<String, Integer> levels = new HashMap<>(playerSkillLevels.getOrDefault(uid, Map.of()));
        Map<String, Integer> xp = new HashMap<>(playerSkillXP.getOrDefault(uid, Map.of()));

        asyncSaver.scheduleDbSave("skills:" + uid, () -> databaseManager.savePlayerSkills(
                uid,
                playerName,
                levels,
                xp
        ));
    }


    public double calculateDynamicXp(double health, double damage, boolean isRare, boolean isBoss) {
        double rarityFactor = isRare ? 2.0 : 1.0;
        double bossFactor = isBoss ? 5.0 : 1.0;
        return SkillProgression.killXp(health, damage, rarityFactor * bossFactor);
    }

    /**
     * MobContext-aware kill XP — applies ONLY the config multiplier, no double-mult.
     * Overkill damage (e.g. the 1e9 guaranteed-kill hit) does not count; see
     * {@link SkillProgression#killXp}.
     */
    public double calculateDynamicXp(double health, double damage, MobContext ctx) {
        return SkillProgression.killXp(health, damage, ctx.xpMultiplier());
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

    public void refreshSkillMenu() {
        // Legacy no-op — GUI refreshes on reopen
    }

    private ItemStack createSkillItem(String skillName, int level, int xp) {
        // Placeholder logic
        return new ItemStack(Material.BOOK);
    }

    public boolean addXpAndCheckLevelUp(String skillName, double xpGained, Player player) {
        UUID uid = player.getUniqueId();
        // Maps exist only between a successful load (setPlayerSkills) and quit (removePlayer).
        // Awarding before the load used to create default maps (level 1) and SAVE them, which
        // could overwrite the stored levels if the save landed first or the load then failed.
        Map<String, Integer> levels = playerSkillLevels.get(uid);
        Map<String, Integer> xp = playerSkillXP.get(uid);
        if (levels == null || xp == null) return false;

        SkillProgression.Result r = applyAward(player, skillName, levels, xp, xpGained, true);
        saveSkills(player);
        return r.levelsGained() > 0;
    }

    /** An admin's XP grant or correction: the same award, without the level-up presentation. */
    public boolean addXpQuietly(String skillName, double xpGained, Player player) {
        UUID uid = player.getUniqueId();
        Map<String, Integer> levels = playerSkillLevels.get(uid);
        Map<String, Integer> xp = playerSkillXP.get(uid);
        if (levels == null || xp == null) return false;
        SkillProgression.Result r = applyAward(player, skillName, levels, xp, xpGained, false);
        saveSkills(player);
        return r.levelsGained() > 0;
    }

    /**
     * Admin/test helper: set a player's skill to an exact level with 0 XP into it, and save.
     * Call on the player's region thread. Returns false (and changes nothing) when the
     * player's skills are not loaded yet.
     */
    public boolean setSkillLevel(Player player, String skillName, int level) {
        UUID uid = player.getUniqueId();
        Map<String, Integer> levels = playerSkillLevels.get(uid);
        Map<String, Integer> xp = playerSkillXP.get(uid);
        if (levels == null || xp == null) return false; // data not loaded yet
        levels.put(skillName, Math.max(1, SkillProgression.clampLevel(level)));
        xp.put(skillName, 0);
        saveSkills(player);
        levelListener.changed(uid);
        return true;
    }

    /** True once this player's skills are loaded (between a successful load and quit). */
    public boolean hasLoadedSkills(UUID uid) {
        return playerSkillLevels.containsKey(uid) && playerSkillXP.containsKey(uid);
    }

    /**
     * Find a registered skill by its internal name or the name players see ("blade_mastery",
     * "Pathfinding" -> "Navigation"), ignoring case, spaces and underscores.
     */
    public String findSkillName(String input) {
        if (input == null) return null;
        String want = SkillNames.normalize(input);
        for (String name : skills.keySet()) {
            if (SkillNames.normalize(name).equals(want)) return name;
        }
        String internal = SkillNames.internalForDisplay(input);
        return internal != null && skills.containsKey(internal) ? internal : null;
    }

    public boolean isMaxLevel(String skillName, int newLevel) {
        return newLevel >= SkillProgression.MAX_LEVEL;
    }

    public void refreshSkillDisplay(Player player) {
        // GUI refreshes on reopen — no live push needed
    }

    public String createLevelUpMessage(String skillName, int level) {
        return "Congratulations! Your " + SkillNames.display(skillName) + " skill has reached level " + level + "!";
    }

    // ── XP curve (hybrid polynomial) ──────────────────────────────
    // Replaces the old 100 * 2^(level-1), which overflowed int around level 26
    // (100 * 2^25 > Integer.MAX_VALUE wraps negative) and bricked progression.
    private static final double XP_BASE = 75.0;
    private static final double XP_EXP = 1.85;
    private static final double XP_LINEAR = 40.0;

    /**
     * Pure XP-curve function: XP required to advance FROM {@code level} to the next.
     * Per-level cost ~115 (L1) to ~373k (L99); cumulative to 100 = 13,199,772. Monotonic and
     * overflow-safe. Static so it is unit-testable without constructing the manager.
     */
    public static int xpForLevel(int level) {
        int l = Math.max(1, level);
        double cost = XP_BASE * Math.pow(l, XP_EXP) + XP_LINEAR * l;
        return (int) Math.round(cost);
    }

    public int calculateRequiredXpForNextLevel(int currentLevel) {
        return xpForLevel(currentLevel);
    }

    public int getSkillXP(String skillName) {
        return skillXP.getOrDefault(skillName, 0);
    }

    public int getSkillXP(Player player, String skillName) {
        return getSkillXP(player.getUniqueId(), skillName);
    }

    /** In-level XP by UUID. Reads a concurrent map, so any thread may call it. */
    public int getSkillXP(UUID playerId, String skillName) {
        Map<String, Integer> xp = playerSkillXP.get(playerId);
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
        return getSkillLevel(player.getUniqueId(), skillName);
    }

    /** Same, by UUID. Reads a concurrent map, so any thread may call it. */
    public int getSkillLevel(UUID playerId, String skillName) {
        Map<String, Integer> levels = playerSkillLevels.get(playerId);
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
        Map<String, Integer> levels = new ConcurrentHashMap<>();
        Map<String, Integer> xp = new ConcurrentHashMap<>();
        int repaired = 0, started = 0;
        for (Map.Entry<String, Integer> e : skillLevels.entrySet()) {
            int raw = e.getValue() == null ? SkillProgression.MIN_LEVEL : e.getValue();
            int fixed = SkillProgression.clampLevel(raw);
            if (raw == 0) started++; // written by an older version at level 0: level 1 now, its XP kept
            else if (fixed != raw) repaired++;
            levels.put(e.getKey(), fixed);
        }
        for (Map.Entry<String, Integer> e : skillXP.entrySet()) {
            int raw = e.getValue() == null ? 0 : e.getValue();
            int fixed = SkillProgression.sanitizeStoredXp(levels.getOrDefault(e.getKey(), 1), raw);
            if (fixed != raw) repaired++;
            xp.put(e.getKey(), fixed);
        }
        // Out-of-range values can only come from the old kill-XP overflow (huge or negative XP)
        // or the missing level cap (> 100). Repair them on load; the next save rewrites them.
        if (repaired > 0) {
            plugin.getLogger().warning("[SkillManager] Repaired " + repaired
                    + " out-of-range skill value(s) for " + player.getName()
                    + " (XP outside its level's range, or level above " + SkillProgression.MAX_LEVEL + ").");
        }
        playerSkillLevels.put(uid, levels);
        playerSkillXP    .put(uid, xp);
        levelListener.loaded(uid);
        // Every skill starts at level 1; older versions wrote a new character's skills at level 0 (the menu said
        // "level 0, 0 / 115"). Written back now, so the database says what the game shows.
        if (started > 0) {
            plugin.getLogger().info("[SkillManager] " + player.getName() + ": " + started
                    + " skill(s) stored at level 0 start at level 1 now (their XP kept).");
            saveSkills(player);
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

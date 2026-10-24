package Manager;

import DefensiveMagic.Shield;
import Manager.ManaType;
import Manager.PlayerManaUpdateEvent;
import Skills.SkillManager;
import org.bukkit.Bukkit;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerManaManager {
    private final SkillManager skillManager;
    static final double DEFAULT_BASE_MANA = 1000.0;
    static final double DEFAULT_MANA_REGEN_RATE = 50.0;
    private static final double DEFAULT_MAGIC_DAMAGE_MULTIPLIER = 1.0;

    private static final class ManaState {
        private double baseMana = DEFAULT_BASE_MANA;
        private double totalMana = DEFAULT_BASE_MANA;
        private double currentMana = DEFAULT_BASE_MANA;
        private double manaRegenRate = DEFAULT_MANA_REGEN_RATE;
        private double magicDamageMultiplier = DEFAULT_MAGIC_DAMAGE_MULTIPLIER;
        private long lastRegenTime = System.currentTimeMillis();
    }

    private final Map<UUID, ManaState> manaStates = new ConcurrentHashMap<>();

    // Maps to track mana drain, boost effects, and boost limits
    private final Map<UUID, Double> playerManaDrains = new ConcurrentHashMap<>();
    private final Map<UUID, Long> drainEndTimes = new ConcurrentHashMap<>();
    private final Map<UUID, Double> activeManaBoosts = new ConcurrentHashMap<>();
    private final Map<UUID, Long> boostEndTimes = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> boostCountMap = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastBoostTimeMap = new ConcurrentHashMap<>();

    // Map to track active shields
    private final Map<UUID, Shield> activeShields = new ConcurrentHashMap<>();

    // Map to track active mana regen tasks per player (prevents stacking and enables cleanup)
    private final Map<UUID, ScheduledTask> regenTasks = new ConcurrentHashMap<>();

    private static final int MAX_BOOSTS_PER_MINUTE = 3;
    private final Plugin plugin;

    public PlayerManaManager(SkillManager skillManager, Plugin plugin) {
        this.skillManager = skillManager;
        this.plugin = plugin;
    }

    public int getSkillLevel(Player player, String skillName) {
        return skillManager.getSkillLevel(player, skillName);
    }

    private ManaState state(Player player) {
        return manaStates.computeIfAbsent(player.getUniqueId(), ignored -> new ManaState());
    }

    /** Read-only {current, max, regen per second} if this player is tracked, else null. Never creates state. */
    public double[] peekMana(UUID playerId) {
        ManaState s = manaStates.get(playerId);
        return s == null ? null : new double[] {s.currentMana, s.totalMana, s.manaRegenRate};
    }

    // Getter for the maximum mana
    public double getMaxMana(Player player) {
        return state(player).totalMana;
    }

    // Setter for the maximum mana
    public void setMaxMana(Player player, double maxMana) {
        ManaState state = state(player);
        state.totalMana = Math.max(0.0, maxMana);
        if (state.currentMana > state.totalMana) {
            state.currentMana = state.totalMana; // Cap current mana to max mana
        }
    }

    // Getter for the current mana
    public double getCurrentMana(Player player) {
        return state(player).currentMana;
    }

    // Method to set the current mana of the player
    public void setPlayerCurrentMana(Player player, double mana) {
        ManaState state = state(player);
        double maxMana = getMaxMana(player);
        if (mana > maxMana) {
            mana = maxMana;
        } else if (mana < 0) {
            mana = 0;
        }
        state.currentMana = mana;
        Bukkit.getPluginManager().callEvent(new PlayerManaUpdateEvent(player, mana));
    }

    // Getter for mana regeneration rate
    public double getManaRegenRate(Player player) {
        return state(player).manaRegenRate;
    }

    // Setter for mana regeneration rate
    public void setManaRegenRate(Player player, double manaRegenRate) {
        state(player).manaRegenRate = Math.max(0.0, manaRegenRate);
    }

    // Method to get the base mana regeneration rate (default 50 per second)
    public double getBaseManaRegenRate() {
        return DEFAULT_MANA_REGEN_RATE;
    }

    // Method to spend mana
    public boolean spendMana(Player player, ManaType manaType, double amount) {
        ManaState state = state(player);
        if (state.currentMana >= amount) {
            state.currentMana -= amount;
            setPlayerCurrentMana(player, state.currentMana); // Update player's mana
            return true;
        }
        return false;
    }

    // Method to increase mana regeneration rate
    public void increaseManaRegenRate(Player player, double increase) {
        ManaState state = state(player);
        state.manaRegenRate = Math.max(0.0, state.manaRegenRate + increase);
    }

    // Method to increase maximum mana
    public void increaseMaxMana(Player player, double increase) {
        ManaState state = state(player);
        setMaxMana(player, state.totalMana + increase); // Update player's max mana
    }

    // Apply a shield to the player
    public void applyShield(Player player, double shieldStrength) {
        UUID playerId = player.getUniqueId();
        Shield shield = new Shield(player, shieldStrength);
        shield.activate();
        activeShields.put(playerId, shield);
    }

    // Method to check if a player has an active shield
    public boolean hasActiveShield(Player player) {
        UUID playerId = player.getUniqueId();
        Shield shield = activeShields.get(playerId);
        return shield != null && shield.isActive();
    }

    // Method to absorb damage using the shield
    public void absorbShieldDamage(Player player, double damage) {
        UUID playerId = player.getUniqueId();
        Shield shield = activeShields.get(playerId);
        if (shield != null && shield.isActive()) {
            shield.absorbDamage(damage);
            if (!shield.isActive()) {
                activeShields.remove(playerId); // Remove the shield if it's broken
            }
        }
    }

    // Method to start the mana regeneration task
    public void startManaRegenTask(Player player) {
        // Cancel any existing regen task to prevent stacking
        stopManaRegenTask(player);

        ScheduledTask task = player.getScheduler().runAtFixedRate(plugin, scheduledTask -> {
            if (player.isOnline()) {
                regenerateMana(player, getManaRegenRate(player) / 20); // Regenerate mana every tick (1/20th of a second)
            } else {
                scheduledTask.cancel();
                regenTasks.remove(player.getUniqueId());
            }
        }, null, 1L, 1L);
        regenTasks.put(player.getUniqueId(), task);
    }

    // Method to stop the mana regeneration task for a player
    public void stopManaRegenTask(Player player) {
        ScheduledTask task = regenTasks.remove(player.getUniqueId());
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
    }

    /**
     * Remove all per-player state for the given UUID.
     * Idempotent, safe if UUID not present, cancels tasks first.
     */
    public void removePlayer(UUID uuid) {
        // Stop regen task
        ScheduledTask task = regenTasks.remove(uuid);
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }

        // Clear all per-player maps
        playerManaDrains.remove(uuid);
        drainEndTimes.remove(uuid);
        activeManaBoosts.remove(uuid);
        boostEndTimes.remove(uuid);
        boostCountMap.remove(uuid);
        lastBoostTimeMap.remove(uuid);
        activeShields.remove(uuid);
        playerManaShields.remove(uuid);
        manaStates.remove(uuid);
    }

    /** Spend BASIC mana */
    public boolean consumeMana(Player player, double amount) {
        return spendMana(player, ManaType.BASIC, amount);   // ← use BASIC
    }

    /** Add mana (capped at max) */
    public void addMana(Player player, double amount) {
        double newMana = getCurrentMana(player) + amount;
        setPlayerCurrentMana(player, newMana);
    }

    /** Get current mana */
    public double getMana(Player player) {
        return getCurrentMana(player);
    }

    // Method to regenerate mana
    public void regenerateMana(Player player, double amount) {
        setPlayerCurrentMana(player, getCurrentMana(player) + amount);
    }

    public void applyManaDrain(Player player, double drainAmountPerSecond, long durationInSeconds) {
        UUID playerId = player.getUniqueId();
        playerManaDrains.put(playerId, drainAmountPerSecond);

        long endTime = System.currentTimeMillis() + (durationInSeconds * 1000);
        drainEndTimes.put(playerId, endTime);

        player.getScheduler().runAtFixedRate(plugin, scheduledTask -> {
            long currentTime = System.currentTimeMillis();

            if (currentTime > drainEndTimes.getOrDefault(playerId, 0L)) {
                playerManaDrains.remove(playerId);
                drainEndTimes.remove(playerId);
                scheduledTask.cancel();
                return;
            }

            double currentMana = getCurrentMana(player);
            double newMana = currentMana - (drainAmountPerSecond / 20);

            if (newMana < 0) {
                newMana = 0;
            }

            setPlayerCurrentMana(player, newMana);
        }, null, 1L, 1L);
    }

    public void applyTemporaryManaBoost(Player player, double boostAmount, long durationInSeconds) {
        UUID playerId = player.getUniqueId();

        if (!canApplyBoost(player)) {
            player.sendMessage("The magic inside of you right now is much too powerful to handle another boost.");
            return;
        }

        ManaState state = state(player);
        state.totalMana += boostAmount;
        state.currentMana = Math.min(state.totalMana, state.currentMana + boostAmount);
        Bukkit.getPluginManager().callEvent(new PlayerManaUpdateEvent(player, state.currentMana));

        long endTime = System.currentTimeMillis() + (durationInSeconds * 1000);
        boostEndTimes.put(playerId, endTime);

        incrementBoostCount(playerId);

        player.getScheduler().runAtFixedRate(plugin, scheduledTask -> {
            long currentTime = System.currentTimeMillis();

            if (currentTime > boostEndTimes.getOrDefault(playerId, 0L)) {
                ManaState current = state(player);
                current.totalMana = Math.max(0.0, current.totalMana - boostAmount);
                if (current.currentMana > current.totalMana) {
                    current.currentMana = current.totalMana;
                }
                Bukkit.getPluginManager().callEvent(new PlayerManaUpdateEvent(player, current.currentMana));
                boostEndTimes.remove(playerId);
                scheduledTask.cancel();
            }
        }, null, 1L, 20L);
    }

    private boolean canApplyBoost(Player player) {
        UUID playerId = player.getUniqueId();
        int boostCount = boostCountMap.getOrDefault(playerId, 0);
        long lastBoostTime = lastBoostTimeMap.getOrDefault(playerId, 0L);

        long currentTime = System.currentTimeMillis();

        if (boostCount < MAX_BOOSTS_PER_MINUTE || (currentTime - lastBoostTime) > 60000) {
            if ((currentTime - lastBoostTime) > 60000) {
                boostCountMap.put(playerId, 0);
            }
            return true;
        }
        return false;
    }

    private void incrementBoostCount(UUID playerId) {
        int currentCount = boostCountMap.getOrDefault(playerId, 0);
        boostCountMap.put(playerId, currentCount + 1);
        lastBoostTimeMap.put(playerId, System.currentTimeMillis());
    }

    public void resetMana(Player player, double amount) {
        setPlayerCurrentMana(player, amount);
    }

    public void applyMagicDamageMultiplier(Player player, double multiplier, boolean isSurge) {
        state(player).magicDamageMultiplier = multiplier;

        if (isSurge) {
            player.getScheduler().runDelayed(plugin, task -> {
                applyMagicDamageMultiplier(player, 1.0, false);
                player.sendMessage("Your Magic Surge has ended.");
            }, null, 200L);
        }
    }

    public void applyManaCostReduction(Player player, double reduction) {
        double manaCost = getCurrentSpellManaCost(player);
        manaCost *= (1.0 - reduction);
        setCurrentSpellManaCost(player, manaCost);
    }

    public void applyTemporaryRegenBoost(Player player, double boost, long duration) {
        ManaState state = state(player);
        state.manaRegenRate = Math.max(0.0, state.manaRegenRate + boost);

        player.getScheduler().runDelayed(plugin, task -> {
            ManaState current = state(player);
            current.manaRegenRate = Math.max(0.0, current.manaRegenRate - boost);
        }, null, Math.max(1L, duration / 50));
    }

    private double getCurrentSpellManaCost(Player player) {
        return getMetadataDouble(player, "currentSpellManaCost", 0.0);
    }

    private void setCurrentSpellManaCost(Player player, double newCost) {
        player.setMetadata("currentSpellManaCost", new FixedMetadataValue(plugin, newCost));
    }

    private double getCurrentSpellDamage(Player player) {
        return getMetadataDouble(player, "currentSpellDamage", 0.0);
    }

    private void setCurrentSpellDamage(Player player, double newDamage) {
        player.setMetadata("currentSpellDamage", new FixedMetadataValue(plugin, newDamage));
    }

    public void applyDynamicSkillInteraction(Player player) {
        int elementalMasteryLevel = skillManager.getSkillLevel(player, "Elemental Mastery");
        int spellWeavingLevel = skillManager.getSkillLevel(player, "Spell Weaving");

        if (elementalMasteryLevel > 0 && spellWeavingLevel > 0) {
            double dynamicBonus = (elementalMasteryLevel + spellWeavingLevel) * 0.01;

            applyManaCostReduction(player, dynamicBonus);
            applyMagicDamageMultiplier(player, 1.0 + dynamicBonus, false);

            player.sendMessage("Your elemental mastery and spell weaving synergize, empowering your spells!");
        }
    }

    public void applyCrossSkillBenefits(Player player) {
        int heavyArmorTrainingLevel = skillManager.getSkillLevel(player, "Heavy Armor Training");
        int defensiveMagicSkillLevel = skillManager.getSkillLevel(player, "Defensive Magic");

        if (heavyArmorTrainingLevel > 0 && defensiveMagicSkillLevel > 0) {
            double manaCostReduction = 0.02 * heavyArmorTrainingLevel;
            applyManaCostReduction(player, manaCostReduction);

            player.sendMessage("Your experience in heavy armor reduces the mana cost of defensive spells!");
        }
    }

    public void checkForMagicSurge(Player player) {
        boolean hasManaRegenBuff = checkForBuff(player, "ManaRegenBuff");
        int intelligenceLevel = skillManager.getSkillLevel(player, "Intelligence");

        if (hasManaRegenBuff && intelligenceLevel >= 5) {
            activateMagicSurge(player);
        }
    }

    private boolean checkForBuff(Player player, String buffName) {
        return player.hasMetadata(buffName);
    }

    private void activateMagicSurge(Player player) {
        applyMagicDamageMultiplier(player, 1.5, true);
        applyTemporaryRegenBoost(player, 2.0, 200L);

        player.sendMessage("You enter a Magic Surge! Spells are more powerful and your mana regenerates faster.");
    }

    public void checkForElementalSynergy(Player player) {
        int fireCasts = getRecentSpellCasts(player, "fire");
        int iceCasts = getRecentSpellCasts(player, "ice");
        int lightningCasts = getRecentSpellCasts(player, "lightning");

        if (fireCasts > 0 && iceCasts > 0 && lightningCasts > 0) {
            activateElementalSynergy(player);
        }
    }

    private int getRecentSpellCasts(Player player, String element) {
        String key = "recent" + element + "Casts";
        return getMetadataInt(player, key, 0);
    }

    private double getMetadataDouble(Player player, String key, double fallback) {
        List<MetadataValue> values = player.getMetadata(key);
        return values.isEmpty() ? fallback : values.get(0).asDouble();
    }

    private int getMetadataInt(Player player, String key, int fallback) {
        List<MetadataValue> values = player.getMetadata(key);
        return values.isEmpty() ? fallback : values.get(0).asInt();
    }

    private void activateElementalSynergy(Player player) {
        double synergyBonus = 1.25;
        applyMagicDamageMultiplier(player, synergyBonus, true);
        applyManaCostReduction(player, 0.2);

        player.sendMessage("Elemental Synergy activated! Your elemental spells are more powerful and cost less mana.");
    }


    public enum ManaShieldType {
        DEFENSIVE, OFFENSIVE, UTILITY, UNIQUE
    }

    // Map to track the active mana shield type per player
    private final Map<UUID, ManaShieldType> playerManaShields = new ConcurrentHashMap<>();

    // Method for players to select their preferred mana shield type
    public void selectManaShield(Player player, ManaShieldType shieldType) {
        UUID playerId = player.getUniqueId();
        playerManaShields.put(playerId, shieldType);
        player.sendMessage("You have selected the " + shieldType.name() + " Mana Shield.");
    }

    // Method to activate the selected Mana Shield
    public void activateManaShield(Player player) {
        UUID playerId = player.getUniqueId();
        ManaShieldType shieldType = playerManaShields.getOrDefault(playerId, ManaShieldType.DEFENSIVE);

        switch (shieldType) {
            case DEFENSIVE:
                activateDefensiveShield(player);
                break;
            case OFFENSIVE:
                activateOffensiveShield(player);
                break;
            case UTILITY:
                activateUtilityShield(player);
                break;
            case UNIQUE:
                activateUniqueShield(player);
                break;
            default:
                player.sendMessage("No valid Mana Shield selected.");
        }
    }

    // Method to activate Defensive Shield
    private void activateDefensiveShield(Player player) {
        double manaCost = calculateManaCost(player, "DEFENSIVE");
        double damageReduction = calculateDamageReduction(player, manaCost);
        reduceMana(player, manaCost);
        applyShield(player, damageReduction);
        player.sendMessage("🛡️ Your Defensive Mana Shield absorbed " + damageReduction + " damage.");
    }

    // Method to activate Offensive Shield
    private void activateOffensiveShield(Player player) {
        double manaCost = calculateManaCost(player, "OFFENSIVE");
        double reflectDamage = calculateReflectDamage(player, manaCost);
        reduceMana(player, manaCost);
        // Implement logic to reflect damage
        player.sendMessage("⚔ Your Offensive Mana Shield reflected " + reflectDamage + " damage back to the attacker.");
    }

    // Method to activate Utility Shield
    private void activateUtilityShield(Player player) {
        double manaCost = calculateManaCost(player, "UTILITY");
        applyUtilityEffects(player, manaCost);
        reduceMana(player, manaCost);
        player.sendMessage("Your Utility Mana Shield enhanced your abilities.");
    }

    // Method to activate Unique Shield
    private void activateUniqueShield(Player player) {
        double manaCost = calculateManaCost(player, "UNIQUE");
        applyUniqueShieldEffects(player, manaCost);
        reduceMana(player, manaCost);
        player.sendMessage("Your Unique Mana Shield empowered you with extraordinary abilities.");
    }

    // Example method to calculate mana cost based on the shield type
    private double calculateManaCost(Player player, String shieldType) {
        // Implement logic to determine mana cost based on shield type and player stats
        return 50.0; // Placeholder value
    }

    // Example method to reduce mana after shield activation
    private void reduceMana(Player player, double manaCost) {
        double currentMana = getCurrentMana(player);
        setPlayerCurrentMana(player, Math.max(0, currentMana - manaCost));
    }

    // Placeholder methods for specific shield effects
    private double calculateDamageReduction(Player player, double manaCost) {
        return manaCost * 2; // Placeholder calculation
    }

    private double calculateReflectDamage(Player player, double manaCost) {
        return manaCost * 1.5; // Placeholder calculation
    }

    private void applyUtilityEffects(Player player, double manaCost) {
        // Implement utility effects like cooldown reduction
    }

    private void applyUniqueShieldEffects(Player player, double manaCost) {
        // Implement unique combination effects
    }
}

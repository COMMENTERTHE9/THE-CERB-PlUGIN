package Manager;

import DefensiveMagic.Shield;
import Manager.ManaType;
import Manager.PlayerManaUpdateEvent;
import Skills.SkillManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerManaManager {
    private final SkillManager skillManager;
    private double baseMana;
    private double totalMana;
    private double currentMana;
    private double manaRegenRate;
    private double magicDamageMultiplier;
    private long lastRegenTime;

    // Maps to track mana drain, boost effects, and boost limits
    private final Map<UUID, Double> playerManaDrains = new HashMap<>();
    private final Map<UUID, Long> drainEndTimes = new HashMap<>();
    private final Map<UUID, Double> activeManaBoosts = new HashMap<>();
    private final Map<UUID, Long> boostEndTimes = new HashMap<>();
    private final Map<UUID, Integer> boostCountMap = new HashMap<>();
    private final Map<UUID, Long> lastBoostTimeMap = new HashMap<>();

    // Map to track active shields
    private final Map<UUID, Shield> activeShields = new HashMap<>();

    private static final int MAX_BOOSTS_PER_MINUTE = 3;
    private final Plugin plugin;

    public PlayerManaManager(SkillManager skillManager, Plugin plugin) {
        this.skillManager = skillManager;
        this.plugin = plugin;
        this.baseMana = 1000;
        this.manaRegenRate = 50; // Default mana regeneration rate per second
        this.magicDamageMultiplier = 1.0; // Default magic damage multiplier
        this.totalMana = baseMana;
        this.currentMana = totalMana;
        this.lastRegenTime = System.currentTimeMillis();
    }

    // Getter for the maximum mana
    public double getMaxMana(Player player) {
        return this.totalMana;
    }

    // Setter for the maximum mana
    public void setMaxMana(double maxMana) {
        this.totalMana = maxMana;
        if (this.currentMana > this.totalMana) {
            this.currentMana = this.totalMana; // Cap current mana to max mana
        }
    }

    // Getter for the current mana
    public double getCurrentMana(Player player) {
        return this.currentMana;
    }

    // Method to set the current mana of the player
    public void setPlayerCurrentMana(Player player, double mana) {
        UUID playerId = player.getUniqueId();
        double maxMana = getMaxMana(player);
        if (mana > maxMana) {
            mana = maxMana;
        } else if (mana < 0) {
            mana = 0;
        }
        currentMana = mana;
        Bukkit.getPluginManager().callEvent(new PlayerManaUpdateEvent(player, mana));
    }

    // Getter for mana regeneration rate
    public double getManaRegenRate(Player player) {
        return this.manaRegenRate;
    }

    // Setter for mana regeneration rate
    public void setManaRegenRate(double manaRegenRate) {
        this.manaRegenRate = manaRegenRate;
    }

    // Method to get the base mana regeneration rate (default 50 per second)
    public double getBaseManaRegenRate() {
        return 50.0;
    }

    // Method to spend mana
    public boolean spendMana(Player player, ManaType manaType, double amount) {
        if (this.currentMana >= amount) {
            this.currentMana -= amount;
            setPlayerCurrentMana(player, this.currentMana); // Update player's mana
            return true;
        }
        return false;
    }

    // Method to increase mana regeneration rate
    public void increaseManaRegenRate(Player player, double increase) {
        this.manaRegenRate += increase;
    }

    // Method to increase maximum mana
    public void increaseMaxMana(Player player, double increase) {
        this.totalMana += increase;
        setMaxMana(this.totalMana); // Update player's max mana
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
        return activeShields.containsKey(playerId) && activeShields.get(playerId).isActive();
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
        new BukkitRunnable() {
            @Override
            public void run() {
                if (player.isOnline()) {
                    regenerateMana(manaRegenRate / 20); // Regenerate mana every tick (1/20th of a second)
                    setPlayerCurrentMana(player, currentMana);
                } else {
                    this.cancel(); // Stop the task if the player is offline
                }
            }
        }.runTaskTimer(plugin, 0L, 1L); // Run every tick (1 tick = 1/20th of a second)
    }

    // Method to regenerate mana
    public void regenerateMana(double amount) {
        this.currentMana += amount;
        if (this.currentMana > this.totalMana) {
            this.currentMana = this.totalMana;
        }
    }

    public void applyManaDrain(Player player, double drainAmountPerSecond, long durationInSeconds) {
        UUID playerId = player.getUniqueId();
        playerManaDrains.put(playerId, drainAmountPerSecond);

        long endTime = System.currentTimeMillis() + (durationInSeconds * 1000);
        drainEndTimes.put(playerId, endTime);

        new BukkitRunnable() {
            @Override
            public void run() {
                long currentTime = System.currentTimeMillis();

                if (currentTime > drainEndTimes.getOrDefault(playerId, 0L)) {
                    playerManaDrains.remove(playerId);
                    drainEndTimes.remove(playerId);
                    this.cancel();
                    return;
                }

                double currentMana = getCurrentMana(player);
                double newMana = currentMana - (drainAmountPerSecond / 20);

                if (newMana < 0) {
                    newMana = 0;
                }

                setPlayerCurrentMana(player, newMana);
            }
        }.runTaskTimerAsynchronously(Bukkit.getPluginManager().getPlugin("cerb"), 0L, 1L);
    }

    public void applyTemporaryManaBoost(Player player, double boostAmount, long durationInSeconds) {
        UUID playerId = player.getUniqueId();

        if (!canApplyBoost(player)) {
            player.sendMessage("The magic inside of you right now is much too powerful to handle another boost.");
            return;
        }

        this.totalMana += boostAmount;
        this.currentMana += boostAmount;

        long endTime = System.currentTimeMillis() + (durationInSeconds * 1000);
        boostEndTimes.put(playerId, endTime);

        incrementBoostCount(playerId);

        new BukkitRunnable() {
            @Override
            public void run() {
                long currentTime = System.currentTimeMillis();

                if (currentTime > boostEndTimes.getOrDefault(playerId, 0L)) {
                    totalMana -= boostAmount;
                    if (currentMana > totalMana) {
                        currentMana = totalMana;
                    }
                    boostEndTimes.remove(playerId);
                    this.cancel();
                }
            }
        }.runTaskTimerAsynchronously(Bukkit.getPluginManager().getPlugin("cerb"), 0L, 20L);
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

    public void resetMana(double amount) {
        this.currentMana = amount;
        if (this.currentMana > this.totalMana) {
            this.currentMana = this.totalMana;
        }
    }

    public void applyMagicDamageMultiplier(Player player, double multiplier, boolean isSurge) {
        this.magicDamageMultiplier = multiplier;

        if (isSurge) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    applyMagicDamageMultiplier(player, 1.0, false);
                    player.sendMessage("Your Magic Surge has ended.");
                }
            }.runTaskLater(Bukkit.getPluginManager().getPlugin("cerb"), 200L);
        }
    }

    public void applyManaCostReduction(Player player, double reduction) {
        double manaCost = getCurrentSpellManaCost(player);
        manaCost *= (1.0 - reduction);
        setCurrentSpellManaCost(player, manaCost);
    }

    public void applyTemporaryRegenBoost(double boost, long duration) {
        this.manaRegenRate += boost;

        new BukkitRunnable() {
            @Override
            public void run() {
                manaRegenRate -= boost;
            }
        }.runTaskLater(Bukkit.getPluginManager().getPlugin("cerb"), duration / 50);
    }

    private double getCurrentSpellManaCost(Player player) {
        return player.getMetadata("currentSpellManaCost").get(0).asDouble();
    }

    private void setCurrentSpellManaCost(Player player, double newCost) {
        player.setMetadata("currentSpellManaCost", new FixedMetadataValue(Bukkit.getPluginManager().getPlugin("cerb"), newCost));
    }

    private double getCurrentSpellDamage(Player player) {
        return player.getMetadata("currentSpellDamage").get(0).asDouble();
    }

    private void setCurrentSpellDamage(Player player, double newDamage) {
        player.setMetadata("currentSpellDamage", new FixedMetadataValue(Bukkit.getPluginManager().getPlugin("cerb"), newDamage));
    }

    public void applyDynamicSkillInteraction(Player player) {
        int elementalMasteryLevel = skillManager.getSkillLevel("Elemental Mastery");
        int spellWeavingLevel = skillManager.getSkillLevel("Spell Weaving");

        if (elementalMasteryLevel > 0 && spellWeavingLevel > 0) {
            double dynamicBonus = (elementalMasteryLevel + spellWeavingLevel) * 0.01;

            applyManaCostReduction(player, dynamicBonus);
            applyMagicDamageMultiplier(player, 1.0 + dynamicBonus, false);

            player.sendMessage("Your elemental mastery and spell weaving synergize, empowering your spells!");
        }
    }

    public void applyCrossSkillBenefits(Player player) {
        int heavyArmorTrainingLevel = skillManager.getSkillLevel("Heavy Armor Training");
        int defensiveMagicSkillLevel = skillManager.getSkillLevel("Defensive Magic Skill");

        if (heavyArmorTrainingLevel > 0 && defensiveMagicSkillLevel > 0) {
            double manaCostReduction = 0.02 * heavyArmorTrainingLevel;
            applyManaCostReduction(player, manaCostReduction);

            player.sendMessage("Your experience in heavy armor reduces the mana cost of defensive spells!");
        }
    }

    public void checkForMagicSurge(Player player) {
        boolean hasManaRegenBuff = checkForBuff(player, "ManaRegenBuff");
        int intelligenceLevel = skillManager.getSkillLevel("Intelligence");

        if (hasManaRegenBuff && intelligenceLevel >= 5) {
            activateMagicSurge(player);
        }
    }

    private boolean checkForBuff(Player player, String buffName) {
        return player.hasMetadata(buffName);
    }

    private void activateMagicSurge(Player player) {
        applyMagicDamageMultiplier(player, 1.5, true);
        applyTemporaryRegenBoost(2.0, 200L);

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
        return player.getMetadata("recent" + element + "Casts").get(0).asInt();
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
    private final Map<UUID, ManaShieldType> playerManaShields = new HashMap<>();

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

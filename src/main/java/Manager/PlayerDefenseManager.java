package Manager;

import Skills.SkillManager;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

public class PlayerDefenseManager {
    private final SkillManager skillManager;
    private final PlayerVirtualHealthManager virtualHealthManager;
    private double baseDefense;
    private double totalDefense;
    private double armorToughness;
    private double damageResistance;
    private double currentEffectiveness = 1.0; // Defense effectiveness starts at 100%
    private final double baseDefenseAmount = 10.0; // Base defense amount for all players

    public PlayerDefenseManager(SkillManager skillManager, PlayerVirtualHealthManager virtualHealthManager) {
        this.skillManager = skillManager;
        this.virtualHealthManager = virtualHealthManager;
        this.baseDefense = baseDefenseAmount; // Start with a base defense amount
        this.totalDefense = 10;
        this.armorToughness = 0;
        this.damageResistance = 0;
    }

    // Calculate base defense based on armor equipped
    private double calculateBaseDefense(Player player) {
        double defense = baseDefenseAmount; // Start with the base defense amount
        ItemStack[] armorContents = player.getInventory().getArmorContents();

        for (ItemStack item : armorContents) {
            if (item != null) {
                switch (item.getType()) {
                    case LEATHER_HELMET:
                    case LEATHER_CHESTPLATE:
                    case LEATHER_LEGGINGS:
                    case LEATHER_BOOTS:
                        defense += 1;
                        break;
                    case CHAINMAIL_HELMET:
                    case CHAINMAIL_CHESTPLATE:
                    case CHAINMAIL_LEGGINGS:
                    case CHAINMAIL_BOOTS:
                        defense += 2;
                        break;
                    case GOLDEN_HELMET:
                    case GOLDEN_CHESTPLATE:
                    case GOLDEN_LEGGINGS:
                    case GOLDEN_BOOTS:
                        defense += 2;
                        break;
                    case IRON_HELMET:
                    case IRON_CHESTPLATE:
                    case IRON_LEGGINGS:
                    case IRON_BOOTS:
                        defense += 3;
                        break;
                    case DIAMOND_HELMET:
                    case DIAMOND_CHESTPLATE:
                    case DIAMOND_LEGGINGS:
                    case DIAMOND_BOOTS:
                        defense += 4;
                        break;
                    case NETHERITE_HELMET:
                    case NETHERITE_CHESTPLATE:
                    case NETHERITE_LEGGINGS:
                    case NETHERITE_BOOTS:
                        defense += 5;
                        break;
                    default:
                        break;
                }
            }
        }
        return defense;
    }

    // Apply skill effects that might increase defense
    private double applySkillBonuses(Player player) {
        double skillBonus = 1.0;

        // Add null check for skillManager
        if (skillManager != null) {
            // Example: Apply Heavy Armor Training skill bonus
            if (skillManager.getSkillLevel("Heavy Armor Training") > 0) {
                skillBonus += skillManager.getTotalEffectMultiplier("Heavy Armor Training") - 1.0; // Assuming it returns a multiplier
            }

            // Apply SurvivalSkill bonuses (Armor Toughness and Damage Resistance)
            if (skillManager.getSkillLevel("Survival") > 0) {
                armorToughness += skillManager.getSkillLevel("Survival") * 1000.0; // Example: +1000 armor toughness per level
                damageResistance += skillManager.getSkillLevel("Survival") * 0.05; // Example: +5% damage resistance per level
            }
        } else {
            // Log a warning in case skillManager is not initialized
            Bukkit.getLogger().warning("SkillManager is null in PlayerDefenseManager. Cannot apply skill bonuses.");
        }

        return skillBonus;
    }

    // Calculate total defense based on armor, base defense, and skills
    public void updateDefense(Player player) {
        this.baseDefense = calculateBaseDefense(player);
        double skillBonus = applySkillBonuses(player);
        this.totalDefense = baseDefense * skillBonus;

        // Calculate effectiveness based on total defense and player health
        double healthFactor = player.getHealth() / player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
        this.currentEffectiveness = healthFactor * (armorToughness / totalDefense);

        // Normalize effectiveness to a reasonable value
        if (currentEffectiveness > 1.0) {
            currentEffectiveness = 1.0;
        } else if (currentEffectiveness < 0.0) {
            currentEffectiveness = 0.0;
        }

        // Apply defense stats for both normal and virtual health systems
        applyDefenseStats(player);

        // Start the defense recovery task if not already at full effectiveness
        if (currentEffectiveness < 1.0) {
            startDefenseRecoveryTask(player);
        }

        // Fire the custom event after updating defense
        Bukkit.getPluginManager().callEvent(new PlayerDefenseUpdateEvent(player, totalDefense * currentEffectiveness));
    }

    // Apply defense stats, including toughness and resistance for normal and virtual health
    private void applyDefenseStats(Player player) {
        // Apply toughness and resistance to virtual health (PvE and PvP)
        virtualHealthManager.setPlayerToughness(player, armorToughness);
        virtualHealthManager.setPlayerResistance(player, damageResistance);

        // Apply defense attributes for normal Minecraft health (PvE and PvP)
        if (player.getAttribute(Attribute.GENERIC_ARMOR) != null) {
            player.getAttribute(Attribute.GENERIC_ARMOR).setBaseValue(totalDefense * currentEffectiveness);
        }
        if (player.getAttribute(Attribute.GENERIC_ARMOR_TOUGHNESS) != null) {
            player.getAttribute(Attribute.GENERIC_ARMOR_TOUGHNESS).setBaseValue(armorToughness);
        }
        if (player.getAttribute(Attribute.GENERIC_KNOCKBACK_RESISTANCE) != null) {
            player.getAttribute(Attribute.GENERIC_KNOCKBACK_RESISTANCE).setBaseValue(damageResistance);
        }
    }

    // Method to recover defense effectiveness over time
    public void recoverDefense(Player player) {
        // Check if the current effectiveness is already at 100%
        if (currentEffectiveness >= 1.0) {
            return; // No need to recover if already at full effectiveness
        }

        // Determine recovery rate based on the quality of the armor (Placeholder for now)
        double recoveryRate = 0.05; // 5% recovery per tick (this is just an example)

        // Gradually recover the defense effectiveness
        currentEffectiveness += recoveryRate;

        // Cap the effectiveness at 100%
        if (currentEffectiveness > 1.0) {
            currentEffectiveness = 1.0;
        }

        // Update player's defense attributes
        applyDefenseStats(player);

        // Fire the custom event after recovering defense
        Bukkit.getPluginManager().callEvent(new PlayerDefenseUpdateEvent(player, totalDefense * currentEffectiveness));
    }

    // Method to start a task that recovers defense effectiveness over time
    public void startDefenseRecoveryTask(Player player) {
        new BukkitRunnable() {
            @Override
            public void run() {
                // Make sure the player is still online before processing
                if (!player.isOnline()) {
                    this.cancel();
                    return;
                }

                // Run on main thread since we're modifying bukkit API elements
                Bukkit.getScheduler().runTask(Bukkit.getPluginManager().getPlugin("CerberusPlugin"), () -> {
                    // Call the recoverDefense method
                    recoverDefense(player);

                    // If defense is fully recovered, stop the task
                    if (currentEffectiveness >= 1.0) {
                        this.cancel();
                    }
                });
            }
        }.runTaskTimer(Bukkit.getPluginManager().getPlugin("CerberusPlugin"), 0L, 20L); // Remove Asynchronously
    }

    // Get the current defense effectiveness
    public double getCurrentEffectiveness() {
        return currentEffectiveness;
    }

    // Get the current defense value
    public double getDefense(Player player) {
        return totalDefense;
    }

    // Set a new defense value
    public void setDefense(Player player, double defense) {
        this.totalDefense = defense;
        applyDefenseStats(player);

        // Fire the custom event after setting defense
        Bukkit.getPluginManager().callEvent(new PlayerDefenseUpdateEvent(player, defense * currentEffectiveness));
    }

    // Get the base defense value (without skill bonuses)
    public double getBaseDefense(Player player) {
        return baseDefense;
    }

    // Increase the player's defense by a specified amount
    public void increaseDefense(Player player, double amount) {
        this.totalDefense += amount;
        applyDefenseStats(player);

        // Fire the custom event after increasing defense
        Bukkit.getPluginManager().callEvent(new PlayerDefenseUpdateEvent(player, totalDefense * currentEffectiveness));
    }

    // Increase the player's armor toughness
    public void increaseArmorToughness(Player player, double amount) {
        this.armorToughness += amount;
        virtualHealthManager.setPlayerToughness(player, armorToughness); // Sync with virtual health system
        applyDefenseStats(player);
    }

    // Increase the player's damage resistance
    public void increaseDamageResistance(Player player, double amount) {
        this.damageResistance += amount;
        virtualHealthManager.setPlayerResistance(player, damageResistance); // Sync with virtual health system
        applyDefenseStats(player);
    }

    // Display defense on the player's HUD (this method would be called from your HUD Manager)
    public String getDefenseDisplay() {
        return String.format("Defense: %.2f | Toughness: %.2f | Resistance: %.2f | Effectiveness: %.2f%%",
                totalDefense, armorToughness, damageResistance, currentEffectiveness * 100);
    }
}

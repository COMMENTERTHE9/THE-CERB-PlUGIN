package Manager;

import Skills.SkillManager;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Manages per-player armor reduction — Layer 2 of the 3-layer defense system.
 * <p>
 * Armor is a flat percent damage reduction based on equipped armor pieces + skill bonuses,
 * hard-capped at {@code armorMaxReduction} (default 40%).
 * <p>
 * Elemental resistances (fire/cold/drowning) from the Survival skill are also managed here
 * and pushed to {@link PlayerVirtualHealthManager}.
 */
public class PlayerDefenseManager {
    private final SkillManager skillManager;
    private final PlayerVirtualHealthManager virtualHealthManager;
    private @Nullable CombatConfig combatConfig;
    private @Nullable DefenseBarManager defenseBarManager;

    private static final Logger log = Logger.getLogger("Cerberus");

    /** Per-player armor reduction (0.0–armorMaxReduction). The ONLY per-player state. */
    private final Map<UUID, Double> playerArmorReduction = new ConcurrentHashMap<>();

    public PlayerDefenseManager(SkillManager skillManager, PlayerVirtualHealthManager virtualHealthManager) {
        this.skillManager = skillManager;
        this.virtualHealthManager = virtualHealthManager;
    }

    public void setDefenseBarManager(DefenseBarManager defenseBarManager) {
        this.defenseBarManager = defenseBarManager;
    }

    /** Inject CombatConfig for reading armor values and caps. */
    public void setCombatConfig(CombatConfig config) {
        this.combatConfig = config;
    }

    /**
     * Recalculate armor reduction from equipped gear + skill bonuses.
     * Call on join, armor change, skill level up.
     */
    public void updateDefense(Player player) {
        double armorSum = calculateArmorFromGear(player);
        double skillBonus = calculateSkillArmorBonus(player);
        double cap = combatConfig != null ? combatConfig.armorMaxReduction : 0.40;

        double totalReduction = Math.min(cap, armorSum + skillBonus);
        playerArmorReduction.put(player.getUniqueId(), totalReduction);

        // Push to virtual health manager
        virtualHealthManager.setArmorReduction(player, totalReduction);

        // Set vanilla armor attribute for display (cosmetic only)
        if (player.getAttribute(Attribute.ARMOR) != null) {
            // Scale 0.0–0.40 to 0–20 for the vanilla armor display
            player.getAttribute(Attribute.ARMOR).setBaseValue(totalReduction * 50.0);
        }

        // Push elemental resistances from Survival skill
        applyElementalResistances(player);

        // Push defense bar bonus from Heavy Armor Training
        applyDefenseBarBonus(player);
    }

    /** Sum per-piece armor reduction from equipped gear using CombatConfig values. */
    private double calculateArmorFromGear(Player player) {
        if (combatConfig == null) return 0.0;

        double total = 0.0;
        ItemStack[] armorContents = player.getInventory().getArmorContents();
        for (ItemStack item : armorContents) {
            if (item != null) {
                total += combatConfig.getArmorReductionForPiece(item.getType());
            }
        }
        return total;
    }

    /** Calculate armor bonus from Heavy Armor Training skill. */
    private double calculateSkillArmorBonus(Player player) {
        if (skillManager == null) return 0.0;

        double bonus = 0.0;
        int hatLevel = skillManager.getSkillLevel(player, "Heavy Armor Training");
        if (hatLevel > 0 && combatConfig != null) {
            bonus += ScalingFormula.compute(hatLevel, combatConfig.hatArmorBonusScaling);
        }
        return bonus;
    }

    /**
     * Survival skill grants elemental resistances (fire, cold, drowning).
     * These are pushed to PlayerVirtualHealthManager's per-type resistance maps.
     */
    private void applyElementalResistances(Player player) {
        if (skillManager == null) return;

        int survivalLevel = skillManager.getSkillLevel(player, "Survival");
        if (survivalLevel <= 0) return;

        // Fire: 1% per level, cap 30%
        double fireResist = Math.min(0.30, survivalLevel * 0.01);
        virtualHealthManager.setFireResistance(player, fireResist);

        // Cold: 1% per level, cap 20%
        double coldResist = Math.min(0.20, survivalLevel * 0.01);
        virtualHealthManager.setColdResistance(player, coldResist);

        // Drowning: 1% per level, cap 20%
        double drowningResist = Math.min(0.20, survivalLevel * 0.01);
        virtualHealthManager.setDrowningResistance(player, drowningResist);
    }

    /** Heavy Armor Training grants bonus defense bar max points. */
    private void applyDefenseBarBonus(Player player) {
        if (skillManager == null || combatConfig == null) return;

        int hatLevel = skillManager.getSkillLevel(player, "Heavy Armor Training");
        if (hatLevel <= 0) return;

        double barBonus = ScalingFormula.compute(hatLevel, combatConfig.hatDefenseBarBonusScaling);
        if (defenseBarManager != null) {
            defenseBarManager.setBonusMaxPoints(player, barBonus);
        }
    }

    /** Get the current armor reduction for a player (0.0–cap). */
    public double getArmorReduction(Player player) {
        return playerArmorReduction.getOrDefault(player.getUniqueId(), 0.0);
    }

    /**
     * Remove per-player state. Idempotent, safe if absent.
     */
    public void removePlayer(UUID uuid) {
        playerArmorReduction.remove(uuid);
    }

    /** Armor % display string for HUD. */
    public String getDefenseDisplay(Player player) {
        double reduction = getArmorReduction(player);
        return String.format("Armor: %.0f%%", reduction * 100);
    }
}

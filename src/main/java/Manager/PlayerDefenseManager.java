package Manager;

import Skills.SkillManager;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.Material;
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
    private @Nullable Attributes.AttributeService attributes;

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

    /** Fortitude: armor, resistances and defense bar on top of gear and skill bonuses. */
    public void setAttributeService(Attributes.AttributeService attributes) {
        this.attributes = attributes;
    }

    /** The player's attributes when the Fortitude integration is on, else null. */
    private @Nullable Attributes.AttributeSnapshot fortitude(Player player) {
        if (attributes == null || !attributes.rules().switches().fortitude()) return null;
        return attributes.get(player.getUniqueId());
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
        // Runs at the start of every hit on the player: read each skill level once.
        int heavyArmor = skillManager != null ? skillManager.getSkillLevel(player, "Heavy Armor Training") : 0;
        int survival = skillManager != null ? skillManager.getSkillLevel(player, "Survival") : 0;
        Attributes.AttributeSnapshot fort = fortitude(player);
        double armorSum = calculateArmorFromGear(player);
        double skillBonus = combatConfig != null ? heavyArmorBonus(combatConfig, heavyArmor) : 0.0;
        double cap = combatConfig != null ? combatConfig.armorMaxReduction : 0.40;
        pushPvpBaseline(player, Math.min(cap, armorSum + skillBonus), survival, heavyArmor);
        double fortArmor = 0.0;
        if (fort != null) {
            fortArmor = fort.armorBonus();                       // Fortitude adds on top of gear + Heavy Armor Training
            cap = Math.min(cap, fort.rules().limits().armor());
        }

        double totalReduction = Math.min(cap, armorSum + skillBonus + fortArmor);
        playerArmorReduction.put(player.getUniqueId(), totalReduction);

        // Push to virtual health manager
        virtualHealthManager.setArmorReduction(player, totalReduction);

        // Set vanilla armor attribute for display (cosmetic only)
        if (player.getAttribute(Attribute.ARMOR) != null) {
            // Scale 0.0–0.40 to 0–20 for the vanilla armor display
            player.getAttribute(Attribute.ARMOR).setBaseValue(totalReduction * 50.0);
        }

        // Push elemental resistances from Survival skill (+ Fortitude)
        applyElementalResistances(player, fort, survival);

        // Push defense bar bonus from Heavy Armor Training (+ Fortitude), scaled to the realm
        double realm = attributes != null ? attributes.get(player.getUniqueId()).realmMultiplier() : 1.0;
        applyDefenseBarBonus(player, fort, realm, heavyArmor);
    }

    /**
     * Defense as it was before Attributes 2.0 (no Fortitude, no Vitality, no realm), which PvP
     * hits are worked out with unless attributes.yml integration.pvp-defense is true.
     */
    private void pushPvpBaseline(Player player, double armor, int survival, int heavyArmor) {
        double bar = defenseBarManager != null ? defenseBarManager.getBaseMaxPoints() : 0.0;
        if (combatConfig != null) bar += heavyArmorBarBonus(combatConfig, heavyArmor);
        virtualHealthManager.setPvpBaseline(player, new PlayerVirtualHealthManager.PvpBaseline(armor,
                survivalFireResist(survival), survivalColdResist(survival), survivalDrowningResist(survival),
                bar, HealthLifecycleEngine.DEFAULT_MAX_HP));
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

    // ── The skill formulas, shared with the Player Menu skill pages ──────

    /** Heavy Armor Training: armor reduction added to the gear's. */
    public static double heavyArmorBonus(CombatConfig cfg, int level) {
        return level > 0 ? ScalingFormula.compute(level, cfg.hatArmorBonusScaling) : 0.0;
    }

    /** Heavy Armor Training: extra defense bar points (before the realm multiplies the bar). */
    public static double heavyArmorBarBonus(CombatConfig cfg, int level) {
        return level > 0 ? ScalingFormula.compute(level, cfg.hatDefenseBarBonusScaling) : 0.0;
    }

    /** Heavy armor = iron, diamond or netherite (trains Heavy Armor Training; its kill fallback). */
    public static boolean isHeavyArmor(Material material) {
        String name = material.name();
        return name.startsWith("IRON_") || name.startsWith("DIAMOND_") || name.startsWith("NETHERITE_");
    }

    /** True when at least one worn piece is heavy armor. */
    public static boolean wearsHeavyArmor(Player player) {
        for (ItemStack piece : player.getInventory().getArmorContents()) {
            if (piece != null && isHeavyArmor(piece.getType())) return true;
        }
        return false;
    }

    /** Survival: fire resistance, 1% per level up to 30%. */
    public static double survivalFireResist(int level) {
        return Math.min(0.30, Math.max(0, level) * 0.01);
    }

    /** Survival: cold resistance, 1% per level up to 20%. */
    public static double survivalColdResist(int level) {
        return Math.min(0.20, Math.max(0, level) * 0.01);
    }

    /** Survival: drowning resistance, 1% per level up to 20%. */
    public static double survivalDrowningResist(int level) {
        return Math.min(0.20, Math.max(0, level) * 0.01);
    }

    /**
     * Survival skill grants elemental resistances (fire, cold, drowning).
     * These are pushed to PlayerVirtualHealthManager's per-type resistance maps.
     */
    private void applyElementalResistances(Player player, @Nullable Attributes.AttributeSnapshot fort, int survivalLevel) {
        if (skillManager == null) return;

        // No early return at Survival 0: Fortitude from other skills (Heavy Armor...) still counts.

        // Fortitude adds the same amount to each, within the attribute resist limit.
        double fortResist = fort != null ? fort.resist() : 0.0;
        double limit = fort != null ? fort.rules().limits().resist() : 1.0;

        virtualHealthManager.setFireResistance(player, Math.min(limit, survivalFireResist(survivalLevel) + fortResist));
        virtualHealthManager.setColdResistance(player, Math.min(limit, survivalColdResist(survivalLevel) + fortResist));
        virtualHealthManager.setDrowningResistance(player, Math.min(limit, survivalDrowningResist(survivalLevel) + fortResist));
    }

    /**
     * Heavy Armor Training grants bonus defense bar max points; Fortitude scales the base bar; the
     * realm multiplies the whole bar (it absorbs hits measured in the realm's magnitude).
     */
    private void applyDefenseBarBonus(Player player, @Nullable Attributes.AttributeSnapshot fort, double realm, int hatLevel) {
        if (skillManager == null || combatConfig == null || defenseBarManager == null) return;

        double base = defenseBarManager.getBaseMaxPoints();
        double bar = base + heavyArmorBarBonus(combatConfig, hatLevel);
        if (fort != null) bar += base * (fort.defenseBarMultiplier() - 1.0);
        bar *= (Double.isFinite(realm) && realm > 0 ? realm : 1.0);
        defenseBarManager.setBonusMaxPoints(player, Math.max(0.0, bar - base));
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

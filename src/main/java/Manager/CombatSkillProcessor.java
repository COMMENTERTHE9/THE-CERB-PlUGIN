package Manager;

import Skills.*;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Random;
import java.util.logging.Logger;

/**
 * Integration layer that applies combat skill effects through the damage pipeline.
 * Orchestrates explicit named phases — never holds direct skill class references,
 * always goes through {@link SkillManager}.
 * <p>
 * All skill scaling uses {@link ScalingFormula#compute(int, ScalingFormula.ScalingParams)}
 * driven by per-stat config from {@link CombatConfig}.
 * <p>
 * Anti-bloat rule: individual skill calculations live in private helper methods
 * (one per skill). If any helper grows beyond ~20 lines, extract to a
 * skill-specific handler class.
 */
public class CombatSkillProcessor {
    private static final Logger log = Logger.getLogger("Cerberus");
    private final SkillManager skillManager;
    private final CombatConfig config;
    private final ElementalInfusionManager elementalManager;
    private final PlayerVirtualHealthManager virtualHealthManager;
    private final Random random = new Random();

    /** Critical Strike XP per melee hit on a mob, and per critical hit. */
    public static final double CRIT_STRIKE_HIT_XP = 2.0;
    public static final double CRIT_STRIKE_CRIT_XP = 8.0;
    /** Heavy Armor Training XP per point of damage taken in heavy armor, capped per hit. */
    public static final double HEAVY_ARMOR_XP_PER_DAMAGE = 0.5;
    public static final double HEAVY_ARMOR_XP_MAX = 10.0;
    /** Survival XP per hit taken. */
    public static final double SURVIVAL_HIT_XP = 1.0;

    public CombatSkillProcessor(SkillManager skillManager,
                                CombatConfig config,
                                ElementalInfusionManager elementalManager,
                                PlayerVirtualHealthManager virtualHealthManager) {
        this.skillManager = skillManager;
        this.config = config;
        this.elementalManager = elementalManager;
        this.virtualHealthManager = virtualHealthManager;
    }

    // ═══════════════════════════════════════════════════════════════
    // ATTACK PHASE — called when attacker is a Player
    // Order: base additive → multiplicative (crits) → elemental → lifesteal calc
    // ═══════════════════════════════════════════════════════════════

    public void processAttackerSkills(CombatContext ctx) {
        Player attacker = ctx.getAttacker();
        if (attacker == null) return;

        // ── Phase 1: Base damage modifiers (additive) ────────────
        double baseMod = 1.0;
        baseMod += getBladeMasteryBonus(ctx);
        baseMod += getWeaponMasteryDamageBonus(ctx);
        double modified = ctx.getRawDamage() * baseMod;
        ctx.attack().setModifiedDamage(modified);
        ctx.appendTrace("BaseModifiers", ctx.getRawDamage(), modified, "x" + String.format("%.2f", baseMod));

        // ── Phase 2: Multiplicative modifiers (crits) ────────────
        applyCriticalStrike(ctx);

        // ── Phase 3: Elemental bonus (additive, after crits) ─────
        applyElementalInfusion(ctx);

        // ── Phase 4: Post-damage calculations (lifesteal) ────────
        double lifesteal = ctx.attack().getModifiedDamage() * getLifestealPercent(ctx);
        ctx.postDamage().setLifestealAmount(lifesteal);
        if (lifesteal > 0) {
            ctx.appendTrace("Lifesteal", 0, lifesteal, null);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // DEFENSE PHASE — called when target is a Player
    // Currently: knockback resistance from Martial Expertise
    // ═══════════════════════════════════════════════════════════════

    public void processDefenderSkills(CombatContext ctx) {
        if (!(ctx.getTarget() instanceof Player defender)) return;

        // Martial Expertise: knockback resistance
        double kbReduction = getMartialExpertiseKBResist(defender);
        if (kbReduction > 0) {
            ctx.appendTrace("MartialKBResist", 1.0, 1.0 - kbReduction, "-" + String.format("%.0f%%", kbReduction * 100));
        }
        // Note: knockback is handled by vanilla — this trace is for debug visibility only
    }

    // ═══════════════════════════════════════════════════════════════
    // POST-DAMAGE PHASE — called after defense pipeline completes
    // Effects: lifesteal healing, disarm, elemental application
    // ═══════════════════════════════════════════════════════════════

    public void applyPostDamageEffects(CombatContext ctx) {
        Player attacker = ctx.getAttacker();
        if (attacker == null) return;

        // Apply lifesteal healing
        double lifesteal = ctx.postDamage().getLifestealAmount();
        if (lifesteal > 0 && ctx.defense().getFinalDamage() > 0) {
            virtualHealthManager.heal(attacker, lifesteal);
            ctx.appendTrace("LifestealApplied", 0, lifesteal, "healed");
        }

        // Check disarm from Martial Expertise
        if (ctx.getTarget() instanceof Player target) {
            applyDisarmCheck(ctx, attacker, target);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Private skill helpers — one per skill, all using ScalingFormula
    // ═══════════════════════════════════════════════════════════════

    /** Blade Mastery: +damage per level when holding a sword. */
    private double getBladeMasteryBonus(CombatContext ctx) {
        Player attacker = ctx.getAttacker();
        if (attacker == null) return 0;

        int level = skillManager.getSkillLevel(attacker, "Blade Mastery");
        if (level <= 0) return 0;

        // Only applies when holding a sword
        ItemStack mainHand = attacker.getInventory().getItemInMainHand();
        if (!isSword(mainHand.getType())) return 0;

        return bladeMasteryBonus(config, level);
    }

    // ── The skill formulas, shared with the Player Menu skill pages ──────

    /** Blade Mastery: extra sword damage against players (0.12 = +12%, added to the base). */
    public static double bladeMasteryBonus(CombatConfig cfg, int level) {
        return level > 0 ? ScalingFormula.compute(level, cfg.bmDamageScaling) : 0.0;
    }

    /** Weapon Mastery: extra damage against players (the curve includes the 1.0 base). */
    public static double weaponMasteryBonus(CombatConfig cfg, int level) {
        return level > 0 ? ScalingFormula.compute(level, cfg.wmDamageScaling) - 1.0 : 0.0;
    }

    /** Weapon Mastery: share of damage dealt that heals the attacker. */
    public static double weaponMasteryLifesteal(CombatConfig cfg, int level) {
        return level > 0 ? ScalingFormula.compute(level, cfg.wmLifestealScaling) : 0.0;
    }

    /** Critical Strike against players: chance per hit. */
    public static double critChance(CombatConfig cfg, int level) {
        return level > 0 ? ScalingFormula.compute(level, cfg.critChanceScaling) : 0.0;
    }

    /** Critical Strike against players: damage multiplier of a critical hit. */
    public static double critMultiplier(CombatConfig cfg, int level) {
        return level > 0 ? ScalingFormula.compute(level, cfg.critMultiplierScaling) : 1.0;
    }

    /** Weapon Mastery: scaled damage multiplier bonus. */
    private double getWeaponMasteryDamageBonus(CombatContext ctx) {
        Player attacker = ctx.getAttacker();
        if (attacker == null) return 0;
        int level = skillManager.getSkillLevel(attacker, "Weapon Mastery");
        if (level <= 0) return 0;
        // The scaling params include the base (e.g., 1.15), so subtract 1.0 for the bonus portion
        return weaponMasteryBonus(config, level);
    }

    /** Critical Strike: roll chance, multiply if hit. Both use ScalingFormula. */
    private void applyCriticalStrike(CombatContext ctx) {
        Player attacker = ctx.getAttacker();
        if (attacker == null) return;
        int level = skillManager.getSkillLevel(attacker, "Critical Strike");
        if (level <= 0) return;

        double chance = critChance(config, level);
        if (random.nextDouble() < chance) {
            double multiplier = critMultiplier(config, level);
            double before = ctx.attack().getModifiedDamage();
            double after = before * multiplier;
            ctx.attack().setModifiedDamage(after);
            ctx.attack().setCritical(true);
            ctx.attack().setCritMultiplier(multiplier);
            ctx.appendTrace("CriticalStrike", before, after, String.format("%.1fx", multiplier));
        }
    }

    /** Elemental Infusion: add bonus elemental damage from weapon infusion. */
    private void applyElementalInfusion(CombatContext ctx) {
        Player attacker = ctx.getAttacker();
        if (attacker == null || elementalManager == null) return;

        ItemStack weapon = attacker.getInventory().getItemInMainHand();
        if (!elementalManager.hasElementalInfusion(weapon)) return;

        CustomTags.ElementalTag tag = elementalManager.getElementalInfusion(weapon);
        if (tag != null) {
            double bonus = ctx.getRawDamage() * (tag.getDamageMultiplier() - 1.0);
            if (bonus > 0) {
                ctx.postDamage().setBonusElementalDamage(bonus);
                ctx.postDamage().setElementalType(mapElementToCustomDamageType(tag.getElementType()));
                ctx.appendTrace("Elemental", 0, bonus, tag.getElementType().name());
            }
        }
    }

    /** Weapon Mastery: lifesteal percent, scaled. */
    private double getLifestealPercent(CombatContext ctx) {
        Player attacker = ctx.getAttacker();
        if (attacker == null) return 0;
        int level = skillManager.getSkillLevel(attacker, "Weapon Mastery");
        if (level <= 0) return 0;
        return weaponMasteryLifesteal(config, level);
    }

    /** Martial Expertise: knockback resistance bonus, scaled. */
    private double getMartialExpertiseKBResist(Player player) {
        int level = skillManager.getSkillLevel(player, "Martial Expertise");
        if (level <= 0) return 0;
        return ScalingFormula.compute(level, config.meKbResistScaling);
    }

    /** Martial Expertise: disarm check against target, scaled chance. */
    private void applyDisarmCheck(CombatContext ctx, Player attacker, Player target) {
        int level = skillManager.getSkillLevel(attacker, "Martial Expertise");
        if (level <= 0) return;

        double chance = ScalingFormula.compute(level, config.meDisarmScaling);
        if (random.nextDouble() < chance) {
            ctx.postDamage().setDisarmTriggered(true);
            ctx.appendTrace("Disarm", 0, 1, "triggered");
            // Actual disarm effect (weapon removal + restore) handled by MartialExpertiseSkill
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // PER-HIT XP — called after each damage event (not just kills)
    // Awards small XP for combat actions: hitting, critting, taking damage
    // ═══════════════════════════════════════════════════════════════

    /**
     * Award attacker XP for landing a hit. Called from VirtualHealthListener
     * after the damage pipeline completes.
     */
    public void awardAttackerHitXp(CombatContext ctx, org.bukkit.entity.Player attacker) {
        if (attacker == null || ctx.getRawDamage() <= 0) return;

        // Critical Strike: always gains XP from melee hits, bonus for crits
        double critXp = ctx.attack().isCritical() ? CRIT_STRIKE_CRIT_XP : CRIT_STRIKE_HIT_XP;
        skillManager.addXpAndCheckLevelUp("Critical Strike", critXp, attacker);
    }

    /**
     * Award defender XP for taking damage. Called from VirtualHealthListener
     * when a player takes damage from a mob or another player.
     */
    public void awardDefenderHitXp(org.bukkit.entity.Player defender, double damageTaken) {
        if (defender == null || damageTaken <= 0) return;

        // Heavy Armor Training: XP from taking hits while wearing heavy armor
        if (hasHeavyArmor(defender)) {
            double hatXp = Math.min(damageTaken * HEAVY_ARMOR_XP_PER_DAMAGE, HEAVY_ARMOR_XP_MAX);
            skillManager.addXpAndCheckLevelUp("Heavy Armor Training", hatXp, defender);
        }

        // Survival: small XP for enduring any damage
        skillManager.addXpAndCheckLevelUp("Survival", SURVIVAL_HIT_XP, defender);
    }


    private boolean hasHeavyArmor(org.bukkit.entity.Player player) {
        return PlayerDefenseManager.wearsHeavyArmor(player);
    }

    // ═══════════════════════════════════════════════════════════════
    // Utility
    // ═══════════════════════════════════════════════════════════════

    private boolean isSword(Material mat) {
        return mat == Material.WOODEN_SWORD || mat == Material.STONE_SWORD
                || mat == Material.IRON_SWORD || mat == Material.GOLDEN_SWORD
                || mat == Material.DIAMOND_SWORD || mat == Material.NETHERITE_SWORD;
    }

    private CustomDamageType mapElementToCustomDamageType(CustomTags.ElementalTag.ElementType elementType) {
        return switch (elementType) {
            case FIRE -> CustomDamageType.PYROMANCY;
            case ICE -> CustomDamageType.CRYOMANCY;
            case LIGHTNING -> CustomDamageType.ELECTROMANCY;
            case DARK -> CustomDamageType.DARK_ARTS;
            case ARCANE -> CustomDamageType.ARCANE_BLAST;
            case EARTH -> CustomDamageType.GEOMANCY;
            case WIND -> CustomDamageType.AEROMANCY;
            case BINDING -> CustomDamageType.MYSTIC_BINDING;
        };
    }
}

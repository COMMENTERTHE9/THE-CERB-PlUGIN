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
            virtualHealthManager.increasePlayerVirtualHealth(attacker, lifesteal);
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

        Skill skill = skillManager.getSkill("Blade Mastery");
        if (skill == null || skill.getLevel() <= 0) return 0;

        // Only applies when holding a sword
        ItemStack mainHand = attacker.getInventory().getItemInMainHand();
        if (!isSword(mainHand.getType())) return 0;

        return ScalingFormula.compute(skill.getLevel(), config.bmDamageScaling);
    }

    /** Weapon Mastery: scaled damage multiplier bonus. */
    private double getWeaponMasteryDamageBonus(CombatContext ctx) {
        Skill skill = skillManager.getSkill("Weapon Mastery");
        if (skill == null || skill.getLevel() <= 0) return 0;
        // The scaling params include the base (e.g., 1.15), so subtract 1.0 for the bonus portion
        return ScalingFormula.compute(skill.getLevel(), config.wmDamageScaling) - 1.0;
    }

    /** Critical Strike: roll chance, multiply if hit. Both use ScalingFormula. */
    private void applyCriticalStrike(CombatContext ctx) {
        Skill skill = skillManager.getSkill("Critical Strike");
        if (skill == null || skill.getLevel() <= 0) return;

        double chance = ScalingFormula.compute(skill.getLevel(), config.critChanceScaling);
        if (random.nextDouble() < chance) {
            double multiplier = ScalingFormula.compute(skill.getLevel(), config.critMultiplierScaling);
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
        Skill skill = skillManager.getSkill("Weapon Mastery");
        if (skill == null || skill.getLevel() <= 0) return 0;
        return ScalingFormula.compute(skill.getLevel(), config.wmLifestealScaling);
    }

    /** Martial Expertise: knockback resistance bonus, scaled. */
    private double getMartialExpertiseKBResist(Player player) {
        Skill skill = skillManager.getSkill("Martial Expertise");
        if (skill == null || skill.getLevel() <= 0) return 0;
        return ScalingFormula.compute(skill.getLevel(), config.meKbResistScaling);
    }

    /** Martial Expertise: disarm check against target, scaled chance. */
    private void applyDisarmCheck(CombatContext ctx, Player attacker, Player target) {
        Skill skill = skillManager.getSkill("Martial Expertise");
        if (skill == null || skill.getLevel() <= 0) return;

        double chance = ScalingFormula.compute(skill.getLevel(), config.meDisarmScaling);
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
        double critXp = 2.0;
        if (ctx.attack().isCritical()) critXp = 8.0;
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
            double hatXp = Math.min(damageTaken * 0.5, 10.0);
            skillManager.addXpAndCheckLevelUp("Heavy Armor Training", hatXp, defender);
        }

        // Survival: small XP for enduring any damage
        skillManager.addXpAndCheckLevelUp("Survival", 1.0, defender);
    }


    private boolean hasHeavyArmor(org.bukkit.entity.Player player) {
        for (org.bukkit.inventory.ItemStack piece : player.getInventory().getArmorContents()) {
            if (piece == null) continue;
            String name = piece.getType().name();
            if (name.startsWith("IRON_") || name.startsWith("DIAMOND_") || name.startsWith("NETHERITE_")) {
                return true;
            }
        }
        return false;
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

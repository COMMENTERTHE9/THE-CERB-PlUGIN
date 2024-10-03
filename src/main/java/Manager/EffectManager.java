package Manager;

import Skills.SkillManager;
import Skills.SpellWeavingSkill;
import Skills.SummoningSkill;
import Skills.ManaRegenerationSkill;
import cerberus.world.cerb.CustomPlayer;
import org.bukkit.entity.Player;

public class EffectManager {

    private final PlayerVirtualHealthManager healthManager;
    private final PlayerDefenseManager defenseManager;
    private final PlayerManaManager manaManager;
    private final SkillManager skillManager;

    public EffectManager(PlayerVirtualHealthManager healthManager, PlayerDefenseManager defenseManager,
                         PlayerManaManager manaManager, SkillManager skillManager) {
        this.healthManager = healthManager;
        this.defenseManager = defenseManager;
        this.manaManager = manaManager;
        this.skillManager = skillManager;
    }

    // Method to apply effects from combat skills
    public void applyCombatSkillEffect(Player player, String skillName) {
        switch (skillName) {
            case "Blade Mastery":
                applyBladeMasteryEffect(player);
                break;
            case "Dual Wielding":
                applyDualWieldingEffect(player);
                break;
            case "Heavy Armor Training":
                applyHeavyArmorTrainingEffect(player);
                break;
            // Add other combat skills here
        }
    }

    // Method to apply effects from magic skills
    public void applyMagicSkillEffect(Player player, String skillName) {
        switch (skillName) {
            case "Intelligence":
                applyIntelligenceEffect(player);
                break;
            case "Arcane Knowledge":
                applyArcaneKnowledgeEffect(player);
                break;
            case "Elemental Mastery":
                applyElementalMasteryEffect(player);
                break;
            case "Spell Weaving":
                applySpellWeavingEffect(player);
                break;
            case "Summoning":
                applySummoningEffect(player);
                break;
            case "Mana Regeneration":
                applyManaRegenerationEffect(player);
                break;
            // Add other magic skills here
        }
    }

    // Blade Mastery effect logic
    private void applyBladeMasteryEffect(Player player) {
        double baseDamage = player.getAttribute(org.bukkit.attribute.Attribute.GENERIC_ATTACK_DAMAGE).getBaseValue();
        player.getAttribute(org.bukkit.attribute.Attribute.GENERIC_ATTACK_DAMAGE).setBaseValue(baseDamage * 1.10); // 10% increase
    }

    // Dual Wielding effect logic
    private void applyDualWieldingEffect(Player player) {
        double baseDamage = player.getAttribute(org.bukkit.attribute.Attribute.GENERIC_ATTACK_DAMAGE).getBaseValue();
        player.getAttribute(org.bukkit.attribute.Attribute.GENERIC_ATTACK_DAMAGE).setBaseValue(baseDamage * 1.20); // 20% increase
        double baseSpeed = player.getAttribute(org.bukkit.attribute.Attribute.GENERIC_ATTACK_SPEED).getBaseValue();
        player.getAttribute(org.bukkit.attribute.Attribute.GENERIC_ATTACK_SPEED).setBaseValue(baseSpeed * 1.10); // 10% increase
    }

    // Heavy Armor Training effect logic
    private void applyHeavyArmorTrainingEffect(Player player) {
        double baseDefense = defenseManager.getDefense(player);
        defenseManager.setDefense(player, baseDefense * 1.10); // 10% increase
        double baseHealth = healthManager.getMaxHealth(player);
        healthManager.setMaxHealth(player, baseHealth + 20); // Example health increase
    }

    // Intelligence skill effect logic
    private void applyIntelligenceEffect(Player player) {
        double manaBonus = 100.0; // Example mana increase
        double spellPowerBonus = 1.10; // Example spell power increase
        manaManager.setMaxMana(manaManager.getMaxMana(player) + manaBonus); // Adjusted to match method signature
        // Assume you have a method to set spell power in the spell system
        // spellManager.setSpellPower(player, spellPowerBonus);
    }

    // Arcane Knowledge effect logic
    private void applyArcaneKnowledgeEffect(Player player) {
        double spellRangeBonus = 1.20; // Example spell range increase
        double manaCostReduction = 0.90; // Example mana cost reduction (10% less)
        // Assume you have methods to set spell range and mana cost in the spell system
        // spellManager.setSpellRange(player, spellRangeBonus);
        // spellManager.setManaCost(player, manaCostReduction);
    }

    // Elemental Mastery effect logic
    private void applyElementalMasteryEffect(Player player) {
        double elementalDamageBonus = 1.15; // Example elemental damage increase
        // Assume you have methods to set elemental damage in the spell system
        // spellManager.setElementalDamage(player, elementalDamageBonus);
    }

    // Spell Weaving effect logic
    private void applySpellWeavingEffect(Player player) {
        SpellWeavingSkill skill = (SpellWeavingSkill) skillManager.getSkill("Spell Weaving");
        if (skill != null) {
            skill.applyEffect(skillManager.getCustomPlayer(player.getUniqueId()));
        }
    }

    // Summoning effect logic
    private void applySummoningEffect(Player player) {
        SummoningSkill skill = (SummoningSkill) skillManager.getSkill("Summoning");
        if (skill != null) {
            skill.applyEffect(skillManager.getCustomPlayer(player.getUniqueId()));
        }
    }

    // Mana Regeneration effect logic
    private void applyManaRegenerationEffect(Player player) {
        ManaRegenerationSkill skill = (ManaRegenerationSkill) skillManager.getSkill("Mana Regeneration");
        if (skill != null) {
            skill.applyEffect(skillManager.getCustomPlayer(player.getUniqueId()));
        }
    }

    // Method to apply custom spell effects
    public void applySpellEffect(Player player, String spellName) {
        // Logic to handle custom spells and their effects
        switch (spellName) {
            case "Fireball":
                // Apply fireball spell effects
                break;
            case "Ice Spike":
                // Apply ice spike spell effects
                break;
            // Add other spells here
        }
    }

    // Method to manage health effects
    public void applyHealthEffect(Player player, double healthBonus) {
        healthManager.setMaxHealth(player, healthManager.getMaxHealth(player) + healthBonus);
    }

    // Method to manage defense effects
    public void applyDefenseEffect(Player player, double defenseBonus) {
        defenseManager.setDefense(player, defenseManager.getDefense(player) + defenseBonus);
    }

    // Method to manage mana effects
    public void applyManaEffect(Player player, double manaBonus) {
        manaManager.setMaxMana(manaManager.getMaxMana(player) + manaBonus); // Adjusted to match method signature
    }

    // Optional: Method to remove or reverse effects if needed
    public void removeEffect(Player player, String effectName) {
        // Logic to reverse or remove an effect from a player
    }

    // Add other utility methods to handle effects as needed
}

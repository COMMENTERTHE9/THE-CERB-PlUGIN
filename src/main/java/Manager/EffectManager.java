package Manager;

import Skills.SkillManager;
import Skills.SpellWeavingSkill;
import Skills.SummoningSkill;
import Skills.ManaRegenerationSkill;
import cerberus.world.cerb.CustomPlayer;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class EffectManager {

    private final PlayerVirtualHealthManager healthManager;
    private final PlayerDefenseManager defenseManager;
    private final PlayerManaManager manaManager;
    private final SkillManager skillManager;

    // Tracks which effects are active per player, and the original values before the effect was applied
    // Structure: playerUUID → effectName → (attributeKey → originalValue)
    private final Map<UUID, Map<String, Map<String, Double>>> activeEffects = new ConcurrentHashMap<>();

    public EffectManager(PlayerVirtualHealthManager healthManager, PlayerDefenseManager defenseManager,
                         PlayerManaManager manaManager, SkillManager skillManager) {
        this.healthManager = healthManager;
        this.defenseManager = defenseManager;
        this.manaManager = manaManager;
        this.skillManager = skillManager;
    }

    /** Check if a player already has an effect active */
    public boolean hasEffect(Player player, String effectName) {
        Map<String, Map<String, Double>> playerEffects = activeEffects.get(player.getUniqueId());
        return playerEffects != null && playerEffects.containsKey(effectName);
    }

    /** Track original values before applying an effect */
    private Map<String, Double> beginEffect(Player player, String effectName) {
        Map<String, Map<String, Double>> playerEffects =
                activeEffects.computeIfAbsent(player.getUniqueId(), k -> new ConcurrentHashMap<>());
        Map<String, Double> originals = new HashMap<>();
        playerEffects.put(effectName, originals);
        return originals;
    }

    /** Get all active effect names for a player */
    public Set<String> getActiveEffects(Player player) {
        Map<String, Map<String, Double>> playerEffects = activeEffects.get(player.getUniqueId());
        return playerEffects != null ? new HashSet<>(playerEffects.keySet()) : Set.of();
    }

    /** Clean up all effects for a player (call on quit/death) */
    public void removeAllEffects(Player player) {
        Set<String> effects = getActiveEffects(player);
        for (String effect : effects) {
            removeEffect(player, effect);
        }
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
        if (hasEffect(player, "Blade Mastery")) return;
        Map<String, Double> originals = beginEffect(player, "Blade Mastery");

        double baseDamage = player.getAttribute(org.bukkit.attribute.Attribute.ATTACK_DAMAGE).getBaseValue();
        originals.put("attackDamage", baseDamage);
        player.getAttribute(org.bukkit.attribute.Attribute.ATTACK_DAMAGE).setBaseValue(baseDamage * 1.10);
    }

    // Dual Wielding effect logic
    private void applyDualWieldingEffect(Player player) {
        if (hasEffect(player, "Dual Wielding")) return;
        Map<String, Double> originals = beginEffect(player, "Dual Wielding");

        double baseDamage = player.getAttribute(org.bukkit.attribute.Attribute.ATTACK_DAMAGE).getBaseValue();
        originals.put("attackDamage", baseDamage);
        player.getAttribute(org.bukkit.attribute.Attribute.ATTACK_DAMAGE).setBaseValue(baseDamage * 1.20);

        double baseSpeed = player.getAttribute(org.bukkit.attribute.Attribute.ATTACK_SPEED).getBaseValue();
        originals.put("attackSpeed", baseSpeed);
        player.getAttribute(org.bukkit.attribute.Attribute.ATTACK_SPEED).setBaseValue(baseSpeed * 1.10);
    }

    // Heavy Armor Training effect logic
    private void applyHeavyArmorTrainingEffect(Player player) {
        if (hasEffect(player, "Heavy Armor Training")) return;
        Map<String, Double> originals = beginEffect(player, "Heavy Armor Training");

        // Defense now comes from gear; recalculate instead of setting directly
        defenseManager.updateDefense(player);

        double baseHealth = healthManager.getMaxHealth(player);
        originals.put("maxHealth", baseHealth);
        healthManager.setMaxHealth(player, baseHealth + 20);
    }

    // Intelligence skill effect logic
    private void applyIntelligenceEffect(Player player) {
        if (hasEffect(player, "Intelligence")) return;
        Map<String, Double> originals = beginEffect(player, "Intelligence");

        double baseMana = manaManager.getMaxMana(player);
        originals.put("maxMana", baseMana);
        manaManager.setMaxMana(baseMana + 100.0);
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
        String key = "health_" + healthBonus;
        if (hasEffect(player, key)) return;
        Map<String, Double> originals = beginEffect(player, key);
        originals.put("maxHealth", healthManager.getMaxHealth(player));
        healthManager.setMaxHealth(player, healthManager.getMaxHealth(player) + healthBonus);
    }

    // Method to manage defense effects — recalculates defense from gear
    public void applyDefenseEffect(Player player, double defenseBonus) {
        String key = "defense_" + defenseBonus;
        if (hasEffect(player, key)) return;
        beginEffect(player, key);
        // Defense now comes from gear; just recalculate
        defenseManager.updateDefense(player);
    }

    // Method to manage mana effects
    public void applyManaEffect(Player player, double manaBonus) {
        String key = "mana_" + manaBonus;
        if (hasEffect(player, key)) return;
        Map<String, Double> originals = beginEffect(player, key);
        originals.put("maxMana", manaManager.getMaxMana(player));
        manaManager.setMaxMana(manaManager.getMaxMana(player) + manaBonus);
    }

    /**
     * Remove an effect by restoring the original values that were captured when the effect was applied.
     */
    public void removeEffect(Player player, String effectName) {
        Map<String, Map<String, Double>> playerEffects = activeEffects.get(player.getUniqueId());
        if (playerEffects == null) return;

        Map<String, Double> originals = playerEffects.remove(effectName);
        if (originals == null) return;

        // Restore each tracked original value
        for (Map.Entry<String, Double> entry : originals.entrySet()) {
            switch (entry.getKey()) {
                case "attackDamage" ->
                    player.getAttribute(org.bukkit.attribute.Attribute.ATTACK_DAMAGE)
                          .setBaseValue(entry.getValue());
                case "attackSpeed" ->
                    player.getAttribute(org.bukkit.attribute.Attribute.ATTACK_SPEED)
                          .setBaseValue(entry.getValue());
                case "defense" ->
                    defenseManager.updateDefense(player);
                case "maxHealth" ->
                    healthManager.setMaxHealth(player, entry.getValue());
                case "maxMana" ->
                    manaManager.setMaxMana(entry.getValue());
            }
        }

        // Clean up empty maps
        if (playerEffects.isEmpty()) {
            activeEffects.remove(player.getUniqueId());
        }
    }
}

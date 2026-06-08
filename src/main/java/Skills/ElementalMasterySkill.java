package Skills;

import Manager.PlayerManaManager;
import Spells.SpellManager;
import org.bukkit.entity.Player;

public class ElementalMasterySkill extends MagicSkill {
    private final PlayerManaManager manaManager;
    private final SpellManager spellManager;

    public ElementalMasterySkill(String name, PlayerManaManager manaManager, SpellManager spellManager) {
        super(name);
        this.manaManager = manaManager;
        this.spellManager = spellManager;
    }

    @Override
    public void applyEffect(Player player) {
        if (player == null) return;

        int level = this.getLevel(); // Get the current skill level

        // Apply general elemental mastery effects
        applyElementalDamageMultiplier(player, 1.0 + 0.07 * level);
        applyElementalControlMultiplier(player, 1.0 + 0.05 * level);

        // Apply specific elemental effects
        applyArcaneBlast(player, level);
        applyPyromancy(player, level);
        applyCryomancy(player, level);
        applyElectromancy(player, level);
        applyDarkArts(player, level);
        applyMysticBinding(player, level);
        applyGeomancy(player, level);
        applyAeromancy(player, level);
    }

    // General elemental damage multiplier
    private void applyElementalDamageMultiplier(Player player, double multiplier) {
        spellManager.applyElementalDamageMultiplier(player, multiplier);
    }

    // General control multiplier (e.g., accuracy, range)
    private void applyElementalControlMultiplier(Player player, double multiplier) {
        spellManager.applyElementalControlMultiplier(player, multiplier);
    }

    // Arcane Blast: Amplifies raw magical energy for powerful attacks
    private void applyArcaneBlast(Player player, int level) {
        double arcaneBlastMultiplier = 1.0 + 0.1 * level;
        double criticalChance = 0.05 * level; // 5% crit chance per level
        spellManager.applyArcaneBlastMultiplier(player, arcaneBlastMultiplier, criticalChance);
    }

    // Pyromancy: Mastery of fire-based spells, causing burn damage
    private void applyPyromancy(Player player, int level) {
        double fireDamageMultiplier = 1.0 + 0.08 * level;
        int burnDuration = 4 + level; // Burn duration increases with level
        spellManager.applyFireDamageMultiplier(player, fireDamageMultiplier, burnDuration);
    }

    // Cryomancy: Mastery of ice and frost-based magic, slowing enemies
    private void applyCryomancy(Player player, int level) {
        double iceDamageMultiplier = 1.0 + 0.08 * level;
        int slowDuration = 3 + level; // Slow duration increases with level
        spellManager.applyIceDamageMultiplier(player, iceDamageMultiplier, slowDuration);
    }

    // Electromancy: Mastery of lightning magic, stunning enemies
    private void applyElectromancy(Player player, int level) {
        double lightningDamageMultiplier = 1.0 + 0.08 * level;
        int stunChance = 10 + 5 * level; // Stun chance increases with level
        spellManager.applyLightningDamageMultiplier(player, lightningDamageMultiplier, stunChance);
    }

    // Dark Arts: Use of shadow and dark energy for life-stealing spells
    private void applyDarkArts(Player player, int level) {
        double darkDamageMultiplier = 1.0 + 0.1 * level;
        double lifeStealPercent = 0.05 * level; // Steal 5% of damage as health per level
        spellManager.applyDarkDamageMultiplier(player, darkDamageMultiplier, lifeStealPercent);
    }

    // Mystic Binding: Enhances spells that bind or trap enemies
    private void applyMysticBinding(Player player, int level) {
        double bindingEffectiveness = 1.0 + 0.07 * level;
        int trapDuration = 5 + level; // Trap duration increases with level
        spellManager.applyBindingEffectiveness(player, bindingEffectiveness, trapDuration);
    }

    // Geomancy: Mastery of earth-based spells, increasing defense
    private void applyGeomancy(Player player, int level) {
        double earthDamageMultiplier = 1.0 + 0.08 * level;
        double defenseBoost = 0.05 * level; // 5% defense boost per level
        spellManager.applyEarthDamageMultiplier(player, earthDamageMultiplier, defenseBoost);
    }

    // Aeromancy: Mastery of wind-based spells, increasing speed and evasion
    private void applyAeromancy(Player player, int level) {
        double windDamageMultiplier = 1.0 + 0.08 * level;
        double evasionBoost = 0.05 * level; // 5% evasion boost per level
        spellManager.applyWindDamageMultiplier(player, windDamageMultiplier, evasionBoost);
    }
}

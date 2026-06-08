package Skills;

import Manager.PlayerManaManager;
import org.bukkit.entity.Player;

public class IntelligenceSkill extends MagicSkill {

    private final PlayerManaManager manaManager;

    public IntelligenceSkill(String name, PlayerManaManager manaManager) {
        super(name);
        this.manaManager = manaManager;
    }

    @Override
    public void applyEffect(Player player) {
        if (player == null) return;

        int level = manaManager.getSkillLevel(player, getName());

        // Increase mana pool based on skill level
        double currentMaxMana = manaManager.getMaxMana(player);  // Pass player object if needed
        double additionalMana = 500.0 * Math.pow(level, 1.2); // Example: Scaled mana increase per level
        manaManager.setMaxMana(player, currentMaxMana + additionalMana);

        // Increase mana regeneration rate based on skill level
        double currentManaRegen = manaManager.getManaRegenRate(player);  // Pass player object if needed
        double additionalManaRegen = 20.0 * Math.pow(level, 1.1); // Example: Scaled mana regen per level
        manaManager.setManaRegenRate(player, currentManaRegen + additionalManaRegen);

        // Apply magic damage based on skill level
        double magicDamageMultiplier = 1.0 + 0.02 * level; // Example: Scaled magic damage increase per level
        manaManager.applyMagicDamageMultiplier(player, magicDamageMultiplier, true);
    }
}

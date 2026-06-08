package Skills;

import Manager.PlayerManaManager;
import org.bukkit.entity.Player;

public class ManaRegenerationSkill extends MagicSkill {

    private final PlayerManaManager manaManager;
    private final double baseRegenRateIncrease = 0.5; // Base increase to regeneration rate per level (5% per level)

    public ManaRegenerationSkill(String name, PlayerManaManager manaManager) {
        super(name);
        this.manaManager = manaManager;
    }

    @Override
    public void applyEffect(Player player) {
        if (player == null) return;

        // Calculate the increased mana regeneration rate based on skill level
        int level = manaManager.getSkillLevel(player, getName());
        double regenRateMultiplier = 5 + (baseRegenRateIncrease * level); // Example: 5% increase per level

        // Apply the increased mana regeneration rate
        manaManager.setManaRegenRate(player, manaManager.getBaseManaRegenRate() * regenRateMultiplier);

        player.sendMessage("Mana regeneration rate increased by " + (regenRateMultiplier - 1) * 100 + "%.");
    }
}

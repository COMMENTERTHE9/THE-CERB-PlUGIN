package Skills;

import Traps.TrapManager;
import cerberus.world.cerb.CustomPlayer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class TrapMasterySkill extends UtilitySkill {

    private final TrapManager trapManager;

    public TrapMasterySkill(String name, TrapManager trapManager) {
        super(name);
        this.trapManager = trapManager;
    }

    @Override
    public void applyEffect(CustomPlayer customPlayer) {
        if (customPlayer == null) return;
        Player player = customPlayer.getPlayer();

        // Handle experience gain and level up logic
        int level = this.getLevel();

        // Enhance trap potency based on skill level
        double potencyBonus = level * 0.1; // Example: +10% trap potency per level
        trapManager.enhanceTrapPotency(player, potencyBonus);

        // Enhance trap trigger sensitivity based on skill level
        double triggerSensitivityBonus = level * 0.05; // Example: +5% trigger sensitivity per level
        trapManager.enhanceTrapTrigger(player, triggerSensitivityBonus);

        // Enhance trap duration based on skill level
        double durationBonus = level * 0.05; // Example: +5% trap duration per level
        trapManager.enhanceTrapDuration(player, durationBonus);
    }

    @Override
    public void applyEffect(Player player) {
        // Direct player effects can be applied here if necessary
    }
}

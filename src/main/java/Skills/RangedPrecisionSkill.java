package Skills;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;
import org.bukkit.util.Vector;

import java.util.Random;

public class RangedPrecisionSkill extends CombatSkill {
    private final Random random = new Random();

    public RangedPrecisionSkill(String name) {
        super(name);
    }

    @Override
    public void applyEffect(Player player) {
        if (player == null) return;

        // Check if the player is holding a bow
        ItemStack mainHandItem = player.getInventory().getItemInMainHand();
        if (mainHandItem != null && mainHandItem.getType() == Material.BOW) {
            int level = this.getLevel();

            // Simulate inaccuracy at lower levels
            if (random.nextDouble() > level / 100.0) { // Higher chance to miss at lower levels
                applyInaccuracy(player);
            }

            // Increase ranged attack damage
            AttributeInstance attackDamageAttr = player.getAttribute(Attribute.ATTACK_DAMAGE);
            if (attackDamageAttr != null) {
                double baseValue = attackDamageAttr.getBaseValue();
                attackDamageAttr.setBaseValue(baseValue * (1.0 + 0.01 * level)); // Increase ranged damage by 1% per skill level
            }

            // Increase critical hit chance
            if (attackDamageAttr != null && random.nextDouble() < level * 0.01) {
                double criticalMultiplier = 1.5;
                player.sendMessage("Critical Hit!");
                attackDamageAttr.setBaseValue(attackDamageAttr.getBaseValue() * criticalMultiplier);
            }

            // Increase draw speed (reduce time to full draw)
            int drawSpeedReductionTicks = Math.min(20, level / 2); // Reduce draw time by up to 10 ticks at level 20 (for example)
            // Implement logic to reduce the draw speed here (this may require handling bow interactions separately)

            // Increase arrow velocity
            Vector velocity = player.getLocation().getDirection();
            velocity.multiply(1.0 + level * 0.01); // Increase velocity by 1% per skill level
            player.getLocation().setDirection(velocity);
        }
    }

    private void applyInaccuracy(Player player) {
        Vector velocity = player.getLocation().getDirection();
        double inaccuracyFactor = 0.5; // Adjust inaccuracy factor based on how inaccurate you want it
        velocity.setX(velocity.getX() + (random.nextDouble() * 2 - 1) * inaccuracyFactor);
        velocity.setY(velocity.getY() + (random.nextDouble() * 2 - 1) * inaccuracyFactor);
        velocity.setZ(velocity.getZ() + (random.nextDouble() * 2 - 1) * inaccuracyFactor);
        player.getLocation().setDirection(velocity);
    }
}

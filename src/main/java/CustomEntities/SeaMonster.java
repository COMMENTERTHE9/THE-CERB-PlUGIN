package CustomEntities;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;

public class SeaMonster {

    private Mob entity; // Change from LivingEntity to Mob

    public SeaMonster(Location location) {
        spawnSeaMonster(location);
    }

    // Method to spawn the sea monster
    private void spawnSeaMonster(Location location) {
        World world = location.getWorld();
        if (world != null) {
            // You can customize this to spawn different types of sea monsters
            entity = (Mob) world.spawnEntity(location, EntityType.GUARDIAN); // Ensure it's a Mob or subclass
            entity.setCustomName("Sea Monster");
            entity.setCustomNameVisible(true);
            entity.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(100.0); // Example: Set max health
            entity.setHealth(100.0); // Set health
            entity.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE).setBaseValue(15.0); // Example: Set attack damage
        }
    }

    // Custom behavior methods
    public void attackPlayer(Player player) {
        if (entity != null && !entity.isDead()) {
            entity.setTarget(player); // This method is now available since 'entity' is of type Mob
            // Additional logic to handle attacking the player
        }
    }

    public void onDeath(Player player) {
        if (entity != null && entity.isDead()) {
            player.sendMessage("You have defeated the Sea Monster!");
            // Additional logic for dropping loot or rewards
        }
    }

    public Mob getEntity() {
        return entity;
    }
}

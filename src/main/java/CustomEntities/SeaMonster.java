package CustomEntities;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
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
            // Name + registry tag in the pre-spawn callback so MobScalingListener (which scales on
            // CreatureSpawnEvent) resolves it as sea_monster (RARE), not a COMMON guardian.
            // Vanilla MAX_HEALTH is cosmetic; HP comes from the realm-scaled virtual pool.
            entity = world.spawn(location, org.bukkit.entity.Guardian.class,
                    org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.CUSTOM, g -> {
                g.setCustomName("Sea Monster");
                g.setCustomNameVisible(true);
                g.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(15.0);
                cerberus.world.cerb.CerberusPlugin plugin = cerberus.world.cerb.CerberusPlugin.getInstance();
                if (plugin != null && plugin.getMobRegistry() != null) {
                    plugin.getMobRegistry().tagEntity(g, "sea_monster");
                }
            });
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

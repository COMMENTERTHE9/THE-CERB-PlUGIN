package CustomEntities;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SeaMonsterManager {
    private final Map<UUID, SeaMonster> seaMonsters;

    public SeaMonsterManager() {
        this.seaMonsters = new HashMap<>();
    }

    // Method to spawn a sea monster
    public SeaMonster spawnSeaMonster(Location location) {
        SeaMonster seaMonster = new SeaMonster(location);
        seaMonsters.put(seaMonster.getEntity().getUniqueId(), seaMonster);
        return seaMonster;
    }

    // Method to get a sea monster by UUID
    public SeaMonster getSeaMonster(UUID uuid) {
        return seaMonsters.get(uuid);
    }

    // Method to remove a sea monster from the map
    public void removeSeaMonster(UUID uuid) {
        seaMonsters.remove(uuid);
    }

    // Method to attack a player
    public void attackPlayer(UUID uuid, Player player) {
        SeaMonster seaMonster = seaMonsters.get(uuid);
        if (seaMonster != null) {
            seaMonster.attackPlayer(player);
        }
    }

    // Method to handle the death of a sea monster
    public void handleSeaMonsterDeath(UUID uuid, Player player) {
        SeaMonster seaMonster = seaMonsters.get(uuid);
        if (seaMonster != null) {
            seaMonster.onDeath(player);
            removeSeaMonster(uuid);
        }
    }

    // Method to clear all sea monsters (e.g., on server shutdown)
    public void clearSeaMonsters() {
        seaMonsters.clear();
    }
}

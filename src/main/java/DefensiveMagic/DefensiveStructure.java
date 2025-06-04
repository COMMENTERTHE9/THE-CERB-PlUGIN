package DefensiveMagic;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;
import java.util.UUID;

public abstract class DefensiveStructure {
    protected final Player player;
    protected final UUID structureId;
    protected boolean isActive;
    protected Location location;
    protected BukkitTask activeTask;
    protected final Plugin plugin;

    public DefensiveStructure(Player player) {
        this.player = player;
        this.structureId = UUID.randomUUID();
        this.isActive = false;
        this.location = player.getLocation();
        this.plugin = Bukkit.getPluginManager().getPlugin("CerberusPlugin");
    }

    public abstract void activate();

    public abstract void deactivate();

    public abstract double getManaCost();

    // Common methods for all defensive structures
    public boolean isActive() {
        return isActive;
    }

    public UUID getStructureId() {
        return structureId;
    }

    public Player getOwner() {
        return player;
    }

    public Location getLocation() {
        return location;
    }

    // Task management
    protected void cancelTask() {
        if (activeTask != null && !activeTask.isCancelled()) {
            activeTask.cancel();
            activeTask = null;
        }
    }

    // Clean up resources
    protected void cleanup() {
        cancelTask();
        isActive = false;
    }
}
package GUIs.player;

import GUIs.menu.Fmt;
import GUIs.menu.MenuView;
import GUIs.menu.Ui;
import Manager.ChunkDiscoveryManager;
import Manager.MobHealthScaling;
import Manager.Numbers;
import Manager.Tier;
import Skills.SkillNames;
import cerberus.world.cerb.CerberusPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * Player Menu → World & Exploration: how dangerous the land is where the player stands (the same
 * distance rule mob spawns use), and how much of the world they have discovered (in memory).
 */
public final class WorldMenu extends PlayerMenu {

    public WorldMenu(CerberusPlugin plugin) {
        super(plugin);
    }

    @Override public String title() { return PlayerTheme.title("World & Exploration"); }

    @Override
    protected void content(MenuView v) {
        Player p = v.viewer();
        Location loc = p.getLocation();
        World world = loc.getWorld();
        Location spawn = world.getSpawnLocation();
        double dx = loc.getX() - spawn.getX(), dz = loc.getZ() - spawn.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);   // same rule as MobScalingListener
        MobHealthScaling scaling = plugin.getCombatConfig().mobHealthScaling;
        int level = scaling.levelForDistance(distance);
        double perLevel = scaling.getBlocksPerLevel();

        v.set(MenuView.HEADER, Ui.item(Material.FILLED_MAP, ChatColor.GOLD + "" + ChatColor.BOLD + "World & Exploration", Ui.lore()
                .stat("World", world.getName())
                .stat("Distance from spawn", Fmt.grouped(Math.round(distance)) + (Math.round(distance) == 1 ? " block" : " blocks"))
                .stat("Creature level here", level + "" + ChatColor.DARK_GRAY + " / " + scaling.getMaxLevel()).build()));

        Ui.Lore danger = Ui.lore()
                .stat("Creatures here", "level " + level)
                .stat("Common creature health", Numbers.hud(scaling.calculate(Tier.COMMON, level)))
                .gap().text("Every " + Fmt.grouped(Math.round(perLevel)) + " blocks from world spawn")
                .text("adds a level, up to " + scaling.getMaxLevel() + ".");
        if (level < scaling.getMaxLevel()) {
            danger.stat("Next level", Fmt.grouped(Math.round(level * perLevel)) + " blocks from spawn");
        }
        danger.gap().text("Rarer creatures belong to higher")
                .text("realms: x8 health per realm.");
        v.set(20, Ui.item(Material.ZOMBIE_HEAD, ChatColor.RED + "Danger", danger.build()));

        ChunkDiscoveryManager chunks = plugin.getChunkDiscoveryManager();
        int found = chunks == null ? -1 : chunks.discoveredCount(p.getUniqueId());
        int pathLevel = plugin.getSkillManager().getSkillLevel(p.getUniqueId(), "Navigation");
        v.set(22, Ui.item(Material.COMPASS, ChatColor.GREEN + "Exploration", Ui.lore()
                .stat("Chunks discovered", found < 0 ? "loading..." : Fmt.grouped(found))
                .stat(SkillNames.display("Navigation"), "level " + pathLevel)
                .gap().text("Each chunk you enter for the first")
                .text("time gives " + Fmt.one(ChunkDiscoveryManager.XP_PER_CHUNK).replace(".0", "") + " "
                        + SkillNames.display("Navigation") + " XP.").build()));

        String biome = Fmt.title(loc.getBlock().getBiome().getKey().getKey());
        v.set(24, Ui.item(Material.GRASS_BLOCK, ChatColor.AQUA + "Where you are", Ui.lore()
                .stat("Biome", biome)
                .stat("Position", loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ())
                .stat("Spawn", spawn.getBlockX() + ", " + spawn.getBlockZ()).build()));

        v.set(31, Ui.item(Material.GRAY_DYE, ChatColor.GRAY + "Regions and landmarks", Ui.lore()
                .raw(Availability.State.IN_DEVELOPMENT.label)
                .text("Named regions, landmarks and a world")
                .text("map are not built yet. Today danger")
                .text("depends only on distance from spawn.").build()));
        v.refreshButton();
    }
}

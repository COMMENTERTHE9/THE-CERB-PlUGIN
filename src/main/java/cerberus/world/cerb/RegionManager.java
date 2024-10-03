package cerberus.world.cerb;

import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class RegionManager {
    private final JavaPlugin plugin;
    private final File regionsFile;
    private FileConfiguration regionsConfig;

    public RegionManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.regionsFile = new File(plugin.getDataFolder(), "regions.yml");
        loadRegions();
    }

    public void loadRegions() {
        if (!regionsFile.exists()) {
            plugin.saveResource("regions.yml", false);
        }
        regionsConfig = YamlConfiguration.loadConfiguration(regionsFile);

        CerberusWorldProtection.clearRegions(); // Clear existing regions before loading

        List<String> regionStrings = regionsConfig.getStringList("regions");
        for (String regionString : regionStrings) {
            String[] parts = regionString.split(",");
            if (parts.length == 6) {
                Location min = new Location(plugin.getServer().getWorlds().get(0),
                        Double.parseDouble(parts[0]),
                        Double.parseDouble(parts[1]),
                        Double.parseDouble(parts[2]));
                Location max = new Location(plugin.getServer().getWorlds().get(0),
                        Double.parseDouble(parts[3]),
                        Double.parseDouble(parts[4]),
                        Double.parseDouble(parts[5]));
                Region region = new Region(min, max);
                CerberusWorldProtection.addProtectedRegion(region);
            }
        }

        plugin.getLogger().info("Loaded " + CerberusWorldProtection.getProtectedRegions().size() + " protected regions.");
    }

    public void saveRegions() {
        List<Region> regions = CerberusWorldProtection.getProtectedRegions();
        List<String> regionStrings = regionsConfig.getStringList("regions");
        regionStrings.clear();

        for (Region region : regions) {
            Location min = region.getMin();
            Location max = region.getMax();
            String regionString = String.format("%f,%f,%f,%f,%f,%f",
                    min.getX(), min.getY(), min.getZ(),
                    max.getX(), max.getY(), max.getZ());
            regionStrings.add(regionString);
        }

        regionsConfig.set("regions", regionStrings);

        try {
            regionsConfig.save(regionsFile);
            plugin.getLogger().info("Saved " + regions.size() + " protected regions.");
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save regions to file");
            e.printStackTrace();
        }
    }
}
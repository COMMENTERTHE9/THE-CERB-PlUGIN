package cerberus.world.cerb;

import DefensiveMagic.*;
import Listener.CombatListener;
import Manager.CombatScoreboardManager;
import org.bukkit.event.EventPriority;
import org.bukkit.plugin.PluginManager;
import Listener.CraftingListener;
import Commands.*;
import CustomEntities.SeaMonster;
import Listener.*;
import Manager.*;
import GUIs.*;
import Skills.*;
import Listener.SpeedLimiterListener;
import Spells.SpellManager;
import CustomEntities.SeaMonsterManager;
import Commands.SpawnZombieCommand;
import Traps.TrapManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import Commands.KillHologramsCommand;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class cerb extends JavaPlugin implements Listener {
    private SkillManager skillManager;
    private RegionSelectorBehavior regionSelectorBehavior;
    private RegionManager regionManager;
    private ProtectionToggleCommand protectionToggleCommand;
    private RegionManagementCommands regionManagementCommands;
    private RegionManagementGUI regionManagementGUI;
    private AdminChestGUI adminChestGUI;
    private PlayerMenuGUI playerMenuGUI;
    private SkillGUI skillGUI;
    private final Random random = new Random();
    private final Set<UUID> adminChestReceivers = new HashSet<>();
    private DatabaseManager databaseManager;
    private static cerb instance;
    private SpellManager spellManager;
    private SeaMonsterManager seaMonsterManager;
    private PlayerVirtualHealthManager playerVirtualHealthManager;
    private PlayerDefenseManager playerDefenseManager;
    private PlayerManaManager playerManaManager;
    private ResourceYieldManager resourceYieldManager;
    private TrapManager trapManager;
    private LuckManager luckManager;
    private MagicFindManager magicFindManager;
    private CraftingManager craftingManager;
    private EffectManager effectManager;
    private PlayerHUDManager playerHUDManager;
    private CombatScoreboardManager combatScoreboardManager;
    private CraftingListener craftingListener;
    private PlayerStrengthManager playerStrengthManager;

    private File playerDataFile;
    private FileConfiguration playerDataConfig;
    private CerberusWorldProtection cerberusWorldProtection;
    private ItemUtils itemUtils;

    // Main plugin constructor should be empty
    public cerb() {
        // No initialization here
    }

    @Override
    public void onEnable() {
        getLogger().info("Cerberus Plugin is being enabled...");
        instance = this;

        createPlayerDataFile();
        getServer().getPluginManager().registerEvents(this, this);

        databaseManager = new DatabaseManager("C:/Users/Gabri/OneDrive/Desktop/Cerberus/cerberus.db");

        playerVirtualHealthManager = new PlayerVirtualHealthManager(this);

        // Initialize managers with null checks
        skillManager = initializeSkillManager(); // Ensure this is initialized properly before others
        skillGUI = new SkillGUI(this, skillManager);  // Ensure skillGUI is initialized

        playerDefenseManager = new PlayerDefenseManager(skillManager, playerVirtualHealthManager);
        playerManaManager = new PlayerManaManager(skillManager, this);
        resourceYieldManager = new ResourceYieldManager(skillManager, this);
        trapManager = new TrapManager(this);
        playerStrengthManager = new PlayerStrengthManager();
        luckManager = new LuckManager(skillManager, this, playerVirtualHealthManager, playerDefenseManager, playerStrengthManager);
        magicFindManager = new MagicFindManager(databaseManager);

        craftingManager = new CraftingManager(skillManager, this);
        craftingListener = new CraftingListener();

        effectManager = new EffectManager(playerVirtualHealthManager, playerDefenseManager, playerManaManager, skillManager);
        spellManager = new SpellManager(effectManager, playerManaManager);

        seaMonsterManager = new SeaMonsterManager();

        // Initialize ItemUtils
        this.itemUtils = new ItemUtils(this);

        // Initialize CerberusWorldProtection
        this.cerberusWorldProtection = new CerberusWorldProtection();

        // Set the necessary managers in the SkillManager (with null check)
        if (skillManager != null) {
            skillManager.setPlayerDefenseManager(playerDefenseManager);
            skillManager.setPlayerManaManager(playerManaManager);
            skillManager.setResourceYieldManager(resourceYieldManager);
            skillManager.setTrapManager(trapManager);
            skillManager.setLuckManager(luckManager);
            skillManager.setMagicFindManager(magicFindManager);
        }

        // Initialize GUIs and register commands
        playerMenuGUI = new PlayerMenuGUI(this, skillManager);
        playerHUDManager = new PlayerHUDManager(playerManaManager, playerDefenseManager, playerVirtualHealthManager);
        combatScoreboardManager = new CombatScoreboardManager();

        this.regionManager = new RegionManager(this);
        this.regionSelectorBehavior = new RegionSelectorBehavior(this); // Updated RegionSelectorBehavior
        this.protectionToggleCommand = new ProtectionToggleCommand();
        this.regionManagementCommands = new RegionManagementCommands(this);
        this.regionManagementGUI = new RegionManagementGUI(this);
        this.adminChestGUI = new AdminChestGUI(this);

        registerCommands();

        registerEventListeners();

        startHUDUpdateTask();

        databaseManager.fixNullCustomIds();

        regionManager.loadRegions();
        getLogger().info("Cerberus Plugin has been enabled!");
    }

    private SkillManager initializeSkillManager() {
        return new SkillManager(
                this,
                new HashMap<>(),
                new HashMap<>(),
                getServer().createInventory(null, 27, "Skill Menu"),
                databaseManager,
                UUID.randomUUID(),
                playerVirtualHealthManager,
                playerDefenseManager,
                craftingManager,
                craftingListener,
                playerStrengthManager,
                seaMonsterManager
        );
    }

    private void registerCommands() {
        Objects.requireNonNull(getCommand("spawncustomzombie")).setExecutor(new SpawnZombieCommand(this));
        Objects.requireNonNull(getCommand("testregen")).setExecutor(new TestRegenCommand(playerVirtualHealthManager));
        Objects.requireNonNull(getCommand("giveTestItem")).setExecutor(new GiveTestItemCommand());
        this.getCommand("regionselector").setExecutor(new CommandHandler(this, regionSelectorBehavior));
        this.getCommand("unregionizer").setExecutor(new CommandHandler(this, regionSelectorBehavior));
        Objects.requireNonNull(getCommand("visualizeregions")).setExecutor(new RegionVisualizerCommand(this));
        Objects.requireNonNull(getCommand("toggleprotection")).setExecutor(protectionToggleCommand);
        Objects.requireNonNull(getCommand("region")).setExecutor(regionManagementCommands);
        Objects.requireNonNull(getCommand("givetestarmor")).setExecutor(new GiveTestArmorCommand());
        Objects.requireNonNull(getCommand("killholograms")).setExecutor(new KillHologramsCommand(this));
    }

    private void registerEventListeners() {
        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new PlayerHUDListener(playerHUDManager, playerDefenseManager), this);
        pm.registerEvents(new CustomItemListener(spellManager), this);
        pm.registerEvents(new MobKillListener(skillManager, skillGUI), this);
        pm.registerEvents(new VirtualHealthListener(playerVirtualHealthManager, new FirstAidSkill("First Aid", playerVirtualHealthManager, craftingManager)), this);
        pm.registerEvents(new RegionManagementGUIListener(this), this);
        pm.registerEvents(new AdminGUIListener(this, adminChestGUI), this);
        pm.registerEvents(new MenuProtectionListener(this), this);
        pm.registerEvents(new MenuListener(this, playerMenuGUI, skillGUI, skillManager), this);
        pm.registerEvents(new ManaListener(playerManaManager), this);
        pm.registerEvents(new DefenseListener(playerHUDManager), this);
        pm.registerEvents(new CustomArmorListener(playerDefenseManager, playerHUDManager), this);
        pm.registerEvents(new DamageDisplayListener(this), this);
        pm.registerEvents(new CombatListener(combatScoreboardManager, this), this);
        pm.registerEvents(craftingListener, this);
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(skillManager, databaseManager, this, skillGUI, playerMenuGUI), this);
        getServer().getPluginManager().registerEvents(new SpeedLimiterListener(), this);

    }

    private void createPlayerDataFile() {
        playerDataFile = new File(getDataFolder(), "playerData.yml");
        if (!playerDataFile.exists()) {
            try {
                playerDataFile.createNewFile();
                getLogger().info("Created playerData.yml file");
            } catch (IOException e) {
                getLogger().severe("Could not create playerData.yml file");
                e.printStackTrace();
            }
        }
        playerDataConfig = YamlConfiguration.loadConfiguration(playerDataFile);
    }

    public FileConfiguration getPlayerDataConfig() {
        return playerDataConfig;
    }

    public void savePlayerDataConfig() {
        try {
            playerDataConfig.save(playerDataFile);
        } catch (IOException e) {
            getLogger().severe("Could not save playerData.yml file");
            e.printStackTrace();
        }
    }

    private void startHUDUpdateTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.isOnline()) {
                        playerHUDManager.updateHUD(player);
                    }
                }
            }
        }.runTaskTimer(this, 0, 20);
    }

    @Override
    public void onDisable() {
        if (regionManager != null) {
            regionManager.saveRegions();
        }
        if (seaMonsterManager != null) {
            seaMonsterManager.clearSeaMonsters();
        }
        getLogger().info("Cerberus Plugin has been disabled!");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        if (playerDefenseManager != null) {
            playerDefenseManager.updateDefense(player);
        }

        if (playerHUDManager != null) {
            playerHUDManager.updateHUD(player);
        }

        CerberusGameMode.enable(player, playerDataConfig);

        UUID playerUUID = player.getUniqueId();
        Map<String, Integer> skillLevels = databaseManager.loadPlayerSkillLevels(playerUUID);
        Map<String, Integer> skillXP = databaseManager.loadPlayerSkillXP(playerUUID);
        Inventory skillMenu = getServer().createInventory(null, 27, "Skill Menu");

        String playerPath = "players." + playerUUID.toString();
        if (!playerDataConfig.contains(playerPath)) {
            playerDataConfig.set(playerPath + ".joinedBefore", true);
            savePlayerDataConfig();

            player.setHealth(player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue());
            player.sendMessage(ChatColor.GREEN + "Welcome to Cerberus Mode for the first time! Your health and hunger have been maximized.");
        }

        if (player.hasPermission("cerberus.admin")) {
            handleAdminChest(player);
        }

        if (playerVirtualHealthManager != null) {
            playerVirtualHealthManager.startHealthUpdateTask(player, playerHUDManager);
        }
    }

    private void handleAdminChest(Player player) {
        boolean hasAdminChest = false;
        int adminChestCount = 0;

        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && adminChestGUI.isAdminChest(item)) {
                hasAdminChest = true;
                adminChestCount++;
            }
        }

        if (!hasAdminChest) {
            player.getInventory().addItem(adminChestGUI.createAdminChest());
            player.sendMessage(ChatColor.GREEN + "You have received the Admin Control Panel chest!");
        } else if (adminChestCount > 1) {
            int removedCount = 0;
            for (int i = 0; i < player.getInventory().getSize(); i++) {
                ItemStack item = player.getInventory().getItem(i);
                if (item != null && adminChestGUI.isAdminChest(item)) {
                    if (removedCount < adminChestCount - 1) {
                        player.getInventory().setItem(i, null);
                        removedCount++;
                    } else {
                        break;
                    }
                }
            }
            player.sendMessage(ChatColor.YELLOW + "Excess Admin Control Panel chests have been removed.");
        }
    }

    @EventHandler
    public void onPlayerBedEnter(PlayerBedEnterEvent event) {
        event.setCancelled(true);
        String[] sleepMessages = {
                "You don't feel tired right now.",
                "A strange energy pulses through the air, keeping you awake.",
                "Feels like a magical force is forcing you awake.",
                "Your mind is clear, as if sleep is the furthest thing you need."
        };
        event.getPlayer().sendMessage(ChatColor.ITALIC + sleepMessages[random.nextInt(sleepMessages.length)]);
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        EntityDamageEvent lastDamageCause = player.getLastDamageCause();
        @NotNull Entity damagerEntity = null;

        if (lastDamageCause instanceof EntityDamageByEntityEvent) {
            damagerEntity = ((EntityDamageByEntityEvent) lastDamageCause).getDamager();
        }

        // Example usage to apply virtual damage before death handling
        playerVirtualHealthManager.applyDamage(player, player.getHealth(), CustomDamageType.ENTITY_ATTACK, damagerEntity);

        // Handle the respawn
        Bukkit.getScheduler().runTask(this, () -> player.spigot().respawn());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        // Access ItemUtils instance
        ItemUtils itemUtils = this.getItemUtils();

        if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            if (adminChestGUI.isAdminChest(item)) {
                event.setCancelled(true);
                adminChestGUI.openAdminGUI(player);
                return;
            }
        }

        if (playerMenuGUI.isPlayerMenuHead(item)) {
            event.setCancelled(true);
            playerMenuGUI.openMainMenu(player);
            return;
        }

        if (itemUtils.isRegionSelector(item) || itemUtils.isUnregionizer(item)) {
            event.setCancelled(true);
            Location loc = event.getClickedBlock() != null ? event.getClickedBlock().getLocation() : null;
            if (loc != null) {
                if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
                    regionSelectorBehavior.handleLeftClick(player, loc);
                } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                    regionSelectorBehavior.handleRightClick(player, loc, item);
                }
            }
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        // Access ItemUtils instance
        ItemUtils itemUtils = this.getItemUtils();

        if (itemUtils.isRegionSelector(item) || itemUtils.isUnregionizer(item)) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "You cannot break blocks while using the Region Selector or Unregionizer.");
            return;
        }

        if (cerberusWorldProtection.isInProtectedRegion(event.getBlock().getLocation())) {
            if (protectionToggleCommand.isProtectionEnabled(player.getUniqueId())) {
                event.setCancelled(true);
                String[] blockMessages = {
                        "There is a force stopping you from breaking this block.",
                        "The block is harder than anything you've felt before.",
                        "A mystical barrier surrounds this area, making it impossible to break.",
                        "Your tools feel useless against this enchanted surface."
                };
                player.sendMessage(ChatColor.RED + blockMessages[random.nextInt(blockMessages.length)]);
            } else {
                player.sendMessage(ChatColor.YELLOW + "You are bypassing region protection (toggle is off).");
            }
        }
    }

    @EventHandler
    public void onPlayerFish(PlayerFishEvent event) {
        Player player = event.getPlayer();
        if (Math.random() < 0.05) {
            SeaMonster seaMonster = seaMonsterManager.spawnSeaMonster(player.getLocation());
            seaMonster.attackPlayer(player);
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        CustomPlayer customPlayer = skillManager.getCustomPlayer(player.getUniqueId());

        if (customPlayer != null) {
            String skillName = "Navigation";
            UtilitySkill skill = (UtilitySkill) skillManager.getSkill(skillName);

            if (skill != null) {
                skill.applyEffect(customPlayer);
            }
        }
    }

    public RegionManager getRegionManager() {
        return regionManager;
    }

    public RegionManagementGUI getRegionManagementGUI() {
        return regionManagementGUI;
    }

    public AdminChestGUI getAdminChestGUI() {
        return adminChestGUI;
    }

    public PlayerMenuGUI getPlayerMenuGUI() {
        return playerMenuGUI;
    }

    public ProtectionToggleCommand getProtectionToggleCommand() {
        return protectionToggleCommand;
    }

    public static cerb getInstance() {
        return instance;
    }

    public SpellManager getSpellManager() {
        return spellManager;
    }

    public SeaMonsterManager getSeaMonsterManager() {
        return seaMonsterManager;
    }

    public SkillManager getSkillManager() {
        return skillManager;
    }

    public PlayerManaManager getPlayerManaManager() {
        return playerManaManager;
    }

    public PlayerDefenseManager getPlayerDefenseManager() {
        return playerDefenseManager;
    }

    public ResourceYieldManager getResourceYieldManager() {
        return resourceYieldManager;
    }

    public TrapManager getTrapManager() {
        return trapManager;
    }

    public LuckManager getLuckManager() {
        return luckManager;
    }

    public MagicFindManager getMagicFindManager() {
        return magicFindManager;
    }

    public CraftingManager getCraftingManager() {
        return craftingManager;
    }

    public EffectManager getEffectManager() {
        return effectManager;
    }

    public PlayerHUDManager getPlayerHUDManager() {
        return playerHUDManager;
    }

    public PlayerVirtualHealthManager getPlayerVirtualHealthManager() {
        return playerVirtualHealthManager;
    }

    public CerberusWorldProtection getCerberusWorldProtection() {
        return cerberusWorldProtection;
    }

    public ItemUtils getItemUtils() {
        return itemUtils;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }
}

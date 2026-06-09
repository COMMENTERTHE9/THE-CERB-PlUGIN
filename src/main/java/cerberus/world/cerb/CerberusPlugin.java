package cerberus.world.cerb;

import Listener.CombatListener;
import Commands.CerbCommand;
import org.bukkit.event.EventPriority;
import org.bukkit.plugin.PluginManager;
import Listener.CraftingListener;
import Commands.*;
import java.util.Objects;
import CustomEntities.SeaMonster;
import Listener.*;
import Manager.*;
import GUIs.*;
import Skills.*;
// SpeedLimiterListener REMOVED — moved to PlayerSessionManager
import Spells.SpellManager;
import CustomEntities.SeaMonsterManager;
import Commands.SpawnZombieCommand;
import org.bukkit.command.CommandSender;
import Listener.CustomItemListener;


import Traps.TrapManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import Listener.DefenseUpdateListener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import Manager.DefenseBarManager;

public class CerberusPlugin extends JavaPlugin implements Listener {

    // Managers and GUIs
    private SkillManager skillManager;
    private AdminChestGUI adminChestGUI;
    private PlayerMenuGUI playerMenuGUI;
    private SkillGUI skillGUI;
    private DatabaseManager databaseManager;
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
    private CraftingListener craftingListener;
    private ConfigSkillMapper skillMapper;  // store it as a field
    private PlayerStrengthManager playerStrengthManager;

    // ------------------------------------------------
    // Async save throttle
    // ------------------------------------------------
    private AsyncSaveManager asyncSaver;


    private long lastQueuedSave = 0L;            // <<< NEW
    private static final long SAVE_COOLDOWN_MS = 1000;   // 1 second  <<< NEW

    private File playerDataFile;
    private FileConfiguration playerDataConfig;
    private static CerberusPlugin instance;

    private final Random random = new Random();
    private final Set<UUID> adminChestReceivers = new HashSet<>();
    private DefenseBarManager defenseBarManager;
    private Manager.ChunkDiscoveryManager chunkDiscoveryManager;
    private MobRegistry mobRegistry;
    private PlayerSessionManager playerSessionManager;
    private DamageDisplayListener damageDisplayListener;
    private CombatConfig combatConfig;
    private CombatSkillProcessor combatSkillProcessor;
    private ElementalInfusionManager elementalInfusionManager;
    private VirtualHealthListener virtualHealthListener;
    private SessionLogger sessionLogger;
    private SkillDetailGUI skillDetailGUI;

    /** Players with combat debug mode enabled — see /cerb combatdebug */
    private final Set<UUID> combatDebugPlayers = ConcurrentHashMap.newKeySet();

    private record PlayerSkillSnapshot(
            UUID playerUUID,
            String playerName,
            Map<String, Integer> levels,
            Map<String, Integer> xp
    ) {}


    @Override
    public void onEnable() {
        getLogger().info("Cerberus Plugin is enabling...");
        instance = this;

        this.asyncSaver = new AsyncSaveManager(this, SAVE_COOLDOWN_MS);

        // quick, safe work on the main thread
        initFiles();

        // heavy I/O moves to an async thread
        Bukkit.getAsyncScheduler().runNow(this, task -> {

            initDatabase();
            initCoreManagers();
            initSkillManager();

            runSync(() -> {
                initGUIs();
                initCommands();

                // ================================================
                // ←←←  REGISTER OUR NEW UMBRELLA ADMIN COMMAND  ←
                // ================================================
                java.util.Objects.requireNonNull(getCommand("cerb"))
                        .setExecutor(new CerbCommand(this));

                // ================================================
                // ←←←  HERE: set up your YAML mapper & listener  ←
                // ================================================
                this.skillMapper = new ConfigSkillMapper(this);                // build & store mapper
                // MobKillListener registered in initListeners() with mobRegistry

                initListeners();        // registers all listeners including PlayerSessionManager
                enableImmediateRespawnForLoadedWorlds();
                databaseManager.fixNullCustomIds();

                getLogger().info("Cerberus Plugin has been enabled!");
            });
        });
    }

    public void saveAllPlayers() {
        List<PlayerSkillSnapshot> snapshots = new ArrayList<>();
        for (Player player : getServer().getOnlinePlayers()) {
            snapshots.add(new PlayerSkillSnapshot(
                    player.getUniqueId(),
                    player.getName(),
                    new HashMap<>(skillManager.getPlayerSkillLevels(player)),
                    new HashMap<>(skillManager.getPlayerSkillXP(player))
            ));
        }

        Bukkit.getAsyncScheduler().runNow(this, task -> {
            for (PlayerSkillSnapshot snapshot : snapshots) {
                String customId = databaseManager.getOrCreateCustomId(snapshot.playerUUID(), snapshot.playerName());
                databaseManager.saveSkillsByCustomId(customId, snapshot.levels(), snapshot.xp());
            }
            getLogger().info("All players' skills saved to the database.");
        });
    }

    @Override
    public void onDisable() {
        // Centralized player cleanup + async skill save
        if (playerSessionManager != null) playerSessionManager.shutdown();

        if (damageDisplayListener != null) damageDisplayListener.shutdown();
        if (seaMonsterManager != null) seaMonsterManager.clearSeaMonsters();
        if (trapManager != null)       trapManager.cleanup();

        getLogger().info("Cerberus Plugin has been disabled!");
    }

    // ---------------------------
    // Initialization Methods
    // ---------------------------

    private void initFiles() {
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }
        saveResource("mobs.yml", false);  // create default mobs.yml on first install
        playerDataFile = new File(getDataFolder(), "playerData.yml");
        try {
            if (!playerDataFile.exists()) {
                playerDataFile.createNewFile();
                getLogger().info("Created playerData.yml file");
            }
        } catch (IOException e) {
            getLogger().severe("Could not create playerData.yml file");
            e.printStackTrace();
        }
        playerDataConfig = YamlConfiguration.loadConfiguration(playerDataFile);
    }

    private void initDatabase() {
        databaseManager = new DatabaseManager(this);
    }

    private void initCoreManagers() {



        // Core managers that need the plugin reference
        playerVirtualHealthManager = new PlayerVirtualHealthManager(this);
        craftingListener = new CraftingListener();
        playerStrengthManager = new PlayerStrengthManager();
        playerHUDManager = new PlayerHUDManager(null, null, playerVirtualHealthManager);
        adminChestGUI = new AdminChestGUI(this);
        mobRegistry = new MobRegistry(this);
        mobVirtualHealthManager = new Manager.MobVirtualHealthManager();
    }

    public AsyncSaveManager getAsyncSaver() {
        return asyncSaver;
    }

    public MobRegistry getMobRegistry() {
        return mobRegistry;
    }

    private Manager.MobVirtualHealthManager mobVirtualHealthManager;

    public Manager.MobVirtualHealthManager getMobVirtualHealthManager() {
        return mobVirtualHealthManager;
    }

    public Manager.ChunkDiscoveryManager getChunkDiscoveryManager() {
        return chunkDiscoveryManager;
    }

    private void initSkillManager() {
        skillManager = new SkillManager(
                this,
                new HashMap<>(),
                new HashMap<>(),
                getServer().createInventory(null, 27, "Skill Menu"),
                databaseManager,
                UUID.randomUUID(),
                playerVirtualHealthManager,
                null, // will set later
                craftingManager,
                craftingListener,
                playerStrengthManager,
                null // seaMonsterManager set later
        );

        playerDefenseManager = new PlayerDefenseManager(skillManager, playerVirtualHealthManager);
        defenseBarManager = new DefenseBarManager(this);
        playerVirtualHealthManager.setDefenseBarManager(defenseBarManager);
        playerManaManager = new PlayerManaManager(skillManager, this);
        resourceYieldManager = new ResourceYieldManager(skillManager, this);
        luckManager = new LuckManager(skillManager, this, playerVirtualHealthManager, playerDefenseManager, playerStrengthManager);
        magicFindManager = new MagicFindManager(databaseManager);
        craftingManager = new CraftingManager(skillManager, this);
        trapManager = new TrapManager(this, craftingManager);
        effectManager = new EffectManager(playerVirtualHealthManager, playerDefenseManager, playerManaManager, skillManager);
        spellManager = new SpellManager(effectManager, playerManaManager, this);
        seaMonsterManager = new SeaMonsterManager();

        // Now that these managers exist, set them in skillManager
        skillManager.setPlayerDefenseManager(playerDefenseManager);
        skillManager.setPlayerManaManager(playerManaManager);
        skillManager.setResourceYieldManager(resourceYieldManager);
        skillManager.setCraftingManager(craftingManager);
        skillManager.setTrapManager(trapManager);
        skillManager.setLuckManager(luckManager);
        skillManager.setMagicFindManager(magicFindManager);
        skillManager.setSeaMonsterManager(seaMonsterManager);
        skillManager.rebuildSkillRegistry();

        // Combat system: config, elemental, skill processor
        combatConfig = new CombatConfig(this);
        sessionLogger = new SessionLogger(this);

        // GUI identity keys and skill definitions (data-driven GUI metadata)
        GuiIdentity.init(this);
        SkillDefinitions.reload(this);

        defenseBarManager.applyCombatConfig(combatConfig);
        playerVirtualHealthManager.setCombatConfig(combatConfig);
        playerVirtualHealthManager.setSessionLogger(sessionLogger);
        playerDefenseManager.setCombatConfig(combatConfig);
        playerDefenseManager.setDefenseBarManager(defenseBarManager);
        elementalInfusionManager = new ElementalInfusionManager(this);
        combatSkillProcessor = new CombatSkillProcessor(
                skillManager, combatConfig, elementalInfusionManager, playerVirtualHealthManager);

        // Update HUD manager now that managers are in place
        playerHUDManager = new PlayerHUDManager(playerManaManager, playerDefenseManager, playerVirtualHealthManager);
        playerHUDManager.setDefenseBarManager(defenseBarManager);
    }


    private void initGUIs() {
        skillGUI = new SkillGUI(this, skillManager);
        playerMenuGUI = new PlayerMenuGUI(this, skillManager);
        skillDetailGUI = new SkillDetailGUI(this, skillManager, combatConfig);
    }

    private void initCommands() {
        Objects.requireNonNull(getCommand("spawncustomzombie")).setExecutor(new SpawnZombieCommand(this));
        Objects.requireNonNull(getCommand("testregen")).setExecutor(new TestRegenCommand(playerVirtualHealthManager));
        Objects.requireNonNull(getCommand("giveTestItem")).setExecutor(new GiveTestItemCommand(this));
        Objects.requireNonNull(getCommand("givetestarmor")).setExecutor(new GiveTestArmorCommand());
        var cerbCmd = Objects.requireNonNull(getCommand("cerb"));
        cerbCmd.setExecutor(new CerbCommand(this));
        cerbCmd.setTabCompleter(new Commands.CerbTabCompleter(this));
    }

    private void initListeners() {
        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(this, this);

        // ── Chunk discovery (Navigation skill) ──
        chunkDiscoveryManager = new Manager.ChunkDiscoveryManager(this, databaseManager, skillManager);

        // ── Centralized lifecycle manager ──
        Manager.PlayerServices services = new Manager.PlayerServices(
                databaseManager, skillManager, playerVirtualHealthManager,
                playerDefenseManager, playerManaManager, playerHUDManager,
                playerStrengthManager, effectManager, defenseBarManager,
                skillGUI, playerMenuGUI, adminChestGUI, mobRegistry,
                chunkDiscoveryManager
        );
        playerSessionManager = new PlayerSessionManager(this, services);
        pm.registerEvents(playerSessionManager, this);

        // ── Combat & mobs ──
        pm.registerEvents(new CombatListener(this, skillManager, spellManager, playerMenuGUI, skillGUI, mobRegistry), this);
        pm.registerEvents(new MobKillListener(this, skillManager, skillGUI, skillMapper, mobRegistry), this);
        pm.registerEvents(new MobScalingListener(this), this);

        // ── HUD & defense (reactive — damage/heal/consume only) ──
        pm.registerEvents(new PlayerHUDListener(playerHUDManager, playerDefenseManager), this);
        pm.registerEvents(new DefenseUpdateListener(playerHUDManager), this);
        pm.registerEvents(new CustomArmorListener(playerDefenseManager, playerHUDManager, this), this);

        // ── Virtual health (damage interception, regen block) ──
        this.virtualHealthListener = new VirtualHealthListener(playerVirtualHealthManager,
                new FirstAidSkill("First Aid", playerVirtualHealthManager, craftingManager), this);
        virtualHealthListener.setCombatSkillProcessor(combatSkillProcessor);
        spellManager.setVirtualHealthListener(virtualHealthListener);
        spellManager.setSkillManager(skillManager);
        pm.registerEvents(virtualHealthListener, this);

        // ── GUIs & interactions (unified listener replaces old per-GUI listeners) ──
        pm.registerEvents(new UnifiedGUIListener(this, skillManager, skillGUI, skillDetailGUI, playerMenuGUI, adminChestGUI), this);
        pm.registerEvents(new PlayerJoinListener(skillManager, this, skillGUI, playerMenuGUI), this);

        // ── Other ──
        pm.registerEvents(new CustomItemListener(spellManager, this), this);
        this.damageDisplayListener = new DamageDisplayListener(this);
        damageDisplayListener.setCombatConfig(combatConfig);
        pm.registerEvents(damageDisplayListener, this);
        pm.registerEvents(craftingListener, this);

        // REMOVED: ManaListener (lifecycle moved to PlayerSessionManager)
        // REMOVED: SpeedLimiterListener (speed limit moved to PlayerSessionManager)
    }

    // ─────────────────────────────────────────────────────────────
    // <<< ADD reloadAll() HERE >>>
    /**
     * Public entry‑point for `/cerb reload`.
     */
    public void reloadAll() {
        initFiles();

        if (mobRegistry != null) {
            mobRegistry.reload();
        }
        SkillDefinitions.reload(this);
        if (skillMapper != null) {
            skillMapper.reload();
        }
        if (combatConfig != null) {
            combatConfig.reload();
        }
        if (defenseBarManager != null && combatConfig != null) {
            defenseBarManager.applyCombatConfig(combatConfig);
        }
        if (playerVirtualHealthManager != null && combatConfig != null) {
            playerVirtualHealthManager.setCombatConfig(combatConfig);
        }
        if (playerDefenseManager != null && combatConfig != null) {
            playerDefenseManager.setCombatConfig(combatConfig);
        }
        if (damageDisplayListener != null && combatConfig != null) {
            damageDisplayListener.setCombatConfig(combatConfig);
        }

        runSync(this::enableImmediateRespawnForLoadedWorlds);
        getLogger().info("Cerberus configuration reloaded.");
    }

    /**
     * Sets DO_IMMEDIATE_RESPAWN to true on all loaded worlds.
     *
     * This is INTENTIONAL and PERMANENT. The Cerberus plugin uses immediate
     * respawn as a core gameplay decision — players never see the death screen.
     * Death restoration is handled directly by PlayerSessionManager via a delayed
     * task scheduled from the death handler, bypassing PlayerRespawnEvent entirely
     * (which doesn't fire reliably on Folia).
     *
     * DO NOT make this configurable.
     * DO NOT remove this method without coordinating with the death/respawn pipeline.
     *
     * See PlayerSessionManager.onPlayerDeath for the corresponding restoration logic.
     */
    private void enableImmediateRespawnForLoadedWorlds() {
        for (World world : Bukkit.getWorlds()) {
            enableImmediateRespawn(world);
        }
    }

    private void enableImmediateRespawn(World world) {
        Boolean current = world.getGameRuleValue(GameRule.DO_IMMEDIATE_RESPAWN);
        if (Boolean.TRUE.equals(current)) {
            return;
        }

        world.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true);
        getLogger().info("Enabled DO_IMMEDIATE_RESPAWN for world '" + world.getName() + "'.");
    }

    // <<< END ADDITION >>>
    // ─────────────────────────────────────────────────────────────

    // =========================================
    // Utility: safely run Bukkit code from async
    // =========================================
    public void runSync(Runnable task) {
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getGlobalRegionScheduler().run(this, scheduledTask -> task.run());
        }
    }

    // ─────────────────────────────────────────────────────────────
// /cerb master command support stubs
// ─────────────────────────────────────────────────────────────

    /** /cerb backup */
    public void backupAll() {
        // TODO: async‐dump DB + copy critical YMLs
    }

    /** /cerb debug <on|off> */
    public void toggleDebug(boolean on) {
        // TODO: flip your plugin’s debug flag
    }

    /** /cerb status */
    public void printStatus(CommandSender who) {
        // TODO: gather TPS, memory use, loaded regions, etc.
        who.sendMessage(ChatColor.AQUA + "=== Server Status ===");
        who.sendMessage("Online players: " + getServer().getOnlinePlayers().size());
    }

    /** /cerb heal <player> */
    public void healPlayer(CommandSender sender, String playerName) {
        Player target = getServer().getPlayerExact(playerName);
        if (target == null || !target.isOnline()) {
            sender.sendMessage(ChatColor.RED + "Player not found.");
            return;
        }
        // TODO: restore their real + virtual health
        target.setHealth(target.getAttribute(Attribute.MAX_HEALTH).getValue());
        playerVirtualHealthManager.resetHealth(target);
        sender.sendMessage(ChatColor.GREEN + "Healed " + playerName + ".");
    }

    /** /cerb mana <player> [amount] */
    public void adjustMana(CommandSender sender, String[] args) {
        // TODO: parse args[1] as player, args[2] (if present) as amount, then call mana manager
        sender.sendMessage(ChatColor.YELLOW + "Mana adjustment is not implemented yet.");
    }

    /** /cerb skill <player> <skill> <level|xp> <value> */
    public void adjustSkill(CommandSender sender, String[] args) {
        // TODO: parse args into target, skill name, "level" vs "xp", and value
        sender.sendMessage(ChatColor.YELLOW + "Skill adjustment is not implemented yet.");
    }

    /** /cerb resetdata <player> */
    public void resetPlayerData(CommandSender sender, String[] args) {
        // TODO: wipe their custom ID, skill levels & XP, virtual health, etc.
        sender.sendMessage(ChatColor.YELLOW + "Reset data is not implemented yet.");
    }


    /** /cerb worldgen <on|off> */
    public void toggleWorldGen(CommandSender sender, String[] args) {
        // TODO: enable/disable your custom worldgen hooks
        sender.sendMessage(ChatColor.YELLOW + "Worldgen toggle is not implemented yet.");
    }

    /** /cerb repairchunk <x> <z> */
    public void repairChunk(CommandSender sender, String[] args) {
        // TODO: call your "nature auto‑repair" routine for the chunk at (x,z)
        sender.sendMessage(ChatColor.YELLOW + "Repair‑chunk is not implemented yet.");
    }

    /** /cerb quest give <region> <type> <count> */
    public void giveQuest(CommandSender sender, String[] args) {
        // TODO: assign a collect/kill/defend quest to a region
        sender.sendMessage(ChatColor.YELLOW + "Quest assignment not implemented yet.");
    }

    /** /cerb event <start|stop> <name> */
    public void controlEvent(CommandSender sender, String[] args) {
        // TODO: start or stop a dynamic event by name
        sender.sendMessage(ChatColor.YELLOW + "Event control not implemented yet.");
    }


    /** /cerb timings <start|stop> */
    public void handleTimings(CommandSender sender, String[] args) {
        // TODO: Bukkit.getServer().getTimings().start()/stop() for your plugin
        sender.sendMessage(ChatColor.YELLOW + "Timings control not implemented yet.");
    }

    /** /cerb profiler spark */
    public void triggerProfiler(CommandSender sender, String[] args) {
        // TODO: if Spark is installed, trigger a snapshot
        sender.sendMessage(ChatColor.YELLOW + "Profiler trigger not implemented yet.");
    }

    /** /cerb listentities <radius> */
    public void listEntitiesAround(CommandSender sender, String[] args) {
        // TODO: parse radius, count custom entities near the sender
        sender.sendMessage(ChatColor.YELLOW + "Entity listing not implemented yet.");
    }

    /** /cerb showqueues */
    public void showQueues(CommandSender sender) {
        // TODO: iterate your scheduled tasks & HUD‐update timers and print them
        sender.sendMessage(ChatColor.YELLOW + "Queue listing not implemented yet.");
    }

    // ---------------------------
    // Event Handlers
    // ---------------------------

    // ---------------------------
// Main Cerberus event handlers
// ---------------------------
    // onPlayerJoin REMOVED — lifecycle moved to PlayerSessionManager

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerBedEnter(PlayerBedEnterEvent e) {
        e.setCancelled(true);
        String[] msgs = {
                "You don't feel tired right now.",
                "A strange energy keeps you awake.",
                "A magical force prevents you from sleeping.",
                "Your mind is too alert to rest."
        };
        e.getPlayer().sendMessage(ChatColor.ITALIC + msgs[random.nextInt(msgs.length)]);
    }


    // onPlayerDeath REMOVED — lifecycle moved to PlayerSessionManager

    // ── Prevent dropping/moving menu items ──────────────────────────

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onPlayerDropItem(PlayerDropItemEvent e) {
        ItemStack item = e.getItemDrop().getItemStack();
        if (playerMenuGUI.isPlayerMenuHead(item) || adminChestGUI.isAdminChest(item)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onInventoryClickProtect(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        // Protect menu items from being moved out of their slots
        ItemStack current = e.getCurrentItem();
        ItemStack cursor = e.getCursor();
        if (current != null && (playerMenuGUI.isPlayerMenuHead(current) || adminChestGUI.isAdminChest(current))) {
            e.setCancelled(true);
            return;
        }
        if (cursor != null && (playerMenuGUI.isPlayerMenuHead(cursor) || adminChestGUI.isAdminChest(cursor))) {
            e.setCancelled(true);
        }
    }

    // ── Item interactions ───────────────────────────────────────────

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onPlayerInteract(PlayerInteractEvent e) {
        Player player = e.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null) return;                                   // EARLY‑EXIT

        // Admin chest?
        if (handleAdminChestInteraction(e, player, hand)) return;
        // Player‑menu head?
        handlePlayerMenuHeadInteraction(e, player, hand);
    }




    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)  // <<< NEW flags
    public void onPlayerFish(PlayerFishEvent e) {
        if (e.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;  // only when fish caught
        if (random.nextDouble() < 0.05) {
            Player player = e.getPlayer();
            SeaMonster sm = seaMonsterManager.spawnSeaMonster(player.getLocation());
            sm.attackPlayer(player);
        }
    }


    // Navigation skill on-move handling is in MobKillListener — removed duplicate here
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onWorldLoad(WorldLoadEvent event) {
        enableImmediateRespawn(event.getWorld());
    }

    // ---------------------------
    // Helper Methods
    // ---------------------------

    // handleFirstJoin, giveAdminChestIfNeeded, removeExcessAdminChests
    // REMOVED — moved to PlayerSessionManager

    private boolean handleAdminChestInteraction(PlayerInteractEvent event, Player player, ItemStack item) {
        if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            if (adminChestGUI.isAdminChest(item)) {
                event.setCancelled(true);
                adminChestGUI.open(player);
                return true;
            }
        }
        return false;
    }

    private boolean handlePlayerMenuHeadInteraction(PlayerInteractEvent event, Player player, ItemStack item) {
        if (playerMenuGUI.isPlayerMenuHead(item)) {
            event.setCancelled(true);
            playerMenuGUI.open(player);
            return true;
        }
        return false;
    }


    public FileConfiguration getPlayerDataConfig() {
        return playerDataConfig;
    }

    public void savePlayerDataConfig() {
        // schedule through your AsyncSaveManager instead
        asyncSaver.scheduleYamlSave(
                "playerData",
                playerDataConfig,
                playerDataFile
        );

    }


    // ---------------------------
    // Getters
    // ---------------------------

    public static CerberusPlugin getInstance() {
        return instance;
    }

    public AdminChestGUI getAdminChestGUI() { return adminChestGUI; }
    public PlayerMenuGUI getPlayerMenuGUI() { return playerMenuGUI; }
    public SpellManager getSpellManager() { return spellManager; }
    public SeaMonsterManager getSeaMonsterManager() { return seaMonsterManager; }
    public SkillManager getSkillManager() { return skillManager; }
    public PlayerManaManager getPlayerManaManager() { return playerManaManager; }
    public PlayerDefenseManager getPlayerDefenseManager() { return playerDefenseManager; }
    public ResourceYieldManager getResourceYieldManager() { return resourceYieldManager; }
    public TrapManager getTrapManager() { return trapManager; }
    public LuckManager getLuckManager() { return luckManager; }
    public MagicFindManager getMagicFindManager() { return magicFindManager; }
    public CraftingManager getCraftingManager() { return craftingManager; }
    public EffectManager getEffectManager() { return effectManager; }
    public PlayerHUDManager getPlayerHUDManager() { return playerHUDManager; }
    public PlayerVirtualHealthManager getPlayerVirtualHealthManager() { return playerVirtualHealthManager; }
    public DatabaseManager getDatabaseManager() { return databaseManager; }
    public DefenseBarManager getDefenseBarManager() { return defenseBarManager; }
    public PlayerSessionManager getPlayerSessionManager() { return playerSessionManager; }
    public CombatConfig getCombatConfig() { return combatConfig; }
    public VirtualHealthListener getVirtualHealthListener() { return virtualHealthListener; }
    public SessionLogger getSessionLogger() { return sessionLogger; }

    /** Check if a player has combat debug enabled. */
    public boolean isCombatDebugEnabled(UUID playerId) {
        return combatDebugPlayers.contains(playerId);
    }

    /** Toggle combat debug for a player. Returns new state. */
    public boolean toggleCombatDebug(UUID playerId) {
        if (combatDebugPlayers.contains(playerId)) {
            combatDebugPlayers.remove(playerId);
            return false;
        } else {
            combatDebugPlayers.add(playerId);
            return true;
        }
    }
}

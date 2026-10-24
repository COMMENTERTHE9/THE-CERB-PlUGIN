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
                sweepPreloadedMobs();   // spawn-region chunks load BEFORE enable — scale their mobs
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
        if (mobLabelManager != null)   mobLabelManager.clear();

        // Throttled async YAML saves may still be pending; async tasks don't outlive disable.
        if (playerDataConfig != null && playerDataFile != null) {
            synchronized (playerDataConfig) {
                try { playerDataConfig.save(playerDataFile); }
                catch (IOException e) { getLogger().severe("Could not save playerData.yml on shutdown: " + e.getMessage()); }
            }
        }

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
        mobLabelManager = new Manager.MobLabelManager(this);
        attributesConfig = new Attributes.AttributesConfig(this);
    }

    private Attributes.AttributesConfig attributesConfig;

    public Attributes.AttributesConfig getAttributesConfig() { return attributesConfig; }

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

    private Manager.MobLabelManager mobLabelManager;

    public Manager.MobLabelManager getMobLabelManager() {
        return mobLabelManager;
    }

    private MobScalingListener mobScalingListener;

    /**
     * Scale/label mobs already in the world at enable time. Spawn-region chunks load during
     * world preparation — before plugins enable — so their EntitiesLoadEvent is never seen by
     * our listener. Folia-safe: each entity is handled on its own region thread via its
     * entity scheduler ('retired' callback = null skips entities removed before the task runs).
     */
    private void sweepPreloadedMobs() {
        if (mobScalingListener == null) return;
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (org.bukkit.entity.LivingEntity le : world.getLivingEntities()) {
                le.getScheduler().run(this,
                        task -> mobScalingListener.scaleIfEligible(le, true), null);
            }
        }
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
        // Control Center: menu framework + shared mob test tools + the admin panel itself
        menuManager = new GUIs.menu.MenuManager(this);
        mobTestService = new Manager.MobTestService(this);
        adminPanel = new GUIs.admin.AdminPanel(this, menuManager, mobTestService);
    }

    private GUIs.menu.MenuManager menuManager;
    private Manager.MobTestService mobTestService;
    private GUIs.admin.AdminPanel adminPanel;

    public GUIs.menu.MenuManager getMenuManager() { return menuManager; }
    public Manager.MobTestService getMobTestService() { return mobTestService; }
    public GUIs.admin.AdminPanel getAdminPanel() { return adminPanel; }

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
        pm.registerEvents(menuManager, this); // routes clicks for Control Center menus

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
        this.mobScalingListener = new MobScalingListener(this);
        pm.registerEvents(mobScalingListener, this);
        pm.registerEvents(mobLabelManager, this);

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
        if (attributesConfig != null) {
            attributesConfig.reload();
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
        // Folia: the target may live in another region than the sender, so touch its health on
        // the target's own entity scheduler.
        target.getScheduler().run(this, task -> {
            target.setHealth(target.getAttribute(Attribute.MAX_HEALTH).getValue());
            playerVirtualHealthManager.resetHealth(target);
        }, null);
        sender.sendMessage(ChatColor.GREEN + "Healed " + playerName + ".");
    }

    /** /cerb mana <player> [amount] */
    public void adjustMana(CommandSender sender, String[] args) {
        // TODO: parse args[1] as player, args[2] (if present) as amount, then call mana manager
        sender.sendMessage(ChatColor.YELLOW + "Mana adjustment is not implemented yet.");
    }

    /**
     * /cerb skill <player> <skill> <level|xp> <value>
     * Skill names may use underscores for spaces (Blade_Mastery). "level" sets an exact level
     * (0-100, XP reset to 0); "xp" adds XP through the normal award path (level-up messages).
     */
    public void adjustSkill(CommandSender sender, String[] args) {
        String usage = ChatColor.RED + "Use: /cerb skill <player> <skill> <level|xp> <value>   (e.g. /cerb skill Steve Blade_Mastery level 50)";
        if (args.length != 5) { sender.sendMessage(usage); return; }
        Player target = getServer().getPlayerExact(args[1]);
        if (target == null) { sender.sendMessage(ChatColor.RED + "Player not online: " + args[1]); return; }
        String skill = skillManager.findSkillName(args[2]);
        if (skill == null) { sender.sendMessage(ChatColor.RED + "Unknown skill: " + args[2] + " (tab-complete shows the names)"); return; }
        String mode = args[3].toLowerCase();
        double value;
        try { value = Double.parseDouble(args[4]); } catch (NumberFormatException e) { sender.sendMessage(usage); return; }
        if (!mode.equals("level") && !mode.equals("xp")) { sender.sendMessage(usage); return; }
        if (!Double.isFinite(value) || value < 0) { sender.sendMessage(ChatColor.RED + "Value must be a number >= 0."); return; }

        // Skill state belongs to the target's region thread (Folia).
        target.getScheduler().run(this, task -> {
            if (mode.equals("level")) {
                skillManager.setSkillLevel(target, skill, (int) value);
            } else {
                skillManager.addXpAndCheckLevelUp(skill, value, target);
            }
            int lvl = skillManager.getSkillLevel(target, skill);
            int xp = skillManager.getSkillXP(target, skill);
            sender.sendMessage(ChatColor.GREEN + target.getName() + ": " + skill + " is now level " + lvl + " (" + xp + " XP)");
        }, null);
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
            return;
        }
        // Number-key swap: the hotbar item named by the key moves into the hovered slot, so the
        // protected item never shows up as current/cursor. Same for the F-key offhand swap.
        Player p = (Player) e.getWhoClicked();
        if (e.getClick() == org.bukkit.event.inventory.ClickType.NUMBER_KEY && e.getHotbarButton() >= 0
                && isProtectedMenuItem(p.getInventory().getItem(e.getHotbarButton()))) {
            e.setCancelled(true);
            return;
        }
        if (e.getClick() == org.bukkit.event.inventory.ClickType.SWAP_OFFHAND
                && isProtectedMenuItem(p.getInventory().getItemInOffHand())) {
            e.setCancelled(true);
        }
    }

    /** F-key (outside inventories) moves the held item to the offhand — keep menu items put. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onSwapHands(PlayerSwapHandItemsEvent e) {
        if (isProtectedMenuItem(e.getMainHandItem()) || isProtectedMenuItem(e.getOffHandItem())) {
            e.setCancelled(true);
        }
    }

    private boolean isProtectedMenuItem(ItemStack item) {
        return item != null && (playerMenuGUI.isPlayerMenuHead(item) || adminChestGUI.isAdminChest(item));
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
        // Right-click only: a left-click swing (e.g. attacking while holding the head) used to
        // pop the menu open mid-fight.
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return false;
        }
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

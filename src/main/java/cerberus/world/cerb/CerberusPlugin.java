package cerberus.world.cerb;

import Listener.CombatListener;
import Commands.CerbCommand;
import Manager.CombatScoreboardManager;
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
import Listener.SpeedLimiterListener;
import Spells.SpellManager;
import CustomEntities.SeaMonsterManager;
import Commands.SpawnZombieCommand;
import org.bukkit.command.CommandSender;
import Listener.CustomItemListener;


import Traps.TrapManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import Listener.DefenseUpdateListener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.*;
import Manager.DefenseBarManager;

public class CerberusPlugin extends JavaPlugin implements Listener {

    // Managers and GUIs
    private SkillManager skillManager;
    private RegionSelectorBehavior regionSelectorBehavior;
    private RegionManager regionManager;
    private ProtectionToggleCommand protectionToggleCommand;
    private RegionManagementCommands regionManagementCommands;
    private RegionManagementGUI regionManagementGUI;
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
    private CombatScoreboardManager combatScoreboardManager;
    private CraftingListener craftingListener;
    private ConfigSkillMapper skillMapper;  // store it as a field
    private PlayerStrengthManager playerStrengthManager;
    private CerberusWorldProtection cerberusWorldProtection;
    private ItemUtils itemUtils;

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


    @Override
    public void onEnable() {
        getLogger().info("Cerberus Plugin is enabling...");
        instance = this;

        this.asyncSaver = new AsyncSaveManager(this, SAVE_COOLDOWN_MS);

        // quick, safe work on the main thread
        initFiles();

        // heavy I/O moves to an async thread
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {

            initDatabase();
            initCoreManagers();
            initSkillManager();
            regionManager.loadRegions();

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
                this.skillMapper = new ConfigSkillMapper(this);                // <<< NEW: build & store mapper
                getServer().getPluginManager().registerEvents(                 // <<< NEW: register with mapper
                        new MobKillListener(this, skillManager, skillGUI, skillMapper),
                        this
                );

                initListeners();        // registers your other listeners
                startHUDUpdateTask();
                databaseManager.fixNullCustomIds();

                getLogger().info("Cerberus Plugin has been enabled!");
            });
        });
    }

    public void saveAllPlayers() {
        // schedule the entire batch as a single, throttled DB save
        asyncSaver.scheduleDbSave("saveAllPlayers", () -> {
            for (Player player : getServer().getOnlinePlayers()) {
                UUID playerUUID = player.getUniqueId();
                Map<String, Integer> levels = skillManager.getPlayerSkillLevels(player);
                Map<String, Integer> xp     = skillManager.getPlayerSkillXP(player);
                String customId = databaseManager.getOrCreateCustomId(playerUUID, player.getName());
                databaseManager.saveSkillsByCustomId(customId, levels, xp);
            }
            getLogger().info("All players’ skills saved to the database.");
        });
    }

    @Override
    public void onDisable() {
        if (regionManager != null)     regionManager.saveRegions();
        if (seaMonsterManager != null) seaMonsterManager.clearSeaMonsters();
        if (trapManager != null)       trapManager.cleanup();

        // ←←← NEW: persist every online player’s skills on shutdown
        saveAllPlayers();
        getLogger().info("All players’ skills saved on shutdown.");

        getLogger().info("Cerberus Plugin has been disabled!");
    }

    // ---------------------------
    // Initialization Methods
    // ---------------------------

    private void initFiles() {
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }
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
        cerberusWorldProtection = new CerberusWorldProtection();
        itemUtils = new ItemUtils(this);
        playerHUDManager = new PlayerHUDManager(null, null, playerVirtualHealthManager);
        combatScoreboardManager = new CombatScoreboardManager();
        regionManager = new RegionManager(this);
        regionSelectorBehavior = new RegionSelectorBehavior(this);
        protectionToggleCommand = new ProtectionToggleCommand();
        regionManagementCommands = new RegionManagementCommands(this);
        regionManagementGUI = new RegionManagementGUI(this);
        adminChestGUI = new AdminChestGUI(this);
    }

    public AsyncSaveManager getAsyncSaver() {
        return asyncSaver;
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
        defenseBarManager = new DefenseBarManager(this, playerDefenseManager);
        playerVirtualHealthManager.setDefenseBarManager(defenseBarManager);
        playerManaManager = new PlayerManaManager(skillManager, this);
        resourceYieldManager = new ResourceYieldManager(skillManager, this);
        trapManager = new TrapManager(this, craftingManager);
        luckManager = new LuckManager(skillManager, this, playerVirtualHealthManager, playerDefenseManager, playerStrengthManager);
        magicFindManager = new MagicFindManager(databaseManager);
        craftingManager = new CraftingManager(skillManager, this);
        effectManager = new EffectManager(playerVirtualHealthManager, playerDefenseManager, playerManaManager, skillManager);
        spellManager = new SpellManager(effectManager, playerManaManager);
        seaMonsterManager = new SeaMonsterManager();

        // Now that these managers exist, set them in skillManager
        skillManager.setPlayerDefenseManager(playerDefenseManager);
        skillManager.setPlayerManaManager(playerManaManager);
        skillManager.setResourceYieldManager(resourceYieldManager);
        skillManager.setTrapManager(trapManager);
        skillManager.setLuckManager(luckManager);
        skillManager.setMagicFindManager(magicFindManager);

        // Update HUD manager now that managers are in place
        playerHUDManager = new PlayerHUDManager(playerManaManager, playerDefenseManager, playerVirtualHealthManager);
        playerHUDManager.setDefenseBarManager(defenseBarManager);
    }


    private void initGUIs() {
        skillGUI = new SkillGUI(this, skillManager);
        playerMenuGUI = new PlayerMenuGUI(this, skillManager);
    }

    private void initCommands() {
        Objects.requireNonNull(getCommand("spawncustomzombie")).setExecutor(new SpawnZombieCommand(this));
        Objects.requireNonNull(getCommand("testregen")).setExecutor(new TestRegenCommand(playerVirtualHealthManager));
        Objects.requireNonNull(getCommand("giveTestItem")).setExecutor(new GiveTestItemCommand());
        Objects.requireNonNull(getCommand("regionselector")).setExecutor(new CommandHandler(this, regionSelectorBehavior));
        Objects.requireNonNull(getCommand("unregionizer")).setExecutor(new CommandHandler(this, regionSelectorBehavior));
        Objects.requireNonNull(getCommand("visualizeregions")).setExecutor(new RegionVisualizerCommand(this));
        Objects.requireNonNull(getCommand("toggleprotection")).setExecutor(protectionToggleCommand);
        Objects.requireNonNull(getCommand("region")).setExecutor(regionManagementCommands);
        Objects.requireNonNull(getCommand("givetestarmor")).setExecutor(new GiveTestArmorCommand());
        Objects.requireNonNull(getCommand("killholograms")).setExecutor(new KillHologramsCommand(this));
        Objects.requireNonNull(getCommand("cerb")).setExecutor(new CerbCommand(this));
    }

    private void initListeners() {
        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(this, this);
        pm.registerEvents(new CombatListener(combatScoreboardManager, this,skillManager,spellManager,playerMenuGUI,skillGUI), this);
        pm.registerEvents(new MobKillListener(this, skillManager, skillGUI, skillMapper), this);
        pm.registerEvents(new PlayerHUDListener(playerHUDManager, playerDefenseManager), this);
        pm.registerEvents(new CustomItemListener(spellManager), this);
        pm.registerEvents(new VirtualHealthListener(playerVirtualHealthManager, new FirstAidSkill("First Aid", playerVirtualHealthManager, craftingManager), this), this);        pm.registerEvents(new RegionManagementGUIListener(this), this);
        pm.registerEvents(new AdminGUIListener(this, adminChestGUI), this);
        pm.registerEvents(new MenuProtectionListener(this), this);
        pm.registerEvents(new MenuListener(this, playerMenuGUI, skillGUI, skillManager), this);
        pm.registerEvents(new ManaListener(playerManaManager), this);
        pm.registerEvents(new DefenseUpdateListener(playerHUDManager), this);
        pm.registerEvents(new CustomArmorListener(playerDefenseManager, playerHUDManager), this);
        pm.registerEvents(new DamageDisplayListener(this), this);
        pm.registerEvents(
                new CombatListener(
                        combatScoreboardManager,
                        this,
                        skillManager,
                        spellManager,
                        playerMenuGUI,
                        skillGUI
                ),
                this
        );
        pm.registerEvents(craftingListener, this);
        pm.registerEvents(new DefenseUpdateListener(playerHUDManager), this);
        pm.registerEvents(new PlayerJoinListener(skillManager, databaseManager, this, skillGUI, playerMenuGUI), this);
        pm.registerEvents(new SpeedLimiterListener(), this);
    }

    // ─────────────────────────────────────────────────────────────
    // <<< ADD reloadAll() HERE >>>
    /**
     * Public entry‑point for `/cerb reload`.
     */
    public void reloadAll() {
        // quick work on main thread
        initFiles();

        // heavy I/O off the main thread
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            initDatabase();
            initCoreManagers();
            initSkillManager();
            regionManager.loadRegions();

            // back to main thread for Bukkit API calls
            runSync(() -> {
                initGUIs();
                initCommands();
                initListeners();
                startHUDUpdateTask();
                databaseManager.fixNullCustomIds();
            });
        });
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
            Bukkit.getScheduler().runTask(this, task);
        }
    }


    private void startHUDUpdateTask() {
        new BukkitRunnable() {
            private int index = 0;
            @Override
            public void run() {
                List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
                if (players.isEmpty()) return;

                Player p = players.get(index++ % players.size());
                if (p.isOnline()) {
                    playerHUDManager.updateHUD(p);
                }
            }
        }.runTaskTimer(this, 0, 1);  // run every tick
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
        who.sendMessage("Loaded regions : " + regionManager.getRegions().size());
    }

    /** /cerb heal <player> */
    public void healPlayer(CommandSender sender, String playerName) {
        Player target = getServer().getPlayerExact(playerName);
        if (target == null || !target.isOnline()) {
            sender.sendMessage(ChatColor.RED + "Player not found.");
            return;
        }
        // TODO: restore their real + virtual health
        target.setHealth(target.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue());
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
        // TODO: parse args into target, skill name, “level” vs “xp”, and value
        sender.sendMessage(ChatColor.YELLOW + "Skill adjustment is not implemented yet.");
    }

    /** /cerb resetdata <player> */
    public void resetPlayerData(CommandSender sender, String[] args) {
        // TODO: wipe their custom ID, skill levels & XP, virtual health, etc.
        sender.sendMessage(ChatColor.YELLOW + "Reset data is not implemented yet.");
    }

    /** /cerb banregion <player> */
    public void banRegionForPlayer(CommandSender sender, String[] args) {
        // TODO: prevent that player from entering protected regions
        sender.sendMessage(ChatColor.YELLOW + "Region‐ban is not implemented yet.");
    }

    /** /cerb region <reload|clear> … */
    public void handleRegionSubcommand(CommandSender sender, String[] args) {
        // TODO: if args[1].equals("reload") → regionManager.loadRegions(), or args[1]=="clear" → delete a region
        sender.sendMessage(ChatColor.YELLOW + "Region sub‑commands not implemented yet.");
    }

    /** /cerb protection list */
    public void listProtections(CommandSender sender) {
        // TODO: iterate your protectionToggleCommand map and send each player’s state
        sender.sendMessage(ChatColor.YELLOW + "Protection list is not implemented yet.");
    }

    /** /cerb worldgen <on|off> */
    public void toggleWorldGen(CommandSender sender, String[] args) {
        // TODO: enable/disable your custom worldgen hooks
        sender.sendMessage(ChatColor.YELLOW + "Worldgen toggle is not implemented yet.");
    }

    /** /cerb repairchunk <x> <z> */
    public void repairChunk(CommandSender sender, String[] args) {
        // TODO: call your “nature auto‑repair” routine for the chunk at (x,z)
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

    /** /cerb teleportall <region> */
    public void teleportAllToRegion(CommandSender sender, String[] args) {
        // TODO: teleport every online player into the given region
        sender.sendMessage(ChatColor.YELLOW + "Teleport‑all not implemented yet.");
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
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)  // <<< NEW flags
    public void onPlayerJoin(PlayerJoinEvent e) {
        Player player = e.getPlayer();

        // Early‑exit: skip if managers not yet ready
        if (playerDefenseManager != null) {
            playerDefenseManager.updateDefense(player);
        }
        if (playerHUDManager != null) {
            playerHUDManager.updateHUD(player);
        }

        CerberusGameMode.enable(player, playerDataConfig);  // assume safe

        handleFirstJoin(player);
        giveAdminChestIfNeeded(player);

        if (playerVirtualHealthManager != null) {
            playerVirtualHealthManager.startHealthUpdateTask(player, playerHUDManager);
        }
    }


    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)  // <<< NEW flags
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


    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)  // <<< NEW flags
    public void onPlayerDeath(PlayerDeathEvent e) {
        Player player = e.getEntity();
        if (playerVirtualHealthManager == null) return;   // EARLY‑EXIT

        EntityDamageEvent cause = player.getLastDamageCause();
        Entity damager = (cause instanceof EntityDamageByEntityEvent edb)
                ? edb.getDamager() : null;

        // Apply virtual damage before real death
        playerVirtualHealthManager.applyDamage(
                player,
                player.getHealth(),
                CustomDamageType.ENTITY_ATTACK,
                damager
        );

        // Force respawn next tick
        Bukkit.getScheduler().runTask(this, player.spigot()::respawn);
    }


    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)  // <<< NEW flags
    public void onPlayerInteract(PlayerInteractEvent e) {
        Player player = e.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null) return;                                   // EARLY‑EXIT

        // Admin chest?
        if (handleAdminChestInteraction(e, player, hand)) return;
        // Player‑menu head?
        if (handlePlayerMenuHeadInteraction(e, player, hand)) return;

        // Region selector
        handleRegionSelectorInteraction(e, player, hand);
    }


    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)  // <<< NEW flags
    public void onBlockBreak(BlockBreakEvent e) {
        Player player = e.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();

        // Region selector tools cannot break
        if (itemUtils.isRegionSelector(hand) || itemUtils.isUnregionizer(hand)) {
            e.setCancelled(true);
            player.sendMessage(ChatColor.RED + "You cannot break blocks with region selector tools.");
            return;
        }

        // Protected region?
        if (cerberusWorldProtection.isInProtectedRegion(e.getBlock().getLocation())
                && protectionToggleCommand.isProtectionEnabled(player.getUniqueId())) {
            e.setCancelled(true);
            sendProtectedBlockMessage(player);
        }
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


    // ─── AFTER ───
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent e) {
        // ignore head‑turns / tiny movements
        if (e.getFrom().distanceSquared(e.getTo()) < 0.01) return;

        Player player = e.getPlayer();
        UtilitySkill nav = (UtilitySkill) skillManager.getSkill("Navigation");
        if (nav == null) return;

        // now pass only the UUID (or the Player) — your UtilitySkill
        // should look up whatever it needs by that key
        nav.applyEffect(player.getUniqueId());
    }

    // ---------------------------
    // Helper Methods
    // ---------------------------

    private void handleFirstJoin(Player player) {
        String playerPath = "players." + player.getUniqueId().toString();
        if (!playerDataConfig.contains(playerPath)) {
            playerDataConfig.set(playerPath + ".joinedBefore", true);
            savePlayerDataConfig();
            player.setHealth(player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue());
            player.sendMessage(ChatColor.GREEN + "Welcome to Cerberus Mode for the first time! Health and hunger maximized.");
        }
    }

    private void giveAdminChestIfNeeded(Player player) {
        if (player.hasPermission("cerberus.admin")) {
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
                removeExcessAdminChests(player, adminChestCount);
            }
        }
    }

    private void removeExcessAdminChests(Player player, int adminChestCount) {
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

    private boolean handleAdminChestInteraction(PlayerInteractEvent event, Player player, ItemStack item) {
        if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            if (adminChestGUI.isAdminChest(item)) {
                event.setCancelled(true);
                adminChestGUI.openAdminGUI(player);
                return true;
            }
        }
        return false;
    }

    private boolean handlePlayerMenuHeadInteraction(PlayerInteractEvent event, Player player, ItemStack item) {
        if (playerMenuGUI.isPlayerMenuHead(item)) {
            event.setCancelled(true);
            playerMenuGUI.openMainMenu(player);
            return true;
        }
        return false;
    }

    private void handleRegionSelectorInteraction(PlayerInteractEvent event, Player player, ItemStack item) {
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

    private void sendProtectedBlockMessage(Player player) {
        String[] blockMessages = {
                "A force stops you from breaking this block.",
                "The block is harder than anything you've felt before.",
                "A mystical barrier protects this area.",
                "Your tools feel useless here."
        };
        player.sendMessage(ChatColor.RED + blockMessages[random.nextInt(blockMessages.length)]);
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

    public RegionManager getRegionManager() { return regionManager; }
    public RegionManagementGUI getRegionManagementGUI() { return regionManagementGUI; }
    public AdminChestGUI getAdminChestGUI() { return adminChestGUI; }
    public PlayerMenuGUI getPlayerMenuGUI() { return playerMenuGUI; }
    public ProtectionToggleCommand getProtectionToggleCommand() { return protectionToggleCommand; }
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
    public CerberusWorldProtection getCerberusWorldProtection() { return cerberusWorldProtection; }
    public ItemUtils getItemUtils() { return itemUtils; }
    public DatabaseManager getDatabaseManager() { return databaseManager; }
    public DefenseBarManager getDefenseBarManager() { return defenseBarManager; }
}
